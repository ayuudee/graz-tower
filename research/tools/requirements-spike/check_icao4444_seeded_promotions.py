#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


DEFAULT_FIXTURE_DIR = (
    Path(__file__).resolve().parent / "downstream/full_document_seeded"
)


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def normalize_payload(payload: Any) -> Any:
    if isinstance(payload, dict):
        normalized = {key: normalize_payload(value) for key, value in payload.items()}
        if "promotionBasis" in normalized and isinstance(normalized["promotionBasis"], dict):
            if "sourceRunManifest" in normalized["promotionBasis"]:
                normalized["promotionBasis"]["sourceRunManifest"] = "__RUN_MANIFEST__"
        return normalized
    if isinstance(payload, list):
        return [normalize_payload(item) for item in payload]
    return payload


def build_seeded_promotion_regression_report(
    promotion_dir: Path,
    fixture_dir: Path,
) -> dict[str, Any]:
    fixture_manifest = load_json(fixture_dir / "promotion_manifest.json")
    promotion_manifest = load_json(promotion_dir / "promotion_manifest.json")

    failures: list[dict[str, Any]] = []
    for field in ("promotionCount", "labels", "artifacts"):
        if promotion_manifest[field] != fixture_manifest[field]:
            failures.append(
                {
                    "kind": "manifest_field_mismatch",
                    "field": field,
                    "expected": fixture_manifest[field],
                    "actual": promotion_manifest[field],
                }
            )

    for artifact in fixture_manifest["artifacts"]:
        fixture_path = fixture_dir / artifact
        promotion_path = promotion_dir / artifact
        if not promotion_path.exists():
            failures.append(
                {
                    "kind": "missing_artifact",
                    "artifact": artifact,
                    "expectedPath": str(fixture_path),
                    "actualPath": str(promotion_path),
                }
            )
            continue

        fixture_payload = normalize_payload(load_json(fixture_path))
        promotion_payload = normalize_payload(load_json(promotion_path))
        if promotion_payload != fixture_payload:
            failures.append(
                {
                    "kind": "artifact_mismatch",
                    "artifact": artifact,
                    "expectedPath": str(fixture_path),
                    "actualPath": str(promotion_path),
                }
            )

    return {
        "status": "pass" if not failures else "fail",
        "fixtureDir": str(fixture_dir),
        "promotionDir": str(promotion_dir),
        "artifactCount": len(fixture_manifest["artifacts"]),
        "failures": failures,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--promotion-dir", type=Path, required=True)
    parser.add_argument("--fixture-dir", type=Path, default=DEFAULT_FIXTURE_DIR)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    report = build_seeded_promotion_regression_report(
        args.promotion_dir,
        args.fixture_dir,
    )
    write_json(args.report, report)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
