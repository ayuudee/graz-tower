#!/usr/bin/env python3
"""Falsifiability audit: are `exactSourceQuotes` actually verbatim?

For each accepted candidate in a per-document `accepted_candidates.json`,
check that every entry in its `exactSourceQuotes` array appears in the
source file after whitespace normalisation. Reports any mismatches.

Usage:

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
      1. Collapse all whitespace runs to a single space.
      2. Strip whitespace adjacent to / and -, since line wraps can split a
         word across these punctuation boundaries (e.g. "pilot/\ndriver" →
         "pilot/driver" once the layout artifact is removed). The model is
         expected to reconstruct the unwrapped form.
    """
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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-root", type=Path, required=True)
    args = parser.parse_args()

    document_dirs = [d for d in args.output_root.iterdir() if d.is_dir()]
    audits = []
    for d in sorted(document_dirs):
        audit = audit_document(d)
        audits.append(audit)
        if "error" in audit:
            print(f"{d.name}: {audit['error']}")
            continue
        print(
            f"{audit['documentId']}: {audit['candidatesAudited']} candidates, "
            f"{audit['totalQuotes']} quotes, {audit['totalMisses']} misses "
            f"({audit['missRate']:.1%})"
        )
        for c in audit["candidatesWithMisses"]:
            for m in c["misses"]:
                excerpt = m["quote"][:120].replace("\n", " ")
                print(f"  MISS — {c['sectionId']}::{c['candidateId']}: \"{excerpt}\"")
    overall_quotes = sum(a.get("totalQuotes", 0) for a in audits)
    overall_misses = sum(a.get("totalMisses", 0) for a in audits)
    print()
    print(f"OVERALL: {overall_quotes} quotes, {overall_misses} misses "
          f"({overall_misses / overall_quotes:.1%} miss rate)" if overall_quotes else "no quotes audited")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
