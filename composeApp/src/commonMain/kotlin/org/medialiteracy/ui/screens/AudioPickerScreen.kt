package org.medialiteracy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Mic
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

import org.medialiteracy.domain.ServiceRegistry

class AudioPickerScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coordinator = ServiceRegistry.analysisCoordinator
        var isRecording by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Analyze Audio", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    onClick = { 
                        if (isRecording) {
                            // SIMULATION: Stop recording and analyze 40 seconds (2 chunks)
                            val mockAudio = ByteArray(40 * 16000 * 2) 
                            coordinator.startAudioAnalysis(mockAudio)
                            navigator.push(AnalysisScreen(inputText = "[Live Recording]"))
                        }
                        isRecording = !isRecording 
                    },
                    modifier = Modifier.size(160.dp),
                    shape = CircleShape,
                    color = if (isRecording) Color(0xFFFFEBEE) else Color(0xFFF5F5F5),
                    border = BorderStroke(2.dp, if (isRecording) Color.Red else Color.LightGray)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Mic, 
                            contentDescription = "Record",
                            modifier = Modifier.size(64.dp),
                            tint = if (isRecording) Color.Red else Color(0xFFC62828)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    if (isRecording) "Recording... Tap to stop" else "Record speech or broadcast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) Color.Red else Color.Black
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Analyze live audio for logical depth and tone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(64.dp))

                Button(
                    onClick = { 
                        // SIMULATION: Select a 30 second file
                        val mockAudio = ByteArray(30 * 16000 * 2) 
                        coordinator.startAudioAnalysis(mockAudio)
                        navigator.push(AnalysisScreen(inputText = "[Audio File]"))
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.AudioFile, null, tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Select Audio File", color = Color.White)
                }
            }
        }
    }
}
