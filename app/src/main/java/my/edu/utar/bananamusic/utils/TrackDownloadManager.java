package my.edu.utar.bananamusic.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;

/**
 * Manages downloading tracks for offline listening
 */
public class TrackDownloadManager {
    private static final String TAG = "TrackDownloadManager";
    private static final String NOTIFICATION_CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Singleton instance
    private static TrackDownloadManager instance;
    
    // Dependencies
    private final Context context;
    private final MusicCache musicCache;
    private final SpotifyHelper spotifyHelper;
    private final UserPreferences preferences;
    private final ExecutorService executorService;
    
    // Track download state
    private final Map<String, DownloadStatus> downloadStatusMap;
    private final Set<DownloadListener> listeners;
    private boolean isDownloading = false;
    private int totalTracks = 0;
    private int completedTracks = 0;
    
    /**
     * Download status enum
     */
    public enum DownloadStatus {
        PENDING,
        DOWNLOADING,
        COMPLETED,
        FAILED
    }
    
    /**
     * Listener for download events
     */
    public interface DownloadListener {
        void onDownloadProgressChanged(int completed, int total);
        void onDownloadStatusChanged(String trackId, DownloadStatus status);
        void onAllDownloadsCompleted();
    }
    
    private TrackDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.musicCache = MusicCache.getInstance(context);
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.preferences = UserPreferences.getInstance(context);
        this.executorService = Executors.newFixedThreadPool(3); // Limit concurrent downloads
        this.downloadStatusMap = new HashMap<>();
        this.listeners = new HashSet<>();
        
        // Create notification channel for downloads
        createNotificationChannel();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized TrackDownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new TrackDownloadManager(context);
        }
        return instance;
    }
    
    /**
     * Add a download listener
     */
    public void addDownloadListener(DownloadListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove a download listener
     */
    public void removeDownloadListener(DownloadListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Create notification channel for downloads
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Downloads";
            String description = "Track download notifications";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = 
                    context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Download a single track for offline listening
     */
    public void downloadTrack(Track track) {
        if (track == null || track.getTrackId() == null) {
            Log.e(TAG, "Cannot download null track or track without ID");
            return;
        }
        
        // Check network connectivity
        if (preferences.getCacheOnlyOnWifi() && !isWifiConnected()) {
            Log.e(TAG, "Cannot download: WiFi only setting enabled but not connected to WiFi");
            showDownloadErrorNotification("Cannot download: WiFi required");
            return;
        }
        
        if (!isNetworkAvailable()) {
            Log.e(TAG, "Cannot download: No network connection");
            showDownloadErrorNotification("Cannot download: No network connection");
            return;
        }
        
        // Check if already cached
        if (musicCache.getCacheStatus(track.getTrackId()) == MusicCache.CacheStatus.CACHED) {
            Log.d(TAG, "Track already cached: " + track.getTitle());
            return;
        }
        
        // Add to download queue
        downloadStatusMap.put(track.getTrackId(), DownloadStatus.PENDING);
        notifyStatusChanged(track.getTrackId(), DownloadStatus.PENDING);
        
        // Start download
        downloadTrackInternal(track);
    }
    
    /**
     * Download a playlist for offline listening
     */
    public void downloadPlaylist(Playlist playlist) {
        if (playlist == null || playlist.getTracks() == null || playlist.getTracks().isEmpty()) {
            Log.e(TAG, "Cannot download empty playlist");
            return;
        }
        
        // Check network connectivity
        if (preferences.getCacheOnlyOnWifi() && !isWifiConnected()) {
            Log.e(TAG, "Cannot download playlist: WiFi only setting enabled but not connected to WiFi");
            showDownloadErrorNotification("Cannot download: WiFi required");
            return;
        }
        
        if (!isNetworkAvailable()) {
            Log.e(TAG, "Cannot download playlist: No network connection");
            showDownloadErrorNotification("Cannot download: No network connection");
            return;
        }
        
        // Reset counters for a new batch download
        isDownloading = true;
        totalTracks = 0;
        completedTracks = 0;
        
        // Count tracks that need downloading
        List<Track> tracksToDownload = new ArrayList<>();
        for (Track track : playlist.getTracks()) {
            if (track.getTrackId() != null && 
                musicCache.getCacheStatus(track.getTrackId()) != MusicCache.CacheStatus.CACHED) {
                tracksToDownload.add(track);
                totalTracks++;
                downloadStatusMap.put(track.getTrackId(), DownloadStatus.PENDING);
                notifyStatusChanged(track.getTrackId(), DownloadStatus.PENDING);
            }
        }
        
        // Update initial progress
        notifyProgressChanged();
        showDownloadNotification();
        
        // No tracks to download
        if (tracksToDownload.isEmpty()) {
            isDownloading = false;
            notifyAllDownloadsCompleted();
            return;
        }
        
        // Download tracks
        for (Track track : tracksToDownload) {
            downloadTrackInternal(track);
        }
    }
    
    /**
     * Internal method to download a track
     */
    private void downloadTrackInternal(Track track) {
        // Set status to downloading
        downloadStatusMap.put(track.getTrackId(), DownloadStatus.DOWNLOADING);
        notifyStatusChanged(track.getTrackId(), DownloadStatus.DOWNLOADING);
        
        // Update notification
        showDownloadNotification();
        
        // Get preview URL from track
        String previewUrl = track.getPreviewUrl();
        
        // If we have a direct URL, use it
        if (previewUrl != null && !previewUrl.isEmpty() && 
            !previewUrl.contains("soundcloud.com")) {
            Log.d(TAG, "Using direct preview URL for download: " + previewUrl);
            musicCache.cacheTrack(track.getTrackId(), previewUrl);
            musicCache.addCacheListener(new TrackCacheListener(track));
            return;
        }
        
        // If we have a Spotify ID, get the URL
        if (track.getSpotifyId() != null && !track.getSpotifyId().isEmpty()) {
            Log.d(TAG, "Getting Spotify preview URL for download");
            resolveSpotifyUrl(track);
            return;
        }
        
        // If we have a title/artist but no URLs, search Spotify
        if (track.getTitle() != null && !track.getTitle().isEmpty()) {
            Log.d(TAG, "Searching Spotify for track to download: " + track.getTitle());
            searchSpotify(track);
            return;
        }
        
        // If we can't get a URL, mark as failed
        Log.e(TAG, "Cannot download track: no preview URL available");
        downloadStatusMap.put(track.getTrackId(), DownloadStatus.FAILED);
        notifyStatusChanged(track.getTrackId(), DownloadStatus.FAILED);
        trackCompleted(false);
    }
    
    /**
     * Resolve a Spotify preview URL for download
     */
    private void resolveSpotifyUrl(Track track) {
        spotifyHelper.getTrackPreviewUrl(track.getSpotifyId(), new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    Log.d(TAG, "Got Spotify preview URL for download: " + previewUrl);
                    musicCache.cacheTrack(track.getTrackId(), previewUrl);
                    musicCache.addCacheListener(new TrackCacheListener(track));
                } else {
                    Log.e(TAG, "Failed to get Spotify preview URL");
                    downloadStatusMap.put(track.getTrackId(), DownloadStatus.FAILED);
                    notifyStatusChanged(track.getTrackId(), DownloadStatus.FAILED);
                    trackCompleted(false);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Spotify error: " + errorMessage);
                downloadStatusMap.put(track.getTrackId(), DownloadStatus.FAILED);
                notifyStatusChanged(track.getTrackId(), DownloadStatus.FAILED);
                trackCompleted(false);
            }
        });
    }
    
    /**
     * Search Spotify for a track to download
     */
    private void searchSpotify(Track track) {
        String query = track.getTitle();
        if (track.getArtist() != null && !track.getArtist().isEmpty()) {
            query += " " + track.getArtist();
        }
        
        spotifyHelper.searchTrack(query, "track", new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(response);
                    if (jsonResponse.has("tracks") && jsonResponse.getJSONObject("tracks").has("items")) {
                        org.json.JSONArray items = jsonResponse.getJSONObject("tracks").getJSONArray("items");
                        
                        if (items.length() > 0) {
                            org.json.JSONObject spotifyTrack = items.getJSONObject(0);
                            String spotifyId = spotifyTrack.getString("id");
                            
                            // Update track with Spotify ID
                            track.setSpotifyId(spotifyId);
                            
                            // Try to get preview URL
                            if (spotifyTrack.has("preview_url") && !spotifyTrack.isNull("preview_url")) {
                                String previewUrl = spotifyTrack.getString("preview_url");
                                track.setPreviewUrl(previewUrl);
                                
                                Log.d(TAG, "Found Spotify track and preview URL: " + previewUrl);
                                musicCache.cacheTrack(track.getTrackId(), previewUrl);
                                musicCache.addCacheListener(new TrackCacheListener(track));
                                return;
                            } else {
                                // Try to get a direct URL
                                resolveSpotifyUrl(track);
                                return;
                            }
                        }
                    }
                    
                    // If we reach here, no suitable track was found
                    Log.e(TAG, "No suitable Spotify track found for download");
                    downloadStatusMap.put(track.getTrackId(), DownloadStatus.FAILED);
                    notifyStatusChanged(track.getTrackId(), DownloadStatus.FAILED);
                    trackCompleted(false);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing Spotify response: " + e.getMessage());
                    downloadStatusMap.put(track.getTrackId(), DownloadStatus.FAILED);
                    notifyStatusChanged(track.getTrackId(), DownloadStatus.FAILED);
                    trackCompleted(false);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Spotify search error: " + errorMessage);
                downloadStatusMap.put(track.getTrackId(), DownloadStatus.FAILED);
                notifyStatusChanged(track.getTrackId(), DownloadStatus.FAILED);
                trackCompleted(false);
            }
        });
    }
    
    /**
     * Track cache listener for download progress
     */
    private class TrackCacheListener implements MusicCache.CacheListener {
        private final Track track;
        
        public TrackCacheListener(Track track) {
            this.track = track;
        }
        
        @Override
        public void onCacheStatusChanged(String trackId, MusicCache.CacheStatus status) {
            if (trackId.equals(track.getTrackId())) {
                if (status == MusicCache.CacheStatus.CACHED) {
                    downloadStatusMap.put(trackId, DownloadStatus.COMPLETED);
                    notifyStatusChanged(trackId, DownloadStatus.COMPLETED);
                    trackCompleted(true);
                    
                    // Remove listener
                    musicCache.removeCacheListener(this);
                }
            }
        }
        
        @Override
        public void onCacheProgress(String trackId, int progress) {
            // We don't need to do anything here
        }
        
        @Override
        public void onCacheError(String trackId, String error) {
            if (trackId.equals(track.getTrackId())) {
                Log.e(TAG, "Cache error for track " + trackId + ": " + error);
                downloadStatusMap.put(trackId, DownloadStatus.FAILED);
                notifyStatusChanged(trackId, DownloadStatus.FAILED);
                trackCompleted(false);
                
                // Remove listener
                musicCache.removeCacheListener(this);
            }
        }
    }
    
    /**
     * Track a completed download and update progress
     */
    private synchronized void trackCompleted(boolean success) {
        completedTracks++;
        notifyProgressChanged();
        showDownloadNotification();
        
        if (completedTracks >= totalTracks) {
            onAllDownloadsFinished();
        }
    }
    
    /**
     * Called when all downloads are finished
     */
    private void onAllDownloadsFinished() {
        isDownloading = false;
        
        // Show completion notification
        showCompletionNotification();
        
        // Notify listeners
        notifyAllDownloadsCompleted();
    }
    
    /**
     * Show download notification
     */
    private void showDownloadNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Downloading tracks")
                .setContentText("Downloaded " + completedTracks + " of " + totalTracks)
                .setProgress(totalTracks, completedTracks, false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "No permission to show notification", e);
        }
    }
    
    /**
     * Show completion notification
     */
    private void showCompletionNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_done)
                .setContentTitle("Downloads completed")
                .setContentText("Downloaded " + completedTracks + " tracks")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true);
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "No permission to show notification", e);
        }
    }
    
    /**
     * Show download error notification
     */
    private void showDownloadErrorNotification(String error) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_error)
                .setContentTitle("Download error")
                .setContentText(error)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true);
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "No permission to show notification", e);
        }
    }
    
    /**
     * Notify all listeners about status change
     */
    private void notifyStatusChanged(String trackId, DownloadStatus status) {
        for (DownloadListener listener : listeners) {
            listener.onDownloadStatusChanged(trackId, status);
        }
    }
    
    /**
     * Notify all listeners about progress change
     */
    private void notifyProgressChanged() {
        for (DownloadListener listener : listeners) {
            listener.onDownloadProgressChanged(completedTracks, totalTracks);
        }
    }
    
    /**
     * Notify all listeners that all downloads are completed
     */
    private void notifyAllDownloadsCompleted() {
        for (DownloadListener listener : listeners) {
            listener.onAllDownloadsCompleted();
        }
    }
    
    /**
     * Check if a track is currently being downloaded
     */
    public boolean isDownloading(String trackId) {
        return downloadStatusMap.containsKey(trackId) && 
               downloadStatusMap.get(trackId) == DownloadStatus.DOWNLOADING;
    }
    
    /**
     * Check if any downloads are in progress
     */
    public boolean isDownloading() {
        return isDownloading;
    }
    
    /**
     * Cancel all downloads
     */
    public void cancelAllDownloads() {
        isDownloading = false;
        
        // Nothing we can do about in-progress downloads
        // but we can prevent new ones from starting
        
        // Notify completion on UI thread
        new Handler(Looper.getMainLooper()).post(() -> {
            completedTracks = totalTracks;
            notifyProgressChanged();
            notifyAllDownloadsCompleted();
        });
    }
    
    /**
     * Check if any network is available
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = 
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
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
}