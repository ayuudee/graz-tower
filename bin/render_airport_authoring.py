#!/usr/bin/env python3

from __future__ import annotations

import argparse
import html
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report


PADDING_PX = 48.0
DEFAULT_WIDTH_PX = 1600.0


@dataclass(frozen=True)
class Bounds:
    min_x: float
    max_x: float
    min_y: float
    max_y: float

    @property
    def width(self) -> float:
        return max(self.max_x - self.min_x, 1.0)

    @property
    def height(self) -> float:
        return max(self.max_y - self.min_y, 1.0)

    def expand(self, margin: float) -> "Bounds":
        return Bounds(
            min_x=self.min_x - margin,
            max_x=self.max_x + margin,
            min_y=self.min_y - margin,
            max_y=self.max_y + margin,
        )


@dataclass(frozen=True)
class Canvas:
    bounds: Bounds
    width_px: float
    height_px: float
    scale: float

    def map(self, point: report.XY) -> tuple[float, float]:
        x = PADDING_PX + ((point.x - self.bounds.min_x) * self.scale)
        y = PADDING_PX + ((self.bounds.max_y - point.y) * self.scale)
        return x, y


@dataclass(frozen=True)
class RunwayShape:
    pair: str
    start: report.XY
    end: report.XY
    width_m: float


@dataclass(frozen=True)
class ReportingPoint:
    code_id: str
    point: report.XY
    code_type: str | None


@dataclass(frozen=True)
class VfrRouteSegment:
    route_id: str
    start_ref: str
    end_ref: str
    start: report.XY
    end: report.XY


@dataclass(frozen=True)
class ProjectedTaxiRouteEdge:
    kind: str
    name: str
    start: report.XY
    end: report.XY


@dataclass(frozen=True)
class ProjectedParkingPosition:
    name: str
    location_type: str
    aircraft_types: str
    heading_deg: float
    point: report.XY


@dataclass(frozen=True)
class ParkingAccessConnector:
    stand_name: str
    stand_point: report.XY
    attach_point: report.XY


@dataclass(frozen=True)
class ParkingAccessBranch:
    branch_name: str
    parent_taxiway: str
    edges: list[ProjectedTaxiRouteEdge]
    stands: list[ProjectedParkingPosition]
    connectors: list[ParkingAccessConnector]

    @property
    def display_name(self) -> str:
        return f"{self.parent_taxiway}/{self.branch_name}" if self.branch_name else self.parent_taxiway


@dataclass(frozen=True)
class TaxiSignShape:
    point: report.XY
    heading_deg: float
    label: str
    raw_text: str


@dataclass(frozen=True)
class ProcedureAnchorShape:
    anchor_id: str
    point: report.XY
    label: str
    note: str | None
    anchor_type: str | None


@dataclass(frozen=True)
class AirspaceShape:
    mid: str
    code_id: str | None
    name: str
    label: str
    lower_limit: str
    upper_limit: str
    category: str
    has_curve_vertices: bool
    boundaries: list[list[report.XY]]


@dataclass(frozen=True)
class SceneContext:
    root: Path
    manifest_path: Path
    manifest: dict
    apt_runways: list[RunwayShape]
    taxi_edges: list[tuple[report.XY, report.XY]]
    taxi_route_edges: list[ProjectedTaxiRouteEdge]
    taxi_nodes: list[report.XY]
    taxi_signs: list[TaxiSignShape]
    tower_xy: report.XY | None
    parking_positions: list[ProjectedParkingPosition]
    parking_access_branches: list[ParkingAccessBranch]
    ground_lines: list[report.DxfLine]
    ground_points: list[report.DxfPoint]
    ground_components: list[list[report.DxfLine]]
    ground_component_status: dict[int, str]
    ground_component_label: dict[int, str]
    ground_marker_status: dict[int, str]
    ground_marker_label: dict[int, str]
    circuit_lines: list[report.DxfLine]
    circuit_points: list[report.DxfPoint]
    circuit_components: list[list[report.DxfLine]]
    circuit_attachments: list[report.EndpointAttachment]
    procedure_anchors: list[ProcedureAnchorShape]
    reporting_points: list[ReportingPoint]
    vfr_route_segments: list[VfrRouteSegment]
    working_airspace_sector_lines: list[report.DxfLine]
    airspace_shapes: list[AirspaceShape]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Render airport authoring overlays as SVG.")
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Directory to write SVG/HTML outputs. Defaults to cad/airports/rendered/<airport>.",
    )
    return parser.parse_args()


def choose_transform(
    orientation: str,
    forward: report.Similarity,
    reverse: report.Similarity,
) -> report.Similarity:
    return forward if orientation == "forward" else reverse


def identity_transform() -> report.Similarity:
    return report.Similarity(
        scale=1.0,
        rotation_rad=0.0,
        translation=report.XY(0.0, 0.0),
    )


def empty_dxf_document() -> report.DxfDocument:
    return report.DxfDocument(
        lines=[],
        points=[],
        entity_counts={},
        entity_layers={},
    )


def manifest_drawing(manifest: dict, drawing_id: str) -> dict | None:
    return next(
        (drawing for drawing in manifest.get("drawings", []) if drawing.get("id") == drawing_id),
        None,
    )


def unique_runways(runways: dict[str, report.RunwayRecord], project) -> list[RunwayShape]:
    unique_records = {id(record): record for record in runways.values()}
    runway_shapes = [
        RunwayShape(
            pair=f"{record.designator_a}/{record.designator_b}",
            start=project(record.end_a),
            end=project(record.end_b),
            width_m=record.width_m,
        )
        for record in unique_records.values()
    ]
    return sorted(runway_shapes, key=lambda runway: runway.pair)


def farthest_pair(points: list[report.DxfPoint]) -> tuple[report.XY, report.XY]:
    return max(
        (
            (a.point, b.point)
            for index, a in enumerate(points)
            for b in points[index + 1 :]
        ),
        key=lambda pair: pair[0].distance_to(pair[1]),
    )


def transform_lines(lines: list[report.DxfLine], transform: report.Similarity) -> list[report.DxfLine]:
    return [
        report.DxfLine(
            layer=line.layer,
            start=transform.apply(line.start),
            end=transform.apply(line.end),
        )
        for line in lines
    ]


def transform_points(points: list[report.DxfPoint], transform: report.Similarity) -> list[report.DxfPoint]:
    return [
        report.DxfPoint(
            layer=point.layer,
            point=transform.apply(point.point),
        )
        for point in points
    ]


def transform_components(
    components: list[list[report.DxfLine]],
    transform: report.Similarity,
) -> list[list[report.DxfLine]]:
    return [transform_lines(component, transform) for component in components]


def bounds_from_points(points: list[report.XY]) -> Bounds:
    xs = [point.x for point in points]
    ys = [point.y for point in points]
    return Bounds(
        min_x=min(xs),
        max_x=max(xs),
        min_y=min(ys),
        max_y=max(ys),
    )


def scene_bounds(points: list[report.XY], margin: float) -> Bounds:
    return bounds_from_points(points).expand(margin)


def make_canvas(bounds: Bounds) -> Canvas:
    usable_width = max(DEFAULT_WIDTH_PX - (2.0 * PADDING_PX), 1.0)
    scale = usable_width / bounds.width
    height_px = (bounds.height * scale) + (2.0 * PADDING_PX)
    return Canvas(bounds=bounds, width_px=DEFAULT_WIDTH_PX, height_px=height_px, scale=scale)


def collect_points_from_lines(lines: list[report.DxfLine]) -> list[report.XY]:
    return [endpoint for line in lines for endpoint in (line.start, line.end)]


def collect_points_from_boundaries(boundaries: list[list[report.XY]]) -> list[report.XY]:
    return [point for boundary in boundaries for point in boundary]


EXCLUDED_PARKING_NAMES = {"Apron Hangar", "Apron West 1", "Apron West 2", "Apron West 3", "Glider tow"}


def project_taxi_route_edges(
    taxi_nodes: dict[int, report.TaxiNode],
    taxi_edges: list[report.TaxiEdge],
    project,
) -> list[ProjectedTaxiRouteEdge]:
    return [
        ProjectedTaxiRouteEdge(
            kind=edge.kind,
            name=edge.name,
            start=project(taxi_nodes[edge.start].position),
            end=project(taxi_nodes[edge.end].position),
        )
        for edge in taxi_edges
        if edge.start in taxi_nodes and edge.end in taxi_nodes
    ]


def project_visible_parking_positions(
    parking_positions: list[report.ParkingPosition],
    project,
) -> list[ProjectedParkingPosition]:
    return [
        ProjectedParkingPosition(
            name=parking.name,
            location_type=parking.location_type,
            aircraft_types=parking.aircraft_types,
            heading_deg=parking.heading_deg,
            point=project(parking.position),
        )
        for parking in parking_positions
        if parking.name and parking.name not in EXCLUDED_PARKING_NAMES
    ]


def nearest_point_on_segment(
    point: report.XY,
    start: report.XY,
    end: report.XY,
) -> tuple[float, report.XY]:
    dx = end.x - start.x
    dy = end.y - start.y
    if dx == 0.0 and dy == 0.0:
        return point.distance_to(start), start
    segment_position = (((point.x - start.x) * dx) + ((point.y - start.y) * dy)) / ((dx * dx) + (dy * dy))
    clamped_position = max(0.0, min(1.0, segment_position))
    projected = report.XY(start.x + (clamped_position * dx), start.y + (clamped_position * dy))
    return point.distance_to(projected), projected


def infer_parking_access_branches(
    parking_positions: list[ProjectedParkingPosition],
    taxi_route_edges: list[ProjectedTaxiRouteEdge],
    parent_taxiway: str = "A",
) -> list[ParkingAccessBranch]:
    relevant_edges = [edge for edge in taxi_route_edges if edge.kind == f"taxiway_{parent_taxiway}"]
    if not relevant_edges or not parking_positions:
        return []

    branch_edges: dict[str, list[ProjectedTaxiRouteEdge]] = {}
    for edge in relevant_edges:
        branch_name = edge.name or parent_taxiway
        branch_edges.setdefault(branch_name, []).append(edge)

    assigned_stands: dict[str, list[ProjectedParkingPosition]] = {}
    connectors_by_branch: dict[str, list[ParkingAccessConnector]] = {}

    for parking in parking_positions:
        best_edge: ProjectedTaxiRouteEdge | None = None
        best_distance: float | None = None
        best_attach_point: report.XY | None = None
        for edge in relevant_edges:
            distance, attach_point = nearest_point_on_segment(parking.point, edge.start, edge.end)
            if best_distance is None or distance < best_distance:
                best_distance = distance
                best_edge = edge
                best_attach_point = attach_point
        if best_edge is None or best_attach_point is None:
            continue
        branch_name = best_edge.name or parent_taxiway
        assigned_stands.setdefault(branch_name, []).append(parking)
        connectors_by_branch.setdefault(branch_name, []).append(
            ParkingAccessConnector(
                stand_name=parking.name,
                stand_point=parking.point,
                attach_point=best_attach_point,
            ),
        )

    branch_order = {"G1": 0, "S1": 1, "X": 2}
    return [
        ParkingAccessBranch(
            branch_name=branch_name,
            parent_taxiway=parent_taxiway,
            edges=branch_edges[branch_name],
            stands=sorted(assigned_stands.get(branch_name, []), key=lambda parking: parking.name),
            connectors=sorted(connectors_by_branch.get(branch_name, []), key=lambda connector: connector.stand_name),
        )
        for branch_name in sorted(
            (name for name in branch_edges if name in assigned_stands),
            key=lambda name: (branch_order.get(name, 99), name),
        )
    ]


def circuit_endpoint_role_lookup(circuit_lines: list[report.DxfLine]) -> dict[str, report.XY]:
    endpoints = report.degree_one_points(circuit_lines)
    if not endpoints:
        return {}
    center_x = sum(point.x for point in endpoints) / len(endpoints)
    center_y = sum(point.y for point in endpoints) / len(endpoints)
    role_lookup: dict[str, report.XY] = {}
    for point in endpoints:
        vertical = "n" if point.y >= center_y else "s"
        horizontal = "e" if point.x >= center_x else "w"
        role = f"{vertical}{horizontal}"
        role_lookup.setdefault(role, point)
    return role_lookup


def build_procedure_anchor_shapes(
    manifest: dict,
    circuit_points: list[report.DxfPoint],
    circuit_lines: list[report.DxfLine],
    reporting_points: list[ReportingPoint],
) -> list[ProcedureAnchorShape]:
    reporting_lookup = {reporting_point.code_id: reporting_point.point for reporting_point in reporting_points}
    circuit_endpoint_lookup = circuit_endpoint_role_lookup(circuit_lines)
    procedure_anchors: list[ProcedureAnchorShape] = []
    for anchor in report.manifest_procedure_anchors(manifest):
        anchor_id = anchor.get("anchorId")
        if not isinstance(anchor_id, str):
            continue

        point: report.XY | None = None
        drawing_id = anchor.get("drawingId")
        drawing_point_index = anchor.get("drawingPointIndex")
        if drawing_id == "vfr_circuit" and isinstance(drawing_point_index, int):
            zero_index = drawing_point_index - 1
            if 0 <= zero_index < len(circuit_points):
                point = circuit_points[zero_index].point

        endpoint_role = anchor.get("endpointRole")
        if point is None and isinstance(endpoint_role, str):
            point = circuit_endpoint_lookup.get(endpoint_role.lower())

        reporting_point_ref = anchor.get("reportingPointRef")
        if point is None and isinstance(reporting_point_ref, str):
            point = reporting_lookup.get(reporting_point_ref)

        if point is None:
            continue

        label = anchor.get("displayLabel") or anchor.get("assignedMeaning") or anchor_id
        note = anchor.get("note")
        anchor_type = anchor.get("anchorType")
        procedure_anchors.append(
            ProcedureAnchorShape(
                anchor_id=anchor_id,
                point=point,
                label=str(label),
                note=note if isinstance(note, str) else None,
                anchor_type=anchor_type if isinstance(anchor_type, str) else None,
            ),
        )

    return procedure_anchors


def build_vfr_route_segments(
    manifest: dict,
    reporting_points: list[ReportingPoint],
    procedure_anchors: list[ProcedureAnchorShape],
) -> list[VfrRouteSegment]:
    point_lookup = {reporting_point.code_id: reporting_point.point for reporting_point in reporting_points}
    point_lookup.update({anchor.anchor_id: anchor.point for anchor in procedure_anchors})
    segments: list[VfrRouteSegment] = []
    for route in manifest.get("namedMappings", {}).get("vfrRoutes", []):
        path_definition = route.get("pathDefinition", {})
        for segment in path_definition.get("segmentSequence", []):
            start_ref = segment.get("from")
            end_ref = segment.get("to")
            if not isinstance(start_ref, str) or not isinstance(end_ref, str):
                continue
            start_point = point_lookup.get(start_ref)
            end_point = point_lookup.get(end_ref)
            if start_point is None or end_point is None:
                continue
            segments.append(
                VfrRouteSegment(
                    route_id=route.get("routeId", "vfr_route"),
                    start_ref=start_ref,
                    end_ref=end_ref,
                    start=start_point,
                    end=end_point,
                ),
            )
    return segments


def bounds_to_viewbox(canvas: Canvas, bounds: Bounds) -> dict[str, float]:
    left, top = canvas.map(report.XY(bounds.min_x, bounds.max_y))
    right, bottom = canvas.map(report.XY(bounds.max_x, bounds.min_y))
    return {
        "x": left,
        "y": top,
        "width": right - left,
        "height": bottom - top,
    }


def centroid(points: list[report.XY]) -> report.XY:
    return report.XY(
        x=sum(point.x for point in points) / len(points),
        y=sum(point.y for point in points) / len(points),
    )


def svg_attrs(extra_attrs: dict[str, str] | None = None) -> str:
    if not extra_attrs:
        return ""
    return "".join(f' {name}="{html.escape(value, quote=True)}"' for name, value in extra_attrs.items())


def polyline_svg(
    canvas: Canvas,
    start: report.XY,
    end: report.XY,
    color: str,
    width_px: float,
    opacity: float = 1.0,
    dash: str | None = None,
    extra_attrs: dict[str, str] | None = None,
) -> str:
    x1, y1 = canvas.map(start)
    x2, y2 = canvas.map(end)
    dash_attr = "" if dash is None else f' stroke-dasharray="{dash}"'
    extra = svg_attrs(extra_attrs)
    return (
        f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" '
        f'stroke="{color}" stroke-width="{width_px:.2f}" stroke-opacity="{opacity:.3f}" '
        f'stroke-linecap="round"{dash_attr}{extra} />'
    )


def circle_svg(
    canvas: Canvas,
    point: report.XY,
    radius_px: float,
    fill: str,
    stroke: str | None = None,
    stroke_width_px: float = 1.0,
    opacity: float = 1.0,
    extra_attrs: dict[str, str] | None = None,
) -> str:
    cx, cy = canvas.map(point)
    stroke_attr = "" if stroke is None else f' stroke="{stroke}" stroke-width="{stroke_width_px:.2f}"'
    extra = svg_attrs(extra_attrs)
    return (
        f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{radius_px:.2f}" '
        f'fill="{fill}" fill-opacity="{opacity:.3f}"{stroke_attr}{extra} />'
    )


def polyshape_svg(
    canvas: Canvas,
    points: list[report.XY],
    stroke: str,
    stroke_width_px: float,
    fill: str,
    fill_opacity: float,
    stroke_opacity: float = 1.0,
    dash: str | None = None,
    closed: bool = True,
    extra_attrs: dict[str, str] | None = None,
) -> str:
    mapped_points = " ".join(
        f"{x:.2f},{y:.2f}"
        for x, y in (canvas.map(point) for point in points)
    )
    element = "polygon" if closed else "polyline"
    dash_attr = "" if dash is None else f' stroke-dasharray="{dash}"'
    fill_value = fill if closed else "none"
    fill_opacity_attr = f' fill-opacity="{fill_opacity:.3f}"' if closed else ""
    extra = svg_attrs(extra_attrs)
    return (
        f'<{element} points="{mapped_points}" fill="{fill_value}"{fill_opacity_attr} '
        f'stroke="{stroke}" stroke-width="{stroke_width_px:.2f}" stroke-opacity="{stroke_opacity:.3f}" '
        f'stroke-linejoin="round" stroke-linecap="round"{dash_attr}{extra} />'
    )


def label_svg(
    canvas: Canvas,
    point: report.XY,
    text: str,
    fill: str,
    dx: float = 8.0,
    dy: float = -8.0,
    font_size: float = 16.0,
    anchor: str = "start",
    stroke: str | None = None,
    stroke_width_px: float = 3.0,
    extra_attrs: dict[str, str] | None = None,
) -> str:
    x, y = canvas.map(point)
    escaped = html.escape(text)
    stroke_attr = "" if stroke is None else f' stroke="{stroke}" stroke-width="{stroke_width_px:.2f}" paint-order="stroke"'
    extra = svg_attrs(extra_attrs)
    return (
        f'<text x="{x:.2f}" y="{y:.2f}" dx="{dx:.2f}" dy="{dy:.2f}" fill="{fill}" '
        f'font-size="{font_size:.1f}" font-family="monospace" text-anchor="{anchor}"{stroke_attr}{extra}>{escaped}</text>'
    )


def cross_svg(canvas: Canvas, point: report.XY, size_px: float, color: str, width_px: float) -> str:
    cx, cy = canvas.map(point)
    half = size_px / 2.0
    return "\n".join(
        [
            f'<line x1="{cx - half:.2f}" y1="{cy - half:.2f}" x2="{cx + half:.2f}" y2="{cy + half:.2f}" stroke="{color}" stroke-width="{width_px:.2f}" />',
            f'<line x1="{cx - half:.2f}" y1="{cy + half:.2f}" x2="{cx + half:.2f}" y2="{cy - half:.2f}" stroke="{color}" stroke-width="{width_px:.2f}" />',
        ]
    )


def component_centroid(lines: list[report.DxfLine]) -> report.XY:
    points = collect_points_from_lines(lines)
    return report.XY(
        x=sum(point.x for point in points) / len(points),
        y=sum(point.y for point in points) / len(points),
    )


def ground_component_style(status: str) -> tuple[str, float, float]:
    if status == "known_divergence":
        return "#f6bd60", 0.95, 3.8
    if status == "suspect":
        return "#ff6b6b", 0.95, 3.8
    return "#42b6ff", 0.82, 3.0


def ground_marker_fill(status: str) -> str:
    if status == "known_divergence":
        return "#f6bd60"
    if status == "suspect":
        return "#ff5f57"
    return "#7ee787"


def parking_branch_palette(branch_name: str) -> tuple[str, str]:
    palette = {
        "G1": ("#7dd3fc", "#16384a"),
        "S1": ("#fcd34d", "#4c3a12"),
        "X": ("#fca5a5", "#4b2222"),
    }
    return palette.get(branch_name, ("#c4b5fd", "#35234d"))


def airspace_palette(index: int, category: str) -> tuple[str, str]:
    primary = [
        ("#58d4ff", "#143244"),
        ("#7ce38b", "#173726"),
        ("#ffd166", "#4a3710"),
        ("#ff9d76", "#4d2718"),
        ("#c6a0ff", "#35214b"),
        ("#ff80bf", "#4a1d35"),
    ]
    secondary = [
        ("#f2a7ff", "#41204a"),
        ("#ff8f8f", "#4a2020"),
        ("#d8b4fe", "#302040"),
    ]
    palette = primary if category == "primary" else secondary
    return palette[index % len(palette)]


def airspace_label(shape: AirspaceShape) -> str:
    if shape.name == "LOWG" and shape.code_id == "LO585":
        return "LOWG CTR"
    return shape.name


def airspace_tooltip(shape: AirspaceShape) -> str:
    parts = [shape.label]
    if shape.code_id is not None:
        parts.append(f"({shape.code_id})")
    parts.append(f"{shape.lower_limit} to {shape.upper_limit}")
    if shape.has_curve_vertices:
        parts.append("contains OFMX curve vertices; rendered as straight-segment approximation")
    return " ".join(parts)


def zoom_circle_attrs(radius_px: float, stroke_width_px: float) -> dict[str, str]:
    return {
        "class": "zoom-circle",
        "data-base-radius": f"{radius_px:.2f}",
        "data-base-stroke-width": f"{stroke_width_px:.2f}",
    }


def zoom_label_attrs(font_size: float, dx: float, dy: float, stroke_width_px: float) -> dict[str, str]:
    return {
        "class": "zoom-label",
        "data-base-font-size": f"{font_size:.2f}",
        "data-base-dx": f"{dx:.2f}",
        "data-base-dy": f"{dy:.2f}",
        "data-base-stroke-width": f"{stroke_width_px:.2f}",
    }


def render_legend(items: list[tuple[str, str]], x: float, y: float) -> str:
    row_height = 22.0
    entries: list[str] = [
        f'<rect x="{x - 18:.2f}" y="{y - 24:.2f}" width="380" height="{(len(items) * row_height) + 28:.2f}" fill="#0f141a" fill-opacity="0.88" stroke="#3a4653" stroke-width="1.0" rx="8" />',
        f'<text x="{x:.2f}" y="{y:.2f}" fill="#f0f6fc" font-size="17" font-family="monospace">Legend</text>',
    ]
    for index, (color, label) in enumerate(items, start=1):
        cy = y + (index * row_height)
        entries.append(f'<circle cx="{x + 8:.2f}" cy="{cy - 5:.2f}" r="5" fill="{color}" />')
        entries.append(
            f'<text x="{x + 24:.2f}" y="{cy:.2f}" fill="#d0d7de" font-size="15" font-family="monospace">{html.escape(label)}</text>'
        )
    return "\n".join(entries)


def svg_document(title: str, canvas: Canvas, body: str) -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{canvas.width_px:.0f}" height="{canvas.height_px:.0f}" viewBox="0 0 {canvas.width_px:.0f} {canvas.height_px:.0f}">
  <rect x="0" y="0" width="{canvas.width_px:.0f}" height="{canvas.height_px:.0f}" fill="#0b0f14" />
  <text x="32" y="36" fill="#f0f6fc" font-size="22" font-family="monospace">{html.escape(title)}</text>
{body}
</svg>
"""


def render_ground_overlay(context: SceneContext, output_path: Path) -> None:
    world_points = []
    world_points.extend(context.taxi_nodes)
    world_points.extend(collect_points_from_lines(context.ground_lines))
    world_points.extend([point.point for point in context.ground_points])
    for runway in context.apt_runways:
        world_points.extend([runway.start, runway.end])
    bounds = scene_bounds(world_points, margin=120.0)
    canvas = make_canvas(bounds)

    parts: list[str] = []
    for runway in context.apt_runways:
        stroke_width = max(runway.width_m * canvas.scale, 4.0)
        parts.append(polyline_svg(canvas, runway.start, runway.end, "#525d6b", stroke_width, opacity=0.33))
        parts.append(label_svg(canvas, report.XY((runway.start.x + runway.end.x) / 2.0, (runway.start.y + runway.end.y) / 2.0), runway.pair, "#9fb0c0", dy=-12.0))

    for start, end in context.taxi_edges:
        parts.append(polyline_svg(canvas, start, end, "#39424d", 1.8, opacity=0.9))

    for index, component in enumerate(context.ground_components, start=1):
        status = context.ground_component_status.get(index, "aligned")
        color, opacity, width_px = ground_component_style(status)
        for line in component:
            parts.append(polyline_svg(canvas, line.start, line.end, color, width_px, opacity=opacity))
        centroid = component_centroid(component)
        label = context.ground_component_label.get(index, f"C{index}")
        parts.append(label_svg(canvas, centroid, label, "#f0f6fc", dy=6.0, anchor="middle"))

    for index, point in enumerate(context.ground_points, start=1):
        status = context.ground_marker_status.get(index, "aligned")
        fill = ground_marker_fill(status)
        parts.append(circle_svg(canvas, point.point, 6.0, fill, stroke="#0b0f14", stroke_width_px=1.5))
        label = context.ground_marker_label.get(index, f"M{index}")
        parts.append(label_svg(canvas, point.point, label, "#f0f6fc", dx=10.0, dy=-10.0))

    if context.tower_xy is not None:
        parts.append(cross_svg(canvas, context.tower_xy, 16.0, "#ffffff", 2.2))
        parts.append(label_svg(canvas, context.tower_xy, "TWR", "#ffffff"))

    parts.append(
        render_legend(
            [
                ("#42b6ff", "hand-authored ground geometry"),
                ("#f6bd60", "known X-Plane divergence"),
                ("#ff6b6b", "unresolved suspect"),
                ("#39424d", "apt.dat taxi graph"),
                ("#525d6b", "apt.dat runways"),
                ("#7ee787", "ground markers"),
            ],
            x=36.0,
            y=74.0,
        ),
    )

    output_path.write_text(svg_document("LOWG ground overlay", canvas, "\n".join(parts)))


def render_ground_divergence_zoom(context: SceneContext, output_path: Path) -> None:
    focus_components = [
        component
        for index, component in enumerate(context.ground_components, start=1)
        if context.ground_component_status.get(index) in {"known_divergence", "suspect"}
    ]
    focus_points = []
    for component in focus_components:
        focus_points.extend(collect_points_from_lines(component))
    for index, point in enumerate(context.ground_points, start=1):
        if context.ground_marker_status.get(index) in {"known_divergence", "suspect"}:
            focus_points.append(point.point)
    if not focus_points:
        focus_points = [
            point
            for component in context.ground_components
            for point in collect_points_from_lines(component)
        ]
        if not focus_points:
            focus_points = [point.point for point in context.ground_points]
    if not focus_points:
        focus_points = [
            point
            for runway in context.apt_runways
            for point in [runway.start, runway.end]
        ] + [
            point
            for start, end in context.taxi_edges
            for point in [start, end]
        ]
    bounds = scene_bounds(focus_points, margin=220.0)
    canvas = make_canvas(bounds)

    parts: list[str] = []
    for runway in context.apt_runways:
        stroke_width = max(runway.width_m * canvas.scale, 4.0)
        parts.append(polyline_svg(canvas, runway.start, runway.end, "#525d6b", stroke_width, opacity=0.28))

    for start, end in context.taxi_edges:
        parts.append(polyline_svg(canvas, start, end, "#39424d", 1.8, opacity=0.9))

    for index, component in enumerate(context.ground_components, start=1):
        status = context.ground_component_status.get(index, "aligned")
        if status == "known_divergence":
            color = "#f6bd60"
            opacity = 1.0
            width_px = 4.2
        elif status == "suspect":
            color = "#ff6b6b"
            opacity = 1.0
            width_px = 4.2
        else:
            color = "#506070"
            opacity = 0.35
            width_px = 2.0
        for line in component:
            parts.append(polyline_svg(canvas, line.start, line.end, color, width_px, opacity=opacity))
        if status in {"known_divergence", "suspect"}:
            centroid = component_centroid(component)
            label = context.ground_component_label.get(index, f"C{index}")
            parts.append(label_svg(canvas, centroid, label, "#ffffff", dy=6.0, anchor="middle"))

    for index, point in enumerate(context.ground_points, start=1):
        status = context.ground_marker_status.get(index, "aligned")
        if status not in {"known_divergence", "suspect"}:
            continue
        fill = ground_marker_fill(status)
        label = context.ground_marker_label.get(index, f"M{index}")
        parts.append(circle_svg(canvas, point.point, 8.0, fill, stroke="#ffffff", stroke_width_px=1.2))
        parts.append(label_svg(canvas, point.point, label, "#ffffff", dx=12.0, dy=-12.0))

    parts.append(
        render_legend(
            [
                ("#f6bd60", "known glider-area divergence"),
                ("#ff6b6b", "unresolved suspect"),
                ("#39424d", "apt.dat taxi / runway reference"),
                ("#f6bd60", "divergence marker"),
            ],
            x=36.0,
            y=74.0,
        ),
    )

    output_path.write_text(svg_document("LOWG 16/34L glider area zoom", canvas, "\n".join(parts)))


def render_vfr_circuit_overlay(context: SceneContext, output_path: Path) -> None:
    world_points = []
    world_points.extend(collect_points_from_lines(context.circuit_lines))
    world_points.extend([point.point for point in context.circuit_points])
    world_points.extend([anchor.point for anchor in context.procedure_anchors])
    world_points.extend([segment.start for segment in context.vfr_route_segments])
    world_points.extend([segment.end for segment in context.vfr_route_segments])
    world_points.extend(collect_points_from_lines(context.working_airspace_sector_lines))
    for runway in context.apt_runways:
        world_points.extend([runway.start, runway.end])
    world_points.extend([point.point for point in context.ground_points])
    if context.tower_xy is not None:
        world_points.append(context.tower_xy)
    world_points.extend([point.point for point in context.reporting_points])
    bounds = scene_bounds(world_points, margin=220.0)
    canvas = make_canvas(bounds)

    parts: list[str] = []
    for runway in context.apt_runways:
        stroke_width = max(runway.width_m * canvas.scale, 4.0)
        parts.append(polyline_svg(canvas, runway.start, runway.end, "#4f5b68", stroke_width, opacity=0.28))
        parts.append(label_svg(canvas, report.XY((runway.start.x + runway.end.x) / 2.0, (runway.start.y + runway.end.y) / 2.0), runway.pair, "#a8b6c5", dy=-10.0))

    for line in context.ground_lines:
        parts.append(polyline_svg(canvas, line.start, line.end, "#243241", 1.8, opacity=0.45))

    for route_segment in context.vfr_route_segments:
        parts.append(polyline_svg(canvas, route_segment.start, route_segment.end, "#6ee7b7", 3.0, opacity=0.92))

    for line in context.working_airspace_sector_lines:
        parts.append(polyline_svg(canvas, line.start, line.end, "#ff6b6b", 3.2, opacity=0.92, dash="12 8"))

    for index, component in enumerate(context.circuit_components, start=1):
        color = "#ffb347" if index == 1 else "#ffd580"
        width_px = 4.0 if index == 1 else 3.0
        for line in component:
            parts.append(polyline_svg(canvas, line.start, line.end, color, width_px, opacity=0.95))
        centroid = component_centroid(component)
        parts.append(label_svg(canvas, centroid, f"V{index}", "#f0f6fc", dy=6.0, anchor="middle"))

    for attachment in context.circuit_attachments:
        parts.append(circle_svg(canvas, attachment.endpoint, 6.0, "#ff5ae0", stroke="#0b0f14", stroke_width_px=1.2))

    for index, point in enumerate(context.circuit_points, start=1):
        parts.append(circle_svg(canvas, point.point, 6.0, "#66e2ff", stroke="#0b0f14", stroke_width_px=1.2))
        parts.append(label_svg(canvas, point.point, f"P{index}", "#f0f6fc"))

    for anchor in context.procedure_anchors:
        if anchor.anchor_type != "vfr_route_join":
            continue
        parts.append(circle_svg(canvas, anchor.point, 7.0, "#7ee787", stroke="#0b0f14", stroke_width_px=1.4))
        parts.append(label_svg(canvas, anchor.point, anchor.label, "#d8ffd8", dx=12.0, dy=-12.0, font_size=15.0))

    if context.tower_xy is not None:
        parts.append(cross_svg(canvas, context.tower_xy, 18.0, "#ffffff", 2.2))
        parts.append(label_svg(canvas, context.tower_xy, "TWR", "#ffffff"))

    for reporting_point in context.reporting_points:
        parts.append(circle_svg(canvas, reporting_point.point, 5.0, "#2dd4bf", stroke="#0b0f14", stroke_width_px=1.0))
        parts.append(label_svg(canvas, reporting_point.point, reporting_point.code_id, "#9ff3e6", dx=10.0, dy=-10.0, font_size=14.0))

    parts.append(
        render_legend(
            [
                ("#ffb347", "VFR circuit graph"),
                ("#6ee7b7", "VFR route paths"),
                ("#ff6b6b", "working VFR operational sectors"),
                ("#ff5ae0", "endpoint-on-segment attachment"),
                ("#7ee787", "circuit join anchors"),
                ("#2dd4bf", "OFMX VFR reporting point"),
                ("#4f5b68", "apt.dat runways"),
                ("#243241", "ground DXF context"),
            ],
            x=36.0,
            y=74.0,
        ),
    )

    output_path.write_text(svg_document("LOWG VFR circuit overlay", canvas, "\n".join(parts)))


def render_interactive_index(context: SceneContext, output_dir: Path, files: list[Path]) -> None:
    full_world_points: list[report.XY] = []
    full_world_points.extend(context.taxi_nodes)
    full_world_points.extend([parking.point for parking in context.parking_positions])
    for branch in context.parking_access_branches:
        full_world_points.extend([edge.start for edge in branch.edges])
        full_world_points.extend([edge.end for edge in branch.edges])
        full_world_points.extend([connector.attach_point for connector in branch.connectors])
    full_world_points.extend(collect_points_from_lines(context.ground_lines))
    full_world_points.extend([point.point for point in context.ground_points])
    full_world_points.extend(collect_points_from_lines(context.circuit_lines))
    full_world_points.extend([point.point for point in context.circuit_points])
    full_world_points.extend([anchor.point for anchor in context.procedure_anchors])
    full_world_points.extend([point.point for point in context.reporting_points])
    full_world_points.extend([segment.start for segment in context.vfr_route_segments])
    full_world_points.extend([segment.end for segment in context.vfr_route_segments])
    full_world_points.extend(collect_points_from_lines(context.working_airspace_sector_lines))
    for runway in context.apt_runways:
        full_world_points.extend([runway.start, runway.end])
    for shape in context.airspace_shapes:
        full_world_points.extend(collect_points_from_boundaries(shape.boundaries))
    if context.tower_xy is not None:
        full_world_points.append(context.tower_xy)

    airport_world_points: list[report.XY] = []
    airport_world_points.extend(context.taxi_nodes)
    airport_world_points.extend([parking.point for parking in context.parking_positions])
    for branch in context.parking_access_branches:
        airport_world_points.extend([edge.start for edge in branch.edges])
        airport_world_points.extend([edge.end for edge in branch.edges])
        airport_world_points.extend([connector.attach_point for connector in branch.connectors])
    airport_world_points.extend(collect_points_from_lines(context.ground_lines))
    airport_world_points.extend([point.point for point in context.ground_points])
    airport_world_points.extend(collect_points_from_lines(context.circuit_lines))
    airport_world_points.extend([point.point for point in context.circuit_points])
    airport_world_points.extend([anchor.point for anchor in context.procedure_anchors])
    airport_world_points.extend([segment.start for segment in context.vfr_route_segments])
    airport_world_points.extend([segment.end for segment in context.vfr_route_segments])
    airport_world_points.extend(collect_points_from_lines(context.working_airspace_sector_lines))
    for runway in context.apt_runways:
        airport_world_points.extend([runway.start, runway.end])
    if context.tower_xy is not None:
        airport_world_points.append(context.tower_xy)

    primary_airspace_points = [
        point
        for shape in context.airspace_shapes
        if shape.category == "primary"
        for point in collect_points_from_boundaries(shape.boundaries)
    ]

    full_bounds = scene_bounds(full_world_points, margin=420.0)
    canvas = make_canvas(full_bounds)
    airport_view = bounds_to_viewbox(canvas, scene_bounds(airport_world_points, margin=180.0))
    airspace_source = primary_airspace_points if primary_airspace_points else full_world_points
    airspace_view = bounds_to_viewbox(canvas, scene_bounds(airspace_source, margin=320.0))
    full_view = {
        "x": 0.0,
        "y": 0.0,
        "width": canvas.width_px,
        "height": canvas.height_px,
    }

    primary_airspaces = [shape for shape in context.airspace_shapes if shape.category == "primary"]
    secondary_airspaces = [shape for shape in context.airspace_shapes if shape.category == "secondary"]
    airspace_notes = [
        f"{shape.label}: OFMX curve vertices are present; the map currently renders straight-segment approximation."
        for shape in context.airspace_shapes
        if shape.has_curve_vertices
    ]

    layer_groups: list[str] = []

    primary_airspace_parts: list[str] = []
    for index, shape in enumerate(primary_airspaces):
        stroke, fill = airspace_palette(index, "primary")
        dash = "10 8" if shape.has_curve_vertices else None
        label_point = centroid(collect_points_from_boundaries(shape.boundaries))
        parts = [
            polyshape_svg(
                canvas,
                boundary,
                stroke=stroke,
                stroke_width_px=3.0,
                fill=fill,
                fill_opacity=0.20,
                stroke_opacity=0.9,
                dash=dash,
                closed=True,
            )
            for boundary in shape.boundaries
        ]
        parts.append(
            label_svg(
                canvas,
                label_point,
                airspace_label(shape),
                stroke,
                dx=0.0,
                dy=0.0,
                font_size=18.0,
                anchor="middle",
                stroke="#0b0f14",
                stroke_width_px=4.0,
                extra_attrs=zoom_label_attrs(18.0, 0.0, 0.0, 4.0),
            ),
        )
        primary_airspace_parts.append(
            f'<g class="airspace-shape"><title>{html.escape(airspace_tooltip(shape))}</title>{"".join(parts)}</g>'
        )
    layer_groups.append(f'<g id="layer-airspace-primary">{"".join(primary_airspace_parts)}</g>')

    secondary_airspace_parts: list[str] = []
    for index, shape in enumerate(secondary_airspaces):
        stroke, fill = airspace_palette(index, "secondary")
        dash = "12 8" if shape.has_curve_vertices else "8 8"
        label_point = centroid(collect_points_from_boundaries(shape.boundaries))
        parts = [
            polyshape_svg(
                canvas,
                boundary,
                stroke=stroke,
                stroke_width_px=2.6,
                fill=fill,
                fill_opacity=0.12,
                stroke_opacity=0.75,
                dash=dash,
                closed=True,
            )
            for boundary in shape.boundaries
        ]
        parts.append(
            label_svg(
                canvas,
                label_point,
                airspace_label(shape),
                stroke,
                dx=0.0,
                dy=0.0,
                font_size=15.0,
                anchor="middle",
                stroke="#0b0f14",
                stroke_width_px=4.0,
                extra_attrs=zoom_label_attrs(15.0, 0.0, 0.0, 4.0),
            ),
        )
        secondary_airspace_parts.append(
            f'<g class="airspace-shape"><title>{html.escape(airspace_tooltip(shape))}</title>{"".join(parts)}</g>'
        )
    layer_groups.append(f'<g id="layer-airspace-secondary">{"".join(secondary_airspace_parts)}</g>')

    runway_parts: list[str] = []
    for runway in context.apt_runways:
        stroke_width = max(runway.width_m * canvas.scale, 4.0)
        runway_parts.append(polyline_svg(canvas, runway.start, runway.end, "#7b8794", stroke_width, opacity=0.40))
        runway_parts.append(
            label_svg(
                canvas,
                report.XY((runway.start.x + runway.end.x) / 2.0, (runway.start.y + runway.end.y) / 2.0),
                runway.pair,
                "#cdd9e5",
                dy=-12.0,
                anchor="middle",
                stroke="#0b0f14",
                stroke_width_px=4.0,
                extra_attrs=zoom_label_attrs(16.0, 0.0, -12.0, 4.0),
            ),
        )
    layer_groups.append(f'<g id="layer-runways">{"".join(runway_parts)}</g>')

    taxi_parts = [polyline_svg(canvas, start, end, "#39424d", 1.8, opacity=0.88) for start, end in context.taxi_edges]
    layer_groups.append(f'<g id="layer-taxi-reference">{"".join(taxi_parts)}</g>')

    taxi_sign_parts: list[str] = []
    for sign in context.taxi_signs:
        tooltip = sign.label if sign.label == sign.raw_text else f"{sign.label} [{sign.raw_text}]"
        taxi_sign_parts.append(
            "<g>"
            f"<title>{html.escape(tooltip)}</title>"
            f"{circle_svg(canvas, sign.point, 4.5, '#d9ff6a', stroke='#0b0f14', stroke_width_px=1.5, extra_attrs=zoom_circle_attrs(4.5, 1.5))}"
            f"{label_svg(canvas, sign.point, sign.label, '#d9ff6a', dx=10.0, dy=-9.0, stroke='#0b0f14', stroke_width_px=4.0, extra_attrs=zoom_label_attrs(13.0, 10.0, -9.0, 4.0))}"
            "</g>"
        )
    layer_groups.append(f'<g id="layer-taxi-signs">{"".join(taxi_sign_parts)}</g>')

    parking_access_parts: list[str] = []
    for branch in context.parking_access_branches:
        stroke, fill = parking_branch_palette(branch.branch_name)
        for edge in branch.edges:
            parking_access_parts.append(
                "<g>"
                f"<title>{html.escape(f'X-Plane parking access {branch.display_name}')}</title>"
                f"{polyline_svg(canvas, edge.start, edge.end, stroke, 3.2, opacity=0.92)}"
                "</g>"
            )
        for connector in branch.connectors:
            parking_access_parts.append(
                "<g>"
                f"<title>{html.escape(f'{connector.stand_name} via {branch.display_name}')}</title>"
                f"{polyline_svg(canvas, connector.stand_point, connector.attach_point, stroke, 1.8, opacity=0.82, dash='7 5')}"
                "</g>"
            )
        if branch.edges:
            branch_points = [edge.start for edge in branch.edges] + [edge.end for edge in branch.edges]
            parking_access_parts.append(
                label_svg(
                    canvas,
                    centroid(branch_points),
                    branch.display_name,
                    stroke,
                    dx=0.0,
                    dy=0.0,
                    font_size=13.0,
                    anchor="middle",
                    stroke="#0b0f14",
                    stroke_width_px=4.0,
                    extra_attrs=zoom_label_attrs(13.0, 0.0, 0.0, 4.0),
                ),
            )
        for parking in branch.stands:
            parking_access_parts.append(
                "<g>"
                f"<title>{html.escape(f'{parking.name} ({parking.location_type}) via {branch.display_name}')}</title>"
                f"{circle_svg(canvas, parking.point, 4.5, fill, stroke=stroke, stroke_width_px=1.2, extra_attrs=zoom_circle_attrs(4.5, 1.2))}"
                f"{label_svg(canvas, parking.point, parking.name, stroke, dx=8.0, dy=-8.0, font_size=12.0, stroke='#0b0f14', stroke_width_px=3.5, extra_attrs=zoom_label_attrs(12.0, 8.0, -8.0, 3.5))}"
                "</g>"
            )
    layer_groups.append(f'<g id="layer-parking-access">{"".join(parking_access_parts)}</g>')

    ground_parts: list[str] = []
    component_mapping = {
        int(mapping["componentIndex"]): mapping
        for mapping in context.manifest.get("namedMappings", {}).get("groundComponents", [])
        if "componentIndex" in mapping
    }
    for index, component in enumerate(context.ground_components, start=1):
        status = context.ground_component_status.get(index, "aligned")
        color, opacity, width_px = ground_component_style(status)
        for line in component:
            ground_parts.append(polyline_svg(canvas, line.start, line.end, color, width_px, opacity=opacity))
        mapping = component_mapping.get(index, {})
        label = mapping.get("displayLabel") or mapping.get("finalName") or context.ground_component_label.get(index)
        if label is not None:
            ground_parts.append(
                label_svg(
                    canvas,
                    component_centroid(component),
                    label,
                    "#ffffff",
                    dy=6.0,
                    anchor="middle",
                    stroke="#0b0f14",
                    stroke_width_px=4.0,
                    extra_attrs=zoom_label_attrs(16.0, 0.0, 6.0, 4.0),
                ),
            )
    layer_groups.append(f'<g id="layer-ground-geometry">{"".join(ground_parts)}</g>')

    ground_marker_parts: list[str] = []
    marker_mapping = {
        int(mapping["markerIndex"]): mapping
        for mapping in context.manifest.get("namedMappings", {}).get("groundMarkers", [])
        if "markerIndex" in mapping
    }
    for index, point in enumerate(context.ground_points, start=1):
        status = context.ground_marker_status.get(index, "aligned")
        fill = ground_marker_fill(status)
        mapping = marker_mapping.get(index, {})
        label = mapping.get("displayLabel") or mapping.get("finalName") or context.ground_marker_label.get(index) or f"M{index}"
        note = mapping.get("note") or mapping.get("referenceHint") or ""
        ground_marker_parts.append(
            "<g>"
            f"<title>{html.escape(f'{label}: {note}' if note else label)}</title>"
            f"{circle_svg(canvas, point.point, 6.0, fill, stroke='#0b0f14', stroke_width_px=1.5, extra_attrs=zoom_circle_attrs(6.0, 1.5))}"
            f"{label_svg(canvas, point.point, label, '#f0f6fc', dx=10.0, dy=-10.0, stroke='#0b0f14', stroke_width_px=4.0, extra_attrs=zoom_label_attrs(16.0, 10.0, -10.0, 4.0))}"
            "</g>"
        )
    layer_groups.append(f'<g id="layer-ground-markers">{"".join(ground_marker_parts)}</g>')

    circuit_parts: list[str] = []
    for index, component in enumerate(context.circuit_components, start=1):
        color = "#ffb347" if index == 1 else "#ffd580"
        width_px = 4.0 if index == 1 else 3.0
        for line in component:
            circuit_parts.append(polyline_svg(canvas, line.start, line.end, color, width_px, opacity=0.95))
    layer_groups.append(f'<g id="layer-vfr-circuit">{"".join(circuit_parts)}</g>')

    attachment_parts = [
        circle_svg(
            canvas,
            attachment.endpoint,
            6.0,
            "#ff5ae0",
            stroke="#0b0f14",
            stroke_width_px=1.2,
            extra_attrs=zoom_circle_attrs(6.0, 1.2),
        )
        for attachment in context.circuit_attachments
    ]
    layer_groups.append(f'<g id="layer-circuit-attachments">{"".join(attachment_parts)}</g>')

    procedure_anchor_parts: list[str] = []
    for anchor in context.procedure_anchors:
        if anchor.anchor_type != "vfr_route_join":
            continue
        tooltip = anchor.label if not anchor.note else f"{anchor.label}: {anchor.note}"
        procedure_anchor_parts.append(
            "<g>"
            f"<title>{html.escape(tooltip)}</title>"
            f"{circle_svg(canvas, anchor.point, 6.5, '#7ee787', stroke='#0b0f14', stroke_width_px=1.4, extra_attrs=zoom_circle_attrs(6.5, 1.4))}"
            f"{label_svg(canvas, anchor.point, anchor.label, '#d8ffd8', dx=10.0, dy=-10.0, stroke='#0b0f14', stroke_width_px=4.0, extra_attrs=zoom_label_attrs(15.0, 10.0, -10.0, 4.0))}"
            "</g>"
        )
    layer_groups.append(f'<g id="layer-procedure-anchors">{"".join(procedure_anchor_parts)}</g>')

    working_sector_parts = [
        polyline_svg(canvas, line.start, line.end, "#ff6b6b", 3.2, opacity=0.92, dash="12 8")
        for line in context.working_airspace_sector_lines
    ]
    layer_groups.append(f'<g id="layer-vfr-operational-sectors">{"".join(working_sector_parts)}</g>')

    vfr_route_parts: list[str] = []
    for route_segment in context.vfr_route_segments:
        vfr_route_parts.append(
            "<g>"
            f"<title>{html.escape(f'{route_segment.route_id}: {route_segment.start_ref} -> {route_segment.end_ref}')}</title>"
            f"{polyline_svg(canvas, route_segment.start, route_segment.end, '#6ee7b7', 3.0, opacity=0.92)}"
            "</g>"
        )
    layer_groups.append(f'<g id="layer-vfr-routes">{"".join(vfr_route_parts)}</g>')

    reporting_parts: list[str] = []
    for reporting_point in context.reporting_points:
        reporting_parts.append(
            "<g>"
            f"<title>{html.escape(reporting_point.code_id)}</title>"
            f"{circle_svg(canvas, reporting_point.point, 5.0, '#2dd4bf', stroke='#0b0f14', stroke_width_px=1.0, extra_attrs=zoom_circle_attrs(5.0, 1.0))}"
            f"{label_svg(canvas, reporting_point.point, reporting_point.code_id, '#9ff3e6', dx=10.0, dy=-10.0, font_size=14.0, stroke='#0b0f14', stroke_width_px=4.0, extra_attrs=zoom_label_attrs(14.0, 10.0, -10.0, 4.0))}"
            "</g>"
        )
    layer_groups.append(f'<g id="layer-vfr-reporting">{"".join(reporting_parts)}</g>')

    tower_parts: list[str] = []
    if context.tower_xy is not None:
        tower_x, tower_y = canvas.map(context.tower_xy)
        tower_parts.append(
            f'<g class="zoom-cross" data-center-x="{tower_x:.2f}" data-center-y="{tower_y:.2f}" '
            f'data-base-size="18.00" data-base-stroke-width="2.20" transform="translate({tower_x:.2f} {tower_y:.2f})">'
            '<line x1="-9.00" y1="-9.00" x2="9.00" y2="9.00" stroke="#ffffff" stroke-width="2.20" />'
            '<line x1="-9.00" y1="9.00" x2="9.00" y2="-9.00" stroke="#ffffff" stroke-width="2.20" />'
            '</g>'
        )
        tower_parts.append(
            label_svg(
                canvas,
                context.tower_xy,
                "TWR",
                "#ffffff",
                stroke="#0b0f14",
                stroke_width_px=4.0,
                extra_attrs=zoom_label_attrs(16.0, 8.0, -8.0, 4.0),
            ),
        )
    layer_groups.append(f'<g id="layer-tower">{"".join(tower_parts)}</g>')

    plate_pack_path = output_dir / "plate" / "index.html"
    plate_link = (
        f'<li><a href="{html.escape(str(Path("plate") / "index.html"))}">generated LOWG plate pack</a></li>'
        if plate_pack_path.exists()
        else ""
    )
    static_links = plate_link + "".join(
        f'<li><a href="{html.escape(path.name)}">{html.escape(path.name)}</a></li>'
        for path in files
    )
    gap_items = "".join(
        f"<li>{html.escape(gap)}</li>"
        for gap in context.manifest.get("knownGaps", [])
    )
    airspace_note_items = "".join(
        f"<li>{html.escape(note)}</li>"
        for note in airspace_notes
    ) or "<li>All LOWG airspace boundaries in the current view are straight-vertex approximations.</li>"
    layer_items = "\n".join(
        [
            '<label><input type="checkbox" data-layer-target="layer-airspace-primary" checked /> LOWG controlled airspace</label>',
            '<label><input type="checkbox" data-layer-target="layer-airspace-secondary" /> Nearby special-use airspace</label>',
            '<label><input type="checkbox" data-layer-target="layer-runways" checked /> apt.dat runways</label>',
            '<label><input type="checkbox" data-layer-target="layer-taxi-reference" checked /> apt.dat taxi reference</label>',
            '<label><input type="checkbox" data-layer-target="layer-taxi-signs" /> apt.dat taxi signs</label>',
            '<label><input type="checkbox" data-layer-target="layer-parking-access" checked /> X-Plane parking access via taxiway A</label>',
            '<label><input type="checkbox" data-layer-target="layer-ground-geometry" checked /> Hand-authored ground geometry</label>',
            '<label><input type="checkbox" data-layer-target="layer-ground-markers" checked /> Ground markers</label>',
            '<label><input type="checkbox" data-layer-target="layer-vfr-circuit" checked /> VFR circuit geometry</label>',
            '<label><input type="checkbox" data-layer-target="layer-circuit-attachments" checked /> Circuit join points</label>',
            '<label><input type="checkbox" data-layer-target="layer-procedure-anchors" checked /> Circuit / procedure anchors</label>',
            '<label><input type="checkbox" data-layer-target="layer-vfr-operational-sectors" checked /> Working VFR operational sectors</label>',
            '<label><input type="checkbox" data-layer-target="layer-vfr-routes" checked /> Sidecar VFR route paths</label>',
            '<label><input type="checkbox" data-layer-target="layer-vfr-reporting" checked /> OFMX VFR reporting points</label>',
            '<label><input type="checkbox" data-layer-target="layer-tower" checked /> Tower</label>',
        ],
    )
    view_state = {
        "airport": airport_view,
        "airspace": airspace_view,
        "full": full_view,
    }
    html_text = f"""<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>LOWG authoring map</title>
    <style>
      :root {{
        color-scheme: dark;
      }}
      body {{
        margin: 0;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        background: #0b0f14;
        color: #f0f6fc;
        min-height: 100vh;
        overflow: hidden;
      }}
      .layout {{
        display: grid;
        grid-template-columns: 320px 1fr;
        height: 100vh;
        overflow: hidden;
      }}
      .sidebar {{
        padding: 22px 20px 28px;
        background: #101720;
        border-right: 1px solid #223041;
        box-sizing: border-box;
        height: 100vh;
        overflow-y: auto;
        overscroll-behavior: contain;
      }}
      h1 {{
        margin: 0 0 10px;
        font-size: 24px;
      }}
      h2 {{
        margin: 24px 0 10px;
        font-size: 15px;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: #9fb0c0;
      }}
      p {{
        margin: 0 0 12px;
        line-height: 1.45;
        color: #c6d1dc;
      }}
      a {{
        color: #7cc7ff;
      }}
      .layer-list,
      .notes,
      .links {{
        display: grid;
        gap: 10px;
      }}
      .layer-list label {{
        display: grid;
        grid-template-columns: 18px 1fr;
        gap: 10px;
        align-items: start;
        color: #d0d7de;
      }}
      .controls {{
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
      }}
      button {{
        background: #172231;
        color: #f0f6fc;
        border: 1px solid #2f425a;
        border-radius: 8px;
        padding: 8px 12px;
        font: inherit;
        cursor: pointer;
      }}
      button:hover {{
        background: #203247;
      }}
      ul {{
        margin: 0;
        padding-left: 18px;
        color: #c6d1dc;
        line-height: 1.4;
      }}
      .swatches {{
        display: grid;
        gap: 8px;
      }}
      .swatch {{
        display: grid;
        grid-template-columns: 18px 1fr;
        gap: 10px;
        align-items: center;
      }}
      .chip {{
        width: 18px;
        height: 18px;
        border-radius: 4px;
        border: 1px solid #111821;
      }}
      .map-shell {{
        position: relative;
        min-width: 0;
        height: 100vh;
        overflow: hidden;
        background:
          radial-gradient(circle at top, rgba(54, 83, 122, 0.16), transparent 36%),
          linear-gradient(180deg, #0a0f14 0%, #0d141c 100%);
      }}
      #map-svg {{
        width: 100%;
        height: 100vh;
        display: block;
        cursor: grab;
        touch-action: none;
      }}
      #map-svg.dragging {{
        cursor: grabbing;
      }}
      #viewport line,
      #viewport polyline,
      #viewport polygon {{
        vector-effect: non-scaling-stroke;
      }}
      .layer-hidden {{
        display: none;
      }}
      .hud {{
        position: absolute;
        right: 18px;
        bottom: 18px;
        background: rgba(10, 15, 20, 0.86);
        border: 1px solid #223041;
        border-radius: 10px;
        padding: 12px 14px;
        max-width: 360px;
        line-height: 1.45;
        color: #c6d1dc;
      }}
      .hud strong {{
        color: #f0f6fc;
      }}
      @media (max-width: 980px) {{
        body {{
          overflow: auto;
        }}
        .layout {{
          grid-template-columns: 1fr;
          height: auto;
          overflow: visible;
        }}
        .sidebar {{
          border-right: none;
          border-bottom: 1px solid #223041;
          height: auto;
        }}
        #map-svg,
        .map-shell {{
          min-height: 70vh;
          height: 70vh;
        }}
      }}
    </style>
  </head>
  <body>
    <div class="layout">
      <aside class="sidebar">
        <h1>LOWG Authoring Map</h1>
        <p>Single combined view from the current LOWG manifest, both DXFs, apt.dat, OFMX reporting points, sidecar-defined VFR route paths, OFMX airspace boundaries, X-Plane-derived parking access spurs, and any normalized working-airspace overlay.</p>
        <h2>View</h2>
        <div class="controls">
          <button type="button" data-fit-view="airport">Airport</button>
          <button type="button" data-fit-view="airspace">Airspace</button>
          <button type="button" data-fit-view="full">Full</button>
        </div>
        <h2>Layers</h2>
        <div class="layer-list">
          {layer_items}
        </div>
        <h2>Legend</h2>
        <div class="swatches">
          <div class="swatch"><span class="chip" style="background:#143244;border-color:#58d4ff;"></span><span>LOWG controlled airspace</span></div>
          <div class="swatch"><span class="chip" style="background:#41204a;border-color:#f2a7ff;"></span><span>Nearby special-use airspace</span></div>
          <div class="swatch"><span class="chip" style="background:#7b8794;"></span><span>apt.dat runways</span></div>
          <div class="swatch"><span class="chip" style="background:#39424d;"></span><span>apt.dat taxi reference</span></div>
          <div class="swatch"><span class="chip" style="background:#d9ff6a;"></span><span>apt.dat taxi signs</span></div>
          <div class="swatch"><span class="chip" style="background:#16384a;border-color:#7dd3fc;"></span><span>X-Plane parking access via A spurs</span></div>
          <div class="swatch"><span class="chip" style="background:#42b6ff;"></span><span>Hand-authored ground geometry</span></div>
          <div class="swatch"><span class="chip" style="background:#f6bd60;"></span><span>Known divergence from X-Plane</span></div>
          <div class="swatch"><span class="chip" style="background:#ffb347;"></span><span>VFR circuit graph</span></div>
          <div class="swatch"><span class="chip" style="background:#ff6b6b;"></span><span>Working VFR operational sectors</span></div>
          <div class="swatch"><span class="chip" style="background:#6ee7b7;"></span><span>VFR route paths</span></div>
          <div class="swatch"><span class="chip" style="background:#2dd4bf;"></span><span>VFR reporting points</span></div>
        </div>
        <h2>Known Gaps</h2>
        <ul class="notes">
          {gap_items}
        </ul>
        <h2>Airspace Notes</h2>
        <ul class="notes">
          {airspace_note_items}
        </ul>
        <h2>Static Renders</h2>
        <ul class="links">
          {static_links}
        </ul>
      </aside>
      <main class="map-shell">
        <svg
          id="map-svg"
          xmlns="http://www.w3.org/2000/svg"
          viewBox="{airport_view['x']:.2f} {airport_view['y']:.2f} {airport_view['width']:.2f} {airport_view['height']:.2f}"
          data-view-states='{html.escape(json.dumps(view_state))}'
        >
          <rect x="0" y="0" width="{canvas.width_px:.2f}" height="{canvas.height_px:.2f}" fill="#0b0f14" />
          <g id="viewport">
            {"".join(layer_groups)}
          </g>
        </svg>
        <div class="hud">
          <strong>Interaction</strong><br />
          Drag to pan. Use the mouse wheel or trackpad to zoom. Toggle layers from the left panel to compare the authored geometry against X-Plane and the OFMX airspace set.
        </div>
      </main>
    </div>
    <script>
      const svg = document.getElementById("map-svg");
      const viewStates = JSON.parse(svg.dataset.viewStates);
      let currentView = {{ ...viewStates.airport }};
      let dragState = null;

      const updateZoomStyles = () => {{
        const ctm = svg.getScreenCTM();
        if (!ctm || Math.abs(ctm.a) < 0.000001) {{
          return;
        }}
        const unitsPerCssPixel = 1 / Math.abs(ctm.a);

        document.querySelectorAll(".zoom-circle").forEach((circle) => {{
          const baseRadius = Number(circle.dataset.baseRadius);
          const baseStrokeWidth = Number(circle.dataset.baseStrokeWidth);
          circle.setAttribute("r", `${{baseRadius * unitsPerCssPixel}}`);
          circle.setAttribute("stroke-width", `${{baseStrokeWidth * unitsPerCssPixel}}`);
        }});

        document.querySelectorAll(".zoom-label").forEach((label) => {{
          const baseFontSize = Number(label.dataset.baseFontSize);
          const baseDx = Number(label.dataset.baseDx);
          const baseDy = Number(label.dataset.baseDy);
          const baseStrokeWidth = Number(label.dataset.baseStrokeWidth);
          label.setAttribute("font-size", `${{baseFontSize * unitsPerCssPixel}}`);
          label.setAttribute("dx", `${{baseDx * unitsPerCssPixel}}`);
          label.setAttribute("dy", `${{baseDy * unitsPerCssPixel}}`);
          label.setAttribute("stroke-width", `${{baseStrokeWidth * unitsPerCssPixel}}`);
        }});

        document.querySelectorAll(".zoom-cross").forEach((cross) => {{
          const centerX = Number(cross.dataset.centerX);
          const centerY = Number(cross.dataset.centerY);
          const baseSize = Number(cross.dataset.baseSize);
          const baseStrokeWidth = Number(cross.dataset.baseStrokeWidth);
          const scale = unitsPerCssPixel;
          cross.setAttribute("transform", `translate(${{centerX}} ${{centerY}}) scale(${{scale}})`);
          cross.querySelectorAll("line").forEach((line) => {{
            line.setAttribute("stroke-width", `${{baseStrokeWidth}}`);
          }});
        }});
      }};

      const applyView = () => {{
        svg.setAttribute("viewBox", `${{currentView.x}} ${{currentView.y}} ${{currentView.width}} ${{currentView.height}}`);
        updateZoomStyles();
      }};

      const cursorPoint = (event) => {{
        const point = svg.createSVGPoint();
        point.x = event.clientX;
        point.y = event.clientY;
        return point.matrixTransform(svg.getScreenCTM().inverse());
      }};

      const fitView = (name) => {{
        currentView = {{ ...viewStates[name] }};
        applyView();
      }};

      const zoomAt = (factor, center) => {{
        currentView = {{
          x: center.x - ((center.x - currentView.x) * factor),
          y: center.y - ((center.y - currentView.y) * factor),
          width: currentView.width * factor,
          height: currentView.height * factor,
        }};
        applyView();
      }};

      svg.addEventListener("wheel", (event) => {{
        event.preventDefault();
        const factor = event.deltaY < 0 ? 0.88 : 1.14;
        zoomAt(factor, cursorPoint(event));
      }}, {{ passive: false }});

      svg.addEventListener("pointerdown", (event) => {{
        if (event.button !== 0) {{
          return;
        }}
        svg.setPointerCapture(event.pointerId);
        dragState = {{
          pointerId: event.pointerId,
          origin: cursorPoint(event),
          startView: {{ ...currentView }},
        }};
        svg.classList.add("dragging");
      }});

      svg.addEventListener("pointermove", (event) => {{
        if (dragState === null || dragState.pointerId !== event.pointerId) {{
          return;
        }}
        const currentPoint = cursorPoint(event);
        currentView = {{
          ...dragState.startView,
          x: dragState.startView.x - (currentPoint.x - dragState.origin.x),
          y: dragState.startView.y - (currentPoint.y - dragState.origin.y),
        }};
        applyView();
      }});

      const endDrag = (event) => {{
        if (dragState !== null && dragState.pointerId === event.pointerId) {{
          dragState = null;
          svg.classList.remove("dragging");
        }}
      }};

      svg.addEventListener("pointerup", endDrag);
      svg.addEventListener("pointercancel", endDrag);
      svg.addEventListener("dblclick", () => fitView("airport"));

      document.querySelectorAll("[data-fit-view]").forEach((button) => {{
        button.addEventListener("click", () => fitView(button.dataset.fitView));
      }});

      document.querySelectorAll("[data-layer-target]").forEach((input) => {{
        const target = document.getElementById(input.dataset.layerTarget);
        if (!input.checked) {{
          target.classList.add("layer-hidden");
        }}
        input.addEventListener("change", () => {{
          target.classList.toggle("layer-hidden", !input.checked);
        }});
      }});

      window.addEventListener("resize", updateZoomStyles);
      applyView();
    </script>
  </body>
</html>
"""
    (output_dir / "index.html").write_text(html_text)


def build_context(manifest_path: Path) -> SceneContext:
    root = report.repo_root()
    manifest = report.load_manifest(manifest_path)

    apt_path = report.resolve_path(root, manifest["sources"]["aptDat"])
    ofmx_path = report.resolve_path(root, manifest["sources"]["ofmx"])
    runways, tower, taxi_nodes, taxi_edges, apt_metadata, parking_positions = report.parse_apt(apt_path)
    taxi_signs = report.parse_apt_signs(apt_path)
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = report.projector(origin)
    reference_runway = manifest["geometricControl"]["referenceRunway"]
    runway_axis_start, runway_axis_end = report.runway_axis_xy(runways, reference_runway, project)

    ground_manifest = manifest_drawing(manifest, "ground")
    circuit_manifest = manifest_drawing(manifest, "vfr_circuit")
    ground_document = (
        report.parse_dxf(report.resolve_path(root, ground_manifest["path"]))
        if ground_manifest is not None
        else empty_dxf_document()
    )
    circuit_document = (
        report.parse_dxf(report.resolve_path(root, circuit_manifest["path"]))
        if circuit_manifest is not None
        else empty_dxf_document()
    )

    if ground_manifest is not None and ground_document.lines:
        longest_ground = max(ground_document.lines, key=lambda line: line.length)
        ground_transform = choose_transform(
            ground_manifest["transform"]["orientation"],
            report.fit_similarity(longest_ground.start, longest_ground.end, runway_axis_start, runway_axis_end),
            report.fit_similarity(longest_ground.end, longest_ground.start, runway_axis_start, runway_axis_end),
        )
        ground_lines = transform_lines(ground_document.lines, ground_transform)
        ground_points = transform_points(ground_document.points, ground_transform)
        ground_components = transform_components(report.connected_components(ground_document.lines), ground_transform)
        if "reportThresholds" in manifest:
            ground_report = report.ground_alignment_report(
                ground_manifest,
                ground_document,
                runway_axis_start,
                runway_axis_end,
                taxi_nodes,
                taxi_edges,
                project,
                manifest["reportThresholds"],
                manifest.get("reconciliationOverrides", {}).get("ground"),
            )
            ground_component_status = {
                component["index"]: component["classification"]
                for component in ground_report["components"]
            }
            ground_component_label = {
                component["index"]: component["label"]
                for component in ground_report["components"]
                if component.get("label") is not None
            }
            ground_marker_status = {
                marker["index"]: marker["classification"]
                for marker in ground_report["markers"]
            }
            ground_marker_label = {
                marker["index"]: marker["label"]
                for marker in ground_report["markers"]
                if marker.get("label") is not None
            }
        else:
            ground_component_status = {}
            ground_component_label = {}
            ground_marker_status = {}
            ground_marker_label = {}
    else:
        ground_transform = identity_transform()
        ground_lines = []
        ground_points = []
        ground_components = []
        ground_component_status = {}
        ground_component_label = {}
        ground_marker_status = {}
        ground_marker_label = {}

    if circuit_manifest is not None and (circuit_document.points or circuit_document.lines):
        declared_circuit_anchor_pair = report.drawing_anchor_pair_from_manifest(
            manifest,
            circuit_manifest["id"],
            circuit_document,
        )
        if declared_circuit_anchor_pair is None:
            circuit_anchor_a, circuit_anchor_b = farthest_pair(circuit_document.points)
        else:
            (_, circuit_anchor_a), (_, circuit_anchor_b) = declared_circuit_anchor_pair
        circuit_transform = choose_transform(
            circuit_manifest["transform"]["orientation"],
            report.fit_similarity(circuit_anchor_a, circuit_anchor_b, runway_axis_start, runway_axis_end),
            report.fit_similarity(circuit_anchor_b, circuit_anchor_a, runway_axis_start, runway_axis_end),
        )
        raw_circuit_components = sorted(
            report.connected_components(circuit_document.lines),
            key=lambda component: len(component),
            reverse=True,
        )
        circuit_components = transform_components(raw_circuit_components, circuit_transform)
        circuit_lines = [line for component in circuit_components for line in component]
        circuit_points = transform_points(circuit_document.points, circuit_transform)
        if "reportThresholds" in manifest:
            raw_attachments = report.find_endpoint_attachments(
                raw_circuit_components,
                manifest["reportThresholds"]["segmentJoinToleranceDrawingUnits"],
            )
            circuit_attachments = [
                report.EndpointAttachment(
                    source_component=attachment.source_component,
                    target_component=attachment.target_component,
                    endpoint=circuit_transform.apply(attachment.endpoint),
                    target_segment_start=circuit_transform.apply(attachment.target_segment_start),
                    target_segment_end=circuit_transform.apply(attachment.target_segment_end),
                    distance=attachment.distance,
                    segment_position=attachment.segment_position,
                )
                for attachment in raw_attachments
            ]
        else:
            circuit_attachments = []
    else:
        circuit_transform = identity_transform()
        circuit_components = []
        circuit_lines = []
        circuit_points = []
        circuit_attachments = []

    ofmx_data = report.parse_ofmx(ofmx_path, manifest["airportCode"])
    openair_data = None
    if not ofmx_data["airspaces"]:
        openair_source = manifest["sources"].get("openAirBundle")
        if isinstance(openair_source, str):
            openair_data = report.parse_openair_bundle(
                report.resolve_path(root, openair_source),
                origin,
                manifest["airportCode"],
                manifest.get("airportName") or manifest["airportCode"],
            )
    reporting_points = [
        ReportingPoint(
            code_id=point.code_id,
            point=project(point.position),
            code_type=point.code_type,
        )
        for point in ofmx_data["airportDesignatedPoints"]
        if (point.code_type or "").startswith("VFR")
    ]
    procedure_anchors = build_procedure_anchor_shapes(
        manifest,
        circuit_points,
        circuit_lines,
        reporting_points,
    )
    vfr_route_segments = build_vfr_route_segments(manifest, reporting_points, procedure_anchors)
    working_airspace_path = root / "cad/airports" / f"{manifest['airportCode'].lower()}_airspace_working_normalized.dxf"
    if not working_airspace_path.exists():
        working_airspace_path = root / "cad/airports" / f"{manifest['airportCode'].lower()}_airspace_working.dxf"
    working_airspace_sector_lines: list[report.DxfLine] = []
    if working_airspace_path.exists():
        working_airspace_document = report.parse_dxf(working_airspace_path)
        working_airspace_sector_lines = [
            line
            for line in working_airspace_document.lines
            if line.layer == "VFR Op Sectors"
        ]

    projected_taxi_route_edges = project_taxi_route_edges(taxi_nodes, taxi_edges, project)
    projected_parking_positions = project_visible_parking_positions(parking_positions, project)
    parking_access_branches = infer_parking_access_branches(projected_parking_positions, projected_taxi_route_edges)
    taxi_edges_xy = [
        (edge.start, edge.end)
        for edge in projected_taxi_route_edges
    ]
    if ofmx_data["airspaces"]:
        airspace_shapes = [
            AirspaceShape(
                mid=airspace.mid,
                code_id=airspace.code_id,
                name=airspace.name or airspace.code_id or airspace.mid,
                label=airspace.name or airspace.code_id or airspace.mid,
                lower_limit=report.format_limit(airspace.lower_value, airspace.lower_unit, airspace.lower_reference),
                upper_limit=report.format_limit(airspace.upper_value, airspace.upper_unit, airspace.upper_reference),
                category="primary" if (airspace.name or "").startswith(manifest["airportCode"]) else "secondary",
                has_curve_vertices=any(
                    vertex.code_type != "GRC"
                    for boundary in ofmx_data["airspaceBoundaries"].get(airspace.mid, [])
                    for vertex in boundary.vertices
                ),
                boundaries=[
                    [project(vertex.position) for vertex in boundary.vertices]
                    for boundary in ofmx_data["airspaceBoundaries"].get(airspace.mid, [])
                    if len(boundary.vertices) >= 3
                ],
            )
            for airspace in ofmx_data["airspaces"]
            if ofmx_data["airspaceBoundaries"].get(airspace.mid)
        ]
    else:
        airspace_shapes = [
            AirspaceShape(
                mid=f"OPENAIR_{index:03d}",
                code_id=None,
                name=airspace.name,
                label=airspace.name,
                lower_limit=airspace.lower_limit or "?",
                upper_limit=airspace.upper_limit or "?",
                category="primary" if "MARIBOR" in airspace.name.upper() else "secondary",
                has_curve_vertices=False,
                boundaries=[
                    [project(point) for point in boundary]
                    for boundary in airspace.boundaries
                    if len(boundary) >= 3
                ],
            )
            for index, airspace in enumerate((openair_data or {"airspaces": []})["airspaces"], start=1)
        ]

    return SceneContext(
        root=root,
        manifest_path=manifest_path,
        manifest=manifest,
        apt_runways=unique_runways(runways, project),
        taxi_edges=taxi_edges_xy,
        taxi_route_edges=projected_taxi_route_edges,
        taxi_nodes=[project(node.position) for node in taxi_nodes.values()],
        taxi_signs=[
            TaxiSignShape(
                point=project(sign.position),
                heading_deg=sign.heading_deg,
                label=sign.display_text,
                raw_text=sign.raw_text,
            )
            for sign in taxi_signs
        ],
        tower_xy=project(tower.position) if tower is not None else None,
        parking_positions=projected_parking_positions,
        parking_access_branches=parking_access_branches,
        ground_lines=ground_lines,
        ground_points=ground_points,
        ground_components=ground_components,
        ground_component_status=ground_component_status,
        ground_component_label=ground_component_label,
        ground_marker_status=ground_marker_status,
        ground_marker_label=ground_marker_label,
        circuit_lines=circuit_lines,
        circuit_points=circuit_points,
        circuit_components=circuit_components,
        circuit_attachments=circuit_attachments,
        procedure_anchors=procedure_anchors,
        reporting_points=reporting_points,
        vfr_route_segments=vfr_route_segments,
        working_airspace_sector_lines=working_airspace_sector_lines,
        airspace_shapes=[shape for shape in airspace_shapes if shape.boundaries],
    )


def main() -> None:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    context = build_context(manifest_path)

    default_output = context.root / "cad/airports/rendered" / context.manifest["airportCode"].lower()
    output_dir = args.output_dir.resolve() if args.output_dir is not None else default_output
    output_dir.mkdir(parents=True, exist_ok=True)
    airport_slug = context.manifest["airportCode"].lower()

    files = [
        output_dir / f"{airport_slug}_ground_overlay.svg",
        output_dir / f"{airport_slug}_ground_divergence_zoom.svg",
        output_dir / f"{airport_slug}_vfr_circuit_overlay.svg",
    ]
    render_ground_overlay(context, files[0])
    render_ground_divergence_zoom(context, files[1])
    render_vfr_circuit_overlay(context, files[2])
    render_interactive_index(context, output_dir, files)

    for file_path in files:
        print(file_path)
    print(output_dir / "index.html")


if __name__ == "__main__":
    main()
