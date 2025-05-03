package my.edu.utar.bananamusic.utils;

import java.util.List;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.providers.ApiDataProvider;

/**
 * Adapter class to convert between different TracksCallback interfaces
 * This helps bridge the gap between ApiDataProvider.TracksCallback and
 * my.edu.utar.bananamusic.utils.callbacks.TracksCallback
 */
public class TracksAdapter {

    /**
     * Convert a utils.callbacks.TracksCallback to ApiDataProvider.TracksCallback
     *
     * @param callback The utils callbacks TracksCallback to adapt
     * @return An ApiDataProvider.TracksCallback that delegates to the provided callback
     */
    public static ApiDataProvider.TracksCallback toApiCallback(my.edu.utar.bananamusic.utils.callbacks.TracksCallback callback) {
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
     * Convert an ApiDataProvider.TracksCallback to utils.callbacks.TracksCallback
     *
     * @param callback The ApiDataProvider TracksCallback to adapt
     * @return A utils.callbacks.TracksCallback that delegates to the provided callback
     */
    public static my.edu.utar.bananamusic.utils.callbacks.TracksCallback toUtilsCallback(ApiDataProvider.TracksCallback callback) {
        return new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
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
} 