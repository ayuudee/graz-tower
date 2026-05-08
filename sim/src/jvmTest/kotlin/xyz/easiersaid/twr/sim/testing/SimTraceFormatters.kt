package xyz.easiersaid.twr.sim.testing

import xyz.easiersaid.twr.protocol.AircraftId

/**
 * Multi-line summary of all transitions for [aircraft] across the trace.
 *
 * Includes responsibility-state transitions (per controller),
 * commitment-stage transitions (per controller), mission-step
 * transitions, and positionPoint transitions in time order. The output
 * is the closure-grade replacement for [formatJourney] — a strict
 * superset (final state + transmissions + the timeline of transitions
 * the journey log can't show).
 */
fun SimTrace.formatAircraftTimeline(aircraft: AircraftId): String = buildString {
    appendLine("─── Timeline for $aircraft ───")
    appendLine("  span = ${span.millis}ms (${span.millis / 1000}s)")
    appendLine("  steps = $size")
    appendLine()

    appendLine("Responsibility transitions:")
    val respTransitions = responsibilityTransitions(aircraft)
    if (respTransitions.isEmpty()) {
        appendLine("  (none)")
    } else {
        for (t in respTransitions) {
            val fromStr = t.from.fold({ "absent" }, { it::class.simpleName ?: "?" })
            val toStr = t.to.fold({ "absent" }, { it::class.simpleName ?: "?" })
            appendLine("  [${t.after.time.millis}ms] ${t.controller}: $fromStr → $toStr")
        }
    }
    appendLine()

    appendLine("Mission step transitions:")
    val stepTransitions = missionStepTransitions(aircraft)
    if (stepTransitions.isEmpty()) {
        appendLine("  (none)")
    } else {
        for (t in stepTransitions) {
            val fromStr = t.from.fold({ "absent" }, { it.name })
            val toStr = t.to.fold({ "absent" }, { it.name })
            appendLine("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
    }
    appendLine()

    appendLine("PositionPoint transitions:")
    val posTransitions = positionPointTransitions(aircraft)
    if (posTransitions.isEmpty()) {
        appendLine("  (none)")
    } else {
        for (t in posTransitions) {
            val fromStr = t.from.fold({ "absent" }, { it.value })
            val toStr = t.to.fold({ "absent" }, { it.value })
            appendLine("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
    }
    appendLine()

    appendLine("Final aircraft state:")
    val finalAc = finalState.aircraft[aircraft]
    if (finalAc == null) {
        appendLine("  (aircraft $aircraft not in final state)")
    } else {
        appendLine("  phase            = ${finalAc.phase}")
        appendLine("  positionPoint    = ${finalAc.positionPoint}")
        appendLine("  altitude (m)     = ${finalAc.altitudeM}")
        appendLine("  speed (m/s)      = ${finalAc.speedMps}")
        finalAc.pilotMission?.let { mission ->
            appendLine("  mission complete = ${mission.isComplete}")
            appendLine("  active task      = ${mission.currentTask?.step}")
            appendLine("  contactedOnFreq  = ${mission.contactedOnFrequency}")
        }
    }
}

/** Multi-line summary of all rule firings across the whole trace. */
fun SimTrace.formatRuleFirings(): String = buildString {
    appendLine("─── Rule firings (full trace) ───")
    val firings = ruleFirings()
    if (firings.isEmpty()) {
        appendLine("  (none)")
    } else {
        for (f in firings) {
            appendLine(formatRuleFiring(f))
        }
    }
}

/** Multi-line summary of rule firings between two cursors (inclusive). */
fun SimTrace.formatRuleFirings(start: TraceCursor, end: TraceCursor): String = buildString {
    require(start.trace === this@formatRuleFirings && end.trace === this@formatRuleFirings) {
        "formatRuleFirings: cursors must belong to this SimTrace"
    }
    require(start.index <= end.index) {
        "formatRuleFirings: start.index (${start.index}) > end.index (${end.index})"
    }
    appendLine("─── Rule firings ${start.time.millis}ms..${end.time.millis}ms ───")
    val firings = ruleFirings().filter {
        it.cursor.index in start.index..end.index
    }
    if (firings.isEmpty()) {
        appendLine("  (none)")
    } else {
        for (f in firings) {
            appendLine(formatRuleFiring(f))
        }
    }
}

private fun formatRuleFiring(f: RuleFiring): String {
    val sourceTag = when (f.source) {
        RuleFiringSource.Transmission -> "tx"
        RuleFiringSource.StageOnlyInferred -> "inferred"
    }
    val instr = f.instruction.fold({ "<no instruction>" }, { it::class.simpleName ?: "?" })
    return "  [${f.time.millis}ms] $sourceTag ${f.controller} → ${f.aircraft}: ${f.ruleId} ($instr)"
}
