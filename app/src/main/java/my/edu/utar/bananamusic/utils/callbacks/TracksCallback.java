package my.edu.utar.bananamusic.utils.callbacks;

import java.util.List;
import my.edu.utar.bananamusic.models.Track;

/**
 * Base callback interface for track-related operations.
 * This is the primary interface that should be used across the application.
 * Other interfaces should adapt to this one.
 */
public interface TracksCallback extends BaseCallback<List<Track>> {
    @Override
    void onSuccess(List<Track> tracks);

    @Override
    void onError(String message);
    
    /**
     * Legacy compatibility method for callbacks using onTracksLoaded pattern
     * Default implementation to maintain backward compatibility
     * @param tracks List of loaded tracks
     */
    default void onTracksLoaded(List<Track> tracks) {
        onSuccess(tracks);
    }
    
    /**
     * Legacy compatibility method for exception-based error callbacks
     * @param e The exception that occurred
     */
    default void onError(Exception e) {
        onError(e != null ? e.getMessage() : "Unknown error");
    }
} 