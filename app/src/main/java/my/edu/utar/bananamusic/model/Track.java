package my.edu.utar.bananamusic.model;

import java.util.List;

public class Track {
    private String id;
    private String title;
    private String artist;
    private String album;
    private String albumArt;
    private String previewUrl;
    private int durationMs;
    private int votes; // For collaborative playlists
    private String spotifyId; // Added for compatibility
    private int trackCount;
    private String[] genres;

    public Track() {
        // Required empty constructor
    }

    public Track(String id, String title, String artist, String album, String albumArt, String previewUrl, int durationMs) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArt = albumArt;
        this.previewUrl = previewUrl;
        this.durationMs = durationMs;
        this.votes = 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    
    // Added for compatibility with models.Track
    public String getTrackId() {
        return id;
    }
    
    // Added for compatibility with models.Track
    public void setTrackId(String trackId) {
        this.id = trackId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getAlbumArt() {
        return albumArt;
    }

    public void setAlbumArt(String albumArt) {
        this.albumArt = albumArt;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = durationMs;
    }

    public int getVotes() {
        return votes;
    }

    public void setVotes(int votes) {
        this.votes = votes;
    }
    
    public String getSpotifyId() {
        return spotifyId;
    }
    
    public void setSpotifyId(String spotifyId) {
        this.spotifyId = spotifyId;
    }

    public void upvote() {
        votes++;
    }

    public void downvote() {
        votes--;
    }

    public String getFormattedDuration() {
        int seconds = (durationMs / 1000) % 60;
        int minutes = (durationMs / (1000 * 60)) % 60;
        
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Get list of genres
     *
     * @return
     */
    public String[] getGenres() {
        return genres;
    }

    /**
     * Set genres
     *
     * @param genres
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
} 