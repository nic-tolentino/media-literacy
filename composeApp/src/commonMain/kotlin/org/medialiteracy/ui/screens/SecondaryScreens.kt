package org.medialiteracy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.core.model.rememberScreenModel
import org.medialiteracy.domain.GemmaOrchestrator
import org.medialiteracy.domain.InferenceState

class LearningScreen : Screen {
    @Composable
    override fun Content() {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Learning Hub", style = MaterialTheme.typography.headlineMedium)
            Text("Sharpen your media literacy skills.")
            Spacer(modifier = Modifier.height(16.dp))
            Text("Quiz: Spot the Fallacy", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { /* TODO */ }) { Text("Start Quiz") }
        }
    }
}

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Local AI Model: Gemma 4 E2B", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { /* TODO */ }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Delete Model Weights")
            }
        }
    }
}

class OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val orchestrator = rememberScreenModel { GemmaOrchestrator() }
        val state by orchestrator.state.collectAsState()
        
        LaunchedEffect(Unit) {
            orchestrator.downloadModel()
        }

        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("Welcome to Gemma4ML", style = MaterialTheme.typography.headlineLarge)
            Text("Setting up your local AI logic engine...")
            Spacer(modifier = Modifier.height(32.dp))
            
            when (val s = state) {
                is InferenceState.DownloadingModel -> {
                    LinearProgressIndicator(progress = s.progress, modifier = Modifier.fillMaxWidth())
                    Text("${(s.progress * 100).toInt()}% - Downloading from GCS...")
                }
                is InferenceState.Idle -> {
                    // Check if we just finished downloading
                    LaunchedEffect(Unit) {
                        navigator.replaceAll(org.medialiteracy.ui.tabs.TabHost())
                    }
                }
                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
