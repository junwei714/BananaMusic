package my.edu.utar.bananamusic.utils;

public class DeezerConfig {
    // Deezer API Configuration
    public static final String API_BASE_URL = "https://api.deezer.com";
    
    // Deezer API Endpoints
    public static final String SEARCH_ENDPOINT = "/search";
    public static final String TRACK_ENDPOINT = "/track";
    public static final String ALBUM_ENDPOINT = "/album";
    public static final String ARTIST_ENDPOINT = "/artist";
    public static final String PLAYLIST_ENDPOINT = "/playlist";
    
    // Cache configuration
    public static final long CACHE_EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    
    // API Rate Limiting
    public static final int MAX_RETRIES = 3;
    public static final long RETRY_DELAY = 1000; // 1 second
    
    private DeezerConfig() {
        // Private constructor to prevent instantiation
    }
} 