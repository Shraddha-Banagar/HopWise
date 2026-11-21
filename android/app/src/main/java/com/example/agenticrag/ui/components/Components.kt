package com.example.agenticrag.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticrag.data.TraceStep
import com.example.agenticrag.ui.theme.AppColors

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppColors.Lavender,
    borderColor: Color = AppColors.Indigo,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { content() },
    )
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val (label, background, foreground) = when (status) {
        "verified", "ok" -> Triple("VERIFIED", AppColors.Navy, AppColors.Cream)
        "corrected", "poisoned" -> Triple(status.uppercase(), AppColors.Indigo, AppColors.Cream)
        "warning" -> Triple("WARN", AppColors.Indigo, AppColors.Cream)
        "error" -> Triple("CONTRADICTION", AppColors.Navy, AppColors.Cream)
        "correction" -> Triple("ROLLBACK", AppColors.Indigo, AppColors.Cream)
        "abstain", "abstained" -> Triple("ABSTAINED", AppColors.Lavender, AppColors.Navy)
        "cache_hit" -> Triple("CACHED", AppColors.Lavender, AppColors.Navy)
        else -> Triple(status.uppercase(), AppColors.Lavender, AppColors.Navy)
    }
    Text(
        text = label,
        color = foreground,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun Accordion(
    title: String,
    subtitle: String = "",
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    SurfaceCard(backgroundColor = AppColors.Cream) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = AppColors.Indigo,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = { content() },
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(text = label.uppercase(), color = AppColors.Indigo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = value, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = AppColors.Navy)
    }
}

@Composable
fun TraceTimeline(steps: List<TraceStep>) {
    if (steps.isEmpty()) {
        Text("No trace steps recorded.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEach { step ->
            val badge = when (step.agent) {
                "planner" -> "P"
                "researcher" -> "R"
                "verifier" -> "V"
                else -> "S"
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                BoxBadge(text = badge)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Accordion(
                        title = step.title,
                        subtitle = "${step.agent.uppercase()}${if (step.hop != null) " · HOP ${step.hop}" else ""} · ${step.latencyMs.formatMs()}",
                    ) {
                        DetailRow("Thought", step.thought)
                        DetailRow("Action", step.action)
                        DetailRow("Observation", step.observation)
                        DetailRow("Verifier Audit", step.audit)
                        if (step.evidence.isNotEmpty()) {
                            Text(
                                text = "EVIDENCE",
                                color = AppColors.Indigo,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            step.evidence.forEach { item ->
                                Text(
                                    text = "${item.title} · score ${String.format("%.3f", item.score)}",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        StatusPill(status = step.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxBadge(text: String) {
    Text(
        text = text,
        color = AppColors.Cream,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(AppColors.Navy),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        lineHeight = 30.sp,
    )
}

private fun Double.formatMs(): String = "${String.format("%.1f", this)} ms"
