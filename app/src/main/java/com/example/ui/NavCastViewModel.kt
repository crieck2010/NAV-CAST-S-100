package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DukcResult
import com.example.data.model.GroundTruthReport
import com.example.data.model.S102Sounding
import com.example.data.model.S104WaterLevel
import com.example.data.model.SurveyArea
import com.example.data.model.VDatumResult
import com.example.data.model.VesselProfile
import com.example.data.repository.NavCastRepository
import com.example.engine.DukcCalculator
import com.example.engine.VDatumEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
  // Model validator state
  val isNmeaStreaming: Boolean = true,
  val currentNmeaSentence: String = "\$SDDPT,14.85,0.0*53",
  val observedTransducerDepth: Float = 14.85f,
  val manualDepthInput: String = "14.85",
  val modelPredictedDepth: Float = 15.20f,
  val groundTruthReports: List<GroundTruthReport> = emptyList(),
  val lastReportJson: String? = null,
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

class NavCastViewModel(
  private val repository: NavCastRepository = NavCastRepository()
) : ViewModel() {

  private val _uiState = MutableStateFlow(createInitialState())
  val uiState: StateFlow<NavCastUiState> = _uiState.asStateFlow()

  private var nmeaStreamJob: Job? = null

  init {
    loadLiveWaterLevel()
    startNmeaStream()
    observeReports()
    calculateVDatum()
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

  // --- Hydrodynamic Model Validator & NMEA Streaming ---

  fun toggleNmeaStreaming() {
    val newState = !_uiState.value.isNmeaStreaming
    _uiState.update { it.copy(isNmeaStreaming = newState) }
    if (newState) {
      startNmeaStream()
    } else {
      nmeaStreamJob?.cancel()
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
  }

  private fun startNmeaStream() {
    nmeaStreamJob?.cancel()
    nmeaStreamJob = viewModelScope.launch {
      while (true) {
        delay(1800)
        if (_uiState.value.isNmeaStreaming) {
          val base = _uiState.value.modelPredictedDepth
          // Subtle acoustic variance (+/- 0.35m) with occasional shoaling variation
          val delta = ((Math.random() - 0.48) * 0.7).toFloat()
          val depth = String.format(Locale.US, "%.2f", base + delta).toFloat()
          val sentence = "\$SDDPT,$depth,0.0*${(30..90).random()}"

          _uiState.update {
            it.copy(
              observedTransducerDepth = depth,
              currentNmeaSentence = sentence,
              manualDepthInput = depth.toString()
            )
          }
        }
      }
    }
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
