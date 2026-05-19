# FN43 Plan Review

Date: 2026-05-19

Scope: isolated adversarial review of the Flow plan for
`fn-43-build-permanent-evidence-dsl-audit-core`.

## Reviewer Verdict

Rejected as written.

The reviewer agreed with the direction: terse DSL over an audit core. The
rejection was that the original acceptance criteria still allowed an
implementation to pass while leaving source/evidence too decorative.

## Critical Findings

### Source validation was too weak

The first plan required source refs to be mechanically traceable, but task 1
only required id-shape validation. That would allow invented ids that look
plausible.

Resolution: the spec and task 1 now require exact validation against accepted
registry records or reviewed spike gap records. Shape validation is explicitly
insufficient.

### Applicability / activation / adequacy were optional by wording

The first plan said reports include activation/adequacy "where applicable".
That creates a loophole where every source case can claim not to need it.

Resolution: the spec and task 6 now require applicability, activation, and
adequacy records for every source-backed case. A source-backed case without
activation must fail or report a typed vacuous/gap reason.

## Major Findings

### Structural readback could be oversold

The reviewer noted that a structural `requiredReadbackAtoms` check must not be
reported as phraseology compliance or as proof that a pilot actually read back a
sim instruction.

Resolution: the spec now requires claim-kind metadata. Structural protocol
checks must be reported as structural protocol requirements and cannot be
worded as rendered phraseology or actual exchange compliance. The generated
protocol slice must also include omitted-field / negative evidence.

### Stable fact identity was underspecified

The first plan required stable ids but did not define the stability contract.

Resolution: the spec and task 2 now define the contract: use radio/event trace
sequence where available, carry source transmission/event id where available,
and use deterministic adapter-local order only as documented fallback.
Timestamp-only ordering is forbidden.

### Expected gap governance was too narrow

The first plan required typed gap ids but did not require affected sources,
missing concept, closure trigger, and tracked backlog/deferment link.

Initial resolution was incomplete: the first revision said "typed gap id" but
could still allow broad typed labels. A second isolated re-review caught this.

Final resolution: the spec, task 1, and task 7 now require every typed gap id to
carry affected source refs, missing model/projection concept, closure trigger,
and tracked `.plan` or deferment/backlog linkage.

### Fuzzing could be tokenistic

One generated domain could be satisfied by trivial data.

Resolution: task 5 now requires non-trivial partition coverage, full
reproducibility metadata, and omitted-field / negative readback evidence.

## Moderate Findings

### Report task dependencies were too loose

The report task could be completed before real DSL/query semantics existed.

Resolution: task 6 now depends directly on the DSL and selector tasks as well
as source refs, facts, and samples.

### "At least as simple as FN41" was subjective

Resolution: task 7 now adds a concrete call-site review gate: no raw source
ids, no fact/provenance/report plumbing, no manual trace scans, and no monitor
vocabulary in ordinary tests.

### Verification was underspecified

Resolution: task 8 now requires the focused verification command to be recorded
and requires report-artifact assertions, not only behavioural assertions.

## Resulting Plan Status

The plan has been revised to close the review loopholes. A final isolated check
approved the last remaining gap-governance fix.

Implementation review should judge the work against the revised acceptance
criteria, not the initial draft.

## Remaining Risk

The hardest remaining risk is balancing the evidence/reporting core against
call-site simplicity. The revised plan handles this by making the public DSL the
design artifact and by explicitly failing review if ordinary tests leak raw
source ids, fact/provenance plumbing, manual trace scans, or monitor vocabulary.
