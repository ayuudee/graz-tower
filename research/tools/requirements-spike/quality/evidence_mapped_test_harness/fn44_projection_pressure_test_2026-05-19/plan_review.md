# FN44 plan review

## Verdict

Ship the plan, with one constraint: do not treat the "two positive cases" target
as permission to invent evidence. If inspection shows fewer than two honest
positive projections are possible, the epic should return a needs-work design
result rather than broaden the source set.

## What is strong

The plan correctly goes wide by capability, not by case count. The selected
source units pressure four different design surfaces: positive information
receipt, negative/window evidence, transfer procedure, and fallback procedure.

The plan also preserves the FN43 lesson: the public API is the product. Harness
complexity is allowed only if it hides complexity from source-mapped test
authors and improves report honesty.

The out-of-scope line is good. Fixing `SIM-RED-1`, full phraseology rendering,
and production certification trace redesign would all swamp the evidence DSL
question.

## Red-team concerns

The riskiest part is critical-phase radio silence. It is very easy to pass this
case by observing no transmissions while failing to prove the aircraft was in a
critical phase. The task specs explicitly forbid that, and completion review
must enforce it.

The transfer-of-communications cases may expose a project doctrine mismatch:
G2 currently models release plus autonomous contact, not necessarily controller
frequency transfer. That is a valid finding. Do not contort the sim or weaken
the source claim just to get a positive case.

The essential-information case may require data the current trace does not
carry. If so, the right outcome is a narrower gap that names the missing trace
projection. An ATIS setup event is not automatically proof of aircraft receipt.

## Required review focus

Completion review must inspect:

- public call-site ceremony;
- whether each source citation activated real facts or a typed gap;
- whether negative evidence depends on explicit windows;
- whether report adequacy/applicability is understandable without reading the
  harness internals;
- whether any new helper should be removed because it was added for only one
  case.

## Decision

Set plan review to `ship`.
