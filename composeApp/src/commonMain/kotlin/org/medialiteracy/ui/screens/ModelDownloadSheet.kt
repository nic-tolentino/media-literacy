package org.medialiteracy.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.medialiteracy.domain.*
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import org.medialiteracy.ui.components.PrimaryButton

@Composable
fun ModelDownloadSheet(
    orchestrator: GemmaOrchestrator,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val downloadState by orchestrator.downloadState.collectAsState()
    val recommended = remember { DeviceCapabilityChecker.recommendedVariant() }
    var selectedVariant by remember { mutableStateOf(recommended) }

    val freeSpaceGb by produceState(0.0) {
        value = orchestrator.availableDiskBytes() / (1024.0 * 1024.0 * 1024.0)
    }

    Dialog(
        onDismissRequest = { if (downloadState !is DownloadState.Downloading) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isOnline = orchestrator.isOnline()
                
                when (val state = downloadState) {
                    is DownloadState.Idle, is DownloadState.Offline -> {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "Unlock AI Analysis",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            "News Decoder analyses articles using a private AI model stored on your device. Nothing ever leaves your phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            "Recommended for your device:",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        VariantOption(
                            variant = ModelVariant.E4B,
                            isSelected = selectedVariant == ModelVariant.E4B,
                            isRecommended = recommended == ModelVariant.E4B,
                            onClick = { selectedVariant = ModelVariant.E4B }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        VariantOption(
                            variant = ModelVariant.E2B,
                            isSelected = selectedVariant == ModelVariant.E2B,
                            isRecommended = recommended == ModelVariant.E2B,
                            onClick = { selectedVariant = ModelVariant.E2B }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Free space on device:", 
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${"%.1f".format(freeSpaceGb)} GB", 
                                style = MaterialTheme.typography.bodySmall, 
                                fontWeight = FontWeight.Bold,
                                color = if (freeSpaceGb < selectedVariant.approximateSizeGb) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        if (!isOnline) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "No internet connection", 
                                        style = MaterialTheme.typography.bodySmall, 
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        PrimaryButton(
                            text = "Download ${selectedVariant.approximateSizeGb} GB",
                            onClick = { orchestrator.startModelDownload(selectedVariant) },
                            enabled = orchestrator.canStartDownload(selectedVariant, freeSpaceGb),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        )
                        
                        TextButton(
                            onClick = onDismiss, 
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    is DownloadState.Downloading, is DownloadState.Paused -> {
                        val progress = if (state is DownloadState.Downloading) state.progressFraction else (state as DownloadState.Paused).progressFraction
                        val currentMb = if (state is DownloadState.Downloading) state.bytesDownloaded / (1024 * 1024) else (state as DownloadState.Paused).bytesDownloaded / (1024 * 1024)
                        val totalMb = if (state is DownloadState.Downloading) state.totalBytes / (1024 * 1024) else (state as DownloadState.Paused).totalBytes / (1024 * 1024)
                        
                        Text(
                            if (state is DownloadState.Paused) "Download paused (Network loss)" else "Downloading ${selectedVariant.displayName}...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${currentMb} MB / ${totalMb} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state is DownloadState.Downloading) {
                                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        OutlinedButton(
                            onClick = { orchestrator.cancelDownload() }, 
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Cancel Download")
                        }
                    }
                    
                    is DownloadState.Verifying -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Verifying file integrity...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("This will only take a moment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    is DownloadState.Complete -> {
                        Icon(
                            Icons.Default.CheckCircle, 
                            null, 
                            tint = MaterialTheme.colorScheme.primary, 
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("AI Engine Ready", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Starting your analysis experience...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        LaunchedEffect(Unit) {
                            delay(1500)
                            onComplete()
                        }
                    }
                    
                    is DownloadState.Failed -> {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Download failed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(state.reason, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        PrimaryButton(
                            text = "Retry",
                            onClick = { orchestrator.startModelDownload(selectedVariant) },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        )
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantOption(
    variant: ModelVariant,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected, 
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        variant.displayName, 
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "RECOMMENDED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (variant == ModelVariant.E4B) "Higher accuracy & reasoning" else "Faster analysis, uses less storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${variant.approximateSizeGb} GB", 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
