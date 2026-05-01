#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from build_icao4444_downstream_baseline import build_downstream_baseline, write_json as write_baseline_json
from build_icao4444_review_pack import build_review_queue, render_review_pack, write_review_outputs
from check_icao4444_golden_regression import build_golden_regression_report, write_golden_regression_report
from check_icao4444_seeded_promotions import (
    DEFAULT_FIXTURE_DIR as DEFAULT_PROMOTION_FIXTURE_DIR,
    build_seeded_promotion_regression_report,
    write_json as write_promotion_regression_json,
)
from build_icao4444_seeded_promotions import build_artifact_payloads, write_json as write_promotion_json
from icao4444_normalizer_lib import DEFAULT_SOURCE, build_normalization_run, write_json
from render_icao4444_normalization_summary import render_summary, write_summary
from validate_icao4444_normalization import build_validation_report, write_validation_report


DEFAULT_OUTPUT_ROOT = Path("/tmp/icao4444-normalization-runs")
DEFAULT_FIXTURE_DIR = (
    Path(__file__).resolve().parent / "golden/icao4444"
)
DEFAULT_DOWNSTREAM_POLICY = (
    Path(__file__).resolve().parent / "downstream/icao4444_downstream_policy.json"
)
PIPELINE_VERSION = "2026-04-24-v3"


def utc_stamp() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H-%M-%SZ")


def choose_output_dir(output_dir: Path | None, output_root: Path) -> Path:
    if output_dir is not None:
        return output_dir
    return output_root / utc_stamp()


def initial_manifest(run: dict[str, Any], output_dir: Path) -> dict[str, Any]:
    return {
        **run["metadata"],
        "pipelineVersion": PIPELINE_VERSION,
        "artifactSchemaVersion": "icao4444-normalization-run-v2",
        "artifactPaths": {
            "documentTape": str(output_dir / "document_tape.json"),
            "blockTree": str(output_dir / "block_tree.json"),
            "sourceUnits": str(output_dir / "source_units.json"),
            "bundleCandidates": str(output_dir / "bundle_candidates.json"),
        },
        "stageCounts": {
            "documentTapeLines": len(run["documentTape"]),
            "blockNodes": len(run["blockTree"]),
            "sourceUnits": len(run["sourceUnits"]),
            "bundleCandidates": len(run["bundleCandidates"]),
        },
        "validationSummary": None,
        "reviewSummary": None,
        "goldenRegressionSummary": None,
        "downstreamBaselineSummary": None,
        "seededPromotionSummary": None,
        "counts": run["counts"],
    }


def update_manifest(
    output_dir: Path,
    manifest: dict[str, Any],
    *,
    validation_report: dict[str, Any],
    review_queue: dict[str, Any],
    golden_report: dict[str, Any] | None,
    downstream_baseline: dict[str, Any] | None,
    seeded_promotion_report: dict[str, Any] | None,
) -> None:
    manifest["artifactPaths"].update(
        {
            "validationReport": str(output_dir / "validation_report.json"),
            "normalizationSummary": str(output_dir / "normalization_summary.md"),
            "reviewQueue": str(output_dir / "review_queue.json"),
            "reviewPack": str(output_dir / "review_pack.md"),
        }
    )
    if golden_report is not None:
        manifest["artifactPaths"]["goldenRegressionReport"] = str(output_dir / "golden_regression_report.json")
    if downstream_baseline is not None:
        manifest["artifactPaths"]["downstreamBaseline"] = str(output_dir / "downstream_baseline.json")
    if seeded_promotion_report is not None:
        manifest["artifactPaths"]["seededPromotionDir"] = str(output_dir / "seeded_promotions")
        manifest["artifactPaths"]["seededPromotionRegressionReport"] = str(output_dir / "seeded_promotion_regression_report.json")
    manifest["validationSummary"] = {
        "machineGate": validation_report["machineGate"],
        "failureCount": len(validation_report["failures"]),
        "warningCount": len(validation_report["warnings"]),
        "unknownStructureCount": validation_report["unknownStructureCount"],
    }
    manifest["reviewSummary"] = {
        "reviewItemCount": review_queue["reviewItemCount"],
        "labelStubCount": review_queue["labelStubCount"],
        "unknownStructureCount": review_queue["unknownStructureCount"],
    }
    manifest["goldenRegressionSummary"] = (
        None
        if golden_report is None
        else {
            "status": golden_report["status"],
            "fixtureCount": golden_report["fixtureCount"],
            "failureCount": golden_report["failures"],
        }
    )
    manifest["downstreamBaselineSummary"] = (
        None
        if downstream_baseline is None
        else {
            "baselineId": downstream_baseline["baselineId"],
            "eligibleBundleCount": downstream_baseline["eligibleBundleCount"],
            "blockedBundleCount": downstream_baseline["blockedBundleCount"],
            "seedEligibleBundleCount": downstream_baseline["seedEligibleBundleCount"],
        }
    )
    manifest["seededPromotionSummary"] = (
        None
        if seeded_promotion_report is None
        else {
            "status": seeded_promotion_report["status"],
            "artifactCount": seeded_promotion_report["artifactCount"],
            "failureCount": len(seeded_promotion_report["failures"]),
        }
    )
    write_json(output_dir / "run_manifest.json", manifest)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--unknown-structure-threshold", type=int, default=0)
    parser.add_argument("--fixture-dir", type=Path)
    parser.add_argument("--skip-golden-regression", action="store_true")
    parser.add_argument("--skip-seeded-promotions", action="store_true")
    parser.add_argument("--downstream-policy", type=Path, default=DEFAULT_DOWNSTREAM_POLICY)
    parser.add_argument("--promotion-fixture-dir", type=Path, default=DEFAULT_PROMOTION_FIXTURE_DIR)
    parser.add_argument("--require-machine-gate-pass", action="store_true")
    parser.add_argument("--require-golden-pass", action="store_true")
    parser.add_argument("--require-seeded-promotion-pass", action="store_true")
    args = parser.parse_args()

    output_dir = choose_output_dir(args.output_dir, args.output_root)
    output_dir.mkdir(parents=True, exist_ok=True)

    run = build_normalization_run(args.source)
    write_json(output_dir / "document_tape.json", run["documentTape"])
    write_json(output_dir / "block_tree.json", run["blockTree"])
    write_json(output_dir / "source_units.json", run["sourceUnits"])
    write_json(output_dir / "bundle_candidates.json", run["bundleCandidates"])

    manifest = initial_manifest(run, output_dir)
    write_json(output_dir / "run_manifest.json", manifest)

    validation_report = build_validation_report(
        output_dir,
        unknown_structure_threshold=args.unknown_structure_threshold,
    )
    write_validation_report(output_dir, validation_report)

    summary = render_summary(output_dir)
    write_summary(output_dir, summary)

    review_queue = build_review_queue(output_dir)
    write_review_outputs(output_dir, review_queue, render_review_pack(review_queue))

    golden_report: dict[str, Any] | None = None
    fixture_dir = args.fixture_dir or DEFAULT_FIXTURE_DIR
    if not args.skip_golden_regression and fixture_dir.exists():
        golden_report = build_golden_regression_report(output_dir, fixture_dir)
        write_golden_regression_report(output_dir / "golden_regression_report.json", golden_report)

    downstream_baseline: dict[str, Any] | None = None
    seeded_promotion_report: dict[str, Any] | None = None
    if not args.skip_seeded_promotions:
        if validation_report["machineGate"] != "pass":
            seeded_promotion_report = {
                "status": "blocked_by_machine_gate",
                "artifactCount": 0,
                "failures": [
                    {
                        "kind": "machine_gate_not_passed",
                        "machineGate": validation_report["machineGate"],
                    }
                ],
            }
        else:
            downstream_baseline = build_downstream_baseline(output_dir, args.downstream_policy)
            write_baseline_json(output_dir / "downstream_baseline.json", downstream_baseline)
            payloads, manifest_payload = build_artifact_payloads(
                labels=sorted(item["primaryLabel"] for item in downstream_baseline["seedEligibleBundles"]),
                run_dir=output_dir,
                baseline_path=output_dir / "downstream_baseline.json",
            )
            promotion_dir = output_dir / "seeded_promotions"
            for filename, payload in payloads:
                write_promotion_json(promotion_dir / filename, payload)
            write_promotion_json(promotion_dir / "promotion_manifest.json", manifest_payload)
            seeded_promotion_report = build_seeded_promotion_regression_report(
                promotion_dir,
                args.promotion_fixture_dir,
            )
            write_promotion_regression_json(
                output_dir / "seeded_promotion_regression_report.json",
                seeded_promotion_report,
            )

    update_manifest(
        output_dir,
        manifest,
        validation_report=validation_report,
        review_queue=review_queue,
        golden_report=golden_report,
        downstream_baseline=downstream_baseline,
        seeded_promotion_report=seeded_promotion_report,
    )

    if args.require_machine_gate_pass and validation_report["machineGate"] != "pass":
        raise SystemExit("machine gate failed")
    if args.require_golden_pass and golden_report is not None and golden_report["status"] != "pass":
        raise SystemExit("golden regression failed")
    if args.require_seeded_promotion_pass:
        if seeded_promotion_report is None:
            raise SystemExit("seeded promotion stage was skipped")
        if seeded_promotion_report["status"] != "pass":
            raise SystemExit("seeded promotion regression failed")

    print(json.dumps(
        {
            "runDir": str(output_dir),
            "machineGate": validation_report["machineGate"],
            "goldenRegression": None if golden_report is None else golden_report["status"],
            "seededPromotionRegression": None if seeded_promotion_report is None else seeded_promotion_report["status"],
            "reviewItemCount": review_queue["reviewItemCount"],
        },
        indent=2,
    ))


if __name__ == "__main__":
    main()
