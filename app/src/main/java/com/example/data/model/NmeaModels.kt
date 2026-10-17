package com.example.data.model

import kotlinx.serialization.Serializable

/**
 * Parsed NMEA 0183 $SDDPT sentence.
 * Format: $--DPT,x.x,x.x,x.x*hh<CR><LF>
 * Field 1: Water depth relative to transducer, in meters
 * Field 2: Offset from transducer in meters (positive = transducer to waterline, negative = transducer to keel)
 * Field 3: Maximum range scale in meters (optional)
 * Checksum: *hh
 */
@Serializable
data class SddptSentence(
  val talker: String = "SD",
  val depthMeters: Double,
  val offsetMeters: Double = 0.0,
  val maxRangeScaleMeters: Double? = null,
  val checksum: String,
  val isChecksumValid: Boolean,
  val rawSentence: String,
  val timestampEpochMs: Long,
  val isoTimestamp: String
) {
  /**
   * Total water depth or depth below keel depending on whether offset is to waterline or keel.
   */
  val correctedDepthMeters: Double
    get() = depthMeters + offsetMeters
}

/**
 * Historical log entry retained for post-cruise hydrographic analysis by NOAA CSDL employees.
 */
@Serializable
data class NmeaLogEntry(
  val id: String,
  val timestampEpochMs: Long,
  val isoTimestamp: String,
  val rawSentence: String,
  val sentenceType: String = "SDDPT",
  val depthMeters: Double,
  val offsetMeters: Double,
  val correctedDepthMeters: Double,
  val sourceDevice: String,
  val isValidChecksum: Boolean,
  val latitude: Double? = null,
  val longitude: Double? = null,
  // Provenance: true when this record came from the built-in simulator or a
  // simulated demo instrument rather than a real connected device.
  val isSimulated: Boolean = false
)

/**
 * NOAA Hydrographic Survey Telemetry Dataset for CSDL Model Validation.
 * Serialized to JSON or CSV for export and download.
 */
@Serializable
data class NoaaSurveyDataset(
  val metadata: NoaaDatasetMetadata,
  val totalRecords: Int,
  val records: List<NmeaLogEntry>
)

@Serializable
data class NoaaDatasetMetadata(
  val vesselName: String,
  val surveyArea: String,
  val organization: String = "NOAA Office of Coast Survey / CSDL",
  val createdIso: String,
  val sounderDevice: String,
  val softwareVersion: String = "NAV-CAST S-100 v1.0 (NOAA CSDL)",
  // Provenance: set by the exporter from the actual records.
  // containsSimulatedData is true if ANY record in the dataset is simulated;
  // per-record truth is NmeaLogEntry.isSimulated.
  val containsSimulatedData: Boolean = false,
  val provenance: String = "UNSPECIFIED"
)

/**
 * Bluetooth SPP Device Information.
 */
data class BluetoothSppDevice(
  val name: String,
  val address: String,
  val isPaired: Boolean = true,
  val isConnected: Boolean = false
)

enum class SppConnectionState {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  STREAMING,
  ERROR
}

/**
 * Classification of marine NMEA 0183 & NMEA 2000 vessel bridge instruments.
 */
enum class VesselInstrumentType(
  val label: String,
  val category: String,
  val typicalSentences: List<String>
) {
  DEPTH_SOUNDER("Depth Sounder / Sonar", "Hydrographic Sounder", listOf("\$SDDPT", "\$SDDBT")),
  AIS_TRANSPONDER("AIS Transponder", "Collision Avoidance", listOf("!AIVDM", "!AIVDO")),
  GPS_RECEIVER("GNSS / GPS Receiver", "Precision Positioning", listOf("\$GPGGA", "\$GPRMC")),
  NMEA_GATEWAY("NMEA Multiplexer / Gateway", "Telemetry Bridge", listOf("\$SDDPT", "\$GPRMC", "!AIVDM")),
  HEADING_SENSOR("Compass / Heading Sensor", "Vessel Attitude", listOf("\$HCHDG", "\$HCHDT")),
  WIND_STATION("Weather / Ultrasonic Anemometer", "Meteorology", listOf("\$WIMWV", "\$WIMDA")),
  OTHER_NMEA("NMEA 0183 Marine Sensor", "General Telemetry", listOf("\$SDDPT"))
}

/**
 * Detailed vessel instrument model discovered via Bluetooth Classic (SPP) / LE scanning.
 */
data class DiscoveredVesselInstrument(
  val id: String,
  val name: String,
  val address: String,
  val type: VesselInstrumentType,
  val rssi: Int = -60,
  val isBonded: Boolean = false,
  val isConnected: Boolean = false,
  val isConnecting: Boolean = false,
  val supportedSentences: List<String> = emptyList(),
  // Provenance: true for the built-in demo instruments, which are NOT real
  // discovered devices. The UI must label these as simulated.
  val isSimulated: Boolean = false,
  val manufacturer: String = "Marine Electronics",
  val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
  val signalBars: Int
    get() = when {
      rssi >= -55 -> 4
      rssi >= -70 -> 3
      rssi >= -85 -> 2
      else -> 1
    }
}

