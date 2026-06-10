package com.example.ui.components

import android.app.Activity
import android.util.Log
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.models.LiveStream
import com.example.models.FirebaseChannel
import com.example.viewmodel.XtreamViewModel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun IPTVPlayer(
    channel: LiveStream?,
    fbChannel: FirebaseChannel?,
    viewModel: XtreamViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isProxy by viewModel.isProxyEnabled.collectAsState()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsState()

    // Programmatically locked in landscape mode for absolute cinematic IPTV stream viewing
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // Prevent screen sleep/dimming while playing stream
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Capture device physical/remote back button
    BackHandler {
        onClose()
    }

    val primaryColor = when (viewModel.currentTheme.collectAsState().value) {
        "netflix" -> Color(0xFFE50914)
        "midnight" -> Color(0xFF00ADB5)
        "sunset" -> Color(0xFFBB86FC)
        "emerald" -> Color(0xFF03C988)
        else -> Color(0xFFFF9800)
    }

    val userAgentString = remember(channel, fbChannel) {
        if (fbChannel != null && fbChannel.userAgent.isNotBlank()) {
            fbChannel.userAgent
        } else {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"
        }
    }

    // Initialize ExoPlayer with the requested User-Agent
    val exoPlayer = remember(userAgentString) {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory().apply {
            setUserAgent(userAgentString)
            setAllowCrossProtocolRedirects(true)
        }
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    var isPlayingState by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    
    // Progress track bar timeline states for movies and series
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    
    // OSD visibility controller
    var showControls by remember { mutableStateOf(true) }
    
    // Auto-hide panel timer
    LaunchedEffect(showControls, isPlayingState) {
        if (showControls && isPlayingState) {
            delay(5000)
            showControls = false
        }
    }

    // Thread loop tracking playback progress
    LaunchedEffect(exoPlayer, isPlayingState) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration
            delay(1000)
        }
    }

    // Watch for stream URL variations and map them reactively
    val streamUrl = remember(channel, fbChannel, isProxy) {
        if (fbChannel != null) {
            val rawUrl = fbChannel.streamUrl
            if (fbChannel.streamType == "Proxy") {
                "http://194.60.93.157/proxy?url=$rawUrl"
            } else {
                rawUrl
            }
        } else if (channel != null) {
            viewModel.getStreamUrl(channel.streamId)
        } else {
            ""
        }
    }

    LaunchedEffect(streamUrl) {
        if (streamUrl.isBlank()) return@LaunchedEffect
        isBuffering = true
        playbackError = null
        try {
            val mediaItem = MediaItem.fromUri(streamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            playbackError = "خطأ في تشغيل الرابط: ${e.localizedMessage}"
            isBuffering = false
        }
    }

    // Listen to ExoPlayer Events standard hooks
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                isPlayingState = (state == Player.STATE_READY && exoPlayer.playWhenReady)
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = "خطأ في تشغيل البث المباشر (رمز الخطأ: ${error.errorCodeName}). قد يكون السيرفر مضغوطاً أو الرابط غير متاح حالياً."
                Log.e("IPTVPlayer", "Player error occur", error)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val focusRequester = remember { FocusRequester() }

    // Request local D-Pad key focus automatically on initialization
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Mapping key events (D-Pad & Emulator keyboard keys)
    val onKeyTrigger: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            showControls = true // Wake OSD up on any remote keypress
            when (keyEvent.key) {
                Key.DirectionUp -> {
                    if (channel != null) {
                        viewModel.playPrevChannel()
                    }
                    true
                }
                Key.DirectionDown -> {
                    if (channel != null) {
                        viewModel.playNextChannel()
                    }
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                    true
                }
                Key.DirectionLeft -> {
                    // Seek backward 10 seconds (movies and series tracking)
                    val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                    exoPlayer.seekTo(target)
                    true
                }
                Key.DirectionRight -> {
                    // Seek forward 10 seconds (movies and series tracking)
                    val target = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                    exoPlayer.seekTo(target)
                    true
                }
                Key.Back -> {
                    onClose()
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent(onKeyTrigger)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // Video View Component Wrapper
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // Use our beautiful custom Compose OSD controls
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true // Prevent screen from turning off at view level
                }
            },
            update = { playerView ->
                playerView.resizeMode = when (aspectRatioMode) {
                    0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    3 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Frame Spinner Overlay
        if (isBuffering && playbackError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = primaryColor,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "جاري تحميل البث...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Playback Error Screen Alert
        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Error icon",
                        tint = Color.Red,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "عذراً، حدث خطأ في تشغيل القناة",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playbackError ?: "",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row {
                        Surface(
                            onClick = {
                                viewModel.toggleProxy()
                            },
                            color = primaryColor,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isProxy) "تشغيل بدون بروكسي" else "تشغيل باستخدام البروكسي",
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Surface(
                            onClick = { onClose() },
                            color = Color.Gray,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "خروج",
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Custom OSD Controller Panel (Auto-hiding)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top controls row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onClose() },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = channel?.name ?: fbChannel?.name ?: "البث المباشر",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1
                            )
                            Text(
                                text = if (channel != null) "رقم القناة: #${channel.streamId}" else "قناة مخصصة أونلاين",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Top Action Badges (Tv control hints)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleProxy() },
                            modifier = Modifier
                                .background(
                                    if (isProxy) primaryColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                    CircleShape
                                )
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = "Proxy",
                                tint = if (isProxy) primaryColor else Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProxy) "مفعل (البروكسي)" else "اتصال مباشر",
                            color = if (isProxy) primaryColor else Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Aspect Ratio indicator / HUD Overlay in mid screen
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = viewModel.translate("remote_hint"),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Bottom bar control elements
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // PROGRESS SCRUBBER TIMELINE (Active only for movies & series)
                    val isMoviesOrSeriesSection = viewModel.currentSection.collectAsState().value.let { sec ->
                        sec == "movies" || sec == "series"
                    }
                    if (isMoviesOrSeriesSection && totalDuration > 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Slider(
                                value = currentPosition.toFloat(),
                                onValueChange = { newValue ->
                                    currentPosition = newValue.toLong()
                                    exoPlayer.seekTo(newValue.toLong())
                                },
                                valueRange = 0f..totalDuration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = primaryColor,
                                    activeTrackColor = primaryColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(currentPosition),
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatTime(totalDuration),
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Navigation hint left
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (channel != null) {
                                IconButton(
                                    onClick = { viewModel.playPrevChannel() },
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Prev",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(primaryColor, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            if (channel != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { viewModel.playNextChannel() },
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Next",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        // Aspect Ratio changer right
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { viewModel.cycleAspectRatio() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Aspect ratio",
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (aspectRatioMode) {
                                    0 -> viewModel.translate("auto_fit")
                                    1 -> viewModel.translate("full_screen")
                                    2 -> viewModel.translate("aspect_16_9")
                                    3 -> viewModel.translate("stretch")
                                    else -> "تلقائي"
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper method to format elapsed time
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val secs = totalSeconds % 60
    val mins = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
