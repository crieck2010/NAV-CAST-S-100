package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DukcResult
import com.example.data.model.UkcStatus
import com.example.data.model.VesselProfile
import com.example.data.repository.NoaaCoopsRepository
import com.example.engine.DukcCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DukcUiState(
  // Vessel Profile State Parameters
  val staticDraftMeters: Float = 3.8f,
  val beamMeters: Float = 16.5f,
  val speedKnots: Float = 9.5f,
  val lengthMeters: Float = 37.7f,
  val blockCoefficient: Float = 0.68f,
  val safetyMarginMeters: Float = 1.0f,
  val isConfinedChannel: Boolean = true,
  val vesselName: String = "S/V Surveyor (example vessel)",
  val vesselType: String = "Hydrographic Survey Vessel",

  // S-100 Ingested Environmental Parameters
  val s102ChartedDepthMeters: Float = 15.2f,    // S-102 High-Resolution Bathymetry
  val s104WaterLevelMeters: Float = 0.42f,      // S-104 Water Level from NOAA CO-OPS REST API
  val s111CurrentSpeedKnots: Float = 1.4f,      // S-111 Surface Current Velocity

  // NOAA CO-OPS Station Telemetry (Default 8575512 Annapolis, MD)
  val stationId: String = NoaaCoopsRepository.DEFAULT_STATION_ID,
  val stationName: String = NoaaCoopsRepository.DEFAULT_STATION_NAME,
  val waterLevelDatum: String = "MLLW",
  val waterLevelSource: String = "NOAA CO-OPS REST API",
  val waterLevelTimestamp: String = "Latest",
  val isWaterLevelLoading: Boolean = false,

  // Calculated Dynamic Under-Keel Clearance
  val dukcResult: DukcResult = DukcCalculator.calculate(
    chartedDepthMeters = 15.2f,
    waterLevelMeters = 0.42f,
    vessel = VesselProfile(
      staticDraftMeters = 3.8f,
      beamMeters = 16.5f,
      lengthMeters = 37.7f,
      blockCoefficient = 0.68f,
      speedKnots = 9.5f,
      safetyMarginMeters = 1.0f
    ),
    isConfinedChannel = true
  )
)

/**
 * ViewModel responsible for Dynamic Under-Keel Clearance (DUKC) calculations.
 * Manages vessel state (static draft, beam, speed, block coefficient) and consumes
 * the NOAA CO-OPS REST API (defaulting to station 8575512) to dynamically update
 * the DUKC model parameters in real time.
 */
class DUKCViewModel(
  private val noaaRepository: NoaaCoopsRepository = NoaaCoopsRepository()
) : ViewModel() {

  private val _uiState = MutableStateFlow(DukcUiState())
  val uiState: StateFlow<DukcUiState> = _uiState.asStateFlow()

  init {
    // Automatically fetch real-time water levels from default NOAA station 8575512
    fetchNoaaWaterLevel(NoaaCoopsRepository.DEFAULT_STATION_ID)
  }

  /**
   * Consumes the NOAA CO-OPS REST API for real-time water levels and updates the DUKC model parameters.
   *
   * @param stationId The NOAA Tide Station ID (defaults to 8575512 Annapolis).
   */
  fun fetchNoaaWaterLevel(stationId: String = NoaaCoopsRepository.DEFAULT_STATION_ID) {
    viewModelScope.launch {
      _uiState.update { it.copy(isWaterLevelLoading = true) }
      val result = noaaRepository.fetchRealTimeWaterLevel(
        stationId = stationId,
        fallbackModelVal = _uiState.value.s104WaterLevelMeters
      )

      result.onSuccess { waterLevel ->
        _uiState.update { current ->
          current.copy(
            stationId = waterLevel.stationId,
            stationName = waterLevel.stationName,
            s104WaterLevelMeters = waterLevel.waterLevelMeters,
            waterLevelDatum = waterLevel.datum,
            waterLevelSource = waterLevel.source,
            waterLevelTimestamp = waterLevel.timestamp,
            isWaterLevelLoading = false
          )
        }
        // Immediately recalculate DUKC model with the updated real-time water level
        recalculate()
      }.onFailure {
        _uiState.update { current -> current.copy(isWaterLevelLoading = false) }
      }
    }
  }

  /**
   * Refreshes real-time water level data from the current or default station.
   */
  fun refreshNoaaWaterLevel() {
    fetchNoaaWaterLevel(_uiState.value.stationId)
  }

  /**
   * Updates static draft parameter and recalculates DUKC.
   */
  fun setStaticDraft(draft: Float) {
    _uiState.update { it.copy(staticDraftMeters = draft.coerceAtLeast(0.5f)) }
    recalculate()
  }

  /**
   * Updates vessel beam parameter and recalculates DUKC.
   */
  fun setBeam(beam: Float) {
    _uiState.update { it.copy(beamMeters = beam.coerceAtLeast(2.0f)) }
    recalculate()
  }

  /**
   * Updates vessel speed through water (STW) in knots and dynamically recalculates squat.
   */
  fun setSpeed(speed: Float) {
    _uiState.update { it.copy(speedKnots = speed.coerceAtLeast(0.0f)) }
    recalculate()
  }

  /**
   * Updates safety margin depth threshold (meters).
   */
  fun setSafetyMargin(margin: Float) {
    _uiState.update { it.copy(safetyMarginMeters = margin.coerceIn(0.2f, 5.0f)) }
    recalculate()
  }

  /**
   * Toggles confined channel / restricted fairway hydrodynamic blockage factor.
   */
  fun setConfinedChannel(isConfined: Boolean) {
    _uiState.update { it.copy(isConfinedChannel = isConfined) }
    recalculate()
  }

  /**
   * Updates S-100 environmental inputs from NOAA live feeds (S-102, S-104, S-111).
   */
  fun updateS100Parameters(
    chartedDepthMeters: Float,
    waterLevelMeters: Float,
    currentSpeedKnots: Float = _uiState.value.s111CurrentSpeedKnots
  ) {
    _uiState.update {
      it.copy(
        s102ChartedDepthMeters = chartedDepthMeters,
        s104WaterLevelMeters = waterLevelMeters,
        s111CurrentSpeedKnots = currentSpeedKnots
      )
    }
    recalculate()
  }

  /**
   * Calculates the dynamic under-keel clearance based on S-100 data parameters.
   */
  fun calculateDynamicUnderKeelClearance(
    s102Depth: Float = _uiState.value.s102ChartedDepthMeters,
    s104WaterLevel: Float = _uiState.value.s104WaterLevelMeters,
    speedKnots: Float = _uiState.value.speedKnots,
    staticDraft: Float = _uiState.value.staticDraftMeters,
    isConfinedChannel: Boolean = _uiState.value.isConfinedChannel
  ): DukcResult {
    val vessel = VesselProfile(
      name = _uiState.value.vesselName,
      type = _uiState.value.vesselType,
      staticDraftMeters = staticDraft,
      beamMeters = _uiState.value.beamMeters,
      lengthMeters = _uiState.value.lengthMeters,
      blockCoefficient = _uiState.value.blockCoefficient,
      speedKnots = speedKnots,
      safetyMarginMeters = _uiState.value.safetyMarginMeters
    )

    return DukcCalculator.calculate(
      chartedDepthMeters = s102Depth,
      waterLevelMeters = s104WaterLevel,
      vessel = vessel,
      isConfinedChannel = isConfinedChannel
    )
  }

  /**
   * Loads pre-calibrated vessel hydrodynamic characteristics.
   */
  fun loadVesselPreset(preset: String) {
    when (preset) {
      "S/V Surveyor" -> _uiState.update {
        it.copy(
          vesselName = "S/V Surveyor (example vessel)",
          vesselType = "Hydrographic Survey Vessel",
          staticDraftMeters = 3.8f,
          beamMeters = 16.5f,
          lengthMeters = 37.7f,
          blockCoefficient = 0.68f,
          speedKnots = 9.5f,
          safetyMarginMeters = 1.0f
        )
      }
      "Hydrographic Launch" -> _uiState.update {
        it.copy(
          vesselName = "Survey Launch 2801",
          vesselType = "Coastal Survey Launch",
          staticDraftMeters = 1.3f,
          beamMeters = 3.6f,
          lengthMeters = 8.5f,
          blockCoefficient = 0.55f,
          speedKnots = 14.0f,
          safetyMarginMeters = 0.6f
        )
      }
      "Post-Panamax Container" -> _uiState.update {
        it.copy(
          vesselName = "M/V Atlantic Mariner",
          vesselType = "Ultra Large Container Vessel",
          staticDraftMeters = 14.2f,
          beamMeters = 48.2f,
          lengthMeters = 366.0f,
          blockCoefficient = 0.82f,
          speedKnots = 11.2f,
          safetyMarginMeters = 1.5f
        )
      }
      "Harbor Tug" -> _uiState.update {
        it.copy(
          vesselName = "Coastal Tug & Fuel Barge",
          vesselType = "Articulated Tug-Barge",
          staticDraftMeters = 5.2f,
          beamMeters = 22.0f,
          lengthMeters = 120.0f,
          blockCoefficient = 0.78f,
          speedKnots = 7.8f,
          safetyMarginMeters = 1.0f
        )
      }
    }
    recalculate()
  }

  private fun recalculate() {
    val state = _uiState.value
    val result = calculateDynamicUnderKeelClearance(
      s102Depth = state.s102ChartedDepthMeters,
      s104WaterLevel = state.s104WaterLevelMeters,
      speedKnots = state.speedKnots,
      staticDraft = state.staticDraftMeters,
      isConfinedChannel = state.isConfinedChannel
    )
    _uiState.update { it.copy(dukcResult = result) }
  }
}
