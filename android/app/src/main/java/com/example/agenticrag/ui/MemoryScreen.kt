package com.example.agenticrag.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticrag.ui.components.Accordion
import com.example.agenticrag.ui.components.DetailRow
import com.example.agenticrag.ui.components.SurfaceCard
import com.example.agenticrag.ui.theme.AppColors
import com.example.agenticrag.viewmodel.AgentViewModel

@Composable
fun MemoryScreen(viewModel: AgentViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Cream)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("Memory & Corpus", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Verified session facts and the active vector index",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Indigo,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SESSION MEMORY · ${state.memory.facts.size} facts",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = viewModel::clearMemory,
                enabled = state.memory.facts.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Indigo,
                    contentColor = AppColors.Cream,
                    disabledContainerColor = AppColors.Lavender,
                    disabledContentColor = AppColors.Navy,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Clear Memory", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (state.memory.facts.isEmpty()) {
            SurfaceCard { Text("No verified facts cached yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
        }
        state.memory.facts.forEach { fact ->
            SurfaceCard(backgroundColor = AppColors.Lavender) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${fact.subject} · ${fact.predicate.replace('_', ' ')}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(text = fact.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "source: ${fact.sourceTitle} · confidence ${String.format("%.2f", fact.confidence)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))
        Accordion(
            title = "k-comparison metrics",
            subtitle = "latency, top score, and chunk overlap from the latest query",
        ) {
            val comparison = state.response?.metrics?.kComparison.orEmpty()
            if (comparison.isEmpty()) {
                Text("Run a query to compare k=1, 3, and 5.", style = MaterialTheme.typography.bodyMedium)
            }
            comparison.forEach { metric ->
                SurfaceCard(backgroundColor = AppColors.Lavender) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("k=${metric.k}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "latency ${String.format("%.2f", metric.latencyMs)} ms · top score " +
                                "${String.format("%.3f", metric.topScore)} · overlap ${String.format("%.2f", metric.chunkOverlap)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        Accordion(
            title = "Indexed ChromaDB documents",
            subtitle = "${state.corpus.documentCount} documents · ${state.corpus.chunkCount} chunks · ${state.corpus.backend}",
        ) {
            state.corpus.documents.forEach { document ->
                SurfaceCard(backgroundColor = AppColors.Lavender) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(document.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${document.entity} · ${document.chunkCount} chunk(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Indigo,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = document.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        state.error?.let { message ->
            SurfaceCard(backgroundColor = AppColors.Navy, borderColor = AppColors.Navy) {
                Text(text = message, color = AppColors.Cream, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}
