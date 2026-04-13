#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

usage() {
    echo "Usage: coe <atc|law|phrase>" >&2
    echo "" >&2
    echo "Reads input from stdin, analyses it with the specified agent," >&2
    echo "and writes structured JSON findings to stdout." >&2
    echo "" >&2
    echo "Examples:" >&2
    echo "  echo 'What is the minimum radar separation?' | coe law" >&2
    echo "  cat transcript.txt | coe phrase" >&2
    echo "  coe atc < question.txt" >&2
    exit 1
}

[[ $# -eq 1 ]] || usage

case "$1" in
    law)    agent="atc-law" ;;
    phrase) agent="atc-phraseology" ;;
    atc)    agent="atc-general" ;;
    *)      echo "Unknown agent: $1" >&2; usage ;;
esac

schema='{
  "type": "object",
  "properties": {
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "element":     { "type": "string" },
          "verdict":     { "type": "string", "enum": ["CORRECT","INCORRECT","AMBIGUOUS","NON_STANDARD","ACCEPTABLE","PARTIAL","MISSING","NOT_COVERED"] },
          "citation":    { "type": "string" },
          "explanation": { "type": "string" }
        },
        "required": ["element", "verdict", "citation", "explanation"]
      }
    },
    "summary": { "type": "string" }
  },
  "required": ["findings", "summary"]
}'

input="$(cat)"

cd "$PROJECT_DIR"
claude \
    --print \
    --agent "$agent" \
    --output-format json \
    --json-schema "$schema" \
    --permission-mode bypassPermissions \
    -p "Analyse the following and return structured findings as JSON.

INPUT:
$input" \
    | jq '.structured_output'
