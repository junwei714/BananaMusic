package my.edu.utar.bananamusic.utils.callbacks;

import my.edu.utar.bananamusic.models.Track;

/**
 * Callback interface for track info operations
 */
public interface TrackInfoCallback {
    /**
     * Called when track info is successfully loaded
     * @param track The loaded track
     */
    void onTrackInfoLoaded(Track track);

    /**
     * Called when the operation fails
     * @param message Description of what went wrong
     */
    void onError(String message);
} 