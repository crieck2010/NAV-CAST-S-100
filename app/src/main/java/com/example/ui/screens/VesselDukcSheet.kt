package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UkcStatus
import com.example.ui.DUKCViewModel
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceHighlight
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NoaaGold
import com.example.ui.theme.OceanicBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Modal Bottom Sheet component for Vessel Profile & DUKC Calculator.
 * Consumes reactive state directly from DUKCViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VesselDukcBottomSheet(
  onDismissRequest: () -> Unit,
  viewModel: DUKCViewModel = viewModel(),
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  modifier: Modifier = Modifier
) {
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    containerColor = NavySurface,
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 10.dp)
          .size(width = 44.dp, height = 4.dp)
          .clip(CircleShape)
          .background(NavySurfaceHighlight)
      )
    },
    modifier = modifier.testTag("vessel_dukc_bottom_sheet")
  ) {
    VesselDukcCalculatorContent(
      viewModel = viewModel,
      onClose = onDismissRequest,
      modifier = Modifier.padding(bottom = 24.dp)
    )
  }
}

/**
 * Main Vessel Profile & DUKC Calculator content with sliders for draft, beam, speed,
 * and a real-time UKC gauge display consuming state from DUKCViewModel.
 */
@Composable
fun VesselDukcCalculatorContent(
  viewModel: DUKCViewModel = viewModel(),
  onClose: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val dukc = state.dukcResult
  val scrollState = rememberScrollState()

  val statusColor by animateColorAsState(
    targetValue = when (dukc.status) {
      UkcStatus.SAFE -> EmeraldSafe
      UkcStatus.CAUTION -> AmberAlert
      UkcStatus.DANGER -> CoralDanger
    },
    label = "statusColor"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.96f,
    targetValue = 1.04f,
    animationSpec = infiniteRepeatable(
      animation = tween(900),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(NavyBackground)
      .verticalScroll(scrollState)
      .padding(16.dp)
      .testTag("vessel_dukc_sheet")
  ) {
    // Header Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(OceanicBlue.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.DirectionsBoat,
            contentDescription = "Vessel Profile",
            tint = NeonCyan,
            modifier = Modifier.size(22.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "VESSEL PROFILE & DUKC CALCULATOR",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
          )
          Text(
            text = state.vesselName,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      if (onClose != null) {
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // NOAA CO-OPS Real-Time REST API Water Level Banner (Default Station 8575512)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(NavySurfaceVariant)
        .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(10.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(8.dp).clip(CircleShape).background(EmeraldSafe)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = "NOAA CO-OPS REST API (STATION ${state.stationId})",
            color = NeonCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = String.format(Locale.US, "%s • Water Level: %+.2f m %s", state.stationName, state.s104WaterLevelMeters, state.waterLevelDatum),
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      if (state.isWaterLevelLoading) {
        androidx.compose.material3.CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          strokeWidth = 2.dp,
          color = NeonCyan
        )
      } else {
        IconButton(
          onClick = { viewModel.refreshNoaaWaterLevel() },
          modifier = Modifier.size(24.dp).testTag("refresh_noaa_water_level_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh NOAA Water Level",
            tint = NeonCyan,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Real-Time Dynamic Under-Keel Clearance (UKC) Gauge Display Card
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, statusColor.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
        .testTag("realtime_ukc_gauge_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Circular Clearance Gauge
          Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
          ) {
            Canvas(modifier = Modifier.fillMaxSize().scale(if (dukc.status == UkcStatus.DANGER) pulseScale else 1f)) {
              val w = size.width
              val h = size.height
              val strokeWidth = 10f
              val radius = (w / 2f) - strokeWidth

              // Background Gauge Track
              drawCircle(
                color = NavySurfaceHighlight,
                radius = radius,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = strokeWidth)
              )

              // Active Clearance Progress Arc
              // Map 0m..10m clearance to 0..270 degrees
              val sweep = ((dukc.underKeelClearanceMeters / 10f).coerceIn(0f, 1f) * 270f)
              drawArc(
                color = statusColor,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(strokeWidth, strokeWidth),
                size = Size(w - strokeWidth * 2, h - strokeWidth * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
              )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = String.format(Locale.US, "%.2f", dukc.underKeelClearanceMeters),
                color = statusColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "METERS",
                color = TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
              )
            }
          }

          Spacer(modifier = Modifier.width(16.dp))

          // Gauge Readouts & Status Pill
          Column(modifier = Modifier.weight(1f)) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .border(1.dp, statusColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
              Text(
                text = when (dukc.status) {
                  UkcStatus.SAFE -> "SAFE NAVIGATION"
                  UkcStatus.CAUTION -> "MARGIN WARNING"
                  UkcStatus.DANGER -> "GROUNDING HAZARD"
                },
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = String.format(Locale.US, "Dynamic Squat: +%.2f m", dukc.dynamicSquatMeters),
              color = AmberAlert,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = String.format(Locale.US, "Dynamic Draft: %.2f m", dukc.dynamicDraftMeters),
              color = TextPrimary,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = String.format(Locale.US, "Total Water Depth: %.2f m", dukc.totalWaterDepthMeters),
              color = NeonCyan,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Input Sliders Card: Draft, Beam, Speed
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth().testTag("vessel_sliders_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "VESSEL CONTROL SLIDERS (REAL-TIME SQUAT)",
          color = TextSecondary,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // 1. Static Draft Slider
        DukcSlider(
          title = "Static Vessel Draft",
          valueText = String.format(Locale.US, "%.1f m (%.1f ft)", state.staticDraftMeters, state.staticDraftMeters * 3.28084f),
          value = state.staticDraftMeters,
          range = 1.0f..18.0f,
          accentColor = NeonCyan,
          onValueChange = { viewModel.setStaticDraft(it) },
          testTag = "slider_draft"
        )

        // 2. Vessel Beam Slider
        DukcSlider(
          title = "Vessel Beam (Width)",
          valueText = String.format(Locale.US, "%.1f meters", state.beamMeters),
          value = state.beamMeters,
          range = 3.0f..60.0f,
          accentColor = OceanicBlue,
          onValueChange = { viewModel.setBeam(it) },
          testTag = "slider_beam"
        )

        // 3. Vessel Speed Slider (STW)
        DukcSlider(
          title = "Speed Through Water (STW)",
          valueText = String.format(Locale.US, "%.1f knots", state.speedKnots),
          value = state.speedKnots,
          range = 0.0f..25.0f,
          accentColor = AmberAlert,
          onValueChange = { viewModel.setSpeed(it) },
          testTag = "slider_speed"
        )

        // 4. Safety Depth Margin Slider
        DukcSlider(
          title = "Safety Margin Threshold",
          valueText = String.format(Locale.US, "%.1f meters", state.safetyMarginMeters),
          value = state.safetyMarginMeters,
          range = 0.3f..3.0f,
          accentColor = EmeraldSafe,
          onValueChange = { viewModel.setSafetyMargin(it) },
          testTag = "slider_margin"
        )

        Spacer(modifier = Modifier.height(6.dp))
        Divider(color = NavySurfaceHighlight)
        Spacer(modifier = Modifier.height(8.dp))

        // Confined Channel / Restricted Fairway Switch
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Confined Dredged Fairway",
              color = TextPrimary,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Applies 1.6x Bernoulli hydrodynamic blockage multiplier",
              color = TextSecondary,
              fontSize = 11.sp
            )
          }

          Switch(
            checked = state.isConfinedChannel,
            onCheckedChange = { viewModel.setConfinedChannel(it) },
            colors = SwitchDefaults.colors(
              checkedThumbColor = NavyBackground,
              checkedTrackColor = NeonCyan,
              uncheckedThumbColor = TextSecondary,
              uncheckedTrackColor = NavySurfaceHighlight
            ),
            modifier = Modifier.testTag("switch_confined_channel")
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Pre-Calibrated Vessel Profile Presets
    Text(
      text = "PRE-CALIBRATED VESSEL PRESETS",
      color = TextSecondary,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp
    )
    Spacer(modifier = Modifier.height(6.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      listOf(
        "NOAA S/V Hassler",
        "Hydrographic Launch",
        "Post-Panamax Container",
        "Harbor Tug"
      ).forEach { preset ->
        val isSelected = state.vesselName.contains(preset.take(7)) ||
            (preset.startsWith("NOAA") && state.vesselName.contains("Hassler")) ||
            (preset.startsWith("Hydro") && state.vesselName.contains("Launch")) ||
            (preset.startsWith("Post") && state.vesselName.contains("Atlantic"))

        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) OceanicBlue else NavySurface)
            .border(
              1.dp,
              if (isSelected) NeonCyan else NavySurfaceHighlight,
              RoundedCornerShape(8.dp)
            )
            .clickable { viewModel.loadVesselPreset(preset) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = preset.replace(" ", "\n"),
            color = if (isSelected) NavyBackground else TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // S-100 Fusion Equation Breakdown Card
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = "CSDL S-100 HYDRODYNAMIC FUSION FORMULA",
          color = TextSecondary,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        FormulaRow("S-102 Charted Bathymetric Depth (MLLW)", String.format(Locale.US, "%.2f m", dukc.chartedDepthMeters), TextPrimary)
        FormulaRow("+ S-104 Live Water Level Forecast", String.format(Locale.US, "%+.2f m", dukc.waterLevelMeters), NeonCyan)
        Divider(color = NavySurfaceHighlight, modifier = Modifier.padding(vertical = 4.dp))
        FormulaRow("= Total Water Column Depth", String.format(Locale.US, "%.2f m", dukc.totalWaterDepthMeters), TextPrimary, isBold = true)
        Spacer(modifier = Modifier.height(4.dp))
        FormulaRow("- Static Vessel Draft", String.format(Locale.US, "-%.2f m", dukc.staticDraftMeters), TextSecondary)
        FormulaRow("- Hydrodynamic Squat (Barrass Formula)", String.format(Locale.US, "-%.2f m", dukc.dynamicSquatMeters), AmberAlert)
        Divider(color = NavySurfaceHighlight, modifier = Modifier.padding(vertical = 4.dp))
        FormulaRow("= Dynamic Under-Keel Clearance", String.format(Locale.US, "%.2f m", dukc.underKeelClearanceMeters), statusColor, isBold = true)
      }
    }
  }
}

/**
 * Adapter wrapper to ensure backwards compatibility with previous NavCastUiState callers.
 */
@Composable
fun VesselDukcSheet(
  state: com.example.ui.NavCastUiState,
  onUpdateVessel: ((com.example.data.model.VesselProfile) -> com.example.data.model.VesselProfile) -> Unit,
  onSelectPreset: (String) -> Unit,
  onToggleConfinedChannel: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val dukcViewModel: DUKCViewModel = viewModel()
  VesselDukcCalculatorContent(viewModel = dukcViewModel, modifier = modifier)
}

@Composable
private fun DukcSlider(
  title: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  accentColor: Color,
  onValueChange: (Float) -> Unit,
  testTag: String
) {
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(text = title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
      Text(
        text = valueText,
        color = accentColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = range,
      colors = SliderDefaults.colors(
        thumbColor = accentColor,
        activeTrackColor = accentColor,
        inactiveTrackColor = NavySurfaceHighlight
      ),
      modifier = Modifier.testTag(testTag)
    )
  }
}

@Composable
private fun FormulaRow(
  label: String,
  value: String,
  color: Color,
  isBold: Boolean = false
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      color = if (isBold) TextPrimary else TextSecondary,
      fontSize = 11.sp,
      fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
    )
    Text(
      text = value,
      color = color,
      fontSize = 12.sp,
      fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.SemiBold,
      fontFamily = FontFamily.Monospace
    )
  }
}
