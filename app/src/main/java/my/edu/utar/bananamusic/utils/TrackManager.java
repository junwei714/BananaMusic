package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import my.edu.utar.bananamusic.data.TrackDatabase;
import my.edu.utar.bananamusic.models.Track;

/**
 * Utility class to manage tracks, history, and caching
 */
public class TrackManager {
    private static final String TAG = "TrackManager";
    private static volatile TrackManager instance;
    private final Context context;
    private final SharedPreferences preferences;
    private static final String PREF_NAME = "track_manager_prefs";
    private static final String KEY_HISTORY = "track_history";
    private static final String KEY_CACHING_ENABLED = "caching_enabled";
    private static final String KEY_LAST_TRACK = "last_played_track";
    private static final int MAX_HISTORY_SIZE = 50;
    private final String userId;
    
    private TrackManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        this.userId = user != null ? user.getUid() : "anonymous";
    }
    
    public static synchronized TrackManager getInstance(Context context) {
        if (instance == null) {
            instance = new TrackManager(context);
        }
        return instance;
    }
    
    /**
     * Add a track to the user's history for better recommendations
     */
    public void addToHistory(Track track) {
        if (track == null) return;
        
        // Add to local history first
        List<Track> history = getLocalHistory();
        
        // Remove if already exists to avoid duplicates
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getId().equals(track.getId())) {
                history.remove(i);
                break;
            }
        }
        
        // Add to front of list
        history.add(0, track);
        
        // Trim to max size
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(0, MAX_HISTORY_SIZE);
        }
        
        // Save locally
        saveLocalHistory(history);
        
        // Save to Firebase if user is signed in
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Save to Firestore
            Map<String, Object> historyItem = new HashMap<>();
            historyItem.put("trackId", track.getId());
            historyItem.put("title", track.getTitle());
            historyItem.put("artist", track.getArtist());
            historyItem.put("artistId", track.getArtistId());
            historyItem.put("timestamp", System.currentTimeMillis());
            
            // Add genres if available
            if (track.getGenres() != null && !track.getGenres().isEmpty()) {
                historyItem.put("genres", track.getGenres());
            }
            
            // Add to Firestore
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection("history")
                .add(historyItem)
                .addOnSuccessListener(documentReference -> 
                    Log.d(TAG, "Track added to history: " + track.getTitle()))
                .addOnFailureListener(e -> 
                    Log.e(TAG, "Error adding track to history: " + e.getMessage()));
                    
            // Also add to local SQLite database for offline access
            if (isCachingEnabled()) {
                TrackDatabase.getInstance(context).addToHistory(user.getUid(), track);
            }
        }
    }
    
    /**
     * Get the user's local track history
     */
    private List<Track> getLocalHistory() {
        String historyJson = preferences.getString(KEY_HISTORY, null);
        if (historyJson != null) {
            try {
                Gson gson = new Gson();
                Type type = new TypeToken<List<Track>>(){}.getType();
                List<Track> history = gson.fromJson(historyJson, type);
                return history != null ? history : new ArrayList<>();
            } catch (Exception e) {
                Log.e(TAG, "Error parsing history JSON: " + e.getMessage());
            }
        }
        return new ArrayList<>();
    }
    
    /**
     * Save the user's local track history
     */
    private void saveLocalHistory(List<Track> history) {
        try {
            Gson gson = new Gson();
            String historyJson = gson.toJson(history);
            preferences.edit().putString(KEY_HISTORY, historyJson).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving history to JSON: " + e.getMessage());
        }
    }
    
    /**
     * Get the last played track
     */
    public Track getLastPlayedTrack() {
        String trackJson = preferences.getString(KEY_LAST_TRACK, null);
        if (trackJson != null) {
            try {
                Gson gson = new Gson();
                return gson.fromJson(trackJson, Track.class);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing last track JSON: " + e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Set the last played track
     */
    public void setLastPlayedTrack(Track track) {
        if (track == null) return;
        
        try {
            Gson gson = new Gson();
            String trackJson = gson.toJson(track);
            preferences.edit().putString(KEY_LAST_TRACK, trackJson).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving last track: " + e.getMessage());
        }
    }
    
    /**
     * Check if caching is enabled
     */
    public boolean isCachingEnabled() {
        return preferences.getBoolean(KEY_CACHING_ENABLED, true);
    }
    
    /**
     * Set caching enabled/disabled
     */
    public void setCachingEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_CACHING_ENABLED, enabled).apply();
    }
    
    /**
     * Clear all track history and cache
     */
    public void clearAllData() {
        // Clear preferences
        preferences.edit()
            .remove(KEY_HISTORY)
            .remove(KEY_LAST_TRACK)
            .apply();
            
        // Clear database cache
        TrackDatabase.getInstance(context).clearAllData();
        
        Log.d(TAG, "All track data cleared");
    }
    
    /**
     * Get most listened genres based on history
     */
    public List<String> getMostListenedGenres(int limit) {
        List<Track> history = getLocalHistory();
        Map<String, Integer> genreCounts = new HashMap<>();
        
        // Count genre occurrences
        for (Track track : history) {
            if (track.getGenres() != null) {
                for (String genre : track.getGenres()) {
                    genreCounts.put(genre, genreCounts.getOrDefault(genre, 0) + 1);
                }
            }
        }
        
        // Sort genres by count
        return genreCounts.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get a list of recently played tracks
     * @param limit Maximum number of tracks to return
     * @return List of recently played tracks
     */
    public List<Track> getRecentlyPlayedTracks(int limit) {
        List<Track> recentTracks = new ArrayList<>();
        TrackDatabase.getInstance(context).getHistory(userId, tracks -> {
            if (tracks != null) {
                recentTracks.addAll(tracks.subList(0, Math.min(tracks.size(), limit)));
            }
        });
        return recentTracks;
    }

    /**
     * Get all tracks from the database
     * @return List of all tracks
     */
    public List<Track> getAllTracks() {
        List<Track> allTracks = new ArrayList<>();
        // Get tracks from history
        TrackDatabase.getInstance(context).getHistory(userId, tracks -> {
            if (tracks != null) {
                allTracks.addAll(tracks);
            }
        });
        return allTracks;
    }

    /**
     * Cache a track in the database
     * @param track Track to cache
     */
    public void cacheTrack(Track track) {
        if (track != null) {
            TrackDatabase.getInstance(context).addToHistory(userId, track);
        }
    }
} 