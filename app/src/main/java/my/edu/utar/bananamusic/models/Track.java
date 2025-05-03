package my.edu.utar.bananamusic.models;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.concurrent.TimeUnit;
import java.util.Locale;
import java.util.Iterator;

public class Track implements Parcelable, Cloneable {
    // Source constants
    public static final String SOURCE_SPOTIFY = "spotify";
    public static final String SOURCE_YOUTUBE = "youtube";
    public static final String SOURCE_PIPED = "piped";
    public static final String SOURCE_SOUNDCLOUD = "soundcloud";
    public static final String SOURCE_LOCAL = "local";
    public static final String SOURCE_JIOSAAVN = "jiosaavn";
    public static final String SOURCE_DEEZER = "deezer";

    private String id;
    private String title;
    private String artist;
    private String album;
    private String albumArtUrl;
    private long duration;  // Duration in milliseconds
    private String previewUrl;
    private boolean isPlayable;
    private int votes; // For collaborative playlists
    private String spotifyId; // For Spotify integration
    private String streamUrl; // Direct streaming URL for the track
    private long deezerTrackId; // For Deezer integration
    private boolean isLiked; // Whether the track is liked by the user
    private boolean isAlbum; // Whether this track represents an album
    private boolean isPlaying; // Whether the track is currently playing
    
    // Additional fields needed by other parts of the application
    private String type; // "track", "album", "playlist", etc.
    private String source; // Where the track is from: "spotify", "youtube", etc.
    private String youtubeUrl;
    private String pipedUrl;
    private String soundCloudUrl;
    private String imageUrl; // Alias for albumArtUrl for compatibility
    private String releaseDate;
    private String[] genres;
    private int popularity; // For Spotify tracks
    private String externalUrl; // External URL to open track in browser or app
    
    // Audio features for recommendation
    private float danceability;
    private float energy;
    private float tempo;
    private float valence;
    private float acousticness;

    // Field to prevent infinite fallback attempts
    private String lastFallbackAttempt;

    // New fields
    private String description; // Detailed description for the track or playlist
    private int progress; // Progress percentage for "Continue Listening" feature

    // New field for tracking when the track was last played
    private long lastPlayedTime;

    // Additional fields to resolve compilation errors
    private String recommendationReason; // Reason for recommending this track
    private Map<String, Float> audioFeatures; // Store audio features
    private List<String> moodTags; // Mood tags associated with this track
    private int releaseYear; // Release year of the track
    private String mood; // Track mood (happy, sad, energetic, etc.)

    // Add these properties and methods to the Track class
    private boolean isSpotifyTrack;
    private boolean isDeezerTrack;

    // New fields for improved recommendations
    private long fetchTimestamp; // When this track was last fetched from API
    private Map<String, Object> metadata; // General-purpose metadata for any additional info
    private boolean isRecentlyRecommended; // Flag to track if this was recently recommended

    // New fields
    private String artistId;
    private boolean isComingSoon;
    private String albumName;

    // New field for Deezer URL
    private String deezerUrl;
    private int rankingPosition; // For trending tracks ranking

    // Required empty constructor for Firebase and other serialization
    public Track() {
        this.moodTags = new ArrayList<>();
        this.audioFeatures = new HashMap<>();
    }

    public Track(String id, String title, String artist, String album, String albumArtUrl, 
                long duration, String previewUrl, boolean isPlayable) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtUrl = albumArtUrl;
        this.duration = duration;
        this.previewUrl = previewUrl;
        this.isPlayable = isPlayable;
        this.votes = 0;
        this.imageUrl = albumArtUrl;
    }

    // Constructor for legacy code that uses String duration
    public Track(String id, String title, String artist, String album, String albumArtUrl, 
                String duration, String previewUrl, boolean isPlayable) {
        this(id, title, artist, album, albumArtUrl, 
             parseDuration(duration), previewUrl, isPlayable);
    }

    // Helper method to parse duration
    private static long parseDuration(String duration) {
        try {
            return Long.parseLong(duration);
        } catch (NumberFormatException e) {
            try {
                String[] parts = duration.split(":");
                if (parts.length == 2) {
                    int minutes = Integer.parseInt(parts[0]);
                    int seconds = Integer.parseInt(parts[1]);
                    return (minutes * 60L + seconds) * 1000L;
                }
            } catch (Exception ignored) {}
            return 0L;
        }
    }

    // Constructor with all fields
    public Track(String id, String title, String artist, String album, String albumArtUrl, 
                int duration, String previewUrl, boolean isPlayable, int votes, String spotifyId) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtUrl = albumArtUrl;
        this.duration = duration;
        this.previewUrl = previewUrl;
        this.isPlayable = isPlayable;
        this.votes = votes;
        this.spotifyId = spotifyId;
        this.imageUrl = albumArtUrl;
    }

    // Constructor matching the legacy code pattern (previewUrl before duration)
    public Track(String id, String title, String artist, String album, String albumArtUrl, 
                String previewUrl, int duration, String spotifyId) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtUrl = albumArtUrl;
        this.previewUrl = previewUrl;
        this.duration = duration;
        this.isPlayable = true;
        this.votes = 0;
        this.spotifyId = spotifyId;
        this.imageUrl = albumArtUrl;
    }
    
    // Constructor for legacy code that uses long for duration
    public Track(String id, String title, String artist, String album, String albumArtUrl, 
                String previewUrl, long duration, String spotifyId) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtUrl = albumArtUrl;
        this.previewUrl = previewUrl;
        this.duration = duration;
        this.isPlayable = true;
        this.votes = 0;
        this.spotifyId = spotifyId;
        this.imageUrl = albumArtUrl;
    }

    // Alternate constructor where previewUrl might be passed directly
    public Track(String id, String title, String artist, String album, String albumArtUrl, 
                String previewUrl, int duration, boolean isPlayable) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtUrl = albumArtUrl;
        this.duration = duration * 1000L; // Convert seconds to milliseconds
        this.previewUrl = previewUrl;
        this.isPlayable = isPlayable;
        this.votes = 0;
        this.imageUrl = albumArtUrl;
    }

    // Additional constructor for backward compatibility
    public Track(String title, String artist, String album, String duration, int albumArtResourceId) {
        this.id = System.currentTimeMillis() + "_" + title.hashCode();
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtUrl = "android.resource://my.edu.utar.bananamusic/" + albumArtResourceId;
        this.imageUrl = this.albumArtUrl;
        this.isPlayable = true;
        this.votes = 0;
        
        // Convert duration string to milliseconds if possible
        try {
            String[] parts = duration.split(":");
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            this.duration = (minutes * 60L + seconds) * 1000L;
        } catch (Exception e) {
            this.duration = 0L;
        }
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getAlbumArtUrl() { return albumArtUrl; }
    public long getDuration() { return duration; }
    public String getPreviewUrl() { return previewUrl; }
    public boolean isPlayable() { return isPlayable; }
    public int getVotes() { return votes; }
    public String getSpotifyId() { return spotifyId; }
    public int getPopularity() { return popularity; }
    public String getStreamUrl() { return streamUrl; }
    public long getDeezerTrackId() { return deezerTrackId; }
    
    /**
     * Get the Deezer track ID as a string.
     * Some Deezer APIs require the ID as a string.
     * @return Deezer track ID as a string, or null if not available
     */
    public String getDeezerIdString() { 
        return deezerTrackId > 0 ? String.valueOf(deezerTrackId) : null; 
    }
    
    public boolean isLiked() { return isLiked; }
    
    /**
     * Check if this track represents an album
     * @return true if this track represents an album
     */
    public boolean getIsAlbum() { return isAlbum; }
    
    // Additional getters for compatibility
    public String getName() { return title; }
    public String getArtistName() { return artist; }
    public String getImageUrl() { return albumArtUrl; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public String getPipedUrl() { return pipedUrl; }
    public String getSoundCloudUrl() { return soundCloudUrl; }
    public String getType() { return type; }
    public String getSource() { return source; }
    public String getReleaseDate() { return releaseDate; }
    
    /**
     * Get genres as a List instead of an array to fix type compatibility issues
     * @return List of genres or empty list if none
     */
    public List<String> getGenres() {
        if (genres == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(genres);
    }
    
    // Audio feature getters
    public float getDanceability() { return danceability; }
    public float getEnergy() { return energy; }
    public float getTempo() { return tempo; }
    public float getValence() { return valence; }
    public float getAcousticness() { return acousticness; }
    
    // Compatibility methods for old model.Track
    public String getTrackId() { return id; }
    public String getAlbumArt() { return albumArtUrl; }
    public int getDurationMs() { return (int) duration; }

    // Additional getters
    public String getExternalUrl() {
        return externalUrl;
    }

    // Getters for new fields
    public String getDescription() { return description; }
    public int getProgress() { return progress; }

    // Add new genre-related getters needed by updated code
    public String getGenre() { 
        return (genres != null && genres.length > 0) ? genres[0] : null; 
    }
    
    // Add tags-related getter
    private List<String> tags;
    
    public List<String> getTags() {
        if (tags == null) {
            tags = new ArrayList<>();
            // Add artist as a tag
            if (artist != null && !artist.isEmpty()) {
                tags.add(artist);
            }
            // Add album as a tag
            if (album != null && !album.isEmpty()) {
                tags.add(album);
            }
            // Add genres as tags
            if (genres != null) {
                tags.addAll(Arrays.asList(genres));
            }
        }
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setAlbum(String album) { this.album = album; }
    public void setAlbumArtUrl(String albumArtUrl) { 
        this.albumArtUrl = albumArtUrl; 
        this.imageUrl = albumArtUrl; // Keep imageUrl in sync
    }
    public void setDuration(long durationMs) {
        this.duration = durationMs;
    }

    /**
     * Helper method to set duration from a string format (e.g. "3:45")
     * @param durationStr Duration string in format "minutes:seconds"
     */
    public void setDurationFromString(String durationStr) {
        this.duration = parseDuration(durationStr);
    }

    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
    public void setPlayable(boolean playable) { isPlayable = playable; }
    public void setVotes(int votes) { this.votes = votes; }
    public void setSpotifyId(String spotifyId) { this.spotifyId = spotifyId; }
    public void setPopularity(int popularity) { this.popularity = popularity; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
    public void setDeezerTrackId(long deezerTrackId) { this.deezerTrackId = deezerTrackId; }
    public void setLiked(boolean liked) { isLiked = liked; }
    
    // Additional setters
    public void setName(String name) { this.title = name; }
    public void setArtistName(String artistName) { this.artist = artistName; }
    public void setImageUrl(String imageUrl) { 
        this.imageUrl = imageUrl;
        this.albumArtUrl = imageUrl; // Keep albumArtUrl in sync
    }
    public void setYoutubeUrl(String youtubeUrl) { this.youtubeUrl = youtubeUrl; }
    public void setPipedUrl(String pipedUrl) { this.pipedUrl = pipedUrl; }
    public void setSoundCloudUrl(String soundCloudUrl) { this.soundCloudUrl = soundCloudUrl; }
    public void setType(String type) { this.type = type; }
    public void setSource(String source) { this.source = source; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    
    /**
     * Set genres from String array
     * @param genres Array of genres
     */
    public void setGenres(String[] genres) { 
        this.genres = genres; 
    }
    
    /**
     * Helper method to set genres from a List
     * @param genres List of genre strings
     */
    public void setGenresFromList(List<String> genres) {
        if (genres == null) {
            this.genres = null;
        } else {
            this.genres = genres.toArray(new String[0]);
        }
    }
    
    // Audio feature setters
    public void setDanceability(float danceability) { this.danceability = danceability; }
    public void setEnergy(float energy) { this.energy = energy; }
    public void setTempo(float tempo) { this.tempo = tempo; }
    public void setValence(float valence) { this.valence = valence; }
    public void setAcousticness(float acousticness) { this.acousticness = acousticness; }
    
    // Compatibility setters for old model.Track
    public void setTrackId(String trackId) { this.id = trackId; }
    public void setAlbumArt(String albumArt) { this.albumArtUrl = albumArt; this.imageUrl = albumArt; }
    public void setDurationMs(long durationMs) { this.duration = durationMs; }
    
    // Voting methods
    public void upvote() { votes++; }
    public void downvote() { votes--; }
    
    // Utility methods
    public String getFormattedDuration() {
        if (duration > 0) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
        return "0:00";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Track track = (Track) obj;
        return id != null && id.equals(track.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return "Track{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", source='" + source + '\'' +
                ", type='" + type + '\'' +
                '}';
    }

    public String getLastFallbackAttempt() {
        return lastFallbackAttempt;
    }
    
    public void setLastFallbackAttempt(String lastFallbackAttempt) {
        this.lastFallbackAttempt = lastFallbackAttempt;
    }

    /**
     * Get the time when this track was last played
     * @return Timestamp in milliseconds when the track was last played
     */
    public long getLastPlayedTime() {
        return lastPlayedTime;
    }
    
    /**
     * Set the time when this track was last played
     * @param lastPlayedTime Timestamp in milliseconds
     */
    public void setLastPlayedTime(long lastPlayedTime) {
        this.lastPlayedTime = lastPlayedTime;
    }

    // Additional setter
    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    // Setters for new fields
    public void setDescription(String description) { this.description = description; }
    public void setProgress(int progress) { this.progress = progress; }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(albumArtUrl);
        dest.writeLong(duration);
        dest.writeString(previewUrl);
        dest.writeByte((byte) (isPlayable ? 1 : 0));
        dest.writeInt(votes);
        dest.writeString(spotifyId);
        dest.writeString(streamUrl);
        dest.writeLong(deezerTrackId);
        dest.writeByte((byte) (isLiked ? 1 : 0));
        dest.writeString(type);
        dest.writeString(source);
        dest.writeString(youtubeUrl);
        dest.writeString(pipedUrl);
        dest.writeString(soundCloudUrl);
        dest.writeString(imageUrl);
        dest.writeString(releaseDate);
        dest.writeStringArray(genres);
        dest.writeFloat(danceability);
        dest.writeFloat(energy);
        dest.writeFloat(tempo);
        dest.writeFloat(valence);
        dest.writeFloat(acousticness);
        dest.writeString(lastFallbackAttempt);
        dest.writeString(externalUrl);
        dest.writeString(description);
        dest.writeInt(progress);
        dest.writeLong(lastPlayedTime);
        dest.writeString(mood);
        dest.writeLong(fetchTimestamp);
        dest.writeByte((byte) (isRecentlyRecommended ? 1 : 0));
        dest.writeString(recommendationReason);
        
        // Write audioFeatures as a serialized JSON string
        if (audioFeatures != null && !audioFeatures.isEmpty()) {
            try {
                org.json.JSONObject jsonFeatures = new org.json.JSONObject();
                for (Map.Entry<String, Float> entry : audioFeatures.entrySet()) {
                    jsonFeatures.put(entry.getKey(), entry.getValue());
                }
                dest.writeString(jsonFeatures.toString());
            } catch (Exception e) {
                dest.writeString("{}");
            }
        } else {
            dest.writeString("{}");
        }
    }
    
    // Add Parcelable CREATOR
    public static final Parcelable.Creator<Track> CREATOR = new Parcelable.Creator<Track>() {
        @Override
        public Track createFromParcel(Parcel in) {
            return new Track(in);
        }

        @Override
        public Track[] newArray(int size) {
            return new Track[size];
        }
    };
    
    // Constructor for Parcelable
    protected Track(Parcel in) {
        id = in.readString();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        albumArtUrl = in.readString();
        duration = in.readLong();
        previewUrl = in.readString();
        isPlayable = in.readByte() != 0;
        votes = in.readInt();
        spotifyId = in.readString();
        streamUrl = in.readString();
        deezerTrackId = in.readLong();
        isLiked = in.readByte() != 0;
        type = in.readString();
        source = in.readString();
        youtubeUrl = in.readString();
        pipedUrl = in.readString();
        soundCloudUrl = in.readString();
        imageUrl = in.readString();
        releaseDate = in.readString();
        genres = in.createStringArray();
        danceability = in.readFloat();
        energy = in.readFloat();
        tempo = in.readFloat();
        valence = in.readFloat();
        acousticness = in.readFloat();
        lastFallbackAttempt = in.readString();
        externalUrl = in.readString();
        description = in.readString();
        progress = in.readInt();
        lastPlayedTime = in.readLong();
        mood = in.readString();
        fetchTimestamp = in.readLong();
        isRecentlyRecommended = in.readByte() != 0;
        recommendationReason = in.readString();
        
        // Read audioFeatures from serialized JSON string
        String featuresJson = in.readString();
        audioFeatures = new HashMap<>();
        if (featuresJson != null && !featuresJson.equals("{}")) {
            try {
                org.json.JSONObject jsonFeatures = new org.json.JSONObject(featuresJson);
                Iterator<String> keys = jsonFeatures.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    audioFeatures.put(key, (float) jsonFeatures.getDouble(key));
                }
            } catch (Exception e) {
                // Just use empty map on error
            }
        }
        
        // Initialize default collections
        if (audioFeatures == null) audioFeatures = new HashMap<>();
        metadata = new HashMap<>();
    }

    // Utility methods for checking track sources
    
    /**
     * Check if this track is from Deezer
     * @return true if the track is from Deezer
     */
    public boolean isDeezerTrack() {
        return isDeezerTrack || SOURCE_DEEZER.equals(source);
    }

    /**
     * Get the Deezer ID as a string for API calls
     * @return Deezer track ID as a string
     */
    public String getDeezerId() {
        // If we have a numeric Deezer ID, convert it to string
        if (deezerTrackId > 0) {
            return String.valueOf(deezerTrackId);
        }
        
        // Check if id might be a Deezer ID
        if (id != null && (id.startsWith("deezer:") || source != null && SOURCE_DEEZER.equals(source))) {
            // Extract ID from "deezer:12345" format
            if (id.startsWith("deezer:")) {
                return id.substring(7);
            }
            return id;
        }
        
        // Otherwise return null
        return null;
    }
    
    /**
     * Set the Deezer ID using a String value
     * @param deezerIdStr Deezer ID as a string
     */
    public void setDeezerId(String deezerIdStr) {
        if (deezerIdStr == null || deezerIdStr.isEmpty()) {
            this.deezerTrackId = 0;
            return;
        }
        
        // If the ID starts with "deezer:", extract just the ID part
        if (deezerIdStr.startsWith("deezer:")) {
            deezerIdStr = deezerIdStr.substring(7);
        }
        
        try {
            this.deezerTrackId = Long.parseLong(deezerIdStr);
        } catch (NumberFormatException e) {
            // If it's not a valid number, store it as 0
            this.deezerTrackId = 0;
            // But keep it as the track ID so getDeezerId can still return it
            if (id == null || id.isEmpty()) {
                this.id = deezerIdStr;
            }
        }
    }
    
    /**
     * Check if this track has a valid preview URL that can be played
     * @return true if the track has a valid preview URL
     */
    public boolean hasPreviewUrl() {
        return previewUrl != null && !previewUrl.isEmpty();
    }
    
    /**
     * Create a track from Deezer data
     * @param id Deezer track ID
     * @param title Track title
     * @param artist Artist name
     * @param album Album name
     * @param albumArtUrl Album artwork URL
     * @param previewUrl 30-second preview URL
     * @param duration Track duration in milliseconds
     * @return A new Track object with Deezer source
     */
    public static Track createDeezerTrack(String id, String title, String artist, String album, 
                                       String albumArtUrl, String previewUrl, int duration) {
        String trackId = id;
        if (trackId != null && !trackId.startsWith("deezer:")) {
            trackId = "deezer:" + trackId;
        }
        
        Track track = new Track(trackId, title, artist, album, albumArtUrl, previewUrl, duration, null);
        track.setSource(SOURCE_DEEZER);
        track.setPlayable(previewUrl != null && !previewUrl.isEmpty());
        
        return track;
    }

    /**
     * Checks if this track has an alternate source available
     * @return true if an alternate source is available
     */
    public boolean hasAlternateSource() {
        // Check if we have alternate sources like preview URL or stream URL
        return (previewUrl != null && !previewUrl.isEmpty()) ||
               (streamUrl != null && !streamUrl.isEmpty());
    }

    /**
     * Creates a clone of this Track object
     * @return A new Track object with the same properties
     */
    @Override
    public Track clone() {
        try {
            Track clone = (Track) super.clone();
            // If we have arrays or objects that need deep cloning, handle them here
            if (this.genres != null) {
                clone.genres = this.genres.clone();
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            // This should never happen since we implement Cloneable
            throw new RuntimeException("Could not clone Track object", e);
        }
    }

    // New methods to resolve compilation errors
    /**
     * Get the reason why this track was recommended
     * @return recommendation reason or empty string if none
     */
    public String getRecommendationReason() {
        return recommendationReason != null ? recommendationReason : "";
    }
    
    /**
     * Set the reason why this track was recommended
     * @param reason The recommendation reason
     */
    public void setRecommendationReason(String reason) {
        this.recommendationReason = reason;
    }
    
    /**
     * Get audio features as a map of feature name to value
     * @return Map of audio features
     */
    public Map<String, Float> getAudioFeatures() {
        if (audioFeatures == null) {
            audioFeatures = new HashMap<>();
            
            // Add basic audio features from existing fields with validation
            if (danceability >= 0 && danceability <= 1) {
                audioFeatures.put("danceability", danceability);
            }
            if (energy >= 0 && energy <= 1) {
                audioFeatures.put("energy", energy);
            }
            if (tempo > 0) {
                // Normalize tempo to 0-1 range (assuming typical range of 60-180 BPM)
                audioFeatures.put("tempo", Math.min(1.0f, tempo / 180.0f));
            }
            if (valence >= 0 && valence <= 1) {
                audioFeatures.put("valence", valence);
            }
            if (acousticness >= 0 && acousticness <= 1) {
                audioFeatures.put("acousticness", acousticness);
            }

            // Add mood-based features
            if (mood != null && !mood.isEmpty()) {
                switch (mood.toLowerCase()) {
                    case "happy":
                        audioFeatures.putIfAbsent("valence", 0.8f);
                        audioFeatures.putIfAbsent("energy", 0.7f);
                        break;
                    case "sad":
                        audioFeatures.putIfAbsent("valence", 0.3f);
                        audioFeatures.putIfAbsent("energy", 0.4f);
                        break;
                    case "energetic":
                        audioFeatures.putIfAbsent("energy", 0.8f);
                        audioFeatures.putIfAbsent("tempo", 0.8f);
                        break;
                    case "relaxed":
                        audioFeatures.putIfAbsent("energy", 0.3f);
                        audioFeatures.putIfAbsent("acousticness", 0.7f);
                        break;
                    case "focused":
                        audioFeatures.putIfAbsent("instrumentalness", 0.7f);
                        audioFeatures.putIfAbsent("speechiness", 0.2f);
                        break;
                }
            }

            // Use genre information to infer features if still missing
            if (genres != null && genres.length > 0) {
                for (String genre : genres) {
                    if (genre == null) continue;
                    String normalizedGenre = genre.toLowerCase();
                    if (normalizedGenre.contains("dance") || normalizedGenre.contains("edm")) {
                        audioFeatures.putIfAbsent("danceability", 0.8f);
                        audioFeatures.putIfAbsent("energy", 0.8f);
                    } else if (normalizedGenre.contains("acoustic") || normalizedGenre.contains("folk")) {
                        audioFeatures.putIfAbsent("acousticness", 0.8f);
                        audioFeatures.putIfAbsent("energy", 0.4f);
                    } else if (normalizedGenre.contains("rock") || normalizedGenre.contains("metal")) {
                        audioFeatures.putIfAbsent("energy", 0.8f);
                        audioFeatures.putIfAbsent("valence", 0.6f);
                    } else if (normalizedGenre.contains("classical") || normalizedGenre.contains("study")) {
                        audioFeatures.putIfAbsent("instrumentalness", 0.8f);
                        audioFeatures.putIfAbsent("acousticness", 0.7f);
                    }
                }
            }
        }
        return audioFeatures;
    }
    
    /**
     * Set audio features
     * @param features Map of audio features
     */
    public void setAudioFeatures(Map<String, Float> features) {
        this.audioFeatures = features;
        
        // Update individual fields if present in the map
        if (features != null) {
            if (features.containsKey("danceability")) {
                this.danceability = features.get("danceability");
            }
            if (features.containsKey("energy")) {
                this.energy = features.get("energy");
            }
            if (features.containsKey("tempo")) {
                this.tempo = features.get("tempo") * 180.0f; // Denormalize tempo
            }
            if (features.containsKey("valence")) {
                this.valence = features.get("valence");
            }
            if (features.containsKey("acousticness")) {
                this.acousticness = features.get("acousticness");
            }
        }
    }
    
    /**
     * Get mood tags associated with this track
     * @return List of mood tags
     */
    public List<String> getMoodTags() {
        if (moodTags == null) {
            moodTags = new ArrayList<>();
        }
        return moodTags;
    }
    
    /**
     * Set mood tags
     * @param moodTags List of mood tags
     */
    public void setMoodTags(List<String> moodTags) {
        this.moodTags = moodTags;
    }
    
    /**
     * Get the release year of the track
     * @return Release year or 0 if unknown
     */
    public int getReleaseYear() {
        if (releaseYear <= 0 && releaseDate != null && !releaseDate.isEmpty()) {
            try {
                // Try to parse year from releaseDate (format: YYYY-MM-DD)
                releaseYear = Integer.parseInt(releaseDate.substring(0, 4));
            } catch (Exception e) {
                // Keep releaseYear as 0 if parsing fails
            }
        }
        return releaseYear;
    }
    
    /**
     * Set the release year
     * @param year Release year
     */
    public void setReleaseYear(int year) {
        this.releaseYear = year;
    }

    public void setIsAlbum(boolean isAlbum) {
        this.isAlbum = isAlbum;
    }

    public String getService() {
        return source;
    }

    public void setService(String service) {
        this.source = service;
    }

    /**
     * Set the mood of this track
     * @param mood The mood (happy, sad, energetic, etc.)
     */
    public void setMood(String mood) {
        this.mood = mood;
    }
    
    /**
     * Get the mood of this track
     * @return The mood or null if not set
     */
    public String getMood() {
        return mood;
    }

    public boolean isSpotifyTrack() {
        return isSpotifyTrack || SOURCE_SPOTIFY.equals(source);
    }

    public void setSpotifyTrack(boolean spotifyTrack) {
        this.isSpotifyTrack = spotifyTrack;
        if (spotifyTrack && source == null) {
            this.source = SOURCE_SPOTIFY;
        }
    }

    public void setDeezerTrack(boolean deezerTrack) {
        this.isDeezerTrack = deezerTrack;
        if (deezerTrack && source == null) {
            this.source = SOURCE_DEEZER;
        }
    }

    // Getters and setters for new fields
    public long getFetchTimestamp() {
        return fetchTimestamp;
    }
    
    public void setFetchTimestamp(long fetchTimestamp) {
        this.fetchTimestamp = fetchTimestamp;
    }
    
    public Map<String, Object> getMetadata() {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public boolean isRecentlyRecommended() {
        return isRecentlyRecommended;
    }
    
    public void setRecentlyRecommended(boolean recentlyRecommended) {
        this.isRecentlyRecommended = recentlyRecommended;
    }
    
    // Helper methods to work with metadata
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }
    
    public Object getMetadataValue(String key) {
        if (metadata != null && metadata.containsKey(key)) {
            return metadata.get(key);
        }
        return null;
    }
    
    public boolean hasMetadata(String key) {
        return metadata != null && metadata.containsKey(key);
    }
    
    // Helper method to check if this track was fetched recently
    public boolean isRecentlyFetched() {
        if (fetchTimestamp <= 0) return false;
        long now = System.currentTimeMillis();
        long age = now - fetchTimestamp;
        // Consider anything fetched within the last hour as "recent"
        return age < 3600000;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    /**
     * Check if this track can be shared
     * @return true if the track can be shared, false otherwise
     */
    public boolean isSharable() {
        // A track is sharable if it has a valid ID and source
        return id != null && !id.isEmpty() && source != null && !source.isEmpty();
    }

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public boolean isComingSoon() {
        return isComingSoon;
    }

    public void setComingSoon(boolean comingSoon) {
        isComingSoon = comingSoon;
    }

    public String getAlbumName() {
        return albumName != null ? albumName : album;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
        if (this.album == null) {
            this.album = albumName;
        }
    }

    /**
     * Generate a message for sharing this track
     * @return A formatted string for sharing
     */
    public String generateSharingMessage() {
        StringBuilder message = new StringBuilder();
        message.append("Check out this track: ").append(title);
        if (artist != null && !artist.isEmpty()) {
            message.append(" by ").append(artist);
        }
        if (album != null && !album.isEmpty()) {
            message.append(" from ").append(album);
        }
        if (externalUrl != null && !externalUrl.isEmpty()) {
            message.append("\n").append(externalUrl);
        }
        return message.toString();
    }

    public String getDeezerUrl() {
        return deezerUrl;
    }
    
    public void setDeezerUrl(String deezerUrl) {
        this.deezerUrl = deezerUrl;
    }

    public int getRankingPosition() {
        return rankingPosition;
    }

    public void setRankingPosition(int position) {
        this.rankingPosition = position;
    }
} 