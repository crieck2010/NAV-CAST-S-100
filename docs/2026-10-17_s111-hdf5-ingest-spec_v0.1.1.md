# NAV-CAST S-100 — Real S-111 HDF5 Ingest: Build Specification

| Field | Value |
|---|---|
| Spec version | v0.1.1 (corrected — see revision note at end) |
| Date | 2026-10-17 |
| Status | **Specced — awaiting Charlie's go-ahead to build (phase gate)** |
| Repo | crieck2010/NAV-CAST-S-100 |
| Scope | Replace the procedurally generated S-111 surface currents with real IHO S-111 HDF5 data published by NOAA, for the app's existing survey areas |
| Builder of record | Google AI Studio / Antigravity, working from this spec |
| Reviewer | Muse (spec author) — build is accepted against §11 tests and §12 acceptance criteria only |

---

## 1. Objective

Today the app's S-111 currents are invented: `NavCastRepository` generates a
9×9 procedural grid per survey area. This build makes the currents **real**:
download the NOAA S-111 tile that covers the selected survey area, parse the
HDF5 file on-device, and render the actual model forecast vectors — labelled
in-app with model, cycle, and valid time.

**Done looks like:** select *Chesapeake Bay Entrance* → the map shows CBOFS
surface-current vectors from a real S-111 tile, the HUD/badge reads
`S-111 · CBOFS · cycle 2026-10-17 00Z · valid <time>Z · LIVE MODEL`, and a
unit test proves the parser against a real NOAA tile committed as a fixture.

## 2. Non-goals (explicitly out of scope)

- No S-102 or S-104 ingest (separate specs later). Soundings stay simulated
  and labelled as such.
- No DUKC engine changes. DUKC may *consume* a real current value at a point
  (§8.3) but its formula and thresholds do not change.
- No UI redesign. One badge/label changes; layout does not.
- No regional (whole-model) files. Tiles only (§4.2).
- No silent fallback to simulated data, anywhere (§9). This is a hard rule,
  inherited from the 2026-10-17 SWOT provenance finding.

## 3. Verified source facts (ground truth — inspected 2026-10-17)

Everything in this section was verified by listing the live bucket and
opening real files. Do not re-derive it; build against it.

### 3.1 Distribution

- Bucket: `noaa-s111-pds` (AWS Open Data, anonymous read, no key, no auth).
  Base URL: `https://noaa-s111-pds.s3.amazonaws.com/`
- Key pattern:
  `ed1.0.1/model_forecast_guidance/<model>/<yyyy>/<MM>/<dd>/<hh>/dcf2/tiles/111US00_<MODEL>_<yyyyMMdd>T<hh>Z_<TILE>.h5`
  plus a `regional/` full-model file beside `tiles/` (~12 MB — never download).
- Tiles are ~240–660 KB each. CBOFS publishes 60 tiles/cycle; TBOFS 15.
- **Cycle hours differ per model** — CBOFS runs at 00Z in the sampled data,
  NGOFS2 at 03Z. Never hard-code cycle hours; discover them by listing (§6).
- Models available (verified): `cbofs, dbofs, gomofs, leofs, lmhofs, loofs,
  lsofs, ngofs2, nyofs, rtofs_east, rtofs_west, sfbofs, tbofs, wcofs`.

### 3.2 App-area → model mapping

| App survey area | Centre | Model | Verified |
|---|---|---|---|
| Chesapeake Bay Entrance (Thimble Shoal) | 36.985, −76.110 | `cbofs` | ✅ tile found, §3.4 |
| Tampa Bay (Egmont Channel & Skyway) | 27.600, −82.720 | `tbofs` | ✅ model + tiles listed; covering tile to confirm in T1 |
| Houston Ship Channel (Galveston Entrance) | 29.360, −94.780 | `ngofs2` | ✅ model listed, 03Z cycles, TX tiles to enumerate in T1 |

Note: the in-app model names are partly wrong today (CBOFS is ROMS-based,
not FVCOM as the UI string says). Correct the strings as part of T5.

### 3.3 File anatomy (S-111 edition 1.0 — `INT.IHO.S-111.1.0`)

Verified on `111US00_CBOFS_20260916T00Z_US4DE1DA.h5` (Delaware Bay tile):

- **Root attributes:** `productSpecification = INT.IHO.S-111.1.0`,
  `horizontalDatumValue = 4326` (EPSG), `epoch = G1762`,
  `geographicIdentifier`, bounding box attrs (W/E/S/N),
  `surfaceCurrentDepth = -4.5` (metres — currents are at 4.5 m depth or
  half the water column, whichever is shallower), `issueDate`, `issueTime`,
  `metadata` (companion XML filename).
- **`/Group_F/SurfaceCurrent`** — compound dataset defining the fields:
  `surfaceCurrentSpeed` with `uom.name = knots`, `fillValue = -9999.0`;
  `surfaceCurrentDirection` with `uom.name = arc-degrees`,
  `fillValue = -9999.0`, valid range 0–360.
  → **Speeds are already in knots.** The app's `S111CurrentVector.speedKnots`
  needs *no* unit conversion. Do not add one.
  → **Read the fill value from this table; do not hard-code −9999.**
- **`/SurfaceCurrent`** group attrs: `dataCodingFormat = 2`,
  `dimension = 2`, `numInstances = 1`, `min/maxDatasetCurrentSpeed`,
  `methodCurrentsProduct` (e.g. `ROMS_Hydrodynamic_Model_Forecasts`),
  uncertainty attrs are −1.0 (unset).
- **`/SurfaceCurrent/SurfaceCurrent.01`** instance attrs:
  `gridOriginLatitude/Longitude` (SW corner), `gridSpacingLatitudinal ≈
  0.005558°`, `gridSpacingLongitudinal ≈ 0.005554°` (~600 m cells),
  `numPointsLatitudinal = 54`, `numPointsLongitudinal = 54`,
  `numberOfTimes = 48`, `timeRecordInterval = 3600` (s),
  `dateTimeOfFirstRecord`, `dateTimeOfLastRecord`,
  `sequencingRule.scanDirection = longitude,latitude`, `startSequence = 0,0`.
- **`/SurfaceCurrent/SurfaceCurrent.01/Group_NNN/values`** for NNN = 001…048:
  each group carries a `timePoint` attribute (`20260916T010000Z` format) and
  one compound dataset `values`, shape (54, 54), fields
  (`surfaceCurrentSpeed` f4, `surfaceCurrentDirection` f4).
  One file = 48 hourly forecast steps.
- ** Fill dominates:** in the Delaware Bay tile, 2,860 of 2,916 cells are
  fill (land / outside model). Fill masking is not an edge case; it is the
  main case. The renderer must never draw a fill cell.

### 3.4 Golden fixture (verified values for tests)

`111US00_CBOFS_20261017T00Z_US4VA1BF.h5` (670,347 bytes) — the CBOFS tile
covering the app's Chesapeake area centre:

- Bounds: W −76.2, E −75.9, S 36.9, N 37.2; grid 54×54; 48 time groups.
- First record `20261017T010000Z`, last `20261019T000000Z`, interval 3600 s.
- Group_001: exactly **2,403 valid (non-fill) cells** of 2,916;
  max valid speed **1.67 knots**.
- The area centre cell (row 15, col 16 from the SW origin) is valid water
  in Group_001: **0.61 knots toward 135.3°**. Cell (0, 0) — the SW corner
  at 36.900, −76.200 — is fill. Tests use both.

## 4. Architecture

Follow the repo's existing layering. All new code in
`app/src/main/java/com/crieck2010/navcast/data/s111/` unless stated.

```
data/s111/
├── S111Catalog.kt       // S3 listing: discover latest cycle + tile keys for a model
├── S111TileRegistry.kt  // static per-model tile bounds (asset JSON, §6.2)
├── S111Downloader.kt    // download + on-device cache (cacheDir), size/TTL guards
├── S111Hdf5Reader.kt    // HDF5 → S111Grid (pure JVM-testable, no Android imports)
├── S111Grid.kt          // parsed grid model: metadata + per-time-step fields
└── S111Repository.kt    // orchestration: catalog → download → parse → sample
```

Integration points (only these):

- `NavCastRepository` — gains `currentsSource` per area: when an S-111 grid
  is loaded for the selected area, its sampled vectors replace the generated
  ones *for that area only*, and the area's provenance flag flips to
  `LIVE_MODEL`. Generated currents remain for unloaded areas, labelled
  `SIMULATED`.
- `S111CurrentVector` — unchanged fields (the model already carries
  lat/lon/speedKnots/directionDeg). Provenance lives on the *dataset*, not
  per-vector (§9).
- `NavCastViewModel` — one new state block: `s111Status` (Idle / Loading /
  Loaded(cycle, validTime, tileId) / Error(message)) + `refreshS111()` trigger
  on area selection.
- Tactical map / HUD — one badge rendering `s111Status`. No other UI change.

### 4.1 Engine purity rule

`S111Hdf5Reader` and `S111Grid` must have **zero Android imports** (same rule
as `engine/`). Input is a `java.io.File`; output is plain Kotlin data. This
is what makes §11 JVM tests possible without an emulator.

## 5. Library decision — HDF5 reader

**Decision: jHDF `io.jhdf:jhdf:0.13.0`** (latest on Maven Central, verified
2026-10-17). Pure-Java HDF5 reader written from the file spec — no JNI, no
per-ABI native libraries, read-only (all we need). Supports compound
datasets and chunked/deflate storage, both present in NOAA tiles.

Rejected, with reasons:

- *Official HDF Group Java bindings (JHI5 / sis-jhdf5)* — JNI wrappers
  around the native C library; per-ABI `.so` packaging on Android is a
  maintenance and APK-size liability for a read-only use case.
- *netCDF-Java* — can read HDF5, but pulls a large dependency tree for one
  file format and abstracts away the S-111 structure we need to validate.

**Risk + spike gate (T0):** jHDF's compound-dataset support on Android must
be proven before anything else is built. T0 is a throwaway spike: add the
dependency, open the golden fixture, read `Group_001/values`, print the
valid-cell count. **Pass = 2,403.** If jHDF fails on the compound dataset or
the chunked layout, STOP and report back — do not silently substitute a
library; the fallback decision returns to Charlie.

Version-catalog entries required (`gradle/libs.versions.toml` +
`app/build.gradle.kts`), following the file's existing conventions:

```toml
jhdf = "0.13.0"
jhdf = { group = "io.jhdf", name = "jhdf", version.ref = "jhdf" }
```

## 6. Discovery: finding the latest tile for an area

### 6.1 Cycle discovery (no hard-coded hours)

For model `<m>` and today (UTC):

1. `GET https://noaa-s111-pds.s3.amazonaws.com/?list-type=2&prefix=ed1.0.1/model_forecast_guidance/<m>/<yyyy>/<MM>/<dd>/`
2. From returned keys, collect distinct `<hh>` segments that contain a
   `tiles/` key. Take the **latest** `<hh>`.
3. If today yields nothing, step back one day at a time, max 2 days, then
   fail with `Error("No S-111 cycle found for <model> in last 48 h")`.
4. The `ed1.0.1` path segment is a constant for v1 but must live in exactly
   one place (`S111Catalog.EDITION_PATH`) so an edition change is a
   one-line fix.

Use OkHttp (already a dependency) and a small hand-rolled XML key extractor;
do not add an AWS SDK.

### 6.2 Tile selection (static registry, generated once)

Tile filenames (`…_US4VA1BF`) carry no coordinates, and probing every tile
header at runtime is wasteful. Therefore:

- **T1 (one-time, off-device):** a Python script (`tools/build_s111_registry.py`,
  h5py — dev-machine only, never shipped) lists one cycle for `cbofs`,
  `tbofs`, `ngofs2`, downloads each tile header's root attributes, and emits
  `app/src/main/assets/s111_tiles.json`:
  `{ model, tileId, west, east, south, north }[]`.
- Verified first entry (must appear in the output):
  `cbofs / US4VA1BF / W −76.2 E −75.9 S 36.9 N 37.2`.
- Runtime selection: tiles whose bounds intersect the survey area's bounding
  box (area centre ± 0.15° for v1). For Chesapeake v1 this resolves to
  exactly `US4VA1BF`. If several intersect, pick the one containing the
  centre; if none contains it, pick the nearest by centre distance and
  surface that fact in `s111Status` (no silent substitution).

## 7. Parsing specification (`S111Hdf5Reader`)

Read, in order, and fail loudly (`S111ParseException` with a specific
message) on any mismatch — never return a partial grid as if valid:

1. Root attrs → verify `productSpecification` starts with `INT.IHO.S-111`
   (accept 1.x; log — don't reject — anything newer). Capture bounds,
   `surfaceCurrentDepth`, `geographicIdentifier`.
2. `/Group_F/SurfaceCurrent` → build a field table: for each of
   `surfaceCurrentSpeed` / `surfaceCurrentDirection` capture `uom.name`
   and `fillValue`. **Assert speed uom == "knots"**; if a future NOAA
   product ships m/s, this assert is the tripwire that stops wrong units
   reaching the map. Parse fill values from the strings in this table.
3. `/SurfaceCurrent/SurfaceCurrent.01` attrs → grid geometry (origin,
   spacings, counts), `numberOfTimes`, time interval.
4. Each `Group_NNN` → `timePoint` parsed as `yyyyMMdd'T'HHmmss'Z'` (UTC);
   `values` compound dataset read via jHDF; both fields extracted to
   `FloatArray`s in row-major order exactly as stored.
5. Output model:

```kotlin
data class S111Grid(
  val tileId: String, val model: String, val cycleIso: String,
  val west: Double, val east: Double, val south: Double, val north: Double,
  val rows: Int, val cols: Int,
  val spacingLat: Double, val spacingLon: Double,
  val depthMeters: Double,            // -4.5 in current NOAA products
  val speedFill: Float, val directionFill: Float,
  val steps: List<S111Step>           // 48 entries, ordered by time
)
data class S111Step(
  val validTimeIso: String,
  val speedKnots: FloatArray,         // rows*cols, fill preserved
  val directionDeg: FloatArray
)
```

6. **Orientation is a verification task, not an assumption.** Given
   `gridOrigin` at the SW corner, `scanDirection = longitude,latitude`, and
   `startSequence = 0,0`, row 0 is expected to be the *southernmost* row and
   column 0 the westernmost. T3 must include a test that proves this against
   geography: the valid-cell mask of the fixture must match the Chesapeake
   Bay water shape (e.g. valid cells concentrated in the bay/channel, land
   to the northwest of the tile) — if the mask comes out flipped, fix the
   indexing in the reader, not the test. The lat/lon of cell (r, c) is:
   `lat = originLat + r * spacingLat`, `lon = originLon + c * spacingLon`.
7. **Direction convention:** S-111 direction is the direction the current
   flows **toward**, in degrees clockwise from true north. The existing
   renderer (`MapLibreNauticalMap.drawS111Currents`) converts
   `directionDeg` to screen angle — T5 must verify arrow orientation on a
   known ebb/flood step rather than assuming the current math accounts for
   screen y-down. Document the confirmed convention in the reader's KDoc.

## 8. Sampling: grid → what the app draws

### 8.1 Map vectors

The renderer consumes `S111CurrentVector(x, y, lat, lon, speedKnots,
directionDeg)` with x/y normalized 0..1 within the *survey area* bounds.

- From the selected step's 54×54 grid, take cells inside the area bounds,
  skip fill, stride to at most ~12×12 = 144 vectors (stride 4 or 5 on a
  54-grid), map each surviving cell to normalized x/y from its real lat/lon.
- Speed/direction pass through unchanged (knots, degrees-toward).

### 8.2 Time selection

- Default step = the group whose `timePoint` is nearest to *now* (UTC).
- Expose `stepIndex` in the ViewModel so a later build can add a time
  slider; v1 UI shows the valid time in the badge and does not add a slider.

### 8.3 Current at a point (for HUD/DUKC display)

`fun currentAt(grid, step, lat, lon): Pair<Float, Float>?` — nearest
non-fill cell within one grid spacing diagonal; `null` over land/fill.
Nearest-neighbour only in v1 (no bilinear interpolation across a fill
boundary — that would invent water where there is none).

## 9. Provenance & honesty requirements (non-negotiable)

Inherited from the SWOT: this build must *reduce* the app's claim-vs-code
gap, never widen it.

1. Every loaded grid carries provenance: model, cycle, tile, valid time,
   source URL. The badge renders it; `s111Status.Loaded` cannot exist
   without it.
2. **No silent fallback.** Any failure (no cycle, download error, parse
   error, no covering tile) → `s111Status.Error` with a human-readable
   message, and the map keeps showing the *simulated* vectors only if they
   were already on screen, still labelled SIMULATED. A failed real load
   must never masquerade as a successful one, and a simulated stream must
   never be presented as a device/model connection (the exact defect the
   Bluetooth update introduced — do not repeat the pattern here).
3. Simulated and live-model currents must be visually distinguishable in
   the layer label: `S-111 Currents — SIMULATED` vs
   `S-111 Currents — CBOFS (LIVE MODEL)`.
4. README data-status table is updated in the same commit series: the
   Tactical Map row's currents become "Live model (S-111 HDF5)" for areas
   with a loaded tile, with the simulation note retained for the rest.

## 10. Caching, size, performance

- Cache: `context.cacheDir/s111/<model>_<cycle>_<tile>.h5`; reuse if the
  discovered latest cycle matches a cached file. Keep at most the newest
  file per model (delete older cycles on successful load).
- Guards: refuse downloads > 2 MB (tiles are ≤ ~0.7 MB; a bigger response
  means the regional file or an error page — abort). Verify downloaded
  size > 0 and first bytes are a valid HDF5 signature (`\x89HDF`) before
  parsing.
- All network + parse work off the main thread (`Dispatchers.IO`), parse of
  a 54×54×48 compound file is small (≈ 2.3 M floats total) — no streaming
  parser, no paging.
- No new permissions. No API keys. No AWS SDK.

## 11. Test plan (acceptance tests — all must pass)

Fixtures committed under `app/src/test/resources/s111/` (binary files in
git are accepted for this project — see decision D2):

- `111US00_CBOFS_20261017T00Z_US4VA1BF.h5` (golden fixture, §3.4)
- `111US00_CBOFS_20260916T00Z_US4DE1DA.h5` (second tile, guards against
  single-tile overfitting)

**Parser golden tests (JVM, `S111Hdf5ReaderTest`):**

1. Metadata: bounds exactly W −76.2 / E −75.9 / S 36.9 / N 37.2;
   rows = cols = 54; `steps.size == 48`; depth −4.5.
2. Units/fill: speed fill == −9999f parsed from Group_F (not a literal in
   the test's expectation of the *reader constant* — assert the reader's
   parsed value); reader rejects a fixture mutated to m/s units (build the
   mutation in-test only if jHDF write support exists — otherwise this is
   a code-review checkpoint, marked as such).
3. Time: step 0 valid time `20261017T010000Z`, step 47
   `20261019T000000Z`, strictly increasing by 3600 s.
4. Fill masking: Group_001 (step 0) valid-cell count == **2,403**;
   max valid speed == 1.67 kn ± 0.01.
5. Orientation: valid mask matches the stored geography per §7.6 (assert
   a known water cell is valid — the area centre cell (row 15, col 16)
   reads 0.61 kn toward 135.3° — and a known land cell, (0, 0) at the SW
   corner, is fill, in step 0).
6. `currentAt` at a known water coordinate returns that cell's
   speed/direction (centre cell: 0.61 kn, 135.3°); at the SW-corner
   coordinate (36.900, −76.200, a fill cell) returns null.

**Catalog tests:** recorded S3 listing XML (text fixture) → parser yields
the expected latest cycle for CBOFS (00Z sample) and NGOFS2 (03Z sample —
this is the regression test for the hard-coded-hour trap).

**Repository tests:** sampled vector count ≤ 144, all vectors non-fill,
x/y within 0..1, provenance fields populated; failure path yields
`Error` status and zero live vectors (never simulated-in-disguise).

**Spike test (T0):** jHDF opens the golden fixture on JVM *and* in an
instrumented/Robolectric run, reads the compound dataset, count == 2,403.
If this fails, the build stops at T0 per §5.

## 12. Build sequence (each task = one check-in; stop at the gate)

| Task | Work | Acceptance |
|---|---|---|
| **T0 Spike** | jHDF dependency + throwaway read of golden fixture | Valid-cell count 2,403 printed on JVM and Android; GO/NO-GO reported before T1 starts |
| **T1 Registry** | `tools/build_s111_registry.py` + `s111_tiles.json` for cbofs/tbofs/ngofs2; covering tiles for all 3 area centres identified | JSON contains US4VA1BF with §3.4 bounds; Tampa/Houston covering tiles named in the T1 report |
| **T2 Reader** | `S111Grid`, `S111Hdf5Reader` per §7 | All §11 parser golden tests pass |
| **T3 Orientation** | §7.6 geography test + KDoc the confirmed indexing/direction conventions | Orientation test passes without flipping the fixture expectation |
| **T4 Catalog + download** | `S111Catalog`, `S111Downloader`, cache guards per §6/§10 | Catalog tests pass; manual run downloads today's CBOFS tile for Chesapeake |
| **T5 Integration** | `S111Repository`, ViewModel state, badge, layer labels, provenance per §9; fix OFS model-name strings (§3.2) | Selecting Chesapeake shows real vectors + LIVE MODEL badge; failure drill (airplane mode) shows Error + SIMULATED label, never a fake success |
| **T6 Docs** | README data-status update (§9.4), CHANGELOG entry in README version history, spec status → Shipped | Docs in the same PR/series as T5 |

## 13. Risks & known unknowns

- **NOAA edition drift:** products are S-111 ed 1.0 under an `ed1.0.1`
  path today; NOAA has already shown with S-102 (2.1 → 3.0) that editions
  turn over. The single-constant rule (§6.1.4) and the product-spec assert
  (§7.2) are the mitigations. A future ed 2.x is a new spec, not a patch.
- **jHDF on Android:** pure Java but not Android-targeted; T0 exists
  precisely to burn this risk down first, cheaply.
- **Tile set stability:** ENC tile names/bounds can change when NOAA
  re-tiles. The registry is an asset, not a truth — T1's script is the
  regeneration path, and runtime selection (§6.2) tolerates a missing
  centre tile by surfacing it, not hiding it.
- **Model skill:** CBOFS/TBOFS/NGOFS2 are forecast guidance, not
  observations. The badge says LIVE MODEL, never LIVE — wording matters
  and is specified deliberately.

## 14. Decisions needed from Charlie (gate — build does not start without these)

- **D1 — Library:** approve jHDF 0.13.0 (pure Java, read-only) per §5,
  with the T0 spike as the kill switch.
- **D2 — Fixtures:** approve committing two real NOAA tiles (~0.91 MB
  total) as test resources in the public repo. (NOAA data are public
  domain; this is a repo-size choice, not a licensing one.)
- **D3 — Scope of v1:** all three areas (registry covers cbofs/tbofs/
  ngofs2) *or* Chesapeake-only first, Tampa/Houston in v1.1. Spec is
  written for all three; Chesapeake is the acceptance area either way.
- **D4 — Refresh behaviour:** auto-load on area selection (spec default)
  vs. manual refresh button only.
- **D5 — Registry:** static asset generated by the T1 script (spec
  default) vs. runtime tile-header probing (slower, more code, always
  current).

---

## Appendix A — Sample verified keys (2026-10-17)

```
ed1.0.1/model_forecast_guidance/cbofs/2026/10/17/00/dcf2/tiles/111US00_CBOFS_20261017T00Z_US4VA1BF.h5   (670,347 B — golden fixture)
ed1.0.1/model_forecast_guidance/cbofs/2026/09/16/00/dcf2/tiles/111US00_CBOFS_20260916T00Z_US4DE1DA.h5   (243,456 B — second fixture)
ed1.0.1/model_forecast_guidance/cbofs/2026/10/17/00/dcf2/regional/111US00_CBOFS_20261017T00Z.h5          (~12 MB — never download)
ed1.0.1/model_forecast_guidance/ngofs2/2026/10/01/03/dcf2/tiles/…                                        (03Z cycles — see §3.1)
```

## Appendix B — What this spec deliberately does not fix

The package rename, the duplicated Bluetooth SPP stacks, the silent
Bluetooth simulation fallback, and export provenance flags were tracked
separately from the 2026-10-17 SWOT — and were all fixed on 2026-10-17,
after this spec was written (see the amendment below). CI remains open.
This build touches none of them except where §9's no-silent-fallback
rule sets the pattern.

**Amendment (2026-10-17, later the same day):** the application package
was renamed from `com.example` to `com.crieck2010.navcast`. Every
`com/example/…` source path in this spec — including the new
`data/s111/` package in §6 — now lives under
`app/src/main/java/com/crieck2010/navcast/…`. No other part of this
spec changes.

---

## Revision note — v0.1.1 (2026-10-17, same day)

**Correction.** v0.1.0 conflated two CBOFS tiles. The golden fixture was
cited as `US4VA1BD` with bounds W −76.2 / E −75.9 — but those bounds belong
to **`US4VA1BF`**, and the 550 valid-cell / 1.03 kn figures were measured on
`US4VA1BD` (bounds W −76.8 / E −76.5, the James River tile, which does not
cover the app area centre). Cause: a summary line from the tile scan was
trusted over the per-file attribute dump. v0.1.1 cites `US4VA1BF`
throughout, with values re-measured directly from that file (2,403 valid
cells in Group_001, max 1.67 kn, centre cell 0.61 kn toward 135.3°), and
the fixture total in D2 rises to ~0.91 MB accordingly. No other section
changed.
