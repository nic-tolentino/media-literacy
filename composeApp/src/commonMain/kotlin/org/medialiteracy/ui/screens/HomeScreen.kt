package org.medialiteracy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class HomeScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        
        var inputText by remember { mutableStateOf("") }
        
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Gemma4ML Logic Intake", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = { /* TODO: Camera */ }, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Capture News Photo (OCR)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(onClick = { /* TODO: Audio */ }, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Record Radio/Speech")
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Paste news text here...") },
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { navigator.push(AnalysisScreen(inputText)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = inputText.isNotBlank()
            ) {
                Text("Start Logic Analysis")
            }
        }
    }
}
