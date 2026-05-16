# EPPLS Chapter 12 Inventory

Generated: `2026-05-15`

Flow task: `fn-20-fn10-source-unit-ingest-close-out-and.5`

## Source Identity

- PDF: `research/pdf/EPPLS.pdf`
- Text extract: `research/txt/eppls-extracted.txt`
- SHA-256: `dc50992422b288e8934799c31fe64c0ae2f17929c1c7c55b49078ddb0236af7b`
- Identified title: `EASA Private Pilot Studies`
- Author/publisher evidence: Phil Croucher / Electrocution Technical Publishers in extracted front matter and running footers.
- ISBN evidence: `978-1-9752253-1-9` in extracted front matter.
- Extraction command: `nix-shell -p poppler-utils --run 'pdftotext -layout research/pdf/EPPLS.pdf research/txt/eppls-extracted.txt'`
- Extracted line count: `33387`

## Intake Result

EPPLS Chapter 12 exists and spans `research/txt/eppls-extracted.txt` lines
`22390-23379`. Chapter 13 starts at line `23383`, so the Chapter 12 boundary is
stable enough for manifest windows.

The source is a pilot-training/background text. Every candidate window is capped
at `background_support`; EPPLS must not supersede ICAO, SERA, CAP 413, or H01.

## Inventory

| Section id | Lines | Disposition | Notes |
| --- | ---: | --- | --- |
| `ch12_radio_discipline_frequency_basics` | `22390-22530` | manifest | Standard phraseology, concise transmissions, listening watch, frequency expression, and primary-reference pointers. |
| `ch12_q_codes_categories_messages` | `22535-22608` | inventory only | Low value for ingestion because primary sources cover message priority and distress/urgency directly. |
| `ch12_operating_words_callsigns_time` | `22609-22852` | manifest | Transmission technique, ATIS mention on initial contact, standard words/phrases, time expression, and callsign abbreviation cautions. |
| `ch12_aeronautical_stations_position_reports` | `22858-22940` | manifest | Station/service suffixes, position-report item order, and pilot-side sequencing of startup, taxi, joining, circuit reports, and go-around calls. |
| `ch12_ground_tower_circuit_approach` | `22946-23049` | manifest | Ground/tower responsibilities, taxi clearance limits, circuit/final reporting, continue versus landing clearance, and approach/radar context. |
| `ch12_clearances_readbacks_conditional_radio_checks_transfer` | `23050-23179` | manifest | Highest-value window: readbacks, conditional clearances, radio checks, readability scale, and transfer-of-communication examples. |
| `ch12_radio_failure` | `23184-23222` | manifest | Communications-failure checks, transmitting blind, last-clearance compliance, circuit light signals, receiver-failure reporting, and transponder context. |
| `ch12_distress_urgency` | `23227-23354` | manifest | Distress/urgency message shape, radio silence, cancellation, emergency frequencies, fuel emergency wording, and emergency transponder context. |
| `ch12_propagation_frequencies_interception` | `23359-23379` | inventory only | Short cross-reference; not worth ingesting for FN10. |

## Recommendation

Create a dedicated EPPLS manifest and queue only the seven `manifest` windows
above. Run it in a separate durable output root after the current v6 retry pass
finishes, so Ollama load and close-out accounting remain distinct.

## Review Considerations

**FP / type safety:** The intake state is explicit in `intake_record.json`
as `manifest_ready`. No unknown or blocked state is treated as success.

**Test architecture:** Validation for this step is JSON parseability, exact
line ranges, source-path existence, and a later queue dry-run before model
calls. Promotion remains blocked on normal quote/schema/raw-root audits.

**Impact:** Adding EPPLS as a separate background source widens source coverage
without contaminating primary-source precedence. The cost is an extra package
and explicit caveats in the final close-out.

**Operational correctness:** EPPLS is not used as law or binding phraseology.
Any operational claim promoted from it must remain background/training support
unless independently supported by ICAO, SERA, CAP 413, or H01.
