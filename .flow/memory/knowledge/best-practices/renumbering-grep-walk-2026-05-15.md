---
title: "Renumbering reconciliation grep walks must span the full affected range"
date: "2026-05-15"
track: knowledge
category: best-practices
module: wiki/data-sources
tags: [citation-discipline, renumbering, grep-walk-completeness, errata-footers, cap413, icao, fn-17]
applies_when: Reconciling section renumbering across the codebase (CAP 413 / ICAO 4444 edition shifts, API version bumps, naming convention migrations) — especially when scoping the cleanup sweep to the hypothesis's focal sections rather than the full range from the verification artifact's mapping table.
related_to: [bug/build-errors/renumbering-grep-walk-must-span-full-2026-05-11]
---

## The rule

When reconciling a section renumbering (a regulatory document edition
shift, an API version bump, etc.) across the codebase, the **grep walk's
regex must enumerate every §-number potentially affected by the shift**,
not just the hypothesis's focal subset. The walk's role is **audit
completeness**, not hypothesis confirmation.

Use the verification artifact's mapping table as the authoritative range,
not the prose description.

## Why it bites

`bug/build-errors/renumbering-grep-walk-must-span-full-2026-05-11.md`:
fn-17's CAP 413 §-renumbering reconciliation was scoped initially to the
"focal" §4.6x range (the hypothesis sections that motivated the task).
The Step 4 grep walk used `rg '§4\.6[5-8]|CAP413_4_6[5-8]'`, explicitly
excluding §4.5x and §4.4x.

But CAP 413 Ed 23 → Ed 24's actual renumbering was a uniform `-1` shift
across BOTH the §4.5x range (continue-approach / landing-clearance) AND
the §4.6x range (missed-approach / VFR-continue) — the verification
artifact's Table 2 typed-entry audit showed the full span. Updating only
§4.6x cites left stale `§4.55-4.56` / `§4.53` references in
current-doctrine prose at `Controller.kt:917`, `AGENTS.md:281`, and the
closed-epic spec errata footers for fn-12 and fn-13.

Codex round-3 review caught the gap: production-code KDoc still read
`CAP 413 §4.55-4.56 + ICAO 4444 §12.3.4.16(d)` alongside properly-updated
§4.64 cites. Internally incoherent — mixing Ed 23 metadata with Ed 24
metadata on the same paragraph.

The cause is generator-level: scoping the cleanup sweep to the hypothesis
range **guarantees** a stale-cite tail in the adjacent ranges of a
uniform shift. The hypothesis predicts what triggered the task; the
mapping table tells you what actually shifted.

## When this applies

- **CAP 413 / ICAO 4444 / other regulatory document edition shifts** in
  citation prose, KDoc, README, errata footers.
- **API version bumps** where endpoint paths or method signatures shift
  in lockstep across a family of routes.
- **Naming convention migrations** where a prefix or suffix changes
  across a class of identifiers.
- **Closed-spec errata footers** that map historical cites in archived
  epic prose — every cite in the spec body must be in the footer.

## Forward-applicable checklist

Before declaring a renumbering-reconciliation R-criterion complete:

1. **First-pass discovery grep covers the FULL §-number space**, not the
   focal subset:
   ```bash
   rg -n '§4\.[0-9]+' <files> | awk -F'§' '{print $2}' | sort -u
   ```
   This surfaces EVERY cited §-number; cross-check each against the
   verification artifact's mapping table.

2. **The verification artifact's typed-entry audit drives mapping
   completeness.** Every existing `CAP413_*` symbol (or analog) gets a
   row in the mapping table, including symbols outside the focal range —
   their `.section` fields are stable structured data that must match
   the verified mapping.

3. **Closed-spec errata footers enumerate EVERY stale §-number cited
   in that spec.** After writing each footer, re-grep the spec body for
   the document's section pattern and confirm every hit is covered by a
   mapping entry. No "we'll just list the focal ones" shortcut — readers
   using the footer as a navigation aid for preserved historical prose
   need the complete mapping.

4. **Out-of-focal-range hits are not automatically "fine."** They may
   also have shifted in the verified renumbering. Treat them as
   in-scope-for-audit by default; carve out only with explicit evidence
   the cited §-number did NOT shift in this edition.

## Cross-references

- Source capture (kept as authoritative event record):
  `.flow/memory/bug/build-errors/renumbering-grep-walk-must-span-full-2026-05-11.md`
