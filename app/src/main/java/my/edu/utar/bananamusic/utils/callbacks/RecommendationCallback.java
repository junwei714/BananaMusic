package my.edu.utar.bananamusic.utils.callbacks;

import java.util.List;
import my.edu.utar.bananamusic.models.Track;

/**
 * Base callback interface for recommendation-related operations.
 * This is the primary interface that should be used across the application.
 * Other recommendation interfaces should adapt to this one.
 */
public interface RecommendationCallback {
    /**
     * Called when recommendations are ready
     * @param recommendations List of recommended tracks
     */
    void onRecommendationsReady(List<Track> recommendations);

    /**
     * Called when the operation fails
     * @param message Description of what went wrong
     */
    void onError(String message);

    /**
     * Legacy compatibility method for success pattern
     * @param tracks List of recommended tracks
     */
    default void onSuccess(List<Track> tracks) {
        onRecommendationsReady(tracks);
    }

    /**
     * Legacy compatibility method for exception-based error callbacks
     * @param e The exception that occurred
     */
    default void onError(Exception e) {
        onError(e != null ? e.getMessage() : "Unknown error");
    }
} 