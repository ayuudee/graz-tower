#!/usr/bin/env python3

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report
import airport_entity_projection as projection
import render_airport_authoring as authoring


def _aip_without_boundaryless_sectors(aip: dict[str, Any]) -> dict[str, Any]:
    """Drop operational sectors that have no boundary geometry from the
    world-candidate AIP projection. The runtime `AirspaceBoundary` requires
    at least one ring, and a boundaryless sector would crash the Kotlin loader.

    Sectors without geometry still live in the structured-airport-package's
    candidate section so their authoring intent is not lost; they just don't
    propagate into the runtime-ready world candidate yet.
    """
    if not isinstance(aip, dict):
        return aip
    sectors = aip.get("operationalSectors")
    if not isinstance(sectors, dict):
        return aip
    kept = {
        sector_id: sector
        for sector_id, sector in sectors.items()
        if isinstance(sector, dict) and sector.get("boundaryPathIds")
    }
    if kept == sectors:
        return aip
    return {**aip, "operationalSectors": kept}


def _copy_point(point: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": point["id"],
        "xMeters": point["xMeters"],
        "yMeters": point["yMeters"],
        "label": point.get("label"),
        "tags": point.get("tags", []),
        "sources": point.get("sources", []),
    }


def _copy_path(path: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": path["id"],
        "pointIds": path["pointIds"],
        "surface": path["surface"],
        "widthMeters": path["widthMeters"],
        "source": path.get("source"),
        "projectionStatus": path.get("projectionStatus"),
        "note": path.get("note"),
    }


def _copy_fix(fix: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": fix["id"],
        "name": fix["name"],
        "pointId": fix["pointId"],
        "type": fix["type"],
    }


def _slugify(text: str) -> str:
    return "".join(character if character.isalnum() else "_" for character in text)


def _build_path_document(
    path_id: str,
    point_ids: list[str],
    geometry_paths: dict[str, dict[str, Any]],
    *,
    surface: str,
    width_meters: float,
    source: str,
    projection_status: str,
    note: str | None = None,
) -> dict[str, Any]:
    if path_id in geometry_paths:
        return _copy_path(geometry_paths[path_id])
    return {
        "id": path_id,
        "pointIds": point_ids,
        "surface": surface,
        "widthMeters": width_meters,
        "source": source,
        "projectionStatus": projection_status,
        "note": note,
    }


def _xy_for_point(point: dict[str, Any]) -> report.XY:
    return report.XY(float(point["xMeters"]), float(point["yMeters"]))


def _point_on_segment(
    point: report.XY,
    start: report.XY,
    end: report.XY,
    *,
    tolerance: float = 1e-6,
) -> bool:
    cross = (point.y - start.y) * (end.x - start.x) - (point.x - start.x) * (end.y - start.y)
    if abs(cross) > tolerance:
        return False
    dot = (point.x - start.x) * (end.x - start.x) + (point.y - start.y) * (end.y - start.y)
    if dot < -tolerance:
        return False
    squared_length = (end.x - start.x) ** 2 + (end.y - start.y) ** 2
    if dot - squared_length > tolerance:
        return False
    return True


def _point_in_ring(point: report.XY, ring: list[report.XY]) -> bool:
    if len(ring) < 3:
        return False
    if any(_point_on_segment(point, start, end) for start, end in zip(ring, ring[1:] + ring[:1])):
        return True

    inside = False
    for start, end in zip(ring, ring[1:] + ring[:1]):
        if (start.y > point.y) == (end.y > point.y):
            continue
        crossing_x = (end.x - start.x) * (point.y - start.y) / (end.y - start.y) + start.x
        if point.x < crossing_x:
            inside = not inside
    return inside


def _point_in_any_ring(point: report.XY, rings: list[list[report.XY]]) -> bool:
    return any(_point_in_ring(point, ring) for ring in rings)


def _heading_unit(heading_degrees: float) -> report.XY:
    radians = math.radians(heading_degrees)
    return report.XY(math.sin(radians), math.cos(radians))


def _scaled(point: report.XY, scalar: float) -> report.XY:
    return report.XY(point.x * scalar, point.y * scalar)


def _translated(point: report.XY, delta: report.XY) -> report.XY:
    return report.XY(point.x + delta.x, point.y + delta.y)


def _left_normal(vector: report.XY) -> report.XY:
    return report.XY(-vector.y, vector.x)


def _candidate_airspace_rings(
    volume: dict[str, Any],
    geometry_points: dict[str, dict[str, Any]],
) -> list[list[report.XY]]:
    rings: list[list[report.XY]] = []
    for ring in volume.get("boundaryPointIds", []):
        if not isinstance(ring, list):
            continue
        ring_points = [
            _xy_for_point(geometry_points[point_id])
            for point_id in ring
            if isinstance(point_id, str) and point_id in geometry_points
        ]
        if len(ring_points) >= 3:
            rings.append(ring_points)
    return rings


def _candidate_altitude_boundary(
    value: Any,
    unit: Any,
    reference: Any,
) -> dict[str, Any] | None:
    if reference == "HEI" and value == 0:
        return {"kind": "SURFACE"}
    if not isinstance(value, (int, float)):
        return None
    level_type = {
        ("FT", "ALT"): "ALTITUDE_FEET",
        ("FT", "HEI"): "HEIGHT_FEET",
        ("FL", "STD"): "FLIGHT_LEVEL",
    }.get((unit, reference))
    if level_type is None:
        return None
    return {
        "kind": "AT_LEVEL",
        "levelType": level_type,
        "value": int(value),
    }


def _candidate_altitude_band(volume: dict[str, Any]) -> dict[str, Any] | None:
    lower = _candidate_altitude_boundary(
        volume.get("lowerValue"),
        volume.get("lowerUnit"),
        volume.get("lowerReference"),
    )
    upper = _candidate_altitude_boundary(
        volume.get("upperValue"),
        volume.get("upperUnit"),
        volume.get("upperReference"),
    )
    if lower is None:
        return None
    return {
        "lower": lower,
        "upper": upper,
    }


def _altitude_sort_key(volume: dict[str, Any]) -> tuple[int, int]:
    reference = volume.get("lowerReference")
    unit = volume.get("lowerUnit")
    value = volume.get("lowerValue")
    numeric_value = int(value) if isinstance(value, (int, float)) else 999999
    if reference == "HEI" and numeric_value == 0:
        return (0, 0)
    if unit == "FT" and reference in {"ALT", "HEI"}:
        return (1, numeric_value)
    if unit == "FL" and reference == "STD":
        return (2, numeric_value)
    return (3, numeric_value)


def _low_level_runtime_airspace_volumes(
    candidate_airspace_volumes: dict[str, dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    grouped: dict[str, list[dict[str, Any]]] = {}
    for volume in candidate_airspace_volumes.values():
        if not isinstance(volume, dict):
            continue
        if not isinstance(volume.get("volumeType"), str) or not isinstance(volume.get("airspaceClass"), str):
            continue
        base_code_id = str(volume.get("baseCodeId") or volume.get("codeId") or volume.get("id"))
        grouped.setdefault(base_code_id, []).append(volume)

    selected: dict[str, dict[str, Any]] = {}
    for base_code_id, volumes in grouped.items():
        lowest = min(volumes, key=_altitude_sort_key)
        selected[str(lowest["id"])] = lowest
    return dict(sorted(selected.items()))


def _boundary_path_documents(
    airport_code: str,
    candidate_airspace_volumes: dict[str, dict[str, Any]],
    geometry_paths: dict[str, dict[str, Any]],
) -> tuple[dict[str, dict[str, Any]], dict[str, list[str]]]:
    path_documents: dict[str, dict[str, Any]] = {}
    path_ids_by_volume: dict[str, list[str]] = {}
    for volume_id, volume in sorted(candidate_airspace_volumes.items()):
        boundary_path_ids: list[str] = []
        for index, ring in enumerate(volume.get("boundaryPointIds", []), start=1):
            if not isinstance(ring, list):
                continue
            point_ids = [point_id for point_id in ring if isinstance(point_id, str)]
            if len(point_ids) < 3:
                continue
            if point_ids[0] != point_ids[-1]:
                point_ids = point_ids + [point_ids[0]]
            path_id = f"{airport_code}_AIRSPACE_{_slugify(volume_id).upper()}_{index:02d}"
            path_documents[path_id] = _build_path_document(
                path_id,
                point_ids,
                geometry_paths,
                surface="SKY",
                width_meters=authoring.AIRSPACE_STROKE_WIDTH_METERS if hasattr(authoring, "AIRSPACE_STROKE_WIDTH_METERS") else 160.0,
                source="candidate_airspace_boundary",
                projection_status="direct_runtime_boundary",
                note=str(volume.get("note") or "Projected runtime airspace boundary from candidate geometry."),
            )
            boundary_path_ids.append(path_id)
        path_ids_by_volume[volume_id] = boundary_path_ids
    return path_documents, path_ids_by_volume


def _fallback_fir_id(manifest: dict[str, Any], airport_code: str) -> str:
    configured = manifest.get("firId")
    if isinstance(configured, str) and configured.strip():
        return configured.strip().upper()
    return {
        "LOWG": "LOVV",
        "LJMB": "LJLA",
    }.get(airport_code, f"{airport_code}_FIR")


def _projected_current_core_airspace(
    *,
    airport_code: str,
    included_points: dict[str, dict[str, Any]],
    candidate_airspace_volumes: dict[str, dict[str, Any]],
    boundary_path_ids_by_volume: dict[str, list[str]],
    geometry_points: dict[str, dict[str, Any]],
    fir_id: str,
    transition_altitude_feet: int,
) -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]], list[str]]:
    projected_volumes: dict[str, dict[str, Any]] = {}

    for volume_id, volume in sorted(candidate_airspace_volumes.items()):
        rings = _candidate_airspace_rings(volume, geometry_points)
        if not rings:
            continue
        altitude_band = _candidate_altitude_band(volume)
        if altitude_band is None:
            continue

        boundary_point_ids = {
            point_id
            for ring in volume.get("boundaryPointIds", [])
            if isinstance(ring, list)
            for point_id in ring
            if isinstance(point_id, str)
        }
        member_point_ids = sorted(
            boundary_point_ids | {
                point_id
                for point_id, point in included_points.items()
                if _point_in_any_ring(_xy_for_point(point), rings)
            }
        )
        if not member_point_ids:
            continue

        projected_volumes[volume_id] = {
            "id": volume_id,
            "name": str(volume.get("name") or volume_id),
            "type": str(volume["volumeType"]),
            "airspaceClass": str(volume["airspaceClass"]),
            "altitudeBand": altitude_band,
            "memberPointIds": member_point_ids,
            "firId": fir_id,
            "boundaryPathIds": boundary_path_ids_by_volume.get(volume_id, []),
            "projectionStatus": "direct_low_level_runtime_airspace_volume",
            "note": volume.get("note"),
        }

    covered_points = {
        point_id
        for volume in projected_volumes.values()
        for point_id in volume["memberPointIds"]
    }
    uncovered_points = sorted(set(included_points) - covered_points)
    assumptions: list[str] = []
    if uncovered_points:
        fallback_volume_id = f"{fir_id}_OPEN_FIR_G"
        projected_volumes[fallback_volume_id] = {
            "id": fallback_volume_id,
            "name": f"{fir_id} open-FIR fallback coverage",
            "type": "FIR",
            "airspaceClass": "G",
            "altitudeBand": {
                "lower": {"kind": "SURFACE"},
                "upper": {
                    "kind": "AT_LEVEL",
                    "levelType": "ALTITUDE_FEET",
                    "value": transition_altitude_feet,
                },
            },
            "memberPointIds": uncovered_points,
            "firId": fir_id,
            "boundaryPathIds": [],
            "projectionStatus": "synthetic_open_fir_fallback",
            "note": "Fallback open-FIR membership for projected points not yet covered by the worked low-level controlled volumes.",
        }
        assumptions.append(
            f"{len(uncovered_points)} projected {airport_code} point(s) still fall outside the worked low-level CTR/TMA slice and are temporarily assigned to an explicit open-FIR Class G fallback volume."
        )

    firs = {
        fir_id: {
            "id": fir_id,
            "name": f"{fir_id} FIR ({airport_code} worked airspace subset)",
            "volumeIds": sorted(projected_volumes.keys()),
            "projectionStatus": "current_core_worked_subset",
        }
    }
    return projected_volumes, firs, assumptions


def _nearest_path_point_id(
    point_ids: list[str],
    points: dict[str, dict[str, Any]],
    target: report.XY,
) -> str:
    return min(
        point_ids,
        key=lambda point_id: _xy_for_point(points[point_id]).distance_to(target),
    )


def _first_matching_sign_point(
    context: authoring.SceneContext,
    *substrings: str,
) -> report.XY | None:
    lowered = tuple(substring.lower() for substring in substrings)
    for sign in context.taxi_signs:
        raw = sign.raw_text.lower()
        if all(substring in raw for substring in lowered):
            return sign.point
    return None


def _published_point_reference_point_id(reference: dict[str, Any] | None) -> str | None:
    if not isinstance(reference, dict):
        return None
    point_id = reference.get("pointId")
    return point_id if isinstance(point_id, str) else None


def _contact_requirement_point_id(requirement: dict[str, Any] | None) -> str | None:
    if not isinstance(requirement, dict):
        return None
    timing = requirement.get("timing")
    if not isinstance(timing, dict):
        return None
    point_id = timing.get("pointId")
    return point_id if isinstance(point_id, str) else None


def _operational_sector_anchor_point_id(anchor: dict[str, Any] | None) -> str | None:
    if not isinstance(anchor, dict):
        return None
    point_id = anchor.get("pointId")
    return point_id if isinstance(point_id, str) else None


def _communication_failure_point_ids(communication_failure: dict[str, Any] | None) -> list[str]:
    if not isinstance(communication_failure, dict):
        return []
    return [
        point_id
        for item in communication_failure.get("afterContactEstablishedExitSequence", [])
        if isinstance(item, dict)
        for point_id in [_published_point_reference_point_id(item)]
        if isinstance(point_id, str)
    ]


def _projected_current_core_holding_points(
    manifest_path: Path,
    bundle: dict[str, Any],
) -> tuple[dict[str, list[dict[str, Any]]], list[str]]:
    taxiways = bundle["coreEntities"]["aerodrome"]["taxiways"]
    geometry_paths = bundle["coreEntities"]["geometry"]["paths"]
    holding_points_by_taxiway = {
        taxiway_id: []
        for taxiway_id in sorted(taxiways)
    }

    if bundle["airportCode"] != "LOWG" or "A" not in taxiways:
        assumptions: list[str] = []
        for hold_id, candidate in sorted(bundle["candidateEntities"].get("holdingPoints", {}).items()):
            if not isinstance(candidate, dict):
                continue
            point_id = candidate.get("pointId")
            runway_id = candidate.get("runwayId")
            if not isinstance(point_id, str) or not isinstance(runway_id, str):
                assumptions.append(f"{hold_id}: candidate holding point is missing pointId/runwayId.")
                continue
            holding_point = {
                "pointId": point_id,
                "name": candidate.get("name") if isinstance(candidate.get("name"), str) else runway_id,
                "type": candidate.get("type") if isinstance(candidate.get("type"), str) else "CAT_A",
                "runwayId": runway_id,
            }
            attached_taxiways = [
                taxiway_id
                for taxiway_id, taxiway in sorted(taxiways.items())
                if point_id in geometry_paths.get(taxiway.get("pathId"), {}).get("pointIds", [])
            ]
            if not attached_taxiways:
                assumptions.append(f"{hold_id}: no taxiway path contained holding point {point_id}.")
                continue
            for taxiway_id in attached_taxiways:
                holding_points_by_taxiway[taxiway_id].append(dict(holding_point))
        return holding_points_by_taxiway, assumptions

    context = authoring.build_context(manifest_path)
    geometry_points = bundle["coreEntities"]["geometry"]["points"]
    taxiway_a = taxiways["A"]
    taxiway_a_path = geometry_paths[taxiway_a["pathId"]]
    point_ids = taxiway_a_path["pointIds"]

    holding_candidates = {
        candidate["name"]: candidate
        for candidate in taxiway_a.get("holdingPoints", [])
        if candidate.get("name") is not None
    }

    assumptions: list[str] = []
    holding_points: list[dict[str, Any]] = []

    d_candidate = holding_candidates.get("D")
    if d_candidate is not None:
        holding_points.append(
            {
                "pointId": d_candidate["pointId"],
                "name": "D",
                "type": "CAT_A",
                "runwayId": "16C",
            }
        )
    else:
        assumptions.append("No snapped D-side holding marker was available for runway 16C in the current-core candidate.")

    hold_from_sign_specs = [
        ("LOWG_HOLD_A_G1_16L", "G1", "16L", ("g1_16l",)),
        ("LOWG_HOLD_A_G2_34R", "G2", "34R", ("g2_34r",)),
        ("LOWG_HOLD_A_X_16R", "X", "16R", ("rwy_16r-34l",)),
        ("LOWG_HOLD_A_X_34L", "X", "34L", ("rwy_16r-34l",)),
        ("LOWG_HOLD_A_Y_34C", "Y", "34C", ("y", "rwy_34c")),
    ]

    seen_sign_positions: dict[tuple[str, ...], str] = {}
    for hold_id, name, runway_id, sign_tokens in hold_from_sign_specs:
        if sign_tokens not in seen_sign_positions:
            sign_point = _first_matching_sign_point(context, *sign_tokens)
            if sign_point is None:
                assumptions.append(
                    f"No taxi-sign evidence matched {' + '.join(sign_tokens)} for runway {runway_id}."
                )
                continue
            seen_sign_positions[sign_tokens] = _nearest_path_point_id(point_ids, geometry_points, sign_point)
        point_id = seen_sign_positions[sign_tokens]
        holding_points.append(
            {
                "pointId": point_id,
                "name": name,
                "type": "CAT_A",
                "runwayId": runway_id,
            }
        )

    holding_points_by_taxiway["A"] = holding_points
    return holding_points_by_taxiway, assumptions


def _candidate_waypoint_altitude_constraint(
    candidate: dict[str, Any] | None,
) -> dict[str, Any] | None:
    if not isinstance(candidate, dict):
        return None
    altitude_1 = candidate.get("altitude1Feet")
    altitude_2 = candidate.get("altitude2Feet")
    raw_type = candidate.get("rawType")
    if not isinstance(altitude_1, int):
        return None
    if raw_type == "+":
        return {"kind": "AT_OR_ABOVE", "minimumFeet": altitude_1}
    if raw_type == "-":
        return {"kind": "AT_OR_BELOW", "maximumFeet": altitude_1}
    if isinstance(altitude_2, int) and altitude_1 != altitude_2:
        return {
            "kind": "BETWEEN",
            "minimumFeet": min(altitude_1, altitude_2),
            "maximumFeet": max(altitude_1, altitude_2),
        }
    return {"kind": "AT", "valueFeet": altitude_1}


def _candidate_waypoint_speed_constraint(
    candidate: dict[str, Any] | None,
) -> dict[str, Any] | None:
    if not isinstance(candidate, dict):
        return None
    speed_knots = candidate.get("speedKnots")
    raw_type = candidate.get("rawType")
    if not isinstance(speed_knots, int):
        return None
    if raw_type == "+":
        return {"kind": "AT_OR_ABOVE", "minimumKnots": speed_knots}
    if raw_type == "-":
        return {"kind": "AT_OR_BELOW", "maximumKnots": speed_knots}
    return {"kind": "AT", "valueKnots": speed_knots}


def _runtime_ifr_subset(
    airport_code: str,
    *,
    core_entities: dict[str, Any],
    candidate_entities: dict[str, Any],
) -> tuple[
    dict[str, dict[str, Any]],
    dict[str, dict[str, Any]],
    dict[str, dict[str, Any]],
    dict[str, dict[str, Any]],
    dict[str, dict[str, Any]],
    dict[str, dict[str, Any]],
    dict[str, dict[str, Any]],
    list[str],
]:
    ifr_procedures = candidate_entities.get("ifrProcedures", {})
    tower_scope = ifr_procedures.get("towerScopeApproaches", {})
    approaches_by_name = tower_scope.get("byName", {})
    holding_candidates = tower_scope.get("holdingPatternCandidates", {}).get("byId", {})
    sid_candidates = ifr_procedures.get("sidCandidates", {}).get("byId", {})
    star_candidates = ifr_procedures.get("starCandidates", {}).get("byId", {})
    runways = core_entities["aerodrome"]["runways"]
    existing_fixes = {
        fix_id: _copy_fix(fix)
        for fix_id, fix in core_entities.get("fixes", {}).items()
    }
    points: dict[str, dict[str, Any]] = {}
    paths: dict[str, dict[str, Any]] = {}
    fixes: dict[str, dict[str, Any]] = {}
    sids: dict[str, dict[str, Any]] = {}
    stars: dict[str, dict[str, Any]] = {}
    approaches: dict[str, dict[str, Any]] = {}
    assumptions: list[str] = []

    point_ids_by_fix_name = {
        fix.get("name"): fix.get("pointId")
        for fix in existing_fixes.values()
        if isinstance(fix.get("name"), str) and isinstance(fix.get("pointId"), str)
    }

    def ensure_point(point_id: str, xy: report.XY, *, label: str, source: str) -> None:
        if point_id in points or point_id in core_entities["geometry"]["points"]:
            return
        points[point_id] = {
            "id": point_id,
            "xMeters": round(xy.x, 6),
            "yMeters": round(xy.y, 6),
            "label": label,
            "tags": ["ifr"],
            "sources": [source],
        }

    def ensure_fix(fix_id: str, point_id: str, *, fix_type: str = "WAYPOINT") -> None:
        if fix_id in existing_fixes or fix_id in fixes:
            return
        fixes[fix_id] = {
            "id": fix_id,
            "name": fix_id,
            "pointId": point_id,
            "type": fix_type,
        }

    def point_id_for_leg(approach_name: str, leg: dict[str, Any]) -> str | None:
        identifier = leg.get("fixIdentifier")
        if isinstance(identifier, str) and identifier.startswith("RW"):
            runway_id = identifier[2:]
            runway = runways.get(runway_id)
            if isinstance(runway, dict):
                return runway["thresholdPointId"]

        if isinstance(identifier, str) and identifier in point_ids_by_fix_name:
            return point_ids_by_fix_name[identifier]

        position = leg.get("position")
        if not isinstance(position, dict):
            return None
        local_position = position.get("localPosition")
        if not isinstance(local_position, dict):
            return None
        xy = report.XY(float(local_position["xMeters"]), float(local_position["yMeters"]))
        point_id = (
            f"{airport_code}_IFR_FIX_{_slugify(identifier).upper()}"
            if isinstance(identifier, str)
            else f"{airport_code}_IFR_{_slugify(approach_name).upper()}_{leg['sequence']}"
        )
        ensure_point(
            point_id,
            xy,
            label=identifier or f"{approach_name} {leg['sequence']}",
            source=str(position.get("sourceKind") or "candidate_ifr_leg"),
        )
        if isinstance(identifier, str) and not identifier.startswith("RW"):
            fix_type = (
                "NDB" if identifier == "GBG"
                else "VOR" if identifier == "GRZ"
                else "WAYPOINT"
            )
            ensure_fix(identifier, point_id, fix_type=fix_type)
            point_ids_by_fix_name[identifier] = point_id
        return point_id

    def candidate_waypoint(approach_name: str, leg: dict[str, Any]) -> dict[str, Any] | None:
        point_id = point_id_for_leg(approach_name, leg)
        if point_id is None:
            return None
        return {
            "pointId": point_id,
            "name": leg.get("fixIdentifier") or f"{approach_name}_{leg['sequence']}",
            "altitudeConstraint": _candidate_waypoint_altitude_constraint(leg.get("altitudeConstraint")),
            "speedConstraint": _candidate_waypoint_speed_constraint(leg.get("speedConstraint")),
        }

    def route_waypoints(route_name: str, candidate_waypoint_legs: list[dict[str, Any]]) -> list[dict[str, Any]]:
        waypoints = [
            waypoint
            for leg in candidate_waypoint_legs
            if isinstance(leg, dict)
            for waypoint in [candidate_waypoint(route_name, leg)]
            if waypoint is not None
        ]
        deduped_waypoints: list[dict[str, Any]] = []
        for waypoint in waypoints:
            if deduped_waypoints and deduped_waypoints[-1]["pointId"] == waypoint["pointId"]:
                continue
            deduped_waypoints.append(waypoint)
        return deduped_waypoints

    def selected_transition_legs(candidate: dict[str, Any]) -> tuple[str | None, list[dict[str, Any]]]:
        runtime_policy = candidate.get("runtimeProjectionPolicy")
        if not isinstance(runtime_policy, dict):
            return None, []
        selected_transition = runtime_policy.get("selectedTransition")
        if not isinstance(selected_transition, str):
            return None, []
        transition_legs_by_name = candidate.get("transitionLegsByName")
        if not isinstance(transition_legs_by_name, dict):
            return selected_transition, []
        legs = transition_legs_by_name.get(selected_transition)
        if not isinstance(legs, list):
            return selected_transition, []
        return selected_transition, [leg for leg in legs if isinstance(leg, dict)]

    holding_candidate = holding_candidates.get("LOWG_GBG_MISSED_HOLD")
    holding_patterns: dict[str, dict[str, Any]] = {}
    if isinstance(holding_candidate, dict):
        position = holding_candidate.get("position")
        if isinstance(position, dict) and isinstance(position.get("localPosition"), dict):
            fix_point_id = point_id_for_leg(
                "LOWG_GBG_MISSED_HOLD",
                {
                    "fixIdentifier": holding_candidate.get("fixIdentifier"),
                    "position": position,
                    "sequence": "FIX",
                },
            )
            if isinstance(fix_point_id, str):
                fix_xy = _xy_for_point(points.get(fix_point_id, core_entities["geometry"]["points"].get(fix_point_id)))
                inbound = _heading_unit(float(holding_candidate.get("inboundTrackMagneticDegrees") or 0.0))
                side = _left_normal(inbound)
                if holding_candidate.get("turnDirection") == "RIGHT":
                    side = _scaled(side, -1.0)
                outbound = _scaled(inbound, -1.0)
                leg_length_meters = (
                    float(holding_candidate["legDistanceNm"]) * 1852.0
                    if isinstance(holding_candidate.get("legDistanceNm"), (int, float))
                    else 3000.0
                )
                radius_meters = 900.0
                loop_points_xy = [
                    fix_xy,
                    _translated(fix_xy, _scaled(side, radius_meters)),
                    _translated(
                        _translated(fix_xy, _scaled(side, radius_meters)),
                        _scaled(outbound, leg_length_meters),
                    ),
                    _translated(
                        _translated(fix_xy, _scaled(side, -radius_meters)),
                        _scaled(outbound, leg_length_meters),
                    ),
                    _translated(fix_xy, _scaled(side, -radius_meters)),
                    fix_xy,
                ]
                loop_point_ids = [fix_point_id]
                for index, xy in enumerate(loop_points_xy[1:-1], start=1):
                    point_id = f"{airport_code}_IFR_HOLD_GBG_LOOP_{index:02d}"
                    ensure_point(
                        point_id,
                        xy,
                        label=f"GBG hold {index}",
                        source="compiled_nominal_holding_loop",
                    )
                    loop_point_ids.append(point_id)
                loop_point_ids.append(fix_point_id)
                loop_path_id = f"{airport_code}_IFR_HOLD_GBG_LOOP"
                paths[loop_path_id] = {
                    "id": loop_path_id,
                    "pointIds": loop_point_ids,
                    "surface": "SKY",
                    "widthMeters": 1600.0,
                    "source": "compiled_nominal_holding_loop",
                    "projectionStatus": "current_core_runtime_ifr_subset",
                    "note": "Nominal GBG missed-approach holding racetrack compiled from inbound course and published one-minute hold timing.",
                }
                holding_patterns["LOWG_GBG_MISSED_HOLD"] = {
                    "id": "LOWG_GBG_MISSED_HOLD",
                    "fixId": "GBG",
                    "inboundCourseDegrees": float(holding_candidate.get("inboundTrackMagneticDegrees") or 0.0),
                    "turnDirection": str(holding_candidate.get("turnDirection") or "LEFT"),
                    "loopPathId": loop_path_id,
                    "legTimeMinutes": int(round(float(holding_candidate.get("legTimeMinutes") or 1.0))),
                    "altitudeFeet": int(holding_candidate.get("minimumAltitudeFeet") or 5000),
                    "projectionStatus": "current_core_runtime_ifr_subset",
                }
                assumptions.append(
                    "LOWG GBG missed-approach hold loop geometry is a nominal compiled racetrack from the published inbound track and one-minute hold timing, because the current runtime model requires explicit loop geometry."
                )

    for sid_id, candidate in sorted(sid_candidates.items()):
        if not isinstance(candidate, dict):
            continue
        if candidate.get("runtimeProjectionBlockers") or candidate.get("unresolvedFixIdentifiers"):
            continue
        runway_id = candidate.get("runwayId")
        if not isinstance(runway_id, str) or runway_id not in runways:
            continue
        waypoints = route_waypoints(str(candidate.get("name") or sid_id), candidate.get("waypoints", []))
        if not waypoints:
            continue
        runway_threshold_point_id = runways.get(runway_id, {}).get("thresholdPointId")
        if (
            isinstance(runway_threshold_point_id, str) and
            waypoints[0].get("pointId") != runway_threshold_point_id
        ):
            waypoints = [
                {
                    "pointId": runway_threshold_point_id,
                    "name": f"RW{runway_id}",
                    "altitudeConstraint": None,
                    "speedConstraint": None,
                },
                *waypoints,
            ]
            assumptions.append(
                f"{sid_id}: Runtime SID projection prepends the runway threshold as the first waypoint when CIFP starts at the first post-departure fix."
            )
        sids[sid_id] = {
            "id": sid_id,
            "name": candidate.get("name") or sid_id,
            "runwayId": runway_id,
            "waypoints": waypoints,
            "transitions": {},
            "projectionStatus": "current_core_runtime_ifr_subset",
        }
        if len(waypoints) >= 2:
            sid_path_id = f"{sid_id}_PATH"
            paths[sid_path_id] = {
                "id": sid_path_id,
                "pointIds": [waypoint["pointId"] for waypoint in waypoints],
                "surface": "SKY",
                "widthMeters": 1200.0,
                "source": "compiled_runtime_ifr_sid_chain",
                "projectionStatus": "current_core_runtime_ifr_subset",
                "note": f"Compiled SID geometry chain for {sid_id}.",
            }
        for assumption in candidate.get("compilationAssumptions", []):
            if isinstance(assumption, str):
                assumptions.append(f"{sid_id}: {assumption}")

    for procedure_name, candidate in sorted(approaches_by_name.items()):
        if not isinstance(candidate, dict):
            continue
        runtime_policy = candidate.get("runtimeProjectionPolicy")
        if not isinstance(runtime_policy, dict):
            continue
        hold_id = candidate.get("missedApproachHoldCandidateId")
        if hold_id not in holding_patterns:
            continue

        selected_transition, feeder_legs = selected_transition_legs(candidate)
        deduped_waypoints = route_waypoints(
            procedure_name,
            [
                *feeder_legs,
                *[
                    leg
                    for leg in candidate.get("finalApproachLegs", [])
                    if isinstance(leg, dict)
                ],
            ],
        )
        if not deduped_waypoints:
            continue
        runway = runways.get(candidate["runwayId"])
        threshold_point_id = runway["thresholdPointId"] if isinstance(runway, dict) else None
        if (
            isinstance(threshold_point_id, str) and
            deduped_waypoints[-1]["pointId"] != threshold_point_id
        ):
            deduped_waypoints.append(
                {
                    "pointId": threshold_point_id,
                    "name": f"RW{candidate['runwayId']}",
                    "altitudeConstraint": None,
                    "speedConstraint": None,
                }
            )

        missed_waypoints = [
            waypoint
            for leg in candidate.get("missedApproachLegs", [])
            if isinstance(leg, dict) and leg.get("pathTerminator") != "HM"
            for waypoint in [candidate_waypoint(procedure_name, leg)]
            if waypoint is not None
        ]
        threshold_waypoint = {
            "pointId": deduped_waypoints[-1]["pointId"],
            "name": deduped_waypoints[-1]["name"],
            "altitudeConstraint": None,
            "speedConstraint": None,
        }
        if not missed_waypoints or missed_waypoints[0]["pointId"] != threshold_waypoint["pointId"]:
            missed_waypoints = [threshold_waypoint] + missed_waypoints

        approach_id = {
            "D16C": "LOWG_VOR_16C",
            "D34C": "LOWG_VOR_34C",
            "R16C": "LOWG_RNP_16C",
            "R34C": "LOWG_RNP_34C",
            "I34C": "LOWG_ILS_34C",
        }.get(procedure_name)
        if approach_id is None:
            continue
        if feeder_legs:
            assumptions.append(
                f"{approach_id}: Runtime IFR projection prepends feeder transition {selected_transition} so the "
                "selected LOWG STAR terminal fix is shared with the approach entry point set."
            )
        approaches[approach_id] = {
            "id": approach_id,
            "name": {
                "D16C": "VOR RWY 16C",
                "D34C": "VOR RWY 34C",
                "R16C": "RNP RWY 16C",
                "R34C": "RNP RWY 34C",
                "I34C": "ILS RWY 34C",
            }[procedure_name],
            "type": runtime_policy["selectedApproachType"],
            "runwayId": candidate["runwayId"],
            "waypoints": deduped_waypoints,
            "minimumAltitude": runtime_policy["minimum"],
            "missedApproach": {
                "waypoints": missed_waypoints,
                "holdAtId": str(hold_id),
            },
            "projectionStatus": "current_core_runtime_ifr_subset",
            "note": runtime_policy.get("selectionNote"),
        }
        final_path_id = f"{approach_id}_FINAL"
        if len(deduped_waypoints) >= 2:
            paths[final_path_id] = {
                "id": final_path_id,
                "pointIds": [waypoint["pointId"] for waypoint in deduped_waypoints],
                "surface": "SKY",
                "widthMeters": 1200.0,
                "source": "compiled_runtime_ifr_approach_chain",
                "projectionStatus": "current_core_runtime_ifr_subset",
                "note": f"Compiled final approach geometry chain for {approach_id}.",
            }
        missed_path_id = f"{approach_id}_MISSED"
        if len(missed_waypoints) >= 2:
            paths[missed_path_id] = {
                "id": missed_path_id,
                "pointIds": [waypoint["pointId"] for waypoint in missed_waypoints],
                "surface": "SKY",
                "widthMeters": 1200.0,
                "source": "compiled_runtime_ifr_missed_chain",
                "projectionStatus": "current_core_runtime_ifr_subset",
                "note": f"Compiled missed-approach geometry chain for {approach_id}.",
            }

    holding_fix_ids = {
        holding_pattern["fixId"]
        for holding_pattern in holding_patterns.values()
        if isinstance(holding_pattern, dict) and isinstance(holding_pattern.get("fixId"), str)
    }
    holding_fix_point_ids = {
        fix.get("pointId")
        for fix_id, fix in {**existing_fixes, **fixes}.items()
        if fix_id in holding_fix_ids and isinstance(fix, dict) and isinstance(fix.get("pointId"), str)
    }
    approach_entry_point_ids = {
        waypoints[0]["pointId"]
        for approach in approaches.values()
        if isinstance(approach, dict)
        for waypoints in [approach.get("waypoints", [])]
        if isinstance(waypoints, list) and waypoints
        if isinstance(waypoints[0], dict) and isinstance(waypoints[0].get("pointId"), str)
    }

    for star_id, candidate in sorted(star_candidates.items()):
        if not isinstance(candidate, dict):
            continue
        if candidate.get("runtimeProjectionBlockers") or candidate.get("unresolvedFixIdentifiers"):
            continue
        waypoints = route_waypoints(str(candidate.get("name") or star_id), candidate.get("waypoints", []))
        if not waypoints:
            continue
        terminal_point_id = waypoints[-1]["pointId"]
        if terminal_point_id not in approach_entry_point_ids and terminal_point_id not in holding_fix_point_ids:
            assumptions.append(
                f"{star_id}: Runtime STAR projection skipped because terminal point {terminal_point_id} is not yet "
                "shared with any projected approach entry point or holding fix."
            )
            continue
        stars[star_id] = {
            "id": star_id,
            "name": candidate.get("name") or star_id,
            "waypoints": waypoints,
            "transitions": {},
            "projectionStatus": "current_core_runtime_ifr_subset",
        }
        if len(waypoints) >= 2:
            star_path_id = f"{star_id}_PATH"
            paths[star_path_id] = {
                "id": star_path_id,
                "pointIds": [waypoint["pointId"] for waypoint in waypoints],
                "surface": "SKY",
                "widthMeters": 1200.0,
                "source": "compiled_runtime_ifr_star_chain",
                "projectionStatus": "current_core_runtime_ifr_subset",
                "note": f"Compiled STAR geometry chain for {star_id}.",
            }
        for assumption in candidate.get("compilationAssumptions", []):
            if isinstance(assumption, str):
                assumptions.append(f"{star_id}: {assumption}")

    return points, paths, fixes, sids, stars, approaches, holding_patterns, assumptions


def build_world_candidate(manifest_path: Path) -> dict[str, Any]:
    manifest = json.loads(manifest_path.read_text())
    bundle = projection.build_projection_bundle(manifest_path)
    core_entities = bundle["coreEntities"]
    candidate_entities = bundle["candidateEntities"]
    aerodrome = core_entities["aerodrome"]
    airport_code = bundle["airportCode"]

    geometry_points = core_entities["geometry"]["points"]
    geometry_paths = core_entities["geometry"]["paths"]
    aerodrome_aip = aerodrome.get("aip", {})
    vfr_routes = core_entities.get("vfrRoutes", {})

    route_path_ids = {
        route["pathId"]
        for route in vfr_routes.values()
        if isinstance(route, dict) and isinstance(route.get("pathId"), str)
    }
    circuit_path_ids = {
        path_id
        for circuit in aerodrome.get("circuits", {}).values()
        if isinstance(circuit, dict)
        for path_id in [
            *(leg.get("pathId") for leg in circuit.get("legs", []) if isinstance(leg, dict)),
            circuit.get("goAroundPathId"),
            *(join.get("entryPathId") for join in circuit.get("joinProcedures", []) if isinstance(join, dict)),
        ]
        if isinstance(path_id, str)
    }
    sector_boundary_path_ids = {
        path_id
        for sector in aerodrome_aip.get("operationalSectors", {}).values()
        if isinstance(sector, dict)
        for path_id in sector.get("boundaryPathIds", [])
        if isinstance(path_id, str)
    }
    candidate_airspace_volumes = _low_level_runtime_airspace_volumes(
        candidate_entities.get("airspaceVolumes", {})
    )
    runtime_ifr_points, runtime_ifr_paths, runtime_ifr_fixes, runtime_ifr_sids, runtime_ifr_stars, runtime_ifr_approaches, runtime_ifr_holding_patterns, ifr_assumptions = _runtime_ifr_subset(
        airport_code,
        core_entities=core_entities,
        candidate_entities=candidate_entities,
    )
    projected_airspace_boundary_paths, boundary_path_ids_by_volume = _boundary_path_documents(
        airport_code,
        candidate_airspace_volumes,
        geometry_paths,
    )

    included_path_ids = (
        {runway["pathId"] for runway in aerodrome["runways"].values()}
        | {taxiway["pathId"] for taxiway in aerodrome["taxiways"].values()}
        | {
            path_id
            for apron in aerodrome["aprons"].values()
            for path_id in apron["pathIds"]
        }
        | route_path_ids
        | circuit_path_ids
        | sector_boundary_path_ids
        | set(projected_airspace_boundary_paths.keys())
        | set(runtime_ifr_paths.keys())
    )
    included_paths = {
        path_id: (
            _copy_path(geometry_paths[path_id])
            if path_id in geometry_paths
            else _copy_path(runtime_ifr_paths[path_id])
            if path_id in runtime_ifr_paths
            else projected_airspace_boundary_paths[path_id]
        )
        for path_id in sorted(included_path_ids)
    }

    included_point_ids = {
        point_id
        for path in included_paths.values()
        for point_id in path["pointIds"]
    } | {
        stand["pointId"]
        for stand in aerodrome["stands"].values()
    } | {
        fix["pointId"]
        for fix in core_entities["fixes"].values()
    } | {
        point_id
        for sector in aerodrome_aip.get("operationalSectors", {}).values()
        if isinstance(sector, dict)
        for point_id in [
            _operational_sector_anchor_point_id(sector.get("anchor")),
            *sector.get("entryExitPointIds", []),
        ]
        if isinstance(point_id, str)
    } | {
        point_id
        for procedure in aerodrome_aip.get("publishedVfrProcedures", {}).values()
        if isinstance(procedure, dict)
        for point_id in [
            *(
                _published_point_reference_point_id(item)
                for item in procedure.get("publishedSequence", [])
                if isinstance(item, dict)
            ),
            *(
                _published_point_reference_point_id(item.get("location"))
                for item in procedure.get("mapLabels", [])
                if isinstance(item, dict)
            ),
            *_communication_failure_point_ids(procedure.get("communicationFailure")),
            _published_point_reference_point_id(procedure.get("terminatesAt")),
            _published_point_reference_point_id(procedure.get("holdAt")),
            _contact_requirement_point_id(procedure.get("contactRequirement")),
        ]
        if isinstance(point_id, str)
    } | {
        point_id
        for circuit in aerodrome.get("circuits", {}).values()
        if isinstance(circuit, dict)
        for point_id in [
            *(
                point_id
                for point_id in circuit.get("reportingPoints", {}).values()
                if isinstance(point_id, str)
            ),
            *(
                join.get("entryPointId")
                for join in circuit.get("joinProcedures", [])
                if isinstance(join, dict)
            ),
        ]
        if isinstance(point_id, str)
    } | {
        waypoint["pointId"]
        for sid in runtime_ifr_sids.values()
        for waypoint in sid.get("waypoints", [])
        if isinstance(waypoint, dict) and isinstance(waypoint.get("pointId"), str)
    } | {
        waypoint["pointId"]
        for star in runtime_ifr_stars.values()
        for waypoint in star.get("waypoints", [])
        if isinstance(waypoint, dict) and isinstance(waypoint.get("pointId"), str)
    } | {
        waypoint["pointId"]
        for approach in runtime_ifr_approaches.values()
        for waypoint in [
            *approach.get("waypoints", []),
            *approach.get("missedApproach", {}).get("waypoints", []),
        ]
        if isinstance(waypoint, dict) and isinstance(waypoint.get("pointId"), str)
    } | {
        fix["pointId"]
        for fix in runtime_ifr_fixes.values()
        if isinstance(fix, dict) and isinstance(fix.get("pointId"), str)
    }
    included_points = {
        point_id: (
            _copy_point(geometry_points[point_id])
            if point_id in geometry_points
            else _copy_point(runtime_ifr_points[point_id])
        )
        for point_id in sorted(included_point_ids)
    }

    transition_altitude_feet = int(aerodrome.get("transitionAltitudeFeet") or 10000)
    fir_id = _fallback_fir_id(manifest, airport_code)
    projected_airspace_volumes, projected_firs, airspace_assumptions = _projected_current_core_airspace(
        airport_code=airport_code,
        included_points=included_points,
        candidate_airspace_volumes=candidate_airspace_volumes,
        boundary_path_ids_by_volume=boundary_path_ids_by_volume,
        geometry_points=geometry_points,
        fir_id=fir_id,
        transition_altitude_feet=transition_altitude_feet,
    )

    holding_points_by_taxiway, holding_assumptions = _projected_current_core_holding_points(
        manifest_path,
        bundle,
    )

    world_taxiways = {
        taxiway_id: {
            "id": taxiway["id"],
            "name": taxiway["name"],
            "pathId": taxiway["pathId"],
            "bidirectional": taxiway.get("bidirectional", True),
            "holdingPoints": holding_points_by_taxiway.get(taxiway_id, []),
            "projectionStatus": taxiway.get("projectionStatus"),
            "note": taxiway.get("note"),
        }
        for taxiway_id, taxiway in sorted(aerodrome["taxiways"].items())
    }

    apron_projection_statuses = {
        apron.get("projectionStatus")
        for apron in aerodrome.get("aprons", {}).values()
    }
    if "direct_authored_parking_graph_with_direct_taxiway_a_attachments" in apron_projection_statuses:
        apron_access_assumption = (
            "Apron access now uses the authored NEW_Parking graph and NEW_Parking_Points stand set, "
            "with direct authored attachments onto taxiway A from the working DXF."
        )
    elif "direct_authored_parking_graph_with_projected_taxiway_joins" in apron_projection_statuses:
        apron_access_assumption = (
            "Apron access now uses the authored NEW_Parking graph and NEW_Parking_Points stand set, "
            "but the joins back to taxiway A are still projected for version 1."
        )
    elif "direct_authored_parking_graph_without_taxiway_a_attachment" in apron_projection_statuses:
        apron_access_assumption = (
            "Apron access now uses the authored NEW_Parking graph and NEW_Parking_Points stand set, "
            "but no taxiway-A attachment could be derived from the authored apron geometry."
        )
    elif "direct_authored_ground_graph" in apron_projection_statuses:
        apron_access_assumption = (
            "Apron and stand access now use the authored working-DXF ground graph and authored stand points."
        )
    else:
        apron_access_assumption = (
            "Apron access remains on the reference parking projection path because no authored NEW_Parking graph is active."
        )

    forced_assumptions = [
        f"{airport_code} current-core airspace membership is projected from explicit low-level point-to-volume assignment over worked boundary geometry. This remains a 2D plan-view approximation, not altitude-aware polygon reasoning.",
        apron_access_assumption,
    ]
    if any(
        isinstance(volume, dict)
        and volume.get("codeType") == "CLASS"
        and isinstance(volume.get("baseCodeId"), str)
        and volume.get("baseCodeId") != volume.get("codeId")
        for volume in candidate_airspace_volumes.values()
    ):
        forced_assumptions.append(
            "Class-layer airspace volumes reuse their parent CTR/TMA boundary geometry where the class records carry no separate boundary of their own."
        )
    if airport_code == "LOWG":
        forced_assumptions.extend(
            [
                "Taxiway A remains a mixed D->A cluster in the projected world candidate because the current core model has no partial-taxiway naming.",
                "Current-core runway-protection holding points are projected only onto the connected taxiway-A spine because the present validator requires every holding point to reach every stand through the ground graph.",
                "The current-core holding-point projection uses the snapped D marker for runway 16C, the G1/G2 sign for 16L/34R, the X sign for 16R/34L, and the Y sign for 34C, all snapped to existing A-path vertices rather than importing the disconnected side-runway hold positions directly.",
            ],
        )
    elif any(holding_points_by_taxiway.values()):
        forced_assumptions.append(
            "Current-core holding points are attached to whichever taxiway paths already contain the authored holding-point geometry. This remains a path-level assignment, not a richer runway-entry area model."
        )
    forced_assumptions.extend(airspace_assumptions)
    forced_assumptions.extend(holding_assumptions)
    forced_assumptions.extend(ifr_assumptions)
    if runtime_ifr_approaches:
        forced_assumptions.append(
            f"{airport_code} runtime IFR approaches use waypoint-only final and missed-approach sequences. "
            "Altitude-only and DME-triggered CIFP legs are collapsed to the current-core waypoint model where necessary."
        )
    if runtime_ifr_sids:
        forced_assumptions.append(
            f"{airport_code} runtime SID projection compiles leading fixless climb legs as runway-threshold waypoints where necessary, because the current SID model is waypoint-based rather than path-terminator based."
        )
    if runtime_ifr_stars:
        forced_assumptions.append(
            f"{airport_code} runtime STAR projection currently uses the published STAR trunks only, and relies on explicitly selected feeder transitions on the projected approach subset so STAR terminal fixes are shared with the approach entry-point set."
        )
    omitted_features = [
        "Only runtime-usable classed low-level airspace volumes are projected into the current candidate; boundary-only/special-use geometry and altitude-aware membership still remain outside the current-core subset.",
    ]
    if any(
        isinstance(route, dict) and route.get("airspaceProfile") is None
        for route in core_entities.get("vfrRoutes", {}).values()
    ):
        omitted_features.append(
            f"{airport_code} mixed boundary-crossing VFR routes still omit route airspace profiles unless they can be assigned honestly under the InVolume / InClass / Segmented model."
        )
    if runtime_ifr_sids or runtime_ifr_stars or runtime_ifr_approaches or runtime_ifr_holding_patterns:
        omitted_features.append(
            f"{airport_code} richer IFR publication variants remain outside the current-core subset where the runtime model still collapses them to a single selected minima/profile path."
        )
    if airport_code == "LOWG":
        omitted_features.extend(
            [
                "The east non-standard hold remains deferred to version 2.",
                "The disconnected B/C/Y/Z holding candidates remain in the richer entity bundle but are not imported directly into the current-core subset because they would violate the present stand-reachability validator rule.",
            ],
        )

    return {
        "airportCode": airport_code,
        "airportName": bundle["airportName"],
        "projectionStatus": "v1_current_core_fit_subset",
        "sourceManifest": bundle["sourceManifest"],
        "sourceStructuredAirportPackage": f"cad/airports/rendered/{airport_code.lower()}/structured-airport-package.json",
        "sourceEntityBundle": f"cad/airports/rendered/{airport_code.lower()}/entity-bundle.json",
        "forcedAssumptions": forced_assumptions,
        "omittedFeatures": omitted_features,
        "projectionGaps": bundle["projectionGaps"],
        "world": {
            "geometry": {
                "points": included_points,
                "paths": included_paths,
            },
            "fixes": dict(sorted({
                **core_entities["fixes"],
                **runtime_ifr_fixes,
            }.items())),
            "vfrRoutes": dict(sorted(core_entities.get("vfrRoutes", {}).items())),
            "aerodrome": {
                "icao": aerodrome["icao"],
                "name": aerodrome["name"],
                "elevationFeet": aerodrome["elevationFeet"],
                "magneticVariationDegrees": aerodrome["magneticVariationDegrees"] or 0,
                "transitionAltitudeFeet": transition_altitude_feet,
                "aip": _aip_without_boundaryless_sectors(aerodrome.get("aip", {})),
                "runways": dict(sorted(aerodrome["runways"].items())),
                "circuits": dict(sorted(aerodrome.get("circuits", {}).items())),
                "taxiways": world_taxiways,
                "stands": dict(sorted(aerodrome["stands"].items())),
                "aprons": dict(sorted(aerodrome["aprons"].items())),
                "sids": dict(sorted(runtime_ifr_sids.items())),
                "stars": dict(sorted(runtime_ifr_stars.items())),
                "approaches": dict(sorted(runtime_ifr_approaches.items())),
                "holdingPatterns": dict(sorted(runtime_ifr_holding_patterns.items())),
            },
            "airspaceVolumes": dict(sorted(projected_airspace_volumes.items())),
            "firs": dict(sorted(projected_firs.items())),
        },
    }


def candidate_json(document: dict[str, Any]) -> str:
    return json.dumps(document, indent=2, sort_keys=True) + "\n"
