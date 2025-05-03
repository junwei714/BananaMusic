package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TrackPlaybackCache {
    private static final String PREFS_NAME = "track_playback_cache";
    private static final String TRACK_URLS_KEY = "track_urls";
    private static final String PLAYBACK_POSITIONS_KEY = "playback_positions";
    private static final long CACHE_EXPIRY_TIME = 24 * 60 * 60 * 1000; // 24 hours

    private static TrackPlaybackCache instance;
    private final SharedPreferences prefs;
    private final Map<String, CachedTrackInfo> trackCache;

    private static class CachedTrackInfo {
        String previewUrl;
        String fullUrl;
        long timestamp;
        int playbackPosition;

        CachedTrackInfo(String previewUrl, String fullUrl) {
            this.previewUrl = previewUrl;
            this.fullUrl = fullUrl;
            this.timestamp = System.currentTimeMillis();
            this.playbackPosition = 0;
        }
    }

    private TrackPlaybackCache(Context context) {
        prefs = context.getApplicationContext()
                      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        trackCache = new HashMap<>();
        loadCache();
    }

    public static synchronized TrackPlaybackCache getInstance(Context context) {
        if (instance == null) {
            instance = new TrackPlaybackCache(context);
        }
        return instance;
    }

    private void loadCache() {
        try {
            String urlsJson = prefs.getString(TRACK_URLS_KEY, "{}");
            String positionsJson = prefs.getString(PLAYBACK_POSITIONS_KEY, "{}");
            
            JSONObject urlsObj = new JSONObject(urlsJson);
            JSONObject positionsObj = new JSONObject(positionsJson);
            
            long currentTime = System.currentTimeMillis();
            
            Iterator<String> keys = urlsObj.keys();
            while (keys.hasNext()) {
                String trackId = keys.next();
                JSONObject trackInfo = urlsObj.getJSONObject(trackId);
                long timestamp = trackInfo.optLong("timestamp", 0);
                
                // Skip expired entries
                if (currentTime - timestamp > CACHE_EXPIRY_TIME) {
                    continue;
                }
                
                CachedTrackInfo info = new CachedTrackInfo(
                    trackInfo.optString("preview"),
                    trackInfo.optString("full")
                );
                info.timestamp = timestamp;
                info.playbackPosition = positionsObj.optInt(trackId, 0);
                trackCache.put(trackId, info);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // If loading fails, start with empty cache
            trackCache.clear();
        }
    }

    private void saveCache() {
        try {
            JSONObject urlsObj = new JSONObject();
            JSONObject positionsObj = new JSONObject();
            
            for (Map.Entry<String, CachedTrackInfo> entry : trackCache.entrySet()) {
                String trackId = entry.getKey();
                CachedTrackInfo info = entry.getValue();
                
                JSONObject trackInfo = new JSONObject();
                trackInfo.put("preview", info.previewUrl);
                trackInfo.put("full", info.fullUrl);
                trackInfo.put("timestamp", info.timestamp);
                
                urlsObj.put(trackId, trackInfo);
                positionsObj.put(trackId, info.playbackPosition);
            }
            
            prefs.edit()
                 .putString(TRACK_URLS_KEY, urlsObj.toString())
                 .putString(PLAYBACK_POSITIONS_KEY, positionsObj.toString())
                 .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cacheTrackUrls(@NonNull String trackId, 
                              @NonNull String previewUrl,
                              @Nullable String fullUrl) {
        CachedTrackInfo info = new CachedTrackInfo(previewUrl, fullUrl);
        trackCache.put(trackId, info);
        saveCache();
    }

    @Nullable
    public String getCachedPreviewUrl(@NonNull String trackId) {
        CachedTrackInfo info = trackCache.get(trackId);
        if (info != null && System.currentTimeMillis() - info.timestamp <= CACHE_EXPIRY_TIME) {
            return info.previewUrl;
        }
        return null;
    }

    @Nullable
    public String getCachedFullUrl(@NonNull String trackId) {
        CachedTrackInfo info = trackCache.get(trackId);
        if (info != null && System.currentTimeMillis() - info.timestamp <= CACHE_EXPIRY_TIME) {
            return info.fullUrl;
        }
        return null;
    }

    public void updatePlaybackPosition(@NonNull String trackId, int position) {
        CachedTrackInfo info = trackCache.get(trackId);
        if (info != null) {
            info.playbackPosition = position;
            saveCache();
        }
    }

    public int getPlaybackPosition(@NonNull String trackId) {
        CachedTrackInfo info = trackCache.get(trackId);
        return info != null ? info.playbackPosition : 0;
    }

    public void clearCache() {
        trackCache.clear();
        prefs.edit().clear().apply();
    }

    public void removeExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        trackCache.entrySet().removeIf(entry ->
            currentTime - entry.getValue().timestamp > CACHE_EXPIRY_TIME);
        saveCache();
    }
}