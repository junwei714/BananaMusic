package my.edu.utar.bananamusic.utils;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import my.edu.utar.bananamusic.models.Track;

public class UnifiedMusicService {
    private static UnifiedMusicService instance;
    private final SpotifyHelper spotifyHelper;
    private final DeezerHelper deezerHelper;
    private final Context context;

    public interface MusicCallback {
        void onSuccess(List<Track> tracks);
        void onError(String message);
    }
    
    public interface TrackPreviewCallback {
        void onSuccess(String previewUrl);
        void onError(String message);
    }

    private UnifiedMusicService(Context context) {
        this.context = context;
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.deezerHelper = DeezerHelper.getInstance(context);
    }

    public static UnifiedMusicService getInstance(Context context) {
        if (instance == null) {
            instance = new UnifiedMusicService(context);
        }
        return instance;
    }

    public void searchTracks(String query, final MusicCallback callback) {
        final List<Track> allTracks = new ArrayList<>();
        final boolean[] spotifyDone = {false};
        final boolean[] deezerDone = {false};

        spotifyHelper.searchTracks(query, new SpotifyHelper.SpotifyTracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                allTracks.addAll(tracks);
                spotifyDone[0] = true;
                if (spotifyDone[0] && deezerDone[0]) {
                    if (allTracks.isEmpty()) {
                        callback.onError("No tracks found");
                    } else {
                        callback.onSuccess(allTracks);
                    }
                }
            }

            @Override
            public void onError(String message) {
                spotifyDone[0] = true;
                if (spotifyDone[0] && deezerDone[0]) {
                    if (allTracks.isEmpty()) {
                        callback.onError("No tracks found");
                    } else {
                        callback.onSuccess(allTracks);
                    }
                }
            }
        });

        deezerHelper.searchTracks(query, new DeezerHelper.DeezerTracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                allTracks.addAll(tracks);
                deezerDone[0] = true;
                if (spotifyDone[0] && deezerDone[0]) {
                    if (allTracks.isEmpty()) {
                        callback.onError("No tracks found");
                    } else {
                        callback.onSuccess(allTracks);
                    }
                }
            }

            @Override
            public void onError(String message) {
                deezerDone[0] = true;
                if (spotifyDone[0] && deezerDone[0]) {
                    if (allTracks.isEmpty()) {
                        callback.onError("No tracks found");
                    } else {
                        callback.onSuccess(allTracks);
                    }
                }
            }
        });
    }

    public void getPlaylistTracks(String userId, String service, MusicCallback callback) {
        if ("Spotify".equals(service)) {
            spotifyHelper.getUserPlaylists(callback);
        } else if ("Deezer".equals(service)) {
            deezerHelper.getUserPlaylists(callback);
        } else {
            callback.onError("Invalid service specified");
        }
    }

    // Existing method maintained for backward compatibility
    public void getTrackPreview(Track track, MusicCallback callback) {
        getTrackPreview(track, new TrackPreviewCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                List<Track> tracks = new ArrayList<>();
                track.setPreviewUrl(previewUrl);
                tracks.add(track);
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    // New method with proper TrackPreviewCallback
    public void getTrackPreview(Track track, TrackPreviewCallback callback) {
        if (track.getService().equals("Spotify")) {
            spotifyHelper.getPreview(track.getId(), new SpotifyHelper.SpotifyCallback() {
                @Override
                public void onSuccess(String result) {
                    callback.onSuccess(result);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } else if (track.getService().equals("Deezer")) {
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
        } else {
            callback.onError("Unsupported service");
        }
    }
} 