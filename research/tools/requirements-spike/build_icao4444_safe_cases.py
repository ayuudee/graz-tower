#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


DEFAULT_PROFILE = (
    Path(__file__).resolve().parent
    / "downstream/generated/icao4444/capability_profiles/controller_readback_v1.json"
)


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def accepted_atoms_artifact(promotion_manifest: dict[str, Any], promotion_dir: Path) -> Path:
    accepted = [
        artifact for artifact in promotion_manifest["artifacts"]
        if artifact.endswith(".accepted_atoms.json")
    ]
    if len(accepted) != 1:
        raise SystemExit(
            f"Expected exactly one accepted-atoms artifact in promotion manifest, found {accepted}"
        )
    return promotion_dir / accepted[0]


def require_supported_seeded_promotion(
    accepted_atoms: dict[str, Any],
    promotion_manifest: dict[str, Any],
    profile: dict[str, Any],
) -> None:
    if accepted_atoms["promotionBasis"]["promotionMode"] != "deterministic_seeded_full_document":
        raise SystemExit(
            f"Unsupported promotion mode {accepted_atoms['promotionBasis']['promotionMode']}"
        )
    if promotion_manifest["promotionCount"] != 1:
        raise SystemExit(
            f"Safe-case generator supports a single seeded promotion, found {promotion_manifest['promotionCount']}"
        )
    if profile["profileId"] != "controller_readback_v1":
        raise SystemExit(f"Unsupported capability profile {profile['profileId']}")
    if accepted_atoms["promotionId"] != "accepted-atoms:icao4444:4.5.7.5.1:full-document":
        raise SystemExit(f"Unsupported promotion {accepted_atoms['promotionId']}")


def atom_map(accepted_atoms: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {
        atom["atomId"]: atom
        for atom in accepted_atoms["atoms"]
    }


def derive_traceability(bundle_id: str, promotion_id: str, accepted_atoms: dict[str, Any]) -> dict[str, Any]:
    return {
        "sourcePromotionId": promotion_id,
        "sourceBundleId": bundle_id,
        "document": "ICAO Doc 4444",
        "section": "4.5.7.5.1",
    }


def build_generated_cases(
    accepted_atoms: dict[str, Any],
    promotion_manifest: dict[str, Any],
    profile: dict[str, Any],
) -> list[dict[str, Any]]:
    atoms_by_id = atom_map(accepted_atoms)
    generation_basis = {
        "generationMode": profile["generationMode"],
        "profileId": profile["profileId"],
        "profileVersion": profile["profileVersion"],
        "policyId": accepted_atoms["promotionBasis"]["policyId"],
        "seedBaselineId": accepted_atoms["promotionBasis"]["seedBaselineId"],
        "sourceSha256": promotion_manifest["sourceSha256"],
    }
    cases: list[dict[str, Any]] = []
    for case_spec in profile["supportedCases"]:
        source_atoms = [atoms_by_id[atom_id] for atom_id in case_spec["sourceAtomIds"]]
        source_unit_ids = [
            source_unit_id
            for atom in source_atoms
            for source_unit_id in atom["sourceUnitIds"]
        ]
        case = {
            "caseId": case_spec["caseId"],
            "sourcePromotionId": accepted_atoms["promotionId"],
            "sourceBundleId": accepted_atoms["bundleId"],
            "capabilityProfileId": profile["profileId"],
            "generationMode": profile["generationMode"],
            "verificationMode": "property_or_family_test",
            "targetLayer": profile["targetLayer"],
            "sourceAtomIds": case_spec["sourceAtomIds"],
            "supportedFacetIds": case_spec["supportedFacetIds"],
            "blockedSiblingFacetIds": case_spec.get("blockedSiblingFacetIds", []),
            "goal": case_spec["goal"],
            "instructionFamilies": case_spec["instructionFamilies"],
            "expectedObservations": case_spec["expectedObservations"],
            "sourceUnitIds": source_unit_ids,
            "sourceTraceability": derive_traceability(
                accepted_atoms["bundleId"],
                accepted_atoms["promotionId"],
                accepted_atoms,
            ),
            "generatorBasis": generation_basis,
        }
        if any(
            blocked_facet in case["goal"]
            for blocked_facet in case["blockedSiblingFacetIds"]
        ):
            raise SystemExit(
                f"Case {case['caseId']} goal mentions blocked sibling facet semantics"
            )
        cases.append(case)
    return cases


def build_blocked_residuals(
    accepted_atoms: dict[str, Any],
    promotion_manifest: dict[str, Any],
    profile: dict[str, Any],
) -> list[dict[str, Any]]:
    atoms_by_id = atom_map(accepted_atoms)
    residuals: list[dict[str, Any]] = []
    for residual_spec in profile["blockedResiduals"]:
        source_atoms = [atoms_by_id[atom_id] for atom_id in residual_spec["sourceAtomIds"]]
        residuals.append(
            {
                "residualId": residual_spec["residualId"],
                "sourcePromotionId": accepted_atoms["promotionId"],
                "sourceBundleId": accepted_atoms["bundleId"],
                "capabilityProfileId": profile["profileId"],
                "blockedAtomIds": residual_spec["sourceAtomIds"],
                "blockedFacetIds": residual_spec["blockedFacetIds"],
                "blockedReason": residual_spec["blockedReason"],
                "requiredModelGap": residual_spec["requiredModelGap"],
                "sourceUnitIds": [
                    source_unit_id
                    for atom in source_atoms
                    for source_unit_id in atom["sourceUnitIds"]
                ],
                "sourceTraceability": derive_traceability(
                    accepted_atoms["bundleId"],
                    accepted_atoms["promotionId"],
                    accepted_atoms,
                ),
                "generatorBasis": {
                    "generationMode": profile["generationMode"],
                    "profileId": profile["profileId"],
                    "profileVersion": profile["profileVersion"],
                    "sourceSha256": promotion_manifest["sourceSha256"],
                },
            }
        )
    return residuals


def build_review_queue(
    cases: list[dict[str, Any]],
    residuals: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    for case in cases:
        if case["blockedSiblingFacetIds"]:
            items.append(
                {
                    "reviewItemId": f"split-case::{case['caseId']}",
                    "reviewKind": "split_supported_case",
                    "targetId": case["caseId"],
                    "reason": "Case was emitted from a source family that required supported/blocked facet splitting.",
                }
            )
    for residual in residuals:
        items.append(
            {
                "reviewItemId": f"blocked-residual::{residual['residualId']}",
                "reviewKind": "blocked_residual",
                "targetId": residual["residualId"],
                "reason": residual["blockedReason"],
            }
        )
    return items


def render_review_pack(
    profile: dict[str, Any],
    cases: list[dict[str, Any]],
    residuals: list[dict[str, Any]],
    review_queue: list[dict[str, Any]],
) -> str:
    lines = [
        f"# Safe Case Review Pack - {profile['profileId']}",
        "",
        f"Generated cases: {len(cases)}",
        f"Blocked residuals: {len(residuals)}",
        f"Review items: {len(review_queue)}",
        "",
        "## Split Cases",
        "",
    ]
    split_cases = [case for case in cases if case["blockedSiblingFacetIds"]]
    if not split_cases:
        lines.append("- none")
    else:
        for case in split_cases:
            lines.extend(
                [
                    f"- `{case['caseId']}`",
                    f"  - supported facets: {', '.join(case['supportedFacetIds'])}",
                    f"  - blocked sibling facets: {', '.join(case['blockedSiblingFacetIds'])}",
                ]
            )
    lines.extend(["", "## Blocked Residuals", ""])
    if not residuals:
        lines.append("- none")
    else:
        for residual in residuals:
            lines.extend(
                [
                    f"- `{residual['residualId']}`",
                    f"  - blocked facets: {', '.join(residual['blockedFacetIds'])}",
                    f"  - reason: {residual['blockedReason']}",
                    f"  - gap: {residual['requiredModelGap']}",
                ]
            )
    lines.extend(["", "## Notes", ""])
    for note in profile.get("notes", []):
        lines.append(f"- {note}")
    return "\n".join(lines) + "\n"


def render_summary(
    profile: dict[str, Any],
    cases: list[dict[str, Any]],
    residuals: list[dict[str, Any]],
) -> str:
    lines = [
        f"# ICAO 4444 Safe Case Generation Summary - {profile['profileId']}",
        "",
        f"- Generated cases: `{len(cases)}`",
        f"- Blocked residuals: `{len(residuals)}`",
        "",
        "Generated cases:",
    ]
    for case in cases:
        lines.append(
            f"- `{case['caseId']}` -> facets: {', '.join(case['supportedFacetIds'])}"
        )
    lines.append("")
    lines.append("Blocked residuals:")
    for residual in residuals:
        lines.append(
            f"- `{residual['residualId']}` -> facets: {', '.join(residual['blockedFacetIds'])}"
        )
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--promotion-dir", type=Path, required=True)
    parser.add_argument("--profile", type=Path, default=DEFAULT_PROFILE)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    promotion_manifest = load_json(args.promotion_dir / "promotion_manifest.json")
    accepted_atoms = load_json(accepted_atoms_artifact(promotion_manifest, args.promotion_dir))
    profile = load_json(args.profile)

    require_supported_seeded_promotion(accepted_atoms, promotion_manifest, profile)

    cases = build_generated_cases(accepted_atoms, promotion_manifest, profile)
    residuals = build_blocked_residuals(accepted_atoms, promotion_manifest, profile)
    review_queue = build_review_queue(cases, residuals)

    output_dir = args.output_dir
    generated_cases_payload = {
        "profileId": profile["profileId"],
        "sourcePromotionId": accepted_atoms["promotionId"],
        "caseCount": len(cases),
        "cases": cases,
    }
    blocked_residuals_payload = {
        "profileId": profile["profileId"],
        "sourcePromotionId": accepted_atoms["promotionId"],
        "residualCount": len(residuals),
        "residuals": residuals,
    }
    generation_manifest = {
        "generationId": f"safe-case-generation:{profile['profileId']}:{accepted_atoms['promotionId']}",
        "profileId": profile["profileId"],
        "profileVersion": profile["profileVersion"],
        "sourcePromotionId": accepted_atoms["promotionId"],
        "sourceBundleId": accepted_atoms["bundleId"],
        "sourceSha256": promotion_manifest["sourceSha256"],
        "generatedCaseCount": len(cases),
        "blockedResidualCount": len(residuals),
        "artifacts": [
            "generated_cases.json",
            "blocked_residuals.json",
            "generation_review_queue.json",
            "generation_review_pack.md",
            "generation_summary.md",
        ],
    }

    write_json(output_dir / "generation_manifest.json", generation_manifest)
    write_json(output_dir / "generated_cases.json", generated_cases_payload)
    write_json(output_dir / "blocked_residuals.json", blocked_residuals_payload)
    write_json(output_dir / "generation_review_queue.json", {"items": review_queue})
    write_text(output_dir / "generation_review_pack.md", render_review_pack(profile, cases, residuals, review_queue))
    write_text(output_dir / "generation_summary.md", render_summary(profile, cases, residuals))


if __name__ == "__main__":
    main()
