package my.edu.utar.bananamusic.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class SpotifyGenres {
    private static final Set<String> VALID_GENRES = new HashSet<>(Arrays.asList(
        // Most common genres
        "pop", "rock", "hip-hop", "rap", "electronic", "dance", 
        "r-n-b", "jazz", "classical", "metal", "indie", "folk",
        
        // Electronic/Dance genres
        "house", "techno", "trance", "dubstep", "drum-and-bass", "edm",
        "electro", "electronic", "ambient", "disco",
        
        // Rock/Metal genres
        "alternative", "punk", "grunge", "hard-rock", "heavy-metal",
        "indie-rock", "progressive-rock", "rock-n-roll",
        
        // Pop genres
        "dance-pop", "indie-pop", "synth-pop", "k-pop", "j-pop",
        "pop-rock", "power-pop",
        
        // Hip-Hop/Urban genres
        "hip-hop", "rap", "trap", "grime", "urban",
        
        // R&B/Soul genres
        "soul", "funk", "motown", "neo-soul", "rhythm-and-blues",
        
        // Jazz/Blues genres
        "blues", "jazz", "bebop", "swing", "fusion", "big-band",
        
        // World/Regional genres
        "latin", "reggae", "afrobeat", "brazilian", "world-music",
        
        // Mood-based genres
        "chill", "happy", "sad", "party", "groove", "summer",
        
        // Activity-based genres
        "work-out", "gaming", "study", "sleep", "meditation",
        
        // Classical/Instrumental genres
        "classical", "instrumental", "opera", "piano", "orchestral",
        
        // Other genres
        "acoustic", "experimental", "singer-songwriter", "country",
        "new-age", "soundtrack"
    ));

    /**
     * Check if a genre is valid according to Spotify's genre list
     */
    public static boolean isValidGenre(String genre) {
        return genre != null && VALID_GENRES.contains(genre.toLowerCase());
    }

    /**
     * Get a random valid genre as a fallback
     */
    public static String getRandomGenre() {
        List<String> genres = new ArrayList<>(VALID_GENRES);
        return genres.get(new Random().nextInt(genres.size()));
    }

    /**
     * Get a similar genre as a fallback
     * Returns a genre that's similar in style/mood, or a random one if no match found
     */
    public static String getSimilarGenre(String originalGenre) {
        if (originalGenre == null) return getRandomGenre();
        
        switch (originalGenre.toLowerCase()) {
            case "ballad":
                return "pop";
            case "lofi":
                return "chill";
            case "instrumental-rock":
                return "rock";
            case "soft-rock":
                return "rock";
            case "instrumental-jazz":
                return "jazz";
            case "modern-classical":
                return "classical";
            default:
                return getRandomGenre();
        }
    }

    /**
     * Validate and clean a comma-separated list of genres
     * Returns a string with only valid genres, replacing invalid ones with similar alternatives
     */
    public static String validateGenres(String genreList) {
        if (genreList == null || genreList.isEmpty()) {
            return "pop"; // Default fallback
        }

        String[] genres = genreList.split(",");
        List<String> validGenres = new ArrayList<>();

        for (String genre : genres) {
            String trimmedGenre = genre.trim().toLowerCase();
            if (isValidGenre(trimmedGenre)) {
                validGenres.add(trimmedGenre);
            } else {
                // Try to get a similar genre instead
                validGenres.add(getSimilarGenre(trimmedGenre));
            }
        }

        // If no valid genres found, return a default genre
        if (validGenres.isEmpty()) {
            return "pop";
        }

        // Join the valid genres with commas
        return String.join(",", validGenres);
    }
} 