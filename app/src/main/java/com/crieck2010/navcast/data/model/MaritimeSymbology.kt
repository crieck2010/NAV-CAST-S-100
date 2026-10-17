package com.crieck2010.navcast.data.model

enum class BuoyType {
  PORT_LATERAL_CAN,      // Green can buoy with cylinder topmark (IALA B - Port on entering)
  STARBOARD_LATERAL_NUN, // Red nun buoy with conical topmark (IALA B - Starboard on entering)
  SAFE_WATER_MARK,       // Red/White vertical stripes with sphere topmark (Mid-channel)
  ISOLATED_DANGER,       // Black with red band, 2 spheres (Wreck / shoal obstruction)
  CARDINAL_NORTH,        // Black/Yellow double cones pointing up
  CARDINAL_SOUTH         // Black/Yellow double cones pointing down
}

enum class BuoyLightColor {
  GREEN,
  RED,
  WHITE,
  YELLOW
}

data class MaritimeBuoy(
  val id: String,
  val name: String,
  val type: BuoyType,
  val x: Float, // Normalized coordinates 0..1 in survey region
  val y: Float,
  val lat: Double,
  val lon: Double,
  val lightCharacteristics: String,
  val lightColor: BuoyLightColor,
  val soundSignal: String? = null
)

enum class HazardType {
  SUBMERGED_ROCK,
  DANGEROUS_WRECK,
  UNDERWATER_OBSTRUCTION,
  SUBMARINE_CABLE
}

data class NauticalHazard(
  val id: String,
  val name: String,
  val type: HazardType,
  val x: Float,
  val y: Float,
  val lat: Double,
  val lon: Double,
  val depthMeters: Float
)

data class AnchorageArea(
  val id: String,
  val name: String,
  val x: Float,
  val y: Float,
  val lat: Double,
  val lon: Double,
  val radiusMeters: Float = 600f
)

data class AisTarget(
  val id: String,
  val mmsi: String,
  val vesselName: String,
  val vesselType: String,
  val x: Float,
  val y: Float,
  val speedKnots: Float,
  val headingDeg: Float,
  val draftMeters: Float,
  val lengthMeters: Float,
  val cpaDistanceNm: Float = 1.4f
)

data class DredgedChannel(
  val name: String,
  val chartedDepthMeters: Float,
  val projectWidthMeters: Float,
  val startX: Float,
  val startY: Float,
  val endX: Float,
  val endY: Float
)
