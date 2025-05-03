package my.edu.utar.bananamusic.utils.callbacks;

import my.edu.utar.bananamusic.models.Playlist;

/**
 * Base callback interface for playlist-related operations.
 * This is the primary interface that should be used across the application.
 * Other interfaces should adapt to this one.
 */
public interface PlaylistCallback extends BaseCallback<Playlist> {
    @Override
    void onSuccess(Playlist playlist);

    @Override
    void onError(String message);

    /**
     * Legacy compatibility method for exception-based error callbacks
     * @param e The exception that occurred
     */
    default void onError(Exception e) {
        onError(e != null ? e.getMessage() : "Unknown error");
    }

    /**
     * Legacy compatibility method for playlist loaded pattern
     * @param playlist The loaded playlist
     */
    default void onPlaylistLoaded(Playlist playlist) {
        onSuccess(playlist);
    }
} 