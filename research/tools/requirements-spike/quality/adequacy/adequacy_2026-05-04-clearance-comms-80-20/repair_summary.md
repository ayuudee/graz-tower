# RR-21 Adequacy Repair Summary

Date: 2026-05-04

The 2026-05-04 clearance-comms 80/20 adequacy review found source-supported facts that had been rejected because their `exactSourceQuotes` crossed page-layout breaks or used non-verbatim quote punctuation. The original rejected records remain in `rejected/`; RR-21 adds corrected content-addressed replacements in `candidates/`.

## Repairs Applied

- `cap413-extracted::clearance_issue_context_2_65_to_2_67::e6be0b94c826d339` replaces `cap413-extracted::clearance_issue_context_2_65_to_2_67::983c86331a930a1b`: When a route clearance is passed subsequent to local departure instructions, or to an aircraft that is already airborne, tactical restrictions that remain in place shall be reiterated to ensure that the immediate profile to be flown by the pilot is unambiguous.
- `cap413-extracted::unable_reclearance_critical_info_2_72_to_2_75::4fead915adae476b` replaces `cap413-extracted::unable_reclearance_critical_info_2_72_to_2_75::af1bed78594b1d89`: Critical information is information, other than that required to enable routine flight, which must be received by pilots to ensure the safety and effective operation of their aircraft.
- `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::333dc36ac40f96bf` replaces `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::2058dc30009cd179`: When traffic conditions will not permit clearance of a requested change, the word 'UNABLE' shall be used.
- `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::61a1a8e58d00206b` replaces `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::3eb599f98d037d47`: The phrase 'cleared flight planned route' shall not be used when granting a re-clearance.
- `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::db9100ced0594851` replaces `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::643a320d5f693ad9`: The phrase 'cleared flight planned route' may be used to describe any route or portion thereof, provided the route or portion thereof is identical to that filed in the flight plan and sufficient routing details are given to definitely establish the aircraft on its route.
- `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::8e01c1f60f1cb79a` replaces `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::b0cea0c2e3246c75`: The phrases 'cleared (designation) departure' or 'cleared (designation) arrival' may be used when standard departure or arrival routes have been established by the appropriate ATS authority and published in Aeronautical Information Publications (AIPs).
- `sera-923-2012-extracted::adherence_flight_plan_8020::f09c370b7b90f3c2` replaces `sera-923-2012-extracted::adherence_flight_plan_8020::31b9cff5534bef98`: Controlled flights on any route other than an established ATS route shall, in so far as practicable, operate directly between the navigation facilities and/or points defining that route unless otherwise authorised or directed.
- `sera-923-2012-extracted::adherence_flight_plan_8020::04d7efdaa550b5b8` replaces `sera-923-2012-extracted::adherence_flight_plan_8020::35da37ec7ec88084`: Requests for flight plan changes involving a change of route with destination changed shall include aircraft identification, flight rules, description of revised route of flight to revised destination aerodrome including related flight plan data, beginning with the position from which requested change of route is to commence, revised time estimates, alternate aerodrome(s), and any other pertinent information.

## Adequacy Result

- Sampled records reviewed: 48.
- Sampled sections reviewed for omissions: 12.
- Pre-repair sampled lifecycle defects: 4.
- Additional same-pattern sibling defects repaired after targeted sweep: 4.
- Sampled section omissions remaining after repair: 0.

The residual confidence claim is scoped to the declared 46-window clearance-comms registry slice: all declared windows are translated, mechanically auditable, and the 80/20 adequacy sample no longer exposes a material omitted source unit after these repairs.
