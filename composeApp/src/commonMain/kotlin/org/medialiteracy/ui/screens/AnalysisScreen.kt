package org.medialiteracy.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import org.medialiteracy.domain.AnalysisResult
import org.medialiteracy.domain.GemmaOrchestrator
import org.medialiteracy.domain.InferenceState
import kotlin.math.cos
import kotlin.math.sin

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
                NewsDecoderHeader { navigator.pop() }
            },
            bottomBar = {
                NewsDecoderBottomNav()
            },
            containerColor = Color.White
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    is InferenceState.Thinking -> {
                        ThinkingState(s.partialResponse)
                    }
                    is InferenceState.Complete -> {
                        ReportContent(s.result) { initialPrompt ->
                            navigator.push(ChatScreen(initialPrompt))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDecoderHeader(onClose: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                "News Decoder",
                color = Color(0xFF1A237E),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1A237E))
            }
        },
        actions = {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
    )
}

@Composable
fun ReportContent(result: AnalysisResult, onLogicHatClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, null, tint = Color(0xFF3F51B5), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "ANALYSIS REPORT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF3F51B5),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Structural Analysis Report",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    "Comprehensive evaluation of logical structure, evidentiary support, and rhetorical framing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }

        item {
            AnalysisSectionCard(
                title = "Executive Summary",
                icon = Icons.Default.School,
                onIconClick = { onLogicHatClick("Can you help me understand the core pillars of this argument?") }
            ) {
                Column {
                    Text(
                        result.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallMetricCard(
                            label = "Primary Strength",
                            value = result.primaryStrength,
                            icon = Icons.Default.CheckCircle,
                            iconColor = Color(0xFF00897B),
                            modifier = Modifier.weight(1f)
                        )
                        SmallMetricCard(
                            label = "Observation Area",
                            value = result.observationArea,
                            icon = Icons.Default.Info,
                            iconColor = Color(0xFF424242),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            AnalysisSectionCard(
                title = "Narrative Tone",
                subtitle = "Evaluates the degree of subjectivity and emotional framing",
                icon = Icons.Default.School,
                onIconClick = { onLogicHatClick("Can you explain how you reached this objectivity score, and how it differs from the logic metrics?") }
            ) {
                NarrativePerspectiveSlider(result.objectivityScore / 100f)
            }
        }

        item {
            AnalysisSectionCard(
                title = "Argument Metrics",
                icon = Icons.Default.School,
                onIconClick = { onLogicHatClick("Can you please explain how you calculated these metric scores?") }
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    RadarChart(
                        logic = result.logicScore.toFloat(),
                        objectivity = result.objectivityScore.toFloat(),
                        evidence = result.evidenceQuality.toFloat(),
                        credibility = result.credibilityScore.toFloat()
                    )
                }
            }
        }

        item {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Rhetorical Patterns Detected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "${result.fallacies.size} Observations",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (result.isAnalyzingFallacies) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF3F51B5))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Identifying rhetorical patterns...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("The Master is deconstructing the deep logical structure. This takes about 20s.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape), color = Color(0xFF3F51B5))
                        }
                    }
                }

                if (result.fallacies.isEmpty() && !result.isAnalyzingFallacies) {
                    Text("No significant patterns detected in this initial pass.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    result.fallacies.forEach { fallacy ->
                        PatternCard(fallacy.type, fallacy.evidence) {
                            onLogicHatClick("Can you please explain why you flagged this specific instance of ${fallacy.type}?")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        item {
            Text(
                "This report evaluates structural logic and rhetorical patterns. It does not verify factual accuracy.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun AnalysisSectionCard(
    title: String, 
    subtitle: String? = null, 
    icon: ImageVector, 
    onIconClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                IconButton(onClick = onIconClick, modifier = Modifier.size(24.dp)) {
                    Icon(icon, null, tint = Color(0xFF3F51B5), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SmallMetricCard(label: String, value: String, icon: ImageVector, iconColor: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFFFAFAFA),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F0F0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Black)
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
        CircularProgressIndicator(color = Color(0xFF1A237E), strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Gemma is deconstructing structure...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(tokens.takeLast(100), style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
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
        Text(message, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Retry Analysis") }
    }
}

@Composable
fun NarrativePerspectiveSlider(value: Float) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subjective", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("Balanced", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("Objective", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val width = size.width
            val height = size.height
            drawRoundRect(Color(0xFFE0E0E0), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2, height / 2))
            drawRoundRect(Color(0xFF7986CB), size = Size(width * value, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2, height / 2))
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(width * value, height / 2))
            drawCircle(Color(0xFF1A237E), radius = 6.dp.toPx(), center = Offset(width * value, height / 2), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
fun RadarChart(logic: Float, objectivity: Float, evidence: Float, credibility: Float) {
    // ENFORCE ABSOLUTE MAPPING: i=0 is North, clockwise logic
    // Order: 0=Evidence (T), 1=Logic (R), 2=Objectivity (B), 3=Credibility (L)
    val values = listOf(evidence, logic, objectivity, credibility)
    
    Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(170.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2
            
            // Central Point reference
            drawCircle(Color.LightGray, radius = 2.dp.toPx(), center = center)
            
            // Web circles (Absolute 0, 25, 50, 75, 100)
            for (i in 1..4) {
                drawCircle(Color(0xFFE0E0E0), radius = radius * (i / 4f), center = center, style = Stroke(width = 1.dp.toPx()))
            }
            
            // Polygon
            val path = Path()
            for (i in 0..3) {
                val angle = (i * Math.PI / 2 - Math.PI / 2).toFloat()
                // Add a tiny floor (0.05) so the polygon is always slightly visible even at 0 scores
                val rawPercent = (values[i] / 100f).coerceIn(0f, 1f) 
                val valPercent = if (rawPercent < 0.05f) 0.05f else rawPercent
                val px = center.x + (radius * valPercent) * cos(angle)
                val py = center.y + (radius * valPercent) * sin(angle)
                
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                drawCircle(Color(0xFF1A237E), radius = 4.dp.toPx(), center = Offset(px, py))
            }
            path.close()
            drawPath(path, Color(0xFF3F51B5).copy(alpha = 0.35f), style = Fill)
            drawPath(path, Color(0xFF1A237E), style = Stroke(width = 3.dp.toPx()))
        }

        // Labels
        Text("EVIDENCE", modifier = Modifier.align(Alignment.TopCenter), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.Gray)
        Text("LOGIC", modifier = Modifier.align(Alignment.CenterEnd), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.Gray)
        Text("OBJECTIVITY", modifier = Modifier.align(Alignment.BottomCenter), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.Gray)
        Text("CREDIBILITY", modifier = Modifier.align(Alignment.CenterStart), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.Gray)
    }
}

@Composable
fun PatternCard(type: String, evidence: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Segment, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(type, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Text(evidence, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.School, null, tint = Color(0xFF3F51B5), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun NewsDecoderBottomNav() {
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
        NavigationBarItem(icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") }, selected = true, onClick = {})
        NavigationBarItem(icon = { Icon(Icons.Default.School, null) }, label = { Text("Learn") }, selected = false, onClick = {})
        NavigationBarItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, selected = false, onClick = {})
    }
}
