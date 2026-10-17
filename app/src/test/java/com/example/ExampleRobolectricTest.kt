package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DiscoveredVesselInstrument
import com.example.data.model.NoaaDatasetMetadata
import com.example.data.model.NoaaSurveyDataset
import com.example.data.model.SddptSentence
import com.example.data.model.SppConnectionState
import com.example.data.model.VesselInstrumentType
import com.example.data.service.ConnectivityManager
import com.example.data.service.DataService
import com.example.data.service.ExportFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NAV-CAST S-100", appName)
  }

  @Test
  fun `test DataService NMEA 0183 SDDPT parser with valid checksum`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dataService = DataService(context)

    // Format: $SDDPT,depth,offset*checksum
    // Compute checksum for "SDDPT,14.85,0.0"
    val checksum = dataService.computeNmeaChecksum("SDDPT,14.85,0.0")
    val validSentence = "\$SDDPT,14.85,0.0*$checksum"

    val parsed = dataService.parseSddpt(validSentence)
    assertNotNull(parsed)
    assertEquals("SD", parsed!!.talker)
    assertEquals(14.85, parsed.depthMeters, 0.001)
    assertEquals(0.0, parsed.offsetMeters, 0.001)
    assertEquals(14.85, parsed.correctedDepthMeters, 0.001)
    assertTrue(parsed.isChecksumValid)
    assertEquals(checksum, parsed.checksum)
  }

  @Test
  fun `test DataService detects invalid NMEA checksum`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dataService = DataService(context)

    val invalidSentence = "\$SDDPT,14.85,0.0*00" // Deliberately incorrect checksum
    val parsed = dataService.parseSddpt(invalidSentence)
    assertNotNull(parsed)
    assertEquals(14.85, parsed!!.depthMeters, 0.001)
    assertFalse(parsed.isChecksumValid)
  }

  @Test
  fun `test DataService parses 3-field SDDPT with keel offset and range`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dataService = DataService(context)

    // $SDDPT,12.50,-0.8,100.0*cs (depth 12.5m, keel offset -0.8m -> keel depth 11.7m)
    val body = "SDDPT,12.50,-0.8,100.0"
    val cs = dataService.computeNmeaChecksum(body)
    val sentence = "\$$body*$cs"

    val parsed = dataService.parseSddpt(sentence)
    assertNotNull(parsed)
    assertEquals(12.50, parsed!!.depthMeters, 0.001)
    assertEquals(-0.8, parsed.offsetMeters, 0.001)
    assertEquals(100.0, parsed.maxRangeScaleMeters ?: 0.0, 0.001)
    assertEquals(11.70, parsed.correctedDepthMeters, 0.001)
    assertTrue(parsed.isChecksumValid)
  }

  @Test
  fun `test Kotlin Serialization of SddptSentence and NoaaSurveyDataset`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dataService = DataService(context)

    val sentence = SddptSentence(
      talker = "SD",
      depthMeters = 16.42,
      offsetMeters = 0.5,
      maxRangeScaleMeters = 200.0,
      checksum = "5A",
      isChecksumValid = true,
      rawSentence = "\$SDDPT,16.42,0.5,200.0*5A",
      timestampEpochMs = 1718000000000L,
      isoTimestamp = "2026-10-17T12:00:00.000Z"
    )

    // Serialize to JSON using DataService
    val jsonString = dataService.serializeSentenceToJson(sentence)
    assertTrue(jsonString.contains("\"depthMeters\": 16.42"))
    assertTrue(jsonString.contains("\"offsetMeters\": 0.5"))

    // Deserialize back from JSON
    val deserialized = dataService.deserializeSentenceFromJson(jsonString)
    assertNotNull(deserialized)
    assertEquals(sentence.depthMeters, deserialized!!.depthMeters, 0.001)
    assertEquals(sentence.rawSentence, deserialized.rawSentence)

    // Test full dataset serialization
    val dataset = NoaaSurveyDataset(
      metadata = NoaaDatasetMetadata(
        vesselName = "NOAA S/V Ferdinand R. Hassler",
        surveyArea = "Chesapeake Bay Main Channel",
        createdIso = "2026-10-17T12:00:00.000Z",
        sounderDevice = "Odom Hydrotrac II (SPP)"
      ),
      totalRecords = 1,
      records = listOf(
        com.example.data.model.NmeaLogEntry(
          id = "TEST-1",
          timestampEpochMs = 1718000000000L,
          isoTimestamp = "2026-10-17T12:00:00.000Z",
          rawSentence = "\$SDDPT,16.42,0.5*5A",
          sentenceType = "SDDPT",
          depthMeters = 16.42,
          offsetMeters = 0.5,
          correctedDepthMeters = 16.92,
          sourceDevice = "Bluetooth SPP",
          isValidChecksum = true,
          latitude = 36.985,
          longitude = -76.110
        )
      )
    )

    val datasetJson = dataService.serializeDatasetToJson(dataset)
    assertTrue(datasetJson.contains("NAV-CAST S-100 (independent project)"))
    val deserializedDataset = dataService.deserializeDatasetFromJson(datasetJson)
    assertNotNull(deserializedDataset)
    assertEquals(1, deserializedDataset!!.totalRecords)
    assertEquals("NOAA S/V Ferdinand R. Hassler", deserializedDataset.metadata.vesselName)
  }

  @Test
  fun `test DataService retains historical log and generates export formats`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dataService = DataService(context)

    // Clear any prior logs
    dataService.clearRetainedLogs()
    assertEquals(0, dataService.rawLogs.value.size)

    // Ingest multiple NMEA sentences
    val sentence1 = dataService.formatSddptSentence(14.85, 0.0)
    val sentence2 = dataService.formatSddptSentence(15.20, 0.5)
    val sentence3 = dataService.formatSddptSentence(13.90, -0.2)

    val entry1 = dataService.ingestSentence(sentence1, sourceDevice = "Bluetooth SPP Sounder")
    val entry2 = dataService.ingestSentence(sentence2, sourceDevice = "Bluetooth SPP Sounder")
    val entry3 = dataService.ingestSentence(sentence3, sourceDevice = "Bluetooth SPP Sounder")

    assertNotNull(entry1)
    assertNotNull(entry2)
    assertNotNull(entry3)
    assertEquals(3, dataService.rawLogs.value.size)

    // Verify running stats
    val stats = dataService.stats.value
    assertEquals(3, stats.validSddptCount)
    assertEquals(13.90, stats.minDepthMeters, 0.05)
    assertEquals(15.20, stats.maxDepthMeters, 0.05)

    // Verify CSV export format
    val csv = dataService.exportToCsv()
    assertTrue(csv.contains("Timestamp_ISO,Timestamp_Epoch_Ms,Sentence_Type,Depth_Meters"))
    assertTrue(csv.contains("14.850"))
    assertTrue(csv.contains("15.200"))
    assertTrue(csv.contains("13.900"))

    // Verify Raw NMEA export format
    val nmeaStream = dataService.exportToRawNmea()
    assertTrue(nmeaStream.contains("# NAV-CAST S-100 \u2014 independent prototype (not a NOAA or CSDL product)"))
    assertTrue(nmeaStream.contains("\$SDDPT,"))

    // Verify JSON export format
    val json = dataService.exportToJson("NOAA Hassler", "Chesapeake Bay")
    assertTrue(json.contains("NAV-CAST S-100 (independent project)"))
    assertTrue(json.contains("\"totalRecords\": 3"))

    // Test creating actual export file
    val file = dataService.createExportFile(
      format = ExportFormat.CSV,
      vesselName = "NOAA Hassler",
      surveyArea = "Chesapeake"
    )
    assertTrue(file.exists())
    assertTrue(file.length() > 0)
    assertTrue(file.name.endsWith(".csv"))
  }

  @Test
  fun `test ConnectivityManager scans and discovers NMEA vessel instruments`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val connectivityManager = ConnectivityManager(context)

    // Verify initial instruments include NMEA depth sounders and AIS transponders
    val instruments = connectivityManager.discoveredInstruments.value
    assertTrue("Should have discovered vessel instruments", instruments.isNotEmpty())

    val sounders = instruments.filter { it.type == VesselInstrumentType.DEPTH_SOUNDER }
    val ais = instruments.filter { it.type == VesselInstrumentType.AIS_TRANSPONDER }
    val gateways = instruments.filter { it.type == VesselInstrumentType.NMEA_GATEWAY }

    assertTrue("Should find Depth Sounder instrument", sounders.isNotEmpty())
    assertTrue("Should find AIS Transponder instrument", ais.isNotEmpty())
    assertTrue("Should find NMEA Gateway instrument", gateways.isNotEmpty())

    // Verify instrument properties
    val sounder = sounders.first()
    assertNotNull(sounder.name)
    assertNotNull(sounder.address)
    assertTrue(sounder.rssi <= 0)
    assertTrue(sounder.signalBars in 1..4)
    assertTrue(sounder.supportedSentences.contains("\$SDDPT"))
  }

  @Test
  fun `test ConnectivityManager classifies AIS, Sounder, Gateway, and GPS correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val connectivityManager = ConnectivityManager(context)

    // Test AIS classification
    val (aisType, aisSentences, _) = connectivityManager.classifyInstrument("Vesper Marine XB-8000 AIS", "00:11:22:33:44:55")
    assertEquals(VesselInstrumentType.AIS_TRANSPONDER, aisType)
    assertTrue(aisSentences.contains("!AIVDM"))

    // Test Depth Sounder classification
    val (sounderType, sounderSentences, _) = connectivityManager.classifyInstrument("Odom Hydrotrac II Depth Sounder", "00:11:22:33:44:66")
    assertEquals(VesselInstrumentType.DEPTH_SOUNDER, sounderType)
    assertTrue(sounderSentences.contains("\$SDDPT"))

    // Test NMEA Gateway classification
    val (gatewayType, gatewaySentences, _) = connectivityManager.classifyInstrument("Actisense W2K-1 NMEA Gateway Mux", "00:11:22:33:44:77")
    assertEquals(VesselInstrumentType.NMEA_GATEWAY, gatewayType)
    assertTrue(gatewaySentences.contains("\$SDDPT"))

    // Test GPS classification
    val (gpsType, gpsSentences, _) = connectivityManager.classifyInstrument("Garmin GPS 24xd Marine", "00:11:22:33:44:88")
    assertEquals(VesselInstrumentType.GPS_RECEIVER, gpsType)
    assertTrue(gpsSentences.contains("\$GPGGA"))
  }

  @Test
  fun `test ConnectivityManager togglePair toggles bonded state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val connectivityManager = ConnectivityManager(context)

    val target = connectivityManager.discoveredInstruments.value.first()
    val initialBonded = target.isBonded

    // Toggle pair
    connectivityManager.togglePair(target)
    val afterFirstToggle = connectivityManager.discoveredInstruments.value.first { it.id == target.id }
    assertEquals(!initialBonded, afterFirstToggle.isBonded)

    // Toggle back
    connectivityManager.togglePair(afterFirstToggle)
    val afterSecondToggle = connectivityManager.discoveredInstruments.value.first { it.id == target.id }
    assertEquals(initialBonded, afterSecondToggle.isBonded)
  }

  @Test
  fun `test ConnectivityManager toggleConnect and DataService telemetry integration`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var lastReceivedSentence: String? = null
    var lastSourceInstrument: DiscoveredVesselInstrument? = null

    val connectivityManager = ConnectivityManager(
      context = context,
      onSentenceReceived = { sentence, instrument ->
        lastReceivedSentence = sentence
        lastSourceInstrument = instrument
      }
    )

    val sounder = connectivityManager.discoveredInstruments.value.first { it.type == VesselInstrumentType.DEPTH_SOUNDER }
    assertFalse(sounder.isConnected)

    // Connect to depth sounder
    connectivityManager.connectInstrument(sounder)

    val connected = connectivityManager.connectedInstrument.value
    assertNotNull(connected)
    assertEquals(sounder.id, connected!!.id)
    assertEquals(SppConnectionState.STREAMING, connectivityManager.connectionState.value)

    // Disconnect
    connectivityManager.disconnectInstrument()
    assertEquals(SppConnectionState.DISCONNECTED, connectivityManager.connectionState.value)
    assertFalse(connectivityManager.discoveredInstruments.value.first { it.id == sounder.id }.isConnected)
  }
}

