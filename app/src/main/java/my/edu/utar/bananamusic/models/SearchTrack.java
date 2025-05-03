package my.edu.utar.bananamusic.models;

public class SearchTrack {
    private String title;
    private String artist;
    private String previewUrl;
    private String albumArtUrl;
    private boolean isPlaying;
    
    public SearchTrack(String title, String artist, String previewUrl) {
        this.title = title;
        this.artist = artist;
        this.previewUrl = previewUrl;
        this.isPlaying = false;
    }
    
    public SearchTrack(String title, String artist, String previewUrl, String albumArtUrl) {
        this.title = title;
        this.artist = artist;
        this.previewUrl = previewUrl;
        this.albumArtUrl = albumArtUrl;
        this.isPlaying = false;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getArtist() {
        return artist;
    }
    
    public String getPreviewUrl() {
        return previewUrl;
    }
    
    public String getAlbumArtUrl() {
        return albumArtUrl;
    }
    
    public void setAlbumArtUrl(String albumArtUrl) {
        this.albumArtUrl = albumArtUrl;
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }
    
    public Track toTrack() {
        Track track = new Track(
            null,  // id
            title,
            artist,
            "Unknown Album",  // albumName
            albumArtUrl,
            0,  // duration
            previewUrl,
            true  // isPreview
        );
        track.setPlaying(isPlaying);
        return track;
    }
} 
 