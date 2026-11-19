package com.frayshare.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.frayshare.app.ui.screens.FullMigrationScreen
import com.frayshare.app.ui.screens.HistoryScreen
import com.frayshare.app.ui.screens.TransferScreen

private data class FrayTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    FrayTab("Передача", Icons.Filled.CompareArrows),
    FrayTab("Полный перенос", Icons.Filled.Devices),
    FrayTab("История", Icons.Filled.History)
)

@Composable
fun FrayApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fray") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selected) {
            0 -> TransferScreen(Modifier.padding(padding))
            1 -> FullMigrationScreen(Modifier.padding(padding))
            else -> HistoryScreen(Modifier.padding(padding))
        }
    }
}
