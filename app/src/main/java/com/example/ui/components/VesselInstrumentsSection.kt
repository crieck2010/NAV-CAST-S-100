package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DiscoveredVesselInstrument
import com.example.data.model.VesselInstrumentType
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

/**
 * UI Component for Bluetooth vessel instrument discovery, scanning,
 * and toggling pairing and connection status.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VesselInstrumentsSection(
  instruments: List<DiscoveredVesselInstrument>,
  isScanning: Boolean,
  connectedInstrument: DiscoveredVesselInstrument?,
  statusMessage: String?,
  onStartDiscovery: () -> Unit,
  onStopDiscovery: () -> Unit,
  onTogglePairInstrument: (DiscoveredVesselInstrument) -> Unit,
  onToggleConnectInstrument: (DiscoveredVesselInstrument) -> Unit,
  onClearStatusMessage: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var selectedCategoryFilter by remember { mutableStateOf<VesselInstrumentType?>(null) }

  val filteredInstruments = remember(instruments, selectedCategoryFilter) {
    if (selectedCategoryFilter == null) {
      instruments
    } else {
      instruments.filter { it.type == selectedCategoryFilter }
    }
  }

  // Animation for radar pulse during scanning
  val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.85f,
    targetValue = 1.25f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "PulseScale"
  )

  Column(modifier = modifier.fillMaxSize()) {
    // Discovery Header Card with Radar Scan Controls
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, if (isScanning) NeonCyan.copy(alpha = 0.5f) else NavySurfaceHighlight, RoundedCornerShape(12.dp))
        .testTag("vessel_discovery_header_card")
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                  if (isScanning) {
                    Brush.radialGradient(listOf(NeonCyan.copy(alpha = 0.35f), NavyBackground))
                  } else {
                    Brush.radialGradient(listOf(OceanicBlue.copy(alpha = 0.25f), NavyBackground))
                  }
                ),
              contentAlignment = Alignment.Center
            ) {
              if (isScanning) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.2f))
                )
                Icon(
                  imageVector = Icons.Default.BluetoothSearching,
                  contentDescription = "Scanning",
                  tint = NeonCyan,
                  modifier = Modifier.size(22.dp)
                )
              } else {
                Icon(
                  imageVector = Icons.Default.Bluetooth,
                  contentDescription = "Bluetooth Instruments",
                  tint = OceanicBlue,
                  modifier = Modifier.size(22.dp)
                )
              }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
              Text(
                text = "VESSEL INSTRUMENTS DISCOVERY",
                color = NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
              )
              Text(
                text = if (isScanning) "Scanning 2.4GHz NMEA SPP band..." else "NMEA 0183 / 2000 Bridges",
                color = if (isScanning) AmberAlert else TextSecondary,
                fontSize = 11.sp
              )
            }
          }

          // Scan / Stop Discovery Action Button
          Button(
            onClick = {
              if (isScanning) onStopDiscovery() else onStartDiscovery()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isScanning) NavySurfaceVariant else OceanicBlue
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("scan_instruments_toggle_btn")
          ) {
            if (isScanning) {
              CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = AmberAlert,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Stop Scan", fontSize = 11.sp, color = AmberAlert, fontWeight = FontWeight.Bold)
            } else {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Start Scan",
                tint = NavyBackground,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Scan Instruments", fontSize = 11.sp, color = NavyBackground, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Active Status Message Banner
        AnimatedVisibility(
          visible = !statusMessage.isNullOrBlank(),
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          Spacer(modifier = Modifier.height(10.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(NavyBackground)
              .border(1.dp, OceanicBlue.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Info,
                  contentDescription = "Status",
                  tint = NeonCyan,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = statusMessage ?: "",
                  color = TextPrimary,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium
                )
              }
              IconButton(
                onClick = onClearStatusMessage,
                modifier = Modifier.size(18.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Dismiss",
                  tint = TextSecondary,
                  modifier = Modifier.size(12.dp)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          FilterChip(
            selected = selectedCategoryFilter == null,
            onClick = { selectedCategoryFilter = null },
            label = { Text("All (${instruments.size})", fontSize = 10.sp) },
            colors = filterChipColors(selectedCategoryFilter == null),
            modifier = Modifier.height(28.dp).testTag("filter_all_instruments")
          )

          val depthCount = instruments.count { it.type == VesselInstrumentType.DEPTH_SOUNDER }
          FilterChip(
            selected = selectedCategoryFilter == VesselInstrumentType.DEPTH_SOUNDER,
            onClick = {
              selectedCategoryFilter = if (selectedCategoryFilter == VesselInstrumentType.DEPTH_SOUNDER) null else VesselInstrumentType.DEPTH_SOUNDER
            },
            label = { Text("Depth Sounders ($depthCount)", fontSize = 10.sp) },
            colors = filterChipColors(selectedCategoryFilter == VesselInstrumentType.DEPTH_SOUNDER),
            modifier = Modifier.height(28.dp).testTag("filter_depth_sounders")
          )

          val aisCount = instruments.count { it.type == VesselInstrumentType.AIS_TRANSPONDER }
          FilterChip(
            selected = selectedCategoryFilter == VesselInstrumentType.AIS_TRANSPONDER,
            onClick = {
              selectedCategoryFilter = if (selectedCategoryFilter == VesselInstrumentType.AIS_TRANSPONDER) null else VesselInstrumentType.AIS_TRANSPONDER
            },
            label = { Text("AIS ($aisCount)", fontSize = 10.sp) },
            colors = filterChipColors(selectedCategoryFilter == VesselInstrumentType.AIS_TRANSPONDER),
            modifier = Modifier.height(28.dp).testTag("filter_ais")
          )

          val gatewayCount = instruments.count { it.type == VesselInstrumentType.NMEA_GATEWAY }
          FilterChip(
            selected = selectedCategoryFilter == VesselInstrumentType.NMEA_GATEWAY,
            onClick = {
              selectedCategoryFilter = if (selectedCategoryFilter == VesselInstrumentType.NMEA_GATEWAY) null else VesselInstrumentType.NMEA_GATEWAY
            },
            label = { Text("Gateways ($gatewayCount)", fontSize = 10.sp) },
            colors = filterChipColors(selectedCategoryFilter == VesselInstrumentType.NMEA_GATEWAY),
            modifier = Modifier.height(28.dp).testTag("filter_gateways")
          )

          val gpsCount = instruments.count { it.type == VesselInstrumentType.GPS_RECEIVER }
          FilterChip(
            selected = selectedCategoryFilter == VesselInstrumentType.GPS_RECEIVER,
            onClick = {
              selectedCategoryFilter = if (selectedCategoryFilter == VesselInstrumentType.GPS_RECEIVER) null else VesselInstrumentType.GPS_RECEIVER
            },
            label = { Text("GPS / GNSS ($gpsCount)", fontSize = 10.sp) },
            colors = filterChipColors(selectedCategoryFilter == VesselInstrumentType.GPS_RECEIVER),
            modifier = Modifier.height(28.dp).testTag("filter_gps")
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // List of Discovered Vessel Instruments
    if (filteredInstruments.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .clip(RoundedCornerShape(12.dp))
          .background(NavySurface)
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.BluetoothSearching,
            contentDescription = "No instruments",
            tint = TextSecondary,
            modifier = Modifier.size(36.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "No vessel instruments matching filter",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Click 'Scan Instruments' to search nearby Bluetooth NMEA bridges or clear filters.",
            color = TextSecondary,
            fontSize = 11.sp
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(filteredInstruments, key = { it.id }) { instrument ->
          VesselInstrumentCard(
            instrument = instrument,
            onTogglePair = { onTogglePairInstrument(instrument) },
            onToggleConnect = { onToggleConnectInstrument(instrument) }
          )
        }
      }
    }
  }
}

/**
 * Individual vessel instrument item with Pair/Unpair and Connect/Disconnect toggles.
 */
@Composable
fun VesselInstrumentCard(
  instrument: DiscoveredVesselInstrument,
  onTogglePair: () -> Unit,
  onToggleConnect: () -> Unit,
  modifier: Modifier = Modifier
) {
  val icon = getInstrumentIcon(instrument.type)
  val isConnected = instrument.isConnected
  val isConnecting = instrument.isConnecting
  val isBonded = instrument.isBonded

  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (isConnected) NavySurfaceHighlight else NavySurface
    ),
    shape = RoundedCornerShape(10.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = if (isConnected) EmeraldSafe.copy(alpha = 0.6f) else NavySurfaceHighlight,
        shape = RoundedCornerShape(10.dp)
      )
      .testTag("instrument_card_${instrument.id.replace(':', '_')}")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(
                if (isConnected) EmeraldSafe.copy(alpha = 0.2f) else OceanicBlue.copy(alpha = 0.15f)
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = icon,
              contentDescription = instrument.type.label,
              tint = if (isConnected) EmeraldSafe else NeonCyan,
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(10.dp))

          Column {
            Text(
              text = instrument.name,
              color = TextPrimary,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1
            )
            Text(
              text = "${instrument.manufacturer} • ${instrument.address}",
              color = TextSecondary,
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              maxLines = 1
            )
          }
        }

        // Signal RSSI Badge
        RssiBadge(rssi = instrument.rssi, bars = instrument.signalBars)
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Instrument Metadata & Sentences Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(NavyBackground)
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = instrument.type.label,
              color = OceanicBlue,
              fontSize = 9.sp,
              fontWeight = FontWeight.SemiBold
            )
          }

          for (sentence in instrument.supportedSentences.take(2)) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(NavyBackground)
                .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
              Text(
                text = sentence,
                color = NeonCyan,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        if (isConnected) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(EmeraldSafe)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "STREAMING",
              color = EmeraldSafe,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Action Toggles: [Pair/Unpair] and [Connect/Disconnect]
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Toggle to Pair
        OutlinedButton(
          onClick = onTogglePair,
          shape = RoundedCornerShape(6.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isBonded) EmeraldSafe else NeonCyan
          ),
          modifier = Modifier
            .weight(1f)
            .height(34.dp)
            .testTag("toggle_pair_btn_${instrument.id.replace(':', '_')}")
        ) {
          Icon(
            imageVector = if (isBonded) Icons.Default.Check else Icons.Default.Link,
            contentDescription = if (isBonded) "Paired" else "Pair",
            tint = if (isBonded) EmeraldSafe else NeonCyan,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isBonded) "Paired" else "Pair Device",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        // Toggle to Connect
        Button(
          onClick = onToggleConnect,
          shape = RoundedCornerShape(6.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = when {
              isConnected -> CoralDanger
              isConnecting -> NavySurfaceVariant
              else -> OceanicBlue
            }
          ),
          modifier = Modifier
            .weight(1.2f)
            .height(34.dp)
            .testTag("toggle_connect_btn_${instrument.id.replace(':', '_')}")
        ) {
          if (isConnecting) {
            CircularProgressIndicator(
              modifier = Modifier.size(14.dp),
              color = NeonCyan,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Connecting...", fontSize = 11.sp, color = TextPrimary)
          } else if (isConnected) {
            Icon(
              imageVector = Icons.Default.PowerSettingsNew,
              contentDescription = "Disconnect",
              tint = NavyBackground,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Disconnect", fontSize = 11.sp, color = NavyBackground, fontWeight = FontWeight.Bold)
          } else {
            Icon(
              imageVector = Icons.Default.BluetoothConnected,
              contentDescription = "Connect",
              tint = NavyBackground,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Connect & Stream", fontSize = 11.sp, color = NavyBackground, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun RssiBadge(rssi: Int, bars: Int) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(NavyBackground)
      .padding(horizontal = 6.dp, vertical = 3.dp)
  ) {
    // 4 vertical signal bars
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.Bottom) {
      for (i in 1..4) {
        val barHeight = (4 + i * 2.5).dp
        val active = i <= bars
        Box(
          modifier = Modifier
            .width(2.5.dp)
            .height(barHeight)
            .background(if (active) EmeraldSafe else TextSecondary.copy(alpha = 0.3f))
        )
      }
    }
    Spacer(modifier = Modifier.width(5.dp))
    Text(
      text = "$rssi dBm",
      color = TextSecondary,
      fontSize = 9.sp,
      fontFamily = FontFamily.Monospace
    )
  }
}

private fun getInstrumentIcon(type: VesselInstrumentType): ImageVector {
  return when (type) {
    VesselInstrumentType.DEPTH_SOUNDER -> Icons.Default.Sensors
    VesselInstrumentType.AIS_TRANSPONDER -> Icons.Default.DirectionsBoat
    VesselInstrumentType.NMEA_GATEWAY -> Icons.Default.Router
    VesselInstrumentType.GPS_RECEIVER -> Icons.Default.MyLocation
    VesselInstrumentType.HEADING_SENSOR -> Icons.Default.Explore
    VesselInstrumentType.WIND_STATION -> Icons.Default.Air
    VesselInstrumentType.OTHER_NMEA -> Icons.Default.Radio
  }
}

@Composable
private fun filterChipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
  containerColor = NavyBackground,
  labelColor = TextSecondary,
  selectedContainerColor = OceanicBlue.copy(alpha = 0.25f),
  selectedLabelColor = NeonCyan
)
