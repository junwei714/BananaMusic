package my.edu.utar.bananamusic.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Enhanced text analyzer using TensorFlow Lite for emotion detection
 * Supports multiple emotion categories with confidence scores
 */
public class EnhancedTextAnalyzer {
    private static final String TAG = "EnhancedTextAnalyzer";
    
    // TensorFlow Lite model filename
    private static final String MODEL_FILENAME = "enhanced_emotion_model.tflite";
    
    // Model configuration
    private static final int MAX_TEXT_LENGTH = 128;
    private static final int EMBEDDING_SIZE = 384;  // Expected embedding size for the model input
    private static final int NUM_EMOTION_CATEGORIES = 8;  // Number of emotion categories the model can detect
    
    // Emotion labels in order they appear in model output
    private static final String[] EMOTION_LABELS = {
        "happy", "sad", "angry", "relaxed", "focused", "energetic", "romantic", "anxious"
    };
    
    // Singleton instance
    private static EnhancedTextAnalyzer instance;
    
    // TensorFlow Lite interpreter
    private Interpreter tfliteInterpreter;
    private boolean modelInitialized = false;
    
    // Background thread executor
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    // Vocabulary map for word embedding
    private Map<String, Integer> vocabularyMap;
    private int vocabularySize;
    
    // Required dependencies
    private final Context context;
    
    /**
     * Private constructor - use getInstance()
     */
    private EnhancedTextAnalyzer(Context context) {
        this.context = context.getApplicationContext();
        
        // Initialize model on a background thread
        executor.execute(this::initializeModel);
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized EnhancedTextAnalyzer getInstance(Context context) {
        if (instance == null) {
            instance = new EnhancedTextAnalyzer(context);
        }
        return instance;
    }
    
    /**
     * Initialize the TensorFlow Lite model and vocabulary
     */
    private void initializeModel() {
        try {
            // Load the TensorFlow Lite model
            MappedByteBuffer modelBuffer = loadModelFile();
            if (modelBuffer != null) {
                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(2);
                tfliteInterpreter = new Interpreter(modelBuffer, options);
                
                // Load vocabulary for text tokenization
                loadVocabulary();
                
                modelInitialized = true;
                Log.d(TAG, "Enhanced text analyzer model initialized successfully");
            } else {
                Log.e(TAG, "Failed to load model file");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing enhanced text analyzer: " + e.getMessage());
        }
    }
    
    /**
     * Load the TensorFlow Lite model file from assets
     */
    private MappedByteBuffer loadModelFile() {
        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILENAME);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            
            Log.d(TAG, "Loading model file: " + MODEL_FILENAME);
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            inputStream.close();
            fileDescriptor.close();
            return buffer;
        } catch (IOException e) {
            Log.e(TAG, "Error loading model file: " + e.getMessage());
            // Try an alternative approach using TFLite Support Library
            try {
                return FileUtil.loadMappedFile(context, MODEL_FILENAME);
            } catch (IOException ex) {
                Log.e(TAG, "Error loading model with alternative method: " + ex.getMessage());
                return null;
            }
        }
    }
    
    /**
     * Load vocabulary for tokenization
     */
    private void loadVocabulary() {
        try {
            // Try to load vocabulary file from assets
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("vocabulary.txt")));
            
            vocabularyMap = new HashMap<>();
            String line;
            int index = 0;
            
            while ((line = reader.readLine()) != null) {
                vocabularyMap.put(line.trim(), index++);
            }
            
            vocabularySize = vocabularyMap.size();
            reader.close();
            
            Log.d(TAG, "Vocabulary loaded: " + vocabularySize + " tokens");
        } catch (IOException e) {
            Log.e(TAG, "Error loading vocabulary: " + e.getMessage());
            
            // Use a simplified vocabulary as fallback
            initializeSimplifiedVocabulary();
        }
    }
    
    /**
     * Initialize a simplified vocabulary for fallback
     */
    private void initializeSimplifiedVocabulary() {
        vocabularyMap = new HashMap<>();
        
        // Add common emotion-related words with indices
        String[] commonWords = {
            "happy", "sad", "angry", "relaxed", "calm", "excited", "energetic", "tired",
            "focused", "distracted", "romantic", "anxious", "stressed", "peaceful", "love",
            "hate", "joy", "sorrow", "fear", "surprise", "good", "bad", "feel", "feeling",
            "mood", "emotion", "i", "am", "very", "extremely", "somewhat", "little", "bit",
            "not", "don't", "dont", "do", "really", "super", "music", "song", "play", "listen"
        };
        
        for (int i = 0; i < commonWords.length; i++) {
            vocabularyMap.put(commonWords[i], i);
        }
        
        vocabularySize = vocabularyMap.size();
        Log.d(TAG, "Simplified vocabulary initialized with " + vocabularySize + " words");
    }
    
    /**
     * Analyze text to detect emotions
     * @param text The text to analyze
     * @return Map of emotions to confidence scores (0.0-1.0)
     */
    public Map<String, Float> analyzeEmotion(String text) {
        if (text == null || text.isEmpty()) {
            return getDefaultEmotionMap();
        }
        
        // Check if model is initialized
        if (!modelInitialized || tfliteInterpreter == null) {
            Log.w(TAG, "Model not initialized, using keyword-based fallback");
            return keywordBasedEmotionAnalysis(text);
        }
        
        try {
            // Preprocess and tokenize text
            float[][] input = tokenizeText(text);
            
            // Run inference
            float[][] output = new float[1][NUM_EMOTION_CATEGORIES];
            tfliteInterpreter.run(input, output);
            
            // Parse results
            Map<String, Float> result = new HashMap<>();
            for (int i = 0; i < EMOTION_LABELS.length; i++) {
                result.put(EMOTION_LABELS[i], output[0][i]);
            }
            
            Log.d(TAG, "Emotion analysis complete: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error during emotion analysis: " + e.getMessage());
            return keywordBasedEmotionAnalysis(text);
        }
    }
    
    /**
     * Analyze emotion asynchronously
     * @param text The text to analyze
     * @param callback Callback for results
     */
    public void analyzeEmotionAsync(String text, EmotionAnalysisCallback callback) {
        executor.execute(() -> {
            Map<String, Float> results = analyzeEmotion(text);
            callback.onEmotionAnalyzed(results);
        });
    }
    
    /**
     * Get the top emotion with highest confidence
     * @param text The text to analyze
     * @return The most likely emotion
     */
    public String detectDominantEmotion(String text) {
        Map<String, Float> emotions = analyzeEmotion(text);
        return getDominantEmotion(emotions);
    }
    
    /**
     * Extract the dominant emotion from a map of emotion scores
     */
    public String getDominantEmotion(Map<String, Float> emotions) {
        if (emotions == null || emotions.isEmpty()) {
            return "neutral";
        }
        
        String topEmotion = "neutral";
        float topScore = 0;
        
        for (Map.Entry<String, Float> entry : emotions.entrySet()) {
            if (entry.getValue() > topScore) {
                topScore = entry.getValue();
                topEmotion = entry.getKey();
            }
        }
        
        return topEmotion;
    }
    
    /**
     * Get a blend of emotions as a weighted map (only emotions with significant scores)
     * @param text The text to analyze
     * @param threshold Minimum score threshold (0.0-1.0)
     * @return Map of emotions to their weights
     */
    public Map<String, Float> getEmotionBlend(String text, float threshold) {
        Map<String, Float> allEmotions = analyzeEmotion(text);
        Map<String, Float> significantEmotions = new HashMap<>();
        
        // Keep only emotions above the threshold
        for (Map.Entry<String, Float> entry : allEmotions.entrySet()) {
            if (entry.getValue() >= threshold) {
                significantEmotions.put(entry.getKey(), entry.getValue());
            }
        }
        
        // If no emotions meet the threshold, use the top emotion
        if (significantEmotions.isEmpty() && !allEmotions.isEmpty()) {
            String dominantEmotion = getDominantEmotion(allEmotions);
            Float score = allEmotions.get(dominantEmotion);
            if (score != null) {
                significantEmotions.put(dominantEmotion, score);
            }
        }
        
        return significantEmotions;
    }
    
    /**
     * Tokenize text for model input
     */
    private float[][] tokenizeText(String text) {
        // Clean and normalize text
        String cleanedText = text.toLowerCase().replaceAll("[^a-z0-9 ]", " ");
        String[] words = cleanedText.split("\\s+");
        
        // Create input tensor of the right size
        float[][] input = new float[1][MAX_TEXT_LENGTH];
        
        // Fill with word indices
        int wordCount = Math.min(words.length, MAX_TEXT_LENGTH);
        for (int i = 0; i < wordCount; i++) {
            String word = words[i];
            // Get word index from vocabulary, use 0 if not found (which should be the padding token)
            input[0][i] = vocabularyMap.containsKey(word) ? vocabularyMap.get(word) : 0;
        }
        
        // Pad with zeros
        for (int i = wordCount; i < MAX_TEXT_LENGTH; i++) {
            input[0][i] = 0;
        }
        
        return input;
    }
    
    /**
     * Fallback keyword-based emotion analysis
     */
    private Map<String, Float> keywordBasedEmotionAnalysis(String text) {
        Map<String, Float> result = getDefaultEmotionMap();
        String lowerText = text.toLowerCase();
        
        // Simple keyword matching with confidence scores
        if (containsAny(lowerText, "happy", "joy", "great", "awesome", "excellent", "wonderful")) {
            result.put("happy", 0.8f);
        }
        
        if (containsAny(lowerText, "sad", "unhappy", "depressed", "down", "blue", "miserable")) {
            result.put("sad", 0.8f);
        }
        
        if (containsAny(lowerText, "angry", "mad", "furious", "upset", "annoyed", "irritated")) {
            result.put("angry", 0.8f);
        }
        
        if (containsAny(lowerText, "calm", "peaceful", "relaxed", "chill", "tranquil", "serene")) {
            result.put("relaxed", 0.8f);
        }
        
        if (containsAny(lowerText, "focused", "concentrate", "study", "work", "productive", "attention")) {
            result.put("focused", 0.8f);
        }
        
        if (containsAny(lowerText, "energetic", "active", "workout", "energy", "pumped", "alive")) {
            result.put("energetic", 0.8f);
        }
        
        if (containsAny(lowerText, "love", "romantic", "passion", "heart", "affection", "intimate")) {
            result.put("romantic", 0.8f);
        }
        
        if (containsAny(lowerText, "anxious", "worried", "nervous", "stress", "tense", "uneasy")) {
            result.put("anxious", 0.8f);
        }
        
        return result;
    }
    
    /**
     * Check if text contains any of the specified keywords
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get default emotion map with low values
     */
    private Map<String, Float> getDefaultEmotionMap() {
        Map<String, Float> defaultMap = new HashMap<>();
        for (String emotion : EMOTION_LABELS) {
            defaultMap.put(emotion, 0.1f);
        }
        defaultMap.put("neutral", 0.5f);
        return defaultMap;
    }
    
    /**
     * Interface for async emotion analysis callback
     */
    public interface EmotionAnalysisCallback {
        void onEmotionAnalyzed(Map<String, Float> emotions);
    }
    
    /**
     * Clean up resources when no longer needed
     */
    public void close() {
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
            tfliteInterpreter = null;
        }
        modelInitialized = false;
    }
} 