# 2026-04-21: FM Overnight Proof Queue Promotion

The `research/tools/r1` overnight Lean runner is now an established local
operations pattern, but its workspace under `research/fm/r1-smoke/` remains
ignored and disposable. The tracked proof source of truth is still
`research/fm/lean`.

## Decision

Successful overnight proof runs should be closed out in two separate steps:

1. regenerate and run queues inside the ignored `research/fm/r1-smoke/`
   workspace
2. review the promoted snapshot, copy the successful theorem files into
   tracked `research/fm/lean`, wire them into `CertifiedAtc.lean`, and build
   the tracked Lean project

This session promoted the completed observation-regression backlog into the
tracked tree as eight theorem-only modules over `GreenfieldCompletion`:

- `GreenfieldObservationInstructionRunwayRadio`
- `GreenfieldObservationInstructionLevelSpeedA`
- `GreenfieldObservationInstructionLevelSpeedB`
- `GreenfieldObservationInstructionTransponder`
- `GreenfieldObservationResolvedGroundRunway`
- `GreenfieldObservationResolvedRouteProcedure`
- `GreenfieldObservationResolvedAirspaceFrequency`
- `GreenfieldObservationPlainInstructionSteps`

## Why

Leaving proof results only in the ignored smoke workspace is not an actual
integration step. The repo needs the reviewed theorem files, import wiring,
and a tracked build result, otherwise the overnight run remains operational
output rather than project state.

## Consequences

- The nightly close-out routine now includes a tracked Lean build after
  promotion.
- `research/fm/README.md`, `research/fm/PROJECT_STATUS.md`, and
  `research/fm/lean/README.md` should be updated when a promoted backlog
  changes the tracked theorem surface.
- When a queue report says the prepared backlog is exhausted, the next session
  should author the next 3-8 coherent graphs before launching another
  overnight run.
