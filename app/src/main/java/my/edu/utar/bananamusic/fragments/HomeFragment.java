package my.edu.utar.bananamusic.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.activities.AllAlbumsActivity;
import my.edu.utar.bananamusic.adapters.FeaturedAlbumsAdapter;
import my.edu.utar.bananamusic.adapters.PlaylistAdapter;
import my.edu.utar.bananamusic.adapters.TrackAdapter;
import my.edu.utar.bananamusic.services.MusicServiceManager;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.models.Album;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.PlaylistDetailsHelper;
import androidx.core.widget.NestedScrollView;
import com.facebook.shimmer.ShimmerFrameLayout;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter.PlaylistCallbackImpl;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter.PlaylistsCallbackImpl;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.utils.callbacks.OperationCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.ApiDataProvider;
import my.edu.utar.bananamusic.utils.RecommendationEngine;
import my.edu.utar.bananamusic.utils.FirebaseAuthHelper;
import my.edu.utar.bananamusic.utils.DeezerHelper;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeFragment extends Fragment implements 
        TrackAdapter.OnTrackClickListener, 
        PlaylistAdapter.OnPlaylistClickListener,
        RecommendationEngine.RecommendationListener {
    
    private static final String TAG = "HomeFragment";
    private View rootView;
    
    private RecyclerView featuredAlbumsRecyclerView;
    private RecyclerView tracksRecyclerView;
    
    private FeaturedAlbumsAdapter featuredAlbumsAdapter;
    private PlaylistAdapter playlistAdapter;
    private TrackAdapter trackAdapter;
    private PlaylistAdapter collaborativePlaylistAdapter;
    
    private SpotifyHelper spotifyHelper;
    private SwipeRefreshLayout swipeRefreshLayout;
    
    private List<Playlist> playlists = new ArrayList<>();
    private List<Track> tracks = new ArrayList<>();
    private List<Playlist> collaborativePlaylists = new ArrayList<>();

    private boolean isLoading = false;
    private boolean hasMorePlaylists = true;
    private static final int PAGE_SIZE = 10;
    private String lastLoadedPlaylistId = null;

    private MusicServiceManager musicServiceManager;

    private TextView tvTrendingError;
    private Button btnRetryTrending;
    private ShimmerFrameLayout shimmerTrendingPlaylists;
    private RecyclerView rvTrendingPlaylists;

    private PlaylistManager playlistManager;

    // Container for recommended content section
    private ViewGroup recommendedContainer;
    
    // For mood-based recommendations
    private TextView tvWelcome;
    private TextView tvCurrentMood;
    private ImageView ivMoodEmoji;
    private TextView tvTimeRecommendationTitle;
    private String currentMood = "happy";
    private boolean isInitialLoad = true;
    
    // Additional services for enhanced recommendations
    private AudioPlayerHelper audioPlayerHelper;
    private ApiDataProvider apiDataProvider;
    private RecommendationEngine recommendationEngine;
    private FirebaseAuthHelper firebaseAuthHelper;
    private DeezerHelper deezerHelper;

    // Add a class-level variable for the layoutManager
    private LinearLayoutManager layoutManager;
    private LinearLayoutManager collaborativeLayoutManager;

    private Handler handler = new Handler(Looper.getMainLooper());
    
    // UI components
    private RecyclerView recommendedRecyclerView;
    private RecyclerView newReleasesRecyclerView;
    private RecyclerView playlistsRecyclerView;
    private RecyclerView collaborativePlaylistsRecyclerView;
    private RecommendedAdapter recommendedAdapter;
    private NewReleasesAdapter newReleasesAdapter;
    private PlaylistAdapter playlistsAdapter;
    private PlaylistAdapter collaborativePlaylistsAdapter;
    private ProgressBar progressBar;
    private ShimmerFrameLayout shimmerRecommended;
    private ShimmerFrameLayout shimmerPlaylists;
    private ShimmerFrameLayout shimmerNewReleases;
    private ShimmerFrameLayout shimmerCollaborative;
    private View emptyCollaborativeView;
    
    // Data providers
    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        
        // Initialize services first
        initializeServices();
        
        // Then initialize views and setup UI
        initializeViews(root);
        
        // Setup RecyclerViews
        setupRecyclerViews();
        setupNewReleasesRecyclerView(); // Explicitly setup new releases
        
        // Setup other UI components
        setupSwipeRefresh();
        setWelcomeMessage();
        
        // Finally load data
        loadData();
        
        return root;
    }

    private void initializeServices() {
        // Initialize all services
        apiDataProvider = ApiDataProvider.getInstance(requireContext());
        spotifyHelper = SpotifyHelper.getInstance(requireContext());
        musicServiceManager = MusicServiceManager.getInstance(requireContext());
        playlistManager = PlaylistManager.getInstance(requireContext());
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        recommendationEngine = RecommendationEngine.getInstance(requireContext());
        firebaseAuthHelper = FirebaseAuthHelper.getInstance();
        deezerHelper = DeezerHelper.getInstance(requireContext());
        firestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
    }

    private void initializeViews(View root) {
        // Store root view for later use
        rootView = root;
        
        // Initialize shimmer layouts
        shimmerRecommended = root.findViewById(R.id.shimmerRecommended);
        shimmerPlaylists = root.findViewById(R.id.shimmerPlaylists);
        shimmerNewReleases = root.findViewById(R.id.shimmerNewReleases);
        
        // Initialize RecyclerViews with correct IDs
        recommendedRecyclerView = root.findViewById(R.id.rvRecommended);
        playlistsRecyclerView = root.findViewById(R.id.rvFeaturedPlaylists);
        newReleasesRecyclerView = root.findViewById(R.id.rvNewReleases);
        
        // Initialize other views
        progressBar = root.findViewById(R.id.progressBar);
        swipeRefreshLayout = root.findViewById(R.id.swipeRefreshLayout);
        tvWelcome = root.findViewById(R.id.tvWelcome);

        // Log view initialization status
        Log.d(TAG, "Views initialized - New Releases RecyclerView: " + (newReleasesRecyclerView != null ? "found" : "not found"));
        Log.d(TAG, "Views initialized - Shimmer: " + (shimmerNewReleases != null ? "found" : "not found"));
    }

    private void setupRecyclerViews() {
        Log.d(TAG, "Setting up RecyclerViews");
        
        // Initialize track adapter for recommendations
        if (rvRecommended != null) {
            // Create layout manager with horizontal orientation
            LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), 
                LinearLayoutManager.HORIZONTAL, false);
            rvRecommended.setLayoutManager(layoutManager);
            
            // Create and set up adapter
            NewRecommendationAdapter recommendationAdapter = new NewRecommendationAdapter(requireContext());
            recommendationAdapter.setOnTrackClickListener((track, position) -> {
                if (track != null && musicServiceManager != null) {
                    musicServiceManager.playTrack(track);
                }
            });
            
            // Set adapter to RecyclerView
            rvRecommended.setAdapter(recommendationAdapter);
            
            // Add spacing decoration
            int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.item_spacing);
            rvRecommended.addItemDecoration(new SpacingItemDecoration(spacingInPixels));
            
            // Disable nested scrolling for smoother performance
            rvRecommended.setNestedScrollingEnabled(false);
        }
        
        // Setup playlists RecyclerView
        if (playlistsRecyclerView != null) {
            playlistsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            playlistAdapter = new PlaylistAdapter(getContext(), playlists, this);
            playlistsRecyclerView.setAdapter(playlistAdapter);
            playlistsRecyclerView.setHasFixedSize(true);
        }

        // Set up New Releases RecyclerView
        if (newReleasesRecyclerView != null) {
            Log.d(TAG, "Setting up New Releases RecyclerView");
            try {
                // Setup layout manager for horizontal scrolling
                LinearLayoutManager newReleasesLayoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
                newReleasesRecyclerView.setLayoutManager(newReleasesLayoutManager);
                
                // Initialize adapter
                newReleasesAdapter = new NewReleasesAdapter(requireContext());
                newReleasesAdapter.setOnTrackClickListener((track, position) -> {
                    if (track != null && musicServiceManager != null) {
                        musicServiceManager.playTrack(track);
                    }
                });
                newReleasesRecyclerView.setAdapter(newReleasesAdapter);
                newReleasesRecyclerView.setHasFixedSize(true);
                
                // Add spacing decoration
                int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.item_spacing);
                newReleasesRecyclerView.addItemDecoration(new SpacingItemDecoration(spacingInPixels));
                
                // Add animation
                newReleasesRecyclerView.setItemAnimator(new DefaultItemAnimator());
                
                Log.d(TAG, "New Releases RecyclerView setup completed");
            } catch (Exception e) {
                Log.e(TAG, "Error setting up New Releases RecyclerView: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "New Releases RecyclerView not found in layout");
        }
    }

    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(
                R.color.colorAccent,
                R.color.colorPrimary,
                R.color.colorPrimaryDark
            );
            swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        }
    }

    private void setWelcomeMessage() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null && tvWelcome != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                tvWelcome.setText(getString(R.string.welcome_user, displayName));
            } else {
                tvWelcome.setText(R.string.welcome_default);
            }
        }
    }

    private void loadData() {
        Log.d(TAG, "Starting to load all data");
        
        // Show shimmer effects and hide recycler views
        showLoadingState(shimmerRecommended, recommendedRecyclerView);
        showLoadingState(shimmerPlaylists, playlistsRecyclerView);
        showLoadingState(shimmerNewReleases, newReleasesRecyclerView);
        showLoadingState(shimmerCollaborative, collaborativePlaylistsRecyclerView);

        // Load data with delays to prevent overwhelming the API
        loadRecommendedTracks();
        
        // Load new releases first since it's more important
        loadNewReleases();
        
        // Delay other loads
        handler.postDelayed(this::loadFeaturedPlaylists, 300);
        handler.postDelayed(this::loadCollaborativePlaylists, 600);
    }

    private void showLoadingState(ShimmerFrameLayout shimmerLayout, RecyclerView recyclerView) {
        if (shimmerLayout != null) {
            shimmerLayout.setVisibility(View.VISIBLE);
            shimmerLayout.startShimmer();
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
    }

    private void showContentState(ShimmerFrameLayout shimmerLayout, RecyclerView recyclerView) {
        if (shimmerLayout != null) {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
        }
        if (recyclerView != null && recyclerView.getAdapter() != null && 
            recyclerView.getAdapter().getItemCount() > 0) {
            recyclerView.setVisibility(View.VISIBLE);
            recyclerView.scheduleLayoutAnimation();
        }
    }

    private void refreshData() {
        Log.d(TAG, "Refreshing all data");
        
        // Show loading indicators
        if (shimmerRecommended != null) {
            shimmerRecommended.setVisibility(View.VISIBLE);
            shimmerRecommended.startShimmer();
        }
        
        // Clear any existing cache at the API level
        if (recommendationEngine != null) {
            try {
                // Use reflection to call clearCache method if it exists
                Method clearCacheMethod = recommendationEngine.getClass().getDeclaredMethod("clearCache");
                clearCacheMethod.setAccessible(true);
                clearCacheMethod.invoke(recommendationEngine);
            } catch (Exception e) {
                // Method might not exist, just log and continue
                Log.d(TAG, "Could not clear recommendation cache: " + e.getMessage());
            }
        }
        
        // Load all data with fresh API calls
        loadRecommendedTracks();
        loadTimeBasedRecommendations();
        loadPlaylists();
        loadFeaturedAlbums();
        
        // Hide refresh indicator if it's showing
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void loadRecommendedTracks() {
        if (rvRecommended == null) {
            Log.e(TAG, "Cannot load recommendations - views not initialized");
            return;
        }

        // Show loading state
        if (shimmerRecommended != null) {
            shimmerRecommended.setVisibility(View.VISIBLE);
            shimmerRecommended.startShimmer();
        }
        rvRecommended.setVisibility(View.GONE);

        Log.d(TAG, "Loading recommendations for mood: " + currentMood);
        
        // Add random offset to get different sets of recommendations each time
        int randomOffset = new Random().nextInt(50);
        int randomLimit = 20 + new Random().nextInt(10); // Get between 20-30 tracks
        
        recommendationEngine.getMoodBasedRecommendations(currentMood, randomLimit, new RecommendationEngine.RecommendationCallback() {
            @Override
            public void onSuccess(List<Track> moodTracks) {
                if (!isAdded()) return;

                if (moodTracks == null || moodTracks.isEmpty()) {
                    Log.w(TAG, "No tracks returned for mood: " + currentMood);
                    loadFallbackTracks();
                    return;
                }
                
                // Shuffle the tracks for more randomness
                Collections.shuffle(moodTracks);
                
                // Take a subset of tracks if we have more than we need
                if (moodTracks.size() > 20) {
                    moodTracks = moodTracks.subList(0, 20);
                }
                
                Log.d(TAG, "Loaded " + moodTracks.size() + " tracks for mood: " + currentMood);
                
                // Update UI with the tracks
                if (rvRecommended.getAdapter() instanceof NewRecommendationAdapter) {
                    ((NewRecommendationAdapter) rvRecommended.getAdapter()).updateData(moodTracks);
                }

                // Hide shimmer and show RecyclerView with animation
                if (shimmerRecommended != null) {
                    shimmerRecommended.stopShimmer();
                    shimmerRecommended.setVisibility(View.GONE);
                }
                
                // Show RecyclerView with fade animation
                rvRecommended.setAlpha(0f);
                rvRecommended.setVisibility(View.VISIBLE);
                rvRecommended.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            }
            
            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                
                Log.e(TAG, "Error loading mood recommendations: " + errorMessage);
                
                if (shimmerRecommended != null) {
                    shimmerRecommended.stopShimmer();
                    shimmerRecommended.setVisibility(View.GONE);
                }
                
                // Try to load fallback recommendations
                loadFallbackTracks();
            }
        });
    }

    private void loadFallbackTracks() {
        List<Track> fallbackTracks = createFallbackRecommendations();
        if (rvRecommended.getAdapter() instanceof NewRecommendationAdapter) {
            ((NewRecommendationAdapter) rvRecommended.getAdapter()).updateData(fallbackTracks);
        }
        if (rvRecommended != null) {
            // Show RecyclerView with fade animation
            rvRecommended.setAlpha(0f);
            rvRecommended.setVisibility(View.VISIBLE);
            rvRecommended.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

    private List<Track> createFallbackRecommendations() {
        List<Track> tracks = new ArrayList<>();
        tracks.add(new Track("1", "Happy Vibes", "Feel Good Artist", "Mood Music", "", 180000, "Local", true));
        tracks.add(new Track("2", "Sunny Day", "Positive Beats", "Mood Music", "", 210000, "Local", true));
        tracks.add(new Track("3", "Good Times", "Happy Tunes", "Mood Music", "", 195000, "Local", true));
        return tracks;
    }

    private void loadFeaturedPlaylists() {
        // Show shimmer loading first
        if (shimmerPlaylists != null) {
            shimmerPlaylists.setVisibility(View.VISIBLE);
            shimmerPlaylists.startShimmer();
        }
        if (playlistsRecyclerView != null) {
            playlistsRecyclerView.setVisibility(View.GONE);
        }

        apiDataProvider.getTrendingPlaylists(false, new ApiDataProvider.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (!isAdded()) return;

                Log.d(TAG, "Successfully loaded " + playlists.size() + " trending playlists");
                if (playlistsAdapter != null) {
                    playlistsAdapter.updateData(playlists);
                }

                // Ensure views are properly visible
                if (shimmerPlaylists != null) {
                    shimmerPlaylists.stopShimmer();
                    shimmerPlaylists.setVisibility(View.GONE);
                }
                if (playlistsRecyclerView != null) {
                    playlistsRecyclerView.setVisibility(View.VISIBLE);
                }

                // Trigger layout animation
                if (playlistsRecyclerView != null) {
                    playlistsRecyclerView.scheduleLayoutAnimation();
                }
            }
            
            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                Log.e(TAG, "Error loading featured playlists: " + message);
                loadFallbackPlaylists();
            }
        });
    }

    private void loadNewReleases() {
        Log.d(TAG, "Starting to load new releases");
        
        // Show shimmer loading first
        if (shimmerNewReleases != null) {
            shimmerNewReleases.setVisibility(View.VISIBLE);
            shimmerNewReleases.startShimmer();
            Log.d(TAG, "Shimmer loading started for new releases");
        }
        if (newReleasesRecyclerView != null) {
            newReleasesRecyclerView.setVisibility(View.GONE);
            Log.d(TAG, "New releases RecyclerView hidden");
        }

        // First try Spotify
        Log.d(TAG, "Attempting to load new releases from Spotify");
        spotifyHelper.getNewReleases(requireContext(), true, new SpotifyHelper.SpotifyTracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (!isAdded()) {
                    Log.w(TAG, "Fragment not attached, skipping new releases update");
                    return;
                }

                if (tracks != null && !tracks.isEmpty()) {
                    Log.d(TAG, "Successfully loaded " + tracks.size() + " new releases from Spotify");
                    // Set source to Spotify
                    for (Track track : tracks) {
                        track.setSource("Spotify");
                    }
                    updateNewReleasesUI(tracks);
                } else {
                    Log.w(TAG, "No new releases from Spotify, trying Deezer");
                    tryLoadFromDeezer();
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Log.e(TAG, "Error loading Spotify new releases: " + message);
                tryLoadFromDeezer();
            }
        });
    }

    private void tryLoadFromDeezer() {
        if (!isAdded()) return;
        
        Log.d(TAG, "Attempting to load from Deezer as fallback");
        deezerHelper.getNewReleases(new DeezerHelper.DeezerTracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (!isAdded()) return;
                
                if (tracks != null && !tracks.isEmpty()) {
                    Log.d(TAG, "Successfully loaded " + tracks.size() + " tracks from Deezer");
                    // Set source to Deezer
                    for (Track track : tracks) {
                        track.setSource("Deezer");
                    }
                    updateNewReleasesUI(tracks);
                } else {
                    Log.w(TAG, "No tracks from Deezer either, loading fallback data");
                    loadFallbackNewReleases();
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Log.e(TAG, "Error loading Deezer tracks: " + message);
                loadFallbackNewReleases();
            }
        });
    }

    private void updateNewReleasesUI(List<Track> tracks) {
        if (!isAdded()) {
            Log.w(TAG, "Fragment not attached, skipping UI update");
            return;
        }

        Log.d(TAG, "Updating new releases UI with " + tracks.size() + " tracks");

        requireActivity().runOnUiThread(() -> {
            try {
                if (newReleasesAdapter != null) {
                    Log.d(TAG, "Updating new releases adapter with data");
                    newReleasesAdapter.updateData(tracks);
                    
                    // Hide shimmer
                    if (shimmerNewReleases != null) {
                        Log.d(TAG, "Stopping shimmer effect");
                        shimmerNewReleases.stopShimmer();
                        shimmerNewReleases.setVisibility(View.GONE);
                    }
                    
                    // Show RecyclerView with animation
                    if (newReleasesRecyclerView != null) {
                        Log.d(TAG, "Making RecyclerView visible with animation");
                        newReleasesRecyclerView.setAlpha(0f);
                        newReleasesRecyclerView.setVisibility(View.VISIBLE);
                        newReleasesRecyclerView.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                        newReleasesRecyclerView.scheduleLayoutAnimation();
                        Log.d(TAG, "New releases UI updated successfully");
                    } else {
                        Log.e(TAG, "New releases RecyclerView is null");
                    }
                } else {
                    Log.e(TAG, "New releases adapter is null");
                    // Try to reinitialize the adapter
                    setupNewReleasesRecyclerView();
                    if (newReleasesAdapter != null) {
                        newReleasesAdapter.updateData(tracks);
                        Log.d(TAG, "Successfully reinitialized adapter and updated data");
                    } else {
                        Log.e(TAG, "Failed to reinitialize new releases adapter");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating new releases UI: " + e.getMessage(), e);
            }
        });
    }

    private void setupNewReleasesRecyclerView() {
        if (newReleasesRecyclerView == null || !isAdded()) {
            Log.e(TAG, "Cannot setup new releases RecyclerView - view is null or fragment not attached");
            return;
        }
        
        Log.d(TAG, "Setting up New Releases RecyclerView");
        try {
            // Setup layout manager for horizontal scrolling
            LinearLayoutManager newReleasesLayoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
            newReleasesRecyclerView.setLayoutManager(newReleasesLayoutManager);
            Log.d(TAG, "Layout manager set for horizontal scrolling");
            
            // Initialize adapter
            newReleasesAdapter = new NewReleasesAdapter(requireContext());
            newReleasesAdapter.setOnTrackClickListener((track, position) -> {
                if (track != null && musicServiceManager != null) {
                    Log.d(TAG, "Track clicked: " + track.getTitle());
                    musicServiceManager.playTrack(track);
                }
            });
            
            // Set adapter to RecyclerView
            newReleasesRecyclerView.setAdapter(newReleasesAdapter);
            newReleasesRecyclerView.setHasFixedSize(true);
            Log.d(TAG, "Adapter set to RecyclerView");
            
            // Add spacing decoration
            int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.item_spacing);
            newReleasesRecyclerView.addItemDecoration(new SpacingItemDecoration(spacingInPixels));
            
            // Add animation
            newReleasesRecyclerView.setItemAnimator(new DefaultItemAnimator());
            
            Log.d(TAG, "New Releases RecyclerView setup completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up New Releases RecyclerView: " + e.getMessage(), e);
        }
    }

    private void loadFallbackNewReleases() {
        Log.d(TAG, "Loading fallback new releases");
        if (!isAdded()) return;

        List<Track> fallbackTracks = new ArrayList<>();
        fallbackTracks.add(new Track(
            "new1",
            "Blinding Lights",
            "The Weeknd",
            "After Hours",
            "https://i.scdn.co/image/ab67616d0000b273c6f7af36ecac25326ee8d360",
            200000,
            "Local",
            true
        ));
        fallbackTracks.add(new Track(
            "new2",
            "As It Was",
            "Harry Styles",
            "Harry's House",
            "https://i.scdn.co/image/ab67616d0000b273b46f74097655d7f353caab14",
            180000,
            "Local",
            true
        ));
        fallbackTracks.add(new Track(
            "new3",
            "Anti-Hero",
            "Taylor Swift",
            "Midnights",
            "https://i.scdn.co/image/ab67616d0000b273bb54dde68cd23e2a268ae0f5",
            190000,
            "Local",
            true
        ));
        fallbackTracks.add(new Track(
            "new4",
            "Flowers",
            "Miley Cyrus",
            "Endless Summer Vacation",
            "https://i.scdn.co/image/ab67616d0000b273f429549123dbe8552764ba1d",
            185000,
            "Local",
            true
        ));
        fallbackTracks.add(new Track(
            "new5",
            "Last Night",
            "Morgan Wallen",
            "One Thing At A Time",
            "https://i.scdn.co/image/ab67616d0000b273807b1fd27d4acb4e26912797",
            195000,
            "Local",
            true
        ));

        Log.d(TAG, "Created " + fallbackTracks.size() + " fallback tracks");
        updateNewReleasesUI(fallbackTracks);
    }

    private void loadCollaborativePlaylists() {
        // Implement collaborative playlists loading logic here
        // This is a placeholder that should be implemented based on your requirements
        if (isAdded()) {
            // For now, just hide the shimmer and show empty state
            showContentState(shimmerCollaborative, collaborativePlaylistsRecyclerView);
            if (emptyCollaborativeView != null) {
                emptyCollaborativeView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void loadFallbackRecommendedTracks() {
        List<Track> fallbackTracks = new ArrayList<>();
        fallbackTracks.add(new Track("1", "Offline Track 1", "Local Artist", "Banana Music", "", 180000, "Local", true));
        fallbackTracks.add(new Track("2", "Offline Track 2", "Local Artist", "Banana Music", "", 210000, "Local", true));
        fallbackTracks.add(new Track("3", "Offline Track 3", "Local Artist", "Banana Music", "", 195000, "Local", true));
        
        if (recommendedAdapter != null) {
            recommendedAdapter.updateData(fallbackTracks);
        }
        showContentState(shimmerRecommended, recommendedRecyclerView);
    }

    private void loadFallbackPlaylists() {
        List<Playlist> fallbackPlaylists = new ArrayList<>();
        fallbackPlaylists.add(new Playlist("local1", "Top Hits", "Banana Music", "Popular tracks for you", "", false, "Local"));
        fallbackPlaylists.add(new Playlist("local2", "Chill Vibes", "Banana Music", "Relaxing music collection", "", false, "Local"));
        fallbackPlaylists.add(new Playlist("local3", "Workout Mix", "Banana Music", "Energy boosting tracks", "", false, "Local"));
        
        if (playlistsAdapter != null) {
            playlistsAdapter.updateData(fallbackPlaylists);
        }
        showContentState(shimmerPlaylists, playlistsRecyclerView);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Store root view for later use
        rootView = view;
        
        // Initialize services
        spotifyHelper = SpotifyHelper.getInstance(requireContext());
        musicServiceManager = MusicServiceManager.getInstance(requireContext());
        playlistManager = PlaylistManager.getInstance(requireContext());
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        apiDataProvider = ApiDataProvider.getInstance(requireContext());
        recommendationEngine = RecommendationEngine.getInstance(requireContext());
        firebaseAuthHelper = FirebaseAuthHelper.getInstance();
        deezerHelper = DeezerHelper.getInstance(requireContext());
        
        // Initialize views
        initializeViews(view);
        
        // Set retry button click listener
        btnRetryTrending.setOnClickListener(v -> refreshData());
        
        // Set see all buttons click listeners
        setupSeeAllButtons(view);
        
        // Setup mood buttons
        setupMoodButtons(view);
        
        // Setup time-based recommendations
        loadTimeBasedRecommendations();
        
        // Setup logo/mood emoji click listener for mood selection
        if (ivMoodEmoji != null) {
            ivMoodEmoji.setOnClickListener(v -> {
                showMoodSelectionDialog();
            });
        }
        
        // Show loading indicators
        showLoadingIndicator();
        
        // Load data with a slight delay to ensure views are properly initialized
        handler.postDelayed(this::loadData, 100);
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize services and helpers
        spotifyHelper = SpotifyHelper.getInstance(requireContext());
        musicServiceManager = MusicServiceManager.getInstance(requireContext());
        playlistManager = PlaylistManager.getInstance(requireContext());
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        apiDataProvider = ApiDataProvider.getInstance(requireContext());
        recommendationEngine = RecommendationEngine.getInstance(requireContext());
        firebaseAuthHelper = FirebaseAuthHelper.getInstance();
        deezerHelper = DeezerHelper.getInstance(requireContext());
        
        // Register as recommendation listener
        recommendationEngine.addListener(this);
    }
    
    private void initSwipeRefreshLayout(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        
        if (swipeRefreshLayout != null) {
            // Set refresh colors
            swipeRefreshLayout.setColorSchemeResources(
                R.color.colorAccent,
                R.color.colorPrimary,
                R.color.colorPrimaryDark
            );
            
            // Set refresh listener
            swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        }
    }
    
    private void initFeaturedAlbumsRecyclerView(View view) {
        // Initialize RecyclerView
        featuredAlbumsRecyclerView = view.findViewById(R.id.rvAlbums);
        
        if (featuredAlbumsRecyclerView == null) {
            Log.e(TAG, "Error: Could not find Albums RecyclerView");
            return;
        }
        
        featuredAlbumsRecyclerView.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        // Initialize adapter
        featuredAlbumsAdapter = new FeaturedAlbumsAdapter(getContext());
        featuredAlbumsRecyclerView.setAdapter(featuredAlbumsAdapter);

        // Set click listener
        featuredAlbumsAdapter.setOnAlbumClickListener(album -> {
            // Handle album click
            Toast.makeText(getContext(), "Selected: " + album.getName(), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to album details or start playing
        });

        // Set up "See All" button
        TextView seeAllButton = view.findViewById(R.id.tvSeeAllAlbums);
        if (seeAllButton != null) {
            seeAllButton.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AllAlbumsActivity.class);
                startActivity(intent);
            });
        }
    }
    
    private void initPlaylistsRecyclerView(View view) {
        // Find the playlists RecyclerView from the layout
        playlistsRecyclerView = view.findViewById(R.id.rv_trending_playlists);
        View shimmerView = view.findViewById(R.id.shimmer_trending);
        TextView errorView = view.findViewById(R.id.tv_trending_error);
        View retryButton = view.findViewById(R.id.btn_retry_trending);
        
        if (playlistsRecyclerView != null) {
            // Setup layout manager for horizontal scrolling
            layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
            playlistsRecyclerView.setLayoutManager(layoutManager);
            
            // Add animation
            playlistsRecyclerView.setItemAnimator(new DefaultItemAnimator());
            
            // Add decoration for spacing
            int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.item_spacing);
            playlistsRecyclerView.addItemDecoration(new SpacingItemDecoration(spacingInPixels));
            
            // Initialize adapter with empty list
            playlistAdapter = new PlaylistAdapter(requireContext());
            playlistAdapter.setOnPlaylistClickListener(this);
            playlistsRecyclerView.setAdapter(playlistAdapter);
            
            // Add scroll listener for endless scrolling
            playlistsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (!isLoading && layoutManager.findLastCompletelyVisibleItemPosition() == playlistAdapter.getItemCount() - 1) {
                        loadMorePlaylists();
                    }
                }
            });
            
            // Setup retry button
            if (retryButton != null) {
                retryButton.setOnClickListener(v -> loadPlaylists());
            }
            
            // Set visibility
            if (shimmerView != null) shimmerView.setVisibility(View.VISIBLE);
            playlistsRecyclerView.setVisibility(View.GONE);
            if (errorView != null) errorView.setVisibility(View.GONE);
            if (retryButton != null) retryButton.setVisibility(View.GONE);
        }
    }
    
    private void initTracksRecyclerView(View view) {
        // Find the tracks RecyclerView from the layout
        tracksRecyclerView = view.findViewById(R.id.rvRecommended);
        
        if (tracksRecyclerView != null) {
            // Setup layout manager for horizontal scrolling
            LinearLayoutManager layoutManager = new LinearLayoutManager(
                getContext(), 
                LinearLayoutManager.HORIZONTAL, 
                false
            );
            tracksRecyclerView.setLayoutManager(layoutManager);
            
            // Initialize adapter with empty list
            tracks = new ArrayList<>();
            trackAdapter = new TrackAdapter(getContext(), tracks, this);
            tracksRecyclerView.setAdapter(trackAdapter);
            
            // Add decoration for spacing
            int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.item_spacing);
            tracksRecyclerView.addItemDecoration(new SpacingItemDecoration(spacingInPixels));
            
            // Add animation
            tracksRecyclerView.setItemAnimator(new DefaultItemAnimator());
        }
    }

    private void initCollaborativePlaylistsRecyclerView(View view) {
        rootView = view;
        collaborativePlaylistsRecyclerView = view.findViewById(R.id.rvCollaborativePlaylists);
        collaborativeLayoutManager = new LinearLayoutManager(getContext());
        collaborativePlaylistsRecyclerView.setLayoutManager(collaborativeLayoutManager);
        
        collaborativePlaylistAdapter = new PlaylistAdapter(requireContext());
        collaborativePlaylistAdapter.setOnPlaylistClickListener(new PlaylistAdapter.OnPlaylistClickListener() {
            @Override
            public void onPlaylistClick(Playlist playlist) {
                if (getActivity() != null) {
                    PlaylistDetailsHelper.showPlaylistDetails(getContext(), playlist);
                }
            }

            @Override
            public void onPlaylistLongClick(Playlist playlist) {
                showPlaylistOptionsMenu(playlist);
            }
        });
        
        collaborativePlaylistsRecyclerView.setAdapter(collaborativePlaylistAdapter);

        // Add scroll listener for endless scrolling
        collaborativePlaylistsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!isLoading && layoutManager.findLastCompletelyVisibleItemPosition() == playlistAdapter.getItemCount() - 1) {
                    loadMorePlaylists();
                }
            }
        });
    }

    private void loadFeaturedAlbums() {
        if (featuredAlbumsRecyclerView == null) {
            Log.e(TAG, "featuredAlbumsRecyclerView is null, cannot load featured albums");
            return;
        }
        
        if (spotifyHelper == null) {
            Log.e(TAG, "SpotifyHelper is null, initializing");
            spotifyHelper = SpotifyHelper.getInstance(requireContext());
        }

        // Show loading indicator
        View shimmerAlbums = rootView.findViewById(R.id.shimmer_albums);
        if (shimmerAlbums != null) {
            shimmerAlbums.setVisibility(View.VISIBLE);
            ((ShimmerFrameLayout)shimmerAlbums).startShimmer();
        }
        featuredAlbumsRecyclerView.setVisibility(View.GONE);

        // Use featured content API to get both recommendations and new releases
                spotifyHelper.getFeaturedContent(requireContext(), false, new SpotifyHelper.FeaturedContentCallback() {
                    @Override
                    public void onSuccess(List<Track> recommendedTracks, List<Track> newReleases) {
                // Only handle new releases here
                if (newReleases != null && !newReleases.isEmpty()) {
                    updateNewReleasesAdapter(newReleases);
                } else {
                    Log.w(TAG, "No new releases returned from API, using fallback data");
                    updateNewReleasesAdapter(createFallbackNewReleases());
                }
                
                // Hide loading indicator
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (shimmerAlbums != null) {
                            shimmerAlbums.setVisibility(View.GONE);
                            ((ShimmerFrameLayout)shimmerAlbums).stopShimmer();
                        }
                        featuredAlbumsRecyclerView.setVisibility(View.VISIBLE);
                    });
                        }
                    }
                    
                    @Override
                    public void onError(String message) {
                Log.e(TAG, "Failed to get featured content: " + message);
                
                // Use fallback data
                updateNewReleasesAdapter(createFallbackNewReleases());
                
                // Hide loading indicator
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (shimmerAlbums != null) {
                            shimmerAlbums.setVisibility(View.GONE);
                            ((ShimmerFrameLayout)shimmerAlbums).stopShimmer();
                        }
                        featuredAlbumsRecyclerView.setVisibility(View.VISIBLE);
                        
                        // Show toast with error message
                        Toast.makeText(requireContext(), 
                            "Failed to load featured albums. Using sample data.", 
                            Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    // Helper method to create sample albums when API fails
    private List<Album> createSampleAlbums() {
        List<Album> sampleAlbums = new ArrayList<>();
        
        // Create a few sample albums
        for (int i = 1; i <= 5; i++) {
            Album album = new Album();
            album.setId("sample_" + i);
            album.setName("Sample Album " + i);
            album.setArtist("Artist " + i);
            sampleAlbums.add(album);
        }
        
        return sampleAlbums;
    }
    
    private void loadRecommendedPlaylists() {
        if (playlistsRecyclerView == null || playlistAdapter == null) {
            Log.e(TAG, "RecyclerView or adapter is null, cannot load recommended playlists");
            return;
        }

        // Show loading state
        showLoadingState(true);

        // Create an API data provider if not available
        if (spotifyHelper == null) {
            spotifyHelper = SpotifyHelper.getInstance(requireContext());
        }

        // Fetch trending playlists
                    spotifyHelper.getFeaturedPlaylists(new SpotifyHelper.SpotifyPlaylistsCallback() {
                        @Override
                        public void onSuccess(List<Playlist> playlistsList) {
                // Update UI on main thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                                if (playlistsList != null && !playlistsList.isEmpty()) {
                            Log.d(TAG, "Loaded " + playlistsList.size() + " recommended playlists");
                            
                            // Mark source as Spotify
                                    for (Playlist playlist : playlistsList) {
                                playlist.setSource(Playlist.SOURCE_SPOTIFY);
                            }
                            
                            // Update the adapter
                            showContent(playlistsList);
                                } else {
                            Log.w(TAG, "No playlists returned from API, using fallback data");
                            showContent(createSamplePlaylists());
                        }
                        
                        // Hide loading state
                        showLoadingState(false);
                    });
                                }
                            }
                            
                            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to load recommended playlists: " + message);
                
                // Update UI on main thread with fallback data
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Use fallback data
                        showContent(createSamplePlaylists());
                        
                        // Hide loading state
                        showLoadingState(false);
                        
                        // Show toast with error message
                        Toast.makeText(requireContext(), 
                            "Failed to load recommended playlists. Using sample data.", 
                            Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void loadMorePlaylists() {
        playlistManager.getUserPlaylists(CallbackAdapter.createPlaylistManagerPlaylistsCallback(new PlaylistsCallbackImpl() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (playlists != null && !playlists.isEmpty()) {
                    appendToPlaylistsList(playlists);
                } else {
                    hasMorePlaylists = false;
                }
                isLoading = false;
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error loading more playlists: " + message);
                Toast.makeText(requireContext(), "Failed to load more playlists: " + message, Toast.LENGTH_SHORT).show();
                isLoading = false;
            }
        }));
    }

    private void showLoadingState(boolean show) {
        View shimmerView = getView().findViewById(R.id.shimmer_trending);
        View errorView = getView().findViewById(R.id.tv_trending_error);
        View retryButton = getView().findViewById(R.id.btn_retry_trending);
        
        if (shimmerView != null) {
            shimmerView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (playlistsRecyclerView != null) {
            playlistsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (errorView != null) {
            errorView.setVisibility(View.GONE);
        }
        if (retryButton != null) {
            retryButton.setVisibility(View.GONE);
        }
    }

    private void showErrorState(String errorMessage) {
        View shimmerView = getView().findViewById(R.id.shimmer_trending);
        TextView errorView = getView().findViewById(R.id.tv_trending_error);
        View retryButton = getView().findViewById(R.id.btn_retry_trending);
        
        if (shimmerView != null) {
            shimmerView.setVisibility(View.GONE);
        }
        if (playlistsRecyclerView != null) {
            playlistsRecyclerView.setVisibility(View.GONE);
        }
        if (errorView != null) {
            errorView.setText(errorMessage);
            errorView.setVisibility(View.VISIBLE);
        }
        if (retryButton != null) {
            retryButton.setVisibility(View.VISIBLE);
        }
    }

    private void showContent(List<Playlist> playlists) {
        // Update adapter with animation
        if (playlistAdapter != null) {
            playlistAdapter.updateData(playlists);
        }
        
        // Show RecyclerView with fade animation
        if (playlistsRecyclerView != null && playlistsRecyclerView.getVisibility() != View.VISIBLE) {
            // Hide shimmer first
            View shimmerView = rootView.findViewById(R.id.shimmer_trending_playlists);
            if (shimmerView != null) {
                shimmerView.setVisibility(View.GONE);
                if (shimmerView instanceof ShimmerFrameLayout) {
                    ((ShimmerFrameLayout) shimmerView).stopShimmer();
                }
            }
            
            // Show content with animation
            playlistsRecyclerView.setAlpha(0f);
            playlistsRecyclerView.setVisibility(View.VISIBLE);
            playlistsRecyclerView.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

    private void showBottomLoading(boolean show) {
        View bottomLoader = getView().findViewById(R.id.loadingMorePlaylists);
        if (bottomLoader != null) {
            bottomLoader.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showPlaylistOptionsMenu(Playlist playlist) {
        if (getContext() == null) return;
        
        PopupMenu popup = new PopupMenu(getContext(), playlistsRecyclerView);
        popup.getMenuInflater().inflate(R.menu.playlist_options_menu, popup.getMenu());
        
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_play) {
                // Play the playlist
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).playPlaylist(playlist);
                }
                return true;
            } else if (itemId == R.id.action_share) {
                // Share the playlist
                sharePlaylist(playlist);
                return true;
            } else if (itemId == R.id.action_add_to_library) {
                // Add to library
                addPlaylistToLibrary(playlist);
                return true;
            }
            return false;
        });
        
        popup.show();
    }

    private void sharePlaylist(Playlist playlist) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, playlist.getName());
        shareIntent.putExtra(Intent.EXTRA_TEXT, 
            "Check out this playlist: " + playlist.getName() + "\n" +
            "Description: " + playlist.getDescription());
        startActivity(Intent.createChooser(shareIntent, "Share Playlist"));
    }

    private void addPlaylistToLibrary(Playlist playlist) {
        playlistManager.addToLibrary(playlist, CallbackAdapter.createPlaylistManagerCallback(new PlaylistCallbackImpl() {
            @Override
            public void onSuccess(Playlist updatedPlaylist) {
                Toast.makeText(requireContext(), "Playlist added to library", Toast.LENGTH_SHORT).show();
                refreshPlaylists();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error adding playlist to library: " + message);
                Toast.makeText(requireContext(), "Failed to add playlist: " + message, Toast.LENGTH_SHORT).show();
            }
        }));
    }

    public void onRefresh() {
        // Refresh all data
        loadFeaturedAlbums();
        loadRecommendedPlaylists();
        loadRecommendedTracks();
        loadCollaborativePlaylists();
        
        // Hide refresh indicator after all data is loaded
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void appendToPlaylistsList(List<Playlist> playlists) {
        if (playlistAdapter != null) {
            playlistAdapter.addData(playlists);
            lastLoadedPlaylistId = playlists.get(playlists.size() - 1).getPlaylistId();
        }
    }

    private void refreshPlaylists() {
        loadPlaylists();
    }

    /**
     * Update recommended tracks in the UI
     */
    private void updateRecommendedTracks(List<Track> tracks) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
        if (tracks == null || tracks.isEmpty()) {
            // Hide recommendations section if no tracks
            if (recommendedContainer != null) {
                recommendedContainer.setVisibility(View.GONE);
            }
            return;
        }

        // Show recommendations section
        if (recommendedContainer != null) {
            recommendedContainer.setVisibility(View.VISIBLE);
        }

            // Hide shimmer
            if (shimmerRecommended != null) {
                shimmerRecommended.stopShimmer();
                shimmerRecommended.setVisibility(View.GONE);
            }

            // Show and update RecyclerView
        if (tracksRecyclerView != null) {
            tracksRecyclerView.setVisibility(View.VISIBLE);
                
                // Ensure adapter is initialized
                if (trackAdapter == null) {
                    trackAdapter = new TrackAdapter(requireContext(), tracks, this);
                    tracksRecyclerView.setAdapter(trackAdapter);
                } else {
            trackAdapter.updateStandardTracks(tracks);
                    trackAdapter.notifyDataSetChanged();
                }
                
                // Trigger layout animation
                tracksRecyclerView.scheduleLayoutAnimation();
            }

            Log.d(TAG, "Updated UI with " + tracks.size() + " tracks");
        });
    }

    /**
     * Update new releases in the featured albums adapter
     */
    private void updateNewReleasesAdapter(List<Track> newReleases) {
        if (newReleases == null || newReleases.isEmpty()) {
            return;
        }

        // Convert tracks to albums
        List<Album> albums = new ArrayList<>();
        for (Track track : newReleases) {
            Album album = new Album();
            album.setId(track.getId());
            album.setName(track.getAlbum());
            album.setArtist(track.getArtist());
            album.setImageUrl(track.getAlbumArtUrl());
                albums.add(album);
        }

        // Update the featured albums adapter on the main thread
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (featuredAlbumsAdapter != null) {
                    featuredAlbumsAdapter.setAlbums(albums);
                    
                    // Hide shimmer if visible
                    View shimmerAlbums = rootView.findViewById(R.id.shimmer_albums);
                    if (shimmerAlbums != null) {
                        shimmerAlbums.setVisibility(View.GONE);
                        if (shimmerAlbums instanceof ShimmerFrameLayout) {
                            ((ShimmerFrameLayout) shimmerAlbums).stopShimmer();
                        }
                    }
                    
                    // Show content
                    if (featuredAlbumsRecyclerView != null) {
                        featuredAlbumsRecyclerView.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    }

    /**
     * Create fallback recommendations when API fails
     */
    private List<Track> createFallbackRecommendations() {
        List<Track> tracks = new ArrayList<>();
        
        // Create sample recommended tracks
        tracks.add(new Track("Blinding Lights", "The Weeknd", "After Hours", "3:20", R.drawable.default_album_art));
        tracks.add(new Track("As It Was", "Harry Styles", "Harry's House", "2:47", R.drawable.default_album_art));
        tracks.add(new Track("Stay", "The Kid LAROI, Justin Bieber", "F*CK LOVE 3+", "2:21", R.drawable.default_album_art));
        tracks.add(new Track("Heat Waves", "Glass Animals", "Dreamland", "3:58", R.drawable.default_album_art));
        tracks.add(new Track("Easy On Me", "Adele", "30", "3:44", R.drawable.default_album_art));
        
        return tracks;
    }

    /**
     * Create fallback new releases when API fails
     */
    private List<Track> createFallbackNewReleases() {
        List<Track> tracks = new ArrayList<>();
        
        // Create sample new releases
        tracks.add(new Track("New Release 1", "Popular Artist", "Latest Album", "3:30", R.drawable.default_album_art));
        tracks.add(new Track("Fresh Track", "Rising Star", "Debut Album", "3:15", R.drawable.default_album_art));
        tracks.add(new Track("Hot Single", "Chart Topper", "Summer Hits", "3:45", R.drawable.default_album_art));
        tracks.add(new Track("Latest Hit", "New Artist", "First Album", "3:20", R.drawable.default_album_art));
        tracks.add(new Track("Trending Now", "Famous Band", "Greatest Hits", "4:00", R.drawable.default_album_art));
        
        return tracks;
    }

    // Add these helper methods
    private void showLoadingIndicator() {
        if (rootView == null) return;
        
        // Show all shimmer loading indicators
        View shimmerRecommended = rootView.findViewById(R.id.shimmer_recommended);
        View shimmerNewReleases = rootView.findViewById(R.id.shimmer_new_releases);
        View shimmerAlbums = rootView.findViewById(R.id.shimmer_albums);
        View shimmerTrending = rootView.findViewById(R.id.shimmer_trending_playlists);
        View shimmerCollaborative = rootView.findViewById(R.id.shimmer_collaborative);
        
        if (shimmerRecommended != null) {
            shimmerRecommended.setVisibility(View.VISIBLE);
            ((ShimmerFrameLayout)shimmerRecommended).startShimmer();
        }
        if (shimmerNewReleases != null) {
            shimmerNewReleases.setVisibility(View.VISIBLE);
            ((ShimmerFrameLayout)shimmerNewReleases).startShimmer();
        }
        if (shimmerAlbums != null) {
            shimmerAlbums.setVisibility(View.VISIBLE);
            ((ShimmerFrameLayout)shimmerAlbums).startShimmer();
        }
        if (shimmerTrending != null) {
            shimmerTrending.setVisibility(View.VISIBLE);
            ((ShimmerFrameLayout)shimmerTrending).startShimmer();
        }
        if (shimmerCollaborative != null) {
            shimmerCollaborative.setVisibility(View.VISIBLE);
            ((ShimmerFrameLayout)shimmerCollaborative).startShimmer();
        }
        
        // Hide the recycler views while loading
        if (tracksRecyclerView != null) tracksRecyclerView.setVisibility(View.GONE);
        if (rvTrendingPlaylists != null) rvTrendingPlaylists.setVisibility(View.GONE);
        if (featuredAlbumsRecyclerView != null) featuredAlbumsRecyclerView.setVisibility(View.GONE);
        RecyclerView rvNewReleases = rootView.findViewById(R.id.rvNewReleases);
        if (rvNewReleases != null) rvNewReleases.setVisibility(View.GONE);
        RecyclerView rvCollaborativePlaylists = rootView.findViewById(R.id.rvCollaborativePlaylists);
        if (rvCollaborativePlaylists != null) rvCollaborativePlaylists.setVisibility(View.GONE);
    }

    private void hideLoadingIndicator() {
        // Hide all shimmer loading indicators
        if (rootView != null) {
            View shimmerRecommended = rootView.findViewById(R.id.shimmer_recommended);
            View shimmerNewReleases = rootView.findViewById(R.id.shimmer_new_releases);
            View shimmerAlbums = rootView.findViewById(R.id.shimmer_albums);
            View shimmerTrending = rootView.findViewById(R.id.shimmer_trending_playlists);
            View shimmerCollaborative = rootView.findViewById(R.id.shimmer_collaborative);
            
            if (shimmerRecommended != null) shimmerRecommended.setVisibility(View.GONE);
            if (shimmerNewReleases != null) shimmerNewReleases.setVisibility(View.GONE);
            if (shimmerAlbums != null) shimmerAlbums.setVisibility(View.GONE);
            if (shimmerTrending != null) shimmerTrending.setVisibility(View.GONE);
            if (shimmerCollaborative != null) shimmerCollaborative.setVisibility(View.GONE);
            
            // Show the recycler views
            if (tracksRecyclerView != null) tracksRecyclerView.setVisibility(View.VISIBLE);
            if (rvTrendingPlaylists != null) rvTrendingPlaylists.setVisibility(View.VISIBLE);
            if (featuredAlbumsRecyclerView != null) featuredAlbumsRecyclerView.setVisibility(View.VISIBLE);
            RecyclerView rvNewReleases = rootView.findViewById(R.id.rvNewReleases);
            if (rvNewReleases != null) rvNewReleases.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptyState() {
        if (tvTrendingError != null) {
            tvTrendingError.setText(R.string.no_playlists_found);
            tvTrendingError.setVisibility(View.VISIBLE);
        }
        
        if (btnRetryTrending != null) {
            btnRetryTrending.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String errorMessage) {
        if (tvTrendingError != null) {
            tvTrendingError.setText(errorMessage);
            tvTrendingError.setVisibility(View.VISIBLE);
        }
        
        if (btnRetryTrending != null) {
            btnRetryTrending.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Set welcome message with user's name if available
     */
    private void setUserWelcomeMessage() {
        if (tvWelcome == null) return;
        
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String displayName = currentUser != null ? currentUser.getDisplayName() : null;
        
        if (displayName != null && !displayName.isEmpty()) {
            tvWelcome.setText(getString(R.string.welcome_user, displayName));
        } else {
            tvWelcome.setText(R.string.welcome_back);
        }
    }
    
    /**
     * Setup mood selection buttons
     */
    private void setupMoodButtons(View view) {
        // Find all mood buttons
        View btnHappy = view.findViewById(R.id.btnHappy);
        View btnRelaxed = view.findViewById(R.id.btnRelaxed);
        View btnSad = view.findViewById(R.id.btnSad);
        View btnEnergetic = view.findViewById(R.id.btnEnergetic);
        
        // Set click listeners for each button
        if (btnHappy != null) {
            btnHappy.setOnClickListener(v -> updateMood("happy"));
        }
        if (btnRelaxed != null) {
            btnRelaxed.setOnClickListener(v -> updateMood("relaxed"));
        }
        if (btnSad != null) {
            btnSad.setOnClickListener(v -> updateMood("sad"));
        }
        if (btnEnergetic != null) {
            btnEnergetic.setOnClickListener(v -> updateMood("energetic"));
        }
    }
    
    /**
     * Update the current mood and load appropriate recommendations
     */
    private void updateMood(String mood) {
        if (mood.equals(currentMood)) {
            // If it's the same mood, no need to update
            return;
        }
        
        this.currentMood = mood;
        
        // Update mood text label
        if (tvCurrentMood != null) {
            tvCurrentMood.setText(getString(R.string.based_on_mood, mood));
        }
        
        // Update mood emoji/icon
        updateMoodIcon(mood);
        
        // Animate background change
        animateMoodBackground(mood);
        
        // Load new recommendations based on mood
        if (recommendationEngine != null) {
            // Show loading first
            View shimmerRecommended = rootView.findViewById(R.id.shimmer_recommended);
            if (shimmerRecommended != null) {
                shimmerRecommended.setVisibility(View.VISIBLE);
                ((ShimmerFrameLayout)shimmerRecommended).startShimmer();
            }
            if (tracksRecyclerView != null) {
                tracksRecyclerView.setVisibility(View.GONE);
            }
            
            // Load new recommendations
            loadRecommendedTracks();
        }
        
        // Update section title based on mood
        updateSectionTitles(mood);
    }
    
    private void updateMoodIcon(String mood) {
        if (ivMoodEmoji == null) return;
        
        int emojiDrawable = R.drawable.ic_mood_happy; // Default
        switch (mood.toLowerCase()) {
            case "happy":
                emojiDrawable = R.drawable.ic_mood_happy;
                break;
            case "relaxed":
                emojiDrawable = R.drawable.ic_mood_relaxed;
                break;
            case "sad":
                emojiDrawable = R.drawable.ic_mood_sad;
                break;
            case "energetic":
                emojiDrawable = R.drawable.ic_mood_energetic;
                break;
            case "romantic":
                emojiDrawable = R.drawable.ic_mood_romantic;
                break;
            case "focused":
                emojiDrawable = R.drawable.ic_mood_focused;
                break;
        }
        
        ivMoodEmoji.setImageResource(emojiDrawable);
    }

    private void animateMoodBackground(String mood) {
        if (rootView == null) return;
        
        // Find all background images
        ImageView happyBg = rootView.findViewById(R.id.ivBackgroundHappy);
        ImageView sadBg = rootView.findViewById(R.id.ivBackgroundSad);
        ImageView energeticBg = rootView.findViewById(R.id.ivBackgroundEnergetic);
        ImageView relaxedBg = rootView.findViewById(R.id.ivBackgroundRelaxed);
        
        // Hide all backgrounds first
        if (happyBg != null) happyBg.setAlpha(0f);
        if (sadBg != null) sadBg.setAlpha(0f);
        if (energeticBg != null) energeticBg.setAlpha(0f);
        if (relaxedBg != null) relaxedBg.setAlpha(0f);
        
        // Determine which background to show
        ImageView targetBg = null;
        switch (mood.toLowerCase()) {
            case "happy":
                targetBg = happyBg;
                break;
            case "sad":
                targetBg = sadBg;
                break;
            case "energetic":
                targetBg = energeticBg;
                break;
            case "relaxed":
                targetBg = relaxedBg;
                break;
            default:
                targetBg = happyBg; // Default to happy
                break;
        }
        
        // Animate the background change
        if (targetBg != null) {
            targetBg.setVisibility(View.VISIBLE);
            targetBg.animate()
                .alpha(1f)
                .setDuration(500)
                .start();
        }
    }

    private void updateSectionTitles(String mood) {
        // Update recommendation section title based on mood
        TextView recommendationTitle = rootView.findViewById(R.id.tvRecommendationTitle);
        if (recommendationTitle != null) {
            String title;
            switch (mood.toLowerCase()) {
                case "happy":
                    title = "Happy Vibes";
                    break;
                case "sad":
                    title = "Melancholy Mix";
                    break;
                case "energetic":
                    title = "Energy Boost";
                    break;
                case "relaxed":
                    title = "Chilled Tunes";
                    break;
                default:
                    title = "Recommendations";
                    break;
            }
            recommendationTitle.setText(title);
        }
        
        // Update recommendation subtitle
        TextView recommendationSubtitle = rootView.findViewById(R.id.tvRecommendationSubtitle);
        if (recommendationSubtitle != null) {
            String subtitle;
            switch (mood.toLowerCase()) {
                case "happy":
                    subtitle = "Music to brighten your day";
                    break;
                case "sad":
                    subtitle = "Songs for your emotions";
                    break;
                case "energetic":
                    subtitle = "Get your heart pumping";
                    break;
                case "relaxed":
                    subtitle = "Calm your mind with these tracks";
                    break;
                default:
                    subtitle = "Because you listened to similar songs";
                    break;
            }
            recommendationSubtitle.setText(subtitle);
        }
    }

    // Implement TrackAdapter.OnTrackClickListener methods
    @Override
    public void onTrackClick(Track track, int position) {
        if (track == null) return;
        
        // Play the selected track
        if (musicServiceManager != null) {
            musicServiceManager.playTrack(track);
        }
    }

    // Implement PlaylistAdapter.OnPlaylistClickListener methods
    @Override
    public void onPlaylistClick(Playlist playlist) {
        if (playlist == null) return;
        
        // Navigate to playlist details
        Bundle args = new Bundle();
        args.putString("playlistId", playlist.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_navigation_home_to_playlistDetailsFragment, args);
    }
    
    // Implement the other method from PlaylistAdapter.OnPlaylistClickListener
    @Override
    public void onPlaylistLongClick(Playlist playlist) {
        if (playlist != null) {
            showPlaylistOptionsMenu(playlist);
        }
    }

    // Implement RecommendationEngine.RecommendationListener method
    @Override
    public void onRecommendationsUpdated() {
        // Reload recommendations when recommendation engine notifies of updates
        if (isAdded()) {
            loadRecommendedTracks();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister from recommendation engine
        if (recommendationEngine != null) {
            recommendationEngine.removeListener(this);
        }
        if (newReleasesAdapter != null) {
            newReleasesAdapter.release();
        }
    }

    /**
     * Set appropriate time-based title based on time of day
     */
    private void loadTimeBasedRecommendations() {
        if (tvTimeRecommendationTitle == null) return;
        
        // Get current hour
        int hour = java.time.LocalTime.now().getHour();
        String timeTitle;
        
        if (hour >= 5 && hour < 12) {
            timeTitle = "Good Morning";
        } else if (hour >= 12 && hour < 17) {
            timeTitle = "Good Afternoon";
        } else if (hour >= 17 && hour < 21) {
            timeTitle = "Good Evening";
        } else {
            timeTitle = "Night Vibes";
        }
        
        tvTimeRecommendationTitle.setText(timeTitle);
        
        // Update time-appropriate greeting
        if (tvWelcome != null) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String displayName = currentUser != null ? currentUser.getDisplayName() : null;
            
            if (displayName != null && !displayName.isEmpty()) {
                if (hour >= 5 && hour < 12) {
                    tvWelcome.setText("Good morning, " + displayName);
                } else if (hour >= 12 && hour < 17) {
                    tvWelcome.setText("Good afternoon, " + displayName);
                } else if (hour >= 17 && hour < 21) {
                    tvWelcome.setText("Good evening, " + displayName);
                } else {
                    tvWelcome.setText("Hello, " + displayName);
                }
            } else {
                if (hour >= 5 && hour < 12) {
                    tvWelcome.setText("Good morning");
                } else if (hour >= 12 && hour < 17) {
                    tvWelcome.setText("Good afternoon");
                } else if (hour >= 17 && hour < 21) {
                    tvWelcome.setText("Good evening");
                } else {
                    tvWelcome.setText("Welcome back");
                }
            }
        }
    }

    /**
     * Item decoration for adding spacing between RecyclerView items
     */
    private static class SpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;

        public SpacingItemDecoration(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, 
                                 @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.right = spacing;
            // Add left margin only for the first item
            if (parent.getChildAdapterPosition(view) == 0) {
                outRect.left = spacing;
            }
        }
    }

    /**
     * Show a dialog to select mood
     */
    private void showMoodSelectionDialog() {
        if (getContext() == null) return;
        
        // Create and show a dialog with mood options
        String[] moods = {"Happy", "Relaxed", "Sad", "Energetic", "Romantic", "Focused"};
        
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Your Mood")
            .setItems(moods, (dialog, which) -> {
                // Update mood based on selection
                updateMood(moods[which].toLowerCase());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setupSeeAllButtons(View view) {
        // Set click listeners for "See All" buttons
        TextView seeAllRecs = view.findViewById(R.id.tvSeeAllRecs);
        TextView seeAllAlbums = view.findViewById(R.id.tvSeeAllAlbums);
        TextView seeAllNewReleases = view.findViewById(R.id.tvSeeAllNewReleases);
        TextView seeAllTrending = view.findViewById(R.id.tv_see_all_trending);
        TextView seeAllCollaborative = view.findViewById(R.id.tvSeeAllCollaborative);
        
        if (seeAllRecs != null) {
            seeAllRecs.setOnClickListener(v -> {
                // Navigate to all recommendations
                navigateToRecommendationsPage();
            });
        }
        
        if (seeAllAlbums != null) {
            seeAllAlbums.setOnClickListener(v -> {
                // Navigate to all albums
                Intent intent = new Intent(getActivity(), AllAlbumsActivity.class);
                startActivity(intent);
            });
        }
        
        if (seeAllNewReleases != null) {
            seeAllNewReleases.setOnClickListener(v -> {
                // Navigate to new releases
                navigateToNewReleasesPage();
            });
        }
        
        if (seeAllTrending != null) {
            seeAllTrending.setOnClickListener(v -> {
                // Navigate to all trending playlists
                navigateToAllPlaylistsPage();
            });
        }
        
        if (seeAllCollaborative != null) {
            seeAllCollaborative.setOnClickListener(v -> {
                // Navigate to all collaborative playlists
                navigateToCollaborativePlaylistsPage();
            });
        }
    }

    private void navigateToRecommendationsPage() {
        if (getActivity() == null) return;
        
        try {
            // Direct fragment navigation without using the Directions class
            Toast.makeText(requireContext(), "This feature is coming soon", Toast.LENGTH_SHORT).show();
            
            // For now, we'll just log this since we don't have the Directions class
            Log.d(TAG, "Would navigate to recommendations page");
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to recommendations: " + e.getMessage());
            Toast.makeText(requireContext(), "This feature is coming soon", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToNewReleasesPage() {
        if (getActivity() == null) return;
        
        try {
            // Direct fragment navigation without using the Directions class
            Toast.makeText(requireContext(), "This feature is coming soon", Toast.LENGTH_SHORT).show();
            
            // For now, we'll just log this since we don't have the Directions class
            Log.d(TAG, "Would navigate to new releases page");
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to new releases: " + e.getMessage());
            Toast.makeText(requireContext(), "This feature is coming soon", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToAllPlaylistsPage() {
        if (getActivity() == null) return;
        
        try {
            // Direct fragment navigation without using the Directions class
            Toast.makeText(requireContext(), "This feature is coming soon", Toast.LENGTH_SHORT).show();
            
            // For now, we'll just log this since we don't have the Directions class
            Log.d(TAG, "Would navigate to all playlists page");
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to playlists: " + e.getMessage());
            Toast.makeText(requireContext(), "This feature is coming soon", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToCollaborativePlaylistsPage() {
        if (getActivity() == null) return;
        
        try {
            // Direct fragment navigation without using the Directions class
            Toast.makeText(requireContext(), "This feature is coming soon", Toast.LENGTH_SHORT).show();
            
            // For now, we'll just log this since we don't have the Directions class
            Log.d(TAG, "Would navigate to collaborative playlists page");
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to collaborative playlists: " + e.getMessage());
            Toast.makeText(requireContext(), "This feature is coming soon", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Force a refresh of recommended tracks every time the fragment resumes
        // This ensures fresh recommendations across app sessions
        if (recommendationEngine != null) {
            Log.d(TAG, "onResume: Refreshing recommendations");
            loadRecommendedTracks();
            
            // Also refresh time-based recommendations
            loadTimeBasedRecommendations();
        }
    }
} 