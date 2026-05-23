# Agent Dialogue

Purpose: shared, abridged communication between agents working in this repo.
Use this file for handoffs, questions, decisions, and current state that another
agent should see quickly.

## Actors

There are exactly two agents collaborating on the ICAO 9432 programme. They
share this file but never the same epic.

- **Test Writer.** Authors source-mapped tests for each chunk. Owns
  test-authoring epics. Does not weaken tests to match current behaviour.
  Hands off to the Test Completer by leaving the chunk's failing tests +
  coverage matrix in a known state and writing an entry below.
- **Test Completer (this agent).** Picks up failing tests from a chunk and
  drives them to green via `flow-next` — planning each repair as its own
  epic, implementing, reviewing, and committing. Never edits the Test
  Writer's tests to make them pass; if a test is wrong, raise a
  `QUESTION:` here and wait.

Neither role downgrades scope on the other's behalf. If a repair turns out
to require a regulation or design decision the Test Completer cannot make
alone, raise a `QUESTION:` and stop — do not stub, skip, or defer.

## Write Protocol

- Append new entries under **Dialogue Log**.
- Keep entries short and factual.
- Include date/time if useful.
- Do not paste long command output; summarize and cite the command.
- Mark open questions explicitly with `QUESTION:`.
- Mark resolved items explicitly with `RESOLVED:`.
- If an entry creates deferred work, also add it to `.plan` AND to
  `docs/deferments.md` if it carries a named contract (per
  `docs/deferments-CONVENTION.md`).

## Current Focus

Prime objective: work through all accepted ICAO Doc 9432 source units as a
source-mapped regulatory testing programme.

Current programme state:

- `fn-46` mapped all accepted ICAO 9432 source units.
- `fn-47` completed chunk 01: communications, transfer, and readback.
- Chunk 01 result: 6 `covered-green`, 4 `expected-gap`, 7
  `phraseology-later`, 2 `policy-blocked`, 1 `not-applicable`.
- Chunk 01 artifacts live under:
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/`.

## Workflow

The chunk workflow has two phases with distinct owners:

1. **Test-authoring phase (Test Writer).** Open a chunk epic. Write
   source-mapped tests against the chunk's ICAO 9432 units. Tests are
   correct-by-construction against the regulation; they are not adjusted
   to fit current implementation. Leave the chunk with a coverage matrix
   and a clear list of failing tests + reason codes.
2. **Test-completion phase (Test Completer).** For each failing test or
   coherent group of failing tests, open a **separate** repair epic via
   `flow-next`. Plan, implement, self-assess, review (where the work
   warrants it), commit. Update the chunk's coverage matrix when a
   group moves to `covered-green`.

The two phases share AGENT_DIALOGUE.md but never share an epic.

## Standing Constraints

Both actors:

- Follow `AGENTS.md` commandments in full. The Test Completer in
  particular owes the principal-agent self-assessment (`AGENTS.md` §
  Self-assessment before review) before any review or commit.
- Keep test-authoring epics separate from implementation repair epics.
- Do not weaken tests to match current implementation.
- Policy-dependent source units require typed policy concepts, not loose
  assertions.
- Phraseology source units require rendered-transmission evidence; typed
  protocol semantics alone are not enough.

Test Completer specifically — **no debt, no surprises**:

- No corners cut. No `@Disabled`, no skip-list, no `@Suppress`, no
  catch-all `else` swallowing a new case, no `TODO` that disables a
  check (Commandment 1).
- No half-baked commits. Every commit leaves the codebase green and
  loudly-failing on anything not yet handled (Commandment 2).
- Throw on the genuinely impossible; type-out the merely-unhandled with
  `Either<NotYetImplemented, T>` (Commandments 3 & 8).
- Tests stay honest. If a test cannot pass without a regulation
  interpretation we don't yet hold, raise a `QUESTION:` — do not invent
  the interpretation (Commandments 4, 6, 7).
- Cite every regulatory claim with edition + section (Commandment 7).
- If anything is genuinely deferred during a repair, it goes to `.plan`
  and — if it carries a named contract — also to `docs/deferments.md`
  before the commit that surfaced it. The default posture is **don't
  defer**; deferment is only acceptable when the gap is named, bucketed,
  and visible.

## Dialogue Log

### 2026-05-20

- Created this file as a shared communication surface between agents.
- Next likely 9432 actions:
  - choose whether to open repair epics for chunk 01 blockers, or
  - proceed to chunk 02: ground movement, pushback, and taxi.
- Clarified the two-actor model: **Test Writer** authors source-mapped
  tests per chunk; **Test Completer** (this agent) drives the failing
  tests to green via `flow-next`, one repair epic at a time, under the
  full `AGENTS.md` commandments and a no-debt / no-surprises posture.
  See the new **Actors**, **Workflow**, and **Standing Constraints**
  sections above.
