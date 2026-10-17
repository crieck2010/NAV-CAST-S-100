package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AisTarget
import com.example.data.model.MaritimeBuoy
import com.example.data.model.NauticalHazard
import com.example.data.model.S102Sounding
import com.example.data.model.SurveyArea
import com.example.ui.NavCastUiState
import com.example.ui.components.MapLibreNauticalMap
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ChartMagenta
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceHighlight
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NoaaBlue
import com.example.ui.theme.NoaaCobalt
import com.example.ui.theme.NoaaGold
import com.example.ui.theme.OceanicBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

import com.example.ui.components.MapLibreMapView

import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TacticalMapScreen(
  state: NavCastUiState,
  onSelectArea: (SurveyArea) -> Unit,
  onSelectSounding: (S102Sounding?) -> Unit,
  onSelectBuoy: (MaritimeBuoy?) -> Unit,
  onSelectHazard: (NauticalHazard?) -> Unit,
  onSelectAis: (AisTarget?) -> Unit,
  onOpenDukcSheet: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showLayersPanel by remember { mutableStateOf(false) }
  var showAreaDropdown by remember { mutableStateOf(false) }
  var is3dPerspective by remember { mutableStateOf(false) }
  var showDukcBottomSheet by remember { mutableStateOf(false) }

  // MapLibre Layer Controls
  var layerBathymetry by remember { mutableStateOf(true) }
  var layerCurrents by remember { mutableStateOf(true) }
  var layerAtoNBuoys by remember { mutableStateOf(true) }
  var layerHazards by remember { mutableStateOf(true) }
  var layerDredgedChannel by remember { mutableStateOf(true) }
  var layerAnchorages by remember { mutableStateOf(true) }
  var layerAisTraffic by remember { mutableStateOf(true) }
  var layerSoundings by remember { mutableStateOf(true) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(NavyBackground)
      .testTag("tactical_map_screen")
  ) {
    // 1. Native MapLibre GL MapView with Dark Maritime Base Map & S-100 Overlays
    MapLibreMapView(
      surveyArea = state.selectedArea,
      selectedSounding = state.selectedSounding,
      selectedBuoy = state.selectedBuoy,
      selectedHazard = state.selectedHazard,
      selectedAis = state.selectedAis,
      ukcStatus = state.currentDukc.status,
      vesselSpeedKnots = state.vesselProfile.speedKnots,
      layerBathymetry = layerBathymetry,
      layerCurrents = layerCurrents,
      layerAtoNBuoys = layerAtoNBuoys,
      layerHazards = layerHazards,
      layerDredgedChannel = layerDredgedChannel,
      layerAnchorages = layerAnchorages,
      layerAisTraffic = layerAisTraffic,
      layerSoundings = layerSoundings,
      is3dPerspective = is3dPerspective,
      onSoundingTapped = onSelectSounding,
      onBuoyTapped = onSelectBuoy,
      onHazardTapped = onSelectHazard,
      onAisTapped = onSelectAis,
      modifier = Modifier.fillMaxSize()
    )

    // 2. NOAA Header & Region Command Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Survey Region Selector Dropdown
      Box {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = NavySurface.copy(alpha = 0.94f),
          border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceHighlight),
          modifier = Modifier
            .clickable { showAreaDropdown = true }
            .testTag("survey_area_selector_btn")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // NOAA Insignia / Seal Icon
            Box(
              modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(listOf(NoaaCobalt, NoaaBlue))
                ),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "NC",
                color = NoaaGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "NAV-CAST S-100",
                  color = NeonCyan,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(EmeraldSafe.copy(alpha = 0.2f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                  Text("MAPLIBRE", color = EmeraldSafe, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
              }
              Text(
                text = state.selectedArea.name,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        DropdownMenu(
          expanded = showAreaDropdown,
          onDismissRequest = { showAreaDropdown = false },
          modifier = Modifier.background(NavySurface)
        ) {
          state.surveyAreas.forEach { area ->
            DropdownMenuItem(
              text = {
                Column {
                  Text(text = area.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                  Text(text = "${area.ofsModelName} • Station ${area.noaaStationId}", color = NeonCyan, fontSize = 11.sp)
                }
              },
              onClick = {
                onSelectArea(area)
                showAreaDropdown = false
              }
            )
          }
        }
      }

      // Quick Map Action Buttons (Layers & 3D Tilt)
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { is3dPerspective = !is3dPerspective },
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (is3dPerspective) OceanicBlue else NavySurface.copy(alpha = 0.94f))
            .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(10.dp))
            .size(36.dp)
            .testTag("toggle_3d_btn")
        ) {
          Icon(
            imageVector = Icons.Default.ViewInAr,
            contentDescription = "Toggle 3D Perspective",
            tint = if (is3dPerspective) NavyBackground else NeonCyan,
            modifier = Modifier.size(18.dp)
          )
        }

        IconButton(
          onClick = { showLayersPanel = !showLayersPanel },
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (showLayersPanel) OceanicBlue else NavySurface.copy(alpha = 0.94f))
            .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(10.dp))
            .size(36.dp)
            .testTag("map_layers_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = "Toggle S-100 Layers",
            tint = if (showLayersPanel) NavyBackground else NeonCyan,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    // 3. Expandable MapLibre S-100 & Maritime Symbology Layer Manager
    AnimatedVisibility(
      visible = showLayersPanel,
      enter = fadeIn() + slideInVertically(),
      exit = fadeOut() + slideOutVertically(),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 58.dp)
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = NavySurface.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceHighlight),
        modifier = Modifier.padding(horizontal = 12.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "MAPLIBRE S-100 & MARITIME LAYERS",
              color = NeonCyan,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.5.sp
            )
            IconButton(
              onClick = { showLayersPanel = false },
              modifier = Modifier.size(20.dp)
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = layerBathymetry,
              onClick = { layerBathymetry = !layerBathymetry },
              label = { Text("S-102 Bathymetry · SIM", fontSize = 10.sp) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OceanicBlue.copy(alpha = 0.3f), selectedLabelColor = NeonCyan)
            )
            FilterChip(
              selected = layerCurrents,
              onClick = { layerCurrents = !layerCurrents },
              label = { Text("S-111 Currents · SIM", fontSize = 10.sp) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OceanicBlue.copy(alpha = 0.3f), selectedLabelColor = NeonCyan)
            )
            FilterChip(
              selected = layerSoundings,
              onClick = { layerSoundings = !layerSoundings },
              label = { Text("Soundings", fontSize = 10.sp) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OceanicBlue.copy(alpha = 0.3f), selectedLabelColor = NeonCyan)
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = layerAtoNBuoys,
              onClick = { layerAtoNBuoys = !layerAtoNBuoys },
              label = { Text("S-52 Buoys & Lights", fontSize = 10.sp) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OceanicBlue.copy(alpha = 0.3f), selectedLabelColor = NeonCyan)
            )
            FilterChip(
              selected = layerDredgedChannel,
              onClick = { layerDredgedChannel = !layerDredgedChannel },
              label = { Text("Dredged Channel", fontSize = 10.sp) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OceanicBlue.copy(alpha = 0.3f), selectedLabelColor = NeonCyan)
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = layerHazards,
              onClick = { layerHazards = !layerHazards },
              label = { Text("Wrecks & Hazards", fontSize = 10.sp) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OceanicBlue.copy(alpha = 0.3f), selectedLabelColor = NeonCyan)
            )
            FilterChip(
              selected = layerAnchorages,
              onClick = { layerAnchorages = !layerAnchorages },
              label = { Text("Anchorages", fontSize = 10.sp) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OceanicBlue.copy(alpha = 0.3f), selectedLabelColor = NeonCyan)
            )
            FilterChip(
              selected = layerAisTraffic,
              onClick = { layerAisTraffic = !layerAisTraffic },
              label = { Text("AIS Traffic", fontSize = 10.sp) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OceanicBlue.copy(alpha = 0.3f), selectedLabelColor = NeonCyan)
            )
          }
        }
      }
    }

    // 4. Bottom Nautical Feature Inspector & Fairway HUD
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = NavySurface.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceHighlight),
        modifier = Modifier.fillMaxWidth().testTag("map_bottom_info_card")
      ) {
        when {
          // Selected Sounding Inspector
          state.selectedSounding != null -> {
            val snd = state.selectedSounding
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(10.dp)
                      .clip(CircleShape)
                      .background(if (snd.depthMeters > 10f) EmeraldSafe else CoralDanger)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "S-102 BATHYMETRIC SOUNDING INSPECTOR",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                IconButton(
                  onClick = { onSelectSounding(null) },
                  modifier = Modifier.size(20.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
              }

              Spacer(modifier = Modifier.height(4.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(
                    text = String.format(Locale.US, "Charted Depth: %.2f m (%.1f ft)", snd.depthMeters, snd.depthMeters * 3.28084f),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                  )
                  Text(
                    text = "Seabed: ${snd.seabedType} • Uncert: ±${snd.uncertaintyMeters}m",
                    color = TextSecondary,
                    fontSize = 11.sp
                  )
                }

                Button(
                  onClick = { showDukcBottomSheet = true },
                  colors = ButtonDefaults.buttonColors(containerColor = OceanicBlue),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text("Calculate DUKC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          // Selected Buoy Inspector
          state.selectedBuoy != null -> {
            val buoy = state.selectedBuoy
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(10.dp)
                      .clip(CircleShape)
                      .background(EmeraldSafe)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "S-52 AID TO NAVIGATION (AtoN)",
                    color = EmeraldSafe,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                IconButton(onClick = { onSelectBuoy(null) }, modifier = Modifier.size(20.dp)) {
                  Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
              }
              Text(
                text = buoy.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Characteristics: ${buoy.lightCharacteristics} • Type: ${buoy.type.name.replace("_", " ")}${if (buoy.soundSignal != null) " • Signal: ${buoy.soundSignal}" else ""}",
                color = TextSecondary,
                fontSize = 11.sp
              )
            }
          }

          // Selected Hazard Inspector
          state.selectedHazard != null -> {
            val haz = state.selectedHazard
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = CoralDanger, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "MARITIME HAZARD / OBSTRUCTION WARNING",
                    color = CoralDanger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                IconButton(onClick = { onSelectHazard(null) }, modifier = Modifier.size(20.dp)) {
                  Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
              }
              Text(
                text = "${haz.name} (Least Depth: ${haz.depthMeters}m)",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Classification: ${haz.type.name.replace("_", " ")} • Maintain safety standoff distance",
                color = AmberAlert,
                fontSize = 11.sp
              )
            }
          }

          // Selected AIS Target Inspector
          state.selectedAis != null -> {
            val ais = state.selectedAis
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.DirectionsBoat, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "AUTOMATIC IDENTIFICATION SYSTEM (AIS)",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                IconButton(onClick = { onSelectAis(null) }, modifier = Modifier.size(20.dp)) {
                  Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
              }
              Text(
                text = "${ais.vesselName} (MMSI: ${ais.mmsi})",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Type: ${ais.vesselType} • SOG: ${ais.speedKnots} kts • HDG: ${ais.headingDeg.toInt()}° • Draft: ${ais.draftMeters}m • CPA: ${ais.cpaDistanceNm} NM",
                color = TextSecondary,
                fontSize = 11.sp
              )
            }
          }

          // Default Fairway Overview Banner
          else -> {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmeraldSafe))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "NOAA OPERATIONAL FORECAST SYSTEM (OFS)",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
                Text(
                  text = "${state.selectedArea.ofsModelName} Active Grid",
                  color = TextPrimary,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Interactive S-100 Vector Map: Tap buoys, hazards, soundings or vessels",
                  color = NeonCyan,
                  fontSize = 10.sp
                )
              }

              Button(
                onClick = { showDukcBottomSheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceHighlight),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("open_vessel_dukc_btn")
              ) {
                Text("Vessel DUKC", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }

    // Modal Bottom Sheet consuming state from DUKCViewModel
    if (showDukcBottomSheet) {
      VesselDukcBottomSheet(
        onDismissRequest = { showDukcBottomSheet = false }
      )
    }
  }
}
