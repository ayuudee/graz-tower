# Requirements Spike

Small prototypes for the requirement-first ATC source-ingestion pipeline.

## Current direction (2026-04-25)

Two lanes coexist:

- **Primary lane — Ollama-first prototype** (`run_icao4444_ollama_first_prototype.py`).
  Local Ollama models do structure proposal, requirement extraction, adversarial
  validation, and promotion judgement. Three deterministic post-steps sit between
  LLM stages (sibling-symmetry resolution, bundle-gate authority override,
  judge-conservatism override). Per-family widening goes through this lane —
  add a new `CASES` entry and run, do not extend the deterministic lane below.
  Currently covers nine source-family slices across three authority ceilings:
  - `readback_family` and `transfer_family` — ICAO 4444 (authoritative_requirement)
  - `sera_readback_family` — SERA Reg (EU) 923/2012 SERA.8015(e) (authoritative_requirement)
  - `egast_readback_family` — EGAST VFR (best_practice)
  - `safetysense22_readback_family` — UK CAA SafetySense Leaflet 02 (best_practice)
  - `slovenia_vfr_readback_family` — Slovenia VFR phraseology guide (best_practice)
  - `h01_readback_family` — AIC A 21/23 H01 §3.8.1 (operational_guidance, bilingual)
  - `cap413_readback_family` — CAP 413 §2.68–2.71 (operational_guidance)
  - `icao9432_readback_family` — ICAO Doc 9432 §2.8.3 English (operational_guidance)
  Architecture and pattern rationale:
  - [ollama-first pipeline design](../../../docs/design/icao4444-ollama-first-pipeline-design.md)
  - [three deterministic post-steps pattern](../../../wiki/design-decisions/2026-04-25-three-deterministic-post-steps-pattern.md)
  - [five-source-family coverage](../../../wiki/design-decisions/2026-04-27-five-source-family-coverage.md)
  - Contract tests for the post-steps: `test_override_contracts.py`.
- **Fallback lane — deterministic ICAO 4444 normalizer + seeded promotion**.
  The pre-pivot lane lives on as comparison/scaffolding and as the source of
  the checked-in golden fixtures. The seeded set is **frozen at the 7 clauses**
  (`4.3.2.1.1`, `4.3.2.1.2`, `4.3.2.1.3`, `4.5.7.5.1`, `4.5.7.5.1.1`,
  `4.5.7.5.2`, `4.5.7.5.2.1`) listed in `golden/icao4444/fixture_manifest.json`.
  The mechanically eligible baseline still contains hundreds of bundles; the
  policy at `downstream/icao4444_downstream_policy.json` blocks them. **Do not
  widen the deterministic seeded set by mechanical rules** — see RR-1 in
  `.plan` for the staged-rollout decision.

Current goals:

- prototype Ollama-first source-structure extraction on narrow real slices
- record what works, what drifts, and what must be guarded with deterministic
  post-steps
- provide a fixed benchmark set and review rubric for comparing source families

## Current Slice

- Doc 4444 readback and transfer-of-communications clauses
- CAP 413 frequency-change and readback paragraphs
- H01 acknowledgement/readback and frequency-transfer clauses
- Doc 9432 transfer-of-communications and read-back requirements
- SERA general/regulatory structure as a control sample
- EGAST VFR advisory readback / conditional-clearance guidance

## Scripts

- `prototype_slice.py`
  Extracts sample `SourceUnit`-like structures from the real documents using
  method-specific structural heuristics.
- `ollama_requirement_trial.py`
  Sends one normalized source-unit payload to a local Ollama model and records
  the raw structured extraction attempt.
- `run_benchmark_matrix.py`
  Regenerates the prototype slices, runs the benchmark manifest against one or
  more models, writes per-case results, a summary, and a judgement template CSV.
- `summarize_benchmark_judgements.py`
  Summarizes a filled judgement CSV by outcome, model, and source family.
- `build_icao4444_bundles.py`
  Deterministically builds bundle candidates for the clean `ICAO 4444`
  production slice from normalized source units.
- `bundle_atom_trial.py`
  Runs a local model against a deterministic requirement bundle to test whether
  bundle-aware atom extraction behaves better than flat source-unit extraction.
- `icao4444_normalizer_lib.py`
  Shared typed artifact model and deterministic whole-document normalization
  logic for `icao4444-extracted.txt`.
- `normalize_icao4444.py`
  Runs whole-document deterministic normalization and writes the initial
  structural artifacts.
- `validate_icao4444_normalization.py`
  Applies the machine-gate structural checks to a normalization run.
- `render_icao4444_normalization_summary.py`
  Produces a compact markdown summary from a normalization run and its
  validation report.
- `build_icao4444_review_pack.py`
  Builds the targeted review queue and markdown review pack for a normalization
  run.
- `run_icao4444_pipeline.py`
  Runs the whole deterministic `ICAO 4444` pipeline end-to-end: normalization,
  machine-gate validation, summary, review pack, optional golden regression,
  downstream baseline, and seeded-promotion regression.
- `export_icao4444_golden_fixtures.py`
  Emits checked-in golden fixtures from a passing whole-document normalization
  run.
- `check_icao4444_golden_regression.py`
  Checks a normalization run against the checked-in golden fixture set.
- `build_icao4444_downstream_baseline.py`
  Applies the downstream eligibility policy to a passing normalization run and
  emits the first baseline bundle set plus blocked-bundle reasons.
- `build_icao4444_seeded_promotions.py`
  Builds the checked full-document seeded downstream artifacts from the passing
  baseline for the current seeded set: `ICAO 4444 §§4.3.2.1.1`, `4.3.2.1.2`,
  `4.3.2.1.3`, `4.5.7.5.1`, `4.5.7.5.1.1`, `4.5.7.5.2`, and `4.5.7.5.2.1`.
- `check_icao4444_seeded_promotions.py`
  Checks a generated seeded-promotion run against the checked seeded-promotion
  fixture set, normalizing run-local manifest paths before comparison.
- `build_icao4444_safe_cases.py`
  Builds deterministic downstream-safe generated case artifacts from seeded
  accepted `ICAO 4444` promotions using an explicit capability profile.
- `check_icao4444_safe_case_generation.py`
  Compares a generated safe-case run against the checked proof-case fixtures.

Supporting artifacts:

- `benchmark_manifest.json`
  Fixed benchmark cases for the first tranche of registry research.
- `benchmark_manifest_guardrail_subset.json`
  Focused judged-failure subset for prompt-guardrail experiments.
- `judgement_rubric.md`
  Review rubric for accepting, splitting, bundling, or rejecting candidate
  requirements.
- `benchmark_judgements_2026-04-23.csv`
  First-pass human adjudication of the initial 24-case benchmark run.
- `bundle_atom_judgements_2026-04-23.csv`
  First-pass human adjudication of bundle-aware atom extraction on the clean
  `ICAO 4444` production slice.
- `downstream/icao4444_bundle_prototype.json`
  Checked-in bundle prototype for the first deterministic `ICAO 4444` slice.
- `downstream/*.accepted_atoms.json`
  First promoted atom sets from the clean `ICAO 4444` production slice.
- `downstream/*.test_candidate.json`
  Downstream test-spec candidates derived from promoted atoms.
- `downstream/*.review_candidate.json`
  Advisory review or suspicion-seed consumer artifacts for best-practice
  sources.

Current whole-document normalization status:

- the `ICAO 4444` lane now has a full-document machine-gate pass
- reviewable residual structure is represented as `label_stub`,
  `table_fragment`, and `separator_line` rather than `unknown_structure`
- the review-pack generator is bounded and deterministic rather than emitting
  every heading and note in the document
- the tranche-exit and appendix/table golden fixtures are checked in under
  `golden/icao4444/`
- the initial downstream policy is checked in at
  `downstream/icao4444_downstream_policy.json`
- the first checked safe-case capability profile is under
  `downstream/generated/icao4444/capability_profiles/`
- the first checked safe-case proof outputs are under
  `downstream/generated/icao4444/controller_readback_v1/`

## Typical Usage

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/prototype_slice.py \
    --output-dir /tmp/requirements-spike"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/ollama_requirement_trial.py \
    --input /tmp/requirements-spike/icao4444_readback.json \
    --unit-id icao4444_readback::clause::4.5.7.5.1 \
    --model qwen3.6:35b-a3b \
    --output /tmp/requirements-spike/ollama-4444-readback.json"
```

The second command assumes `biggy:11434` is reachable from the environment.

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/run_benchmark_matrix.py \
    --manifest research/tools/requirements-spike/benchmark_manifest.json \
    --output-dir /tmp/requirements-benchmark-run \
    --models qwen3.6:35b-a3b qwen2.5-coder:32b"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/run_benchmark_matrix.py \
    --manifest research/tools/requirements-spike/benchmark_manifest_guardrail_subset.json \
    --output-dir /tmp/requirements-benchmark-guardrail-run \
    --prompt-variant family_guarded \
    --models qwen3.6:35b-a3b qwen2.5-coder:32b"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/summarize_benchmark_judgements.py \
    --csv research/tools/requirements-spike/benchmark_judgements_2026-04-23.csv"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/build_icao4444_bundles.py \
    --output /tmp/icao4444-bundles.json"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/bundle_atom_trial.py \
    --input research/tools/requirements-spike/downstream/icao4444_bundle_prototype.json \
    --bundle-id icao4444-extracted:4.5.7.5.1 \
    --model qwen3.6:35b-a3b \
    --output /tmp/icao4444-readback-bundle-trial.json"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/normalize_icao4444.py \
    --output-dir /tmp/icao4444-normalization-run"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/run_icao4444_pipeline.py \
    --output-dir /tmp/icao4444-pipeline-proof \
    --require-machine-gate-pass \
    --require-golden-pass \
    --require-seeded-promotion-pass"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/validate_icao4444_normalization.py \
    --run-dir /tmp/icao4444-normalization-run \
    --unknown-structure-threshold 0"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/render_icao4444_normalization_summary.py \
    --run-dir /tmp/icao4444-normalization-run"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/build_icao4444_review_pack.py \
    --run-dir /tmp/icao4444-normalization-run"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/export_icao4444_golden_fixtures.py \
    --run-dir /tmp/icao4444-normalization-run \
    --output-dir research/tools/requirements-spike/golden/icao4444"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/check_icao4444_golden_regression.py \
    --run-dir /tmp/icao4444-normalization-run \
    --fixture-dir research/tools/requirements-spike/golden/icao4444 \
    --report /tmp/icao4444-golden-regression-report.json"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/build_icao4444_downstream_baseline.py \
    --run-dir /tmp/icao4444-normalization-run \
    --output /tmp/icao4444-downstream-baseline.json"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/build_icao4444_seeded_promotions.py \
    --run-dir /tmp/icao4444-normalization-run \
    --baseline /tmp/icao4444-downstream-baseline.json \
    --all-seeded \
    --output-dir research/tools/requirements-spike/downstream/full_document_seeded"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/check_icao4444_seeded_promotions.py \
    --promotion-dir /tmp/icao4444-pipeline-proof/seeded_promotions \
    --fixture-dir research/tools/requirements-spike/downstream/full_document_seeded \
    --report /tmp/icao4444-seeded-promotion-regression-report.json"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/build_icao4444_safe_cases.py \
    --promotion-dir research/tools/requirements-spike/downstream/full_document_seeded \
    --output-dir /tmp/icao4444-safe-cases"
```

```bash
nix-shell -p python3 --run \
  "python3 research/tools/requirements-spike/check_icao4444_safe_case_generation.py \
    --run-dir /tmp/icao4444-safe-cases \
    --fixture-dir research/tools/requirements-spike/downstream/generated/icao4444/controller_readback_v1 \
    --report /tmp/icao4444-safe-case-regression-report.json"
```
