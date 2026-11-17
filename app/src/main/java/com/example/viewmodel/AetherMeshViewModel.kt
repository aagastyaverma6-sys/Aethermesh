package com.example.viewmodel

import android.app.Application
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SurvivalAiRepository
import com.example.audio.AcousticFskModem
import com.example.audio.AcousticModemState
import com.example.crypto.EncryptedPacket
import com.example.crypto.EncryptedPacketInspection
import com.example.crypto.TacticalCrypto
import com.example.mesh.LogSeverity
import com.example.mesh.MeshEfficiencySample
import com.example.mesh.MeshLink
import com.example.mesh.MeshNode
import com.example.mesh.MeshRouter
import com.example.mesh.NodeRole
import com.example.mesh.P2pCommsMessage
import com.example.mesh.PacketPulseState
import com.example.mesh.PinCategory
import com.example.mesh.RouteResult
import com.example.mesh.TacticalPin
import com.example.mesh.TerminalLogEntry
import com.example.transport.BleCapabilities
import com.example.transport.BleMeshTransport
import com.example.transport.BleRadioStatus
import com.example.transport.BtClassicMeshTransport
import com.example.transport.BtClassicStatus
import com.example.transport.DiscoveredBlePeer
import com.example.transport.DiscoveredBtClassicPeer
import com.example.transport.DiscoveredLanPeer
import com.example.transport.DiscoveredWifiDirectPeer
import com.example.transport.LanMeshTransport
import com.example.transport.WifiP2pMeshTransport
import com.example.transport.WifiP2pStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "AetherMeshViewModel"
private const val PEER_STALE_MS = 16_000L

sealed interface AiTriageUiState {
    data object Idle : AiTriageUiState
    data object Loading : AiTriageUiState
    data class Streaming(val query: String, val text: String) : AiTriageUiState
    data class Success(val query: String, val text: String) : AiTriageUiState
    data class Error(val query: String, val message: String) : AiTriageUiState
}

@Immutable
data class TriageExchange(
    val id: Long,
    val query: String,
    val response: String,
    val timestamp: String,
    val isError: Boolean = false
)

@Immutable
data class MeshCommandUiState(
    val selfNodeId: String = "AETH-0000",
    val selfCallsign: String = Build.MODEL ?: "Android Device",
    val nodes: List<MeshNode> = emptyList(),
    val links: List<MeshLink> = emptyList(),
    val pins: List<TacticalPin> = emptyList(),
    val p2pMessages: List<P2pCommsMessage> = emptyList(),
    val armedPinCategory: PinCategory? = null,
    val selectedNodeId: String? = null,
    val linkRangeKm: Float = 2.5f,
    val blackoutActive: Boolean = false,
    val batterySaverEnabled: Boolean = false,
    val activeRoute: RouteResult? = null,
    val isRouteRerouted: Boolean = false,
    val rerouteAlertBanner: String? = null,
    val connectedPeerCount: Int = 0,
    val onlineNodeCount: Int = 1,
    val meshHopEfficiencyPct: Int = 100,
    val avgLatencyMs: Int = 0,
    val efficiencyHistory: List<MeshEfficiencySample> = emptyList(),
    val packetPulse: PacketPulseState = PacketPulseState(),
    val terminalLogs: List<TerminalLogEntry> = emptyList(),
    val latestPacketInspection: EncryptedPacketInspection? = null,
    val simulateWrongKey: Boolean = false,
    val bleStatus: BleRadioStatus = BleRadioStatus.PERMISSION_REQUIRED,
    val bleCapabilities: BleCapabilities = BleCapabilities(
        bleSupported = true,
        codedPhySupported = false,
        extendedAdvSupported = false,
        hypRangeActive = false
    ),
    val hypRangeEnabled: Boolean = false,
    val btClassicEnabled: Boolean = false,
    val btClassicStatus: BtClassicStatus = BtClassicStatus.STANDBY,
    val wifiDirectEnabled: Boolean = false,
    val wifiDirectStatus: WifiP2pStatus = WifiP2pStatus.STANDBY,
    val wifiDirectGroupOwnerIp: String? = null,
    val wifiDirectIsGroupOwner: Boolean = false,
    val wifiDirectPeers: List<DiscoveredWifiDirectPeer> = emptyList(),
    val lanActive: Boolean = false,
    val localIpAddress: String? = null,
    val localTcpPort: Int = 0
)

class AetherMeshViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val survivalAiRepository = SurvivalAiRepository()

    private val selfNodeId: String = deriveStableNodeId(application)
    private var selfCallsign: String = (Build.MODEL ?: "Android").take(16)

    private val _meshState = MutableStateFlow(
        MeshCommandUiState(
            selfNodeId = selfNodeId,
            selfCallsign = selfCallsign
        )
    )
    val meshState: StateFlow<MeshCommandUiState> = _meshState.asStateFlow()

    private val acousticModem = AcousticFskModem(viewModelScope)
    val acousticState: StateFlow<AcousticModemState> = acousticModem.state

    private val _aiState = MutableStateFlow<AiTriageUiState>(AiTriageUiState.Idle)
    val aiState: StateFlow<AiTriageUiState> = _aiState.asStateFlow()

    private val _triageHistory = MutableStateFlow<List<TriageExchange>>(emptyList())
    val triageHistory: StateFlow<List<TriageExchange>> = _triageHistory.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val shortTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val lanPeers = ConcurrentHashMap<String, DiscoveredLanPeer>()
    private val blePeers = ConcurrentHashMap<String, DiscoveredBlePeer>()
    private val btClassicPeers = ConcurrentHashMap<String, DiscoveredBtClassicPeer>()
    private val wifiDirectPeers = ConcurrentHashMap<String, DiscoveredWifiDirectPeer>()
    private val manualOfflinePeers = ConcurrentHashMap.newKeySet<String>()
    private val customNodePositions = ConcurrentHashMap<String, Pair<Float, Float>>()
    private val seenPacketIds = LinkedHashSet<String>()

    private var logCounter = 0L
    private var msgCounter = 0L
    private var packetSeq = 100
    private var pinCounter = 0
    private var lastDeliveredPath: List<String> = emptyList()
    private var latestEncryptedPacket: EncryptedPacket? = null
    private var pulseJob: Job? = null
    private var aiJob: Job? = null

    private val lanTransport = LanMeshTransport(
        context = application,
        scope = viewModelScope,
        selfNodeId = selfNodeId,
        getSelfName = { selfCallsign },
        onPeerDiscovered = { peer ->
            val isNew = !lanPeers.containsKey(peer.nodeId) && !blePeers.containsKey(peer.nodeId)
            lanPeers[peer.nodeId] = peer
            if (isNew) {
                appendLog(
                    tag = "PEER_LAN",
                    message = "Discovered real peer ${peer.deviceName} (${peer.nodeId}) @ ${peer.ipAddress}:${peer.tcpPort}",
                    severity = LogSeverity.SUCCESS
                )
            }
            rebuildRealTopology("LAN_DISCOVERY")
        },
        onPacketReceived = { wireJson, transportLabel, senderInfo ->
            handleIncomingWirePacket(wireJson, transportLabel, senderInfo)
        },
        onStatusChanged = { active, localIp, tcpPort ->
            _meshState.update {
                it.copy(
                    lanActive = active,
                    localIpAddress = localIp,
                    localTcpPort = tcpPort
                )
            }
        }
    )

    private val bleTransport = BleMeshTransport(
        context = application,
        selfNodeId = selfNodeId,
        getSelfName = { selfCallsign },
        onPeerDiscovered = { peer ->
            val isNew = !blePeers.containsKey(peer.nodeId) && !lanPeers.containsKey(peer.nodeId)
            blePeers[peer.nodeId] = peer
            if (isNew) {
                val phyLabel = if (peer.isCodedPhy) "HYPRANGE CODED-PHY" else "BLE-1M"
                appendLog(
                    tag = if (peer.isCodedPhy) "HYPRANGE" else "PEER_BLE",
                    message = "Discovered $phyLabel peer ${peer.deviceName} (${peer.nodeId}) RSSI=${peer.rssiDbm}dBm (~${peer.distanceMeters.roundToInt()}m)",
                    severity = LogSeverity.SUCCESS
                )
            }
            rebuildRealTopology("BLE_DISCOVERY")
        },
        onPacketReceived = { wireJson, transportLabel, senderInfo ->
            handleIncomingWirePacket(wireJson, transportLabel, senderInfo)
        },
        onStatusChanged = { status, caps ->
            _meshState.update {
                it.copy(
                    bleStatus = status,
                    bleCapabilities = caps
                )
            }
        }
    )

    private val btClassicTransport = BtClassicMeshTransport(
        context = application,
        scope = viewModelScope,
        selfNodeId = selfNodeId,
        getSelfName = { selfCallsign },
        onPeerDiscovered = { peer ->
            val isNew = !btClassicPeers.containsKey(peer.nodeId)
            btClassicPeers[peer.nodeId] = peer
            if (isNew) {
                appendLog(
                    tag = "BT_CLASSIC",
                    message = "Discovered BT Classic SPP peer ${peer.deviceName} (${peer.btAddress})",
                    severity = LogSeverity.SUCCESS
                )
            }
            rebuildRealTopology("BT_CLASSIC_DISCOVERY")
        },
        onPacketReceived = { wireJson, transportLabel, senderInfo ->
            handleIncomingWirePacket(wireJson, transportLabel, senderInfo)
        },
        onStatusChanged = { status ->
            _meshState.update { it.copy(btClassicStatus = status) }
        }
    )

    private val wifiP2pTransport = WifiP2pMeshTransport(
        context = application,
        onPeersUpdated = { peers ->
            wifiDirectPeers.clear()
            peers.forEach { wifiDirectPeers[it.nodeId] = it }
            _meshState.update { it.copy(wifiDirectPeers = peers) }
            if (peers.isNotEmpty()) {
                appendLog(
                    tag = "WIFI_P2P",
                    message = "Wi-Fi Direct scan: ${peers.size} device(s) nearby (${peers.joinToString { p -> p.deviceName }}).",
                    severity = LogSeverity.INFO
                )
            }
            rebuildRealTopology("WIFI_P2P_PEERS")
        },
        onGroupConnectionUpdated = { groupFormed, isGroupOwner, goIp ->
            _meshState.update {
                it.copy(
                    wifiDirectGroupOwnerIp = goIp,
                    wifiDirectIsGroupOwner = isGroupOwner
                )
            }
            if (groupFormed) {
                lanTransport.sendDiscoveryBurst()
            }
        },
        onStatusChanged = { status ->
            _meshState.update { it.copy(wifiDirectStatus = status) }
        },
        onEventLog = { tag, message, isError ->
            appendLog(tag, message, if (isError) LogSeverity.WARNING else LogSeverity.INFO)
        }
    )

    init {
        initializeRealMesh()
        lanTransport.start()
        bleTransport.start()
        startPeerPruner()
    }

    private fun deriveStableNodeId(app: Application): String {
        val raw = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
            ?: (Build.MODEL + Build.DEVICE)
        val suffix = abs(raw.hashCode()).toString(16).uppercase(Locale.US).padStart(4, '0').take(4)
        return "AETH-$suffix"
    }

    private fun nowTs(): String = timeFormat.format(Date())
    private fun shortTs(): String = shortTimeFormat.format(Date())

    private fun initializeRealMesh() {
        val selfNode = MeshRouter.buildSelfNode(selfNodeId, selfCallsign)
        val bleCaps = bleTransport.getCapabilities()
        val bootLogs = listOf(
            TerminalLogEntry(
                id = ++logCounter,
                timestamp = nowTs(),
                tag = "INIT",
                message = "Multi-Radio Mesh ready ($selfNodeId // $selfCallsign). Zero fake nodes.",
                severity = LogSeverity.INFO
            ),
            TerminalLogEntry(
                id = ++logCounter,
                timestamp = nowTs(),
                tag = "HYPRANGE",
                message = if (bleCaps.codedPhySupported && bleCaps.extendedAdvSupported) {
                    "BLE 5+ Coded PHY hardware READY — toggle HypRange for extended range."
                } else {
                    "BLE 1M standard radio READY (Coded PHY S=8 depends on device chipset)."
                },
                severity = LogSeverity.INFO
            ),
            TerminalLogEntry(
                id = ++logCounter,
                timestamp = nowTs(),
                tag = "CRYPTO",
                message = "AES-128-CBC tactical key active (${TacticalCrypto.KEY_FINGERPRINT}).",
                severity = LogSeverity.SUCCESS
            )
        )

        _meshState.update {
            it.copy(
                nodes = listOf(selfNode),
                links = emptyList(),
                connectedPeerCount = 0,
                onlineNodeCount = 1,
                meshHopEfficiencyPct = 100,
                terminalLogs = bootLogs,
                bleStatus = bleTransport.checkStatus(),
                bleCapabilities = bleCaps,
                btClassicStatus = btClassicTransport.checkStatus(),
                wifiDirectStatus = wifiP2pTransport.checkStatus()
            )
        }
    }

    private fun startPeerPruner() {
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                val now = System.currentTimeMillis()
                var prunedAny = false

                val lanIter = lanPeers.entries.iterator()
                while (lanIter.hasNext()) {
                    val entry = lanIter.next()
                    if (now - entry.value.lastSeenMs > PEER_STALE_MS) {
                        appendLog("PEER_LOST", "Peer ${entry.value.deviceName} (${entry.key}) timed out on LAN.", LogSeverity.WARNING)
                        lanIter.remove()
                        prunedAny = true
                    }
                }

                val bleIter = blePeers.entries.iterator()
                while (bleIter.hasNext()) {
                    val entry = bleIter.next()
                    if (now - entry.value.lastSeenMs > PEER_STALE_MS) {
                        appendLog("PEER_LOST", "Peer ${entry.value.deviceName} (${entry.key}) moved out of BLE range.", LogSeverity.WARNING)
                        bleIter.remove()
                        prunedAny = true
                    }
                }

                val btcIter = btClassicPeers.entries.iterator()
                while (btcIter.hasNext()) {
                    val entry = btcIter.next()
                    if (now - entry.value.lastSeenMs > PEER_STALE_MS * 2) {
                        btClassicIterRemove(btcIter, entry.value.deviceName)
                        prunedAny = true
                    }
                }

                if (prunedAny) {
                    rebuildRealTopology("PEER_TIMEOUT")
                }
            }
        }
    }

    private fun btClassicIterRemove(iter: MutableIterator<MutableMap.MutableEntry<String, DiscoveredBtClassicPeer>>, name: String) {
        appendLog("BT_LOST", "BT Classic peer $name timed out.", LogSeverity.WARNING)
        iter.remove()
    }

    fun getRequiredBlePermissions(): Array<String> = bleTransport.requiredPermissions()

    fun getRequiredRadioPermissions(): Array<String> {
        return (bleTransport.requiredPermissions().toList() +
            btClassicTransport.requiredPermissions().toList() +
            wifiP2pTransport.requiredPermissions().toList()).distinct().toTypedArray()
    }

    fun onBlePermissionsResult() {
        onRadioPermissionsResult()
    }

    fun onRadioPermissionsResult() {
        val bleStatus = bleTransport.checkStatus()
        val bleCaps = bleTransport.getCapabilities()
        _meshState.update {
            it.copy(
                bleStatus = bleStatus,
                bleCapabilities = bleCaps,
                btClassicStatus = btClassicTransport.checkStatus(),
                wifiDirectStatus = wifiP2pTransport.checkStatus()
            )
        }
        if (bleStatus == BleRadioStatus.ACTIVE) {
            bleTransport.start()
            appendLog("BLE", "BLE radio active — advertising & scanning.", LogSeverity.SUCCESS)
        } else if (bleStatus == BleRadioStatus.BLUETOOTH_DISABLED) {
            appendLog("BLE", "Bluetooth is OFF. Enable Bluetooth for BLE / HypRange / BT Classic.", LogSeverity.WARNING)
        }
        if (_meshState.value.btClassicEnabled) {
            btClassicTransport.start()
        }
        if (_meshState.value.wifiDirectEnabled) {
            wifiP2pTransport.start()
        }
    }

    fun toggleHypRange(enabled: Boolean? = null) {
        val next = enabled ?: !_meshState.value.hypRangeEnabled
        val caps = bleTransport.getCapabilities()
        bleTransport.setHypRangeEnabled(next)
        val updatedCaps = bleTransport.getCapabilities()
        _meshState.update {
            it.copy(
                hypRangeEnabled = next,
                bleCapabilities = updatedCaps
            )
        }
        if (next) {
            val detail = if (caps.codedPhySupported && caps.extendedAdvSupported) {
                "HypRange (BLE 5+ Coded PHY S=8) ENGAGED — long-range beacons & 1.45x link horizon active."
            } else {
                "HypRange requested: device hardware reports Coded PHY=${caps.codedPhySupported}; running max-sensitivity extended scan + range profile."
            }
            appendLog("HYPRANGE", detail, if (caps.codedPhySupported) LogSeverity.SUCCESS else LogSeverity.WARNING)
        } else {
            appendLog("HYPRANGE", "HypRange disabled — reverted to standard BLE 1M PHY.", LogSeverity.INFO)
        }
        rebuildRealTopology("HYPRANGE_MODE")
    }

    fun toggleBtClassic(enabled: Boolean? = null) {
        val next = enabled ?: !_meshState.value.btClassicEnabled
        _meshState.update { it.copy(btClassicEnabled = next) }
        if (next) {
            btClassicTransport.start()
            appendLog("BT_CLASSIC", "Bluetooth Classic RFCOMM/SPP server active & probing nearby phones/tablets.", LogSeverity.SUCCESS)
        } else {
            btClassicTransport.stop()
            btClassicPeers.clear()
            appendLog("BT_CLASSIC", "Bluetooth Classic radio placed on standby.", LogSeverity.INFO)
            rebuildRealTopology("BT_CLASSIC_STOP")
        }
    }

    fun toggleWifiDirect(enabled: Boolean? = null) {
        val next = enabled ?: !_meshState.value.wifiDirectEnabled
        _meshState.update { it.copy(wifiDirectEnabled = next) }
        if (next) {
            wifiP2pTransport.start()
        } else {
            wifiP2pTransport.stop()
            wifiDirectPeers.clear()
            _meshState.update {
                it.copy(
                    wifiDirectPeers = emptyList(),
                    wifiDirectGroupOwnerIp = null,
                    wifiDirectIsGroupOwner = false
                )
            }
            appendLog("WIFI_P2P", "Wi-Fi Direct radio stopped.", LogSeverity.INFO)
            rebuildRealTopology("WIFI_P2P_STOP")
        }
    }

    fun createWifiDirectGroup() {
        if (!_meshState.value.wifiDirectEnabled) {
            _meshState.update { it.copy(wifiDirectEnabled = true) }
            wifiP2pTransport.start()
        }
        wifiP2pTransport.createGroup()
    }

    fun connectWifiDirectPeer(deviceAddress: String) {
        if (!_meshState.value.wifiDirectEnabled) {
            _meshState.update { it.copy(wifiDirectEnabled = true) }
            wifiP2pTransport.start()
        }
        wifiP2pTransport.connectToPeer(deviceAddress)
    }

    fun rescanPeers() {
        lanTransport.sendDiscoveryBurst()
        val bleStatus = bleTransport.checkStatus()
        _meshState.update {
            it.copy(
                bleStatus = bleStatus,
                bleCapabilities = bleTransport.getCapabilities()
            )
        }
        if (bleStatus == BleRadioStatus.ACTIVE) {
            bleTransport.rescan()
        }
        if (_meshState.value.btClassicEnabled) {
            btClassicTransport.rescan()
        }
        if (_meshState.value.wifiDirectEnabled) {
            wifiP2pTransport.discoverPeers()
        }
        appendLog("SCAN", "Multi-radio discovery burst dispatched (LAN / BLE-HypRange / BT Classic / Wi-Fi Direct).", LogSeverity.INFO)
    }

    fun updateCallsign(newName: String) {
        val clean = newName.trim().take(16)
        if (clean.isNotEmpty()) {
            selfCallsign = clean
            _meshState.update { it.copy(selfCallsign = clean) }
            rebuildRealTopology("CALLSIGN")
            lanTransport.sendDiscoveryBurst()
        }
    }

    fun onNodeDrag(nodeId: String, newXKm: Float, newYKm: Float) {
        if (nodeId == selfNodeId) return
        val clampedX = newXKm.coerceIn(-4.7f, 4.7f)
        val clampedY = newYKm.coerceIn(-4.7f, 4.7f)
        customNodePositions[nodeId] = clampedX to clampedY
        rebuildRealTopology("DRAG")
    }

    fun updateLinkRange(rangeKm: Float) {
        val clamped = rangeKm.coerceIn(0.5f, 4.5f)
        _meshState.update { it.copy(linkRangeKm = clamped) }
        rebuildRealTopology("RANGE")
    }

    fun selectNode(nodeId: String?) {
        _meshState.update { it.copy(selectedNodeId = nodeId) }
    }

    fun toggleNodeOnline(nodeId: String) {
        if (nodeId == selfNodeId) return
        val nowOffline = if (manualOfflinePeers.contains(nodeId)) {
            manualOfflinePeers.remove(nodeId)
            false
        } else {
            manualOfflinePeers.add(nodeId)
            true
        }
        appendLog(
            tag = if (nowOffline) "NODE_MUTE" else "NODE_UP",
            message = if (nowOffline) "Peer $nodeId marked offline locally for routing test." else "Peer $nodeId restored.",
            severity = if (nowOffline) LogSeverity.CRITICAL else LogSeverity.SUCCESS
        )
        rebuildRealTopology(if (nowOffline) "NODE_DOWN" else "NODE_UP")
    }

    fun toggleDisasterBlackout() {
        val nextBlackout = !_meshState.value.blackoutActive
        val peerIds = _meshState.value.nodes.filter { it.role != NodeRole.SELF }.map { it.id }

        if (nextBlackout) {
            if (peerIds.isEmpty()) {
                appendLog(
                    tag = "BLACKOUT",
                    message = "No real peers connected yet. Connect a second phone/tablet first.",
                    severity = LogSeverity.WARNING
                )
                return
            }
            if (peerIds.size >= 2) {
                manualOfflinePeers.add(peerIds.first())
                appendLog(
                    tag = "BLACKOUT",
                    message = "Blackout test: severed link to ${peerIds.first()}. Rerouting via remaining peers...",
                    severity = LogSeverity.CRITICAL
                )
            } else {
                manualOfflinePeers.add(peerIds.first())
                appendLog(
                    tag = "BLACKOUT",
                    message = "Blackout test: severed sole peer ${peerIds.first()}! Use Acoustic FSK Modem fallback.",
                    severity = LogSeverity.CRITICAL
                )
            }
        } else {
            manualOfflinePeers.clear()
            appendLog("RESTORE", "Blackout cleared — all discovered peers restored.", LogSeverity.SUCCESS)
        }

        _meshState.update { it.copy(blackoutActive = nextBlackout) }
        rebuildRealTopology(if (nextBlackout) "BLACKOUT" else "RESTORE")
    }

    private fun rebuildRealTopology(reasonTag: String) {
        val selfNode = MeshRouter.buildSelfNode(selfNodeId, selfCallsign)
        val allIds = (lanPeers.keys + blePeers.keys + btClassicPeers.keys + wifiDirectPeers.keys).distinct()

        val peerNodes = allIds.map { id ->
            val lan = lanPeers[id]
            val ble = blePeers[id]
            val btc = btClassicPeers[id]
            val wfd = wifiDirectPeers[id]

            val name = lan?.deviceName ?: ble?.deviceName ?: btc?.deviceName ?: wfd?.deviceName ?: id
            val distMeters = ble?.distanceMeters ?: btc?.distanceMeters ?: if (wfd != null) 25f else 12f
            val pos = customNodePositions[id] ?: MeshRouter.computePeerRadarPosition(id, distMeters)

            val tags = buildList {
                if (lan != null) add("LAN")
                if (ble != null) add(if (ble.isCodedPhy) "HYPRANGE" else "BLE")
                if (btc != null) add("BT-SPP")
                if (wfd != null) add(if (wfd.isConnected) "WFD-LINK" else "WFD-PEER")
            }
            val transportLabel = tags.joinToString("+").ifEmpty { "MESH" }
            val isCoded = (ble?.isCodedPhy == true) || (_meshState.value.hypRangeEnabled && ble != null)

            MeshNode(
                id = id,
                label = name,
                role = NodeRole.REAL_PEER,
                xKm = pos.first,
                yKm = pos.second,
                isOnline = !manualOfflinePeers.contains(id),
                batteryPct = 100,
                signalDbm = ble?.rssiDbm ?: btc?.rssiDbm ?: -42,
                transport = transportLabel,
                ipAddress = lan?.ipAddress ?: wfd?.groupOwnerAddress,
                tcpPort = lan?.tcpPort ?: if (wfd?.isConnected == true) 45873 else null,
                bleAddress = ble?.bleAddress,
                isCodedPhy = isCoded,
                btClassicAddress = btc?.btAddress,
                wifiDirectAddress = wfd?.deviceAddress,
                wifiDirectConnected = wfd?.isConnected == true,
                distanceMeters = distMeters,
                lastSeenMs = maxOf(
                    lan?.lastSeenMs ?: 0L,
                    ble?.lastSeenMs ?: 0L,
                    btc?.lastSeenMs ?: 0L,
                    wfd?.lastSeenMs ?: 0L
                )
            )
        }

        val allNodes = listOf(selfNode) + peerNodes
        val rangeKm = _meshState.value.linkRangeKm
        val rawLinks = MeshRouter.buildLinks(allNodes, rangeKm)

        val targetPeer = peerNodes.filter { it.isOnline }.maxByOrNull { it.distanceMeters }
        val newRoute = targetPeer?.let {
            MeshRouter.findShortestPath(allNodes, rawLinks, selfNodeId, it.id)
        }

        val brokenAt = MeshRouter.isRouteBroken(lastDeliveredPath, allNodes, rangeKm)
        var isRerouted = _meshState.value.isRouteRerouted
        var rerouteBanner = _meshState.value.rerouteAlertBanner

        if (brokenAt != null && newRoute != null && newRoute.path != lastDeliveredPath) {
            isRerouted = true
            val pathStr = newRoute.path.joinToString(" -> ")
            rerouteBanner = "REROUTED AROUND $brokenAt: $pathStr"
            appendLog(
                tag = "REROUTE",
                message = "Broken link detected at [$brokenAt]! Adaptive reroute: $pathStr",
                severity = LogSeverity.REROUTE
            )
            lastDeliveredPath = newRoute.path
        } else if (brokenAt != null && newRoute == null) {
            isRerouted = false
            rerouteBanner = "LINK LOST AT $brokenAt — NO PEER ROUTE"
        } else if (newRoute != null) {
            if (!_meshState.value.blackoutActive && (reasonTag == "RESTORE" || reasonTag == "NODE_UP")) {
                isRerouted = false
                rerouteBanner = null
            }
            if (lastDeliveredPath.isEmpty()) {
                lastDeliveredPath = newRoute.path
            }
        } else if (peerNodes.isEmpty()) {
            isRerouted = false
            rerouteBanner = null
            lastDeliveredPath = emptyList()
        }

        val styledLinks = MeshRouter.buildLinks(
            nodes = allNodes,
            rangeKm = rangeKm,
            activeRoutePath = newRoute?.path.orEmpty(),
            isReroutedRoute = isRerouted
        )

        val eff = if (newRoute != null) {
            MeshRouter.computeEfficiencyPct(newRoute.hopCount, newRoute.totalLatencyMs, delivered = true)
        } else if (peerNodes.isEmpty()) {
            100
        } else {
            0
        }

        _meshState.update { state ->
            state.copy(
                nodes = allNodes,
                links = styledLinks,
                activeRoute = newRoute,
                isRouteRerouted = isRerouted,
                rerouteAlertBanner = rerouteBanner,
                connectedPeerCount = MeshRouter.countConnectedPeers(allNodes, styledLinks, selfNodeId),
                onlineNodeCount = allNodes.count { it.isOnline },
                meshHopEfficiencyPct = eff,
                avgLatencyMs = newRoute?.totalLatencyMs ?: 0
            )
        }
    }

    fun sendP2pMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return

        val current = _meshState.value
        val onlinePeers = current.nodes.filter { it.role != NodeRole.SELF && it.isOnline }
        val pktId = "MSG-0${++packetSeq}"

        val payloadJson = JSONObject().apply {
            put("type", "P2P_CHAT")
            put("packet_id", pktId)
            put("src", selfNodeId)
            put("src_name", selfCallsign)
            put("msg", clean)
            put("ts", shortTs())
        }

        val encrypted = TacticalCrypto.encryptJsonPayload(
            packetId = pktId,
            packetType = "P2P_MSG",
            sourceNode = selfNodeId,
            targetNode = if (onlinePeers.isEmpty()) "LOCAL" else "${onlinePeers.size} PEERS",
            jsonPayload = payloadJson,
            timestamp = shortTs()
        )
        latestEncryptedPacket = encrypted
        val inspection = TacticalCrypto.inspectAndDecrypt(encrypted, useWrongKeyForTest = current.simulateWrongKey)

        _meshState.update { state ->
            state.copy(
                latestPacketInspection = inspection,
                p2pMessages = (listOf(
                    P2pCommsMessage(
                        id = ++msgCounter,
                        senderId = selfNodeId,
                        senderName = "$selfCallsign [YOU]",
                        body = clean,
                        transport = if (onlinePeers.isEmpty()) "LOCAL" else onlinePeers.first().transport,
                        isOutgoing = true,
                        isSos = false,
                        encryptedVerified = true,
                        timestamp = shortTs()
                    )
                ) + state.p2pMessages).take(40)
            )
        }

        if (onlinePeers.isEmpty()) {
            appendLog(
                tag = "TX_WARN",
                message = "[$pktId] Encrypted locally (AES-128), but 0 peers connected. Pair via LAN, BLE/HypRange, BT Classic, or Wi-Fi Direct!",
                severity = LogSeverity.WARNING
            )
            return
        }

        dispatchEncryptedPacketToPeers(encrypted, onlinePeers)
    }

    fun broadcastSosPacket() {
        val current = _meshState.value
        val onlinePeers = current.nodes.filter { it.role != NodeRole.SELF && it.isOnline }
        val seq = ++packetSeq
        val pktId = "SOS-0$seq"

        val payloadJson = JSONObject().apply {
            put("type", "SOS_CRITICAL_ALERT")
            put("packet_id", pktId)
            put("src", selfNodeId)
            put("src_name", selfCallsign)
            put("dst", if (onlinePeers.isEmpty()) "BROADCAST" else onlinePeers.joinToString(",") { it.id })
            put("msg", "EMERGENCY SOS BEACON FROM $selfCallsign")
            put("ts", nowTs())
        }

        val encrypted = TacticalCrypto.encryptJsonPayload(
            packetId = pktId,
            packetType = "SOS",
            sourceNode = selfNodeId,
            targetNode = if (onlinePeers.isEmpty()) "NO_PEER" else "${onlinePeers.size} PEER(S)",
            jsonPayload = payloadJson,
            timestamp = shortTs()
        )
        latestEncryptedPacket = encrypted
        val inspection = TacticalCrypto.inspectAndDecrypt(encrypted, useWrongKeyForTest = current.simulateWrongKey)

        _meshState.update { state ->
            state.copy(
                latestPacketInspection = inspection,
                p2pMessages = (listOf(
                    P2pCommsMessage(
                        id = ++msgCounter,
                        senderId = selfNodeId,
                        senderName = "$selfCallsign [YOU]",
                        body = "CRITICAL SOS BEACON BROADCAST",
                        transport = if (onlinePeers.isEmpty()) "NONE" else "MESH",
                        isOutgoing = true,
                        isSos = true,
                        encryptedVerified = true,
                        timestamp = shortTs()
                    )
                ) + state.p2pMessages).take(40)
            )
        }

        if (onlinePeers.isEmpty()) {
            val failedSample = MeshEfficiencySample(
                sequence = current.efficiencyHistory.size + 1,
                hopCount = 0,
                latencyMs = 0,
                efficiencyPct = 0,
                delivered = false
            )
            _meshState.update { state ->
                state.copy(
                    meshHopEfficiencyPct = 0,
                    efficiencyHistory = (state.efficiencyHistory + failedSample).takeLast(12)
                )
            }
            appendLog(
                tag = "SOS_WAIT",
                message = "[$pktId] AES-128 SOS ready (${encrypted.cipherBytes.size}B) — 0 real peers in range. Enable a radio or Acoustic FSK Modem.",
                severity = LogSeverity.CRITICAL
            )
            return
        }

        dispatchEncryptedPacketToPeers(encrypted, onlinePeers)
    }

    private fun dispatchEncryptedPacketToPeers(
        encrypted: EncryptedPacket,
        peers: List<MeshNode>
    ) {
        val wireJson = encrypted.toWireJson(ttl = 3, visited = listOf(selfNodeId))
        appendLog(
            tag = "TX_AES",
            message = "[${encrypted.packetId}] Sending ${encrypted.cipherBytes.size}B AES-128 packet across active radios...",
            severity = LogSeverity.INFO
        )

        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            var deliveredCount = 0
            val wfdGoIp = _meshState.value.wifiDirectGroupOwnerIp
            val wfdIsGo = _meshState.value.wifiDirectIsGroupOwner

            for (peer in peers) {
                var sent = false
                if (peer.ipAddress != null && peer.tcpPort != null) {
                    sent = lanTransport.sendEncryptedPacket(peer.ipAddress, peer.tcpPort, wireJson)
                    if (sent) {
                        val tag = if (peer.wifiDirectConnected) "WFD_OK" else "LAN_OK"
                        appendLog(tag, "[${encrypted.packetId}] Delivered via TCP -> ${peer.label} (${peer.ipAddress})", LogSeverity.SUCCESS)
                    }
                }
                if (!sent && peer.wifiDirectConnected && !wfdIsGo && wfdGoIp != null) {
                    sent = lanTransport.sendEncryptedPacket(wfdGoIp, 45873, wireJson)
                    if (sent) {
                        appendLog("WFD_OK", "[${encrypted.packetId}] Delivered via Wi-Fi Direct -> ${peer.label} ($wfdGoIp)", LogSeverity.SUCCESS)
                    }
                }
                if (!sent && peer.bleAddress != null) {
                    sent = bleTransport.sendEncryptedPacket(
                        bleAddress = peer.bleAddress,
                        wireJson = wireJson,
                        preferCodedPhy = _meshState.value.hypRangeEnabled || peer.isCodedPhy
                    )
                    if (sent) {
                        val label = if (_meshState.value.hypRangeEnabled || peer.isCodedPhy) "HYPRANGE" else "BLE_OK"
                        appendLog(label, "[${encrypted.packetId}] Delivered via BLE GATT -> ${peer.label} (${peer.bleAddress})", LogSeverity.SUCCESS)
                    }
                }
                if (!sent && peer.btClassicAddress != null) {
                    sent = btClassicTransport.sendEncryptedPacket(peer.btClassicAddress, wireJson)
                    if (sent) {
                        appendLog("BTC_OK", "[${encrypted.packetId}] Delivered via BT Classic RFCOMM -> ${peer.label}", LogSeverity.SUCCESS)
                    }
                }
                if (sent) {
                    deliveredCount++
                    animatePacketPulse(encrypted.packetId, listOf(selfNodeId, peer.id), isRerouted = _meshState.value.isRouteRerouted)
                } else {
                    appendLog("TX_ERR", "[${encrypted.packetId}] No reachable socket on ${peer.label}. If Wi-Fi Direct, pair first.", LogSeverity.WARNING)
                }
            }

            val elapsedMs = (System.currentTimeMillis() - startMs).toInt().coerceAtLeast(6)
            val eff = MeshRouter.computeEfficiencyPct(
                hopCount = 1,
                totalLatencyMs = elapsedMs,
                delivered = deliveredCount > 0
            )
            val sample = MeshEfficiencySample(
                sequence = _meshState.value.efficiencyHistory.size + 1,
                hopCount = if (deliveredCount > 0) 1 else 0,
                latencyMs = elapsedMs,
                efficiencyPct = eff,
                delivered = deliveredCount > 0
            )
            _meshState.update { state ->
                state.copy(
                    meshHopEfficiencyPct = eff,
                    avgLatencyMs = elapsedMs,
                    efficiencyHistory = (state.efficiencyHistory + sample).takeLast(12)
                )
            }
        }
    }

    private fun handleIncomingWirePacket(
        wireJson: String,
        transportLabel: String,
        senderInfo: String
    ) {
        val currentWrongKey = _meshState.value.simulateWrongKey
        val parsed = TacticalCrypto.parseAndDecryptWireEnvelope(wireJson, useWrongKeyForTest = currentWrongKey)
            ?: return

        val pkt = parsed.packet
        synchronized(seenPacketIds) {
            if (!seenPacketIds.add(pkt.packetId)) return
            if (seenPacketIds.size > 150) {
                seenPacketIds.remove(seenPacketIds.first())
            }
        }

        latestEncryptedPacket = pkt
        _meshState.update { it.copy(latestPacketInspection = parsed.inspection) }

        val obj = parsed.decryptedObject
        if (obj == null) {
            appendLog(
                tag = "RX_FAIL",
                message = "[${pkt.packetId}] Received via $transportLabel ($senderInfo), AES-128 decryption failed!",
                severity = LogSeverity.CRITICAL
            )
            return
        }

        val type = obj.optString("type")
        val srcName = obj.optString("src_name", pkt.sourceNode)

        when (type) {
            "P2P_CHAT", "SOS_CRITICAL_ALERT" -> {
                val isSos = type == "SOS_CRITICAL_ALERT"
                val msgText = obj.optString("msg", "")
                _meshState.update { state ->
                    state.copy(
                        p2pMessages = (listOf(
                            P2pCommsMessage(
                                id = ++msgCounter,
                                senderId = pkt.sourceNode,
                                senderName = srcName,
                                body = msgText,
                                transport = transportLabel,
                                isOutgoing = false,
                                isSos = isSos,
                                encryptedVerified = true,
                                timestamp = shortTs()
                            )
                        ) + state.p2pMessages).take(40)
                    )
                }
                appendLog(
                    tag = if (isSos) "RX_SOS" else "RX_MSG",
                    message = "[${pkt.packetId}] $transportLabel from $srcName: \"$msgText\" (AES-128 Verified)",
                    severity = if (isSos) LogSeverity.CRITICAL else LogSeverity.SUCCESS
                )
                animatePacketPulse(pkt.packetId, listOf(pkt.sourceNode, selfNodeId), isRerouted = false)
            }

            "TACTICAL_CROWD_PIN" -> {
                val catName = obj.optString("category", PinCategory.MEDICAL_AID.name)
                val cat = runCatching { PinCategory.valueOf(catName) }.getOrDefault(PinCategory.MEDICAL_AID)
                val xKm = obj.optDouble("x_km", 0.0).toFloat()
                val yKm = obj.optDouble("y_km", 0.0).toFloat()
                val pinId = obj.optString("pin_id", "PIN-${++pinCounter}")
                val pin = TacticalPin(
                    id = pinId,
                    category = cat,
                    xKm = xKm,
                    yKm = yKm,
                    authorNode = srcName,
                    timestamp = shortTs(),
                    propagatedCount = 1
                )
                _meshState.update { state ->
                    state.copy(pins = (listOf(pin) + state.pins).take(24))
                }
                appendLog(
                    tag = "RX_PIN",
                    message = "[${pkt.packetId}] $transportLabel synced ${cat.label} pin from $srcName.",
                    severity = LogSeverity.WARNING
                )
            }
        }

        if (parsed.ttl > 1 && selfNodeId !in parsed.visited) {
            val relayPeers = _meshState.value.nodes.filter {
                it.role != NodeRole.SELF && it.isOnline && it.id != pkt.sourceNode && it.id !in parsed.visited
            }
            if (relayPeers.isNotEmpty()) {
                val nextWire = pkt.toWireJson(ttl = parsed.ttl - 1, visited = parsed.visited + selfNodeId)
                viewModelScope.launch {
                    for (peer in relayPeers) {
                        if (peer.ipAddress != null && peer.tcpPort != null) {
                            lanTransport.sendEncryptedPacket(peer.ipAddress, peer.tcpPort, nextWire)
                        } else if (peer.bleAddress != null) {
                            bleTransport.sendEncryptedPacket(peer.bleAddress, nextWire, _meshState.value.hypRangeEnabled)
                        } else if (peer.btClassicAddress != null) {
                            btClassicTransport.sendEncryptedPacket(peer.btClassicAddress, nextWire)
                        }
                    }
                    appendLog("RELAY", "[${pkt.packetId}] Multi-hop relayed encrypted packet to ${relayPeers.size} peer(s).", LogSeverity.REROUTE)
                }
            }
        }
    }

    private fun animatePacketPulse(pktId: String, path: List<String>, isRerouted: Boolean) {
        if (path.size < 2) return
        pulseJob?.cancel()
        pulseJob = viewModelScope.launch {
            val totalSegments = path.size - 1
            val stepDelay = if (_meshState.value.batterySaverEnabled) 55L else 28L

            for (seg in 0 until totalSegments) {
                val steps = if (_meshState.value.batterySaverEnabled) 6 else 10
                for (step in 0..steps) {
                    _meshState.update {
                        it.copy(
                            packetPulse = PacketPulseState(
                                isActive = true,
                                path = path,
                                currentSegmentIndex = seg,
                                segmentProgress = step.toFloat() / steps.toFloat(),
                                packetId = pktId,
                                isRerouted = isRerouted
                            )
                        )
                    }
                    delay(stepDelay)
                }
            }
            _meshState.update { it.copy(packetPulse = PacketPulseState(isActive = false)) }
        }
    }

    fun armPinCategory(category: PinCategory?) {
        _meshState.update { state ->
            val next = if (state.armedPinCategory == category) null else category
            state.copy(armedPinCategory = next)
        }
    }

    fun dropPinAt(xKm: Float, yKm: Float, categoryOverride: PinCategory? = null) {
        val current = _meshState.value
        val category = categoryOverride ?: current.armedPinCategory ?: return
        val clampedX = xKm.coerceIn(-4.6f, 4.6f)
        val clampedY = yKm.coerceIn(-4.6f, 4.6f)
        val pinId = "PIN-${++pinCounter}"
        val onlinePeers = current.nodes.filter { it.role != NodeRole.SELF && it.isOnline }

        val pin = TacticalPin(
            id = pinId,
            category = category,
            xKm = clampedX,
            yKm = clampedY,
            authorNode = selfCallsign,
            timestamp = shortTs(),
            propagatedCount = onlinePeers.size
        )

        val pktId = "PIN-0${++packetSeq}"
        val pinPayload = JSONObject().apply {
            put("type", "TACTICAL_CROWD_PIN")
            put("pin_id", pinId)
            put("category", category.name)
            put("label", category.label)
            put("x_km", (clampedX * 100f).roundToInt() / 100f)
            put("y_km", (clampedY * 100f).roundToInt() / 100f)
            put("author", selfNodeId)
            put("src_name", selfCallsign)
            put("propagated_peers", onlinePeers.size)
            put("ts", nowTs())
        }

        val encrypted = TacticalCrypto.encryptJsonPayload(
            packetId = pktId,
            packetType = "PIN:${category.shortCode}",
            sourceNode = selfNodeId,
            targetNode = "${onlinePeers.size} PEERS",
            jsonPayload = pinPayload,
            timestamp = shortTs()
        )
        latestEncryptedPacket = encrypted
        val inspection = TacticalCrypto.inspectAndDecrypt(encrypted, useWrongKeyForTest = current.simulateWrongKey)

        _meshState.update { state ->
            state.copy(
                pins = (listOf(pin) + state.pins).take(24),
                armedPinCategory = null,
                latestPacketInspection = inspection
            )
        }

        appendLog(
            tag = "PIN_SYNC",
            message = "[$pktId] ${category.label} dropped -> AES-128 syncing to ${onlinePeers.size} real peer(s).",
            severity = LogSeverity.WARNING
        )

        if (onlinePeers.isNotEmpty()) {
            dispatchEncryptedPacketToPeers(encrypted, onlinePeers)
        }
    }

    fun clearPins() {
        _meshState.update { it.copy(pins = emptyList()) }
        appendLog("PIN_CLR", "Cleared tactical crowd-pins from radar.", LogSeverity.INFO)
    }

    fun toggleSimulateWrongKey() {
        val current = _meshState.value
        val nextWrong = !current.simulateWrongKey
        val updatedInspection = latestEncryptedPacket?.let {
            TacticalCrypto.inspectAndDecrypt(it, useWrongKeyForTest = nextWrong)
        }
        _meshState.update {
            it.copy(
                simulateWrongKey = nextWrong,
                latestPacketInspection = updatedInspection
            )
        }
        appendLog(
            tag = "CRYPTO",
            message = if (nextWrong) {
                "Inspector switched to ROGUE KEY — decryption verification failed as expected."
            } else {
                "Inspector restored SHARED_TAC_01 key — AES-128 decryption verified."
            },
            severity = if (nextWrong) LogSeverity.WARNING else LogSeverity.SUCCESS
        )
    }

    fun toggleBatterySaver() {
        val next = !_meshState.value.batterySaverEnabled
        _meshState.update { it.copy(batterySaverEnabled = next) }
        appendLog(
            tag = "POWER",
            message = if (next) {
                "Battery Saver Mode ENABLED — liquid refraction & sweep throttled."
            } else {
                "Battery Saver Mode DISABLED — full liquid glass optical pipeline restored."
            },
            severity = LogSeverity.INFO
        )
    }

    fun updateAcousticMessage(msg: String) = acousticModem.updateMessage(msg)

    fun setAcousticHighBand(highBand: Boolean) = acousticModem.setHighBandMode(highBand)

    fun transmitAudioPulse() {
        val msg = acousticState.value.message.trim()
        if (msg.isEmpty()) return
        val modeLabel = if (acousticState.value.isHighBandUltrasonic) "6.8/8.2 kHz" else "1.2/2.2 kHz"
        appendLog("ACOUSTIC", "Modulating FSK audio pulse ($modeLabel): \"$msg\"", LogSeverity.WARNING)
        acousticModem.transmitPulse {
            appendLog("ACOUSTIC", "Acoustic FSK burst complete.", LogSeverity.SUCCESS)
        }
    }

    fun stopAudioPulse() {
        acousticModem.stop()
        appendLog("ACOUSTIC", "Acoustic FSK transmission aborted.", LogSeverity.INFO)
    }

    fun querySurvivalAi(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = AiTriageUiState.Loading
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    survivalAiRepository.streamTriageProtocol(cleanPrompt).collect { chunk ->
                        accumulated.append(chunk)
                        _aiState.value = AiTriageUiState.Streaming(
                            query = cleanPrompt,
                            text = accumulated.toString()
                        )
                    }
                }
                val finalResponse = accumulated.toString().trim()
                if (finalResponse.isNotEmpty()) {
                    _aiState.value = AiTriageUiState.Success(cleanPrompt, finalResponse)
                    _triageHistory.update { list ->
                        listOf(
                            TriageExchange(
                                id = System.currentTimeMillis(),
                                query = cleanPrompt,
                                response = finalResponse,
                                timestamp = shortTs()
                            )
                        ) + list
                    }
                } else {
                    _aiState.value = AiTriageUiState.Error(cleanPrompt, "AI returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Survival AI inference timed out", e)
                val msg = "Triage query timed out after 60 seconds. Check connectivity and retry."
                _aiState.value = AiTriageUiState.Error(cleanPrompt, msg)
                _triageHistory.update {
                    listOf(TriageExchange(System.currentTimeMillis(), cleanPrompt, msg, shortTs(), isError = true)) + it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Survival AI inference failed", e)
                val msg = e.localizedMessage ?: "Firebase AI Logic inference error."
                _aiState.value = AiTriageUiState.Error(cleanPrompt, msg)
                _triageHistory.update {
                    listOf(TriageExchange(System.currentTimeMillis(), cleanPrompt, msg, shortTs(), isError = true)) + it
                }
            }
        }
    }

    fun clearAiSession() {
        aiJob?.cancel()
        _aiState.value = AiTriageUiState.Idle
        _triageHistory.value = emptyList()
    }

    private fun appendLog(tag: String, message: String, severity: LogSeverity) {
        val entry = TerminalLogEntry(
            id = ++logCounter,
            timestamp = nowTs(),
            tag = tag,
            message = message,
            severity = severity
        )
        _meshState.update { state ->
            state.copy(terminalLogs = (listOf(entry) + state.terminalLogs).take(60))
        }
    }

    override fun onCleared() {
        super.onCleared()
        lanTransport.stop()
        bleTransport.stop()
        btClassicTransport.stop()
        wifiP2pTransport.stop()
        acousticModem.stop()
        pulseJob?.cancel()
        aiJob?.cancel()
    }
}
