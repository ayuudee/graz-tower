# LOWG -- Graz Airport

Primary test airport. Complex enough to exercise most features.

## Physical layout (apt.dat)

- **3 runways:** 16C/34C (3000m, asphalt, 45m wide, main), 16R/34L (grass, 25m), 16L/34R (grass, 30m)
- **Displaced thresholds:** 16C has 260m displaced threshold
- **6 helipads:** H1-H6
- **96 taxi nodes**, **75 taxi edges**, **86 active zones**
- **~60 stands** (mostly tie-down, some gates)
- **26 taxi signs**, **3 lighting objects** (PAPI for 16C/34C)
- **Tower viewpoint** at record 14
- **ATC flows:** Northern Flow (wind 080-260) and Southern Flow (wind 260-080), each with runway assignments for all 3 runway pairs
- **Pattern runway:** 16C left-hand (from record 1101)

## Operational data (OFMX)

- **Elevation:** 1120ft
- **Magnetic variation:** 5 degrees (2022)
- **Transition altitude:** 10,000ft
- **CTR:** Class D, SFC-4500ft AMSL (code LO585)
- **TMA layers:** LOWG 1-5 with Class C/D/E segments up to FL245
- **Frequencies:** GRAZ TOWER 118.200, GRAZ RADAR 119.300/120.440, ATIS 126.130
- **VFR reporting points:** GRAZ-NORD (MRP), SENDER DOBL, KALSDORF, AUTOBAHN-OST, AUTOBAHN-WEST, GREEN CITY (all RP), GLEISDORF (MRP), LASSNITZHOEHE (MRP)
- **Navaids:** GRZ VOR/DME (116.20)

## IFR procedures (CIFP)

- **SIDs (21):** ABIR3V/4G/5U, GBG5Y/7X, GOLV4U/5G, GOTA5G/5U, GRZ4X/4Y, MILG3H/5U/6G, MURE4U/5G, RADL4V/5G/6U, ROPA4G/4U -- for RW16C and RW34C
- **STARs (8):** ABIR1M, GBG1M, GOLV1M, GOTA2M, LEOB1M, MURE1M, RADL2M, RUPE2M -- all coded for ALL runways
- **Approaches (5 types):** D16C (VOR/DME), D34C (VOR/DME), I34C (ILS cat III), R16C (RNAV), R34C (RNAV) -- with multiple approach transitions each
- **ILS:** OEG localizer on 34C, cat III
- **Runway thresholds:** 16C at N47000722/E015261181 elev 1117ft, 34C at N46584003/E015263581 elev 1088ft

## Known gaps

- **Circuit geometry:** Not in any downloadable data source. apt.dat gives direction (left-hand for 16C). OFM renders accurate circuits in their tiles but geometry is not exported. Needs hand-authoring.
- **VFR routes:** OFMX has the reporting points but not the route geometry connecting them. OFM map shows routes labelled PRC-1 through PRC-5 (PRC-4/5 appear to be the circuit patterns). Route geometry needs hand-authoring.
- **IFR fix positions:** Some CIFP waypoints (PIBIP, XIBAR, RONOT) not found in the 2017 earth_fix.dat. Need current version or manual lookup.
