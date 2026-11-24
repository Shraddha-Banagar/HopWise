package com.example.agenticrag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agenticrag.ui.AskScreen
import com.example.agenticrag.ui.EvidenceScreen
import com.example.agenticrag.ui.HopWiseLandingScreen
import com.example.agenticrag.ui.MemoryScreen
import com.example.agenticrag.ui.theme.AgenticRAGTheme
import com.example.agenticrag.ui.theme.AppColors
import com.example.agenticrag.viewmodel.AgentViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgenticRAGTheme {
                AgenticApp()
            }
        }
    }
}

@Composable
fun AgenticApp(viewModel: AgentViewModel = viewModel()) {
    var showLanding by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }

    BackHandler(enabled = !showLanding) { showLanding = true }

    Crossfade(targetState = showLanding, label = "RootTransition") { landing ->
        if (landing) {
            HopWiseLandingScreen(onOpenWorkspace = { showLanding = false })
        } else {
            WorkspaceScreen(
                viewModel = viewModel,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onBackToHome = { showLanding = true },
            )
        }
    }
}

@Composable
private fun WorkspaceScreen(
    viewModel: AgentViewModel,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onBackToHome: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val tabs = listOf("Ask" to Icons.Filled.Chat, "Evidence" to Icons.Filled.Article, "Memory" to Icons.Filled.Storage)
    val badgeCounts = listOf(0, state.scenarios.size, state.memory.facts.size)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppColors.Cream,
        bottomBar = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.Lavender),
                )

                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    containerColor = AppColors.Cream,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEachIndexed { index, (label, icon) ->
                        val count = badgeCounts.getOrElse(index) { 0 }

                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            icon = {
                                Box {
                                    Icon(imageVector = icon, contentDescription = label)
                                    if (count > 0) {
                                        Badge(
                                            modifier = Modifier.align(Alignment.TopEnd),
                                            containerColor = AppColors.Navy,
                                            contentColor = AppColors.Cream,
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppColors.Cream,
                                selectedTextColor = AppColors.Navy,
                                indicatorColor = AppColors.Navy,
                                unselectedIconColor = AppColors.Indigo,
                                unselectedTextColor = AppColors.Indigo,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> AskScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
                onBackToHome = onBackToHome,
            )
            1 -> EvidenceScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
            2 -> MemoryScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
        }
    }
}
