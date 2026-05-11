---
title: "Renumbering grep walk must span full §-range, not just hypothesis focal sections"
date: "2026-05-11"
track: bug
category: build-errors
module: wiki/data-sources/cap413-edition-24-capture.md
tags: [citation-discipline, renumbering, grep-walk-completeness, fn-17, errata-footers]
problem_type: build-error
symptoms: Stale §-number cites in current-doctrine prose at Controller.kt + AGENTS.md after a focal-range-only cleanup; closed-spec errata footers missing mappings for non-focal §-numbers actually cited in those specs
root_cause: Step 4 grep walk character class limited to the renumbering hypothesis's focal sections (§4.6x) when the actual Ed 23→Ed 24 shift spanned a wider range (§4.4x-§4.5x-§4.6x); errata footer scope inherited the same focal narrowing
resolution_type: fix
related_to: [bug/build-errors/stop-and-report-deferment-contracts-2026-05-09]
---

## Problem

CAP 413 §-renumbering reconciliation work was scoped initially to the
"focal" §4.6x range (the hypothesis sections) plus the typed-entry
audit. The Step 4 grep walk `rg '§4\.6[5-8]|CAP413_4_6[5-8]'`
explicitly excluded §4.5x. But Ed 23 → Ed 24's renumbering was a
uniform `-1` shift across BOTH the §4.5x (continue-approach /
landing-clearance) AND the §4.6x (missed-approach / VFR-continue)
ranges. So updating only §4.6x cites left stale `§4.55-4.56` /
`§4.53` cites in current-doctrine prose at `Controller.kt`,
`AGENTS.md`, and the closed-epic spec errata footers.

Codex reviewer round 3 caught the gap: production-code KDoc still
read "CAP 413 §4.55-4.56 + ICAO 4444 §12.3.4.16(d)" alongside
properly-updated §4.64 cites — internally incoherent (mixing Ed 23
metadata with Ed 24 metadata on the same paragraph). Closed-spec
errata footers were similarly narrow — fn-12's footer only mapped
§4.65-§4.68 even though fn-12's body cites §4.53 and §4.55/§4.56;
fn-13's footer omitted §4.53.

## What Didn't Work

Limiting the grep walk's character class to just the focal range
`§4.6[5-8]`. The hypothesis predicted only the §4.6x shift, but the
actual mapping extended across §4.4x-§4.5x-§4.6x. Scoping the cleanup
sweep to the hypothesis range guarantees a stale-cite tail in the
adjacent ranges.

## Solution

For any CAP 413 (or analogous) edition-numbering cleanup:

1. **First-pass discovery grep** should cover the FULL `§4\.[0-9]+`
   range (or the broader §-number space of the cited document), NOT
   just the hypothesis's focal sections. The grep walk's role is
   audit completeness, not hypothesis confirmation.

2. **The Table 2 typed-entry audit** in the verification artifact
   drives mapping completeness — every existing `CAP413_*` symbol
   gets a row, including symbols outside the focal §-range, because
   their `.section` fields are stable structured data that must
   match the verified Ed 24 mapping.

3. **Closed-spec errata footers** must enumerate EVERY stale
   §-number cited in that spec (grep the spec body, map each cite
   in the footer), not just the focal mapping. Readers using the
   footer as a navigation aid for preserved historical prose need
   the complete mapping for that spec.

## Prevention

For renumbering-reconciliation tasks: include a `## Approach` step
that performs an explicit "out-of-focal-range" grep audit before
declaring R5/R6 complete. Pattern:

  rg -n '§4\.[0-9]+' <files> | awk -F'§' '{print $2}' | sort -u

This surfaces EVERY §-number cited in the touched files; cross-check
each against the verification artifact's mapping table. Hits outside
the focal range are not automatically "fine" — they may also have
shifted in the verified renumbering.

For closed-spec errata footers: after writing each footer, re-grep
the spec body for the document's section pattern and confirm every
hit is covered by a mapping entry in the footer. No "we'll just
list the focal ones" shortcut.
