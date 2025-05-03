package my.edu.utar.bananamusic.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.TrackAdapter;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.services.MoodRecommendationService;
import my.edu.utar.bananamusic.ui.base.SafeFragment;
import my.edu.utar.bananamusic.utils.SafeRecyclerViewLayoutManager;

public class MoodRecommendationFragment extends SafeFragment {

    private EditText moodEditText;
    private TextView detectedMoodText;
    private ProgressBar loadingProgressBar;
    private RecyclerView recommendationsRecyclerView;
    private TrackAdapter trackAdapter;
    
    private MoodRecommendationService recommendationService;
    
    // Mood preset buttons
    private Button btnHappy, btnSad, btnEnergetic, btnRelaxed, btnRomantic, btnFocused;
    
    public MoodRecommendationFragment() {
        // Required empty public constructor
    }
    
    public static MoodRecommendationFragment newInstance() {
        return new MoodRecommendationFragment();
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recommendationService = MoodRecommendationService.getInstance(requireContext());
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mood_recommendation, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        moodEditText = view.findViewById(R.id.moodEditText);
        Button getMoodRecommendationsButton = view.findViewById(R.id.getMoodRecommendationsButton);
        detectedMoodText = view.findViewById(R.id.detectedMoodText);
        loadingProgressBar = view.findViewById(R.id.loadingProgressBar);
        recommendationsRecyclerView = view.findViewById(R.id.recommendationsRecyclerView);
        
        // Initialize mood preset buttons
        btnHappy = view.findViewById(R.id.btnHappy);
        btnSad = view.findViewById(R.id.btnSad);
        btnEnergetic = view.findViewById(R.id.btnEnergetic);
        btnRelaxed = view.findViewById(R.id.btnRelaxed);
        btnRomantic = view.findViewById(R.id.btnRomantic);
        btnFocused = view.findViewById(R.id.btnFocused);
        
        // Setup RecyclerView with safe layout manager
        recommendationsRecyclerView.setLayoutManager(new SafeRecyclerViewLayoutManager(requireContext()));
        recommendationsRecyclerView.setSaveEnabled(false); // Disable state saving
        trackAdapter = new TrackAdapter(new ArrayList<>(), (track, position) -> {
            // Handle track selection
            Toast.makeText(requireContext(), "Selected: " + track.getTitle(), Toast.LENGTH_SHORT).show();
            // In a real app, you would start playback here
        });
        recommendationsRecyclerView.setAdapter(trackAdapter);
        
        // Apply additional safety features
        useSafeLayoutManager(recommendationsRecyclerView);
        
        // Setup click listeners for mood recommendation button
        getMoodRecommendationsButton.setOnClickListener(v -> {
            String mood = moodEditText.getText().toString().trim();
            if (!mood.isEmpty()) {
                getRecommendationsForMood(mood);
            } else {
                Toast.makeText(requireContext(), "Please enter a mood", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Setup click listeners for preset mood buttons
        setupMoodPresetButtons();
    }
    
    private void setupMoodPresetButtons() {
        btnHappy.setOnClickListener(v -> {
            moodEditText.setText("Happy");
            getRecommendationsForMood("Happy");
        });
        
        btnSad.setOnClickListener(v -> {
            moodEditText.setText("Sad");
            getRecommendationsForMood("Sad");
        });
        
        btnEnergetic.setOnClickListener(v -> {
            moodEditText.setText("Energetic");
            getRecommendationsForMood("Energetic");
        });
        
        btnRelaxed.setOnClickListener(v -> {
            moodEditText.setText("Relaxed");
            getRecommendationsForMood("Relaxed");
        });
        
        btnRomantic.setOnClickListener(v -> {
            moodEditText.setText("Romantic");
            getRecommendationsForMood("Romantic");
        });
        
        btnFocused.setOnClickListener(v -> {
            moodEditText.setText("Focused");
            getRecommendationsForMood("Focused");
        });
    }
    
    private void getRecommendationsForMood(String mood) {
        // Show loading state
        detectedMoodText.setText("Detecting mood: " + mood);
        loadingProgressBar.setVisibility(View.VISIBLE);
        recommendationsRecyclerView.setVisibility(View.GONE);
        
        // Get recommendations from service
        recommendationService.getRecommendationsForMood(mood, new MoodRecommendationService.RecommendationCallback() {
            @Override
            public void onRecommendationsReady(List<Track> recommendations) {
                // Update UI on the main thread
                if (isAdded() && getActivity() != null) {
                    loadingProgressBar.setVisibility(View.GONE);
                    recommendationsRecyclerView.setVisibility(View.VISIBLE);
                    
                    if (!recommendations.isEmpty()) {
                        trackAdapter.updateTrackList(recommendations);
                        detectedMoodText.setText("Recommended songs for: " + mood);
                    } else {
                        detectedMoodText.setText("No recommendations found for: " + mood);
                    }
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                // Handle error
                if (isAdded() && getActivity() != null) {
                    loadingProgressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    detectedMoodText.setText("Error getting recommendations");
                }
            }
        });
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // Save minimal state to avoid RecyclerView issues
        try {
            super.onSaveInstanceState(outState);
        } catch (Exception e) {
            android.util.Log.e("MoodRecommendationFragment", "Error in onSaveInstanceState: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void onDestroyView() {
        // Clean up adapter reference
        if (recommendationsRecyclerView != null) {
            recommendationsRecyclerView.setAdapter(null);
        }
        super.onDestroyView();
    }
} 