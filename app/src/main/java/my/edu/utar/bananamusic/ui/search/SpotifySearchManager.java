package my.edu.utar.bananamusic.ui.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * SpotifySearchManager handles search operations, caching and state management for Spotify searches.
 * This class implements proper authentication, caching, rate limiting, and pagination.
 */
public class SpotifySearchManager {
    private static final String TAG = "SpotifySearchManager";
    private static final String PREF_RECENT_SEARCHES = "recent_searches";
    private static final int MAX_RECENT_SEARCHES = 10;
    private static final int CACHE_SIZE = 50; // Cache size in number of search queries
    private static final int DEFAULT_PAGE_SIZE = 20;

    // API endpoints
    private static final String SPOTIFY_SEARCH_URL = "https://api.spotify.com/v1/search";
    private static final String AUTH_TOKEN_URL = "https://accounts.spotify.com/api/token";

    // Singleton instance
    private static SpotifySearchManager instance;

    // Dependencies
    private final Context context;
    private final OkHttpClient httpClient;
    private final SpotifyHelper spotifyHelper;
    private final SharedPreferences prefs;

    // In-memory cache for search results
    private final LruCache<String, List<Track>> searchCache;

    // LiveData for reactive UI updates
    private final MutableLiveData<List<Track>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<List<String>> recentSearches = new MutableLiveData<>();

    // Current search state
    private String currentQuery = "";
    private String currentType = "track,artist,album";
    private int currentOffset = 0;
    private boolean hasMoreResults = true;
    private int totalResults = 0;

    // Rate limiting tracking
    private long lastRequestTime = 0;
    private int requestCount = 0;
    private static final long RATE_LIMIT_WINDOW = 30000; // 30 seconds
    private static final int RATE_LIMIT_MAX_REQUESTS = 10; // Maximum requests per window

    /**
     * Private constructor for singleton pattern
     */
    private SpotifySearchManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("spotify_search_prefs", Context.MODE_PRIVATE);
        this.spotifyHelper = SpotifyHelper.getInstance(context);

        // Configure HTTP client with timeouts
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        // Initialize LRU cache
        this.searchCache = new LruCache<>(CACHE_SIZE);

        // Initialize reactive state
        this.isLoading.setValue(false);
        this.errorMessage.setValue(null);
        this.searchResults.setValue(new ArrayList<>());
        loadRecentSearches();
    }

    /**
     * Get singleton instance
     */
    public static synchronized SpotifySearchManager getInstance(Context context) {
        if (instance == null) {
            instance = new SpotifySearchManager(context);
        }
        return instance;
    }

    /**
     * Perform a search for tracks, artists, or albums
     * 
     * @param query The search query
     * @param type The type of search (track, artist, album, or a comma-separated list)
     * @param refreshCache Whether to force refresh the cache
     */
    public void search(String query, String type, boolean refreshCache) {
        if (query == null || query.trim().isEmpty()) {
            errorMessage.setValue("Search query cannot be empty");
            return;
        }

        // Normalize query
        query = query.trim().toLowerCase();
        
        // Reset pagination when starting a new search
        if (!query.equals(currentQuery) || !type.equals(currentType)) {
            currentOffset = 0;
            hasMoreResults = true;
            totalResults = 0;
        }
        
        currentQuery = query;
        currentType = type;

        // Save to recent searches
        addToRecentSearches(query);

        // Check cache first if not refreshing
        String cacheKey = getCacheKey(query, type);
        if (!refreshCache) {
            List<Track> cachedResults = searchCache.get(cacheKey);
            if (cachedResults != null) {
                Log.d(TAG, "Cache hit for query: " + query);
                searchResults.setValue(cachedResults);
                return;
            }
        }

        // Show loading state
        isLoading.setValue(true);
        errorMessage.setValue(null);

        // Check rate limiting
        if (!canMakeRequest()) {
            isLoading.setValue(false);
            errorMessage.setValue("Too many requests. Please try again in a moment.");
            return;
        }

        // Execute search via Spotify Helper
        spotifyHelper.searchTracks(query, type, new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String response) {
                trackRequest();
                try {
                    // Check if response is empty or null
                    if (response == null || response.isEmpty()) {
                        Log.e(TAG, "Empty response from Spotify API");
                        errorMessage.postValue("No results found");
                        isLoading.postValue(false);
                        return;
                    }
                    
                    // Check if response is an empty array
                    if (response.trim().equals("[]")) {
                        Log.d(TAG, "Empty array response from Spotify API");
                        searchResults.postValue(new ArrayList<>());
                        isLoading.postValue(false);
                        return;
                    }
                    
                    List<Track> tracks = parseSearchResponse(response);
                    
                    // Cache the results
                    searchCache.put(cacheKey, tracks);
                    
                    // Update the UI
                    searchResults.postValue(tracks);
                    isLoading.postValue(false);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing search results: " + e.getMessage(), e);
                    errorMessage.postValue("Error parsing search results: " + e.getMessage());
                    isLoading.postValue(false);
                }
            }

            @Override
            public void onError(String message) {
                trackRequest();
                Log.e(TAG, "Search error: " + message);
                errorMessage.postValue(message);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Load the next page of results (pagination)
     */
    public void loadMoreResults() {
        if (!hasMoreResults || isLoading.getValue() == true) {
            return;
        }

        // Check if we already have all results
        if (searchResults.getValue() != null && searchResults.getValue().size() >= totalResults) {
            hasMoreResults = false;
            return;
        }

        // Increment offset for pagination
        currentOffset += DEFAULT_PAGE_SIZE;
        
        // Show loading state
        isLoading.setValue(true);

        // Check rate limiting
        if (!canMakeRequest()) {
            isLoading.setValue(false);
            errorMessage.setValue("Too many requests. Please try again in a moment.");
            return;
        }

        // Build the search URL with pagination
        HttpUrl.Builder urlBuilder = HttpUrl.parse(SPOTIFY_SEARCH_URL).newBuilder()
                .addQueryParameter("q", currentQuery)
                .addQueryParameter("type", currentType)
                .addQueryParameter("limit", String.valueOf(DEFAULT_PAGE_SIZE))
                .addQueryParameter("offset", String.valueOf(currentOffset))
                .addQueryParameter("market", "from_token");

        // Get the authentication token
        spotifyHelper.getClientCredentialsToken(new SpotifyHelper.SpotifyCallback() {
            @Override
            public void onSuccess(String token) {
                Request request = new Request.Builder()
                        .url(urlBuilder.build())
                        .header("Authorization", "Bearer " + token)
                        .build();

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        trackRequest();
                        Log.e(TAG, "Network error: " + e.getMessage());
                        errorMessage.postValue("Network error: " + e.getMessage());
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        trackRequest();
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String jsonData = response.body().string();
                                List<Track> newTracks = parseSearchResponse(jsonData);
                                
                                // Merge with existing results
                                List<Track> currentTracks = searchResults.getValue();
                                if (currentTracks == null) {
                                    currentTracks = new ArrayList<>();
                                }
                                
                                currentTracks.addAll(newTracks);
                                
                                // Update UI
                                searchResults.postValue(currentTracks);
                                
                                // Check if we have more results
                                hasMoreResults = newTracks.size() == DEFAULT_PAGE_SIZE;
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing pagination results: " + e.getMessage());
                                errorMessage.postValue("Error loading more results");
                            } finally {
                                isLoading.postValue(false);
                            }
                        } else {
                            String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                            Log.e(TAG, "API error: " + errorBody);
                            errorMessage.postValue("Error loading more results");
                            isLoading.postValue(false);
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Auth error: " + message);
                errorMessage.postValue("Authentication error");
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Clear search results and state
     */
    public void clearSearch() {
        currentQuery = "";
        currentType = "track,artist,album";
        currentOffset = 0;
        hasMoreResults = true;
        searchResults.setValue(new ArrayList<>());
        isLoading.setValue(false);
        errorMessage.setValue(null);
    }

    /**
     * Parse the search response from the Spotify API
     */
    private List<Track> parseSearchResponse(String jsonData) throws JSONException {
        List<Track> tracks = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(jsonData);
        
        // Parse tracks
        if (jsonObject.has("tracks")) {
            JSONObject tracksObj = jsonObject.getJSONObject("tracks");
            totalResults = tracksObj.optInt("total", 0);
            
            // Check if items exists and is an array
            if (tracksObj.has("items")) {
                JSONArray items = tracksObj.getJSONArray("items");
                
                for (int i = 0; i < items.length(); i++) {
                    JSONObject trackJson = items.getJSONObject(i);
                    
                    String id = trackJson.optString("id");
                    String name = trackJson.optString("name", "Unknown Track");
                    String previewUrl = trackJson.optString("preview_url");
                    
                    // Get album info
                    String albumName = "Unknown Album";
                    String albumArt = null;
                    
                    if (trackJson.has("album")) {
                        JSONObject albumJson = trackJson.getJSONObject("album");
                        albumName = albumJson.optString("name", "Unknown Album");
                        
                        JSONArray images = albumJson.optJSONArray("images");
                        if (images != null && images.length() > 0) {
                            albumArt = images.getJSONObject(0).optString("url");
                        }
                    }
                    
                    // Get artist info
                    String artist = "Unknown Artist";
                    JSONArray artists = trackJson.optJSONArray("artists");
                    if (artists != null && artists.length() > 0) {
                        artist = artists.getJSONObject(0).optString("name", "Unknown Artist");
                    }
                    
                    int duration = trackJson.optInt("duration_ms", 0);
                    int popularity = trackJson.optInt("popularity", 0);
                    
                    Track track = new Track(
                        id,
                        name,
                        artist,
                        albumName,
                        albumArt,
                        previewUrl,
                        duration,
                        id
                    );
                    
                    track.setSource(Track.SOURCE_SPOTIFY);
                    track.setPopularity(popularity);
                    track.setType("track");
                    track.setPlayable(previewUrl != null && !previewUrl.isEmpty());
                    
                    tracks.add(track);
                }
            }
        }
        
        // Parse artists if requested
        if (jsonObject.has("artists") && currentType.contains("artist")) {
            try {
                JSONObject artistsObj = jsonObject.getJSONObject("artists");
                if (artistsObj.has("items")) {
                    JSONArray items = artistsObj.getJSONArray("items");
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject artistJson = items.getJSONObject(i);
                        
                        String id = artistJson.optString("id");
                        String name = artistJson.optString("name", "Unknown Artist");
                        
                        // Get artist image if available
                        String artistImage = null;
                        JSONArray images = artistJson.optJSONArray("images");
                        if (images != null && images.length() > 0) {
                            artistImage = images.getJSONObject(0).optString("url");
                        }
                        
                        // Create a Track object to represent the artist
                        Track artistTrack = new Track(
                            id,
                            name,
                            "Artist",  // Use "Artist" as the artist name to indicate it's an artist entry
                            name + " - Top Tracks",
                            artistImage,
                            null,  // No preview URL for artists
                            0,
                            id
                        );
                        
                        artistTrack.setSource(Track.SOURCE_SPOTIFY);
                        artistTrack.setType("artist");
                        
                        tracks.add(artistTrack);
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing artists section: " + e.getMessage());
                // Continue without adding artists to the result
            }
        }
        
        // Parse albums if requested
        if (jsonObject.has("albums") && currentType.contains("album")) {
            try {
                JSONObject albumsObj = jsonObject.getJSONObject("albums");
                if (albumsObj.has("items")) {
                    JSONArray items = albumsObj.getJSONArray("items");
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject albumJson = items.getJSONObject(i);
                        
                        String id = albumJson.optString("id");
                        String name = albumJson.optString("name", "Unknown Album");
                        
                        // Get album image
                        String albumImage = null;
                        JSONArray images = albumJson.optJSONArray("images");
                        if (images != null && images.length() > 0) {
                            albumImage = images.getJSONObject(0).optString("url");
                        }
                        
                        // Get artist info
                        String artist = "Various Artists";
                        JSONArray artists = albumJson.optJSONArray("artists");
                        if (artists != null && artists.length() > 0) {
                            artist = artists.getJSONObject(0).optString("name", "Unknown Artist");
                        }
                        
                        // Create a Track object to represent the album
                        Track albumTrack = new Track(
                            id,
                            name,
                            artist,
                            "Album",
                            albumImage,
                            null,  // No preview URL for albums
                            0,
                            id
                        );
                        
                        albumTrack.setSource(Track.SOURCE_SPOTIFY);
                        albumTrack.setType("album");
                        
                        tracks.add(albumTrack);
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing albums section: " + e.getMessage());
                // Continue without adding albums to the result
            }
        }
        
        return tracks;
    }

    /**
     * Add a query to recent searches
     */
    private void addToRecentSearches(String query) {
        Set<String> searches = prefs.getStringSet(PREF_RECENT_SEARCHES, new HashSet<>());
        Set<String> updatedSearches = new HashSet<>(searches);
        
        // Remove if exists (to reorder)
        updatedSearches.remove(query);
        
        // Convert to list for ordering
        List<String> searchesList = new ArrayList<>(updatedSearches);
        
        // Add to beginning
        searchesList.add(0, query);
        
        // Trim to max size
        if (searchesList.size() > MAX_RECENT_SEARCHES) {
            searchesList = searchesList.subList(0, MAX_RECENT_SEARCHES);
        }
        
        // Convert back to set and save
        updatedSearches = new HashSet<>(searchesList);
        prefs.edit().putStringSet(PREF_RECENT_SEARCHES, updatedSearches).apply();
        
        // Update LiveData
        recentSearches.setValue(searchesList);
    }

    /**
     * Load recent searches from SharedPreferences
     */
    private void loadRecentSearches() {
        Set<String> searches = prefs.getStringSet(PREF_RECENT_SEARCHES, new HashSet<>());
        List<String> searchesList = new ArrayList<>(searches);
        recentSearches.setValue(searchesList);
    }

    /**
     * Clear recent searches
     */
    public void clearRecentSearches() {
        prefs.edit().remove(PREF_RECENT_SEARCHES).apply();
        recentSearches.setValue(new ArrayList<>());
    }

    /**
     * Generate a cache key for the query and type
     */
    private String getCacheKey(String query, String type) {
        return query + ":" + type;
    }

    /**
     * Track API request for rate limiting
     */
    private void trackRequest() {
        long currentTime = System.currentTimeMillis();
        
        // Reset counter if outside window
        if (currentTime - lastRequestTime > RATE_LIMIT_WINDOW) {
            requestCount = 0;
            lastRequestTime = currentTime;
        }
        
        requestCount++;
    }

    /**
     * Check if we can make an API request based on rate limiting
     */
    private boolean canMakeRequest() {
        long currentTime = System.currentTimeMillis();
        
        // Reset counter if outside window
        if (currentTime - lastRequestTime > RATE_LIMIT_WINDOW) {
            requestCount = 0;
            lastRequestTime = currentTime;
            return true;
        }
        
        return requestCount < RATE_LIMIT_MAX_REQUESTS;
    }

    /**
     * Get LiveData for search results
     */
    public LiveData<List<Track>> getSearchResults() {
        return searchResults;
    }

    /**
     * Get LiveData for loading state
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Get LiveData for error messages
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get LiveData for recent searches
     */
    public LiveData<List<String>> getRecentSearches() {
        return recentSearches;
    }

    /**
     * Check if there are more results to load
     */
    public boolean hasMoreResults() {
        return hasMoreResults;
    }

    /**
     * Get total results count
     */
    public int getTotalResults() {
        return totalResults;
    }

    /**
     * Add a search term to recent searches
     * @param query The search query to add
     */
    public void addRecentSearch(String query) {
        addToRecentSearches(query);
    }
} 