package com.example.agenticrag.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

private val SubtleBorder = AppColors.Indigo.copy(alpha = 0.28f)

@Composable
fun AskScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier,
    onBackToHome: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isInputFocused by remember { mutableStateOf(false) }
    var parametersExpanded by rememberSaveable { mutableStateOf(true) }
    val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }
    val askEnabled = state.question.isNotBlank() && !state.isLoading
    val askContainerColor by animateColorAsState(
        targetValue = if (askEnabled) AppColors.Navy else AppColors.Lavender,
        animationSpec = tween(durationMillis = 220),
        label = "AskContainerColor",
    )
    val askContentColor by animateColorAsState(
        targetValue = if (askEnabled) AppColors.Cream else AppColors.Navy,
        animationSpec = tween(durationMillis = 220),
        label = "AskContentColor",
    )

    fun dismissKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Cream)
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (isInputFocused) dismissKeyboard()
                }
            },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBackToHome,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to HopWise",
                        tint = AppColors.Navy,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Text(
                    text = "Agent Workspace",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )

                AgentStatusPill(isRunning = state.isLoading)
            }

            Text(
                text = "Multi-hop retrieval with grounded verification",
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.15.sp),
                color = AppColors.Indigo,
                modifier = Modifier.padding(start = 56.dp, top = 4.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(1.dp)
                    .background(if (isScrolled) AppColors.Lavender else AppColors.Cream),
            )

            Spacer(modifier = Modifier.height(16.dp))

            SurfaceCard(
                backgroundColor = AppColors.Lavender,
                borderColor = SubtleBorder,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PARAMETERS",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                            color = AppColors.Indigo,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { parametersExpanded = !parametersExpanded },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (parametersExpanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = if (parametersExpanded) {
                                    "Collapse parameters"
                                } else {
                                    "Expand parameters"
                                },
                                tint = AppColors.Navy,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = parametersExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ControlRow(label = "k") {
                                listOf(1, 3, 5).forEach { value ->
                                    SegmentPill(
                                        text = value.toString(),
                                        selected = state.k == value,
                                    ) {
                                        viewModel.onKChange(value)
                                    }
                                }
                            }

                            ControlRow(label = "hops") {
                                listOf(2, 3, 4).forEach { value ->
                                    SegmentPill(
                                        text = value.toString(),
                                        selected = state.maxHops == value,
                                    ) {
                                        viewModel.onMaxHopsChange(value)
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(AppColors.Cream),
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Poison Hop 1",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f),
                                    )

                                    Text(
                                        text = if (state.poisonHopOne) "Active" else "Off",
                                        color = AppColors.Indigo,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Switch(
                                        checked = state.poisonHopOne,
                                        onCheckedChange = viewModel::onPoisonChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AppColors.Cream,
                                            checkedTrackColor = AppColors.Indigo,
                                            uncheckedThumbColor = AppColors.Indigo,
                                            uncheckedTrackColor = AppColors.Cream,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    if (!parametersExpanded) {
                        Text(
                            text = "k=${state.k} · ${state.maxHops} hops · " +
                                "Poison: ${if (state.poisonHopOne) "Active" else "Off"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.Navy,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                PipelineProgressCard()
                Spacer(modifier = Modifier.height(16.dp))
            }

            state.error?.let { message ->
                SurfaceCard(
                    backgroundColor = AppColors.Navy,
                    borderColor = AppColors.Navy,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            color = AppColors.Cream,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
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
                    borderColor = AppColors.Navy,
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

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.dismissResponse() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss final answer",
                                tint = AppColors.Cream,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.Cream)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = response.answer,
                            style = MaterialTheme.typography.bodyLarge,
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
            } else if (!state.isLoading) {
                EmptyState()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColors.Cream,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.question,
                    onValueChange = viewModel::onQuestionChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isInputFocused = it.isFocused },
                    placeholder = {
                        Text(
                            "Ask a multi-hop question",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.Navy,
                        unfocusedTextColor = AppColors.Navy,
                        cursorColor = AppColors.Indigo,
                        focusedBorderColor = AppColors.Indigo,
                        unfocusedBorderColor = AppColors.Lavender,
                        focusedContainerColor = AppColors.Cream,
                        unfocusedContainerColor = AppColors.Cream,
                    ),
                    trailingIcon = {
                        if (state.question.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.onQuestionChange("")
                                    dismissKeyboard()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear question",
                                    tint = AppColors.Indigo,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = viewModel::ask,
                    enabled = state.question.isNotBlank() && !state.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = askContainerColor,
                        contentColor = askContentColor,
                        disabledContainerColor = askContainerColor,
                        disabledContentColor = askContentColor,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(
                        text = if (state.isLoading) "Running..." else "Ask",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentStatusPill(isRunning: Boolean) {
    val background = if (isRunning) AppColors.Navy else AppColors.Lavender
    val foreground = if (isRunning) AppColors.Cream else AppColors.Navy
    val dotColor by animateColorAsState(
        targetValue = if (isRunning) AppColors.Lavender else AppColors.Navy,
        label = "StatusDotColor",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = if (isRunning) "Running" else "Ready",
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ControlRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.Navy,
            modifier = Modifier.width(38.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SegmentPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "SegmentScale",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) AppColors.Navy else AppColors.Cream,
        label = "SegmentBackground",
    )
    val foregroundColor by animateColorAsState(
        targetValue = if (selected) AppColors.Cream else AppColors.Navy,
        label = "SegmentForeground",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (selected) AppColors.Navy else SubtleBorder,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = foregroundColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PipelineFlowIndicator()
    }
}

@Composable
private fun PipelineFlowIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PipelinePill(label = "Plan")
        FlowArrow()
        PipelinePill(label = "Retrieve")
        FlowArrow()
        PipelinePill(label = "Verify")
    }
}

@Composable
private fun PipelinePill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Lavender)
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = AppColors.Navy,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FlowArrow() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = AppColors.Indigo,
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(14.dp),
    )
}

@Composable
private fun PipelineProgressCard() {
    val transition = rememberInfiniteTransition(label = "PipelineProgress")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "PipelineStep",
    )
    val activeIndex = step.toInt().coerceIn(0, 2)

    SurfaceCard(
        backgroundColor = AppColors.Lavender,
        borderColor = SubtleBorder,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AGENT PIPELINE",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                    color = AppColors.Indigo,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = "RUNNING",
                    color = AppColors.Navy,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PipelineStage(index = 0, label = "Planner", active = activeIndex == 0)
                StageConnector(active = activeIndex > 0)
                PipelineStage(index = 1, label = "Researcher", caption = "hops", active = activeIndex == 1)
                StageConnector(active = activeIndex > 1)
                PipelineStage(index = 2, label = "Verifier", active = activeIndex == 2)
            }

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.Indigo,
                trackColor = AppColors.Cream,
            )
        }
    }
}

@Composable
private fun PipelineStage(
    index: Int,
    label: String,
    caption: String? = null,
    active: Boolean,
) {
    val circleColor by animateColorAsState(
        targetValue = if (active) AppColors.Navy else AppColors.Cream,
        label = "StageCircleColor",
    )
    val circleContentColor by animateColorAsState(
        targetValue = if (active) AppColors.Cream else AppColors.Navy,
        label = "StageContentColor",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(circleColor)
                .border(
                    width = 1.dp,
                    color = if (active) AppColors.Navy else SubtleBorder,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                color = circleContentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = label,
            color = AppColors.Navy,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )

        if (caption != null) {
            Text(
                text = caption,
                color = AppColors.Indigo,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun StageConnector(active: Boolean) {
    val color by animateColorAsState(
        targetValue = if (active) AppColors.Navy else SubtleBorder,
        label = "ConnectorColor",
    )

    Box(
        modifier = Modifier
            .width(20.dp)
            .height(2.dp)
            .background(color),
    )
}
