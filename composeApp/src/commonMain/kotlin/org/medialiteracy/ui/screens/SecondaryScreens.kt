package org.medialiteracy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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

class LearningScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Learning Hub", fontWeight = FontWeight.Bold) }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Master the art of deconstruction.",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    ChallengeCard()
                }

                item {
                    Text("Foundations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                item {
                    ModuleItem(Icons.Default.BugReport, "Identifying Fallacies", "Learn to spot 15 common logical errors.")
                }
                item {
                    ModuleItem(Icons.Default.Balance, "Rhetorical Balance", "Understand how 'Steel-manning' improves debate.")
                }
                item {
                    ModuleItem(Icons.Default.Public, "Media Landscapes", "How algorithms shape your information diet.")
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun ChallengeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3F51B5))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("DAILY CHALLENGE", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("Spot the 'Straw Man'", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("A 2-minute quiz to sharpen your cognitive defenses.", color = Color.White.copy(alpha = 0.9f))
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {}, 
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3F51B5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Challenge")
            }
        }
    }
}

@Composable
fun ModuleItem(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8F9FA))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF4DB6AC), modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
         Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
                Text("Model Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingRow(Icons.Default.Storage, "Gemma 4-E2B-it", "Version 1.2 (Active)")
                SettingRow(Icons.Default.History, "Auto-clear History", "After 24 hours")
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text("Privacy & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingRow(Icons.Default.VpnKey, "Biometric Lock", "Disabled")
                SettingRow(Icons.Default.CloudOff, "External Access", "Always Blocked")
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = { /* TODO */ }, 
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Model Weights")
                }
                Text(
                    "This action cannot be undone. You will need to re-download the 1.2GB model to resume use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Color(0xFF3F51B5))
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
    }
}
