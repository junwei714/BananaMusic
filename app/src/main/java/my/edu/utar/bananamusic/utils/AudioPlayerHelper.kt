package my.edu.utar.bananamusic.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import android.widget.SeekBar
import my.edu.utar.bananamusic.models.Track
import java.io.IOException

class AudioPlayerHelper private constructor(private val context: Context) {
    companion object {
        private const val TAG = "AudioPlayerHelper"
        
        // Repeat mode constants
        const val REPEAT_MODE_NONE = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2
        
        @Volatile
        private var instance: AudioPlayerHelper? = null
        
        fun getInstance(context: Context): AudioPlayerHelper {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayerHelper(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentTrack: Track? = null
    private var isPreparing = false
    private var listener: OnPlaybackChangedListener? = null
    private val handler = Handler()
    private var updateSeekBarTask: Runnable? = null
    private var seekBar: SeekBar? = null
    
    // API helpers
    private val soundCloudHelper = SoundCloudHelper.getInstance(context)
    private val spotifyHelper = SpotifyHelper.getInstance(context)
    
    // Playback state
    private var shuffleEnabled = false
    private var repeatMode = REPEAT_MODE_NONE
    
    interface OnPlaybackChangedListener {
        fun onTrackPlay(track: Track)
        fun onTrackPause()
        fun onTrackStop()
        fun onTrackComplete()
        fun onPlaybackError(errorMessage: String)
        fun onShuffleModeChanged(enabled: Boolean)
        fun onRepeatModeChanged(repeatMode: Int)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onProgressChanged(position: Int)
        fun onPlaybackCompleted()
    }
    
    fun playTrack(track: Track) {
        if (track == null) {
            Log.e(TAG, "Cannot play null track")
            return
        }
        
        // Check if we're already playing this track
        if (currentTrack?.trackId == track.trackId && mediaPlayer?.isPlaying == true) {
            Log.d(TAG, "Already playing this track: ${track.title}")
            return
        }
        
        // Store the track
        currentTrack = track
        
        // Choose playback strategy based on available sources
        
        // 1. Prioritize YouTube URL if available
        if (!track.youtubeUrl.isNullOrEmpty()) {
            Log.d(TAG, "Track has YouTube URL, playing directly: ${track.youtubeUrl}")
            playWithUrl(track.youtubeUrl)
            return
        }
        
        // 2. Try Piped URL if available
        if (!track.pipedUrl.isNullOrEmpty()) {
            Log.d(TAG, "Track has Piped URL, playing directly: ${track.pipedUrl}")
            playWithUrl(track.pipedUrl)
            return
        }
        
        // 3. Fall back to preview URL
        val audioUrl = track.previewUrl
        if (!audioUrl.isNullOrEmpty()) {
            Log.d(TAG, "Track has preview URL: $audioUrl")
            
            // Check if URL is a SoundCloud URL and needs resolution
            if (SoundCloudHelper.isSoundCloudUrl(audioUrl)) {
                Log.d(TAG, "SoundCloud URL detected: $audioUrl")
                
                // Notify we're loading a SoundCloud track
                listener?.onPlaybackError("Loading track from SoundCloud...")
                
                // Resolve SoundCloud URL asynchronously
                soundCloudHelper.getToken(object : SoundCloudHelper.SoundCloudCallback {
                    override fun onSuccess(token: String) {
                        try {
                            // Use the token to resolve the track
                            val resolvedUrl = soundCloudHelper.resolveTrackUrl(audioUrl)
                            if (resolvedUrl != null) {
                                Log.d(TAG, "SoundCloud URL resolved: $resolvedUrl")
                                listener?.onPlaybackError("SoundCloud track loaded!")
                                playWithUrl(resolvedUrl)
                            } else {
                                onError("Failed to resolve SoundCloud URL")
                            }
                        } catch (e: Exception) {
                            onError("Error: ${e.message}")
                        }
                    }
                    
                    override fun onError(errorMessage: String) {
                        Log.e(TAG, "SoundCloud error: $errorMessage")
                        listener?.onPlaybackError("SoundCloud error: $errorMessage")
                        
                        // Fallback to direct preview
                        val fallbackUrl = soundCloudHelper.getDirectPreviewUrl(audioUrl)
                        playWithUrl(fallbackUrl)
                    }
                })
                return
            }
            
            // Direct playback for standard URLs
            playWithUrl(audioUrl)
            return
        }
        
        // 4. Try to resolve using Spotify ID
        if (!track.spotifyId.isNullOrEmpty()) {
            Log.d(TAG, "Track has Spotify ID, resolving preview URL")
            resolveAndPlaySpotifyTrack(track)
            return
        }
        
        // 5. No playable URLs found
        listener?.onPlaybackError("This track has no playable sources available")
    }
    
    private fun resolveAndPlaySpotifyTrack(track: Track) {
        listener?.onPlaybackError("Loading track from Spotify...")
        
        spotifyHelper.getTrackPreviewUrl(track.spotifyId, object : SpotifyHelper.SpotifyCallback {
            override fun onSuccess(previewUrl: String) {
                if (!previewUrl.isNullOrEmpty()) {
                    Log.d(TAG, "Spotify preview URL: $previewUrl")
                    listener?.onPlaybackError("Spotify track loaded!")
                    
                    // Update the track's preview URL for future use
                    track.previewUrl = previewUrl
                    
                    // Play the track
                    playWithUrl(previewUrl)
                } else {
                    onError("No preview available for this track")
                }
            }
            
            override fun onError(errorMessage: String) {
                Log.e(TAG, "Spotify error: $errorMessage")
                listener?.onPlaybackError("Spotify error: $errorMessage")
                
                // Try fallback - use direct URL if we know it
                val directUrl = spotifyHelper.getDirectPreviewUrl(track.spotifyId)
                if (directUrl != null) {
                    playWithUrl(directUrl)
                }
            }
        })
    }
    
    private fun playWithUrl(audioUrl: String) {
        // Reset and release any existing MediaPlayer
        releaseMediaPlayer()
        
        Log.d(TAG, "Attempting to play URL: $audioUrl")
        
        try {
            // Create a new MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                isPreparing = true
                
                // Set audio attributes for better system integration
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                }
                
                // Ensure volume is at maximum
                setVolume(1.0f, 1.0f)
                
                // Set the data source
                try {
                    setDataSource(audioUrl)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting data source: ${e.message}", e)
                    listener?.onPlaybackError("Error setting data source: ${e.message}")
                    releaseMediaPlayer()
                    return
                }
                
                // Prepare the player asynchronously
                prepareAsync()
            }
            
            setupMediaPlayerListeners()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer: ${e.message}", e)
            listener?.onPlaybackError("Error initializing player: ${e.message}")
            releaseMediaPlayer()
        }
    }
    
    private fun setupMediaPlayerListeners() {
        mediaPlayer?.apply {
            setOnPreparedListener { mp ->
                Log.d(TAG, "MediaPlayer prepared successfully, starting playback")
                isPreparing = false
                
                // Start playback
                mp.start()
                
                // Update UI
                currentTrack?.let { track ->
                    listener?.onTrackPlay(track)
                }
                
                // Set up seek bar if available
                if (seekBar != null) {
                    startProgressUpdate()
                }
            }
            
            setOnCompletionListener { mp ->
                Log.d(TAG, "Playback completed")
                
                listener?.onTrackComplete()
                
                val currentTask = updateSeekBarTask
                if (handler != null && currentTask != null) {
                    handler.removeCallbacks(currentTask)
                }
            }
            
            setOnErrorListener { mp, what, extra ->
                isPreparing = false
                Log.e(TAG, "MediaPlayer error: $what, $extra for URL: ${currentTrack?.previewUrl}")
                
                // Handle specific errors
                when (what) {
                    MediaPlayer.MEDIA_ERROR_UNKNOWN -> {
                        if (extra == -2147483648) {
                            // This is the common error 1, -2147483648 
                            Log.e(TAG, "MediaPlayer error 1/-2147483648: Invalid media or connection issue")
                            
                            // Try YouTube URL if available
                            currentTrack?.youtubeUrl?.let { youtubeUrl ->
                                if (youtubeUrl.isNotEmpty()) {
                                    Log.d(TAG, "Trying YouTube URL as fallback: $youtubeUrl")
                                    playWithUrl(youtubeUrl)
                                    return@setOnErrorListener true
                                }
                            }
                            
                            // Try Piped URL if available
                            currentTrack?.pipedUrl?.let { pipedUrl ->
                                if (pipedUrl.isNotEmpty()) {
                                    Log.d(TAG, "Trying Piped URL as fallback: $pipedUrl")
                                    playWithUrl(pipedUrl)
                                    return@setOnErrorListener true
                                }
                            }
                            
                            // Try with direct URL if we have a Spotify ID as last resort
                            currentTrack?.spotifyId?.let { spotifyId ->
                                val directUrl = spotifyHelper.getDirectPreviewUrl(spotifyId)
                                if (!directUrl.isNullOrEmpty()) {
                                    Log.d(TAG, "Got fresh Spotify URL, retrying playback")
                                    playWithUrl(directUrl)
                                    return@setOnErrorListener true
                                }
                            }
                        }
                        
                        listener?.onPlaybackError("Playback error (Unknown)")
                    }
                    MediaPlayer.MEDIA_ERROR_SERVER_DIED -> {
                        // Try YouTube URL if available
                        currentTrack?.youtubeUrl?.let { youtubeUrl ->
                            if (youtubeUrl.isNotEmpty()) {
                                Log.d(TAG, "Server died, trying YouTube URL as fallback: $youtubeUrl")
                                playWithUrl(youtubeUrl)
                                return@setOnErrorListener true
                            }
                        }
                        
                        listener?.onPlaybackError("Server connection error")
                    }
                    else -> {
                        // Try YouTube URL for any other error too
                        currentTrack?.youtubeUrl?.let { youtubeUrl ->
                            if (youtubeUrl.isNotEmpty()) {
                                Log.d(TAG, "Error $what, trying YouTube URL as fallback: $youtubeUrl")
                                playWithUrl(youtubeUrl)
                                return@setOnErrorListener true
                            }
                        }
                        
                        listener?.onPlaybackError("Playback error: $what")
                    }
                }
                
                // Release resources on error
                releaseMediaPlayer()
                true // Error handled
            }
            
            setOnInfoListener { mp, what, extra ->
                Log.d(TAG, "MediaPlayer info: $what, $extra")
                false
            }
            
            setOnBufferingUpdateListener { mp, percent ->
                Log.d(TAG, "Buffering: $percent%")
            }
        }
    }
    
    fun resumePlayback() {
        if (mediaPlayer != null && !mediaPlayer!!.isPlaying && !isPreparing) {
            mediaPlayer?.start()
            currentTrack?.let { track ->
                listener?.onTrackPlay(track)
            }
            
            if (seekBar != null) {
                startProgressUpdate()
            }
        }
    }
    
    fun pausePlayback() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            listener?.onTrackPause()
            
            val currentTask = updateSeekBarTask
            if (handler != null && currentTask != null) {
                handler.removeCallbacks(currentTask)
            }
        }
    }
    
    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            
            listener?.onTrackStop()
            
            val currentTask = updateSeekBarTask
            if (handler != null && currentTask != null) {
                handler.removeCallbacks(currentTask)
            }
        }
    }
    
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
    
    fun getCurrentTrack(): Track? {
        return currentTrack
    }
    
    fun setSeekBar(seekBar: SeekBar) {
        this.seekBar = seekBar
        
        this.seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Pause updates while user is dragging
                val currentHandler = handler
                val currentTask = updateSeekBarTask
                if (currentHandler != null && currentTask != null) {
                    currentHandler.removeCallbacks(currentTask)
                }
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Resume updates when user stops dragging
                if (mediaPlayer?.isPlaying == true) {
                    startProgressUpdate()
                }
            }
        })
        
        if (mediaPlayer?.isPlaying == true) {
            startProgressUpdate()
        }
    }
    
    fun setOnPlaybackChangedListener(listener: OnPlaybackChangedListener) {
        this.listener = listener
    }
    
    fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        
        val currentTask = updateSeekBarTask
        if (currentTask != null) {
            handler.removeCallbacks(currentTask)
        }
        
        currentTrack = null
        isPreparing = false
    }
    
    fun toggleShuffle(): Boolean {
        shuffleEnabled = !shuffleEnabled
        Log.d(TAG, "Shuffle mode: ${if (shuffleEnabled) "enabled" else "disabled"}")
        
        listener?.onShuffleModeChanged(shuffleEnabled)
        
        return shuffleEnabled
    }
    
    fun setShuffleEnabled(enabled: Boolean) {
        if (shuffleEnabled != enabled) {
            shuffleEnabled = enabled
            Log.d(TAG, "Shuffle mode: ${if (shuffleEnabled) "enabled" else "disabled"}")
            
            listener?.onShuffleModeChanged(shuffleEnabled)
        }
    }
    
    fun isShuffleEnabled(): Boolean {
        return shuffleEnabled
    }
    
    fun cycleRepeatMode(): Int {
        repeatMode = when (repeatMode) {
            REPEAT_MODE_NONE -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_NONE
            else -> REPEAT_MODE_NONE
        }
        
        Log.d(TAG, "Repeat mode: ${getRepeatModeName()}")
        
        listener?.onRepeatModeChanged(repeatMode)
        
        return repeatMode
    }
    
    fun setRepeatMode(mode: Int) {
        val newMode = when (mode) {
            in REPEAT_MODE_NONE..REPEAT_MODE_ONE -> mode
            else -> REPEAT_MODE_NONE
        }
        
        if (repeatMode != newMode) {
            repeatMode = newMode
            Log.d(TAG, "Repeat mode: ${getRepeatModeName()}")
            
            listener?.onRepeatModeChanged(repeatMode)
        }
    }
    
    fun getRepeatMode(): Int {
        return repeatMode
    }
    
    private fun getRepeatModeName(): String {
        return when (repeatMode) {
            REPEAT_MODE_NONE -> "None"
            REPEAT_MODE_ONE -> "One"
            REPEAT_MODE_ALL -> "All"
            else -> "Unknown"
        }
    }
    
    fun getCurrentPosition(): Long {
        return mediaPlayer?.currentPosition?.toLong() ?: 0L
    }
    
    fun seekTo(position: Long) {
        mediaPlayer?.seekTo(position.toInt())
    }
    
    private fun startProgressUpdate() {
        val currentHandler = handler ?: return
        val currentSeekBar = seekBar
        val currentPlayer = mediaPlayer
        
        // Remove any existing callback
        val currentTask = updateSeekBarTask
        if (currentTask != null) {
            currentHandler.removeCallbacks(currentTask)
        }

        // Create a new Runnable that will be stored as a class member
        val progressUpdateTask = object : Runnable {
            override fun run() {
                if (currentPlayer?.isPlaying == true) {
                    val position = currentPlayer.currentPosition
                    listener?.onProgressChanged(position)
                    currentSeekBar?.progress = position
                    
                    // Use the same handler instance we captured at the start
                    currentHandler.postDelayed(this, 1000)
                }
            }
        }

        // Store the task first
        updateSeekBarTask = progressUpdateTask
        
        // Then post it using our captured handler
        currentHandler.post(progressUpdateTask)
    }
} 