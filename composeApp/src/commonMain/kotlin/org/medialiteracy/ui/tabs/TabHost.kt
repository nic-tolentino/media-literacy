package org.medialiteracy.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.medialiteracy.ui.screens.HomeScreen
import org.medialiteracy.ui.screens.LearningScreen
import org.medialiteracy.ui.screens.SettingsScreen

/**
 * TabHost serves as the main navigation container for the application.
 * Restored the prominent shadow for the bottom navigation bar.
 */
class TabHost : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        TabNavigator(AnalyseTab) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    Column {
                        // Using Divider (compatible with most M3 versions) to ensure separation 
                        // and a prominent shadow surface for visual depth.
                        Divider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp)
                        Surface(
                            shadowElevation = 16.dp, 
                            tonalElevation = 0.dp,
                            color = Color.White
                        ) {
                            NavigationBar(
                                containerColor = Color.White,
                                tonalElevation = 0.dp,
                                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                TabNavigationItem(AnalyseTab)
                                TabNavigationItem(LearnTab)
                                TabNavigationItem(SettingsTab)
                            }
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    CurrentTab()
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    val isSelected = tabNavigator.current == tab
    
    NavigationBarItem(
        selected = isSelected,
        onClick = { tabNavigator.current = tab },
        icon = { 
            Icon(
                painter = tab.options.icon!!, 
                contentDescription = tab.options.title
            ) 
        },
        label = { 
            Text(
                tab.options.title,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ) 
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF1A237E),
            selectedTextColor = Color(0xFF1A237E),
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray,
            indicatorColor = Color(0xFFE8EAF6)
        )
    )
}

object AnalyseTab : Tab {
    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u, 
            title = "Analyse", 
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Analytics)
        )

    @Composable
    override fun Content() {
        Navigator(HomeScreen())
    }
}

object LearnTab : Tab {
    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u, 
            title = "Learn", 
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.School)
        )

    @Composable
    override fun Content() {
        Navigator(LearningScreen())
    }
}

object SettingsTab : Tab {
    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 2u, 
            title = "Settings", 
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Settings)
        )

    @Composable
    override fun Content() {
        Navigator(SettingsScreen())
    }
}
