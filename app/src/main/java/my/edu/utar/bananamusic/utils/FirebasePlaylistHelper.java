package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;

/**
 * Helper class for handling Firebase Realtime Database interactions for playlists
 */
public class FirebasePlaylistHelper {
    private static final String TAG = "FirebasePlaylistHelper";
    private static FirebasePlaylistHelper instance;
    private FirebaseDatabase database;
    private DatabaseReference playlistsRef;
    private Context context;

    // Database paths
    private static final String PLAYLISTS_PATH = "playlists";
    private static final String COLLABORATIVE_PLAYLISTS_PATH = "collaborative_playlists";
    private static final String USER_PLAYLISTS_PATH = "user_playlists";
    private static final String CONNECTION_TEST_PATH = "connection_test";

    private FirebasePlaylistHelper(Context context) {
        this.context = context.getApplicationContext();
        
        try {
            // Initialize database
            database = FirebaseDatabase.getInstance();
            
            // Get reference to playlists node
            playlistsRef = database.getReference(PLAYLISTS_PATH);
            
            // Enable disk persistence for offline support
            database.setPersistenceEnabled(true);
            
            // Keep collaborative playlists synced for offline access
            database.getReference(COLLABORATIVE_PLAYLISTS_PATH).keepSynced(true);
            
            Log.d(TAG, "Firebase Playlist Helper initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase Playlist Helper: " + e.getMessage());
        }
    }

    public static synchronized FirebasePlaylistHelper getInstance(Context context) {
        if (instance == null) {
            instance = new FirebasePlaylistHelper(context);
        }
        return instance;
    }

    /**
     * Interface for playlist fetch callbacks
     */
    public interface PlaylistsCallback {
        void onSuccess(List<Playlist> playlists);
        void onError(String errorMessage);
    }
    
    /**
     * Interface for connection status callbacks
     */
    public interface ConnectionStatusCallback {
        void onConnected();
        void onDisconnected(String reason);
    }
    
    /**
     * Check Firebase Realtime Database connection status
     * 
     * @param callback Callback to handle the result
     */
    public void checkConnectionStatus(ConnectionStatusCallback callback) {
        try {
            Log.d(TAG, "Checking Firebase Realtime Database connection status...");
            
            // Create a connection test node with server timestamp
            DatabaseReference connTest = database.getReference(CONNECTION_TEST_PATH);
            Map<String, Object> testData = new HashMap<>();
            testData.put("timestamp", ServerValue.TIMESTAMP);
            testData.put("device", android.os.Build.MODEL);
            
            connTest.setValue(testData, new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                    if (databaseError == null) {
                        // Connection successful
                        Log.d(TAG, "Firebase Realtime Database connection successful");
                        callback.onConnected();
                        
                        // Clean up test data after 5 seconds
                        new android.os.Handler().postDelayed(() -> {
                            connTest.removeValue();
                        }, 5000);
                    } else {
                        // Connection failed
                        Log.e(TAG, "Firebase Realtime Database connection failed: " + databaseError.getMessage());
                        callback.onDisconnected(databaseError.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception while checking Firebase connection: " + e.getMessage());
            callback.onDisconnected("Exception: " + e.getMessage());
        }
    }

    /**
     * Fetch all collaborative playlists from Firebase
     * 
     * @param callback Callback to handle the result
     */
    public void getCollaborativePlaylists(PlaylistsCallback callback) {
        DatabaseReference collabRef = database.getReference(COLLABORATIVE_PLAYLISTS_PATH);
        
        // Order by timestamp to get the most recent ones first
        Query query = collabRef.orderByChild("lastUpdated");
        
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<Playlist> playlists = new ArrayList<>();
                
                // Process each playlist from the database
                for (DataSnapshot playlistSnapshot : dataSnapshot.getChildren()) {
                    try {
                        // Get playlist ID and data
                        String playlistId = playlistSnapshot.getKey();
                        
                        // Basic playlist info
                        String name = playlistSnapshot.child("name").getValue(String.class);
                        String description = playlistSnapshot.child("description").getValue(String.class);
                        String imageUrl = playlistSnapshot.child("imageUrl").getValue(String.class);
                        String creatorId = playlistSnapshot.child("creatorId").getValue(String.class);
                        String creatorName = playlistSnapshot.child("creatorName").getValue(String.class);
                        Boolean isCollaborative = playlistSnapshot.child("collaborative").getValue(Boolean.class);
                        Long lastUpdated = playlistSnapshot.child("lastUpdated").getValue(Long.class);
                        
                        // Get tracks first to use in constructor
                        List<Track> tracks = new ArrayList<>();
                        DataSnapshot tracksSnapshot = playlistSnapshot.child("tracks");
                        if (tracksSnapshot.exists()) {
                            for (DataSnapshot trackSnapshot : tracksSnapshot.getChildren()) {
                                Track track = parseTrackFromSnapshot(trackSnapshot);
                                if (track != null) {
                                    tracks.add(track);
                                }
                            }
                        }
                        
                        // Create playlist object with proper constructor
                        Playlist playlist = new Playlist(playlistId, tracks);
                        if (name != null) playlist.setName(name);
                        if (description != null) playlist.setDescription(description);
                        
                        // Set properties that may not have direct setters
                        setPlaylistImageUrl(playlist, imageUrl != null ? imageUrl : "");
                        setPlaylistOwnerName(playlist, creatorName != null ? creatorName : "Unknown User");
                        setPlaylistCreatorId(playlist, creatorId);
                        playlist.setSource("Firebase");
                        playlist.setCollaborative(isCollaborative != null ? isCollaborative : true);
                        
                        // Set last updated timestamp
                        if (lastUpdated != null) {
                            setPlaylistLastUpdated(playlist, lastUpdated);
                        }
                        
                        // Get collaborators
                        DataSnapshot collaboratorsSnapshot = playlistSnapshot.child("collaborators");
                        if (collaboratorsSnapshot.exists()) {
                            Map<String, String> collaborators = new HashMap<>();
                            for (DataSnapshot collaboratorSnapshot : collaboratorsSnapshot.getChildren()) {
                                String userId = collaboratorSnapshot.getKey();
                                String userName = collaboratorSnapshot.getValue(String.class);
                                if (userId != null && userName != null) {
                                    collaborators.put(userId, userName);
                                }
                            }
                            setPlaylistCollaborators(playlist, collaborators);
                        }
                        
                        playlists.add(playlist);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing playlist: " + e.getMessage());
                    }
                }
                
                // Return playlists via callback
                callback.onSuccess(playlists);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error fetching collaborative playlists: " + databaseError.getMessage());
                callback.onError(databaseError.getMessage());
            }
        });
    }
    
    /**
     * Parse a track from a DataSnapshot
     * 
     * @param snapshot DataSnapshot containing track data
     * @return Track object
     */
    private Track parseTrackFromSnapshot(DataSnapshot snapshot) {
        try {
            String trackId = snapshot.getKey();
            String title = snapshot.child("title").getValue(String.class);
            String artist = snapshot.child("artist").getValue(String.class);
            String album = snapshot.child("album").getValue(String.class);
            String imageUrl = snapshot.child("imageUrl").getValue(String.class);
            String previewUrl = snapshot.child("previewUrl").getValue(String.class);
            String spotifyId = snapshot.child("spotifyId").getValue(String.class);
            
            // Make sure we have the minimum required fields
            if (title == null || artist == null) {
                Log.e(TAG, "Missing required track fields for track ID: " + trackId);
                return null;
            }
            
            // Create track with available information - use the constructor that matches the Track class
            String source = "Firebase";
            int duration = 0; // Default duration
            boolean isPlayable = true; // Default is playable
            
            // Using the constructor: Track(String id, String title, String artist, String album, String albumArtUrl, int duration, String source, boolean isPlayable)
            Track track = new Track(
                trackId, 
                title, 
                artist, 
                album != null ? album : "", 
                imageUrl != null ? imageUrl : "", 
                duration, 
                source, 
                isPlayable
            );
            
            // Set optional fields if available
            if (previewUrl != null) track.setPreviewUrl(previewUrl);
            if (spotifyId != null) track.setSpotifyId(spotifyId);
            
            return track;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing track from snapshot: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Helper method to set playlist image URL
     */
    private void setPlaylistImageUrl(Playlist playlist, String imageUrl) {
        try {
            // Try reflection first
            java.lang.reflect.Field field = playlist.getClass().getDeclaredField("coverImageUrl");
            field.setAccessible(true);
            field.set(playlist, imageUrl);
        } catch (Exception e) {
            Log.e(TAG, "Error setting playlist image URL: " + e.getMessage());
            // Try regular method if available
            try {
                playlist.setCoverImageUrl(imageUrl);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to set playlist image URL: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Helper method to set playlist owner name
     */
    private void setPlaylistOwnerName(Playlist playlist, String ownerName) {
        try {
            // Try reflection first
            java.lang.reflect.Field field = playlist.getClass().getDeclaredField("creatorName");
            field.setAccessible(true);
            field.set(playlist, ownerName);
        } catch (Exception e) {
            Log.e(TAG, "Error setting playlist creator name: " + e.getMessage());
            // Try regular method if available
            try {
                playlist.setCreatorName(ownerName);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to set playlist creator name: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Helper method to set playlist creator ID
     */
    private void setPlaylistCreatorId(Playlist playlist, String creatorId) {
        try {
            // Try reflection first
            java.lang.reflect.Field field = playlist.getClass().getDeclaredField("creatorId");
            field.setAccessible(true);
            field.set(playlist, creatorId);
        } catch (Exception e) {
            Log.e(TAG, "Error setting playlist creator ID: " + e.getMessage());
            // Try regular method if available
            try {
                playlist.getClass().getMethod("setCreatorId", String.class).invoke(playlist, creatorId);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to set playlist creator ID: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Helper method to set playlist last updated timestamp
     */
    private void setPlaylistLastUpdated(Playlist playlist, Long lastUpdated) {
        try {
            // Try reflection first
            java.lang.reflect.Field field = playlist.getClass().getDeclaredField("lastUpdated");
            field.setAccessible(true);
            field.set(playlist, lastUpdated);
        } catch (Exception e) {
            Log.e(TAG, "Error setting playlist last updated: " + e.getMessage());
            // Try regular method if available
            try {
                playlist.getClass().getMethod("setLastUpdated", Long.class).invoke(playlist, lastUpdated);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to set playlist last updated: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Helper method to set playlist collaborators
     */
    private void setPlaylistCollaborators(Playlist playlist, Map<String, String> collaborators) {
        try {
            // Try reflection first
            java.lang.reflect.Field field = playlist.getClass().getDeclaredField("collaborators");
            field.setAccessible(true);
            field.set(playlist, collaborators);
        } catch (Exception e) {
            Log.e(TAG, "Error setting playlist collaborators: " + e.getMessage());
            // Try regular method if available
            try {
                playlist.getClass().getMethod("setCollaborators", Map.class).invoke(playlist, collaborators);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to set playlist collaborators: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Refresh collaborative playlists data
     */
    public void refreshCollaborativePlaylists(PlaylistsCallback callback) {
        DatabaseReference collabRef = database.getReference(COLLABORATIVE_PLAYLISTS_PATH);
        collabRef.keepSynced(true);
        
        // Force a refresh by adding a one-time listener
        collabRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Once refreshed, get playlists normally
                getCollaborativePlaylists(callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error refreshing collaborative playlists: " + databaseError.getMessage());
                callback.onError("Failed to refresh playlists: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Create a sample collaborative playlist for testing
     * This method is useful for populating the database with initial test data
     */
    public void createSampleCollaborativePlaylist(String userId, String userName) {
        try {
            DatabaseReference collabRef = database.getReference(COLLABORATIVE_PLAYLISTS_PATH);
            
            // Generate a unique ID for the playlist
            String playlistId = collabRef.push().getKey();
            
            if (playlistId == null) {
                Log.e(TAG, "Failed to generate playlist ID");
                return;
            }
            
            // Playlist base data
            Map<String, Object> playlistData = new HashMap<>();
            playlistData.put("name", "Sample Collaborative Playlist");
            playlistData.put("description", "This is a sample collaborative playlist created for testing");
            playlistData.put("creatorId", userId);
            playlistData.put("creatorName", userName);
            playlistData.put("collaborative", true);
            playlistData.put("coverImageUrl", "https://i.imgur.com/DvpvklR.png"); // Sample image
            playlistData.put("lastUpdated", ServerValue.TIMESTAMP);
            
            // Add collaborators (just the creator for now)
            Map<String, Object> collaborators = new HashMap<>();
            collaborators.put(userId, userName);
            playlistData.put("collaborators", collaborators);
            
            // Add some sample tracks
            Map<String, Object> tracks = new HashMap<>();
            
            // Track 1
            Map<String, Object> track1 = new HashMap<>();
            track1.put("title", "Sample Track 1");
            track1.put("artist", "Sample Artist 1");
            track1.put("album", "Sample Album 1");
            track1.put("albumArt", "https://i.imgur.com/DvpvklR.png");
            tracks.put("track1", track1);
            
            // Track 2
            Map<String, Object> track2 = new HashMap<>();
            track2.put("title", "Sample Track 2");
            track2.put("artist", "Sample Artist 2");
            track2.put("album", "Sample Album 2");
            track2.put("albumArt", "https://i.imgur.com/DvpvklR.png");
            tracks.put("track2", track2);
            
            playlistData.put("tracks", tracks);
            
            // Save the playlist to the database
            collabRef.child(playlistId).setValue(playlistData, new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                    if (databaseError == null) {
                        Log.d(TAG, "Sample collaborative playlist created successfully");
                    } else {
                        Log.e(TAG, "Error creating sample playlist: " + databaseError.getMessage());
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating sample playlist: " + e.getMessage());
        }
    }
} 