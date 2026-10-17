package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BluetoothSppDevice
import com.example.data.model.DiscoveredVesselInstrument
import com.example.data.model.DukcResult
import com.example.data.model.GroundTruthReport
import com.example.data.model.NmeaLogEntry
import com.example.data.model.S102Sounding
import com.example.data.model.S104WaterLevel
import com.example.data.model.SddptSentence
import com.example.data.model.SppConnectionState
import com.example.data.model.SurveyArea
import com.example.data.model.VDatumResult
import com.example.data.model.VesselProfile
import com.example.data.repository.NavCastRepository
import com.example.data.service.ConnectivityManager
import com.example.data.service.DataService
import com.example.data.service.DataServiceStats
import com.example.data.service.ExportFormat
import com.example.engine.DukcCalculator
import com.example.engine.VDatumEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class NavCastUiState(
  val selectedArea: SurveyArea,
  val surveyAreas: List<SurveyArea>,
  val vesselProfile: VesselProfile,
  val waterLevel: S104WaterLevel,
  val isWaterLevelLoading: Boolean = false,
  val selectedSounding: S102Sounding? = null,
  val selectedBuoy: com.example.data.model.MaritimeBuoy? = null,
  val selectedHazard: com.example.data.model.NauticalHazard? = null,
  val selectedAis: com.example.data.model.AisTarget? = null,
  val currentDukc: DukcResult,
  val isConfinedChannel: Boolean = true,
  // Model validator & NMEA state
  val isNmeaStreaming: Boolean = true,
  val currentNmeaSentence: String = "\$SDDPT,14.85,0.0*53",
  val observedTransducerDepth: Float = 14.85f,
  val manualDepthInput: String = "14.85",
  val modelPredictedDepth: Float = 15.20f,
  val groundTruthReports: List<GroundTruthReport> = emptyList(),
  val lastReportJson: String? = null,
  // Bluetooth SPP & DataService Historical Log Retention
  val sppConnectionState: SppConnectionState = SppConnectionState.STREAMING,
  val connectedSppDevice: String = "NOAA Sim SPP (200kHz)",
  val latestSddpt: SddptSentence? = null,
  val retainedNmeaLogs: List<NmeaLogEntry> = emptyList(),
  val dataServiceStats: DataServiceStats = DataServiceStats(),
  val isSppSimulating: Boolean = true,
  val pairedBluetoothDevices: List<BluetoothSppDevice> = emptyList(),
  // Bluetooth Vessel Instrument Discovery & Connectivity
  val discoveredInstruments: List<DiscoveredVesselInstrument> = ConnectivityManager.DEFAULT_INSTRUMENTS,
  val isScanningInstruments: Boolean = false,
  val connectedInstrument: DiscoveredVesselInstrument? = null,
  val connectivityStatusMessage: String? = null,
  // AR Simulation
  val simulatedSurgeMeters: Float = 1.8f,
  val showArBathymetryMesh: Boolean = true,
  val showArCurrentVectors: Boolean = true,
  // VDatum
  val vdatumRegion: String = VDatumEngine.SUPPORTED_REGIONS.first(),
  val vdatumInputDatum: String = "NAD83(2011) Ellipsoid",
  val vdatumInputHeight: Double = -32.50,
  val vdatumOutputDatum: String = "MLLW (Chart Datum)",
  val vdatumResult: VDatumResult? = null,
  // Active Navigation Screen
  val currentTab: NavigationTab = NavigationTab.TACTICAL_MAP,
  val userNotification: String? = null
)

enum class NavigationTab {
  TACTICAL_MAP,
  DUKC_CALCULATOR,
  MODEL_VALIDATOR,
  AR_SURFACE,
  POCKET_VDATUM
}

class NavCastViewModel @JvmOverloads constructor(
  application: Application,
  private val repository: NavCastRepository = NavCastRepository(),
  val dataService: DataService = DataService(application.applicationContext)
) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(createInitialState())
  val uiState: StateFlow<NavCastUiState> = _uiState.asStateFlow()

  init {
    loadLiveWaterLevel()
    observeDataService()
    observeReports()
    calculateVDatum()
    refreshPairedDevices()
    startSppSimulation()
  }

  private fun observeDataService() {
    viewModelScope.launch {
      dataService.connectionState.collect { connState ->
        _uiState.update { it.copy(sppConnectionState = connState) }
      }
    }
    viewModelScope.launch {
      dataService.connectedDeviceName.collect { name ->
        _uiState.update { it.copy(connectedSppDevice = name) }
      }
    }
    viewModelScope.launch {
      dataService.latestSentence.collect { sentence ->
        if (sentence != null) {
          _uiState.update {
            it.copy(
              latestSddpt = sentence,
              observedTransducerDepth = sentence.depthMeters.toFloat(),
              currentNmeaSentence = sentence.rawSentence,
              manualDepthInput = String.format(Locale.US, "%.2f", sentence.depthMeters)
            )
          }
          recalculateDukc()
        }
      }
    }
    viewModelScope.launch {
      dataService.rawLogs.collect { logs ->
        _uiState.update { it.copy(retainedNmeaLogs = logs) }
      }
    }
    viewModelScope.launch {
      dataService.stats.collect { stats ->
        _uiState.update { it.copy(dataServiceStats = stats) }
      }
    }
    viewModelScope.launch {
      dataService.isSimulationActive.collect { simActive ->
        _uiState.update {
          it.copy(
            isSppSimulating = simActive,
            isNmeaStreaming = simActive || it.sppConnectionState == SppConnectionState.STREAMING
          )
        }
      }
    }
    viewModelScope.launch {
      dataService.connectivityManager.discoveredInstruments.collect { list ->
        _uiState.update { it.copy(discoveredInstruments = list) }
      }
    }
    viewModelScope.launch {
      dataService.connectivityManager.isScanning.collect { scanning ->
        _uiState.update { it.copy(isScanningInstruments = scanning) }
      }
    }
    viewModelScope.launch {
      dataService.connectivityManager.connectedInstrument.collect { inst ->
        _uiState.update { it.copy(connectedInstrument = inst) }
      }
    }
    viewModelScope.launch {
      dataService.connectivityManager.statusMessage.collect { msg ->
        if (msg != null) {
          _uiState.update { it.copy(connectivityStatusMessage = msg) }
        }
      }
    }
  }

  private fun createInitialState(): NavCastUiState {
    val initialArea = repository.surveyAreas.first()
    val initialVessel = VesselProfile()
    val initialWaterLevel = S104WaterLevel(
      stationId = initialArea.noaaStationId,
      stationName = "NOAA Sewells Point (8638610)",
      timestamp = "Loading...",
      waterLevelMeters = 0.42f,
      source = "NOAA CO-OPS API"
    )
    val initialDukc = DukcCalculator.calculate(
      chartedDepthMeters = initialArea.defaultBaseDepthMeters,
      waterLevelMeters = initialWaterLevel.waterLevelMeters,
      vessel = initialVessel,
      isConfinedChannel = true
    )

    return NavCastUiState(
      selectedArea = initialArea,
      surveyAreas = repository.surveyAreas,
      vesselProfile = initialVessel,
      waterLevel = initialWaterLevel,
      currentDukc = initialDukc
    )
  }

  private fun observeReports() {
    viewModelScope.launch {
      repository.groundTruthReports.collect { reports ->
        _uiState.update { it.copy(groundTruthReports = reports) }
      }
    }
  }

  fun selectTab(tab: NavigationTab) {
    _uiState.update { it.copy(currentTab = tab) }
  }

  fun selectSurveyArea(area: SurveyArea) {
    _uiState.update {
      it.copy(
        selectedArea = area,
        selectedSounding = null,
        modelPredictedDepth = area.defaultBaseDepthMeters
      )
    }
    loadLiveWaterLevel()
    recalculateDukc()
  }

  fun selectSounding(sounding: S102Sounding?) {
    _uiState.update {
      it.copy(
        selectedSounding = sounding,
        selectedBuoy = null,
        selectedHazard = null,
        selectedAis = null,
        modelPredictedDepth = sounding?.depthMeters ?: it.selectedArea.defaultBaseDepthMeters
      )
    }
    recalculateDukc()
  }

  fun selectBuoy(buoy: com.example.data.model.MaritimeBuoy?) {
    _uiState.update {
      it.copy(
        selectedBuoy = buoy,
        selectedSounding = null,
        selectedHazard = null,
        selectedAis = null
      )
    }
  }

  fun selectHazard(hazard: com.example.data.model.NauticalHazard?) {
    _uiState.update {
      it.copy(
        selectedHazard = hazard,
        selectedSounding = null,
        selectedBuoy = null,
        selectedAis = null
      )
    }
  }

  fun selectAis(ais: com.example.data.model.AisTarget?) {
    _uiState.update {
      it.copy(
        selectedAis = ais,
        selectedSounding = null,
        selectedBuoy = null,
        selectedHazard = null
      )
    }
  }

  fun updateVesselProfile(update: (VesselProfile) -> VesselProfile) {
    _uiState.update { current ->
      val updated = update(current.vesselProfile)
      current.copy(vesselProfile = updated)
    }
    recalculateDukc()
  }

  fun setVesselPreset(type: String) {
    val preset = when (type) {
      "NOAA S/V Hassler" -> VesselProfile(
        id = "hassler",
        name = "NOAA S/V Ferdinand R. Hassler",
        type = "Hydrographic Survey Vessel",
        staticDraftMeters = 3.8f,
        beamMeters = 16.5f,
        lengthMeters = 37.7f,
        blockCoefficient = 0.68f,
        speedKnots = 9.5f,
        safetyMarginMeters = 1.0f
      )
      "Hydrographic Launch" -> VesselProfile(
        id = "hsl-2801",
        name = "CSDL Survey Launch HSL-2801",
        type = "Coastal Survey Launch",
        staticDraftMeters = 1.3f,
        beamMeters = 3.6f,
        lengthMeters = 8.5f,
        blockCoefficient = 0.55f,
        speedKnots = 14.0f,
        safetyMarginMeters = 0.6f
      )
      "Post-Panamax Container" -> VesselProfile(
        id = "container",
        name = "M/V Atlantic Mariner",
        type = "Ultra Large Container Vessel",
        staticDraftMeters = 14.2f,
        beamMeters = 48.2f,
        lengthMeters = 366.0f,
        blockCoefficient = 0.82f,
        speedKnots = 11.2f,
        safetyMarginMeters = 1.5f
      )
      else -> VesselProfile(
        id = "tug",
        name = "Coastal Tug & Fuel Barge",
        type = "Articulated Tug-Barge",
        staticDraftMeters = 5.2f,
        beamMeters = 22.0f,
        lengthMeters = 120.0f,
        blockCoefficient = 0.78f,
        speedKnots = 7.8f,
        safetyMarginMeters = 1.0f
      )
    }

    _uiState.update { it.copy(vesselProfile = preset) }
    recalculateDukc()
  }

  fun setConfinedChannel(isConfined: Boolean) {
    _uiState.update { it.copy(isConfinedChannel = isConfined) }
    recalculateDukc()
  }

  fun loadLiveWaterLevel() {
    viewModelScope.launch {
      _uiState.update { it.copy(isWaterLevelLoading = true) }
      val area = _uiState.value.selectedArea
      val water = repository.fetchWaterLevel(
        stationId = area.noaaStationId,
        fallbackModelVal = 0.45f
      )
      _uiState.update {
        it.copy(
          waterLevel = water,
          isWaterLevelLoading = false,
          userNotification = "Water level updated: ${water.waterLevelMeters}m (${water.source})"
        )
      }
      recalculateDukc()
    }
  }

  private fun recalculateDukc() {
    val state = _uiState.value
    val chartedDepth = state.selectedSounding?.depthMeters ?: state.selectedArea.defaultBaseDepthMeters
    val dukc = DukcCalculator.calculate(
      chartedDepthMeters = chartedDepth,
      waterLevelMeters = state.waterLevel.waterLevelMeters,
      vessel = state.vesselProfile,
      isConfinedChannel = state.isConfinedChannel
    )
    _uiState.update { it.copy(currentDukc = dukc) }
  }

  // --- Hydrodynamic Model Validator, Bluetooth SPP & NMEA Streaming ---

  fun toggleNmeaStreaming() {
    if (_uiState.value.isSppSimulating || _uiState.value.sppConnectionState == SppConnectionState.STREAMING) {
      dataService.stopSimulation()
      dataService.disconnect()
      _uiState.update { it.copy(isNmeaStreaming = false) }
    } else {
      startSppSimulation()
      _uiState.update { it.copy(isNmeaStreaming = true) }
    }
  }

  fun startSppSimulation() {
    val state = _uiState.value
    dataService.startSimulation(
      baseDepthMeters = state.modelPredictedDepth.toDouble(),
      transducerOffsetMeters = 0.0,
      intervalMs = 1200,
      lat = state.selectedArea.centerLat,
      lon = state.selectedArea.centerLon
    )
    _uiState.update { it.copy(isNmeaStreaming = true) }
  }

  fun stopSppSimulation() {
    dataService.stopSimulation()
    _uiState.update { it.copy(isNmeaStreaming = false) }
  }

  fun connectBluetoothSpp(device: BluetoothSppDevice) {
    dataService.stopSimulation()
    dataService.connectToDevice(device.address, device.name)
    _uiState.update { it.copy(isNmeaStreaming = true) }
  }

  fun disconnectBluetoothSpp() {
    dataService.disconnect()
    _uiState.update { it.copy(isNmeaStreaming = false) }
  }

  fun refreshPairedDevices() {
    val devices = dataService.getPairedDevices()
    _uiState.update { it.copy(pairedBluetoothDevices = devices) }
  }

  // --- Bluetooth Vessel Instrument Discovery & Connectivity Service ---

  fun startBluetoothDiscovery() {
    dataService.connectivityManager.startDiscovery()
  }

  fun stopBluetoothDiscovery() {
    dataService.connectivityManager.stopDiscovery()
  }

  fun togglePairInstrument(instrument: DiscoveredVesselInstrument) {
    dataService.connectivityManager.togglePair(instrument)
  }

  fun toggleConnectInstrument(instrument: DiscoveredVesselInstrument) {
    dataService.connectivityManager.toggleConnect(instrument)
  }

  fun clearConnectivityStatus() {
    dataService.connectivityManager.clearStatusMessage()
    _uiState.update { it.copy(connectivityStatusMessage = null) }
  }

  fun clearNmeaHistoricalLogs() {
    dataService.clearRetainedLogs()
    _uiState.update { it.copy(userNotification = "Historical NMEA raw data log cleared.") }
  }

  fun exportAndShareLogs(format: ExportFormat, onReady: (File, Intent) -> Unit) {
    viewModelScope.launch {
      val state = _uiState.value
      val file = dataService.createExportFile(
        format = format,
        vesselName = state.vesselProfile.name,
        surveyArea = state.selectedArea.name
      )
      val intent = dataService.createShareIntent(file, format)
      onReady(file, intent)
    }
  }

  fun setManualObservedDepth(depthStr: String) {
    val parsed = depthStr.toFloatOrNull() ?: _uiState.value.observedTransducerDepth
    _uiState.update {
      it.copy(
        manualDepthInput = depthStr,
        observedTransducerDepth = parsed
      )
    }
    // Also ingest into data service log as manual depth sounding
    val formatted = dataService.formatSddptSentence(parsed.toDouble(), 0.0, "SD")
    dataService.ingestSentence(
      rawSentence = formatted,
      sourceDevice = "Manual Calibration Input",
      lat = _uiState.value.selectedArea.centerLat,
      lon = _uiState.value.selectedArea.centerLon
    )
  }

  fun submitGroundTruthReport(notes: String = "") {
    val state = _uiState.value
    val residual = state.observedTransducerDepth - state.modelPredictedDepth
    val type = when {
      residual < -0.8f -> "Critical Shoaling (Hazard)"
      residual < -0.3f -> "Model Over-prediction (Shoal)"
      residual > 0.8f -> "Channel Deepening (Dredge/Scour)"
      residual > 0.3f -> "Model Under-prediction (Deep)"
      else -> "Model Concurrence (Nominal)"
    }

    val reportId = "REP-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())}"
    val report = GroundTruthReport(
      id = reportId,
      timestamp = System.currentTimeMillis(),
      lat = state.selectedArea.centerLat + (Math.random() - 0.5) * 0.02,
      lon = state.selectedArea.centerLon + (Math.random() - 0.5) * 0.02,
      observedDepthMeters = state.observedTransducerDepth,
      modelDepthMeters = state.modelPredictedDepth,
      residualMeters = String.format(Locale.US, "%.2f", residual).toFloat(),
      discrepancyType = type,
      rawNmeaSentence = state.currentNmeaSentence,
      vesselName = state.vesselProfile.name,
      notes = notes.ifBlank { "Field telemetry report submitted to CSDL validation pipeline." }
    )

    repository.addGroundTruthReport(report)

    val jsonTelemetry = """
      {
        "csdl_telemetry_report": {
          "id": "${report.id}",
          "timestamp_iso": "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(report.timestamp))}",
          "vessel": "${report.vesselName}",
          "survey_area": "${state.selectedArea.name}",
          "ofs_model": "${state.selectedArea.ofsModelName}",
          "coordinates": {
            "latitude": ${report.lat},
            "longitude": ${report.lon}
          },
          "hydrographic_soundings": {
            "transducer_observed_m": ${report.observedDepthMeters},
            "csdl_model_predicted_m": ${report.modelDepthMeters},
            "residual_delta_m": ${report.residualMeters},
            "classification": "${report.discrepancyType}",
            "raw_nmea": "${report.rawNmeaSentence}"
          },
          "water_level": {
            "value_m": ${state.waterLevel.waterLevelMeters},
            "source": "${state.waterLevel.source}",
            "datum": "${state.waterLevel.datum}"
          },
          "notes": "${report.notes}"
        }
      }
    """.trimIndent()

    _uiState.update {
      it.copy(
        lastReportJson = jsonTelemetry,
        userNotification = "Discrepancy report $reportId dispatched to CSDL!"
      )
    }
  }

  fun deleteReport(id: String) {
    repository.deleteReport(id)
  }

  fun clearNotification() {
    _uiState.update { it.copy(userNotification = null) }
  }

  // --- AR Simulation ---

  fun setSimulatedSurge(meters: Float) {
    _uiState.update { it.copy(simulatedSurgeMeters = meters) }
  }

  fun toggleArBathymetryMesh() {
    _uiState.update { it.copy(showArBathymetryMesh = !it.showArBathymetryMesh) }
  }

  fun toggleArCurrentVectors() {
    _uiState.update { it.copy(showArCurrentVectors = !it.showArCurrentVectors) }
  }

  // --- Pocket VDatum ---

  fun setVDatumParams(
    region: String = _uiState.value.vdatumRegion,
    inputDatum: String = _uiState.value.vdatumInputDatum,
    inputHeight: Double = _uiState.value.vdatumInputHeight,
    outputDatum: String = _uiState.value.vdatumOutputDatum
  ) {
    _uiState.update {
      it.copy(
        vdatumRegion = region,
        vdatumInputDatum = inputDatum,
        vdatumInputHeight = inputHeight,
        vdatumOutputDatum = outputDatum
      )
    }
    calculateVDatum()
  }

  private fun calculateVDatum() {
    val state = _uiState.value
    val res = VDatumEngine.transform(
      region = state.vdatumRegion,
      lat = state.selectedArea.centerLat,
      lon = state.selectedArea.centerLon,
      inputDatum = state.vdatumInputDatum,
      inputHeight = state.vdatumInputHeight,
      outputDatum = state.vdatumOutputDatum
    )
    _uiState.update { it.copy(vdatumResult = res) }
  }
}
