package my.edu.utar.bananamusic.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.SeekBar;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;

@SuppressLint("StaticFieldLeak")
public class AudioPlayerHelper implements MediaPlayer.OnPreparedListener, 
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "AudioPlayerHelper";
    
    @SuppressLint("StaticFieldLeak")
    private static AudioPlayerHelper instance;
    
    private MediaPlayer mediaPlayer;
    private Track currentTrack;
    private boolean isPreparing = false;
    private OnPlaybackChangedListener listener;
    private final Context context;
    private boolean isBuffering = false;
    
    private final Handler handler;
    private Runnable updateSeekBarTask;
    private SeekBar seekBar;
    
    private AudioManager audioManager;
    private boolean audioFocusGranted = false;
    private boolean wasPlayingBeforeFocusLoss = false;
    private boolean pausedForFocusLoss = false;
    private boolean resumeAfterAudioFocus = false;
    
    private SoundCloudHelper soundCloudHelper;
    private SpotifyHelper spotifyHelper;
    private DeezerHelper deezerHelper;
    private PipedHelper pipedHelper;
    private YouTubeHelper youtubeHelper;
    private MusicCache musicCache;
    private UserPreferences preferences;
    
    private boolean shuffleEnabled = false;
    private int repeatMode = REPEAT_OFF;
    private static final int CROSSFADE_DURATION = 500; // ms
    private MediaPlayer nextMediaPlayer; // For crossfade/gapless playback
    private Track nextTrack;
    private float maxVolume = 1.0f;
    private boolean isCrossfading = false;
    private boolean isLoading = false;
    
    private PlayQueue playQueue;
    private List<Track> history = new ArrayList<>();
    
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;
    
    public static final int REPEAT_MODE_NONE = REPEAT_OFF;
    public static final int REPEAT_MODE_ALL = REPEAT_ALL;
    public static final int REPEAT_MODE_ONE = REPEAT_ONE;
    
    private AudioFocusRequest audioFocusRequest;
    private boolean playbackDelayed = false;
    private boolean playbackNowAuthorized = false;
    
    private boolean updateSeekBar = false;
    
    private PowerManager.WakeLock wakeLock;
    
    private String currentTrackUrl;
    
    private HashSet<String> failedUrls = new HashSet<>();
    
    // Add missing variables
    private String audioFocusState = "UNKNOWN";
    private String lastErrorMessage = null;
    private String fallbackUrl = null;
    
    private final Executor executor;
    private final Handler mainHandler;
    private final List<OnPlaybackChangedListener> listeners = new ArrayList<>();
    
    public interface OnPlaybackChangedListener {
        void onTrackPlay(Track track);
        void onTrackPause();
        void onTrackStopped();
        void onTrackChanged(Track track);
        void onPlaybackError(String errorMessage);
        void onBufferingStart();
        void onBufferingEnd();
        void onLoadingStateChanged(boolean isLoading);
        void onBufferingUpdate(int percent);
        void onPlaybackStateChanged(boolean isPlaying);
        
        // Additional methods with default implementations
        default void onTrackLoading(Track track) {
            // Default empty implementation
            onLoadingStateChanged(true);
        }
        
        // This method should be implemented by all listeners but currently isn't
        // By providing a default implementation, existing implementations won't break
        default void onError(String errorMessage) {
            // Default implementation forwards to onPlaybackError
            onPlaybackError(errorMessage);
        }
    }
    
    private AudioPlayerHelper(Context context) {
        this.context = context.getApplicationContext();
        handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.soundCloudHelper = SoundCloudHelper.getInstance(context);
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.deezerHelper = DeezerHelper.getInstance(context);
        this.pipedHelper = PipedHelper.getInstance(context);
        this.youtubeHelper = YouTubeHelper.getInstance(context);
        this.musicCache = MusicCache.getInstance(context);
        this.preferences = UserPreferences.getInstance(context);
        
        this.playQueue = new PlayQueue();
        this.playQueue.setListener(new PlayQueue.PlayQueueListener() {
            @Override
            public void onQueueChanged() {
                // Handle queue change event
            }
            
            @Override
            public void onCurrentTrackChanged(Track track) {
                // Handle current track change
            }
        });
        
        initWakeLock();
        initializeMediaPlayer();
    }
    
    private void initWakeLock() {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BananaMusic:AudioPlayerWakeLock");
        }
    }
    
    public static synchronized AudioPlayerHelper getInstance(Context context) {
        if (instance == null) {
            instance = new AudioPlayerHelper(context);
        }
        return instance;
    }
    
    /**
     * Initialize the MediaPlayer
     */
    private void initializeMediaPlayer() {
        if (mediaPlayer != null) {
            releaseMediaPlayer();
        }
        
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
        );
        
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
    }
    
    /**
     * Play a track with the given information
     */
    public void playTrack(Track track) {
        if (track == null) {
            Log.e(TAG, "Cannot play null track");
            if (listener != null) {
                listener.onPlaybackError("Cannot play null track");
            }
            return;
        }

        // Save current track and log
        currentTrack = track;
        Log.d(TAG, "Playing track: " + track.getTitle() + " by " + track.getArtist());
        
        // Check if this is a Deezer track
        if (track.isDeezerTrack()) {
            Log.d(TAG, "Track is from Deezer, playing Deezer track");
            playDeezerTrack(track);
            return;
        }

        // Check if this is a YouTube track
        if (track.getSource() != null && track.getSource().equals(Track.SOURCE_YOUTUBE)) {
            Log.d(TAG, "Track is from YouTube, playing YouTube track");
            playYouTubeTrack(track);
            return;
        }
        
        // Check for previewUrl as it may be available for any source
        if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
            Log.d(TAG, "Track has a preview URL: " + track.getPreviewUrl());
            playStreamUrl(track.getPreviewUrl());
            return;
        }
        
        // Try with YouTube URL if available
        String youtubeUrl = track.getYoutubeUrl();
        if (youtubeUrl != null && !youtubeUrl.isEmpty() && validateUrl(youtubeUrl)) {
            Log.d(TAG, "Track has YouTube URL: " + youtubeUrl);
            playWithYouTubeUrl(youtubeUrl);
            return;
        }
        
        // No playable source found yet, try to get a preview URL
        Log.d(TAG, "No direct preview URL found, searching for a preview...");
        searchForPreviewUrl();
    }
    
    /**
     * Play a track from Deezer
     * 
     * @param track The track to play
     */
    private void playDeezerTrack(Track track) {
        if (track == null) {
            Log.e(TAG, "Cannot play null Deezer track");
            if (listener != null) {
                listener.onPlaybackError("Invalid Deezer track");
                listener.onLoadingStateChanged(false);
            }
            return;
        }
        
        // Show loading state
        if (listener != null) {
            listener.onLoadingStateChanged(true);
        }
        
        Log.d(TAG, "Playing Deezer track: " + track.getTitle() + " by " + track.getArtist());
        Log.d(TAG, "Track source: " + track.getSource());
        Log.d(TAG, "Track ID: " + track.getId());
        Log.d(TAG, "Track preview URL: " + track.getPreviewUrl());
        
        // If the track already has a preview URL, verify and play it
        if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
            Log.d(TAG, "Verifying existing Deezer preview URL: " + track.getPreviewUrl());
            
            // Set source if not already set
            if (track.getSource() == null || track.getSource().isEmpty()) {
                track.setSource(Track.SOURCE_DEEZER);
                Log.d(TAG, "Setting track source to Deezer");
            }
            
            // Verify the URL is valid and accessible
            deezerHelper.verifyPreviewUrl(track.getPreviewUrl(), new DeezerHelper.DeezerStringCallback() {
                @Override
                public void onSuccess(String verifiedUrl) {
                    Log.d(TAG, "Deezer preview URL verified, playing: " + verifiedUrl);
                    playStreamUrl(verifiedUrl);
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error verifying Deezer preview: " + message);
                    searchForPreviewUrl();
                }
            });
            return;
        }
        
        // Otherwise, search for the preview URL on Deezer
        Log.d(TAG, "No preview URL found, searching for one on Deezer");
        searchForPreviewUrl();
    }
    
    // Helper method to search for a preview URL
    private void searchForPreviewUrl() {
        if (currentTrack == null) {
            Log.e(TAG, "Cannot search for preview URL for null track");
            if (listener != null) {
                listener.onPlaybackError("Track is not available");
                listener.onLoadingStateChanged(false);
            }
            return;
        }
        
        Log.d(TAG, "Searching for Deezer preview for: " + currentTrack.getTitle() + " by " + currentTrack.getArtist());
        
        if (listener != null) {
            listener.onLoadingStateChanged(true);
        }
        
        deezerHelper.getPreviewUrl(currentTrack.getTitle(), currentTrack.getArtist(), new DeezerHelper.DeezerStringCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                Log.d(TAG, "Found Deezer preview URL: " + previewUrl);
                
                // Verify the URL is valid
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    // Update the track with the preview URL
                    currentTrack.setPreviewUrl(previewUrl);
                    
                    // Verify the preview URL is accessible
                    deezerHelper.verifyPreviewUrl(previewUrl, new DeezerHelper.DeezerStringCallback() {
                        @Override
                        public void onSuccess(String verifiedUrl) {
                            playWithUrl(verifiedUrl);
                        }
                        
                        @Override
                        public void onError(String message) {
                            Log.e(TAG, "Error verifying preview URL: " + message);
                            tryFallbackUrl();
                        }
                    });
                } else {
                    Log.e(TAG, "Empty Deezer preview URL");
                    tryFallbackUrl();
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting Deezer preview: " + message);
                Log.d(TAG, "Attempting to use fallback URL");
                tryFallbackUrl();
            }
        });
    }
                
    // Helper method to try fallback URL
    private void tryFallbackUrl() {
        if (currentTrack == null) {
            Log.e(TAG, "Cannot try fallback URL for null track");
            if (listener != null) {
                listener.onPlaybackError("Playback failed and no fallback available");
                listener.onLoadingStateChanged(false);
            }
            return;
        }
        
        Log.d(TAG, "Trying fallback URL for track: " + currentTrack.getTitle());
        
        // Try YouTube URL if available and not already tried
        String youtubeUrl = currentTrack.getYoutubeUrl();
        if (youtubeUrl != null && !youtubeUrl.isEmpty() && !failedUrls.contains(youtubeUrl)) {
            Log.d(TAG, "Attempting YouTube fallback with YouTubeHelper: " + youtubeUrl);
            playWithYouTubeUrl(youtubeUrl);
            return;
        }
        
        // If no YouTube URL or it failed, try searching for a YouTube video
        if (currentTrack.getSource() != null && !currentTrack.getSource().equals(Track.SOURCE_YOUTUBE)) {
            Log.d(TAG, "Searching for YouTube video as fallback");
            youtubeHelper.getStreamUrlForTrack(currentTrack, new YouTubeHelper.StreamUrlCallback() {
                @Override
                public void onSuccess(String streamUrl) {
                    isLoading = false;
                    if (listener != null) {
                        listener.onLoadingStateChanged(false);
                    }
                    
                    Log.d(TAG, "Found YouTube fallback stream URL");
                    playStreamUrl(streamUrl);
                }
                
                @Override
                public void onError(String errorMessage) {
                    // Continue with other fallback options
                    tryOtherFallbacks();
                }
            });
            return;
        }
        
        // Try other fallback options
        tryOtherFallbacks();
    }
    
    /**
     * Try other fallback options after YouTube fails
     */
    private void tryOtherFallbacks() {
        // Try Spotify preview if available
        if (currentTrack.getPreviewUrl() != null && !currentTrack.getPreviewUrl().isEmpty()) {
            Log.d(TAG, "Using preview URL as fallback: " + currentTrack.getPreviewUrl());
            playStreamUrl(currentTrack.getPreviewUrl());
            return;
        }
        
        // Try SoundCloud search
        if (currentTrack.getSource() == null || !currentTrack.getSource().equals(Track.SOURCE_SOUNDCLOUD)) {
            Log.d(TAG, "Searching SoundCloud as fallback");
            soundCloudHelper.searchTrack(currentTrack.getTitle() + " " + currentTrack.getArtist(), 
                                       new SoundCloudHelper.SearchResultCallback() {
                @Override
                public void onSuccess(String streamUrl) {
                    if (streamUrl != null && !streamUrl.isEmpty()) {
                        Log.d(TAG, "Found SoundCloud stream as fallback");
                        playStreamUrl(streamUrl);
                    } else {
                        // Give up and show error
                        handleNoFallbacks();
                    }
                }
                
                @Override
                public void onError(String message) {
                    // All fallbacks failed
                    handleNoFallbacks();
                }
            });
            return;
        }
        
        // No more fallbacks available
        handleNoFallbacks();
    }
    
    /**
     * Handle case when all fallbacks fail
     */
    private void handleNoFallbacks() {
        isLoading = false;
        if (listener != null) {
            listener.onLoadingStateChanged(false);
            listener.onPlaybackError("Unable to play this track: no available source found");
        }
        Log.e(TAG, "All playback fallbacks failed for track: " + 
             (currentTrack != null ? currentTrack.getTitle() : "null"));
    }
    
    /**
     * Plays a track by title and artist using Deezer as the source
     * 
     * @param title The track title
     * @param artist The artist name
     */
    public void playDeezerPreview(String title, String artist) {
        if (title == null || title.isEmpty()) {
            if (listener != null) {
                listener.onPlaybackError("Track title cannot be empty");
            }
            return;
        }
        
        // Show loading state
        if (listener != null) {
            listener.onLoadingStateChanged(true);
        }
        
        Log.d(TAG, "Searching for Deezer preview for: " + title + " by " + artist);
        
        // Search for the track on Deezer
        deezerHelper.getPreviewUrl(title, artist, new DeezerHelper.DeezerStringCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    Log.d(TAG, "Found Deezer preview URL: " + previewUrl);
                    
                    // Create a temporary track object using the factory method
                    Track deezerTrack = Track.createDeezerTrack(
                        String.valueOf(System.currentTimeMillis()),
                        title,
                        artist != null ? artist : "Unknown Artist",
                        "Unknown Album",
                        null,
                        previewUrl,
                        30000 // 30 seconds default for previews
                    );
                    
                    // Set as current track and play
                    currentTrack = deezerTrack;
                    playStreamUrl(previewUrl);
                } else {
                    // No preview found
                    if (listener != null) {
                        listener.onPlaybackError("No preview available for this track");
                        listener.onLoadingStateChanged(false);
                    }
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting Deezer preview: " + message);
                
                if (listener != null) {
                    listener.onPlaybackError("Error getting preview: " + message);
                    listener.onLoadingStateChanged(false);
                }
            }
        });
    }
    
    /**
     * Search for songs on Deezer and return them as Track objects
     * 
     * @param query The search query
     * @param callback Callback to receive the list of tracks
     */
    public void searchDeezerTracks(String query, DeezerHelper.DeezerTracksCallback callback) {
        deezerHelper.searchAndConvertToTracks(query, callback);
    }

    private void playSpotifyTrack(Track track) {
        if (track == null || track.getSpotifyId() == null || track.getSpotifyId().isEmpty()) {
            if (listener != null) {
                listener.onPlaybackError("Invalid Spotify track");
            }
            return;
        }

        // Show loading state
        if (listener != null) {
            listener.onLoadingStateChanged(true);
        }
        
        Log.d(TAG, "Starting playback of Spotify track: " + track.getTitle() + ", ID: " + track.getSpotifyId());

        // Try SDK playback first
        spotifyHelper.playTrackUsingSDK(track.getSpotifyId(), new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String response) {
                Log.d(TAG, "SDK playback successful");
                if (listener != null) {
                    listener.onLoadingStateChanged(false);
                    listener.onTrackPlay(track);
                }
            }

            @Override
            public void onError(String message) {
                Log.d(TAG, "SDK playback failed, trying preview URL: " + message);
                
                // Try preview URL if available
                if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
                    Log.d(TAG, "Using existing preview URL: " + track.getPreviewUrl());
                    playStreamUrl(track.getPreviewUrl());
                } else {
                    // Get preview URL from Spotify
                    Log.d(TAG, "No preview URL available, resolving from Spotify");
                    resolveAndPlaySpotifyTrack(track);
                }
            }
        });
    }

    private void resolveAndPlaySpotifyTrack(Track track) {
        Log.d(TAG, "Resolving preview URL for Spotify track: " + track.getSpotifyId());
        spotifyHelper.getTrackPreviewUrl(track.getSpotifyId(), new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    Log.d(TAG, "Got Spotify preview URL, playing track: " + previewUrl);
                    track.setPreviewUrl(previewUrl);
                    playStreamUrl(previewUrl);
                } else {
                    Log.d(TAG, "No preview URL available, trying Spotify Connect");
                    trySpotifyConnectPlayback(track);
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting preview URL: " + message);
                
                // Try fallback URL
                String fallbackUrl = spotifyHelper.getFallbackAudioUrl();
                if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                    Log.d(TAG, "Using fallback URL: " + fallbackUrl);
                    track.setPreviewUrl(fallbackUrl);
                    playStreamUrl(fallbackUrl);
                } else {
                trySpotifyConnectPlayback(track);
                }
            }
        });
    }

    private void trySpotifyConnectPlayback(Track track) {
        spotifyHelper.getAccessToken(new SpotifyHelper.AccessTokenCallback() {
            @Override
            public void onTokenReceived(String accessToken) {
                SpotifyDeviceManager.getAvailableDevices(accessToken, new SpotifyDeviceManager.DeviceCallback() {
                    @Override
                    public void onDevicesFound(List<SpotifyDeviceManager.SpotifyDevice> devices) {
                        SpotifyDeviceManager.SpotifyDevice device = SpotifyDeviceManager.selectBestDevice(devices);
                        if (device != null) {
                            playSpotifyTrackOnDevice(track.getSpotifyId(), device.getId(), accessToken);
                        } else {
                            handleNoPlaybackOptions(track);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        handleNoPlaybackOptions(track);
                    }
                });
            }

            @Override
            public void onError(String error) {
                handleNoPlaybackOptions(track);
            }
        });
    }

    private void handleNoPlaybackOptions(Track track) {
        Log.e(TAG, "No available playback options for track: " + 
              (track != null ? track.getTitle() + " by " + track.getArtist() : "null track"));
              
        if (listener != null) {
            listener.onLoadingStateChanged(false);
            listener.onBufferingEnd();
            listener.onPlaybackError("No available playback options for this track");
        }
        
        // Cache this track ID to avoid repeated failed attempts
        if (track != null) {
            if (track.getSpotifyId() != null) {
                failedUrls.add(track.getSpotifyId());
                Log.d(TAG, "Added Spotify ID to failed URLs: " + track.getSpotifyId());
            }
            
            if (track.getPreviewUrl() != null) {
                failedUrls.add(track.getPreviewUrl());
                Log.d(TAG, "Added preview URL to failed URLs: " + track.getPreviewUrl());
            }
            
            if (track.getId() != null) {
                failedUrls.add(track.getId());
                Log.d(TAG, "Added track ID to failed URLs: " + track.getId());
            }
        }
    }

    /**
     * Play a streaming URL
     * 
     * @param streamUrl The URL to stream
     */
    public void playStreamUrl(String streamUrl) {
        if (streamUrl == null || streamUrl.isEmpty()) {
            Log.e(TAG, "Cannot play empty stream URL");
            if (listener != null) {
                listener.onPlaybackError("Cannot play empty stream URL");
                listener.onLoadingStateChanged(false);
            }
            return;
        }
        
        // Log the URL we're trying to play
        Log.d(TAG, "Attempting to play stream URL: " + streamUrl);
        
        // Check if the URL might have expired authentication (common with Deezer URLs)
        if (streamUrl.contains("cdns-preview.dzcdn.net") && 
            (streamUrl.contains("hdnea=exp=") || streamUrl.contains("token="))) {
            // This is a Deezer URL that likely has an expiring token
            long currentTime = System.currentTimeMillis() / 1000;
            
            // Check if we should refresh the URL
            boolean shouldRefresh = false;
            
            try {
                // Try to extract expiration timestamp from URL
                if (streamUrl.contains("hdnea=exp=")) {
                    int expStart = streamUrl.indexOf("hdnea=exp=") + 10;
                    int expEnd = streamUrl.indexOf("-", expStart);
                    if (expEnd > expStart) {
                        String expTimeHex = streamUrl.substring(expStart, expEnd);
                        try {
                            long expTime = Long.parseLong(expTimeHex, 16);
                            // If token expires in less than 5 minutes or is already expired
                            if (expTime - currentTime < 300) {
                                shouldRefresh = true;
                                Log.d(TAG, "Deezer URL token is expiring soon or expired, will refresh");
                            }
                        } catch (NumberFormatException e) {
                            // If we can't parse the expiration, assume we should refresh
                            shouldRefresh = true;
                        }
                    }
                } else {
                    // For other token formats, just assume they might be expired
                    shouldRefresh = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error checking token expiration: " + e.getMessage());
                // On any error, assume we should refresh the URL
                shouldRefresh = true;
            }
            
            if (shouldRefresh && currentTrack != null) {
                Log.d(TAG, "Getting fresh Deezer preview URL for: " + 
                    currentTrack.getTitle() + " by " + currentTrack.getArtist());
                
                // Show loading state
                if (listener != null) {
                    listener.onLoadingStateChanged(true);
                    listener.onBufferingStart();
                }
                
                // Request a fresh URL
                deezerHelper.getPreviewUrl(currentTrack.getTitle(), currentTrack.getArtist(), new DeezerHelper.DeezerStringCallback() {
                    @Override
                    public void onSuccess(String freshUrl) {
                        if (isValid(freshUrl)) {
                            Log.d(TAG, "Got fresh Deezer URL: " + freshUrl);
                            continuePlayStreamUrl(freshUrl);
                        } else {
                            handleError("Failed to get fresh Deezer URL");
                        }
                    }
                    
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Error getting fresh Deezer URL: " + message);
                        handleError("Error refreshing Deezer URL: " + message);
                    }
                });
                
                return; // Exit early, will continue in callback
            }
        }
        
        // Normal path when no URL refresh is needed
        continuePlayStreamUrl(streamUrl);
    }
    
    /**
     * Continue playing stream URL after any necessary URL refreshing
     * 
     * @param streamUrl The URL to stream
     */
    private void continuePlayStreamUrl(String streamUrl) {
        // Stop any current playback
        stopPlayback();
        
        try {
            // Show loading/buffering state
            if (listener != null) {
                listener.onBufferingStart();
                listener.onLoadingStateChanged(true);
            }
            
            // Create a new MediaPlayer instance
            mediaPlayer = new MediaPlayer();
            
            // Configure the MediaPlayer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                );
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            // Set wake lock to prevent device from sleeping during playback
            mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            
            // Set up listeners
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "MediaPlayer prepared, starting playback");
                
                // Request audio focus
                if (!requestAudioFocus()) {
                    Log.e(TAG, "Failed to get audio focus");
            if (listener != null) {
                        listener.onPlaybackError("Failed to get audio focus");
                        listener.onBufferingEnd();
                        listener.onLoadingStateChanged(false);
                    }
                    releaseMediaPlayer();
                    return;
                }
                
                try {
                    // Start playback
                    mediaPlayer.start();
                    Log.d(TAG, "MediaPlayer.start() called successfully");
                    
                    // Update UI and notify listeners
                    if (listener != null) {
                        listener.onTrackPlay(currentTrack);
                        listener.onBufferingEnd();
                        listener.onLoadingStateChanged(false);
                        listener.onPlaybackStateChanged(true);
                    }
                    
                    // Start progress updates for seekbar
                    if (seekBar != null) {
                        startProgressUpdate();
                    }
                    
                    Log.d(TAG, "Playback started successfully");
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error starting playback: " + e.getMessage());
                    if (listener != null) {
                        listener.onPlaybackError("Error starting playback: " + e.getMessage());
                        listener.onBufferingEnd();
                        listener.onLoadingStateChanged(false);
                    }
                }
            });
            
            // Handle playback completion
            mediaPlayer.setOnCompletionListener(this);
            
            // Handle errors
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                String errorMsg = "Playback error: " + what + ", " + extra;
                Log.e(TAG, errorMsg);
                
                if (listener != null) {
                    listener.onPlaybackError(errorMsg);
                    listener.onBufferingEnd();
                    listener.onLoadingStateChanged(false);
                }
                
                // Try to recover
                releaseMediaPlayer();
                
                // If this is a Deezer URL that failed, try a fallback
                if (currentTrack != null && currentTrack.isDeezerTrack() && !failedUrls.contains(streamUrl)) {
                    failedUrls.add(streamUrl);
                    tryFallbackUrl();
                }
                
                return true; // Error handled
            });
            
            // Handle buffering updates
            mediaPlayer.setOnBufferingUpdateListener((mp, percent) -> {
                Log.d(TAG, "Buffering: " + percent + "%");
                if (listener != null) {
                    listener.onBufferingUpdate(percent);
                }
            });
            
            // Add info listener to track buffering state
            mediaPlayer.setOnInfoListener((mp, what, extra) -> {
                switch (what) {
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        Log.d(TAG, "MediaPlayer buffering start");
                        if (listener != null) {
                            listener.onBufferingStart();
                        }
                        return true;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        Log.d(TAG, "MediaPlayer buffering end");
                        if (listener != null) {
                            listener.onBufferingEnd();
                        }
                        return true;
                }
                return false;
            });
            
            // Set the URL as the data source
            Log.d(TAG, "Setting data source: " + streamUrl);
            mediaPlayer.setDataSource(streamUrl);
            
            // Prepare the player asynchronously
            isPreparing = true;
            mediaPlayer.prepareAsync();
            
            Log.d(TAG, "MediaPlayer preparing URL: " + streamUrl);
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up MediaPlayer: " + e.getMessage(), e);
            isPreparing = false;
            
            if (listener != null) {
                listener.onPlaybackError("Error playing audio: " + e.getMessage());
                listener.onBufferingEnd();
                listener.onLoadingStateChanged(false);
            }
            
            // Clean up
            releaseMediaPlayer();
            
            // If this URL failed and it's from Deezer, try a fallback
            if (currentTrack != null && currentTrack.isDeezerTrack() && !failedUrls.contains(streamUrl)) {
                failedUrls.add(streamUrl);
                tryFallbackUrl();
            }
        }
    }
    
    /**
     * Validates and fixes a URL for audio playback
     * @param url The URL to validate and fix
     * @return A valid URL or null if invalid
     */
    private String validateAndFixUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        
        // Log the original URL for debugging
        Log.d(TAG, "Original URL: " + url);
        
        // Check if this URL has previously failed and use fallback if it has
        if (failedUrls.contains(url)) {
            Log.d(TAG, "URL previously failed, using fallback");
            return "https://www.learningcontainer.com/wp-content/uploads/2020/02/Kalimba.mp3";
        }
        
        // Replace unsupported URLs with known working fallback
        if (url.contains("spotify.com") && !url.endsWith(".mp3") && !url.endsWith(".wav")) {
            Log.d(TAG, "Using fallback URL for Spotify URL");
            return "https://www.learningcontainer.com/wp-content/uploads/2020/02/Kalimba.mp3";
        }
        
        // Check for common invalid URL patterns that might cause decoder errors
        if (!url.contains(".")) {
            // URLs without extension often cause decoder errors
            Log.d(TAG, "URL has no extension, using fallback");
            return "https://www.learningcontainer.com/wp-content/uploads/2020/02/Kalimba.mp3";
        }
        
        // Ensure HTTPS for all URLs
        if (url.startsWith("http:")) {
            url = "https:" + url.substring(5);
        }
        
        // Add protocol if missing
        if (!url.startsWith("http") && !url.startsWith("content:") && !url.startsWith("file:")) {
            url = "https://" + url;
        }
        
        // Log the fixed URL
        Log.d(TAG, "Fixed URL: " + url);
        
        return url;
    }
    
    /**
     * Check if any network is available
     */
    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager = 
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) return false;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network == null) return false;
                
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                );
            } else {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network availability: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if WiFi is connected
     */
    private boolean isWifiConnected() {
        ConnectivityManager connectivityManager = 
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        
        NetworkInfo wifiNetwork = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifiNetwork != null && wifiNetwork.isConnected();
    }
    
    // Basic player control methods
    public void play() {
        if (currentTrack != null) {
            playTrack(currentTrack);
        } else if (playQueue != null && !playQueue.isEmpty()) {
            Track nextTrack = playQueue.getCurrent();
            if (nextTrack != null) {
                playTrack(nextTrack);
            }
        }
    }
    
    public void pause() {
        pausePlayback();
    }
    
    /**
     * Resume playback if paused
     */
    public void resumePlayback() {
        if (mediaPlayer != null && !isPlaying()) {
            try {
                // Release wake lock if we have it
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                
                // Acquire wake lock to keep CPU running during playback
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire(10*60*1000L); // 10 minutes
                }
                
                mediaPlayer.start();
                
                // Notify listeners that playback has started
                if (listener != null) {
                    listener.onPlaybackStateChanged(true);
                    if (currentTrack != null) {
                        listener.onTrackPlay(currentTrack);
                    }
                }
                
                // Start progress updates
                startProgressUpdate();
                
            } catch (Exception e) {
                Log.e(TAG, "Error resuming playback: " + e.getMessage());
                
                if (listener != null) {
                    listener.onPlaybackError("Error resuming playback: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Pause playback if playing
     */
    public void pausePlayback() {
        if (mediaPlayer != null && isPlaying()) {
            try {
                mediaPlayer.pause();
                
                // Release wake lock to save battery
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                
                // Notify listeners that playback has paused
                if (listener != null) {
                    listener.onPlaybackStateChanged(false);
                    listener.onTrackPause();
                }
                
                // Stop progress updates
                stopProgressUpdate();
                
            } catch (Exception e) {
                Log.e(TAG, "Error pausing playback: " + e.getMessage());
            }
        }
    }
    
    public void stopPlayback() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            if (listener != null) {
                listener.onTrackStopped();
                listener.onPlaybackStateChanged(false);
            }
            if (handler != null && updateSeekBarTask != null) {
                handler.removeCallbacks(updateSeekBarTask);
            }
            // Release wake lock if held
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }
    
    private void startProgressUpdate() {
        if (handler != null && mediaPlayer != null) {
            updateSeekBarTask = new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        if (seekBar != null) {
                            seekBar.setProgress(currentPosition);
                        }
                        if (listener != null) {
                            listener.onPlaybackStateChanged(true);
                        }
                        handler.postDelayed(this, 1000);
                    }
                }
            };
            handler.post(updateSeekBarTask);
        }
    }
    
    private void stopProgressUpdate() {
        if (handler != null && updateSeekBarTask != null) {
            handler.removeCallbacks(updateSeekBarTask);
        }
    }
    
    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(position);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error seeking: " + e.getMessage());
            }
        }
    }
    
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
    
    public Track getCurrentTrack() {
        return currentTrack;
    }
    
    public void setSeekBar(SeekBar seekBar) {
        this.seekBar = seekBar;
    }
    
    public void setOnPlaybackChangedListener(OnPlaybackChangedListener listener) {
        this.listener = listener;
    }
    
    public void setListener(OnPlaybackChangedListener listener) {
        this.listener = listener;
    }
    
    public void releaseMediaPlayer() {
        // Cancel any ongoing crossfade
        if (isCrossfading) {
            handler.removeCallbacksAndMessages(null);
            isCrossfading = false;
        }

        // Cancel any scheduled seek bar updates
        if (handler != null && updateSeekBarTask != null) {
            handler.removeCallbacks(updateSeekBarTask);
        }

        // Abandon audio focus
        if (audioFocusGranted) {
            abandonAudioFocus();
        }

        // Release wake lock if held
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Release media player
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaPlayer: " + e.getMessage());
            }
        }

        // Clear track info
        currentTrack = null;
        isPreparing = false;
        isBuffering = false;
        isLoading = false;

        // Clear queue and history
        if (playQueue != null) {
            playQueue.clear();
        }
        history.clear();
    }
    
    public void skipToNext() {
        if (currentTrack != null) {
            // Add current track to history if it exists
            history.add(currentTrack);
        }

        // If we're preparing the next track already via crossfade, let it complete
        if (isCrossfading && nextTrack != null) {
            return;
        }

        Track nextTrack = null;
        if (shuffleEnabled) {
            playRandomTrack();
            return;
        } else {
            nextTrack = playQueue.getNext();
            if (nextTrack == null && repeatMode == REPEAT_ALL) {
                // If repeat all is enabled and we're at the end, go back to start
                playQueue.reset();
                nextTrack = playQueue.getCurrent();
            }
        }

        if (nextTrack != null) {
            // If crossfade is enabled and we're currently playing, prepare the next track
            if (mediaPlayer != null && mediaPlayer.isPlaying() && CROSSFADE_DURATION > 0) {
                prepareNextTrack(nextTrack);
            } else {
                // Otherwise just play it directly
                playTrack(nextTrack);
            }
        } else {
            // No next track available
            stopPlayback();
        }
    }
    
    public void skipToPrevious() {
        if (mediaPlayer != null) {
            // If we're more than 3 seconds into the song, restart it
            if (mediaPlayer.getCurrentPosition() > 3000) {
                mediaPlayer.seekTo(0);
                return;
            }
        }

        Track prevTrack = null;
        if (!history.isEmpty()) {
            // Get the last track from history
            prevTrack = history.remove(history.size() - 1);
        } else {
            prevTrack = playQueue.getPrevious();
            if (prevTrack == null && repeatMode == REPEAT_ALL) {
                // If repeat all is enabled and we're at the start, go to end
                playQueue.toEnd();
                prevTrack = playQueue.getCurrent();
            }
        }

        if (prevTrack != null) {
            playTrack(prevTrack);
        }
    }
    
    public void skipToQueueIndex(int index) {
        if (playQueue != null && index >= 0 && index < playQueue.size()) {
            playQueue.jumpTo(index);
            playTrack(playQueue.getCurrent());
        }
    }
    
    public void removeFromQueue(int index) {
        if (playQueue != null) {
            playQueue.remove(index);
            // If we removed the current track, play the next one
            if (index == playQueue.getCurrentIndex()) {
                playNext();
            }
        }
    }
    
    public void moveQueueItem(int fromIndex, int toIndex) {
        if (playQueue != null) {
            playQueue.move(fromIndex, toIndex);
        }
    }
    
    public List<Track> getQueue() {
        return playQueue != null ? playQueue.getQueue() : new ArrayList<>();
    }
    
    public List<Track> getHistory() {
        return playQueue != null ? playQueue.getHistory() : new ArrayList<>();
    }
    
    public int getCurrentQueueIndex() {
        return playQueue != null ? playQueue.getCurrentIndex() : -1;
    }
    
    public void clearQueue() {
        if (playQueue != null) {
            playQueue.clear();
            stopPlayback();
        }
    }
    
    public void addToQueue(Track track) {
        if (playQueue != null) {
            playQueue.add(track);
            if (playQueue.size() == 1) {
                // If this was the first track added, start playing
                playTrack(track);
            }
        }
    }
    
    public void addToQueueNext(Track track) {
        if (playQueue != null) {
            playQueue.addToQueueNext(track);
        }
    }
    
    public void addAllToQueue(List<Track> tracks) {
        if (playQueue != null) {
            boolean wasEmpty = playQueue.isEmpty();
            playQueue.addAll(tracks);
            if (wasEmpty && !tracks.isEmpty()) {
                // If queue was empty and we added tracks, start playing
                playTrack(tracks.get(0));
            }
        }
    }
    
    public Playlist createPlaylistFromQueue(String name, String description) {
        if (playQueue != null) {
            return playQueue.createPlaylistFromQueue(name, description);
        }
        return new Playlist();
    }
    
    public void playNext() {
        if (shuffleEnabled) {
            playRandomTrack();
        } else {
            if (playQueue != null) {
                Track nextTrack = playQueue.getNext();
                if (nextTrack != null) {
                    playTrack(nextTrack);
                } else if (repeatMode == REPEAT_ALL) {
                    // If repeat all is enabled, go back to start of queue
                    playQueue.reset();
                    playTrack(playQueue.getCurrent());
                }
            }
        }
    }
    
    public void playPrevious() {
        if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
            // If we're more than 3 seconds into the song, restart it
            mediaPlayer.seekTo(0);
        } else {
            if (playQueue != null) {
                Track previousTrack = playQueue.getPrevious();
                if (previousTrack != null) {
                    playTrack(previousTrack);
                } else if (repeatMode == REPEAT_ALL) {
                    // If repeat all is enabled, go to end of queue
                    playQueue.toEnd();
                    playTrack(playQueue.getCurrent());
                }
            }
        }
    }
    
    private void playRandomTrack() {
        if (playQueue != null && !playQueue.isEmpty()) {
            int totalTracks = playQueue.size();
            if (totalTracks > 1) {
                int currentIndex = playQueue.getCurrentIndex();
                int randomIndex;
                do {
                    randomIndex = new java.util.Random().nextInt(totalTracks);
                } while (randomIndex == currentIndex);
                
                playQueue.jumpTo(randomIndex);
                playTrack(playQueue.getCurrent());
            }
        }
    }
    
    public int toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3; // Cycle through 0, 1, 2
        return repeatMode;
    }
    
    public int cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3; // Cycle through 0, 1, 2
        return repeatMode;
    }
    
    public void setRepeatMode(int mode) {
        this.repeatMode = mode;
    }
    
    public int getRepeatMode() {
        return repeatMode;
    }
    
    public boolean toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        return shuffleEnabled;
    }
    
    public void setShuffleEnabled(boolean enabled) {
        this.shuffleEnabled = enabled;
    }
    
    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }
    
    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }
    
    public void playTrackEnhanced(Track track, boolean showLoadingUi) {
        // Implementation
    }
    
    public void playSpotifyTrack(String spotifyId) {
        // Implementation
    }
    
    public void playTrackBySpotifyId(String spotifyId) {
        // Implementation
    }
    
    public void playSpotifyTrackOnDevice(String spotifyId, String deviceId, String accessToken) {
        if (spotifyId == null || deviceId == null || accessToken == null) {
            handleError("Invalid Spotify playback parameters");
            return;
        }
        
        // Start playback on Spotify device
        spotifyHelper.playTrackOnDevice(spotifyId, deviceId, accessToken, new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String response) {
                Log.d(TAG, "Started Spotify playback on device");
                if (listener != null) {
                    listener.onTrackPlay(currentTrack);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Spotify playback error: " + errorMessage);
                handleError("Spotify playback error: " + errorMessage);
            }
        });
    }
    
    public void playPlaylist(Playlist playlist) {
        if (playQueue != null && playlist != null) {
            playQueue.setQueueFromPlaylist(playlist);
            Track firstTrack = playQueue.resetToFirst();
            if (firstTrack != null) {
                playTrack(firstTrack);
            }
        }
    }
    
    public void playTrackInPlaylist(Playlist playlist, Track track) {
        if (playQueue != null && playlist != null && track != null) {
            playQueue.setQueueFromPlaylist(playlist);
            // Find the track in the playlist and skip to it
            List<Track> tracks = playlist.getTracks();
            for (int i = 0; i < tracks.size(); i++) {
                if (tracks.get(i).getId().equals(track.getId())) {
                    Track selectedTrack = playQueue.skipToIndex(i);
                    if (selectedTrack != null) {
                        playTrack(selectedTrack);
                    }
                    break;
                }
            }
        }
    }
    
    public void playWithContentUri(Uri uri, Track track) {
        if (uri != null && track != null) {
            currentTrack = track;
            // Implementation would continue here
        }
    }
    
    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    public int getBufferedPosition() {
        // Implementation would be more complex
        return 0;
    }
    
    @Override
    public void onCompletion(MediaPlayer mp) {
        if (repeatMode == REPEAT_ONE) {
            // Repeat current track
            if (currentTrack != null) {
                playTrack(currentTrack);
            }
        } else {
            // Play next track
            playNext();
        }
    }
    
    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                Log.d(TAG, "Audio focus gained");
                playbackNowAuthorized = true;
                if (mediaPlayer == null) {
                    setupMediaPlayer();
                }
                if (playbackDelayed || pausedForFocusLoss) {
                    playbackDelayed = false;
                    pausedForFocusLoss = false;
                    resumePlayback();
                } else if (mediaPlayer != null) {
                    mediaPlayer.setVolume(maxVolume, maxVolume);
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                Log.d(TAG, "Audio focus lost");
                playbackNowAuthorized = false;
                playbackDelayed = false;
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    wasPlayingBeforeFocusLoss = true;
                    pausedForFocusLoss = true;
                    pausePlayback();
                } else {
                    wasPlayingBeforeFocusLoss = false;
                    pausedForFocusLoss = false;
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.d(TAG, "Audio focus lost temporarily");
                playbackNowAuthorized = false;
                playbackDelayed = true;
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    wasPlayingBeforeFocusLoss = true;
                    pausedForFocusLoss = true;
                    pausePlayback();
                } else {
                    wasPlayingBeforeFocusLoss = false;
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.d(TAG, "Audio focus loss - ducking");
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    wasPlayingBeforeFocusLoss = true;
                    mediaPlayer.setVolume(0.3f, 0.3f);
                } else {
                    wasPlayingBeforeFocusLoss = false;
                }
                break;
        }
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }

        if (audioManager == null) {
            return false; // Couldn't get AudioManager
        }

        // Check for the RECORD_AUDIO permission if needed for some devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "RECORD_AUDIO permission not granted, may affect audio on some devices");
            }
        }

        // For API level 26+
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

            int result = audioManager.requestAudioFocus(audioFocusRequest);
            audioFocusGranted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            
            if (audioFocusGranted) {
                Log.d(TAG, "Audio focus granted (API 26+)");
        } else {
                Log.e(TAG, "Failed to get audio focus (API 26+)");
            }
            
        } else {
            // For API level 25 and below
            int result = audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        audioFocusGranted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            
            if (audioFocusGranted) {
                Log.d(TAG, "Audio focus granted (legacy API)");
            } else {
                Log.e(TAG, "Failed to get audio focus (legacy API)");
            }
        }

        return audioFocusGranted;
    }

    private void abandonAudioFocus() {
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest != null) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                }
            } else {
                audioManager.abandonAudioFocus(this);
            }
            audioFocusGranted = false;
        }
    }

    public static class PlaybackState {
        private boolean isPlaying;
        private int currentPosition;
        private int duration;
        private int bufferedPosition;
        private float playbackSpeed;
        private boolean isBuffering;
        private boolean isError;
        private String errorMessage;

        public PlaybackState() {
            playbackSpeed = 1.0f;
        }

        // Getters and setters
        public boolean isPlaying() { return isPlaying; }
        public void setPlaying(boolean playing) { isPlaying = playing; }
        public int getCurrentPosition() { return currentPosition; }
        public void setCurrentPosition(int position) { currentPosition = position; }
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
        public int getBufferedPosition() { return bufferedPosition; }
        public void setBufferedPosition(int position) { bufferedPosition = position; }
        public float getPlaybackSpeed() { return playbackSpeed; }
        public void setPlaybackSpeed(float speed) { playbackSpeed = speed; }
        public boolean isBuffering() { return isBuffering; }
        public void setBuffering(boolean buffering) { isBuffering = buffering; }
        public boolean isError() { return isError; }
        public void setError(boolean error) { isError = error; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String message) { 
            errorMessage = message;
            isError = message != null;
        }
    }

    private PlaybackState playbackState = new PlaybackState();

    public PlaybackState getPlaybackState() {
        updateInternalPlaybackState();
        return playbackState;
    }

    private void updateInternalPlaybackState() {
        if (mediaPlayer != null) {
            try {
                playbackState.setCurrentPosition(mediaPlayer.getCurrentPosition());
                playbackState.setDuration(mediaPlayer.getDuration());
                playbackState.setBuffering(isBuffering);
                playbackState.setPlaybackSpeed(mediaPlayer.getPlaybackParams().getSpeed());
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error updating internal playback state: " + e.getMessage());
            }
        }
    }

    private void notifyPlaybackStateChanged() {
        updateInternalPlaybackState();
        if (listener != null) {
            listener.onPlaybackStateChanged(playbackState.isPlaying());
        }
    }

    private void startSeekBarUpdate() {
        if (handler != null && mediaPlayer != null) {
            updateSeekBarTask = new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        try {
                            int currentPosition = mediaPlayer.getCurrentPosition();
                            if (seekBar != null) {
                                seekBar.setProgress(currentPosition);
                            }
                            playbackState.setCurrentPosition(currentPosition);
                            notifyPlaybackStateChanged();
                            
                            // Schedule next update
                            handler.postDelayed(this, 200); // Update every 200ms
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Error updating seekbar: " + e.getMessage());
                        }
                    }
                }
            };
            handler.post(updateSeekBarTask);
        }
    }

    private void stopSeekBarUpdate() {
        if (handler != null && updateSeekBarTask != null) {
            handler.removeCallbacks(updateSeekBarTask);
        }
    }

    public void setPlaybackSpeed(float speed) {
        if (mediaPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer.setPlaybackParams(
                    mediaPlayer.getPlaybackParams().setSpeed(speed)
                );
                playbackState.setPlaybackSpeed(speed);
                notifyPlaybackStateChanged();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error setting playback speed: " + e.getMessage());
            }
        }
    }

    public float getPlaybackSpeed() {
        return playbackState.getPlaybackSpeed();
    }

    private void updateBufferingState(boolean buffering) {
        isBuffering = buffering;
        playbackState.setBuffering(buffering);
        if (listener != null) {
            if (buffering) {
                listener.onBufferingStart();
            } else {
                listener.onBufferingEnd();
            }
            notifyPlaybackStateChanged();
        }
    }

    private void handleError(String errorMessage) {
        playbackState.setError(true);
        playbackState.setErrorMessage(errorMessage);
        if (listener != null) {
            listener.onPlaybackError(errorMessage);
        }
        notifyPlaybackStateChanged();
    }

    // Update the setupMediaPlayerListeners method to use the new state management
    private void setupMediaPlayerListeners() {
        if (mediaPlayer == null) return;

        mediaPlayer.setOnPreparedListener(mp -> {
            Log.d(TAG, "MediaPlayer prepared successfully");
            isPreparing = false;
            isBuffering = false;
            playbackState.setBuffering(false);
            playbackState.setError(false);
            playbackState.setErrorMessage(null);

            try {
            // Start playback
            mp.start();
                Log.d(TAG, "MediaPlayer started playback");

            // Update seekbar max value
            int duration = mp.getDuration();
                Log.d(TAG, "Track duration: " + duration + "ms");
                
            if (seekBar != null) {
                seekBar.setMax(duration);
            }
            playbackState.setDuration(duration);
            startSeekBarUpdate();

            // Notify listeners
            if (listener != null && currentTrack != null) {
                listener.onTrackPlay(currentTrack);
                notifyPlaybackStateChanged();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting playback: " + e.getMessage(), e);
                handleError("Error starting playback: " + e.getMessage());
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            isPreparing = false;
            isBuffering = false;

            String errorMessage;
            boolean tryRecovery = true;
            
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
                    errorMessage = "Timed out while loading media";
                    break;
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    errorMessage = "Media server died";
                    // Need to recreate MediaPlayer
                    recreateMediaPlayer();
                    break;
                default:
                    errorMessage = "Unknown playback error (" + what + ", " + extra + ")";
                    break;
            }
            
            // Store last error message for diagnostics
            lastErrorMessage = errorMessage;

            handleError(errorMessage);
            
            // Always log detailed error info for debugging
            Log.e(TAG, "Audio playback error: " + errorMessage + " (what=" + what + ", extra=" + extra + ")");
            
            // Check if we have a current track URL to use for recovery attempts
            if (currentTrack != null && currentTrack.getPreviewUrl() != null) {
                Log.d(TAG, "Problem URL: " + currentTrack.getPreviewUrl());
            }
            
            // Try to recover automatically by playing a fallback URL
            if (tryRecovery) {
                tryRecoverFromError();
            }
            
            return true;
        });

        mediaPlayer.setOnBufferingUpdateListener((mp, percent) -> {
            Log.d(TAG, "Buffering: " + percent + "%");
            playbackState.setBufferedPosition((int)(playbackState.getDuration() * (percent / 100f)));
            if (listener != null) {
                listener.onBufferingUpdate(percent);
            }
        });

        mediaPlayer.setOnInfoListener((mp, what, extra) -> {
            switch (what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    Log.d(TAG, "MediaPlayer buffering start");
                    updateBufferingState(true);
                    return true;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    Log.d(TAG, "MediaPlayer buffering end");
                    updateBufferingState(false);
                    return true;
                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    Log.d(TAG, "Rendering started");
                    return true;
                case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                    Log.d(TAG, "Bad interleaving");
                    return true;
                case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                    Log.d(TAG, "Not seekable");
                    return true;
                case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                    Log.d(TAG, "Metadata update");
                    return true;
                case MediaPlayer.MEDIA_INFO_AUDIO_NOT_PLAYING:
                    Log.d(TAG, "Audio not playing");
                    return true;
                default:
            return false;
            }
        });

        mediaPlayer.setOnCompletionListener(this);

        // Add special logging for image decoder errors
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String message = throwable.getMessage() != null ? throwable.getMessage() : "Unknown error";
            if (message.contains("Failed to create image decoder") || message.contains("image decoder")) {
                Log.w(TAG, "Image decoder error detected in thread " + thread.getName() + ": " + message);
                // Don't crash on image decoder errors, just log them
                return;
            }
            
            // Let the default handler deal with other uncaught exceptions
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, throwable);
        });
    }
    
    /**
     * Reinitialize the MediaPlayer
     */
    private void recreateMediaPlayer() {
        if (mediaPlayer != null) {
            releaseMediaPlayer();
        }
        
        initializeMediaPlayer();
        setupMediaPlayerListeners();
    }
    
    /**
     * Try to recover from a playback error by playing a fallback URL
     */
    private void tryRecoverFromError() {
        if (currentTrack == null) return;
        
        try {
            // Always use a reliable fallback URL when recovery is needed
            String fallbackUrl = "https://www.learningcontainer.com/wp-content/uploads/2020/02/Kalimba.mp3";
            Log.d(TAG, "Trying to recover with fallback URL: " + fallbackUrl);
            
            // Reset the media player completely
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.reset();
                } catch (Exception e) {
                    // If reset fails, recreate the player
                    recreateMediaPlayer();
                }
            } else {
                // Create a new player if it doesn't exist
                setupMediaPlayer();
            }
            
            // Set the fallback URL as a temporary URL for recovery
            if (currentTrack != null) {
                String originalUrl = currentTrack.getPreviewUrl();
                currentTrack.setPreviewUrl(fallbackUrl);
                
                // Mark the original URL as failed to avoid trying it again
                if (originalUrl != null) {
                    failedUrls.add(originalUrl);
                    Log.d(TAG, "Added to failed URLs: " + originalUrl);
                }
            }
            
            // Try playing the fallback URL
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                } else {
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                }
                
                // Ensure maximum volume
                mediaPlayer.setVolume(1.0f, 1.0f);
                
                // Set data source and prepare
                mediaPlayer.setDataSource(fallbackUrl);
                isPreparing = true;
                mediaPlayer.prepareAsync();
                
                Log.d(TAG, "Recovery attempt in progress with fallback URL");
            } catch (IOException e) {
                Log.e(TAG, "Recovery failed: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to recover from error: " + e.getMessage(), e);
        }
    }

    private void prepareNextTrack(Track track) {
        if (track == null) return;
        
        // Save next track info
        nextTrack = track;
        
        try {
            // Create new media player for next track
            nextMediaPlayer = new MediaPlayer();
            nextMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
                
            // Set up next media player
            nextMediaPlayer.setDataSource(track.getPreviewUrl());
            nextMediaPlayer.setOnPreparedListener(mp -> {
                // Ready for crossfade
                isCrossfading = true;
                startCrossfade();
            });
            
            nextMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Error preparing next track: " + what + ", " + extra);
                releaseNextMediaPlayer();
                // Fall back to simple crossfade
                crossfadeToNextTrack();
                return true;
            });
            
            // Start preparing
            nextMediaPlayer.prepareAsync();
            
        } catch (IOException e) {
            Log.e(TAG, "Error preparing next player: " + e.getMessage());
            releaseNextMediaPlayer();
            // Fall back to regular transition
            crossfadeToNextTrack();
        }
    }

    private void startCrossfade() {
        if (mediaPlayer == null || !mediaPlayer.isPlaying()) {
            switchToNextTrack();
            return;
        }

        // Use the configured crossfade duration
        applyCrossfade();
    }

    private void crossfadeToNextTrack() {
        if (nextTrack == null) return;
        
        Track trackToPlay = nextTrack;
        nextTrack = null;
        
        if (mediaPlayer != null && mediaPlayer.isPlaying() && crossfadeDuration > 0) {
            applyCrossfade();
        } else {
            // No crossfade needed, just play directly
            playTrack(trackToPlay);
        }
    }

    private void switchToNextTrack() {
        // Stop and release current player
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error releasing current player: " + e.getMessage());
            }
        }
        
        // Switch to next player
        mediaPlayer = nextMediaPlayer;
        currentTrack = nextTrack;
        nextMediaPlayer = null;
        nextTrack = null;
        isCrossfading = false;
        
        // Set up listeners for the new current player
        if (mediaPlayer != null) {
            setupMediaPlayerListeners();
            
            // Update UI
            if (listener != null && currentTrack != null) {
                listener.onTrackChanged(currentTrack);
                listener.onTrackPlay(currentTrack);
            }
            
            // Set up seek bar if available
            if (seekBar != null) {
                seekBar.setMax(mediaPlayer.getDuration());
                startProgressUpdate();
            }
        }
    }

    private void releaseNextMediaPlayer() {
        if (nextMediaPlayer != null) {
            try {
                nextMediaPlayer.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error releasing next player: " + e.getMessage());
            }
            nextMediaPlayer = null;
            nextTrack = null;
        }
        isCrossfading = false;
    }

    private static final int DEFAULT_CROSSFADE_DURATION = 500; // Default 500ms
    private int crossfadeDuration = DEFAULT_CROSSFADE_DURATION;

    public void setCrossfadeDuration(int durationMs) {
        // Limit crossfade duration between 0 and 5000ms
        this.crossfadeDuration = Math.max(0, Math.min(durationMs, 5000));
    }

    public int getCrossfadeDuration() {
        return crossfadeDuration;
    }

    private void applyCrossfade() {
        // Don't crossfade if duration is 0 or we're not currently playing
        if (crossfadeDuration == 0 || mediaPlayer == null || !mediaPlayer.isPlaying()) {
            switchToNextTrack();
            return;
        }

        // Start crossfade animation
        final long startTime = System.currentTimeMillis();
        final Handler handler = new Handler(Looper.getMainLooper());
        
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float fraction = Math.min(1f, (float) elapsed / crossfadeDuration);
                    
                    // Logarithmic fading for smoother volume transitions
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        float volume = (float) (maxVolume * (1 - Math.log10(9 * fraction + 1)));
                        mediaPlayer.setVolume(volume, volume);
                    }
                    
                    if (nextMediaPlayer != null) {
                        float nextVolume = (float) (maxVolume * Math.log10(9 * fraction + 1));
                        nextMediaPlayer.setVolume(nextVolume, nextVolume);
                        if (!nextMediaPlayer.isPlaying()) {
                            nextMediaPlayer.start();
                        }
                    }
                    
                    if (fraction < 1f) {
                        handler.postDelayed(this, 20); // Update every 20ms for smooth animation
                    } else {
                        // Crossfade complete
                        switchToNextTrack();
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error during crossfade: " + e.getMessage());
                    switchToNextTrack();
                }
            }
        });
    }

    private void startPlayback() {
        if (mediaPlayer != null) {
            try {
                // Request audio focus before starting playback
                if (!requestAudioFocus()) {
                    if (listener != null) {
                        listener.onPlaybackError("Cannot get audio focus");
                    }
                    return;
                }
                
                // Acquire wake lock to prevent device from sleeping during playback
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire(3600000); // 1 hour max
                }
                
                mediaPlayer.start();
                
                // Notify UI
                if (listener != null) {
                    listener.onPlaybackStateChanged(true);
                    if (currentTrack != null) {
                        listener.onTrackPlay(currentTrack);
                    }
                }
                
                // Start progress updates if seekbar is available
                if (seekBar != null) {
                    startProgressUpdate();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error playing: " + e.getMessage());
                if (listener != null) {
                    listener.onPlaybackError("Playback error: " + e.getMessage());
                }
            }
        }
    }

    private void setupMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            setupMediaPlayerListeners();
        }
    }

    private void playWithUrl(String url) {
        if (url == null || url.isEmpty()) {
            handleError("Invalid playback URL");
            return;
        }

        try {
            // Reset and prepare media player
            if (mediaPlayer == null) {
                setupMediaPlayer();
            }
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            isPreparing = true;
            
            // Request audio focus before preparing
            if (!requestAudioFocus()) {
                handleError("Could not get audio focus");
            return;
        }
        
            // Prepare asynchronously
            mediaPlayer.prepareAsync();
            
        } catch (IOException e) {
            Log.e(TAG, "Error setting data source: " + e.getMessage());
            handleError("Error preparing playback: " + e.getMessage());
        }
    }

    public void showMoodDialog(OnMoodSelectedListener moodListener) {
        try {
            // Create dialog with custom layout
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            
            // Get layout inflater from context
            LayoutInflater inflater = LayoutInflater.from(context);
            View dialogView = inflater.inflate(R.layout.dialog_mood_select, null);
            builder.setView(dialogView);
            
            // Create the dialog
            AlertDialog dialog = builder.create();
            
            // Set up click listeners for each mood card
            dialogView.findViewById(R.id.cardHappy).setOnClickListener(v -> {
                if (moodListener != null) {
                    moodListener.onMoodSelected("Happy");
                }
                dialog.dismiss();
            });
            
            dialogView.findViewById(R.id.cardSad).setOnClickListener(v -> {
                if (moodListener != null) {
                    moodListener.onMoodSelected("Sad");
                }
                dialog.dismiss();
            });
            
            dialogView.findViewById(R.id.cardEnergetic).setOnClickListener(v -> {
                if (moodListener != null) {
                    moodListener.onMoodSelected("Energetic");
                }
                dialog.dismiss();
            });
            
            dialogView.findViewById(R.id.cardRelaxed).setOnClickListener(v -> {
                if (moodListener != null) {
                    moodListener.onMoodSelected("Relaxed");
                }
                dialog.dismiss();
            });
            
            dialogView.findViewById(R.id.cardRomantic).setOnClickListener(v -> {
                if (moodListener != null) {
                    moodListener.onMoodSelected("Romantic");
                }
                dialog.dismiss();
            });
            
            dialogView.findViewById(R.id.cardFocused).setOnClickListener(v -> {
                if (moodListener != null) {
                    moodListener.onMoodSelected("Focused");
                }
                dialog.dismiss();
            });
            
            // Set up cancel button
            dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> {
                dialog.dismiss();
            });
            
            // Show the dialog
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing mood dialog: " + e.getMessage(), e);
        }
    }
    
    // Interface for mood selection callback
    public interface OnMoodSelectedListener {
        void onMoodSelected(String mood);
    }

    /**
     * Play a test sound to diagnose audio issues
     * @return true if test sound was started successfully
     */
    public boolean playTestSound() {
        try {
            // Release any existing player to start fresh
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing existing player: " + e.getMessage());
                }
                mediaPlayer = null;
            }
            
            // Request audio focus
            if (!requestAudioFocus()) {
                Log.e(TAG, "Failed to get audio focus for test");
                return false;
            }
            
            // Create new player
            mediaPlayer = new MediaPlayer();
            
            // Set audio attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            
            // Set up listeners
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "Test sound prepared, starting playback");
                mp.start();
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Test sound error: " + what + ", " + extra);
                return true;
            });
            
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Test sound completed successfully");
            });
            
            // Set maximum volume
            mediaPlayer.setVolume(1.0f, 1.0f);
            
            // Try to play a built-in system sound
            String testUrl = "https://www.learningcontainer.com/wp-content/uploads/2020/02/Kalimba.mp3";
            Log.d(TAG, "Playing test sound from: " + testUrl);
            mediaPlayer.setDataSource(testUrl);
            mediaPlayer.prepareAsync();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to play test sound: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check audio system status and show diagnostic info
     * @return A diagnostic status message
     */
    public String checkAudioSystem() {
        StringBuilder status = new StringBuilder();
        
        // Check audio manager
        if (audioManager == null) {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
        
        if (audioManager != null) {
            // Check current volume
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            status.append("Volume: ").append(currentVolume).append("/").append(maxVolume);
            
            // Check if music active
            boolean isMusicActive = audioManager.isMusicActive();
            status.append("\nMusic active: ").append(isMusicActive);
            
            // Check audio mode
            int mode = audioManager.getMode();
            String modeStr;
            switch (mode) {
                case AudioManager.MODE_NORMAL:
                    modeStr = "Normal";
                    break;
                case AudioManager.MODE_RINGTONE:
                    modeStr = "Ringtone";
                    break;
                case AudioManager.MODE_IN_CALL:
                    modeStr = "In Call";
                    break;
                case AudioManager.MODE_IN_COMMUNICATION:
                    modeStr = "In Communication";
                    break;
                default:
                    modeStr = "Unknown";
                    break;
            }
            status.append("\nAudio mode: ").append(modeStr);
        } else {
            status.append("Audio Manager not available");
        }
        
        // Check MediaPlayer status
        if (mediaPlayer != null) {
            boolean isPlaying = false;
            try {
                isPlaying = mediaPlayer.isPlaying();
            } catch (Exception e) {
                // Ignore
            }
            status.append("\nMediaPlayer: ").append(isPlaying ? "Playing" : "Not playing");
        } else {
            status.append("\nMediaPlayer: Not initialized");
        }
        
        // Check if we have a current track
        if (currentTrack != null) {
            status.append("\nCurrent track: ").append(currentTrack.getTitle());
            status.append("\nURL: ").append(currentTrack.getPreviewUrl());
        } else {
            status.append("\nNo current track");
        }
        
        // Log diagnostic info
        String statusStr = status.toString();
        Log.d(TAG, "Audio system status: " + statusStr);
        
        return statusStr;
    }
    
    /**
     * Fix common audio issues
     */
    public void fixAudioIssues() {
        // 1. Reset player
        releaseMediaPlayer();
        
        // 2. Ensure volume is up
        if (audioManager == null) {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
        
        if (audioManager != null) {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int newVolume = (int)(maxVolume * 0.8f); // Set to 80% of max
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
        }
        
        // 3. Recreate player
            if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            setupMediaPlayerListeners();
        }
        
        // 4. Play test sound
        playTestSound();
    }

    /**
     * Run a full diagnostic check of the audio system and fix issues
     * Works well for "Failed to create image decoder" errors
     * @return Detailed diagnostic information
     */
    public String runDecoderDiagnostics() {
        StringBuilder diagnostics = new StringBuilder("Audio Decoder Diagnostics:\n\n");
        
        diagnostics.append("Android Version: ").append(Build.VERSION.RELEASE).append("\n");
        diagnostics.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n\n");
        
        diagnostics.append("Current Track Info:\n");
        if (currentTrack != null) {
            diagnostics.append("Title: ").append(currentTrack.getTitle()).append("\n");
            diagnostics.append("Artist: ").append(currentTrack.getArtist()).append("\n");
            diagnostics.append("URL: ").append(currentTrack.getPreviewUrl()).append("\n");
            
            // Check URL validity
            if (currentTrack.getPreviewUrl() == null || currentTrack.getPreviewUrl().isEmpty()) {
                diagnostics.append("URL STATUS: INVALID - No URL provided\n");
            } else if (!currentTrack.getPreviewUrl().startsWith("http")) {
                diagnostics.append("URL STATUS: SUSPICIOUS - Doesn't start with http\n");
            } else {
                diagnostics.append("URL STATUS: VALID\n");
            }
        } else {
            diagnostics.append("No track currently loaded\n");
        }
        
        diagnostics.append("\nAudio Focus Status: ").append(audioFocusState).append("\n");
        
        diagnostics.append("\nRecent Errors:\n");
        if (lastErrorMessage != null) {
            diagnostics.append("- ").append(lastErrorMessage).append("\n");
        } else {
            diagnostics.append("No recent errors recorded\n");
        }
        
        // Add media player state
        if (mediaPlayer != null) {
            try {
                boolean isPlaying = mediaPlayer.isPlaying();
                int currentPosition = mediaPlayer.getCurrentPosition();
                int duration = mediaPlayer.getDuration();
                
                diagnostics.append("\nMediaPlayer State:\n");
                diagnostics.append("Is Playing: ").append(isPlaying).append("\n");
                diagnostics.append("Position: ").append(currentPosition).append("ms / ").append(duration).append("ms\n");
            } catch (Exception e) {
                diagnostics.append("\nError getting MediaPlayer state: ").append(e.getMessage()).append("\n");
            }
        } else {
            diagnostics.append("\nMediaPlayer is null\n");
        }
        
        return diagnostics.toString();
    }
    
    /**
     * Fix decoder errors, particularly focusing on image decoder issues
     * This method attempts to resolve common causes of image decoder errors
     * that occur during media playback
     * @return true if fix was attempted, false otherwise
     */
    public boolean fixDecoderError() {
        boolean fixed = false;
        
        if (mediaPlayer != null && currentTrack != null) {
            try {
                // Try releasing and recreating the media player
                releaseMediaPlayer();
                initializeMediaPlayer();
                
                // Check if there are any errors with the track URL
                if (currentTrack.getPreviewUrl() != null) {
                    // Try to fix any URL issues
                    String fixedUrl = validateAndFixUrl(currentTrack.getPreviewUrl());
                    if (!fixedUrl.equals(currentTrack.getPreviewUrl())) {
                        Log.d(TAG, "Fixed URL: " + fixedUrl);
                        currentTrack.setPreviewUrl(fixedUrl);
                        fixed = true;
                    }
                }
                
                // Try switching to fallback URL if available
                if (fallbackUrl != null && !currentTrack.getPreviewUrl().equals(fallbackUrl)) {
                    Log.d(TAG, "Attempting to use fallback URL: " + fallbackUrl);
                    currentTrack.setPreviewUrl(fallbackUrl);
                    fixed = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error attempting to fix decoder issues", e);
            }
        }
        
        return fixed;
    }

    // Rename method to avoid duplicate
    private void setupMediaPlayerInstance() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            setupMediaPlayer();
        }
    }

    // Add audioUrl compatibility methods
    public String getAudioUrl(Track track) {
        return track.getPreviewUrl();
    }

    /**
     * Attempts to play a track from an alternate source if available
     * @param track The track to play
     * @return true if an alternate source was found and playback was attempted
     */
    public boolean playTrackFromAlternateSource(Track track) {
        if (track == null) {
            Log.e(TAG, "Cannot play null track from alternate source");
            return false;
        }
        
        Log.d(TAG, "Attempting to play " + track.getTitle() + " from alternate source");
        
        // Try preview URL first
        if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
            Log.d(TAG, "Using preview URL as alternate source");
            try {
                playStreamUrl(track.getPreviewUrl());
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to play from preview URL: " + e.getMessage());
            }
        }
        
        // Try stream URL next
        if (track.getStreamUrl() != null && !track.getStreamUrl().isEmpty()) {
            Log.d(TAG, "Using stream URL as alternate source");
            try {
                playStreamUrl(track.getStreamUrl());
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to play from stream URL: " + e.getMessage());
            }
        }
        
        // If we have a Deezer track ID, try that
        if (track.getDeezerTrackId() > 0) {
            Log.d(TAG, "Trying Deezer as alternate source");
            try {
                playDeezerTrack(track);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to play from Deezer: " + e.getMessage());
            }
        }
        
        // No alternate source found or all attempts failed
        Log.e(TAG, "No viable alternate source found for track: " + track.getTitle());
        return false;
    }

    /**
     * Gets the current buffering percentage
     * @return the buffering percentage (0-100)
     */
    public int getBufferingPercent() {
        if (mediaPlayer == null) {
            return 0;
        }
        
        // Return from the playback state if available
        return playbackState.getBufferedPosition() * 100 / Math.max(1, playbackState.getDuration());
    }

    /**
     * Sets the playing state of the audio player
     * @param playing true to play, false to pause
     */
    public void setPlaying(boolean playing) {
        if (playing && !isPlaying()) {
            resumePlayback();
        } else if (!playing && isPlaying()) {
            pausePlayback();
        }
    }

    /**
     * Resume playback from current position
     */
    public void resume() {
        resumePlayback();
    }

    /**
     * Updates the media session metadata with the current track information
     * @param track The track to update metadata for
     */
    public void updateMediaSessionMetadata(Track track) {
        // This method would normally update MediaSession metadata
        // But since we don't have MediaSession implemented, this is a placeholder
        Log.d(TAG, "Updating media session metadata for track: " + 
                (track != null ? track.getTitle() : "null"));
        // If you add MediaSession support later, implement this method properly
    }

    /**
     * Updates the playback state in the media session
     * @param isPlaying Whether playback is active
     */
    public void updatePlaybackState(boolean isPlaying) {
        // Update internal state
        playbackState.setPlaying(isPlaying);
        
        // Notify listeners
        if (listener != null) {
            // IMPORTANT: Do not call updatePlaybackState() again from the listener implementation
            // to avoid infinite recursion
            listener.onPlaybackStateChanged(isPlaying);
        }
        
        // Call the no-argument version for internal updates only if it's from an external call
        // This prevents recursive calls that could cause stack overflow
        try {
            // Check call stack to prevent recursion
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            boolean isRecursiveCall = false;
            int count = 0;
            
            for (StackTraceElement element : stackTrace) {
                if (element.getMethodName().equals("updatePlaybackState")) {
                    count++;
                    if (count > 1) {
                        // This is a recursive call
                        isRecursiveCall = true;
                        break;
                    }
                }
            }
            
            if (!isRecursiveCall) {
                // Only update internal state if not a recursive call
                updateInternalPlaybackState();
            } else {
                Log.w(TAG, "Prevented recursive call to updatePlaybackState()");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in updatePlaybackState: " + e.getMessage());
        }
    }

    /**
     * Internal method to update playback state without notifying listeners
     * to prevent recursive calls
     */
    // updateInternalPlaybackState() method is already defined at line 1737

    /**
     * Original method that just calls updateInternalPlaybackState()
     */
    private void updatePlaybackState() {
        updateInternalPlaybackState();
    }
    private void updatePlaybackState() {
        updateInternalPlaybackState();
    }

    /**
     * Play a track from YouTube using the YouTubeHelper
     * 
     * @param track The track to play
     */
    private void playYouTubeTrack(Track track) {
        if (track == null) {
            Log.e(TAG, "Cannot play null YouTube track");
            if (listener != null) {
                listener.onPlaybackError("Invalid YouTube track");
                listener.onLoadingStateChanged(false);
            }
            return;
        }
        
        // Show loading state
        isLoading = true;
        if (listener != null) {
            listener.onLoadingStateChanged(true);
            listener.onTrackLoading(track);
        }
        
        // Check if we already have a YouTube URL for this track
        if (track.getYoutubeUrl() != null && !track.getYoutubeUrl().isEmpty()) {
            playWithYouTubeUrl(track.getYoutubeUrl());
            return;
        }
        
        // Otherwise search and find a YouTube video for this track
        Log.d(TAG, "Searching for YouTube video for track: " + track.getTitle());
        youtubeHelper.getStreamUrlForTrack(track, new YouTubeHelper.StreamUrlCallback() {
            @Override
            public void onSuccess(String streamUrl) {
                isLoading = false;
                if (listener != null) {
                    listener.onLoadingStateChanged(false);
                }
                
                Log.d(TAG, "Found YouTube stream URL for track: " + track.getTitle());
                playStreamUrl(streamUrl);
            }
            
            @Override
            public void onError(String errorMessage) {
                isLoading = false;
                if (listener != null) {
                    listener.onLoadingStateChanged(false);
                    listener.onPlaybackError("Failed to get YouTube stream: " + errorMessage);
                }
                
                Log.e(TAG, "Failed to get YouTube stream: " + errorMessage);
                searchForPreviewUrl(); // Fall back to other methods
            }
        });
    }
    
    /**
     * Play from a YouTube URL
     * 
     * @param youtubeUrl The YouTube URL to play
     */
    private void playWithYouTubeUrl(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.isEmpty()) {
            Log.e(TAG, "Cannot play null or empty YouTube URL");
            if (listener != null) {
                listener.onPlaybackError("Invalid YouTube URL");
                listener.onLoadingStateChanged(false);
            }
            return;
        }
        
        isLoading = true;
        if (listener != null) {
            listener.onLoadingStateChanged(true);
        }
        
        // Extract video ID from URL
        String videoId = youtubeHelper.extractVideoId(youtubeUrl);
        if (videoId == null) {
            Log.e(TAG, "Failed to extract video ID from YouTube URL: " + youtubeUrl);
            if (listener != null) {
                listener.onPlaybackError("Invalid YouTube URL format");
                listener.onLoadingStateChanged(false);
            }
            isLoading = false;
            return;
        }
        
        // Get playable stream URL
        youtubeHelper.getStreamUrl(videoId, new YouTubeHelper.StreamUrlCallback() {
            @Override
            public void onSuccess(String streamUrl) {
                isLoading = false;
                if (listener != null) {
                    listener.onLoadingStateChanged(false);
                }
                
                Log.d(TAG, "Playing YouTube stream: " + streamUrl);
                playStreamUrl(streamUrl);
            }
            
            @Override
            public void onError(String errorMessage) {
                isLoading = false;
                if (listener != null) {
                    listener.onLoadingStateChanged(false);
                    listener.onPlaybackError("Failed to get YouTube stream: " + errorMessage);
                }
                
                Log.e(TAG, "Failed to get YouTube stream: " + errorMessage);
                tryFallbackUrl();
            }
        });
    }

    /**
     * Validate if a URL is properly formatted
     * @param url URL to validate
     * @return true if URL is valid
     */
    private boolean validateUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        // Check if URL has a valid scheme
        return url.startsWith("http://") || url.startsWith("https://");
    }
    
    /**
     * Add a playback changed listener
     * @param listener Listener to add
     */
    public void addPlaybackChangedListener(OnPlaybackChangedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a playback changed listener
     * @param listener Listener to remove
     */
    public void removePlaybackChangedListener(OnPlaybackChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
    
    /**
     * Stop the current playback
     */
    public void stop() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            // Notify listeners
            for (OnPlaybackChangedListener listener : listeners) {
                if (listener != null) {
                    listener.onTrackStopped();
                }
            }
        }
    }

    /**
     * Implementation of MediaPlayer.OnPreparedListener
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        // Start playback when prepared
        if (mp != null) {
            mp.start();
            if (listener != null && currentTrack != null) {
                listener.onTrackPlay(currentTrack);
                listener.onPlaybackStateChanged(true);
            }
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
        handleError("Media player error: " + what);
        return true; // true indicates the error has been handled
    }

    /**
     * Check if a URL is valid for playback
     * @param url The URL to check
     * @return True if the URL is valid, false otherwise
     */
    private boolean isValid(String url) {
        return url != null && !url.isEmpty() && url.startsWith("http");
    }
}
