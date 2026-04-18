from __future__ import annotations

import math
import re

import airport_authoring_report as report


def format_number(value: float) -> str:
    if math.isclose(value, round(value), abs_tol=1e-9):
        return str(int(round(value)))
    return f"{value:.6f}".rstrip("0").rstrip(".")


def dxf_pair(code: int | str, value: int | float | str) -> list[str]:
    if isinstance(value, float):
        rendered = format_number(value)
    else:
        rendered = str(value)
    return [str(code), rendered]


def sanitize_layer_name(value: str, used: set[str]) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9_]+", "_", value.upper()).strip("_")
    cleaned = cleaned or "LAYER"
    base = cleaned[:28]
    candidate = base
    suffix = 1
    while candidate in used:
        candidate = f"{base[:24]}_{suffix:02d}"
        suffix += 1
    used.add(candidate)
    return candidate


def safe_close_boundary(points: list[report.XY], tolerance: float = 1e-6) -> list[tuple[report.XY, report.XY]]:
    if len(points) < 2:
        return []
    pairs = list(zip(points, points[1:]))
    if points[0].distance_to(points[-1]) > tolerance:
        pairs.append((points[-1], points[0]))
    return pairs


def write_header(lines: list[str], extmin: report.XY, extmax: report.XY) -> None:
    lines.extend(
        dxf_pair(0, "SECTION")
        + dxf_pair(2, "HEADER")
        + dxf_pair(9, "$ACADVER")
        + dxf_pair(1, "AC1009")
        + dxf_pair(9, "$INSBASE")
        + dxf_pair(10, 0.0)
        + dxf_pair(20, 0.0)
        + dxf_pair(30, 0.0)
        + dxf_pair(9, "$EXTMIN")
        + dxf_pair(10, extmin.x)
        + dxf_pair(20, extmin.y)
        + dxf_pair(30, 0.0)
        + dxf_pair(9, "$EXTMAX")
        + dxf_pair(10, extmax.x)
        + dxf_pair(20, extmax.y)
        + dxf_pair(30, 0.0)
        + dxf_pair(0, "ENDSEC")
    )


def write_layers(lines: list[str], layer_colors: dict[str, int]) -> None:
    lines.extend(dxf_pair(0, "SECTION") + dxf_pair(2, "TABLES"))
    lines.extend(dxf_pair(0, "TABLE") + dxf_pair(2, "LAYER") + dxf_pair(70, len(layer_colors)))
    for layer_name, color in layer_colors.items():
        lines.extend(
            dxf_pair(0, "LAYER")
            + dxf_pair(2, layer_name)
            + dxf_pair(70, 0)
            + dxf_pair(62, color)
            + dxf_pair(6, "CONTINUOUS")
        )
    lines.extend(dxf_pair(0, "ENDTAB") + dxf_pair(0, "ENDSEC"))


def write_line_entity(lines: list[str], layer: str, start: report.XY, end: report.XY) -> None:
    lines.extend(
        dxf_pair(0, "LINE")
        + dxf_pair(8, layer)
        + dxf_pair(10, start.x)
        + dxf_pair(20, start.y)
        + dxf_pair(30, 0.0)
        + dxf_pair(11, end.x)
        + dxf_pair(21, end.y)
        + dxf_pair(31, 0.0)
    )


def write_point_entity(lines: list[str], layer: str, point: report.XY) -> None:
    lines.extend(
        dxf_pair(0, "POINT")
        + dxf_pair(8, layer)
        + dxf_pair(10, point.x)
        + dxf_pair(20, point.y)
        + dxf_pair(30, 0.0)
    )


def write_text_entity(lines: list[str], layer: str, point: report.XY, text: str, height: float) -> None:
    lines.extend(
        dxf_pair(0, "TEXT")
        + dxf_pair(8, layer)
        + dxf_pair(10, point.x)
        + dxf_pair(20, point.y)
        + dxf_pair(30, 0.0)
        + dxf_pair(40, height)
        + dxf_pair(1, text)
    )


def append_cross(lines: list[str], layer: str, point: report.XY, half_size: float) -> None:
    write_line_entity(lines, layer, report.XY(point.x - half_size, point.y - half_size), report.XY(point.x + half_size, point.y + half_size))
    write_line_entity(lines, layer, report.XY(point.x - half_size, point.y + half_size), report.XY(point.x + half_size, point.y - half_size))
