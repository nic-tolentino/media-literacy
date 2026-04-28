package org.medialiteracy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.LocalIndication
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
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.core.model.rememberScreenModel
import org.medialiteracy.domain.SavedAnalysis
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class HomeScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { HomeScreenModel() }
        val savedAnalyses by screenModel.savedAnalyses.collectAsState()
        var articleToDelete by remember { mutableStateOf<SavedAnalysis?>(null) }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "News Decoder",
                            color = Color(0xFF3F51B5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {}) { Icon(Icons.Default.Menu, "Menu") }
                    },
                    actions = {
                        IconButton(onClick = {}) { Icon(Icons.Default.Search, "Search") }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            "Deconstruct news logic and argument structure.",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 40.sp,
                            fontSize = 34.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Select an input method below to analyze internal consistency and rhetorical patterns.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }

                item {
                    InputCard(
                        title = "Paste Text / URL",
                        description = "Quickly analyze articles, blog posts, or copied text fragments.",
                        icon = Icons.Default.Assignment,
                        containerColor = Color(0xFF3F51B5),
                        onClick = { 
                            val rootNavigator = navigator.parent ?: navigator
                            rootNavigator.push(PasteInputScreen()) 
                        }
                    )
                }

                item {
                    InputCard(
                        title = "Scan Newspaper",
                        description = "Use your camera to extract text from physical media.",
                        icon = Icons.Default.CropFree,
                        containerColor = Color(0xFF00796B), // Slightly darker teal
                        onClick = { 
                            val rootNavigator = navigator.parent ?: navigator
                            rootNavigator.push(PhotoPickerScreen()) 
                        }
                    )
                }

                item {
                    InputCard(
                        title = "Record Audio",
                        description = "Transcribe and analyze live speeches or broadcasts.",
                        icon = Icons.Default.Mic,
                        containerColor = Color(0xFFC62828), // Crimson
                        onClick = { 
                            val rootNavigator = navigator.parent ?: navigator
                            rootNavigator.push(AudioPickerScreen()) 
                        }
                    )
                }

                if (savedAnalyses.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Recent Analyses",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "VIEW ALL",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF3F51B5),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(savedAnalyses, key = { it.id }) { analysis ->
                        RecentAnalysisCard(
                            analysis = analysis,
                            onClick = {
                                val rootNavigator = navigator.parent ?: navigator
                                rootNavigator.push(AnalysisScreen(
                                    inputText = analysis.originalArticleText,
                                    cachedResult = analysis.analysisResult,
                                    analysisId = analysis.id
                                ))
                            },
                            onDelete = {
                                articleToDelete = analysis
                            }
                        )
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.History, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No history yet", color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text("Start an analysis to see history here.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            articleToDelete?.let { analysis ->
                AlertDialog(
                    onDismissRequest = { articleToDelete = null },
                    title = { Text("Delete Analysis") },
                    text = { Text("Are you sure you want to delete this analysis? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                screenModel.deleteAnalysis(analysis.id)
                                articleToDelete = null
                            }
                        ) {
                            Text("Delete", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { articleToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputCard(
    title: String,
    description: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp).padding(start = 8.dp),
                tint = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentAnalysisCard(
    analysis: SavedAnalysis,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val result = analysis.analysisResult
    // ... timeStr calculation ...
    val timeStr = remember(analysis.timestamp) {
        try {
            val instant = Instant.fromEpochMilliseconds(analysis.timestamp)
            val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${dt.monthNumber}/${dt.dayOfMonth} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
        } catch (e: Exception) {
            "Just now"
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeText(
                    text = result.credibility.uppercase(),
                    containerColor = when {
                        result.credibilityScore > 70 -> Color(0xFFA7FFEB)
                        result.credibilityScore < 40 -> Color(0xFFFFCCBC)
                        else -> Color(0xFFF5F5F5)
                    },
                    contentColor = when {
                        result.credibilityScore > 70 -> Color(0xFF004D40)
                        result.credibilityScore < 40 -> Color(0xFFBF360C)
                        else -> Color.DarkGray
                    }
                )
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                analysis.originalArticleText.take(60).replace("\n", " ") + "...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Score: ${result.credibilityScore}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun BadgeText(text: String, containerColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = contentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
