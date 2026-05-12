package org.medialiteracy.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.medialiteracy.ui.LocalThemeIsDark
import org.medialiteracy.ui.analyticalColors
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.medialiteracy.domain.MediaResource
import org.medialiteracy.domain.Tactic
import org.medialiteracy.domain.TacticCategory

import cafe.adriel.voyager.navigator.Navigator
import org.medialiteracy.ui.components.AppBarTitle
import org.medialiteracy.ui.LocalRootNavigator

class LearningScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigator = LocalRootNavigator.current ?: navigator
        val screenModel = rememberScreenModel { LearningScreenModel() }
        val useDarkTheme = LocalThemeIsDark.current
        
        val blueColor = MaterialTheme.analyticalColors.blue
        val tealColor = MaterialTheme.analyticalColors.teal
        val redColor = MaterialTheme.analyticalColors.red
        val purpleColor = MaterialTheme.analyticalColors.purple
        
        val actualRootNavigator = rootNavigator
        
        val curriculum by screenModel.curriculum.collectAsState()
        val resourcePortal by screenModel.resourcePortal.collectAsState()
        val isLoading by screenModel.isLoading.collectAsState()

        var topicsExpanded by remember { mutableStateOf(false) }
        var portalExpanded by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                Surface(
                    shadowElevation = MaterialTheme.analyticalColors.appBarElevation,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    CenterAlignedTopAppBar(
                        title = { AppBarTitle("Learning Hub") },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    val resourcesByCategory = remember(resourcePortal) {
                        resourcePortal?.resources?.groupBy { it.category } ?: emptyMap()
                    }

                    val topicCategories = listOf(
                        "Verification Tactics", "Analytical Frameworks", "AI & Algorithmic Literacy", 
                        "Data & Statistical Literacy", "Deepfakes & Synthetic Media", "Economic Incentives", 
                        "Information Ecosystems", "Linguistic Analysis", "Psychological Resilience", 
                        "Science Communication"
                    )
                    val portalCategories = listOf(
                        "Books", "Podcasts", "Courses", "Tools", "Games", "Fact-Checking", "Communities"
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 64.dp)
                    ) {
                        // --- SECTION 1: FALLACIES & BIASES (BLUE) ---
                        item(key = "section_fallacies_header") {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionPortalHeader("Fallacies & Biases", "Recognize flawed logic and mental shortcuts")
                        }
                        
                        item(key = "fallacies_hero") {
                            val heroTactics = curriculum?.categories?.flatMap { it.tactics }?.take(5) ?: emptyList()
                            HeroCarousel(heroTactics, color = blueColor) { tactic ->
                                actualRootNavigator.push(TacticsLibraryScreen(tactic.title))
                            }
                        }

                        curriculum?.categories?.let { categories ->
                            items(categories, key = { "curriculum_cat_${it.id}" }) { category ->
                                val (catColor, catIconColor) = if (category.id == "fallacies") {
                                    blueColor to MaterialTheme.analyticalColors.brightBlue
                                } else {
                                    purpleColor to MaterialTheme.analyticalColors.brightPurple
                                }
                                
                                CategoryCard(
                                    title = category.title,
                                    subtitle = "${category.tactics.size} entries",
                                    icon = if (category.id == "fallacies") Icons.Default.Gavel else Icons.Default.Psychology,
                                    color = catColor,
                                    iconColor = catIconColor
                                ) {
                                    actualRootNavigator.push(CategoryDetailScreen(category))
                                }
                            }
                        }

                        // --- SECTION 2: TOPICS (GREEN) ---
                        item(key = "section_topics_header") {
                            Spacer(modifier = Modifier.height(32.dp))
                            SectionPortalHeader("Core Topics", "Frameworks and tactics for active verification")
                        }

                        item(key = "topics_hero") {
                            val topicResources = resourcesByCategory.filterKeys { it in topicCategories }
                                .values.flatten().take(5)
                            // Core Topics Hero cards - Unified Teal
                            HeroResourceCarousel(topicResources, containerColor = tealColor, textColor = Color.White)
                        }

                        item(key = "topics_list_container") {
                            val topicsToDisplay = resourcesByCategory.filterKeys { it in topicCategories }.toList()
                            Column {
                                topicsToDisplay.take(3).forEach { (name, resources) ->
                                    CategoryCard(
                                        title = name,
                                        subtitle = "${resources.size} entries",
                                        icon = getIconForCategory(name),
                                        color = tealColor,
                                        iconColor = MaterialTheme.analyticalColors.brightTeal
                                    ) {
                                        actualRootNavigator.push(ResourceCategoryScreen(name, resources))
                                    }
                                }

                                AnimatedVisibility(
                                    visible = topicsExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        topicsToDisplay.drop(3).forEach { (name, resources) ->
                                            CategoryCard(
                                                title = name,
                                                subtitle = "${resources.size} resources",
                                                icon = getIconForCategory(name),
                                                color = tealColor,
                                                iconColor = MaterialTheme.analyticalColors.brightTeal
                                            ) {
                                                actualRootNavigator.push(ResourceCategoryScreen(name, resources))
                                            }
                                        }
                                    }
                                }
                                
                                if (topicsToDisplay.size > 3) {
                                    ExpandButton(
                                        expanded = topicsExpanded, 
                                        showText = "Show all Topics", 
                                        hideText = "Show Less"
                                    ) { topicsExpanded = !topicsExpanded }
                                }
                            }
                        }

                        // --- SECTION 3: RESOURCE PORTAL (RED) ---
                        item(key = "section_portal_header") {
                            Spacer(modifier = Modifier.height(32.dp))
                            SectionPortalHeader("Resource Portal", "Deeper learning through books, media, and tools")
                        }

                        item(key = "portal_hero") {
                            val portalHero = resourcesByCategory.filterKeys { it in portalCategories }
                                .values.flatten().shuffled().take(5)
                            // Resource Portal Hero cards - Darker Red for better contrast
                            HeroResourceCarousel(portalHero, containerColor = redColor, textColor = Color.White)
                        }

                        item(key = "portal_list_container") {
                            val portalToDisplay = resourcesByCategory.filterKeys { it in portalCategories }.toList()
                            Column {
                                portalToDisplay.take(3).forEach { (name, resources) ->
                                    CategoryCard(
                                        title = name,
                                        subtitle = "${resources.size} entries",
                                        icon = getIconForCategory(name),
                                        color = redColor,
                                        iconColor = MaterialTheme.analyticalColors.brightRed
                                    ) {
                                        actualRootNavigator.push(ResourceCategoryScreen(name, resources))
                                    }
                                }

                                AnimatedVisibility(
                                    visible = portalExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        portalToDisplay.drop(3).forEach { (name, resources) ->
                                            CategoryCard(
                                                title = name,
                                                subtitle = "${resources.size} entries",
                                                icon = getIconForCategory(name),
                                                color = redColor,
                                                iconColor = MaterialTheme.analyticalColors.brightRed
                                            ) {
                                                actualRootNavigator.push(ResourceCategoryScreen(name, resources))
                                            }
                                        }
                                    }
                                }

                                if (portalToDisplay.size > 3) {
                                    ExpandButton(
                                        expanded = portalExpanded, 
                                        showText = "Show all Categories", 
                                        hideText = "Show Less"
                                    ) { portalExpanded = !portalExpanded }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandButton(
    expanded: Boolean, 
    showText: String, 
    hideText: String, 
    onClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expanded) hideText else showText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SectionPortalHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroCarousel(
    tactics: List<Tactic>, 
    color: Color = Color(0xFF3F51B5),
    onClick: (Tactic) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(tactics, key = { "hero_tactic_${it.title}" }) { tactic ->
            Card(
                onClick = { onClick(tactic) },
                modifier = Modifier
                    .width(280.dp)
                    .height(140.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = color),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(20.dp).align(Alignment.CenterStart)) {
                        Text(
                            tactic.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            tactic.definition,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.Star,
                        null,
                        modifier = Modifier.size(80.dp).align(Alignment.CenterEnd).offset(x = 20.dp).alpha(0.1f),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroResourceCarousel(
    resources: List<MediaResource>,
    containerColor: Color = Color.White,
    textColor: Color = Color.Black
) {
    val uriHandler = LocalUriHandler.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(resources, key = { "hero_res_${it.url}" }) { resource ->
            Card(
                onClick = { uriHandler.openUri(resource.url) },
                modifier = Modifier
                    .width(240.dp)
                    .height(120.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = if (containerColor == Color.White) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE)) else null
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(
                        resource.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        resource.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (textColor == Color.White) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    iconColor: Color = color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun getIconForCategory(category: String): ImageVector {
    return when {
        category.contains("Book", true) -> Icons.AutoMirrored.Filled.MenuBook
        category.contains("Podcast", true) -> Icons.Default.Podcasts
        category.contains("Course", true) -> Icons.Default.School
        category.contains("Tool", true) -> Icons.Default.Construction
        category.contains("Game", true) -> Icons.Default.Gamepad
        category.contains("Site", true) -> Icons.Default.Language
        category.contains("Fact", true) -> Icons.AutoMirrored.Filled.FactCheck
        category.contains("Community", true) -> Icons.Default.Groups
        category.contains("Verification", true) -> Icons.AutoMirrored.Filled.FactCheck
        category.contains("Framework", true) -> Icons.AutoMirrored.Filled.Rule
        category.contains("AI", true) -> Icons.Default.AutoAwesome
        category.contains("Deepfake", true) -> Icons.Default.Face
        category.contains("Data", true) -> Icons.Default.BarChart
        category.contains("Linguistic", true) -> Icons.Default.Translate
        category.contains("Propaganda", true) -> Icons.Default.Campaign
        category.contains("Resilience", true) -> Icons.Default.Shield
        category.contains("Science", true) -> Icons.Default.Science
        category.contains("Ecosystem", true) -> Icons.Default.Public
        category.contains("Economic", true) -> Icons.Default.Paid
        else -> Icons.AutoMirrored.Filled.Segment
    }
}

// Sub-screens remain for detail views
class TacticsLibraryScreen(private val initialQuery: String = "") : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LearningScreenModel() }
        val curriculum by screenModel.curriculum.collectAsState()
        var searchQuery by remember { mutableStateOf(initialQuery) }

        Scaffold(
            topBar = {
                val useDarkTheme = LocalThemeIsDark.current
                Surface(
                    shadowElevation = if (useDarkTheme) 0.dp else 16.dp,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    TopAppBar(
                        title = { AppBarTitle("Tactics Library") },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("Search tactics, fallacies...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    curriculum?.categories?.forEach { category ->
                        val filtered = category.tactics.filter { 
                            val normalizedTitle = it.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                            val normalizedQuery = searchQuery.lowercase().replace(Regex("[^a-z0-9]"), "")
                            
                            normalizedTitle.contains(normalizedQuery) || 
                            normalizedQuery.contains(normalizedTitle) ||
                            it.definition.contains(searchQuery, true)
                        }
                        if (filtered.isNotEmpty()) {
                            item(key = "cat_lib_header_${category.id}") { 
                                Text(
                                    category.title.uppercase(), 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                ) 
                            }
                            items(filtered, key = { it.title }) { tactic ->
                                TacticCardItem(tactic)
                            }
                        }
                    }
                }
            }
        }
    }
}

class CategoryDetailScreen(val category: TacticCategory) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var searchQuery by remember { mutableStateOf("") }

        Scaffold(
            topBar = {
                val useDarkTheme = LocalThemeIsDark.current
                Surface(
                    shadowElevation = if (useDarkTheme) 0.dp else 16.dp,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    CenterAlignedTopAppBar(
                        title = { AppBarTitle(category.title) },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("Search ${category.title.lowercase()}...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(category.tactics.filter { it.title.contains(searchQuery, true) }, key = { it.title }) { tactic ->
                        TacticCardItem(tactic)
                    }
                }
            }
        }
    }
}

class ResourceCategoryScreen(val categoryName: String, val resources: List<MediaResource>) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var searchQuery by remember { mutableStateOf("") }

        Scaffold(
            topBar = {
                val useDarkTheme = LocalThemeIsDark.current
                Surface(
                    shadowElevation = if (useDarkTheme) 0.dp else 16.dp,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    CenterAlignedTopAppBar(
                        title = { AppBarTitle(categoryName) },
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
            Column(modifier = Modifier.padding(padding)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("Search in $categoryName...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(resources.filter { it.title.contains(searchQuery, true) || it.description.contains(searchQuery, true) }, key = { it.url }) { resource ->
                        ResourceCardItem(resource)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TacticCardItem(tactic: Tactic) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tactic.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(tactic.definition, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("CANONICAL EXAMPLE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Text(tactic.canonical_example, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceCardItem(resource: MediaResource) {
    val uriHandler = LocalUriHandler.current
    Card(
        onClick = { uriHandler.openUri(resource.url) },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(resource.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(resource.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(resource.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(20.dp).padding(start = 8.dp))
        }
    }
}
