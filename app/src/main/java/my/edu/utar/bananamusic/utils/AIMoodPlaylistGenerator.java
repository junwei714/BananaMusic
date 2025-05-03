package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.TensorFlowHelper;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AI-based mood playlist generator
 * This class uses mood detection and track analysis to create personalized playlists
 */
public class AIMoodPlaylistGenerator {
    private static final String TAG = "AIMoodPlaylistGenerator";
    
    // Callback interface for playlist generation
    public interface PlaylistCallback {
        void onPlaylistGenerated(Playlist playlist);
        void onError(String errorMessage);
    }
    
    private final Context context;
    private final TensorFlowHelper tensorFlowHelper;
    private final ApiDataProvider dataProvider;
    private final SpotifyHelper spotifyHelper;
    private final Random random = new Random();
    
    // Executor for background tasks
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
    
    // Map of mood traits to audio characteristics
    private static final Map<String, AudioCharacteristics> MOOD_CHARACTERISTICS = new HashMap<>();
    static {
        // Happy mood - upbeat, major key, medium-high energy
        MOOD_CHARACTERISTICS.put("Happy", new AudioCharacteristics(
                0.7f, 0.9f,  // Energy range
                0.6f, 0.9f,  // Valence range (positivity)
                90, 130,     // Tempo range (BPM)
                true,        // Major key
                0.4f, 0.7f   // Danceability range
        ));
        
        // Sad mood - slow tempo, minor key, low energy
        MOOD_CHARACTERISTICS.put("Sad", new AudioCharacteristics(
                0.1f, 0.4f,  // Energy range
                0.1f, 0.4f,  // Valence range (positivity)
                60, 90,      // Tempo range (BPM)
                false,       // Minor key
                0.2f, 0.5f   // Danceability range
        ));
        
        // Energetic mood - high tempo, high energy
        MOOD_CHARACTERISTICS.put("Energetic", new AudioCharacteristics(
                0.8f, 1.0f,  // Energy range
                0.5f, 0.9f,  // Valence range (positivity) 
                120, 160,    // Tempo range (BPM)
                true,        // Major key
                0.6f, 0.9f   // Danceability range
        ));
        
        // Relaxed mood - slow tempo, medium-low energy
        MOOD_CHARACTERISTICS.put("Relaxed", new AudioCharacteristics(
                0.1f, 0.5f,  // Energy range
                0.3f, 0.7f,  // Valence range (positivity)
                60, 90,      // Tempo range (BPM)
                true,        // Either key
                0.2f, 0.5f   // Danceability range
        ));
    }
    
    // Add a cache for generated playlists
    private final Map<String, Playlist> playlistCache = new HashMap<>();
    
    /**
     * Constructor
     * @param context Application context
     */
    public AIMoodPlaylistGenerator(Context context) {
        this.context = context.getApplicationContext();
        this.tensorFlowHelper = TensorFlowHelper.getInstance(context);
        this.dataProvider = ApiDataProvider.getInstance(context);
        this.spotifyHelper = SpotifyHelper.getInstance(context);
    }
    
    /**
     * Asynchronously generates a playlist from text input
     */
    public void generatePlaylistFromTextAsync(String text, int intensity, final PlaylistCallback callback) {
        Log.d(TAG, "Generating playlist from text: " + text + " with intensity: " + intensity);
        
        // Run on background thread
        new Thread(() -> {
            try {
                Playlist playlist = generatePlaylistFromText(text, intensity);
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onPlaylistGenerated(playlist);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error generating playlist: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onError("Failed to generate playlist: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Generate a playlist from text input
     */
    public Playlist generatePlaylistFromText(String text, int intensity) {
        // Determine mood from text
        String mood = tensorFlowHelper.detectMood(text);
        int moodConfidence = 75; // Default confidence
        
        Log.d(TAG, "Detected mood: " + mood + " with confidence: " + moodConfidence + "%");
        
        // Generate playlist for the detected mood
        return generatePlaylistForMood(mood, intensity);
    }
    
    /**
     * Generate a playlist for a predefined mood (overloaded for backward compatibility)
     * @param mood The selected mood
     * @param callback Callback to receive the generated playlist
     */
    public void generatePlaylistForMoodAsync(String mood, OnPlaylistGeneratedListener callback) {
        // Wrap the old listener interface with the new callback interface
        PlaylistCallback wrapperCallback = new PlaylistCallback() {
            @Override
            public void onPlaylistGenerated(Playlist playlist) {
                callback.onPlaylistGenerated(playlist);
            }
            
            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        };
        
        generatePlaylistForMoodAsync(mood, 50, wrapperCallback);
    }
    
    /**
     * Asynchronously generates a playlist for a specific mood
     */
    public void generatePlaylistForMoodAsync(String mood, int intensity, final PlaylistCallback callback) {
        Log.d(TAG, "Generating playlist for mood: " + mood + " with intensity: " + intensity);
        
        // Run on background thread
        new Thread(() -> {
            try {
                Playlist playlist = generatePlaylistForMood(mood, intensity);
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onPlaylistGenerated(playlist);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error generating playlist: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onError("Failed to generate playlist: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Generate a playlist for a predefined mood (overloaded for backward compatibility)
     * @param mood The selected mood
     * @return A generated playlist
     */
    public Playlist generatePlaylistForMood(String mood) {
        return generatePlaylistForMood(mood, null, 50); // Default intensity
    }
    
    /**
     * Generate a playlist for a mood
     * @param mood The detected or selected mood
     * @param intensity Intensity of the mood (0-100)
     * @return A generated playlist
     */
    public Playlist generatePlaylistForMood(String mood, int intensity) {
        return generatePlaylistForMood(mood, null, intensity);
    }
    
    /**
     * Generate a playlist for a mood with optional custom description and intensity
     * @param mood The detected or selected mood
     * @param customDescription Optional custom mood description
     * @param intensity Intensity of the mood (0-100)
     * @return A generated playlist
     */
    private Playlist generatePlaylistForMood(String mood, String customDescription, int intensity) {
        Log.d(TAG, "Generating playlist for mood: " + mood + " with intensity: " + intensity);
        
        // Normalize intensity to 0-100 range
        intensity = Math.max(0, Math.min(100, intensity));
        float normalizedIntensity = intensity / 100f;
        
        // Generate playlist name
        String intensityDescriptor = getIntensityDescriptor(normalizedIntensity);
        String playlistName = generatePlaylistName(mood, intensityDescriptor);
        
        // Create a new playlist
        Playlist playlist = new Playlist();
        playlist.setName(playlistName);
        
        // Set appropriate cover image based on mood
        setCoverImageForMood(playlist, mood);
        
        // Find tracks that match the mood characteristics
        // Use Spotify API to get mood-based recommendations
        final CountDownLatch latch = new CountDownLatch(1);
        final List<Track> matchingTracks = new ArrayList<>();
        
        // Get recommendations from Spotify based on mood
        spotifyHelper.getMoodBasedRecommendations(mood, intensity, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                // Add all recommended tracks to our playlist
                matchingTracks.addAll(tracks);
                latch.countDown();
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error getting recommendations from Spotify: " + errorMessage);
                latch.countDown();
            }
        });
        
        try {
            // Wait for the API call to complete (max 10 seconds)
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for Spotify API", e);
            Thread.currentThread().interrupt();
        }
        
        // If no tracks received from Spotify, add fallback tracks
        if (matchingTracks.isEmpty()) {
            Log.d(TAG, "No tracks from Spotify, using fallback mechanism");
            // Execute a synchronous call to get some popular tracks
            try {
                final CountDownLatch fallbackLatch = new CountDownLatch(1);
                spotifyHelper.searchTracks("top " + mood, new SpotifyHelper.SpotifyCallback() {
                    @Override
                    public void onSuccess(String jsonResponse) {
                        try {
                            // Parse the response to get tracks
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            if (jsonObject.has("tracks") && jsonObject.getJSONObject("tracks").has("items")) {
                                JSONArray items = jsonObject.getJSONObject("tracks").getJSONArray("items");
                                
                                for (int i = 0; i < Math.min(items.length(), 20); i++) {
                                    JSONObject trackJson = items.getJSONObject(i);
                                    
                                    String id = trackJson.getString("id");
                                    String name = trackJson.getString("name");
                                    
                                    // Get preview URL
                                    String previewUrl = trackJson.optString("preview_url", null);
                                    
                                    // Get album info
                                    String albumName = "Unknown Album";
                                    String albumArt = null;
                                    if (trackJson.has("album")) {
                                        JSONObject albumJson = trackJson.getJSONObject("album");
                                        albumName = albumJson.getString("name");
                                        
                                        // Get album art
                                        if (albumJson.has("images") && albumJson.getJSONArray("images").length() > 0) {
                                            albumArt = albumJson.getJSONArray("images").getJSONObject(0).getString("url");
                                        }
                                    }
                                    
                                    // Get artist info
                                    String artist = "Unknown Artist";
                                    if (trackJson.has("artists") && trackJson.getJSONArray("artists").length() > 0) {
                                        artist = trackJson.getJSONArray("artists").getJSONObject(0).getString("name");
                                    }
                                    
                                    // Get duration in milliseconds
                                    int duration = trackJson.optInt("duration_ms", 0);
                                    
                                    // Create track object
                                    Track track = new Track(
                                        id,
                                        name,
                                        artist,
                                        albumName,
                                        albumArt,
                                        previewUrl,
                                        duration,
                                        id  // Use the same ID as spotifyId
                                    );
                                    
                                    matchingTracks.add(track);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing fallback tracks: " + e.getMessage());
                        } finally {
                            fallbackLatch.countDown();
                        }
                    }
                    
                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "Error getting fallback tracks: " + errorMessage);
                        fallbackLatch.countDown();
                    }
                });
                
                fallbackLatch.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                Log.e(TAG, "Error with fallback track mechanism: " + e.getMessage());
            }
        }
        
        // Add tracks to playlist
        if (matchingTracks.isEmpty()) {
            // If still no tracks, create a placeholder description
            playlist.setDescription("A " + intensityDescriptor.toLowerCase() + " " + mood.toLowerCase() + 
                " playlist. No matching tracks found at the moment. Try again later.");
        } else {
            // Add a description with the number of tracks
            playlist.setDescription("A " + intensityDescriptor.toLowerCase() + " " + mood.toLowerCase() + 
                " playlist with " + matchingTracks.size() + " tracks.");
            
            // Add track IDs to the playlist
            for (Track track : matchingTracks) {
                playlist.addTrack(track.getTrackId());
            }
            
            // Store the full track objects for immediate playback
            playlist.setTracks(matchingTracks);
        }
        
        Log.d(TAG, "Generated playlist with " + matchingTracks.size() + " tracks");
        return playlist;
    }
    
    /**
     * Get intensity descriptor based on intensity value
     * @param normalizedIntensity Intensity from 0-1
     * @return String descriptor of the intensity
     */
    private String getIntensityDescriptor(float normalizedIntensity) {
        if (normalizedIntensity < 0.3f) {
            return "Gentle";
        } else if (normalizedIntensity < 0.6f) {
            return "Moderate";
        } else if (normalizedIntensity < 0.8f) {
            return "Strong";  
        } else {
            return "Intense";
        }
    }
    
    /**
     * Get a full intensity description for the playlist description
     * @param normalizedIntensity Intensity from 0-1
     * @param mood The mood
     * @return Intensity description
     */
    private String getIntensityDescription(float normalizedIntensity, String mood) {
        String baseDescription = "A " + getIntensityDescriptor(normalizedIntensity).toLowerCase() + " " + 
                                mood.toLowerCase() + " playlist";
        
        if (normalizedIntensity > 0.7f) {
            return baseDescription + " to maximize your " + mood.toLowerCase() + " experience.";
        } else if (normalizedIntensity > 0.4f) {
            return baseDescription + " for your " + mood.toLowerCase() + " moments.";
        } else {
            return baseDescription + " with a subtle " + mood.toLowerCase() + " vibe.";
        }
    }
    
    /**
     * Get tracks that match a specific mood with intensity
     * @param mood The mood to match
     * @param intensity Intensity from 0-1
     * @return List of matching tracks
     */
    private List<Track> getTracksForMood(String mood, float intensity) {
        final List<Track> resultTracks = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        
        // Get mood matching tracks from Spotify
        SpotifyHelper spotifyHelper = SpotifyHelper.getInstance(context);
        spotifyHelper.getMoodBasedRecommendations(mood, (int)(intensity * 100), new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                resultTracks.addAll(tracks);
                latch.countDown();
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting mood tracks: " + message);
                latch.countDown();
            }
        });
        
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for track results: " + e.getMessage());
        }
        
        // If no tracks were found, try using Deezer API
        if (resultTracks.isEmpty()) {
            final CountDownLatch deezerLatch = new CountDownLatch(1);
            DeezerHelper deezerHelper = DeezerHelper.getInstance(context);
            deezerHelper.getMoodTracks(mapMoodToDeezerParam(mood), new DeezerHelper.DeezerTracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        resultTracks.addAll(tracks);
                    } else {
                        Log.e(TAG, "No tracks found for mood: " + mood);
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Failed to get mood tracks: " + message);
                }
            });
            
            try {
                deezerLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for Deezer results: " + e.getMessage());
            }
        }
        
        return resultTracks;
    }
    
    /**
     * Adjust audio characteristics based on mood intensity
     * @param baseCharacteristics Base characteristics for the mood
     * @param intensity Intensity from 0-1
     * @return Adjusted characteristics
     */
    private AudioCharacteristics adjustCharacteristicsForIntensity(AudioCharacteristics baseCharacteristics, float intensity) {
        // Make a copy of the base characteristics
        AudioCharacteristics adjusted = new AudioCharacteristics(
                baseCharacteristics.minEnergy,
                baseCharacteristics.maxEnergy,
                baseCharacteristics.minValence,
                baseCharacteristics.maxValence,
                baseCharacteristics.minTempo,
                baseCharacteristics.maxTempo,
                baseCharacteristics.isMajorKey,
                baseCharacteristics.minDanceability,
                baseCharacteristics.maxDanceability
        );
        
        // Adjust the range based on intensity
        if (intensity < 0.3f) {
            // Low intensity - narrow the ranges toward the middle
            adjusted.minEnergy = adjusted.minEnergy * 0.7f + 0.15f;
            adjusted.maxEnergy = adjusted.maxEnergy * 0.7f + 0.15f;
            adjusted.minValence = adjusted.minValence * 0.7f + 0.15f;
            adjusted.maxValence = adjusted.maxValence * 0.7f + 0.15f;
            adjusted.minTempo = (int) (adjusted.minTempo * 0.7f + 30);
            adjusted.maxTempo = (int) (adjusted.maxTempo * 0.7f + 30);
            adjusted.requireMajorKey = false; // Be more flexible with key
        } else if (intensity > 0.7f) {
            // High intensity - push the extremes further
            adjusted.minEnergy = Math.max(0, adjusted.minEnergy * 1.2f - 0.1f);
            adjusted.maxEnergy = Math.min(1, adjusted.maxEnergy * 1.2f);
            adjusted.minValence = Math.max(0, adjusted.minValence * 1.2f - 0.1f);
            adjusted.maxValence = Math.min(1, adjusted.maxValence * 1.2f);
            adjusted.minTempo = (int) Math.max(60, adjusted.minTempo * 1.1f);
            adjusted.maxTempo = (int) Math.min(180, adjusted.maxTempo * 1.1f);
            adjusted.requireMajorKey = true; // Be more strict with key
        }
        // Middle intensity - use base characteristics as is
        
        return adjusted;
    }
    
    /**
     * Simulate audio analysis matching for a track
     * In a real app, this would use actual audio features from a music API
     * @param track The track to analyze
     * @param characteristics Target audio characteristics
     * @return true if the track matches the mood
     */
    private boolean simulateAudioAnalysisMatch(Track track, AudioCharacteristics characteristics) {
        // This is a simulation - in a real app, you would use actual audio features
        // For demo, we'll use the track title and artist to simulate a match
        
        String title = track.getTitle().toLowerCase();
        String artist = track.getArtist().toLowerCase();
        
        // Simplified simulation based on the track title/artist and target characteristics
        float energy = 0.5f;
        float valence = 0.5f;
        int tempo = 100;
        boolean isMajorKey = true;
        float danceability = 0.5f;
        
        // Adjust simulated values based on track properties
        // This is very simplified - real analysis would be much more sophisticated
        
        // Check for high energy words
        if (title.contains("party") || title.contains("rock") || title.contains("high") || 
            title.contains("power") || title.contains("energy") || title.contains("dance")) {
            energy += 0.3f;
            tempo += 20;
            danceability += 0.2f;
        }
        
        // Check for low energy words
        if (title.contains("slow") || title.contains("ballad") || title.contains("sad") || 
            title.contains("quiet") || title.contains("smooth")) {
            energy -= 0.3f;
            tempo -= 20;
            danceability -= 0.1f;
        }
        
        // Check for positive words
        if (title.contains("happy") || title.contains("joy") || title.contains("good") || 
            title.contains("love") || title.contains("sweet")) {
            valence += 0.3f;
            isMajorKey = true;
        }
        
        // Check for negative words
        if (title.contains("sad") || title.contains("blue") || title.contains("cry") || 
            title.contains("hurt") || title.contains("pain")) {
            valence -= 0.3f;
            isMajorKey = false;
        }
        
        // Ensure values are in valid range
        energy = Math.max(0.0f, Math.min(1.0f, energy));
        valence = Math.max(0.0f, Math.min(1.0f, valence));
        tempo = Math.max(60, Math.min(180, tempo));
        danceability = Math.max(0.0f, Math.min(1.0f, danceability));
        
        // Check if the track matches the target characteristics
        boolean energyMatch = energy >= characteristics.minEnergy && energy <= characteristics.maxEnergy;
        boolean valenceMatch = valence >= characteristics.minValence && valence <= characteristics.maxValence;
        boolean tempoMatch = tempo >= characteristics.minTempo && tempo <= characteristics.maxTempo;
        boolean keyMatch = !characteristics.requireMajorKey || isMajorKey == characteristics.isMajorKey;
        boolean danceabilityMatch = danceability >= characteristics.minDanceability && danceability <= characteristics.maxDanceability;
        
        // Return true if most characteristics match
        int matches = 0;
        if (energyMatch) matches++;
        if (valenceMatch) matches++;
        if (tempoMatch) matches++;
        if (keyMatch) matches++;
        if (danceabilityMatch) matches++;
        
        return matches >= 3;  // At least 3 out of 5 characteristics should match
    }
    
    /**
     * Set a cover image for the playlist based on mood
     * @param playlist The playlist to update
     * @param mood The mood
     */
    private void setCoverImageForMood(Playlist playlist, String mood) {
        // Set a cover image based on the mood
        switch (mood.toLowerCase()) {
            case "happy":
                playlist.setCoverImageUrl("https://example.com/images/happy_mood.jpg");
                break;
            case "sad":
                playlist.setCoverImageUrl("https://example.com/images/sad_mood.jpg");
                break;
            case "energetic":
                playlist.setCoverImageUrl("https://example.com/images/energetic_mood.jpg");
                break;
            case "relaxed":
                playlist.setCoverImageUrl("https://example.com/images/relaxed_mood.jpg");
                break;
            default:
                playlist.setCoverImageUrl("https://example.com/images/default_mood.jpg");
                break;
        }
    }
    
    /**
     * Simulate audio analysis for a track
     * @param track Track to analyze
     * @return Simulated audio characteristics
     */
    private AudioCharacteristics simulateAudioAnalysisForTrack(Track track) {
        // This would be replaced by actual audio analysis in a real implementation
        // For demo purposes, we'll use randomized values with some bias based on track properties
        Random random = new Random(track.getTrackId().hashCode()); // Use track ID as seed for consistency
        
        // Default characteristics
        float energy = 0.5f + (random.nextFloat() * 0.5f - 0.25f);
        float valence = 0.5f + (random.nextFloat() * 0.5f - 0.25f);
        float danceability = 0.5f + (random.nextFloat() * 0.5f - 0.25f);
        float acousticness = 0.5f + (random.nextFloat() * 0.5f - 0.25f);
        int tempo = 100 + random.nextInt(40);
        int instrumentalness = random.nextInt(100);
        boolean isMajorKey = random.nextBoolean();
        
        // Bias based on track name
        String trackName = track.getTitle().toLowerCase();
        if (trackName.contains("happy") || trackName.contains("joy") || trackName.contains("sun")) {
            valence += 0.2f;
            energy += 0.1f;
            isMajorKey = true;
        } else if (trackName.contains("sad") || trackName.contains("blue") || trackName.contains("rain")) {
            valence -= 0.2f;
            energy -= 0.1f;
            isMajorKey = false;
        } else if (trackName.contains("dance") || trackName.contains("party")) {
            danceability += 0.3f;
            energy += 0.2f;
            tempo += 20;
        } else if (trackName.contains("slow") || trackName.contains("chill")) {
            tempo -= 20;
            energy -= 0.2f;
        }
        
        // Clamp values to valid ranges
        energy = Math.max(0, Math.min(1, energy));
        valence = Math.max(0, Math.min(1, valence));
        danceability = Math.max(0, Math.min(1, danceability));
        acousticness = Math.max(0, Math.min(1, acousticness));
        tempo = Math.max(60, Math.min(200, tempo));
        
        return new AudioCharacteristics(
                energy - 0.1f, energy + 0.1f,
                valence - 0.1f, valence + 0.1f,
                tempo - 10, tempo + 10,
                isMajorKey,
                danceability - 0.1f, danceability + 0.1f
        );
    }
    
    /**
     * Callback interface for async playlist generation
     */
    public interface OnPlaylistGeneratedListener {
        void onPlaylistGenerated(Playlist playlist);
        void onError(String errorMessage);
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
     * Calculate match score between track characteristics and target characteristics
     * @param track Track characteristics
     * @param target Target characteristics for the mood
     * @return Match score (higher is better)
     */
    private double calculateMatchScore(AudioCharacteristics track, AudioCharacteristics target) {
        double score = 0.0;
        
        // Energy match (0-1)
        double energyMatch = calculateRangeOverlap(
                track.minEnergy, track.maxEnergy,
                target.minEnergy, target.maxEnergy);
        
        // Valence match (0-1)
        double valenceMatch = calculateRangeOverlap(
                track.minValence, track.maxValence,
                target.minValence, target.maxValence);
        
        // Tempo match (normalized to 0-1)
        double tempoRangeMatch = calculateRangeOverlap(
                track.minTempo, track.maxTempo,
                target.minTempo, target.maxTempo) / 200.0;
        
        // Danceability match (0-1)
        double danceabilityMatch = calculateRangeOverlap(
                track.minDanceability, track.maxDanceability,
                target.minDanceability, target.maxDanceability);
        
        // Key type match (binary)
        double keyMatch = (track.isMajorKey == target.isMajorKey) ? 1.0 : 0.0;
        
        // Weight the different factors (adjust these weights based on testing)
        score += energyMatch * 0.3;
        score += valenceMatch * 0.3;
        score += tempoRangeMatch * 0.15;
        score += danceabilityMatch * 0.15;
        score += keyMatch * 0.1;
        
        return score;
    }
    
    /**
     * Calculate the overlap percentage between two ranges
     * @return Overlap value from 0-1
     */
    private double calculateRangeOverlap(float min1, float max1, float min2, float max2) {
        float overlapStart = Math.max(min1, min2);
        float overlapEnd = Math.min(max1, max2);
        
        if (overlapEnd < overlapStart) {
            return 0.0; // No overlap
        }
        
        float overlap = overlapEnd - overlapStart;
        float range1 = max1 - min1;
        float range2 = max2 - min2;
        
        if (range1 == 0 || range2 == 0) {
            return 0.0; // Prevent division by zero
        }
        
        // Return average percentage of overlap relative to both ranges
        return (overlap / range1 + overlap / range2) / 2.0;
    }
    
    /**
     * Generate playlist name based on mood and intensity
     */
    private String generatePlaylistName(String mood, String intensityDescriptor) {
        // Combine mood and intensity for name
        switch (mood) {
            case "Happy":
                return "Your " + intensityDescriptor + " Happy Vibes";
            case "Sad":
                return "Your " + intensityDescriptor + " Melancholy Moments";
            case "Energetic":
                return "Your " + intensityDescriptor + " Energy Boost";
            case "Relaxed":
                return "Your " + intensityDescriptor + " Chill Session";
            default:
                return "Your " + intensityDescriptor + " " + mood + " Mix";
        }
    }
    
    /**
     * Get audio characteristics adjusted for a specific mood and intensity
     */
    private AudioCharacteristics getMoodCharacteristics(String mood, float normalizedIntensity) {
        AudioCharacteristics characteristics = new AudioCharacteristics();
        
        // Base values adjusted by intensity
        switch (mood) {
            case "Happy":
                characteristics.minEnergy = 0.6f + (normalizedIntensity * 0.3f);
                characteristics.maxEnergy = 0.8f + (normalizedIntensity * 0.2f);
                characteristics.minValence = 0.6f + (normalizedIntensity * 0.3f);
                characteristics.maxValence = 0.8f + (normalizedIntensity * 0.2f);
                characteristics.minTempo = (int)(100 + (normalizedIntensity * 30));
                characteristics.maxTempo = (int)(140 + (normalizedIntensity * 40));
                characteristics.isMajorKey = true;
                characteristics.minDanceability = 0.5f + (normalizedIntensity * 0.2f);
                characteristics.maxDanceability = 0.7f + (normalizedIntensity * 0.3f);
                break;
                
            case "Sad":
                characteristics.minEnergy = 0.1f + (normalizedIntensity * 0.2f);
                characteristics.maxEnergy = 0.4f + (normalizedIntensity * 0.1f);
                characteristics.minValence = 0.1f;
                characteristics.maxValence = 0.3f + (normalizedIntensity * 0.1f);
                characteristics.minTempo = 60;
                characteristics.maxTempo = (int)(100 - (normalizedIntensity * 20));
                characteristics.isMajorKey = false;
                characteristics.minDanceability = 0.1f + (normalizedIntensity * 0.1f);
                characteristics.maxDanceability = 0.3f + (normalizedIntensity * 0.1f);
                break;
                
            case "Energetic":
                characteristics.minEnergy = 0.7f + (normalizedIntensity * 0.2f);
                characteristics.maxEnergy = 0.9f + (normalizedIntensity * 0.1f);
                characteristics.minValence = 0.4f + (normalizedIntensity * 0.2f);
                characteristics.maxValence = 0.7f + (normalizedIntensity * 0.3f);
                characteristics.minTempo = (int)(120 + (normalizedIntensity * 40));
                characteristics.maxTempo = (int)(180 + (normalizedIntensity * 20));
                characteristics.isMajorKey = true;
                characteristics.minDanceability = 0.6f + (normalizedIntensity * 0.3f);
                characteristics.maxDanceability = 0.8f + (normalizedIntensity * 0.2f);
                break;
                
            case "Relaxed":
                characteristics.minEnergy = 0.1f + (normalizedIntensity * 0.1f);
                characteristics.maxEnergy = 0.4f + (normalizedIntensity * 0.1f);
                characteristics.minValence = 0.3f + (normalizedIntensity * 0.2f);
                characteristics.maxValence = 0.6f + (normalizedIntensity * 0.1f);
                characteristics.minTempo = 60 + (int)(normalizedIntensity * 10);
                characteristics.maxTempo = (int)(90 - (normalizedIntensity * 20));
                characteristics.isMajorKey = true;
                characteristics.minDanceability = 0.1f + (normalizedIntensity * 0.1f);
                characteristics.maxDanceability = 0.4f + (normalizedIntensity * 0.1f);
                break;
                
            default:
                // Default to balanced characteristics
                characteristics.minValence = 0.3f;
                characteristics.maxValence = 0.7f;
                characteristics.minEnergy = 0.3f;
                characteristics.maxEnergy = 0.7f;
                characteristics.minDanceability = 0.3f;
                characteristics.maxDanceability = 0.7f;
                characteristics.minTempo = 80;
                characteristics.maxTempo = 130;
                break;
        }
        
        return characteristics;
    }
    
    /**
     * Inner class to hold audio characteristics for mood detection and playlist generation
     */
    private static class AudioCharacteristics {
        float minEnergy;
        float maxEnergy;
        float minValence;
        float maxValence;
        int minTempo;
        int maxTempo;
        boolean isMajorKey;
        boolean requireMajorKey;
        float minDanceability;
        float maxDanceability;
        
        /**
         * Default constructor
         */
        public AudioCharacteristics() {
            this.minEnergy = 0.5f;
            this.maxEnergy = 0.8f;
            this.minValence = 0.5f;
            this.maxValence = 0.8f;
            this.minTempo = 100;
            this.maxTempo = 140;
            this.isMajorKey = true;
            this.requireMajorKey = true;
            this.minDanceability = 0.5f;
            this.maxDanceability = 0.8f;
        }
        
        /**
         * Constructor with all parameters
         */
        public AudioCharacteristics(float minEnergy, float maxEnergy, float minValence, float maxValence,
                                  int minTempo, int maxTempo, boolean isMajorKey,
                                  float minDanceability, float maxDanceability) {
            this.minEnergy = minEnergy;
            this.maxEnergy = maxEnergy;
            this.minValence = minValence;
            this.maxValence = maxValence;
            this.minTempo = minTempo;
            this.maxTempo = maxTempo;
            this.isMajorKey = isMajorKey;
            this.requireMajorKey = true;
            this.minDanceability = minDanceability;
            this.maxDanceability = maxDanceability;
        }
    }

    /**
     * Generate a playlist using Spotify API for a specific mood
     * This method uses real recommendations instead of dummy data
     */
    public void generatePlaylistWithSpotifyAsync(String mood, int intensity, final PlaylistCallback callback) {
        Log.d(TAG, "Generating Spotify playlist for mood: " + mood + " with intensity: " + intensity);
        
        // Get intensity descriptor for playlist name
        String intensityDescriptor = getIntensityDescriptor(intensity);
        
        // Generate a creative playlist name based on mood and intensity
        String playlistName = generatePlaylistName(mood, intensityDescriptor);
        
        // Get Spotify recommendations based on mood and intensity
        spotifyHelper.getMoodBasedRecommendations(mood, intensity, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                // Create the playlist with the tracks
                Playlist playlist = new Playlist(playlistName, tracks);
                
                // Set mood and cover image
                playlist.setMood(mood);
                setPlaylistCoverImage(playlist, mood);
                
                // Return the playlist on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onPlaylistGenerated(playlist);
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Spotify recommendations error: " + errorMessage);
                
                // Fallback to local generation if Spotify fails
                Log.d(TAG, "Falling back to local playlist generation");
                generatePlaylistForMoodAsync(mood, intensity, callback);
            }
        });
    }

    /**
     * Simulate audio analysis for a track (alias to simulateAudioAnalysisForTrack for backward compatibility)
     */
    private AudioCharacteristics simulateAudioAnalysis(Track track) {
        return simulateAudioAnalysisForTrack(track);
    }

    /**
     * Set playlist cover image based on mood
     */
    private void setPlaylistCoverImage(Playlist playlist, String mood) {
        setCoverImageForMood(playlist, mood);
    }

    /**
     * Maps a mood string to a parameter suitable for Deezer API
     * @param mood The mood string
     * @return The Deezer-compatible query parameter
     */
    private String mapMoodToDeezerParam(String mood) {
        if (mood == null) return "happy";
        
        switch (mood.toLowerCase()) {
            case "happy": return "happy";
            case "sad": return "sad";
            case "energetic": return "energetic";
            case "relaxed": return "chill";
            case "romantic": return "romantic";
            case "focus": return "focus";
            default: return mood.toLowerCase();
        }
    }
} 