# Complete Lean Formalization Campaign

## Goal & Context
Use Flow-Next as the controlling execution system for the `research/fm` Lean project until the documented formalization programme reaches an honest completion point.

Completion here does not mean "prove all ATC." It means:

- keep the root `CertifiedAtc` Lean build green;
- preserve the already-closed scoped core and delivered current-shape/world-backed theorem packages;
- audit the FM docs and inventories into a single current frontier;
- close any genuine current-model gaps that the docs/inventories still expose;
- define and record the stopping rule for the current formalization generation;
- use `research/tools/r1` only as a background candidate generator for bounded proof chores and expansion suggestions.

The FM docs are source of truth. The campaign starts from `research/fm/README.md`, `research/fm/PROJECT_STATUS.md`, `research/fm/AGENT_GUIDE.md`, `research/fm/parity_inventory.md`, `research/fm/refinement_inventory.md`, `research/fm/FLOW_NEXT_FRONTIER.md`, and the root-gated Lean modules under `research/fm/lean/CertifiedAtc/`.

## Architecture & Data Models
Flow-Next owns execution state in `.flow/`. Lean remains the proof authority. The repo Nix shell exposes `.flow/bin/flowctl` and supplies Lean, Python, Node, and the `r1` dependencies.

The campaign has three lanes:

1. Frontier consolidation: reconcile README/status/inventories/root-gated modules so the current proof frontier is explicit and non-stale.
2. Proof closure: work through small closed Lean slices only where the frontier exposes a genuine current-model gap.
3. Background expansion: periodically run `research/tools/r1` against refreshed Lean snapshots to propose candidate chores; promote only reviewed, bounded, buildable candidates into Flow-Next tasks.

## API Contracts
Flow-Next commands are the only writer for `.flow` task state:

- `flowctl ready --epic <id> --json` to select work;
- `flowctl start <task-id> --json` to claim it;
- `flowctl done <task-id> --summary-file ... --evidence-json ... --json` to record completion;
- `flowctl validate --all --json` as the structure gate.

Lean proof work is gated by:

```bash
nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc "cd research/fm/lean && lake build CertifiedAtc"
```

If `r1` is used, it must operate in `research/fm/r1-smoke/`, refresh its seed snapshot from `research/fm/lean/`, regenerate queue artifacts instead of hand-editing stale queues, and leave historical `runs/` intact unless explicitly reset.

## Edge Cases & Constraints
Do not reopen closed theorem families unless the FM docs or runtime semantics show a real change. Do not use old atomic/legacy bridge work as the default widening path. Do not promote raw `r1` output without human/pilot review and a green Lean build. Do not introduce `sorry`, `admit`, or new axioms.

A task that changes proof-visible runtime semantics must update `research/fm/README.md`, `research/fm/PROJECT_STATUS.md`, the active scope/frontier note, and any affected inventory in the same change.

## Acceptance Criteria
- [ ] Flow-Next validates with `flowctl validate --all --json`.
- [ ] The campaign spec has been broken into dependency-ordered tasks by Flow-Next planning.
- [ ] The FM frontier docs and inventories agree on the current closed/open theorem surface.
- [ ] Every promoted Lean task has a root `CertifiedAtc` build as evidence.
- [ ] No `sorry`, `admit`, or new axioms are introduced by campaign work.
- [ ] `r1` has a documented background-runner operating loop and at least one smoke run only if it is used for candidate generation.
- [ ] The campaign ends with an explicit completion note stating what is closed, what is intentionally out of scope, and what would reopen the formalization.

## Boundaries
Out of scope unless explicitly promoted into a new epic:

- the requirement-spike work;
- broad runtime model redesign;
- new ATC regulatory claims not already grounded in the FM/source docs;
- treating `r1` or Ralph as the critical path owner.

## Decision Context
Flow-Next should manage this because it forces re-anchoring before work, records proof evidence, and keeps a durable task graph for a long-running Lean project. `r1` is useful for lower-powered overnight search and rote candidate generation, but it should not own the formal proof frontier. The main proof campaign remains Codex/pilot-owned, with Lean builds and FM docs as the acceptance gate.

## Review Considerations
FP / type safety: Lean proof totality is the gate. No `sorry`, `admit`, or new axioms. Any model widening must make impossible states unrepresentable where the Lean model can express that, and must not hide current-model gaps behind convenience lemmas.

Test architecture: root `lake build CertifiedAtc` is mandatory for proof changes. Kotlin/Gradle tests are required only when a task changes runtime semantics or code outside `research/fm`.

Impact: the campaign couples Flow-Next state to the FM documentation lifecycle and proof build evidence. That is intentional; stale docs are a failure mode. Reversal is to close or archive the campaign epic without changing Lean semantics, because Flow-Next state is separate from proof source.

Operational correctness: no new ATC procedure or phraseology claim may enter without source citation. Lean tasks that encode operational behavior must point back to the relevant FM/source document or cited regulation already present in the project corpus.
