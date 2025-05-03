package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import my.edu.utar.bananamusic.models.Track;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Helper class for fetching audio streams from Piped.video (a YouTube frontend)
 * This allows ad-free streaming of audio from YouTube videos without requiring YouTube Premium
 */
public class PipedHelper {
    private static final String TAG = "PipedHelper";
    
    // Piped instances - if one doesn't work, try another
    private static final String[] PIPED_INSTANCES = {
            "https://pipedapi.syncpundit.io",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.moomoo.me",
            "https://api.piped.projectsegfau.lt",
            "https://api-piped.ftp.sh",
            "https://piped-api.garudalinux.org",
            "https://pipedapi.in.projectsegfau.lt",
            "https://api.piped.privacydev.net"
    };
    
    // Default instance to use first (chosen from more reliable ones)
    private static final String DEFAULT_INSTANCE = PIPED_INSTANCES[0];
    
    private static PipedHelper instance;
    private final OkHttpClient httpClient;
    private final Context context;
    private final Gson gson;
    
    // Current instance index (for fallback)
    private int currentInstanceIndex = 0;
    
    // Counter for instance failures
    private int instanceFailCount = 0;
    
    /**
     * Callback interface for Piped API requests
     */
    public interface PipedCallback {
        void onSuccess(String result);
        void onError(String errorMessage);
    }
    
    /**
     * Callback for search results
     */
    public interface SearchResultsCallback {
        void onSuccess(List<YouTubeVideo> videos);
        void onError(String errorMessage);
    }
    
    /**
     * Callback for audio streams
     */
    public interface AudioStreamCallback {
        void onSuccess(String audioUrl, String videoId);
        void onError(String errorMessage);
    }
    
    /**
     * YouTube video model for search results
     */
    public static class YouTubeVideo {
        private String videoId;
        private String title;
        private String channelName;
        private String thumbnailUrl;
        private long duration; // in seconds
        private long views;
        
        public YouTubeVideo(String videoId, String title, String channelName, 
                            String thumbnailUrl, long duration, long views) {
            this.videoId = videoId;
            this.title = title;
            this.channelName = channelName;
            this.thumbnailUrl = thumbnailUrl;
            this.duration = duration;
            this.views = views;
        }
        
        public String getVideoId() {
            return videoId;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getChannelName() {
            return channelName;
        }
        
        public String getThumbnailUrl() {
            return thumbnailUrl;
        }
        
        public long getDuration() {
            return duration;
        }
        
        public String getFormattedDuration() {
            long minutes = duration / 60;
            long seconds = duration % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
        
        public long getViews() {
            return views;
        }
    }
    
    private PipedHelper(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        
        // Configure OkHttpClient with timeouts
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }
    
    public static synchronized PipedHelper getInstance(Context context) {
        if (instance == null) {
            instance = new PipedHelper(context);
        }
        return instance;
    }
    
    /**
     * Search for videos on YouTube via Piped API
     * @param query Search query
     * @param callback Callback with results
     */
    public void searchVideos(String query, final SearchResultsCallback callback) {
        if (query == null || query.isEmpty()) {
            callback.onError("Search query cannot be empty");
            return;
        }

        // Get a working instance first
        String apiBase = getWorkingInstance();
        
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            // Add "music" to query and filter to improve results relevance
            String enhancedQuery = encodedQuery + "+music";
            String searchUrl = apiBase + "/search?q=" + enhancedQuery + "&filter=music";
            
            Log.d(TAG, "Searching Piped with URL: " + searchUrl);
            
            // Add User-Agent to appear more like a regular browser
            String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";
            
            Request request = new Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", userAgent)
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to search videos: " + e.getMessage());
                    instanceFailCount++;
                    
                    // Try another instance if this one failed
                    if (tryNextInstance()) {
                        // Retry with the next instance
                        searchVideos(query, callback);
                    } else {
                        // All instances failed
                        callback.onError("Network error: " + e.getMessage());
                    }
                }
                
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        
                        // Check if we got a Cloudflare block page
                        if (json.contains("Cloudflare") || json.contains("challenge") || 
                            json.contains("Attention Required") || json.contains("Sorry, you have been blocked")) {
                            Log.e(TAG, "Cloudflare block detected, trying next instance");
                            instanceFailCount++;
                            if (tryNextInstance()) {
                                searchVideos(query, callback);
                            } else {
                                callback.onError("All Piped instances are blocked by Cloudflare");
                            }
                            return;
                        }
                        
                        try {
                            List<YouTubeVideo> videos = parseSearchResults(json);
                            
                            // Log success if we found videos
                            if (!videos.isEmpty()) {
                                Log.d(TAG, "Piped search successful, found " + videos.size() + " videos");
                                instanceFailCount = 0; // Reset failure counter on success
                            } else {
                                Log.d(TAG, "Piped search returned no videos");
                            }
                            
                            callback.onSuccess(videos);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing search results: " + e.getMessage());
                            instanceFailCount++;
                            callback.onError("Error parsing search results: " + e.getMessage());
                        }
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : 
                                          "Unknown error: " + response.code();
                        
                        // Check for Cloudflare block 
                        if (response.code() == 403 || response.code() == 503 || 
                            errorBody.contains("Cloudflare") || errorBody.contains("challenge")) {
                            Log.e(TAG, "Cloudflare block detected (code: " + response.code() + "), trying next instance");
                            instanceFailCount++;
                            if (tryNextInstance()) {
                                searchVideos(query, callback);
                            } else {
                                callback.onError("All Piped instances are blocked by Cloudflare");
                            }
                            return;
                        }
                        
                        Log.e(TAG, "Piped API error: " + errorBody);
                        instanceFailCount++;
                        
                        // Try another instance if this one failed
                        if (tryNextInstance()) {
                            // Retry with the next instance
                            searchVideos(query, callback);
                        } else {
                            // All instances failed
                            callback.onError("API error: " + errorBody);
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error encoding query: " + e.getMessage());
            callback.onError("Error encoding query: " + e.getMessage());
        }
    }
    
    /**
     * Search for videos specifically for a track (song + artist)
     * @param track Track to search for
     * @param callback Callback with results
     */
    public void searchVideoForTrack(Track track, final SearchResultsCallback callback) {
        if (track == null || track.getTitle() == null || track.getArtist() == null) {
            callback.onError("Invalid track information");
            return;
        }
        
        // Create a query combining song title and artist
        String query = track.getTitle() + " " + track.getArtist();
        searchVideos(query, callback);
    }
    
    /**
     * Get streams for a video (including audio-only streams)
     * @param videoId YouTube video ID
     * @param callback Callback with audio stream URL
     */
    public void getAudioStreamUrl(String videoId, final AudioStreamCallback callback) {
        String url = getCurrentInstance() + "/streams/" + videoId;
        
        // Add User-Agent to avoid Cloudflare blocks
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";
        
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to get streams: " + e.getMessage());
                instanceFailCount++;
                
                // Try another instance if this one failed
                if (tryNextInstance()) {
                    // Retry with the next instance
                    getAudioStreamUrl(videoId, callback);
                } else {
                    // All instances failed
                    callback.onError("Network error: " + e.getMessage());
                }
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    
                    // Check if we got a Cloudflare block page
                    if (json.contains("Cloudflare") || json.contains("challenge") || 
                        json.contains("Attention Required") || json.contains("Sorry, you have been blocked")) {
                        Log.e(TAG, "Cloudflare block detected, trying next instance");
                        instanceFailCount++;
                        if (tryNextInstance()) {
                            getAudioStreamUrl(videoId, callback);
                        } else {
                            callback.onError("All Piped instances are blocked by Cloudflare");
                        }
                        return;
                    }
                    
                    try {
                        String audioUrl = parseAudioStreamUrl(json);
                        if (audioUrl != null) {
                            callback.onSuccess(audioUrl, videoId);
                        } else {
                            callback.onError("No audio stream found");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing streams: " + e.getMessage());
                        callback.onError("Error parsing streams: " + e.getMessage());
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : 
                                      "Unknown error: " + response.code();
                    
                    // Check for Cloudflare block 
                    if (response.code() == 403 || response.code() == 503 || 
                        errorBody.contains("Cloudflare") || errorBody.contains("challenge")) {
                        Log.e(TAG, "Cloudflare block detected (code: " + response.code() + "), trying next instance");
                        instanceFailCount++;
                        if (tryNextInstance()) {
                            getAudioStreamUrl(videoId, callback);
                        } else {
                            callback.onError("All Piped instances are blocked by Cloudflare");
                        }
                        return;
                    }
                    
                    Log.e(TAG, "Piped API error: " + errorBody);
                    instanceFailCount++;
                    
                    // Try another instance if this one failed
                    if (tryNextInstance()) {
                        // Retry with the next instance
                        getAudioStreamUrl(videoId, callback);
                    } else {
                        // All instances failed
                        callback.onError("API error: " + errorBody);
                    }
                }
            }
        });
    }
    
    /**
     * Find the best audio stream URL for a track's title and artist
     * @param track Track to find audio for
     * @param callback Callback with audio URL and video ID
     */
    public void findAudioForTrack(Track track, final AudioStreamCallback callback) {
        searchVideoForTrack(track, new SearchResultsCallback() {
            @Override
            public void onSuccess(List<YouTubeVideo> videos) {
                if (videos != null && !videos.isEmpty()) {
                    // Find the best matching video (first one usually works well)
                    YouTubeVideo bestMatch = videos.get(0);
                    
                    // Get audio stream for this video
                    getAudioStreamUrl(bestMatch.getVideoId(), callback);
                } else {
                    callback.onError("No videos found for this track");
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                callback.onError("Search error: " + errorMessage);
            }
        });
    }
    
    /**
     * Parse search results JSON
     * @param json JSON response from Piped API
     * @return List of YouTube videos
     */
    private List<YouTubeVideo> parseSearchResults(String json) {
        List<YouTubeVideo> videos = new ArrayList<>();
        
        try {
            JsonObject response = gson.fromJson(json, JsonObject.class);
            
            if (response.has("items")) {
                JsonArray items = response.getAsJsonArray("items");
                
                for (JsonElement item : items) {
                    JsonObject videoJson = item.getAsJsonObject();
                    
                    if (videoJson.has("type") && videoJson.get("type").getAsString().equals("stream")) {
                        String videoId = videoJson.get("url").getAsString().replace("/watch?v=", "");
                        String title = videoJson.has("title") ? videoJson.get("title").getAsString() : "Unknown";
                        String channelName = "Unknown";
                        
                        if (videoJson.has("uploaderName")) {
                            channelName = videoJson.get("uploaderName").getAsString();
                        }
                        
                        String thumbnailUrl = "";
                        if (videoJson.has("thumbnail")) {
                            thumbnailUrl = videoJson.get("thumbnail").getAsString();
                        }
                        
                        // Duration might be in different formats
                        long duration = 0;
                        if (videoJson.has("duration")) {
                            try {
                                duration = parseDuration(videoJson.get("duration").getAsString());
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing duration: " + e.getMessage());
                            }
                        }
                        
                        long views = 0;
                        if (videoJson.has("views")) {
                            try {
                                views = videoJson.get("views").getAsLong();
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing views: " + e.getMessage());
                            }
                        }
                        
                        YouTubeVideo video = new YouTubeVideo(
                                videoId, title, channelName, thumbnailUrl, duration, views);
                        videos.add(video);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing search results: " + e.getMessage());
        }
        
        return videos;
    }
    
    /**
     * Parse stream info JSON to get audio-only stream URL
     * @param json JSON response from Piped API
     * @return Audio stream URL
     */
    private String parseAudioStreamUrl(String json) {
        try {
            JsonObject response = gson.fromJson(json, JsonObject.class);
            
            // First try to get audio-only streams
            if (response.has("audioStreams")) {
                JsonArray audioStreams = response.getAsJsonArray("audioStreams");
                
                if (audioStreams.size() > 0) {
                    // Get the first (usually best quality) audio stream
                    JsonObject audioStream = audioStreams.get(0).getAsJsonObject();
                    return audioStream.get("url").getAsString();
                }
            }
            
            // If no audio-only streams, try video streams
            if (response.has("videoStreams")) {
                JsonArray videoStreams = response.getAsJsonArray("videoStreams");
                
                for (JsonElement streamElement : videoStreams) {
                    JsonObject stream = streamElement.getAsJsonObject();
                    
                    // Get the lowest quality video stream (to save bandwidth)
                    if (stream.has("quality") && stream.get("quality").getAsString().equals("360p")) {
                        return stream.get("url").getAsString();
                    }
                }
                
                // If no 360p stream, just use the first one
                if (videoStreams.size() > 0) {
                    JsonObject stream = videoStreams.get(0).getAsJsonObject();
                    return stream.get("url").getAsString();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing stream info: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Parse duration string (e.g. "12:34" or "1:23:45") to seconds
     * @param duration Duration string
     * @return Duration in seconds
     */
    private long parseDuration(String duration) {
        String[] parts = duration.split(":");
        long seconds = 0;
        
        try {
            if (parts.length == 3) {
                // Hours:Minutes:Seconds
                seconds = Long.parseLong(parts[0]) * 3600 +
                          Long.parseLong(parts[1]) * 60 +
                          Long.parseLong(parts[2]);
            } else if (parts.length == 2) {
                // Minutes:Seconds
                seconds = Long.parseLong(parts[0]) * 60 +
                          Long.parseLong(parts[1]);
            } else if (parts.length == 1) {
                // Just seconds
                seconds = Long.parseLong(parts[0]);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing duration parts: " + e.getMessage());
        }
        
        return seconds;
    }
    
    /**
     * Get the current Piped instance URL
     * @return Piped instance URL
     */
    private String getCurrentInstance() {
        return PIPED_INSTANCES[currentInstanceIndex];
    }
    
    /**
     * Try the next Piped instance if the current one fails
     * @return true if there's another instance to try, false otherwise
     */
    private boolean tryNextInstance() {
        currentInstanceIndex++;
        
        if (currentInstanceIndex < PIPED_INSTANCES.length) {
            Log.d(TAG, "Switching to Piped instance: " + getCurrentInstance());
            return true;
        } else {
            // Reset to the first instance for next time
            currentInstanceIndex = 0;
            return false;
        }
    }
    
    // Add a method to get a working instance with better error handling
    public String getWorkingInstance() {
        // Set User-Agent to appear more like a regular browser
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";
        
        // Try to use the current instance if it hasn't failed more than the threshold
        if (instanceFailCount <= 2 && currentInstanceIndex < PIPED_INSTANCES.length) {
            return PIPED_INSTANCES[currentInstanceIndex];
        }
        
        // Start from a random instance to balance load
        int startIndex = (int) (Math.random() * PIPED_INSTANCES.length);
        
        // Try each instance until one works
        for (int i = 0; i < PIPED_INSTANCES.length; i++) {
            int idx = (startIndex + i) % PIPED_INSTANCES.length;
            String instance = PIPED_INSTANCES[idx];
            
            try {
                // Test with a small request first
                String testUrl = instance + "/trending?region=US";
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(testUrl).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("HEAD");
                connection.setRequestProperty("User-Agent", userAgent);
                
                int responseCode = connection.getResponseCode();
                
                // Check for successful response (200-299)
                if (responseCode >= 200 && responseCode < 300) {
                    Log.d(TAG, "Found working Piped instance: " + instance);
                    currentInstanceIndex = idx;
                    instanceFailCount = 0;
                    return instance;
                }
                
                // Detect Cloudflare or other blocks
                if (responseCode == 403 || responseCode == 503) {
                    Log.d(TAG, "Piped instance " + instance + " is blocked by Cloudflare or similar (code: " + responseCode + ")");
                }
            } catch (Exception e) {
                Log.d(TAG, "Piped instance " + instance + " failed: " + e.getMessage());
            }
        }
        
        // If all instances fail, still return a value but it might not work
        Log.w(TAG, "All Piped instances failed, using first instance as fallback");
        currentInstanceIndex = 0;
        return PIPED_INSTANCES[0];
    }
} 