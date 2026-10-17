package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.NavCastUiState
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceHighlight
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OceanicBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.sin

@Composable
fun ArSurgeScreen(
  state: NavCastUiState,
  onSurgeChanged: (Float) -> Unit,
  onToggleBathymetryMesh: () -> Unit,
  onToggleCurrentVectors: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
  }

  // Animation for wave motion and current particle vectors
  val infiniteTransition = rememberInfiniteTransition(label = "arAnimation")
  val wavePhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (2 * Math.PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(2800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "wavePhase"
  )

  val particleFlow by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "particleFlow"
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(NavyBackground)
      .testTag("ar_surge_screen")
  ) {
    // Background Viewfinder: CameraX or Synthetic Harbor Environment
    if (hasCameraPermission) {
      AndroidView(
        factory = { ctx ->
          val previewView = PreviewView(ctx)
          val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
          cameraProviderFuture.addListener({
            try {
              val cameraProvider = cameraProviderFuture.get()
              val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
              }
              val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
              cameraProvider.unbindAll()
              cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            } catch (_: Exception) {
              // Gracefully handle camera bind failures
            }
          }, ContextCompat.getMainExecutor(ctx))
          previewView
        },
        modifier = Modifier.fillMaxSize()
      )
    } else {
      // Synthetic Nautical Camera Viewport
      SyntheticNauticalEnvironment(modifier = Modifier.fillMaxSize())
    }

    // AR Canvas Overlay: Artificial Horizon, Surge Waterline, Subsurface Mesh, Current Particles
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // 1. AR Center Crosshairs & Artificial Horizon
      drawArCrosshairs(w, h)

      // 2. Subsurface 3D Bathymetric Mesh (underwater)
      if (state.showArBathymetryMesh) {
        drawSubsurfaceMesh(w, h, state.simulatedSurgeMeters, wavePhase)
      }

      // 3. Dynamic Storm Surge Waterline projected on seawall / pilings
      drawSurgeWaterline(w, h, state.simulatedSurgeMeters, wavePhase)

      // 4. S-111 Surface Current Vectors & Particles
      if (state.showArCurrentVectors) {
        drawArCurrentParticles(w, h, particleFlow)
      }
    }

    // Top AR Telemetry HUD
    Column(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // AR Status Pill
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NavySurface.copy(alpha = 0.88f))
            .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(if (hasCameraPermission) EmeraldSafe else AmberAlert)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (hasCameraPermission) "AR CAMERA FEED: ACTIVE" else "AR SYNTHETIC VIEWPORT",
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
        }

        // Camera Permission Request Button (if not granted)
        if (!hasCameraPermission) {
          Button(
            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            colors = ButtonDefaults.buttonColors(containerColor = OceanicBlue),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(32.dp).testTag("enable_camera_btn")
          ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Camera", modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Enable Cam", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Watermark Prediction Banner
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(NavySurface.copy(alpha = 0.85f))
          .border(1.dp, CoralDanger.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "PREDICTED STORM SURGE WATERMARK",
            color = CoralDanger,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = String.format(Locale.US, "+%.2f m above MHHW (CSDL ADCIRC)", state.simulatedSurgeMeters),
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
          )
        }

        Text(
          text = if (state.simulatedSurgeMeters > 2.0f) "COASTAL INUNDATION" else "MINOR OVERWASH",
          color = if (state.simulatedSurgeMeters > 2.0f) CoralDanger else AmberAlert,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // Bottom Controls: Storm Surge Slider & Layer Toggles
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface.copy(alpha = 0.92f)),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 12.dp)
        .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(16.dp))
        .testTag("ar_controls_panel")
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        // Surge Slider
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Simulate Surge Offset:",
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = String.format(Locale.US, "+%.1f meters", state.simulatedSurgeMeters),
            color = CoralDanger,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
          )
        }

        Slider(
          value = state.simulatedSurgeMeters,
          onValueChange = onSurgeChanged,
          valueRange = 0.0f..5.0f,
          colors = SliderDefaults.colors(
            thumbColor = CoralDanger,
            activeTrackColor = CoralDanger,
            inactiveTrackColor = NavySurfaceHighlight
          ),
          modifier = Modifier.testTag("surge_slider")
        )

        // Layer Filter Chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilterChip(
            selected = state.showArBathymetryMesh,
            onClick = onToggleBathymetryMesh,
            label = { Text("Subsurface 3D Mesh", fontSize = 11.sp) },
            leadingIcon = {
              Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = OceanicBlue.copy(alpha = 0.3f),
              selectedLabelColor = NeonCyan
            )
          )

          FilterChip(
            selected = state.showArCurrentVectors,
            onClick = onToggleCurrentVectors,
            label = { Text("S-111 Current Flow", fontSize = 11.sp) },
            leadingIcon = {
              Icon(Icons.Default.Waves, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = OceanicBlue.copy(alpha = 0.3f),
              selectedLabelColor = NeonCyan
            )
          )
        }
      }
    }
  }
}

@Composable
private fun SyntheticNauticalEnvironment(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.45f

    // Sky Gradient
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
        startY = 0f,
        endY = horizonY
      ),
      topLeft = Offset(0f, 0f),
      size = androidx.compose.ui.geometry.Size(w, horizonY)
    )

    // Sea Surface Gradient
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0A2540), Color(0xFF031424)),
        startY = horizonY,
        endY = h
      ),
      topLeft = Offset(0f, horizonY),
      size = androidx.compose.ui.geometry.Size(w, h - horizonY)
    )

    // Pier Pilings / Seawall structure for watermark overlay
    val pilingWidth = 36f
    listOf(0.18f, 0.50f, 0.82f).forEach { normX ->
      val px = w * normX
      drawRect(
        color = Color(0xFF334155),
        topLeft = Offset(px - pilingWidth / 2, horizonY - 80f),
        size = androidx.compose.ui.geometry.Size(pilingWidth, h - horizonY + 80f)
      )
      // High-water stains on pilings
      drawLine(
        color = Color(0xFF475569),
        start = Offset(px - pilingWidth / 2, horizonY),
        end = Offset(px + pilingWidth / 2, horizonY),
        strokeWidth = 3f
      )
    }

    // Distant lighthouse beacon
    drawCircle(
      color = AmberAlert.copy(alpha = 0.8f),
      radius = 4f,
      center = Offset(w * 0.88f, horizonY - 22f)
    )
  }
}

private fun DrawScope.drawArCrosshairs(w: Float, h: Float) {
  val cx = w / 2f
  val cy = h / 2f
  val r = 38f

  // Center Reticle
  drawCircle(
    color = NeonCyan.copy(alpha = 0.45f),
    radius = r,
    center = Offset(cx, cy),
    style = Stroke(width = 1.5f)
  )

  // Cross lines
  drawLine(
    color = NeonCyan.copy(alpha = 0.6f),
    start = Offset(cx - r - 12f, cy),
    end = Offset(cx - 8f, cy),
    strokeWidth = 2f
  )
  drawLine(
    color = NeonCyan.copy(alpha = 0.6f),
    start = Offset(cx + 8f, cy),
    end = Offset(cx + r + 12f, cy),
    strokeWidth = 2f
  )
  drawLine(
    color = NeonCyan.copy(alpha = 0.6f),
    start = Offset(cx, cy - r - 12f),
    end = Offset(cx, cy - 8f),
    strokeWidth = 2f
  )
  drawLine(
    color = NeonCyan.copy(alpha = 0.6f),
    start = Offset(cx, cy + 8f),
    end = Offset(cx, cy + r + 12f),
    strokeWidth = 2f
  )
}

private fun DrawScope.drawSurgeWaterline(w: Float, h: Float, surgeMeters: Float, phase: Float) {
  // Base water level at 55% screen height; each meter of surge raises the waterline
  val surgePixelOffset = surgeMeters * 42f
  val baseWaterY = h * 0.58f - surgePixelOffset

  val surgePath = Path().apply {
    moveTo(0f, baseWaterY)
    var x = 0f
    val step = 20f
    while (x <= w) {
      val waveOffset = (sin(x * 0.02 + phase) * 8f).toFloat()
      lineTo(x, baseWaterY + waveOffset)
      x += step
    }
    lineTo(w, h)
    lineTo(0f, h)
    close()
  }

  // Draw translucent surge flood layer
  drawPath(
    path = surgePath,
    color = OceanicBlue.copy(alpha = 0.22f)
  )

  // Watermark Crest Line
  val crestPath = Path().apply {
    moveTo(0f, baseWaterY)
    var x = 0f
    val step = 15f
    while (x <= w) {
      val waveOffset = (sin(x * 0.02 + phase) * 8f).toFloat()
      lineTo(x, baseWaterY + waveOffset)
      x += step
    }
  }

  drawPath(
    path = crestPath,
    color = CoralDanger,
    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
  )
}

private fun DrawScope.drawSubsurfaceMesh(w: Float, h: Float, surgeMeters: Float, phase: Float) {
  val startY = h * 0.62f
  val meshRows = 6
  val meshCols = 8

  for (r in 0 until meshRows) {
    val rowY = startY + (r * (h - startY) / meshRows)
    val path = Path()
    path.moveTo(0f, rowY)

    for (c in 0..meshCols) {
      val colX = c * (w / meshCols)
      val depthDisplacement = (sin(c * 0.8 + r + phase) * 6f).toFloat()
      path.lineTo(colX, rowY + depthDisplacement)
    }

    drawPath(
      path = path,
      color = NeonCyan.copy(alpha = 0.15f + r * 0.03f),
      style = Stroke(width = 1.2f)
    )
  }

  // Vertical perspective lines
  for (c in 0..meshCols) {
    val topX = c * (w / meshCols)
    val bottomX = (topX - w / 2f) * 1.4f + w / 2f
    drawLine(
      color = NeonCyan.copy(alpha = 0.18f),
      start = Offset(topX, startY),
      end = Offset(bottomX, h),
      strokeWidth = 1f
    )
  }
}

private fun DrawScope.drawArCurrentParticles(w: Float, h: Float, flow: Float) {
  val particleCount = 18
  for (i in 0 until particleCount) {
    val normX = (i.toFloat() / particleCount + flow) % 1.0f
    val px = normX * w
    val py = (h * 0.65f) + (i % 5) * 35f

    // Moving arrow streak
    val length = 32f
    drawLine(
      color = EmeraldSafe.copy(alpha = 0.75f),
      start = Offset(px, py),
      end = Offset(px + length, py + 8f),
      strokeWidth = 2.5f,
      cap = StrokeCap.Round
    )

    drawCircle(
      color = Color.White,
      radius = 2.5f,
      center = Offset(px + length, py + 8f)
    )
  }
}
