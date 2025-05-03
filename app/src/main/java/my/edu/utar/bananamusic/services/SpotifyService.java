package my.edu.utar.bananamusic.services;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.SpotifyConfig;

public class SpotifyService {
    private static final String TAG = "SpotifyService";
    private static final String AUTH_URL = SpotifyConfig.AUTH_URL;
    private static final String BASE_URL = SpotifyConfig.API_BASE_URL;
    private static SpotifyService instance;
    private final RequestQueue requestQueue;
    private String accessToken;
    private long tokenExpirationTime;
    private boolean isInitialized = false;

    private static final String CLIENT_ID = SpotifyConfig.CLIENT_ID;
    private static final String CLIENT_SECRET = SpotifyConfig.CLIENT_SECRET;

    public interface SpotifyCallback<T> {
        void onSuccess(List<T> items);
        void onError(String message);
    }

    private SpotifyService(Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
        authenticate(() -> setInitialized(true));
    }

    public static synchronized SpotifyService getInstance(Context context) {
        if (instance == null) {
            instance = new SpotifyService(context);
        }
        return instance;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    private void setInitialized(boolean initialized) {
        this.isInitialized = initialized;
    }

    private void authenticate(final Runnable onSuccess) {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String base64Credentials = android.util.Base64.encodeToString(credentials.getBytes(), android.util.Base64.NO_WRAP);

        StringRequest request = new StringRequest(Request.Method.POST, AUTH_URL,
            response -> {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    accessToken = jsonResponse.getString("access_token");
                    int expiresIn = jsonResponse.getInt("expires_in");
                    tokenExpirationTime = System.currentTimeMillis() + (expiresIn * 1000);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing authentication response", e);
                }
            },
            error -> Log.e(TAG, "Authentication error: " + error.getMessage())) {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Basic " + base64Credentials);
                return headers;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("grant_type", "client_credentials");
                return params;
            }
        };

        requestQueue.add(request);
    }

    public void getFeaturedPlaylists(SpotifyCallback<Playlist> callback) {
        authenticate(() -> {
            String url = BASE_URL + "/browse/featured-playlists?limit=20";

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        List<Playlist> playlists = new ArrayList<>();
                        JSONObject playlistsObject = response.getJSONObject("playlists");
                        JSONArray items = playlistsObject.getJSONArray("items");

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            Playlist playlist = new Playlist();
                            playlist.setPlaylistId("spotify_" + item.getString("id"));
                            playlist.setName(item.getString("name"));
                            playlist.setDescription(item.optString("description", ""));
                            
                            JSONObject images = item.getJSONArray("images").getJSONObject(0);
                            playlist.setCoverImageUrl(images.getString("url"));
                            
                            playlist.setTrackCount(item.getJSONObject("tracks").getInt("total"));
                            playlist.setCreatorName(item.getJSONObject("owner").getString("display_name"));
                            playlist.setSource("spotify");
                            playlists.add(playlist);
                        }

                        callback.onSuccess(playlists);
                    } catch (JSONException e) {
                        callback.onError("Error parsing Spotify response: " + e.getMessage());
                    }
                },
                error -> callback.onError("Error fetching Spotify playlists: " + error.getMessage())
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + accessToken);
                    return headers;
                }
            };

            requestQueue.add(request);
        });
    }

    public void getPlaylistTracks(String playlistId, SpotifyCallback<Track> callback) {
        authenticate(() -> {
            // Remove "spotify_" prefix from playlistId
            String actualId = playlistId.replace("spotify_", "");
            String url = BASE_URL + "/playlists/" + actualId + "/tracks";

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        List<Track> tracks = new ArrayList<>();
                        JSONArray items = response.getJSONArray("items");

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            JSONObject trackObj = item.getJSONObject("track");
                            
                            Track track = new Track();
                            track.setTrackId("spotify_" + trackObj.getString("id"));
                            track.setTitle(trackObj.getString("name"));
                            
                            JSONArray artists = trackObj.getJSONArray("artists");
                            StringBuilder artistNames = new StringBuilder();
                            for (int j = 0; j < artists.length(); j++) {
                                if (j > 0) artistNames.append(", ");
                                artistNames.append(artists.getJSONObject(j).getString("name"));
                            }
                            track.setArtist(artistNames.toString());
                            
                            JSONObject album = trackObj.getJSONObject("album");
                            track.setAlbum(album.getString("name"));
                            if (album.getJSONArray("images").length() > 0) {
                                track.setAlbumArtUrl(album.getJSONArray("images").getJSONObject(0).getString("url"));
                            }
                            
                            track.setDuration(trackObj.getInt("duration_ms"));
                            track.setPreviewUrl(trackObj.optString("preview_url", null));
                            track.setSource("spotify");
                            tracks.add(track);
                        }

                        callback.onSuccess(tracks);
                    } catch (JSONException e) {
                        callback.onError("Error parsing Spotify tracks: " + e.getMessage());
                    }
                },
                error -> callback.onError("Error fetching Spotify tracks: " + error.getMessage())
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + accessToken);
                    return headers;
                }
            };

            requestQueue.add(request);
        });
    }

    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
} 