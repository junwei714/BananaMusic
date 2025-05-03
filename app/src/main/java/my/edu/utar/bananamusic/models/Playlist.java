package my.edu.utar.bananamusic.models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;
import android.content.Context;
import androidx.annotation.DrawableRes;
import android.os.Parcel;
import android.os.Parcelable;

public class Playlist implements Parcelable {
    private String playlistId;
    private String name;
    private String description;
    private String coverImageUrl;
    private String creatorId;
    private String creatorName;
    private Date createdAt;
    private Date updatedAt;
    private long lastUpdated; // Timestamp in milliseconds
    private List<String> trackIds;
    private Map<String, Integer> trackVotes;
    
    // Added PropertyName annotation for Firestore mapping
    @PropertyName("collaborative")
    private boolean isCollaborative;
    
    private String mood;
    private List<String> collaborators;
    private int coverImageResourceId; // For drawable resources
    private int trackCount;  // Keep as int
    
    // Cached list of Track objects (not just IDs)
    private transient List<Track> tracks;
    
    // Source field for where the playlist comes from (e.g., "SPOTIFY", "LOCAL")
    private String source;
    
    // Constants for source field
    public static final String SOURCE_SPOTIFY = "SPOTIFY";
    public static final String SOURCE_LOCAL = "LOCAL";
    public static final String SOURCE_DEEZER = "DEEZER";
    
    // Field to track whether this playlist is collected/liked by the current user
    @PropertyName("collected")
    private boolean isCollected;
    
    // Field to track whether this playlist is public
    @PropertyName("public")
    private boolean isPublic;
    
    // Field for embedded track data in Firestore (used by Firestore mapping)
    private Map<String, Object> trackData;
    
    // Local resource ID for cover art
    private @DrawableRes int coverArtResId;
    
    private boolean isSaved;  // Add this field
    
    /**
     * Default constructor
     */
    public Playlist() {
        trackIds = new ArrayList<>();
        trackVotes = new HashMap<>();
        collaborators = new ArrayList<>();
        createdAt = new Date();
        trackData = new HashMap<>();
        this.coverImageUrl = ""; // Default empty image URL
    }
    
    public Playlist(String playlistId, String name, String description, String creatorId, String creatorName, boolean isCollaborative, String mood) {
        this();
        this.playlistId = playlistId;
        this.name = name;
        this.description = description;
        this.coverImageUrl = "";
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.createdAt = new Date();
        this.trackIds = new ArrayList<>();
        this.trackVotes = new HashMap<>();
        this.isCollaborative = isCollaborative;
        this.mood = mood;
        this.collaborators = new ArrayList<>();
        this.collaborators.add(creatorId);
        this.coverImageResourceId = 0;
        this.coverImageUrl = ""; // Default empty image URL
    }
    
    /**
     * Constructor with name, track count, and local resource ID
     */
    public Playlist(String name, int trackCount, @DrawableRes int coverArtResId) {
        this.name = name;
        this.trackCount = trackCount;
        this.coverArtResId = coverArtResId;
        this.tracks = new ArrayList<>();
        this.coverImageUrl = ""; // Default empty image URL
        this.trackIds = new ArrayList<>();
        this.trackVotes = new HashMap<>();
        this.collaborators = new ArrayList<>();
        this.createdAt = new Date();
    }
    
    // Constructor for creating a playlist with a list of tracks
    public Playlist(String name, List<Track> tracks) {
        this.name = name;
        this.trackIds = new ArrayList<>();
        this.trackVotes = new HashMap<>();
        this.collaborators = new ArrayList<>();
        this.createdAt = new Date();
        
        // Add tracks if provided
        if (tracks != null) {
            for (Track track : tracks) {
                addTrack(track.getTrackId());
            }
        }
        this.coverImageUrl = ""; // Default empty image URL
    }
    
    // Constructor from Parcel
    protected Playlist(Parcel in) {
        playlistId = in.readString();
        name = in.readString();
        description = in.readString();
        coverImageUrl = in.readString();
        if (coverImageUrl == null) coverImageUrl = ""; // Ensure not null
        isCollaborative = in.readByte() != 0;
        trackIds = new ArrayList<>();
        in.readList(trackIds, String.class.getClassLoader());
        trackVotes = new HashMap<>();
        in.readMap(trackVotes, Integer.class.getClassLoader());
        createdAt = new Date(in.readLong());
        updatedAt = new Date(in.readLong());
        lastUpdated = in.readLong();
        collaborators = new ArrayList<>();
        in.readList(collaborators, String.class.getClassLoader());
        coverImageResourceId = in.readInt();
        coverArtResId = in.readInt();
        trackCount = in.readInt();
        source = in.readString();
        isCollected = in.readByte() != 0;
        isPublic = in.readByte() != 0;
        trackData = new HashMap<>();
        in.readMap(trackData, Object.class.getClassLoader());
        tracks = new ArrayList<>();
        in.readList(tracks, Track.class.getClassLoader());
        isSaved = in.readByte() != 0;
    }
    
    // Methods for Parcelable implementation
    public static final Creator<Playlist> CREATOR = new Creator<Playlist>() {
        @Override
        public Playlist createFromParcel(Parcel in) {
            return new Playlist(in);
        }
        
        @Override
        public Playlist[] newArray(int size) {
            return new Playlist[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(playlistId);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeString(coverImageUrl);
        dest.writeByte((byte) (isCollaborative ? 1 : 0));
        dest.writeList(trackIds);
        dest.writeMap(trackVotes);
        dest.writeLong(createdAt.getTime());
        dest.writeLong(updatedAt.getTime());
        dest.writeLong(lastUpdated);
        dest.writeList(collaborators);
        dest.writeInt(coverImageResourceId);
        dest.writeInt(coverArtResId);
        dest.writeInt(trackCount);
        dest.writeString(source);
        dest.writeByte((byte) (isCollected ? 1 : 0));
        dest.writeByte((byte) (isPublic ? 1 : 0));
        dest.writeMap(trackData);
        dest.writeList(tracks);
        dest.writeByte((byte) (isSaved ? 1 : 0));
    }
    
    // Getters and setters
    public String getPlaylistId() {
        return playlistId;
    }
    
    public void setPlaylistId(String playlistId) {
        this.playlistId = playlistId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getCoverImageUrl() {
        return coverImageUrl;
    }
    
    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl != null ? coverImageUrl : ""; // Ensure not null
    }
    
    public int getCoverImageResourceId() {
        return coverImageResourceId;
    }
    
    public void setCoverImageResourceId(int coverImageResourceId) {
        this.coverImageResourceId = coverImageResourceId;
    }
    
    public String getCreatorId() {
        return creatorId;
    }
    
    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }
    
    public String getCreatorName() {
        return creatorName;
    }
    
    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public Date getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
        // Also update the updatedAt Date for compatibility
        this.updatedAt = new Date(lastUpdated);
    }
    
    public List<String> getTrackIds() {
        return trackIds;
    }
    
    public void setTrackIds(List<String> trackIds) {
        this.trackIds = trackIds;
    }
    
    public Map<String, Integer> getTrackVotes() {
        return trackVotes;
    }
    
    public void setTrackVotes(Map<String, Integer> trackVotes) {
        this.trackVotes = trackVotes;
    }
    
    /**
     * Check if this playlist is collaborative
     * @return true if collaborative, false otherwise
     */
    @PropertyName("collaborative")
    public boolean isCollaborative() {
        return isCollaborative;
    }
    
    /**
     * Set whether this playlist is collaborative
     * @param collaborative true if collaborative, false otherwise
     */
    @PropertyName("collaborative")
    public void setCollaborative(boolean collaborative) {
        this.isCollaborative = collaborative;
    }
    
    public String getMood() {
        return mood;
    }
    
    public void setMood(String mood) {
        this.mood = mood;
    }
    
    public List<String> getCollaborators() {
        return collaborators;
    }
    
    public void setCollaborators(List<String> collaborators) {
        this.collaborators = collaborators;
    }
    
    @PropertyName("trackCount")
    public int getTrackCount() {
        return trackCount;
    }
    
    @PropertyName("trackCount")
    public void setTrackCount(int count) {
        // Ensure count is not negative
        if (count < 0) {
            count = 0;
        }
        this.trackCount = count;
        
        // If we have track IDs, ensure they match
        if (trackIds != null && trackIds.size() != count) {
            // If track IDs don't match count, use actual size
            if (trackIds.size() > 0) {
                this.trackCount = trackIds.size();
            }
        }
    }
    
    @Exclude
    public String getFormattedTrackCount() {
        return trackCount + (trackCount == 1 ? " song" : " songs");
    }
    
    @Exclude
    public void setTrackCountFromString(String trackCountStr) {
        if (trackCountStr == null || trackCountStr.isEmpty()) {
            setTrackCount(0);
            return;
        }
        
        try {
            // Extract the number from the string
            String numStr = trackCountStr.replaceAll("[^0-9]", "");
            int count = Integer.parseInt(numStr);
            setTrackCount(count);
        } catch (NumberFormatException e) {
            // Default to 0 if parsing fails
            setTrackCount(0);
        }
    }
    
    /**
     * Get the source of this playlist
     * @return Source identifier like "SPOTIFY" or "LOCAL"
     */
    public String getSource() {
        return source;
    }
    
    /**
     * Set the source of this playlist
     * @param source Source identifier like "SPOTIFY" or "LOCAL"
     */
    public void setSource(String source) {
        this.source = source;
    }
    
    /**
     * Get the list of tracks in this playlist
     * This is a compatibility method that might return null if tracks haven't been loaded
     * @return List of Track objects or null if not loaded
     */
    public List<Track> getTracks() {
        return tracks;
    }
    
    /**
     * Set the list of tracks for this playlist
     * @param tracks List of Track objects
     */
    public void setTracks(List<Track> tracks) {
        this.tracks = tracks;
    }
    
    /**
     * Check if this playlist is collected/liked by the current user
     * @return true if collected, false otherwise
     */
    @PropertyName("collected")
    public boolean isCollected() {
        return isCollected;
    }
    
    /**
     * Set whether this playlist is collected/liked by the current user
     * @param collected true if collected, false otherwise
     */
    @PropertyName("collected")
    public void setCollected(boolean collected) {
        this.isCollected = collected;
    }
    
    /**
     * Check if this playlist is public
     * @return true if public, false otherwise
     */
    @PropertyName("public")
    public boolean isPublic() {
        return isPublic;
    }
    
    /**
     * Set whether this playlist is public
     * @param isPublic true if public, false otherwise
     */
    @PropertyName("public")
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }
    
    // Helper methods
    public void addTrack(String trackId) {
        if (!trackIds.contains(trackId)) {
            trackIds.add(trackId);
            trackVotes.put(trackId, 0);
        }
    }
    
    public void removeTrack(String trackId) {
        trackIds.remove(trackId);
        trackVotes.remove(trackId);
    }
    
    public void addCollaborator(String userId) {
        if (!collaborators.contains(userId)) {
            collaborators.add(userId);
        }
    }
    
    public void removeCollaborator(String userId) {
        // Don't remove creator
        if (!userId.equals(creatorId)) {
            collaborators.remove(userId);
        }
    }
    
    public int getVotesForTrack(String trackId) {
        if (trackVotes.containsKey(trackId)) {
            return trackVotes.get(trackId);
        }
        return 0;
    }
    
    public void upvoteTrack(String trackId) {
        if (trackVotes.containsKey(trackId)) {
            trackVotes.put(trackId, trackVotes.get(trackId) + 1);
        }
    }
    
    public void downvoteTrack(String trackId) {
        if (trackVotes.containsKey(trackId)) {
            trackVotes.put(trackId, trackVotes.get(trackId) - 1);
        }
    }
    
    /**
     * Add multiple tracks to the playlist
     * @param tracks List of tracks to add
     */
    public void addTracks(List<Track> tracks) {
        if (tracks != null) {
            for (Track track : tracks) {
                addTrack(track.getTrackId());
            }
        }
    }
    
    /**
     * Get the ID of the playlist - alias for getPlaylistId()
     * @return The playlist ID
     */
    public String getId() {
        return playlistId;
    }
    
    /**
     * Get the image resource - alias for getCoverImageResourceId()
     * @return The resource ID for the playlist cover
     */
    public int getImageResource() {
        return coverImageResourceId;
    }
    
    /**
     * Get the album art URL from the first track in the playlist
     * This is useful for displaying a cover without needing to upload one
     * @return The URL of the album art or null if no tracks or no album art
     */
    public String getFirstTrackAlbumArtUrl() {
        // If we have loaded Track objects, check them first
        if (tracks != null && !tracks.isEmpty()) {
            for (Track track : tracks) {
                if (track != null && track.getAlbumArtUrl() != null && !track.getAlbumArtUrl().isEmpty()) {
                    return track.getAlbumArtUrl();
                }
            }
        }
        
        // Return the cover image URL if it's already set
        if (coverImageUrl != null && !coverImageUrl.isEmpty()) {
            return coverImageUrl;
        }
        
        // Default is null to use a placeholder
        return null;
    }

    /**
     * Helper method to create a user document with a saved playlist if it doesn't exist
     */
    public Map<String, Object> getTrackData() {
        return trackData;
    }

    /**
     * Set the track data map
     * @param trackData Map of track data
     */
    public void setTrackData(Map<String, Object> trackData) {
        this.trackData = trackData;
    }

    /**
     * Set the ID of the playlist - alias for setPlaylistId()
     * @param id The playlist ID
     */
    public void setId(String id) {
        this.playlistId = id;
    }

    /**
     * Set the image URL - alias for setCoverImageUrl()
     * @param imageUrl The URL of the playlist cover image
     */
    public void setImageUrl(String imageUrl) {
        this.coverImageUrl = imageUrl;
    }

    // Local resource ID for cover art
    public int getCoverArtResId() {
        return coverArtResId;
    }
    
    public void setCoverArtResId(int coverArtResId) {
        this.coverArtResId = coverArtResId;
    }

    // Helper methods
    public boolean hasLocalCoverArt() {
        return coverArtResId != 0;
    }
    
    public boolean hasRemoteCoverArt() {
        return coverImageUrl != null && !coverImageUrl.isEmpty();
    }
    
    // Get display text for UI
    public String getDisplayName() {
        return name != null ? name : "Unnamed Playlist";
    }
    
    public String getDisplayCreator() {
        return creatorName != null ? creatorName : "Unknown Creator";
    }
    
    public String getDisplayTrackCount() {
        return trackCount + (trackCount == 1 ? " track" : " tracks");
    }
    
    @Override
    public String toString() {
        return "Playlist{" +
            "name='" + name + '\'' +
            ", creator='" + creatorName + '\'' +
            ", trackCount=" + trackCount +
            ", source='" + source + '\'' +
            '}';
    }

    @PropertyName("saved")
    public boolean isSaved() {
        return isSaved;
    }

    @PropertyName("saved")
    public void setSaved(boolean saved) {
        isSaved = saved;
    }

    /**
     * Get the preloaded tracks for this playlist
     * @return List of preloaded tracks or null if not loaded
     */
    public List<Track> getPreloadedTracks() {
        return tracks;
    }

    /**
     * Set preloaded tracks for this playlist
     * @param tracks List of tracks to preload
     */
    public void setPreloadedTracks(List<Track> tracks) {
        this.tracks = tracks;
        if (tracks != null) {
            this.trackCount = tracks.size();
            this.trackIds = new ArrayList<>();
            for (Track track : tracks) {
                this.trackIds.add(track.getId());
            }
        }
    }
} 