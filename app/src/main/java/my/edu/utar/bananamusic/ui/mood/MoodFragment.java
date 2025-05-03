package my.edu.utar.bananamusic.ui.mood;

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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.TrackAdapter;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.services.MoodRecommendationService;

/**
 * Fragment for mood-based music recommendations
 */
public class MoodFragment extends Fragment implements TrackAdapter.OnTrackClickListener {
    
    private EditText moodInputEditText;
    private Button submitButton;
    private RecyclerView recommendationsRecyclerView;
    private ProgressBar loadingProgressBar;
    private TextView moodDetectedTextView;
    private TrackAdapter trackAdapter;
    private List<Track> recommendedTracks = new ArrayList<>();
    
    private MoodRecommendationService moodRecommendationService;
    
    public MoodFragment() {
        // Required empty public constructor
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize mood recommendation service
        moodRecommendationService = MoodRecommendationService.getInstance(requireContext());
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_mood, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize UI components
        moodInputEditText = view.findViewById(R.id.mood_input_edit_text);
        submitButton = view.findViewById(R.id.submit_mood_button);
        recommendationsRecyclerView = view.findViewById(R.id.recommendations_recycler_view);
        loadingProgressBar = view.findViewById(R.id.loading_progress_bar);
        moodDetectedTextView = view.findViewById(R.id.mood_detected_text_view);
        
        // Set up the RecyclerView
        recommendationsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        trackAdapter = new TrackAdapter(requireContext(), recommendedTracks, this);
        recommendationsRecyclerView.setAdapter(trackAdapter);
        
        // Set up quick mood buttons
        setupMoodButtons(view);
        
        // Set up submit button click listener
        submitButton.setOnClickListener(v -> getMoodRecommendations());
    }
    
    private void setupMoodButtons(View view) {
        // Initialize mood buttons with predefined moods
        Button happyButton = view.findViewById(R.id.happy_mood_button);
        Button sadButton = view.findViewById(R.id.sad_mood_button);
        Button energeticButton = view.findViewById(R.id.energetic_mood_button);
        Button relaxedButton = view.findViewById(R.id.relaxed_mood_button);
        Button romanticButton = view.findViewById(R.id.romantic_mood_button);
        Button focusedButton = view.findViewById(R.id.focused_mood_button);
        
        // Set click listeners for quick mood selection
        happyButton.setOnClickListener(v -> setMoodAndGetRecommendations("happy"));
        sadButton.setOnClickListener(v -> setMoodAndGetRecommendations("sad"));
        energeticButton.setOnClickListener(v -> setMoodAndGetRecommendations("energetic"));
        relaxedButton.setOnClickListener(v -> setMoodAndGetRecommendations("relaxed"));
        romanticButton.setOnClickListener(v -> setMoodAndGetRecommendations("romantic"));
        focusedButton.setOnClickListener(v -> setMoodAndGetRecommendations("focused"));
    }
    
    private void setMoodAndGetRecommendations(String mood) {
        moodInputEditText.setText(mood);
        getMoodRecommendations();
    }
    
    private void getMoodRecommendations() {
        String moodInput = moodInputEditText.getText().toString().trim();
        
        if (moodInput.isEmpty()) {
            Toast.makeText(getContext(), "Please enter your mood", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading indicator
        loadingProgressBar.setVisibility(View.VISIBLE);
        recommendationsRecyclerView.setVisibility(View.GONE);
        moodDetectedTextView.setVisibility(View.GONE);
        
        // Get recommendations based on mood (using fixed limit of 10)
        moodRecommendationService.getRecommendationsForMood(moodInput, 10, new MoodRecommendationService.RecommendationCallback() {
            @Override
            public void onRecommendationsReady(List<Track> recommendations) {
                // Update UI on the main thread
                if (isAdded()) {
                    // Hide loading indicator
                    loadingProgressBar.setVisibility(View.GONE);
                    
                    // Update mood detected text
                    moodDetectedTextView.setText("Recommended for mood: " + moodInput);
                    moodDetectedTextView.setVisibility(View.VISIBLE);
                    
                    // Update recycler view with recommendations
                    recommendedTracks.clear();
                    recommendedTracks.addAll(recommendations);
                    trackAdapter.notifyDataSetChanged();
                    recommendationsRecyclerView.setVisibility(View.VISIBLE);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                // Update UI on the main thread
                if (isAdded()) {
                    // Hide loading indicator
                    loadingProgressBar.setVisibility(View.GONE);
                    
                    // Show error message
                    Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onTrackClick(Track track, int position) {
        // Handle track click
        if (track != null) {
            Toast.makeText(requireContext(), "Playing: " + track.getTitle(), Toast.LENGTH_SHORT).show();
            // TODO: Implement track playback
        }
    }
} 