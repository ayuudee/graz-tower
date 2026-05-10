# Current source-unit production readiness

## Conversation Evidence
- [user] "Remember the ultimate goal. We simply want to get all the sources translated into units, have all that validated, and be confident that our structured representation (for all sources) is accurate."
- [user] "There is no need to integrate them at this point. In my view that's a secondary stage ... The end goal is that we should have clear source units for each document, validated, and then we'll work out how to integrate them all."
- [user] "Let's work on 3 levels of plan: (1) sources to complete; (2) sections within sources; and (3) progress within section."
- [user] "Good work. Continue. Let's get all the current docs as complete as possible, in as production-ready state as possible. Then we'll move on to nolan and eppls and see what, if anything we can take. Do a gap analysis of that, then review/red-team that (independent), then present it to me as a plan."
- [user] "$flow-next-capture the 6 step plan above."
- [user] "approve."
- [paraphrase] The approved six-step plan is: stabilize the current frame, retry the failed windows, curate pending candidates against the correct run evidence, resolve H01, run adequacy and closure reviews, then build source packages.

## Goal & Context
Complete the current source-unit corpus to a production-ready state before widening to Nolan or EPPLS. [paraphrase]

Production-ready means each current source is represented discretely as structured source units, with source/section/tactical progress visible, validation evidence attached, and residual non-claims stated explicitly rather than implied away. [paraphrase]

This epic is not an integration epic. It does not decide which source wins across overlapping authorities, deduplicate across books, or convert the outputs into downstream controller rules. It prepares trustworthy per-source artifacts so that later integration can choose from honest inputs. [user]

## Scope
In scope:

- Stabilize the current status baseline and make stale counts, stale run roots, and unresolved blockers visible. [paraphrase]
- Retry the known failed source windows and convert each outcome into either ingested units or a blocking defect record. [paraphrase]
- Resolve the H01 blocker by completing the missing source-side check or by making a scoped non-claim explicit. [paraphrase]
- Curate pending candidates only when the producing run evidence is available and correctly associated with the candidate. [paraphrase]
- Run adequacy and closure reviews before packaging. [paraphrase]
- Build per-source packages only after their validation gates pass. [paraphrase]

Out of scope:

- Nolan and EPPLS ingestion, except for preserving them as the next-phase gap-analysis target. [user]
- Cross-source integration, source precedence, and downstream rule selection. [user]
- New theme discovery or open-ended slice invention. [paraphrase]

## Current Source Frame
Task 1 must produce the explicit current-source frame used by the rest of the epic. The default control set is every document manifest currently present in the requirements-spike document manifests, plus any source already represented in the live registry but missing from those manifests. Nolan and EPPLS are excluded from the current package claim unless they are already present in that manifest/registry control set; otherwise they are named as future gap-analysis inputs. [inferred]

The frame artifact must classify each source as included, excluded-future, or blocked-ambiguous, and later tasks must use that artifact rather than reinterpreting the corpus. [inferred]

## Execution Plan
1. Stabilize the current frame. Produce a fresh independent status report that shows source-level, section-level, and tactical-level progress for the current source set. [paraphrase]
2. Retry the 14 failed windows. Re-run or repair only the failed source windows already identified by the current frame; do not widen the source scope while this is happening. [paraphrase]
3. Resolve H01. Either complete the missing source-language/source-side check, or explicitly mark the relevant coverage as a non-claim so packages cannot imply more than they prove. [paraphrase]
4. Curate pending candidates with correct provenance. Every curation decision must use the candidate's producing run evidence and must record a reasoned promote, demote, or defer outcome. H01-created pending records must be present before this task starts. [paraphrase]
5. Run adequacy and closure reviews. Independently check counts, failure ledgers, quote/source traceability, high-value samples, and remaining gaps before packaging. [paraphrase]
6. Build source packages. Emit per-source packages with source units, provenance, validation status, and known residual gaps only after the hard gates pass. [paraphrase]

## Artifact Contracts
Each source package must contain the structured units for one source, provenance sufficient to find the supporting source text, validation evidence, and any explicit non-claims. [paraphrase]

The status report is the operational control surface. It must always show the three levels the user requested: source completion, section completion, and tactical progress or blockers. [user]

The parse-failure and pending-curation ledgers are blocking evidence, not optional notes. A package cannot be marked ready while an unresolved blocker applies to it. [inferred]

Source packages must be written under a single package root for the run, named by timestamp or run id. That root must contain a package manifest, one package JSON per source, and a validation report. Each per-source package must include: schema name/version, document id, source path, included manifest sections, package status (`ready`, `scoped_ready`, or `blocked`), source units, provenance, validation evidence, blockers, and explicit non-claims. [inferred]

A package root is valid only if a deterministic validation command or recorded validation report reconciles it against the closure review, live registry counts, and final three-level status report. If no validator exists yet, task 6 must add a narrow validator or block package completion. [inferred]

## Edge Cases & Constraints
A candidate with missing or mismatched producing run evidence is not curated optimistically; it remains blocked until the evidence is recovered or the candidate is explicitly rejected as unverifiable. [inferred]

A source may become package-ready even if another source is still blocked, provided the package does not claim cross-source completeness. [inferred]

A source package may include explicit non-claims, but those non-claims must be prominent enough that downstream consumers cannot mistake them for omissions. [paraphrase]

A blocked quote/source audit prevents full `ready` package status for affected sources. Such sources must be `scoped_ready` with the missing audit called out, or `blocked` if the missing audit undermines traceability. [inferred]

Nolan and EPPLS are deliberately deferred until the current source set is made as complete as possible and reviewed. [user]

## Review Considerations
FP / type safety: This epic is mostly data-pipeline and research-artifact work, but the equivalent totality rule still applies. Status values must be explicit, blockers must not be hidden behind skip lists, and unexpected parser or curation states must fail loudly rather than producing plausible empty output. [paraphrase]

Test architecture: Verification should favor deterministic gates and reproducible ledgers over ad hoc sampling. Sampling is useful for adequacy review, but it cannot replace quote/source traceability, status-count reconciliation, and package-gate checks. [paraphrase]

Impact: The main risk is false confidence. Keeping sources discrete reduces premature coupling, while hard gates and explicit non-claims make later integration safer because it can choose among honest source packages. [paraphrase]

Operational correctness: Every regulatory or phraseology claim inside the structured units must remain traceable to the source document and section. No ATC-law or phraseology claim should be accepted without a precise citation to source text. [user]

## Acceptance Criteria
- **R1:** A fresh independent status report exists for the current source set, showing source-level completion, section-level completion, tactical blockers, stale prior claims, and the current next action for each source. [paraphrase]
- **R2:** The 14 failed windows from the current frame are all retried or repaired; each window ends as ingested, explicitly rejected as not a valid source-unit window, or recorded as a blocking defect with enough evidence to continue later. [paraphrase]
- **R3:** Pending candidates are curated only against their correct producing run evidence after H01 resolution has either completed or produced its own pending records; no package-ready source has pending candidates left unresolved because of stale or mismatched run roots. [paraphrase]
- **R4:** The H01 blocker is resolved by completing the missing source-side check or by recording a clear scoped non-claim; no package implies H01 completeness beyond that evidence. [paraphrase]
- **R5:** Adequacy and closure reviews are complete before packaging, covering count reconciliation, failure ledgers, source traceability, high-value sample review, and residual-gap review. A blocked quote/source audit prevents full package-ready status for affected sources. [paraphrase]
- **R6:** Per-source packages are built only after their hard gates pass, and each package includes source units, provenance, validation status, explicit residual gaps or non-claims, and a validation report that reconciles the package inventory to closure evidence. [paraphrase]
- **R7:** After current packages are ready or blocked with honest evidence, a Nolan and EPPLS gap-analysis plan is prepared and independently red-teamed before those sources are ingested. [user]

## Boundaries
This epic completes the current source-unit frame. It does not decide final regulatory precedence, merge overlapping source claims, or turn structured units into runtime ATC behavior. [user]

## Decision Context
The plan uses a source-first execution model because the user explicitly rejected theme invention and premature integration. The source packages are therefore intermediate products: honest, validated representations of individual texts, ready for a later integration stage. [user]

The plan also keeps set-and-forget work bounded by hard gates. Long-running processing may continue unattended, but each chunk must still report source, section, and tactical status so overnight progress does not create ambiguous coverage claims. [paraphrase]

## Inference Tally
- [inferred] The current source set means the active manifest/registry-controlled sources, excluding Nolan and EPPLS until the next gap-analysis stage unless they already appear in that control set.
- [inferred] Source packages are per-source artifacts, not an integrated downstream registry.
- [inferred] Parse-failure and pending-curation ledgers are hard package blockers when they apply to a source.
- [inferred] A package root needs an explicit package manifest and validation report so package readiness is objective.
- [inferred] A blocked quote/source audit cannot support full package-ready status.
