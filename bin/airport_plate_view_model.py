from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import airport_authoring_report as report
import airport_structured_package as structured_package
import airport_world_candidate as current_core
import render_airport_authoring as authoring


@dataclass(frozen=True)
class RunwayRow:
    designator: str
    dimensions: str
    tora: str
    lda: str
    note: str


DEFAULT_PLATE_IDS: list[str] = [
    "AD-1", "AD-6", "AD-3", "AD-4", "AD-5",
    "PRC-1", "PRC-2", "PRC-3", "PRC-4", "PRC-5",
    "ENR-1", "AD-2", "ARR-1",
]


@dataclass(frozen=True)
class PlateViewModel:
    root: Path
    manifest_path: Path
    airport_code: str
    airport: report.OfmxAirport
    frequency_lines: list[str]
    runway_rows: list[RunwayRow]
    current_core_aerodrome: dict[str, Any]
    current_core_geometry_paths: dict[str, dict[str, Any]]
    current_core_point_lookup: dict[str, report.XY]
    current_core_path_lookup: dict[str, list[report.XY]]
    candidate_vfr_routes: dict[str, dict[str, Any]]
    candidate_published_vfr_procedures: dict[str, dict[str, Any]]
    candidate_circuit_graphs: dict[str, dict[str, Any]]
    candidate_operational_sectors: dict[str, dict[str, Any]]
    candidate_operational_sector_shapes: dict[str, authoring.AirspaceShape]
    reporting_lookup: dict[str, report.XY]
    anchor_lookup: dict[str, report.XY]
    primary_airspaces: list[authoring.AirspaceShape]
    secondary_airspaces: list[authoring.AirspaceShape]
    ctr_shape: authoring.AirspaceShape | None
    parachute_area_shape: authoring.AirspaceShape | None
    main_circuit: list[report.DxfLine]
    west_circuit: list[report.DxfLine]
    east_circuit: list[report.DxfLine]
    current_core_issue_count: int | str
    current_core_assumptions: list[str]
    projection_gaps: list[str]
    omitted_features: list[str]
    plate_ids: list[str]


def path_lines(points: list[report.XY]) -> list[report.DxfLine]:
    return [
        report.DxfLine(start=points[index], end=points[index + 1], layer="entity")
        for index in range(len(points) - 1)
    ]


def build_entity_frequency_lines(aerodrome: dict[str, Any]) -> list[str]:
    frequencies = aerodrome.get("frequencies", [])

    def sort_key(frequency: dict[str, Any]) -> tuple[int, str]:
        call_sign = str(frequency.get("callSign", "")).upper()
        if "RADAR" in call_sign:
            order = 0
        elif "ATIS" in call_sign:
            order = 1
        elif "TOWER" in call_sign:
            order = 2
        else:
            order = 3
        return (order, call_sign, str(frequency.get("frequencyMhz", "")))

    return [
        f"{frequency.get('frequencyMhz', '?')} {frequency.get('callSign', '?')}"
        for frequency in sorted(
            [item for item in frequencies if isinstance(item, dict)],
            key=sort_key,
        )
        if frequency.get("callSign") and frequency.get("frequencyMhz")
    ]


def build_entity_runway_rows(
    aerodrome: dict[str, Any],
    path_lookup: dict[str, list[report.XY]],
    geometry_paths: dict[str, dict[str, Any]],
) -> list[RunwayRow]:
    runways = aerodrome.get("runways", {})
    order = {
        "16C": 0,
        "34C": 1,
        "16R": 2,
        "34L": 3,
        "16L": 4,
        "34R": 5,
    }
    rows: list[RunwayRow] = []
    for runway_id, runway in runways.items():
        if not isinstance(runway, dict):
            continue
        path_id = runway.get("pathId", "")
        path_points = path_lookup.get(path_id, [])
        path = geometry_paths.get(path_id, {})
        length_m = (
            round(path_points[0].distance_to(path_points[-1]))
            if len(path_points) >= 2
            else int(runway.get("declaredDistances", {}).get("toraMeters", 0) or 0)
        )
        width_m = path.get("widthMeters")
        width_text = f" x {round(float(width_m))} M" if isinstance(width_m, (int, float)) else ""
        declared = runway.get("declaredDistances", {}) if isinstance(runway.get("declaredDistances"), dict) else {}
        rows.append(
            RunwayRow(
                designator=str(runway_id),
                dimensions=f"{length_m}{width_text}",
                tora=f"{declared.get('toraMeters', '?')} M",
                lda=f"{declared.get('ldaMeters', '?')} M",
                note=str(runway.get("projectionStatus", "")),
            ),
        )
    return sorted(rows, key=lambda row: order.get(row.designator, 99))


def build_candidate_airspace_shapes(
    structured_package_data: dict[str, Any],
    point_lookup: dict[str, report.XY],
) -> list[authoring.AirspaceShape]:
    shapes: list[authoring.AirspaceShape] = []
    for airspace_id, airspace in structured_package_data.get("candidateOperationalStructures", {}).get("airspaceVolumes", {}).items():
        if not isinstance(airspace, dict):
            continue
        boundaries: list[list[report.XY]] = []
        for ring in airspace.get("boundaryPointIds", []):
            if not isinstance(ring, list):
                continue
            boundary = [point_lookup[point_id] for point_id in ring if point_id in point_lookup]
            if boundary:
                boundaries.append(boundary)
        if not boundaries:
            continue
        shapes.append(
            authoring.AirspaceShape(
                mid=str(airspace_id),
                code_id=str(airspace.get("codeId")) if airspace.get("codeId") is not None else None,
                name=str(airspace.get("name", airspace_id)),
                label=str(airspace.get("label", airspace.get("name", airspace_id))),
                lower_limit=str(airspace.get("lowerLimit", "?")),
                upper_limit=str(airspace.get("upperLimit", "?")),
                category=str(airspace.get("category", "primary")),
                has_curve_vertices=False,
                boundaries=boundaries,
            ),
        )
    return sorted(shapes, key=lambda shape: (shape.category, shape.name))


def build_candidate_circuit_components(
    structured_package_data: dict[str, Any],
    path_lookup: dict[str, list[report.XY]],
) -> tuple[list[report.DxfLine], list[report.DxfLine], list[report.DxfLine]]:
    circuit_graphs = structured_package_data.get("candidateOperationalStructures", {}).get("circuitGraphs", {})
    graph_lines: dict[str, list[report.DxfLine]] = {}
    for graph_id, graph in circuit_graphs.items():
        if not isinstance(graph, dict):
            continue
        path_id = graph.get("pathId")
        if not isinstance(path_id, str):
            continue
        points = path_lookup.get(path_id, [])
        if len(points) < 2:
            continue
        graph_lines[graph_id] = path_lines(points)
    return (
        graph_lines.get("main_shared_graph", []),
        graph_lines.get("west_side_component", []),
        graph_lines.get("east_side_component", []),
    )


def build_current_core_operational_sector_shapes(
    aerodrome: dict[str, Any],
    path_lookup: dict[str, list[report.XY]],
) -> dict[str, authoring.AirspaceShape]:
    shapes: dict[str, authoring.AirspaceShape] = {}
    sectors = aerodrome.get("aip", {}).get("operationalSectors", {})
    for sector_id, sector in sectors.items():
        if not isinstance(sector, dict):
            continue
        boundaries: list[list[report.XY]] = []
        for path_id in sector.get("boundaryPathIds", []):
            if not isinstance(path_id, str):
                continue
            boundary = path_lookup.get(path_id, [])
            if boundary:
                boundaries.append(boundary)
        if not boundaries:
            continue
        altitude_band = sector.get("altitudeBand", {}) if isinstance(sector.get("altitudeBand"), dict) else {}
        upper = altitude_band.get("upper", {}) if isinstance(altitude_band.get("upper"), dict) else {}
        shapes[str(sector_id)] = authoring.AirspaceShape(
            mid=str(sector_id),
            code_id=None,
            name=str(sector.get("name", sector_id)),
            label=str(sector.get("label", sector.get("name", sector_id))),
            lower_limit="SFC",
            upper_limit=(
                f"{int(upper.get('value'))} FT MSL"
                if upper.get("kind") == "AT_LEVEL" and upper.get("levelType") == "ALTITUDE_FEET"
                else "?"
            ),
            category="operational_sector",
            has_curve_vertices=False,
            boundaries=boundaries,
        )
    return dict(sorted(shapes.items()))


def build_current_core_ctr_shape(
    world_candidate: dict[str, Any],
    path_lookup: dict[str, list[report.XY]],
) -> authoring.AirspaceShape | None:
    airspace = world_candidate.get("world", {}).get("airspaceVolumes", {})
    ctr = airspace.get("LO585", {}) if isinstance(airspace, dict) else {}
    boundary_path_ids = ctr.get("boundaryPathIds", [])
    boundaries = [
        path_lookup[path_id]
        for path_id in boundary_path_ids
        if isinstance(path_id, str) and path_id in path_lookup
    ]
    if not boundaries:
        return None
    altitude_band = ctr.get("altitudeBand", {}) if isinstance(ctr.get("altitudeBand"), dict) else {}
    upper = altitude_band.get("upper", {}) if isinstance(altitude_band.get("upper"), dict) else {}
    return authoring.AirspaceShape(
        mid=str(ctr.get("id", "LO585")),
        code_id="LO585",
        name=str(ctr.get("name", "CTR")),
        label="LOWG CTR",
        lower_limit="SFC",
        upper_limit=(
            f"{int(upper.get('value'))} FT"
            if upper.get("kind") == "AT_LEVEL" and upper.get("levelType") == "ALTITUDE_FEET"
            else "?"
        ),
        category="primary",
        has_curve_vertices=False,
        boundaries=boundaries,
    )


def build_plate_view_model(manifest_path: Path) -> PlateViewModel:
    root = report.repo_root()
    manifest = report.load_manifest(manifest_path)
    structured_package_data = structured_package.build_structured_airport_package(manifest_path)
    world_candidate = current_core.build_world_candidate(manifest_path)
    validation_report_path = root / "cad" / "airports" / "rendered" / manifest["airportCode"].lower() / "world-validation-report.json"
    world_validation_report = json.loads(validation_report_path.read_text()) if validation_report_path.exists() else {}

    current_core_aerodrome = world_candidate.get("world", {}).get("aerodrome", {})
    current_core_geometry_paths = world_candidate.get("world", {}).get("geometry", {}).get("paths", {})
    current_core_point_lookup = {
        point_id: report.XY(float(point["xMeters"]), float(point["yMeters"]))
        for point_id, point in world_candidate.get("world", {}).get("geometry", {}).get("points", {}).items()
    }
    current_core_path_lookup = {
        path_id: [
            current_core_point_lookup[point_id]
            for point_id in path.get("pointIds", [])
            if point_id in current_core_point_lookup
        ]
        for path_id, path in current_core_geometry_paths.items()
    }

    core_geometry_points = {
        point_id: report.XY(float(point["xMeters"]), float(point["yMeters"]))
        for point_id, point in structured_package_data.get("directCoreFitEntities", {}).get("geometry", {}).get("points", {}).items()
    }
    core_geometry_paths = {
        path_id: [
            core_geometry_points[point_id]
            for point_id in path.get("pointIds", [])
            if point_id in core_geometry_points
        ]
        for path_id, path in structured_package_data.get("directCoreFitEntities", {}).get("geometry", {}).get("paths", {}).items()
    }

    core_aerodrome = structured_package_data.get("directCoreFitEntities", {}).get("aerodrome", {})
    reporting_lookup = {
        fix_id: core_geometry_points[fix["pointId"]]
        for fix_id, fix in structured_package_data.get("directCoreFitEntities", {}).get("fixes", {}).items()
        if fix.get("pointId") in core_geometry_points
    }
    anchor_lookup = {
        anchor_id: core_geometry_points[anchor["pointId"]]
        for anchor_id, anchor in structured_package_data.get("candidateOperationalStructures", {}).get("namedPoints", {}).items()
        if anchor.get("pointId") in core_geometry_points
    }

    candidate_operational_sector_shapes = build_current_core_operational_sector_shapes(
        current_core_aerodrome,
        current_core_path_lookup,
    )
    ctr_shape = build_current_core_ctr_shape(world_candidate, current_core_path_lookup)
    primary_airspaces = [ctr_shape] if ctr_shape is not None else []
    secondary_airspaces: list[authoring.AirspaceShape] = []
    parachute_area_shape = None
    main_circuit, west_circuit, east_circuit = build_candidate_circuit_components(structured_package_data, core_geometry_paths)

    return PlateViewModel(
        root=root,
        manifest_path=manifest_path,
        airport_code=str(manifest["airportCode"]),
        airport=report.OfmxAirport(
            code_id=str(core_aerodrome.get("icao", manifest["airportCode"])),
            name=str(core_aerodrome.get("name", manifest["airportCode"])),
            position=report.Geo(0.0, 0.0),
            elevation_ft=int(core_aerodrome.get("elevationFeet", 0) or 0),
            magnetic_variation=int(core_aerodrome.get("magneticVariationDegrees", 0) or 0),
            transition_altitude_ft=int(core_aerodrome.get("transitionAltitudeFeet", 0) or 0),
        ),
        frequency_lines=build_entity_frequency_lines(core_aerodrome),
        runway_rows=build_entity_runway_rows(
            current_core_aerodrome,
            current_core_path_lookup,
            current_core_geometry_paths,
        ),
        current_core_aerodrome=current_core_aerodrome,
        current_core_geometry_paths=current_core_geometry_paths,
        current_core_point_lookup=current_core_point_lookup,
        current_core_path_lookup=current_core_path_lookup,
        candidate_vfr_routes={
            route["id"]: route
            for route in world_candidate.get("world", {}).get("vfrRoutes", {}).values()
            if isinstance(route, dict) and "id" in route
        },
        candidate_published_vfr_procedures={
            procedure_id: procedure
            for procedure_id, procedure in current_core_aerodrome.get("aip", {}).get("publishedVfrProcedures", {}).items()
            if isinstance(procedure, dict)
        },
        candidate_circuit_graphs={
            graph_id: graph
            for graph_id, graph in structured_package_data.get("candidateOperationalStructures", {}).get("circuitGraphs", {}).items()
            if isinstance(graph, dict)
        },
        candidate_operational_sectors={
            sector_id: sector
            for sector_id, sector in current_core_aerodrome.get("aip", {}).get("operationalSectors", {}).items()
            if isinstance(sector, dict)
        },
        candidate_operational_sector_shapes=candidate_operational_sector_shapes,
        reporting_lookup=reporting_lookup,
        anchor_lookup=anchor_lookup,
        primary_airspaces=primary_airspaces,
        secondary_airspaces=secondary_airspaces,
        ctr_shape=ctr_shape,
        parachute_area_shape=parachute_area_shape,
        main_circuit=main_circuit,
        west_circuit=west_circuit,
        east_circuit=east_circuit,
        current_core_issue_count=world_validation_report.get("issueCount", "?"),
        current_core_assumptions=[str(item) for item in world_validation_report.get("forcedAssumptions", [])],
        projection_gaps=[
            str(item)
            for item in structured_package_data.get("projectionDiagnostics", {}).get("projectionGaps", [])
        ],
        omitted_features=[str(item) for item in world_candidate.get("omittedFeatures", [])],
        plate_ids=_resolve_plate_ids(manifest),
    )


def _resolve_plate_ids(manifest: dict[str, Any]) -> list[str]:
    declared = manifest.get("plates")
    if isinstance(declared, list) and declared:
        out: list[str] = []
        for entry in declared:
            if isinstance(entry, str) and entry.strip():
                out.append(entry.strip())
        if out:
            return out
    return list(DEFAULT_PLATE_IDS)
