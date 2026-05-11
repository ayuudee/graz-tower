# Handoff — fn-15/16/17/18 ready for execution (2026-05-11)

Four flow-next planning epics shipped to `.flow/specs/` in commit `16c4fe8`. The previous session closed fn-14 (G3a-react crosswind) and queued these four as the next pieces of work. Pick one (or all, in dependency order) to execute via `/flow-next:work`.

## Execution status

| Epic | Verdict | Codex rounds | R-IDs | Tasks | Notes |
|------|---------|--------------|-------|-------|-------|
| **fn-15-g3a-react-tailwind-pilot-reactive-go** | SHIP | 2 | 14 | 2 | Cleanest — closely mirrors fn-14's shape |
| **fn-16-wind-state-migrate-to-aerodromeweather** | SHIP | 9 | 13 | 2 | Cross-cutting refactor; hard cutover |
| **fn-17-cap-413-edition-24-numbering** | substantively done, no SHIP verdict after 31 rounds | 31 | 13 | 1 | See caveat below |
| **fn-18-deferment-register-reorganization-four** | SHIP | 5 | 16 | 3 | Meta-epic; introduces docs/deferments.md + four-bucket model |

All four pass `flowctl validate`. Execution order depends on goals (see Dependencies below).

## Recommended execution order

1. **fn-15** — easiest, finishes the G3a-react pair (crosswind + tailwind). Direct application of fn-14's machinery.
2. **fn-18** — establishes the four-bucket deferment model. Doing this BEFORE fn-16 means fn-16's seven new deferments file into the new canonical surfaces from the start. Doing it AFTER means fn-16's deferments get migrated as part of fn-18.2. Either order works; "fn-18 first" produces less churn.
3. **fn-16** — cross-cutting refactor; expect implementation pushback from codex review. Plan is unusually thorough.
4. **fn-17** — see caveat. Decide whether to ship as-is, re-scope, or park.

Dependencies (declared in epic JSON; `flowctl` enforces):
- fn-15 → depends on fn-14, fn-11, fn-12, fn-8
- fn-16 → depends on fn-12 (runway-obstruction migration precedent), fn-14
- fn-17 → depends on fn-11, fn-12, fn-13, fn-14 (citation-update targets)
- fn-18 → no flow-next deps (meta over everything)

## fn-17 caveat — READ BEFORE EXECUTING

The fn-17 plan ran 31 codex rounds without reaching SHIP. The substance converged by round ~10; rounds 11-31 chased cross-paragraph consistency at asymptote on a 1176-line epic spec. Per `feedback_pass_scope.md`, the loop hit diminishing returns around round 20.

**Critical finding the plan surfaces**: the docs-scout claim from fn-14 planning — that CAP 413 Edition 24 renumbered §4.66→§4.65 and §4.67→§4.66 — **could not be verified**. The CAA endpoint advertised as serving "Edition 24 effective 1 July 2026" actually still serves Edition 23 (effective 21 January 2021; SHA `f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21e746e7`).

The plan accommodates four branches:
- **A** — renumbering-confirmed → rename sweep
- **A-retire** — pilot-initiated GA section retired entirely → different sweep
- **B** — numbering-retained → no renames, just verification artifact + R9
- **C** — unverifiable → defer renumbering work, ship only R9

**R9 (in scope all branches)**: every CAP 413 entry in `RegulationDatabase.kt` carries `edition = "27th ed. (2023)"` — factually wrong (CAP 413 numbering is Ed 1-24, not 27). This is a real bug independent of any renumbering question.

**Options**:
- **Option 1**: Execute fn-17 as-is. Task .1 Step 1 is "verify primary source"; everything else gates on the verdict.
- **Option 2**: Re-scope: split into (a) a small "fix the wrong edition literal" epic that ships R9 immediately, and (b) "CAP 413 Edition 24 renumbering when verified" parked until Edition 24 actually exists.
- **Option 3**: Park fn-17 entirely until Edition 24 is independently verified.

Recommend Option 2 if you want progress this session; Option 1 if you want the full branch-aware execution; Option 3 if you suspect Edition 24 may never ship in the form fn-14 assumed.

## fn-18 — the deferment-register meta-epic

This epic establishes the four-bucket hybrid model:

1. **Behavioral contracts with eventual acceptance shape** → `@Ignore`d tests in per-module `*DeferredContractsSpec.kt`. The pattern already exists at `pilot/src/commonTest/kotlin/.../DeferredContractsSpec.kt` (six entries pinning D-PF.1/2/3 + D-AUDIT.5/6). Promotes to canon.
2. **API / state-shape gap** → `@Ignore`d test with the missing call commented out next to the assertion.
3. **Material multi-task work** → flow-next epic in `todo` status (surfaced by `flowctl list --status=todo`).
4. **Doctrinal cleanup / cross-cutting refactor / blocked-on-real-world** → entry in `docs/deferments.md` (the narrative map).

`docs/deferments.md` is the **map**, not the **store** — each entry carries a `pinned_at:` field pointing at the canonical store (test file:test name / epic ID / `narrative only`).

Three registers coexist:
- `.plan` — repo-root, canonical for **operational** backlog (short-IDs B3/IFR-1/RR-*/M*).
- `docs/deferments.md` — NEW, canonical for **D-*** deferments with contract shape.
- `~/.claude/plans/pilot-firewall.md` — off-repo, architectural design home (sister to docs/deferments.md; identical content).

R15 (whole-repo exhaustiveness gate) and R16 (bucket-1 value-flow reference) are the load-bearing structural checks. Read fn-18 epic spec + task .1 before executing.

## Memory updates this session would benefit from

After fn-18 executes successfully, update the auto-memory entry `reference_audit_registers.md` — it currently says deferments live ONLY at `~/.claude/plans/pilot-firewall.md`, missing the `.plan` sister-register convention and the upcoming `docs/deferments.md` map. The fn-18 plan handles `reference_audit_registers.md` update as acceptance (R11).

## Process discipline reminders (already in auto-memory)

- `/flow-next:work fn-N` spawns a worker subagent with fresh context — let it run.
- Codex impl-review until SHIP after every task (`feedback_review_discipline.md`).
- No corners cut, no skip-lists, no silent workarounds (`feedback_no_corners.md`).
- World-only test triggers in sim tests (`feedback_world_only_test_triggers.md`).
- Don't soften deferments (`feedback_reality_anchored.md`).

## Working tree state at handoff

- Branch: `master` (8 commits ahead of `origin/main`)
- Working tree clean
- Latest commit: `16c4fe8 fn-15/16/17/18: queue four planning epics for next-session execution`
- Eight golden sim tests green (last verified at fn-14.2 closure)
- No outstanding code changes; everything is in `.flow/specs/` and `.flow/tasks/`

When done with any of fn-15/16/17/18, delete this file as part of the closing commit. It's a session-handoff note, not durable architecture.
