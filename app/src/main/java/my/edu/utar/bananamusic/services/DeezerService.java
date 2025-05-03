package my.edu.utar.bananamusic.services;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;

public class DeezerService {
    private static final String TAG = "DeezerService";
    private static final String BASE_URL = "https://api.deezer.com";
    private static DeezerService instance;
    private final RequestQueue requestQueue;
    private boolean isInitialized = false;

    public interface DeezerCallback<T> {
        void onSuccess(List<T> items);
        void onError(String message);
    }

    private DeezerService(Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    public static synchronized DeezerService getInstance(Context context) {
        if (instance == null) {
            instance = new DeezerService(context);
        }
        return instance;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    private void setInitialized(boolean initialized) {
        isInitialized = initialized;
    }

    public void initialize() {
        // ... existing initialization code ...
        setInitialized(true);
    }

    public void getFeaturedPlaylists(DeezerCallback<Playlist> callback) {
        String url = BASE_URL + "/chart/0/playlists?limit=20";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
            response -> {
                try {
                    List<Playlist> playlists = new ArrayList<>();
                    JSONArray data = response.getJSONArray("data");

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        Playlist playlist = new Playlist();
                        playlist.setPlaylistId("deezer_" + item.getString("id"));
                        playlist.setName(item.getString("title"));
                        playlist.setDescription(item.optString("description", ""));
                        playlist.setCoverImageUrl(item.getString("picture_big"));
                        playlist.setTrackCount(item.optInt("nb_tracks", 0));
                        playlist.setCreatorName(item.getJSONObject("creator").getString("name"));
                        playlist.setSource("deezer");
                        playlists.add(playlist);
                    }

                    callback.onSuccess(playlists);
                } catch (JSONException e) {
                    callback.onError("Error parsing Deezer response: " + e.getMessage());
                }
            },
            error -> callback.onError("Error fetching Deezer playlists: " + error.getMessage())
        );

        requestQueue.add(request);
    }

    public void getPlaylistTracks(String playlistId, DeezerCallback<Track> callback) {
        // Remove "deezer_" prefix from playlistId
        String actualId = playlistId.replace("deezer_", "");
        String url = BASE_URL + "/playlist/" + actualId + "/tracks";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
            response -> {
                try {
                    List<Track> tracks = new ArrayList<>();
                    JSONArray data = response.getJSONArray("data");

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        Track track = new Track();
                        track.setTrackId("deezer_" + item.getString("id"));
                        track.setTitle(item.getString("title"));
                        track.setArtist(item.getJSONObject("artist").getString("name"));
                        track.setAlbum(item.getJSONObject("album").getString("title"));
                        track.setAlbumArtUrl(item.getJSONObject("album").getString("cover_big"));
                        track.setDuration(item.getInt("duration") * 1000L);
                        track.setPreviewUrl(item.getString("preview"));
                        track.setSource("deezer");
                        tracks.add(track);
                    }

                    callback.onSuccess(tracks);
                } catch (JSONException e) {
                    callback.onError("Error parsing Deezer tracks: " + e.getMessage());
                }
            },
            error -> callback.onError("Error fetching Deezer tracks: " + error.getMessage())
        );

        requestQueue.add(request);
    }

    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
} 