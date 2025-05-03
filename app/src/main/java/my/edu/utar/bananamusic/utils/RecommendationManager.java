package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import my.edu.utar.bananamusic.models.Track;
import okhttp3.HttpUrl;

/**
 * Manager class that coordinates recommendations from multiple sources (Spotify and Deezer)
 * This class provides failover between different recommendation sources and handles caching.
 */
public class RecommendationManager {
    private static final String TAG = "RecommendationManager";
    
    // Preference constants
    private static final String PREF_NAME = "recommendation_prefs";
    private static final String PREF_PRIMARY_SOURCE = "primary_source";
    
    // Source constants
    public static final String SOURCE_SPOTIFY = "spotify";
    public static final String SOURCE_DEEZER = "deezer";
    
    // Default values
    private static final String DEFAULT_PRIMARY_SOURCE = SOURCE_SPOTIFY;
    
    // Singleton instance
    private static RecommendationManager instance;
    
    // Context reference
    private final Context context;
    
    // API helpers
    private final SpotifyHelper spotifyHelper;
    private final DeezerHelper deezerHelper;
    
    // Current primary source
    private String primarySource;
    
    /**
     * Callback interface for recommendations
     */
    public interface RecommendationsCallback {
        void onSuccess(List<Track> tracks);
        void onError(String message);
    }
    
    /**
     * Private constructor for singleton pattern
     */
    private RecommendationManager(Context context) {
        this.context = context.getApplicationContext();
        
        // Initialize helpers
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.deezerHelper = DeezerHelper.getInstance(context);
        
        // Load preferred source from preferences
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.primarySource = prefs.getString(PREF_PRIMARY_SOURCE, DEFAULT_PRIMARY_SOURCE);
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized RecommendationManager getInstance(Context context) {
        if (instance == null) {
            instance = new RecommendationManager(context);
        }
        return instance;
    }
    
    /**
     * Set the primary source for recommendations
     * @param source The source to use (SOURCE_SPOTIFY or SOURCE_DEEZER)
     */
    public void setPrimarySource(String source) {
        if (SOURCE_SPOTIFY.equals(source) || SOURCE_DEEZER.equals(source)) {
            this.primarySource = source;
            
            // Save to preferences
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_PRIMARY_SOURCE, source).apply();
        }
    }
    
    /**
     * Get the current primary source
     */
    public String getPrimarySource() {
        return primarySource;
    }
    
    /**
     * Get recommendations based on mood
     * Will try the primary source first, then fall back to the secondary source if needed
     * 
     * @param mood The mood to get recommendations for
     * @param limit Number of tracks to request
     * @param callback Callback to receive results
     */
    public void getMoodRecommendations(String mood, int limit, final RecommendationsCallback callback) {
        // Normalize and validate input
        String normalizedMood = mood != null ? mood.toLowerCase().trim() : "happy";
        int finalLimit = Math.min(50, Math.max(1, limit));
        
        Log.d(TAG, "Getting recommendations for mood: " + normalizedMood + ", limit: " + finalLimit + 
              ", primary source: " + primarySource);
        
        if (SOURCE_SPOTIFY.equals(primarySource)) {
            // Try Spotify first, then fall back to Deezer
            getSpotifyRecommendations(normalizedMood, finalLimit, new SpotifyHelper.SpotifyRecommendationsCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    callback.onSuccess(tracks);
                }
                
                @Override
                public void onError(String message) {
                    Log.w(TAG, "Spotify recommendation error: " + message + ". Falling back to Deezer.");
                    
                    // Fall back to Deezer
                    getDeezerRecommendations(normalizedMood, finalLimit, adaptToDeezerCallback(callback));
                }
            });
        } else {
            // Try Deezer first, then fall back to Spotify
            getDeezerRecommendations(normalizedMood, finalLimit, new DeezerHelper.DeezerRecommendationsCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    callback.onSuccess(tracks);
                }
                
                @Override
                public void onError(String message) {
                    Log.w(TAG, "Deezer recommendation error: " + message + ". Falling back to Spotify.");
                    
                    // Fall back to Spotify
                    getSpotifyRecommendations(normalizedMood, finalLimit, adaptToSpotifyCallback(callback));
                }
            });
        }
    }
    
    // Adapter methods to convert between callback interfaces
    private SpotifyHelper.SpotifyRecommendationsCallback adaptToSpotifyCallback(final RecommendationsCallback callback) {
        return new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }
    
    private DeezerHelper.DeezerRecommendationsCallback adaptToDeezerCallback(final RecommendationsCallback callback) {
        return new DeezerHelper.DeezerRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }
    
    /**
     * Get combined recommendations from both sources
     * This method gets recommendations from both sources and combines them
     * 
     * @param mood The mood to get recommendations for
     * @param limit Number of tracks to request (total)
     * @param callback Callback to receive results
     */
    public void getCombinedRecommendations(String mood, int limit, final RecommendationsCallback callback) {
        // Normalize and validate input
        String normalizedMood = mood != null ? mood.toLowerCase().trim() : "happy";
        int finalLimit = Math.min(50, Math.max(1, limit));
        
        // Split the limit between sources
        int limitPerSource = Math.max(1, finalLimit / 2);
        
        Log.d(TAG, "Getting combined recommendations for mood: " + normalizedMood + 
              ", limit per source: " + limitPerSource);
        
        // Track results and completion status
        final List<Track> combinedTracks = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger completedSources = new AtomicInteger(0);
        final AtomicBoolean hasError = new AtomicBoolean(false);
        
        // Get Spotify recommendations
        getSpotifyRecommendations(normalizedMood, limitPerSource, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                synchronized (combinedTracks) {
                    combinedTracks.addAll(tracks);
                }
                
                if (completedSources.incrementAndGet() == 2) {
                    finalizeCombinedResults(combinedTracks, finalLimit, callback);
                }
            }
            
            @Override
            public void onError(String message) {
                Log.w(TAG, "Spotify recommendation error in combined request: " + message);
                hasError.set(true);
                
                if (completedSources.incrementAndGet() == 2) {
                    finalizeCombinedResults(combinedTracks, finalLimit, callback);
                }
            }
        });
        
        // Get Deezer recommendations
        getDeezerRecommendations(normalizedMood, limitPerSource, new DeezerHelper.DeezerRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                synchronized (combinedTracks) {
                    combinedTracks.addAll(tracks);
                }
                
                if (completedSources.incrementAndGet() == 2) {
                    finalizeCombinedResults(combinedTracks, finalLimit, callback);
                }
            }
            
            @Override
            public void onError(String message) {
                Log.w(TAG, "Deezer recommendation error in combined request: " + message);
                hasError.set(true);
                
                if (completedSources.incrementAndGet() == 2) {
                    finalizeCombinedResults(combinedTracks, finalLimit, callback);
                }
            }
        });
    }
    
    /**
     * Process and return the final combined results
     */
    private void finalizeCombinedResults(List<Track> tracks, int limit, RecommendationsCallback callback) {
        if (tracks.isEmpty()) {
            // If no tracks were found, try a more popular query with Spotify
            spotifyHelper.getMoodBasedRecommendations("happy", limit * 2, new SpotifyHelper.SpotifyRecommendationsCallback() {
                @Override
                public void onSuccess(List<Track> generatedTracks) {
                    if (!generatedTracks.isEmpty()) {
                        callback.onSuccess(generatedTracks);
                    } else {
                        // Try another high-popularity genre as last resort
                        getSpotifyPopularTracks(limit, callback);
                    }
                }
                
                @Override
                public void onError(String message) {
                    // If this fails, try getting popular tracks instead
                    getSpotifyPopularTracks(limit, callback);
                }
            });
            return;
        }
        
        // Shuffle the tracks to mix sources
        Collections.shuffle(tracks);
        
        // Limit to requested number
        List<Track> result = tracks.size() <= limit 
            ? tracks 
            : new ArrayList<>(tracks.subList(0, limit));
            
        callback.onSuccess(result);
    }
    
    /**
     * Get popular tracks from Spotify as a last resort
     */
    private void getSpotifyPopularTracks(int limit, RecommendationsCallback callback) {
        try {
            // Build parameters for popular tracks (no specific mood)
            HttpUrl.Builder urlBuilder = HttpUrl.parse("https://api.spotify.com/v1/recommendations").newBuilder();
            urlBuilder.addQueryParameter("seed_genres", "pop");
            urlBuilder.addQueryParameter("limit", String.valueOf(limit));
            urlBuilder.addQueryParameter("min_popularity", "70"); // High popularity tracks only
            
            // Execute through SpotifyHelper for token management
            spotifyHelper.getRecommendationsFromUrl(urlBuilder.build().toString(), new SpotifyHelper.SpotifyRecommendationsCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    callback.onSuccess(tracks);
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Failed to get popular tracks: " + message);
                    callback.onError("Could not load recommendations at this time");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error getting popular tracks: " + e.getMessage());
            callback.onError("Could not load recommendations at this time");
        }
    }
    
    /**
     * Get recommendations from Spotify
     */
    private void getSpotifyRecommendations(String mood, int limit, SpotifyHelper.SpotifyRecommendationsCallback callback) {
        spotifyHelper.getMoodBasedRecommendations(mood, limit, callback);
    }
    
    /**
     * Get recommendations from Deezer
     */
    private void getDeezerRecommendations(String mood, int limit, DeezerHelper.DeezerRecommendationsCallback callback) {
        deezerHelper.getDeezerRecommendations(mood, limit, callback);
    }
    
    /**
     * Clear all recommendation caches
     */
    public void clearCaches() {
        spotifyHelper.clearCache();
        deezerHelper.clearCache();
    }
    
    /**
     * Force refresh recommendations by clearing caches and getting fresh data
     * This bypasses all cached data and makes direct API calls to Spotify
     * 
     * @param mood The mood to get recommendations for
     * @param limit Number of tracks to request
     * @param callback Callback to receive results
     */
    public void forceRefreshRecommendations(String mood, int limit, final RecommendationsCallback callback) {
        // First clear all caches
        clearCaches();
        
        // Also clear the track cache to ensure we don't use cached tracks
        MusicCache.getInstance(context).clearCache();
        
        // Set a flag to bypass cache in the SpotifyHelper if it exists
        if (spotifyHelper != null) {
            try {
                spotifyHelper.setCachingEnabled(false);
            } catch (Exception e) {
                Log.w(TAG, "Could not disable caching in SpotifyHelper: " + e.getMessage());
            }
        }
        
        // Get fresh recommendations
        getMoodRecommendations(mood, limit, new RecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                // Re-enable caching after we get the results
                if (spotifyHelper != null) {
                    try {
                        spotifyHelper.setCachingEnabled(true);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not re-enable caching in SpotifyHelper: " + e.getMessage());
                    }
                }
                
                // Filter out any tracks that don't have a preview URL
                List<Track> validTracks = new ArrayList<>();
                for (Track track : tracks) {
                    if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
                        validTracks.add(track);
                    }
                }
                
                callback.onSuccess(validTracks);
            }
            
            @Override
            public void onError(String message) {
                // Re-enable caching even if there was an error
                if (spotifyHelper != null) {
                    try {
                        spotifyHelper.setCachingEnabled(true);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not re-enable caching in SpotifyHelper: " + e.getMessage());
                    }
                }
                
                callback.onError(message);
            }
        });
    }
} 