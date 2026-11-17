package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.mesh.PinCategory
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.HypRangeViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AetherMeshViewModel
import com.example.viewmodel.MeshCommandUiState
import kotlin.random.Random

private data class HudTab(
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String
)

private val HUD_TABS = listOf(
    HudTab(R.string.tab_radar, Icons.Default.Radar, "nav_tab_radar"),
    HudTab(R.string.tab_telemetry, Icons.Default.Security, "nav_tab_telemetry"),
    HudTab(R.string.tab_acoustic, Icons.Default.GraphicEq, "nav_tab_acoustic"),
    HudTab(R.string.tab_ai, Icons.Default.MedicalServices, "nav_tab_ai")
)

@Composable
fun AetherMeshApp(
    viewModel: AetherMeshViewModel = viewModel()
) {
    val meshState by viewModel.meshState.collectAsStateWithLifecycle()
    val acousticState by viewModel.acousticState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val triageHistory by viewModel.triageHistory.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var desktopCenterMode by rememberSaveable { mutableIntStateOf(0) }

    val dropPinNearSelf: (PinCategory) -> Unit = { cat ->
        val angle = Random.nextDouble(0.0, Math.PI * 2.0)
        val dist = Random.nextDouble(0.35, 1.15)
        viewModel.dropPinAt(
            xKm = (Math.cos(angle) * dist).toFloat(),
            yKm = (Math.sin(angle) * dist).toFloat(),
            categoryOverride = cat
        )
    }

    val batteryAlpha = if (meshState.batterySaverEnabled) 0.88f else 1f
    val radioPermissions = viewModel.getRequiredRadioPermissions()

    ProvideLiquidGlassClock(batterySaver = meshState.batterySaverEnabled) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .alpha(batteryAlpha)
        ) {
            // Dynamic volumetric light field refracted by all Liquid Glass surfaces
            LiquidAmbientBackground(batterySaver = meshState.batterySaverEnabled)

            val isWideCommandCenter = maxWidth >= 900.dp

            if (isWideCommandCenter) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopCommandHeader(
                            meshState = meshState,
                            onToggleBatterySaver = viewModel::toggleBatterySaver,
                            modifier = Modifier
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        TelemetryAndPinsScreen(
                            state = meshState,
                            onToggleBatterySaver = viewModel::toggleBatterySaver,
                            onArmPin = viewModel::armPinCategory,
                            onDropPinNearSelf = dropPinNearSelf,
                            onClearPins = viewModel::clearPins,
                            onToggleWrongKeyTest = viewModel::toggleSimulateWrongKey,
                            onNavigateToRadarForPin = { desktopCenterMode = 0 },
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight()
                        )

                        Column(
                            modifier = Modifier
                                .weight(1.35f)
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CenterStageChip(
                                    text = "MESH TOPOLOGY RADAR",
                                    selected = desktopCenterMode == 0,
                                    onClick = { desktopCenterMode = 0 },
                                    modifier = Modifier.weight(1f)
                                )
                                CenterStageChip(
                                    text = "ACOUSTIC FSK MODEM",
                                    selected = desktopCenterMode == 1,
                                    onClick = { desktopCenterMode = 1 },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (desktopCenterMode == 0) {
                                MeshRadarScreen(
                                    state = meshState,
                                    radioPermissions = radioPermissions,
                                    onRadioPermissionsResult = viewModel::onRadioPermissionsResult,
                                    onToggleHypRange = { viewModel.toggleHypRange() },
                                    onToggleBtClassic = { viewModel.toggleBtClassic() },
                                    onToggleWifiDirect = { viewModel.toggleWifiDirect() },
                                    onCreateWifiDirectGroup = viewModel::createWifiDirectGroup,
                                    onConnectWifiDirectPeer = viewModel::connectWifiDirectPeer,
                                    onDragNode = viewModel::onNodeDrag,
                                    onTapNode = viewModel::selectNode,
                                    onToggleNodeOnline = viewModel::toggleNodeOnline,
                                    onRescanPeers = viewModel::rescanPeers,
                                    onToggleBlackout = viewModel::toggleDisasterBlackout,
                                    onBroadcastSos = viewModel::broadcastSosPacket,
                                    onSendP2pMessage = viewModel::sendP2pMessage,
                                    onUpdateRangeKm = viewModel::updateLinkRange,
                                    onArmPin = viewModel::armPinCategory,
                                    onDropPinAt = { x, y -> viewModel.dropPinAt(x, y) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                AcousticModemScreen(
                                    state = acousticState,
                                    batterySaver = meshState.batterySaverEnabled,
                                    onMessageChange = viewModel::updateAcousticMessage,
                                    onHighBandToggle = viewModel::setAcousticHighBand,
                                    onTransmitPulse = viewModel::transmitAudioPulse,
                                    onStopPulse = viewModel::stopAudioPulse,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        SurvivalAiScreen(
                            aiState = aiState,
                            history = triageHistory,
                            batterySaverEnabled = meshState.batterySaverEnabled,
                            onQueryAi = viewModel::querySurvivalAi,
                            onClearSession = viewModel::clearAiSession,
                            modifier = Modifier
                                .weight(1.0f)
                                .fillMaxHeight()
                        )
                    }
                }
            } else {
                BackHandler(enabled = selectedTab != 0) {
                    selectedTab = 0
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopCommandHeader(
                            meshState = meshState,
                            onToggleBatterySaver = viewModel::toggleBatterySaver,
                            modifier = Modifier
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    },
                    bottomBar = {
                        LiquidGlassNavigationDock(
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it },
                            batterySaver = meshState.batterySaverEnabled,
                            accentColor = if (meshState.hypRangeEnabled) HypRangeViolet else PhosphorEmerald
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> MeshRadarScreen(
                                state = meshState,
                                radioPermissions = radioPermissions,
                                onRadioPermissionsResult = viewModel::onRadioPermissionsResult,
                                onToggleHypRange = { viewModel.toggleHypRange() },
                                onToggleBtClassic = { viewModel.toggleBtClassic() },
                                onToggleWifiDirect = { viewModel.toggleWifiDirect() },
                                onCreateWifiDirectGroup = viewModel::createWifiDirectGroup,
                                onConnectWifiDirectPeer = viewModel::connectWifiDirectPeer,
                                onDragNode = viewModel::onNodeDrag,
                                onTapNode = viewModel::selectNode,
                                onToggleNodeOnline = viewModel::toggleNodeOnline,
                                onRescanPeers = viewModel::rescanPeers,
                                onToggleBlackout = viewModel::toggleDisasterBlackout,
                                onBroadcastSos = viewModel::broadcastSosPacket,
                                onSendP2pMessage = viewModel::sendP2pMessage,
                                onUpdateRangeKm = viewModel::updateLinkRange,
                                onArmPin = viewModel::armPinCategory,
                                onDropPinAt = { x, y -> viewModel.dropPinAt(x, y) }
                            )
                            1 -> TelemetryAndPinsScreen(
                                state = meshState,
                                onToggleBatterySaver = viewModel::toggleBatterySaver,
                                onArmPin = viewModel::armPinCategory,
                                onDropPinNearSelf = dropPinNearSelf,
                                onClearPins = viewModel::clearPins,
                                onToggleWrongKeyTest = viewModel::toggleSimulateWrongKey,
                                onNavigateToRadarForPin = { selectedTab = 0 }
                            )
                            2 -> AcousticModemScreen(
                                state = acousticState,
                                batterySaver = meshState.batterySaverEnabled,
                                onMessageChange = viewModel::updateAcousticMessage,
                                onHighBandToggle = viewModel::setAcousticHighBand,
                                onTransmitPulse = viewModel::transmitAudioPulse,
                                onStopPulse = viewModel::stopAudioPulse
                            )
                            3 -> SurvivalAiScreen(
                                aiState = aiState,
                                history = triageHistory,
                                batterySaverEnabled = meshState.batterySaverEnabled,
                                onQueryAi = viewModel::querySurvivalAi,
                                onClearSession = viewModel::clearAiSession
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopCommandHeader(
    meshState: MeshCommandUiState,
    onToggleBatterySaver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val headerAccent = when {
        meshState.hypRangeEnabled -> HypRangeViolet
        meshState.isRouteRerouted -> ElectricAmber
        meshState.connectedPeerCount > 0 -> PhosphorEmerald
        else -> TacticalCyan
    }

    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        accentColor = headerAccent,
        batterySaver = meshState.batterySaverEnabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusLed(
                        color = when {
                            meshState.connectedPeerCount == 0 -> TacticalCyan
                            meshState.isRouteRerouted -> ElectricAmber
                            else -> PhosphorEmerald
                        },
                        dimmed = meshState.batterySaverEnabled
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AETHER-MESH",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, TacticalCyan.copy(alpha = 0.48f), RoundedCornerShape(50))
                            .background(TacticalCyan.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "AES-128 encrypted",
                                tint = TacticalCyan,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "AES-128",
                                style = MaterialTheme.typography.labelSmall,
                                color = TacticalCyan
                            )
                        }
                    }
                    if (meshState.hypRangeEnabled) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, HypRangeViolet.copy(alpha = 0.6f), RoundedCornerShape(50))
                                .background(HypRangeViolet.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HYPRANGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = HypRangeViolet
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "PEERS:${meshState.connectedPeerCount} • EFF:${meshState.meshHopEfficiencyPct}% • ${if (meshState.blackoutActive) "BLACKOUT REROUTE" else "LIQUID GLASS HUD"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (meshState.blackoutActive) ElectricAmber else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("header_battery_saver_button")
                    .clickable(onClick = onToggleBatterySaver),
                color = if (meshState.batterySaverEnabled) ElectricAmber.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    1.dp,
                    if (meshState.batterySaverEnabled) ElectricAmber else Color.White.copy(alpha = 0.22f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatterySaver,
                        contentDescription = stringResource(R.string.cd_battery_saver),
                        tint = if (meshState.batterySaverEnabled) ElectricAmber else PhosphorEmerald,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (meshState.batterySaverEnabled) "ECO" else "PWR",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (meshState.batterySaverEnabled) ElectricAmber else PhosphorEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassNavigationDock(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    batterySaver: Boolean,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 28.dp,
            accentColor = accentColor,
            batterySaver = batterySaver
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HUD_TABS.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    val label = stringResource(tab.labelRes)
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) accentColor else TextMuted,
                        animationSpec = spring(),
                        label = "dock_icon_color"
                    )
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) accentColor.copy(alpha = 0.20f) else Color.Transparent,
                        animationSpec = spring(),
                        label = "dock_pill_color"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(pillColor)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        1.dp,
                                        Brush.linearGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.35f),
                                                accentColor.copy(alpha = 0.55f)
                                            )
                                        ),
                                        RoundedCornerShape(22.dp)
                                    )
                                } else Modifier
                            )
                            .testTag(tab.tag)
                            .clickable { onSelectTab(index) }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = label,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterStageChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        color = if (selected) PhosphorEmerald.copy(alpha = 0.18f) else Color(0x88101827),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (selected) PhosphorEmerald else PanelBorder)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 9.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) PhosphorEmerald else TextSecondary
            )
        }
    }
}
