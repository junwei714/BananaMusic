package my.edu.utar.bananamusic.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.FirebaseConfig;

/**
 * ViewModel for Spotify search functionality
 * This provides a clean interface between the UI and the search manager
 */
public class SpotifySearchViewModel extends AndroidViewModel {
    private static final String TAG = "SpotifySearchViewModel";

    private final SpotifySearchManager searchManager;
    private final PlaylistManager playlistManager;

    // LiveData for UI observing
    private final LiveData<List<Track>> searchResults;
    private final LiveData<Boolean> isLoading;
    private final LiveData<String> errorMessage;
    private final LiveData<List<String>> recentSearches;
    
    // Track currently being previewed
    private final MutableLiveData<Track> currentPreviewTrack = new MutableLiveData<>();
    
    // Filter state
    private final MutableLiveData<String> activeFilter = new MutableLiveData<>("track");
    
    // Playlists to add tracks to
    private final MediatorLiveData<List<String>> userPlaylists = new MediatorLiveData<>();

    /**
     * Constructor
     */
    public SpotifySearchViewModel(@NonNull Application application) {
        super(application);
        
        // Initialize managers
        searchManager = SpotifySearchManager.getInstance(application);
        playlistManager = PlaylistManager.getInstance(application);
        
        // Connect LiveData from SearchManager
        searchResults = searchManager.getSearchResults();
        isLoading = searchManager.getIsLoading();
        errorMessage = searchManager.getErrorMessage();
        recentSearches = searchManager.getRecentSearches();
        
        // Set default filter
        activeFilter.setValue("track");
        
        // Load playlists
        loadUserPlaylists();
    }

    /**
     * Load user playlists
     */
    private void loadUserPlaylists() {
        playlistManager.getUserPlaylists(new PlaylistManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<my.edu.utar.bananamusic.models.Playlist> playlists) {
                // Extract playlist names
                List<String> playlistNames = new ArrayList<>();
                for (my.edu.utar.bananamusic.models.Playlist playlist : playlists) {
                    playlistNames.add(playlist.getName());
                }
                userPlaylists.setValue(playlistNames);
            }

            @Override
            public void onError(String errorMessage) {
                // Handle error
                userPlaylists.setValue(new ArrayList<>());
            }
        });
    }

    /**
     * Perform a search with the current filter
     */
    public void search(String query) {
        search(query, activeFilter.getValue(), false);
    }

    /**
     * Perform a search with a specific filter
     */
    public void search(String query, String filter, boolean forceRefresh) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        
        // Update active filter
        activeFilter.setValue(filter);
        
        // Perform search
        searchManager.search(query, filter, forceRefresh);
    }

    /**
     * Clear search results
     */
    public void clearSearch() {
        searchManager.clearSearch();
    }

    /**
     * Load more results (pagination)
     */
    public void loadMoreResults() {
        searchManager.loadMoreResults();
    }

    /**
     * Clear recent searches
     */
    public void clearRecentSearches() {
        searchManager.clearRecentSearches();
    }

    /**
     * Start playing a preview
     */
    public void playPreview(Track track) {
        if (track == null || track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
            stopPreview();
            return;
        }
        
        // Stop current preview if different track
        Track currentTrack = currentPreviewTrack.getValue();
        if (currentTrack != null && !currentTrack.getId().equals(track.getId())) {
            stopPreview();
        }
        
        // Set as current preview track
        currentPreviewTrack.setValue(track);
    }

    /**
     * Stop the current preview
     */
    public void stopPreview() {
        currentPreviewTrack.setValue(null);
    }

    /**
     * Add track to a user playlist
     */
    public void addTrackToPlaylist(Track track, String playlistId) {
        if (track == null || playlistId == null) {
            return;
        }
        
        // Ensure the track has an ID
        if (track.getId() == null || track.getId().isEmpty()) {
            // Generate a UUID for the track
            String uuid = java.util.UUID.randomUUID().toString();
            track.setId(uuid);
        }
        
        // Convert to Firestore Track model
        my.edu.utar.bananamusic.models.Track firestoreTrack = convertToFirestoreTrack(track);
        
        // Use the new method that handles embedded track data
        playlistManager.saveTrackAndAddToPlaylist(playlistId, firestoreTrack, new PlaylistManager.PlaylistCallback() {
            @Override
            public void onSuccess(my.edu.utar.bananamusic.models.Playlist playlist) {
                // Track added successfully
                android.util.Log.d(TAG, "Track added to playlist successfully: " + track.getTitle());
            }
            
            @Override
            public void onError(String errorMessage) {
                // Handle error
                android.util.Log.e(TAG, "Failed to add track to playlist: " + errorMessage);
            }
        });
    }
    
    /**
     * Convert a Spotify Track to a Firestore Track model
     */
    private my.edu.utar.bananamusic.models.Track convertToFirestoreTrack(Track spotifyTrack) {
        my.edu.utar.bananamusic.models.Track track = new my.edu.utar.bananamusic.models.Track();
        
        // Set basic track info
        track.setTrackId(spotifyTrack.getId());
        track.setTitle(spotifyTrack.getTitle());
        track.setArtist(spotifyTrack.getArtist());
        track.setAlbum(spotifyTrack.getAlbum());
        track.setDurationMs(spotifyTrack.getDuration());
        track.setAlbumArtUrl(spotifyTrack.getAlbumArtUrl());
        track.setPreviewUrl(spotifyTrack.getPreviewUrl());
        
        // Add Spotify specific fields if available
        track.setSpotifyId(spotifyTrack.getId());
        track.setPopularity(spotifyTrack.getPopularity());
        
        return track;
    }

    /**
     * Create a new playlist with a track
     */
    public void createPlaylistWithTrack(String playlistName, Track track) {
        if (playlistName == null || playlistName.trim().isEmpty() || track == null) {
            return;
        }
        
        // Convert to Firestore Track model
        my.edu.utar.bananamusic.models.Track firestoreTrack = convertToFirestoreTrack(track);
        
        // Create playlist first
        playlistManager.createPlaylist(
            playlistName,    // name
            "",              // description
            false,           // isCollaborative
            "",              // mood
            null,            // coverImageUri
            new PlaylistManager.PlaylistCallback() {
                @Override
                public void onSuccess(my.edu.utar.bananamusic.models.Playlist playlist) {
                    // Then add the track using our new method
                    playlistManager.saveTrackAndAddToPlaylist(
                        playlist.getPlaylistId(),
                        firestoreTrack,
                        new PlaylistManager.PlaylistCallback() {
                            @Override
                            public void onSuccess(my.edu.utar.bananamusic.models.Playlist updatedPlaylist) {
                                android.util.Log.d(TAG, "Created playlist with track: " + playlistName);
                            }
                            
                            @Override
                            public void onError(String errorMessage) {
                                android.util.Log.e(TAG, "Created playlist but failed to add track: " + errorMessage);
                            }
                        }
                    );
                }
                
                @Override
                public void onError(String errorMessage) {
                    android.util.Log.e(TAG, "Failed to create playlist: " + errorMessage);
                }
            }
        );
    }

    /**
     * Get search results
     */
    public LiveData<List<Track>> getSearchResults() {
        return searchResults;
    }

    /**
     * Get loading state
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Get error message
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get recent searches
     */
    public LiveData<List<String>> getRecentSearches() {
        return recentSearches;
    }

    /**
     * Get current preview track
     */
    public LiveData<Track> getCurrentPreviewTrack() {
        return currentPreviewTrack;
    }

    /**
     * Get active filter
     */
    public LiveData<String> getActiveFilter() {
        return activeFilter;
    }

    /**
     * Set active filter
     */
    public void setActiveFilter(String filter) {
        activeFilter.setValue(filter);
    }

    /**
     * Get user playlists
     */
    public LiveData<List<String>> getUserPlaylists() {
        return userPlaylists;
    }

    /**
     * Check if there are more results to load
     */
    public boolean hasMoreResults() {
        return searchManager.hasMoreResults();
    }
    
    /**
     * Get total results count
     */
    public int getTotalResults() {
        return searchManager.getTotalResults();
    }

    /**
     * Add a search term to recent searches
     * @param query The search query to add
     */
    public void addRecentSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        
        searchManager.addRecentSearch(query.trim());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopPreview();
    }
} 