package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import com.example.R
import com.example.models.LiveCategory
import com.example.models.LiveStream
import com.example.models.FirebaseChannel
import com.example.viewmodel.XtreamViewModel

@Composable
fun IPTVDashboard(
    viewModel: XtreamViewModel,
    modifier: Modifier = Modifier
) {
    val isActivated by viewModel.isActivated.collectAsState()

    // Gate Access control screen
    if (!isActivated) {
        ActivationScreen(viewModel = viewModel, modifier = modifier)
        return
    }

    val activeSection by viewModel.currentSection.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val vodCategories by viewModel.vodCategories.collectAsState()
    val seriesCategories by viewModel.seriesCategories.collectAsState()
    val fbCategories by viewModel.fbCategories.collectAsState()

    // Map correct category list per section
    val activeCategories = remember(activeSection, categories, vodCategories, seriesCategories, fbCategories) {
        when (activeSection) {
            "movies" -> vodCategories
            "series" -> seriesCategories
            "main_channels" -> fbCategories.map { LiveCategory(it.id, it.name) }
            else -> categories
        }
    }

    val filteredStreams by viewModel.filteredStreams.collectAsState()
    val filteredFbChannels by viewModel.filteredFbChannels.collectAsState()
    
    val selectedCategory by when (activeSection) {
        "movies" -> viewModel.selectedVodCategoryId.collectAsState()
        "series" -> viewModel.selectedSeriesCategoryId.collectAsState()
        "main_channels" -> viewModel.selectedFbCategoryId.collectAsState()
        else -> viewModel.selectedCategoryId.collectAsState()
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    val currentChannel by viewModel.currentPlayingChannel.collectAsState()
    val currentFbChannel by viewModel.currentPlayingFirebaseChannel.collectAsState()
    val favorites by viewModel.favoriteChannels.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showDeveloperControl by remember { mutableStateOf(false) }

    // Dynamic Centralized Active Theme Colors
    val activeTheme by viewModel.currentTheme.collectAsState()
    val primaryColor = remember(activeTheme) {
        when (activeTheme) {
            "netflix" -> Color(0xFFE50914)
            "midnight" -> Color(0xFF00ADB5)
            "sunset" -> Color(0xFFBB86FC)
            "emerald" -> Color(0xFF03C988)
            else -> Color(0xFFFF9800)
        }
    }
    val darkBgColor = remember(activeTheme) {
        when (activeTheme) {
            "netflix" -> Color(0xFF000000)
            "midnight" -> Color(0xFF0F172A)
            "sunset" -> Color(0xFF191122)
            "emerald" -> Color(0xFF080F0C)
            else -> Color(0xFF0F1016)
        }
    }
    val sidebarBgColor = remember(activeTheme) {
        when (activeTheme) {
            "netflix" -> Color(0xFF100304)
            "midnight" -> Color(0xFF0B101E)
            "sunset" -> Color(0xFF110A1A)
            "emerald" -> Color(0xFF040806)
            else -> Color(0xFF0A0B10)
        }
    }

    val appBgGradient = Brush.verticalGradient(
        colors = listOf(darkBgColor, darkBgColor.copy(alpha = 0.9f))
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appBgGradient)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTVOrWide = maxWidth >= 600.dp || maxHeight < 480.dp

            if (isTVOrWide) {
                // TV CINEMA COMPATIBLE SPLIT LAYOUT
                IPTVTVLayout(
                    viewModel = viewModel,
                    activeSection = activeSection,
                    categories = activeCategories,
                    filteredStreams = filteredStreams,
                    filteredFbChannels = filteredFbChannels,
                    selectedCatId = selectedCategory,
                    query = searchQuery,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    favorites = favorites.map { it.streamId }.toSet(),
                    onOpenSettings = { showSettings = true },
                    primaryColor = primaryColor,
                    sidebarBgColor = sidebarBgColor
                )
            } else {
                // COMPACT MOBILE PHONE LAYOUT
                IPTVMobileLayout(
                    viewModel = viewModel,
                    activeSection = activeSection,
                    categories = activeCategories,
                    filteredStreams = filteredStreams,
                    filteredFbChannels = filteredFbChannels,
                    selectedCatId = selectedCategory,
                    query = searchQuery,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    favorites = favorites.map { it.streamId }.toSet(),
                    onOpenSettings = { showSettings = true },
                    primaryColor = primaryColor
                )
            }
        }

        // Landscape Fullscreen Video Player for Xtream standard codes
        currentChannel?.let { channel ->
            IPTVPlayer(
                channel = channel,
                fbChannel = null,
                viewModel = viewModel,
                onClose = { viewModel.selectChannel(null) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Fullscreen Player for Firebase custom streams
        currentFbChannel?.let { desc ->
            IPTVPlayer(
                channel = null,
                fbChannel = desc,
                viewModel = viewModel,
                onClose = { viewModel.selectFirebaseChannel(null) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Netflix Cinematic Detail overlay panel for Movies & Series details
        val currentDetailStream by viewModel.currentDetailStream.collectAsState()
        currentDetailStream?.let {
            CinemaDetailOverlay(
                viewModel = viewModel,
                primaryColor = primaryColor,
                onClose = { viewModel.setDetailStream(null) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // App Settings Drawer (Slide-over side drawer taking half-screen width)
        SettingsDrawerOverlay(
            isOpen = showSettings,
            viewModel = viewModel,
            primaryColor = primaryColor,
            onOpenDeveloperConsole = {
                showSettings = false
                showDeveloperControl = true
            },
            onDismiss = { showSettings = false }
        )

        // Developer Admin Control Dashboard Screen Overlay
        if (showDeveloperControl) {
            DeveloperDashboard(
                viewModel = viewModel,
                onClose = { showDeveloperControl = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun IPTVTVLayout(
    viewModel: XtreamViewModel,
    activeSection: String,
    categories: List<LiveCategory>,
    filteredStreams: List<LiveStream>,
    filteredFbChannels: List<FirebaseChannel>,
    selectedCatId: String,
    query: String,
    isLoading: Boolean,
    errorMessage: String?,
    favorites: Set<Int>,
    onOpenSettings: () -> Unit,
    primaryColor: Color,
    sidebarBgColor: Color
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // TV Sidebar
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(sidebarBgColor)
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            val uriHandler = LocalUriHandler.current
            
            // App Name Inside Sidebar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = "Logo",
                    tint = primaryColor,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Loop Live",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = viewModel.translate("tv_edition"),
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Telegram support Button
            var isTelegramFocused by remember { mutableStateOf(false) }
            val telegramBg = if (isTelegramFocused) Color(0xFF24A1DE).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(telegramBg)
                    .onFocusChanged { state -> isTelegramFocused = state.isFocused }
                    .focusable()
                    .clickable {
                        try {
                            uriHandler.openUri("https://t.me/+zFTWopL4zedlNTJi")
                        } catch (e: Exception) {}
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_telegram),
                    contentDescription = "Telegram",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = viewModel.translate("telegram"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = viewModel.translate("telegram_desc"),
                        color = Color(0xFF24A1DE),
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val isYtEnabled by viewModel.isYoutubeEnabled.collectAsState()
            val tvSections = remember(isYtEnabled) {
                val list = mutableListOf(
                    Triple("live", Icons.Default.LiveTv, viewModel.translate("live_tv")),
                    Triple("movies", Icons.Default.Movie, viewModel.translate("movies")),
                    Triple("series", Icons.Default.Tv, viewModel.translate("series"))
                )
                if (isYtEnabled) {
                    list.add(Triple("youtube", Icons.Default.PlayCircleFilled, "يوتيوب"))
                }
                list.add(Triple("main_channels", Icons.Default.RssFeed, viewModel.translate("main_channels")))
                list
            }

            // TV SECTION ROUTE CONTROLLERS inside Sidebar (Live TV, Movies, Series, YouTube, Firebase Online)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tvSections.forEach { (sec, icon, label) ->
                    var isSFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { viewModel.setSection(sec) },
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (activeSection == sec) primaryColor else if (isSFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { isSFocused = it.isFocused }
                    ) {
                        Icon(imageVector = icon, contentDescription = label, tint = if (activeSection == sec) Color.White else Color.LightGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Virtual Category list per selected Section
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (activeSection != "youtube") {
                    item {
                        TVSidebarItem(
                            label = viewModel.translate("all_channels"),
                            icon = Icons.Default.LiveTv,
                            isSelected = selectedCatId == "all",
                            onSelect = { viewModel.selectCategory("all") },
                            primaryColor = primaryColor
                        )
                    }
                    
                    if (activeSection != "main_channels") {
                        item {
                            TVSidebarItem(
                                label = viewModel.translate("favs"),
                                icon = Icons.Default.Favorite,
                                isSelected = selectedCatId == "favorites",
                                onSelect = { viewModel.selectCategory("favorites") },
                                primaryColor = primaryColor
                            )
                        }
                    }

                    item {
                        Text(
                            text = viewModel.translate("categories_header"),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    items(categories) { cat ->
                        TVSidebarItem(
                            label = cat.categoryName,
                            icon = Icons.Default.Category,
                            isSelected = selectedCatId == cat.categoryId,
                            onSelect = { viewModel.selectCategory(cat.categoryId) },
                            primaryColor = primaryColor
                        )
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "دعم يوتيوب الذكي 🍿",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "يمكنك البحث عن الفيديوهات وبثها مباشرة، أو تنزيلها لمشاهدتها في أي وقت لاحق بدون اتصال بالإنترنت.",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // settings bottom buttons
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (activeSection == "movies") viewModel.loadVodData()
                        else if (activeSection == "series") viewModel.loadSeriesData()
                        else if (activeSection == "main_channels") viewModel.loadFirebaseData()
                        else viewModel.loadData()
                    },
                    modifier = Modifier.background(Color(0xFF1F1F2F), RoundedCornerShape(8.dp))
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh lists", tint = Color.LightGray)
                }
                
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.background(Color(0xFF1F1F2F), RoundedCornerShape(8.dp))
                ) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Configure connection", tint = Color.LightGray)
                }
            }
        }

        // Content Frame
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleString = when (selectedCatId) {
                    "all" -> "${viewModel.translate("live_tv")} (${viewModel.translate("all_channels")})"
                    "favorites" -> viewModel.translate("favs")
                    "recents" -> viewModel.translate("history")
                    else -> categories.firstOrNull { it.categoryId == selectedCatId }?.categoryName ?: "المحتوى"
                }

                Text(
                    text = "${viewModel.translate(activeSection)} : $titleString",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search box
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text(viewModel.translate("search_hint"), fontSize = 13.sp, color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = Color(0xFF1E1E2E),
                            unfocusedContainerColor = Color(0xFF111116),
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .width(260.dp)
                            .height(48.dp)
                    )

                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Menu, contentDescription = "Settings", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card list block
            val currentGridColumns by viewModel.gridColumns.collectAsState()
            
            if (activeSection == "youtube") {
                YouTubeSectionContent(
                    viewModel = viewModel,
                    gridColsList = currentGridColumns,
                    primaryColor = primaryColor
                )
            } else if (activeSection == "main_channels") {
                FirebaseChannelContentList(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    channels = filteredFbChannels,
                    gridColsList = currentGridColumns,
                    viewModel = viewModel,
                    primaryColor = primaryColor
                )
            } else {
                ChannelContentList(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    streams = filteredStreams,
                    favorites = favorites,
                    gridCells = GridCells.Fixed(currentGridColumns),
                    viewModel = viewModel,
                    aspectRatio = if (activeSection == "movies" || activeSection == "series") 0.7f else 1.6f,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

@Composable
fun TVSidebarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    primaryColor: Color
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val bg = when {
        isSelected -> primaryColor
        isFocused -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    
    val textColor = when {
        isSelected -> Color.White
        isFocused -> primaryColor
        else -> Color.LightGray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .onFocusChanged { state -> 
                isFocused = state.isFocused
                if (state.isFocused) {
                    onSelect()
                }
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && 
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                    onSelect()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun IPTVMobileLayout(
    viewModel: XtreamViewModel,
    activeSection: String,
    categories: List<LiveCategory>,
    filteredStreams: List<LiveStream>,
    filteredFbChannels: List<FirebaseChannel>,
    selectedCatId: String,
    query: String,
    isLoading: Boolean,
    errorMessage: String?,
    favorites: Set<Int>,
    onOpenSettings: () -> Unit,
    primaryColor: Color
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0B10))
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                val uriHandler = LocalUriHandler.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = "Logo",
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loop Live",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E2E))
                                .clickable {
                                    try {
                                        uriHandler.openUri("https://t.me/+zFTWopL4zedlNTJi")
                                    } catch (e: Exception) {}
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_telegram),
                                contentDescription = "Telegram",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = viewModel.translate("telegram"),
                                color = Color(0xFF24A1DE),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Menu, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                }

                if (activeSection != "youtube") {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Box
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text(viewModel.translate("search_hint"), fontSize = 14.sp, color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = Color(0xFF1E1E2E),
                            unfocusedContainerColor = Color(0xFF12121A),
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Lazy categories list
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        item {
                            MobileCategoryTab(
                                label = viewModel.translate("all_channels"),
                                isSelected = selectedCatId == "all",
                                onSelect = { viewModel.selectCategory("all") },
                                primaryColor = primaryColor
                            )
                        }

                        if (activeSection != "main_channels") {
                            item {
                                MobileCategoryTab(
                                    label = viewModel.translate("favs"),
                                    isSelected = selectedCatId == "favorites",
                                    onSelect = { viewModel.selectCategory("favorites") },
                                    primaryColor = primaryColor
                                )
                            }
                        }

                        items(categories) { cat ->
                            MobileCategoryTab(
                                label = cat.categoryName,
                                isSelected = selectedCatId == cat.categoryId,
                                onSelect = { viewModel.selectCategory(cat.categoryId) },
                                primaryColor = primaryColor
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color(0xFF090A0E),
                contentColor = Color.White,
                modifier = Modifier.height(64.dp)
            ) {
                val isYtEnabled by viewModel.isYoutubeEnabled.collectAsState()
                val bottomItems = remember(isYtEnabled) {
                    val list = mutableListOf(
                        Triple("live", Icons.Default.LiveTv, viewModel.translate("live_tv")),
                        Triple("library", Icons.Default.MovieFilter, "مكتبة"),
                        Triple("main_favorites", Icons.Default.Favorite, "مفضلتي")
                    )
                    if (isYtEnabled) {
                        list.add(Triple("youtube", Icons.Default.PlayCircleFilled, "يوتيوب"))
                    }
                    list.add(Triple("main_channels", Icons.Default.RssFeed, viewModel.translate("main_channels")))
                    list
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomItems.forEach { (sec, icon, label) ->
                        val isSel = when (sec) {
                            "library" -> activeSection == "movies" || activeSection == "series"
                            "main_favorites" -> activeSection == "live" && selectedCatId == "favorites"
                            else -> activeSection == sec
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    when (sec) {
                                        "library" -> viewModel.setSection("movies")
                                        "main_favorites" -> {
                                            viewModel.setSection("live")
                                            viewModel.selectCategory("favorites")
                                        }
                                        else -> viewModel.setSection(sec)
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSel) primaryColor else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = if (isSel) primaryColor else Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            val gridColumnsCount by viewModel.gridColumns.collectAsState()
            
            if (activeSection == "youtube") {
                YouTubeSectionContent(
                    viewModel = viewModel,
                    gridColsList = gridColumnsCount,
                    primaryColor = primaryColor
                )
            } else {
                if (activeSection == "movies" || activeSection == "series") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF151622))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val isMovieSelected = activeSection == "movies"
                        Button(
                            onClick = { viewModel.setSection("movies") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMovieSelected) primaryColor else Color.Transparent
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "الافلام 🎬",
                                color = if (isMovieSelected) Color.White else Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.setSection("series") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isMovieSelected) primaryColor else Color.Transparent
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "المسلسلات 📺",
                                color = if (!isMovieSelected) Color.White else Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (activeSection == "main_channels") {
                    FirebaseChannelContentList(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        channels = filteredFbChannels,
                        gridColsList = gridColumnsCount,
                        viewModel = viewModel,
                        primaryColor = primaryColor
                    )
                } else {
                    ChannelContentList(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        streams = filteredStreams,
                        favorites = favorites,
                        gridCells = GridCells.Fixed(gridColumnsCount),
                        viewModel = viewModel,
                        aspectRatio = if (activeSection == "movies" || activeSection == "series") 0.7f else 1.6f,
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun MobileCategoryTab(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    primaryColor: Color
) {
    val bg = if (isSelected) primaryColor else Color(0xFF161726)
    val textColor = if (isSelected) Color.White else Color.LightGray

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ChannelContentList(
    isLoading: Boolean,
    errorMessage: String?,
    streams: List<LiveStream>,
    favorites: Set<Int>,
    gridCells: GridCells,
    viewModel: XtreamViewModel,
    aspectRatio: Float,
    primaryColor: Color
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = primaryColor,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = viewModel.translate("loading_streams"),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else if (errorMessage != null && streams.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(text = errorMessage, color = Color.Red, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.loadData() }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                    Text(viewModel.translate("retry_button"))
                }
            }
        }
    } else if (streams.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = viewModel.translate("empty_category"), color = Color.Gray, fontSize = 13.sp)
        }
    } else {
        LazyVerticalGrid(
            columns = gridCells,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(streams, key = { it.streamId }) { stream ->
                val activeSection = viewModel.currentSection.collectAsState().value
                ChannelCard(
                    stream = stream,
                    isFavorite = favorites.contains(stream.streamId),
                    onSelect = {
                        if (activeSection == "movies" || activeSection == "series") {
                            viewModel.setDetailStream(stream)
                        } else {
                            viewModel.selectChannel(stream)
                        }
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(stream) },
                    aspectRatio = aspectRatio,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

// Separate Online list compiler for published Firebase Channels
@Composable
fun FirebaseChannelContentList(
    isLoading: Boolean,
    errorMessage: String?,
    channels: List<FirebaseChannel>,
    gridColsList: Int,
    viewModel: XtreamViewModel,
    primaryColor: Color
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = primaryColor,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "جاري جلب القنوات والافلام المنشورة أونلاين...",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا يوجد قنوات منشورة أونلاين في هذا القسم.", color = Color.Gray, fontSize = 13.sp)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColsList),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(channels, key = { it.id }) { chan ->
                // Compile dynamic LiveStream model to utilize robust card element
                val tempStream = LiveStream(
                    streamId = chan.id.hashCode().coerceAtLeast(1),
                    name = chan.name,
                    streamIcon = chan.logoUrl,
                    categoryId = chan.categoryId
                )
                ChannelCard(
                    stream = tempStream,
                    isFavorite = false,
                    onSelect = { viewModel.selectFirebaseChannel(chan) },
                    onToggleFavorite = { /* Firebase channels are online-only */ },
                    aspectRatio = 1.6f,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

// Beautiful Sliding Side settings pane (opens half-screen drawer style on click)
@Composable
fun SettingsDrawerOverlay(
    isOpen: Boolean,
    viewModel: XtreamViewModel,
    primaryColor: Color,
    onOpenDeveloperConsole: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }
    val isProxy by viewModel.isProxyEnabled.collectAsState()
    val activeTheme by viewModel.currentTheme.collectAsState()
    val activeLang by viewModel.currentLang.collectAsState()
    val activeGridColumns by viewModel.gridColumns.collectAsState()
    val activeTvEngine by viewModel.playerEngineTv.collectAsState()
    val activeMovieEngine by viewModel.playerEngineMovie.collectAsState()
    val activeOrientation by viewModel.appOrientation.collectAsState()

    var showPasswordInput by remember { mutableStateOf(false) }
    var passwordValue by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    // Collapsible section active tracker
    var expandedSection by remember { mutableStateOf<String?>(null) }

    // Use full-size layer with slide in animation and background blend
    androidx.compose.animation.AnimatedVisibility(
        visible = isOpen,
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }) + androidx.compose.animation.fadeOut()
    ) {
        // Background dimming layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() }
        ) {
            // Sliding Side Panel Container
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.End
            ) {
                // Spacer to allow half screen click to dismiss
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onDismiss() }
                )

                // Drawer Body Panel
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 420.dp)
                        .width(if (LocalContext.current.resources.configuration.smallestScreenWidthDp >= 600) 400.dp else 325.dp)
                        .clickable(enabled = false) {} // block click propagation
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0F1014), Color(0xFF151624))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = viewModel.translate("settings_title"),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }
                        
                        Divider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
                        )

                        // Nested Scrollable Content list
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // == SECTION 1: LANGUAGE ==
                            item {
                                val isExpanded = expandedSection == "lang"
                                CollapsibleCard(
                                    title = viewModel.translate("lang_label"),
                                    icon = Icons.Default.Language,
                                    isExpanded = isExpanded,
                                    primaryColor = primaryColor,
                                    onHeaderClick = {
                                        expandedSection = if (isExpanded) null else "lang"
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("ar" to "العربية", "en" to "English", "fr" to "Français").forEach { (code, lang) ->
                                            Button(
                                                onClick = { viewModel.selectLanguage(code) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (activeLang == code) primaryColor else Color(0xFF222437)
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(lang, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            // == SECTION 2: THEME ==
                            item {
                                val isExpanded = expandedSection == "theme"
                                CollapsibleCard(
                                    title = viewModel.translate("theme_label"),
                                    icon = Icons.Default.Palette,
                                    isExpanded = isExpanded,
                                    primaryColor = primaryColor,
                                    onHeaderClick = {
                                        expandedSection = if (isExpanded) null else "theme"
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(
                                            "default" to Color(0xFFFF9800),
                                            "netflix" to Color(0xFFE50914),
                                            "midnight" to Color(0xFF00ADB5),
                                            "sunset" to Color(0xFFBB86FC),
                                            "emerald" to Color(0xFF03C988)
                                        ).forEach { (thm, color) ->
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .clickable { viewModel.selectTheme(thm) }
                                                    .padding(4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (activeTheme == thm) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape)
                                                            .background(Color.White.copy(alpha = 0.4f))
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Selected",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp).align(Alignment.Center)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // == SECTION 3: SCREEN ORIENTATION ==
                            item {
                                val isExpanded = expandedSection == "orientation"
                                CollapsibleCard(
                                    title = viewModel.translate("orientation_label"),
                                    icon = Icons.Default.ScreenRotation,
                                    isExpanded = isExpanded,
                                    primaryColor = primaryColor,
                                    onHeaderClick = {
                                        expandedSection = if (isExpanded) null else "orientation"
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                "sensor" to viewModel.translate("orientation_auto"),
                                                "landscape" to viewModel.translate("orientation_landscape"),
                                                "portrait" to viewModel.translate("orientation_portrait")
                                            ).forEach { (ori, label) ->
                                                Button(
                                                    onClick = { activity?.let { viewModel.selectOrientation(it, ori) } },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (activeOrientation == ori) primaryColor else Color(0xFF222437)
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = viewModel.translate("orientation_desc"),
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            // == SECTION 4: CARD FORMATTING / GRID ==
                            item {
                                val isExpanded = expandedSection == "grid"
                                CollapsibleCard(
                                    title = viewModel.translate("grid_columns_label"),
                                    icon = Icons.Default.GridView,
                                    isExpanded = isExpanded,
                                    primaryColor = primaryColor,
                                    onHeaderClick = {
                                        expandedSection = if (isExpanded) null else "grid"
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        listOf(2, 3, 4).forEach { col ->
                                            Button(
                                                onClick = { viewModel.selectGridColumns(col) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (activeGridColumns == col) primaryColor else Color(0xFF222437)
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "${col}x$col",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // == SECTION 5: PLAYBACK ENGINES AND PROXIES ==
                            item {
                                val isExpanded = expandedSection == "engines"
                                CollapsibleCard(
                                    title = "محركات التشغيل والبروكسي",
                                    icon = Icons.Default.PlayCircle,
                                    isExpanded = isExpanded,
                                    primaryColor = primaryColor,
                                    onHeaderClick = {
                                        expandedSection = if (isExpanded) null else "engines"
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // TV engine selectors
                                        Text(
                                            text = viewModel.translate("engine_tv_label"),
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("smart" to "Smart", "proxy" to "Proxy", "ts" to "TS", "m3u8" to "M3U8", "normal" to "Direct").forEach { (code, label) ->
                                                Button(
                                                    onClick = { viewModel.selectPlayerTvEngine(code) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (activeTvEngine == code) primaryColor else Color(0xFF222437)
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }

                                        // Movies engine selectors
                                        Text(
                                            text = viewModel.translate("engine_movie_label"),
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                "smart" to "Smart (MKV/MP4)",
                                                "proxy" to "Proxy Player"
                                            ).forEach { (code, label) ->
                                                Button(
                                                    onClick = { viewModel.selectPlayerMovieEngine(code) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (activeMovieEngine == code) primaryColor else Color(0xFF222437)
                                                    ),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }

                                        Divider(color = Color.White.copy(alpha = 0.05f))

                                        // Proxy switches
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = viewModel.translate("proxy_toggle"),
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = viewModel.translate("proxy_toggle_desc"),
                                                    color = Color.Gray,
                                                    fontSize = 9.sp
                                                )
                                            }
                                            Switch(
                                                checked = isProxy,
                                                onCheckedChange = { viewModel.toggleProxy() },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = primaryColor
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // == SECTION 6: DEV CONTROLLER (PASSWORD REQ) ==
                            item {
                                val isExpanded = expandedSection == "admin"
                                CollapsibleCard(
                                    title = viewModel.translate("admin_panel"),
                                    icon = Icons.Default.DeveloperMode,
                                    isExpanded = isExpanded,
                                    primaryColor = primaryColor,
                                    onHeaderClick = {
                                        expandedSection = if (isExpanded) null else "admin"
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (!showPasswordInput) {
                                            Button(
                                                onClick = { showPasswordInput = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("انقر لفتح قفل المطور 🔓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            OutlinedTextField(
                                                value = passwordValue,
                                                onValueChange = { 
                                                    passwordValue = it 
                                                    passwordError = false
                                                },
                                                label = { Text(viewModel.translate("admin_pass_hint"), color = Color.Gray) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.LightGray,
                                                    focusedBorderColor = primaryColor
                                                ),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            if (passwordError) {
                                                Text(viewModel.translate("admin_wrong_pass"), color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    if (passwordValue == "ali2008#$1") {
                                                        passwordValue = ""
                                                        showPasswordInput = false
                                                        onOpenDeveloperConsole()
                                                    } else {
                                                        passwordError = true
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(viewModel.translate("admin_unlock"), color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // == SECTION 6.1: TELEGRAM LINK ==
                            item {
                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            try {
                                                uriHandler.openUri("https://t.me/+zFTWopL4zedlNTJi")
                                            } catch (e: Exception) {}
                                            onDismiss()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF161E2E)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = null,
                                                tint = Color(0xFF24A1DE),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "قناة تيلجرام ميديا 📢",
                                                color = Color(0xFF24A1DE),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = null,
                                            tint = Color(0xFF24A1DE).copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            // == SECTION 6.2: DOWNLOADED VIDEOS ==
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setSection("youtube")
                                            onDismiss()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF1E1E2E)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                tint = Color(0xFF03C988),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "الفيديوهات المحملة (أوفلاين) 📥",
                                                color = Color(0xFF03C988),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = null,
                                            tint = Color(0xFF03C988).copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            // == SECTION 7: LOG OUT (DANGER CELL) ==
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.logout()
                                            onDismiss()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF2E1115)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Logout,
                                                contentDescription = null,
                                                tint = Color(0xFFFF4D4D),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = viewModel.translate("logout_label"),
                                                color = Color(0xFFFF5252),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252).copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Footer Close settings button
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "إغلاق نافذة الإعدادات",
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Custom Collapsible Card Helper for beautiful organized structure
@Composable
fun CollapsibleCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    primaryColor: Color,
    onHeaderClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) Color(0xFF131422) else Color(0xFF1B1C28)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) primaryColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.03f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = onHeaderClick,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isExpanded) primaryColor else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        color = if (isExpanded) Color.White else Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = if (isExpanded) primaryColor else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expose child buttons/layout only when expanded
            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                content()
            }
        }
    }
}

@Composable
fun CinemaDetailOverlay(
    viewModel: XtreamViewModel,
    primaryColor: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stream by viewModel.currentDetailStream.collectAsState()
    val selectedVodInfo by viewModel.selectedVodInfo.collectAsState()
    val selectedSeriesInfo by viewModel.selectedSeriesInfo.collectAsState()
    val isDetailLoading by viewModel.isDetailLoading.collectAsState()
    val activeSection by viewModel.currentSection.collectAsState()

    if (stream == null) return

    val coverUrl = remember(stream, selectedVodInfo, selectedSeriesInfo) {
        selectedVodInfo?.cover 
            ?: selectedSeriesInfo?.cover 
            ?: stream?.cover 
            ?: stream?.streamIcon 
            ?: ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080C))
            .clickable(enabled = true, onClick = {}) // block clicks through
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 1. TOP HERO ASPECT BANNER (Netflix style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                // Background Cover AsyncImage
                if (coverUrl.isNotEmpty()) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Hero Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF13141F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "No Cover",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                // Smooth Vignette overlapping black/dark gradients at the bottom of the image for Netflix effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    Color(0xFF07080C).copy(alpha = 0.95f),
                                    Color(0xFF07080C)
                                ),
                                startY = 0f
                            )
                        )
                )

                // Back arrow top overlay bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (activeSection == "movies") "تفاصيل الفيلم السينمائي" else "تفاصيل المسلسل والدراما",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(38.dp))
                }

                // Overlapping Mini Floating Card Poster on the bottom left
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .width(110.dp)
                            .height(155.dp)
                            .shadow(8.dp, RoundedCornerShape(10.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (coverUrl.isNotEmpty()) {
                            coil.compose.AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF13141F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }

                    // Simple quick title overlay next to it
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        val name = selectedVodInfo?.name ?: selectedSeriesInfo?.name ?: stream?.name ?: ""
                        Text(
                            text = name,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 26.sp,
                            modifier = Modifier.widthIn(max = 240.dp)
                        )
                    }
                }
            }

            // 2. DETAILS AREA UNDERNEATH
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (isDetailLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = primaryColor,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "جاري جلب تفاصيل المحتوى...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    val plot = selectedVodInfo?.plot ?: selectedSeriesInfo?.plot ?: ""
                    val rating = selectedVodInfo?.rating ?: selectedSeriesInfo?.rating ?: ""
                    val releaseDate = selectedVodInfo?.releaseDate ?: selectedSeriesInfo?.releaseDate ?: ""
                    val genre = selectedVodInfo?.genre ?: selectedSeriesInfo?.genre ?: ""
                    val castList = selectedVodInfo?.cast ?: selectedSeriesInfo?.cast ?: emptyList()

                    // Meta Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (rating.isNotEmpty() && rating != "0") {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFFAD0A), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "★ $rating",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        if (releaseDate.isNotEmpty()) {
                            Text(
                                text = releaseDate,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        if (genre.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = genre,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Direct Action Play Button for Movies
                    if (activeSection == "movies") {
                        Button(
                            onClick = {
                                viewModel.selectChannel(stream)
                                viewModel.setDetailStream(null)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "تشغيل الفيلم الآن",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    // Plot Description Box
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "الوصف وقصة العمل الأصلي",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (plot.isNotBlank()) plot else "لا يوجد وصف مدخل لهذا العمل في السيرفر حالياً.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    // Real Cast Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "طاقم العمل والتمثيل الحقيقي",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (castList.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(castList) { actor ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(74.dp)
                                    ) {
                                        Card(
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(54.dp)
                                                .border(1.5.dp, primaryColor.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            coil.compose.AsyncImage(
                                                model = actor.imageUrl,
                                                contentDescription = actor.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = actor.name,
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "لا توجد معلومات متوفرة عن الممثلين",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    // Series details - Seasons and Episodes (Netflix Layout!)
                    if (activeSection == "series" && selectedSeriesInfo != null) {
                        val name = selectedVodInfo?.name ?: selectedSeriesInfo?.name ?: stream?.name ?: ""
                        val seasons = selectedSeriesInfo!!.seasons

                        if (seasons.isNotEmpty()) {
                            var selectedSeasonNum by remember { mutableStateOf(seasons.keys.first()) }
                            val episodes = seasons[selectedSeasonNum] ?: emptyList()

                            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "مواسم العمل والحلقات",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            // Season Choice Row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(seasons.keys.toList()) { seasonNum ->
                                    val isSelected = selectedSeasonNum == seasonNum
                                    Button(
                                        onClick = { selectedSeasonNum = seasonNum },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) primaryColor else Color.White.copy(alpha = 0.08f)
                                        ),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                    ) {
                                        Text(
                                            text = "الموسم $seasonNum",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Netflix Style Ep List
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (episodes.isEmpty()) {
                                    Text("لا توجد حلقات متاحة لهذا الموسم المختار حالياً.", color = Color.Gray, fontSize = 12.sp)
                                } else {
                                    episodes.forEach { episode ->
                                        var isEpisodeFocused by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isEpisodeFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
                                                .border(1.2.dp, if (isEpisodeFocused) primaryColor else Color.Transparent, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    viewModel.playEpisode(episode, name)
                                                    viewModel.setDetailStream(null)
                                                }
                                                .onFocusChanged { isEpisodeFocused = it.isFocused }
                                                .focusable()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Play action icon on left
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(primaryColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Play Episode",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "الحلقة ${episode.episodeNum} : ${episode.title.ifBlank { "الحلقة " + episode.episodeNum }}",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (!episode.plot.isNullOrBlank()) {
                                                    Text(
                                                        text = episode.plot,
                                                        color = Color.White.copy(alpha = 0.6f),
                                                        fontSize = 11.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        lineHeight = 15.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "لا يوجد تفاصيل مواسم أو حلقات لهذا المسلسل.",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
