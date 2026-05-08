# fn-3-complete-lean-formalization-campaign.5 Promote reviewed proof chores into Flow-Next tasks

## Description
Review the current-model gap ledger and the `r1` smoke output, then promote only bounded, useful proof chores into normal Flow-Next tasks. This task should not itself do broad proof work; it turns reviewed candidates into executable tasks with clear gates.

## Acceptance
- [ ] Every promoted proof chore has a task with a concrete Lean module scope, no-`sorry`/no-`admit` requirement, and root `CertifiedAtc` build evidence.
- [ ] Raw `r1` output is either rejected with a reason, left as candidate material, or promoted into a task after review.
- [ ] If no proof chores are promotable, the task records why and leaves the campaign ready for completion/stopping-rule review.
- [ ] Dependencies are updated so the completion/stopping-rule task cannot be started before any promoted proof tasks that must precede it.

## Done summary
Reviewed the current-model gap ledger and the r1 smoke outputs. No proof chore was promoted: the ledger still has no confirmed current-model gap, the generated backlog unit is only a comment-based generator smoke candidate, and the completed deterministic inspection queue produced no promoted snapshot or missing-theorem finding. Recorded the promotion decision in research/fm/FLOW_NEXT_GAP_LEDGER.md and left the campaign ready for completion/stopping-rule review without adding dependency edges.
## Evidence
- Commits:
- Tests:
- PRs: