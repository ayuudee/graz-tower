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
    ) -> str:
        key = rounded_key(point)
        existing_id = self._point_key_to_id.get(key)
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

        self._point_key_to_id[key] = point_id
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
    if len(points) != len(point_ids):
        return None
    return add_path(
        geometry_paths,
        registry,
        path_id,
        points,
        surface=surface,
        width_m=width_m,
        source=source,
        projection_status=projection_status,
        point_id_prefix=path_id,
        note=note,
        preferred_ids={index: point_id for index, point_id in enumerate(point_ids, start=1)},
    )


def lowg_vfr_airspace_profile(route_id: str, point_ids: list[str]) -> dict[str, Any] | None:
    del route_id
    del point_ids
    return None


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


def authored_parking_rows(
    manifest: dict[str, Any],
    root: Path,
) -> tuple[list[tuple[str, report.XY]], list[report.DxfLine], list[str]]:
    working_dxf = manifest.get("workingDxf")
    if not isinstance(working_dxf, dict):
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
    ofmx_path = report.resolve_path(root, manifest["sources"]["ofmx"])
    runways, _, _, _, _, _ = report.parse_apt(apt_path)
    ofmx_data = report.parse_ofmx(ofmx_path, airport_code)
    airport = ofmx_data["airport"]

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
            note="Candidate circuit graph component; not yet projected into directional CircuitProcedure entities.",
        )
        candidate_entities["circuitGraphs"][component_name] = {
            "id": component_name,
            "pathId": path_id,
            "closed": closed,
            "projectionStatus": "candidate_shared_circuit_graph",
        }

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
        path_id = f"{airport_code}_VFR_ROUTE_{slugify(route_id).upper()}"
        path = add_core_path_from_point_ids(
            geometry_paths,
            registry,
            path_id,
            point_ids,
            surface="SKY",
            width_m=SKY_WIDTH_METERS,
            source="structured_vfr_route",
            projection_status="direct",
            note=route.get("note"),
        )
        if path is None:
            continue
        airspace_profile = lowg_vfr_airspace_profile(route_id, point_ids)
        core_entities["vfrRoutes"][route_id] = {
            "id": route_id,
            "name": route.get("name", route_id),
            "pointIds": point_ids,
            "pathId": path_id,
            "airspaceProfile": airspace_profile,
            "projectionStatus": "direct" if airspace_profile is not None else "direct_geometry_airspace_profile_pending",
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
            "associatedCircuitIds": [],
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

    for airspace_shape in scene.airspace_shapes:
        boundary_point_ids: list[list[str]] = []
        for boundary_index, boundary in enumerate(airspace_shape.boundaries, start=1):
            point_ids: list[str] = []
            for point_index, point in enumerate(boundary, start=1):
                point_ids.append(
                    registry.register(
                        point,
                        f"{airport_code}_AIRSPACE_{slugify(airspace_shape.code_id or airspace_shape.mid).upper()}_{boundary_index:02d}_{point_index:02d}",
                        tags=["airspace_boundary"],
                        sources=["ofmx"],
                    )
                )
            boundary_point_ids.append(point_ids)
        candidate_entities["airspaceVolumes"][airspace_shape.mid] = {
            "id": airspace_shape.mid,
            "name": airspace_shape.name,
            "codeId": airspace_shape.code_id,
            "label": airspace_shape.label,
            "lowerLimit": airspace_shape.lower_limit,
            "upperLimit": airspace_shape.upper_limit,
            "boundaryPointIds": boundary_point_ids,
            "category": airspace_shape.category,
            "projectionStatus": "candidate_boundary_geometry_without_point_claims",
            "note": "Boundary geometry is available, but point-to-volume membership has not yet been projected for the current core model.",
        }

    core_entities["geometry"]["points"] = registry.as_json()

    publication_semantics = {
        "publishedVfrProcedures": manifest_named_mapping(manifest, "publishedVfrProcedures"),
        "publishedAerodromeInformation": manifest_named_mapping(manifest, "publishedAerodromeInformation"),
        "operationalSectors": sector_shapes(scene),
    }

    projection_gaps = [
        "Taxiway A is still a provisional mixed D->A cluster and should be split into clean segment ownership before a strict core import.",
        "Holding-point candidates exist, but runway-protection assignment and HoldingPointType are not yet encoded strongly enough for the current validator.",
        "The current circuit drawing is still a shared graph and has not yet been projected into final directional CircuitProcedure entities.",
        "Airspace boundary geometry is available, but point-to-volume membership for all projected points is not yet assigned.",
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
