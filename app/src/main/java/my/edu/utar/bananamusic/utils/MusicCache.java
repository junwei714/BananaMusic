package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.Playlist;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Handles caching of music files for offline playback
 */
public class MusicCache {
    private static final String TAG = "MusicCache";
    private static final String CACHE_DIR = "music_cache";
    private static final long MAX_CACHE_SIZE = 500 * 1024 * 1024; // 500MB
    private static final int BUFFER_SIZE = 8192;
    
    private static MusicCache instance;
    private final Context context;
    private final OkHttpClient client;
    private final File cacheDir;
    private final UserPreferences preferences;
    private final Map<String, CacheStatus> cacheStatusMap;
    private final ExecutorService executorService;
    
    // Maximum number of concurrent background operations
    private static final int MAX_CONCURRENT_OPERATIONS = 2;
    private final Set<CacheListener> listeners;
    
    public enum CacheStatus {
        NOT_CACHED,
        CACHING,
        CACHED
    }
    
    // Interface for cache listeners
    public interface CacheListener {
        void onCacheStatusChanged(String trackId, CacheStatus status);
        void onCacheProgress(String trackId, int progress);
        void onCacheError(String trackId, String error);
    }
    
    private MusicCache(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        this.preferences = UserPreferences.getInstance(context);
        this.cacheStatusMap = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_OPERATIONS); // Limit concurrent downloads
        this.listeners = new HashSet<>();
        
        // Initialize cache status map
        initializeCacheStatus();
    }
    
    public static synchronized MusicCache getInstance(Context context) {
        if (instance == null) {
            instance = new MusicCache(context);
        }
        return instance;
    }
    
    /**
     * Initialize cache status map based on existing files
     */
    private void initializeCacheStatus() {
        if (!cacheDir.exists()) return;
        
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".mp3")) {
                String trackId = file.getName().replace(".mp3", "");
                cacheStatusMap.put(trackId, CacheStatus.CACHED);
            }
        }
        
        Log.d(TAG, "Initialized cache with " + cacheStatusMap.size() + " files");
    }
    
    /**
     * Add a cache listener
     */
    public void addCacheListener(CacheListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove a cache listener
     */
    public void removeCacheListener(CacheListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Check if a track is cached
     */
    public CacheStatus getCacheStatus(String trackId) {
        return cacheStatusMap.getOrDefault(trackId, CacheStatus.NOT_CACHED);
    }
    
    /**
     * Check if a track is cached and available for offline playback
     * @param trackId The track ID to check
     * @return True if the track is cached, false otherwise
     */
    public boolean isTrackCached(String trackId) {
        return getCacheStatus(trackId) == CacheStatus.CACHED && 
               new File(cacheDir, trackId + ".mp3").exists();
    }
    
    /**
     * Get cached file for a track
     * @return File if cached, null otherwise
     */
    public File getCachedFile(String trackId) {
        if (getCacheStatus(trackId) == CacheStatus.CACHED) {
            File file = new File(cacheDir, trackId + ".mp3");
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }
    
    /**
     * Cache a track from a URL
     */
    public void cacheTrack(String trackId, String url) {
        if (cacheStatusMap.getOrDefault(trackId, CacheStatus.NOT_CACHED) == CacheStatus.CACHING) {
            Log.d(TAG, "Track already being cached: " + trackId);
            return;
        }
        
        // Check if already cached
        File cachedFile = new File(cacheDir, trackId + ".mp3");
        if (cachedFile.exists()) {
            Log.d(TAG, "Track already cached: " + trackId);
            cacheStatusMap.put(trackId, CacheStatus.CACHED);
            notifyStatusChanged(trackId, CacheStatus.CACHED);
            return;
        }
        
        // Check available space
        if (getDirSize(cacheDir) > MAX_CACHE_SIZE) {
            Log.w(TAG, "Cache full, cleaning up...");
            cleanCache();
        }
        
        // Set status to caching
        cacheStatusMap.put(trackId, CacheStatus.CACHING);
        notifyStatusChanged(trackId, CacheStatus.CACHING);
        
        // Start caching in background
        executorService.execute(() -> {
            try {
                cacheTrackInternal(trackId, url);
            } catch (Exception e) {
                Log.e(TAG, "Error caching track: " + e.getMessage(), e);
                cacheStatusMap.put(trackId, CacheStatus.NOT_CACHED);
                notifyError(trackId, e.getMessage());
            }
        });
    }
    
    /**
     * Internal method to cache a track
     */
    private void cacheTrackInternal(String trackId, String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        
        File tempFile = new File(cacheDir, trackId + ".tmp");
        File finalFile = new File(cacheDir, trackId + ".mp3");
        
        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download track: " + response.code());
            }
            
            long contentLength = response.body().contentLength();
            InputStream inputStream = response.body().byteStream();
            
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalBytesRead = 0;
                int progress = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    
                    totalBytesRead += bytesRead;
                    if (contentLength > 0) {
                        int newProgress = (int) (totalBytesRead * 100 / contentLength);
                        if (newProgress > progress) {
                            progress = newProgress;
                            notifyProgress(trackId, progress);
                        }
                    }
                }
                
                // Flush and close
                outputStream.flush();
            }
            
            // Rename temp file to final file
            if (tempFile.renameTo(finalFile)) {
                cacheStatusMap.put(trackId, CacheStatus.CACHED);
                notifyStatusChanged(trackId, CacheStatus.CACHED);
                Log.d(TAG, "Successfully cached track: " + trackId);
                
                // Save to user preferences
                Set<String> cachedTracks = preferences.getCachedTracks();
                cachedTracks.add(trackId);
                preferences.setCachedTracks(cachedTracks);
            } else {
                throw new IOException("Failed to rename temp file");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error downloading track: " + e.getMessage());
            tempFile.delete();
            cacheStatusMap.put(trackId, CacheStatus.NOT_CACHED);
            notifyError(trackId, e.getMessage());
        }
    }
    
    /**
     * Delete a cached track
     */
    public void deleteCache(String trackId) {
        File file = new File(cacheDir, trackId + ".mp3");
        if (file.exists() && file.delete()) {
            Log.d(TAG, "Deleted cached track: " + trackId);
            cacheStatusMap.put(trackId, CacheStatus.NOT_CACHED);
            notifyStatusChanged(trackId, CacheStatus.NOT_CACHED);
            
            // Update user preferences
            Set<String> cachedTracks = preferences.getCachedTracks();
            cachedTracks.remove(trackId);
            preferences.setCachedTracks(cachedTracks);
        }
    }
    
    /**
     * Clean cache when it gets too large
     */
    private void cleanCache() {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        
        // Sort files by last modified (oldest first)
        java.util.Arrays.sort(files, (f1, f2) -> 
                Long.compare(f1.lastModified(), f2.lastModified()));
        
        // Delete oldest files until we're under 80% of max size
        long targetSize = (long)(MAX_CACHE_SIZE * 0.8);
        long currentSize = getDirSize(cacheDir);
        
        for (File file : files) {
            if (currentSize <= targetSize) break;
            
            if (file.isFile() && file.getName().endsWith(".mp3")) {
                String trackId = file.getName().replace(".mp3", "");
                long fileSize = file.length();
                
                if (file.delete()) {
                    currentSize -= fileSize;
                    cacheStatusMap.put(trackId, CacheStatus.NOT_CACHED);
                    notifyStatusChanged(trackId, CacheStatus.NOT_CACHED);
                    
                    // Update user preferences
                    Set<String> cachedTracks = preferences.getCachedTracks();
                    cachedTracks.remove(trackId);
                    preferences.setCachedTracks(cachedTracks);
                    
                    Log.d(TAG, "Cleaned cached track: " + trackId);
                }
            }
        }
    }
    
    /**
     * Get the size of a directory
     */
    private long getDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        
        if (files == null) return 0;
        
        for (File file : files) {
            if (file.isFile()) {
                size += file.length();
            }
        }
        
        return size;
    }
    
    /**
     * Clear the entire cache
     */
    public void clearCache() {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isFile() && file.delete()) {
                String trackId = file.getName().replace(".mp3", "");
                cacheStatusMap.put(trackId, CacheStatus.NOT_CACHED);
                notifyStatusChanged(trackId, CacheStatus.NOT_CACHED);
            }
        }
        
        // Clear cached tracks preference
        preferences.setCachedTracks(new HashSet<>());
        
        Log.d(TAG, "Cleared entire cache");
    }
    
    /**
     * Notify all listeners about status change
     */
    private void notifyStatusChanged(String trackId, CacheStatus status) {
        for (CacheListener listener : listeners) {
            listener.onCacheStatusChanged(trackId, status);
        }
    }
    
    /**
     * Notify all listeners about progress
     */
    private void notifyProgress(String trackId, int progress) {
        for (CacheListener listener : listeners) {
            listener.onCacheProgress(trackId, progress);
        }
    }
    
    /**
     * Notify all listeners about errors
     */
    private void notifyError(String trackId, String error) {
        for (CacheListener listener : listeners) {
            listener.onCacheError(trackId, error);
        }
    }
    
    /**
     * Cache a track in the background without blocking
     * @param track The track to cache
     * @param url The URL to download from
     */
    public void cacheTrackInBackground(my.edu.utar.bananamusic.models.Track track, String url) {
        if (track == null || url == null || url.isEmpty()) {
            return;
        }
        
        // Use the executor service instead of creating a new thread
        // This prevents thread proliferation and better manages system resources
        executorService.execute(() -> {
            try {
                // Only cache if:
                // 1. The track is not already cached
                // 2. The track is not already being cached
                // 3. The URL is valid
                // 4. We have caching enabled
                if (!isTrackCached(track.getId()) && 
                    getCacheStatus(track.getId()) != CacheStatus.CACHING &&
                    url.startsWith("http") &&
                    isCachingEnabled) {
                    
                    cacheTrack(track.getId(), url);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error caching track in background: " + e.getMessage(), e);
            }
        });
    }
    
    // Flag to control if caching is enabled
    private boolean isCachingEnabled = true;
    
    /**
     * Enable or disable track caching
     * @param enabled True to enable caching, false to disable
     */
    public void setCachingEnabled(boolean enabled) {
        this.isCachingEnabled = enabled;
    }
    
    /**
     * Check if caching is enabled
     * @return True if caching is enabled
     */
    public boolean isCachingEnabled() {
        return isCachingEnabled;
    }
    
    // The maximum size of the cache in bytes
    private long maxCacheSize = MAX_CACHE_SIZE;
    
    /**
     * Set the maximum cache size in bytes
     * @param maxSizeBytes Maximum size in bytes
     */
    public void setMaxCacheSize(long maxSizeBytes) {
        this.maxCacheSize = maxSizeBytes;
        // Check if we need to clean the cache after reducing the max size
        if (getDirSize(cacheDir) > maxCacheSize) {
            cleanCache();
        }
    }
    
    /**
     * Get the current cache size in bytes
     * @return Current cache size in bytes
     */
    public long getCurrentCacheSize() {
        return getDirSize(cacheDir);
    }
    
    /**
     * Get the maximum cache size in bytes
     * @return Maximum cache size in bytes
     */
    public long getMaxCacheSize() {
        return maxCacheSize;
    }
    
    /**
     * Returns the list of cached tracks
     * @return List of Track objects that are currently cached
     */
    public List<Track> getCachedTracks() {
        List<Track> cachedTracks = new ArrayList<>();
        
        if (!cacheDir.exists()) {
            return cachedTracks;
        }
        
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return cachedTracks;
        }
        
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".mp3")) {
                String trackId = file.getName().replace(".mp3", "");
                // Try to get track from cache
                Track track = getTrackFromId(trackId);
                if (track != null) {
                    cachedTracks.add(track);
                }
            }
        }
        
        return cachedTracks;
    }
    
    /**
     * Returns the list of cached playlists
     * @return List of Playlist objects that are currently cached
     */
    public List<Playlist> getCachedPlaylists() {
        // This method can be implemented in future to support playlist caching
        // For now, just return an empty list
        return new ArrayList<>();
    }
    
    /**
     * Cache multiple tracks at once
     * @param tracks List of tracks to cache
     */
    public void cacheTracks(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        
        for (Track track : tracks) {
            if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
                cacheTrackInBackground(track, track.getPreviewUrl());
            } else if (track.getStreamUrl() != null && !track.getStreamUrl().isEmpty()) {
                cacheTrackInBackground(track, track.getStreamUrl());
            }
        }
    }
    
    /**
     * Helper method to get a Track object from an ID
     * @param trackId ID of the track to retrieve
     * @return Track object or null if not found
     */
    private Track getTrackFromId(String trackId) {
        // This is a simplified implementation
        // In a real app, you would use a database or other storage
        Track track = new Track();
        track.setId(trackId);
        track.setTitle("Cached Track");
        return track;
    }
}