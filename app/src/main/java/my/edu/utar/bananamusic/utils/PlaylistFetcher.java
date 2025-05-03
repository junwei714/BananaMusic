package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Utility class to fetch playlists from various sources
 */
public class PlaylistFetcher {
    private static final String TAG = "PlaylistFetcher";
    
    private final Context context;
    private final SpotifyHelper spotifyHelper;
    private final PlaylistManager playlistManager;
    private final OkHttpClient httpClient;
    
    /**
     * Callback interface for playlist operations
     */
    public interface PlaylistFetchCallback {
        void onSuccess(List<Playlist> playlists);
        void onError(String errorMessage);
    }
    
    /**
     * Callback interface for single playlist
     */
    public interface SinglePlaylistCallback {
        void onSuccess(Playlist playlist);
        void onError(String errorMessage);
    }
    
    /**
     * Constructor
     * @param context Application context
     */
    public PlaylistFetcher(Context context) {
        this.context = context.getApplicationContext();
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.playlistManager = PlaylistManager.getInstance(context);
        this.httpClient = new OkHttpClient();
    }
    
    /**
     * Fetch trending playlists from Spotify
     * @param callback Callback for result
     */
    public void fetchTrendingPlaylists(PlaylistFetchCallback callback) {
        spotifyHelper.getFeaturedPlaylists(new SpotifyHelper.SpotifyPlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlistsList) {
                try {
                    // Add all playlists to the main list
                    callback.onSuccess(playlistsList);
                } catch (Exception e) {
                    Log.e("PlaylistFetcher", "Error handling Spotify playlists: " + e.getMessage());
                    callback.onError("Error handling playlists: " + e.getMessage());
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e("PlaylistFetcher", "Error fetching Spotify playlists: " + message);
                callback.onError(message);
            }
        });
    }
    
    /**
     * Fetch playlists by category from Spotify
     * @param categoryId Category ID (e.g., "pop", "rock", "mood")
     * @param callback Callback for result
     */
    public void fetchPlaylistsByCategory(String categoryId, PlaylistFetchCallback callback) {
        spotifyHelper.getClientCredentialsToken(new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String token) {
                String url = "https://api.spotify.com/v1/browse/categories/" + categoryId + "/playlists?limit=10";
                
                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + token)
                        .build();
                
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "Failed to fetch category playlists: " + e.getMessage());
                        callback.onError("Network error: " + e.getMessage());
                    }
                    
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String jsonResponse = response.body().string();
                                List<Playlist> playlists = parseSpotifyFeaturedPlaylists(new JSONObject(jsonResponse));
                                callback.onSuccess(playlists);
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing JSON: " + e.getMessage());
                                callback.onError("Error parsing response: " + e.getMessage());
                            }
                        } else {
                            callback.onError("API error: HTTP " + response.code());
                        }
                    }
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error getting Spotify token: " + errorMessage);
                callback.onError("Authentication error: " + errorMessage);
            }
        });
    }
    
    /**
     * Import a playlist from Spotify by its URL or ID
     * @param spotifyUrl Spotify playlist URL or ID
     * @param callback Callback for imported playlist
     */
    public void importSpotifyPlaylist(String spotifyUrl, SinglePlaylistCallback callback) {
        // Extract playlist ID from URL if needed
        String playlistId = extractSpotifyPlaylistId(spotifyUrl);
        if (playlistId == null) {
            callback.onError("Invalid Spotify playlist URL or ID");
            return;
        }
        
        spotifyHelper.getClientCredentialsToken(new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String token) {
                // First get the playlist details
                String playlistUrl = "https://api.spotify.com/v1/playlists/" + playlistId;
                
                Request request = new Request.Builder()
                        .url(playlistUrl)
                        .header("Authorization", "Bearer " + token)
                        .build();
                
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "Failed to fetch playlist: " + e.getMessage());
                        callback.onError("Network error: " + e.getMessage());
                    }
                    
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String jsonResponse = response.body().string();
                                JSONObject playlistJson = new JSONObject(jsonResponse);
                                
                                // Create a new playlist model
                                Playlist playlist = new Playlist();
                                playlist.setName(playlistJson.getString("name"));
                                
                                if (playlistJson.has("description") && !playlistJson.isNull("description")) {
                                    playlist.setDescription(playlistJson.getString("description"));
                                } else {
                                    playlist.setDescription("Imported from Spotify");
                                }
                                
                                // Get cover image
                                if (playlistJson.has("images") && playlistJson.getJSONArray("images").length() > 0) {
                                    JSONArray images = playlistJson.getJSONArray("images");
                                    playlist.setCoverImageUrl(images.getJSONObject(0).getString("url"));
                                }
                                
                                // Now fetch tracks
                                fetchSpotifyPlaylistTracks(token, playlistId, new PlaylistTracksCallback() {
                                    @Override
                                    public void onSuccess(List<Track> tracks) {
                                        // Create a new playlist in our app
                                        playlistManager.createPlaylistFromTracks(
                                            playlist.getName(), 
                                            playlist.getDescription(), 
                                            tracks,
                                            playlist.getCoverImageUrl(),
                                            new PlaylistManager.PlaylistCallback() {
                                                @Override
                                                public void onSuccess(Playlist createdPlaylist) {
                                                    callback.onSuccess(createdPlaylist);
                                                }
                                                
                                                @Override
                                                public void onError(String errorMessage) {
                                                    callback.onError("Failed to save playlist: " + errorMessage);
                                                }
                                            }
                                        );
                                    }
                                    
                                    @Override
                                    public void onError(String errorMessage) {
                                        callback.onError("Failed to fetch playlist tracks: " + errorMessage);
                                    }
                                });
                                
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing playlist JSON: " + e.getMessage());
                                callback.onError("Error parsing playlist data: " + e.getMessage());
                            }
                        } else {
                            callback.onError("Failed to fetch playlist: " + response.code());
                        }
                    }
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error getting Spotify token: " + errorMessage);
                callback.onError("Authentication error: " + errorMessage);
            }
        });
    }
    
    /**
     * Callback interface for fetching playlist tracks
     */
    private interface PlaylistTracksCallback {
        void onSuccess(List<Track> tracks);
        void onError(String errorMessage);
    }
    
    /**
     * Fetch tracks for a Spotify playlist
     */
    private void fetchSpotifyPlaylistTracks(String token, String playlistId, PlaylistTracksCallback callback) {
        String tracksUrl = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks?limit=50";
        
        Request request = new Request.Builder()
                .url(tracksUrl)
                .header("Authorization", "Bearer " + token)
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch playlist tracks: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonResponse = response.body().string();
                        JSONObject tracksJson = new JSONObject(jsonResponse);
                        JSONArray items = tracksJson.getJSONArray("items");
                        
                        List<Track> tracks = new ArrayList<>();
                        
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            JSONObject trackObj = item.getJSONObject("track");
                            
                            // Skip local files and episodes that can't be played in our app
                            if (trackObj.has("is_local") && trackObj.getBoolean("is_local") || 
                                !trackObj.getString("type").equals("track")) {
                                continue;
                            }
                            
                            Track track = new Track();
                            track.setTrackId(trackObj.getString("id")); // Use original Spotify ID
                            track.setTitle(trackObj.getString("name"));
                            
                            // Get preview URL if available
                            if (trackObj.has("preview_url") && !trackObj.isNull("preview_url")) {
                                track.setPreviewUrl(trackObj.getString("preview_url"));
                            }
                            
                            // Set duration
                            if (trackObj.has("duration_ms")) {
                                track.setDurationMs(trackObj.getLong("duration_ms"));
                            }
                            
                            // Set Spotify ID
                            track.setSpotifyId(trackObj.getString("id"));
                            
                            // Get artists
                            JSONArray artists = trackObj.getJSONArray("artists");
                            if (artists.length() > 0) {
                                JSONObject artist = artists.getJSONObject(0);
                                track.setArtist(artist.getString("name"));
                            }
                            
                            // Get album info and image
                            if (trackObj.has("album")) {
                                JSONObject album = trackObj.getJSONObject("album");
                                track.setAlbum(album.getString("name"));
                                
                                // Get album image
                                if (album.has("images") && album.getJSONArray("images").length() > 0) {
                                    JSONArray images = album.getJSONArray("images");
                                    track.setAlbumArt(images.getJSONObject(0).getString("url"));
                                }
                            }
                            
                            tracks.add(track);
                        }
                        
                        callback.onSuccess(tracks);
                        
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing tracks JSON: " + e.getMessage());
                        callback.onError("Error parsing tracks data: " + e.getMessage());
                    }
                } else {
                    callback.onError("Failed to fetch tracks: " + response.code());
                }
            }
        });
    }
    
    /**
     * Extract playlist ID from a Spotify URL or URI
     */
    private String extractSpotifyPlaylistId(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        
        // If it's already just an ID (no slashes or colons)
        if (!input.contains("/") && !input.contains(":")) {
            return input;
        }
        
        // Handle spotify:playlist:ID format
        if (input.startsWith("spotify:playlist:")) {
            return input.substring("spotify:playlist:".length());
        }
        
        // Handle https://open.spotify.com/playlist/ID format
        if (input.contains("spotify.com/playlist/")) {
            String id = input.substring(input.indexOf("playlist/") + "playlist/".length());
            
            // Remove query params if present
            if (id.contains("?")) {
                id = id.substring(0, id.indexOf("?"));
            }
            
            return id;
        }
        
        return null;
    }
    
    /**
     * Parse Spotify featured playlists from JSON response
     */
    private List<Playlist> parseSpotifyFeaturedPlaylists(JSONObject response) throws JSONException {
        List<Playlist> playlists = new ArrayList<>();
        
        JSONObject playlistsObj = response.getJSONObject("playlists");
        JSONArray items = playlistsObj.getJSONArray("items");
        
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            
            Playlist playlist = new Playlist();
            playlist.setPlaylistId(item.getString("id")); // Use Spotify ID for now
            playlist.setName(item.getString("name"));
            
            // Get description if available
            if (item.has("description") && !item.isNull("description")) {
                playlist.setDescription(item.getString("description"));
            } else {
                playlist.setDescription("Spotify featured playlist");
            }
            
            // Get track count if available
            if (item.has("tracks") && item.getJSONObject("tracks").has("total")) {
                int trackCount = item.getJSONObject("tracks").getInt("total");
                playlist.setTrackCount(trackCount);
            }
            
            // Get cover image
            if (item.has("images") && item.getJSONArray("images").length() > 0) {
                JSONArray images = item.getJSONArray("images");
                playlist.setCoverImageUrl(images.getJSONObject(0).getString("url"));
            }
            
            playlists.add(playlist);
        }
        
        return playlists;
    }
    
    /**
     * Search for playlists on Spotify
     * @param query Search query
     * @param callback Callback for result
     */
    public void searchSpotifyPlaylists(String query, PlaylistFetchCallback callback) {
        spotifyHelper.getClientCredentialsToken(new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String token) {
                try {
                    // URL encode the query
                    String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                    String url = "https://api.spotify.com/v1/search?q=" + encodedQuery + "&type=playlist&limit=10";
                    
                    Request request = new Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer " + token)
                            .build();
                    
                    httpClient.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Log.e(TAG, "Failed to search playlists: " + e.getMessage());
                            callback.onError("Network error: " + e.getMessage());
                        }
                        
                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            if (response.isSuccessful() && response.body() != null) {
                                try {
                                    String jsonResponse = response.body().string();
                                    JSONObject responseJson = new JSONObject(jsonResponse);
                                    
                                    if (responseJson.has("playlists")) {
                                        JSONObject playlistsObj = responseJson.getJSONObject("playlists");
                                        if (playlistsObj.has("items")) {
                                            JSONArray items = playlistsObj.getJSONArray("items");
                                            List<Playlist> playlists = new ArrayList<>();
                                            
                                            for (int i = 0; i < items.length(); i++) {
                                                JSONObject item = items.getJSONObject(i);
                                                
                                                Playlist playlist = new Playlist();
                                                playlist.setPlaylistId(item.getString("id"));
                                                playlist.setName(item.getString("name"));
                                                
                                                // Get description if available
                                                if (item.has("description") && !item.isNull("description")) {
                                                    playlist.setDescription(item.getString("description"));
                                                } else {
                                                    playlist.setDescription("Spotify playlist");
                                                }
                                                
                                                // Get track count if available
                                                if (item.has("tracks") && item.getJSONObject("tracks").has("total")) {
                                                    int trackCount = item.getJSONObject("tracks").getInt("total");
                                                    playlist.setTrackCount(trackCount);
                                                }
                                                
                                                // Get cover image
                                                if (item.has("images") && item.getJSONArray("images").length() > 0) {
                                                    JSONArray images = item.getJSONArray("images");
                                                    playlist.setCoverImageUrl(images.getJSONObject(0).getString("url"));
                                                }
                                                
                                                playlists.add(playlist);
                                            }
                                            
                                            callback.onSuccess(playlists);
                                        } else {
                                            callback.onSuccess(new ArrayList<>());
                                        }
                                    } else {
                                        callback.onSuccess(new ArrayList<>());
                                    }
                                } catch (JSONException e) {
                                    Log.e(TAG, "Error parsing search results: " + e.getMessage());
                                    callback.onError("Error parsing search results: " + e.getMessage());
                                }
                            } else {
                                callback.onError("Failed to search playlists: " + response.code());
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error searching playlists: " + e.getMessage());
                    callback.onError("Error searching playlists: " + e.getMessage());
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error getting Spotify token: " + errorMessage);
                callback.onError("Authentication error: " + errorMessage);
            }
        });
    }
} 