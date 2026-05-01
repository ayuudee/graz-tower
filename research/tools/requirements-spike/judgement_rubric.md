# Requirement Spike Judgement Rubric

Use this rubric when reviewing model output against a benchmark case.

## Outcome Labels

- `accepted`
  The candidate is source-grounded, correctly classified, and can enter the
  provisional registry unchanged.

- `needs_split`
  The candidate bundles multiple obligations or conditions that should be
  represented separately.

- `needs_bundle`
  The candidate should not stand alone and must remain attached to sibling
  obligations, notes, or examples.

- `ambiguous`
  The source does not support a single clear interpretation without further
  human review.

- `unsupported_by_source`
  The candidate overstates or invents semantics not justified by the source
  unit.

- `advisory_only`
  The candidate is useful, but it must remain advisory/support material and not
  enter the authoritative requirement registry.

- `needs_human_review`
  The candidate is plausible but the risk of misclassification is high enough
  that a human should resolve it before promotion.

## Review Questions

For each candidate, answer these:

1. Does the candidate stay within the actual source unit text?
2. Is the authority class correct, or is it overpromoted?
3. Does the candidate preserve important conditions or qualifiers?
4. Should this stand alone, or should it remain attached to a parent/bundle?
5. Would this be safe to hand to a downstream consumer without misleading it?

## Common Failure Modes

- Example text treated as a rule
- Advisory text treated as law
- Parent list item promoted without its governing clause
- Bilingual/manual text collapsed into one opaque claim
- Compound clause flattened into fake certainty
- Heading or section label treated as a substantive requirement
