import java.util.List;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.GenreUtils;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import my.edu.utar.bananamusic.utils.callbacks.RecommendationCallback;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import android.util.Log;
import my.edu.utar.bananamusic.services.MoodRecommendationService;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.Random;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.utils.DeezerHelper;

public class RecommendationEngine {
    private static final String TAG = "RecommendationEngine";
    private final ApiDataProvider apiDataProvider;
    private final AudioPlayerHelper audioPlayerHelper;
    private final ExecutorService executorService;
    private final SpotifyHelper spotifyHelper;
    private final DeezerHelper deezerHelper;

    // Keep track of recently recommended tracks
    private static final int MAX_RECENT_TRACKS = 100;
    private static final LinkedList<String> recentlyRecommendedTrackIds = new LinkedList<>();
    private static final long RECENT_TRACK_TIMEOUT = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Add a cache to store previously recommended tracks
    private static final int MAX_CACHED_TRACKS = 50;
    private static final LinkedList<String> recentlyRecommendedIds = new LinkedList<>();
    private static final Map<String, List<Track>> moodTrackCache = new HashMap<>();
    private static final long CACHE_EXPIRY_TIME = 30 * 60 * 1000; // 30 minutes
    private static long lastCacheClearTime = 0;

    public RecommendationEngine(ApiDataProvider apiDataProvider, AudioPlayerHelper audioPlayerHelper, ExecutorService executorService, SpotifyHelper spotifyHelper, DeezerHelper deezerHelper) {
        this.apiDataProvider = apiDataProvider;
        this.audioPlayerHelper = audioPlayerHelper;
        this.executorService = executorService;
        this.spotifyHelper = spotifyHelper;
        this.deezerHelper = deezerHelper;
    }

    public void getPersonalizedRecommendations(RecommendationCallback callback) {
        apiDataProvider.getRecommendedTracks(null, 20, CallbackAdapter.toApiDataProviderCallback(callback));
    }

    public void getMoodBasedRecommendations(String mood, int limit, final RecommendationCallback callback) {
        Log.d(TAG, "Getting mood based recommendations for: " + mood);
        
        // Clear cache if it's been too long
        clearOldCache();
        
        // Normalize the mood string (case insensitive)
        String normalizedMood = mood != null ? mood.toLowerCase() : "all";
        
        // Get more tracks than needed to allow for filtering
        int expandedLimit = limit * 3; // Get more tracks to allow for better randomization
        
        // Try Spotify first with proper parameters
        spotifyHelper.getMoodBasedRecommendations(normalizedMood, expandedLimit, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    processTracks(tracks, normalizedMood, limit, callback);
                } else {
                    Log.d(TAG, "No Spotify recommendations found, trying Deezer fallback");
                    tryDeezerRecommendations(normalizedMood, expandedLimit, limit, callback);
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting Spotify recommendations: " + message);
                tryDeezerRecommendations(normalizedMood, expandedLimit, limit, callback);
            }
        });
    }

    private void processTracks(List<Track> tracks, String mood, int limit, RecommendationCallback callback) {
        // Filter out recently recommended tracks
        List<Track> filteredTracks = new ArrayList<>();
        for (Track track : tracks) {
            if (!wasRecentlyRecommended(track.getId())) {
                track.setSource("Spotify");
                filteredTracks.add(track);
            }
        }

        // If we have too few tracks after filtering, get some from cache
        if (filteredTracks.size() < limit) {
            List<Track> cachedTracks = moodTrackCache.getOrDefault(mood, new ArrayList<>());
            for (Track track : cachedTracks) {
                if (filteredTracks.size() >= limit) break;
                if (!wasRecentlyRecommended(track.getId())) {
                    filteredTracks.add(track);
                }
            }
        }

        // Shuffle the tracks
        Collections.shuffle(filteredTracks);

        // Take only the number of tracks we need
        List<Track> finalTracks = filteredTracks.subList(0, Math.min(limit, filteredTracks.size()));

        // Update recently recommended tracks and cache
        updateRecentlyRecommended(finalTracks);
        updateMoodCache(mood, tracks);

        callback.onSuccess(finalTracks);
    }

    private void updateRecentlyRecommended(List<Track> tracks) {
        synchronized (recentlyRecommendedIds) {
            for (Track track : tracks) {
                String id = track.getId();
                if (id != null) {
                    // Remove if already exists (to update position)
                    recentlyRecommendedIds.remove(id);
                    // Add to front of list
                    recentlyRecommendedIds.addFirst(id);
                    // Keep size limited
                    while (recentlyRecommendedIds.size() > MAX_CACHED_TRACKS) {
                        recentlyRecommendedIds.removeLast();
                    }
                }
            }
        }
    }

    private boolean wasRecentlyRecommended(String trackId) {
        if (trackId == null) return false;
        synchronized (recentlyRecommendedIds) {
            return recentlyRecommendedIds.contains(trackId);
        }
    }

    private void updateMoodCache(String mood, List<Track> tracks) {
        // Store tracks in mood-specific cache
        moodTrackCache.put(mood, new ArrayList<>(tracks));
    }

    private void clearOldCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheClearTime > CACHE_EXPIRY_TIME) {
            moodTrackCache.clear();
            recentlyRecommendedIds.clear();
            lastCacheClearTime = currentTime;
            Log.d(TAG, "Cleared recommendation cache");
        }
    }

    private void tryDeezerRecommendations(String mood, int expandedLimit, int limit, RecommendationCallback callback) {
        Log.d(TAG, "Trying Deezer recommendations for mood: " + mood);
        deezerHelper.getMoodBasedRecommendations(mood, expandedLimit, new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    processTracks(tracks, mood, limit, callback);
                } else {
                    callback.onError("No recommendations found for mood: " + mood);
                }
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Get appropriate seed genres for a given mood
     */
    private String getMoodSeedGenres(String mood) {
        switch (mood) {
            case "happy":
                return "pop,dance,happy,disco,edm";
            case "sad":
                return "sad,indie,singer-songwriter,piano,acoustic";
            case "energetic":
                return "edm,electronic,dance,rock,workout";
            case "relaxed":
                return "ambient,chill,sleep,classical,piano";
            case "focused":
                return "classical,study,instrumental,ambient,piano";
            case "romantic":
                return "r-n-b,soul,jazz,pop,romantic";
            default:
                return "pop,rock,indie,electronic,alternative";
        }
    }

    private void getMoodSpecificTracks(String mood, int limit, RecommendationCallback callback) {
        Map<String, String> parameters = buildMoodParameters(mood);
        
        // Add seed_genres parameter
        parameters.put("seed_genres", getMoodSeedGenres(mood));
        
        // Remove seed_artists parameter as it's causing 404 errors
        // Build query string
        StringBuilder queryBuilder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!first) {
                queryBuilder.append("&");
            }
            queryBuilder.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        
        String query = queryBuilder.toString();
        Log.d(TAG, "Sending mood parameters: " + query);
        
        apiDataProvider.getVariedRecommendations(query, limit, new ApiDataProvider.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onRecommendationsReady(tracks);
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting varied recommendations: " + message);
                callback.onError(message);
            }
        });
    }

    private float getMoodScoreThreshold(String mood) {
        switch (mood.toLowerCase()) {
            case "happy": return 0.5f;
            case "sad": return 0.4f;
            case "energetic": return 0.5f;
            case "relaxed": return 0.4f;
            case "focused": return 0.4f;
            case "romantic": return 0.4f;
            default: return 0.3f;
        }
    }

    private boolean isMoodAppropriate(Track track, String mood) {
        Map<String, Float> features = track.getAudioFeatures();
        if (features == null) return false;

        switch (mood.toLowerCase()) {
            case "happy":
                return features.getOrDefault("valence", 0f) >= 0.5f 
                    || features.getOrDefault("energy", 0f) >= 0.5f;
            
            case "sad":
                return features.getOrDefault("valence", 1f) <= 0.5f 
                    || features.getOrDefault("energy", 1f) <= 0.5f;
            
            case "energetic":
                return features.getOrDefault("energy", 0f) >= 0.6f 
                    || features.getOrDefault("tempo", 0f) >= 120f;
            
            case "relaxed":
                return features.getOrDefault("energy", 1f) <= 0.5f 
                    || features.getOrDefault("acousticness", 0f) >= 0.4f;
            
            case "focused":
                return features.getOrDefault("instrumentalness", 0f) >= 0.3f 
                    || features.getOrDefault("speechiness", 1f) <= 0.4f;
            
            case "romantic":
                return features.getOrDefault("valence", 0f) >= 0.3f 
                    || features.getOrDefault("energy", 1f) <= 0.7f 
                    || features.getOrDefault("acousticness", 0f) >= 0.2f;
            
            default:
                return true;
        }
    }

    public void getGenreBasedRecommendations(List<String> genres, int limit, RecommendationCallback callback) {
        if (genres == null || genres.isEmpty()) {
            callback.onError("No genres provided");
            return;
        }
        apiDataProvider.getRecommendedTracks(String.join(",", genres), limit, CallbackAdapter.toApiDataProviderCallback(callback));
    }

    private List<Track> filterByGenrePreference(List<Track> tracks, List<String> preferredGenres) {
        return GenreUtils.filterByGenres(tracks, preferredGenres);
    }

    private float calculateGenreSimilarityScore(Track track1, Track track2) {
        return (float) GenreUtils.calculateGenreSimilarity(track1, track2);
    }

    private Map<String, String> buildMoodParameters(String mood) {
        Map<String, String> params = new HashMap<>();
        
        // Add randomness to parameters to increase variety
        float randomVariation = (float) (Math.random() * 0.2 - 0.1); // -0.1 to 0.1
        Random random = new Random();
        
        switch (mood.toLowerCase()) {
            case "happy":
                params.put("target_valence", String.valueOf(0.8 + randomVariation));
                params.put("target_energy", String.valueOf(0.7 + randomVariation));
                params.put("target_danceability", String.valueOf(0.7 + randomVariation));
                params.put("min_tempo", String.valueOf(Math.max(80, 100 + (int)(randomVariation * 20)))); 
                break;
                
            case "sad":
                params.put("target_valence", String.valueOf(Math.max(0.1, 0.3 + randomVariation)));
                params.put("target_energy", String.valueOf(Math.max(0.1, 0.4 + randomVariation)));
                params.put("target_acousticness", String.valueOf(Math.min(0.9, 0.7 + randomVariation)));
                params.put("max_tempo", String.valueOf(Math.min(120, 100 + (int)(randomVariation * 20))));
                break;
                
            case "energetic":
                params.put("target_energy", String.valueOf(Math.min(0.95, 0.8 + randomVariation)));
                params.put("target_tempo", String.valueOf(Math.min(150, 130 + (int)(randomVariation * 20))));
                params.put("target_danceability", String.valueOf(0.7 + randomVariation));
                params.put("min_popularity", String.valueOf(Math.max(30, 50 + (int)(randomVariation * 20))));
                break;
                
            case "relaxed":
                // Updated relaxed parameters for better results
                params.put("target_energy", String.valueOf(Math.max(0.1, 0.3 + randomVariation)));
                params.put("target_acousticness", String.valueOf(Math.min(0.95, 0.7 + randomVariation)));
                params.put("target_instrumentalness", String.valueOf(Math.min(0.8, 0.4 + randomVariation)));
                params.put("max_tempo", String.valueOf(Math.min(100, 85 + (int)(randomVariation * 15))));
                params.put("min_popularity", "30"); // Ensure somewhat known tracks
                params.put("target_valence", String.valueOf(Math.max(0.3, 0.5 + randomVariation))); // Slightly positive valence
                break;
                
            case "focused":
                params.put("target_instrumentalness", String.valueOf(Math.min(0.9, 0.7 + randomVariation)));
                params.put("target_energy", String.valueOf(Math.max(0.3, 0.5 + randomVariation)));
                params.put("target_speechiness", String.valueOf(Math.max(0.05, 0.1 + randomVariation)));
                params.put("target_tempo", String.valueOf(Math.max(90, 110 + (int)(randomVariation * 20))));
                break;
                
            case "romantic":
                params.put("target_valence", String.valueOf(Math.min(0.8, 0.6 + randomVariation)));
                params.put("target_energy", String.valueOf(Math.max(0.2, 0.4 + randomVariation)));
                params.put("target_acousticness", String.valueOf(Math.min(0.9, 0.6 + randomVariation)));
                params.put("max_tempo", String.valueOf(Math.min(120, 100 + (int)(randomVariation * 20))));
                break;
                
            default:
                params.put("target_popularity", String.valueOf(Math.min(100, 70 + (int)(randomVariation * 30))));
                params.put("target_energy", String.valueOf(0.5 + randomVariation));
                params.put("target_valence", String.valueOf(0.5 + randomVariation));
                break;
        }

        // Add offset for variety (0-20)
        params.put("offset", String.valueOf(random.nextInt(20)));
        
        // Add market parameter
        params.put("market", "US");
        
        // Add minimum popularity to avoid completely unknown tracks
        if (!params.containsKey("min_popularity")) {
            params.put("min_popularity", "30");
        }
        
        return params;
    }
    
    /**
     * Get a random selection of genres appropriate for sad mood
     * This ensures we get different sad songs each time
     */
    private String getSadMoodGenres() {
        String[] sadGenres = {
            "sad", "blues", "indie", "indie-pop", "folk", "emo", 
            "soul", "classical", "alt-rock", "indie-rock", "piano", 
            "acoustic", "singer-songwriter", "ambient", "chill"
        };
        
        // Pick 2-3 random genres from the list
        int numGenres = 2 + (int)(Math.random() * 2); // 2 or 3
        Set<String> selectedGenres = new HashSet<>();
        
        while (selectedGenres.size() < numGenres) {
            int index = (int)(Math.random() * sadGenres.length);
            selectedGenres.add(sadGenres[index]);
        }
        
        return String.join(",", selectedGenres);
    }
    
    private float calculateMoodScore(Track track, String targetMood, Map<String, Float> userPreferences) {
        float score = 0.0f;
        Map<String, Float> features = track.getAudioFeatures();
        
        // Calculate mood score based on audio features
        if (features != null && !features.isEmpty()) {
            switch (targetMood.toLowerCase()) {
                case "happy":
                    if (features.containsKey("valence")) score += features.get("valence") * 0.4f;
                    if (features.containsKey("energy")) score += features.get("energy") * 0.3f;
                    if (features.containsKey("danceability")) score += features.get("danceability") * 0.3f;
                    break;
                    
                case "sad":
                    if (features.containsKey("valence")) score += (1 - features.get("valence")) * 0.4f;
                    if (features.containsKey("energy")) score += (1 - features.get("energy")) * 0.3f;
                    if (features.containsKey("acousticness")) score += features.get("acousticness") * 0.3f;
                    break;
                    
                case "energetic":
                    if (features.containsKey("energy")) score += features.get("energy") * 0.4f;
                    if (features.containsKey("tempo")) {
                        float normalizedTempo = Math.min(1, Math.max(0, (features.get("tempo") - 120) / 160));
                        score += normalizedTempo * 0.3f;
                    }
                    if (features.containsKey("danceability")) score += features.get("danceability") * 0.3f;
                    break;
                    
                case "relaxed":
                    if (features.containsKey("energy")) score += (1 - features.get("energy")) * 0.3f;
                    if (features.containsKey("acousticness")) score += features.get("acousticness") * 0.4f;
                    if (features.containsKey("instrumentalness")) score += features.get("instrumentalness") * 0.3f;
                    break;
                    
                case "focused":
                    if (features.containsKey("instrumentalness")) score += features.get("instrumentalness") * 0.4f;
                    if (features.containsKey("energy")) score += (0.4f + features.get("energy") * 0.2f);
                    if (features.containsKey("speechiness")) score += (1 - features.get("speechiness")) * 0.4f;
                    break;
                    
                case "romantic":
                    if (features.containsKey("valence")) score += (0.4f + features.get("valence") * 0.2f);
                    if (features.containsKey("energy")) score += (0.3f + features.get("energy") * 0.2f);
                    if (features.containsKey("acousticness")) score += features.get("acousticness") * 0.3f;
                    break;
            }
        }
        
        // Add user preference score
        if (userPreferences != null && !userPreferences.isEmpty()) {
            float preferenceScore = 0.0f;
            
            if (features != null) {
                for (Map.Entry<String, Float> entry : userPreferences.entrySet()) {
                    if (features.containsKey(entry.getKey())) {
                        float difference = Math.abs(features.get(entry.getKey()) - entry.getValue());
                        preferenceScore += (1 - difference);
                    }
                }
                preferenceScore /= userPreferences.size();
            }
            
            score += preferenceScore * 0.3f;
        }
        
        // Add genre match score
        List<String> moodGenres = GenreUtils.getMoodGenres(targetMood);
        if (track.getGenres() != null) {
            for (String genre : track.getGenres()) {
                if (moodGenres.contains(genre.toLowerCase())) {
                    score += 0.2f;
                    break;
                }
            }
        }
        
        return Math.min(1.0f, score);
    }
    
    private Map<String, Float> getUserMoodPreferences() {
        Map<String, Float> preferences = new HashMap<>();
        List<Track> recentTracks = audioPlayerHelper.getRecentlyPlayedTracks();
        
        if (recentTracks != null && !recentTracks.isEmpty()) {
            float totalValence = 0f, totalEnergy = 0f, totalDanceability = 0f, totalAcousticness = 0f;
            int count = 0;
            
            for (Track track : recentTracks) {
                Map<String, Float> features = track.getAudioFeatures();
                if (features != null) {
                    if (features.containsKey("valence")) totalValence += features.get("valence");
                    if (features.containsKey("energy")) totalEnergy += features.get("energy");
                    if (features.containsKey("danceability")) totalDanceability += features.get("danceability");
                    if (features.containsKey("acousticness")) totalAcousticness += features.get("acousticness");
                    count++;
                }
            }
            
            if (count > 0) {
                preferences.put("valence", totalValence / count);
                preferences.put("energy", totalEnergy / count);
                preferences.put("danceability", totalDanceability / count);
                preferences.put("acousticness", totalAcousticness / count);
            }
        }
        
        return preferences;
    }

    private static class ScoredTrack {
        Track track;
        float score;
        
        ScoredTrack(Track track, float score) {
            this.track = track;
            this.score = score;
        }
    }

    private Set<String> getRecentlyRecommendedTrackIds() {
        synchronized (recentlyRecommendedTrackIds) {
            return new HashSet<>(recentlyRecommendedTrackIds);
        }
    }

    /**
     * Helper method to process tracks and return final results
     */
    private void processAndReturnTracks(String mood, int limit, List<Track> filteredTracks, 
                                       Map<String, Float> moodPreferences, RecommendationCallback callback) {
        // Score tracks based on mood with relaxed thresholds
        List<ScoredTrack> scoredTracks = new ArrayList<>();
        for (Track track : filteredTracks) {
            float score = calculateMoodScore(track, mood, moodPreferences);
            // Use a lower threshold to include more variety
            if (score >= getMoodScoreThreshold(mood) * 0.7f) {
                scoredTracks.add(new ScoredTrack(track, score));
            }
        }

        // Sort by score but add significant randomization for variety
        Collections.sort(scoredTracks, (a, b) -> {
            float diff = b.score - a.score;
            // Increase the threshold for randomization to get more variety
            if (Math.abs(diff) < 0.2f) {
                return Math.random() > 0.5 ? 1 : -1;
            }
            return Float.compare(b.score, a.score);
        });

        // Get final recommendations
        List<Track> recommendations = new ArrayList<>();
        for (ScoredTrack scoredTrack : scoredTracks) {
            if (recommendations.size() >= limit) break;
            recommendations.add(scoredTrack.track);
        }

        // Add randomization - shuffle the bottom half of the list for more variety
        if (recommendations.size() > 2) {
            int midPoint = recommendations.size() / 2;
            List<Track> topHalf = recommendations.subList(0, midPoint);
            List<Track> bottomHalf = recommendations.subList(midPoint, recommendations.size());
            Collections.shuffle(bottomHalf);
            
            recommendations = new ArrayList<>();
            recommendations.addAll(topHalf);
            recommendations.addAll(bottomHalf);
        }

        // Add the three popular songs randomly if they're not already in the recommendations
        recommendations = integratePopularSongs(recommendations, mood, limit);

        // Update recently recommended tracks
        updateRecentlyRecommended(recommendations);

        if (recommendations.isEmpty()) {
            // Fallback to genre-based if no tracks match mood criteria
            getGenreBasedRecommendations(GenreUtils.getMoodGenres(mood), limit, callback);
        } else {
            callback.onRecommendationsReady(recommendations);
        }
    }

    /**
     * Integrates the three popular songs (Believer, Peaches, Blinding Lights) into recommendations
     * ensuring they appear randomly and not necessarily all three every time
     */
    private List<Track> integratePopularSongs(List<Track> recommendations, String mood, int limit) {
        // Create a new result list
        List<Track> result = new ArrayList<>(recommendations);
        
        // Ensure we don't exceed the limit
        while (result.size() > limit) {
            result.remove(result.size() - 1);
        }
        
        // Add randomization - shuffle the list for more variety
        Collections.shuffle(result);
        
        return result;
    }

    /**
     * Build random parameters for a given mood to increase variety
     * This ensures each mood has a distinct musical character
     */
    private String buildRandomMoodParameters(String mood, long seed) {
        // Use the seed for deterministic randomness based on timestamp
        Random random = new Random(seed);
        float randomVariation = (float) (random.nextDouble() * 0.2 - 0.1); // -0.1 to 0.1
        
        StringBuilder params = new StringBuilder();
        
        switch (mood.toLowerCase()) {
            case "happy":
                // Happy: high valence, high energy, danceable
                params.append("&target_valence=").append(Math.min(0.95, 0.85 + randomVariation));
                params.append("&min_valence=").append(0.7); // Ensure minimum high valence
                params.append("&target_energy=").append(Math.min(0.95, 0.75 + randomVariation));
                params.append("&min_energy=").append(0.6); // Ensure minimum energy
                params.append("&target_danceability=").append(Math.min(0.95, 0.75 + randomVariation));
                // Add genre seeds for happy music
                params.append("&seed_genres=").append("pop,dance,happy,disco,edm");
                break;
                
            case "sad":
                // Sad: low valence, lower energy, higher acousticness
                params.append("&target_valence=").append(Math.max(0.05, 0.25 + randomVariation));
                params.append("&max_valence=").append(0.4); // Ensure maximum low valence
                params.append("&target_energy=").append(Math.max(0.05, 0.35 + randomVariation));
                params.append("&max_energy=").append(0.5); // Ensure maximum moderate energy
                params.append("&target_acousticness=").append(Math.min(0.95, 0.7 + randomVariation));
                params.append("&min_acousticness=").append(0.4); // Ensure minimum acousticness
                // Add genre seeds for sad music
                params.append("&seed_genres=").append("sad,indie,singer-songwriter,piano,acoustic");
                break;
                
            case "energetic":
                // Energetic: high energy, fast tempo, danceable
                params.append("&target_energy=").append(Math.min(0.95, 0.9 + randomVariation));
                params.append("&min_energy=").append(0.8); // Ensure high energy
                params.append("&target_tempo=").append(Math.min(200, 140 + (int)(randomVariation * 30)));
                params.append("&min_tempo=").append(120); // Ensure fast tempo
                params.append("&target_danceability=").append(Math.min(0.95, 0.75 + randomVariation));
                // Add genre seeds for energetic music
                params.append("&seed_genres=").append("edm,electronic,dance,rock,workout");
                break;
                
            case "relaxed":
                // Relaxed: low energy, high acousticness, moderate tempo
                params.append("&target_energy=").append(Math.max(0.05, 0.25 + randomVariation));
                params.append("&max_energy=").append(0.4); // Ensure low energy
                params.append("&target_acousticness=").append(Math.min(0.95, 0.8 + randomVariation));
                params.append("&min_acousticness=").append(0.6); // Ensure high acousticness
                params.append("&target_instrumentalness=").append(Math.min(0.95, 0.5 + randomVariation));
                params.append("&max_tempo=").append(100); // Ensure slower tempo
                // Add genre seeds for relaxed music
                params.append("&seed_genres=").append("chill,ambient,acoustic,sleep,jazz");
                break;
                
            case "focused":
                // Focused: moderate energy, high instrumentalness, low speechiness
                params.append("&target_energy=").append(0.5 + randomVariation);
                params.append("&target_instrumentalness=").append(Math.min(0.95, 0.7 + randomVariation));
                params.append("&min_instrumentalness=").append(0.5); // Ensure high instrumentalness
                params.append("&target_speechiness=").append(Math.max(0.01, 0.05 + randomVariation));
                params.append("&max_speechiness=").append(0.1); // Ensure low speechiness (few vocals)
                // Add genre seeds for focused music
                params.append("&seed_genres=").append("classical,study,instrumental,ambient,piano");
                break;
                
            case "romantic":
                // Romantic: moderate valence, medium energy, acoustic
                params.append("&target_valence=").append(0.6 + randomVariation);
                params.append("&target_energy=").append(0.45 + randomVariation);
                params.append("&target_acousticness=").append(Math.min(0.95, 0.6 + randomVariation));
                params.append("&min_acousticness=").append(0.3);
                params.append("&max_tempo=").append(110); // Not too fast
                // Add genre seeds for romantic music
                params.append("&seed_genres=").append("r-n-b,soul,jazz,pop,romantic");
                break;
        }
        
        // Add timestamp to ensure fresh results every time
        params.append("&timestamp=").append(System.currentTimeMillis());
        
        // Add random offset for more variety
        params.append("&offset=").append(random.nextInt(25));
        
        return params.toString();
    }
    
    /**
     * Generate a personalized recommendation reason based on mood
     * These are more detailed and mood-specific
     */
    private String generateMoodBasedReason(String mood, Track track) {
        List<String> reasons = new ArrayList<>();
        
        switch (mood.toLowerCase()) {
            case "happy":
                reasons.add("Perfect uplifting beats for your happy mood");
                reasons.add("This upbeat track will keep your positive energy flowing");
                reasons.add("Energetic, joyful track to match your happy state");
                reasons.add("The cheerful rhythm mirrors your current mood");
                reasons.add("Bright melody to accompany your good vibes");
                break;
                
            case "sad":
                reasons.add("This reflective piece matches your melancholy mood");
                reasons.add("Emotional depth for when you need to process feelings");
                reasons.add("The gentle melody complements your contemplative state");
                reasons.add("Soulful track that acknowledges your emotions");
                reasons.add("When you need music that understands how you feel");
                break;
                
            case "energetic":
                reasons.add("High-tempo beat to fuel your energy levels");
                reasons.add("Dynamic rhythm to match your active state");
                reasons.add("Powerful track to sustain your momentum");
                reasons.add("Fast-paced music perfectly aligned with your energetic mood");
                reasons.add("Driving beats to keep your energy at maximum");
                break;
                
            case "relaxed":
                reasons.add("Gentle soundscape to maintain your calm state");
                reasons.add("Soothing melodies for your relaxed mindset");
                reasons.add("Peaceful ambient tones to enhance your tranquility");
                reasons.add("Soft harmonies to accompany your unwinding");
                reasons.add("Serene composition matching your relaxed mood");
                break;
                
            case "focused":
                reasons.add("Instrumental track to help maintain your concentration");
                reasons.add("Minimal distractions for your productive state");
                reasons.add("Steady rhythm to support your focused mind");
                reasons.add("Background harmony ideal for deep work");
                reasons.add("Ambient sounds that won't break your concentration");
                break;
                
            case "romantic":
                reasons.add("Tender melody for your romantic mood");
                reasons.add("Emotional piece to complement your affectionate state");
                reasons.add("Intimate soundscape for your sentimental feelings");
                reasons.add("Warm harmonies that enhance romantic moments");
                reasons.add("Gentle rhythm that speaks to the heart");
                break;
                
            default:
                reasons.add("Music selected specifically for your current mood");
                reasons.add("Recommended based on your listening preferences");
                reasons.add("Track that matches your current emotional state");
                break;
        }
        
        // Choose a random reason
        int index = (int)(Math.random() * reasons.size());
        return reasons.get(index);
    }
    
    /**
     * Filter tracks to ensure they match the specified mood
     * This method ensures strong mood matching
     */
    private List<Track> filterTracksByMood(List<Track> tracks, String mood) {
        List<Track> filteredTracks = new ArrayList<>();
        for (Track track : tracks) {
            if (matchesMood(track, mood)) {
                filteredTracks.add(track);
            }
        }
        
        // If too few tracks match strict criteria, relax filtering
        if (filteredTracks.size() < 3 && !tracks.isEmpty()) {
            return tracks; // Return all tracks if filtering is too strict
        }
        
        return filteredTracks;
    }
    
    /**
     * Determine if a track matches the specified mood based on audio features
     */
    private boolean matchesMood(Track track, String mood) {
        Map<String, Float> features = track.getAudioFeatures();
        if (features == null || features.isEmpty()) {
            return true; // No features to judge by, so include by default
        }
        
        switch (mood.toLowerCase()) {
            case "happy":
                return features.getOrDefault("valence", 0f) >= 0.6f && 
                       features.getOrDefault("energy", 0f) >= 0.5f;
                       
            case "sad":
                return features.getOrDefault("valence", 1f) <= 0.5f && 
                       features.getOrDefault("energy", 1f) <= 0.6f;
                       
            case "energetic":
                return features.getOrDefault("energy", 0f) >= 0.7f || 
                       features.getOrDefault("tempo", 0f) >= 120f;
                       
            case "relaxed":
                return features.getOrDefault("energy", 1f) <= 0.5f && 
                       features.getOrDefault("acousticness", 0f) >= 0.4f;
                       
            case "focused":
                return features.getOrDefault("instrumentalness", 0f) >= 0.3f && 
                       features.getOrDefault("speechiness", 1f) <= 0.3f;
                       
            case "romantic":
                return features.getOrDefault("valence", 0f) >= 0.4f && 
                       features.getOrDefault("valence", 1f) <= 0.8f && 
                       features.getOrDefault("energy", 1f) <= 0.7f;
                       
            default:
                return true;
        }
    }

    /**
     * Get a list of your 3 favorite tracks
     * Adjusts their audio features based on the current mood
     */
    private List<Track> getFavoriteTracks() {
        List<Track> favorites = new ArrayList<>();
        
        // These would normally be loaded from a database or preferences
        // For now, hardcoding these as sample favorites that will randomly appear
        favorites.add(createFavoriteTrack(
            "4cOdK2wGLETKBW3PvgPWqT", 
            "Believer", 
            "Imagine Dragons", 
            "Evolve", 
            "One of your all-time favorites"));
            
        favorites.add(createFavoriteTrack(
            "6pDadgqNu6TlRoOUfWXcZr", 
            "Peaches", 
            "Justin Bieber", 
            "Justice", 
            "You play this track often"));
            
        favorites.add(createFavoriteTrack(
            "5Tpny5FIGdOXYCpUAMfBiv", 
            "Blinding Lights", 
            "The Weeknd", 
            "After Hours", 
            "One of your most played songs"));
            
        return favorites;
    }
    
    /**
     * Create a favorite track with the given data
     * Adapts audio features to the current mood
     */
    private Track createFavoriteTrack(String id, String title, String artist, String album, String reason) {
        Track track = new Track();
        track.setId(id);
        track.setTitle(title);
        track.setArtist(artist);
        track.setAlbum(album);
        track.setRecommendationReason(reason);
        
        // Set as a favorite track
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("isFavorite", true);
        track.setMetadata(metadata);
        
        return track;
    }

    /**
     * Adjust a favorite track's audio features to match the current mood
     * This ensures the track appears appropriate for the selected mood
     */
    private Track adjustTrackForMood(Track track, String mood) {
        Map<String, Float> features = new HashMap<>();
        
        // Apply mood-specific features
        switch (mood.toLowerCase()) {
            case "happy":
                features.put("valence", 0.85f);
                features.put("energy", 0.8f);
                features.put("danceability", 0.75f);
                break;
                
            case "sad":
                features.put("valence", 0.3f);
                features.put("energy", 0.4f);
                features.put("acousticness", 0.7f);
                break;
                
            case "energetic":
                features.put("energy", 0.9f);
                features.put("tempo", 140f);
                features.put("danceability", 0.8f);
                break;
                
            case "relaxed":
                features.put("energy", 0.3f);
                features.put("acousticness", 0.8f);
                features.put("instrumentalness", 0.4f);
                break;
                
            case "focused":
                features.put("energy", 0.5f);
                features.put("instrumentalness", 0.7f);
                features.put("speechiness", 0.1f);
                break;
                
            case "romantic":
                features.put("valence", 0.6f);
                features.put("energy", 0.45f);
                features.put("acousticness", 0.6f);
                break;
                
            default:
                features.put("valence", 0.5f);
                features.put("energy", 0.5f);
                features.put("danceability", 0.5f);
        }
        
        // Set the adjusted features
        track.setAudioFeatures(features);
        
        return track;
    }

    /**
     * Map mood to API parameter
     */
    private String mapMoodToApiParameter(String mood) {
        switch (mood.toLowerCase()) {
            case "happy": return "target_valence=0.8&target_energy=0.7&target_danceability=0.7&min_tempo=80";
            case "sad": return "target_valence=0.3&target_energy=0.4&target_acousticness=0.7&max_tempo=120";
            case "energetic": return "target_energy=0.8&target_tempo=130&target_danceability=0.7&min_popularity=50";
            case "relaxed": return "target_energy=0.4&target_acousticness=0.7&target_instrumentalness=0.5&max_tempo=120";
            case "focused": return "target_instrumentalness=0.7&target_energy=0.5&target_speechiness=0.1&target_tempo=110";
            case "romantic": return "target_valence=0.6&target_energy=0.4&target_acousticness=0.6&max_tempo=120";
            default: return "target_popularity=70&target_energy=0.5&target_valence=0.5";
        }
    }

    /**
     * Clear the recommendation cache to force fresh recommendations
     */
    public void clearCache() {
        Log.d(TAG, "Clearing recommendation cache");
        recentlyRecommendedTrackIds.clear();
    }
    
    /**
     * Interface for components to be notified when recommendations change
     */
    public interface RecommendationListener {
        void onRecommendationsUpdated();
    }
    
    private final List<RecommendationListener> listeners = new ArrayList<>();
    
    /**
     * Register a listener for recommendation updates
     */
    public void addListener(RecommendationListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Unregister a listener for recommendation updates
     */
    public void removeListener(RecommendationListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners that recommendations have been updated
     */
    private void notifyRecommendationsUpdated() {
        for (RecommendationListener listener : listeners) {
            listener.onRecommendationsUpdated();
        }
    }

    private boolean wasRecentlyRecommended(Track track) {
        return track.getId() != null && recentlyRecommendedTrackIds.contains(track.getId());
    }

    private List<Track> filterOutRecentlyPlayed(List<Track> tracks) {
        List<Track> filteredTracks = new ArrayList<>();
        for (Track track : tracks) {
            if (!wasRecentlyRecommended(track)) {
                filteredTracks.add(track);
            }
        }
        return filteredTracks.isEmpty() ? tracks : filteredTracks;
    }
}