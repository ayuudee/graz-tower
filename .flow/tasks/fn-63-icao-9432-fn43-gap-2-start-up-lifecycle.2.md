# fn-63-icao-9432-fn43-gap-2-start-up-lifecycle.2 Implement start-up lifecycle evidence or explicit block

## Description
Based on the manifest, either implement the minimal real start-up lifecycle/evidence path or make the remaining block loudly visible without false-greening the source units.
## Acceptance
No lifecycle source unit moves green without real approval/start evidence. Phraseology and policy rows remain blocked. Tests pin the chosen movement.
## Done summary
No implementation was added. Reviewed impact showed an evidence-only patch would be a false green: StartupApproved has no sim state effect, default engineRunning is not an engine-start event, and CLEARANCE_DELIVERY commitments are unmodelled. The lifecycle row remains explicitly blocked by D-PF.1.
## Evidence
- Commits:
- Tests: .flow/bin/flowctl validate --epic fn-63-icao-9432-fn43-gap-2-start-up-lifecycle, git diff --check
- PRs: