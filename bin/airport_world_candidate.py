#!/usr/bin/env python3

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report
import airport_entity_projection as projection
import render_airport_authoring as authoring


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
                note=str(volume.get("note") or "Projected LOWG airspace boundary from OFMX geometry."),
            )
            boundary_path_ids.append(path_id)
        path_ids_by_volume[volume_id] = boundary_path_ids
    return path_documents, path_ids_by_volume


def _projected_current_core_airspace(
    *,
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
        fallback_volume_id = "LOVV_OPEN_FIR_G"
        projected_volumes[fallback_volume_id] = {
            "id": fallback_volume_id,
            "name": "LOVV open-FIR fallback coverage",
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
            "note": "Fallback open-FIR membership for projected LOWG points not yet covered by the worked low-level controlled volumes.",
        }
        assumptions.append(
            f"{len(uncovered_points)} projected LOWG point(s) still fall outside the worked low-level CTR/TMA slice and are temporarily assigned to an explicit open-FIR Class G fallback volume."
        )

    firs = {
        fir_id: {
            "id": fir_id,
            "name": "LOVV FIR (LOWG worked airspace subset)",
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
) -> tuple[list[dict[str, Any]], list[str]]:
    context = authoring.build_context(manifest_path)
    geometry_points = bundle["coreEntities"]["geometry"]["points"]
    taxiway_a = bundle["coreEntities"]["aerodrome"]["taxiways"]["A"]
    taxiway_a_path = bundle["coreEntities"]["geometry"]["paths"][taxiway_a["pathId"]]
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

    return holding_points, assumptions


def build_world_candidate(manifest_path: Path) -> dict[str, Any]:
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
    )
    included_paths = {
        path_id: (
            _copy_path(geometry_paths[path_id])
            if path_id in geometry_paths
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
    }
    included_points = {
        point_id: _copy_point(geometry_points[point_id])
        for point_id in sorted(included_point_ids)
    }

    transition_altitude_feet = int(aerodrome.get("transitionAltitudeFeet") or 10000)
    fir_id = "LOVV"
    projected_airspace_volumes, projected_firs, airspace_assumptions = _projected_current_core_airspace(
        included_points=included_points,
        candidate_airspace_volumes=candidate_airspace_volumes,
        boundary_path_ids_by_volume=boundary_path_ids_by_volume,
        geometry_points=geometry_points,
        fir_id=fir_id,
        transition_altitude_feet=transition_altitude_feet,
    )

    forced_holding_points, holding_assumptions = _projected_current_core_holding_points(
        manifest_path,
        bundle,
    )

    world_taxiways = {
        taxiway_id: {
            "id": taxiway["id"],
            "name": taxiway["name"],
            "pathId": taxiway["pathId"],
            "bidirectional": taxiway.get("bidirectional", True),
            "holdingPoints": forced_holding_points if taxiway_id == "A" else [],
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
    else:
        apron_access_assumption = (
            "Apron access remains on the reference parking projection path because no authored NEW_Parking graph is active."
        )

    forced_assumptions = [
        "LOWG current-core airspace membership is now projected from the worked CTR/TMA boundary geometry as an explicit low-level point-to-volume assignment. This remains a 2D plan-view approximation, not altitude-aware polygon reasoning.",
        "OFMX class-layer volumes reuse their parent CTR/TMA boundary geometry where the class records carry no separate boundary of their own.",
        "Taxiway A remains a mixed D->A cluster in the projected world candidate because the current core model has no partial-taxiway naming.",
        apron_access_assumption,
        "Current-core runway-protection holding points are projected only onto the connected taxiway-A spine because the present validator requires every holding point to reach every stand through the ground graph.",
        "The current-core holding-point projection uses the snapped D marker for runway 16C, the G1/G2 sign for 16L/34R, the X sign for 16R/34L, and the Y sign for 34C, all snapped to existing A-path vertices rather than importing the disconnected side-runway hold positions directly.",
    ]
    forced_assumptions.extend(airspace_assumptions)
    forced_assumptions.extend(holding_assumptions)
    omitted_features = [
        "Only the worked LOWG low-level CTR/TMA slice is projected into the current runtime candidate; broader surrounding airspace and altitude-aware membership still remain outside the current-core subset.",
        "The east non-standard hold remains deferred to version 2.",
        "The disconnected B/C/Y/Z holding candidates remain in the richer entity bundle but are not imported directly into the current-core subset because they would violate the present stand-reachability validator rule.",
    ]

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
            "fixes": dict(sorted(core_entities["fixes"].items())),
            "vfrRoutes": dict(sorted(core_entities.get("vfrRoutes", {}).items())),
            "aerodrome": {
                "icao": aerodrome["icao"],
                "name": aerodrome["name"],
                "elevationFeet": aerodrome["elevationFeet"],
                "magneticVariationDegrees": aerodrome["magneticVariationDegrees"] or 0,
                "transitionAltitudeFeet": transition_altitude_feet,
                "aip": aerodrome.get("aip", {}),
                "runways": dict(sorted(aerodrome["runways"].items())),
                "circuits": dict(sorted(aerodrome.get("circuits", {}).items())),
                "taxiways": world_taxiways,
                "stands": dict(sorted(aerodrome["stands"].items())),
                "aprons": dict(sorted(aerodrome["aprons"].items())),
            },
            "airspaceVolumes": dict(sorted(projected_airspace_volumes.items())),
            "firs": dict(sorted(projected_firs.items())),
        },
    }


def candidate_json(document: dict[str, Any]) -> str:
    return json.dumps(document, indent=2, sort_keys=True) + "\n"
