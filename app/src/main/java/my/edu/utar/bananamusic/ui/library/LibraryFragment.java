package my.edu.utar.bananamusic.ui.library;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.EnhancedPlaylistAdapter;
import my.edu.utar.bananamusic.adapters.PlaylistAdapter;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.ui.base.SafeFragment;
import my.edu.utar.bananamusic.utils.FirebaseAuthHelper;
import my.edu.utar.bananamusic.utils.PlaylistDetailsHelper;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.RecyclerViewSafety;
import my.edu.utar.bananamusic.utils.SafeRecyclerViewLayoutManager;
import my.edu.utar.bananamusic.ui.playlist.CreatePlaylistActivity;
import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.services.UnifiedMusicService;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.ui.dialogs.AuthRequiredDialog;
import my.edu.utar.bananamusic.utils.DeezerHelper;
import my.edu.utar.bananamusic.ui.dialogs.CreatePlaylistDialogFragment;
import android.view.Gravity;
import android.widget.AutoCompleteTextView;

public class LibraryFragment extends SafeFragment implements CreatePlaylistDialogFragment.PlaylistCreationListener {
    private static final String TAG = "LibraryFragment";

    private TextView tvYourLibrary;
    private TextView tvUserPlaylistsHeader;
    private TextView tvCollaborativePlaylistsHeader;
    private TextView tvErrorMessage;
    private RecyclerView rvUserPlaylists, rvCollaborativePlaylists;
    private FloatingActionButton fabCreatePlaylist;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EnhancedPlaylistAdapter userPlaylistAdapter, collaborativePlaylistAdapter;
    private List<Playlist> userPlaylists = new ArrayList<>();
    private List<Playlist> collaborativePlaylists = new ArrayList<>();
    private FirebaseAuthHelper authHelper;
    
    // Shimmer layouts
    private com.facebook.shimmer.ShimmerFrameLayout shimmerUserPlaylists;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerCollaborativePlaylists;
    private View emptyView;

    private UnifiedMusicService unifiedMusicService;
    private MediaPlayer mediaPlayer;
    private PlaylistAdapter playlistAdapter;
    private PlaylistManager playlistManager;
    private AudioPlayerHelper audioPlayerHelper;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authHelper = FirebaseAuthHelper.getInstance();
        unifiedMusicService = UnifiedMusicService.getInstance(requireContext());
        mediaPlayer = new MediaPlayer();
        playlistAdapter = new PlaylistAdapter(requireContext());
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        
        // Set has options menu to true
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_library, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_repair_library) {
            // Call repair method in MainActivity
            if (getActivity() instanceof MainActivity) {
                Toast.makeText(requireContext(), "Repairing library...", Toast.LENGTH_SHORT).show();
                ((MainActivity) getActivity()).repairCollaborativePlaylists();
                return true;
            }
        }
        
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_library, container, false);
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
            setupRecyclerViews();
            setupSwipeRefresh();
            loadUserData();
            loadLibraryData();
            
            if (fabCreatePlaylist != null) {
                fabCreatePlaylist.setOnClickListener(v -> {
                    try {
                        // Show the CreatePlaylistDialogFragment
                        CreatePlaylistDialogFragment dialog = new CreatePlaylistDialogFragment();
                        dialog.setOnDismissListener(dialogInterface -> {
                            // Refresh playlists when dialog is dismissed
                            refreshPlaylists();
                        });
                        dialog.show(getChildFragmentManager(), "create_playlist");
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing playlist creation dialog", e);
                        Toast.makeText(requireContext(), "Could not create playlist", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
            showErrorState("Could not load your library. Please try again.");
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Refresh playlists when returning from create playlist
        if (requestCode == 1001) {
            Log.d(TAG, "Returned from playlist creation, refreshing playlists");
            refreshPlaylists();
        }
    }
    
    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                Log.d(TAG, "Swipe refresh triggered");
                refreshPlaylists();
            });
            
            // Set nice, music-themed colors
            swipeRefreshLayout.setColorSchemeResources(
                R.color.colorPrimary,     // Spotify Green
                R.color.colorHappy,       // Happy Yellow
                R.color.colorEnergetic,   // Energetic Red
                R.color.colorSad          // Sad Blue
            );
            
            // Set the background color of the refresh indicator
            swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.colorPrimaryDark);
            
            // Set the distance to trigger a refresh
            swipeRefreshLayout.setDistanceToTriggerSync(300);
            
            // Make the refresh indicator larger and more visible
            swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);
        }
    }
    
    private void initViews(View view) {
        try {
            tvYourLibrary = view.findViewById(R.id.tv_your_library);
            rvUserPlaylists = view.findViewById(R.id.rv_user_playlists);
            rvCollaborativePlaylists = view.findViewById(R.id.rv_collaborative_playlists);
            fabCreatePlaylist = view.findViewById(R.id.fab_create_playlist);
            swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
            
            // Add headers for sections
            tvUserPlaylistsHeader = view.findViewById(R.id.tv_user_playlists_header);
            tvCollaborativePlaylistsHeader = view.findViewById(R.id.tv_collaborative_playlists_header);
            
            // Add progress bar and error message
            progressBar = view.findViewById(R.id.progress_bar);
            tvErrorMessage = view.findViewById(R.id.tv_error_message);
            
            // Log missing views instead of crashing
            if (progressBar == null) {
                Log.w(TAG, "Progress bar not found in layout");
            }
            
            if (tvErrorMessage == null) {
                Log.w(TAG, "Error message TextView not found in layout");
            }
            
            if (swipeRefreshLayout == null) {
                Log.w(TAG, "SwipeRefreshLayout not found in layout - add it to your layout");
            }
            
            // Shimmer layouts
            shimmerUserPlaylists = view.findViewById(R.id.shimmer_user_playlists);
            shimmerCollaborativePlaylists = view.findViewById(R.id.shimmer_collaborative_playlists);
            
            // Find empty view - temporarily setting to error message view
            emptyView = view.findViewById(R.id.tv_error_message);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
        }
    }
    
    private void setupRecyclerViews() {
        try {
            // User Playlists RecyclerView
            EnhancedPlaylistAdapter.PlaylistClickListener userListener = new EnhancedPlaylistAdapter.PlaylistClickListener() {
                @Override
                public void onPlaylistClick(Playlist playlist) {
                    if (playlist != null) {
                        openPlaylistDetails(playlist);
                    }
                }

                @Override
                public void onTrackClick(Track track) {
                    if (track != null) {
                        onTrackSelected(track);
                    }
                }
            };
            
            userPlaylistAdapter = new EnhancedPlaylistAdapter(requireContext(), userPlaylists, userListener);
            
            if (rvUserPlaylists != null) {
                // Use vertical layout for better showcase of tracks
                rvUserPlaylists.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                    requireContext(), androidx.recyclerview.widget.LinearLayoutManager.VERTICAL, false));
                rvUserPlaylists.setAdapter(userPlaylistAdapter);
                
                // Add item decoration for spacing
                int spacing = (int) getResources().getDimension(R.dimen.item_spacing);
                rvUserPlaylists.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                    @Override
                    public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, 
                                              @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                        outRect.bottom = spacing;
                    }
                });
                
                // Make sure recycler view is visible
                rvUserPlaylists.setVisibility(View.VISIBLE);
            } else {
                Log.e(TAG, "User playlists RecyclerView is null");
            }
            
            // Collaborative Playlists RecyclerView
            EnhancedPlaylistAdapter.PlaylistClickListener collaborativeListener = new EnhancedPlaylistAdapter.PlaylistClickListener() {
                @Override
                public void onPlaylistClick(Playlist playlist) {
                    if (playlist != null) {
                        openPlaylistDetails(playlist);
                    }
                }

                @Override
                public void onTrackClick(Track track) {
                    if (track != null) {
                        onTrackSelected(track);
                    }
                }
            };
            
            collaborativePlaylistAdapter = new EnhancedPlaylistAdapter(requireContext(), collaborativePlaylists, collaborativeListener);
            
            if (rvCollaborativePlaylists != null) {
                // Use vertical layout for better showcase of tracks
                rvCollaborativePlaylists.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                    requireContext(), androidx.recyclerview.widget.LinearLayoutManager.VERTICAL, false));
                rvCollaborativePlaylists.setAdapter(collaborativePlaylistAdapter);
                
                // Add item decoration for spacing
                int spacing = (int) getResources().getDimension(R.dimen.item_spacing);
                rvCollaborativePlaylists.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                    @Override
                    public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, 
                                              @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                        outRect.bottom = spacing;
                    }
                });
                
                // Make sure recycler view is visible
                rvCollaborativePlaylists.setVisibility(View.VISIBLE);
            } else {
                Log.e(TAG, "Collaborative playlists RecyclerView is null");
            }
            
            // Add sample playlists if there are none
            addSamplePlaylists();
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerViews", e);
        }
    }
    
    /**
     * This method used to add sample playlists but no longer does.
     * We now wait for real data to load instead of showing dummy playlists.
     */
    private void addSamplePlaylists() {
        // This method intentionally left empty - no longer adding sample playlists
        // Real playlists will be loaded from the API/database instead
        Log.d(TAG, "Sample playlists no longer added - waiting for real data");
    }
    
    private void loadUserData() {
        try {
            if (authHelper == null) {
                Log.e(TAG, "Auth helper is null");
                return;
            }
            
            FirebaseUser user = authHelper.getCurrentUser();
            if (user != null) {
                String displayName = user.getDisplayName();
                if (tvYourLibrary != null) {
                    tvYourLibrary.setText(displayName != null && !displayName.isEmpty() ? 
                            displayName + "'s Library" : "Your Library");
                }
            } else {
                Log.d(TAG, "User is not logged in");
                if (tvYourLibrary != null) {
                    tvYourLibrary.setText(R.string.your_library);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading user data", e);
            if (tvYourLibrary != null) {
                tvYourLibrary.setText(R.string.your_library);
            }
        }
    }
    
    /**
     * Load the data for the library
     */
    private void loadLibraryData() {
        if (playlistManager != null) {
            // Update all playlist covers to ensure they have images
            playlistManager.updateAllPlaylistCovers();
            
            // Load user playlists
            loadUserPlaylists();
            
            // Load collaborative playlists
            loadCollaborativePlaylists();
        }
    }
    
    private void updatePlaylistSections() {
        // Stop shimmer and hide shimmer containers
        if (shimmerUserPlaylists != null) {
            shimmerUserPlaylists.stopShimmer();
            shimmerUserPlaylists.setVisibility(View.GONE);
        }
        
        if (shimmerCollaborativePlaylists != null) {
            shimmerCollaborativePlaylists.stopShimmer();
            shimmerCollaborativePlaylists.setVisibility(View.GONE);
        }
        
        // Check if we have playlists to display
        boolean hasUserPlaylists = userPlaylistAdapter != null && 
                                  userPlaylistAdapter.getItemCount() > 0;
        boolean hasCollaborativePlaylists = collaborativePlaylistAdapter != null && 
                                          collaborativePlaylistAdapter.getItemCount() > 0;
        
        // Show/hide section headers based on playlist availability
        if (tvUserPlaylistsHeader != null) {
            tvUserPlaylistsHeader.setVisibility(hasUserPlaylists ? View.VISIBLE : View.GONE);
        }
        
        if (tvCollaborativePlaylistsHeader != null) {
            // Update the collaborative playlists header to indicate they are collected playlists
            tvCollaborativePlaylistsHeader.setText("Collected Playlists");
            tvCollaborativePlaylistsHeader.setVisibility(hasCollaborativePlaylists ? View.VISIBLE : View.GONE);
        }
        
        if (rvUserPlaylists != null) {
            rvUserPlaylists.setVisibility(hasUserPlaylists ? View.VISIBLE : View.GONE);
        }
        
        if (rvCollaborativePlaylists != null) {
            rvCollaborativePlaylists.setVisibility(hasCollaborativePlaylists ? View.VISIBLE : View.GONE);
        }
        
        // Show empty state if no playlists
        if (!hasUserPlaylists && !hasCollaborativePlaylists) {
            showEmptyState();
        } else {
            hideErrorState();
        }
        
        // Hide loading
        showLoading(false);
        
        // Stop refresh animation if active
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
    
    private void showLoading(boolean isLoading) {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE); // Always hide the old progress bar
            }
            
            // Handle shimmer effects
            if (shimmerUserPlaylists != null) {
                if (isLoading) {
                    shimmerUserPlaylists.setVisibility(View.VISIBLE);
                    shimmerUserPlaylists.startShimmer();
                    
                    // Hide the actual RecyclerView while shimmer is showing
                    if (rvUserPlaylists != null) {
                        rvUserPlaylists.setVisibility(View.GONE);
                    }
                } else {
                    shimmerUserPlaylists.stopShimmer();
                    shimmerUserPlaylists.setVisibility(View.GONE);
                    
                    // Show the RecyclerView when loading is done
                    if (rvUserPlaylists != null) {
                        rvUserPlaylists.setVisibility(View.VISIBLE);
                    }
                }
            }
            
            if (shimmerCollaborativePlaylists != null) {
                if (isLoading) {
                    shimmerCollaborativePlaylists.setVisibility(View.VISIBLE);
                    shimmerCollaborativePlaylists.startShimmer();
                    
                    // Hide the actual RecyclerView while shimmer is showing
                    if (rvCollaborativePlaylists != null) {
                        rvCollaborativePlaylists.setVisibility(View.GONE);
                    }
                } else {
                    shimmerCollaborativePlaylists.stopShimmer();
                    shimmerCollaborativePlaylists.setVisibility(View.GONE);
                    
                    // Show the RecyclerView when loading is done (if it has items)
                    if (rvCollaborativePlaylists != null) {
                        rvCollaborativePlaylists.setVisibility(View.VISIBLE);
                    }
                }
            }
            
            // Also update swipe refresh if we're using it
            if (swipeRefreshLayout != null && !isLoading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing/hiding loading state", e);
        }
    }
    
    private void showEmptyState() {
        try {
            if (tvErrorMessage != null) {
                tvErrorMessage.setText(R.string.no_playlists_found);
                tvErrorMessage.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing empty state", e);
        }
    }
    
    private void showErrorState(String message) {
        try {
            if (tvErrorMessage != null) {
                tvErrorMessage.setText(message);
                tvErrorMessage.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing error state", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            // Refresh playlists each time the fragment is resumed
            // This ensures newly created playlists appear in the list
            refreshPlaylists();
            Log.d(TAG, "LibraryFragment resumed, refreshing playlists");
            // Remove toast notification here
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
            if (isAdded()) {
                Toast.makeText(requireContext(), "Error refreshing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * Refreshes both personal and collaborative playlists
     * Call this method after creating or modifying a playlist
     */
    public void refreshPlaylists() {
        try {
            Log.d(TAG, "Refreshing playlists");
            showLoading(true); // This will now show shimmer effects instead of progress bar
            hideErrorState();
            
            if (authHelper == null) {
                authHelper = FirebaseAuthHelper.getInstance();
            }
            
            if (authHelper.isUserLoggedIn()) {
                // Get the PlaylistManager instance
                if (playlistManager == null) {
                    playlistManager = PlaylistManager.getInstance(requireContext());
                }
                
                // First, load the user's playlists
                loadUserPlaylists();
                
                // Then load collaborative playlists
                loadCollaborativePlaylists();
            } else {
                Log.w(TAG, "User not logged in, showing auth required");
                showAuthRequired();
                showLoading(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing playlists", e);
            showErrorState("Error refreshing playlists: " + e.getMessage());
            showLoading(false);
        }
    }
    
    /**
     * Show authentication required dialog
     */
    private void showAuthRequired() {
        try {
            if (!isAdded() || getContext() == null) return;
            
            // Show a dialog informing the user they need to log in
            AuthRequiredDialog dialog = AuthRequiredDialog.newInstance(
                    "You need to log in to view your playlists and access your library."
            );
            
            dialog.show(getChildFragmentManager());
        } catch (Exception e) {
            Log.e(TAG, "Error showing auth required dialog", e);
            // Fallback to simple error message
            showErrorState("Please log in to view your playlists");
        }
    }
    
    /**
     * Load user playlists from Firebase
     */
    private void loadUserPlaylists() {
        if (!isAdded() || playlistManager == null) return;
        
        Log.d(TAG, "Loading user playlists");
        
        // Show shimmer for user playlists
        if (shimmerUserPlaylists != null) {
            shimmerUserPlaylists.setVisibility(View.VISIBLE);
            shimmerUserPlaylists.startShimmer();
        }
        
        // Get the current user
        FirebaseUser user = authHelper.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "User not logged in, can't load user playlists");
            if (shimmerUserPlaylists != null) {
                shimmerUserPlaylists.hideShimmer();
                shimmerUserPlaylists.setVisibility(View.GONE);
            }
            showErrorState("Please log in to view your playlists");
            showLoading(false);
            return;
        }
        
        // Listen for user playlists
        playlistManager.getUserPlaylists(new PlaylistManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (!isAdded()) return;
                
                Log.d(TAG, "Got " + (playlists != null ? playlists.size() : 0) + " user playlists");
                
                // Hide shimmer
                if (shimmerUserPlaylists != null) {
                    shimmerUserPlaylists.hideShimmer();
                    shimmerUserPlaylists.setVisibility(View.GONE);
                }
                
                // Update UI
                updateUserPlaylists(playlists);
            }
            
            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                
                Log.e(TAG, "Error loading user playlists: " + message);
                
                // Hide shimmer
                if (shimmerUserPlaylists != null) {
                    shimmerUserPlaylists.hideShimmer();
                    shimmerUserPlaylists.setVisibility(View.GONE);
                }
                
                // Show error
                showErrorState("Error loading your playlists: " + message);
                showLoading(false);
            }
        });
    }
    
    /**
     * Load collaborative playlists from Firebase
     */
    private void loadCollaborativePlaylists() {
        if (!isAdded() || playlistManager == null) return;
        
        Log.d(TAG, "Loading collaborative playlists");
        
        // Show shimmer for collaborative playlists
        if (shimmerCollaborativePlaylists != null) {
            shimmerCollaborativePlaylists.setVisibility(View.VISIBLE);
            shimmerCollaborativePlaylists.startShimmer();
        }
        
        // Get the current user
        FirebaseUser user = authHelper.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "User not logged in, can't load collaborative playlists");
            if (shimmerCollaborativePlaylists != null) {
                shimmerCollaborativePlaylists.hideShimmer();
                shimmerCollaborativePlaylists.setVisibility(View.GONE);
            }
            return;
        }
        
        // Listen for collaborative playlists
        playlistManager.getCollaborativePlaylists(new PlaylistManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (!isAdded()) return;
                
                Log.d(TAG, "Got " + (playlists != null ? playlists.size() : 0) + " collaborative playlists");
                
                // Hide shimmer
                if (shimmerCollaborativePlaylists != null) {
                    shimmerCollaborativePlaylists.hideShimmer();
                    shimmerCollaborativePlaylists.setVisibility(View.GONE);
                }
                
                // Update UI
                updateCollaborativePlaylists(playlists);
                
                // Hide loading when both types of playlists are loaded
                showLoading(false);
            }
            
            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                
                Log.e(TAG, "Error loading collaborative playlists: " + message);
                
                // Hide shimmer
                if (shimmerCollaborativePlaylists != null) {
                    shimmerCollaborativePlaylists.hideShimmer();
                    shimmerCollaborativePlaylists.setVisibility(View.GONE);
                }
                
                // Show error toast but don't block the UI since user playlists might have loaded
                Toast.makeText(requireContext(), 
                    "Error loading collaborative playlists: " + message, 
                    Toast.LENGTH_SHORT).show();
                
                // Hide loading
                showLoading(false);
            }
        });
    }
    
    /**
     * Update the UI with user playlists
     */
    private void updateUserPlaylists(List<Playlist> playlists) {
        if (!isAdded()) return;
        
        // Filter out collaborative playlists
        List<Playlist> nonCollaborativePlaylists = new ArrayList<>();
        if (playlists != null) {
            for (Playlist playlist : playlists) {
                if (!playlist.isCollaborative()) {
                    nonCollaborativePlaylists.add(playlist);
                }
            }
        }
        
        // Save reference to playlists
        this.userPlaylists = nonCollaborativePlaylists;
        
        // Show/hide empty state
        boolean isEmpty = this.userPlaylists.isEmpty();
        if (tvUserPlaylistsHeader != null) {
            tvUserPlaylistsHeader.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        
        if (rvUserPlaylists != null) {
            rvUserPlaylists.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(isEmpty && this.collaborativePlaylists.isEmpty() ? 
                View.VISIBLE : View.GONE);
        }
        
        // Update adapter
        if (rvUserPlaylists != null) {
            if (userPlaylistAdapter == null) {
                userPlaylistAdapter = new EnhancedPlaylistAdapter(requireContext(), this.userPlaylists, 
                    new EnhancedPlaylistAdapter.PlaylistClickListener() {
                        @Override
                        public void onPlaylistClick(Playlist playlist) {
                            if (playlist != null) {
                                openPlaylistDetails(playlist);
                            }
                        }

                        @Override
                        public void onTrackClick(Track track) {
                            if (track != null) {
                                onTrackSelected(track);
                            }
                        }
                    });
                rvUserPlaylists.setAdapter(userPlaylistAdapter);
            } else {
                userPlaylistAdapter.updateData(this.userPlaylists);
            }
        }
    }
    
    /**
     * Update the UI with collaborative playlists
     */
    private void updateCollaborativePlaylists(List<Playlist> playlists) {
        if (!isAdded()) return;
        
        // Filter out playlists that the user owns
        FirebaseUser currentUser = authHelper.getCurrentUser();
        List<Playlist> filteredPlaylists = new ArrayList<>();
        
        if (playlists != null && currentUser != null) {
            String userId = currentUser.getUid();
            for (Playlist playlist : playlists) {
                // Only add collaborative playlists that the user doesn't own
                if (playlist.isCollaborative() && !userId.equals(playlist.getCreatorId())) {
                    filteredPlaylists.add(playlist);
                }
            }
        }
        
        // Save reference to playlists
        this.collaborativePlaylists = filteredPlaylists;
        
        // Show/hide empty state
        boolean isEmpty = this.collaborativePlaylists.isEmpty();
        if (tvCollaborativePlaylistsHeader != null) {
            tvCollaborativePlaylistsHeader.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        
        if (rvCollaborativePlaylists != null) {
            rvCollaborativePlaylists.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(isEmpty && this.userPlaylists.isEmpty() ? 
                View.VISIBLE : View.GONE);
        }
        
        // Update adapter
        if (rvCollaborativePlaylists != null) {
            if (collaborativePlaylistAdapter == null) {
                collaborativePlaylistAdapter = new EnhancedPlaylistAdapter(requireContext(), 
                    this.collaborativePlaylists,
                    new EnhancedPlaylistAdapter.PlaylistClickListener() {
                        @Override
                        public void onPlaylistClick(Playlist playlist) {
                            if (playlist != null) {
                                openPlaylistDetails(playlist);
                            }
                        }

                        @Override
                        public void onTrackClick(Track track) {
                            if (track != null) {
                                onTrackSelected(track);
                            }
                        }
                    });
                rvCollaborativePlaylists.setAdapter(collaborativePlaylistAdapter);
            } else {
                collaborativePlaylistAdapter.updateData(this.collaborativePlaylists);
            }
        }
    }
    
    /**
     * Open playlist details
     */
    private void openPlaylistDetails(Playlist playlist) {
        if (!isAdded() || playlist == null) return;
        
        Log.d(TAG, "Opening playlist: " + playlist.getName());
        
        // Pre-fetch tracks to improve the user experience
        playlistManager.getPlaylistTracks(playlist.getPlaylistId(), new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (!isAdded()) return;
                
                // Update tracks in the playlist model
                playlist.setTracks(tracks);
                
                // Navigate to playlist detail
                Bundle args = new Bundle();
                args.putString("playlistId", playlist.getPlaylistId());
                args.putString("playlistName", playlist.getName());
                args.putString("playlistCoverUrl", playlist.getCoverImageUrl());
                args.putBoolean("isCollaborative", playlist.isCollaborative());
                
                Navigation.findNavController(requireView())
                        .navigate(R.id.playlistDetailFragment, args);
            }
            
            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                
                // Navigate anyway, the details fragment will handle the error
                Bundle args = new Bundle();
                args.putString("playlistId", playlist.getPlaylistId());
                args.putString("playlistName", playlist.getName());
                args.putString("playlistCoverUrl", playlist.getCoverImageUrl());
                args.putBoolean("isCollaborative", playlist.isCollaborative());
                
                Navigation.findNavController(requireView())
                        .navigate(R.id.playlistDetailFragment, args);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // No listener cleanup needed since we're not storing listeners
    }

    // Add a loadPlaylists method to fix the missing error
    private void loadPlaylists() {
        showLoadingIndicator();
        playlistManager.getUserPlaylists(new PlaylistManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                hideLoadingIndicator();
                if (playlists != null && !playlists.isEmpty()) {
                    playlistAdapter.updateData(playlists);
                    showContent();
                } else {
                    showEmptyState();
                }
            }

            @Override
            public void onError(String message) {
                hideLoadingIndicator();
                showError("Failed to load playlists: " + message);
            }
        });
    }
    
    private List<Playlist> convertTracksToPlaylists(List<Track> tracks) {
        List<Playlist> playlists = new ArrayList<>();
        for (Track track : tracks) {
            Playlist playlist = new Playlist();
            playlist.setName(track.getTitle());
            playlist.setDescription(track.getArtist());
            playlist.setCoverImageUrl(track.getAlbumArtUrl());
            playlists.add(playlist);
        }
        return playlists;
    }
    
    private void checkEmptyState() {
        // Check if both adapters are empty and show empty state if needed
        boolean isEmpty = (userPlaylistAdapter == null || userPlaylistAdapter.getItemCount() == 0) &&
                         (collaborativePlaylistAdapter == null || collaborativePlaylistAdapter.getItemCount() == 0);
                         
        if (isEmpty && emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
        } else if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
    }

    /**
     * Navigate to the now playing screen
     */
    private void navigateToNowPlaying() {
        if (getActivity() != null && !isDetached() && isAdded()) {
            try {
                // Navigate using NavController
                NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
                navController.navigate(R.id.action_libraryFragment_to_nowPlayingFragment);
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to now playing", e);
            }
        }
    }

    /**
     * Load playlist tracks from unified music service
     * This is different from loadUserPlaylists() which loads from Firebase
     */
    private void loadUnifiedServicePlaylists() {
        showLoadingIndicator();
        unifiedMusicService.getPlaylistTracks("user", "Spotify", new UnifiedMusicService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                hideLoadingIndicator();
                updatePlaylistTracks(tracks);
            }

            @Override
            public void onError(String message) {
                hideLoadingIndicator();
                showError("Failed to load playlists: " + message);
                
                // Try loading from Deezer as fallback
                unifiedMusicService.getPlaylistTracks("user", "Deezer", new UnifiedMusicService.MusicCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        hideLoadingIndicator();
                        updatePlaylistTracks(tracks);
                    }

                    @Override
                    public void onError(String deezerError) {
                        hideLoadingIndicator();
                        showError("Failed to load playlists from both services");
                    }
                });
            }
        });
    }

    private void onTrackSelected(Track track) {
        if (track == null) return;
        playTrack(track);
    }

    private void updatePlaylistTracks(List<Track> tracks) {
        if (tracks.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
            playlistAdapter.updateTracks(tracks);
        }
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

    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideEmptyState() {
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void playTrack(Track track) {
        if (track == null) return;
        
        showLoadingIndicator();
        
        // Set track source to Deezer
        track.setSource(Track.SOURCE_DEEZER);
        
        // Get Deezer preview URL and play
        DeezerHelper.getInstance(requireContext()).getDeezerStreamUrl(track, new DeezerHelper.DeezerCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                hideLoadingIndicator();
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    // Update track with preview URL
                    track.setPreviewUrl(previewUrl);
                    
                    // Update UI to show now playing
                    if (getActivity() instanceof MainActivity) {
                        MainActivity activity = (MainActivity) getActivity();
                        // First update the mini player with track info
                        activity.updateMiniPlayer(track);
                        // Then show it
                        activity.showMiniPlayer();
                    }
                    
                    // Use AudioPlayerHelper to play the track
                    if (audioPlayerHelper != null) {
                        // Set the playback changed listener before playing
                        audioPlayerHelper.setOnPlaybackChangedListener(new AudioPlayerHelper.OnPlaybackChangedListener() {
                            @Override
                            public void onTrackPlay(Track track) {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).updateMiniPlayer(track);
                                }
                            }

                            @Override
                            public void onTrackPause() {
                                // Update play/pause button state if needed
                            }

                            @Override
                            public void onTrackStopped() {
                                // Handle track stopped
                            }

                            @Override
                            public void onTrackChanged(Track track) {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).updateMiniPlayer(track);
                                }
                            }

                            @Override
                            public void onPlaybackError(String errorMessage) {
                                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onBufferingStart() {
                                showLoadingIndicator();
                            }

                            @Override
                            public void onBufferingEnd() {
                                hideLoadingIndicator();
                            }

                            @Override
                            public void onLoadingStateChanged(boolean isLoading) {
                                if (isLoading) {
                                    showLoadingIndicator();
                                } else {
                                    hideLoadingIndicator();
                                }
                            }

                            @Override
                            public void onBufferingUpdate(int percent) {
                                // Update buffering progress if needed
                            }

                            @Override
                            public void onPlaybackStateChanged(boolean isPlaying) {
                                // Update play/pause button state if needed
                            }

                            @Override
                            public void onTrackComplete() {
                                // Handle track completion
                            }
                        });
                        audioPlayerHelper.playTrack(track);
                    } else {
                        // Reinitialize AudioPlayerHelper if null
                        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
                        audioPlayerHelper.playTrack(track);
                    }
                } else {
                    Toast.makeText(requireContext(), "No preview available for this track", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onError(String message) {
                hideLoadingIndicator();
                Toast.makeText(requireContext(), "Error getting track URL: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove MediaPlayer release since AudioPlayerHelper manages its own lifecycle
    }

    private void hideErrorState() {
        if (tvErrorMessage != null) {
            tvErrorMessage.setVisibility(View.GONE);
        }
    }

    private void loadPlaylistTracks(Playlist playlist) {
        if (playlist == null) return;
        
        showLoadingIndicator();
        PlaylistManager.getInstance(requireContext()).getPlaylistTracks(
            playlist.getPlaylistId(),
            new TracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    hideLoadingIndicator();
                    if (tracks != null && !tracks.isEmpty()) {
                        // Update the adapter with the tracks
                        if (playlistAdapter != null) {
                            playlistAdapter.setItems(tracks);
                        }
                    } else {
                        showError("No tracks found in this playlist");
                    }
                }

                @Override
                public void onError(String message) {
                    hideLoadingIndicator();
                    showError("Error loading tracks: " + message);
                }
            });
    }

    private void showPlaylistOptionsDialog(Playlist playlist) {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Playlist Options")
               .setItems(new String[]{"Edit", "Delete", "Share"}, (dialog, which) -> {
                   switch (which) {
                       case 0: // Edit
                           editPlaylist(playlist);
                           break;
                       case 1: // Delete
                           deletePlaylist(playlist);
                           break;
                       case 2: // Share
                           sharePlaylist(playlist);
                           break;
                   }
               })
               .show();
    }

    private void showContent() {
        if (rvUserPlaylists != null) {
            rvUserPlaylists.setVisibility(View.VISIBLE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void initializeAdapters() {
        // Initialize PlaylistManager
        playlistManager = PlaylistManager.getInstance(requireContext());
        
        // Initialize playlist adapter
        playlistAdapter = new PlaylistAdapter(requireContext());
        playlistAdapter.setOnPlaylistClickListener(new PlaylistAdapter.OnPlaylistClickListener() {
            @Override
            public void onPlaylistClick(Playlist playlist) {
                if (playlist != null) {
                    PlaylistDetailsHelper.showPlaylistDetails(requireContext(), playlist);
                }
            }

            @Override
            public void onPlaylistSaveClick(Playlist playlist) {
                if (playlist != null) {
                    // Toggle save state of the playlist
                    boolean currentlySaved = playlist.isSaved();
                    playlist.setSaved(!currentlySaved);
                    
                    // Update in Firebase
                    if (currentlySaved) {
                        playlistManager.removeFromLibrary(playlist.getPlaylistId(), 
                            new PlaylistManager.PlaylistCallback() {
                                @Override
                                public void onSuccess(Playlist updatedPlaylist) {
                                    Toast.makeText(requireContext(), 
                                        "Removed from your library", Toast.LENGTH_SHORT).show();
                                    refreshPlaylists();
                                }
                                
                                @Override
                                public void onError(String message) {
                                    // Revert the save state
                                    playlist.setSaved(true);
                                    Toast.makeText(requireContext(), 
                                        "Error removing playlist: " + message, Toast.LENGTH_SHORT).show();
                                }
                            });
                    } else {
                        playlistManager.addToLibrary(playlist, 
                            new PlaylistManager.PlaylistCallback() {
                                @Override
                                public void onSuccess(Playlist updatedPlaylist) {
                                    Toast.makeText(requireContext(), 
                                        "Added to your library", Toast.LENGTH_SHORT).show();
                                    refreshPlaylists();
                                }
                                
                                @Override
                                public void onError(String message) {
                                    // Revert the save state
                                    playlist.setSaved(false);
                                    Toast.makeText(requireContext(), 
                                        "Error adding playlist: " + message, Toast.LENGTH_SHORT).show();
                                }
                            });
                    }
                }
            }

            @Override
            public void onPlaylistLongClick(Playlist playlist) {
                showPlaylistOptionsDialog(playlist);
            }
        });
    }

    private void editPlaylist(Playlist playlist) {
        if (playlist == null) return;
        
        // Navigate to edit playlist activity
        Intent intent = new Intent(requireContext(), my.edu.utar.bananamusic.ui.playlist.CreatePlaylistActivity.class);
        intent.putExtra("PLAYLIST_ID", playlist.getPlaylistId());
        intent.putExtra("EDIT_MODE", true);
        startActivity(intent);
    }

    private void deletePlaylist(Playlist playlist) {
        if (playlist == null) return;
        
        // Show confirmation dialog
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete \"" + playlist.getName() + "\"?")
            .setPositiveButton("Delete", (dialog, which) -> {
                // Show loading indicator
                showLoadingIndicator();
                
                // Delete playlist
                playlistManager.deletePlaylist(playlist.getPlaylistId(), new my.edu.utar.bananamusic.utils.callbacks.OperationCallback() {
                    @Override
                    public void onSuccess(String message) {
                        hideLoadingIndicator();
                        Toast.makeText(requireContext(), "Playlist deleted", Toast.LENGTH_SHORT).show();
                        
                        // Refresh playlists
                        loadPlaylists();
                    }

                    @Override
                    public void onError(String message) {
                        hideLoadingIndicator();
                        Toast.makeText(requireContext(), "Error deleting playlist: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void sharePlaylist(Playlist playlist) {
        if (playlist == null) return;
        
        // Create a share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out this playlist: " + playlist.getName());
        
        // Create shareable text
        String shareText = "Check out \"" + playlist.getName() + "\" playlist on BananaMusic!\n\n";
        if (playlist.getDescription() != null && !playlist.getDescription().isEmpty()) {
            shareText += playlist.getDescription() + "\n\n";
        }
        shareText += "Shared from BananaMusic app";
        
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share playlist via"));
    }

    private void handleLibraryError(Exception e) {
        Log.e(TAG, "Library error: " + e.getMessage(), e);
        showErrorState("Error loading your library. Please try again.");
    }

    public void refreshContent() {
        // Reload playlists and update the UI
        if (playlistManager != null) {
            playlistManager.loadUserPlaylists(new PlaylistManager.PlaylistsCallback() {
                @Override
                public void onSuccess(List<Playlist> playlists) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            // Update your RecyclerView adapter with the new playlists
                            if (playlistAdapter != null) {
                                playlistAdapter.updatePlaylists(playlists);
                            }
                        });
                    }
                }

                @Override
                public void onError(String message) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Error refreshing: " + message, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        }
    }

    @Override
    public void onPlaylistCreated() {
        refreshPlaylists();
    }

    private void showCreatePlaylistDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_create_playlist);

        // Set dialog width to match parent with margins
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Initialize views
        TextInputEditText etPlaylistName = dialog.findViewById(R.id.etPlaylistName);
        TextInputEditText etPlaylistDescription = dialog.findViewById(R.id.etPlaylistDescription);
        AutoCompleteTextView spinnerMood = dialog.findViewById(R.id.spinnerMood);
        SwitchMaterial switchCollaborative = dialog.findViewById(R.id.switchCollaborative);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);
        MaterialButton btnCreate = dialog.findViewById(R.id.btnCreate);

        // Setup mood spinner
        String[] moods = {"Happy", "Sad", "Chill", "Party", "Focus", "Romantic"};
        ArrayAdapter<String> moodAdapter = new ArrayAdapter<>(
            requireContext(),
            R.layout.item_dropdown,
            moods
        );
        spinnerMood.setAdapter(moodAdapter);

        // Handle button clicks
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnCreate.setOnClickListener(v -> {
            String name = etPlaylistName.getText().toString().trim();
            String description = etPlaylistDescription.getText().toString().trim();
            String mood = spinnerMood.getText().toString();
            boolean isCollaborative = switchCollaborative.isChecked();

            if (name.isEmpty()) {
                etPlaylistName.setError("Please enter a playlist name");
                return;
            }

            // Get current user
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(requireContext(), "Please sign in to create a playlist", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show loading
            ProgressDialog progressDialog = new ProgressDialog(requireContext());
            progressDialog.setMessage("Creating playlist...");
            progressDialog.show();

            // Create playlist data
            String playlistId = UUID.randomUUID().toString();
            Map<String, Object> playlistData = new HashMap<>();
            playlistData.put("playlistId", playlistId);
            playlistData.put("name", name);
            playlistData.put("description", description);
            playlistData.put("mood", mood.isEmpty() ? null : mood);
            playlistData.put("creatorId", currentUser.getUid());
            playlistData.put("creatorName", currentUser.getDisplayName());
            playlistData.put("isCollaborative", isCollaborative);
            playlistData.put("createdAt", FieldValue.serverTimestamp());
            playlistData.put("coverImageUrl", ""); // Will be updated when first track is added
            playlistData.put("trackData", new HashMap<>());
            playlistData.put("trackIds", new ArrayList<>());
            playlistData.put("collected", false);

            // Initialize collaborators array with creator
            List<String> collaborators = new ArrayList<>();
            collaborators.add(currentUser.getUid());
            playlistData.put("collaborators", collaborators);

            // Save to Firebase
            FirebaseFirestore.getInstance()
                .collection("playlists")
                .document(playlistId)
                .set(playlistData)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "Playlist created successfully", Toast.LENGTH_SHORT).show();
                    
                    // Refresh playlists
                    loadUserPlaylists();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "Failed to create playlist: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        });

        dialog.show();
    }
} 