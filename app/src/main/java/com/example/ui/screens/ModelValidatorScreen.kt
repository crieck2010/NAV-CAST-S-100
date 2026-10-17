package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BluetoothSppDevice
import com.example.data.model.DiscoveredVesselInstrument
import com.example.data.model.GroundTruthReport
import com.example.data.model.NmeaLogEntry
import com.example.data.model.SppConnectionState
import com.example.data.model.VesselInstrumentType
import com.example.data.service.ExportFormat
import com.example.ui.NavCastUiState
import com.example.ui.components.VesselInstrumentsSection
import com.example.ui.components.VesselInstrumentCard
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ModelValidatorScreen(
  state: NavCastUiState,
  onToggleNmeaStream: () -> Unit,
  onManualDepthChanged: (String) -> Unit,
  onSubmitReport: (String) -> Unit,
  onDeleteReport: (String) -> Unit,
  onConnectBluetoothDevice: (BluetoothSppDevice) -> Unit = {},
  onDisconnectBluetooth: () -> Unit = {},
  onStartSimulation: () -> Unit = {},
  onRefreshDevices: () -> Unit = {},
  onClearHistoricalLogs: () -> Unit = {},
  onExportLogs: (ExportFormat, (File, Intent) -> Unit) -> Unit = { _, _ -> },
  onStartDiscovery: () -> Unit = {},
  onStopDiscovery: () -> Unit = {},
  onTogglePairInstrument: (DiscoveredVesselInstrument) -> Unit = {},
  onToggleConnectInstrument: (DiscoveredVesselInstrument) -> Unit = {},
  onClearConnectivityStatus: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var reportNotes by remember { mutableStateOf("") }
  var showJsonDialog by remember { mutableStateOf(false) }
  var showBluetoothDialog by remember { mutableStateOf(false) }
  var showExportDialog by remember { mutableStateOf(false) }
  var showClearLogsConfirmDialog by remember { mutableStateOf(false) }
  var selectedTab by remember { mutableIntStateOf(0) } // 0: Live Residual & Reports, 1: Historical Raw Data Log

  val residual = state.observedTransducerDepth - state.modelPredictedDepth
  val isAnomaly = abs(residual) >= 0.5f

  val residualColor = when {
    residual < -0.8f -> CoralDanger
    residual < -0.3f -> AmberAlert
    residual > 0.8f -> OceanicBlue
    else -> EmeraldSafe
  }

  val sppColor = when (state.sppConnectionState) {
    SppConnectionState.STREAMING -> EmeraldSafe
    SppConnectionState.CONNECTED -> OceanicBlue
    SppConnectionState.CONNECTING -> AmberAlert
    SppConnectionState.ERROR -> CoralDanger
    SppConnectionState.DISCONNECTED -> TextSecondary
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(NavyBackground)
      .padding(16.dp)
      .testTag("model_validator_screen")
  ) {
    // Header
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
            imageVector = Icons.Default.Assessment,
            contentDescription = "Model Validator",
            tint = NeonCyan,
            modifier = Modifier.size(22.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "CSDL HYDRODYNAMIC MODEL GROUND-TRUTHING",
            color = NeonCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
          )
          Text(
            text = "OFS In-Situ Residual Engine",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Stream Toggle Button
      Button(
        onClick = onToggleNmeaStream,
        colors = ButtonDefaults.buttonColors(
          containerColor = if (state.isNmeaStreaming) NavySurfaceVariant else OceanicBlue
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("toggle_nmea_stream_btn")
      ) {
        Icon(
          imageVector = if (state.isNmeaStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = "Stream Control",
          tint = if (state.isNmeaStreaming) AmberAlert else NavyBackground,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (state.isNmeaStreaming) "NMEA: Live" else "Stream: Paused",
          fontSize = 11.sp,
          color = if (state.isNmeaStreaming) TextPrimary else NavyBackground,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Bluetooth SPP & Sounder Status Bar
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth().testTag("bluetooth_spp_bar")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = "Bluetooth SPP",
            tint = sppColor,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(sppColor)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "SPP: ${state.sppConnectionState.name}",
                color = sppColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
            }
            Text(
              text = state.connectedSppDevice,
              color = TextSecondary,
              fontSize = 10.sp,
              maxLines = 1
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = {
              onRefreshDevices()
              showBluetoothDialog = true
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(30.dp).testTag("select_bt_device_btn")
          ) {
            Text("BT Devices", fontSize = 10.sp, color = NeonCyan)
          }

          if (state.sppConnectionState != SppConnectionState.DISCONNECTED) {
            Button(
              onClick = {
                if (state.isSppSimulating) {
                  onToggleNmeaStream()
                } else {
                  onDisconnectBluetooth()
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceHighlight),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.height(30.dp)
            ) {
              Text("Disconnect", fontSize = 10.sp, color = AmberAlert)
            }
          } else {
            Button(
              onClick = onStartSimulation,
              colors = ButtonDefaults.buttonColors(containerColor = OceanicBlue),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.height(30.dp)
            ) {
              Text("Simulate SPP", fontSize = 10.sp, color = NavyBackground, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Inbound NMEA 0183 ($SDDPT) Telemetry Bar
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth().testTag("nmea_console_bar")
    ) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (state.isNmeaStreaming) EmeraldSafe else TextSecondary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "NMEA 0183 INBOUND (\$SDDPT):",
              color = TextSecondary,
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold
            )
          }

          val hasValidChecksum = state.latestSddpt?.isChecksumValid ?: true
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (hasValidChecksum) EmeraldSafe.copy(alpha = 0.2f) else AmberAlert.copy(alpha = 0.2f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = if (hasValidChecksum) "CHECKSUM OK" else "CHECKSUM WARN",
              color = if (hasValidChecksum) EmeraldSafe else AmberAlert,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = state.currentNmeaSentence,
          color = NeonCyan,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Navigation Tabs: [Live Residual & Feedback] vs [Historical Raw Data Log & Download]
    TabRow(
      selectedTabIndex = selectedTab,
      containerColor = NavySurface,
      contentColor = NeonCyan,
      indicator = { tabPositions ->
        TabRowDefaults.SecondaryIndicator(
          modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
          color = NeonCyan
        )
      },
      modifier = Modifier.clip(RoundedCornerShape(8.dp)).testTag("validator_tab_row")
    ) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = {
          Text(
            text = "Model Residuals & Reports",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selectedTab == 0) NeonCyan else TextSecondary
          )
        }
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Raw NMEA Log (${state.retainedNmeaLogs.size})",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (selectedTab == 1) NeonCyan else TextSecondary
            )
            if (state.retainedNmeaLogs.isNotEmpty()) {
              Spacer(modifier = Modifier.width(4.dp))
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(EmeraldSafe)
              )
            }
          }
        }
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        text = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Instruments (${state.discoveredInstruments.size})",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (selectedTab == 2) NeonCyan else TextSecondary
            )
            if (state.isScanningInstruments) {
              Spacer(modifier = Modifier.width(4.dp))
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(AmberAlert)
              )
            }
          }
        }
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Tab 0: In-Situ Residual & Ground Truth Reports
    when (selectedTab) {
      0 -> {
        // Split Cards: Observed Depth vs CSDL Model Prediction
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Left: Transducer Observation
        Card(
          colors = CardDefaults.cardColors(containerColor = NavySurface),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .weight(1f)
            .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(12.dp))
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "OBSERVED DEPTH",
              color = TextSecondary,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Echosounder \$SDDPT",
              color = NeonCyan,
              fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = String.format(Locale.US, "%.2f m", state.observedTransducerDepth),
              color = TextPrimary,
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = state.manualDepthInput,
              onValueChange = onManualDepthChanged,
              label = { Text("Manual Overwrite (m)", fontSize = 9.sp) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = NavySurfaceHighlight
              ),
              modifier = Modifier.fillMaxWidth().testTag("manual_depth_input")
            )
          }
        }

        // Right: CSDL OFS Model Prediction
        Card(
          colors = CardDefaults.cardColors(containerColor = NavySurface),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .weight(1f)
            .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(12.dp))
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "CSDL MODEL DEPTH",
              color = TextSecondary,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = state.selectedArea.ofsModelName.take(18) + "...",
              color = OceanicBlue,
              fontSize = 10.sp,
              maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = String.format(Locale.US, "%.2f m", state.modelPredictedDepth),
              color = TextPrimary,
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Source: NBS Bathymetry + CSDL Hydrodynamic Model",
              color = TextSecondary,
              fontSize = 9.sp,
              lineHeight = 12.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Real-Time Residual Meter Card
      Card(
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, residualColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
          .testTag("residual_meter_card")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "IN-SITU RESIDUAL (Observed - Model)",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = String.format(Locale.US, "%+.2f m", residual),
                  color = residualColor,
                  fontSize = 26.sp,
                  fontWeight = FontWeight.ExtraBold,
                  fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = when {
                    residual < -0.8f -> "CRITICAL SHOALING"
                    residual < -0.3f -> "MODEL OVER-PREDICTION"
                    residual > 0.8f -> "CHANNEL DEEPENING"
                    else -> "MODEL CONCURRENCE"
                  },
                  color = residualColor,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(bottom = 6.dp)
                )
              }
            }

            if (isAnomaly) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(residualColor.copy(alpha = 0.2f))
                  .border(1.dp, residualColor, RoundedCornerShape(8.dp))
                  .padding(horizontal = 8.dp, vertical = 6.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Anomaly Alert",
                    tint = residualColor,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "ANOMALY",
                    color = residualColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Flag discrepancy button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                onSubmitReport(reportNotes)
                reportNotes = ""
                showJsonDialog = true
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isAnomaly) CoralDanger else OceanicBlue
              ),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(1f).testTag("flag_discrepancy_btn")
            ) {
              Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Flag Discrepancy",
                tint = NavyBackground,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Flag & Report to CSDL",
                color = NavyBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              )
            }

            if (state.lastReportJson != null) {
              Button(
                onClick = { showJsonDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceHighlight),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("View JSON", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "SUBMITTED CSDL GROUND-TRUTH REPORTS (${state.groundTruthReports.size})",
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(6.dp))

      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (state.groundTruthReports.isEmpty()) {
          item {
            Text(
              text = "No validation discrepancy reports submitted yet.",
              color = TextSecondary,
              fontSize = 12.sp,
              modifier = Modifier.padding(vertical = 12.dp)
            )
          }
        } else {
          items(state.groundTruthReports, key = { it.id }) { r ->
            ReportHistoryItem(report = r, onDelete = { onDeleteReport(r.id) })
          }
        }
      }
      }
      1 -> {
        // Tab 1: Historical Raw Data Log & Download for NOAA Employees
        HistoricalLogSection(
          logs = state.retainedNmeaLogs,
          stats = state.dataServiceStats,
          onOpenExportDialog = { showExportDialog = true },
          onOpenClearConfirm = { showClearLogsConfirmDialog = true },
          modifier = Modifier.weight(1f)
        )
      }
      else -> {
        // Tab 2: Bluetooth Vessel Instruments Discovery & Connectivity Service
        VesselInstrumentsSection(
          instruments = state.discoveredInstruments,
          isScanning = state.isScanningInstruments,
          connectedInstrument = state.connectedInstrument,
          statusMessage = state.connectivityStatusMessage,
          onStartDiscovery = onStartDiscovery,
          onStopDiscovery = onStopDiscovery,
          onTogglePairInstrument = onTogglePairInstrument,
          onToggleConnectInstrument = onToggleConnectInstrument,
          onClearStatusMessage = onClearConnectivityStatus,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }

  // ==========================================
  // DIALOGS & ACTION SHEETS
  // ==========================================

  // 1. Bluetooth Vessel Instruments & Discovery Dialog
  if (showBluetoothDialog) {
    AlertDialog(
      onDismissRequest = { showBluetoothDialog = false },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth", tint = NeonCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Vessel Instruments & SPP", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
          if (state.isScanningInstruments) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AmberAlert, strokeWidth = 2.dp)
          }
        }
      },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Scan and connect to external NMEA-capable vessel instruments (AIS transponders, depth sounders, and NMEA gateways) via Bluetooth SPP:",
            color = TextSecondary,
            fontSize = 11.sp
          )
          Spacer(modifier = Modifier.height(10.dp))

          // Scan Action and Tab switch buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Button(
              onClick = {
                if (state.isScanningInstruments) onStopDiscovery() else onStartDiscovery()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isScanningInstruments) NavySurfaceVariant else OceanicBlue
              ),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.weight(1f).height(32.dp).testTag("dialog_scan_instruments_btn")
            ) {
              Text(
                text = if (state.isScanningInstruments) "Stop Scan" else "Scan Instruments",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isScanningInstruments) AmberAlert else NavyBackground
              )
            }

            OutlinedButton(
              onClick = {
                selectedTab = 2
                showBluetoothDialog = false
              },
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.weight(1f).height(32.dp).testTag("dialog_switch_to_tab2_btn")
            ) {
              Text("Open Full Tab", fontSize = 10.sp, color = NeonCyan)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // High-fidelity Simulator quick launcher
          Card(
            colors = CardDefaults.cardColors(containerColor = NavySurfaceHighlight),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                onStartSimulation()
                showBluetoothDialog = false
                Toast.makeText(context, "Started NOAA Survey Simulator", Toast.LENGTH_SHORT).show()
              }
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.PlayArrow, contentDescription = "Simulate", tint = EmeraldSafe, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text("NOAA Survey Echosounder (Simulator)", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Synthesizes realistic 200kHz \$SDDPT with wave heave", color = TextSecondary, fontSize = 9.sp)
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Discovered Instruments (${state.discoveredInstruments.size}):",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(6.dp))

          LazyColumn(modifier = Modifier.height(200.dp)) {
            items(state.discoveredInstruments, key = { it.id }) { inst ->
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (inst.isConnected) NavySurfaceHighlight else NavyBackground
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 3.dp)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = inst.name,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                      )
                      if (inst.isConnected) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(EmeraldSafe))
                      }
                    }
                    Text(
                      text = "${inst.type.label} • ${inst.rssi} dBm",
                      color = TextSecondary,
                      fontSize = 9.sp
                    )
                  }

                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Pair toggle
                    OutlinedButton(
                      onClick = { onTogglePairInstrument(inst) },
                      shape = RoundedCornerShape(4.dp),
                      modifier = Modifier.height(26.dp)
                    ) {
                      Text(
                        text = if (inst.isBonded) "Paired" else "Pair",
                        fontSize = 9.sp,
                        color = if (inst.isBonded) EmeraldSafe else NeonCyan
                      )
                    }

                    // Connect toggle
                    Button(
                      onClick = { onToggleConnectInstrument(inst) },
                      colors = ButtonDefaults.buttonColors(
                        containerColor = if (inst.isConnected) CoralDanger else OceanicBlue
                      ),
                      shape = RoundedCornerShape(4.dp),
                      modifier = Modifier.height(26.dp)
                    ) {
                      Text(
                        text = if (inst.isConnected) "Disconnect" else "Connect",
                        fontSize = 9.sp,
                        color = NavyBackground,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showBluetoothDialog = false }) {
          Text("Close", color = TextSecondary)
        }
      },
      containerColor = NavySurface
    )
  }

  // 2. Download / Export Format Dialog
  if (showExportDialog) {
    AlertDialog(
      onDismissRequest = { showExportDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.FileDownload, contentDescription = "Download", tint = NeonCyan)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Download Historical Raw Data Log", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column {
          Text(
            text = "Export ${state.retainedNmeaLogs.size} retained NMEA 0183 \$SDDPT records for analysis by NOAA CSDL hydrographers:",
            color = TextSecondary,
            fontSize = 12.sp
          )
          Spacer(modifier = Modifier.height(12.dp))

          // Format 1: CSV
          ExportFormatOption(
            title = "Hydrographic CSV (.csv)",
            subtitle = "Columns: UTC ISO, Depth (m), Offset, Status, Coordinates for GIS & Excel",
            icon = Icons.Default.Assessment,
            onClick = {
              showExportDialog = false
              onExportLogs(ExportFormat.CSV) { file, intent ->
                Toast.makeText(context, "Generated ${file.name}", Toast.LENGTH_SHORT).show()
                context.startActivity(Intent.createChooser(intent, "Download NOAA Hydrographic CSV"))
              }
            }
          )

          Spacer(modifier = Modifier.height(8.dp))

          // Format 2: Raw NMEA
          ExportFormatOption(
            title = "Raw NMEA Stream (.nmea)",
            subtitle = "Native \$SDDPT sentences for CARIS HIPS/SIPS, Pydro, & Hypack post-processing",
            icon = Icons.Default.History,
            onClick = {
              showExportDialog = false
              onExportLogs(ExportFormat.RAW_NMEA) { file, intent ->
                Toast.makeText(context, "Generated ${file.name}", Toast.LENGTH_SHORT).show()
                context.startActivity(Intent.createChooser(intent, "Download Raw NMEA Stream"))
              }
            }
          )

          Spacer(modifier = Modifier.height(8.dp))

          // Format 3: NOAA JSON
          ExportFormatOption(
            title = "NOAA CSDL JSON Dataset (.json)",
            subtitle = "Kotlin Serialization schema with vessel metadata & survey specs",
            icon = Icons.Default.Storage,
            onClick = {
              showExportDialog = false
              onExportLogs(ExportFormat.JSON) { file, intent ->
                Toast.makeText(context, "Generated ${file.name}", Toast.LENGTH_SHORT).show()
                context.startActivity(Intent.createChooser(intent, "Download NOAA CSDL JSON"))
              }
            }
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showExportDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = NavySurface
    )
  }

  // 3. Clear Confirmation Dialog
  if (showClearLogsConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showClearLogsConfirmDialog = false },
      title = { Text("Purge Historical Log?", color = TextPrimary, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "Are you sure you want to clear all retained NMEA raw records from both memory and local persistent storage?",
          color = TextSecondary,
          fontSize = 12.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onClearHistoricalLogs()
            showClearLogsConfirmDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = CoralDanger)
        ) {
          Text("Clear All Logs", color = TextPrimary)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearLogsConfirmDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = NavySurface
    )
  }

  // 4. JSON Preview Dialog
  if (showJsonDialog && state.lastReportJson != null) {
    AlertDialog(
      onDismissRequest = { showJsonDialog = false },
      title = {
        Text(
          text = "CSDL Telemetry Packet",
          color = TextPrimary,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column {
          Text(
            text = "Automated JSON payload formatted for NOAA Coast Survey Development Laboratory modeling feedback ingestion:",
            color = TextSecondary,
            fontSize = 11.sp
          )
          Spacer(modifier = Modifier.height(8.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(NavyBackground)
              .padding(10.dp)
          ) {
            Text(
              text = state.lastReportJson ?: "",
              color = NeonCyan,
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              lineHeight = 14.sp
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("CSDL Telemetry", state.lastReportJson)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Telemetry JSON copied to clipboard", Toast.LENGTH_SHORT).show()
            showJsonDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = OceanicBlue)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Copy JSON", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showJsonDialog = false }) {
          Text("Close", color = TextSecondary)
        }
      },
      containerColor = NavySurface
    )
  }
}

@Composable
private fun ExportFormatOption(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = NavyBackground),
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(OceanicBlue.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextSecondary, fontSize = 10.sp)
      }
      Icon(Icons.Default.Download, contentDescription = "Download", tint = NeonCyan, modifier = Modifier.size(16.dp))
    }
  }
}

@Composable
private fun HistoricalLogSection(
  logs: List<NmeaLogEntry>,
  stats: com.example.data.service.DataServiceStats,
  onOpenExportDialog: () -> Unit,
  onOpenClearConfirm: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxWidth()) {
    // Stats Dashboard Card
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().testTag("log_stats_card")
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Storage, contentDescription = "Storage", tint = NeonCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "NOAA CSDL RAW NMEA 0183 ARCHIVE",
              color = NeonCyan,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(EmeraldSafe.copy(alpha = 0.2f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "${logs.size} RECORDS RETAINED",
              color = EmeraldSafe,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Summary Metric Badges
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          StatChip(title = "Total Pings", value = "${stats.totalPacketsReceived}", modifier = Modifier.weight(1f))
          StatChip(title = "Valid \$SDDPT", value = "${stats.validSddptCount}", modifier = Modifier.weight(1f))
          StatChip(title = "Avg Depth", value = String.format(Locale.US, "%.2fm", stats.averageDepthMeters), modifier = Modifier.weight(1f))
          StatChip(title = "Depth Range", value = String.format(Locale.US, "%.1f-%.1f", stats.minDepthMeters, stats.maxDepthMeters), modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions: Download / Export & Clear
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = onOpenExportDialog,
            enabled = logs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = OceanicBlue),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).testTag("download_logs_btn")
          ) {
            Icon(Icons.Default.Download, contentDescription = "Download", tint = NavyBackground, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Download / Export Log", color = NavyBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }

          OutlinedButton(
            onClick = onOpenClearConfirm,
            enabled = logs.isNotEmpty(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("clear_logs_btn")
          ) {
            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear", color = TextSecondary, fontSize = 11.sp)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
      text = "REAL-TIME LOG STREAM (PERSISTENT DISK STORAGE)",
      color = TextSecondary,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(6.dp))

    // Log list
    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (logs.isEmpty()) {
        item {
          Text(
            text = "No NMEA raw telemetry has been recorded yet. Start the stream or connect an external sounder to begin persistent retention.",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 14.dp)
          )
        }
      } else {
        items(logs, key = { it.id }) { item ->
          NmeaLogRow(entry = item)
        }
      }
    }
  }
}

@Composable
private fun StatChip(
  title: String,
  value: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(NavyBackground)
      .padding(horizontal = 6.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(title, color = TextSecondary, fontSize = 8.sp, maxLines = 1)
      Text(value, color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
  }
}

@Composable
private fun NmeaLogRow(entry: NmeaLogEntry) {
  Card(
    colors = CardDefaults.cardColors(containerColor = NavySurface),
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = entry.isoTimestamp.substringAfter("T").take(12),
            color = TextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = entry.rawSentence,
            color = NeonCyan,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
          )
        }
        Text(
          text = "Depth: ${String.format(Locale.US, "%.2fm", entry.depthMeters)} | Offset: ${entry.offsetMeters}m | Source: ${entry.sourceDevice}",
          color = TextSecondary,
          fontSize = 9.sp
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .background(if (entry.isValidChecksum) EmeraldSafe.copy(alpha = 0.2f) else AmberAlert.copy(alpha = 0.2f))
          .padding(horizontal = 5.dp, vertical = 2.dp)
      ) {
        Text(
          text = if (entry.isValidChecksum) "OK" else "WARN",
          color = if (entry.isValidChecksum) EmeraldSafe else AmberAlert,
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )
      }
    }
  }
}

@Composable
private fun ReportHistoryItem(
  report: GroundTruthReport,
  onDelete: () -> Unit
) {
  val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(report.timestamp))
  val itemColor = if (report.residualMeters < -0.5f) CoralDanger else NeonCyan

  Card(
    colors = CardDefaults.cardColors(containerColor = NavySurface),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = report.id,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = dateStr,
            color = TextSecondary,
            fontSize = 10.sp
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${report.discrepancyType}: Obs ${report.observedDepthMeters}m vs Model ${report.modelDepthMeters}m (Δ ${report.residualMeters}m)",
          color = itemColor,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = report.notes,
          color = TextSecondary,
          fontSize = 10.sp,
          maxLines = 1
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete Report",
          tint = TextSecondary,
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}
