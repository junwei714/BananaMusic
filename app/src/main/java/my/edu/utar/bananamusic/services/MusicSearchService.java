package my.edu.utar.bananamusic.services;

import android.util.Log;
import my.edu.utar.bananamusic.models.DeezerSearchResponse;
import my.edu.utar.bananamusic.models.SpotifySearchResponse;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.network.DeezerApiService;
import my.edu.utar.bananamusic.network.SpotifyApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MusicSearchService {
    private static final String TAG = "MusicSearchService";
    private final SpotifyApiService spotifyApiService;
    private final DeezerApiService deezerApiService;
    private final SpotifyAuthManager spotifyAuthManager;
    private static final int MAX_RETRIES = 3;
    private int retryCount = 0;

    public interface SearchCallback {
        void onSearchComplete(List<Track> tracks);
        void onSearchError(String error);
    }

    public MusicSearchService(SpotifyApiService spotifyApiService, DeezerApiService deezerApiService) {
        this.spotifyApiService = spotifyApiService;
        this.deezerApiService = deezerApiService;
        this.spotifyAuthManager = SpotifyAuthManager.getInstance();
    }

    public void searchTracks(String query, SearchCallback callback) {
        retryCount = 0;
        performSpotifyAuthAndSearch(query, callback);
    }

    private void performSpotifyAuthAndSearch(String query, SearchCallback callback) {
        Log.d(TAG, "Attempting Spotify authentication, retry count: " + retryCount);
        
        spotifyAuthManager.getToken(new SpotifyAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String token) {
                Log.d(TAG, "Successfully obtained Spotify token");
                performSpotifySearch(token, query, callback);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Spotify authentication failed: " + error);
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    Log.d(TAG, "Retrying authentication, attempt " + retryCount);
                    performSpotifyAuthAndSearch(query, callback);
                } else {
                    Log.e(TAG, "Max retry attempts reached, falling back to Deezer");
                    // Fall back to Deezer search
                    performDeezerSearch(query, callback);
                }
            }
        });
    }

    private void performSpotifySearch(String token, String query, SearchCallback callback) {
        Log.d(TAG, "Performing Spotify search with query: " + query);
        
        spotifyApiService.searchTracks("Bearer " + token, query, "track", 10)
            .enqueue(new Callback<SpotifySearchResponse>() {
                @Override
                public void onResponse(Call<SpotifySearchResponse> call, Response<SpotifySearchResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<SpotifySearchResponse.Track> spotifyTracks = response.body().getTracks().getItems();
                        processSpotifyResults(spotifyTracks, callback);
                    } else {
                        Log.e(TAG, "Spotify search failed with code: " + response.code());
                        // Fall back to Deezer search
                        performDeezerSearch(query, callback);
                    }
                }

                @Override
                public void onFailure(Call<SpotifySearchResponse> call, Throwable t) {
                    Log.e(TAG, "Spotify search error: " + t.getMessage());
                    // Fall back to Deezer search
                    performDeezerSearch(query, callback);
                }
            });
    }

    private void performDeezerSearch(String query, SearchCallback callback) {
        Log.d(TAG, "Falling back to Deezer search with query: " + query);
        
        deezerApiService.searchTrack(query)
            .enqueue(new Callback<DeezerSearchResponse>() {
                @Override
                public void onResponse(Call<DeezerSearchResponse> call, Response<DeezerSearchResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<Track> tracks = new ArrayList<>();
                        for (DeezerSearchResponse.Track deezerTrack : response.body().getTracks()) {
                            Track track = new Track();
                            track.setId(String.valueOf(deezerTrack.getId()));
                            track.setTitle(deezerTrack.getTitle());
                            track.setArtist(deezerTrack.getArtist().getName());
                            track.setAlbumArtUrl(deezerTrack.getAlbum().getCoverMedium());
                            track.setPreviewUrl(deezerTrack.getPreviewUrl());
                            track.setDuration(deezerTrack.getDuration() * 1000); // Convert to milliseconds
                            track.setPlayable(deezerTrack.getPreviewUrl() != null);
                            tracks.add(track);
                        }
                        callback.onSearchComplete(tracks);
                    } else {
                        callback.onSearchError("No results found");
                    }
                }

                @Override
                public void onFailure(Call<DeezerSearchResponse> call, Throwable t) {
                    callback.onSearchError("Search failed: " + t.getMessage());
                }
            });
    }

    private void processSpotifyResults(List<SpotifySearchResponse.Track> spotifyTracks, SearchCallback callback) {
        if (spotifyTracks.isEmpty()) {
            callback.onSearchComplete(new ArrayList<>());
            return;
        }

        List<Track> finalTracks = new ArrayList<>();
        AtomicInteger pendingRequests = new AtomicInteger(spotifyTracks.size());

        for (SpotifySearchResponse.Track spotifyTrack : spotifyTracks) {
            Track track = new Track();
            track.setId(spotifyTrack.getId());
            track.setTitle(spotifyTrack.getName());
            track.setArtist(spotifyTrack.getArtists().get(0).getName());
            if (!spotifyTrack.getAlbum().getImages().isEmpty()) {
                track.setAlbumArtUrl(spotifyTrack.getAlbum().getImages().get(0).getUrl());
            }
            track.setDuration(spotifyTrack.getDurationMs());
            track.setPlayable(true);
            track.setSource("spotify");
            track.setSpotifyId(spotifyTrack.getId());
            finalTracks.add(track);

            if (pendingRequests.decrementAndGet() == 0) {
                callback.onSearchComplete(finalTracks);
            }
        }
    }
} 