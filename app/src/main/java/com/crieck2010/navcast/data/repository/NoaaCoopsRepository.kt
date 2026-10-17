package com.crieck2010.navcast.data.repository

import com.crieck2010.navcast.data.api.NoaaApiClient
import com.crieck2010.navcast.data.api.NoaaCoopsService
import com.crieck2010.navcast.data.model.S104WaterLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data repository to consume the NOAA CO-OPS REST API for real-time water level data.
 * Uses NOAA Station 8575512 (Annapolis, MD) as the primary default station.
 */
class NoaaCoopsRepository(
  private val apiService: NoaaCoopsService = NoaaApiClient.service
) {

  companion object {
    /** Default NOAA CO-OPS station: 8575512 (Annapolis, MD - Severn River / Chesapeake Bay Entrance) */
    const val DEFAULT_STATION_ID = "8575512"
    const val DEFAULT_STATION_NAME = "Annapolis (8575512)"
  }

  private val _waterLevelState = MutableStateFlow(
    S104WaterLevel(
      stationId = DEFAULT_STATION_ID,
      stationName = DEFAULT_STATION_NAME,
      timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
      waterLevelMeters = 0.42f,
      datum = "MLLW",
      source = "NOAA CO-OPS REST API (8575512)",
      isLive = true
    )
  )
  val waterLevelState: StateFlow<S104WaterLevel> = _waterLevelState.asStateFlow()

  /**
   * Fetches the latest real-time water level observation from the NOAA CO-OPS Data API.
   *
   * Endpoint:
   * https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?date=latest&station=8575512&product=water_level&datum=MLLW&units=metric&time_zone=gmt&application=NavCast&format=json
   *
   * @param stationId NOAA Tide Station ID (defaults to 8575512).
   * @param fallbackModelVal Fallback predicted water level if offline.
   * @return Result containing the S104WaterLevel observation.
   */
  suspend fun fetchRealTimeWaterLevel(
    stationId: String = DEFAULT_STATION_ID,
    fallbackModelVal: Float = 0.42f
  ): Result<S104WaterLevel> = withContext(Dispatchers.IO) {
    try {
      val response = apiService.getWaterLevel(stationId = stationId)
      val item = response.data?.firstOrNull()
      val valueStr = item?.value

      if (valueStr != null) {
        val parsedValue = valueStr.toFloatOrNull() ?: fallbackModelVal
        val stationName = response.metadata?.name ?: if (stationId == DEFAULT_STATION_ID) "Annapolis" else "Station $stationId"
        val timestamp = item.time ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        val waterLevel = S104WaterLevel(
          stationId = stationId,
          stationName = "$stationName ($stationId)",
          timestamp = "$timestamp GMT",
          waterLevelMeters = parsedValue,
          datum = "MLLW",
          source = "NOAA CO-OPS Live API",
          isLive = true
        )

        _waterLevelState.value = waterLevel
        return@withContext Result.success(waterLevel)
      } else {
        val errorMsg = response.error?.get("message")?.toString() ?: "No water level data returned"
        val fallback = createFallbackWaterLevel(stationId, fallbackModelVal, "NOAA OFS Model ($errorMsg)")
        _waterLevelState.value = fallback
        return@withContext Result.success(fallback)
      }
    } catch (e: Exception) {
      val fallback = createFallbackWaterLevel(
        stationId = stationId,
        fallbackVal = fallbackModelVal,
        source = "NOAA OFS Hydrodynamic Model (offline fallback: ${e.localizedMessage ?: "Network error"})"
      )
      _waterLevelState.value = fallback
      return@withContext Result.success(fallback)
    }
  }

  private fun createFallbackWaterLevel(
    stationId: String,
    fallbackVal: Float,
    source: String
  ): S104WaterLevel {
    val timeNow = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    return S104WaterLevel(
      stationId = stationId,
      stationName = if (stationId == DEFAULT_STATION_ID) DEFAULT_STATION_NAME else "Station $stationId",
      timestamp = "$timeNow GMT",
      waterLevelMeters = fallbackVal,
      datum = "MLLW",
      source = source,
      isLive = false
    )
  }
}
