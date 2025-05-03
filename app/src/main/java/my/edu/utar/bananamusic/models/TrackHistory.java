package my.edu.utar.bananamusic.models;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Represents the history of a track, including when it was last played
 * and how many times it has been played.
 */
public class TrackHistory implements Serializable {    private static final String TAG = "TrackHistory";
    private static final String PREF_NAME = "track_history";
    private static final String KEY_HISTORY = "history_list";
    private static final int MAX_HISTORY_SIZE = 100;
    
    private static TrackHistory instance;
    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson;
    private List<TrackHistoryItem> historyList;
    
    private Track track;
    private long lastPlayed;
    private int playCount;
    
    public static class TrackHistoryItem implements Serializable {
        private Track track;
        private long lastPlayed;
        private int playCount;
        
        public TrackHistoryItem(Track track) {
            this.track = track;
            this.lastPlayed = System.currentTimeMillis();
            this.playCount = 1;
        }
        
        public Track getTrack() { return track; }
        public long getLastPlayed() { return lastPlayed; }
        public int getPlayCount() { return playCount; }
        
        public void incrementPlayCount() {
            this.playCount++;
            this.lastPlayed = System.currentTimeMillis();
        }
    }
    
    protected TrackHistory(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.historyList = loadHistory();
    }

    public TrackHistory(Track track) {
        if (instance == null) {
            throw new IllegalStateException("TrackHistory must be initialized with Context first");
        }
        this.context = instance.context;
        this.preferences = instance.preferences;
        this.gson = instance.gson;
        this.historyList = instance.historyList;
        this.track = track;
        this.lastPlayed = System.currentTimeMillis();
        this.playCount = 1;
    }
    
    public static synchronized TrackHistory getInstance(Context context) {
        if (instance == null) {
            instance = new TrackHistory(context);
        }
        return instance;
    }

    private List<TrackHistoryItem> loadHistory() {
        String json = preferences.getString(KEY_HISTORY, null);
        if (json != null) {
            try {
                Type type = new TypeToken<ArrayList<TrackHistoryItem>>(){}.getType();
                return gson.fromJson(json, type);
            } catch (Exception e) {
                Log.e(TAG, "Error loading history: " + e.getMessage());
            }
        }
        return new ArrayList<>();
    }
    
    private void saveHistory() {
        String json = gson.toJson(historyList);
        preferences.edit().putString(KEY_HISTORY, json).apply();
    }
    
    public void addTrack(Track track) {
        // Find existing entry
        TrackHistoryItem existingItem = null;
        for (TrackHistoryItem item : historyList) {
            if (item.getTrack().getId().equals(track.getId())) {
                existingItem = item;
                break;
            }
        }
        
        if (existingItem != null) {
            // Update existing entry
            existingItem.incrementPlayCount();
            // Move to front of list
            historyList.remove(existingItem);
            historyList.add(0, existingItem);
        } else {
            // Add new entry
            TrackHistoryItem newItem = new TrackHistoryItem(track);
            historyList.add(0, newItem);
            
            // Trim list if needed
            if (historyList.size() > MAX_HISTORY_SIZE) {
                historyList = historyList.subList(0, MAX_HISTORY_SIZE);
            }
        }
        
        // Save changes
        saveHistory();
    }
    
    public List<Track> getRecentTracks(int limit) {
        List<Track> tracks = new ArrayList<>();
        int count = Math.min(limit, historyList.size());
        
        for (int i = 0; i < count; i++) {
            tracks.add(historyList.get(i).getTrack());
        }
        
        return tracks;
    }
    
    public List<Track> getMostPlayedTracks(int limit) {
        // Sort by play count
        List<TrackHistoryItem> sortedList = new ArrayList<>(historyList);
        Collections.sort(sortedList, new Comparator<TrackHistoryItem>() {
            @Override
            public int compare(TrackHistoryItem item1, TrackHistoryItem item2) {
                return Integer.compare(item2.getPlayCount(), item1.getPlayCount());
            }
        });
        
        // Convert to track list
        List<Track> tracks = new ArrayList<>();
        int count = Math.min(limit, sortedList.size());
        
        for (int i = 0; i < count; i++) {
            tracks.add(sortedList.get(i).getTrack());
        }
        
        return tracks;
    }
    
    public Track getTrack() {
        return track;
    }

    public void setTrack(Track track) {
        this.track = track;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        this.playCount = playCount;
    }

    public void incrementPlayCount() {
        this.playCount++;
        this.lastPlayed = System.currentTimeMillis();
    }
}