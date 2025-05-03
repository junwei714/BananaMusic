package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback;
import my.edu.utar.bananamusic.utils.callbacks.OperationCallback;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;

/**
 * Manages playlist operations
 */
public class PlaylistManager {
    private static final String TAG = "PlaylistManager";
    
    // Firestore collection names
    private static final String COLLECTION_PLAYLISTS = "playlists";
    private static final String COLLECTION_TRACKS = "tracks";
    private static final String COLLECTION_USERS = "users";
    
    private static PlaylistManager instance;
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
    private final FirebaseAuth auth;
    private final UserPreferences userPreferences;
    private final Context context;
    
    private final Map<String, ListenerRegistration> playlistListeners = new HashMap<>();
    
    public interface PlaylistCallback {
        void onSuccess(Playlist playlist);
        void onError(String errorMessage);
    }

    public interface PlaylistsCallback {
        void onSuccess(List<Playlist> playlists);
        void onError(String message);
    }

    public interface OperationCallback {
        void onSuccess(String message);
        void onError(String message);
    }
    
    /**
     * Callback interface for track operations within PlaylistManager
     */
    public interface TracksCallback {
        /**
         * Called when tracks are successfully loaded
         * @param tracks List of tracks
         */
        void onSuccess(List<Track> tracks);
        
        /**
         * Called when an error occurs
         * @param message Error message
         */
        void onError(String message);
    }
    
    private PlaylistManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.userPreferences = UserPreferences.getInstance(context);
    }
    
    public static synchronized PlaylistManager getInstance(Context context) {
        if (instance == null) {
            instance = new PlaylistManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Create a new playlist
     */
    public void createPlaylist(Playlist playlist, Uri coverImageUri, PlaylistCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }

        // Upload cover image if provided
        if (coverImageUri != null) {
            uploadCoverImage(playlist.getPlaylistId(), coverImageUri, new UploadCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    playlist.setCoverImageUrl(imageUrl);
                    savePlaylistToFirestore(playlist, callback);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error uploading cover image: " + error);
                    savePlaylistToFirestore(playlist, callback);
                }
            });
        } else {
            savePlaylistToFirestore(playlist, callback);
        }
    }
    
    /**
     * Create a new playlist with string parameters
     */
    public void createPlaylist(String name, String description, boolean collaborative, 
                              String mood, Uri coverImageUri, PlaylistCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }
        
        // Create a new playlist with the exact format
        Map<String, Object> playlistData = new HashMap<>();
        
        // Generate UUID for playlist ID
        String playlistId = java.util.UUID.randomUUID().toString();
        playlistData.put("playlistId", playlistId);
        
        // Add creator info
        playlistData.put("creatorId", currentUser.getUid());
        playlistData.put("creatorName", currentUser.getDisplayName() != null ? 
                        currentUser.getDisplayName() : "Anonymous");
        
        // Add basic info
        playlistData.put("name", name);
        playlistData.put("description", description);
        playlistData.put("mood", mood);
        playlistData.put("isCollaborative", collaborative);
        playlistData.put("collected", false);
        
        // Initialize collaborators array with creator
        List<String> collaborators = new ArrayList<>();
        collaborators.add(currentUser.getUid());
        playlistData.put("collaborators", collaborators);
        
        // Initialize empty track data, IDs and votes
        playlistData.put("trackData", new HashMap<>());
        playlistData.put("trackIds", new ArrayList<>());
        playlistData.put("trackVotes", new HashMap<>());
        
        // Set creation timestamp
        playlistData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date()));
        
        // If cover image provided, upload it first
        if (coverImageUri != null) {
            uploadCoverImage(playlistId, coverImageUri, new UploadCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    playlistData.put("coverImageUrl", imageUrl);
                    saveNewPlaylistToFirestore(playlistId, playlistData, callback);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error uploading cover image: " + error);
                    saveNewPlaylistToFirestore(playlistId, playlistData, callback);
                }
            });
        } else {
            saveNewPlaylistToFirestore(playlistId, playlistData, callback);
        }
    }
    
    /**
     * Update an existing playlist with individual parameters
     */
    public void updatePlaylist(String playlistId, String name, String description,
                              boolean collaborative, String mood,
                              Uri newCoverImageUri, PlaylistCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }

        // First get the existing playlist
        getPlaylist(playlistId, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                // Update the playlist with new values
                playlist.setName(name);
                playlist.setDescription(description);
                playlist.setCollaborative(collaborative);
                playlist.setMood(mood);
                playlist.setLastUpdated(System.currentTimeMillis());

                // Now use the existing updatePlaylist method
                updatePlaylist(playlist, newCoverImageUri, callback);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError("Failed to retrieve playlist for update: " + errorMessage);
            }
        });
    }
    
    /**
     * Update an existing playlist
     */
    public void updatePlaylist(Playlist playlist, Uri newCoverImageUri, PlaylistCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }

        // Upload new cover image if provided
        if (newCoverImageUri != null) {
            uploadCoverImage(playlist.getPlaylistId(), newCoverImageUri, new UploadCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    playlist.setCoverImageUrl(imageUrl);
                    savePlaylistToFirestore(playlist, callback);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error uploading new cover image: " + error);
                    savePlaylistToFirestore(playlist, callback);
                }
            });
        } else {
            savePlaylistToFirestore(playlist, callback);
        }
    }
    
    /**
     * Delete a playlist
     */
    public void deletePlaylist(String playlistId, PlaylistCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }

        db.collection("playlists").document(playlistId)
            .delete()
            .addOnSuccessListener(aVoid -> {
                // Delete cover image if exists
                deletePlaylistCoverImage(playlistId);
                
                // Remove from user's saved playlists
                removePlaylistFromUserSaved(currentUser.getUid(), playlistId);
                
                callback.onSuccess(null);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error deleting playlist: " + e.getMessage());
                callback.onError("Failed to delete playlist: " + e.getMessage());
            });
    }
    
    /**
     * Delete a playlist with internal OperationCallback
     */
    public void deletePlaylist(String playlistId, OperationCallback callback) {
        deletePlaylist(playlistId, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess("Playlist deleted successfully");
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }
    
    /**
     * Delete a playlist with utils.callbacks version
     */
    public void deletePlaylist(String playlistId, my.edu.utar.bananamusic.utils.callbacks.OperationCallback callback) {
        deletePlaylist(playlistId, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess("Playlist deleted successfully");
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }
    
    /**
     * Get tracks for a playlist
     */
    public void getPlaylistTracks(String playlistId, my.edu.utar.bananamusic.utils.callbacks.TracksCallback callback) {
        // Check if user is authenticated first
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "getPlaylistTracks: No user logged in");
            callback.onError("User not authenticated");
            return;
        }

        Log.d(TAG, "Getting tracks for playlist: " + playlistId);
        
        // Get the playlist document which contains trackData
        db.collection(COLLECTION_PLAYLISTS).document(playlistId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    try {
                        // Get the playlist object
                        Playlist playlist = documentSnapshot.toObject(Playlist.class);
                        List<Track> tracks = new ArrayList<>();
                        
                        if (playlist != null) {
                            // Get trackData map directly from the document
                            Map<String, Object> trackData = (Map<String, Object>) documentSnapshot.get("trackData");
                            
                            if (trackData != null && !trackData.isEmpty()) {
                                Log.d(TAG, "Found trackData with " + trackData.size() + " tracks");
                                
                                // Extract tracks from trackData and create Track objects
                                for (Map.Entry<String, Object> entry : trackData.entrySet()) {
                                    String trackId = entry.getKey();
                                    Map<String, Object> trackInfo = (Map<String, Object>) entry.getValue();
                                    
                                    if (trackInfo != null) {
                                        Track track = new Track();
                                        track.setTrackId(trackId);
                                        track.setId(trackId);
                                        track.setTitle((String) trackInfo.get("title"));
                                        track.setArtist((String) trackInfo.get("artist"));
                                        track.setAlbum((String) trackInfo.get("album"));
                                        track.setAlbumArtUrl((String) trackInfo.get("albumArtUrl"));
                                        track.setPreviewUrl((String) trackInfo.get("previewUrl"));
                                        track.setSpotifyId((String) trackInfo.get("spotifyId"));
                                        track.setSource((String) trackInfo.get("source"));
                                        
                                        // Handle duration - it might be a Long or Integer
                                        Object durationObj = trackInfo.get("duration");
                                        if (durationObj != null) {
                                            long durationMs = 0;
                                            if (durationObj instanceof Long) {
                                                durationMs = (Long) durationObj;
                                            } else if (durationObj instanceof Integer) {
                                                durationMs = (Integer) durationObj;
                                            } else if (durationObj instanceof String) {
                                                try {
                                                    durationMs = Long.parseLong((String) durationObj);
                                                } catch (NumberFormatException e) {
                                                    Log.w(TAG, "Failed to parse duration string: " + durationObj);
                                                }
                                            }
                                            track.setDuration(durationMs);
                                        }
                                        
                                        tracks.add(track);
                                    }
                                }
                                
                                // Sort tracks according to trackIds order if available
                                if (playlist.getTrackIds() != null && !playlist.getTrackIds().isEmpty()) {
                                    List<String> trackIds = playlist.getTrackIds();
                                    // Create a map of trackId to position in trackIds for sorting
                                    Map<String, Integer> trackPositions = new HashMap<>();
                                    for (int i = 0; i < trackIds.size(); i++) {
                                        trackPositions.put(trackIds.get(i), i);
                                    }
                                    
                                    // Sort tracks based on their position in trackIds
                                    Collections.sort(tracks, (t1, t2) -> {
                                        Integer pos1 = trackPositions.get(t1.getTrackId());
                                        Integer pos2 = trackPositions.get(t2.getTrackId());
                                        
                                        // Handle null cases (tracks not in trackIds)
                                        if (pos1 == null && pos2 == null) return 0;
                                        if (pos1 == null) return 1;  // Push unknown tracks to end
                                        if (pos2 == null) return -1;
                                        
                                        return pos1.compareTo(pos2);
                                    });
                                }
                                
                                // Update playlist cover with first track's album art if no cover is set
                                if ((playlist.getCoverImageUrl() == null || playlist.getCoverImageUrl().isEmpty()) 
                                    && !tracks.isEmpty() && tracks.get(0).getAlbumArtUrl() != null) {
                                    String firstTrackCover = tracks.get(0).getAlbumArtUrl();
                                    db.collection(COLLECTION_PLAYLISTS).document(playlistId)
                                        .update("coverImageUrl", firstTrackCover)
                                        .addOnSuccessListener(aVoid -> 
                                            Log.d(TAG, "Updated playlist cover with first track album art"))
                                        .addOnFailureListener(e -> 
                                            Log.e(TAG, "Error updating playlist cover: " + e.getMessage()));
                                }
                                
                                Log.d(TAG, "Processed " + tracks.size() + " tracks for playlist: " + playlistId);
                                callback.onSuccess(tracks);
                            } else if (playlist.getTrackIds() != null && !playlist.getTrackIds().isEmpty()) {
                                // If trackData is missing but trackIds exists, handle this case separately
                                Log.w(TAG, "No trackData found, but trackIds exist. Trying to retrieve tracks individually.");
                                getTracksFromIds(playlist.getTrackIds(), callback);
                            } else {
                                // No tracks in this playlist
                                Log.d(TAG, "No tracks found in playlist: " + playlistId);
                                callback.onSuccess(new ArrayList<>());
                            }
                        } else {
                            callback.onError("Failed to parse playlist data");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing playlist data: " + e.getMessage(), e);
                        callback.onError("Error processing tracks: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "Playlist not found: " + playlistId);
                    callback.onError("Playlist not found");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting playlist: " + e.getMessage());
                callback.onError("Failed to get playlist: " + e.getMessage());
            });
    }
    
    /**
     * Helper method to get tracks from a list of track IDs
     */
    private void getTracksFromIds(List<String> trackIds, my.edu.utar.bananamusic.utils.callbacks.TracksCallback callback) {
        List<Track> tracks = new ArrayList<>();
        final int[] completedQueries = {0};
        
        for (String trackId : trackIds) {
            db.collection(COLLECTION_TRACKS).document(trackId)
                .get()
                .addOnSuccessListener(trackSnapshot -> {
                    if (trackSnapshot.exists()) {
                        Track track = trackSnapshot.toObject(Track.class);
                        if (track != null) {
                            track.setTrackId(trackSnapshot.getId());
                            tracks.add(track);
                        }
                    }
                    
                    completedQueries[0]++;
                    // When all queries are complete, return the tracks
                    if (completedQueries[0] >= trackIds.size()) {
                        Log.d(TAG, "Retrieved " + tracks.size() + " tracks from IDs");
                        callback.onSuccess(tracks);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting track " + trackId + ": " + e.getMessage());
                    completedQueries[0]++;
                    
                    // When all queries are complete, return whatever tracks we have
                    if (completedQueries[0] >= trackIds.size()) {
                        Log.d(TAG, "Retrieved " + tracks.size() + " tracks with some errors");
                        callback.onSuccess(tracks);
                    }
                });
        }
    }
    
    /**
     * Get user's playlists
     */
    public void getUserPlaylists(PlaylistsCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "getUserPlaylists: No user logged in");
            callback.onError("User not authenticated");
            return;
        }

        String userId = currentUser.getUid();
        Log.d(TAG, "Querying user playlists for ID: " + userId);

        // Query playlists where the user is the creator (instead of user's collection)
        db.collection(COLLECTION_PLAYLISTS)
            .whereEqualTo("creatorId", userId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<Playlist> playlists = new ArrayList<>();
                for (DocumentSnapshot document : queryDocumentSnapshots) {
                    String playlistId = document.getId();
                    Log.d(TAG, "Found user playlist: " + playlistId);
                    
                    Playlist playlist = document.toObject(Playlist.class);
                    if (playlist != null) {
                        // Ensure playlistId is set
                        playlist.setPlaylistId(playlistId);
                        playlists.add(playlist);
                    }
                }
                Log.d(TAG, "Retrieved " + playlists.size() + " user playlists");
                callback.onSuccess(playlists);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting user playlists: " + e.getMessage());
                callback.onError("Failed to get playlists: " + e.getMessage());
            });
    }
    
    /**
     * Get user's playlists with utils.callbacks version
     */
    public void getUserPlaylists(my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback callback) {
        getUserPlaylists(new PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Get collaborative playlists
     */
    public void getCollaborativePlaylists(PlaylistsCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "getCollaborativePlaylists: No user logged in");
            callback.onError("User not authenticated");
            return;
        }

        String userId = currentUser.getUid();
        Log.d(TAG, "Querying collaborative playlists for user: " + userId);

        // Query playlists that are collaborative
        db.collection(COLLECTION_PLAYLISTS)
            .whereEqualTo("isCollaborative", true)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<Playlist> playlists = new ArrayList<>();
                for (DocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        Log.d(TAG, "Processing collaborative playlist: " + document.getId());
                        Playlist playlist = new Playlist();
                        updatePlaylistFromDocument(document, playlist);
                        
                        // Add playlist if it has tracks
                        if (playlist.getTrackCount() > 0) {
                            Log.d(TAG, "Adding playlist " + playlist.getPlaylistId() + 
                                  " with " + playlist.getTrackCount() + " tracks");
                        } else {
                            Log.w(TAG, "Playlist " + playlist.getPlaylistId() + 
                                  " has no tracks, but data shows: trackData=" + 
                                  (document.contains("trackData") ? "present" : "missing") + 
                                  ", trackIds=" + (document.contains("trackIds") ? "present" : "missing"));
                        }
                        playlists.add(playlist);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing collaborative playlist: " + e.getMessage());
                    }
                }
                callback.onSuccess(playlists);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading collaborative playlists: " + e.getMessage());
                callback.onError("Error loading collaborative playlists: " + e.getMessage());
            });
    }
    
    /**
     * Get collaborative playlists with utils.callbacks version
     */
    public void getCollaborativePlaylists(my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback callback) {
        getCollaborativePlaylists(new PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Get collaborative playlists that the user has collected
     *
     * @param callback Callback to notify when playlists are loaded
     */
    public void getCollectedCollaborativePlaylists(PlaylistsCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }

        // Get playlists from user's collection
        db.collection(COLLECTION_USERS).document(currentUser.getUid())
            .collection(COLLECTION_PLAYLISTS)
            .whereEqualTo("collaborative", true)  // Filter for collaborative playlists
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<Playlist> playlists = new ArrayList<>();
                queryDocumentSnapshots.forEach(document -> {
                    Playlist playlist = document.toObject(Playlist.class);
                    if (playlist != null) {
                        playlists.add(playlist);
                    }
                });
                callback.onSuccess(playlists);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting collected collaborative playlists: " + e.getMessage());
                callback.onError("Failed to get collaborative playlists: " + e.getMessage());
            });
    }
    
    /**
     * Get collaborative playlists that the user has collected with utils.callbacks version
     */
    public void getCollectedCollaborativePlaylists(my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback callback) {
        getCollectedCollaborativePlaylists(new PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Add playlist to library
     */
    public void addToLibrary(Playlist playlist, PlaylistCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }

        db.collection(COLLECTION_USERS).document(currentUser.getUid())
            .collection(COLLECTION_PLAYLISTS).document(playlist.getPlaylistId())
            .set(playlist)
            .addOnSuccessListener(aVoid -> callback.onSuccess(playlist))
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error adding playlist to library: " + e.getMessage());
                callback.onError("Failed to add playlist to library: " + e.getMessage());
            });
    }
    
    /**
     * Remove playlist from library
     */
    public void removeFromLibrary(String playlistId, PlaylistCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }

        db.collection(COLLECTION_USERS).document(currentUser.getUid())
            .collection(COLLECTION_PLAYLISTS).document(playlistId)
            .delete()
            .addOnSuccessListener(aVoid -> callback.onSuccess(null))
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error removing playlist from library: " + e.getMessage());
                callback.onError("Failed to remove playlist from library: " + e.getMessage());
            });
    }
    
    /**
     * Repair collaborative playlists
     */
    public void repairCollaborativePlaylists(OperationCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }

        try {
            db.collection(COLLECTION_PLAYLISTS)
                .whereEqualTo("collaborative", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        List<Playlist> playlists = new ArrayList<>();
                        queryDocumentSnapshots.forEach(document -> {
                            Playlist playlist = document.toObject(Playlist.class);
                            playlists.add(playlist);
                        });
                        
                        if (playlists.isEmpty()) {
                            callback.onSuccess("No collaborative playlists to repair");
                            return;
                        }
                        
                        // Update all playlists
                        WriteBatch batch = db.batch();
                        for (Playlist playlist : playlists) {
                            DocumentReference playlistRef = db.collection(COLLECTION_PLAYLISTS)
                                .document(playlist.getPlaylistId());
                            batch.update(playlistRef, "lastRepaired", System.currentTimeMillis());
                        }
                        
                        batch.commit()
                            .addOnSuccessListener(aVoid -> callback.onSuccess("Repaired " + playlists.size() + " playlists"))
                            .addOnFailureListener(e -> callback.onError("Failed to repair playlists: " + e.getMessage()));
                    } catch (Exception e) {
                        Log.e(TAG, "Error repairing collaborative playlists: " + e.getMessage());
                        callback.onError("Error during repair: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error repairing collaborative playlists: " + e.getMessage());
                    callback.onError(e.getMessage());
                });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    /**
     * Repair collaborative playlists using the utils.callbacks version
     */
    public void repairCollaborativePlaylists(my.edu.utar.bananamusic.utils.callbacks.OperationCallback callback) {
        repairCollaborativePlaylists(new OperationCallback() {
            @Override
            public void onSuccess(String message) {
                callback.onSuccess(message);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Add playlist listener
     *
     * @return
     */
    public ListenerRegistration addPlaylistListener(String playlistId, PlaylistCallback callback) {
        if (playlistListeners.containsKey(playlistId)) {
            removePlaylistListener(playlistId);
        }

        ListenerRegistration registration = db.collection(COLLECTION_PLAYLISTS)
            .document(playlistId)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    Log.e(TAG, "Error listening to playlist: " + error.getMessage());
                    callback.onError("Failed to listen to playlist: " + error.getMessage());
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    Playlist playlist = snapshot.toObject(Playlist.class);
                    if (playlist != null) {
                        callback.onSuccess(playlist);
                    }
                }
            });

        playlistListeners.put(playlistId, registration);
        return registration;
    }
    
    /**
     * Remove playlist listener
     */
    public void removePlaylistListener(String playlistId) {
        ListenerRegistration registration = playlistListeners.remove(playlistId);
        if (registration != null) {
            registration.remove();
        }
    }
    
    /**
     * Check if playlist is in library
     */
    public boolean isPlaylistCollected(String playlistId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return false;
        }

        AtomicBoolean isCollected = new AtomicBoolean(false);
        db.collection(COLLECTION_USERS).document(currentUser.getUid())
            .collection(COLLECTION_PLAYLISTS).document(playlistId)
            .get()
            .addOnSuccessListener(documentSnapshot -> 
                isCollected.set(documentSnapshot != null && documentSnapshot.exists()))
            .addOnFailureListener(e -> 
                Log.e(TAG, "Error checking if playlist is collected: " + e.getMessage()));

        return isCollected.get();
    }
    
    /**
     * Update all playlist covers
     */
    public void updateAllPlaylistCovers() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        db.collection(COLLECTION_PLAYLISTS)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                WriteBatch batch = db.batch();
                queryDocumentSnapshots.forEach(document -> {
                    Playlist playlist = document.toObject(Playlist.class);
                    if (playlist != null && (playlist.getCoverImageUrl() == null || playlist.getCoverImageUrl().isEmpty())) {
                        DocumentReference docRef = document.getReference();
                        Map<String, Object> updates = new HashMap<>();
                        
                        // Get trackData to check for first track's album art
                        Map<String, Object> trackData = (Map<String, Object>) document.get("trackData");
                        if (trackData != null && !trackData.isEmpty()) {
                            // Try to get the first track's album art
                            List<String> trackIds = playlist.getTrackIds();
                            if (trackIds != null && !trackIds.isEmpty()) {
                                String firstTrackId = trackIds.get(0);
                                Map<String, Object> firstTrackData = (Map<String, Object>) trackData.get(firstTrackId);
                                if (firstTrackData != null && firstTrackData.containsKey("albumArtUrl")) {
                                    String albumArtUrl = (String) firstTrackData.get("albumArtUrl");
                                    if (albumArtUrl != null && !albumArtUrl.isEmpty()) {
                                        updates.put("coverImageUrl", albumArtUrl);
                                        batch.update(docRef, updates);
                                        return; // Skip using default cover
                                    }
                                }
                            }
                        }
                        
                        // If no track album art available, use default cover based on mood
                        String defaultCoverUrl = getDefaultCoverUrlForMood(playlist.getMood());
                        updates.put("coverImageUrl", defaultCoverUrl);
                        batch.update(docRef, updates);
                    }
                });
                
                batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Successfully updated playlist covers");
                    })
                    .addOnFailureListener(e -> 
                        Log.e(TAG, "Error updating playlist covers: " + e.getMessage()));
            })
            .addOnFailureListener(e -> 
                Log.e(TAG, "Error getting playlists: " + e.getMessage()));
    }
    
    /**
     * Interface for upload callbacks
     */
    private interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onError(String error);
    }
    
    /**
     * Upload a cover image to Firebase Storage
     */
    private void uploadCoverImage(String playlistId, Uri imageUri, UploadCallback callback) {
        StorageReference storageRef = storage.getReference()
            .child("playlist_covers")
            .child(playlistId + ".jpg");

        storageRef.putFile(imageUri)
            .continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }
                return storageRef.getDownloadUrl();
            })
            .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error uploading cover image: " + e.getMessage());
                callback.onError(e.getMessage());
            });
    }
    
    /**
     * Delete a playlist's cover image from Firebase Storage
     */
    private void deletePlaylistCoverImage(String playlistId) {
        StorageReference storageRef = storage.getReference()
            .child("playlist_covers")
            .child(playlistId + ".jpg");

        storageRef.delete()
            .addOnFailureListener(e -> 
                Log.e(TAG, "Error deleting cover image: " + e.getMessage()));
    }
    
    /**
     * Remove a playlist from user's saved playlists
     */
    private void removePlaylistFromUserSaved(String userId, String playlistId) {
        db.collection("users").document(userId)
            .collection("playlists").document(playlistId)
            .delete()
            .addOnFailureListener(e -> 
                Log.e(TAG, "Error removing playlist from user saved: " + e.getMessage()));
    }
    
    /**
     * Save a playlist to Firestore
     */
    private void savePlaylistToFirestore(Playlist playlist, PlaylistCallback callback) {
        if (playlist == null || playlist.getPlaylistId() == null) {
            callback.onError("Invalid playlist");
            return;
        }

        // Create a map with the exact format
        Map<String, Object> updates = new HashMap<>();
        
        // Add all fields in the specified format
        updates.put("playlistId", playlist.getPlaylistId());
        updates.put("name", playlist.getName());
        updates.put("description", playlist.getDescription());
        updates.put("coverImageUrl", playlist.getCoverImageUrl());
        updates.put("creatorId", playlist.getCreatorId());
        updates.put("creatorName", playlist.getCreatorName());
        updates.put("mood", playlist.getMood());
        updates.put("isCollaborative", playlist.isCollaborative());
        updates.put("collaborators", playlist.getCollaborators() != null ? 
                    playlist.getCollaborators() : new ArrayList<>());
        updates.put("trackData", playlist.getTrackData() != null ? 
                    playlist.getTrackData() : new HashMap<>());
        updates.put("trackIds", playlist.getTrackIds() != null ? 
                    playlist.getTrackIds() : new ArrayList<>());
        
        // Update the document
        db.collection(COLLECTION_PLAYLISTS)
            .document(playlist.getPlaylistId())
            .update(updates)
            .addOnSuccessListener(aVoid -> callback.onSuccess(playlist))
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating playlist: " + e.getMessage());
                callback.onError("Failed to update playlist: " + e.getMessage());
            });
    }

    /**
     * Get trending playlists
     * 
     * @param callback Callback to notify when playlists are loaded
     */
    public void getTrendingPlaylists(PlaylistsCallback callback) {
        db.collection(COLLECTION_PLAYLISTS)
            .whereEqualTo("public", true)
            .orderBy("lastUpdated", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<Playlist> trendingPlaylists = new ArrayList<>();
                queryDocumentSnapshots.forEach(document -> {
                    Playlist playlist = document.toObject(Playlist.class);
                    if (playlist != null) {
                        trendingPlaylists.add(playlist);
                    }
                });
                callback.onSuccess(trendingPlaylists);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting trending playlists: " + e.getMessage());
                callback.onError("Failed to get trending playlists: " + e.getMessage());
            });
    }
    
    /**
     * Populate playlists from Spotify API
     * 
     * @param callback Callback to notify when playlists are loaded
     */
    public void populateFromSpotify(PlaylistsCallback callback) {
        SpotifyHelper spotifyHelper = SpotifyHelper.getInstance(context);
        if (spotifyHelper == null) {
            callback.onError("SpotifyHelper not available");
            return;
        }
        
        spotifyHelper.getFeaturedPlaylists(new SpotifyHelper.SpotifyPlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                // Process the playlists and save them to Firestore
                Log.d(TAG, "Got " + playlists.size() + " playlists from Spotify");
                saveSpotifyPlaylists(playlists, callback);
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error getting Spotify playlists: " + errorMessage);
                callback.onError("Failed to get Spotify playlists: " + errorMessage);
            }
        });
    }
    
    /**
     * Save multiple Spotify playlists to Firestore
     */
    private void saveSpotifyPlaylists(List<Playlist> playlists, PlaylistsCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }
        
        WriteBatch batch = db.batch();
        List<Playlist> savedPlaylists = new ArrayList<>();
        
        for (Playlist playlist : playlists) {
            // Set as Spotify source
            playlist.setSource(Playlist.SOURCE_SPOTIFY);
            
            // Reference to playlist document
            DocumentReference playlistRef = db.collection(COLLECTION_PLAYLISTS)
                .document(playlist.getPlaylistId());
            
            // Add to batch
            batch.set(playlistRef, playlist);
            
            // Reference to user's playlists collection
            DocumentReference userPlaylistRef = db.collection(COLLECTION_USERS)
                .document(currentUser.getUid())
                .collection(COLLECTION_PLAYLISTS)
                .document(playlist.getPlaylistId());
            
            // Add to user's playlists
            batch.set(userPlaylistRef, playlist);
            
            savedPlaylists.add(playlist);
        }
        
        // Commit the batch
        batch.commit()
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Successfully saved " + savedPlaylists.size() + " Spotify playlists");
                callback.onSuccess(savedPlaylists);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving Spotify playlists: " + e.getMessage());
                callback.onError("Failed to save Spotify playlists: " + e.getMessage());
            });
    }

    /**
     * Get all playlists (user playlists and collaborative playlists)
     *
     * @param callback Callback to notify when playlists are loaded
     */
    public void getAllPlaylists(PlaylistsCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }

        // First get user playlists
        db.collection(COLLECTION_USERS).document(currentUser.getUid())
            .collection(COLLECTION_PLAYLISTS)
            .get()
            .addOnSuccessListener(userPlaylistsSnapshots -> {
                List<Playlist> allPlaylists = new ArrayList<>();
                
                // Add user playlists to the list
                userPlaylistsSnapshots.forEach(document -> {
                    Playlist playlist = document.toObject(Playlist.class);
                    if (playlist != null) {
                        allPlaylists.add(playlist);
                    }
                });
                
                // Then get collaborative playlists
                db.collection(COLLECTION_PLAYLISTS)
                    .whereEqualTo("collaborative", true)
                    .get()
                    .addOnSuccessListener(collaborativePlaylistsSnapshots -> {
                        Set<String> userPlaylistIds = allPlaylists.stream()
                            .map(Playlist::getPlaylistId)
                            .collect(Collectors.toSet());
                            
                        // Add collaborative playlists if not already in the list
                        collaborativePlaylistsSnapshots.forEach(document -> {
                            Playlist playlist = document.toObject(Playlist.class);
                            if (playlist != null && !userPlaylistIds.contains(playlist.getPlaylistId())) {
                                allPlaylists.add(playlist);
                            }
                        });
                        
                        callback.onSuccess(allPlaylists);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting collaborative playlists: " + e.getMessage());
                        // Still return user playlists
                        callback.onSuccess(allPlaylists);
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting user playlists: " + e.getMessage());
                callback.onError("Failed to get playlists: " + e.getMessage());
            });
    }

    /**
     * Save a track and add it to a playlist
     * 
     * @param playlistId The ID of the playlist to add the track to
     * @param track The track to save and add
     * @param callback Callback to notify when the operation is complete
     */
    public void saveTrackAndAddToPlaylist(String playlistId, Track track, PlaylistCallback callback) {
        if (playlistId == null || track == null) {
            callback.onError("Invalid playlist ID or track");
            return;
        }

        // First save the track to the tracks collection
        db.collection(COLLECTION_PLAYLISTS)
            .document(playlistId)
            .collection(COLLECTION_TRACKS)
            .document(track.getTrackId())
            .set(track)
            .addOnSuccessListener(aVoid -> {
                // Then update the playlist to include the track
                db.collection(COLLECTION_PLAYLISTS)
                    .document(playlistId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Playlist playlist = documentSnapshot.toObject(Playlist.class);
                            if (playlist != null) {
                                // Add track ID to playlist if not already present
                                if (playlist.getTrackIds() == null) {
                                    playlist.setTrackIds(new ArrayList<>());
                                }
                                if (!playlist.getTrackIds().contains(track.getTrackId())) {
                                    playlist.getTrackIds().add(track.getTrackId());
                                }
                                
                                // Update track count
                                playlist.setTrackCount(playlist.getTrackIds().size());
                                
                                // Get or create trackData map
                                Map<String, Object> trackData = playlist.getTrackData();
                                if (trackData == null) {
                                    trackData = new HashMap<>();
                                }
                                
                                // Create track data in the exact format specified
                                Map<String, Object> trackMap = new HashMap<>();
                                trackMap.put("album", track.getAlbum());
                                trackMap.put("albumArtUrl", track.getAlbumArtUrl());
                                trackMap.put("artist", track.getArtist());
                                trackMap.put("duration", track.getDuration());
                                trackMap.put("previewUrl", track.getPreviewUrl());
                                trackMap.put("source", track.getSource());
                                trackMap.put("spotifyId", track.getSpotifyId());
                                trackMap.put("title", track.getTitle());
                                
                                // Add track data to map using track ID as key
                                trackData.put(track.getTrackId(), trackMap);
                                playlist.setTrackData(trackData);
                                
                                // Initialize or update track votes
                                Map<String, Integer> trackVotes = playlist.getTrackVotes();
                                if (trackVotes == null) {
                                    trackVotes = new HashMap<>();
                                }
                                if (!trackVotes.containsKey(track.getTrackId())) {
                                    trackVotes.put(track.getTrackId(), 0);
                                }
                                playlist.setTrackVotes(trackVotes);
                                
                                // Prepare update data
                                Map<String, Object> updateData = new HashMap<>();
                                updateData.put("trackIds", playlist.getTrackIds());
                                updateData.put("trackCount", playlist.getTrackCount());
                                updateData.put("trackData", trackData);
                                updateData.put("trackVotes", trackVotes);
                                updateData.put("lastUpdated", System.currentTimeMillis());
                                
                                // If this is the first track, or no cover image exists, use its album art as the playlist cover
                                if ((playlist.getTrackIds().size() == 1 || playlist.getCoverImageUrl() == null || playlist.getCoverImageUrl().isEmpty()) 
                                    && track.getAlbumArtUrl() != null) {
                                    updateData.put("coverImageUrl", track.getAlbumArtUrl());
                                    playlist.setCoverImageUrl(track.getAlbumArtUrl());
                                }
                                
                                // Save updated playlist with all fields
                                db.collection(COLLECTION_PLAYLISTS)
                                    .document(playlistId)
                                    .update(updateData)
                                    .addOnSuccessListener(v -> callback.onSuccess(playlist))
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error updating playlist: " + e.getMessage());
                                        callback.onError("Failed to update playlist: " + e.getMessage());
                                    });
                            } else {
                                callback.onError("Failed to parse playlist");
                            }
                        } else {
                            callback.onError("Playlist not found");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting playlist: " + e.getMessage());
                        callback.onError("Failed to get playlist: " + e.getMessage());
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving track: " + e.getMessage());
                callback.onError("Failed to save track: " + e.getMessage());
            });
    }

    /**
     * Remove a track from a playlist
     * 
     * @param playlistId The ID of the playlist to remove the track from
     * @param trackId The ID of the track to remove
     * @param callback Callback to notify when the operation is complete
     */
    public void removeTrackFromPlaylist(String playlistId, String trackId, PlaylistCallback callback) {
        if (playlistId == null || trackId == null) {
            callback.onError("Invalid playlist ID or track ID");
            return;
        }

        // First remove the track from the tracks collection
        db.collection(COLLECTION_PLAYLISTS)
            .document(playlistId)
            .collection(COLLECTION_TRACKS)
            .document(trackId)
            .delete()
            .addOnSuccessListener(aVoid -> {
                // Then update the playlist to remove the track ID
                db.collection(COLLECTION_PLAYLISTS)
                    .document(playlistId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Playlist playlist = documentSnapshot.toObject(Playlist.class);
                            if (playlist != null) {
                                // Remove track ID from playlist if present
                                if (playlist.getTrackIds() != null) {
                                    playlist.getTrackIds().remove(trackId);
                                }
                                
                                // Update track count
                                playlist.setTrackCount(playlist.getTrackIds() != null ? 
                                    playlist.getTrackIds().size() : 0);
                                
                                // Save updated playlist
                                savePlaylistToFirestore(playlist, callback);
                            } else {
                                callback.onError("Failed to parse playlist");
                            }
                        } else {
                            callback.onError("Playlist not found");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting playlist: " + e.getMessage());
                        callback.onError("Failed to get playlist: " + e.getMessage());
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error removing track: " + e.getMessage());
                callback.onError("Failed to remove track: " + e.getMessage());
            });
    }

    /**
     * Get trending playlists using the utils.callbacks.PlaylistsCallback
     */
    public void getTrendingPlaylists(my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback callback) {
        getTrendingPlaylists(new PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Get all playlists
     */
    public void getAllPlaylists(my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback callback) {
        getAllPlaylists(new PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Create a playlist with more parameters
     */
    public void createPlaylist(String name, String description, boolean collaborative, String mood, 
                              Uri coverImageUri, my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback callback) {
        // Create a new playlist object
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }
        
        Playlist playlist = new Playlist();
        playlist.setPlaylistId(db.collection(COLLECTION_PLAYLISTS).document().getId());
        playlist.setName(name);
        playlist.setDescription(description);
        playlist.setCollaborative(collaborative);
        playlist.setMood(mood);
        playlist.setCreatorId(currentUser.getUid());
        playlist.setCreatorName(currentUser.getDisplayName());
        playlist.setTrackCount(0);
        playlist.setPublic(true);
        playlist.setLastUpdated(System.currentTimeMillis());
        
        // Call the existing method
        createPlaylist(playlist, coverImageUri, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess(playlist);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Populate playlists from Spotify with utils.callbacks version
     */
    public void populateFromSpotify(my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback callback) {
        populateFromSpotify(new PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Save a track and add it to a playlist using utils.callbacks version
     */
    public void saveTrackAndAddToPlaylist(String playlistId, Track track, my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback callback) {
        saveTrackAndAddToPlaylist(playlistId, track, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess(playlist);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Remove a track from a playlist using utils.callbacks version
     */
    public void removeTrackFromPlaylist(String playlistId, String trackId, my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback callback) {
        removeTrackFromPlaylist(playlistId, trackId, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess(playlist);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    /**
     * Add multiple tracks to a playlist
     * 
     * @param playlistId The ID of the playlist to add the tracks to
     * @param tracks The tracks to add
     * @param callback Callback to notify when the operation is complete
     */
    public void addTracksToPlaylist(String playlistId, List<Track> tracks, PlaylistCallback callback) {
        if (playlistId == null || tracks == null || tracks.isEmpty()) {
            callback.onError("Invalid playlist ID or tracks");
            return;
        }

        // First get the playlist
        db.collection(COLLECTION_PLAYLISTS)
            .document(playlistId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Playlist playlist = documentSnapshot.toObject(Playlist.class);
                    if (playlist != null) {
                        // Add all tracks in a batch
                        WriteBatch batch = db.batch();
                        
                        // Initialize track IDs list if needed
                        if (playlist.getTrackIds() == null) {
                            playlist.setTrackIds(new ArrayList<>());
                        }
                        
                        // Add each track to the batch and update the track IDs list
                        for (Track track : tracks) {
                            if (!playlist.getTrackIds().contains(track.getTrackId())) {
                                playlist.getTrackIds().add(track.getTrackId());
                                
                                // Add track document to playlist's tracks collection
                                DocumentReference trackRef = db.collection(COLLECTION_PLAYLISTS)
                                    .document(playlistId)
                                    .collection(COLLECTION_TRACKS)
                                    .document(track.getTrackId());
                                
                                batch.set(trackRef, track);
                            }
                        }
                        
                        // Update track count
                        playlist.setTrackCount(playlist.getTrackIds().size());
                        
                        // Update the playlist document
                        DocumentReference playlistRef = db.collection(COLLECTION_PLAYLISTS)
                            .document(playlistId);
                        batch.set(playlistRef, playlist);
                        
                        // Commit the batch
                        batch.commit()
                            .addOnSuccessListener(aVoid -> callback.onSuccess(playlist))
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error adding tracks to playlist: " + e.getMessage());
                                callback.onError("Failed to add tracks to playlist: " + e.getMessage());
                            });
                    } else {
                        callback.onError("Failed to parse playlist");
                    }
                } else {
                    callback.onError("Playlist not found");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting playlist: " + e.getMessage());
                callback.onError("Failed to get playlist: " + e.getMessage());
            });
    }
    
    /**
     * Add multiple tracks to a playlist with utils.callbacks version
     */
    public void addTracksToPlaylist(String playlistId, List<Track> tracks, my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback callback) {
        addTracksToPlaylist(playlistId, tracks, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess(playlist);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Get a playlist by its ID
     * @param playlistId The playlist ID
     * @param callback Callback for the operation result
     */
    public void getPlaylist(String playlistId, PlaylistCallback callback) {
        if (playlistId == null || playlistId.isEmpty()) {
            callback.onError("Invalid playlist ID");
            return;
        }

        db.collection(COLLECTION_PLAYLISTS)
            .document(playlistId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    try {
                        Playlist playlist = new Playlist();
                        updatePlaylistFromDocument(documentSnapshot, playlist);
                        callback.onSuccess(playlist);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing playlist: " + e.getMessage());
                        callback.onError("Error parsing playlist: " + e.getMessage());
                    }
                } else {
                    callback.onError("Playlist not found");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting playlist: " + e.getMessage());
                callback.onError("Error getting playlist: " + e.getMessage());
            });
    }
    
    /**
     * Vote for a track in a playlist
     * @param playlistId The playlist ID
     * @param trackId The track ID
     * @param isUpvote True for upvote, false for downvote
     * @param callback Callback for the operation result
     */
    public void voteForTrack(String playlistId, String trackId, boolean isUpvote, PlaylistCallback callback) {
        if (playlistId == null || playlistId.isEmpty() || trackId == null || trackId.isEmpty()) {
            callback.onError("Invalid playlist or track ID");
            return;
        }
        
        // Get the current playlist first
        getPlaylist(playlistId, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                // Update the votes for the track
                Map<String, Integer> trackVotes = playlist.getTrackVotes();
                if (trackVotes == null) {
                    trackVotes = new HashMap<>();
                    playlist.setTrackVotes(trackVotes);
                }
                
                // Get current vote count or default to 0
                int currentVotes = trackVotes.getOrDefault(trackId, 0);
                
                // Update vote
                if (isUpvote) {
                    trackVotes.put(trackId, currentVotes + 1);
                } else {
                    trackVotes.put(trackId, currentVotes - 1);
                }
                
                // Update the playlist
                db.collection(COLLECTION_PLAYLISTS).document(playlistId)
                    .update("trackVotes", trackVotes)
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(playlist);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating track votes: " + e.getMessage());
                        callback.onError("Error updating track votes: " + e.getMessage());
                    });
            }
            
            @Override
            public void onError(String message) {
                callback.onError("Error getting playlist: " + message);
            }
        });
    }
    
    /**
     * Recover missing tracks in a playlist
     * @param playlistId The playlist ID
     * @param callback Callback for the operation result
     */
    public void recoverMissingTracks(String playlistId, PlaylistCallback callback) {
        if (playlistId == null || playlistId.isEmpty()) {
            callback.onError("Invalid playlist ID");
            return;
        }
        
        // Get the current playlist first
        getPlaylist(playlistId, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                // Check if playlist has trackIds
                List<String> trackIds = playlist.getTrackIds();
                if (trackIds == null || trackIds.isEmpty()) {
                    callback.onError("No tracks to recover in this playlist");
                    return;
                }
                
                // Validate each track ID exists in the tracks collection
                AtomicBoolean anyTrackRecovered = new AtomicBoolean(false);
                int totalTracks = trackIds.size();
                AtomicBoolean[] tracksChecked = new AtomicBoolean[totalTracks];
                for (int i = 0; i < totalTracks; i++) {
                    tracksChecked[i] = new AtomicBoolean(false);
                }
                
                for (int i = 0; i < totalTracks; i++) {
                    final int index = i;
                    String trackId = trackIds.get(i);
                    db.collection(COLLECTION_TRACKS).document(trackId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            tracksChecked[index].set(true);
                            
                            // If track doesn't exist, mark it for recovery
                            if (!documentSnapshot.exists()) {
                                anyTrackRecovered.set(true);
                                // Log missing track for repair
                                Log.d(TAG, "Missing track detected: " + trackId);
                            }
                            
                            // Check if all tracks have been processed
                            boolean allChecked = true;
                            for (AtomicBoolean checked : tracksChecked) {
                                if (!checked.get()) {
                                    allChecked = false;
                                    break;
                                }
                            }
                            
                            // If all tracks checked, return result
                            if (allChecked) {
                                if (anyTrackRecovered.get()) {
                                    // Some tracks were missing, update playlist
                                    playlist.setUpdatedAt(new Date());
                                    callback.onSuccess(playlist);
                                } else {
                                    // No tracks were recovered
                                    callback.onSuccess(playlist);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            tracksChecked[index].set(true);
                            Log.e(TAG, "Error checking track " + trackId + ": " + e.getMessage());
                            
                            // Check if all tracks have been processed despite error
                            boolean allChecked = true;
                            for (AtomicBoolean checked : tracksChecked) {
                                if (!checked.get()) {
                                    allChecked = false;
                                    break;
                                }
                            }
                            
                            // If all tracks checked, return result
                            if (allChecked) {
                                callback.onSuccess(playlist);
                            }
                        });
                }
            }
            
            @Override
            public void onError(String message) {
                callback.onError("Error getting playlist: " + message);
            }
        });
    }
    
    /**
     * Repair all playlists in the system
     * @param callback Callback for the operation result
     */
    public void repairAllPlaylists(PlaylistsCallback callback) {
        db.collection(COLLECTION_PLAYLISTS)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                List<Playlist> playlists = new ArrayList<>();
                List<Playlist> repairedPlaylists = new ArrayList<>();
                
                for (int i = 0; i < queryDocumentSnapshots.size(); i++) {
                    Playlist playlist = queryDocumentSnapshots.getDocuments().get(i).toObject(Playlist.class);
                    if (playlist != null) {
                        playlist.setPlaylistId(queryDocumentSnapshots.getDocuments().get(i).getId());
                        playlists.add(playlist);
                    }
                }
                
                if (playlists.isEmpty()) {
                    callback.onSuccess(playlists);
                    return;
                }
                
                // For each playlist, check and repair if needed
                AtomicBoolean[] playlistsChecked = new AtomicBoolean[playlists.size()];
                for (int i = 0; i < playlists.size(); i++) {
                    playlistsChecked[i] = new AtomicBoolean(false);
                }
                
                for (int i = 0; i < playlists.size(); i++) {
                    final int index = i;
                    Playlist playlist = playlists.get(i);
                    
                    recoverMissingTracks(playlist.getPlaylistId(), new PlaylistCallback() {
                        @Override
                        public void onSuccess(Playlist repairedPlaylist) {
                            playlistsChecked[index].set(true);
                            repairedPlaylists.add(repairedPlaylist);
                            
                            // Check if all playlists have been processed
                            boolean allChecked = true;
                            for (AtomicBoolean checked : playlistsChecked) {
                                if (!checked.get()) {
                                    allChecked = false;
                                    break;
                                }
                            }
                            
                            // If all playlists checked, return result
                            if (allChecked) {
                                callback.onSuccess(repairedPlaylists);
                            }
                        }
                        
                        @Override
                        public void onError(String message) {
                            playlistsChecked[index].set(true);
                            Log.e(TAG, "Error repairing playlist " + playlist.getPlaylistId() + ": " + message);
                            
                            // Check if all playlists have been processed despite error
                            boolean allChecked = true;
                            for (AtomicBoolean checked : playlistsChecked) {
                                if (!checked.get()) {
                                    allChecked = false;
                                    break;
                                }
                            }
                            
                            // If all playlists checked, return result
                            if (allChecked) {
                                callback.onSuccess(repairedPlaylists);
                            }
                        }
                    });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting playlists: " + e.getMessage());
                callback.onError("Error getting playlists: " + e.getMessage());
            });
    }
    
    /**
     * Get all tracks from all playlists
     * @param callback Callback for the operation result
     */
    public void getAllTracks(TracksCallback callback) {
        db.collection(COLLECTION_TRACKS)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                List<Track> tracks = new ArrayList<>();
                for (int i = 0; i < queryDocumentSnapshots.size(); i++) {
                    Track track = queryDocumentSnapshots.getDocuments().get(i).toObject(Track.class);
                    if (track != null) {
                        track.setId(queryDocumentSnapshots.getDocuments().get(i).getId());
                        tracks.add(track);
                    }
                }
                
                callback.onSuccess(tracks);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting all tracks: " + e.getMessage());
                callback.onError("Error getting all tracks: " + e.getMessage());
            });
    }
    
    /**
     * Add a collaborator to a playlist
     * @param playlistId The playlist ID
     * @param userId The user ID to add as collaborator
     * @param callback Callback for the operation result
     */
    public void addCollaboratorToPlaylist(String playlistId, String userId, PlaylistCallback callback) {
        if (playlistId == null || playlistId.isEmpty() || userId == null || userId.isEmpty()) {
            callback.onError("Invalid playlist or user ID");
            return;
        }
        
        // Get the current playlist first
        getPlaylist(playlistId, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                // Update the collaborators list
                List<String> collaborators = playlist.getCollaborators();
                if (collaborators == null) {
                    collaborators = new ArrayList<>();
                    playlist.setCollaborators(collaborators);
                }
                
                // Add the user if not already a collaborator
                if (!collaborators.contains(userId)) {
                    collaborators.add(userId);
                    
                    // Also ensure playlist is collaborative
                    playlist.setCollaborative(true);
                    
                    // Update the playlist
                    db.collection(COLLECTION_PLAYLISTS).document(playlistId)
                        .update(
                            "collaborators", collaborators,
                            "collaborative", true
                        )
                        .addOnSuccessListener(aVoid -> {
                            callback.onSuccess(playlist);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error adding collaborator: " + e.getMessage());
                            callback.onError("Error adding collaborator: " + e.getMessage());
                        });
                } else {
                    // User is already a collaborator
                    callback.onSuccess(playlist);
                }
            }
            
            @Override
            public void onError(String message) {
                callback.onError("Error getting playlist: " + message);
            }
        });
    }
    
    /**
     * Create a new playlist from a list of tracks
     * @param userId The creator user ID
     * @param name The playlist name
     * @param tracks The tracks to add to the playlist
     * @param description The playlist description
     * @param callback Callback for the operation result
     */
    public void createPlaylistFromTracks(String userId, String name, List<Track> tracks, String description, PlaylistCallback callback) {
        if (userId == null || userId.isEmpty() || name == null || name.isEmpty()) {
            callback.onError("Invalid user ID or playlist name");
            return;
        }
        
        // Create a new playlist
        Playlist playlist = new Playlist();
        playlist.setCreatorId(userId);
        playlist.setName(name);
        playlist.setDescription(description != null ? description : "");
        playlist.setCreatedAt(new Date());
        playlist.setUpdatedAt(new Date());
        playlist.setLastUpdated(System.currentTimeMillis());
        playlist.setCollaborative(false);
        playlist.setPublic(true);
        
        // Add collaborators (just the creator initially)
        List<String> collaborators = new ArrayList<>();
        collaborators.add(userId);
        playlist.setCollaborators(collaborators);
        
        // Add tracks if provided
        List<String> trackIds = new ArrayList<>();
        Map<String, Integer> trackVotes = new HashMap<>();
        
        if (tracks != null && !tracks.isEmpty()) {
            for (Track track : tracks) {
                String trackId = track.getId();
                if (trackId != null && !trackId.isEmpty()) {
                    trackIds.add(trackId);
                    trackVotes.put(trackId, 0); // Initialize with 0 votes
                }
            }
        }
        
        playlist.setTrackIds(trackIds);
        playlist.setTrackVotes(trackVotes);
        playlist.setTrackCount(trackIds.size());
        
        // Save the playlist to Firestore
        db.collection(COLLECTION_PLAYLISTS)
            .add(playlist)
            .addOnSuccessListener(documentReference -> {
                String playlistId = documentReference.getId();
                playlist.setPlaylistId(playlistId);
                
                // If we have tracks, add them to the tracks collection if they don't exist yet
                if (tracks != null && !tracks.isEmpty()) {
                    WriteBatch batch = db.batch();
                    
                    for (Track track : tracks) {
                        String trackId = track.getId();
                        if (trackId != null && !trackId.isEmpty()) {
                            // Check if track exists and add if necessary
                            DocumentReference trackRef = db.collection(COLLECTION_TRACKS).document(trackId);
                            batch.set(trackRef, track);
                        }
                    }
                    
                    // Commit the batch
                    batch.commit()
                        .addOnSuccessListener(aVoid -> {
                            callback.onSuccess(playlist);
                        })
                        .addOnFailureListener(e -> {
                            // Playlist was created but tracks weren't added
                            Log.e(TAG, "Error adding tracks: " + e.getMessage());
                            callback.onSuccess(playlist); // Still return success as playlist was created
                        });
                } else {
                    // No tracks to add
                    callback.onSuccess(playlist);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error creating playlist: " + e.getMessage());
                callback.onError("Error creating playlist: " + e.getMessage());
            });
    }

    /**
     * Get playlist by ID 
     * @param playlistId The ID of the playlist to retrieve
     * @param callback Callback to notify with the playlist data
     */
    public void getPlaylistById(String playlistId, PlaylistCallback callback) {
        if (playlistId == null || playlistId.isEmpty()) {
            callback.onError("Invalid playlist ID");
            return;
        }
        
        db.collection(COLLECTION_PLAYLISTS).document(playlistId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    try {
                        Playlist playlist = documentSnapshot.toObject(Playlist.class);
                        if (playlist != null) {
                            callback.onSuccess(playlist);
                        } else {
                            callback.onError("Failed to parse playlist data");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing playlist: " + e.getMessage());
                        callback.onError("Error parsing playlist: " + e.getMessage());
                    }
                } else {
                    callback.onError("Playlist not found");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching playlist: " + e.getMessage());
                callback.onError("Error fetching playlist: " + e.getMessage());
            });
    }

    private void updatePlaylistFromDocument(DocumentSnapshot document, Playlist playlist) {
        if (document == null || playlist == null) return;
        
        // Update basic fields
        playlist.setPlaylistId(document.getId());
        if (document.contains("name")) playlist.setName(document.getString("name"));
        if (document.contains("description")) playlist.setDescription(document.getString("description"));
        if (document.contains("coverImageUrl")) playlist.setCoverImageUrl(document.getString("coverImageUrl"));
        if (document.contains("creatorId")) playlist.setCreatorId(document.getString("creatorId"));
        if (document.contains("creatorName")) playlist.setCreatorName(document.getString("creatorName"));
        if (document.contains("mood")) playlist.setMood(document.getString("mood"));
        if (document.contains("isCollaborative")) playlist.setCollaborative(document.getBoolean("isCollaborative"));
        
        // Update track-related fields
        Map<String, Object> trackData = null;
        List<String> trackIds = null;
        
        // Get trackData if it exists
        if (document.contains("trackData")) {
            trackData = (Map<String, Object>) document.get("trackData");
            playlist.setTrackData(trackData);
        }
        
        // Get trackIds if it exists
        if (document.contains("trackIds")) {
            trackIds = (List<String>) document.get("trackIds");
            playlist.setTrackIds(trackIds);
        }
        
        // Update track count - prioritize trackData over trackIds for count
        if (trackData != null && !trackData.isEmpty()) {
            playlist.setTrackCount(trackData.size());
        } else if (trackIds != null && !trackIds.isEmpty()) {
            playlist.setTrackCount(trackIds.size());
        } else {
            playlist.setTrackCount(0);
        }
        
        // Update collaborators
        if (document.contains("collaborators")) {
            List<String> collaborators = (List<String>) document.get("collaborators");
            playlist.setCollaborators(collaborators);
        }
        
        // Update votes
        if (document.contains("trackVotes")) {
            Map<String, Integer> votes = (Map<String, Integer>) document.get("trackVotes");
            playlist.setTrackVotes(votes);
        }
        
        // Log for debugging
        Log.d("PlaylistManager", "Updated playlist " + playlist.getPlaylistId() + 
              " with track count: " + playlist.getTrackCount() + 
              ", trackData size: " + (trackData != null ? trackData.size() : 0) + 
              ", trackIds size: " + (trackIds != null ? trackIds.size() : 0));
    }

    private void saveNewPlaylistToFirestore(String playlistId, Map<String, Object> playlistData, PlaylistCallback callback) {
        db.collection(COLLECTION_PLAYLISTS)
            .document(playlistId)
            .set(playlistData)
            .addOnSuccessListener(aVoid -> {
                // Convert the map back to a Playlist object
                Playlist playlist = new Playlist();
                playlist.setPlaylistId(playlistId);
                playlist.setName((String) playlistData.get("name"));
                playlist.setDescription((String) playlistData.get("description"));
                playlist.setMood((String) playlistData.get("mood"));
                playlist.setCollaborative((Boolean) playlistData.get("isCollaborative"));
                playlist.setCreatorId((String) playlistData.get("creatorId"));
                playlist.setCreatorName((String) playlistData.get("creatorName"));
                playlist.setCoverImageUrl((String) playlistData.get("coverImageUrl"));
                playlist.setCollaborators((List<String>) playlistData.get("collaborators"));
                playlist.setTrackData((Map<String, Object>) playlistData.get("trackData"));
                playlist.setTrackIds((List<String>) playlistData.get("trackIds"));
                
                callback.onSuccess(playlist);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error creating playlist: " + e.getMessage());
                callback.onError("Failed to create playlist: " + e.getMessage());
            });
    }

    public void loadUserPlaylists(PlaylistsCallback callback) {
        getUserPlaylists(callback);
    }

    /**
     * Get a default cover image URL based on the playlist's mood
     * @param mood The mood of the playlist
     * @return URL of the default cover image for the mood
     */
    private String getDefaultCoverUrlForMood(String mood) {
        if (mood == null || mood.isEmpty()) {
            return "https://firebasestorage.googleapis.com/v0/b/bananamusic.appspot.com/o/default_covers%2Fdefault.jpg";
        }
        
        switch (mood.toLowerCase()) {
            case "happy":
                return "https://firebasestorage.googleapis.com/v0/b/bananamusic.appspot.com/o/default_covers%2Fhappy.jpg";
            case "sad":
                return "https://firebasestorage.googleapis.com/v0/b/bananamusic.appspot.com/o/default_covers%2Fsad.jpg";
            case "energetic":
                return "https://firebasestorage.googleapis.com/v0/b/bananamusic.appspot.com/o/default_covers%2Fenergetic.jpg";
            case "relaxed":
                return "https://firebasestorage.googleapis.com/v0/b/bananamusic.appspot.com/o/default_covers%2Frelaxed.jpg";
            case "romantic":
                return "https://firebasestorage.googleapis.com/v0/b/bananamusic.appspot.com/o/default_covers%2Fromantic.jpg";
            default:
                return "https://firebasestorage.googleapis.com/v0/b/bananamusic.appspot.com/o/default_covers%2Fdefault.jpg";
        }
    }
} 