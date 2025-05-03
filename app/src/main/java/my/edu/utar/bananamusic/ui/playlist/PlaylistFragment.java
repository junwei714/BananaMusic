package my.edu.utar.bananamusic.ui.playlist;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.PlaylistManagerExt;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapters;

/**
 * Fragment for displaying a playlist and its tracks
 */
public class PlaylistFragment extends Fragment {
    private static final String TAG = "PlaylistFragment";
    private static final String ARG_PLAYLIST_ID = "playlist_id";
    
    private String playlistId;
    private Playlist currentPlaylist;
    private PlaylistManager playlistManager;
    private ListenerRegistration playlistListener;
    
    // UI elements
    private ImageButton btnLovePlaylist;
    private FloatingActionButton btnPlay;
    private ImageButton btnShuffle;
    private ImageButton btnDownload;
    
    public static PlaylistFragment newInstance(String playlistId) {
        PlaylistFragment fragment = new PlaylistFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PLAYLIST_ID, playlistId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            playlistId = getArguments().getString(ARG_PLAYLIST_ID);
        }
        
        // Initialize playlist manager
        playlistManager = PlaylistManager.getInstance(requireContext());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize UI elements
        btnLovePlaylist = view.findViewById(R.id.btnLovePlaylist);
        btnPlay = view.findViewById(R.id.btnPlay);
        btnShuffle = view.findViewById(R.id.btnShuffle);
        btnDownload = view.findViewById(R.id.btnDownload);
        
        // Set up click listeners
        setupClickListeners();
        
        // Load playlist data
        if (playlistId != null) {
            loadPlaylist();
        }
    }
    
    private void setupClickListeners() {
        // Set up love button click listener
        btnLovePlaylist.setOnClickListener(v -> {
            if (currentPlaylist != null) {
                togglePlaylistInLibrary();
            }
        });
        
        // Set up play button click listener
        btnPlay.setOnClickListener(v -> {
            if (currentPlaylist != null) {
                playPlaylist();
            }
        });
        
        // Set up shuffle button click listener
        btnShuffle.setOnClickListener(v -> {
            if (currentPlaylist != null) {
                shufflePlaylist();
            }
        });
        
        // Set up download button click listener
        btnDownload.setOnClickListener(v -> {
            if (currentPlaylist != null) {
                downloadPlaylist();
            }
        });
    }
    
    private void loadPlaylist() {
        playlistManager.getPlaylist(playlistId, new PlaylistManager.PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                // Handle success
                updateUI(playlist);
            }

            @Override
            public void onError(String message) {
                // Handle error
                showError(message);
            }
        });
    }
    
    private void updateUI(Playlist playlist) {
        if (playlist != null && isAdded()) {
            currentPlaylist = playlist;
            updateUI();
        }
    }
    
    private void updateUI() {
        if (currentPlaylist == null || !isAdded()) return;
        
        // Update love button state
        updateLoveButtonState();
    }
    
    private void updateLoveButtonState() {
        if (currentPlaylist == null || !isAdded()) return;
        
        boolean isCollected = playlistManager.isPlaylistCollected(currentPlaylist.getPlaylistId());
        
        // Update button icon
        btnLovePlaylist.setImageResource(isCollected ? 
                R.drawable.ic_favorite : 
                R.drawable.ic_favorite_border);
        
        // Update button tint if needed
        btnLovePlaylist.setColorFilter(requireContext().getResources().getColor(
                isCollected ? R.color.colorAccent : R.color.text_secondary));
    }
    
    private void togglePlaylistInLibrary() {
        if (currentPlaylist == null || !isAdded()) return;
        
        String playlistId = currentPlaylist.getPlaylistId();
        boolean currentlyInLibrary = playlistManager.isPlaylistCollected(playlistId);
        boolean isCollaborative = currentPlaylist.isCollaborative();
        
        // Apply animation to button for better feedback
        btnLovePlaylist.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(100)
            .withEndAction(() -> btnLovePlaylist.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(100)
                .start())
            .start();
        
        // Use PlaylistManager to toggle collection state
        PlaylistManagerExt.toggleCollectPlaylist(requireContext(), playlistId, !currentlyInLibrary, 
                new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist updatedPlaylist) {
                if (!isAdded()) return;
                
                // Update our current playlist reference
                currentPlaylist = updatedPlaylist;
                
                // Update UI
                updateLoveButtonState();
                
                // Show appropriate feedback
                String message;
                if (currentlyInLibrary) {
                    message = "Removed from your library";
                } else {
                    if (isCollaborative) {
                        message = "Added to your library as a collaborative playlist";
                    } else {
                        message = "Added to your library";
                    }
                }
                
                Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show();
                
                // Refresh the LibraryFragment if we're in MainActivity
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).refreshLibraryFragment();
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                
                // Revert UI
                updateLoveButtonState();
                
                // Show error
                Snackbar.make(requireView(), 
                    "Error: " + errorMessage, 
                    Snackbar.LENGTH_LONG).show();
            }
        });
    }
    
    private void playPlaylist() {
        // Implement playlist playback
        Toast.makeText(requireContext(), "Playing playlist", Toast.LENGTH_SHORT).show();
    }
    
    private void shufflePlaylist() {
        // Implement playlist shuffle
        Toast.makeText(requireContext(), "Shuffling playlist", Toast.LENGTH_SHORT).show();
    }
    
    private void downloadPlaylist() {
        // Implement playlist download
        Toast.makeText(requireContext(), "Downloading playlist", Toast.LENGTH_SHORT).show();
    }
    
    private void showError(String message) {
        Log.e(TAG, "Error loading playlist: " + message);
        if (isAdded()) {
            Toast.makeText(requireContext(), "Error loading playlist: " + message, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Remove playlist listener
        if (playlistListener != null) {
            playlistListener.remove();
            playlistListener = null;
        }
    }
} 