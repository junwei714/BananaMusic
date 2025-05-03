package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.Playlist;

public class MoodBasedRecommendationManager {
    private static final String TAG = "MoodRecommendationMgr";
    private static final int CACHE_SIZE = 50; // Number of playlists to cache per mood
    
    private final Context context;
    private final DeezerHelper deezerHelper;
    private final SpotifyHelper spotifyHelper;
    private final Map<String, List<String>> moodGenreMap;
    private final LruCache<String, List<Track>> playlistCache;
    private final Random random;
    
    // Define available moods
    public static final List<String> AVAILABLE_MOODS = List.of(
        "happy", "sad", "chill", "party", "focus", "romantic"
    );
    
    // Mood to genre mapping
    private static final Map<String, List<String>> MOOD_GENRES = new HashMap<String, List<String>>() {{
        put("happy", List.of("pop", "dance", "happy", "feel-good"));
        put("sad", List.of("sad", "emotional", "soul", "blues"));
        put("chill", List.of("chill", "ambient", "lofi", "relaxing"));
        put("party", List.of("party", "dance", "edm", "club"));
        put("focus", List.of("focus", "study", "instrumental", "classical"));
        put("romantic", List.of("love", "romantic", "r-n-b", "jazz"));
    }};
    
    public MoodBasedRecommendationManager(Context context) {
        this.context = context;
        this.deezerHelper = DeezerHelper.getInstance(context);
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.moodGenreMap = MOOD_GENRES;
        this.random = new Random();
        
        // Initialize cache
        this.playlistCache = new LruCache<>(CACHE_SIZE);
    }
    
    public interface RecommendationCallback {
        void onSuccess(List<Track> tracks, String mood);
        void onError(String message);
    }
    
    public void getRecommendationsForMood(String mood, int limit, RecommendationCallback callback) {
        // Validate mood
        if (!AVAILABLE_MOODS.contains(mood.toLowerCase())) {
            callback.onError("Invalid mood: " + mood);
            return;
        }
        
        // Check cache first
        List<Track> cachedTracks = playlistCache.get(mood);
        if (cachedTracks != null && !cachedTracks.isEmpty()) {
            // Return a random subset of cached tracks
            List<Track> randomSelection = getRandomSubset(cachedTracks, limit);
            callback.onSuccess(randomSelection, mood);
            return;
        }
        
        // Get recommendations from Deezer
        deezerHelper.searchPlaylists(mood, new DeezerHelper.DeezerPlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                if (playlist == null) {
                    fallbackToSpotify(mood, limit, callback);
                    return;
                }
                
                // Get tracks from the playlist
                deezerHelper.getPlaylistTracks(playlist.getId(), new DeezerHelper.DeezerTracksCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        if (tracks.isEmpty()) {
                            fallbackToSpotify(mood, limit, callback);
                            return;
                        }
                        
                        // Cache the tracks
                        playlistCache.put(mood, new ArrayList<>(tracks));
                        
                        // Return a random subset
                        List<Track> randomSelection = getRandomSubset(tracks, limit);
                        
                        // Add mood-specific recommendation reasons
                        for (Track track : randomSelection) {
                            track.setRecommendationReason(generateMoodReason(mood));
                        }
                        
                        callback.onSuccess(randomSelection, mood);
                    }
                    
                    @Override
                    public void onError(String message) {
                        fallbackToSpotify(mood, limit, callback);
                    }
                });
            }
            
            @Override
            public void onError(String message) {
                fallbackToSpotify(mood, limit, callback);
            }
        });
    }
    
    private void fallbackToSpotify(String mood, int limit, RecommendationCallback callback) {
        List<String> genres = moodGenreMap.getOrDefault(mood, Collections.singletonList(mood));
        
        spotifyHelper.getMoodBasedRecommendations(mood, limit * 2, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks.isEmpty()) {
                    callback.onError("No tracks found for mood: " + mood);
                    return;
                }
                
                // Cache the tracks
                playlistCache.put(mood, new ArrayList<>(tracks));
                
                // Return a random subset
                List<Track> randomSelection = getRandomSubset(tracks, limit);
                
                // Add mood-specific recommendation reasons
                for (Track track : randomSelection) {
                    track.setRecommendationReason(generateMoodReason(mood));
                }
                
                callback.onSuccess(randomSelection, mood);
            }
            
            @Override
            public void onError(String message) {
                callback.onError("Failed to get recommendations: " + message);
            }
        });
    }
    
    private List<Track> getRandomSubset(List<Track> tracks, int limit) {
        List<Track> shuffled = new ArrayList<>(tracks);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, Math.min(limit, shuffled.size()));
    }
    
    private String generateMoodReason(String mood) {
        List<String> reasons = new ArrayList<>();
        switch (mood.toLowerCase()) {
            case "happy":
                reasons.add("Perfect for lifting your spirits! 🌟");
                reasons.add("Get ready for some good vibes! 😊");
                reasons.add("Because you're in a happy mood! 🎉");
                break;
            case "sad":
                reasons.add("For when you need to feel all the feels 💭");
                reasons.add("Sometimes it's okay to embrace the blues 💙");
                reasons.add("Music that understands you 🌧");
                break;
            case "chill":
                reasons.add("Time to relax and unwind 😌");
                reasons.add("Perfect for your chill moments ✨");
                reasons.add("Kick back and enjoy 🌅");
                break;
            case "party":
                reasons.add("Let's get this party started! 🎉");
                reasons.add("Time to dance! 💃");
                reasons.add("Perfect party vibes 🎈");
                break;
            case "focus":
                reasons.add("Keep your concentration flowing 🎯");
                reasons.add("Music to boost your productivity 📚");
                reasons.add("Perfect for deep work 💡");
                break;
            case "romantic":
                reasons.add("Set the mood for romance 💝");
                reasons.add("Love is in the air 💕");
                reasons.add("Perfect for romantic moments ❤️");
                break;
            default:
                reasons.add("Specially selected for your current mood 🎵");
        }
        return reasons.get(random.nextInt(reasons.size()));
    }
    
    public void clearCache() {
        playlistCache.evictAll();
    }
    
    public List<String> getAvailableMoods() {
        return new ArrayList<>(AVAILABLE_MOODS);
    }
} 