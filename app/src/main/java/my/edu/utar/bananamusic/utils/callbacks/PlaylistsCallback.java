package my.edu.utar.bananamusic.utils.callbacks;

import java.util.List;
import my.edu.utar.bananamusic.models.Playlist;

/**
 * Base callback interface for operations that return multiple playlists.
 * This is the primary interface that should be used across the application.
 * Other interfaces should adapt to this one.
 */
public interface PlaylistsCallback {
    /**
     * Called when the operation is successful
     * @param playlists List of playlists
     */
    void onSuccess(List<Playlist> playlists);

    /**
     * Called when the operation fails
     * @param message Description of what went wrong
     */
    void onError(String message);

    /**
     * Legacy compatibility method for exception-based error callbacks
     * @param e The exception that occurred
     */
    default void onError(Exception e) {
        onError(e != null ? e.getMessage() : "Unknown error");
    }

    /**
     * Legacy compatibility method for playlists loaded pattern
     * @param playlists List of loaded playlists
     */
    default void onPlaylistsLoaded(List<Playlist> playlists) {
        onSuccess(playlists);
    }
} 