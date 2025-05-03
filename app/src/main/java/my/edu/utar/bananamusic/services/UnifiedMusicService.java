package my.edu.utar.bananamusic.services;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.utils.DeezerHelper;
import org.json.JSONArray;
import org.json.JSONObject;

public class UnifiedMusicService {
    private static final String TAG = "UnifiedMusicService";
    private static UnifiedMusicService instance;
    private final SpotifyHelper spotifyHelper;
    private final DeezerHelper deezerHelper;
    private final Context context;

    public interface TrackPreviewCallback {
        void onSuccess(String previewUrl);
        void onError(String message);
    }

    public interface MusicCallback {
        void onSuccess(List<Track> tracks);
        void onError(String message);
    }

    private UnifiedMusicService(Context context) {
        this.context = context;
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.deezerHelper = DeezerHelper.getInstance(context);
    }

    public static synchronized UnifiedMusicService getInstance(Context context) {
        if (instance == null) {
            instance = new UnifiedMusicService(context);
        }
        return instance;
    }

    public void searchTracks(String query, final MusicCallback callback) {
        spotifyHelper.searchTracks(query, new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    List<Track> tracks = new ArrayList<>();
                    
                    if (jsonResponse.has("tracks")) {
                        JSONObject tracksObj = jsonResponse.getJSONObject("tracks");
                        if (tracksObj.has("items")) {
                            JSONArray items = tracksObj.getJSONArray("items");
                            
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject trackJson = items.getJSONObject(i);
                                
                                String id = trackJson.optString("id");
                                String name = trackJson.optString("name", "Unknown Track");
                                String previewUrl = trackJson.optString("preview_url");
                                
                                // Get album info
                                String albumName = "Unknown Album";
                                String albumArt = null;
                                
                                if (trackJson.has("album")) {
                                    JSONObject albumJson = trackJson.getJSONObject("album");
                                    albumName = albumJson.optString("name", "Unknown Album");
                                    
                                    JSONArray images = albumJson.optJSONArray("images");
                                    if (images != null && images.length() > 0) {
                                        albumArt = images.getJSONObject(0).optString("url");
                                    }
                                }
                                
                                // Get artist info
                                String artist = "Unknown Artist";
                                JSONArray artists = trackJson.optJSONArray("artists");
                                if (artists != null && artists.length() > 0) {
                                    artist = artists.getJSONObject(0).optString("name", "Unknown Artist");
                                }
                                
                                int duration = trackJson.optInt("duration_ms", 0);
                                
                                Track track = new Track(
                                    id,
                                    name,
                                    artist,
                                    albumName,
                                    albumArt,
                                    duration,
                                    previewUrl,
                                    true
                                );
                                track.setSource(Track.SOURCE_SPOTIFY);
                                track.setSpotifyId(id);
                                
                                tracks.add(track);
                            }
                        }
                    }
                    
                    if (tracks.isEmpty()) {
                        // Try Deezer as fallback
                        searchDeezerTracks(query, callback);
                    } else {
                    callback.onSuccess(tracks);
                    }
                } catch (Exception e) {
                    // Try Deezer as fallback
                    searchDeezerTracks(query, callback);
                }
            }

            @Override
            public void onError(String message) {
                // Try Deezer as fallback
                searchDeezerTracks(query, callback);
            }
        });
    }
    
    private void searchDeezerTracks(String query, final MusicCallback callback) {
        deezerHelper.searchTracks(query, new DeezerHelper.DeezerCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    // Check if response is a URL (starts with http/https)
                    if (response.startsWith("http://") || response.startsWith("https://")) {
                        // Create a single track with the preview URL
                        Track track = new Track();
                        track.setId("deezer_" + System.currentTimeMillis());
                        track.setTitle(query);
                        track.setArtist("Unknown Artist");
                        track.setPreviewUrl(response);
                        track.setSource(Track.SOURCE_DEEZER);
                        track.setPlayable(true);
                        
                        List<Track> tracks = new ArrayList<>();
                        tracks.add(track);
                    callback.onSuccess(tracks);
                        return;
                    }

                    // If not a URL, parse as JSON
                    JSONObject jsonResponse = new JSONObject(response);
                    List<Track> tracks = new ArrayList<>();
                    
                    if (jsonResponse.has("data")) {
                        JSONArray items = jsonResponse.getJSONArray("data");
                        
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject trackJson = items.getJSONObject(i);
                            
                            String id = String.valueOf(trackJson.optLong("id"));
                            String title = trackJson.optString("title", "Unknown Track");
                            String previewUrl = trackJson.optString("preview");
                            
                            // Get artist info
                            String artist = "Unknown Artist";
                            if (trackJson.has("artist")) {
                                JSONObject artistJson = trackJson.getJSONObject("artist");
                                artist = artistJson.optString("name", "Unknown Artist");
                            }
                            
                            // Get album info
                            String albumName = "Unknown Album";
                            String albumArt = null;
                            
                            if (trackJson.has("album")) {
                                JSONObject albumJson = trackJson.getJSONObject("album");
                                albumName = albumJson.optString("title", "Unknown Album");
                                
                                // Try different image sizes
                                if (albumJson.has("cover_big")) {
                                    albumArt = albumJson.optString("cover_big");
                                } else if (albumJson.has("cover_medium")) {
                                    albumArt = albumJson.optString("cover_medium");
                                } else if (albumJson.has("cover_small")) {
                                    albumArt = albumJson.optString("cover_small");
                                }
                            }
                            
                            int duration = trackJson.optInt("duration", 0) * 1000; // Convert to ms
                            
                            Track track = new Track(
                                "deezer:" + id,
                                title,
                                artist,
                                albumName,
                                albumArt,
                                duration,
                                previewUrl,
                                true
                            );
                            track.setSource(Track.SOURCE_DEEZER);
                            track.setDeezerTrackId(Long.parseLong(id));
                            
                            tracks.add(track);
                        }
                    }
                    
                    if (tracks.isEmpty()) {
                        callback.onError("No results found from any service");
                } else {
                        callback.onSuccess(tracks);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing Deezer response: " + e.getMessage());
                    callback.onError("Error parsing Deezer response: " + e.getMessage());
                }
            }
            
            @Override
            public void onError(String message) {
                callback.onError("Deezer search error: " + message);
            }
        });
    }

    public void getTrackPreview(Track track, final TrackPreviewCallback callback) {
        if (track == null || track.getId() == null) {
            callback.onError("Invalid track");
            return;
        }

        if (track.getSource().equals(Track.SOURCE_SPOTIFY)) {
            spotifyHelper.getPreview(track.getId(), new SpotifyHelper.SpotifyCallback() {
                @Override
                public void onSuccess(String previewUrl) {
                    callback.onSuccess(previewUrl);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } else {
            deezerHelper.getPreview(track.getId(), new DeezerHelper.DeezerCallback() {
                @Override
                public void onSuccess(String previewUrl) {
                    callback.onSuccess(previewUrl);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        }
    }

    public void getArtistTopTracks(String artistId, String source, MusicCallback callback) {
        if (source.equals("Spotify")) {
            spotifyHelper.getArtistTracks(artistId, new SpotifyHelper.SpotifyCallback() {
                @Override
                public void onSuccess(String response) {
                    List<Track> tracks = spotifyHelper.parseTracksFromResponse(response);
                    callback.onSuccess(tracks);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } else if (source.equals("Deezer")) {
            deezerHelper.getArtistTracks(artistId, new DeezerHelper.DeezerCallback() {
                @Override
                public void onSuccess(String response) {
                    List<Track> tracks = deezerHelper.parseTracksFromResponse(response);
                    callback.onSuccess(tracks);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        }
    }

    public void getPlaylistTracks(String playlistId, String source, final MusicCallback callback) {
        if (source.equals(Track.SOURCE_SPOTIFY)) {
            spotifyHelper.getPlaylist(playlistId, new SpotifyHelper.SpotifyCallback() {
                @Override
                public void onSuccess(String response) {
                    List<Track> tracks = spotifyHelper.parseTracksFromResponse(response);
                    callback.onSuccess(tracks);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } else {
            deezerHelper.getPlaylist(playlistId, new DeezerHelper.DeezerCallback() {
                @Override
                public void onSuccess(String response) {
                    List<Track> tracks = deezerHelper.parseTracksFromResponse(response);
                    callback.onSuccess(tracks);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        }
    }

    public void getUserPlaylists(final MusicCallback callback) {
        // Try to get playlists from both services
        List<Track> allPlaylists = new ArrayList<>();
        
        spotifyHelper.getPlaylists(new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String response) {
                List<Track> tracks = spotifyHelper.parseTracksFromResponse(response);
                allPlaylists.addAll(tracks);
                // Try Deezer next
                getDeezerPlaylists(allPlaylists, callback);
            }
            
            @Override
            public void onError(String message) {
                // If Spotify fails, try Deezer
                getDeezerPlaylists(allPlaylists, callback);
            }
        });
    }

    private void getDeezerPlaylists(List<Track> existingPlaylists, final MusicCallback callback) {
        deezerHelper.getPlaylists(new DeezerHelper.DeezerCallback() {
            @Override
            public void onSuccess(String response) {
                List<Track> tracks = deezerHelper.parseTracksFromResponse(response);
                existingPlaylists.addAll(tracks);
                callback.onSuccess(existingPlaylists);
            }
            
            @Override
            public void onError(String message) {
                if (existingPlaylists.isEmpty()) {
                    callback.onError("Failed to get playlists: " + message);
                } else {
                    // Return whatever we got from Spotify
                    callback.onSuccess(existingPlaylists);
                }
            }
        });
    }
} 