package my.edu.utar.bananamusic.services;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;

public class MusicServiceManager {
    private static final String TAG = "MusicServiceManager";
    private static MusicServiceManager instance;
    private final DeezerService deezerService;
    private final SpotifyService spotifyService;
    private InitializationCallback pendingCallback;

    public interface MusicServiceCallback<T> {
        void onSuccess(List<T> items);
        void onError(String message);
    }

    public interface InitializationCallback {
        void onInitialized();
    }

    private MusicServiceManager(Context context) {
        deezerService = DeezerService.getInstance(context);
        spotifyService = SpotifyService.getInstance(context);
    }

    public static synchronized MusicServiceManager getInstance(Context context) {
        if (instance == null) {
            instance = new MusicServiceManager(context);
        }
        return instance;
    }

    public void getAllFeaturedPlaylists(MusicServiceCallback<Playlist> callback) {
        List<Playlist> allPlaylists = new ArrayList<>();
        AtomicInteger completedRequests = new AtomicInteger(0);
        final boolean[] hasError = {false};

        // Get Deezer playlists
        deezerService.getFeaturedPlaylists(new DeezerService.DeezerCallback<Playlist>() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                synchronized (allPlaylists) {
                    allPlaylists.addAll(playlists);
                }
                checkCompletion();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Deezer error: " + message);
                checkCompletion();
            }
        });

        // Get Spotify playlists
        spotifyService.getFeaturedPlaylists(new SpotifyService.SpotifyCallback<Playlist>() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                synchronized (allPlaylists) {
                    allPlaylists.addAll(playlists);
                }
                checkCompletion();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Spotify error: " + message);
                checkCompletion();
            }
        });

        // Helper method to check if all requests are complete
        Runnable checkCompletion = () -> {
            if (completedRequests.incrementAndGet() == 2) {
                if (allPlaylists.isEmpty() && hasError[0]) {
                    callback.onError("Failed to fetch playlists from both services");
                } else {
                    callback.onSuccess(allPlaylists);
                }
            }
        };
    }

    public void getPlaylistTracks(String playlistId, MusicServiceCallback<Track> callback) {
        if (playlistId.startsWith("deezer_")) {
            deezerService.getPlaylistTracks(playlistId, new DeezerService.DeezerCallback<Track>() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    callback.onSuccess(tracks);
                }

                @Override
                public void onError(String message) {
                    callback.onError("Deezer error: " + message);
                }
            });
        } else if (playlistId.startsWith("spotify_")) {
            spotifyService.getPlaylistTracks(playlistId, new SpotifyService.SpotifyCallback<Track>() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    callback.onSuccess(tracks);
                }

                @Override
                public void onError(String message) {
                    callback.onError("Spotify error: " + message);
                }
            });
        } else {
            callback.onError("Invalid playlist ID format");
        }
    }

    public void setInitializationCallback(InitializationCallback callback) {
        this.pendingCallback = callback;
        checkInitialization();
    }

    private void checkCompletion() {
        if (spotifyService.isInitialized() && deezerService.isInitialized()) {
            if (pendingCallback != null) {
                pendingCallback.onInitialized();
                pendingCallback = null;
            }
        }
    }

    private void checkInitialization() {
        if (spotifyService.isInitialized() && deezerService.isInitialized()) {
            if (pendingCallback != null) {
                pendingCallback.onInitialized();
                pendingCallback = null;
            }
        }
    }
} 