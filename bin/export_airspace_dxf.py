#!/usr/bin/env python3

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report
import simple_dxf as dxf


@dataclass(frozen=True)
class RunwayShape:
    pair: str
    start: report.XY
    end: report.XY


@dataclass(frozen=True)
class ReportingPointShape:
    code_id: str
    point: report.XY


@dataclass(frozen=True)
class AirspaceShape:
    layer_name: str
    label: str
    color_index: int
    boundaries: list[list[report.XY]]
    has_curve_vertices: bool


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export airport OFMX airspace and VFR points to a simple editable DXF.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="DXF output path. Defaults to cad/airports/<airport>_airspace_working.dxf.",
    )
    return parser.parse_args()


def centroid(points: list[report.XY]) -> report.XY:
    return report.XY(
        x=sum(point.x for point in points) / len(points),
        y=sum(point.y for point in points) / len(points),
    )


def unique_runways(runways: dict[str, report.RunwayRecord], project) -> list[RunwayShape]:
    unique_records = {id(record): record for record in runways.values()}
    runway_shapes = [
        RunwayShape(
            pair=f"{record.designator_a}/{record.designator_b}",
            start=project(record.end_a),
            end=project(record.end_b),
        )
        for record in unique_records.values()
    ]
    return sorted(runway_shapes, key=lambda runway: runway.pair)


def airspace_display_label(airspace: report.OfmxAirspace) -> str:
    if airspace.name == "LOWG" and airspace.code_id == "LO585":
        return "LOWG CTR"
    pieces = [piece for piece in [airspace.code_id, airspace.name] if piece]
    return " ".join(pieces) if pieces else airspace.mid


def build_shapes(manifest_path: Path) -> tuple[list[RunwayShape], report.XY | None, list[ReportingPointShape], list[AirspaceShape]]:
    root = report.repo_root()
    manifest = report.load_manifest(manifest_path)

    apt_path = report.resolve_path(root, manifest["sources"]["aptDat"])
    ofmx_path = report.resolve_path(root, manifest["sources"]["ofmx"])
    runways, tower, _taxi_nodes, _taxi_edges, apt_metadata, _parking_positions = report.parse_apt(apt_path)
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = report.projector(origin)
    ofmx_data = report.parse_ofmx(ofmx_path, manifest["airportCode"])

    reporting_points = sorted(
        [
            ReportingPointShape(code_id=point.code_id, point=project(point.position))
            for point in ofmx_data["airportDesignatedPoints"]
            if (point.code_type or "").startswith("VFR")
        ],
        key=lambda point: point.code_id,
    )

    used_layer_names = {"0", "DEFPOINTS", "RUNWAYS", "RUNWAY_LABELS", "TOWER", "TOWER_LABELS", "VFR_POINTS", "VFR_LABELS", "AIRSPACE_LABELS"}
    airspace_shapes: list[AirspaceShape] = []
    for airspace in ofmx_data["airspaces"]:
        boundaries = ofmx_data["airspaceBoundaries"].get(airspace.mid, [])
        if not boundaries:
            continue
        category_color = 4 if (airspace.name or "").startswith(manifest["airportCode"]) else 6
        layer_name = dxf.sanitize_layer_name(
            f"ASP_{airspace.code_id or airspace.name or airspace.mid}",
            used_layer_names,
        )
        projected_boundaries = [
            [project(vertex.position) for vertex in boundary.vertices]
            for boundary in boundaries
            if len(boundary.vertices) >= 2
        ]
        if not projected_boundaries:
            continue
        airspace_shapes.append(
            AirspaceShape(
                layer_name=layer_name,
                label=airspace_display_label(airspace),
                color_index=category_color,
                boundaries=projected_boundaries,
                has_curve_vertices=any(
                    vertex.code_type != "GRC"
                    for boundary in boundaries
                    for vertex in boundary.vertices
                ),
            ),
        )

    return (
        unique_runways(runways, project),
        project(tower.position) if tower is not None else None,
        reporting_points,
        airspace_shapes,
    )


def collect_extents(
    runways: list[RunwayShape],
    tower_xy: report.XY | None,
    reporting_points: list[ReportingPointShape],
    airspaces: list[AirspaceShape],
) -> tuple[report.XY, report.XY]:
    points: list[report.XY] = []
    for runway in runways:
        points.extend([runway.start, runway.end])
    if tower_xy is not None:
        points.append(tower_xy)
    points.extend(point.point for point in reporting_points)
    for airspace in airspaces:
        for boundary in airspace.boundaries:
            points.extend(boundary)
    min_x = min(point.x for point in points)
    max_x = max(point.x for point in points)
    min_y = min(point.y for point in points)
    max_y = max(point.y for point in points)
    margin = max(max_x - min_x, max_y - min_y) * 0.04
    return report.XY(min_x - margin, min_y - margin), report.XY(max_x + margin, max_y + margin)


def write_header(lines: list[str], extmin: report.XY, extmax: report.XY) -> None:
    dxf.write_header(lines, extmin, extmax)


def write_layers(lines: list[str], layer_colors: dict[str, int]) -> None:
    dxf.write_layers(lines, layer_colors)


def write_line_entity(lines: list[str], layer: str, start: report.XY, end: report.XY) -> None:
    dxf.write_line_entity(lines, layer, start, end)


def write_point_entity(lines: list[str], layer: str, point: report.XY) -> None:
    dxf.write_point_entity(lines, layer, point)


def write_text_entity(lines: list[str], layer: str, point: report.XY, text: str, height: float) -> None:
    dxf.write_text_entity(lines, layer, point, text, height)


def append_cross(lines: list[str], layer: str, point: report.XY, half_size: float) -> None:
    dxf.append_cross(lines, layer, point, half_size)


def write_entities(
    lines: list[str],
    runways: list[RunwayShape],
    tower_xy: report.XY | None,
    reporting_points: list[ReportingPointShape],
    airspaces: list[AirspaceShape],
    extmin: report.XY,
    extmax: report.XY,
) -> None:
    max_span = max(extmax.x - extmin.x, extmax.y - extmin.y)
    point_cross_half = max(max_span * 0.004, 80.0)
    label_height = max(max_span * 0.005, 130.0)
    airspace_label_height = max(max_span * 0.006, 160.0)
    label_offset = max(point_cross_half * 1.4, 120.0)

    lines.extend(dxf_pair(0, "SECTION") + dxf_pair(2, "ENTITIES"))

    for runway in runways:
        write_line_entity(lines, "RUNWAYS", runway.start, runway.end)
        midpoint = report.XY((runway.start.x + runway.end.x) / 2.0, (runway.start.y + runway.end.y) / 2.0)
        write_text_entity(lines, "RUNWAY_LABELS", report.XY(midpoint.x, midpoint.y + label_offset), runway.pair, label_height)

    if tower_xy is not None:
        write_point_entity(lines, "TOWER", tower_xy)
        append_cross(lines, "TOWER", tower_xy, point_cross_half)
        write_text_entity(lines, "TOWER_LABELS", report.XY(tower_xy.x + label_offset, tower_xy.y + label_offset), "TWR", label_height)

    for point in reporting_points:
        write_point_entity(lines, "VFR_POINTS", point.point)
        append_cross(lines, "VFR_POINTS", point.point, point_cross_half)
        write_text_entity(lines, "VFR_LABELS", report.XY(point.point.x + label_offset, point.point.y + label_offset), point.code_id, label_height)

    for airspace in airspaces:
        label_points = [point for boundary in airspace.boundaries for point in boundary]
        for boundary in airspace.boundaries:
            for start, end in dxf.safe_close_boundary(boundary):
                write_line_entity(lines, airspace.layer_name, start, end)
        label = airspace.label if not airspace.has_curve_vertices else f"{airspace.label} (FNT)"
        write_text_entity(lines, "AIRSPACE_LABELS", centroid(label_points), label, airspace_label_height)

    lines.extend(dxf_pair(0, "ENDSEC") + dxf_pair(0, "EOF"))


def export_airspace_dxf(manifest_path: Path, output_path: Path) -> None:
    runways, tower_xy, reporting_points, airspaces = build_shapes(manifest_path)
    extmin, extmax = collect_extents(runways, tower_xy, reporting_points, airspaces)

    layer_colors = {
        "0": 7,
        "RUNWAYS": 8,
        "RUNWAY_LABELS": 8,
        "TOWER": 7,
        "TOWER_LABELS": 7,
        "VFR_POINTS": 3,
        "VFR_LABELS": 3,
        "AIRSPACE_LABELS": 2,
    }
    for airspace in airspaces:
        layer_colors[airspace.layer_name] = airspace.color_index

    lines: list[str] = []
    write_header(lines, extmin, extmax)
    write_layers(lines, layer_colors)
    write_entities(lines, runways, tower_xy, reporting_points, airspaces, extmin, extmax)

    output_path.write_text("\n".join(lines) + "\n")


def main() -> None:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    airport_code = report.load_manifest(manifest_path)["airportCode"].lower()
    default_output = report.repo_root() / "cad/airports" / f"{airport_code}_airspace_working.dxf"
    output_path = args.output.resolve() if args.output is not None else default_output
    export_airspace_dxf(manifest_path, output_path)
    print(output_path)


if __name__ == "__main__":
    main()
