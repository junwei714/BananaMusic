package my.edu.utar.bananamusic.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import my.edu.utar.bananamusic.ml.UserPreferenceModel;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.TrackHistory;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.DeezerHelper;
import my.edu.utar.bananamusic.utils.UIThreadHelper;
import my.edu.utar.bananamusic.utils.TrackManager;
import my.edu.utar.bananamusic.managers.HistoryManager;
import my.edu.utar.bananamusic.utils.GenreUtils;
import my.edu.utar.bananamusic.utils.callbacks.TrackInfoCallback;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Service that provides AI-powered music recommendations based on user listening history
 * and feedback. It integrates with AudioPlayerHelper to track user listening behavior
 * and provides personalized recommendations.
 */
public class AIRecommendationService {
    private static final String TAG = "AIRecommendationService";
    
    // Singleton instance
    private static AIRecommendationService instance;
    
    // Context reference
    private final Context context;
    
    // Models and helpers
    private final UserPreferenceModel preferenceModel;
    private final AudioPlayerHelper audioPlayerHelper;
    private final DeezerHelper deezerHelper;
    private final ApiDataProvider apiDataProvider;
    private final PlaylistManager playlistManager;
    
    // Background executor
    private final Executor executor;
    
    // Recommendation listeners
    private final List<RecommendationListener> listeners = new ArrayList<>();
    
    // Recommendation cache to avoid redundant processing
    private List<Track> cachedRecommendations = new ArrayList<>();
    private long lastRecommendationTime = 0;
    private static final long CACHE_TTL = 1800000; // 30 minutes in milliseconds
    
    // Cache for album artwork to reduce API calls
    private final Map<String, String> albumArtCache = new HashMap<>();
    private static final int MAX_ALBUM_ART_CACHE_SIZE = 200;
    
    // New weights for different recommendation factors
    private static final float WEIGHT_GENRE_MATCH = 0.3f;
    private static final float WEIGHT_ARTIST_SIMILARITY = 0.2f;
    private static final float WEIGHT_MOOD_MATCH = 0.2f;
    private static final float WEIGHT_TEMPO_MATCH = 0.15f;
    private static final float WEIGHT_ERA_MATCH = 0.15f;

    // Constants for diversity control
    private static final float DISCOVERY_RATIO = 0.3f; // 30% new/unknown artists
    private static final int MIN_GENRE_VARIETY = 3; // Minimum different genres to include
    
    private TrackManager trackManager;
    private HistoryManager historyManager;
    
    private String currentMood = "happy"; // Default mood
    
    /**
     * Interface for components to be notified when recommendations change
     */
    public interface RecommendationListener {
        void onRecommendationsUpdated(List<Track> recommendations);
    }
    
    /**
     * Callback for recommendation requests
     */
    public interface RecommendationCallback {
        void onRecommendationsReady(List<Track> recommendations);
        void onError(String errorMessage);
        // Add compatibility method
        default void onSuccess(List<Track> tracks) {
            onRecommendationsReady(tracks);
        }
    }
    
    /**
     * Callback for feedback processing
     */
    public interface FeedbackCallback {
        void onFeedbackProcessed(boolean success);
    }
    
    /**
     * Interface for feedback on recommendation quality
     */
    public interface RecommendationQualityCallback {
        void onQualityReported(boolean success);
    }
    
    /**
     * Private constructor - use getInstance()
     */
    private AIRecommendationService(Context context) {
        this.context = context.getApplicationContext();
        this.preferenceModel = UserPreferenceModel.getInstance(context);
        this.audioPlayerHelper = AudioPlayerHelper.getInstance(context);
        this.deezerHelper = DeezerHelper.getInstance(context);
        this.apiDataProvider = ApiDataProvider.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
        this.trackManager = TrackManager.getInstance(context);
        this.historyManager = HistoryManager.getInstance(context);
        this.playlistManager = PlaylistManager.getInstance(context);
        
        // Initialize by processing existing track history
        initializeFromTrackHistory();
        
        Log.d(TAG, "AIRecommendationService initialized");
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized AIRecommendationService getInstance(Context context) {
        if (instance == null) {
            instance = new AIRecommendationService(context);
        }
        return instance;
    }
    
    /**
     * Initialize the model with existing track history data
     */
    private void initializeFromTrackHistory() {
        executor.execute(() -> {
            // Get track history from audio player
            Map<String, TrackHistory> trackHistory = audioPlayerHelper.getTrackHistoryMap();
            if (trackHistory == null || trackHistory.isEmpty()) {
                Log.d(TAG, "No track history data available for initialization");
                return;
            }
            
            Log.d(TAG, "Initializing from track history with " + trackHistory.size() + " tracks");
            
            // Process each track history entry
            for (TrackHistory history : trackHistory.values()) {
                if (history.getTrack() != null) {
                    // Update preference model with track data
                    preferenceModel.updateWithTrack(
                        history.getTrack(), 
                        history.getPlayCount(),
                        history.getLastPlayed()
                    );
                }
            }
            
            Log.d(TAG, "Finished initializing from track history");
        });
    }
    
    /**
     * Register a listener to be notified when recommendations are updated
     */
    public void addRecommendationListener(RecommendationListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a previously registered listener
     */
    public void removeRecommendationListener(RecommendationListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners that recommendations have been updated
     */
    private void notifyListeners(List<Track> recommendations) {
        UIThreadHelper.runOnMainThread(() -> {
            for (RecommendationListener listener : listeners) {
                listener.onRecommendationsUpdated(recommendations);
            }
        });
    }
    
    /**
     * Get personalized recommendations for the user with explanations
     */
    public void getPersonalizedRecommendations(int limit, RecommendationCallback callback) {
        // Get personalized recommendations
        apiDataProvider.getPersonalizedRecommendations(
            currentMood, // Current mood
            20, // Limit
            adaptToApiCallback(callback) // Callback
        );
    }

    /**
     * Calculate a score for a track based on user preferences
     */
    private float calculateTrackScore(Track track, Map<String, Integer> genrePrefs,
                                    Map<String, Integer> artistPrefs, 
                                    Map<String, Float> moodPrefs) {
        float score = 0;
        
        // Genre match
        if (track.getGenres() != null) {
            float genreScore = calculateGenreScore(track.getGenres(), genrePrefs);
            score += WEIGHT_GENRE_MATCH * genreScore;
        }

        // Artist similarity
        if (track.getArtist() != null) {
            float artistScore = calculateArtistScore(track.getArtist(), artistPrefs);
            score += WEIGHT_ARTIST_SIMILARITY * artistScore;
        }

        // Mood match
        if (track.getMoodTags() != null) {
            float moodScore = calculateMoodScore(track.getMoodTags(), moodPrefs);
            score += WEIGHT_MOOD_MATCH * moodScore;
        }

        // Tempo and era matching
        score += WEIGHT_TEMPO_MATCH * calculateTempoScore(track);
        score += WEIGHT_ERA_MATCH * calculateEraScore(track);

        return score;
    }

    /**
     * Generate a human-readable explanation for why this track was recommended
     */
    private String generateRecommendationExplanation(Track track, Map<String, Integer> genrePrefs,
                                                   Map<String, Integer> artistPrefs, 
                                                   Map<String, Float> moodPrefs) {
        StringBuilder explanation = new StringBuilder();
        
        // Find the strongest matching factor
        float genreScore = calculateGenreScore(track.getGenres(), genrePrefs);
        float artistScore = calculateArtistScore(track.getArtist(), artistPrefs);
        float moodScore = calculateMoodScore(track.getMoodTags(), moodPrefs);

        if (genreScore > artistScore && genreScore > moodScore) {
            explanation.append("Based on your love for ").append(track.getGenres().get(0));
        } else if (artistScore > genreScore && artistScore > moodScore) {
            explanation.append("Similar to ").append(track.getArtist());
        } else {
            explanation.append("Matches your current mood preferences");
        }

        return explanation.toString();
    }

    /**
     * Ensure diversity in recommendations by balancing familiar and discovery tracks
     */
    private List<Track> ensureDiversity(List<ScoredTrack> scoredTracks, int limit) {
        // Sort by score
        scoredTracks.sort((a, b) -> Float.compare(b.score, a.score));
        
        // Calculate how many discovery tracks to include
        int discoveryCount = Math.round(limit * DISCOVERY_RATIO);
        
        // Split into familiar and discovery tracks
        List<Track> familiar = new ArrayList<>();
        List<Track> discovery = new ArrayList<>();
        Set<String> selectedGenres = new HashSet<>();
        
        for (ScoredTrack scored : scoredTracks) {
            Track track = scored.track;
            
            // Track genre variety
            if (track.getGenres() != null && !track.getGenres().isEmpty()) {
                selectedGenres.add(track.getGenres().get(0));
            }
            
            if (isKnownArtist(track.getArtist())) {
                if (familiar.size() < (limit - discoveryCount)) {
                    familiar.add(track);
                }
            } else {
                if (discovery.size() < discoveryCount) {
                    discovery.add(track);
                }
            }
            
            // Break if we have enough tracks and genre variety
            if (familiar.size() + discovery.size() >= limit && 
                selectedGenres.size() >= MIN_GENRE_VARIETY) {
                break;
            }
        }
        
        // Combine and shuffle slightly to avoid obvious grouping
        List<Track> result = new ArrayList<>(familiar);
        result.addAll(discovery);
        Collections.shuffle(result.subList(Math.min(3, result.size()), result.size()));
        
        return result;
    }

    private static class ScoredTrack {
        final Track track;
        final float score;
        final String explanation;

        ScoredTrack(Track track, float score, String explanation) {
            this.track = track;
            this.score = score;
            this.explanation = explanation;
        }
    }

    // Helper methods for calculating various scores
    private float calculateGenreScore(List<String> trackGenres, Map<String, Integer> genrePrefs) {
        if (trackGenres == null || trackGenres.isEmpty() || genrePrefs.isEmpty()) {
            return 0;
        }
        
        float maxScore = 0;
        int maxPreference = Collections.max(genrePrefs.values());
        
        for (String genre : trackGenres) {
            if (genrePrefs.containsKey(genre)) {
                float normalizedScore = (float) genrePrefs.get(genre) / maxPreference;
                maxScore = Math.max(maxScore, normalizedScore);
            }
        }
        
        return maxScore;
    }

    private float calculateArtistScore(String artist, Map<String, Integer> artistPrefs) {
        if (artist == null || artistPrefs.isEmpty()) {
            return 0;
        }
        
        Integer plays = artistPrefs.get(artist);
        if (plays == null) {
            return 0;
        }
        
        int maxPlays = Collections.max(artistPrefs.values());
        return (float) plays / maxPlays;
    }

    private float calculateMoodScore(List<String> trackMoods, Map<String, Float> moodPrefs) {
        if (trackMoods == null || trackMoods.isEmpty() || moodPrefs.isEmpty()) {
            return 0;
        }
        
        float maxScore = 0;
        for (String mood : trackMoods) {
            Float preference = moodPrefs.get(mood);
            if (preference != null) {
                maxScore = Math.max(maxScore, preference);
            }
        }
        
        return maxScore;
    }

    private float calculateTempoScore(Track track) {
        // Implementation would compare track tempo with user's preferred tempo ranges
        return 0.5f; // Placeholder implementation
    }

    private float calculateEraScore(Track track) {
        // Implementation would compare track release year with user's preferred eras
        return 0.5f; // Placeholder implementation
    }

    private boolean isKnownArtist(String artist) {
        return audioPlayerHelper.getArtistPlayCount(artist) > 0;
    }
    
    /**
     * Ensure that all tracks in the list have valid album art URLs
     * @param tracks The list of tracks to check
     * @param onComplete Callback to run when all tracks have been verified
     */
    private void ensureTracksHaveAlbumArt(List<Track> tracks, Runnable onComplete) {
        // Count of tracks that need their artwork updated
        final int[] pendingUpdates = {0};
        
        for (Track track : tracks) {
            // Skip tracks that already have valid album art (not placeholder)
            if (track.getAlbumArt() != null && !track.getAlbumArt().isEmpty() && 
               !track.getAlbumArt().contains("placeholder.com")) {
                continue;
            }
            
            // Check if we have this track's album art in the cache
            String cacheKey = generateAlbumArtCacheKey(track);
            if (albumArtCache.containsKey(cacheKey)) {
                String cachedArtUrl = albumArtCache.get(cacheKey);
                if (cachedArtUrl != null && !cachedArtUrl.isEmpty()) {
                    track.setAlbumArt(cachedArtUrl);
                    Log.d(TAG, "Using cached album art for: " + track.getTitle());
                    continue;
                }
            }
            
            pendingUpdates[0]++;
            
            // Try getting album art from multiple sources
            tryGetAlbumArtFromAPI(track, () -> {
                // Cache the result for future use
                if (track.getAlbumArt() != null && !track.getAlbumArt().isEmpty()) {
                    cacheAlbumArt(cacheKey, track.getAlbumArt());
                }
                checkCompletionAndCallback(pendingUpdates, onComplete);
            });
        }
        
        // If no tracks needed updates, call the completion callback immediately
        if (pendingUpdates[0] == 0) {
            onComplete.run();
        }
    }
    
    /**
     * Generate a cache key for album art based on track metadata
     */
    private String generateAlbumArtCacheKey(Track track) {
        StringBuilder key = new StringBuilder();
        
        // Use a combination of track identifiers for the key
        if (track.getSpotifyId() != null && !track.getSpotifyId().isEmpty()) {
            key.append("spotify:").append(track.getSpotifyId());
        } else {
            // If no Spotify ID, use artist + title
            if (track.getArtist() != null) {
                key.append(track.getArtist().toLowerCase()).append("_");
            }
            if (track.getTitle() != null) {
                key.append(track.getTitle().toLowerCase());
            }
        }
        
        return key.toString();
    }
    
    /**
     * Cache album art URL for a track
     */
    private void cacheAlbumArt(String key, String artUrl) {
        // Add to cache, implementing a simple LRU by removing random entries when full
        if (albumArtCache.size() >= MAX_ALBUM_ART_CACHE_SIZE) {
            // Remove a random key to make room (simple approach)
            String keyToRemove = albumArtCache.keySet().iterator().next();
            albumArtCache.remove(keyToRemove);
        }
        
        albumArtCache.put(key, artUrl);
    }
    
    /**
     * Try to get album art from multiple API sources in order of priority
     */
    private void tryGetAlbumArtFromAPI(Track track, Runnable onComplete) {
        if (track.getSpotifyId() != null && !track.getSpotifyId().isEmpty()) {
            apiDataProvider.getTrackInfo(track.getSpotifyId(), new TrackInfoCallback() {
                @Override
                public void onTrackInfoLoaded(Track updatedTrack) {
                    if (updatedTrack != null && updatedTrack.getAlbumArt() != null 
                            && !updatedTrack.getAlbumArt().isEmpty()) {
                        track.setAlbumArt(updatedTrack.getAlbumArt());
                        // Cache the album art
                        String cacheKey = generateAlbumArtCacheKey(track);
                        cacheAlbumArt(cacheKey, updatedTrack.getAlbumArt());
                        onComplete.run();
                    } else {
                        tryGetAlbumArtFromDeezer(track, onComplete);
                    }
                }

                @Override
                public void onError(String message) {
                    Log.d(TAG, "Error getting track info: " + message);
                    tryGetAlbumArtFromDeezer(track, onComplete);
                }
            });
        } else {
            tryGetAlbumArtFromDeezer(track, onComplete);
        }
    }
    
    /**
     * Try to get album art from Deezer API
     */
    private void tryGetAlbumArtFromDeezer(Track track, Runnable onComplete) {
        // Create a search query with artist and title
        String query = "";
        if (track.getArtist() != null && !track.getArtist().isEmpty()) {
            query += "artist:\"" + track.getArtist() + "\" ";
        }
        if (track.getTitle() != null && !track.getTitle().isEmpty()) {
            query += "track:\"" + track.getTitle() + "\"";
        }
        
        if (query.isEmpty()) {
            // No search data, use default art
            setDefaultAlbumArt(track);
            onComplete.run();
            return;
        }
        
        // Use Deezer API through DeezerHelper
        deezerHelper.searchTracks(query, new DeezerHelper.DeezerCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    JSONArray data = jsonResponse.optJSONArray("data");
                    
                    if (data != null && data.length() > 0) {
                        JSONObject trackData = data.getJSONObject(0);
                        JSONObject album = trackData.optJSONObject("album");
                        
                        if (album != null) {
                            // Try to get high-resolution cover first
                            String albumArt = album.optString("cover_xl"); 
                            if (albumArt == null || albumArt.isEmpty()) {
                                albumArt = album.optString("cover_big");
                            }
                            if (albumArt == null || albumArt.isEmpty()) {
                                albumArt = album.optString("cover_medium");
                            }
                            if (albumArt == null || albumArt.isEmpty()) {
                                albumArt = album.optString("cover");
                            }
                            
                            if (albumArt != null && !albumArt.isEmpty()) {
                                track.setAlbumArt(albumArt);
                                Log.d(TAG, "Updated album art from Deezer API for: " + track.getTitle());
                                onComplete.run();
                                return;
                            }
                        }
                    }
                    
                    // If we couldn't get art from Deezer, use default
                    setDefaultAlbumArt(track);
                    onComplete.run();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing Deezer response: " + e.getMessage());
                    setDefaultAlbumArt(track);
                    onComplete.run();
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Deezer API error: " + errorMessage);
                setDefaultAlbumArt(track);
                onComplete.run();
            }
        });
    }
    
    /**
     * Set default album art based on track genre, mood, or title
     * @param track The track to set album art for
     */
    private void setDefaultAlbumArt(Track track) {
        // Default URL in case we can't determine a better one
        String defaultArt = "https://via.placeholder.com/300";
        
        // Try to determine the mood or genre from the track's metadata
        String title = track.getTitle() != null ? track.getTitle().toLowerCase() : "";
        String artist = track.getArtist() != null ? track.getArtist().toLowerCase() : "";
        String album = track.getAlbum() != null ? track.getAlbum().toLowerCase() : "";
        String[] genres = getGenreArray(track);
        
        // Check for genre first
        boolean genreFound = false;
        for (String genre : genres) {
            String artUrl = getArtUrlForGenre(genre.toLowerCase());
            if (artUrl != null) {
                track.setAlbumArt(artUrl);
                genreFound = true;
                break;
            }
        }
        
        // If no genre match, check for mood indicators in title, artist, or album
        if (!genreFound) {
            if (containsAny(title + " " + artist + " " + album, 
                    new String[]{"happy", "joy", "fun", "smile", "laugh"})) {
                track.setAlbumArt("https://via.placeholder.com/300/FFD700/000000?text=Happy");
            } 
            else if (containsAny(title + " " + artist + " " + album, 
                    new String[]{"sad", "blue", "cry", "tear", "melancholy"})) {
                track.setAlbumArt("https://via.placeholder.com/300/4682B4/FFFFFF?text=Sad");
            }
            else if (containsAny(title + " " + artist + " " + album, 
                    new String[]{"love", "heart", "romantic", "passion"})) {
                track.setAlbumArt("https://via.placeholder.com/300/FF69B4/FFFFFF?text=Love");
            }
            else if (containsAny(title + " " + artist + " " + album, 
                    new String[]{"energy", "power", "dance", "party", "club"})) {
                track.setAlbumArt("https://via.placeholder.com/300/FF4500/FFFFFF?text=Energy");
            }
            else if (containsAny(title + " " + artist + " " + album, 
                    new String[]{"relax", "chill", "calm", "peace", "sleep"})) {
                track.setAlbumArt("https://via.placeholder.com/300/00BFFF/FFFFFF?text=Relax");
            }
            else if (containsAny(title + " " + artist + " " + album,
                    new String[]{"nostalgic", "nostalgia", "memory", "memories", "throwback"})) {
                track.setAlbumArt("https://via.placeholder.com/300/9370DB/FFFFFF?text=Nostalgic");
            }
            else if (containsAny(title + " " + artist + " " + album,
                    new String[]{"epic", "dramatic", "powerful", "intense", "orchestra"})) {
                track.setAlbumArt("https://via.placeholder.com/300/8B0000/FFFFFF?text=Epic");
            }
            else {
                // Default with track title if no mood detected
                String encodedTitle = title.replace(" ", "%20");
                if (encodedTitle.length() > 15) {
                    encodedTitle = encodedTitle.substring(0, 15) + "...";
                }
                track.setAlbumArt("https://via.placeholder.com/300/333333/FFFFFF?text=" + encodedTitle);
            }
        }
        
        Log.d(TAG, "Applied default album art for track: " + track.getTitle());
    }
    
    /**
     * Get album art URL for a specific genre
     */
    private String getArtUrlForGenre(String genre) {
        if (genre == null) return null;
        
        // Match genre to a color/theme
        if (genre.contains("rock")) {
            return "https://via.placeholder.com/300/8B0000/FFFFFF?text=Rock";
        } else if (genre.contains("pop")) {
            return "https://via.placeholder.com/300/FF1493/FFFFFF?text=Pop";
        } else if (genre.contains("hip") || genre.contains("rap")) {
            return "https://via.placeholder.com/300/191970/FFFFFF?text=Hip-Hop";
        } else if (genre.contains("jazz")) {
            return "https://via.placeholder.com/300/CD853F/FFFFFF?text=Jazz";
        } else if (genre.contains("classic")) {
            return "https://via.placeholder.com/300/DEB887/000000?text=Classical";
        } else if (genre.contains("electronic") || genre.contains("techno") || genre.contains("house")) {
            return "https://via.placeholder.com/300/00CED1/000000?text=Electronic";
        } else if (genre.contains("country")) {
            return "https://via.placeholder.com/300/DAA520/000000?text=Country";
        } else if (genre.contains("folk")) {
            return "https://via.placeholder.com/300/8FBC8F/000000?text=Folk";
        } else if (genre.contains("metal")) {
            return "https://via.placeholder.com/300/696969/FFFFFF?text=Metal";
        } else if (genre.contains("blues")) {
            return "https://via.placeholder.com/300/4682B4/FFFFFF?text=Blues";
        } else if (genre.contains("reggae")) {
            return "https://via.placeholder.com/300/228B22/FFFFFF?text=Reggae";
        } else if (genre.contains("r&b") || genre.contains("soul")) {
            return "https://via.placeholder.com/300/800080/FFFFFF?text=R%26B";
        } else {
            return null;
        }
    }
    
    /**
     * Check if a string contains any of the keywords
     */
    private boolean containsAny(String text, String[] keywords) {
        if (text == null) return false;
        String lowerText = text.toLowerCase();
        
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if all pending updates are complete and invoke the callback if they are
     */
    private void checkCompletionAndCallback(final int[] pendingUpdates, Runnable onComplete) {
        pendingUpdates[0]--;
        if (pendingUpdates[0] <= 0) {
            onComplete.run();
        }
    }
    
    /**
     * Get recommendations similar to a specific track
     * @param track The track to find similar tracks for
     * @param limit Maximum number of recommendations to return
     * @param callback Callback to receive the recommendations
     */
    public void getSimilarTrackRecommendations(Track track, int limit, RecommendationCallback callback) {
        apiDataProvider.getRecommendedTracks("all", limit, adaptToApiCallback(callback));
    }
    
    /**
     * Get recommendations by genre
     * @param genre The genre to match
     * @param limit Maximum number of tracks to return
     * @param callback Callback to receive the recommendations
     */
    public void getGenreRecommendations(String genre, int limit, RecommendationCallback callback) {
        List<Track> tracks = trackManager.getAllTracks();
        List<Track> genreTracks = GenreUtils.filterByGenres(tracks, Collections.singletonList(genre));
        
        if (genreTracks.isEmpty()) {
            callback.onError("No tracks found for genre: " + genre);
            return;
        }
        
        // Sort by relevance to genre
        List<ScoredTrack> scoredTracks = new ArrayList<>();
        for (Track track : genreTracks) {
            float score = calculateRelevanceScore(track, genre);
            scoredTracks.add(new ScoredTrack(track, score, 
                "Recommended because you like " + genre + " music"));
        }
        
        // Sort by score
        Collections.sort(scoredTracks, (a, b) -> Float.compare(b.score, a.score));
        
        // Extract Track objects
        List<Track> recommendations = new ArrayList<>();
        int count = Math.min(limit, scoredTracks.size());
        for (int i = 0; i < count; i++) {
            recommendations.add(scoredTracks.get(i).track);
        }
        
        callback.onRecommendationsReady(recommendations);
    }

    private float calculateRelevanceScore(Track track, String targetGenre) {
        float score = 0.0f;
        
        // Check direct genre match
        List<String> trackGenres = track.getGenres();
        if (trackGenres.contains(targetGenre.toLowerCase())) {
            score += 1.0f;
        }
        
        // Check metadata match
        String metadata = (track.getTitle() + " " + track.getArtist() + " " + track.getAlbum()).toLowerCase();
        if (metadata.contains(targetGenre.toLowerCase())) {
            score += 0.5f;
        }
        
        return score;
    }

    /**
     * Log details about the recommendations for debugging
     */
    private void logRecommendationDetails(Track sourceTrack, List<Track> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) return;
        
        StringBuilder sb = new StringBuilder();
        sb.append("Recommendations for: ")
          .append(sourceTrack.getTitle())
          .append(" by ")
          .append(sourceTrack.getArtist())
          .append(" (").append(recommendations.size()).append(" tracks):\n");
        
        for (int i = 0; i < recommendations.size(); i++) {
            Track track = recommendations.get(i);
            sb.append(i+1).append(". ")
              .append(track.getTitle())
              .append(" by ")
              .append(track.getArtist())
              .append("\n");
        }
        
        Log.d(TAG, sb.toString());
    }
    
    /**
     * Track when a user plays a track to update the preference model
     * @param track The track that was played
     */
    public void trackPlayedTrack(Track track) {
        if (track == null || track.getId() == null) return;
        
        executor.execute(() -> {
            // For new tracks, use initial values
            preferenceModel.updateWithTrack(track, 1, System.currentTimeMillis());
            
            // Invalidate recommendation cache
            lastRecommendationTime = 0;
        });
    }
    
    /**
     * Process user feedback on a recommendation
     * @param trackId ID of the track that was rated
     * @param isPositive Whether the feedback was positive (true) or negative (false)
     * @param callback Callback for the result
     */
    public void processFeedback(String trackId, boolean isPositive, FeedbackCallback callback) {
        if (trackId == null || trackId.isEmpty()) {
            if (callback != null) {
                UIThreadHelper.runOnMainThread(() -> callback.onFeedbackProcessed(false));
            }
            return;
        }
        
        // Forward to the preference model
        preferenceModel.processFeedback(trackId, isPositive, new UserPreferenceModel.FeedbackCallback() {
            public void onFeedbackProcessed(boolean success) {
                if (success) {
                    // Invalidate recommendation cache on successful feedback
                    lastRecommendationTime = 0;
                }
                
                if (callback != null) {
                    UIThreadHelper.runOnMainThread(() -> callback.onFeedbackProcessed(success));
                }
            }
        });
    }
    
    /**
     * Generate mood-based recommendations
     * @param mood The mood to match
     * @param limit Maximum number of recommendations to return
     * @param callback Callback to receive the recommendations
     */
    public void getMoodBasedRecommendations(String mood, int limit, RecommendationCallback callback) {
        // Delegate to MoodRecommendationService
        MoodRecommendationService moodService = MoodRecommendationService.getInstance(context);
        moodService.getRecommendationsForMood(mood, limit, new MoodRecommendationService.RecommendationCallback() {
            public void onRecommendationsReady(List<Track> recommendations) {
                UIThreadHelper.runOnMainThread(() -> callback.onRecommendationsReady(recommendations));
            }
            
            public void onError(String errorMessage) {
                UIThreadHelper.runOnMainThread(() -> callback.onError(errorMessage));
            }
        });
    }
    
    /**
     * Clear the recommendation cache to force fetching new recommendations
     */
    public void clearRecommendationCache() {
        cachedRecommendations.clear();
        lastRecommendationTime = 0;
        Log.d(TAG, "Recommendation cache cleared");
    }
    
    /**
     * Clear the album art cache
     */
    public void clearAlbumArtCache() {
        albumArtCache.clear();
        Log.d(TAG, "Album art cache cleared");
    }
    
    /**
     * Report quality of a recommendation for AI model improvement
     * 
     * @param trackId ID of the track that was recommended
     * @param rating Quality rating from 1-5 (1 = poor, 5 = excellent)
     * @param source Source of the recommendation (e.g., "similar", "genre", "mood")
     * @param callback Callback for operation result
     */
    public void reportRecommendationQuality(String trackId, int rating, String source, 
                                           RecommendationQualityCallback callback) {
        if (trackId == null || trackId.isEmpty()) {
            if (callback != null) {
                UIThreadHelper.runOnMainThread(() -> callback.onQualityReported(false));
            }
            return;
        }
        
        // Validate rating range
        int validRating = Math.max(1, Math.min(5, rating));
        
        // Execute in background
        executor.execute(() -> {
            // Store the rating in the preference model
            Map<String, Object> qualityData = new HashMap<>();
            qualityData.put("trackId", trackId);
            qualityData.put("rating", validRating);
            qualityData.put("source", source);
            qualityData.put("timestamp", System.currentTimeMillis());
            
            boolean success = preferenceModel.storeRecommendationQuality(trackId, qualityData);
            
            // If rating is high (4-5), consider it positive feedback
            // If rating is low (1-2), consider it negative feedback
            if (validRating >= 4) {
                preferenceModel.processFeedback(trackId, true, null);
            } else if (validRating <= 2) {
                preferenceModel.processFeedback(trackId, false, null);
            }
            
            // Invalidate cache if feedback is very negative to refresh recommendations
            if (validRating == 1) {
                clearRecommendationCache();
            }
            
            // Log the quality report
            Log.d(TAG, "Recommendation quality report: Track=" + trackId + 
                      ", Rating=" + validRating + ", Source=" + source + 
                      ", Success=" + success);
            
            // Return result via callback
            if (callback != null) {
                final boolean finalSuccess = success;
                UIThreadHelper.runOnMainThread(() -> {
                    callback.onQualityReported(finalSuccess);
                });
            }
        });
    }
    
    /**
     * Get recommendations based on the most recently played tracks
     * 
     * @param limit Maximum number of recommendations to return
     * @param callback Callback to receive the recommendations
     */
    public void getRecentlyPlayedBasedRecommendations(int limit, RecommendationCallback callback) {
        // Get recently played tracks from audio player
        List<Track> recentTracks = audioPlayerHelper.getRecentlyPlayedTracks();
        
        if (recentTracks == null || recentTracks.isEmpty()) {
            Log.d(TAG, "No recently played tracks available for recommendations");
            fallbackToGenericRecommendations(limit, callback);
            return;
        }
        
        // Limit to the most recent 10 tracks if we have more
        if (recentTracks.size() > 10) {
            recentTracks = recentTracks.subList(0, 10);
        }
        
        Log.d(TAG, "Getting recommendations based on " + recentTracks.size() + " recently played tracks");
        
        // Use the first (most recent) track as a seed for recommendations
        Track seedTrack = recentTracks.get(0);
        
        // Create a final copy of the track list for use in the inner class
        final List<Track> finalRecentTracks = new ArrayList<>(recentTracks);
        
        // Get similar tracks based on the seed track
        getSimilarTrackRecommendations(seedTrack, limit, new RecommendationCallback() {
            @Override
            public void onRecommendationsReady(List<Track> recommendations) {
                // Filter out tracks that were recently played
                List<Track> filteredRecommendations = new ArrayList<>();
                
                for (Track recommendation : recommendations) {
                    boolean alreadyPlayed = false;
                    for (Track recentTrack : finalRecentTracks) {
                        if (recommendation.getId() != null && 
                            recommendation.getId().equals(recentTrack.getId())) {
                            alreadyPlayed = true;
                            break;
                        }
                    }
                    
                    if (!alreadyPlayed) {
                        filteredRecommendations.add(recommendation);
                    }
                }
                
                // If we filtered out too many, add some back in from generic recommendations
                if (filteredRecommendations.size() < limit / 2) {
                    apiDataProvider.getRecommendedTracks("all", limit - filteredRecommendations.size(), 
                        adaptToApiCallback(new RecommendationCallback() {
                            @Override
                            public void onRecommendationsReady(List<Track> additionalTracks) {
                                filteredRecommendations.addAll(additionalTracks);
                                callback.onRecommendationsReady(filteredRecommendations);
                            }
                            
                            @Override
                            public void onError(String message) {
                                // Still return what we have
                                callback.onRecommendationsReady(filteredRecommendations);
                            }
                        }));
                } else {
                    callback.onRecommendationsReady(filteredRecommendations);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error getting similar tracks: " + errorMessage);
                fallbackToGenericRecommendations(limit, callback);
            }
        });
    }
    
    /**
     * Refresh the recommendation model based on the latest user listening data
     * This should be called periodically or after significant user activity
     */
    public void refreshRecommendationModel() {
        executor.execute(() -> {
            Log.d(TAG, "Refreshing recommendation model with latest user data");
            
            // Clear caches to force fresh recommendations
            clearRecommendationCache();
            
            // Get track history from audio player
            Map<String, TrackHistory> trackHistory = audioPlayerHelper.getTrackHistoryMap();
            if (trackHistory == null || trackHistory.isEmpty()) {
                Log.d(TAG, "No track history data available for model refresh");
                return;
            }
            
            // Process each track history entry
            int processedTracks = 0;
            for (TrackHistory history : trackHistory.values()) {
                if (history.getTrack() != null && history.getLastPlayed() > 0) {
                    // Update preference model with track data
                    preferenceModel.updateWithTrack(
                        history.getTrack(), 
                        history.getPlayCount(),
                        history.getLastPlayed()
                    );
                    processedTracks++;
                }
            }
            
            Log.d(TAG, "Recommendation model refreshed with " + processedTracks + " tracks");
        });
    }

    /**
     * Get recommendations in batch with diversity controls
     */
    public void getBatchRecommendations(int batchSize, boolean ensureDiversity, RecommendationCallback callback) {
        apiDataProvider.getRecommendedTracks("all", batchSize, adaptToApiCallback(callback));
    }

    /**
     * Analyze if it's a good time to refresh recommendations
     * @return true if recommendations should be refreshed
     */
    public boolean shouldRefreshRecommendations() {
        // Check if cache is expired
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRecommendationTime > CACHE_TTL) {
            return true;
        }

        // Check if user's recent activity suggests different preferences
        List<TrackHistory> recentHistory = audioPlayerHelper.getTrackHistoryList();
        if (recentHistory.isEmpty()) {
            return false;
        }

        // Get most recent track
        TrackHistory mostRecent = recentHistory.get(0);
        
        // If the most recent track is very different from our cached recommendations,
        // we might want to refresh
        if (!cachedRecommendations.isEmpty()) {
            Track recentTrack = mostRecent.getTrack();
            if (recentTrack != null) {
                // Calculate average similarity to cached recommendations
                double avgSimilarity = cachedRecommendations.stream()
                    .mapToDouble(cached -> calculateTrackSimilarity(recentTrack, cached))
                    .average()
                    .orElse(1.0);
                
                // If similarity is low, suggest refreshing
                if (avgSimilarity < 0.3) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Calculate similarity between two tracks based on their features
     */
    private double calculateTrackSimilarity(Track track1, Track track2) {
        if (track1 == null || track2 == null) {
            return 0.0;
        }

        double similarity = 0.0;
        int factors = 0;

        // Compare genres
        List<String> genres1 = track1.getGenres();
        List<String> genres2 = track2.getGenres();
        if (genres1 != null && genres2 != null && !genres1.isEmpty() && !genres2.isEmpty()) {
            // Calculate Jaccard similarity for genres
            Set<String> union = new HashSet<>(genres1);
            union.addAll(genres2);
            Set<String> intersection = new HashSet<>(genres1);
            intersection.retainAll(genres2);
            similarity += (double) intersection.size() / union.size();
            factors++;
        }

        // Compare audio features
        Map<String, Float> features1 = track1.getAudioFeatures();
        Map<String, Float> features2 = track2.getAudioFeatures();
        if (features1 != null && features2 != null && !features1.isEmpty() && !features2.isEmpty()) {
            // Calculate feature similarity using Euclidean distance
            double featureSimilarity = 0.0;
            int featureCount = 0;
            
            for (String feature : features1.keySet()) {
                if (features2.containsKey(feature)) {
                    double diff = features1.get(feature) - features2.get(feature);
                    featureSimilarity += 1.0 - Math.min(1.0, Math.abs(diff));
                    featureCount++;
                }
            }
            
            if (featureCount > 0) {
                similarity += featureSimilarity / featureCount;
                factors++;
            }
        }

        // Compare mood tags
        List<String> moods1 = track1.getMoodTags();
        List<String> moods2 = track2.getMoodTags();
        if (moods1 != null && moods2 != null && !moods1.isEmpty() && !moods2.isEmpty()) {
            Set<String> union = new HashSet<>(moods1);
            union.addAll(moods2);
            Set<String> intersection = new HashSet<>(moods1);
            intersection.retainAll(moods2);
            similarity += (double) intersection.size() / union.size();
            factors++;
        }

        return factors > 0 ? similarity / factors : 0.0;
    }

    /**
     * Pre-fetch recommendations in background
     * This can be called when we predict the user might need recommendations soon
     */
    public void prefetchRecommendations(int count) {
        if (!shouldRefreshRecommendations()) {
            return;
        }

        executor.execute(() -> {
            getBatchRecommendations(count, true, new RecommendationCallback() {
                @Override
                public void onRecommendationsReady(List<Track> recommendations) {
                    // Just cache the results
                    cachedRecommendations = new ArrayList<>(recommendations);
                    lastRecommendationTime = System.currentTimeMillis();
                    Log.d(TAG, "Pre-fetched " + recommendations.size() + " recommendations");
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error pre-fetching recommendations: " + errorMessage);
                }
            });
        });
    }

    private Map<String, Integer> analyzeGenrePreferences(List<TrackHistory> history) {
        Map<String, Integer> genrePrefs = new HashMap<>();
        for (TrackHistory trackHistory : history) {
            Track track = trackHistory.getTrack();
            if (track != null && track.getGenres() != null) {
                for (String genre : track.getGenres()) {
                    genrePrefs.put(genre, genrePrefs.getOrDefault(genre, 0) + trackHistory.getPlayCount());
                }
            }
        }
        return genrePrefs;
    }

    private Map<String, Integer> analyzeArtistPreferences(List<TrackHistory> history) {
        Map<String, Integer> artistPrefs = new HashMap<>();
        for (TrackHistory trackHistory : history) {
            Track track = trackHistory.getTrack();
            if (track != null && track.getArtist() != null) {
                artistPrefs.put(track.getArtist(), 
                    artistPrefs.getOrDefault(track.getArtist(), 0) + trackHistory.getPlayCount());
            }
        }
        return artistPrefs;
    }

    private Map<String, Float> analyzeMoodPreferences(List<TrackHistory> history) {
        Map<String, Float> moodPrefs = new HashMap<>();
        for (TrackHistory trackHistory : history) {
            Track track = trackHistory.getTrack();
            if (track != null && track.getMoodTags() != null && !track.getMoodTags().isEmpty()) {
                String mood = track.getMoodTags().get(0);
                moodPrefs.put(mood, 
                        moodPrefs.getOrDefault(mood, 0f) + trackHistory.getPlayCount());
            }
        }
        return moodPrefs;
    }

    private Map<String, Float> analyzeGenres(Track track) {
        Map<String, Float> genreScores = new HashMap<>();
        if (track.getGenres() != null) {
            for (String genre : track.getGenres()) {
                genreScores.put(genre, 1.0f / track.getGenres().size());
            }
        }
        return genreScores;
    }

    private String[] getGenreArray(Track track) {
        List<String> genres = track.getGenres() != null ? track.getGenres() : new ArrayList<>();
        return genres.toArray(new String[0]);
    }

    private boolean hasMatchingGenre(Track track1, Track track2) {
        List<String> genres1 = track1.getGenres();
        List<String> genres2 = track2.getGenres();

        return genres1 != null && genres2 != null && 
               !genres1.isEmpty() && !genres2.isEmpty() &&
               !Collections.disjoint(genres1, genres2);
    }

    private List<Track> filterByGenres(List<Track> tracks, List<String> targetGenres) {
        if (tracks == null || targetGenres == null || targetGenres.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> genreSet = new HashSet<>(targetGenres);
        return tracks.stream()
                    .filter(track -> track.getGenres() != null && 
                                   track.getGenres().stream()
                                        .anyMatch(genreSet::contains))
                    .collect(Collectors.toList());
    }

    // Add missing methods from AudioPlayerHelper
    public List<TrackHistory> getTrackHistoryList() {
        return audioPlayerHelper.getTrackHistoryList();
    }

    public int getArtistPlayCount(String artist) {
        return audioPlayerHelper.getArtistPlayCount(artist);
    }

    // Add mood tracking methods
    private Map<String, Float> moodAverages = new HashMap<>();
    
    private String getMood(Track track) {
        Map<String, Float> features = track.getAudioFeatures();
        if (features == null) return null;
        
        float valence = features.getOrDefault("valence", 0.5f);
        float energy = features.getOrDefault("energy", 0.5f);
        
        if (valence > 0.7 && energy > 0.7) return "Happy";
        if (valence < 0.3 && energy < 0.3) return "Sad";
        if (valence > 0.5 && energy < 0.5) return "Relaxed";
        if (valence < 0.5 && energy > 0.5) return "Angry";
        return "Neutral";
    }

    private List<Track> filterTracksByGenre(List<Track> tracks, List<String> preferredGenres) {
        if (tracks == null || preferredGenres == null || preferredGenres.isEmpty()) {
            return new ArrayList<>();
        }
        
        return tracks.stream()
                .filter(track -> track.getGenres() != null && 
                               !Collections.disjoint(track.getGenres(), preferredGenres))
                .collect(Collectors.toList());
    }

    private float calculateGenreSimilarity(Track track1, Track track2) {
        List<String> genres1 = track1.getGenres();
        List<String> genres2 = track2.getGenres();
        
        if (genres1 == null || genres2 == null || genres1.isEmpty() || genres2.isEmpty()) {
            return 0.0f;
        }

        Set<String> set1 = new HashSet<>(genres1);
        Set<String> set2 = new HashSet<>(genres2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return union.isEmpty() ? 0.0f : (float) intersection.size() / union.size();
    }

    private List<String> getGenres(Track track) {
        return track.getGenres() != null ? track.getGenres() : new ArrayList<>();
    }

    String[] getGenresArray(Track track) {
        List<String> genres = track.getGenres();
        return genres != null ? genres.toArray(new String[0]) : new String[0];
    }

    String getMoodFromTrack(Track track) {
        // Assuming Track has been updated to use List<String> for mood tags
        List<String> moodTags = track.getMoodTags();
        return moodTags != null && !moodTags.isEmpty() ? moodTags.get(0) : null;
    }

    /**
     * Convert RecommendationCallback to ApiDataProvider.TracksCallback
     */
    private my.edu.utar.bananamusic.providers.ApiDataProvider.TracksCallback adaptToApiCallback(final RecommendationCallback callback) {
        if (callback == null) return null;
        return new my.edu.utar.bananamusic.providers.ApiDataProvider.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onRecommendationsReady(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    /**
     * Fallback to generic recommendations when AI recommendations fail
     */
    private void fallbackToGenericRecommendations(int limit, RecommendationCallback callback) {
        // Get recommendations from the API provider
        apiDataProvider.getRecommendedTracks("all", limit, new ApiDataProvider.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onRecommendationsReady(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError("Failed to get generic recommendations: " + message);
            }
        });
    }

    /**
     * Convert RecommendationCallback to TracksCallback
     */
    private ApiDataProvider.TracksCallback adaptToTracksCallback(final RecommendationCallback callback) {
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
     * Convert RecommendationCallback to utils.callbacks.TracksCallback
     */
    private TracksCallback adaptToUtilsTracksCallback(final RecommendationCallback callback) {
        return new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onRecommendationsReady(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    public void getMixedPlaylistRecommendations(String playlistId, int limit, RecommendationCallback callback) {
        // Get tracks from the playlist and generate recommendations based on those
        playlistManager.getPlaylistTracks(playlistId, adaptToUtilsTracksCallback(new RecommendationCallback() {
            @Override
            public void onRecommendationsReady(List<Track> playlistTracks) {
                if (playlistTracks == null || playlistTracks.isEmpty()) {
                    callback.onError("Playlist is empty");
                    return;
                }

                // Select a random subset of tracks to seed recommendations
                List<Track> seedTracks = selectRandomElements(playlistTracks, 
                        Math.min(5, playlistTracks.size()));
                
                // Get seed track IDs
                List<String> seedIds = seedTracks.stream()
                        .map(Track::getId)
                        .collect(Collectors.toList());
                
                // Get recommendations
                apiDataProvider.getRecommendationsBasedOnTrackIds(
                        seedIds, 
                        limit, 
                        adaptToTracksCallback(new RecommendationCallback() {
                            @Override
                            public void onRecommendationsReady(List<Track> recommendations) {
                                if (recommendations == null || recommendations.isEmpty()) {
                                    callback.onError("Could not generate recommendations based on the playlist");
                                    return;
                                }
                                
                                // Filter out tracks that are already in the playlist
                                Set<String> playlistTrackIds = playlistTracks.stream()
                                        .map(Track::getId)
                                        .collect(Collectors.toSet());
                                
                                List<Track> filteredRecommendations = recommendations.stream()
                                        .filter(track -> !playlistTrackIds.contains(track.getId()))
                                        .limit(limit)
                                        .collect(Collectors.toList());
                                
                                if (filteredRecommendations.isEmpty()) {
                                    callback.onError("No recommendations available outside of playlist tracks");
                                    return;
                                }
                                
                                callback.onRecommendationsReady(filteredRecommendations);
                            }
                            
                            @Override
                            public void onError(String message) {
                                callback.onError("Error getting recommendations: " + message);
                            }
                        })
                );
            }
            
            @Override
            public void onError(String message) {
                callback.onError("Error getting playlist tracks: " + message);
            }
        }));
    }

    /**
     * Selects a random subset of elements from the input list
     * 
     * @param list The input list to select from
     * @param count Number of elements to select
     * @param <T> Type of elements in the list
     * @return A randomly selected subset of elements
     */
    private <T> List<T> selectRandomElements(List<T> list, int count) {
        if (list == null || list.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        
        if (count >= list.size()) {
            return new ArrayList<>(list);
        }
        
        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy);
        return copy.subList(0, count);
    }

    /**
     * Set the current mood for recommendations
     * @param mood The current mood
     */
    public void setCurrentMood(String mood) {
        this.currentMood = mood;
    }
}