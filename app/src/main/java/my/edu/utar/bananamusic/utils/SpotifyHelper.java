package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.Playlist;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Map;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;
import java.lang.StringBuilder;
import okhttp3.HttpUrl;
import okhttp3.Headers;

/**
 * Helper class for Spotify API operations
 */
public class SpotifyHelper {
    private static final String TAG = "SpotifyHelper";
    private static final String BASE_API_URL = "https://api.spotify.com/v1";
    private static SpotifyHelper instance;
    private final Context context;
    private final OkHttpClient client;
    private boolean cachingEnabled = true;
    private String accessToken;
    private long tokenExpirationTime;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SpotifyCallback {
        void onSuccess(String result);
        void onError(String message);
    }

    public interface SpotifyTracksCallback {
        void onSuccess(List<Track> tracks);
        void onError(String message);
    }

    public interface SpotifyPlaylistsCallback {
        void onSuccess(List<Playlist> playlists);
        void onError(String message);
    }

    public interface SpotifyRecommendationsCallback {
        void onSuccess(List<Track> tracks);
        void onError(String message);
    }

    public interface AccessTokenCallback {
        void onTokenReceived(String token);
        void onError(String message);
    }

    public interface FeaturedContentCallback {
        void onSuccess(List<Track> recommendedTracks, List<Track> newReleases);
        void onError(String message);
    }

    public interface JSONResponseCallback {
        void onSuccess(JSONObject response);
        void onError(String message);
    }

    private SpotifyHelper(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    public static synchronized SpotifyHelper getInstance(Context context) {
        if (instance == null) {
            instance = new SpotifyHelper(context);
        }
        return instance;
    }

    public void getAccessToken(AccessTokenCallback callback) {
        Log.d(TAG, "Getting access token...");
        
        if (isTokenValid()) {
            Log.d(TAG, "Reusing existing valid token: " + accessToken.substring(0, Math.min(10, accessToken.length())) + "...");
            callback.onTokenReceived(accessToken);
            return;
        }

        // Create credentials
        String credentials = SpotifyConfig.CLIENT_ID + ":" + SpotifyConfig.CLIENT_SECRET;
        String base64Credentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);

        // Log credentials (partial)
        Log.d(TAG, "Using client ID: " + SpotifyConfig.CLIENT_ID);
        Log.d(TAG, "Base64 credentials (partial): " + base64Credentials.substring(0, Math.min(10, base64Credentials.length())) + "...");

        // Create request body
        RequestBody formBody = new FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build();

        // Create request
        Request request = new Request.Builder()
            .url(SpotifyConfig.AUTH_URL)
            .addHeader("Authorization", "Basic " + base64Credentials)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build();

        // Log request details
        Log.d(TAG, "Token request URL: " + SpotifyConfig.AUTH_URL);
        Log.d(TAG, "Token request method: POST");
        Log.d(TAG, "Token request headers:");
        Headers headers = request.headers();
        for (int i = 0, size = headers.size(); i < size; i++) {
            String name = headers.name(i);
            // Don't log the full Authorization header
            if (name.equals("Authorization")) {
                Log.d(TAG, name + ": Basic " + base64Credentials.substring(0, Math.min(10, base64Credentials.length())) + "...");
            } else {
                Log.d(TAG, name + ": " + headers.value(i));
            }
        }

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMsg = "Failed to get access token: " + e.getMessage();
                Log.e(TAG, errorMsg);
                callback.onError(errorMsg);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : null;
                
                // Log response details
                Log.d(TAG, "Token response code: " + response.code());
                Log.d(TAG, "Token response headers:");
                Headers responseHeaders = response.headers();
                for (int i = 0, size = responseHeaders.size(); i < size; i++) {
                    Log.d(TAG, responseHeaders.name(i) + ": " + responseHeaders.value(i));
                }

                if (!response.isSuccessful() || responseBody == null) {
                    String errorMsg = "Failed to get token. Status: " + response.code();
                    if (responseBody != null) {
                        errorMsg += ", Body: " + responseBody;
                    }
                    Log.e(TAG, errorMsg);
                    callback.onError(errorMsg);
                    return;
                }

                try {
                    JSONObject json = new JSONObject(responseBody);
                    
                    // Get token details
                    String token = json.getString("access_token");
                    String tokenType = json.getString("token_type");
                    int expiresIn = json.getInt("expires_in");
                    
                    // Validate token
                    if (token == null || token.trim().isEmpty()) {
                        Log.e(TAG, "Received empty token from Spotify");
                        callback.onError("Received empty token from Spotify");
                        return;
                    }

                    // Validate token type
                    if (!"Bearer".equalsIgnoreCase(tokenType)) {
                        Log.e(TAG, "Unexpected token type: " + tokenType);
                        callback.onError("Unexpected token type: " + tokenType);
                        return;
                    }

                    // Store token
                    accessToken = token;
                    tokenExpirationTime = System.currentTimeMillis() + (expiresIn * 1000);
                    
                    Log.d(TAG, "Successfully obtained new access token: " + token.substring(0, Math.min(10, token.length())) + "...");
                    Log.d(TAG, "Token type: " + tokenType);
                    Log.d(TAG, "Token expires in: " + expiresIn + " seconds");
                    Log.d(TAG, "Token expiration time: " + tokenExpirationTime);
                    
                    callback.onTokenReceived(token);
                } catch (JSONException e) {
                    String errorMsg = "Failed to parse token response: " + e.getMessage();
                    Log.e(TAG, errorMsg);
                    callback.onError(errorMsg);
                }
            }
        });
    }

    /**
     * Check if the current access token is valid
     */
    private boolean isTokenValid() {
        // Check if token exists and is not empty
        if (accessToken == null || accessToken.trim().isEmpty()) {
            Log.d(TAG, "Access token is null or empty");
            return false;
        }

        // Check token format
        if (!accessToken.matches("^[A-Za-z0-9-._~+/]+=*$")) {
            Log.e(TAG, "Token format is invalid");
            return false;
        }

        // Check minimum token length (Spotify tokens are typically quite long)
        if (accessToken.length() < 20) {
            Log.e(TAG, "Token length is suspiciously short: " + accessToken.length());
            return false;
    }

        // Check if token has expired
        long currentTime = System.currentTimeMillis();
        if (currentTime >= tokenExpirationTime) {
            Log.d(TAG, String.format("Token expired %d seconds ago. Current time: %d, Expiration: %d", 
                (currentTime - tokenExpirationTime) / 1000, currentTime, tokenExpirationTime));
            return false;
        }

        // Add a 30-second buffer to prevent edge cases
        if (tokenExpirationTime - currentTime <= 30000) {
            Log.d(TAG, "Token will expire in less than 30 seconds, considering it invalid");
            return false;
        }

        Log.d(TAG, String.format("Token is valid. Expires in: %d seconds", 
            (tokenExpirationTime - currentTime) / 1000));
        return true;
    }

    /**
     * Ensure we have a valid token before making API calls
     */
    private void ensureValidToken(Runnable callback) {
        Log.d(TAG, "\n=== Token Validation Check ===");
        Log.d(TAG, "Checking token validity...");
        
        // Log current token state
        if (accessToken != null) {
            Log.d(TAG, String.format("Current token (first 10 chars): %s...", 
                accessToken.substring(0, Math.min(10, accessToken.length()))));
            Log.d(TAG, "Token length: " + accessToken.length());
        } else {
            Log.d(TAG, "Current token is null");
        }
        
        if (isTokenValid()) {
            Log.d(TAG, "Token is valid, proceeding with request");
            callback.run();
            return;
        }

        Log.d(TAG, "Token invalid or expired, requesting new token...");
        
        // Clear existing token data
        accessToken = null;
        tokenExpirationTime = 0;
        
        getAccessToken(new AccessTokenCallback() {
            @Override
            public void onTokenReceived(String token) {
                if (token == null || token.trim().isEmpty()) {
                    Log.e(TAG, "Received null or empty token from getAccessToken");
                    return;
                }
                
                // Validate new token format
                if (!token.matches("^[A-Za-z0-9-._~+/]+=*$")) {
                    Log.e(TAG, "Received invalid token format from getAccessToken");
                    return;
                }
                
                accessToken = token;
                // Set expiration to slightly less than an hour to be safe
                tokenExpirationTime = System.currentTimeMillis() + (3500 * 1000); // 58.33 minutes
                
                Log.d(TAG, String.format("Successfully obtained new access token (first 10 chars): %s...", 
                    token.substring(0, Math.min(10, token.length()))));
                Log.d(TAG, "New token length: " + token.length());
                Log.d(TAG, String.format("Token will expire in: %d seconds", 
                    (tokenExpirationTime - System.currentTimeMillis()) / 1000));
                
                callback.run();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to get access token: " + message);
            }
        });
    }

    public void getClientCredentialsToken(SpotifyCallback callback) {
        // Get a proper token using the client credentials flow
            getAccessToken(new AccessTokenCallback() {
                @Override
                public void onTokenReceived(String token) {
                callback.onSuccess(token);
                }

                @Override
                public void onError(String message) {
                callback.onError(message);
                }
            });
    }

    public void searchTracks(String query, String type, SpotifyCallback callback) {
        // Implementation
        callback.onSuccess("[]");
    }

    public void searchTracks(String query, SpotifyCallback callback) {
        searchTracks(query, "track", callback);
    }

    /**
     * Search tracks with a callback that returns a track list
     * 
     * @param query The search query
     * @param callback Callback to handle the list of tracks
     */
    public void searchTracks(String query, SpotifyTracksCallback callback) {
        ensureValidToken(() -> {
            String encodedQuery;
            try {
                encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            } catch (Exception e) {
                Log.e(TAG, "Error encoding query: " + e.getMessage());
                callback.onError("Error encoding query: " + e.getMessage());
                return;
            }
            
            String url = SpotifyConfig.API_BASE_URL + "/search?q=" + encodedQuery + "&type=track&limit=20";
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();
    
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Failed to search tracks: " + e.getMessage());
                }
    
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        JSONObject tracks = json.getJSONObject("tracks");
                        JSONArray items = tracks.getJSONArray("items");
                        
                        List<Track> trackList = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            trackList.add(parseTrackJson(items.getJSONObject(i)));
                        }
                        callback.onSuccess(trackList);
                    } catch (JSONException e) {
                        callback.onError("Failed to parse search results: " + e.getMessage());
                    }
                }
            });
        });
    }

    public void searchTrack(String query, String type, SpotifyCallback callback) {
        searchTracks(query, type, callback);
    }

    public void getTrackPreviewUrl(String spotifyId, SpotifyCallback callback) {
        // Implementation
        callback.onSuccess("https://example.com/preview.mp3");
    }

    public void getFullTrackInfo(String spotifyId, SpotifyCallback callback) {
        // Implementation
        callback.onSuccess("{}");
    }

    public void getFeaturedPlaylists(SpotifyPlaylistsCallback callback) {
        ensureValidToken(() -> {
            String url = SpotifyConfig.API_BASE_URL + "/browse/featured-playlists?limit=20";
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Failed to get featured playlists: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        
                        // Safely check if "playlists" key exists
                        JSONObject playlists = json.optJSONObject("playlists");
                        if (playlists == null) {
                            Log.w(TAG, "Response doesn't contain 'playlists' key");
                            callback.onSuccess(new ArrayList<>()); // Return empty list
                            return;
                        }
                        
                        // Safely get items array
                        JSONArray items = playlists.optJSONArray("items");
                        if (items == null) {
                            Log.w(TAG, "Playlists object doesn't contain 'items' array");
                            callback.onSuccess(new ArrayList<>()); // Return empty list
                            return;
                        }
                        
                        List<Playlist> playlistList = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            try {
                                JSONObject item = items.getJSONObject(i);
                                JSONObject owner = item.optJSONObject("owner");
                                JSONArray images = item.optJSONArray("images");
                                
                                Playlist playlist = new Playlist(
                                    item.optString("id", "unknown_" + i),
                                    item.optString("name", "Unnamed Playlist"),
                                    owner != null ? owner.optString("display_name", "Unknown") : "Unknown",
                                    item.optString("description", ""),
                                    images != null && images.length() > 0 ? images.optJSONObject(0).optString("url", "") : "",
                                    false,
                                    "Spotify"
                                );
                                playlistList.add(playlist);
                            } catch (Exception e) {
                                // Log error but continue processing other playlists
                                Log.e(TAG, "Error parsing playlist at index " + i + ": " + e.getMessage());
                            }
                        }
                        callback.onSuccess(playlistList);
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse playlists: " + e.getMessage());
                        callback.onError("Failed to parse playlists: " + e.getMessage());
                    }
                }
            });
        });
    }

    public void getCollaborativePlaylists(SpotifyPlaylistsCallback callback) {
        ensureValidToken(() -> {
            // First, check if user is logged in
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Log.w(TAG, "No user logged in - returning empty playlists list");
                callback.onSuccess(new ArrayList<>());
                return;
            }
            
            // Get user playlists from Firebase
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("playlists")
                .whereEqualTo("isCollaborative", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Playlist> playlists = new ArrayList<>();
                    
                    // Process each playlist document
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String id = document.getId();
                        String name = document.getString("name");
                        String description = document.getString("description");
                        String creatorName = document.getString("creatorName");
                        String coverImageUrl = document.getString("coverImageUrl");
                        boolean isCollaborative = Boolean.TRUE.equals(document.getBoolean("isCollaborative"));
                        
                        Playlist playlist = new Playlist(id, name, creatorName, description, coverImageUrl, isCollaborative, "Firebase");
                        playlists.add(playlist);
                    }
                    
                    callback.onSuccess(playlists);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading collaborative playlists: " + e.getMessage());
                    callback.onError(e.getMessage());
                });
        });
    }

    public void getPlaylistTracks(String playlistId, JSONResponseCallback callback) {
        if (playlistId == null || playlistId.isEmpty()) {
            Log.e(TAG, "Invalid playlist ID provided (null or empty)");
            callback.onError("Invalid playlist ID");
            return;
        }
        
        Log.d(TAG, "Getting tracks for Spotify playlist ID: " + playlistId);
        ensureValidToken(() -> {
            String url = SpotifyConfig.API_BASE_URL + "/playlists/" + playlistId + "/tracks?limit=50";
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String errorMsg = "Failed to get playlist tracks: " + e.getMessage();
                    Log.e(TAG, errorMsg);
                    mainHandler.post(() -> callback.onError(errorMsg));
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorMsg = "Error response: " + response.code();
                        Log.e(TAG, errorMsg + " - " + response.message());
                        mainHandler.post(() -> callback.onError(errorMsg));
                        return;
                    }
                    
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        Log.d(TAG, "Successfully got playlist tracks response");
                        
                        // Check if we got a valid response with items
                        if (!json.has("items")) {
                            Log.w(TAG, "Spotify playlist response missing 'items' field");
                            json.put("items", new JSONArray()); // Add empty items array
                        } else {
                            JSONArray items = json.getJSONArray("items");
                            Log.d(TAG, "Fetched " + items.length() + " tracks from Spotify playlist");
                        }
                        
                        mainHandler.post(() -> callback.onSuccess(json));
                    } catch (Exception e) {
                        String errorMsg = "Failed to parse playlist tracks: " + e.getMessage();
                        Log.e(TAG, errorMsg, e);
                        mainHandler.post(() -> callback.onError(errorMsg));
                    }
                }
            });
        });
    }

    public Track parseTrackJson(JSONObject trackJson) throws JSONException {
        String id = trackJson.getString("id");
        String title = trackJson.getString("name");
        String artist = trackJson.getJSONArray("artists").getJSONObject(0).getString("name");
        String album = trackJson.getJSONObject("album").getString("name");
        String imageUrl = trackJson.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");
        String previewUrl = trackJson.getString("preview_url");
        int duration = trackJson.optInt("duration_ms", 0);
        
        return new Track(id, title, artist, album, imageUrl, duration, "Spotify", true);
    }

    public void clearCache() {
        // Implementation
    }

    public void setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
    }

    public void playTrackUsingSDK(String spotifyId, SpotifyCallback callback) {
        // Implementation
        callback.onSuccess("Playing track");
    }

    public void playTrackOnDevice(String spotifyId, String deviceId, String accessToken, SpotifyCallback callback) {
        // Implementation
        callback.onSuccess("Playing track on device");
    }

    public String getFallbackAudioUrl() {
        return "https://p.scdn.co/mp3-preview/default.mp3";
    }

    public void getRecommendationsFromUrl(String url, SpotifyRecommendationsCallback callback) {
        // Implementation
        callback.onSuccess(new ArrayList<>());
    }

    public void getRecommendations(SpotifyRecommendationsCallback callback) {
        // Implementation
        callback.onSuccess(new ArrayList<>());
    }

    public void getMoodBasedRecommendations(String mood, int limit, SpotifyRecommendationsCallback callback) {
        // First verify we have a valid token
        if (accessToken == null || accessToken.trim().isEmpty()) {
            Log.e(TAG, "Access token is null or empty before making request");
            callback.onError("No valid access token available");
            return;
        }

        // Add explicit expiration check
        if (System.currentTimeMillis() >= tokenExpirationTime) {
            Log.e(TAG, "Access token has expired. Current time: " + System.currentTimeMillis() + ", Expiration: " + tokenExpirationTime);
            getAccessToken(new AccessTokenCallback() {
                @Override
                public void onTokenReceived(String token) {
                    // Retry the request with new token
                    getMoodBasedRecommendations(mood, limit, callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError("Failed to refresh expired token: " + message);
                }
            });
            return;
        }

        ensureValidToken(() -> {
            try {
        // Build URL with proper parameters
                String baseUrl = SpotifyConfig.API_BASE_URL + "/recommendations";
                HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
                urlBuilder.addQueryParameter("seed_genres", getMoodSeedGenres(mood.toLowerCase()));
                urlBuilder.addQueryParameter("limit", String.valueOf(limit));
                urlBuilder.addQueryParameter("market", "US");
                urlBuilder.addQueryParameter("min_popularity", "25");

        // Add mood-specific parameters
        switch (mood.toLowerCase()) {
            case "happy":
                        urlBuilder.addQueryParameter("target_valence", String.valueOf(0.8f));
                        urlBuilder.addQueryParameter("target_energy", String.valueOf(0.7f));
                        urlBuilder.addQueryParameter("min_valence", "0.6");
                        urlBuilder.addQueryParameter("target_tempo", "120");
                break;
            case "sad":
                        urlBuilder.addQueryParameter("target_valence", String.valueOf(0.3f));
                        urlBuilder.addQueryParameter("target_energy", String.valueOf(0.4f));
                        urlBuilder.addQueryParameter("max_valence", "0.5");
                        urlBuilder.addQueryParameter("target_acousticness", String.valueOf(0.6f));
                        urlBuilder.addQueryParameter("target_instrumentalness", String.valueOf(0.4f));
                        urlBuilder.addQueryParameter("max_tempo", "100");
                break;
            case "energetic":
                        urlBuilder.addQueryParameter("target_energy", String.valueOf(0.9f));
                        urlBuilder.addQueryParameter("target_tempo", "130");
                        urlBuilder.addQueryParameter("min_energy", "0.7");
                        urlBuilder.addQueryParameter("target_danceability", "0.7");
                break;
            case "relaxed":
                        urlBuilder.addQueryParameter("target_energy", String.valueOf(0.3f));
                        urlBuilder.addQueryParameter("target_acousticness", String.valueOf(0.7f));
                        urlBuilder.addQueryParameter("max_energy", "0.5");
                        urlBuilder.addQueryParameter("target_tempo", "85");
                break;
            case "focused":
                        urlBuilder.addQueryParameter("target_instrumentalness", String.valueOf(0.6f));
                        urlBuilder.addQueryParameter("target_energy", String.valueOf(0.5f));
                        urlBuilder.addQueryParameter("max_speechiness", "0.3");
                break;
            default:
                        urlBuilder.addQueryParameter("target_valence", "0.5");
                        urlBuilder.addQueryParameter("target_energy", "0.5");
                        break;
                }

                String url = urlBuilder.build().toString();

                // Create the request with explicit GET method
                Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "BananaMusic/1.0")
                    .build();

                // Verify request token before sending
                verifyRequestToken(request);

                // Execute request with enhanced error handling
                Log.d(TAG, "Executing request...");
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        String errorMessage = "Network request failed: " + e.getMessage();
                        Log.e(TAG, "\n=== Request Failure ===");
                        Log.e(TAG, "Error: " + errorMessage);
                        Log.e(TAG, "Failed URL: " + call.request().url());
                        Log.e(TAG, "Exception stack trace:");
                        e.printStackTrace();
                        mainHandler.post(() -> callback.onError(errorMessage));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        // Log response details immediately
                        Log.d(TAG, "\n=== Response Details ===");
                        Log.d(TAG, "Response code: " + response.code());
                        Log.d(TAG, "Response message: " + response.message());
                        Log.d(TAG, "Response protocol: " + response.protocol());
                        
                        // Log response headers
                        Headers responseHeaders = response.headers();
                        Log.d(TAG, "Response headers:");
                        for (String name : responseHeaders.names()) {
                            Log.d(TAG, name + ": " + responseHeaders.get(name));
        }

                        // Read response body
                        String responseBody = null;
                        try {
                            responseBody = response.body() != null ? response.body().string() : null;
                            Log.d(TAG, "Response body: " + (responseBody != null ? responseBody : "null"));
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading response body: " + e.getMessage());
                        }

                        // Handle unsuccessful responses
                        if (!response.isSuccessful()) {
                            String errorMessage;
                            if (responseBody != null && !responseBody.isEmpty()) {
                                try {
                                    JSONObject json = new JSONObject(responseBody);
                                    JSONObject error = json.optJSONObject("error");
                                    if (error != null) {
                                        String status = String.valueOf(error.optInt("status"));
                                        String message = error.optString("message");
                                        String reason = error.optString("reason");
                                        
                                        errorMessage = String.format("Spotify API error %s: %s", status, message);
                                        if (!reason.isEmpty()) {
                                            errorMessage += " (Reason: " + reason + ")";
                                        }
                                        
                                        // Enhanced error logging for auth issues
                                        if (response.code() == 401) {
                                            Log.e(TAG, "\n=== Authentication Error Details ===");
                                            Log.e(TAG, "Error message: " + errorMessage);
                                            Log.e(TAG, "Token status:");
                                            Log.e(TAG, "- Token exists: " + (accessToken != null));
                                            Log.e(TAG, "- Token empty: " + (accessToken == null || accessToken.trim().isEmpty()));
                                            Log.e(TAG, "- Token valid: " + isTokenValid());
                                            Log.e(TAG, "- Token expiration: " + tokenExpirationTime);
                                            Log.e(TAG, "- Current time: " + System.currentTimeMillis());
                                            Log.e(TAG, "- Time until expiration: " + 
                                                ((tokenExpirationTime - System.currentTimeMillis()) / 1000) + " seconds");
                                            Log.e(TAG, "Request details:");
                                            Log.e(TAG, "- URL: " + request.url());
                                            Log.e(TAG, "- Method: " + request.method());
                                            Log.e(TAG, "- Headers present: " + request.headers().toString());
                                            
                                            // Verify Authorization header format
                                            String authHeader = request.header("Authorization");
                                            if (authHeader != null) {
                                                Log.e(TAG, "- Auth header format valid: " + 
                                                    authHeader.startsWith("Bearer "));
                                                Log.e(TAG, "- Auth header length: " + authHeader.length());
                                            } else {
                                                Log.e(TAG, "- Auth header missing!");
                                            }
                                        }
                                    } else {
                                        errorMessage = "API error " + response.code() + ": " + responseBody;
                                    }
                                } catch (JSONException e) {
                                    errorMessage = "API error " + response.code() + ": " + responseBody;
                                    Log.e(TAG, "Failed to parse error response: " + e.getMessage());
                                }
                            } else {
                                errorMessage = "API error " + response.code() + " with empty response";
                            }
                            final String finalError = errorMessage;
                            Log.e(TAG, finalError);
                            mainHandler.post(() -> callback.onError(finalError));
                            return;
                        }

                        // Process successful response
                        try {
                            if (responseBody == null || responseBody.isEmpty()) {
                                throw new JSONException("Empty response body");
                            }

                            JSONObject json = new JSONObject(responseBody);
                            JSONArray tracks = json.getJSONArray("tracks");
                            List<Track> trackList = new ArrayList<>();

                            for (int i = 0; i < tracks.length(); i++) {
                                try {
                                    Track track = parseTrackJson(tracks.getJSONObject(i));
                                    track.setFetchTimestamp(System.currentTimeMillis());
                                    trackList.add(track);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing track at index " + i + ": " + e.getMessage());
                                }
                            }

                            final List<Track> finalTrackList = trackList;
                            mainHandler.post(() -> {
                                if (finalTrackList.isEmpty()) {
                                    callback.onError("No tracks found for the current mood");
                                } else {
                                    callback.onSuccess(finalTrackList);
                                }
                            });
                        } catch (JSONException e) {
                            String parseError = "Failed to parse recommendations: " + e.getMessage();
                            Log.e(TAG, parseError);
                            mainHandler.post(() -> callback.onError(parseError));
                        }
                    }
                });

            } catch (Exception e) {
                String error = "Error preparing request: " + e.getMessage();
                Log.e(TAG, error, e);
                callback.onError(error);
            }
        });
    }

    private void verifyRequestToken(Request request) {
        Log.d(TAG, "\n=== Request Token Verification ===");
        
        String authHeader = request.header("Authorization");
        if (authHeader == null) {
            Log.e(TAG, "Authorization header is missing!");
            return;
        }

        if (!authHeader.startsWith("Bearer ")) {
            Log.e(TAG, "Authorization header doesn't start with 'Bearer '");
            return;
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix
        if (token.isEmpty()) {
            Log.e(TAG, "Token is empty in Authorization header");
            return;
        }

        if (!token.equals(accessToken)) {
            Log.e(TAG, "Token in request doesn't match stored token!");
            return;
        }

        Log.d(TAG, "Request token verification passed");
        Log.d(TAG, String.format("Auth header length: %d", authHeader.length()));
        Log.d(TAG, String.format("Token format valid: %s", token.matches("^[A-Za-z0-9-._~+/]+=*$")));
    }

    private String getMoodSeedGenres(String mood) {
        String genres;
        switch (mood) {
            case "happy":
                genres = "pop,dance,happy,disco,edm";
                break;
            case "sad":
                // Using verified Spotify genres for sad mood
                genres = "acoustic,piano,indie-pop,folk,ambient";
                break;
            case "energetic":
                genres = "edm,electronic,dance,rock,work-out";
                break;
            case "relaxed":
                genres = "ambient,chill,sleep,classical,piano";
                break;
            case "focused":
                genres = "classical,study,instrumental,ambient,piano";
                break;
            case "romantic":
                genres = "r-n-b,soul,jazz,pop";
                break;
            default:
                genres = "pop,rock,indie,electronic,alternative";
        }
        
        // Validate genres before returning
        return SpotifyGenres.validateGenres(genres);
    }

    private String getMoodSeedArtists(String mood) {
        // Return artist IDs that match the mood
        switch (mood.toLowerCase()) {
            case "chill":
                return "0oSGxfWSnnOXhD2fKuz2Gy,2RQXRUsr4IW1f3mKyKsy4B"; // Nujabes, Jack Johnson
            case "energetic":
                return "7dGJo4pcD2V6oG8kP0tJRR,66CXWjxzNUsdJxJ2JdwvnR"; // Eminem, Ariana Grande
            case "happy":
                return "06HL4z0CvFAxyc27GXpf02,6eUKZXaKkcviH0Ku9w2n3V"; // Taylor Swift, Justin Bieber
            case "sad":
                return "4gzpq5DPGxSnKTe4SA8HAU,4dpARuHxo51G3z768sgnrY"; // Adele, Coldplay
            default:
                return "06HL4z0CvFAxyc27GXpf02,3WrFJ7ztbogyGnTHbHJFl2"; // Taylor Swift, The Beatles
        }
    }

    public void getFeaturedContent(Context context, boolean useCache, FeaturedContentCallback callback) {
        ensureValidToken(() -> {
            String url = SpotifyConfig.API_BASE_URL + "/browse/featured-playlists?limit=20";
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Failed to get featured content: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        
                        // Safely get playlists object
                        JSONObject playlists = json.optJSONObject("playlists");
                        if (playlists == null) {
                            Log.w(TAG, "Response doesn't contain 'playlists' key");
                            callback.onSuccess(new ArrayList<>(), new ArrayList<>()); // Return empty lists
                            return;
                        }
                        
                        // Safely get items array
                        JSONArray items = playlists.optJSONArray("items");
                        if (items == null) {
                            Log.w(TAG, "Playlists object doesn't contain 'items' array");
                            callback.onSuccess(new ArrayList<>(), new ArrayList<>()); // Return empty lists
                            return;
                        }
                        
                        List<Track> recommendedTracks = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) continue;
                            
                            try {
                                String id = item.optString("id", "");
                                String name = item.optString("name", "Unknown Playlist");
                                
                                // Safely get owner
                                JSONObject owner = item.optJSONObject("owner");
                                String creatorName = (owner != null) ? owner.optString("display_name", "Unknown Creator") : "Unknown Creator";
                                
                                // Safely get first image
                                String imageUrl = "";
                                JSONArray images = item.optJSONArray("images");
                                if (images != null && images.length() > 0) {
                                    JSONObject firstImage = images.optJSONObject(0);
                                    if (firstImage != null) {
                                        imageUrl = firstImage.optString("url", "");
                                    }
                                }
                                
                                Track track = new Track(
                                    id,
                                    name,
                                    creatorName,
                                    "Featured Playlist",
                                    imageUrl,
                                    0,
                                    "Spotify",
                                    true
                                );
                                recommendedTracks.add(track);
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing playlist item: " + e.getMessage());
                                // Continue with next item
                            }
                        }
                        callback.onSuccess(recommendedTracks, new ArrayList<>());
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse featured content: " + e.getMessage());
                        callback.onError("Failed to parse featured content: " + e.getMessage());
                    }
                }
            });
        });
    }

    public void getNewReleases(Context context, boolean useCache, SpotifyTracksCallback callback) {
        if (!isTokenValid()) {
            Log.d(TAG, "Token not valid, getting new token before fetching new releases");
            getAccessToken(new AccessTokenCallback() {
                @Override
                public void onTokenReceived(String token) {
                    fetchNewReleases(context, useCache, callback);
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "Failed to get access token: " + message);
                    mainHandler.post(() -> callback.onError("Authentication failed: " + message));
                }
            });
        } else {
            fetchNewReleases(context, useCache, callback);
        }
    }

    private void fetchNewReleases(Context context, boolean useCache, SpotifyTracksCallback callback) {
        String url = SpotifyConfig.API_BASE_URL + "/browse/new-releases?limit=20&country=US";
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to get new releases: " + e.getMessage());
                mainHandler.post(() -> callback.onError("Failed to get new releases: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        String error = response.body() != null ? response.body().string() : "Unknown error";
                        Log.e(TAG, "Error response from Spotify: " + error);
                        mainHandler.post(() -> callback.onError("Error response from Spotify: " + error));
                        return;
                    }

                    String jsonData = response.body().string();
                    JSONObject json = new JSONObject(jsonData);
                    
                    // Safely get albums object
                    JSONObject albums = json.optJSONObject("albums");
                    if (albums == null) {
                        Log.w(TAG, "Response doesn't contain 'albums' key");
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                        return;
                    }
                    
                    // Safely get items array
                    JSONArray items = albums.optJSONArray("items");
                    if (items == null) {
                        Log.w(TAG, "Albums object doesn't contain 'items' array");
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                        return;
                    }
                    
                    List<Track> tracks = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        try {
                            JSONObject albumObj = items.getJSONObject(i);
                            
                            // Get album info
                            String id = albumObj.getString("id");
                            String name = albumObj.getString("name");
                            
                            // Get artist info
                            JSONArray artists = albumObj.getJSONArray("artists");
                            String artistName = artists.length() > 0 ? 
                                artists.getJSONObject(0).getString("name") : "Unknown Artist";
                            
                            // Get album art
                            String imageUrl = "";
                            JSONArray images = albumObj.getJSONArray("images");
                            if (images.length() > 0) {
                                imageUrl = images.getJSONObject(0).getString("url");
                            }
                            
                            Track track = new Track(
                                id,
                                name,
                                artistName,
                                name,
                                imageUrl,
                                0,
                                "Spotify",
                                true
                            );
                            
                            tracks.add(track);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing album item: " + e.getMessage());
                            // Continue with next item
                        }
                    }
                    
                    if (tracks.isEmpty()) {
                        Log.w(TAG, "No tracks parsed from Spotify new releases response");
                    } else {
                        Log.d(TAG, "Successfully parsed " + tracks.size() + " new releases from Spotify");
                    }
                    mainHandler.post(() -> callback.onSuccess(tracks));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse new releases: " + e.getMessage());
                    mainHandler.post(() -> callback.onError("Failed to parse new releases: " + e.getMessage()));
                }
            }
        });
    }

    public void searchArtistTracks(String artistName, int limit, ApiDataProvider.TracksCallback callback) {
        ensureValidToken(() -> {
            String url = SpotifyConfig.API_BASE_URL + "/search?q=" + artistName + "&type=track&limit=" + limit;
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Failed to search artist tracks: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        JSONObject tracks = json.getJSONObject("tracks");
                        JSONArray items = tracks.getJSONArray("items");
                        
                        List<Track> trackList = new ArrayList<>();
                        for (int i = 0; i < Math.min(items.length(), limit); i++) {
                            trackList.add(parseTrackJson(items.getJSONObject(i)));
                        }
                        callback.onSuccess(trackList);
                    } catch (JSONException e) {
                        callback.onError("Failed to parse artist tracks: " + e.getMessage());
                    }
                }
            });
        });
    }

    public void getGenreBasedRecommendations(String genre, int limit, ApiDataProvider.TracksCallback callback) {
        ensureValidToken(() -> {
            String url = SpotifyConfig.API_BASE_URL + "/recommendations?seed_genres=" + genre + "&limit=" + limit;
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Failed to get genre recommendations: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        JSONArray tracks = json.getJSONArray("tracks");
                        
                        List<Track> trackList = new ArrayList<>();
                        for (int i = 0; i < Math.min(tracks.length(), limit); i++) {
                            trackList.add(parseTrackJson(tracks.getJSONObject(i)));
                        }
                        callback.onSuccess(trackList);
                    } catch (JSONException e) {
                        callback.onError("Failed to parse genre recommendations: " + e.getMessage());
                    }
                }
            });
        });
    }

    public void getRecentlyPlayed(int limit, ApiDataProvider.TracksCallback callback) {
        ensureValidToken(() -> {
            String url = SpotifyConfig.API_BASE_URL + "/me/player/recently-played?limit=" + limit;
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Failed to get recently played tracks: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        JSONArray items = json.getJSONArray("items");
                        
                        List<Track> tracks = new ArrayList<>();
                        for (int i = 0; i < Math.min(items.length(), limit); i++) {
                            JSONObject item = items.getJSONObject(i);
                            tracks.add(parseTrackJson(item.getJSONObject("track")));
                        }
                        callback.onSuccess(tracks);
                    } catch (JSONException e) {
                        callback.onError("Failed to parse recently played tracks: " + e.getMessage());
                    }
                }
            });
        });
    }

    public void getVariedRecommendations(String query, int limit, SpotifyRecommendationsCallback callback) {
        ensureValidToken(() -> {
            // Add randomization to get different results each time
            Random random = new Random();
            float randomVariation = (random.nextFloat() * 0.2f) - 0.1f; // -0.1 to 0.1
            int randomOffset = random.nextInt(50);

            // Build URL with proper parameters
            StringBuilder urlBuilder = new StringBuilder(BASE_API_URL + "/recommendations?");
            
            // Get seed genres and artists based on mood
            String mood = query.toLowerCase();
            String seedGenres = getMoodSeedGenres(mood);
            String seedArtists = getMoodSeedArtists(mood);
            
            // Add seed parameters
            urlBuilder.append("seed_genres=").append(seedGenres);
            urlBuilder.append("&seed_artists=").append(seedArtists);
            urlBuilder.append("&limit=").append(limit);
            urlBuilder.append("&min_popularity=25"); // Ensure somewhat popular tracks
            urlBuilder.append("&market=US");

            // Add mood-specific parameters
            switch (mood) {
                case "happy":
                    urlBuilder.append("&target_valence=").append(0.8 + randomVariation);
                    urlBuilder.append("&target_energy=").append(0.7 + randomVariation);
                    urlBuilder.append("&min_valence=0.6");
                    break;
                case "sad":
                    urlBuilder.append("&target_valence=").append(0.3 + randomVariation);
                    urlBuilder.append("&target_energy=").append(0.4 + randomVariation);
                    urlBuilder.append("&max_valence=0.5");
                    break;
                case "energetic":
                    urlBuilder.append("&target_energy=").append(0.8 + randomVariation);
                    urlBuilder.append("&min_tempo=120");
                    urlBuilder.append("&target_danceability=").append(0.7 + randomVariation);
                    break;
                case "relaxed":
                    urlBuilder.append("&target_energy=").append(0.3 + randomVariation);
                    urlBuilder.append("&target_acousticness=").append(0.7 + randomVariation);
                    urlBuilder.append("&max_tempo=100");
                    break;
                case "focused":
                    urlBuilder.append("&target_instrumentalness=").append(0.7 + randomVariation);
                    urlBuilder.append("&target_energy=").append(0.5 + randomVariation);
                    urlBuilder.append("&max_speechiness=0.3");
                    break;
                case "romantic":
                    urlBuilder.append("&target_valence=").append(0.6 + randomVariation);
                    urlBuilder.append("&target_energy=").append(0.5 + randomVariation);
                    urlBuilder.append("&target_acousticness=").append(0.6 + randomVariation);
                    break;
                default:
                    urlBuilder.append("&target_energy=").append(0.5 + randomVariation);
                    urlBuilder.append("&target_valence=").append(0.5 + randomVariation);
            }
            
            // Add timestamp and offset for variety
            urlBuilder.append("&timestamp=").append(System.currentTimeMillis());
            urlBuilder.append("&offset=").append(randomOffset);
            
            String url = urlBuilder.toString();
            Log.d(TAG, "Getting varied recommendations with URL: " + url);
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to get recommendations: " + e.getMessage());
                    mainHandler.post(() -> callback.onError("Failed to get recommendations: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            String errorBody = response.body().string();
                            Log.e(TAG, "API error: " + response.code() + " - " + errorBody);
                            mainHandler.post(() -> callback.onError("API error: " + response.code()));
                            return;
                        }

                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);

                        if (!json.has("tracks")) {
                            Log.e(TAG, "Response doesn't contain tracks");
                            mainHandler.post(() -> callback.onError("Invalid response format"));
                            return;
                        }

                        JSONArray tracks = json.getJSONArray("tracks");
                        List<Track> trackList = new ArrayList<>();

                        for (int i = 0; i < tracks.length(); i++) {
                            try {
                                Track track = parseTrackJson(tracks.getJSONObject(i));
                                track.setFetchTimestamp(System.currentTimeMillis());
                                trackList.add(track);
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing track at index " + i + ": " + e.getMessage());
                                // Continue with next track
                            }
                        }

                        // Add randomization
                        if (trackList.size() > 1) {
                            Collections.shuffle(trackList);
                        }

                        Log.d(TAG, "Successfully fetched " + trackList.size() + " tracks");
                        mainHandler.post(() -> callback.onSuccess(trackList));
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing recommendations response: " + e.getMessage());
                        mainHandler.post(() -> callback.onError("Error parsing recommendations: " + e.getMessage()));
                    }
                }
            });
        });
    }

    public void fetchAlbumContent(String albumId, final TracksCallback callback) {
        if (albumId == null || albumId.isEmpty()) {
            callback.onError("Invalid album ID");
            return;
        }

        ensureValidToken(() -> {
            String url = SpotifyConfig.API_BASE_URL + "/albums/" + albumId + "/tracks";
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Failed to fetch album content: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        JSONArray items = json.getJSONArray("items");
                        
                        List<Track> tracks = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            tracks.add(parseTrackJson(items.getJSONObject(i)));
                        }
                        callback.onSuccess(tracks);
                    } catch (JSONException e) {
                        callback.onError("Failed to parse album content: " + e.getMessage());
                    }
                }
            });
        });
    }

    public List<Track> parseTracksFromResponse(String response) {
        List<Track> tracks = new ArrayList<>();
        
        if (response == null || response.isEmpty()) {
            return tracks;
        }
        
        try {
            JSONObject jsonResponse = new JSONObject(response);
            
            // Check for tracks list
            if (jsonResponse.has("tracks")) {
                JSONObject tracksObject = jsonResponse.getJSONObject("tracks");
                JSONArray items = tracksObject.getJSONArray("items");
                
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    Track track = parseTrackFromJson(item);
                    if (track != null) {
                        tracks.add(track);
                    }
                }
            } else if (jsonResponse.has("items")) {
                // Direct items array for playlist tracks
                JSONArray items = jsonResponse.getJSONArray("items");
                
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    // For playlists, the track is in a nested "track" object
                    if (item.has("track")) {
                        JSONObject trackObject = item.getJSONObject("track");
                        Track track = parseTrackFromJson(trackObject);
                        if (track != null) {
                            tracks.add(track);
                        }
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
            String title = json.getString("name");
            
            // Get artist info
            JSONArray artists = json.getJSONArray("artists");
            StringBuilder artistName = new StringBuilder();
            for (int j = 0; j < artists.length(); j++) {
                if (j > 0) artistName.append(", ");
                artistName.append(artists.getJSONObject(j).getString("name"));
            }
            
            // Get album info
            JSONObject album = json.getJSONObject("album");
            String albumName = album.getString("name");
            
            // Get album art
            String albumArtUrl = "";
            JSONArray images = album.getJSONArray("images");
            if (images.length() > 0) {
                albumArtUrl = images.getJSONObject(0).getString("url");
            }
            
            // Get duration
            int durationMs = json.getInt("duration_ms");
            
            // Get preview URL if available
            String previewUrl = json.optString("preview_url", "");
            
            // Create track
            Track track = new Track(id, title, artistName.toString(), albumName, albumArtUrl, durationMs, previewUrl, true);
            track.setSource(Track.SOURCE_SPOTIFY);
            track.setSpotifyId(id);
            
            return track;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing track: " + e.getMessage());
            return null;
        }
    }
    
    public void getPreview(String trackId, SpotifyCallback callback) {
        if (!isAuthenticated()) {
            authenticate(() -> getPreview(trackId, callback));
            return;
        }
        
        String url = "https://api.spotify.com/v1/tracks/" + trackId;
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
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
                    String previewUrl = track.optString("preview_url", "");
                    
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
    
    public void getArtistTracks(String artistId, SpotifyCallback callback) {
        if (!isAuthenticated()) {
            authenticate(() -> getArtistTracks(artistId, callback));
            return;
        }
        
        String url = "https://api.spotify.com/v1/artists/" + artistId + "/top-tracks?market=US";
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
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
    
    public void getPlaylist(String playlistId, SpotifyCallback callback) {
        if (!isAuthenticated()) {
            authenticate(() -> getPlaylist(playlistId, callback));
            return;
        }
        
        String url = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks";
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
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
    
    public void getPlaylists(SpotifyCallback callback) {
        if (!isAuthenticated()) {
            authenticate(() -> getPlaylists(callback));
            return;
        }
        
        String url = "https://api.spotify.com/v1/me/playlists";
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
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
        getPlaylists(new SpotifyCallback() {
            @Override
            public void onSuccess(String result) {
                try {
                    // Parse the JSON result into a list of tracks
                    JSONObject jsonResponse = new JSONObject(result);
                    JSONArray itemsArray = jsonResponse.getJSONArray("items");
                    List<Track> tracks = new ArrayList<>();

                    for (int i = 0; i < itemsArray.length(); i++) {
                        JSONObject playlist = itemsArray.getJSONObject(i);
                        String name = playlist.getString("name");
                        String id = playlist.getString("id");
                        String imageUrl = "";
                        
                        // Get the image if available
                        JSONArray images = playlist.getJSONArray("images");
                        if (images.length() > 0) {
                            imageUrl = images.getJSONObject(0).getString("url");
                        }
                        
                        // Create a track representation of the playlist
                        Track playlistTrack = new Track();
                        playlistTrack.setId(id);
                        playlistTrack.setTitle(name);
                        playlistTrack.setAlbumArtUrl(imageUrl);
                        playlistTrack.setSource(Track.SOURCE_SPOTIFY);
                        
                        tracks.add(playlistTrack);
                    }
                    
                    callback.onSuccess(tracks);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing playlists: " + e.getMessage());
                    callback.onError("Error parsing playlists: " + e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void getRecommendedTracks(String query, int limit, SpotifyRecommendationsCallback callback) {
        ensureValidToken(() -> {
            String url;
            
            // Check if this is a general recommendation request
            if (query == null || query.isEmpty() || query.equalsIgnoreCase("all")) {
                // For general recommendations, use a variety of random seed genres to ensure variety
                String[] popularGenres = {"pop", "rock", "hip-hop", "r-n-b", "indie", "electronic", 
                                        "jazz", "country", "classical", "metal", "dance", "soul"};
                
                // Select random genres
                int genreCount = 2 + (int)(Math.random() * 3); // 2-4 genres
                StringBuilder genresBuilder = new StringBuilder();
                Set<Integer> selectedIndices = new HashSet<>();
                
                while (selectedIndices.size() < genreCount) {
                    int index = (int)(Math.random() * popularGenres.length);
                    if (selectedIndices.add(index)) {
                        if (genresBuilder.length() > 0) {
                            genresBuilder.append(",");
                        }
                        genresBuilder.append(popularGenres[index]);
                    }
                }
                
                // Validate the selected genres
                String validatedGenres = SpotifyGenres.validateGenres(genresBuilder.toString());
                
                // Build URL with validated genres
                HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_API_URL + "/recommendations").newBuilder();
                urlBuilder.addQueryParameter("seed_genres", validatedGenres);
                urlBuilder.addQueryParameter("limit", String.valueOf(limit));
                urlBuilder.addQueryParameter("market", "US");
                urlBuilder.addQueryParameter("min_popularity", "25");
                
                url = urlBuilder.build().toString();
            } else {
                // For specific query, validate the genres first
                String validatedGenres = SpotifyGenres.validateGenres(query);
                
                // Build URL with validated genres
                HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_API_URL + "/recommendations").newBuilder();
                urlBuilder.addQueryParameter("seed_genres", validatedGenres);
                urlBuilder.addQueryParameter("limit", String.valueOf(limit));
                urlBuilder.addQueryParameter("market", "US");
                urlBuilder.addQueryParameter("min_popularity", "25");
                
                url = urlBuilder.build().toString();
            }

            // Make the API request
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String errorMsg = "Failed to get recommendations: " + e.getMessage();
                    Log.e(TAG, errorMsg);
                    mainHandler.post(() -> callback.onError(errorMsg));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        JSONArray tracks = json.getJSONArray("tracks");
                        List<Track> recommendedTracks = parseTracksFromArray(tracks);
                        mainHandler.post(() -> callback.onSuccess(recommendedTracks));
                    } catch (Exception e) {
                        String errorMsg = "Error parsing recommendations: " + e.getMessage();
                        Log.e(TAG, errorMsg);
                        mainHandler.post(() -> callback.onError(errorMsg));
                    }
                }
            });
        });
    }

    private void makeSpotifyApiCall(String url, SpotifyRecommendationsCallback callback) {
        // First check if we have a valid token
        if (!isTokenValid()) {
            Log.w(TAG, "Access token is missing or expired, attempting to refresh...");
            getAccessToken(new AccessTokenCallback() {
                @Override
                public void onTokenReceived(String token) {
                    // Token refreshed, retry the API call
                    makeApiCallWithToken(url, token, callback);
                }

                @Override
                public void onError(String message) {
                    String errorMsg = "Unable to get access token: " + message;
                    Log.e(TAG, errorMsg);
                    mainHandler.post(() -> callback.onError("Please try again later. Authentication failed."));
                }
            });
            return;
        }

        // We have a valid token, make the API call
        makeApiCallWithToken(url, accessToken, callback);
    }

    private void makeApiCallWithToken(String url, String token, SpotifyRecommendationsCallback callback) {
        try {
            // Validate token before creating request
            if (token == null || token.trim().isEmpty()) {
                String errorMsg = "Cannot make API call - token is null or empty";
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onError("Authentication error. Please try again later."));
                return;
            }

            // Create request with proper authorization
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/json")
                .build();

            // Log request details (but only show first few chars of token)
            Log.d(TAG, "Making Spotify API call to: " + url);
            Log.d(TAG, "Using token: " + token.substring(0, Math.min(10, token.length())) + "...");

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String errorMsg = "Network error: " + e.getMessage();
                    Log.e(TAG, errorMsg);
                    mainHandler.post(() -> callback.onError("Unable to connect to Spotify. Please check your internet connection."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : null;
                    
                    // Log response details
                    Log.d(TAG, String.format("Spotify API Response - Code: %d", response.code()));
                    if (!response.isSuccessful()) {
                        Log.d(TAG, "Error response body: " + responseBody);
                    }

                    try {
                        if (response.code() == 401) {
                            // Token is invalid or expired
                            Log.e(TAG, "Authorization failed: " + responseBody);
                            // Clear the token to force a refresh
                            accessToken = null;
                            tokenExpirationTime = 0;
                            mainHandler.post(() -> callback.onError("Session expired. Please try again."));
                            return;
                        }

                        if (!response.isSuccessful()) {
                            String errorMessage = "Unknown error";
                            if (responseBody != null && !responseBody.isEmpty()) {
                                try {
                                    JSONObject error = new JSONObject(responseBody)
                                        .getJSONObject("error");
                                    errorMessage = error.getString("message");
                                } catch (JSONException e) {
                                    errorMessage = responseBody;
                                }
                            }
                            String errorMsg = String.format("API error %d: %s", response.code(), errorMessage);
                            Log.e(TAG, errorMsg);
                            mainHandler.post(() -> callback.onError("Unable to get recommendations. Please try again later."));
                            return;
                        }

                        // Only process response if we have a successful status code and non-empty body
                        if (responseBody != null && !responseBody.isEmpty()) {
                            processSuccessfulResponse(responseBody, callback);
                        } else {
                            Log.e(TAG, "Empty response body from Spotify API");
                            mainHandler.post(() -> callback.onError("No recommendations available at this time."));
                        }
                    } catch (Exception e) {
                        String errorMsg = "Error processing response: " + e.getMessage();
                        Log.e(TAG, errorMsg, e);
                        mainHandler.post(() -> callback.onError("Unable to process recommendations. Please try again later."));
                    }
                }
            });
        } catch (Exception e) {
            String errorMsg = "Error creating request: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            mainHandler.post(() -> callback.onError("Unable to make request. Please try again later."));
        }
    }

    private void processSuccessfulResponse(String responseBody, SpotifyRecommendationsCallback callback) throws JSONException {
        JSONObject json = new JSONObject(responseBody);
                        if (!json.has("tracks")) {
            Log.e(TAG, "Response missing tracks array: " + responseBody);
            mainHandler.post(() -> callback.onError("No recommendations available at this time."));
                            return;
                        }

                        JSONArray tracks = json.getJSONArray("tracks");
                        List<Track> trackList = new ArrayList<>();

                        for (int i = 0; i < tracks.length(); i++) {
                            try {
                                Track track = parseTrackJson(tracks.getJSONObject(i));
                                track.setFetchTimestamp(System.currentTimeMillis());
                                trackList.add(track);
                            } catch (Exception e) {
                Log.w(TAG, "Error parsing track at index " + i + ": " + e.getMessage());
                                // Continue with next track
                            }
                        }

        if (trackList.isEmpty()) {
            Log.w(TAG, "No valid tracks found in response");
            mainHandler.post(() -> callback.onError("No recommendations available at this time."));
            return;
        }

        // Randomize results if we have multiple tracks
                        if (trackList.size() > 1) {
                            Collections.shuffle(trackList);
                        }

                        Log.d(TAG, "Successfully fetched " + trackList.size() + " tracks");
                        mainHandler.post(() -> callback.onSuccess(trackList));
    }

    /**
     * Get personalized recommendations based on user's listening history, mood and preferences
     * 
     * @param mood The current user mood (can be null for no mood filtering)
     * @param limit Maximum number of tracks to return
     * @param seedTracks Optional list of seed track IDs
     * @param seedArtists Optional list of seed artist IDs
     * @param seedGenres Optional list of seed genres
     * @param callback Callback to handle the results
     */
    public void getPersonalizedRecommendations(String mood, int limit, 
                                            List<String> seedTracks, 
                                            List<String> seedArtists, 
                                            List<String> seedGenres,
                                            SpotifyRecommendationsCallback callback) {
        ensureValidToken(() -> {
            // Build dynamic recommendation parameters based on available seeds
            StringBuilder urlBuilder = new StringBuilder(BASE_API_URL + "/recommendations?limit=" + limit);
            
            // Add seed tracks if available (up to 5 max according to Spotify API)
            if (seedTracks != null && !seedTracks.isEmpty()) {
                String tracks = String.join(",", seedTracks.subList(0, Math.min(seedTracks.size(), 2)));
                urlBuilder.append("&seed_tracks=").append(tracks);
            }
            
            // Add seed artists if available
            if (seedArtists != null && !seedArtists.isEmpty()) {
                String artists = String.join(",", seedArtists.subList(0, Math.min(seedArtists.size(), 2)));
                urlBuilder.append("&seed_artists=").append(artists);
            }
            
            // Add seed genres if available
            if (seedGenres != null && !seedGenres.isEmpty()) {
                String genres = String.join(",", seedGenres.subList(0, Math.min(seedGenres.size(), 2)));
                urlBuilder.append("&seed_genres=").append(genres);
            }
            
            // Add mood-based parameters if a mood is specified
            if (mood != null && !mood.isEmpty()) {
                switch (mood.toLowerCase()) {
                    case "happy":
                        urlBuilder.append("&min_valence=0.7&target_energy=0.8&min_tempo=100");
                        break;
                    case "sad":
                        urlBuilder.append("&max_valence=0.3&target_energy=0.4&max_tempo=100");
                        break;
                    case "energetic":
                        urlBuilder.append("&min_energy=0.8&target_tempo=120&min_danceability=0.6");
                        break;
                    case "relaxed":
                        urlBuilder.append("&max_energy=0.4&target_acousticness=0.8&max_tempo=90");
                        break;
                    case "focused":
                        urlBuilder.append("&target_instrumentalness=0.6&max_speechiness=0.2&target_energy=0.5");
                        break;
                    default:
                        // If an unknown mood, just add a reasonable default
                        urlBuilder.append("&target_energy=0.6&target_valence=0.5");
                        break;
                }
            }
            
            // Make the API call
            String url = urlBuilder.toString();
            Log.d(TAG, "Personalized recommendations URL: " + url);
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();
                
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Failed to get recommendations: " + e.getMessage()));
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            String errorMsg = "API error: " + response.code() + " - " + response.message();
                            Log.e(TAG, errorMsg);
                            mainHandler.post(() -> callback.onError(errorMsg));
                            return;
                        }
                        
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        JSONArray tracks = json.getJSONArray("tracks");
                        
                        List<Track> trackList = new ArrayList<>();
                        for (int i = 0; i < tracks.length(); i++) {
                            trackList.add(parseTrackJson(tracks.getJSONObject(i)));
                        }
                        
                        // Cache the recommendations
                        if (cachingEnabled) {
                            // Cache implementation would go here
                            // This would allow for offline viewing of recommendations
                        }
                        
                        mainHandler.post(() -> callback.onSuccess(trackList));
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse recommendation results: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Failed to parse recommendation results: " + e.getMessage()));
                    }
                }
            });
        });
    }

    /**
     * Get seed values for recommendations based on user's listening history
     * 
     * @param callback Callback with seed values
     */
    public void getRecommendationSeeds(FirebaseUser user, RecommendationSeedsCallback callback) {
        // Get user's recent history
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        if (user == null) {
            // Return empty seeds if no user
            callback.onSuccess(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            return;
        }
        
        db.collection("users").document(user.getUid())
          .collection("history")
          .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .limit(10)
          .get()
          .addOnSuccessListener(querySnapshot -> {
              List<String> seedTracks = new ArrayList<>();
              List<String> seedArtists = new ArrayList<>();
              Set<String> artistIds = new HashSet<>();
              
              for (QueryDocumentSnapshot doc : querySnapshot) {
                  String trackId = doc.getString("trackId");
                  String artistId = doc.getString("artistId");
                  
                  if (trackId != null && !trackId.isEmpty() && seedTracks.size() < 3) {
                      seedTracks.add(trackId);
                  }
                  
                  if (artistId != null && !artistId.isEmpty() && !artistIds.contains(artistId)) {
                      artistIds.add(artistId);
                      if (seedArtists.size() < 2) {
                          seedArtists.add(artistId);
                      }
                  }
              }
              
              // Add some genres based on user preference
              List<String> seedGenres = new ArrayList<>();
              db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        List<String> userGenres = (List<String>) userDoc.get("preferredGenres");
                        if (userGenres != null && !userGenres.isEmpty()) {
                            for (String genre : userGenres) {
                                if (seedGenres.size() < 2) {
                                    seedGenres.add(genre);
                                }
                            }
                        }
                    }
                    
                    // Ensure we have at least some seeds
                    if (seedTracks.isEmpty() && seedArtists.isEmpty() && seedGenres.isEmpty()) {
                        // Use a default seed genre
                        seedGenres.add("pop");
                    }
                    
                    callback.onSuccess(seedTracks, seedArtists, seedGenres);
                })
                .addOnFailureListener(e -> {
                    // If we fail getting genres, just return what we have
                    callback.onSuccess(seedTracks, seedArtists, new ArrayList<>());
                });
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Error getting user history for recommendation seeds", e);
              // Return empty seeds on failure
              callback.onSuccess(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
          });
    }
    
    /**
     * Callback for recommendation seeds
     */
    public interface RecommendationSeedsCallback {
        void onSuccess(List<String> seedTracks, List<String> seedArtists, List<String> seedGenres);
    }

    /**
     * Get featured playlists with caching and pagination
     * 
     * @param offset The offset for pagination
     * @param limit Maximum number of playlists to return
     * @param callback Callback with playlists
     */
    public void getFeaturedPlaylistsWithPagination(int offset, int limit, SpotifyPlaylistsCallback callback) {
        ensureValidToken(() -> {
            String url = BASE_API_URL + "/browse/featured-playlists?offset=" + offset + "&limit=" + limit + "&country=MY";
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();
                
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Failed to get featured playlists: " + e.getMessage()));
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            String errorMsg = "API error: " + response.code() + " - " + response.message();
                            Log.e(TAG, errorMsg);
                            mainHandler.post(() -> callback.onError(errorMsg));
                            return;
                        }
                        
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        JSONObject playlists = json.getJSONObject("playlists");
                        JSONArray items = playlists.getJSONArray("items");
                        
                        List<Playlist> playlistList = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject playlistItem = items.getJSONObject(i);
                            Playlist playlist = new Playlist();
                            playlist.setId("spotify:playlist:" + playlistItem.getString("id"));
                            playlist.setName(playlistItem.getString("name"));
                            
                            // Get the description
                            if (playlistItem.has("description")) {
                                playlist.setDescription(playlistItem.getString("description"));
                            }
                            
                            // Get the image URL
                            JSONArray images = playlistItem.getJSONArray("images");
                            if (images.length() > 0) {
                                JSONObject image = images.getJSONObject(0);
                                playlist.setImageUrl(image.getString("url"));
                            }
                            
                            // Get the track count
                            if (playlistItem.has("tracks")) {
                                JSONObject tracks = playlistItem.getJSONObject("tracks");
                                if (tracks.has("total")) {
                                    playlist.setTrackCount(tracks.getInt("total"));
                                }
                            }
                            
                            playlistList.add(playlist);
                        }
                        
                        // Cache the playlists if needed
                        if (cachingEnabled) {
                            // Cache implementation would go here
                        }
                        
                        mainHandler.post(() -> callback.onSuccess(playlistList));
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse featured playlists: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Failed to parse featured playlists: " + e.getMessage()));
                    }
                }
            });
        });
    }

    /**
     * Get new releases with pagination and filtering
     * 
     * @param offset The offset for pagination
     * @param limit Maximum number of releases to return
     * @param genres Optional list of genres to filter by
     * @param callback Callback with tracks
     */
    public void getNewReleasesWithFiltering(int offset, int limit, List<String> genres, SpotifyTracksCallback callback) {
        ensureValidToken(() -> {
            String url = BASE_API_URL + "/browse/new-releases?offset=" + offset + "&limit=" + limit + "&country=MY";
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();
                
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Failed to get new releases: " + e.getMessage()));
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            String errorMsg = "API error: " + response.code() + " - " + response.message();
                            Log.e(TAG, errorMsg);
                            mainHandler.post(() -> callback.onError(errorMsg));
                            return;
                        }
                        
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        JSONObject albums = json.getJSONObject("albums");
                        JSONArray items = albums.getJSONArray("items");
                        
                        List<Track> trackList = new ArrayList<>();
                        List<String> albumIds = new ArrayList<>();
                        
                        // Extract album IDs first
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject album = items.getJSONObject(i);
                            albumIds.add(album.getString("id"));
                        }
                        
                        // Process the albums in batches of 5 to avoid hitting rate limits
                        processAlbumsInBatches(albumIds, genres, trackList, callback);
                        
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse new releases: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Failed to parse new releases: " + e.getMessage()));
                    }
                }
            });
        });
    }
    
    /**
     * Process albums in batches to get tracks
     */
    private void processAlbumsInBatches(List<String> albumIds, List<String> genres, 
                                       List<Track> trackList, SpotifyTracksCallback callback) {
        // Base case for recursion
        if (albumIds.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(trackList));
            return;
        }
        
        // Take up to 5 albums for this batch
        int batchSize = Math.min(5, albumIds.size());
        List<String> batch = albumIds.subList(0, batchSize);
        List<String> remaining = albumIds.subList(batchSize, albumIds.size());
        
        // Create a String of comma-separated album IDs
        String albumIdsParam = String.join(",", batch);
        String url = BASE_API_URL + "/albums?ids=" + albumIdsParam;
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // On failure, just continue with remaining albums
                processAlbumsInBatches(remaining, genres, trackList, callback);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        // On error, just continue with remaining albums
                        processAlbumsInBatches(remaining, genres, trackList, callback);
                        return;
                    }
                    
                    String jsonData = response.body().string();
                    JSONObject json = new JSONObject(jsonData);
                    JSONArray albums = json.getJSONArray("albums");
                    
                    for (int i = 0; i < albums.length(); i++) {
                        JSONObject album = albums.getJSONObject(i);
                        
                        // Check if album matches any of the requested genres (if specified)
                        if (genres != null && !genres.isEmpty()) {
                            JSONArray albumGenres = album.optJSONArray("genres");
                            boolean matchesGenre = false;
                            
                            if (albumGenres != null) {
                                for (int j = 0; j < albumGenres.length(); j++) {
                                    String genre = albumGenres.getString(j).toLowerCase();
                                    for (String requestedGenre : genres) {
                                        if (genre.contains(requestedGenre.toLowerCase())) {
                                            matchesGenre = true;
                                            break;
                                        }
                                    }
                                    if (matchesGenre) break;
                                }
                            }
                            
                            if (!matchesGenre) continue; // Skip this album if it doesn't match genres
                        }
                        
                        // Process tracks from this album
                        JSONObject tracks = album.getJSONObject("tracks");
                        JSONArray items = tracks.getJSONArray("items");
                        
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject trackItem = items.getJSONObject(j);
                            Track track = parseTrackJson(trackItem);
                            
                            // Add album information to the track
                            track.setAlbumName(album.getString("name"));
                            
                            // Get the album image
                            JSONArray images = album.getJSONArray("images");
                            if (images.length() > 0) {
                                track.setImageUrl(images.getJSONObject(0).getString("url"));
                            }
                            
                            trackList.add(track);
                        }
                    }
                    
                    // Process the next batch
                    processAlbumsInBatches(remaining, genres, trackList, callback);
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse album data: " + e.getMessage(), e);
                    // On error, just continue with remaining albums
                    processAlbumsInBatches(remaining, genres, trackList, callback);
                }
            }
        });
    }

    /**
     * Parse tracks from a JSONArray
     * @param tracksArray JSONArray containing track objects
     * @return List of Track objects
     */
    private List<Track> parseTracksFromArray(JSONArray tracksArray) throws JSONException {
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < tracksArray.length(); i++) {
            try {
                JSONObject trackJson = tracksArray.getJSONObject(i);
                Track track = parseTrackJson(trackJson);
                if (track != null) {
                    tracks.add(track);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing track at index " + i + ": " + e.getMessage());
                // Continue with next track
            }
        }
        return tracks;
    }

    /**
     * Check if already authenticated with valid token
     */
    public boolean isAuthenticated() {
        return isTokenValid();
    }

    /**
     * Authenticate and then run callback
     */
    public void authenticate(Runnable callback) {
        if (isTokenValid()) {
            // Already authenticated, run callback directly
            callback.run();
            return;
        }

        // Need to get new token
        Log.d(TAG, "Getting new access token for authentication...");
        getAccessToken(new AccessTokenCallback() {
            @Override
            public void onTokenReceived(String token) {
                if (token == null || token.trim().isEmpty()) {
                    Log.e(TAG, "Authentication failed: received null or empty token");
                    return;
                }
                Log.d(TAG, "Successfully authenticated with new token");
                callback.run();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Authentication failed: " + message);
                // Cannot run callback if authentication failed
            }
        });
    }
}