package my.edu.utar.bananamusic.utils.callbacks;

import java.util.List;
import my.edu.utar.bananamusic.models.Track;

/**
 * Callback interface for track selection events
 */
public interface TrackSelectionCallback {
    /**
     * Called when tracks are selected from the track selector
     * @param selectedTracks List of selected tracks
     */
    void onTracksSelected(List<Track> selectedTracks);
} 