#!/usr/bin/env python3

from __future__ import annotations

import argparse
from pathlib import Path

import airport_authoring_report as report
import render_airport_authoring as authoring
import simple_dxf as dxf


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export a baseline airport DXF from X-Plane airport data plus OFMX VFR points.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="DXF output path. Defaults to cad/airports/<airport>_xplane_baseline.dxf.",
    )
    return parser.parse_args()


def midpoint(point_a: report.XY, point_b: report.XY) -> report.XY:
    return report.XY((point_a.x + point_b.x) / 2.0, (point_a.y + point_b.y) / 2.0)


def route_layer_name(kind: str) -> str:
    if kind == "runway":
        return "XP_ROUTE_RUNWAYS"
    if kind.startswith("taxiway_"):
        return f"XP_ROUTE_TWY_{kind.split('_', 1)[1].upper()}"
    return f"XP_ROUTE_{kind.upper()}"


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


def collect_extents(
    runways: list[authoring.RunwayShape],
    tower_xy: report.XY | None,
    taxi_nodes: list[report.XY],
    taxi_route_edges: list[authoring.ProjectedTaxiRouteEdge],
    taxi_signs: list[authoring.TaxiSignShape],
    parking_positions: list[authoring.ProjectedParkingPosition],
    parking_access_branches: list[authoring.ParkingAccessBranch],
    reporting_points: list[authoring.ReportingPoint],
) -> tuple[report.XY, report.XY]:
    points: list[report.XY] = []
    for runway in runways:
        points.extend([runway.start, runway.end])
    if tower_xy is not None:
        points.append(tower_xy)
    points.extend(taxi_nodes)
    for edge in taxi_route_edges:
        points.extend([edge.start, edge.end])
    for sign in taxi_signs:
        points.append(sign.point)
    for parking in parking_positions:
        points.append(parking.point)
    for branch in parking_access_branches:
        for edge in branch.edges:
            points.extend([edge.start, edge.end])
        for connector in branch.connectors:
            points.extend([connector.stand_point, connector.attach_point])
    for reporting_point in reporting_points:
        points.append(reporting_point.point)
    min_x = min(point.x for point in points)
    max_x = max(point.x for point in points)
    min_y = min(point.y for point in points)
    max_y = max(point.y for point in points)
    margin = max(max_x - min_x, max_y - min_y) * 0.06
    return report.XY(min_x - margin, min_y - margin), report.XY(max_x + margin, max_y + margin)


def write_entities(
    lines: list[str],
    runways: list[authoring.RunwayShape],
    tower_xy: report.XY | None,
    taxi_nodes: list[report.XY],
    taxi_route_edges: list[authoring.ProjectedTaxiRouteEdge],
    taxi_signs: list[authoring.TaxiSignShape],
    parking_positions: list[authoring.ProjectedParkingPosition],
    parking_access_branches: list[authoring.ParkingAccessBranch],
    reporting_points: list[authoring.ReportingPoint],
    extmin: report.XY,
    extmax: report.XY,
) -> None:
    max_span = max(extmax.x - extmin.x, extmax.y - extmin.y)
    point_cross_half = max(max_span * 0.0025, 28.0)
    label_height = max(max_span * 0.0032, 44.0)
    small_label_height = max(label_height * 0.72, 30.0)
    label_offset = max(point_cross_half * 1.5, 42.0)

    lines.extend(dxf.dxf_pair(0, "SECTION") + dxf.dxf_pair(2, "ENTITIES"))

    for runway in runways:
        dxf.write_line_entity(lines, "APT_RUNWAYS", runway.start, runway.end)
        dxf.write_text_entity(
            lines,
            "APT_RUNWAY_LABELS",
            report.XY(midpoint(runway.start, runway.end).x, midpoint(runway.start, runway.end).y + label_offset),
            runway.pair,
            label_height,
        )

    if tower_xy is not None:
        dxf.write_point_entity(lines, "APT_TOWER", tower_xy)
        dxf.append_cross(lines, "APT_TOWER", tower_xy, point_cross_half)
        dxf.write_text_entity(lines, "APT_TOWER_LABELS", report.XY(tower_xy.x + label_offset, tower_xy.y + label_offset), "TWR", label_height)

    for node in taxi_nodes:
        dxf.write_point_entity(lines, "XP_TAXI_NODES", node)

    for edge in taxi_route_edges:
        dxf.write_line_entity(lines, route_layer_name(edge.kind), edge.start, edge.end)
        label = route_edge_label(edge)
        if label is not None:
            dxf.write_text_entity(lines, "XP_ROUTE_LABELS", midpoint(edge.start, edge.end), label, small_label_height)

    for sign in taxi_signs:
        dxf.write_point_entity(lines, "XP_TAXI_SIGNS", sign.point)
        dxf.append_cross(lines, "XP_TAXI_SIGNS", sign.point, point_cross_half * 0.7)
        dxf.write_text_entity(
            lines,
            "XP_TAXI_SIGN_LABELS",
            report.XY(sign.point.x + label_offset, sign.point.y + (label_offset * 0.6)),
            sign.label,
            small_label_height,
        )

    for parking in parking_positions:
        dxf.write_point_entity(lines, "XP_PARKING", parking.point)
        dxf.append_cross(lines, "XP_PARKING", parking.point, point_cross_half * 0.5)
        dxf.write_text_entity(
            lines,
            "XP_PARKING_LABELS",
            report.XY(parking.point.x + label_offset, parking.point.y - (label_offset * 0.4)),
            parking.name,
            small_label_height,
        )

    for branch in parking_access_branches:
        branch_layer = f"XP_BRANCH_{branch.parent_taxiway}_{branch.branch_name or branch.parent_taxiway}"
        for edge in branch.edges:
            dxf.write_line_entity(lines, branch_layer, edge.start, edge.end)
        for connector in branch.connectors:
            dxf.write_line_entity(lines, branch_layer, connector.stand_point, connector.attach_point)
        branch_points = [edge.start for edge in branch.edges] + [edge.end for edge in branch.edges]
        if branch_points:
            centroid = report.XY(
                sum(point.x for point in branch_points) / len(branch_points),
                sum(point.y for point in branch_points) / len(branch_points),
            )
            dxf.write_text_entity(lines, "XP_BRANCH_LABELS", centroid, branch.display_name, small_label_height)

    for reporting_point in reporting_points:
        dxf.write_point_entity(lines, "OFMX_VFR_POINTS", reporting_point.point)
        dxf.append_cross(lines, "OFMX_VFR_POINTS", reporting_point.point, point_cross_half)
        dxf.write_text_entity(
            lines,
            "OFMX_VFR_LABELS",
            report.XY(reporting_point.point.x + label_offset, reporting_point.point.y + label_offset),
            reporting_point.code_id,
            label_height,
        )

    lines.extend(dxf.dxf_pair(0, "ENDSEC") + dxf.dxf_pair(0, "EOF"))


def export_xplane_baseline_dxf(manifest_path: Path, output_path: Path) -> None:
    root = report.repo_root()
    manifest = report.load_manifest(manifest_path)

    apt_path = report.resolve_path(root, manifest["sources"]["aptDat"])
    ofmx_path = report.resolve_path(root, manifest["sources"]["ofmx"])
    runways, tower, taxi_nodes, taxi_edges, apt_metadata, parking_positions = report.parse_apt(apt_path)
    taxi_signs = report.parse_apt_signs(apt_path)
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = report.projector(origin)
    ofmx_data = report.parse_ofmx(ofmx_path, manifest["airportCode"])

    projected_runways = authoring.unique_runways(runways, project)
    projected_taxi_route_edges = authoring.project_taxi_route_edges(taxi_nodes, taxi_edges, project)
    projected_taxi_nodes = [project(node.position) for node in taxi_nodes.values()]
    projected_taxi_signs = [
        authoring.TaxiSignShape(
            point=project(sign.position),
            heading_deg=sign.heading_deg,
            label=sign.display_text,
            raw_text=sign.raw_text,
        )
        for sign in taxi_signs
    ]
    projected_parking_positions = authoring.project_visible_parking_positions(parking_positions, project)
    parking_access_branches = authoring.infer_parking_access_branches(projected_parking_positions, projected_taxi_route_edges)
    reporting_points = sorted(
        [
            authoring.ReportingPoint(
                code_id=point.code_id,
                point=project(point.position),
                code_type=point.code_type,
            )
            for point in ofmx_data["airportDesignatedPoints"]
            if (point.code_type or "").startswith("VFR")
        ],
        key=lambda point: point.code_id,
    )
    tower_xy = project(tower.position) if tower is not None else None

    extmin, extmax = collect_extents(
        projected_runways,
        tower_xy,
        projected_taxi_nodes,
        projected_taxi_route_edges,
        projected_taxi_signs,
        projected_parking_positions,
        parking_access_branches,
        reporting_points,
    )

    layer_colors: dict[str, int] = {
        "0": 7,
        "APT_RUNWAYS": 8,
        "APT_RUNWAY_LABELS": 8,
        "APT_TOWER": 7,
        "APT_TOWER_LABELS": 7,
        "XP_TAXI_NODES": 9,
        "XP_ROUTE_LABELS": 8,
        "XP_TAXI_SIGNS": 2,
        "XP_TAXI_SIGN_LABELS": 2,
        "XP_PARKING": 7,
        "XP_PARKING_LABELS": 7,
        "XP_BRANCH_LABELS": 5,
        "OFMX_VFR_POINTS": 3,
        "OFMX_VFR_LABELS": 3,
    }
    for edge in projected_taxi_route_edges:
        layer_colors[route_layer_name(edge.kind)] = route_color_index(edge.kind)
    for branch in parking_access_branches:
        layer_name = f"XP_BRANCH_{branch.parent_taxiway}_{branch.branch_name or branch.parent_taxiway}"
        layer_colors[layer_name] = branch_color_index(branch.branch_name)

    lines: list[str] = []
    dxf.write_header(lines, extmin, extmax)
    dxf.write_layers(lines, layer_colors)
    write_entities(
        lines,
        projected_runways,
        tower_xy,
        projected_taxi_nodes,
        projected_taxi_route_edges,
        projected_taxi_signs,
        projected_parking_positions,
        parking_access_branches,
        reporting_points,
        extmin,
        extmax,
    )
    output_path.write_text("\n".join(lines) + "\n")


def main() -> None:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    airport_code = report.load_manifest(manifest_path)["airportCode"].lower()
    default_output = report.repo_root() / "cad/airports" / f"{airport_code}_xplane_baseline.dxf"
    output_path = args.output.resolve() if args.output is not None else default_output
    export_xplane_baseline_dxf(manifest_path, output_path)
    print(output_path)


if __name__ == "__main__":
    main()
