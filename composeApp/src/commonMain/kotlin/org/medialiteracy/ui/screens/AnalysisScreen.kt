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

data class AnalysisScreen(val inputText: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val orchestrator = rememberScreenModel { GemmaOrchestrator() }
        val state by orchestrator.state.collectAsState()
        
        LaunchedEffect(inputText) {
            orchestrator.startAnalysis(inputText)
        }
        
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Logic Analysis", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            when (val s = state) {
                is InferenceState.Idle, is InferenceState.Loading -> {
                    CircularProgressIndicator()
                    Text("Preparing engine...")
                }
                is InferenceState.Thinking -> {
                    CircularProgressIndicator()
                    Text("Gemma is thinking: ${s.tokens}")
                }
                is InferenceState.Complete -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Analysis Results", style = MaterialTheme.typography.titleLarge)
                            Text("Summary: ${s.result.summary}")
                            Text("Fallacies Detected: ${s.result.fallacies.size}")
                            Text("Evidence Quality: ${s.result.evidenceQuality}%")
                            Text("Credibility: ${s.result.credibility}")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navigator.push(ChatScreen()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Discuss with Logic Master")
                    }
                }
                is InferenceState.Error -> {
                    Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                }
                is InferenceState.DownloadingModel -> {
                    Text("Model download in progress...")
                }
            }
        }
    }
}
