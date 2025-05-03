package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Album;
import my.edu.utar.bananamusic.models.Artist;
import my.edu.utar.bananamusic.models.MusicSource;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.repositories.MusicRepository;
import my.edu.utar.bananamusic.repositories.SpotifyRepository;

public class SearchHelper {
    private static final String TAG = "SearchHelper";
    private static final long DEBOUNCE_DELAY = 300; // milliseconds
    private static final long SEARCH_TIMEOUT = 10; // seconds
    
    private final Context context;
    private final MusicRepository localRepository;
    private final SpotifyRepository spotifyRepository;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private Runnable pendingSearch;
    private boolean isSearching = false;

    public interface SearchCallback {
        void onSearchStarted();
        void onSearchResult(List<Track> results);
        void onSearchError(String error);
    }

    public SearchHelper(Context context) {
        this.context = context;
        this.localRepository = MusicRepository.getInstance(context);
        this.spotifyRepository = SpotifyRepository.getInstance(context);
        this.executorService = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void search(String query, SearchCallback callback) {
        // Cancel any pending search
        if (pendingSearch != null) {
            mainHandler.removeCallbacks(pendingSearch);
        }

        // If query is empty or too short, return empty results
        if (TextUtils.isEmpty(query) || query.length() < 2) {
            mainHandler.post(() -> callback.onSearchResult(new ArrayList<>()));
            return;
        }

        // If already searching, wait for completion
        if (isSearching) {
            Log.d(TAG, "Search in progress, queuing new search");
            pendingSearch = () -> performSearchOperation(query, callback);
            mainHandler.postDelayed(pendingSearch, DEBOUNCE_DELAY);
            return;
        }

        pendingSearch = () -> performSearchOperation(query, callback);
        mainHandler.postDelayed(pendingSearch, DEBOUNCE_DELAY);
    }

    private void performSearchOperation(String query, SearchCallback callback) {
        isSearching = true;
        mainHandler.post(callback::onSearchStarted);

        executorService.execute(() -> {
            try {
                List<Track> results = new ArrayList<>();
                
                // Search in parallel
                Future<List<Track>> localFuture = executorService.submit(() -> searchLocal(query));
                Future<List<Track>> spotifyFuture = executorService.submit(() -> searchSpotify(query));
                
                try {
                    // Collect local results
                    results.addAll(localFuture.get());
                    
                    // Collect Spotify results
                    results.addAll(spotifyFuture.get());
                } catch (Exception e) {
                    Log.e(TAG, "Error searching for music: " + e.getMessage());
                    throw new Exception(context.getString(R.string.search_error), e);
                }

                mainHandler.post(() -> {
                    isSearching = false;
                    callback.onSearchResult(results);
                });
            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    isSearching = false;
                    callback.onSearchError("Search failed: " + e.getMessage());
                });
            }
        });
    }

    private List<Track> searchLocal(String query) {
        List<Track> results = new ArrayList<>();
        try {
            results = localRepository.searchTracks(query);
            // Mark tracks as local
            for (Track track : results) {
                track.setSource(Track.SOURCE_LOCAL);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error searching local tracks: " + e.getMessage());
        }
        return results;
    }
    
    private List<Track> searchSpotify(String query) {
        List<Track> results = new ArrayList<>();
        try {
            if (NetworkHelper.isNetworkAvailable(context)) {
                // Create a synchronized wrapper to get results
                Object lock = new Object();
                boolean[] done = {false};
                
                // Call Spotify repository with callback
                spotifyRepository.searchTracks(query, new SpotifyRepository.SearchCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        results.addAll(tracks);
                        // Mark tracks as from Spotify
                        for (Track track : results) {
                            track.setSource(Track.SOURCE_SPOTIFY);
                        }
                        done[0] = true;
                        synchronized (lock) {
                            lock.notify();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "Error searching Spotify tracks: " + errorMessage);
                        done[0] = true;
                        synchronized (lock) {
                            lock.notify();
                        }
                    }
                });
                
                // Wait for callback completion (with timeout)
                synchronized (lock) {
                    if (!done[0]) {
                        try {
                            lock.wait(5000); // 5 seconds timeout
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            Log.e(TAG, "Search interrupted: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error searching Spotify tracks: " + e.getMessage());
        }
        return results;
    }

    public void cleanup() {
        try {
            isSearching = false;
            if (pendingSearch != null) {
                mainHandler.removeCallbacks(pendingSearch);
            }
            executorService.shutdown();
            if (!executorService.awaitTermination(SEARCH_TIMEOUT, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
} 