package com.example.agenticrag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agenticrag.ui.AskScreen
import com.example.agenticrag.ui.EvidenceScreen
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Ask" to Icons.Filled.Chat, "Evidence" to Icons.Filled.Article, "Memory" to Icons.Filled.Storage)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppColors.Cream,
        bottomBar = {
            NavigationBar(containerColor = AppColors.Cream) {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
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
        },
    ) { padding ->
        when (selectedTab) {
            0 -> AskScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
            1 -> EvidenceScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
            2 -> MemoryScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
        }
    }
}
