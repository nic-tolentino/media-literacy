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

class HomeScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

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
                            // Use parent navigator to push above the TabNavigator stack
                            val rootNavigator = navigator.parent ?: navigator
                            rootNavigator.push(AnalysisScreen("Dummy Paste Text")) 
                        }
                    )
                }

                item {
                    InputCard(
                        title = "Scan Newspaper",
                        description = "Use your camera to extract text from physical media.",
                        icon = Icons.Default.CropFree,
                        containerColor = Color(0xFF00796B), // Slightly darker teal
                        onClick = { /* TODO */ }
                    )
                }

                item {
                    InputCard(
                        title = "Record Audio",
                        description = "Transcribe and analyze live speeches or broadcasts.",
                        icon = Icons.Default.Mic,
                        containerColor = Color(0xFFC62828), // Crimson
                        onClick = { /* TODO */ }
                    )
                }

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

                items(dummyRecentAnalyses) { analysis ->
                    RecentAnalysisCard(analysis)
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun InputCard(
    title: String,
    description: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current
            ) { onClick() },
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

data class AnalysisEntry(val title: String, val source: String, val bias: String, val time: String)

val dummyRecentAnalyses = listOf(
    AnalysisEntry("Economic Policy Shifts Proposed in New Bill", "GlobalFinance.com", "LOW BIAS", "2h ago"),
    AnalysisEntry("The Outrageous Claims By Opposition Leaders", "DailyOpinion.net", "HIGH BIAS", "5h ago"),
    AnalysisEntry("Local Election Results Bring Mixed Reactions", "CityTribune.org", "MODERATE", "1d ago")
)

@Composable
fun RecentAnalysisCard(entry: AnalysisEntry) {
    Card(
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
                    text = entry.bias,
                    containerColor = when (entry.bias) {
                        "LOW BIAS" -> Color(0xFFA7FFEB)
                        "HIGH BIAS" -> Color(0xFFFFCCBC)
                        else -> Color(0xFFF5F5F5)
                    },
                    contentColor = when (entry.bias) {
                        "LOW BIAS" -> Color(0xFF004D40)
                        "HIGH BIAS" -> Color(0xFFBF360C)
                        else -> Color.DarkGray
                    }
                )
                Text(entry.time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                Text(entry.source, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
