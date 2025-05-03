package my.edu.utar.bananamusic.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.PlaylistAdapter;
import my.edu.utar.bananamusic.adapters.TrackAdapter;
import my.edu.utar.bananamusic.auth.LoginActivity;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.PlaylistDetailsHelper;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.activities.CreatePlaylistActivity;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;

public class ProfileActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private FirebaseUser currentUser;

    private ShapeableImageView ivProfilePicture;
    private Chip chipCurrentMood;
    private RecyclerView rvRecentlyPlayed;
    private RecyclerView rvPlaylists;
    private FloatingActionButton fabEditProfile;
    private PlaylistAdapter playlistAdapter;
    private View progressBar;
    private View emptyView;
    private PlaylistManager playlistManager;
    private AudioPlayerHelper audioPlayerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initFirebase();
        initViews();
        setupToolbar();
        loadUserData();
        setupRecyclerViews();
        setClickListeners();
    }

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            // User not logged in, redirect to login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        playlistManager = PlaylistManager.getInstance(this);
        audioPlayerHelper = AudioPlayerHelper.getInstance(this);
    }

    private void initViews() {
        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        chipCurrentMood = findViewById(R.id.chipCurrentMood);
        rvRecentlyPlayed = findViewById(R.id.rvRecentlyPlayed);
        rvPlaylists = findViewById(R.id.rvPlaylists);
        fabEditProfile = findViewById(R.id.fabEditProfile);
        progressBar = findViewById(R.id.progressBar);
        emptyView = findViewById(R.id.emptyView);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Profile");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void loadUserData() {
        if (currentUser != null) {
            mFirestore.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String displayName = documentSnapshot.getString("displayName");
                            String email = currentUser.getEmail();
                            String mood = documentSnapshot.getString("mood");
                            String profilePicUrl = documentSnapshot.getString("profilePicUrl");

                            // Update UI with user data
                            if (getSupportActionBar() != null) {
                                getSupportActionBar().setTitle(displayName);
                            }
                            
                            if (profilePicUrl != null && !profilePicUrl.isEmpty()) {
                                Glide.with(this)
                                        .load(profilePicUrl)
                                        .placeholder(R.drawable.default_profile_picture)
                                        .into(ivProfilePicture);
                            }

                            if (mood != null && !mood.isEmpty()) {
                                chipCurrentMood.setText(mood);
                                chipCurrentMood.setVisibility(View.VISIBLE);
                            } else {
                                chipCurrentMood.setVisibility(View.GONE);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error loading profile: " + e.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void setupRecyclerViews() {
        // Setup Recently Played
        rvRecentlyPlayed.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        List<Track> recentTracks = new ArrayList<>(); // Will be populated later
        TrackAdapter trackAdapter = new TrackAdapter(this, recentTracks, (track, position) -> {
            // Handle track click
            Toast.makeText(this, "Playing " + track.getTitle(), Toast.LENGTH_SHORT).show();
            
            // Play the track
            if (audioPlayerHelper != null) {
                audioPlayerHelper.playTrack(track);
            }
        });
        rvRecentlyPlayed.setAdapter(trackAdapter);

        // Setup Playlists
        rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
        playlistAdapter = new PlaylistAdapter(this);
        playlistAdapter.setOnPlaylistClickListener(new PlaylistAdapter.OnPlaylistClickListener() {
            @Override
            public void onPlaylistClick(Playlist playlist) {
                // Navigate to playlist detail
                PlaylistDetailsHelper.showPlaylistDetails(ProfileActivity.this, playlist);
            }

            @Override
            public void onPlaylistSaveClick(Playlist playlist) {
                // Handle save playlist action
                if (playlist != null) {
                    if (playlist.isSaved()) {
                        // Remove from saved playlists
                        removeFromSavedPlaylists(playlist);
                    } else {
                        // Add to saved playlists
                        addToSavedPlaylists(playlist);
                    }
                }
            }
        });
        rvPlaylists.setAdapter(playlistAdapter);

        // Load user's playlists
        loadUserPlaylists();
    }

    private void loadUserPlaylists() {
        showLoadingIndicator();
        playlistManager.getUserPlaylists(new PlaylistManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                hideLoadingIndicator();
                if (playlists != null && !playlists.isEmpty()) {
                    playlistAdapter.updateData(playlists);
                } else {
                    showEmptyState();
                }
            }

            @Override
            public void onError(String message) {
                hideLoadingIndicator();
                showError("Failed to load playlists: " + message);
            }
        });
    }

    private void showPlaylistOptionsDialog(Playlist playlist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Playlist Options")
               .setItems(new String[]{"Edit", "Delete", "Share"}, (dialog, which) -> {
                   switch (which) {
                       case 0: // Edit
                           editPlaylist(playlist);
                           break;
                       case 1: // Delete
                           deletePlaylist(playlist);
                           break;
                       case 2: // Share
                           sharePlaylist(playlist);
                           break;
                   }
               })
               .show();
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
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
        }
        if (rvPlaylists != null) {
            rvPlaylists.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setClickListeners() {
        fabEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            startActivity(intent);
        });
    }

    private void playTrack(Track track) {
        if (track == null) return;
        
        // Show a toast indicating track is playing
        Toast.makeText(this, "Playing: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        
        // In a real implementation, this would integrate with your audio player service
        // For now, we'll just display a message
        if (track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
            // Here you would send the preview URL to your audio player service
        } else {
            Toast.makeText(this, "No preview available for this track", Toast.LENGTH_SHORT).show();
        }
    }

    private void editPlaylist(Playlist playlist) {
        if (playlist == null) return;
        
        // Navigate to edit playlist activity
        Intent intent = new Intent(this, CreatePlaylistActivity.class);
        intent.putExtra("PLAYLIST_ID", playlist.getPlaylistId());
        intent.putExtra("EDIT_MODE", true);
        startActivity(intent);
    }

    private void deletePlaylist(Playlist playlist) {
        if (playlist == null) return;
        
        // Show confirmation dialog
        new AlertDialog.Builder(this)
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete \"" + playlist.getName() + "\"?")
            .setPositiveButton("Delete", (dialog, which) -> {
                // Show loading indicator
                showLoadingIndicator();
                
                // Delete playlist
                playlistManager.deletePlaylist(playlist.getPlaylistId(), new PlaylistManager.OperationCallback() {
                    @Override
                    public void onSuccess(String message) {
                        hideLoadingIndicator();
                        Toast.makeText(ProfileActivity.this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                        
                        // Refresh playlists
                        loadUserPlaylists();
                    }

                    @Override
                    public void onError(String message) {
                        hideLoadingIndicator();
                        Toast.makeText(ProfileActivity.this, "Error deleting playlist: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void sharePlaylist(Playlist playlist) {
        if (playlist == null) return;
        
        // Create a share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out this playlist: " + playlist.getName());
        
        // Create shareable text
        String shareText = "Check out \"" + playlist.getName() + "\" playlist on BananaMusic!\n\n";
        if (playlist.getDescription() != null && !playlist.getDescription().isEmpty()) {
            shareText += playlist.getDescription() + "\n\n";
        }
        shareText += "Shared from BananaMusic app";
        
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share playlist via"));
    }

    private void addToSavedPlaylists(Playlist playlist) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || playlist == null) return;

        // Reference to user's saved playlists
        DocumentReference userSavedPlaylistRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.getUid())
            .collection("savedPlaylists")
            .document(playlist.getPlaylistId());

        // Create save data
        Map<String, Object> savedPlaylist = new HashMap<>();
        savedPlaylist.put("playlistId", playlist.getPlaylistId());
        savedPlaylist.put("savedAt", System.currentTimeMillis());
        savedPlaylist.put("creatorId", playlist.getCreatorId());
        savedPlaylist.put("name", playlist.getName());

        userSavedPlaylistRef.set(savedPlaylist)
            .addOnSuccessListener(aVoid -> {
                playlist.setSaved(true);
                playlistAdapter.notifyDataSetChanged();
                Toast.makeText(this, "Added to your library", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to save playlist", Toast.LENGTH_SHORT).show();
            });
    }

    private void removeFromSavedPlaylists(Playlist playlist) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || playlist == null) return;

        // Reference to user's saved playlist
        DocumentReference userSavedPlaylistRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.getUid())
            .collection("savedPlaylists")
            .document(playlist.getPlaylistId());

        userSavedPlaylistRef.delete()
            .addOnSuccessListener(aVoid -> {
                playlist.setSaved(false);
                playlistAdapter.notifyDataSetChanged();
                Toast.makeText(this, "Removed from your library", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to remove playlist", Toast.LENGTH_SHORT).show();
            });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 