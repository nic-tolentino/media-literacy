package org.medialiteracy.ui.screens
import org.medialiteracy.ui.components.AppBarTitle
import org.medialiteracy.ui.analyticalColors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.core.model.rememberScreenModel
import org.medialiteracy.domain.GemmaOrchestrator
import org.medialiteracy.ui.LocalRootNavigator

class PasteInputScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigator = LocalRootNavigator.current ?: navigator
        var text by remember { mutableStateOf("") }
        
        val actualNavigator = rootNavigator

        val orchestrator = rememberScreenModel { GemmaOrchestrator() }

        Scaffold(
            topBar = {
                Surface(
                    shadowElevation = MaterialTheme.analyticalColors.appBarElevation,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    TopAppBar(
                        title = { AppBarTitle("Paste Content") },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Article Text",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        "TEST SAMPLES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(SampleArticles.all) { article ->
                        AssistChip(
                            onClick = { text = article.content },
                            label = { Text(article.title) },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.primary
                              ),
                              border = AssistChipDefaults.assistChipBorder(
                                  borderColor = MaterialTheme.colorScheme.primary,
                                  borderWidth = 1.dp
                              )
                          )
                      }
                  }
                  
                  OutlinedTextField(
                      value = text,
                      onValueChange = { text = it },
                      modifier = Modifier
                          .fillMaxWidth()
                          .weight(1f),
                      placeholder = { 
                          Text("Paste the full text of the article or speech you want to analyze...") 
                      },
                      shape = RoundedCornerShape(16.dp),
                      colors = TextFieldDefaults.outlinedTextFieldColors(
                          focusedBorderColor = MaterialTheme.colorScheme.primary,
                          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                      )
                  )
  
                  Spacer(modifier = Modifier.height(24.dp))
  
                  Button(
                      onClick = {
                          if (text.isNotBlank()) {
                              orchestrator.reset()
                              // Use replace so backing out from AnalysisScreen goes to Home
                              actualNavigator.replace(AnalysisScreen(text))
                          }
                      },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Deconstruct with Gemma", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
