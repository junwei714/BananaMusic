package my.edu.utar.bananamusic.repositories;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import my.edu.utar.bananamusic.models.Album;
import my.edu.utar.bananamusic.models.Artist;
import my.edu.utar.bananamusic.models.Track;

public class SpotifyRepository {
    private static final String TAG = "SpotifyRepository";
    private static final String SPOTIFY_API_BASE_URL = "https://api.spotify.com/v1";
    private static final String SPOTIFY_AUTH_URL = "https://accounts.spotify.com/api/token";
    private static final String PREF_NAME = "spotify_prefs";
    private static final String PREF_ACCESS_TOKEN = "access_token";
    private static final String PREF_TOKEN_EXPIRES = "token_expires";

    private static SpotifyRepository instance;
    private final Context context;
    private final OkHttpClient httpClient;
    private final SharedPreferences preferences;

    private String accessToken;
    private long tokenExpiresAt;

    private SpotifyRepository(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient();
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // Load saved access token if available
        this.accessToken = preferences.getString(PREF_ACCESS_TOKEN, null);
        this.tokenExpiresAt = preferences.getLong(PREF_TOKEN_EXPIRES, 0);
    }

    public static synchronized SpotifyRepository getInstance(Context context) {
        if (instance == null) {
            instance = new SpotifyRepository(context);
        }
        return instance;
    }

    public boolean isAuthenticated() {
        long currentTime = System.currentTimeMillis();
        return accessToken != null && currentTime < tokenExpiresAt;
    }

    public interface SearchCallback {
        void onSuccess(List<Track> tracks);
        void onError(String errorMessage);
    }

    public void searchTracks(String query, final SearchCallback callback) {
        if (!isAuthenticated()) {
            callback.onError("Not authenticated with Spotify");
            return;
        }

        String url = SPOTIFY_API_BASE_URL + "/search?q=" + query + "&type=track&limit=20";
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Error: " + response.code() + " " + response.message());
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONObject jsonObject = new JSONObject(responseData);
                    JSONObject tracksObject = jsonObject.getJSONObject("tracks");
                    JSONArray items = tracksObject.getJSONArray("items");
                    
                    List<Track> tracks = new ArrayList<>();
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject trackObject = items.getJSONObject(i);
                        
                        String id = trackObject.getString("id");
                        String name = trackObject.getString("name");
                        
                        JSONObject albumObject = trackObject.getJSONObject("album");
                        String album = albumObject.getString("name");
                        
                        JSONArray artistsArray = trackObject.getJSONArray("artists");
                        String artist = "";
                        if (artistsArray.length() > 0) {
                            artist = artistsArray.getJSONObject(0).getString("name");
                        }
                        
                        String albumArtUrl = "";
                        JSONArray images = albumObject.getJSONArray("images");
                        if (images.length() > 0) {
                            albumArtUrl = images.getJSONObject(0).getString("url");
                        }
                        
                        int duration = trackObject.getInt("duration_ms");
                        boolean isPlayable = trackObject.optBoolean("is_playable", true);
                        String previewUrl = trackObject.optString("preview_url", null);
                        
                        Track track = new Track(id, name, artist, album, albumArtUrl, previewUrl, duration, isPlayable);
                        track.setSource(Track.SOURCE_SPOTIFY);
                        track.setSpotifyId(id);
                        track.setExternalUrl(trackObject.getJSONObject("external_urls").optString("spotify", ""));
                        
                        tracks.add(track);
                    }
                    
                    callback.onSuccess(tracks);
                    
                } catch (JSONException e) {
                    callback.onError("Error parsing response: " + e.getMessage());
                }
            }
        });
    }

    public interface ArtistSearchCallback {
        void onSuccess(List<Artist> artists);
        void onError(String errorMessage);
    }
    
    public void searchArtists(String query, final ArtistSearchCallback callback) {
        if (!isAuthenticated()) {
            callback.onError("Not authenticated with Spotify");
            return;
        }

        String url = SPOTIFY_API_BASE_URL + "/search?q=" + query + "&type=artist&limit=20";
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Process artists response
                if (!response.isSuccessful()) {
                    callback.onError("Error: " + response.code() + " " + response.message());
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONObject jsonObject = new JSONObject(responseData);
                    JSONObject artistsObject = jsonObject.getJSONObject("artists");
                    JSONArray items = artistsObject.getJSONArray("items");
                    
                    List<Artist> artists = new ArrayList<>();
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject artistObject = items.getJSONObject(i);
                        
                        String id = artistObject.getString("id");
                        String name = artistObject.getString("name");
                        
                        String imageUrl = "";
                        JSONArray images = artistObject.getJSONArray("images");
                        if (images.length() > 0) {
                            imageUrl = images.getJSONObject(0).getString("url");
                        }
                        
                        Artist artist = new Artist(id, name, imageUrl);
                        artist.setSpotifyId(id);
                        artist.setLocal(false);
                        
                        // Add genres if available
                        JSONArray genresArray = artistObject.optJSONArray("genres");
                        if (genresArray != null) {
                            List<String> genres = new ArrayList<>();
                            for (int j = 0; j < genresArray.length(); j++) {
                                genres.add(genresArray.getString(j));
                            }
                            artist.setGenres(genres);
                        }
                        
                        // Set popularity if available
                        int popularity = artistObject.optInt("popularity", 0);
                        artist.setPopularity(popularity);
                        
                        artists.add(artist);
                    }
                    
                    callback.onSuccess(artists);
                    
                } catch (JSONException e) {
                    callback.onError("Error parsing response: " + e.getMessage());
                }
            }
        });
    }

    public interface AlbumSearchCallback {
        void onSuccess(List<Album> albums);
        void onError(String errorMessage);
    }
    
    public void searchAlbums(String query, final AlbumSearchCallback callback) {
        if (!isAuthenticated()) {
            callback.onError("Not authenticated with Spotify");
            return;
        }

        String url = SPOTIFY_API_BASE_URL + "/search?q=" + query + "&type=album&limit=20";
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Process albums response
                if (!response.isSuccessful()) {
                    callback.onError("Error: " + response.code() + " " + response.message());
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONObject jsonObject = new JSONObject(responseData);
                    JSONObject albumsObject = jsonObject.getJSONObject("albums");
                    JSONArray items = albumsObject.getJSONArray("items");
                    
                    List<Album> albums = new ArrayList<>();
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject albumObject = items.getJSONObject(i);
                        
                        String id = albumObject.getString("id");
                        String name = albumObject.getString("name");
                        
                        JSONArray artistsArray = albumObject.getJSONArray("artists");
                        String artist = "";
                        String artistId = "";
                        if (artistsArray.length() > 0) {
                            JSONObject artistObject = artistsArray.getJSONObject(0);
                            artist = artistObject.getString("name");
                            artistId = artistObject.getString("id");
                        }
                        
                        String albumArtUrl = "";
                        JSONArray images = albumObject.getJSONArray("images");
                        if (images.length() > 0) {
                            albumArtUrl = images.getJSONObject(0).getString("url");
                        }
                        
                        Album album = new Album(id, name, artist, albumArtUrl);
                        album.setArtistId(artistId);
                        album.setSpotifyId(id);
                        album.setLocal(false);
                        
                        // Get total tracks if available
                        int totalTracks = albumObject.optInt("total_tracks", 0);
                        album.setTrackCount(totalTracks);
                        
                        // Get year from release date if available
                        String releaseDate = albumObject.optString("release_date", "");
                        if (releaseDate.length() >= 4) {
                            try {
                                int year = Integer.parseInt(releaseDate.substring(0, 4));
                                album.setYear(year);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Error parsing release date: " + releaseDate);
                            }
                        }
                        
                        albums.add(album);
                    }
                    
                    callback.onSuccess(albums);
                    
                } catch (JSONException e) {
                    callback.onError("Error parsing response: " + e.getMessage());
                }
            }
        });
    }
    
    // Add authentication methods if needed for your app
} 