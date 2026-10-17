package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.NavCastViewModel
import com.example.ui.NavigationTab
import com.example.ui.components.MapLibreStyleHelper
import com.example.ui.components.NavHudBar
import com.example.ui.screens.ArSurgeScreen
import com.example.ui.screens.ModelValidatorScreen
import com.example.ui.screens.PocketVDatumScreen
import com.example.ui.screens.TacticalMapScreen
import com.example.ui.screens.VesselDukcSheet
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceHighlight
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Standard Dark Maritime MapLibre Style URL and fallback JSON matching the #0B132B color palette.
 */
const val MAPLIBRE_DARK_MARITIME_STYLE_URL = "https://demotiles.maplibre.org/style.json"

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize the MapLibre GL Native Android SDK with application context
    MapLibre.getInstance(this)

    setContent {
      MyApplicationTheme {
        NavCastMainShell()
      }
    }
  }
}

/**
 * MapView composable using the MapLibre GL Native Android library.
 * Initializes with a dark maritime style matching the #0B132B color palette.
 */
@Composable
fun MapLibreMapView(
  targetLat: Double = 36.9850,
  targetLon: Double = -76.1100,
  zoomLevel: Double = 12.0,
  pitchTilt: Double = 0.0,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val darkStyleJson = remember { MapLibreStyleHelper.getDarkMaritimeStyleJson() }

  val mapView = remember {
    MapView(context).apply {
      getMapAsync { map ->
        map.uiSettings.isAttributionEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isCompassEnabled = false

        // Initialize MapLibre with dark maritime style matching #0B132B
        map.setStyle(Style.Builder().fromJson(darkStyleJson)) {
          val cameraPosition = CameraPosition.Builder()
            .target(LatLng(targetLat, targetLon))
            .zoom(zoomLevel)
            .tilt(pitchTilt)
            .build()
          map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 600)
        }
      }
    }
  }

  // Bind MapView to Android Activity Lifecycle
  DisposableEffect(lifecycleOwner, mapView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> mapView.onStart()
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        Lifecycle.Event.ON_STOP -> mapView.onStop()
        Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      mapView.onDestroy()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(NavyBackground)
      .testTag("maplibre_composable_container")
  ) {
    AndroidView(
      factory = { mapView },
      modifier = Modifier.fillMaxSize()
    )

    // MapLibre Dark Style Status Pill
    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 8.dp, end = 10.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(NavySurface.copy(alpha = 0.85f))
        .border(1.dp, NavySurfaceHighlight, RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(EmeraldSafe))
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = "MAPLIBRE #0B132B DARK",
        color = NeonCyan,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
      )
    }
  }
}

/**
 * Base UI Shell using Jetpack Compose Scaffold with Top Tactical HUD and Bottom Navigation.
 */
@Composable
fun NavCastMainShell(
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
    modifier = modifier.fillMaxSize().testTag("main_scaffold_shell"),
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
