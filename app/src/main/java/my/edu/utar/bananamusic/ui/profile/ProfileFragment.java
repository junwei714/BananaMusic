package my.edu.utar.bananamusic.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.TrackAdapter;
import my.edu.utar.bananamusic.auth.LoginActivity;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.DummyDataProvider;
import my.edu.utar.bananamusic.utils.FirebaseAuthHelper;
import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.ui.base.SafeFragment;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.RecentlyPlayedManager;
import com.google.android.material.snackbar.Snackbar;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapters;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.utils.callbacks.BaseCallback;
import my.edu.utar.bananamusic.services.UnifiedMusicService;
import my.edu.utar.bananamusic.utils.RecentlyPlayedCache;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;

public class ProfileFragment extends SafeFragment {
    private static final String TAG = "ProfileFragment";

    private ImageView ivProfileImage;
    private TextView tvDisplayName, tvEmail, tvRecentlyPlayedTitle;
    private TextView tvErrorMessage;
    private Button btnEditProfile, btnLogout, btnClearHistory;
    private RecyclerView rvRecentlyPlayed;
    private ProgressBar progressBar;
    private TrackAdapter trackAdapter;
    private List<Track> recentTracks = new ArrayList<>();
    private FirebaseAuthHelper authHelper;
    private ApiDataProvider apiDataProvider;
    private UnifiedMusicService unifiedMusicService;
    private MediaPlayer mediaPlayer;
    private TrackAdapter recentAdapter;
    private TrackAdapter favoritesAdapter;
    private View emptyRecentView;
    private View emptyFavoritesView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View loadingView;
    private View errorView;
    private Button btnRetry;
    private RecentlyPlayedCache recentlyPlayedCache;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private int currentRetryAttempt = 0;
    private static final long RETRY_DELAY_MS = 1000; // Start with 1 second

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            authHelper = FirebaseAuthHelper.getInstance();
            apiDataProvider = ApiDataProvider.getInstance(requireContext());
            unifiedMusicService = UnifiedMusicService.getInstance(requireContext());
            recentlyPlayedCache = RecentlyPlayedCache.getInstance(requireContext());
        } catch (Exception e) {
            Log.e(TAG, "Error initializing services", e);
        }
        mediaPlayer = new MediaPlayer();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_profile, container, false);
            
            // Initialize views
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            loadingView = view.findViewById(R.id.loadingView);
            errorView = view.findViewById(R.id.errorView);
            tvErrorMessage = view.findViewById(R.id.tvErrorMessage);
            btnRetry = view.findViewById(R.id.btnRetry);
            rvRecentlyPlayed = view.findViewById(R.id.rvRecentlyPlayed);
            
            // Setup SwipeRefreshLayout
            swipeRefreshLayout.setOnRefreshListener(this::refreshRecentlyPlayed);
            
            // Setup retry button
            btnRetry.setOnClickListener(v -> refreshRecentlyPlayed());
            
            initViews(view);
            setupRecyclerView();
            loadUserData();
            loadRecentlyPlayed();
            setupClickListeners();
            
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout", e);
            return new View(requireContext());
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            initViews(view);
            setupRecyclerView();
            loadUserData();
            loadRecentlyPlayed();
            setupClickListeners();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
            showErrorState("Could not load profile. Please try again.");
        }
    }
    
    private void initViews(View view) {
        try {
            ivProfileImage = view.findViewById(R.id.ivProfileImage);
            tvDisplayName = view.findViewById(R.id.tvDisplayName);
            tvEmail = view.findViewById(R.id.tvEmail);
            tvRecentlyPlayedTitle = view.findViewById(R.id.tvRecentlyPlayedTitle);
            btnEditProfile = view.findViewById(R.id.btnEditProfile);
            btnLogout = view.findViewById(R.id.btnLogout);
            btnClearHistory = view.findViewById(R.id.btnClearHistory);
            
            // Find progress bar and error message (add to your layout)
            progressBar = view.findViewById(R.id.progress_bar);
            
            // Log missing views instead of crashing
            if (progressBar == null) {
                Log.w(TAG, "Progress bar not found in layout");
            }
            
            if (tvErrorMessage == null) {
                Log.w(TAG, "Error message TextView not found in layout");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
        }
    }
    
    private void setupRecyclerView() {
        try {
            trackAdapter = new TrackAdapter(requireContext(), recentTracks, (track, position) -> {
                try {
                    if (track == null) {
                        Log.e(TAG, "Attempted to play null track");
                        return;
                    }
                    
                    // Track clicked - play the track
                    if (isAdded() && getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateMiniPlayer(track);
                        Toast.makeText(requireContext(), "Playing: " + track.getTitle(), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error playing track", e);
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Could not play track", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            
            if (rvRecentlyPlayed != null) {
                rvRecentlyPlayed.setLayoutManager(new LinearLayoutManager(requireContext()));
                rvRecentlyPlayed.setAdapter(trackAdapter);
            } else {
                Log.e(TAG, "RecyclerView is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerView", e);
        }
    }
    
    private void loadUserData() {
        try {
            showLoading(true);
            
            if (authHelper == null) {
                Log.e(TAG, "Auth helper is null");
                showErrorState("Error loading user data");
                return;
            }
            
            FirebaseUser user = authHelper.getCurrentUser();
            if (user != null) {
                String displayName = user.getDisplayName();
                String email = user.getEmail();
                
                if (tvDisplayName != null) {
                    tvDisplayName.setText(displayName != null && !displayName.isEmpty() ? displayName : "User");
                }
                
                if (tvEmail != null) {
                    tvEmail.setText(email != null && !email.isEmpty() ? email : "No email available");
                }
                
                if (ivProfileImage != null) {
                    if (user.getPhotoUrl() != null) {
                        try {
                            Glide.with(this)
                                .load(user.getPhotoUrl())
                                .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .error(R.drawable.ic_profile_placeholder)
                                .into(ivProfileImage);
                        } catch (Exception e) {
                            Log.e(TAG, "Error loading profile image", e);
                            ivProfileImage.setImageResource(R.drawable.ic_profile_placeholder);
                        }
                    } else {
                        ivProfileImage.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                }
                
                // Show profile content
                showProfileContent(true);
            } else {
                Log.d(TAG, "User is not logged in");
                // Show guest user info
                if (tvDisplayName != null) {
                    tvDisplayName.setText(R.string.guest_user);
                }
                
                if (tvEmail != null) {
                    tvEmail.setText(R.string.sign_in_to_access);
                }
                
                if (ivProfileImage != null) {
                    ivProfileImage.setImageResource(R.drawable.ic_profile_placeholder);
                }
                
                // Show login button instead of profile actions
                if (btnEditProfile != null) {
                    btnEditProfile.setText(R.string.login);
                    btnEditProfile.setVisibility(View.VISIBLE);
                }
                
                // Show profile content
                showProfileContent(true);
            }
            
            showLoading(false);
        } catch (Exception e) {
            Log.e(TAG, "Error loading user data", e);
            showLoading(false);
            showErrorState("Could not load profile data");
        }
    }
    
    private void loadRecentlyPlayed() {
        showLoading(true);
        
        // Get tracks from cache
        List<Track> cachedTracks = recentlyPlayedCache.getCachedTracks();
        if (!cachedTracks.isEmpty()) {
            updateRecentlyPlayedTracks(cachedTracks);
            showLoading(false);
        } else {
            handleEmptyState();
            showLoading(false);
        }
    }
    
    private void refreshRecentlyPlayed() {
        currentRetryAttempt = 0;
        loadRecentlyPlayedWithRetry();
    }

    private void loadRecentlyPlayedWithRetry() {
        if (currentRetryAttempt >= MAX_RETRY_ATTEMPTS) {
            handleError("Failed to load after several attempts");
            return;
        }

        showLoading(true);
        long delay = RETRY_DELAY_MS * (long) Math.pow(2, currentRetryAttempt);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            unifiedMusicService.searchTracks("user:recent", new UnifiedMusicService.MusicCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        recentlyPlayedCache.cacheTracks(tracks);
                        updateRecentlyPlayedTracks(tracks);
                        showLoading(false);
                    } else {
                        handleEmptyState();
                    }
                }

                @Override
                public void onError(String message) {
                    currentRetryAttempt++;
                    if (currentRetryAttempt < MAX_RETRY_ATTEMPTS) {
                        loadRecentlyPlayedWithRetry();
                    } else {
                        handleError("Couldn't load recent tracks");
                    }
                }
            });
        }, delay);
    }

    private void updateRecentlyPlayedTracks(List<Track> tracks) {
        if (!isAdded()) return;
        
        try {
            if (tracks != null && !tracks.isEmpty()) {
                if (trackAdapter != null) {
                    trackAdapter.updateTrackList(tracks);
                }
                showContent();
            } else {
                handleEmptyState();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating tracks", e);
            handleError("Error updating tracks");
        }
    }

    private void showLoading(boolean show) {
        if (!isAdded()) return;
        
        if (loadingView != null) {
            loadingView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            errorView.setVisibility(View.GONE);
            if (trackAdapter != null && trackAdapter.getItemCount() == 0) {
                rvRecentlyPlayed.setVisibility(View.GONE);
            }
        }
    }

    private void showContent() {
        if (!isAdded()) return;
        
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        rvRecentlyPlayed.setVisibility(View.VISIBLE);
    }

    private void handleEmptyState() {
        if (!isAdded()) return;
        
        loadingView.setVisibility(View.GONE);
        rvRecentlyPlayed.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        tvErrorMessage.setText("No recently played tracks");
        btnRetry.setVisibility(View.VISIBLE);
    }

    private void handleError(String message) {
        if (!isAdded()) return;
        
        loadingView.setVisibility(View.GONE);
        rvRecentlyPlayed.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        tvErrorMessage.setText(message);
        btnRetry.setVisibility(View.VISIBLE);
    }

    private void showSnackbar(String message) {
        if (!isAdded()) return;
        
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
    }
    
    private void setupClickListeners() {
        try {
            if (btnEditProfile != null) {
                btnEditProfile.setOnClickListener(v -> {
                    try {
                        if (authHelper != null && authHelper.getCurrentUser() != null) {
                            // Launch the edit profile activity
                            Intent intent = new Intent(requireContext(), my.edu.utar.bananamusic.activities.EditProfileActivity.class);
                            startActivity(intent);
                        } else {
                            // User is not logged in, redirect to login
                            if (isAdded() && getContext() != null) {
                                startActivity(new Intent(requireContext(), LoginActivity.class));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling edit profile click", e);
                    }
                });
            }
            
            if (btnLogout != null) {
                btnLogout.setOnClickListener(v -> {
                    try {
                        if (authHelper != null) {
                            authHelper.signOut();
                            if (isAdded() && getContext() != null) {
                                startActivity(new Intent(requireContext(), LoginActivity.class));
                                if (getActivity() != null) {
                                    getActivity().finish();
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error logging out", e);
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(requireContext(), "Error logging out", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            if (btnClearHistory != null) {
                btnClearHistory.setOnClickListener(v -> {
                    recentlyPlayedCache.clearCache();
                    loadRecentlyPlayed();
                });
            }
            
            if (btnRetry != null) {
                btnRetry.setOnClickListener(v -> loadRecentlyPlayed());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners", e);
        }
    }
    
    private void showErrorState(String message) {
        try {
            if (tvErrorMessage != null) {
                tvErrorMessage.setText(message);
                tvErrorMessage.setVisibility(View.VISIBLE);
            }
            
            showProfileContent(false);
        } catch (Exception e) {
            Log.e(TAG, "Error showing error state", e);
        }
    }
    
    private void showProfileContent(boolean show) {
        try {
            int visibility = show ? View.VISIBLE : View.GONE;
            
            if (tvDisplayName != null) tvDisplayName.setVisibility(visibility);
            if (tvEmail != null) tvEmail.setVisibility(visibility);
            if (ivProfileImage != null) ivProfileImage.setVisibility(visibility);
            if (btnEditProfile != null) btnEditProfile.setVisibility(visibility);
            if (btnLogout != null) btnLogout.setVisibility(visibility);
            
            // Recently played section visibility is managed separately
        } catch (Exception e) {
            Log.e(TAG, "Error showing/hiding profile content", e);
        }
    }
    
    // Helper method to show errors on the main thread
    private void showErrorOnMainThread(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            showLoading(false);
            hideRecentlyPlayedSection();
            if (isAdded() && getContext() != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void onDestroyView() {
        try {
            // Clean up resources
            recentTracks.clear();
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroyView", e);
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Reload user data to reflect any changes made in EditProfileActivity
        loadUserData();
        
        // Reload recently played tracks when fragment is resumed
        // This ensures the list updates after a user plays a song and returns to the profile screen
        loadRecentlyPlayed();
    }

    /**
     * Show message when no recently played tracks are available
     */
    private void showNoRecentlyPlayedMessage() {
        rvRecentlyPlayed.setVisibility(View.GONE);
        showErrorState("No recently played tracks available");
    }

    private void showEmptyState() {
        showErrorState("No recently played tracks available");
    }

    private void showError(String message) {
        showErrorState(message);
    }

    private void loadUserFavorites() {
        showLoadingIndicator();
        unifiedMusicService.searchTracks("user:favorites", new UnifiedMusicService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                hideLoadingIndicator();
                updateFavoriteTracks(tracks);
            }

            @Override
            public void onError(String message) {
                hideLoadingIndicator();
                showError("Failed to load favorites: " + message);
            }
        });
    }

    private void updateFavoriteTracks(List<Track> tracks) {
        if (tracks.isEmpty()) {
            showEmptyFavorites();
        } else {
            hideEmptyFavorites();
            favoritesAdapter.updateTrackList(tracks);
        }
    }

    private void updateRecentTracks(List<Track> tracks) {
        if (tracks.isEmpty()) {
            showEmptyRecent();
        } else {
            hideEmptyRecent();
            recentAdapter.updateTrackList(tracks);
        }
    }

    private void playTrack(Track track) {
        showLoadingIndicator();
        unifiedMusicService.getTrackPreview(track, new UnifiedMusicService.TrackPreviewCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                hideLoadingIndicator();
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    playAudio(previewUrl, track);
                } else {
                    showError("No preview available for this track");
                }
            }

            @Override
            public void onError(String message) {
                hideLoadingIndicator();
                showError("Failed to play track: " + message);
            }
        });
    }

    private void showLoadingIndicator() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoadingIndicator() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void showEmptyFavorites() {
        if (emptyFavoritesView != null) {
            emptyFavoritesView.setVisibility(View.VISIBLE);
        }
    }

    private void hideEmptyFavorites() {
        if (emptyFavoritesView != null) {
            emptyFavoritesView.setVisibility(View.GONE);
        }
    }

    private void showEmptyRecent() {
        if (emptyRecentView != null) {
            emptyRecentView.setVisibility(View.VISIBLE);
        }
    }

    private void hideEmptyRecent() {
        if (emptyRecentView != null) {
            emptyRecentView.setVisibility(View.GONE);
        }
    }

    private void playAudio(String previewUrl, Track track) {
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            try {
                mediaPlayer.setDataSource(previewUrl);
                mediaPlayer.prepare();
                mediaPlayer.start();
                updateNowPlayingUI(track);
            } catch (IOException e) {
                showError("Error playing track: " + e.getMessage());
            }
        }
    }

    private void updateNowPlayingUI(Track track) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateMiniPlayer(track);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void hideRecentlyPlayedSection() {
        if (rvRecentlyPlayed != null) {
            rvRecentlyPlayed.setVisibility(View.GONE);
        }
        if (tvRecentlyPlayedTitle != null) {
            tvRecentlyPlayedTitle.setVisibility(View.GONE);
        }
    }
} 