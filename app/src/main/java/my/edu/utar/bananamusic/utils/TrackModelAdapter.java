package my.edu.utar.bananamusic.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter class to convert between different Track models
 * This resolves incompatibilities between my.edu.utar.bananamusic.model.Track
 * and my.edu.utar.bananamusic.models.Track
 */
public class TrackModelAdapter {

    /**
     * Convert model.Track to models.Track
     * @param track Track from model package
     * @return Track from models package
     */
    public static my.edu.utar.bananamusic.models.Track toModelsTrack(my.edu.utar.bananamusic.model.Track track) {
        if (track == null) {
            return null;
        }
        
        String trackId = track.getTrackId() != null ? track.getTrackId() : "";
        String title = track.getTitle() != null ? track.getTitle() : "";
        String artist = track.getArtist() != null ? track.getArtist() : "";
        String album = track.getAlbum() != null ? track.getAlbum() : "";
        String albumArt = track.getAlbumArt() != null ? track.getAlbumArt() : "";
        String previewUrl = track.getPreviewUrl() != null ? track.getPreviewUrl() : "";
        long durationMs = track.getDurationMs();
        String spotifyId = track.getSpotifyId() != null ? track.getSpotifyId() : "";
        
        return new my.edu.utar.bananamusic.models.Track(
                trackId, title, artist, album, albumArt, previewUrl, durationMs, spotifyId
        );
    }

    /**
     * Convert models.Track to model.Track
     * @param track Track from models package
     * @return Track from model package
     */
    public static my.edu.utar.bananamusic.model.Track toModelTrack(my.edu.utar.bananamusic.models.Track track) {
        if (track == null) {
            return null;
        }
        
        my.edu.utar.bananamusic.model.Track modelTrack = new my.edu.utar.bananamusic.model.Track();
        
        // Set primary fields
        modelTrack.setTrackId(track.getTrackId());
        modelTrack.setTitle(track.getTitle());
        modelTrack.setArtist(track.getArtist());
        modelTrack.setAlbum(track.getAlbum());
        modelTrack.setAlbumArt(track.getAlbumArt());
        modelTrack.setPreviewUrl(track.getPreviewUrl());
        
        // Handle long to int conversion safely
        long durationMs = track.getDurationMs();
        if (durationMs <= Integer.MAX_VALUE) {
            modelTrack.setDurationMs((int) durationMs);
        } else {
            modelTrack.setDurationMs(Integer.MAX_VALUE);
        }
        
        // Set additional fields
        if (track.getSpotifyId() != null) {
            modelTrack.setSpotifyId(track.getSpotifyId());
        }
        
        return modelTrack;
    }

    /**
     * Convert a list of model.Track to a list of models.Track
     * @param tracks List of tracks from model package
     * @return List of tracks from models package
     */
    public static List<my.edu.utar.bananamusic.models.Track> toModelsTracks(List<my.edu.utar.bananamusic.model.Track> tracks) {
        if (tracks == null) {
            return new ArrayList<>();
        }
        
        List<my.edu.utar.bananamusic.models.Track> result = new ArrayList<>(tracks.size());
        for (my.edu.utar.bananamusic.model.Track track : tracks) {
            result.add(toModelsTrack(track));
        }
        return result;
    }

    /**
     * Convert a list of models.Track to a list of model.Track
     * @param tracks List of tracks from models package
     * @return List of tracks from model package
     */
    public static List<my.edu.utar.bananamusic.model.Track> toModelTracks(List<my.edu.utar.bananamusic.models.Track> tracks) {
        if (tracks == null) {
            return new ArrayList<>();
        }
        
        List<my.edu.utar.bananamusic.model.Track> result = new ArrayList<>(tracks.size());
        for (my.edu.utar.bananamusic.models.Track track : tracks) {
            result.add(toModelTrack(track));
        }
        return result;
    }
} 