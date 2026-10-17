package com.example.data.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.DiscoveredVesselInstrument
import com.example.data.model.SppConnectionState
import com.example.data.model.VesselInstrumentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.UUID

/**
 * Bluetooth discovery service and vessel instrument connectivity manager.
 * Scans for available NMEA-capable vessel instruments (like AIS transponders,
 * depth sounders, and NMEA 0183/2000 gateways) and provides toggles to pair and connect.
 */
class ConnectivityManager(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
  private val onSentenceReceived: ((rawSentence: String, instrument: DiscoveredVesselInstrument) -> Unit)? = null
) {
  companion object {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // SIMULATED demo instruments, shown (labelled SIM) so the UI can be
    // exercised without hardware. These are NOT real discovered devices.
    val DEFAULT_INSTRUMENTS: List<DiscoveredVesselInstrument> = listOf(
      DiscoveredVesselInstrument(
        id = "00:1A:7D:DA:71:01",
        name = "SIM — Odom Hydrotrac II (Demo)",
        address = "00:1A:7D:DA:71:01",
        type = VesselInstrumentType.DEPTH_SOUNDER,
        rssi = -54,
        isBonded = true,
        isConnected = false,
        supportedSentences = listOf("\$SDDPT", "\$SDDBT"),
        isSimulated = true,
        manufacturer = "Teledyne Odom Hydrographic"
      ),
      DiscoveredVesselInstrument(
        id = "00:1B:DC:04:8A:22",
        name = "SIM — Vesper Marine XB-8000 AIS (Demo)",
        address = "00:1B:DC:04:8A:22",
        type = VesselInstrumentType.AIS_TRANSPONDER,
        rssi = -62,
        isBonded = false,
        isConnected = false,
        supportedSentences = listOf("!AIVDM", "!AIVDO", "\$GPGGA"),
        isSimulated = true,
        manufacturer = "Vesper Marine (Garmin)"
      ),
      DiscoveredVesselInstrument(
        id = "B8:27:EB:19:C3:44",
        name = "SIM — Actisense W2K-1 NMEA Gateway (Demo)",
        address = "B8:27:EB:19:C3:44",
        type = VesselInstrumentType.NMEA_GATEWAY,
        rssi = -68,
        isBonded = true,
        isConnected = false,
        supportedSentences = listOf("\$SDDPT", "!AIVDM", "\$GPRMC"),
        isSimulated = true,
        manufacturer = "Active Research Ltd (Actisense)"
      ),
      DiscoveredVesselInstrument(
        id = "AC:67:84:55:12:90",
        name = "SIM — Garmin GPS 24xd Marine (Demo)",
        address = "AC:67:84:55:12:90",
        type = VesselInstrumentType.GPS_RECEIVER,
        rssi = -73,
        isBonded = false,
        isConnected = false,
        supportedSentences = listOf("\$GPGGA", "\$GPRMC", "\$HCHDG"),
        isSimulated = true,
        manufacturer = "Garmin International"
      ),
      DiscoveredVesselInstrument(
        id = "70:B3:D5:E8:22:19",
        name = "SIM — Airmar DST810 Smart Triducer (Demo)",
        address = "70:B3:D5:E8:22:19",
        type = VesselInstrumentType.DEPTH_SOUNDER,
        rssi = -79,
        isBonded = false,
        isConnected = false,
        supportedSentences = listOf("\$SDDPT", "\$VWVHW", "\$YXXDR"),
        isSimulated = true,
        manufacturer = "Airmar Technology Corp"
      )
    )
  }

  private val bluetoothAdapter: BluetoothAdapter? by lazy {
    try {
      BluetoothAdapter.getDefaultAdapter()
    } catch (_: Exception) {
      null
    }
  }

  // Reactive state flows
  private val _discoveredInstruments = MutableStateFlow<List<DiscoveredVesselInstrument>>(DEFAULT_INSTRUMENTS)
  val discoveredInstruments: StateFlow<List<DiscoveredVesselInstrument>> = _discoveredInstruments.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _connectedInstrument = MutableStateFlow<DiscoveredVesselInstrument?>(null)
  val connectedInstrument: StateFlow<DiscoveredVesselInstrument?> = _connectedInstrument.asStateFlow()

  private val _connectionState = MutableStateFlow(SppConnectionState.DISCONNECTED)
  val connectionState: StateFlow<SppConnectionState> = _connectionState.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private var connectionJob: Job? = null
  private var streamSimulationJob: Job? = null
  private var bluetoothSocket: BluetoothSocket? = null
  private var receiverRegistered = false

  // BroadcastReceiver for Bluetooth classic discovery
  private val discoveryReceiver = object : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(c: Context?, intent: Intent?) {
      when (intent?.action) {
        BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
          _isScanning.value = true
          _statusMessage.value = "Scanning for marine vessel instruments..."
        }
        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
          _isScanning.value = false
          _statusMessage.value = "Scan complete. Found ${_discoveredInstruments.value.size} instruments."
        }
        BluetoothDevice.ACTION_FOUND -> {
          val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
          } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
          }
          val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (-70).toShort()).toInt()

          if (device != null) {
            handleDiscoveredDevice(device, rssi)
          }
        }
        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
          val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
          } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
          }
          val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
          if (device != null) {
            val isBonded = bondState == BluetoothDevice.BOND_BONDED
            val isBonding = bondState == BluetoothDevice.BOND_BONDING
            updateDeviceBondState(device.address, isBonded, isBonding)
          }
        }
      }
    }
  }

  init {
    loadBondedDevices()
  }

  // ==========================================
  // DISCOVERY SERVICE
  // ==========================================

  /**
   * Starts scanning for available NMEA-capable vessel instruments.
   */
  @SuppressLint("MissingPermission")
  fun startDiscovery() {
    if (_isScanning.value) return

    _isScanning.value = true
    _statusMessage.value = "Initiating Bluetooth marine instrument discovery..."

    // Check permissions
    val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
    }

    if (bluetoothAdapter != null && bluetoothAdapter!!.isEnabled && hasScanPermission) {
      registerReceiverIfNeeded()
      try {
        if (bluetoothAdapter!!.isDiscovering) {
          bluetoothAdapter!!.cancelDiscovery()
        }
        bluetoothAdapter!!.startDiscovery()
      } catch (e: Exception) {
        _statusMessage.value = "Hardware scan error: ${e.message}. Using vessel radar."
      }
    }

    // Always run scan coroutine to load paired devices, discover simulated nearby instruments,
    // and provide smooth visual feedback on emulator or bench test environments
    scope.launch(Dispatchers.Default) {
      loadBondedDevices()
      delay(3000)
      // Ensure default/simulated instruments are visible and up to date
      mergeSimulatedInstruments()
      delay(1500)
      _isScanning.value = false
      val simCount = _discoveredInstruments.value.count { it.isSimulated }
      _statusMessage.value = "Discovery finished. ${_discoveredInstruments.value.size} vessel instruments available ($simCount are SIMULATED demo instruments, labelled SIM)."
    }
  }

  /**
   * Stops ongoing Bluetooth discovery.
   */
  @SuppressLint("MissingPermission")
  fun stopDiscovery() {
    _isScanning.value = false
    if (bluetoothAdapter != null) {
      val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
      if (hasScanPermission) {
        try {
          if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
          }
        } catch (_: Exception) {}
      }
    }
    _statusMessage.value = "Discovery stopped."
  }

  // ==========================================
  // PAIRING TOGGLE
  // ==========================================

  /**
   * Toggles pairing (bonding) state for the given vessel instrument.
   * If already bonded, unpairs it; if not bonded, creates a bond.
   */
  @SuppressLint("MissingPermission")
  fun togglePair(instrument: DiscoveredVesselInstrument) {
    val willBeBonded = !instrument.isBonded

    // If it's a physical device on the adapter
    if (bluetoothAdapter != null && bluetoothAdapter!!.isEnabled) {
      val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }

      if (hasConnectPermission) {
        try {
          val device = bluetoothAdapter?.getRemoteDevice(instrument.address)
          if (device != null) {
            if (instrument.isBonded) {
              // Unpair via reflection (standard Android Bluetooth technique)
              val removeBondMethod = device.javaClass.getMethod("removeBond")
              removeBondMethod.invoke(device)
            } else {
              // Pair
              device.createBond()
            }
          }
        } catch (e: Exception) {
          // Handled gracefully below
        }
      }
    }

    // Update internal state
    _discoveredInstruments.update { list ->
      list.map { inst ->
        if (inst.id == instrument.id || inst.address == instrument.address) {
          inst.copy(isBonded = willBeBonded)
        } else {
          inst
        }
      }
    }

    val actionWord = if (willBeBonded) "Paired with" else "Unpaired from"
    _statusMessage.value = "$actionWord ${instrument.name}"
  }

  // ==========================================
  // CONNECTION TOGGLE
  // ==========================================

  /**
   * Toggles the connection to the specified vessel instrument.
   * If currently connected to this instrument, disconnects it.
   * If disconnected or connected to another device, connects to this instrument.
   */
  fun toggleConnect(instrument: DiscoveredVesselInstrument) {
    if (instrument.isConnected || (_connectedInstrument.value?.id == instrument.id && _connectionState.value == SppConnectionState.STREAMING)) {
      disconnectInstrument()
    } else {
      connectInstrument(instrument)
    }
  }

  /**
   * Connects to a specific vessel instrument over Bluetooth SPP or telemetry simulation.
   */
  @SuppressLint("MissingPermission")
  fun connectInstrument(instrument: DiscoveredVesselInstrument) {
    disconnectInstrument()

    _connectionState.value = SppConnectionState.CONNECTING
    _statusMessage.value = "Connecting to ${instrument.name} (${instrument.type.label})..."

    // Mark instrument as connecting in the list
    _discoveredInstruments.update { list ->
      list.map { inst ->
        inst.copy(
          isConnecting = inst.id == instrument.id,
          isConnected = false
        )
      }
    }

    connectionJob = scope.launch(Dispatchers.IO) {
      var connectedRealSocket = false

      // Check if real Bluetooth hardware connection is possible
      val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }

      if (bluetoothAdapter != null && bluetoothAdapter!!.isEnabled && hasConnectPermission) {
        try {
          val device = bluetoothAdapter?.getRemoteDevice(instrument.address)
          if (device != null) {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter?.cancelDiscovery()
            bluetoothSocket?.connect()
            connectedRealSocket = true
          }
        } catch (_: Exception) {
          connectedRealSocket = false
        }
      }

      if (connectedRealSocket && bluetoothSocket != null) {
        // Real hardware stream
        onConnectionEstablished(instrument)
        try {
          val reader = BufferedReader(InputStreamReader(bluetoothSocket!!.inputStream))
          while (isActive) {
            val line = reader.readLine() ?: break
            if (line.isNotBlank()) {
              onSentenceReceived?.invoke(line, instrument)
            }
          }
        } catch (_: Exception) {
          // Stream interrupted
        } finally {
          disconnectSocket()
        }
      } else if (instrument.isSimulated) {
        // Explicit simulation ONLY: the user deliberately selected one of the
        // labelled SIM demo instruments. The stream is synthetic and is
        // announced as such — it is never a fallback for a failed real
        // connection.
        delay(600) // Brief connection handshake simulation
        onConnectionEstablished(instrument)
        _statusMessage.value =
          "SIMULATED: streaming synthetic NMEA from demo instrument '${instrument.name}'. No real device is connected."
        startTelemetryStreamSimulation(instrument)
      } else {
        // Real connection failed. Fail visibly — do NOT silently substitute a
        // simulated stream (removed 2026-10-17: the previous behaviour
        // reported a failed connection as 'Connected… Streaming telemetry'
        // while streaming invented data).
        disconnectSocket()
        _connectedInstrument.value = null
        _connectionState.value = SppConnectionState.ERROR
        _statusMessage.value =
          "Could not connect to '${instrument.name}' over Bluetooth SPP. No data is streaming, and simulation was NOT started."
        _discoveredInstruments.update { list ->
          list.map { it.copy(isConnected = false, isConnecting = false) }
        }
      }
    }
  }

  /**
   * Disconnects any active vessel instrument connection.
   */
  fun disconnectInstrument() {
    connectionJob?.cancel()
    connectionJob = null
    streamSimulationJob?.cancel()
    streamSimulationJob = null
    disconnectSocket()

    val prev = _connectedInstrument.value
    _connectedInstrument.value = null
    _connectionState.value = SppConnectionState.DISCONNECTED
    _statusMessage.value = if (prev != null) "Disconnected from ${prev.name}" else "Disconnected."

    _discoveredInstruments.update { list ->
      list.map { it.copy(isConnected = false, isConnecting = false) }
    }
  }

  private fun onConnectionEstablished(instrument: DiscoveredVesselInstrument) {
    val activeInst = instrument.copy(
      isConnected = true,
      isConnecting = false,
      isBonded = if (instrument.isSimulated) instrument.isBonded else true
    )
    _connectedInstrument.value = activeInst
    _connectionState.value = SppConnectionState.STREAMING
    _statusMessage.value = if (instrument.isSimulated) {
      "SIMULATED instrument '${instrument.name}' selected. Streaming synthetic NMEA telemetry."
    } else {
      "Connected to ${instrument.name}. Streaming NMEA telemetry."
    }

    _discoveredInstruments.update { list ->
      list.map { inst ->
        if (inst.id == instrument.id) {
          activeInst
        } else {
          inst.copy(isConnected = false, isConnecting = false)
        }
      }
    }
  }

  private fun startTelemetryStreamSimulation(instrument: DiscoveredVesselInstrument) {
    streamSimulationJob?.cancel()
    streamSimulationJob = scope.launch(Dispatchers.Default) {
      var seq = 0
      while (isActive) {
        delay(1200)
        seq++

        val sentence = generateSentenceForInstrument(instrument, seq)
        onSentenceReceived?.invoke(sentence, instrument)
      }
    }
  }

  /**
   * Generates realistic NMEA sentences matching the instrument type.
   */
  private fun generateSentenceForInstrument(instrument: DiscoveredVesselInstrument, step: Int): String {
    return when (instrument.type) {
      VesselInstrumentType.DEPTH_SOUNDER -> {
        // Generate valid $SDDPT sentence with realistic acoustic depth and heave
        val waveSwell = Math.sin(step * 0.28) * 0.24
        val slope = (step % 50 - 25) * 0.012
        val noise = (Math.random() - 0.5) * 0.05
        val depth = String.format(Locale.US, "%.2f", 14.85 + waveSwell + slope + noise)
        val body = "SDDPT,$depth,0.0"
        val checksum = computeNmeaChecksum(body)
        "\$$body*$checksum"
      }
      VesselInstrumentType.AIS_TRANSPONDER -> {
        // Alternate between Class A / Class B AIS VDM payloads and GPS fix
        if (step % 2 == 0) {
          "!AIVDM,1,1,,A,13aEO:0P0000h0?K>4A=g?vN0000,0*11"
        } else {
          val body = String.format(Locale.US, "GPGGA,123519.00,3658.2%02d,N,07619.4%02d,W,1,08,0.9,2.4,M,-32.5,M,,", step % 90, step % 90)
          val checksum = computeNmeaChecksum(body)
          "\$$body*$checksum"
        }
      }
      VesselInstrumentType.NMEA_GATEWAY -> {
        // Gateway multiplexes both depth and navigation
        if (step % 2 == 0) {
          val depth = String.format(Locale.US, "%.2f", 14.92 + Math.sin(step * 0.2) * 0.18)
          val body = "SDDPT,$depth,0.0"
          val checksum = computeNmeaChecksum(body)
          "\$$body*$checksum"
        } else {
          val body = "GPRMC,123519.00,A,3658.21,N,07619.42,W,09.5,142.0,171026,,,A"
          val checksum = computeNmeaChecksum(body)
          "\$$body*$checksum"
        }
      }
      VesselInstrumentType.GPS_RECEIVER -> {
        val body = String.format(Locale.US, "GPGGA,123519.00,3658.21%d,N,07619.42%d,W,2,10,0.8,3.2,M,-32.5,M,,", step % 9, step % 9)
        val checksum = computeNmeaChecksum(body)
        "\$$body*$checksum"
      }
      VesselInstrumentType.HEADING_SENSOR -> {
        val heading = String.format(Locale.US, "%.1f", 142.5 + Math.sin(step * 0.1) * 3.0)
        val body = "HCHDG,$heading,,,,"
        val checksum = computeNmeaChecksum(body)
        "\$$body*$checksum"
      }
      VesselInstrumentType.WIND_STATION -> {
        val angle = String.format(Locale.US, "%.1f", 45.0 + Math.sin(step * 0.3) * 5.0)
        val speed = String.format(Locale.US, "%.1f", 12.4 + (Math.random() - 0.5) * 2.0)
        val body = "WIMWV,$angle,R,$speed,N,A"
        val checksum = computeNmeaChecksum(body)
        "\$$body*$checksum"
      }
      VesselInstrumentType.OTHER_NMEA -> {
        val depth = String.format(Locale.US, "%.2f", 15.00 + (Math.random() - 0.5) * 0.3)
        val body = "SDDPT,$depth,0.0"
        val checksum = computeNmeaChecksum(body)
        "\$$body*$checksum"
      }
    }
  }

  private fun computeNmeaChecksum(content: String): String {
    var cs = 0
    for (char in content) {
      cs = cs xor char.code
    }
    return String.format(Locale.US, "%02X", cs)
  }

  // ==========================================
  // DEVICE MANAGEMENT & HEURISTICS
  // ==========================================

  @SuppressLint("MissingPermission")
  private fun loadBondedDevices() {
    if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
      mergeSimulatedInstruments()
      return
    }

    val hasConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

    if (!hasConnect) {
      mergeSimulatedInstruments()
      return
    }

    try {
      val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
      val detected = bonded.map { dev ->
        val name = dev.name ?: "Marine Instrument (${dev.address.takeLast(5)})"
        val (type, sentences, mfr) = classifyInstrument(name, dev.address)
        DiscoveredVesselInstrument(
          id = dev.address,
          name = name,
          address = dev.address,
          type = type,
          rssi = -60,
          isBonded = true,
          isConnected = dev.address == _connectedInstrument.value?.address,
          supportedSentences = sentences,
          manufacturer = mfr
        )
      }

      if (detected.isNotEmpty()) {
        _discoveredInstruments.update { existing ->
          val map = existing.associateBy { it.id }.toMutableMap()
          for (d in detected) {
            map[d.id] = d
          }
          map.values.toList()
        }
      } else {
        mergeSimulatedInstruments()
      }
    } catch (_: Exception) {
      mergeSimulatedInstruments()
    }
  }

  @SuppressLint("MissingPermission")
  private fun handleDiscoveredDevice(device: BluetoothDevice, rssi: Int) {
    val name = device.name ?: "Unknown Marine Device (${device.address.takeLast(5)})"
    val (type, sentences, mfr) = classifyInstrument(name, device.address)
    val isBonded = device.bondState == BluetoothDevice.BOND_BONDED

    val discovered = DiscoveredVesselInstrument(
      id = device.address,
      name = name,
      address = device.address,
      type = type,
      rssi = rssi,
      isBonded = isBonded,
      isConnected = device.address == _connectedInstrument.value?.address,
      supportedSentences = sentences,
      manufacturer = mfr,
      lastSeenTimestamp = System.currentTimeMillis()
    )

    _discoveredInstruments.update { current ->
      val updated = current.toMutableList()
      val index = updated.indexOfFirst { it.id == discovered.id || it.address == discovered.address }
      if (index >= 0) {
        updated[index] = discovered
      } else {
        updated.add(discovered)
      }
      updated
    }
  }

  private fun updateDeviceBondState(address: String, isBonded: Boolean, isBonding: Boolean) {
    _discoveredInstruments.update { list ->
      list.map { inst ->
        if (inst.address == address) {
          inst.copy(isBonded = isBonded)
        } else {
          inst
        }
      }
    }
    if (isBonded) {
      _statusMessage.value = "Successfully paired device $address"
    }
  }

  private fun mergeSimulatedInstruments() {
    _discoveredInstruments.update { current ->
      val map = current.associateBy { it.id }.toMutableMap()
      for (sim in DEFAULT_INSTRUMENTS) {
        if (!map.containsKey(sim.id)) {
          map[sim.id] = sim
        }
      }
      map.values.toList()
    }
  }

  /**
   * Classifies a Bluetooth device by name or address to determine its vessel instrument type,
   * supported NMEA 0183 sentences, and manufacturer.
   */
  fun classifyInstrument(name: String, address: String): Triple<VesselInstrumentType, List<String>, String> {
    val lower = name.lowercase(Locale.ROOT)
    return when {
      lower.contains("ais") || lower.contains("vesper") || lower.contains("em-trak") || lower.contains("ait") -> {
        Triple(
          VesselInstrumentType.AIS_TRANSPONDER,
          listOf("!AIVDM", "!AIVDO", "\$GPGGA"),
          if (lower.contains("vesper")) "Vesper Marine" else if (lower.contains("garmin")) "Garmin" else "Marine AIS"
        )
      }
      lower.contains("sounder") || lower.contains("dpt") || lower.contains("echo") || lower.contains("sonar") ||
        lower.contains("odom") || lower.contains("knudsen") || lower.contains("airmar") || lower.contains("triducer") -> {
        Triple(
          VesselInstrumentType.DEPTH_SOUNDER,
          listOf("\$SDDPT", "\$SDDBT"),
          if (lower.contains("odom")) "Teledyne Odom" else if (lower.contains("airmar")) "Airmar Technology" else "Echosounder Mfr"
        )
      }
      lower.contains("gateway") || lower.contains("mux") || lower.contains("actisense") || lower.contains("yacht") || lower.contains("bridge") -> {
        Triple(
          VesselInstrumentType.NMEA_GATEWAY,
          listOf("\$SDDPT", "!AIVDM", "\$GPRMC"),
          if (lower.contains("actisense")) "Actisense" else "NMEA Gateway Mfr"
        )
      }
      lower.contains("gps") || lower.contains("gnss") || lower.contains("garmin") || lower.contains("furuno") -> {
        Triple(
          VesselInstrumentType.GPS_RECEIVER,
          listOf("\$GPGGA", "\$GPRMC"),
          if (lower.contains("garmin")) "Garmin International" else "Furuno Electric"
        )
      }
      lower.contains("compass") || lower.contains("heading") || lower.contains("gyro") || lower.contains("hdg") -> {
        Triple(
          VesselInstrumentType.HEADING_SENSOR,
          listOf("\$HCHDG", "\$HCHDT"),
          "Electronic Compass"
        )
      }
      lower.contains("wind") || lower.contains("weather") || lower.contains("anemometer") -> {
        Triple(
          VesselInstrumentType.WIND_STATION,
          listOf("\$WIMWV", "\$WIMDA"),
          "Marine Meteorology"
        )
      }
      else -> {
        Triple(
          VesselInstrumentType.OTHER_NMEA,
          listOf("\$SDDPT"),
          "Marine Instrument"
        )
      }
    }
  }

  private fun registerReceiverIfNeeded() {
    if (!receiverRegistered) {
      try {
        val filter = IntentFilter().apply {
          addAction(BluetoothDevice.ACTION_FOUND)
          addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
          addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
          addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(discoveryReceiver, filter)
        receiverRegistered = true
      } catch (_: Exception) {}
    }
  }

  fun unregisterReceiver() {
    if (receiverRegistered) {
      try {
        context.unregisterReceiver(discoveryReceiver)
      } catch (_: Exception) {}
      receiverRegistered = false
    }
  }

  private fun disconnectSocket() {
    try {
      bluetoothSocket?.close()
    } catch (_: Exception) {}
    bluetoothSocket = null
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }
}
