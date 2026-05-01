#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


DEFAULT_BASELINE = Path("/tmp/icao4444-downstream-baseline.json")

SEEDED_PROMOTION_SPECS = {
    "4.3.2.1.1": {
        "acceptedAtomsFile": "icao4444_arriving_aircraft_transfer.full_document.accepted_atoms.json",
        "testCandidateFile": "icao4444_arriving_aircraft_transfer.full_document.test_candidate.json",
        "promotionId": "accepted-atoms:icao4444:4.3.2.1.1:full-document",
        "bundleId": "icao4444-extracted:4.3.2.1.1",
        "authorityClass": "authoritative_requirement",
        "verificationMode": "property_or_family_test",
        "expectedSelectors": ["4.3.2.1.1", "a)", "a)>1)", "a)>2)", "b)", "c)"],
        "atoms": [
            {
                "atomId": "icao4444:4.3.2.1.1:arriving_transfer_conditions",
                "sourceSelectors": ["4.3.2.1.1", "a)", "a)>1)", "a)>2)", "b)", "c)"],
                "dependsOnSelectors": [],
                "claimText": "Control of an arriving aircraft shall be transferred from the unit providing approach control service to the unit providing aerodrome control service when the aircraft is in the vicinity of the aerodrome and it is considered that approach and landing will be completed in visual reference to the ground, or the aircraft has reached uninterrupted visual meteorological conditions, or the aircraft is at a prescribed point or level, or the aircraft has landed, as specified in letters of agreement or ATS unit instructions.",
                "requirementKind": "workflow_constraint",
                "actors": [
                    "unit providing approach control service",
                    "unit providing aerodrome control service"
                ],
                "requiredBehaviour": [
                    "transfer control of an arriving aircraft only when one of the clause-defined arrival transfer conditions is satisfied"
                ],
                "applicability": [
                    "arriving controlled aircraft transitioning from approach control service to aerodrome control service"
                ],
                "sourceSupport": [
                    "Arriving aircraft. Control of an arriving aircraft shall be transferred from the unit providing approach control service to the unit providing aerodrome control service when the aircraft:",
                    "is in the vicinity of the aerodrome, and",
                    "it is considered that approach and landing will be completed in visual reference to the ground, or",
                    "has reached uninterrupted visual meteorological conditions, or",
                    "is at a prescribed point or level, or",
                    "has landed,",
                    "as specified in letters of agreement or ATS unit instructions."
                ],
            }
        ],
        "testCandidate": {
            "testCandidateId": "tc-icao4444-4.3.2.1.1-arriving-transfer-conditions-full-document",
            "verificationMode": "property_or_family_test",
            "targetLayer": "coordination logic integration",
            "goal": "Verify that arriving-aircraft control transfer is accepted only on the clause-defined arrival transfer conditions and remains traceable to the chosen branch.",
            "scenarioFamilies": [
                {
                    "familyId": "arriving-transfer-visual-reference",
                    "sourceAtomId": "icao4444:4.3.2.1.1:arriving_transfer_conditions"
                },
                {
                    "familyId": "arriving-transfer-prescribed-point-or-level",
                    "sourceAtomId": "icao4444:4.3.2.1.1:arriving_transfer_conditions"
                },
                {
                    "familyId": "arriving-transfer-landed",
                    "sourceAtomId": "icao4444:4.3.2.1.1:arriving_transfer_conditions"
                }
            ],
            "expectedObservations": [
                "Arrival transfer is accepted when one of the clause-defined conditions is satisfied.",
                "Arrival transfer that does not satisfy any clause-defined condition is rejected or flagged as non-compliant.",
                "The chosen transfer condition remains explicit and traceable in the evaluation."
            ],
            "sourceTraceability": {
                "document": "ICAO Doc 4444",
                "section": "4.3.2.1.1",
                "sourceRef": "/home/andrew/dev/projects/twr2/research/txt/icao4444-extracted.txt:3090-3103"
            }
        }
    },
    "4.3.2.1.2": {
        "acceptedAtomsFile": "icao4444_arriving_aircraft_transfer_timing.full_document.accepted_atoms.json",
        "reviewCandidateFile": "icao4444_arriving_aircraft_transfer_timing.full_document.review_candidate.json",
        "promotionId": "accepted-atoms:icao4444:4.3.2.1.2:full-document",
        "bundleId": "icao4444-extracted:4.3.2.1.2",
        "authorityClass": "operational_guidance",
        "verificationMode": "review_only",
        "expectedSelectors": ["4.3.2.1.2", "Note.—"],
        "atoms": [
            {
                "atomId": "icao4444:4.3.2.1.2:arriving_transfer_timing",
                "sourceSelectors": ["4.3.2.1.2"],
                "dependsOnSelectors": [],
                "claimText": "Transfer of communications to the aerodrome controller should be effected at such a point, level or time that clearance to land or alternative instructions, as well as information on essential local traffic, can be issued in a timely manner.",
                "requirementKind": "workflow_constraint",
                "actors": [
                    "unit providing approach control service",
                    "aerodrome controller",
                ],
                "requiredBehaviour": [
                    "effect transfer of communications to the aerodrome controller at a point, level or time that still permits timely landing clearance or alternative instructions and essential local traffic information"
                ],
                "applicability": [
                    "when transferring communications for arriving aircraft to the aerodrome controller"
                ],
                "sourceSupport": [
                    "Transfer of communications to the aerodrome controller should be effected at such a point, level or time",
                    "that clearance to land or alternative instructions, as well as information on essential local traffic, can be issued in a timely manner.",
                ],
            }
        ],
        "reviewCandidate": {
            "reviewCandidateId": "rc-icao4444-4.3.2.1.2-arriving-transfer-timing-full-document",
            "targetLayer": "coordination-logic or operational review",
            "goal": "Review whether communications transfer to the aerodrome controller is timed so landing clearance, alternative instructions, and essential local traffic information can still be issued in a timely manner.",
            "focusAreas": [
                "handoff timing",
                "availability of landing clearance or alternative instructions",
                "availability of essential local traffic information",
            ],
            "sourceTraceability": {
                "document": "ICAO Doc 4444",
                "section": "4.3.2.1.2",
                "sourceRef": "/home/andrew/dev/projects/twr2/research/txt/icao4444-extracted.txt:3104-3109",
            },
        },
    },
    "4.3.2.1.3": {
        "acceptedAtomsFile": "icao4444_departing_aircraft_transfer.full_document.accepted_atoms.json",
        "testCandidateFile": "icao4444_departing_aircraft_transfer.full_document.test_candidate.json",
        "promotionId": "accepted-atoms:icao4444:4.3.2.1.3:full-document",
        "bundleId": "icao4444-extracted:4.3.2.1.3",
        "authorityClass": "authoritative_requirement",
        "verificationMode": "property_or_family_test",
        "expectedSelectors": ["4.3.2.1.3", "a)", "a)>1)", "a)>2)", "a)>3)", "b)", "b)>1)", "b)>2)", "b)>Note.—"],
        "atoms": [
            {
                "atomId": "icao4444:4.3.2.1.3:vmc_transfer_branch",
                "sourceSelectors": ["a)", "a)>1)", "a)>2)", "a)>3)"],
                "dependsOnSelectors": ["4.3.2.1.3"],
                "claimText": "Control of a departing aircraft shall be transferred from the unit providing aerodrome control service to the unit providing approach control service when visual meteorological conditions prevail in the vicinity of the aerodrome, prior to the aircraft leaving the vicinity of the aerodrome, prior to the aircraft entering instrument meteorological conditions, or when the aircraft is at a prescribed point or level, as specified in letters of agreement or ATS unit instructions.",
                "requirementKind": "workflow_constraint",
                "actors": [
                    "unit providing aerodrome control service",
                    "unit providing approach control service",
                ],
                "requiredBehaviour": [
                    "transfer control of a departing aircraft under the visual-meteorological branch at one of the specified permitted points or times"
                ],
                "applicability": [
                    "visual meteorological conditions prevail in the vicinity of the aerodrome"
                ],
                "sourceSupport": [
                    "Control of a departing aircraft shall be transferred from the unit providing aerodrome control service to the unit providing approach control service:",
                    "when visual meteorological conditions prevail in the vicinity of the aerodrome:",
                    "prior to the time the aircraft leaves the vicinity of the aerodrome,",
                    "prior to the aircraft entering instrument meteorological conditions, or",
                    "when the aircraft is at a prescribed point or level,",
                    "as specified in letters of agreement or ATS unit instructions;",
                ],
            },
            {
                "atomId": "icao4444:4.3.2.1.3:imc_transfer_branch",
                "sourceSelectors": ["b)", "b)>1)", "b)>2)"],
                "dependsOnSelectors": ["4.3.2.1.3"],
                "claimText": "Control of a departing aircraft shall be transferred from the unit providing aerodrome control service to the unit providing approach control service when instrument meteorological conditions prevail at the aerodrome, immediately after the aircraft is airborne, or when the aircraft is at a prescribed point or level, as specified in letters of agreement or local instructions.",
                "requirementKind": "workflow_constraint",
                "actors": [
                    "unit providing aerodrome control service",
                    "unit providing approach control service",
                ],
                "requiredBehaviour": [
                    "transfer control of a departing aircraft under the instrument-meteorological branch immediately after airborne or at the prescribed point or level"
                ],
                "applicability": [
                    "instrument meteorological conditions prevail at the aerodrome"
                ],
                "sourceSupport": [
                    "Control of a departing aircraft shall be transferred from the unit providing aerodrome control service to the unit providing approach control service:",
                    "when instrument meteorological conditions prevail at the aerodrome:",
                    "immediately after the aircraft is airborne, or",
                    "when the aircraft is at a prescribed point or level,",
                    "as specified in letters of agreement or local instructions.",
                ],
            },
        ],
        "testCandidate": {
            "testCandidateId": "tc-icao4444-4.3.2.1.3-departing-transfer-branches-full-document",
            "verificationMode": "property_or_family_test",
            "targetLayer": "coordination logic integration",
            "goal": "Verify that departing-aircraft control transfer is evaluated against the correct meteorological branch and only considered compliant at one of the branch-specific permitted points or times.",
            "scenarioFamilies": [
                {
                    "familyId": "departing-transfer-vmc",
                    "sourceAtomId": "icao4444:4.3.2.1.3:vmc_transfer_branch",
                },
                {
                    "familyId": "departing-transfer-imc",
                    "sourceAtomId": "icao4444:4.3.2.1.3:imc_transfer_branch",
                },
            ],
            "expectedObservations": [
                "The VMC branch accepts transfer only at the listed visual-condition points or times.",
                "The IMC branch accepts transfer only immediately after airborne or at a prescribed point or level.",
                "The branch decision is explicit and traceable to the meteorological condition.",
                "A transfer that does not satisfy the active branch is rejected or flagged as non-compliant.",
            ],
            "sourceTraceability": {
                "document": "ICAO Doc 4444",
                "section": "4.3.2.1.3",
                "sourceRef": "/home/andrew/dev/projects/twr2/research/txt/icao4444-extracted.txt:3110-3130",
            },
        },
    },
    "4.5.7.5.1": {
        "acceptedAtomsFile": "icao4444_readback_required_items.full_document.accepted_atoms.json",
        "testCandidateFile": "icao4444_readback_required_items.full_document.test_candidate.json",
        "promotionId": "accepted-atoms:icao4444:4.5.7.5.1:full-document",
        "bundleId": "icao4444-extracted:4.5.7.5.1",
        "authorityClass": "authoritative_requirement",
        "verificationMode": "deterministic_test",
        "expectedSelectors": ["4.5.7.5.1", "a)", "b)", "c)"],
        "atoms": [
            {
                "atomId": "icao4444:4.5.7.5.1:voice_readback_core",
                "sourceSelectors": ["4.5.7.5.1"],
                "dependsOnSelectors": [],
                "claimText": "The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice.",
                "requirementKind": "rule",
                "actors": ["flight crew"],
                "requiredBehaviour": [
                    "read back to the air traffic controller safety-related parts of ATC clearances and instructions transmitted by voice"
                ],
                "applicability": [
                    "ATC clearances and instructions transmitted by voice"
                ],
                "sourceSupport": [
                    "The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice."
                ],
            },
            {
                "atomId": "icao4444:4.5.7.5.1:item_route_clearances",
                "sourceSelectors": ["a)"],
                "dependsOnSelectors": ["4.5.7.5.1"],
                "claimText": "The flight crew shall read back ATC route clearances.",
                "requirementKind": "rule",
                "actors": ["flight crew"],
                "requiredBehaviour": [
                    "read back ATC route clearances"
                ],
                "applicability": [
                    "when ATC route clearances are transmitted by voice"
                ],
                "sourceSupport": [
                    "The following items shall always be read back:",
                    "ATC route clearances;"
                ],
            },
            {
                "atomId": "icao4444:4.5.7.5.1:item_runway_movements",
                "sourceSelectors": ["b)"],
                "dependsOnSelectors": ["4.5.7.5.1"],
                "claimText": "The flight crew shall read back clearances and instructions to enter, land on, take off from, hold short of, cross, taxi and backtrack on any runway.",
                "requirementKind": "rule",
                "actors": ["flight crew"],
                "requiredBehaviour": [
                    "read back clearances and instructions to enter, land on, take off from, hold short of, cross, taxi and backtrack on any runway"
                ],
                "applicability": [
                    "when runway movement clearances or instructions are transmitted by voice"
                ],
                "sourceSupport": [
                    "The following items shall always be read back:",
                    "clearances and instructions to enter, land on, take off from, hold short of, cross, taxi and backtrack on any runway; and"
                ],
            },
            {
                "atomId": "icao4444:4.5.7.5.1:item_runway_altimeter_ssr_levels",
                "sourceSelectors": ["c)"],
                "dependsOnSelectors": ["4.5.7.5.1"],
                "claimText": "The flight crew shall read back runway-in-use, altimeter settings, SSR codes, level instructions, heading and speed instructions and transition levels.",
                "requirementKind": "rule",
                "actors": ["flight crew"],
                "requiredBehaviour": [
                    "read back runway-in-use, altimeter settings, SSR codes, level instructions, heading and speed instructions and transition levels"
                ],
                "applicability": [
                    "when the listed operational items are transmitted by voice or contained in ATIS broadcasts as specified by the clause"
                ],
                "sourceSupport": [
                    "The following items shall always be read back:",
                    "runway-in-use, altimeter settings, SSR codes, level instructions, heading and speed instructions and, whether issued by the controller or contained in automatic terminal information service (ATIS) broadcasts, transition levels."
                ],
            },
        ],
        "testCandidate": {
            "testCandidateId": "tc-icao4444-4.5.7.5.1-readback-items-full-document",
            "verificationMode": "property_or_family_test",
            "targetLayer": "controller+sim integration",
            "goal": "Verify that each always-read-back item family is treated as requiring pilot readback and does not transition to a completed acknowledgement state on silence or non-readback acknowledgement.",
            "scenarioFamilies": [
                {
                    "familyId": "route-clearance-readback",
                    "sourceAtomId": "icao4444:4.5.7.5.1:item_route_clearances",
                },
                {
                    "familyId": "runway-movement-readback",
                    "sourceAtomId": "icao4444:4.5.7.5.1:item_runway_movements",
                },
                {
                    "familyId": "operational-item-readback",
                    "sourceAtomId": "icao4444:4.5.7.5.1:item_runway_altimeter_ssr_levels",
                },
            ],
            "expectedObservations": [
                "The controller or protocol layer marks the item family as requiring readback.",
                "A correct readback satisfies the requirement.",
                "Silence or a generic acknowledgement without the required readback does not satisfy the requirement.",
                "Incorrect readback routes into the discrepancy-correction path rather than a successful acknowledgement state.",
            ],
            "sourceTraceability": {
                "document": "ICAO Doc 4444",
                "section": "4.5.7.5.1",
                "sourceRef": "/home/andrew/dev/projects/twr2/research/txt/icao4444-extracted.txt:3405-3417",
            },
        },
    },
    "4.5.7.5.1.1": {
        "acceptedAtomsFile": "icao4444_other_clearances_acknowledgement.full_document.accepted_atoms.json",
        "testCandidateFile": "icao4444_other_clearances_acknowledgement.full_document.test_candidate.json",
        "promotionId": "accepted-atoms:icao4444:4.5.7.5.1.1:full-document",
        "bundleId": "icao4444-extracted:4.5.7.5.1.1",
        "authorityClass": "authoritative_requirement",
        "verificationMode": "property_or_family_test",
        "expectedSelectors": ["4.5.7.5.1.1"],
        "atoms": [
            {
                "atomId": "icao4444:4.5.7.5.1.1:other_clearances_acknowledgement",
                "sourceSelectors": ["4.5.7.5.1.1"],
                "dependsOnSelectors": [],
                "claimText": "Other clearances or instructions, including conditional clearances, shall be read back or acknowledged in a manner that clearly indicates that they have been understood and will be complied with.",
                "requirementKind": "rule",
                "actors": ["flight crew"],
                "requiredBehaviour": [
                    "read back or acknowledge other clearances or instructions in a way that clearly indicates understanding and compliance"
                ],
                "applicability": [
                    "other clearances or instructions transmitted by voice, including conditional clearances"
                ],
                "sourceSupport": [
                    "Other clearances or instructions, including conditional clearances, shall be read back or acknowledged in a manner to clearly indicate that they have been understood and will be complied with."
                ],
            }
        ],
        "testCandidate": {
            "testCandidateId": "tc-icao4444-4.5.7.5.1.1-other-clearances-acknowledgement-full-document",
            "verificationMode": "property_or_family_test",
            "targetLayer": "controller+sim integration",
            "goal": "Verify that other clearances or instructions, including conditional clearances, require a readback or acknowledgement that clearly indicates understanding and intended compliance.",
            "scenarioFamilies": [
                {
                    "familyId": "conditional-clearance-acknowledgement",
                    "sourceAtomId": "icao4444:4.5.7.5.1.1:other_clearances_acknowledgement"
                },
                {
                    "familyId": "non-standard-instruction-acknowledgement",
                    "sourceAtomId": "icao4444:4.5.7.5.1.1:other_clearances_acknowledgement"
                }
            ],
            "expectedObservations": [
                "A response that clearly indicates understanding and compliance is accepted.",
                "Silence or an acknowledgement that does not clearly indicate understanding and compliance is rejected or flagged as insufficient."
            ],
            "sourceTraceability": {
                "document": "ICAO Doc 4444",
                "section": "4.5.7.5.1.1",
                "sourceRef": "/home/andrew/dev/projects/twr2/research/txt/icao4444-extracted.txt:3422-3423"
            }
        }
    },
    "4.5.7.5.2": {
        "acceptedAtomsFile": "icao4444_controller_readback_discrepancy_correction.full_document.accepted_atoms.json",
        "testCandidateFile": "icao4444_controller_readback_discrepancy_correction.full_document.test_candidate.json",
        "promotionId": "accepted-atoms:icao4444:4.5.7.5.2:full-document",
        "bundleId": "icao4444-extracted:4.5.7.5.2",
        "authorityClass": "authoritative_requirement",
        "verificationMode": "property_or_family_test",
        "expectedSelectors": ["4.5.7.5.2"],
        "atoms": [
            {
                "atomId": "icao4444:4.5.7.5.2:controller_discrepancy_correction",
                "sourceSelectors": ["4.5.7.5.2"],
                "dependsOnSelectors": [],
                "claimText": "The controller shall listen to the readback to ascertain that the clearance or instruction has been correctly acknowledged by the flight crew and shall take immediate action to correct any discrepancies revealed by the readback.",
                "requirementKind": "workflow_constraint",
                "actors": ["controller"],
                "requiredBehaviour": [
                    "listen to the readback to verify correct acknowledgement",
                    "take immediate corrective action when the readback reveals a discrepancy"
                ],
                "applicability": [
                    "when a flight crew readback is received for a clearance or instruction"
                ],
                "sourceSupport": [
                    "The controller shall listen to the readback to ascertain that the clearance or instruction has been correctly acknowledged by the flight crew and shall take immediate action to correct any discrepancies revealed by the readback."
                ],
            }
        ],
        "testCandidate": {
            "testCandidateId": "tc-icao4444-4.5.7.5.2-controller-discrepancy-correction-full-document",
            "verificationMode": "property_or_family_test",
            "targetLayer": "controller+sim integration",
            "goal": "Verify that the controller checks readbacks for correctness and corrects discrepancies immediately rather than treating them as successful acknowledgements.",
            "scenarioFamilies": [
                {
                    "familyId": "incorrect-readback-correction",
                    "sourceAtomId": "icao4444:4.5.7.5.2:controller_discrepancy_correction"
                },
                {
                    "familyId": "correct-readback-confirmation",
                    "sourceAtomId": "icao4444:4.5.7.5.2:controller_discrepancy_correction"
                }
            ],
            "expectedObservations": [
                "A correct readback is accepted.",
                "A discrepant readback routes into immediate correction rather than successful acknowledgement."
            ],
            "sourceTraceability": {
                "document": "ICAO Doc 4444",
                "section": "4.5.7.5.2",
                "sourceRef": "/home/andrew/dev/projects/twr2/research/txt/icao4444-extracted.txt:3424-3425"
            }
        }
    },
    "4.5.7.5.2.1": {
        "acceptedAtomsFile": "icao4444_cpdlc_voice_readback_not_required.full_document.accepted_atoms.json",
        "reviewCandidateFile": "icao4444_cpdlc_voice_readback_not_required.full_document.review_candidate.json",
        "promotionId": "accepted-atoms:icao4444:4.5.7.5.2.1:full-document",
        "bundleId": "icao4444-extracted:4.5.7.5.2.1",
        "authorityClass": "authoritative_requirement",
        "verificationMode": "review_only",
        "expectedSelectors": ["4.5.7.5.2.1", "Note.—"],
        "atoms": [
            {
                "atomId": "icao4444:4.5.7.5.2.1:cpdlc_voice_readback_not_required",
                "sourceSelectors": ["4.5.7.5.2.1"],
                "dependsOnSelectors": [],
                "claimText": "Unless specified by the appropriate ATS authority, voice readback of controller-pilot data link communications messages shall not be required.",
                "requirementKind": "workflow_constraint",
                "actors": ["controller", "flight crew"],
                "requiredBehaviour": [
                    "do not require voice readback of CPDLC messages unless the appropriate ATS authority specifies otherwise"
                ],
                "applicability": [
                    "controller-pilot data link communications messages"
                ],
                "sourceSupport": [
                    "Unless specified by the appropriate ATS authority, voice readback of controller-pilot data link communications (CPDLC) messages shall not be required."
                ],
            }
        ],
        "reviewCandidate": {
            "reviewCandidateId": "rc-icao4444-4.5.7.5.2.1-cpdlc-voice-readback-full-document",
            "targetLayer": "data-link conformance or protocol review",
            "goal": "Review whether any CPDLC implementation or conformance logic incorrectly requires voice readback when no appropriate ATS authority override exists.",
            "focusAreas": [
                "CPDLC acknowledgement handling",
                "voice readback requirements",
                "authority-specific overrides"
            ],
            "sourceTraceability": {
                "document": "ICAO Doc 4444",
                "section": "4.5.7.5.2.1",
                "sourceRef": "/home/andrew/dev/projects/twr2/research/txt/icao4444-extracted.txt:3426-3429"
            }
        }
    },
}


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def require_seeded_labels(baseline: dict[str, Any], labels: list[str]) -> None:
    seeded_labels = {item["primaryLabel"] for item in baseline["seedEligibleBundles"]}
    missing = [label for label in labels if label not in seeded_labels]
    if missing:
        raise SystemExit(f"labels are not in the seeded downstream baseline: {missing}")


def collect_descendant_units(
    unit_id: str,
    *,
    children_by_parent: dict[str, list[dict[str, Any]]],
) -> list[dict[str, Any]]:
    descendants: list[dict[str, Any]] = []
    pending = list(children_by_parent.get(unit_id, []))
    while pending:
        current = pending.pop(0)
        descendants.append(current)
        pending.extend(children_by_parent.get(current["sourceUnitId"], []))
    return descendants


def relative_selector(
    unit: dict[str, Any],
    *,
    units_by_id: dict[str, dict[str, Any]],
    blocks_by_id: dict[str, dict[str, Any]],
    root_unit_id: str,
) -> str:
    if unit["sourceUnitId"] == root_unit_id:
        return blocks_by_id[unit["blockId"]]["label"]
    labels: list[str] = []
    current = unit
    while current["sourceUnitId"] != root_unit_id:
        labels.append(blocks_by_id[current["blockId"]]["label"])
        parent_id = current["parentSourceUnitId"]
        if parent_id is None or parent_id not in units_by_id:
            raise SystemExit(
                f"Could not reconstruct selector path for {unit['sourceUnitId']}"
            )
        current = units_by_id[parent_id]
    return ">".join(reversed(labels))


def selector_index_for_bundle(
    *,
    bundle: dict[str, Any],
    units_by_id: dict[str, dict[str, Any]],
    blocks_by_id: dict[str, dict[str, Any]],
    children_by_parent: dict[str, list[dict[str, Any]]],
) -> dict[str, list[dict[str, Any]]]:
    relevant_units: list[dict[str, Any]] = []
    for member_id in bundle["memberIds"]:
        unit = units_by_id[member_id]
        relevant_units.append(unit)
        relevant_units.extend(
            collect_descendant_units(member_id, children_by_parent=children_by_parent)
        )

    index: dict[str, list[dict[str, Any]]] = {}
    seen: set[str] = set()
    for unit in relevant_units:
        if unit["sourceUnitId"] in seen:
            continue
        seen.add(unit["sourceUnitId"])
        selector = relative_selector(
            unit,
            units_by_id=units_by_id,
            blocks_by_id=blocks_by_id,
            root_unit_id=bundle["primarySourceUnitId"],
        )
        if selector is None:
            continue
        index.setdefault(selector, []).append(unit)
    return index


def build_accepted_atoms(
    *,
    spec: dict[str, Any],
    baseline: dict[str, Any],
    manifest: dict[str, Any],
    selector_units: dict[str, list[dict[str, Any]]],
    run_dir: Path,
) -> dict[str, Any]:
    return {
        "promotionId": spec["promotionId"],
        "bundleId": spec["bundleId"],
        "promotionBasis": {
            "promotionMode": "deterministic_seeded_full_document",
            "seedBaselineId": baseline["baselineId"],
            "policyId": baseline["policyId"],
            "sourceRunManifest": str(run_dir / "run_manifest.json"),
            "sourceSha256": manifest["sourceSha256"],
        },
        "authorityClass": spec["authorityClass"],
        "verificationMode": spec["verificationMode"],
        "atoms": [
            {
                "atomId": atom["atomId"],
                "claimText": atom["claimText"],
                "requirementKind": atom["requirementKind"],
                "sourceUnitIds": [
                    unit["sourceUnitId"]
                    for selector in atom["sourceSelectors"]
                    for unit in selector_units[selector]
                ],
                "dependsOnSourceUnitIds": [
                    unit["sourceUnitId"]
                    for selector in atom["dependsOnSelectors"]
                    for unit in selector_units[selector]
                ],
                "actors": atom["actors"],
                "requiredBehaviour": atom["requiredBehaviour"],
                "applicability": atom["applicability"],
                "sourceSupport": atom["sourceSupport"],
            }
            for atom in spec["atoms"]
        ],
    }


def build_artifact_payloads(
    *,
    labels: list[str],
    run_dir: Path,
    baseline_path: Path,
) -> tuple[list[tuple[str, dict[str, Any]]], dict[str, Any]]:
    baseline = load_json(baseline_path)
    require_seeded_labels(baseline, labels)

    manifest = load_json(run_dir / "run_manifest.json")
    blocks = load_json(run_dir / "block_tree.json")
    units = load_json(run_dir / "source_units.json")
    bundles = load_json(run_dir / "bundle_candidates.json")

    blocks_by_id = {block["blockId"]: block for block in blocks}
    units_by_id = {unit["sourceUnitId"]: unit for unit in units}
    children_by_parent: dict[str, list[dict[str, Any]]] = {}
    for unit in units:
        parent = unit["parentSourceUnitId"]
        if parent is not None:
            children_by_parent.setdefault(parent, []).append(unit)

    payloads: list[tuple[str, dict[str, Any]]] = []

    for label in labels:
        spec = SEEDED_PROMOTION_SPECS[label]
        bundle = next(
            item
            for item in bundles
            if blocks_by_id[units_by_id[item["primarySourceUnitId"]]["blockId"]]["label"] == label
        )
        selector_units = selector_index_for_bundle(
            bundle=bundle,
            units_by_id=units_by_id,
            blocks_by_id=blocks_by_id,
            children_by_parent=children_by_parent,
        )
        missing_selectors = [item for item in spec["expectedSelectors"] if item not in selector_units]
        if missing_selectors:
            raise SystemExit(f"Missing expected selectors for {label}: {missing_selectors}")

        accepted_atoms = build_accepted_atoms(
            spec=spec,
            baseline=baseline,
            manifest=manifest,
            selector_units=selector_units,
            run_dir=run_dir,
        )
        payloads.append((spec["acceptedAtomsFile"], accepted_atoms))

        if "testCandidateFile" in spec:
            payloads.append(
                (
                    spec["testCandidateFile"],
                    {
                        **spec["testCandidate"],
                        "sourcePromotionId": spec["promotionId"],
                    },
                )
            )
        if "reviewCandidateFile" in spec:
            payloads.append(
                (
                    spec["reviewCandidateFile"],
                    {
                        **spec["reviewCandidate"],
                        "sourcePromotionId": spec["promotionId"],
                    },
                )
            )

    manifest_payload = {
        "sourceDocumentId": manifest["sourceDocumentId"],
        "sourceSha256": manifest["sourceSha256"],
        "seedBaselineId": baseline["baselineId"],
        "promotionCount": len(labels),
        "labels": labels,
        "artifacts": [filename for filename, _ in payloads],
    }
    return payloads, manifest_payload


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--label", action="append", choices=sorted(SEEDED_PROMOTION_SPECS.keys()))
    parser.add_argument("--all-seeded", action="store_true")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    labels = sorted(SEEDED_PROMOTION_SPECS.keys()) if args.all_seeded else args.label
    if not labels:
        raise SystemExit("Provide --label ... or --all-seeded")

    payloads, manifest_payload = build_artifact_payloads(
        labels=labels,
        run_dir=args.run_dir,
        baseline_path=args.baseline,
    )

    for filename, payload in payloads:
        write_json(args.output_dir / filename, payload)
    write_json(args.output_dir / "promotion_manifest.json", manifest_payload)


if __name__ == "__main__":
    main()
