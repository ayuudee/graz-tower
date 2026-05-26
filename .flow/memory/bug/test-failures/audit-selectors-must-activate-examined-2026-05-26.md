---
title: Audit selectors must activate examined facts on Fail path too
date: "2026-05-26"
track: bug
category: test-failures
module: sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt
tags: [fn-48, fn-48.3, evidence-dsl, activation-check, audit-selector, codex-impl-review, silent-override]
problem_type: test-failure
symptoms: Selector returns specific Fail with reason+evidence; final EvidenceAuditResult shows generic 'did not activate any evidence facts' Fail instead.
root_cause: AuditEvidenceCaseBuilder.toCase overrides selector outcomes when activationFactIds is empty (and outcome is not ExpectedGap/Vacuous). Fail paths that forget to call activate() lose their specific diagnostics.
resolution_type: fix
related_to: [bug/test-failures/beliefstate-active-window-pin-use-2026-05-16, bug/test-failures/compound-predicate-test-assertions-2026-05-11, bug/test-failures/dynamic-injection-sim-tests-must-gate-2026-05-16, bug/test-failures/inherited-sim-test-gate-semantics-may-2026-05-11, bug/test-failures/predicate-guards-over-sealed-types-must-2026-05-16]
---

## Problem

`AuditEvidenceCaseBuilder.toCase(requireSources = true)` runs an
**activation check** after the user-supplied `expect { ... }` block
returns: if `activationFactIds` is empty AND the outcome is not
`ExpectedGap` / `Vacuous`, the framework **replaces** the selector's
specific `Pass` / `Fail` (with its diagnostic reason and evidence list)
with a generic `Fail` reading "Source evidence case '<id>' did not
activate any evidence facts". This silently destroys the selector's
diagnosis when the selector forgot to call its `activate` callback on
some path.

This bit `AuditReceptionDoubtSubject.requiresRepetitionResponse()`:
the Pass path activated all doubt facts before returning, but the
"unresolved doubt → Fail" path returned the specific failure without
activating anything. The activation check then overrode the
selector's "Reception-doubt observations without repetition response
for <aircraft>" reason + per-fact evidence with the generic
"did not activate any evidence facts" message — hiding the real
diagnosis.

## What Didn't Work

Returning a `Fail` with rich `evidence` and a precise `reason` was
not enough; without `activate(fact.id)` being called on the
considered facts, the activation check stripped the diagnostic.

## Solution

For audit selectors that take a "considered the facts but the
regulation didn't hold" Fail path, call `activate(fact.id)` for
**every fact the selector consulted** as soon as the filtered list
is determined, **before** branching into Pass vs Fail. Activation
reflects "the selector examined these facts" (so the framework knows
the selector engaged with the source's facts), not "the regulation
passed". Pattern:

```kotlin
fun branch(): EvidenceAuditOutcome {
    val relevant = facts.filter { … }
    if (relevant.isEmpty()) {
        return EvidenceAuditOutcome.Fail(
            reason = "missing evidence for <subject>",
            evidence = emptyList(),
        )
    }
    // Activate immediately — both Pass and Fail paths.
    relevant.forEach { fact -> activate(fact.id) }
    return if (passCondition(relevant)) {
        EvidenceAuditOutcome.Pass(evidence = …)
    } else {
        EvidenceAuditOutcome.Fail(reason = …, evidence = …)
    }
}
```

The "no facts at all" path can stay un-activated — that path
correctly surfaces as the generic "did not activate any evidence
facts" Fail (which matches the diagnosis).

Anchors:
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt`
  `AuditEvidenceCaseBuilder.toCase` — the activation check.
- `AuditReceptionDoubtSubject.requiresRepetitionResponse` —
  the fixed selector (commit `b4f20546`).

## Prevention

When adding a new audit selector class, write at least one selector
test that:

1. Constructs an evidence set where the selector should return `Fail`
   for a **regulation-specific** reason (not "no facts at all").
2. Runs the selector inside `simEvidence { source("…") {
   cites(…); expect { selector. … } } }`.
3. Asserts on the resulting `EvidenceAuditResult` that:
   - `outcome` is the expected `Fail`,
   - `activationFactIds` is non-empty,
   - `outcome.reason` contains the selector's specific reason
     substring (not the generic activation-check string),
   - `outcome.evidence` carries the per-fact diagnostic strings
     the selector produces.

Without (3), the activation-check failure mode is invisible: the
test sees "outcome is Fail" and passes, but for the wrong reason.

A grep for new `class Audit*Subject` files should pair with a grep
for `activationFactIds.isNotEmpty()` and `outcome.reason.contains(`
in their test files.
