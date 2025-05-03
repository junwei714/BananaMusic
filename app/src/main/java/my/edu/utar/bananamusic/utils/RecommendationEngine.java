package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

import my.edu.utar.bananamusic.ml.MoodClassifier;
import my.edu.utar.bananamusic.models.Artist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.TrackHistory;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.utils.DeezerHelper;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapters;

/**
 * Engine responsible for providing personalized music recommendations
 * based on user listening history, genre preferences, and track similarity.
 * Now supports emotion-based recommendations using TensorFlow Lite.
 */
public class RecommendationEngine {
    private static final String TAG = "RecommendationEngine";
    
    private static RecommendationEngine instance;
    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService executorService;
    private final FirebaseFirestore db;
    private final Map<String, List<Track>> genreCache;
    private final Map<String, List<Track>> artistCache;
    private final Map<String, List<Track>> emotionCache;
    
    // TensorFlow Lite interpreter for sentiment analysis
    private Interpreter tfLiteInterpreter;
    private static final String MODEL_PATH = "mood_classifier.tflite";
    private static final int SENTENCE_LEN = 256; // Max length of input text
    
    // Helper classes for API and mood classification
    private TensorFlowHelper tensorFlowHelper;
    private SpotifyHelper spotifyHelper;
    private ApiDataProvider apiDataProvider;
    private final DeezerHelper deezerHelper;
    
    // Emotion to music attribute mapping
    private static final Map<String, Map<String, Float>> EMOTION_ATTRIBUTES = new HashMap<>();
    
    // Constants for preference keys
    private static final String PREF_FILE = "recommendation_prefs";
    private static final String KEY_GENRES = "listened_genres";
    private static final String KEY_ARTISTS = "listened_artists";
    private static final String KEY_TOTAL_LISTENS = "total_listen_count";
    
    // Cache of current artist and genre preferences
    private Map<String, Integer> genreScores = new HashMap<>();
    private Map<String, Integer> artistScores = new HashMap<>();
    private int totalListens = 0;
    
    private List<RecommendationListener> listeners = new ArrayList<>();
    
    /**
     * Helper class for JSON parsing maps
     */
    private static class JsonParser {
        public static <T> Map<String, T> mapFromJson(String json, Class<T> valueClass) {
            try {
                JSONObject jsonObject = new JSONObject(json);
                Map<String, T> map = new HashMap<>();
                
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (valueClass == Integer.class) {
                        map.put(key, (T) Integer.valueOf(jsonObject.getInt(key)));
                    } else if (valueClass == String.class) {
                        map.put(key, (T) jsonObject.getString(key));
                    }
                }
                
                return map;
            } catch (Exception e) {
                Log.e(TAG, "Error parsing JSON: " + e.getMessage());
                return new HashMap<>();
            }
        }
        
        public static <T> String mapToJson(Map<String, T> map) {
            try {
                JSONObject jsonObject = new JSONObject();
                
                for (Map.Entry<String, T> entry : map.entrySet()) {
                    jsonObject.put(entry.getKey(), entry.getValue());
                }
                
                return jsonObject.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error creating JSON: " + e.getMessage());
                return "{}";
            }
        }
    }
    
    /**
     * Interface for components to be notified of recommendation updates
     */
    public interface RecommendationListener {
        void onRecommendationsUpdated();
    }
    
    /**
     * Callback interface for recommendation results
     */
    public interface RecommendationCallback {
        void onSuccess(List<Track> recommendedTracks);
        void onError(String errorMessage);
    }
    
    static {
        // Initialize emotion to music attribute mappings
        Map<String, Float> happyAttributes = new HashMap<>();
        happyAttributes.put("tempo", 120.0f); // Higher tempo
        happyAttributes.put("energy", 0.8f);  // High energy
        happyAttributes.put("valence", 0.9f); // High positivity
        happyAttributes.put("danceability", 0.7f);
        EMOTION_ATTRIBUTES.put("happy", happyAttributes);
        
        Map<String, Float> sadAttributes = new HashMap<>();
        sadAttributes.put("tempo", 80.0f);    // Lower tempo
        sadAttributes.put("energy", 0.4f);    // Lower energy
        sadAttributes.put("valence", 0.2f);   // Low positivity
        sadAttributes.put("danceability", 0.3f);
        EMOTION_ATTRIBUTES.put("sad", sadAttributes);
        
        Map<String, Float> relaxedAttributes = new HashMap<>();
        relaxedAttributes.put("tempo", 85.0f);   // Moderate-low tempo
        relaxedAttributes.put("energy", 0.3f);   // Low energy
        relaxedAttributes.put("valence", 0.6f);  // Moderate positivity
        relaxedAttributes.put("danceability", 0.4f);
        EMOTION_ATTRIBUTES.put("relaxed", relaxedAttributes);
        
        Map<String, Float> energeticAttributes = new HashMap<>();
        energeticAttributes.put("tempo", 130.0f);  // High tempo
        energeticAttributes.put("energy", 0.9f);   // Very high energy
        energeticAttributes.put("valence", 0.7f);  // Moderately high positivity
        energeticAttributes.put("danceability", 0.8f);
        EMOTION_ATTRIBUTES.put("energetic", energeticAttributes);
        
        Map<String, Float> focusedAttributes = new HashMap<>();
        focusedAttributes.put("tempo", 100.0f);    // Moderate tempo
        focusedAttributes.put("energy", 0.5f);     // Moderate energy
        focusedAttributes.put("valence", 0.5f);    // Neutral positivity
        focusedAttributes.put("danceability", 0.3f);
        EMOTION_ATTRIBUTES.put("focused", focusedAttributes);
        
        Map<String, Float> romanticAttributes = new HashMap<>();
        romanticAttributes.put("tempo", 90.0f);     // Moderate-slow tempo
        romanticAttributes.put("energy", 0.6f);     // Moderate energy
        romanticAttributes.put("valence", 0.7f);    // Moderate-high positivity
        romanticAttributes.put("danceability", 0.5f);
        EMOTION_ATTRIBUTES.put("romantic", romanticAttributes);
    }
    
    private RecommendationEngine(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        this.executorService = Executors.newSingleThreadExecutor();
        this.db = FirebaseFirestore.getInstance();
        this.genreCache = new HashMap<>();
        this.artistCache = new HashMap<>();
        this.emotionCache = new HashMap<>();
        
        // Initialize Spotify helper
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        
        // Initialize Deezer helper
        this.deezerHelper = DeezerHelper.getInstance(context);
        
        // Load existing preferences
        loadPreferences();
        
        // Initialize TensorFlow helper for mood detection
        try {
            tensorFlowHelper = TensorFlowHelper.getInstance(context);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TensorFlow helper: " + e.getMessage());
        }
    }
    
    public static synchronized RecommendationEngine getInstance(Context context) {
        if (instance == null) {
            instance = new RecommendationEngine(context);
        }
        return instance;
    }
    
    /**
     * Load TensorFlow Lite model from assets
     */
    private MappedByteBuffer loadModelFile() throws Exception {
        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            
            // Check if file is too small (likely not a valid model)
            if (declaredLength < 1024) {
                Log.e(TAG, "Model file is too small to be a valid TensorFlow Lite model");
                throw new IllegalArgumentException("Invalid TensorFlow model file size");
            }
            
            // Check if the file appears to be text (not binary)
            byte[] header = new byte[Math.min(100, (int)declaredLength)];
            inputStream.read(header);
            String headerString = new String(header).trim().toLowerCase();
            boolean isTextFile = headerString.contains("placeholder") || 
                                headerString.contains("this is") || 
                                !containsBinaryData(header);
            
            if (isTextFile) {
                Log.e(TAG, "Model file appears to be a text placeholder, not a valid TensorFlow model");
                throw new IllegalArgumentException("Model file is a text placeholder");
            }
            
            // Reset stream position
            inputStream.getChannel().position(0);
            
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            inputStream.close();
            fileDescriptor.close();
            return buffer;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load TensorFlow model: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Check if the data contains binary (non-text) content
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
     * Register a listener for recommendation updates
     */
    public void addListener(RecommendationListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a registered listener
     */
    public void removeListener(RecommendationListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Load preferences from SharedPreferences
     */
    private void loadPreferences() {
        // Load genre preferences
        String genreJson = preferences.getString(KEY_GENRES, "{}");
        try {
            genreScores = JsonParser.mapFromJson(genreJson, Integer.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing genre preferences: " + e.getMessage());
            genreScores = new HashMap<>();
        }
        
        // Load artist preferences
        String artistJson = preferences.getString(KEY_ARTISTS, "{}");
        try {
            artistScores = JsonParser.mapFromJson(artistJson, Integer.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing artist preferences: " + e.getMessage());
            artistScores = new HashMap<>();
        }
        
        // Load total listen count
        totalListens = preferences.getInt(KEY_TOTAL_LISTENS, 0);
    }
    
    /**
     * Save preferences to SharedPreferences
     */
    private void savePreferences() {
        try {
            String genreJson = JsonParser.mapToJson(genreScores);
            String artistJson = JsonParser.mapToJson(artistScores);
            
            preferences.edit()
                .putString(KEY_GENRES, genreJson)
                .putString(KEY_ARTISTS, artistJson)
                .putInt(KEY_TOTAL_LISTENS, totalListens)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving preferences: " + e.getMessage());
        }
    }
    
    /**
     * Track a song that the user has listened to
     * @param track The track that was played
     */
    public void trackListenedSong(Track track) {
        executorService.execute(() -> {
            // Update genre scores
            if (track.getGenres() != null && !track.getGenres().isEmpty()) {
                for (String genre : track.getGenres()) {
                    if (genre != null && !genre.isEmpty()) {
                        int currentScore = genreScores.getOrDefault(genre, 0);
                        genreScores.put(genre, currentScore + 1);
                    }
                }
            }
            
            // Update artist scores
            if (track.getArtist() != null && !track.getArtist().isEmpty()) {
            String artist = track.getArtist();
                int currentScore = artistScores.getOrDefault(artist, 0);
                artistScores.put(artist, currentScore + 1);
            }
            
            // Update total listen count
            totalListens++;
            
            // Save updated preferences
            savePreferences();
            
            // Notify listeners
            notifyListeners();
        });
    }
    
    /**
     * Notify all registered listeners of updates
     */
    private void notifyListeners() {
        for (RecommendationListener listener : listeners) {
            listener.onRecommendationsUpdated();
        }
    }
    
    /**
     * Get a sorted list of top items (genres or artists)
     */
    private List<String> getTopItems(Map<String, Integer> scores, int limit) {
        // Sort by score (descending)
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(scores.entrySet());
        sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        // Extract just the names
        List<String> topItems = new ArrayList<>();
        for (int i = 0; i < Math.min(sortedEntries.size(), limit); i++) {
            topItems.add(sortedEntries.get(i).getKey());
        }
        
        return topItems;
    }
    
    /**
     * Get personalized recommendations based on user's listening history
     * @param limit Maximum number of tracks to recommend
     * @param callback Callback to receive results
     */
    public void getPersonalizedRecommendations(int limit, RecommendationCallback callback) {
        if (spotifyHelper == null) {
            spotifyHelper = SpotifyHelper.getInstance(context);
        }

        // Get personalized recommendations from Spotify
        spotifyHelper.getPersonalizedRecommendations(null, limit, null, null, null, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError("Failed to get personalized recommendations: " + message);
            }
        });
    }
    
    /**
     * Get recommended tracks (with fallback mechanism)
     * @param callback Callback to notify when tracks are loaded
     */
    public void getRecommendedTracks(TracksCallback callback) {
        executorService.execute(() -> {
            if (spotifyHelper == null) {
                spotifyHelper = SpotifyHelper.getInstance(context);
            }

            spotifyHelper.getRecommendedTracks("all", 10, new SpotifyHelper.SpotifyRecommendationsCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    callback.onSuccess(tracks);
                }

                @Override
                public void onError(String message) {
                    callback.onError("Failed to get recommendations: " + message);
                }
            });
        });
    }
    
    /**
     * Get emotion-based recommendations
     * @param emotion User's emotional state (e.g., "happy", "sad", "relaxed")
     * @param limit Maximum number of tracks to return
     * @param callback Callback to receive recommendation results
     */
    public void getEmotionBasedRecommendations(String emotion, int limit, RecommendationCallback callback) {
        if (spotifyHelper == null) {
            spotifyHelper = SpotifyHelper.getInstance(context);
        }

        String mood = mapEmotionToApiMood(emotion);
        spotifyHelper.getMoodBasedRecommendations(mood, limit, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError("Failed to get emotion-based recommendations: " + message);
            }
        });
    }
    
    /**
     * Get recommended tracks (with fallback mechanism)
     * @param query Query to search for tracks
     * @param limit Maximum number of tracks to return
     * @param callback Callback to notify when tracks are loaded
     */
    public void getRecommendedTracks(String query, int limit, TracksCallback callback) {
        // Try getting tracks from API first
        apiDataProvider.getRecommendedTracks(query, limit, adaptToApiCallback(callback));
    }
    
    /**
     * Get varied recommendations
     */
    public void getVariedRecommendations(String query, int limit, final TracksCallback callback) {
        if (spotifyHelper == null) {
            spotifyHelper = SpotifyHelper.getInstance(context);
        }

        spotifyHelper.getVariedRecommendations(query, limit, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError("Failed to get varied recommendations: " + message);
            }
        });
    }
    
    /**
     * Get mood-based recommendations
     * This method should handle missing TF model and support "all" mood parameter
     */
    public void getMoodBasedRecommendations(String mood, int limit, RecommendationCallback callback) {
        if (spotifyHelper == null) {
            spotifyHelper = SpotifyHelper.getInstance(context);
        }

        // Get recommendations directly from Spotify
        spotifyHelper.getMoodBasedRecommendations(mood, limit, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                // Add mood-specific recommendation reasons
                for (Track track : tracks) {
                    if (track.getRecommendationReason() == null || track.getRecommendationReason().isEmpty()) {
                        track.setRecommendationReason(getMoodBasedReason(mood, track));
                    }
                }
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting mood recommendations: " + message);
                callback.onError("Failed to get recommendations: " + message);
            }
        });
    }

    private String getMoodBasedReason(String mood, Track track) {
        String[] reasons;
        switch (mood.toLowerCase()) {
            case "happy":
                reasons = new String[]{
                    "Perfect for your upbeat mood",
                    "This matches your happy vibes",
                    "Keep the good mood going",
                    "Great for happy moments"
                };
                break;
            case "sad":
                reasons = new String[]{
                    "When you need to feel understood",
                    "Music for reflection",
                    "Perfect for emotional moments",
                    "Let the music comfort you"
                };
                break;
            case "energetic":
                reasons = new String[]{
                    "Keep your energy high",
                    "Perfect for an energetic mood",
                    "Boost your energy levels",
                    "Keep the momentum going"
                };
                break;
            case "relaxed":
                reasons = new String[]{
                    "Help you unwind",
                    "Perfect for relaxation",
                    "Calm your mind",
                    "Create a peaceful atmosphere"
                };
                break;
            default:
                reasons = new String[]{
                    "Recommended for your current mood",
                    "Matches your current vibe",
                    "Selected just for you",
                    "Based on your mood"
                };
        }
        return reasons[new Random().nextInt(reasons.length)];
    }

    /**
     * Get emotion-based recommendations using genre matching as a fallback
     */
    public void getEmotionGenreRecommendations(String emotion, int limit, RecommendationCallback callback) {
        Log.d(TAG, "Using genre-based emotion fallback for: " + emotion);
        
        // Map emotion to genre
        List<String> genres = getEmotionGenres(emotion);
        
        if (genres.isEmpty()) {
            callback.onError("Could not find genres for emotion: " + emotion);
            return;
        }
        
        // Build a query using OR for multiple genres
        StringBuilder queryBuilder = new StringBuilder();
        for (int i = 0; i < genres.size(); i++) {
            if (i > 0) {
                queryBuilder.append(" OR ");
            }
            queryBuilder.append("genre:\"").append(genres.get(i)).append("\"");
        }
        
        final String query = queryBuilder.toString();
        
        // Use varied recommendations for better diversity
        apiDataProvider.getVariedRecommendations(query, limit, CallbackAdapters.toApiProviderCallback(new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    List<Track> recommendedTracks = filterRecommendationsByMood(tracks, emotion, limit);
                    callback.onSuccess(recommendedTracks);
                } else {
                    callback.onError("No recommendations found for: " + emotion);
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting varied recommendations: " + message);
                callback.onError(message);
            }
            
            @Override
            public void onTracksLoaded(List<Track> tracks) {
                onSuccess(tracks);
            }
        }));
    }
    
    /**
     * Filter tracks by emotion attributes
     */
    private List<Track> filterTracksByEmotionAttributes(List<Track> tracks, String emotion, int limit) {
        // Get target attributes for the emotion
        Map<String, Float> targetAttributes = EMOTION_ATTRIBUTES.get(emotion.toLowerCase());
        if (targetAttributes == null) {
            // No specific attributes for this emotion, return tracks as is
            return new ArrayList<>(tracks.subList(0, Math.min(tracks.size(), limit)));
        }
        
        // Calculate scores for each track based on how well they match the emotion attributes
        Map<Track, Float> trackScores = new HashMap<>();
        
        for (Track track : tracks) {
            float score = calculateEmotionMatchScore(track, targetAttributes);
            trackScores.put(track, score);
        }
        
        // Sort tracks by score (descending)
        List<Map.Entry<Track, Float>> sortedTracks = new ArrayList<>(trackScores.entrySet());
        sortedTracks.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        // Extract just the tracks
        List<Track> filteredTracks = new ArrayList<>();
        for (int i = 0; i < Math.min(sortedTracks.size(), limit); i++) {
            filteredTracks.add(sortedTracks.get(i).getKey());
        }
        
        return filteredTracks;
    }
    
    /**
     * Calculate how well a track matches emotion attributes
     */
    private float calculateEmotionMatchScore(Track track, Map<String, Float> targetAttributes) {
        // Use track attributes to calculate a match score
        // This is a simplified algorithm - more sophisticated matching could be used
        float score = 1.0f;
        
        // For now, use a simplified approach that doesn't rely on audioFeatures
        // Check if genre matches emotion
        List<String> trackGenres = track.getGenres();
        if (trackGenres != null && !trackGenres.isEmpty()) {
            List<String> emotionGenres = getEmotionGenres(mapApiMoodToEmotion(trackGenres.get(0)));
            for (String genre : trackGenres) {
                if (emotionGenres.contains(genre)) {
                    score *= 1.5f; // Boost score for genre match
                    break;
                }
            }
        }
        
        // Use tempo as a proxy for mood if available
        if (track.getTempo() > 0 && targetAttributes.containsKey("tempo")) {
            float tempoDiff = Math.abs(track.getTempo() - targetAttributes.get("tempo"));
            float tempoScore = Math.max(0, 1 - (tempoDiff / 120f)); // Normalize
            score *= (0.8f + (0.2f * tempoScore));
        }
        
        return score;
    }
    
    /**
     * Get genres associated with an emotion
     */
    private List<String> getEmotionGenres(String emotion) {
        List<String> genres = new ArrayList<>();
        
        switch (emotion.toLowerCase()) {
            case "happy":
                genres.add("pop");
                genres.add("dance");
                genres.add("electronic");
                genres.add("disco");
                break;
            case "sad":
                genres.add("blues");
                genres.add("soul");
                genres.add("folk");
                genres.add("indie");
                break;
            case "relaxed":
                genres.add("ambient");
                genres.add("chillout");
                genres.add("acoustic");
                genres.add("classical");
                break;
            case "energetic":
                genres.add("rock");
                genres.add("metal");
                genres.add("punk");
                genres.add("edm");
                break;
            case "focused":
                genres.add("classical");
                genres.add("instrumental");
                genres.add("jazz");
                genres.add("study");
                break;
            case "romantic":
                genres.add("r&b");
                genres.add("jazz");
                genres.add("soul");
                genres.add("acoustic");
                break;
            default:
                genres.add("pop");
                break;
        }
        
        return genres;
    }
    
    /**
     * Map API mood to emotion
     */
    private String mapApiMoodToEmotion(String apiMood) {
        if (apiMood == null) {
            return "happy"; // Default
        }
        
        apiMood = apiMood.toLowerCase();
        
        if (apiMood.contains("happy") || apiMood.contains("joy")) {
            return "happy";
        } else if (apiMood.contains("sad") || apiMood.contains("melancholy")) {
            return "sad";
        } else if (apiMood.contains("relax") || apiMood.contains("calm") || apiMood.contains("chill")) {
            return "relaxed";
        } else if (apiMood.contains("energy") || apiMood.contains("power") || apiMood.contains("workout")) {
            return "energetic";
        } else if (apiMood.contains("focus") || apiMood.contains("study") || apiMood.contains("concentrate")) {
            return "focused";
        } else if (apiMood.contains("love") || apiMood.contains("romance")) {
            return "romantic";
        }
        
        return "happy"; // Default
    }
    
    /**
     * Map emotion to a mood suitable for the API
     */
    private String mapEmotionToApiMood(String emotion) {
        switch (emotion.toLowerCase()) {
            case "happy":
                return "happy";
            case "sad":
                return "sad";
            case "angry":
                return "energetic";
            case "relaxed":
                return "relaxed";
            case "focused":
                return "focused";
            case "romantic":
                return "romantic";
            default:
                return "all";
        }
    }
    
    /**
     * Detect emotion from user input text
     * @param text User input text
     * @return Detected emotion
     */
    public String detectEmotion(String text) {
        // Use TensorFlowHelper for emotion detection
        if (tensorFlowHelper != null) {
            return tensorFlowHelper.detectMood(text);
        }
        
        // Fallback to simple analysis if TensorFlowHelper is not available
        return predictEmotion(text);
    }
    
    /**
     * Detect emotion from user input asynchronously
     * @param text User input text
     * @param callback Callback for the detected emotion
     */
    public void detectEmotionAsync(String text, TensorFlowHelper.OnMoodDetectedListener callback) {
        if (tensorFlowHelper != null) {
            tensorFlowHelper.detectMoodAsync(text, callback);
        } else {
            // Fallback with simple detection
            String emotion = predictEmotion(text);
            callback.onMoodDetected(emotion);
        }
    }
    
    /**
     * Analyze emotion using TensorFlow Lite
     * @param text Text to analyze
     * @return Map of emotion scores
     */
    public Map<String, Float> analyzeEmotion(String text) {
        try {
            if (tfLiteInterpreter == null) {
                throw new Exception("TensorFlow interpreter not initialized");
            }
            
            // Tokenize text into model input
            float[][] input = tokenizeText(text);
            
            // Create output buffer
            float[][] output = new float[1][6]; // 6 emotion categories
            
            // Run inference
            tfLiteInterpreter.run(input, output);
            
            // Map the output to emotions
            Map<String, Float> emotionScores = new HashMap<>();
            String[] emotions = {"angry", "happy", "neutral", "sad", "relaxed", "focused"};
            
            for (int i = 0; i < emotions.length; i++) {
                emotionScores.put(emotions[i], output[0][i]);
            }
            
            return emotionScores;
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing emotion: " + e.getMessage());
            Map<String, Float> defaultResult = new HashMap<>();
            defaultResult.put("neutral", 1.0f);
            return defaultResult;
        }
    }
    
    /**
     * Convert text to input features for the TensorFlow model
     */
    private float[][] tokenizeText(String text) {
        // Simple tokenization - in a real implementation, this would use a proper tokenizer
        float[][] tokenized = new float[1][SENTENCE_LEN];
        
        // Clear the input
        for (int i = 0; i < SENTENCE_LEN; i++) {
            tokenized[0][i] = 0.0f;
        }
        
        // Convert text to lowercase
        String processedText = text.toLowerCase().trim();
        
        // Very simple character-level encoding
        for (int i = 0; i < Math.min(processedText.length(), SENTENCE_LEN); i++) {
            tokenized[0][i] = (float) processedText.charAt(i) / 256.0f; // Normalize to [0,1]
        }
        
        return tokenized;
    }
    
    /**
     * Map raw user input to a supported emotion
     * @param userInput Text input from user
     * @return Predicted emotion
     */
    public String predictEmotion(String userInput) {
        // Use TensorFlow model to predict emotion
        Map<String, Float> emotionScores = analyzeEmotion(userInput);
        
        // Find the emotion with the highest score
        String topEmotion = "neutral";
        float maxScore = 0.0f;
        
        for (Map.Entry<String, Float> entry : emotionScores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                topEmotion = entry.getKey();
            }
        }
        
        return topEmotion;
    }
    
    /**
     * Clear cached recommendations for a specific emotion
     * @param emotion Emotion to clear cache for
     */
    public void clearEmotionCache(String emotion) {
        if (emotion != null && emotionCache.containsKey(emotion)) {
            emotionCache.remove(emotion);
        }
    }
    
    /**
     * Clear all cached recommendations
     */
    public void clearAllCaches() {
        emotionCache.clear();
        genreCache.clear();
        artistCache.clear();
    }
    
    /**
     * Filter recommendations based on mood and limit
     */
    private List<Track> filterRecommendationsByMood(List<Track> tracks, String emotion, int limit) {
        if (tracks == null || tracks.isEmpty()) {
            return new ArrayList<>();
        }

        // Get target audio characteristics for this emotion
        Map<String, Float> targetAttributes = EMOTION_ATTRIBUTES.get(emotion.toLowerCase());
        if (targetAttributes == null) {
            return new ArrayList<>(tracks.subList(0, Math.min(tracks.size(), limit)));
        }

        // Score each track based on how well it matches the emotion
        List<TrackMatch> scoredTracks = new ArrayList<>();
        for (Track track : tracks) {
            double score = calculateEmotionMatchScore(track, targetAttributes);
            scoredTracks.add(new TrackMatch(track, score));
        }

        // Sort by score descending
        Collections.sort(scoredTracks, (a, b) -> Double.compare(b.score, a.score));

        // Take top N tracks
        List<Track> filteredTracks = new ArrayList<>();
        for (int i = 0; i < Math.min(scoredTracks.size(), limit); i++) {
            filteredTracks.add(scoredTracks.get(i).track);
        }

        return filteredTracks;
    }
    
    /**
     * Helper class to store track with match score
     */
    private static class TrackMatch {
        Track track;
        double score;
        
        TrackMatch(Track track, double score) {
            this.track = track;
            this.score = score;
        }
    }

    /**
     * Helper method to convert TracksCallback to ApiDataProvider.TracksCallback
     */
    private ApiDataProvider.TracksCallback adaptToApiCallback(final TracksCallback callback) {
        return new ApiDataProvider.TracksCallback() {
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
     * Helper method to convert RecommendationCallback to TracksCallback
     */
    private TracksCallback adaptFromRecommendationCallback(final RecommendationCallback callback) {
        return new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }

            @Override
            public void onTracksLoaded(List<Track> tracks) {
                onSuccess(tracks);
            }
        };
    }

    /**
     * Get featured content with recommendations and new releases
     */
    public void getFeaturedContent(final ApiDataProvider.FeaturedContentCallback callback) {
        apiDataProvider.getFeaturedContent(new ApiDataProvider.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                // Since we only get tracks from getFeaturedContent now, we'll pass them as recommended tracks
                callback.onSuccess(tracks, new ArrayList<>());
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void getRecommendations(String type, int limit, final TracksCallback callback) {
        apiDataProvider.getVariedRecommendations(type, limit, CallbackAdapters.toApiProviderCallback(callback));
    }
} 