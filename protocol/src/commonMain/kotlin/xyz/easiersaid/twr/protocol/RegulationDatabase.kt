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

    val ICAO4444_7_10_2 = RegulationRef(
        document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§7.10.2",
        title = "Go-around instruction",
        principle = "If controller considers aircraft cannot safely complete approach, instructions to go around shall be given",
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

    val CAP413_2_7 = RegulationRef(
        document = "CAP_413", edition = "27th ed. (2023)", section = "§2.7",
        title = "Frequency change and two-way communication",
        principle =
            "When instructed to change frequency the pilot shall establish two-way communication " +
                "on the new frequency; an initial call identifies the aircraft to the receiving unit",
        category = RegulationCategory.GUIDANCE,
    )

    val CAP413_4_46 = RegulationRef(
        document = "CAP_413", edition = "27th ed. (2023)", section = "§4.46",
        title = "Readback of ground movement instructions",
        principle =
            "Hold short / hold position instructions relating to runways must be read back in full " +
                "including the runway designator or holding point; silent acknowledgement is not acceptable",
        category = RegulationCategory.PHRASEOLOGY,
    )

    val CAP413_4_49 = RegulationRef(
        document = "CAP_413", edition = "27th ed. (2023)", section = "§4.49",
        title = "Circuit sequencing and spacing",
        principle = "Controller issues sequence number, traffic information, and delaying action to coordinate circuit traffic",
        category = RegulationCategory.GUIDANCE,
    )

    val CAP413_4_55 = RegulationRef(
        document = "CAP_413", edition = "27th ed. (2023)", section = "§4.55",
        title = "Continue approach — delayed landing clearance",
        principle = "When runway is occupied but expected to clear, controller delays landing clearance with continue approach",
        category = RegulationCategory.GUIDANCE,
    )
}
