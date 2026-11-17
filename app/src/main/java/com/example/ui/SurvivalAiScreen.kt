package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.viewmodel.AiTriageUiState
import com.example.viewmodel.TriageExchange

@Composable
fun SurvivalAiScreen(
    aiState: AiTriageUiState,
    history: List<TriageExchange>,
    batterySaverEnabled: Boolean,
    onQueryAi: (String) -> Unit,
    onClearSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customQuery by rememberSaveable { mutableStateOf("") }

    val triageChips = listOf(
        "Severe Bleeding" to "Protocol for severe arterial bleeding and improvised tourniquet application",
        "Trapped Under Rubble" to "Survival protocol when trapped under earthquake rubble with crush risk and dust",
        "No Clean Water" to "Field water purification protocol without boiling equipment or commercial filters",
        "Hypothermia" to "Field triage protocol for severe hypothermia and cold shock stabilization"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TacticalPanel(
            title = "AETHER-MEDIC // SURVIVAL & TRIAGE AI",
            statusColor = PhosphorEmerald,
            badgeText = "GEMINI 3.8 FLASH",
            batterySaver = batterySaverEnabled,
            headerAction = if (history.isNotEmpty() || aiState !is AiTriageUiState.Idle) {
                {
                    Box(
                        modifier = Modifier
                            .testTag("ai_clear_button")
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(onClick = onClearSession)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Reset AI triage session",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else null,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "QUICK-TAP EMERGENCY TRIAGE PROTOCOLS",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                triageChips.forEachIndexed { idx, (label, prompt) ->
                    Box(
                        modifier = Modifier
                            .testTag("triage_chip_$idx")
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, CrimsonPulse.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .background(CrimsonPulse.copy(alpha = 0.12f))
                            .clickable { onQueryAi(prompt) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = CrimsonPulse,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Output / History Viewport
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (aiState) {
                    is AiTriageUiState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PhosphorEmerald
                            )
                            Text(
                                text = "SYNTHESIZING FIELD PROTOCOL...",
                                style = MaterialTheme.typography.labelMedium,
                                color = PhosphorEmerald
                            )
                        }
                    }
                    is AiTriageUiState.Streaming -> {
                        TriageProtocolCard(
                            query = aiState.query,
                            response = aiState.text,
                            timestamp = "LIVE",
                            isStreaming = true,
                            isError = false
                        )
                    }
                    is AiTriageUiState.Error -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CrimsonPulse, RoundedCornerShape(4.dp))
                                .background(CrimsonPulse.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = CrimsonPulse,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = aiState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CrimsonPulse
                                )
                            }
                            OutlinedButton(
                                onClick = { onQueryAi(aiState.query) },
                                shape = RoundedCornerShape(3.dp),
                                border = BorderStroke(1.dp, CrimsonPulse),
                                colors = ButtonDefaults.outlinedButtonColors(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "RETRY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrimsonPulse
                                )
                            }
                        }
                    }
                    is AiTriageUiState.Idle, is AiTriageUiState.Success -> Unit
                }

                if (history.isEmpty() && aiState is AiTriageUiState.Idle) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = PhosphorEmerald.copy(alpha = 0.65f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AETHER-MEDIC TRIAGE READY",
                            style = MaterialTheme.typography.labelMedium,
                            color = PhosphorEmerald
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap an emergency protocol chip above or enter a field trauma/survival situation below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                } else {
                    history.forEach { item ->
                        TriageProtocolCard(
                            query = item.query,
                            response = item.response,
                            timestamp = item.timestamp,
                            isStreaming = false,
                            isError = item.isError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = customQuery,
                    onValueChange = { customQuery = it },
                    placeholder = {
                        Text(
                            text = "Enter trauma, shelter, or survival query...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_query_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PhosphorEmerald,
                        unfocusedBorderColor = PanelBorder,
                        focusedContainerColor = ObsidianBg,
                        unfocusedContainerColor = ObsidianBg
                    )
                )

                IconButton(
                    onClick = {
                        val q = customQuery.trim()
                        if (q.isNotEmpty()) {
                            onQueryAi(q)
                            customQuery = ""
                        }
                    },
                    modifier = Modifier
                        .testTag("ai_send_button")
                        .size(52.dp)
                        .border(1.dp, PhosphorEmerald, RoundedCornerShape(4.dp))
                        .background(PhosphorEmerald.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send survival triage query",
                        tint = PhosphorEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun TriageProtocolCard(
    query: String,
    response: String,
    timestamp: String,
    isStreaming: Boolean,
    isError: Boolean
) {
    val accent = when {
        isError -> CrimsonPulse
        isStreaming -> ElectricAmber
        else -> PhosphorEmerald
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROTOCOL // $timestamp",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            if (isStreaming) {
                Text(
                    text = "STREAMING...",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricAmber
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "> $query",
            style = MaterialTheme.typography.labelSmall,
            color = TacticalCyan
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = response,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = if (isError) CrimsonPulse else TextPrimary
        )
    }
}
