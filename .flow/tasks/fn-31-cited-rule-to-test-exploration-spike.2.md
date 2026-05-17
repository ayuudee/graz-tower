# fn-31-cited-rule-to-test-exploration-spike.2 Run adversarial and retrieval grounding probes

## Description
Run two small experiments against the same cited rule family or a closely related one: an adversarial scenario-generation probe and a retrieval/grounding probe. The point is to evaluate usefulness and failure modes, not to trust model output.

For adversarial probing, ask a model to generate attempts to violate minimal cited rules, then classify each output by whether it is executable, manually translatable, design-blocked, invalid, or uncited. For retrieval/grounding, test whether lightweight retrieval over the source units is sufficient to answer which sources govern a scenario or rule claim before considering training/fine-tuning.

## Acceptance
- [ ] Prompt/model/backend details are recorded well enough to reproduce the probe.
- [ ] Generated adversarial scenarios keep target rule/source-unit identity or are marked invalid when they fail to do so.
- [ ] Each scenario is classified as executable, manually translatable, design-blocked, invalid, or useful as a defect hypothesis.
- [ ] Retrieval/grounding is tested on representative questions and evaluated for citation correctness and answer usefulness.
- [ ] The task explicitly recommends for or against further adversarial generation, retrieval tooling, and model training/fine-tuning.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
