package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.WindReport

/**
 * Observed weather at a single aerodrome. The [wind] field is a sealed
 * [WindReport] (not nullable) so every consumer must handle the
 * "no report" case explicitly.
 *
 * fn-16: relocated from `:controller` to `:core/world` (sibling of
 * [Aerodrome]) per `project_rich_world_domain.md` — time-varying state
 * lives on the entity. With [Aerodrome.weather] now the single source of
 * truth, [WeatherObservation] is structurally the value carried by that
 * field, and `:core` is the natural module home so every reader
 * (`:controller`, `:sim`, `:pilot` tests) imports from one place.
 *
 * fn-14.1 (G3a-react) prior: the [WindReport] sealed type was lifted to
 * `:protocol` so the pilot can consume the wind projection through the
 * firewall without depending on `:controller`. `WeatherObservation`
 * (the full `(WindReport, qnh, visibility)` triple) was retained at
 * `:controller` then; fn-16 moves it to `:core/world` alongside
 * [Aerodrome] where it logically lives.
 */
data class WeatherObservation(
    val wind: WindReport,
    val qnh: PressureSetting?,
    val visibility: Int?,
)
