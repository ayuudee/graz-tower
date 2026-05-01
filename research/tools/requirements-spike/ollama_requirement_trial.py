#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import urllib.request
from pathlib import Path


DEFAULT_ENDPOINT = "http://biggy:11434/api/generate"


SYSTEM_PROMPT = """You are extracting requirement candidates from a normalized ATC source unit.
Work only from the supplied source unit text and metadata.
Do not invent authority, scope, or obligations that are not present.
If the unit is example-heavy, say so and prefer no candidates over fabricated ones.
Return strict JSON only."""


def build_prompt(
    unit: dict,
    *,
    authority_ceiling: str | None = None,
    prompt_variant: str = "default",
) -> str:
    guardrail_block = ""
    if prompt_variant == "family_guarded":
        guardrail_lines = [
            "Additional guardrails:",
            "- Do not assign a stronger authorityClass than the allowed ceiling.",
            "- If the unit is a parent clause that introduces a following list or subordinate conditions then prefer no standalone candidate.",
            "- If the unit is background explanation, example material, or heading-like text then prefer no candidates unless a clearly self-contained rule is present.",
            "- If the unit contains multiple independent obligations then split them into separate candidates.",
        ]
        if authority_ceiling is not None:
            guardrail_lines.insert(1, f"- Maximum allowed authorityClass for this unit: {authority_ceiling}.")
        guardrail_block = "\n" + "\n".join(guardrail_lines)
    elif authority_ceiling is not None:
        guardrail_block = (
            "\nAdditional guardrails:\n"
            f"- Maximum allowed authorityClass for this unit: {authority_ceiling}."
        )

    return f"""
Extract requirement candidates from this single source unit.

Source unit:
{json.dumps(unit, indent=2)}
{guardrail_block}

Return JSON with this shape:
{{
  "assessment": {{
    "unitKind": "<copied or inferred>",
    "extractable": true,
    "reason": "<short reason>"
  }},
  "candidates": [
    {{
      "claimText": "...",
      "authorityClass": "authoritative_requirement|operational_guidance|best_practice|background_support",
      "requirementKind": "rule|phraseology_rule|workflow_constraint|definition|best_practice|background_constraint",
      "actors": ["..."],
      "requiredBehaviour": ["..."],
      "forbiddenOutcomes": ["..."],
      "applicability": ["..."],
      "ambiguities": ["..."],
      "sourceSupport": ["exact short supporting phrases copied from the unit"]
    }}
  ]
}}
""".strip()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--unit-id", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--authority-ceiling")
    parser.add_argument(
        "--prompt-variant",
        choices=["default", "family_guarded"],
        default="default",
    )
    args = parser.parse_args()

    payload = json.loads(args.input.read_text(encoding="utf-8"))
    unit = next(unit for unit in payload["units"] if unit["source_unit_id"] == args.unit_id)
    prompt = build_prompt(
        unit,
        authority_ceiling=args.authority_ceiling,
        prompt_variant=args.prompt_variant,
    )

    request_body = {
        "model": args.model,
        "prompt": prompt,
        "system": SYSTEM_PROMPT,
        "format": "json",
        "stream": False,
        "options": {"temperature": 0},
    }

    req = urllib.request.Request(
        args.endpoint,
        data=json.dumps(request_body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as response:
        raw = json.loads(response.read().decode("utf-8"))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(
            {
                "model": args.model,
                "endpoint": args.endpoint,
                "input": str(args.input),
                "unitId": args.unit_id,
                "promptVariant": args.prompt_variant,
                "authorityCeiling": args.authority_ceiling,
                "requestPromptChars": len(prompt),
                "rawResponse": raw,
            },
            indent=2,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
