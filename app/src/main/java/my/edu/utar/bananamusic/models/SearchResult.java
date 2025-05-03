package my.edu.utar.bananamusic.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Model class for search results from different music sources
 */
public class SearchResult {
    public enum Source {
        LOCAL,
        SPOTIFY,
        YOUTUBE,
        JIOSAAVN,
        DEEZER
    }

    private String id;
    private String title;
    private String artist;
    private String album;
    private String albumArtUrl;
    private int duration; // in milliseconds
    private Source source;
    private String previewUrl;
    private String externalUrl;
    private boolean isPlayable;

    public SearchResult(@NonNull String id, @NonNull String title, @NonNull String artist, 
                        @Nullable String album, @Nullable String albumArtUrl, int duration, 
                        @NonNull Source source, @Nullable String previewUrl, 
                        @Nullable String externalUrl, boolean isPlayable) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtUrl = albumArtUrl;
        this.duration = duration;
        this.source = source;
        this.previewUrl = previewUrl;
        this.externalUrl = externalUrl;
        this.isPlayable = isPlayable;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        this.title = title;
    }

    @NonNull
    public String getArtist() {
        return artist;
    }

    public void setArtist(@NonNull String artist) {
        this.artist = artist;
    }

    @Nullable
    public String getAlbum() {
        return album;
    }

    public void setAlbum(@Nullable String album) {
        this.album = album;
    }

    @Nullable
    public String getAlbumArtUrl() {
        return albumArtUrl;
    }

    public void setAlbumArtUrl(@Nullable String albumArtUrl) {
        this.albumArtUrl = albumArtUrl;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    @NonNull
    public Source getSource() {
        return source;
    }

    public void setSource(@NonNull Source source) {
        this.source = source;
    }

    @Nullable
    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(@Nullable String previewUrl) {
        this.previewUrl = previewUrl;
    }

    @Nullable
    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(@Nullable String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public boolean isPlayable() {
        return isPlayable;
    }

    public void setPlayable(boolean playable) {
        isPlayable = playable;
    }

    /**
     * Create a SearchResult object from a Track
     * 
     * @param track The Track object to convert
     * @return A new SearchResult object
     */
    public static SearchResult fromTrack(@NonNull Track track) {
        return new SearchResult(
                track.getId(),
                track.getTitle(),
                track.getArtist(),
                track.getAlbum(),
                track.getAlbumArtUrl(),
                track.getDurationMs(),
                Source.LOCAL, // Assume local for Track objects
                track.getPreviewUrl(),
                null, // No external URL for local tracks
                true  // Local tracks are always playable
        );
    }

    /**
     * Convert this SearchResult to a Track
     * 
     * @return A new Track object
     */
    public Track toTrack() {
        Track track = new Track();
        track.setId(this.id);
        track.setTitle(this.title);
        track.setArtist(this.artist);
        track.setAlbum(this.album);
        track.setAlbumArtUrl(this.albumArtUrl);
        track.setDurationMs((long) this.duration);
        track.setPreviewUrl(this.previewUrl);
        track.setPlayable(this.isPlayable);
        track.setExternalUrl(this.externalUrl);
        
        // Set source based on the enum value
        switch (this.source) {
            case SPOTIFY:
                track.setSource(Track.SOURCE_SPOTIFY);
                break;
            case YOUTUBE:
                track.setSource(Track.SOURCE_YOUTUBE);
                break;
            case JIOSAAVN:
                track.setSource(Track.SOURCE_JIOSAAVN);
                break;
            case DEEZER:
                track.setSource(Track.SOURCE_DEEZER);
                track.setDeezerTrack(true);
                // For Deezer tracks, ensure the ID is properly formatted
                if (track.getId() != null && !track.getId().startsWith("deezer:")) {
                    track.setId("deezer:" + track.getId());
                }
                break;
            case LOCAL:
            default:
                track.setSource(Track.SOURCE_LOCAL);
                break;
        }
        
        return track;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchResult that = (SearchResult) o;

        if (!id.equals(that.id)) return false;
        return source == that.source;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + source.hashCode();
        return result;
    }
} 