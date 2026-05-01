#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from ollama_requirement_trial import SYSTEM_PROMPT, build_prompt
from prototype_slice import SLICE_SPECS, build_slice_payload


DEFAULT_ENDPOINT = "http://biggy:11434/api/generate"


@dataclass(frozen=True)
class BenchmarkCase:
    caseId: str
    familyId: str
    sliceId: str
    unitId: str
    expectedUnitKind: str
    expectedAuthorityClassCeiling: str
    notes: str


def load_manifest(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def build_slices(output_dir: Path) -> dict[str, dict[str, Any]]:
    output_dir.mkdir(parents=True, exist_ok=True)
    payloads: dict[str, dict[str, Any]] = {}
    for spec in SLICE_SPECS:
        payload = build_slice_payload(spec)
        payloads[spec.slice_id] = payload
        (output_dir / f"{spec.slice_id}.json").write_text(
            json.dumps(payload, indent=2),
            encoding="utf-8",
        )
    return payloads


def find_unit(payloads: dict[str, dict[str, Any]], case: BenchmarkCase) -> dict[str, Any]:
    payload = payloads[case.sliceId]
    return next(unit for unit in payload["units"] if unit["source_unit_id"] == case.unitId)


def invoke_model(
    model: str,
    unit: dict[str, Any],
    endpoint: str,
    *,
    authority_ceiling: str,
    prompt_variant: str,
) -> dict[str, Any]:
    prompt = build_prompt(
        unit,
        authority_ceiling=authority_ceiling,
        prompt_variant=prompt_variant,
    )
    request_body = {
        "model": model,
        "prompt": prompt,
        "system": SYSTEM_PROMPT,
        "format": "json",
        "stream": False,
        "options": {"temperature": 0},
    }
    req = urllib.request.Request(
        endpoint,
        data=json.dumps(request_body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=180) as response:
        raw = json.loads(response.read().decode("utf-8"))
    return {
        "requestPromptChars": len(prompt),
        "requestSystemChars": len(SYSTEM_PROMPT),
        "authorityCeiling": authority_ceiling,
        "promptVariant": prompt_variant,
        "response": raw,
    }


def extract_structured_payload(raw_call: dict[str, Any]) -> tuple[dict[str, Any] | None, str | None]:
    raw_response = raw_call["response"]
    candidate_text = raw_response.get("response") or raw_response.get("thinking") or ""
    if not candidate_text:
        return None, "empty_response"
    try:
        return json.loads(candidate_text), None
    except json.JSONDecodeError as error:
        return None, f"json_decode_error:{error.msg}"


def write_review_sheet(rows: list[dict[str, Any]], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "caseId",
                "model",
                "familyId",
                "sliceId",
                "unitId",
                "expectedUnitKind",
                "expectedAuthorityClassCeiling",
                "parseOk",
                "candidateCount",
                "requestPromptChars",
                "outcome",
                "authorityOk",
                "structureOk",
                "notes",
            ],
        )
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "caseId": row["caseId"],
                    "model": row["model"],
                    "familyId": row["familyId"],
                    "sliceId": row["sliceId"],
                    "unitId": row["unitId"],
                    "expectedUnitKind": row["expectedUnitKind"],
                    "expectedAuthorityClassCeiling": row["expectedAuthorityClassCeiling"],
                    "parseOk": row["parseOk"],
                    "candidateCount": row["candidateCount"],
                    "requestPromptChars": row["requestPromptChars"],
                    "outcome": "",
                    "authorityOk": "",
                    "structureOk": "",
                    "notes": "",
                }
            )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--models", nargs="+", required=True)
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--case-id", action="append", default=[])
    parser.add_argument(
        "--prompt-variant",
        choices=["default", "family_guarded"],
        default="default",
    )
    args = parser.parse_args()

    manifest = load_manifest(args.manifest)
    cases = [BenchmarkCase(**case) for case in manifest["cases"]]
    if args.case_id:
        selected = set(args.case_id)
        cases = [case for case in cases if case.caseId in selected]

    slice_dir = args.output_dir / "slices"
    results_dir = args.output_dir / "results"
    payloads = build_slices(slice_dir)

    summary_rows: list[dict[str, Any]] = []
    for case in cases:
        unit = find_unit(payloads, case)
        for model in args.models:
            raw = invoke_model(
                model=model,
                unit=unit,
                endpoint=args.endpoint,
                authority_ceiling=case.expectedAuthorityClassCeiling,
                prompt_variant=args.prompt_variant,
            )
            structured, parse_error = extract_structured_payload(raw)
            out_dir = results_dir / model.replace(":", "__")
            out_dir.mkdir(parents=True, exist_ok=True)
            out_path = out_dir / f"{case.caseId}.json"
            payload = {
                "case": asdict(case),
                "model": model,
                "endpoint": args.endpoint,
                "promptVariant": args.prompt_variant,
                "unit": unit,
                "rawCall": raw,
                "structuredResponse": structured,
                "parseError": parse_error,
            }
            out_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
            summary_rows.append(
                {
                    "caseId": case.caseId,
                    "model": model,
                    "familyId": case.familyId,
                    "sliceId": case.sliceId,
                    "unitId": case.unitId,
                    "expectedUnitKind": case.expectedUnitKind,
                    "expectedAuthorityClassCeiling": case.expectedAuthorityClassCeiling,
                    "parseOk": parse_error is None,
                    "candidateCount": len((structured or {}).get("candidates", [])),
                    "requestPromptChars": raw["requestPromptChars"],
                    "promptVariant": args.prompt_variant,
                    "resultPath": str(out_path),
                }
            )

    (args.output_dir / "summary.json").write_text(
        json.dumps(
            {
                "manifest": str(args.manifest),
                "models": args.models,
                "promptVariant": args.prompt_variant,
                "rows": summary_rows,
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    write_review_sheet(summary_rows, args.output_dir / "judgement_template.csv")


if __name__ == "__main__":
    main()
