package my.edu.utar.bananamusic.utils;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;

/**
 * This class provides dummy data for testing and fallback purposes
 */
public class DummyDataProvider {
    private Context context;
    private static DummyDataProvider instance;
    
    public DummyDataProvider(Context context) {
        this.context = context;
    }
    
    public DummyDataProvider() {
        // Default constructor for backward compatibility
    }
    
    /**
     * Get singleton instance of DummyDataProvider
     * @param context Application context (optional)
     * @return Instance of DummyDataProvider
     */
    public static synchronized DummyDataProvider getInstance(Context context) {
        if (instance == null) {
            instance = new DummyDataProvider(context);
        }
        return instance;
    }
    
    /**
     * Get singleton instance without context
     * @return Instance of DummyDataProvider
     */
    public static synchronized DummyDataProvider getInstance() {
        if (instance == null) {
            instance = new DummyDataProvider();
        }
        return instance;
    }

    public List<String> getMoods() {
        return Arrays.asList("Happy", "Sad", "Energetic", "Relaxed", "Romantic", "Focused");
    }

    public List<Playlist> getTrendingPlaylists() {
        List<Playlist> playlists = new ArrayList<>();
        playlists.add(createDummyPlaylist("Trending 1", "Top Hits"));
        playlists.add(createDummyPlaylist("Trending 2", "New Releases"));
        return playlists;
    }

    public List<Track> getRecentTracks() {
        return generateDummyTracks(5);
    }

    public List<Track> getPlaylistTracks(String playlistId) {
        return generateDummyTracks(10);
    }

    public List<Track> getRecommendedTracks(String query, int limit) {
        return generateDummyTracks(limit);
    }

    public List<Track> getNewReleases() {
        return generateDummyTracks(10);
    }

    private List<Track> generateDummyTracks(int count) {
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tracks.add(createDummyTrack("Track " + i));
        }
        return tracks;
    }

    private Track createDummyTrack(String title) {
        return new Track(
            "dummy_" + System.currentTimeMillis(),
            title,
            "Artist",
            "Album",
            "https://example.com/cover.jpg",
            240000,
            "dummy",
            true
        );
    }

    private Playlist createDummyPlaylist(String id, String name) {
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setName(name);
        playlist.setDescription("A dummy playlist");
        playlist.setImageUrl("https://example.com/playlist.jpg");
        return playlist;
    }
    
    private boolean containsTrack(List<Track> tracks, Track track) {
        return false;
    }
    
    private String getSampleAudioUrl() {
        return "";
    }
    
    public List<Track> getDummyAlbums() {
        return new ArrayList<>();
    }
    
    private boolean trackMatchesMood(Track track, String mood) {
        return false;
    }
    
    private List<Track> getAllTracks() {
        return new ArrayList<>();
    }
    
    public List<Track> getDummyTracks(int count) {
        return new ArrayList<>();
    }
    
    public List<Playlist> getDummyPlaylists(int count) {
        return new ArrayList<>();
    }
    
    public List<Track> getTracksForMood(String mood, int limit) {
        return new ArrayList<>();
    }
    
    /**
     * Get collaborative playlists created with friends
     * @return List of collaborative playlists
     */
    public List<Playlist> getCollaborativePlaylists() {
        return new ArrayList<>();
    }
    
    /**
     * Get recently played tracks
     * @param limit Maximum number of tracks to return
     * @return List of recently played tracks
     */
    public List<Track> getRecentlyPlayed(int limit) {
        return new ArrayList<>();
    }

    public List<Track> getEmptyTrackList() {
        return new ArrayList<>();
    }

    public List<Track> createSingleTrackList(Track track) {
        List<Track> tracks = new ArrayList<>();
        if (track != null) {
            tracks.add(track);
        }
        return tracks;
    }
} 