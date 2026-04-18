#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import math
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report
import simple_dxf as dxf

DEFAULT_OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
DEFAULT_OVERPASS_TIMEOUT_S = 90
DEFAULT_FETCH_MARGIN_M = 4_000.0
DEFAULT_OVERPASS_RETRIES = 2
UNDERLAY_TAG_FILTERS: tuple[tuple[str, str | None], ...] = (
    ("highway", None),
    ("railway", None),
    ("waterway", None),
    ("aeroway", None),
    ("landuse", None),
    ("leisure", None),
    ("building", None),
    ("natural", "water"),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Project OSM GeoJSON into the airport-local DXF frame for use as an underlay.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "geojson",
        type=Path,
        nargs="?",
        help="Input GeoJSON file in WGS84 / lon-lat coordinates. Omit when using --fetch-overpass.",
    )
    parser.add_argument(
        "--fetch-overpass",
        action="store_true",
        help="Fetch a local OSM underlay directly from Overpass instead of reading a GeoJSON file.",
    )
    parser.add_argument(
        "--geojson-cache",
        type=Path,
        default=None,
        help="Optional path to write the fetched GeoJSON. Defaults to cad/airports/<airport>_osm_underlay.geojson.",
    )
    parser.add_argument(
        "--overpass-endpoint",
        default=DEFAULT_OVERPASS_ENDPOINT,
        help=f"Overpass API interpreter endpoint. Defaults to {DEFAULT_OVERPASS_ENDPOINT}.",
    )
    parser.add_argument(
        "--timeout-s",
        type=int,
        default=DEFAULT_OVERPASS_TIMEOUT_S,
        help=f"Overpass query timeout in seconds. Defaults to {DEFAULT_OVERPASS_TIMEOUT_S}.",
    )
    parser.add_argument(
        "--margin-m",
        type=float,
        default=DEFAULT_FETCH_MARGIN_M,
        help=f"Margin to add around the airport reference geometry when fetching OSM, in meters. Defaults to {DEFAULT_FETCH_MARGIN_M:.0f}.",
    )
    parser.add_argument(
        "--retries",
        type=int,
        default=DEFAULT_OVERPASS_RETRIES,
        help=f"Retry count for transient Overpass failures per feature class. Defaults to {DEFAULT_OVERPASS_RETRIES}.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="DXF output path. Defaults to cad/airports/<airport>_osm_underlay.dxf.",
    )
    return parser.parse_args()


def sanitize_suffix(value: str) -> str:
    return "".join(character if character.isalnum() else "_" for character in value.upper()).strip("_") or "UNSPEC"


def geometry_family(geometry_type: str) -> str:
    if geometry_type in {"Point", "MultiPoint"}:
        return "POINT"
    if geometry_type in {"LineString", "MultiLineString"}:
        return "LINE"
    if geometry_type in {"Polygon", "MultiPolygon"}:
        return "POLYGON"
    return "OTHER"


def feature_layer(properties: dict[str, Any], geometry_type: str) -> str:
    for key in ("highway", "railway", "waterway", "aeroway", "landuse", "natural", "building", "leisure"):
        value = properties.get(key)
        if isinstance(value, str) and value:
            return f"OSM_{key.upper()}_{sanitize_suffix(value)}"
        if value is True:
            return f"OSM_{key.upper()}"
    return f"OSM_{geometry_family(geometry_type)}"


def layer_color(layer_name: str) -> int:
    if layer_name.startswith("OSM_HIGHWAY_"):
        return 8
    if layer_name.startswith("OSM_RAILWAY_"):
        return 5
    if layer_name.startswith("OSM_WATERWAY_") or layer_name.startswith("OSM_NATURAL_WATER"):
        return 4
    if layer_name.startswith("OSM_LANDUSE_") or layer_name.startswith("OSM_LEISURE_"):
        return 3
    if layer_name.startswith("OSM_BUILDING"):
        return 9
    if layer_name.startswith("OSM_AEROWAY_"):
        return 2
    return 7


def label_text(properties: dict[str, Any]) -> str | None:
    name = properties.get("name")
    if isinstance(name, str) and name:
        return name
    ref = properties.get("ref")
    if isinstance(ref, str) and ref:
        return ref
    return None


def project_lonlat(project, coordinate: list[float]) -> report.XY:
    lon, lat = coordinate[:2]
    return project(report.Geo(float(lat), float(lon)))


def point_centroid(points: list[report.XY]) -> report.XY:
    return report.XY(
        sum(point.x for point in points) / len(points),
        sum(point.y for point in points) / len(points),
    )


def line_midpoint(points: list[report.XY]) -> report.XY:
    if len(points) == 1:
        return points[0]
    lengths = [points[index].distance_to(points[index + 1]) for index in range(len(points) - 1)]
    total = sum(lengths)
    if total <= 0.0:
        return points[0]
    target = total / 2.0
    walked = 0.0
    for index, length in enumerate(lengths):
        if walked + length >= target and length > 0.0:
            ratio = (target - walked) / length
            start = points[index]
            end = points[index + 1]
            return report.XY(start.x + ((end.x - start.x) * ratio), start.y + ((end.y - start.y) * ratio))
        walked += length
    return points[-1]


def polygon_rings(geometry: dict[str, Any], project) -> list[list[report.XY]]:
    geometry_type = geometry.get("type")
    coordinates = geometry.get("coordinates", [])
    if geometry_type == "Polygon":
        return [[project_lonlat(project, coordinate) for coordinate in ring] for ring in coordinates if len(ring) >= 3]
    if geometry_type == "MultiPolygon":
        rings: list[list[report.XY]] = []
        for polygon in coordinates:
            rings.extend(
                [project_lonlat(project, coordinate) for coordinate in ring]
                for ring in polygon
                if len(ring) >= 3
            )
        return rings
    return []


def line_paths(geometry: dict[str, Any], project) -> list[list[report.XY]]:
    geometry_type = geometry.get("type")
    coordinates = geometry.get("coordinates", [])
    if geometry_type == "LineString":
        return [[project_lonlat(project, coordinate) for coordinate in coordinates if len(coordinate) >= 2]]
    if geometry_type == "MultiLineString":
        return [
            [project_lonlat(project, coordinate) for coordinate in line if len(coordinate) >= 2]
            for line in coordinates
        ]
    return []


def point_geometries(geometry: dict[str, Any], project) -> list[report.XY]:
    geometry_type = geometry.get("type")
    coordinates = geometry.get("coordinates", [])
    if geometry_type == "Point" and len(coordinates) >= 2:
        return [project_lonlat(project, coordinates)]
    if geometry_type == "MultiPoint":
        return [project_lonlat(project, coordinate) for coordinate in coordinates if len(coordinate) >= 2]
    return []


def collect_extents(features: list[dict[str, Any]]) -> tuple[report.XY, report.XY]:
    points = [
        report.XY(float(point["x"]), float(point["y"]))
        for feature in features
        for point in feature["points"]
    ]
    min_x = min(point.x for point in points)
    max_x = max(point.x for point in points)
    min_y = min(point.y for point in points)
    max_y = max(point.y for point in points)
    margin = max(max_x - min_x, max_y - min_y) * 0.04
    return report.XY(min_x - margin, min_y - margin), report.XY(max_x + margin, max_y + margin)


def airport_reference_geometries(manifest_path: Path) -> tuple[dict[str, Any], report.Geo, list[report.Geo]]:
    root = report.repo_root()
    manifest = report.load_manifest(manifest_path)
    apt_path = report.resolve_path(root, manifest["sources"]["aptDat"])
    if apt_path is None:
        raise ValueError("Manifest does not declare an apt.dat source.")

    runways, tower, taxi_nodes, _, apt_metadata, parking_positions = report.parse_apt(apt_path)
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    reference_geometries: list[report.Geo] = []

    seen_runway_pairs: set[tuple[str, str]] = set()
    for runway in runways.values():
        pair = tuple(sorted((runway.designator_a, runway.designator_b)))
        if pair in seen_runway_pairs:
            continue
        seen_runway_pairs.add(pair)
        reference_geometries.extend((runway.end_a, runway.end_b))

    if tower is not None:
        reference_geometries.append(tower.position)

    reference_geometries.extend(node.position for node in taxi_nodes.values())
    reference_geometries.extend(position.position for position in parking_positions)
    return manifest, origin, reference_geometries


def fetch_bbox(reference_geometries: list[report.Geo], margin_m: float) -> tuple[float, float, float, float]:
    if not reference_geometries:
        raise ValueError("Unable to derive an airport fetch bounding box without reference geometry.")

    min_lat = min(geo.lat for geo in reference_geometries)
    max_lat = max(geo.lat for geo in reference_geometries)
    min_lon = min(geo.lon for geo in reference_geometries)
    max_lon = max(geo.lon for geo in reference_geometries)
    mid_lat = (min_lat + max_lat) / 2.0
    lat_margin_deg = math.degrees(margin_m / report.EARTH_RADIUS_METERS)
    lon_margin_deg = math.degrees(margin_m / (report.EARTH_RADIUS_METERS * math.cos(math.radians(mid_lat))))
    return (
        min_lat - lat_margin_deg,
        min_lon - lon_margin_deg,
        max_lat + lat_margin_deg,
        max_lon + lon_margin_deg,
    )


def overpass_clause(key: str, value: str | None, bbox: tuple[float, float, float, float]) -> str:
    south, west, north, east = bbox
    if value is None:
        return f'way["{key}"]({south:.7f},{west:.7f},{north:.7f},{east:.7f});'
    return f'way["{key}"="{value}"]({south:.7f},{west:.7f},{north:.7f},{east:.7f});'


def build_overpass_query(
    bbox: tuple[float, float, float, float],
    timeout_s: int,
    filters: tuple[tuple[str, str | None], ...] = UNDERLAY_TAG_FILTERS,
) -> str:
    clauses = "\n  ".join(overpass_clause(key, value, bbox) for key, value in filters)
    return f"""
[out:json][timeout:{timeout_s}];
(
  {clauses}
);
out geom;
""".strip()


def is_closed_ring(coordinates: list[list[float]]) -> bool:
    return len(coordinates) >= 4 and coordinates[0] == coordinates[-1]


def is_area_way(tags: dict[str, Any], coordinates: list[list[float]]) -> bool:
    if not is_closed_ring(coordinates):
        return False
    if "building" in tags or "landuse" in tags or "leisure" in tags:
        return True
    if tags.get("natural") == "water":
        return True
    if "aeroway" in tags:
        return True
    return False


def overpass_element_to_feature(element: dict[str, Any]) -> dict[str, Any] | None:
    element_type = element.get("type")
    tags = element.get("tags")
    if not isinstance(tags, dict):
        return None

    feature_properties = {
        **tags,
        "osm_type": element_type,
        "osm_id": element.get("id"),
    }

    if element_type == "node":
        lat = element.get("lat")
        lon = element.get("lon")
        if not isinstance(lat, (int, float)) or not isinstance(lon, (int, float)):
            return None
        return {
            "type": "Feature",
            "properties": feature_properties,
            "geometry": {
                "type": "Point",
                "coordinates": [float(lon), float(lat)],
            },
        }

    if element_type != "way":
        return None

    raw_geometry = element.get("geometry")
    if not isinstance(raw_geometry, list):
        return None

    coordinates = [
        [float(point["lon"]), float(point["lat"])]
        for point in raw_geometry
        if isinstance(point, dict) and "lon" in point and "lat" in point
    ]
    if len(coordinates) < 2:
        return None

    geometry: dict[str, Any]
    if is_area_way(tags, coordinates):
        closed_coordinates = coordinates if is_closed_ring(coordinates) else coordinates + [coordinates[0]]
        geometry = {
            "type": "Polygon",
            "coordinates": [closed_coordinates],
        }
    else:
        geometry = {
            "type": "LineString",
            "coordinates": coordinates,
        }

    return {
        "type": "Feature",
        "properties": feature_properties,
        "geometry": geometry,
    }


def fetch_overpass_geojson(
    manifest_path: Path,
    endpoint: str,
    timeout_s: int,
    margin_m: float,
    retries: int,
) -> dict[str, Any]:
    _, _, reference_geometries = airport_reference_geometries(manifest_path)
    bbox = fetch_bbox(reference_geometries, margin_m)
    features: list[dict[str, Any]] = []
    seen_features: set[tuple[str, int]] = set()
    queries: list[str] = []
    warnings: list[str] = []

    for tag_filter in UNDERLAY_TAG_FILTERS:
        query = build_overpass_query(bbox, timeout_s, filters=(tag_filter,))
        queries.append(query)
        payload = urllib.parse.urlencode({"data": query}).encode("utf-8")
        request = urllib.request.Request(
            endpoint,
            data=payload,
            headers={"User-Agent": "twr2-airport-authoring/1.0"},
        )
        tag_key, tag_value = tag_filter
        tag_label = f'{tag_key}={tag_value}' if tag_value is not None else tag_key
        overpass_json: dict[str, Any] | None = None
        remaining_attempts = max(retries, 0) + 1
        for attempt in range(remaining_attempts):
            try:
                with urllib.request.urlopen(request, timeout=timeout_s + 10) as response:
                    overpass_json = json.loads(response.read().decode("utf-8"))
                break
            except urllib.error.HTTPError as error:
                if error.code in {429, 504} and attempt + 1 < remaining_attempts:
                    time.sleep(float(attempt + 1) * 2.0)
                    continue
                warnings.append(f"Overpass query failed for filter {tag_label}: HTTP {error.code}")
                overpass_json = None
                break
        if overpass_json is None:
            continue

        for element in overpass_json.get("elements", []):
            if not isinstance(element, dict):
                continue
            feature = overpass_element_to_feature(element)
            if feature is None:
                continue
            osm_type = feature["properties"].get("osm_type")
            osm_id = feature["properties"].get("osm_id")
            if not isinstance(osm_type, str) or not isinstance(osm_id, int):
                continue
            dedupe_key = (osm_type, osm_id)
            if dedupe_key in seen_features:
                continue
            seen_features.add(dedupe_key)
            features.append(feature)

    if not features:
        raise ValueError("Overpass query returned no usable OSM features.")

    return {
        "type": "FeatureCollection",
        "features": features,
        "metadata": {
            "source": "Overpass API",
            "endpoint": endpoint,
            "bbox": {
                "south": bbox[0],
                "west": bbox[1],
                "north": bbox[2],
                "east": bbox[3],
            },
            "queries": queries,
            "warnings": warnings,
        },
    }


def project_features(manifest_path: Path, geojson_path: Path) -> list[dict[str, Any]]:
    root = report.repo_root()
    manifest = report.load_manifest(manifest_path)
    apt_path = report.resolve_path(root, manifest["sources"]["aptDat"])
    _, _, _, _, apt_metadata, _ = report.parse_apt(apt_path)
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = report.projector(origin)

    raw = json.loads(geojson_path.read_text())
    features = raw.get("features", [])

    projected_features: list[dict[str, Any]] = []
    for feature in features:
        if not isinstance(feature, dict):
            continue
        geometry = feature.get("geometry")
        properties = feature.get("properties", {})
        if not isinstance(geometry, dict) or not isinstance(properties, dict):
            continue
        geometry_type = str(geometry.get("type", ""))
        layer = feature_layer(properties, geometry_type)
        label = label_text(properties)

        points = point_geometries(geometry, project)
        if points:
            projected_features.append(
                {
                    "layer": layer,
                    "kind": "point",
                    "points": [{"x": point.x, "y": point.y} for point in points],
                    "label": label,
                    "labelPoint": points[0],
                }
            )
            continue

        lines = line_paths(geometry, project)
        if lines:
            for line in lines:
                if len(line) < 2:
                    continue
                projected_features.append(
                    {
                        "layer": layer,
                        "kind": "line",
                        "points": [{"x": point.x, "y": point.y} for point in line],
                        "label": label,
                        "labelPoint": line_midpoint(line),
                    }
                )
            continue

        rings = polygon_rings(geometry, project)
        if rings:
            for ring in rings:
                projected_features.append(
                    {
                        "layer": layer,
                        "kind": "polygon",
                        "points": [{"x": point.x, "y": point.y} for point in ring],
                        "label": label,
                        "labelPoint": point_centroid(ring),
                    }
                )
    return projected_features


def project_geojson_features(manifest_path: Path, geojson: dict[str, Any]) -> list[dict[str, Any]]:
    root = report.repo_root()
    manifest = report.load_manifest(manifest_path)
    apt_path = report.resolve_path(root, manifest["sources"]["aptDat"])
    _, _, _, _, apt_metadata, _ = report.parse_apt(apt_path)
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = report.projector(origin)

    features = geojson.get("features", [])
    projected_features: list[dict[str, Any]] = []
    for feature in features:
        if not isinstance(feature, dict):
            continue
        geometry = feature.get("geometry")
        properties = feature.get("properties", {})
        if not isinstance(geometry, dict) or not isinstance(properties, dict):
            continue
        geometry_type = str(geometry.get("type", ""))
        layer = feature_layer(properties, geometry_type)
        label = label_text(properties)

        points = point_geometries(geometry, project)
        if points:
            projected_features.append(
                {
                    "layer": layer,
                    "kind": "point",
                    "points": [{"x": point.x, "y": point.y} for point in points],
                    "label": label,
                    "labelPoint": points[0],
                }
            )
            continue

        lines = line_paths(geometry, project)
        if lines:
            for line in lines:
                if len(line) < 2:
                    continue
                projected_features.append(
                    {
                        "layer": layer,
                        "kind": "line",
                        "points": [{"x": point.x, "y": point.y} for point in line],
                        "label": label,
                        "labelPoint": line_midpoint(line),
                    }
                )
            continue

        rings = polygon_rings(geometry, project)
        if rings:
            for ring in rings:
                projected_features.append(
                    {
                        "layer": layer,
                        "kind": "polygon",
                        "points": [{"x": point.x, "y": point.y} for point in ring],
                        "label": label,
                        "labelPoint": point_centroid(ring),
                    }
                )
    return projected_features


def write_entities(lines: list[str], features: list[dict[str, Any]], extmin: report.XY, extmax: report.XY) -> None:
    max_span = max(extmax.x - extmin.x, extmax.y - extmin.y)
    point_cross_half = max(max_span * 0.002, 24.0)
    label_height = max(max_span * 0.0026, 34.0)
    label_layer = "OSM_LABELS"

    lines.extend(dxf.dxf_pair(0, "SECTION") + dxf.dxf_pair(2, "ENTITIES"))
    for feature in features:
        points = [report.XY(float(point["x"]), float(point["y"])) for point in feature["points"]]
        layer = str(feature["layer"])
        kind = str(feature["kind"])
        if kind == "point":
            for point in points:
                dxf.write_point_entity(lines, layer, point)
                dxf.append_cross(lines, layer, point, point_cross_half)
        elif kind == "line":
            for start, end in zip(points, points[1:]):
                dxf.write_line_entity(lines, layer, start, end)
        elif kind == "polygon":
            for start, end in dxf.safe_close_boundary(points):
                dxf.write_line_entity(lines, layer, start, end)
        label = feature.get("label")
        label_point = feature.get("labelPoint")
        if isinstance(label, str) and label and isinstance(label_point, report.XY):
            dxf.write_text_entity(lines, label_layer, label_point, label, label_height)
    lines.extend(dxf.dxf_pair(0, "ENDSEC") + dxf.dxf_pair(0, "EOF"))


def export_osm_geojson_dxf(manifest_path: Path, geojson_path: Path, output_path: Path) -> None:
    features = project_features(manifest_path, geojson_path)
    if not features:
        raise ValueError("No usable GeoJSON features were found.")
    extmin, extmax = collect_extents(features)

    layer_colors = {"0": 7, "OSM_LABELS": 8}
    for feature in features:
        layer_colors[str(feature["layer"])] = layer_color(str(feature["layer"]))

    lines: list[str] = []
    dxf.write_header(lines, extmin, extmax)
    dxf.write_layers(lines, layer_colors)
    write_entities(lines, features, extmin, extmax)
    output_path.write_text("\n".join(lines) + "\n")


def export_osm_feature_collection_dxf(manifest_path: Path, geojson: dict[str, Any], output_path: Path) -> None:
    features = project_geojson_features(manifest_path, geojson)
    if not features:
        raise ValueError("No usable GeoJSON features were found.")
    extmin, extmax = collect_extents(features)

    layer_colors = {"0": 7, "OSM_LABELS": 8}
    for feature in features:
        layer_colors[str(feature["layer"])] = layer_color(str(feature["layer"]))

    lines: list[str] = []
    dxf.write_header(lines, extmin, extmax)
    dxf.write_layers(lines, layer_colors)
    write_entities(lines, features, extmin, extmax)
    output_path.write_text("\n".join(lines) + "\n")


def main() -> None:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    airport_code = report.load_manifest(manifest_path)["airportCode"].lower()
    default_output = report.repo_root() / "cad/airports" / f"{airport_code}_osm_underlay.dxf"
    output_path = args.output.resolve() if args.output is not None else default_output
    if args.fetch_overpass and args.geojson is not None:
        raise SystemExit("Provide either a GeoJSON file or --fetch-overpass, not both.")
    if not args.fetch_overpass and args.geojson is None:
        raise SystemExit("Provide a GeoJSON file or use --fetch-overpass.")

    if args.fetch_overpass:
        geojson_cache_path = (
            args.geojson_cache.resolve()
            if args.geojson_cache is not None
            else report.repo_root() / "cad/airports" / f"{airport_code}_osm_underlay.geojson"
        )
        fetched_geojson = fetch_overpass_geojson(
            manifest_path=manifest_path,
            endpoint=args.overpass_endpoint,
            timeout_s=args.timeout_s,
            margin_m=args.margin_m,
            retries=args.retries,
        )
        geojson_cache_path.write_text(json.dumps(fetched_geojson, indent=2) + "\n")
        export_osm_feature_collection_dxf(manifest_path, fetched_geojson, output_path)
    else:
        geojson_path = args.geojson.resolve()
        export_osm_geojson_dxf(manifest_path, geojson_path, output_path)
    print(output_path)


if __name__ == "__main__":
    main()
