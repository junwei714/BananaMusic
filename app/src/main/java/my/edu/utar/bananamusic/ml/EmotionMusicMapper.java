package my.edu.utar.bananamusic.ml;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import my.edu.utar.bananamusic.models.Track;

/**
 * Enhanced Emotion to Music Mapper - handles mapping between emotions and musical attributes
 * with user feedback adaptation and mixed emotion support.
 */
public class EmotionMusicMapper {
    private static final String TAG = "EmotionMusicMapper";
    
    // SharedPreferences keys
    private static final String PREF_FILE = "emotion_mapper_prefs";
    private static final String KEY_EMOTION_ATTRIBUTES = "emotion_attributes";
    private static final String KEY_USER_FEEDBACK = "user_feedback";
    private static final String KEY_USER_EMOTION_PROFILES = "user_emotion_profiles";
    
    // Singleton instance
    private static EmotionMusicMapper instance;
    
    // Dependencies
    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson;
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    // Core emotion to musical attribute mapping
    private Map<String, Map<String, Float>> emotionAttributes = new HashMap<>();
    
    // User feedback tracking
    private Map<String, List<FeedbackEntry>> userFeedback = new HashMap<>();
    
    // User emotion profiles (personalized emotional responses)
    private Map<String, Map<String, Float>> userEmotionProfiles = new HashMap<>();
    
    /**
     * Private constructor - use getInstance()
     */
    private EmotionMusicMapper(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        this.gson = new Gson();
        
        // Initialize with default mappings
        initializeDefaultMappings();
        
        // Load saved data
        loadData();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized EmotionMusicMapper getInstance(Context context) {
        if (instance == null) {
            instance = new EmotionMusicMapper(context);
        }
        return instance;
    }
    
    /**
     * Initialize default emotion to music attribute mappings
     */
    private void initializeDefaultMappings() {
        // HAPPY - upbeat, energetic, major key
        Map<String, Float> happyAttributes = new HashMap<>();
        happyAttributes.put("tempo", 120.0f);       // Moderate to fast tempo
        happyAttributes.put("energy", 0.8f);        // High energy
        happyAttributes.put("valence", 0.9f);       // High positivity
        happyAttributes.put("danceability", 0.7f);  // Danceable
        happyAttributes.put("acousticness", 0.3f);  // Not particularly acoustic
        happyAttributes.put("instrumentalness", 0.2f); // Mostly with vocals
        happyAttributes.put("mode", 1.0f);          // Major key
        emotionAttributes.put("happy", happyAttributes);
        
        // SAD - slower, minor key, more acoustic
        Map<String, Float> sadAttributes = new HashMap<>();
        sadAttributes.put("tempo", 80.0f);         // Slower tempo
        sadAttributes.put("energy", 0.4f);         // Lower energy
        sadAttributes.put("valence", 0.2f);        // Low positivity
        sadAttributes.put("danceability", 0.3f);   // Less danceable
        sadAttributes.put("acousticness", 0.7f);   // More acoustic
        sadAttributes.put("instrumentalness", 0.4f); // Mix of instrumental and vocals
        sadAttributes.put("mode", 0.0f);           // Minor key
        emotionAttributes.put("sad", sadAttributes);
        
        // RELAXED - slow tempo, low energy, acoustic
        Map<String, Float> relaxedAttributes = new HashMap<>();
        relaxedAttributes.put("tempo", 85.0f);     // Slow to moderate tempo
        relaxedAttributes.put("energy", 0.3f);     // Low energy
        relaxedAttributes.put("valence", 0.6f);    // Moderate positivity
        relaxedAttributes.put("danceability", 0.4f); // Less danceable
        relaxedAttributes.put("acousticness", 0.8f); // Highly acoustic
        relaxedAttributes.put("instrumentalness", 0.6f); // More instrumental
        relaxedAttributes.put("mode", 0.5f);        // Mix of major and minor
        emotionAttributes.put("relaxed", relaxedAttributes);
        
        // ENERGETIC - fast tempo, high energy, danceable
        Map<String, Float> energeticAttributes = new HashMap<>();
        energeticAttributes.put("tempo", 130.0f);    // Fast tempo
        energeticAttributes.put("energy", 0.9f);     // Very high energy
        energeticAttributes.put("valence", 0.7f);    // Moderate-high positivity
        energeticAttributes.put("danceability", 0.8f); // Highly danceable
        energeticAttributes.put("acousticness", 0.2f); // Less acoustic
        energeticAttributes.put("instrumentalness", 0.3f); // Mix with vocals
        energeticAttributes.put("mode", 0.7f);        // More major than minor
        emotionAttributes.put("energetic", energeticAttributes);
        
        // FOCUSED - medium tempo, moderate energy, more instrumental
        Map<String, Float> focusedAttributes = new HashMap<>();
        focusedAttributes.put("tempo", 100.0f);     // Medium tempo
        focusedAttributes.put("energy", 0.5f);      // Moderate energy
        focusedAttributes.put("valence", 0.5f);     // Neutral
        focusedAttributes.put("danceability", 0.3f); // Less danceable
        focusedAttributes.put("acousticness", 0.6f); // Somewhat acoustic
        focusedAttributes.put("instrumentalness", 0.8f); // Mostly instrumental
        focusedAttributes.put("mode", 0.5f);         // Mix of major and minor
        emotionAttributes.put("focused", focusedAttributes);
        
        // ROMANTIC - medium tempo, warm, emotional
        Map<String, Float> romanticAttributes = new HashMap<>();
        romanticAttributes.put("tempo", 90.0f);      // Medium-slow tempo
        romanticAttributes.put("energy", 0.6f);      // Moderate energy
        romanticAttributes.put("valence", 0.7f);     // Fairly positive
        romanticAttributes.put("danceability", 0.5f); // Moderate danceability
        romanticAttributes.put("acousticness", 0.6f); // Somewhat acoustic
        romanticAttributes.put("instrumentalness", 0.3f); // Mostly with vocals
        romanticAttributes.put("mode", 0.6f);         // More major than minor
        emotionAttributes.put("romantic", romanticAttributes);
        
        // ANXIOUS - faster tempo, dissonance, higher intensity
        Map<String, Float> anxiousAttributes = new HashMap<>();
        anxiousAttributes.put("tempo", 125.0f);      // Fast tempo
        anxiousAttributes.put("energy", 0.7f);       // High energy
        anxiousAttributes.put("valence", 0.3f);      // Low-moderate positivity
        anxiousAttributes.put("danceability", 0.4f); // Less danceable
        anxiousAttributes.put("acousticness", 0.4f); // Mixed
        anxiousAttributes.put("instrumentalness", 0.5f); // Mixed
        anxiousAttributes.put("mode", 0.3f);         // More minor than major
        emotionAttributes.put("anxious", anxiousAttributes);
        
        // NOSTALGIC - moderate tempo, warm, emotional resonance
        Map<String, Float> nostalgicAttributes = new HashMap<>();
        nostalgicAttributes.put("tempo", 95.0f);      // Medium tempo
        nostalgicAttributes.put("energy", 0.5f);      // Moderate energy
        nostalgicAttributes.put("valence", 0.6f);     // Moderate positivity
        nostalgicAttributes.put("danceability", 0.4f); // Less danceable
        nostalgicAttributes.put("acousticness", 0.7f); // More acoustic
        nostalgicAttributes.put("instrumentalness", 0.4f); // Mix of vocals and instrumental
        nostalgicAttributes.put("mode", 0.5f);         // Balance of major and minor
        emotionAttributes.put("nostalgic", nostalgicAttributes);
    }
    
    /**
     * Load saved data from SharedPreferences
     */
    private void loadData() {
        String emotionAttributesJson = preferences.getString(KEY_EMOTION_ATTRIBUTES, "");
        if (!emotionAttributesJson.isEmpty()) {
            Type type = new TypeToken<Map<String, Map<String, Float>>>(){}.getType();
            Map<String, Map<String, Float>> savedAttributes = gson.fromJson(emotionAttributesJson, type);
            if (savedAttributes != null) {
                emotionAttributes = savedAttributes;
            }
        }
        
        String userFeedbackJson = preferences.getString(KEY_USER_FEEDBACK, "");
        if (!userFeedbackJson.isEmpty()) {
            Type type = new TypeToken<Map<String, List<FeedbackEntry>>>(){}.getType();
            Map<String, List<FeedbackEntry>> savedFeedback = gson.fromJson(userFeedbackJson, type);
            if (savedFeedback != null) {
                userFeedback = savedFeedback;
            }
        }
        
        String userProfilesJson = preferences.getString(KEY_USER_EMOTION_PROFILES, "");
        if (!userProfilesJson.isEmpty()) {
            Type type = new TypeToken<Map<String, Map<String, Float>>>(){}.getType();
            Map<String, Map<String, Float>> savedProfiles = gson.fromJson(userProfilesJson, type);
            if (savedProfiles != null) {
                userEmotionProfiles = savedProfiles;
            }
        }
    }
    
    /**
     * Save data to SharedPreferences
     */
    private void saveData() {
        SharedPreferences.Editor editor = preferences.edit();
        
        // Save emotion attributes
        String emotionAttributesJson = gson.toJson(emotionAttributes);
        editor.putString(KEY_EMOTION_ATTRIBUTES, emotionAttributesJson);
        
        // Save user feedback
        String userFeedbackJson = gson.toJson(userFeedback);
        editor.putString(KEY_USER_FEEDBACK, userFeedbackJson);
        
        // Save user profiles
        String userProfilesJson = gson.toJson(userEmotionProfiles);
        editor.putString(KEY_USER_EMOTION_PROFILES, userProfilesJson);
        
        editor.apply();
    }
    
    /**
     * Get music attributes for an emotion
     * @param emotion The emotion to get attributes for
     * @return Map of musical attributes to values
     */
    public Map<String, Float> getAttributesForEmotion(String emotion) {
        String normalizedEmotion = normalizeEmotion(emotion);
        
        // Check for personal profile first
        if (userEmotionProfiles.containsKey(normalizedEmotion)) {
            return userEmotionProfiles.get(normalizedEmotion);
        }
        
        // Fall back to standard profiles
        if (emotionAttributes.containsKey(normalizedEmotion)) {
            return emotionAttributes.get(normalizedEmotion);
        }
        
        // Default to happy if emotion not found
        return emotionAttributes.getOrDefault("happy", new HashMap<>());
    }
    
    /**
     * Get music attributes for a blend of emotions
     * @param emotionMap Map of emotions to their intensity (0.0-1.0)
     * @return Blended map of musical attributes
     */
    public Map<String, Float> getAttributesForEmotionBlend(Map<String, Float> emotionMap) {
        // If no emotions or only one emotion, handle simply
        if (emotionMap == null || emotionMap.isEmpty()) {
            return getAttributesForEmotion("happy");
        } else if (emotionMap.size() == 1) {
            Map.Entry<String, Float> entry = emotionMap.entrySet().iterator().next();
            return getAttributesForEmotion(entry.getKey());
        }
        
        // Create an empty result map for the blended attributes
        Map<String, Float> blendedAttributes = new HashMap<>();
        
        // Normalize weights to ensure they sum to 1.0
        float totalWeight = 0f;
        for (float weight : emotionMap.values()) {
            totalWeight += weight;
        }
        
        // For each emotion, get its attributes and blend them according to weight
        for (Map.Entry<String, Float> emotion : emotionMap.entrySet()) {
            float normalizedWeight = emotion.getValue() / totalWeight;
            Map<String, Float> attributes = getAttributesForEmotion(emotion.getKey());
            
            // For each attribute in this emotion, add its weighted value
            for (Map.Entry<String, Float> attr : attributes.entrySet()) {
                String attrName = attr.getKey();
                float weightedValue = attr.getValue() * normalizedWeight;
                
                // Add to result map
                blendedAttributes.put(attrName, blendedAttributes.getOrDefault(attrName, 0f) + weightedValue);
            }
        }
        
        return blendedAttributes;
    }
    
    /**
     * Record user feedback about a recommendation
     * @param emotion The emotion that was detected
     * @param track The track that was recommended
     * @param rating User rating from 1-5 (5 being best match)
     * @return True if feedback was successfully recorded
     */
    public boolean recordFeedback(String emotion, Track track, int rating) {
        if (emotion == null || track == null || rating < 1 || rating > 5) {
            return false;
        }
        
        String normalizedEmotion = normalizeEmotion(emotion);
        
        // Create feedback entry
        FeedbackEntry entry = new FeedbackEntry();
        entry.trackId = track.getTrackId();
        entry.trackName = track.getTitle();
        entry.timestamp = System.currentTimeMillis();
        entry.rating = rating;
        entry.trackAttributes = extractTrackAttributes(track);
        
        // Add to feedback collection
        if (!userFeedback.containsKey(normalizedEmotion)) {
            userFeedback.put(normalizedEmotion, new ArrayList<>());
        }
        userFeedback.get(normalizedEmotion).add(entry);
        
        // Save updated feedback
        saveData();
        
        // Process the feedback to update emotion profiles - done asynchronously
        executor.execute(() -> updateEmotionProfilesBasedOnFeedback(normalizedEmotion));
        
        return true;
    }
    
    /**
     * Update emotion profiles based on accumulated feedback
     * @param emotion The specific emotion to update
     */
    private void updateEmotionProfilesBasedOnFeedback(String emotion) {
        List<FeedbackEntry> feedbackList = userFeedback.get(emotion);
        if (feedbackList == null || feedbackList.isEmpty()) {
            return;
        }
        
        // Get current default attributes for this emotion
        Map<String, Float> currentAttributes = new HashMap<>(emotionAttributes.getOrDefault(emotion, new HashMap<>()));
        
        // Extract high-rated tracks (4-5 stars)
        List<Map<String, Float>> goodMatches = new ArrayList<>();
        for (FeedbackEntry entry : feedbackList) {
            if (entry.rating >= 4 && entry.trackAttributes != null) {
                goodMatches.add(entry.trackAttributes);
            }
        }
        
        // If we have good matches, compute average attributes
        if (!goodMatches.isEmpty()) {
            Map<String, Float> userProfile = new HashMap<>();
            Map<String, Integer> attributeCounts = new HashMap<>();
            
            // Sum all attributes
            for (Map<String, Float> attributes : goodMatches) {
                for (Map.Entry<String, Float> attr : attributes.entrySet()) {
                    String key = attr.getKey();
                    float value = attr.getValue();
                    
                    // Add to running sum
                    userProfile.put(key, userProfile.getOrDefault(key, 0f) + value);
                    attributeCounts.put(key, attributeCounts.getOrDefault(key, 0) + 1);
                }
            }
            
            // Calculate averages
            for (String key : userProfile.keySet()) {
                int count = attributeCounts.getOrDefault(key, 0);
                if (count > 0) {
                    userProfile.put(key, userProfile.get(key) / count);
                }
            }
            
            // Blend with default attributes (70% user preference, 30% defaults)
            // This prevents overfitting to a small number of tracks
            Map<String, Float> blendedProfile = new HashMap<>();
            
            // Add all keys from both maps
            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(userProfile.keySet());
            allKeys.addAll(currentAttributes.keySet());
            
            for (String key : allKeys) {
                float userValue = userProfile.getOrDefault(key, 0f);
                float defaultValue = currentAttributes.getOrDefault(key, 0f);
                
                // If both have the attribute, blend them
                if (userProfile.containsKey(key) && currentAttributes.containsKey(key)) {
                    blendedProfile.put(key, (userValue * 0.7f) + (defaultValue * 0.3f));
                } 
                // If only user profile has it, use that with slight regression to defaults
                else if (userProfile.containsKey(key)) {
                    blendedProfile.put(key, userValue * 0.8f);
                }
                // If only default has it, keep the default
                else {
                    blendedProfile.put(key, defaultValue);
                }
            }
            
            // Save the personalized profile
            userEmotionProfiles.put(emotion, blendedProfile);
            saveData();
            Log.d(TAG, "Updated user emotion profile for: " + emotion);
        }
    }
    
    /**
     * Extract attributes from a track
     */
    private Map<String, Float> extractTrackAttributes(Track track) {
        Map<String, Float> attributes = new HashMap<>();
        
        // Extract available attributes from track
        if (track.getTempo() > 0) {
            attributes.put("tempo", (float) track.getTempo());
        }
        
        if (track.getValence() > 0) {
            attributes.put("valence", track.getValence());
        }
        
        if (track.getEnergy() > 0) {
            attributes.put("energy", track.getEnergy());
        }
        
        if (track.getDanceability() > 0) {
            attributes.put("danceability", track.getDanceability());
        }
        
        if (track.getAcousticness() > 0) {
            attributes.put("acousticness", track.getAcousticness());
        }
        
        // Instrumentalness is not available in the Track class yet
        // We'll use a default value for now
        attributes.put("instrumentalness", 0.5f);
        
        return attributes;
    }
    
    /**
     * Get a list of available emotions
     * @return List of supported emotions
     */
    public List<String> getAvailableEmotions() {
        return new ArrayList<>(emotionAttributes.keySet());
    }
    
    /**
     * Normalize emotion name for consistency
     */
    private String normalizeEmotion(String emotion) {
        if (emotion == null || emotion.isEmpty()) {
            return "happy";
        }
        
        String normalized = emotion.toLowerCase().trim();
        
        // Map similar emotions
        if (normalized.equals("joyful") || normalized.equals("excited") || normalized.equals("cheerful")) {
            return "happy";
        } else if (normalized.equals("depressed") || normalized.equals("down") || normalized.equals("gloomy")) {
            return "sad";
        } else if (normalized.equals("calm") || normalized.equals("peaceful") || normalized.equals("chill")) {
            return "relaxed";
        } else if (normalized.equals("lively") || normalized.equals("active") || normalized.equals("pumped")) {
            return "energetic";
        } else if (normalized.equals("productive") || normalized.equals("studying") || normalized.equals("working")) {
            return "focused";
        } else if (normalized.equals("love") || normalized.equals("passionate") || normalized.equals("sensual")) {
            return "romantic";
        } else if (normalized.equals("nervous") || normalized.equals("stressed") || normalized.equals("worried")) {
            return "anxious";
        } else if (normalized.equals("melancholic") || normalized.equals("yearning") || normalized.equals("reminiscent")) {
            return "nostalgic";
        }
        
        return normalized;
    }
    
    /**
     * Class for storing user feedback entries
     */
    public static class FeedbackEntry {
        public String trackId;
        public String trackName;
        public long timestamp;
        public int rating;  // 1-5 scale
        public Map<String, Float> trackAttributes;
    }
    
    /**
     * Clear all user feedback data
     */
    public void clearFeedbackData() {
        userFeedback.clear();
        userEmotionProfiles.clear();
        saveData();
    }
} 