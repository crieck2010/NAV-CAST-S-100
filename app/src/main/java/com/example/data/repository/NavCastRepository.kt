package com.example.data.repository

import com.example.data.api.NoaaApiClient
import com.example.data.model.AisTarget
import com.example.data.model.AnchorageArea
import com.example.data.model.BuoyLightColor
import com.example.data.model.BuoyType
import com.example.data.model.DredgedChannel
import com.example.data.model.GroundTruthReport
import com.example.data.model.HazardType
import com.example.data.model.MaritimeBuoy
import com.example.data.model.NauticalHazard
import com.example.data.model.S102Sounding
import com.example.data.model.S104WaterLevel
import com.example.data.model.S111CurrentVector
import com.example.data.model.SurveyArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NavCastRepository {

  private val _groundTruthReports = MutableStateFlow<List<GroundTruthReport>>(emptyList())
  val groundTruthReports: StateFlow<List<GroundTruthReport>> = _groundTruthReports.asStateFlow()

  val surveyAreas: List<SurveyArea> = listOf(
    createChesapeakeBayArea(),
    createTampaBayArea(),
    createHoustonShipChannelArea()
  )

  init {
    _groundTruthReports.value = listOf(
      GroundTruthReport(
        id = "REP-2026-001",
        timestamp = System.currentTimeMillis() - 3600000 * 2,
        lat = 36.9682,
        lon = -76.0821,
        observedDepthMeters = 8.12f,
        modelDepthMeters = 9.45f,
        residualMeters = -1.33f,
        discrepancyType = "Shoaling Anomaly",
        rawNmeaSentence = "\$SDDPT,8.12,0.0*5E",
        vesselName = "S/V Surveyor",
        notes = "[SAMPLE — demonstration report, not a real submission] Post-nor'easter sand ridge migration outside Thimble Shoal channel boundary."
      ),
      GroundTruthReport(
        id = "REP-2026-002",
        timestamp = System.currentTimeMillis() - 3600000 * 6,
        lat = 36.9912,
        lon = -76.1245,
        observedDepthMeters = 15.65f,
        modelDepthMeters = 15.10f,
        residualMeters = 0.55f,
        discrepancyType = "Channel Deepening",
        rawNmeaSentence = "\$SDDPT,15.65,0.0*58",
        vesselName = "Survey Launch 2801",
        notes = "[SAMPLE — demonstration report, not a real submission] USACE maintenance dredging verification."
      )
    )
  }

  suspend fun fetchWaterLevel(stationId: String, fallbackModelVal: Float): S104WaterLevel = withContext(Dispatchers.IO) {
    try {
      val response = NoaaApiClient.service.getWaterLevel(stationId = stationId)
      val item = response.data?.firstOrNull()
      val valueStr = item?.value
      if (valueStr != null) {
        val parsedVal = valueStr.toFloatOrNull() ?: fallbackModelVal
        return@withContext S104WaterLevel(
          stationId = stationId,
          stationName = response.metadata?.name ?: "NOAA CO-OPS ($stationId)",
          timestamp = item.time ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
          waterLevelMeters = parsedVal,
          source = "NOAA CO-OPS Live REST API",
          isLive = true
        )
      }
    } catch (_: Exception) {
      // Fall back to the NOAA OFS hydrodynamic model prediction
    }

    val timeNow = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    S104WaterLevel(
      stationId = stationId,
      stationName = "NOAA OFS Model Prediction ($stationId)",
      timestamp = "$timeNow GMT",
      waterLevelMeters = fallbackModelVal,
      source = "NOAA OFS Hydrodynamic Model (fallback — not a live observation)",
      isLive = false
    )
  }

  fun addGroundTruthReport(report: GroundTruthReport) {
    _groundTruthReports.value = listOf(report) + _groundTruthReports.value
  }

  fun deleteReport(id: String) {
    _groundTruthReports.value = _groundTruthReports.value.filter { it.id != id }
  }

  private fun createChesapeakeBayArea(): SurveyArea {
    val soundings = mutableListOf<S102Sounding>()
    val currents = mutableListOf<S111CurrentVector>()

    val rows = 9
    val cols = 9
    for (r in 0 until rows) {
      for (c in 0 until cols) {
        val x = c / (cols - 1).toFloat()
        val y = r / (rows - 1).toFloat()
        val lat = 36.9400 + y * 0.09
        val lon = -76.1900 + x * 0.16

        // Deep central navigational channel along y ~ 0.45..0.55
        val distFromChannel = kotlin.math.abs(y - 0.50f)
        val depth = if (distFromChannel < 0.12f) {
          15.4f + (c % 3) * 0.4f
        } else if (distFromChannel < 0.25f) {
          10.2f - distFromChannel * 12f
        } else {
          3.6f + (c % 2) * 0.7f // Shoal flat
        }

        val seabed = when {
          depth > 14f -> "Soft Silt / Mud"
          depth > 8f -> "Fine Sand"
          else -> "Shell & Sand Shoal"
        }

        soundings.add(
          S102Sounding(
            id = "snd-cb-$r-$c",
            x = x,
            y = y,
            lat = lat,
            lon = lon,
            depthMeters = depth,
            seabedType = seabed
          )
        )

        // S-111 Current vectors
        if (r % 2 == 0 && c % 2 == 0) {
          val speed = 1.3f + (r * 0.15f)
          val direction = 70f + (c * 2.5f)
          currents.add(
            S111CurrentVector(
              id = "cur-cb-$r-$c",
              x = x,
              y = y,
              lat = lat,
              lon = lon,
              speedKnots = speed,
              directionDeg = direction
            )
          )
        }
      }
    }

    // Standard IHO S-52 Maritime Aids to Navigation (Buoys)
    val buoys = listOf(
      MaritimeBuoy(
        id = "b-cb-1",
        name = "G '1TS' Fl G 2.5s",
        type = BuoyType.PORT_LATERAL_CAN,
        x = 0.15f,
        y = 0.58f,
        lat = 36.9750,
        lon = -76.1600,
        lightCharacteristics = "Fl G 2.5s",
        lightColor = BuoyLightColor.GREEN,
        soundSignal = "Gong"
      ),
      MaritimeBuoy(
        id = "b-cb-2",
        name = "R '2TS' Fl R 4s",
        type = BuoyType.STARBOARD_LATERAL_NUN,
        x = 0.15f,
        y = 0.42f,
        lat = 36.9950,
        lon = -76.1600,
        lightCharacteristics = "Fl R 4s",
        lightColor = BuoyLightColor.RED,
        soundSignal = "Bell"
      ),
      MaritimeBuoy(
        id = "b-cb-3",
        name = "G '3TS' Fl G 4s",
        type = BuoyType.PORT_LATERAL_CAN,
        x = 0.48f,
        y = 0.58f,
        lat = 36.9750,
        lon = -76.1100,
        lightCharacteristics = "Fl G 4s",
        lightColor = BuoyLightColor.GREEN
      ),
      MaritimeBuoy(
        id = "b-cb-4",
        name = "R '4TS' Fl R 2.5s",
        type = BuoyType.STARBOARD_LATERAL_NUN,
        x = 0.48f,
        y = 0.42f,
        lat = 36.9950,
        lon = -76.1100,
        lightCharacteristics = "Fl R 2.5s",
        lightColor = BuoyLightColor.RED
      ),
      MaritimeBuoy(
        id = "b-cb-5",
        name = "G '5TS' Q G",
        type = BuoyType.PORT_LATERAL_CAN,
        x = 0.82f,
        y = 0.58f,
        lat = 36.9750,
        lon = -76.0600,
        lightCharacteristics = "Q G",
        lightColor = BuoyLightColor.GREEN
      ),
      MaritimeBuoy(
        id = "b-cb-6",
        name = "R '6TS' Q R",
        type = BuoyType.STARBOARD_LATERAL_NUN,
        x = 0.82f,
        y = 0.42f,
        lat = 36.9950,
        lon = -76.0600,
        lightCharacteristics = "Q R",
        lightColor = BuoyLightColor.RED
      ),
      MaritimeBuoy(
        id = "b-cb-mid",
        name = "RW 'TS' Mo(A) 6s",
        type = BuoyType.SAFE_WATER_MARK,
        x = 0.05f,
        y = 0.50f,
        lat = 36.9850,
        lon = -76.1800,
        lightCharacteristics = "Mo(A) 6s",
        lightColor = BuoyLightColor.WHITE,
        soundSignal = "Whistle"
      )
    )

    val hazards = listOf(
      NauticalHazard(
        id = "haz-cb-1",
        name = "Submerged Obstruction (Fish Haven)",
        type = HazardType.UNDERWATER_OBSTRUCTION,
        x = 0.70f,
        y = 0.22f,
        lat = 37.0120,
        lon = -76.0800,
        depthMeters = 4.2f
      ),
      NauticalHazard(
        id = "haz-cb-2",
        name = "Dangerous Wreck PA (3.1m)",
        type = HazardType.DANGEROUS_WRECK,
        x = 0.32f,
        y = 0.78f,
        lat = 36.9520,
        lon = -76.1400,
        depthMeters = 3.1f
      )
    )

    val anchorages = listOf(
      AnchorageArea(
        id = "anc-cb-1",
        name = "Anchorage Bravo (Deep Draft Commercial)",
        x = 0.28f,
        y = 0.26f,
        lat = 37.0050,
        lon = -76.1450,
        radiusMeters = 800f
      )
    )

    val aisTargets = listOf(
      AisTarget(
        id = "ais-1",
        mmsi = "368123450",
        vesselName = "M/V ATLANTIC RUNNER",
        vesselType = "Container Ship",
        x = 0.65f,
        y = 0.51f,
        speedKnots = 13.4f,
        headingDeg = 250f,
        draftMeters = 13.8f,
        lengthMeters = 294f,
        cpaDistanceNm = 0.8f
      ),
      AisTarget(
        id = "ais-2",
        mmsi = "338987654",
        vesselName = "PILOT BOAT HAMPTON",
        vesselType = "Pilot Tender",
        x = 0.22f,
        y = 0.47f,
        speedKnots = 18.2f,
        headingDeg = 072f,
        draftMeters = 2.1f,
        lengthMeters = 19.5f,
        cpaDistanceNm = 1.9f
      )
    )

    val channel = DredgedChannel(
      name = "THIMBLE SHOAL CHANNEL",
      chartedDepthMeters = 15.2f,
      projectWidthMeters = 300f,
      startX = 0.0f,
      startY = 0.50f,
      endX = 1.0f,
      endY = 0.50f
    )

    return SurveyArea(
      id = "chesapeake_bay",
      name = "Chesapeake Bay Entrance (Thimble Shoal)",
      region = "Atlantic Coast / Hampton Roads",
      centerLat = 36.9850,
      centerLon = -76.1100,
      noaaStationId = "8638610",
      ofsModelName = "CBOFS (Chesapeake Bay OFS - FVCOM)",
      defaultBaseDepthMeters = 15.2f,
      soundings = soundings,
      currents = currents,
      buoys = buoys,
      hazards = hazards,
      anchorages = anchorages,
      aisTargets = aisTargets,
      channel = channel
    )
  }

  private fun createTampaBayArea(): SurveyArea {
    val soundings = mutableListOf<S102Sounding>()
    val currents = mutableListOf<S111CurrentVector>()

    val rows = 9
    val cols = 9
    for (r in 0 until rows) {
      for (c in 0 until cols) {
        val x = c / (cols - 1).toFloat()
        val y = r / (rows - 1).toFloat()
        val lat = 27.5500 + y * 0.09
        val lon = -82.7900 + x * 0.14

        val distFromChannel = kotlin.math.abs(y - 0.46f)
        val depth = if (distFromChannel < 0.12f) {
          13.8f + (c % 2) * 0.5f
        } else if (distFromChannel < 0.26f) {
          8.6f - distFromChannel * 9f
        } else {
          3.2f + (c % 3) * 0.4f
        }

        soundings.add(
          S102Sounding(
            id = "snd-tb-$r-$c",
            x = x,
            y = y,
            lat = lat,
            lon = lon,
            depthMeters = depth,
            seabedType = if (depth > 12f) "Sand & Limestone" else "Shallow Sand Bar"
          )
        )

        if (r % 2 == 1 && c % 2 == 1) {
          currents.add(
            S111CurrentVector(
              id = "cur-tb-$r-$c",
              x = x,
              y = y,
              lat = lat,
              lon = lon,
              speedKnots = 1.6f + (c * 0.14f),
              directionDeg = 82f + (r * 2f)
            )
          )
        }
      }
    }

    val buoys = listOf(
      MaritimeBuoy(
        id = "b-tb-1",
        name = "G '1' Fl G 2.5s (Egmont)",
        type = BuoyType.PORT_LATERAL_CAN,
        x = 0.20f,
        y = 0.54f,
        lat = 27.5850,
        lon = -82.7600,
        lightCharacteristics = "Fl G 2.5s",
        lightColor = BuoyLightColor.GREEN
      ),
      MaritimeBuoy(
        id = "b-tb-2",
        name = "R '2' Fl R 4s (Egmont)",
        type = BuoyType.STARBOARD_LATERAL_NUN,
        x = 0.20f,
        y = 0.38f,
        lat = 27.6050,
        lon = -82.7600,
        lightCharacteristics = "Fl R 4s",
        lightColor = BuoyLightColor.RED
      ),
      MaritimeBuoy(
        id = "b-tb-3",
        name = "G '3' Fl G 4s",
        type = BuoyType.PORT_LATERAL_CAN,
        x = 0.60f,
        y = 0.54f,
        lat = 27.5850,
        lon = -82.7000,
        lightCharacteristics = "Fl G 4s",
        lightColor = BuoyLightColor.GREEN
      ),
      MaritimeBuoy(
        id = "b-tb-4",
        name = "R '4' Fl R 2.5s",
        type = BuoyType.STARBOARD_LATERAL_NUN,
        x = 0.60f,
        y = 0.38f,
        lat = 27.6050,
        lon = -82.7000,
        lightCharacteristics = "Fl R 2.5s",
        lightColor = BuoyLightColor.RED
      )
    )

    val hazards = listOf(
      NauticalHazard(
        id = "haz-tb-1",
        name = "Egmont Shoal Submerged Bar",
        type = HazardType.SUBMERGED_ROCK,
        x = 0.15f,
        y = 0.18f,
        lat = 27.6250,
        lon = -82.7700,
        depthMeters = 2.4f
      )
    )

    val anchorages = listOf(
      AnchorageArea(
        id = "anc-tb-1",
        name = "Tampa Outer Anchorage",
        x = 0.85f,
        y = 0.25f,
        lat = 27.6200,
        lon = -82.6800,
        radiusMeters = 750f
      )
    )

    val aisTargets = listOf(
      AisTarget(
        id = "ais-tb-1",
        mmsi = "367554430",
        vesselName = "TANKER GULF PATRIOT",
        vesselType = "Chemical Tanker",
        x = 0.72f,
        y = 0.46f,
        speedKnots = 10.8f,
        headingDeg = 262f,
        draftMeters = 11.2f,
        lengthMeters = 183f,
        cpaDistanceNm = 1.1f
      )
    )

    val channel = DredgedChannel(
      name = "EGMONT CHANNEL",
      chartedDepthMeters = 13.8f,
      projectWidthMeters = 220f,
      startX = 0.0f,
      startY = 0.46f,
      endX = 1.0f,
      endY = 0.46f
    )

    return SurveyArea(
      id = "tampa_bay",
      name = "Tampa Bay (Egmont Channel & Skyway)",
      region = "Gulf of Mexico Coast",
      centerLat = 27.6000,
      centerLon = -82.7200,
      noaaStationId = "8726520",
      ofsModelName = "TBOFS (Tampa Bay OFS - ROMS)",
      defaultBaseDepthMeters = 13.8f,
      soundings = soundings,
      currents = currents,
      buoys = buoys,
      hazards = hazards,
      anchorages = anchorages,
      aisTargets = aisTargets,
      channel = channel
    )
  }

  private fun createHoustonShipChannelArea(): SurveyArea {
    val soundings = mutableListOf<S102Sounding>()
    val currents = mutableListOf<S111CurrentVector>()

    val rows = 9
    val cols = 9
    for (r in 0 until rows) {
      for (c in 0 until cols) {
        val x = c / (cols - 1).toFloat()
        val y = r / (rows - 1).toFloat()
        val lat = 29.3100 + y * 0.09
        val lon = -94.8600 + x * 0.14

        val distFromChannel = kotlin.math.abs(y - 0.51f)
        val depth = if (distFromChannel < 0.11f) {
          14.6f + (c % 2) * 0.4f
        } else {
          4.0f + (c % 2) * 0.6f
        }

        soundings.add(
          S102Sounding(
            id = "snd-hs-$r-$c",
            x = x,
            y = y,
            lat = lat,
            lon = lon,
            depthMeters = depth,
            seabedType = if (depth > 12f) "Heavy Clay / Mud" else "Oyster Reef / Silt"
          )
        )

        if (r % 2 == 0 && c % 2 == 1) {
          currents.add(
            S111CurrentVector(
              id = "cur-hs-$r-$c",
              x = x,
              y = y,
              lat = lat,
              lon = lon,
              speedKnots = 1.9f + (r * 0.12f),
              directionDeg = 315f + (c * 2f)
            )
          )
        }
      }
    }

    val buoys = listOf(
      MaritimeBuoy(
        id = "b-hs-1",
        name = "G '11' Fl G 2.5s (Bolivar)",
        type = BuoyType.PORT_LATERAL_CAN,
        x = 0.25f,
        y = 0.58f,
        lat = 29.3400,
        lon = -94.8200,
        lightCharacteristics = "Fl G 2.5s",
        lightColor = BuoyLightColor.GREEN
      ),
      MaritimeBuoy(
        id = "b-hs-2",
        name = "R '12' Fl R 4s (Bolivar)",
        type = BuoyType.STARBOARD_LATERAL_NUN,
        x = 0.25f,
        y = 0.44f,
        lat = 29.3600,
        lon = -94.8200,
        lightCharacteristics = "Fl R 4s",
        lightColor = BuoyLightColor.RED
      )
    )

    val hazards = listOf(
      NauticalHazard(
        id = "haz-hs-1",
        name = "Submerged Pipeline Crossing",
        type = HazardType.SUBMARINE_CABLE,
        x = 0.50f,
        y = 0.35f,
        lat = 29.3800,
        lon = -94.7800,
        depthMeters = 3.8f
      )
    )

    val anchorages = listOf(
      AnchorageArea(
        id = "anc-hs-1",
        name = "Bolivar Roads Anchorage 'A'",
        x = 0.70f,
        y = 0.72f,
        lat = 29.3300,
        lon = -94.7500,
        radiusMeters = 650f
      )
    )

    val aisTargets = listOf(
      AisTarget(
        id = "ais-hs-1",
        mmsi = "367000123",
        vesselName = "TUG BRAVO & BARGE",
        vesselType = "Tug & Tow",
        x = 0.42f,
        y = 0.50f,
        speedKnots = 7.6f,
        headingDeg = 312f,
        draftMeters = 4.2f,
        lengthMeters = 110f,
        cpaDistanceNm = 0.5f
      )
    )

    val channel = DredgedChannel(
      name = "HOUSTON SHIP CHANNEL",
      chartedDepthMeters = 14.6f,
      projectWidthMeters = 160f,
      startX = 0.0f,
      startY = 0.51f,
      endX = 1.0f,
      endY = 0.51f
    )

    return SurveyArea(
      id = "houston_ship_channel",
      name = "Houston Ship Channel (Galveston Entrance)",
      region = "Northwest Gulf Coast",
      centerLat = 29.3600,
      centerLon = -94.7800,
      noaaStationId = "8770613",
      ofsModelName = "NGOFS2 (Northern Gulf of Mexico OFS)",
      defaultBaseDepthMeters = 14.6f,
      soundings = soundings,
      currents = currents,
      buoys = buoys,
      hazards = hazards,
      anchorages = anchorages,
      aisTargets = aisTargets,
      channel = channel
    )
  }
}
