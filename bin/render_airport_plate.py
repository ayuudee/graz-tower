#!/usr/bin/env python3

from __future__ import annotations

import argparse
import html
import math
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report
import airport_plate_view_model as plate_vm
import render_airport_authoring as authoring


MAP_WIDTH_PX = 1040.0
MAP_HEIGHT_PX = 680.0
MAP_PADDING_PX = 28.0

PlateData = plate_vm.PlateViewModel


@dataclass(frozen=True)
class PlateMapCanvas:
    bounds: authoring.Bounds
    width_px: float
    height_px: float
    padding_px: float
    scale: float

    def map(self, point: report.XY) -> tuple[float, float]:
        x = self.padding_px + ((point.x - self.bounds.min_x) * self.scale)
        y = self.padding_px + ((self.bounds.max_y - point.y) * self.scale)
        return x, y


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Render a generated airport plate pack as HTML plus SVG page maps.")
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Directory to write the plate pack into. Defaults to cad/airports/rendered/<airport>/plate.",
    )
    return parser.parse_args()


def slugify(text: str) -> str:
    return "".join(character.lower() if character.isalnum() else "-" for character in text).strip("-")


def natural_sort_key(text: str) -> list[int | str]:
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", text)]


def build_map_canvas(points: list[report.XY], margin: float, width_px: float = MAP_WIDTH_PX, height_px: float = MAP_HEIGHT_PX) -> PlateMapCanvas:
    bounds = authoring.scene_bounds(points, margin)
    usable_width = max(width_px - (2.0 * MAP_PADDING_PX), 1.0)
    usable_height = max(height_px - (2.0 * MAP_PADDING_PX), 1.0)
    scale = min(usable_width / bounds.width, usable_height / bounds.height)
    return PlateMapCanvas(
        bounds=bounds,
        width_px=width_px,
        height_px=height_px,
        padding_px=MAP_PADDING_PX,
        scale=scale,
    )


def svg_document(width_px: float, height_px: float, body: str) -> str:
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width_px:.0f}" height="{height_px:.0f}" '
        f'viewBox="0 0 {width_px:.0f} {height_px:.0f}">'
        f'<rect x="0" y="0" width="{width_px:.0f}" height="{height_px:.0f}" fill="#f7f7f4" />'
        f"{body}</svg>"
    )


def svg_line(
    canvas: PlateMapCanvas,
    start: report.XY,
    end: report.XY,
    stroke: str,
    width_px: float,
    opacity: float = 1.0,
    dash: str | None = None,
) -> str:
    x1, y1 = canvas.map(start)
    x2, y2 = canvas.map(end)
    dash_attr = "" if dash is None else f' stroke-dasharray="{dash}"'
    return (
        f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" '
        f'stroke="{stroke}" stroke-width="{width_px:.2f}" stroke-opacity="{opacity:.3f}" '
        f'stroke-linecap="round"{dash_attr} />'
    )


def svg_polyline(
    canvas: PlateMapCanvas,
    points: list[report.XY],
    stroke: str,
    width_px: float,
    fill: str = "none",
    fill_opacity: float = 0.0,
    stroke_opacity: float = 1.0,
    dash: str | None = None,
    closed: bool = False,
) -> str:
    mapped_points = " ".join(f"{x:.2f},{y:.2f}" for x, y in (canvas.map(point) for point in points))
    dash_attr = "" if dash is None else f' stroke-dasharray="{dash}"'
    element = "polygon" if closed else "polyline"
    fill_attr = f'fill="{fill}" fill-opacity="{fill_opacity:.3f}"'
    return (
        f'<{element} points="{mapped_points}" {fill_attr} '
        f'stroke="{stroke}" stroke-width="{width_px:.2f}" stroke-opacity="{stroke_opacity:.3f}" '
        f'stroke-linejoin="round" stroke-linecap="round"{dash_attr} />'
    )


def svg_circle(
    canvas: PlateMapCanvas,
    point: report.XY,
    radius_px: float,
    fill: str,
    stroke: str | None = None,
    stroke_width_px: float = 1.0,
    opacity: float = 1.0,
) -> str:
    cx, cy = canvas.map(point)
    stroke_attr = "" if stroke is None else f' stroke="{stroke}" stroke-width="{stroke_width_px:.2f}"'
    return (
        f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{radius_px:.2f}" '
        f'fill="{fill}" fill-opacity="{opacity:.3f}"{stroke_attr} />'
    )


def svg_text(
    canvas: PlateMapCanvas,
    point: report.XY,
    text: str,
    fill: str,
    font_size: float = 14.0,
    dx: float = 0.0,
    dy: float = 0.0,
    anchor: str = "start",
    stroke: str | None = None,
    stroke_width_px: float = 3.0,
    rotate_deg: float | None = None,
) -> str:
    x, y = canvas.map(point)
    rotate_attr = "" if rotate_deg is None else f' transform="rotate({rotate_deg:.2f} {x:.2f} {y:.2f})"'
    stroke_attr = "" if stroke is None else f' stroke="{stroke}" stroke-width="{stroke_width_px:.2f}" paint-order="stroke"'
    return (
        f'<text x="{x + dx:.2f}" y="{y + dy:.2f}" fill="{fill}" font-family="monospace" '
        f'font-size="{font_size:.1f}" text-anchor="{anchor}"{stroke_attr}{rotate_attr}>{html.escape(text)}</text>'
    )


def boxed_label_svg(
    canvas: PlateMapCanvas,
    point: report.XY,
    text: str,
    fill_box: str = "#ffffff",
    fill_text: str = "#111111",
    stroke_box: str = "#333333",
    stroke_text: str | None = "#ffffff",
    font_size: float = 15.0,
    padding_x: float = 7.0,
    padding_y: float = 4.0,
    dx: float = 0.0,
    dy: float = 0.0,
    anchor: str = "center",
    rotate_deg: float | None = None,
) -> str:
    x, y = canvas.map(point)
    x += dx
    y += dy
    label_width = (len(text) * font_size * 0.61) + (2.0 * padding_x)
    label_height = font_size + (2.0 * padding_y)
    if anchor == "center":
        left = x - (label_width / 2.0)
    elif anchor == "end":
        left = x - label_width
    else:
        left = x
    top = y - font_size + 2.0 - padding_y
    rotate_attr = "" if rotate_deg is None else f' transform="rotate({rotate_deg:.2f} {x:.2f} {y:.2f})"'
    return (
        f'<g{rotate_attr}>'
        f'<rect x="{left:.2f}" y="{top:.2f}" width="{label_width:.2f}" height="{label_height:.2f}" '
        f'fill="{fill_box}" stroke="{stroke_box}" stroke-width="1.0" rx="2.5" />'
        f'<text x="{x:.2f}" y="{y:.2f}" fill="{fill_text}" font-family="monospace" '
        f'font-size="{font_size:.1f}" text-anchor="{anchor}"'
        + (
            f' stroke="{stroke_text}" stroke-width="2.6" paint-order="stroke"'
            if stroke_text is not None
            else ""
        )
        + f">{html.escape(text)}</text></g>"
    )


def midpoint(point_a: report.XY, point_b: report.XY) -> report.XY:
    return report.XY((point_a.x + point_b.x) / 2.0, (point_a.y + point_b.y) / 2.0)


def polyline_midpoint(points: list[report.XY]) -> report.XY:
    if len(points) == 1:
        return points[0]
    segment_lengths = [points[index].distance_to(points[index + 1]) for index in range(len(points) - 1)]
    total_length = sum(segment_lengths)
    if total_length <= 0.0:
        return points[0]
    target = total_length / 2.0
    cumulative = 0.0
    for index, length in enumerate(segment_lengths):
        if cumulative + length >= target and length > 0.0:
            ratio = (target - cumulative) / length
            start = points[index]
            end = points[index + 1]
            return report.XY(
                start.x + ((end.x - start.x) * ratio),
                start.y + ((end.y - start.y) * ratio),
            )
        cumulative += length
    return points[-1]


def path_angle_degrees(point_a: report.XY, point_b: report.XY) -> float:
    return math.degrees(math.atan2(point_b.y - point_a.y, point_b.x - point_a.x))


def collect_airspace_points(shapes: list[authoring.AirspaceShape]) -> list[report.XY]:
    return [point for shape in shapes for point in authoring.collect_points_from_boundaries(shape.boundaries)]


def background_grid_svg(canvas: PlateMapCanvas, step_m: float = 1000.0) -> str:
    start_x = math.floor(canvas.bounds.min_x / step_m) * step_m
    end_x = math.ceil(canvas.bounds.max_x / step_m) * step_m
    start_y = math.floor(canvas.bounds.min_y / step_m) * step_m
    end_y = math.ceil(canvas.bounds.max_y / step_m) * step_m
    parts: list[str] = []
    x_value = start_x
    while x_value <= end_x:
        parts.append(
            svg_line(
                canvas,
                report.XY(x_value, canvas.bounds.min_y),
                report.XY(x_value, canvas.bounds.max_y),
                "#e2e1dc",
                1.0,
            ),
        )
        x_value += step_m
    y_value = start_y
    while y_value <= end_y:
        parts.append(
            svg_line(
                canvas,
                report.XY(canvas.bounds.min_x, y_value),
                report.XY(canvas.bounds.max_x, y_value),
                "#e2e1dc",
                1.0,
            ),
        )
        y_value += step_m
    return "".join(parts)


def runway_polygon(runway: authoring.RunwayShape) -> list[report.XY]:
    dx = runway.end.x - runway.start.x
    dy = runway.end.y - runway.start.y
    length = math.hypot(dx, dy)
    if length <= 0.0:
        return [runway.start, runway.end, runway.end, runway.start]
    ux = dx / length
    uy = dy / length
    px = -uy
    py = ux
    half_width = runway.width_m / 2.0
    return [
        report.XY(runway.start.x + (px * half_width), runway.start.y + (py * half_width)),
        report.XY(runway.end.x + (px * half_width), runway.end.y + (py * half_width)),
        report.XY(runway.end.x - (px * half_width), runway.end.y - (py * half_width)),
        report.XY(runway.start.x - (px * half_width), runway.start.y - (py * half_width)),
    ]


def runway_shape_list_svg(shapes: list[authoring.RunwayShape], canvas: PlateMapCanvas, emphasis: set[str] | None = None) -> str:
    emphasis = emphasis or set()
    parts: list[str] = []
    for runway in shapes:
        polygon = runway_polygon(runway)
        fill = "#111111" if runway.pair in emphasis else "#ffffff"
        stroke = "#0f1114"
        parts.append(svg_polyline(canvas, polygon, stroke, 1.8, fill=fill, fill_opacity=1.0, closed=True))
        parts.append(svg_line(canvas, runway.start, runway.end, "#4d4d4d", 1.2, dash="10 7"))
        label_color = "#ffffff" if runway.pair in emphasis else "#111111"
        parts.append(
            boxed_label_svg(
                canvas,
                midpoint(runway.start, runway.end),
                runway.pair,
                fill_box="#ffffff" if runway.pair not in emphasis else "#111111",
                fill_text=label_color,
                stroke_box="#111111",
                stroke_text=None,
                font_size=13.0,
            ),
        )
    return "".join(parts)

def airspace_shapes_svg(
    canvas: PlateMapCanvas,
    shapes: list[authoring.AirspaceShape],
    fill: str,
    stroke: str,
    fill_opacity: float,
    stroke_width_px: float = 1.4,
    dashed: bool = False,
    label: bool = False,
) -> str:
    dash = "8 6" if dashed else None
    parts: list[str] = []
    for shape in shapes:
        for boundary in shape.boundaries:
            parts.append(
                svg_polyline(
                    canvas,
                    boundary,
                    stroke=stroke,
                    width_px=stroke_width_px,
                    fill=fill,
                    fill_opacity=fill_opacity,
                    stroke_opacity=0.95,
                    dash=dash,
                    closed=True,
                ),
            )
        if label and shape.boundaries:
            center = authoring.centroid(shape.boundaries[0])
            parts.append(
                boxed_label_svg(
                    canvas,
                    center,
                    authoring.airspace_label(shape),
                    fill_box="#ffffff",
                    fill_text="#1c2a3a",
                    stroke_box=stroke,
                    stroke_text=None,
                    font_size=12.0,
                ),
            )
    return "".join(parts)

def route_polyline_svg(
    canvas: PlateMapCanvas,
    points: list[report.XY],
    label: str | None = None,
    color: str = "#1e3f6d",
    width_px: float = 3.4,
) -> str:
    if len(points) < 2:
        return ""
    parts = [svg_polyline(canvas, points, color, width_px, stroke_opacity=0.98)]
    if label is not None:
        center = polyline_midpoint(points)
        anchor_segment_start = points[max((len(points) // 2) - 1, 0)]
        anchor_segment_end = points[min(len(points) // 2, len(points) - 1)]
        parts.append(
            boxed_label_svg(
                canvas,
                center,
                label,
                fill_box=color,
                fill_text="#ffffff",
                stroke_box=color,
                stroke_text=None,
                font_size=12.5,
                rotate_deg=path_angle_degrees(anchor_segment_start, anchor_segment_end),
            ),
        )
    return "".join(parts)


def reporting_points_svg(
    canvas: PlateMapCanvas,
    reporting_lookup: dict[str, report.XY],
    labels: list[str],
    fill: str = "#1e3f6d",
    text_fill: str = "#111111",
    label_overrides: dict[str, str] | None = None,
) -> str:
    label_overrides = label_overrides or {}
    parts: list[str] = []
    for label in labels:
        point = reporting_lookup.get(label)
        if point is None:
            continue
        display_label = label_overrides.get(label, label)
        parts.append(svg_circle(canvas, point, 5.5, fill, stroke="#ffffff", stroke_width_px=1.4))
        parts.append(
            boxed_label_svg(
                canvas,
                point,
                display_label,
                fill_box="#ffffff",
                fill_text=text_fill,
                stroke_box="#444444",
                stroke_text=None,
                font_size=12.5,
                dy=-14.0,
                anchor="center",
            ),
        )
    return "".join(parts)

def circuit_lines_svg(
    canvas: PlateMapCanvas,
    primary: list[report.DxfLine],
    secondary: list[report.DxfLine] | None = None,
    tertiary: list[report.DxfLine] | None = None,
) -> str:
    parts: list[str] = []
    if tertiary is not None:
        for line in tertiary:
            parts.append(svg_line(canvas, line.start, line.end, "#b7bcc4", 1.8, opacity=0.55))
    if secondary is not None:
        for line in secondary:
            parts.append(svg_line(canvas, line.start, line.end, "#6283b5", 2.2, opacity=0.82))
    for line in primary:
        parts.append(svg_line(canvas, line.start, line.end, "#1f45a0", 3.0, opacity=0.98))
    return "".join(parts)


def tower_svg(data: PlateData, canvas: PlateMapCanvas) -> str:
    tower_point = data.anchor_lookup.get("tower")
    if tower_point is None:
        return ""
    cx, cy = canvas.map(tower_point)
    return (
        f'<g><rect x="{cx - 7:.2f}" y="{cy - 7:.2f}" width="14" height="14" fill="#f8df52" stroke="#111111" stroke-width="1.2" />'
        f'{svg_text(canvas, tower_point, "C", "#111111", font_size=12.0, anchor="middle", dy=4.5)}</g>'
    )


def polygon_or_lines_points(shape: authoring.AirspaceShape) -> list[report.XY]:
    return authoring.collect_points_from_boundaries(shape.boundaries)


def current_core_aerodrome(data: PlateData) -> dict[str, Any]:
    return data.current_core_aerodrome


def current_core_geometry_paths(data: PlateData) -> dict[str, dict[str, Any]]:
    return data.current_core_geometry_paths


def current_core_runway_shapes(data: PlateData) -> list[authoring.RunwayShape]:
    runways = current_core_aerodrome(data).get("runways", {})
    geometry_paths = current_core_geometry_paths(data)
    grouped: dict[tuple[str, str], dict[str, Any]] = {}
    for runway_id, runway in runways.items():
        path_id = runway.get("pathId")
        if not isinstance(path_id, str):
            continue
        path = geometry_paths.get(path_id)
        points = data.current_core_path_lookup.get(path_id)
        if path is None or points is None or len(points) < 2:
            continue
        endpoint_pair = tuple(sorted((path["pointIds"][0], path["pointIds"][-1])))
        grouped.setdefault(
            endpoint_pair,
            {
                "ids": [],
                "points": points,
                "widthMeters": float(path.get("widthMeters", 45.0)),
            },
        )["ids"].append(runway_id)
    order = {
        "16C/34C": 0,
        "16R/34L": 1,
        "16L/34R": 2,
    }
    shapes = [
        authoring.RunwayShape(
            pair="/".join(sorted(group["ids"])),
            start=group["points"][0],
            end=group["points"][-1],
            width_m=group["widthMeters"],
        )
        for group in grouped.values()
    ]
    return sorted(shapes, key=lambda shape: order.get(shape.pair, 99))


def current_core_runway_shapes_svg(data: PlateData, canvas: PlateMapCanvas, emphasis: set[str] | None = None) -> str:
    return runway_shape_list_svg(current_core_runway_shapes(data), canvas, emphasis=emphasis)


def current_core_taxiway_paths_svg(
    data: PlateData,
    canvas: PlateMapCanvas,
    highlight_ids: set[str] | None = None,
    limit_ids: set[str] | None = None,
) -> str:
    highlight_ids = highlight_ids or set()
    taxiways = current_core_aerodrome(data).get("taxiways", {})
    order = {"A": 0, "B": 1, "C": 2, "Y": 3, "Z": 4}
    parts: list[str] = []
    for taxiway_id, taxiway in sorted(taxiways.items(), key=lambda item: order.get(item[0], 99)):
        if limit_ids is not None and taxiway_id not in limit_ids:
            continue
        path_points = data.current_core_path_lookup.get(taxiway.get("pathId", ""))
        if path_points is None or len(path_points) < 2:
            continue
        is_highlighted = taxiway_id in highlight_ids
        color = "#3c4148" if not is_highlighted else "#cf9b1f"
        width = 2.1 if not is_highlighted else 3.6
        opacity = 0.9 if is_highlighted else 0.76
        parts.append(svg_polyline(canvas, path_points, color, width, stroke_opacity=opacity))
        parts.append(
            boxed_label_svg(
                canvas,
                polyline_midpoint(path_points),
                taxiway.get("name", taxiway_id),
                fill_box="#fff4a8" if is_highlighted else "#ffffff",
                fill_text="#111111",
                stroke_box="#2c2c2c",
                stroke_text=None,
                font_size=13.0,
            ),
        )
    return "".join(parts)


def current_core_holding_points_svg(data: PlateData, canvas: PlateMapCanvas) -> str:
    taxiways = current_core_aerodrome(data).get("taxiways", {})
    grouped: dict[str, dict[str, Any]] = {}
    for taxiway in taxiways.values():
        for holding_point in taxiway.get("holdingPoints", []):
            point_id = holding_point.get("pointId")
            if not isinstance(point_id, str):
                continue
            grouped.setdefault(
                point_id,
                {
                    "point": data.current_core_point_lookup.get(point_id),
                    "labels": [],
                },
            )
            name = holding_point.get("name")
            runway_id = holding_point.get("runwayId")
            if isinstance(name, str) and isinstance(runway_id, str):
                grouped[point_id]["labels"].append(f"{name} {runway_id}")
            elif isinstance(name, str):
                grouped[point_id]["labels"].append(name)
            elif isinstance(runway_id, str):
                grouped[point_id]["labels"].append(runway_id)
    parts: list[str] = []
    for item in grouped.values():
        point = item.get("point")
        if point is None:
            continue
        label = " / ".join(item.get("labels", []))
        parts.append(svg_circle(canvas, point, 5.5, "#f8df52", stroke="#111111", stroke_width_px=1.1))
        if label:
            parts.append(
                boxed_label_svg(
                    canvas,
                    point,
                    label,
                    fill_box="#f8df52",
                    fill_text="#111111",
                    stroke_box="#2a2a2a",
                    stroke_text=None,
                    font_size=11.5,
                    dx=14.0,
                    dy=-10.0,
                    anchor="start",
                ),
            )
    return "".join(parts)


def current_core_aprons_svg(data: PlateData, canvas: PlateMapCanvas) -> str:
    aprons = current_core_aerodrome(data).get("aprons", {})
    parts: list[str] = []
    for _, apron in sorted(aprons.items(), key=lambda item: item[1].get("name", "")):
        path_ids = apron.get("pathIds", [])
        if not path_ids:
            continue
        branch_name = apron.get("name", "").split("/")[-1]
        fill, stroke = authoring.parking_branch_palette(branch_name)
        main_path_points = data.current_core_path_lookup.get(path_ids[0], [])
        if len(main_path_points) >= 2:
            parts.append(svg_polyline(canvas, main_path_points, stroke, 2.8, stroke_opacity=0.95))
            parts.append(
                boxed_label_svg(
                    canvas,
                    polyline_midpoint(main_path_points),
                    apron.get("name", ""),
                    fill_box="#ffffff",
                    fill_text=stroke,
                    stroke_box=stroke,
                    stroke_text=None,
                    font_size=11.5,
                ),
            )
        for path_id in path_ids[1:]:
            path_points = data.current_core_path_lookup.get(path_id, [])
            if len(path_points) >= 2:
                parts.append(svg_polyline(canvas, path_points, stroke, 1.5, stroke_opacity=0.78, dash="7 5"))
    return "".join(parts)


def current_core_stands_svg(data: PlateData, canvas: PlateMapCanvas) -> str:
    stands = current_core_aerodrome(data).get("stands", {})
    parts: list[str] = []
    for _, stand in sorted(stands.items(), key=lambda item: item[1].get("name", "")):
        point = data.current_core_point_lookup.get(stand.get("pointId", ""))
        if point is None:
            continue
        parts.append(svg_circle(canvas, point, 4.6, "#ffffff", stroke="#111111", stroke_width_px=1.1))
        parts.append(
            boxed_label_svg(
                canvas,
                point,
                stand.get("name", ""),
                fill_box="#ffffff",
                fill_text="#111111",
                stroke_box="#444444",
                stroke_text=None,
                font_size=11.0,
                dx=9.0,
                dy=-8.0,
                anchor="start",
            ),
        )
    return "".join(parts)


def build_plate_data(manifest_path: Path) -> PlateData:
    return plate_vm.build_plate_view_model(manifest_path)


def route_points(data: PlateData, point_refs: list[str]) -> list[report.XY]:
    points: list[report.XY] = []
    for point_ref in point_refs:
        point = data.reporting_lookup.get(point_ref) or data.anchor_lookup.get(point_ref)
        if point is not None:
            points.append(point)
    return points


def vfr_route_for_id(data: PlateData, route_id: str) -> dict[str, Any] | None:
    return data.candidate_vfr_routes.get(route_id)


def route_points_for_vfr_route(data: PlateData, route_id: str) -> list[report.XY]:
    route = vfr_route_for_id(data, route_id)
    if route is None:
        return []
    point_refs = route.get("publishedNodeRefs", [])
    if isinstance(point_refs, list) and point_refs:
        return route_points(data, point_refs)
    point_ids = route.get("pointIds", [])
    if not isinstance(point_ids, list):
        return []
    return [
        data.current_core_point_lookup[point_id]
        for point_id in point_ids
        if point_id in data.current_core_point_lookup
    ]


def candidate_circuit_graph(data: PlateData, graph_id: str) -> dict[str, Any] | None:
    graph = data.candidate_circuit_graphs.get(graph_id)
    return graph if isinstance(graph, dict) else None


def current_core_circuit(data: PlateData, circuit_id: str) -> dict[str, Any] | None:
    circuit = current_core_aerodrome(data).get("circuits", {}).get(circuit_id)
    return circuit if isinstance(circuit, dict) else None


def entity_route_items(data: PlateData, route_id: str) -> list[str]:
    route = vfr_route_for_id(data, route_id)
    if route is None:
        return [f"{route_id}: not projected."]
    items: list[str] = []
    node_refs = route.get("publishedNodeRefs", [])
    if isinstance(node_refs, list) and node_refs:
        items.append("route: " + " -> ".join(str(item) for item in node_refs))
    point_ids = route.get("pointIds", [])
    if isinstance(point_ids, list) and point_ids:
        items.append("point ids: " + " -> ".join(str(item) for item in point_ids))
    airspace_profile = route.get("airspaceProfile")
    if isinstance(airspace_profile, dict):
        items.append(f"airspace profile: {airspace_profile.get('kind', '?')}")
    projection_status = route.get("projectionStatus")
    if isinstance(projection_status, str):
        items.append(f"projection status: {projection_status}")
    blocked_fields = route.get("blockedFields", [])
    if isinstance(blocked_fields, list) and blocked_fields:
        items.append("blocked fields: " + ", ".join(str(item) for item in blocked_fields))
    return items


def circuit_graph_items(data: PlateData, graph_id: str) -> list[str]:
    graph = candidate_circuit_graph(data, graph_id)
    if graph is None:
        return [f"{graph_id}: not projected."]
    items = [
        f"path id: {graph.get('pathId', '?')}",
        f"closed: {graph.get('closed', False)}",
    ]
    projection_status = graph.get("projectionStatus")
    if isinstance(projection_status, str):
        items.append(f"projection status: {projection_status}")
    return items


def circuit_procedure_items(data: PlateData, circuit_id: str) -> list[str]:
    circuit = current_core_circuit(data, circuit_id)
    if circuit is None:
        return [f"{circuit_id}: not projected."]
    items = [
        f"runway: {circuit.get('runwayId', '?')}",
        f"direction: {circuit.get('direction', '?')}",
        f"altitude: {circuit.get('altitudeFeet', '?')} FT",
    ]
    legs = circuit.get("legs", [])
    if isinstance(legs, list) and legs:
        items.append(
            "legs: " + " -> ".join(
                f"{leg.get('name', '?')} ({leg.get('pathId', '?')})"
                for leg in legs
                if isinstance(leg, dict)
            )
        )
    joins = circuit.get("joinProcedures", [])
    if isinstance(joins, list) and joins:
        items.append(
            "joins: " + ", ".join(
                f"{join.get('type', '?')} @ {join.get('entryPointId', '?')}"
                for join in joins
                if isinstance(join, dict)
            )
        )
    projection_status = circuit.get("projectionStatus")
    if isinstance(projection_status, str):
        items.append(f"projection status: {projection_status}")
    source_loop = circuit.get("sourceLoop")
    if isinstance(source_loop, str):
        items.append(f"source loop: {source_loop}")
    return items


def candidate_operational_sector(data: PlateData, sector_id: str) -> dict[str, Any] | None:
    sector = data.candidate_operational_sectors.get(sector_id)
    return sector if isinstance(sector, dict) else None


def candidate_operational_sector_shape(data: PlateData, sector_id: str) -> authoring.AirspaceShape | None:
    return data.candidate_operational_sector_shapes.get(sector_id)


def published_reference_label(reference: Any) -> str | None:
    if not isinstance(reference, dict):
        return None
    value = reference.get("reference")
    return value if isinstance(value, str) else None


def contact_timing_label(contact_requirement: Any) -> str | None:
    if not isinstance(contact_requirement, dict):
        return None
    timing = contact_requirement.get("timing")
    if not isinstance(timing, dict):
        return None
    kind = timing.get("kind")
    if kind == "BEFORE_ENTRY":
        return "contact before entry"
    reference = timing.get("reference")
    if kind == "BEFORE_POINT" and isinstance(reference, str):
        return f"contact before {reference}"
    if kind == "AT_POINT" and isinstance(reference, str):
        return f"contact at {reference}"
    if kind == "DISTANCE_BEFORE" and isinstance(reference, str) and timing.get("distanceNm") is not None:
        return f"contact {timing['distanceNm']} NM before {reference}"
    if kind == "BY_ALTITUDE" and timing.get("feet") is not None:
        return f"contact by {timing['feet']} ft"
    return None


def operational_sector_items(data: PlateData, sector_id: str) -> list[str]:
    sector = candidate_operational_sector(data, sector_id)
    if sector is None:
        return [f"{sector_id}: not projected."]
    items: list[str] = []
    anchor = sector.get("anchor")
    if isinstance(anchor, dict):
        anchor_kind = anchor.get("kind")
        anchor_point_id = anchor.get("pointId")
        if isinstance(anchor_point_id, str):
            items.append(f"anchor: {anchor_kind or 'UNKNOWN'} @ {anchor_point_id}")
    altitude_limits = sector.get("altitudeBand")
    if isinstance(altitude_limits, dict):
        upper = altitude_limits.get("upper")
        if isinstance(upper, dict) and upper.get("kind") == "AT_LEVEL" and upper.get("value") is not None:
            items.append(f"upper limit: {upper['value']} ft MSL")
    contact_timing = contact_timing_label(sector.get("contactRequirement"))
    if isinstance(contact_timing, str):
        items.append(contact_timing)
    associated = sector.get("associatedProcedureIds")
    if isinstance(associated, list) and associated:
        items.append("procedures: " + ", ".join(str(item) for item in associated))
    relation = sector.get("relationToCtr")
    if isinstance(relation, str):
        items.append(f"CTR relation: {relation}")
    projection_status = sector.get("projectionStatus")
    if isinstance(projection_status, str):
        items.append(f"projection status: {projection_status}")
    return items


def candidate_published_vfr_procedure(data: PlateData, procedure_id: str) -> dict[str, Any] | None:
    procedure = data.candidate_published_vfr_procedures.get(procedure_id)
    return procedure if isinstance(procedure, dict) else None


def published_vfr_procedure_items(data: PlateData, procedure_id: str) -> list[str]:
    procedure = candidate_published_vfr_procedure(data, procedure_id)
    if procedure is None:
        return [f"{procedure_id}: not projected."]
    items: list[str] = []
    sequence = procedure.get("publishedSequence")
    if isinstance(sequence, list) and sequence:
        items.append(
            "sequence: " + " -> ".join(
                str(item.get("reference", item))
                if isinstance(item, dict)
                else str(item)
                for item in sequence
            )
        )
    contact_timing = contact_timing_label(procedure.get("contactRequirement"))
    if isinstance(contact_timing, str):
        items.append(contact_timing)
    advisories = procedure.get("advisories")
    if isinstance(advisories, dict):
        for field_name in (
            "contact",
            "altitude",
            "route",
            "reporting",
            "availability",
            "specialProcedure",
            "noiseAbatement",
            "speedCap",
            "squawkConvention",
            "activationHours",
            "equipmentMinimum",
            "language",
            "general",
        ):
            value = advisories.get(field_name)
            if isinstance(value, str) and value:
                items.append(value)
    terminates_at = procedure.get("terminatesAt")
    terminates_at_label = published_reference_label(terminates_at)
    if isinstance(terminates_at_label, str):
        items.append(f"terminates at: {terminates_at_label}")
    hold_at = procedure.get("holdAt")
    hold_at_label = published_reference_label(hold_at)
    if isinstance(hold_at_label, str):
        items.append(f"hold at: {hold_at_label}")
    communication_failure = procedure.get("communicationFailure")
    if isinstance(communication_failure, dict):
        before_contact = communication_failure.get("beforeContactEstablished")
        if isinstance(before_contact, str) and before_contact:
            items.append(f"before contact established: {before_contact}")
        note = communication_failure.get("note")
        if isinstance(note, str) and note:
            items.append(note)
        after_contact = communication_failure.get("afterContactEstablishedExitSequence")
        if isinstance(after_contact, list) and after_contact:
            items.append(
                "after contact established: " + " -> ".join(
                    label
                    for item in after_contact
                    for label in [published_reference_label(item)]
                    if isinstance(label, str)
                )
            )
    associated_routes = procedure.get("associatedVfrRouteIds")
    if isinstance(associated_routes, list) and associated_routes:
        items.append("routes: " + ", ".join(str(item) for item in associated_routes))
    associated_sectors = procedure.get("associatedOperationalSectorIds")
    if isinstance(associated_sectors, list) and associated_sectors:
        items.append("sectors: " + ", ".join(str(item) for item in associated_sectors))
    associated_graphs = procedure.get("associatedCircuitIds")
    if isinstance(associated_graphs, list) and associated_graphs:
        items.append("circuits: " + ", ".join(str(item) for item in associated_graphs))
    departure_runways = procedure.get("departureRunwayIds")
    if isinstance(departure_runways, list) and departure_runways:
        items.append("departure runways: " + ", ".join(str(item) for item in departure_runways))
    applicable_runways = procedure.get("applicableRunwayIds")
    if isinstance(applicable_runways, list) and applicable_runways:
        items.append("applicable runways: " + ", ".join(str(item) for item in applicable_runways))
    projection_status = procedure.get("projectionStatus")
    if isinstance(projection_status, str):
        items.append(f"projection status: {projection_status}")
    return items


def projection_gap_items(data: PlateData, *keywords: str) -> list[str]:
    lowered_keywords = [keyword.lower() for keyword in keywords]
    sources = (
        [str(item) for item in data.projection_gaps]
        + [str(item) for item in data.omitted_features]
    )
    matches = [
        item
        for item in sources
        if any(keyword in item.lower() for keyword in lowered_keywords)
    ]
    seen: set[str] = set()
    ordered: list[str] = []
    for item in matches:
        if item in seen:
            continue
        seen.add(item)
        ordered.append(item)
    return ordered


def parking_access_summary_lines(data: PlateData) -> list[str]:
    aprons = current_core_aerodrome(data).get("aprons", {})
    if aprons:
        return [
            f"{apron.get('name')}: {len(apron.get('standIds', []))} visible stand(s) attached in the current projected v1 pass."
            for _, apron in sorted(aprons.items(), key=lambda item: item[1].get("name", ""))
        ]
    return ["No projected apron-access branches are available in the strict plate view model."]


def operational_sector_svg(data: PlateData, canvas: PlateMapCanvas, sector_id: str) -> str:
    shape = candidate_operational_sector_shape(data, sector_id)
    if shape is None:
        return ""
    return airspace_shapes_svg(
        canvas,
        [shape],
        fill="#d7e4ef",
        stroke="#476988",
        fill_opacity=0.22,
        stroke_width_px=1.8,
        label=True,
    )


def parachute_area_svg(data: PlateData, canvas: PlateMapCanvas, include_consult_note: bool = False) -> str:
    if data.parachute_area_shape is None:
        return ""
    label = "PARACHUTE AREA up to FL160"
    if include_consult_note:
        label = f"{label} consult ATIS / TWR"
    return (
        airspace_shapes_svg(
            canvas,
            [data.parachute_area_shape],
            fill="#d8ccb3",
            stroke="#8a7456",
            fill_opacity=0.52,
            stroke_width_px=1.5,
        )
        + boxed_label_svg(
            canvas,
            authoring.centroid(data.parachute_area_shape.boundaries[0]),
            label,
            fill_box="#ffffff",
            fill_text="#5c4f33",
            stroke_box="#8a7456",
            stroke_text=None,
            font_size=12.0,
        )
    )


def render_ad1_map(data: PlateData) -> str:
    world_points: list[report.XY] = []
    world_points.extend(data.current_core_point_lookup.values())
    if data.parachute_area_shape is not None:
        world_points.extend(polygon_or_lines_points(data.parachute_area_shape))
    tower_point = data.anchor_lookup.get("tower")
    if tower_point is not None:
        world_points.append(tower_point)
    canvas = build_map_canvas(world_points, margin=180.0)

    parts = [background_grid_svg(canvas, 500.0)]
    if data.parachute_area_shape is not None:
        parts.append(
            airspace_shapes_svg(
                canvas,
                [data.parachute_area_shape],
                fill="#d8ccb3",
                stroke="#8a7456",
                fill_opacity=0.55,
                stroke_width_px=1.5,
            ),
        )
        center = authoring.centroid(data.parachute_area_shape.boundaries[0])
        parts.append(
            boxed_label_svg(
                canvas,
                center,
                "PARACHUTE AREA up to FL160",
                fill_box="#ffffff",
                fill_text="#5c4f33",
                stroke_box="#8a7456",
                stroke_text=None,
                font_size=12.0,
            ),
        )
    parts.append(current_core_runway_shapes_svg(data, canvas))
    parts.append(current_core_taxiway_paths_svg(data, canvas))
    parts.append(current_core_holding_points_svg(data, canvas))
    parts.append(tower_svg(data, canvas))
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def render_ad6_map(data: PlateData) -> str:
    world_points: list[report.XY] = []
    world_points.extend(data.current_core_point_lookup.values())
    if data.parachute_area_shape is not None:
        world_points.extend(polygon_or_lines_points(data.parachute_area_shape))
    tower_point = data.anchor_lookup.get("tower")
    if tower_point is not None:
        world_points.append(tower_point)
    canvas = build_map_canvas(world_points, margin=160.0)

    emphasis = {"16R/34L"}
    parts = [background_grid_svg(canvas, 500.0)]
    if data.parachute_area_shape is not None:
        parts.append(
            airspace_shapes_svg(
                canvas,
                [data.parachute_area_shape],
                fill="#d8ccb3",
                stroke="#8a7456",
                fill_opacity=0.52,
            ),
        )
    parts.append(current_core_runway_shapes_svg(data, canvas, emphasis=emphasis))
    parts.append(current_core_taxiway_paths_svg(data, canvas, highlight_ids={"Y"}))
    parts.append(tower_svg(data, canvas))
    y_path_id = current_core_aerodrome(data).get("taxiways", {}).get("Y", {}).get("pathId")
    y_points = data.current_core_path_lookup.get(y_path_id, []) if isinstance(y_path_id, str) else []
    if len(y_points) >= 2:
        parts.append(
            boxed_label_svg(
                canvas,
                polyline_midpoint(y_points),
                "GLIDER OPS AREA",
                fill_box="#ffffff",
                fill_text="#8c5f00",
                stroke_box="#cf9b1f",
                stroke_text=None,
                font_size=13.0,
                dy=24.0,
            ),
        )
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def render_prc1_map(data: PlateData) -> str:
    path = route_points_for_vfr_route(data, "vfr_western_corridor_path")
    world_points = list(path)
    world_points.extend(data.current_core_point_lookup.values())
    world_points.extend(collect_airspace_points(data.primary_airspaces))
    world_points.extend(collect_airspace_points(data.secondary_airspaces))
    if data.parachute_area_shape is not None:
        world_points.extend(polygon_or_lines_points(data.parachute_area_shape))
    canvas = build_map_canvas(world_points, margin=420.0)

    parts = [background_grid_svg(canvas, 1000.0)]
    parts.append(airspace_shapes_svg(canvas, data.secondary_airspaces, "#f0ece8", "#9f8f7d", 0.08, dashed=True))
    parts.append(airspace_shapes_svg(canvas, data.primary_airspaces, "#f1d6d6", "#748ea8", 0.18, label=False))
    parts.append(parachute_area_svg(data, canvas, include_consult_note=True))
    parts.append(current_core_runway_shapes_svg(data, canvas))
    if len(path) >= 2:
        parts.append(route_polyline_svg(canvas, path, label="vfr_western_corridor_path"))
    parts.append(
        reporting_points_svg(
            canvas,
            data.reporting_lookup,
            ["GRAZ-NORD", "GREEN CITY", "AUTOBAHN-WEST"],
        ),
    )
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def render_prc2_map(data: PlateData) -> str:
    world_points: list[report.XY] = []
    southwest_join_path = route_points_for_vfr_route(data, "vfr_southwest_entry_path")
    world_points.extend(southwest_join_path)
    world_points.extend(data.current_core_point_lookup.values())
    world_points.extend(authoring.collect_points_from_lines(data.main_circuit))
    world_points.extend(authoring.collect_points_from_lines(data.west_circuit))
    sector_shape = candidate_operational_sector_shape(data, "sector_whiskey")
    if sector_shape is not None:
        world_points.extend(polygon_or_lines_points(sector_shape))
    if data.parachute_area_shape is not None:
        world_points.extend(polygon_or_lines_points(data.parachute_area_shape))
    canvas = build_map_canvas(world_points, margin=420.0)

    parts = [background_grid_svg(canvas, 1000.0)]
    parts.append(operational_sector_svg(data, canvas, "sector_whiskey"))
    parts.append(parachute_area_svg(data, canvas, include_consult_note=True))
    parts.append(current_core_runway_shapes_svg(data, canvas))
    parts.append(circuit_lines_svg(canvas, primary=[], secondary=data.west_circuit + data.main_circuit, tertiary=data.east_circuit))
    if len(southwest_join_path) >= 2:
        parts.append(route_polyline_svg(canvas, southwest_join_path, label="vfr_southwest_entry_path", color="#2f5a92", width_px=2.8))
    parts.append(
        reporting_points_svg(
            canvas,
            data.reporting_lookup,
            ["SENDER DOBL"],
        ),
    )
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def render_prc3_map(data: PlateData) -> str:
    world_points: list[report.XY] = []
    southeast_join_path = route_points_for_vfr_route(data, "vfr_southeast_entry_path")
    world_points.extend(southeast_join_path)
    world_points.extend(data.current_core_point_lookup.values())
    world_points.extend(authoring.collect_points_from_lines(data.main_circuit))
    world_points.extend(authoring.collect_points_from_lines(data.east_circuit))
    sector_shape = candidate_operational_sector_shape(data, "sector_echo")
    if sector_shape is not None:
        world_points.extend(polygon_or_lines_points(sector_shape))
    if data.parachute_area_shape is not None:
        world_points.extend(polygon_or_lines_points(data.parachute_area_shape))
    canvas = build_map_canvas(world_points, margin=420.0)

    parts = [background_grid_svg(canvas, 1000.0)]
    parts.append(operational_sector_svg(data, canvas, "sector_echo"))
    parts.append(parachute_area_svg(data, canvas, include_consult_note=True))
    parts.append(current_core_runway_shapes_svg(data, canvas))
    parts.append(circuit_lines_svg(canvas, primary=[], secondary=data.east_circuit + data.main_circuit, tertiary=data.west_circuit))
    if len(southeast_join_path) >= 2:
        parts.append(route_polyline_svg(canvas, southeast_join_path, label="vfr_southeast_entry_path", color="#2f5a92", width_px=2.8))
    parts.append(
        reporting_points_svg(
            canvas,
            data.reporting_lookup,
            ["KALSDORF"],
        ),
    )
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def render_prc4_map(data: PlateData) -> str:
    world_points: list[report.XY] = []
    world_points.extend(authoring.collect_points_from_lines(data.main_circuit))
    world_points.extend(authoring.collect_points_from_lines(data.west_circuit))
    world_points.extend(authoring.collect_points_from_lines(data.east_circuit))
    world_points.extend(data.current_core_point_lookup.values())
    if data.parachute_area_shape is not None:
        world_points.extend(polygon_or_lines_points(data.parachute_area_shape))
    canvas = build_map_canvas(world_points, margin=200.0)

    parts = [background_grid_svg(canvas, 500.0)]
    if data.parachute_area_shape is not None:
        parts.append(parachute_area_svg(data, canvas, include_consult_note=False))
    parts.append(current_core_runway_shapes_svg(data, canvas, emphasis={"16R/34L"}))
    parts.append(circuit_lines_svg(canvas, primary=data.west_circuit + data.main_circuit, tertiary=data.east_circuit))
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def render_prc5_map(data: PlateData) -> str:
    world_points: list[report.XY] = []
    world_points.extend(authoring.collect_points_from_lines(data.main_circuit))
    world_points.extend(authoring.collect_points_from_lines(data.east_circuit))
    world_points.extend(authoring.collect_points_from_lines(data.west_circuit))
    world_points.extend(data.current_core_point_lookup.values())
    tower_point = data.anchor_lookup.get("tower")
    if tower_point is not None:
        world_points.append(tower_point)
    canvas = build_map_canvas(world_points, margin=180.0)

    parts = [background_grid_svg(canvas, 500.0)]
    parts.append(current_core_runway_shapes_svg(data, canvas, emphasis={"16C/34C", "16L/34R"}))
    parts.append(circuit_lines_svg(canvas, primary=data.main_circuit + data.east_circuit, tertiary=data.west_circuit))
    parts.append(tower_svg(data, canvas))
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def render_enr1_map(data: PlateData) -> str:
    world_points: list[report.XY] = []
    world_points.extend(collect_airspace_points(data.primary_airspaces))
    world_points.extend(collect_airspace_points(data.secondary_airspaces))
    for shape in data.candidate_operational_sector_shapes.values():
        world_points.extend(polygon_or_lines_points(shape))
    world_points.extend(list(data.reporting_lookup.values()))
    world_points.extend(data.current_core_point_lookup.values())
    canvas = build_map_canvas(world_points, margin=500.0)

    parts = [background_grid_svg(canvas, 2000.0)]
    parts.append(airspace_shapes_svg(canvas, data.secondary_airspaces, "#f0ece8", "#9f8f7d", 0.10, dashed=True))
    parts.append(airspace_shapes_svg(canvas, data.primary_airspaces, "#f1d6d6", "#748ea8", 0.16))
    parts.append(
        airspace_shapes_svg(
            canvas,
            list(data.candidate_operational_sector_shapes.values()),
            "#d7e4ef",
            "#476988",
            0.18,
            stroke_width_px=1.8,
            label=True,
        )
    )
    parts.append(current_core_runway_shapes_svg(data, canvas))

    route_specs = [
        ("vfr_northeast_entry_path", route_points_for_vfr_route(data, "vfr_northeast_entry_path")),
        ("vfr_western_corridor_path", route_points_for_vfr_route(data, "vfr_western_corridor_path")),
        ("vfr_southwest_entry_path", route_points_for_vfr_route(data, "vfr_southwest_entry_path")),
        ("vfr_southeast_entry_path", route_points_for_vfr_route(data, "vfr_southeast_entry_path")),
    ]
    for label, points in route_specs:
        if len(points) >= 2:
            parts.append(route_polyline_svg(canvas, points, label=label))
    parts.append(reporting_points_svg(canvas, data.reporting_lookup, list(data.reporting_lookup.keys())))
    tower_point = data.anchor_lookup.get("tower")
    if tower_point is not None:
        parts.append(
            boxed_label_svg(
                canvas,
                tower_point,
                "tower",
                fill_box="#ffffff",
                fill_text="#6a5151",
                stroke_box="#7e7e7e",
                stroke_text=None,
                font_size=11.0,
                dy=-22.0,
            ),
        )
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def render_ad2_map(data: PlateData) -> str:
    points: list[report.XY] = []
    tower_point = data.anchor_lookup.get("tower")
    if tower_point is not None:
        points.append(tower_point)
    points.extend(data.current_core_point_lookup.values())
    canvas = build_map_canvas(points, margin=120.0)

    parts = [background_grid_svg(canvas, 250.0)]
    parts.append(current_core_runway_shapes_svg(data, canvas))
    parts.append(current_core_taxiway_paths_svg(data, canvas, highlight_ids={"A", "B", "C", "Z"}, limit_ids={"A", "B", "C", "Z"}))
    parts.append(current_core_aprons_svg(data, canvas))
    parts.append(current_core_stands_svg(data, canvas))
    parts.append(tower_svg(data, canvas))
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def render_arr1_map(data: PlateData) -> str:
    path = route_points_for_vfr_route(data, "vfr_northeast_entry_path")
    world_points = list(path)
    world_points.extend(collect_airspace_points(data.primary_airspaces))
    world_points.extend(collect_airspace_points(data.secondary_airspaces))
    world_points.extend(data.current_core_point_lookup.values())
    world_points.extend(authoring.collect_points_from_lines(data.main_circuit))
    world_points.extend(authoring.collect_points_from_lines(data.east_circuit))
    if data.parachute_area_shape is not None:
        world_points.extend(polygon_or_lines_points(data.parachute_area_shape))
    canvas = build_map_canvas(world_points, margin=500.0)

    parts = [background_grid_svg(canvas, 1000.0)]
    parts.append(airspace_shapes_svg(canvas, data.secondary_airspaces, "#f0ece8", "#9f8f7d", 0.08, dashed=True))
    parts.append(airspace_shapes_svg(canvas, data.primary_airspaces, "#f1d6d6", "#748ea8", 0.14))
    parts.append(parachute_area_svg(data, canvas, include_consult_note=False))
    parts.append(current_core_runway_shapes_svg(data, canvas))
    parts.append(circuit_lines_svg(canvas, primary=[], secondary=data.east_circuit + data.main_circuit, tertiary=data.west_circuit))
    if len(path) >= 2:
        parts.append(route_polyline_svg(canvas, path, label="vfr_northeast_entry_path"))
    parts.append(reporting_points_svg(canvas, data.reporting_lookup, ["GLEISDORF", "LASSNITZHÖHE", "AUTOBAHN-OST"]))
    return svg_document(canvas.width_px, canvas.height_px, "".join(parts))


def fuel_text() -> str:
    return "ENTITY-ONLY PROJECTION"


def header_meta_text(data: PlateData) -> str:
    variation = f"{data.airport.magnetic_variation}° E" if data.airport.magnetic_variation is not None else "?"
    elevation = f"{data.airport.elevation_ft} FT" if data.airport.elevation_ft is not None else "?"
    return f"elev: {elevation} | var: {variation} | permitted: aircraft copter glider ultralight"


def frequency_box_html(data: PlateData) -> str:
    rows = "".join(f"<div>{html.escape(line)}</div>" for line in data.frequency_lines)
    return f'<div class="freq-box">{rows}</div>'


def runway_table_html(data: PlateData) -> str:
    rows = "".join(
        "<tr>"
        f"<td>{html.escape(row.designator)}</td>"
        f"<td>{html.escape(row.dimensions)}</td>"
        f"<td>{html.escape(row.tora)}</td>"
        f"<td>{html.escape(row.lda)}</td>"
        f"<td>{html.escape(row.note)}</td>"
        "</tr>"
        for row in data.runway_rows
    )
    return (
        '<table class="runway-table">'
        "<thead><tr><th>RWY</th><th>DIM</th><th>TORA</th><th>LDA</th><th>NOTE</th></tr></thead>"
        f"<tbody>{rows}</tbody></table>"
    )


def bullet_list_html(items: list[str]) -> str:
    return "<ul>" + "".join(f"<li>{html.escape(item)}</li>" for item in items) + "</ul>"


def info_block_html(title: str, items: list[str]) -> str:
    return f'<section class="info-block"><h3>{html.escape(title)}</h3>{bullet_list_html(items)}</section>'


def note_block_html(title: str, text: str, extra_class: str = "") -> str:
    classes = "info-block"
    if extra_class:
        classes = f"{classes} {extra_class}"
    return f'<section class="{classes}"><h3>{html.escape(title)}</h3><p>{html.escape(text)}</p></section>'


def page_header_html(title: str, plate_id: str, data: PlateData) -> str:
    return (
        '<div class="plate-header">'
        f'<div class="plate-title">{html.escape(title)}</div>'
        f'<div class="plate-id">{html.escape(plate_id)}</div>'
        f'<div class="plate-airport"><div>{html.escape(data.airport.name)}</div><div>{html.escape(data.airport_code)}/GRZ</div></div>'
        "</div>"
    )


def page_chrome_html(title: str, plate_id: str, body_html: str, data: PlateData) -> str:
    return (
        f'<section class="plate-page" id="{html.escape(slugify(plate_id))}">'
        '<div class="side-note">Not to be used as primary source of navigation</div>'
        f"{page_header_html(title, plate_id, data)}"
        f'<div class="plate-meta"><div>{html.escape(header_meta_text(data))}</div><div>{html.escape(fuel_text())}</div></div>'
        '<div class="plate-banner">THIS SPACE IS RESERVED FOR FURTHER AD INFORMATION</div>'
        f'<div class="plate-body">{body_html}</div>'
        f'<div class="plate-footer"><span>Generated {html.escape(data.airport_code)} entity-only draft.</span><span>{html.escape(plate_id)}</span><span>{html.escape(data.airport_code)}</span></div>'
        "</section>"
    )


def map_shell_html(map_file: str, data: PlateData, overlay_note: str | None = None) -> str:
    note_html = "" if overlay_note is None else f'<div class="map-note">{html.escape(overlay_note)}</div>'
    return (
        '<div class="map-shell">'
        f'<img class="map-image" src="{html.escape(map_file)}" alt="{html.escape(map_file)}" />'
        f"{frequency_box_html(data)}"
        f"{note_html}"
        "</div>"
    )


def gap_block_html(title: str, items: list[str]) -> str:
    return info_block_html(title, items if items else ["No projected entity content is available for this section."])


def stand_names_by_type(data: PlateData) -> tuple[list[str], list[str]]:
    stands = current_core_aerodrome(data).get("stands", {})
    airline_gates = sorted(
        (
            stand.get("name", "")
            for stand in stands.values()
            if isinstance(stand, dict) and stand.get("locationType") == "gate"
        ),
        key=natural_sort_key,
    )
    general_aviation = sorted(
        (
            stand.get("name", "")
            for stand in stands.values()
            if isinstance(stand, dict) and stand.get("locationType") != "gate"
        ),
        key=natural_sort_key,
    )
    return airline_gates, general_aviation


def gap_page_html(data: PlateData, plate_id: str, title: str, items: list[str]) -> str:
    body = note_block_html("Projection Gap", " ".join(items))
    return page_chrome_html(title, plate_id, body, data)


def ad1_page_html(data: PlateData, map_file: str) -> str:
    body = (
        map_shell_html(map_file, data)
        + runway_table_html(data)
    )
    return page_chrome_html("TAXI LAYOUT", "AD-1", body, data)


def ad6_page_html(data: PlateData, map_file: str) -> str:
    body = (
        map_shell_html(map_file, data)
        + runway_table_html(data)
    )
    return page_chrome_html("TAXI LAYOUT GLIDER OPS", "AD-6", body, data)


def prc_arrival_departure_page_html(data: PlateData, plate_id: str, title: str, map_file: str, overlay_note: str | None = None) -> str:
    route_ids = {
        "PRC-1": ["vfr_western_corridor_path"],
        "PRC-2": ["vfr_southwest_entry_path"],
        "PRC-3": ["vfr_southeast_entry_path"],
    }.get(plate_id, [])
    procedure_ids = {
        "PRC-1": ["prc_1_arrival_graz_nord", "prc_1_departure_graz_nord"],
        "PRC-2": ["prc_2_arrival_sender_dobl", "prc_2_departure_sender_dobl"],
        "PRC-3": ["prc_3_arrival_kalsdorf", "prc_3_departure_kalsdorf"],
    }.get(plate_id, [])
    sector_ids = {
        "PRC-2": ["sector_whiskey"],
        "PRC-3": ["sector_echo"],
    }.get(plate_id, [])
    gap_keywords = {
        "PRC-1": ("vfrroute", "first-class runtime entities"),
        "PRC-2": ("vfrroute", "sector", "first-class runtime entities"),
        "PRC-3": ("vfrroute", "sector", "first-class runtime entities"),
    }.get(plate_id, ("vfrroute",))
    blocks: list[str] = [map_shell_html(map_file, data, overlay_note=overlay_note)]
    panels = ['<div class="procedure-grid">']
    for procedure_id in procedure_ids:
        panels.append(info_block_html(procedure_id, published_vfr_procedure_items(data, procedure_id)))
    for route_id in route_ids:
        panels.append(info_block_html(route_id, entity_route_items(data, route_id)))
    for sector_id in sector_ids:
        panels.append(info_block_html(sector_id, operational_sector_items(data, sector_id)))
    panels.append(gap_block_html("Projection Gap", projection_gap_items(data, *gap_keywords)))
    panels.append("</div>")
    blocks.append("".join(panels))
    return page_chrome_html(title, plate_id, "".join(blocks), data)


def prc4_page_html(data: PlateData, map_file: str) -> str:
    procedure = current_core_aerodrome(data).get("aip", {}).get("publishedVfrProcedures", {}).get("prc_4_west_traffic_circuit", {})
    associated_circuits = [
        circuit_id
        for circuit_id in procedure.get("associatedCircuitIds", [])
        if isinstance(circuit_id, str)
    ]
    blocks = [
        map_shell_html(map_file, data),
        '<div class="procedure-grid">'
        + info_block_html("prc_4_west_traffic_circuit", published_vfr_procedure_items(data, "prc_4_west_traffic_circuit"))
        + "".join(
            info_block_html(circuit_id, circuit_procedure_items(data, circuit_id))
            for circuit_id in associated_circuits
        )
        + info_block_html("main_shared_graph", circuit_graph_items(data, "main_shared_graph"))
        + info_block_html("west_side_component", circuit_graph_items(data, "west_side_component"))
        + "</div>",
        runway_table_html(data),
    ]
    return page_chrome_html("VFR APP/DEP TFC 16R/34L", "PRC-4", "".join(blocks), data)


def prc5_page_html(data: PlateData, map_file: str) -> str:
    procedure = current_core_aerodrome(data).get("aip", {}).get("publishedVfrProcedures", {}).get("prc_5_east_hold", {})
    associated_circuits = [
        circuit_id
        for circuit_id in procedure.get("associatedCircuitIds", [])
        if isinstance(circuit_id, str)
    ]
    blocks = [
        map_shell_html(map_file, data),
        '<div class="procedure-grid">'
        + info_block_html("prc_5_east_hold", published_vfr_procedure_items(data, "prc_5_east_hold"))
        + "".join(
            info_block_html(circuit_id, circuit_procedure_items(data, circuit_id))
            for circuit_id in associated_circuits
        )
        + info_block_html("main_shared_graph", circuit_graph_items(data, "main_shared_graph"))
        + info_block_html("east_side_component", circuit_graph_items(data, "east_side_component"))
        + gap_block_html("Projection Gap", projection_gap_items(data, "east non-standard hold"))
        + "</div>",
    ]
    return page_chrome_html("VFR APP/DEP", "PRC-5", "".join(blocks), data)


def enr1_page_html(data: PlateData, map_file: str) -> str:
    blocks = [map_shell_html(map_file, data)]
    panels = ['<div class="procedure-grid">']
    panels.append(
        info_block_html(
            "Entity Coverage",
            [
                f"primary airspace volumes: {len(data.primary_airspaces)}",
                f"secondary airspace volumes: {len(data.secondary_airspaces)}",
                f"projected operational sectors: {len(data.candidate_operational_sectors)}",
                f"projected VFR routes: {len(data.candidate_vfr_routes)}",
                f"projected fixes: {len(data.reporting_lookup)}",
            ],
        ),
    )
    panels.append(gap_block_html("Projection Gap", projection_gap_items(data, "sector", "airspace boundary geometry", "first-class runtime entities")))
    panels.append("</div>")
    blocks.append("".join(panels))
    return page_chrome_html("VFR TRANSIT", "ENR-1", "".join(blocks), data)


def ad2_page_html(data: PlateData, map_file: str) -> str:
    airline_gates, general_aviation = stand_names_by_type(data)
    blocks = [
        map_shell_html(map_file, data),
        info_block_html(
            "Projected Apron / Parking Inventory",
            parking_access_summary_lines(data)
            + [f"projected airline gates: {len(airline_gates)}"]
            + [f"projected general-aviation stands: {len(general_aviation)}"],
        ),
        info_block_html(
            "Visible Stands",
            [
                "Airline / terminal side: " + ", ".join(airline_gates),
                "General aviation: " + ", ".join(general_aviation),
            ],
        ),
    ]
    return page_chrome_html("APRON / PARKING", "AD-2", "".join(blocks), data)


def arr1_page_html(data: PlateData, map_file: str) -> str:
    blocks = [
        map_shell_html(map_file, data),
        '<div class="procedure-grid">'
        + info_block_html("arr_1_gleisdorf_arrival_only", published_vfr_procedure_items(data, "arr_1_gleisdorf_arrival_only"))
        + info_block_html("vfr_northeast_entry_path", entity_route_items(data, "vfr_northeast_entry_path"))
        + gap_block_html("Projection Gap", projection_gap_items(data, "vfrroute", "sector", "first-class runtime entities"))
        + "</div>",
        runway_table_html(data),
    ]
    return page_chrome_html("VFR ARRIVAL ONLY", "ARR-1", "".join(blocks), data)


def _build_page_html(data: PlateData, plate_id: str, page_map_files: dict[str, str]) -> str:
    if plate_id == "AD-1":
        return ad1_page_html(data, page_map_files["AD-1"])
    if plate_id == "AD-6":
        return ad6_page_html(data, page_map_files["AD-6"])
    if plate_id == "AD-3":
        return gap_page_html(
            data,
            "AD-3",
            "BRIEFING",
            ["No entity-projected briefing or local-regulations content exists for this page yet."],
        )
    if plate_id == "AD-4":
        return gap_page_html(
            data,
            "AD-4",
            "BRIEFING",
            projection_gap_items(data, "published vfr procedures", "sector", "vfrroute")
            or ["No entity-projected approach / departure / COM FAILURE briefing content exists for this page yet."],
        )
    if plate_id == "AD-5":
        return gap_page_html(
            data,
            "AD-5",
            "BRIEFING",
            ["No entity-projected miscellaneous briefing content exists for this page yet."],
        )
    if plate_id in {"PRC-1", "PRC-2", "PRC-3"}:
        return prc_arrival_departure_page_html(data, plate_id, "VFR APP/DEP", page_map_files[plate_id])
    if plate_id == "PRC-4":
        return prc4_page_html(data, page_map_files["PRC-4"])
    if plate_id == "PRC-5":
        return prc5_page_html(data, page_map_files["PRC-5"])
    if plate_id == "ENR-1":
        return enr1_page_html(data, page_map_files["ENR-1"])
    if plate_id == "AD-2":
        return ad2_page_html(data, page_map_files["AD-2"])
    if plate_id == "ARR-1":
        return arr1_page_html(data, page_map_files["ARR-1"])
    raise ValueError(f"Unknown plate id: {plate_id}")


def render_html_document(data: PlateData, output_dir: Path, page_map_files: dict[str, str]) -> str:
    issue_count = data.current_core_issue_count

    pages = [
        (plate_id, _build_page_html(data, plate_id, page_map_files))
        for plate_id in data.plate_ids
    ]

    nav_items = "".join(
        f'<a href="#{html.escape(slugify(plate_id))}">{html.escape(plate_id)}</a>'
        for plate_id, _ in pages
    )
    page_html = "".join(page for _, page in pages)

    return f"""<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>{html.escape(data.airport_code)} generated plate pack</title>
    <style>
      :root {{
        color-scheme: light;
      }}
      * {{
        box-sizing: border-box;
      }}
      body {{
        margin: 0;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        background: #d8d6cf;
        color: #111111;
      }}
      .topbar {{
        position: sticky;
        top: 0;
        z-index: 20;
        display: flex;
        gap: 10px;
        flex-wrap: wrap;
        padding: 14px 18px;
        background: rgba(255, 255, 255, 0.96);
        border-bottom: 1px solid #bcb6aa;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
      }}
      .topbar a {{
        color: #224466;
        text-decoration: none;
        padding: 6px 10px;
        border: 1px solid #bcb6aa;
        border-radius: 999px;
        background: #f7f4ed;
      }}
      .summary {{
        padding: 16px 20px 4px;
        max-width: 1100px;
        margin: 0 auto;
        color: #2b2b2b;
      }}
      .summary p {{
        margin: 0 0 12px;
        line-height: 1.5;
      }}
      .pages {{
        display: grid;
        gap: 26px;
        padding: 18px 16px 32px;
        justify-content: center;
      }}
      .plate-page {{
        position: relative;
        width: 210mm;
        min-height: 297mm;
        background: #ffffff;
        border: 1px solid #989289;
        box-shadow: 0 12px 36px rgba(0, 0, 0, 0.12);
        padding: 16px 16px 18px 48px;
      }}
      .side-note {{
        position: absolute;
        left: 6px;
        top: 50%;
        transform: rotate(-90deg) translateX(-50%);
        transform-origin: left top;
        font-size: 11px;
        color: #505050;
      }}
      .plate-header {{
        display: grid;
        grid-template-columns: 1fr 90px 180px;
        align-items: end;
        gap: 8px;
        border-bottom: 2px solid #1f1f1f;
        padding-bottom: 6px;
      }}
      .plate-title {{
        font-size: 28px;
        line-height: 1;
      }}
      .plate-id {{
        font-size: 24px;
        text-align: center;
        font-weight: 700;
      }}
      .plate-airport {{
        text-align: right;
        font-size: 20px;
        line-height: 1.05;
        font-weight: 700;
      }}
      .plate-meta {{
        display: grid;
        grid-template-columns: 1fr auto;
        gap: 12px;
        font-size: 11px;
        padding: 4px 0;
        border-bottom: 1px solid #1f1f1f;
      }}
      .plate-banner {{
        margin-top: 4px;
        background: #f0e000;
        border: 1px solid #111111;
        font-weight: 700;
        padding: 4px 8px;
      }}
      .plate-body {{
        display: grid;
        gap: 12px;
        padding-top: 10px;
      }}
      .map-shell {{
        position: relative;
        background: #f7f7f4;
        border: 1px solid #1f1f1f;
        min-height: 640px;
        overflow: hidden;
      }}
      .map-image {{
        display: block;
        width: 100%;
        height: auto;
      }}
      .freq-box {{
        position: absolute;
        right: 12px;
        top: 12px;
        min-width: 280px;
        background: rgba(255, 255, 255, 0.92);
        border: 1px solid #111111;
      }}
      .freq-box div {{
        padding: 7px 10px;
        border-top: 1px solid #111111;
      }}
      .freq-box div:first-child {{
        border-top: none;
      }}
      .map-note {{
        position: absolute;
        left: 12px;
        bottom: 12px;
        max-width: 420px;
        padding: 8px 10px;
        background: rgba(255, 245, 245, 0.95);
        border: 1px solid #c54b4b;
        color: #7e1d1d;
        line-height: 1.45;
      }}
      .procedure-grid,
      .briefing-grid {{
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 12px;
      }}
      .briefing-grid {{
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }}
      .info-block {{
        border: 1px solid #111111;
        padding: 10px 12px;
        background: #faf8f2;
      }}
      .info-block h3 {{
        margin: 0 0 8px;
        font-size: 16px;
      }}
      .info-block p,
      .info-block li {{
        line-height: 1.45;
      }}
      .info-block p {{
        margin: 0;
      }}
      .info-block ul {{
        margin: 0;
        padding-left: 18px;
      }}
      .runway-table {{
        width: 100%;
        border-collapse: collapse;
        font-size: 13px;
      }}
      .runway-table th,
      .runway-table td {{
        border: 1px solid #111111;
        padding: 5px 7px;
        text-align: left;
      }}
      .runway-table thead {{
        background: #efede8;
      }}
      .plate-footer {{
        display: grid;
        grid-template-columns: 1fr auto auto;
        gap: 12px;
        align-items: end;
        font-size: 12px;
        padding-top: 10px;
        color: #3d3d3d;
      }}
      @media print {{
        body {{
          background: #ffffff;
        }}
        .topbar,
        .summary {{
          display: none;
        }}
        .pages {{
          gap: 0;
          padding: 0;
        }}
        .plate-page {{
          box-shadow: none;
          border: none;
          break-after: page;
          page-break-after: always;
          width: 210mm;
          min-height: 297mm;
          margin: 0;
        }}
      }}
      @media (max-width: 980px) {{
        .plate-page {{
          width: min(100%, 210mm);
          min-height: auto;
        }}
        .plate-header {{
          grid-template-columns: 1fr 90px 130px;
        }}
        .procedure-grid,
        .briefing-grid {{
          grid-template-columns: 1fr;
        }}
        .freq-box {{
          position: static;
          margin: 8px;
        }}
        .map-note {{
          position: static;
          margin: 8px;
        }}
      }}
    </style>
  </head>
    <body>
    <nav class="topbar">{nav_items}</nav>
    <section class="summary">
      <p>This is an entity-only LOWG draft pack. The renderer intentionally excludes publication-semantic supplement content and uses only the structured airport package via the strict plate view model, the validated current-core subset, and the generated validation/projection-gap reports.</p>
      <p>Current Kotlin validator status for the validated subset: <code>{html.escape(str(issue_count))}</code> issues. Sparse or gap-heavy pages are deliberate: they show what the entity projection can actually support today.</p>
    </section>
    <main class="pages">{page_html}</main>
  </body>
</html>
"""


_PAGE_MAP_BUILDERS: dict[str, Any] = {
    "AD-1": "render_ad1_map",
    "AD-6": "render_ad6_map",
    "PRC-1": "render_prc1_map",
    "PRC-2": "render_prc2_map",
    "PRC-3": "render_prc3_map",
    "PRC-4": "render_prc4_map",
    "PRC-5": "render_prc5_map",
    "ENR-1": "render_enr1_map",
    "AD-2": "render_ad2_map",
    "ARR-1": "render_arr1_map",
}


def render_page_maps(data: PlateData, output_dir: Path) -> dict[str, str]:
    builders = {name: globals()[name] for name in _PAGE_MAP_BUILDERS.values()}
    page_map_files: dict[str, str] = {}
    for plate_id in data.plate_ids:
        builder_name = _PAGE_MAP_BUILDERS.get(plate_id)
        if not builder_name:
            continue
        builder = builders[builder_name]
        filename = f"{slugify(plate_id)}-map.svg"
        output_path = output_dir / filename
        output_path.write_text(builder(data))
        page_map_files[plate_id] = filename
    return page_map_files


def main() -> None:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    data = build_plate_data(manifest_path)
    default_output = data.root / "cad/airports/rendered" / data.airport_code.lower() / "plate"
    output_dir = args.output_dir.resolve() if args.output_dir is not None else default_output
    output_dir.mkdir(parents=True, exist_ok=True)

    page_map_files = render_page_maps(data, output_dir)
    html_text = render_html_document(data, output_dir, page_map_files)
    output_path = output_dir / "index.html"
    output_path.write_text(html_text)

    for plate_id in sorted(page_map_files):
        print(output_dir / page_map_files[plate_id])
    print(output_path)


if __name__ == "__main__":
    main()
