package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import my.edu.utar.bananamusic.models.Track;

/**
 * A utility class for playing songs using Spotify API
 */
public class SpotifyPlayer implements AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "SpotifyPlayer";

    private Context context;
    private SpotifyHelper spotifyHelper;
    private MediaPlayer mediaPlayer;
    private Track currentTrack;
    private PlayerCallback playerCallback;
    private boolean isPreparing = false;
    
    // Audio focus management
    private AudioManager audioManager;
    private boolean audioFocusGranted = false;
    private boolean wasPlayingBeforeFocusLoss = false;
    private AudioFocusRequest audioFocusRequest;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private int retryCount = 0;

    public interface PlayerCallback {
        void onPlaybackStarted(Track track);
        void onPlaybackPaused();
        void onPlaybackStopped();
        void onPlaybackError(String message);
        void onBufferingUpdate(int percent);
    }

    public SpotifyPlayer(Context context) {
        this.context = context.getApplicationContext();
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        setupMediaPlayer();
    }

    private void setupMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            );

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                String errorMessage = "Unknown error";
                switch (what) {
                    case MediaPlayer.MEDIA_ERROR_IO:
                        errorMessage = "Network error or I/O operation failed";
                        break;
                    case MediaPlayer.MEDIA_ERROR_MALFORMED:
                        errorMessage = "Malformed media data";
                        break;
                    case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                        errorMessage = "Unsupported format";
                        break;
                    case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                        errorMessage = "Operation timed out";
                        break;
                }
                
                Log.e(TAG, "MediaPlayer error: " + errorMessage);
                if (playerCallback != null) {
                    playerCallback.onPlaybackError(errorMessage);
                }
                return true;
            });

            mediaPlayer.setOnBufferingUpdateListener((mp, percent) -> {
                if (playerCallback != null) {
                    playerCallback.onBufferingUpdate(percent);
                }
            });
            
            // Set max volume
            mediaPlayer.setVolume(1.0f, 1.0f);
        }
    }

    /**
     * Play a track using its Spotify ID
     * @param spotifyId The Spotify ID of the track to play
     */
    public void playTrack(String spotifyId) {
        if (spotifyId == null || spotifyId.isEmpty()) {
            if (playerCallback != null) {
                playerCallback.onPlaybackError("Invalid Spotify ID");
            }
            return;
        }

        // Request audio focus before starting playback
        if (!requestAudioFocus()) {
            if (playerCallback != null) {
                playerCallback.onPlaybackError("Cannot get audio focus");
            }
            return;
        }

        releaseMediaPlayer();
        setupMediaPlayer();
        
        // Create a temporary track with the information we have
        currentTrack = new Track();
        currentTrack.setSpotifyId(spotifyId);
        currentTrack.setTitle("Loading...");
        currentTrack.setArtist("Loading...");
        currentTrack.setSource(Track.SOURCE_SPOTIFY);
        
        // First, get the track's preview URL from Spotify
        spotifyHelper.getTrackPreviewUrl(spotifyId, new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    
                    // Update the track with full information
                    if (json.has("name")) {
                        currentTrack.setTitle(json.getString("name"));
                    }
                    
                    if (json.has("artists") && json.getJSONArray("artists").length() > 0) {
                        currentTrack.setArtist(json.getJSONArray("artists").getJSONObject(0).getString("name"));
                    }
                    
                    if (json.has("album") && json.getJSONObject("album").has("name")) {
                        currentTrack.setAlbum(json.getJSONObject("album").getString("name"));
                    }
                    
                    if (json.has("album") && json.getJSONObject("album").has("images") && 
                        json.getJSONObject("album").getJSONArray("images").length() > 0) {
                        currentTrack.setAlbumArtUrl(json.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url"));
                    }
                    
                    if (json.has("duration_ms")) {
                        currentTrack.setDurationMs(json.getInt("duration_ms"));
                    }
                    
                    // Check if we have a preview URL
                    String previewUrl = null;
                    if (json.has("preview_url") && !json.isNull("preview_url")) {
                        previewUrl = json.getString("preview_url");
                        currentTrack.setPreviewUrl(previewUrl);
                    }
                    
                    if (previewUrl != null && !previewUrl.isEmpty()) {
                        playPreviewUrl(previewUrl);
                    } else {
                        // Try fallback method for getting audio
                        fallbackToAudioLink(spotifyId);
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing track info JSON: " + e.getMessage());
                    if (playerCallback != null) {
                        playerCallback.onPlaybackError("Failed to parse track information");
                    }
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting track preview URL: " + message);
                fallbackToAudioLink(spotifyId);
            }
        });
    }
    
    /**
     * Try to play a track using a direct preview URL
     */
    private void playPreviewUrl(String previewUrl) {
        if (previewUrl == null || previewUrl.isEmpty()) {
            if (playerCallback != null) {
                playerCallback.onPlaybackError("Preview URL is empty");
            }
            return;
        }
        
        try {
            isPreparing = true;
            mediaPlayer.reset();
            
            Log.d(TAG, "Setting data source to URL: " + previewUrl);
            mediaPlayer.setDataSource(previewUrl);
            
            mediaPlayer.setOnPreparedListener(mp -> {
                isPreparing = false;
                Log.d(TAG, "MediaPlayer prepared successfully, starting playback");
                
                // Reset retry count on successful playback
                retryCount = 0;
                
                // Set volume to maximum
                mediaPlayer.setVolume(1.0f, 1.0f);
                
                // Start playback
                mediaPlayer.start();
                
                if (playerCallback != null) {
                    playerCallback.onPlaybackStarted(currentTrack);
                }
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                isPreparing = false;
                
                if (retryCount < MAX_RETRY_ATTEMPTS) {
                    retryCount++;
                    Log.d(TAG, "Retrying playback attempt " + retryCount);
                    // Retry with exponential backoff
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        playPreviewUrl(previewUrl);
                    }, 1000 * retryCount);
                    return true;
                }
                
                // Reset retry count
                retryCount = 0;
                
                // Try fallback
                fallbackToAudioLink(currentTrack.getSpotifyId());
                return true;
            });
            
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "Error playing preview URL: " + e.getMessage());
            isPreparing = false;
            
            if (playerCallback != null) {
                playerCallback.onPlaybackError("Failed to play preview: " + e.getMessage());
            }
            
            // Try fallback on exception
            fallbackToAudioLink(currentTrack.getSpotifyId());
        }
    }
    
    /**
     * Try to get an alternative audio link if preview URL fails
     */
    private void fallbackToAudioLink(String spotifyId) {
        spotifyHelper.getFullTrackInfo(spotifyId, new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    
                    // Update the track with full information if not already done
                    if (currentTrack.getTitle().equals("Loading...")) {
                        if (json.has("name")) {
                            currentTrack.setTitle(json.getString("name"));
                        }
                        
                        if (json.has("artists") && json.getJSONArray("artists").length() > 0) {
                            currentTrack.setArtist(json.getJSONArray("artists").getJSONObject(0).getString("name"));
                        }
                        
                        if (json.has("album") && json.getJSONObject("album").has("name")) {
                            currentTrack.setAlbum(json.getJSONObject("album").getString("name"));
                        }
                        
                        if (json.has("album") && json.getJSONObject("album").has("images") && 
                            json.getJSONObject("album").getJSONArray("images").length() > 0) {
                            currentTrack.setAlbumArtUrl(json.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url"));
                        }
                        
                        if (json.has("duration_ms")) {
                            currentTrack.setDurationMs(json.getInt("duration_ms"));
                        }
                    }
                    
                    // Extract a preview URL if present in the full track info
                    String previewUrl = null;
                    if (json.has("preview_url") && !json.isNull("preview_url")) {
                        previewUrl = json.getString("preview_url");
                        if (previewUrl != null && !previewUrl.isEmpty()) {
                            currentTrack.setPreviewUrl(previewUrl);
                            playPreviewUrl(previewUrl);
                            return;
                        }
                    }
                    
                    // If we still don't have a preview URL, try the fallback URL
                    String fallbackUrl = spotifyHelper.getFallbackAudioUrl();
                    if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                        playPreviewUrl(fallbackUrl);
                    } else {
                        // Use a hardcoded sample audio as last resort
                        String sampleAudio = "https://p.scdn.co/mp3-preview/54ee893cc2141cd847401d95ae24d9adeb0c25a4";
                        playPreviewUrl(sampleAudio);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing fallback track info: " + e.getMessage());
                    if (playerCallback != null) {
                        playerCallback.onPlaybackError("Failed to parse track information");
                    }
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting fallback track info: " + message);
                // Try to play a sample audio as last resort
                String sampleAudio = "https://p.scdn.co/mp3-preview/54ee893cc2141cd847401d95ae24d9adeb0c25a4";
                playPreviewUrl(sampleAudio);
            }
        });
    }

    /**
     * Pause playback
     */
    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (playerCallback != null) {
                playerCallback.onPlaybackPaused();
            }
        }
    }

    /**
     * Resume playback
     */
    public void resume() {
        // Request audio focus before resuming
        if (!requestAudioFocus()) {
            if (playerCallback != null) {
                playerCallback.onPlaybackError("Cannot get audio focus");
            }
            return;
        }
        
        if (mediaPlayer != null && !mediaPlayer.isPlaying() && !isPreparing) {
            mediaPlayer.start();
            if (playerCallback != null) {
                playerCallback.onPlaybackStarted(currentTrack);
            }
        }
    }

    /**
     * Stop playback
     */
    public void stop() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            
            // Abandon audio focus
            abandonAudioFocus();
            
            if (playerCallback != null) {
                playerCallback.onPlaybackStopped();
            }
        }
    }
    
    /**
     * Check if player is currently playing
     */
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
    
    /**
     * Get the currently playing track
     */
    public Track getCurrentTrack() {
        return currentTrack;
    }
    
    /**
     * Set a callback listener for player events
     */
    public void setPlayerCallback(PlayerCallback callback) {
        this.playerCallback = callback;
    }
    
    /**
     * Release the media player resources
     */
    public void releaseMediaPlayer() {
        // Abandon audio focus
        abandonAudioFocus();
        
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    
    /**
     * Request audio focus
     * @return true if audio focus was granted, false otherwise
     */
    private boolean requestAudioFocus() {
        if (audioManager == null) {
            return false;
        }
        
        int result;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
                
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build();
                
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        }
        
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusGranted = true;
            return true;
        } else {
            audioFocusGranted = false;
            return false;
        }
    }
    
    /**
     * Abandon audio focus
     */
    private void abandonAudioFocus() {
        if (audioManager == null) {
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(this);
        }
        
        audioFocusGranted = false;
    }
    
    /**
     * Handle audio focus changes
     */
    @Override
    public void onAudioFocusChange(int focusChange) {
        if (audioManager == null) {
            Log.e(TAG, "AudioManager is null in onAudioFocusChange");
            return;
        }
        
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // Resume playback
                Log.d(TAG, "Audio focus gained");
                if (mediaPlayer != null) {
                    if (wasPlayingBeforeFocusLoss) {
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                            if (playerCallback != null) {
                                playerCallback.onPlaybackStarted(currentTrack);
                            }
                        }
                    }
                    // Set volume to maximum
                    mediaPlayer.setVolume(1.0f, 1.0f);
                }
                wasPlayingBeforeFocusLoss = false;
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS:
                // Stop playback
                Log.d(TAG, "Audio focus lost");
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    wasPlayingBeforeFocusLoss = true;
                    mediaPlayer.pause();
                    if (playerCallback != null) {
                        playerCallback.onPlaybackPaused();
                    }
                } else {
                    wasPlayingBeforeFocusLoss = false;
                }
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Pause playback
                Log.d(TAG, "Audio focus lost temporarily");
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    wasPlayingBeforeFocusLoss = true;
                    mediaPlayer.pause();
                    if (playerCallback != null) {
                        playerCallback.onPlaybackPaused();
                    }
                } else {
                    wasPlayingBeforeFocusLoss = false;
                }
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lower the volume
                Log.d(TAG, "Audio focus lost temporarily, can duck");
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    // Lower the volume to 30% while ducking
                    mediaPlayer.setVolume(0.3f, 0.3f);
                }
                break;
        }
    }
}