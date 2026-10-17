package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.model.AisTarget
import com.example.data.model.AnchorageArea
import com.example.data.model.BuoyLightColor
import com.example.data.model.BuoyType
import com.example.data.model.DredgedChannel
import com.example.data.model.HazardType
import com.example.data.model.MaritimeBuoy
import com.example.data.model.NauticalHazard
import com.example.data.model.S102Sounding
import com.example.data.model.S111CurrentVector
import com.example.data.model.SurveyArea
import com.example.data.model.UkcStatus
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.BathymetryDeep
import com.example.ui.theme.BathymetryMedium
import com.example.ui.theme.BathymetryShallow
import com.example.ui.theme.BathymetryShoal
import com.example.ui.theme.ChartMagenta
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.LateralRed
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NoaaGold
import com.example.ui.theme.OceanicBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance MapLibre-integrated Vector Nautical Map component.
 * Renders S-102 bathymetry, S-111 current vectors, and standard IHO S-52 / NOAA maritime symbology.
 */
@Composable
fun MapLibreNauticalMap(
  surveyArea: SurveyArea,
  selectedSounding: S102Sounding?,
  selectedBuoy: MaritimeBuoy?,
  selectedHazard: NauticalHazard?,
  selectedAis: AisTarget?,
  ukcStatus: UkcStatus,
  vesselSpeedKnots: Float,
  // Layer visibility toggles
  layerBathymetry: Boolean,
  layerCurrents: Boolean,
  layerAtoNBuoys: Boolean,
  layerHazards: Boolean,
  layerDredgedChannel: Boolean,
  layerAnchorages: Boolean,
  layerAisTraffic: Boolean,
  layerSoundings: Boolean,
  is3dPerspective: Boolean,
  // Tap callbacks
  onSoundingTapped: (S102Sounding?) -> Unit,
  onBuoyTapped: (MaritimeBuoy?) -> Unit,
  onHazardTapped: (NauticalHazard?) -> Unit,
  onAisTapped: (AisTarget?) -> Unit,
  modifier: Modifier = Modifier
) {
  var zoomScale by remember { mutableFloatStateOf(1.0f) }
  var panOffsetX by remember { mutableFloatStateOf(0f) }
  var panOffsetY by remember { mutableFloatStateOf(0f) }

  // Animated pulse for S-111 current particles & buoy lights
  val infiniteTransition = rememberInfiniteTransition(label = "maplibreAnim")
  val currentPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(2400, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "currentPhase"
  )

  val beaconPulse by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "beaconPulse"
  )

  Box(modifier = modifier.fillMaxSize().background(NavyBackground)) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            zoomScale = (zoomScale * zoom).coerceIn(0.55f, 4.0f)
            panOffsetX += pan.x
            panOffsetY += pan.y
          }
        }
        .pointerInput(surveyArea) {
          detectTapGestures { tapOffset ->
            val w = size.width
            val h = size.height

            // 1. Check buoys tap
            var tappedBuoy: MaritimeBuoy? = null
            surveyArea.buoys.forEach { buoy ->
              val bx = (buoy.x * w * 0.8f + w * 0.1f) * zoomScale + panOffsetX
              val by = (buoy.y * h * 0.7f + h * 0.15f) * zoomScale + panOffsetY
              if (kotlin.math.hypot(tapOffset.x - bx, tapOffset.y - by) < 36f * zoomScale) {
                tappedBuoy = buoy
              }
            }
            if (tappedBuoy != null) {
              onBuoyTapped(tappedBuoy)
              return@detectTapGestures
            }

            // 2. Check hazards tap
            var tappedHazard: NauticalHazard? = null
            surveyArea.hazards.forEach { haz ->
              val hx = (haz.x * w * 0.8f + w * 0.1f) * zoomScale + panOffsetX
              val hy = (haz.y * h * 0.7f + h * 0.15f) * zoomScale + panOffsetY
              if (kotlin.math.hypot(tapOffset.x - hx, tapOffset.y - hy) < 36f * zoomScale) {
                tappedHazard = haz
              }
            }
            if (tappedHazard != null) {
              onHazardTapped(tappedHazard)
              return@detectTapGestures
            }

            // 3. Check AIS targets tap
            var tappedAis: AisTarget? = null
            surveyArea.aisTargets.forEach { ais ->
              val ax = (ais.x * w * 0.8f + w * 0.1f) * zoomScale + panOffsetX
              val ay = (ais.y * h * 0.7f + h * 0.15f) * zoomScale + panOffsetY
              if (kotlin.math.hypot(tapOffset.x - ax, tapOffset.y - ay) < 36f * zoomScale) {
                tappedAis = ais
              }
            }
            if (tappedAis != null) {
              onAisTapped(tappedAis)
              return@detectTapGestures
            }

            // 4. Check soundings tap
            var tappedSounding: S102Sounding? = null
            var minDistance = Float.MAX_VALUE
            surveyArea.soundings.forEach { snd ->
              val sx = (snd.x * w * 0.8f + w * 0.1f) * zoomScale + panOffsetX
              val sy = (snd.y * h * 0.7f + h * 0.15f) * zoomScale + panOffsetY
              val dist = kotlin.math.hypot(tapOffset.x - sx, tapOffset.y - sy)
              if (dist < 40f * zoomScale && dist < minDistance) {
                minDistance = dist
                tappedSounding = snd
              }
            }
            onSoundingTapped(tappedSounding)
          }
        }
    ) {
      val w = size.width
      val h = size.height

      // 1. Draw MapLibre Base Graticule Grid & Coordinate Labels
      drawMapLibreGraticule(w, h, panOffsetX, panOffsetY, zoomScale)

      // 2. Draw S-102 Bathymetry Depth Fill, Gradient Cells & Contours
      if (layerBathymetry) {
        drawMapLibreBathymetry(
          soundings = surveyArea.soundings,
          selectedSounding = selectedSounding,
          w = w,
          h = h,
          panX = panOffsetX,
          panY = panOffsetY,
          scale = zoomScale,
          showSoundingText = layerSoundings
        )
      }

      // 3. Draw Dredged Navigation Channel Limits & Corridor
      if (layerDredgedChannel && surveyArea.channel != null) {
        drawDredgedChannel(
          channel = surveyArea.channel,
          w = w,
          h = h,
          panX = panOffsetX,
          panY = panOffsetY,
          scale = zoomScale
        )
      }

      // 4. Draw Designated Anchorage Areas (⚓)
      if (layerAnchorages) {
        drawAnchorages(
          anchorages = surveyArea.anchorages,
          w = w,
          h = h,
          panX = panOffsetX,
          panY = panOffsetY,
          scale = zoomScale
        )
      }

      // 5. Draw S-111 Animated Surface Current Vectors & Particles
      if (layerCurrents) {
        drawS111Currents(
          currents = surveyArea.currents,
          w = w,
          h = h,
          panX = panOffsetX,
          panY = panOffsetY,
          scale = zoomScale,
          phase = currentPhase
        )
      }

      // 6. Draw Maritime Hazards (Submerged Rocks & Wrecks)
      if (layerHazards) {
        drawNauticalHazards(
          hazards = surveyArea.hazards,
          selectedHazard = selectedHazard,
          w = w,
          h = h,
          panX = panOffsetX,
          panY = panOffsetY,
          scale = zoomScale
        )
      }

      // 7. Draw Standard IHO S-52 / NOAA AtoN Buoys with Flashing Lights
      if (layerAtoNBuoys) {
        drawMaritimeBuoys(
          buoys = surveyArea.buoys,
          selectedBuoy = selectedBuoy,
          w = w,
          h = h,
          panX = panOffsetX,
          panY = panOffsetY,
          scale = zoomScale,
          beaconPulse = beaconPulse
        )
      }

      // 8. Draw AIS Marine Traffic Targets
      if (layerAisTraffic) {
        drawAisTargets(
          aisTargets = surveyArea.aisTargets,
          selectedAis = selectedAis,
          w = w,
          h = h,
          panX = panOffsetX,
          panY = panOffsetY,
          scale = zoomScale
        )
      }

      // 9. Draw Own-Ship S-52 ECDIS Vessel Symbol
      drawOwnShipSymbol(
        w = w,
        h = h,
        panX = panOffsetX,
        panY = panOffsetY,
        scale = zoomScale,
        speedKnots = vesselSpeedKnots,
        status = ukcStatus
      )

      // 10. Draw True North Nautical Compass Rose in top corner
      drawCompassRose(w, h)
    }
  }
}

// ==================== RENDERING SUB-ENGINES ====================

private fun DrawScope.drawMapLibreGraticule(w: Float, h: Float, panX: Float, panY: Float, scale: Float) {
  val step = 90f * scale
  var x = (panX % step)
  while (x < w) {
    drawLine(
      color = Color(0x1448CAE4),
      start = Offset(x, 0f),
      end = Offset(x, h),
      strokeWidth = 1f
    )
    x += step
  }

  var y = (panY % step)
  while (y < h) {
    drawLine(
      color = Color(0x1448CAE4),
      start = Offset(0f, y),
      end = Offset(w, y),
      strokeWidth = 1f
    )
    y += step
  }
}

private fun DrawScope.drawMapLibreBathymetry(
  soundings: List<S102Sounding>,
  selectedSounding: S102Sounding?,
  w: Float,
  h: Float,
  panX: Float,
  panY: Float,
  scale: Float,
  showSoundingText: Boolean
) {
  val paintInteger = Paint().apply {
    color = android.graphics.Color.WHITE
    textSize = 22f * scale.coerceIn(0.75f, 1.4f)
    textAlign = Paint.Align.RIGHT
    isAntiAlias = true
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
  }

  val paintDecimal = Paint().apply {
    color = android.graphics.Color.WHITE
    textSize = 15f * scale.coerceIn(0.75f, 1.4f)
    textAlign = Paint.Align.LEFT
    isAntiAlias = true
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
  }

  soundings.forEach { snd ->
    val cx = (snd.x * w * 0.8f + w * 0.1f) * scale + panX
    val cy = (snd.y * h * 0.7f + h * 0.15f) * scale + panY

    val depthColor = when {
      snd.depthMeters > 14f -> BathymetryDeep
      snd.depthMeters > 9f -> BathymetryMedium
      snd.depthMeters > 5f -> BathymetryShallow
      else -> BathymetryShoal
    }

    // Outer bathymetry depth cell fill
    drawCircle(
      color = depthColor.copy(alpha = 0.50f),
      radius = 20f * scale,
      center = Offset(cx, cy)
    )

    // Inner depth nucleus
    drawCircle(
      color = depthColor,
      radius = 10f * scale,
      center = Offset(cx, cy)
    )

    // Selection ring
    if (snd.id == selectedSounding?.id) {
      drawCircle(
        color = NoaaGold,
        radius = 26f * scale,
        center = Offset(cx, cy),
        style = Stroke(width = 3.5f)
      )
    }

    // True Nautical Sounding text formatting: Integer with Subscript Tenths (e.g. 15₄)
    if (showSoundingText && scale >= 0.85f) {
      val intPart = snd.depthMeters.toInt()
      val decPart = ((snd.depthMeters - intPart) * 10).toInt()

      drawContext.canvas.nativeCanvas.drawText(
        intPart.toString(),
        cx - 2f * scale,
        cy + 7f * scale,
        paintInteger
      )
      drawContext.canvas.nativeCanvas.drawText(
        decPart.toString(),
        cx + 2f * scale,
        cy + 12f * scale, // subscript offset
        paintDecimal
      )
    }
  }
}

private fun DrawScope.drawDredgedChannel(
  channel: DredgedChannel,
  w: Float,
  h: Float,
  panX: Float,
  panY: Float,
  scale: Float
) {
  val startY = (h * (channel.startY - 0.08f)) * scale + panY
  val endY = (h * (channel.startY + 0.08f)) * scale + panY

  val channelRect = Path().apply {
    moveTo(0f, startY)
    lineTo(w, startY)
    lineTo(w, endY)
    lineTo(0f, endY)
    close()
  }

  // Translucent channel bed fill
  drawPath(
    path = channelRect,
    color = EmeraldSafe.copy(alpha = 0.07f)
  )

  // Channel boundaries with dashed symbology
  val dashEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f))
  drawLine(
    color = EmeraldSafe.copy(alpha = 0.55f),
    start = Offset(0f, startY),
    end = Offset(w, startY),
    strokeWidth = 2.5f,
    pathEffect = dashEffect
  )
  drawLine(
    color = EmeraldSafe.copy(alpha = 0.55f),
    start = Offset(0f, endY),
    end = Offset(w, endY),
    strokeWidth = 2.5f,
    pathEffect = dashEffect
  )

  // Charted Channel Name & Depth Banner
  val textPaint = Paint().apply {
    color = android.graphics.Color.parseColor("#48CAE4")
    textSize = 20f * scale.coerceIn(0.7f, 1.3f)
    textAlign = Paint.Align.CENTER
    isAntiAlias = true
    isFakeBoldText = true
    letterSpacing = 0.15f
  }

  drawContext.canvas.nativeCanvas.drawText(
    "${channel.name} • MIN DEPTH ${channel.chartedDepthMeters}m",
    w * 0.5f,
    (startY + endY) / 2f + 6f * scale,
    textPaint
  )
}

private fun DrawScope.drawAnchorages(
  anchorages: List<AnchorageArea>,
  w: Float,
  h: Float,
  panX: Float,
  panY: Float,
  scale: Float
) {
  val anchorPaint = Paint().apply {
    color = android.graphics.Color.parseColor("#BA68C8")
    textSize = 28f * scale.coerceIn(0.8f, 1.4f)
    textAlign = Paint.Align.CENTER
    isAntiAlias = true
  }

  anchorages.forEach { anc ->
    val cx = (anc.x * w * 0.8f + w * 0.1f) * scale + panX
    val cy = (anc.y * h * 0.7f + h * 0.15f) * scale + panY
    val radius = 48f * scale

    // Dashed anchorage circle
    drawCircle(
      color = ChartMagenta.copy(alpha = 0.6f),
      radius = radius,
      center = Offset(cx, cy),
      style = Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
      )
    )

    // Standard Anchor Symbol ⚓
    drawContext.canvas.nativeCanvas.drawText("⚓", cx, cy + 10f * scale, anchorPaint)

    // Name label
    val labelPaint = Paint().apply {
      color = android.graphics.Color.parseColor("#E0E6ED")
      textSize = 14f * scale.coerceIn(0.7f, 1.2f)
      textAlign = Paint.Align.CENTER
      isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(anc.name, cx, cy + radius + 16f * scale, labelPaint)
  }
}

private fun DrawScope.drawS111Currents(
  currents: List<S111CurrentVector>,
  w: Float,
  h: Float,
  panX: Float,
  panY: Float,
  scale: Float,
  phase: Float
) {
  currents.forEach { cur ->
    val cx = (cur.x * w * 0.8f + w * 0.1f) * scale + panX
    val cy = (cur.y * h * 0.7f + h * 0.15f) * scale + panY

    val len = (28f + cur.speedKnots * 14f) * scale
    val angleRad = Math.toRadians(cur.directionDeg.toDouble())
    val endX = cx + (cos(angleRad) * len).toFloat()
    val endY = cy + (sin(angleRad) * len).toFloat()

    // S-111 Flow vector line
    drawLine(
      color = NeonCyan.copy(alpha = 0.85f),
      start = Offset(cx, cy),
      end = Offset(endX, endY),
      strokeWidth = 2.5f * scale,
      cap = StrokeCap.Round
    )

    // Arrowhead
    val headSize = 9f * scale
    val headAngle = Math.toRadians(32.0)
    val lx = endX - (headSize * cos(angleRad - headAngle)).toFloat()
    val ly = endY - (headSize * sin(angleRad - headAngle)).toFloat()
    val rx = endX - (headSize * cos(angleRad + headAngle)).toFloat()
    val ry = endY - (headSize * sin(angleRad + headAngle)).toFloat()

    drawLine(color = NeonCyan, start = Offset(endX, endY), end = Offset(lx, ly), strokeWidth = 2f * scale, cap = StrokeCap.Round)
    drawLine(color = NeonCyan, start = Offset(endX, endY), end = Offset(rx, ry), strokeWidth = 2f * scale, cap = StrokeCap.Round)

    // Animated hydrodynamic particle streak
    val px = cx + (endX - cx) * phase
    val py = cy + (endY - cy) * phase
    drawCircle(color = Color.White, radius = 3.5f * scale, center = Offset(px, py))
  }
}

private fun DrawScope.drawNauticalHazards(
  hazards: List<NauticalHazard>,
  selectedHazard: NauticalHazard?,
  w: Float,
  h: Float,
  panX: Float,
  panY: Float,
  scale: Float
) {
  val dangerPaint = Paint().apply {
    color = android.graphics.Color.parseColor("#E63946")
    textSize = 14f * scale.coerceIn(0.7f, 1.2f)
    textAlign = Paint.Align.CENTER
    isAntiAlias = true
    isFakeBoldText = true
  }

  hazards.forEach { haz ->
    val cx = (haz.x * w * 0.8f + w * 0.1f) * scale + panX
    val cy = (haz.y * h * 0.7f + h * 0.15f) * scale + panY
    val r = 16f * scale

    // Dotted danger circle
    drawCircle(
      color = CoralDanger,
      radius = r,
      center = Offset(cx, cy),
      style = Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
      )
    )

    // Hazard symbol (+ cross for rock / sunken obstruction)
    drawLine(color = CoralDanger, start = Offset(cx - 8f * scale, cy), end = Offset(cx + 8f * scale, cy), strokeWidth = 2.5f * scale)
    drawLine(color = CoralDanger, start = Offset(cx, cy - 8f * scale), end = Offset(cx, cy + 8f * scale), strokeWidth = 2.5f * scale)

    // Label
    drawContext.canvas.nativeCanvas.drawText(
      "${haz.depthMeters}m",
      cx,
      cy + r + 14f * scale,
      dangerPaint
    )
  }
}

private fun DrawScope.drawMaritimeBuoys(
  buoys: List<MaritimeBuoy>,
  selectedBuoy: MaritimeBuoy?,
  w: Float,
  h: Float,
  panX: Float,
  panY: Float,
  scale: Float,
  beaconPulse: Float
) {
  val labelPaint = Paint().apply {
    color = android.graphics.Color.WHITE
    textSize = 13f * scale.coerceIn(0.7f, 1.2f)
    textAlign = Paint.Align.CENTER
    isAntiAlias = true
    isFakeBoldText = true
  }

  buoys.forEach { buoy ->
    val cx = (buoy.x * w * 0.8f + w * 0.1f) * scale + panX
    val cy = (buoy.y * h * 0.7f + h * 0.15f) * scale + panY

    val isSelected = buoy.id == selectedBuoy?.id
    val bodyColor = when (buoy.type) {
      BuoyType.PORT_LATERAL_CAN -> EmeraldSafe
      BuoyType.STARBOARD_LATERAL_NUN -> LateralRed
      BuoyType.SAFE_WATER_MARK -> Color.White
      else -> AmberAlert
    }

    val lightColor = when (buoy.lightColor) {
      BuoyLightColor.GREEN -> EmeraldSafe
      BuoyLightColor.RED -> LateralRed
      BuoyLightColor.WHITE -> Color.White
      BuoyLightColor.YELLOW -> AmberAlert
    }

    // 1. Flashing Light Halo Beacon
    drawCircle(
      color = lightColor.copy(alpha = 0.25f * beaconPulse),
      radius = (22f + beaconPulse * 12f) * scale,
      center = Offset(cx, cy)
    )

    // 2. Draw S-52 Buoy Shape
    when (buoy.type) {
      BuoyType.PORT_LATERAL_CAN -> {
        // Can / Cylinder shape (square base with flat top)
        val rectSize = 18f * scale
        drawRect(
          color = bodyColor,
          topLeft = Offset(cx - rectSize / 2, cy - rectSize / 2),
          size = Size(rectSize, rectSize)
        )
      }
      BuoyType.STARBOARD_LATERAL_NUN -> {
        // Conical / Nun shape (triangle pointing up)
        val path = Path().apply {
          moveTo(cx, cy - 12f * scale)
          lineTo(cx + 10f * scale, cy + 10f * scale)
          lineTo(cx - 10f * scale, cy + 10f * scale)
          close()
        }
        drawPath(path, bodyColor)
      }
      BuoyType.SAFE_WATER_MARK -> {
        // Sphere shape with red & white vertical bands
        drawCircle(color = LateralRed, radius = 10f * scale, center = Offset(cx, cy))
        drawCircle(color = Color.White, radius = 5f * scale, center = Offset(cx, cy))
      }
      else -> {
        drawCircle(color = bodyColor, radius = 9f * scale, center = Offset(cx, cy))
      }
    }

    // Selection ring
    if (isSelected) {
      drawCircle(color = NoaaGold, radius = 22f * scale, center = Offset(cx, cy), style = Stroke(width = 3f))
    }

    // Buoy Label (e.g. G '1TS')
    drawContext.canvas.nativeCanvas.drawText(
      buoy.name.take(7),
      cx,
      cy - 16f * scale,
      labelPaint
    )
  }
}

private fun DrawScope.drawAisTargets(
  aisTargets: List<AisTarget>,
  selectedAis: AisTarget?,
  w: Float,
  h: Float,
  panX: Float,
  panY: Float,
  scale: Float
) {
  val textPaint = Paint().apply {
    color = android.graphics.Color.parseColor("#48CAE4")
    textSize = 13f * scale.coerceIn(0.7f, 1.2f)
    textAlign = Paint.Align.CENTER
    isAntiAlias = true
  }

  aisTargets.forEach { ais ->
    val cx = (ais.x * w * 0.8f + w * 0.1f) * scale + panX
    val cy = (ais.y * h * 0.7f + h * 0.15f) * scale + panY

    val angleRad = Math.toRadians(ais.headingDeg.toDouble())
    val length = (ais.speedKnots * 4f) * scale

    // AIS Triangle Target (Standard S-52 symbol)
    val triPath = Path().apply {
      moveTo(cx + (14f * cos(angleRad)).toFloat(), cy + (14f * sin(angleRad)).toFloat())
      moveTo(cx + (10f * cos(angleRad)).toFloat(), cy + (10f * sin(angleRad)).toFloat())
      lineTo(cx + (8f * cos(angleRad + 2.5)).toFloat(), cy + (8f * sin(angleRad + 2.5)).toFloat())
      lineTo(cx + (8f * cos(angleRad - 2.5)).toFloat(), cy + (8f * sin(angleRad - 2.5)).toFloat())
      close()
    }

    drawPath(triPath, EmeraldSafe)

    // Heading & Velocity leader
    drawLine(
      color = EmeraldSafe,
      start = Offset(cx, cy),
      end = Offset(cx + (length * cos(angleRad)).toFloat(), cy + (length * sin(angleRad)).toFloat()),
      strokeWidth = 2f * scale
    )

    // Name & Speed label
    drawContext.canvas.nativeCanvas.drawText(
      "${ais.vesselName} (${ais.speedKnots} kts)",
      cx,
      cy + 20f * scale,
      textPaint
    )
  }
}

private fun DrawScope.drawOwnShipSymbol(
  w: Float,
  h: Float,
  panX: Float,
  panY: Float,
  scale: Float,
  speedKnots: Float,
  status: UkcStatus
) {
  val vx = (w * 0.45f) * scale + panX
  val vy = (h * 0.50f) * scale + panY

  val shipColor = when (status) {
    UkcStatus.SAFE -> EmeraldSafe
    UkcStatus.CAUTION -> AmberAlert
    UkcStatus.DANGER -> CoralDanger
  }

  // S-52 Own-Ship Hull Contour
  val shipPath = Path().apply {
    moveTo(vx + 30f * scale, vy + 4f * scale)
    lineTo(vx - 22f * scale, vy - 14f * scale)
    lineTo(vx - 22f * scale, vy + 22f * scale)
    close()
  }

  drawPath(shipPath, shipColor)

  // COG / Velocity vector (6-minute prediction tick)
  val leaderLen = (speedKnots * 9f) * scale
  drawLine(
    color = shipColor.copy(alpha = 0.85f),
    start = Offset(vx, vy),
    end = Offset(vx + leaderLen, vy + leaderLen * 0.35f),
    strokeWidth = 2.5f,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
  )

  // Own-Ship Center Core
  drawCircle(color = Color.White, radius = 3.5f * scale, center = Offset(vx, vy))
}

private fun DrawScope.drawCompassRose(w: Float, h: Float) {
  val cx = w - 46f
  val cy = 46f
  val r = 26f

  // Azimuth Ring
  drawCircle(
    color = NeonCyan.copy(alpha = 0.5f),
    radius = r,
    center = Offset(cx, cy),
    style = Stroke(width = 1.5f)
  )

  // North Arrow
  val northPath = Path().apply {
    moveTo(cx, cy - r)
    lineTo(cx + 6f, cy - 4f)
    lineTo(cx - 6f, cy - 4f)
    close()
  }
  drawPath(northPath, NoaaGold)

  val paint = Paint().apply {
    color = android.graphics.Color.parseColor("#EAA812")
    textSize = 14f
    textAlign = Paint.Align.CENTER
    isAntiAlias = true
    isFakeBoldText = true
  }
  drawContext.canvas.nativeCanvas.drawText("N", cx, cy - r - 4f, paint)
}
