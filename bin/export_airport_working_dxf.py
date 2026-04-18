#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report
import export_osm_geojson_dxf as osm_underlay
import render_airport_authoring as authoring


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export a combined working DXF with X-Plane baseline layers plus authored airport layers.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "--no-raster-osm",
        action="store_true",
        help="Do not embed the cached raster OSM underlay image reference into the DXF.",
    )
    parser.add_argument(
        "--include-vector-osm",
        action="store_true",
        help="Include the cached OSM vector underlay as REF_OSM_* layers. Off by default; raster underlays are preferred.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="DXF output path. Defaults to cad/airports/<airport>_working_combined.dxf.",
    )
    return parser.parse_args()


def midpoint(point_a: report.XY, point_b: report.XY) -> report.XY:
    return report.XY((point_a.x + point_b.x) / 2.0, (point_a.y + point_b.y) / 2.0)


def route_layer_name(kind: str) -> str:
    if kind == "runway":
        return "REF_ROUTE_RUNWAYS"
    if kind.startswith("taxiway_"):
        return f"REF_ROUTE_TWY_{kind.split('_', 1)[1].upper()}"
    return f"REF_ROUTE_{kind.upper()}"


def route_color_index(kind: str) -> int:
    return {
        "runway": 1,
        "taxiway_A": 3,
        "taxiway_B": 4,
        "taxiway_D": 2,
        "taxiway_E": 6,
    }.get(kind, 8)


def branch_color_index(branch_name: str) -> int:
    return {
        "G1": 3,
        "S1": 5,
        "X": 1,
    }.get(branch_name, 8)


def route_edge_label(edge: authoring.ProjectedTaxiRouteEdge) -> str | None:
    if edge.kind == "runway":
        return None
    if not edge.name:
        return None
    if edge.kind.startswith("taxiway_"):
        parent = edge.kind.split("_", 1)[1].upper()
        return f"{parent}/{edge.name}"
    return edge.name


def collect_extents(context: authoring.SceneContext) -> tuple[report.XY, report.XY]:
    points: list[report.XY] = []
    for runway in context.apt_runways:
        points.extend([runway.start, runway.end])
    if context.tower_xy is not None:
        points.append(context.tower_xy)
    points.extend(context.taxi_nodes)
    for edge in context.taxi_route_edges:
        points.extend([edge.start, edge.end])
    for sign in context.taxi_signs:
        points.append(sign.point)
    for parking in context.parking_positions:
        points.append(parking.point)
    for branch in context.parking_access_branches:
        for edge in branch.edges:
            points.extend([edge.start, edge.end])
        for connector in branch.connectors:
            points.extend([connector.stand_point, connector.attach_point])
    for reporting_point in context.reporting_points:
        points.append(reporting_point.point)
    for line in context.ground_lines:
        points.extend([line.start, line.end])
    for point in context.ground_points:
        points.append(point.point)
    for line in context.circuit_lines:
        points.extend([line.start, line.end])
    for point in context.circuit_points:
        points.append(point.point)
    for anchor in context.procedure_anchors:
        points.append(anchor.point)
    for line in context.working_airspace_sector_lines:
        points.extend([line.start, line.end])
    min_x = min(point.x for point in points)
    max_x = max(point.x for point in points)
    min_y = min(point.y for point in points)
    max_y = max(point.y for point in points)
    margin = max(max_x - min_x, max_y - min_y) * 0.06
    return report.XY(min_x - margin, min_y - margin), report.XY(max_x + margin, max_y + margin)


def collect_feature_extents(features: list[dict[str, object]]) -> tuple[report.XY, report.XY] | None:
    points = [
        report.XY(float(point["x"]), float(point["y"]))
        for feature in features
        for point in feature.get("points", [])
    ]
    if not points:
        return None
    min_x = min(point.x for point in points)
    max_x = max(point.x for point in points)
    min_y = min(point.y for point in points)
    max_y = max(point.y for point in points)
    margin = max(max_x - min_x, max_y - min_y) * 0.04
    return report.XY(min_x - margin, min_y - margin), report.XY(max_x + margin, max_y + margin)


def combine_extents(
    primary: tuple[report.XY, report.XY],
    secondary: tuple[report.XY, report.XY] | None,
) -> tuple[report.XY, report.XY]:
    if secondary is None:
        return primary
    return (
        report.XY(
            min(primary[0].x, secondary[0].x),
            min(primary[0].y, secondary[0].y),
        ),
        report.XY(
            max(primary[1].x, secondary[1].x),
            max(primary[1].y, secondary[1].y),
        ),
    )


def optional_osm_underlay_features(manifest_path: Path) -> list[dict[str, object]]:
    airport_code = report.load_manifest(manifest_path)["airportCode"].lower()
    geojson_path = report.repo_root() / "cad/airports" / f"{airport_code}_osm_underlay.geojson"
    if not geojson_path.exists():
        return []
    return [
        {
            **feature,
            "layer": f"REF_{str(feature['layer'])}",
            "labelLayer": "REF_OSM_LABELS",
        }
        for feature in osm_underlay.project_features(manifest_path, geojson_path)
    ]


def write_reference_entities(
    modelspace,
    context: authoring.SceneContext,
    osm_features: list[dict[str, object]],
    extmin: report.XY,
    extmax: report.XY,
) -> None:
    max_span = max(extmax.x - extmin.x, extmax.y - extmin.y)
    point_cross_half = max(max_span * 0.0025, 28.0)
    label_height = max(max_span * 0.0032, 44.0)
    small_label_height = max(label_height * 0.72, 30.0)
    label_offset = max(point_cross_half * 1.5, 42.0)

    for runway in context.apt_runways:
        modelspace.add_line((runway.start.x, runway.start.y), (runway.end.x, runway.end.y), dxfattribs={"layer": "REF_RUNWAYS"})
        modelspace.add_text(
            runway.pair,
            dxfattribs={
                "layer": "REF_RUNWAY_LABELS",
                "height": label_height,
                "insert": (midpoint(runway.start, runway.end).x, midpoint(runway.start, runway.end).y + label_offset),
            },
        )

    if context.tower_xy is not None:
        modelspace.add_point((context.tower_xy.x, context.tower_xy.y), dxfattribs={"layer": "REF_TOWER"})
        append_cross_entities(modelspace, "REF_TOWER", context.tower_xy, point_cross_half)
        modelspace.add_text(
            "TWR",
            dxfattribs={
                "layer": "REF_TOWER_LABELS",
                "height": label_height,
                "insert": (context.tower_xy.x + label_offset, context.tower_xy.y + label_offset),
            },
        )

    for node in context.taxi_nodes:
        modelspace.add_point((node.x, node.y), dxfattribs={"layer": "REF_TAXI_NODES"})

    for edge in context.taxi_route_edges:
        modelspace.add_line((edge.start.x, edge.start.y), (edge.end.x, edge.end.y), dxfattribs={"layer": route_layer_name(edge.kind)})
        label = route_edge_label(edge)
        if label is not None:
            midpoint_xy = midpoint(edge.start, edge.end)
            modelspace.add_text(
                label,
                dxfattribs={
                    "layer": "REF_ROUTE_LABELS",
                    "height": small_label_height,
                    "insert": (midpoint_xy.x, midpoint_xy.y),
                },
            )

    for sign in context.taxi_signs:
        modelspace.add_point((sign.point.x, sign.point.y), dxfattribs={"layer": "REF_TAXI_SIGNS"})
        append_cross_entities(modelspace, "REF_TAXI_SIGNS", sign.point, point_cross_half * 0.7)
        modelspace.add_text(
            sign.label,
            dxfattribs={
                "layer": "REF_TAXI_SIGN_LABELS",
                "height": small_label_height,
                "insert": (sign.point.x + label_offset, sign.point.y + (label_offset * 0.6)),
            },
        )

    for parking in context.parking_positions:
        modelspace.add_point((parking.point.x, parking.point.y), dxfattribs={"layer": "REF_PARKING"})
        append_cross_entities(modelspace, "REF_PARKING", parking.point, point_cross_half * 0.5)
        modelspace.add_text(
            parking.name,
            dxfattribs={
                "layer": "REF_PARKING_LABELS",
                "height": small_label_height,
                "insert": (parking.point.x + label_offset, parking.point.y - (label_offset * 0.4)),
            },
        )

    for branch in context.parking_access_branches:
        layer_name = f"REF_BRANCH_{branch.parent_taxiway}_{branch.branch_name or branch.parent_taxiway}"
        for edge in branch.edges:
            modelspace.add_line((edge.start.x, edge.start.y), (edge.end.x, edge.end.y), dxfattribs={"layer": layer_name})
        for connector in branch.connectors:
            modelspace.add_line(
                (connector.stand_point.x, connector.stand_point.y),
                (connector.attach_point.x, connector.attach_point.y),
                dxfattribs={"layer": layer_name},
            )
        branch_points = [edge.start for edge in branch.edges] + [edge.end for edge in branch.edges]
        if branch_points:
            centroid = report.XY(
                sum(point.x for point in branch_points) / len(branch_points),
                sum(point.y for point in branch_points) / len(branch_points),
            )
            modelspace.add_text(
                branch.display_name,
                dxfattribs={
                    "layer": "REF_BRANCH_LABELS",
                    "height": small_label_height,
                    "insert": (centroid.x, centroid.y),
                },
            )

    for reporting_point in context.reporting_points:
        modelspace.add_point((reporting_point.point.x, reporting_point.point.y), dxfattribs={"layer": "REF_VFR_POINTS"})
        append_cross_entities(modelspace, "REF_VFR_POINTS", reporting_point.point, point_cross_half)
        modelspace.add_text(
            reporting_point.code_id,
            dxfattribs={
                "layer": "REF_VFR_LABELS",
                "height": label_height,
                "insert": (reporting_point.point.x + label_offset, reporting_point.point.y + label_offset),
            },
        )

    osm_point_cross_half = max(point_cross_half * 0.45, 12.0)
    osm_label_height = max(small_label_height * 0.8, 18.0)
    for feature in osm_features:
        feature_points = [
            report.XY(float(point["x"]), float(point["y"]))
            for point in feature.get("points", [])
        ]
        if not feature_points:
            continue
        layer_name = str(feature["layer"])
        feature_kind = str(feature["kind"])
        if feature_kind == "point":
            for point in feature_points:
                modelspace.add_point((point.x, point.y), dxfattribs={"layer": layer_name})
                append_cross_entities(modelspace, layer_name, point, osm_point_cross_half)
        elif feature_kind == "line":
            for start, end in zip(feature_points, feature_points[1:]):
                modelspace.add_line((start.x, start.y), (end.x, end.y), dxfattribs={"layer": layer_name})
        elif feature_kind == "polygon":
            for start, end in safe_close_boundary(feature_points):
                modelspace.add_line((start.x, start.y), (end.x, end.y), dxfattribs={"layer": layer_name})
        label = feature.get("label")
        label_point = feature.get("labelPoint")
        if isinstance(label, str) and label and isinstance(label_point, report.XY):
            modelspace.add_text(
                label,
                dxfattribs={
                    "layer": str(feature["labelLayer"]),
                    "height": osm_label_height,
                    "insert": (label_point.x, label_point.y),
                },
            )


def write_authored_entities(
    modelspace,
    context: authoring.SceneContext,
    extmin: report.XY,
    extmax: report.XY,
) -> None:
    max_span = max(extmax.x - extmin.x, extmax.y - extmin.y)
    point_cross_half = max(max_span * 0.0025, 28.0)
    label_height = max(max_span * 0.0032, 44.0)
    label_offset = max(point_cross_half * 1.5, 42.0)

    for line in context.ground_lines:
        modelspace.add_line((line.start.x, line.start.y), (line.end.x, line.end.y), dxfattribs={"layer": "AUTH_GROUND"})
    for point in context.ground_points:
        modelspace.add_point((point.point.x, point.point.y), dxfattribs={"layer": "AUTH_GROUND_POINTS"})
        append_cross_entities(modelspace, "AUTH_GROUND_POINTS", point.point, point_cross_half * 0.8)

    for line in context.circuit_lines:
        modelspace.add_line((line.start.x, line.start.y), (line.end.x, line.end.y), dxfattribs={"layer": "AUTH_VFR_CIRCUIT"})
    for point in context.circuit_points:
        modelspace.add_point((point.point.x, point.point.y), dxfattribs={"layer": "AUTH_VFR_CIRCUIT_POINTS"})
        append_cross_entities(modelspace, "AUTH_VFR_CIRCUIT_POINTS", point.point, point_cross_half * 0.8)

    for anchor in context.procedure_anchors:
        modelspace.add_point((anchor.point.x, anchor.point.y), dxfattribs={"layer": "AUTH_PROCEDURE_ANCHORS"})
        append_cross_entities(modelspace, "AUTH_PROCEDURE_ANCHORS", anchor.point, point_cross_half)
        modelspace.add_text(
            anchor.label,
            dxfattribs={
                "layer": "AUTH_PROCEDURE_ANCHOR_LABELS",
                "height": label_height,
                "insert": (anchor.point.x + label_offset, anchor.point.y + label_offset),
            },
        )

    for line in context.working_airspace_sector_lines:
        modelspace.add_line((line.start.x, line.start.y), (line.end.x, line.end.y), dxfattribs={"layer": "AUTH_VFR_OP_SECTORS"})


def append_cross_entities(modelspace, layer: str, point: report.XY, half_size: float) -> None:
    modelspace.add_line(
        (point.x - half_size, point.y - half_size),
        (point.x + half_size, point.y + half_size),
        dxfattribs={"layer": layer},
    )
    modelspace.add_line(
        (point.x - half_size, point.y + half_size),
        (point.x + half_size, point.y - half_size),
        dxfattribs={"layer": layer},
    )


def safe_close_boundary(points: list[report.XY], tolerance: float = 1e-6) -> list[tuple[report.XY, report.XY]]:
    if len(points) < 2:
        return []
    pairs = list(zip(points, points[1:]))
    if points[0].distance_to(points[-1]) > tolerance:
        pairs.append((points[-1], points[0]))
    return pairs


def read_raster_underlay_placement(manifest_path: Path) -> dict[str, object] | None:
    airport_code = report.load_manifest(manifest_path)["airportCode"].lower()
    placement_path = report.repo_root() / "cad/airports" / f"{airport_code}_osm_underlay_placement.json"
    if not placement_path.exists():
        return None
    return json.loads(placement_path.read_text())


def load_preserved_new_layers(path: Path) -> tuple[dict[str, int], list[dict[str, object]], tuple[report.XY, report.XY] | None]:
    try:
        import ezdxf
    except ImportError:
        return {}, [], None

    if not path.exists():
        return {}, [], None

    existing = ezdxf.readfile(path)
    layer_colors = {
        layer.dxf.name: int(layer.dxf.color)
        for layer in existing.layers
        if layer.dxf.name.startswith("NEW_")
    }
    preserved_entities: list[dict[str, object]] = []
    extent_points: list[report.XY] = []

    def xy_from_vec(vec) -> report.XY:
        return report.XY(float(vec[0]), float(vec[1]))

    for entity in existing.modelspace():
        layer_name = entity.dxf.layer
        if not isinstance(layer_name, str) or not layer_name.startswith("NEW_"):
            continue
        entity_type = entity.dxftype()
        if entity_type == "LINE":
            start = xy_from_vec(entity.dxf.start)
            end = xy_from_vec(entity.dxf.end)
            preserved_entities.append({"type": "LINE", "layer": layer_name, "start": start, "end": end})
            extent_points.extend((start, end))
        elif entity_type == "POINT":
            point = xy_from_vec(entity.dxf.location)
            preserved_entities.append({"type": "POINT", "layer": layer_name, "point": point})
            extent_points.append(point)
        elif entity_type == "TEXT":
            insert = xy_from_vec(entity.dxf.insert)
            preserved_entities.append(
                {
                    "type": "TEXT",
                    "layer": layer_name,
                    "insert": insert,
                    "text": entity.dxf.text,
                    "height": float(entity.dxf.height),
                },
            )
            extent_points.append(insert)
        elif entity_type == "LWPOLYLINE":
            points = [report.XY(float(x), float(y)) for x, y, *_ in entity.get_points("xy")]
            preserved_entities.append(
                {
                    "type": "LWPOLYLINE",
                    "layer": layer_name,
                    "points": points,
                    "closed": bool(entity.closed),
                },
            )
            extent_points.extend(points)
        elif entity_type == "CIRCLE":
            center = xy_from_vec(entity.dxf.center)
            radius = float(entity.dxf.radius)
            preserved_entities.append(
                {"type": "CIRCLE", "layer": layer_name, "center": center, "radius": radius},
            )
            extent_points.extend(
                [
                    report.XY(center.x - radius, center.y - radius),
                    report.XY(center.x + radius, center.y + radius),
                ],
            )
        elif entity_type == "ARC":
            center = xy_from_vec(entity.dxf.center)
            radius = float(entity.dxf.radius)
            preserved_entities.append(
                {
                    "type": "ARC",
                    "layer": layer_name,
                    "center": center,
                    "radius": radius,
                    "start_angle": float(entity.dxf.start_angle),
                    "end_angle": float(entity.dxf.end_angle),
                },
            )
            extent_points.extend(
                [
                    report.XY(center.x - radius, center.y - radius),
                    report.XY(center.x + radius, center.y + radius),
                ],
            )

    if not extent_points:
        return layer_colors, preserved_entities, None

    min_x = min(point.x for point in extent_points)
    max_x = max(point.x for point in extent_points)
    min_y = min(point.y for point in extent_points)
    max_y = max(point.y for point in extent_points)
    margin = max(max_x - min_x, max_y - min_y, 1.0) * 0.04
    return (
        layer_colors,
        preserved_entities,
        (
            report.XY(min_x - margin, min_y - margin),
            report.XY(max_x + margin, max_y + margin),
        ),
    )


def write_preserved_new_entities(modelspace, entities: list[dict[str, object]]) -> None:
    for entity in entities:
        entity_type = str(entity["type"])
        layer_name = str(entity["layer"])
        if entity_type == "LINE":
            start = entity["start"]
            end = entity["end"]
            modelspace.add_line((start.x, start.y), (end.x, end.y), dxfattribs={"layer": layer_name})
        elif entity_type == "POINT":
            point = entity["point"]
            modelspace.add_point((point.x, point.y), dxfattribs={"layer": layer_name})
        elif entity_type == "TEXT":
            insert = entity["insert"]
            modelspace.add_text(
                str(entity["text"]),
                dxfattribs={
                    "layer": layer_name,
                    "height": float(entity["height"]),
                    "insert": (insert.x, insert.y),
                },
            )
        elif entity_type == "LWPOLYLINE":
            points = [(point.x, point.y) for point in entity["points"]]
            modelspace.add_lwpolyline(points, close=bool(entity["closed"]), dxfattribs={"layer": layer_name})
        elif entity_type == "CIRCLE":
            center = entity["center"]
            modelspace.add_circle((center.x, center.y), float(entity["radius"]), dxfattribs={"layer": layer_name})
        elif entity_type == "ARC":
            center = entity["center"]
            modelspace.add_arc(
                (center.x, center.y),
                float(entity["radius"]),
                float(entity["start_angle"]),
                float(entity["end_angle"]),
                dxfattribs={"layer": layer_name},
            )


def export_airport_working_dxf(
    manifest_path: Path,
    output_path: Path,
    include_vector_osm: bool,
    include_raster_osm: bool,
) -> None:
    try:
        import ezdxf
    except ImportError as error:
        raise SystemExit(
            "ezdxf is required for the working DXF export. Run via `nix-shell -p python3 python3Packages.ezdxf --run ...`."
        ) from error

    context = authoring.build_context(manifest_path)
    osm_features = optional_osm_underlay_features(manifest_path) if include_vector_osm else []
    raster_placement = read_raster_underlay_placement(manifest_path) if include_raster_osm else None
    preserved_new_layer_colors, preserved_new_entities, preserved_new_extents = load_preserved_new_layers(output_path)
    raster_extents = None
    if isinstance(raster_placement, dict):
        bounds = raster_placement.get("boundsMeters")
        if isinstance(bounds, dict):
            raster_extents = (
                report.XY(float(bounds["minX"]), float(bounds["minY"])),
                report.XY(float(bounds["maxX"]), float(bounds["maxY"])),
            )
    extmin, extmax = combine_extents(
        combine_extents(
            combine_extents(collect_extents(context), collect_feature_extents(osm_features)),
            preserved_new_extents,
        ),
        raster_extents,
    )

    layer_colors: dict[str, int] = {
        "0": 7,
        "REF_OSM_RASTER": 8,
        "REF_RUNWAYS": 8,
        "REF_RUNWAY_LABELS": 8,
        "REF_TOWER": 7,
        "REF_TOWER_LABELS": 7,
        "REF_TAXI_NODES": 9,
        "REF_ROUTE_LABELS": 8,
        "REF_TAXI_SIGNS": 2,
        "REF_TAXI_SIGN_LABELS": 2,
        "REF_PARKING": 7,
        "REF_PARKING_LABELS": 7,
        "REF_BRANCH_LABELS": 5,
        "REF_VFR_POINTS": 3,
        "REF_VFR_LABELS": 3,
        "AUTH_GROUND": 1,
        "AUTH_GROUND_POINTS": 1,
        "AUTH_VFR_CIRCUIT": 5,
        "AUTH_VFR_CIRCUIT_POINTS": 5,
        "AUTH_PROCEDURE_ANCHORS": 6,
        "AUTH_PROCEDURE_ANCHOR_LABELS": 6,
        "AUTH_VFR_OP_SECTORS": 4,
    }
    for edge in context.taxi_route_edges:
        layer_colors[route_layer_name(edge.kind)] = route_color_index(edge.kind)
    for branch in context.parking_access_branches:
        layer_name = f"REF_BRANCH_{branch.parent_taxiway}_{branch.branch_name or branch.parent_taxiway}"
        layer_colors[layer_name] = branch_color_index(branch.branch_name)
    for feature in osm_features:
        layer_name = str(feature["layer"])
        layer_colors[layer_name] = osm_underlay.layer_color(layer_name.removeprefix("REF_"))
    if osm_features:
        layer_colors["REF_OSM_LABELS"] = 8
    for layer_name, color in preserved_new_layer_colors.items():
        layer_colors[layer_name] = color

    doc = ezdxf.new("R2000")
    doc.header["$INSBASE"] = (0.0, 0.0, 0.0)
    doc.header["$EXTMIN"] = (extmin.x, extmin.y, 0.0)
    doc.header["$EXTMAX"] = (extmax.x, extmax.y, 0.0)

    existing_layers = {layer.dxf.name for layer in doc.layers}
    for layer_name, color in layer_colors.items():
        if layer_name in existing_layers:
            doc.layers.get(layer_name).color = color
        else:
            doc.layers.add(layer_name, color=color)

    modelspace = doc.modelspace()
    write_reference_entities(modelspace, context, osm_features, extmin, extmax)
    write_authored_entities(modelspace, context, extmin, extmax)
    write_preserved_new_entities(modelspace, preserved_new_entities)

    if isinstance(raster_placement, dict):
        image_path = Path(str(raster_placement["imagePath"]))
        image_size = raster_placement.get("imageSizePx")
        placement = raster_placement.get("recommendedPlacement")
        lower_left = placement.get("lowerLeft") if isinstance(placement, dict) else None
        if (
            image_path.exists()
            and isinstance(image_size, dict)
            and isinstance(placement, dict)
            and isinstance(lower_left, dict)
        ):
            image_relative_path = os.path.relpath(image_path, output_path.parent)
            image_def = doc.add_image_def(
                filename=image_relative_path,
                size_in_pixel=(int(image_size["width"]), int(image_size["height"])),
            )
            modelspace.add_image(
                insert=(float(lower_left["x"]), float(lower_left["y"])),
                size_in_units=(float(placement["widthMeters"]), float(placement["heightMeters"])),
                image_def=image_def,
                rotation=0.0,
                dxfattribs={"layer": "REF_OSM_RASTER"},
            )

    doc.saveas(output_path)


def main() -> None:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    airport_code = report.load_manifest(manifest_path)["airportCode"].lower()
    default_output = report.repo_root() / "cad/airports" / f"{airport_code}_working_combined.dxf"
    output_path = args.output.resolve() if args.output is not None else default_output
    export_airport_working_dxf(
        manifest_path,
        output_path,
        include_vector_osm=args.include_vector_osm,
        include_raster_osm=not args.no_raster_osm,
    )
    print(output_path)


if __name__ == "__main__":
    main()
