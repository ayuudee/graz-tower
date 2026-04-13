# Command Catalog

The typed command source of truth is:

- [CommandCatalog.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/CommandCatalog.lean)

That module is both:

- the command vocabulary
- the static routing table for certification plans

## Core Types

- `CommandClass`
- `ParameterKind`
- `ParameterSpec`
- `PreconditionTag`
- `LifecycleClass`
- `CompletionTrigger`
- `LifecycleSpec`
- `PlanTemplate`
- `CommandProfile`

## Core Functions

- `classOf : Command → CommandClass`
- `profile : CommandClass → CommandProfile`
- `commandCatalog : List CommandProfile`
- `commandPlan : Command → PlanTemplate`

Derived orchestration function:

- `compile_command : CommandClass → PlanTemplate`

`compile_command` is defined in
[Interfaces.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Interfaces.lean)
as the orchestration-side projection of the static plan stored in the command
profile.

## Why The Catalog Matters

The architectural change in `research/fm` is that command routing is no longer an
implicit convention.

`PlanTemplate` records, statically, for each command class:

- whether a certified path is currently defined
- whether runway certification is required
- whether surface certification is required
- whether air certification is required
- whether separation checking is required
- whether compatibility is required
- whether the command is treated as a joint act

This means the command catalog is the static half of the optional
single-issuer composition layer. It fixes what the command must go through
before any runtime state is consulted if that orchestration path is used.

It is still useful even when the project focus is kernel-first, because it
records the intended dependency surface without claiming that one global theorem
must already exist.

## Current Coverage

Important distinction:

- the catalog can describe more command classes than are currently proved
- orchestration proofs are currently only complete for a partial runway/air
  slice

So the catalog should be read as:

- a frozen architecture contract for routing
- not a claim that every command class already has a proved instantiation path
- not a claim that the full orchestration theorem is the primary project goal

## Current Proved Slice

The currently supported orchestration slice is:

- `HoldShortOf`
- `TaxiTo`
- `CrossRunway`
- `LineUpAndWait`
- `ClearedForTakeoff`
- `ClearedToLand`
- `ClearedTouchAndGo`
- `GoAround`
- `JoinCircuit`
- `ExtendDownwind`
- `ContinueApproach`
- `ReduceSpeedTo`
- `ClimbTo`
- `DescendTo`
- `ClearedApproach`
- `CrossControlledAirspace`

Those commands are the current benchmark for one optional composition path. They
do not change the more important kernel-first reading of the project.

## Grounding Boundary

The command classes still mirror the current product surface in the main
project, but `research/fm` is not yet a code-refinement project.

For now the catalog is an architecture and theorem artifact, not an executable
integration guarantee, and not a reason to treat the global orchestration
theorem as mandatory.
