package org.medialiteracy.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.medialiteracy.domain.GemmaOrchestrator
import org.medialiteracy.domain.InferenceState
import org.medialiteracy.ui.tabs.TabHost

class OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val orchestrator = rememberScreenModel { GemmaOrchestrator() }
        val state by orchestrator.state.collectAsState()

        LaunchedEffect(Unit) {
            orchestrator.downloadModel()
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header Icon
                Icon(
                    Icons.Default.Security, 
                    contentDescription = null, 
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF3F51B5)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "Welcome to News Decoder",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Establishing your private, on-device logic engine for safe media analysis.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Status Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        when (val s = state) {
                            is InferenceState.DownloadingModel -> {
                                StatusItem(
                                    icon = Icons.Default.CloudDownload,
                                    title = "Retrieving Model Weights",
                                    description = "Gemma 4-E2B (1.2GB)",
                                    progress = s.progress
                                )
                            }
                            is InferenceState.Idle -> {
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
                            else -> {
                                Text("Initializing system...")
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
fun StatusItem(icon: ImageVector, title: String, description: String, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF3F51B5), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    LinearProgressIndicator(
        progress = progress, 
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF3F51B5),
        trackColor = Color.LightGray.copy(alpha = 0.3f)
    )
    Text(
        "${(progress * 100).toInt()}%", 
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelSmall
    )
}
