package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import my.edu.utar.bananamusic.models.Track;
import com.google.firebase.firestore.FirebaseFirestoreException;

/**
 * Manager class that handles the recently played tracks functionality
 * Stores data in both SharedPreferences (local) and Firestore (cloud)
 */
public class RecentlyPlayedManager {
    private static final String TAG = "RecentlyPlayedManager";
    private static final String PREF_NAME = "recently_played_prefs";
    private static final String KEY_RECENTLY_PLAYED = "recently_played_tracks";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_LAST_SYNC = "last_sync_time";
    private static final int MAX_RECENTLY_PLAYED = 5;

    private static RecentlyPlayedManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final FirebaseFirestore db;
    private final FirebaseAuthHelper authHelper;
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    private RecentlyPlayedManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.db = FirebaseFirestore.getInstance();
        this.authHelper = FirebaseAuthHelper.getInstance();
    }

    public static synchronized RecentlyPlayedManager getInstance(Context context) {
        if (instance == null) {
            instance = new RecentlyPlayedManager(context);
        }
        return instance;
    }

    /**
     * Add a track to recently played list
     * @param track The track to add
     */
    public void addTrackToRecentlyPlayed(Track track) {
        if (track == null || track.getId() == null) {
            return;
        }

        try {
            // First, save locally
            saveTrackLocally(track);

            // Then try to save to Firestore if user is logged in
            if (authHelper.isUserLoggedIn()) {
                saveTrackToFirestore(track);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding track to recently played", e);
        }
    }

    /**
     * Save a track to local storage (SharedPreferences)
     * @param track The track to save
     */
    private void saveTrackLocally(Track track) {
        try {
            // Get current list
            List<RecentlyPlayedTrack> recentlyPlayed = getLocalRecentlyPlayed();
            
            // Create a recently played entry
            RecentlyPlayedTrack newEntry = new RecentlyPlayedTrack(
                    track.getId(),
                    track.getTitle(),
                    track.getArtist(),
                    track.getAlbum(),
                    track.getImageUrl(),
                    track.getDurationMs(),
                    System.currentTimeMillis()
            );
            
            // Remove if already in the list (to avoid duplicates)
            recentlyPlayed.removeIf(item -> item.getTrackId().equals(track.getId()));
            
            // Add to the beginning of the list
            recentlyPlayed.add(0, newEntry);
            
            // Keep only the last 5 tracks
            if (recentlyPlayed.size() > MAX_RECENTLY_PLAYED) {
                recentlyPlayed = recentlyPlayed.subList(0, MAX_RECENTLY_PLAYED);
            }
            
            // Save to SharedPreferences
            String json = gson.toJson(recentlyPlayed);
            prefs.edit().putString(KEY_RECENTLY_PLAYED, json).apply();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving track locally", e);
        }
    }

    /**
     * Save a track to Firestore cloud database
     * @param track The track to save
     */
    private void saveTrackToFirestore(Track track) {
        try {
            FirebaseUser user = authHelper.getCurrentUser();
            if (user == null) return;
            
            String uid = user.getUid();
            DocumentReference userRef = db.collection("users").document(uid);
            
            // Create a recently played entry with complete track information
            Map<String, Object> recentTrack = new HashMap<>();
            recentTrack.put("trackId", track.getId());
            recentTrack.put("title", track.getTitle() != null ? track.getTitle() : "Unknown Title");
            recentTrack.put("artist", track.getArtist() != null ? track.getArtist() : "Unknown Artist");
            recentTrack.put("album", track.getAlbum() != null ? track.getAlbum() : "Unknown Album");
            recentTrack.put("imageUrl", track.getImageUrl());
            recentTrack.put("durationMs", track.getDurationMs());
            recentTrack.put("timestamp", System.currentTimeMillis());
            recentTrack.put("type", "track");
            recentTrack.put("source", track.getSource() != null ? track.getSource() : "local");
            
            // Get the current recently played tracks
            userRef.collection("recently_played")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        // If we already have 5 or more tracks, delete the oldest one
                        if (querySnapshot.size() >= MAX_RECENTLY_PLAYED) {
                            // Get the oldest document
                            DocumentSnapshot oldestDoc = querySnapshot.getDocuments()
                                    .get(querySnapshot.size() - 1);
                            // Delete it
                            oldestDoc.getReference().delete();
                        }
                        
                        // Now add the new track
                        userRef.collection("recently_played")
                                .document(track.getId())
                                .set(recentTrack)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Track added to Firestore recently played");
                                    
                                    // Update the recentlyPlayed array in the user document
                                    userRef.get().addOnSuccessListener(documentSnapshot -> {
                                        ArrayList<String> recentlyPlayed = new ArrayList<>();
                                        if (documentSnapshot.exists() && documentSnapshot.contains("recentlyPlayed")) {
                                            // Get the data as a List<String> instead of ArrayList
                                            List<String> list = (List<String>) documentSnapshot.get("recentlyPlayed");
                                            if (list != null) {
                                                recentlyPlayed.addAll(list);
                                            }
                                        }
                                        
                                        // Remove if exists (to avoid duplicates)
                                        recentlyPlayed.remove(track.getId());
                                        
                                        // Add to the beginning
                                        recentlyPlayed.add(0, track.getId());
                                        
                                        // Keep only the last 5
                                        if (recentlyPlayed.size() > MAX_RECENTLY_PLAYED) {
                                            recentlyPlayed = new ArrayList<>(recentlyPlayed.subList(0, MAX_RECENTLY_PLAYED));
                                        }
                                        
                                        // Update Firestore with complete track info
                                        Map<String, Object> updates = new HashMap<>();
                                        updates.put("recentlyPlayed", recentlyPlayed);
                                        updates.put("lastPlayedTrack", track.getId());
                                        updates.put("lastPlayedTitle", track.getTitle());
                                        updates.put("lastPlayedArtist", track.getArtist());
                                        userRef.update(updates);
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error adding track to Firestore recently played", e);
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking recently played count", e);
                    });
                            
        } catch (Exception e) {
            Log.e(TAG, "Error saving track to Firestore", e);
        }
    }

    /**
     * Get recently played tracks
     * @param callback Callback with the list of tracks
     */
    public void getRecentlyPlayedTracks(RecentlyPlayedCallback callback) {
        try {
            if (authHelper.isUserLoggedIn() && !isSyncing.get()) {
                // Try to get from Firestore first (cloud data)
                syncWithFirestore(success -> {
                    // After sync, return the tracks from local storage
                    List<Track> tracks = convertToTracks(getLocalRecentlyPlayed());
                    callback.onTracksLoaded(tracks);
                });
            } else {
                // Just use local data
                List<Track> tracks = convertToTracks(getLocalRecentlyPlayed());
                callback.onTracksLoaded(tracks);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting recently played tracks", e);
            callback.onTracksLoaded(new ArrayList<>());
        }
    }

    /**
     * Get recently played tracks from local storage
     * @return List of recently played tracks
     */
    private List<RecentlyPlayedTrack> getLocalRecentlyPlayed() {
        String json = prefs.getString(KEY_RECENTLY_PLAYED, null);
        
        if (json == null) {
            return new ArrayList<>();
        }
        
        Type type = new TypeToken<List<RecentlyPlayedTrack>>(){}.getType();
        List<RecentlyPlayedTrack> result = gson.fromJson(json, type);
        return result != null ? result : new ArrayList<>();
    }

    /**
     * Sync local data with Firestore
     * @param callback Callback indicating sync success
     */
    private void syncWithFirestore(SyncCallback callback) {
        // Set syncing flag to prevent concurrent syncs
        if (!isSyncing.compareAndSet(false, true)) {
            // Already syncing, just return
            callback.onSyncComplete(false);
            return;
        }
        
        try {
            FirebaseUser user = authHelper.getCurrentUser();
            if (user == null) {
                isSyncing.set(false);
                callback.onSyncComplete(false);
                return;
            }
            
            String uid = user.getUid();
            
            // Check if we need to sync (only sync if it's been more than 5 minutes)
            long lastSync = prefs.getLong(KEY_LAST_SYNC, 0);
            long now = System.currentTimeMillis();
            if (now - lastSync < 5 * 60 * 1000) { // 5 minutes
                // No need to sync, it's recent enough
                isSyncing.set(false);
                callback.onSyncComplete(true);
                return;
            }
            
            // Get cloud data
            db.collection("users").document(uid)
                    .collection("recently_played")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(MAX_RECENTLY_PLAYED)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        try {
                            List<RecentlyPlayedTrack> cloudTracks = new ArrayList<>();
                            
                            for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                                Map<String, Object> data = doc.getData();
                                if (data != null) {
                                    String trackId = (String) data.get("trackId");
                                    String title = (String) data.get("title");
                                    String artist = (String) data.get("artist");
                                    String album = (String) data.get("album");
                                    String imageUrl = (String) data.get("imageUrl");
                                    long durationMs = 0;
                                    if (data.get("durationMs") instanceof Number) {
                                        durationMs = ((Number) data.get("durationMs")).longValue();
                                    }
                                    long timestamp = 0;
                                    if (data.get("timestamp") instanceof Number) {
                                        timestamp = ((Number) data.get("timestamp")).longValue();
                                    }
                                    
                                    RecentlyPlayedTrack track = new RecentlyPlayedTrack(
                                            trackId, title, artist, album, imageUrl, durationMs, timestamp);
                                    cloudTracks.add(track);
                                }
                            }
                            
                            // Merge with local data, prioritizing the most recent
                            mergeWithLocalData(cloudTracks);
                            
                            // Update last sync time
                            prefs.edit().putLong(KEY_LAST_SYNC, now).apply();
                            
                            // Clear syncing flag
                            isSyncing.set(false);
                            callback.onSyncComplete(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing cloud data", e);
                            isSyncing.set(false);
                            callback.onSyncComplete(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting cloud data", e);
                        // Set last sync time to avoid constant retries
                        prefs.edit().putLong(KEY_LAST_SYNC, now).apply();
                        isSyncing.set(false);
                        // Use local data only
                        callback.onSyncComplete(true);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error during sync", e);
            isSyncing.set(false);
            callback.onSyncComplete(false);
        }
    }

    /**
     * Merge cloud data with local data
     * @param cloudTracks Tracks from cloud
     */
    private void mergeWithLocalData(List<RecentlyPlayedTrack> cloudTracks) {
        List<RecentlyPlayedTrack> localTracks = getLocalRecentlyPlayed();
        Map<String, RecentlyPlayedTrack> mergedTracks = new HashMap<>();
        
        // Add local tracks to the map
        for (RecentlyPlayedTrack track : localTracks) {
            mergedTracks.put(track.getTrackId(), track);
        }
        
        // Add/update with cloud tracks
        for (RecentlyPlayedTrack track : cloudTracks) {
            RecentlyPlayedTrack existing = mergedTracks.get(track.getTrackId());
            if (existing == null || track.getTimestamp() > existing.getTimestamp()) {
                mergedTracks.put(track.getTrackId(), track);
            }
        }
        
        // Convert back to list and sort by timestamp (descending)
        List<RecentlyPlayedTrack> result = new ArrayList<>(mergedTracks.values());
        result.sort((t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
        
        // Trim if needed
        if (result.size() > MAX_RECENTLY_PLAYED) {
            result = result.subList(0, MAX_RECENTLY_PLAYED);
        }
        
        // Save to SharedPreferences
        String json = gson.toJson(result);
        prefs.edit().putString(KEY_RECENTLY_PLAYED, json).apply();
    }

    /**
     * Convert RecentlyPlayedTrack list to Track list
     * @param recentlyPlayed List of recently played tracks
     * @return List of Track objects
     */
    private List<Track> convertToTracks(List<RecentlyPlayedTrack> recentlyPlayed) {
        List<Track> tracks = new ArrayList<>();
        
        for (RecentlyPlayedTrack recent : recentlyPlayed) {
            Track track = new Track();
            track.setId(recent.getTrackId());
            track.setTitle(recent.getTitle() != null ? recent.getTitle() : "Unknown Title");
            track.setArtist(recent.getArtist() != null ? recent.getArtist() : "Unknown Artist");
            track.setAlbum(recent.getAlbum() != null ? recent.getAlbum() : "Unknown Album");
            track.setImageUrl(recent.getImageUrl());
            track.setDurationMs(recent.getDurationMs());
            track.setType("track"); // Set proper type
            track.setSource("local"); // Set source
            
            // Set the time ago description
            long timeSinceLastPlayed = System.currentTimeMillis() - recent.getTimestamp();
            String timeAgo = formatTimeAgo(timeSinceLastPlayed);
            track.setLastFallbackAttempt(timeAgo);
            track.setDescription(recent.getArtist() + " • " + recent.getAlbum());
            
            tracks.add(track);
        }
        
        return tracks;
    }
    
    /**
     * Format time ago in a human-readable format
     */
    private String formatTimeAgo(long milliseconds) {
        long seconds = milliseconds / 1000;
        
        if (seconds < 60) {
            return "Just now";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else if (seconds < 604800) {
            long days = seconds / 86400;
            return days + (days == 1 ? " day ago" : " days ago");
        } else if (seconds < 2592000) {
            long weeks = seconds / 604800;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        } else {
            return "Long time ago";
        }
    }

    /**
     * Clear all recently played tracks
     */
    public void clearRecentlyPlayed() {
        // Clear local data
        prefs.edit().remove(KEY_RECENTLY_PLAYED).apply();
        
        // Clear cloud data if user is logged in
        if (authHelper.isUserLoggedIn()) {
            FirebaseUser user = authHelper.getCurrentUser();
            if (user != null) {
                String uid = user.getUid();
                
                // Clear the recentlyPlayed array in the user document
                db.collection("users").document(uid)
                        .update("recentlyPlayed", new ArrayList<>())
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Cleared cloud recently played data"))
                        .addOnFailureListener(e -> Log.e(TAG, "Error clearing cloud recently played data", e));
            }
        }
    }

    /**
     * Inner class representing a recently played track
     */
    public static class RecentlyPlayedTrack {
        private String trackId;
        private String title;
        private String artist;
        private String album;
        private String imageUrl;
        private long durationMs;
        private long timestamp;

        public RecentlyPlayedTrack(String trackId, String title, String artist, String album, 
                                  String imageUrl, long durationMs, long timestamp) {
            this.trackId = trackId;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.imageUrl = imageUrl;
            this.durationMs = durationMs;
            this.timestamp = timestamp;
        }

        public String getTrackId() { return trackId; }
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getAlbum() { return album; }
        public String getImageUrl() { return imageUrl; }
        public long getDurationMs() { return durationMs; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Callback for recently played tracks
     */
    public interface RecentlyPlayedCallback {
        void onTracksLoaded(List<Track> tracks);
    }

    /**
     * Callback for sync operations
     */
    private interface SyncCallback {
        void onSyncComplete(boolean success);
    }

    /**
     * Callback for Firestore operations
     */
    private interface FirestoreCallback {
        void onCallback(boolean success);
    }

    /**
     * Get recently played tracks from Firestore
     * @param callback Callback to handle the result
     */
    private void getRecentlyPlayedFromFirestore(FirestoreCallback callback) {
        FirebaseUser user = authHelper.getCurrentUser();
        if (user == null) {
            callback.onCallback(false);
            return;
        }
        
        String uid = user.getUid();
        db.collection("users").document(uid).collection("recently_played")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(MAX_RECENTLY_PLAYED)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Process the results
                    try {
                        List<RecentlyPlayedTrack> cloudTracks = new ArrayList<>();
                        
                        // Extract and process each document
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                String trackId = (String) data.get("trackId");
                                String title = (String) data.get("title");
                                String artist = (String) data.get("artist");
                                String album = (String) data.get("album");
                                String imageUrl = (String) data.get("imageUrl");
                                long durationMs = 0;
                                if (data.get("durationMs") instanceof Number) {
                                    durationMs = ((Number) data.get("durationMs")).longValue();
                                }
                                long timestamp = 0;
                                if (data.get("timestamp") instanceof Number) {
                                    timestamp = ((Number) data.get("timestamp")).longValue();
                                }
                                
                                // Create and add track
                                RecentlyPlayedTrack track = new RecentlyPlayedTrack(
                                        trackId, title, artist, album, imageUrl, durationMs, timestamp);
                                cloudTracks.add(track);
                            }
                        }
                        
                        // Save last sync time
                        long now = System.currentTimeMillis();
                        prefs.edit().putLong(KEY_LAST_SYNC, now).apply();
                        
                        // Merge with local data, prioritizing the most recent
                        mergeWithLocalData(cloudTracks);
                        
                        Log.d(TAG, "Firestore recently played fetched: " + cloudTracks.size());
                        callback.onCallback(true);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firestore data", e);
                        callback.onCallback(false);
                    }
                })
                .addOnFailureListener(e -> {
                    // Set the last sync time to avoid continuous retries on permission failure
                    long now = System.currentTimeMillis();
                    prefs.edit().putLong(KEY_LAST_SYNC, now).apply();
                    
                    Log.e(TAG, "Error getting recently played from Firestore", e);
                    if (e instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
                        if (firestoreException.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.w(TAG, "Permission denied accessing Firestore. Using local data only.");
                        }
                    }
                    
                    // Return success even if cloud fetch fails - we'll use local data
                    callback.onCallback(true);
                });
    }
} 