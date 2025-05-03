package my.edu.utar.bananamusic.utils.callbacks;

import java.util.List;
import android.util.Log;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.DeezerHelper;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.utils.DeezerHelper.DeezerTracksCallback;
import my.edu.utar.bananamusic.utils.SpotifyHelper.SpotifyTracksCallback;

/**
 * Utility class to adapt between different callback interfaces
 */
public class CallbackAdapter {

    /**
     * Base interface for all track-related callbacks
     */
    public interface BaseTracksCallback {
        void onSuccess(List<Track> tracks);
        void onError(String message);
    }

    /**
     * Interface for inline implementations of callbacks
     */
    public interface CallbackImpl extends BaseTracksCallback {
        // No additional methods needed since it has the same signature
    }

    /**
     * Interface for playlist callback implementations
     */
    public interface PlaylistCallbackImpl {
        void onSuccess(Playlist playlist);
        void onError(String message);
    }

    /**
     * Interface for playlists callback implementations
     */
    public interface PlaylistsCallbackImpl {
        void onSuccess(List<Playlist> playlists);
        void onError(String message);
    }

    /**
     * Convert from utils.callbacks.TracksCallback to DeezerHelper.DeezerTracksCallback
     */
    public static DeezerHelper.DeezerTracksCallback toDeezerTracksCallback(final TracksCallback callback) {
        if (callback == null) return null;
        return new DeezerHelper.DeezerTracksCallback() {
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
     * Convert from DeezerHelper.DeezerTracksCallback to utils.callbacks.TracksCallback
     */
    public static TracksCallback fromDeezerTracksCallback(final DeezerHelper.DeezerTracksCallback callback) {
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
     * Convert from utils.callbacks.TracksCallback to ApiDataProvider.TracksCallback
     */
    public static ApiDataProvider.TracksCallback toApiDataProviderCallback(final TracksCallback callback) {
        if (callback == null) return null;
        return new ApiDataProvider.TracksCallback() {
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
     * Convert from ApiDataProvider.TracksCallback to utils.callbacks.TracksCallback
     */
    public static TracksCallback fromApiDataProviderCallback(final ApiDataProvider.TracksCallback callback) {
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
     * Convert from utils.callbacks.PlaylistCallback to PlaylistManager.PlaylistCallback
     */
    public static PlaylistManager.PlaylistCallback toPlaylistManagerCallback(final PlaylistCallback callback) {
        if (callback == null) return null;
        return new PlaylistManager.PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess(playlist);
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    /**
     * Convert from PlaylistManager.PlaylistCallback to utils.callbacks.PlaylistCallback
     */
    public static PlaylistCallback fromPlaylistManagerCallback(final PlaylistManager.PlaylistCallback callback) {
        if (callback == null) return null;
        return new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess(playlist);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    /**
     * Convert from utils.callbacks.TracksCallback to SpotifyHelper.SpotifyRecommendationsCallback
     */
    public static SpotifyHelper.SpotifyRecommendationsCallback toSpotifyRecommendationsCallback(final TracksCallback callback) {
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
     * Convert from SpotifyHelper.SpotifyRecommendationsCallback to utils.callbacks.TracksCallback
     */
    public static TracksCallback fromSpotifyRecommendationsCallback(final SpotifyHelper.SpotifyRecommendationsCallback callback) {
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
     * Convert from DeezerHelper.DeezerTracksCallback to ApiDataProvider.TracksCallback
     */
    public static ApiDataProvider.TracksCallback fromDeezerToApiCallback(final DeezerHelper.DeezerTracksCallback callback) {
        return new ApiDataProvider.TracksCallback() {
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
     * Create a utils.callbacks.TracksCallback from a CallbackImpl
     */
    public static TracksCallback createTracksCallback(final CallbackImpl callback) {
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
     * Convert from utils.callbacks.PlaylistsCallback to PlaylistManager.PlaylistsCallback
     */
    public static PlaylistManager.PlaylistsCallback toPlaylistManagerPlaylistsCallback(final PlaylistsCallback callback) {
        if (callback == null) return null;
        return new PlaylistManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    /**
     * Create a PlaylistManager.PlaylistsCallback from a PlaylistsCallbackImpl
     */
    public static PlaylistManager.PlaylistsCallback createPlaylistManagerPlaylistsCallback(final PlaylistsCallbackImpl callback) {
        if (callback == null) return null;
        return new PlaylistManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    /**
     * Create a PlaylistManager.PlaylistCallback from a PlaylistCallbackImpl
     */
    public static PlaylistManager.PlaylistCallback createPlaylistManagerCallback(final PlaylistCallbackImpl callback) {
        if (callback == null) return null;
        return new PlaylistManager.PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess(playlist);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    /**
     * Convert from RecommendationCallback to ApiDataProvider.TracksCallback
     */
    public static ApiDataProvider.TracksCallback toApiDataProviderCallback(RecommendationCallback callback) {
        if (callback == null) return null;
        return new ApiDataProvider.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onRecommendationsReady(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    /**
     * Creates a RecommendationCallback from a CallbackImpl
     */
    public static RecommendationCallback createRecommendationCallback(CallbackImpl callback) {
        if (callback == null) return null;
        return new RecommendationCallback() {
            @Override
            public void onRecommendationsReady(List<Track> recommendations) {
                callback.onSuccess(recommendations);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    /**
     * Converts a RecommendationCallback to a TracksCallback
     */
    public static TracksCallback toTracksCallback(RecommendationCallback callback) {
        if (callback == null) return null;
        return new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onRecommendationsReady(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    public static SpotifyHelper.SpotifyTracksCallback toSpotifyTracksCallback(final TracksCallback callback) {
        if (callback == null) return null;
        return new SpotifyHelper.SpotifyTracksCallback() {
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
    
    public static ApiDataProvider.TracksCallback toApiProviderCallback(final TracksCallback callback) {
        if (callback == null) return null;
        return new ApiDataProvider.TracksCallback() {
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
    
    public static TracksCallback createUtilsTracksCallback(final CallbackImpl callback) {
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
        };
    }
    
    public static TracksCallback toDeezerTracksCallback(final ApiDataProvider.TracksCallback callback) {
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
        };
    }

    public static ApiDataProvider.TracksCallback fromApiProviderCallback(final ApiDataProvider.TracksCallback callback) {
        if (callback == null) return null;
        return new ApiDataProvider.TracksCallback() {
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

    public static DeezerTracksCallback wrapDeezerCallback(final TracksCallback callback) {
        return new DeezerTracksCallback() {
            public void onSuccess(List<Track> tracks) {
                if (callback != null) {
                    callback.onSuccess(tracks);
                }
            }
            
            public void onError(String message) {
                if (callback != null) {
                    callback.onError(message);
                }
            }
        };
    }
    
    public static SpotifyTracksCallback wrapSpotifyCallback(final TracksCallback callback) {
        return new SpotifyTracksCallback() {
            public void onSuccess(List<Track> tracks) {
                if (callback != null) {
                    callback.onSuccess(tracks);
                }
            }
            
            public void onError(String message) {
                if (callback != null) {
                    callback.onError(message);
                }
            }
        };
    }
} 