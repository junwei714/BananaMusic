package my.edu.utar.bananamusic.ml;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * A TensorFlow Lite-based classifier for mood detection and song recommendation
 */
public class MoodClassifier {
    private static final String TAG = "MoodClassifier";
    private static final String MODEL_FILE = "mood_classifier.tflite";
    
    // Mood categories supported by the model
    public static final String MOOD_HAPPY = "happy";
    public static final String MOOD_SAD = "sad";
    public static final String MOOD_ENERGETIC = "energetic";
    public static final String MOOD_RELAXED = "relaxed";
    public static final String MOOD_ROMANTIC = "romantic";
    public static final String MOOD_FOCUSED = "focused";
    
    // Singleton instance
    private static MoodClassifier instance;
    
    // TensorFlow Lite interpreter
    private Interpreter tfliteInterpreter;
    private boolean modelLoaded = false;
    
    // Input and output sizes for the model
    private static final int INPUT_SIZE = 1;
    private static final int OUTPUT_SIZE = 6; // Number of mood categories
    
    // Mood to index mapping
    private final Map<String, Integer> moodToIndex = new HashMap<>();
    private final Map<Integer, String> indexToMood = new HashMap<>();
    
    // Feature definitions for mood detection
    private static final String[] FEATURES = {
        "danceability", "energy", "tempo", "valence", "acousticness", "loudness"
    };
    
    // Mood characteristic profiles - these would be learned by the model
    // but we're providing defaults for immediate use
    private final Map<String, float[]> moodProfiles = new HashMap<>();
    
    private MoodClassifier(Context context) {
        initializeMoodMappings();
        initializeMoodProfiles();
        loadModel(context);
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized MoodClassifier getInstance(Context context) {
        if (instance == null) {
            instance = new MoodClassifier(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Load the TensorFlow Lite model
     */
    private void loadModel(Context context) {
        try {
            // Check if the model file exists in assets
            String[] assetList = context.getAssets().list("");
            boolean modelExists = false;
            for (String asset : assetList) {
                if (MODEL_FILE.equals(asset)) {
                    modelExists = true;
                    break;
                }
            }
            
            if (!modelExists) {
                Log.w(TAG, "Model file not found in assets, using fallback approach");
                return;
            }
            
            // For this demo, the TFLite file is a placeholder text file, not a real model
            // So we'll check the first few bytes to see if it's a real flatbuffer
            try (java.io.InputStream inputStream = context.getAssets().open(MODEL_FILE)) {
                byte[] header = new byte[4];
                int bytesRead = inputStream.read(header);
                
                // TFLite files should start with the bytes "TFL3"
                if (bytesRead < 4 || header[0] != 'T' || header[1] != 'F' || header[2] != 'L' || header[3] != '3') {
                    Log.w(TAG, "File exists but is not a valid TFLite model, using fallback approach");
                    return;
                }
                
                // If we get here, try loading the real model
                ByteBuffer modelBuffer = loadModelFile(context);
                Interpreter.Options options = new Interpreter.Options();
                options.setUseNNAPI(false);
                tfliteInterpreter = new Interpreter(modelBuffer, options);
                modelLoaded = true;
                Log.d(TAG, "TensorFlow Lite model loaded successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading TensorFlow Lite model: " + e.getMessage());
            Log.d(TAG, "Using fallback mood classification method");
            // We'll fall back to the rule-based approach when model loading fails
        }
    }
    
    /**
     * Load the model file directly from assets
     */
    private ByteBuffer loadModelFile(Context context) throws IOException {
        try (java.io.InputStream inputStream = context.getAssets().open(MODEL_FILE)) {
            // Get the size of the file
            int fileSize = inputStream.available();
            
            // Allocate a ByteBuffer to store the model
            ByteBuffer modelBuffer = ByteBuffer.allocateDirect(fileSize);
            modelBuffer.order(ByteOrder.nativeOrder());
            
            // Read the model into the ByteBuffer
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                modelBuffer.put(buffer, 0, bytesRead);
            }
            
            // Reset the position to the beginning
            modelBuffer.rewind();
            
            Log.d(TAG, "Successfully loaded model from assets: " + fileSize + " bytes");
            return modelBuffer;
        }
    }
    
    /**
     * Initialize mood to index mappings
     */
    private void initializeMoodMappings() {
        // Set up bidirectional mappings
        String[] moods = {
            MOOD_HAPPY, MOOD_SAD, MOOD_ENERGETIC, 
            MOOD_RELAXED, MOOD_ROMANTIC, MOOD_FOCUSED
        };
        
        for (int i = 0; i < moods.length; i++) {
            moodToIndex.put(moods[i], i);
            indexToMood.put(i, moods[i]);
        }
    }
    
    /**
     * Initialize mood profiles with characteristic audio features
     */
    private void initializeMoodProfiles() {
        // Happy mood: high valence, moderate-high energy, moderate-high tempo
        moodProfiles.put(MOOD_HAPPY, new float[]{0.8f, 0.7f, 120f, 0.85f, 0.3f, -6f});
        
        // Sad mood: low valence, low energy, slow tempo, high acousticness
        moodProfiles.put(MOOD_SAD, new float[]{0.3f, 0.3f, 80f, 0.2f, 0.8f, -12f});
        
        // Energetic mood: high energy, high tempo, moderate-high danceability
        moodProfiles.put(MOOD_ENERGETIC, new float[]{0.7f, 0.9f, 140f, 0.6f, 0.2f, -4f});
        
        // Relaxed mood: low energy, slow tempo, high acousticness
        moodProfiles.put(MOOD_RELAXED, new float[]{0.4f, 0.3f, 90f, 0.5f, 0.7f, -14f});
        
        // Romantic mood: moderate valence, moderate energy, moderate acousticness
        moodProfiles.put(MOOD_ROMANTIC, new float[]{0.5f, 0.5f, 100f, 0.6f, 0.5f, -10f});
        
        // Focused mood: low-moderate energy, moderate tempo, high acousticness
        moodProfiles.put(MOOD_FOCUSED, new float[]{0.3f, 0.4f, 100f, 0.4f, 0.7f, -12f});
    }
    
    /**
     * Detect the mood from audio features using the TensorFlow Lite model
     * @param audioFeatures Map of audio feature names to values
     * @return The detected mood
     */
    public String detectMood(Map<String, Float> audioFeatures) {
        if (modelLoaded) {
            return detectMoodWithModel(audioFeatures);
        } else {
            return detectMoodWithRules(audioFeatures);
        }
    }
    
    /**
     * Detect mood using the TensorFlow Lite model
     */
    private String detectMoodWithModel(Map<String, Float> audioFeatures) {
        try {
            // Prepare input data
            float[][] input = new float[1][FEATURES.length];
            for (int i = 0; i < FEATURES.length; i++) {
                String feature = FEATURES[i];
                input[0][i] = audioFeatures.containsKey(feature) ? 
                    audioFeatures.get(feature) : 0.0f;
            }
            
            // Prepare output buffer
            float[][] output = new float[1][OUTPUT_SIZE];
            
            // Run inference
            tfliteInterpreter.run(input, output);
            
            // Find the mood with highest probability
            int maxIndex = 0;
            float maxValue = output[0][0];
            
            for (int i = 1; i < OUTPUT_SIZE; i++) {
                if (output[0][i] > maxValue) {
                    maxValue = output[0][i];
                    maxIndex = i;
                }
            }
            
            // Convert index to mood
            return indexToMood.getOrDefault(maxIndex, MOOD_HAPPY);
            
        } catch (Exception e) {
            Log.e(TAG, "Error running model inference: " + e.getMessage());
            return detectMoodWithRules(audioFeatures);
        }
    }
    
    /**
     * Rule-based fallback for mood detection when model fails
     */
    private String detectMoodWithRules(Map<String, Float> audioFeatures) {
        // Extract key features with defaults
        float valence = audioFeatures.getOrDefault("valence", 0.5f);
        float energy = audioFeatures.getOrDefault("energy", 0.5f);
        float tempo = audioFeatures.getOrDefault("tempo", 120f);
        float acousticness = audioFeatures.getOrDefault("acousticness", 0.5f);
        
        // Simple rule-based classification
        if (valence > 0.7f && energy > 0.6f) {
            return MOOD_HAPPY;
        } else if (valence < 0.3f && energy < 0.4f) {
            return MOOD_SAD;
        } else if (energy > 0.8f && tempo > 125f) {
            return MOOD_ENERGETIC;
        } else if (energy < 0.4f && acousticness > 0.6f) {
            return MOOD_RELAXED;
        } else if (valence > 0.5f && energy > 0.4f && energy < 0.6f) {
            return MOOD_ROMANTIC;
        } else if (acousticness > 0.6f && energy > 0.3f && energy < 0.5f) {
            return MOOD_FOCUSED;
        }
        
        // Default to happy if no clear match
        return MOOD_HAPPY;
    }
    
    /**
     * Get the similarity score between a track's features and a target mood
     * @param audioFeatures The track's audio features
     * @param targetMood The target mood
     * @return Similarity score (0-1, higher is more similar)
     */
    public float getMoodSimilarityScore(Map<String, Float> audioFeatures, String targetMood) {
        if (!moodProfiles.containsKey(targetMood)) {
            return 0.0f;
        }
        
        float[] targetProfile = moodProfiles.get(targetMood);
        float totalDistance = 0.0f;
        float maxDistance = FEATURES.length * 2.0f; // Maximum possible distance
        
        // Calculate normalized Euclidean distance between features
        for (int i = 0; i < FEATURES.length; i++) {
            String feature = FEATURES[i];
            float trackValue = audioFeatures.getOrDefault(feature, 0.0f);
            float targetValue = targetProfile[i];
            
            // Normalize tempo to 0-1 range
            if (feature.equals("tempo")) {
                trackValue = trackValue / 200.0f; // Assuming max tempo is 200 BPM
                targetValue = targetValue / 200.0f;
            }
            
            // Normalize loudness from dB (-60 to 0) to 0-1 range
            if (feature.equals("loudness")) {
                trackValue = (trackValue + 60.0f) / 60.0f;
                targetValue = (targetValue + 60.0f) / 60.0f;
            }
            
            float featureDistance = Math.abs(trackValue - targetValue);
            totalDistance += featureDistance;
        }
        
        // Convert distance to similarity (1 - normalized distance)
        return 1.0f - (totalDistance / maxDistance);
    }
    
    /**
     * Sort tracks by how well they match a given mood
     * @param tracks List of tracks with audio features
     * @param targetMood The mood to match
     * @return Map of tracks to their mood similarity scores
     */
    public Map<String, Float> rankTracksByMood(Map<String, Map<String, Float>> tracks, String targetMood) {
        Map<String, Float> trackScores = new HashMap<>();
        
        for (Map.Entry<String, Map<String, Float>> entry : tracks.entrySet()) {
            String trackId = entry.getKey();
            Map<String, Float> features = entry.getValue();
            
            float score = getMoodSimilarityScore(features, targetMood);
            trackScores.put(trackId, score);
        }
        
        return trackScores;
    }
    
    /**
     * Predict the mood that best matches a given text input
     * @param textInput User's text input
     * @return The most likely mood
     */
    public String predictMoodFromText(String textInput) {
        // Normalize input
        String input = textInput.toLowerCase().trim();
        
        // Check for direct mood references
        if (input.contains("happy") || input.contains("joy") || input.contains("cheerful") || 
            input.contains("upbeat") || input.contains("good") || input.contains("great")) {
            return MOOD_HAPPY;
            
        } else if (input.contains("sad") || input.contains("depress") || input.contains("melancholy") || 
                   input.contains("down") || input.contains("blue") || input.contains("unhappy")) {
            return MOOD_SAD;
            
        } else if (input.contains("energetic") || input.contains("hyper") || input.contains("workout") || 
                   input.contains("energy") || input.contains("pump") || input.contains("active")) {
            return MOOD_ENERGETIC;
            
        } else if (input.contains("relax") || input.contains("calm") || input.contains("chill") || 
                   input.contains("peaceful") || input.contains("serene") || input.contains("tranquil")) {
            return MOOD_RELAXED;
            
        } else if (input.contains("romantic") || input.contains("love") || input.contains("passion") || 
                   input.contains("date") || input.contains("dating") || input.contains("relationship")) {
            return MOOD_ROMANTIC;
            
        } else if (input.contains("focus") || input.contains("study") || input.contains("work") || 
                   input.contains("concentrate") || input.contains("productivity")) {
            return MOOD_FOCUSED;
        }
        
        // No clear match - analyze sentiment
        boolean positiveWords = input.contains("good") || input.contains("great") || 
                               input.contains("happy") || input.contains("joy");
                               
        boolean negativeWords = input.contains("bad") || input.contains("sad") || 
                               input.contains("angry") || input.contains("upset");
                               
        boolean highEnergy = input.contains("energy") || input.contains("active") || 
                            input.contains("fast") || input.contains("strong");
                            
        boolean lowEnergy = input.contains("slow") || input.contains("quiet") || 
                           input.contains("calm") || input.contains("relax");
        
        // Determine mood based on sentiment analysis
        if (positiveWords && highEnergy) {
            return MOOD_ENERGETIC;
        } else if (positiveWords && lowEnergy) {
            return MOOD_RELAXED;
        } else if (negativeWords && highEnergy) {
            return MOOD_ENERGETIC; // Angry tends to match with energetic music
        } else if (negativeWords && lowEnergy) {
            return MOOD_SAD;
        } else if (positiveWords) {
            return MOOD_HAPPY;
        } else if (negativeWords) {
            return MOOD_SAD;
        }
        
        // Default mood
        return MOOD_HAPPY;
    }
} 