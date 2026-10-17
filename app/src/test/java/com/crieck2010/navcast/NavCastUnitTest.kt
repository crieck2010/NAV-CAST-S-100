package com.crieck2010.navcast

import com.crieck2010.navcast.data.model.UkcStatus
import com.crieck2010.navcast.data.model.VesselProfile
import com.crieck2010.navcast.data.repository.NoaaCoopsRepository
import com.crieck2010.navcast.engine.DukcCalculator
import com.crieck2010.navcast.engine.VDatumEngine
import com.crieck2010.navcast.ui.DUKCViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavCastUnitTest {

  @Test
  fun testDukcCalculation_SafeCondition() {
    val vessel = VesselProfile(
      staticDraftMeters = 3.5f,
      speedKnots = 8.0f,
      blockCoefficient = 0.65f,
      safetyMarginMeters = 1.0f
    )
    val result = DukcCalculator.calculate(
      chartedDepthMeters = 15.0f,
      waterLevelMeters = 0.5f,
      vessel = vessel,
      isConfinedChannel = true
    )

    assertEquals(15.5f, result.totalWaterDepthMeters, 0.05f)
    assertTrue(result.dynamicDraftMeters > 3.5f)
    assertEquals(UkcStatus.SAFE, result.status)
    assertTrue(result.underKeelClearanceMeters > 10.0f)
  }

  @Test
  fun testDukcCalculation_DangerCondition() {
    val vessel = VesselProfile(
      staticDraftMeters = 14.0f,
      speedKnots = 12.0f,
      blockCoefficient = 0.85f,
      safetyMarginMeters = 1.5f
    )
    val result = DukcCalculator.calculate(
      chartedDepthMeters = 13.8f,
      waterLevelMeters = 0.2f,
      vessel = vessel,
      isConfinedChannel = true
    )

    assertEquals(UkcStatus.DANGER, result.status)
  }

  @Test
  fun testVDatumTransformation() {
    val result = VDatumEngine.transform(
      region = "Chesapeake Bay (MD/VA)",
      lat = 36.9850,
      lon = -76.1100,
      inputDatum = "NAD83(2011) Ellipsoid",
      inputHeight = -34.82,
      outputDatum = "NAVD88 Orthometric"
    )

    assertEquals(0.0, result.outputHeightMeters, 0.05)
  }

  @Test
  fun testDUKCViewModel_StateAndS100Calculation() {
    val viewModel = DUKCViewModel()

    val initial = viewModel.uiState.value
    assertTrue(initial.staticDraftMeters > 0f)
    assertTrue(initial.beamMeters > 0f)
    assertTrue(initial.speedKnots >= 0f)

    viewModel.setStaticDraft(4.2f)
    viewModel.setBeam(18.0f)
    viewModel.setSpeed(10.0f)

    assertEquals(4.2f, viewModel.uiState.value.staticDraftMeters, 0.01f)
    assertEquals(18.0f, viewModel.uiState.value.beamMeters, 0.01f)
    assertEquals(10.0f, viewModel.uiState.value.speedKnots, 0.01f)

    val result = viewModel.calculateDynamicUnderKeelClearance(
      s102Depth = 16.0f,
      s104WaterLevel = 0.5f,
      speedKnots = 10.0f,
      staticDraft = 4.2f
    )

    assertEquals(16.5f, result.totalWaterDepthMeters, 0.05f)
    assertTrue(result.dynamicDraftMeters > 4.2f)
    assertTrue(result.underKeelClearanceMeters > 10.0f)
    assertEquals(UkcStatus.SAFE, result.status)

    val dangerResult = viewModel.calculateDynamicUnderKeelClearance(
      s102Depth = 4.0f,
      s104WaterLevel = 0.0f,
      speedKnots = 10.0f,
      staticDraft = 4.2f
    )
    assertEquals(UkcStatus.DANGER, dangerResult.status)
  }

  @Test
  fun testNoaaCoopsRepository_DefaultStation8575512_UpdatesDukc() = runTest {
    val repository = NoaaCoopsRepository()

    // Verify default station ID is 8575512 (Annapolis, MD)
    assertEquals("8575512", NoaaCoopsRepository.DEFAULT_STATION_ID)
    assertEquals("8575512", repository.waterLevelState.value.stationId)

    // Fetch water level using default station 8575512
    val result = repository.fetchRealTimeWaterLevel(
      stationId = NoaaCoopsRepository.DEFAULT_STATION_ID,
      fallbackModelVal = 0.55f
    )
    assertTrue(result.isSuccess)
    val waterLevel = result.getOrThrow()
    assertEquals("8575512", waterLevel.stationId)
    assertEquals("MLLW", waterLevel.datum)

    // Verify that updating S-100 parameters in DUKCViewModel dynamically shifts DUKC
    val dukcViewModel = DUKCViewModel(repository)
    dukcViewModel.updateS100Parameters(
      chartedDepthMeters = 15.0f,
      waterLevelMeters = waterLevel.waterLevelMeters
    )

    val updatedDukc = dukcViewModel.uiState.value.dukcResult
    // Total depth should equal 15.0 + water level
    assertEquals(15.0f + waterLevel.waterLevelMeters, updatedDukc.totalWaterDepthMeters, 0.05f)
  }
}
