package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import my.edu.utar.bananamusic.models.Track;

public class RecentlyPlayedCache {
    private static final String TAG = "RecentlyPlayedCache";
    private static final String PREF_NAME = "recently_played_cache";
    private static final String KEY_TRACKS = "tracks";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final long CACHE_VALIDITY_DURATION = 24 * 60 * 60 * 1000; // 24 hours
    private static final int MAX_TRACKS = 20;

    private static RecentlyPlayedCache instance;
    private final SharedPreferences prefs;
    private final Gson gson;

    private RecentlyPlayedCache(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized RecentlyPlayedCache getInstance(Context context) {
        if (instance == null) {
            instance = new RecentlyPlayedCache(context);
        }
        return instance;
    }

    public void cacheTracks(List<Track> tracks) {
        try {
            List<Track> existingTracks = getCachedTracks();
            List<Track> newTracks = new ArrayList<>();

            // Add new tracks at the beginning
            for (Track track : tracks) {
                if (!containsTrack(existingTracks, track)) {
                    newTracks.add(track);
                }
            }

            // Add existing tracks
            newTracks.addAll(existingTracks);

            // Trim the list if it exceeds the maximum size
            if (newTracks.size() > MAX_TRACKS) {
                newTracks = newTracks.subList(0, MAX_TRACKS);
            }

            // Save to SharedPreferences
            String json = gson.toJson(newTracks);
            prefs.edit()
                .putString(KEY_TRACKS, json)
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply();

            Log.d(TAG, "Cached " + newTracks.size() + " tracks");
        } catch (Exception e) {
            Log.e(TAG, "Error caching tracks", e);
        }
    }

    public void addTrack(Track track) {
        try {
            List<Track> tracks = getCachedTracks();

            // Remove if already exists
            tracks.removeIf(t -> t.getId().equals(track.getId()));

            // Add to beginning
            tracks.add(0, track);

            // Trim if necessary
            if (tracks.size() > MAX_TRACKS) {
                tracks = tracks.subList(0, MAX_TRACKS);
            }

            // Save
            String json = gson.toJson(tracks);
            prefs.edit()
                .putString(KEY_TRACKS, json)
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply();

            Log.d(TAG, "Added track to cache: " + track.getTitle());
        } catch (Exception e) {
            Log.e(TAG, "Error adding track to cache", e);
        }
    }

    public List<Track> getCachedTracks() {
        try {
            String json = prefs.getString(KEY_TRACKS, null);
            if (json != null) {
                Type type = new TypeToken<List<Track>>(){}.getType();
                return gson.fromJson(json, type);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached tracks", e);
        }
        return new ArrayList<>();
    }

    public boolean isCacheValid() {
        long lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0);
        return System.currentTimeMillis() - lastUpdate < CACHE_VALIDITY_DURATION;
    }

    public void clearCache() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Cache cleared");
    }

    private boolean containsTrack(List<Track> tracks, Track track) {
        return tracks.stream().anyMatch(t -> t.getId().equals(track.getId()));
    }
} 