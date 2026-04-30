package xyz.easiersaid.twr.pilot

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test

/**
 * Architectural enforcement test (Pass 2, Item 6) — closure of D-PF.4.
 *
 * Every defaulted parameter on [PilotMission]'s primary constructor must be
 * non-nullable. Nullable defaults are forbidden after Pass 2: the algebraic
 * absence semantic is `Option<T> = None`, never `T? = null`.
 *
 * The check is reflective rather than source-text: Kotlin metadata on a
 * `data class`'s primary constructor is reliable across formatting, typealias,
 * and refactor variation. A regex would miss `typealias MaybeRunway = RunwayId?`
 * laundering, multi-line constructor formats, and required-without-default
 * nullable params.
 *
 * **Symmetric form**: the test forbids the `T? = null` shape uniformly. There
 * is no allowlist (`joinLeg: Option<LegName> = None` simply isn't nullable, so
 * it doesn't trigger the check). Required structural parameters (`goal`, `root`)
 * are skipped because they have no defaults.
 *
 * **No-suppression rule** (per Pass 2 plan, Item 6): an architectural test
 * failure is never resolved by `@Disabled`, `@Suppress`, or test removal.
 * Resolve by migrating the offending field to `Option<T> = None`, or by
 * formally amending the plan if D-PF.4's contract is being relaxed.
 */
class MissionOptionalityTest {

    @Test
    fun `every defaulted parameter on PilotMission's primary constructor is non-nullable`() {
        val ctor = PilotMission::class.primaryConstructor
            ?: throw AssertionError("PilotMission has no primary constructor — refactor changed shape unexpectedly")
        val violations = mutableListOf<String>()
        for (param in ctor.parameters) {
            val name = param.name ?: continue
            // Required parameters (no default) are structural fields like `goal` and `root`;
            // they are not subject to the optionality contract.
            if (!param.isOptional) continue
            if (param.type.isMarkedNullable) {
                violations += "$name: ${param.type} — nullable defaulted parameter; migrate to Option<T> = None."
            }
        }
        check(violations.isEmpty()) {
            """
            OPTIONALITY VIOLATION (D-PF.4 closure): PilotMission's primary constructor
            contains nullable defaulted parameters that must be migrated to Option<T>:
                ${violations.joinToString("\n                ")}

            Fix: replace `T? = null` with `Option<T> = None`. The primary constructor
            must use the algebraic Option form for absence semantics — null defaults are
            forbidden after Pass 2 (D-PF.4 closure in pilot-firewall.md).
            """.trimIndent()
        }
    }
}
