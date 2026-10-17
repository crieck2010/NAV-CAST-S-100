package com.crieck2010.navcast.engine

import com.crieck2010.navcast.data.model.VDatumResult
import kotlin.math.roundToInt

object VDatumEngine {

  val SUPPORTED_REGIONS = listOf(
    "Chesapeake Bay (MD/VA)",
    "Tampa Bay (FL)",
    "Houston / Galveston (TX)",
    "Puget Sound (WA)",
    "New York Harbor (NY/NJ)"
  )

  val SUPPORTED_DATUMS = listOf(
    "NAD83(2011) Ellipsoid",
    "WGS84 Ellipsoid",
    "NAVD88 Orthometric",
    "MLLW (Chart Datum)",
    "MHW (Bridge Clearance)",
    "MSL (Mean Sea Level)"
  )

  data class RegionGridOffset(
    val geoidSeparation: Double, // Ellipsoid to NAVD88 (Geoid18)
    val navd88ToMllw: Double,    // NAVD88 to MLLW
    val navd88ToMhw: Double,     // NAVD88 to MHW
    val navd88ToMsl: Double      // NAVD88 to MSL
  )

  private val REGION_OFFSETS = mapOf(
    "Chesapeake Bay (MD/VA)" to RegionGridOffset(
      geoidSeparation = -34.82,
      navd88ToMllw = -0.32,
      navd88ToMhw = 0.58,
      navd88ToMsl = 0.12
    ),
    "Tampa Bay (FL)" to RegionGridOffset(
      geoidSeparation = -28.15,
      navd88ToMllw = -0.28,
      navd88ToMhw = 0.44,
      navd88ToMsl = 0.08
    ),
    "Houston / Galveston (TX)" to RegionGridOffset(
      geoidSeparation = -26.90,
      navd88ToMllw = -0.35,
      navd88ToMhw = 0.62,
      navd88ToMsl = 0.14
    ),
    "Puget Sound (WA)" to RegionGridOffset(
      geoidSeparation = -22.45,
      navd88ToMllw = -2.10,
      navd88ToMhw = 2.45,
      navd88ToMsl = 0.20
    ),
    "New York Harbor (NY/NJ)" to RegionGridOffset(
      geoidSeparation = -32.10,
      navd88ToMllw = -0.85,
      navd88ToMhw = 1.25,
      navd88ToMsl = 0.18
    )
  )

  /**
   * Transforms input height between ellipsoidal, orthometric, and tidal datums.
   */
  fun transform(
    region: String,
    lat: Double,
    lon: Double,
    inputDatum: String,
    inputHeight: Double,
    outputDatum: String
  ): VDatumResult {
    val offsets = REGION_OFFSETS[region] ?: REGION_OFFSETS.values.first()

    // Step 1: Convert input to NAVD88 intermediate orthometric height
    val heightInNavd88 = when (inputDatum) {
      "NAD83(2011) Ellipsoid" -> inputHeight - offsets.geoidSeparation
      "WGS84 Ellipsoid" -> inputHeight - offsets.geoidSeparation - 0.12 // Small WGS84 vs NAD83 frame shift
      "NAVD88 Orthometric" -> inputHeight
      "MLLW (Chart Datum)" -> inputHeight + offsets.navd88ToMllw
      "MHW (Bridge Clearance)" -> inputHeight + offsets.navd88ToMhw
      "MSL (Mean Sea Level)" -> inputHeight + offsets.navd88ToMsl
      else -> inputHeight
    }

    // Step 2: Convert NAVD88 to target datum
    val outputHeight = when (outputDatum) {
      "NAD83(2011) Ellipsoid" -> heightInNavd88 + offsets.geoidSeparation
      "WGS84 Ellipsoid" -> heightInNavd88 + offsets.geoidSeparation + 0.12
      "NAVD88 Orthometric" -> heightInNavd88
      "MLLW (Chart Datum)" -> heightInNavd88 - offsets.navd88ToMllw
      "MHW (Bridge Clearance)" -> heightInNavd88 - offsets.navd88ToMhw
      "MSL (Mean Sea Level)" -> heightInNavd88 - offsets.navd88ToMsl
      else -> heightInNavd88
    }

    return VDatumResult(
      region = region,
      latitude = lat,
      longitude = lon,
      inputDatum = inputDatum,
      inputHeightMeters = roundToThree(inputHeight),
      outputDatum = outputDatum,
      outputHeightMeters = roundToThree(outputHeight),
      ellipsoidToNavd88Meters = roundToThree(offsets.geoidSeparation),
      navd88ToMllwMeters = roundToThree(offsets.navd88ToMllw),
      navd88ToMhwMeters = roundToThree(offsets.navd88ToMhw)
    )
  }

  private fun roundToThree(value: Double): Double {
    return (value * 1000.0).roundToInt() / 1000.0
  }
}
