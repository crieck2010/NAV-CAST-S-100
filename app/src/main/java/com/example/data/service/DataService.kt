package com.example.data.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.model.BluetoothSppDevice
import com.example.data.model.DiscoveredVesselInstrument
import com.example.data.model.NmeaLogEntry
import com.example.data.model.NoaaDatasetMetadata
import com.example.data.model.NoaaSurveyDataset
import com.example.data.model.SddptSentence
import com.example.data.model.SppConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DataServiceStats(
  val totalPacketsReceived: Int = 0,
  val validSddptCount: Int = 0,
  val checksumErrors: Int = 0,
  val averageDepthMeters: Double = 0.0,
  val minDepthMeters: Double = 0.0,
  val maxDepthMeters: Double = 0.0,
  val lastPingIso: String = "None"
)

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
  CSV("csv", "text/csv", "Hydrographic CSV (.csv)"),
  RAW_NMEA("nmea", "text/plain", "Raw NMEA Stream (.nmea)"),
  JSON("json", "application/json", "NAV-CAST JSON (.json)")
}

/**
 * Service to parse incoming NMEA 0183 strings ($SDDPT) from external GPS/depth sounders
 * via Bluetooth SPP (Serial Port Profile) using Kotlin Serialization, and maintain a
 * persistent historical log for post-cruise analysis.
 *
 * Transport note (consolidated 2026-10-17): this class no longer opens Bluetooth
 * sockets itself. All SPP transport lives in [ConnectivityManager] — the single
 * stack — which delivers sentences through its onSentenceReceived callback.
 * DataService keeps parsing, the retained log, statistics, and exports.
 */
class DataService(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
  companion object {
    const val RAW_LOG_FILENAME = "noaa_raw_nmea_sddpt.log"
    const val RETAINED_JSON_FILENAME = "noaa_sddpt_retained.json"
    private const val MAX_MEMORY_LOG_ENTRIES = 2000
  }

  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private var simulationJob: Job? = null

  private val _connectionState = MutableStateFlow(SppConnectionState.DISCONNECTED)
  val connectionState: StateFlow<SppConnectionState> = _connectionState.asStateFlow()

  private val _connectedDeviceName = MutableStateFlow("None")
  val connectedDeviceName: StateFlow<String> = _connectedDeviceName.asStateFlow()

  private val _latestSentence = MutableStateFlow<SddptSentence?>(null)
  val latestSentence: StateFlow<SddptSentence?> = _latestSentence.asStateFlow()

  private val _rawLogs = MutableStateFlow<List<NmeaLogEntry>>(emptyList())
  val rawLogs: StateFlow<List<NmeaLogEntry>> = _rawLogs.asStateFlow()

  private val _stats = MutableStateFlow(DataServiceStats())
  val stats: StateFlow<DataServiceStats> = _stats.asStateFlow()

  private val _isSimulationActive = MutableStateFlow(false)
  val isSimulationActive: StateFlow<Boolean> = _isSimulationActive.asStateFlow()

  private val logFile: File = File(context.filesDir, RAW_LOG_FILENAME)
  private val jsonFile: File = File(context.filesDir, RETAINED_JSON_FILENAME)

  val connectivityManager: ConnectivityManager = ConnectivityManager(
    context = context,
    scope = scope,
    onSentenceReceived = { rawSentence, instrument ->
      ingestSentence(
        rawSentence = rawSentence,
        sourceDevice = "${instrument.type.label}: ${instrument.name}",
        isSimulated = instrument.isSimulated
      )
    }
  )

  init {
    loadRetainedLogs()
    scope.launch {
      connectivityManager.connectionState.collect { connState ->
        _connectionState.value = connState
      }
    }
    scope.launch {
      connectivityManager.connectedInstrument.collect { inst ->
        _connectedDeviceName.value = inst?.name ?: "None"
      }
    }
  }

  // ==========================================
  // NMEA 0183 PARSER ($SDDPT)
  // ==========================================

  /**
   * Parses an NMEA 0183 sentence, validating and extracting $SDDPT depth data.
   * Format: $--DPT,x.x,x.x,x.x*hh
   * Example: $SDDPT,14.85,0.0,100.0*53
   */
  fun parseSddpt(raw: String): SddptSentence? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("$") || !trimmed.contains("DPT")) {
      return null
    }

    val asteriskIndex = trimmed.indexOf('*')
    val hasChecksum = asteriskIndex != -1
    val sentenceContent = if (hasChecksum) trimmed.substring(1, asteriskIndex) else trimmed.substring(1)
    val providedChecksum = if (hasChecksum && asteriskIndex + 1 < trimmed.length) {
      trimmed.substring(asteriskIndex + 1).trim()
    } else {
      ""
    }

    val calculatedChecksum = computeNmeaChecksum(sentenceContent)
    val isChecksumValid = if (hasChecksum && providedChecksum.isNotEmpty()) {
      calculatedChecksum.equals(providedChecksum, ignoreCase = true)
    } else {
      false
    }

    val fields = sentenceContent.split(",")
    if (fields.isEmpty()) return null

    val header = fields[0]
    val talker = if (header.length >= 3) header.substring(0, header.length - 3) else "SD"

    // Field 1: Water depth relative to transducer in meters
    val depthMeters = fields.getOrNull(1)?.toDoubleOrNull() ?: return null

    // Field 2: Offset from transducer in meters (optional, default 0.0)
    val offsetMeters = fields.getOrNull(2)?.toDoubleOrNull() ?: 0.0

    // Field 3: Max range scale in meters (optional)
    val maxRange = fields.getOrNull(3)?.toDoubleOrNull()

    val epochNow = System.currentTimeMillis()
    val isoTimestamp = formatIsoTimestamp(epochNow)

    return SddptSentence(
      talker = if (talker.isEmpty()) "SD" else talker,
      depthMeters = depthMeters,
      offsetMeters = offsetMeters,
      maxRangeScaleMeters = maxRange,
      checksum = providedChecksum.ifEmpty { calculatedChecksum },
      isChecksumValid = isChecksumValid,
      rawSentence = trimmed,
      timestampEpochMs = epochNow,
      isoTimestamp = isoTimestamp
    )
  }

  /**
   * Computes 8-bit XOR checksum between '$' and '*' per NMEA 0183 standard.
   */
  fun computeNmeaChecksum(sentenceWithoutDollarOrStar: String): String {
    var checksum = 0
    for (char in sentenceWithoutDollarOrStar) {
      checksum = checksum xor char.code
    }
    return String.format(Locale.US, "%02X", checksum)
  }

  /**
   * Generates a fully formatted, valid NMEA 0183 $SDDPT sentence.
   */
  fun formatSddptSentence(depthMeters: Double, offsetMeters: Double = 0.0, talker: String = "SD"): String {
    val body = String.format(Locale.US, "%sDPT,%.2f,%.1f", talker, depthMeters, offsetMeters)
    val checksum = computeNmeaChecksum(body)
    return "\$$body*$checksum"
  }

  // ==========================================
  // KOTLIN SERIALIZATION HELPERS
  // ==========================================

  fun serializeSentenceToJson(sentence: SddptSentence): String {
    return json.encodeToString(SddptSentence.serializer(), sentence)
  }

  fun deserializeSentenceFromJson(jsonStr: String): SddptSentence? {
    return try {
      json.decodeFromString(SddptSentence.serializer(), jsonStr)
    } catch (_: Exception) {
      null
    }
  }

  fun serializeDatasetToJson(dataset: NoaaSurveyDataset): String {
    return json.encodeToString(NoaaSurveyDataset.serializer(), dataset)
  }

  fun deserializeDatasetFromJson(jsonStr: String): NoaaSurveyDataset? {
    return try {
      json.decodeFromString(NoaaSurveyDataset.serializer(), jsonStr)
    } catch (_: Exception) {
      null
    }
  }

  // ==========================================
  // LOG RETENTION & PERSISTENCE
  // ==========================================

  /**
   * Ingests, parses, and permanently retains an NMEA 0183 string.
   */
  fun ingestSentence(
    rawSentence: String,
    sourceDevice: String = _connectedDeviceName.value,
    lat: Double? = null,
    lon: Double? = null,
    isSimulated: Boolean = false
  ): NmeaLogEntry? {
    val parsed = parseSddpt(rawSentence)
    _stats.update { s ->
      s.copy(
        totalPacketsReceived = s.totalPacketsReceived + 1,
        validSddptCount = s.validSddptCount + (if (parsed != null && parsed.isChecksumValid) 1 else 0),
        checksumErrors = s.checksumErrors + (if (parsed != null && !parsed.isChecksumValid) 1 else 0)
      )
    }

    if (parsed == null) return null

    _latestSentence.value = parsed

    val entry = NmeaLogEntry(
      id = "SDDPT-${parsed.timestampEpochMs}-${(100..999).random()}",
      timestampEpochMs = parsed.timestampEpochMs,
      isoTimestamp = parsed.isoTimestamp,
      rawSentence = parsed.rawSentence,
      sentenceType = "${parsed.talker}DPT",
      depthMeters = parsed.depthMeters,
      offsetMeters = parsed.offsetMeters,
      correctedDepthMeters = parsed.correctedDepthMeters,
      sourceDevice = sourceDevice,
      isValidChecksum = parsed.isChecksumValid,
      latitude = lat,
      longitude = lon,
      isSimulated = isSimulated
    )

    // Update in-memory log
    _rawLogs.update { current ->
      val updated = (listOf(entry) + current).take(MAX_MEMORY_LOG_ENTRIES)
      updated
    }

    // Update running statistics
    updateRunningStats(entry)

    // Append to persistent log files asynchronously
    scope.launch(Dispatchers.IO) {
      appendLogToDisk(entry)
    }

    return entry
  }

  private fun updateRunningStats(entry: NmeaLogEntry) {
    _stats.update { s ->
      val logs = _rawLogs.value
      val depths = logs.map { it.depthMeters }
      val avg = if (depths.isNotEmpty()) depths.average() else 0.0
      val min = depths.minOrNull() ?: 0.0
      val max = depths.maxOrNull() ?: 0.0
      s.copy(
        averageDepthMeters = avg,
        minDepthMeters = min,
        maxDepthMeters = max,
        lastPingIso = entry.isoTimestamp
      )
    }
  }

  @Synchronized
  private fun appendLogToDisk(entry: NmeaLogEntry) {
    try {
      // 1. Raw NMEA text append
      FileWriter(logFile, true).use { writer ->
        writer.appendLine("${entry.isoTimestamp} | ${entry.sourceDevice} | ${entry.rawSentence}")
      }

      // 2. Periodic JSON flush of retained entries
      if (_rawLogs.value.size % 5 == 0 || _rawLogs.value.size < 10) {
        val dataset = NoaaSurveyDataset(
          metadata = NoaaDatasetMetadata(
            vesselName = "S/V Surveyor",
            surveyArea = "Unassigned Survey Area",
            createdIso = formatIsoTimestamp(System.currentTimeMillis()),
            sounderDevice = entry.sourceDevice
          ),
          totalRecords = _rawLogs.value.size,
          records = _rawLogs.value
        )
        val jsonPayload = serializeDatasetToJson(dataset)
        jsonFile.writeText(jsonPayload)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun loadRetainedLogs() {
    scope.launch(Dispatchers.IO) {
      try {
        if (jsonFile.exists() && jsonFile.length() > 0) {
          val content = jsonFile.readText()
          val dataset = deserializeDatasetFromJson(content)
          if (dataset != null && dataset.records.isNotEmpty()) {
            _rawLogs.value = dataset.records.take(MAX_MEMORY_LOG_ENTRIES)
            if (dataset.records.isNotEmpty()) {
              updateRunningStats(dataset.records.first())
            }
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  /**
   * Purges retained historical logs from both memory and persistent disk files.
   */
  fun clearRetainedLogs() {
    _rawLogs.value = emptyList()
    _stats.value = DataServiceStats()
    scope.launch(Dispatchers.IO) {
      try {
        if (logFile.exists()) logFile.delete()
        if (jsonFile.exists()) jsonFile.delete()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  // ==========================================
  // NOAA EXPORT & DOWNLOAD GENERATION
  // ==========================================

  /**
   * Generates a standard Hydrographic Survey CSV file.
   */
  fun exportToCsv(): String {
    val sb = StringBuilder()
    sb.appendLine("Timestamp_ISO,Timestamp_Epoch_Ms,Sentence_Type,Depth_Meters,Offset_Meters,Corrected_Depth_Meters,Valid_Checksum,Source_Device,Latitude,Longitude,Raw_NMEA,Is_Simulated")
    for (item in _rawLogs.value.reversed()) {
      sb.appendLine(
        "${item.isoTimestamp}," +
          "${item.timestampEpochMs}," +
          "${item.sentenceType}," +
          String.format(Locale.US, "%.3f", item.depthMeters) + "," +
          String.format(Locale.US, "%.3f", item.offsetMeters) + "," +
          String.format(Locale.US, "%.3f", item.correctedDepthMeters) + "," +
          "${item.isValidChecksum}," +
          "\"${item.sourceDevice}\"," +
          (item.latitude?.toString() ?: "") + "," +
          (item.longitude?.toString() ?: "") + "," +
          "\"${item.rawSentence}\"" +
          ",${item.isSimulated}"
      )
    }
    return sb.toString()
  }

  /**
   * Generates raw .nmea stream file ready for CARIS, Pydro, or Hypack ingestion.
   */
  fun exportToRawNmea(): String {
    val sb = StringBuilder()
    sb.appendLine("# NAV-CAST S-100 — independent prototype (not a NOAA or CSDL product)")
    sb.appendLine("# Hydrographic Telemetry Log - NMEA 0183 \$SDDPT Stream")
    sb.appendLine("# Exported: ${formatIsoTimestamp(System.currentTimeMillis())}")
    sb.appendLine("# Records: ${_rawLogs.value.size}")
    val containsSimNmea = _rawLogs.value.any { it.isSimulated }
    sb.appendLine("# Contains simulated data: ${if (containsSimNmea) "YES" else "NO"}")
    if (containsSimNmea) {
      sb.appendLine("# WARNING: SIMULATED records are present. Per-record flags are in the CSV and JSON exports (Is_Simulated / isSimulated); raw sentences below are not individually flagged.")
    }
    sb.appendLine("# -------------------------------------------------------------")
    for (item in _rawLogs.value.reversed()) {
      sb.appendLine(item.rawSentence)
    }
    return sb.toString()
  }

  /**
   * Generates a structured NAV-CAST JSON dataset.
   */
  fun exportToJson(vesselName: String, surveyArea: String): String {
    val containsSimJson = _rawLogs.value.any { it.isSimulated }
    val dataset = NoaaSurveyDataset(
      metadata = NoaaDatasetMetadata(
        vesselName = vesselName,
        surveyArea = surveyArea,
        organization = "NAV-CAST S-100 (independent project)",
        createdIso = formatIsoTimestamp(System.currentTimeMillis()),
        sounderDevice = _connectedDeviceName.value,
        containsSimulatedData = containsSimJson,
        provenance = when {
          _rawLogs.value.isEmpty() -> "EMPTY LOG — no records"
          containsSimJson -> "CONTAINS SIMULATED DATA — per-record isSimulated flags are authoritative"
          else -> "REAL — all records recorded from a connected instrument"
        }
      ),
      totalRecords = _rawLogs.value.size,
      records = _rawLogs.value.reversed()
    )
    return serializeDatasetToJson(dataset)
  }

  /**
   * Saves the export file to the public downloads or application cache directory
   * and returns the resulting File ready for download or external analysis.
   */
  suspend fun createExportFile(
    format: ExportFormat,
    vesselName: String = "S/V Surveyor",
    surveyArea: String = "Chesapeake Bay Approach"
  ): File = withContext(Dispatchers.IO) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "NAVCAST_SDDPT_${timestamp}.${format.extension}"

    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val targetFile = File(exportDir, fileName)

    val content = when (format) {
      ExportFormat.CSV -> exportToCsv()
      ExportFormat.RAW_NMEA -> exportToRawNmea()
      ExportFormat.JSON -> exportToJson(vesselName, surveyArea)
    }

    targetFile.writeText(content)
    targetFile
  }

  /**
   * Creates an Android ACTION_SEND Intent with FileProvider URI to share or download the log.
   */
  fun createShareIntent(file: File, format: ExportFormat): Intent {
    val uri: Uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file
    )

    return Intent(Intent.ACTION_SEND).apply {
      type = format.mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, "NAV-CAST S-100 Hydrographic Log: ${file.name}")
      putExtra(Intent.EXTRA_TEXT, "Exported ${format.label} containing ${_rawLogs.value.size} NMEA 0183 \$SDDPT records.")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  // ==========================================
  // BLUETOOTH SPP — DELEGATED TO ConnectivityManager
  // (single transport stack since 2026-10-17)
  // ==========================================

  /**
   * Returns classic paired Bluetooth SPP devices visible to Android OS.
   * Delegates to [ConnectivityManager] — the single Bluetooth stack.
   */
  fun getPairedDevices(): List<BluetoothSppDevice> = connectivityManager.getPairedDevices()

  /**
   * Connects to an external GPS/depth sounder via Bluetooth SPP.
   * Delegates transport to [ConnectivityManager]: the device is classified,
   * wrapped as a [DiscoveredVesselInstrument], and connected through the same
   * code path as an instrument picked from discovery — including its
   * no-silent-simulation-fallback behaviour. Sentences arrive via the
   * manager's callback and are ingested by this service.
   */
  fun connectToDevice(deviceAddress: String, deviceName: String = "External Echosounder") {
    stopSimulation()
    val (type, sentences, manufacturer) = connectivityManager.classifyInstrument(deviceName, deviceAddress)
    connectivityManager.connectInstrument(
      DiscoveredVesselInstrument(
        id = deviceAddress,
        name = deviceName,
        address = deviceAddress,
        type = type,
        rssi = -60,
        isBonded = true,
        isConnected = false,
        supportedSentences = sentences,
        manufacturer = manufacturer
      )
    )
  }

  /**
   * Closes the active Bluetooth SPP connection (owned by [ConnectivityManager])
   * and stops any simulation running in this service.
   */
  fun disconnect() {
    stopSimulation()
    connectivityManager.disconnectInstrument()
    _connectionState.value = SppConnectionState.DISCONNECTED
    _connectedDeviceName.value = "None"
  }

  // ==========================================
  // HIGH-FIDELITY SIMULATION MODE (TESTING & DEMO)
  // ==========================================

  /**
   * Starts high-fidelity NMEA 0183 $SDDPT stream simulator for testing without hardware sounder.
   */
  fun startSimulation(
    baseDepthMeters: Double = 14.85,
    transducerOffsetMeters: Double = 0.0,
    intervalMs: Long = 1200,
    lat: Double = 36.96,
    lon: Double = -76.32
  ) {
    stopSimulation()
    _isSimulationActive.value = true
    _connectionState.value = SppConnectionState.STREAMING
    _connectedDeviceName.value = "NAV-CAST Survey Simulator (SPP)"

    simulationJob = scope.launch(Dispatchers.Default) {
      var step = 0
      while (isActive) {
        delay(intervalMs)
        step++

        // Hydrodynamic acoustic ping fluctuation with realistic wave surge & slight slope
        val waveSwell = Math.sin(step * 0.25) * 0.22
        val seabedSlope = (step % 40 - 20) * 0.015
        val pingNoise = (Math.random() - 0.5) * 0.06
        val depth = String.format(Locale.US, "%.2f", baseDepthMeters + waveSwell + seabedSlope + pingNoise).toDouble()

        val sentence = formatSddptSentence(
          depthMeters = depth,
          offsetMeters = transducerOffsetMeters,
          talker = "SD"
        )

        ingestSentence(
          rawSentence = sentence,
          sourceDevice = "SIMULATED — NAV-CAST Sim SPP (200kHz)",
          lat = lat + (Math.random() - 0.5) * 0.001,
          lon = lon + (Math.random() - 0.5) * 0.001,
          isSimulated = true
        )
      }
    }
  }

  fun stopSimulation() {
    simulationJob?.cancel()
    simulationJob = null
    _isSimulationActive.value = false
    // Reset to DISCONNECTED only when this service's own simulation was the
    // source of the STREAMING state — never tear down the state of a real
    // Bluetooth connection owned by ConnectivityManager.
    if (_connectionState.value == SppConnectionState.STREAMING &&
      connectivityManager.connectionState.value != SppConnectionState.STREAMING
    ) {
      _connectionState.value = SppConnectionState.DISCONNECTED
      _connectedDeviceName.value = "None"
    }
  }

  private fun formatIsoTimestamp(epochMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(epochMs))
  }
}
