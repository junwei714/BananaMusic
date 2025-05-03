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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import android.widget.Toast;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.TrackHistory;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import my.edu.utar.bananamusic.utils.callbacks.CallbackCompat;

@SuppressLint("StaticFieldLeak")
public class AudioPlayerHelper implements MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
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
    
    private HashMap<String, TrackHistory> trackHistory = new HashMap<>();
    
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
    
    // Add a new class field
    private boolean isUpdatingPlaybackState = false;
    
    // Add apiDataProvider as a class field
    private ApiDataProvider apiDataProvider;
    
    private Runnable onCompletionListener;
    
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
        void onTrackComplete();
    }
    
    private AudioPlayerHelper(Context context) {
        this.context = context.getApplicationContext();
        
        // Initialize apiDataProvider 
        this.apiDataProvider = ApiDataProvider.getInstance(context);
        
        this.handler = new Handler(Looper.getMainLooper());
        this.soundCloudHelper = SoundCloudHelper.getInstance(context);
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.deezerHelper = DeezerHelper.getInstance(context);
        this.pipedHelper = PipedHelper.getInstance(context);
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
        
        // First priority: Check if this is a Deezer track
        if (track.isDeezerTrack()) {
            Log.d(TAG, "Track is from Deezer, playing Deezer track");
            playDeezerTrack(track);
            return;
        }
        
        // Second priority: Handle library playlist tracks without a preset source
        // For these, we'll use Deezer as the main source to ensure consistent playback
        if (track.getSource() == null || track.getSource().isEmpty()) {
            Log.d(TAG, "Library track without source, using Deezer");
            playDeezerTrack(track);
            return;
        }
        
        // Third priority: Check for previewUrl as it may be available for any source
        if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
            Log.d(TAG, "Track has a preview URL: " + track.getPreviewUrl());
            playStreamUrl(track.getPreviewUrl());
            return;
        }
        
        // No playable source found yet, try to get a preview URL using Deezer
        Log.d(TAG, "No direct preview URL found, searching for a preview using Deezer...");
        playDeezerTrack(track);
    }
    
    /**
     * Play a track from Deezer
     * 
     * @param track The track to play
     */
    private void playDeezerTrack(Track track) {
        if (track == null) {
            Log.e(TAG, "Cannot play null track");
            notifyPlaybackError("Cannot play null track");
            return;
        }
        
        Log.d(TAG, "Playing Deezer track: " + track.getTitle() + " by " + track.getArtist());
        
        // Update UI to show loading state
        setLoadingState(true);
        if (listener != null) {
            runOnMainThread(() -> {
                if (listener != null) {
                    listener.onBufferingStart();
                }
            });
        }
        
        // First try cache - check if we have a valid preview URL in the track object
        if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty() && !failedUrls.contains(track.getPreviewUrl())) {
            try {
                Log.d(TAG, "Using cached preview URL: " + track.getPreviewUrl());
                playStreamUrl(track.getPreviewUrl());
                
                // Store the current track for UI updates
                this.currentTrack = track;
                
                // Update play history
                updateTrackHistory(track);
                
                // Notify listeners
                if (listener != null) {
                    runOnMainThread(() -> {
                        if (listener != null) {
                            listener.onTrackPlay(track);
                            listener.onTrackChanged(track);
                        }
                    });
                }
                
                return;
            } catch (Exception e) {
                Log.e(TAG, "Error playing cached preview URL: " + e.getMessage());
                failedUrls.add(track.getPreviewUrl());
                // Continue to request a new URL
            }
        }
        
        // No valid cached URL, request a new one
        requestNewDeezerUrl(track);
    }
    
    /**
     * Request a new Deezer preview URL for the given track
     */
    private void requestNewDeezerUrl(Track track) {
        // Create a structured search query for better results
        String query = track.getTitle() + " " + track.getArtist();
        
        // First try the exact artist and title search
        deezerHelper.getPreviewUrl(track.getTitle(), track.getArtist(), new DeezerHelper.DeezerCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                // Store the URL in the track for future use
                track.setPreviewUrl(previewUrl);
                track.setSource(Track.SOURCE_DEEZER);
                
                // Play the track
                playStreamUrl(previewUrl);
                
                // Store the current track for UI updates
                currentTrack = track;
                
                // Update play history
                updateTrackHistory(track);
                
                // Notify listeners
                if (listener != null) {
                    runOnMainThread(() -> {
                        if (listener != null) {
                            listener.onTrackPlay(track);
                            listener.onTrackChanged(track);
                            listener.onBufferingEnd();
                        }
                    });
                }
            }
            
            @Override
            public void onError(String message) {
                Log.w(TAG, "Failed to get exact match preview URL: " + message);
                
                // Try broader search if exact match fails
                deezerHelper.searchTracks(query, 5, new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        if (tracks != null && !tracks.isEmpty()) {
                            // Use the first result as most relevant
                            Track deezerTrack = tracks.get(0);
                            
                            // Log match quality for debugging
                            Log.d(TAG, "Found Deezer track: " + deezerTrack.getTitle() + " by " + deezerTrack.getArtist());
                            Log.d(TAG, "Original track: " + track.getTitle() + " by " + track.getArtist());
                            
                            if (deezerTrack.getPreviewUrl() != null && !deezerTrack.getPreviewUrl().isEmpty()) {
                                // Update the original track with Deezer data
                                track.setPreviewUrl(deezerTrack.getPreviewUrl());
                                track.setSource(Track.SOURCE_DEEZER);
                                track.setDeezerId(deezerTrack.getDeezerId());
                                
                                // If original track has no album art, use Deezer's
                                if ((track.getAlbumArtUrl() == null || track.getAlbumArtUrl().isEmpty()) && 
                                    deezerTrack.getAlbumArtUrl() != null && !deezerTrack.getAlbumArtUrl().isEmpty()) {
                                    track.setAlbumArtUrl(deezerTrack.getAlbumArtUrl());
                                }
                                
                                // Play the track
                                playStreamUrl(track.getPreviewUrl());
                                
                                // Store the current track for UI updates
                                currentTrack = track;
                                
                                // Update play history
                                updateTrackHistory(track);
                                
                                // Notify listeners
                                if (listener != null) {
                                    runOnMainThread(() -> {
                                        if (listener != null) {
                                            listener.onTrackPlay(track);
                                            listener.onTrackChanged(track);
                                            listener.onBufferingEnd();
                                        }
                                    });
                                }
                            } else {
                                handleDeezerFailure("No preview available for track");
                            }
                        } else {
                            handleDeezerFailure("No matching tracks found");
                        }
                    }
                    
                    @Override
                    public void onError(String searchError) {
                        Log.e(TAG, "Deezer search error: " + searchError);
                        // Try SoundCloud as fallback
                        fallbackToSoundCloud(track);
                    }
                });
            }
        });
    }
    
    /**
     * Handle a failure to get Deezer preview URL
     * 
     * @param message Error message
     */
    private void handleDeezerFailure(String message) {
        Log.e(TAG, "Failed to get Deezer preview: " + message);
        
        // Check if we have a SoundCloud helper to try
        if (soundCloudHelper != null && currentTrack != null) {
            Log.d(TAG, "Trying SoundCloud as fallback");
            String query = currentTrack.getTitle() + " " + currentTrack.getArtist();
            soundCloudHelper.searchTrack(query, new SoundCloudHelper.SearchCallback() {
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        Track foundTrack = tracks.get(0);
                        if (foundTrack.getPreviewUrl() != null && !foundTrack.getPreviewUrl().isEmpty()) {
                            Log.d(TAG, "Found alternative on SoundCloud: " + foundTrack.getTitle());
                            
                            // Update our track with the SoundCloud URL
                            currentTrack.setPreviewUrl(foundTrack.getPreviewUrl());
                            currentTrack.setSource(Track.SOURCE_SOUNDCLOUD);
                            
                            playStreamUrl(foundTrack.getPreviewUrl());
                        } else {
                            notifyPlaybackError("No playable sources found for this track");
                        }
                    } else {
                        notifyPlaybackError("No playable sources found for this track");
                    }
                }
                
                public void onError(String scError) {
                    Log.e(TAG, "SoundCloud search error: " + scError);
                    notifyPlaybackError("Failed to get audio: " + message);
                }
            });
        } else {
            // Notify listener of error
            notifyPlaybackError("Failed to get audio: " + message);
        }
    }
    
    /**
     * Notify listeners of a playback error
     */
    private void notifyPlaybackError(String message) {
        lastErrorMessage = message;
        playbackState.setError(true);
        playbackState.setErrorMessage(message);
        
        if (listener != null) {
            // Use our helper method to ensure callbacks run on main thread
            runOnMainThread(() -> {
                if (listener != null) {
                    listener.onPlaybackError(message);
                }
            });
        }
    }
    
    // Helper method to search for a preview URL
    private void searchForPreviewUrl() {
        if (currentTrack == null) {
            Log.e(TAG, "Cannot search for preview: currentTrack is null");
            return;
        }
        
        Log.d(TAG, "Searching for preview URL for: " + currentTrack.getTitle());
        
        // Try to get preview from Deezer first
        deezerHelper.getPreviewUrl(currentTrack.getTitle(), currentTrack.getArtist(), new DeezerHelper.DeezerCallback() {
            public void onSuccess(String previewUrl) {
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    Log.d(TAG, "Got Deezer preview URL: " + previewUrl);
                    
                    // Update track with preview URL
                    currentTrack.setPreviewUrl(previewUrl);
                    currentTrack.setSource(Track.SOURCE_DEEZER);
                    
                    // Verify the URL is valid before using it
                    deezerHelper.verifyPreviewUrl(previewUrl, new DeezerHelper.DeezerCallback() {
                        public void onSuccess(String verifiedUrl) {
                            playStreamUrl(verifiedUrl);
                        }
                        
                        public void onError(String message) {
                            tryFallbackUrl();
                        }
                    });
                } else {
                    tryFallbackUrl();
                }
            }
            
            public void onError(String message) {
                Log.e(TAG, "Error getting Deezer preview: " + message);
                tryFallbackUrl();
            }
        });
    }
                
    // Helper method to try fallback URL
    private void tryFallbackUrl() {
        // No Deezer preview available, try alternative sources
        Log.d(TAG, "No Deezer preview, trying alternative sources");
        
        // Try SoundCloud as another option
        if (currentTrack != null) {
            String searchQuery = currentTrack.getTitle() + " " + currentTrack.getArtist();
            Log.d(TAG, "Searching SoundCloud for: " + searchQuery);
            
            // Use SoundCloud to find an alternative
            soundCloudHelper.searchTrack(searchQuery, 
                                       new SoundCloudHelper.SearchCallback() {
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        Track foundTrack = tracks.get(0);
                        if (foundTrack.getPreviewUrl() != null && !foundTrack.getPreviewUrl().isEmpty()) {
                            Log.d(TAG, "Found alternative on SoundCloud: " + foundTrack.getTitle());
                            playStreamUrl(foundTrack.getPreviewUrl());
                        } else {
                            handleNoPlaybackOptions(currentTrack);
                        }
                    } else {
                        handleNoPlaybackOptions(currentTrack);
                    }
                }
                
                public void onError(String message) {
                    Log.e(TAG, "SoundCloud search error: " + message);
                    handleNoPlaybackOptions(currentTrack);
                }
            });
        } else {
            handleError("No track information available");
        }
    }
    
    /**
     * Plays a track by title and artist using Deezer as the source
     * 
     * @param title The track title
     * @param artist The artist name
     */
    public void playDeezerPreview(String title, String artist) {
        // Check if we have a valid track
        if (title == null || title.isEmpty()) {
            if (listener != null) {
                listener.onPlaybackError("Invalid track information");
            }
            return;
        }
        
        Log.d(TAG, "Searching for Deezer preview for: " + title + " by " + artist);
        
        // Set loading state
        isLoading = true;
        if (listener != null) {
            listener.onLoadingStateChanged(true);
        }
        
        deezerHelper.getPreviewUrl(title, artist, new DeezerHelper.DeezerCallback() {
            public void onSuccess(String previewUrl) {
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    Log.d(TAG, "Got Deezer preview URL: " + previewUrl);
                    
                    // Create a track object if none exists
                    if (currentTrack == null) {
                        // Create track with a unique ID
                        String id = System.currentTimeMillis() + "_" + title.hashCode();
                        String album = "Unknown Album";
                        String albumArtUrl = "";
                        int duration = 30000; // Default 30 seconds
                        currentTrack = new Track(id, title, artist, album, albumArtUrl, 
                                                duration, previewUrl, true);
                        currentTrack.setSource(Track.SOURCE_DEEZER);
                    } else {
                        currentTrack.setPreviewUrl(previewUrl);
                        currentTrack.setSource(Track.SOURCE_DEEZER);
                    }
                    
                    playStreamUrl(previewUrl);
                } else {
                    Log.e(TAG, "Empty Deezer preview URL for: " + title);
                    handleError("No preview available for this track");
                }
                
                // Reset loading state
                isLoading = false;
                if (listener != null) {
                    listener.onLoadingStateChanged(false);
                }
            }
            
            public void onError(String message) {
                Log.e(TAG, "Error getting Deezer preview: " + message);
                handleError("Error getting preview: " + message);
                
                // Reset loading state
                isLoading = false;
                if (listener != null) {
                    listener.onLoadingStateChanged(false);
                }
            }
        });
    }
    
    /**
     * Search for songs on Deezer and return them as Track objects
     * 
     * @param query The search query
     * @param callback Callback to provide results
     */
    public void searchDeezerTracks(String query, TracksCallback callback) {
        // Create wrapper callback to convert between interfaces
        my.edu.utar.bananamusic.utils.callbacks.TracksCallback utilsCallback = 
            new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    callback.onTracksLoaded(tracks);
                }

                @Override
                public void onError(String errorMessage) {
                    callback.onError(errorMessage);
                }
            };
        deezerHelper.searchAndConvertToTracks(query, utilsCallback);
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
                deezerHelper.getPreviewUrl(currentTrack.getTitle(), currentTrack.getArtist(), new DeezerHelper.DeezerCallback() {
                    @Override
                    public void onSuccess(String freshUrl) {
                        if (freshUrl != null && !freshUrl.isEmpty()) {
                            Log.d(TAG, "Got fresh Deezer preview URL: " + freshUrl);
                            currentTrack.setPreviewUrl(freshUrl);
                            // Play with the fresh URL
                            continuePlayStreamUrl(freshUrl);
                        } else {
                            Log.e(TAG, "Failed to get fresh URL, trying with original");
                            continuePlayStreamUrl(streamUrl);
                        }
                    }
                    
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Error getting fresh URL: " + message + ". Trying original URL.");
                        continuePlayStreamUrl(streamUrl);
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
        try {
            // Create a new media player
            releaseMediaPlayer();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            
            // Set the data source (the URL to play)
            mediaPlayer.setDataSource(streamUrl);
            mediaPlayer.setOnCompletionListener(this);
            
            // Set up additional event listeners for better UI feedback
            setupMediaPlayerListeners();
            
            // Start preparing the media player
            isPreparing = true;
            mediaPlayer.prepareAsync();
            
            // Track this song for personalized recommendations
            trackSongForRecommendations();
            
            // Tell the listener we're buffering
            updateBufferingState(true);
            
            // Store current URL
            currentTrackUrl = streamUrl;
            
            // Acquire wake lock to allow background playback
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(3600*1000); // 1 hour timeout
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing stream: " + e.getMessage());
            // Handle error
            handleError("Error playing audio: " + e.getMessage());
        }
    }
    
    /**
     * Track the currently playing song for future recommendations
     */
    private void trackSongForRecommendations() {
        if (currentTrack != null) {
            RecommendationEngine recommender = RecommendationEngine.getInstance(context);
            // Method was renamed from trackListened to trackListenedSong
            recommender.trackListenedSong(currentTrack);
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
                } else {
                    // Queue is empty and not repeating - attempt to get recommendations
                    if (currentTrack != null && preferences != null && 
                        preferences.getBoolean("enable_auto_recommendations", true)) {
                        
                        Log.d(TAG, "Queue ended, fetching recommendations based on last track");
                        fetchRecommendationsAndContinue();
                    }
                }
            }
        }
    }
    
    private void fetchRecommendationsAndContinue() {
        if (currentTrack == null) {
            // Handle empty queue
            stopPlaybackAfterQueueEnded();
            return;
        }

        // This method was removed, use getPersonalizedRecommendations instead
        RecommendationEngine.getInstance(context).getPersonalizedRecommendations(5, 
            new RecommendationEngine.RecommendationCallback() {
                @Override
                public void onSuccess(List<Track> recommendedTracks) {
                    // Filter out tracks that are already in the queue or recently played
                    List<Track> filteredTracks = new ArrayList<>();
                    for (Track track : recommendedTracks) {
                        // Check if the track is already in the queue
                        boolean isInQueue = false;
                        List<Track> queueTracks = playQueue.getQueue();
                        for (Track queueTrack : queueTracks) {
                            if (queueTrack.getId() != null && queueTrack.getId().equals(track.getId())) {
                                isInQueue = true;
                                break;
                            }
                        }
                        
                        if (!isInQueue && !wasRecentlyPlayed(track)) {
                            filteredTracks.add(track);
                        }
                    }
                    
                    // Add filtered recommendations to the queue
                    if (!filteredTracks.isEmpty()) {
                        // Take at most 3 tracks
                        int tracksToAdd = Math.min(filteredTracks.size(), 3);
                        for (int i = 0; i < tracksToAdd; i++) {
                            playQueue.add(filteredTracks.get(i));
                        }
                        playNext();
                    } else {
                        // No usable recommendations, try getting recommendations by genre
                        getGenreRecommendations();
                    }
                }
                
                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error getting recommendations: " + errorMessage);
                    getGenreRecommendations();
                }
            });
    }
    
    private boolean wasRecentlyPlayed(Track track) {
        if (track == null || track.getId() == null) return false;
        
        // Check if track is in recently played history
        TrackHistory history = trackHistory.get(track.getId());
        if (history == null) return false;
        
        // Consider a track recently played if it was played in the last 24 hours
        long oneDayMs = 24 * 60 * 60 * 1000;
        return (System.currentTimeMillis() - history.getLastPlayed()) < oneDayMs;
    }
    
    private void getGenreRecommendations() {
        // Get the genre associated with the most recently played songs
        if (apiDataProvider != null) {
            String query = "genre:\"" + currentTrack.getGenre() + "\"";
            
            apiDataProvider.getRecommendedTracks(query, 5, new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
                @Override
                public void onSuccess(List<Track> genreTracks) {
                    if (genreTracks != null && !genreTracks.isEmpty()) {
                        // Shuffle the tracks to get different ones each time
                        Collections.shuffle(genreTracks);
                        
                        // Limit to first 3 tracks
                        int count = Math.min(3, genreTracks.size());
                        List<Track> limitedTracks = genreTracks.subList(0, count);
                        
                        // Add these tracks to the queue after the current item
                        for (Track track : limitedTracks) {
                            addToQueueNext(track);
                        }
                        
                        playNext();
                    } else {
                        Log.d(TAG, "No genre recommendations found, using general recommendations");
                        fetchRecommendationsAndContinue();
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting genre recommendations: " + message);
                    // Fall back to general recommendations
                    fetchRecommendationsAndContinue();
                }
            });
        } else {
            // If API provider is not available, use general recommendations
            fetchRecommendationsAndContinue();
        }
    }
    
    private void stopPlaybackAfterQueueEnded() {
        runOnMainThread(() -> {
            stopPlayback();
            if (listener != null) {
                listener.onLoadingStateChanged(false);
            }
        });
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
            Log.d(TAG, "Playing playlist: " + playlist.getName());
            playQueue.setQueueFromPlaylist(playlist);
            Track firstTrack = playQueue.resetToFirst();
            if (firstTrack != null) {
                // Prepare all tracks in playlist for Deezer playback
                preparePlaylistTracksForDeezer(playlist, () -> {
                    playTrack(firstTrack);
                });
            }
        }
    }
    
    /**
     * Prepare all tracks in a playlist for Deezer playback by pre-fetching preview URLs
     * This improves the playback experience by reducing delays between tracks
     * 
     * @param playlist The playlist to prepare
     * @param callback Callback to run after preparation
     */
    private void preparePlaylistTracksForDeezer(Playlist playlist, Runnable callback) {
        if (playlist == null || playlist.getTracks() == null || playlist.getTracks().isEmpty()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        
        List<Track> tracks = playlist.getTracks();
        // Only preload next 3 tracks to reduce initial loading time
        int currentIndex = playQueue != null ? playQueue.getCurrentIndex() : 0;
        int endIndex = Math.min(currentIndex + 3, tracks.size());
        List<Track> tracksToPreload = tracks.subList(currentIndex, endIndex);
        
        final int[] tracksToProcess = {tracksToPreload.size()};
        final int[] processedTracks = {0};
        
        Log.d(TAG, "Preparing " + tracksToProcess[0] + " tracks for playback");
        
        // Start playing the first track immediately if it has a preview URL
        Track firstTrack = tracks.get(currentIndex);
        if (firstTrack.getPreviewUrl() != null && !firstTrack.getPreviewUrl().isEmpty()) {
            if (callback != null) {
                callback.run();
            }
        }
        
        for (Track track : tracksToPreload) {
            // Skip if track already has a preview URL
            if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
                processedTracks[0]++;
                if (processedTracks[0] >= tracksToProcess[0] && callback != null) {
                    callback.run();
                }
                continue;
            }
            
            // Use Deezer to get preview URLs
            deezerHelper.getPreviewUrl(track.getTitle(), track.getArtist(), new DeezerHelper.DeezerCallback() {
                @Override
                public void onSuccess(String previewUrl) {
                    track.setPreviewUrl(previewUrl);
                    track.setSource(Track.SOURCE_DEEZER);
                    
                    processedTracks[0]++;
                    Log.d(TAG, "Prepared track " + processedTracks[0] + "/" + tracksToProcess[0] + ": " + track.getTitle());
                    
                    if (processedTracks[0] >= tracksToProcess[0] && callback != null) {
                        callback.run();
                    }
                }
                
                @Override
                public void onError(String message) {
                    // Still count as processed but log error
                    Log.e(TAG, "Failed to prepare track: " + track.getTitle() + " - " + message);
                    
                    processedTracks[0]++;
                    if (processedTracks[0] >= tracksToProcess[0] && callback != null) {
                        callback.run();
                    }
                }
            });
        }
        
        // Start background preloading for the rest of the playlist
        new Thread(() -> {
            for (int i = endIndex; i < tracks.size(); i++) {
                Track track = tracks.get(i);
                if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
                    continue;
                }
                
                try {
                    // Add a small delay to avoid overwhelming the server
                    Thread.sleep(100);
                    
                    deezerHelper.getPreviewUrl(track.getTitle(), track.getArtist(), new DeezerHelper.DeezerCallback() {
                        @Override
                        public void onSuccess(String previewUrl) {
                            track.setPreviewUrl(previewUrl);
                            track.setSource(Track.SOURCE_DEEZER);
                            Log.d(TAG, "Background prepared track: " + track.getTitle());
                        }
                        
                        @Override
                        public void onError(String message) {
                            Log.e(TAG, "Failed to prepare track in background: " + track.getTitle() + " - " + message);
                        }
                    });
                } catch (InterruptedException e) {
                    Log.e(TAG, "Background preloading interrupted", e);
                    break;
                }
            }
        }).start();
    }
    
    public void playTrackInPlaylist(Playlist playlist, Track track) {
        if (playQueue != null && playlist != null && track != null) {
            Log.d(TAG, "Playing track in playlist: " + track.getTitle() + " in " + playlist.getName());
            playQueue.setQueueFromPlaylist(playlist);
            
            // Find the track in the playlist and skip to it
            List<Track> tracks = playlist.getTracks();
            int trackIndex = -1;
            
            for (int i = 0; i < tracks.size(); i++) {
                if (tracks.get(i).getId().equals(track.getId())) {
                    trackIndex = i;
                    break;
                }
            }
            
            if (trackIndex != -1) {
                final int selectedIndex = trackIndex;
                
                // Prepare all tracks in playlist for Deezer playback
                preparePlaylistTracksForDeezer(playlist, () -> {
                    Track selectedTrack = playQueue.skipToIndex(selectedIndex);
                    if (selectedTrack != null) {
                        playTrack(selectedTrack);
                    }
                });
            } else {
                // Track not found in playlist, play first track
                playPlaylist(playlist);
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
        // Add completed track to recently played
        if (currentTrack != null) {
            // Log for debugging
            Log.d(TAG, "Track completed: " + currentTrack.getTitle());
            
            // Add to recently played
            RecentlyPlayedManager.getInstance(context).addTrackToRecentlyPlayed(currentTrack);
            
            // Update track history
            updateTrackHistory(currentTrack);
            
            // Notify UI that track history has changed
            if (listener != null) {
                runOnMainThread(() -> {
                    if (listener != null) {
                        listener.onPlaybackStateChanged(false);
                        listener.onTrackComplete();
                    }
                });
            }
        }
        
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
        updatePlaybackState();
        return playbackState;
    }

    private void updatePlaybackState() {
        if (mediaPlayer != null) {
            try {
                playbackState.setPlaying(mediaPlayer.isPlaying());
                playbackState.setCurrentPosition(mediaPlayer.getCurrentPosition());
                playbackState.setDuration(mediaPlayer.getDuration());
            } catch (Exception e) {
                Log.e(TAG, "Error updating playback state: " + e.getMessage());
            }
        }
        
        // Notify any listeners
        notifyPlaybackStateChanged();
    }

    private void notifyPlaybackStateChanged() {
        if (listener != null) {
            // Use our helper method to ensure callbacks run on main thread
            runOnMainThread(() -> {
                if (listener != null) {
                    listener.onPlaybackStateChanged(playbackState.isPlaying());
                }
            });
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
            // Use our helper method to ensure callbacks run on main thread
            if (buffering) {
                runOnMainThread(() -> {
                    if (listener != null) {
                        listener.onBufferingStart();
                    }
                });
            } else {
                runOnMainThread(() -> {
                    if (listener != null) {
                        listener.onBufferingEnd();
                    }
                });
            }
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

            // Add track to history
            if (currentTrack != null) {
                updateTrackHistory(currentTrack);
            }

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
     * Recreate the media player after a fatal error
     */
    private void recreateMediaPlayer() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            setupMediaPlayerListeners();
            Log.d(TAG, "MediaPlayer recreated after error");
        } catch (Exception e) {
            Log.e(TAG, "Error recreating media player: " + e.getMessage(), e);
        }
    }
    
    /**
     * Try to recover from a playback error by playing a fallback URL
     */
    private void tryRecoverFromError() {
        if (currentTrack == null) {
            return;
        }
        
        // Try to get a fresh preview URL from Deezer
        Log.d(TAG, "Attempting to recover from error for: " + currentTrack.getTitle());
        
        deezerHelper.getPreviewUrl(currentTrack.getTitle(), currentTrack.getArtist(), new DeezerHelper.DeezerCallback() {
            public void onSuccess(String previewUrl) {
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    Log.d(TAG, "Got fresh Deezer preview URL: " + previewUrl);
                    currentTrack.setPreviewUrl(previewUrl);
                    playStreamUrl(previewUrl);
                } else {
                    // No Deezer preview available, try other sources
                    tryFallbackUrl();
                }
            }
            
            public void onError(String message) {
                Log.e(TAG, "Error getting fresh Deezer preview: " + message);
                // Try other sources
                tryFallbackUrl();
            }
        });
    }

    private void prepareNextTrack(Track track) {
        if (track == null) return;
        
        // Store reference to next track
        nextTrack = track;
        
        // Release existing next player if it exists
        releaseNextMediaPlayer();
        
        // Only proceed if the current media player is valid
        if (mediaPlayer == null || !mediaPlayer.isPlaying()) return;
        
        try {
            // Create new media player for next track
            nextMediaPlayer = new MediaPlayer();
            
            // Set audio attributes similar to main player
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
                nextMediaPlayer.setAudioAttributes(audioAttributes);
            } else {
                nextMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            
            // Get URL for the next track
            String url = getAudioUrl(track);
            if (url == null || url.isEmpty()) {
                Log.e(TAG, "No valid URL for next track: " + track.getTitle());
                releaseNextMediaPlayer();
                return;
            }

            // Cache the next track for faster playback if appropriate
            if (musicCache != null && preferences != null && 
                preferences.getBoolean("enable_track_caching", true) &&
                !url.startsWith("content://")) {
                musicCache.cacheTrackInBackground(track, url);
            }
            
            // Prepare the next track
            nextMediaPlayer.setDataSource(url);
            nextMediaPlayer.setOnPreparedListener(mp -> {
                // Set up gapless playback once prepared
                try {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayer.setNextMediaPlayer(nextMediaPlayer);
                        Log.d(TAG, "Next track prepared for gapless playback: " + track.getTitle());
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error setting next media player: " + e.getMessage());
                    releaseNextMediaPlayer();
                }
            });
            
            // Handle errors in next track
            nextMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Error preparing next track: " + what + ", " + extra);
                releaseNextMediaPlayer();
                return true;
            });
            
            // Start preparing the next track
            nextMediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare next track: " + e.getMessage());
            releaseNextMediaPlayer();
        }
    }

    private void startCrossfade() {
        if (crossfadeDuration <= 0 || nextMediaPlayer == null || !isCrossfading) {
            // If crossfade disabled or next player not ready, switch directly
            Log.d(TAG, "Crossfade not available or disabled, switching directly");
            switchToNextTrack();
            return;
        }

        // Calculate when to start the crossfade based on current position
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            final int currentPosition = mediaPlayer.getCurrentPosition();
            final int totalDuration = mediaPlayer.getDuration();
            final int timeLeft = totalDuration - currentPosition;
            
            if (timeLeft <= crossfadeDuration + 100) { // Add small buffer
                // Start crossfade now
                Log.d(TAG, "Starting crossfade immediately (near end of track)");
                applyCrossfade();
            } else {
                // Schedule crossfade for later
                int delayBeforeCrossfade = timeLeft - crossfadeDuration;
                Log.d(TAG, "Scheduling crossfade in " + delayBeforeCrossfade + "ms");
                
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
        applyCrossfade();
                    }
                }, delayBeforeCrossfade);
            }
        } else {
            // Not playing or no media player, switch directly
            switchToNextTrack();
        }
    }

    private void applyCrossfade() {
        if (mediaPlayer == null || nextMediaPlayer == null || !mediaPlayer.isPlaying()) {
            Log.w(TAG, "Cannot apply crossfade - one of the players is null or not playing");
            switchToNextTrack();
            return;
        }
        
        isCrossfading = true;
        
        final int duration = preferences != null 
            ? preferences.getInt("crossfade_duration_ms", DEFAULT_CROSSFADE_DURATION) 
            : DEFAULT_CROSSFADE_DURATION;
        
        final float startVolume = maxVolume;
        final float endVolume = 0f;
        
        // Get current position
        final int currentPosition = mediaPlayer.getCurrentPosition();
        final int totalDuration = mediaPlayer.getDuration();
        
        // Only crossfade if we have enough time left in the current track
        if (totalDuration - currentPosition <= duration) {
            Log.d(TAG, "Not enough time left for crossfade, switching directly");
            switchToNextTrack();
            return;
        }
        
        // Start the next player with volume 0
        try {
            nextMediaPlayer.setVolume(0f, 0f);
            nextMediaPlayer.start();
            
            Log.d(TAG, "Starting crossfade over " + duration + "ms");
            
            // Create a timer to handle the volume fade
            final int steps = 20; // Number of volume adjustment steps
            final int stepDuration = duration / steps;
            final float volumeStep = startVolume / steps;
            
            // Use a handler for smoother volume transitions
            final Handler fadeHandler = new Handler(Looper.getMainLooper());
            final Runnable fadeRunnable = new Runnable() {
                private int currentStep = 0;
                
                @Override
                public void run() {
                    if (mediaPlayer == null || nextMediaPlayer == null) {
                        // One of the players was released, abort crossfade
                        isCrossfading = false;
                        return;
                    }
                    
                    currentStep++;
                    float currentFirstVolume = Math.max(0f, startVolume - (volumeStep * currentStep));
                    float currentSecondVolume = Math.min(maxVolume, volumeStep * currentStep);
                    
                    try {
                        // Apply volume changes
                        mediaPlayer.setVolume(currentFirstVolume, currentFirstVolume);
                        nextMediaPlayer.setVolume(currentSecondVolume, currentSecondVolume);
                        
                        // Log progress for debug purposes
                        if (currentStep % 5 == 0) {
                            Log.d(TAG, "Crossfade step " + currentStep + ": " +
                                "First player volume: " + currentFirstVolume +
                                ", Second player volume: " + currentSecondVolume);
                        }
                        
                        // Check if we've completed all steps
                        if (currentStep >= steps) {
                            // Crossfade complete, clean up first player
                            Log.d(TAG, "Crossfade complete, switching fully to next track");
                            if (mediaPlayer != null) {
                                mediaPlayer.stop();
                                mediaPlayer.release();
                            }
                            mediaPlayer = nextMediaPlayer;
                            mediaPlayer.setVolume(maxVolume, maxVolume);
                            nextMediaPlayer = null;
                            
                            // Update track information
                            currentTrack = nextTrack;
        nextTrack = null;
        
                            // Notify listeners
                            if (listener != null) {
                                runOnMainThread(() -> {
                                    if (listener != null) {
                                        listener.onTrackChanged(currentTrack);
                                    }
                                });
                            }
                            
                            isCrossfading = false;
        } else {
                            // Schedule next step
                            fadeHandler.postDelayed(this, stepDuration);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error during crossfade: " + e.getMessage());
                        // Error during crossfade, fall back to direct switch
                        switchToNextTrack();
                    }
                }
            };
            
            // Start the fade process
            fadeHandler.post(fadeRunnable);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start crossfade: " + e.getMessage());
            // Error starting crossfade, fall back to direct switch
            switchToNextTrack();
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

    /**
     * Enable or disable crossfading between tracks
     * @param enabled True to enable crossfade, false to disable
     */
    public void setCrossfadingEnabled(boolean enabled) {
        if (!enabled) {
            // If disabling, set duration to 0
            crossfadeDuration = 0;
        } else if (crossfadeDuration == 0) {
            // If enabling and duration was 0, set to default
            crossfadeDuration = DEFAULT_CROSSFADE_DURATION;
        }
        
        // Store preference
        if (preferences != null) {
            preferences.setBoolean("enable_crossfade", enabled);
        }
        
        Log.d(TAG, "Crossfading " + (enabled ? "enabled" : "disabled") + 
                   ", duration: " + crossfadeDuration + "ms");
    }
    
    /**
     * Set low latency audio mode
     * @param enabled True to enable low latency mode, false to disable
     */
    public void setLowLatencyMode(boolean enabled) {
        // This will affect new MediaPlayer instances
        if (preferences != null) {
            preferences.setBoolean("use_low_latency_audio", enabled);
        }
        
        // We don't immediately apply this to existing players as it requires
        // recreation of the MediaPlayer, which would interrupt playback
        Log.d(TAG, "Low latency mode " + (enabled ? "enabled" : "disabled") + 
                   " (will apply to new playback sessions)");
    }
    
    /**
     * Check if low latency mode is enabled
     * @return True if low latency mode is enabled
     */
    public boolean isLowLatencyModeEnabled() {
        return preferences != null && 
               preferences.getBoolean("use_low_latency_audio", false);
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
        
        if (currentTrack != null) {
            try {
                // First, try releasing memory by clearing artwork
                if (currentTrack.getAlbumArtUrl() != null) {
                    Log.d(TAG, "Clearing artwork reference to release memory");
                    currentTrack.setAlbumArtUrl(null);
                }
                
                // Try recreating the media player
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing media player", e);
                    }
                    
                    String currentUrl = currentTrack.getPreviewUrl();
                    
                    // Try fallback decoder options
                    try {
                        // Create a new MediaPlayer instance
                        mediaPlayer = new MediaPlayer();
                        
                        // Add basic listeners
                        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                            Log.e(TAG, "Media player error during recovery: " + what + ", " + extra);
                            return true;
                        });
                        
                        // Try initializing with different options
                        // This can sometimes get around decoder limitations
                setupMediaPlayer();
                        
                        fixed = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error recreating media player", e);
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

    // Add missing method
    private void initializeMediaPlayer() {
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
        // Guard against recursive calls
        if (isUpdatingPlaybackState) return;
        
        try {
            isUpdatingPlaybackState = true;
            
        // Update internal state
        playbackState.setPlaying(isPlaying);
        
        // Notify listeners
        if (listener != null) {
            listener.onPlaybackStateChanged(isPlaying);
            }
        } finally {
            isUpdatingPlaybackState = false;
        }
    }

    /**
     * Helper method to run callbacks on the main thread
     * 
     * @param runnable The code to run on the main thread
     */
    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Already on main thread, run directly
            runnable.run();
        } else {
            // Post to main thread handler
            new Handler(Looper.getMainLooper()).post(runnable);
        }
    }
    
    /**
     * Update loading state and notify listeners on the main thread
     */
    private void setLoadingState(boolean isLoading) {
        this.isLoading = isLoading;
        
        if (listener != null) {
            final boolean loading = isLoading;
            runOnMainThread(() -> {
                if (listener != null) {
                    listener.onLoadingStateChanged(loading);
                }
            });
        }
    }

    /**
     * Test method to simulate listening history for recommendations
     * This allows testing the recommendation system without actually playing songs
     * 
     * @param genre A genre to simulate listening to (e.g., "rock", "pop", etc.)
     * @param count Number of tracks to simulate
     */
    public void simulateListeningHistory(String genre, int count) {
        RecommendationEngine recommender = RecommendationEngine.getInstance(context);
        
        for (int i = 0; i < count; i++) {
            // Create a dummy track with the specified genre
            Track dummyTrack = new Track();
            dummyTrack.setId("dummy_" + System.currentTimeMillis() + "_" + i);
            dummyTrack.setTitle("Dummy Track " + i);
            dummyTrack.setArtist("Dummy Artist");
            // Set the genre using appropriate method
            dummyTrack.setGenres(new String[] { genre });
            
            // Method was renamed from trackListened to trackListenedSong
            recommender.trackListenedSong(dummyTrack);
        }
    }

    /**
     * Retrieves the user's recently played tracks from the history
     * @return List of recently played tracks, ordered by most recent first
     */
    public List<Track> getRecentlyPlayedTracks() {
        List<Track> recentlyPlayed = new ArrayList<>();
        
        // Check if we have track history data
        if (trackHistory == null || trackHistory.isEmpty()) {
            return recentlyPlayed;
        }
        
        // Get the most recent tracks (up to 5)
        int count = 0;
        for (Map.Entry<String, TrackHistory> entry : trackHistory.entrySet()) {
            if (count >= 5) break;
            
            TrackHistory history = entry.getValue();
            if (history.getTrack() != null) {
                // Clone the track to avoid modifying the original
                Track track = history.getTrack().clone();
                
                // Calculate how long ago this was played
                long timeSinceLastPlayed = System.currentTimeMillis() - history.getLastPlayed();
                String timeAgo = formatTimeAgo(timeSinceLastPlayed);
                
                // Store the time ago in the lastFallbackAttempt field (repurposing it)
                track.setLastFallbackAttempt(timeAgo);
                
                recentlyPlayed.add(track);
                count++;
            }
        }
        
        // Sort by most recently played
        Collections.sort(recentlyPlayed, (t1, t2) -> {
            String time1 = t1.getLastFallbackAttempt();
            String time2 = t2.getLastFallbackAttempt();
            
            // Simple heuristic sorting: shorter time ago = more recent
            // This works because "2 hours ago" is shorter than "2 days ago", etc.
            return time1.length() - time2.length();
        });
        
        return recentlyPlayed;
    }
    
    /**
     * Formats milliseconds into a human-readable "time ago" string
     * @param milliseconds Time in milliseconds
     * @return Formatted string like "2 hours ago"
     */
    private String formatTimeAgo(long milliseconds) {
        long seconds = milliseconds / 1000;
        
        if (seconds < 60) {
            return "Just now";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else if (seconds < 604800) {
            long days = seconds / 86400;
            return days + (days == 1 ? " day ago" : " days ago");
        } else if (seconds < 2592000) {
            long weeks = seconds / 604800;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        } else {
            return "Long time ago";
        }
    }

    /**
     * Update track history when a track is played
     * @param track The track that was played
     */
    private void updateTrackHistory(Track track) {
        if (track == null || track.getId() == null) {
            return;
        }
        
        try {
            // Update the trackHistory HashMap (for detailed history tracking)
            String trackId = track.getId();
            TrackHistory trackHistoryEntry = trackHistory.get(trackId);
            
            if (trackHistoryEntry == null) {
                trackHistoryEntry = new TrackHistory(track);
                trackHistory.put(trackId, trackHistoryEntry);
            } else {
                trackHistoryEntry.incrementPlayCount();
            }
            
            // Also update the regular history list (for simple recently played list)
            // Remove if it exists already (to move it to the top)
            history.remove(track);
            
            // Add to the beginning of history
            history.add(0, track);
            
            // Limit history size
            if (history.size() > 20) {
                history.remove(history.size() - 1);
            }
            
            // Use the new RecentlyPlayedManager to add the track to recently played
            RecentlyPlayedManager recentlyPlayedManager = RecentlyPlayedManager.getInstance(context);
            recentlyPlayedManager.addTrackToRecentlyPlayed(track);
            
            // Notify AI recommendation service
            try {
                // Use reflection to avoid direct dependency
                Class<?> serviceClass = Class.forName("my.edu.utar.bananamusic.services.AIRecommendationService");
                Object service = serviceClass.getMethod("getInstance", Context.class).invoke(null, context);
                serviceClass.getMethod("trackPlayedTrack", Track.class).invoke(service, track);
            } catch (Exception e) {
                // Silently ignore if AI service is not available
                Log.d(TAG, "AI recommendation service not available: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating track history", e);
        }
    }

    // Add method to expose track history map
    public Map<String, TrackHistory> getTrackHistoryMap() {
        return trackHistory;
    }
    
    /**
     * Get a list of track history items
     * @return List of TrackHistory objects
     */
    public List<TrackHistory> getTrackHistoryList() {
        List<TrackHistory> result = new ArrayList<>();
        for (TrackHistory history : trackHistory.values()) {
            result.add(history);
        }
        return result;
    }
    
    /**
     * Get the number of times an artist has been played
     * @param artist Artist name
     * @return Number of plays for the artist
     */
    public int getArtistPlayCount(String artist) {
        if (artist == null || artist.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        for (TrackHistory history : trackHistory.values()) {
            Track track = history.getTrack();
            if (track != null && artist.equals(track.getArtist())) {
                count += history.getPlayCount();
            }
        }
        return count;
    }

    /**
     * Interface for receiving playback state updates
     */
    public interface PlaybackStateListener {
        /**
         * Called when the track changes
         * @param track The new track being played
         */
        void onTrackChanged(Track track);
        
        /**
         * Called when the playback state changes (playing/paused)
         * @param isPlaying true if playing, false if paused
         */
        void onPlaybackStateChanged(boolean isPlaying);
        
        /**
         * Called when buffering progress updates
         * @param percent The percentage of buffering completed (0-100)
         */
        void onBufferingUpdate(int percent);
    }
    
    /**
     * Add a listener for playback state changes
     * @param listener The listener to add
     */
    public void addPlaybackStateListener(PlaybackStateListener listener) {
        if (listener != null) {
            // Implementation would usually store this listener in a list
            // and notify it when events occur
            
            // For now, we'll just handle the initial state
            if (currentTrack != null) {
                listener.onTrackChanged(currentTrack);
                listener.onPlaybackStateChanged(isPlaying());
            }
        }
    }

    private void findRelatedTracks(final Track track, final RelatedTracksCallback callback) {
        if (track == null) {
            if (callback != null) {
                callback.onError("Cannot find related tracks for null track");
            }
            return;
        }

        // Try to find related tracks by the same artist first
        if (apiDataProvider != null) {
            // Use getRecommendedTracks or another suitable method instead of searchTracks
            apiDataProvider.getRecommendedTracks(track.getArtist(), 10, 
                createApiCallback(new RelatedTracksCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        if (tracks != null && !tracks.isEmpty()) {
                            // Filter out the current track
                            List<Track> filtered = new ArrayList<>();
                            for (Track t : tracks) {
                                if (!t.getId().equals(track.getId())) {
                                    filtered.add(t);
                                }
                            }
                            
                            if (!filtered.isEmpty()) {
                                // Shuffle the tracks to get different ones each time
                                Collections.shuffle(filtered);
                                
                                // Limit to first 3 tracks
                                int count = Math.min(3, filtered.size());
                                List<Track> limitedTracks = filtered.subList(0, count);
                                
                                if (callback != null) {
                                    callback.onSuccess(limitedTracks);
                                }
                                return;
                            }
                        }
                        
                        // If no artist tracks, try by genre
                        findTracksByGenre(track, callback);
                    }
                    
                    @Override
                    public void onError(String message) {
                        // Fall back to genre search
                        findTracksByGenre(track, callback);
                    }
                }));
        } else {
            // Fall back to genre if API provider is not available
            findTracksByGenre(track, callback);
        }
    }

    private void findTracksByGenre(final Track track, final RelatedTracksCallback callback) {
        if (track == null || track.getGenre() == null || track.getGenre().isEmpty()) {
            if (callback != null) {
                callback.onError("Cannot find tracks by genre: Track or genre is null");
            }
            return;
        }
        
        if (apiDataProvider != null) {
            // Use getRecommendedTracks or another suitable method instead of searchTracks
            apiDataProvider.getRecommendedTracks(track.getGenre(), 15, 
                createApiCallback(new RelatedTracksCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        if (tracks != null && !tracks.isEmpty()) {
                            // Filter out the current track and any tracks by the same artist
                            List<Track> filtered = new ArrayList<>();
                            for (Track t : tracks) {
                                if (!t.getId().equals(track.getId()) && 
                                    !t.getArtist().equals(track.getArtist())) {
                                    filtered.add(t);
                                }
                            }
                            
                            if (!filtered.isEmpty()) {
                                // Shuffle the tracks to get different ones each time
                                Collections.shuffle(filtered);
                                
                                // Limit to first 3 tracks
                                int count = Math.min(3, filtered.size());
                                List<Track> limitedTracks = filtered.subList(0, count);
                                
                                if (callback != null) {
                                    callback.onSuccess(limitedTracks);
                                }
                                return;
                            }
                        }
                        
                        // If no genre tracks, try random popular tracks
                        getRandomPopularTracks(callback);
                    }
                    
                    @Override
                    public void onError(String message) {
                        // Fall back to random popular tracks
                        getRandomPopularTracks(callback);
                    }
                }));
        } else {
            // Fall back to random popular tracks if API provider is not available
            getRandomPopularTracks(callback);
        }
    }

    /**
     * Interface for related tracks callbacks
     */
    public interface RelatedTracksCallback {
        void onSuccess(List<Track> tracks);
        void onError(String message);
        // Removing onRelatedTracksLoaded as it's redundant with onSuccess
    }

    /**
     * Get random popular tracks as a fallback
     * @param callback Callback to notify when tracks are ready
     */
    private void getRandomPopularTracks(RelatedTracksCallback callback) {
        if (apiDataProvider == null) {
            if (callback != null) {
                callback.onError("API Data Provider is not available");
            }
            return;
        }
        
        // Use API Data Provider to get recommended tracks with "popular" mood
        apiDataProvider.getRecommendedTracks("popular", 10, 
            createApiCallback(new RelatedTracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        // Shuffle the tracks to get different ones each time
                        Collections.shuffle(tracks);
                        
                        // Limit to 5 tracks
                        int count = Math.min(5, tracks.size());
                        List<Track> limitedTracks = tracks.subList(0, count);
                        
                        if (callback != null) {
                            callback.onSuccess(limitedTracks);
                        }
                    } else {
                        // If no tracks found, return empty list
                        if (callback != null) {
                            callback.onSuccess(new ArrayList<>());
                        }
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting random popular tracks: " + message);
                    // Return empty list on error
                    if (callback != null) {
                        callback.onSuccess(new ArrayList<>());
                    }
                }
            }));
    }

    /**
     * Helper method to create an ApiDataProvider.TracksCallback from a standard callback
     */
    private my.edu.utar.bananamusic.utils.callbacks.TracksCallback createApiCallback(
            final RelatedTracksCallback callback) {
        return my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter.createTracksCallback(
            new my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter.CallbackImpl() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    callback.onSuccess(tracks);
                }
                
                @Override
                public void onError(String errorMessage) {
                    callback.onError(errorMessage);
                }
            });
    }

    /**
     * Enhanced method to play a track from Spotify using Deezer as fallback
     * This method provides a more reliable playback experience by trying multiple sources
     */
    public void playSpotifyTrackWithDeezerFallback(Track track) {
        if (track == null) {
            if (listener != null) {
                listener.onPlaybackError("Invalid track data");
                listener.onLoadingStateChanged(false);
            }
            return;
        }

        // Set as current track
        currentTrack = track;
        isLoading = true;

        // Show loading state
        if (listener != null) {
            listener.onLoadingStateChanged(true);
            listener.onBufferingStart();
        }

        Log.d(TAG, "Playing track: " + track.getTitle() + " by " + track.getArtist());

        // Try playing with existing preview URL if available
        if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
            deezerHelper.verifyPreviewUrl(track.getPreviewUrl(), new DeezerHelper.DeezerCallback() {
                @Override
                public void onSuccess(String verifiedUrl) {
                    Log.d(TAG, "Using existing preview URL: " + verifiedUrl);
                    playStreamUrl(verifiedUrl);
                }

                @Override
                public void onError(String message) {
                    Log.d(TAG, "Existing preview URL failed, searching for alternatives");
                    searchForAlternativePreviewUrl(track);
                }
            });
            return;
        }

        // No preview URL available, search for one
        searchForAlternativePreviewUrl(track);
    }

    /**
     * Search for alternative preview URLs when the primary one fails
     */
    private void searchForAlternativePreviewUrl(Track track) {
        // First try Deezer search by track title and artist
        deezerHelper.getPreviewUrl(track.getTitle(), track.getArtist(), new DeezerHelper.DeezerCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    Log.d(TAG, "Found Deezer preview URL: " + previewUrl);
                    track.setPreviewUrl(previewUrl);
                    track.setSource(Track.SOURCE_DEEZER);
                    playStreamUrl(previewUrl);
                } else {
                    // Try Spotify preview as fallback
                    trySpotifyPreview(track);
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Deezer preview error: " + message);
                // Try Spotify preview as fallback
                trySpotifyPreview(track);
            }
        });
    }

    /**
     * Try to get and play a Spotify preview
     */
    private void trySpotifyPreview(Track track) {
        // Check if we have a Spotify ID
        if (track.getSpotifyId() != null && !track.getSpotifyId().isEmpty()) {
            Log.d(TAG, "Trying to get Spotify preview for ID: " + track.getSpotifyId());
            
            spotifyHelper.getFullTrackInfo(track.getSpotifyId(), new SpotifyHelper.SpotifyCallback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject trackInfo = new JSONObject(response);
                        String previewUrl = trackInfo.optString("preview_url", null);
                        
                        if (previewUrl != null && !previewUrl.isEmpty()) {
                            Log.d(TAG, "Found Spotify preview URL: " + previewUrl);
                            track.setPreviewUrl(previewUrl);
                            track.setSource(Track.SOURCE_SPOTIFY);
                            playStreamUrl(previewUrl);
                        } else {
                            Log.d(TAG, "No Spotify preview URL available");
                            handleNoPreviewAvailable();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Spotify track info", e);
                        handleNoPreviewAvailable();
                    }
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting Spotify preview: " + message);
                    handleNoPreviewAvailable();
                }
            });
        } else {
            // Last resort: search Spotify by track name and artist
            searchSpotifyByQuery(track);
        }
    }

    /**
     * Search Spotify by track name and artist as last resort
     */
    private void searchSpotifyByQuery(Track track) {
        String query = track.getTitle() + " " + track.getArtist();
        Log.d(TAG, "Searching Spotify for: " + query);
        
        spotifyHelper.searchTracks(query, new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    JSONObject tracks = json.optJSONObject("tracks");
                    
                    if (tracks != null && tracks.has("items")) {
                        JSONArray items = tracks.getJSONArray("items");
                        
                        if (items.length() > 0) {
                            JSONObject firstTrack = items.getJSONObject(0);
                            String previewUrl = firstTrack.optString("preview_url");
                            
                            if (previewUrl != null && !previewUrl.isEmpty()) {
                                Log.d(TAG, "Found Spotify preview from search: " + previewUrl);
                                track.setPreviewUrl(previewUrl);
                                track.setSource(Track.SOURCE_SPOTIFY);
                                playStreamUrl(previewUrl);
                                return;
                            }
                        }
                    }
                    
                    handleNoPreviewAvailable();
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing Spotify search results", e);
                    handleNoPreviewAvailable();
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Spotify search error: " + message);
                handleNoPreviewAvailable();
            }
        });
    }

    /**
     * Handle the case when no preview is available from any source
     */
    private void handleNoPreviewAvailable() {
        Log.d(TAG, "No preview available from any source");
        
        if (listener != null) {
            listener.onPlaybackError("No preview available for this track");
            listener.onLoadingStateChanged(false);
        }
    }

    /**
     * Try to find an alternative source from SoundCloud when Deezer fails
     * 
     * @param track The track to search for on SoundCloud
     */
    private void fallbackToSoundCloud(Track track) {
        if (track == null) {
            Log.e(TAG, "Cannot search for null track");
            notifyPlaybackError("Cannot search for null track");
            return;
        }
        
        Log.d(TAG, "Trying SoundCloud fallback for: " + track.getTitle() + " by " + track.getArtist());
        
        // Show loading state
        setLoadingState(true);
        
        // Create search query
        String query = track.getTitle() + " " + track.getArtist();
        
        // Try to search for the track on SoundCloud
        if (soundCloudHelper != null) {
            soundCloudHelper.searchTrack(query, new SoundCloudHelper.SearchCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        // We found tracks on SoundCloud
                        Track scTrack = tracks.get(0);
                        
                        Log.d(TAG, "Found SoundCloud match: " + scTrack.getTitle() + " by " + scTrack.getArtist());
                        
                        // Update original track with SoundCloud info
                        if (scTrack.getStreamUrl() != null && !scTrack.getStreamUrl().isEmpty()) {
                            track.setStreamUrl(scTrack.getStreamUrl());
                            track.setSource(Track.SOURCE_SOUNDCLOUD);
                            
                            // Play the track
                            playStreamUrl(scTrack.getStreamUrl());
                            
                            // Update current track
                            currentTrack = track;
                            
                            // Update UI
                            if (listener != null) {
                                runOnMainThread(() -> {
                                    if (listener != null) {
                                        listener.onTrackPlay(track);
                                        listener.onTrackChanged(track);
                                        listener.onBufferingEnd();
                                    }
                                });
                            }
                        } else {
                            notifyPlaybackError("No playable URL found for: " + track.getTitle());
                        }
                    } else {
                        Log.d(TAG, "No SoundCloud matches found for: " + track.getTitle());
                        notifyPlaybackError("Could not find track on SoundCloud");
                    }
                }
                
                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "SoundCloud search error: " + errorMessage);
                    notifyPlaybackError("Error searching SoundCloud: " + errorMessage);
                }
            });
        } else {
            Log.e(TAG, "SoundCloud helper is null, cannot search");
            notifyPlaybackError("SoundCloud service unavailable");
        }
    }
    
    /**
     * Play a preview URL directly
     * @param previewUrl The URL of the preview to play
     */
    public void playPreview(String previewUrl) {
        if (previewUrl == null || previewUrl.isEmpty()) {
            Log.e(TAG, "Cannot play null or empty preview URL");
            notifyPlaybackError("Invalid preview URL");
            return;
        }
        
        Log.d(TAG, "Playing preview URL: " + previewUrl);
        playStreamUrl(previewUrl);
    }
}