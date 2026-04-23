package org.medialiteracy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen

class ChatScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Logic Master Chat", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.weight(1f))
            
            Card(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("Gemma: How can I help you deconstruct this news further?", modifier = Modifier.padding(8.dp))
            }
            
            Row(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about the logic...") }
                )
                Button(onClick = { /* TODO */ }, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Send")
                }
            }
        }
    }
}
