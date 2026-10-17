package com.example.engine

import com.example.data.model.DukcResult
import com.example.data.model.UkcStatus
import com.example.data.model.VesselProfile
import kotlin.math.max
import kotlin.math.roundToInt

object DukcCalculator {

  /**
   * Calculates Dynamic Under-Keel Clearance (DUKC) fusing S-102 bathymetry,
   * S-104 water levels, and hydrodynamic ship squat.
   *
   * Formula:
   * Total Depth = Charted Depth + Water Level
   * Squat (Barrass Formula) = (Cb * V^2 / 100) * channelFactor
   * Dynamic Draft = Static Draft + Squat
   * UKC = Total Depth - Dynamic Draft
   */
  fun calculate(
    chartedDepthMeters: Float,
    waterLevelMeters: Float,
    vessel: VesselProfile,
    isConfinedChannel: Boolean = true
  ): DukcResult {
    val totalDepth = max(0f, chartedDepthMeters + waterLevelMeters)

    // Channel confinement factor: 1.0 in open water, 1.6 in restricted fairway
    val channelFactor = if (isConfinedChannel) 1.6f else 1.0f
    val vSquared = vessel.speedKnots * vessel.speedKnots
    val squat = (vessel.blockCoefficient * vSquared / 100f) * channelFactor

    val dynamicDraft = vessel.staticDraftMeters + squat
    val ukc = totalDepth - dynamicDraft

    val status = when {
      ukc < 0.6f -> UkcStatus.DANGER
      ukc < vessel.safetyMarginMeters -> UkcStatus.CAUTION
      else -> UkcStatus.SAFE
    }

    return DukcResult(
      chartedDepthMeters = roundToTwoDecimals(chartedDepthMeters),
      waterLevelMeters = roundToTwoDecimals(waterLevelMeters),
      totalWaterDepthMeters = roundToTwoDecimals(totalDepth),
      staticDraftMeters = roundToTwoDecimals(vessel.staticDraftMeters),
      dynamicSquatMeters = roundToTwoDecimals(squat),
      dynamicDraftMeters = roundToTwoDecimals(dynamicDraft),
      underKeelClearanceMeters = roundToTwoDecimals(ukc),
      status = status
    )
  }

  private fun roundToTwoDecimals(value: Float): Float {
    return (value * 100f).roundToInt() / 100f
  }
}
