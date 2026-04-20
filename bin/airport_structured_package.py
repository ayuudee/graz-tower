#!/usr/bin/env python3

from __future__ import annotations

import json
import math
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report
import render_airport_authoring as authoring


ROUNDING_PLACES = 6
GROUND_WIDTH_METERS = 18.0
SKY_WIDTH_METERS = 160.0
SNAP_TOLERANCE_METERS = 8.0
PARKING_GRAPH_SPLIT_TOLERANCE_METERS = 0.5


def manifest_named_mapping(manifest: dict[str, Any], key: str) -> list[dict[str, Any]]:
    return manifest.get("namedMappings", {}).get(key, [])


def working_dxf_config(manifest: dict[str, Any]) -> dict[str, Any] | None:
    working_dxf = manifest.get("workingDxf")
    return working_dxf if isinstance(working_dxf, dict) else None


def slugify(text: str) -> str:
    return "".join(character.lower() if character.isalnum() else "_" for character in text).strip("_")


def rounded_key(point: report.XY) -> tuple[float, float]:
    return (round(point.x, ROUNDING_PLACES), round(point.y, ROUNDING_PLACES))


def distance(point_a: report.XY, point_b: report.XY) -> float:
    return math.hypot(point_a.x - point_b.x, point_a.y - point_b.y)


class PointRegistry:
    def __init__(self) -> None:
        self._points: dict[str, dict[str, Any]] = {}
        self._point_key_to_id: dict[tuple[float, float], str] = {}

    def register(
        self,
        point: report.XY,
        preferred_id: str,
        *,
        label: str | None = None,
        tags: list[str] | None = None,
        sources: list[str] | None = None,
        reuse_existing: bool = True,
    ) -> str:
        key = rounded_key(point)
        existing_id = self._point_key_to_id.get(key) if reuse_existing else None
        if existing_id is not None:
            entry = self._points[existing_id]
            entry["tags"] = sorted(set(entry.get("tags", [])) | set(tags or []))
            entry["sources"] = sorted(set(entry.get("sources", [])) | set(sources or []))
            if entry.get("label") is None and label is not None:
                entry["label"] = label
            return existing_id

        point_id = preferred_id
        suffix = 2
        while point_id in self._points:
            point_id = f"{preferred_id}_{suffix}"
            suffix += 1

        self._point_key_to_id.setdefault(key, point_id)
        self._points[point_id] = {
            "id": point_id,
            "xMeters": round(point.x, 6),
            "yMeters": round(point.y, 6),
            "label": label,
            "tags": sorted(set(tags or [])),
            "sources": sorted(set(sources or [])),
        }
        return point_id

    def lookup_id(self, point: report.XY) -> str | None:
        return self._point_key_to_id.get(rounded_key(point))

    def point_lookup(self) -> dict[str, report.XY]:
        return {
            point_id: report.XY(float(entry["xMeters"]), float(entry["yMeters"]))
            for point_id, entry in self._points.items()
        }

    def as_json(self) -> dict[str, dict[str, Any]]:
        return dict(sorted(self._points.items()))


def ordered_points_from_lines(lines: list[report.DxfLine]) -> tuple[list[report.XY], bool]:
    if not lines:
        return [], False
    point_lookup: dict[tuple[float, float], report.XY] = {}
    adjacency: dict[tuple[float, float], list[tuple[int, tuple[float, float]]]] = defaultdict(list)

    for index, line in enumerate(lines):
        start_key = rounded_key(line.start)
        end_key = rounded_key(line.end)
        point_lookup[start_key] = line.start
        point_lookup[end_key] = line.end
        adjacency[start_key].append((index, end_key))
        adjacency[end_key].append((index, start_key))

    closed = all(len(neighbours) == 2 for neighbours in adjacency.values())
    start_key = next((candidate for candidate, neighbours in adjacency.items() if len(neighbours) == 1), rounded_key(lines[0].start))
    ordered = [point_lookup[start_key]]
    current_key = start_key
    used_lines: set[int] = set()

    while True:
        candidates = [candidate for candidate in adjacency[current_key] if candidate[0] not in used_lines]
        if not candidates:
            break
        line_index, next_key = candidates[0]
        used_lines.add(line_index)
        ordered.append(point_lookup[next_key])
        current_key = next_key
        if closed and current_key == start_key:
            break

    if len(used_lines) != len(lines):
        unique_points: list[report.XY] = []
        seen: set[tuple[float, float]] = set()
        for line in lines:
            for point in (line.start, line.end):
                point_key = rounded_key(point)
                if point_key in seen:
                    continue
                seen.add(point_key)
                unique_points.append(point)
        return unique_points, False

    return ordered, closed


def nearest_point_on_polyline(
    point: report.XY,
    polyline_points: list[report.XY],
) -> tuple[float, report.XY, int, float]:
    best_distance: float | None = None
    best_projected: report.XY | None = None
    best_segment_index = 0
    best_segment_position = 0.0

    for index in range(len(polyline_points) - 1):
        start = polyline_points[index]
        end = polyline_points[index + 1]
        segment_distance, projected = authoring.nearest_point_on_segment(point, start, end)
        dx = end.x - start.x
        dy = end.y - start.y
        if dx == 0.0 and dy == 0.0:
            segment_position = 0.0
        else:
            segment_position = (((projected.x - start.x) * dx) + ((projected.y - start.y) * dy)) / ((dx * dx) + (dy * dy))
        if best_distance is None or segment_distance < best_distance:
            best_distance = segment_distance
            best_projected = projected
            best_segment_index = index
            best_segment_position = max(0.0, min(1.0, segment_position))

    if best_distance is None or best_projected is None:
        return float("inf"), point, 0, 0.0
    return best_distance, best_projected, best_segment_index, best_segment_position


def insert_points_into_polyline(
    polyline_points: list[report.XY],
    insertions: list[report.XY],
    tolerance_m: float,
) -> tuple[list[report.XY], list[report.XY]]:
    if len(polyline_points) < 2 or not insertions:
        return polyline_points, []

    by_segment: dict[int, list[tuple[float, report.XY]]] = defaultdict(list)
    unplaced: list[report.XY] = []

    for insertion in insertions:
        best_distance, _, segment_index, segment_position = nearest_point_on_polyline(insertion, polyline_points)
        if best_distance > tolerance_m:
            unplaced.append(insertion)
            continue
        by_segment[segment_index].append((segment_position, insertion))

    ordered: list[report.XY] = [polyline_points[0]]
    for index in range(len(polyline_points) - 1):
        segment_insertions = sorted(by_segment.get(index, []), key=lambda item: item[0])
        for _, insertion in segment_insertions:
            if distance(ordered[-1], insertion) > 1e-6:
                ordered.append(insertion)
        if distance(ordered[-1], polyline_points[index + 1]) > 1e-6:
            ordered.append(polyline_points[index + 1])

    return ordered, unplaced


def polyline_length(points: list[report.XY]) -> float:
    return sum(points[index].distance_to(points[index + 1]) for index in range(len(points) - 1))


def interpolate_along_polyline(points: list[report.XY], distance_m: float) -> report.XY:
    if not points:
        raise ValueError("Cannot interpolate an empty polyline")
    if len(points) == 1:
        return points[0]
    remaining = distance_m
    for start, end in zip(points, points[1:]):
        segment_length = start.distance_to(end)
        if segment_length <= 1e-9:
            continue
        if remaining <= segment_length:
            ratio = remaining / segment_length
            return report.XY(
                start.x + (end.x - start.x) * ratio,
                start.y + (end.y - start.y) * ratio,
            )
        remaining -= segment_length
    return points[-1]


def ensure_minimum_polyline_points(points: list[report.XY], minimum_points: int) -> list[report.XY]:
    if len(points) >= minimum_points:
        return points
    if len(points) < 2:
        raise ValueError("Cannot expand a polyline with fewer than two points")
    total_length = polyline_length(points)
    if total_length <= 1e-9:
        raise ValueError("Cannot expand a zero-length polyline")
    step = total_length / float(minimum_points - 1)
    return [
        interpolate_along_polyline(points, step * index)
        for index in range(minimum_points)
    ]


def add_path(
    paths: dict[str, dict[str, Any]],
    registry: PointRegistry,
    path_id: str,
    points: list[report.XY],
    *,
    surface: str,
    width_m: float,
    source: str,
    projection_status: str,
    point_id_prefix: str,
    point_label_prefix: str | None = None,
    note: str | None = None,
    preferred_ids: dict[int, str] | None = None,
    preferred_labels: dict[int, str] | None = None,
) -> dict[str, Any] | None:
    if len(points) < 2:
        return None

    point_ids: list[str] = []
    for index, point in enumerate(points, start=1):
        preferred_id = (preferred_ids or {}).get(index) or f"{point_id_prefix}_{index:02d}"
        label = (preferred_labels or {}).get(index)
        if label is None and point_label_prefix is not None:
            label = f"{point_label_prefix} {index}"
        point_ids.append(
            registry.register(
                point,
                preferred_id,
                label=label,
                tags=[surface.lower(), "path_point"],
                sources=[source],
            )
        )

    paths[path_id] = {
        "id": path_id,
        "pointIds": point_ids,
        "surface": surface,
        "widthMeters": round(width_m, 2),
        "lengthMeters": round(polyline_length(points), 2),
        "source": source,
        "projectionStatus": projection_status,
        "note": note,
    }
    return paths[path_id]


def frequencies_from_ofmx(ofmx_frequencies: list[report.OfmxFrequency]) -> list[dict[str, Any]]:
    items = []
    for frequency in sorted(ofmx_frequencies, key=lambda item: ((item.call_sign or ""), item.frequency_mhz)):
        items.append(
            {
                "callSign": frequency.call_sign,
                "frequencyMhz": frequency.frequency_mhz,
                "codeType": frequency.code_type,
                "language": frequency.language,
            }
        )
    return items


def sector_shapes(scene: authoring.SceneContext) -> list[dict[str, Any]]:
    if not scene.working_airspace_sector_lines:
        return []
    components = report.connected_components(scene.working_airspace_sector_lines)
    items: list[dict[str, Any]] = []
    for component in components:
        ordered, closed = ordered_points_from_lines(component)
        if not ordered:
            continue
        centroid = authoring.centroid(ordered[:-1] if closed and len(ordered) > 1 else ordered)
        if scene.tower_xy is None or centroid.x < scene.tower_xy.x:
            label = "SECTOR WHISKEY"
        else:
            label = "SECTOR ECHO"
        items.append(
            {
                "label": label,
                "points": [{"xMeters": round(point.x, 6), "yMeters": round(point.y, 6)} for point in ordered],
                "closed": closed,
                "projectionStatus": "working_geometry_only",
            }
        )
    return sorted(items, key=lambda item: item["label"])


def structured_operational_sectors(
    manifest: dict[str, Any],
    scene: authoring.SceneContext,
    registry: PointRegistry,
    fixes: dict[str, dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    shapes_by_label = {
        str(item["label"]): item
        for item in sector_shapes(scene)
        if isinstance(item, dict) and isinstance(item.get("label"), str)
    }

    sectors: dict[str, dict[str, Any]] = {}
    for item in manifest_named_mapping(manifest, "operationalSectors"):
        if not isinstance(item, dict):
            continue
        sector_id = item.get("sectorId")
        label = item.get("label")
        if not isinstance(sector_id, str) or not isinstance(label, str):
            continue

        shape = shapes_by_label.get(label)
        boundary_point_ids: list[list[str]] = []
        if isinstance(shape, dict):
            points = shape.get("points", [])
            if isinstance(points, list) and points:
                boundary_point_ids.append(
                    [
                        registry.register(
                            report.XY(float(point["xMeters"]), float(point["yMeters"])),
                            f"{sector_id.upper()}_BOUNDARY_{point_index:02d}",
                            tags=["operational_sector_boundary"],
                            sources=["working_airspace_dxf"],
                        )
                        for point_index, point in enumerate(points, start=1)
                        if isinstance(point, dict) and "xMeters" in point and "yMeters" in point
                    ]
                )

        anchor_point_ref = item.get("anchorPointRef")
        anchor_point_id = (
            fixes.get(anchor_point_ref, {}).get("pointId")
            if isinstance(anchor_point_ref, str)
            else None
        )
        entry_exit_point_ids = [
            fixes[point_ref]["pointId"]
            for point_ref in item.get("entryExitPointRefs", [])
            if isinstance(point_ref, str) and point_ref in fixes
        ]
        sectors[sector_id] = {
            "id": sector_id,
            "name": label,
            "label": label,
            "kind": item.get("kind"),
            "anchorPointRef": anchor_point_ref,
            "anchorPointId": anchor_point_id,
            "anchorRole": item.get("anchorRole"),
            "entryExitPointRefs": [
                point_ref
                for point_ref in item.get("entryExitPointRefs", [])
                if isinstance(point_ref, str)
            ],
            "entryExitPointIds": entry_exit_point_ids,
            "associatedProcedureIds": [
                procedure_id
                for procedure_id in item.get("associatedProcedureIds", [])
                if isinstance(procedure_id, str)
            ],
            "contactRequirement": item.get("contactRequirement"),
            "altitudeLimits": item.get("altitudeLimits"),
            "relationToCtr": item.get("relationToCtr"),
            "boundaryPointIds": boundary_point_ids,
            "closed": bool(shape.get("closed")) if isinstance(shape, dict) else False,
            "projectionStatus": (
                "candidate_operational_sector_with_working_geometry"
                if boundary_point_ids
                else "candidate_operational_sector_missing_working_geometry"
            ),
            "note": item.get("behaviourNote"),
            "specialProcedureNote": item.get("specialProcedureNote"),
        }
    return sectors


def published_procedure_route_ids(procedure_id: str) -> list[str]:
    return {
        "prc_1_arrival_graz_nord": ["vfr_western_corridor_path"],
        "prc_1_departure_graz_nord": ["vfr_western_corridor_path"],
        "prc_2_arrival_sender_dobl": ["vfr_southwest_entry_path"],
        "prc_2_departure_sender_dobl": ["vfr_southwest_entry_path"],
        "prc_3_arrival_kalsdorf": ["vfr_southeast_entry_path"],
        "prc_3_departure_kalsdorf": ["vfr_southeast_entry_path"],
        "arr_1_gleisdorf_arrival_only": ["vfr_northeast_entry_path"],
    }.get(procedure_id, [])


def published_procedure_sector_ids(procedure_id: str) -> list[str]:
    return {
        "prc_2_arrival_sender_dobl": ["sector_whiskey"],
        "prc_2_departure_sender_dobl": ["sector_whiskey"],
        "prc_3_arrival_kalsdorf": ["sector_echo"],
        "prc_3_departure_kalsdorf": ["sector_echo"],
    }.get(procedure_id, [])


def published_procedure_circuit_graph_ids(procedure_id: str) -> list[str]:
    return {
        "prc_4_west_traffic_circuit": ["main_shared_graph", "west_side_component"],
        "prc_5_east_hold": ["main_shared_graph", "east_side_component"],
    }.get(procedure_id, [])


def resolve_point_ref(
    point_ref: str,
    fixes: dict[str, dict[str, Any]],
    named_points: dict[str, dict[str, Any]],
    operational_sectors: dict[str, dict[str, Any]],
) -> tuple[str | None, str]:
    if point_ref in fixes:
        return fixes[point_ref].get("pointId"), "fix"
    if point_ref in named_points:
        return named_points[point_ref].get("pointId"), "named_point"
    for sector in operational_sectors.values():
        if not isinstance(sector, dict):
            continue
        if point_ref in {
            sector.get("id"),
            sector.get("name"),
            sector.get("label"),
        }:
            return sector.get("anchorPointId"), "operational_sector_anchor"
    return None, "unresolved"


def structured_published_vfr_procedures(
    manifest: dict[str, Any],
    fixes: dict[str, dict[str, Any]],
    named_points: dict[str, dict[str, Any]],
    operational_sectors: dict[str, dict[str, Any]],
    vfr_routes: dict[str, dict[str, Any]],
    circuit_graphs: dict[str, dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    procedures: dict[str, dict[str, Any]] = {}
    for item in manifest_named_mapping(manifest, "publishedVfrProcedures"):
        if not isinstance(item, dict):
            continue
        procedure_id = item.get("procedureId")
        if not isinstance(procedure_id, str):
            continue

        associated_route_ids = [
            route_id
            for route_id in published_procedure_route_ids(procedure_id)
            if route_id in vfr_routes
        ]
        associated_sector_ids = [
            sector_id
            for sector_id in published_procedure_sector_ids(procedure_id)
            if sector_id in operational_sectors
        ]
        associated_circuit_graph_ids = [
            graph_id
            for graph_id in published_procedure_circuit_graph_ids(procedure_id)
            if graph_id in circuit_graphs
        ]

        resolved_sequence: list[dict[str, Any]] = []
        for point_ref in item.get("publishedSequence", []):
            if not isinstance(point_ref, str):
                continue
            point_id, resolution_type = resolve_point_ref(
                point_ref,
                fixes,
                named_points,
                operational_sectors,
            )
            resolved_sequence.append(
                {
                    "ref": point_ref,
                    "pointId": point_id,
                    "resolutionType": resolution_type,
                }
            )

        map_waypoint_labels: list[dict[str, Any]] = []
        for label in item.get("mapWaypointLabels", []):
            if not isinstance(label, dict):
                continue
            point_ref = label.get("pointRef")
            point_id, resolution_type = (
                resolve_point_ref(point_ref, fixes, named_points, operational_sectors)
                if isinstance(point_ref, str)
                else (None, "unresolved")
            )
            map_waypoint_labels.append(
                {
                    "pointRef": point_ref,
                    "pointId": point_id,
                    "label": label.get("label"),
                    "resolutionType": resolution_type,
                }
            )

        terminates_at = item.get("terminatesAt")
        terminates_at_point_id, _ = (
            resolve_point_ref(terminates_at, fixes, named_points, operational_sectors)
            if isinstance(terminates_at, str)
            else (None, "unresolved")
        )
        hold_at = item.get("holdAt")
        hold_at_point_id, _ = (
            resolve_point_ref(hold_at, fixes, named_points, operational_sectors)
            if isinstance(hold_at, str)
            else (None, "unresolved")
        )

        procedures[procedure_id] = {
            "id": procedure_id,
            "plateId": item.get("plateId"),
            "status": item.get("status"),
            "kind": item.get("kind"),
            "publishedSequence": [
                value
                for value in item.get("publishedSequence", [])
                if isinstance(value, str)
            ],
            "resolvedPublishedSequence": resolved_sequence,
            "associatedVfrRouteIds": associated_route_ids,
            "associatedOperationalSectorIds": associated_sector_ids,
            "associatedCircuitGraphIds": associated_circuit_graph_ids,
            "contactRequirement": item.get("contactRequirement"),
            "contactNote": item.get("contactNote"),
            "altitudeConstraintNote": item.get("altitudeConstraintNote"),
            "routeNote": item.get("routeNote"),
            "reportingNote": item.get("reportingNote"),
            "availabilityNote": item.get("availabilityNote"),
            "specialProcedureNote": item.get("specialProcedureNote"),
            "mapWaypointLabels": map_waypoint_labels,
            "terminatesAt": terminates_at,
            "terminatesAtPointId": terminates_at_point_id,
            "holdAt": hold_at,
            "holdAtPointId": hold_at_point_id,
            "commFailureExitSequence": [
                value
                for value in item.get("commFailureExitSequence", [])
                if isinstance(value, str)
            ],
            "commFailureNote": item.get("commFailureNote"),
            "departureRunwaySet": [
                value
                for value in item.get("departureRunwaySet", [])
                if isinstance(value, str)
            ],
            "applicableRunwaySet": [
                value
                for value in item.get("applicableRunwaySet", [])
                if isinstance(value, str)
            ],
            "projectionStatus": "candidate_structured_published_vfr_procedure",
            "note": item.get("note"),
        }
    return procedures


def add_core_path_from_point_ids(
    geometry_paths: dict[str, dict[str, Any]],
    registry: PointRegistry,
    path_id: str,
    point_ids: list[str],
    *,
    surface: str,
    width_m: float,
    source: str,
    projection_status: str,
    note: str | None = None,
) -> dict[str, Any] | None:
    point_lookup = registry.point_lookup()
    points = [point_lookup[point_id] for point_id in point_ids if point_id in point_lookup]
    if len(points) != len(point_ids) or len(point_ids) < 2:
        return None
    geometry_paths[path_id] = {
        "id": path_id,
        "pointIds": point_ids,
        "surface": surface,
        "widthMeters": round(width_m, 2),
        "lengthMeters": round(polyline_length(points), 2),
        "source": source,
        "projectionStatus": projection_status,
        "note": note,
    }
    return geometry_paths[path_id]


def dedupe_consecutive_points(points: list[report.XY], tolerance_m: float = 1e-6) -> list[report.XY]:
    if not points:
        return []
    deduped = [points[0]]
    for point in points[1:]:
        if distance(deduped[-1], point) <= tolerance_m:
            continue
        deduped.append(point)
    return deduped


def orient_polyline(points: list[report.XY], *, north_to_south: bool) -> list[report.XY]:
    if len(points) < 2:
        return list(points)
    first_is_north = points[0].y >= points[-1].y
    ordered = list(points)
    if first_is_north != north_to_south:
        ordered.reverse()
    return ordered


def component_graph(lines: list[report.DxfLine]) -> tuple[dict[tuple[float, float], report.XY], dict[tuple[float, float], set[tuple[float, float]]]]:
    points: dict[tuple[float, float], report.XY] = {}
    adjacency: dict[tuple[float, float], set[tuple[float, float]]] = defaultdict(set)
    for line in lines:
        start_key = rounded_key(line.start)
        end_key = rounded_key(line.end)
        points[start_key] = line.start
        points[end_key] = line.end
        adjacency[start_key].add(end_key)
        adjacency[end_key].add(start_key)
    return points, adjacency


def simple_paths_between_vertices(
    adjacency: dict[tuple[float, float], set[tuple[float, float]]],
    start_key: tuple[float, float],
    end_key: tuple[float, float],
) -> list[list[tuple[float, float]]]:
    ordered_adjacency = {
        key: sorted(neighbours, key=lambda point_key: (point_key[1], point_key[0]))
        for key, neighbours in adjacency.items()
    }
    paths: list[list[tuple[float, float]]] = []

    def dfs(current: tuple[float, float], path: list[tuple[float, float]]) -> None:
        if current == end_key:
            paths.append(path.copy())
            return
        for neighbour in ordered_adjacency.get(current, []):
            if neighbour in path:
                continue
            path.append(neighbour)
            dfs(neighbour, path)
            path.pop()

    dfs(start_key, [start_key])
    return paths


def path_distance_to_targets(points: list[report.XY], targets: list[report.XY]) -> float:
    return sum(
        nearest_point_on_polyline(target, points)[0]
        for target in targets
    )


def mean_x(points: list[report.XY]) -> float:
    return sum(point.x for point in points) / len(points)


def nearest_index(points: list[report.XY], target: report.XY, tolerance_m: float = 1.0) -> int:
    for index, point in enumerate(points):
        if distance(point, target) <= tolerance_m:
            return index
    raise ValueError(f"Point {target} was not found on the projected polyline within {tolerance_m}m")


def point_ids_for_polyline(
    registry: PointRegistry,
    points: list[report.XY],
    *,
    preferred_prefix: str,
    source: str,
    force_new_indexes: set[int] | None = None,
    force_new_keys: set[tuple[float, float]] | None = None,
) -> list[str]:
    point_ids: list[str] = []
    forced_keys = force_new_keys or set()
    forced_indexes = force_new_indexes or set()
    for index, point in enumerate(points, start=1):
        force_new = index in forced_indexes or rounded_key(point) in forced_keys
        existing_id = None if force_new else registry.lookup_id(point)
        point_id = registry.register(
            point,
            existing_id or f"{preferred_prefix}_{index:02d}",
            tags=["sky", "path_point"],
            sources=[source],
            reuse_existing=not force_new,
        )
        if point_ids and point_ids[-1] == point_id:
            continue
        point_ids.append(point_id)
    return point_ids


def split_outer_leg_point_ids(
    point_ids: list[str],
    point_lookup: dict[str, report.XY],
) -> tuple[list[str], list[str], list[str]]:
    if len(point_ids) < 4:
        raise ValueError("Outer circuit path must contain at least four points")
    if len(point_ids) == 4:
        return (
            point_ids[:2],
            point_ids[1:3],
            point_ids[2:],
        )
    segment_lengths = [
        distance(point_lookup[point_ids[index]], point_lookup[point_ids[index + 1]])
        for index in range(len(point_ids) - 1)
    ]
    longest_index = max(range(len(segment_lengths)), key=segment_lengths.__getitem__)
    if longest_index == 0 or longest_index == len(point_ids) - 2:
        segment_count = len(point_ids) - 1
        first_break = max(1, segment_count // 3)
        second_break = max(first_break + 1, (2 * segment_count) // 3)
        return (
            point_ids[: first_break + 1],
            point_ids[first_break : second_break + 1],
            point_ids[second_break:],
        )
    return (
        point_ids[: longest_index + 1],
        point_ids[longest_index : longest_index + 2],
        point_ids[longest_index + 1 :],
    )


def join_type_for_point(
    point_id: str,
    *,
    final_points: list[str],
    base_points: list[str],
    downwind_points: list[str],
    crosswind_points: list[str],
) -> str | None:
    if point_id in final_points:
        return "STRAIGHT_IN"
    if point_id in base_points:
        return "BASE"
    if point_id in downwind_points:
        return "DOWNWIND"
    if point_id in crosswind_points:
        return "CROSSWIND"
    return None


def projected_loop_specs(
    scene: authoring.SceneContext,
    threshold_points: dict[str, report.XY],
) -> list[dict[str, Any]]:
    main_component = scene.circuit_components[0]
    component_points, adjacency = component_graph(main_component)
    branch_keys = [key for key, neighbours in adjacency.items() if len(neighbours) > 2]
    if len(branch_keys) != 2:
        raise ValueError(f"Expected two branch vertices in the main circuit component, found {len(branch_keys)}")

    main_paths = [
        [component_points[key] for key in path]
        for path in simple_paths_between_vertices(adjacency, branch_keys[0], branch_keys[1])
    ]
    if len(main_paths) != 3:
        raise ValueError(f"Expected three simple branch-to-branch paths in the main circuit component, found {len(main_paths)}")

    center_thresholds = [threshold_points["16C"], threshold_points["34C"]]
    central_axis = orient_polyline(
        min(main_paths, key=lambda path: path_distance_to_targets(path, center_thresholds)),
        north_to_south=True,
    )
    outer_candidates = [path for path in main_paths if path != central_axis]
    west_outer = orient_polyline(
        min(outer_candidates, key=mean_x),
        north_to_south=False,
    )
    east_outer = orient_polyline(
        max(outer_candidates, key=mean_x),
        north_to_south=False,
    )

    side_components = [
        (
            index,
            orient_polyline(ordered_points_from_lines(component)[0], north_to_south=True),
        )
        for index, component in enumerate(scene.circuit_components[1:], start=2)
    ]
    west_component_index, west_axis = min(side_components, key=lambda item: mean_x(item[1]))
    east_component_index, east_axis = max(side_components, key=lambda item: mean_x(item[1]))

    attachments_by_component: dict[int, list[report.EndpointAttachment]] = defaultdict(list)
    for attachment in scene.circuit_attachments:
        attachments_by_component[attachment.source_component].append(attachment)

    def complementary_outer_path(
        outer_path: list[report.XY],
        attachments: list[report.EndpointAttachment],
    ) -> list[report.XY]:
        if len(attachments) != 2:
            raise ValueError(f"Expected two attachments for side component, found {len(attachments)}")
        attachment_points = [attachment.endpoint for attachment in attachments]
        inserted_outer_path, unplaced = insert_points_into_polyline(outer_path, attachment_points, SNAP_TOLERANCE_METERS)
        if unplaced:
            raise ValueError(f"Failed to insert attachment points into outer path: {unplaced}")
        inserted_outer_path = dedupe_consecutive_points(inserted_outer_path)
        south_attachment = min(attachment_points, key=lambda point: point.y)
        north_attachment = max(attachment_points, key=lambda point: point.y)
        south_index = nearest_index(inserted_outer_path, south_attachment)
        north_index = nearest_index(inserted_outer_path, north_attachment)
        if south_index > north_index:
            inserted_outer_path = list(reversed(inserted_outer_path))
            south_index = nearest_index(inserted_outer_path, south_attachment)
            north_index = nearest_index(inserted_outer_path, north_attachment)
        return inserted_outer_path[south_index : north_index + 1]

    west_outer_remainder = complementary_outer_path(
        west_outer,
        attachments_by_component[west_component_index],
    )
    east_outer_remainder = complementary_outer_path(
        east_outer,
        attachments_by_component[east_component_index],
    )

    return [
        {
            "loopId": "center_west",
            "axisPoints": central_axis,
            "outerPoints": west_outer,
            "runwayNorth": "16C",
            "runwaySouth": "34C",
            "thresholdNorth": "16C",
            "thresholdSouth": "34C",
            "axisAnchorIds": [],
            "outerAnchorIds": ["circuit_nw_entry", "circuit_sw_entry"],
            "northDirection": "RIGHT_HAND",
            "southDirection": "LEFT_HAND",
            "altitudeFeet": 2500,
        },
        {
            "loopId": "center_east",
            "axisPoints": central_axis,
            "outerPoints": east_outer,
            "runwayNorth": "16C",
            "runwaySouth": "34C",
            "thresholdNorth": "16C",
            "thresholdSouth": "34C",
            "axisAnchorIds": [],
            "outerAnchorIds": ["circuit_ne_entry", "circuit_se_entry"],
            "northDirection": "LEFT_HAND",
            "southDirection": "RIGHT_HAND",
            "altitudeFeet": 2500,
        },
        {
            "loopId": "west_side",
            "axisPoints": west_axis,
            "outerPoints": west_outer_remainder,
            "runwayNorth": "16R",
            "runwaySouth": "34L",
            "thresholdNorth": "16R",
            "thresholdSouth": "34L",
            "axisAnchorIds": ["circuit_nw_entry", "circuit_sw_entry"],
            "outerAnchorIds": [],
            "northDirection": "RIGHT_HAND",
            "southDirection": "LEFT_HAND",
            "altitudeFeet": 2500,
        },
        {
            "loopId": "east_side",
            "axisPoints": east_axis,
            "outerPoints": east_outer_remainder,
            "runwayNorth": "16L",
            "runwaySouth": "34R",
            "thresholdNorth": "16L",
            "thresholdSouth": "34R",
            "axisAnchorIds": ["circuit_ne_entry", "circuit_se_entry"],
            "outerAnchorIds": [],
            "northDirection": "LEFT_HAND",
            "southDirection": "RIGHT_HAND",
            "altitudeFeet": 2500,
        },
    ]


def projected_circuit_procedures(
    scene: authoring.SceneContext,
    registry: PointRegistry,
    geometry_paths: dict[str, dict[str, Any]],
    core_entities: dict[str, Any],
    candidate_entities: dict[str, Any],
    airport_code: str,
) -> dict[str, dict[str, Any]]:
    point_lookup = registry.point_lookup()
    threshold_ids = {
        runway_id: runway["thresholdPointId"]
        for runway_id, runway in core_entities["aerodrome"]["runways"].items()
    }
    threshold_points = {
        runway_id: point_lookup[point_id]
        for runway_id, point_id in threshold_ids.items()
    }
    anchor_points = {
        anchor_id: point_lookup[anchor["pointId"]]
        for anchor_id, anchor in candidate_entities["namedPoints"].items()
        if isinstance(anchor, dict) and isinstance(anchor.get("pointId"), str) and anchor["pointId"] in point_lookup
    }

    circuits: dict[str, dict[str, Any]] = {}
    published_circuit_ids_by_procedure: dict[str, list[str]] = {
        "prc_4_west_traffic_circuit": [],
        "prc_5_east_hold": [],
    }

    for loop_spec in projected_loop_specs(scene, threshold_points):
        axis_points, axis_unplaced = insert_points_into_polyline(
            loop_spec["axisPoints"],
            [
                threshold_points[loop_spec["thresholdNorth"]],
                threshold_points[loop_spec["thresholdSouth"]],
            ],
            SNAP_TOLERANCE_METERS,
        )
        if axis_unplaced:
            raise ValueError(f"Failed to place axis insertions for loop {loop_spec['loopId']}: {axis_unplaced}")
        axis_points, _ = insert_points_into_polyline(
            axis_points,
            [
                anchor_points[anchor_id]
                for anchor_id in loop_spec.get("axisAnchorIds", [])
                if anchor_id in anchor_points
            ],
            SNAP_TOLERANCE_METERS,
        )

        outer_points, _ = insert_points_into_polyline(
            loop_spec["outerPoints"],
            [
                anchor_points[anchor_id]
                for anchor_id in loop_spec.get("outerAnchorIds", [])
                if anchor_id in anchor_points
            ],
            SNAP_TOLERANCE_METERS,
        )

        axis_points = dedupe_consecutive_points(orient_polyline(axis_points, north_to_south=True))
        outer_points = dedupe_consecutive_points(orient_polyline(outer_points, north_to_south=False))
        outer_points = ensure_minimum_polyline_points(outer_points, 4)
        north_threshold_index = nearest_index(axis_points, threshold_points[loop_spec["thresholdNorth"]])
        south_threshold_index = nearest_index(axis_points, threshold_points[loop_spec["thresholdSouth"]])

        axis_point_ids = point_ids_for_polyline(
            registry,
            axis_points,
            preferred_prefix=f"{airport_code}_CIRCUIT_{slugify(loop_spec['loopId']).upper()}_AXIS",
            source="cad_circuit_projection",
            force_new_indexes={north_threshold_index + 1, south_threshold_index + 1},
        )
        outer_point_ids = point_ids_for_polyline(
            registry,
            outer_points,
            preferred_prefix=f"{airport_code}_CIRCUIT_{slugify(loop_spec['loopId']).upper()}_OUTER",
            source="cad_circuit_projection",
        )
        point_lookup = registry.point_lookup()
        north_threshold_point_id = axis_point_ids[north_threshold_index]
        south_threshold_point_id = axis_point_ids[south_threshold_index]

        def build_directional_circuit(
            runway_id: str,
            direction: str,
            threshold_id: str,
            axis_ids: list[str],
            outer_ids: list[str],
        ) -> dict[str, Any]:
            threshold_index = axis_ids.index(threshold_id)
            final_ids = axis_ids[: threshold_index + 1]
            upwind_ids = axis_ids[threshold_index:]
            crosswind_ids, downwind_ids, base_ids = split_outer_leg_point_ids(outer_ids, point_lookup)

            circuit_id = f"{airport_code}_CIRCUIT_{runway_id}_{slugify(loop_spec['loopId']).upper()}"
            leg_order = [
                ("UPWIND", upwind_ids),
                ("CROSSWIND", crosswind_ids),
                ("DOWNWIND", downwind_ids),
                ("BASE", base_ids),
                ("FINAL", final_ids),
            ]
            legs: list[dict[str, Any]] = []
            for leg_name, leg_point_ids in leg_order:
                path_id = f"{circuit_id}_{leg_name}"
                add_core_path_from_point_ids(
                    geometry_paths,
                    registry,
                    path_id,
                    leg_point_ids,
                    surface="SKY",
                    width_m=SKY_WIDTH_METERS,
                    source="cad_circuit_projection",
                    projection_status="direct_runtime_circuit_entity",
                    note=f"Projected LOWG circuit {runway_id} {loop_spec['loopId']} {leg_name.lower()} leg.",
                )
                legs.append(
                    {
                        "name": leg_name,
                        "pathId": path_id,
                    }
                )

            join_procedures = [
                {
                    "type": join_type,
                    "entryPointId": candidate_entities["namedPoints"][anchor_id]["pointId"],
                    "entryPathId": None,
                }
                for anchor_id in [*loop_spec.get("axisAnchorIds", []), *loop_spec.get("outerAnchorIds", [])]
                if anchor_id in candidate_entities["namedPoints"]
                for point_id in [candidate_entities["namedPoints"][anchor_id]["pointId"]]
                for join_type in [
                    join_type_for_point(
                        point_id,
                        final_points=final_ids,
                        base_points=base_ids,
                        downwind_points=downwind_ids,
                        crosswind_points=crosswind_ids,
                    )
                ]
                if isinstance(join_type, str)
            ]

            return {
                "id": circuit_id,
                "runwayId": runway_id,
                "direction": direction,
                "legs": legs,
                "altitudeFeet": loop_spec["altitudeFeet"],
                "reportingPoints": {},
                "joinProcedures": join_procedures,
                "goAroundPathId": legs[0]["pathId"],
                "projectionStatus": "direct_runtime_circuit_entity",
                "sourceLoop": loop_spec["loopId"],
            }

        north_runway_id = loop_spec["runwayNorth"]
        south_runway_id = loop_spec["runwaySouth"]

        north_circuit = build_directional_circuit(
            north_runway_id,
            loop_spec["northDirection"],
            north_threshold_point_id,
            axis_point_ids,
            outer_point_ids,
        )
        south_circuit = build_directional_circuit(
            south_runway_id,
            loop_spec["southDirection"],
            south_threshold_point_id,
            list(reversed(axis_point_ids)),
            list(reversed(outer_point_ids)),
        )

        circuits[north_circuit["id"]] = north_circuit
        circuits[south_circuit["id"]] = south_circuit

        if loop_spec["loopId"] == "west_side":
            published_circuit_ids_by_procedure["prc_4_west_traffic_circuit"].extend(
                [north_circuit["id"], south_circuit["id"]]
            )
        elif loop_spec["loopId"] in {"center_east", "east_side"}:
            published_circuit_ids_by_procedure["prc_5_east_hold"].extend(
                [north_circuit["id"], south_circuit["id"]]
            )

    candidate_entities["publishedCircuitAssociations"] = published_circuit_ids_by_procedure
    return circuits


def airspace_base_code_id(code_id: str | None) -> str | None:
    if not isinstance(code_id, str):
        return None
    return code_id.split("_", 1)[0]


def projected_airspace_volume_type(code_type: str | None) -> str | None:
    return {
        "CTR": "CTR",
        "TMA": "TMA",
    }.get(code_type or "")


def projected_airspace_class(code_type: str | None, code_id: str | None) -> str | None:
    if code_type == "CTR":
        # Current migration scope is LOWG-focused; the worked CTR is Class D per
        # the local AIP/chart material and wiki notes.
        return "D"
    if code_type == "CLASS" and isinstance(code_id, str) and "_" in code_id:
        suffix = code_id.rsplit("_", 1)[1]
        if suffix in {"A", "B", "C", "D", "E", "F", "G"}:
            return suffix
    return None


def structured_airspace_volumes(
    scene: authoring.SceneContext,
    ofmx_data: dict[str, Any],
    registry: PointRegistry,
    airport_code: str,
) -> dict[str, dict[str, Any]]:
    shapes_by_code_id = {
        shape.code_id: shape
        for shape in scene.airspace_shapes
        if shape.code_id is not None and shape.boundaries
    }
    airspaces_by_code_id = {
        airspace.code_id: airspace
        for airspace in ofmx_data["airspaces"]
        if isinstance(airspace.code_id, str)
    }

    volumes: dict[str, dict[str, Any]] = {}
    for airspace in ofmx_data["airspaces"]:
        runtime_id = airspace.code_id or airspace.mid
        base_code_id = airspace_base_code_id(airspace.code_id) or runtime_id
        base_airspace = airspaces_by_code_id.get(base_code_id)
        boundary_shape = shapes_by_code_id.get(airspace.code_id) or shapes_by_code_id.get(base_code_id)
        if boundary_shape is None or not boundary_shape.boundaries:
            continue

        boundary_point_ids: list[list[str]] = []
        for boundary_index, boundary in enumerate(boundary_shape.boundaries, start=1):
            point_ids: list[str] = []
            for point_index, point in enumerate(boundary, start=1):
                point_ids.append(
                    registry.register(
                        point,
                        f"{airport_code}_AIRSPACE_{slugify(runtime_id).upper()}_{boundary_index:02d}_{point_index:02d}",
                        tags=["airspace_boundary"],
                        sources=["ofmx"],
                    )
                )
            boundary_point_ids.append(point_ids)

        volume_type = projected_airspace_volume_type(airspace.code_type) or projected_airspace_volume_type(
            base_airspace.code_type if base_airspace is not None else None
        )
        airspace_class = projected_airspace_class(airspace.code_type, airspace.code_id)
        if airspace.code_type == "CLASS" and boundary_shape.code_id != airspace.code_id:
            note = f"Class-layer airspace volume using the {base_code_id} boundary geometry."
        elif volume_type is not None and airspace_class is not None:
            note = "Runtime-usable airspace volume projected from OFMX boundary geometry."
        else:
            note = "Boundary geometry is available, but this record does not yet map cleanly to a runtime airspace volume."

        volumes[runtime_id] = {
            "id": runtime_id,
            "sourceMid": airspace.mid,
            "codeId": airspace.code_id,
            "baseCodeId": base_code_id,
            "codeType": airspace.code_type,
            "name": airspace.name,
            "label": boundary_shape.label,
            "lowerValue": airspace.lower_value,
            "lowerUnit": airspace.lower_unit,
            "lowerReference": airspace.lower_reference,
            "upperValue": airspace.upper_value,
            "upperUnit": airspace.upper_unit,
            "upperReference": airspace.upper_reference,
            "lowerLimit": report.format_limit(airspace.lower_value, airspace.lower_unit, airspace.lower_reference),
            "upperLimit": report.format_limit(airspace.upper_value, airspace.upper_unit, airspace.upper_reference),
            "volumeType": volume_type,
            "airspaceClass": airspace_class,
            "boundaryPointIds": boundary_point_ids,
            "category": boundary_shape.category,
            "projectionStatus": (
                "candidate_runtime_airspace_volume"
                if volume_type is not None and airspace_class is not None
                else "candidate_boundary_geometry_without_runtime_class"
            ),
            "note": note,
        }

    return dict(sorted(volumes.items()))


def candidate_airspace_boundary_rings(
    candidate_airspace_volumes: dict[str, dict[str, Any]],
    volume_id: str,
    point_lookup: dict[str, report.XY],
) -> list[list[report.XY]]:
    volume = candidate_airspace_volumes.get(volume_id)
    if not isinstance(volume, dict):
        return []
    rings: list[list[report.XY]] = []
    for ring in volume.get("boundaryPointIds", []):
        if not isinstance(ring, list):
            continue
        ring_points = [
            point_lookup[point_id]
            for point_id in ring
            if isinstance(point_id, str) and point_id in point_lookup
        ]
        if len(ring_points) >= 3:
            rings.append(ring_points)
    return rings


def route_boundary_transition_point(
    start: report.XY,
    end: report.XY,
    boundary_rings: list[list[report.XY]],
    *,
    tolerance: float = 1e-6,
) -> report.XY | None:
    intersections: list[report.XY] = []
    for ring in boundary_rings:
        for segment_start, segment_end in zip(ring, ring[1:] + ring[:1]):
            intersection = segment_intersection_point(start, end, segment_start, segment_end, tolerance=tolerance)
            if intersection is None:
                continue
            if distance(intersection, start) <= tolerance or distance(intersection, end) <= tolerance:
                continue
            intersections.append(intersection)

    if not intersections:
        return None
    unique_intersections = unique_xy(intersections)
    return min(unique_intersections, key=lambda point: distance(start, point))


def projected_segmented_route(
    route_id: str,
    point_ids: list[str],
    registry: PointRegistry,
    candidate_airspace_volumes: dict[str, dict[str, Any]],
    airport_code: str,
    leg_specs: list[dict[str, str]],
) -> tuple[list[str], dict[str, Any] | None, str]:
    if len(point_ids) < 2 or len(leg_specs) != len(point_ids) - 1:
        return point_ids, None, "direct_geometry_airspace_profile_pending"

    point_lookup = registry.point_lookup()
    projected_point_ids = [point_ids[0]]
    projected_segments: list[dict[str, str]] = []
    transition_counter = 1

    for leg_index, leg_spec in enumerate(leg_specs):
        start_point_id = point_ids[leg_index]
        end_point_id = point_ids[leg_index + 1]
        kind = leg_spec.get("kind")

        if kind == "IN_VOLUME":
            volume_id = leg_spec.get("airspaceVolumeId")
            if not isinstance(volume_id, str):
                return point_ids, None, "direct_geometry_airspace_profile_pending"
            projected_segments.append(
                {
                    "fromPointId": start_point_id,
                    "toPointId": end_point_id,
                    "airspaceVolumeId": volume_id,
                }
            )
            projected_point_ids.append(end_point_id)
            continue

        if kind != "TRANSITION":
            return point_ids, None, "direct_geometry_airspace_profile_pending"

        from_volume_id = leg_spec.get("fromAirspaceVolumeId")
        to_volume_id = leg_spec.get("toAirspaceVolumeId")
        if not isinstance(from_volume_id, str) or not isinstance(to_volume_id, str):
            return point_ids, None, "direct_geometry_airspace_profile_pending"

        start_point = point_lookup.get(start_point_id)
        end_point = point_lookup.get(end_point_id)
        if start_point is None or end_point is None:
            return point_ids, None, "direct_geometry_airspace_profile_pending"

        transition_point = route_boundary_transition_point(
            start_point,
            end_point,
            candidate_airspace_boundary_rings(candidate_airspace_volumes, to_volume_id, point_lookup)
            or candidate_airspace_boundary_rings(candidate_airspace_volumes, from_volume_id, point_lookup),
        )
        if transition_point is None:
            return point_ids, None, "direct_geometry_airspace_profile_pending"

        transition_point_id = registry.register(
            transition_point,
            f"{airport_code}_ROUTE_{slugify(route_id).upper()}_AIRSPACE_TRANSITION_{transition_counter:02d}",
            tags=["airspace_transition"],
            sources=["projected_airspace_membership"],
            reuse_existing=False,
        )
        transition_counter += 1

        projected_segments.extend(
            [
                {
                    "fromPointId": start_point_id,
                    "toPointId": transition_point_id,
                    "airspaceVolumeId": from_volume_id,
                },
                {
                    "fromPointId": transition_point_id,
                    "toPointId": end_point_id,
                    "airspaceVolumeId": to_volume_id,
                },
            ]
        )
        projected_point_ids.extend([transition_point_id, end_point_id])

    return (
        projected_point_ids,
        {
            "kind": "SEGMENTED",
            "segments": projected_segments,
        },
        "direct",
    )


def lowg_vfr_route_projection(
    route_id: str,
    point_ids: list[str],
    registry: PointRegistry,
    candidate_airspace_volumes: dict[str, dict[str, Any]],
    airport_code: str,
) -> tuple[list[str], dict[str, Any] | None, str]:
    ctr_volume_id = "LO585" if "LO585" in candidate_airspace_volumes else None

    if route_id in {"vfr_southeast_entry_path", "vfr_southwest_entry_path"} and ctr_volume_id is not None:
        return (
            point_ids,
            {
                "kind": "IN_VOLUME",
                "airspaceVolumeId": ctr_volume_id,
            },
            "direct",
        )

    if route_id == "vfr_western_corridor_path" and ctr_volume_id is not None and "LO0EF_E" in candidate_airspace_volumes:
        return projected_segmented_route(
            route_id,
            point_ids,
            registry,
            candidate_airspace_volumes,
            airport_code,
            [
                {
                    "kind": "IN_VOLUME",
                    "airspaceVolumeId": ctr_volume_id,
                },
                {
                    "kind": "IN_VOLUME",
                    "airspaceVolumeId": ctr_volume_id,
                },
                {
                    "kind": "TRANSITION",
                    "fromAirspaceVolumeId": ctr_volume_id,
                    "toAirspaceVolumeId": "LO0EF_E",
                },
            ],
        )

    return point_ids, None, "direct_geometry_airspace_profile_pending"


def published_reference_kind(resolution_type: str | None, point_id: str | None = None) -> str:
    if point_id is None:
        return "LITERAL"
    return {
        "fix": "FIX",
        "named_point": "NAMED_POINT",
        "operational_sector_anchor": "OPERATIONAL_SECTOR_ANCHOR",
    }.get(str(resolution_type), "LITERAL")


def published_point_reference(
    reference: str | None,
    point_id: str | None,
    resolution_type: str | None,
) -> dict[str, Any] | None:
    if reference is None:
        return None
    kind = published_reference_kind(resolution_type, point_id)
    result: dict[str, Any] = {
        "kind": kind,
        "reference": reference,
    }
    if kind != "LITERAL" and point_id is not None:
        result["pointId"] = point_id
    return result


def group_cifp_procedures(
    procedure_refs: list[report.CifpProcedureRef],
    *,
    include_runway_from_transition: bool = False,
    include_runway_from_name: bool = False,
) -> dict[str, dict[str, Any]]:
    grouped: dict[str, dict[str, Any]] = {}
    for procedure in procedure_refs:
        entry = grouped.setdefault(
            procedure.name,
            {
                "name": procedure.name,
                "routeTypes": set(),
                "transitions": set(),
                "runwayIds": set(),
            },
        )
        entry["routeTypes"].add(procedure.route_type)
        entry["transitions"].add(procedure.transition)
        if include_runway_from_transition and procedure.transition.startswith("RW"):
            entry["runwayIds"].add(procedure.transition.removeprefix("RW"))
        if include_runway_from_name and len(procedure.name) > 1:
            candidate = procedure.name[1:]
            if candidate[:2].isdigit():
                entry["runwayIds"].add(candidate)

    return {
        name: {
            "name": name,
            "routeTypes": sorted(entry["routeTypes"]),
            "transitions": sorted(entry["transitions"]),
            "runwayIds": sorted(entry["runwayIds"]),
        }
        for name, entry in sorted(grouped.items())
    }


def cifp_runway_thresholds(cifp_data: dict[str, Any]) -> dict[str, dict[str, float]]:
    return {
        designator: {
            "latitude": round(position.lat, 8),
            "longitude": round(position.lon, 8),
        }
        for designator, position in sorted(cifp_data.get("runways", {}).items())
    }


def cifp_fix_resolution_detail(
    cifp_data: dict[str, Any],
    ofmx_data: dict[str, Any],
    chart_fix_data: dict[str, Any],
) -> dict[str, Any]:
    resolution = report.cifp_fix_resolution(cifp_data, ofmx_data, chart_fix_data)
    resolved_designated_points = {
        identifier: {
            "codeId": point.code_id,
            "name": point.name,
            "codeType": point.code_type,
            "latitude": round(point.position.lat, 8),
            "longitude": round(point.position.lon, 8),
        }
        for identifier, point in sorted(ofmx_data["allDesignatedPoints"].items())
        if identifier in resolution["presentInOfmxDesignatedPoints"]
    }
    resolved_navaids = {
        identifier: {
            "codeId": navaid.code_id,
            "name": navaid.name,
            "kind": navaid.kind,
            "frequency": navaid.frequency,
            "latitude": round(navaid.position.lat, 8),
            "longitude": round(navaid.position.lon, 8),
        }
        for identifier, navaid in sorted(ofmx_data.get("allNavaids", {}).items())
        if identifier in resolution["presentInOfmxNavaids"]
    }
    resolved_chart_points = {
        identifier: {
            **entry,
        }
        for identifier, entry in sorted(chart_fix_data.get("resolvedFixes", {}).items())
        if identifier in resolution["presentInChartCodingTables"]
    }
    references_by_identifier = {
        identifier: sorted(
            {
                f"{fix_ref.section}:{fix_ref.kind}:{fix_ref.section}/{fix_ref.subsection}"
                for fix_ref in fix_refs
            },
        )
        for identifier, fix_refs in sorted(cifp_data["fixRefs"].items())
    }
    return {
        **resolution,
        "resolvedDesignatedPoints": resolved_designated_points,
        "resolvedNavaids": resolved_navaids,
        "resolvedChartPoints": resolved_chart_points,
        "chartSourceFiles": chart_fix_data.get("filesScanned", []),
        "conflictingChartIdentifiers": chart_fix_data.get("conflictingIdentifiers", []),
        "referencesByIdentifier": references_by_identifier,
    }


def ifr_fix_position_lookup(
    fix_resolution: dict[str, Any],
    runway_thresholds: dict[str, dict[str, float]],
) -> dict[str, dict[str, Any]]:
    lookup: dict[str, dict[str, Any]] = {}

    def register(identifier: str, entry: dict[str, Any], source_kind: str) -> None:
        latitude = entry.get("latitude")
        longitude = entry.get("longitude")
        if not isinstance(latitude, (int, float)) or not isinstance(longitude, (int, float)):
            return
        lookup[identifier] = {
            "identifier": identifier,
            "latitude": float(latitude),
            "longitude": float(longitude),
            "sourceKind": source_kind,
            "sourceCharts": entry.get("sourceCharts", []),
            "derivationMethod": entry.get("derivationMethod"),
        }

    for key, source_kind in (
        ("resolvedDesignatedPoints", "ofmx_designated_point"),
        ("resolvedNavaids", "ofmx_navaid"),
        ("resolvedChartPoints", "chart_coding_table"),
        ("resolvedDerivedApproachGeometry", "derived_approach_geometry"),
    ):
        for identifier, entry in sorted(fix_resolution.get(key, {}).items()):
            if isinstance(entry, dict):
                register(identifier, entry, source_kind)

    for designator, entry in sorted(runway_thresholds.items()):
        if not isinstance(entry, dict):
            continue
        register(f"RW{designator}", entry, "cifp_runway_threshold")

    return lookup


def local_position_document(project, latitude: float, longitude: float) -> dict[str, float]:
    xy = project(report.Geo(latitude, longitude))
    return {
        "xMeters": round(xy.x, 6),
        "yMeters": round(xy.y, 6),
    }


def candidate_altitude_constraint(record: report.CifpApproachRecord) -> dict[str, Any] | None:
    if (
        not record.altitude_constraint_type
        and record.altitude_1 is None
        and record.altitude_2 is None
    ):
        return None
    return {
        "rawType": record.altitude_constraint_type or None,
        "altitude1Feet": record.altitude_1,
        "altitude2Feet": record.altitude_2,
    }


def candidate_speed_constraint(record: report.CifpApproachRecord) -> dict[str, Any] | None:
    if not record.speed_constraint_type and record.speed_limit is None:
        return None
    return {
        "rawType": record.speed_constraint_type or None,
        "speedKnots": record.speed_limit,
    }


def candidate_ifr_leg(
    record: report.CifpApproachRecord,
    position_lookup: dict[str, dict[str, Any]],
    project,
    *,
    override_fix_identifier: str | None = None,
    override_position_entry: dict[str, Any] | None = None,
) -> dict[str, Any]:
    identifier = override_fix_identifier if override_fix_identifier is not None else record.fix_identifier or None
    position_entry = (
        override_position_entry
        if override_position_entry is not None
        else position_lookup.get(record.fix_identifier) if record.fix_identifier else None
    )
    position_document = None
    if position_entry is not None:
        position_document = {
            "latitude": round(position_entry["latitude"], 8),
            "longitude": round(position_entry["longitude"], 8),
            "localPosition": local_position_document(project, position_entry["latitude"], position_entry["longitude"]),
            "sourceKind": position_entry["sourceKind"],
        }
        source_charts = position_entry.get("sourceCharts")
        if isinstance(source_charts, list) and source_charts:
            position_document["sourceCharts"] = source_charts
        derivation_method = position_entry.get("derivationMethod")
        if isinstance(derivation_method, str):
            position_document["derivationMethod"] = derivation_method

    return {
        "sequence": record.sequence,
        "fixIdentifier": identifier,
        "waypointDescription": record.waypoint_description or None,
        "turnDirection": record.turn_direction_code,
        "pathTerminator": record.path_terminator,
        "referenceIdentifier": record.reference_identifier,
        "referenceCodeType": record.reference_code_type,
        "referenceSubtype": record.reference_subtype,
        "primaryCourseDegrees": (
            record.primary_course_tenths / 10.0
            if record.primary_course_tenths is not None
            else None
        ),
        "primaryCourseRaw": record.primary_course_raw,
        "primaryDistanceNm": (
            record.primary_distance_tenths / 10.0
            if record.primary_distance_tenths is not None
            else None
        ),
        "primaryDistanceRaw": record.primary_distance_raw,
        "secondaryCourseDegrees": (
            record.secondary_course_tenths / 10.0
            if record.secondary_course_tenths is not None
            else None
        ),
        "secondaryCourseRaw": record.secondary_course_raw,
        "secondaryDistanceNm": (
            record.secondary_distance_tenths / 10.0
            if record.secondary_distance_tenths is not None
            else None
        ),
        "secondaryDistanceRaw": record.secondary_distance_raw,
        "altitudeConstraint": candidate_altitude_constraint(record),
        "speedConstraint": candidate_speed_constraint(record),
        "position": position_document,
    }


def ordered_cifp_leg_sequences(
    cifp_data: dict[str, Any],
    section: str,
) -> dict[tuple[str, str], list[report.CifpProcedureLegRecord]]:
    grouped: dict[tuple[str, str], list[report.CifpProcedureLegRecord]] = defaultdict(list)
    for record in cifp_data.get("procedureLegRecords", {}).get(section, []):
        grouped[(record.procedure_name, record.transition)].append(record)
    return {
        key: sorted(
            records,
            key=lambda record: int(record.sequence),
        )
        for key, records in sorted(grouped.items())
    }


def compiled_ifr_route_waypoints(
    records: list[report.CifpProcedureLegRecord],
    position_lookup: dict[str, dict[str, Any]],
    project,
    *,
    runway_id: str | None = None,
) -> tuple[list[dict[str, Any]], list[str], list[str], list[str]]:
    waypoints: list[dict[str, Any]] = []
    unresolved_fix_identifiers: set[str] = set()
    runtime_projection_blockers: set[str] = set()
    compilation_assumptions: list[str] = []

    threshold_identifier = f"RW{runway_id}" if isinstance(runway_id, str) and runway_id else None
    threshold_position = (
        position_lookup.get(threshold_identifier)
        if isinstance(threshold_identifier, str)
        else None
    )

    for index, record in enumerate(records):
        if record.fix_identifier:
            if record.fix_identifier in position_lookup:
                waypoints.append(candidate_ifr_leg(record, position_lookup, project))
            else:
                unresolved_fix_identifiers.add(record.fix_identifier)
            continue

        if index == 0 and threshold_identifier is not None and threshold_position is not None:
            waypoints.append(
                candidate_ifr_leg(
                    record,
                    position_lookup,
                    project,
                    override_fix_identifier=threshold_identifier,
                    override_position_entry=threshold_position,
                )
            )
            compilation_assumptions.append(
                "Leading fixless departure leg compiled as a threshold-anchored waypoint because the current route model is waypoint-based."
            )
            continue

        runtime_projection_blockers.add("fixless_leg_unrepresentable_in_waypoint_route_model")

    deduped_waypoints: list[dict[str, Any]] = []
    for waypoint in waypoints:
        if deduped_waypoints and deduped_waypoints[-1].get("fixIdentifier") == waypoint.get("fixIdentifier"):
            continue
        deduped_waypoints.append(waypoint)

    return (
        deduped_waypoints,
        sorted(unresolved_fix_identifiers),
        sorted(runtime_projection_blockers),
        compilation_assumptions,
    )


def sid_candidate_routes(
    airport_code: str,
    cifp_data: dict[str, Any],
    fix_resolution: dict[str, Any],
    runway_thresholds: dict[str, dict[str, float]],
    project,
) -> dict[str, Any]:
    position_lookup = ifr_fix_position_lookup(fix_resolution, runway_thresholds)
    by_id: dict[str, Any] = {}

    for (name, transition), records in ordered_cifp_leg_sequences(cifp_data, "SID").items():
        runway_id = transition.removeprefix("RW") if transition.startswith("RW") else None
        waypoints, unresolved_fix_identifiers, runtime_projection_blockers, compilation_assumptions = compiled_ifr_route_waypoints(
            records,
            position_lookup,
            project,
            runway_id=runway_id,
        )
        if runway_id is None:
            runtime_projection_blockers = sorted(runtime_projection_blockers + ["sid_missing_runway_transition"])
        sid_id = f"{airport_code}_SID_{name}_{runway_id or transition.replace('/', '_')}"
        by_id[sid_id] = {
            "id": sid_id,
            "name": name,
            "runwayId": runway_id,
            "sourceTransition": transition,
            "routeType": records[0].route_type if records else None,
            "waypoints": waypoints,
            "transitions": {},
            "unresolvedFixIdentifiers": unresolved_fix_identifiers,
            "runtimeProjectionBlockers": runtime_projection_blockers,
            "compilationAssumptions": compilation_assumptions,
            "projectionStatus": (
                "candidate_ifr_sid_ready_for_runtime_projection"
                if runway_id is not None and not runtime_projection_blockers and not unresolved_fix_identifiers and waypoints
                else "candidate_ifr_sid_with_runtime_blockers"
            ),
        }

    return {
        "status": "candidate_ifr_sid_routes_from_cifp_sequences",
        "note": (
            "LOWG SIDs currently compile from CIFP as single-transition runway-specific waypoint routes. "
            "Fixless initial departure legs are anchored to the runway threshold where necessary."
        ),
        "byId": by_id,
    }


def star_candidate_routes(
    airport_code: str,
    cifp_data: dict[str, Any],
    fix_resolution: dict[str, Any],
    runway_thresholds: dict[str, dict[str, float]],
    project,
) -> dict[str, Any]:
    position_lookup = ifr_fix_position_lookup(fix_resolution, runway_thresholds)
    by_id: dict[str, Any] = {}

    for (name, transition), records in ordered_cifp_leg_sequences(cifp_data, "STAR").items():
        waypoints, unresolved_fix_identifiers, runtime_projection_blockers, compilation_assumptions = compiled_ifr_route_waypoints(
            records,
            position_lookup,
            project,
        )
        star_id = f"{airport_code}_STAR_{name}"
        by_id[star_id] = {
            "id": star_id,
            "name": name,
            "sourceTransition": transition,
            "routeType": records[0].route_type if records else None,
            "waypoints": waypoints,
            "transitions": {},
            "unresolvedFixIdentifiers": unresolved_fix_identifiers,
            "runtimeProjectionBlockers": runtime_projection_blockers,
            "compilationAssumptions": compilation_assumptions,
            "projectionStatus": (
                "candidate_ifr_star_ready_for_runtime_projection"
                if not runtime_projection_blockers and not unresolved_fix_identifiers and waypoints
                else "candidate_ifr_star_with_runtime_blockers"
            ),
        }

    return {
        "status": "candidate_ifr_star_routes_from_cifp_sequences",
        "note": (
            "LOWG STARs currently compile from CIFP as single-transition waypoint routes using resolved published fix positions."
        ),
        "byId": by_id,
    }


def hold_leg_time_minutes(record: report.CifpApproachRecord) -> float | None:
    raw = record.secondary_distance_raw or record.primary_distance_raw
    if not isinstance(raw, str) or not raw.startswith("T"):
        return None
    numeric = raw[1:]
    if not numeric.isdigit():
        return None
    return int(numeric) / 10.0


LOWG_RUNTIME_IFR_MINIMA_POLICY: dict[str, dict[str, Any]] = {
    "D16C": {
        "selectedApproachType": "VOR",
        "minimum": {
            "type": "MINIMUM_DESCENT_ALTITUDE",
            "altitudeFeet": 1780,
            "heightFeet": 660,
        },
        "sourceChart": "LOWG_Approach_VOR16C_04092025.pdf",
        "selectionNote": (
            "Version 1 runtime projection uses the published straight-in VOR/DME minima for VOR RWY 16C."
        ),
    },
    "D34C": {
        "selectedApproachType": "VOR",
        "selectedTransition": "PIB2S",
        "minimum": {
            "type": "MINIMUM_DESCENT_ALTITUDE",
            "altitudeFeet": 1480,
            "heightFeet": 392,
        },
        "sourceChart": "LOWG_Approach_VOR34C_04092025.pdf",
        "selectionNote": (
            "Version 1 runtime projection uses the published straight-in VOR/DME minima for VOR RWY 34C."
        ),
    },
    "R16C": {
        "selectedApproachType": "RNP",
        "minimum": {
            "type": "MINIMUM_DESCENT_ALTITUDE",
            "altitudeFeet": 1510,
            "heightFeet": 400,
        },
        "sourceChart": "LOWG_Approach_RNP RWY16C_04092025.pdf",
        "selectionNote": (
            "Version 1 runtime projection uses the published LNAV straight-in minima as the single "
            "runtime minimum for RNP RWY 16C."
        ),
    },
    "R34C": {
        "selectedApproachType": "RNP",
        "minimum": {
            "type": "MINIMUM_DESCENT_ALTITUDE",
            "altitudeFeet": 1470,
            "heightFeet": 382,
        },
        "sourceChart": "LOWG_Approach_RNP RWY34C_04092025.pdf",
        "selectionNote": (
            "Version 1 runtime projection uses the published LNAV straight-in minima at the standard "
            "2.5 percent missed-approach climb gradient as the single runtime minimum for RNP RWY 34C."
        ),
    },
    "I34C": {
        "selectedApproachType": "ILS",
        "selectedTransition": "XIB2S",
        "minimum": {
            "type": "DECISION_ALTITUDE",
            "altitudeFeet": 1303,
            "heightFeet": 215,
        },
        "sourceChart": "LOWG_Approach_ILS CAT II-III or LOC 34C_04092025.pdf",
        "selectionNote": (
            "Version 1 runtime projection uses ILS CAT I minima only. LOC-DME and CAT II/III remain "
            "publication-only and are not projected into the runtime candidate."
        ),
    },
}


def tower_scope_ifr_holding_patterns(
    default_approaches: dict[str, list[report.CifpApproachRecord]],
    position_lookup: dict[str, dict[str, Any]],
    project,
) -> dict[str, Any]:
    gbg_holds = [
        record
        for records in default_approaches.values()
        for record in records
        if record.fix_identifier == "GBG" and record.path_terminator == "HM"
    ]
    if not gbg_holds:
        return {
            "status": "missing_default_missed_approach_hold_data",
            "byId": {},
        }

    first = gbg_holds[0]
    position_entry = position_lookup.get("GBG")
    position_document = None
    if position_entry is not None:
        position_document = {
            "latitude": round(position_entry["latitude"], 8),
            "longitude": round(position_entry["longitude"], 8),
            "localPosition": local_position_document(project, position_entry["latitude"], position_entry["longitude"]),
            "sourceKind": position_entry["sourceKind"],
        }

    return {
        "status": "candidate_shared_missed_approach_hold_without_runtime_loop",
        "byId": {
            "LOWG_GBG_MISSED_HOLD": {
                "id": "LOWG_GBG_MISSED_HOLD",
                "fixIdentifier": "GBG",
                "position": position_document,
                "turnDirection": (
                    "LEFT"
                    if first.turn_direction_code == "L"
                    else "RIGHT" if first.turn_direction_code == "R" else None
                ),
                "inboundTrackMagneticDegrees": (
                    first.secondary_course_tenths / 10.0
                    if first.secondary_course_tenths is not None
                    else None
                ),
                "legTimeMinutes": hold_leg_time_minutes(first),
                "minimumAltitudeFeet": first.altitude_1,
                "sourceProcedures": sorted(default_approaches.keys()),
                "projectionStatus": "candidate_ifr_hold_without_runtime_loop",
                "runtimeProjectionBlockers": [
                    "holding_pattern_loop_geometry_unavailable",
                ],
            }
        },
    }


def approach_type_candidates(name: str) -> list[str]:
    if name.startswith("I"):
        return ["ILS", "LOC"]
    if name.startswith("D"):
        return ["VOR"]
    if name.startswith("R"):
        return ["RNP"]
    return []


def runtime_ifr_projection_policy(name: str) -> dict[str, Any] | None:
    policy = LOWG_RUNTIME_IFR_MINIMA_POLICY.get(name)
    return dict(policy) if isinstance(policy, dict) else None


def tower_scope_ifr_approaches(
    cifp_data: dict[str, Any],
    fix_resolution: dict[str, Any],
    runway_thresholds: dict[str, dict[str, float]],
    project,
) -> dict[str, Any]:
    default_approaches: dict[str, list[report.CifpApproachRecord]] = defaultdict(list)
    feeder_transitions: dict[str, set[str]] = defaultdict(set)
    transition_approaches: dict[str, dict[str, list[report.CifpApproachRecord]]] = defaultdict(lambda: defaultdict(list))

    for procedure in cifp_data.get("procedureRefs", {}).get("APPCH", []):
        feeder_transitions[procedure.name].add(procedure.transition)

    for record in cifp_data.get("approachRecords", []):
        if record.transition != "(default)":
            transition_approaches[record.procedure_name][record.transition].append(record)
            continue
        if record.route_type not in {"D", "I", "R"}:
            continue
        if not record.procedure_name.startswith(record.route_type):
            continue
        default_approaches[record.procedure_name].append(record)

    position_lookup = ifr_fix_position_lookup(fix_resolution, runway_thresholds)
    holding_patterns = tower_scope_ifr_holding_patterns(default_approaches, position_lookup, project)
    approaches_by_name: dict[str, Any] = {}

    for name, records in sorted(default_approaches.items()):
        ordered_records = sorted(records, key=lambda record: int(record.sequence))
        runway_id = name[1:] if len(name) > 1 else ""
        explicit_runway_threshold = f"RW{runway_id}"
        explicit_runway_indexes = [
            index
            for index, record in enumerate(ordered_records)
            if record.fix_identifier == explicit_runway_threshold
        ]
        split_index = next(
            iter(explicit_runway_indexes[-1:] or []),
            None,
        )
        if split_index is not None:
            split_index += 1
        else:
            split_index = next(
                (
                    index
                    for index, record in enumerate(ordered_records)
                    if record.path_terminator in {"CA", "DF", "HM"} and record.fix_identifier in {"", "GBG"}
                ),
                len(ordered_records),
            )
        final_records = ordered_records[:split_index]
        missed_records = ordered_records[split_index:]
        approach_types = approach_type_candidates(name)

        runtime_projection_blockers = ["minimum_altitude_unavailable"]
        if len(approach_types) > 1:
            runtime_projection_blockers.append("runtime_approach_type_selection_required")
        if not final_records or final_records[-1].fix_identifier != explicit_runway_threshold:
            runtime_projection_blockers.append("final_path_terminator_compilation_required")
        if missed_records:
            runtime_projection_blockers.append("missed_approach_leg_compilation_required")
        if holding_patterns.get("byId"):
            runtime_projection_blockers.append("holding_pattern_loop_geometry_unavailable")

        unresolved_fix_identifiers = sorted(
            {
                record.fix_identifier
                for record in ordered_records
                if record.fix_identifier and record.fix_identifier not in position_lookup
            }
        )
        if unresolved_fix_identifiers:
            runtime_projection_blockers.append("unresolved_fix_positions")

        runtime_policy = runtime_ifr_projection_policy(name)
        if runtime_policy is not None:
            runtime_projection_blockers = [
                blocker
                for blocker in runtime_projection_blockers
                if blocker != "minimum_altitude_unavailable"
            ]
            if (
                runtime_policy.get("selectedApproachType") in approach_types and
                "runtime_approach_type_selection_required" in runtime_projection_blockers
            ):
                runtime_projection_blockers = [
                    blocker
                    for blocker in runtime_projection_blockers
                    if blocker != "runtime_approach_type_selection_required"
                ]

        approaches_by_name[name] = {
            "name": name,
            "runwayId": runway_id,
            "approachTypes": approach_types,
            "defaultRouteType": ordered_records[0].route_type if ordered_records else None,
            "availableTransitions": sorted(
                transition
                for transition in feeder_transitions.get(name, set())
                if transition != "(default)"
            ),
            "transitionLegsByName": {
                transition: [
                    candidate_ifr_leg(record, position_lookup, project)
                    for record in sorted(records, key=lambda record: int(record.sequence))
                ]
                for transition, records in sorted(transition_approaches.get(name, {}).items())
            },
            "finalApproachLegs": [
                candidate_ifr_leg(record, position_lookup, project)
                for record in final_records
            ],
            "missedApproachLegs": [
                candidate_ifr_leg(record, position_lookup, project)
                for record in missed_records
            ],
            "missedApproachHoldCandidateId": (
                "LOWG_GBG_MISSED_HOLD"
                if any(record.fix_identifier == "GBG" for record in missed_records)
                else None
            ),
            "runtimeProjectionPolicy": runtime_policy,
            "runtimeProjectionBlockers": runtime_projection_blockers,
            "unresolvedFixIdentifiers": unresolved_fix_identifiers,
            "projectionStatus": (
                "candidate_tower_scope_ifr_with_selected_runtime_policy"
                if runtime_policy is not None
                else "candidate_tower_scope_ifr_minima_unavailable"
            ),
        }

    return {
        "status": "candidate_tower_scope_ifr_with_runtime_blockers",
        "note": (
            "LOWG default approach and missed-approach sequences are now projected as tower-scope IFR candidates. "
            "Chart-derived minima and a runtime-selection policy are now attached for the first current-core subset "
            "(VOR 16C, VOR 34C, RNP 16C, RNP 34C, ILS 34C), while LOC remains publication-only. Final/missed leg "
            "compilation and GBG hold-loop geometry are still handled later in the runtime candidate projection step."
        ),
        "byName": approaches_by_name,
        "holdingPatternCandidates": holding_patterns,
    }


def build_ifr_inventory(
    cifp_data: dict[str, Any],
    ofmx_data: dict[str, Any],
    chart_fix_data: dict[str, Any],
    project,
) -> dict[str, Any]:
    procedures = cifp_data["procedures"]
    procedure_refs = cifp_data["procedureRefs"]
    runway_thresholds = cifp_runway_thresholds(cifp_data)
    fix_resolution = cifp_fix_resolution_detail(cifp_data, ofmx_data, chart_fix_data)
    airport_code = ofmx_data["airport"].code_id if ofmx_data.get("airport") is not None else "AIRPORT"
    total_procedure_names = sum(
        int(procedures[section].get("nameCount", 0))
        for section in ("SID", "STAR", "APPCH")
    )
    if total_procedure_names == 0:
        empty_by_name = {"summary": procedures["SID"], "byName": {}}
        return {
            "status": "not_available_no_cifp_source",
            "note": (
                f"No CIFP-style procedure source is configured for {airport_code}, so IFR inventory and "
                "runtime IFR projection are not available yet."
            ),
            "sids": {
                "summary": procedures["SID"],
                "byName": {},
            },
            "sidCandidates": {
                "projectionStatus": "not_available_no_cifp_source",
                "byName": {},
            },
            "stars": {
                "summary": procedures["STAR"],
                "byName": {},
            },
            "starCandidates": {
                "projectionStatus": "not_available_no_cifp_source",
                "byName": {},
            },
            "approaches": {
                "summary": procedures["APPCH"],
                "byName": {},
            },
            "towerScopeApproaches": {
                "projectionStatus": "not_available_no_cifp_source",
                "byName": {},
            },
            "runwayThresholds": runway_thresholds,
            "fixResolution": fix_resolution,
        }
    return {
        "status": "inventory_plus_tower_scope_candidates",
        "note": (
            "CIFP procedure content is available and inventoried here, but the current LOWG runtime "
            "projection only includes a first IFR subset after chart-derived minima selection and migration-side "
            "compilation. SIDs and STARs now compile into candidate waypoint-route structures here; the current-core "
            "candidate already imports the full LOWG SID set, while STARs, LOC 34C, and richer published minima "
            "variants remain outside the current-core projection."
        ),
        "sids": {
            "summary": procedures["SID"],
            "byName": group_cifp_procedures(
                procedure_refs["SID"],
                include_runway_from_transition=True,
            ),
        },
        "sidCandidates": sid_candidate_routes(
            airport_code,
            cifp_data,
            fix_resolution,
            runway_thresholds,
            project,
        ),
        "stars": {
            "summary": procedures["STAR"],
            "byName": group_cifp_procedures(procedure_refs["STAR"]),
        },
        "starCandidates": star_candidate_routes(
            airport_code,
            cifp_data,
            fix_resolution,
            runway_thresholds,
            project,
        ),
        "approaches": {
            "summary": procedures["APPCH"],
            "byName": group_cifp_procedures(
                procedure_refs["APPCH"],
                include_runway_from_name=True,
            ),
        },
        "towerScopeApproaches": tower_scope_ifr_approaches(
            cifp_data,
            fix_resolution,
            runway_thresholds,
            project,
        ),
        "runwayThresholds": runway_thresholds,
        "fixResolution": fix_resolution,
    }


def published_map_label(
    label: str | None,
    point_ref: str | None,
    point_id: str | None,
    resolution_type: str | None,
) -> dict[str, Any] | None:
    if not isinstance(label, str):
        return None
    location = published_point_reference(point_ref, point_id, resolution_type)
    if location is None:
        return None
    return {
        "label": label,
        "location": location,
    }


def contact_timing(
    contact_requirement: dict[str, Any] | None,
    fixes: dict[str, Any],
    named_points: dict[str, Any],
    operational_sectors: dict[str, Any],
) -> dict[str, Any] | None:
    if not isinstance(contact_requirement, dict):
        return None
    if bool(contact_requirement.get("beforeEntry")):
        return {"kind": "BEFORE_ENTRY"}
    before_ref = contact_requirement.get("before")
    if isinstance(before_ref, str):
        point_id, _ = resolve_point_ref(
            before_ref,
            fixes,
            named_points,
            operational_sectors,
        )
        if isinstance(point_id, str):
            return {
                "kind": "BEFORE_POINT",
                "pointId": point_id,
                "reference": before_ref,
            }
    at_ref = contact_requirement.get("at")
    if isinstance(at_ref, str):
        point_id, _ = resolve_point_ref(
            at_ref,
            fixes,
            named_points,
            operational_sectors,
        )
        if isinstance(point_id, str):
            return {
                "kind": "AT_POINT",
                "pointId": point_id,
                "reference": at_ref,
            }
    return None


def projected_contact_requirement(
    contact_requirement: dict[str, Any] | None,
    fixes: dict[str, Any],
    named_points: dict[str, Any],
    operational_sectors: dict[str, Any],
) -> dict[str, Any] | None:
    if not isinstance(contact_requirement, dict):
        return None
    timing = contact_timing(contact_requirement, fixes, named_points, operational_sectors)
    if timing is None:
        return None
    return {
        "role": str(contact_requirement.get("role", "")).upper(),
        "timing": timing,
    }


def operational_sector_anchor(
    anchor_point_id: str | None,
    anchor_role: str | None,
) -> dict[str, Any] | None:
    if anchor_point_id is None:
        return None
    kind = {
        "ctr_boundary_reporting_point": "CTR_BOUNDARY_REPORTING_POINT",
        "reporting_point": "REPORTING_POINT",
        "navaid": "NAVAID",
    }.get(str(anchor_role), "REPORTING_POINT")
    return {
        "kind": kind,
        "pointId": anchor_point_id,
    }


def projected_operational_sector_kind(raw_kind: str | None) -> str:
    return {
        "vfr_operational_sector": "VFR_OPERATIONAL",
        "ifr_holding_sector": "IFR_HOLDING_SECTOR",
        "vfr_training_area": "VFR_TRAINING_AREA",
        "glider_sector": "GLIDER_SECTOR",
        "night_vfr_sector": "NIGHT_VFR_SECTOR",
        "helicopter_operational_sector": "HELICOPTER_OPERATIONAL",
    }.get(str(raw_kind), "VFR_OPERATIONAL")


def projected_operational_sector_ctr_relation(raw_relation: str | None) -> str | None:
    return {
        "boundary_or_overlap_operational_sector": "BOUNDARY_OR_OVERLAP",
        "overlap_operational_sector": "OVERLAPS_CTR",
        "within_ctr_operational_sector": "WITHIN_CTR",
        "adjacent_to_ctr_operational_sector": "ADJACENT_TO_CTR",
        "inside_tma_outside_ctr_operational_sector": "INSIDE_TMA_OUTSIDE_CTR",
    }.get(str(raw_relation))


def projected_published_vfr_procedure_kind(raw_kind: str | None) -> str:
    return {
        "arrival_only": "ARRIVAL",
        "departure_only": "DEPARTURE",
        "traffic_circuit": "CIRCUIT_PUBLICATION",
        "circuit_attached_hold": "CIRCUIT_ATTACHED_HOLD",
        "transit": "TRANSIT",
    }.get(str(raw_kind), "CIRCUIT_PUBLICATION")


def published_procedure_advisories(procedure: dict[str, Any]) -> dict[str, Any] | None:
    advisories = {
        "contact": procedure.get("contactNote"),
        "altitude": procedure.get("altitudeConstraintNote"),
        "route": procedure.get("routeNote"),
        "reporting": procedure.get("reportingNote"),
        "availability": procedure.get("availabilityNote"),
        "specialProcedure": procedure.get("specialProcedureNote"),
        "noiseAbatement": procedure.get("noiseAbatementNote"),
        "speedCap": procedure.get("speedCapNote"),
        "squawkConvention": procedure.get("squawkConventionNote"),
        "activationHours": procedure.get("activationHoursNote"),
        "equipmentMinimum": procedure.get("equipmentMinimumNote"),
        "language": procedure.get("languageNote"),
        "general": procedure.get("note"),
    }
    return advisories if any(value is not None for value in advisories.values()) else None


def published_procedure_communication_failure(
    procedure: dict[str, Any],
    fixes: dict[str, Any],
    named_points: dict[str, Any],
    operational_sectors: dict[str, Any],
) -> dict[str, Any] | None:
    after_contact_sequence = [
        published_point_reference(
            value,
            resolve_point_ref(
                value,
                fixes,
                named_points,
                operational_sectors,
            )[0] if isinstance(value, str) else None,
            resolve_point_ref(
                value,
                fixes,
                named_points,
                operational_sectors,
            )[1] if isinstance(value, str) else "literal",
        )
        for value in procedure.get("commFailureExitSequence", [])
        if isinstance(value, str)
    ]
    after_contact_sequence = [value for value in after_contact_sequence if value is not None]
    before_contact = procedure.get("preContactCommFailureNote")
    if before_contact is None and not after_contact_sequence:
        note = procedure.get("commFailureNote")
        if note is None:
            return None
    return {
        "beforeContactEstablished": before_contact,
        "afterContactEstablishedExitSequence": after_contact_sequence,
        "note": procedure.get("commFailureNote"),
    }


def unique_xy(points: list[report.XY]) -> list[report.XY]:
    unique: list[report.XY] = []
    seen: set[tuple[float, float]] = set()
    for point in points:
        key = rounded_key(point)
        if key in seen:
            continue
        seen.add(key)
        unique.append(point)
    return unique


def segment_intersection_point(
    start_a: report.XY,
    end_a: report.XY,
    start_b: report.XY,
    end_b: report.XY,
    *,
    tolerance: float = 1e-9,
) -> report.XY | None:
    def cross(vector_a: tuple[float, float], vector_b: tuple[float, float]) -> float:
        return vector_a[0] * vector_b[1] - vector_a[1] * vector_b[0]

    vector_a = (end_a.x - start_a.x, end_a.y - start_a.y)
    vector_b = (end_b.x - start_b.x, end_b.y - start_b.y)
    delta = (start_b.x - start_a.x, start_b.y - start_a.y)
    denominator = cross(vector_a, vector_b)
    delta_cross_a = cross(delta, vector_a)

    if abs(denominator) <= tolerance and abs(delta_cross_a) <= tolerance:
        return None
    if abs(denominator) <= tolerance:
        return None

    scale_a = cross(delta, vector_b) / denominator
    scale_b = cross(delta, vector_a) / denominator
    if not (-tolerance <= scale_a <= 1.0 + tolerance and -tolerance <= scale_b <= 1.0 + tolerance):
        return None

    return report.XY(
        start_a.x + scale_a * vector_a[0],
        start_a.y + scale_a * vector_a[1],
    )


def nearest_reference_parking(
    references: list[authoring.ProjectedParkingPosition],
    point: report.XY,
) -> tuple[authoring.ProjectedParkingPosition | None, float]:
    if not references:
        return None, float("inf")
    best = min(references, key=lambda reference: reference.point.distance_to(point))
    return best, best.point.distance_to(point)


def parking_location_type(name: str, reference: authoring.ProjectedParkingPosition | None) -> str:
    if reference is not None and reference.location_type:
        return reference.location_type
    return "gate" if name[:1].isdigit() else "misc"


def nearest_runway_pair_name(
    point: report.XY,
    runway_shapes_by_pair: dict[str, authoring.RunwayShape],
) -> str | None:
    best_pair: tuple[float, str] | None = None
    for pair_name, runway_shape in runway_shapes_by_pair.items():
        distance_m, _ = authoring.nearest_point_on_segment(point, runway_shape.start, runway_shape.end)
        if best_pair is None or distance_m < best_pair[0]:
            best_pair = (distance_m, pair_name)
    return best_pair[1] if best_pair is not None else None


def nearest_threshold_runway_id(
    point: report.XY,
    runway_ids: list[str],
    registry: PointRegistry,
    core_entities: dict[str, Any],
) -> str | None:
    point_lookup = registry.point_lookup()
    best_runway: tuple[float, str] | None = None
    for runway_id in runway_ids:
        runway = core_entities["aerodrome"]["runways"].get(runway_id)
        if not isinstance(runway, dict):
            continue
        threshold_point_id = runway.get("thresholdPointId")
        if not isinstance(threshold_point_id, str) or threshold_point_id not in point_lookup:
            continue
        distance_m = distance(point, point_lookup[threshold_point_id])
        if best_runway is None or distance_m < best_runway[0]:
            best_runway = (distance_m, runway_id)
    return best_runway[1] if best_runway is not None else None


def apply_authored_working_ground(
    manifest: dict[str, Any],
    root: Path,
    scene: authoring.SceneContext,
    registry: PointRegistry,
    geometry_paths: dict[str, dict[str, Any]],
    core_entities: dict[str, Any],
    candidate_entities: dict[str, Any],
    airport_code: str,
    working_document: report.DxfDocument | None,
    runway_shapes_by_pair: dict[str, authoring.RunwayShape],
) -> list[str]:
    working_dxf = working_dxf_config(manifest)
    if working_dxf is None or working_document is None:
        return []
    layers = working_dxf.get("layers")
    if not isinstance(layers, dict):
        return []

    ground_layer = layers.get("authoredGroundGraph")
    stand_layer = layers.get("authoredStandPoints")
    holding_layer = layers.get("authoredHoldingPoints")
    manoeuvring_layer = layers.get("authoredManoeuvringAreas")
    if not isinstance(ground_layer, str):
        return []

    ground_lines = working_dxf_lines(working_document, ground_layer)
    stand_points = [point.point for point in working_dxf_points(working_document, stand_layer if isinstance(stand_layer, str) else None)]
    holding_points = [point.point for point in working_dxf_points(working_document, holding_layer if isinstance(holding_layer, str) else None)]
    manoeuvring_lines = working_dxf_lines(working_document, manoeuvring_layer if isinstance(manoeuvring_layer, str) else None)

    if not ground_lines:
        return [f"Working-DXF layer {ground_layer} is configured as authoredGroundGraph but contains no lines."]

    split_polylines = split_authored_working_graph(ground_lines, stand_points + holding_points)

    core_entities["aerodrome"]["taxiways"] = {}
    core_entities["aerodrome"]["stands"] = {}
    core_entities["aerodrome"]["aprons"] = {}
    candidate_entities["holdingPoints"] = {}

    taxiways: dict[str, dict[str, Any]] = {}
    taxiway_segments: list[dict[str, Any]] = []
    taxiway_name_counts: dict[str, int] = defaultdict(int)
    apron_path_ids: list[str] = []
    notes: list[str] = [
        f"Authored ground graph imported from working-DXF layer {ground_layer}.",
    ]

    for line_index, line in enumerate(ground_lines, start=1):
        split_points = dedupe_consecutive_points(split_polylines.get(line_index - 1, [line.start, line.end]))
        if len(split_points) < 2:
            continue
        for segment_index, (segment_start, segment_end) in enumerate(zip(split_points, split_points[1:]), start=1):
            if distance(segment_start, segment_end) <= 1e-6:
                continue
            segment_line = report.DxfLine(layer=ground_layer, start=segment_start, end=segment_end)
            taxiway_name, reference_hint = nearest_reference_taxiway(segment_line, scene.taxi_route_edges)
            display_name = taxiway_name or "AUTH"
            taxiway_name_counts[display_name] += 1
            taxiway_id = f"{airport_code}_TWY_{slugify(display_name).upper()}_{taxiway_name_counts[display_name]:02d}"
            path_id = f"{taxiway_id}_PATH"
            note_parts = [f"Authored ground segment from {ground_layer}."]
            if reference_hint is not None:
                note_parts.append(f"Nearest apt.dat route reference: {reference_hint}.")
            add_path(
                geometry_paths,
                registry,
                path_id,
                [segment_start, segment_end],
                surface="GROUND",
                width_m=GROUND_WIDTH_METERS,
                source="cad_working_dxf",
                projection_status="direct_authored_ground_graph",
                point_id_prefix=taxiway_id,
                point_label_prefix=display_name,
                note=" ".join(note_parts),
            )
            taxiway_record = {
                "id": taxiway_id,
                "name": display_name,
                "pathId": path_id,
                "bidirectional": True,
                "holdingPoints": [],
                "projectionStatus": "direct_authored_ground_graph",
                "note": " ".join(note_parts),
            }
            taxiways[taxiway_id] = taxiway_record
            taxiway_segments.append(
                {
                    "taxiwayId": taxiway_id,
                    "pathId": path_id,
                    "lineIndex": line_index,
                    "segmentIndex": segment_index,
                    "start": segment_start,
                    "end": segment_end,
                    "displayName": display_name,
                }
            )
            apron_path_ids.append(path_id)

    used_stand_names: set[str] = set()
    stand_ids: list[str] = []
    for stand_index, stand_point in enumerate(sorted(stand_points, key=lambda point: (point.y, point.x)), start=1):
        stand_name, reference_parking, reference_distance = authored_stand_name(
            stand_index,
            stand_point,
            scene.parking_positions,
            used_stand_names,
        )
        stand_id = f"{airport_code}_STAND_{slugify(stand_name).upper()}"
        point_id = registry.register(
            stand_point,
            f"{stand_id}_POINT",
            label=stand_name,
            tags=["stand", parking_location_type(stand_name, reference_parking)],
            sources=["cad_working_dxf"],
        )
        core_entities["aerodrome"]["stands"][stand_id] = {
            "id": stand_id,
            "name": stand_name,
            "pointId": point_id,
            "locationType": parking_location_type(stand_name, reference_parking),
            "aircraftTypes": reference_parking.aircraft_types if reference_parking is not None else "",
            "projectionStatus": "direct_authored_ground_graph",
            "note": (
                f"Authored parking point from {stand_layer}; nearest apt.dat reference is {reference_distance:.1f}m away."
                if reference_parking is not None and isinstance(stand_layer, str)
                else (
                    f"Authored parking point from {stand_layer}."
                    if isinstance(stand_layer, str)
                    else "Authored parking point from the working DXF."
                )
            ),
        }
        stand_ids.append(stand_id)

    hold_points_by_pair: dict[str, list[report.XY]] = defaultdict(list)
    for hold_point in holding_points:
        pair_name = nearest_runway_pair_name(hold_point, runway_shapes_by_pair)
        if pair_name is None:
            continue
        hold_points_by_pair[pair_name].append(hold_point)

    for pair_name, pair_hold_points in sorted(hold_points_by_pair.items()):
        pair_runway_ids = pair_name.split("/")
        for hold_index, hold_point in enumerate(sorted(pair_hold_points, key=lambda point: (point.y, point.x)), start=1):
            if len(pair_hold_points) == 1 and len(pair_runway_ids) == 2:
                assigned_runway_ids = pair_runway_ids
                hold_status = "provisional_shared_pair_hold"
                hold_note = (
                    f"Single authored holding point on runway pair {pair_name}; duplicated onto both directions for version 1."
                )
            else:
                assigned_runway_id = nearest_threshold_runway_id(hold_point, pair_runway_ids, registry, core_entities)
                assigned_runway_ids = [assigned_runway_id] if assigned_runway_id is not None else []
                hold_status = "direct_authored_hold_point"
                hold_note = f"Authored holding point from {holding_layer}."

            nearest_segment = min(
                (
                    (
                        authoring.nearest_point_on_segment(hold_point, segment["start"], segment["end"])[0],
                        segment,
                    )
                    for segment in taxiway_segments
                ),
                default=None,
                key=lambda item: item[0],
            )
            attached_taxiway_id = nearest_segment[1]["taxiwayId"] if nearest_segment is not None else None

            if not assigned_runway_ids:
                notes.append(
                    f"Holding point {hold_index} on runway pair {pair_name} could not be assigned to a runway threshold."
                )

            for runway_id in assigned_runway_ids:
                hold_suffix = slugify(runway_id).upper() if isinstance(runway_id, str) else f"PAIR_{hold_index:02d}"
                holding_point_id = f"{airport_code}_HOLD_{hold_suffix}"
                point_id = registry.register(
                    hold_point,
                    f"{holding_point_id}_POINT",
                    label=runway_id if isinstance(runway_id, str) else None,
                    tags=["holding_point", "ground"],
                    sources=["cad_working_dxf"],
                )
                holding_point = {
                    "id": holding_point_id,
                    "pointId": point_id,
                    "name": runway_id if isinstance(runway_id, str) else None,
                    "type": "CAT_A",
                    "runwayId": runway_id,
                    "projectionStatus": hold_status,
                    "note": hold_note,
                }
                candidate_entities["holdingPoints"][holding_point_id] = holding_point
                if attached_taxiway_id is not None and attached_taxiway_id in taxiways:
                    taxiways[attached_taxiway_id]["holdingPoints"].append(holding_point)

    core_entities["aerodrome"]["taxiways"] = dict(sorted(taxiways.items()))

    if apron_path_ids or stand_ids:
        core_entities["aerodrome"]["aprons"][f"{airport_code}_APRON_AUTHORED_GROUND"] = {
            "id": f"{airport_code}_APRON_AUTHORED_GROUND",
            "name": "Authored Ground",
            "pathIds": sorted(apron_path_ids),
            "standIds": sorted(stand_ids),
            "projectionStatus": "direct_authored_ground_graph",
            "note": "Version-1 apron/support geometry projected directly from the authored working-DXF ground graph.",
        }

    if manoeuvring_lines:
        for area_index, component in enumerate(report.connected_components(manoeuvring_lines), start=1):
            component_path_ids: list[str] = []
            for line_index, line in enumerate(component, start=1):
                path_id = f"{airport_code}_MANOEUVRING_{area_index:02d}_{line_index:02d}"
                add_path(
                    geometry_paths,
                    registry,
                    path_id,
                    [line.start, line.end],
                    surface="GROUND",
                    width_m=GROUND_WIDTH_METERS,
                    source="cad_working_dxf",
                    projection_status="candidate_authored_manoeuvring_area_perimeter",
                    point_id_prefix=f"{airport_code}_MANOEUVRING_{area_index:02d}_{line_index:02d}",
                    note=f"Authored manoeuvring-area perimeter from {manoeuvring_layer}.",
                )
                component_path_ids.append(path_id)
            core_entities["aerodrome"]["aprons"][f"{airport_code}_MANOEUVRING_AREA_{area_index:02d}"] = {
                "id": f"{airport_code}_MANOEUVRING_AREA_{area_index:02d}",
                "name": f"Manoeuvring Area {area_index}",
                "pathIds": component_path_ids,
                "standIds": [],
                "projectionStatus": "candidate_authored_manoeuvring_area_perimeter",
                "note": "Authored manoeuvring area perimeter retained as apron-style geometry because the current core model has no dedicated manoeuvring-area primitive.",
            }
        notes.append(
            f"Manoeuvring-area perimeter from {manoeuvring_layer} retained as apron-style candidate geometry."
        )

    notes.append(
        "Taxiway names are currently inferred from the nearest apt.dat route edges and should be confirmed explicitly later."
    )
    return notes


def working_dxf_document(
    manifest: dict[str, Any],
    root: Path,
) -> report.DxfDocument | None:
    working_dxf = working_dxf_config(manifest)
    if working_dxf is None:
        return None
    path_value = working_dxf.get("path")
    if not isinstance(path_value, str):
        return None
    return report.parse_dxf(report.resolve_path(root, path_value))


def working_dxf_lines(
    document: report.DxfDocument | None,
    layer_name: str | None,
) -> list[report.DxfLine]:
    if document is None or not isinstance(layer_name, str):
        return []
    return [line for line in document.lines if line.layer == layer_name]


def working_dxf_points(
    document: report.DxfDocument | None,
    layer_name: str | None,
) -> list[report.DxfPoint]:
    if document is None or not isinstance(layer_name, str):
        return []
    return [point for point in document.points if point.layer == layer_name]


def empty_cifp_data() -> dict[str, Any]:
    return {
        "procedures": {
            "SID": {"nameCount": 0, "transitionCount": 0, "names": [], "transitions": []},
            "STAR": {"nameCount": 0, "transitionCount": 0, "names": [], "transitions": []},
            "APPCH": {"nameCount": 0, "transitionCount": 0, "names": [], "transitions": []},
        },
        "procedureRefs": {
            "SID": [],
            "STAR": [],
            "APPCH": [],
        },
        "procedureLegRecords": {
            "SID": [],
            "STAR": [],
            "APPCH": [],
        },
        "fixRefs": {},
        "runways": {},
        "approachRecords": [],
    }


def authored_parking_rows(
    manifest: dict[str, Any],
    root: Path,
) -> tuple[list[tuple[str, report.XY]], list[report.DxfLine], list[str]]:
    working_dxf = working_dxf_config(manifest)
    if working_dxf is None:
        return [], [], []

    path_value = working_dxf.get("path")
    layers = working_dxf.get("layers")
    naming = working_dxf.get("parkingPointNaming")
    if not isinstance(path_value, str) or not isinstance(layers, dict) or not isinstance(naming, dict):
        return [], [], []

    graph_layer = layers.get("authoredParkingGraph")
    point_layer = layers.get("authoredParkingPoints")
    row_names = naming.get("rows")
    if not isinstance(graph_layer, str) or not isinstance(point_layer, str) or not isinstance(row_names, list):
        return [], [], []

    document = report.parse_dxf(report.resolve_path(root, path_value))
    graph_lines = [line for line in document.lines if line.layer == graph_layer]
    stand_points = [point for point in document.points if point.layer == point_layer]
    if not graph_lines or not stand_points:
        return [], graph_lines, []

    ordered_points = sorted(stand_points, key=lambda item: (item.point.y, item.point.x))
    named_points: list[tuple[str, report.XY]] = []
    diagnostics: list[str] = []
    cursor = 0

    for row_index, row in enumerate(row_names, start=1):
        if not isinstance(row, list):
            diagnostics.append(f"Working-DXF parking row {row_index} is not a list.")
            continue
        row_labels = [str(value) for value in row]
        row_points = ordered_points[cursor : cursor + len(row_labels)]
        if len(row_points) != len(row_labels):
            diagnostics.append(
                f"Working-DXF parking row {row_index} expected {len(row_labels)} point(s) but found {len(row_points)}."
            )
            break
        for label, point in zip(row_labels, sorted(row_points, key=lambda item: (item.point.x, item.point.y))):
            named_points.append((label, point.point))
        cursor += len(row_labels)

    if cursor != len(ordered_points):
        diagnostics.append(
            f"Working-DXF parking naming consumed {cursor} point(s) but the layer contains {len(ordered_points)} point(s)."
        )

    return named_points, graph_lines, diagnostics


def canonical_reference_taxiway_name(
    kind: str,
    name: str,
) -> str | None:
    stripped_name = str(name).strip()
    if stripped_name:
        return stripped_name.upper()
    if kind.startswith("taxiway_"):
        return kind.split("_", 1)[1].upper()
    return None


def nearest_reference_taxiway(
    line: report.DxfLine,
    reference_edges: list[authoring.ProjectedTaxiRouteEdge],
) -> tuple[str | None, str | None]:
    best: tuple[float, float, authoring.ProjectedTaxiRouteEdge] | None = None
    for edge in reference_edges:
        if not edge.kind.startswith("taxiway_"):
            continue
        start_distance, _ = authoring.nearest_point_on_segment(line.start, edge.start, edge.end)
        end_distance, _ = authoring.nearest_point_on_segment(line.end, edge.start, edge.end)
        score = (start_distance + end_distance, max(start_distance, end_distance))
        if best is None or score < (best[0], best[1]):
            best = (score[0], score[1], edge)
    if best is None:
        return None, None

    edge = best[2]
    label = canonical_reference_taxiway_name(edge.kind, edge.name)
    reference_hint = edge.kind if not edge.name else f"{edge.kind}:{edge.name}"
    return label, reference_hint


def unique_name(base_name: str, used_names: set[str]) -> str:
    if base_name not in used_names:
        used_names.add(base_name)
        return base_name
    suffix = 2
    while f"{base_name}_{suffix}" in used_names:
        suffix += 1
    unique = f"{base_name}_{suffix}"
    used_names.add(unique)
    return unique


def authored_stand_name(
    index: int,
    point: report.XY,
    references: list[authoring.ProjectedParkingPosition],
    used_names: set[str],
) -> tuple[str, authoring.ProjectedParkingPosition | None, float]:
    reference_parking, reference_distance = nearest_reference_parking(references, point)
    base_name = (
        str(reference_parking.name)
        if reference_parking is not None and isinstance(reference_parking.name, str) and reference_parking.name
        else f"AUTH_{index:02d}"
    )
    return unique_name(base_name, used_names), reference_parking, reference_distance


def split_authored_working_graph(
    lines: list[report.DxfLine],
    split_points: list[report.XY],
) -> dict[int, list[report.XY]]:
    insertions: dict[int, list[report.XY]] = defaultdict(list)

    for line_index, line in enumerate(lines):
        for point in split_points:
            distance_m, _ = authoring.nearest_point_on_segment(point, line.start, line.end)
            if distance_m <= PARKING_GRAPH_SPLIT_TOLERANCE_METERS:
                insertions[line_index].append(point)
        for other_index, other_line in enumerate(lines):
            if other_index == line_index:
                continue
            for endpoint in (other_line.start, other_line.end):
                distance_m, _ = authoring.nearest_point_on_segment(endpoint, line.start, line.end)
                if distance_m <= PARKING_GRAPH_SPLIT_TOLERANCE_METERS:
                    insertions[line_index].append(endpoint)
        for other_index in range(line_index + 1, len(lines)):
            other_line = lines[other_index]
            intersection = segment_intersection_point(
                line.start,
                line.end,
                other_line.start,
                other_line.end,
            )
            if intersection is None:
                continue
            insertions[line_index].append(intersection)
            insertions[other_index].append(intersection)

    return {
        line_index: insert_points_into_polyline(
            [line.start, line.end],
            unique_xy(points),
            tolerance_m=PARKING_GRAPH_SPLIT_TOLERANCE_METERS,
        )[0]
        for line_index, (line, points) in enumerate(
            [(line, insertions.get(index, [])) for index, line in enumerate(lines)]
        )
    }


def nearest_point_on_lines(
    point: report.XY,
    lines: list[report.DxfLine],
) -> tuple[float, report.XY, int] | None:
    best: tuple[float, report.XY, int] | None = None
    for index, line in enumerate(lines):
        distance_m, projected = authoring.nearest_point_on_segment(point, line.start, line.end)
        if best is None or distance_m < best[0]:
            best = (distance_m, projected, index)
    return best


def build_structured_airport_package(manifest_path: Path) -> dict[str, Any]:
    scene = authoring.build_context(manifest_path)
    manifest = scene.manifest
    root = scene.root
    resolved_manifest_path = manifest_path if manifest_path.is_absolute() else (root / manifest_path)
    airport_code = manifest["airportCode"]

    apt_path = report.resolve_path(root, manifest["sources"]["aptDat"])
    cifp_source = manifest["sources"].get("cifp")
    cifp_path = report.resolve_path(root, cifp_source) if isinstance(cifp_source, str) else None
    ofmx_path = report.resolve_path(root, manifest["sources"]["ofmx"])
    runways, _, _, _, apt_metadata, _ = report.parse_apt(apt_path)
    cifp_data = report.parse_cifp(cifp_path) if cifp_path is not None else empty_cifp_data()
    ofmx_data = report.parse_ofmx(ofmx_path, airport_code)
    charts_directory = report.resolve_path(root, manifest["sources"].get("chartsDirectory"))
    chart_fix_data = report.extract_chart_coding_table_fixes(charts_directory)
    airport = ofmx_data["airport"]
    origin = report.Geo(float(apt_metadata["datum_lat"]), float(apt_metadata["datum_lon"]))
    project = report.projector(origin)
    working_document = working_dxf_document(manifest, root)

    registry = PointRegistry()
    geometry_paths: dict[str, dict[str, Any]] = {}

    core_entities: dict[str, Any] = {
        "geometry": {
            "points": {},
            "paths": geometry_paths,
        },
        "aerodrome": {
            "icao": airport_code,
            "name": manifest.get("airportName") or airport.name,
            "elevationFeet": airport.elevation_ft,
            "magneticVariationDegrees": airport.magnetic_variation,
            "transitionAltitudeFeet": airport.transition_altitude_ft,
            "frequencies": frequencies_from_ofmx(ofmx_data["frequencies"]),
            "aip": {
                "noiseAbatement": [],
                "specialInstructions": [],
                "operationalSectors": {},
                "publishedVfrProcedures": {},
            },
            "runways": {},
            "circuits": {},
            "taxiways": {},
            "stands": {},
            "aprons": {},
        },
        "fixes": {},
        "vfrRoutes": {},
    }
    candidate_entities: dict[str, Any] = {
        "namedPoints": {},
        "holdingPoints": {},
        "vfrRoutes": {},
        "circuitGraphs": {},
        "operationalSectors": {},
        "publishedVfrProcedures": {},
        "airspaceVolumes": {},
        "ifrProcedures": {},
    }

    runway_shapes_by_pair = {shape.pair: shape for shape in scene.apt_runways}
    for pair, runway_shape in sorted(runway_shapes_by_pair.items()):
        designator_a, designator_b = pair.split("/")
        point_id_a = registry.register(
            runway_shape.start,
            f"{airport_code}_RWY_{slugify(designator_a).upper()}_THR",
            label=designator_a,
            tags=["runway_threshold", "runway"],
            sources=["apt.dat"],
        )
        point_id_b = registry.register(
            runway_shape.end,
            f"{airport_code}_RWY_{slugify(designator_b).upper()}_THR",
            label=designator_b,
            tags=["runway_threshold", "runway"],
            sources=["apt.dat"],
        )
        path_id_a = f"{airport_code}_PATH_RWY_{slugify(designator_a).upper()}"
        path_id_b = f"{airport_code}_PATH_RWY_{slugify(designator_b).upper()}"
        add_path(
            geometry_paths,
            registry,
            path_id_a,
            [runway_shape.start, runway_shape.end],
            surface="RUNWAY",
            width_m=runway_shape.width_m,
            source="apt.dat",
            projection_status="direct",
            point_id_prefix=f"{airport_code}_RWY_{slugify(designator_a).upper()}",
            preferred_ids={1: point_id_a, 2: point_id_b},
            preferred_labels={1: designator_a, 2: designator_b},
            note="Directional runway path using the full strip axis.",
        )
        add_path(
            geometry_paths,
            registry,
            path_id_b,
            [runway_shape.end, runway_shape.start],
            surface="RUNWAY",
            width_m=runway_shape.width_m,
            source="apt.dat",
            projection_status="direct",
            point_id_prefix=f"{airport_code}_RWY_{slugify(designator_b).upper()}",
            preferred_ids={1: point_id_b, 2: point_id_a},
            preferred_labels={1: designator_b, 2: designator_a},
            note="Directional runway path using the full strip axis.",
        )
        runway_record = runways[designator_a]
        length_m = round(runway_shape.start.distance_to(runway_shape.end))
        if runway_record.designator_a == designator_a:
            displaced_a = runway_record.displaced_a_m
            displaced_b = runway_record.displaced_b_m
        else:
            displaced_a = runway_record.displaced_b_m
            displaced_b = runway_record.displaced_a_m
        core_entities["aerodrome"]["runways"][designator_a] = {
            "id": designator_a,
            "pathId": path_id_a,
            "thresholdPointId": point_id_a,
            "declaredDistances": {
                "toraMeters": length_m,
                "todaMeters": length_m,
                "asdaMeters": length_m,
                "ldaMeters": max(length_m - round(displaced_a), 0),
            },
            "projectionStatus": "direct",
            "note": "Version 1 uses the full strip and intentionally does not model displaced-threshold asymmetry.",
        }
        core_entities["aerodrome"]["runways"][designator_b] = {
            "id": designator_b,
            "pathId": path_id_b,
            "thresholdPointId": point_id_b,
            "declaredDistances": {
                "toraMeters": length_m,
                "todaMeters": length_m,
                "asdaMeters": length_m,
                "ldaMeters": max(length_m - round(displaced_b), 0),
            },
            "projectionStatus": "direct",
            "note": "Version 1 uses the full strip and intentionally does not model displaced-threshold asymmetry.",
        }

    ground_components_by_index = {
        index: component
        for index, component in enumerate(scene.ground_components, start=1)
    }
    transformed_ground_marker_points = {
        index: point.point
        for index, point in enumerate(scene.ground_points, start=1)
    }
    ground_markers_by_component: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for marker in manifest_named_mapping(manifest, "groundMarkers"):
        if marker.get("status") == "DELETE":
            continue
        marker_index = marker.get("markerIndex")
        if not isinstance(marker_index, int):
            continue
        point = transformed_ground_marker_points.get(marker_index)
        if point is None:
            drawing_point = marker.get("drawingPoint")
            if not isinstance(drawing_point, dict):
                continue
            point = report.XY(float(drawing_point["x"]), float(drawing_point["y"]))
        marker_name = marker.get("finalName")
        for component_mapping in manifest_named_mapping(manifest, "groundComponents"):
            if marker_index in component_mapping.get("associatedMarkerIndexes", []):
                ground_markers_by_component[int(component_mapping["componentIndex"])].append(
                    {
                        "markerIndex": marker_index,
                        "name": marker_name,
                        "point": point,
                        "note": marker.get("note"),
                    }
                )
                break

    ordered_component_points: dict[int, list[report.XY]] = {}
    component_paths: dict[int, str] = {}
    component_notes: dict[int, str] = {}

    for component_mapping in manifest_named_mapping(manifest, "groundComponents"):
        component_index = int(component_mapping["componentIndex"])
        if component_mapping.get("semanticRole") == "runway_axis_control":
            continue
        component_lines = ground_components_by_index.get(component_index, [])
        ordered_points, _ = ordered_points_from_lines(component_lines)
        inserted_points, unplaced_points = insert_points_into_polyline(
            ordered_points,
            [marker["point"] for marker in ground_markers_by_component.get(component_index, [])],
            tolerance_m=SNAP_TOLERANCE_METERS,
        )
        ordered_component_points[component_index] = inserted_points
        component_name = str(component_mapping.get("finalName") or component_mapping.get("displayLabel") or f"component_{component_index}")
        taxiway_id = component_name
        projection_status = "direct"
        note_parts: list[str] = []
        if component_name == "A" and component_mapping.get("candidateNameFromMarkers") == "D":
            projection_status = "provisional_mixed_taxiway_cluster"
            note_parts.append("This projected taxiway still contains the runway-end D turn and should be split later.")
        note = str(component_mapping.get("note") or "")
        if note:
            note_parts.append(note)
        if unplaced_points:
            note_parts.append(f"{len(unplaced_points)} holding marker(s) could not be inserted onto the component path.")
        path_id = f"{airport_code}_PATH_TWY_{slugify(taxiway_id).upper()}"
        component_paths[component_index] = path_id
        component_notes[component_index] = " ".join(part for part in note_parts if part)
        add_path(
            geometry_paths,
            registry,
            path_id,
            inserted_points,
            surface="GROUND",
            width_m=GROUND_WIDTH_METERS,
            source="cad_ground",
            projection_status=projection_status,
            point_id_prefix=f"{airport_code}_TWY_{slugify(taxiway_id).upper()}",
            point_label_prefix=component_name,
            note=component_notes[component_index] or None,
        )

    a_component_index = next(
        (
            int(component_mapping["componentIndex"])
            for component_mapping in manifest_named_mapping(manifest, "groundComponents")
            if str(component_mapping.get("finalName")) == "A"
        ),
        None,
    )
    a_polyline = ordered_component_points.get(a_component_index, []) if a_component_index is not None else []
    a_branch_join_points: list[report.XY] = []
    apron_branch_builders: list[dict[str, Any]] = []

    for branch in scene.parking_access_branches:
        branch_lines = [
            report.DxfLine(layer=branch.display_name, start=edge.start, end=edge.end)
            for edge in branch.edges
        ]
        branch_points, _ = ordered_points_from_lines(branch_lines)
        if len(branch_points) < 2:
            continue
        if a_polyline:
            first_distance, _, _, _ = nearest_point_on_polyline(branch_points[0], a_polyline)
            last_distance, _, _, _ = nearest_point_on_polyline(branch_points[-1], a_polyline)
            if last_distance < first_distance:
                branch_points = list(reversed(branch_points))
        branch_points, _ = insert_points_into_polyline(
            branch_points,
            [connector.attach_point for connector in branch.connectors],
            tolerance_m=SNAP_TOLERANCE_METERS,
        )
        join_point = branch_points[0]
        if a_polyline:
            _, projected_join_point, _, _ = nearest_point_on_polyline(branch_points[0], a_polyline)
            join_point = projected_join_point
            a_branch_join_points.append(projected_join_point)
        apron_branch_builders.append(
            {
                "branch": branch,
                "points": branch_points,
                "joinPoint": join_point,
            }
        )

    if a_component_index is not None and a_branch_join_points:
        augmented_a_points, _ = insert_points_into_polyline(a_polyline, a_branch_join_points, tolerance_m=SNAP_TOLERANCE_METERS)
        ordered_component_points[a_component_index] = augmented_a_points
        path_id = component_paths[a_component_index]
        geometry_paths.pop(path_id, None)
        add_path(
            geometry_paths,
            registry,
            path_id,
            augmented_a_points,
            surface="GROUND",
            width_m=GROUND_WIDTH_METERS,
            source="cad_ground",
            projection_status="provisional_mixed_taxiway_cluster" if component_notes[a_component_index] else "direct",
            point_id_prefix=f"{airport_code}_TWY_A",
            point_label_prefix="A",
            note=component_notes[a_component_index] or "Includes projected apron join points for the version-1 parking branches.",
        )

    for component_mapping in manifest_named_mapping(manifest, "groundComponents"):
        component_index = int(component_mapping["componentIndex"])
        if component_mapping.get("semanticRole") == "runway_axis_control":
            continue
        component_name = str(component_mapping.get("finalName") or component_mapping.get("displayLabel") or f"component_{component_index}")
        holding_points: list[dict[str, Any]] = []
        for marker in ground_markers_by_component.get(component_index, []):
            marker_name = marker.get("name")
            marker_point = marker["point"]
            snapped_point_id = registry.lookup_id(marker_point)
            point_id = snapped_point_id or registry.register(
                marker_point,
                f"{airport_code}_HOLD_{slugify(str(marker_name or marker['markerIndex'])).upper()}",
                label=str(marker_name) if marker_name is not None else None,
                tags=["holding_point_candidate", "ground"],
                sources=["cad_ground"],
            )
            holding_point_id = f"{airport_code}_HOLD_{slugify(str(marker_name or marker['markerIndex'])).upper()}"
            holding_points.append(
                {
                    "id": holding_point_id,
                    "pointId": point_id,
                    "name": marker_name,
                    "type": None,
                    "runwayId": None,
                    "projectionStatus": "candidate_missing_type_and_runway_assignment" if snapped_point_id is not None else "blocked_marker_not_on_taxiway_path",
                    "note": (
                        marker.get("note")
                        if snapped_point_id is not None
                        else "Marker could not be snapped onto the projected taxiway path. " +
                        (marker.get("note") or "")
                    ),
                }
            )
            candidate_entities["holdingPoints"][holding_point_id] = holding_points[-1]
        core_entities["aerodrome"]["taxiways"][component_name] = {
            "id": component_name,
            "name": component_name,
            "pathId": component_paths[component_index],
            "bidirectional": True,
            "holdingPoints": holding_points,
            "projectionStatus": "provisional_mixed_taxiway_cluster" if component_name == "A" else "direct",
            "note": component_notes.get(component_index),
        }

    stand_lookup_by_name: dict[str, str] = {}
    authored_parking_notes: list[str] = []
    authored_parking_attachment_mode = "reference_only"
    authored_named_stands, authored_parking_lines, authored_parking_diagnostics = authored_parking_rows(manifest, root)

    if authored_named_stands and authored_parking_lines:
        for stand_name, stand_point in authored_named_stands:
            reference_parking, reference_distance = nearest_reference_parking(scene.parking_positions, stand_point)
            stand_id = f"{airport_code}_STAND_{slugify(stand_name).upper()}"
            point_id = registry.register(
                stand_point,
                f"{stand_id}_POINT",
                label=stand_name,
                tags=["stand", parking_location_type(stand_name, reference_parking)],
                sources=["cad_working_dxf", "authored_parking"],
            )
            stand_lookup_by_name[stand_name] = stand_id
            core_entities["aerodrome"]["stands"][stand_id] = {
                "id": stand_id,
                "name": stand_name,
                "pointId": point_id,
                "locationType": parking_location_type(stand_name, reference_parking),
                "aircraftTypes": reference_parking.aircraft_types if reference_parking is not None else "",
                "projectionStatus": "direct_authored_geometry_with_reference_attrs" if reference_parking is not None else "direct_authored_geometry",
                "note": (
                    f"Authored stand point from NEW_Parking_Points; nearest apt.dat reference is {reference_distance:.1f}m away."
                    if reference_parking is not None
                    else "Authored stand point from NEW_Parking_Points."
                ),
            }

        authored_graph_insertions: dict[int, list[report.XY]] = defaultdict(list)
        stand_points = [stand_point for _, stand_point in authored_named_stands]
        current_a_polyline = ordered_component_points.get(a_component_index, []) if a_component_index is not None else []
        direct_a_attachment_points: list[report.XY] = []
        for line_index, line in enumerate(authored_parking_lines):
            for point in stand_points:
                distance_m, _ = authoring.nearest_point_on_segment(point, line.start, line.end)
                if distance_m <= PARKING_GRAPH_SPLIT_TOLERANCE_METERS:
                    authored_graph_insertions[line_index].append(point)
            for other_index, other_line in enumerate(authored_parking_lines):
                if other_index == line_index:
                    continue
                for endpoint in (other_line.start, other_line.end):
                    distance_m, _ = authoring.nearest_point_on_segment(endpoint, line.start, line.end)
                    if distance_m <= PARKING_GRAPH_SPLIT_TOLERANCE_METERS:
                        authored_graph_insertions[line_index].append(endpoint)
            for other_index in range(line_index + 1, len(authored_parking_lines)):
                other_line = authored_parking_lines[other_index]
                intersection = segment_intersection_point(
                    line.start,
                    line.end,
                    other_line.start,
                    other_line.end,
                )
                if intersection is None:
                    continue
                authored_graph_insertions[line_index].append(intersection)
                authored_graph_insertions[other_index].append(intersection)
            if current_a_polyline:
                for endpoint in (line.start, line.end):
                    distance_m, projected_point, _, _ = nearest_point_on_polyline(endpoint, current_a_polyline)
                    if distance_m <= PARKING_GRAPH_SPLIT_TOLERANCE_METERS:
                        authored_graph_insertions[line_index].append(projected_point)
                        direct_a_attachment_points.append(projected_point)
                for a_start, a_end in zip(current_a_polyline, current_a_polyline[1:]):
                    intersection = segment_intersection_point(
                        line.start,
                        line.end,
                        a_start,
                        a_end,
                    )
                    if intersection is None:
                        continue
                    authored_graph_insertions[line_index].append(intersection)
                    direct_a_attachment_points.append(intersection)

        taxiway_join_paths: list[str] = []
        taxiway_join_notes: list[str] = []
        direct_a_attachment_points = unique_xy(direct_a_attachment_points)
        join_targets: list[tuple[report.XY, report.XY, float]] = []

        if a_component_index is not None and current_a_polyline and direct_a_attachment_points:
            augmented_a_points, _ = insert_points_into_polyline(
                current_a_polyline,
                direct_a_attachment_points,
                tolerance_m=PARKING_GRAPH_SPLIT_TOLERANCE_METERS,
            )
            ordered_component_points[a_component_index] = augmented_a_points
            path_id = component_paths[a_component_index]
            geometry_paths.pop(path_id, None)
            direct_attachment_note = f"Includes {len(direct_a_attachment_points)} direct authored parking attachment point(s)."
            note = (
                f"{component_notes[a_component_index]} {direct_attachment_note}".strip()
                if component_notes[a_component_index]
                else direct_attachment_note
            )
            add_path(
                geometry_paths,
                registry,
                path_id,
                augmented_a_points,
                surface="GROUND",
                width_m=GROUND_WIDTH_METERS,
                source="cad_ground",
                projection_status="provisional_mixed_taxiway_cluster" if component_notes[a_component_index] else "direct",
                point_id_prefix=f"{airport_code}_TWY_A",
                point_label_prefix="A",
                note=note,
            )
            authored_parking_attachment_mode = "direct"
        else:
            projected_join_points = unique_xy([branch_builder["joinPoint"] for branch_builder in apron_branch_builders])
            for join_point in projected_join_points:
                best = nearest_point_on_lines(join_point, authored_parking_lines)
                if best is None:
                    continue
                distance_m, projected_point, line_index = best
                authored_graph_insertions[line_index].append(projected_point)
                join_targets.append((join_point, projected_point, distance_m))
                taxiway_join_notes.append(
                    f"Projected taxiway-A apron join inserted at {distance_m:.1f}m from the authored parking graph."
                )
            if join_targets:
                authored_parking_attachment_mode = "projected"
            else:
                authored_parking_attachment_mode = "unattached"

        apron_path_ids: list[str] = []
        for line_index, line in enumerate(authored_parking_lines, start=1):
            split_points, _ = insert_points_into_polyline(
                [line.start, line.end],
                unique_xy(authored_graph_insertions.get(line_index - 1, [])),
                tolerance_m=PARKING_GRAPH_SPLIT_TOLERANCE_METERS,
            )
            path_id = f"{airport_code}_APRON_AUTHORED_{line_index:02d}"
            add_path(
                geometry_paths,
                registry,
                path_id,
                split_points,
                surface="GROUND",
                width_m=GROUND_WIDTH_METERS,
                source="cad_working_dxf",
                projection_status="direct_authored_parking_graph",
                point_id_prefix=f"{airport_code}_APRON_AUTHORED_{line_index:02d}",
                note="Authored parking/apron geometry from NEW_Parking.",
            )
            apron_path_ids.append(path_id)

        for connector_index, (join_point, attach_point, distance_m) in enumerate(join_targets, start=1):
            if distance(join_point, attach_point) <= 1e-6:
                continue
            connector_path_id = f"{airport_code}_APRON_AUTHORED_JOIN_{connector_index:02d}"
            add_path(
                geometry_paths,
                registry,
                connector_path_id,
                [join_point, attach_point],
                surface="GROUND",
                width_m=GROUND_WIDTH_METERS,
                source="projected_taxiway_apron_join",
                projection_status="provisional_projected_apron_join",
                point_id_prefix=f"{airport_code}_APRON_AUTHORED_JOIN_{connector_index:02d}",
                note=f"Projected join from taxiway A into the authored parking graph ({distance_m:.1f}m gap).",
            )
            taxiway_join_paths.append(connector_path_id)

        apron_projection_status = "direct_authored_parking_graph"
        apron_note = "Authored apron graph from NEW_Parking."
        if authored_parking_attachment_mode == "direct":
            apron_projection_status = "direct_authored_parking_graph_with_direct_taxiway_a_attachments"
            apron_note = "Authored apron graph from NEW_Parking with direct taxiway A attachments from the working DXF."
        elif authored_parking_attachment_mode == "projected":
            apron_projection_status = "direct_authored_parking_graph_with_projected_taxiway_joins"
            apron_note = "Authored apron graph from NEW_Parking with projected joins back to taxiway A for version 1 current-core connectivity."
        elif authored_parking_attachment_mode == "unattached":
            apron_projection_status = "direct_authored_parking_graph_without_taxiway_a_attachment"
            apron_note = "Authored apron graph from NEW_Parking, but no taxiway A attachment could be derived."

        core_entities["aerodrome"]["aprons"][f"{airport_code}_APRON_AUTHORED_PARKING"] = {
            "id": f"{airport_code}_APRON_AUTHORED_PARKING",
            "name": "Authored Parking",
            "pathIds": apron_path_ids + taxiway_join_paths,
            "standIds": sorted(stand_lookup_by_name.values()),
            "projectionStatus": apron_projection_status,
            "note": apron_note,
        }
        authored_parking_notes.extend(authored_parking_diagnostics)
        authored_parking_notes.extend(taxiway_join_notes)
    else:
        for parking in scene.parking_positions:
            stand_id = f"{airport_code}_STAND_{slugify(parking.name).upper()}"
            point_id = registry.register(
                parking.point,
                f"{stand_id}_POINT",
                label=parking.name,
                tags=["stand", parking.location_type],
                sources=["apt.dat"],
            )
            stand_lookup_by_name[parking.name] = stand_id
            core_entities["aerodrome"]["stands"][stand_id] = {
                "id": stand_id,
                "name": parking.name,
                "pointId": point_id,
                "locationType": parking.location_type,
                "aircraftTypes": parking.aircraft_types,
                "projectionStatus": "direct",
            }

        for branch_builder in apron_branch_builders:
            branch = branch_builder["branch"]
            branch_points = branch_builder["points"]
            join_point = branch_builder["joinPoint"]
            display_name = branch.display_name
            apron_id = f"{airport_code}_APRON_{slugify(display_name).upper()}"
            main_points = [join_point] + branch_points if distance(join_point, branch_points[0]) > 1e-6 else branch_points
            main_path_id = f"{apron_id}_MAIN"
            apron_path_ids: list[str] = []
            add_path(
                geometry_paths,
                registry,
                main_path_id,
                main_points,
                surface="GROUND",
                width_m=GROUND_WIDTH_METERS,
                source="xplane_parking_branch",
                projection_status="provisional_synthetic_apron_attachment",
                point_id_prefix=f"{apron_id}_PATH_MAIN",
                note="X-Plane branch geometry attached to taxiway A by a projected join point.",
            )
            apron_path_ids.append(main_path_id)
            stand_ids: list[str] = []
            for connector_index, connector in enumerate(branch.connectors, start=1):
                stand_id = stand_lookup_by_name.get(connector.stand_name)
                if stand_id is None:
                    continue
                stand_ids.append(stand_id)
                connector_path_id = f"{apron_id}_STAND_{connector_index:02d}"
                add_path(
                    geometry_paths,
                    registry,
                    connector_path_id,
                    [connector.attach_point, connector.stand_point],
                    surface="GROUND",
                    width_m=GROUND_WIDTH_METERS,
                    source="synthetic_parking_connector",
                    projection_status="provisional_synthetic_stand_leadin",
                    point_id_prefix=f"{apron_id}_PATH_STAND_{connector_index:02d}",
                    note="Synthetic nearest-branch stand lead-in used for version 1 parking access.",
                )
                apron_path_ids.append(connector_path_id)
            core_entities["aerodrome"]["aprons"][apron_id] = {
                "id": apron_id,
                "name": display_name,
                "pathIds": apron_path_ids,
                "standIds": sorted(set(stand_ids)),
                "projectionStatus": "provisional_synthetic_stand_leadins",
                "note": "Branch spine comes from X-Plane taxi-route geometry; stand lead-ins are synthetic nearest-branch connectors.",
            }

    for reporting_point in scene.reporting_points:
        point_id = registry.register(
            reporting_point.point,
            f"{airport_code}_FIX_{slugify(reporting_point.code_id).upper()}",
            label=reporting_point.code_id,
            tags=["fix", "vfr_reporting_point"],
            sources=["ofmx"],
        )
        fix_id = reporting_point.code_id
        core_entities["fixes"][fix_id] = {
            "id": fix_id,
            "name": reporting_point.code_id,
            "pointId": point_id,
            "type": "WAYPOINT",
            "reportingType": reporting_point.code_type,
            "projectionStatus": "direct",
        }

    for anchor in scene.procedure_anchors:
        point_id = registry.register(
            anchor.point,
            f"{airport_code}_ANCHOR_{slugify(anchor.anchor_id).upper()}",
            label=anchor.label,
            tags=["named_anchor"],
            sources=["manifest"],
        )
        candidate_entities["namedPoints"][anchor.anchor_id] = {
            "id": anchor.anchor_id,
            "pointId": point_id,
            "label": anchor.label,
            "anchorType": anchor.anchor_type,
            "note": anchor.note,
            "projectionStatus": "candidate_named_point",
        }

    if scene.tower_xy is not None:
        tower_point_id = registry.register(
            scene.tower_xy,
            f"{airport_code}_TOWER_POINT",
            label="TWR",
            tags=["tower"],
            sources=["apt.dat"],
        )
        candidate_entities["namedPoints"]["tower"] = {
            "id": "tower",
            "pointId": tower_point_id,
            "label": "TWR",
            "anchorType": "tower_reference",
            "note": "Tower viewpoint reference point.",
            "projectionStatus": "candidate_named_point",
        }

    candidate_entities["airspaceVolumes"] = structured_airspace_volumes(
        scene,
        ofmx_data,
        registry,
        airport_code,
    )

    for route in manifest_named_mapping(manifest, "vfrRoutes"):
        route_id = route.get("routeId")
        if not isinstance(route_id, str):
            continue
        point_ids: list[str] = []
        unresolved_refs: list[str] = []
        for node in route.get("pathDefinition", {}).get("nodeSequence", []):
            if not isinstance(node, dict):
                continue
            node_ref = node.get("nodeRef")
            if not isinstance(node_ref, str):
                continue
            if node_ref in core_entities["fixes"]:
                point_ids.append(core_entities["fixes"][node_ref]["pointId"])
            elif node_ref in candidate_entities["namedPoints"]:
                point_ids.append(candidate_entities["namedPoints"][node_ref]["pointId"])
            else:
                unresolved_refs.append(node_ref)
        candidate_entities["vfrRoutes"][route_id] = {
            "id": route_id,
            "name": route_id,
            "pointIds": point_ids,
            "publishedNodeRefs": [
                node.get("nodeRef")
                for node in route.get("pathDefinition", {}).get("nodeSequence", [])
                if isinstance(node, dict) and isinstance(node.get("nodeRef"), str)
            ],
            "routeType": route.get("routeType"),
            "projectionStatus": "candidate_route_airspace_profile_pending" if not unresolved_refs else "blocked_unresolved_route_points",
            "blockedFields": ["airspaceProfile"],
            "unresolvedNodeRefs": unresolved_refs,
            "note": route.get("note"),
        }

    candidate_entities["operationalSectors"] = structured_operational_sectors(
        manifest,
        scene,
        registry,
        core_entities["fixes"],
    )

    circuit_components = sorted(scene.circuit_components, key=lambda component: len(component), reverse=True)
    circuit_component_names = ["main_shared_graph", "west_side_component", "east_side_component"]
    for component_name, component_lines in zip(circuit_component_names, circuit_components):
        ordered_points, closed = ordered_points_from_lines(component_lines)
        path_id = f"{airport_code}_CIRCUIT_{slugify(component_name).upper()}"
        add_path(
            geometry_paths,
            registry,
            path_id,
            ordered_points,
            surface="SKY",
            width_m=SKY_WIDTH_METERS,
            source="cad_circuit",
            projection_status="candidate_shared_circuit_graph",
            point_id_prefix=f"{airport_code}_CIRCUIT_{slugify(component_name).upper()}",
            note="Candidate shared circuit graph component retained as source geometry for the projected LOWG directional circuits.",
        )
        candidate_entities["circuitGraphs"][component_name] = {
            "id": component_name,
            "pathId": path_id,
            "closed": closed,
            "projectionStatus": "candidate_shared_circuit_graph",
        }

    core_entities["aerodrome"]["circuits"] = projected_circuit_procedures(
        scene,
        registry,
        geometry_paths,
        core_entities,
        candidate_entities,
        airport_code,
    )

    candidate_entities["publishedVfrProcedures"] = structured_published_vfr_procedures(
        manifest,
        core_entities["fixes"],
        candidate_entities["namedPoints"],
        candidate_entities["operationalSectors"],
        candidate_entities["vfrRoutes"],
        candidate_entities["circuitGraphs"],
    )

    for route_id, route in sorted(candidate_entities["vfrRoutes"].items()):
        point_ids = [
            point_id
            for point_id in route.get("pointIds", [])
            if isinstance(point_id, str)
        ]
        projected_point_ids, airspace_profile, route_projection_status = lowg_vfr_route_projection(
            route_id,
            point_ids,
            registry,
            candidate_entities["airspaceVolumes"],
            airport_code,
        )
        path_id = f"{airport_code}_VFR_ROUTE_{slugify(route_id).upper()}"
        path = add_core_path_from_point_ids(
            geometry_paths,
            registry,
            path_id,
            projected_point_ids,
            surface="SKY",
            width_m=SKY_WIDTH_METERS,
            source="structured_vfr_route",
            projection_status=route_projection_status,
            note=route.get("note"),
        )
        if path is None:
            continue
        core_entities["vfrRoutes"][route_id] = {
            "id": route_id,
            "name": route.get("name", route_id),
            "pointIds": projected_point_ids,
            "pathId": path_id,
            "airspaceProfile": airspace_profile,
            "projectionStatus": route_projection_status,
            "note": route.get("note"),
        }

    for sector_id, sector in sorted(candidate_entities["operationalSectors"].items()):
        boundary_path_ids: list[str] = []
        for ring_index, ring in enumerate(sector.get("boundaryPointIds", []), start=1):
            if not isinstance(ring, list):
                continue
            ring_point_ids = [point_id for point_id in ring if isinstance(point_id, str)]
            path_id = f"{airport_code}_SECTOR_{slugify(sector_id).upper()}_{ring_index:02d}"
            path = add_core_path_from_point_ids(
                geometry_paths,
                registry,
                path_id,
                ring_point_ids,
                surface="SKY",
                width_m=SKY_WIDTH_METERS,
                source="working_airspace_dxf",
                projection_status="direct_runtime_boundary",
                note=sector.get("note"),
            )
            if path is not None:
                boundary_path_ids.append(path_id)

        altitude_limits = sector.get("altitudeLimits") if isinstance(sector.get("altitudeLimits"), dict) else {}
        contact_limit = altitude_limits.get("upperFeetMsl")
        core_entities["aerodrome"]["aip"]["operationalSectors"][sector_id] = {
            "id": sector_id,
            "name": sector.get("name", sector_id),
            "kind": projected_operational_sector_kind(sector.get("kind")),
            "boundaryPathIds": boundary_path_ids,
            "anchor": operational_sector_anchor(
                sector.get("anchorPointId") if isinstance(sector.get("anchorPointId"), str) else None,
                sector.get("anchorRole") if isinstance(sector.get("anchorRole"), str) else None,
            ),
            "entryExitPointIds": [
                point_id
                for point_id in sector.get("entryExitPointIds", [])
                if isinstance(point_id, str)
            ],
            "altitudeBand": {
                "lower": {"kind": "SURFACE"},
                "upper": (
                    {"kind": "AT_LEVEL", "levelType": "ALTITUDE_FEET", "value": int(contact_limit)}
                    if isinstance(contact_limit, int)
                    else None
                ),
            },
            "contactRequirement": projected_contact_requirement(
                sector.get("contactRequirement") if isinstance(sector.get("contactRequirement"), dict) else None,
                core_entities["fixes"],
                candidate_entities["namedPoints"],
                candidate_entities["operationalSectors"],
            ),
            "relationToCtr": projected_operational_sector_ctr_relation(
                sector.get("relationToCtr") if isinstance(sector.get("relationToCtr"), str) else None,
            ),
            "associatedProcedureIds": [
                procedure_id
                for procedure_id in sector.get("associatedProcedureIds", [])
                if isinstance(procedure_id, str)
            ],
            "projectionStatus": "direct_runtime_aip_entity",
            "note": sector.get("note"),
            "specialProcedureNote": sector.get("specialProcedureNote"),
        }

    for procedure_id, procedure in sorted(candidate_entities["publishedVfrProcedures"].items()):
        terminates_at_ref = procedure.get("terminatesAt")
        terminates_at_point_id, terminates_at_resolution = (
            resolve_point_ref(
                terminates_at_ref,
                core_entities["fixes"],
                candidate_entities["namedPoints"],
                candidate_entities["operationalSectors"],
            )
            if isinstance(terminates_at_ref, str)
            else (None, None)
        )
        hold_at_ref = procedure.get("holdAt")
        hold_at_point_id, hold_at_resolution = (
            resolve_point_ref(
                hold_at_ref,
                core_entities["fixes"],
                candidate_entities["namedPoints"],
                candidate_entities["operationalSectors"],
            )
            if isinstance(hold_at_ref, str)
            else (None, None)
        )
        core_entities["aerodrome"]["aip"]["publishedVfrProcedures"][procedure_id] = {
            "id": procedure_id,
            "plateId": procedure.get("plateId", procedure_id),
            "kind": projected_published_vfr_procedure_kind(
                procedure.get("kind") if isinstance(procedure.get("kind"), str) else None,
            ),
            "publishedSequence": [
                published_point_reference(
                    sequence_item.get("ref"),
                    sequence_item.get("pointId"),
                    sequence_item.get("resolutionType"),
                )
                for sequence_item in procedure.get("resolvedPublishedSequence", [])
                if isinstance(sequence_item, dict)
            ],
            "associatedVfrRouteIds": [
                route_id
                for route_id in procedure.get("associatedVfrRouteIds", [])
                if isinstance(route_id, str)
            ],
            "associatedOperationalSectorIds": [
                sector_id
                for sector_id in procedure.get("associatedOperationalSectorIds", [])
                if isinstance(sector_id, str)
            ],
            "associatedCircuitIds": sorted(
                {
                    circuit_id
                    for circuit_id in candidate_entities.get("publishedCircuitAssociations", {}).get(procedure_id, [])
                    if isinstance(circuit_id, str) and circuit_id in core_entities["aerodrome"]["circuits"]
                }
            ),
            "contactRequirement": projected_contact_requirement(
                procedure.get("contactRequirement") if isinstance(procedure.get("contactRequirement"), dict) else None,
                core_entities["fixes"],
                candidate_entities["namedPoints"],
                candidate_entities["operationalSectors"],
            ),
            "advisories": published_procedure_advisories(procedure),
            "mapLabels": [
                map_label_document
                for map_label in procedure.get("mapWaypointLabels", [])
                if isinstance(map_label, dict)
                for map_label_document in [
                    published_map_label(
                        map_label.get("label"),
                        map_label.get("pointRef"),
                        map_label.get("pointId"),
                        map_label.get("resolutionType"),
                    )
                ]
                if map_label_document is not None
            ],
            "terminatesAt": published_point_reference(
                terminates_at_ref,
                terminates_at_point_id,
                terminates_at_resolution,
            ),
            "holdAt": published_point_reference(
                hold_at_ref,
                hold_at_point_id,
                hold_at_resolution,
            ),
            "communicationFailure": published_procedure_communication_failure(
                procedure,
                core_entities["fixes"],
                candidate_entities["namedPoints"],
                candidate_entities["operationalSectors"],
            ),
            "departureRunwayIds": [
                runway_id
                for runway_id in procedure.get("departureRunwaySet", [])
                if isinstance(runway_id, str)
            ],
            "applicableRunwayIds": [
                runway_id
                for runway_id in procedure.get("applicableRunwaySet", [])
                if isinstance(runway_id, str)
            ],
            "projectionStatus": "direct_runtime_aip_entity",
        }

    core_entities["geometry"]["points"] = registry.as_json()

    publication_semantics = {
        "publishedVfrProcedures": manifest_named_mapping(manifest, "publishedVfrProcedures"),
        "publishedAerodromeInformation": manifest_named_mapping(manifest, "publishedAerodromeInformation"),
        "operationalSectors": sector_shapes(scene),
    }

    candidate_entities["ifrProcedures"] = build_ifr_inventory(cifp_data, ofmx_data, chart_fix_data, project)

    projection_gaps = [
        "Taxiway A is still a provisional mixed D->A cluster and should be split into clean segment ownership before a strict core import.",
        "Holding-point candidates exist, but runway-protection assignment and HoldingPointType are not yet encoded strongly enough for the current validator.",
        "Airspace boundary geometry is available, but point-to-volume membership for all projected points is not yet assigned.",
        "LOWG IFR identifier resolution is now good enough for a broader runtime subset, but STARs, LOC 34C, and richer published minima variants still remain outside the current-core projection.",
        "The east non-standard hold remains deferred to version 2 until the loiter model is corrected.",
    ]
    if authored_parking_attachment_mode == "projected":
        projection_gaps.append(
            "Version-1 apron access uses authored NEW_Parking geometry for the internal apron graph, but the joins back to taxiway A are still projected."
        )
    elif authored_parking_attachment_mode == "unattached":
        projection_gaps.append(
            "Version-1 apron access uses authored NEW_Parking geometry, but no taxiway A attachment could be derived from the authored graph."
        )
    projection_gaps.extend(authored_parking_notes)

    return {
        "packageStatus": "v1_partial",
        "airportCode": airport_code,
        "airportName": manifest.get("airportName") or airport.name,
        "sourceManifest": str(resolved_manifest_path.relative_to(root)),
        "summary": {
            "runways": len(core_entities["aerodrome"]["runways"]),
            "circuits": len(core_entities["aerodrome"]["circuits"]),
            "taxiways": len(core_entities["aerodrome"]["taxiways"]),
            "stands": len(core_entities["aerodrome"]["stands"]),
            "aprons": len(core_entities["aerodrome"]["aprons"]),
            "fixes": len(core_entities["fixes"]),
            "directVfrRoutes": len(core_entities["vfrRoutes"]),
            "directOperationalSectors": len(core_entities["aerodrome"]["aip"]["operationalSectors"]),
            "directPublishedVfrProcedures": len(core_entities["aerodrome"]["aip"]["publishedVfrProcedures"]),
            "candidateVfrRoutes": len(candidate_entities["vfrRoutes"]),
            "candidateOperationalSectors": len(candidate_entities["operationalSectors"]),
            "candidatePublishedVfrProcedures": len(candidate_entities["publishedVfrProcedures"]),
            "candidateCircuitGraphs": len(candidate_entities["circuitGraphs"]),
            "candidateIfrProcedureNames": sum(
                len(candidate_entities["ifrProcedures"][section]["byName"])
                for section in ("sids", "stars", "approaches")
            ),
            "candidateTowerScopeApproaches": len(
                candidate_entities["ifrProcedures"]
                .get("towerScopeApproaches", {})
                .get("byName", {})
            ),
        },
        "directCoreFitEntities": core_entities,
        "candidateOperationalStructures": candidate_entities,
        "publicationSemantics": publication_semantics,
        "projectionDiagnostics": {
            "projectionGaps": projection_gaps,
        },
    }


def structured_package_json(package: dict[str, Any]) -> str:
    return json.dumps(package, indent=2, sort_keys=True) + "\n"
