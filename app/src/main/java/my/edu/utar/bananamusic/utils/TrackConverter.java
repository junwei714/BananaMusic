package my.edu.utar.bananamusic.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to help migrate from model.Track to models.Track.
 * This helps during the transition phase where both Track classes might be in use.
 * 
 * This class can eventually be removed once the migration to models.Track is complete.
 */
public class TrackConverter {

    /**
     * Convert from old model.Track to new models.Track
     * @param oldTrack the old Track model
     * @return the new Track model with data copied from the old model
     */
    public static my.edu.utar.bananamusic.models.Track convertToNewTrack(my.edu.utar.bananamusic.model.Track oldTrack) {
        if (oldTrack == null) return null;
        
        // Handle any null values safely
        String id = oldTrack.getId() != null ? oldTrack.getId() : "";
        String title = oldTrack.getTitle() != null ? oldTrack.getTitle() : "";
        String artist = oldTrack.getArtist() != null ? oldTrack.getArtist() : "";
        String album = oldTrack.getAlbum() != null ? oldTrack.getAlbum() : "";
        String albumArt = oldTrack.getAlbumArt() != null ? oldTrack.getAlbumArt() : "";
        String previewUrl = oldTrack.getPreviewUrl() != null ? oldTrack.getPreviewUrl() : "";
        int durationMs = oldTrack.getDurationMs();
        int votes = oldTrack.getVotes();
        String spotifyId = oldTrack.getSpotifyId() != null ? oldTrack.getSpotifyId() : "";
        
        return new my.edu.utar.bananamusic.models.Track(
            id,
            title,
            artist,
            album,
            albumArt,
            durationMs,
            previewUrl,
            true, // Assuming old tracks are playable
            votes,
            spotifyId
        );
    }

    /**
     * Convert from new models.Track to old model.Track
     * @param newTrack the new Track model
     * @return the old Track model with data copied from the new model
     */
    public static my.edu.utar.bananamusic.model.Track convertToOldTrack(my.edu.utar.bananamusic.models.Track newTrack) {
        if (newTrack == null) return null;
        
        // Create base track with required fields
        my.edu.utar.bananamusic.model.Track oldTrack = new my.edu.utar.bananamusic.model.Track(
            newTrack.getId(),
            newTrack.getTitle(),
            newTrack.getArtist(),
            newTrack.getAlbum(),
            newTrack.getAlbumArtUrl(),
            newTrack.getPreviewUrl(),
            newTrack.getDurationMs()
        );
        
        // Set additional fields
        oldTrack.setVotes(newTrack.getVotes());
        oldTrack.setSpotifyId(newTrack.getSpotifyId());
        
        return oldTrack;
    }

    /**
     * Convert a list of old model.Track to a list of new models.Track
     * @param oldTracks the list of old Track models
     * @return a list of new Track models
     */
    public static List<my.edu.utar.bananamusic.models.Track> convertToNewTracks(List<my.edu.utar.bananamusic.model.Track> oldTracks) {
        if (oldTracks == null) return new ArrayList<>();
        
        List<my.edu.utar.bananamusic.models.Track> newTracks = new ArrayList<>(oldTracks.size());
        for (my.edu.utar.bananamusic.model.Track oldTrack : oldTracks) {
            my.edu.utar.bananamusic.models.Track newTrack = convertToNewTrack(oldTrack);
            if (newTrack != null) {
                newTracks.add(newTrack);
            }
        }
        
        return newTracks;
    }

    /**
     * Convert a list of new models.Track to a list of old model.Track
     * @param newTracks the list of new Track models
     * @return a list of old Track models
     */
    public static List<my.edu.utar.bananamusic.model.Track> convertToOldTracks(List<my.edu.utar.bananamusic.models.Track> newTracks) {
        if (newTracks == null) return new ArrayList<>();
        
        List<my.edu.utar.bananamusic.model.Track> oldTracks = new ArrayList<>(newTracks.size());
        for (my.edu.utar.bananamusic.models.Track newTrack : newTracks) {
            my.edu.utar.bananamusic.model.Track oldTrack = convertToOldTrack(newTrack);
            if (oldTrack != null) {
                oldTracks.add(oldTrack);
            }
        }
        
        return oldTracks;
    }

    /**
     * Check if a Track object is null-safe and has all required fields populated
     * @param track The track to check
     * @return true if the track is valid, false otherwise
     */
    public static boolean isValidTrack(my.edu.utar.bananamusic.models.Track track) {
        return track != null && 
               track.getId() != null && 
               track.getTitle() != null && 
               track.getArtist() != null;
    }
} 