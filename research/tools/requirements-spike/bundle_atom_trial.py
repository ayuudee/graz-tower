#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import urllib.request
from pathlib import Path


DEFAULT_ENDPOINT = "http://biggy:11434/api/generate"


SYSTEM_PROMPT = """You are extracting requirement atoms from a deterministic ATC requirement bundle.
Work only from the supplied bundle members and metadata.
Do not invent authority, scope, or obligations that are not present.
Preserve parent/child structure. If the parent clause only introduces subordinate items, do not promote the parent as a standalone atom.
Return strict JSON only."""


def build_prompt(bundle: dict) -> str:
    return f"""
Extract promotable requirement atoms from this deterministic bundle.

Rules:
- Work only from the bundle members.
- Keep the bundle authority ceiling.
- If the primary clause is only an introduction to subordinate items, prefer no standalone atom for it.
- If subordinate items are the real obligations or required items, represent them as atoms with dependencies on the parent bundle.
- Supporting notes remain support unless they add a clearly self-contained obligation.

Bundle:
{json.dumps(bundle, indent=2)}

Return JSON with this shape:
{{
  "assessment": {{
    "bundleKind": "<copied>",
    "atomizable": true,
    "reason": "<short reason>"
  }},
  "atoms": [
    {{
      "claimText": "...",
      "authorityClass": "authoritative_requirement|operational_guidance|best_practice|background_support",
      "requirementKind": "rule|phraseology_rule|workflow_constraint|definition|best_practice|background_constraint",
      "sourceUnitIds": ["..."],
      "dependsOnSourceUnitIds": ["..."],
      "actors": ["..."],
      "requiredBehaviour": ["..."],
      "applicability": ["..."],
      "ambiguities": ["..."],
      "sourceSupport": ["exact short supporting phrases copied from the bundle members"]
    }}
  ]
}}
""".strip()


def load_bundle(path: Path, bundle_id: str) -> dict:
    payload = json.loads(path.read_text(encoding="utf-8"))
    return next(bundle for bundle in payload["bundles"] if bundle["bundleId"] == bundle_id)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--bundle-id", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    args = parser.parse_args()

    bundle = load_bundle(args.input, args.bundle_id)
    prompt = build_prompt(bundle)
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
    with urllib.request.urlopen(req, timeout=180) as response:
        raw = json.loads(response.read().decode("utf-8"))

    structured = None
    parse_error = None
    candidate_text = raw.get("response") or raw.get("thinking") or ""
    if candidate_text:
        try:
            structured = json.loads(candidate_text)
        except json.JSONDecodeError as error:
            parse_error = f"json_decode_error:{error.msg}"
    else:
        parse_error = "empty_response"

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(
            {
                "bundleId": args.bundle_id,
                "model": args.model,
                "endpoint": args.endpoint,
                "requestPromptChars": len(prompt),
                "rawResponse": raw,
                "structuredResponse": structured,
                "parseError": parse_error,
            },
            indent=2,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
