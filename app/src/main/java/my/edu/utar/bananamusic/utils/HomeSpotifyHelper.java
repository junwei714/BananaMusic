package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Helper class dedicated to fetching music content from Spotify for the Home Fragment
 */
public class HomeSpotifyHelper {
    private static final String TAG = "HomeSpotifyHelper";
    private static HomeSpotifyHelper instance;
    private SpotifyHelper spotifyHelper;
    private String accessToken;

    private HomeSpotifyHelper(Context context) {
        this.spotifyHelper = SpotifyHelper.getInstance(context);
    }

    public static synchronized HomeSpotifyHelper getInstance(Context context) {
        if (instance == null) {
            instance = new HomeSpotifyHelper(context);
        }
        return instance;
    }

    /**
     * Interface for recommended songs callback
     */
    public interface RecommendedSongsCallback {
        void onSuccess(List<Track> tracks);
        void onError(String errorMessage);
    }

    /**
     * Interface for trending playlists callback
     */
    public interface TrendingPlaylistsCallback {
        void onSuccess(List<Playlist> playlists);
        void onError(String errorMessage);
    }

    /**
     * Interface for callbacks when playlists are loaded
     */
    public interface PlaylistsCallback {
        /**
         * Called when playlists are successfully loaded
         * @param playlists The loaded playlists
         */
        void onSuccess(List<Playlist> playlists);
        
        /**
         * Called when an error occurs loading playlists
         * @param errorMessage The error message
         */
        void onError(String errorMessage);
    }

    /**
     * Interface for API request callbacks
     */
    public interface ApiRequestCallback {
        /**
         * Called when the request succeeds
         * @param response The JSON response
         */
        void onSuccess(JSONObject response);
        
        /**
         * Called when the request fails
         * @param errorMessage The error message
         */
        void onError(String errorMessage);
    }

    /**
     * Get personalized recommendations from Spotify based on user preferences
     *
     * @param limit Number of recommendations to fetch
     * @param callback Callback to handle the result
     */
    public void getPersonalizedRecommendations(int limit, RecommendedSongsCallback callback) {
        // First get a valid access token
        spotifyHelper.getAccessToken(new SpotifyHelper.AccessTokenCallback() {
            @Override
            public void onTokenReceived(String token) {
                // Build the recommendations URL with seed tracks, artists, or genres
                String url = "https://api.spotify.com/v1/recommendations?limit=" + limit;
                
                // Add seed genres for better recommendations
                url += "&seed_genres=pop,rock,hip-hop,electronic,r-n-b";
                
                // Add audio features for more targeted recommendations
                url += "&target_energy=0.7&target_danceability=0.8&min_popularity=70";
                
                // Make the API request
                spotifyHelper.getRecommendationsFromUrl(url, new SpotifyHelper.SpotifyRecommendationsCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        // Process the tracks and return them via callback
                        callback.onSuccess(tracks);
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Error fetching personalized recommendations: " + message);
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to get Spotify access token: " + message);
                callback.onError("Authentication error: " + message);
            }
        });
    }

    /**
     * Get trending playlists from Spotify's featured playlists
     *
     * @param callback Callback to handle the result
     */
    public void getTrendingPlaylists(TrendingPlaylistsCallback callback) {
        spotifyHelper.getFeaturedPlaylists(new SpotifyHelper.SpotifyPlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error fetching trending playlists: " + message);
                callback.onError(message);
            }
        });
    }
    
    /**
     * Helper method to set playlist image URL
     * This is used since the Playlist class doesn't have a setImageUrl method
     */
    private void setPlaylistImage(Playlist playlist, String imageUrl) {
        try {
            // Using reflection to set the field directly if the method doesn't exist
            java.lang.reflect.Field field = playlist.getClass().getDeclaredField("coverImageUrl");
            field.setAccessible(true);
            field.set(playlist, imageUrl);
        } catch (Exception e) {
            Log.e(TAG, "Error setting playlist image URL: " + e.getMessage());
            // Fallback - try to use the public field or getter/setter if available
            try {
                playlist.setCoverImageUrl(imageUrl);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to set playlist image URL: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Helper method to set playlist owner name
     * This is used since the Playlist class doesn't have a setOwnerName method
     */
    private void setPlaylistOwner(Playlist playlist, String ownerName) {
        try {
            // Using reflection to set the field directly if the method doesn't exist
            java.lang.reflect.Field field = playlist.getClass().getDeclaredField("creatorName");
            field.setAccessible(true);
            field.set(playlist, ownerName);
        } catch (Exception e) {
            Log.e(TAG, "Error setting playlist creator name: " + e.getMessage());
            // Fallback - try to use the public field or getter/setter if available
            try {
                playlist.setCreatorName(ownerName);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to set playlist creator name: " + ex.getMessage());
            }
        }
    }

    /**
     * Get featured playlists from Spotify
     * @param callback Callback for results
     */
    public void getFeaturedPlaylists(final PlaylistsCallback callback) {
        try {
            Log.d(TAG, "Getting featured playlists from Spotify");
            
            // First get a valid token
            spotifyHelper.getAccessToken(new SpotifyHelper.AccessTokenCallback() {
                @Override
                public void onTokenReceived(String token) {
                    // Now use the token to get featured playlists
                    String url = "https://api.spotify.com/v1/browse/featured-playlists?limit=10";
                    
                    // Create headers with auth token
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + token);
                    
                    // Make the request
                    apiRequest(url, headers, new ApiRequestCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            try {
                                Log.d(TAG, "Got featured playlists response");
                                List<Playlist> playlists = parsePlaylistsFromResponse(response);
                                callback.onSuccess(playlists);
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing featured playlists", e);
                                callback.onError("Error parsing featured playlists: " + e.getMessage());
                            }
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Error getting featured playlists: " + errorMessage);
                            callback.onError(errorMessage);
                        }
                    });
                }
                
                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error getting Spotify token for featured playlists: " + errorMessage);
                    callback.onError(errorMessage);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in getFeaturedPlaylists", e);
            callback.onError("Error getting featured playlists: " + e.getMessage());
        }
    }
    
    /**
     * Parse playlists from a Spotify API response
     */
    private List<Playlist> parsePlaylistsFromResponse(JSONObject response) throws JSONException {
        List<Playlist> playlists = new ArrayList<>();
        
        // Extract the playlists object
        JSONObject playlistsObj = response.getJSONObject("playlists");
        JSONArray items = playlistsObj.getJSONArray("items");
        
        // Parse each playlist
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            
            String id = item.getString("id");
            String name = item.getString("name");
            String description = item.optString("description", "");
            
            // Get the owner name
            String ownerName = "Spotify";
            if (item.has("owner") && !item.isNull("owner")) {
                JSONObject owner = item.getJSONObject("owner");
                ownerName = owner.optString("display_name", "Spotify");
            }
            
            // Get the image URL
            String imageUrl = "";
            if (item.has("images") && !item.isNull("images")) {
                JSONArray images = item.getJSONArray("images");
                if (images.length() > 0) {
                    JSONObject image = images.getJSONObject(0);
                    imageUrl = image.getString("url");
                }
            }
            
            // Create the playlist
            Playlist playlist = new Playlist();
            playlist.setPlaylistId(id);
            playlist.setName(name);
            playlist.setDescription(description);
            playlist.setCreatorName(ownerName);
            playlist.setCoverImageUrl(imageUrl);
            playlist.setSource("Spotify");
            
            playlists.add(playlist);
        }
        
        return playlists;
    }

    /**
     * Make an API request with headers
     * @param url The URL to request
     * @param headers The headers to include
     * @param callback The callback for response
     */
    private void apiRequest(String url, Map<String, String> headers, ApiRequestCallback callback) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
                
            Request.Builder requestBuilder = new Request.Builder()
                .url(url);
                
            // Add headers if provided
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            
            Request request = requestBuilder.build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "API request failed: " + e.getMessage());
                    callback.onError("Network error: " + e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            String errorMsg = "API error: " + response.code();
                            Log.e(TAG, errorMsg);
                            callback.onError(errorMsg);
                            return;
                        }
                        
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        callback.onSuccess(jsonResponse);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing API response: " + e.getMessage());
                        callback.onError("Error parsing response: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error making API request: " + e.getMessage());
            callback.onError("Error: " + e.getMessage());
        }
    }

    private void refreshToken() {
        spotifyHelper.getAccessToken(new SpotifyHelper.AccessTokenCallback() {
            @Override
            public void onTokenReceived(String token) {
                accessToken = token;
                // Handle token received
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting access token: " + message);
            }
        });
    }
} 