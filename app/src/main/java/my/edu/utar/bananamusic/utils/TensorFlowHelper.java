package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.AsyncTask;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import my.edu.utar.bananamusic.models.Track;

/**
 * Helper class for mood-based music recommendations using TensorFlow Lite
 */
public class TensorFlowHelper {
    private static final String TAG = "TensorFlowHelper";
    private static final String MODEL_FILE = "mood_classifier.tflite";
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int EMBEDDING_SIZE = 384; // Typical BERT embedding size
    
    // Set this to false to disable TensorFlow and always use keyword-based detection
    private static final boolean USE_TENSORFLOW = true;
    
    // Mood classification thresholds
    private static final float MOOD_THRESHOLD = 0.5f;
    
    private static TensorFlowHelper instance;
    private Interpreter tflite;
    private boolean isInitialized = false;
    private Context context;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private AtomicBoolean isModelLoading = new AtomicBoolean(false);
    
    // Cache for mood detection results to avoid redundant processing
    private final Map<String, String> moodCache = new HashMap<>();
    
    // Map to store track IDs by mood
    private Map<String, List<String>> moodToTrackMap = new HashMap<>();
    
    // Mood sentiment ranges
    private static final Map<String, Float[]> MOOD_SENTIMENT_RANGES = new HashMap<>();
    static {
        MOOD_SENTIMENT_RANGES.put("Happy", new Float[]{0.6f, 1.0f});
        MOOD_SENTIMENT_RANGES.put("Sad", new Float[]{-1.0f, -0.3f});
        MOOD_SENTIMENT_RANGES.put("Energetic", new Float[]{0.3f, 0.9f});
        MOOD_SENTIMENT_RANGES.put("Relaxed", new Float[]{-0.2f, 0.4f});
        MOOD_SENTIMENT_RANGES.put("Romantic", new Float[]{0.1f, 0.7f});
        MOOD_SENTIMENT_RANGES.put("Focused", new Float[]{-0.1f, 0.5f});
    }
    
    // Vocabulary for keyword-based fallback
    private static final Map<String, String> MOOD_KEYWORDS = new HashMap<>();
    static {
        MOOD_KEYWORDS.put("happy|joy|wonderful|great|excited|upbeat|cheerful|celebrate", "Happy");
        MOOD_KEYWORDS.put("sad|depressed|down|blue|gloomy|heartbroken|melancholy", "Sad");
        MOOD_KEYWORDS.put("energy|energetic|workout|run|powerful|strong|dynamic|upbeat", "Energetic");
        MOOD_KEYWORDS.put("relax|calm|peace|chill|tranquil|soothing|gentle|quiet", "Relaxed");
        MOOD_KEYWORDS.put("love|romantic|passion|intimate|sensual|affection", "Romantic");
        MOOD_KEYWORDS.put("focus|study|concentrate|work|productivity|attention", "Focused");
    }
    
    /**
     * Private constructor - use getInstance()
     * @param context Application context
     */
    private TensorFlowHelper(Context context) {
        this.context = context.getApplicationContext();
        try {
            initializeMoodData();
            
            // Check if model exists before attempting to load it
            boolean modelExists = modelFileExists();
            
            if (modelExists && USE_TENSORFLOW) {
                Log.d(TAG, "TensorFlow model file exists, will attempt initialization");
                // Defer TensorFlow initialization to background thread
                executor.execute(this::initializeTensorFlow);
            } else {
                if (!modelExists) {
                    Log.w(TAG, "TensorFlow model file not found in assets, will use keyword-based detection only");
                } else {
                    Log.d(TAG, "TensorFlow usage is disabled, will use keyword-based detection only");
                }
                isInitialized = false;
            }
            Log.d(TAG, "TensorFlow Helper initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TensorFlow Helper", e);
            isInitialized = false;
        }
    }
    
    /**
     * Get singleton instance
     * @param context Application context
     * @return TensorFlowHelper instance
     */
    public static synchronized TensorFlowHelper getInstance(Context context) {
        if (instance == null) {
            instance = new TensorFlowHelper(context);
        }
        return instance;
    }
    
    /**
     * Initialize TensorFlow Lite model
     */
    private void initializeTensorFlow() {
        // If already loading, don't try again
        if (isModelLoading.getAndSet(true)) {
            return;
        }
        
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_FILE);
            if (modelBuffer != null) {
                try {
                    Interpreter.Options options = new Interpreter.Options();
                    options.setNumThreads(2);
                    tflite = new Interpreter(modelBuffer, options);
                    isInitialized = true;
                    Log.d(TAG, "TensorFlow Lite model loaded successfully");
                } catch (IllegalArgumentException e) {
                    // This catches cases where the buffer isn't a valid TFLite model
                    Log.e(TAG, "Invalid TensorFlow Lite model format", e);
                    isInitialized = false;
                    tflite = null;
                }
            } else {
                Log.e(TAG, "Failed to load model buffer, using fallback detection");
                isInitialized = false;
                tflite = null;
            }
        } catch (Exception e) {
            // Handle invalid model file
            Log.e(TAG, "Error loading TensorFlow Lite model, using fallback detection", e);
            isInitialized = false;
            tflite = null;
        } finally {
            isModelLoading.set(false);
        }
    }
    
    /**
     * Check if model file exists in assets
     * @return true if file exists
     */
    private boolean modelFileExists() {
        try {
            String[] files = context.getAssets().list("");
            if (files != null) {
                for (String file : files) {
                    if (MODEL_FILE.equals(file)) {
                        Log.d(TAG, "Found model file: " + file);
                        
                        // Also check if it's a valid file (not empty)
                        try (AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE)) {
                            if (fileDescriptor.getLength() <= 0) {
                                Log.w(TAG, "Model file exists but is empty");
                                return false;
                            }
                            return true;
                        } catch (IOException e) {
                            Log.w(TAG, "Error checking model file size: " + e.getMessage());
                            return false;
                        }
                    }
                }
            }
            Log.w(TAG, "Model file not found in assets");
        } catch (IOException e) {
            Log.e(TAG, "Error checking assets directory", e);
        }
        return false;
    }
    
    /**
     * Initialize mood-to-track mapping data
     */
    private void initializeMoodData() {
        // In a real implementation, this data would come from analyzing tracks
        // For our prototype, we'll use our dummy data to create mood maps
        try {
            DummyDataProvider dataProvider = new DummyDataProvider(context);
            List<Track> tracks = dataProvider.getRecentTracks();
            
            // Initialize mood lists
            for (String mood : dataProvider.getMoods()) {
                moodToTrackMap.put(mood, new ArrayList<>());
            }
            
            // For demonstration, assign tracks to moods based on their characteristics
            // In a real app, this would use audio analysis or metadata
            if (tracks.size() > 0) moodToTrackMap.get("Happy").add(tracks.get(0).getTrackId()); // Shape of You
            if (tracks.size() > 2) moodToTrackMap.get("Happy").add(tracks.get(2).getTrackId()); // Watermelon Sugar
            
            if (tracks.size() > 1) moodToTrackMap.get("Energetic").add(tracks.get(1).getTrackId()); // Blinding Lights
            if (tracks.size() > 4) moodToTrackMap.get("Energetic").add(tracks.get(4).getTrackId()); // Don't Start Now
            
            if (tracks.size() > 3) moodToTrackMap.get("Sad").add(tracks.get(3).getTrackId()); // Bad Guy
            
            Log.d(TAG, "Mood data initialized with " + moodToTrackMap.size() + " moods");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing mood data", e);
            // Initialize with empty data as fallback
            moodToTrackMap.put("Happy", new ArrayList<>());
            moodToTrackMap.put("Sad", new ArrayList<>());
            moodToTrackMap.put("Energetic", new ArrayList<>());
            moodToTrackMap.put("Relaxed", new ArrayList<>());
        }
    }
    
    /**
     * Load TensorFlow Lite model file from assets
     */
    private MappedByteBuffer loadModelFile(Context context, String modelFile) throws IOException {
        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFile);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            
            // Validate file contents before mapping
            if (declaredLength <= 0) {
                Log.e(TAG, "Model file has invalid length: " + declaredLength);
                return null;
            }
            
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            
            // Additional validation
            if (buffer.capacity() <= 0) {
                Log.e(TAG, "Created a buffer with 0 capacity");
                return null;
            }
            
            // Check if the buffer contains binary data that looks like a TFLite model
            byte[] header = new byte[Math.min(16, buffer.capacity())];
            buffer.get(header);
            buffer.rewind(); // Reset position after reading
            
            if (!containsBinaryData(header)) {
                Log.e(TAG, "Model file does not appear to be a valid TFLite model");
                return null;
            }
            
            return buffer;
        } catch (IOException e) {
            Log.e(TAG, "Error loading model file: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Check if the data contains binary (non-text) content
     * @param data Byte array to check
     * @return true if contains binary data
     */
    private boolean containsBinaryData(byte[] data) {
        for (byte b : data) {
            // Check for common binary file markers or non-printable ASCII
            if ((b < 9 || (b > 13 && b < 32)) && b != 0) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Detect mood from user input text with caching
     * Uses TensorFlow Lite if available, falls back to keyword-based detection
     * @param inputText User input text
     * @return Detected mood
     */
    public String detectMood(String inputText) {
        if (inputText == null || inputText.trim().isEmpty()) {
            return "Happy"; // Default mood
        }
        
        inputText = inputText.toLowerCase().trim();
        
        // Check cache first
        if (moodCache.containsKey(inputText)) {
            return moodCache.get(inputText);
        }
        
        String detectedMood;
        
        // Try TensorFlow model first if enabled and available
        if (USE_TENSORFLOW && isInitialized && tflite != null) {
            try {
                detectedMood = detectMoodWithTensorFlow(inputText);
            } catch (Exception e) {
                Log.e(TAG, "Error using TensorFlow for mood detection, falling back to keywords", e);
                // Fall back to keyword matching
                detectedMood = detectMoodWithKeywords(inputText);
            }
        } else {
            // Fallback to advanced keyword matching
            Log.d(TAG, "Using keyword-based mood detection");
            detectedMood = detectMoodWithKeywords(inputText);
        }
        
        // Cache the result
        moodCache.put(inputText, detectedMood);
        
        return detectedMood;
    }
    
    /**
     * Detect mood asynchronously
     * @param inputText User input text
     * @param callback Callback for the detected mood
     */
    public void detectMoodAsync(String inputText, OnMoodDetectedListener callback) {
        if (inputText == null || inputText.trim().isEmpty()) {
            if (callback != null) {
                callback.onMoodDetected("Happy"); // Default mood
            }
            return;
        }
        
        final String cleanedInput = inputText.toLowerCase().trim();
        
        // Check cache first
        if (moodCache.containsKey(cleanedInput)) {
            if (callback != null) {
                callback.onMoodDetected(moodCache.get(cleanedInput));
            }
            return;
        }
        
        // Process on background thread
        executor.execute(() -> {
            String detectedMood;
            
            // Try TensorFlow model first if enabled and available
            if (USE_TENSORFLOW && isInitialized && tflite != null) {
                try {
                    detectedMood = detectMoodWithTensorFlow(cleanedInput);
                } catch (Exception e) {
                    Log.e(TAG, "Error using TensorFlow for mood detection in async call, falling back to keywords", e);
                    // Fall back to keyword matching
                    detectedMood = detectMoodWithKeywords(cleanedInput);
                }
            } else {
                // Fallback to advanced keyword matching
                Log.d(TAG, "Using keyword-based mood detection for async call");
                detectedMood = detectMoodWithKeywords(cleanedInput);
            }
            
            // Cache the result
            moodCache.put(cleanedInput, detectedMood);
            
            // Deliver result on main thread
            final String mood = detectedMood;
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onMoodDetected(mood);
                }
            });
        });
    }
    
    /**
     * Detect mood using TensorFlow Lite model
     * @param inputText Text to analyze
     * @return Detected mood
     */
    private String detectMoodWithTensorFlow(String inputText) {
        // Calculate sentiment score (simple approximation)
        float sentimentScore = calculateSentimentScore(inputText);
        
        // Match sentiment score to mood ranges
        for (Map.Entry<String, Float[]> entry : MOOD_SENTIMENT_RANGES.entrySet()) {
            Float[] range = entry.getValue();
            if (sentimentScore >= range[0] && sentimentScore <= range[1]) {
                return entry.getKey();
            }
        }
        
        // Default fallback
        return "Happy";
    }
    
    /**
     * Simple sentiment analysis as an approximation
     * @param text Text to analyze
     * @return Sentiment score from -1 (negative) to 1 (positive)
     */
    private float calculateSentimentScore(String text) {
        // This is a very simplified sentiment analyzer
        // Real systems would use ML models or more sophisticated algorithms
        
        // Lists of positive and negative words
        String[] positiveWords = {"happy", "joy", "love", "great", "excellent", "wonderful", 
                "amazing", "awesome", "good", "best", "beautiful", "like", "enjoy"};
        
        String[] negativeWords = {"sad", "bad", "hate", "terrible", "awful", "horrible", 
                "worst", "dislike", "disappointed", "angry", "upset", "depressed"};
        
        float score = 0;
        text = text.toLowerCase();
        
        // Count positive words
        for (String word : positiveWords) {
            if (text.contains(word)) {
                score += 0.2f;
            }
        }
        
        // Count negative words
        for (String word : negativeWords) {
            if (text.contains(word)) {
                score -= 0.2f;
            }
        }
        
        // Clamp to [-1, 1] range
        return Math.max(-1.0f, Math.min(1.0f, score));
    }
    
    /**
     * Detect mood using enhanced keyword matching
     * @param inputText Text to analyze
     * @return Detected mood
     */
    private String detectMoodWithKeywords(String inputText) {
        // Check each set of keywords against the input text
        for (Map.Entry<String, String> entry : MOOD_KEYWORDS.entrySet()) {
            String[] keywords = entry.getKey().split("\\|");
            for (String keyword : keywords) {
                if (inputText.contains(keyword)) {
                    return entry.getValue();
                }
            }
        }
        
        // Default to Happy if no match
        return "Happy";
    }
    
    /**
     * Get track IDs for a specific mood
     * @param mood Mood to get tracks for
     * @return List of track IDs
     */
    public List<String> getTrackIdsForMood(String mood) {
        if (moodToTrackMap.containsKey(mood)) {
            return new ArrayList<>(moodToTrackMap.get(mood));
        }
        
        // Default to empty list if mood not found
        return new ArrayList<>();
    }
    
    /**
     * Add a track to a specific mood category
     * @param mood Mood category
     * @param trackId Track ID to add
     */
    public void addTrackToMood(String mood, String trackId) {
        if (!moodToTrackMap.containsKey(mood)) {
            moodToTrackMap.put(mood, new ArrayList<>());
        }
        
        if (!moodToTrackMap.get(mood).contains(trackId)) {
            moodToTrackMap.get(mood).add(trackId);
            Log.d(TAG, "Added track " + trackId + " to mood " + mood);
        }
    }
    
    /**
     * Clear mood detection cache
     */
    public void clearCache() {
        moodCache.clear();
    }
    
    /**
     * Interface for mood detection callback
     */
    public interface OnMoodDetectedListener {
        void onMoodDetected(String mood);
    }
    
    /**
     * Close resources when no longer needed
     */
    public void close() {
        if (tflite != null) {
            try {
                tflite.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing TensorFlow interpreter", e);
            }
            tflite = null;
        }
        isInitialized = false;
        moodCache.clear();
        Log.d(TAG, "TensorFlow resources released");
    }
    
    /**
     * Detect mood from text input
     * @param inputText Text to analyze
     * @return Detected mood
     */
    public String detectMoodFromText(String inputText) {
        return detectMood(inputText);
    }
} 