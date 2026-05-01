# Bundle Atom Judgement Rubric

Use this rubric when reviewing bundle-aware atom extraction on the first
production slice.

## Outcome Labels

- `accepted`
  The atoms are structurally sound, source-grounded, and safe to promote with at
  most minor clerical cleanup.

- `accepted_with_editorial_fixup`
  The atoms are promotion-worthy, but need small manual cleanup such as adding
  missing `sourceSupport`, tightening wording, or normalizing actor names.

- `needs_split`
  The output still bundles more than one operational atom into one atom.

- `needs_bundle_context`
  The output still treats a parent/list structure as if it were standalone.

- `unsupported_by_bundle`
  The output overstates or invents semantics not justified by the bundle
  members.

- `needs_human_review`
  The output is plausible but not safe to promote without human review.

## Review Questions

1. Does each atom stay within the bundle members named in `sourceUnitIds`?
2. Does the output avoid promoting the parent clause alone when the bundle
   structure says it should not?
3. Are the resulting atoms discrete enough for promotion?
4. Are the dependencies on the parent clause explicit where needed?
5. Would the promoted atoms be safe to hand to a downstream consumer?

## Common Failure Modes

- Parent intro clause promoted as a standalone atom despite subordinate items
- Branch-specific conditions collapsed back into one atom
- Missing dependency on the parent clause
- Empty or weak `sourceSupport` despite otherwise good atoms
- Item-level atom wording that drifts beyond the actual list item
