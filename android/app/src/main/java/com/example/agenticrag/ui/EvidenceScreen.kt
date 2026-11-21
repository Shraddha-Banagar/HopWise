package com.example.agenticrag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticrag.data.Annotation
import com.example.agenticrag.data.Scenario
import com.example.agenticrag.ui.components.Accordion
import com.example.agenticrag.ui.components.DetailRow
import com.example.agenticrag.ui.components.StatusPill
import com.example.agenticrag.ui.components.SurfaceCard
import com.example.agenticrag.ui.components.TraceTimeline
import com.example.agenticrag.ui.theme.AppColors
import com.example.agenticrag.viewmodel.AgentViewModel

@Composable
fun EvidenceScreen(viewModel: AgentViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val selected = state.selectedScenario

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Cream)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        if (selected == null) {
            Text("Evidence & Traces", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Three annotated benchmark runs",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Indigo,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (state.scenarios.isEmpty()) {
                SurfaceCard { Text("Loading benchmark scenarios…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
            }
            state.scenarios.forEach { scenario ->
                SurfaceCard(
                    modifier = Modifier.clickable { viewModel.selectScenario(scenario) },
                    backgroundColor = AppColors.Lavender,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = scenario.title, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = scenario.subtitle, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "EXPECTED: ${scenario.expected}",
                            color = AppColors.Indigo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            Text(
                text = "BACK",
                color = AppColors.Indigo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.clearSelectedScenario() },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(selected.title, style = MaterialTheme.typography.headlineMedium)
            Text(text = selected.subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.Indigo)
            Spacer(modifier = Modifier.height(14.dp))

            SurfaceCard(backgroundColor = AppColors.Navy, borderColor = AppColors.Navy) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "BENCHMARK QUESTION", color = AppColors.Lavender, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = selected.question, color = AppColors.Cream, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "k=${selected.k} · hops=${selected.maxHops} · poison=${selected.simulateWrongFirstHop}",
                        color = AppColors.Lavender,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.runScenario(selected) },
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Indigo,
                    contentColor = AppColors.Cream,
                    disabledContainerColor = AppColors.Lavender,
                    disabledContentColor = AppColors.Navy,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isLoading) "Running…" else "Re-run Live")
            }
            Spacer(modifier = Modifier.height(14.dp))

            selected.annotations.forEach { annotation ->
                AnnotationCard(annotation)
                Spacer(modifier = Modifier.height(10.dp))
            }

            val response = state.response
            if (response != null && response.scenario == selected.id) {
                SurfaceCard(backgroundColor = AppColors.Navy, borderColor = AppColors.Navy) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LIVE RESULT", color = AppColors.Lavender, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = response.answer, color = AppColors.Cream, style = MaterialTheme.typography.bodyLarge)
                        }
                        StatusPill(status = response.status)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Accordion(
                    title = "Live Agent Execution Trace",
                    subtitle = "${response.steps.size} steps · ${response.metrics.retrievalCalls} retrievals",
                ) {
                    TraceTimeline(steps = response.steps)
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun AnnotationCard(annotation: Annotation) {
    val block = annotation.block
    val content = listOfNotNull(
        annotation.thought?.takeIf { it.isNotBlank() }?.let { "Thought" to it },
        annotation.observation?.takeIf { it.isNotBlank() }?.let { "Observation" to it },
        annotation.audit?.takeIf { it.isNotBlank() }?.let { "Verifier Audit" to it },
    )
    Accordion(title = block, subtitle = "${content.size} annotated block${if (content.size == 1) "" else "s"}") {
        content.forEach { (label, value) -> DetailRow(label = label, value = value) }
    }
}
