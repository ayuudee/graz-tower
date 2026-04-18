#!/usr/bin/env python3

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_structured_package as structured


def build_projection_bundle(manifest_path: Path) -> dict[str, Any]:
    package = structured.build_structured_airport_package(manifest_path)
    return {
        "projectionStatus": package["packageStatus"],
        "airportCode": package["airportCode"],
        "airportName": package["airportName"],
        "sourceManifest": package["sourceManifest"],
        "summary": package["summary"],
        "coreEntities": package["directCoreFitEntities"],
        "candidateEntities": package["candidateOperationalStructures"],
        "publicationSupplement": package["publicationSemantics"],
        "projectionGaps": package.get("projectionDiagnostics", {}).get("projectionGaps", []),
    }


def projection_json(bundle: dict[str, Any]) -> str:
    return json.dumps(bundle, indent=2, sort_keys=True) + "\n"
