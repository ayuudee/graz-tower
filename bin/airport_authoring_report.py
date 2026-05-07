#!/usr/bin/env python3

from __future__ import annotations

import argparse
import csv
import json
import math
import re
import shutil
import subprocess
import xml.etree.ElementTree as element_tree
import zipfile
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any


EARTH_RADIUS_METERS = 6_371_000.0


@dataclass(frozen=True)
class XY:
    x: float
    y: float

    def __add__(self, other: "XY") -> "XY":
        return XY(self.x + other.x, self.y + other.y)

    def __sub__(self, other: "XY") -> "XY":
        return XY(self.x - other.x, self.y - other.y)

    def scale(self, factor: float) -> "XY":
        return XY(self.x * factor, self.y * factor)

    def distance_to(self, other: "XY") -> float:
        return math.hypot(self.x - other.x, self.y - other.y)


@dataclass(frozen=True)
class Geo:
    lat: float
    lon: float


@dataclass(frozen=True)
class RunwayRecord:
    designator_a: str
    designator_b: str
    end_a: Geo
    end_b: Geo
    width_m: float
    displaced_a_m: float
    displaced_b_m: float


@dataclass(frozen=True)
class TowerViewpoint:
    position: Geo


@dataclass(frozen=True)
class TaxiNode:
    node_id: int
    position: Geo
    usage: str
    name: str


@dataclass(frozen=True)
class TaxiEdge:
    start: int
    end: int
    kind: str
    name: str


@dataclass(frozen=True)
class TaxiSign:
    position: Geo
    heading_deg: float
    size: float
    raw_text: str
    display_text: str


@dataclass(frozen=True)
class ParkingPosition:
    position: Geo
    heading_deg: float
    location_type: str
    aircraft_types: str
    name: str
    operation_letter: str | None
    operation_type: str | None


@dataclass(frozen=True)
class DxfLine:
    layer: str
    start: XY
    end: XY

    @property
    def length(self) -> float:
        return self.start.distance_to(self.end)

    @property
    def angle_deg(self) -> float:
        return math.degrees(math.atan2(self.end.y - self.start.y, self.end.x - self.start.x))


@dataclass(frozen=True)
class DxfPoint:
    layer: str
    point: XY


@dataclass(frozen=True)
class DxfDocument:
    lines: list[DxfLine]
    points: list[DxfPoint]
    entity_counts: dict[str, int]
    entity_layers: dict[str, dict[str, int]]


@dataclass(frozen=True)
class Similarity:
    scale: float
    rotation_rad: float
    translation: XY

    def apply(self, point: XY) -> XY:
        cos_r = math.cos(self.rotation_rad)
        sin_r = math.sin(self.rotation_rad)
        rotated = XY(
            self.scale * (cos_r * point.x - sin_r * point.y),
            self.scale * (sin_r * point.x + cos_r * point.y),
        )
        return rotated + self.translation

    def shifted(self, delta: XY) -> "Similarity":
        return Similarity(
            scale=self.scale,
            rotation_rad=self.rotation_rad,
            translation=self.translation + delta,
        )


@dataclass(frozen=True)
class CifpProcedureRef:
    section: str
    route_type: str
    name: str
    transition: str


@dataclass(frozen=True)
class CifpFixRef:
    identifier: str
    section: str
    subsection: str
    kind: str


@dataclass(frozen=True)
class CifpProcedureLegRecord:
    section: str
    sequence: str
    route_type: str
    procedure_name: str
    transition: str
    fix_identifier: str
    waypoint_description: str
    turn_direction_code: str | None
    path_terminator: str
    reference_identifier: str | None
    reference_code_type: str | None
    reference_subtype: str | None
    primary_course_raw: str | None
    primary_distance_raw: str | None
    secondary_course_raw: str | None
    secondary_distance_raw: str | None
    primary_course_tenths: int | None
    primary_distance_tenths: int | None
    secondary_course_tenths: int | None
    secondary_distance_tenths: int | None
    altitude_constraint_type: str
    altitude_1: int | None
    altitude_2: int | None
    speed_constraint_type: str
    speed_limit: int | None


CifpApproachRecord = CifpProcedureLegRecord


@dataclass(frozen=True)
class OfmxAirport:
    code_id: str
    name: str
    position: Geo
    elevation_ft: int | None
    magnetic_variation: int | None
    transition_altitude_ft: int | None


@dataclass(frozen=True)
class OfmxRunway:
    designator: str
    length_m: int | None
    width_m: int | None
    composition: str | None


@dataclass(frozen=True)
class OfmxRunwayDirection:
    designator: str
    position: Geo | None
    true_bearing: int | None
    magnetic_bearing: int | None


@dataclass(frozen=True)
class OfmxDesignatedPoint:
    code_id: str
    name: str | None
    position: Geo
    code_type: str | None
    associated_airport_code: str | None


@dataclass(frozen=True)
class OfmxNavaid:
    code_id: str
    name: str | None
    position: Geo
    kind: str
    frequency: str | None


@dataclass(frozen=True)
class OfmxUnit:
    mid: str
    name: str
    code_type: str
    airport_code_id: str | None
    code_class: str | None


@dataclass(frozen=True)
class OfmxService:
    mid: str
    unit_mid: str
    code_type: str
    sequence_number: int | None


@dataclass(frozen=True)
class OfmxFrequency:
    service_mid: str
    frequency_mhz: str
    code_type: str | None
    call_sign: str | None
    language: str | None


@dataclass(frozen=True)
class OfmxAirspace:
    mid: str
    code_type: str | None
    code_id: str | None
    name: str | None
    lower_value: int | None
    lower_unit: str | None
    lower_reference: str | None
    upper_value: int | None
    upper_unit: str | None
    upper_reference: str | None


@dataclass(frozen=True)
class OfmxBoundaryVertex:
    code_type: str
    position: Geo


@dataclass(frozen=True)
class OfmxAirspaceBoundary:
    airspace_mid: str
    vertices: list[OfmxBoundaryVertex]


@dataclass(frozen=True)
class OpenAirAirspace:
    airspace_class: str
    kind: str | None
    name: str
    lower_limit: str | None
    upper_limit: str | None
    boundaries: list[list[Geo]]
    source_path: str


@dataclass(frozen=True)
class CupWaypoint:
    name: str
    code_id: str
    country: str | None
    position: Geo
    elevation: str | None
    style: str | None
    source_path: str


@dataclass(frozen=True)
class EndpointAttachment:
    source_component: int
    target_component: int
    endpoint: XY
    target_segment_start: XY
    target_segment_end: XY
    distance: float
    segment_position: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Emit a reconciliation report for a hand-authored airport package.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit JSON instead of Markdown",
    )
    return parser.parse_args()


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def load_manifest(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text())


def resolve_path(root: Path, relative_path: str | None) -> Path | None:
    return None if relative_path is None else root / relative_path


def parse_apt(
    path: Path,
) -> tuple[
    dict[str, RunwayRecord],
    TowerViewpoint | None,
    dict[int, TaxiNode],
    list[TaxiEdge],
    dict[str, str],
    list[ParkingPosition],
]:
    runways: dict[str, RunwayRecord] = {}
    tower: TowerViewpoint | None = None
    taxi_nodes: dict[int, TaxiNode] = {}
    taxi_edges: list[TaxiEdge] = []
    metadata: dict[str, str] = {}
    parking_positions: list[ParkingPosition] = []

    for raw_line in path.read_text().splitlines():
        parts = raw_line.strip().split()
        if not parts:
            continue

        code = parts[0]
        if code == "100" and len(parts) >= 21:
            runway = RunwayRecord(
                designator_a=parts[8],
                end_a=Geo(float(parts[9]), float(parts[10])),
                displaced_a_m=float(parts[11]),
                designator_b=parts[17],
                end_b=Geo(float(parts[18]), float(parts[19])),
                displaced_b_m=float(parts[20]),
                width_m=float(parts[1]),
            )
            runways[runway.designator_a] = runway
            runways[runway.designator_b] = runway
        elif code == "14" and len(parts) >= 3:
            tower = TowerViewpoint(position=Geo(float(parts[1]), float(parts[2])))
        elif code == "1201" and len(parts) >= 5:
            node = TaxiNode(
                node_id=int(parts[4]),
                position=Geo(float(parts[1]), float(parts[2])),
                usage=parts[3],
                name=" ".join(parts[5:]) if len(parts) > 5 else "",
            )
            taxi_nodes[node.node_id] = node
        elif code == "1202" and len(parts) >= 5:
            taxi_edges.append(
                TaxiEdge(
                    start=int(parts[1]),
                    end=int(parts[2]),
                    kind=parts[4],
                    name=" ".join(parts[5:]) if len(parts) > 5 else "",
                ),
            )
        elif code == "1300" and len(parts) >= 6:
            parking_positions.append(
                ParkingPosition(
                    position=Geo(float(parts[1]), float(parts[2])),
                    heading_deg=float(parts[3]),
                    location_type=parts[4],
                    aircraft_types=parts[5],
                    name=" ".join(parts[6:]) if len(parts) > 6 else "",
                    operation_letter=None,
                    operation_type=None,
                ),
            )
        elif code == "1301" and len(parts) >= 2 and parking_positions:
            previous = parking_positions[-1]
            parking_positions[-1] = ParkingPosition(
                position=previous.position,
                heading_deg=previous.heading_deg,
                location_type=previous.location_type,
                aircraft_types=previous.aircraft_types,
                name=previous.name,
                operation_letter=parts[1],
                operation_type=" ".join(parts[2:]) if len(parts) > 2 else None,
            )
        elif code == "1302" and len(parts) >= 3:
            metadata[parts[1]] = " ".join(parts[2:])

    return runways, tower, taxi_nodes, taxi_edges, metadata, parking_positions


def simplify_apt_sign_text(raw_text: str) -> str:
    text = re.sub(r"\{[^}]*\}", " / ", raw_text)
    text = text.replace("_", " ")
    text = text.replace("|", " | ")
    text = re.sub(r"\s*/\s*", " / ", text)
    text = re.sub(r"\s*\|\s*", " | ", text)
    text = re.sub(r"\s+", " ", text).strip(" /|")
    return text or raw_text


def parse_apt_signs(path: Path) -> list[TaxiSign]:
    signs: list[TaxiSign] = []

    for raw_line in path.read_text().splitlines():
        parts = raw_line.strip().split()
        if not parts or parts[0] != "20" or len(parts) < 7:
            continue

        raw_text = " ".join(parts[6:])
        signs.append(
            TaxiSign(
                position=Geo(float(parts[1]), float(parts[2])),
                heading_deg=float(parts[3]),
                size=float(parts[4]),
                raw_text=raw_text,
                display_text=simplify_apt_sign_text(raw_text),
            ),
        )

    return signs


def projector(origin: Geo):
    lat0 = math.radians(origin.lat)
    lon0 = math.radians(origin.lon)

    def project(geo: Geo) -> XY:
        lat = math.radians(geo.lat)
        lon = math.radians(geo.lon)
        x = (lon - lon0) * math.cos(lat0) * EARTH_RADIUS_METERS
        y = (lat - lat0) * EARTH_RADIUS_METERS
        return XY(x, y)

    return project


def point_in_polygon(point: XY, polygon: list[XY]) -> bool:
    inside = False
    for start, end in zip(polygon, polygon[1:] + polygon[:1]):
        if ((start.y > point.y) != (end.y > point.y)) and (
            point.x < (end.x - start.x) * (point.y - start.y) / ((end.y - start.y) or 1e-12) + start.x
        ):
            inside = not inside
    return inside


def nearest_point_on_segment(point: XY, start: XY, end: XY) -> tuple[float, XY]:
    dx = end.x - start.x
    dy = end.y - start.y
    if dx == 0.0 and dy == 0.0:
        return point.distance_to(start), start
    segment_position = (((point.x - start.x) * dx) + ((point.y - start.y) * dy)) / ((dx * dx) + (dy * dy))
    clamped_position = max(0.0, min(1.0, segment_position))
    projected = XY(start.x + (clamped_position * dx), start.y + (clamped_position * dy))
    return point.distance_to(projected), projected


def read_dxf_text(path: Path) -> str:
    raw = path.read_bytes()
    for encoding in ("utf-8", "cp1252", "latin-1"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def parse_dxf(path: Path) -> DxfDocument:
    tokens = read_dxf_text(path).splitlines()
    pairs = list(zip(tokens[0::2], tokens[1::2]))
    lines: list[DxfLine] = []
    points: list[DxfPoint] = []
    entity_counts: dict[str, int] = defaultdict(int)
    entity_layers: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))

    interesting_entities = {"LINE", "POINT", "LWPOLYLINE", "POLYLINE", "VERTEX", "ARC", "CIRCLE", "TEXT", "MTEXT", "INSERT"}

    index = 0
    while index < len(pairs):
        code, value = pairs[index]
        if code.strip() != "0":
            index += 1
            continue

        entity = value
        entity_counts[entity] += 1
        attrs: dict[str, list[str]] = {}
        index += 1
        while index < len(pairs):
            next_code, next_value = pairs[index]
            if next_code.strip() == "0":
                break
            attrs.setdefault(next_code.strip(), []).append(next_value)
            index += 1

        layer = attrs.get("8", [""])[0]
        if entity in interesting_entities:
            entity_layers[entity][layer] += 1

        if entity == "LINE":
            lines.append(
                DxfLine(
                    layer=layer,
                    start=XY(float(attrs["10"][0]), float(attrs["20"][0])),
                    end=XY(float(attrs["11"][0]), float(attrs["21"][0])),
                ),
            )
        elif entity == "ARC":
            cx = float(attrs["10"][0])
            cy = float(attrs["20"][0])
            r = float(attrs["40"][0])
            start_angle = math.radians(float(attrs["50"][0]))
            end_angle = math.radians(float(attrs["51"][0]))
            lines.append(
                DxfLine(
                    layer=layer,
                    start=XY(cx + r * math.cos(start_angle), cy + r * math.sin(start_angle)),
                    end=XY(cx + r * math.cos(end_angle), cy + r * math.sin(end_angle)),
                ),
            )
        elif entity == "POINT":
            points.append(
                DxfPoint(
                    layer=layer,
                    point=XY(float(attrs["10"][0]), float(attrs["20"][0])),
                ),
            )

    return DxfDocument(
        lines=lines,
        points=points,
        entity_counts=dict(entity_counts),
        entity_layers={entity: dict(layer_counts) for entity, layer_counts in entity_layers.items()},
    )


def fit_similarity(a1: XY, a2: XY, b1: XY, b2: XY) -> Similarity:
    va = a2 - a1
    vb = b2 - b1
    len_a = math.hypot(va.x, va.y)
    len_b = math.hypot(vb.x, vb.y)
    scale = len_b / len_a
    rotation = math.atan2(vb.y, vb.x) - math.atan2(va.y, va.x)
    cos_r = math.cos(rotation)
    sin_r = math.sin(rotation)
    mapped_a1 = XY(
        scale * (cos_r * a1.x - sin_r * a1.y),
        scale * (sin_r * a1.x + cos_r * a1.y),
    )
    translation = b1 - mapped_a1
    return Similarity(scale=scale, rotation_rad=rotation, translation=translation)


def point_to_segment_projection(point: XY, start: XY, end: XY) -> tuple[float, float]:
    dx = end.x - start.x
    dy = end.y - start.y
    if dx == 0.0 and dy == 0.0:
        return point.distance_to(start), 0.0

    parameter = ((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)
    projection_parameter = max(0.0, min(1.0, parameter))
    projection = XY(start.x + projection_parameter * dx, start.y + projection_parameter * dy)
    return point.distance_to(projection), parameter


def point_to_segment_distance(point: XY, start: XY, end: XY) -> float:
    distance, _ = point_to_segment_projection(point, start, end)
    return distance


def connected_components(lines: list[DxfLine], tolerance: float = 1e-6) -> list[list[DxfLine]]:
    buckets: dict[tuple[int, int], list[int]] = defaultdict(list)
    for line_index, line in enumerate(lines):
        for point in (line.start, line.end):
            buckets[(round(point.x / tolerance), round(point.y / tolerance))].append(line_index)

    adjacency: dict[int, set[int]] = {line_index: set() for line_index in range(len(lines))}
    for indexes in buckets.values():
        for a in indexes:
            for b in indexes:
                if a != b:
                    adjacency[a].add(b)

    components: list[list[DxfLine]] = []
    seen: set[int] = set()
    for line_index in range(len(lines)):
        if line_index in seen:
            continue

        frontier = [line_index]
        component_indexes: list[int] = []
        while frontier:
            current = frontier.pop()
            if current in seen:
                continue
            seen.add(current)
            component_indexes.append(current)
            frontier.extend(adjacency[current] - seen)

        components.append([lines[i] for i in component_indexes])

    return components


def graph_summary(lines: list[DxfLine], tolerance: float = 1e-6) -> dict[str, int]:
    vertex_ids: dict[tuple[int, int], int] = {}
    next_id = 0
    degrees: dict[int, int] = defaultdict(int)

    def vertex(point: XY) -> int:
        nonlocal next_id
        key = (round(point.x / tolerance), round(point.y / tolerance))
        if key not in vertex_ids:
            vertex_ids[key] = next_id
            next_id += 1
        return vertex_ids[key]

    for line in lines:
        start_id = vertex(line.start)
        end_id = vertex(line.end)
        degrees[start_id] += 1
        degrees[end_id] += 1

    degree_one = sum(1 for degree in degrees.values() if degree == 1)
    degree_two = sum(1 for degree in degrees.values() if degree == 2)
    branch_vertices = sum(1 for degree in degrees.values() if degree > 2)
    cycle_rank = len(lines) - len(degrees) + 1
    return {
        "vertices": len(degrees),
        "degree1": degree_one,
        "degree2": degree_two,
        "branchVertices": branch_vertices,
        "cycleRank": cycle_rank,
    }


def degree_one_points(lines: list[DxfLine], tolerance: float = 1e-6) -> list[XY]:
    vertices: dict[tuple[int, int], dict[str, Any]] = {}

    def add(point: XY) -> None:
        key = (round(point.x / tolerance), round(point.y / tolerance))
        if key not in vertices:
            vertices[key] = {"point": point, "degree": 0}
        vertices[key]["degree"] = int(vertices[key]["degree"]) + 1

    for line in lines:
        add(line.start)
        add(line.end)

    return [entry["point"] for entry in vertices.values() if int(entry["degree"]) == 1]


def component_stats(lines: list[DxfLine]) -> dict[str, float]:
    xs = [coordinate for line in lines for coordinate in (line.start.x, line.end.x)]
    ys = [coordinate for line in lines for coordinate in (line.start.y, line.end.y)]
    return {
        "lineCount": len(lines),
        "totalLength": sum(line.length for line in lines),
        "minX": min(xs),
        "maxX": max(xs),
        "minY": min(ys),
        "maxY": max(ys),
    }


def mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else float("nan")


def classify_distance(value: float, ok_threshold: float, suspect_threshold: float) -> str:
    if value <= ok_threshold:
        return "aligned"
    if value <= suspect_threshold:
        return "approximate"
    return "suspect"


def override_by_index(overrides: list[dict[str, Any]], key: str) -> dict[int, dict[str, Any]]:
    return {
        int(item[key]): item
        for item in overrides
        if key in item
    }


def apply_override(
    reference_classification: str,
    override: dict[str, Any] | None,
) -> dict[str, Any]:
    if override is None:
        return {
            "classification": reference_classification,
            "referenceClassification": reference_classification,
            "label": None,
            "note": None,
        }

    return {
        "classification": override.get("classification", reference_classification),
        "referenceClassification": reference_classification,
        "label": override.get("label"),
        "note": override.get("note"),
    }


def format_limit(value: int | None, unit: str | None, reference: str | None) -> str:
    if value is None:
        return "?"
    pieces = [str(value)]
    if unit:
        pieces.append(unit)
    if reference:
        pieces.append(reference)
    return " ".join(pieces)


def apt_runway_positions(runways: dict[str, RunwayRecord]) -> dict[str, Geo]:
    positions: dict[str, Geo] = {}
    for runway in {id(record): record for record in runways.values()}.values():
        positions[runway.designator_a] = runway.end_a
        positions[runway.designator_b] = runway.end_b
    return positions


def runway_axis_xy(runways: dict[str, RunwayRecord], designator: str, project) -> tuple[XY, XY]:
    runway = runways[designator]
    if runway.designator_a == designator:
        return project(runway.end_a), project(runway.end_b)
    return project(runway.end_b), project(runway.end_a)


def manifest_procedure_anchors(
    manifest: dict[str, Any],
    drawing_id: str | None = None,
    anchor_type: str | None = None,
) -> list[dict[str, Any]]:
    anchors = manifest.get("namedMappings", {}).get("procedureAnchors", [])
    return [
        anchor
        for anchor in anchors
        if (drawing_id is None or anchor.get("drawingId") == drawing_id)
        and (anchor_type is None or anchor.get("anchorType") == anchor_type)
    ]


def resolve_drawing_point(document: DxfDocument, drawing_point_index: Any) -> XY | None:
    if not isinstance(drawing_point_index, int):
        return None
    zero_index = drawing_point_index - 1
    if zero_index < 0 or zero_index >= len(document.points):
        return None
    return document.points[zero_index].point


def drawing_anchor_pair_from_manifest(
    manifest: dict[str, Any],
    drawing_id: str,
    document: DxfDocument,
    anchor_type: str = "runway_end_anchor_candidate",
) -> tuple[tuple[str, XY], tuple[str, XY]] | None:
    resolved_anchors = [
        (str(anchor.get("anchorId")), point)
        for anchor in sorted(
            manifest_procedure_anchors(manifest, drawing_id=drawing_id, anchor_type=anchor_type),
            key=lambda candidate: int(candidate.get("drawingPointIndex", 10**9)),
        )
        for point in [resolve_drawing_point(document, anchor.get("drawingPointIndex"))]
        if point is not None
    ]
    if len(resolved_anchors) < 2:
        return None
    return resolved_anchors[0], resolved_anchors[1]


def parse_cifp_coordinate(value: str) -> Geo | None:
    trimmed = value.strip()
    if not trimmed:
        return None
    hemisphere = trimmed[0]
    digits = trimmed[1:]
    degree_width = 2 if hemisphere in {"N", "S"} else 3
    if len(digits) < degree_width + 6:
        return None
    degrees = int(digits[:degree_width])
    minutes = int(digits[degree_width : degree_width + 2])
    seconds = int(digits[degree_width + 2 :]) / 100.0
    decimal = degrees + (minutes / 60.0) + (seconds / 3600.0)
    if hemisphere in {"S", "W"}:
        decimal *= -1.0
    if hemisphere in {"N", "S"}:
        return Geo(decimal, 0.0)
    return Geo(0.0, decimal)


def parse_cifp(path: Path) -> dict[str, Any]:
    procedures_by_section: dict[str, set[tuple[str, str]]] = defaultdict(set)
    procedure_refs: dict[str, list[CifpProcedureRef]] = defaultdict(list)
    procedure_leg_records: dict[str, list[CifpProcedureLegRecord]] = defaultdict(list)
    fix_refs: dict[str, list[CifpFixRef]] = defaultdict(list)
    runways: dict[str, Geo] = {}
    approach_records: list[CifpApproachRecord] = []

    def parse_optional_int(value: str) -> int | None:
        stripped = value.strip()
        if not stripped:
            return None
        try:
            return int(stripped)
        except ValueError:
            return None

    for raw_line in path.read_text().splitlines():
        trimmed = raw_line.strip()
        if not trimmed:
            continue

        if ":" not in trimmed:
            continue

        prefix, rest = trimmed.split(":", 1)
        rest = rest.rstrip(";")
        if prefix in {"SID", "STAR", "APPCH"}:
            parts = rest.split(",")
            if len(parts) < 30:
                continue

            transition = parts[3].strip() or "(default)"
            procedure = CifpProcedureRef(
                section=prefix,
                route_type=parts[1].strip(),
                name=parts[2].strip(),
                transition=transition,
            )
            procedures_by_section[prefix].add((procedure.name, procedure.transition))
            procedure_refs[prefix].append(procedure)

            candidates = (
                (parts[4].strip(), parts[6].strip(), parts[7].strip(), "fix"),
                (parts[13].strip(), parts[15].strip(), parts[16].strip(), "recommendedNavaid"),
                (
                    parts[30].strip() if len(parts) > 30 else "",
                    parts[32].strip() if len(parts) > 32 else "",
                    parts[33].strip() if len(parts) > 33 else "",
                    "centerFix",
                ),
            )
            for identifier, section, subsection, kind in candidates:
                if identifier:
                    fix_refs[identifier].append(
                        CifpFixRef(
                            identifier=identifier,
                            section=section,
                            subsection=subsection,
                            kind=kind,
                        ),
                    )
            record = CifpProcedureLegRecord(
                section=prefix,
                sequence=parts[0].strip(),
                route_type=parts[1].strip(),
                procedure_name=parts[2].strip(),
                transition=transition,
                fix_identifier=parts[4].strip(),
                waypoint_description=parts[8].strip(),
                turn_direction_code=parts[9].strip() or None,
                path_terminator=parts[11].strip(),
                reference_identifier=parts[13].strip() or None,
                reference_code_type=parts[15].strip() or None,
                reference_subtype=parts[16].strip() or None,
                primary_course_raw=parts[18].strip() or None,
                primary_distance_raw=parts[19].strip() or None,
                secondary_course_raw=parts[20].strip() or None,
                secondary_distance_raw=parts[21].strip() or None,
                primary_course_tenths=parse_optional_int(parts[18]),
                primary_distance_tenths=parse_optional_int(parts[19]),
                secondary_course_tenths=parse_optional_int(parts[20]),
                secondary_distance_tenths=parse_optional_int(parts[21]),
                altitude_constraint_type=parts[22].strip(),
                altitude_1=parse_optional_int(parts[23]),
                altitude_2=parse_optional_int(parts[24]),
                speed_constraint_type=parts[26].strip(),
                speed_limit=parse_optional_int(parts[27]),
            )
            procedure_leg_records[prefix].append(record)
            if prefix == "APPCH":
                approach_records.append(record)
        elif prefix == "RWY":
            semicolon_parts = rest.split(";")
            main_parts = semicolon_parts[0].split(",")
            if not main_parts:
                continue
            designator = main_parts[0].strip()
            if designator.startswith("RW"):
                designator = designator[2:]
            threshold_parts = semicolon_parts[1].split(",") if len(semicolon_parts) > 1 else []
            if len(threshold_parts) >= 2:
                lat_geo = parse_cifp_coordinate(threshold_parts[0])
                lon_geo = parse_cifp_coordinate(threshold_parts[1])
                if lat_geo and lon_geo:
                    runways[designator] = Geo(lat_geo.lat, lon_geo.lon)

    def summarize(section: str) -> dict[str, Any]:
        names = sorted({name for name, _ in procedures_by_section[section]})
        transitions = sorted({transition for _, transition in procedures_by_section[section]})
        return {
            "nameCount": len(names),
            "transitionCount": len(transitions),
            "names": names,
            "transitions": transitions,
        }

    return {
        "procedures": {
            "SID": summarize("SID"),
            "STAR": summarize("STAR"),
            "APPCH": summarize("APPCH"),
        },
        "procedureRefs": procedure_refs,
        "procedureLegRecords": procedure_leg_records,
        "fixRefs": fix_refs,
        "runways": runways,
        "approachRecords": approach_records,
    }


def child_text(parent: element_tree.Element | None, path: str) -> str | None:
    if parent is None:
        return None
    child = parent.find(path)
    if child is None or child.text is None:
        return None
    return child.text.strip()


def child_int(parent: element_tree.Element | None, path: str) -> int | None:
    value = child_text(parent, path)
    if value is None or not value:
        return None
    stripped = value.strip()
    if stripped.endswith(".0"):
        stripped = stripped[:-2]
    return int(stripped) if stripped.lstrip("-").isdigit() else None


def child_geo(parent: element_tree.Element | None, lat_path: str, lon_path: str) -> Geo | None:
    lat_text = child_text(parent, lat_path)
    lon_text = child_text(parent, lon_path)
    if lat_text is None or lon_text is None:
        return None
    lat = parse_ofmx_coordinate(lat_text)
    lon = parse_ofmx_coordinate(lon_text)
    if lat is None or lon is None:
        return None
    return Geo(lat if lat_text[-1] in {"N", "S"} else 0.0, lon if lon_text[-1] in {"E", "W"} else 0.0)


def parse_ofmx_coordinate(value: str) -> float | None:
    trimmed = value.strip()
    if not trimmed:
        return None
    hemisphere = trimmed[-1]
    numeric = trimmed[:-1]
    try:
        decimal = float(numeric)
    except ValueError:
        return None
    if hemisphere in {"S", "W"}:
        return -decimal
    return decimal


def parse_ofmx(path: Path, airport_code: str) -> dict[str, Any]:
    root = element_tree.parse(path).getroot()

    airport: OfmxAirport | None = None
    runways: list[OfmxRunway] = []
    runway_directions: dict[str, OfmxRunwayDirection] = {}
    all_designated_points: dict[str, OfmxDesignatedPoint] = {}
    airport_designated_points: list[OfmxDesignatedPoint] = []
    all_navaids: dict[str, OfmxNavaid] = {}
    units: dict[str, OfmxUnit] = {}
    services: dict[str, OfmxService] = {}
    frequencies: list[OfmxFrequency] = []
    airspaces: dict[str, OfmxAirspace] = {}
    all_airspace_boundaries: dict[str, list[OfmxAirspaceBoundary]] = defaultdict(list)
    service_airspace_associations: list[tuple[str, str]] = []

    def register_navaid(navaid: OfmxNavaid) -> None:
        priority = {"VOR": 0, "NDB": 1, "DME": 2}
        existing = all_navaids.get(navaid.code_id)
        if existing is None or priority.get(navaid.kind, 99) < priority.get(existing.kind, 99):
            all_navaids[navaid.code_id] = navaid

    for ahp in root.findall("Ahp"):
        code_id = child_text(ahp, "AhpUid/codeId")
        if code_id != airport_code:
            continue
        position = child_geo(ahp, "geoLat", "geoLong")
        if position is None:
            continue
        airport = OfmxAirport(
            code_id=code_id,
            name=child_text(ahp, "txtName") or code_id,
            position=position,
            elevation_ft=child_int(ahp, "valElev"),
            magnetic_variation=child_int(ahp, "valMagVar"),
            transition_altitude_ft=child_int(ahp, "valTransitionAlt"),
        )

    for rwy in root.findall("Rwy"):
        code_id = child_text(rwy, "RwyUid/AhpUid/codeId")
        if code_id != airport_code:
            continue
        runways.append(
            OfmxRunway(
                designator=child_text(rwy, "RwyUid/txtDesig") or "?",
                length_m=child_int(rwy, "valLen"),
                width_m=child_int(rwy, "valWid"),
                composition=child_text(rwy, "codeComposition"),
            ),
        )

    for rdn in root.findall("Rdn"):
        code_id = child_text(rdn, "RdnUid/RwyUid/AhpUid/codeId")
        if code_id != airport_code:
            continue
        designator = child_text(rdn, "RdnUid/txtDesig") or "?"
        runway_directions[designator] = OfmxRunwayDirection(
            designator=designator,
            position=child_geo(rdn, "geoLat", "geoLong"),
            true_bearing=child_int(rdn, "valTrueBrg"),
            magnetic_bearing=child_int(rdn, "valMagBrg"),
        )

    for dpn in root.findall("Dpn"):
        position = child_geo(dpn, "DpnUid/geoLat", "DpnUid/geoLong")
        code_id = child_text(dpn, "DpnUid/codeId")
        if position is None or code_id is None:
            continue
        point = OfmxDesignatedPoint(
            code_id=code_id,
            name=child_text(dpn, "txtName"),
            position=position,
            code_type=child_text(dpn, "codeType"),
            associated_airport_code=child_text(dpn, "AhpUidAssoc/codeId"),
        )
        all_designated_points[point.code_id] = point
        if point.associated_airport_code == airport_code:
            airport_designated_points.append(point)

    for vor in root.findall("Vor"):
        vor_uid = vor.find("VorUid")
        code_id = child_text(vor_uid, "codeId")
        position = child_geo(vor_uid, "geoLat", "geoLong") if vor_uid is not None else None
        if code_id is None or position is None:
            continue
        register_navaid(
            OfmxNavaid(
                code_id=code_id,
                name=child_text(vor, "txtName"),
                position=position,
                kind="VOR",
                frequency=child_text(vor, "valFreq"),
            )
        )

    for ndb in root.findall("Ndb"):
        ndb_uid = ndb.find("NdbUid")
        code_id = child_text(ndb_uid, "codeId")
        position = child_geo(ndb_uid, "geoLat", "geoLong") if ndb_uid is not None else None
        if code_id is None or position is None:
            continue
        register_navaid(
            OfmxNavaid(
                code_id=code_id,
                name=child_text(ndb, "txtName"),
                position=position,
                kind="NDB",
                frequency=child_text(ndb, "valFreq"),
            )
        )

    for dme in root.findall("Dme"):
        dme_uid = dme.find("DmeUid")
        code_id = child_text(dme_uid, "codeId")
        position = child_geo(dme_uid, "geoLat", "geoLong") if dme_uid is not None else None
        if code_id is None or position is None:
            continue
        register_navaid(
            OfmxNavaid(
                code_id=code_id,
                name=child_text(dme, "txtName"),
                position=position,
                kind="DME",
                frequency=child_text(dme, "valGhostFreq") or child_text(dme, "valFreq"),
            )
        )

    for uni in root.findall("Uni"):
        airport_code_id = child_text(uni, "AhpUid/codeId")
        if airport_code_id != airport_code:
            continue
        unit_uid = uni.find("UniUid")
        if unit_uid is None:
            continue
        mid = unit_uid.get("mid")
        if mid is None:
            continue
        units[mid] = OfmxUnit(
            mid=mid,
            name=child_text(unit_uid, "txtName") or "?",
            code_type=child_text(unit_uid, "codeType") or "?",
            airport_code_id=airport_code_id,
            code_class=child_text(uni, "codeClass"),
        )

    for ser in root.findall("Ser"):
        ser_uid = ser.find("SerUid")
        if ser_uid is None:
            continue
        mid = ser_uid.get("mid")
        unit_mid = ser_uid.find("UniUid").get("mid") if ser_uid.find("UniUid") is not None else None
        code_type = child_text(ser_uid, "codeType")
        if mid is None or unit_mid is None or code_type is None:
            continue
        services[mid] = OfmxService(
            mid=mid,
            unit_mid=unit_mid,
            code_type=code_type,
            sequence_number=child_int(ser_uid, "noSeq"),
        )

    for fqy in root.findall("Fqy"):
        fqy_uid = fqy.find("FqyUid")
        ser_uid = fqy_uid.find("SerUid") if fqy_uid is not None else None
        service_mid = ser_uid.get("mid") if ser_uid is not None else None
        if service_mid is None:
            continue
        frequencies.append(
            OfmxFrequency(
                service_mid=service_mid,
                frequency_mhz=child_text(fqy_uid, "valFreqTrans") or "?",
                code_type=child_text(fqy, "codeType"),
                call_sign=child_text(fqy, "Cdl/txtCallSign"),
                language=child_text(fqy, "Cdl/codeLang"),
            ),
        )

    for ase in root.findall("Ase"):
        ase_uid = ase.find("AseUid")
        if ase_uid is None:
            continue
        mid = ase_uid.get("mid")
        if mid is None:
            continue
        airspaces[mid] = OfmxAirspace(
            mid=mid,
            code_type=child_text(ase_uid, "codeType"),
            code_id=child_text(ase_uid, "codeId"),
            name=child_text(ase, "txtName"),
            lower_value=child_int(ase, "valDistVerLower"),
            lower_unit=child_text(ase, "uomDistVerLower"),
            lower_reference=child_text(ase, "codeDistVerLower"),
            upper_value=child_int(ase, "valDistVerUpper"),
            upper_unit=child_text(ase, "uomDistVerUpper"),
            upper_reference=child_text(ase, "codeDistVerUpper"),
        )

    for abd in root.findall("Abd"):
        ase_uid = abd.find("AbdUid/AseUid")
        if ase_uid is None:
            continue
        airspace_mid = ase_uid.get("mid")
        if airspace_mid is None:
            continue
        vertices = [
            OfmxBoundaryVertex(
                code_type=child_text(avx, "codeType") or "?",
                position=position,
            )
            for avx in abd.findall("Avx")
            for position in [child_geo(avx, "geoLat", "geoLong")]
            if position is not None
        ]
        if vertices:
            all_airspace_boundaries[airspace_mid].append(
                OfmxAirspaceBoundary(
                    airspace_mid=airspace_mid,
                    vertices=vertices,
                ),
            )

    for sae in root.findall("Sae"):
        sae_uid = sae.find("SaeUid")
        if sae_uid is None:
            continue
        ser_uid = sae_uid.find("SerUid")
        ase_uid = sae_uid.find("AseUid")
        if ser_uid is None or ase_uid is None:
            continue
        service_mid = ser_uid.get("mid")
        airspace_mid = ase_uid.get("mid")
        if service_mid and airspace_mid:
            service_airspace_associations.append((service_mid, airspace_mid))

    airport_unit_mids = {mid for mid, unit in units.items() if unit.airport_code_id == airport_code}
    airport_service_mids = {mid for mid, service in services.items() if service.unit_mid in airport_unit_mids}
    airport_frequencies = [
        frequency
        for frequency in frequencies
        if frequency.service_mid in airport_service_mids
    ]
    associated_airspace_mids = {
        airspace_mid
        for service_mid, airspace_mid in service_airspace_associations
        if service_mid in airport_service_mids
    }
    named_airspaces = {
        airspace.mid
        for airspace in airspaces.values()
        if (airspace.name or "").startswith(airport_code)
    }
    airport_airspaces = [
        airspaces[mid]
        for mid in sorted(associated_airspace_mids | named_airspaces)
        if mid in airspaces
    ]
    airport_airspaces_by_mid = {
        airspace.mid: airspace
        for airspace in airport_airspaces
    }
    airport_airspace_boundaries = {
        airspace_mid: list(all_airspace_boundaries[airspace_mid])
        for airspace_mid in airport_airspaces_by_mid
        if airspace_mid in all_airspace_boundaries
    }

    return {
        "airport": airport,
        "runways": runways,
        "runwayDirections": runway_directions,
        "allDesignatedPoints": all_designated_points,
        "allNavaids": dict(sorted(all_navaids.items())),
        "airportDesignatedPoints": sorted(
            airport_designated_points,
            key=lambda point: (point.code_type or "", point.code_id),
        ),
        "units": units,
        "services": services,
        "frequencies": sorted(
            airport_frequencies,
            key=lambda frequency: (frequency.call_sign or "", frequency.frequency_mhz),
        ),
        "airspaces": sorted(
            airport_airspaces,
            key=lambda airspace: (airspace.name or "", airspace.code_id or ""),
        ),
        "airportAirspacesByMid": airport_airspaces_by_mid,
        "airspaceBoundaries": airport_airspace_boundaries,
    }


def parse_openair_coordinate(value: str) -> Geo | None:
    parts = value.strip().split()
    if len(parts) != 4:
        return None
    lat_text, lat_hemi, lon_text, lon_hemi = parts

    def convert(component_text: str, hemisphere: str) -> float | None:
        pieces = component_text.split(":")
        if len(pieces) != 3:
            return None
        try:
            degrees, minutes, seconds = [float(piece) for piece in pieces]
        except ValueError:
            return None
        value = degrees + (minutes / 60.0) + (seconds / 3600.0)
        return -value if hemisphere in {"S", "W"} else value

    latitude = convert(lat_text, lat_hemi.upper())
    longitude = convert(lon_text, lon_hemi.upper())
    if latitude is None or longitude is None:
        return None
    return Geo(latitude, longitude)


def parse_cup_coordinate(value: str) -> float | None:
    match = re.match(r"^(\d{2,3})(\d{2}\.\d+)([NSEW])$", value.strip(), re.IGNORECASE)
    if match is None:
        return None
    degrees_text, minutes_text, hemisphere = match.groups()
    try:
        degrees = float(degrees_text)
        minutes = float(minutes_text)
    except ValueError:
        return None
    decimal = degrees + (minutes / 60.0)
    return -decimal if hemisphere.upper() in {"S", "W"} else decimal


def parse_cup_bundle(path: Path) -> dict[str, Any]:
    with zipfile.ZipFile(path) as archive:
        candidate_names = [
            name
            for name in archive.namelist()
            if name.lower().endswith(".cup") and "/isolated/" in name.lower()
        ]
        if not candidate_names:
            candidate_names = [
                name
                for name in archive.namelist()
                if name.lower().endswith(".cup")
            ]
        if not candidate_names:
            return {
                "sourcePath": None,
                "waypointsByCode": {},
                "waypointsByName": {},
            }
        source_name = candidate_names[0]
        text = archive.read(source_name).decode("utf-8", errors="replace")

    waypoints_by_code: dict[str, CupWaypoint] = {}
    waypoints_by_name: dict[str, CupWaypoint] = {}
    reader = csv.reader(text.splitlines())
    for row in reader:
        if len(row) < 5:
            continue
        if row[0].strip().lower() == "name" and row[1].strip().lower() == "code":
            continue
        name = row[0].strip()
        code_id = row[1].strip().upper()
        country = row[2].strip() or None
        latitude = parse_cup_coordinate(row[3])
        longitude = parse_cup_coordinate(row[4])
        if not name or not code_id or latitude is None or longitude is None:
            continue
        waypoint = CupWaypoint(
            name=name,
            code_id=code_id,
            country=country,
            position=Geo(latitude, longitude),
            elevation=row[5].strip() or None if len(row) > 5 else None,
            style=row[6].strip() or None if len(row) > 6 else None,
            source_path=source_name,
        )
        waypoints_by_code[code_id] = waypoint
        waypoints_by_name[name.strip().upper()] = waypoint

    return {
        "sourcePath": source_name,
        "waypointsByCode": dict(sorted(waypoints_by_code.items())),
        "waypointsByName": dict(sorted(waypoints_by_name.items())),
    }


def canonical_openair_name(name: str) -> str:
    cleaned = re.sub(r"\s+\(\d+/\d+\)", "", name.strip())
    cleaned = re.sub(r"(?:\s+\d{3}\.\d{1,3})+$", "", cleaned)
    return re.sub(r"\s+", " ", cleaned).strip()


def openair_kind(name: str) -> str | None:
    upper = name.upper()
    for prefix in ("CTR", "TMA", "CTA", "RMZ", "TMZ", "ATZ"):
        if upper.startswith(prefix):
            return prefix
    return None


def openair_relevance(
    name: str,
    points: list[Geo],
    airport_position: Geo,
    airport_code: str,
    airport_name: str,
) -> bool:
    if len(points) < 3:
        return False
    name_upper = name.upper()
    airport_tokens = {
        airport_code.upper(),
        *{
            token
            for token in re.split(r"[^A-Z0-9]+", airport_name.upper())
            if len(token) >= 4 and token not in {"AIRPORT", "AERODROME"}
        },
    }
    if any(token and token in name_upper for token in airport_tokens):
        return True

    project = projector(airport_position)
    airport_xy = XY(0.0, 0.0)
    boundary_xy = [project(point) for point in points]
    if point_in_polygon(airport_xy, boundary_xy):
        return True

    nearest_distance = min(
        nearest_point_on_segment(airport_xy, start, end)[0]
        for start, end in zip(boundary_xy, boundary_xy[1:] + boundary_xy[:1])
    )
    return nearest_distance <= 25_000.0


def parse_openair_bundle(
    path: Path,
    airport_position: Geo,
    airport_code: str,
    airport_name: str,
) -> dict[str, Any]:
    with zipfile.ZipFile(path) as archive:
        candidate_names = [
            name
            for name in archive.namelist()
            if name.lower().endswith(".openair.txt") and "/isolated/" in name.lower()
        ]
        if not candidate_names:
            candidate_names = [
                name
                for name in archive.namelist()
                if name.lower().endswith(".openair.txt")
            ]
        if not candidate_names:
            return {
                "sourcePath": None,
                "airspaces": [],
            }
        source_name = next(
            (name for name in candidate_names if "seeyou" in name.lower()),
            candidate_names[0],
        )
        text = archive.read(source_name).decode("utf-8", errors="replace")

    grouped_airspaces: dict[tuple[str, str | None, str, str | None, str | None], dict[str, Any]] = {}
    current: dict[str, Any] | None = None

    def flush_current() -> None:
        if current is None or len(current["points"]) < 3:
            return
        points = current["points"]
        if not openair_relevance(current["name"], points, airport_position, airport_code, airport_name):
            return
        canonical_name = canonical_openair_name(current["name"])
        key = (
            current["airspaceClass"],
            current["kind"],
            canonical_name,
            current["lowerLimit"],
            current["upperLimit"],
        )
        existing = grouped_airspaces.get(key)
        if existing is None:
            grouped_airspaces[key] = {
                "airspaceClass": current["airspaceClass"],
                "kind": current["kind"],
                "name": canonical_name,
                "lowerLimit": current["lowerLimit"],
                "upperLimit": current["upperLimit"],
                "boundaries": [points],
            }
        else:
            existing["boundaries"].append(points)

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("*"):
            continue
        if line.startswith("AC "):
            flush_current()
            current = {
                "airspaceClass": line[3:].strip().upper(),
                "kind": None,
                "name": "OPENAIR",
                "lowerLimit": None,
                "upperLimit": None,
                "points": [],
            }
            continue
        if current is None:
            continue
        if line.startswith("AY "):
            current["kind"] = line[3:].strip().upper()
        elif line.startswith("AN "):
            current["name"] = line[3:].strip()
        elif line.startswith("AL "):
            current["lowerLimit"] = line[3:].strip()
        elif line.startswith("AH "):
            current["upperLimit"] = line[3:].strip()
        elif line.startswith("DP "):
            point = parse_openair_coordinate(line[3:])
            if point is not None:
                current["points"].append(point)

    flush_current()

    parsed_airspaces = [
        OpenAirAirspace(
            airspace_class=str(entry["airspaceClass"]),
            kind=str(entry["kind"]) if entry["kind"] is not None else None,
            name=str(entry["name"]),
            lower_limit=str(entry["lowerLimit"]) if entry["lowerLimit"] is not None else None,
            upper_limit=str(entry["upperLimit"]) if entry["upperLimit"] is not None else None,
            boundaries=list(entry["boundaries"]),
            source_path=source_name,
        )
        for _, entry in sorted(
            grouped_airspaces.items(),
            key=lambda item: (
                item[0][0],
                item[0][1] or "",
                item[0][2],
                item[0][3] or "",
                item[0][4] or "",
            ),
        )
    ]

    return {
        "sourcePath": source_name,
        "airspaces": parsed_airspaces,
    }


def runway_position_cross_check(
    apt_positions: dict[str, Geo],
    ofmx_positions: dict[str, OfmxRunwayDirection],
    cifp_positions: dict[str, Geo],
    project,
) -> list[dict[str, Any]]:
    designators = sorted(set(apt_positions) | set(ofmx_positions) | set(cifp_positions))
    entries: list[dict[str, Any]] = []
    for designator in designators:
        apt_xy = project(apt_positions[designator]) if designator in apt_positions else None
        ofmx_geo = ofmx_positions[designator].position if designator in ofmx_positions else None
        ofmx_xy = project(ofmx_geo) if ofmx_geo is not None else None
        cifp_xy = project(cifp_positions[designator]) if designator in cifp_positions else None
        entries.append(
            {
                "designator": designator,
                "hasApt": apt_xy is not None,
                "hasOfmx": ofmx_xy is not None,
                "hasCifp": cifp_xy is not None,
                "aptToOfmxMeters": apt_xy.distance_to(ofmx_xy) if apt_xy and ofmx_xy else None,
                "aptToCifpMeters": apt_xy.distance_to(cifp_xy) if apt_xy and cifp_xy else None,
                "ofmxToCifpMeters": ofmx_xy.distance_to(cifp_xy) if ofmx_xy and cifp_xy else None,
            },
        )
    return entries


def nearest_node(point: XY, nodes: dict[int, XY]) -> tuple[int, float]:
    node_id, node = min(nodes.items(), key=lambda item: point.distance_to(item[1]))
    return node_id, point.distance_to(node)


def nearest_edge(point: XY, edges: list[tuple[TaxiEdge, XY, XY]]) -> tuple[TaxiEdge, float]:
    edge, distance = min(
        ((edge, point_to_segment_distance(point, start, end)) for edge, start, end in edges),
        key=lambda item: item[1],
    )
    return edge, distance


def midpoint(point_a: XY, point_b: XY) -> XY:
    return XY((point_a.x + point_b.x) / 2.0, (point_a.y + point_b.y) / 2.0)


def ground_alignment_report(
    drawing_manifest: dict[str, Any],
    document: DxfDocument,
    runway_axis_start: XY,
    runway_axis_end: XY,
    taxi_nodes: dict[int, TaxiNode],
    taxi_edges: list[TaxiEdge],
    project,
    thresholds: dict[str, float],
    overrides: dict[str, Any] | None = None,
) -> dict[str, Any]:
    longest_line = max(document.lines, key=lambda line: line.length)
    forward_transform = fit_similarity(longest_line.start, longest_line.end, runway_axis_start, runway_axis_end)
    reverse_transform = fit_similarity(longest_line.end, longest_line.start, runway_axis_start, runway_axis_end)
    orientation = drawing_manifest["transform"]["orientation"]
    chosen_transform = forward_transform if orientation == "forward" else reverse_transform
    alternate_transform = reverse_transform if orientation == "forward" else forward_transform

    taxi_nodes_xy = {node_id: project(node.position) for node_id, node in taxi_nodes.items()}
    taxi_edges_xy = [
        (edge, project(taxi_nodes[edge.start].position), project(taxi_nodes[edge.end].position))
        for edge in taxi_edges
        if edge.start in taxi_nodes and edge.end in taxi_nodes
    ]
    ground_overrides = overrides or {}
    marker_overrides = override_by_index(ground_overrides.get("markers", []), "markerIndex")
    component_overrides = override_by_index(ground_overrides.get("components", []), "componentIndex")

    marker_results = []
    for marker_index, marker in enumerate(document.points, start=1):
        mapped_point = chosen_transform.apply(marker.point)
        nearest_node_id, node_distance = nearest_node(mapped_point, taxi_nodes_xy)
        nearest_edge_record, edge_distance = nearest_edge(mapped_point, taxi_edges_xy)
        reference_classification = classify_distance(
            edge_distance,
            thresholds["markerOkMeters"],
            thresholds["markerSuspectMeters"],
        )
        override_result = apply_override(reference_classification, marker_overrides.get(marker_index))
        marker_results.append(
            {
                "index": marker_index,
                "layer": marker.layer,
                "rawPoint": {"x": marker.point.x, "y": marker.point.y},
                "nearestNodeId": nearest_node_id,
                "nearestNodeName": taxi_nodes[nearest_node_id].name,
                "nearestNodeUsage": taxi_nodes[nearest_node_id].usage,
                "nodeDistanceMeters": node_distance,
                "nearestEdge": {
                    "start": nearest_edge_record.start,
                    "end": nearest_edge_record.end,
                    "kind": nearest_edge_record.kind,
                    "name": nearest_edge_record.name,
                },
                "edgeDistanceMeters": edge_distance,
                "classification": override_result["classification"],
                "referenceClassification": override_result["referenceClassification"],
                "label": override_result["label"],
                "note": override_result["note"],
            },
        )

    components = connected_components(document.lines)
    component_results = []
    for component_index, component in enumerate(components, start=1):
        transformed_endpoints = [chosen_transform.apply(endpoint) for line in component for endpoint in (line.start, line.end)]
        endpoint_distances = [
            min(point_to_segment_distance(point, start, end) for _, start, end in taxi_edges_xy)
            for point in transformed_endpoints
        ]
        stats = component_stats(component)
        reference_classification = classify_distance(
            mean(endpoint_distances),
            thresholds["componentMeanEdgeOkMeters"],
            thresholds["componentMeanEdgeSuspectMeters"],
        )
        override_result = apply_override(reference_classification, component_overrides.get(component_index))
        component_results.append(
            {
                "index": component_index,
                "lineCount": stats["lineCount"],
                "totalLength": stats["totalLength"],
                "meanEndpointEdgeMeters": mean(endpoint_distances),
                "maxEndpointEdgeMeters": max(endpoint_distances),
                "bounds": {
                    "minX": stats["minX"],
                    "maxX": stats["maxX"],
                    "minY": stats["minY"],
                    "maxY": stats["maxY"],
                },
                "classification": override_result["classification"],
                "referenceClassification": override_result["referenceClassification"],
                "label": override_result["label"],
                "note": override_result["note"],
            },
        )

    runway_vector = runway_axis_end - runway_axis_start
    runway_length = math.hypot(runway_vector.x, runway_vector.y)
    normal = XY(-runway_vector.y / runway_length, runway_vector.x / runway_length)
    shift_trials = []
    for meters in range(-60, 61, 5):
        shifted = chosen_transform.shifted(normal.scale(float(meters)))
        midpoint_distances = [
            min(
                point_to_segment_distance(midpoint(shifted.apply(line.start), shifted.apply(line.end)), start, end)
                for _, start, end in taxi_edges_xy
            )
            for line in document.lines
        ]
        shift_trials.append({"meters": meters, "meanEdgeDistanceMeters": mean(midpoint_distances)})
    best_shift = min(shift_trials, key=lambda trial: trial["meanEdgeDistanceMeters"])

    return {
        "path": drawing_manifest["path"],
        "role": drawing_manifest["role"],
        "entityCounts": document.entity_counts,
        "entityLayers": document.entity_layers,
        "lineCount": len(document.lines),
        "pointCount": len(document.points),
        "componentCount": len(components),
        "controlLine": {
            "length": longest_line.length,
            "angleDegrees": longest_line.angle_deg,
        },
        "transform": {
            "strategy": drawing_manifest["transform"]["strategy"],
            "orientation": orientation,
            "scale": chosen_transform.scale,
            "rotationDegrees": math.degrees(chosen_transform.rotation_rad),
            "alternateScale": alternate_transform.scale,
            "alternateRotationDegrees": math.degrees(alternate_transform.rotation_rad),
        },
        "runwayOffsetCheck": {
            "bestPerpendicularShiftMeters": best_shift["meters"],
            "bestMeanMidpointEdgeMeters": best_shift["meanEdgeDistanceMeters"],
        },
        "markers": marker_results,
        "components": component_results,
    }


def find_endpoint_attachments(
    components: list[list[DxfLine]],
    tolerance: float,
) -> list[EndpointAttachment]:
    attachments: list[EndpointAttachment] = []
    for source_index, component in enumerate(components, start=1):
        for endpoint in degree_one_points(component):
            best_match: EndpointAttachment | None = None
            for target_index, target_component in enumerate(components, start=1):
                if source_index == target_index:
                    continue
                for line in target_component:
                    distance, raw_parameter = point_to_segment_projection(endpoint, line.start, line.end)
                    if distance > tolerance:
                        continue
                    if raw_parameter <= 0.0 or raw_parameter >= 1.0:
                        continue
                    candidate = EndpointAttachment(
                        source_component=source_index,
                        target_component=target_index,
                        endpoint=endpoint,
                        target_segment_start=line.start,
                        target_segment_end=line.end,
                        distance=distance,
                        segment_position=raw_parameter,
                    )
                    if best_match is None or candidate.distance < best_match.distance:
                        best_match = candidate
            if best_match is not None:
                attachments.append(best_match)
    return attachments


def circuit_alignment_report(
    manifest: dict[str, Any],
    drawing_manifest: dict[str, Any],
    document: DxfDocument,
    runway_axis_start: XY,
    runway_axis_end: XY,
    tower_xy: XY | None,
    thresholds: dict[str, float],
) -> dict[str, Any]:
    declared_anchor_pair = drawing_anchor_pair_from_manifest(
        manifest,
        drawing_manifest["id"],
        document,
    )
    if declared_anchor_pair is None:
        point_pairs = [
            (a.point, b.point)
            for index, a in enumerate(document.points)
            for b in document.points[index + 1 :]
        ]
        anchor_a, anchor_b = max(point_pairs, key=lambda pair: pair[0].distance_to(pair[1]))
        anchor_ids = None
        anchor_strategy = "farthest_pair_fallback"
    else:
        (anchor_a_id, anchor_a), (anchor_b_id, anchor_b) = declared_anchor_pair
        anchor_ids = [anchor_a_id, anchor_b_id]
        anchor_strategy = "manifest_procedure_anchors"
    forward_transform = fit_similarity(anchor_a, anchor_b, runway_axis_start, runway_axis_end)
    reverse_transform = fit_similarity(anchor_b, anchor_a, runway_axis_start, runway_axis_end)
    orientation = drawing_manifest["transform"]["orientation"]
    chosen_transform = forward_transform if orientation == "forward" else reverse_transform
    alternate_transform = reverse_transform if orientation == "forward" else forward_transform

    residual_points = [point.point for point in document.points if point.point not in {anchor_a, anchor_b}]

    def tower_residual(transform: Similarity) -> float | None:
        if tower_xy is None or not residual_points:
            return None
        return min(transform.apply(point).distance_to(tower_xy) for point in residual_points)

    chosen_residual = tower_residual(chosen_transform)
    alternate_residual = tower_residual(alternate_transform)

    components = sorted(connected_components(document.lines), key=lambda component: len(component), reverse=True)
    component_results = []
    for component_index, component in enumerate(components, start=1):
        stats = component_stats(component)
        graph = graph_summary(component)
        component_results.append(
            {
                "index": component_index,
                "lineCount": stats["lineCount"],
                "totalLength": stats["totalLength"],
                "graph": graph,
                "bounds": {
                    "minX": stats["minX"],
                    "maxX": stats["maxX"],
                    "minY": stats["minY"],
                    "maxY": stats["maxY"],
                },
            },
        )

    attachments = find_endpoint_attachments(
        components,
        thresholds["segmentJoinToleranceDrawingUnits"],
    )

    return {
        "path": drawing_manifest["path"],
        "role": drawing_manifest["role"],
        "entityCounts": document.entity_counts,
        "entityLayers": document.entity_layers,
        "lineCount": len(document.lines),
        "pointCount": len(document.points),
        "componentCount": len(components),
        "anchorPairLength": anchor_a.distance_to(anchor_b),
        "anchorSelection": {
            "strategy": anchor_strategy,
            "anchorIds": anchor_ids,
        },
        "transform": {
            "strategy": drawing_manifest["transform"]["strategy"],
            "orientation": orientation,
            "scale": chosen_transform.scale,
            "rotationDegrees": math.degrees(chosen_transform.rotation_rad),
            "alternateScale": alternate_transform.scale,
            "alternateRotationDegrees": math.degrees(alternate_transform.rotation_rad),
        },
        "towerCrossCheck": {
            "chosenResidualMeters": chosen_residual,
            "chosenClassification": None
            if chosen_residual is None
            else classify_distance(
                chosen_residual,
                thresholds["towerResidualOkMeters"],
                thresholds["towerResidualSuspectMeters"],
            ),
            "alternateResidualMeters": alternate_residual,
        },
        "components": component_results,
        "attachments": [
            {
                "sourceComponent": attachment.source_component,
                "targetComponent": attachment.target_component,
                "endpoint": {"x": attachment.endpoint.x, "y": attachment.endpoint.y},
                "targetSegmentStart": {
                    "x": attachment.target_segment_start.x,
                    "y": attachment.target_segment_start.y,
                },
                "targetSegmentEnd": {
                    "x": attachment.target_segment_end.x,
                    "y": attachment.target_segment_end.y,
                },
                "distanceDrawingUnits": attachment.distance,
                "segmentPosition": attachment.segment_position,
            }
            for attachment in attachments
        ],
    }


def chart_inventory(charts_directory: Path | None) -> dict[str, Any]:
    if charts_directory is None or not charts_directory.exists():
        return {"count": 0, "files": []}
    files = sorted(path.name for path in charts_directory.iterdir() if path.is_file())
    return {"count": len(files), "files": files}


def parse_chart_coordinate(value: str) -> float | None:
    trimmed = value.strip().upper()
    if not trimmed:
        return None
    hemisphere = trimmed[0]
    numeric = trimmed[1:]
    if hemisphere in {"N", "S"}:
        degree_digits = 2
    elif hemisphere in {"E", "W"}:
        degree_digits = 3
    else:
        return None
    try:
        degrees = int(numeric[:degree_digits])
        minutes = int(numeric[degree_digits : degree_digits + 2])
        seconds = float(numeric[degree_digits + 2 :])
    except ValueError:
        return None
    decimal = degrees + minutes / 60.0 + seconds / 3600.0
    return -decimal if hemisphere in {"S", "W"} else decimal


def parse_chart_geo(lat_text: str, lon_text: str) -> Geo | None:
    lat = parse_chart_coordinate(lat_text)
    lon = parse_chart_coordinate(lon_text)
    if lat is None or lon is None:
        return None
    return Geo(lat=lat, lon=lon)


def extract_chart_coding_table_fixes(charts_directory: Path | None) -> dict[str, Any]:
    if charts_directory is None or not charts_directory.exists():
        return {
            "available": False,
            "filesScanned": [],
            "resolvedFixes": {},
            "conflictingIdentifiers": [],
            "note": "No chart directory available.",
        }
    if shutil.which("pdftotext") is None:
        return {
            "available": False,
            "filesScanned": [],
            "resolvedFixes": {},
            "conflictingIdentifiers": [],
            "note": "pdftotext is not available in PATH.",
        }

    lat_pattern = re.compile(r"^\s*([NS]\d{6}\.\d{2})\b")
    lat_inline_pattern = re.compile(r"\b([NS]\d{6}\.\d{2})\b")
    lon_pattern = re.compile(r"\b([EW]\d{7}\.\d{2})\b")
    row_pattern = re.compile(
        r"^\s*[A-Z]{2}\s+([A-Z0-9]{2,10})(?:\s+[A-Za-z0-9]+)?\s+(yes|no)\b",
    )
    collected: dict[str, dict[tuple[float, float], set[str]]] = defaultdict(lambda: defaultdict(set))
    files_scanned: list[str] = []

    for pdf_path in sorted(charts_directory.glob("*.pdf")):
        files_scanned.append(pdf_path.name)
        try:
            text = subprocess.check_output(
                ["pdftotext", "-layout", str(pdf_path), "-"],
                text=True,
            )
        except (FileNotFoundError, subprocess.CalledProcessError):
            continue
        pending_lat: str | None = None
        pending_identifier: str | None = None
        pending_identifier_lat: str | None = None
        for raw_line in text.splitlines():
            if pending_identifier is not None:
                lon_match = lon_pattern.search(raw_line)
                if lon_match is not None and pending_identifier_lat is not None:
                    position = parse_chart_geo(pending_identifier_lat, lon_match.group(1))
                    if position is not None:
                        key = (round(position.lat, 8), round(position.lon, 8))
                        collected[pending_identifier][key].add(pdf_path.name)
                    pending_identifier = None
                    pending_identifier_lat = None
                    continue
            stripped = raw_line.strip()
            lat_match = lat_pattern.match(raw_line)
            if lat_match is not None:
                pending_identifier = None
                pending_identifier_lat = None
                pending_lat = lat_match.group(1)
                continue
            match = row_pattern.match(raw_line)
            row_lat = pending_lat
            if row_lat is None and match is not None:
                inline_lat_match = lat_inline_pattern.search(raw_line)
                if inline_lat_match is not None:
                    row_lat = inline_lat_match.group(1)
            if row_lat is not None and match is not None:
                identifier = match.group(1)
                lon_match = lon_pattern.search(raw_line)
                if lon_match is not None:
                    position = parse_chart_geo(row_lat, lon_match.group(1))
                    if position is not None:
                        key = (round(position.lat, 8), round(position.lon, 8))
                        collected[identifier][key].add(pdf_path.name)
                else:
                    pending_identifier = identifier
                    pending_identifier_lat = row_lat
                pending_lat = None
                continue
            if stripped:
                pending_lat = None
                pending_identifier = None
                pending_identifier_lat = None

    resolved_fixes: dict[str, dict[str, Any]] = {}
    conflicting_identifiers: list[str] = []
    for identifier, positions in sorted(collected.items()):
        if len(positions) != 1:
            conflicting_identifiers.append(identifier)
            continue
        (latitude, longitude), source_charts = next(iter(positions.items()))
        resolved_fixes[identifier] = {
            "codeId": identifier,
            "latitude": latitude,
            "longitude": longitude,
            "sourceCharts": sorted(source_charts),
        }

    return {
        "available": True,
        "filesScanned": files_scanned,
        "resolvedFixes": resolved_fixes,
        "conflictingIdentifiers": conflicting_identifiers,
        "note": "Chart coding-table extraction is based on local pdf-to-text parsing of published IFR charts.",
    }


def destination_geo(origin: Geo, bearing_deg: float, distance_nm: float) -> Geo:
    angular_distance = (distance_nm * 1852.0) / EARTH_RADIUS_METERS
    bearing = math.radians(bearing_deg)
    lat1 = math.radians(origin.lat)
    lon1 = math.radians(origin.lon)
    lat2 = math.asin(
        math.sin(lat1) * math.cos(angular_distance)
        + math.cos(lat1) * math.sin(angular_distance) * math.cos(bearing)
    )
    lon2 = lon1 + math.atan2(
        math.sin(bearing) * math.sin(angular_distance) * math.cos(lat1),
        math.cos(angular_distance) - math.sin(lat1) * math.sin(lat2),
    )
    return Geo(lat=math.degrees(lat2), lon=math.degrees(lon2))


def derive_cifp_approach_geometry(
    cifp_data: dict[str, Any],
    ofmx_data: dict[str, Any],
    chart_fix_data: dict[str, Any] | None = None,
) -> dict[str, Any]:
    known_positions: dict[str, Geo] = {
        identifier: point.position
        for identifier, point in ofmx_data["allDesignatedPoints"].items()
    }
    known_positions.update(
        {
            identifier: navaid.position
            for identifier, navaid in ofmx_data.get("allNavaids", {}).items()
        }
    )
    known_positions.update(
        {
            identifier: Geo(
                lat=float(entry["latitude"]),
                lon=float(entry["longitude"]),
            )
            for identifier, entry in (chart_fix_data or {}).get("resolvedFixes", {}).items()
            if entry.get("latitude") is not None and entry.get("longitude") is not None
        }
    )
    known_positions.update(
        {
            f"RW{designator}": position
            for designator, position in cifp_data.get("runways", {}).items()
        }
    )

    derived: dict[str, dict[str, Any]] = {}

    def register(identifier: str, position: Geo, method: str, record: CifpApproachRecord) -> bool:
        if identifier in known_positions:
            return False
        existing = derived.get(identifier)
        position_payload = {
            "codeId": identifier,
            "latitude": round(position.lat, 8),
            "longitude": round(position.lon, 8),
            "derivationMethod": method,
            "procedure": record.procedure_name,
            "transition": record.transition,
            "pathTerminator": record.path_terminator,
            "referenceIdentifier": record.reference_identifier,
        }
        if existing is not None:
            if (
                existing["latitude"] == position_payload["latitude"]
                and existing["longitude"] == position_payload["longitude"]
            ):
                return False
            return False
        derived[identifier] = position_payload
        known_positions[identifier] = position
        return True

    changed = True
    while changed:
        changed = False
        for record in cifp_data.get("approachRecords", []):
            reference_identifier = record.reference_identifier
            if reference_identifier and reference_identifier in known_positions:
                reference_position = known_positions[reference_identifier]
                if (
                    record.fix_identifier
                    and record.fix_identifier not in known_positions
                    and record.primary_course_tenths is not None
                    and record.primary_distance_tenths is not None
                ):
                    course_deg = record.primary_course_tenths / 10.0
                    distance_nm = record.primary_distance_tenths / 10.0
                    if record.reference_code_type == "D":
                        bearing = course_deg
                    elif (record.reference_code_type, record.reference_subtype) == ("P", "I"):
                        bearing = (course_deg + 180.0) % 360.0
                    else:
                        continue
                    changed = (
                        register(
                            record.fix_identifier,
                            destination_geo(reference_position, bearing, distance_nm),
                            "approach_reference_projection",
                            record,
                        )
                        or changed
                    )
            elif (
                reference_identifier
                and reference_identifier not in known_positions
                and record.fix_identifier in known_positions
                and record.primary_course_tenths is not None
                and record.primary_distance_tenths is not None
                and (record.reference_code_type, record.reference_subtype) == ("P", "I")
            ):
                fix_position = known_positions[record.fix_identifier]
                changed = (
                    register(
                        reference_identifier,
                        destination_geo(
                            fix_position,
                            record.primary_course_tenths / 10.0,
                            record.primary_distance_tenths / 10.0,
                        ),
                        "localizer_reference_back_projection",
                        record,
                    )
                    or changed
                )

    return {
        "resolvedFixes": dict(sorted(derived.items())),
        "note": "Derived approach geometry is inferred from CIFP APPCH reference course/distance pairs and known runway/navaid positions.",
    }


@dataclass(frozen=True)
class XPlaneFixEntry:
    identifier: str
    position: Geo
    region: str
    country: str


@dataclass(frozen=True)
class XPlaneNavaidEntry:
    identifier: str
    position: Geo
    type_code: int
    region: str
    country: str
    frequency: int | None
    name: str | None


def parse_xplane_fix_cache(path: Path) -> dict[str, XPlaneFixEntry]:
    """Parse an X-Plane 12 earth_fix.dat-subset file into an identifier lookup.

    Accepts both the full earth_fix.dat and the per-airport cache produced by
    bin/extract_xplane_airport_cache.py. Lines starting with a letter or the
    terminator "99" are skipped.
    """
    entries: dict[str, XPlaneFixEntry] = {}
    for raw in path.read_text().splitlines():
        parts = raw.split()
        if len(parts) < 5:
            continue
        try:
            lat = float(parts[0])
            lon = float(parts[1])
        except ValueError:
            continue
        entry = XPlaneFixEntry(
            identifier=parts[2],
            position=Geo(lat, lon),
            region=parts[3],
            country=parts[4],
        )
        # A given identifier may appear multiple times globally; keep the first.
        entries.setdefault(entry.identifier, entry)
    return entries


def parse_xplane_navaid_cache(path: Path) -> dict[str, XPlaneNavaidEntry]:
    """Parse an X-Plane 12 earth_nav.dat-subset file into an identifier lookup."""
    entries: dict[str, XPlaneNavaidEntry] = {}
    for raw in path.read_text().splitlines():
        parts = raw.split()
        if len(parts) < 11:
            continue
        try:
            type_code = int(parts[0])
            lat = float(parts[1])
            lon = float(parts[2])
        except ValueError:
            continue
        frequency_raw = parts[4] if len(parts) > 4 else ""
        try:
            frequency = int(frequency_raw)
        except ValueError:
            frequency = None
        entry = XPlaneNavaidEntry(
            identifier=parts[7],
            position=Geo(lat, lon),
            type_code=type_code,
            region=parts[8],
            country=parts[9],
            frequency=frequency,
            name=" ".join(parts[10:]) if len(parts) > 10 else None,
        )
        entries.setdefault(entry.identifier, entry)
    return entries


def cifp_fix_resolution(
    cifp_data: dict[str, Any],
    ofmx_data: dict[str, Any],
    chart_fix_data: dict[str, Any] | None = None,
    xplane_fixes: dict[str, XPlaneFixEntry] | None = None,
    xplane_navaids: dict[str, XPlaneNavaidEntry] | None = None,
) -> dict[str, Any]:
    designated_codes = set(ofmx_data["allDesignatedPoints"].keys())
    navaid_codes = set(ofmx_data.get("allNavaids", {}).keys())
    chart_codes = set((chart_fix_data or {}).get("resolvedFixes", {}).keys())
    derived_fix_data = derive_cifp_approach_geometry(cifp_data, ofmx_data, chart_fix_data)
    derived_codes = set(derived_fix_data["resolvedFixes"].keys())
    xplane_fix_codes = set((xplane_fixes or {}).keys())
    xplane_navaid_codes = set((xplane_navaids or {}).keys())
    xplane_codes = xplane_fix_codes | xplane_navaid_codes
    ofmx_codes = designated_codes | navaid_codes
    all_codes = ofmx_codes | chart_codes | derived_codes | xplane_codes
    identifiers = sorted(cifp_data["fixRefs"].keys())
    present = [identifier for identifier in identifiers if identifier in all_codes]
    present_in_ofmx = [identifier for identifier in identifiers if identifier in ofmx_codes]
    present_in_designated_points = [identifier for identifier in identifiers if identifier in designated_codes]
    present_in_navaids = [identifier for identifier in identifiers if identifier in navaid_codes]
    present_in_chart_tables = [identifier for identifier in identifiers if identifier in chart_codes]
    present_in_derived_approach_geometry = [identifier for identifier in identifiers if identifier in derived_codes]
    present_in_xplane_fixes = [identifier for identifier in identifiers if identifier in xplane_fix_codes]
    present_in_xplane_navaids = [identifier for identifier in identifiers if identifier in xplane_navaid_codes]
    missing = [identifier for identifier in identifiers if identifier not in all_codes]
    return {
        "totalDistinctIdentifiers": len(identifiers),
        "presentInCheckedInSources": present,
        "presentInOfmxSources": present_in_ofmx,
        "presentInOfmxDesignatedPoints": present_in_designated_points,
        "presentInOfmxNavaids": present_in_navaids,
        "presentInChartCodingTables": present_in_chart_tables,
        "presentInDerivedApproachGeometry": present_in_derived_approach_geometry,
        "presentInXplaneFixes": present_in_xplane_fixes,
        "presentInXplaneNavaids": present_in_xplane_navaids,
        "missingFromCheckedInSources": missing,
        "resolvedDerivedApproachGeometry": derived_fix_data["resolvedFixes"],
        "note": "This scan checks checked-in OFMX designated points, parsed VOR/NDB/DME records, local IFR chart coding tables, derived CIFP approach geometry, and optional X-Plane 12 fix/navaid caches. Some localizer/final-approach identifiers may still remain unresolved where no honest source or derivation is available.",
    }


def build_report(manifest_path: Path) -> dict[str, Any]:
    root = repo_root()
    manifest = load_manifest(manifest_path)

    apt_path = resolve_path(root, manifest["sources"]["aptDat"])
    cifp_path = resolve_path(root, manifest["sources"]["cifp"])
    ofmx_path = resolve_path(root, manifest["sources"]["ofmx"])
    charts_directory = resolve_path(root, manifest["sources"].get("chartsDirectory"))

    runways, tower, taxi_nodes, taxi_edges, apt_metadata, parking_positions = parse_apt(apt_path)
    origin = Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = projector(origin)
    reference_runway = manifest["geometricControl"]["referenceRunway"]
    runway_axis_start, runway_axis_end = runway_axis_xy(runways, reference_runway, project)
    tower_xy = project(tower.position) if tower else None

    ground_manifest = next(drawing for drawing in manifest["drawings"] if drawing["id"] == "ground")
    circuit_manifest = next(drawing for drawing in manifest["drawings"] if drawing["id"] == "vfr_circuit")
    ground_document = parse_dxf(resolve_path(root, ground_manifest["path"]))
    circuit_document = parse_dxf(resolve_path(root, circuit_manifest["path"]))

    ofmx_data = parse_ofmx(ofmx_path, manifest["airportCode"])
    cifp_data = parse_cifp(cifp_path)
    chart_fix_data = extract_chart_coding_table_fixes(charts_directory)
    apt_positions = apt_runway_positions(runways)
    runway_cross_check = runway_position_cross_check(
        apt_positions,
        ofmx_data["runwayDirections"],
        cifp_data["runways"],
        project,
    )

    ground_report = ground_alignment_report(
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
    circuit_report = circuit_alignment_report(
        manifest,
        circuit_manifest,
        circuit_document,
        runway_axis_start,
        runway_axis_end,
        tower_xy,
        manifest["reportThresholds"],
    )

    return {
        "airportCode": manifest["airportCode"],
        "airportName": manifest["airportName"],
        "manifest": {
            "referenceRunway": reference_runway,
            "displacedThresholdModelled": manifest["geometricControl"]["displacedThresholdModelled"],
            "authoringStatus": manifest["authoringStatus"],
            "namedMappings": manifest.get("namedMappings", {}),
            "knownGaps": manifest.get("knownGaps", []),
        },
        "sourceInventory": {
            "apt": {
                "datum": {"lat": origin.lat, "lon": origin.lon},
                "runwayCount": len({id(record): record for record in runways.values()}),
                "runways": sorted(
                    [
                        {
                            "designatorPair": f"{record.designator_a}/{record.designator_b}",
                            "widthMeters": record.width_m,
                            "displacedThresholdA": record.displaced_a_m,
                            "displacedThresholdB": record.displaced_b_m,
                        }
                        for record in {id(runway): runway for runway in runways.values()}.values()
                    ],
                    key=lambda runway: runway["designatorPair"],
                ),
                "taxiNodeCount": len(taxi_nodes),
                "taxiEdgeCount": len(taxi_edges),
                "parkingPositionCount": len(parking_positions),
                "towerPresent": tower is not None,
            },
            "charts": chart_inventory(charts_directory),
            "ofmx": {
                "airport": {
                    "name": ofmx_data["airport"].name if ofmx_data["airport"] else None,
                    "elevationFeet": ofmx_data["airport"].elevation_ft if ofmx_data["airport"] else None,
                    "magneticVariation": ofmx_data["airport"].magnetic_variation if ofmx_data["airport"] else None,
                    "transitionAltitudeFeet": ofmx_data["airport"].transition_altitude_ft if ofmx_data["airport"] else None,
                },
                "runways": [
                    {
                        "designator": runway.designator,
                        "lengthMeters": runway.length_m,
                        "widthMeters": runway.width_m,
                        "composition": runway.composition,
                    }
                    for runway in ofmx_data["runways"]
                ],
                "designatedPoints": [
                    {
                        "codeId": point.code_id,
                        "name": point.name,
                        "type": point.code_type,
                    }
                    for point in ofmx_data["airportDesignatedPoints"]
                ],
                "frequencies": [
                    {
                        "callSign": frequency.call_sign,
                        "frequencyMhz": frequency.frequency_mhz,
                        "serviceType": ofmx_data["services"][frequency.service_mid].code_type
                        if frequency.service_mid in ofmx_data["services"]
                        else None,
                    }
                    for frequency in ofmx_data["frequencies"]
                ],
                "airspaces": [
                    {
                        "name": airspace.name,
                        "codeId": airspace.code_id,
                        "type": airspace.code_type,
                        "lower": format_limit(airspace.lower_value, airspace.lower_unit, airspace.lower_reference),
                        "upper": format_limit(airspace.upper_value, airspace.upper_unit, airspace.upper_reference),
                    }
                    for airspace in ofmx_data["airspaces"]
                ],
            },
            "cifp": {
                "procedures": cifp_data["procedures"],
                "runways": {
                    designator: {"lat": position.lat, "lon": position.lon}
                    for designator, position in sorted(cifp_data["runways"].items())
                },
                "fixResolution": cifp_fix_resolution(cifp_data, ofmx_data, chart_fix_data),
            },
            "runwayCrossCheck": runway_cross_check,
        },
        "drawings": {
            "ground": ground_report,
            "vfrCircuit": circuit_report,
        },
    }


def format_distance(value: float | None) -> str:
    return "?" if value is None else f"{value:.1f}m"


def render_markdown(report: dict[str, Any]) -> str:
    lines: list[str] = []
    lines.append(f"# {report['airportCode']} authoring report")
    lines.append("")
    lines.append("## Manifest")
    lines.append(f"- Reference runway: {report['manifest']['referenceRunway']}")
    lines.append(f"- Displaced threshold modelled: {report['manifest']['displacedThresholdModelled']}")
    lines.append("- Declared authoring status:")
    for item in report["manifest"]["authoringStatus"]:
        lines.append(f"  - {item['id']}: {item['status']}")
        for note in item["notes"]:
            lines.append(f"    - {note}")
    if report["manifest"]["knownGaps"]:
        lines.append("- Declared known gaps:")
        for gap in report["manifest"]["knownGaps"]:
            lines.append(f"  - {gap}")
    lines.append("")
    lines.append("## Source inventory")
    apt = report["sourceInventory"]["apt"]
    lines.append(
        f"- apt.dat: {apt['runwayCount']} runway pairs, {apt['taxiNodeCount']} taxi nodes, "
        f"{apt['taxiEdgeCount']} taxi edges, tower present={apt['towerPresent']}"
    )
    chart_inventory_report = report["sourceInventory"]["charts"]
    lines.append(
        f"- Charts directory: {chart_inventory_report['count']} files"
        + (f" ({', '.join(chart_inventory_report['files'])})" if chart_inventory_report["files"] else "")
    )
    ofmx = report["sourceInventory"]["ofmx"]
    lines.append(
        f"- OFMX airport: {ofmx['airport']['name']} elev={ofmx['airport']['elevationFeet']}ft "
        f"magvar={ofmx['airport']['magneticVariation']} transition={ofmx['airport']['transitionAltitudeFeet']}ft"
    )
    lines.append(
        f"- OFMX associated designated points ({len(ofmx['designatedPoints'])}): "
        + ", ".join(f"{point['codeId']} [{point['type']}]" for point in ofmx["designatedPoints"])
    )
    lines.append(
        f"- OFMX frequencies ({len(ofmx['frequencies'])}): "
        + ", ".join(
            f"{frequency['callSign']} {frequency['frequencyMhz']} ({frequency['serviceType']})"
            for frequency in ofmx["frequencies"]
        )
    )
    lines.append(
        f"- OFMX airspaces ({len(ofmx['airspaces'])}): "
        + ", ".join(
            f"{airspace['name']} [{airspace['lower']} -> {airspace['upper']}]"
            for airspace in ofmx["airspaces"]
        )
    )
    cifp = report["sourceInventory"]["cifp"]
    lines.append(
        f"- CIFP procedures: SID {cifp['procedures']['SID']['nameCount']}, "
        f"STAR {cifp['procedures']['STAR']['nameCount']}, "
        f"APPCH names {cifp['procedures']['APPCH']['nameCount']} "
        f"across {cifp['procedures']['APPCH']['transitionCount']} transitions"
    )
    lines.append(
        f"- CIFP fix identifiers: {cifp['fixResolution']['totalDistinctIdentifiers']} distinct; "
        f"{len(cifp['fixResolution']['presentInCheckedInSources'])} found in checked-in sources "
        f"({len(cifp['fixResolution']['presentInOfmxDesignatedPoints'])} designated points, "
        f"{len(cifp['fixResolution']['presentInOfmxNavaids'])} navaids, "
        f"{len(cifp['fixResolution']['presentInChartCodingTables'])} chart coding-table fixes, "
        f"{len(cifp['fixResolution']['presentInDerivedApproachGeometry'])} derived approach fixes); "
        f"{len(cifp['fixResolution']['missingFromCheckedInSources'])} missing from that scan"
    )
    if cifp["fixResolution"]["missingFromCheckedInSources"]:
        lines.append(
            "- CIFP identifiers not found in parsed OFMX sources or chart coding tables: "
            + ", ".join(cifp["fixResolution"]["missingFromCheckedInSources"][:20])
        )
        lines.append(f"  - {cifp['fixResolution']['note']}")
    lines.append("")
    lines.append("## Runway cross-check")
    for entry in report["sourceInventory"]["runwayCrossCheck"]:
        lines.append(
            f"- {entry['designator']}: apt/ofmx={format_distance(entry['aptToOfmxMeters'])}, "
            f"apt/cifp={format_distance(entry['aptToCifpMeters'])}, "
            f"ofmx/cifp={format_distance(entry['ofmxToCifpMeters'])}"
        )
    lines.append("")
    lines.append("## Ground DXF")
    ground = report["drawings"]["ground"]
    lines.append(
        f"- {ground['path']}: {ground['lineCount']} lines, {ground['pointCount']} points, "
        f"{ground['componentCount']} components"
    )
    lines.append(
        f"- Geometry layers: "
        + ", ".join(
            f"{entity}={layer_counts}"
            for entity, layer_counts in sorted(ground["entityLayers"].items())
            if entity in {"LINE", "POINT"}
        )
    )
    lines.append(
        f"- Transform: scale={ground['transform']['scale']:.4f}, "
        f"rotation={ground['transform']['rotationDegrees']:.2f}deg"
    )
    lines.append(
        f"- Runway offset check: best perpendicular shift={ground['runwayOffsetCheck']['bestPerpendicularShiftMeters']}m "
        f"with mean midpoint edge distance={ground['runwayOffsetCheck']['bestMeanMidpointEdgeMeters']:.1f}m"
    )
    lines.append("- Marker assessment:")
    for marker in ground["markers"]:
        label_suffix = "" if marker["label"] is None else f" label={marker['label']}"
        reference_suffix = (
            ""
            if marker["classification"] == marker["referenceClassification"]
            else f" reference={marker['referenceClassification']}"
        )
        note_suffix = "" if marker["note"] is None else f" note={marker['note']}"
        lines.append(
            f"  - marker {marker['index']} at ({marker['rawPoint']['x']:.1f},{marker['rawPoint']['y']:.1f}) "
            f"edge={marker['edgeDistanceMeters']:.1f}m node={marker['nodeDistanceMeters']:.1f}m "
            f"class={marker['classification']}{reference_suffix}{label_suffix}{note_suffix} "
            f"nearestEdge={marker['nearestEdge']['name']}"
        )
    lines.append("- Component assessment:")
    for component in ground["components"]:
        label_suffix = "" if component["label"] is None else f" label={component['label']}"
        reference_suffix = (
            ""
            if component["classification"] == component["referenceClassification"]
            else f" reference={component['referenceClassification']}"
        )
        note_suffix = "" if component["note"] is None else f" note={component['note']}"
        lines.append(
            f"  - component {component['index']}: {component['lineCount']} lines "
            f"meanEdge={component['meanEndpointEdgeMeters']:.1f}m "
            f"maxEdge={component['maxEndpointEdgeMeters']:.1f}m "
            f"class={component['classification']}{reference_suffix}{label_suffix}{note_suffix}"
        )
    lines.append("")
    lines.append("## VFR circuit DXF")
    circuit = report["drawings"]["vfrCircuit"]
    lines.append(
        f"- {circuit['path']}: {circuit['lineCount']} lines, {circuit['pointCount']} points, "
        f"{circuit['componentCount']} components"
    )
    lines.append(
        f"- Geometry layers: "
        + ", ".join(
            f"{entity}={layer_counts}"
            for entity, layer_counts in sorted(circuit["entityLayers"].items())
            if entity in {"LINE", "POINT"}
        )
    )
    lines.append(
        f"- Transform: scale={circuit['transform']['scale']:.4f}, "
        f"rotation={circuit['transform']['rotationDegrees']:.2f}deg, "
        f"tower residual={format_distance(circuit['towerCrossCheck']['chosenResidualMeters'])} "
        f"class={circuit['towerCrossCheck']['chosenClassification']}"
    )
    lines.append(
        f"- Anchor selection: {circuit['anchorSelection']['strategy']}"
        + (
            f" ({', '.join(circuit['anchorSelection']['anchorIds'])})"
            if circuit["anchorSelection"]["anchorIds"]
            else ""
        )
    )
    lines.append("- Component graph summary:")
    for component in circuit["components"]:
        graph = component["graph"]
        lines.append(
            f"  - component {component['index']}: {component['lineCount']} lines "
            f"vertices={graph['vertices']} degree1={graph['degree1']} "
            f"branch={graph['branchVertices']} cycleRank={graph['cycleRank']}"
        )
    lines.append("- Endpoint-on-segment attachments:")
    if circuit["attachments"]:
        for attachment in circuit["attachments"]:
            lines.append(
                f"  - component {attachment['sourceComponent']} joins component {attachment['targetComponent']} "
                f"at ({attachment['endpoint']['x']:.1f},{attachment['endpoint']['y']:.1f}) "
                f"distance={attachment['distanceDrawingUnits']:.4f}"
            )
    else:
        lines.append("  - none detected")
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    report = build_report(args.manifest.resolve())
    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print(render_markdown(report))


if __name__ == "__main__":
    main()
