package my.edu.utar.bananamusic.ui.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.TrackAdapter;
import my.edu.utar.bananamusic.adapters.SearchHistoryAdapter;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.ui.base.SafeFragment;
import my.edu.utar.bananamusic.utils.JioSaavnHelper;
import my.edu.utar.bananamusic.utils.PipedHelper;
import my.edu.utar.bananamusic.utils.RecyclerViewSafety;
import my.edu.utar.bananamusic.utils.SafeRecyclerViewLayoutManager;
import my.edu.utar.bananamusic.utils.SpotifyHelper;
import my.edu.utar.bananamusic.utils.SpotifySearchHelper;
import my.edu.utar.bananamusic.ui.search.SpotifySearchViewModel;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.SpotifyDeviceManager;
import my.edu.utar.bananamusic.utils.DeezerHelper;
import my.edu.utar.bananamusic.utils.UnifiedMusicService;

import org.json.JSONArray;
import org.json.JSONObject;

import com.bumptech.glide.Glide;

import my.edu.utar.bananamusic.models.DeezerSearchResponse;
import my.edu.utar.bananamusic.models.SearchTrack;
import my.edu.utar.bananamusic.models.SpotifySearchResponse;
import my.edu.utar.bananamusic.models.SpotifyToken;
import my.edu.utar.bananamusic.network.DeezerApiService;
import my.edu.utar.bananamusic.network.SpotifyApiService;
import my.edu.utar.bananamusic.network.SpotifyAuthService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.jakewharton.rxbinding4.widget.RxTextView;

import my.edu.utar.bananamusic.services.MusicSearchService;

/**
 * Fragment for searching music across different sources
 */
public class SearchFragment extends SafeFragment implements TrackAdapter.OnTrackClickListener {
    private static final String TAG = "SearchFragment";
    private static final long SEARCH_DEBOUNCE_DELAY = 300; // milliseconds

    // UI Components
    private EditText searchEditText;
    private ProgressBar progressBar;
    private TextView noResultsText;
    private RecyclerView searchResultsRecyclerView;
    private TrackAdapter searchResultsAdapter;
    private View shimmerLayout;
    private RecyclerView searchHistoryRecyclerView;
    private View searchHistoryContainer;
    private TextView clearHistoryText;
    private ImageView voiceSearchButton;
    private TextView recentSearchesHeader;
    private ImageView clearSearchButton;
    private NestedScrollView scrollView;
    private LinearLayout loadMoreProgress;
    private TextView searchResultsCount;
    private View trackLoadingOverlay;
    private TextView trackLoadingText;

    // For recent searches
    private static final int MAX_RECENT_SEARCHES = 10;
    private static final String PREF_SEARCH_HISTORY = "search_history";
    private List<String> recentSearches = new ArrayList<>();
    private SearchHistoryAdapter searchHistoryAdapter;

    // Helpers
    private SpotifySearchHelper spotifySearchHelper;
    private Handler mainHandler;
    private Runnable searchRunnable;
    
    // Helper classes for different audio sources
    private SpotifyHelper spotifyHelper;
    private PipedHelper pipedHelper;
    private JioSaavnHelper jioSaavnHelper;
    private DeezerHelper deezerHelper;
    
    // Add a background executor for search operations
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();

    // Current active filter
    private String currentFilter = "all";
    // Current source filter
    private String sourceFilter = "all";

    // ViewModel
    private SpotifySearchViewModel searchViewModel;

    // Chip references
    private Chip artistFilterChip;
    private Chip albumFilterChip;
    private Chip playlistFilterChip;
    private Chip songFilterChip;

    private AudioPlayerHelper audioPlayerHelper;

    private UnifiedMusicService unifiedMusicService;
    private SearchResultAdapter searchResultAdapter;

    // API Services
    private SpotifyAuthService spotifyAuthService;
    private SpotifyApiService spotifyApiService;
    private DeezerApiService deezerApiService;
    
    // Token
    private String spotifyToken;

    private MusicSearchService musicSearchService;

    public interface OnTrackSelectedListener {
        void onTrackSelected(Track track);
    }

    private OnTrackSelectedListener onTrackSelectedListener = new OnTrackSelectedListener() {
        @Override
        public void onTrackSelected(Track track) {
            if (track != null) {
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
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize API helpers
        spotifyHelper = SpotifyHelper.getInstance(requireContext());
        pipedHelper = PipedHelper.getInstance(requireContext());
        jioSaavnHelper = JioSaavnHelper.getInstance(requireContext());
        spotifySearchHelper = new SpotifySearchHelper(requireContext());
        deezerHelper = DeezerHelper.getInstance(requireContext());
        
        // Initialize audio player helper
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        audioPlayerHelper.setOnPlaybackChangedListener(new AudioPlayerHelper.OnPlaybackChangedListener() {
            @Override
            public void onTrackPlay(Track track) {
                // Update UI to show currently playing track
                if (isAdded()) {
                    mainHandler.post(() -> updateNowPlayingIndicator(track));
                }
            }

            @Override
            public void onTrackPause() {
                // No need to update UI for pause
            }

            @Override
            public void onTrackStopped() {
                // Clear the currently playing indicator when playback stops
                if (isAdded()) {
                    mainHandler.post(() -> updateNowPlayingIndicator(null));
                }
            }

            @Override
            public void onTrackChanged(Track track) {
                // Update UI when track changes
                if (isAdded()) {
                    mainHandler.post(() -> updateNowPlayingIndicator(track));
                }
            }

            @Override
            public void onPlaybackError(String errorMessage) {
                // Show error toast
                if (isAdded()) {
                    mainHandler.post(() -> Toast.makeText(requireContext(), 
                        getString(R.string.error_message, errorMessage), 
                        Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onBufferingStart() {
                // Show buffering indicator if needed
            }

            @Override
            public void onBufferingEnd() {
                // Hide buffering indicator if needed
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        // Update UI for track completion
                        updatePlaybackControls(false);
                        // Reset any search-specific UI elements if needed
                    });
                }
            }

            @Override
            public void onTrackComplete() {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        // Update UI for track completion
                        updatePlaybackControls(false);
                        // Reset any search-specific UI elements if needed
                    });
                }
            }

            @Override
            public void onLoadingStateChanged(boolean isLoading) {
                // Update loading state in UI if needed
            }

            @Override
            public void onBufferingUpdate(int percent) {
                // Update buffering progress if needed
            }

            @Override
            public void onPlaybackStateChanged(boolean isPlaying) {
                // Update play/pause button state if needed
            }
        });

        unifiedMusicService = UnifiedMusicService.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_search, container, false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize ViewModel
        searchViewModel = new ViewModelProvider(this).get(SpotifySearchViewModel.class);
        
        // Find views
        initViews(view);
        
        // Initialize search services
        initializeSearchServices();
        
        // Setup search listener
        setupSearchListener();
        
        // Setup adapters
        setupAdapters();
        
        // Setup observers
        setupObservers();
        
        // Load recent searches
        displayRecentSearches();
    }
    
    private void initViews(View view) {
        searchEditText = view.findViewById(R.id.edit_search);
        progressBar = view.findViewById(R.id.progress_bar);
        noResultsText = view.findViewById(R.id.text_empty_state);
        searchResultsRecyclerView = view.findViewById(R.id.recycler_search_results);
        clearSearchButton = view.findViewById(R.id.button_search);
        
        // Set initial visibility of clear button to GONE
        if (clearSearchButton != null) {
            clearSearchButton.setVisibility(View.GONE);
        }
        
        // Display instruction text initially
        if (noResultsText != null) {
            noResultsText.setText(R.string.search_instructions);
            noResultsText.setVisibility(View.VISIBLE);
        }
        
        // Setup RecyclerView with empty adapter - using JUST ONE adapter
        searchResultsAdapter = new TrackAdapter(requireContext(), new ArrayList<>(), (track, position) -> {
            // When a track is clicked, play it through MainActivity
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                activity.playTrack(track);
            }
        });
        
        if (searchResultsRecyclerView != null) {
            searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            searchResultsRecyclerView.setAdapter(searchResultsAdapter);
        }
    }

    private void setupFilterChips(View view) {
        // Since filter chips don't exist in our layout, skip this setup
    }

    private void setupAdapters() {
        // Setup search results adapter
        searchResultsAdapter = new TrackAdapter(requireContext(), new ArrayList<>(), new TrackAdapter.OnTrackClickListener() {
            @Override
            public void onTrackClick(Track track, int position) {
                playTrackAndShowMiniPlayer(track);
            }
        });

        if (searchResultsRecyclerView != null) {
            searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            searchResultsRecyclerView.setAdapter(searchResultsAdapter);
        }
        
        // Setup search history adapter
        searchHistoryAdapter = new SearchHistoryAdapter(new SearchHistoryAdapter.OnSearchHistoryClickListener() {
            @Override
            public void onSearchItemClick(String query) {
                searchEditText.setText(query);
                performSearch(query);
            }

            @Override
            public void onSearchItemDelete(String query) {
                // Remove item from search history
                recentSearches.remove(query);
                saveRecentSearches();
                searchHistoryAdapter.setSearchHistory(recentSearches);
                
                // Hide recycler view if empty
                if (recentSearches.isEmpty() && searchHistoryRecyclerView != null) {
                    searchHistoryRecyclerView.setVisibility(View.GONE);
                    if (recentSearchesHeader != null) {
                        recentSearchesHeader.setVisibility(View.GONE);
                    }
                    if (clearHistoryText != null) {
                        clearHistoryText.setVisibility(View.GONE);
                    }
                }
            }
        });
        
        // Set search history and attach to recycler view if it exists
        if (searchHistoryAdapter != null) {
            searchHistoryAdapter.setSearchHistory(recentSearches);
        }
        
        if (searchHistoryRecyclerView != null) {
            searchHistoryRecyclerView.setAdapter(searchHistoryAdapter);
            searchHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
    }

    private void setupListeners() {
        // Search input text change listener
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                
                // Toggle clear button visibility
                if (clearSearchButton != null) {
                    clearSearchButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }
                
                // Cancel any pending searches
                if (searchRunnable != null) {
                    mainHandler.removeCallbacks(searchRunnable);
                }
                
                // Clear results if query is empty
                if (query.isEmpty()) {
                    // Clear the search results
                    if (searchResultsAdapter != null) {
                        searchResultsAdapter.setTracks(new ArrayList<>());
                    }
                    
                    // Show instruction text
                    if (noResultsText != null) {
                        noResultsText.setText(R.string.search_instructions);
                        noResultsText.setVisibility(View.VISIBLE);
                    }
                    
                    // Hide the recycler view
                    if (searchResultsRecyclerView != null) {
                        searchResultsRecyclerView.setVisibility(View.GONE);
                    }
                    
                    return;
                }
                
                // Debounce search requests
                searchRunnable = () -> {
                    // Show user that search is in progress
                    showLoadingIndicator();
                    
                    Log.d(TAG, "Searching for: " + query);
                    
                    // Create simple mock results for testing
                    // In a real implementation, this would be a network call to a music API
                    createMockResultsWithDelay(query, 500);
                };
                
                mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_DELAY);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Clear search button
        if (clearSearchButton != null) {
            clearSearchButton.setOnClickListener(v -> {
                searchEditText.setText("");
                
                // Clear search results
                if (searchResultsAdapter != null) {
                    searchResultsAdapter.setTracks(new ArrayList<>());
                }
                
                // Show instruction text
                if (noResultsText != null) {
                    noResultsText.setText(R.string.search_instructions);
                    noResultsText.setVisibility(View.VISIBLE);
                }
                
                // Hide recycler view
                if (searchResultsRecyclerView != null) {
                    searchResultsRecyclerView.setVisibility(View.GONE);
                }
                
                hideKeyboard();
            });
        }
        
        // Voice search button
        if (voiceSearchButton != null) {
            voiceSearchButton.setOnClickListener(v -> {
                startVoiceSearch();
            });
        }
        
        // Scroll listener for pagination - skip if scrollView is null
        if (scrollView != null) {
            scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) 
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY > 0 && scrollY == (v.getChildAt(0).getMeasuredHeight() - v.getMeasuredHeight())) {
                        if (searchViewModel.hasMoreResults() && 
                            loadMoreProgress != null && loadMoreProgress.getVisibility() != View.VISIBLE && 
                            !searchEditText.getText().toString().trim().isEmpty()) {
                            loadMoreProgress.setVisibility(View.VISIBLE);
                            searchViewModel.loadMoreResults();
                        }
                    }
                });
        }
    }

    private void setupObservers() {
        // Observe search results
        searchViewModel.getSearchResults().observe(getViewLifecycleOwner(), tracks -> {
            if (searchResultsAdapter != null) {
                searchResultsAdapter.setTracks(tracks);
                
                // Add animation to the RecyclerView if not already set
                if (searchResultsRecyclerView != null && searchResultsRecyclerView.getLayoutAnimation() == null) {
                    LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(
                            requireContext(), R.anim.layout_animation_fall_down);
                    searchResultsRecyclerView.setLayoutAnimation(controller);
                }
                
                // Run the layout animation
                if (searchResultsRecyclerView != null) {
                    searchResultsRecyclerView.scheduleLayoutAnimation();
                }
            }
            
            // Update UI visibility
            boolean hasResults = tracks != null && !tracks.isEmpty();
            boolean isSearching = !searchEditText.getText().toString().trim().isEmpty();
            
            // Update search results visibility
            if (searchResultsRecyclerView != null) {
                searchResultsRecyclerView.setVisibility(hasResults ? View.VISIBLE : View.GONE);
            }
            if (noResultsText != null) {
                noResultsText.setVisibility(!hasResults && isSearching ? View.VISIBLE : View.GONE);
            }
            
            // Update search history visibility - only show when not searching and not showing results
            boolean showHistory = !hasResults && !isSearching && searchHistoryAdapter != null && searchHistoryAdapter.getItemCount() > 0;
            if (recentSearchesHeader != null) {
                recentSearchesHeader.setVisibility(showHistory ? View.VISIBLE : View.GONE);
            }
            if (searchHistoryRecyclerView != null) {
                searchHistoryRecyclerView.setVisibility(showHistory ? View.VISIBLE : View.GONE);
            }
            if (clearHistoryText != null) {
                clearHistoryText.setVisibility(showHistory ? View.VISIBLE : View.GONE);
            }
                
            // Update results count and show/hide clear button
            /*
            View clearResultsButton = getView().findViewById(R.id.button_clear_results);
            if (hasResults) {
                if (searchResultsCount != null) {
                    int total = searchViewModel.getTotalResults();
                    // Format and display results count
                    searchResultsCount.setText(String.format("Showing %d of %d results", 
                        Math.min(tracks.size(), total), total));
                    searchResultsCount.setVisibility(View.VISIBLE);
                }
                
                // Show clear button
                if (clearResultsButton != null) {
                    clearResultsButton.setVisibility(View.VISIBLE);
                }
            } else {
                if (searchResultsCount != null) {
                    searchResultsCount.setVisibility(View.GONE);
                }
                
                // Hide clear button
                if (clearResultsButton != null) {
                    clearResultsButton.setVisibility(View.GONE);
                }
            }
            */
            
            // Hide load more progress
            if (loadMoreProgress != null) {
                loadMoreProgress.setVisibility(View.GONE);
            }
        });
        
        // Observe loading state
        searchViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (progressBar != null) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
            
            // If loading, show shimmer effect
            if (isLoading) {
                if (shimmerLayout != null) {
                    shimmerLayout.setVisibility(View.VISIBLE);
                }
                if (searchResultsRecyclerView != null) {
                    searchResultsRecyclerView.setVisibility(View.GONE);
                }
            } else {
                if (shimmerLayout != null) {
                    shimmerLayout.setVisibility(View.GONE);
                }
            }
        });
        
        // Observe error message
        searchViewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                if (noResultsText != null) {
                    noResultsText.setText(message);
                    noResultsText.setVisibility(View.VISIBLE);
                }
            } else {
                if (noResultsText != null) {
                    noResultsText.setVisibility(View.GONE);
                }
            }
        });
        
        // Observe recent searches
        searchViewModel.getRecentSearches().observe(getViewLifecycleOwner(), searches -> {
            if (searchHistoryAdapter != null) {
                searchHistoryAdapter.updateData(searches);
            }
            
            // Show/hide recent searches section
            boolean hasRecentSearches = searches != null && !searches.isEmpty();
            boolean hasResults = searchResultsAdapter != null && searchResultsAdapter.getItemCount() > 0;
            boolean isSearching = !searchEditText.getText().toString().trim().isEmpty();
            
            // Only show history when not searching and not showing results
            boolean showRecentSearches = hasRecentSearches && !isSearching && !hasResults;
            
            if (recentSearchesHeader != null) {
                recentSearchesHeader.setVisibility(showRecentSearches ? View.VISIBLE : View.GONE);
            }
            if (searchHistoryRecyclerView != null) {
                searchHistoryRecyclerView.setVisibility(showRecentSearches ? View.VISIBLE : View.GONE);
            }
            if (clearHistoryText != null) {
                clearHistoryText.setVisibility(showRecentSearches ? View.VISIBLE : View.GONE);
            }
        });
    }

    private String getActiveFilter() {
        // Since filter chips don't exist, always return "all"
        return "all";
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        showLoadingIndicator();
        unifiedMusicService.searchTracks(query, new UnifiedMusicService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                hideLoadingIndicator();
                updateSearchResults(tracks);
            }

            @Override
            public void onError(String message) {
                hideLoadingIndicator();
                showError("Search failed: " + message);
            }
        });
    }

    private void updateSearchResults(List<Track> tracks) {
        hideLoadingIndicator();
        
        if (tracks.isEmpty()) {
            // Show no results message
            if (noResultsText != null) {
                noResultsText.setText(R.string.no_search_results);
                noResultsText.setVisibility(View.VISIBLE);
            }
            
            // Hide recycler view
            if (searchResultsRecyclerView != null) {
                searchResultsRecyclerView.setVisibility(View.GONE);
            }
        } else {
            // Hide no results message
            if (noResultsText != null) {
                noResultsText.setVisibility(View.GONE);
            }
            
            // Show recycler view with results
            if (searchResultsRecyclerView != null) {
                searchResultsRecyclerView.setVisibility(View.VISIBLE);
            }
            
            // Update adapter with results
            if (searchResultsAdapter != null) {
                searchResultsAdapter.setTracks(tracks);
                // Apply layout animation to recycler view
                if (searchResultsRecyclerView != null) {
                    LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(
                            requireContext(), R.anim.layout_animation_fall_down);
                    searchResultsRecyclerView.setLayoutAnimation(controller);
                    searchResultsRecyclerView.scheduleLayoutAnimation();
                }
            }
        }
    }

    private void onTrackSelected(Track track) {
        if (track == null) return;
        
        showLoadingIndicator();
        unifiedMusicService.getTrackPreview(track, new UnifiedMusicService.TrackPreviewCallback() {
            @Override
            public void onSuccess(String previewUrl) {
                hideLoadingIndicator();
                if (previewUrl != null && !previewUrl.isEmpty()) {
                    // Update track with preview URL
                    track.setPreviewUrl(previewUrl);
                    // Play through MainActivity
                    if (getActivity() instanceof MainActivity) {
                        MainActivity activity = (MainActivity) getActivity();
                        activity.playTrack(track);
                    }
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

    private void displayRecentSearches() {
        // Nothing to do here, ViewModel observer will handle this
    }

    private void updateNowPlayingIndicator(Track track) {
        if (searchResultsAdapter != null && track != null) {
            searchResultsAdapter.setCurrentlyPlayingTrack(track);
        }
    }
    
    @Override
    public void onTrackClick(Track track, int position) {
        Log.d(TAG, "Track clicked: " + track.getTitle());
        
        // Add to recent searches if from search results
        if (searchEditText != null && !searchEditText.getText().toString().trim().isEmpty()) {
            addToRecentSearches(searchEditText.getText().toString().trim());
            
            // Also save to ViewModel for cross-fragment persistence
            if (searchViewModel != null) {
                searchViewModel.addRecentSearch(searchEditText.getText().toString().trim());
            }
        }
        
        // Play track and show mini player
        playTrackAndShowMiniPlayer(track);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && searchEditText != null) {
            imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
        }
    }

    private void startVoiceSearch() {
        try {
            // Create an intent to recognize speech
            android.content.Intent intent = new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_prompt));
            startActivityForResult(intent, 1);
        } catch (Exception e) {
            // If speech recognition is not available, show a toast
            Log.e(TAG, "Voice search not available: " + e.getMessage());
            Toast.makeText(requireContext(), R.string.voice_search_not_available, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            // Get the result
            ArrayList<String> result = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String query = result.get(0);
                // Set the query in the search box
                if (searchEditText != null) {
                    searchEditText.setText(query);
                    // Perform the search
                    performSearch(query);
                }
            }
        }
    }

    // Save search query to history for future reference
    private void saveSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        
        // Check if query already exists in history
        if (recentSearches.contains(query)) {
            // Remove it (we'll add it to the top)
            recentSearches.remove(query);
        }
        
        // Add to the top of the list
        recentSearches.add(0, query);
        
        // Trim list if needed
        while (recentSearches.size() > MAX_RECENT_SEARCHES) {
            recentSearches.remove(recentSearches.size() - 1);
        }
        
        // Save to shared preferences
        saveRecentSearches();
        
        // Update adapter
        if (searchHistoryAdapter != null) {
            searchHistoryAdapter.notifyDataSetChanged();
        }
        
        // Make search history visible
        if (searchHistoryContainer != null && searchHistoryContainer.getVisibility() != View.VISIBLE) {
            searchHistoryContainer.setVisibility(View.VISIBLE);
        }
        if (searchHistoryRecyclerView != null && searchHistoryRecyclerView.getVisibility() != View.VISIBLE) {
            searchHistoryRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void saveRecentSearches() {
        // Get shared preferences editor
        android.content.SharedPreferences.Editor editor = requireContext().getSharedPreferences(PREF_SEARCH_HISTORY, Context.MODE_PRIVATE).edit();
        
        // Clear all existing entries
        editor.clear();
        
        // Save up to MAX_RECENT_SEARCHES items
        for (int i = 0; i < recentSearches.size() && i < MAX_RECENT_SEARCHES; i++) {
            editor.putString("search_" + i, recentSearches.get(i));
        }
        
        // Apply changes
        editor.apply();
    }

    @Override
    public void onDestroyView() {
        // Clear adapter before destroying view
        if (searchResultsAdapter != null && searchResultsRecyclerView != null) {
            searchResultsAdapter.setTracks(new ArrayList<>());
            searchResultsRecyclerView.setAdapter(null);
        }

        if (searchRunnable != null) {
            mainHandler.removeCallbacks(searchRunnable);
        }

        // Shutdown executor service
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
            try {
                if (!searchExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    searchExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                searchExecutor.shutdownNow();
            }
        }

        super.onDestroyView();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop any preview playback
        searchViewModel.stopPreview();
    }

    /**
     * Setup the search history recyclerview
     */
    private void setupSearchHistory() {
        // Initialize search history adapter if needed
        if (searchHistoryAdapter == null) {
            searchHistoryAdapter = new SearchHistoryAdapter(new SearchHistoryAdapter.OnSearchHistoryClickListener() {
                @Override
                public void onSearchItemClick(String query) {
                    searchEditText.setText(query);
                    performSearch(query);
                }

                @Override
                public void onSearchItemDelete(String query) {
                    // Remove item from search history
                    recentSearches.remove(query);
                    saveRecentSearches();
                    searchHistoryAdapter.setSearchHistory(recentSearches);
                }
            });
        }

        // Set adapter and layout manager
        if (searchHistoryRecyclerView != null) {
            searchHistoryRecyclerView.setAdapter(searchHistoryAdapter);
            searchHistoryRecyclerView.setLayoutManager(
                    new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        }
    }

    /**
     * Show the initial state of the search screen
     */
    private void showInitialState() {
        // Clear search results
        searchViewModel.clearSearch();
        
        // Hide search results and related UI elements
        if (searchResultsRecyclerView != null) {
            searchResultsRecyclerView.setVisibility(View.GONE);
        }
        if (noResultsText != null) {
            noResultsText.setVisibility(View.GONE);
        }
        
        // Hide search results count and clear button
        if (searchResultsCount != null) {
            searchResultsCount.setVisibility(View.GONE);
        }
        
        /*
        View clearResultsButton = getView().findViewById(R.id.button_clear_results);
        if (clearResultsButton != null) {
            clearResultsButton.setVisibility(View.GONE);
        }
        */
        
        // Show recent searches header if there are recent searches
        boolean hasRecentSearches = recentSearches != null && !recentSearches.isEmpty();
        if (recentSearchesHeader != null) {
            recentSearchesHeader.setVisibility(hasRecentSearches ? View.VISIBLE : View.GONE);
        }
        if (searchHistoryRecyclerView != null) {
            searchHistoryRecyclerView.setVisibility(hasRecentSearches ? View.VISIBLE : View.GONE);
        }
        if (clearHistoryText != null) {
            clearHistoryText.setVisibility(hasRecentSearches ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Show track loading overlay when track is loading
     * @param trackTitle Title of the track being loaded
     */
    private void showTrackLoadingOverlay(String trackTitle) {
        if (!isAdded()) return;
        
        // Show loading indicator
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        
        // Show loading toast
        String loadingMessage = getString(R.string.loading_track, trackTitle);
        Toast.makeText(requireContext(), loadingMessage, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Hide loading overlay
     */
    private void hideTrackLoadingOverlay() {
        if (!isAdded()) return;
        
        // Hide loading indicator
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
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

    private void showEmptyState() {
        if (noResultsText != null) {
            noResultsText.setVisibility(View.VISIBLE);
            if (searchResultsRecyclerView != null) {
                searchResultsRecyclerView.setVisibility(View.GONE);
            }
        }
    }

    private void hideEmptyState() {
        if (noResultsText != null) {
            noResultsText.setVisibility(View.GONE);
            if (searchResultsRecyclerView != null) {
                searchResultsRecyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void playAudio(String previewUrl, Track track) {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            // Update the track's preview URL before playing
            track.setPreviewUrl(previewUrl);
            // Set all required fields for the mini player
            track.setPlayable(true);
            track.setDurationMs(track.getDuration());
            track.setImageUrl(track.getAlbumArtUrl());
            track.setAlbumArt(track.getAlbumArtUrl());
            track.setType("track");
            track.setSource("search");
            // Use MainActivity's playTrack method which handles miniplayer visibility
            activity.playTrack(track);
            // Explicitly show the mini player
            activity.showMiniPlayer();
            // Update playback controls
            updatePlaybackControls(true);
        }
    }

    private class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {
        private List<Track> tracks;
        private OnTrackSelectedListener listener;

        public SearchResultAdapter(List<Track> tracks, OnTrackSelectedListener listener) {
            this.tracks = tracks;
            this.listener = listener;
        }

        public void updateTracks(List<Track> newTracks) {
            this.tracks = newTracks;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_result, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Track track = tracks.get(position);
            holder.bind(track, listener);
        }

        @Override
        public int getItemCount() {
            return tracks.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private TextView titleView;
            private TextView artistView;
            // We'll skip the ImageView since it's causing issues

            ViewHolder(View itemView) {
                super(itemView);
                // Use only the guaranteed IDs that exist in all layouts
                titleView = itemView.findViewById(R.id.text_track_title);
                artistView = itemView.findViewById(R.id.text_track_artist);
            }

            void bind(final Track track, final OnTrackSelectedListener listener) {
                titleView.setText(track.getTitle());
                artistView.setText(track.getArtist());

                // Skip image loading since we're not using an ImageView
                /*
                if (track.getAlbumArtUrl() != null) {
                    Glide.with(itemView.getContext())
                            .load(track.getAlbumArtUrl())
                            .placeholder(R.drawable.placeholder_album)
                            .into(imageView);
                }
                */

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onTrackSelected(track);
                    }
                });
            }
        }
    }

    /**
     * Add a query to recent searches
     * @param query The search query to add
     */
    private void addToRecentSearches(String query) {
        // Don't save empty queries
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        
        query = query.trim();
        
        // Remove if already exists (to reorder)
        recentSearches.remove(query);
        
        // Add to beginning
        recentSearches.add(0, query);
        
        // Limit size
        if (recentSearches.size() > MAX_RECENT_SEARCHES) {
            recentSearches = recentSearches.subList(0, MAX_RECENT_SEARCHES);
        }
        
        // Save to preferences
        saveRecentSearches();
        
        // Update adapter if exists
        if (searchHistoryAdapter != null) {
            searchHistoryAdapter.updateItems(recentSearches);
        }
    }

    /**
     * Create mock search results for testing with a delay to simulate network call
     */
    private void createMockResultsWithDelay(String query, long delayMs) {
        // Simulate network delay
        mainHandler.postDelayed(() -> {
            List<Track> results = createMockResults(query);
            updateSearchResults(results);
            hideLoadingIndicator();
        }, delayMs);
    }

    /**
     * Create mock search results for testing
     */
    private List<Track> createMockResults(String query) {
        List<Track> results = new ArrayList<>();
        
        // Add 5 mock results instead of just 3
        for (int i = 1; i <= 5; i++) {
            Track track = new Track();
            track.setId(String.valueOf(i));
            
            // Alternate between different title formats
            if (i % 2 == 0) {
                track.setTitle(query + " - Song " + i);
            } else {
                track.setTitle("Best of " + query + " #" + i);
            }
            
            track.setArtist("Artist " + i);
            track.setAlbum("Album " + (i % 3 + 1));
            track.setDuration(180000 + (i * 15000)); // Different durations
            track.setAlbumArtUrl("https://picsum.photos/200?random=" + i); // Different images
            
            // Make every other track playable
            track.setPlayable(i % 2 == 0);
            
            results.add(track);
        }
        
        return results;
    }

    private void initializeSearchServices() {
        // Initialize Spotify API service
        Retrofit spotifyRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.spotify.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        spotifyApiService = spotifyRetrofit.create(SpotifyApiService.class);

        // Initialize Deezer API service
        Retrofit deezerRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.deezer.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        deezerApiService = deezerRetrofit.create(DeezerApiService.class);

        // Initialize MusicSearchService
        musicSearchService = new MusicSearchService(spotifyApiService, deezerApiService);
    }

    private void setupSearchListener() {
        if (searchEditText != null) {
            RxTextView.textChanges(searchEditText)
                    .debounce(300, TimeUnit.MILLISECONDS)
                    .filter(text -> text.length() >= 2)
                    .subscribe(text -> {
                        requireActivity().runOnUiThread(() -> {
                            showLoadingIndicator();
                            hideEmptyState();
                        });

                        musicSearchService.searchTracks(text.toString(), new MusicSearchService.SearchCallback() {
                            @Override
                            public void onSearchComplete(List<Track> tracks) {
                                requireActivity().runOnUiThread(() -> {
                                    hideLoadingIndicator();
                                    if (tracks.isEmpty()) {
                                        showEmptyState();
                                    } else {
                                        if (searchResultsAdapter != null) {
                                            searchResultsAdapter.setTracks(tracks);
                                            searchResultsRecyclerView.setVisibility(View.VISIBLE);
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onSearchError(String error) {
                                requireActivity().runOnUiThread(() -> {
                                    hideLoadingIndicator();
                                    showError(error);
                                    showEmptyState();
                                });
                            }
                        });
                    });
        }
    }

    private void updatePlaybackControls(boolean isPlaying) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                // Update adapter if it exists
                if (searchResultsAdapter != null) {
                    searchResultsAdapter.updatePlayingState(isPlaying);
                }
                
                // Hide loading overlay if it was showing
                hideTrackLoadingOverlay();
            });
        }
    }

    private void playTrackAndShowMiniPlayer(Track track) {
        if (track == null) return;

        // Show loading state
        showTrackLoadingOverlay(track.getTitle());

        // Get the MainActivity instance
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();

            // Make sure the track has all necessary information
            if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
                // If preview URL is missing, try to get it through UnifiedMusicService
                unifiedMusicService.getTrackPreview(track, new UnifiedMusicService.TrackPreviewCallback() {
                    @Override
                    public void onSuccess(String previewUrl) {
                        hideTrackLoadingOverlay();
                        if (previewUrl != null && !previewUrl.isEmpty()) {
                            // Set all required fields for the mini player
                            track.setPreviewUrl(previewUrl);
                            track.setPlayable(true);
                            track.setDurationMs(track.getDuration());
                            track.setImageUrl(track.getAlbumArtUrl());
                            track.setAlbumArt(track.getAlbumArtUrl());
                            track.setType("track");
                            track.setSource("search");
                            
                            // Play the track through MainActivity
                            activity.playTrack(track);
                            // Show mini player
                            activity.showMiniPlayer();
                            // Update playback controls
                            updatePlaybackControls(true);
                        } else {
                            showError("No preview available for this track");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        hideTrackLoadingOverlay();
                        showError("Failed to play track: " + message);
                    }
                });
            } else {
                // If we already have the preview URL, play directly
                hideTrackLoadingOverlay();
                
                // Set all required fields for the mini player
                track.setPlayable(true);
                track.setDurationMs(track.getDuration());
                track.setImageUrl(track.getAlbumArtUrl());
                track.setAlbumArt(track.getAlbumArtUrl());
                track.setType("track");
                track.setSource("search");
                
                activity.playTrack(track);
                activity.showMiniPlayer();
                updatePlaybackControls(true);
            }
        }
    }
}