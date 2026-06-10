package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.models.LiveStream

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(
    stream: LiveStream,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1.6f,
    primaryColor: Color = Color(0xFFFF9800)
) {
    var isFocused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f)
    
    val focusBorderColor = if (isFocused) primaryColor else Color.Transparent
    val cardBg = if (isFocused) Color(0xFF1E2030) else Color(0xFF0F1016)

    val imageSource = remember(stream) {
        if (!stream.streamIcon.isNullOrEmpty()) {
            stream.streamIcon
        } else if (!stream.cover.isNullOrEmpty()) {
            stream.cover
        } else {
            null
        }
    }

    Card(
        modifier = modifier
            .padding(4.dp)
            .scale(animatedScale)
            .onFocusChanged { state ->
                isFocused = state.isFocused
            }
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
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
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onToggleFavorite
            )
            .testTag("channel_card_${stream.streamId}"),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (isFocused) 2.5.dp else 1.dp, if (isFocused) focusBorderColor else Color.White.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Logo / Cover Frame with dynamic Aspect Ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(Color(0xFF040406))
            ) {
                if (imageSource != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageSource)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Default Icon",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Banner overlay for gradients
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 120f
                            )
                        )
                )

                // Favorite Quick badge
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite Button",
                        tint = if (isFavorite) Color.Red else Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // Content section indicator tag
                Text(
                    text = "#${stream.streamId}",
                    fontSize = 9.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Name label details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stream.name ?: "N/A",
                    color = if (isFocused) primaryColor else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
