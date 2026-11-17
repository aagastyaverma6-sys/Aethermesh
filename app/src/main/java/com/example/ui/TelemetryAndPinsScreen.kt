package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mesh.PinCategory
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MeshCommandUiState
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun TelemetryAndPinsScreen(
    state: MeshCommandUiState,
    onToggleBatterySaver: () -> Unit,
    onArmPin: (PinCategory?) -> Unit,
    onDropPinNearSelf: (PinCategory) -> Unit,
    onClearPins: () -> Unit,
    onToggleWrongKeyTest: () -> Unit,
    onNavigateToRadarForPin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Hardware & Mesh Telemetry
        TacticalPanel(
            title = "HARDWARE & MESH TELEMETRY",
            statusColor = PhosphorEmerald,
            badgeText = if (state.batterySaverEnabled) "ECO POWER" else "NOMINAL",
            batterySaver = state.batterySaverEnabled
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val effColor = when {
                    state.meshHopEfficiencyPct >= 75 -> PhosphorEmerald
                    state.meshHopEfficiencyPct >= 40 -> ElectricAmber
                    else -> CrimsonPulse
                }
                MetricTile(
                    label = "HOP EFFICIENCY",
                    value = "${state.meshHopEfficiencyPct}%",
                    sublabel = state.activeRoute?.let { "${it.hopCount} HOPS / ${it.totalLatencyMs}ms" } ?: "NO ROUTE",
                    accentColor = effColor,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "ACTIVE PEERS",
                    value = "${state.connectedPeerCount}/${(state.nodes.size - 1).coerceAtLeast(1)}",
                    sublabel = "${state.onlineNodeCount} NODES ONLINE",
                    accentColor = TacticalCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Mesh Hop Efficiency Sparkline
            Text(
                text = "HOP EFFICIENCY & LATENCY TELEMETRY",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            EfficiencySparklineCanvas(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                    .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
                    .padding(6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Battery Saver Mode Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (state.batterySaverEnabled) ElectricAmber.copy(alpha = 0.7f) else PanelBorder,
                        RoundedCornerShape(4.dp)
                    )
                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatterySaver,
                        contentDescription = null,
                        tint = if (state.batterySaverEnabled) ElectricAmber else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "BATTERY SAVER MODE",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.batterySaverEnabled) ElectricAmber else TextPrimary
                        )
                        Text(
                            text = "Dims radar sweep & throttles HUD glow",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
                Switch(
                    checked = state.batterySaverEnabled,
                    onCheckedChange = { onToggleBatterySaver() },
                    modifier = Modifier
                        .testTag("battery_saver_switch")
                        .semantics { contentDescription = "Toggle battery saver mode" },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBg,
                        checkedTrackColor = ElectricAmber,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = PanelBorder
                    )
                )
            }
        }

        // 2. Tactical Crowd-Pin System
        TacticalPanel(
            title = "TACTICAL CROWD-PIN SYSTEM",
            statusColor = ElectricAmber,
            badgeText = "${state.pins.size} PINS",
            batterySaver = state.batterySaverEnabled,
            headerAction = if (state.pins.isNotEmpty()) {
                {
                    Box(
                        modifier = Modifier
                            .testTag("clear_pins_button")
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(onClick = onClearPins)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear tactical pins",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else null
        ) {
            Text(
                text = "Tap a hazard to arm radar placement, or broadcast immediately near NODE-00:",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PinCategory.entries.chunked(2).forEach { rowCats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowCats.forEach { cat ->
                            val armed = state.armedPinCategory == cat
                            val color = pinColor(cat)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        1.dp,
                                        if (armed) color else PanelBorder,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .background(
                                        if (armed) color.copy(alpha = 0.16f) else ObsidianBg,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pin_arm_${cat.name.lowercase()}")
                                        .clickable {
                                            onArmPin(cat)
                                            onNavigateToRadarForPin()
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = cat.label.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { onDropPinNearSelf(cat) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pin_quick_drop_${cat.name.lowercase()}")
                                        .minimumInteractiveComponentSize(),
                                    shape = RoundedCornerShape(3.dp),
                                    border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "DROP + SYNC",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.pins.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
                        .background(ObsidianBg, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.pins.take(5).forEach { pin ->
                        val c = pinColor(pin.category)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "[${pin.category.shortCode}] (${String.format(Locale.US, "%+.1f", pin.xKm)}, ${String.format(Locale.US, "%+.1f", pin.yKm)})km",
                                style = MaterialTheme.typography.bodySmall,
                                color = c
                            )
                            Text(
                                text = "${pin.propagatedCount} PEERS • ${pin.timestamp}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // 3. AES-128 Encrypted Hex Packet Inspector
        val inspection = state.latestPacketInspection
        TacticalPanel(
            title = "AES-128 ENCRYPTED HEX PACKET INSPECTOR",
            statusColor = if (inspection?.decryptionVerified == false) CrimsonPulse else TacticalCyan,
            badgeText = inspection?.packetType ?: "STANDBY",
            batterySaver = state.batterySaverEnabled
        ) {
            if (inspection == null) {
                Text(
                    text = "No packets inspected yet. Broadcast SOS or drop a tactical pin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${inspection.packetId} • ${inspection.sourceNode} -> ${inspection.targetNode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = inspection.keyFingerprint,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    OutlinedButton(
                        onClick = onToggleWrongKeyTest,
                        modifier = Modifier
                            .testTag("toggle_wrong_key_button")
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(
                            1.dp,
                            if (state.simulateWrongKey) CrimsonPulse else TacticalCyan.copy(alpha = 0.6f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (state.simulateWrongKey) CrimsonPulse.copy(alpha = 0.16f) else ObsidianBg
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = if (state.simulateWrongKey) CrimsonPulse else TacticalCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.simulateWrongKey) "RESTORE KEY" else "TEST TAMPER",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.simulateWrongKey) CrimsonPulse else TacticalCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Verification Status Banner
                val statusColor = if (inspection.decryptionVerified) PhosphorEmerald else CrimsonPulse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (inspection.decryptionVerified) Icons.Default.LockOpen else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = inspection.statusDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = ElectricAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "RAW ENCRYPTED HEX PAYLOAD (IV + CIPHERTEXT)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricAmber
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
                        .background(ObsidianBg, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = inspection.rawCiphertextHex,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = ElectricAmber
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (inspection.decryptionVerified) {
                        "DECRYPTED JSON PAYLOAD (RECEIVER NODE)"
                    } else {
                        "DECRYPTION OUTPUT (ERROR)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inspection.decryptionVerified) PhosphorEmerald else CrimsonPulse
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
                        .background(ObsidianBg, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = inspection.decryptedJson,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = if (inspection.decryptionVerified) PhosphorEmerald else CrimsonPulse
                    )
                }
            }
        }
    }
}

@Composable
private fun EfficiencySparklineCanvas(
    state: MeshCommandUiState,
    modifier: Modifier = Modifier
) {
    val samples = state.efficiencyHistory
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val count = samples.size

        // Reference 50% and 80% horizontal grid lines
        drawLine(
            color = PanelBorder,
            start = Offset(0f, h * 0.5f),
            end = Offset(w, h * 0.5f),
            strokeWidth = 1.dp.toPx()
        )

        if (count == 1) {
            val y = h * (1f - (samples[0].efficiencyPct / 100f).coerceIn(0f, 1f))
            drawCircle(
                color = if (samples[0].delivered) PhosphorEmerald else CrimsonPulse,
                radius = 4.dp.toPx(),
                center = Offset(w / 2f, y)
            )
            return@Canvas
        }

        val path = Path()
        val stepX = w / (count - 1).coerceAtLeast(1)
        samples.forEachIndexed { idx, sample ->
            val x = idx * stepX
            val y = h * (1f - (sample.efficiencyPct / 100f).coerceIn(0f, 1f))
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = TacticalCyan,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        samples.forEachIndexed { idx, sample ->
            val x = idx * stepX
            val y = h * (1f - (sample.efficiencyPct / 100f).coerceIn(0f, 1f))
            val dotColor = when {
                !sample.delivered -> CrimsonPulse
                sample.efficiencyPct >= 75 -> PhosphorEmerald
                else -> ElectricAmber
            }
            drawCircle(color = dotColor, radius = 3.5.dp.toPx(), center = Offset(x, y))
        }
    }
}
