# Safe Case Review Pack - controller_readback_v1

Generated cases: 3
Blocked residuals: 1
Review items: 2

## Split Cases

- `case-icao4444-4.5.7.5.1-operational-items-supported-controller-readback-v1`
  - supported facets: altimeter_settings, ssr_codes, level_instructions, heading_instructions, speed_instructions
  - blocked sibling facets: runway_in_use, transition_levels

## Blocked Residuals

- `residual-icao4444-4.5.7.5.1-operational-items-unmodelled-controller-readback-v1`
  - blocked facets: runway_in_use, transition_levels
  - reason: missing_instruction_model
  - gap: The current instruction model has no standalone ATC instruction types for explicit runway-in-use advisories or transition-level issuance.

## Notes

- This profile is deliberately narrow and only covers the first seeded proof case.
- Supported cases must not imply support for blocked sibling facets.
- The operational-item family is split explicitly because the source clause is broader than the current downstream capability surface.
