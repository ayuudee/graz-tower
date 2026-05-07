#!/usr/bin/env python3

from __future__ import annotations

import argparse
import io
import json
import math
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report


TILE_SIZE_PX = 256


@dataclass(frozen=True)
class SlippyTile:
    layer: str
    zoom: int
    x: int
    y: int
    path: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Render a clipped slippy-tile bundle into a local-frame raster underlay PNG.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "bundle",
        type=Path,
        nargs="?",
        help="Slippy tile ZIP bundle. Defaults to manifest sources.slippyTilesBundle.",
    )
    parser.add_argument(
        "--layer",
        type=str,
        default=None,
        help="Tile layer name inside the bundle. Defaults to the only layer present, or 'aero' if available.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="PNG output path. Defaults to cad/airports/<airport>_raster_underlay.png.",
    )
    parser.add_argument(
        "--placement-output",
        type=Path,
        default=None,
        help="Placement metadata JSON path. Defaults to cad/airports/<airport>_raster_underlay_placement.json.",
    )
    parser.add_argument(
        "--margin-m",
        type=float,
        default=6000.0,
        help="Airport-local margin around the apt.dat footprint when selecting tiles. Defaults to 6000 m.",
    )
    return parser.parse_args()


def tile_from_member(path: str) -> SlippyTile | None:
    parts = PurePosixPath(path).parts
    if len(parts) < 7 or parts[-1].lower().endswith(".png") is False:
        return None
    if parts[-5] != "256" or parts[-4] != "latest":
        return None
    try:
        zoom = int(parts[-3])
        x = int(parts[-2])
        y = int(Path(parts[-1]).stem)
    except ValueError:
        return None
    return SlippyTile(layer=parts[-6], zoom=zoom, x=x, y=y, path=path)


def available_tiles(bundle_path: Path) -> list[SlippyTile]:
    with zipfile.ZipFile(bundle_path) as bundle:
        return [
            tile
            for member in bundle.namelist()
            for tile in [tile_from_member(member)]
            if tile is not None
        ]


def choose_layer(tiles: list[SlippyTile], requested_layer: str | None) -> str:
    layers = sorted({tile.layer for tile in tiles})
    if requested_layer is not None:
        if requested_layer not in layers:
            raise ValueError(f"Layer '{requested_layer}' not found in tile bundle. Available: {', '.join(layers)}")
        return requested_layer
    if len(layers) == 1:
        return layers[0]
    if "aero" in layers:
        return "aero"
    raise ValueError(f"Multiple layers present; choose one explicitly: {', '.join(layers)}")


def choose_zoom(tiles: list[SlippyTile]) -> int:
    zoom_levels = sorted({tile.zoom for tile in tiles})
    return max(zoom_levels)


def tile_lon_deg(x: int, zoom: int) -> float:
    return (x / (2**zoom)) * 360.0 - 180.0


def tile_lat_deg(y: int, zoom: int) -> float:
    n = math.pi - (2.0 * math.pi * y / (2**zoom))
    return math.degrees(math.atan(math.sinh(n)))


def lon_to_tile_x(lon_deg: float, zoom: int) -> int:
    return int(math.floor(((lon_deg + 180.0) / 360.0) * (2**zoom)))


def lat_to_tile_y(lat_deg: float, zoom: int) -> int:
    lat_rad = math.radians(lat_deg)
    y = (1.0 - (math.asinh(math.tan(lat_rad)) / math.pi)) / 2.0
    return int(math.floor(y * (2**zoom)))


def airport_origin(manifest_path: Path) -> report.Geo:
    manifest = report.load_manifest(manifest_path)
    apt_path = report.resolve_path(report.repo_root(), manifest["sources"]["aptDat"])
    _, _, _, _, apt_metadata, _ = report.parse_apt(apt_path)
    return report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))


def invert_projector(origin: report.Geo):
    lat0 = math.radians(origin.lat)
    lon0 = math.radians(origin.lon)

    def unproject(point: report.XY) -> report.Geo:
        lat = lat0 + (point.y / report.EARTH_RADIUS_METERS)
        lon = lon0 + (point.x / (report.EARTH_RADIUS_METERS * math.cos(lat0)))
        return report.Geo(math.degrees(lat), math.degrees(lon))

    return unproject


def airport_bounds_xy(manifest_path: Path) -> tuple[report.XY, report.XY]:
    manifest = report.load_manifest(manifest_path)
    apt_path = report.resolve_path(report.repo_root(), manifest["sources"]["aptDat"])
    runways, tower, taxi_nodes, taxi_edges, apt_metadata, parking_positions = report.parse_apt(apt_path)
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = report.projector(origin)
    points: list[report.XY] = []
    for runway in {id(record): record for record in runways.values()}.values():
        points.extend([project(runway.end_a), project(runway.end_b)])
    points.extend(project(node.position) for node in taxi_nodes.values())
    points.extend(project(parking.position) for parking in parking_positions)
    if tower is not None:
        points.append(project(tower.position))
    min_x = min(point.x for point in points)
    max_x = max(point.x for point in points)
    min_y = min(point.y for point in points)
    max_y = max(point.y for point in points)
    return report.XY(min_x, min_y), report.XY(max_x, max_y)


def crop_tiles_to_airport_extent(
    manifest_path: Path,
    tiles: list[SlippyTile],
    margin_m: float,
) -> list[SlippyTile]:
    origin = airport_origin(manifest_path)
    unproject = invert_projector(origin)
    extmin, extmax = airport_bounds_xy(manifest_path)
    padded_min = report.XY(extmin.x - margin_m, extmin.y - margin_m)
    padded_max = report.XY(extmax.x + margin_m, extmax.y + margin_m)
    north_west = unproject(report.XY(padded_min.x, padded_max.y))
    north_east = unproject(report.XY(padded_max.x, padded_max.y))
    south_west = unproject(report.XY(padded_min.x, padded_min.y))
    south_east = unproject(report.XY(padded_max.x, padded_min.y))
    lons = [north_west.lon, north_east.lon, south_west.lon, south_east.lon]
    lats = [north_west.lat, north_east.lat, south_west.lat, south_east.lat]
    zoom = choose_zoom(tiles)
    min_tile_x = lon_to_tile_x(min(lons), zoom)
    max_tile_x = lon_to_tile_x(max(lons), zoom)
    min_tile_y = lat_to_tile_y(max(lats), zoom)
    max_tile_y = lat_to_tile_y(min(lats), zoom)
    cropped = [
        tile
        for tile in tiles
        if min_tile_x <= tile.x <= max_tile_x and min_tile_y <= tile.y <= max_tile_y
    ]
    return cropped or tiles


def render_bundle(
    manifest_path: Path,
    bundle_path: Path,
    output_path: Path,
    placement_path: Path,
    requested_layer: str | None,
    margin_m: float,
) -> None:
    try:
        from PIL import Image
    except ImportError as error:
        raise SystemExit(
            "Pillow is required for slippy raster rendering. Run via `nix-shell -p python3 python3Packages.pillow --run ...`."
        ) from error

    all_tiles = available_tiles(bundle_path)
    if not all_tiles:
        raise ValueError(f"No renderable PNG slippy tiles found in {bundle_path}")

    layer = choose_layer(all_tiles, requested_layer)
    layer_tiles = [tile for tile in all_tiles if tile.layer == layer]
    zoom = choose_zoom(layer_tiles)
    tiles = crop_tiles_to_airport_extent(
        manifest_path,
        [tile for tile in layer_tiles if tile.zoom == zoom],
        margin_m,
    )
    min_x = min(tile.x for tile in tiles)
    max_x = max(tile.x for tile in tiles)
    min_y = min(tile.y for tile in tiles)
    max_y = max(tile.y for tile in tiles)
    width_tiles = (max_x - min_x) + 1
    height_tiles = (max_y - min_y) + 1
    image = Image.new("RGBA", (width_tiles * TILE_SIZE_PX, height_tiles * TILE_SIZE_PX), (0, 0, 0, 0))

    with zipfile.ZipFile(bundle_path) as bundle:
        for tile in tiles:
            with bundle.open(tile.path) as source:
                tile_image = Image.open(io.BytesIO(source.read())).convert("RGBA")
            paste_x = (tile.x - min_x) * TILE_SIZE_PX
            paste_y = (tile.y - min_y) * TILE_SIZE_PX
            image.paste(tile_image, (paste_x, paste_y))

    image.save(output_path)

    west_lon = tile_lon_deg(min_x, zoom)
    east_lon = tile_lon_deg(max_x + 1, zoom)
    north_lat = tile_lat_deg(min_y, zoom)
    south_lat = tile_lat_deg(max_y + 1, zoom)
    project = report.projector(airport_origin(manifest_path))
    corners = [
        project(report.Geo(north_lat, west_lon)),
        project(report.Geo(north_lat, east_lon)),
        project(report.Geo(south_lat, west_lon)),
        project(report.Geo(south_lat, east_lon)),
    ]
    min_corner_x = min(point.x for point in corners)
    max_corner_x = max(point.x for point in corners)
    min_corner_y = min(point.y for point in corners)
    max_corner_y = max(point.y for point in corners)

    placement = {
        "imagePath": str(output_path.resolve()),
        "sourceBundle": str(bundle_path.resolve()),
        "layer": layer,
        "zoom": zoom,
        "cropMarginMeters": margin_m,
        "boundsMeters": {
            "minX": min_corner_x,
            "minY": min_corner_y,
            "maxX": max_corner_x,
            "maxY": max_corner_y,
        },
        "imageSizePx": {
            "width": image.width,
            "height": image.height,
        },
        "recommendedPlacement": {
            "lowerLeft": {
                "x": min_corner_x,
                "y": min_corner_y,
            },
            "widthMeters": max_corner_x - min_corner_x,
            "heightMeters": max_corner_y - min_corner_y,
        },
        "metersPerPixel": {
            "x": (max_corner_x - min_corner_x) / image.width,
            "y": (max_corner_y - min_corner_y) / image.height,
        },
    }
    placement_path.write_text(json.dumps(placement, indent=2) + "\n")


def main() -> None:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    manifest = report.load_manifest(manifest_path)
    airport_code = manifest["airportCode"].lower()
    root = report.repo_root()
    bundle_path = (
        args.bundle.resolve()
        if args.bundle is not None
        else report.resolve_path(root, manifest["sources"]["slippyTilesBundle"])
    )
    output_path = (
        args.output.resolve()
        if args.output is not None
        else root / "cad/airports" / f"{airport_code}_raster_underlay.png"
    )
    placement_path = (
        args.placement_output.resolve()
        if args.placement_output is not None
        else root / "cad/airports" / f"{airport_code}_raster_underlay_placement.json"
    )
    render_bundle(
        manifest_path,
        bundle_path,
        output_path,
        placement_path,
        args.layer,
        args.margin_m,
    )
    print(output_path)
    print(placement_path)


if __name__ == "__main__":
    main()
