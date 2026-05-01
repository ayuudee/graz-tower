#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report
import export_osm_geojson_dxf as osm_underlay


DEFAULT_WIDTH_PX = 4096
DEFAULT_MARGIN_M = 4000.0


@dataclass(frozen=True)
class RasterCanvas:
    extmin: report.XY
    extmax: report.XY
    width_px: int
    height_px: int
    scale: float

    def map(self, point: report.XY) -> tuple[float, float]:
        return (
            (point.x - self.extmin.x) * self.scale,
            (self.extmax.y - point.y) * self.scale,
        )


@dataclass(frozen=True)
class Extents:
    min_x: float
    min_y: float
    max_x: float
    max_y: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Render a cached OSM GeoJSON underlay into a raster PNG in the airport-local frame.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "geojson",
        type=Path,
        nargs="?",
        help="Input GeoJSON file. Defaults to cad/airports/<airport>_osm_underlay.geojson.",
    )
    parser.add_argument(
        "--width-px",
        type=int,
        default=DEFAULT_WIDTH_PX,
        help=f"Raster width in pixels. Defaults to {DEFAULT_WIDTH_PX}.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="PNG output path. Defaults to cad/airports/<airport>_osm_underlay.png.",
    )
    parser.add_argument(
        "--placement-output",
        type=Path,
        default=None,
        help="Placement metadata JSON path. Defaults to cad/airports/<airport>_osm_underlay_placement.json.",
    )
    parser.add_argument(
        "--margin-m",
        type=float,
        default=DEFAULT_MARGIN_M,
        help=f"Airport-local raster margin in meters. Defaults to {DEFAULT_MARGIN_M:.0f}.",
    )
    return parser.parse_args()


def highway_style(layer_name: str, scale: float) -> tuple[tuple[int, int, int, int], int]:
    layer = layer_name.removeprefix("OSM_HIGHWAY_")
    widths = {
        "MOTORWAY": 8,
        "MOTORWAY_LINK": 6,
        "PRIMARY": 7,
        "PRIMARY_LINK": 5,
        "SECONDARY": 6,
        "SECONDARY_LINK": 4,
        "TERTIARY": 5,
        "RESIDENTIAL": 4,
        "UNCLASSIFIED": 4,
        "SERVICE": 3,
        "LIVING_STREET": 3,
        "TRACK": 2,
        "PATH": 2,
        "CYCLEWAY": 2,
        "FOOTWAY": 2,
        "PEDESTRIAN": 3,
        "STEPS": 2,
        "RACEWAY": 3,
        "PLATFORM": 2,
        "REST_AREA": 2,
        "CONSTRUCTION": 2,
    }
    colors = {
        "MOTORWAY": (214, 121, 71, 255),
        "MOTORWAY_LINK": (214, 121, 71, 255),
        "PRIMARY": (225, 170, 92, 255),
        "PRIMARY_LINK": (225, 170, 92, 255),
        "SECONDARY": (216, 194, 114, 255),
        "SECONDARY_LINK": (216, 194, 114, 255),
        "TERTIARY": (192, 192, 178, 255),
        "RESIDENTIAL": (180, 180, 180, 255),
        "UNCLASSIFIED": (180, 180, 180, 255),
        "SERVICE": (158, 158, 158, 255),
        "LIVING_STREET": (162, 162, 162, 255),
        "TRACK": (135, 122, 104, 255),
        "PATH": (113, 113, 113, 255),
        "CYCLEWAY": (88, 130, 168, 255),
        "FOOTWAY": (113, 113, 113, 255),
        "PEDESTRIAN": (160, 160, 160, 255),
        "STEPS": (113, 113, 113, 255),
        "RACEWAY": (158, 92, 92, 255),
        "PLATFORM": (126, 126, 126, 255),
        "REST_AREA": (170, 170, 170, 255),
        "CONSTRUCTION": (177, 136, 94, 255),
    }
    base_width = widths.get(layer, 3)
    return colors.get(layer, (170, 170, 170, 255)), max(int(round(base_width * scale)), 1)


def line_style(layer_name: str, scale: float) -> tuple[tuple[int, int, int, int], int]:
    if layer_name.startswith("OSM_HIGHWAY_"):
        return highway_style(layer_name, scale)
    if layer_name.startswith("OSM_RAILWAY_"):
        return (86, 86, 86, 255), max(int(round(2.2 * scale)), 1)
    if layer_name.startswith("OSM_WATERWAY_"):
        return (85, 146, 196, 255), max(int(round(2.6 * scale)), 1)
    if layer_name.startswith("OSM_AEROWAY_"):
        return (160, 160, 160, 255), max(int(round(2.0 * scale)), 1)
    return (140, 140, 140, 255), max(int(round(1.8 * scale)), 1)


def polygon_style(layer_name: str) -> tuple[tuple[int, int, int, int], tuple[int, int, int, int]]:
    if layer_name.startswith("OSM_BUILDING"):
        return (214, 211, 206, 255), (176, 173, 168, 255)
    if layer_name.startswith("OSM_LANDUSE_") or layer_name.startswith("OSM_LEISURE_"):
        return (224, 236, 216, 255), (196, 212, 188, 255)
    if layer_name.startswith("OSM_NATURAL_WATER") or layer_name.startswith("OSM_WATERWAY_"):
        return (205, 226, 244, 255), (116, 162, 198, 255)
    if layer_name.startswith("OSM_AEROWAY_"):
        return (233, 229, 220, 255), (200, 196, 188, 255)
    return (230, 230, 230, 255), (190, 190, 190, 255)


def feature_sort_key(feature: dict[str, object]) -> tuple[int, int]:
    kind = str(feature["kind"])
    layer = str(feature["layer"])
    if kind == "polygon":
        if layer.startswith("OSM_LANDUSE_") or layer.startswith("OSM_LEISURE_"):
            return (0, 0)
        if layer.startswith("OSM_NATURAL_WATER"):
            return (0, 1)
        if layer.startswith("OSM_AEROWAY_"):
            return (0, 2)
        if layer.startswith("OSM_BUILDING"):
            return (0, 3)
        return (0, 4)
    if kind == "line":
        if layer.startswith("OSM_WATERWAY_"):
            return (1, 0)
        if layer.startswith("OSM_RAILWAY_"):
            return (1, 1)
        if layer.startswith("OSM_HIGHWAY_"):
            return (1, 2)
        if layer.startswith("OSM_AEROWAY_"):
            return (1, 3)
        return (1, 4)
    return (2, 0)


def build_canvas(extents: Extents, width_px: int) -> RasterCanvas:
    extmin = report.XY(extents.min_x, extents.min_y)
    extmax = report.XY(extents.max_x, extents.max_y)
    span_x = max(extmax.x - extmin.x, 1.0)
    span_y = max(extmax.y - extmin.y, 1.0)
    scale = width_px / span_x
    height_px = max(int(round(span_y * scale)), 1)
    return RasterCanvas(
        extmin=extmin,
        extmax=extmax,
        width_px=width_px,
        height_px=height_px,
        scale=scale,
    )


def airport_extents(manifest_path: Path, margin_m: float) -> Extents:
    manifest = report.load_manifest(manifest_path)
    apt_path = report.resolve_path(report.repo_root(), manifest["sources"]["aptDat"])
    runways, tower, taxi_nodes, _, apt_metadata, parking_positions = report.parse_apt(apt_path)
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = report.projector(origin)
    points: list[report.XY] = []
    for runway in {id(record): record for record in runways.values()}.values():
        points.extend([project(runway.end_a), project(runway.end_b)])
    points.extend(project(node.position) for node in taxi_nodes.values())
    points.extend(project(position.position) for position in parking_positions)
    if tower is not None:
        points.append(project(tower.position))
    min_x = min(point.x for point in points) - margin_m
    max_x = max(point.x for point in points) + margin_m
    min_y = min(point.y for point in points) - margin_m
    max_y = max(point.y for point in points) + margin_m
    return Extents(min_x=min_x, min_y=min_y, max_x=max_x, max_y=max_y)


def draw_feature(draw, canvas: RasterCanvas, feature: dict[str, object]) -> None:
    points = [report.XY(float(point["x"]), float(point["y"])) for point in feature.get("points", [])]
    if len(points) < 2:
        return
    mapped = [canvas.map(point) for point in points]
    layer = str(feature["layer"])
    kind = str(feature["kind"])
    style_scale = canvas.width_px / 2048.0

    if kind == "polygon":
        fill, outline = polygon_style(layer)
        draw.polygon(mapped, fill=fill, outline=outline)
        return

    color, width = line_style(layer, style_scale)
    draw.line(mapped, fill=color, width=width, joint="curve")


def render_raster(
    manifest_path: Path,
    geojson_path: Path,
    output_path: Path,
    placement_path: Path,
    width_px: int,
    margin_m: float,
) -> None:
    try:
        from PIL import Image, ImageDraw
    except ImportError as error:
        raise SystemExit(
            "Pillow is required for raster rendering. Run via `nix-shell -p python3 python3Packages.pillow --run ...`."
        ) from error

    features = osm_underlay.project_features(manifest_path, geojson_path)
    if not features:
        raise ValueError("No usable OSM features were found for raster rendering.")

    canvas = build_canvas(airport_extents(manifest_path, margin_m), width_px)
    image = Image.new("RGBA", (canvas.width_px, canvas.height_px), (244, 243, 238, 255))
    draw = ImageDraw.Draw(image, "RGBA")

    for feature in sorted(features, key=feature_sort_key):
        draw_feature(draw, canvas, feature)

    image.save(output_path)

    placement = {
        "imagePath": str(output_path.resolve()),
        "sourceGeojson": str(geojson_path.resolve()),
        "boundsMeters": {
            "minX": canvas.extmin.x,
            "minY": canvas.extmin.y,
            "maxX": canvas.extmax.x,
            "maxY": canvas.extmax.y,
        },
        "imageSizePx": {
            "width": canvas.width_px,
            "height": canvas.height_px,
        },
        "recommendedPlacement": {
            "lowerLeft": {
                "x": canvas.extmin.x,
                "y": canvas.extmin.y,
            },
            "widthMeters": canvas.extmax.x - canvas.extmin.x,
            "heightMeters": canvas.extmax.y - canvas.extmin.y,
        },
        "cropMarginMeters": margin_m,
        "metersPerPixel": {
            "x": (canvas.extmax.x - canvas.extmin.x) / canvas.width_px,
            "y": (canvas.extmax.y - canvas.extmin.y) / canvas.height_px,
        },
    }
    placement_path.write_text(json.dumps(placement, indent=2) + "\n")


def main() -> None:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    airport_code = report.load_manifest(manifest_path)["airportCode"].lower()
    root = report.repo_root() / "cad/airports"
    geojson_path = args.geojson.resolve() if args.geojson is not None else root / f"{airport_code}_osm_underlay.geojson"
    output_path = args.output.resolve() if args.output is not None else root / f"{airport_code}_osm_underlay.png"
    placement_path = (
        args.placement_output.resolve()
        if args.placement_output is not None
        else root / f"{airport_code}_osm_underlay_placement.json"
    )
    render_raster(
        manifest_path=manifest_path,
        geojson_path=geojson_path,
        output_path=output_path,
        placement_path=placement_path,
        width_px=args.width_px,
        margin_m=args.margin_m,
    )
    print(output_path)


if __name__ == "__main__":
    main()
