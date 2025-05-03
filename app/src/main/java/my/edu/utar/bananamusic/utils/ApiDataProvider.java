package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.utils.callbacks.TrackInfoCallback;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapters;
import my.edu.utar.bananamusic.utils.DummyDataProvider;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.utils.DeezerHelper;

/**
 * Provider for API data operations
 */
public class ApiDataProvider {
    private static final String TAG = "ApiDataProvider";
    private static ApiDataProvider instance;
    private final Context context;
    private final Handler mainHandler;
    private final PlaylistManager playlistManager;
    private final Random random;
    private final RecentlyPlayedManager recentlyPlayedManager;

    private ApiDataProvider(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.playlistManager = PlaylistManager.getInstance(context);
        this.recentlyPlayedManager = RecentlyPlayedManager.getInstance(context);
        this.random = new Random();
    }

    public static ApiDataProvider getInstance(Context context) {
        if (instance == null) {
            instance = new ApiDataProvider(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Get recently played tracks
     */
    public void getRecentlyPlayed(int limit, TracksCallback callback) {
        try {
            recentlyPlayedManager.getRecentlyPlayedTracks(new RecentlyPlayedManager.RecentlyPlayedCallback() {
                @Override
                public void onTracksLoaded(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(tracks));
                    } else {
                        // If no recently played tracks, get featured content
                        getFeaturedContent(callback);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error getting recently played tracks: " + e.getMessage());
            mainHandler.post(() -> callback.onError("Failed to get recently played tracks"));
        }
    }

    /**
     * Get tracks for a playlist
     */
    public void getPlaylistTracks(String playlistId, final TracksCallback callback) {
        playlistManager.getPlaylistTracks(playlistId, new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                mainHandler.post(() -> callback.onSuccess(tracks));
            }
            
            @Override
            public void onError(String message) {
                mainHandler.post(() -> callback.onError(message));
            }

            @Override
            public void onTracksLoaded(List<Track> tracks) {
                onSuccess(tracks);
            }
        });
    }

    /**
     * Get featured content
     */
    public void getFeaturedContent(TracksCallback callback) {
        // For now, return recommended tracks
        getRecommendedTracks(null, 20, callback);
    }

    /**
     * Get recommended tracks based on query and limit
     */
    public void getRecommendedTracks(String query, int limit, TracksCallback callback) {
        // Simulate API call with dummy data
        List<Track> recommendedTracks = generateDummyTracks(limit);
        mainHandler.post(() -> callback.onSuccess(recommendedTracks));
    }

    /**
     * Get varied recommendations based on mood
     */
    public void getVariedRecommendations(String mood, int limit, TracksCallback callback) {
        // Simulate API call with dummy data
        List<Track> recommendations = generateDummyTracks(limit);
        mainHandler.post(() -> callback.onSuccess(recommendations));
    }

    /**
     * Get featured albums
     */
    public void getFeaturedAlbums(TracksCallback callback) {
        // Simulate API call with dummy data
        List<Track> featuredTracks = generateDummyTracks(10);
        mainHandler.post(() -> callback.onSuccess(featuredTracks));
    }

    /**
     * Get featured albums by mood
     */
    public void getFeaturedAlbumsByMood(String mood, TracksCallback callback) {
        // Simulate API call with dummy data
        List<Track> moodTracks = generateDummyTracks(8);
        mainHandler.post(() -> callback.onSuccess(moodTracks));
    }

    /**
     * Get personalized recommendations
     */
    public void getPersonalizedRecommendations(TracksCallback callback) {
        // Simulate API call with dummy data
        List<Track> personalizedTracks = generateDummyTracks(15);
        mainHandler.post(() -> callback.onSuccess(personalizedTracks));
    }

    /**
     * Get track info
     */
    public void getTrackInfo(String trackId, TrackInfoCallback callback) {
        // Simulate API call with dummy data
        Track track = generateDummyTrack();
        track.setId(trackId);
        mainHandler.post(() -> callback.onTrackInfoLoaded(track));
    }

    /**
     * Generate dummy tracks for testing
     */
    private List<Track> generateDummyTracks(int count) {
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tracks.add(generateDummyTrack());
        }
        return tracks;
    }

    /**
     * Generate a dummy track for testing
     */
    private Track generateDummyTrack() {
        Track track = new Track();
        track.setId("track_" + random.nextInt(1000));
        track.setTitle("Sample Track " + random.nextInt(100));
        track.setArtist("Sample Artist " + random.nextInt(20));
        track.setAlbum("Sample Album " + random.nextInt(10));
        track.setDuration((180 + random.nextInt(180)) * 1000L);
        track.setImageUrl("https://example.com/cover_" + random.nextInt(10) + ".jpg");
        return track;
    }
}