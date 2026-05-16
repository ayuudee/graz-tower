# Nolan Background Inventory

Generated: `2026-05-15`

Flow task: `fn-20-fn10-source-unit-ingest-close-out-and.6`

## Source Identity

- PDF: `research/pdf/fundies.pdf`
- Text extract: `research/txt/nolan-fundamentals-extracted.txt`
- SHA-256: `438d772a3f872f969efeb3a03149b027d7ffa089d4e11e73af991781c72c390c`
- Title: `Fundamentals of Air Traffic Control`
- Author: Michael S. Nolan
- Edition: 5th Edition
- Publisher: Delmar, Cengage Learning
- Copyright year: 2011
- ISBN-13: `978-1-4354-8272-2`
- PDF pages: `674`
- Extracted lines: `42853`

## Decision

Nolan has relevant ATC material, but I do not recommend ingesting it in the
current close-out. It is a U.S./FAA textbook, not a primary authority source and
not local to the ICAO/SERA/CAP 413/H01 frame. Its useful content is explanatory
background and overlaps heavily with stronger sources already in the run plus
EPPLS Chapter 12.

If used later, Nolan should be a dedicated `background_support` package with
narrow windows. It should not be promoted into the current primary-source
package or used to settle operational doctrine.

## Candidate Windows

| Section id | Lines | Disposition | Notes |
| --- | ---: | --- | --- |
| `nolan_history_light_gun_signals` | `987-1050` | inventory only | Light-gun history and signal table; primary light-signal sources are preferable. |
| `nolan_ch4_atc_communications_clearance_basics` | `10681-10904` | possible later background | Facility suffixes, route/radial pronunciation, communication procedures, and clearance examples. |
| `nolan_ch4_identification_departure_altitude_reports_holding` | `10905-11743` | possible later background | Aircraft identification, departure instructions, route/altitude assignments, reports, and holding. Too broad for immediate ingest. |
| `nolan_ch4_additional_communications_phraseology` | `11745-11968` | possible later background | Acknowledge, affirmative/negative, say intentions, expect, and standard abbreviation background. |
| `nolan_ch5_transfer_control_communication_handoff` | `12441-12490` | possible later background | Conceptual distinction between transfer of communication, transfer of control, and handoff. |
| `nolan_ch6_tower_roles_atis_clearance_delivery` | `13060-13653` | possible later background | Tower roles, flight data, ATIS content, clearance delivery, and weather/PIREP dissemination. |
| `nolan_ch6_ground_taxi_hold_short_crossing` | `13724-13816` | possible later background | Taxi instructions, hold short, runway crossing coordination, and urgency wording. |
| `nolan_ch6_local_control_takeoff_landing_option` | `13870-14510` | possible later background | Runway separation, takeoff/landing phraseology, anticipated separation, touch-and-go, stop-and-go, low approach, and option clearances. |
| `nolan_ch6_wake_turbulence` | `14568-14786` | inventory only | Wake-turbulence explanation; not a communications/source-unit priority here. |
| `nolan_ch7_visual_separation` | `17386-17493` | inventory only | Visual separation explanation and example dialogue; outside immediate close-out scope. |

## Searched Signals

The inventory searched table-of-contents entries and text hits for:
`ATC Communications Procedures`, `Clearance`, `Phraseology`, `Control Tower
Procedures`, `Clearance Delivery Controller Duties`, `Transfer of
Communication`, `handoff`, `ATIS`, `readback`, `taxi`, `hold short`, `takeoff`,
`landing`, and `visual separation`.

## Review Considerations

**FP / type safety:** The inventory state is explicit:
`inventory_ready_no_ingest_recommended`. No manifest is created from Nolan in
this task, so downstream queue generation cannot silently include it.

**Test architecture:** JSON parseability and exact line ranges are enough for
the no-ingest decision. If Nolan is ingested later, require a separate manifest,
queue dry-run, raw-root consistency audit, and authority-ceiling audit.

**Impact:** Keeping Nolan out of the immediate package avoids mixing U.S.
textbook content into a primary-source frame. It remains available as a
background source if later design work needs explanatory examples.

**Operational correctness:** Nolan must not be used as binding ATC law or
phraseology. Its U.S./FAA examples can conflict with European/ICAO-local
phraseology and should be cross-checked against primary sources before any
operational claim is made.
