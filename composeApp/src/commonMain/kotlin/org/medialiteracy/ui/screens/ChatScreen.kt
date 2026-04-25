package org.medialiteracy.ui.screens

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

data class ChatScreen(val initialMessage: String? = null) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val orchestrator = rememberScreenModel { GemmaOrchestrator() }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        
        var messageText by remember { mutableStateOf("") }
        var isGenerating by remember { mutableStateOf(false) }
        var streamingResponse by remember { mutableStateOf("") }
        var hasOrientedToResponse by remember { mutableStateOf(false) }
        
        val messages = remember { 
            mutableStateListOf<ChatMessage>().apply {
                if (initialMessage != null) {
                    add(ChatMessage(initialMessage, true))
                } else {
                    add(ChatMessage("Hello, I am the Logic Master. How can I help you?", false))
                }
            }
        }

        fun sendMessage(text: String) {
            isGenerating = true
            streamingResponse = ""
            hasOrientedToResponse = false // Reset gravity for the new response
            
            orchestrator.generateChatResponse(
                text,
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

        LaunchedEffect(initialMessage) {
            if (initialMessage != null && messages.size == 1) {
                sendMessage(initialMessage)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Logic Master", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Gemma 4-E2B", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4DB6AC))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Column {
                        if (isGenerating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = Color(0xFF3F51B5))
                        }
                        Row(modifier = Modifier.padding(16.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                placeholder = { Text("Ask about logic...") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                enabled = !isGenerating,
                                colors = TextFieldDefaults.textFieldColors(containerColor = Color(0xFFF1F3F4), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = { if (messageText.isNotBlank() && !isGenerating) { val t = messageText; messages.add(ChatMessage(t, true)); messageText = ""; sendMessage(t) } },
                                containerColor = if (isGenerating) Color.LightGray else Color(0xFF3F51B5),
                                contentColor = Color.White,
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
                           Text("Master is reasoning...", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
                .background(if (msg.isUser) Color(0xFF3F51B5) else Color(0xFFF1F3F4))
                .padding(14.dp)
        ) {
            Text(msg.text, color = if (msg.isUser) Color.White else Color.Black, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}
