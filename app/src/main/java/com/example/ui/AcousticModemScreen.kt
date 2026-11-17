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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AcousticModemState
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AcousticModemScreen(
    state: AcousticModemState,
    batterySaver: Boolean,
    onMessageChange: (String) -> Unit,
    onHighBandToggle: (Boolean) -> Unit,
    onTransmitPulse: () -> Unit,
    onStopPulse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        "TRAPPED 2ND FLOOR NEED MEDIC",
        "NEED WATER & INSULIN SECTOR 4",
        "BRIDGE OUT REROUTE NORTH"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TacticalPanel(
            title = "ACOUSTIC FALLBACK MODEM // FSK",
            statusColor = if (state.isTransmitting) CrimsonPulse else TacticalCyan,
            badgeText = if (state.isTransmitting) "TX LIVE" else "STANDBY",
            batterySaver = batterySaver
        ) {
            Text(
                text = state.statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isTransmitting) CrimsonPulse else TacticalCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Band Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CarrierBandButton(
                    title = "AUDIBLE FSK",
                    subtitle = "1.2 / 2.2 kHz",
                    selected = !state.isHighBandUltrasonic,
                    accent = TacticalCyan,
                    onClick = { onHighBandToggle(false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("band_audible_button")
                )
                CarrierBandButton(
                    title = "HIGH-BAND",
                    subtitle = "6.8 / 8.2 kHz",
                    selected = state.isHighBandUltrasonic,
                    accent = ElectricAmber,
                    onClick = { onHighBandToggle(true) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("band_ultrasonic_button")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Emergency Text Input
            OutlinedTextField(
                value = state.message,
                onValueChange = onMessageChange,
                label = {
                    Text(
                        text = "EMERGENCY PAYLOAD (ASCII -> FSK)",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                singleLine = true,
                enabled = !state.isTransmitting,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TacticalCyan,
                    unfocusedBorderColor = PanelBorder,
                    focusedContainerColor = ObsidianBg,
                    unfocusedContainerColor = ObsidianBg,
                    focusedLabelColor = TacticalCyan,
                    unfocusedLabelColor = TextMuted
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("acoustic_message_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = !state.isTransmitting) {
                                onMessageChange(preset)
                            },
                        color = GunmetalElevated,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, PanelBorder)
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Reactive Waveform Visualizer
            Text(
                text = "REAL-TIME PCM FSK CARRIER WAVEFORM",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            WaveformCanvas(
                samples = state.waveformSamples,
                isTransmitting = state.isTransmitting,
                accentColor = if (state.isHighBandUltrasonic) ElectricAmber else TacticalCyan
            )

            if (state.isTransmitting) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CrimsonPulse,
                    trackColor = PanelBorder
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Transmit / Abort Button
            Button(
                onClick = {
                    if (state.isTransmitting) onStopPulse() else onTransmitPulse()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("transmit_audio_button"),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isTransmitting) CrimsonPulse else TacticalCyan,
                    contentColor = if (state.isTransmitting) TextPrimary else ObsidianBg
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = if (state.isTransmitting) Icons.Default.Stop else Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isTransmitting) {
                        stringResource(R.string.btn_abort_audio)
                    } else {
                        stringResource(R.string.btn_transmit_audio)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Binary Bit-Stream & Morse Code Readout
        TacticalPanel(
            title = "BIT-STREAM & MORSE ENCODER",
            statusColor = PhosphorEmerald,
            badgeText = "${state.message.length * 8} BITS",
            batterySaver = batterySaver
        ) {
            Text(
                text = "BINARY FSK BIT-STREAM (62.5 BPS)",
                style = MaterialTheme.typography.labelSmall,
                color = PhosphorEmerald
            )
            Spacer(modifier = Modifier.height(4.dp))

            val annotatedBits = buildAnnotatedString {
                var rawIdx = 0
                state.bitStream.forEach { ch ->
                    if (ch == ' ') {
                        append(' ')
                    } else {
                        val isActive = state.isTransmitting && rawIdx == state.activeBitIndex
                        val isSent = state.isTransmitting && rawIdx < state.activeBitIndex
                        withStyle(
                            SpanStyle(
                                color = when {
                                    isActive -> CrimsonPulse
                                    isSent -> PhosphorEmerald
                                    else -> TextSecondary
                                },
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                background = if (isActive) CrimsonPulse.copy(alpha = 0.25f) else Color.Transparent
                            )
                        ) {
                            append(ch)
                        }
                        rawIdx++
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF05070A))
                    .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = annotatedBits,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("acoustic_bitstream_text")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ITU MORSE FALLBACK SEQUENCE",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricAmber
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF05070A))
                    .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = state.morseCode.ifEmpty { "NO DATA" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricAmber,
                    modifier = Modifier.testTag("acoustic_morse_text")
                )
            }
        }
    }
}

@Composable
private fun CarrierBandButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        color = if (selected) accent.copy(alpha = 0.16f) else GunmetalElevated,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (selected) accent else PanelBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = if (selected) accent else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) accent else TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun WaveformCanvas(
    samples: List<Float>,
    isTransmitting: Boolean,
    accentColor: Color
) {
    val desc = stringResource(R.string.cd_waveform_canvas)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF05070A))
            .border(1.dp, if (isTransmitting) accentColor else PanelBorder, RoundedCornerShape(4.dp))
            .semantics { contentDescription = desc }
            .testTag("waveform_visualizer")
    ) {
        val midY = size.height / 2f
        // Center zero-crossing reference line
        drawLine(
            color = PanelBorder,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.dp.toPx()
        )

        if (samples.isEmpty()) return@Canvas
        val path = Path()
        val stepX = size.width / (samples.size - 1).coerceAtLeast(1)

        samples.forEachIndexed { idx, sample ->
            val x = idx * stepX
            val amp = if (isTransmitting) sample else 0f
            val y = midY - (amp * (size.height * 0.42f))
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = if (isTransmitting) accentColor else TextMuted,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
