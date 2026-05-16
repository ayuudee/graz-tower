# Review: EPPLS Chapter 12 Intake Plan

Generated: `2026-05-15`

Reviewed plan: `plan.md`

## Verdict

The plan is fit to execute as an intake and inventory plan. It should not yet
create an EPPLS manifest or run model ingestion, because source identity and
line-stable text extraction are still unresolved.

## Findings

### High: PDF identity is not yet established

The file is named `EPPLS.pdf`, but local metadata only exposes the title
`Front.fm`. The plan correctly blocks on title/edition verification before
using the file as a source. This is necessary because filename-only identity
would not satisfy source provenance requirements.

### High: Text extraction is a hard gate

The PDF is reported by macOS metadata as password-encrypted, and the current
shell lacks Poppler tools. The plan correctly requires deterministic extraction
and stable line ranges before any manifest window is created. Without that,
quote audit and source-window reproducibility would be impossible.

### Medium: Authority ceiling must remain conservative

The plan correctly avoids assuming EPPLS is authoritative. The initial ceiling
should stay `background_support` or `best_practice` until the actual document
identity and issuing body are verified from the PDF text itself.

### Medium: OCR fallback needs a high bar

The plan allows OCR only if it can be made reproducible enough for line-number
provenance. That is the right constraint. If OCR is needed, the execution task
should record OCR tool/version/settings and should prefer page-scoped inventory
over promotion unless exact quote matching is reliable.

## Review Considerations

**FP / type safety:** The source-state set in the plan is explicit and avoids a
silent success state. Execution should preserve that by writing a blocker state
when identity or extraction fails.

**Test architecture:** The plan includes the right gates: checksum, extraction
command, line ranges, dry-run queue, raw-root audit, quote/schema gates, and
registry reproducibility. The only addition during execution should be a small
machine-readable intake record so later tasks can consume the state without
parsing prose.

**Impact:** A dedicated EPPLS package avoids contaminating the current-frame
output with unreviewed pilot-side/training content. The cost is an extra package
and explicit non-claims, which is appropriate for a one-off close-out.

**Operational correctness:** The plan does not state any EPPLS regulatory or
phraseology facts beyond local metadata. That is correct. Operational claims
must wait until Chapter 12 text is identified with exact section/line
provenance.

## Required Execution Notes

- Do not create `documents/eppls.json` until identity and extraction gates pass.
- Do not promote EPPLS records into the current-frame package.
- Record a blocked state if Poppler/text extraction cannot read the encrypted
  PDF.
- Keep checking the active v6 retry run while this source-intake work proceeds.
