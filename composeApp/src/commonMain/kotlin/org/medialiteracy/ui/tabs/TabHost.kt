package org.medialiteracy.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.medialiteracy.ui.screens.HomeScreen
import org.medialiteracy.ui.screens.LearningScreen
import org.medialiteracy.ui.screens.SettingsScreen

class TabHost : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        TabNavigator(HomeTab) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        TabNavigationItem(HomeTab)
                        TabNavigationItem(LearnTab)
                        TabNavigationItem(SettingsTab)
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    CurrentTab()
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    NavigationBarItem(
        selected = tabNavigator.current == tab,
        onClick = { tabNavigator.current = tab },
        icon = { Icon(painter = tab.options.icon!!, contentDescription = tab.options.title) },
        label = { Text(tab.options.title) }
    )
}

object HomeTab : Tab {
    override val options: TabOptions
        @Composable
        get() = TabOptions(index = 0u, title = "Home", icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Home))

    @Composable
    override fun Content() {
        HomeScreen().Content()
    }
}

object LearnTab : Tab {
    override val options: TabOptions
        @Composable
        get() = TabOptions(index = 1u, title = "Learn", icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.List))

    @Composable
    override fun Content() {
        LearningScreen().Content()
    }
}

object SettingsTab : Tab {
    override val options: TabOptions
        @Composable
        get() = TabOptions(index = 2u, title = "Settings", icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Settings))

    @Composable
    override fun Content() {
        SettingsScreen().Content()
    }
}
