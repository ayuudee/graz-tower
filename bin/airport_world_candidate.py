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
    sector_boundary_path_ids = {
        path_id
        for sector in aerodrome_aip.get("operationalSectors", {}).values()
        if isinstance(sector, dict)
        for path_id in sector.get("boundaryPathIds", [])
        if isinstance(path_id, str)
    }
    candidate_ctr = next(
        (
            airspace
            for airspace in candidate_entities.get("airspaceVolumes", {}).values()
            if isinstance(airspace, dict) and str(airspace.get("codeId")) == "LO585"
        ),
        None,
    )
    synthetic_boundary_paths = {
        f"{airport_code}_SYNTH_AIRSPACE_{index:02d}": _build_path_document(
            f"{airport_code}_SYNTH_AIRSPACE_{index:02d}",
            [point_id for point_id in ring if isinstance(point_id, str)],
            geometry_paths,
            surface="SKY",
            width_meters=authoring.AIRSPACE_STROKE_WIDTH_METERS if hasattr(authoring, "AIRSPACE_STROKE_WIDTH_METERS") else 160.0,
            source="candidate_airspace_boundary",
            projection_status="synthetic_runtime_boundary",
            note="Projected real CTR boundary paired with synthetic point-claim coverage.",
        )
        for index, ring in enumerate(candidate_ctr.get("boundaryPointIds", []) if isinstance(candidate_ctr, dict) else [], start=1)
        if isinstance(ring, list)
    }

    included_path_ids = (
        {runway["pathId"] for runway in aerodrome["runways"].values()}
        | {taxiway["pathId"] for taxiway in aerodrome["taxiways"].values()}
        | {
            path_id
            for apron in aerodrome["aprons"].values()
            for path_id in apron["pathIds"]
        }
        | route_path_ids
        | sector_boundary_path_ids
        | set(synthetic_boundary_paths.keys())
    )
    included_paths = {
        path_id: (
            _copy_path(geometry_paths[path_id])
            if path_id in geometry_paths
            else synthetic_boundary_paths[path_id]
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
    }
    included_points = {
        point_id: _copy_point(geometry_points[point_id])
        for point_id in sorted(included_point_ids)
    }

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

    transition_altitude_feet = int(aerodrome.get("transitionAltitudeFeet") or 10000)
    fir_id = "LOVV"
    synthetic_volume_id = f"{airport_code}_V1_POINT_CLAIM"
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
        "A single synthetic point-claim CTR volume still covers every projected LOWG v1 world point so validation can focus on entity fit instead of unfinished airspace membership, but it now also carries the worked LOWG CTR boundary path for runtime-owned chart geometry.",
        "Taxiway A remains a mixed D->A cluster in the projected world candidate because the current core model has no partial-taxiway naming.",
        apron_access_assumption,
        "Current-core runway-protection holding points are projected only onto the connected taxiway-A spine because the present validator requires every holding point to reach every stand through the ground graph.",
        "The current-core holding-point projection uses the snapped D marker for runway 16C, the G1/G2 sign for 16L/34R, the X sign for 16R/34L, and the Y sign for 34C, all snapped to existing A-path vertices rather than importing the disconnected side-runway hold positions directly.",
    ]
    forced_assumptions.extend(holding_assumptions)
    omitted_features = [
        "Circuit procedures are omitted from the current world candidate because the current CAD is still a shared graph and has not yet been projected into directional CircuitProcedure entities.",
        "Only the worked LOWG CTR boundary is projected into the current runtime candidate; the broader surrounding airspace set still remains outside the current-core subset until point membership is assigned honestly.",
        "LOWG mixed boundary-crossing VFR routes still omit route airspace profiles unless they can be assigned honestly under the new InVolume / InClass / Segmented model.",
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
                "taxiways": world_taxiways,
                "stands": dict(sorted(aerodrome["stands"].items())),
                "aprons": dict(sorted(aerodrome["aprons"].items())),
            },
            "syntheticAirspace": {
                "firId": fir_id,
                "firName": "LOVV FIR (LOWG v1 synthetic claim coverage)",
                "volumeId": synthetic_volume_id,
                "volumeName": "LOWG v1 projected point-claim coverage",
                "type": "CTR",
                "airspaceClass": "D",
                "upperAltitudeFeet": transition_altitude_feet,
                "memberPointIds": sorted(included_point_ids),
                "boundaryPathIds": sorted(synthetic_boundary_paths.keys()),
                "projectionStatus": "synthetic_validator_support",
                "note": "Synthetic point-claim volume used only to exercise the current validator against the projected LOWG subset.",
            },
        },
    }


def candidate_json(document: dict[str, Any]) -> str:
    return json.dumps(document, indent=2, sort_keys=True) + "\n"
