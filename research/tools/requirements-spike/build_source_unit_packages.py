#!/usr/bin/env python3
"""Build current-frame per-source source-unit packages.

This is intentionally scoped to the fn-9 current registry frame. It packages
the accepted registry records per source and validates the package inventory
against the closure review, final source-progress report, and live registry.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
SPIKE = ROOT / "research/tools/requirements-spike"
DEFAULT_REGISTRY_ROOT = SPIKE / "registry/ollama_first"
DEFAULT_DOCUMENTS_DIR = SPIKE / "documents"
DEFAULT_QUALITY_ROOT = SPIKE / "quality"
DEFAULT_CLOSURE_REPORT = DEFAULT_QUALITY_ROOT / "closure/closure_2026-05-09-fn9/closure_report.json"
DEFAULT_STATUS_REPORT = (
    DEFAULT_QUALITY_ROOT
    / "source_processing_queue/source_processing_queue_2026-05-09-fn9-post-curation"
    / "source_progress_report.json"
)
DEFAULT_OUTPUT_ROOT = DEFAULT_QUALITY_ROOT / "source_packages"


PACKAGE_STATUS_BY_READINESS = {
    "package-ready": "ready",
    "scoped-package-ready": "scoped_ready",
    "blocked": "blocked",
}

PACKAGE_STATUS_VALUES = frozenset(PACKAGE_STATUS_BY_READINESS.values())


def utc_stamp() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H-%M-%SZ")


def utc_now() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def repo_relative(path: Path) -> str:
    resolved = path if path.is_absolute() else ROOT / path
    try:
        return str(resolved.resolve().relative_to(ROOT))
    except ValueError:
        return str(resolved.resolve())


def record_path(path: Path) -> bool:
    if path.name == "_section.json" or path.suffix != ".json":
        return False
    parts = path.stem.split("::")
    return len(parts) == 3 and len(parts[2]) == 16


def load_manifests(documents_dir: Path) -> dict[str, dict[str, Any]]:
    manifests: dict[str, dict[str, Any]] = {}
    for path in sorted(documents_dir.glob("*.json")):
        manifest = read_json(path)
        manifests[manifest["documentId"]] = {
            "path": str(path.relative_to(ROOT)),
            "manifest": manifest,
        }
    return manifests


def load_registry_records(registry_root: Path) -> dict[str, dict[str, list[dict[str, Any]]]]:
    records: dict[str, dict[str, list[dict[str, Any]]]] = defaultdict(
        lambda: {"candidates": [], "pending": [], "rejected": []}
    )
    for bucket in ("candidates", "pending", "rejected"):
        bucket_root = registry_root / bucket
        if not bucket_root.exists():
            continue
        for path in sorted(bucket_root.rglob("*.json")):
            if not record_path(path):
                continue
            record = read_json(path)
            records[record["documentId"]][bucket].append(record)
    return records


def section_counts(records: list[dict[str, Any]]) -> Counter[str]:
    return Counter(record.get("sectionId", "") for record in records)


def package_non_claims(source: dict[str, Any]) -> list[str]:
    non_claims = [
        "This package is per-source only; it does not integrate, deduplicate, or choose precedence across sources.",
        "This package does not claim full corpus completeness, Nolan coverage, or EPPLS coverage.",
    ]
    if source["readiness"] == "scoped-package-ready":
        non_claims.append(
            "This package covers the manifested source windows only; high-priority source-window "
            "hardening rows outside those windows remain out of package scope."
        )
    non_claims.extend(source.get("scopeNotes") or [])
    return non_claims


def build_source_package(
    *,
    source: dict[str, Any],
    manifest_wrapper: dict[str, Any] | None,
    records: dict[str, list[dict[str, Any]]],
    closure_report: dict[str, Any],
    status_report_path: Path,
    closure_report_path: Path,
) -> dict[str, Any]:
    readiness = source["readiness"]
    package_status = PACKAGE_STATUS_BY_READINESS.get(readiness)
    if package_status is None:
        raise ValueError(f"unknown source readiness for {source['documentId']}: {readiness!r}")

    manifest = manifest_wrapper["manifest"] if manifest_wrapper else None
    accepted = sorted(records["candidates"], key=lambda record: record["canonicalId"])
    pending = sorted(records["pending"], key=lambda record: record["canonicalId"])
    rejected = sorted(records["rejected"], key=lambda record: record["canonicalId"])
    accepted_by_section = section_counts(accepted)
    pending_by_section = section_counts(pending)
    rejected_by_section = section_counts(rejected)
    sections = []
    for section in manifest.get("sections", []) if manifest else []:
        section_id = section["sectionId"]
        sections.append(
            {
                "sectionId": section_id,
                "familyId": section.get("familyId", ""),
                "sourceRef": f"{manifest['sourcePath']}:{section['startLine']}-{section['endLine']}",
                "acceptedRecords": accepted_by_section.get(section_id, 0),
                "pendingRecords": pending_by_section.get(section_id, 0),
                "rejectedRecords": rejected_by_section.get(section_id, 0),
                "notes": section.get("notes", ""),
            }
        )

    blockers: list[str] = []
    if source["pendingRecords"]:
        blockers.append("pending records remain")
    if source.get("readyToIngestManifestSections"):
        blockers.append("ready-to-ingest manifest sections remain")
    if package_status == "ready" and source.get("highPriorityRowsNeedingSourceWindowHardening"):
        blockers.append("ready package cannot have high-priority source-window hardening backlog")

    return {
        "schemaName": "requirements_source_unit_package",
        "formatVersion": "2026-05-09-v1",
        "generatedAt": closure_report["generatedAt"],
        "sourceFrameId": "fn9-current-frame-2026-05-09",
        "documentId": source["documentId"],
        "documentTitle": manifest.get("documentTitle", source["documentId"]) if manifest else source["documentId"],
        "sourcePath": manifest.get("sourcePath") if manifest else None,
        "manifestPath": manifest_wrapper["path"] if manifest_wrapper else None,
        "packageStatus": package_status,
        "closureReadiness": readiness,
        "closureReason": source["reason"],
        "sections": sections,
        "sourceUnits": accepted,
        "recordCounts": {
            "accepted": len(accepted),
            "pending": len(pending),
            "rejected": len(rejected),
        },
        "rejectedRecordIds": [record["canonicalId"] for record in rejected],
        "residualGaps": {
            "highPriorityRowsNeedingSourceWindowHardening": source[
                "highPriorityRowsNeedingSourceWindowHardening"
            ],
        },
        "blockers": blockers,
        "nonClaims": package_non_claims(source),
        "validationEvidence": {
            "closureReview": repo_relative(closure_report_path),
            "sourceProgressReport": repo_relative(status_report_path),
            "quoteAudit": closure_report["evidence"]["quoteAuditJson"],
            "reproducibilityAudit": closure_report["evidence"]["reproducibilityAuditJson"],
            "adequacyAssessment": (
                "research/tools/requirements-spike/quality/adequacy/"
                "adequacy_2026-05-09-fn9-current-frame/adequacy_assessment.md"
            ),
        },
    }


def validate_packages(
    *,
    package_manifest: dict[str, Any],
    packages: dict[str, dict[str, Any]],
    closure_report: dict[str, Any],
    status_report: dict[str, Any],
    registry_records: dict[str, dict[str, list[dict[str, Any]]]],
) -> dict[str, Any]:
    errors: list[dict[str, Any]] = []
    closure_by_doc = {
        source["documentId"]: source
        for source in closure_report["sourceClassifications"]
    }
    status_by_doc = {
        source["documentId"]: source
        for source in status_report["levels"]["source"]
    }
    manifest_docs = {pkg["documentId"] for pkg in package_manifest["packages"]}
    expected_docs = set(closure_by_doc)
    if manifest_docs != expected_docs:
        errors.append(
            {
                "kind": "package_document_set_mismatch",
                "expected": sorted(expected_docs),
                "actual": sorted(manifest_docs),
            }
        )

    for document_id, closure_source in closure_by_doc.items():
        package = packages.get(document_id)
        if package is None:
            errors.append({"kind": "missing_package", "documentId": document_id})
            continue
        expected_status = PACKAGE_STATUS_BY_READINESS[closure_source["readiness"]]
        if package.get("packageStatus") != expected_status:
            errors.append(
                {
                    "kind": "package_status_mismatch",
                    "documentId": document_id,
                    "expected": expected_status,
                    "actual": package.get("packageStatus"),
                }
            )
        if package.get("packageStatus") not in PACKAGE_STATUS_VALUES:
            errors.append({"kind": "unknown_package_status", "documentId": document_id})
        live = registry_records.get(document_id, {"candidates": [], "pending": [], "rejected": []})
        expected_counts = {
            "accepted": len(live["candidates"]),
            "pending": len(live["pending"]),
            "rejected": len(live["rejected"]),
        }
        if package.get("recordCounts") != expected_counts:
            errors.append(
                {
                    "kind": "record_count_mismatch",
                    "documentId": document_id,
                    "expected": expected_counts,
                    "actual": package.get("recordCounts"),
                }
            )
        if len(package.get("sourceUnits") or []) != expected_counts["accepted"]:
            errors.append(
                {
                    "kind": "source_unit_count_mismatch",
                    "documentId": document_id,
                    "expected": expected_counts["accepted"],
                    "actual": len(package.get("sourceUnits") or []),
                }
            )
        status_source = status_by_doc.get(document_id)
        if status_source is None:
            errors.append({"kind": "missing_status_source", "documentId": document_id})
            continue
        for field in (
            "manifestSections",
            "landedManifestSections",
            "acceptedRecords",
            "pendingRecords",
            "rejectedRecords",
            "highPriorityRowsNeedingSourceWindowHardening",
        ):
            if closure_source.get(field) != status_source.get(field):
                errors.append(
                    {
                        "kind": "closure_status_count_mismatch",
                        "documentId": document_id,
                        "field": field,
                        "closure": closure_source.get(field),
                        "status": status_source.get(field),
                    }
                )
        if package["packageStatus"] == "ready":
            if package["recordCounts"]["pending"]:
                errors.append({"kind": "ready_has_pending_records", "documentId": document_id})
            if package["residualGaps"]["highPriorityRowsNeedingSourceWindowHardening"]:
                errors.append({"kind": "ready_has_hardening_backlog", "documentId": document_id})
        if document_id == "h01-extracted" and package["packageStatus"] == "ready":
            errors.append({"kind": "h01_ready_hidden_scope_claim", "documentId": document_id})

    return {
        "schemaName": "requirements_source_package_validation",
        "formatVersion": "2026-05-09-v1",
        "generatedAt": package_manifest["generatedAt"],
        "status": "pass" if not errors else "fail",
        "errors": errors,
        "counts": {
            "packages": len(packages),
            "ready": sum(1 for package in packages.values() if package["packageStatus"] == "ready"),
            "scopedReady": sum(
                1 for package in packages.values() if package["packageStatus"] == "scoped_ready"
            ),
            "blocked": sum(1 for package in packages.values() if package["packageStatus"] == "blocked"),
            "sourceUnits": sum(len(package["sourceUnits"]) for package in packages.values()),
        },
    }


def build_packages(
    *,
    registry_root: Path,
    documents_dir: Path,
    closure_report_path: Path,
    status_report_path: Path,
    output_dir: Path,
) -> dict[str, Any]:
    generated_at = utc_now()
    closure_report = read_json(closure_report_path)
    status_report = read_json(status_report_path)
    manifests = load_manifests(documents_dir)
    registry_records = load_registry_records(registry_root)

    packages: dict[str, dict[str, Any]] = {}
    package_entries = []
    for source in closure_report["sourceClassifications"]:
        document_id = source["documentId"]
        package = build_source_package(
            source=source,
            manifest_wrapper=manifests.get(document_id),
            records=registry_records.get(document_id, {"candidates": [], "pending": [], "rejected": []}),
            closure_report={**closure_report, "generatedAt": generated_at},
            status_report_path=status_report_path,
            closure_report_path=closure_report_path,
        )
        package_path = output_dir / f"{document_id}.source_package.json"
        write_json(package_path, package)
        packages[document_id] = package
        package_entries.append(
            {
                "documentId": document_id,
                "packagePath": repo_relative(package_path),
                "packageStatus": package["packageStatus"],
                "acceptedRecords": package["recordCounts"]["accepted"],
                "pendingRecords": package["recordCounts"]["pending"],
                "rejectedRecords": package["recordCounts"]["rejected"],
            }
        )

    package_manifest = {
        "schemaName": "requirements_source_package_manifest",
        "formatVersion": "2026-05-09-v1",
        "generatedAt": generated_at,
        "sourceFrameId": "fn9-current-frame-2026-05-09",
        "closureReview": repo_relative(closure_report_path),
        "sourceProgressReport": repo_relative(status_report_path),
        "validationReport": repo_relative(output_dir / "validation_report.json"),
        "packages": package_entries,
        "nonClaims": [
            "Packages are per-source; no cross-source integration, deduplication, or precedence selection is claimed.",
            "Packages cover the current source frame only; Nolan and EPPLS are excluded future sources.",
            "A package with status scoped_ready is not a full-document-complete source representation.",
        ],
    }
    validation_report = validate_packages(
        package_manifest=package_manifest,
        packages=packages,
        closure_report=closure_report,
        status_report=status_report,
        registry_records=registry_records,
    )
    write_json(output_dir / "package_manifest.json", package_manifest)
    write_json(output_dir / "validation_report.json", validation_report)
    if validation_report["status"] != "pass":
        raise SystemExit(json.dumps(validation_report, indent=2, ensure_ascii=False))
    return {
        "outputDir": str(output_dir),
        "packageManifest": repo_relative(output_dir / "package_manifest.json"),
        "validationReport": repo_relative(output_dir / "validation_report.json"),
        "validationStatus": validation_report["status"],
        "counts": validation_report["counts"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Build current-frame source-unit packages.")
    parser.add_argument("--registry-root", type=Path, default=DEFAULT_REGISTRY_ROOT)
    parser.add_argument("--documents-dir", type=Path, default=DEFAULT_DOCUMENTS_DIR)
    parser.add_argument("--closure-report", type=Path, default=DEFAULT_CLOSURE_REPORT)
    parser.add_argument("--status-report", type=Path, default=DEFAULT_STATUS_REPORT)
    parser.add_argument("--output-dir", type=Path, default=None)
    args = parser.parse_args()

    output_dir = args.output_dir or (DEFAULT_OUTPUT_ROOT / f"source_packages_{utc_stamp()}")
    if not output_dir.is_absolute():
        output_dir = ROOT / output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    result = build_packages(
        registry_root=args.registry_root,
        documents_dir=args.documents_dir,
        closure_report_path=args.closure_report,
        status_report_path=args.status_report,
        output_dir=output_dir,
    )
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
