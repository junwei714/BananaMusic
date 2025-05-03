package my.edu.utar.bananamusic.utils.callbacks;

import java.util.List;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.SpotifyHelper;

/**
 * Converter utilities for callback interfaces
 */
public class CallbackConverters {
    
    /**
     * Convert between TracksCallback and SpotifyRecommendationsCallback
     */
    public static SpotifyHelper.SpotifyRecommendationsCallback tracksToSpotify(
            final TracksCallback callback) {
        if (callback == null) return null;
        
        return new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }
    
    /**
     * Convert between SpotifyRecommendationsCallback and TracksCallback
     */
    public static TracksCallback spotifyToTracks(
            final SpotifyHelper.SpotifyRecommendationsCallback callback) {
        if (callback == null) return null;
        
        return new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
            
            @Override
            public void onTracksLoaded(List<Track> tracks) {
                onSuccess(tracks);
            }
        };
    }
    
    /**
     * Convert between any TracksCallback implementation and a standard interface
     */
    public static TracksCallback toStandardTracksCallback(final Object callback) {
        if (callback == null) return null;
        
        if (callback instanceof TracksCallback) {
            return (TracksCallback) callback;
        }
        
        if (callback instanceof SpotifyHelper.SpotifyRecommendationsCallback) {
            return spotifyToTracks((SpotifyHelper.SpotifyRecommendationsCallback) callback);
        }
        
        // Default implementation that logs errors
        return new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                android.util.Log.w("CallbackConverters", "Unknown callback type received tracks");
            }
            
            @Override
            public void onError(String message) {
                android.util.Log.e("CallbackConverters", "Unknown callback type received error: " + message);
            }
        };
    }
} 