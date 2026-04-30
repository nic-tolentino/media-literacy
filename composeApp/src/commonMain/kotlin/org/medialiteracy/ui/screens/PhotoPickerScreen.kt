package org.medialiteracy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
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

class PhotoPickerScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coordinator = ServiceRegistry.analysisCoordinator

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Scan Newspaper", fontWeight = FontWeight.Bold) },
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
                        // SIMULATION: In a real app, this would capture camera bytes
                        val mockImage = ByteArray(1024) 
                        coordinator.startImageAnalysis(mockImage, "Scan of a newspaper front page about local election bias.")
                        navigator.push(AnalysisScreen(inputText = "[Image Content]"))
                    },
                    modifier = Modifier.size(200.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFF5F5F5),
                    border = BorderStroke(2.dp, Color.LightGray)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CameraAlt, 
                            contentDescription = "Capture",
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFF00796B)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "Capture an article to extract text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Gemma will use OCR to read and deconstruct the argument.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = { 
                        val mockImage = ByteArray(1024) 
                        coordinator.startImageAnalysis(mockImage, "Gallery photo of a political flyer.")
                        navigator.push(AnalysisScreen(inputText = "[Gallery Photo]"))
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Select from Gallery")
                }
            }
        }
    }
}
