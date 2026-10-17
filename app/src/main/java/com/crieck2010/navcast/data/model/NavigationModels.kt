package com.crieck2010.navcast.data.model

enum class UkcStatus {
  SAFE,
  CAUTION,
  DANGER
}

data class VesselProfile(
  val id: String = "vessel-1",
  val name: String = "S/V Surveyor (example vessel)",
  val type: String = "Hydrographic Survey Vessel",
  val staticDraftMeters: Float = 3.8f,
  val beamMeters: Float = 16.5f,
  val lengthMeters: Float = 37.7f,
  val blockCoefficient: Float = 0.68f,
  val speedKnots: Float = 9.5f,
  val safetyMarginMeters: Float = 1.0f
)

data class DukcResult(
  val chartedDepthMeters: Float,
  val waterLevelMeters: Float,
  val totalWaterDepthMeters: Float,
  val staticDraftMeters: Float,
  val dynamicSquatMeters: Float,
  val dynamicDraftMeters: Float,
  val underKeelClearanceMeters: Float,
  val status: UkcStatus
) {
  val isSafe: Boolean get() = status != UkcStatus.DANGER
}

data class S102Sounding(
  val id: String,
  val x: Float, // Normalized 0..1 in map bounds
  val y: Float, // Normalized 0..1 in map bounds
  val lat: Double,
  val lon: Double,
  val depthMeters: Float,
  val seabedType: String,
  val uncertaintyMeters: Float = 0.15f
)

data class S111CurrentVector(
  val id: String,
  val x: Float,
  val y: Float,
  val lat: Double,
  val lon: Double,
  val speedKnots: Float,
  val directionDeg: Float,
  val layer: String = "Surface (0.5m)"
)

data class S104WaterLevel(
  val stationId: String,
  val stationName: String,
  val timestamp: String,
  val waterLevelMeters: Float,
  val datum: String = "MLLW",
  val source: String = "NOAA CO-OPS API",
  val isLive: Boolean = true
)

data class GroundTruthReport(
  val id: String,
  val timestamp: Long,
  val lat: Double,
  val lon: Double,
  val observedDepthMeters: Float,
  val modelDepthMeters: Float,
  val residualMeters: Float,
  val discrepancyType: String,
  val rawNmeaSentence: String,
  val transducerFrequencyKhz: Int = 200,
  val vesselName: String,
  val notes: String = ""
)

data class SurveyArea(
  val id: String,
  val name: String,
  val region: String,
  val centerLat: Double,
  val centerLon: Double,
  val noaaStationId: String,
  val ofsModelName: String,
  val defaultBaseDepthMeters: Float,
  val soundings: List<S102Sounding>,
  val currents: List<S111CurrentVector>,
  val buoys: List<MaritimeBuoy> = emptyList(),
  val hazards: List<NauticalHazard> = emptyList(),
  val anchorages: List<AnchorageArea> = emptyList(),
  val aisTargets: List<AisTarget> = emptyList(),
  val channel: DredgedChannel? = null
)

data class VDatumResult(
  val region: String,
  val latitude: Double,
  val longitude: Double,
  val inputDatum: String,
  val inputHeightMeters: Double,
  val outputDatum: String,
  val outputHeightMeters: Double,
  val ellipsoidToNavd88Meters: Double,
  val navd88ToMllwMeters: Double,
  val navd88ToMhwMeters: Double,
  val modelVersion: String = "VDatum v4.5.1"
)
