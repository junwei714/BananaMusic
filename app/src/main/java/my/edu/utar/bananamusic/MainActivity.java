package my.edu.utar.bananamusic;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.adapters.PlaylistAdapter;
import my.edu.utar.bananamusic.auth.LoginActivity;
import my.edu.utar.bananamusic.managers.MediaPlayerManager;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.ui.dialogs.AddToPlaylistDialog;
import my.edu.utar.bananamusic.ui.dialogs.AuthRequiredDialog;
import my.edu.utar.bananamusic.ui.dialogs.CreatePlaylistDialogFragment;
import my.edu.utar.bananamusic.ui.home.HomeFragment;
import my.edu.utar.bananamusic.ui.library.LibraryFragment;
import my.edu.utar.bananamusic.ui.player.MiniPlayerView;
import my.edu.utar.bananamusic.ui.player.NowPlayingFragment;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.FirebaseAuthHelper;
import my.edu.utar.bananamusic.utils.MusicServiceManager;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.callbacks.TrackSelectionCallback;
import my.edu.utar.bananamusic.viewmodels.PlaybackViewModel;
import my.edu.utar.bananamusic.utils.RecentlyPlayedCache;

/**
 * MainActivity is the main entry point of the application
 * It hosts navigation and the mini player
 */
public class MainActivity extends AppCompatActivity implements MediaPlayerManager.PlaybackListener, NowPlayingFragment.NowPlayingListener, AuthRequiredDialog.AuthRequiredCallback, AudioPlayerHelper.OnPlaybackChangedListener {

    private static final String TAG = "MainActivity";
    public static final String DEBUG_SHA1 = "83:DE:41:03:C7:6D:FF:D0:95:CF:9C:D0:52:F6:AF:69:61:77:38:CC";
    
    private BottomNavigationView bottomNavigation;
    private NavController navController;
    private FirebaseAuthHelper authHelper;
    
    // Mini player views
    private MiniPlayerView miniPlayerView;
    
    // Current track info
    private Track currentTrack;
    private boolean isPlaying = false;
    
    // Audio player
    private AudioPlayerHelper audioPlayerHelper;

    // Add NowPlayingFragment field
    private NowPlayingFragment nowPlayingFragment;
    private boolean isNowPlayingVisible = false;

    // Add a flag to prevent recursive calls
    private boolean isUpdatingFromFragment = false;

    // Add a new class field after the other boolean flags
    private boolean isUpdatingPlaybackState = false;

    private MediaPlayerManager mediaPlayerManager;
    private MusicServiceManager musicServiceManager;
    private PlaylistManager playlistManager;
    private PlaylistAdapter collaborativeAdapter;
    private PlaylistAdapter userPlaylistAdapter;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    // Add field to store last navigation state
    private int lastSelectedNavigationItemId = R.id.navigation_home;

    // Add ViewModel field
    private PlaybackViewModel playbackViewModel;

    // Add missing field
    private boolean isBuffering = false;

    private RecentlyPlayedCache recentlyPlayedCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize ViewModel
        playbackViewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);

        // Initialize managers
        mediaPlayerManager = MediaPlayerManager.getInstance();
        playlistManager = PlaylistManager.getInstance(this);
        audioPlayerHelper = AudioPlayerHelper.getInstance(this);
        recentlyPlayedCache = RecentlyPlayedCache.getInstance(this);

        // Initialize views
        initViews();
        setupNavigation();
        
        // Set up mini player with click listeners
        setupMiniPlayer();
        setupMiniPlayerClickListeners();
        
        // Observe playback state changes
        observePlaybackState();
        
        // Register as playback listener
        mediaPlayerManager.setPlaybackListener(this);
        
        // Set AudioPlayerHelper callback
        audioPlayerHelper.setOnPlaybackChangedListener(this);
        
        Log.d(TAG, "MainActivity initialized with callbacks");
    }
    
    private void initViews() {
        // Initialize BottomNavigationView
        bottomNavigation = findViewById(R.id.bottom_navigation);
        if (bottomNavigation == null) {
            Log.e(TAG, "Bottom navigation not found in layout!");
        } else {
            Log.d(TAG, "Bottom navigation found and initialized");
            // Make sure it's visible from the start
            bottomNavigation.setVisibility(View.VISIBLE);
        }
        
        // Initialize MiniPlayerView
        miniPlayerView = findViewById(R.id.mini_player_view);
        if (miniPlayerView == null) {
            Log.e(TAG, "Mini player view not found in layout!");
        }
        
        // Set up BottomSheetBehavior for the Now Playing sheet
        setupBottomSheetBehavior();
    }
    
    /**
     * Set up the BottomSheetBehavior for the Now Playing sheet
     * to ensure it doesn't overlap with the bottom navigation
     */
    private void setupBottomSheetBehavior() {
        try {
            // Find the Now Playing sheet
            View nowPlayingSheet = findViewById(R.id.fragment_container_now_playing);
            if (nowPlayingSheet != null) {
                // Get the BottomSheetBehavior from the Now Playing sheet
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior = 
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(nowPlayingSheet);
                
                // Set a bottom margin for the expanded state to avoid covering the bottom navigation
                behavior.setExpandedOffset(150); // Approximate height of bottom nav
                
                // Set state change listener
                behavior.addBottomSheetCallback(new com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        // Always ensure navigation is visible after state changes
                        if (bottomNavigation != null) {
                            bottomNavigation.post(() -> {
                                bottomNavigation.bringToFront();
                                bottomNavigation.setVisibility(View.VISIBLE);
                            });
                        }
                    }
                    
                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                        // No additional action needed
                    }
                });
                
                Log.d(TAG, "BottomSheetBehavior configured successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up BottomSheetBehavior: " + e.getMessage());
        }
    }
    
    private void setupMiniPlayer() {
        Log.d(TAG, "Setting up mini player");
        
        if (miniPlayerView == null) {
            Log.e(TAG, "MiniPlayerView not found in layout!");
            return;
        }
        
        // Set up initial state
        miniPlayerView.setVisibility(View.GONE);
        
        // Set up click listener for the mini player
        miniPlayerView.setOnMiniPlayerClickListener(new MiniPlayerView.OnMiniPlayerClickListener() {
            @Override
            public void onMiniPlayerClicked() {
                Log.d(TAG, "Mini player clicked in MainActivity, currentTrack: " + 
                    (currentTrack != null ? currentTrack.getTitle() : "null"));
                
                if (currentTrack != null) {
                    // Hide mini player first to prevent it from showing behind the fragment
                    miniPlayerView.setVisibility(View.GONE);
                    
                    // Then show the now playing fragment
                    showNowPlayingFragment();
                } else {
                    Log.e(TAG, "Cannot show now playing: no current track");
                }
            }

            @Override
            public void onPlayPauseClicked() {
                togglePlayPause();
            }

            @Override
            public void onNextClicked() {
                if (audioPlayerHelper != null) {
                    audioPlayerHelper.skipToNext();
                }
            }

            @Override
            public void onPreviousClicked() {
                if (audioPlayerHelper != null) {
                    audioPlayerHelper.skipToPrevious();
                }
            }

            @Override
            public void onDismissRequested() {
                hideMiniPlayer();
            }

            @Override
            public void onLikeClicked(Track track, boolean isLiked) {
                updateTrackFavoriteStatus(track, isLiked);
            }

            @Override
            public void onAddToPlaylistClicked(Track track) {
                showAddToPlaylistDialog(track);
            }
        });
        
        // Initialize with current track if exists
        if (audioPlayerHelper != null && audioPlayerHelper.getCurrentTrack() != null) {
            currentTrack = audioPlayerHelper.getCurrentTrack();
            updateMiniPlayer(currentTrack);
        }
        
        // Ensure mini player stays above other views
        miniPlayerView.bringToFront();
        
        Log.d(TAG, "Mini player setup completed");
    }
    
    /**
     * Check for available audio output devices and update the mini player
     */
    private void checkForAvailableDevices() {
        // This would typically check for Bluetooth, Chromecast, etc.
        // For now we'll just check for basic audio devices
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        boolean hasWiredHeadset = audioManager.isWiredHeadsetOn();
        boolean hasBluetoothA2dp = audioManager.isBluetoothA2dpOn();
        
        // Update the mini player to show the connect button if devices are available
        if (miniPlayerView != null) {
            miniPlayerView.updateConnectDeviceButton(hasWiredHeadset || hasBluetoothA2dp);
        }
    }
    
    /**
     * Show a dialog for connecting to external audio devices
     */
    private void showConnectToDeviceDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_connect_device, null);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connect to a device")
               .setView(dialogView)
               .setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        
        // Find and set up the device buttons in the dialog
        setupDeviceButtons(dialogView, dialog);
        
        dialog.show();
    }
    
    /**
     * Set up the device buttons in the connect dialog
     */
    private void setupDeviceButtons(View dialogView, AlertDialog dialog) {
        // This implementation would typically scan for and list available devices
        // For now, we'll just have some example devices

        // Find the devices container
        ViewGroup devicesContainer = dialogView.findViewById(R.id.devices_container);
        if (devicesContainer == null) return;
        
        // Check for audio devices
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        boolean hasWiredHeadset = audioManager.isWiredHeadsetOn();
        boolean hasBluetoothA2dp = audioManager.isBluetoothA2dpOn();
        
        // Add buttons for available devices
        if (hasWiredHeadset) {
            addDeviceButton(devicesContainer, "Wired Headphones", R.drawable.ic_headset, dialog);
        }
        
        if (hasBluetoothA2dp) {
            addDeviceButton(devicesContainer, "Bluetooth Speaker", R.drawable.ic_bluetooth, dialog);
        }
        
        // Always add this option
        addDeviceButton(devicesContainer, "This Phone", R.drawable.ic_smartphone, dialog);
        
        // Add example Spotify Connect devices
        addDeviceButton(devicesContainer, "Living Room Speaker", R.drawable.ic_speaker, dialog);
        addDeviceButton(devicesContainer, "Bedroom TV", R.drawable.ic_tv, dialog);
    }
    
    /**
     * Add a device button to the container
     */
    private void addDeviceButton(ViewGroup container, String deviceName, int iconResId, AlertDialog dialog) {
        View deviceButton = getLayoutInflater().inflate(R.layout.item_connect_device, container, false);
        
        ImageView deviceIcon = deviceButton.findViewById(R.id.device_icon);
        TextView deviceNameText = deviceButton.findViewById(R.id.device_name);
        
        deviceIcon.setImageResource(iconResId);
        deviceNameText.setText(deviceName);
        
        deviceButton.setOnClickListener(v -> {
            // Handle device selection
            Toast.makeText(MainActivity.this, 
                    "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        
        container.addView(deviceButton);
    }
    
    private void setupNavigation() {
        // Initialize NavController with the NavHostFragment
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            
            // Setup the bottom navigation with the NavController
            bottomNavigation = findViewById(R.id.bottom_navigation);
            if (bottomNavigation != null) {
                // Setup the bottom navigation with the NavController 
                NavigationUI.setupWithNavController(bottomNavigation, navController);
                
                // Make sure the bottom navigation is visible here as well
                bottomNavigation.setVisibility(View.VISIBLE);
                
                // Use our custom helper to apply custom layouts to the bottom navigation items
                try {
                    my.edu.utar.bananamusic.utils.BottomNavigationViewHelper.applyCustomItemLayouts(bottomNavigation);
                    Log.d(TAG, "Applied custom navigation item layouts");
                } catch (Exception e) {
                    Log.e(TAG, "Error applying custom navigation layouts: " + e.getMessage());
                }
                
                // Set item selection listener for custom handling if needed
                bottomNavigation.setOnNavigationItemSelectedListener(item -> {
                    int id = item.getItemId();
                    
                    // Make sure the bottom navigation is visible
                    bottomNavigation.setVisibility(View.VISIBLE);
                    
                    // Handle navigation based on the selected item
                    if (id == R.id.navigation_home) {
                        navController.navigate(R.id.homeFragment);
                        return true;
                    } else if (id == R.id.navigation_search) {
                        navController.navigate(R.id.searchFragment);
                        return true;
                    } else if (id == R.id.navigation_library) {
                        navController.navigate(R.id.libraryFragment);
                        return true;
                    } else if (id == R.id.navigation_profile) {
                        navController.navigate(R.id.profileFragment);
                        return true;
                    }
                    return false;
                });
                
                // Update the navigation bar visibility based on destination
                navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                    // Show navigation bar for main destinations
                    if (destination.getId() == R.id.homeFragment ||
                        destination.getId() == R.id.searchFragment ||
                        destination.getId() == R.id.libraryFragment ||
                        destination.getId() == R.id.profileFragment) {
                        showBottomNavigation();
                    } else {
                        // Optional: hide for other destinations
                        // Uncomment the line below if you want to hide navigation on other screens
                        // hideBottomNavigation();
                    }
                });
            } else {
                Log.e(TAG, "Bottom navigation view not found in layout!");
            }
        } else {
            Log.e(TAG, "NavHostFragment not found in layout!");
        }
    }
    
    private void showBottomNavigation() {
        if (bottomNavigation != null) {
            Log.d(TAG, "Showing bottom navigation");
            
            // First make sure it's visible
            bottomNavigation.setVisibility(View.VISIBLE);
            
            // Animate it if it was hidden
            if (bottomNavigation.getTranslationY() != 0) {
                bottomNavigation.animate()
                    .translationY(0)
                    .alpha(1.0f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            }
            
            // Additional checks to make sure the parent is also visible
            View parent = (View) bottomNavigation.getParent();
            if (parent != null && parent.getVisibility() != View.VISIBLE) {
                parent.setVisibility(View.VISIBLE);
            }
        }
    }
    
    private void hideBottomNavigation() {
        if (bottomNavigation != null && bottomNavigation.getVisibility() == View.VISIBLE) {
            // Check if we're on a main screen - if so, we shouldn't hide the navigation
            if (navController != null) {
                int currentDestId = navController.getCurrentDestination().getId();
                if (currentDestId == R.id.homeFragment ||
                    currentDestId == R.id.searchFragment ||
                    currentDestId == R.id.libraryFragment ||
                    currentDestId == R.id.profileFragment) {
                    Log.d(TAG, "Prevented hiding navigation on main screen");
                    return; // Don't hide on main screens
                }
            }
            
            Log.d(TAG, "Hiding bottom navigation");
            bottomNavigation.animate()
                .translationY(bottomNavigation.getHeight())
                .alpha(0.0f)
                .setDuration(300)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> bottomNavigation.setVisibility(View.GONE))
                .start();
        }
    }
    
    /**
     * Clear all views in the current fragment to prevent memory leaks and crashes during navigation
     */
    private void clearCurrentFragmentViews() {
        try {
            // Get the current fragment from the NavHostFragment
            Fragment navHostFragment = getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            
            if (navHostFragment instanceof NavHostFragment) {
                Fragment currentFragment = ((NavHostFragment) navHostFragment).getChildFragmentManager().getPrimaryNavigationFragment();
                
                if (currentFragment != null) {
                    // Call onPause manually to ensure adapters are cleared
                    currentFragment.onPause();
                    
                    // Force a state saving to trigger adapter cleanup
                    Bundle bundle = new Bundle();
                    currentFragment.onSaveInstanceState(bundle);
                    
                    Log.d(TAG, "Cleared views for fragment: " + currentFragment.getClass().getSimpleName());
                }
            } else if (navHostFragment instanceof NowPlayingFragment) {
                // NowPlayingFragment is directly in the fragment manager, not inside a NavHostFragment
                Log.d(TAG, "Clearing views for direct NowPlayingFragment");
                navHostFragment.onPause();
                Bundle bundle = new Bundle();
                navHostFragment.onSaveInstanceState(bundle);
            } else if (navHostFragment != null) {
                Log.d(TAG, "Current fragment is not a NavHostFragment: " + navHostFragment.getClass().getSimpleName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing fragment views: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        // Add audio troubleshooter option
        menu.add(Menu.NONE, 12345, Menu.NONE, "Fix Audio");
        // Add update playlist covers option
        menu.add(Menu.NONE, 12346, Menu.NONE, "Update Playlist Covers");
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            logout();
            return true;
        } else if (id == R.id.menu_play_local) {
            playLocalMp3File();
            return true;
        } else if (id == 12345) {
            troubleshootAudio();
            return true;
        } else if (id == 12346) {
            updateAllPlaylistCovers();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void logout() {
        authHelper.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
    
    /**
     * Update the mini player with current track information
     */
    public void updateMiniPlayer(Track track) {
        if (track == null || miniPlayerView == null) {
            Log.e(TAG, "Cannot update mini player: " + 
                (track == null ? "track is null" : "miniPlayerView is null"));
            return;
        }
        
        currentTrack = track;
        
        runOnUiThread(() -> {
            try {
                // Ensure we're in a valid state
                if (!isFinishing() && !isDestroyed()) {
                    // Update mini player state
                    boolean isPlaying = audioPlayerHelper != null && audioPlayerHelper.isPlaying();
                    boolean shouldShow = track != null;
                    
                    if (shouldShow) {
                        // Update track info first
                        miniPlayerView.updateTrackInfo(track);
                        miniPlayerView.updatePlayPauseButton(isPlaying);
                        
                        // Then show the player if needed
                        if (miniPlayerView.getVisibility() != View.VISIBLE) {
                            miniPlayerView.show();
                        }
                        
                        // Start progress updates if playing
                        if (isPlaying) {
                            miniPlayerView.startTimeUpdates();
                        }
                        
                        // Ensure proper z-ordering
                        if (miniPlayerView.getParent() instanceof ViewGroup) {
                            miniPlayerView.bringToFront();
                        }
                    } else {
                        // Hide the player if no track
                        miniPlayerView.hide();
                    }
                    
                    Log.d(TAG, "Mini player updated successfully with track: " + track.getTitle() +
                          " (playing: " + isPlaying + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating mini player", e);
            }
        });
    }
    
    /**
     * Set up a direct click listener for the mini player that uses the guaranteed method
     */
    private void setupDirectMiniPlayerClickListener() {
        if (miniPlayerView != null) {
            miniPlayerView.setOnMiniPlayerClickListener(new MiniPlayerView.OnMiniPlayerClickListener() {
                @Override
                public void onMiniPlayerClicked() {
                    if (currentTrack != null) {
                        showNowPlayingFragment();
                    }
                }

                @Override
                public void onPlayPauseClicked() {
                    togglePlayPause();
                }

                @Override
                public void onNextClicked() {
                    if (audioPlayerHelper != null) {
                        audioPlayerHelper.skipToNext();
                    }
                }

                @Override
                public void onPreviousClicked() {
                    if (audioPlayerHelper != null) {
                        audioPlayerHelper.skipToPrevious();
                    }
                }

                @Override
                public void onDismissRequested() {
                    hideMiniPlayer();
                }

                @Override
                public void onLikeClicked(Track track, boolean isLiked) {
                    updateTrackFavoriteStatus(track, isLiked);
                }

                @Override
                public void onAddToPlaylistClicked(Track track) {
                    showAddToPlaylistDialog(track);
                }
            });
        }
    }
    
    private void startEnhancedPlayback() {
        if (currentTrack != null) {
            // Use enhanced playback with loading UI
            audioPlayerHelper.playTrackEnhanced(currentTrack, true);
            
            // Notify any visible adapters about the currently playing track
            updateVisibleAdapters();
        }
    }
    
    /**
     * Update visible adapters in the current fragment to ensure they display correctly after navigation
     */
    private void updateVisibleAdapters() {
        try {
            // Get the current fragment from the NavHostFragment
            Fragment navHostFragment = getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            
            // Check if the fragment is a NavHostFragment
            if (navHostFragment instanceof NavHostFragment) {
                Fragment currentFragment = ((NavHostFragment) navHostFragment).getChildFragmentManager().getPrimaryNavigationFragment();
                
                if (currentFragment != null) {
                    String fragmentName = currentFragment.getClass().getSimpleName();
                    Log.d(TAG, "Updating adapters for fragment: " + fragmentName);
                    
                    // For HomeFragment specifically, trigger adapter refresh
                    if (currentFragment instanceof HomeFragment) {
                        View fragmentView = currentFragment.getView();
                        if (fragmentView != null) {
                            // Find RecyclerViews in the fragment
                            RecyclerView rvRecommended = fragmentView.findViewById(R.id.rvRecommended);
                            RecyclerView rvTrendingPlaylists = fragmentView.findViewById(R.id.rvFeaturedPlaylists);
                            RecyclerView rvNewReleases = fragmentView.findViewById(R.id.rvNewReleases);
                            RecyclerView rvMoodSelection = fragmentView.findViewById(R.id.rvMoodSelection);
                            RecyclerView rvCollaborative = fragmentView.findViewById(R.id.rvCollaborativePlaylists);
                            
                            // Force adapter refresh on each recycler view if it exists and has an adapter
                            updateRecyclerView(rvRecommended);
                            updateRecyclerView(rvTrendingPlaylists);
                            updateRecyclerView(rvNewReleases);
                            updateRecyclerView(rvMoodSelection);
                            updateRecyclerView(rvCollaborative);
                        }
                    }
                    
                    // Add similar handling for other fragments with RecyclerViews if needed
                }
            } else if (navHostFragment instanceof NowPlayingFragment) {
                // Handle NowPlayingFragment separately if needed
                Log.d(TAG, "Current fragment is NowPlayingFragment, no adapters to update");
            } else if (navHostFragment != null) {
                Log.d(TAG, "Current fragment is not a NavHostFragment: " + navHostFragment.getClass().getSimpleName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating visible adapters: " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to safely update a RecyclerView adapter
     */
    private void updateRecyclerView(RecyclerView recyclerView) {
        if (recyclerView == null) return;
        
        try {
            // First check if the RecyclerView is in a good state to be modified
            if (!recyclerView.isAttachedToWindow() || recyclerView.isComputingLayout() || recyclerView.isInLayout()) {
                // Post update to happen after the current layout pass is complete
                recyclerView.post(() -> updateRecyclerView(recyclerView));
                return;
            }
            
            // Check if adapter exists
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            if (adapter != null) {
                // Use a milder approach than notifyDataSetChanged which can cause issues
                adapter.notifyItemRangeChanged(0, adapter.getItemCount());
                Log.d(TAG, "Safely refreshed adapter for RecyclerView: " + recyclerView.getId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing adapter: " + e.getMessage());
            // Try again after a delay if it failed
            recyclerView.postDelayed(() -> {
                try {
                    if (recyclerView.getAdapter() != null) {
                        recyclerView.getAdapter().notifyDataSetChanged();
                    }
                } catch (Exception ignored) {
                    // Give up if it fails twice
                }
            }, 250);
        }
    }
    
    private void startPlayback() {
        if (currentTrack != null) {
            audioPlayerHelper.playTrack(currentTrack);
        }
    }
    
    private void togglePlayPause() {
        if (audioPlayerHelper != null) {
            if (playbackViewModel.isPlaying()) {
                audioPlayerHelper.pause();
                playbackViewModel.pause();
            } else {
                audioPlayerHelper.resume();
                playbackViewModel.play();
            }
        }
    }
    
    /**
     * Handles track changes from both the audio player and NowPlayingFragment
     */
    @Override
    public void onTrackPause() {
        Log.d(TAG, "Track paused");
        playbackViewModel.pause();
    }

    // Method to show toast messages
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Method to get AudioPlayerHelper instance
    public AudioPlayerHelper getAudioPlayerHelper() {
        return audioPlayerHelper;
    }

    // Method to setup mini player click listeners
    private void setupMiniPlayerClickListeners() {
        if (miniPlayerView != null) {
            miniPlayerView.setOnMiniPlayerClickListener(new MiniPlayerView.OnMiniPlayerClickListener() {
                @Override
                public void onMiniPlayerClicked() {
                    showNowPlayingFragment();
                }

                @Override
                public void onPlayPauseClicked() {
                    togglePlayPause();
                }

                @Override
                public void onNextClicked() {
                    if (audioPlayerHelper != null) {
                        audioPlayerHelper.skipToNext();
                    }
                }

                @Override
                public void onPreviousClicked() {
                    if (audioPlayerHelper != null) {
                        audioPlayerHelper.skipToPrevious();
                    }
                }

                @Override
                public void onDismissRequested() {
                    hideMiniPlayer();
                }

                @Override
                public void onLikeClicked(Track track, boolean isLiked) {
                    updateTrackFavoriteStatus(track, isLiked);
                }

                @Override
                public void onAddToPlaylistClicked(Track track) {
                    showAddToPlaylistDialog(track);
                }
            });
        }
    }

    // Method to show/hide mini player
    public void showMiniPlayer() {
        if (miniPlayerView != null) {
            miniPlayerView.setVisibility(View.VISIBLE);
        }
    }

    public void hideMiniPlayer() {
        if (miniPlayerView != null) {
            miniPlayerView.setVisibility(View.GONE);
        }
    }

    // Method to show now playing fragment
    private void showNowPlayingFragment() {
        if (!isNowPlayingVisible && currentTrack != null) {
            // Store current navigation state
            if (bottomNavigation != null && bottomNavigation.getSelectedItemId() != 0) {
                lastSelectedNavigationItemId = bottomNavigation.getSelectedItemId();
            }

            // Create fragment with track data
            Bundle args = new Bundle();
            args.putParcelable("track", currentTrack);
            args.putBoolean("is_playing", isPlaying);
            
            nowPlayingFragment = NowPlayingFragment.newInstance();
            nowPlayingFragment.setArguments(args);
            
            // Show the fragment container
            View container = findViewById(R.id.fragment_container_now_playing);
            if (container != null) {
                container.setVisibility(View.VISIBLE);
            }
            
            // Add the fragment
            getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                .replace(R.id.fragment_container_now_playing, nowPlayingFragment)
                .addToBackStack(null)
                .commit();
            
            isNowPlayingVisible = true;
        }
    }

    // Methods for track management
    private void updateTrackFavoriteStatus(Track track, boolean isLiked) {
        if (track != null) {
            // Implement your track favorite status update logic here
            // This might involve updating a database or making an API call
        }
    }

    private void showAddToPlaylistDialog(Track track) {
        if (track != null) {
            AddToPlaylistDialog dialog = AddToPlaylistDialog.newInstance(track);
            dialog.show(getSupportFragmentManager(), "add_to_playlist");
        }
    }

    // Navigation methods
    public void navigateToAllRecommendations() {
        navController.navigate(R.id.action_navigation_home_to_recommendationsFragment);
    }

    public void navigateToAllPlaylists() {
        navController.navigate(R.id.action_navigation_home_to_allPlaylistsFragment);
    }

    public void navigateToAllNewReleases() {
        navController.navigate(R.id.action_navigation_home_to_newReleasesFragment);
    }

    public void navigateToAllCollaborativePlaylists() {
        navController.navigate(R.id.action_navigation_home_to_collaborativePlaylistsFragment);
    }

    public void showCreatePlaylistDialog() {
        CreatePlaylistDialogFragment dialog = new CreatePlaylistDialogFragment();
        dialog.show(getSupportFragmentManager(), "create_playlist");
    }

    // Library management methods
    public void refreshLibraryFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (fragment instanceof NavHostFragment) {
            Fragment currentFragment = ((NavHostFragment) fragment).getChildFragmentManager().getFragments().get(0);
            if (currentFragment instanceof LibraryFragment) {
                ((LibraryFragment) currentFragment).refreshContent();
            }
        }
    }

    public void repairCollaborativePlaylists() {
        // Implement your collaborative playlist repair logic here
    }

    // Track playback methods
    public void playTrack(Track track) {
        if (track != null && audioPlayerHelper != null) {
            audioPlayerHelper.playTrack(track);
            // Update and show the mini player
            updateMiniPlayer(track);
            showMiniPlayer();
        }
    }

    // Debug/test methods
    private void playLocalMp3File() {
        // Implementation for testing local MP3 playback
    }

    private void troubleshootAudio() {
        // Implementation for audio troubleshooting
    }

    private void updateAllPlaylistCovers() {
        // Implementation for updating all playlist covers
    }

    // Track selector
    public void showTrackSelector(TrackSelectionCallback callback) {
        // Create and show a dialog for track selection
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Tracks");

        // Get the layout inflater
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_track_selector, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recycler_view_tracks);

        // Set up the RecyclerView with your track adapter
        // This is a placeholder - you'll need to implement your own adapter
        // that allows for track selection
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            // TODO: Set up your track selection adapter here
        }

        builder.setView(dialogView)
               .setPositiveButton("Add", (dialog, which) -> {
                   // TODO: Get selected tracks from your adapter
                   List<Track> selectedTracks = new ArrayList<>(); // Get this from your adapter
                   callback.onTracksSelected(selectedTracks);
               })
               .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Required interface implementation
    @Override
    public void onProgressChanged(int progress) {
        playbackViewModel.updateProgress(progress);
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        this.isPlaying = isPlaying;
        if (miniPlayerView != null) {
            miniPlayerView.updatePlayPauseButton(isPlaying);
            if (isPlaying) {
                miniPlayerView.startTimeUpdates();
            } else {
                miniPlayerView.stopTimeUpdates();
            }
        }
    }

    @Override
    public void onTrackChanged(Track track) {
        if (track != null) {
            Log.d(TAG, "Track changed: " + track.getTitle());
            currentTrack = track;
            playbackViewModel.startNewTrack(track);
            // Always update MiniPlayer
            if (miniPlayerView != null) {
                miniPlayerView.updateTrackInfo(track);
            }
        }
    }

    @Override
    public void onNowPlayingTrackChanged(Track track) {
        if (track != null) {
            Log.d(TAG, "Now playing track changed: " + track.getTitle());
            currentTrack = track;
            playbackViewModel.startNewTrack(track);
            // Always update MiniPlayer
            if (miniPlayerView != null) {
                miniPlayerView.updateTrackInfo(track);
            }
        }
    }

    @Override
    public void onMinimizeClicked() {
        if (nowPlayingFragment != null) {
            // Hide the fragment container
            View container = findViewById(R.id.fragment_container_now_playing);
            if (container != null) {
                container.setVisibility(View.GONE);
            }
            
            // Remove the fragment using FragmentManager
            getSupportFragmentManager()
                .beginTransaction()
                .remove(nowPlayingFragment)
                .commit();
            
            nowPlayingFragment = null;
            isNowPlayingVisible = false;
            
            // Show mini player and re-register observers
            showMiniPlayer();
            reregisterObservers();
            
            // Restore previous navigation state
            if (bottomNavigation != null && navController != null) {
                // Make sure bottom navigation is visible
                showBottomNavigation();
                
                // Restore the last selected item
                if (lastSelectedNavigationItemId != 0) {
                    // Navigate to the last selected destination
                    if (lastSelectedNavigationItemId == R.id.navigation_home) {
                        navController.navigate(R.id.homeFragment);
                    } else if (lastSelectedNavigationItemId == R.id.navigation_search) {
                        navController.navigate(R.id.searchFragment);
                    } else if (lastSelectedNavigationItemId == R.id.navigation_library) {
                        navController.navigate(R.id.libraryFragment);
                    } else if (lastSelectedNavigationItemId == R.id.navigation_profile) {
                        navController.navigate(R.id.profileFragment);
                    }
                    bottomNavigation.setSelectedItemId(lastSelectedNavigationItemId);
                }
            }
        }
    }

    private void reregisterObservers() {
        Log.d(TAG, "Reregistering observers in MainActivity");
        
        // Remove any existing observers to prevent duplicates
        if (playbackViewModel != null) {
            // Remove existing observers
            playbackViewModel.getCurrentTrack().removeObservers(this);
            playbackViewModel.getPlaybackState().removeObservers(this);
            playbackViewModel.getBufferingProgress().removeObservers(this);
            playbackViewModel.getPlaybackProgress().removeObservers(this);
            playbackViewModel.getErrorMessage().removeObservers(this);
            
            // Re-observe playback state
            observePlaybackState();
            
            // Update mini player with current state
            Track currentTrack = playbackViewModel.getCurrentTrackValue();
            if (currentTrack != null) {
                updateMiniPlayer(currentTrack);
            }
            
            // Update play state
            PlaybackViewModel.PlaybackState state = playbackViewModel.getCurrentState();
            boolean isPlaying = state == PlaybackViewModel.PlaybackState.PLAYING;
            boolean isBuffering = state == PlaybackViewModel.PlaybackState.BUFFERING;
            
            if (miniPlayerView != null) {
                miniPlayerView.updatePlayPauseButton(isPlaying && !isBuffering);
            }
        }
    }

    private void observePlaybackState() {
        Log.d(TAG, "Setting up playback state observers");
        
        // Observe track changes
        playbackViewModel.getCurrentTrack().observe(this, track -> {
            if (track != null) {
                Log.d(TAG, "MiniPlayer received track update: " + track.getTitle());
                currentTrack = track;
                // Always update MiniPlayer regardless of NowPlayingFragment state
                if (miniPlayerView != null) {
                    miniPlayerView.updateTrackInfo(track);
                }
            }
        });

        // Observe playback state
        playbackViewModel.getPlaybackState().observe(this, state -> {
            Log.d(TAG, "MiniPlayer received playback state update: " + state);
            isPlaying = state == PlaybackViewModel.PlaybackState.PLAYING;
            isBuffering = state == PlaybackViewModel.PlaybackState.BUFFERING;
            
            if (miniPlayerView != null) {
                miniPlayerView.updatePlayPauseButton(isPlaying && !isBuffering);
                if (isPlaying && !isBuffering) {
                    miniPlayerView.startTimeUpdates();
                } else {
                    miniPlayerView.stopTimeUpdates();
                }
            }
        });

        // Observe buffering progress
        playbackViewModel.getBufferingProgress().observe(this, progress -> {
            Log.d(TAG, "MiniPlayer received buffering progress update: " + progress);
            if (miniPlayerView != null) {
                miniPlayerView.updateProgress(progress);
            }
        });

        // Observe playback progress
        playbackViewModel.getPlaybackProgress().observe(this, progress -> {
            Log.d(TAG, "MiniPlayer received playback progress update: " + progress);
            if (miniPlayerView != null) {
                miniPlayerView.updateProgress(progress);
            }
        });

        // Observe error messages
        playbackViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showToast(error);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up observers
        if (playbackViewModel != null) {
            playbackViewModel.getCurrentTrack().removeObservers(this);
            playbackViewModel.getPlaybackState().removeObservers(this);
            playbackViewModel.getBufferingProgress().removeObservers(this);
            playbackViewModel.getPlaybackProgress().removeObservers(this);
            playbackViewModel.getErrorMessage().removeObservers(this);
        }
    }

    // Method to get the PlaybackViewModel (used by fragments)
    public PlaybackViewModel getPlaybackViewModel() {
        return playbackViewModel;
    }

    @Override
    public void onCancel() {
        // Handle cancellation of auth dialog
        // Usually just dismiss and continue with limited functionality
        Toast.makeText(this, "Some features may be limited without signing in", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLoginSelected() {
        // Navigate to login activity
        Intent loginIntent = new Intent(this, LoginActivity.class);
        startActivity(loginIntent);
        finish(); // Close current activity since we need user to be logged in
    }

    @Override
    public void onBufferingUpdate(int percent) {
        // Update buffering state in mini player
        if (miniPlayerView != null) {
            miniPlayerView.updatePlayPauseButton(false); // Show pause during buffering
            miniPlayerView.updateProgress(percent);
        }
        // Update buffering in now playing fragment if visible
        if (nowPlayingFragment != null && isNowPlayingVisible) {
            nowPlayingFragment.onBufferingUpdate(percent);
        }
    }

    @Override
    public void onBufferingEnd() {
        // Handle buffering end if needed
    }

    @Override
    public void onTrackComplete() {
        // Handle track completion
        // This will be called when a track finishes playing
        runOnUiThread(() -> {
            // Update UI elements for track completion
            updatePlaybackControls(false);
            // You might want to trigger next track playback here if not handled elsewhere
        });
    }

    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        // Update loading state
        if (miniPlayerView != null) {
            miniPlayerView.updatePlayPauseButton(!isLoading && isPlaying);
        }
        if (nowPlayingFragment != null && isNowPlayingVisible) {
            nowPlayingFragment.onLoadingStateChanged(isLoading);
        }
    }

    @Override
    public void onBufferingStart() {
        playbackViewModel.startBuffering();
    }

    @Override
    public void onPlaybackError(String errorMessage) {
        Log.e(TAG, "Playback error: " + errorMessage);
        playbackViewModel.setError(errorMessage);
        showToast(errorMessage);
    }

    @Override
    public void onTrackPlay(Track track) {
        Log.d(TAG, "Track started playing: " + track.getTitle());
        playbackViewModel.startNewTrack(track);
        playbackViewModel.play();
        
        // Add track to recently played
        if (track != null) {
            recentlyPlayedCache.addTrack(track);
        }
    }

    @Override
    public void onTrackStopped() {
        Log.d(TAG, "Track stopped");
        playbackViewModel.pause();
    }

    @Override
    public void onPlaybackComplete() {
        // Handle playback completion
        if (miniPlayerView != null) {
            miniPlayerView.updatePlayPauseButton(false);
            miniPlayerView.stopTimeUpdates();
        }
        // Update playback state in ViewModel
        playbackViewModel.pause();
    }

    private void updatePlaybackControls(boolean isPlaying) {
        // Update mini player controls
        if (miniPlayerView != null) {
            miniPlayerView.updatePlayPauseButton(isPlaying);
            if (isPlaying) {
                miniPlayerView.startTimeUpdates();
            } else {
                miniPlayerView.stopTimeUpdates();
            }
        }
        
        // Update playback state in ViewModel
        if (isPlaying) {
            playbackViewModel.play();
        } else {
            playbackViewModel.pause();
        }
    }
}