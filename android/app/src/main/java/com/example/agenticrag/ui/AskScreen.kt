
package com.example.agenticrag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticrag.ui.components.Accordion
import com.example.agenticrag.ui.components.StatusPill
import com.example.agenticrag.ui.components.SurfaceCard
import com.example.agenticrag.ui.components.TraceTimeline
import com.example.agenticrag.ui.theme.AppColors
import com.example.agenticrag.viewmodel.AgentViewModel

@Composable
fun AskScreen(viewModel: AgentViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Cream)
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Agent Workspace",
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "Multi-hop retrieval with grounded verification",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Indigo,
            )

            Spacer(modifier = Modifier.height(16.dp))

            SurfaceCard(backgroundColor = AppColors.Lavender) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlRow(label = "k") {
                        listOf(1, 3, 5).forEach { value ->
                            SegmentPill(
                                text = value.toString(),
                                selected = state.k == value
                            ) {
                                viewModel.onKChange(value)
                            }
                        }
                    }

                    ControlRow(label = "hops") {
                        listOf(2, 3, 4).forEach { value ->
                            SegmentPill(
                                text = value.toString(),
                                selected = state.maxHops == value
                            ) {
                                viewModel.onMaxHopsChange(value)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Poison Hop 1",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )

                        Switch(
                            checked = state.poisonHopOne,
                            onCheckedChange = viewModel::onPoisonChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AppColors.Cream,
                                checkedTrackColor = AppColors.Indigo,
                                uncheckedThumbColor = AppColors.Indigo,
                                uncheckedTrackColor = AppColors.Lavender,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.Indigo,
                    trackColor = AppColors.Lavender,
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            state.error?.let { message ->
                SurfaceCard(
                    backgroundColor = AppColors.Navy,
                    borderColor = AppColors.Navy
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            color = AppColors.Cream,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "DISMISS",
                            color = AppColors.Lavender,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                viewModel.dismissError()
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            val response = state.response

            if (response != null) {
                SurfaceCard(
                    backgroundColor = AppColors.Navy,
                    borderColor = AppColors.Navy
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Final answer",
                            color = AppColors.Cream,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )

                        StatusPill(status = response.status)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.Cream)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = response.answer,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Accordion(
                    title = "Agent Execution Trace",
                    subtitle = "${response.steps.size} steps · " +
                        "${response.metrics.hopsUsed}/${response.metrics.maxHops} hops · " +
                        "${response.metrics.retrievalCalls} retrievals · " +
                        "${response.metrics.cacheHits} cached",
                ) {
                    TraceTimeline(steps = response.steps)
                }
            } else {
                SurfaceCard(backgroundColor = AppColors.Lavender) {
                    Text(
                        text = "Enter a multi-hop question to start the agent pipeline.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.question,
                onValueChange = viewModel::onQuestionChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Ask a multi-hop question",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.Navy,
                    unfocusedTextColor = AppColors.Navy,
                    cursorColor = AppColors.Indigo,
                    focusedBorderColor = AppColors.Indigo,
                    unfocusedBorderColor = AppColors.Lavender,
                    focusedContainerColor = AppColors.Cream,
                    unfocusedContainerColor = AppColors.Cream,
                ),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = viewModel::ask,
                enabled = state.question.isNotBlank() && !state.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Navy,
                    contentColor = AppColors.Cream,
                    disabledContainerColor = AppColors.Lavender,
                    disabledContentColor = AppColors.Navy,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(56.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AppColors.Cream,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Ask")
                }
            }
        }
    }
}

@Composable
private fun ControlRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(38.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SegmentPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) AppColors.Navy else AppColors.Cream
    val foreground = if (selected) AppColors.Cream else AppColors.Navy

    Text(
        text = text,
        color = foreground,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, AppColors.Indigo, RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
