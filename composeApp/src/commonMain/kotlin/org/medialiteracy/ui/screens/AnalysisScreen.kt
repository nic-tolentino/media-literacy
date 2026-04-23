package org.medialiteracy.ui.screens

import androidx.compose.animation.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.medialiteracy.domain.AnalysisResult
import org.medialiteracy.domain.GemmaOrchestrator
import org.medialiteracy.domain.InferenceState

data class AnalysisScreen(val inputText: String) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val orchestrator = rememberScreenModel { GemmaOrchestrator() }
        val state by orchestrator.state.collectAsState()
        val scope = rememberCoroutineScope()
        
        LaunchedEffect(inputText) {
            orchestrator.startAnalysis(inputText)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Analysis Report", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) { Icon(Icons.Default.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = {}) { Icon(Icons.Default.Share, "Share") }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    is InferenceState.Thinking -> {
                        ThinkingState(s.tokens)
                    }
                    is InferenceState.Complete -> {
                        ReportContent(s.result) {
                            navigator.push(ChatScreen())
                        }
                    }
                    is InferenceState.Error -> {
                        ErrorState(s.message) { 
                            scope.launch { orchestrator.startAnalysis(inputText) } 
                        }
                    }
                    else -> {
                        ThinkingState("Initializing Engine...")
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingState(tokens: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color(0xFF3F51B5), strokeWidth = 6.dp, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Gemma is deconstructing the logic...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            tokens.takeLast(100), // Show partial thought tokens
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "ANALYZING RHETORICAL PATTERNS",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4DB6AC),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ReportContent(result: AnalysisResult, onDiscuss: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Executive Summary", fontWeight = FontWeight.Bold, color = Color(0xFF3F51B5))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(result.summary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreCard(
                    modifier = Modifier.weight(1f),
                    label = "Credibility",
                    value = result.credibility,
                    containerColor = Color(0xFFA7FFEB)
                )
                ScoreCard(
                    modifier = Modifier.weight(1f),
                    label = "Evidence Qual.",
                    value = "${result.evidenceQuality}%",
                    containerColor = Color(0xFFE8EAF6)
                )
            }
        }

        item {
            Text("Fallacy Radar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (result.fallacies.isEmpty()) {
            item {
                Text("No common logical fallacies detected.", color = Color.Gray)
            }
        } else {
            items(result.fallacies.size) { index ->
                val f = result.fallacies[index]
                FallacyItem(f.type, f.evidence)
            }
        }

        item {
            Button(
                onClick = onDiscuss,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Forum, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Discuss with Logic Master")
            }
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun ScoreCard(modifier: Modifier, label: String, value: String, containerColor: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF212121))
        }
    }
}

@Composable
fun FallacyItem(title: String, snippet: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF3F3)) // Slight red tint
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ReportProblem, null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(snippet, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Analysis Failed", fontWeight = FontWeight.Bold)
        Text(message, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Retry Analysis") }
    }
}
