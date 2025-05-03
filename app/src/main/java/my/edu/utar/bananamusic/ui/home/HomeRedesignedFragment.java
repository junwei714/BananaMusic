package my.edu.utar.bananamusic.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DividerItemDecoration;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.EnhancedRecommendationAdapter;
import my.edu.utar.bananamusic.adapters.PlaylistAdapter;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.services.AIRecommendationService;
import my.edu.utar.bananamusic.ui.base.SafeFragment;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.FirebaseAuthHelper;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.RecommendationEngine;
import com.google.firebase.auth.FirebaseUser;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.utils.DeezerHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.utils.callbacks.BaseCallback;
import my.edu.utar.bananamusic.utils.SpotifyHelper.SpotifyPlaylistsCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import my.edu.utar.bananamusic.utils.MoodBasedRecommendationManager;
import my.edu.utar.bananamusic.adapters.MoodSelectionAdapter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import my.edu.utar.bananamusic.network.SpotifyApiService;
import my.edu.utar.bananamusic.network.DeezerApiService;
import my.edu.utar.bananamusic.services.MusicSearchService;
import my.edu.utar.bananamusic.utils.SpotifyConfig;
import my.edu.utar.bananamusic.adapters.TopTrendingAdapter;
import my.edu.utar.bananamusic.models.Album;

/**
 * Redesigned Home Fragment with enhanced personalized recommendations
 */
public class HomeRedesignedFragment extends SafeFragment implements 
        EnhancedRecommendationAdapter.OnTrackClickListener, 
        PlaylistAdapter.OnPlaylistClickListener,
        RecommendationEngine.RecommendationListener {
    
    private static final String TAG = "HomeRedesignedFragment";
    
    // UI Components
    private TextView tvWelcome;
    private TextView tvCurrentMood;
    private ImageButton ivMoodEmoji;
    private TextView tvTimeRecommendationTitle;
    private TextView tvRecommendationContext;
    
    // RecyclerViews
    private RecyclerView rvRecommendations;
    private RecyclerView rvTrendingPlaylists;
    private RecyclerView rvCollaborativePlaylists;
    private RecyclerView rvTopTrending;
    
    // Adapters
    private EnhancedRecommendationAdapter recommendationAdapter;
    private PlaylistAdapter trendingPlaylistsAdapter;
    private PlaylistAdapter collaborativePlaylistsAdapter;
    private TopTrendingAdapter topTrendingAdapter;
    
    // Services and helpers
    private AudioPlayerHelper audioPlayerHelper;
    private ApiDataProvider apiDataProvider;
    private PlaylistManager playlistManager;
    private RecommendationEngine recommendationEngine;
    private FirebaseAuthHelper firebaseAuthHelper;
    private SpotifyHelper spotifyHelper;
    private DeezerHelper deezerHelper;
    
    // State
    private String currentMood = "happy";
    private boolean isInitialLoad = true;
    
    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;
    
    private MoodBasedRecommendationManager moodRecommendationManager;
    private ChipGroup chipGroupMoods;
    private ImageButton btnRefreshRecommendations;
    
    private SpotifyApiService spotifyApiService;
    private DeezerApiService deezerApiService;
    private MusicSearchService musicSearchService;
    
    private Handler refreshHandler;
    private static final long REFRESH_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize helpers
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        apiDataProvider = ApiDataProvider.getInstance(requireContext());
        playlistManager = PlaylistManager.getInstance(requireContext());
        recommendationEngine = RecommendationEngine.getInstance(requireContext());
        firebaseAuthHelper = FirebaseAuthHelper.getInstance();
        spotifyHelper = SpotifyHelper.getInstance(requireContext());
        deezerHelper = DeezerHelper.getInstance(requireContext());
        
        // Register as recommendation listener
        recommendationEngine.addListener(this);
        
        firestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
        
        initializeSearchServices();
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home_redesigned, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views first
        initViews(view);
        
        // Initialize the mood recommendation manager
        moodRecommendationManager = new MoodBasedRecommendationManager(requireContext());
        
        // Setup logo button click
        setupLogoButton();
        
        // Set user's name in welcome message
        setUserWelcomeMessage();
        
        // Initialize adapters
        setupAdapters();
        
        // Load initial data
        loadTimeBasedRecommendations();
        loadTrendingPlaylists();
        loadCollaborativePlaylists();
        loadRecommendations();
        loadTopTrending();
        
        // Setup refresh handler
        setupRefreshHandler();
    }
    
    private void initViews(View view) {
        // Initialize Views
        tvWelcome = view.findViewById(R.id.tvWelcome);
        tvCurrentMood = view.findViewById(R.id.tvCurrentMood);
        ivMoodEmoji = view.findViewById(R.id.logo_button);
        tvTimeRecommendationTitle = view.findViewById(R.id.tvMorningEnergyTitle);
        tvRecommendationContext = view.findViewById(R.id.tvRecommendationContext);
        
        // Initialize RecyclerViews with null checks
        rvRecommendations = view.findViewById(R.id.rvRecommendations);
        if (rvRecommendations == null) {
            Log.e(TAG, "Failed to find rvRecommendations");
        }
        
        rvTopTrending = view.findViewById(R.id.rvTopTrending);
        if (rvTopTrending == null) {
            Log.e(TAG, "Failed to find rvTopTrending");
        }
        
        rvCollaborativePlaylists = view.findViewById(R.id.rvCollaborativePlaylists);
        if (rvCollaborativePlaylists == null) {
            Log.e(TAG, "Failed to find rvCollaborativePlaylists");
        }
        
        // Set up RecyclerViews with their LayoutManagers
        if (rvRecommendations != null) {
            rvRecommendations.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        }
        
        if (rvTopTrending != null) {
            rvTopTrending.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
        
        if (rvCollaborativePlaylists != null) {
            rvCollaborativePlaylists.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        }
    }
    
    private void setupLogoButton() {
        if (ivMoodEmoji != null) {
            ivMoodEmoji.setOnClickListener(v -> showMoodSelectionDialog());
        }
    }

    private void showMoodSelectionDialog() {
        if (!isAdded()) return;

        // Create and show the dialog
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_mood_selection);

        // Set dialog width to match parent with margins
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(Gravity.CENTER);
            
            // Add animation
            window.setWindowAnimations(R.style.DialogAnimation);
            
            // Add corner radius
            window.setBackgroundDrawableResource(R.drawable.rounded_dialog_background);
        }

        // Replace the RecyclerView initialization with direct click listeners for mood cards
        MaterialCardView moodHappy = dialog.findViewById(R.id.moodHappy);
        MaterialCardView moodSad = dialog.findViewById(R.id.moodSad);
        MaterialCardView moodChill = dialog.findViewById(R.id.moodChill);
        MaterialCardView moodParty = dialog.findViewById(R.id.moodParty);
        MaterialCardView moodFocus = dialog.findViewById(R.id.moodFocus);
        MaterialCardView moodRomantic = dialog.findViewById(R.id.moodRomantic);

        View.OnClickListener moodClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedMood = "";
                if (v.getId() == R.id.moodHappy) {
                    selectedMood = "Happy";
                } else if (v.getId() == R.id.moodSad) {
                    selectedMood = "Sad";
                } else if (v.getId() == R.id.moodChill) {
                    selectedMood = "Chill";
                } else if (v.getId() == R.id.moodParty) {
                    selectedMood = "Party";
                } else if (v.getId() == R.id.moodFocus) {
                    selectedMood = "Focus";
                } else if (v.getId() == R.id.moodRomantic) {
                    selectedMood = "Romantic";
                }
                
                if (!selectedMood.isEmpty()) {
                    // Update UI and fetch songs based on mood
                    updateMood(selectedMood);
                    dialog.dismiss();
                }
            }
        };

        moodHappy.setOnClickListener(moodClickListener);
        moodSad.setOnClickListener(moodClickListener);
        moodChill.setOnClickListener(moodClickListener);
        moodParty.setOnClickListener(moodClickListener);
        moodFocus.setOnClickListener(moodClickListener);
        moodRomantic.setOnClickListener(moodClickListener);

        dialog.show();
    }

    private void updateMood(String mood) {
        Log.d(TAG, "Updating mood to: " + mood);
        this.currentMood = mood;
        
        // Update adapter's current mood
        if (recommendationAdapter != null) {
            Log.d(TAG, "Setting current mood in adapter");
            recommendationAdapter.setCurrentMood(mood);
        } else {
            Log.e(TAG, "Cannot update mood - recommendationAdapter is null");
        }
        
        // Update mood display
        if (tvCurrentMood != null) {
            String moodEmoji = getMoodEmoji(mood);
            String displayText = "Current mood: " + mood.substring(0, 1).toUpperCase() + mood.substring(1);
            if (moodEmoji != null) {
                displayText += " " + moodEmoji;
            }
            tvCurrentMood.setText(displayText);
            Log.d(TAG, "Updated mood display text: " + displayText);
        } else {
            Log.e(TAG, "Cannot update mood display - tvCurrentMood is null");
        }

        // Update recommendation context
        if (tvRecommendationContext != null) {
            String contextText = "Based on your " + mood + " mood";
            tvRecommendationContext.setText(contextText);
            Log.d(TAG, "Updated recommendation context: " + contextText);
        } else {
            Log.e(TAG, "Cannot update recommendation context - tvRecommendationContext is null");
        }

        // Add animation for mood transition
        animateMoodTransition(mood);
        
        // Load new recommendations with slight delay for smooth animation
        new Handler().postDelayed(() -> {
            Log.d(TAG, "Loading new recommendations for mood: " + mood);
            loadRecommendations();
        }, 300);
    }

    private String getMoodEmoji(String mood) {
        switch (mood.toLowerCase()) {
            case "happy": return "😊";
            case "sad": return "😢";
            case "chill": return "😌";
            case "party": return "🎉";
            case "focus": return "🎯";
            case "romantic": return "💝";
            default: return "��";
        }
    }
    
    private void setupAdapters() {
        // Setup recommendation adapter if view exists
        if (rvRecommendations != null) {
            recommendationAdapter = new EnhancedRecommendationAdapter(requireContext(), new ArrayList<>(), this);
            rvRecommendations.setAdapter(recommendationAdapter);
        }

        // Setup collaborative playlists adapter if view exists
        if (rvCollaborativePlaylists != null) {
            collaborativePlaylistsAdapter = new PlaylistAdapter(requireContext(), this);
            rvCollaborativePlaylists.setAdapter(collaborativePlaylistsAdapter);
        }

        // Setup top trending adapter if view exists
        if (rvTopTrending != null) {
            topTrendingAdapter = new TopTrendingAdapter(new TopTrendingAdapter.OnTrackClickListener() {
                @Override
                public void onTrackClick(Track track, int position) {
                    if (track.getDeezerUrl() != null) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(track.getDeezerUrl()));
                        startActivity(intent);
                    }
                }

                @Override
                public void onPlayPreviewClick(Track track, int position) {
                    if (track.getPreviewUrl() != null) {
                        audioPlayerHelper.playTrack(track);
                    }
                }
            });
            rvTopTrending.setAdapter(topTrendingAdapter);
        }
    }
    
    private void setUserWelcomeMessage() {
        FirebaseUser currentUser = firebaseAuthHelper.getCurrentUser();
        String displayName = currentUser != null ? currentUser.getDisplayName() : null;
                
        if (displayName != null && !displayName.isEmpty()) {
            tvWelcome.setText(getString(R.string.welcome_user, displayName));
        } else {
            tvWelcome.setText(R.string.welcome_back);
        }
    }
    
    /**
     * Animate the transition between moods with a subtle ripple effect
     */
    private void animateMoodTransition(String mood) {
        if (getActivity() == null || getView() == null) return;
        
        // Create ripple
        View rippleView = new View(requireContext());
        rippleView.setBackgroundResource(getMoodRippleBackground(mood));
        
        // Get ripple container
        FrameLayout rippleContainer = getView().findViewById(R.id.rippleContainer);
        if (rippleContainer != null) {
            // Set up ripple dimensions
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            rippleView.setLayoutParams(params);
            
            // Add to container
            rippleContainer.addView(rippleView);
            
            // Animate
            rippleView.setAlpha(0f);
            rippleView.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .withEndAction(() -> {
                        // Clean up when animation ends
                        if (rippleContainer != null) {
                            rippleContainer.removeView(rippleView);
                        }
                    });
        }
    }
    
    /**
     * Get the appropriate ripple background resource for the selected mood
     */
    private int getMoodRippleBackground(String mood) {
        switch (mood.toLowerCase()) {
            case "happy":
                return R.drawable.ripple_mood_happy;
            case "sad":
                return R.drawable.ripple_mood_sad;
            case "energetic":
                return R.drawable.ripple_mood_energetic;
            case "relaxed":
                return R.drawable.ripple_mood_relaxed;
            case "focused":
                return R.drawable.ripple_mood_focused;
            default:
                return R.drawable.ripple_mood_default;
        }
    }
    
    private void loadTimeBasedRecommendations() {
        if (tvTimeRecommendationTitle == null) {
            Log.w(TAG, "Time recommendation title view is not initialized");
            return;
        }

        // Set appropriate time-based title based on time of day
        int hour = java.time.LocalTime.now().getHour();
        String timeTitle;
        String timeSubtitle;
        
        if (hour >= 5 && hour < 12) {
            timeTitle = "Good Morning";
            timeSubtitle = "Start your day with these tracks";
        } else if (hour >= 12 && hour < 17) {
            timeTitle = "Good Afternoon";
            timeSubtitle = "Perfect midday music";
        } else if (hour >= 17 && hour < 21) {
            timeTitle = "Good Evening";
            timeSubtitle = "Wind down with these selections";
        } else {
            timeTitle = "Night Vibes";
            timeSubtitle = "Music for your night session";
        }
        
        tvTimeRecommendationTitle.setText(timeTitle);
    }
    
    private void loadRecommendations() {
        try {
            if (rvRecommendations == null || recommendationAdapter == null) {
                Log.e(TAG, "Cannot load recommendations - views not initialized");
                return;
            }
            
            // Show loading state
            showLoadingIndicator(rvRecommendations);
            
            Log.d(TAG, "Loading recommendations for mood: " + currentMood);
            
            // Get recommendations based on current mood
            moodRecommendationManager.getRecommendationsForMood(currentMood, 10, new MoodBasedRecommendationManager.RecommendationCallback() {
                @Override
                public void onSuccess(List<Track> tracks, String mood) {
                    if (!isAdded()) return;
                    
                    hideLoadingIndicator(rvRecommendations);
                    
                    if (tracks == null || tracks.isEmpty()) {
                        Log.w(TAG, "No tracks returned for mood: " + mood);
                        showEmptyState(rvRecommendations, "No recommendations available for " + mood + " mood");
                        return;
                    }
                    
                    Log.d(TAG, "Loaded " + tracks.size() + " recommendation tracks for mood: " + mood);
                    
                    // Update UI with recommendation data with animation
                    animateRecommendationsUpdate(tracks);
                    
                    // Update recommendation context
                    updateRecommendationContext("Mood: " + mood.substring(0, 1).toUpperCase() + mood.substring(1) + " " + getMoodEmoji(mood));
                }
                
                @Override
                public void onError(String message) {
                    if (!isAdded()) return;
                    
                    hideLoadingIndicator(rvRecommendations);
                    showEmptyState(rvRecommendations, message);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error loading recommendations", e);
            hideLoadingIndicator(rvRecommendations);
            showEmptyState(rvRecommendations, "Failed to load recommendations");
        }
    }
    
    /**
     * Generate personalized recommendation reason based on mood and track
     */
    private String getMoodBasedReason(String mood, Track track) {
        String[] happyReasons = {
            "Perfect for your upbeat " + mood + " mood",
            "This matches your " + mood + " vibes right now",
            "Just what you need when you're feeling " + mood,
            "Recommended for your current " + mood + " mood"
        };
        
        String[] sadReasons = {
            "This resonates with your " + mood + " emotions",
            "When you're feeling " + mood + ", this hits different",
            "Perfect soundtrack for a " + mood + " moment",
            "This understands your " + mood + " mood"
        };
        
        String[] energeticReasons = {
            "Boost your " + mood + " levels with this track",
            "Perfect rhythm for your " + mood + " state",
            "This matches your " + mood + " energy",
            "Keep the " + mood + " vibes going with this"
        };
        
        String[] relaxedReasons = {
            "Perfect for your " + mood + " state of mind",
            "Enhance your " + mood + " mood with this tune",
            "This complements your " + mood + " mindset",
            "When you want to stay " + mood + ", this delivers"
        };
        
        String[] focusedReasons = {
            "Maintain your " + mood + " state with this track",
            "Perfect backdrop for " + mood + " activities",
            "This enhances your " + mood + " concentration",
            "Curated for your " + mood + " session"
        };
        
        String[] defaultReasons = {
            "Recommended for your current mood",
            "This matches your current vibe",
            "Perfect addition to your playlist",
            "You might enjoy this track right now"
        };
        
        String[] reasons;
        switch (mood.toLowerCase()) {
            case "happy":
                reasons = happyReasons;
                break;
            case "sad":
                reasons = sadReasons;
                break;
            case "energetic":
                reasons = energeticReasons;
                break;
            case "relaxed":
                reasons = relaxedReasons;
                break;
            case "focused":
                reasons = focusedReasons;
                break;
            default:
                reasons = defaultReasons;
                break;
        }
        
        int randomIndex = new Random().nextInt(reasons.length);
        return reasons[randomIndex];
    }
    
    /**
     * Animate the update of recommendations for a smoother experience
     */
    private void animateRecommendationsUpdate(List<Track> tracks) {
        if (recommendationAdapter == null || rvRecommendations == null) return;
        
        // First hide the recycler view
        rvRecommendations.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction(() -> {
                // Update the data
                recommendationAdapter.updateData(tracks);
                
                // Show it again with animation
                rvRecommendations.animate()
                    .alpha(1f)
                    .setDuration(300);
            });
    }
    
    /**
     * Load fallback recommendations when mood-based recommendations fail
     */
    private void loadFallbackRecommendations() {
        if (!isAdded() || rvRecommendations == null || recommendationAdapter == null) {
            return;
        }
        
        Log.d(TAG, "Loading fallback recommendations");
        
        apiDataProvider.getFeaturedContent(new ApiDataProvider.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (!isAdded()) return;
                
                if (tracks != null && !tracks.isEmpty()) {
                    Log.d(TAG, "Loaded " + tracks.size() + " fallback tracks");
                    
                    // Only update if the current adapter is empty
                    if (recommendationAdapter.getItemCount() == 0) {
                        updateRecommendationsAdapter(new HashMap<>(), new HashMap<>(), tracks);
                        updateRecommendationContext("Featured Tracks");
                    }
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error loading fallback recommendations: " + message);
                // Last resort - create dummy content
                if (isAdded() && recommendationAdapter.getItemCount() == 0) {
                    List<Track> dummyTracks = createDummyTracks();
                    updateRecommendationsAdapter(new HashMap<>(), new HashMap<>(), dummyTracks);
                    updateRecommendationContext("Suggested Tracks");
                }
            }
        });
    }
    
    /**
     * Show loading indicator on a recycler view
     */
    private void showLoadingIndicator(RecyclerView recyclerView) {
        if (recyclerView == null || !isAdded()) return;
        
        // Find or create a progress bar as a sibling to the recycler view
        ViewGroup parent = (ViewGroup) recyclerView.getParent();
        if (parent != null) {
            ProgressBar progressBar = parent.findViewById(R.id.progress_bar);
            if (progressBar == null) {
                progressBar = new ProgressBar(requireContext());
                progressBar.setId(R.id.progress_bar);
                progressBar.setIndeterminate(true);
                
                // Add progress bar to parent with same layout parameters as recycler view
                ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
                parent.addView(progressBar, params);
            }
            
            progressBar.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Hide loading indicator and show recycler view
     */
    private void hideLoadingIndicator(RecyclerView recyclerView) {
        if (recyclerView == null || !isAdded()) return;
        
        ViewGroup parent = (ViewGroup) recyclerView.getParent();
        if (parent != null) {
            View progressBar = parent.findViewById(R.id.progress_bar);
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Show error state when loading fails
     */
    private void showErrorState(RecyclerView recyclerView, String message) {
        if (recyclerView == null || !isAdded()) return;
        
        ViewGroup parent = (ViewGroup) recyclerView.getParent();
        if (parent != null) {
            TextView errorText = parent.findViewById(R.id.error_text);
            if (errorText == null) {
                errorText = new TextView(requireContext());
                errorText.setId(R.id.error_text);
                errorText.setGravity(Gravity.CENTER);
                errorText.setPadding(32, 32, 32, 32);
                errorText.setTextSize(16);
                
                // Add error text to parent with same layout parameters as recycler view
                ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
                parent.addView(errorText, params);
            }
            
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Show empty state when no results
     */
    private void showEmptyState(RecyclerView recyclerView, String message) {
        showErrorState(recyclerView, message);
    }
    
    private void loadGenreRecommendations(Map<Track, String> artistBasedRecs, Map<Track, String> genreBasedRecs, List<Track> recentFavorites) {
        // Stub method - will be implemented when API is available
    }
    
    private void loadRecentFavorites(Map<Track, String> artistBasedRecs, Map<Track, String> genreBasedRecs, List<Track> recentFavorites) {
        // Stub method - will be implemented when API is available
    }
    
    private void loadMoodRecommendations(Map<Track, String> artistBasedRecs, Map<Track, String> genreBasedRecs, List<Track> recentFavorites) {
        // Stub method - will be implemented when API is available
    }
    
    private void loadFallbackRecommendations(Map<Track, String> artistBasedRecs, Map<Track, String> genreBasedRecs, List<Track> recentFavorites) {
        // Stub method - will be implemented when API is available
    }
    
    private void updateRecommendationsAdapter(Map<Track, String> artistBasedRecs, Map<Track, String> genreBasedRecs, List<Track> recentFavorites) {
        // Simplified stub implementation
        if (isDetached() || !isAdded()) return;
        
        // Create some dummy data for testing UI
        List<Track> dummyTracks = createDummyTracks();
        
        requireActivity().runOnUiThread(() -> {
            recommendationAdapter.updateData(dummyTracks);
            
            // Show a toast on initial load
            if (isInitialLoad) {
                Toast.makeText(requireContext(), "Personalized recommendations loaded", Toast.LENGTH_SHORT).show();
                isInitialLoad = false;
            }
        });
    }
    
    private void updateRecommendationContext(String context) {
        if (isDetached() || !isAdded()) return;
        
        requireActivity().runOnUiThread(() -> {
            // Implementation of updateRecommendationContext method
        });
    }
    
    // Helper method to create some dummy tracks for testing
    private List<Track> createDummyTracks() {
        List<Track> tracks = new ArrayList<>();
        
        // Create a few dummy tracks
        Track track1 = new Track(
            "1", "Believer", "Imagine Dragons", "Evolve",
            "https://i.scdn.co/image/ab67616d0000b2732fbd77033247e889cb7d2ac4",
            180000, null, true);
        track1.setType("Track");
        track1.setDescription("Because you listened to Imagine Dragons");
            
        Track track2 = new Track(
            "2", "Peaches", "Justin Bieber", "Justice",
            "https://i.scdn.co/image/ab67616d0000b273e6f407c7f3a0ec98845e4431",
            198000, null, true);
        track2.setType("Track");
        track2.setDescription("Based on your interest in Pop");
            
        Track track3 = new Track(
            "3", "Blinding Lights", "The Weeknd", "After Hours",
            "https://i.scdn.co/image/ab67616d0000b27370dbc9f47669d47ad0ca9a8b",
            200000, null, true);
        track3.setType("Track");
        track3.setDescription("Popular right now");
        
        tracks.add(track1);
        tracks.add(track2);
        tracks.add(track3);
        
        return tracks;
    }
    
    private void loadTrendingPlaylists() {
        if (rvTrendingPlaylists == null || trendingPlaylistsAdapter == null) {
            Log.e(TAG, "Cannot load trending playlists - views not initialized");
            return;
        }
        
        Log.d(TAG, "Loading trending playlists");
        showLoadingIndicator(rvTrendingPlaylists);
        
        apiDataProvider.getTrendingPlaylists(false, new ApiDataProvider.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (!isAdded()) return;
                
                hideLoadingIndicator(rvTrendingPlaylists);
                
                if (playlists == null || playlists.isEmpty()) {
                    Log.w(TAG, "No trending playlists returned from API");
                    showEmptyState(rvTrendingPlaylists, getString(R.string.no_playlists_found));
                    
                    // Create fallback content
                    createFallbackTrendingPlaylists();
                    return;
                }
                
                Log.d(TAG, "Loaded " + playlists.size() + " trending playlists");
                
                // Update adapter
                trendingPlaylistsAdapter.updateData(playlists);
            }
            
            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                
                Log.e(TAG, "Failed to load trending playlists: " + message);
                hideLoadingIndicator(rvTrendingPlaylists);
                showErrorState(rvTrendingPlaylists, getString(R.string.error_loading_playlists));
                
                // Create fallback content
                createFallbackTrendingPlaylists();
            }
        });
    }
    
    private void createFallbackTrendingPlaylists() {
        if (!isAdded() || trendingPlaylistsAdapter == null || trendingPlaylistsAdapter.getItemCount() > 0) {
            return;
        }
        
        Log.d(TAG, "Creating fallback trending playlists");
        
        // Generate some dummy playlists
        List<Playlist> fallbackPlaylists = new ArrayList<>();
        
        // Add dummy playlists with different moods
        String[] moods = {"Happy", "Energetic", "Relaxed", "Focused", "Romantic", "Sad"};
        String[] creators = {"BananaMusic", "Music Lovers", "Trending Tracks", "Top Charts", "Popular Hits", "Featured Artists"};
        String[] colors = {"FF4B8B", "4CAF50", "2196F3", "9C27B0", "E91E63", "607D8B"}; // Different colors for different moods
        
        for (int i = 0; i < Math.min(moods.length, creators.length); i++) {
            String mood = moods[i];
            String playlistId = "local:playlist:" + i;
            String title = mood + " Hits";
            String creator = creators[i];
            String description = "Best " + mood.toLowerCase() + " tracks for your day";
            String color = colors[i];
            String imageUrl;
            
            try {
                imageUrl = String.format("https://placehold.co/400x400/%s/ffffff?text=%s", 
                    color, java.net.URLEncoder.encode(mood, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException e) {
                Log.e(TAG, "Error encoding mood name: " + e.getMessage());
                // Fallback to using the mood name directly if encoding fails
                imageUrl = String.format("https://placehold.co/400x400/%s/ffffff?text=%s", 
                    color, mood.replace(" ", "+"));
            }
            
            Playlist playlist = new Playlist(playlistId, title, creator, description, imageUrl, false, "Local");
            playlist.setMood(mood.toLowerCase());
            fallbackPlaylists.add(playlist);
            
            Log.d(TAG, String.format("Created fallback playlist: name=%s, coverImageUrl=%s", title, imageUrl));
        }
        
        // Update adapter with fallback playlists
        if (fallbackPlaylists.size() > 0) {
            Log.d(TAG, "Setting " + fallbackPlaylists.size() + " fallback playlists");
            
            // Only update UI if we're attached to activity
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (trendingPlaylistsAdapter != null) {
                        trendingPlaylistsAdapter.updateData(fallbackPlaylists);
                        
                        // Hide any error messages
                        if (rvTrendingPlaylists != null) {
                            ViewGroup parent = (ViewGroup) rvTrendingPlaylists.getParent();
                            if (parent != null) {
                                View errorText = parent.findViewById(R.id.error_text);
                                if (errorText != null) {
                                    errorText.setVisibility(View.GONE);
                                }
                            }
                            rvTrendingPlaylists.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }
    }
    
    private void loadCollaborativePlaylists() {
        FirebaseUser currentUser = firebaseAuthHelper.getCurrentUser();
        if (currentUser == null || !isAdded() || getView() == null) {
            Log.w(TAG, "Cannot load collaborative playlists - user not logged in or fragment not attached");
            return;
        }

        // Find views
        View collaborativeSection = getView().findViewById(R.id.collaborativePlaylistsSection);
        View emptyView = getView().findViewById(R.id.emptyCollaborativeView);
        
        if (collaborativeSection == null || rvCollaborativePlaylists == null) {
            Log.e(TAG, "Collaborative playlists views not found");
            return;
        }

        // Show loading state
        showLoadingIndicator(rvCollaborativePlaylists);

        // Load all public collaborative playlists from Firestore
        Log.d(TAG, "Loading collaborative playlists for user: " + currentUser.getUid());
        firestore.collection("playlists")
            .whereEqualTo("isCollaborative", true)  // Get all collaborative playlists
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!isAdded()) return;

                Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " collaborative playlists");
                List<Playlist> allCollaborativePlaylists = new ArrayList<>();

                // Process all playlists
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        String id = document.getId();
                        String name = document.getString("name");
                        String description = document.getString("description");
                        String creatorName = document.getString("creatorName");
                        String creatorId = document.getString("creatorId");
                        String coverImageUrl = document.getString("coverImageUrl");
                        boolean isCollaborative = Boolean.TRUE.equals(document.getBoolean("isCollaborative"));
                        String mood = document.getString("mood");
                        
                        // Get track data and track count
                        Map<String, Object> trackData = (Map<String, Object>) document.get("trackData");
                        List<String> trackIds = (List<String>) document.get("trackIds");
                        int trackCount = 0;
                        
                        if (trackData != null && !trackData.isEmpty()) {
                            trackCount = trackData.size();
                            Log.d(TAG, "Playlist " + name + " has " + trackCount + " tracks in trackData");
                        } else if (trackIds != null && !trackIds.isEmpty()) {
                            trackCount = trackIds.size();
                            Log.d(TAG, "Playlist " + name + " has " + trackCount + " tracks in trackIds");
                        }
                        
                        // If no cover image URL is provided, create a default one using a placeholder service
                        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
                            try {
                                String encodedName = java.net.URLEncoder.encode(name, "UTF-8");
                                coverImageUrl = "https://placehold.co/400x400/6C4AB6/ffffff?text=" + encodedName;
                            } catch (java.io.UnsupportedEncodingException e) {
                                Log.e(TAG, "Error encoding playlist name: " + e.getMessage());
                                coverImageUrl = "https://placehold.co/400x400/6C4AB6/ffffff?text=" + 
                                    name.replace(" ", "+");
                            }
                        }
                        
                        // Create playlist with correct constructor parameters
                        Playlist playlist = new Playlist(id, name, description, creatorId, creatorName, 
                            isCollaborative, mood);
                        playlist.setCoverImageUrl(coverImageUrl);
                        playlist.setTrackCount(trackCount);
                        playlist.setTrackData(trackData);
                        playlist.setTrackIds(trackIds);
                                
                        // Get collaborators list
                        List<String> collaborators = (List<String>) document.get("collaborators");
                        boolean isUserCollaborator = collaborators != null && 
                            collaborators.contains(currentUser.getUid());
                        playlist.setCollaborators(collaborators);
                        
                        // Add a label to indicate if user is part of this playlist
                        String displayDescription;
                        if (currentUser.getUid().equals(creatorId)) {
                            displayDescription = "Your collaborative playlist • " + trackCount + " tracks";
                        } else if (isUserCollaborator) {
                            displayDescription = "You're a collaborator • " + trackCount + " tracks";
                        } else {
                            displayDescription = "Public collaborative playlist • " + trackCount + " tracks";
                        }
                        playlist.setDescription(displayDescription);
                        
                        Log.d(TAG, String.format("Added playlist: name=%s, tracks=%d, coverImageUrl=%s", 
                            playlist.getName(), playlist.getTrackCount(), playlist.getCoverImageUrl()));
                        
                        allCollaborativePlaylists.add(playlist);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing playlist: " + e.getMessage());
                    }
                }

                // Sort playlists: user's playlists first, then collaborating playlists, then others
                Collections.sort(allCollaborativePlaylists, (p1, p2) -> {
                    boolean isUser1Owner = currentUser.getUid().equals(p1.getCreatorId());
                    boolean isUser2Owner = currentUser.getUid().equals(p2.getCreatorId());
                    
                    if (isUser1Owner && !isUser2Owner) return -1;
                    if (!isUser1Owner && isUser2Owner) return 1;
                    
                    List<String> collaborators1 = p1.getCollaborators();
                    List<String> collaborators2 = p2.getCollaborators();
                    
                    boolean isCollaborator1 = collaborators1 != null && collaborators1.contains(currentUser.getUid());
                    boolean isCollaborator2 = collaborators2 != null && collaborators2.contains(currentUser.getUid());
                    
                    if (isCollaborator1 && !isCollaborator2) return -1;
                    if (!isCollaborator1 && isCollaborator2) return 1;
                    
                    return p1.getName().compareTo(p2.getName());
                });

                // Update the UI
                if (getView() != null && rvCollaborativePlaylists != null) {
                    if (allCollaborativePlaylists.isEmpty()) {
                        // Show empty state
                        if (emptyView != null) {
                            emptyView.setVisibility(View.VISIBLE);
                        }
                        if (rvCollaborativePlaylists != null) {
                            rvCollaborativePlaylists.setVisibility(View.GONE);
                        }
                    } else {
                        // Show playlists
                        if (emptyView != null) {
                            emptyView.setVisibility(View.GONE);
                        }
                        if (rvCollaborativePlaylists != null) {
                            rvCollaborativePlaylists.setVisibility(View.VISIBLE);
                            collaborativePlaylistsAdapter.updateData(allCollaborativePlaylists);
                        }
                    }
                }

                hideLoadingIndicator(rvCollaborativePlaylists);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading collaborative playlists: " + e.getMessage());
                if (isAdded()) {
                    handleCollaborativePlaylistsError(e.getMessage());
                }
            });
    }

    private void handleCollaborativePlaylistsError(String message) {
        if (!isAdded() || getView() == null) return;

        View emptyView = getView().findViewById(R.id.emptyCollaborativeView);
        
        // Show error state if the RecyclerView exists
        if (rvCollaborativePlaylists != null) {
            showErrorState(rvCollaborativePlaylists, message);
        }
        
        // Show empty view as fallback
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
        }
        
        Log.e(TAG, "Error loading collaborative playlists: " + message);
    }

    // Add method to save/unsave collaborative playlist
    private void toggleSaveCollaborativePlaylist(Playlist playlist) {
        FirebaseUser currentUser = firebaseAuthHelper.getCurrentUser();
        if (currentUser == null || playlist == null) return;

        String userId = currentUser.getUid();
        String playlistId = playlist.getPlaylistId();

        // Reference to user's saved playlists
        DocumentReference userSavedPlaylistRef = firestore
            .collection("users")
            .document(userId)
            .collection("savedPlaylists")
            .document(playlistId);

        if (playlist.isSaved()) {
            // Remove from saved playlists
            userSavedPlaylistRef.delete()
                .addOnSuccessListener(aVoid -> {
                    playlist.setSaved(false);
                    collaborativePlaylistsAdapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), "Removed from your library", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error removing playlist: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to remove playlist", Toast.LENGTH_SHORT).show();
            });
        } else {
            // Add to saved playlists
            Map<String, Object> savedPlaylist = new HashMap<>();
            savedPlaylist.put("playlistId", playlistId);
            savedPlaylist.put("savedAt", System.currentTimeMillis());
            savedPlaylist.put("creatorId", playlist.getCreatorId());
            savedPlaylist.put("name", playlist.getName());

            userSavedPlaylistRef.set(savedPlaylist)
                .addOnSuccessListener(aVoid -> {
                    playlist.setSaved(true);
                    collaborativePlaylistsAdapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), "Added to your library", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving playlist: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to save playlist", Toast.LENGTH_SHORT).show();
                });
        }
    }

    // Override the onPlaylistSaveClick method from the adapter
    @Override
    public void onPlaylistSaveClick(Playlist playlist) {
        toggleSaveCollaborativePlaylist(playlist);
    }
    
    // EnhancedRecommendationAdapter.OnTrackClickListener interface methods
    @Override
    public void onTrackClick(Track track, int position) {
        if (track == null || !isAdded()) return;
        
        Log.d(TAG, "Track clicked: " + track.getTitle());
        
        if (audioPlayerHelper != null) {
            // Get the current list of tracks from the adapter
            List<Track> currentTracks = recommendationAdapter.getCurrentTracks();
            
            // Set the playlist starting from the clicked track
            List<Track> playlistTracks = new ArrayList<>(currentTracks.subList(position, currentTracks.size()));
            playlistTracks.addAll(currentTracks.subList(0, position)); // Add earlier tracks at the end
            
            // Create a temporary playlist for these tracks
            Playlist tempPlaylist = new Playlist();
            tempPlaylist.setName("Current Recommendations");
            tempPlaylist.setTracks(playlistTracks);
            
            // Play the playlist starting from the selected track
            audioPlayerHelper.playTrackInPlaylist(tempPlaylist, track);
            
            Toast.makeText(requireContext(), 
                "Playing: " + track.getTitle(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onPlayButtonClick(Track track, int position) {
        onTrackClick(track, position); // Reuse the same logic
    }
    
    @Override
    public void onSaveButtonClick(Track track, int position) {
        if (track != null) {
            // Save track for later or add to playlist
            Toast.makeText(requireContext(), "Saved for later: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // PlaylistAdapter.OnPlaylistClickListener interface methods
    @Override
    public void onPlaylistClick(Playlist playlist) {
        if (playlist == null) return;
        
        Log.d(TAG, "Playlist clicked: " + playlist.getName());
        
        // First, get the tracks for this playlist
        playlistManager.getPlaylistTracks(playlist.getPlaylistId(), new TracksCallback() {
                @Override
            public void onSuccess(List<Track> tracks) {
                if (!isAdded()) return;
                
                if (tracks != null && !tracks.isEmpty()) {
                    Log.d(TAG, "Loaded " + tracks.size() + " tracks for playlist: " + playlist.getName());
                    
                    // Update the playlist's tracks
                    playlist.setTracks(tracks);
                    
                    // Start playback using AudioPlayerHelper
                    if (audioPlayerHelper != null) {
                        audioPlayerHelper.playPlaylist(playlist);
                        
                        Toast.makeText(requireContext(), 
                            "Playing playlist: " + playlist.getName(), 
                            Toast.LENGTH_SHORT).show();
                        }
                        
                    // Navigate to playlist detail
                    Bundle args = new Bundle();
                    args.putString("playlistId", playlist.getPlaylistId());
                    args.putString("playlistName", playlist.getName());
                    args.putString("playlistCoverUrl", playlist.getCoverImageUrl());
                    Navigation.findNavController(requireView())
                        .navigate(R.id.action_navigation_home_to_playlistDetailFragment, args);
                } else {
                    Toast.makeText(requireContext(), 
                        "This playlist has no tracks", 
                        Toast.LENGTH_SHORT).show();
                    }
                }
                
                @Override
                public void onError(String message) {
                if (!isAdded()) return;
                Log.e(TAG, "Error loading playlist tracks: " + message);
                Toast.makeText(requireContext(), 
                    "Could not load playlist tracks: " + message, 
                    Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void showPlaylistDetail(Playlist playlist, List<Track> tracks) {
        // Navigate to playlist detail, passing both playlist and tracks
        Bundle args = new Bundle();
        args.putString("playlistId", playlist.getPlaylistId());
        args.putString("playlistName", playlist.getName());
        args.putString("playlistCoverUrl", playlist.getCoverImageUrl());
        args.putString("playlistSource", playlist.getSource());
        args.putParcelableArrayList("tracks", new ArrayList<>(tracks));
        Navigation.findNavController(requireView()).navigate(R.id.action_navigation_home_to_playlistDetailFragment, args);
    }
    
    // RecommendationEngine.RecommendationListener interface methods
    @Override
    public void onRecommendationsUpdated() {
        // Reload recommendations when recommendation engine notifies of updates
        loadRecommendations();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister from recommendation engine
        recommendationEngine.removeListener(this);
        // Remove refresh callbacks
        if (refreshHandler != null) {
            refreshHandler.removeCallbacksAndMessages(null);
        }
    }

    private void setupMoodChips() {
        if (chipGroupMoods == null) {
            Log.e(TAG, "ChipGroup is null. Make sure it's properly initialized.");
            return;
        }
        
        chipGroupMoods.removeAllViews();
        
        for (String mood : MoodBasedRecommendationManager.AVAILABLE_MOODS) {
            Chip chip = new Chip(requireContext());
            chip.setText(mood.substring(0, 1).toUpperCase() + mood.substring(1));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            
            // Set emoji based on mood
            String emoji = getMoodEmoji(mood);
            if (emoji != null) {
                chip.setText(chip.getText() + " " + emoji);
            }
            
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentMood = mood;
                    loadRecommendations();
                }
            });
            
            chipGroupMoods.addView(chip);
            
            // Check the default mood
            if (mood.equals(currentMood)) {
                chip.setChecked(true);
            }
        }
    }

    private void initializeSearchServices() {
        // Initialize Spotify API service
        String spotifyBaseUrl = SpotifyConfig.API_BASE_URL;
        if (!spotifyBaseUrl.endsWith("/")) {
            spotifyBaseUrl += "/";
        }
        
        Retrofit spotifyRetrofit = new Retrofit.Builder()
                .baseUrl(spotifyBaseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        spotifyApiService = spotifyRetrofit.create(SpotifyApiService.class);

        // Initialize Deezer API service
        String deezerBaseUrl = "https://api.deezer.com/";
        Retrofit deezerRetrofit = new Retrofit.Builder()
                .baseUrl(deezerBaseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        deezerApiService = deezerRetrofit.create(DeezerApiService.class);

        // Initialize MusicSearchService
        musicSearchService = new MusicSearchService(spotifyApiService, deezerApiService);
    }

    private void loadTopTrending() {
        showLoadingIndicator(rvTopTrending);
        
        // Call Deezer API to get top tracks
        deezerHelper.getTopTracks(new DeezerHelper.TopTracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (!isAdded()) return;
                
                hideLoadingIndicator(rvTopTrending);
                if (tracks.isEmpty()) {
                    showEmptyState(rvTopTrending, "No trending tracks available");
                } else {
                    topTrendingAdapter.setTracks(tracks);
                }
            }
            
            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                
                hideLoadingIndicator(rvTopTrending);
                showErrorState(rvTopTrending, "Failed to load trending tracks: " + message);
            }
        });
    }

    private void setupRefreshHandler() {
        refreshHandler = new Handler(Looper.getMainLooper());
        // Schedule periodic refresh
        refreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    loadTopTrending();
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL);
                }
            }
        }, REFRESH_INTERVAL);
    }
} 