package xyz.easiersaid.twr.protocol

/**
 * Parse the magnetic heading (degrees) of a runway from its
 * [RunwayId] designator.
 *
 * **Convention** (FAA AIM §7-1-12.d.3, ICAO Annex 14 §5.2): runway
 * designators are the runway's magnetic bearing in tens of degrees,
 * rounded to the nearest 10°. A designator of `27` therefore means
 * magnetic 270°. Multi-runway suffixes (`L`/`C`/`R` for parallels)
 * carry no heading information; only the leading two digits are
 * parsed.
 *
 * **Fail-closed parse**: returns `null` when
 *  - the [RunwayId.value] is shorter than two characters (a real
 *    runway designator is two digits per ICAO Annex 14 §5.2, with an
 *    optional `L`/`C`/`R` suffix; single-digit values like `"5"` or
 *    empty strings are not valid runway designators);
 *  - the first two characters are not an integer (e.g. `HX`);
 *  - the parsed designator is outside the real-runway range `01..36`
 *    (e.g. `00`, `37`, `99` — these are nonsense designators).
 *
 * The two failure modes are merged: any callers (pilot crosswind
 * recognition, controller runway-selection helpers) treat `null` as
 * "cannot reason about runway heading" and fail closed. **No silent
 * default-to-zero, no silent acceptance of out-of-range** — those
 * would mask both a bad fixture (a typo in a CAD aerodrome) and a
 * production bug (e.g. an EBLG-style `BX` synthetic identifier).
 *
 * Lifted from the inline pattern at
 * `controller/.../assess/RunwayAssessment.kt:402-409` and made
 * typed/range-checked. The controller's `selectRunwayConfiguration`
 * continues to use the inline form by design — its bucket scan is
 * tolerant of out-of-range designators (they sort to the back of the
 * crosswind bucket and are filtered out by the ±90° gate), while the
 * pilot's crosswind recognition requires a strict yes/no.
 *
 * **Examples**:
 *  - `RunwayId("27").headingDegreesMagnetic() == 270`
 *  - `RunwayId("36L").headingDegreesMagnetic() == 360`
 *  - `RunwayId("01R").headingDegreesMagnetic() == 10`
 *  - `RunwayId("00").headingDegreesMagnetic() == null` (out of range)
 *  - `RunwayId("37").headingDegreesMagnetic() == null` (out of range)
 *  - `RunwayId("HX").headingDegreesMagnetic() == null` (parse fail)
 *  - `RunwayId("").headingDegreesMagnetic() == null` (parse fail)
 */
fun RunwayId.headingDegreesMagnetic(): Int? =
    value.take(2)
        .takeIf { it.length == 2 }            // require both designator digits — no single-char tolerance
        ?.toIntOrNull()
        ?.takeIf { it in 1..36 }              // real-runway range — fail closed on 00 / 37 / 99
        ?.let { it * 10 }
