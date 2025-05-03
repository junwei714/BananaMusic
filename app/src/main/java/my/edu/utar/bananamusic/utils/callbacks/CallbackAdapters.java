package my.edu.utar.bananamusic.utils.callbacks;

import java.util.List;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.PlaylistManager;

public class CallbackAdapters {
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

    public static TracksCallback fromApiProviderCallback(final ApiDataProvider.TracksCallback callback) {
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
} 