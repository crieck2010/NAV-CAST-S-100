package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.engine.VDatumEngine
import com.example.ui.NavCastUiState
import com.example.ui.theme.AmberAlert
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

@Composable
fun PocketVDatumScreen(
  state: NavCastUiState,
  onParamsChanged: (region: String, inputDatum: String, inputHeight: Double, outputDatum: String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  var inputHeightText by remember { mutableStateOf(state.vdatumInputHeight.toString()) }
  var showRegionMenu by remember { mutableStateOf(false) }
  var showInputDatumMenu by remember { mutableStateOf(false) }
  var showOutputDatumMenu by remember { mutableStateOf(false) }

  val result = state.vdatumResult

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(NavyBackground)
      .verticalScroll(scrollState)
      .padding(16.dp)
      .testTag("pocket_vdatum_screen")
  ) {
    // Header
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(OceanicBlue.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Public,
          contentDescription = "Pocket VDatum",
          tint = NeonCyan,
          modifier = Modifier.size(22.dp)
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(
          text = "POCKET VDATUM ENGINE (OFFLINE)",
          color = NeonCyan,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.5.sp
        )
        Text(
          text = "CSDL Geodetic & Tidal Transformer",
          color = TextPrimary,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Transformation Parameters Card
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurface),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(14.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        // Region Selector
        Text(
          text = "GEODETIC GRID REGION",
          color = TextSecondary,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = NavySurfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showRegionMenu = true }
              .padding(vertical = 4.dp)
              .testTag("vdatum_region_dropdown")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(text = state.vdatumRegion, color = TextPrimary, fontWeight = FontWeight.Bold)
              Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
            }
          }

          DropdownMenu(
            expanded = showRegionMenu,
            onDismissRequest = { showRegionMenu = false },
            modifier = Modifier.background(NavySurface)
          ) {
            VDatumEngine.SUPPORTED_REGIONS.forEach { reg ->
              DropdownMenuItem(
                text = { Text(text = reg, color = TextPrimary) },
                onClick = {
                  onParamsChanged(
                    reg,
                    state.vdatumInputDatum,
                    state.vdatumInputHeight,
                    state.vdatumOutputDatum
                  )
                  showRegionMenu = false
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input Datum Selector
        Text(
          text = "SOURCE DATUM",
          color = TextSecondary,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = NavySurfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showInputDatumMenu = true }
              .padding(vertical = 4.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(text = state.vdatumInputDatum, color = TextPrimary, fontWeight = FontWeight.SemiBold)
              Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = OceanicBlue, modifier = Modifier.size(16.dp))
            }
          }

          DropdownMenu(
            expanded = showInputDatumMenu,
            onDismissRequest = { showInputDatumMenu = false },
            modifier = Modifier.background(NavySurface)
          ) {
            VDatumEngine.SUPPORTED_DATUMS.forEach { datum ->
              DropdownMenuItem(
                text = { Text(text = datum, color = TextPrimary) },
                onClick = {
                  onParamsChanged(
                    state.vdatumRegion,
                    datum,
                    state.vdatumInputHeight,
                    state.vdatumOutputDatum
                  )
                  showInputDatumMenu = false
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Height Input Field
        OutlinedTextField(
          value = inputHeightText,
          onValueChange = { newVal ->
            inputHeightText = newVal
            val parsed = newVal.toDoubleOrNull()
            if (parsed != null) {
              onParamsChanged(
                state.vdatumRegion,
                state.vdatumInputDatum,
                parsed,
                state.vdatumOutputDatum
              )
            }
          },
          label = { Text("Input Elevation / Height (meters)", fontSize = 11.sp) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = NavySurfaceHighlight
          ),
          modifier = Modifier.fillMaxWidth().testTag("vdatum_height_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Output Datum Selector
        Text(
          text = "TARGET OUTPUT DATUM",
          color = TextSecondary,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = NavySurfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showOutputDatumMenu = true }
              .padding(vertical = 4.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(text = state.vdatumOutputDatum, color = NeonCyan, fontWeight = FontWeight.Bold)
              Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
            }
          }

          DropdownMenu(
            expanded = showOutputDatumMenu,
            onDismissRequest = { showOutputDatumMenu = false },
            modifier = Modifier.background(NavySurface)
          ) {
            VDatumEngine.SUPPORTED_DATUMS.forEach { datum ->
              DropdownMenuItem(
                text = { Text(text = datum, color = TextPrimary) },
                onClick = {
                  onParamsChanged(
                    state.vdatumRegion,
                    state.vdatumInputDatum,
                    state.vdatumInputHeight,
                    datum
                  )
                  showOutputDatumMenu = false
                }
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Transformation Result Card
    if (result != null) {
      val feetVal = result.outputHeightMeters * 3.28084

      Card(
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
          .testTag("vdatum_result_card")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "TRANSFORMED ELEVATION (${result.outputDatum})",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = String.format(Locale.US, "%.3f m", result.outputHeightMeters),
                  color = NeonCyan,
                  fontSize = 28.sp,
                  fontWeight = FontWeight.ExtraBold,
                  fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = String.format(Locale.US, "(%.2f ft)", feetVal),
                  color = TextSecondary,
                  fontSize = 13.sp,
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier.padding(bottom = 4.dp)
                )
              }
            }

            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = "${result.outputHeightMeters} m (${result.outputDatum})"
                clipboard.setPrimaryClip(ClipData.newPlainText("VDatum Elevation", text))
                Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NavySurfaceVariant)
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonCyan, modifier = Modifier.size(18.dp))
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Divider(color = NavySurfaceHighlight)
          Spacer(modifier = Modifier.height(12.dp))

          // Geodetic Offsets Breakdown
          Text(
            text = "VERTICAL SEPARATION PROFILE (CSDL VDATUM GRID)",
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(6.dp))

          SeparationRow("Geoid18 Separation (Ellipsoid to NAVD88)", String.format(Locale.US, "%+.2f m", result.ellipsoidToNavd88Meters))
          SeparationRow("TSS Separation (NAVD88 to MLLW)", String.format(Locale.US, "%+.2f m", result.navd88ToMllwMeters))
          SeparationRow("TSS Separation (NAVD88 to MHW)", String.format(Locale.US, "%+.2f m", result.navd88ToMhwMeters))
          SeparationRow("Model Grid Architecture", result.modelVersion)
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Operational Context Card
    Card(
      colors = CardDefaults.cardColors(containerColor = NavySurfaceVariant),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.Top
      ) {
        Icon(
          imageVector = Icons.Default.Info,
          contentDescription = "VDatum Field Info",
          tint = NeonCyan,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Field hydrographers utilize Pocket VDatum on rugged Android tablets to transform RTK-GNSS antenna ellipsoidal heights directly into Mean Lower Low Water (MLLW) chart datums in real time outside cellular coverage.",
          color = TextSecondary,
          fontSize = 11.sp,
          lineHeight = 15.sp
        )
      }
    }
  }
}

@Composable
private fun SeparationRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text = label, color = TextSecondary, fontSize = 11.sp)
    Text(text = value, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
  }
}
