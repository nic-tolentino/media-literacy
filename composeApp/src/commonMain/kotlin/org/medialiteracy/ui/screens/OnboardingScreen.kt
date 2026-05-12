package org.medialiteracy.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import org.medialiteracy.domain.EngineInternalState
import org.medialiteracy.domain.GemmaOrchestrator
import org.medialiteracy.domain.InferenceState
import org.medialiteracy.ui.tabs.TabHost
import org.medialiteracy.ui.components.PrimaryButton
import org.jetbrains.compose.resources.painterResource
import medialiteracy.composeapp.generated.resources.Res
import medialiteracy.composeapp.generated.resources.app_logo

class OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Header Logo
                    Image(
                        painter = painterResource(Res.drawable.app_logo),
                        contentDescription = "News Decoder Logo",
                        modifier = Modifier.size(120.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        "News Decoder",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "Your on-device companion for critical media analysis and truth discovery.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    FeatureHighlight(
                        icon = Icons.Default.Security,
                        title = "100% Private",
                        description = "Analysis happens entirely on your device. Your data never leaves your phone."
                    )
                    FeatureHighlight(
                        icon = Icons.Default.AutoAwesome,
                        title = "Socratic AI",
                        description = "Identify logical fallacies and bias using advanced on-device AI models."
                    )
                    FeatureHighlight(
                        icon = Icons.Default.CloudDownload,
                        title = "Offline Analysis",
                        description = "Once models are downloaded, you can analyze media anywhere, even without internet."
                    )
                    FeatureHighlight(
                        icon = Icons.Default.Refresh,
                        title = "Free & Curated",
                        description = "Totally free to use. Includes a curated library of media literacy learning resources."
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PrimaryButton(
                        text = "Get Started",
                        onClick = { 
                            scope.launch {
                                org.medialiteracy.domain.SettingsRepository.getInstance().setHasCompletedOnboarding(true)
                                navigator.replaceAll(TabHost())
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        "VERSION 1.0 · BETA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureHighlight(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
