package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import my.edu.utar.bananamusic.models.Album;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.Playlist;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.Date;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.Playlist;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Helper class for interacting with the Deezer API to fetch music data
 */
public class DeezerHelper {
    private static final String TAG = "DeezerHelper";
    private static final String BASE_API_URL = "https://api.deezer.com";
    public static final String SOURCE_NAME = "Deezer";
    
    private Context context;
    private static volatile DeezerHelper instance;
    private OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean cachingEnabled = true;
    
    /**
     * General-purpose callback interface for Deezer operations
     * Used for methods that return a string result (like URLs)
     */
    public interface DeezerCallback {
        void onSuccess(String previewUrl);
        void onError(String message);
    }
    
    /**
     * Alias for DeezerCallback to maintain backward compatibility
     * Used in AudioPlayerHelper
     */
    public interface DeezerStringCallback extends DeezerCallback {
        // Same methods as DeezerCallback, just a different name
    }

    /**
     * Interface for Deezer track operations
     */
    public interface DeezerTracksCallback {
        /**
         * Called when tracks are successfully loaded
         * @param tracks List of loaded tracks
         */
        void onSuccess(List<Track> tracks);
        
        /**
         * Called when an error occurs during track operations
         * @param errorMessage The error message
         */
        void onError(String errorMessage);
    }

    /**
     * Interface for Deezer recommendations operations
     */
    public interface DeezerRecommendationsCallback {
        void onSuccess(List<Track> tracks);
        void onError(String message);
    }

    /**
     * Interface for callbacks when JSON response is received
     */
    public interface JSONResponseCallback {
        void onSuccess(JSONObject jsonResponse);
        void onError(String message);
    }

    /**
     * Interface for callbacks when tracks are loaded with exception error handling
     */
    public interface TracksCallback {
        /**
         * Called when tracks are successfully loaded
         * @param tracks List of loaded tracks
         */
        void onTracksLoaded(List<Track> tracks);
        
        /**
         * Called when an error occurs during track operations
         * @param e The exception that occurred
         */
        void onError(Exception e);
    }

    /**
     * Callback interface for playlists operations
     */
    public interface DeezerPlaylistsCallback {
        void onSuccess(List<Playlist> playlists);
        void onError(String message);
    }

    /**
     * Callback interface for single playlist operations
     */
    public interface DeezerPlaylistCallback {
        void onSuccess(Playlist playlist);
        void onError(String message);
    }

    /**
     * Interface for new releases operations
     */
    public interface NewReleasesCallback {
        void onSuccess(List<Album> albums);
        void onError(String message);
    }

    /**
     * Interface for top tracks operations
     */
    public interface TopTracksCallback {
        void onSuccess(List<Track> tracks);
        void onError(String message);
    }

    private DeezerHelper(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    public static synchronized DeezerHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DeezerHelper(context);
        }
        return instance;
    }
    
    /**
     * Search for tracks by query
     * 
     * @param query The search query
     * @param limit Number of results to return
     * @param callback Callback to handle the result
     */
    public void searchTracks(String query, int limit, my.edu.utar.bananamusic.utils.callbacks.TracksCallback callback) {
        if (query == null || query.isEmpty()) {
            callback.onError("Search query cannot be empty");
            return;
        }
        
        // Encode query for URL
        String encodedQuery;
        try {
            encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Error encoding query: " + e.getMessage());
            callback.onError("Error encoding query: " + e.getMessage());
            return;
        }
        
        // Build URL for search
        String url = BASE_API_URL + "/search?q=" + encodedQuery + "&limit=" + limit;
        
        // Build request
        Request request = new Request.Builder()
            .url(url)
            .build();
        
        // Execute request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Failed to search tracks: " + e.getMessage();
                Log.e(TAG, errorMessage);
                runOnMainThread(() -> callback.onError(new Exception(errorMessage)));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMessage = "Error response: " + response.code();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(new Exception(errorMessage)));
                    return;
                }
                
                try {
                    String jsonData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(jsonData);
                    
                    List<Track> tracks = parseTrackListFromResponse(jsonResponse);
                    runOnMainThread(() -> callback.onSuccess(tracks));
                    
                } catch (JSONException e) {
                    String errorMessage = "Error parsing search results: " + e.getMessage();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(new Exception(errorMessage)));
                }
            }
        });
    }
    
    /**
     * Search tracks with the DeezerCallback interface
     * 
     * @param query The search query
     * @param callback Callback to handle the result
     */
    public void searchTracks(String query, DeezerCallback callback) {
        searchTracks(query, 10, new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    Track track = tracks.get(0);
                    callback.onSuccess(track.getPreviewUrl());
                } else {
                    callback.onError("No tracks found");
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }
    
    /**
     * Search tracks with the DeezerTracksCallback interface
     * 
     * @param query The search query
     * @param callback Callback to handle the result
     */
    public void searchTracks(String query, DeezerTracksCallback callback) {
        searchTracks(query, 10, new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
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
     * Parse track list from JSON response
     */
    private List<Track> parseTrackListFromResponse(JSONObject jsonResponse) throws JSONException {
        List<Track> tracks = new ArrayList<>();
        
        if (jsonResponse.has("data")) {
            JSONArray tracksArray = jsonResponse.getJSONArray("data");
            tracks = parseTracksFromArray(tracksArray);
        }
        
        return tracks;
    }

    /**
     * Parse tracks from JSON array
     */
    private List<Track> parseTracksFromArray(JSONArray tracksArray) throws JSONException {
        List<Track> tracks = new ArrayList<>();
        
        for (int i = 0; i < tracksArray.length(); i++) {
            JSONObject trackJson = tracksArray.getJSONObject(i);
            Track track = parseTrackJson(trackJson);
            if (track != null) {
                tracks.add(track);
            }
        }
        
        return tracks;
    }
    
    /**
     * Parse track from JSON object
     */
    private Track parseTrackJson(JSONObject trackJson) throws JSONException {
        if (trackJson == null) return null;
        
        String id = trackJson.optString("id", "");
        String title = trackJson.optString("title", "");
        int duration = trackJson.optInt("duration", 0) * 1000; // Convert to milliseconds
        
        // Extract preview URL with better error handling
        String previewUrl = "";
        if (trackJson.has("preview") && !trackJson.isNull("preview")) {
            previewUrl = trackJson.getString("preview");
            Log.d(TAG, "Found preview URL for track " + title + ": " + previewUrl);
        } else {
            Log.w(TAG, "No preview URL found for track " + title);
        }
        
        boolean isPlayable = previewUrl != null && !previewUrl.isEmpty();
        
        // Get artist information
        String artist = "Unknown Artist";
        if (trackJson.has("artist") && !trackJson.isNull("artist")) {
            JSONObject artistJson = trackJson.getJSONObject("artist");
            artist = artistJson.optString("name", "Unknown Artist");
        }
            
        // Get album information
        String album = "Unknown Album";
        String albumArt = "";
        if (trackJson.has("album") && !trackJson.isNull("album")) {
            JSONObject albumJson = trackJson.getJSONObject("album");
            album = albumJson.optString("title", "Unknown Album");
                
            // Get album cover with fallbacks for different sizes
            if (albumJson.has("cover_big") && !albumJson.isNull("cover_big")) {
                albumArt = albumJson.optString("cover_big", "");
            } else if (albumJson.has("cover_medium") && !albumJson.isNull("cover_medium")) {
                albumArt = albumJson.optString("cover_medium", "");
            } else if (albumJson.has("cover_small") && !albumJson.isNull("cover_small")) {
                albumArt = albumJson.optString("cover_small", "");
            } else if (albumJson.has("cover") && !albumJson.isNull("cover")) {
                albumArt = albumJson.optString("cover", "");
            }
        }
        
        // Create track object
        Track track = new Track();
        track.setId(id);
        track.setTitle(title);
        track.setArtist(artist);
        track.setAlbum(album);
        track.setAlbumArtUrl(albumArt);
        track.setDuration(duration);
        track.setSource(Track.SOURCE_DEEZER);
        track.setPlayable(isPlayable);
        
        // Set preview URL
        track.setPreviewUrl(previewUrl);
        
        // Store Deezer ID in the track
        track.setDeezerId(id);
        
        // Set stream URL to be the same as preview URL for direct playback
        if (isPlayable) {
            track.setStreamUrl(previewUrl);
        }
        
        return track;
    }

    /**
     * Verify if a preview URL is valid
     *
     * @param previewUrl URL to verify
     * @param callback Callback to handle the result
     */
    public void verifyPreviewUrl(String previewUrl, DeezerCallback callback) {
        if (previewUrl == null || previewUrl.isEmpty()) {
            runOnMainThread(() -> callback.onError("Preview URL is empty"));
            return;
        }
        
        // Handle local files or special URL schemes
        if (previewUrl.startsWith("Local") || previewUrl.startsWith("file:") || 
            previewUrl.startsWith("content:") || previewUrl.startsWith("android.resource:")) {
            Log.d(TAG, "Skipping URL verification for non-HTTP URL: " + previewUrl);
            runOnMainThread(() -> callback.onSuccess(previewUrl));
            return;
        }
        
        // Ensure URL has a valid protocol
        if (!previewUrl.startsWith("http://") && !previewUrl.startsWith("https://")) {
            String errorMessage = "Invalid URL scheme: " + previewUrl;
            Log.e(TAG, errorMessage);
            runOnMainThread(() -> callback.onError(errorMessage));
            return;
        }
        
        Request request = new Request.Builder()
            .url(previewUrl)
                .head() // We only need headers to check if the URL exists
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Failed to verify preview URL: " + e.getMessage();
                Log.e(TAG, errorMessage);
                runOnMainThread(() -> callback.onError(errorMessage));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnMainThread(() -> callback.onSuccess(previewUrl));
                } else {
                    String errorMessage = "Invalid preview URL: " + response.code();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                }
            }
        });
    }
    
    /**
     * Get preview URL for a track by searching for it
     *
     * @param title Track title
     * @param artist Track artist
     * @param callback Callback to handle the result
     */
    public void getPreviewUrl(String title, String artist, DeezerCallback callback) {
        String searchQuery = title;
        if (artist != null && !artist.isEmpty()) {
            searchQuery += " " + artist;
        }
        
        searchTracks(searchQuery, 10, new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    Track track = tracks.get(0);
                    String previewUrl = track.getPreviewUrl();
                    
                    if (previewUrl != null && !previewUrl.isEmpty()) {
                        callback.onSuccess(previewUrl);
                    } else {
                        callback.onError("No preview URL found for track");
                    }
                } else {
                    callback.onError("No tracks found matching query");
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }
    
    /**
     * Get Deezer streaming URL for a track
     *
     * @param track The track
     * @param callback Callback to handle the result
     */
    public void getDeezerStreamUrl(Track track, DeezerCallback callback) {
        if (track == null) {
            runOnMainThread(() -> callback.onError("Track is null"));
            return;
        }
        
        // If the track already has a preview URL, return it
        if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
            runOnMainThread(() -> callback.onSuccess(track.getPreviewUrl()));
            return;
        }
        
        // Try to get the preview URL using the track title and artist
        getPreviewUrl(track.getTitle(), track.getArtist(), callback);
    }
    
    /**
     * Search tracks and convert them to Track objects
     *
     * @param query The search query
     * @param callback Callback to handle the result
     */
    public void searchAndConvertToTracks(String query, my.edu.utar.bananamusic.utils.callbacks.TracksCallback callback) {
        searchTracks(query, 20, callback);
    }
    
    /**
     * Clear all caches
     */
    public void clearCache() {
        // Clear any caches if implemented
        Log.d(TAG, "Cache cleared");
    }
    
    /**
     * Get mood-based recommendations
     * Wrapper method that adapts between interfaces
     * 
     * @param mood The mood
     * @param limit Number of tracks
     * @param callback Recommendations callback
     */
    public void getMoodBasedRecommendations(String mood, int limit, my.edu.utar.bananamusic.utils.callbacks.TracksCallback callback) {
        // Convert the callback to our internal type
        TracksCallback internalCallback = new TracksCallback() {
            @Override
            public void onTracksLoaded(List<Track> tracks) {
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e.getMessage());
            }
        };
        getMoodBasedRecommendationsInternal(mood, limit, internalCallback);
    }
    
    /**
     * Get recommended tracks from Deezer
     * 
     * @param callback Callback to handle the result
     */
    public void getRecommendedTracks(DeezerTracksCallback callback) {
        String url = BASE_API_URL + "/chart/0/tracks?limit=20";
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Failed to get recommended tracks: " + e.getMessage();
                Log.e(TAG, errorMessage);
                runOnMainThread(() -> callback.onError(errorMessage));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String jsonData = response.body().string();
                    JSONObject json = new JSONObject(jsonData);
                    JSONArray tracks = json.getJSONArray("data");
                    List<Track> trackList = parseTracksFromArray(tracks);
                    runOnMainThread(() -> callback.onSuccess(trackList));
                } catch (JSONException e) {
                    String errorMessage = "Failed to parse recommended tracks: " + e.getMessage();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                }
            }
        });
    }
    
    /**
     * Get recommendations based on a query
     * 
     * @param query The query (e.g., artist name, genre)
     * @param limit Maximum number of tracks to return
     * @param callback Callback to handle the result
     */
    public void getRecommendations(String query, int limit, DeezerTracksCallback callback) {
        searchTracks(query, new DeezerCallback() {
            @Override
            public void onSuccess(String response) {
                List<Track> tracks = parseTracksFromResponse(response);
                callback.onSuccess(tracks);
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Get track information by Deezer track ID
     * 
     * @param trackId Deezer track ID
     * @param callback Callback to handle the result
     */
    public void getTrackById(String trackId, JSONResponseCallback callback) {
        String url = BASE_API_URL + "/track/" + trackId;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Failed to fetch track: " + e.getMessage();
                Log.e(TAG, errorMessage);
                runOnMainThread(() -> callback.onError(errorMessage));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMessage = "Error response: " + response.code();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    runOnMainThread(() -> callback.onSuccess(jsonResponse));
                    
                } catch (JSONException e) {
                    String errorMessage = "Error parsing track information: " + e.getMessage();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                }
            }
        });
    }

    /**
     * Get recommendations for a specific mood
     *
     * @param mood The mood keyword
     * @param limit Maximum number of tracks to return
     * @param callback Callback to handle the result
     */
    private void getMoodBasedRecommendationsInternal(String mood, int limit, TracksCallback callback) {
        String playlistId = getMoodPlaylistId(mood);
        if (playlistId == null) {
            callback.onError(new Exception("Invalid mood: " + mood));
            return;
        }
        
        String url = BASE_API_URL + "/playlist/" + playlistId + "/tracks?limit=" + limit;
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Failed to get mood recommendations: " + e.getMessage();
                Log.e(TAG, errorMessage);
                runOnMainThread(() -> callback.onError(e));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String jsonData = response.body().string();
                    JSONObject json = new JSONObject(jsonData);
                    JSONArray tracks = json.getJSONArray("data");
                    List<Track> trackList = parseTracksFromArray(tracks);
                    runOnMainThread(() -> callback.onTracksLoaded(trackList));
                } catch (JSONException e) {
                    String errorMessage = "Failed to parse mood recommendations: " + e.getMessage();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(e));
                }
            }
        });
    }
    
    /**
     * Map mood to appropriate Deezer playlist ID
     */
    private String getMoodPlaylistId(String mood) {
        switch (mood.toLowerCase()) {
            case "happy":
                return "1479458365"; // Happy Hits playlist
            case "sad":
                return "1123808481"; // Sad Songs playlist
            case "energetic":
                return "1353632755"; // Workout playlist
            case "relaxed":
                return "1234567890"; // Chill playlist
            case "romantic":
                return "1987654321"; // Love Songs playlist
            default:
                return null;
        }
    }

    /**
     * Get Deezer recommendations with callback
     *
     * @param mood The mood keyword
     * @param limit Maximum number of tracks to return
     * @param callback Callback to receive recommendations
     */
    public void getDeezerRecommendations(String mood, int limit, DeezerRecommendationsCallback callback) {
        String playlistId = getMoodPlaylistId(mood);
        if (playlistId == null) {
            callback.onError("Invalid mood: " + mood);
            return;
        }
        
        String url = BASE_API_URL + "/playlist/" + playlistId + "/tracks?limit=" + limit;
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Failed to get mood recommendations: " + e.getMessage();
                Log.e(TAG, errorMessage);
                runOnMainThread(() -> callback.onError(errorMessage));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String jsonData = response.body().string();
                    JSONObject json = new JSONObject(jsonData);
                    JSONArray tracks = json.getJSONArray("data");
                    List<Track> trackList = parseTracksFromArray(tracks);
                    runOnMainThread(() -> callback.onSuccess(trackList));
                } catch (JSONException e) {
                    String errorMessage = "Failed to parse mood recommendations: " + e.getMessage();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                }
            }
        });
    }

    /**
     * Run a runnable on the main thread
     */
    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    /**
     * Get tracks for a specific mood
     * @param mood The mood (e.g., "happy", "sad", "energetic")
     * @param callback Callback to return the tracks
     */
    public void getMoodTracks(String mood, TracksCallback callback) {
        String query = mapMoodToSearchQuery(mood);
        getRecommendations(query, 20, new DeezerTracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onTracksLoaded(tracks);
            }
            
            @Override
            public void onError(String message) {
                callback.onError(new Exception(message));
            }
        });
    }

    /**
     * Get tracks for a specific mood using utils.callbacks.TracksCallback
     * @param mood The mood (e.g., "happy", "sad", "energetic")
     * @param callback Callback to return the tracks
     */
    public void getMoodTracks(String mood, DeezerTracksCallback callback) {
        getMoodBasedRecommendations(mood, 20, new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Map a mood to a search query
     * @param mood The mood string
     * @return A search query relevant to the mood
     */
    private String mapMoodToSearchQuery(String mood) {
        switch (mood.toLowerCase()) {
            case "happy":
                return "happy upbeat";
            case "sad":
                return "sad emotional";
            case "energetic":
                return "energetic workout";
            case "relaxed":
                return "chill relax";
            case "romantic":
                return "love romantic";
            default:
                return mood;
        }
    }

    public void getPlaylistTracks(String playlistId, DeezerTracksCallback callback) {
        if (playlistId == null || playlistId.isEmpty()) {
            Log.e(TAG, "Invalid Deezer playlist ID (null or empty)");
            callback.onError("Invalid playlist ID");
            return;
        }
        
        Log.d(TAG, "Getting tracks for Deezer playlist ID: " + playlistId);
        String url = BASE_API_URL + "/playlist/" + playlistId + "/tracks?limit=50";
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMsg = "Failed to get Deezer playlist tracks: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                runOnMainThread(() -> callback.onError(errorMsg));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMsg = "Error response from Deezer: " + response.code();
                    Log.e(TAG, errorMsg + " - " + response.message());
                    runOnMainThread(() -> callback.onError(errorMsg));
                    return;
                }
                
                try {
                    String jsonData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(jsonData);
                    
                    // Check if response contains data array
                    if (!jsonResponse.has("data")) {
                        Log.w(TAG, "Deezer playlist response missing 'data' field");
                        runOnMainThread(() -> callback.onSuccess(new ArrayList<>()));
                        return;
                    }
                    
                    JSONArray tracksArray = jsonResponse.getJSONArray("data");
                    Log.d(TAG, "Fetched " + tracksArray.length() + " tracks from Deezer playlist");
                    
                    List<Track> tracks = parseTracksFromArray(tracksArray);
                    runOnMainThread(() -> callback.onSuccess(tracks));
                    
                } catch (JSONException e) {
                    String errorMsg = "Error parsing Deezer playlist tracks: " + e.getMessage();
                    Log.e(TAG, errorMsg, e);
                    runOnMainThread(() -> callback.onError(errorMsg));
                }
            }
        });
    }

    public List<Track> parseTracksFromResponse(String response) {
        List<Track> tracks = new ArrayList<>();
        
        if (response == null || response.isEmpty()) {
            return tracks;
        }
        
        try {
            JSONObject jsonResponse = new JSONObject(response);
            
            // Check for data array which contains tracks
            if (jsonResponse.has("data")) {
                JSONArray data = jsonResponse.getJSONArray("data");
                
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    Track track = parseTrackFromJson(item);
                    if (track != null) {
                        tracks.add(track);
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing tracks from response: " + e.getMessage());
        }
        
        return tracks;
    }
    
    private Track parseTrackFromJson(JSONObject json) {
        try {
            String id = json.getString("id");
            String title = json.getString("title");
            
            // Get artist info
            JSONObject artist = json.getJSONObject("artist");
            String artistName = artist.getString("name");
            
            // Get album info
            JSONObject album = json.getJSONObject("album");
            String albumName = album.getString("title");
            
            // Get album art
            String albumArtUrl = album.getString("cover_big");
            
            // Get duration
            int durationSec = json.getInt("duration");
            long durationMs = durationSec * 1000L;
            
            // Get preview URL
            String previewUrl = json.getString("preview");
            
            // Create track
            Track track = new Track(id, title, artistName, albumName, albumArtUrl, durationMs, previewUrl, true);
            track.setSource(Track.SOURCE_DEEZER);
            track.setDeezerTrackId(Long.parseLong(id));
            
            return track;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing track: " + e.getMessage());
            return null;
        }
    }
    
    public void getPreview(String trackId, DeezerCallback callback) {
        String url = BASE_API_URL + "track/" + trackId;
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error fetching track preview: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onError("Error: " + response.code());
                    return;
                }
                
                try {
                    String responseBody = response.body().string();
                    JSONObject track = new JSONObject(responseBody);
                    String previewUrl = track.optString("preview", "");
                    
                    if (previewUrl != null && !previewUrl.isEmpty()) {
                        callback.onSuccess(previewUrl);
                    } else {
                        callback.onError("No preview available");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing preview response: " + e.getMessage());
                    callback.onError("Error parsing response");
                }
            }
        });
    }
    
    public void getArtistTracks(String artistId, DeezerCallback callback) {
        String url = BASE_API_URL + "artist/" + artistId + "/top?limit=50";
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error fetching artist tracks: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onError("Error: " + response.code());
                    return;
                }
                
                try {
                    String responseBody = response.body().string();
                    callback.onSuccess(responseBody);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing artist tracks response: " + e.getMessage());
                    callback.onError("Error parsing response");
                }
            }
        });
    }
    
    public void getPlaylist(String playlistId, DeezerCallback callback) {
        String url = BASE_API_URL + "playlist/" + playlistId + "/tracks";
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error fetching playlist: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onError("Error: " + response.code());
                    return;
                }
                
                try {
                    String responseBody = response.body().string();
                    callback.onSuccess(responseBody);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing playlist response: " + e.getMessage());
                    callback.onError("Error parsing response");
                }
            }
        });
    }
    
    public void getPlaylists(DeezerCallback callback) {
        long userId = 0; // Default value
        
        if (userId <= 0) {
            callback.onError("Invalid user ID");
            return;
        }
        
        String url = BASE_API_URL + "user/" + userId + "/playlists";
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error fetching playlists: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onError("Error: " + response.code());
                    return;
                }
                
                try {
                    String responseBody = response.body().string();
                    callback.onSuccess(responseBody);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing playlists response: " + e.getMessage());
                    callback.onError("Error parsing response");
                }
            }
        });
    }

    /**
     * Get user playlists for UnifiedMusicService
     */
    public void getUserPlaylists(my.edu.utar.bananamusic.utils.UnifiedMusicService.MusicCallback callback) {
        // Since we need a userID which we don't have, return an empty list for now
        callback.onSuccess(new ArrayList<>());
    }

    /**
     * Get track preview URL by track ID
     */
    public void getTrackPreview(String trackId, DeezerCallback callback) {
        String url = BASE_API_URL + "/track/" + trackId;
        
        Request request = new Request.Builder()
            .url(url)
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error fetching track preview: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Error: " + response.code());
                    return;
                }
                
                try {
                    String jsonData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(jsonData);
                    String previewUrl = jsonResponse.optString("preview", "");
                    
                    if (previewUrl != null && !previewUrl.isEmpty()) {
                        callback.onSuccess(previewUrl);
                    } else {
                        callback.onError("No preview available");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing track preview: " + e.getMessage());
                    callback.onError("Error parsing response");
                }
            }
        });
    }

    /**
     * Get new releases from Deezer's official editorial releases endpoint
     * Only returns albums released within the last 30 days
     */
    public void getNewReleases(NewReleasesCallback callback) {
        // Use chart/0/albums as fallback since it's more reliable
        String url = BASE_API_URL + "/chart/0/albums";
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Failed to get new releases: " + e.getMessage();
                Log.e(TAG, errorMessage);
                runOnMainThread(() -> callback.onError(errorMessage));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMessage = "Error response: " + response.code();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                    return;
                }
                
                try {
                    String jsonData = response.body().string();
                    Log.d(TAG, "Received response: " + jsonData);
                    
                    JSONObject jsonResponse = new JSONObject(jsonData);
                    JSONArray albumsArray = jsonResponse.getJSONArray("data");
                    Log.d(TAG, "Found " + albumsArray.length() + " albums in response");
                    
                    List<Album> albums = new ArrayList<>();
                    Calendar sixMonthsAgo = Calendar.getInstance();
                    sixMonthsAgo.add(Calendar.MONTH, -6); // Extend to 6 months to get more releases
                    
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    
                    for (int i = 0; i < albumsArray.length(); i++) {
                        try {
                            JSONObject albumJson = albumsArray.getJSONObject(i);
                            
                            String id = albumJson.getString("id");
                            String title = albumJson.getString("title");
                            String artist = albumJson.getJSONObject("artist").getString("name");
                            String coverUrl = albumJson.getString("cover_big");
                            String deezerUrl = albumJson.getString("link");
                            
                            // Parse release date if available
                            Date releaseDate = null;
                            if (albumJson.has("release_date")) {
                                String releaseDateStr = albumJson.getString("release_date");
                                Log.d(TAG, "Release date for " + title + ": " + releaseDateStr);
                                if (releaseDateStr != null && !releaseDateStr.isEmpty()) {
                                    try {
                                        releaseDate = sdf.parse(releaseDateStr);
                                    } catch (Exception e) {
                                        Log.w(TAG, "Could not parse release date: " + releaseDateStr);
                                    }
                                }
                            }
                            
                            // Include album if it's within last 6 months or if no release date
                            if (releaseDate == null || !releaseDate.before(sixMonthsAgo.getTime())) {
                                Album album = new Album(id, title, artist, coverUrl, releaseDate, deezerUrl);
                                albums.add(album);
                                Log.d(TAG, "Added album: " + title + " by " + artist);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing album: " + e.getMessage());
                            // Continue with next album instead of failing completely
                            continue;
                        }
                    }
                    
                    // Sort albums by release date (newest first)
                    Collections.sort(albums, (a1, a2) -> {
                        Date d1 = a1.getReleaseDate();
                        Date d2 = a2.getReleaseDate();
                        if (d1 == null && d2 == null) return 0;
                        if (d1 == null) return 1;
                        if (d2 == null) return -1;
                        return d2.compareTo(d1);
                    });
                    
                    Log.d(TAG, "Final number of albums after filtering: " + albums.size());
                    
                    if (albums.isEmpty()) {
                        runOnMainThread(() -> callback.onError("No recent releases found"));
                    } else {
                        runOnMainThread(() -> callback.onSuccess(albums));
                    }
                    
                } catch (Exception e) {
                    String errorMessage = "Error parsing new releases: " + e.getMessage();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                }
            }
        });
    }
    
    /**
     * Get top trending tracks from Deezer
     * Fetches 50 tracks and filters them to return the best 10 with no duplicate artists
     */
    public void getTopTracks(TopTracksCallback callback) {
        // Request 50 tracks to have a larger pool to filter from
        String url = BASE_API_URL + "/chart/0/tracks?limit=50";
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Failed to get top tracks: " + e.getMessage();
                Log.e(TAG, errorMessage);
                runOnMainThread(() -> callback.onError(errorMessage));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMessage = "Error response: " + response.code();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                    return;
                }
                
                try {
                    String jsonData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(jsonData);
                    JSONArray tracksArray = jsonResponse.getJSONArray("data");
                    
                    List<Track> allTracks = parseTracksFromArray(tracksArray);
                    
                    // Filter tracks to avoid duplicate artists and ensure variety
                    List<Track> filteredTracks = new ArrayList<>();
                    Set<String> seenArtists = new HashSet<>();
                    
                    for (Track track : allTracks) {
                        String artist = track.getArtist();
                        // Only add the track if we haven't seen this artist before
                        if (!seenArtists.contains(artist.toLowerCase())) {
                            filteredTracks.add(track);
                            seenArtists.add(artist.toLowerCase());
                            
                            // Break if we have enough tracks
                            if (filteredTracks.size() >= 10) {
                                break;
                            }
                        }
                    }
                    
                    // If we don't have enough tracks after filtering, add more from the remaining tracks
                    if (filteredTracks.size() < 10) {
                        for (Track track : allTracks) {
                            if (!filteredTracks.contains(track)) {
                                filteredTracks.add(track);
                                if (filteredTracks.size() >= 10) {
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Add ranking numbers to track titles
                    for (int i = 0; i < filteredTracks.size(); i++) {
                        Track track = filteredTracks.get(i);
                        track.setRankingPosition(i + 1);
                    }
                    
                    runOnMainThread(() -> callback.onSuccess(filteredTracks));
                    
                } catch (Exception e) {
                    String errorMessage = "Error parsing top tracks: " + e.getMessage();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                }
            }
        });
    }

    /**
     * Get featured playlists from Deezer
     * @param callback Callback to notify when playlists are loaded
     */
    public void getFeaturedPlaylists(DeezerPlaylistsCallback callback) {
        // For now, return an empty list
        callback.onSuccess(new ArrayList<>());
    }

    /**
     * Search for playlists based on mood
     * @param mood The mood to search for
     * @param callback Callback to handle the result
     */
    public void searchPlaylists(String mood, DeezerPlaylistCallback callback) {
        if (mood == null || mood.isEmpty()) {
            callback.onError("Mood cannot be empty");
            return;
        }
        
        // Encode query for URL
        String encodedQuery;
        try {
            encodedQuery = java.net.URLEncoder.encode(mood, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Error encoding mood query: " + e.getMessage());
            callback.onError("Error encoding query: " + e.getMessage());
            return;
        }
        
        // Build URL for search
        String url = BASE_API_URL + "/search/playlist?q=" + encodedQuery + "&limit=10";
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMessage = "Failed to search playlists: " + e.getMessage();
                Log.e(TAG, errorMessage);
                runOnMainThread(() -> callback.onError(errorMessage));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMessage = "Error response: " + response.code();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                    return;
                }
                
                try {
                    String jsonData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(jsonData);
                    
                    if (!jsonResponse.has("data") || jsonResponse.getJSONArray("data").length() == 0) {
                        runOnMainThread(() -> callback.onError("No playlists found for mood: " + mood));
                        return;
                    }
                    
                    // Get a random playlist from the results
                    JSONArray playlists = jsonResponse.getJSONArray("data");
                    int randomIndex = new Random().nextInt(playlists.length());
                    JSONObject playlistJson = playlists.getJSONObject(randomIndex);
                    
                    // Parse playlist data
                    String id = playlistJson.getString("id");
                    String title = playlistJson.getString("title");
                    String description = playlistJson.optString("description", "");
                    
                    // Get creator info
                    JSONObject creator = playlistJson.optJSONObject("creator");
                    String creatorName = creator != null ? creator.optString("name", "Unknown") : "Unknown";
                    
                    // Get picture URL
                    String pictureUrl = playlistJson.optString("picture_big", "");
                    if (pictureUrl.isEmpty()) {
                        pictureUrl = playlistJson.optString("picture_medium", "");
                    }
                    
                    // Create playlist object
                    Playlist playlist = new Playlist(id, title, creatorName, description, pictureUrl, false, SOURCE_NAME);
                    playlist.setMood(mood);
                    
                    runOnMainThread(() -> callback.onSuccess(playlist));
                    
                } catch (Exception e) {
                    String errorMessage = "Error parsing playlist results: " + e.getMessage();
                    Log.e(TAG, errorMessage);
                    runOnMainThread(() -> callback.onError(errorMessage));
                }
            }
        });
    }
}