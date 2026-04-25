#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import time
import urllib.request
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_SOURCE = ROOT / "research/txt/icao4444-extracted.txt"
DEFAULT_BASE_URL = "http://biggy:11434"
DEFAULT_OUTPUT_ROOT = Path("/tmp/icao4444-ollama-first-prototype-runs")

CASES = {
    "readback_family": {
        "caseId": "icao4444_readback_family",
        "documentId": "icao4444-extracted",
        "familyId": "icao4444_readback_family",
        "authorityCeiling": "authoritative_requirement",
        "startLine": 3401,
        "endLine": 3429,
        "notes": "Readback family with parent clause, subordinate list, notes, and following standalone clauses.",
    },
    "transfer_family": {
        "caseId": "icao4444_transfer_family",
        "documentId": "icao4444-extracted",
        "familyId": "icao4444_transfer_family",
        "authorityCeiling": "authoritative_requirement",
        "startLine": 3086,
        "endLine": 3131,
        "notes": "Transfer family with arriving and departing control-transfer clauses, nested conditions, and supporting note.",
    },
}


def utc_stamp() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H-%M-%SZ")


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def read_window(source: Path, *, start_line: int, end_line: int) -> str:
    lines = source.read_text(encoding="utf-8").split("\n")
    window_lines: list[str] = []
    for line_no in range(start_line, end_line + 1):
        if 1 <= line_no <= len(lines):
            text = lines[line_no - 1].replace("\x0c", "").replace("\x07", "")
            window_lines.append(f"{line_no}: {text}")
    return "\n".join(window_lines).strip()


def call_ollama_chat(
    *,
    base_url: str,
    model: str,
    system_prompt: str,
    user_prompt: str,
    temperature: float,
    num_predict: int,
    num_ctx: int,
    timeout_seconds: int,
    disable_thinking: bool = True,
) -> dict[str, Any]:
    url = base_url.rstrip("/") + "/api/chat"
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "stream": False,
        "format": "json",
        "options": {
            "temperature": temperature,
            "num_predict": num_predict,
            "num_ctx": num_ctx,
        },
    }
    if disable_thinking:
        payload["think"] = False
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    start = time.time()
    with urllib.request.urlopen(req, timeout=timeout_seconds) as response:
        raw = json.loads(response.read().decode("utf-8"))
    elapsed_ms = int((time.time() - start) * 1000)
    message = raw.get("message", {})
    content = message.get("content", "")
    parsed = parse_json_payload(content)
    if parsed is None:
        excerpt = content[:1200].replace("\n", "\\n")
        raise SystemExit(f"Model {model} returned invalid JSON content: {excerpt}")
    return {
        "model": model,
        "requestPromptChars": len(user_prompt),
        "requestSystemChars": len(system_prompt),
        "elapsedMs": elapsed_ms,
        "rawResponse": raw,
        "parsed": parsed,
    }


def parse_json_payload(text: str) -> Any | None:
    stripped = text.strip()
    if not stripped:
        return None
    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        pass
    if "```json" in stripped:
        fenced = stripped.split("```json", 1)[1].split("```", 1)[0].strip()
        try:
            return json.loads(fenced)
        except json.JSONDecodeError:
            pass
    first_object = stripped.find("{")
    last_object = stripped.rfind("}")
    if first_object != -1 and last_object != -1 and last_object > first_object:
        candidate = stripped[first_object:last_object + 1]
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            pass
    first_array = stripped.find("[")
    last_array = stripped.rfind("]")
    if first_array != -1 and last_array != -1 and last_array > first_array:
        candidate = stripped[first_array:last_array + 1]
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            pass
    return None


def require_fields(payload: dict[str, Any], fields: list[str], *, stage: str) -> None:
    missing = [field for field in fields if field not in payload]
    if missing:
        nested_matches = [
            value
            for value in payload.values()
            if isinstance(value, dict) and all(field in value for field in fields)
        ]
        if len(nested_matches) == 1:
            payload.clear()
            payload.update(nested_matches[0])
            return
    if missing:
        raise SystemExit(f"{stage} missing required fields: {missing}")


def normalize_judge_payload(payload: dict[str, Any], *, window: dict[str, Any], candidate: dict[str, Any]) -> None:
    if "caseId" not in payload:
        payload["caseId"] = window["caseId"]
    if "candidateId" not in payload:
        payload["candidateId"] = candidate["candidateId"]
    if "rationale" not in payload and isinstance(payload.get("challenge"), str):
        payload["rationale"] = payload["challenge"]


def build_source_window_payload(source: Path, case: dict[str, Any]) -> dict[str, Any]:
    return {
        "caseId": case["caseId"],
        "documentId": case["documentId"],
        "familyId": case["familyId"],
        "authorityCeiling": case["authorityCeiling"],
        "sourcePath": str(source),
        "sourceRef": f"{source}:{case['startLine']}-{case['endLine']}",
        "notes": case["notes"],
        "windowText": read_window(
            source,
            start_line=case["startLine"],
            end_line=case["endLine"],
        ),
    }


def structure_prompts(window: dict[str, Any]) -> tuple[str, str]:
    system = (
        "You are interpreting a narrow ICAO Doc 4444 source window. "
        "Your job is to propose structure from the source itself. "
        "Do not invent items not present in the source. "
        "Return strict JSON only."
    )
    user = f"""
Propose the structure of this source window.

Rules:
- Work only from the supplied source window.
- Identify parent clauses, subordinate list items, notes, and standalone clauses.
- If a clause introduces subordinate items, say so explicitly.
- If structure is unclear, say so rather than flattening it.
- Keep authority at or below {window["authorityCeiling"]}.

Source window:
{json.dumps(window, indent=2)}

Return JSON with exactly this shape:
{{
  "caseId": "{window["caseId"]}",
  "assessment": {{
    "overallShape": "clean_clause_family | mixed_clause_family | unclear",
    "requiresBundle": true,
    "requiresSplit": true,
    "notes": ["short note"]
  }},
  "structureItems": [
    {{
      "itemId": "stable short id",
      "label": "source label like 4.5.7.5.1 or a) or Note.—",
      "kind": "parent_clause | standalone_clause | list_item | note",
      "parentItemId": "item id or null",
      "sourceStartLine": 3405,
      "sourceEndLine": 3406,
      "text": "exact paraphrase-free source text for this item"
    }}
  ],
  "relationships": [
    {{
      "fromItemId": "item id",
      "relation": "introduces | supports | follows",
      "toItemId": "item id"
    }}
  ]
}}
""".strip()
    return system, user


def reconcile_structure_prompts(
    window: dict[str, Any],
    structure_attempts: list[dict[str, Any]],
) -> tuple[str, str]:
    system = (
        "You are reconciling multiple independent structure interpretations of the same ICAO Doc 4444 source window. "
        "Your job is to produce the most accurate source-grounded structure. "
        "Prefer preserving nested structure over flattening. "
        "Return strict JSON only."
    )
    attempts_payload = [
        {
            "attemptId": attempt["attemptId"],
            "assessment": attempt["parsed"]["assessment"],
            "structureItems": attempt["parsed"]["structureItems"],
            "relationships": attempt["parsed"]["relationships"],
        }
        for attempt in structure_attempts
    ]
    user = f"""
Reconcile these independent structure attempts into one final source structure.

Rules:
- Work only from the supplied source window and attempt outputs.
- Preserve every numbered, lettered, and sub-numbered item that is visible in the source.
- Do not merge sibling list items into one item.
- Do not merge nested sub-items into their parent when they can be represented separately.
- Notes must remain notes.
- If attempts disagree, choose the structure that best preserves the source hierarchy and exact source text.
- It is acceptable to mark the final shape as requiring split or bundle treatment.

Source window:
{json.dumps(window, indent=2)}

Structure attempts:
{json.dumps(attempts_payload, indent=2)}

Return JSON with exactly this shape:
{{
  "caseId": "{window["caseId"]}",
  "assessment": {{
    "overallShape": "clean_clause_family | mixed_clause_family | unclear",
    "requiresBundle": true,
    "requiresSplit": true,
    "notes": ["short note"]
  }},
  "structureItems": [
    {{
      "itemId": "stable short id",
      "label": "source label like 4.5.7.5.1 or a) or 1) or Note.—",
      "kind": "parent_clause | subordinate_clause | standalone_clause | list_item | nested_list_item | note",
      "parentItemId": "item id or null",
      "sourceStartLine": 3405,
      "sourceEndLine": 3406,
      "text": "exact paraphrase-free source text for this item"
    }}
  ],
  "relationships": [
    {{
      "fromItemId": "item id",
      "relation": "introduces | supports | follows",
      "toItemId": "item id"
    }}
  ],
  "reconciliationNotes": ["short note"]
}}
""".strip()
    return system, user


def requirement_prompts(window: dict[str, Any], structure: dict[str, Any]) -> tuple[str, str]:
    system = (
        "You are deriving requirement candidates from a narrow ICAO Doc 4444 source window. "
        "Use the proposed structure, but stay grounded in the source. "
        "Prefer conservative candidates over broad or invented ones. "
        "Accuracy matters more than coverage. "
        "It is better to emit fewer candidates than to over-promote source material. "
        "Return strict JSON only."
    )
    user = f"""
Derive requirement candidates from this source window and proposed structure.

Rules:
- Keep authority at or below {window["authorityCeiling"]}.
- If the parent clause needs subordinate context, do not promote it alone.
- If a clause contains multiple independent obligations, split them.
- Prefer fewer, stronger candidates over many weak ones.
- Do not emit a standalone candidate for a parent clause whose operative meaning depends on a subordinate list introduced by that parent.
- Notes, examples, and background text must not become authoritative requirements.
- Notes may become operational guidance only when they clearly state a reusable operational or phraseology convention from the source itself.
- If a note/example/background item is emitted, default it to `review_only` unless the source itself clearly justifies stronger downstream use.
- If an item is merely explanatory or referential, mark it as support-only rather than a promotable candidate.
- Quote source exactly; do not paraphrase beyond what is needed for a concise claim.

Source window:
{json.dumps(window, indent=2)}

Proposed structure:
{json.dumps(structure, indent=2)}

Return JSON with exactly this shape:
{{
  "caseId": "{window["caseId"]}",
  "candidates": [
    {{
      "candidateId": "stable short id",
      "sourceItemIds": ["item ids"],
      "claimText": "single requirement claim",
      "authorityClass": "authoritative_requirement | operational_guidance | best_practice | background_support",
      "modality": "shall | should | may | note | example",
      "requirementKind": "rule | workflow_constraint | phraseology_rule | definition | best_practice",
      "testability": "sim_executable | partially_executable | review_only",
      "verificationMode": "deterministic_test | property_or_family_test | exploratory_probe | review_only",
      "promotionHint": "promote | advisory_only | support_only",
      "exactSourceQuotes": ["short exact source phrase"],
      "rationale": "short reason"
    }}
  ]
}}
""".strip()
    return system, user


def reconcile_prompts(
    window: dict[str, Any],
    structure: dict[str, Any],
    extraction_attempts: list[dict[str, Any]],
) -> tuple[str, str]:
    system = (
        "You are reconciling multiple independent requirement-extraction attempts from the same ICAO Doc 4444 source window. "
        "Produce the most accurate conservative candidate set. "
        "Prefer correctness over recall. "
        "Return strict JSON only."
    )
    attempts_payload = [
        {
            "attemptId": attempt["attemptId"],
            "candidates": attempt["parsed"]["candidates"],
        }
        for attempt in extraction_attempts
    ]
    user = f"""
Reconcile these independent extraction attempts into one conservative candidate set.

Rules:
- Work only from the supplied source window, structure, and attempt outputs.
- Prefer candidates that are semantically consistent across attempts.
- A singleton candidate may survive if it is clearly source-grounded and structurally independent.
- If attempts disagree on a parent/list candidate, prefer preserving that disagreement as `needs_bundle` or `needs_split` later rather than flattening early.
- Notes/background may survive only as `advisory_only` or `support_only`.
- Do not emit duplicate candidates that express the same obligation at different granularities unless the source clearly supports both.

Source window:
{json.dumps(window, indent=2)}

Proposed structure:
{json.dumps(structure, indent=2)}

Extraction attempts:
{json.dumps(attempts_payload, indent=2)}

Return JSON with exactly this shape:
{{
  "caseId": "{window["caseId"]}",
  "notes": ["short note"],
  "candidates": [
    {{
      "candidateId": "stable short id",
      "sourceItemIds": ["item ids"],
      "claimText": "single requirement claim",
      "authorityClass": "authoritative_requirement | operational_guidance | best_practice | background_support",
      "modality": "shall | should | may | note | example",
      "requirementKind": "rule | workflow_constraint | phraseology_rule | definition | best_practice",
      "testability": "sim_executable | partially_executable | review_only",
      "verificationMode": "deterministic_test | property_or_family_test | exploratory_probe | review_only",
      "promotionHint": "promote | advisory_only | support_only",
      "exactSourceQuotes": ["short exact source phrase"],
      "supportingAttempts": ["attempt ids"],
      "consistencyClass": "consistent | partial | singleton",
      "rationale": "short reason"
    }}
  ]
}}
""".strip()
    return system, user


def challenge_prompts(window: dict[str, Any], structure: dict[str, Any], candidate: dict[str, Any]) -> tuple[str, str]:
    system = (
        "You are the challenger in a source-grounded requirement review. "
        "Try to falsify the candidate from the supplied source. "
        "Authority verdicts are directional: `authority_too_high` means the candidate claims "
        "more authority than the source supports (e.g. labels a `should` clause or a Note as `authoritative_requirement`); "
        "`authority_too_low` means the candidate underclaims (e.g. labels a `shall` clause as `operational_guidance`). "
        "Direction sanity check: if your concern is that the candidate should sit at a LOWER authority class, the verdict is `authority_too_high`. "
        "If your concern is that the candidate should sit at a HIGHER authority class, the verdict is `authority_too_low`. "
        "The verdict's direction and the concern text must agree. "
        "Modality reasoning must be grounded in the candidate's own `sourceItemIds` only. "
        "Read each cited item's `text` from the structure and identify its modality marker (`shall`, `should`, `may`, `note`, `example`, or `none` if there is no marker in that item). "
        "Do not import modality from other items in the window. "
        "The window's `authorityCeiling` is an upper bound for the family, not the modality of any specific clause. "
        "Every `sourceQuote` you return must be exact text drawn from one of the candidate's cited items. "
        "ICAO convention on Notes: a `Note.—` paragraph is non-normative explanatory text. "
        "It is correctly classified as `operational_guidance` or `background_support` with `advisory_only` or `support_only` promotion. "
        "Do not raise `authority_too_low` against a candidate that classifies a Note as advisory, unless the Note's own text contains a normative `shall` verb. "
        "Structural concerns (the candidate bundles items that should be split, or omits items it depends on) are NOT authority verdicts; "
        "use `wrong_split`, `overbroad`, or `underspecified` for those. Reserve `authority_too_high` / `authority_too_low` for cases where the candidate's `authorityClass` "
        "or `modality` field disagrees with the modality of its own cited items. "
        "Be strict and concise. "
        "Return strict JSON only."
    )
    user = f"""
Challenge this candidate using only the supplied source window and structure.

Procedure:
1. Read the candidate's `sourceItemIds`.
2. For each id, find that item in the structure and read its `text`.
3. Identify the modality marker in that text (`shall`, `should`, `may`, `note`, `example`, or `none`).
4. Combine those into an `effectiveModality` for the candidate as a whole. If the cited items use mixed modalities, return `mixed` and explain in `concerns`.
5. Only after that, decide the verdict. Authority verdicts must be consistent with the effective modality you just declared.
6. Every quote in `sourceQuotes` must be exact text from one of the cited items.

Source window:
{json.dumps(window, indent=2)}

Proposed structure:
{json.dumps(structure, indent=2)}

Candidate:
{json.dumps(candidate, indent=2)}

Return JSON with exactly this shape:
{{
  "caseId": "{window["caseId"]}",
  "candidateId": "{candidate["candidateId"]}",
  "candidateSourceItemModalities": [
    {{ "itemId": "id from candidate.sourceItemIds", "modality": "shall | should | may | note | example | none" }}
  ],
  "effectiveModality": "shall | should | may | note | example | mixed",
  "verdict": "supported | overbroad | underspecified | authority_too_high | authority_too_low | wrong_split | unsupported_by_source",
  "concerns": ["short concern"],
  "sourceQuotes": ["short exact phrase, drawn only from the candidate's cited items"]
}}
""".strip()
    return system, user


def defense_prompts(window: dict[str, Any], structure: dict[str, Any], candidate: dict[str, Any]) -> tuple[str, str]:
    system = (
        "You are the defender in a source-grounded requirement review. "
        "Justify the candidate strictly from the supplied source. "
        "Be strict and concise. "
        "Return strict JSON only."
    )
    user = f"""
Defend this candidate using only the supplied source window and structure.

Source window:
{json.dumps(window, indent=2)}

Proposed structure:
{json.dumps(structure, indent=2)}

Candidate:
{json.dumps(candidate, indent=2)}

Return JSON with exactly this shape:
{{
  "caseId": "{window["caseId"]}",
  "candidateId": "{candidate["candidateId"]}",
  "verdict": "defensible | weak | unsupported",
  "supports": ["short support point"],
  "sourceQuotes": ["short exact supporting phrase"]
}}
""".strip()
    return system, user


def bundle_gate_prompts(window: dict[str, Any], structure: dict[str, Any], candidate: dict[str, Any]) -> tuple[str, str]:
    system = (
        "You are the bundle-gate reviewer in a source-grounded requirement review. "
        "Your only job is to decide whether the candidate's scope is structurally complete: "
        "does the parent or governing clause's operative meaning require subordinate items "
        "that the candidate does not include? "
        "Read each item the candidate cites in `sourceItemIds`. "
        "Then look in the structure for items whose `parentItemId` points at any of those cited items. "
        "If those subordinate items carry the operative timing, conditions, or qualifications of the parent, "
        "and the candidate does not include them, the scope is incomplete. "
        "If the candidate already includes the operatively necessary subordinates, the scope is complete. "
        "If the candidate is a single self-contained clause with no dependent subordinates, the scope is complete. "
        "Return strict JSON only."
    )
    user = f"""
Decide structural completeness for this candidate.

Source window:
{json.dumps(window, indent=2)}

Proposed structure:
{json.dumps(structure, indent=2)}

Candidate:
{json.dumps(candidate, indent=2)}

Return JSON with exactly this shape:
{{
  "caseId": "{window["caseId"]}",
  "candidateId": "{candidate["candidateId"]}",
  "scopeComplete": true,
  "missingDependencies": ["item ids that are operatively required but not in the candidate's sourceItemIds"],
  "rationale": "short rationale citing item ids"
}}
""".strip()
    return system, user


def judge_prompts(
    window: dict[str, Any],
    structure: dict[str, Any],
    candidate: dict[str, Any],
    challenge: dict[str, Any],
    defense: dict[str, Any],
    bundle_gate: dict[str, Any],
) -> tuple[str, str]:
    system = (
        "You are the judge in a source-grounded requirement review. "
        "Decide the outcome using only the supplied source window, structure, candidate, challenge, defense, and bundle gate. "
        "Return strict JSON only."
    )
    user = f"""
Judge this candidate.

Rules:
- Prefer conservative outcomes.
- Allowed decisions: accepted, needs_split, needs_bundle, advisory_only, ambiguous, unsupported_by_source, needs_human_review.
- Do not promote beyond the source.
- If `promotionHint` is `advisory_only` or `support_only`, do not return `accepted`.
- If a note/example/background item is source-grounded but non-normative, prefer `advisory_only` over `accepted`.
- Use the bundle gate's `scopeComplete` to decide bundle/split mechanically:
  - if `scopeComplete` is true and the candidate's content is coherent, do NOT return `needs_bundle`.
  - if `scopeComplete` is false, prefer `needs_bundle` (the candidate is missing operatively required subordinates listed in `missingDependencies`).
  - structural fragmentation across multiple separable obligations is `needs_split` regardless of `scopeComplete`.

Source window:
{json.dumps(window, indent=2)}

Proposed structure:
{json.dumps(structure, indent=2)}

Candidate:
{json.dumps(candidate, indent=2)}

Bundle gate:
{json.dumps(bundle_gate, indent=2)}

Challenge:
{json.dumps(challenge, indent=2)}

Defense:
{json.dumps(defense, indent=2)}

Return JSON with exactly this shape:
{{
  "caseId": "{window["caseId"]}",
  "candidateId": "{candidate["candidateId"]}",
  "decision": "accepted | needs_split | needs_bundle | advisory_only | ambiguous | unsupported_by_source | needs_human_review",
  "confidence": "low | medium | high",
  "rationale": "short rationale",
  "notes": ["short note"]
}}
""".strip()
    return system, user


def render_summary(
    *,
    window: dict[str, Any],
    structure_result: dict[str, Any],
    structure_attempt_count: int,
    extraction_attempt_count: int,
    requirement_result: dict[str, Any],
    judged_candidates: list[dict[str, Any]],
    max_candidates: int,
) -> str:
    accepted = [item for item in judged_candidates if item["judge"]["decision"] == "accepted"]
    lines = [
        f"# Ollama-First Prototype Summary",
        "",
        f"- case: `{window['caseId']}`",
        f"- sourceRef: `{window['sourceRef']}`",
        f"- structureModel: `{structure_result['model']}`",
        f"- structureAttempts: `{structure_attempt_count}`",
        f"- extractionAttempts: `{extraction_attempt_count}`",
        f"- extractionModel: `{requirement_result['model']}`",
        f"- candidateCount: `{len(requirement_result['parsed']['candidates'])}`",
        f"- judgedCandidateCount: `{len(judged_candidates)}` of `{min(len(requirement_result['parsed']['candidates']), max_candidates)}` requested",
        f"- acceptedCount: `{len(accepted)}`",
        "",
        "## Structure",
        f"- overallShape: `{structure_result['parsed']['assessment']['overallShape']}`",
        f"- requiresBundle: `{structure_result['parsed']['assessment']['requiresBundle']}`",
        f"- requiresSplit: `{structure_result['parsed']['assessment']['requiresSplit']}`",
        f"- structureItemCount: `{len(structure_result['parsed']['structureItems'])}`",
        "",
        "## Judge Decisions",
    ]
    for item in judged_candidates:
        lines.append(
            f"- `{item['candidate']['candidateId']}`: `{item['judge']['decision']}` ({item['judge']['confidence']})"
        )
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--case", choices=sorted(CASES.keys()), default="readback_family")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--structure-model", default="qwen3.6:35b-a3b")
    parser.add_argument("--structure-reconcile-model", default="qwen3.6:35b-a3b")
    parser.add_argument("--extraction-model", default="qwen3.6:35b-a3b")
    parser.add_argument("--reconcile-model", default="qwen3.6:35b-a3b")
    parser.add_argument("--challenge-model", default="qwen2.5-coder:32b")
    parser.add_argument("--defense-model", default="qwen3.6:35b-a3b")
    parser.add_argument("--bundle-gate-model", default="qwen3.6:35b-a3b")
    parser.add_argument("--judge-model", default="qwen3.6:35b-a3b")
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--num-ctx", type=int, default=12288)
    parser.add_argument("--max-candidates", type=int, default=8)
    parser.add_argument("--structure-attempts", type=int, default=3)
    parser.add_argument("--extraction-attempts", type=int, default=3)
    args = parser.parse_args()

    output_dir = args.output_dir or (DEFAULT_OUTPUT_ROOT / utc_stamp())
    output_dir.mkdir(parents=True, exist_ok=True)

    case = CASES[args.case]
    window = build_source_window_payload(args.source, case)
    write_json(output_dir / "source_window.json", window)

    structure_system, structure_user = structure_prompts(window)
    structure_attempts: list[dict[str, Any]] = []
    for attempt_no in range(1, args.structure_attempts + 1):
        attempt_result = call_ollama_chat(
            base_url=args.base_url,
            model=args.structure_model,
            system_prompt=structure_system,
            user_prompt=structure_user + f"\n\nIndependent structure attempt id: structure_attempt_{attempt_no}.",
            temperature=0.2,
            num_predict=5000,
            num_ctx=args.num_ctx,
            timeout_seconds=300,
        )
        attempt_result["attemptId"] = f"structure_attempt_{attempt_no}"
        structure_attempts.append(attempt_result)
        write_json(output_dir / "structure_attempts" / f"attempt_{attempt_no}.json", attempt_result)
        require_fields(
            attempt_result["parsed"],
            ["caseId", "assessment", "structureItems", "relationships"],
            stage=f"structure:attempt_{attempt_no}",
        )

    structure_reconcile_system, structure_reconcile_user = reconcile_structure_prompts(window, structure_attempts)
    structure_result = call_ollama_chat(
        base_url=args.base_url,
        model=args.structure_reconcile_model,
        system_prompt=structure_reconcile_system,
        user_prompt=structure_reconcile_user,
        temperature=0.0,
        num_predict=6000,
        num_ctx=args.num_ctx,
        timeout_seconds=300,
    )
    write_json(output_dir / "structure_reconciliation_response.json", structure_result)
    write_json(output_dir / "structure_response.json", structure_result)
    require_fields(structure_result["parsed"], ["caseId", "assessment", "structureItems", "relationships"], stage="structure_reconcile")

    requirement_system, requirement_user = requirement_prompts(window, structure_result["parsed"])
    extraction_attempts: list[dict[str, Any]] = []
    for attempt_no in range(1, args.extraction_attempts + 1):
        attempt_result = call_ollama_chat(
            base_url=args.base_url,
            model=args.extraction_model,
            system_prompt=requirement_system,
            user_prompt=requirement_user + f"\n\nIndependent attempt id: attempt_{attempt_no}.",
            temperature=0.2,
            num_predict=3500,
            num_ctx=args.num_ctx,
            timeout_seconds=300,
        )
        attempt_result["attemptId"] = f"attempt_{attempt_no}"
        extraction_attempts.append(attempt_result)
        write_json(output_dir / "extraction_attempts" / f"attempt_{attempt_no}.json", attempt_result)
        require_fields(attempt_result["parsed"], ["caseId", "candidates"], stage=f"requirements:attempt_{attempt_no}")

    reconcile_system, reconcile_user = reconcile_prompts(window, structure_result["parsed"], extraction_attempts)
    requirement_result = call_ollama_chat(
        base_url=args.base_url,
        model=args.reconcile_model,
        system_prompt=reconcile_system,
        user_prompt=reconcile_user,
        temperature=0.0,
        num_predict=3500,
        num_ctx=args.num_ctx,
        timeout_seconds=300,
    )
    write_json(output_dir / "reconciliation_response.json", requirement_result)
    write_json(output_dir / "requirement_response.json", requirement_result)
    require_fields(requirement_result["parsed"], ["caseId", "candidates"], stage="reconcile")

    judged_candidates: list[dict[str, Any]] = []
    for candidate in requirement_result["parsed"]["candidates"][: args.max_candidates]:
        require_fields(
            candidate,
            [
                "candidateId",
                "sourceItemIds",
                "claimText",
                "authorityClass",
                "modality",
                "requirementKind",
                "testability",
                "verificationMode",
                "promotionHint",
                "exactSourceQuotes",
                "supportingAttempts",
                "consistencyClass",
                "rationale",
            ],
            stage=f"candidate:{candidate.get('candidateId', '<unknown>')}",
        )

        challenge_system, challenge_user = challenge_prompts(window, structure_result["parsed"], candidate)
        challenge_result = call_ollama_chat(
            base_url=args.base_url,
            model=args.challenge_model,
            system_prompt=challenge_system,
            user_prompt=challenge_user,
            temperature=0.1,
            num_predict=900,
            num_ctx=args.num_ctx,
            timeout_seconds=180,
        )
        write_json(output_dir / "challenge" / f"{candidate['candidateId']}.json", challenge_result)
        require_fields(
            challenge_result["parsed"],
            [
                "caseId",
                "candidateId",
                "candidateSourceItemModalities",
                "effectiveModality",
                "verdict",
                "concerns",
                "sourceQuotes",
            ],
            stage=f"challenge:{candidate['candidateId']}",
        )

        defense_system, defense_user = defense_prompts(window, structure_result["parsed"], candidate)
        defense_result = call_ollama_chat(
            base_url=args.base_url,
            model=args.defense_model,
            system_prompt=defense_system,
            user_prompt=defense_user,
            temperature=0.1,
            num_predict=900,
            num_ctx=args.num_ctx,
            timeout_seconds=180,
        )
        write_json(output_dir / "defense" / f"{candidate['candidateId']}.json", defense_result)
        require_fields(defense_result["parsed"], ["caseId", "candidateId", "verdict", "supports", "sourceQuotes"], stage=f"defense:{candidate['candidateId']}")

        bundle_gate_system, bundle_gate_user = bundle_gate_prompts(window, structure_result["parsed"], candidate)
        bundle_gate_result = call_ollama_chat(
            base_url=args.base_url,
            model=args.bundle_gate_model,
            system_prompt=bundle_gate_system,
            user_prompt=bundle_gate_user,
            temperature=0.0,
            num_predict=600,
            num_ctx=args.num_ctx,
            timeout_seconds=180,
        )
        write_json(output_dir / "bundle_gate" / f"{candidate['candidateId']}.json", bundle_gate_result)
        require_fields(
            bundle_gate_result["parsed"],
            ["caseId", "candidateId", "scopeComplete", "missingDependencies", "rationale"],
            stage=f"bundle_gate:{candidate['candidateId']}",
        )

        judge_system, judge_user = judge_prompts(
            window,
            structure_result["parsed"],
            candidate,
            challenge_result["parsed"],
            defense_result["parsed"],
            bundle_gate_result["parsed"],
        )
        judge_result = call_ollama_chat(
            base_url=args.base_url,
            model=args.judge_model,
            system_prompt=judge_system,
            user_prompt=judge_user,
            temperature=0.0,
            num_predict=2000,
            num_ctx=args.num_ctx,
            timeout_seconds=300,
        )
        normalize_judge_payload(judge_result["parsed"], window=window, candidate=candidate)
        write_json(output_dir / "judge" / f"{candidate['candidateId']}.json", judge_result)
        require_fields(judge_result["parsed"], ["caseId", "candidateId", "decision", "confidence", "rationale", "notes"], stage=f"judge:{candidate['candidateId']}")

        judged_candidates.append(
            {
                "candidate": candidate,
                "challenge": challenge_result["parsed"],
                "defense": defense_result["parsed"],
                "bundleGate": bundle_gate_result["parsed"],
                "judge": judge_result["parsed"],
            }
        )

    manifest = {
        "caseId": window["caseId"],
        "sourceRef": window["sourceRef"],
        "sourcePath": window["sourcePath"],
        "structureModel": args.structure_model,
        "structureReconcileModel": args.structure_reconcile_model,
        "extractionModel": args.extraction_model,
        "reconcileModel": args.reconcile_model,
        "challengeModel": args.challenge_model,
        "defenseModel": args.defense_model,
        "bundleGateModel": args.bundle_gate_model,
        "judgeModel": args.judge_model,
        "numCtx": args.num_ctx,
        "maxCandidates": args.max_candidates,
        "structureAttempts": args.structure_attempts,
        "extractionAttempts": args.extraction_attempts,
        "artifactPaths": {
            "sourceWindow": str(output_dir / "source_window.json"),
            "structureAttemptsDir": str(output_dir / "structure_attempts"),
            "structureReconciliationResponse": str(output_dir / "structure_reconciliation_response.json"),
            "structureResponse": str(output_dir / "structure_response.json"),
            "extractionAttemptsDir": str(output_dir / "extraction_attempts"),
            "reconciliationResponse": str(output_dir / "reconciliation_response.json"),
            "requirementResponse": str(output_dir / "requirement_response.json"),
            "challengeDir": str(output_dir / "challenge"),
            "defenseDir": str(output_dir / "defense"),
            "bundleGateDir": str(output_dir / "bundle_gate"),
            "judgeDir": str(output_dir / "judge"),
            "summary": str(output_dir / "summary.md"),
        },
        "structurePromptChars": structure_result["requestPromptChars"],
        "requirementPromptChars": requirement_result["requestPromptChars"],
        "candidateCount": len(requirement_result["parsed"]["candidates"]),
        "judgedCandidateCount": len(judged_candidates),
        "judgeOutcomes": [
            {
                "candidateId": item["candidate"]["candidateId"],
                "decision": item["judge"]["decision"],
                "confidence": item["judge"]["confidence"],
            }
            for item in judged_candidates
        ],
    }
    write_json(output_dir / "run_manifest.json", manifest)
    write_json(output_dir / "judged_candidates.json", judged_candidates)
    write_text(
        output_dir / "summary.md",
        render_summary(
            window=window,
            structure_result=structure_result,
            structure_attempt_count=args.structure_attempts,
            extraction_attempt_count=args.extraction_attempts,
            requirement_result=requirement_result,
            judged_candidates=judged_candidates,
            max_candidates=args.max_candidates,
        ),
    )
    print(json.dumps({"runDir": str(output_dir), "candidateCount": manifest["candidateCount"]}, indent=2))


if __name__ == "__main__":
    main()
