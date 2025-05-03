package my.edu.utar.bananamusic.utils.callbacks;

import java.util.List;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.SpotifyHelper;

/**
 * Compatibility utilities for working with callbacks
 */
public class CallbackCompat {

    /**
     * Create a PlaylistCallback from a lambda implementation
     */
    public static PlaylistCallback createPlaylistCallback(
            final PlaylistCallbackLambda successHandler,
            final ErrorCallbackLambda errorHandler) {
        return new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                if (successHandler != null) {
                    successHandler.onSuccess(playlist);
                }
            }
            
            @Override
            public void onError(String message) {
                if (errorHandler != null) {
                    errorHandler.onError(message);
                }
            }
        };
    }
    
    /**
     * Create a PlaylistsCallback from lambda implementations
     */
    public static PlaylistsCallback createPlaylistsCallback(
            final PlaylistsCallbackLambda successHandler,
            final ErrorCallbackLambda errorHandler) {
        return new PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (successHandler != null) {
                    successHandler.onSuccess(playlists);
                }
            }
            
            @Override
            public void onError(String message) {
                if (errorHandler != null) {
                    errorHandler.onError(message);
                }
            }
        };
    }

    /**
     * Create a TracksCallback from lambda implementations
     */
    public static TracksCallback createTracksCallback(
            final TracksCallbackLambda successHandler,
            final ErrorCallbackLambda errorHandler) {
        return new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (successHandler != null) {
                    successHandler.onSuccess(tracks);
                }
            }
            
            @Override
            public void onError(String message) {
                if (errorHandler != null) {
                    errorHandler.onError(message);
                }
            }
            
            @Override
            public void onTracksLoaded(List<Track> tracks) {
                onSuccess(tracks);
            }
        };
    }
    
    /**
     * Success handler for PlaylistCallback
     */
    public interface PlaylistCallbackLambda {
        void onSuccess(Playlist playlist);
    }
    
    /**
     * Success handler for PlaylistsCallback
     */
    public interface PlaylistsCallbackLambda {
        void onSuccess(List<Playlist> playlists);
    }
    
    /**
     * Success handler for TracksCallback
     */
    public interface TracksCallbackLambda {
        void onSuccess(List<Track> tracks);
    }
    
    /**
     * Error handler for callbacks
     */
    public interface ErrorCallbackLambda {
        void onError(String message);
    }
} 