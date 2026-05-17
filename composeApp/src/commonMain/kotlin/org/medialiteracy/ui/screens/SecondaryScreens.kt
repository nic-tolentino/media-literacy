package org.medialiteracy.ui.screens
import org.medialiteracy.ui.components.AppBarTitle
import org.medialiteracy.ui.components.PrimaryButton
import org.medialiteracy.ui.LocalThemeIsDark
import org.medialiteracy.ui.analyticalColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.model.rememberScreenModel
import org.medialiteracy.domain.SettingsRepository
import org.medialiteracy.domain.ThemeMode
import org.medialiteracy.ui.LocalRootNavigator
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.launch

class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val settingsRepository = remember { SettingsRepository.getInstance() }
        val themeMode by settingsRepository.getThemeMode().collectAsState(ThemeMode.SYSTEM)
        val scope = rememberCoroutineScope()

        val orchestrator = rememberScreenModel { org.medialiteracy.domain.GemmaOrchestrator() }
        val installedVariant by orchestrator.installedVariant.collectAsState()
        val recommended = remember { org.medialiteracy.domain.DeviceCapabilityChecker.recommendedVariant() }
        val freeSpaceGb by produceState(0.0) {
            value = orchestrator.availableDiskBytes() / (1024.0 * 1024.0 * 1024.0)
        }

        var showDeleteDialog by remember { mutableStateOf(false) }
        var showDownloadSheet by remember { mutableStateOf(false) }

        if (showDownloadSheet) {
            ModelDownloadSheet(
                orchestrator = orchestrator,
                onDismiss = { showDownloadSheet = false },
                onComplete = { 
                    showDownloadSheet = false
                    scope.launch {
                        orchestrator.resetEngine()
                    }
                }
            )
        }

         Scaffold(
            topBar = {
                Surface(
                    shadowElevation = MaterialTheme.analyticalColors.appBarElevation,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    CenterAlignedTopAppBar(
                        title = { AppBarTitle("Settings") },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                ThemeSelectionRow(
                    selectedMode = themeMode,
                    onModeSelected = { mode ->
                        scope.launch {
                            settingsRepository.setThemeMode(mode)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text("AI Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (installedVariant != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(installedVariant?.displayName ?: "", fontWeight = FontWeight.Bold)
                                Text("${installedVariant?.approximateSizeGb} GB · Active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (installedVariant == org.medialiteracy.domain.ModelVariant.E2B && recommended == org.medialiteracy.domain.ModelVariant.E4B) {
                        Spacer(modifier = Modifier.height(12.dp))
                        UpgradeCard(
                            freeSpaceGb = freeSpaceGb,
                            onClick = { showDownloadSheet = true }
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("No model installed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text("Analysis features are disabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PrimaryButton(
                                text = "Download",
                                onClick = { showDownloadSheet = true },
                                modifier = Modifier.width(120.dp).height(40.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text("App Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingRow(Icons.Default.History, "Auto-clear History", "After 24 hours")
                SettingRow(Icons.Default.VpnKey, "Biometric Lock", "Disabled")
                SettingRow(Icons.Default.CloudOff, "External Access", "Always Blocked")
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete model file?") },
                        text = { Text("This removes the ${installedVariant?.approximateSizeGb} GB ${installedVariant?.displayName} model from your device. Analysis features will be unavailable until you re-download a model.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    installedVariant?.let { variant ->
                                        orchestrator.deleteModel(variant) { 
                                            showDeleteDialog = false
                                            // In a real app we'd refresh state here
                                        }
                                    }
                                }
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionRow(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val options = listOf(
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark",
                ThemeMode.SYSTEM to "System"
            )
            
            options.forEach { (mode, label) ->
                val selected = selectedMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
fun SettingRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeCard(freeSpaceGb: Double, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨ Upgrade available", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Gemma 4 E4B · 3.4 GB. More accurate analysis — your device supports it. Needs 3.4 GB free temporarily (+1 GB net after upgrade).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
