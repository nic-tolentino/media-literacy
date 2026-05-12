package org.medialiteracy.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.medialiteracy.domain.EngineInternalState
import org.medialiteracy.domain.GemmaOrchestrator
import org.medialiteracy.domain.InferenceState
import org.medialiteracy.ui.tabs.TabHost
import org.jetbrains.compose.resources.painterResource
import medialiteracy.composeapp.generated.resources.Res
import medialiteracy.composeapp.generated.resources.app_logo

class OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val orchestrator = rememberScreenModel { GemmaOrchestrator() }
        val state by orchestrator.state.collectAsState()

        LaunchedEffect(Unit) {
            orchestrator.downloadModel()
        }

        val engineState by orchestrator.engineState.collectAsState()

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header Logo
                Image(
                    painter = painterResource(Res.drawable.app_logo),
                    contentDescription = "News Decoder Logo",
                    modifier = Modifier.size(120.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "Welcome to News Decoder",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Establishing your private, on-device logic engine for safe media analysis.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Status Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        when (engineState) {
                            EngineInternalState.Initializing -> {
                                StatusItem(
                                    icon = Icons.Default.CloudDownload,
                                    title = "Initializing Engine",
                                    description = "Loading Gemma weights (1.2GB)",
                                    progress = 0.5f // Indeterminate or mock
                                )
                            }
                            EngineInternalState.Idle -> {
                                StatusItem(
                                    icon = Icons.Default.AutoAwesome,
                                    title = "AI Ready",
                                    description = "Optimization complete. You're ready to analyze.",
                                    progress = 1f
                                )
                                
                                LaunchedEffect(Unit) {
                                    navigator.replaceAll(TabHost())
                                }
                            }
                            EngineInternalState.Error -> {
                                StatusItem(
                                    icon = Icons.Default.Security,
                                    title = "Initialization Failed",
                                    description = "Model not found or corrupted. Please check your storage.",
                                    progress = 0f,
                                    isError = true
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Button(
                                    onClick = { orchestrator.resetEngine() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry Initialization")
                                }
                            }
                            else -> {
                                Text("System status: $engineState", color = MaterialTheme.colorScheme.onSurface)
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "100% OFFLINE. PRIVATE. SECURE.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StatusItem(icon: ImageVector, title: String, description: String, progress: Float, isError: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon, 
            null, 
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, 
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    LinearProgressIndicator(
        progress = progress, 
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant
    )
    Text(
        if (isError) "ERROR" else "${(progress * 100).toInt()}%", 
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelSmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
