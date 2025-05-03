package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.Calendar;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;

/**
 * Helper class to manage user preferences and local storage
 */
public class UserPreferences {
    private static final String TAG = "UserPreferences";
    private static final String PREF_NAME = "BananaMusicPrefs";
    
    // Preference keys
    private static final String KEY_FAVORITE_TRACKS = "favorite_tracks";
    private static final String KEY_RECENTLY_PLAYED = "recently_played";
    private static final String KEY_AUDIO_QUALITY = "audio_quality";
    private static final String KEY_DOWNLOAD_OVER_WIFI = "download_over_wifi";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_NOTIFICATIONS = "notifications";
    private static final String KEY_LAST_PLAYED_TRACK = "last_played_track";
    private static final String KEY_LAST_VOLUME = "last_volume";
    private static final String KEY_SAVED_PLAYLISTS = "saved_playlists";
    private static final String KEY_COLLABORATIVE_PLAYLISTS = "collaborative_playlists";
    private static final String KEY_SOUNDCLOUD_TOKEN = "soundcloud_token";
    private static final String KEY_SOUNDCLOUD_TOKEN_EXPIRY = "soundcloud_token_expiry";
    private static final String KEY_SPOTIFY_TOKEN = "spotify_token";
    private static final String KEY_SPOTIFY_TOKEN_EXPIRY = "spotify_token_expiry";
    private static final String KEY_CACHED_TRACKS = "cached_tracks";
    private static final String KEY_OFFLINE_MODE = "offline_mode";
    private static final String KEY_CACHE_ONLY_ON_WIFI = "cache_only_on_wifi";
    private static final String KEY_CROSSFADE_DURATION = "crossfade_duration";
    private static final String KEY_RECENTLY_RECOMMENDED = "recently_recommended";
    private static final String KEY_LAST_CACHE_REFRESH = "last_cache_refresh";
    
    // Add methods to store and retrieve Spotify user token
    private static final String PREF_SPOTIFY_USER_TOKEN = "spotify_user_token";
    private static final String PREF_SPOTIFY_USER_TOKEN_EXPIRY = "spotify_user_token_expiry";
    
    // Default values
    private static final boolean DEFAULT_DOWNLOAD_OVER_WIFI = true;
    private static final boolean DEFAULT_DARK_MODE = true;
    private static final boolean DEFAULT_NOTIFICATIONS = true;
    private static final int DEFAULT_AUDIO_QUALITY = 1; // 0=Low, 1=Normal, 2=High
    private static final int DEFAULT_VOLUME = 70;
    private static final int MAX_RECENTLY_PLAYED = 50;
    private static final boolean DEFAULT_OFFLINE_MODE = false;
    private static final boolean DEFAULT_CACHE_ONLY_ON_WIFI = true;
    private static final int DEFAULT_CROSSFADE_DURATION = 0; // 0 means no crossfade
    private static final int MAX_RECENT_RECOMMENDATIONS = 50;
    
    private static UserPreferences instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    
    private UserPreferences(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        Log.d(TAG, "UserPreferences initialized");
    }
    
    public static synchronized UserPreferences getInstance(Context context) {
        if (instance == null) {
            instance = new UserPreferences(context);
        }
        return instance;
    }
    
    /**
     * Check if a track is in the user's favorites
     * @param trackId ID of the track to check
     * @return true if the track is a favorite
     */
    public boolean isTrackFavorite(String trackId) {
        Set<String> favorites = getFavoriteTrackIds();
        return favorites.contains(trackId);
    }
    
    /**
     * Add a track to favorites
     * @param trackId ID of the track to add
     */
    public void addFavoriteTrack(String trackId) {
        Set<String> favorites = getFavoriteTrackIds();
        
        if (favorites.add(trackId)) {
            saveFavoriteTrackIds(favorites);
            Log.d(TAG, "Added track to favorites: " + trackId);
        }
    }
    
    /**
     * Remove a track from favorites
     * @param trackId ID of the track to remove
     */
    public void removeFavoriteTrack(String trackId) {
        Set<String> favorites = getFavoriteTrackIds();
        
        if (favorites.remove(trackId)) {
            saveFavoriteTrackIds(favorites);
            Log.d(TAG, "Removed track from favorites: " + trackId);
        }
    }
    
    /**
     * Get the set of favorite track IDs
     * @return Set of track IDs
     */
    public Set<String> getFavoriteTrackIds() {
        return prefs.getStringSet(KEY_FAVORITE_TRACKS, new HashSet<>());
    }
    
    /**
     * Save the set of favorite track IDs
     * @param trackIds Set of track IDs
     */
    private void saveFavoriteTrackIds(Set<String> trackIds) {
        prefs.edit().putStringSet(KEY_FAVORITE_TRACKS, trackIds).apply();
    }
    
    /**
     * Add a track to recently played
     * @param trackId ID of the track to add
     */
    public void addRecentlyPlayed(String trackId) {
        List<String> recentlyPlayed = getRecentlyPlayed();
        
        // Remove if already in the list to avoid duplicates
        recentlyPlayed.remove(trackId);
        
        // Add to the beginning of the list
        recentlyPlayed.add(0, trackId);
        
        // Trim the list if needed
        if (recentlyPlayed.size() > MAX_RECENTLY_PLAYED) {
            recentlyPlayed = recentlyPlayed.subList(0, MAX_RECENTLY_PLAYED);
        }
        
        // Save the updated list
        saveRecentlyPlayed(recentlyPlayed);
        Log.d(TAG, "Added track to recently played: " + trackId);
    }
    
    /**
     * Get the list of recently played track IDs
     * @return List of track IDs
     */
    public List<String> getRecentlyPlayed() {
        String json = prefs.getString(KEY_RECENTLY_PLAYED, null);
        
        if (json == null) {
            return new ArrayList<>();
        }
        
        Type type = new TypeToken<List<String>>(){}.getType();
        return gson.fromJson(json, type);
    }
    
    /**
     * Save the list of recently played track IDs
     * @param trackIds List of track IDs
     */
    private void saveRecentlyPlayed(List<String> trackIds) {
        String json = gson.toJson(trackIds);
        prefs.edit().putString(KEY_RECENTLY_PLAYED, json).apply();
    }
    
    /**
     * Set the last played track ID
     * @param trackId ID of the track
     */
    public void setLastPlayedTrack(String trackId) {
        prefs.edit().putString(KEY_LAST_PLAYED_TRACK, trackId).apply();
    }
    
    /**
     * Get the last played track ID
     * @return Track ID or null if no track was played
     */
    public String getLastPlayedTrack() {
        return prefs.getString(KEY_LAST_PLAYED_TRACK, null);
    }
    
    /**
     * Set the audio quality preference
     * @param quality 0=Low, 1=Normal, 2=High
     */
    public void setAudioQuality(int quality) {
        if (quality < 0 || quality > 2) {
            quality = DEFAULT_AUDIO_QUALITY;
        }
        prefs.edit().putInt(KEY_AUDIO_QUALITY, quality).apply();
    }
    
    /**
     * Get the audio quality preference
     * @return 0=Low, 1=Normal, 2=High
     */
    public int getAudioQuality() {
        return prefs.getInt(KEY_AUDIO_QUALITY, DEFAULT_AUDIO_QUALITY);
    }
    
    /**
     * Set whether to download only over WiFi
     * @param wifiOnly true to download only over WiFi
     */
    public void setDownloadOverWifiOnly(boolean wifiOnly) {
        prefs.edit().putBoolean(KEY_DOWNLOAD_OVER_WIFI, wifiOnly).apply();
    }
    
    /**
     * Check if downloads should only happen over WiFi
     * @return true if downloads should only happen over WiFi
     */
    public boolean getDownloadOverWifiOnly() {
        return prefs.getBoolean(KEY_DOWNLOAD_OVER_WIFI, DEFAULT_DOWNLOAD_OVER_WIFI);
    }
    
    /**
     * Set dark mode preference
     * @param darkMode true for dark mode
     */
    public void setDarkMode(boolean darkMode) {
        prefs.edit().putBoolean(KEY_DARK_MODE, darkMode).apply();
    }
    
    /**
     * Check if dark mode is enabled
     * @return true if dark mode is enabled
     */
    public boolean isDarkModeEnabled() {
        return prefs.getBoolean(KEY_DARK_MODE, DEFAULT_DARK_MODE);
    }
    
    /**
     * Set notifications preference
     * @param enabled true to enable notifications
     */
    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }
    
    /**
     * Check if notifications are enabled
     * @return true if notifications are enabled
     */
    public boolean areNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS, DEFAULT_NOTIFICATIONS);
    }
    
    /**
     * Set the last volume level
     * @param volume Volume level (0-100)
     */
    public void setLastVolume(int volume) {
        if (volume < 0) volume = 0;
        if (volume > 100) volume = 100;
        prefs.edit().putInt(KEY_LAST_VOLUME, volume).apply();
    }
    
    /**
     * Get the last volume level
     * @return Volume level (0-100)
     */
    public int getLastVolume() {
        return prefs.getInt(KEY_LAST_VOLUME, DEFAULT_VOLUME);
    }
    
    /**
     * Save a playlist ID to local storage
     * @param playlistId ID of the playlist to save
     */
    public void savePlaylist(String playlistId) {
        savePlaylist(playlistId, false);
    }
    
    /**
     * Save a playlist ID to local storage with collaborative status
     * @param playlistId The ID of the playlist to save
     * @param isCollaborative Whether the playlist is collaborative
     */
    public void savePlaylist(String playlistId, boolean isCollaborative) {
        // Save to general playlists
        Set<String> playlists = getSavedPlaylists();
        playlists.add(playlistId);
        saveSavedPlaylists(playlists);
        Log.d(TAG, "Saved playlist locally: " + playlistId);
        
        // If collaborative, also save to collaborative set
        if (isCollaborative) {
            addCollaborativePlaylist(playlistId);
        }
    }
    
    /**
     * Remove a playlist ID from local storage
     * @param playlistId ID of the playlist to remove
     */
    public void removePlaylist(String playlistId) {
        // Remove from regular playlist set
        Set<String> playlists = getSavedPlaylists();
        playlists.remove(playlistId);
        saveSavedPlaylists(playlists);
        
        // Also remove from collaborative set if present
        Set<String> collaborativePlaylists = getCollaborativePlaylists();
        if (collaborativePlaylists.contains(playlistId)) {
            collaborativePlaylists.remove(playlistId);
            saveCollaborativePlaylists(collaborativePlaylists);
            Log.d(TAG, "Removed collaborative playlist from local storage: " + playlistId);
        } else {
            Log.d(TAG, "Removed playlist from local storage: " + playlistId);
        }
    }
    
    /**
     * Get the set of saved playlists
     * @return Set of playlist IDs
     */
    public Set<String> getSavedPlaylists() {
        return prefs.getStringSet(KEY_SAVED_PLAYLISTS, new HashSet<>());
    }
    
    /**
     * Add a playlist to saved playlists
     * @param playlistId ID of the playlist to add
     */
    public void addSavedPlaylist(String playlistId) {
        Set<String> playlists = getSavedPlaylists();
        if (playlists.add(playlistId)) {
            saveSavedPlaylists(playlists);
            Log.d(TAG, "Added playlist to saved: " + playlistId);
        }
    }
    
    /**
     * Remove a playlist from saved playlists
     * @param playlistId ID of the playlist to remove
     */
    public void removeSavedPlaylist(String playlistId) {
        Set<String> playlists = getSavedPlaylists();
        if (playlists.remove(playlistId)) {
            saveSavedPlaylists(playlists);
            Log.d(TAG, "Removed playlist from saved: " + playlistId);
        }
    }
    
    /**
     * Get the set of collaborative playlists
     * @return Set of playlist IDs
     */
    public Set<String> getCollaborativePlaylists() {
        return prefs.getStringSet(KEY_COLLABORATIVE_PLAYLISTS, new HashSet<>());
    }
    
    /**
     * Check if a playlist is collaborative
     * @param playlistId ID of the playlist to check
     * @return true if the playlist is marked as collaborative
     */
    public boolean isPlaylistCollaborative(String playlistId) {
        Set<String> collaborativePlaylists = getCollaborativePlaylists();
        return collaborativePlaylists.contains(playlistId);
    }
    
    /**
     * Save the set of playlist IDs
     * @param playlistIds Set of playlist IDs
     */
    private void saveSavedPlaylists(Set<String> playlistIds) {
        prefs.edit().putStringSet(KEY_SAVED_PLAYLISTS, playlistIds).apply();
    }
    
    /**
     * Save the set of collaborative playlist IDs
     * @param playlistIds Set of collaborative playlist IDs
     */
    private void saveCollaborativePlaylists(Set<String> playlistIds) {
        prefs.edit().putStringSet(KEY_COLLABORATIVE_PLAYLISTS, playlistIds).apply();
    }
    
    /**
     * Save SoundCloud token and expiry time
     * @param token The SoundCloud access token
     * @param expiresAt Token expiry timestamp in milliseconds
     */
    public void saveSoundCloudToken(String token, long expiresAt) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SOUNDCLOUD_TOKEN, token);
        editor.putLong(KEY_SOUNDCLOUD_TOKEN_EXPIRY, expiresAt);
        editor.apply();
        Log.d(TAG, "Saved SoundCloud token, expires at: " + expiresAt);
    }
    
    /**
     * Get the saved SoundCloud token
     * @return The token or null if not saved
     */
    public String getSoundCloudToken() {
        return prefs.getString(KEY_SOUNDCLOUD_TOKEN, null);
    }
    
    /**
     * Get the SoundCloud token expiry timestamp
     * @return Expiry time in milliseconds
     */
    public long getSoundCloudTokenExpiry() {
        return prefs.getLong(KEY_SOUNDCLOUD_TOKEN_EXPIRY, 0);
    }
    
    /**
     * Save Spotify token and expiry time
     * @param token The Spotify access token
     * @param expiresAt Token expiry timestamp in milliseconds
     */
    public void saveSpotifyToken(String token, long expiresAt) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SPOTIFY_TOKEN, token);
        editor.putLong(KEY_SPOTIFY_TOKEN_EXPIRY, expiresAt);
        editor.apply();
        Log.d(TAG, "Saved Spotify token, expires at: " + expiresAt);
    }
    
    /**
     * Get the saved Spotify token
     * @return The token or null if not saved
     */
    public String getSpotifyToken() {
        return prefs.getString(KEY_SPOTIFY_TOKEN, null);
    }
    
    /**
     * Get the Spotify token expiry timestamp
     * @return Expiry time in milliseconds
     */
    public long getSpotifyTokenExpiry() {
        return prefs.getLong(KEY_SPOTIFY_TOKEN_EXPIRY, 0);
    }
    
    /**
     * Clear all token data (for logout)
     */
    public void clearTokens() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_SOUNDCLOUD_TOKEN);
        editor.remove(KEY_SOUNDCLOUD_TOKEN_EXPIRY);
        editor.remove(KEY_SPOTIFY_TOKEN);
        editor.remove(KEY_SPOTIFY_TOKEN_EXPIRY);
        editor.apply();
        Log.d(TAG, "Cleared all token data");
    }
    
    /**
     * Clear all preferences
     */
    public void clearAll() {
        prefs.edit().clear().apply();
        Log.d(TAG, "All preferences cleared");
    }
    
    /**
     * Get set of cached track IDs
     */
    public Set<String> getCachedTracks() {
        return prefs.getStringSet(KEY_CACHED_TRACKS, new HashSet<>());
    }
    
    /**
     * Set cached track IDs
     */
    public void setCachedTracks(Set<String> trackIds) {
        prefs.edit().putStringSet(KEY_CACHED_TRACKS, trackIds).apply();
    }
    
    /**
     * Check if offline mode is enabled
     */
    public boolean isOfflineModeEnabled() {
        return prefs.getBoolean(KEY_OFFLINE_MODE, DEFAULT_OFFLINE_MODE);
    }
    
    /**
     * Set offline mode
     */
    public void setOfflineMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply();
    }
    
    /**
     * Check if caching is only allowed on WiFi
     */
    public boolean getCacheOnlyOnWifi() {
        return prefs.getBoolean(KEY_CACHE_ONLY_ON_WIFI, DEFAULT_CACHE_ONLY_ON_WIFI);
    }
    
    /**
     * Set whether caching should only happen on WiFi
     */
    public void setCacheOnlyOnWifi(boolean wifiOnly) {
        prefs.edit().putBoolean(KEY_CACHE_ONLY_ON_WIFI, wifiOnly).apply();
    }
    
    /**
     * Get the crossfade duration in milliseconds
     * @return Crossfade duration (0 = disabled)
     */
    public int getCrossfadeDuration() {
        return prefs.getInt(KEY_CROSSFADE_DURATION, DEFAULT_CROSSFADE_DURATION);
    }
    
    /**
     * Set the crossfade duration
     * @param durationMs Duration in milliseconds (0 to disable)
     */
    public void setCrossfadeDuration(int durationMs) {
        if (durationMs < 0) {
            durationMs = DEFAULT_CROSSFADE_DURATION;
        }
        prefs.edit().putInt(KEY_CROSSFADE_DURATION, durationMs).apply();
    }
    
    /**
     * Save the Spotify user token and its expiry time
     * @param token The OAuth token
     * @param expiresIn Seconds until token expires
     */
    public void saveSpotifyUserToken(String token, int expiresIn) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_SPOTIFY_USER_TOKEN, token);
        
        // Calculate expiry time
        long expiryTime = System.currentTimeMillis() + (expiresIn * 1000L);
        editor.putLong(PREF_SPOTIFY_USER_TOKEN_EXPIRY, expiryTime);
        
        editor.apply();
    }
    
    /**
     * Get the saved Spotify user token
     * @return The token, or null if it's not saved or has expired
     */
    public String getSpotifyUserToken() {
        long expiryTime = prefs.getLong(PREF_SPOTIFY_USER_TOKEN_EXPIRY, 0);
        
        // Check if token has expired
        if (System.currentTimeMillis() > expiryTime) {
            // Token has expired, clear it and return null
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(PREF_SPOTIFY_USER_TOKEN);
            editor.remove(PREF_SPOTIFY_USER_TOKEN_EXPIRY);
            editor.apply();
            return null;
        }
        
        return prefs.getString(PREF_SPOTIFY_USER_TOKEN, null);
    }
    
    /**
     * Clear the saved Spotify user token
     */
    public void clearSpotifyUserToken() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PREF_SPOTIFY_USER_TOKEN);
        editor.remove(PREF_SPOTIFY_USER_TOKEN_EXPIRY);
        editor.apply();
    }
    
    /**
     * Generic method to get a boolean preference with a default value
     * @param key The preference key
     * @param defaultValue The default value if preference doesn't exist
     * @return The boolean preference value
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }
    
    /**
     * Generic method to set a boolean preference
     * @param key The preference key
     * @param value The value to set
     */
    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }
    
    /**
     * Generic method to get an integer preference with a default value
     * @param key The preference key
     * @param defaultValue The default value if preference doesn't exist
     * @return The integer preference value
     */
    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }
    
    /**
     * Generic method to set an integer preference
     * @param key The preference key
     * @param value The value to set
     */
    public void setInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }
    
    /**
     * Add a playlist to collaborative playlists
     * @param playlistId ID of the playlist to add
     */
    public void addCollaborativePlaylist(String playlistId) {
        Set<String> playlists = getCollaborativePlaylists();
        if (playlists.add(playlistId)) {
            saveCollaborativePlaylists(playlists);
            Log.d(TAG, "Added playlist to collaborative: " + playlistId);
            
            // Also add to saved playlists for consistency
            addSavedPlaylist(playlistId);
        }
    }
    
    /**
     * Remove a playlist from collaborative playlists
     * @param playlistId ID of the playlist to remove
     */
    public void removeCollaborativePlaylist(String playlistId) {
        Set<String> playlists = getCollaborativePlaylists();
        if (playlists.remove(playlistId)) {
            saveCollaborativePlaylists(playlists);
            Log.d(TAG, "Removed playlist from collaborative: " + playlistId);
        }
    }
    
    /**
     * Get the set of recently recommended track IDs to avoid repetition
     */
    public Set<String> getRecentlyRecommendedTracks() {
        return prefs.getStringSet(KEY_RECENTLY_RECOMMENDED, new HashSet<>());
    }
    
    /**
     * Add tracks to the recently recommended list
     */
    public void addToRecentlyRecommended(List<Track> tracks) {
        Set<String> recentlyRecommended = new HashSet<>(getRecentlyRecommendedTracks());
        
        // Add new track IDs
        for (Track track : tracks) {
            recentlyRecommended.add(track.getId());
        }
        
        // Keep only the most recent tracks to avoid the set growing too large
        if (recentlyRecommended.size() > MAX_RECENT_RECOMMENDATIONS) {
            // Convert to list for easier manipulation
            List<String> trackIds = new ArrayList<>(recentlyRecommended);
            // Keep only the most recent MAX_RECENT_RECOMMENDATIONS entries
            trackIds = trackIds.subList(trackIds.size() - MAX_RECENT_RECOMMENDATIONS, trackIds.size());
            recentlyRecommended = new HashSet<>(trackIds);
        }
        
        prefs.edit().putStringSet(KEY_RECENTLY_RECOMMENDED, recentlyRecommended).apply();
    }
    
    /**
     * Get the timestamp of the last cache refresh
     */
    public long getLastCacheRefreshTime() {
        return prefs.getLong(KEY_LAST_CACHE_REFRESH, 0);
    }
    
    /**
     * Set the timestamp of the last cache refresh
     */
    public void setLastCacheRefreshTime(long timestamp) {
        prefs.edit().putLong(KEY_LAST_CACHE_REFRESH, timestamp).apply();
    }
    
    /**
     * Get a mood for recommendation variety that changes each day
     */
    public String getRecommendationVarietyMood() {
        String[] moods = {"energetic", "chill", "happy", "sad", "focus", "workout", "party"};
        Calendar calendar = Calendar.getInstance();
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        return moods[dayOfYear % moods.length];
    }

    public void saveLastPlayedPlaylist(String playlistId) {
        prefs.edit().putString("last_played_playlist", playlistId).apply();
    }

    public String getLastPlayedPlaylist() {
        return prefs.getString("last_played_playlist", null);
    }

    public void savePlaybackPosition(long position) {
        prefs.edit().putLong("playback_position", position).apply();
    }

    public long getPlaybackPosition() {
        return prefs.getLong("playback_position", 0);
    }
} 