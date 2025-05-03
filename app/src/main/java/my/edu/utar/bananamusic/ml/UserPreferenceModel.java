package my.edu.utar.bananamusic.ml;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.Calendar;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.TrackHistory;

/**
 * Machine learning model that learns from user listening history to generate
 * personalized music recommendations. This model uses collaborative filtering
 * techniques and content-based filtering to create a hybrid recommendation system.
 */
public class UserPreferenceModel {
    private static final String TAG = "UserPreferenceModel";
    
    // Singleton instance
    private static UserPreferenceModel instance;
    
    // Context
    private final Context context;
    
    // Executor for background processing
    private final Executor executor;
    
    // SharedPreferences for storing model data
    private final SharedPreferences preferences;
    private static final String PREF_FILE = "user_preference_model";
    private static final String KEY_GENRE_WEIGHTS = "genre_weights";
    private static final String KEY_ARTIST_WEIGHTS = "artist_weights";
    private static final String KEY_AUDIO_FEATURES = "audio_features";
    private static final String KEY_LISTENING_TIMES = "listening_times";
    private static final String KEY_FEEDBACK_DATA = "feedback_data";
    private static final String KEY_RECOMMENDATION_QUALITY = "recommendation_quality";
    
    // Weights maps
    private Map<String, Float> genreWeights = new HashMap<>();
    private Map<String, Float> artistWeights = new HashMap<>();
    private Map<String, Map<String, Float>> audioFeatureProfiles = new HashMap<>();
    private Map<String, Integer> listeningTimeDistribution = new HashMap<>();
    private Map<String, Integer> feedbackRatings = new HashMap<>();
    private Map<String, Map<String, Object>> recommendationQuality = new HashMap<>();
    
    // Constants for recommendation algorithm
    private static final float GENRE_WEIGHT = 0.25f;
    private static final float ARTIST_WEIGHT = 0.25f;
    private static final float AUDIO_FEATURES_WEIGHT = 0.35f;
    private static final float RECENCY_WEIGHT = 0.15f;
    
    // Audio features to track
    private static final String[] AUDIO_FEATURES = {
        "danceability", "energy", "tempo", "valence", 
        "acousticness", "loudness", "instrumentalness"
    };
    
    // Callback interface for recommendations
    public interface RecommendationCallback {
        void onRecommendationsReady(List<Track> recommendations, Map<String, Float> scores);
        void onError(String message);
    }
    
    // Feedback interface
    public interface FeedbackCallback {
        void onFeedbackProcessed(boolean success);
    }
    
    // Private constructor - use getInstance()
    private UserPreferenceModel(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.preferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        
        // Load saved model data
        loadModelData();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized UserPreferenceModel getInstance(Context context) {
        if (instance == null) {
            instance = new UserPreferenceModel(context);
        }
        return instance;
    }
    
    /**
     * Load model data from SharedPreferences
     */
    private void loadModelData() {
        try {
            // Load genre weights
            String genresJson = preferences.getString(KEY_GENRE_WEIGHTS, "");
            if (!genresJson.isEmpty()) {
                genreWeights = jsonToMap(genresJson);
            }
            
            // Load artist weights
            String artistsJson = preferences.getString(KEY_ARTIST_WEIGHTS, "");
            if (!artistsJson.isEmpty()) {
                artistWeights = jsonToMap(artistsJson);
            }
            
            // Load audio feature profiles
            String featuresJson = preferences.getString(KEY_AUDIO_FEATURES, "");
            if (!featuresJson.isEmpty()) {
                audioFeatureProfiles = jsonToNestedMap(featuresJson);
            }
            
            // Load listening time distribution
            String timesJson = preferences.getString(KEY_LISTENING_TIMES, "");
            if (!timesJson.isEmpty()) {
                listeningTimeDistribution = jsonToIntMap(timesJson);
            }
            
            // Load feedback data
            String feedbackJson = preferences.getString(KEY_FEEDBACK_DATA, "");
            if (!feedbackJson.isEmpty()) {
                feedbackRatings = jsonToIntMap(feedbackJson);
            }
            
            // Load recommendation quality data
            String qualityJson = preferences.getString(KEY_RECOMMENDATION_QUALITY, "");
            if (!qualityJson.isEmpty()) {
                recommendationQuality = jsonToQualityMap(qualityJson);
            }
            
            Log.d(TAG, "Loaded model data");
        } catch (Exception e) {
            Log.e(TAG, "Error loading model data: " + e.getMessage());
            // Initialize with defaults if loading fails
            genreWeights = new HashMap<>();
            artistWeights = new HashMap<>();
            audioFeatureProfiles = new HashMap<>();
            listeningTimeDistribution = new HashMap<>();
            feedbackRatings = new HashMap<>();
            recommendationQuality = new HashMap<>();
        }
    }
    
    /**
     * Save model data to SharedPreferences
     */
    private void saveModelData() {
        try {
            SharedPreferences.Editor editor = preferences.edit();
            
            // Save genre weights
            editor.putString(KEY_GENRE_WEIGHTS, mapToJson(genreWeights));
            
            // Save artist weights
            editor.putString(KEY_ARTIST_WEIGHTS, mapToJson(artistWeights));
            
            // Save audio feature profiles
            editor.putString(KEY_AUDIO_FEATURES, nestedMapToJson(audioFeatureProfiles));
            
            // Save listening time distribution
            editor.putString(KEY_LISTENING_TIMES, intMapToJson(listeningTimeDistribution));
            
            // Save feedback data
            editor.putString(KEY_FEEDBACK_DATA, intMapToJson(feedbackRatings));
            
            // Save recommendation quality data
            editor.putString(KEY_RECOMMENDATION_QUALITY, qualityMapToJson(recommendationQuality));
            
            editor.apply();
            Log.d(TAG, "Saved model data");
        } catch (Exception e) {
            Log.e(TAG, "Error saving model data: " + e.getMessage());
        }
    }
    
    /**
     * Update the model with a newly played track
     * @param track The track that was played
     * @param playCount How many times it's been played
     * @param lastPlayed When it was last played (timestamp)
     */
    public void updateWithTrack(Track track, int playCount, long lastPlayed) {
        if (track == null || track.getId() == null) return;
        
        executor.execute(() -> {
            // Update genre weights
            updateGenreWeights(track, playCount);
            
            // Update artist weights
            updateArtistWeights(track, playCount);
            
            // Update audio feature profile
            updateAudioFeatureProfile(track);
            
            // Update listening time distribution
            updateListeningTimeDistribution(lastPlayed);
            
            // Save updated model
            saveModelData();
        });
    }
    
    /**
     * Update genre weights based on a track play
     */
    private void updateGenreWeights(Track track, int playCount) {
        List<String> genres = track.getGenres();
        if (genres == null || genres.isEmpty()) return;
        
        for (String genre : genres) {
            if (genre == null || genre.isEmpty()) continue;
            
            // Get current weight or default to 0
            float currentWeight = genreWeights.getOrDefault(genre, 0.0f);
            
            // Calculate new weight with decay for older plays
            float playCountFactor = calculatePlayCountFactor(playCount);
            float newWeight = currentWeight + playCountFactor;
            
            genreWeights.put(genre, newWeight);
        }
        
        // Normalize weights to prevent unbounded growth
        normalizeWeights(genreWeights);
    }
    
    /**
     * Update artist weights based on a track play
     */
    private void updateArtistWeights(Track track, int playCount) {
        String artist = track.getArtist();
        if (artist == null || artist.isEmpty()) return;
        
        // Get current weight or default to 0
        float currentWeight = artistWeights.getOrDefault(artist, 0.0f);
        
        // Calculate new weight with decay for older plays
        float playCountFactor = calculatePlayCountFactor(playCount);
        float newWeight = currentWeight + playCountFactor;
        
        artistWeights.put(artist, newWeight);
        
        // Normalize weights to prevent unbounded growth
        normalizeWeights(artistWeights);
    }
    
    /**
     * Update audio feature profile based on track
     */
    private void updateAudioFeatureProfile(Track track) {
        // Extract audio features from track
        Map<String, Float> features = extractAudioFeatures(track);
        if (features.isEmpty()) return;
        
        String trackId = track.getId();
        audioFeatureProfiles.put(trackId, features);
        
        // Limit the size of the profiles map to conserve memory
        if (audioFeatureProfiles.size() > 1000) {
            // Remove oldest entries (this is a simplified approach)
            List<String> keys = new ArrayList<>(audioFeatureProfiles.keySet());
            for (int i = 0; i < 100; i++) {
                audioFeatureProfiles.remove(keys.get(i));
            }
        }
    }
    
    /**
     * Update the listening time distribution based on the current timestamp
     */
    public void updateListeningTimeDistribution(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String hourKey = String.valueOf(hour);
        
        // Update the listening time distribution map
        if (listeningTimeDistribution.containsKey(hourKey)) {
            int count = listeningTimeDistribution.get(hourKey);
            listeningTimeDistribution.put(hourKey, count + 1);
        } else {
            listeningTimeDistribution.put(hourKey, 1);
        }
        
        // Save updated distribution
        saveModelData();
    }
    
    /**
     * Calculate play count factor with decay
     */
    private float calculatePlayCountFactor(int playCount) {
        // Logarithmic growth with diminishing returns for repeated plays
        return (float) Math.log10(1 + playCount);
    }
    
    /**
     * Normalize a map of weights so they sum to 1.0
     */
    private void normalizeWeights(Map<String, Float> weights) {
        float sum = 0.0f;
        for (float weight : weights.values()) {
            sum += weight;
        }
        
        if (sum > 0) {
            for (Map.Entry<String, Float> entry : weights.entrySet()) {
                weights.put(entry.getKey(), entry.getValue() / sum);
            }
        }
    }
    
    /**
     * Extract audio features from a track
     */
    private Map<String, Float> extractAudioFeatures(Track track) {
        Map<String, Float> features = new HashMap<>();
        
        // If track has audio features already, use those
        if (track.getDanceability() > 0) {
            features.put("danceability", track.getDanceability());
        }
        if (track.getEnergy() > 0) {
            features.put("energy", track.getEnergy());
        }
        if (track.getTempo() > 0) {
            features.put("tempo", track.getTempo() / 200.0f); // Normalize to 0-1
        }
        if (track.getValence() > 0) {
            features.put("valence", track.getValence());
        }
        if (track.getAcousticness() > 0) {
            features.put("acousticness", track.getAcousticness());
        }
        
        return features;
    }
    
    /**
     * Generate recommendations for a user based on their listening history
     * @param availableTracks List of tracks to choose recommendations from
     * @param limit Maximum number of recommendations to return
     * @param callback Callback for results
     */
    public void generateRecommendations(List<Track> availableTracks, int limit, RecommendationCallback callback) {
        if (availableTracks == null || availableTracks.isEmpty()) {
            callback.onError("No tracks available for recommendations");
            return;
        }
        
        executor.execute(() -> {
            try {
                // Calculate scores for each track
                Map<String, Float> trackScores = new HashMap<>();
                
                for (Track track : availableTracks) {
                    if (track.getId() == null) continue;
                    
                    // Skip tracks that have received negative feedback
                    if (feedbackRatings.containsKey(track.getId()) && 
                        feedbackRatings.get(track.getId()) < 0) {
                        continue;
                    }
                    
                    float score = calculateTrackScore(track);
                    trackScores.put(track.getId(), score);
                }
                
                // Sort tracks by score
                List<Track> recommendations = new ArrayList<>(availableTracks);
                Collections.sort(recommendations, (t1, t2) -> {
                    float score1 = trackScores.getOrDefault(t1.getId(), 0.0f);
                    float score2 = trackScores.getOrDefault(t2.getId(), 0.0f);
                    return Float.compare(score2, score1); // Descending order
                });
                
                // Limit to requested number
                if (recommendations.size() > limit) {
                    recommendations = recommendations.subList(0, limit);
                }
                
                // Extract scores for recommended tracks
                Map<String, Float> recommendationScores = new HashMap<>();
                for (Track track : recommendations) {
                    if (track.getId() != null) {
                        recommendationScores.put(track.getId(), 
                            trackScores.getOrDefault(track.getId(), 0.0f));
                    }
                }
                
                // Return recommendations via callback
                callback.onRecommendationsReady(recommendations, recommendationScores);
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating recommendations: " + e.getMessage());
                callback.onError("Failed to generate recommendations: " + e.getMessage());
            }
        });
    }
    
    /**
     * Calculate a score for a track based on user preferences
     */
    private float calculateTrackScore(Track track) {
        float genreScore = calculateGenreScore(track);
        float artistScore = calculateArtistScore(track);
        float audioFeaturesScore = calculateAudioFeaturesScore(track);
        float timeScore = calculateTimeScore();
        
        // Apply feedback boost if available
        float feedbackBoost = 0.0f;
        if (track.getId() != null && feedbackRatings.containsKey(track.getId())) {
            int rating = feedbackRatings.get(track.getId());
            feedbackBoost = rating > 0 ? 0.2f : 0.0f; // 20% boost for positive feedback
        }
        
        // Weighted sum of scores
        float score = (genreScore * GENRE_WEIGHT) +
                      (artistScore * ARTIST_WEIGHT) + 
                      (audioFeaturesScore * AUDIO_FEATURES_WEIGHT) +
                      (timeScore * RECENCY_WEIGHT) +
                      feedbackBoost;
        
        return score;
    }
    
    /**
     * Calculate genre score for a track based on user preference
     */
    private float calculateGenreScore(Track track) {
        if (track == null || track.getGenres() == null || track.getGenres().isEmpty()) {
            return 0.0f;
        }

        float score = 0.0f;
        for (String genre : track.getGenres()) {
            score += genreWeights.getOrDefault(genre, 0.0f);
        }
        return score;
    }
    
    /**
     * Calculate artist score for a track based on user preference
     */
    private float calculateArtistScore(Track track) {
        if (artistWeights.isEmpty() || track.getArtist() == null) {
            return 0.0f;
        }
        
        return artistWeights.getOrDefault(track.getArtist(), 0.0f);
    }
    
    /**
     * Calculate audio features score based on user preference
     */
    private float calculateAudioFeaturesScore(Track track) {
        if (audioFeatureProfiles.isEmpty()) {
            return 0.5f; // Neutral score if no profiles yet
        }
        
        // Extract current track features
        Map<String, Float> trackFeatures = extractAudioFeatures(track);
        if (trackFeatures.isEmpty()) {
            return 0.5f; // Neutral score if no features
        }
        
        // Calculate average similarity to preferred tracks
        float totalSimilarity = 0.0f;
        int count = 0;
        
        for (Map<String, Float> profile : audioFeatureProfiles.values()) {
            float similarity = calculateFeatureSimilarity(trackFeatures, profile);
            totalSimilarity += similarity;
            count++;
        }
        
        return count > 0 ? totalSimilarity / count : 0.5f;
    }
    
    /**
     * Calculate similarity between two audio feature sets
     */
    private float calculateFeatureSimilarity(Map<String, Float> features1, Map<String, Float> features2) {
        float totalDistance = 0.0f;
        int featureCount = 0;
        
        for (String feature : AUDIO_FEATURES) {
            if (features1.containsKey(feature) && features2.containsKey(feature)) {
                float value1 = features1.get(feature);
                float value2 = features2.get(feature);
                float distance = Math.abs(value1 - value2);
                totalDistance += distance;
                featureCount++;
            }
        }
        
        if (featureCount == 0) return 0.5f;
        
        // Convert distance to similarity (1 = identical, 0 = completely different)
        return 1.0f - (totalDistance / featureCount);
    }
    
    /**
     * Calculate time score based on current time vs listening patterns
     */
    private float calculateTimeScore() {
        if (listeningTimeDistribution.isEmpty()) {
            return 0.5f; // Neutral score if no data
        }
        
        // Get current hour
        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        
        // Get peak listening hour
        int peakHour = 0;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : listeningTimeDistribution.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                peakHour = Integer.parseInt(entry.getKey());
            }
        }
        
        // Calculate proximity to peak hour (closer = higher score)
        int hourDifference = Math.abs(currentHour - peakHour);
        if (hourDifference > 12) hourDifference = 24 - hourDifference; // Handle day wraparound
        
        // Convert to score (0-1 range)
        return 1.0f - (hourDifference / 12.0f);
    }
    
    /**
     * Process user feedback on a recommendation
     * @param trackId ID of the track
     * @param isPositive Whether the feedback was positive
     * @param callback Callback for result
     */
    public void processFeedback(String trackId, boolean isPositive, FeedbackCallback callback) {
        if (trackId == null || trackId.isEmpty()) {
            if (callback != null) callback.onFeedbackProcessed(false);
            return;
        }
        
        executor.execute(() -> {
            try {
                // Update feedback ratings
                int currentRating = feedbackRatings.getOrDefault(trackId, 0);
                int adjustment = isPositive ? 1 : -1;
                feedbackRatings.put(trackId, currentRating + adjustment);
                
                // Save changes
                saveModelData();
                
                if (callback != null) {
                    callback.onFeedbackProcessed(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing feedback: " + e.getMessage());
                if (callback != null) {
                    callback.onFeedbackProcessed(false);
                }
            }
        });
    }
    
    /**
     * Get tracks similar to a target track
     * @param targetTrack The track to find similarities for
     * @param availableTracks Tracks to search through
     * @param limit Maximum number of similar tracks to return
     * @return List of similar tracks, sorted by similarity
     */
    public List<Track> getSimilarTracks(Track targetTrack, List<Track> availableTracks, int limit) {
        if (targetTrack == null || availableTracks == null || availableTracks.isEmpty()) {
            return new ArrayList<>();
        }
        
        Map<String, Float> targetFeatures = extractAudioFeatures(targetTrack);
        if (targetFeatures.isEmpty()) {
            // Fall back to genre/artist matching if no audio features
            return getSimilarTracksByMetadata(targetTrack, availableTracks, limit);
        }
        
        // Calculate similarity scores
        Map<Track, Float> similarityScores = new HashMap<>();
        for (Track track : availableTracks) {
            if (track.getId() != null && !track.getId().equals(targetTrack.getId())) {
                Map<String, Float> trackFeatures = extractAudioFeatures(track);
                float similarity = calculateFeatureSimilarity(targetFeatures, trackFeatures);
                
                // Boost similarity if same artist or genre
                if (track.getArtist() != null && track.getArtist().equals(targetTrack.getArtist())) {
                    similarity += 0.2f; // 20% boost for same artist
                }
                
                if (track.getGenres() != null && targetTrack.getGenres() != null) {
                    Set<String> trackGenres = new HashSet<>();
                    trackGenres.addAll(track.getGenres());
                    
                    for (String genre : targetTrack.getGenres()) {
                        if (trackGenres.contains(genre)) {
                            similarity += 0.1f; // 10% boost per shared genre
                            break;
                        }
                    }
                }
                
                similarityScores.put(track, similarity);
            }
        }
        
        // Sort by similarity score
        List<Track> similarTracks = new ArrayList<>(similarityScores.keySet());
        Collections.sort(similarTracks, (t1, t2) -> 
            Float.compare(similarityScores.get(t2), similarityScores.get(t1)));
        
        // Limit results
        if (similarTracks.size() > limit) {
            similarTracks = similarTracks.subList(0, limit);
        }
        
        return similarTracks;
    }
    
    /**
     * Get similar tracks based on metadata when audio features aren't available
     */
    private List<Track> getSimilarTracksByMetadata(Track targetTrack, List<Track> availableTracks, int limit) {
        Map<Track, Integer> scoreMap = new HashMap<>();
        
        String targetArtist = targetTrack.getArtist();
        List<String> targetGenres = targetTrack.getGenres();
        
        for (Track track : availableTracks) {
            if (track.getId() != null && !track.getId().equals(targetTrack.getId())) {
                int score = 0;
                
                // Same artist = high score
                if (track.getArtist() != null && targetArtist != null && 
                    track.getArtist().equals(targetArtist)) {
                    score += 5;
                }
                
                // Shared genres
                if (track.getGenres() != null && targetGenres != null) {
                    Set<String> trackGenres = new HashSet<>();
                    trackGenres.addAll(track.getGenres());
                    
                    for (String genre : targetGenres) {
                        if (trackGenres.contains(genre)) {
                            score += 3;
                        }
                    }
                }
                
                scoreMap.put(track, score);
            }
        }
        
        // Sort by score
        List<Track> similarTracks = new ArrayList<>(scoreMap.keySet());
        Collections.sort(similarTracks, (t1, t2) -> 
            Integer.compare(scoreMap.get(t2), scoreMap.get(t1)));
        
        // Limit results
        if (similarTracks.size() > limit) {
            similarTracks = similarTracks.subList(0, limit);
        }
        
        return similarTracks;
    }
    
    /**
     * Utility to convert JSON string to Map<String, Float>
     */
    private Map<String, Float> jsonToMap(String json) throws JSONException {
        Map<String, Float> map = new HashMap<>();
        JSONObject jsonObject = new JSONObject(json);
        
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, (float)jsonObject.getDouble(key));
        }
        
        return map;
    }
    
    /**
     * Convert JSON string to Map<String, Integer>
     */
    private Map<String, Integer> jsonToIntMap(String jsonStr) throws JSONException {
        Map<String, Integer> map = new HashMap<>();
        
        if (jsonStr != null && !jsonStr.isEmpty()) {
            JSONObject jsonObject = new JSONObject(jsonStr);
            Iterator<String> keys = jsonObject.keys();
            
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, jsonObject.getInt(key));
            }
        }
        
        return map;
    }
    
    /**
     * Convert Map<String, Integer> to JSON string
     */
    private String intMapToJson(Map<String, Integer> map) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            jsonObject.put(entry.getKey(), entry.getValue());
        }
        
        return jsonObject.toString();
    }
    
    /**
     * Utility to convert Map<String, Integer> to JSON string
     */
    private String stringIntMapToJson(Map<String, Integer> map) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            jsonObject.put(entry.getKey(), entry.getValue());
        }
        
        return jsonObject.toString();
    }
    
    /**
     * Utility to convert Map<String, Float> to JSON string
     */
    private String mapToJson(Map<String, Float> map) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        
        for (Map.Entry<String, Float> entry : map.entrySet()) {
            jsonObject.put(entry.getKey(), entry.getValue());
        }
        
        return jsonObject.toString();
    }
    
    /**
     * Utility to convert JSON string to Map<String, Map<String, Float>>
     */
    private Map<String, Map<String, Float>> jsonToNestedMap(String json) throws JSONException {
        Map<String, Map<String, Float>> nestedMap = new HashMap<>();
        JSONObject jsonObject = new JSONObject(json);
        
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject innerJson = jsonObject.getJSONObject(key);
            Map<String, Float> innerMap = new HashMap<>();
            
            Iterator<String> innerKeys = innerJson.keys();
            while (innerKeys.hasNext()) {
                String innerKey = innerKeys.next();
                innerMap.put(innerKey, (float)innerJson.getDouble(innerKey));
            }
            
            nestedMap.put(key, innerMap);
        }
        
        return nestedMap;
    }
    
    /**
     * Utility to convert Map<String, Map<String, Float>> to JSON string
     */
    private String nestedMapToJson(Map<String, Map<String, Float>> nestedMap) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        
        for (Map.Entry<String, Map<String, Float>> entry : nestedMap.entrySet()) {
            JSONObject innerJson = new JSONObject();
            
            for (Map.Entry<String, Float> innerEntry : entry.getValue().entrySet()) {
                innerJson.put(innerEntry.getKey(), innerEntry.getValue());
            }
            
            jsonObject.put(entry.getKey(), innerJson);
        }
        
        return jsonObject.toString();
    }
    
    /**
     * Store recommendation quality feedback for model improvement
     * 
     * @param trackId The ID of the recommended track
     * @param qualityData Map containing quality data (rating, source, timestamp, etc.)
     * @return true if successfully stored, false otherwise
     */
    public boolean storeRecommendationQuality(String trackId, Map<String, Object> qualityData) {
        if (trackId == null || trackId.isEmpty() || qualityData == null) {
            return false;
        }
        
        try {
            // Store the quality data for this track
            recommendationQuality.put(trackId, qualityData);
            
            // Save the updated model
            saveModelData();
            
            // Use this feedback to adjust weights for related genres and artists
            if (qualityData.containsKey("rating")) {
                int rating = (int) qualityData.get("rating");
                
                // Find the track in our audio feature profiles to get its genres and artist
                if (audioFeatureProfiles.containsKey(trackId)) {
                    // Adjust genre weights based on rating
                    List<String> genres = getGenresForTrack(trackId);
                    if (genres != null) {
                        for (String genre : genres) {
                            if (genreWeights.containsKey(genre)) {
                                float currentWeight = genreWeights.get(genre);
                                float adjustment = (rating - 3) * 0.05f; // -0.1 to +0.1 based on rating
                                genreWeights.put(genre, Math.max(0.1f, Math.min(1.0f, currentWeight + adjustment)));
                            }
                        }
                    }
                    
                    // Adjust artist weights based on rating
                    String artist = getArtistForTrack(trackId);
                    if (artist != null && artistWeights.containsKey(artist)) {
                        float currentWeight = artistWeights.get(artist);
                        float adjustment = (rating - 3) * 0.05f; // -0.1 to +0.1 based on rating
                        artistWeights.put(artist, Math.max(0.1f, Math.min(1.0f, currentWeight + adjustment)));
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error storing recommendation quality: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get genres associated with a track ID based on our model data
     */
    private List<String> getGenresForTrack(String trackId) {
        // In a real implementation, we would store genre information with each track
        // For this example, we'll extract from the track ID if it's in a format like "genre:rock:1234"
        if (trackId.contains(":")) {
            String[] parts = trackId.split(":");
            if (parts.length >= 2 && parts[0].equals("genre")) {
                return Collections.singletonList(parts[1]);
            }
        }
        
        // Otherwise return null
        return null;
    }
    
    /**
     * Get artist associated with a track ID based on our model data
     */
    private String getArtistForTrack(String trackId) {
        // In a real implementation, we would store artist information with each track
        // For this example, we'll extract from the track ID if it's in a format like "artist:name:1234"
        if (trackId.contains(":")) {
            String[] parts = trackId.split(":");
            if (parts.length >= 2 && parts[0].equals("artist")) {
                return parts[1];
            }
        }
        
        // Otherwise return null
        return null;
    }
    
    /**
     * Convert recommendation quality map to JSON string
     */
    private String qualityMapToJson(Map<String, Map<String, Object>> qualityMap) throws JSONException {
        JSONObject json = new JSONObject();
        
        for (Map.Entry<String, Map<String, Object>> entry : qualityMap.entrySet()) {
            JSONObject entryJson = new JSONObject();
            for (Map.Entry<String, Object> dataEntry : entry.getValue().entrySet()) {
                entryJson.put(dataEntry.getKey(), dataEntry.getValue());
            }
            json.put(entry.getKey(), entryJson);
        }
        
        return json.toString();
    }
    
    /**
     * Parse JSON string into recommendation quality map
     */
    private Map<String, Map<String, Object>> jsonToQualityMap(String jsonStr) throws JSONException {
        Map<String, Map<String, Object>> result = new HashMap<>();
        JSONObject json = new JSONObject(jsonStr);
        
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject entryJson = json.getJSONObject(key);
            
            Map<String, Object> data = new HashMap<>();
            Iterator<String> dataKeys = entryJson.keys();
            while (dataKeys.hasNext()) {
                String dataKey = dataKeys.next();
                data.put(dataKey, entryJson.get(dataKey));
            }
            
            result.put(key, data);
        }
        
        return result;
    }

    private void processTrackGenres(Track track) {
        List<String> genres = track.getGenres();
        if (genres == null || genres.isEmpty()) {
            return;
        }
        
        for (String genre : genres) {
            genreWeights.put(genre, genreWeights.getOrDefault(genre, 0.0f) + 1.0f);
        }
    }

    private Set<String> getTrackGenres(Track track) {
        Set<String> trackGenres = new HashSet<>();
        if (track.getGenres() != null) {
            trackGenres.addAll(track.getGenres());
        }
        return trackGenres;
    }

    private float calculateGenreSimilarity(Track targetTrack, Track track) {
        List<String> targetGenres = targetTrack.getGenres();
        List<String> compareGenres = track.getGenres();
        
        if (targetGenres == null || compareGenres == null || 
            targetGenres.isEmpty() || compareGenres.isEmpty()) {
            return 0.0f;
        }

        Set<String> trackGenres = new HashSet<>(track.getGenres());
        
        // Calculate Jaccard similarity
        Set<String> intersection = new HashSet<>(targetGenres);
        intersection.retainAll(trackGenres);
        
        Set<String> union = new HashSet<>(targetGenres);
        union.addAll(trackGenres);
        
        return union.isEmpty() ? 0.0f : (float) intersection.size() / union.size();
    }

    private void updateGenreWeight(String genre) {
        genreWeights.put(genre, genreWeights.getOrDefault(genre, 0.0f) + 1.0f);
    }
}