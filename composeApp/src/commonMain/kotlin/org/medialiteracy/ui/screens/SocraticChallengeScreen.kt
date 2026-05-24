package org.medialiteracy.ui.screens

import org.medialiteracy.ui.components.AppBarTitle
import org.medialiteracy.ui.components.PrimaryButton
import org.medialiteracy.ui.analyticalColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.medialiteracy.domain.GemmaOrchestrator
import org.medialiteracy.domain.InferenceState
import org.medialiteracy.domain.SocraticSession

sealed interface PendingNavigation {
    data class Rating(val rating: Int) : PendingNavigation
    data class Question(val index: Int, val question: String) : PendingNavigation
    data class Stance(val stance: String) : PendingNavigation
}

val PendingNavigationSaver = listSaver<PendingNavigation?, Any>(
    save = { pending ->
        when (pending) {
            null -> emptyList()
            is PendingNavigation.Rating -> listOf("Rating", pending.rating)
            is PendingNavigation.Question -> listOf("Question", pending.index, pending.question)
            is PendingNavigation.Stance -> listOf("Stance", pending.stance)
        }
    },
    restore = { list ->
        if (list.isEmpty()) null
        else when (list[0] as String) {
            "Rating" -> PendingNavigation.Rating((list[1] as Number).toInt())
            "Question" -> PendingNavigation.Question((list[1] as Number).toInt(), list[2] as String)
            "Stance" -> PendingNavigation.Stance(list[1] as String)
            else -> null
        }
    }
)

data class SocraticChallengeScreen(val isReengagement: Boolean = false) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val orchestrator = rememberScreenModel { GemmaOrchestrator() }
        val state by orchestrator.state.collectAsState()
        val currentResult = (state as? InferenceState.Complete)?.result ?: orchestrator.currentAnalysisResult

        // Safeguard: If we somehow don't have results, skip straight to the report
        if (currentResult == null) {
            LaunchedEffect(Unit) {
                if (isReengagement) {
                    navigator.pop()
                } else {
                    orchestrator.consumeFreshAnalysis()
                    navigator.replace(AnalysisScreen(inputText = orchestrator.currentArticleText ?: ""))
                }
            }
            return
        }

        // Navigation helpers centralise the two different back-stack contracts:
        // - Fresh analysis: replace Socratic with AnalysisScreen, then push Chat on top
        // - Re-engagement: replace Socratic with Chat (AnalysisScreen already sits below)
        val doNavigateToChat: (SocraticSession) -> Unit = { session ->
            orchestrator.updateSocraticSession(session)
            if (isReengagement) {
                navigator.replace(ChatScreen())
            } else {
                orchestrator.consumeFreshAnalysis()
                navigator.replace(
                    AnalysisScreen(
                        inputText = orchestrator.currentArticleText ?: "",
                        cachedResult = currentResult
                    )
                )
                navigator.push(ChatScreen())
            }
        }

        val doNavigateToReport: () -> Unit = {
            if (isReengagement) {
                navigator.pop()
            } else {
                orchestrator.consumeFreshAnalysis()
                navigator.replace(
                    AnalysisScreen(
                        inputText = orchestrator.currentArticleText ?: "",
                        cachedResult = currentResult
                    )
                )
            }
        }

        var sliderValue by rememberSaveable { mutableStateOf(5f) }
        var isSliderTouched by rememberSaveable { mutableStateOf(false) }
        val scrollState = rememberScrollState()

        var pendingNavigation by rememberSaveable(stateSaver = PendingNavigationSaver) {
            mutableStateOf<PendingNavigation?>(null)
        }

        val showNavigationLoader = pendingNavigation != null && currentResult.isFallaciesLoading

        LaunchedEffect(currentResult.isFallaciesLoading) {
            if (!currentResult.isFallaciesLoading) {
                val pending = pendingNavigation
                if (pending != null) {
                    when (pending) {
                        is PendingNavigation.Rating ->
                            doNavigateToChat(SocraticSession(userObjectivityRating = pending.rating))
                        is PendingNavigation.Question ->
                            doNavigateToChat(SocraticSession(selectedQuestionIndex = pending.index))
                        is PendingNavigation.Stance ->
                            doNavigateToChat(SocraticSession(userStance = pending.stance))
                    }
                    pendingNavigation = null
                }
            }
        }

        Scaffold(
            topBar = {
                Surface(
                    shadowElevation = MaterialTheme.analyticalColors.appBarElevation,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    CenterAlignedTopAppBar(
                        title = { AppBarTitle("Socratic Learning") },
                        actions = {
                            IconButton(onClick = doNavigateToReport) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Skip challenge and view report",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrimaryButton(
                            text = "Submit Assessment & Discuss",
                            onClick = {
                                if (currentResult.isFallaciesLoading) {
                                    pendingNavigation = PendingNavigation.Rating(sliderValue.toInt())
                                } else {
                                    doNavigateToChat(SocraticSession(userObjectivityRating = sliderValue.toInt()))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = isSliderTouched
                        )

                        OutlinedButton(
                            onClick = doNavigateToReport,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Skip to Detailed Report", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Executive Summary Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.analyticalColors.brightTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Executive Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentResult.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Objectivity assessment slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "🧠 Cognitive Challenge: Evaluate Objectivity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Before viewing the detailed scores, how objective do you think this article is?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Selected Rating: ${if (isSliderTouched) sliderValue.toInt().toString() else "Drag to select"}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSliderTouched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it; isSliderTouched = true },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Highly Biased (1)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Neutral (5)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Objective (10)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                // Stance Challenge
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "⚖️ Stance Challenge: Agree or Disagree?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Do you generally agree or disagree with the core arguments presented in this article?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (currentResult.isFallaciesLoading) {
                                    pendingNavigation = PendingNavigation.Stance("disagree")
                                } else {
                                    doNavigateToChat(SocraticSession(userStance = "disagree"))
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.analyticalColors.brightRed,
                                contentColor = Color.White
                            )
                        ) {
                            Text("I Disagree", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = {
                                if (currentResult.isFallaciesLoading) {
                                    pendingNavigation = PendingNavigation.Stance("agree")
                                } else {
                                    doNavigateToChat(SocraticSession(userStance = "agree"))
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.analyticalColors.brightTeal,
                                contentColor = Color.White
                            )
                        ) {
                            Text("I Agree", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                // Deep-dive questions (secondary path)
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (currentResult.socraticQuestions.isNotEmpty()) {
                        Text(
                            text = "Or, explore a specific structural question:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        currentResult.socraticQuestions.forEachIndexed { index, question ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (currentResult.isFallaciesLoading) {
                                            pendingNavigation = PendingNavigation.Question(index, question)
                                        } else {
                                            doNavigateToChat(SocraticSession(selectedQuestionIndex = index))
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = question,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Select question",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else if (currentResult.isSocraticLoading) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Analyzing claims to formulate Socratic questions...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Deep-dive analytical questions are unavailable for this text.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showNavigationLoader) {
            Dialog(
                onDismissRequest = {
                    // Back button or timeout: cancel pending navigation and go straight to report
                    pendingNavigation = null
                    doNavigateToReport()
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Completing analysis...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Wrapping up the deep structural scan. You will enter the classroom automatically in a few seconds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        TextButton(onClick = {
                            pendingNavigation = null
                            doNavigateToReport()
                        }) {
                            Text("Skip to Report", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
