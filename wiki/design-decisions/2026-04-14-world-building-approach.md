# 2026-04-14: World Building Approach

## Context

We need 2-3 accurate airport worlds for training and testing. We investigated automating the translation from parsed data (apt.dat + OFMX + CIFP) into the `AviationWorld` domain model.

## Decision

**Hand-author worlds using a CAD/GIS tool rather than building an automated translation pipeline.**

## Rationale

- The automated sources have significant gaps: no circuit geometry, no VFR route connectivity, missing IFR fix positions.
- Circuit and VFR route accuracy matters -- a synthesized rectangle is not acceptable for a training environment.
- The OFM map shows accurate circuits and VFR routes, but this data is baked into pre-rendered raster tiles and is not available in the downloadable OFMX exports.
- We only need 2-3 worlds, not hundreds. The automation cost doesn't justify itself.
- Hand-authoring against the OFM plates, AIP charts, and satellite imagery gives control over accuracy.

## Approach

1. Use a CAD/GIS tool (LibreCAD or QGIS -- TBD) to draw geometry: circuit turning points, VFR routes, taxiway centrelines, etc.
2. Export coordinates from the CAD tool.
3. Combine exported coordinates with parsed data (frequencies, procedures, airspace from the parsers) to produce `AviationWorld` instances.
4. The parsers remain useful as reference/lookup tools during authoring.

## What this supersedes

The earlier design for a full `AirportBuilder` translation layer with `RunwayTranslator`, `TaxiNetworkTranslator`, `ProcedureTranslator`, etc. That design assumed automation was the goal. The translators may still be useful for the mechanical parts (e.g., reading frequencies, converting CIFP procedures to domain SIDs/STARs) but the geometry will come from CAD, not from automated conversion of apt.dat taxi networks.

## Open

- Which CAD/GIS tool (LibreCAD being explored)
- Export format from CAD tool to code
- Whether QGIS with OFM tile overlay would be better (native WGS84 coordinates, GeoJSON export)
