package com.crieck2010.navcast.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crieck2010.navcast.data.model.UkcStatus
import com.crieck2010.navcast.ui.NavCastUiState
import com.crieck2010.navcast.ui.theme.AmberAlert
import com.crieck2010.navcast.ui.theme.CoralDanger
import com.crieck2010.navcast.ui.theme.EmeraldSafe
import com.crieck2010.navcast.ui.theme.NavyBackground
import com.crieck2010.navcast.ui.theme.NavySurface
import com.crieck2010.navcast.ui.theme.NavySurfaceHighlight
import com.crieck2010.navcast.ui.theme.NavySurfaceVariant
import com.crieck2010.navcast.ui.theme.NeonCyan
import com.crieck2010.navcast.ui.theme.OceanicBlue
import com.crieck2010.navcast.ui.theme.TextPrimary
import com.crieck2010.navcast.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun NavHudBar(
  state: NavCastUiState,
  onRefreshWaterLevel: () -> Unit,
  onOpenDukcDetails: () -> Unit,
  modifier: Modifier = Modifier
) {
  val ukc = state.currentDukc
  val ukcColor by animateColorAsState(
    targetValue = when (ukc.status) {
      UkcStatus.SAFE -> EmeraldSafe
      UkcStatus.CAUTION -> AmberAlert
      UkcStatus.DANGER -> CoralDanger
    },
    label = "ukcColor"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(800),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(NavySurface.copy(alpha = 0.95f))
      .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
      .padding(horizontal = 14.dp, vertical = 10.dp)
      .testTag("nav_hud_bar")
  ) {
    // Top Row: Geolocation, Area & Water Level
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Coordinates & Area
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(NavySurfaceVariant),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = "Vessel Heading",
            tint = NeonCyan,
            modifier = Modifier.size(18.dp)
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = state.selectedArea.name,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
          )
          Text(
            text = String.format(
              Locale.US,
              "%.4f°N, %.4f°W • %.1f kts",
              state.selectedArea.centerLat,
              kotlin.math.abs(state.selectedArea.centerLon),
              state.vesselProfile.speedKnots
            ),
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }

      // Live Water Level (S-104)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(NavySurfaceVariant)
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Water,
          contentDescription = "S-104 Water Level",
          tint = OceanicBlue,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.End) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "S-104: ",
              color = TextSecondary,
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = String.format(Locale.US, "%+.2f m", state.waterLevel.waterLevelMeters),
              color = NeonCyan,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }
          Text(
            text = state.waterLevel.datum,
            color = TextSecondary,
            fontSize = 9.sp
          )
        }

        Spacer(modifier = Modifier.width(4.dp))
        if (state.isWaterLevelLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = NeonCyan
          )
        } else {
          IconButton(
            onClick = onRefreshWaterLevel,
            modifier = Modifier.size(20.dp).testTag("refresh_water_level_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh NOAA Water Level",
              tint = TextSecondary,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Bottom Row: Dynamic Under-Keel Clearance (DUKC) Safety Gauge
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(NavyBackground)
        .border(1.dp, ukcColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        .clickable { onOpenDukcDetails() }
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .testTag("dukc_gauge_card"),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(12.dp)
            .scale(if (ukc.status == UkcStatus.DANGER) pulseScale else 1f)
            .clip(CircleShape)
            .background(ukcColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = "DYNAMIC UKC CLEARANCE",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
          )
          Row(verticalAlignment = Alignment.Bottom) {
            Text(
              text = String.format(Locale.US, "%.2f m", ukc.underKeelClearanceMeters),
              color = ukcColor,
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraBold,
              fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = when (ukc.status) {
                UkcStatus.SAFE -> "SAFE PASSAGE"
                UkcStatus.CAUTION -> "MARGIN WARNING"
                UkcStatus.DANGER -> "DANGER < 0.6m"
              },
              color = ukcColor,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(bottom = 2.dp)
            )
          }
        }
      }

      // Squat & Dynamic Draft Telemetry pill
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = String.format(Locale.US, "Squat: +%.2fm", ukc.dynamicSquatMeters),
          color = AmberAlert,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Medium
        )
        Text(
          text = String.format(Locale.US, "Dyn Draft: %.2fm", ukc.dynamicDraftMeters),
          color = TextSecondary,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    }
  }
}
