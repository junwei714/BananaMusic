package my.edu.utar.bananamusic.models;

public enum MusicSource {
    LOCAL,
    SPOTIFY,
    YOUTUBE,
    DEEZER,
    JIOSAAVN;

    @Override
    public String toString() {
        switch (this) {
            case LOCAL:
                return "Local";
            case SPOTIFY:
                return "Spotify";
            case YOUTUBE:
                return "YouTube";
            case DEEZER:
                return "Deezer";
            case JIOSAAVN:
                return "JioSaavn";
            default:
                return name();
        }
    }
} 