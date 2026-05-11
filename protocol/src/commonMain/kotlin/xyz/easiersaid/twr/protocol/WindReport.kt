package xyz.easiersaid.twr.protocol

/**
 * Sealed wind-report state. Replaces a `Wind?` field on
 * weather-observation types so consumers must explicitly handle the
 * "no wind report yet" case rather than treating null as a silent
 * fallback. Originally resolved G1-DEF-7 (pre-G1.6 must-fix) and
 * lived in `:controller/ControllerTypes.kt`.
 *
 * fn-14.1 (G3a-react): lifted from `:controller` to `:protocol` so
 * `:pilot` can consume it via [xyz.easiersaid.twr.pilot.PilotInput]
 * without depending on `:controller`. `WeatherObservation` (the
 * `(WindReport, qnh, visibility)` triple) stays in `:controller` —
 * only the wind projection crosses the pilot firewall. Move-only:
 * no shape change, no behavior change. Existing `:controller`
 * consumers re-import from `:protocol`.
 *
 * **Doctrine — wind direction convention** (FAA AIM §7-1-12.d.3):
 * the [Wind.directionDegrees] value carried inside [Available] is
 * **Magnetic, FROM-degrees** in twr2 — matching the ATIS/ATC voice
 * sensing path the pilot uses. METAR/TAF use True; printed text is
 * a different sensing channel and is out of scope for v1. Crosswind
 * computations against runway designators (which are themselves
 * Magnetic by convention) therefore share a single reference frame.
 */
sealed interface WindReport {
    /** A current wind report is available. */
    data class Available(val wind: Wind) : WindReport

    /**
     * No wind report has been received yet — typically before the first
     * METAR cycle, or in the controller's belief state when the weather
     * observation hasn't been refreshed. Downstream selection logic
     * (e.g. controller-side `selectRunwayIntoWind`, pilot-side crosswind
     * recognition) returns null/no-decision rather than picking a default.
     */
    data object NotReported : WindReport
}
