package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mesh.MeshEfficiencySample
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalPanel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TacticalPanel(
    title: String,
    modifier: Modifier = Modifier,
    statusColor: Color = PhosphorEmerald,
    badgeText: String? = null,
    batterySaver: Boolean = false,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val contentAlpha = if (batterySaver) 0.86f else 1f
    Surface(
        modifier = modifier.alpha(contentAlpha),
        color = GunmetalPanel,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PanelBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GunmetalElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    StatusLed(color = statusColor, dimmed = batterySaver)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(statusColor.copy(alpha = 0.16f))
                                .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (headerAction != null) {
                    headerAction()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PanelBorder)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun StatusLed(
    color: Color,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (dimmed) color.copy(alpha = 0.55f) else color)
    )
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    sublabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(GunmetalElevated)
            .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            color = accentColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EfficiencySparkline(
    samples: List<MeshEfficiencySample>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF06080C))
            .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        if (samples.isEmpty()) return@Canvas
        val slots = 12
        val barSpacing = 4.dp.toPx()
        val totalSpacing = barSpacing * (slots - 1)
        val barWidth = ((size.width - totalSpacing) / slots).coerceAtLeast(4f)
        val startOffset = slots - samples.size

        samples.forEachIndexed { idx, sample ->
            val slotIdx = startOffset + idx
            val left = slotIdx * (barWidth + barSpacing)
            val ratio = (sample.efficiencyPct / 100f).coerceIn(0.08f, 1f)
            val barHeight = size.height * ratio
            val top = size.height - barHeight
            val color = when {
                !sample.delivered -> CrimsonPulse
                sample.hopCount >= 4 -> ElectricAmber
                else -> PhosphorEmerald
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}
