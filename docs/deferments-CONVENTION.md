# Deferments Convention

This document is the **doctrine** for how deferred work is filed, discovered, and
closed in this repo. The live index of named deferments lives in
[`deferments.md`](./deferments.md). This file (`deferments-CONVENTION.md`)
explains the decision tree, the schema, and the lifecycle.

Keep these two files in sync. When the convention changes here, the map updates
to match (and vice versa).

---

## 1. Overview — four buckets

A **deferment** is work consciously parked with a real-fix contract: an
eventual API shape, a missing prerequisite, a named closure trigger, or a
test-that-lands-when-closed. Every deferment falls into exactly one of four
buckets:

| Bucket | What it is | Canonical store |
|-------:|------------|-----------------|
| 1 | Test contract — API exists today | `<module>/src/commonTest/kotlin/.../DeferredContractsSpec.kt` (`@Ignore`d test with real-API value-flow reference) |
| 2 | API gap — API doesn't exist yet | Same file as bucket 1, but the test body is commented-out pseudo-code (no compile-check until the API lands) |
| 3 | Multi-task scope — needs an epic | A flow-next epic in `todo` status (one stub epic per deferred scope) |
| 4 | Narrative / doctrinal / cross-cutting | `docs/deferments.md` entry only (no test, no epic, no inline anchor besides the breadcrumb) |

The `docs/deferments.md` register is the **map** — every named deferment has
exactly one entry there, regardless of which bucket it falls into. The entry's
`Pinned at:` field is the single source of truth for where the canonical
record lives (test file path + test name, flow-next epic ID, or `narrative
only`).

### What is NOT a deferment

If the item is an ordinary known issue / operational backlog item (`B3`,
`IFR-1`, `RR-*`, `M*`, "by-design / accepted") with no real-fix contract — it
lives in `.plan`, not in `docs/deferments.md`. The boundary rule:

- **Named `D-*` prefix with a real-fix contract** → `docs/deferments.md`.
- **Otherwise** → `.plan`.

See `AGENTS.md § Project Plan` for the same boundary rule restated.

---

## 2. Decision tree — which bucket?

When you surface a deferment during a pass, walk this interview-question form
top-to-bottom and stop at the first match:

> **Does this deferment have a clear test shape I could write today** (even
> as an `@Ignore`d skeleton)?
>
> - **Yes, the API exists today.**
>   → **Bucket 1.** Add an `@Ignore`d test to the relevant module's
>   `DeferredContractsSpec.kt` (create the file if the module doesn't have
>   one). The test body MUST contain a real-current-API value-flow reference
>   (see § 5).
>
> - **Yes, but the API is missing** (e.g. "needs `PilotInput.nearbyTraffic`").
>   → **Bucket 2.** Same file, same `@Ignore` shell, but the test body is
>   commented-out pseudo-code. The KDoc names the missing API so the
>   implementer knows what to add when uncommenting.
>
> - **No, but it's clearly multi-task scope** (would need its own epic).
>   → **Bucket 3.** Create a flow-next epic via `.flow/bin/flowctl epic
>   create`, set it to `todo`, then file the deferment in `docs/deferments.md`
>   with `Status: planned` and `Pinned at: fn-N-<epic-id>`.
>
> - **No, and it's doctrinal / cross-cutting / blocked-on-real-world.**
>   → **Bucket 4.** `docs/deferments.md` entry only, `Status: narrative`,
>   `Pinned at: narrative only`. May graduate to bucket 1 / 2 / 3 later.

Buckets 1 and 2 are mechanically identical (same file, same `@Ignore`
annotation, same module placement). The split is **decision-tree clarity** for
the implementer: bucket 1 means "just uncomment and the test runs"; bucket 2
means "uncomment + extend the API surface, THEN the test runs."

---

## 3. Status taxonomy — four leaves

Every entry in `docs/deferments.md` carries exactly one of these statuses. No
extension without a spec amendment.

- **`blocked`** — waiting on prerequisite work that does not yet exist
  (a model field, a scenario, a third aerodrome). Has a clear unblock
  trigger. The entry MUST carry a `Blocked on:` field naming the prerequisite.

- **`planned`** — has a flow-next epic stub (`todo` status) whose tasks are
  NOT all `done`. Bucket 3 entries. **Verify at the point of writing**: if
  ALL tasks of the referenced epic are `done`, the deferment is closed and
  belongs in `## Archive`, not the active body.

- **`narrative`** — bucket 4 entry with no clear pin today. May graduate
  to `blocked` or `planned` later via the transition convention (§ 7).

- **`closed`** — kept ONLY in the `## Archive` section for history. Closed
  entries do NOT appear in the active body. Single canonical location, no
  duplicates. The active body's job is forward-looking; the archive's job
  is "what closed this, what code enforces the contract now."

**Anti-decision**: there is no `active`, `in-progress`, `partial`, `paused`,
or `accepted-as-by-design` status. Those distinctions are properly the
epic-status surface (`open`/`done`) or the inline `.plan` "by-design /
accepted" pattern. The four-leaf taxonomy is exhaustive.

---

## 4. `Pinned at:` field — plain text, greppable

The `Pinned at:` field tells a reader where to find the canonical record.
Plain text only — no markdown links (links break when files move; plain text
survives `grep -rn` and IDE jump-to-line).

### Format

- **For tests** (buckets 1 + 2): `path/to/File.kt::test_name_or_section`
  - Example: `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::PF1 airport requiring startup clearance has REQUEST_STARTUP and AWAIT_STARTUP_APPROVAL`

- **For epics** (bucket 3): `fn-N-<epic-id>`
  - Example: `fn-16-wind-state-migrate-to-aerodromeweather`

- **For narrative** (bucket 4): `narrative only`

- **For archive entries**: the field is omitted entirely (see § 8 — archive
  schema is three fields, none of them `Pinned at:`).

---

## 5. Entry schema — locked field order

Every entry in `docs/deferments.md` uses this exact shape:

```markdown
### <D-ID example> — <One-line title>
**Status:** <blocked | planned | narrative | closed>
**Pinned at:** <path/to/file.kt::test_name | fn-N-epic-id | narrative only>
**Blocked on:** <free text; only present when status=blocked>
**Why:** <1-3 sentences. Why this is deferred, what the world looks like when it lands. Reality-anchored, no "operationally acceptable" softening.>
**Contract:** <conditional — present when the source body carried a real-fix contract richer than 3 sentences of Why: can hold. Captures eventual API shape, blocked-on prerequisite, named closure trigger, or test-that-lands-when-closed. Verbatim from source.>
**Closes by:** <new epic when activated | inline in epic fn-N | archived when prerequisite lands | epic fn-N (planned)>
```

### Rules

- **Field order is fixed.** Reorder breaks the file's mechanical scannability.
- **`Blocked on:` is omitted entirely** (not blank, not "n/a") when `Status ≠ blocked`.
- **`Contract:` is omitted** when the source content fits inside `Why:`'s 1-3 sentence cap.
- **All other fields are always present.**
- **`Why:` is capped at 1-3 sentences** — enforced by review. Longer
  contract detail moves to `Contract:`.
- **Headings use `### D-...`** for entries. Use no other depth for an entry.
- **Heading discipline**: only `### D-...` headings denote a deferment entry.
  Section-organising headings use `##` depth or prose. This makes the file
  mechanically scannable: `grep -c '^### D-' docs/deferments.md` counts
  entries.

### Compile-check guarantee — bucket 1 vs bucket 2

This is the load-bearing distinction between buckets 1 and 2:

- **Bucket 1** — when the deferred API exists today, the `@Ignore`d test
  body **MUST** include at least one **non-import** real-compilable
  reference to the current API. What counts: an assertion using a real
  type, OR a type construction of a real domain class, OR a function call
  returning a real value — i.e. a reference that **participates in the
  test body's value flow**. **Import-only references do NOT count** (IDE
  auto-cleanup can remove them; detekt unused-import rules can flag them).
  Minimum: one statement like `val x: RealType = constructor(...)` or
  `assertEquals(realFunction(...), expectedValue)` inside the test body,
  even though `@Ignore` skips runtime execution.

- **Bucket 2** — when the API is missing, the test body is
  **commented-out pseudo-code only**; no compile-check is possible until
  the API lands. The KDoc names what API is missing so an implementer
  knows what to add when uncommenting.

The blanket claim "bucket 1/2 = compile-checked acceptance shape" applies
only to bucket 1 in its strong form. Bucket 2 is narrative-in-code that
becomes compile-checked when the implementer flips `@Ignore` off +
uncomments + the new API exists.

---

## 6. Worked schema examples — one per status

These examples use **non-matching placeholder IDs** (prefixes like
`<D-ID example>`, `EXAMPLE-PASS-feature-shape`, `EXAMPLE-AUDIT.NN-FOLLOWUP`,
`DEFERMENT_ID_EXAMPLE`) so that the repo-wide R15 grep cannot pattern-match
the convention doc's illustrative IDs as real entries that should appear in
`docs/deferments.md`. Real-ID examples may be added as a documentation polish
after migration tasks (fn-18.2 / fn-18.3) populate the active body — but
schema-shape with placeholder IDs is the form that survives the R15 regex.

### Bucket 1 — test contract, API exists today

```markdown
### EXAMPLE-AUDIT.feature — Sealed precedence carries source provenance
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::EXAMPLE-AUDIT feature sealed precedence test
**Blocked on:** Source-provenance field added to RunwayAssignment carrier type.
**Why:** Current code carries the assigned-runway value but not the source that produced it; cross-checks against pilot acknowledgement cannot detect mismatch without the provenance.
**Closes by:** archived when prerequisite lands.
```

Test body shape (bucket 1 — real-API value flow):

```kotlin
@Ignore
@Test
fun `EXAMPLE-AUDIT feature sealed precedence test`() {
    val assignment = RunwayAssignment(runwayId = RunwayId("09"))    // real type
    assertEquals(RunwayId("09"), assignment.runwayId)               // value-flow assertion
    // TODO when EXAMPLE-AUDIT.feature lands:
    //   add `source: RunwayAssignmentSource` field, assert source carried through.
}
```

### Bucket 2 — API gap, API missing today

```markdown
### EXAMPLE-PASS-missing-api — Pilot can see nearby traffic
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::EXAMPLE-PASS pilot reads nearby traffic
**Blocked on:** `PilotInput.nearbyTraffic` field doesn't exist yet.
**Why:** Pilot collision-avoidance decisions require knowing other aircraft positions; the input surface is currently runway/wind/clearance only.
**Closes by:** archived when prerequisite lands.
```

Test body shape (bucket 2 — commented-out pseudo-code, no compile-check):

```kotlin
@Ignore
@Test
fun `EXAMPLE-PASS pilot reads nearby traffic`() {
    // TODO when EXAMPLE-PASS-missing-api lands — requires PilotInput.nearbyTraffic field:
    //   val input = PilotInput(nearbyTraffic = listOf(otherAircraft))
    //   val decision = pilot.decide(input)
    //   assertContains(decision.observations, OtherAircraftObserved(otherAircraft))
}
```

The bucket-2 test body has NO non-comment statements — there's no real-API
reference to anchor against, because the API doesn't exist yet. It becomes
bucket 1 (compile-checked) the moment the API lands and the implementer
uncomments.

### Bucket 3 — multi-task scope, epic stub exists

```markdown
### EXAMPLE-PASS-feature-shape — Multi-task feature waiting on its epic
**Status:** planned
**Pinned at:** fn-N-<example>-epic
**Why:** The work spans test + protocol + controller + pilot changes and warrants its own epic; tracked as a stub now to avoid losing the breadcrumb.
**Closes by:** epic fn-N-<example>-epic (planned).
```

No test file changes — the epic is the canonical record.

### Bucket 4 — narrative, no clear pin today

```markdown
### EXAMPLE-PASS-doctrinal-note — Cross-cutting tooling note
**Status:** narrative
**Pinned at:** narrative only
**Why:** Detekt rule that ensures every inline `D-PASS-*` code comment appears in `docs/deferments.md` would catch ID drift; v1 ships human-readable map only, tooling lift is a follow-up.
**Closes by:** new epic when CI tooling lift becomes worthwhile.
```

### Archived entries — three locked fields (Status + Closed by + Enforcement)

When a deferment closes, its entry moves out of the active prefix section
and into `## Archive` with the body trimmed to these three fields:

```markdown
### DEFERMENT_ID_EXAMPLE — Closed-deferment one-line title
**Status:** closed
**Closed by:** Pass N (commit abcdef0) — see ~/.claude/plans/pass-N-<example>.md
**Enforcement:** `RealType` sealed type in `protocol/<example>.kt`; invariant test in `<example>ContractSpec.kt`
```

The `Enforcement:` field is **mandatory** — it preserves the enforcement
surface that the deleted comment block would otherwise reference. Without it,
the archive entry is less informative than the state being removed. Closure
history must be at least as informative as the deleted code.

No `Why:` / `Blocked on:` / `Pinned at:` fields on archived entries. The
active register is forward-looking; the archive is "what closed this, what
code enforces the contract now."

---

## 7. Bucket transition — in-place status update

When a deferment moves between buckets (e.g. bucket 4 narrative → bucket 1
test once an API lands, or bucket 4 narrative → bucket 3 planned once an
epic stub is created), the `docs/deferments.md` entry updates **in place**:

- `Status:` field flips (`narrative → blocked`, `narrative → planned`, etc).
- `Pinned at:` updates to point at the new test or epic ID.
- A parenthetical "(promoted to bucket N on YYYY-MM-DD)" appended to the
  `Why:` field's last sentence — short, no rewording.
- The old `Pinned at: narrative only` value moves into a `Previously:` line
  ONLY if useful for history (e.g. there are now two test pin candidates
  competing); otherwise dropped. Git history preserves the diff.

**Anti-pattern**: do NOT add a new entry and mark the old "obsolete." One
entry per deferment ID throughout its life. The ID's history is the entry's
history — it migrates with it.

---

## 8. Archive policy — closed entries

When a deferment closes (the work landed, or it became by-design, or it was
abandoned with a recorded rationale), the entry moves to the `## Archive`
section at the bottom of `docs/deferments.md`. It does NOT get deleted.

Rationale: grep-discoverability beats git-archaeology. "This was deferred
and then closed by X" is information future agents will want when they hit
a related contract surface.

**Format**: archive entries use the same `### D-...` heading discipline as
active entries (so `grep '^### D-' docs/deferments.md` counts both active
and closed), but the body is trimmed to the three locked archive fields
shown in § 6.

**Process when closing**:

1. Cut the active entry from its `## D-*` prefix section.
2. Trim the body to Status + Closed by + Enforcement.
3. Paste into `## Archive`.
4. If a bucket-1/2 test referenced the deferment, the test either (a) gets
   deleted (the contract is now first-class production code, no `@Ignore`
   shell needed), or (b) gets converted to a non-`@Ignore`d regression test
   pinning the now-enforced shape. No silent retention of `@Ignore`d tests
   for closed deferments.

Single canonical location, no duplicates.

---

## 9. File locations

| Surface | Location |
|---------|----------|
| The map | `docs/deferments.md` (repo root) |
| This convention | `docs/deferments-CONVENTION.md` (repo root) |
| Bucket-1 / 2 canonical store (per module) | `<module>/src/commonTest/kotlin/xyz/easiersaid/twr/<module>/DeferredContractsSpec.kt` |
| Bucket-3 canonical store | flow-next epic in `todo` status (managed via `.flow/bin/flowctl`) |
| Bucket-4 canonical store | `docs/deferments.md` entry only |
| Historical secondary (pre-Pass-17 pass narrative) | `~/.claude/plans/pilot-firewall.md § Deferments register` (repo-external; user-owned) |

**Per-module `DeferredContractsSpec.kt` discipline**:

- Each module that has at least one bucket-1 / bucket-2 deferment gets its
  own `DeferredContractsSpec.kt`. Today only `:pilot` has one. Modules likely
  to grow one at migration time: `:controller`, `:protocol`, `:sim`.
- File path: `<module>/src/commonTest/kotlin/xyz/easiersaid/twr/<module>/DeferredContractsSpec.kt`.
- Class name: `class DeferredContractsSpec` (package is the discriminator;
  no need to repeat the module name in the class).
- Empty `DeferredContractsSpec.kt` files are NOT created. Only modules
  with actual contract-shape deferments get the file.
- Centralisation in `:protocol` is **rejected**: `:protocol` cannot import
  from `:pilot` / `:controller` / `:sim`, and the test bodies need access
  to the deferred-API call shapes that live in the consumer modules.
  Proximity wins.

---

## 10. Test-method-name anchors must NOT match the `D-*` regex

When a bucket-1 / bucket-2 entry's `Pinned at:` field points at a Kotlin
test-method name (the test-body anchor), the test-method name MUST use a
non-matching form that DOES NOT begin with `D-PASS-` / `D-AUDIT.` / `D-PF.`
/ `D-WORLD-` / `D-WORLD.`. The canonical ID with `D-` stays in the
`### D-...` heading and the `Pinned at:` `<file>::<anchor>` cite uses a
matching but `D-`-stripped anchor.

**Rationale.** R15's whole-repo grep matches every `D-*` string in `*.kt`,
`*.md`, `.plan`. If a Kotlin test-method name contains a `D-PASS-...` or
`D-AUDIT....` substring, that substring appears as a concrete ID in the
repo-wide ID set and must have a `### D-...` heading in `docs/deferments.md`.
Allowing "alias forms" would weaken R15's acceptance: the gate would pass
while concrete `D-*` strings still lack matching docs headings.

**Convention for test-method anchors:**

- Drop the leading `D-` prefix on the test-method name.
- Substitute `_` for `.` in numeric-suffixed IDs because Kotlin's backtick
  identifier handling is fragile around `.`.
- The `Pinned at:` field uses the form `<file>::<anchor> <short test
  description>` where `<anchor>` is the `D-`-stripped form; the `<file>::`
  prefix makes the anchor unambiguous.

**Examples in the repo:**

| Canonical (docs heading) | Test-method anchor (no `D-` prefix) |
|---|---|
| canonical 13.3-II-FOLLOWUP form | `PASS-13_3-II RunwayLengthFailure ...` (in `controller/.../DeferredContractsSpec.kt`) |
| canonical 17.2 form | `PASS-17_2 IFR procedure helpers ...` (in `controller/.../DeferredContractsSpec.kt`) |

When filing a new bucket-1 / bucket-2 entry whose canonical ID contains `.`,
follow this convention: keep the canonical `D-...` form in the `###`
heading, strip the `D-` prefix and substitute `_` for `.` in the
test-method name.

---

## Cross-references

- `docs/deferments.md` — the live index.
- `AGENTS.md § Project Plan` — discovery pointer + boundary rule for
  `docs/deferments.md` vs `.plan`.
- `AGENTS.md § Self-assessment before review` — deferment-honesty step.
- `~/.claude/plans/pilot-firewall.md § Deferments register` — historical
  secondary (the user-owned, pre-Pass-17 register).
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt` —
  prior-art for the bucket-1 / bucket-2 pattern.
