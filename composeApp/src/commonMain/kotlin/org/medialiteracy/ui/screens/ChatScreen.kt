package org.medialiteracy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class ChatScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var messageText by remember { mutableStateOf("") }
        val messages = remember { 
            mutableListOf(
                ChatMessage("Hello, I am the Logic Master. How can I help you deconstruct the rhetorical patterns in this article further?", false),
                ChatMessage("Could you explain the 'Straw Man' fallacy you detected specifically?", true),
                ChatMessage("Certainly. In the second paragraph, the author oversimplifies the opposition's stance on carbon taxes to make it easier to attack. This is a classic straw man.", false)
            )
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
                        IconButton(onClick = { navigator.pop() }) { Icon(Icons.Default.ArrowBack, "Back") }
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Ask about logic, bias, or debate...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.textFieldColors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                containerColor = Color(0xFFF1F3F4)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    messages.add(ChatMessage(messageText, true))
                                    messageText = ""
                                }
                            },
                            containerColor = Color(0xFF3F51B5),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Send, null)
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg)
                }
            }
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun ChatBubble(msg: ChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (msg.isUser) 16.dp else 0.dp,
                        bottomEnd = if (msg.isUser) 0.dp else 16.dp
                    )
                )
                .background(if (msg.isUser) Color(0xFF3F51B5) else Color(0xFFF8F9FA))
                .padding(12.dp)
        ) {
            Text(
                msg.text, 
                color = if (msg.isUser) Color.White else Color.Black,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}
