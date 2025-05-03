package my.edu.utar.bananamusic.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.PlaylistAdapter;
import my.edu.utar.bananamusic.adapters.RecommendedAdapter;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.ui.dialogs.CreatePlaylistDialogFragment;
import my.edu.utar.bananamusic.ui.dialogs.ProgressDialogFragment;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.utils.TrackManager;

public class HomeFragment extends Fragment implements 
    RecommendedAdapter.OnTrackClickListener, 
    PlaylistAdapter.OnPlaylistClickListener,
    CreatePlaylistDialogFragment.PlaylistCreationListener {

    private static final String TAG = "HomeFragment";
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean shouldRefreshOnResume = false;
    private String currentMood = null; // Tracks the current user mood
    private boolean isDataLoading = false;
    private boolean hasInitialDataLoaded = false;
    
    // Cached data for fast reloading
    private List<Track> cachedRecommendations = new ArrayList<>();
    private List<Playlist> cachedFeaturedPlaylists = new ArrayList<>();
    private List<Track> cachedNewReleases = new ArrayList<>();
    
    // UI components
    private RecyclerView recommendedRecyclerView;
    private RecyclerView newReleasesRecyclerView;
    private RecyclerView playlistsRecyclerView;
    private RecyclerView collaborativePlaylistsRecyclerView;
    private RecommendedAdapter recommendedAdapter;
    private RecommendedAdapter newReleasesAdapter;
    private PlaylistAdapter playlistsAdapter;
    private PlaylistAdapter collaborativePlaylistsAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private ShimmerFrameLayout shimmerRecommended;
    private ShimmerFrameLayout shimmerPlaylists;
    private ShimmerFrameLayout shimmerNewReleases;
    private ShimmerFrameLayout shimmerCollaborative;
    private View emptyCollaborativeView;
    private TextView tvWelcome;
    
    // Data providers
    private ApiDataProvider apiDataProvider;
    private SpotifyHelper spotifyHelper;
    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;
    private FirebaseAnalytics firebaseAnalytics;
    private TrackManager trackManager;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        
        // Initialize Firebase Analytics
        firebaseAnalytics = FirebaseAnalytics.getInstance(requireContext());
        trackManager = TrackManager.getInstance(requireContext());
        
        initializeViews(root);
        setupRecyclerViews();
        setupSwipeRefresh();
        setWelcomeMessage();
        
        // Get user mood state if available
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            firestore.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && documentSnapshot.contains("currentMood")) {
                            currentMood = documentSnapshot.getString("currentMood");
                            Log.d(TAG, "User current mood: " + currentMood);
                        }
                        loadData();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting user mood: " + e.getMessage());
                        loadData();
                    });
        } else {
            loadData();
        }
        
        return root;
    }
    
    private void initializeViews(View root) {
        // Initialize RecyclerViews
        recommendedRecyclerView = root.findViewById(R.id.rvRecommended);
        newReleasesRecyclerView = root.findViewById(R.id.rvNewReleases);
        playlistsRecyclerView = root.findViewById(R.id.rvFeaturedPlaylists);
        collaborativePlaylistsRecyclerView = root.findViewById(R.id.rvCollaborativePlaylists);
        
        // Initialize Shimmer layouts
        shimmerRecommended = root.findViewById(R.id.shimmerRecommended);
        shimmerPlaylists = root.findViewById(R.id.shimmerPlaylists);
        shimmerNewReleases = root.findViewById(R.id.shimmerNewReleases);
        shimmerCollaborative = root.findViewById(R.id.shimmerCollaborative);
        
        // Initialize other views
        progressBar = root.findViewById(R.id.progressBar);
        swipeRefreshLayout = root.findViewById(R.id.swipeRefreshLayout);
        emptyCollaborativeView = root.findViewById(R.id.emptyCollaborativeView);
        tvWelcome = root.findViewById(R.id.tvWelcome);

        // Initialize data providers
        apiDataProvider = ApiDataProvider.getInstance(requireContext());
        spotifyHelper = SpotifyHelper.getInstance(requireContext());
        firestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();

        // Setup click listeners
        root.findViewById(R.id.tvSeeAllRecs).setOnClickListener(v -> showAllRecommendations());
        root.findViewById(R.id.tvSeeAllPlaylists).setOnClickListener(v -> showAllPlaylists());
        root.findViewById(R.id.tvSeeAllReleases).setOnClickListener(v -> showAllNewReleases());
        root.findViewById(R.id.tvSeeAllCollaborative).setOnClickListener(v -> showAllCollaborativePlaylists());
        
        // Setup create collaborative playlist button
        View btnCreateCollaborative = root.findViewById(R.id.btnCreateCollaborative);
        if (btnCreateCollaborative != null) {
            btnCreateCollaborative.setOnClickListener(v -> createNewCollaborativePlaylist());
        }
    }
    
    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
            R.color.refresh_color_1,
            R.color.refresh_color_2,
            R.color.refresh_color_3,
            R.color.refresh_color_4
        );
        swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        swipeRefreshLayout.setProgressViewOffset(true, 0, 
            getResources().getDimensionPixelSize(R.dimen.swipe_refresh_offset));
    }
    
    private void setupRecyclerViews() {
        // Setup recommended RecyclerView
        recommendedAdapter = new RecommendedAdapter(requireContext(), new ArrayList<>(), this);
        recommendedRecyclerView.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recommendedRecyclerView.setAdapter(recommendedAdapter);
        recommendedRecyclerView.setHasFixedSize(true);

        // Setup new releases RecyclerView
        newReleasesAdapter = new RecommendedAdapter(requireContext(), new ArrayList<>(), this);
        newReleasesRecyclerView.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        newReleasesRecyclerView.setAdapter(newReleasesAdapter);
        newReleasesRecyclerView.setHasFixedSize(true);

        // Setup playlists RecyclerView
        playlistsAdapter = new PlaylistAdapter(requireContext(), new ArrayList<>(), this);
        playlistsRecyclerView.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        playlistsRecyclerView.setAdapter(playlistsAdapter);
        playlistsRecyclerView.setHasFixedSize(true);

        // Setup collaborative playlists RecyclerView with grid layout
        collaborativePlaylistsAdapter = new PlaylistAdapter(requireContext(), new ArrayList<>(), this);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return 1; // Each item takes up one column
            }
        });
        collaborativePlaylistsRecyclerView.setLayoutManager(gridLayoutManager);
        collaborativePlaylistsRecyclerView.setAdapter(collaborativePlaylistsAdapter);
        collaborativePlaylistsRecyclerView.setHasFixedSize(true);
        
        // Add item decoration for grid spacing
        int spacing = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        collaborativePlaylistsRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                     @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int column = position % 2;
                
                outRect.left = column == 0 ? spacing : spacing / 2;
                outRect.right = column == 1 ? spacing : spacing / 2;
                outRect.bottom = spacing;
                outRect.top = spacing; // Add top spacing for all items
            }
        });

        // Add animations
        addRecyclerViewAnimation(recommendedRecyclerView);
        addRecyclerViewAnimation(newReleasesRecyclerView);
        addRecyclerViewAnimation(playlistsRecyclerView);
        addRecyclerViewAnimation(collaborativePlaylistsRecyclerView);
    }
    
    private void addScrollListener(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Apply subtle scale animation to visible items while scrolling
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int firstVisible = layoutManager.findFirstVisibleItemPosition();
                    int lastVisible = layoutManager.findLastVisibleItemPosition();
                    
                    for (int i = firstVisible; i <= lastVisible; i++) {
                        View view = layoutManager.findViewByPosition(i);
                        if (view != null) {
                            float scrollPercentage = Math.abs(dx) / (float) view.getWidth();
                            float scale = 1.0f - (scrollPercentage * 0.1f);
                            scale = Math.max(0.9f, Math.min(1.0f, scale));
                            view.setScaleX(scale);
                            view.setScaleY(scale);
                        }
                    }
                }
            }
            
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                
                // Reset scale when scrolling stops
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int firstVisible = layoutManager.findFirstVisibleItemPosition();
                        int lastVisible = layoutManager.findLastVisibleItemPosition();
                        
                        for (int i = firstVisible; i <= lastVisible; i++) {
                            View view = layoutManager.findViewByPosition(i);
                            if (view != null) {
                                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                            }
                        }
                    }
                }
            }
        });
    }
    
    private void showAllRecommendations() {
        // Navigate to all recommendations screen
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToAllRecommendations();
        }
    }
    
    private void showAllPlaylists() {
        // Navigate to all playlists screen
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToAllPlaylists();
        }
    }
    
    private void showAllNewReleases() {
        // Navigate to all new releases screen
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToAllNewReleases();
        }
    }
    
    private void showAllCollaborativePlaylists() {
        // Navigate to all collaborative playlists screen
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToAllCollaborativePlaylists();
        }
    }
    
    private void createNewCollaborativePlaylist() {
        // Show create playlist dialog
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showCreatePlaylistDialog();
        }
    }
    
    private void refreshData() {
        // Clear existing data
        recommendedAdapter.clearData();
        playlistsAdapter.clearData();
        newReleasesAdapter.clearData();
        collaborativePlaylistsAdapter.clearData();
        
        // Clear cached data
        cachedRecommendations.clear();
        cachedFeaturedPlaylists.clear();
        cachedNewReleases.clear();

        // Show shimmer effects
        showLoadingState(shimmerRecommended, recommendedRecyclerView);
        showLoadingState(shimmerPlaylists, playlistsRecyclerView);
        showLoadingState(shimmerNewReleases, newReleasesRecyclerView);
        showLoadingState(shimmerCollaborative, collaborativePlaylistsRecyclerView);

        // Refresh user mood before loading data
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            firestore.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && documentSnapshot.contains("currentMood")) {
                            currentMood = documentSnapshot.getString("currentMood");
                            Log.d(TAG, "Refreshed user mood: " + currentMood);
                        }
                        loadData();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error refreshing user mood: " + e.getMessage());
                        loadData();
                    });
        } else {
            loadData();
        }
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
            recyclerView.scheduleLayoutAnimation(); // Replay enter animation
        }
    }
    
    private void setWelcomeMessage() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                tvWelcome.setText(getString(R.string.welcome_user, displayName));
            } else {
                tvWelcome.setText(R.string.welcome_default);
            }
        } else {
            tvWelcome.setText(R.string.welcome_default);
        }
    }
    
    private void loadData() {
        if (isDataLoading) {
            // Avoid multiple simultaneous data loads
            return;
        }
        
        isDataLoading = true;
        
        // Enable the swipe refresh indicator 
        swipeRefreshLayout.setRefreshing(true);
        
        // Load personalized recommendations
        loadPersonalizedRecommendations();
        
        // Load featured playlists with pagination and sorting
        loadFeaturedPlaylistsWithPagination();
        
        // Load new releases with filtering
        loadNewReleasesWithFiltering();
        
        // Load collaborative playlists (using existing implementation)
        loadCollaborativePlaylists();
    }
    
    private void loadPersonalizedRecommendations() {
        showLoadingState(shimmerRecommended, recommendedRecyclerView);
        
        // Use personalized recommendations with mood-based filtering
        apiDataProvider.getPersonalizedRecommendations(currentMood, 20, new ApiDataProvider.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks.isEmpty()) {
                    Log.w(TAG, "No personalized recommendations returned");
                    loadFallbackRecommendedTracks();
                    return;
                }
                
                // Save to cache
                cachedRecommendations.clear();
                cachedRecommendations.addAll(tracks);
                
                // Update UI with recommended tracks
                recommendedAdapter.updateData(tracks);
                
                // Show content
                showContentState(shimmerRecommended, recommendedRecyclerView);
                
                // Play entrance animation
                recommendedRecyclerView.scheduleLayoutAnimation();
                
                // Check if all data has loaded
                checkAllDataLoaded();
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error loading personalized recommendations: " + message);
                
                // Show cached recommendations if available
                if (!cachedRecommendations.isEmpty()) {
                    Log.d(TAG, "Using cached recommendations after error");
                    recommendedAdapter.updateData(cachedRecommendations);
                    showContentState(shimmerRecommended, recommendedRecyclerView);
                } else {
                    loadFallbackRecommendedTracks();
                }
                
                // Check if all data has loaded
                checkAllDataLoaded();
            }
        });
    }
    
    private void loadFeaturedPlaylistsWithPagination() {
        showLoadingState(shimmerPlaylists, playlistsRecyclerView);
        
        // Get user's listening preferences to determine sort order
        FirebaseUser user = firebaseAuth.getCurrentUser();
        final String[] sortByRef = {"popularity"}; // Using array to make it effectively final
        
        if (user != null) {
            firestore.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && documentSnapshot.contains("preferredSortOrder")) {
                            sortByRef[0] = documentSnapshot.getString("preferredSortOrder");
                        }
                        fetchFeaturedPlaylists(sortByRef[0]);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting user preferences: " + e.getMessage());
                        fetchFeaturedPlaylists(sortByRef[0]);
                    });
        } else {
            fetchFeaturedPlaylists(sortByRef[0]);
        }
    }
    
    private void fetchFeaturedPlaylists(String sortBy) {
        // Get featured playlists with pagination and sorting
        apiDataProvider.getFeaturedPlaylistsWithPagination(0, 10, sortBy, 
                new ApiDataProvider.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (playlists.isEmpty()) {
                    Log.w(TAG, "No featured playlists returned");
                    loadFallbackPlaylists();
                    return;
                }
                
                // Save to cache
                cachedFeaturedPlaylists.clear();
                cachedFeaturedPlaylists.addAll(playlists);
                
                // Update UI with playlists
                playlistsAdapter.updateData(playlists);
                
                // Show content
                showContentState(shimmerPlaylists, playlistsRecyclerView);
                
                // Play entrance animation
                playlistsRecyclerView.scheduleLayoutAnimation();
                
                // Check if all data has loaded
                checkAllDataLoaded();
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error loading featured playlists: " + message);
                
                // Show cached playlists if available
                if (!cachedFeaturedPlaylists.isEmpty()) {
                    Log.d(TAG, "Using cached playlists after error");
                    playlistsAdapter.updateData(cachedFeaturedPlaylists);
                    showContentState(shimmerPlaylists, playlistsRecyclerView);
                } else {
                    loadFallbackPlaylists();
                }
                
                // Check if all data has loaded
                checkAllDataLoaded();
            }
        });
    }
    
    private void loadNewReleasesWithFiltering() {
        showLoadingState(shimmerNewReleases, newReleasesRecyclerView);
        
        // Get user's preferred genres for filtering
        FirebaseUser user = firebaseAuth.getCurrentUser();
        List<String> preferredGenres = new ArrayList<>();
        
        if (user != null) {
            firestore.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && documentSnapshot.contains("preferredGenres")) {
                            preferredGenres.addAll((List<String>) documentSnapshot.get("preferredGenres"));
                        }
                        fetchNewReleases(preferredGenres);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting user preferred genres: " + e.getMessage());
                        fetchNewReleases(preferredGenres);
                    });
        } else {
            fetchNewReleases(preferredGenres);
        }
    }
    
    private void fetchNewReleases(List<String> genres) {
        // Get new releases with filtering
        apiDataProvider.getNewReleasesWithFiltering(0, 15, genres, 
                new ApiDataProvider.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks.isEmpty()) {
                    Log.w(TAG, "No new releases returned");
                    loadFallbackNewReleases();
                    return;
                }
                
                // Save to cache
                cachedNewReleases.clear();
                cachedNewReleases.addAll(tracks);
                
                // Update UI with new releases
                newReleasesAdapter.updateData(tracks);
                
                // Show content
                showContentState(shimmerNewReleases, newReleasesRecyclerView);
                
                // Play entrance animation
                newReleasesRecyclerView.scheduleLayoutAnimation();
                
                // Check if all data has loaded
                checkAllDataLoaded();
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error loading new releases: " + message);
                
                // Show cached new releases if available
                if (!cachedNewReleases.isEmpty()) {
                    Log.d(TAG, "Using cached new releases after error");
                    newReleasesAdapter.updateData(cachedNewReleases);
                    showContentState(shimmerNewReleases, newReleasesRecyclerView);
                } else {
                    loadFallbackNewReleases();
                }
                
                // Check if all data has loaded
                checkAllDataLoaded();
            }
        });
    }
    
    private void loadFallbackRecommendedTracks() {
        // Create a few fallback tracks in case API fails
        List<Track> fallbackTracks = new ArrayList<>();
        fallbackTracks.add(new Track("1", "Offline Track 1", "Local Artist", "Banana Music", "", 180000, "Local", true));
        fallbackTracks.add(new Track("2", "Offline Track 2", "Local Artist", "Banana Music", "", 210000, "Local", true));
        fallbackTracks.add(new Track("3", "Offline Track 3", "Local Artist", "Banana Music", "", 195000, "Local", true));
        recommendedAdapter.updateData(fallbackTracks);
    }
    
    private void loadFallbackPlaylists() {
        // Create fallback playlists in case API fails
        List<Playlist> fallbackPlaylists = new ArrayList<>();
        fallbackPlaylists.add(new Playlist("local1", "Top Hits", "Banana Music", "Popular tracks for you", "", false, "Local"));
        fallbackPlaylists.add(new Playlist("local2", "Chill Vibes", "Banana Music", "Relaxing music collection", "", false, "Local"));
        fallbackPlaylists.add(new Playlist("local3", "Workout Mix", "Banana Music", "Energy boosting tracks", "", false, "Local"));
        playlistsAdapter.updateData(fallbackPlaylists);
    }
    
    private void loadFallbackNewReleases() {
        // Create fallback new releases in case API fails
        List<Track> fallbackTracks = new ArrayList<>();
        fallbackTracks.add(new Track("new1", "New Release 1", "Popular Artist", "Latest Album", "", 180000, "Local", true));
        fallbackTracks.add(new Track("new2", "New Release 2", "Rising Star", "Debut Album", "", 200000, "Local", true));
        fallbackTracks.add(new Track("new3", "New Release 3", "Famous Band", "Fresh Singles", "", 190000, "Local", true));
        newReleasesAdapter.updateData(fallbackTracks);
    }
    
    private void loadCollaborativePlaylists() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "Loading collaborative playlists for user: " + currentUser.getUid());
            
            // Show loading state
            if (shimmerCollaborative != null) {
                shimmerCollaborative.setVisibility(View.VISIBLE);
                shimmerCollaborative.startShimmer();
            }
            if (collaborativePlaylistsRecyclerView != null) {
                collaborativePlaylistsRecyclerView.setVisibility(View.GONE);
            }
            if (emptyCollaborativeView != null) {
                emptyCollaborativeView.setVisibility(View.GONE);
            }

            // Load from Firestore - collaborative playlists where user is creator or collaborator
            firestore.collection("playlists")
                .whereEqualTo("isCollaborative", true)
                .whereArrayContains("collaborators", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isAdded()) {
                        Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " collaborative playlists");
                        
                        List<Playlist> collaborativePlaylists = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            Log.d(TAG, "Processing playlist: " + document.getId());
                            
                            // Create playlist object with required fields
                            Playlist playlist = new Playlist();
                            playlist.setPlaylistId(document.getId());
                            playlist.setName(document.getString("name"));
                            playlist.setDescription(document.getString("description"));
                            playlist.setCreatorName(document.getString("creatorName"));
                            playlist.setCoverImageUrl(document.getString("coverImageUrl"));
                            playlist.setCollaborative(true);
                            playlist.setSource("Firebase");
                            
                            // Set default values if data is missing
                            if (playlist.getName() == null) {
                                playlist.setName(getString(R.string.playlist_name));
                            }
                            if (playlist.getDescription() == null) {
                                playlist.setDescription(getString(R.string.no_description));
                            }
                            if (playlist.getCreatorName() == null) {
                                playlist.setCreatorName(getString(R.string.unknown_creator));
                            }
                            
                            // Get tracks data if available
                            Map<String, Object> trackData = (Map<String, Object>) document.get("trackData");
                            List<String> trackIds = new ArrayList<>();
                            if (trackData != null) {
                                trackIds.addAll(trackData.keySet());
                                playlist.setTrackIds(trackIds);
                                playlist.setTrackData(trackData);
                                
                                List<Track> tracks = new ArrayList<>();
                                for (Map.Entry<String, Object> entry : trackData.entrySet()) {
                                    Map<String, Object> trackInfo = (Map<String, Object>) entry.getValue();
                                    Track track = new Track(
                                        entry.getKey(),
                                        (String) trackInfo.get("title"),
                                        (String) trackInfo.get("artist"),
                                        (String) trackInfo.get("album"),
                                        (String) trackInfo.get("albumArtUrl"),
                                        ((Number) trackInfo.get("duration")).longValue(),
                                        (String) trackInfo.get("source"),
                                        true
                                    );
                                    track.setPreviewUrl((String) trackInfo.get("previewUrl"));
                                    track.setSpotifyId((String) trackInfo.get("spotifyId"));
                                    tracks.add(track);
                                }
                                playlist.setTracks(tracks);
                                Log.d(TAG, "Added " + tracks.size() + " tracks to playlist: " + playlist.getName());
                            } else {
                                Log.d(TAG, "No track data found for playlist: " + playlist.getName());
                            }
                            
                            collaborativePlaylists.add(playlist);
                        }
                        
                        // Update adapter with playlists
                        if (collaborativePlaylistsAdapter != null) {
                            collaborativePlaylistsAdapter.updateData(collaborativePlaylists);
                            Log.d(TAG, "Updated adapter with " + collaborativePlaylists.size() + " playlists");
                        }
                        
                        // Show appropriate view based on data
                        if (collaborativePlaylists.isEmpty()) {
                            Log.d(TAG, "No collaborative playlists found, showing empty state");
                            if (collaborativePlaylistsRecyclerView != null) {
                                collaborativePlaylistsRecyclerView.setVisibility(View.GONE);
                            }
                            if (emptyCollaborativeView != null) {
                                emptyCollaborativeView.setVisibility(View.VISIBLE);
                            }
                        } else {
                            Log.d(TAG, "Showing " + collaborativePlaylists.size() + " collaborative playlists");
                            if (collaborativePlaylistsRecyclerView != null) {
                                collaborativePlaylistsRecyclerView.setVisibility(View.VISIBLE);
                            }
                            if (emptyCollaborativeView != null) {
                                emptyCollaborativeView.setVisibility(View.GONE);
                            }
                        }
                        
                        // Hide loading state
                        if (shimmerCollaborative != null) {
                            shimmerCollaborative.stopShimmer();
                            shimmerCollaborative.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Log.e(TAG, "Error loading collaborative playlists: " + e.getMessage());
                        // Hide loading state
                        if (shimmerCollaborative != null) {
                            shimmerCollaborative.stopShimmer();
                            shimmerCollaborative.setVisibility(View.GONE);
                        }
                        // Show empty state
                        if (collaborativePlaylistsRecyclerView != null) {
                            collaborativePlaylistsRecyclerView.setVisibility(View.GONE);
                        }
                        if (emptyCollaborativeView != null) {
                            emptyCollaborativeView.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(requireContext(), getString(R.string.error_loading_playlists, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
        } else {
            Log.d(TAG, "User not logged in, showing empty state");
            // User not logged in
            if (shimmerCollaborative != null) {
                shimmerCollaborative.stopShimmer();
                shimmerCollaborative.setVisibility(View.GONE);
            }
            if (collaborativePlaylistsRecyclerView != null) {
                collaborativePlaylistsRecyclerView.setVisibility(View.GONE);
            }
            if (emptyCollaborativeView != null) {
                emptyCollaborativeView.setVisibility(View.VISIBLE);
            }
        }
    }
    
    private void checkAllDataLoaded() {
        // Check if all data sections have loaded
        boolean recommendedLoaded = !recommendedAdapter.isEmpty();
        boolean playlistsLoaded = !playlistsAdapter.isEmpty();
        boolean newReleasesLoaded = !newReleasesAdapter.isEmpty();
        boolean collaborativeLoaded = !collaborativePlaylistsAdapter.isEmpty() || emptyCollaborativeView.getVisibility() == View.VISIBLE;
        
        if (recommendedLoaded && playlistsLoaded && newReleasesLoaded && collaborativeLoaded) {
            // Hide loading indicators
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            
            // Update loading state
            isDataLoading = false;
            hasInitialDataLoaded = true;
            
            Log.d(TAG, "All data loaded successfully");
        }
    }
    
    @Override
    public void onTrackClick(Track track) {
        // Show track details
        Bundle args = new Bundle();
        args.putParcelable("track", track);
        
        Navigation.findNavController(requireView())
                .navigate(R.id.action_navigation_home_to_trackDetailFragment, args);
                
        // Add to history for better recommendations
        trackManager.addToHistory(track);
    }
    
    @Override
    public void onPlayButtonClick(Track track, int position) {
        playTrack(track);
        
        // Add track to history for better recommendations
        trackManager.addToHistory(track);
        
        // Apply play animation on the item
        View view = recommendedRecyclerView.findViewHolderForAdapterPosition(position).itemView;
        View playButton = view.findViewById(R.id.btnPlayPause);
        
        if (playButton != null) {
            playButton.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(200)
                    .withEndAction(() -> 
                        playButton.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(200)
                                .start()
                    )
                    .start();
        }
    }
    
    @Override
    public void onPlaylistClick(Playlist playlist) {
        // Get title from playlist name
        final String playlistTitle = playlist.getName() != null && !playlist.getName().isEmpty() 
            ? playlist.getName() 
            : getString(R.string.playlist_detail_title);
        
        // Check if we have preloaded tracks for this playlist
        if (playlist.getPreloadedTracks() != null && !playlist.getPreloadedTracks().isEmpty()) {
            Log.d(TAG, "Using preloaded tracks for playlist " + playlist.getName());
            showPlaylistDetail(playlist, playlistTitle);
        } else {
            // Show loading dialog
            ProgressDialogFragment progressDialog = ProgressDialogFragment.newInstance(
                    getString(R.string.loading_playlist_title),
                    getString(R.string.loading_playlist_message));
            progressDialog.show(getChildFragmentManager(), "loading_playlist");
            
            // Load playlist tracks
            String playlistId = playlist.getId();
            apiDataProvider.getPlaylistTracks(playlistId, new ApiDataProvider.TracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    // Dismiss loading dialog
                    progressDialog.dismiss();
                    
                    // Store tracks in playlist object
                    playlist.setPreloadedTracks(tracks);
                    
                    // Show playlist detail
                    showPlaylistDetail(playlist, playlistTitle);
                }
                
                @Override
                public void onError(String message) {
                    // Dismiss loading dialog
                    progressDialog.dismiss();
                    
                    // Show error toast
                    Toast.makeText(requireContext(), 
                                  getString(R.string.error_loading_playlist, message), 
                                  Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    
    private void showPlaylistDetail(Playlist playlist, String title) {
        // Create bundle with playlist data
        Bundle args = new Bundle();
        args.putParcelable("playlist", playlist);
        args.putString("title", title);
        
        // Navigate to playlist detail fragment
        Navigation.findNavController(requireView())
                .navigate(R.id.action_navigation_home_to_playlistDetailFragment, args);
    }
    
    private void playTrack(Track track) {
        if (track == null) {
            showError("Invalid track");
            return;
        }
        
        // Start loading animation
        progressBar.setVisibility(View.VISIBLE);
        
        // Update mini player in MainActivity if it exists
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateMiniPlayer(track);
            progressBar.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
            showError("Unable to play track");
        }
    }
    
    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Refresh data if needed
        if (shouldRefreshOnResume) {
            shouldRefreshOnResume = false;
            refreshData();
        } else if (!hasInitialDataLoaded) {
            // Load initial data if it hasn't been loaded yet
            loadData();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        // Mark that we should refresh on resume if we've been paused for a while
        handler.postDelayed(() -> shouldRefreshOnResume = true, 300000); // 5 minutes
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onPlaylistCreated() {
        // Set flag to refresh data when fragment resumes
        shouldRefreshOnResume = true;
    }

    @Override
    public void onPlaylistSaveClick(Playlist playlist) {
        if (playlist == null) return;
        
        // Toggle save state
        boolean currentlySaved = playlist.isSaved();
        playlist.setSaved(!currentlySaved);
        
        // Get Firebase instances
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in to save playlists", Toast.LENGTH_SHORT).show();
            playlist.setSaved(currentlySaved); // Revert state
            return;
        }
        
        // Reference to user's saved playlists
        DocumentReference userSavedPlaylistRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.getUid())
            .collection("savedPlaylists")
            .document(playlist.getPlaylistId());
        
        if (playlist.isSaved()) {
            // Save playlist
            Map<String, Object> playlistData = new HashMap<>();
            playlistData.put("playlistId", playlist.getPlaylistId());
            playlistData.put("savedAt", FieldValue.serverTimestamp());
            
            userSavedPlaylistRef.set(playlistData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(), "Playlist saved", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to save playlist", Toast.LENGTH_SHORT).show();
                    playlist.setSaved(currentlySaved); // Revert state on failure
                });
        } else {
            // Remove from saved playlists
            userSavedPlaylistRef.delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(), "Playlist removed from saved", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to remove playlist", Toast.LENGTH_SHORT).show();
                    playlist.setSaved(currentlySaved); // Revert state on failure
                });
        }
    }

    @Override
    public void onShareTrack(Track track) {
        if (track.isSharable()) {
            // Analytics tracking of shared content
            Bundle params = new Bundle();
            params.putString("track_id", track.getId());
            params.putString("track_name", track.getTitle());
            params.putString("artist_name", track.getArtist());
            params.putString("source", track.getSource());
            
            // Track this share event if analytics is available
            if (firebaseAnalytics != null) {
                firebaseAnalytics.logEvent("share_track", params);
            }
            
            // Track user engagement with recommended content
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                firestore.collection("users").document(user.getUid())
                    .collection("engagement")
                    .add(new HashMap<String, Object>() {{
                        put("trackId", track.getId());
                        put("action", "share");
                        put("timestamp", System.currentTimeMillis());
                    }})
                    .addOnSuccessListener(documentReference -> 
                        Log.d(TAG, "Engagement recorded with ID: " + documentReference.getId()))
                    .addOnFailureListener(e -> 
                        Log.w(TAG, "Error recording engagement", e));
            }
        }
    }

    private void loadSpotifyRecommendations() {
        if (!isAdded()) return;

        // Start shimmer animation
        if (shimmerRecommended != null) {
            shimmerRecommended.setVisibility(View.VISIBLE);
            shimmerRecommended.startShimmer();
        }

        Log.d(TAG, "Loading Spotify recommendations");

        // Get recommendations from Spotify
        spotifyHelper.getRecommendations(new SpotifyHelper.SpotifyRecommendationsCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (!isAdded()) return;

                Log.d(TAG, "Got " + tracks.size() + " Spotify recommendations");

                // Ensure track source is set
                for (Track track : tracks) {
                    if (track.getSource() == null || track.getSource().isEmpty()) {
                        track.setSource("Spotify");
                    }
                }

                // Stop shimmer animation
                if (shimmerRecommended != null) {
                    shimmerRecommended.hideShimmer();
                    shimmerRecommended.setVisibility(View.GONE);
                }

                // Update UI on main thread
                handler.post(() -> {
                    if (!isAdded()) return;

                    // Update adapter with new tracks
                    if (recommendedAdapter == null) {
                        recommendedAdapter = new RecommendedAdapter(requireContext(), tracks, HomeFragment.this);
                        recommendedRecyclerView.setAdapter(recommendedAdapter);
                    } else {
                        recommendedAdapter.updateData(tracks);
                    }

                    // Show empty view if needed
                    if (tracks.isEmpty()) {
                        // TODO: Add empty view
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                Log.e(TAG, "Error loading Spotify recommendations: " + message);

                // Stop shimmer animation
                if (shimmerRecommended != null) {
                    shimmerRecommended.hideShimmer();
                    shimmerRecommended.setVisibility(View.GONE);
                }

                // Show error message if needed
                Toast.makeText(requireContext(), "Error loading recommendations: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSpotifyPlaylists() {
        if (!isAdded()) return;

        // Start shimmer animation
        if (shimmerPlaylists != null) {
            shimmerPlaylists.setVisibility(View.VISIBLE);
            shimmerPlaylists.startShimmer();
        }

        Log.d(TAG, "Loading Spotify playlists");

        // Get featured playlists from Spotify
        spotifyHelper.getFeaturedPlaylists(new SpotifyHelper.SpotifyPlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (!isAdded()) return;

                Log.d(TAG, "Got " + playlists.size() + " Spotify playlists");

                // Ensure playlist source is set
                for (Playlist playlist : playlists) {
                    if (playlist.getSource() == null || playlist.getSource().isEmpty()) {
                        playlist.setSource("Spotify");
                    }
                }

                // Stop shimmer animation
                if (shimmerPlaylists != null) {
                    shimmerPlaylists.hideShimmer();
                    shimmerPlaylists.setVisibility(View.GONE);
                }

                // Update UI on main thread
                handler.post(() -> {
                    if (!isAdded()) return;

                    // Update adapter with new playlists
                    if (playlistsAdapter == null) {
                        playlistsAdapter = new PlaylistAdapter(requireContext(), playlists, HomeFragment.this);
                        playlistsRecyclerView.setAdapter(playlistsAdapter);
                    } else {
                        playlistsAdapter.updateData(playlists);
                    }

                    // Show empty view if needed
                    if (playlists.isEmpty()) {
                        // TODO: Add empty view
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                Log.e(TAG, "Error loading Spotify playlists: " + message);

                // Stop shimmer animation
                if (shimmerPlaylists != null) {
                    shimmerPlaylists.hideShimmer();
                    shimmerPlaylists.setVisibility(View.GONE);
                }

                // Show error message if needed
                Toast.makeText(requireContext(), "Error loading playlists: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSpotifyPlaylistTracks(Playlist playlist) {
        if (!isAdded() || playlist == null) return;

        // Show loading
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        // Get playlist tracks from Spotify
        spotifyHelper.getPlaylistTracks(playlist.getPlaylistId(), new SpotifyHelper.JSONResponseCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) return;

                try {
                    // Parse tracks from JSON
                    List<Track> tracks = new ArrayList<>();
                    if (response.has("items")) {
                        JSONArray items = response.getJSONArray("items");
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            if (item.has("track")) {
                                JSONObject trackJson = item.getJSONObject("track");
                                Track track = spotifyHelper.parseTrackJson(trackJson);
                                tracks.add(track);
                            }
                        }
                    }

                    Log.d(TAG, "Got " + tracks.size() + " tracks for playlist: " + playlist.getName());

                    // Hide loading
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }

                    // Add tracks to playlist
                    playlist.setTracks(tracks);

                    // Navigate to playlist detail
                    Bundle args = new Bundle();
                    args.putString("playlistId", playlist.getPlaylistId());
                    args.putString("playlistName", playlist.getName());
                    args.putString("playlistImage", playlist.getCoverImageUrl());
                    args.putBoolean("isCollaborative", playlist.isCollaborative());
                    args.putString("source", playlist.getSource());
                    
                    Navigation.findNavController(requireView())
                            .navigate(R.id.action_navigation_home_to_playlistDetailFragment, args);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing playlist tracks: " + e.getMessage());
                    onError("Error parsing playlist tracks: " + e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                Log.e(TAG, "Error loading playlist tracks: " + message);

                // Hide loading
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                // Show error
                Toast.makeText(requireContext(), "Error loading playlist: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addRecyclerViewAnimation(RecyclerView recyclerView) {
        if (recyclerView == null) return;
        
        // Add item animation
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        
        // Add layout animation
        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(
            requireContext(), R.anim.layout_animation_slide_from_bottom);
        recyclerView.setLayoutAnimation(animation);
    }
} 