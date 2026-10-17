package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.NavHudBar
import com.example.ui.screens.ArSurgeScreen
import com.example.ui.screens.ModelValidatorScreen
import com.example.ui.screens.PocketVDatumScreen
import com.example.ui.screens.TacticalMapScreen
import com.example.ui.screens.VesselDukcSheet
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceHighlight
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OceanicBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun NavCastApp(
  viewModel: NavCastViewModel = viewModel(),
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.userNotification) {
    state.userNotification?.let { msg ->
      snackbarHostState.showSnackbar(
        message = msg,
        duration = SnackbarDuration.Short
      )
      viewModel.clearNotification()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("navcast_app_root"),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      NavHudBar(
        state = state,
        onRefreshWaterLevel = { viewModel.loadLiveWaterLevel() },
        onOpenDukcDetails = { viewModel.selectTab(NavigationTab.DUKC_CALCULATOR) }
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = NavySurface,
        modifier = Modifier.testTag("navcast_bottom_nav")
      ) {
        NavigationBarItem(
          selected = state.currentTab == NavigationTab.TACTICAL_MAP,
          onClick = { viewModel.selectTab(NavigationTab.TACTICAL_MAP) },
          icon = { Icon(Icons.Default.Map, contentDescription = "Tactical Map", modifier = Modifier.size(20.dp)) },
          label = { Text("Map", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = navBarColors(),
          modifier = Modifier.testTag("tab_map")
        )

        NavigationBarItem(
          selected = state.currentTab == NavigationTab.DUKC_CALCULATOR,
          onClick = { viewModel.selectTab(NavigationTab.DUKC_CALCULATOR) },
          icon = { Icon(Icons.Default.DirectionsBoat, contentDescription = "DUKC Calculator", modifier = Modifier.size(20.dp)) },
          label = { Text("DUKC", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = navBarColors(),
          modifier = Modifier.testTag("tab_dukc")
        )

        NavigationBarItem(
          selected = state.currentTab == NavigationTab.MODEL_VALIDATOR,
          onClick = { viewModel.selectTab(NavigationTab.MODEL_VALIDATOR) },
          icon = { Icon(Icons.Default.Assessment, contentDescription = "CSDL Validator", modifier = Modifier.size(20.dp)) },
          label = { Text("Validator", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = navBarColors(),
          modifier = Modifier.testTag("tab_validator")
        )

        NavigationBarItem(
          selected = state.currentTab == NavigationTab.AR_SURFACE,
          onClick = { viewModel.selectTab(NavigationTab.AR_SURFACE) },
          icon = { Icon(Icons.Default.Visibility, contentDescription = "AR Surge", modifier = Modifier.size(20.dp)) },
          label = { Text("AR Surge", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = navBarColors(),
          modifier = Modifier.testTag("tab_ar")
        )

        NavigationBarItem(
          selected = state.currentTab == NavigationTab.POCKET_VDATUM,
          onClick = { viewModel.selectTab(NavigationTab.POCKET_VDATUM) },
          icon = { Icon(Icons.Default.Public, contentDescription = "Pocket VDatum", modifier = Modifier.size(20.dp)) },
          label = { Text("VDatum", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = navBarColors(),
          modifier = Modifier.testTag("tab_vdatum")
        )
      }
    },
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState) { data ->
        Snackbar(
          containerColor = NavySurfaceHighlight,
          contentColor = TextPrimary,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.padding(16.dp)
        ) {
          Text(text = data.visuals.message, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(NavyBackground)
    ) {
      when (state.currentTab) {
        NavigationTab.TACTICAL_MAP -> TacticalMapScreen(
          state = state,
          onSelectArea = { viewModel.selectSurveyArea(it) },
          onSelectSounding = { viewModel.selectSounding(it) },
          onSelectBuoy = { viewModel.selectBuoy(it) },
          onSelectHazard = { viewModel.selectHazard(it) },
          onSelectAis = { viewModel.selectAis(it) },
          onOpenDukcSheet = { viewModel.selectTab(NavigationTab.DUKC_CALCULATOR) }
        )

        NavigationTab.DUKC_CALCULATOR -> VesselDukcSheet(
          state = state,
          onUpdateVessel = { viewModel.updateVesselProfile(it) },
          onSelectPreset = { viewModel.setVesselPreset(it) },
          onToggleConfinedChannel = { viewModel.setConfinedChannel(it) }
        )

        NavigationTab.MODEL_VALIDATOR -> ModelValidatorScreen(
          state = state,
          onToggleNmeaStream = { viewModel.toggleNmeaStreaming() },
          onManualDepthChanged = { viewModel.setManualObservedDepth(it) },
          onSubmitReport = { viewModel.submitGroundTruthReport(it) },
          onDeleteReport = { viewModel.deleteReport(it) }
        )

        NavigationTab.AR_SURFACE -> ArSurgeScreen(
          state = state,
          onSurgeChanged = { viewModel.setSimulatedSurge(it) },
          onToggleBathymetryMesh = { viewModel.toggleArBathymetryMesh() },
          onToggleCurrentVectors = { viewModel.toggleArCurrentVectors() }
        )

        NavigationTab.POCKET_VDATUM -> PocketVDatumScreen(
          state = state,
          onParamsChanged = { reg, inDat, inHt, outDat ->
            viewModel.setVDatumParams(region = reg, inputDatum = inDat, inputHeight = inHt, outputDatum = outDat)
          }
        )
      }
    }
  }
}

@Composable
private fun navBarColors() = NavigationBarItemDefaults.colors(
  selectedIconColor = NavyBackground,
  selectedTextColor = NeonCyan,
  unselectedIconColor = TextSecondary,
  unselectedTextColor = TextSecondary,
  indicatorColor = NeonCyan
)
