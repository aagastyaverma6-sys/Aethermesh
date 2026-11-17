package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalPanel
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
    var desktopCenterMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Radar, 1 = Acoustic Modem

    val dropPinNearSelf: (PinCategory) -> Unit = { cat ->
        val angle = Random.nextDouble(0.0, Math.PI * 2.0)
        val dist = Random.nextDouble(0.35, 1.15)
        viewModel.dropPinAt(
            xKm = (Math.cos(angle) * dist).toFloat(),
            yKm = (Math.sin(angle) * dist).toFloat(),
            categoryOverride = cat
        )
    }

    val batteryAlpha = if (meshState.batterySaverEnabled) 0.86f else 1f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .alpha(batteryAlpha)
    ) {
        val isWideCommandCenter = maxWidth >= 900.dp

        if (isWideCommandCenter) {
            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopCommandHeader(
                        meshState = meshState,
                        onToggleBatterySaver = viewModel::toggleBatterySaver,
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        )
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
                                blePermissions = viewModel.getRequiredBlePermissions(),
                                onBlePermissionsResult = viewModel::onBlePermissionsResult,
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
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopCommandHeader(
                        meshState = meshState,
                        onToggleBatterySaver = viewModel::toggleBatterySaver,
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = GunmetalPanel,
                        contentColor = TextPrimary,
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PanelBorder)
                    ) {
                        HUD_TABS.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTab = index },
                                modifier = Modifier.testTag(tab.tag),
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PhosphorEmerald,
                                    selectedTextColor = PhosphorEmerald,
                                    indicatorColor = PhosphorEmerald.copy(alpha = 0.16f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
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
                            blePermissions = viewModel.getRequiredBlePermissions(),
                            onBlePermissionsResult = viewModel::onBlePermissionsResult,
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

@Composable
private fun TopCommandHeader(
    meshState: MeshCommandUiState,
    onToggleBatterySaver: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GunmetalPanel)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusLed(
                        color = when {
                            meshState.activeRoute == null -> CrimsonPulse
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, TacticalCyan.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            .background(TacticalCyan.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "AES-128 encrypted",
                                tint = TacticalCyan,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "AES128",
                                style = MaterialTheme.typography.labelSmall,
                                color = TacticalCyan
                            )
                        }
                    }
                }
                Text(
                    text = "PEERS:${meshState.connectedPeerCount} • EFF:${meshState.meshHopEfficiencyPct}% • ${if (meshState.blackoutActive) "BLACKOUT REROUTE" else "NOMINAL"}",
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
                color = if (meshState.batterySaverEnabled) ElectricAmber.copy(alpha = 0.2f) else GunmetalElevated,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(
                    1.dp,
                    if (meshState.batterySaverEnabled) ElectricAmber else PanelBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatterySaver,
                        contentDescription = stringResource(R.string.cd_battery_saver),
                        tint = if (meshState.batterySaverEnabled) ElectricAmber else PhosphorEmerald,
                        modifier = Modifier.size(16.dp)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PanelBorder)
        )
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
        color = if (selected) PhosphorEmerald.copy(alpha = 0.16f) else GunmetalPanel,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (selected) PhosphorEmerald else PanelBorder)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) PhosphorEmerald else TextSecondary
            )
        }
    }
}
