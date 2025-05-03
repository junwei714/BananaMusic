package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
 * Helper class for JioSaavn API integration
 * Uses community APIs to fetch song metadata and streaming URLs
 */
public class JioSaavnHelper {
    private static final String TAG = "JioSaavnHelper";
    
    // JioSaavn API endpoints (community API, not official)
    private static final String[] API_ENDPOINTS = {
            "https://saavn.me",
            "https://jiosaavn-api.vercel.app",
            "https://jiosaavn-api-mj3l.onrender.com",
            "https://api-saavn.vercel.app",
            "https://jiosaavn-api-production.up.railway.app"
    };
    
    private String currentEndpoint = API_ENDPOINTS[0];
    private int failCount = 0;
    private static final int MAX_FAIL_COUNT = 2;
    
    private static JioSaavnHelper instance;
    private final OkHttpClient httpClient;
    private final Context context;
    private final Gson gson;
    
    /**
     * Callback interface for API requests
     */
    public interface JioSaavnCallback {
        void onSuccess(String result);
        void onError(String errorMessage);
    }
    
    /**
     * Callback for search results
     */
    public interface SearchResultsCallback {
        void onSuccess(List<JioSaavnSong> songs);
        void onError(String errorMessage);
    }
    
    /**
     * Callback for song details
     */
    public interface SongDetailsCallback {
        void onSuccess(JioSaavnSong song);
        void onError(String errorMessage);
    }
    
    /**
     * JioSaavn song model
     */
    public static class JioSaavnSong {
        private String id;
        private String title;
        private String artist;
        private String album;
        private String imageUrl;
        private String audioUrl;
        private String releaseDate;
        private int duration; // in seconds
        private int year;
        private String language;
        
        public JioSaavnSong(String id, String title, String artist, String album, 
                            String imageUrl, String audioUrl, int duration, int year) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.imageUrl = imageUrl;
            this.audioUrl = audioUrl;
            this.duration = duration;
            this.year = year;
        }
        
        // Convert to Track model for app use
        public Track toTrack() {
            // Create formatted duration string
            int minutes = duration / 60;
            int seconds = duration % 60;
            String durationStr = String.format("%d:%02d", minutes, seconds);
            
            // Create a new Track object
            Track track = new Track(title, artist, album, durationStr, 0);
            track.setTrackId("jiosaavn:" + id);
            track.setAlbumArt(imageUrl);
            track.setPreviewUrl(audioUrl);
            track.setSource(Track.SOURCE_JIOSAAVN);
            
            // Set additional metadata if available
            track.setReleaseDate(releaseDate);
            
            return track;
        }
        
        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getAlbum() { return album; }
        public String getImageUrl() { return imageUrl; }
        public String getAudioUrl() { return audioUrl; }
        public int getDuration() { return duration; }
        public int getYear() { return year; }
        public String getLanguage() { return language; }
        public String getReleaseDate() { return releaseDate; }
        
        // Setters
        public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
        public void setLanguage(String language) { this.language = language; }
    }
    
    private JioSaavnHelper(Context context) {
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
    
    public static synchronized JioSaavnHelper getInstance(Context context) {
        if (instance == null) {
            instance = new JioSaavnHelper(context);
        }
        return instance;
    }
    
    /**
     * Search for songs on JioSaavn
     * @param query Search query
     * @param callback Callback with results
     */
    public void searchSongs(String query, final SearchResultsCallback callback) {
        if (query == null || query.isEmpty()) {
            callback.onError("Search query cannot be empty");
            return;
        }
        
        // Try to find a working endpoint if we've had too many failures
        if (failCount >= MAX_FAIL_COUNT) {
            currentEndpoint = getWorkingEndpoint();
            failCount = 0;
        }
        
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String searchUrl = currentEndpoint + "/search/songs?query=" + encodedQuery + "&limit=50";
            
            Request request = new Request.Builder()
                    .url(searchUrl)
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to search songs: " + e.getMessage());
                    
                    // Try another endpoint if this one failed
                    if (tryNextEndpoint()) {
                        // Retry with the next endpoint
                        searchSongs(query, callback);
                    } else {
                        // All endpoints failed
                        callback.onError("Network error: " + e.getMessage());
                    }
                }
                
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        
                        try {
                            List<JioSaavnSong> songs = parseSearchResults(json);
                            callback.onSuccess(songs);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing search results: " + e.getMessage());
                            callback.onError("Error parsing search results: " + e.getMessage());
                        }
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : 
                                           "Unknown error: " + response.code();
                        Log.e(TAG, "JioSaavn API error: " + errorBody);
                        
                        // Try another endpoint if this one failed
                        if (tryNextEndpoint()) {
                            // Retry with the next endpoint
                            searchSongs(query, callback);
                        } else {
                            // All endpoints failed
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
     * Search for songs specifically for a track (song + artist)
     * @param track Track to search for
     * @param callback Callback with results
     */
    public void searchSongsForTrack(Track track, final SearchResultsCallback callback) {
        if (track == null || track.getTitle() == null) {
            callback.onError("Invalid track information");
            return;
        }
        
        // Create a query combining song title and artist if available
        String query = track.getTitle();
        if (track.getArtist() != null && !track.getArtist().isEmpty()) {
            query += " " + track.getArtist();
        }
        
        searchSongs(query, callback);
    }
    
    /**
     * Get song details by ID
     * @param songId JioSaavn song ID
     * @param callback Callback with song details
     */
    public void getSongDetails(String songId, final SongDetailsCallback callback) {
        String url = currentEndpoint + "/songs?id=" + songId;
        
        Request request = new Request.Builder()
                .url(url)
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to get song details: " + e.getMessage());
                
                // Try another endpoint if this one failed
                if (tryNextEndpoint()) {
                    // Retry with the next endpoint
                    getSongDetails(songId, callback);
                } else {
                    // All endpoints failed
                    callback.onError("Network error: " + e.getMessage());
                }
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    
                    try {
                        JioSaavnSong song = parseSongDetails(json);
                        if (song != null) {
                            callback.onSuccess(song);
                        } else {
                            callback.onError("Could not parse song details");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing song details: " + e.getMessage());
                        callback.onError("Error parsing song details: " + e.getMessage());
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : 
                                       "Unknown error: " + response.code();
                    Log.e(TAG, "JioSaavn API error: " + errorBody);
                    
                    // Try another endpoint if this one failed
                    if (tryNextEndpoint()) {
                        // Retry with the next endpoint
                        getSongDetails(songId, callback);
                    } else {
                        // All endpoints failed
                        callback.onError("API error: " + errorBody);
                    }
                }
            }
        });
    }
    
    /**
     * Find the best audio match for a track on JioSaavn
     * @param track Track to find audio for
     * @param callback Callback with the track with updated audio URL
     */
    public void findAudioForTrack(Track track, final SongDetailsCallback callback) {
        searchSongsForTrack(track, new SearchResultsCallback() {
            @Override
            public void onSuccess(List<JioSaavnSong> songs) {
                if (songs != null && !songs.isEmpty()) {
                    // Find the best matching song
                    JioSaavnSong bestMatch = findBestMatch(songs, track);
                    
                    // Get full details for this song
                    getSongDetails(bestMatch.getId(), callback);
                } else {
                    callback.onError("No songs found for this track");
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                callback.onError("Search error: " + errorMessage);
            }
        });
    }
    
    /**
     * Find best matching song from search results
     * @param songs List of songs from search results
     * @param track Original track to match
     * @return Best matching song
     */
    private JioSaavnSong findBestMatch(List<JioSaavnSong> songs, Track track) {
        // Simple implementation - return first result
        // Could be improved with scoring based on title and artist similarity
        return songs.get(0);
    }
    
    /**
     * Parse search results JSON
     * @param json JSON response from JioSaavn API
     * @return List of JioSaavn songs
     */
    private List<JioSaavnSong> parseSearchResults(String json) {
        List<JioSaavnSong> songs = new ArrayList<>();
        
        try {
            JsonObject response = JsonParser.parseString(json).getAsJsonObject();
            
            // Different API endpoints might have different response formats
            if (response.has("results") || response.has("data")) {
                JsonArray results = response.has("results") ? 
                                    response.getAsJsonArray("results") : 
                                    response.getAsJsonArray("data");
                
                for (JsonElement result : results) {
                    JsonObject songJson = result.getAsJsonObject();
                    
                    String id = songJson.has("id") ? songJson.get("id").getAsString() : "";
                    
                    // Different APIs might use different field names
                    String title = getBestAvailableField(songJson, new String[]{"title", "name", "song"});
                    String artist = getBestAvailableField(songJson, new String[]{"artist", "singers", "primary_artists"});
                    String album = getBestAvailableField(songJson, new String[]{"album", "album_name"});
                    String imageUrl = getBestAvailableField(songJson, new String[]{"image", "cover", "image_url"});
                    
                    // Handle duration (might be in different formats)
                    int duration = 0;
                    if (songJson.has("duration") && !songJson.get("duration").isJsonNull()) {
                        try {
                            String durationStr = songJson.get("duration").getAsString();
                            duration = parseDuration(durationStr);
                        } catch (Exception e) {
                            try {
                                duration = songJson.get("duration").getAsInt();
                            } catch (Exception e2) {
                                Log.e(TAG, "Error parsing duration: " + e2.getMessage());
                            }
                        }
                    }
                    
                    // Handle year
                    int year = 0;
                    if (songJson.has("year") && !songJson.get("year").isJsonNull()) {
                        try {
                            year = songJson.get("year").getAsInt();
                        } catch (Exception e) {
                            try {
                                year = Integer.parseInt(songJson.get("year").getAsString());
                            } catch (Exception e2) {
                                Log.e(TAG, "Error parsing year: " + e2.getMessage());
                            }
                        }
                    }
                    
                    // Audio URL might not be available in search results, will be fetched with song details
                    String audioUrl = "";
                    if (songJson.has("downloadUrl") && !songJson.get("downloadUrl").isJsonNull()) {
                        try {
                            JsonArray downloadUrls = songJson.getAsJsonArray("downloadUrl");
                            if (downloadUrls.size() > 0) {
                                audioUrl = downloadUrls.get(downloadUrls.size() - 1).getAsJsonObject()
                                        .get("link").getAsString();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing download URL: " + e.getMessage());
                        }
                    }
                    
                    // Alternative field names for audio URL
                    if (audioUrl.isEmpty()) {
                        audioUrl = getBestAvailableField(songJson, new String[]{"media_url", "audio_url", "url"});
                    }
                    
                    JioSaavnSong song = new JioSaavnSong(id, title, artist, album, imageUrl, audioUrl, duration, year);
                    
                    // Add language if available
                    if (songJson.has("language") && !songJson.get("language").isJsonNull()) {
                        song.setLanguage(songJson.get("language").getAsString());
                    }
                    
                    // Add release date if available
                    if (songJson.has("release_date") && !songJson.get("release_date").isJsonNull()) {
                        song.setReleaseDate(songJson.get("release_date").getAsString());
                    }
                    
                    songs.add(song);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing search results: " + e.getMessage());
        }
        
        return songs;
    }
    
    /**
     * Parse song details JSON
     * @param json JSON response from JioSaavn API
     * @return JioSaavn song
     */
    private JioSaavnSong parseSongDetails(String json) {
        try {
            JsonObject response = JsonParser.parseString(json).getAsJsonObject();
            
            // Different API endpoints might have different response formats
            JsonObject songJson;
            if (response.has("data")) {
                JsonArray data = response.getAsJsonArray("data");
                if (data.size() > 0) {
                    songJson = data.get(0).getAsJsonObject();
                } else {
                    return null;
                }
            } else {
                songJson = response;
            }
            
            String id = songJson.has("id") ? songJson.get("id").getAsString() : "";
            
            // Different APIs might use different field names
            String title = getBestAvailableField(songJson, new String[]{"title", "name", "song"});
            String artist = getBestAvailableField(songJson, new String[]{"artist", "singers", "primary_artists"});
            String album = getBestAvailableField(songJson, new String[]{"album", "album_name"});
            String imageUrl = getBestAvailableField(songJson, new String[]{"image", "cover", "image_url"});
            
            // Handle duration (might be in different formats)
            int duration = 0;
            if (songJson.has("duration") && !songJson.get("duration").isJsonNull()) {
                try {
                    String durationStr = songJson.get("duration").getAsString();
                    duration = parseDuration(durationStr);
                } catch (Exception e) {
                    try {
                        duration = songJson.get("duration").getAsInt();
                    } catch (Exception e2) {
                        Log.e(TAG, "Error parsing duration: " + e2.getMessage());
                    }
                }
            }
            
            // Handle year
            int year = 0;
            if (songJson.has("year") && !songJson.get("year").isJsonNull()) {
                try {
                    year = songJson.get("year").getAsInt();
                } catch (Exception e) {
                    try {
                        year = Integer.parseInt(songJson.get("year").getAsString());
                    } catch (Exception e2) {
                        Log.e(TAG, "Error parsing year: " + e2.getMessage());
                    }
                }
            }
            
            // Audio URL is the most important part
            String audioUrl = "";
            
            // Try different possible field names for audio URL
            if (songJson.has("media_url") && !songJson.get("media_url").isJsonNull()) {
                audioUrl = songJson.get("media_url").getAsString();
            } else if (songJson.has("downloadUrl") && !songJson.get("downloadUrl").isJsonNull()) {
                try {
                    JsonArray downloadUrls = songJson.getAsJsonArray("downloadUrl");
                    if (downloadUrls.size() > 0) {
                        // Get the highest quality download URL (usually the last one)
                        audioUrl = downloadUrls.get(downloadUrls.size() - 1).getAsJsonObject()
                                .get("link").getAsString();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing download URL: " + e.getMessage());
                }
            }
            
            // Try other field names if still not found
            if (audioUrl.isEmpty()) {
                audioUrl = getBestAvailableField(songJson, new String[]{"audio_url", "url", "stream_url"});
            }
            
            JioSaavnSong song = new JioSaavnSong(id, title, artist, album, imageUrl, audioUrl, duration, year);
            
            // Add language if available
            if (songJson.has("language") && !songJson.get("language").isJsonNull()) {
                song.setLanguage(songJson.get("language").getAsString());
            }
            
            // Add release date if available
            if (songJson.has("release_date") && !songJson.get("release_date").isJsonNull()) {
                song.setReleaseDate(songJson.get("release_date").getAsString());
            } else if (year > 0) {
                song.setReleaseDate(String.valueOf(year));
            }
            
            return song;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing song details: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the best available field from JSON object
     * @param json JSON object
     * @param fieldNames Array of possible field names
     * @return Field value or empty string if not found
     */
    private String getBestAvailableField(JsonObject json, String[] fieldNames) {
        for (String field : fieldNames) {
            if (json.has(field) && !json.get(field).isJsonNull()) {
                return json.get(field).getAsString();
            }
        }
        return "";
    }
    
    /**
     * Parse duration string to seconds
     * @param duration Duration string (e.g. "3:45" or "180")
     * @return Duration in seconds
     */
    private int parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }
        
        // Check if it's already in seconds
        if (!duration.contains(":")) {
            try {
                return Integer.parseInt(duration);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        
        // Parse MM:SS format
        String[] parts = duration.split(":");
        try {
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return minutes * 60 + seconds;
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing duration parts: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Get the current API endpoint URL
     * @return API endpoint URL
     */
    private String getCurrentEndpoint() {
        return currentEndpoint;
    }
    
    /**
     * Try the next API endpoint if the current one fails
     * @return true if there's another endpoint to try, false otherwise
     */
    private boolean tryNextEndpoint() {
        failCount++;
        
        if (failCount < MAX_FAIL_COUNT) {
            Log.d(TAG, "Switching to JioSaavn API endpoint: " + getCurrentEndpoint());
            return true;
        } else {
            // Reset to the first endpoint for next time
            failCount = 0;
            return false;
        }
    }
    
    // Add a method to find a working endpoint
    private String getWorkingEndpoint() {
        for (String endpoint : API_ENDPOINTS) {
            try {
                String testUrl = endpoint + "/search/songs?query=test&limit=1";
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(testUrl).openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestMethod("HEAD");
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 400) {
                    Log.d(TAG, "Found working JioSaavn endpoint: " + endpoint);
                    return endpoint;
                }
            } catch (Exception e) {
                Log.d(TAG, "JioSaavn endpoint " + endpoint + " failed: " + e.getMessage());
            }
        }
        return API_ENDPOINTS[0]; // Return default as fallback
    }
} 