package my.edu.utar.bananamusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import my.edu.utar.bananamusic.R
import my.edu.utar.bananamusic.models.Track
import kotlin.time.Duration.Companion.milliseconds

// Custom theme colors based on BananaMusic app
private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyBlack = Color(0xFF191414)
private val DarkBackground = Color(0xFF121212)
private val CardBackground = Color(0xFF282828)
private val GrayText = Color(0xFFB3B3B3)

@Composable
fun MusicPlayerScreen(
    track: Track?,
    isPlaying: Boolean,
    currentPosition: Long,
    isFavorite: Boolean,
    isShuffleEnabled: Boolean = false,
    repeatMode: Int = AudioPlayerHelper.REPEAT_MODE_NONE,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onFavoriteClick: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    val sliderPosition = remember { mutableStateOf(0f) }
    val trackDuration = track?.durationMs ?: 100000L
    
    // Update slider position based on current playback position
    LaunchedEffect(currentPosition) {
        if (trackDuration > 0) {
            sliderPosition.value = currentPosition.toFloat() / trackDuration.toFloat()
        }
    }
    
    val animatedSliderPosition by animateFloatAsState(
        targetValue = sliderPosition.value,
        label = "SliderAnimation"
    )
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "NOW PLAYING",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White
                    )
                }
            }
            
            // Album art
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(track?.coverUrl ?: R.drawable.ic_playlist_placeholder)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Track info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = track?.title ?: "Unknown Track",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = track?.artist ?: "Unknown Artist",
                        color = GrayText,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                IconButton(
                    onClick = { 
                        onFavoriteClick(!isFavorite)
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) SpotifyGreen else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Progress slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = animatedSliderPosition,
                    onValueChange = { 
                        sliderPosition.value = it
                    },
                    onValueChangeFinished = {
                        onSeek((sliderPosition.value * trackDuration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = SpotifyGreen,
                        activeTrackColor = SpotifyGreen,
                        inactiveTrackColor = CardBackground
                    )
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        color = GrayText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatDuration(trackDuration),
                        color = GrayText,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Playback controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle button
                IconButton(onClick = { onShuffleClick() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shuffle),
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                    )
                }
                
                // Previous button
                IconButton(
                    onClick = { onPreviousClick() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_previous),
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Play/Pause button
                IconButton(
                    onClick = { onPlayPauseClick() },
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                // Next button
                IconButton(
                    onClick = { onNextClick() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_next),
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Repeat button
                IconButton(onClick = { onRepeatClick() }) {
                    Icon(
                        painter = painterResource(
                            id = when (repeatMode) {
                                AudioPlayerHelper.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                                AudioPlayerHelper.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                                else -> R.drawable.ic_repeat
                            }
                        ),
                        contentDescription = "Repeat",
                        tint = when (repeatMode) {
                            AudioPlayerHelper.REPEAT_MODE_NONE -> Color.White.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Bottom controls (optional)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = GrayText
                    )
                }
            }
        }
    }
}

// Helper function to format duration
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
} 