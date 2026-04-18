#!/usr/bin/env python3

from __future__ import annotations

import argparse
import math
import sys
import unicodedata
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report


@dataclass(frozen=True)
class DxfLineEntity:
    layer: str
    start: report.XY
    end: report.XY


@dataclass(frozen=True)
class DxfPointEntity:
    layer: str
    point: report.XY


@dataclass(frozen=True)
class DxfTextEntity:
    layer: str
    point: report.XY
    text: str
    height: float


@dataclass(frozen=True)
class DxfContent:
    layer_colors: dict[str, int]
    lines: list[DxfLineEntity]
    points: list[DxfPointEntity]
    texts: list[DxfTextEntity]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Normalize an edited airspace DXF back into the shared local frame using VFR reporting points.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument("input_dxf", type=Path, help="Path to the edited DXF to normalize")
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Output path for the normalized DXF. Defaults to <input>_normalized.dxf",
    )
    return parser.parse_args()


def parse_dxf_content(path: Path) -> DxfContent:
    tokens = path.read_text().splitlines()
    pairs = list(zip(tokens[0::2], tokens[1::2]))
    layer_colors: dict[str, int] = {}
    lines: list[DxfLineEntity] = []
    points: list[DxfPointEntity] = []
    texts: list[DxfTextEntity] = []

    index = 0
    while index < len(pairs):
        code, value = pairs[index]
        if code.strip() != "0":
            index += 1
            continue

        entity = value
        attrs: dict[str, list[str]] = defaultdict(list)
        index += 1
        while index < len(pairs):
            next_code, next_value = pairs[index]
            if next_code.strip() == "0":
                break
            attrs[next_code.strip()].append(next_value)
            index += 1

        layer = attrs.get("8", [""])[0]

        if entity == "LAYER":
            color_text = attrs.get("62", ["7"])[0]
            layer_name = attrs.get("2", [layer])[0]
            layer_colors[layer_name] = int(color_text)
        elif entity == "LINE":
            lines.append(
                DxfLineEntity(
                    layer=layer,
                    start=report.XY(float(attrs["10"][0]), float(attrs["20"][0])),
                    end=report.XY(float(attrs["11"][0]), float(attrs["21"][0])),
                ),
            )
        elif entity == "POINT":
            points.append(
                DxfPointEntity(
                    layer=layer,
                    point=report.XY(float(attrs["10"][0]), float(attrs["20"][0])),
                ),
            )
        elif entity == "TEXT":
            texts.append(
                DxfTextEntity(
                    layer=layer,
                    point=report.XY(float(attrs["10"][0]), float(attrs["20"][0])),
                    text=attrs.get("1", [""])[0],
                    height=float(attrs.get("40", ["100.0"])[0]),
                ),
            )

    return DxfContent(
        layer_colors=layer_colors,
        lines=lines,
        points=points,
        texts=texts,
    )


def repair_mojibake(text: str) -> str:
    try:
        repaired = text.encode("latin1").decode("utf-8")
        return repaired
    except (UnicodeEncodeError, UnicodeDecodeError):
        return text


def normalize_label(text: str) -> str:
    repaired = repair_mojibake(text)
    ascii_text = unicodedata.normalize("NFKD", repaired).encode("ascii", "ignore").decode("ascii")
    return "".join(character for character in ascii_text.upper() if character.isalnum())


def fit_similarity_from_correspondences(
    source_points: list[report.XY],
    target_points: list[report.XY],
) -> report.Similarity:
    source_mean = report.XY(
        x=sum(point.x for point in source_points) / len(source_points),
        y=sum(point.y for point in source_points) / len(source_points),
    )
    target_mean = report.XY(
        x=sum(point.x for point in target_points) / len(target_points),
        y=sum(point.y for point in target_points) / len(target_points),
    )
    centered_source = [point - source_mean for point in source_points]
    centered_target = [point - target_mean for point in target_points]
    numerator_cos = sum(
        source.x * target.x + source.y * target.y
        for source, target in zip(centered_source, centered_target)
    )
    numerator_sin = sum(
        source.x * target.y - source.y * target.x
        for source, target in zip(centered_source, centered_target)
    )
    source_sum_squares = sum(
        source.x * source.x + source.y * source.y
        for source in centered_source
    )
    scale = math.hypot(numerator_cos, numerator_sin) / source_sum_squares
    rotation = math.atan2(numerator_sin, numerator_cos)
    cos_r = math.cos(rotation)
    sin_r = math.sin(rotation)
    mapped_source_mean = report.XY(
        scale * (cos_r * source_mean.x - sin_r * source_mean.y),
        scale * (sin_r * source_mean.x + cos_r * source_mean.y),
    )
    translation = target_mean - mapped_source_mean
    return report.Similarity(scale=scale, rotation_rad=rotation, translation=translation)


def find_labeled_vfr_points(content: DxfContent) -> dict[str, report.XY]:
    vfr_points = [point.point for point in content.points if point.layer == "VFR_POINTS"]
    vfr_labels = [text for text in content.texts if text.layer == "VFR_LABELS"]
    label_to_point: dict[str, report.XY] = {}
    for label in vfr_labels:
        key = normalize_label(label.text)
        nearest_point = min(vfr_points, key=lambda point: point.distance_to(label.point))
        label_to_point[key] = nearest_point
    return label_to_point


def reference_vfr_points(manifest_path: Path) -> dict[str, report.XY]:
    root = report.repo_root()
    manifest = report.load_manifest(manifest_path)
    apt_path = report.resolve_path(root, manifest["sources"]["aptDat"])
    ofmx_path = report.resolve_path(root, manifest["sources"]["ofmx"])
    _runways, _tower, _taxi_nodes, _taxi_edges, apt_metadata, _parking_positions = report.parse_apt(apt_path)
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = report.projector(origin)
    ofmx = report.parse_ofmx(ofmx_path, manifest["airportCode"])
    return {
        normalize_label(point.code_id): project(point.position)
        for point in ofmx["airportDesignatedPoints"]
        if (point.code_type or "").startswith("VFR")
    }


def transformed_extents(content: DxfContent, transform: report.Similarity) -> tuple[report.XY, report.XY]:
    transformed_points: list[report.XY] = []
    for line in content.lines:
        transformed_points.extend([transform.apply(line.start), transform.apply(line.end)])
    transformed_points.extend(transform.apply(point.point) for point in content.points)
    transformed_points.extend(transform.apply(text.point) for text in content.texts)
    min_x = min(point.x for point in transformed_points)
    max_x = max(point.x for point in transformed_points)
    min_y = min(point.y for point in transformed_points)
    max_y = max(point.y for point in transformed_points)
    margin = max(max_x - min_x, max_y - min_y) * 0.02
    return report.XY(min_x - margin, min_y - margin), report.XY(max_x + margin, max_y + margin)


def format_number(value: float) -> str:
    if math.isclose(value, round(value), abs_tol=1e-9):
        return str(int(round(value)))
    return f"{value:.6f}".rstrip("0").rstrip(".")


def dxf_pair(code: int | str, value: int | float | str) -> list[str]:
    rendered = format_number(value) if isinstance(value, float) else str(value)
    return [str(code), rendered]


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


def write_entities(lines: list[str], content: DxfContent, transform: report.Similarity) -> None:
    lines.extend(dxf_pair(0, "SECTION") + dxf_pair(2, "ENTITIES"))
    for line in content.lines:
        start = transform.apply(line.start)
        end = transform.apply(line.end)
        lines.extend(
            dxf_pair(0, "LINE")
            + dxf_pair(8, line.layer)
            + dxf_pair(10, start.x)
            + dxf_pair(20, start.y)
            + dxf_pair(30, 0.0)
            + dxf_pair(11, end.x)
            + dxf_pair(21, end.y)
            + dxf_pair(31, 0.0)
        )
    for point in content.points:
        mapped = transform.apply(point.point)
        lines.extend(
            dxf_pair(0, "POINT")
            + dxf_pair(8, point.layer)
            + dxf_pair(10, mapped.x)
            + dxf_pair(20, mapped.y)
            + dxf_pair(30, 0.0)
        )
    for text in content.texts:
        mapped = transform.apply(text.point)
        lines.extend(
            dxf_pair(0, "TEXT")
            + dxf_pair(8, text.layer)
            + dxf_pair(10, mapped.x)
            + dxf_pair(20, mapped.y)
            + dxf_pair(30, 0.0)
            + dxf_pair(40, text.height)
            + dxf_pair(1, text.text)
        )
    lines.extend(dxf_pair(0, "ENDSEC") + dxf_pair(0, "EOF"))


def normalize_airspace_dxf(manifest_path: Path, input_path: Path, output_path: Path) -> report.Similarity:
    content = parse_dxf_content(input_path)
    observed_points = find_labeled_vfr_points(content)
    reference_points = reference_vfr_points(manifest_path)
    common_labels = sorted(set(observed_points) & set(reference_points))
    if len(common_labels) < 2:
        raise ValueError("Need at least two labeled VFR reporting points to normalize the DXF.")

    transform = fit_similarity_from_correspondences(
        [observed_points[label] for label in common_labels],
        [reference_points[label] for label in common_labels],
    )
    extmin, extmax = transformed_extents(content, transform)

    layer_colors = {"0": 7, **content.layer_colors}
    lines: list[str] = []
    write_header(lines, extmin, extmax)
    write_layers(lines, layer_colors)
    write_entities(lines, content, transform)
    output_path.write_text("\n".join(lines) + "\n")
    return transform


def main() -> None:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    input_path = args.input_dxf.resolve()
    output_path = args.output.resolve() if args.output is not None else input_path.with_name(f"{input_path.stem}_normalized.dxf")
    transform = normalize_airspace_dxf(manifest_path, input_path, output_path)
    print(output_path)
    print(f"scale={transform.scale:.12f}")
    print(f"rotation_deg={math.degrees(transform.rotation_rad):.12f}")
    print(f"translation_x={transform.translation.x:.6f}")
    print(f"translation_y={transform.translation.y:.6f}")


if __name__ == "__main__":
    main()
