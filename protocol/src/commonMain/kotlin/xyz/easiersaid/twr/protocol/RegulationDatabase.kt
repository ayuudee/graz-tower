package xyz.easiersaid.twr.protocol

/**
 * Regulation references used by controller rules.
 *
 * Procedure documents (4444) as primary authority, phraseology (9432) as secondary,
 * guidance (9870, CAP 413) as supplementary. Expand as procedures are added.
 *
 * Editions pinned per Phase 5 design doc §1.3. Citation triples: (document, edition, section).
 */
object RegulationDatabase {

    // ── SERA ─────────────────────────────────────────────────────────

    val SERA_5001 = RegulationRef(
        document = "SERA", edition = RegulationRef.SERA_EDITION, section = "SERA.5001",
        title = "Visual flight rules",
        principle = "VFR flights shall be conducted in conditions of visibility and distance from cloud not less than those specified",
        category = RegulationCategory.LAW,
    )

    val SERA_5005 = RegulationRef(
        document = "SERA", edition = RegulationRef.SERA_EDITION, section = "SERA.5005",
        title = "VFR flight limitations",
        principle = "VFR flights shall not be operated when conditions deteriorate below VMC minima; hold or divert",
        category = RegulationCategory.LAW,
    )

    val SERA_3225 = RegulationRef(
        document = "SERA", edition = RegulationRef.SERA_EDITION, section = "SERA.3225",
        title = "Circuit joining",
        principle = "Aircraft joining the circuit shall conform to the traffic pattern or receive joining instructions",
        category = RegulationCategory.LAW,
    )

    val SERA_8005_C = RegulationRef(
        document = "SERA", edition = RegulationRef.SERA_EDITION, section = "SERA.8005(c)",
        title = "ATC separation obligation",
        principle = "ATC clearances shall provide separation between controlled flights",
        category = RegulationCategory.LAW,
    )

    // ── ICAO Annex 2 ─────────────────────────────────────────────────

    val ANNEX2_3_6 = RegulationRef(
        document = "ICAO_ANNEX_2", edition = "10th ed. (2005)", section = "§3.6",
        title = "ATC clearances",
        principle = "ATC clearances shall be obtained prior to operating a controlled flight; compliance is mandatory",
        category = RegulationCategory.PROCEDURE,
    )

    // ── ICAO Annex 11 ────────────────────────────────────────────────

    val ANNEX11_2_2 = RegulationRef(
        document = "ICAO_ANNEX_11", edition = "14th ed. (2018)", section = "§2.2",
        title = "Objectives of ATS",
        principle = "ATS shall provide for the safe, orderly, and expeditious flow of air traffic",
        category = RegulationCategory.PROCEDURE,
    )

    val ANNEX11_2_3 = RegulationRef(
        document = "ICAO_ANNEX_11", edition = "14th ed. (2018)", section = "§2.3",
        title = "ATC service responsibilities",
        principle = "ATC service provides instructions and clearances to prevent collisions and expedite traffic",
        category = RegulationCategory.PROCEDURE,
    )

    /**
     * Pass 15 (D-AUDIT.8 closure): ATIS service. Pilots receive ATIS
     * before first contact and acknowledge the letter on first
     * transmission; controllers issue advisory `CurrentInformationIs`
     * on letter mismatch (no readback obligation per §4.3.6).
     */
    val ICAO_ANNEX_11_4_3 = RegulationRef(
        document = "ICAO_ANNEX_11", edition = "14th ed. (2018)", section = "§4.3",
        title = "ATIS broadcast service",
        principle = "Aerodrome control service provides ATIS broadcast; pilots acknowledge the current letter on first contact",
        category = RegulationCategory.PROCEDURE,
    )

    // ── ICAO Doc 4444 ────────────────────────────────────────────────

    val ICAO4444_4_5 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§4.5",
        title = "ATC clearance content",
        principle = "ATC clearances shall contain the clearance limit, route, and any other necessary instructions",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_5 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§5",
        title = "Separation methods and minima",
        principle = "Controller shall ensure prescribed separation between controlled flights using approved methods",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_5_4_1 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§5.4.1",
        title = "Radar separation minima",
        principle = "Radar separation minima shall not be less than the prescribed values",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_5_4_2 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§5.4.2",
        title = "Wake turbulence separation",
        principle = "Wake turbulence separation minima shall be applied between aircraft based on category",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_6_3 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§6.3",
        title = "Transfer of control",
        principle = "Transfer of control shall be coordinated between transferring and accepting units",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_6_5 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§6.5",
        title = "Approach clearance",
        principle = "Approach clearance issued when separation and sequencing permit",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_7_6 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§7.6",
        title = "Movement on the manoeuvring area",
        principle = "Aircraft and vehicles shall not operate on the manoeuvring area without ATC authorisation",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_7_9 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§7.9",
        title = "Take-off clearance",
        principle = "Take-off clearance issued when runway is available and separation exists; requires two-way communication",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_7_9_3 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§7.9.3",
        title = "Conditional clearances",
        principle = "Conditional clearances reference the traffic or condition, then the instruction; condition stated first",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_7_10 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§7.10",
        title = "Arriving aircraft",
        principle = "Controller is responsible for sequencing arriving traffic; landing clearance when runway available",
        category = RegulationCategory.PROCEDURE,
    )

    /**
     * ICAO Doc 4444 §7.10.2 — "Clearance to land". Source text:
     * "An aircraft may be cleared to land when there is reasonable assurance
     * that the separation in 7.10.1, or prescribed in accordance with 7.11,
     * will exist when the aircraft crosses the runway threshold, provided
     * that a clearance to land shall not be issued until a preceding landing
     * aircraft has crossed the runway threshold."
     *
     * Consumers (`GA-PRE-CLEAR`, `GA-POST-CLEAR`, `ARR-GO-AROUND`,
     * `ARR-GO-AROUND-CLEARANCE-ISSUED`, reactive-separation GA emission) cite
     * §7.10.2 because the go-around is the operational *consequence* of the
     * reasonable-assurance test failing: if assurance cannot be re-established
     * before threshold crossing, clearance is withheld / withdrawn and a
     * go-around is issued.
     */
    val ICAO4444_7_10_2 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§7.10.2",
        title = "Clearance to land — reasonable assurance",
        principle = "Landing clearance may be issued only when there is reasonable assurance the required " +
            "separation will exist at threshold crossing; absent that assurance the clearance is withheld " +
            "or withdrawn and a go-around follows",
        category = RegulationCategory.PROCEDURE,
    )

    /**
     * fn-12 (R7): runway obstruction / incursion go-around mandate.
     *
     * "If a runway-incursion / obstruction situation is observed, the
     * aircraft on final shall be instructed to go around. In all cases the
     * pilot shall be informed of the runway incursion or obstruction."
     *
     * Note (4444 commentary): "Animals and flocks of birds may constitute
     * an obstruction with regard to runway operations." The reason on
     * radio is MUST, not optional — see also §8.9.6.1.8.
     */
    val ICAO4444_7_4_1_4_1 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§7.4.1.4.1",
        title = "Runway obstruction — go-around mandate",
        principle = "If a runway is observed obstructed, the aircraft on final shall be instructed to go around; " +
            "pilot shall be informed of the obstruction",
        category = RegulationCategory.PROCEDURE,
    )

    /**
     * fn-12 (R7): reason on radio is reason-on-radio convention.
     *
     * Source text: "In all such cases, the reason for the instruction or the
     * advice **should** be given to the pilot." (Note: "should", not "shall"
     * — recommendatory, not mandatory. fn-12.1's original transcription said
     * "shall"; corrected in fn-14 cleanup pass per docs-scout finding in
     * fn-13 planning.)
     */
    val ICAO4444_8_9_6_1_8 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§8.9.6.1.8",
        title = "Reason for instruction given to pilot",
        principle = "In all such cases, the reason for the instruction or the advice should be given to the pilot",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_7_11 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§7.11",
        title = "Post-landing taxi",
        principle = "Controller directs aircraft to vacate the runway via a specific route",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_10_1 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§10.1",
        title = "Transfer of communications",
        principle =
            "Transfer of communication between ATC units is effected by instructing the aircraft " +
                "to change frequency; transferring unit remains responsible until the aircraft has " +
                "established two-way communication with the accepting unit",
        category = RegulationCategory.PROCEDURE,
    )

    val ICAO4444_12_3_2 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§12.3.2",
        title = "Readback corrections",
        principle = "If an incorrect readback is noted, the controller shall transmit NEGATIVE followed by the correct version",
        category = RegulationCategory.PHRASEOLOGY,
    )

    // ── ICAO Doc 9432 (phraseology) ──────────────────────────────────

    val ICAO9432_INITIAL_CONTACT = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Initial contact",
        principle = "Initial contact includes callsign, type, position, altitude, and intentions",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_STARTUP = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Start-up approval",
        principle = "Engine start-up requires approval at controlled aerodromes when procedures require it",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_TAXI = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Taxi clearance",
        principle = "Taxi instructions include the taxi route and holding point; pilot reads back runway and route",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_READY = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Ready for departure report",
        principle = "Pilot reports ready for departure at the holding point",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_LINEUP = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Line up and wait",
        principle = "Instruction to enter and line up on the runway without takeoff clearance",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_TAKEOFF = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Take-off clearance phraseology",
        principle = "Take-off clearance includes runway designator and surface wind",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_LANDING = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Landing clearance phraseology",
        principle = "Landing clearance includes runway designator; wind given when significant",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_CONTINUE_APPROACH = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Continue approach",
        principle = "Instruction to continue the approach when landing clearance cannot yet be issued",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_HOLD_POSITION = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Hold position",
        principle = "Instruction to stop and hold at current position",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_GO_AROUND = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Go-around instruction",
        principle = "Instruction to abandon approach and climb away; mandatory readback",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_CONDITIONAL = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Conditional clearance phraseology",
        principle = "Conditional clearances state the condition first, then the instruction",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_CIRCUIT_REPORTS = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Circuit position reports",
        principle = "Pilots report position in the circuit as required by local procedures or ATC",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_CIRCUIT_JOIN = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Circuit joining instructions",
        principle = "ATC issues instructions for joining the aerodrome traffic circuit",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_EXTEND_DOWNWIND = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Extend downwind for spacing",
        principle = "Controller instructs trailing aircraft to extend downwind for in-trail spacing",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_FREQUENCY_CHANGE = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.3",
        title = "Frequency change",
        principle = "Frequency change approved or instructed; pilot reads back the new frequency",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val ICAO9432_READBACK = RegulationRef(
        document = "ICAO_9432", edition = RegulationRef.ICAO_9432_EDITION, section = "Ch.4",
        title = "Readback requirements",
        principle = "Pilot shall read back safety-significant elements of ATC clearances",
        category = RegulationCategory.PHRASEOLOGY,
    )

    // ── ICAO Doc 9870 (guidance) ─────────────────────────────────────

    val ICAO9870_RUNWAY_INCURSION = RegulationRef(
        document = "ICAO_9870", edition = "2nd ed. (2007)", section = "Ch.3",
        title = "Runway incursion prevention",
        principle = "Procedures and vigilance to prevent unauthorised presence on the runway",
        category = RegulationCategory.GUIDANCE,
    )

    // ── CAP 413 (guidance / national) ────────────────────────────────

    /**
     * fn-17.1 (R9): UNREVIEWED per Table 2 audit in
     * `wiki/data-sources/cap413-edition-24-capture.md`. Ed 24 §2.7 retains
     * the same SAFETYCOM content as Ed 23 §2.7, but the codebase principle
     * here ("Frequency change and two-way communication") matches neither
     * Ed 23 §2.7 nor Ed 24 §2.7 — pre-existing principle-vs-cite drift
     * unrelated to the renumbering scope of fn-17. Edition string pinned
     * to Ed 23 Corr inline literal per the universal hard gate (Ed 24
     * metadata would produce an unverified citation triple); separate
     * audit deferred to `D-PASS-cap413-2_7-principle-cite-audit`.
     */
    val CAP413_2_7 = RegulationRef(
        document = "CAP_413", edition = "Edition 23 Corr (effective 21 January 2021)", section = "§2.7",
        title = "Frequency change and two-way communication",
        principle =
            "When instructed to change frequency the pilot shall establish two-way communication " +
                "on the new frequency; an initial call identifies the aircraft to the receiving unit",
        category = RegulationCategory.GUIDANCE,
    )

    /**
     * fn-17.1 (R9): UNREVIEWED per Table 2 audit in
     * `wiki/data-sources/cap413-edition-24-capture.md`. Codebase principle
     * ("readback of ground movement / hold short") matches neither Ed 23
     * §4.46 (= "traffic information prior to joining circuit") nor Ed 24
     * §4.46 (= "routine reports as required by local procedures"). Pre-
     * existing principle-vs-cite drift unrelated to fn-17's renumbering
     * scope. Edition string pinned to Ed 23 Corr inline literal per the
     * universal hard gate; separate audit deferred to
     * `D-PASS-cap413-4_46-principle-cite-audit`.
     */
    val CAP413_4_46 = RegulationRef(
        document = "CAP_413", edition = "Edition 23 Corr (effective 21 January 2021)", section = "§4.46",
        title = "Readback of ground movement instructions",
        principle =
            "Hold short / hold position instructions relating to runways must be read back in full " +
                "including the runway designator or holding point; silent acknowledgement is not acceptable",
        category = RegulationCategory.PHRASEOLOGY,
    )

    /**
     * fn-17.1 (R9): RENUMBERED per Table 2 audit in
     * `wiki/data-sources/cap413-edition-24-capture.md`. Ed 23 §4.49
     * ("co-ordinate traffic in the circuit, to issue a pilot their number
     * in the sequence") → Ed 24 §4.48 (verbatim-identical content; spelling
     * shift "co-ordinate" → "coordinate"). Section field updated; principle
     * unchanged.
     */
    val CAP413_4_49 = RegulationRef(
        document = "CAP_413", edition = RegulationRef.CAP_413_EDITION, section = "§4.48",
        title = "Circuit sequencing and spacing",
        principle = "Controller issues sequence number, traffic information, and delaying action to coordinate circuit traffic",
        category = RegulationCategory.GUIDANCE,
    )

    /**
     * fn-17.1 (R9): UNCHANGED (in Ed 24) per Table 2 audit in
     * `wiki/data-sources/cap413-edition-24-capture.md`. Codebase principle
     * ("REPORT FINAL RUNWAY [designator]... 'final' / 'long final' report")
     * matches Ed 24 §4.51 exactly. (Note: in Ed 23 this content lived at
     * §4.52, not §4.51; the codebase appears to have pre-emptively cited
     * the upcoming Ed 24 §-number. Ed 24's `-1` renumbering across the
     * §4.5x range makes the existing `§4.51` cite correct against Ed 24
     * content.) No section change needed; edition string updated.
     */
    val CAP413_4_51 = RegulationRef(
        document = "CAP_413", edition = RegulationRef.CAP_413_EDITION, section = "§4.51",
        title = "Report final",
        principle = "REPORT FINAL RUNWAY [designator] — ATC requests pilot report when turning final; " +
            "used to time landing clearance and sequence departing traffic",
        category = RegulationCategory.PHRASEOLOGY,
    )

    /**
     * fn-13.1 (R7): CONTINUE APPROACH — runway obstructed at final.
     *
     * Upgraded in place from a placeholder. Tighter principle per docs-scout
     * guidance: this regulation describes the pre-clearance CONTINUE APPROACH
     * surface — runway obstructed at/after the 4 NM final report but
     * expected to be available in good time for a safe landing. Distinct
     * from CAP 413 §4.52 (cancellation of issued landing clearance, which
     * is post-clearance).
     *
     * fn-17.1 (R9): RENUMBERED per Table 2 audit in
     * `wiki/data-sources/cap413-edition-24-capture.md`. Ed 23 §4.55 →
     * Ed 24 §4.54 (verbatim-identical content). Section field updated;
     * principle unchanged. Symbol name `CAP413_4_55` intentionally
     * retained for Kotlin source stability (renaming would explode the
     * downstream call-site change list without aiding citation
     * correctness — the `section` field carries the Ed 24 number).
     */
    val CAP413_4_55 = RegulationRef(
        document = "CAP_413", edition = RegulationRef.CAP_413_EDITION, section = "§4.54",
        title = "Continue approach — runway obstructed at final",
        principle = "When the runway is obstructed at or after the 4 NM final report but is expected to be " +
            "available in good time for a safe landing, the controller delays landing clearance and " +
            "instructs CONTINUE APPROACH; pilot reads back",
        category = RegulationCategory.GUIDANCE,
    )

    /**
     * fn-13.1 (R7): cancellation of issued landing clearance.
     *
     * Post-clearance counterpart to CAP413_4_55: when a controller cancels
     * an already-issued landing clearance but expects re-issue in good time
     * for a safe landing, the phraseology is CONTINUE APPROACH, CANCEL
     * LANDING CLEARANCE (reason), ACKNOWLEDGE with pilot readback.
     *
     * Entry added for future-proofing — fn-13.1 itself fires the
     * pre-clearance variant (AwaitApproach stage only). The post-clearance
     * cancellation path is a future deferment.
     *
     * fn-17.1 (R9): RENUMBERED per Table 2 audit in
     * `wiki/data-sources/cap413-edition-24-capture.md`. Ed 23 §4.53 →
     * Ed 24 §4.52 (verbatim-identical content). Section field updated;
     * principle unchanged. Symbol name `CAP413_4_53` intentionally
     * retained for Kotlin source stability.
     */
    val CAP413_4_53 = RegulationRef(
        document = "CAP_413", edition = RegulationRef.CAP_413_EDITION, section = "§4.52",
        title = "Cancellation of issued landing clearance",
        principle = "Where a controller cancels an issued landing clearance but expects re-issue in good time " +
            "for a safe landing, the reason should be given if time permits; phraseology is " +
            "CONTINUE APPROACH, CANCEL LANDING CLEARANCE (reason), ACKNOWLEDGE with pilot readback",
        category = RegulationCategory.PHRASEOLOGY,
    )

    /**
     * fn-13.1 (R7): CONTINUE APPROACH is not a landing clearance.
     *
     * The instruction tells the pilot to continue the approach pending a
     * landing decision. The pilot must NOT treat it as an invitation to
     * land — they wait for the landing clearance proper or initiate a
     * missed approach if the clearance does not materialise.
     *
     * fn-17.1 (R9): RENUMBERED per Table 2 audit in
     * `wiki/data-sources/cap413-edition-24-capture.md`. Ed 23 §4.56 →
     * Ed 24 §4.55 (verbatim-identical content). Section field updated;
     * principle unchanged. Symbol name `CAP413_4_56` intentionally
     * retained for Kotlin source stability.
     */
    val CAP413_4_56 = RegulationRef(
        document = "CAP_413", edition = RegulationRef.CAP_413_EDITION, section = "§4.55",
        title = "CONTINUE APPROACH is not a landing clearance",
        principle = "The instruction CONTINUE APPROACH is not an invitation to land; the pilot must wait " +
            "for landing clearance or initiate a missed approach",
        category = RegulationCategory.PHRASEOLOGY,
    )

    /**
     * fn-13.1 (R7): ICAO Doc 4444 §12.3.4.16 — landing-clearance phraseology
     * including CONTINUE APPROACH variants. Captures the formal phraseology
     * `CONTINUE APPROACH [PREPARE FOR POSSIBLE GO AROUND]` used when
     * landing clearance is delayed. Explicitly stipulates that this is NOT
     * a landing clearance (mirrors CAP 413 §4.55 in Ed 24 — formerly
     * §4.56 in Ed 23; renumbered per fn-17.1).
     */
    val ICAO4444_12_3_4_16 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§12.3.4.16",
        title = "Landing clearance phraseology — approach instructions",
        principle = "CONTINUE APPROACH [PREPARE FOR POSSIBLE GO AROUND] is the approach-instruction phraseology " +
            "used when landing clearance is delayed; it is not a landing clearance",
        category = RegulationCategory.PHRASEOLOGY,
    )

    /**
     * fn-12 (R7): missed approach phraseology.
     *
     * "GO AROUND, [reason], …" — controller-initiated missed approach.
     * The phraseology mandates the reason; the companion
     * `RunwayObstructionInformation` carries the structured
     * obstruction-info that renders the reason ("runway obstructed,
     * runway 09").
     *
     * fn-17.1 (R3 / R9): RENUMBERED per Table 2 audit in
     * `wiki/data-sources/cap413-edition-24-capture.md`. Ed 23 §4.65 →
     * **Ed 24 §4.64** (verbatim-identical content; ATC-initiated GA
     * phraseology block "go around, I say again, go around, acknowledge"
     * preserved). Symbol renamed from `CAP413_4_65` to `CAP413_4_64`
     * (compiler-driven rename across consumers per R4 — see
     * `controller/.../TowerArrival.kt` import + call sites and
     * `controller/.../Controller.kt` call site). The `section` field
     * also reflects the new §4.64 number for citation-triple coherence.
     */
    val CAP413_4_64 = RegulationRef(
        document = "CAP_413", edition = RegulationRef.CAP_413_EDITION, section = "§4.64",
        title = "Missed approach phraseology",
        principle = "GO AROUND [reason] — controller-initiated missed approach with the reason",
        category = RegulationCategory.PHRASEOLOGY,
    )

    // ── fn-14.1 (G3a-react R14): POH crosswind reactive-GA anchors ──

    /**
     * fn-14.1 (R14): FAA Airplane Flying Handbook (FAA-H-8083-3C)
     * Chapter 9 — "Common Errors" #1 for crosswind approaches:
     * attempting a landing in crosswinds exceeding the airplane's
     * maximum demonstrated crosswind component.
     *
     * The principle is the modelling anchor for the pilot's reactive
     * go-around in `applyCrosswindGoAround`: when the world's wind
     * report against the active runway produces a crosswind component
     * greater than [xyz.easiersaid.twr.protocol.AircraftType.maxCrosswindKnots],
     * a competent VFR pilot initiates a go-around (per AFH guidance,
     * Common Error #1 is the failure mode of NOT doing so).
     *
     * Note: the AFH framing pairs with [FAA_FAR_23_233_CROSSWIND_CERT]
     * (the certification floor) and [ICAO_ANNEX_6_PII_2_4_PIC] (PIC
     * authority to make the decision).
     */
    val FAA_AFH_CH9_CROSSWIND_ERRORS = RegulationRef(
        document = "FAA_AFH", edition = "FAA-H-8083-3C (2021)", section = "Ch 9 Common Errors #1",
        title = "Common error: landing in crosswinds exceeding max demonstrated",
        principle = "Attempting a landing in crosswinds that exceed the airplane's maximum demonstrated " +
            "crosswind component is a common error — a competent pilot goes around instead",
        category = RegulationCategory.GUIDANCE,
    )

    /**
     * fn-14.1 (R14): 14 CFR §23.233(a) (pre-Amendment 64) — crosswind
     * certification floor. Small airplanes must demonstrate
     * controllable handling in a 90° crosswind of at least
     * `0.2 V_SO` (the type's stall speed with full flaps at MTOW).
     * Pairs with FAA AC 23-8B: the demonstrated value is **performance
     * information, not a limitation**.
     *
     * Models the typed datum
     * [xyz.easiersaid.twr.protocol.AircraftType.maxCrosswindKnots]
     * itself — the POH-cited demonstrated crosswind for each type
     * carries forward this certification anchor.
     */
    val FAA_FAR_23_233_CROSSWIND_CERT = RegulationRef(
        document = "FAA_FAR_23", edition = "14 CFR §23.233(a) (pre-Amd 64)", section = "§23.233(a)",
        title = "Crosswind certification floor — 0.2 V_SO",
        principle = "Small airplanes must demonstrate controllable handling in a 90° crosswind of at least " +
            "0.2 V_SO; the demonstrated value is performance information, not a formal limitation",
        category = RegulationCategory.LAW,
    )

    /**
     * fn-14.1 (R14): ICAO Annex 6 Part II §2.4 (General Aviation
     * Operations) — Pilot-in-Command final authority. The PIC has
     * final authority over the operation of the aircraft, including
     * the decision to initiate a go-around when conditions on the
     * approach are not acceptable (e.g. crosswind exceeds the
     * aircraft type's demonstrated POH value).
     *
     * Anchors the pilot's autonomous transmission of `Report(GoingAround)`
     * — no ATC permission is required (also CAP 413 §4.66 (Ed 24 — formerly
     * §4.67 in Ed 23, renumbered per fn-17.1) / ICAO Doc 4444 §12.3.4.18 for
     * the phraseology side).
     */
    val ICAO_ANNEX_6_PII_2_4_PIC = RegulationRef(
        document = "ICAO_ANNEX_6_PII", edition = "10th ed. (2018)", section = "§2.4",
        title = "PIC final authority — general aviation operations",
        principle = "The pilot-in-command has final authority over the operation of the aircraft, including " +
            "the decision to initiate a go-around when approach conditions are not acceptable",
        category = RegulationCategory.LAW,
    )

    /**
     * fn-14.1 (R14): FAA AIM §7-1-12.d.3 — wind reference frame
     * convention. ATC-broadcast surface winds are converted from True
     * to **Magnetic** at the controller; printed sources (METAR, TAF)
     * use **True**. Runway designators are themselves Magnetic by
     * convention (ICAO Annex 14 §5.2).
     *
     * Anchors the type contract on
     * [xyz.easiersaid.twr.protocol.Wind.directionDegrees] (twr2 stores
     * Magnetic FROM-degrees, matching the ATIS/ATC voice sensing path
     * the pilot reads). Same reference frame as
     * [xyz.easiersaid.twr.protocol.headingDegreesMagnetic] — single-
     * frame crosswind computation in
     * `pilot.observe.crosswindComponentKnots` does not need a
     * True/Magnetic conversion in v1.
     */
    val FAA_AIM_7_1_12_WIND_MAGNETIC = RegulationRef(
        document = "FAA_AIM", edition = "AIM (current)", section = "§7-1-12.d.3",
        title = "ATC-voice surface wind in Magnetic degrees",
        principle = "Surface wind broadcast by ATC is reported in Magnetic degrees, FROM-direction; " +
            "printed sources (METAR/TAF) use True degrees",
        category = RegulationCategory.GUIDANCE,
    )
}
