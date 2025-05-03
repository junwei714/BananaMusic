package my.edu.utar.bananamusic.ui.tracks;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.SafeTrackAdapter;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.ui.base.SafeFragment;

/**
 * Fragment for displaying tracks with error handling
 */
public class TracksFragment extends SafeFragment implements SafeTrackAdapter.OnTrackClickListener {
    
    private static final String TAG = "TracksFragment";
    private SafeTrackAdapter adapter;
    private RecyclerView recyclerView;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tracks, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        Log.d(TAG, "Setting up TracksFragment");
        
        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.recycler_view);
        
        // Initialize adapter
        adapter = new SafeTrackAdapter(requireContext(), this);
        
        // Set up RecyclerView with the adapter
        adapter.setupWithRecyclerView(recyclerView, true);
        
        // Load trending tracks
        loadTrendingTracks();
    }
    
    /**
     * Load trending tracks
     */
    private void loadTrendingTracks() {
        Log.d(TAG, "Loading trending tracks");
        adapter.fetchTrendingTracks();
    }
    
    /**
     * Search for tracks with query
     */
    public void searchTracks(String query) {
        Log.d(TAG, "Searching tracks with query: " + query);
        adapter.searchTracks(query);
    }
    
    /**
     * Load tracks by mood
     */
    public void loadTracksByMood(String mood) {
        Log.d(TAG, "Loading tracks by mood: " + mood);
        adapter.fetchTracksByMood(mood);
    }
    
    @Override
    public void onTrackClick(Track track, int position) {
        // Handle track click
        Log.d(TAG, "Track clicked: " + track.getTitle());
        Toast.makeText(requireContext(), "Track clicked: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        
        // Navigate to track detail or start playback
        // Implementation depends on your app's navigation/playback system
    }
    
    @Override
    public void onTrackOptionsClick(Track track, int position, View view) {
        // Show options menu for track
        // For example: show a popup menu with options like add to playlist, share, etc.
        Log.d(TAG, "Track options clicked: " + track.getTitle());
        Toast.makeText(requireContext(), "Options for: " + track.getTitle(), Toast.LENGTH_SHORT).show();
    }
} 