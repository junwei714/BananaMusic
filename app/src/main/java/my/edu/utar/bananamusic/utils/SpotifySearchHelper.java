package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;

public class SpotifySearchHelper {
    private static final String TAG = "SpotifySearchHelper";
    private static final String SPOTIFY_API_BASE_URL = "https://api.spotify.com/v1";
    private static final String SEARCH_ENDPOINT = "/search";
    
    private final Context context;
    private final RequestQueue requestQueue;
    private final SpotifyHelper spotifyHelper;
    private String accessToken;

    public interface SearchCallback {
        void onSearchResults(List<Track> tracks);
        void onError(String message);
    }

    public SpotifySearchHelper(Context context) {
        this.context = context;
        this.requestQueue = Volley.newRequestQueue(context);
        this.spotifyHelper = SpotifyHelper.getInstance(context);
    }

    public void searchTracks(String query, SearchCallback callback) {
        // First ensure we have a valid access token
        refreshToken();
    
        // Build the search URL
        String url = SPOTIFY_API_BASE_URL + SEARCH_ENDPOINT + 
                    "?q=" + query + 
                    "&type=track" +
                    "&limit=20";
    
        // Create headers with authorization
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
    
        // Make the API request
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            response -> {
                try {
                    List<Track> tracks = new ArrayList<>();
                    JSONObject tracksObj = response.getJSONObject("tracks");
                    JSONArray items = tracksObj.getJSONArray("items");
    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject trackJson = items.getJSONObject(i);
                        
                        // Extract track details
                        String id = trackJson.getString("id");
                        String name = trackJson.getString("name");
                        String previewUrl = trackJson.optString("preview_url", "");
                        
                        // Get artist info
                        JSONArray artists = trackJson.getJSONArray("artists");
                        String artistName = artists.getJSONObject(0).getString("name");
                        
                        // Get album info
                        JSONObject album = trackJson.getJSONObject("album");
                        String albumName = album.getString("name");
                        
                        // Get album art URL
                        JSONArray images = album.getJSONArray("images");
                        String imageUrl = "";
                        if (images.length() > 0) {
                            imageUrl = images.getJSONObject(0).getString("url");
                        }
                        
                        // Create track object with proper album art URL
                        Track track = new Track(id, name, artistName, albumName, imageUrl, 0, previewUrl, true);
                        track.setTrackId(id);
                        track.setSource(Track.SOURCE_SPOTIFY);
                        track.setPreviewUrl(previewUrl);
                        track.setAlbumArtUrl(imageUrl); // Explicitly set both URLs
                        track.setImageUrl(imageUrl);
                        
                        tracks.add(track);
                    }
                    
                    callback.onSearchResults(tracks);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing search results: " + e.getMessage());
                    callback.onError("Error parsing search results");
                }
            },
            error -> {
                Log.e(TAG, "Error searching tracks: " + error.getMessage());
                callback.onError("Error searching tracks: " + error.getMessage());
            }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers;
            }
        };
    
        // Add request to queue
        requestQueue.add(request);
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