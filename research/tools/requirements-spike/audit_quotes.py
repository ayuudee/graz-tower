#!/usr/bin/env python3
"""Falsifiability audit: are `exactSourceQuotes` actually verbatim?

For each accepted candidate, check that every entry in its `exactSourceQuotes`
array appears in the source file after whitespace normalisation. Reports any
mismatches.

Usage:

    python3 research/tools/requirements-spike/audit_quotes.py \\
        --registry-root research/tools/requirements-spike/registry/ollama_first

Historical ingest-output mode is still available:

    python3 research/tools/requirements-spike/audit_quotes.py \\
        --output-root /tmp/overnight-shakedown
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]


def normalize(text: str) -> str:
    """Normalise text for verbatim-quote matching:
      1. Remove non-whitespace layout controls left by PDF extraction.
      2. Collapse all whitespace runs to a single space.
      3. Strip whitespace adjacent to / and -, since line wraps can split a
         word across these punctuation boundaries (e.g. "pilot/\ndriver" →
         "pilot/driver" once the layout artifact is removed). The model is
         expected to reconstruct the unwrapped form.
    """
    text = "".join(
        ch
        for ch in text
        if ch >= " " or ch in "\t\n\r\f\v"
    )
    s = re.sub(r"\s+", " ", text).strip()
    s = re.sub(r"\s*/\s*", "/", s)
    s = re.sub(r"-\s+", "-", s)
    return s


def load_source(source_path: str) -> str:
    """Load source file, return whitespace-normalised content."""
    full = (ROOT / source_path).read_text(encoding="utf-8")
    return normalize(full)


def audit_candidate(candidate: dict[str, Any], normalized_source: str) -> dict[str, Any]:
    quotes = candidate.get("exactSourceQuotes", [])
    misses = []
    hits = []
    for quote in quotes:
        if not isinstance(quote, str) or not quote.strip():
            misses.append({"quote": quote, "reason": "empty or non-string"})
            continue
        normalized_quote = normalize(quote)
        if normalized_quote in normalized_source:
            hits.append(quote)
        else:
            misses.append({
                "quote": quote,
                "reason": "not found verbatim in source after whitespace normalisation",
            })
    return {
        "candidateId": candidate.get("candidateId"),
        "sectionId": candidate.get("sectionId"),
        "totalQuotes": len(quotes),
        "hits": len(hits),
        "misses": misses,
    }


def audit_document(document_dir: Path) -> dict[str, Any]:
    aggregate_path = document_dir / "accepted_candidates.json"
    if not aggregate_path.exists():
        return {"documentDir": str(document_dir), "error": "no accepted_candidates.json"}
    aggregate = json.loads(aggregate_path.read_text(encoding="utf-8"))
    source_path = aggregate.get("sourcePath")
    if not source_path:
        return {"documentDir": str(document_dir), "error": "no sourcePath in aggregate"}
    normalized_source = load_source(source_path)
    candidate_audits: list[dict[str, Any]] = []
    for candidate in aggregate.get("acceptedCandidates", []):
        candidate_audits.append(audit_candidate(candidate, normalized_source))
    total_quotes = sum(c["totalQuotes"] for c in candidate_audits)
    total_misses = sum(len(c["misses"]) for c in candidate_audits)
    candidates_with_misses = [c for c in candidate_audits if c["misses"]]
    return {
        "documentId": aggregate.get("documentId"),
        "documentDir": str(document_dir),
        "sourcePath": source_path,
        "candidatesAudited": len(candidate_audits),
        "totalQuotes": total_quotes,
        "totalMisses": total_misses,
        "missRate": (total_misses / total_quotes) if total_quotes else 0.0,
        "candidatesWithMisses": candidates_with_misses,
    }


def accepted_registry_candidate_paths(registry_root: Path) -> list[Path]:
    candidates_root = registry_root / "candidates"
    if not candidates_root.exists():
        return []
    return [
        path
        for path in sorted(candidates_root.rglob("*.json"))
        if path.name != "_section.json"
    ]


def audit_registry(registry_root: Path) -> list[dict[str, Any]]:
    source_cache: dict[str, str] = {}
    audits_by_document: dict[str, dict[str, Any]] = {}
    for candidate_path in accepted_registry_candidate_paths(registry_root):
        candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
        if candidate.get("lifecycle", {}).get("state") != "accepted":
            continue
        document_id = candidate.get("documentId")
        source_path = candidate.get("provenance", {}).get("sourcePath")
        if not document_id or not source_path:
            document_id = document_id or str(candidate_path.parent)
            audit = audits_by_document.setdefault(
                document_id,
                {
                    "documentId": document_id,
                    "sourcePath": source_path,
                    "candidatesAudited": 0,
                    "totalQuotes": 0,
                    "totalMisses": 0,
                    "candidatesWithMisses": [],
                    "errors": [],
                },
            )
            audit["errors"].append({
                "candidatePath": str(candidate_path),
                "error": "missing documentId or provenance.sourcePath",
            })
            continue
        if source_path not in source_cache:
            source_cache[source_path] = load_source(source_path)
        candidate_audit = audit_candidate(candidate, source_cache[source_path])
        audit = audits_by_document.setdefault(
            document_id,
            {
                "documentId": document_id,
                "sourcePath": source_path,
                "candidatesAudited": 0,
                "totalQuotes": 0,
                "totalMisses": 0,
                "candidatesWithMisses": [],
                "errors": [],
            },
        )
        audit["candidatesAudited"] += 1
        audit["totalQuotes"] += candidate_audit["totalQuotes"]
        audit["totalMisses"] += len(candidate_audit["misses"])
        if candidate_audit["misses"]:
            audit["candidatesWithMisses"].append(candidate_audit)

    for audit in audits_by_document.values():
        total_quotes = audit["totalQuotes"]
        audit["missRate"] = (audit["totalMisses"] / total_quotes) if total_quotes else 0.0
    return list(audits_by_document.values())


def print_audits(audits: list[dict[str, Any]]) -> tuple[int, int, int]:
    error_count = 0
    for audit in sorted(audits, key=lambda item: item.get("documentId") or ""):
        if "error" in audit:
            print(f"{audit.get('documentDir', audit.get('documentId'))}: {audit['error']}")
            error_count += 1
            continue
        for error in audit.get("errors", []):
            print(f"{audit['documentId']}: {error['candidatePath']}: {error['error']}")
            error_count += 1
        print(
            f"{audit['documentId']}: {audit['candidatesAudited']} candidates, "
            f"{audit['totalQuotes']} quotes, {audit['totalMisses']} misses "
            f"({audit['missRate']:.1%})"
        )
        for candidate in audit["candidatesWithMisses"]:
            for miss in candidate["misses"]:
                excerpt = str(miss["quote"])[:120].replace("\n", " ")
                print(
                    f"  MISS - {candidate['sectionId']}::"
                    f"{candidate['candidateId']}: \"{excerpt}\""
                )
    overall_quotes = sum(a.get("totalQuotes", 0) for a in audits)
    overall_misses = sum(a.get("totalMisses", 0) for a in audits)
    return overall_quotes, overall_misses, error_count


def main() -> int:
    parser = argparse.ArgumentParser()
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--output-root", type=Path)
    source.add_argument(
        "--registry-root",
        type=Path,
        help="Promoted registry root containing candidates/*/*.json records",
    )
    args = parser.parse_args()

    if args.registry_root:
        audits = audit_registry(args.registry_root)
    else:
        document_dirs = [d for d in args.output_root.iterdir() if d.is_dir()]
        audits = [audit_document(d) for d in sorted(document_dirs)]

    overall_quotes, overall_misses, error_count = print_audits(audits)
    print()
    print(f"OVERALL: {overall_quotes} quotes, {overall_misses} misses "
          f"({overall_misses / overall_quotes:.1%} miss rate)" if overall_quotes else "no quotes audited")
    return 1 if overall_misses or error_count else 0


if __name__ == "__main__":
    raise SystemExit(main())
