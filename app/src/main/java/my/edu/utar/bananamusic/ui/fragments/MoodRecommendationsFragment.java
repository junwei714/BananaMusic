package my.edu.utar.bananamusic.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.MoodRecommendationsAdapter;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.ui.base.SafeFragment;
import my.edu.utar.bananamusic.utils.SafeRecyclerViewLayoutManager;
import my.edu.utar.bananamusic.viewmodels.MoodRecommendationsViewModel;

/**
 * Fragment for displaying mood-based music recommendations.
 */
public class MoodRecommendationsFragment extends SafeFragment implements MoodRecommendationsAdapter.OnMoodTrackClickListener {

    private MoodRecommendationsViewModel viewModel;
    private RecyclerView recommendationsRecyclerView;
    private MoodRecommendationsAdapter adapter;
    private ProgressBar loadingProgressBar;
    private TextView errorTextView;
    private TextView emptyStateTextView;
    private Spinner moodSpinner;

    public MoodRecommendationsFragment() {
        // Required empty public constructor
    }

    public static MoodRecommendationsFragment newInstance() {
        return new MoodRecommendationsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MoodRecommendationsViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_mood_recommendations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        recommendationsRecyclerView = view.findViewById(R.id.recommendationsRecyclerView);
        loadingProgressBar = view.findViewById(R.id.loadingProgressBar);
        errorTextView = view.findViewById(R.id.errorTextView);
        emptyStateTextView = view.findViewById(R.id.emptyStateTextView);
        moodSpinner = view.findViewById(R.id.moodSpinner);
        
        // Setup RecyclerView with safe layout manager
        recommendationsRecyclerView.setLayoutManager(new SafeRecyclerViewLayoutManager(requireContext()));
        recommendationsRecyclerView.setSaveEnabled(false); // Disable state saving
        adapter = new MoodRecommendationsAdapter(requireContext(), this);
        recommendationsRecyclerView.setAdapter(adapter);
        
        // Apply additional safety features
        useSafeLayoutManager(recommendationsRecyclerView);
        
        // Set background color for better visibility during debugging
        recommendationsRecyclerView.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"));
        
        // Log for debugging
        android.util.Log.d("MoodRecommendationsFragment", "RecyclerView setup complete");
        
        // Setup mood spinner
        setupMoodSpinner();
        
        // Observe ViewModel
        observeViewModel();
    }

    private void setupMoodSpinner() {
        List<String> moods = viewModel.getSupportedMoods();
        
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, moods);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        moodSpinner.setAdapter(spinnerAdapter);
        moodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedMood = (String) parent.getItemAtPosition(position);
                loadRecommendationsForMood(selectedMood);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private void loadRecommendationsForMood(String mood) {
        viewModel.loadRecommendationsForMood(mood);
    }

    private void observeViewModel() {
        // Observe loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            loadingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) {
                errorTextView.setVisibility(View.GONE);
                emptyStateTextView.setVisibility(View.GONE);
            }
            android.util.Log.d("MoodRecommendationsFragment", "Loading state: " + isLoading);
        });

        // Observe recommended tracks
        viewModel.getRecommendedTracks().observe(getViewLifecycleOwner(), tracks -> {
            android.util.Log.d("MoodRecommendationsFragment", "Received " + tracks.size() + " tracks");
            
            adapter.updateTracks(tracks);
            
            if (tracks.isEmpty()) {
                emptyStateTextView.setVisibility(View.VISIBLE);
                recommendationsRecyclerView.setVisibility(View.GONE);
                android.util.Log.d("MoodRecommendationsFragment", "Showing empty state");
            } else {
                emptyStateTextView.setVisibility(View.GONE);
                recommendationsRecyclerView.setVisibility(View.VISIBLE);
                android.util.Log.d("MoodRecommendationsFragment", "Showing track list");
            }
        });

        // Observe error state
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                errorTextView.setText(errorMsg);
                errorTextView.setVisibility(View.VISIBLE);
                android.util.Log.e("MoodRecommendationsFragment", "Error: " + errorMsg);
            } else {
                errorTextView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onTrackClick(Track track, int position) {
        // Open track details or play the track
        Toast.makeText(requireContext(), "Selected: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        // TODO: Implement navigation to track details or begin playback
    }

    @Override
    public void onPlayButtonClick(Track track, int position) {
        // Start playback immediately
        Toast.makeText(requireContext(), "Playing: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        // TODO: Implement immediate track playback
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // Save minimal state to avoid RecyclerView issues
        try {
            super.onSaveInstanceState(outState);
        } catch (Exception e) {
            android.util.Log.e("MoodRecommendationsFragment", "Error in onSaveInstanceState: " + e.getMessage(), e);
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