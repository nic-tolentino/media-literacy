package org.medialiteracy.ui.screens
import org.medialiteracy.ui.components.AppBarTitle

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

class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
         Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { AppBarTitle("Settings") }
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Model Weights", color = Color.White)
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
