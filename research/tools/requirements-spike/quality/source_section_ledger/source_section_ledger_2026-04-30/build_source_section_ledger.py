#!/usr/bin/env python3
"""Build the 2026-04-30 source-section disposition ledger.

This is an audit artifact, not a general parser.  It combines:

* the exact current manifest sections;
* source-document table-of-contents / major-section rows; and
* explicit duplicate/subset rows for local extracts that should not be
  independently ingested.
"""

from __future__ import annotations

import csv
import json
import re
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path


ROOT = Path.cwd()
OUT = ROOT / "research/tools/requirements-spike/quality/source_section_ledger/source_section_ledger_2026-04-30"
MANIFEST_DIR = ROOT / "research/tools/requirements-spike/documents"
REGISTRY_ROOT = ROOT / "research/tools/requirements-spike/registry/ollama_first"


@dataclass(frozen=True)
class Row:
    row_id: str
    document_id: str
    source_path: str
    section_ref: str
    title: str
    granularity: str
    disposition: str
    priority: str
    manifest_section_id: str
    start_line: str
    end_line: str
    rationale: str
    next_action: str


DOC_SOURCE_PATH = {
    "cap413-extracted": "research/txt/cap413-extracted.txt",
    "cap413-aerodrome-chapter": "research/txt/cap413-aerodrome-chapter.txt",
    "egast-vfr-extracted": "research/txt/egast-vfr-extracted.txt",
    "h01-extracted": "research/txt/h01-extracted.txt",
    "h01-aerodrome-chapter": "research/txt/h01-aerodrome-chapter.txt",
    "icao4444-extracted": "research/txt/icao4444-extracted.txt",
    "icao9432-extracted": "research/txt/icao9432-extracted.txt",
    "icao9432-aerodrome-chapter": "research/txt/icao9432-aerodrome-chapter.txt",
    "nolan-fundamentals-extracted": "research/txt/nolan-fundamentals-extracted.txt",
    "safetysense22-extracted": "research/txt/safetysense22-extracted.txt",
    "sera-923-2012-extracted": "research/txt/sera-923-2012-extracted.txt",
    "slovenia-vfr-extracted": "research/txt/slovenia-vfr-extracted.txt",
}


CURRENT_MANIFEST_BY_REF = {
    ("cap413-extracted", "CAP413 2.68-2.71"): "readback_2_68_2_71",
    ("egast-vfr-extracted", "EGAST 2 readback advisory"): "readback_advisory",
    ("h01-extracted", "H01 3.8.1"): "acknowledgement_3_8_1",
    ("h01-extracted", "H01 3.8.2"): "end_of_conversation_3_8_2",
    ("h01-extracted", "H01 3.8.3"): "corrections_3_8_3",
    ("icao4444-extracted", "ICAO4444 4.3.2.1"): "transfer_4_3_2_1",
    ("icao4444-extracted", "ICAO4444 4.5.7.5"): "readback_4_5_7_5",
    ("icao4444-extracted", "ICAO4444 4.6.1"): "speed_control_4_6_1",
    ("icao4444-extracted", "ICAO4444 5.8.1-5.8.4"): "wake_turbulence_5_8",
    ("icao4444-extracted", "ICAO4444 7.6.1-7.6.3.1.1"): "aerodrome_traffic_7_6",
    ("icao4444-extracted", "ICAO4444 7.9"): "departing_aircraft_7_9",
    ("icao4444-extracted", "ICAO4444 7.10"): "arriving_aircraft_7_10",
    ("icao4444-extracted", "ICAO4444 7.11.1-7.11.6"): "reduced_runway_7_11",
    ("icao9432-extracted", "ICAO9432 2.8.1 EN"): "communications_2_8_1_en",
    ("icao9432-extracted", "ICAO9432 2.8.3 EN"): "readback_2_8_3_en",
    ("icao9432-extracted", "ICAO9432 4.4 EN"): "taxi_4_4_en",
    ("safetysense22-extracted", "SafetySense Readbacks"): "readbacks",
    ("sera-923-2012-extracted", "SERA.8005"): "atc_service_8005",
    ("sera-923-2012-extracted", "SERA.8010"): "separation_minima_8010",
    ("sera-923-2012-extracted", "SERA.8015(a-d)"): "clearances_8015_a_d",
    ("sera-923-2012-extracted", "SERA.8015(e)"): "readback_8015_e",
    ("slovenia-vfr-extracted", "Slovenia 2 readback"): "readback",
}


def slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


def add(
    rows: list[Row],
    document_id: str,
    section_ref: str,
    title: str,
    granularity: str,
    disposition: str,
    priority: str,
    rationale: str,
    next_action: str,
    manifest_section_id: str = "",
    start_line: str = "",
    end_line: str = "",
) -> None:
    rows.append(
        Row(
            row_id=f"{document_id}::{slug(section_ref)}",
            document_id=document_id,
            source_path=DOC_SOURCE_PATH[document_id],
            section_ref=section_ref,
            title=title,
            granularity=granularity,
            disposition=disposition,
            priority=priority,
            manifest_section_id=manifest_section_id,
            start_line=start_line,
            end_line=end_line,
            rationale=rationale,
            next_action=next_action,
        )
    )


def read_manifest_windows() -> list[dict]:
    windows: list[dict] = []
    for path in sorted(MANIFEST_DIR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for section in data["sections"]:
            windows.append(
                {
                    "document_id": data["documentId"],
                    "source_path": data["sourcePath"],
                    "section_id": section["sectionId"],
                    "family_id": section["familyId"],
                    "start_line": section["startLine"],
                    "end_line": section["endLine"],
                    "notes": section.get("notes", ""),
                }
            )
    return windows


def read_landed_registry_sections() -> set[tuple[str, str]]:
    landed: set[tuple[str, str]] = set()
    candidates_root = REGISTRY_ROOT / "candidates"
    if not candidates_root.exists():
        return landed
    for document_dir in candidates_root.iterdir():
        if not document_dir.is_dir():
            continue
        for section_dir in document_dir.iterdir():
            if not section_dir.is_dir():
                continue
            candidate_files = [
                path
                for path in section_dir.glob("*.json")
                if path.name != "_section.json"
            ]
            if candidate_files:
                landed.add((document_dir.name, section_dir.name))
    return landed


def add_manifest_windows(rows: list[Row]) -> None:
    landed_sections = read_landed_registry_sections()
    for window in read_manifest_windows():
        is_landed = (window["document_id"], window["section_id"]) in landed_sections
        disposition = "extracted_current" if is_landed else "manifest_only"
        priority = "none" if is_landed else "high"
        rationale = (
            "Exact line range has accepted records in the registry."
            if is_landed
            else "Exact line range is in documents/*.json but has not landed accepted registry records."
        )
        next_action = (
            "No action unless the source line range or accepted record set is corrected."
            if is_landed
            else "Queue for clean Ollama ingestion, promotion, curation, and audit."
        )
        add(
            rows,
            window["document_id"],
            f"MANIFEST {window['section_id']}",
            window["notes"].split(". ")[0],
            "manifest_window",
            disposition,
            priority,
            rationale,
            next_action,
            manifest_section_id=window["section_id"],
            start_line=str(window["start_line"]),
            end_line=str(window["end_line"]),
        )


def add_duplicate_extracts(rows: list[Row]) -> None:
    duplicates = [
        ("cap413-aerodrome-chapter", "CAP 413 aerodrome excerpt", "Subset of cap413-extracted; do not ingest independently."),
        ("h01-aerodrome-chapter", "H01 aerodrome/phraseology excerpt", "Subset of h01-extracted; do not ingest independently."),
        ("icao9432-aerodrome-chapter", "ICAO 9432 aerodrome-control excerpt", "Subset of icao9432-extracted; do not ingest independently."),
    ]
    for document_id, title, rationale in duplicates:
        add(
            rows,
            document_id,
            "WHOLE FILE",
            title,
            "duplicate_extract",
            "duplicate_subset",
            "none",
            rationale,
            "Use the corresponding full extract row when widening coverage.",
        )


def cap413_rows(rows: list[Row]) -> None:
    sections = [
        ("CAP413 front matter", "Amendment record, effective pages, revision history, foreword", "out_of_scope", "none", "Document administration, not operational content."),
        ("CAP413 Ch1", "Glossary", "support_only", "low", "Definitions and abbreviations support interpretation but should not become standalone rules without a procedural source."),
        ("CAP413 Ch2", "Radiotelephony general procedures", "partially_extracted", "high", "Current registry covers readback requirements plus CAP 413 §§2.82-2.91; other communication procedures remain available."),
        ("CAP413 Ch2 Introduction", "Introduction", "support_only", "low", "Context for UK radiotelephony procedure."),
        ("CAP413 Ch2 Use of VHF RTF Channels", "Use of VHF RTF channels", "extract", "medium", "Potential frequency/channel procedure material."),
        ("CAP413 Ch2 Transmitting Technique", "Transmitting technique", "extract", "medium", "Useful controller/pilot communication-behaviour material."),
        ("CAP413 Ch2 Transmission of Letters", "Transmission of letters", "support_only", "low", "Phraseology support unless spelling rules are needed directly."),
        ("CAP413 Ch2 Transmission of Numbers", "Transmission of numbers", "support_only", "low", "Phraseology support unless number transmission is modelled."),
        ("CAP413 Ch2 Transmission of Time", "Transmission of time", "support_only", "low", "Phraseology support."),
        ("CAP413 Ch2 Standard Words and Phrases", "Standard words and phrases", "extract", "medium", "Useful phraseology vocabulary and semantic mapping."),
        ("CAP413 Ch2 Callsigns for Aeronautical Stations", "Callsigns for aeronautical stations", "extract", "medium", "Potential station naming and callsign rules."),
        ("CAP413 Ch2 Callsigns for Aircraft", "Callsigns for aircraft", "extract", "medium", "Potential aircraft callsign rules."),
        ("CAP413 Ch2 Military Aircraft Callsigns", "Military aircraft callsigns", "defer_with_reason", "low", "Military-specific scope is not a tower-v1 priority."),
        ("CAP413 Ch2 Continuation of Communications", "Continuation of communications", "extract", "medium", "Potential frequency/contact persistence rules."),
        ("CAP413 Ch2 Corrections and Repetitions", "Corrections and repetitions", "manifest_only", "high", "Exact CAP 413 §§2.54-2.55 window exists but has not landed in the registry."),
        ("CAP413 Ch2 Acknowledgement of Receipt", "Acknowledgement of receipt", "manifest_only", "high", "Exact CAP 413 §2.56 window exists but has not landed in the registry."),
        ("CAP413 Ch2 Transfer of Communications", "Transfer of communications", "manifest_only", "high", "Exact CAP 413 §§2.57-2.64 split windows exist but have not landed in the registry."),
        ("CAP413 Ch2 Clearance Issue and Read-back Requirements", "Clearance issue and read-back requirements", "partially_extracted", "high", "Registry covers CAP 413 2.68-2.71 and exact windows exist for 2.65-2.67 and 2.72-2.75."),
        ("CAP413 Ch2 Withholding Clearances", "Withholding clearances", "extract", "medium", "Potential controller clearance timing behaviour."),
        ("CAP413 Ch2 Simultaneous Transmissions", "Simultaneous transmissions", "defer_with_reason", "low", "Radio-channel simulation detail not yet modelled."),
        ("CAP413 Ch2 Complying with Clearances and Instructions", "Complying with clearances and instructions", "extracted_current", "none", "CAP 413 §§2.82-2.87 landed in the registry on 2026-05-01."),
        ("CAP413 Ch2 Communication Failure", "Communication failure", "extracted_current", "none", "CAP 413 §§2.88-2.91 landed in the registry on 2026-05-01."),
        ("CAP413 Ch2 Test Transmissions", "Test transmissions", "defer_with_reason", "low", "Radio-service detail outside current sim behaviour."),
        ("CAP413 Ch2 Complaints and Records", "Telecommunication complaints, watch, and communication records", "out_of_scope", "none", "Administrative/operational logging material."),
        ("CAP413 Ch2 Categories of Message", "Categories of message", "extract", "medium", "May support message priority/emergency handling."),
        ("CAP413 Ch3", "General phraseology", "support_only", "medium", "Useful phraseology templates; lower authority than ICAO/SERA where they overlap."),
        ("CAP413 Ch4", "Aerodrome phraseology", "extract", "high", "High-value tower/taxi/takeoff/landing phraseology coverage not represented by current manifest."),
        ("CAP413 Ch5", "Radar phraseology", "defer_with_reason", "medium", "Surveillance/service material; extract when radar services enter scope."),
        ("CAP413 Ch6", "Approach phraseology", "defer_with_reason", "medium", "Approach-control material; extract with IFR/APP sequencing work."),
        ("CAP413 Ch7", "Area phraseology", "defer_with_reason", "low", "En-route/area-control scope."),
        ("CAP413 Ch8", "Emergency phraseology", "extract", "high", "Emergency communications likely valuable for controller/pilot failure modes."),
        ("CAP413 Ch9", "Miscellaneous phraseology", "support_only", "medium", "Contains useful special topics but should be selected by consumer need."),
        ("CAP413 Ch10", "Military specific phraseology", "out_of_scope", "none", "Military-specific material is not in the current civil tower scope."),
        ("CAP413 Ch11", "Phraseology examples", "support_only", "medium", "Example dialogues should seed scenarios, not requirements by themselves."),
        ("CAP413 Appendix 1", "UK differences to ICAO radiotelephony procedures", "support_only", "medium", "Jurisdictional variation; extract only where UK-specific behaviour is needed."),
        ("CAP413 Appendix 2", "UK civil/military radiotelephony differences", "out_of_scope", "none", "Civil/military difference material outside current scope."),
        ("CAP413 Bibliography", "Bibliography", "out_of_scope", "none", "Reference list only."),
    ]
    for ref, title, disposition, priority, rationale in sections:
        next_action = "No ingestion now." if disposition in {"support_only", "out_of_scope", "defer_with_reason", "extracted_current"} else "Consider in a future topic batch."
        add(rows, "cap413-extracted", ref, title, "toc_major_section", disposition, priority, rationale, next_action)


def egast_rows(rows: list[Row]) -> None:
    sections = [
        ("EGAST 1", "Introduction and abbreviations", "support_only", "low"),
        ("EGAST 2", "Good radiotelephony practice", "partially_extracted", "medium"),
        ("EGAST 3", "General phraseology", "support_only", "medium"),
        ("EGAST 4", "Departure phraseology", "support_only", "medium"),
        ("EGAST 5", "Aerodrome phraseology for helicopters", "support_only", "low"),
        ("EGAST 6", "Cross country flight phraseology", "support_only", "medium"),
        ("EGAST 7", "Arrival phraseology", "support_only", "medium"),
        ("EGAST 8", "Unattended aerodrome phraseology", "support_only", "low"),
    ]
    for ref, title, disposition, priority in sections:
        add(
            rows,
            "egast-vfr-extracted",
            ref,
            title,
            "toc_chapter",
            disposition,
            priority,
            "Best-practice guide; preserve for scenario and phraseology support rather than primary law.",
            "Use as support material unless a best-practice consumer explicitly asks for it.",
        )


def h01_rows(rows: list[Row]) -> None:
    sections = [
        ("H01 1", "Introduction"),
        ("H01 2", "General"),
        ("H01 2.1", "Guidelines for transmission"),
        ("H01 2.2", "Execution of radio telephony"),
        ("H01 2.3", "Categories of messages"),
        ("H01 2.4", "Cancellation of messages"),
        ("H01 2.5", "Language"),
        ("H01 2.6", "Transmission of numbers"),
        ("H01 2.7", "Transmission of RTF frequencies"),
        ("H01 2.8", "Transmission of time"),
        ("H01 2.9", "Transmission of levels"),
        ("H01 2.10", "Word spelling in radiotelephony"),
        ("H01 2.11", "Transmitting technique"),
        ("H01 3", "Radiotelephony procedures"),
        ("H01 3.1", "Standard words and phrases"),
        ("H01 3.2", "Call signs"),
        ("H01 3.3", "Establishment of radiotelephony communications"),
        ("H01 3.4", "Interpilot communication"),
        ("H01 3.5", "Multiple call"),
        ("H01 3.6", "General call"),
        ("H01 3.7", "Test procedures"),
        ("H01 3.8", "Exchange of communications"),
        ("H01 3.9", "Assurance of RTF communication/frequencies to be used"),
        ("H01 3.10", "Transfer of VHF communications"),
        ("H01 3.11", "Specific communication procedures"),
        ("H01 4", "Contingencies"),
        ("H01 4.1", "Distress and urgency communication procedures"),
        ("H01 4.2", "Acts of unlawful interference"),
        ("H01 4.3", "Direction finding"),
        ("H01 4.4", "Voice communications failure"),
        ("H01 4.5", "Blocked frequency"),
        ("H01 4.6", "Unauthorized use of ATC frequency"),
        ("H01 4.7", "Minimum fuel and fuel emergency"),
        ("H01 4.8", "Transponder codes in case of emergency"),
        ("H01 5", "Phraseologies"),
        ("H01 5.1", "Contents of phraseologies"),
        ("H01 5.2", "Application"),
        ("H01 5.3", "Controlled aerodromes"),
        ("H01 5.4", "Aerodromes without air traffic control"),
        ("H01 5.5", "Miscellaneous aerodrome phraseology"),
        ("H01 5.6", "General flight handling phraseology"),
        ("H01 5.7", "Additional phraseologies for IFR flights and surveillance services"),
        ("H01 5.8", "Phraseologies in contingencies"),
        ("H01 5.9", "Military phraseologies"),
        ("H01 Appendix 1", "Interception"),
        ("H01 Appendix 2", "Definitions"),
        ("H01 Appendix 3", "Abbreviations for MET transmissions"),
        ("H01 Appendix 4", "Aircraft observations and reports by voice communications"),
        ("H01 Appendix 5", "Sources"),
        ("H01 Amendments", "List of amendments"),
    ]
    partial = {"H01 3.8"}
    high_extract = {"H01 3.3", "H01 3.8", "H01 3.9", "H01 3.10", "H01 4", "H01 4.1", "H01 4.4", "H01 5.3", "H01 5.4", "H01 5.6", "H01 5.8"}
    out = {"H01 5.9", "H01 Appendix 5", "H01 Amendments"}
    for ref, title in sections:
        if ref in partial:
            disposition = "partially_extracted"
        elif ref in out:
            disposition = "out_of_scope"
        elif ref in high_extract:
            disposition = "extract"
        elif ref.startswith("H01 Appendix"):
            disposition = "support_only"
        else:
            disposition = "support_only"
        priority = "high" if ref in high_extract else ("none" if disposition == "out_of_scope" else "medium")
        rationale = "Austrian operational guidance; bilingual text needs English-side filtering before extraction."
        add(rows, "h01-extracted", ref, title, "toc_section", disposition, priority, rationale, "Use in a phraseology/communications topic batch.")


def icao4444_rows(rows: list[Row]) -> None:
    sections = [
        ("ICAO4444 Ch1", "Definitions"),
        ("ICAO4444 Ch2", "ATS safety management"),
        ("ICAO4444 2.1", "General"),
        ("ICAO4444 2.2", "Objectives"),
        ("ICAO4444 2.3", "ATS safety management activities"),
        ("ICAO4444 2.4", "Monitoring of safety levels"),
        ("ICAO4444 2.5", "Safety reviews"),
        ("ICAO4444 2.6", "Safety assessments"),
        ("ICAO4444 2.7", "Safety-enhancing measures"),
        ("ICAO4444 Ch3", "ATS system capacity and air traffic flow management"),
        ("ICAO4444 3.1", "Capacity management"),
        ("ICAO4444 3.2", "Air traffic flow management"),
        ("ICAO4444 Ch4", "General provisions for air traffic services"),
        ("ICAO4444 4.1", "Responsibility for the provision of air traffic control service"),
        ("ICAO4444 4.2", "Responsibility for the provision of flight information service and alerting service"),
        ("ICAO4444 4.3", "Division of responsibility for control between air traffic control units"),
        ("ICAO4444 4.4", "Flight plan"),
        ("ICAO4444 4.5", "Air traffic control clearances"),
        ("ICAO4444 4.6", "Horizontal speed control instructions"),
        ("ICAO4444 4.7", "Vertical speed control instructions"),
        ("ICAO4444 4.8", "Change from IFR to VFR flight"),
        ("ICAO4444 4.9", "Wake turbulence categories"),
        ("ICAO4444 4.10", "Altimeter setting procedures"),
        ("ICAO4444 4.11", "Position reporting"),
        ("ICAO4444 4.12", "Reporting operational and meteorological information"),
        ("ICAO4444 4.13", "Presentation and updating of flight plan and control data"),
        ("ICAO4444 4.14", "Failure or irregularity of systems and equipment"),
        ("ICAO4444 4.15", "Data link communications initiation procedures"),
        ("ICAO4444 Ch5", "Separation methods and minima"),
        ("ICAO4444 5.1", "Introduction"),
        ("ICAO4444 5.2", "Provisions for the separation of controlled traffic"),
        ("ICAO4444 5.3", "Vertical separation"),
        ("ICAO4444 5.4", "Horizontal separation"),
        ("ICAO4444 5.5", "Separation of aircraft holding in flight"),
        ("ICAO4444 5.6", "Minimum separation between departing aircraft"),
        ("ICAO4444 5.7", "Separation of departing aircraft from arriving aircraft"),
        ("ICAO4444 5.8", "Time-based wake turbulence longitudinal separation minima"),
        ("ICAO4444 5.9", "Clearances to fly maintaining own separation in VMC"),
        ("ICAO4444 5.10", "Essential traffic information"),
        ("ICAO4444 5.11", "Reduction in separation minima"),
        ("ICAO4444 Ch6", "Separation in the vicinity of aerodromes"),
        ("ICAO4444 6.1", "Reduction in separation minima in the vicinity of aerodromes"),
        ("ICAO4444 6.2", "Essential local traffic"),
        ("ICAO4444 6.3", "Procedures for departing aircraft"),
        ("ICAO4444 6.4", "Information for departing aircraft"),
        ("ICAO4444 6.5", "Procedures for arriving aircraft"),
        ("ICAO4444 6.6", "Information for arriving aircraft"),
        ("ICAO4444 6.7", "Operations on parallel or near-parallel runways"),
        ("ICAO4444 Ch7", "Procedures for aerodrome control service"),
        ("ICAO4444 7.1", "Functions of aerodrome control towers"),
        ("ICAO4444 7.2", "Selection of runway-in-use"),
        ("ICAO4444 7.3", "Initial call to aerodrome control tower"),
        ("ICAO4444 7.4", "Information to aircraft by aerodrome control towers"),
        ("ICAO4444 7.5", "Essential information on aerodrome conditions"),
        ("ICAO4444 7.6", "Control of aerodrome traffic"),
        ("ICAO4444 7.7", "Control of traffic in the traffic circuit"),
        ("ICAO4444 7.8", "Order of priority for arriving and departing aircraft"),
        ("ICAO4444 7.9", "Control of departing aircraft"),
        ("ICAO4444 7.10", "Control of arriving aircraft"),
        ("ICAO4444 7.11", "Reduced runway separation minima between aircraft using the same runway"),
        ("ICAO4444 7.12", "Use of a visual surveillance system in aerodrome control service"),
        ("ICAO4444 7.13", "Procedures for low visibility operations"),
        ("ICAO4444 7.14", "Suspension of visual flight rules operations"),
        ("ICAO4444 7.15", "Authorization of special VFR flights"),
        ("ICAO4444 7.16", "Aeronautical ground lights"),
        ("ICAO4444 7.17", "Designation of hot spots"),
        ("ICAO4444 Ch8", "ATS surveillance services"),
        ("ICAO4444 8.1", "ATS surveillance systems capabilities"),
        ("ICAO4444 8.2", "Situation display"),
        ("ICAO4444 8.3", "Communications"),
        ("ICAO4444 8.4", "Provision of ATS surveillance services"),
        ("ICAO4444 8.5", "Use of SSR transponders and ADS-B transmitters"),
        ("ICAO4444 8.6", "General procedures"),
        ("ICAO4444 8.7", "Use of ATS surveillance systems in the air traffic control service"),
        ("ICAO4444 8.8", "Emergencies, hazards and equipment failures"),
        ("ICAO4444 8.9", "Use of ATS surveillance systems in the approach control service"),
        ("ICAO4444 8.10", "Use of ATS surveillance systems in the aerodrome control service"),
        ("ICAO4444 8.11", "Use of ATS surveillance systems in the flight information service"),
        ("ICAO4444 Ch9", "Flight information service and alerting service"),
        ("ICAO4444 9.1", "Flight information service"),
        ("ICAO4444 9.2", "Alerting service"),
        ("ICAO4444 Ch10", "Coordination"),
        ("ICAO4444 10.1", "Coordination in respect of ATC service"),
        ("ICAO4444 10.2", "Coordination in respect of FIS and alerting service"),
        ("ICAO4444 10.3", "Coordination in respect of air traffic advisory service"),
        ("ICAO4444 10.4", "Coordination between ATS units and telecommunication stations"),
        ("ICAO4444 Ch11", "Air traffic services messages"),
        ("ICAO4444 11.1", "Categories of messages"),
        ("ICAO4444 11.2", "General provisions"),
        ("ICAO4444 11.3", "Methods of message exchange"),
        ("ICAO4444 11.4", "Message types and their application"),
        ("ICAO4444 Ch12", "Phraseologies"),
        ("ICAO4444 12.1", "Communications procedures"),
        ("ICAO4444 12.2", "General phraseologies"),
        ("ICAO4444 12.3", "ATC phraseologies"),
        ("ICAO4444 12.4", "ATS surveillance service phraseologies"),
        ("ICAO4444 12.5", "ADS-C phraseologies"),
        ("ICAO4444 12.6", "Alerting phraseologies"),
        ("ICAO4444 12.7", "Ground crew/flight crew phraseologies"),
        ("ICAO4444 Ch13", "ADS-C services"),
        ("ICAO4444 13.1", "General"),
        ("ICAO4444 13.2", "ADS-C ground system capabilities"),
        ("ICAO4444 13.3", "ADS-C-related aeronautical information"),
        ("ICAO4444 13.4", "Use of ADS-C in ATC service"),
        ("ICAO4444 13.5", "Use of ADS-C in separation minima"),
        ("ICAO4444 Ch14", "Controller-pilot data link communications"),
        ("ICAO4444 14.1", "General"),
        ("ICAO4444 14.2", "Establishment of CPDLC"),
        ("ICAO4444 14.3", "Exchange of operational CPDLC messages"),
        ("ICAO4444 Ch15", "Emergencies, communication failure and contingencies"),
        ("ICAO4444 15.1", "Emergency procedures"),
        ("ICAO4444 15.2", "Oceanic in-flight contingencies"),
        ("ICAO4444 15.3", "Air-ground communications failure"),
        ("ICAO4444 15.4", "Assistance to VFR flights"),
        ("ICAO4444 15.5", "Other in-flight contingencies"),
        ("ICAO4444 15.6", "ATC contingencies"),
        ("ICAO4444 15.7", "Other ATC contingency procedures"),
        ("ICAO4444 15.8", "Volcanic ash cloud procedures"),
        ("ICAO4444 Ch16", "Miscellaneous procedures"),
        ("ICAO4444 16.1", "Military traffic responsibility"),
        ("ICAO4444 16.2", "Unmanned free balloons responsibility"),
        ("ICAO4444 16.3", "Air traffic incident report"),
        ("ICAO4444 16.4", "Use of repetitive flight plans"),
        ("ICAO4444 16.5", "Strategic lateral offset procedures"),
        ("ICAO4444 16.6", "Suspected communicable disease or public health risk"),
        ("ICAO4444 Appendix 1", "Instructions for air-reporting by voice communications"),
        ("ICAO4444 Appendix 2", "Flight plan"),
        ("ICAO4444 Appendix 3", "Air traffic services messages"),
        ("ICAO4444 Appendix 4", "Air traffic incident report"),
        ("ICAO4444 Appendix 5", "CPDLC message set"),
        ("ICAO4444 Appendix 6", "AIDC messages"),
    ]
    partial = {"ICAO4444 4.3", "ICAO4444 4.5", "ICAO4444 4.6", "ICAO4444 5.8", "ICAO4444 7.6", "ICAO4444 7.11"}
    extracted = {"ICAO4444 7.9", "ICAO4444 7.10"}
    high_extract = {
        "ICAO4444 4.1", "ICAO4444 4.2", "ICAO4444 4.3", "ICAO4444 4.5", "ICAO4444 4.6", "ICAO4444 4.7",
        "ICAO4444 4.9", "ICAO4444 4.10", "ICAO4444 4.11", "ICAO4444 4.14", "ICAO4444 5.2", "ICAO4444 5.3",
        "ICAO4444 5.4", "ICAO4444 5.6", "ICAO4444 5.7", "ICAO4444 5.8", "ICAO4444 5.10", "ICAO4444 5.11",
        "ICAO4444 6.1", "ICAO4444 6.2", "ICAO4444 6.3", "ICAO4444 6.4", "ICAO4444 6.5", "ICAO4444 6.6",
        "ICAO4444 6.7", "ICAO4444 7.1", "ICAO4444 7.2", "ICAO4444 7.3", "ICAO4444 7.4", "ICAO4444 7.5",
        "ICAO4444 7.6", "ICAO4444 7.7", "ICAO4444 7.8", "ICAO4444 7.11", "ICAO4444 7.12", "ICAO4444 7.13",
        "ICAO4444 7.14", "ICAO4444 7.15", "ICAO4444 7.16", "ICAO4444 7.17", "ICAO4444 12.1", "ICAO4444 12.2",
        "ICAO4444 12.3", "ICAO4444 15.1", "ICAO4444 15.3", "ICAO4444 15.4", "ICAO4444 15.6",
    }
    defer_prefixes = ("ICAO4444 8", "ICAO4444 10", "ICAO4444 13", "ICAO4444 14", "ICAO4444 16", "ICAO4444 Appendix")
    for ref, title in sections:
        if ref in extracted:
            disposition = "extracted_current"
        elif ref in partial:
            disposition = "partially_extracted"
        elif ref in high_extract:
            disposition = "extract"
        elif ref.startswith(defer_prefixes):
            disposition = "defer_with_reason"
        elif ref == "ICAO4444 Ch1":
            disposition = "support_only"
        else:
            disposition = "support_only"
        priority = "high" if disposition in {"extract", "partially_extracted"} and ref in high_extract else ("none" if disposition == "extracted_current" else "medium")
        rationale = "Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour."
        if disposition == "defer_with_reason":
            rationale = "Relevant to future surveillance, coordination, data-link, en-route, or administrative scope rather than immediate tower extraction."
        add(rows, "icao4444-extracted", ref, title, "toc_section", disposition, priority, rationale, "Queue by topic batch if disposition is extract or partially_extracted.")


def icao9432_rows(rows: list[Row]) -> None:
    sections = [
        ("ICAO9432 Ch1", "Glossary"), ("ICAO9432 1.1", "Definitions"), ("ICAO9432 1.2", "Abbreviations"), ("ICAO9432 1.3", "Explanation of scenario"),
        ("ICAO9432 Ch2", "General operating procedures"), ("ICAO9432 2.1", "Introduction"), ("ICAO9432 2.2", "Transmitting technique"),
        ("ICAO9432 2.3", "Transmission of letters"), ("ICAO9432 2.4", "Transmission of numbers"), ("ICAO9432 2.5", "Transmission of time"),
        ("ICAO9432 2.6", "Standard words and phrases"), ("ICAO9432 2.7", "Call signs"), ("ICAO9432 2.7.1", "Call signs for aeronautical stations"),
        ("ICAO9432 2.7.2", "Aircraft call signs"), ("ICAO9432 2.8", "Communications"), ("ICAO9432 2.8.1", "Establishment and continuation of communication"),
        ("ICAO9432 2.8.2", "Transfer of communications"), ("ICAO9432 2.8.3", "Issue of clearance and read-back requirements"),
        ("ICAO9432 2.8.4", "Test procedures"), ("ICAO9432 Ch3", "General phraseology"), ("ICAO9432 3.1", "Introduction"),
        ("ICAO9432 3.2", "Role of phraseologies and plain language"), ("ICAO9432 3.3", "Level instructions"), ("ICAO9432 3.4", "Position reporting"),
        ("ICAO9432 3.5", "Flight plans"), ("ICAO9432 Ch4", "Aerodrome control: aircraft"), ("ICAO9432 4.1", "Introduction"),
        ("ICAO9432 4.2", "Departure information and engine starting procedures"), ("ICAO9432 4.3", "Push-back"), ("ICAO9432 4.4", "Taxi instructions"),
        ("ICAO9432 4.5", "Take-off procedures"), ("ICAO9432 4.6", "Aerodrome traffic circuit"), ("ICAO9432 4.7", "Final approach and landing"),
        ("ICAO9432 4.8", "Go around"), ("ICAO9432 4.9", "After landing"), ("ICAO9432 4.10", "Essential aerodrome information"),
        ("ICAO9432 Ch5", "Aerodrome control: vehicles"), ("ICAO9432 5.1", "Introduction"), ("ICAO9432 5.2", "Movement instructions"),
        ("ICAO9432 5.3", "Crossing runways"), ("ICAO9432 5.4", "Vehicles towing aircraft"), ("ICAO9432 Ch6", "General ATS surveillance service phraseology"),
        ("ICAO9432 6.1", "Introduction"), ("ICAO9432 6.2", "Identification and vectoring"), ("ICAO9432 6.3", "Vectoring"),
        ("ICAO9432 6.4", "Traffic information and avoiding action"), ("ICAO9432 6.5", "Secondary surveillance radar"),
        ("ICAO9432 6.6", "Radar assistance after radiocommunications failure"), ("ICAO9432 6.7", "Alerting phraseologies"),
        ("ICAO9432 Ch7", "Approach control"), ("ICAO9432 7.1", "IFR departures"), ("ICAO9432 7.2", "VFR departures"),
        ("ICAO9432 7.3", "IFR arrivals"), ("ICAO9432 7.4", "VFR arrivals"), ("ICAO9432 7.5", "Vectors to final approach"),
        ("ICAO9432 7.6", "Surveillance radar approach"), ("ICAO9432 7.7", "Precision radar approach"), ("ICAO9432 Ch8", "Area control"),
        ("ICAO9432 8.1", "Area control units"), ("ICAO9432 8.2", "Position information"), ("ICAO9432 8.3", "Level information"),
        ("ICAO9432 8.4", "Flights joining airways"), ("ICAO9432 8.5", "Flights leaving airways"), ("ICAO9432 8.6", "Flights crossing airways"),
        ("ICAO9432 8.7", "Flights holding en route"), ("ICAO9432 8.8", "ATS surveillance"), ("ICAO9432 8.9", "Automatic Dependent Surveillance"),
        ("ICAO9432 8.10", "Oceanic control"), ("ICAO9432 Ch9", "Distress, urgency, and communications failure"), ("ICAO9432 9.1", "Introduction"),
        ("ICAO9432 9.2", "Distress messages"), ("ICAO9432 9.2.1", "Aircraft in distress"), ("ICAO9432 9.2.2", "Imposition of silence"),
        ("ICAO9432 9.2.3", "Termination of distress and silence"), ("ICAO9432 9.3", "Urgency messages"), ("ICAO9432 9.4", "Emergency descent"),
        ("ICAO9432 9.5", "Aircraft communications failure"), ("ICAO9432 Ch10", "Meteorological and other aerodrome information"),
        ("ICAO9432 10.1", "Introduction"), ("ICAO9432 10.2", "Runway Visual Range"), ("ICAO9432 10.3", "Runway surface conditions"),
        ("ICAO9432 Ch11", "Miscellaneous flight handling"), ("ICAO9432 11.1", "SELCAL"), ("ICAO9432 11.2", "Fuel dumping"),
        ("ICAO9432 11.3", "Wake turbulence"), ("ICAO9432 11.4", "Wind shear"), ("ICAO9432 11.5", "Direction finding"),
        ("ICAO9432 11.6", "ACAS manoeuvres"), ("ICAO9432 Appendix 1", "Differences from ICAO radiotelephony procedures"),
        ("ICAO9432 Appendix 2", "Inaccuracies corrected in original version"),
    ]
    partial = {"ICAO9432 2.8"}
    extracted = {"ICAO9432 2.8.1", "ICAO9432 2.8.3", "ICAO9432 4.4"}
    high = {"ICAO9432 2.8.2", "ICAO9432 Ch4", "ICAO9432 4.2", "ICAO9432 4.3", "ICAO9432 4.5", "ICAO9432 4.6", "ICAO9432 4.7", "ICAO9432 4.8", "ICAO9432 4.9", "ICAO9432 4.10", "ICAO9432 Ch5", "ICAO9432 Ch9"}
    for ref, title in sections:
        if ref in extracted:
            disposition = "extracted_current"
        elif ref in partial:
            disposition = "partially_extracted"
        elif ref in high:
            disposition = "extract"
        elif ref.startswith(("ICAO9432 6", "ICAO9432 7", "ICAO9432 8")):
            disposition = "defer_with_reason"
        else:
            disposition = "support_only"
        priority = "high" if disposition in {"extract", "partially_extracted"} else ("none" if disposition == "extracted_current" else "medium")
        add(rows, "icao9432-extracted", ref, title, "toc_section", disposition, priority, "ICAO radiotelephony manual; bilingual extraction requires English-side filtering.", "Use as phraseology/support extraction source by topic.")


def sera_rows(rows: list[Row]) -> None:
    source = ROOT / DOC_SOURCE_PATH["sera-923-2012-extracted"]
    pattern = re.compile(r"^(SERA\.\d{4,5})\.?\s+(.+)$")
    seen: set[str] = set()
    current = {"SERA.8005", "SERA.8010", "SERA.8015", "SERA.8020", "SERA.8025", "SERA.8030", "SERA.8035"}
    high = {"SERA.3225", "SERA.6005", "SERA.7001", "SERA.7005", "SERA.8020", "SERA.8025", "SERA.8030", "SERA.8035", "SERA.9005", "SERA.9010", "SERA.11005", "SERA.12015"}
    medium = {"SERA.2005", "SERA.2010", "SERA.2015", "SERA.5005", "SERA.5010", "SERA.5015", "SERA.5020", "SERA.5025", "SERA.6001", "SERA.10005", "SERA.11010", "SERA.12020"}
    with source.open("r", encoding="utf-8", errors="replace") as f:
        for line_no, line in enumerate(f, start=1):
            if line_no > 3000:
                break
            m = pattern.match(line.strip())
            if not m:
                continue
            ref, title = m.groups()
            if not title[:1].isupper():
                continue
            if ref in seen:
                continue
            seen.add(ref)
            if ref in current:
                disposition = "extracted_current"
                priority = "none"
                rationale = "Current SERA manifest covers this ATS/clearance section."
            elif ref in high:
                disposition = "extract"
                priority = "high"
                rationale = "Binding SERA material relevant to ATC service, communications, FIS, aerodrome, or emergencies."
            elif ref in medium:
                disposition = "extract"
                priority = "medium"
                rationale = "Binding SERA material with likely downstream value but not the first tower batch."
            else:
                disposition = "defer_with_reason"
                priority = "low"
                rationale = "Binding regulation, but outside immediate ATC/tower extraction value or primarily flight-rule background."
            add(rows, "sera-923-2012-extracted", ref, title, "sera_section", disposition, priority, rationale, "Queue by SERA topic if needed.", start_line=str(line_no))


def safetysense_rows(rows: list[Row]) -> None:
    topics = [
        "Radio Licensing and Approval", "Radio Equipment", "Frequency selection", "Volume & Squelch", "Intercom", "Transponder",
        "Frequency Monitoring Codes", "Making radio Calls", "Use of Callsigns", "Readbacks", "Changing frequency", "Aerodrome operations",
        "In the Circuit", "ATC Aerodrome", "Aerodrome Flight Information Service", "Air/Ground Communication Service", "Unattended Aerodrome",
        "UK Flight Information Services", "UK FIS Sectors", "Lower Airspace Radar Services", "Requesting a UK Flight Information Service",
        "Transit of Controlled Airspace", "Special VFR", "Military ATC", "Military Air Traffic Zone", "Mayday or Pan call", "Radio Failure",
        "Lost procedures", "Further Reading",
    ]
    out = {"Radio Licensing and Approval", "Radio Equipment", "Volume & Squelch", "Intercom", "Further Reading"}
    for topic in topics:
        if topic == "Readbacks":
            disposition = "extracted_current"
            priority = "none"
        elif topic in out:
            disposition = "out_of_scope"
            priority = "none"
        else:
            disposition = "support_only"
            priority = "medium"
        add(rows, "safetysense22-extracted", f"SafetySense {topic}", topic, "leaflet_topic", disposition, priority, "Training/best-practice leaflet; use for heuristics and scenarios, not primary law.", "Use only as support unless a training consumer asks for it.")


def slovenia_rows(rows: list[Row]) -> None:
    chapters = [
        ("Slovenia 1", "Introduction"), ("Slovenia 2", "General"), ("Slovenia 3", "Departure phraseology"), ("Slovenia 4", "En-route phraseology"),
        ("Slovenia 5", "Arrival phraseology"), ("Slovenia 6", "Emergency and urgency messages"), ("Slovenia 7", "Helicopter operations"),
        ("Slovenia 8", "Unattended aerodromes"), ("Slovenia 9", "Aerodromes with a flight information service officer"),
        ("Slovenia 10", "Gliding"), ("Slovenia 11", "Ballooning"), ("Slovenia 12", "Complete flights"),
    ]
    for ref, title in chapters:
        disposition = "partially_extracted" if ref == "Slovenia 2" else "support_only"
        priority = "medium"
        add(rows, "slovenia-vfr-extracted", ref, title, "toc_chapter", disposition, priority, "Local VFR guide; useful for examples/local variation and scenario seeds.", "Use as support material unless local Slovenian VFR behaviour is in scope.")


def nolan_rows(rows: list[Row]) -> None:
    chapters = [
        ("Nolan Ch1", "History of Air Traffic Control"), ("Nolan Ch2", "Navigation Systems"), ("Nolan Ch3", "Air Traffic Control System Structure"),
        ("Nolan Ch4", "Airport Air Traffic Control Communications: Procedures and Phraseology"), ("Nolan Ch5", "Air Traffic Control Procedures and Organization"),
        ("Nolan Ch6", "Airport Air Traffic Control Tower"), ("Nolan Ch7", "Terminal Radar Approach Control"),
        ("Nolan Ch8", "Air Route Traffic Control Centers"), ("Nolan Ch9", "Traffic Management"), ("Nolan Ch10", "Oceanic and International ATC"),
        ("Nolan Ch11", "Flight Service Stations"), ("Nolan Ch12", "The Federal Aviation Administration"), ("Nolan Ch13", "Future of Air Traffic Control"),
    ]
    for ref, title in chapters:
        add(rows, "nolan-fundamentals-extracted", ref, title, "textbook_chapter", "background_only", "none", "Textbook/background source. Do not promote into requirements without corroborating primary source.", "Use for concepts, explanations, and scenario context only.")


def build_rows() -> list[Row]:
    rows: list[Row] = []
    add_manifest_windows(rows)
    add_duplicate_extracts(rows)
    cap413_rows(rows)
    egast_rows(rows)
    h01_rows(rows)
    icao4444_rows(rows)
    icao9432_rows(rows)
    sera_rows(rows)
    safetysense_rows(rows)
    slovenia_rows(rows)
    nolan_rows(rows)
    return rows


def write_csv(path: Path, rows: list[Row]) -> None:
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(asdict(rows[0]).keys()))
        writer.writeheader()
        for row in rows:
            writer.writerow(asdict(row))


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    rows = build_rows()
    duplicate_ids = [row.row_id for row in rows if [r.row_id for r in rows].count(row.row_id) > 1]
    if duplicate_ids:
        raise SystemExit(f"duplicate row_id values: {sorted(set(duplicate_ids))}")

    write_csv(OUT / "source_section_ledger.csv", rows)

    by_disposition = Counter(row.disposition for row in rows)
    by_document = Counter(row.document_id for row in rows)
    by_priority = Counter(row.priority for row in rows)
    high_value = [
        asdict(row)
        for row in rows
        if row.disposition in {"extract", "partially_extracted"} and row.priority == "high"
    ]
    manifest_window_count = sum(1 for row in rows if row.granularity == "manifest_window")
    landed_manifest_count = sum(
        1
        for row in rows
        if row.granularity == "manifest_window" and row.disposition == "extracted_current"
    )
    manifest_only_count = sum(
        1
        for row in rows
        if row.granularity == "manifest_window" and row.disposition == "manifest_only"
    )
    exact_window_hardening_needed = [
        row
        for row in rows
        if row.granularity != "manifest_window"
        and row.disposition in {"extract", "partially_extracted"}
        and row.priority == "high"
    ]
    summary = {
        "generated": "2026-05-01",
        "ledger_row_count": len(rows),
        "manifest_window_rows": manifest_window_count,
        "landed_manifest_window_rows": landed_manifest_count,
        "manifest_only_window_rows": manifest_only_count,
        "by_disposition": dict(sorted(by_disposition.items())),
        "by_document": dict(sorted(by_document.items())),
        "by_priority": dict(sorted(by_priority.items())),
        "high_priority_extract_or_partial_count": len(high_value),
        "high_priority_rows_needing_exact_windows": len(exact_window_hardening_needed),
        "scope_note": "TOC/major-section granularity plus exact manifest windows; not paragraph-level atomization.",
    }
    (OUT / "source_section_ledger_summary.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    md = [
        "# Source Section Ledger - 2026-04-30",
        "",
        "## Scope",
        "",
        f"This ledger is the control surface for future source widening. It covers the local `research/txt/` corpus at table-of-contents / major-section granularity and includes exact manifest-window rows. As of this ledger, `documents/*.json` contains {manifest_window_count} exact windows: {landed_manifest_count} have accepted registry records and {manifest_only_count} are manifest-only windows awaiting clean ingestion/promotion.",
        "",
        "It is not paragraph-level atomization. A row with `extract` means the section should be considered for a future topic batch; it does not mean the section has already been translated.",
        "",
        "## Summary",
        "",
        f"- Ledger rows: {summary['ledger_row_count']}",
        f"- Exact manifest-window rows: {summary['manifest_window_rows']}",
        f"- Landed manifest-window rows: {summary['landed_manifest_window_rows']}",
        f"- Manifest-only rows ready for Ollama ingestion: {summary['manifest_only_window_rows']}",
        f"- High-priority `extract` or `partially_extracted` rows: {summary['high_priority_extract_or_partial_count']}",
        f"- High-priority non-manifest rows needing exact source windows: {summary['high_priority_rows_needing_exact_windows']}",
        "",
        "The disposition table counts all rows. The Ollama-ready count above is restricted to exact `manifest_window` rows, so it excludes high-level TOC rollups that also carry `manifest_only` disposition.",
        "",
        "### By Disposition",
        "",
        "| Disposition | Rows |",
        "| --- | ---: |",
    ]
    for disposition, count in sorted(by_disposition.items()):
        md.append(f"| {disposition} | {count} |")
    md.extend(["", "### By Document", "", "| Document | Rows |", "| --- | ---: |"])
    for document_id, count in sorted(by_document.items()):
        md.append(f"| {document_id} | {count} |")
    md.extend(
        [
            "",
            "## High-Priority Future Extraction Rows",
            "",
            "| Document | Section | Disposition | Rationale |",
            "| --- | --- | --- | --- |",
        ]
    )
    for row in high_value:
        md.append(f"| {row['document_id']} | {row['section_ref']} - {row['title']} | {row['disposition']} | {row['rationale']} |")
    md.extend(
        [
            "",
            "## Checks",
            "",
            "1. Exact manifest-window rows are read from `documents/*.json`; their disposition is computed from whether accepted registry records exist for the same document/section.",
            "2. SERA section rows are generated from the source text's `SERA.xxxx` headings before the appendices/differences material.",
            "3. CAP 413, EGAST, H01, ICAO 4444, ICAO 9432, SafetySense, Slovenia, and Nolan rows are keyed from their table-of-contents / major-section structure.",
            "4. The three aerodrome excerpt files are marked `duplicate_subset` rather than queued for separate extraction.",
            "",
            "## Review Considerations",
            "",
            "FP / type safety: no Kotlin/domain code changed.",
            "",
            "Test architecture: this is an audit ledger. The check is deterministic regeneration plus row-id uniqueness and manifest cross-checks.",
            "",
            "Impact: future widening can now be tracked by section disposition instead of vague document-level intent.",
            "",
            "Operational correctness: no new ATC rule claim is made. Dispositions only rank source sections for future extraction/support/defer decisions.",
        ]
    )
    (OUT / "source_section_ledger.md").write_text("\n".join(md) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
