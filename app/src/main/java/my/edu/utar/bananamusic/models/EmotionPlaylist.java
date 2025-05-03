package my.edu.utar.bananamusic.models;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Model class for emotion-based playlists that can be shared between users
 */
public class EmotionPlaylist {
    private static final String TAG = "EmotionPlaylist";
    
    // Firestore collection names
    private static final String COLLECTION_PLAYLISTS = "emotion_playlists";
    private static final String COLLECTION_USERS = "users";
    
    // Database fields
    @SerializedName("id")
    private String id;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("emotion")
    private String emotion;
    
    @SerializedName("emotion_intensity")
    private float emotionIntensity;
    
    @SerializedName("secondary_emotion")
    private String secondaryEmotion;
    
    @SerializedName("secondary_intensity")
    private float secondaryIntensity;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("creator_id")
    private String creatorId;
    
    @SerializedName("creator_name")
    private String creatorName;
    
    @SerializedName("creation_date")
    private Date creationDate;
    
    @SerializedName("last_modified")
    private Date lastModified;
    
    @SerializedName("track_ids")
    private List<String> trackIds;
    
    @SerializedName("likes")
    private int likes;
    
    @SerializedName("shares")
    private int shares;
    
    @SerializedName("is_public")
    private boolean isPublic;
    
    @SerializedName("tags")
    private List<String> tags;
    
    // Cached track objects
    private transient List<Track> tracks;
    
    /**
     * Default constructor for serialization
     */
    public EmotionPlaylist() {
        this.id = UUID.randomUUID().toString();
        this.trackIds = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.creationDate = new Date();
        this.lastModified = new Date();
        this.likes = 0;
        this.shares = 0;
        this.isPublic = true;
    }
    
    /**
     * Creates a new emotion playlist
     * @param name Playlist name
     * @param emotion Primary emotion
     * @param description Description of the playlist mood/feeling
     * @param creatorId ID of the creator
     */
    public EmotionPlaylist(String name, String emotion, String description, String creatorId) {
        this();
        this.name = name;
        this.emotion = emotion;
        this.emotionIntensity = 1.0f;
        this.description = description;
        this.creatorId = creatorId;
    }
    
    /**
     * Creates a new emotion playlist with mixed emotions
     * @param name Playlist name
     * @param primaryEmotion Primary emotion
     * @param primaryIntensity Intensity of primary emotion (0.0-1.0)
     * @param secondaryEmotion Secondary emotion
     * @param secondaryIntensity Intensity of secondary emotion (0.0-1.0)
     * @param description Description of the playlist
     * @param creatorId ID of the creator
     */
    public EmotionPlaylist(String name, String primaryEmotion, float primaryIntensity,
                           String secondaryEmotion, float secondaryIntensity,
                           String description, String creatorId) {
        this(name, primaryEmotion, description, creatorId);
        this.emotionIntensity = primaryIntensity;
        this.secondaryEmotion = secondaryEmotion;
        this.secondaryIntensity = secondaryIntensity;
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.lastModified = new Date();
    }

    public String getEmotion() {
        return emotion;
    }

    public void setEmotion(String emotion) {
        this.emotion = emotion;
        this.lastModified = new Date();
    }

    public float getEmotionIntensity() {
        return emotionIntensity;
    }

    public void setEmotionIntensity(float emotionIntensity) {
        this.emotionIntensity = emotionIntensity;
        this.lastModified = new Date();
    }

    public String getSecondaryEmotion() {
        return secondaryEmotion;
    }

    public void setSecondaryEmotion(String secondaryEmotion) {
        this.secondaryEmotion = secondaryEmotion;
        this.lastModified = new Date();
    }

    public float getSecondaryIntensity() {
        return secondaryIntensity;
    }

    public void setSecondaryIntensity(float secondaryIntensity) {
        this.secondaryIntensity = secondaryIntensity;
        this.lastModified = new Date();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.lastModified = new Date();
    }

    public String getCreatorId() {
        return creatorId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public List<String> getTrackIds() {
        return trackIds;
    }

    public void setTrackIds(List<String> trackIds) {
        this.trackIds = trackIds;
        this.lastModified = new Date();
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public int getShares() {
        return shares;
    }

    public void setShares(int shares) {
        this.shares = shares;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
        this.lastModified = new Date();
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
        this.lastModified = new Date();
    }
    
    /**
     * Add a track to the playlist
     * @param trackId ID of the track to add
     * @return true if track was added, false if it was already in the playlist
     */
    public boolean addTrack(String trackId) {
        if (trackIds.contains(trackId)) {
            return false;
        }
        
        trackIds.add(trackId);
        lastModified = new Date();
        return true;
    }
    
    /**
     * Remove a track from the playlist
     * @param trackId ID of the track to remove
     * @return true if track was removed, false if it wasn't in the playlist
     */
    public boolean removeTrack(String trackId) {
        boolean removed = trackIds.remove(trackId);
        if (removed) {
            lastModified = new Date();
        }
        return removed;
    }
    
    /**
     * Add a tag to the playlist
     * @param tag Tag to add
     */
    public void addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
            lastModified = new Date();
        }
    }
    
    /**
     * Remove a tag from the playlist
     * @param tag Tag to remove
     */
    public void removeTag(String tag) {
        if (tags.remove(tag)) {
            lastModified = new Date();
        }
    }
    
    /**
     * Get the tracks in the playlist
     * @param context Application context for data access
     * @param callback Callback for track loading completion
     */
    public void getTracks(Context context, TracksCallback callback) {
        // If tracks are already loaded, return them immediately
        if (tracks != null && !tracks.isEmpty() && tracks.size() == trackIds.size()) {
            callback.onTracksLoaded(tracks);
            return;
        }
        
        if (trackIds.isEmpty()) {
            callback.onTracksLoaded(new ArrayList<>());
            return;
        }
        
        // Initialize tracks list
        tracks = new ArrayList<>();
        
        // In a real implementation, we would use a service to fetch the tracks
        // For now, we'll create placeholder tracks
        for (String trackId : trackIds) {
            // Create a placeholder track with the ID
            Track track = new Track();
            track.setTrackId(trackId);
            track.setTitle("Track " + trackId);
            track.setArtist("Unknown Artist");
            
            tracks.add(track);
        }
        
        // Return the placeholder tracks
        callback.onTracksLoaded(tracks);
    }
    
    /**
     * Save this playlist to Firestore
     * @param callback Callback for save completion
     */
    public void saveToFirestore(SaveCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Convert to map for Firestore
        Map<String, Object> playlistData = toMap();
        
        if (id == null || id.isEmpty()) {
            // New playlist
            id = UUID.randomUUID().toString();
            playlistData.put("id", id);
        }
        
        // Save to Firestore
        db.collection(COLLECTION_PLAYLISTS).document(id)
            .set(playlistData)
            .addOnSuccessListener(aVoid -> {
                // Add to user's playlists
                if (creatorId != null && !creatorId.isEmpty()) {
                    db.collection(COLLECTION_USERS).document(creatorId)
                        .update("playlists", FieldValue.arrayUnion(id))
                        .addOnSuccessListener(aVoid2 -> callback.onSuccess(id))
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Error updating user's playlists", e);
                            callback.onSuccess(id); // Still consider it a success
                        });
                } else {
                    callback.onSuccess(id);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving playlist", e);
                callback.onError("Failed to save playlist: " + e.getMessage());
            });
    }
    
    /**
     * Share this playlist with another user
     * @param targetUserId ID of the user to share with
     * @param callback Callback for share completion
     */
    public void shareWithUser(String targetUserId, ShareCallback callback) {
        if (!isPublic) {
            callback.onError("Cannot share private playlist");
            return;
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Update shares count
        db.collection(COLLECTION_PLAYLISTS).document(id)
            .update("shares", FieldValue.increment(1))
            .addOnSuccessListener(aVoid -> {
                // Add to target user's shared playlists
                db.collection(COLLECTION_USERS).document(targetUserId)
                    .update("shared_playlists", FieldValue.arrayUnion(id))
                    .addOnSuccessListener(aVoid2 -> {
                        shares++;
                        callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error sharing playlist with user", e);
                        callback.onError("Failed to share playlist: " + e.getMessage());
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating share count", e);
                callback.onError("Failed to update share count: " + e.getMessage());
            });
    }
    
    /**
     * Like this playlist
     * @param userId ID of the user liking the playlist
     * @param callback Callback for like completion
     */
    public void like(String userId, LikeCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Check if user already liked this playlist
        db.collection(COLLECTION_USERS).document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                List<String> likedPlaylists = (List<String>) documentSnapshot.get("liked_playlists");
                if (likedPlaylists != null && likedPlaylists.contains(id)) {
                    callback.onError("You already liked this playlist");
                    return;
                }
                
                // Update likes count
                db.collection(COLLECTION_PLAYLISTS).document(id)
                    .update("likes", FieldValue.increment(1))
                    .addOnSuccessListener(aVoid -> {
                        // Add to user's liked playlists
                        db.collection(COLLECTION_USERS).document(userId)
                            .update("liked_playlists", FieldValue.arrayUnion(id))
                            .addOnSuccessListener(aVoid2 -> {
                                likes++;
                                callback.onSuccess(likes);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error updating user's liked playlists", e);
                                callback.onError("Failed to update likes: " + e.getMessage());
                            });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating likes count", e);
                        callback.onError("Failed to update likes: " + e.getMessage());
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking if user liked playlist", e);
                callback.onError("Failed to check likes: " + e.getMessage());
            });
    }
    
    /**
     * Load a playlist from Firestore by ID
     * @param playlistId ID of the playlist to load
     * @param callback Callback for load completion
     */
    public static void loadFromFirestore(String playlistId, LoadCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection(COLLECTION_PLAYLISTS).document(playlistId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    EmotionPlaylist playlist = documentSnapshot.toObject(EmotionPlaylist.class);
                    callback.onSuccess(playlist);
                } else {
                    callback.onError("Playlist not found");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading playlist", e);
                callback.onError("Failed to load playlist: " + e.getMessage());
            });
    }
    
    /**
     * Find public playlists by emotion
     * @param emotion The emotion to search for
     * @param limit Maximum number of playlists to return
     * @param callback Callback for search completion
     */
    public static void findByEmotion(String emotion, int limit, SearchCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection(COLLECTION_PLAYLISTS)
            .whereEqualTo("is_public", true)
            .whereEqualTo("emotion", emotion)
            .orderBy("likes", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<EmotionPlaylist> playlists = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    EmotionPlaylist playlist = document.toObject(EmotionPlaylist.class);
                    playlists.add(playlist);
                }
                callback.onSuccess(playlists);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error finding playlists by emotion", e);
                callback.onError("Failed to find playlists: " + e.getMessage());
            });
    }
    
    /**
     * Find trending emotion playlists
     * @param limit Maximum number of playlists to return
     * @param callback Callback for search completion
     */
    public static void findTrending(int limit, SearchCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection(COLLECTION_PLAYLISTS)
            .whereEqualTo("is_public", true)
            .orderBy("likes", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<EmotionPlaylist> playlists = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    EmotionPlaylist playlist = document.toObject(EmotionPlaylist.class);
                    playlists.add(playlist);
                }
                callback.onSuccess(playlists);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error finding trending playlists", e);
                callback.onError("Failed to find trending playlists: " + e.getMessage());
            });
    }
    
    /**
     * Find a user's created playlists
     * @param userId ID of the user
     * @param callback Callback for search completion
     */
    public static void findByCreator(String userId, SearchCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection(COLLECTION_PLAYLISTS)
            .whereEqualTo("creator_id", userId)
            .orderBy("last_modified", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<EmotionPlaylist> playlists = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    EmotionPlaylist playlist = document.toObject(EmotionPlaylist.class);
                    playlists.add(playlist);
                }
                callback.onSuccess(playlists);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error finding user's playlists", e);
                callback.onError("Failed to find playlists: " + e.getMessage());
            });
    }
    
    /**
     * Convert playlist to a map for Firestore
     */
    private Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("emotion", emotion);
        map.put("emotion_intensity", emotionIntensity);
        
        if (secondaryEmotion != null && !secondaryEmotion.isEmpty()) {
            map.put("secondary_emotion", secondaryEmotion);
            map.put("secondary_intensity", secondaryIntensity);
        }
        
        map.put("description", description);
        map.put("creator_id", creatorId);
        map.put("creator_name", creatorName);
        map.put("creation_date", creationDate);
        map.put("last_modified", new Date());
        map.put("track_ids", trackIds);
        map.put("likes", likes);
        map.put("shares", shares);
        map.put("is_public", isPublic);
        map.put("tags", tags);
        
        return map;
    }
    
    /**
     * Create a shareable link for this playlist
     */
    public String getShareableLink() {
        return "bananamusic://playlist/" + id;
    }
    
    /**
     * Get a JSON representation of this playlist for sharing
     */
    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
    
    /**
     * Create a playlist from JSON
     */
    public static EmotionPlaylist fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, EmotionPlaylist.class);
    }
    
    /**
     * Callback for track loading
     */
    public interface TracksCallback {
        void onTracksLoaded(List<Track> tracks);
        void onError(String message);
    }
    
    /**
     * Callback for playlist saving
     */
    public interface SaveCallback {
        void onSuccess(String playlistId);
        void onError(String message);
    }
    
    /**
     * Callback for playlist loading
     */
    public interface LoadCallback {
        void onSuccess(EmotionPlaylist playlist);
        void onError(String message);
    }
    
    /**
     * Callback for playlist searching
     */
    public interface SearchCallback {
        void onSuccess(List<EmotionPlaylist> playlists);
        void onError(String message);
    }
    
    /**
     * Callback for playlist sharing
     */
    public interface ShareCallback {
        void onSuccess();
        void onError(String message);
    }
    
    /**
     * Callback for playlist liking
     */
    public interface LikeCallback {
        void onSuccess(int newLikeCount);
        void onError(String message);
    }
} 