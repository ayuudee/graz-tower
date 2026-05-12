# Deferments Register

This file is the **map** of named deferments in the repo. Every deferred
contract — work consciously parked with a real-fix contract (eventual API
shape, blocked-on prerequisite, named closure trigger) — has exactly one
entry here. The four-bucket model determines where the canonical record
lives; the `Pinned at:` field points readers at it. See
[`deferments-CONVENTION.md`](./deferments-CONVENTION.md) for the decision
tree, schema, status taxonomy, and lifecycle.

**How to read this file.** Entries are grouped by ID prefix into four
top-level subsections (`## D-PF`, `## D-AUDIT`, `## D-PASS`, `## D-WORLD`).
Closed entries live in `## Archive` at the bottom. Status taxonomy is four
leaves: `blocked` / `planned` / `narrative` / `closed`. **Heading
discipline**: only `### D-...` headings denote a deferment entry —
`grep -c '^### D-' docs/deferments.md` counts entries. Section-organising
headings use `##` depth; empty-body placeholders use one-line prose.

## D-PF

_(populated by fn-18.2 from `~/.claude/plans/pilot-firewall.md`.)_

## D-AUDIT

_(populated by fn-18.2 from `~/.claude/plans/pilot-firewall.md`.)_

## D-PASS

### D-PASS-deferments-map-tooling-automation — Tooling automation over deferments map
**Status:** narrative
**Pinned at:** narrative only
**Why:** v1 of the deferments register ships the human-readable map only; drift detection between `docs/deferments.md`, inline `// D-PASS-*` / `// D-AUDIT-*` / `// D-PF.*` code comments, and `Pinned at:` test paths is currently grep-and-eyeball. A detekt rule or repo-root script that parses `docs/deferments.md`, verifies every `Pinned at:` test or epic exists, and asserts every inline deferment-ID comment appears as an entry would turn that drift into a CI failure.
**Closes by:** new epic when CI tooling lift becomes worthwhile.

### D-PASS-deferments-renumbering-discipline — Mixed ID-scheme cleanup
**Status:** narrative
**Pinned at:** narrative only
**Why:** The current deferment-ID scheme mixes legacy dotted forms (`D-AUDIT.7.II-FOLLOWUP`, `D-PASS-13.3-II-FOLLOWUP`) from the pre-flow-next pass-N tracking with fn-7+ dash-suffixed names (`D-PASS-g3a-react-tailwind-limit`, `D-AUDIT-polygon-ctr`). The mix produces inconsistent grep patterns and visual scan noise. v1 preserves all existing IDs as-is to bound migration scope; a future cleanup pass would settle on the dash-suffixed form (which survives renumbering) and script-rewrite all references.
**Closes by:** new epic when settling on a single ID convention.

### D-PASS-deferments-cross-ref-from-impl-review — Defer flow for review findings
**Status:** narrative
**Pinned at:** narrative only
**Why:** When a code-review agent (RepoPrompt / Codex) surfaces a finding that the principal agent defers, the convention for "this becomes a deferment" is currently a manual sibling-file step — the agent has to remember the four-bucket model, pick the right bucket, and write the entry by hand. A `/flow-next:defer` skill (or similar) that prompts for bucket assignment and writes the record would make the convention's discovery surface match the convention's discipline.
**Closes by:** new epic when a `/flow-next:defer` skill is justified.

_(remaining D-PASS-* entries populated by fn-18.2 / fn-18.3.)_

## D-WORLD

_(populated by fn-18.2.)_

## Archive

_(closed entries land here per the convention's archive policy — see `docs/deferments-CONVENTION.md § 8`. Active entries use the full schema; archive entries use the three-field locked form `Status:` + `Closed by:` + `Enforcement:`.)_
