package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SearchHistoryManager {
    private static final String PREF_NAME = "search_history_prefs";
    private static final String KEY_SEARCH_HISTORY = "search_history";
    private static final int MAX_HISTORY_ITEMS = 10;

    private final SharedPreferences preferences;
    private final Gson gson;

    public SearchHistoryManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public List<String> getSearchHistory() {
        String json = preferences.getString(KEY_SEARCH_HISTORY, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void addSearchQuery(String query) {
        List<String> history = getSearchHistory();
        
        // Remove if exists (to move it to the top)
        history.remove(query);
        
        // Add to the beginning
        history.add(0, query);
        
        // Trim if exceeds max size
        if (history.size() > MAX_HISTORY_ITEMS) {
            history = history.subList(0, MAX_HISTORY_ITEMS);
        }
        
        saveSearchHistory(history);
    }

    public void removeSearchQuery(String query) {
        List<String> history = getSearchHistory();
        history.remove(query);
        saveSearchHistory(history);
    }

    public void clearSearchHistory() {
        preferences.edit().remove(KEY_SEARCH_HISTORY).apply();
    }

    private void saveSearchHistory(List<String> history) {
        String json = gson.toJson(history);
        preferences.edit().putString(KEY_SEARCH_HISTORY, json).apply();
    }
} 