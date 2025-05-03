package my.edu.utar.bananamusic.utils;

import java.util.*;
import java.util.stream.Collectors;
import my.edu.utar.bananamusic.models.Track;

/**
 * Utility class for handling genre-related operations consistently across the application
 */
public class GenreUtils {
    private static final Map<String, List<String>> MOOD_GENRE_MAP = new HashMap<>();
    private static final Map<String, List<String>> GENRE_RELATED_MAP = new HashMap<>();
    
    static {
        // Comprehensive mood to genre mappings
        MOOD_GENRE_MAP.put("happy", Arrays.asList("pop", "dance", "disco", "funk", "house", "tropical"));
        MOOD_GENRE_MAP.put("sad", Arrays.asList("blues", "soul", "ambient", "folk", "indie", "alternative"));
        MOOD_GENRE_MAP.put("energetic", Arrays.asList("rock", "metal", "edm", "house", "drum-and-bass", "dubstep"));
        MOOD_GENRE_MAP.put("relaxed", Arrays.asList("jazz", "classical", "lounge", "acoustic", "ambient", "chill"));
        MOOD_GENRE_MAP.put("romantic", Arrays.asList("r&b", "soul", "jazz", "pop", "acoustic", "indie"));
        MOOD_GENRE_MAP.put("focused", Arrays.asList("classical", "ambient", "electronic", "instrumental", "minimal"));
        MOOD_GENRE_MAP.put("party", Arrays.asList("dance", "pop", "hip-hop", "electronic", "house", "disco"));
        
        // Related genres for better recommendations
        GENRE_RELATED_MAP.put("pop", Arrays.asList("dance-pop", "indie-pop", "synth-pop", "electropop"));
        GENRE_RELATED_MAP.put("rock", Arrays.asList("alternative", "indie-rock", "hard-rock", "classic-rock"));
        GENRE_RELATED_MAP.put("electronic", Arrays.asList("house", "techno", "trance", "edm"));
        // Add more related genres as needed
    }

    /**
     * Get genres associated with a mood
     */
    public static List<String> getMoodGenres(String mood) {
        List<String> genres = new ArrayList<>();
        
        switch (mood.toLowerCase()) {
            case "happy":
                genres.addAll(Arrays.asList(
                    "pop", "dance", "disco", "funk",
                    "happy", "party", "tropical house",
                    "k-pop", "j-pop", "power pop",
                    "dance pop", "electropop"
                ));
                break;
                
            case "sad":
                genres.addAll(Arrays.asList(
                    "blues", "soul", "folk",
                    "indie folk", "acoustic",
                    "sad", "melancholic",
                    "ambient", "piano",
                    "dark ambient", "slowcore"
                ));
                break;
                
            case "energetic":
                genres.addAll(Arrays.asList(
                    "rock", "metal", "punk",
                    "edm", "dubstep", "drum-and-bass",
                    "hardcore", "power metal",
                    "workout", "gym", "running",
                    "house", "techno"
                ));
                break;
                
            case "relaxed":
                genres.addAll(Arrays.asList(
                    "ambient", "chillout", "lounge",
                    "new age", "nature", "meditation",
                    "classical", "piano", "soft rock",
                    "bossa nova", "jazz", "smooth jazz"
                ));
                break;
                
            case "focused":
                genres.addAll(Arrays.asList(
                    "classical", "instrumental",
                    "post-rock", "ambient",
                    "minimal", "study", "focus",
                    "piano", "contemporary classical",
                    "background", "concentration"
                ));
                break;
                
            case "romantic":
                genres.addAll(Arrays.asList(
                    "r&b", "soul", "jazz",
                    "love songs", "ballad",
                    "acoustic", "soft rock",
                    "indie", "slow jazz",
                    "bossa nova", "smooth jazz"
                ));
                break;
                
            default:
                // Default to popular genres
                genres.addAll(Arrays.asList(
                    "pop", "rock", "hip hop",
                    "electronic", "dance"
                ));
        }
        
        return genres;
    }

    /**
     * Get related genres for a given genre
     */
    public static List<String> getRelatedGenres(String genre) {
        List<String> related = new ArrayList<>();
        
        switch (genre.toLowerCase()) {
            case "pop":
                related.addAll(Arrays.asList("dance pop", "electropop", "indie pop", "synth-pop"));
                break;
            case "rock":
                related.addAll(Arrays.asList("alternative rock", "indie rock", "hard rock", "classic rock"));
                break;
            case "electronic":
                related.addAll(Arrays.asList("dance", "house", "techno", "edm", "electronica"));
                break;
            case "classical":
                related.addAll(Arrays.asList("contemporary classical", "orchestral", "piano", "baroque"));
                break;
            case "jazz":
                related.addAll(Arrays.asList("smooth jazz", "fusion", "bebop", "swing", "big band"));
                break;
            case "hip hop":
                related.addAll(Arrays.asList("rap", "trap", "urban", "r&b"));
                break;
            case "metal":
                related.addAll(Arrays.asList("heavy metal", "power metal", "death metal", "rock"));
                break;
            case "indie":
                related.addAll(Arrays.asList("indie rock", "indie pop", "alternative", "indie folk"));
                break;
            case "ambient":
                related.addAll(Arrays.asList("chillout", "downtempo", "new age", "atmospheric"));
                break;
            case "folk":
                related.addAll(Arrays.asList("indie folk", "folk rock", "acoustic", "singer-songwriter"));
                break;
            default:
                related.add(genre); // Return the original genre if no relations defined
        }
        
        return related;
    }

    /**
     * Calculate genre similarity between two tracks using Jaccard similarity
     */
    public static double calculateGenreSimilarity(Track track1, Track track2) {
        if (track1 == null || track2 == null) return 0.0;
        
        List<String> genres1 = track1.getGenres();
        List<String> genres2 = track2.getGenres();
        
        if (genres1 == null || genres2 == null) return 0.0;
        
        Set<String> genreSet1 = new HashSet<>(genres1);
        Set<String> genreSet2 = new HashSet<>(genres2);
        
        if (genreSet1.isEmpty() || genreSet2.isEmpty()) return 0.0;
        
        Set<String> intersection = new HashSet<>(genreSet1);
        intersection.retainAll(genreSet2);
        
        Set<String> union = new HashSet<>(genreSet1);
        union.addAll(genreSet2);
        
        return (double) intersection.size() / union.size();
    }

    /**
     * Filter a list of tracks by target genres
     */
    public static List<Track> filterByGenres(List<Track> tracks, List<String> targetGenres) {
        if (tracks == null || targetGenres == null || targetGenres.isEmpty()) {
            return new ArrayList<>();
        }

        return tracks.stream()
            .filter(track -> {
                List<String> trackGenres = normalizeGenres(track.getGenres());
                return !Collections.disjoint(trackGenres, targetGenres);
            })
            .collect(Collectors.toList());
    }

    /**
     * Normalize genre strings for consistent comparison
     */
    private static List<String> normalizeGenres(List<String> genres) {
        if (genres == null) return new ArrayList<>();
        Set<String> normalizedGenres = new HashSet<>();
        for (String genre : genres) {
            if (genre != null) {
                normalizedGenres.add(formatGenreName(genre));
            }
        }
        return new ArrayList<>(normalizedGenres);
    }

    /**
     * Format a genre name for display
     */
    public static String formatGenreName(String genre) {
        if (genre == null || genre.isEmpty()) {
            return "";
        }
        
        StringBuilder formatted = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : genre.toCharArray()) {
            if (c == '-' || c == '_') {
                formatted.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                formatted.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                formatted.append(c);
            }
        }
        
        return formatted.toString();
    }
}