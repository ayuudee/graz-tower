# Memory index — graz-tower (flow-next memory)

Project-scoped flow-next memory. Each entry below is a one-line summary of a
file in this directory; the file itself carries the full content. Discoverable
via `flowctl memory search <token>`.

## knowledge/best-practices

- **`knowledge/best-practices/test-pin-discipline-2026-05-15.md`** — Sim test pins: mint-id timestamps for same-cycle ordering, observe real post-state not predicate compounds, allowlists key by stable identifier, compound-predicate failure modes.
- **`knowledge/best-practices/inherited-gate-semantics-2026-05-15.md`** — Copy-pasted sim-test gates between sibling axes need re-validation per semantic meaning, not syntactic pattern (fn-15.2 lesson).
- **`knowledge/best-practices/renumbering-grep-walk-2026-05-15.md`** — Grep walks for renumbering reconciliation must span the FULL affected range, not just the hypothesis's focal sections (fn-17 lesson).
- **`knowledge/best-practices/pre-existing-failure-register-2026-05-15.md`** — Pre-existing test failures carried across 2+ epics must be promoted to named D-WORLD.N register entries (fn-16.2 → fn-19 lesson).

## knowledge/conventions

- **`knowledge/conventions/rich-world-domain-2026-05-15.md`** — Time-varying state lives on the entity (`Aerodrome.weather`, `Runway.obstruction`); hard atomic cutover, no shims (fn-12 + fn-16 precedents).

## knowledge/workflow

- **`knowledge/workflow/flowctl-done-state-sync-2026-05-15.md`** — After `flowctl done`, run a post-done state-sync sweep (clear BLOCKED narrative, populate Evidence, flip `.json` status) BEFORE re-invoking codex impl-review — otherwise review cycles on artifact mismatches (fn-18.2 5-round lesson).
