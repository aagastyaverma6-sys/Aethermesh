package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.mesh.LogSeverity
import com.example.mesh.MeshNode
import com.example.mesh.MeshRouter
import com.example.mesh.NodeRole
import com.example.mesh.P2pCommsMessage
import com.example.mesh.PinCategory
import com.example.mesh.TerminalLogEntry
import com.example.transport.BleRadioStatus
import com.example.transport.BtClassicStatus
import com.example.transport.WifiP2pStatus
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.HypRangeViolet
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MeshCommandUiState
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MeshRadarScreen(
    state: MeshCommandUiState,
    radioPermissions: Array<String>,
    onRadioPermissionsResult: () -> Unit,
    onToggleHypRange: () -> Unit,
    onToggleBtClassic: () -> Unit,
    onToggleWifiDirect: () -> Unit,
    onCreateWifiDirectGroup: () -> Unit,
    onConnectWifiDirectPeer: (String) -> Unit,
    onDragNode: (String, Float, Float) -> Unit,
    onTapNode: (String?) -> Unit,
    onToggleNodeOnline: (String) -> Unit,
    onRescanPeers: () -> Unit,
    onToggleBlackout: () -> Unit,
    onBroadcastSos: () -> Unit,
    onSendP2pMessage: (String) -> Unit,
    onUpdateRangeKm: (Float) -> Unit,
    onArmPin: (PinCategory?) -> Unit,
    onDropPinAt: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true
) {
    var messageDraft by rememberSaveable { mutableStateOf("") }

    val radioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRadioPermissionsResult()
    }

    val columnModifier = if (scrollable) {
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp)
    } else {
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    }

    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Multi-Radio Tactical Transceiver Matrix (LAN, BLE + HypRange Coded PHY, BT Classic, Wi-Fi Direct)
        TacticalPanel(
            title = "MULTI-RADIO TRANSCEIVER MATRIX",
            statusColor = when {
                state.hypRangeEnabled -> HypRangeViolet
                state.wifiDirectStatus == WifiP2pStatus.CONNECTED_GROUP_OWNER ||
                    state.wifiDirectStatus == WifiP2pStatus.CONNECTED_CLIENT -> PhosphorEmerald
                else -> TacticalCyan
            },
            badgeText = buildString {
                if (state.hypRangeEnabled) append("HYPRANGE")
                else if (state.bleStatus == BleRadioStatus.ACTIVE) append("BLE 1M")
                else append("LAN")
            },
            batterySaver = state.batterySaverEnabled,
            headerAction = if (
                state.bleStatus == BleRadioStatus.PERMISSION_REQUIRED ||
                state.wifiDirectStatus == WifiP2pStatus.PERMISSION_REQUIRED
            ) {
                {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ElectricAmber.copy(alpha = 0.2f))
                            .border(1.dp, ElectricAmber, RoundedCornerShape(50))
                            .clickable { radioPermissionLauncher.launch(radioPermissions) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("grant_radios_pill")
                    ) {
                        Text(
                            text = "GRANT RADIOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricAmber
                        )
                    }
                }
            } else null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioModeTile(
                        title = "LAN / HOTSPOT",
                        subtitle = if (state.localIpAddress != null) {
                            "${state.localIpAddress}:${state.localTcpPort}"
                        } else {
                            "No Wi-Fi LAN"
                        },
                        statusBadge = if (state.lanActive && state.localIpAddress != null) "ACTIVE" else "LISTEN",
                        icon = Icons.Default.Wifi,
                        active = state.localIpAddress != null,
                        accentColor = PhosphorEmerald,
                        onClick = onRescanPeers,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("radio_tile_lan")
                    )

                    val hypActive = state.hypRangeEnabled
                    val codedHw = state.bleCapabilities.codedPhySupported
                    RadioModeTile(
                        title = "HYPRANGE BLE 5+",
                        subtitle = when {
                            state.bleStatus == BleRadioStatus.PERMISSION_REQUIRED -> "Tap to grant BLE"
                            state.bleStatus == BleRadioStatus.BLUETOOTH_DISABLED -> "Bluetooth OFF"
                            hypActive && codedHw -> "Coded PHY S=8"
                            hypActive -> "Max Sensitivity"
                            codedHw -> "Coded PHY Ready"
                            else -> "BLE 1M Active"
                        },
                        statusBadge = if (hypActive) "HYPRANGE" else "BLE 1M",
                        icon = Icons.Default.Sensors,
                        active = hypActive,
                        accentColor = HypRangeViolet,
                        onClick = {
                            if (state.bleStatus == BleRadioStatus.PERMISSION_REQUIRED) {
                                radioPermissionLauncher.launch(radioPermissions)
                            } else {
                                onToggleHypRange()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("radio_tile_hyprange")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val btcActive = state.btClassicEnabled && state.btClassicStatus == BtClassicStatus.ACTIVE
                    RadioModeTile(
                        title = "BT CLASSIC",
                        subtitle = when (state.btClassicStatus) {
                            BtClassicStatus.ACTIVE -> "RFCOMM SPP Ready"
                            BtClassicStatus.PERMISSION_REQUIRED -> "Permission Needed"
                            BtClassicStatus.BLUETOOTH_DISABLED -> "Bluetooth OFF"
                            BtClassicStatus.UNSUPPORTED -> "Unsupported"
                            BtClassicStatus.STANDBY -> "Tap to Enable"
                        },
                        statusBadge = if (btcActive) "SPP ON" else "OFF",
                        icon = Icons.Default.BluetoothConnected,
                        active = btcActive,
                        accentColor = TacticalCyan,
                        onClick = {
                            if (state.btClassicStatus == BtClassicStatus.PERMISSION_REQUIRED) {
                                radioPermissionLauncher.launch(radioPermissions)
                            } else {
                                onToggleBtClassic()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("radio_tile_bt_classic")
                    )

                    val wfdActive = state.wifiDirectEnabled && (
                        state.wifiDirectStatus == WifiP2pStatus.DISCOVERING ||
                            state.wifiDirectStatus == WifiP2pStatus.CONNECTED_GROUP_OWNER ||
                            state.wifiDirectStatus == WifiP2pStatus.CONNECTED_CLIENT
                        )
                    RadioModeTile(
                        title = "WI-FI DIRECT",
                        subtitle = when (state.wifiDirectStatus) {
                            WifiP2pStatus.CONNECTED_GROUP_OWNER -> "Group Owner Ready"
                            WifiP2pStatus.CONNECTED_CLIENT -> "Linked ${state.wifiDirectGroupOwnerIp ?: "P2P"}"
                            WifiP2pStatus.DISCOVERING -> "${state.wifiDirectPeers.size} P2P Peer(s)"
                            WifiP2pStatus.PERMISSION_REQUIRED -> "Tap to Grant"
                            WifiP2pStatus.WIFI_DISABLED -> "Enable Wi-Fi"
                            WifiP2pStatus.UNSUPPORTED -> "Unsupported"
                            WifiP2pStatus.STANDBY -> "Zero-Router P2P"
                        },
                        statusBadge = when (state.wifiDirectStatus) {
                            WifiP2pStatus.CONNECTED_GROUP_OWNER, WifiP2pStatus.CONNECTED_CLIENT -> "LINKED"
                            WifiP2pStatus.DISCOVERING -> "SCAN"
                            else -> "OFF"
                        },
                        icon = Icons.Default.WifiTethering,
                        active = wfdActive,
                        accentColor = ElectricAmber,
                        onClick = {
                            if (state.wifiDirectStatus == WifiP2pStatus.PERMISSION_REQUIRED) {
                                radioPermissionLauncher.launch(radioPermissions)
                            } else {
                                onToggleWifiDirect()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("radio_tile_wifi_direct")
                    )
                }

                // Wi-Fi Direct Quick Group / Peer Pairing Strip when Wi-Fi Direct is active
                if (state.wifiDirectEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElectricAmber.copy(alpha = 0.08f))
                            .border(1.dp, ElectricAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.wifiDirectPeers.isEmpty()) {
                                    "WI-FI DIRECT: SCANNING NEARBY DEVICES..."
                                } else {
                                    "WI-FI DIRECT PEERS: ${state.wifiDirectPeers.size} FOUND"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricAmber
                            )
                            Text(
                                text = state.wifiDirectPeers.firstOrNull()?.let {
                                    "Tap Connect on ${it.deviceName} or Host Group"
                                } ?: "Enable Wi-Fi Direct on 2nd device or tap Host Group",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val firstPeer = state.wifiDirectPeers.firstOrNull { !it.isConnected }
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    if (firstPeer != null) {
                                        onConnectWifiDirectPeer(firstPeer.deviceAddress)
                                    } else {
                                        onCreateWifiDirectGroup()
                                    }
                                },
                            color = ElectricAmber.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, ElectricAmber)
                        ) {
                            Text(
                                text = if (firstPeer != null) "CONNECT" else "HOST GROUP",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        // Reroute / No-Route Live Alert Banner
        if (state.rerouteAlertBanner != null) {
            val isNoRoute = state.activeRoute == null
            val bannerColor = if (isNoRoute) CrimsonPulse else ElectricAmber
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                accentColor = bannerColor,
                batterySaver = state.batterySaverEnabled
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = bannerColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.rerouteAlertBanner,
                        style = MaterialTheme.typography.labelSmall,
                        color = bannerColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Main Interactive Liquid Glass Radar Panel (Real Devices Only — Zero Fake Nodes)
        TacticalPanel(
            title = "REAL P2P RADAR // ${state.selfCallsign.uppercase()}",
            statusColor = when {
                state.hypRangeEnabled -> HypRangeViolet
                state.connectedPeerCount == 0 -> TacticalCyan
                state.isRouteRerouted -> ElectricAmber
                else -> PhosphorEmerald
            },
            badgeText = "${state.connectedPeerCount} REAL PEERS",
            batterySaver = state.batterySaverEnabled
        ) {
            // Quick Pin Drop Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PIN:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                PinCategory.entries.forEach { cat ->
                    val armed = state.armedPinCategory == cat
                    val color = pinColor(cat)
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("radar_arm_pin_${cat.name.lowercase()}")
                            .clickable { onArmPin(cat) },
                        color = if (armed) color.copy(alpha = 0.24f) else Color(0x880B1220),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (armed) color else PanelBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = pinIcon(cat),
                                contentDescription = cat.label,
                                tint = color,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (armed) "TAP MAP" else cat.shortCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (armed) color else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Liquid Glass Optical Radar Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .heightIn(max = 390.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xE6091220),
                                Color(0xF2050810)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.30f),
                                if (state.armedPinCategory != null) ElectricAmber else PhosphorEmerald.copy(alpha = 0.38f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                TacticalRadarCanvas(
                    state = state,
                    onDragNode = onDragNode,
                    onTapNode = onTapNode,
                    onDropPinAt = onDropPinAt,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Cupertino Glass Pill (Top-Left)
                LiquidGlassSurface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    cornerRadius = 12.dp,
                    accentColor = if (state.hypRangeEnabled) HypRangeViolet else TacticalCyan,
                    batterySaver = state.batterySaverEnabled
                ) {
                    Text(
                        text = "${state.selfNodeId}\n${if (state.hypRangeEnabled) "HYPRANGE" else "REAL RADAR"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }

                // Floating Cupertino Glass Telemetry Pill (Top-End)
                LiquidGlassSurface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    cornerRadius = 12.dp,
                    accentColor = when {
                        state.meshHopEfficiencyPct == 0 -> CrimsonPulse
                        state.isRouteRerouted -> ElectricAmber
                        else -> PhosphorEmerald
                    },
                    batterySaver = state.batterySaverEnabled
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "EFF: ${state.meshHopEfficiencyPct}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                state.meshHopEfficiencyPct == 0 -> CrimsonPulse
                                state.isRouteRerouted -> ElectricAmber
                                else -> PhosphorEmerald
                            }
                        )
                        Text(
                            text = if (state.activeRoute != null) {
                                "${state.activeRoute.hopCount} HOPS // ${state.activeRoute.totalLatencyMs}ms"
                            } else {
                                "${state.connectedPeerCount} PEERS ONLINE"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                // Honest empty-peer Liquid Glass notice when 0 other real devices are nearby
                if (state.nodes.size <= 1) {
                    LiquidGlassSurface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                            .fillMaxWidth(),
                        cornerRadius = 14.dp,
                        accentColor = TacticalCyan,
                        batterySaver = state.batterySaverEnabled
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "0 REAL PEERS ON RADAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricAmber,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Connect Phone & Tablet on Wi-Fi/Hotspot, BLE HypRange, BT Classic, or Wi-Fi Direct.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Selected Real Peer Inspector Pill
            val selectedNode = state.nodes.firstOrNull { it.id == state.selectedNodeId }
            if (selectedNode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x990C1422))
                        .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedNode.label} • ${selectedNode.transport} • ${if (selectedNode.isOnline) "ONLINE" else "MUTED"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedNode.isOnline) PhosphorEmerald else CrimsonPulse
                        )
                        Text(
                            text = buildString {
                                append("ID ${selectedNode.id}")
                                selectedNode.ipAddress?.let { append(" • IP $it:${selectedNode.tcpPort ?: 45873}") }
                                selectedNode.bleAddress?.let { append(" • BLE $it (${selectedNode.signalDbm}dBm)") }
                                selectedNode.btClassicAddress?.let { append(" • BT $it") }
                                selectedNode.wifiDirectAddress?.let { append(" • P2P $it") }
                                if (selectedNode.distanceMeters > 0f) {
                                    append(" • ~${selectedNode.distanceMeters.roundToInt()}m")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    if (selectedNode.role != NodeRole.SELF) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val wfdAddr = selectedNode.wifiDirectAddress
                            if (wfdAddr != null && !selectedNode.wifiDirectConnected) {
                                OutlinedButton(
                                    onClick = { onConnectWifiDirectPeer(wfdAddr) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, ElectricAmber),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "PAIR P2P",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricAmber
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { onToggleNodeOnline(selectedNode.id) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_selected_node_button"),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedNode.isOnline) CrimsonPulse else PhosphorEmerald
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = if (selectedNode.isOnline) CrimsonPulse else PhosphorEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedNode.isOnline) "MUTE" else "REVIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedNode.isOnline) CrimsonPulse else PhosphorEmerald
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { onTapNode(null) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss node selection",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Range Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.hypRangeEnabled) {
                        "HYPRANGE: ${(state.linkRangeKm * 1450).roundToInt()}m"
                    } else {
                        "RANGE: ${(state.linkRangeKm * 1000).roundToInt()}m"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.hypRangeEnabled) HypRangeViolet else TextSecondary,
                    modifier = Modifier.width(128.dp)
                )
                Slider(
                    value = state.linkRangeKm,
                    onValueChange = onUpdateRangeKm,
                    valueRange = 0.5f..4.5f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("link_range_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = if (state.hypRangeEnabled) HypRangeViolet else PhosphorEmerald,
                        activeTrackColor = if (state.hypRangeEnabled) HypRangeViolet else PhosphorEmerald,
                        inactiveTrackColor = PanelBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons: Rescan Multi-Radio Peers & Blackout Test
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (state.bleStatus == BleRadioStatus.PERMISSION_REQUIRED ||
                            state.wifiDirectStatus == WifiP2pStatus.PERMISSION_REQUIRED
                        ) {
                            radioPermissionLauncher.launch(radioPermissions)
                        }
                        onRescanPeers()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("rescan_peers_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.78f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = TacticalCyan.copy(alpha = 0.08f)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TacticalCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "RESCAN RADIOS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TacticalCyan,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onToggleBlackout,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("blackout_toggle_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (state.blackoutActive) ElectricAmber.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (state.blackoutActive) ElectricAmber else PanelBorder
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (state.blackoutActive) ElectricAmber else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (state.blackoutActive) "END BLACKOUT" else "SIM BLACKOUT",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.blackoutActive) ElectricAmber else TextPrimary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onBroadcastSos,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("broadcast_sos_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonPulse,
                    contentColor = TextPrimary
                ),
                contentPadding = PaddingValues(vertical = 13.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_broadcast_sos),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Phone <-> Tablet Direct Encrypted P2P Comms Panel
        TacticalPanel(
            title = "PHONE <-> TABLET P2P COMMS",
            statusColor = TacticalCyan,
            badgeText = "${state.p2pMessages.size} MSGS",
            batterySaver = state.batterySaverEnabled
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageDraft,
                    onValueChange = { messageDraft = it },
                    placeholder = {
                        Text(
                            text = "Send AES-128 message to paired device...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("p2p_message_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TacticalCyan,
                        unfocusedBorderColor = PanelBorder,
                        focusedContainerColor = Color(0x99060910),
                        unfocusedContainerColor = Color(0x99060910),
                        cursorColor = TacticalCyan
                    )
                )
                Button(
                    onClick = {
                        if (messageDraft.isNotBlank()) {
                            onSendP2pMessage(messageDraft)
                            messageDraft = ""
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("send_p2p_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TacticalCyan,
                        contentColor = ObsidianBg
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send encrypted P2P message",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (state.p2pMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x99060910))
                        .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.p2pMessages.take(8).forEach { msg ->
                        P2pMessageRow(msg)
                    }
                }
            }
        }

        // Live Terminal Packet Feed
        TacticalPanel(
            title = "LIVE MESH TERMINAL & REROUTE LOG",
            statusColor = if (state.isRouteRerouted) ElectricAmber else PhosphorEmerald,
            badgeText = "${state.terminalLogs.size} EVENTS",
            batterySaver = state.batterySaverEnabled
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xAA05070C))
                    .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
                    .padding(10.dp)
                    .testTag("terminal_log_list"),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(state.terminalLogs, key = { it.id }) { entry ->
                    TerminalLogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun RadioModeTile(
    title: String,
    subtitle: String,
    statusBadge: String,
    icon: ImageVector,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = if (active) {
                        listOf(accentColor.copy(alpha = 0.22f), Color(0xAA0B1320))
                    } else {
                        listOf(Color.White.copy(alpha = 0.06f), Color(0x99080E18))
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (active) {
                        listOf(Color.White.copy(alpha = 0.35f), accentColor.copy(alpha = 0.65f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.14f), PanelBorder)
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) accentColor else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (active) accentColor.copy(alpha = 0.6f) else PanelBorder,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = statusBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) accentColor else TextMuted,
                    fontSize = 9.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) accentColor.copy(alpha = 0.9f) else TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun P2pMessageRow(msg: P2pCommsMessage) {
    val accent = when {
        msg.isSos -> CrimsonPulse
        msg.isOutgoing -> TacticalCyan
        else -> PhosphorEmerald
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.09f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = msg.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "[${msg.transport}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (msg.encryptedVerified) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "AES-128 Verified",
                        tint = PhosphorEmerald,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = msg.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = msg.body,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary
        )
    }
}

@Composable
private fun TacticalRadarCanvas(
    state: MeshCommandUiState,
    onDragNode: (String, Float, Float) -> Unit,
    onTapNode: (String?) -> Unit,
    onDropPinAt: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val radarDesc = stringResource(R.string.cd_radar_canvas)

    val latestNodes by rememberUpdatedState(state.nodes)
    val latestArmedPin by rememberUpdatedState(state.armedPinCategory)

    val infiniteTransition = rememberInfiniteTransition(label = "radar_anim")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )
    val linkPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "link_phase"
    )

    val sweepColor = if (state.hypRangeEnabled) HypRangeViolet else PhosphorEmerald

    Canvas(
        modifier = modifier
            .semantics { contentDescription = radarDesc }
            .testTag("mesh_radar_canvas")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val halfW = size.width / 2f
                    val halfH = size.height / 2f
                    val radiusPx = min(halfW, halfH) * 0.90f
                    if (radiusPx <= 0f) return@detectTapGestures

                    val tapXKm = ((offset.x - halfW) / radiusPx) * MeshRouter.RADAR_RADIUS_KM
                    val tapYKm = ((offset.y - halfH) / radiusPx) * MeshRouter.RADAR_RADIUS_KM

                    if (latestArmedPin != null) {
                        onDropPinAt(tapXKm, tapYKm)
                        return@detectTapGestures
                    }

                    val hitThresholdPx = 28.dp.toPx()
                    var hitNode: MeshNode? = null
                    var minDist = Float.MAX_VALUE
                    for (node in latestNodes) {
                        val nx = halfW + (node.xKm / MeshRouter.RADAR_RADIUS_KM) * radiusPx
                        val ny = halfH + (node.yKm / MeshRouter.RADAR_RADIUS_KM) * radiusPx
                        val d = hypot(offset.x - nx, offset.y - ny)
                        if (d <= hitThresholdPx && d < minDist) {
                            minDist = d
                            hitNode = node
                        }
                    }
                    onTapNode(hitNode?.id)
                }
            }
            .pointerInput(Unit) {
                var draggedNodeId: String? = null
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val halfW = size.width / 2f
                        val halfH = size.height / 2f
                        val radiusPx = min(halfW, halfH) * 0.90f
                        if (radiusPx <= 0f) return@detectDragGestures

                        val hitThresholdPx = 32.dp.toPx()
                        var bestId: String? = null
                        var minDist = Float.MAX_VALUE
                        for (node in latestNodes) {
                            if (node.role == NodeRole.SELF) continue
                            val nx = halfW + (node.xKm / MeshRouter.RADAR_RADIUS_KM) * radiusPx
                            val ny = halfH + (node.yKm / MeshRouter.RADAR_RADIUS_KM) * radiusPx
                            val d = hypot(startOffset.x - nx, startOffset.y - ny)
                            if (d <= hitThresholdPx && d < minDist) {
                                minDist = d
                                bestId = node.id
                            }
                        }
                        draggedNodeId = bestId
                        if (bestId != null) onTapNode(bestId)
                    },
                    onDragEnd = { draggedNodeId = null },
                    onDragCancel = { draggedNodeId = null },
                    onDrag = { change, _ ->
                        val id = draggedNodeId ?: return@detectDragGestures
                        change.consume()
                        val halfW = size.width / 2f
                        val halfH = size.height / 2f
                        val radiusPx = min(halfW, halfH) * 0.90f
                        if (radiusPx <= 0f) return@detectDragGestures

                        val newXKm = ((change.position.x - halfW) / radiusPx) * MeshRouter.RADAR_RADIUS_KM
                        val newYKm = ((change.position.y - halfH) / radiusPx) * MeshRouter.RADAR_RADIUS_KM
                        onDragNode(id, newXKm, newYKm)
                    }
                )
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) * 0.45f
        val nodeMap = state.nodes.associateBy { it.id }

        fun toScreen(xKm: Float, yKm: Float): Offset {
            return Offset(
                x = center.x + (xKm / MeshRouter.RADAR_RADIUS_KM) * maxRadius,
                y = center.y + (yKm / MeshRouter.RADAR_RADIUS_KM) * maxRadius
            )
        }

        if (!state.batterySaverEnabled) {
            rotate(degrees = sweepAngle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.82f to Color.Transparent,
                        0.96f to sweepColor.copy(alpha = 0.08f),
                        1.0f to sweepColor.copy(alpha = 0.28f),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                    topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                    size = androidx.compose.ui.geometry.Size(maxRadius * 2f, maxRadius * 2f)
                )
            }
        }

        for (km in 1..5) {
            val r = maxRadius * (km / 5f)
            drawCircle(
                color = if (km == 5) PanelBorder.copy(alpha = 0.95f) else PanelBorder.copy(alpha = 0.55f),
                radius = r,
                center = center,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = if (km < 5) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
                )
            )
            val labelRes = textMeasurer.measure(
                text = "${km}K",
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 8.sp,
                    color = TextMuted
                )
            )
            drawText(
                textLayoutResult = labelRes,
                topLeft = Offset(center.x + 3.dp.toPx(), center.y - r + 2.dp.toPx())
            )
        }

        drawLine(
            color = PanelBorder.copy(alpha = 0.5f),
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = PanelBorder.copy(alpha = 0.5f),
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = 1.dp.toPx()
        )

        val dashEffect = if (state.batterySaverEnabled) {
            null
        } else {
            PathEffect.dashPathEffect(floatArrayOf(12f, 8f), phase = -linkPhase)
        }

        for (link in state.links) {
            val a = nodeMap[link.fromId] ?: continue
            val b = nodeMap[link.toId] ?: continue
            val p1 = toScreen(a.xKm, a.yKm)
            val p2 = toScreen(b.xKm, b.yKm)

            when {
                link.isRerouteHighlight -> {
                    if (!state.batterySaverEnabled) {
                        drawLine(
                            color = ElectricAmber.copy(alpha = 0.30f),
                            start = p1,
                            end = p2,
                            strokeWidth = 6.dp.toPx()
                        )
                    }
                    drawLine(
                        color = ElectricAmber,
                        start = p1,
                        end = p2,
                        strokeWidth = 2.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
                link.isActiveRoute -> {
                    if (!state.batterySaverEnabled) {
                        drawLine(
                            color = sweepColor.copy(alpha = 0.30f),
                            start = p1,
                            end = p2,
                            strokeWidth = 5.dp.toPx()
                        )
                    }
                    drawLine(
                        color = sweepColor,
                        start = p1,
                        end = p2,
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
                else -> {
                    drawLine(
                        color = sweepColor.copy(alpha = if (state.batterySaverEnabled) 0.16f else 0.28f),
                        start = p1,
                        end = p2,
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        for (pin in state.pins) {
            val pos = toScreen(pin.xKm, pin.yKm)
            val color = pinColor(pin.category)
            drawCircle(color = color.copy(alpha = 0.25f), radius = 11.dp.toPx(), center = pos)
            drawCircle(color = color, radius = 5.dp.toPx(), center = pos)
            val pinLabel = textMeasurer.measure(
                text = pin.category.shortCode,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            drawText(
                textLayoutResult = pinLabel,
                topLeft = Offset(pos.x + 7.dp.toPx(), pos.y - 6.dp.toPx())
            )
        }

        for (node in state.nodes) {
            val pos = toScreen(node.xKm, node.yKm)
            val isSelected = node.id == state.selectedNodeId
            val nodeColor = when {
                !node.isOnline -> CrimsonPulse
                node.role == NodeRole.SELF -> TacticalCyan
                node.isCodedPhy -> HypRangeViolet
                node.wifiDirectConnected -> ElectricAmber
                else -> PhosphorEmerald
            }

            if (isSelected) {
                drawCircle(
                    color = TextPrimary,
                    radius = 15.dp.toPx(),
                    center = pos,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            if (!node.isOnline) {
                drawCircle(color = CrimsonPulse.copy(alpha = 0.18f), radius = 10.dp.toPx(), center = pos)
                val xSize = 5.dp.toPx()
                drawLine(color = CrimsonPulse, start = Offset(pos.x - xSize, pos.y - xSize), end = Offset(pos.x + xSize, pos.y + xSize), strokeWidth = 2.dp.toPx())
                drawLine(color = CrimsonPulse, start = Offset(pos.x + xSize, pos.y - xSize), end = Offset(pos.x - xSize, pos.y + xSize), strokeWidth = 2.dp.toPx())
            } else {
                if (!state.batterySaverEnabled) {
                    drawCircle(color = nodeColor.copy(alpha = 0.24f), radius = 13.dp.toPx(), center = pos)
                }
                drawCircle(color = ObsidianBg, radius = 7.dp.toPx(), center = pos)
                drawCircle(color = nodeColor, radius = 5.5.dp.toPx(), center = pos)
            }

            val labelText = when (node.role) {
                NodeRole.SELF -> "YOU (${state.selfCallsign.take(10)})"
                NodeRole.GATEWAY -> "GW-RESCUE"
                NodeRole.REAL_PEER -> "${node.label.take(12)} [${node.transport}]"
            }
            val measured = textMeasurer.measure(
                text = labelText,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (node.isOnline) nodeColor else CrimsonPulse
                )
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(pos.x - measured.size.width / 2f, pos.y + 9.dp.toPx())
            )
        }

        val pulse = state.packetPulse
        if (pulse.isActive && pulse.currentSegmentIndex < pulse.path.lastIndex) {
            val fromNode = nodeMap[pulse.path[pulse.currentSegmentIndex]]
            val toNode = nodeMap[pulse.path[pulse.currentSegmentIndex + 1]]
            if (fromNode != null && toNode != null) {
                val p1 = toScreen(fromNode.xKm, fromNode.yKm)
                val p2 = toScreen(toNode.xKm, toNode.yKm)
                val px = p1.x + (p2.x - p1.x) * pulse.segmentProgress
                val py = p1.y + (p2.y - p1.y) * pulse.segmentProgress
                val pulsePos = Offset(px, py)
                val pulseColor = if (pulse.isRerouted) ElectricAmber else CrimsonPulse

                drawCircle(color = pulseColor.copy(alpha = 0.35f), radius = 14.dp.toPx(), center = pulsePos)
                drawCircle(color = TextPrimary, radius = 5.dp.toPx(), center = pulsePos)
            }
        }
    }
}

@Composable
private fun TerminalLogRow(entry: TerminalLogEntry) {
    val tagColor = when (entry.severity) {
        LogSeverity.INFO -> TacticalCyan
        LogSeverity.SUCCESS -> PhosphorEmerald
        LogSeverity.WARNING -> ElectricAmber
        LogSeverity.CRITICAL -> CrimsonPulse
        LogSeverity.REROUTE -> ElectricAmber
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.timestamp.take(8),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "[${entry.tag}]",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tagColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.severity == LogSeverity.CRITICAL || entry.severity == LogSeverity.REROUTE) {
                tagColor
            } else {
                TextPrimary
            }
        )
    }
}

fun pinColor(category: PinCategory): Color = when (category) {
    PinCategory.BLOCKED_ROAD -> CrimsonPulse
    PinCategory.MEDICAL_AID -> ElectricAmber
    PinCategory.SAFE_SHELTER -> PhosphorEmerald
    PinCategory.CLEAN_WATER -> TacticalCyan
}

fun pinIcon(category: PinCategory) = when (category) {
    PinCategory.BLOCKED_ROAD -> Icons.Default.ReportProblem
    PinCategory.MEDICAL_AID -> Icons.Default.LocalHospital
    PinCategory.SAFE_SHELTER -> Icons.Default.Home
    PinCategory.CLEAN_WATER -> Icons.Default.WaterDrop
}
