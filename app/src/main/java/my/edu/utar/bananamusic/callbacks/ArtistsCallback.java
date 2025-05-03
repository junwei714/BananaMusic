package my.edu.utar.bananamusic.callbacks;

import java.util.List;
import my.edu.utar.bananamusic.models.Artist;

/**
 * Callback interface for artist-related operations
 */
public interface ArtistsCallback {
    /**
     * Called when artists are successfully loaded
     * @param artists List of loaded artists
     */
    void onArtistsLoaded(List<Artist> artists);

    /**
     * Called when an error occurs during artist operations
     * @param e The exception that occurred
     */
    void onError(Exception e);
} 