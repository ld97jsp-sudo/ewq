package com.example.ui.components

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.viewmodel.XtreamViewModel
import com.example.viewmodel.YouTubeVideo
import com.example.viewmodel.YouTubeDownload
import java.io.File

@Composable
fun YouTubeSectionContent(
    viewModel: XtreamViewModel,
    gridColsList: Int,
    primaryColor: Color
) {
    val context = LocalContext.current
    val query by viewModel.youtubeSearchQuery.collectAsState()
    val videos by viewModel.youtubeVideos.collectAsState()
    val isYoutubeLoading by viewModel.isYoutubeLoading.collectAsState()
    val downloads by viewModel.youtubeDownloads.collectAsState()
    val downloadingVideos by viewModel.downloadingVideos.collectAsState()
    
    var subTab by remember { mutableStateOf(1) } // 1: YouTube Online, 2: Offline Downloads
    var activeVideoIdByWeb by remember { mutableStateOf<String?>(null) }
    
    val currentLocalFilePath by viewModel.currentPlayingLocalFilePath.collectAsState()
    val currentLocalFileTitle by viewModel.currentPlayingLocalTitle.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1016))
    ) {
        // Double tab split from above - choice between Youtube online searching and offline downloaded clips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { subTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTab == 1) primaryColor else Color(0xFF1E1E2E)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("البحث عبر يوتيوب", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Button(
                onClick = { subTab = 2 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTab == 2) primaryColor else Color(0xFF1E1E2E)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.DownloadForOffline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("الفيديوهات المحملة (${downloads.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        if (subTab == 1) {
            // Online list search field and grid
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.searchYoutubeVideos(it) },
                placeholder = { Text("ابحث عن أي فيلم، كرتون، أو حلقة يوتيوب...", color = Color.Gray, fontSize = 13.sp) },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            if (isYoutubeLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            } else if (videos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("ادخل عبارة بحث للبدء أو تحقق من اتصالك بالإنترنت", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColsList),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(videos) { vid ->
                        val downloadProgress = downloadingVideos[vid.videoId]
                        val isDownloading = downloadProgress != null
                        val isAlreadyDownloaded = downloads.any { it.videoId == vid.videoId }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF151622)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeVideoIdByWeb = vid.videoId
                                }
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.6f)
                                ) {
                                    AsyncImage(
                                        model = vid.thumbnailUrl,
                                        contentDescription = vid.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    
                                    // Play icon overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Play",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }

                                    // Downloading progress indicator overlay
                                    if (isDownloading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.7f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(
                                                    progress = { downloadProgress ?: 0.01f },
                                                    color = primaryColor,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val percent = ((downloadProgress ?: 0.01f) * 100).toInt()
                                                Text("جاري التحميل $percent%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = vid.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = vid.channelTitle,
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Action buttons row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { activeVideoIdByWeb = vid.videoId },
                                            modifier = Modifier
                                                .background(primaryColor, RoundedCornerShape(6.dp))
                                                .height(28.dp)
                                                .width(52.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Text("شغل", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        if (isAlreadyDownloaded) {
                                            IconButton(
                                                onClick = { /* already downloaded */ },
                                                enabled = false
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = Color.Green, modifier = Modifier.size(20.dp))
                                            }
                                        } else if (isDownloading) {
                                            IconButton(
                                                onClick = { /* download in progress */ },
                                                enabled = false
                                            ) {
                                                CircularProgressIndicator(color = primaryColor, modifier = Modifier.size(16.dp))
                                            }
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    viewModel.downloadYoutubeVideo(context, vid.videoId, vid.title, vid.thumbnailUrl)
                                                },
                                                modifier = Modifier
                                                    .background(Color(0xFF222437), RoundedCornerShape(6.dp))
                                                    .size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = "Download Video", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Offline downloads page List of local media downloads
            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("لم تقم بتنزيل مقاطع فيديو بعد.", color = Color.LightGray, fontSize = 13.sp)
                        Text("قم بالبحث في يوتيوب واضغط أيقونة التحميل لتشغيلها أوفلاين.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColsList),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(downloads) { download ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF151622)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.playLocalFile(download.localFilePath, download.title)
                                }
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.6f)
                                ) {
                                    AsyncImage(
                                        model = download.thumbnailUrl,
                                        contentDescription = download.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Play offline",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = download.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.playLocalFile(download.localFilePath, download.title) },
                                            modifier = Modifier
                                                .background(primaryColor, RoundedCornerShape(6.dp))
                                                .height(28.dp)
                                                .width(72.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Text("تشغيل", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.deleteYoutubeDownload(download.videoId)
                                            },
                                            modifier = Modifier
                                                .background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                .size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Download", tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Embed Widescreen / Landscape YouTube Interactive iframe Player
    activeVideoIdByWeb?.let { videoId ->
        YouTubeWebPlayerOverlay(
            videoId = videoId,
            onClose = { activeVideoIdByWeb = null }
        )
    }

    // Embed local files offline playback overlay
    currentLocalFilePath?.let { filePath ->
        LocalFileOfflinePlayerOverlay(
            filePath = filePath,
            title = currentLocalFileTitle ?: "مقطع فيديو محمل",
            onClose = { viewModel.playLocalFile(null, null) }
        )
    }
}

@Composable
fun YouTubeWebPlayerOverlay(
    videoId: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }

    // Lock Landscape on creation and restore on close
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            // strictly prevent escaping outside WebView - prevent opening external Youtube app
                            return false
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    
                    // ModestBranding=1 hides major Youtube branding. Controls=1 displays native web elements cleanly
                    val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&modestbranding=1&showinfo=0&rel=0&controls=1&fs=1&theme=dark"
                    loadUrl(embedUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Close Floating Button (at the top right in landscape)
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(30.dp))
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close player", tint = Color.White)
        }
    }
}

@Composable
fun LocalFileOfflinePlayerOverlay(
    filePath: String,
    title: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Build customized horizontal ExoPlayer loader for local files offline
        val uri = remember(filePath) { android.net.Uri.fromFile(File(filePath)) }
        
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                    
                    val exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build().apply {
                        val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)
                        setMediaItem(mediaItem)
                        prepare()
                        playWhenReady = true
                    }
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view ->
                view.player?.release()
            }
        )

        // Title and Back button row overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 400.dp)
                )
            }
            
            Text(
                text = "تشغيل محلي (أوفلاين) 📁",
                color = Color.Green,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
