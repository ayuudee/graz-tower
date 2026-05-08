# fn-3-complete-lean-formalization-campaign.6 Record campaign stopping rule and completion frontier

## Description
Close the campaign only when the current formalization generation has a defensible stopping point. This task records what is closed, what is intentionally outside the current model, and what future event would reopen Lean work.

Before starting this task, re-anchor against `flowctl ready --epic fn-3-complete-lean-formalization-campaign --json`; if task 5 created proof tasks that should block completion, add them as dependencies first.

## Acceptance
- [ ] Flow-Next validates with `flowctl validate --all --json`.
- [ ] Root `CertifiedAtc` builds after all campaign proof/doc updates.
- [ ] The final frontier note states the closed theorem surface, intentional out-of-scope branches, and reopening triggers.
- [ ] README/status/inventory docs agree with the final frontier note.
- [ ] The epic is ready for completion review and closure.

## Done summary
Recorded the campaign stopping rule and completion frontier. The final frontier note now states the closed scoped/current-shape/world-backed theorem surface, intentional out-of-scope branches, and concrete reopening triggers. README and PROJECT_STATUS now point at the Flow-Next completion frontier, and PROJECT_STATUS clarifies that historical proof debt is not the active current-model proof gap ledger. No proof code changed; the root CertifiedAtc build and Flow-Next validation are green.
## Evidence
- Commits:
- Tests:
- PRs: