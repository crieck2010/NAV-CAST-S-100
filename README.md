# NAV-CAST S-100

An Android prototype exploring **IHO S-100 data fusion for marine navigation**:
combining bathymetry (S-102), water levels (S-104), and surface currents (S-111)
into a dynamic under-keel clearance (DUKC) workflow, a pocket datum
transformer, and a hydrodynamic model validator that ingests real echosounder
data over Bluetooth.

> ## ⚠️ Disclaimer — read first
>
> **NOT FOR NAVIGATION.** NAV-CAST S-100 is an experimental research and
> education prototype. It is **not** an ECDIS, is not type-approved under
> SOLAS / IMO performance standards, and must never be used as a primary or
> sole means of navigation, under-keel clearance decision-making, or survey
> of record.
>
> **No affiliation.** This is an independent personal project. It is not
> produced, endorsed, or affiliated with NOAA, the NOAA Office of Coast
> Survey, the Coast Survey Development Laboratory (CSDL), the IHO, or any
> other government agency or company named in the code or UI. References to
> those organizations describe data sources, standards, or the professional
> context the prototype explores — nothing more.
>
> **Data status matters.** Except where explicitly marked live, the data in
> this app is **simulated or procedurally generated** for demonstration
> (see the data-status table below). Bathymetric, water-level, current, and
> clearance values shown by this app may be wrong. Always navigate and survey
> with official, corrected products from an authorized hydrographic office.

## What it does

| Feature | What it is | Status |
|---|---|---|
| **Tactical Map** | MapLibre GL map with S-100-style overlays (soundings, currents, buoys, hazards, anchorages, AIS targets) for Chesapeake Bay, Tampa Bay, and the Houston Ship Channel | Map is real; overlay data is **procedurally generated** |
| **DUKC Calculator** | Dynamic under-keel clearance from charted depth + water level − dynamic draft, with squat estimated by the Barrass formula (confined/open channel factor) and SAFE / CAUTION / DANGER status | Educational model — **not** an S-129 implementation, no uncertainty propagation |
| **Model Validator** | Ingests NMEA 0183 `$SDDPT` depth sentences from an external echosounder over **Bluetooth Classic SPP**, validates checksums, keeps a retained log (2,000 entries), and compares observed depths against model predictions. Exports CSV, raw `.nmea`, and JSON | Real SPP transport, parser, and export pipeline; a clearly-named simulator is built in for hardware-free testing |
| **Pocket VDatum** | Converts heights between NAD83(2011) / WGS84 ellipsoid, NAVD88, MLLW, MHW, and MSL for five US regions | **Approximation** — one constant offset set per region, *not* NOAA VDatum gridded transformations; do not use for survey work |
| **AR Surge** | Camera-overlay concept visualizing storm-surge height and surface-current flow | Simulated visualization concept |
| **Live water level** | Latest observed water level for a NOAA CO-OPS station, fetched from the public CO-OPS API | **Live** (real API). On failure the app falls back to a model value and labels it as not live |

## Data sources & honesty notes

- **Live:** NOAA CO-OPS water-level observations via
  `api.tidesandcurrents.noaa.gov` (public, no key required).
- **Simulated / generated:** S-102 soundings and S-111 current vectors are
  generated in-code on a grid; no S-100 HDF5/GML dataset is parsed yet.
  Bluetooth discovery includes demonstration instruments for UI testing, and
  the NMEA simulator generates synthetic `$SDDPT` sentences. Treat any
  unmarked value as simulated.
- **Approximated:** Pocket VDatum offsets are regional constants, not the
  gridded NOAA VDatum product. DUKC squat is a single-formula estimate.
- **Standards referenced:** IHO S-100 framework and the S-101 / S-102 /
  S-104 / S-111 / S-129 product specifications. This project implements
  concepts from those specifications; it does not claim conformance.

## Tech stack

- Kotlin 2.2 · Jetpack Compose (Material 3) · single-activity Android app
- MapLibre GL Android SDK 11.5 for the tactical map
- Retrofit + Moshi for the NOAA CO-OPS API
- Bluetooth Classic SPP (RFCOMM) + kotlinx.serialization for NMEA ingest,
  log retention, and CSV / NMEA / JSON export
- CameraX for the AR surge view
- Min SDK 24 · Target SDK 36

## Project structure

```
app/src/main/java/com/crieck2010/navcast/
├── data/
│   ├── api/          NOAA CO-OPS Retrofit service
│   ├── model/        Navigation, symbology, and NMEA data models
│   ├── repository/   Survey areas, water-level fetch + fallback
│   └── service/      Bluetooth SPP connectivity (single stack) + NMEA DataService
├── engine/           DukcCalculator, VDatumEngine (pure Kotlin, unit-tested)
└── ui/               Compose screens, ViewModels, HUD and map components
```

## Building

1. Clone this repository.
2. Open it in Android Studio and let Gradle sync (Gradle wrapper properties
   are included; the project was authored with the Android Gradle Plugin
   version catalog in `gradle/libs.versions.toml`).
3. Run the `app` configuration on a device or emulator (API 24+).
   A physical device is needed to test Bluetooth SPP against a real
   echosounder; without one, use the built-in NMEA simulator.
4. Unit and Robolectric tests (16) live under `app/src/test/` and cover the
   DUKC and VDatum engines, the `$SDDPT` parser and checksum validation,
   serialization, log retention, and export formats.

No API keys are required. The `.env.example` Gemini key is an optional
AI Studio artifact and is not needed to build or run the app.

## Roadmap

- [x] Label simulated vs. live data end-to-end, including a provenance flag
      in every export — done 2026-10-17: demo Bluetooth instruments are
      renamed and badged SIM, the silent Bluetooth simulation fallback and
      the auto-start simulation on launch are removed, every NMEA log
      record carries an `isSimulated` flag, and the CSV / raw NMEA / JSON
      exports all state whether simulated data is present
- [x] Consolidate the two Bluetooth SPP implementations into one transport
      — done 2026-10-17: ConnectivityManager is the only SPP stack;
      DataService delegates transport to it
- [ ] Parse `$GPGGA` / `$GPRMC` so logged soundings carry a real position
- [ ] Real S-111 HDF5 ingest from NOAA's AWS Open Data distribution —
      specced 2026-10-17, awaiting build go-ahead:
      [docs/2026-10-17_s111-hdf5-ingest-spec_v0.1.1.md](docs/2026-10-17_s111-hdf5-ingest-spec_v0.1.1.md)
- [ ] Real S-102 (edition 3.0.0) bathymetric surface ingest
- [ ] Move DUKC toward the IHO S-129 under-keel clearance specification,
      including uncertainty

## Version history

- **2026-10-17** — Initial public prototype: tactical map, DUKC calculator,
  Pocket VDatum, AR surge concept, live CO-OPS water levels.
- **2026-10-17** — Bluetooth SPP + NMEA `$SDDPT` ingest, retained log, and
  CSV / NMEA / JSON export added to the Model Validator.
- **2026-10-17** — Hygiene pass: this README, MIT license, repository
  description/topics, and an in-app not-for-navigation disclaimer.
- **2026-10-17** — Provenance pass: simulated data labelled end-to-end
  (SIM demo instruments, map layer labels, per-record export flags);
  silent Bluetooth simulation fallback and launch auto-simulation removed.
- **2026-10-17** — De-branding, SPP consolidation, and package rename:
  exports/defaults/UI no longer present the app as a NOAA/CSDL product
  (factual NOAA data-source credits kept); the duplicate Bluetooth SPP
  stack in DataService was removed in favour of ConnectivityManager;
  the package is now `com.crieck2010.navcast` (was `com.example`;
  applicationId unchanged for AI Studio builds).

## License

MIT — see [LICENSE](LICENSE).

NOAA data are public domain. IHO S-100 product specifications are published
by the International Hydrographic Organization. MapLibre is open source.
This project's use of those names is descriptive only and implies no
endorsement.
