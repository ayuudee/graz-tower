# 2026-04-25 Bundle Gate And Challenger Guards Stabilise The Judge

## Decision

Per-candidate adjudication in the Ollama-first prototype now has four
independent stages, in this order:

1. **Challenger** — content and authority.
2. **Defender** — source-grounded justification.
3. **Bundle gate** — pure structural-completeness yes/no.
4. **Judge** — uses challenger and defender for content/authority and the
   bundle gate's `scopeComplete` mechanically for bundle/split decisions.

The challenger is also tightened with three guards:

- modality must be derived only from the candidate's own `sourceItemIds`;
- ICAO Notes are non-normative unless they contain `shall`;
- authority verdicts are directional (`authority_too_high` / `authority_too_low`)
  and the verdict direction must match the concern text.

## Why

Three additive spikes against the v4 baseline produced clean attribution:

- Spike 1's directional rename surfaced a hidden cross-clause contamination
  bug.
- Spike 2's modality-grounded challenger eliminated contamination but exposed
  three further bugs (Notes-as-authoritative, direction inversion, structural
  concerns mis-labelled as authority).
- Spike 3's challenger guards plus bundle gate produced the cleanest
  judgement surface so far, including resolution of the v4 VMC/IMC judge
  inconsistency that was the residual of RR-3.

The bundle gate is the load-bearing change: by giving the judge a mechanical
answer to "does this candidate's scope require items it does not include?",
the judge stops re-litigating bundle/split from the candidate alone, which
was the source of the VMC/IMC instability.

## Evidence

- [icao4444-ollama-first-bundle-gate-and-challenger-guards-2026-04-25.md](docs/design/icao4444-ollama-first-bundle-gate-and-challenger-guards-2026-04-25.md)
- [transfer Spike 3 summary](/tmp/icao4444-ollama-first-prototype-spike-3-transfer/summary.md)
- [readback Spike 3 summary](/tmp/icao4444-ollama-first-prototype-spike-3-readback/summary.md)

## Consequence

- The default Ollama-first per-candidate flow is now: challenger → defender →
  bundle gate → judge.
- `wrong_authority` is removed from the verdict enum; replaced by directional
  pair.
- Structural concerns belong on the bundle gate, not in authority verdicts.
- `RR-5` tracks the residual challenger noise: mixed-modality structural
  concerns are still labelled `authority_too_high` despite the prompt rule.
  This does not affect final outcomes today because the bundle gate provides
  the structural signal directly.
