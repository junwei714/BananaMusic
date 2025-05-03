package my.edu.utar.bananamusic.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.TrackHistory;
import my.edu.utar.bananamusic.utils.TrackManager;

/**
 * Manages track history and listening patterns
 */
public class HistoryManager {
    private static final String TAG = "HistoryManager";
    private static final String PREF_NAME = "history_manager_prefs";
    private static final String KEY_HISTORY = "track_history";
    private static final int MAX_HISTORY_SIZE = 1000;

    private static HistoryManager instance;
    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson;
    private final Map<String, TrackHistory> historyMap;
    private final TrackManager trackManager;

    private HistoryManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.historyMap = new ConcurrentHashMap<>();
        this.trackManager = TrackManager.getInstance(context);
        loadHistory();
    }

    public static synchronized HistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryManager(context);
        }
        return instance;
    }

    private void loadHistory() {
        String json = preferences.getString(KEY_HISTORY, null);
        if (json != null) {
            try {
                Type type = new TypeToken<List<TrackHistory>>(){}.getType();
                List<TrackHistory> historyList = gson.fromJson(json, type);
                for (TrackHistory history : historyList) {
                    Track track = history.getTrack();
                    if (track != null && track.getId() != null) {
                        historyMap.put(track.getId(), history);
                        trackManager.cacheTrack(track);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading history: " + e.getMessage());
            }
        }
    }

    private void saveHistory() {
        List<TrackHistory> historyList = new ArrayList<>(historyMap.values());
        String json = gson.toJson(historyList);
        preferences.edit().putString(KEY_HISTORY, json).apply();
    }

    public void addToHistory(Track track) {
        if (track == null || track.getId() == null) {
            return;
        }

        TrackHistory history = historyMap.get(track.getId());
        if (history == null) {
            history = new TrackHistory(track);
            historyMap.put(track.getId(), history);
        } else {
            history.incrementPlayCount();
        }

        trackManager.cacheTrack(track);
        saveHistory();

        // Trim history if needed
        if (historyMap.size() > MAX_HISTORY_SIZE) {
            List<TrackHistory> sortedHistory = new ArrayList<>(historyMap.values());
            Collections.sort(sortedHistory, (h1, h2) -> Long.compare(h2.getLastPlayed(), h1.getLastPlayed()));
            
            while (historyMap.size() > MAX_HISTORY_SIZE) {
                TrackHistory oldest = sortedHistory.remove(sortedHistory.size() - 1);
                historyMap.remove(oldest.getTrack().getId());
            }
            
            saveHistory();
        }
    }

    public List<Track> getRecentTracks(int limit) {
        List<TrackHistory> sortedHistory = new ArrayList<>(historyMap.values());
        Collections.sort(sortedHistory, (h1, h2) -> Long.compare(h2.getLastPlayed(), h1.getLastPlayed()));

        List<Track> recentTracks = new ArrayList<>();
        int count = Math.min(limit, sortedHistory.size());
        for (int i = 0; i < count; i++) {
            recentTracks.add(sortedHistory.get(i).getTrack());
        }
        return recentTracks;
    }

    public List<Track> getMostPlayedTracks(int limit) {
        List<TrackHistory> sortedHistory = new ArrayList<>(historyMap.values());
        Collections.sort(sortedHistory, (h1, h2) -> Integer.compare(h2.getPlayCount(), h1.getPlayCount()));

        List<Track> mostPlayed = new ArrayList<>();
        int count = Math.min(limit, sortedHistory.size());
        for (int i = 0; i < count; i++) {
            mostPlayed.add(sortedHistory.get(i).getTrack());
        }
        return mostPlayed;
    }

    public TrackHistory getTrackHistory(String trackId) {
        return historyMap.get(trackId);
    }

    public int getPlayCount(String trackId) {
        TrackHistory history = historyMap.get(trackId);
        return history != null ? history.getPlayCount() : 0;
    }

    public long getLastPlayed(String trackId) {
        TrackHistory history = historyMap.get(trackId);
        return history != null ? history.getLastPlayed() : 0;
    }

    public void clearHistory() {
        historyMap.clear();
        saveHistory();
    }

    public int getHistorySize() {
        return historyMap.size();
    }

    public Map<String, Integer> getPlayCounts() {
        Map<String, Integer> playCounts = new ConcurrentHashMap<>();
        for (Map.Entry<String, TrackHistory> entry : historyMap.entrySet()) {
            playCounts.put(entry.getKey(), entry.getValue().getPlayCount());
        }
        return playCounts;
    }
}