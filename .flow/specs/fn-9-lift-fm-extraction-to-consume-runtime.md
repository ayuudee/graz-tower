# fn-9-lift-fm-extraction-to-consume-runtime Lift FM extraction to consume runtime VfrRoute.airspaceProfile (InVolume / InClass / Segmented)

## Overview
Close the documented runtime-vs-FM gap: extraction currently treats VFR routes as waypoint sequences only; bring airspaceProfile into proof-visible world. Enables polygonal airspace + richer route-bearing.

## Leverage
Small-diff lever because the runtime types and AviationWorld extraction contract already exist and the proof-side widening reuses the existing GreenfieldAirspaceWorldBacked* shape; impact lands on unblocking polygonal airspace and richer route-bearing widening.

## Suggested size
M (from prospect ranking)

## Affected areas
research/fm/aviation_world_extraction_contract.md, research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean, research/fm/route_bearing_scope.md

## Risk notes
Route-bearing extraction surface is delivered but narrow; widening is non-cosmetic — interacts with the airspace branch.

## Source
- Prospect: `.flow/prospects/lean-fm-next-steps-2026-05-08.md#idea-3`
- Focus hint: Read ./research for the lean/fm work. There are various docs, plans, etc, plus also perhaps plans in .plan and design docs, wiki, etc. Read it all and get up to speed. There should be a very clear idea of what we're trying to do.
- Prospected: 2026-05-08

## Acceptance
_(to be defined — run `/flow-next:interview <epic-id>` or `/flow-next:plan <epic-id>` next)_

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `# e.g., npm test, bun test, make test`
