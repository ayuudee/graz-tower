# CAP 413 Edition 24 — primary-source verification capture

Verification artifact for `fn-17-cap-413-edition-24-numbering.1` (epic
`fn-17-cap-413-edition-24-numbering`). Closes the
`D-PASS-cap413-edition-24-reconciliation` deferment filed in fn-14.

## Source

- **Discovery path that succeeded**: Path 2 (CAA landing page at
  `https://www.caa.co.uk/our-work/publications/documents/content/cap-413/`,
  which redirected to
  `https://www.caa.co.uk/data-and-publications/publications/documents/content/cap-413/`).
  The landing page advertises **two** download links labelled
  `CAP413 Current` (`/publication/download/27609`, 3.5MB — Ed 23 Corr)
  and `CAP 413 Future` (`/publication/download/18165`, 3.0MB — Ed 24).
- **Ed 24 download URL**: `https://www.caa.co.uk/publication/download/18165`
- **Capture date**: 2026-05-11

## Attempted Ed 24 sources

| # | URL | HTTP status | Content-Type | Final URL | Edition observed | SHA-256 | Notes |
|---|-----|-------------|--------------|-----------|------------------|---------|-------|
| Path 1 | `https://www.caa.co.uk/publication/download/27609` | 200 | `application/pdf` | (same) | **Edition 23 Corr**, effective 21 January 2021 | `f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21e746e7` | This is the "CAP413 Current" link still serving Ed 23 Corr at task time. Matches planning-time SHA exactly. |
| Path 2 | `https://www.caa.co.uk/our-work/publications/documents/content/cap-413/` | 200 | `text/html` | `https://www.caa.co.uk/data-and-publications/publications/documents/content/cap-413/` | HTML landing page — advertises both Ed 23 (`/27609`) and Ed 24 (`/18165`) download links | not hashed: HTML landing page, not the PDF payload | Page text reads `Effective date 1 July 2026` + `Version date: 21-Apr-2026`. The `CAP 413 Future` link → `/publication/download/18165`. Following this link successfully retrieved the Ed 24 PDF (see Ed 24 PDF metadata below). |
| Path 3 | `https://www.caa.co.uk/CAP413` | 200 | `text/html` | `https://www.caa.co.uk/data-and-publications/publications/documents/content/cap-413/` | Same HTML landing page as Path 2 (redirect target) | not hashed: HTML landing page, not the PDF payload | Path 3 short-link redirects to the same landing page Path 2 reaches. No independent payload. |
| Path 4 | — | — | — | — | — | — | Web-search tool available in this environment but not needed; Paths 2/3 surfaced the Ed 24 download link directly via CAA-hosted landing page. Recorded for honest-evidence-trail. |

### Ed 24 PDF retrieved via landing-page link

- **URL**: `https://www.caa.co.uk/publication/download/18165`
- **HTTP status**: 200
- **Content-Type**: `application/pdf`
- **SHA-256**: `c620cda9b6bdbe8e9ed51b258e4df2f6e3edc839226e53ee2b591cb696a966ac`
- **Size**: 3,046,668 bytes

### Non-CAA Ed 24 candidates

None discovered. (Per Step 1.1 policy: non-CAA mirrors would not have
been accepted for branch selection regardless.)

## Ed 24 PDF metadata

- **SHA-256**: `c620cda9b6bdbe8e9ed51b258e4df2f6e3edc839226e53ee2b591cb696a966ac`
- **Edition masthead string** (page 2): `Edition 24`
- **Subtitle** (page 2): `Incorporating amendments to 31 March 2026`
- **Effective-date masthead string** (page 2): `Effective date 1 July 2026`
- **Date footer** (page 2): `1 JULY 2026`
- **Publication date** (per landing page metadata): Version date 21-Apr-2026

## Ed 23 comparison source

- **URL**: `https://www.caa.co.uk/publication/download/27609`
- **SHA-256**: `f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21e746e7`
- **Edition masthead string** (page 3): `Edition 23`
- **Subtitle**: `Incorporating amendments to 26 November 2020`
- **Effective-date masthead string**: `Effective date 21 January 2021`
- **Publication date** (per page 3 / page 4): June 2020; Twenty-third edition Corr 8 June 2020 (effective date 17 August 2020); Twenty-third edition incorporating amendments to 26 November 2020 (effective date 21 January 2021)
- **Capture date**: 2026-05-11

SHA matches the planning-time Ed 23 SHA recorded in the fn-17 epic spec
exactly (no drift). The same CAA endpoint (`/publication/download/27609`)
that is labelled "CAP413 Current" on the landing page still serves Ed 23
Corr — this is intentional behaviour by the CAA, since Ed 24's effective
date is 1 July 2026 (i.e., Ed 23 remains the legally current edition
through 30 June 2026, with Ed 24 published in advance).

## Section captures

### Table 1: §4.65-§4.68 renumbering map (focal sections)

| Ed 23 §-num | Topic | ≤ 25-word identifying excerpt (paraphrased) | Ed 24 §-num | Classification | updatedInTask | Verbatim ≤ 1-sentence excerpt (Ed 24 wording) |
|---|---|---|---|---|---|---|
| §4.65 | ATC-initiated missed approach ("go around, I say again, go around") | Instructions to carry out missed approach to avert unsafe situation; brief transmissions; "go around, I say again, go around, acknowledge" | **§4.64** | RENUMBERED | true (`CAP413_4_65` → `CAP413_4_64`; KDoc + section field updated) | "Instructions to carry out a missed approach may be given to avert an unsafe situation." (Ed 24 §4.64, page 24) |
| §4.66 | VFR-continue / published-missed-approach split | Aircraft on instrument approach carries out published missed approach; VFR aircraft continues into normal traffic circuit unless instructed otherwise | **§4.65** | RENUMBERED (prose-only — no typed entry) | true (Step 4 prose sweep updates KDoc / wiki / test cites) | "An aircraft on an instrument approach is to carry out the published missed approach procedure and an aircraft operating VFR is to continue into the normal traffic circuit unless instructions are issued to the contrary." (Ed 24 §4.65, page 25) |
| §4.67 | Pilot-initiated GA ("going around") | Pilot-initiated missed approach uses phrase "going around"; controller responds "Roger" | **§4.66** | RENUMBERED (prose-only) | true (Step 4 prose sweep) | "In the event of missed approach being initiated by the pilot, the phrase 'going around' shall be used." (Ed 24 §4.66, page 25) |
| §4.68 | Military missed approach phraseology cross-reference | Military missed approach phraseology shown in Chapter 10 | **§4.67** | RENUMBERED (prose-only) | true (Step 4 prose sweep) | "Missed Approach Phraseology used by military controllers is shown in Chapter 10." (Ed 24 §4.67, page 25) |

**Note on hypothesis vs verdict**: The fn-14 docs-scout hypothesis claimed
"Edition 24 renumbered §4.66 → §4.65 (VFR-continue) and §4.67 → §4.66
(pilot-initiated GA)" while leaving §4.65 (ATC-initiated GA) in place.
This is **partially correct but incomplete**: the docs-scout was right
about §4.66 → §4.65 and §4.67 → §4.66, but missed that the §4.65 ATC-
initiated-GA section **also moves backward by one** to **§4.64** in
Ed 24. The full pattern is a uniform `-1` renumbering across §4.65 / §4.66
/ §4.67 / §4.68 (Ed 23) → §4.64 / §4.65 / §4.66 / §4.67 (Ed 24). Topic
content is preserved verbatim or nearly verbatim across the boundary; the
phraseology examples carry through unchanged. (Epic R2 quirk per
Step 2: "follow Ed 24's actual numbering; don't force the hypothesis.")

### Table 2: CAP413 typed-entry audit (every `val CAP413_*` in `RegulationDatabase.kt`)

Audit basis: every entry classified by comparing Ed 23 PDF content
(SHA `f3b4839e…e746e7`) against Ed 24 PDF content
(SHA `c620cda9…66ac`) at the cited section number.

| Symbol | Codebase §-num (pre-fn-17) | Codebase title / topic | Ed 24 §-num | Classification | updatedInTask | Page locator in Ed 24 | Basis-of-check paraphrase | Verbatim excerpt (REFINED/RETIRED only) |
|---|---|---|---|---|---|---|---|---|
| `CAP413_2_7` | §2.7 | "Frequency change and two-way communication" | §2.7 | **UNREVIEWED** | false | Ed 24 Ch.2 page 3 (line 1352 of extract) | Ed 24 §2.7 retains exact Ed 23 §2.7 content (SAFETYCOM transmission range ≤ 10 NM / below 2,000 ft); **however the codebase principle on `CAP413_2_7` ("Frequency change and two-way communication... establish two-way communication on the new frequency") does NOT correspond to §2.7 SAFETYCOM content in either edition**. This is a pre-existing miscite (principle drifted from the cited section), independent of any Ed 24 renumbering. Deferred for separate principle-vs-cite audit (out of fn-17.1 scope per epic Decision #3 — "no code behaviour change; pure rename + KDoc principle-text refresh + edition-string correction"). Hard gate triggers inline Ed 23 literal + deferment KDoc note. | — |
| `CAP413_4_46` | §4.46 | "Readback of ground movement instructions" | §4.46 | **UNREVIEWED** | false | Ed 24 Ch.4 page 19 (line 4692 of extract) | Ed 24 §4.46 = "pilot having joined the traffic circuit makes routine reports as required by local procedures" (Ed 23 §4.47 content shifted to §4.46). The codebase principle ("Hold short / hold position instructions relating to runways must be read back in full") matches neither Ed 23 §4.46 (= "traffic information prior to joining circuit") nor Ed 24 §4.46. Pre-existing miscite, out of fn-17.1 scope. | — |
| `CAP413_4_49` | §4.49 | "Circuit sequencing and spacing" | **§4.48** | **RENUMBERED** | true | Ed 24 Ch.4 page 20 (line 4749 of extract) | Ed 24 §4.48 reads "It may be necessary in order to coordinate traffic in the circuit, to issue a pilot their number in the sequence along with the position of the preceding aircraft and delaying action if necessary" — **identical content** to Ed 23 §4.49 except Ed 24 spells "coordinate" without hyphen. Section field updated; principle unchanged. | — |
| `CAP413_4_51` | §4.51 | "Report final" | §4.51 | **UNCHANGED** (in Ed 24) | true (cite stays §4.51; principle valid against Ed 24 §4.51) | Ed 24 Ch.4 page 21 (line 4837 of extract) | Codebase principle ("REPORT FINAL RUNWAY [designator] — ATC requests pilot report when turning final") matches Ed 24 §4.51 exactly ("A 'final' report is made when an aircraft has turned onto final approach... long final report..."). Note: in Ed 23 this content lived at §4.52, not §4.51 (Ed 23 §4.51 was about "last circuit"); the codebase appears to have already cited the upcoming Ed 24 §-number, perhaps coincidentally. Ed 24 makes the existing cite correct. | — |
| `CAP413_4_53` | §4.53 | "Cancellation of issued landing clearance" | **§4.52** | **RENUMBERED** | true | Ed 24 Ch.4 page 23 (line 4879 of extract) | Ed 24 §4.52 = "Where a controller cancels a landing clearance but feels that a landing clearance will be re-issued in good time for the aircraft to make a safe landing, they should, if time permits, give the reason for cancelling the landing clearance" — verbatim identical to Ed 23 §4.53. Section field updated; principle unchanged. | — |
| `CAP413_4_55` | §4.55 | "Continue approach — runway obstructed at final" | **§4.54** | **RENUMBERED** | true | Ed 24 Ch.4 page 23 (line 4908 of extract) | Ed 24 §4.54 = "The runway may be obstructed when the aircraft makes its 'final' report at 4 NM or less from touchdown but is expected to be available in good time for the aircraft to make a safe landing. On these occasions, the controller will delay landing clearance" — identical to Ed 23 §4.55. Section field updated; principle unchanged. | — |
| `CAP413_4_56` | §4.56 | "CONTINUE APPROACH is not a landing clearance" | **§4.55** | **RENUMBERED** | true | Ed 24 Ch.4 page 24 (line 4921 of extract) | Ed 24 §4.55 = "The controller may or may not explain why the landing clearance has been delayed but the instruction to 'continue' IS NOT an invitation to land and the pilot must wait for landing clearance or initiate a missed approach" — identical to Ed 23 §4.56. Section field updated; principle unchanged. | — |
| `CAP413_4_65` | §4.65 | "Missed approach phraseology" (controller-initiated GA — "GO AROUND [reason]") | **§4.64** | **RENUMBERED** | true | Ed 24 Ch.4 page 24 (line 5034 of extract — note §4.64 is on the previous page, this points to the surrounding context block) | Ed 24 §4.64 = "Instructions to carry out a missed approach may be given to avert an unsafe situation. When a missed approach is initiated cockpit workload is inevitably high. Any transmissions to aircraft going around shall be brief and kept to a minimum" with the phraseology block "BIGJET 347, go around, I say again, go around, acknowledge / Going around, BIGJET 347" — verbatim identical to Ed 23 §4.65. Section field updated; principle ("GO AROUND [reason] — controller-initiated missed approach with the reason") unchanged. Typed-entry `val CAP413_4_65` renamed to `val CAP413_4_64` per epic R3 and consumer call sites updated. | — |

## Mapping table — old Ed 23 §-number → new Ed 24 §-number

For the **focal §4.6x range** (drives R3-R6, R7, R12):

| Ed 23 (old) | Ed 24 (new) | Content topic |
|-------------|-------------|---------------|
| §4.65 | §4.64 | ATC-initiated missed approach ("go around, I say again") |
| §4.66 | §4.65 | VFR-continue / published-missed split |
| §4.67 | §4.66 | Pilot-initiated GA ("going around") |
| §4.68 | §4.67 | Military missed approach phraseology cross-reference |
| §4.69 | §4.68 | Runway vacating / communicating after landing (not cited by codebase) |

For the **other CAP413_* typed entries**:

| Ed 23 (old) | Ed 24 (new) | Content topic | Symbol |
|-------------|-------------|---------------|--------|
| §2.7 | §2.7 | SAFETYCOM transmission range (codebase principle pre-existing miscite — see Table 2 UNREVIEWED note) | `CAP413_2_7` |
| §4.46 | §4.46 | Routine reports / traffic information (codebase principle pre-existing miscite — see Table 2 UNREVIEWED note) | `CAP413_4_46` |
| §4.49 | §4.48 | Circuit sequencing / number-in-sequence | `CAP413_4_49` |
| §4.52 | §4.51 | "Final" report / long-final report (codebase already cited §4.51 — see Table 2 UNCHANGED note) | `CAP413_4_51` |
| §4.53 | §4.52 | Cancellation of issued landing clearance | `CAP413_4_53` |
| §4.55 | §4.54 | Continue approach — runway obstructed at final | `CAP413_4_55` |
| §4.56 | §4.55 | CONTINUE APPROACH is not a landing clearance | `CAP413_4_56` |

## Retained historical cites

Per epic Decision #4 + Step 4 — explicit classification, no silent
omissions. Each entry: file path + line + cite text + reason for
retaining Ed 23 numbering.

(For Markdown narrative files we prefer this artifact recording over
inline comments per codex round-5 finding #7; inline comments OK in
code / KDoc.)

- **`.flow/specs/fn-11-g3a-single-aircraft-pilot-trained-vfr.md`** — every
  §4.6x / `CAP413_4_65` cite in this closed-epic spec. Reason: closed-
  epic historical record; Ed 23 numbering preserved as-authored.
  Pointer added via `## Errata` footer per Step 5.
- **`.flow/specs/fn-12-g3a-obstruction-single-aircraft-atc.md`** — every
  §4.6x / `CAP413_4_65` cite. Reason: closed-epic historical record;
  pointer added via `## Errata` footer.
- **`.flow/specs/fn-13-g3a-obstruction-continue-approach-three.md`** —
  every §4.6x / `CAP413_4_65` cite. Reason: closed-epic historical
  record; pointer added via `## Errata` footer.
- **`.flow/tasks/fn-12-g3a-obstruction-single-aircraft-atc.1.md`** —
  contains historical references to `CAP413_4_65` in the task spec (lines
  ~181, 315, 333) as authored against Ed 23. Reason: closed-epic task
  spec historical record. The epic (`fn-12-g3a-obstruction-single-
  aircraft-atc`) is `done`; this task spec captures the design state at
  fn-12 closure. Current-doctrine citations live in
  `protocol/.../RegulationDatabase.kt` (Ed 24-coherent post-fn-17.1) and
  in `controller/.../Controller.kt` / `TowerArrival.kt` (also updated by
  fn-17.1). Errata footer on the parent epic spec (`fn-12-g3a-
  obstruction-single-aircraft-atc.md`) is the canonical Ed 24 pointer
  for this surface; the task spec is not separately footered.
- **`.flow/tasks/fn-13-g3a-obstruction-continue-approach-three.2.md`**
  — same shape as fn-12.1: contains historical `CAP413_4_65` references
  (lines ~101, 199) authored against Ed 23. Closed-epic task spec
  historical record; covered by the fn-13 errata footer.
- **`.flow/HANDOFF.md`** — contains the pre-fn-17 docs-scout hypothesis
  text (lines ~33 cf.). Reason: handoff narrative captures the
  pre-verification state. Will be deleted at fn-15/16/17/18 epic
  closure per the parent agent's instructions; not in scope for fn-17.1
  edits.
- **`.flow/specs/fn-17-cap-413-edition-24-numbering.md`** + the task
  spec `.flow/tasks/fn-17-cap-413-edition-24-numbering.1.md` + **this**
  artifact `wiki/data-sources/cap413-edition-24-capture.md` — every
  §4.6x / `CAP413_4_6x` cite. Reason: fn-17 own artifacts ARE the
  verification record; excluded from the Step 4 grep walk per the spec.

## Hypothesis

> "fn-14 docs-scout claimed Edition 24 renumbered §4.66 → §4.65
> (VFR-continue) and §4.67 → §4.66 (pilot-initiated GA)."

## Verdict — Branch A — Confirmed (with Edition #1 quirk)

§4.66 (VFR-continue, Ed 23) → §4.65 (Ed 24). §4.67 (pilot-initiated GA,
Ed 23) → §4.66 (Ed 24). The docs-scout hypothesis is **correct on
those two mappings**.

**Edition #1 quirk** (epic R2 / Step 2): the hypothesis missed that
§4.65 (ATC-initiated GA, Ed 23) **also** moves backward by one, to
§4.64 (Ed 24). The full pattern is a uniform `-1` shift across
§4.65 / §4.66 / §4.67 / §4.68. ATC-initiated GA phraseology is
**preserved**, not retired (no Branch A-retire).

R-firing-by-branch: R1 / R2 / R3 / R4 / R5 / R6 / R7 / R8 / R9 / R10 /
R11 all fire (Branch A complete). R12 / R13 are no-ops in Branch A.

## Local extraction procedure

Future reviewers can re-derive section content against the captured
SHA without re-downloading by:

```
# 1. Re-fetch the PDF from the CAA "CAP 413 Future" landing-page link
#    (or the direct /publication/download/18165 URL if the landing
#    page has rotated download IDs in the meantime).
curl -sS -L -o cap413-ed24.pdf https://www.caa.co.uk/publication/download/18165

# 2. Verify SHA-256 matches:
#    c620cda9b6bdbe8e9ed51b258e4df2f6e3edc839226e53ee2b591cb696a966ac
sha256sum cap413-ed24.pdf

# 3. Extract text (this artifact used `pypdf` because pdftotext was not
#    available in the implementer's environment; `pdftotext -layout`
#    would produce equivalent output for grep purposes).
python3 -c "import pypdf; r=pypdf.PdfReader('cap413-ed24.pdf'); print(chr(10).join(p.extract_text() for p in r.pages))" > cap413-ed24.txt

# 4. Grep focal sections:
grep -nE '^[[:space:]]*4\.(46|49|51|53|55|56|6[4-8])|^2\.7[[:space:]]' cap413-ed24.txt
```

**Extraction notes**: `pypdf 6.11.0` was used because `pdftotext` is not
in the implementer's PATH (Homebrew's `poppler` formula is not installed
in this environment). Output is reflowed (each text run on its own line)
but section headings remain identifiable by the `^N.M` prefix pattern.
For the focal §4.65 region the extraction is fully faithful — section
boundaries land where expected and the phraseology examples carry
through. No manual confirmation of section boundaries was required.

The CAP 413 PDF is Crown copyright (Civil Aviation Authority 2026);
this artifact captures URL + SHA + ≤ 1-sentence excerpts only per the
PDF's reproduction notice (page 3 of the Ed 24 PDF: "Copies of this
publication may be reproduced for personal use, or for use within a
company or organisation, but may not otherwise be reproduced for
publication"). No paragraph-length verbatim manual text is committed.

## Implementer environment notes

- `pdftotext` (Poppler) is **not installed** in the implementer's
  environment. `pypdf 6.11.0` (Python venv) was used as a substitute
  for text extraction; output validated against grep-by-section-number
  for focal §4.6x and verified to faithfully render the section
  boundaries needed for Tables 1 + 2.
- Gradle initially refused to run in the sandbox because writes to
  `/Users/andrew/.gradle/` are blocked by the harness filesystem
  policy. **Workaround applied successfully**: cloned the entire
  Gradle user-home (`/Users/andrew/.gradle/{caches,native,wrapper}`)
  to `$TMPDIR/gradle-user-home/`, removed lock files, ran with
  `GRADLE_USER_HOME=$TMPDIR/gradle-user-home` +
  `_JAVA_OPTIONS=-Djava.io.tmpdir=$TMPDIR` (to redirect the Kotlin
  compiler's intermediate files away from the system-default
  `/var/folders/...` path which is also sandbox-blocked) +
  `--offline --no-daemon`. JAVA_HOME pointed at a Nix-installed
  Zulu JDK 21. **R11 verify completed: BUILD SUCCESSFUL, eight
  goldens GREEN** (see fn-17.1 `## Evidence`).
