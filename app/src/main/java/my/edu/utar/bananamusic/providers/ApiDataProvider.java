package my.edu.utar.bananamusic.providers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import my.edu.utar.bananamusic.data.TrackDatabase;
import my.edu.utar.bananamusic.data.PlaylistDatabase;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.Album;
import my.edu.utar.bananamusic.utils.TrackManager;
import my.edu.utar.bananamusic.utils.DeezerHelper;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.utils.callbacks.BaseCallback;
import my.edu.utar.bananamusic.utils.callbacks.TrackInfoCallback;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * Provider for API data with callbacks
 */
public class ApiDataProvider {
    private static final String TAG = "ApiDataProvider";
    private static ApiDataProvider instance;
    private final Context context;
    private final SpotifyHelper spotifyHelper;
    private final DeezerHelper deezerHelper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TrackManager trackManager;

    public interface PlaylistsCallback extends BaseCallback<List<Playlist>> {
        void onSuccess(List<Playlist> playlists);
        void onError(String message);
    }
    
    /**
     * Callback interface for tracks operations
     */
    public interface TracksCallback extends BaseCallback<List<Track>> {
        void onSuccess(List<Track> tracks);
        void onError(String message);
    }

    /**
     * Callback interface for featured content
     */
    public interface FeaturedContentCallback extends BaseCallback<List<Track>> {
        void onSuccess(List<Track> recommendedTracks, List<Track> newReleases);
        void onError(String message);
    }

    /**
     * Callback for personalized recommendations
     */
    public interface PersonalizedCallback {
        void onSuccess(List<Track> tracks, String message);
        void onError(String message);
    }

    private ApiDataProvider(Context context) {
        this.context = context.getApplicationContext();
        this.spotifyHelper = SpotifyHelper.getInstance(context);
        this.deezerHelper = DeezerHelper.getInstance(context);
        this.trackManager = TrackManager.getInstance(context);
    }

    public static synchronized ApiDataProvider getInstance(Context context) {
        if (instance == null) {
            instance = new ApiDataProvider(context);
        }
        return instance;
    }

    /**
     * Get tracks for a specific playlist by ID
     * @param playlistId The playlist ID
     * @param callback Callback to notify when tracks are loaded
     */
    public void getPlaylistTracks(String playlistId, final TracksCallback callback) {
        if (playlistId == null || playlistId.isEmpty()) {
            callback.onError("Invalid playlist ID: null or empty");
            return;
        }
        
        Log.d(TAG, "Getting tracks for playlist ID: " + playlistId);
        
        if (playlistId.startsWith("spotify:playlist:")) {
            String spotifyId = playlistId.replace("spotify:playlist:", "");
            Log.d(TAG, "Getting Spotify playlist tracks for ID: " + spotifyId);
            spotifyHelper.getPlaylistTracks(spotifyId, new SpotifyHelper.JSONResponseCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        List<Track> tracks = parsePlaylistTracks(response);
                        if (tracks.isEmpty()) {
                            Log.w(TAG, "No tracks found in Spotify playlist");
                        } else {
                            Log.d(TAG, "Found " + tracks.size() + " tracks in Spotify playlist");
                        }
                        callback.onSuccess(tracks);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Spotify playlist tracks: " + e.getMessage(), e);
                        onError("Error parsing playlist tracks: " + e.getMessage());
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting Spotify playlist tracks: " + message);
                    callback.onError(message);
                }
            });
        } else if (playlistId.startsWith("deezer:playlist:")) {
            String deezerId = playlistId.replace("deezer:playlist:", "");
            Log.d(TAG, "Getting Deezer playlist tracks for ID: " + deezerId);
            deezerHelper.getPlaylistTracks(deezerId, new DeezerHelper.DeezerTracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks.isEmpty()) {
                        Log.w(TAG, "No tracks found in Deezer playlist");
                    } else {
                        Log.d(TAG, "Found " + tracks.size() + " tracks in Deezer playlist");
                    }
                    callback.onSuccess(tracks);
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting Deezer playlist tracks: " + message);
                    callback.onError(message);
                }
            });
        } else {
            // Try to handle the playlist ID as a local playlist ID
            Log.d(TAG, "Trying to handle as local playlist: " + playlistId);
            
            // Attempt with PlaylistManager
            PlaylistManager playlistManager = PlaylistManager.getInstance(context);
            playlistManager.getPlaylistTracks(playlistId, new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks.isEmpty()) {
                        Log.w(TAG, "No tracks found in local playlist");
                    } else {
                        Log.d(TAG, "Found " + tracks.size() + " tracks in local playlist");
                    }
                    callback.onSuccess(tracks);
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting local playlist tracks: " + message);
                    callback.onError("Unsupported playlist ID format or playlist not found: " + message);
                }
            });
        }
    }

    /**
     * Get recommended tracks based on mood or use general recommendations
     * @param query The mood for recommendations, or null for general
     * @param limit Maximum number of tracks to return
     * @param callback Callback to notify when tracks are loaded
     */
    public void getRecommendedTracks(String query, int limit, TracksCallback callback) {
        // Check if query is null, empty, or "all" - treat all these cases as general recommendations
        if (query == null || query.isEmpty() || "all".equalsIgnoreCase(query)) {
            Log.d(TAG, "Getting general recommendations");
            spotifyHelper.getFeaturedContent(context, false, new SpotifyHelper.FeaturedContentCallback() {
                @Override
                public void onSuccess(List<Track> recommendedTracks, List<Track> newReleases) {
                    if (recommendedTracks != null && !recommendedTracks.isEmpty()) {
                        callback.onSuccess(recommendedTracks.subList(0, Math.min(limit, recommendedTracks.size())));
                    } else {
                        Log.d(TAG, "Spotify featured content returned no tracks, using Deezer fallback");
                        useDeezerFallback(null, limit, callback);
                    }
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting Spotify recommendations: " + message);
                    useDeezerFallback(null, limit, callback);
                }
            });
        } else {
            Log.d(TAG, "Getting mood-based recommendations for mood: " + query);
            spotifyHelper.getMoodBasedRecommendations(query, limit, new SpotifyHelper.SpotifyRecommendationsCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        callback.onSuccess(tracks);
                    } else {
                        Log.d(TAG, "Spotify mood recommendations returned no tracks, using Deezer fallback");
                        useDeezerFallback(query, limit, callback);
                    }
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting mood-based recommendations for " + query + ": " + message);
                    useDeezerFallback(query, limit, callback);
                }
            });
        }
    }
    
    /**
     * Get recently played tracks
     */
    public void getRecentlyPlayed(int limit, final TracksCallback callback) {
        spotifyHelper.getRecentlyPlayed(limit, new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    callback.onSuccess(tracks);
                } else {
                    getFeaturedContent(callback);
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Failed to get recently played tracks: " + message + ". Falling back to featured content.");
                getFeaturedContent(callback);
            }
        });
    }

    /**
     * Get featured content
     */
    public void getFeaturedContent(final TracksCallback callback) {
        spotifyHelper.getFeaturedContent(context, false, new SpotifyHelper.FeaturedContentCallback() {
            @Override
            public void onSuccess(List<Track> recommendedTracks, List<Track> newReleases) {
                if (recommendedTracks != null && !recommendedTracks.isEmpty()) {
                    callback.onSuccess(recommendedTracks);
                } else {
                    useDeezerFallback(null, 20, callback);
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Failed to get featured content: " + message + ". Falling back to Deezer.");
                useDeezerFallback(null, 20, callback);
            }
        });
    }

    /**
     * Get trending playlists
     * @param useCache Whether to use cached data
     * @param callback Callback to notify when playlists are loaded
     */
    public void getTrendingPlaylists(boolean useCache, PlaylistsCallback callback) {
        spotifyHelper.getFeaturedPlaylists(new SpotifyHelper.SpotifyPlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> spotifyPlaylists) {
                if (spotifyPlaylists != null && !spotifyPlaylists.isEmpty()) {
                    callback.onSuccess(spotifyPlaylists);
                } else {
                    getDeezerTrendingPlaylists(callback);
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error fetching Spotify playlists: " + message);
                getDeezerTrendingPlaylists(callback);
            }
        });
    }

    private void getDeezerTrendingPlaylists(PlaylistsCallback callback) {
        deezerHelper.getRecommendedTracks(new DeezerHelper.DeezerTracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                List<Playlist> playlists = new ArrayList<>();
                // Convert tracks to playlists
                for (Track track : tracks) {
                    Playlist playlist = new Playlist(
                        "deezer:playlist:" + track.getId(),
                        track.getTitle(),
                        track.getArtist(),
                        "Popular tracks from " + track.getArtist(),
                        track.getImageUrl(),
                        false,
                        "Deezer"
                    );
                    playlists.add(playlist);
                }
                callback.onSuccess(playlists);
            }
            
            @Override
            public void onError(String message) {
                callback.onError("Failed to get trending playlists: " + message);
            }
        });
    }

    /**
     * Get new releases
     * @param useCache Whether to use cached data
     * @param callback Callback to notify when tracks are loaded
     */
    public void getNewReleases(Context context, boolean useCache, final TracksCallback callback) {
        Log.d(TAG, "Getting new releases from Spotify");
        spotifyHelper.getNewReleases(context, useCache, new SpotifyHelper.SpotifyTracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    Log.d(TAG, "Successfully got " + tracks.size() + " new releases from Spotify");
                    callback.onSuccess(tracks);
                } else {
                    Log.d(TAG, "No tracks from Spotify, trying Deezer");
                    tryDeezerNewReleases(callback);
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to get new releases from Spotify: " + message);
                tryDeezerNewReleases(callback);
            }
        });
    }

    private void tryDeezerNewReleases(final TracksCallback callback) {
        deezerHelper.getNewReleases(new DeezerHelper.NewReleasesCallback() {
            @Override
            public void onSuccess(List<Album> albums) {
                if (albums != null && !albums.isEmpty()) {
                    // Convert albums to tracks
                    List<Track> tracks = new ArrayList<>();
                    for (Album album : albums) {
                        Track track = new Track();
                        track.setId(album.getId());
                        track.setTitle(album.getTitle());
                        track.setArtist(album.getArtist());
                        track.setAlbumArtUrl(album.getCoverUrl());
                        track.setAlbum(album.getTitle());
                        track.setDeezerUrl(album.getDeezerUrl());
                        track.setSource(DeezerHelper.SOURCE_NAME);
                        track.setType("album");
                        track.setIsAlbum(true);
                        tracks.add(track);
                    }
                    Log.d(TAG, "Successfully got " + tracks.size() + " new releases from Deezer");
                    callback.onSuccess(tracks);
                } else {
                    Log.w(TAG, "No albums from Deezer");
                    callback.onSuccess(new ArrayList<>()); // This will trigger fallback in HomeFragment
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to get new releases from Deezer: " + message);
                callback.onSuccess(new ArrayList<>()); // This will trigger fallback in HomeFragment
            }
        });
    }

    /**
     * Get varied recommendations based on query and limit
     */
    public void getVariedRecommendations(String query, int limit, TracksCallback callback) {
        spotifyHelper.getVariedRecommendations(query, limit, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(String message) {
                getRecommendedTracks(null, limit, callback);
            }
        });
    }

    /**
     * Get track info
     */
    public void getTrackInfo(String trackId, TrackInfoCallback callback) {
        // Simulate API call with dummy data
        Track track = generateDummyTrack();
        track.setId(trackId);
        mainHandler.post(() -> callback.onTrackInfoLoaded(track));
    }

    /**
     * Get personalized track recommendations using dynamic seed values and current user mood
     * 
     * @param mood The current user mood
     * @param limit Maximum number of tracks to return
     * @param callback Callback with recommendations
     */
    public void getPersonalizedRecommendations(String mood, int limit, final TracksCallback callback) {
        // Get current user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        
        // Get cached recommendations if available
        if (user != null && cachingEnabled()) {
            TrackDatabase.getInstance(context).getCachedRecommendations(user.getUid(), mood, tracks -> {
                if (tracks != null && !tracks.isEmpty()) {
                    Log.d(TAG, "Using cached recommendations");
                    mainHandler.post(() -> callback.onSuccess(tracks));
                    // Continue fetching fresh recommendations in the background
                    fetchPersonalizedRecommendations(user, mood, limit, null);
                    return;
                }
                
                // No cache, fetch fresh recommendations
                fetchPersonalizedRecommendations(user, mood, limit, callback);
            });
        } else {
            // No user or caching disabled, fetch fresh recommendations
            fetchPersonalizedRecommendations(user, mood, limit, callback);
        }
    }
    
    /**
     * Fetch fresh personalized recommendations from Spotify API
     */
    private void fetchPersonalizedRecommendations(FirebaseUser user, String mood, int limit, 
                                                TracksCallback callback) {
        spotifyHelper.getRecommendationSeeds(user, (seedTracks, seedArtists, seedGenres) -> {
            Log.d(TAG, "Got recommendation seeds - tracks: " + seedTracks.size() + 
                  ", artists: " + seedArtists.size() + ", genres: " + seedGenres.size());
                  
            spotifyHelper.getPersonalizedRecommendations(mood, limit, seedTracks, seedArtists, seedGenres, 
                new SpotifyHelper.SpotifyRecommendationsCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        Log.d(TAG, "Got " + tracks.size() + " personalized recommendations");
                        
                        // Cache the recommendations if user is logged in
                        if (user != null && cachingEnabled()) {
                            TrackDatabase.getInstance(context).cacheRecommendations(
                                user.getUid(), mood, tracks);
                        }
                        
                        // Only notify callback if it was provided
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess(tracks));
                        }
                    }
                    
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Error getting personalized recommendations: " + message);
                        
                        // Fall back to cached recommendations if available
                        if (user != null && cachingEnabled() && callback != null) {
                            TrackDatabase.getInstance(context).getCachedRecommendations(user.getUid(), mood, tracks -> {
                                if (tracks != null && !tracks.isEmpty()) {
                                    Log.d(TAG, "Falling back to cached recommendations after API error");
                                    mainHandler.post(() -> callback.onSuccess(tracks));
                                } else {
                                    // No cache, use fallback method
                                    useDeezerFallback(mood, limit, callback);
                                }
                            });
                        } else if (callback != null) {
                            // No user or caching disabled, use fallback
                            useDeezerFallback(mood, limit, callback);
                        }
                    }
                });
        });
    }
    
    /**
     * Get featured playlists with pagination and sorting
     * 
     * @param offset The offset for pagination
     * @param limit Maximum number of playlists to return
     * @param sortBy How to sort the playlists (popularity, recency)
     * @param callback Callback with playlists
     */
    public void getFeaturedPlaylistsWithPagination(int offset, int limit, String sortBy, 
                                                 final PlaylistsCallback callback) {
        // Get current user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        
        // Check for cached playlists
        if (user != null && cachingEnabled()) {
            PlaylistDatabase.getInstance(context).getCachedFeaturedPlaylists(playlists -> {
                if (playlists != null && !playlists.isEmpty()) {
                    Log.d(TAG, "Using cached featured playlists");
                    
                    // Sort the playlists according to the requested sort order
                    sortPlaylists(playlists, sortBy, user);
                    
                    // Apply pagination
                    int toIndex = Math.min(offset + limit, playlists.size());
                    if (offset < playlists.size()) {
                        List<Playlist> paginatedPlaylists = playlists.subList(offset, toIndex);
                        mainHandler.post(() -> callback.onSuccess(paginatedPlaylists));
                    } else {
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    }
                    
                    // Continue fetching fresh playlists in the background
                    fetchFeaturedPlaylistsWithPagination(offset, limit, sortBy, null);
                    return;
                }
                
                // No cache, fetch fresh playlists
                fetchFeaturedPlaylistsWithPagination(offset, limit, sortBy, callback);
            });
        } else {
            // No user or caching disabled, fetch fresh playlists
            fetchFeaturedPlaylistsWithPagination(offset, limit, sortBy, callback);
        }
    }
    
    /**
     * Fetch fresh featured playlists from Spotify API
     */
    private void fetchFeaturedPlaylistsWithPagination(int offset, int limit, String sortBy, 
                                                    PlaylistsCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        
        spotifyHelper.getFeaturedPlaylistsWithPagination(offset, limit, 
            new SpotifyHelper.SpotifyPlaylistsCallback() {
                @Override
                public void onSuccess(List<Playlist> playlists) {
                    Log.d(TAG, "Got " + playlists.size() + " featured playlists");
                    
                    // Sort the playlists according to the requested sort order
                    sortPlaylists(playlists, sortBy, user);
                    
                    // Cache the playlists if user is logged in
                    if (user != null && cachingEnabled()) {
                        PlaylistDatabase.getInstance(context).cacheFeaturedPlaylists(playlists);
                    }
                    
                    // Only notify callback if it was provided
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(playlists));
                    }
                    
                    // Preload playlist details for a better user experience
                    preloadPlaylistDetails(playlists);
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting featured playlists: " + message);
                    
                    // Fall back to cached playlists if available
                    if (user != null && cachingEnabled() && callback != null) {
                        PlaylistDatabase.getInstance(context).getCachedFeaturedPlaylists(playlists -> {
                            if (playlists != null && !playlists.isEmpty()) {
                                Log.d(TAG, "Falling back to cached playlists after API error");
                                
                                // Sort and paginate
                                sortPlaylists(playlists, sortBy, user);
                                int toIndex = Math.min(offset + limit, playlists.size());
                                if (offset < playlists.size()) {
                                    List<Playlist> paginatedPlaylists = playlists.subList(offset, toIndex);
                                    mainHandler.post(() -> callback.onSuccess(paginatedPlaylists));
                                } else {
                                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                                }
                            } else {
                                // No cache, use fallback method
                                getDeezerFeaturedPlaylists(callback);
                            }
                        });
                    } else if (callback != null) {
                        // No user or caching disabled, use fallback
                        getDeezerFeaturedPlaylists(callback);
                    }
                }
            });
    }
    
    /**
     * Preload detailed information for playlists to improve user experience
     */
    private void preloadPlaylistDetails(List<Playlist> playlists) {
        if (playlists == null || playlists.isEmpty()) return;
        
        // Only preload details for the first few playlists to avoid unnecessary API calls
        int preloadCount = Math.min(5, playlists.size());
        
        for (int i = 0; i < preloadCount; i++) {
            Playlist playlist = playlists.get(i);
            if (playlist.getId().startsWith("spotify:playlist:")) {
                String spotifyId = playlist.getId().replace("spotify:playlist:", "");
                spotifyHelper.getPlaylistTracks(spotifyId, new SpotifyHelper.JSONResponseCallback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        try {
                            List<Track> tracks = parsePlaylistTracks(response);
                            playlist.setPreloadedTracks(tracks);
                            
                            // Cache the playlist with its tracks
                            if (cachingEnabled()) {
                                PlaylistDatabase.getInstance(context).cachePlaylistWithTracks(playlist, tracks);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing preloaded playlist tracks: " + e.getMessage(), e);
                        }
                    }
                    
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Error preloading playlist tracks: " + message);
                    }
                });
            }
        }
    }
    
    /**
     * Sort playlists based on the requested sort order and user preferences
     */
    private void sortPlaylists(List<Playlist> playlists, String sortBy, FirebaseUser user) {
        if (playlists == null || playlists.isEmpty()) return;
        
        if ("popularity".equalsIgnoreCase(sortBy)) {
            // Sort by popularity (track count can be used as a proxy for popularity)
            Collections.sort(playlists, (p1, p2) -> Integer.compare(p2.getTrackCount(), p1.getTrackCount()));
        } else if ("recency".equalsIgnoreCase(sortBy)) {
            // In this case we're relying on the order from the API, which is already by recency
            // So we don't need to do anything
        } else if (user != null) {
            // Default sort order - try to match user's listening habits
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            // This operation would normally need to be done asynchronously
            // For demo purposes, we're just showing what would happen
            db.collection("users").document(user.getUid())
              .collection("history")
              .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
              .limit(50)
              .get()
              .addOnSuccessListener(querySnapshot -> {
                  // Count artist and genre occurrences
                  Map<String, Integer> artistCounts = new HashMap<>();
                  Map<String, Integer> genreCounts = new HashMap<>();
                  
                  for (QueryDocumentSnapshot doc : querySnapshot) {
                      String artistId = doc.getString("artistId");
                      List<String> genres = (List<String>) doc.get("genres");
                      
                      if (artistId != null) {
                          artistCounts.put(artistId, artistCounts.getOrDefault(artistId, 0) + 1);
                      }
                      
                      if (genres != null) {
                          for (String genre : genres) {
                              genreCounts.put(genre, genreCounts.getOrDefault(genre, 0) + 1);
                          }
                      }
                  }
                  
                  // Score each playlist based on how well it matches the user's history
                  Map<Playlist, Integer> playlistScores = new HashMap<>();
                  
                  for (Playlist playlist : playlists) {
                      int score = 0;
                      
                      // If we have preloaded tracks, use them to calculate the score
                      if (playlist.getPreloadedTracks() != null) {
                          for (Track track : playlist.getPreloadedTracks()) {
                              // Increase score if user listened to this artist
                              String artistId = track.getArtistId();
                              if (artistId != null && artistCounts.containsKey(artistId)) {
                                  score += artistCounts.get(artistId);
                              }
                              
                              // Increase score if user listened to this genre
                              List<String> trackGenres = track.getGenres();
                              if (trackGenres != null) {
                                  for (String genre : trackGenres) {
                                      if (genreCounts.containsKey(genre)) {
                                          score += genreCounts.get(genre);
                                      }
                                  }
                              }
                          }
                      }
                      
                      playlistScores.put(playlist, score);
                  }
                  
                  // Sort playlists by score
                  Collections.sort(playlists, (p1, p2) -> 
                      Integer.compare(playlistScores.getOrDefault(p2, 0), 
                                     playlistScores.getOrDefault(p1, 0)));
              });
        }
    }
    
    /**
     * Get new releases with filtering for user preferences
     * 
     * @param offset The offset for pagination
     * @param limit Maximum number of releases to return
     * @param genres Optional list of genres to filter by
     * @param callback Callback with tracks
     */
    public void getNewReleasesWithFiltering(int offset, int limit, List<String> genres, 
                                          final TracksCallback callback) {
        // Get current user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        
        // Check for cached new releases
        if (user != null && cachingEnabled()) {
            TrackDatabase.getInstance(context).getCachedNewReleases(tracks -> {
                if (tracks != null && !tracks.isEmpty()) {
                    Log.d(TAG, "Using cached new releases");
                    
                    // Filter tracks by genres if specified
                    List<Track> filteredTracks = filterTracksByGenres(tracks, genres);
                    
                    // Apply pagination
                    int toIndex = Math.min(offset + limit, filteredTracks.size());
                    if (offset < filteredTracks.size()) {
                        List<Track> paginatedTracks = filteredTracks.subList(offset, toIndex);
                        mainHandler.post(() -> callback.onSuccess(paginatedTracks));
                    } else {
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    }
                    
                    // Continue fetching fresh new releases in the background
                    fetchNewReleasesWithFiltering(offset, limit, genres, null);
                    return;
                }
                
                // No cache, fetch fresh new releases
                fetchNewReleasesWithFiltering(offset, limit, genres, callback);
            });
        } else {
            // No user or caching disabled, fetch fresh new releases
            fetchNewReleasesWithFiltering(offset, limit, genres, callback);
        }
    }
    
    /**
     * Fetch fresh new releases from Spotify API
     */
    private void fetchNewReleasesWithFiltering(int offset, int limit, List<String> genres, 
                                             TracksCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        
        spotifyHelper.getNewReleasesWithFiltering(offset, limit, genres, 
            new SpotifyHelper.SpotifyTracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    Log.d(TAG, "Got " + tracks.size() + " new releases");
                    
                    // Cache the tracks if user is logged in
                    if (user != null && cachingEnabled()) {
                        TrackDatabase.getInstance(context).cacheNewReleases(tracks);
                    }
                    
                    // Only notify callback if it was provided
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(tracks));
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error getting new releases: " + message);
                    
                    // Fall back to cached new releases if available
                    if (user != null && cachingEnabled() && callback != null) {
                        TrackDatabase.getInstance(context).getCachedNewReleases(tracks -> {
                            if (tracks != null && !tracks.isEmpty()) {
                                Log.d(TAG, "Falling back to cached new releases after API error");
                                
                                // Filter and paginate
                                List<Track> filteredTracks = filterTracksByGenres(tracks, genres);
                                int toIndex = Math.min(offset + limit, filteredTracks.size());
                                if (offset < filteredTracks.size()) {
                                    List<Track> paginatedTracks = filteredTracks.subList(offset, toIndex);
                                    mainHandler.post(() -> callback.onSuccess(paginatedTracks));
                                } else {
                                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                                }
                            } else {
                                // No cache, use fallback method
                                tryDeezerNewReleases(callback);
                            }
                        });
                    } else if (callback != null) {
                        // No user or caching disabled, use fallback
                        tryDeezerNewReleases(callback);
                    }
                }
            });
    }
    
    /**
     * Filter tracks by genres
     */
    private List<Track> filterTracksByGenres(List<Track> tracks, List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return tracks;
        }
        
        List<Track> filteredTracks = new ArrayList<>();
        
        for (Track track : tracks) {
            List<String> trackGenres = track.getGenres();
            
            if (trackGenres != null) {
                boolean matchesGenre = false;
                
                for (String trackGenre : trackGenres) {
                    for (String requestedGenre : genres) {
                        if (trackGenre.toLowerCase().contains(requestedGenre.toLowerCase())) {
                            matchesGenre = true;
                            break;
                        }
                    }
                    
                    if (matchesGenre) break;
                }
                
                if (matchesGenre) {
                    filteredTracks.add(track);
                }
            }
        }
        
        return filteredTracks;
    }
    
    /**
     * Check if caching is enabled
     */
    private boolean cachingEnabled() {
        return trackManager.isCachingEnabled();
    }

    /**
     * Generate dummy tracks for testing
     */
    private List<Track> generateDummyTracks(int count) {
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Track track = new Track(
                "dummy_" + i,
                "Track " + i,
                "Artist " + i,
                "Album " + i,
                "",
                240000, // 4 minutes
                "dummy",
                true
            );
            tracks.add(track);
        }
        return tracks;
    }
    
    /**
     * Generate a dummy track for testing
     */
    private Track generateDummyTrack() {
        return new Track(
            "dummy_1",
            "Sample Track",
            "Sample Artist",
            "Sample Album",
            "",
            240000, // 4 minutes
            "dummy",
            true
        );
    }

    /**
     * Get recommendations based on track IDs
     * @param trackIds List of track IDs to base recommendations on
     * @param limit Maximum number of tracks to return
     * @param callback Callback to notify when recommendations are ready
     */
    public void getRecommendationsBasedOnTrackIds(List<String> trackIds, int limit, TracksCallback callback) {
        if (trackIds == null || trackIds.isEmpty()) {
            mainHandler.post(() -> callback.onError("No track IDs provided"));
            return;
        }
        
        // For now, we'll use general recommendations as a fallback
        getRecommendedTracks(null, limit, callback);
    }
    
    /**
     * Search for tracks matching the given query
     * @param query The search query (can include prefixes like "artist:", "genre:", etc.)
     * @param callback Callback to notify when search results are ready
     */
    public void searchTracks(String query, final TracksCallback callback) {
        spotifyHelper.searchTracks(query, new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject jsonResponse = new JSONObject(result);
                    JSONObject tracks = jsonResponse.getJSONObject("tracks");
                    JSONArray items = tracks.getJSONArray("items");
                    List<Track> trackList = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject trackJson = items.getJSONObject(i);
                        Track track = spotifyHelper.parseTrackJson(trackJson);
                        trackList.add(track);
                    }
                    callback.onSuccess(trackList);
                } catch (Exception e) {
                    onError("Error parsing search results: " + e.getMessage());
                }
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Get recently played tracks from local history
     */
    private void getRecentlyPlayedFromHistory(TracksCallback callback) {
        try {
            List<Track> recentTracks = trackManager.getRecentlyPlayedTracks(20);
            if (recentTracks != null && !recentTracks.isEmpty()) {
                callback.onSuccess(recentTracks);
            } else {
                callback.onError("No recently played tracks found");
            }
        } catch (Exception e) {
            callback.onError("Error getting recently played tracks: " + e.getMessage());
        }
    }

    /**
     * Get tracks from a Spotify playlist
     * @param spotifyId The Spotify playlist ID
     * @param callback Callback to notify when tracks are loaded
     */
    public void getSpotifyPlaylistTracks(String spotifyId, final TracksCallback callback) {
        spotifyHelper.getPlaylistTracks(spotifyId, new SpotifyHelper.JSONResponseCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    List<Track> tracks = parsePlaylistTracks(response);
                    callback.onSuccess(tracks);
                } catch (Exception e) {
                    onError("Error parsing playlist tracks: " + e.getMessage());
                }
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Get backup genre for a given genre
     */
    public String getBackupGenre(String genre) {
        if (genre == null || genre.isEmpty()) return "pop";
        
        // Simple mapping of similar genres
        switch (genre.toLowerCase()) {
            case "rock": return "alternative";
            case "pop": return "dance";
            case "hip hop": return "rap";
            case "electronic": return "dance";
            case "classical": return "instrumental";
            case "jazz": return "blues";
            default: return "pop";
        }
    }

    /**
     * Get featured albums by mood
     */
    public void getFeaturedAlbumsByMood(String mood, TracksCallback callback) {
        spotifyHelper.getMoodBasedRecommendations(mood, 20, new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(String message) {
                // Fall back to general recommendations
                getRecommendedTracks(null, 20, callback);
            }
        });
    }

    /**
     * Get featured albums
     */
    public void getFeaturedAlbums(TracksCallback callback) {
        spotifyHelper.getFeaturedContent(context, false, new SpotifyHelper.FeaturedContentCallback() {
            @Override
            public void onSuccess(List<Track> recommendedTracks, List<Track> newReleases) {
                // Combine both lists for featured albums
                List<Track> combined = new ArrayList<>();
                if (recommendedTracks != null) combined.addAll(recommendedTracks);
                if (newReleases != null) combined.addAll(newReleases);
                callback.onSuccess(combined);
            }

            @Override
            public void onError(String message) {
                // Fall back to general recommendations
                getRecommendedTracks(null, 20, callback);
            }
        });
    }

    public List<Track> getRecentlyPlayedTracks(int limit) {
        return trackManager.getRecentlyPlayedTracks(limit);
    }

    /**
     * Get collaborators for a playlist
     */
    public void getCollaborators(String playlistId, TracksCallback callback) {
        // Return empty list as this feature is not yet implemented
        callback.onSuccess(new ArrayList<>());
    }

    /**
     * Invite a collaborator to a playlist
     */
    public void inviteCollaborator(String playlistId, String userId, TracksCallback callback) {
        // Return empty list as this feature is not yet implemented
        callback.onSuccess(new ArrayList<>());
    }

    /**
     * Add a track to the user's library
     */
    public void addTrackToLibrary(Track track, TracksCallback callback) {
        // Return the track in a list
        List<Track> tracks = new ArrayList<>();
        tracks.add(track);
        callback.onSuccess(tracks);
    }

    /**
     * Fall back to Deezer for recommendations
     */
    private void useDeezerFallback(String query, int limit, TracksCallback callback) {
        Log.d(TAG, "Using Deezer fallback for query: " + (query != null ? query : "general recommendations"));
        if (query == null || query.isEmpty() || "all".equalsIgnoreCase(query)) {
            deezerHelper.getRecommendedTracks(new DeezerHelper.DeezerTracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        Log.d(TAG, "Deezer fallback returned " + tracks.size() + " tracks");
                        callback.onSuccess(tracks.subList(0, Math.min(limit, tracks.size())));
                    } else {
                        Log.d(TAG, "Deezer fallback returned no tracks, providing dummy content");
                        provideDummyContent(limit, callback);
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Deezer fallback error: " + message + ". Providing dummy content");
                    provideDummyContent(limit, callback);
                }
            });
        } else {
            deezerHelper.getMoodTracks(query, new DeezerHelper.DeezerTracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        Log.d(TAG, "Deezer mood tracks returned " + tracks.size() + " tracks");
                        callback.onSuccess(tracks.subList(0, Math.min(limit, tracks.size())));
                    } else {
                        Log.d(TAG, "No Deezer tracks found for mood: " + query + ", providing dummy content");
                        provideDummyContent(limit, callback, query);
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Failed to get mood tracks from Deezer: " + message + ". Providing dummy content");
                    provideDummyContent(limit, callback, query);
                }
            });
        }
    }

    /**
     * Provide dummy content when all other sources fail
     */
    private void provideDummyContent(int limit, TracksCallback callback) {
        Log.d(TAG, "Providing general dummy content as last resort");
        List<Track> dummyTracks = generateDummyTracks(limit);
        callback.onSuccess(dummyTracks);
    }

    /**
     * Provide mood-specific dummy content
     */
    private void provideDummyContent(int limit, TracksCallback callback, String mood) {
        Log.d(TAG, "Providing mood-specific dummy content for mood: " + mood);
        List<Track> dummyTracks = generateDummyTracksForMood(limit, mood);
        callback.onSuccess(dummyTracks);
    }

    /**
     * Generate dummy tracks with mood-specific characteristics
     * @param count Number of tracks to generate
     * @param mood The mood to generate tracks for
     * @return List of dummy tracks
     */
    private List<Track> generateDummyTracksForMood(int count, String mood) {
        List<Track> tracks = new ArrayList<>();
        String normalizedMood = mood.toLowerCase();
        
        // Mood-specific track generators
        Map<String, List<String>> moodTrackTitles = new HashMap<>();
        moodTrackTitles.put("happy", List.of(
            "Happy Day", "Sunshine Smile", "Joyful Morning", "Celebration", 
            "Good Vibes", "Upbeat Rhythm", "Dancing Clouds", "Summer Fun"
        ));
        moodTrackTitles.put("sad", List.of(
            "Blue Rain", "Lost Memories", "Winter's Tear", "Empty Room", 
            "Silent Echo", "Fading Light", "Lonely Path", "Yesterday's Gone"
        ));
        moodTrackTitles.put("energetic", List.of(
            "Power Up", "Adrenaline Rush", "Electric Pulse", "Maximum Energy", 
            "Fierce Beats", "Unstoppable", "Accelerate", "Dynamic Force"
        ));
        moodTrackTitles.put("relaxed", List.of(
            "Gentle Waves", "Peaceful Sunset", "Calm Waters", "Soft Breeze", 
            "Quiet Moments", "Serene Mind", "Tranquil Space", "Easy Flow"
        ));
        moodTrackTitles.put("romantic", List.of(
            "Love Story", "Eternal Bond", "Heart's Whisper", "Sweet Embrace", 
            "Starlit Dance", "Forever Yours", "Tender Moments", "Passionate"
        ));
        moodTrackTitles.put("focused", List.of(
            "Clear Mind", "Deep Focus", "Concentration", "Productive Flow", 
            "Mental Clarity", "Study Session", "Mindful Work", "Attention"
        ));
        
        // Artists for each mood
        Map<String, List<String>> moodArtists = new HashMap<>();
        moodArtists.put("happy", List.of(
            "Joy Makers", "Bright Day Band", "The Smiles", "Happy Harmony", 
            "Positive Vibes", "Sunshine Orchestra", "Upbeat Rhythms"
        ));
        moodArtists.put("sad", List.of(
            "Melancholy Whispers", "Lost Echoes", "Blue Soul", "Teardrops", 
            "Rainy Day Collective", "The Lonesome", "Shadow's Embrace"
        ));
        moodArtists.put("energetic", List.of(
            "Dynamo", "Power Pulse", "Electric Storm", "Adrenaline Rush", 
            "The Energizers", "Full Force", "Intensity"
        ));
        moodArtists.put("relaxed", List.of(
            "Calm Waters", "Serene Sounds", "Peaceful Harmony", "Tranquil Tides", 
            "Gentle Breeze", "The Meditators", "Easy Flow"
        ));
        moodArtists.put("romantic", List.of(
            "Love Notes", "Heart Strings", "Sweet Serenaders", "Passion Project", 
            "Moonlight Romantics", "Eternal Bond", "Love Story"
        ));
        moodArtists.put("focused", List.of(
            "Mind Clarity", "Deep Focus", "Concentration", "Mindful Music", 
            "Productivity Sounds", "Study Session", "Clear Thoughts"
        ));
        
        // Get the appropriate lists for the requested mood
        List<String> titles = moodTrackTitles.getOrDefault(normalizedMood, moodTrackTitles.get("happy"));
        List<String> artists = moodArtists.getOrDefault(normalizedMood, moodArtists.get("happy"));
        
        // Generate tracks
        for (int i = 0; i < count; i++) {
            String title = titles.get(i % titles.size());
            String artist = artists.get(i % artists.size());
            String id = "dummy_" + normalizedMood + "_" + i;
            
            String imageUrl = "https://placehold.co/200x200/3498db/ffffff?text=" + 
                              normalizedMood.substring(0, 1).toUpperCase() + 
                              normalizedMood.substring(1);
            
            Track track = new Track(
                id,
                title,
                artist,
                normalizedMood.substring(0, 1).toUpperCase() + normalizedMood.substring(1) + " Music",
                imageUrl,
                180000, // 3 minutes
                "Local",
                true
            );
            
            // Add more mood-specific metadata
            track.setMood(normalizedMood);
            tracks.add(track);
        }
        
        return tracks;
    }

    /**
     * Convert from ApiDataProvider.TracksCallback to utils.callbacks.TracksCallback
     */
    public static TracksCallback fromApiProviderCallback(final ApiDataProvider.TracksCallback callback) {
        if (callback == null) return null;
        TracksCallback tracksCallback = new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                callback.onSuccess(tracks);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
        return tracksCallback;
    }

    /**
     * Parse playlist tracks from a Spotify API response
     * @param response The JSON response from Spotify API
     * @return List of Track objects
     */
    private List<Track> parsePlaylistTracks(JSONObject response) throws Exception {
        List<Track> tracks = new ArrayList<>();
        
        try {
            // Get the tracks object from response
            if (!response.has("tracks") && !response.has("items")) {
                Log.w(TAG, "Response doesn't contain tracks or items: " + response.toString().substring(0, Math.min(200, response.toString().length())));
                return tracks;
            }
            
            // Get items array - could be directly in response or in a tracks object
            JSONArray items;
            if (response.has("tracks")) {
                JSONObject tracksObj = response.getJSONObject("tracks");
                items = tracksObj.getJSONArray("items");
            } else {
                items = response.getJSONArray("items");
            }
            
            // Loop through items
            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    JSONObject trackObj;
                    
                    // Track could be directly in item or in a track property
                    if (item.has("track")) {
                        trackObj = item.getJSONObject("track");
                    } else {
                        trackObj = item;
                    }
                    
                    // Skip null tracks or local files
                    if (trackObj.isNull("id") || trackObj.optBoolean("is_local", false)) {
                        continue;
                    }
                    
                    // Extract track info
                    String id = trackObj.getString("id");
                    String title = trackObj.getString("name");
                    String previewUrl = trackObj.optString("preview_url", "");
                    int duration = trackObj.optInt("duration_ms", 0);
                    
                    // Get artists
                    JSONArray artists = trackObj.getJSONArray("artists");
                    StringBuilder artistsStr = new StringBuilder();
                    for (int j = 0; j < artists.length(); j++) {
                        if (j > 0) artistsStr.append(", ");
                        artistsStr.append(artists.getJSONObject(j).getString("name"));
                    }
                    String artist = artistsStr.toString();
                    
                    // Get album info
                    String album = "";
                    String albumArtUrl = "";
                    if (trackObj.has("album") && !trackObj.isNull("album")) {
                        JSONObject albumObj = trackObj.getJSONObject("album");
                        album = albumObj.optString("name", "");
                        
                        // Get album art
                        if (albumObj.has("images") && albumObj.getJSONArray("images").length() > 0) {
                            JSONArray images = albumObj.getJSONArray("images");
                            albumArtUrl = images.getJSONObject(0).getString("url");
                        }
                    }
                    
                    // Create track object
                    Track track = new Track(id, title, artist, album, albumArtUrl, duration, "Spotify", false);
                    track.setPreviewUrl(previewUrl);
                    track.setSpotifyId(id);
                    
                    tracks.add(track);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing track at index " + i + ": " + e.getMessage());
                    // Continue with next track
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing playlist tracks: " + e.getMessage(), e);
            throw e;
        }
        
        Log.d(TAG, "Successfully parsed " + tracks.size() + " tracks from playlist");
        return tracks;
    }

    private void onError(String message) {
        Log.e(TAG, message);
    }

    private void handleSpotifyPlaylistResponse(JSONObject json, PlaylistsCallback callback) throws JSONException {
        // Safely check if the "playlists" key exists using optJSONObject
        JSONObject playlistsObj = json.optJSONObject("playlists");
        List<Playlist> playlists = new ArrayList<>();
        
        if (playlistsObj == null) {
            // Log a meaningful warning if "playlists" key is missing
            Log.w(TAG, "Response doesn't contain 'playlists' key, returning empty playlist list");
            // Skip parsing and return empty list instead of throwing an exception
            callback.onSuccess(playlists);
            return;
        }
        
        // Continue with parsing if "playlists" exists
        JSONArray items = playlistsObj.optJSONArray("items");
        if (items == null) {
            Log.w(TAG, "Playlists object doesn't contain 'items' array, returning empty playlist list");
            callback.onSuccess(playlists);
            return;
        }
        
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                Playlist playlist = new Playlist(
                    item.optString("id", "unknown_" + i),
                    item.optString("name", "Unnamed Playlist"),
                    item.optJSONObject("owner") != null ? item.optJSONObject("owner").optString("display_name", "Unknown") : "Unknown",
                    item.optString("description", ""),
                    item.optJSONArray("images") != null && item.optJSONArray("images").length() > 0 ? 
                        item.optJSONArray("images").optJSONObject(0).optString("url", "") : "",
                    false,
                    "Spotify"
                );
                playlists.add(playlist);
            } catch (Exception e) {
                // Log error but continue processing other playlists
                Log.e(TAG, "Error parsing playlist at index " + i + ": " + e.getMessage());
            }
        }
        callback.onSuccess(playlists);
    }

    public void getEmptyTrackList(TracksCallback callback) {
        callback.onSuccess(new ArrayList<>());
    }

    public void getEmptyPlaylistList(PlaylistsCallback callback) {
        callback.onSuccess(new ArrayList<>());
    }

    public void createSingleTrackList(Track track, TracksCallback callback) {
        List<Track> tracks = new ArrayList<>();
        tracks.add(track);
        callback.onSuccess(tracks);
    }

    /**
     * Get featured playlists from Deezer
     */
    private void getDeezerFeaturedPlaylists(PlaylistsCallback callback) {
        DeezerHelper.getInstance(context).getFeaturedPlaylists(new DeezerHelper.DeezerPlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                callback.onSuccess(playlists);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error getting Deezer featured playlists: " + message);
                callback.onError(message);
            }
        });
    }
} 