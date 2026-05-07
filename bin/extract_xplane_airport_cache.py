#!/usr/bin/env python3

"""Per-airport X-Plane data extractor.

Given an airport ICAO code, pulls only the fixes and navaids needed for that
airport out of the staged Navigraph cycle globals (`data/navdata/earth_fix.dat`,
`data/navdata/earth_nav.dat`) into compact per-airport cache files.

Selection policy:
  1. Everything in `earth_fix.dat` / `earth_nav.dat` tagged with the airport ICAO
     as its region column — these are the airport's terminal-area fixes/navaids.
  2. Plus every enroute or cross-region fix/navaid *referenced by the airport's
     CIFP procedures* (SID/STAR/APPCH). Selection is by (identifier, ICAO country
     region) pair so we don't accidentally pull a like-named fix on the wrong
     continent.

The cache files are in X-Plane's original line format so the same parser works
on them. Lightweight commit target (kilobytes), not the multi-megabyte globals.
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_authoring_report as report  # noqa: E402  (sibling import)


@dataclass(frozen=True)
class _FixLine:
    raw: str
    ident: str
    region: str
    country: str
    lat: float
    lon: float


@dataclass(frozen=True)
class _NavaidLine:
    raw: str
    type_code: int
    ident: str
    region: str
    country: str
    lat: float
    lon: float


def _parse_fix_line(line: str) -> _FixLine | None:
    parts = line.split()
    # earth_fix.dat layout: lat lon ident region country [extras...] name
    if len(parts) < 5:
        return None
    try:
        lat = float(parts[0])
        lon = float(parts[1])
    except ValueError:
        return None
    return _FixLine(
        raw=line,
        ident=parts[2],
        region=parts[3],
        country=parts[4],
        lat=lat,
        lon=lon,
    )


def _parse_navaid_line(line: str) -> _NavaidLine | None:
    parts = line.split()
    # earth_nav.dat layout varies by type_code but the first eight columns
    # follow: type lat lon elev freq range mag_var ident region country [...].
    if len(parts) < 10:
        return None
    try:
        type_code = int(parts[0])
        lat = float(parts[1])
        lon = float(parts[2])
    except ValueError:
        return None
    # ident and region are positional but type 99 (end-of-file marker) etc. break
    # the pattern, so we guard.
    if len(parts) < 11:
        return None
    ident = parts[7]
    region = parts[8]
    country = parts[9]
    return _NavaidLine(
        raw=line,
        type_code=type_code,
        ident=ident,
        region=region,
        country=country,
        lat=lat,
        lon=lon,
    )


def _load_fix_index(path: Path) -> list[_FixLine]:
    out: list[_FixLine] = []
    with path.open() as handle:
        for raw_line in handle:
            stripped = raw_line.rstrip("\n\r")
            entry = _parse_fix_line(stripped)
            if entry is not None:
                out.append(entry)
    return out


def _load_navaid_index(path: Path) -> list[_NavaidLine]:
    out: list[_NavaidLine] = []
    with path.open() as handle:
        for raw_line in handle:
            stripped = raw_line.rstrip("\n\r")
            entry = _parse_navaid_line(stripped)
            if entry is not None:
                out.append(entry)
    return out


def _cifp_fix_requirements(cifp_path: Path) -> dict[str, set[tuple[str, str]]]:
    """Return a dict keyed by identifier; value is the set of (country, kind) pairs.

    `kind` is "fix" for waypoint references, "navaid" for D/N/V records. The
    country code comes from CIFP's region column right after the identifier.
    """
    cifp = report.parse_cifp(cifp_path)
    requirements: dict[str, set[tuple[str, str]]] = defaultdict(set)
    # parse_cifp already aggregates into fix_refs; walk the leg records so we
    # also get the per-reference country codes (subsection is not always set).
    for section, records in cifp["procedureLegRecords"].items():
        for record in records:
            _add_requirement(requirements, record.fix_identifier, record.reference_code_type)
            if record.reference_identifier:
                _add_requirement(
                    requirements,
                    record.reference_identifier,
                    record.reference_code_type,
                )
    return requirements


def _add_requirement(
    requirements: dict[str, set[tuple[str, str]]],
    identifier: str,
    code_type: str | None,
) -> None:
    if not identifier:
        return
    # Runway self-references like RW32 / RW14L are not globally-named fixes;
    # they refer to the enclosing airport's own runway. Skip lookup.
    if len(identifier) >= 3 and identifier.startswith("RW") and identifier[2].isdigit():
        return
    # code_type encodes ICAO area (first two chars) in ARINC 424. Keep the
    # whole string as a disambiguator when present; "?" means no hint.
    country = (code_type or "?").strip()
    kind = "navaid" if country and country not in {"?", ""} and country[0] in {"D", "N", "V"} else "fix"
    requirements[identifier].add((country, kind))


def _pick_geographically_nearest(
    candidates: Iterable[_FixLine | _NavaidLine],
    target: tuple[float, float],
) -> _FixLine | _NavaidLine | None:
    best = None
    best_d = math.inf
    for entry in candidates:
        d = math.hypot(entry.lat - target[0], entry.lon - target[1])
        if d < best_d:
            best_d = d
            best = entry
    return best


def _airport_position_from_earth_aptmeta(path: Path, icao: str) -> tuple[float, float] | None:
    if not path.exists():
        return None
    with path.open() as handle:
        for raw_line in handle:
            parts = raw_line.split()
            if len(parts) < 4 or parts[0] != icao:
                continue
            try:
                return (float(parts[2]), float(parts[3]))
            except ValueError:
                return None
    return None


def extract_airport_cache(
    icao: str,
    navdata_dir: Path,
    cifp_dir: Path,
    output_dir: Path,
) -> dict[str, Path]:
    cifp_path = cifp_dir / f"{icao}.dat"
    earth_fix_path = navdata_dir / "earth_fix.dat"
    earth_nav_path = navdata_dir / "earth_nav.dat"
    aptmeta_path = navdata_dir / "earth_aptmeta.dat"

    if not cifp_path.exists():
        raise FileNotFoundError(f"CIFP for {icao} not found at {cifp_path}")
    if not earth_fix_path.exists():
        raise FileNotFoundError(f"earth_fix.dat missing at {earth_fix_path}")
    if not earth_nav_path.exists():
        raise FileNotFoundError(f"earth_nav.dat missing at {earth_nav_path}")

    airport_position = _airport_position_from_earth_aptmeta(aptmeta_path, icao)
    if airport_position is None:
        raise RuntimeError(
            f"{icao} is not listed in {aptmeta_path}; cannot disambiguate multi-region fix names",
        )

    requirements = _cifp_fix_requirements(cifp_path)
    output_dir.mkdir(parents=True, exist_ok=True)

    fix_entries = _load_fix_index(earth_fix_path)
    navaid_entries = _load_navaid_index(earth_nav_path)

    fix_by_ident: dict[str, list[_FixLine]] = defaultdict(list)
    for entry in fix_entries:
        fix_by_ident[entry.ident].append(entry)
    navaid_by_ident: dict[str, list[_NavaidLine]] = defaultdict(list)
    for entry in navaid_entries:
        navaid_by_ident[entry.ident].append(entry)

    # (1) Everything tagged with airport ICAO as its region = terminal-specific.
    selected_fixes: dict[int, _FixLine] = {}
    selected_navaids: dict[int, _NavaidLine] = {}
    for idx, entry in enumerate(fix_entries):
        if entry.region == icao:
            selected_fixes[idx] = entry
    for idx, entry in enumerate(navaid_entries):
        if entry.region == icao:
            selected_navaids[idx] = entry

    # (2) Every CIFP-referenced identifier. Prefer region==airport_icao, then
    # ENRT with country matching the CIFP hint, then geographically nearest.
    unresolved: list[str] = []
    for identifier in sorted(requirements):
        hints = requirements[identifier]
        # Skip terminal identifiers already captured in pass (1).
        already_have_fix = any(e.ident == identifier for e in selected_fixes.values())
        already_have_navaid = any(e.ident == identifier for e in selected_navaids.values())
        if already_have_fix or already_have_navaid:
            continue

        fix_candidates = fix_by_ident.get(identifier, [])
        navaid_candidates = navaid_by_ident.get(identifier, [])
        if not fix_candidates and not navaid_candidates:
            unresolved.append(identifier)
            continue

        # Disambiguate on country hints first where we have any.
        country_hints = {h[0][:2] for h in hints if h[0] not in {"?", ""}}
        if country_hints:
            fix_candidates = [c for c in fix_candidates if c.country in country_hints] or fix_candidates
            navaid_candidates = [c for c in navaid_candidates if c.country in country_hints] or navaid_candidates

        pick_fix = _pick_geographically_nearest(fix_candidates, airport_position)
        pick_navaid = _pick_geographically_nearest(navaid_candidates, airport_position)
        if isinstance(pick_fix, _FixLine):
            idx = fix_entries.index(pick_fix)
            selected_fixes[idx] = pick_fix
        if isinstance(pick_navaid, _NavaidLine):
            idx = navaid_entries.index(pick_navaid)
            selected_navaids[idx] = pick_navaid

    if unresolved:
        raise RuntimeError(
            f"{icao} CIFP references identifiers not found in X-Plane global data: {unresolved}",
        )

    fixes_out = output_dir / f"{icao}_fixes.dat"
    navaids_out = output_dir / f"{icao}_navaids.dat"

    header_fix = [
        "I",
        "1200 Version - X-Plane earth_fix.dat subset",
        f"# Extracted for {icao} from X-Plane 12 by bin/extract_xplane_airport_cache.py",
        "",
    ]
    header_nav = [
        "I",
        "1200 Version - X-Plane earth_nav.dat subset",
        f"# Extracted for {icao} from X-Plane 12 by bin/extract_xplane_airport_cache.py",
        "",
    ]

    with fixes_out.open("w") as handle:
        handle.write("\n".join(header_fix))
        for idx in sorted(selected_fixes):
            handle.write(selected_fixes[idx].raw + "\n")
        handle.write("99\n")
    with navaids_out.open("w") as handle:
        handle.write("\n".join(header_nav))
        for idx in sorted(selected_navaids):
            handle.write(selected_navaids[idx].raw + "\n")
        handle.write("99\n")

    return {
        "fixes": fixes_out,
        "navaids": navaids_out,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Extract per-airport fix and navaid caches from staged Navigraph cycle data.",
    )
    parser.add_argument("icao", help="Airport ICAO code (e.g. LJMB)")
    parser.add_argument(
        "--navdata-dir",
        type=Path,
        default=None,
        help="Directory holding earth_fix.dat / earth_nav.dat / earth_aptmeta.dat (default: data/navdata/)",
    )
    parser.add_argument(
        "--cifp-dir",
        type=Path,
        default=None,
        help="Directory holding per-airport CIFP files (default: data/cifp/)",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Directory to write per-airport caches (default: data/airac_cache/)",
    )
    args = parser.parse_args()
    root = report.repo_root()
    navdata_dir = args.navdata_dir or (root / "data" / "navdata")
    cifp_dir = args.cifp_dir or (root / "data" / "cifp")
    output_dir = args.output_dir or (root / "data" / "airac_cache")
    result = extract_airport_cache(args.icao, navdata_dir, cifp_dir, output_dir)
    print(f"fixes:   {result['fixes']}")
    print(f"navaids: {result['navaids']}")


if __name__ == "__main__":
    main()
