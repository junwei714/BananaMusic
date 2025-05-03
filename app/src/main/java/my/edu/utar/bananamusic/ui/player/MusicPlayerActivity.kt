package my.edu.utar.bananamusic.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import my.edu.utar.bananamusic.models.Track
import my.edu.utar.bananamusic.services.MusicPlaybackService
import my.edu.utar.bananamusic.utils.AudioPlayerHelper
import my.edu.utar.bananamusic.utils.ApiDataProvider
import my.edu.utar.bananamusic.utils.FirebaseAuthHelper
import my.edu.utar.bananamusic.utils.PlaylistManager
import my.edu.utar.bananamusic.utils.UserPreferences
import my.edu.utar.bananamusic.utils.RecentlyPlayedManager

class MusicPlayerActivity : ComponentActivity(), MusicPlaybackService.PlaybackCallback {
    
    companion object {
        const val TAG = "MusicPlayerActivity"
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_PLAYLIST_ID = "playlist_id"
    }
    
    private lateinit var audioPlayerHelper: AudioPlayerHelper
    private var currentTrack: Track? = null
    private var trackList: List<Track> = emptyList()
    private var currentTrackIndex = 0
    private lateinit var userPreferences: UserPreferences
    
    // Service related fields
    private var musicService: MusicPlaybackService? = null
    private var serviceBound = false
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlaybackService.MusicBinder
            musicService = binder.getService()
            musicService?.setPlaybackCallback(this@MusicPlayerActivity)
            serviceBound = true
            
            // Start playback in the service if we have a track loaded
            currentTrack?.let { track ->
                musicService?.playTrack(track)
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize components
        audioPlayerHelper = AudioPlayerHelper.getInstance(this)
        userPreferences = UserPreferences.getInstance(this)
        
        // Get track ID from intent
        val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
        val playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID)
        
        // Load tracks and find the current track
        loadTracksAndPlay(trackId, playlistId)
        
        // Bind to the music service
        bindMusicService()
        
        setContent {
            MusicPlayerContent()
        }
    }
    
    override fun onStart() {
        super.onStart()
        if (!serviceBound) {
            bindMusicService()
        }
    }
    
    override fun onStop() {
        super.onStop()
        // We don't want to unbind the service when the activity stops
        // so that playback can continue in the background
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            musicService?.setPlaybackCallback(null)
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    private fun bindMusicService() {
        val intent = Intent(this, MusicPlaybackService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        startService(intent)
    }
    
    @Composable
    fun MusicPlayerContent() {
        var isPlaying by remember { mutableStateOf(false) }
        var currentPosition by remember { mutableStateOf(0L) }
        var isFavorite by remember { mutableStateOf(currentTrack?.let { userPreferences.isTrackFavorite(it.trackId) } ?: false) }
        var shuffleEnabled by remember { mutableStateOf(false) }
        var repeatMode by remember { mutableStateOf(AudioPlayerHelper.REPEAT_MODE_NONE) }
        
        // Update playback position periodically
        LaunchedEffect(isPlaying) {
            while (isPlaying) {
                currentPosition = audioPlayerHelper.getCurrentPosition()
                delay(1000) // Update every second
            }
        }
        
        // Set initial state
        LaunchedEffect(Unit) {
            isPlaying = musicService?.isPlaying() ?: audioPlayerHelper.isPlaying()
            isFavorite = currentTrack?.let { userPreferences.isTrackFavorite(it.trackId) } ?: false
            shuffleEnabled = musicService?.isShuffleEnabled() ?: false
            repeatMode = musicService?.getRepeatMode() ?: AudioPlayerHelper.REPEAT_MODE_NONE
        }
        
        // Update favorite status when track changes
        LaunchedEffect(currentTrack) {
            isFavorite = currentTrack?.let { userPreferences.isTrackFavorite(it.trackId) } ?: false
        }
        
        MusicPlayerScreen(
            track = currentTrack,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            isFavorite = isFavorite,
            isShuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            onPlayPauseClick = {
                if (isPlaying) {
                    pausePlayback()
                } else {
                    resumePlayback()
                }
                isPlaying = !isPlaying
            },
            onPreviousClick = {
                playPreviousTrack()
                isPlaying = true
            },
            onNextClick = {
                playNextTrack()
                isPlaying = true
            },
            onShuffleClick = {
                val newShuffleState = musicService?.toggleShuffle() ?: false
                shuffleEnabled = newShuffleState
            },
            onRepeatClick = {
                val newRepeatMode = musicService?.cycleRepeatMode() ?: AudioPlayerHelper.REPEAT_MODE_NONE
                repeatMode = newRepeatMode
            },
            onFavoriteClick = { newFavoriteStatus ->
                currentTrack?.let { track ->
                    Log.d(TAG, "Track ${track.title} favorite status: $newFavoriteStatus")
                    
                    if (newFavoriteStatus) {
                        userPreferences.addFavoriteTrack(track.trackId)
                        Toast.makeText(this@MusicPlayerActivity, "Added to favorites", Toast.LENGTH_SHORT).show()
                    } else {
                        userPreferences.removeFavoriteTrack(track.trackId)
                        Toast.makeText(this@MusicPlayerActivity, "Removed from favorites", Toast.LENGTH_SHORT).show()
                    }
                    
                    // If user is logged in, save to Firebase as well
                    val authHelper = FirebaseAuthHelper.getInstance()
                    if (authHelper.isUserLoggedIn()) {
                        lifecycleScope.launch {
                            try {
                                if (newFavoriteStatus) {
                                    authHelper.addFavoriteTrack(track.trackId)
                                } else {
                                    authHelper.removeFavoriteTrack(track.trackId)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error syncing favorites with Firebase", e)
                            }
                        }
                    }
                    
                    isFavorite = newFavoriteStatus
                }
            },
            onSeek = { position ->
                audioPlayerHelper.seekTo(position)
                currentPosition = position
            },
            onBackClick = {
                finish()
            }
        )
    }
    
    private fun loadTracksAndPlay(trackId: String?, playlistId: String?) {
        if (playlistId != null) {
            // Use real data from PlaylistManager
            val playlistManager = PlaylistManager.getInstance(this)
            
            // Load tracks from the real playlist
            playlistManager.getPlaylistTracks(playlistId, object : PlaylistManager.TracksCallback {
                override fun onSuccess(tracks: MutableList<Track>) {
                    trackList = tracks
                    processTracksAndPlay(trackId)
                }
                
                override fun onError(errorMessage: String) {
                    Log.e(TAG, "Error loading playlist tracks: $errorMessage")
                    Toast.makeText(this@MusicPlayerActivity, 
                        "Error loading tracks: $errorMessage", Toast.LENGTH_SHORT).show()
                    finish()
                }
            })
        } else {
            // Get tracks from recently played list
            val apiProvider = ApiDataProvider.getInstance(this)
            apiProvider.getRecentlyPlayed(object : ApiDataProvider.TracksCallback {
                override fun onTracksLoaded(tracks: MutableList<Track>) {
                    trackList = tracks
                    processTracksAndPlay(trackId)
                }
                
                override fun onError(message: String) {
                    Log.e(TAG, "Error loading recently played tracks: $message")
                    Toast.makeText(this@MusicPlayerActivity, 
                        "Error loading tracks: $message", Toast.LENGTH_SHORT).show()
                    finish()
                }
            })
        }
    }
    
    private fun processTracksAndPlay(trackId: String?) {
        // Make sure we have tracks to play
        if (trackList.isEmpty()) {
            Log.e(TAG, "No tracks found to play")
            finish()
            return
        }
        
        // Find selected track in the list or use the first one
        if (trackId != null) {
            val index = trackList.indexOfFirst { it.trackId == trackId }
            if (index != -1) {
                currentTrackIndex = index
            }
        }
        
        currentTrack = trackList[currentTrackIndex]
        playCurrentTrack()
    }
    
    private fun playCurrentTrack() {
        currentTrack?.let { track ->
            if (serviceBound && musicService != null) {
                musicService?.playTrack(track)
            } else {
                // Fallback to direct playback if service not bound
                audioPlayerHelper.playAudio(
                    track.previewUrl,
                    onPrepared = {
                        // Nothing to do
                    },
                    onCompletion = {
                        playNextTrack()
                    },
                    onError = { error ->
                        Log.e(TAG, "Error playing track: $error")
                        // Try next track on error
                        playNextTrack()
                    }
                )
            }
            
            // Add to recently played using our new RecentlyPlayedManager
            lifecycleScope.launch {
                try {
                    // Add to recently played history
                    RecentlyPlayedManager.getInstance(this@MusicPlayerActivity)
                        .addTrackToRecentlyPlayed(track)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding to recently played", e)
                }
            }
            
            // Update the UI based on the current track
            updateUI()
        }
    }
    
    private fun playNextTrack() {
        currentTrackIndex = (currentTrackIndex + 1) % trackList.size
        currentTrack = trackList[currentTrackIndex]
        playCurrentTrack()
    }
    
    private fun playPreviousTrack() {
        currentTrackIndex = if (currentTrackIndex > 0) {
            currentTrackIndex - 1
        } else {
            trackList.size - 1
        }
        currentTrack = trackList[currentTrackIndex]
        playCurrentTrack()
    }
    
    private fun pausePlayback() {
        if (serviceBound && musicService != null) {
            musicService?.pausePlayback()
        } else {
            audioPlayerHelper.pauseAudio()
        }
    }
    
    private fun resumePlayback() {
        if (serviceBound && musicService != null) {
            musicService?.resumePlayback()
        } else {
            audioPlayerHelper.resumeAudio()
        }
    }
    
    // MusicPlaybackService.PlaybackCallback implementation
    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        // Update UI will be handled by composables
    }
    
    override fun onTrackComplete() {
        playNextTrack()
    }
    
    override fun onPlaybackError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        // Try next track on error
        playNextTrack()
    }
    
    override fun onPreviousClicked() {
        playPreviousTrack()
    }
    
    override fun onNextClicked() {
        playNextTrack()
    }
    
    override fun onShuffleModeChanged(enabled: Boolean) {
        // Update UI will be handled by composables
    }
    
    override fun onRepeatModeChanged(repeatMode: Int) {
        // Update UI will be handled by composables
    }
} 