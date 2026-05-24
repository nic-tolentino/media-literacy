package org.medialiteracy.ui.screens
import org.medialiteracy.ui.components.AppBarTitle
import org.medialiteracy.ui.analyticalColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import org.medialiteracy.domain.GemmaOrchestrator
import org.medialiteracy.domain.AnalysisResult

data class ChatScreen(
    val articleText: String? = null,
    val analysisResult: AnalysisResult? = null,
    val initialMessage: String? = null
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val orchestrator = rememberScreenModel { GemmaOrchestrator() }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        val activeResult = analysisResult ?: orchestrator.currentAnalysisResult
        val activeArticleText = articleText ?: orchestrator.currentArticleText
        val socraticSessionState by orchestrator.socraticSession.collectAsState()
        
        var messageText by remember { mutableStateOf("") }
        var isGenerating by remember { mutableStateOf(false) }
        var streamingResponse by remember { mutableStateOf("") }
        var hasOrientedToResponse by remember { mutableStateOf(false) }
        
        val messages = remember { 
            mutableStateListOf<ChatMessage>().apply {
                if (socraticSessionState?.selectedQuestionIndex != null) {
                    // Question is initiated via system prompt to guide user self-reflection
                } else if (socraticSessionState?.userObjectivityRating != null) {
                    // Rating is sent silently as a system prompt, so we do not render a user bubble
                } else if (socraticSessionState?.userStance != null) {
                    // Stance is sent silently as a system prompt, so we do not render a user bubble
                } else {
                    if (initialMessage != null) {
                        add(ChatMessage(initialMessage, true))
                    } else {
                        add(ChatMessage("How can I help you analyze the logic and evidence in this article?", false))
                    }
                }
            }
        }

        fun sendMessage(text: String) {
            isGenerating = true
            streamingResponse = ""
            hasOrientedToResponse = false // Reset gravity for the new response
            
            orchestrator.generateChatResponse(
                userMessage = text,
                onUpdate = { partial -> 
                    streamingResponse = partial
                    
                    // Trigger gravity ONLY on the first tokens to orient the user
                    if (!hasOrientedToResponse && partial.length > 5) {
                        scope.launch {
                            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            hasOrientedToResponse = true 
                        }
                    }
                },
                onComplete = { final ->
                    messages.add(ChatMessage(final, false))
                    streamingResponse = ""
                    isGenerating = false
                }
            )
        }

        LaunchedEffect(Unit) {
            val session = socraticSessionState
            if (session?.selectedQuestionIndex != null) {
                val question = activeResult?.socraticQuestions?.getOrNull(session.selectedQuestionIndex)
                if (question != null && messages.isEmpty()) {
                    val prompt = "Start the classroom conversation. Ask the user for their thoughts on this question: \"$question\". Do not answer it yourself. Keep the message short and in a warm, welcoming Socratic tone."
                    sendMessage(prompt)
                }
            } else if (session?.userObjectivityRating != null) {
                val userRating = session.userObjectivityRating
                if (messages.isEmpty()) {
                    val prompt = "Start the classroom conversation. Acknowledge that the user rated the article's objectivity as $userRating/10. Ask them to explain the reasoning behind their rating. Do not mention any AI score. Keep the message short and Socratic."
                    sendMessage(prompt)
                }
            } else if (session?.userStance != null) {
                val stance = session.userStance
                if (messages.isEmpty()) {
                    val prompt = "Start the classroom conversation. Acknowledge that the user generally $stance with the article. Challenge them to examine the merits or potential flaws of this position, and ask how the evidence could be improved. Keep the message short and Socratic."
                    sendMessage(prompt)
                }
            } else if (initialMessage != null && messages.size == 1) {
                sendMessage(initialMessage)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                orchestrator.updateSocraticSession(null)
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
                        title = { AppBarTitle("Classroom") },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        },
                        actions = {
                            if (activeResult != null) {
                                TextButton(
                                    onClick = { navigator.pop() }
                                ) {
                                    Text(
                                        text = "View Report",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Column {
                        if (isGenerating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.padding(16.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                placeholder = { Text("Ask about logic...") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                enabled = !isGenerating,
                                // TODO: migrate to TextFieldDefaults.colors() once min Material3 version is bumped
                colors = @Suppress("DEPRECATION") TextFieldDefaults.textFieldColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = { if (messageText.isNotBlank() && !isGenerating) { val t = messageText; messages.add(ChatMessage(t, true)); messageText = ""; sendMessage(t) } },
                                containerColor = if (isGenerating) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                                contentColor = if (isGenerating) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) { Icon(Icons.AutoMirrored.Filled.Send, null) }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 16.dp)
            ) {
                items(messages) { msg -> ChatBubble(msg) }
                if (streamingResponse.isNotEmpty()) {
                    item { ChatBubble(ChatMessage(streamingResponse, false)) }
                }
                if (isGenerating && streamingResponse.isEmpty()) {
                    item { 
                        Box(Modifier.padding(start = 12.dp)) {
                           Text("Gemma is reasoning...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun ChatBubble(msg: ChatMessage) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentWidth(if (msg.isUser) Alignment.End else Alignment.Start)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (msg.isUser) 16.dp else 0.dp, bottomEnd = if (msg.isUser) 0.dp else 16.dp))
                .background(if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp)
        ) {
            Text(msg.text, color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}
