package my.edu.utar.bananamusic.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.TrackAdapter;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.services.AIRecommendationService;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;

/**
 * Fragment that displays AI-powered music recommendations with feedback options
 */
public class AIRecommendationFragment extends Fragment implements AIRecommendationService.RecommendationListener {
    private static final String TAG = "AIRecommendFragment";
    
    // UI components
    private RecyclerView recyclerViewRecommendations;
    private ProgressBar progressBar;
    private TextView textViewMessage;
    private MaterialCardView cardFeedback;
    private TextView tvTrackInfo;
    private Button btnThumbsUp, btnThumbsDown, btnDismiss;
    private FloatingActionButton fabRefresh;
    
    // Adapters & data
    private TrackAdapter trackAdapter;
    private List<Track> recommendedTracks = new ArrayList<>();
    
    // Services
    private AIRecommendationService recommendationService;
    private AudioPlayerHelper audioPlayerHelper;
    
    // State
    private Track currentFeedbackTrack;
    private boolean isInitialLoad = true;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recommendationService = AIRecommendationService.getInstance(requireContext());
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_recommendation, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize UI components
        recyclerViewRecommendations = view.findViewById(R.id.recyclerViewRecommendations);
        progressBar = view.findViewById(R.id.progressBar);
        textViewMessage = view.findViewById(R.id.textViewMessage);
        cardFeedback = view.findViewById(R.id.cardFeedback);
        tvTrackInfo = view.findViewById(R.id.tvTrackInfo);
        btnThumbsUp = view.findViewById(R.id.btnThumbsUp);
        btnThumbsDown = view.findViewById(R.id.btnThumbsDown);
        btnDismiss = view.findViewById(R.id.btnDismiss);
        fabRefresh = view.findViewById(R.id.fabRefresh);
        
        // Setup RecyclerView
        recyclerViewRecommendations.setLayoutManager(new LinearLayoutManager(requireContext()));
        trackAdapter = new TrackAdapter(requireContext(), recommendedTracks, (track, position) -> {
            // Handle track click
            Toast.makeText(requireContext(), "Playing " + track.getTitle(), Toast.LENGTH_SHORT).show();
            
            // Play the track
            AudioPlayerHelper playerHelper = AudioPlayerHelper.getInstance(requireContext());
            playerHelper.playTrack(track);
            
            // Show feedback card for this track
            showFeedbackCard(track);
        });
        recyclerViewRecommendations.setAdapter(trackAdapter);
        
        // Setup click listeners
        btnThumbsUp.setOnClickListener(v -> provideFeedback(true));
        btnThumbsDown.setOnClickListener(v -> provideFeedback(false));
        btnDismiss.setOnClickListener(v -> hideFeedbackCard());
        
        fabRefresh.setOnClickListener(v -> {
            loadRecommendations(true);
        });
        
        // Hide feedback card initially
        cardFeedback.setVisibility(View.GONE);
        
        // Load recommendations
        loadRecommendations(false);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        // Register as listener for recommendation updates
        recommendationService.addRecommendationListener(this);
    }
    
    @Override
    public void onStop() {
        super.onStop();
        // Unregister listener
        recommendationService.removeRecommendationListener(this);
    }
    
    /**
     * Load AI-powered recommendations
     * @param forceRefresh Whether to force a refresh (bypassing cache)
     */
    private void loadRecommendations(boolean forceRefresh) {
        // Show loading UI
        progressBar.setVisibility(View.VISIBLE);
        textViewMessage.setVisibility(View.GONE);
        recyclerViewRecommendations.setVisibility(View.GONE);
        hideFeedbackCard();
        
        // Request recommendations (10 tracks)
        recommendationService.getPersonalizedRecommendations(10, new AIRecommendationService.RecommendationCallback() {
            @Override
            public void onRecommendationsReady(List<Track> recommendations) {
                if (isAdded()) {
                    if (recommendations.isEmpty()) {
                        showEmptyState();
                    } else {
                        updateRecommendations(recommendations);
                    }
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                if (isAdded()) {
                    // Show error message
                    progressBar.setVisibility(View.GONE);
                    textViewMessage.setText("Error loading recommendations: " + errorMessage);
                    textViewMessage.setVisibility(View.VISIBLE);
                    
                    // Show any existing recommendations if we have them
                    if (!recommendedTracks.isEmpty()) {
                        recyclerViewRecommendations.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
    }
    
    /**
     * Update UI with new recommendations
     */
    private void updateRecommendations(List<Track> recommendations) {
        // Update data
        recommendedTracks.clear();
        recommendedTracks.addAll(recommendations);
        
        // Update UI
        progressBar.setVisibility(View.GONE);
        textViewMessage.setVisibility(View.GONE);
        recyclerViewRecommendations.setVisibility(View.VISIBLE);
        trackAdapter.notifyDataSetChanged();
        
        // Show a toast on initial load
        if (isInitialLoad) {
            Toast.makeText(requireContext(), 
                "Recommendations based on your listening history", 
                Toast.LENGTH_SHORT).show();
            isInitialLoad = false;
        }
        
        // Automatically request feedback for a random track if we have more than 3
        if (recommendations.size() > 3 && Math.random() < 0.3) { // 30% chance
            int randomIndex = new Random().nextInt(recommendations.size());
            showFeedbackCard(recommendations.get(randomIndex));
        }
    }
    
    /**
     * Show empty state when no recommendations are available
     */
    private void showEmptyState() {
        progressBar.setVisibility(View.GONE);
        textViewMessage.setText("No recommendations available yet. Try playing some tracks first!");
        textViewMessage.setVisibility(View.VISIBLE);
        recyclerViewRecommendations.setVisibility(View.GONE);
    }
    
    /**
     * Show feedback card for a specific track
     */
    private void showFeedbackCard(Track track) {
        if (track == null || track.getId() == null) return;
        
        currentFeedbackTrack = track;
        tvTrackInfo.setText("Track: " + track.getTitle() + " • Artist: " + track.getArtist());
        cardFeedback.setVisibility(View.VISIBLE);
    }
    
    /**
     * Hide the feedback card
     */
    private void hideFeedbackCard() {
        cardFeedback.setVisibility(View.GONE);
        currentFeedbackTrack = null;
    }
    
    /**
     * Submit user feedback for a track
     * @param isPositive true for thumbs up, false for thumbs down
     */
    private void provideFeedback(boolean isPositive) {
        if (currentFeedbackTrack == null || currentFeedbackTrack.getId() == null) {
            hideFeedbackCard();
            return;
        }
        
        // Show feedback in progress
        btnThumbsUp.setEnabled(false);
        btnThumbsDown.setEnabled(false);
        btnDismiss.setEnabled(false);
        
        // Process feedback
        recommendationService.processFeedback(
            currentFeedbackTrack.getId(), 
            isPositive, 
            success -> {
                if (isAdded()) {
                    // Re-enable buttons
                    btnThumbsUp.setEnabled(true);
                    btnThumbsDown.setEnabled(true);
                    btnDismiss.setEnabled(true);
                    
                    // Show a toast confirmation
                    String message = isPositive ? 
                        "Thanks! We'll recommend more like this" : 
                        "Thanks! We'll improve your recommendations";
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    
                    // Hide the feedback card
                    hideFeedbackCard();
                    
                    // Refresh recommendations if negative feedback
                    if (!isPositive) {
                        loadRecommendations(true);
                    }
                }
            }
        );
    }
    
    @Override
    public void onRecommendationsUpdated(List<Track> recommendations) {
        if (isAdded()) {
            updateRecommendations(recommendations);
        }
    }
} 