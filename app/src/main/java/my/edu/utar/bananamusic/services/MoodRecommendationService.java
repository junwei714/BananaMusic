package my.edu.utar.bananamusic.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import my.edu.utar.bananamusic.ml.MoodClassifier;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.DummyDataProvider;
import my.edu.utar.bananamusic.utils.UIThreadHelper;
import my.edu.utar.bananamusic.utils.GenreUtils;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.utils.TrackManager;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;

/**
 * Service for providing mood-based music recommendations
 */
public class MoodRecommendationService {
    private static final String TAG = "MoodRecommendationSvc";
    
    // Singleton instance
    private static MoodRecommendationService instance;
    
    // Context reference
    private final Context context;
    
    // Dependencies
    private final MoodClassifier moodClassifier;
    private final ApiDataProvider apiDataProvider;
    private final DummyDataProvider dummyDataProvider;
    
    // Cache for audio features
    private final Map<String, Map<String, Float>> audioFeaturesCache = new HashMap<>();
    
    private final Executor executor;
    private final Handler mainHandler;
    private final Random random;
    
    // Map of mood categories to musical features
    private final List<String> happyGenres = Arrays.asList("pop", "dance", "electronic", "funk");
    private final List<String> sadGenres = Arrays.asList("blues", "soul", "jazz", "indie");
    private final List<String> energeticGenres = Arrays.asList("rock", "metal", "EDM", "trap");
    private final List<String> relaxedGenres = Arrays.asList("ambient", "chillout", "classical", "acoustic");
    private final List<String> romanticGenres = Arrays.asList("R&B", "soul", "jazz", "acoustic");
    private final List<String> focusedGenres = Arrays.asList("classical", "ambient", "instrumental", "lo-fi");
    
    private final TrackManager trackManager;
    
    // Callback interface for recommendation results
    public interface RecommendationCallback {
        void onRecommendationsReady(List<Track> recommendations);
        void onError(String errorMessage);
    }
    
    // Make constructor public for compatibility
    public MoodRecommendationService(Context context) {
        this.context = context.getApplicationContext();
        this.moodClassifier = MoodClassifier.getInstance(context);
        this.apiDataProvider = ApiDataProvider.getInstance(context);
        this.dummyDataProvider = new DummyDataProvider();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.random = new Random();
        this.trackManager = TrackManager.getInstance(context);
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized MoodRecommendationService getInstance(Context context) {
        if (instance == null) {
            instance = new MoodRecommendationService(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Get recommendations from text input describing mood
     * @param textInput User's text input
     * @param limit Maximum number of recommendations to return
     * @param callback Callback for recommendation results
     */
    public void getRecommendationsFromText(String textInput, int limit, RecommendationCallback callback) {
        // Detect mood from text
        String detectedMood = moodClassifier.predictMoodFromText(textInput);
        Log.d(TAG, "Detected mood from text: " + detectedMood);
        
        // Get recommendations for the detected mood
        getRecommendationsForMood(detectedMood, limit, callback);
    }
    
    /**
     * Get recommendations for a specific mood with a default limit
     * This method is added for compatibility with the updated UI flow
     * 
     * @param mood The target mood
     * @param callback Callback for recommendation results
     */
    public void getRecommendationsForMood(String mood, RecommendationCallback callback) {
        // Use a default limit of 10 tracks
        getRecommendationsForMood(mood, 10, callback);
    }
    
    /**
     * Get music recommendations for a specific mood with limit
     *
     * @param mood The mood to get recommendations for
     * @param limit Maximum number of tracks to return
     * @param callback Callback to return results
     */
    public void getRecommendationsForMood(String mood, int limit, RecommendationCallback callback) {
        // Get genre mappings for the mood
        List<String> moodGenres = GenreUtils.getMoodGenres(mood);
        
        List<Track> tracks = trackManager.getAllTracks();
        // Filter tracks by mood-appropriate genres
        List<Track> moodTracks = GenreUtils.filterByGenres(tracks, moodGenres);
        
        // Sort by relevance score
        Collections.sort(moodTracks, (t1, t2) -> {
            float score1 = calculateMoodRelevanceScore(t1, mood);
            float score2 = calculateMoodRelevanceScore(t2, mood);
            return Float.compare(score2, score1);
        });

        // Limit results
        if (moodTracks.size() > limit) {
            moodTracks = moodTracks.subList(0, limit);
        }

        callback.onRecommendationsReady(moodTracks);
    }
    
    /**
     * Alternative method to get recommendations if API search fails
     */
    private void getRecommendationsAlternative(String mood, int limit, RecommendationCallback callback) {
        Log.d(TAG, "Using alternative recommendation approach for mood: " + mood);
        
        // Get relevant genres
        List<String> relevantGenres = getGenresForMood(mood);
        
        // Ensure we have some genres to work with
        if (relevantGenres.isEmpty()) {
            Log.d(TAG, "No relevant genres found, using generic fallback");
            relevantGenres = Arrays.asList("pop", "rock", "electronic");
        }
        
        // Use ApiDataProvider to get real tracks
        List<Track> resultTracks = getTracksForGenres(relevantGenres);
        
        if (resultTracks.isEmpty()) {
            Log.d(TAG, "No tracks found from API, trying recommended tracks");
            
            // Try to get recommended tracks as another fallback
            final CountDownLatch latch = new CountDownLatch(1);
            final List<Track> recommendedTracks = new ArrayList<>();
            
            apiDataProvider.getRecommendedTracks("top", 20, adaptToApiCallback(new TracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        recommendedTracks.addAll(tracks);
                    }
                    latch.countDown();
                }
                
                @Override
                public void onTracksLoaded(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        recommendedTracks.addAll(tracks);
                    }
                    latch.countDown();
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error loading recommended tracks: " + message);
                    latch.countDown();
                }
            }));
            
            try {
                latch.await(2, TimeUnit.SECONDS);
                if (!recommendedTracks.isEmpty()) {
                    callback.onRecommendationsReady(recommendedTracks);
                    return;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for tracks", e);
            }
        }
        
        // If we got here, use the tracks we found or fallback to default
        if (!resultTracks.isEmpty()) {
            callback.onRecommendationsReady(resultTracks);
        } else {
            callback.onRecommendationsReady(getDefaultTracksForMood(mood));
        }
    }
    
    /**
     * Removes duplicate tracks from a list
     * 
     * @param tracks List of tracks that may contain duplicates
     * @return List of tracks with duplicates removed
     */
    private List<Track> removeDuplicates(List<Track> tracks) {
        List<Track> uniqueTracks = new ArrayList<>();
        Map<String, Boolean> trackMap = new HashMap<>();
        
        for (Track track : tracks) {
            String key = "";
            
            if (track.getId() != null && !track.getId().isEmpty()) {
                key = track.getId();
            } else if (track.getTitle() != null && track.getArtist() != null) {
                key = track.getTitle() + "-" + track.getArtist();
            } else {
                // Skip tracks with no identifiable information
                continue;
            }
            
            if (!trackMap.containsKey(key)) {
                trackMap.put(key, true);
                uniqueTracks.add(track);
            }
        }
        
        return uniqueTracks;
    }
    
    /**
     * Sort tracks by relevance to the given mood
     */
    private List<Track> sortTracksByMoodRelevance(List<Track> tracks, String mood) {
        // Create a copy of the list to sort
        List<Track> sortedTracks = new ArrayList<>(tracks);
        
        // Sort based on relevance score
        Collections.sort(sortedTracks, (t1, t2) -> {
            float score1 = calculateMoodRelevanceScore(t1, mood);
            float score2 = calculateMoodRelevanceScore(t2, mood);
            // Sort in descending order (higher score first)
            return Float.compare(score2, score1);
        });
        
        return sortedTracks;
    }
    
    /**
     * Calculate a relevance score for a track based on mood
     */
    private float calculateMoodRelevanceScore(Track track, String mood) {
        float score = 0.0f;
        Map<String, Float> features = track.getAudioFeatures();
        
        if (features == null || features.isEmpty()) {
            return score;
        }
        
        switch (mood.toLowerCase()) {
            case "happy":
                // Happy: high valence, medium-high energy, medium-high danceability
                if (features.containsKey("valence")) score += features.get("valence") * 0.4f;
                if (features.containsKey("energy")) score += features.get("energy") * 0.3f;
                if (features.containsKey("danceability")) score += features.get("danceability") * 0.3f;
                if (features.containsKey("tempo")) {
                    float normalizedTempo = Math.min(1, Math.max(0, (features.get("tempo") - 90) / 140));
                    score += normalizedTempo * 0.2f;
                }
                break;
                
            case "sad":
                // Sad: low valence, low-medium energy, low-medium tempo, high acousticness
                if (features.containsKey("valence")) score += (1 - features.get("valence")) * 0.4f;
                if (features.containsKey("energy")) score += (1 - features.get("energy")) * 0.3f;
                if (features.containsKey("acousticness")) score += features.get("acousticness") * 0.2f;
                if (features.containsKey("tempo")) {
                    float normalizedTempo = Math.min(1, Math.max(0, (features.get("tempo") - 60) / 140));
                    score += (1 - normalizedTempo) * 0.2f;
                }
                break;
                
            case "energetic":
                // Energetic: high energy, high tempo, medium-high danceability, higher loudness
                if (features.containsKey("energy")) score += features.get("energy") * 0.4f;
                if (features.containsKey("tempo")) {
                    float normalizedTempo = Math.min(1, Math.max(0, (features.get("tempo") - 120) / 160));
                    score += normalizedTempo * 0.3f;
                }
                if (features.containsKey("danceability")) score += features.get("danceability") * 0.2f;
                if (features.containsKey("loudness")) {
                    float normalizedLoudness = Math.min(1, Math.max(0, (features.get("loudness") + 60) / 60));
                    score += normalizedLoudness * 0.1f;
                }
                break;

            case "relaxed":
                // Relaxed: medium-low energy, high acousticness, medium valence, low tempo
                if (features.containsKey("energy")) score += (1 - features.get("energy")) * 0.3f;
                if (features.containsKey("acousticness")) score += features.get("acousticness") * 0.3f;
                if (features.containsKey("valence")) score += (0.4f + features.get("valence") * 0.2f);
                if (features.containsKey("tempo")) {
                    float normalizedTempo = Math.min(1, Math.max(0, (features.get("tempo") - 60) / 140));
                    score += (1 - normalizedTempo) * 0.2f;
                }
                break;

            case "focused":
                // Focused: medium energy, high instrumentalness, medium tempo, low speechiness
                if (features.containsKey("instrumentalness")) score += features.get("instrumentalness") * 0.4f;
                if (features.containsKey("energy")) score += (0.4f + features.get("energy") * 0.2f);
                if (features.containsKey("speechiness")) score += (1 - features.get("speechiness")) * 0.2f;
                if (features.containsKey("tempo")) {
                    float normalizedTempo = Math.min(1, Math.max(0, (features.get("tempo") - 80) / 120));
                    score += (0.5f + normalizedTempo * 0.2f);
                }
                break;

            case "romantic":
                // Romantic: medium valence, medium-low energy, high acousticness, medium-low tempo
                if (features.containsKey("valence")) score += (0.4f + features.get("valence") * 0.2f);
                if (features.containsKey("energy")) score += (0.3f + features.get("energy") * 0.2f);
                if (features.containsKey("acousticness")) score += features.get("acousticness") * 0.3f;
                if (features.containsKey("tempo")) {
                    float normalizedTempo = Math.min(1, Math.max(0, (features.get("tempo") - 60) / 120));
                    score += (1 - normalizedTempo) * 0.2f;
                }
                break;

            default:
                // Default to balanced scoring
                if (features.containsKey("valence")) score += features.get("valence") * 0.25f;
                if (features.containsKey("energy")) score += features.get("energy") * 0.25f;
                if (features.containsKey("danceability")) score += features.get("danceability") * 0.25f;
                if (features.containsKey("acousticness")) score += features.get("acousticness") * 0.25f;
                break;
        }

        // Apply genre boost if the track's genre matches the mood
        List<String> moodGenres = GenreUtils.getMoodGenres(mood);
        if (hasMatchingGenre(track, moodGenres)) {
            score *= 1.2f; // 20% boost for matching genre
        }

        return Math.min(1.0f, score);
    }
    
    /**
     * Normalize mood input to standard categories
     */
    private String normalizeMood(String mood) {
        if (mood == null || mood.isEmpty()) {
            return "happy"; // Default mood
        }
        
        String lowerMood = mood.toLowerCase();
        
        if (lowerMood.contains("happy") || lowerMood.contains("joy") || lowerMood.contains("excited") || 
            lowerMood.contains("cheerful") || lowerMood.contains("upbeat")) {
            return "happy";
        } else if (lowerMood.contains("sad") || lowerMood.contains("down") || lowerMood.contains("blue") || 
                  lowerMood.contains("depressed") || lowerMood.contains("melancholy")) {
            return "sad";
        } else if (lowerMood.contains("energetic") || lowerMood.contains("pumped") || lowerMood.contains("workout") || 
                  lowerMood.contains("active") || lowerMood.contains("hyper")) {
            return "energetic";
        } else if (lowerMood.contains("relax") || lowerMood.contains("calm") || lowerMood.contains("peace") || 
                  lowerMood.contains("chill") || lowerMood.contains("tranquil")) {
            return "calm";
        } else if (lowerMood.contains("romantic") || lowerMood.contains("love") || lowerMood.contains("passion")) {
            return "romantic";
        } else if (lowerMood.contains("angry") || lowerMood.contains("mad") || lowerMood.contains("rage") || 
                  lowerMood.contains("furious")) {
            return "angry";
        } else if (lowerMood.contains("anxious") || lowerMood.contains("nervous") || lowerMood.contains("tense") || 
                  lowerMood.contains("worry")) {
            return "anxious";
        } else if (lowerMood.contains("focus") || lowerMood.contains("study") || lowerMood.contains("concentrate") || 
                  lowerMood.contains("productive")) {
            return "focused";
        } else {
            return "happy"; // Default to happy if no match
        }
    }
    
    /**
     * Gets additional keywords for a mood to expand search
     */
    private List<String> getKeywordsForMood(String mood) {
        List<String> keywords = new ArrayList<>();
        
        switch (mood) {
            case "happy":
                keywords.add("upbeat");
                keywords.add("cheerful");
                keywords.add("feel good");
                keywords.add("positive vibes");
                break;
            case "sad":
                keywords.add("melancholy");
                keywords.add("emotional");
                keywords.add("heartbreak");
                keywords.add("ballad");
                break;
            case "energetic":
                keywords.add("upbeat");
                keywords.add("workout");
                keywords.add("pump up");
                keywords.add("high energy");
                break;
            case "relaxed":
                keywords.add("chill");
                keywords.add("ambient");
                keywords.add("peaceful");
                keywords.add("meditation");
                break;
            case "focused":
                keywords.add("concentration");
                keywords.add("study music");
                keywords.add("background");
                keywords.add("instrumental");
                break;
            case "romantic":
                keywords.add("love songs");
                keywords.add("ballads");
                keywords.add("slow dance");
                keywords.add("date night");
                break;
            default:
                keywords.add(mood + " music");
                keywords.add(mood + " songs");
                break;
        }
        
        return keywords;
    }
    
    /**
     * Get genres for a specific mood
     * 
     * @param mood The mood to get genres for
     * @return List of genres associated with the mood
     */
    private List<String> getGenresForMood(String mood) {
        Map<String, List<String>> moodGenreMap = getMoodToGenresMapping();
        return moodGenreMap.getOrDefault(mood, new ArrayList<>());
    }

    /**
     * Create a mapping of moods to relevant music genres
     * 
     * @return Map of mood to list of genres
     */
    private Map<String, List<String>> getMoodToGenresMapping() {
        Map<String, List<String>> moodToGenresMap = new HashMap<>();
        
        // Happy mood genres
        moodToGenresMap.put("happy", Arrays.asList(
                "pop", "dance", "disco", "happy", "party", "summer", "funk"
        ));
        
        // Sad mood genres
        moodToGenresMap.put("sad", Arrays.asList(
                "blues", "sad", "indie", "chill", "melancholy", "acoustic", "soul"
        ));
        
        // Angry mood genres
        moodToGenresMap.put("angry", Arrays.asList(
                "metal", "rock", "punk", "hardcore", "heavy-metal", "grunge"
        ));
        
        // Relaxed mood genres
        moodToGenresMap.put("relaxed", Arrays.asList(
                "ambient", "chill", "meditation", "sleep", "jazz", "piano", "classical", "study"
        ));
        
        return moodToGenresMap;
    }

    /**
     * Get tracks for a list of genres
     * 
     * @param genres List of genres to fetch tracks for
     * @return List of tracks that match the genres
     */
    private List<Track> getTracksForGenres(List<String> genres) {
        List<Track> tracksForGenres = new ArrayList<>();
        
        // Try to get tracks for each genre
            for (String genre : genres) {
            try {
                final CountDownLatch latch = new CountDownLatch(1);
                final List<Track> genreTracks = new ArrayList<>();
                
                apiDataProvider.getRecommendedTracks(genre, 5, adaptToApiCallback(new TracksCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        if (tracks != null && !tracks.isEmpty()) {
                            genreTracks.addAll(tracks);
                        }
                        latch.countDown();
                    }
                    
                    @Override
                    public void onTracksLoaded(List<Track> tracks) {
                        if (tracks != null && !tracks.isEmpty()) {
                            genreTracks.addAll(tracks);
                        }
                        latch.countDown();
                    }
                    
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Error getting tracks for genre " + genre + ": " + message);
                        latch.countDown();
                    }
                }));
                
                try {
                    latch.await(2, TimeUnit.SECONDS);
                    if (!genreTracks.isEmpty()) {
                        tracksForGenres.addAll(genreTracks);
                        
                        // If we have enough tracks, stop fetching more
                        if (tracksForGenres.size() >= 20) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for tracks: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching tracks for genre: " + genre, e);
            }
        }
        
        // Shuffle the tracks to mix the genres
        Collections.shuffle(tracksForGenres);
        
        // Remove duplicate tracks
        tracksForGenres = removeDuplicates(tracksForGenres);
        
        // Limit to 10 tracks
        if (tracksForGenres.size() > 10) {
            tracksForGenres = tracksForGenres.subList(0, 10);
        }
        
        return tracksForGenres;
    }
    
    /**
     * Returns a limited set of fallback tracks if no other tracks are found
     */
    private List<Track> getLimitedFallbackTracks(List<String> genres) {
        List<Track> fallbacks = new ArrayList<>();
        
        // Create a few placeholder tracks based on genres
        for (int i = 0; i < Math.min(5, genres.size()); i++) {
            String genre = genres.get(i);
            Track track = new Track();
            track.setId("fallback_" + System.currentTimeMillis() + "_" + i);
            track.setTitle("Recommended " + genre + " song " + (i + 1));
            track.setArtist("Banana Music AI");
            track.setImageUrl("https://picsum.photos/200/200?random=" + i);
            track.setDurationMs((180 + new Random().nextInt(120)) * 1000L);
            track.setGenres(new String[]{genre});
            fallbacks.add(track);
        }
        
        return fallbacks;
    }
    
    /**
     * Get audio features for a track
     * @param track The track to get features for
     * @return Map of audio features or null if not available
     */
    private Map<String, Float> getAudioFeaturesForTrack(Track track) {
        // This would ideally call an API to get actual audio features
        // For now, we'll use random values with some bias based on genre
        Map<String, Float> features = new HashMap<>();
        
        try {
            Random random = new Random(track.getId().hashCode()); // Use track ID as seed for consistent features
            
            // Default features
            features.put("danceability", 0.3f + random.nextFloat() * 0.4f);
            features.put("energy", 0.3f + random.nextFloat() * 0.4f);
            features.put("valence", 0.3f + random.nextFloat() * 0.4f);
            features.put("tempo", 80f + random.nextFloat() * 80f);
            features.put("loudness", -20f + random.nextFloat() * 15f);
            features.put("acousticness", 0.3f + random.nextFloat() * 0.4f);
            
            // Adjust based on genre
            List<String> genreList = track.getGenres();
            if (genreList != null && !genreList.isEmpty()) {
                for (String genre : genreList) {
                    if (genre == null) continue;
                    
                    genre = genre.toLowerCase();
                    
                    // Dance genres tend to be more danceable and energetic
                    if (genre.contains("dance") || genre.contains("pop") || genre.contains("electronic")) {
                        features.put("danceability", Math.min(1.0f, features.get("danceability") + 0.2f));
                        features.put("energy", Math.min(1.0f, features.get("energy") + 0.1f));
                    }
                    
                    // Rock and metal tend to be high energy, less acoustic
                    if (genre.contains("rock") || genre.contains("metal")) {
                        features.put("energy", Math.min(1.0f, features.get("energy") + 0.3f));
                        features.put("acousticness", Math.max(0.0f, features.get("acousticness") - 0.2f));
                        features.put("loudness", Math.min(-5.0f, features.get("loudness") + 5.0f));
                    }
                    
                    // Classical and jazz tend to be more acoustic, less danceable
                    if (genre.contains("classic") || genre.contains("jazz") || genre.contains("instrumental")) {
                        features.put("acousticness", Math.min(1.0f, features.get("acousticness") + 0.3f));
                        features.put("danceability", Math.max(0.0f, features.get("danceability") - 0.1f));
                    }
                    
                    // Happy genres have higher valence
                    if (genre.contains("happy") || genre.contains("pop")) {
                        features.put("valence", Math.min(1.0f, features.get("valence") + 0.2f));
                    }
                    
                    // Sad genres have lower valence
                    if (genre.contains("sad") || genre.contains("blues") || genre.contains("soul")) {
                        features.put("valence", Math.max(0.0f, features.get("valence") - 0.2f));
                    }
                }
            }
            
            return features;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating audio features: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get tracks based on mood
     * 
     * @param mood The mood to get tracks for
     * @return List of tracks that match the mood
     */
    public List<Track> getMoodBasedTracks(String mood) {
        List<Track> tracks = new ArrayList<>();
        
        // Get genres related to the mood
        List<String> moodGenres = getGenresForMood(mood);
        
        if (moodGenres.isEmpty()) {
            Log.w(TAG, "No genres found for mood: " + mood + ". Using default tracks.");
            return getDefaultTracksForMood(mood);
        }
        
        // Fetch tracks for the mood genres
        tracks = getTracksForGenres(moodGenres);
        
        // If we couldn't get enough tracks from the API, use default tracks
        if (tracks.isEmpty() || tracks.size() < 5) {
            Log.i(TAG, "Not enough tracks found via API for mood: " + mood + ". Using default tracks.");
            return getDefaultTracksForMood(mood);
        }
        
        return tracks;
    }
    
    /**
     * Get default tracks for a mood (used as fallback when API fails)
     * 
     * @param mood The mood to get default tracks for
     * @return List of default tracks for the mood
     */
    private List<Track> getDefaultTracksForMood(String mood) {
        Log.d(TAG, "Using default tracks for mood: " + mood);
        List<Track> defaultTracks = new ArrayList<>();
        
        // Create some default tracks based on the mood
        String[] artists = {"Banana Artist", "Mood Music", "Genre Expert", "Music Mood"};
        String[] adjectives = {"Great", "Cool", "Amazing", "Best", "Perfect"};
        
        Random random = new Random();
        
        // Create 10 default tracks
        for (int i = 0; i < 10; i++) {
            Track track = new Track();
            String artist = artists[random.nextInt(artists.length)];
            String adjective = adjectives[random.nextInt(adjectives.length)];
            
            track.setId("default_" + mood + "_" + i);
            track.setTitle(adjective + " " + mood + " song " + (i + 1));
            track.setArtist(artist);
            track.setAlbum("Mood Collection");
            track.setDurationMs((180 + random.nextInt(120)) * 1000L);
            
            // For genres, check if the Track class uses array or list
            // We're assuming it uses a String array based on the error
            track.setGenres(new String[]{mood.toLowerCase()});
            
            defaultTracks.add(track);
        }
        
        return defaultTracks;
    }

    /**
     * Get appropriate music genres for a specific mood
     */
    private List<String> getMoodGenres(String mood) {
        return GenreUtils.getMoodGenres(mood);
    }

    public List<Track> getMoodRecommendedTracks(String mood) {
        Log.d(TAG, "Getting recommendations for mood: " + mood);
        
        try {
            List<String> genres = getMoodGenres(mood);
            List<Track> tracks = new ArrayList<>();
            
            // Get recommendations from genres
            for (String genre : genres) {
                // Using the getRecommendedTracks method that exists in ApiDataProvider
                final CountDownLatch latch = new CountDownLatch(1);
                final List<Track> genreTracks = new ArrayList<>();
                
                apiDataProvider.getRecommendedTracks(genre, 5, adaptToApiCallback(new TracksCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        if (tracks != null && !tracks.isEmpty()) {
                            genreTracks.addAll(tracks);
                        }
                        latch.countDown();
                    }
                    
                    @Override
                    public void onTracksLoaded(List<Track> tracks) {
                        if (tracks != null && !tracks.isEmpty()) {
                            genreTracks.addAll(tracks);
                        }
                        latch.countDown();
                    }
                    
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Error getting tracks for genre: " + genre + " - " + message);
                        latch.countDown();
                    }
                }));
                
                try {
                    latch.await(2, TimeUnit.SECONDS);
                    
                    if (!genreTracks.isEmpty()) {
                        tracks.addAll(genreTracks);
                        
                        // If we have enough tracks, we can stop
                        if (tracks.size() >= 20) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for tracks: " + e.getMessage());
                }
            }
            
            // Shuffle the tracks for variety
            if (!tracks.isEmpty()) {
                Collections.shuffle(tracks);
                int limit = Math.min(tracks.size(), 10);
                return tracks.subList(0, limit);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting tracks for mood: " + mood, e);
        }
        
        // Fallback to default tracks if API call fails or returns no results
        return getDefaultTracksForMood(mood);
    }

    /**
     * Get backup genre for a given genre
     * @param genre The original genre
     * @return A backup genre that is similar to the input genre
     */
    private String getBackupGenre(String genre) {
        if (genre == null || genre.isEmpty()) return "pop";
        
        // Simple mapping of similar genres
        switch (genre.toLowerCase()) {
            case "rock": return "alternative";
            case "pop": return "dance";
            case "hip hop": return "rap";
            case "electronic": return "dance";
            case "classical": return "instrumental";
            case "jazz": return "blues";
            case "metal": return "rock";
            case "indie": return "alternative";
            case "soul": return "r&b";
            case "funk": return "soul";
            case "ambient": return "electronic";
            default: return "pop";
        }
    }

    /**
     * Get recommended tracks for a mood using the ApiDataProvider directly
     */
    public void getRecommendedTracksForMood(String mood, int limit, RecommendationCallback callback) {
        List<String> genres = getMoodGenres(mood);
        if (genres.isEmpty()) {
            UIThreadHelper.runOnMainThread(() -> callback.onError("No genres found for mood: " + mood));
            return;
        }

        // Randomly select one of the genres to get recommendations
        final String selectedGenre;
        // Also get related genres for better variety
        List<String> relatedGenres = GenreUtils.getRelatedGenres(genres.get(0));
        if (!relatedGenres.isEmpty() && new Random().nextFloat() < 0.3f) {
            // Sometimes pick a related genre
            selectedGenre = relatedGenres.get(new Random().nextInt(relatedGenres.size()));
        } else {
            // Use a genre from the original list
            selectedGenre = genres.get(new Random().nextInt(genres.size()));
        }

        apiDataProvider.getRecommendedTracks(selectedGenre, limit, adaptToApiCallback(new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    callback.onRecommendationsReady(tracks);
                } else {
                    // Try backup genre if no tracks found
                    String backupGenre = getBackupGenre(selectedGenre);
                    if (backupGenre != null && !backupGenre.equals(selectedGenre)) {
                        apiDataProvider.getRecommendedTracks(backupGenre, limit, adaptToApiCallback(new TracksCallback() {
                            @Override
                            public void onSuccess(List<Track> backupTracks) {
                                if (backupTracks != null && !backupTracks.isEmpty()) {
                                    callback.onRecommendationsReady(backupTracks);
                                } else {
                                    callback.onError("No tracks found for mood: " + mood);
                                }
                            }
                            
                            @Override
                            public void onTracksLoaded(List<Track> backupTracks) {
                                onSuccess(backupTracks);
                            }
                            
                            @Override
                            public void onError(String message) {
                                callback.onError("Error getting backup tracks: " + message);
                            }
                        }));
                    } else {
                        callback.onError("No tracks found for mood: " + mood);
                    }
                }
            }
            
            @Override
            public void onTracksLoaded(List<Track> tracks) {
                onSuccess(tracks);
            }
            
            @Override
            public void onError(String message) {
                // Try backup genre on error
                String backupGenre = getBackupGenre(selectedGenre);
                if (backupGenre != null && !backupGenre.equals(selectedGenre)) {
                    apiDataProvider.getRecommendedTracks(backupGenre, limit, adaptToApiCallback(new TracksCallback() {
                        @Override
                        public void onSuccess(List<Track> backupTracks) {
                            if (backupTracks != null && !backupTracks.isEmpty()) {
                                callback.onRecommendationsReady(backupTracks);
                            } else {
                                callback.onError("No tracks found for mood: " + mood);
                            }
                        }
                        
                        @Override
                        public void onTracksLoaded(List<Track> backupTracks) {
                            onSuccess(backupTracks);
                        }
                        
                        @Override
                        public void onError(String message) {
                            callback.onError("Error getting backup tracks: " + message);
                        }
                    }));
                } else {
                    callback.onError("Error getting tracks: " + message);
                }
            }
        }));
    }

    private List<Track> filterByGenre(List<Track> tracks, List<String> targetGenres) {
        return GenreUtils.filterByGenres(tracks, targetGenres);
    }

    private List<String> getTrackGenres(Track track) {
        return track.getGenres() != null ? track.getGenres() : new ArrayList<>();
    }
    
    private double calculateGenreSimilarity(Track track1, Track track2) {
        return GenreUtils.calculateGenreSimilarity(track1, track2);
    }

    public void getMoodBasedRecommendations(String mood, int limit, RecommendationCallback callback) {
        List<Track> tracks = trackManager.getAllTracks();
        // Filter tracks by mood
        List<Track> moodTracks = new ArrayList<>();
        
        // Filter tracks by mood-appropriate genres
        List<String> moodGenres = GenreUtils.getMoodGenres(mood);
        moodTracks = GenreUtils.filterByGenres(tracks, moodGenres);
        
        // Sort by relevance
        Collections.sort(moodTracks, (t1, t2) -> {
            float score1 = calculateMoodRelevanceScore(t1, mood);
            float score2 = calculateMoodRelevanceScore(t2, mood);
            return Float.compare(score2, score1);
        });
        
        // Limit results
        if (moodTracks.size() > limit) {
            moodTracks = moodTracks.subList(0, limit);
        }
        
        callback.onRecommendationsReady(moodTracks);
    }

    private String[] getGenresArray(Track track) {
        List<String> genres = track.getGenres();
        return genres != null ? genres.toArray(new String[0]) : new String[0];
    }

    private boolean hasMatchingGenre(Track track, List<String> targetGenres) {
        List<String> trackGenres = track.getGenres();
        if (trackGenres == null || trackGenres.isEmpty() || targetGenres == null || targetGenres.isEmpty()) {
            return false;
        }
        
        for (String genre : trackGenres) {
            if (targetGenres.contains(genre.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private String getMainGenre(Track track) {
        List<String> genres = track.getGenres();
        return (genres != null && !genres.isEmpty()) ? genres.get(0) : null;
    }

    private String formatGenres(Track track) {
        if (track == null) return "";
        
        List<String> genres = track.getGenres();
        if (genres == null || genres.isEmpty()) return "";
        
        List<String> formattedGenres = new ArrayList<>();
        for (String genre : genres) {
            formattedGenres.add(GenreUtils.formatGenreName(genre));
        }
        
        return String.join(", ", formattedGenres);
    }

    /**
     * Helper method to convert TracksCallback to ApiDataProvider.TracksCallback
     */
    private ApiDataProvider.TracksCallback adaptToApiCallback(final TracksCallback callback) {
        return CallbackAdapter.toApiDataProviderCallback(callback);
    }

    /**
     * Convert from utils.callbacks.TracksCallback to ApiDataProvider.TracksCallback
     */
    private my.edu.utar.bananamusic.providers.ApiDataProvider.TracksCallback toApiCallback(
            final my.edu.utar.bananamusic.utils.callbacks.TracksCallback callback) {
        if (callback == null) return null;
        return new my.edu.utar.bananamusic.providers.ApiDataProvider.TracksCallback() {
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
}