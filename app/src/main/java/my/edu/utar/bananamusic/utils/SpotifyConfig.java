package my.edu.utar.bananamusic.utils;

public class SpotifyConfig {
    // Spotify API Configuration
    public static final String CLIENT_ID = "bf67b6a68f6e4ae485b90f4d9693c8ea";
    public static final String CLIENT_SECRET = "63a2534240184d9682a189a878b53a7b";
    public static final String REDIRECT_URI = "bananamusic://callback";
    
    // Spotify API Endpoints
    public static final String AUTH_URL = "https://accounts.spotify.com/api/token";
    public static final String API_BASE_URL = "https://api.spotify.com/v1";
    
    // Spotify API Scopes
    public static final String[] REQUIRED_SCOPES = {
        "user-read-private",
        "user-read-email",
        "user-library-read",
        "user-read-recently-played",
        "playlist-read-private",
        "playlist-read-collaborative",
        "streaming"
    };
    
    // Cache configuration
    public static final long CACHE_EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    
    // API Rate Limiting
    public static final int MAX_RETRIES = 3;
    public static final long RETRY_DELAY = 1000; // 1 second
    
    private SpotifyConfig() {
        // Private constructor to prevent instantiation
    }
} 