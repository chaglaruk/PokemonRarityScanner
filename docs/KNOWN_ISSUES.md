# Known Issues

Issues confirmed during this managed run:

## Confirmed
- ~~OCR text varies across device density, font rendering, and capture timing~~ — mitigated by multi-attempt OCR (CP: 3 strategies, name: dynamic+static, candy: 2 regions)
- ~~Repeated frames amplify stale or partially parsed values~~ — mitigated by `ScanConsistencyGate` with authoritative anchor enforcement
- ~~Telemetry and scan debug artifacts must remain app-private~~ — verified: app cache only, 20-file cap, privacy tests exist

## Observed (not yet fixed)
- `SpeciesRefiner` (417 lines) has 15+ override conditions — high mutation risk; recommend expanding test coverage
- `TextParser` (913 lines) hardcodes fallback pokemon list as emergency fallback — could be stale if names JSON is corrupt
- No test for empty `pokemonNames` list in `TextParser.rankNameCandidates`

## Validate Next Run
- `ScanFrameFusion.shouldRunDetailedPass` dead code removed — verify tests pass
- Boundary tests added — verify they cover 0.55 CP quality and 0.86 text confidence
