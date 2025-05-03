package my.edu.utar.bananamusic.utils.callbacks;

import java.util.List;
import my.edu.utar.bananamusic.models.Track;

/**
 * Simple callback interface for track-related operations
 */
public interface CallbackImpl {
    /**
     * Called when the operation is successful
     * @param tracks List of tracks
     */
    void onSuccess(List<Track> tracks);

    /**
     * Called when the operation fails
     * @param message Description of what went wrong
     */
    void onError(String message);
} 