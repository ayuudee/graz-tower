# fn-52-implement-icao-9432-chunk-02.3 Model ground-station radio-test signal identity and duration evidence

## Description

Introduce enough typed model/evidence surface to identify ground-station radio
test signals and verify the 10-second duration obligation without rendered
phraseology string matching.

## Acceptance

- [x] Ground-station test signal identity is typed.
- [x] Duration is measured from real transmission start/end data.
- [x] Spoken-number and callsign content remains blocked by `PHRASE-1`.
- [x] Source-mapped test cites `icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d` for the duration sub-obligation only.

## Done summary
Added typed ground-station test-signal identity and measured duration evidence
for the ICAO 9432 10-second duration sub-obligation while leaving spoken-number
and station-callsign content blocked by `PHRASE-1`.

## Evidence
- Commit: `212af937 fn-52.3 model ground-station test signals`
- Tests: `./gradlew-nix :sim:jvmTest`
- Tests: `./gradlew-nix detekt`
- Tests: `git diff --check`
- Tests: `.flow/bin/flowctl validate --epic fn-52-implement-icao-9432-chunk-02`
