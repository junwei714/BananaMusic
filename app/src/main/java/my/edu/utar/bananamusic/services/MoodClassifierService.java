package my.edu.utar.bananamusic.services;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.DummyDataProvider;
import my.edu.utar.bananamusic.utils.TensorFlowHelper;
import my.edu.utar.bananamusic.utils.TrackModelAdapter;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;

/**
 * Service class that handles mood classification and recommendations
 */
public class MoodClassifierService {
    private static final String TAG = "MoodClassifierService";
    
    private static MoodClassifierService instance;
    private final Context context;
    private final TensorFlowHelper tensorFlowHelper;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
    
    // Cache for recommendations to avoid redundant processing
    private final Map<String, List<Track>> recommendationsCache = new HashMap<>();
    
    /**
     * Private constructor - use getInstance()
     * @param context Application context
     */
    private MoodClassifierService(Context context) {
        this.context = context.getApplicationContext();
        this.tensorFlowHelper = TensorFlowHelper.getInstance(context);
        Log.d(TAG, "MoodClassifierService initialized");
    }
    
    /**
     * Get singleton instance
     * @param context Application context
     * @return MoodClassifierService instance
     */
    public static synchronized MoodClassifierService getInstance(Context context) {
        if (instance == null) {
            instance = new MoodClassifierService(context);
        }
        return instance;
    }
    
    /**
     * Get list of supported moods
     * @return List of mood names
     */
    public List<String> getSupportedMoods() {
        DummyDataProvider dataProvider = new DummyDataProvider(context);
        return dataProvider.getMoods();
    }
    
    /**
     * Get track recommendations based on mood
     * @param mood The mood to recommend tracks for
     * @param callback Callback for when recommendations are ready
     */
    public void getRecommendationsForMood(String mood, RecommendationsCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                // Check cache first
                if (recommendationsCache.containsKey(mood)) {
                    Log.d(TAG, "Using cached recommendations for mood: " + mood);
                    callback.onRecommendationsReady(recommendationsCache.get(mood));
                    return;
                }
                
                // Use ApiDataProvider with varied recommendations for better variety
                my.edu.utar.bananamusic.utils.ApiDataProvider apiProvider = 
                    my.edu.utar.bananamusic.utils.ApiDataProvider.getInstance(context);
                
                apiProvider.getVariedRecommendations(mood, 20, new TracksCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        // Cache the results
                        recommendationsCache.put(mood, tracks);
                        
                        // Return recommendations
                        callback.onRecommendationsReady(tracks);
                    }
                    
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Error from ApiDataProvider: " + message);
                        // Fall back to original implementation
                        fallbackToGenerateRecommendations(mood, callback);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error getting recommendations for mood: " + mood, e);
                callback.onError("Failed to get recommendations: " + e.getMessage());
            }
        });
    }
    
    /**
     * Fallback method that uses the original implementation when the API approach fails
     */
    private void fallbackToGenerateRecommendations(String mood, RecommendationsCallback callback) {
        try {
            // Get track IDs for the specified mood
            List<String> trackIds = tensorFlowHelper.getTrackIdsForMood(mood);
            
            // If no tracks for this mood, try to generate some
            if (trackIds == null || trackIds.isEmpty()) {
                Log.d(TAG, "No tracks found for mood: " + mood + ", generating recommendations");
                generateRecommendationsForMood(mood, callback);
                return;
            }
            
            // Convert track IDs to Track objects
            List<Track> recommendations = getTracksFromIds(trackIds);
            
            // Cache the results
            recommendationsCache.put(mood, recommendations);
            
            // Return recommendations
            callback.onRecommendationsReady(recommendations);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting recommendations for mood: " + mood, e);
            callback.onError("Failed to get recommendations: " + e.getMessage());
        }
    }
    
    /**
     * Generate mood-based recommendations by analyzing track metadata
     * @param mood The mood to generate recommendations for
     * @param callback Callback for when recommendations are ready
     */
    private void generateRecommendationsForMood(String mood, RecommendationsCallback callback) {
        try {
            // Get all available tracks
            DummyDataProvider dataProvider = new DummyDataProvider(context);
            // Use tracks directly, no conversion needed
            List<Track> allTracks = dataProvider.getRecentTracks();
            
            // Filter tracks based on mood
            List<Track> moodTracks = new ArrayList<>();
            
            for (Track track : allTracks) {
                // In a real app, this would use audio features and lyrics
                // Here we're using a simple keyword match in the title and artist
                String titleAndArtist = track.getTitle() + " " + track.getArtist();
                String detectedMood = tensorFlowHelper.detectMood(titleAndArtist);
                
                if (mood.equalsIgnoreCase(detectedMood)) {
                    moodTracks.add(track);
                    
                    // Also add this track to the TensorFlow helper for future use
                    tensorFlowHelper.addTrackToMood(mood, track.getId());
                }
            }
            
            // If still no tracks, just return some random tracks
            if (moodTracks.isEmpty()) {
                Log.d(TAG, "No tracks matched mood: " + mood + ", using random tracks");
                Collections.shuffle(allTracks);
                int count = Math.min(5, allTracks.size());
                moodTracks = allTracks.subList(0, count);
            }
            
            // Cache the results
            recommendationsCache.put(mood, moodTracks);
            
            // Return recommendations
            callback.onRecommendationsReady(moodTracks);
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating recommendations for mood: " + mood, e);
            callback.onError("Failed to generate recommendations: " + e.getMessage());
        }
    }
    
    /**
     * Convert track IDs to Track objects
     * @param trackIds List of track IDs
     * @return List of Track objects
     */
    private List<Track> getTracksFromIds(List<String> trackIds) {
        List<Track> tracks = new ArrayList<>();
        
        DummyDataProvider dataProvider = new DummyDataProvider(context);
        // Use tracks directly, no conversion needed
        List<Track> allTracks = dataProvider.getRecentTracks();
        
        // Match tracks by ID
        for (String trackId : trackIds) {
            for (Track track : allTracks) {
                if (track.getId().equals(trackId)) {
                    tracks.add(track);
                    break;
                }
            }
        }
        
        return tracks;
    }
    
    /**
     * Classify a track's mood based on its metadata
     * @param track The track to classify
     * @return The detected mood
     */
    public String classifyTrackMood(Track track) {
        // Use title and artist for classification
        String titleAndArtist = track.getTitle() + " " + track.getArtist();
        return tensorFlowHelper.detectMood(titleAndArtist);
    }
    
    /**
     * Clear the recommendations cache
     */
    public void clearCache() {
        recommendationsCache.clear();
        tensorFlowHelper.clearCache();
        Log.d(TAG, "Recommendation cache cleared");
    }
    
    /**
     * Interface for recommendation callbacks
     */
    public interface RecommendationsCallback {
        void onRecommendationsReady(List<Track> recommendations);
        void onError(String errorMessage);
    }
} 