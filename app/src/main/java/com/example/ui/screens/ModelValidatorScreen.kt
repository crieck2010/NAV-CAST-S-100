package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.model.GroundTruthReport
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
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var reportNotes by remember { mutableStateOf("") }
  var showJsonDialog by remember { mutableStateOf(false) }

  val residual = state.observedTransducerDepth - state.modelPredictedDepth
  val isAnomaly = abs(residual) >= 0.5f

  val residualColor = when {
    residual < -0.8f -> CoralDanger
    residual < -0.3f -> AmberAlert
    residual > 0.8f -> OceanicBlue
    else -> EmeraldSafe
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

    Spacer(modifier = Modifier.height(14.dp))

    // NMEA Console Bar
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (state.isNmeaStreaming) EmeraldSafe else TextSecondary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "NMEA 0183 (Transducer Inbound): ",
          color = TextSecondary,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace
        )
        Text(
          text = state.currentNmeaSentence,
          color = NeonCyan,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

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
            text = "Echosounder (200kHz)",
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
          // Manual Entry field for calibration
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
            text = "Grid Source: National Bathymetric Source (NBS) + CSDL OFS Grid",
            color = TextSecondary,
            fontSize = 9.sp,
            lineHeight = 12.sp
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Real-Time Residual Meter Card
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, residualColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
        .testTag("residual_meter_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
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
                fontSize = 28.sp,
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

    Spacer(modifier = Modifier.height(14.dp))

    // Telemetry Reports History Header
    Text(
      text = "SUBMITTED CSDL GROUND-TRUTH REPORTS (${state.groundTruthReports.size})",
      color = TextSecondary,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(6.dp))

    // Reports List
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
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

  // JSON Preview Dialog
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
