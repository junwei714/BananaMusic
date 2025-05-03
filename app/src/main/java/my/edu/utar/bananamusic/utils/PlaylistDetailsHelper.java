package my.edu.utar.bananamusic.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.PopupMenu;
import android.widget.ImageButton;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.TrackAdapter;
import my.edu.utar.bananamusic.adapters.TrackAdapter.OnTrackActionListener;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.providers.ApiDataProvider;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import my.edu.utar.bananamusic.ui.library.LibraryFragment;
import android.util.Log;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.UUID;
import android.content.Intent;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import my.edu.utar.bananamusic.utils.UserPreferences;
import my.edu.utar.bananamusic.utils.FirebaseConfig;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapters;
import my.edu.utar.bananamusic.utils.TracksAdapter;
import my.edu.utar.bananamusic.ui.dialogs.AddToPlaylistDialog;

/**
 * Helper class to show playlist details dialog across different fragments
 */
public class PlaylistDetailsHelper {

    private static ApiDataProvider apiProvider;

    /**
     * Shows a dialog with playlist details and options
     * @param context Context for showing dialog and toast messages
     * @param playlist The playlist to show details for
     */
    public static void showPlaylistDetails(Context context, Playlist playlist) {
        // Ensure we have a valid playlist with tracks
        if (context == null || playlist == null || playlist.getPlaylistId() == null) {
            if (context != null) {
                Toast.makeText(context, "Invalid playlist", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        // Show loading indicator
        Toast.makeText(context, "Loading playlist tracks...", Toast.LENGTH_SHORT).show();
        
        // Get tracks for the playlist using ApiDataProvider with callbacks
        ApiDataProvider apiProvider = ApiDataProvider.getInstance(context);
        
        // Check if this is a Spotify playlist (starting with "spotify:")
        if (playlist.getPlaylistId() != null && playlist.getPlaylistId().startsWith("spotify:")) {
            // Extract the Spotify ID from the playlist ID (format: spotify:playlist:ID)
            String spotifyId = playlist.getPlaylistId().replace("spotify:playlist:", "");
            Log.d("PlaylistDetailsHelper", "Loading Spotify playlist: " + spotifyId);
            
            apiProvider.getPlaylistTracks(playlist.getPlaylistId(), TracksAdapter.toApiCallback(new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    Log.d("PlaylistDetailsHelper", "Successfully loaded " + tracks.size() + " tracks from Spotify playlist");
                    handlePlaylistTracks(context, playlist, tracks, dialog -> {});
                }
                
                @Override
                public void onError(String message) {
                    Log.e("PlaylistDetailsHelper", "Error loading Spotify tracks: " + message);
                    Toast.makeText(context, "Error loading tracks: " + message, Toast.LENGTH_SHORT).show();
                }
            }));
        } else if (playlist.getPlaylistId() != null && playlist.getPlaylistId().startsWith("deezer:")) {
            // Extract the Deezer ID from the playlist ID (format: deezer:playlist:ID)
            String deezerId = playlist.getPlaylistId().replace("deezer:playlist:", "");
            Log.d("PlaylistDetailsHelper", "Loading Deezer playlist: " + deezerId);
            
            apiProvider.getPlaylistTracks(playlist.getPlaylistId(), TracksAdapter.toApiCallback(new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    Log.d("PlaylistDetailsHelper", "Successfully loaded " + tracks.size() + " tracks from Deezer playlist");
                    handlePlaylistTracks(context, playlist, tracks, dialog -> {});
                }
                
                @Override
                public void onError(String message) {
                    Log.e("PlaylistDetailsHelper", "Error loading Deezer tracks: " + message);
                    Toast.makeText(context, "Error loading tracks: " + message, Toast.LENGTH_SHORT).show();
                }
            }));
        } else {
            // Use the standard playlist tracks getter for local playlists
            Log.d("PlaylistDetailsHelper", "Loading local playlist: " + playlist.getPlaylistId());
            
            apiProvider.getPlaylistTracks(playlist.getPlaylistId(), TracksAdapter.toApiCallback(new my.edu.utar.bananamusic.utils.callbacks.TracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (tracks != null && !tracks.isEmpty()) {
                        // Update the playlist with the tracks
                        Log.d("PlaylistDetailsHelper", "Successfully loaded " + tracks.size() + " tracks from local playlist");
                        playlist.setTracks(tracks);
                        handlePlaylistTracks(context, playlist, tracks, dialog -> {});
                    } else {
                        Log.w("PlaylistDetailsHelper", "No tracks found in playlist");
                        Toast.makeText(context, "No tracks found in playlist", Toast.LENGTH_SHORT).show();
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e("PlaylistDetailsHelper", "Error loading local tracks: " + message);
                    Toast.makeText(context, "Error loading playlist tracks: " + message, Toast.LENGTH_SHORT).show();
                }
            }));
        }
    }

    /**
     * Handle the retrieved playlist tracks by displaying them in a dialog
     */
    private static void handlePlaylistTracks(Context context, Playlist playlist, List<Track> tracks, Consumer<AlertDialog> dialogCallback) {
        if (tracks == null || tracks.isEmpty()) {
            Toast.makeText(context, "Could not load tracks for this playlist", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create custom dialog with improved UI
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogTheme);
        
        // Inflate custom layout
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_playlist_details, null);
        builder.setView(dialogView);
        
        // Create the dialog without showing it yet
        AlertDialog dialog = builder.create();
        
        // Request no title feature
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // Set transparent background for rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        // Set up views
        ImageView ivPlaylistCover = dialogView.findViewById(R.id.ivPlaylistCover);
        TextView tvPlaylistTitle = dialogView.findViewById(R.id.playlistTitle);
        TextView tvPlaylistDescription = dialogView.findViewById(R.id.tvPlaylistDescription);
        TextView tvTrackCount = dialogView.findViewById(R.id.tvTrackCount);
        RecyclerView rvPlaylistTracks = dialogView.findViewById(R.id.rvPlaylistTracks);
        Button btnClose = dialogView.findViewById(R.id.btnClose);
        Button btnPlayAll = dialogView.findViewById(R.id.btnPlayAll);
        Button btnInvite = dialogView.findViewById(R.id.btnInvite);
        ImageButton btnLovePlaylist = dialogView.findViewById(R.id.btnLovePlaylist);
        
        // Set playlist title
        tvPlaylistTitle.setText(playlist.getName());
        
        // Set playlist information
        if (playlist.getCoverImageUrl() != null && !playlist.getCoverImageUrl().isEmpty()) {
            Glide.with(context)
                .load(playlist.getCoverImageUrl())
                .apply(RequestOptions.centerCropTransform())
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .into(ivPlaylistCover);
        } else if (playlist.getCoverImageResourceId() != 0) {
            ivPlaylistCover.setImageResource(playlist.getCoverImageResourceId());
        }
        
        // Set description and track count with collaboration info
        String description = playlist.getDescription() != null ? playlist.getDescription() : 
                            "A " + (playlist.getMood() != null ? playlist.getMood() : "great") + " playlist with great tracks";
        
        // Add collaborative badge if playlist is collaborative
        if (playlist.isCollaborative()) {
            description = "👥 Collaborative: " + description;
        }
        
        tvPlaylistDescription.setText(description);
        
        // Format track count with total duration if available
        long totalDurationMs = calculateTotalDuration(tracks);
        String formattedDuration = formatTotalDuration(totalDurationMs);
        String trackCountText = tracks.size() + " track" + (tracks.size() == 1 ? "" : "s");
        if (totalDurationMs > 0) {
            trackCountText += " • " + formattedDuration;
        }
        tvTrackCount.setText(trackCountText);
        
        // Set up the love button for adding/removing playlist from library
        updateLoveButtonState(context, btnLovePlaylist, playlist);
        
        // Set up love button click listener
        btnLovePlaylist.setOnClickListener(v -> {
            togglePlaylistInLibrary(context, playlist, btnLovePlaylist);
        });
        
        // Show/hide invite button based on whether playlist is collaborative
        if (btnInvite != null) {
            if (playlist.isCollaborative()) {
                // Check if user is the creator or a collaborator
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                boolean isCreatorOrCollaborator = false;
                
                if (currentUser != null) {
                    // Check if user is creator
                    if (playlist.getCreatorId() != null && playlist.getCreatorId().equals(currentUser.getUid())) {
                        isCreatorOrCollaborator = true;
                    }
                    // Check if user is a collaborator
                    else if (playlist.getCollaborators() != null && playlist.getCollaborators().contains(currentUser.getUid())) {
                        isCreatorOrCollaborator = true;
                    }
                }
                
                // Only show invite button if user is creator or collaborator
                btnInvite.setVisibility(isCreatorOrCollaborator ? View.VISIBLE : View.GONE);
                
                // Set up invite button click listener
                btnInvite.setOnClickListener(v -> {
                    // Show invite dialog
                    showInviteCollaboratorsDialog(context, playlist);
                });
            } else {
                btnInvite.setVisibility(View.GONE);
            }
        }
        
        // Set up the track adapter with improved click handling
        TrackAdapter trackAdapter = new TrackAdapter(context, tracks, new TrackAdapter.OnTrackActionListener() {
            @Override
            public void onTrackPlay(Track track, int position) {
                // Play the track
                AudioPlayerHelper playerHelper = AudioPlayerHelper.getInstance(context);
                playerHelper.playTrack(track);
            }
            
            @Override
            public void onTrackDelete(Track track, int position) {
                // Handle track deletion
                if (playlist.isCollaborative()) {
                    // Show confirmation dialog for collaborative playlists
                    new AlertDialog.Builder(context)
                        .setTitle("Remove Track")
                        .setMessage("Remove \"" + track.getTitle() + "\" from this playlist?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            // Remove the track from the playlist
                            Toast.makeText(context, "Removing track...", Toast.LENGTH_SHORT).show();
                            // Implement actual removal here
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
            }
            
            @Override
            public void onTrackAddToPlaylist(Track track, int position) {
                // Show dialog to add track to another playlist
                showAddTrackToPlaylistDialog(context, track);
            }
            
            @Override
            public void onTrackMenuClick(Track track, int position, View view) {
                // Show a popup menu with track options
                PopupMenu popupMenu = new PopupMenu(context, view);
                popupMenu.inflate(R.menu.menu_track_options);
                
                // Configure menu items based on playlist type
                if (!playlist.isCollaborative()) {
                    popupMenu.getMenu().findItem(R.id.action_vote_up).setVisible(false);
                    popupMenu.getMenu().findItem(R.id.action_vote_down).setVisible(false);
                }
                
                // Set up menu item click listeners
                popupMenu.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_play) {
                        onTrackPlay(track, position);
                        return true;
                    } else if (itemId == R.id.action_add_to_playlist) {
                        onTrackAddToPlaylist(track, position);
                        return true;
                    } else if (itemId == R.id.action_vote_up) {
                        voteForTrack(context, playlist, track, true);
                        return true;
                    } else if (itemId == R.id.action_vote_down) {
                        voteForTrack(context, playlist, track, false);
                        return true;
                    } else if (itemId == R.id.action_remove) {
                        onTrackDelete(track, position);
                        return true;
                    }
                    return false;
                });
                
                // Show the menu
                popupMenu.show();
            }
        });
        
        // Set up RecyclerView
        rvPlaylistTracks.setLayoutManager(new LinearLayoutManager(context));
        rvPlaylistTracks.setAdapter(trackAdapter);
        
        // Set up Play All button
        btnPlayAll.setOnClickListener(v -> {
            if (!tracks.isEmpty()) {
                // Play the first track and queue the rest
                AudioPlayerHelper audioPlayerHelper = AudioPlayerHelper.getInstance(context);
                
                // Ensure all tracks have a source
                for (Track track : tracks) {
                    if (track.getSource() == null || track.getSource().isEmpty()) {
                        track.setSource(Track.SOURCE_DEEZER);
                    }
                }
                
                // Play the first track
                audioPlayerHelper.playTrack(tracks.get(0));
                
                // Queue the rest
                if (tracks.size() > 1) {
                    List<Track> remainingTracks = tracks.subList(1, tracks.size());
                    audioPlayerHelper.addAllToQueue(remainingTracks);
                }
                
                // Show toast message
                Toast.makeText(context, "Playing playlist: " + playlist.getName(), Toast.LENGTH_SHORT).show();
                
                // Dismiss the dialog if in a full-screen activity
                if (context instanceof MainActivity) {
                    dialog.dismiss();
                }
            }
        });
        
        // Set up Close button
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        // Show the dialog
        dialog.show();
        
        // Call back with the dialog
        dialogCallback.accept(dialog);
    }

    /**
     * Calculate total duration of tracks in milliseconds
     */
    private static long calculateTotalDuration(List<Track> tracks) {
        long totalDuration = 0;
        for (Track track : tracks) {
            if (track.getDuration() > 0) {
                totalDuration += track.getDuration();
            }
        }
        return totalDuration;
    }
    
    /**
     * Format total duration into a readable string (e.g., "1 hr 23 min")
     */
    private static String formatTotalDuration(long durationMs) {
        if (durationMs <= 0) {
            return "";
        }
        
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        
        if (hours > 0) {
            return hours + " hr " + minutes + " min";
        } else {
            return minutes + " min";
        }
    }

    /**
     * Helper method to vote for a track in a collaborative playlist
     */
    private static void voteForTrack(Context context, Playlist playlist, Track track, boolean isUpvote) {
        if (context == null || playlist == null || track == null) {
            return;
        }
        
        PlaylistManager playlistManager = PlaylistManager.getInstance(context);
        playlistManager.voteForTrack(playlist.getPlaylistId(), track.getId(), isUpvote, new PlaylistManager.PlaylistCallback() {
            @Override
            public void onSuccess(Playlist updatedPlaylist) {
                String voteType = isUpvote ? "upvoted" : "downvoted";
                Toast.makeText(context, "You " + voteType + " " + track.getTitle(), Toast.LENGTH_SHORT).show();
                
                // Refresh the playlist details
                showPlaylistDetails(context, updatedPlaylist);
            }
            
            @Override
            public void onError(String errorMessage) {
                Toast.makeText(context, "Error voting for track: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Play a track using Spotify Web API if available, otherwise fall back to the app's player
     * @param context Activity context
     * @param track Track to play
     * @param spotifyAccessToken Spotify access token (can be null)
     */
    public static void playTrackWithSpotify(Context context, Track track, String spotifyAccessToken) {
        if (track == null || context == null) {
            return;
        }
        
        // Check if we have a Spotify ID and token
        if (track.getSpotifyId() != null && !track.getSpotifyId().isEmpty() 
                && spotifyAccessToken != null && !spotifyAccessToken.isEmpty()) {
            
            // Show loading toast
            Toast.makeText(context, "Finding Spotify devices...", Toast.LENGTH_SHORT).show();
            
            // Get available Spotify devices
            SpotifyDeviceManager.getAvailableDevices(spotifyAccessToken, new SpotifyDeviceManager.DeviceCallback() {
                @Override
                public void onDevicesFound(List<SpotifyDeviceManager.SpotifyDevice> devices) {
                    // Select best device
                    SpotifyDeviceManager.SpotifyDevice device = SpotifyDeviceManager.selectBestDevice(devices);
                    
                    if (device != null) {
                        // We have a device, play on it
                        Toast.makeText(context, "Playing on " + device.getName(), Toast.LENGTH_SHORT).show();
                        
                        // Get player helper
                        AudioPlayerHelper playerHelper = AudioPlayerHelper.getInstance(context);
                        
                        // Play track on Spotify
                        playerHelper.playSpotifyTrackOnDevice(track.getSpotifyId(), device.getId(), spotifyAccessToken);
                    } else {
                        // No Spotify devices available, fall back to normal playback
                        Toast.makeText(context, "No Spotify devices found, using app player", Toast.LENGTH_SHORT).show();
                        fallbackToAppPlayer(context, track);
                    }
                }
                
                @Override
                public void onError(String message) {
                    // Error getting Spotify devices, fall back to normal playback
                    Toast.makeText(context, "Spotify device error: Using app player", Toast.LENGTH_SHORT).show();
                    fallbackToAppPlayer(context, track);
                }
            });
        } else {
            // No Spotify ID or token, use normal playback
            fallbackToAppPlayer(context, track);
        }
    }

    /**
     * Fall back to playing the track using the app's built-in player
     */
    private static void fallbackToAppPlayer(Context context, Track track) {
        try {
            // Set track source to Deezer if not specified
            if (track.getSource() == null || track.getSource().isEmpty()) {
                track.setSource(Track.SOURCE_DEEZER);
            }
            
            // Get AudioPlayerHelper instance and play the track
            AudioPlayerHelper audioPlayerHelper = AudioPlayerHelper.getInstance(context);
            audioPlayerHelper.playTrack(track);
            
            // Show toast message
            Toast.makeText(context, "Playing: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("PlaylistDetailsHelper", "Error playing track", e);
            Toast.makeText(context, "Could not play track: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Helper method to refresh the LibraryFragment if it's visible
     * @param context Context to find the FragmentManager
     */
    private static void refreshLibraryFragment(Context context) {
        try {
            // Try using MainActivity's method first (this is the new method we added)
            if (context instanceof MainActivity) {
                ((MainActivity) context).refreshLibraryFragment();
                return;
            }
            
            // Fall back to the existing approach
            if (context instanceof FragmentActivity) {
                FragmentManager fragmentManager = ((FragmentActivity) context).getSupportFragmentManager();
                Fragment fragment = null;
                
                // Find the LibraryFragment by tag
                fragment = fragmentManager.findFragmentByTag("LibraryFragment");
                
                // If tag doesn't work, try finding by traversing all active fragments
                if (fragment == null) {
                    // Try to find using NavHostFragment and navigation ID
                    Fragment navHostFragment = fragmentManager.findFragmentById(R.id.nav_host_fragment);
                    if (navHostFragment instanceof NavHostFragment) {
                        NavController navController = ((NavHostFragment) navHostFragment).getNavController();
                        // Check if the current destination is the library fragment
                        if (navController.getCurrentDestination().getId() == R.id.navigation_library) {
                            // Try to find the LibraryFragment
                            List<Fragment> fragments = navHostFragment.getChildFragmentManager().getFragments();
                            for (Fragment f : fragments) {
                                if (f instanceof LibraryFragment) {
                                    fragment = f;
                                    break;
                                }
                            }
                        }
                    }
                }
                
                // If found, refresh it
                if (fragment instanceof LibraryFragment) {
                    ((LibraryFragment) fragment).refreshPlaylists();
                }
            }
        } catch (Exception e) {
            Log.e("PlaylistDetailsHelper", "Error refreshing LibraryFragment", e);
        }
    }

    /**
     * Shows a dialog to add a track to a playlist
     * @param context Context for showing dialog and toast messages
     * @param track The track to add to a playlist
     */
    public static void showAddTrackToPlaylistDialog(Context context, Track track) {
        if (context == null || track == null) return;
        AddToPlaylistDialog dialog = AddToPlaylistDialog.newInstance(track);
        if (context instanceof androidx.fragment.app.FragmentActivity) {
            dialog.show(((androidx.fragment.app.FragmentActivity) context).getSupportFragmentManager(), "add_to_playlist");
        }
    }

    /**
     * Debug method to check why tracks aren't showing in a playlist
     * This will help identify if tracks are properly stored but not being retrieved
     * @param context Activity context
     * @param playlistId ID of the playlist to debug
     */
    public static void debugPlaylistTracks(Context context, String playlistId) {
        PlaylistManager playlistManager = PlaylistManager.getInstance(context);
        ApiDataProvider apiProvider = ApiDataProvider.getInstance(context);
        
        playlistManager.getPlaylist(playlistId, CallbackAdapter.toPlaylistManagerCallback(new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                if (playlist == null) {
                    Toast.makeText(context, "Playlist not found", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Get tracks for the playlist
                apiProvider.getPlaylistTracks(playlist.getPlaylistId(), CallbackAdapters.toApiProviderCallback(new TracksCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        if (tracks != null && !tracks.isEmpty()) {
                            // Process tracks
                            StringBuilder sb = new StringBuilder();
                            sb.append("Tracks in playlist ").append(playlist.getName()).append(":\n\n");
                            
                            for (Track track : tracks) {
                                sb.append("- ").append(track.getTitle())
                                  .append(" by ").append(track.getArtist())
                                  .append(" [").append(track.getId()).append("]\n");
                            }
                            
                            // Show tracks in dialog
                            new AlertDialog.Builder(context)
                                .setTitle("Playlist Tracks")
                                .setMessage(sb.toString())
                                .setPositiveButton("OK", null)
                                .show();
                        } else {
                            Toast.makeText(context, "No tracks found in playlist", Toast.LENGTH_SHORT).show();
                        }
                    }
                    
                    @Override
                    public void onError(String message) {
                        Toast.makeText(context, "Error loading tracks: " + message, Toast.LENGTH_SHORT).show();
                    }
                }));
            }
            
            @Override
            public void onError(String errorMessage) {
                Toast.makeText(context, "Error loading playlist: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        }));
    }

    /**
     * Attempts to repair a playlist by recovering missing tracks
     * @param context Context for showing toast messages and dialogs
     * @param playlistId ID of the playlist to repair
     */
    public static void repairPlaylist(Context context, String playlistId) {
        if (context == null || playlistId == null || playlistId.isEmpty()) {
            if (context != null) {
                Toast.makeText(context, "Invalid playlist information", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        // Show a progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(context)
            .setTitle("Repairing Playlist")
            .setMessage("Attempting to recover missing tracks...\n\nThis may take a moment.")
            .setCancelable(false)
            .show();
        
        // Get PlaylistManager instance
        PlaylistManager playlistManager = PlaylistManager.getInstance(context);
        
        // Attempt to recover the tracks
        playlistManager.recoverMissingTracks(playlistId, new PlaylistManager.PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                // Dismiss the progress dialog
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                
                // Show success dialog with results
                new AlertDialog.Builder(context)
                    .setTitle("Playlist Repair Complete")
                    .setMessage("The playlist \"" + playlist.getName() + "\" has been repaired.\n\n" +
                               "All track data has been recovered where possible. " +
                               "The playlist should now display tracks correctly.\n\n" +
                               "Try playing the tracks to see if they work.")
                    .setPositiveButton("View Playlist", (dialog, which) -> {
                        // Show the playlist details again
                        showPlaylistDetails(context, playlist);
                    })
                    .setNegativeButton("OK", null)
                    .show();
                
                // Refresh the library to show changes
                refreshLibraryFragment(context);
            }
            
            @Override
            public void onError(String errorMessage) {
                // Dismiss the progress dialog
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                
                // Show error dialog
                new AlertDialog.Builder(context)
                    .setTitle("Repair Failed")
                    .setMessage("Could not repair the playlist: " + errorMessage + 
                               "\n\nTry adding tracks again or creating a new playlist.")
                    .setPositiveButton("OK", null)
                    .show();
            }
        });
    }

    /**
     * Attempts to repair all user playlists in a single operation
     * @param context Context for showing toast messages and dialogs
     */
    public static void repairAllPlaylists(Context context) {
        if (context == null) {
            return;
        }
        
        // Show a progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(context)
            .setTitle("Repairing All Playlists")
            .setMessage("Attempting to recover missing tracks for all playlists...\n\nThis may take a moment.")
            .setCancelable(false)
            .show();
        
        // Get PlaylistManager instance
        PlaylistManager playlistManager = PlaylistManager.getInstance(context);
        
        // Call the method to repair all playlists
        playlistManager.repairAllPlaylists(new PlaylistManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                // Dismiss the progress dialog
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                
                // Show success dialog with results
                new AlertDialog.Builder(context)
                    .setTitle("Playlist Repair Complete")
                    .setMessage("Successfully processed " + playlists.size() + " playlists.\n\n" +
                               "All track data has been recovered where possible. " +
                               "Your playlists should now display tracks correctly.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        // Refresh the library to show changes
                        refreshLibraryFragment(context);
                    })
                    .show();
            }
            
            @Override
            public void onError(String errorMessage) {
                // Dismiss the progress dialog
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                
                // Show error dialog
                new AlertDialog.Builder(context)
                    .setTitle("Repair Failed")
                    .setMessage("Error repairing playlists: " + errorMessage)
                    .setPositiveButton("OK", null)
                    .show();
            }
        });
    }
    
    /**
     * Show all tracks in the database for debugging purposes
     * @param context Context for showing toast messages and dialogs
     */
    public static void showAllTracks(Context context) {
        if (context == null) {
            return;
        }
        
        // Show a progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(context)
            .setTitle("Loading Tracks")
            .setMessage("Retrieving all tracks from database...")
            .setCancelable(false)
            .show();
        
        // Get PlaylistManager instance
        PlaylistManager playlistManager = PlaylistManager.getInstance(context);
        
        // Get all tracks from database
        playlistManager.getAllTracks(new PlaylistManager.TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                // Dismiss the progress dialog
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                
                if (tracks.isEmpty()) {
                    // No tracks found
                    new AlertDialog.Builder(context)
                        .setTitle("No Tracks Found")
                        .setMessage("No tracks were found in the database.")
                        .setPositiveButton("OK", null)
                        .show();
                    return;
                }
                
                // Build a message with all track info
                StringBuilder message = new StringBuilder();
                message.append("Found ").append(tracks.size()).append(" tracks:\n\n");
                
                for (int i = 0; i < tracks.size(); i++) {
                    Track track = tracks.get(i);
                    message.append(i+1).append(". ")
                           .append(track.getTitle())
                           .append(" by ")
                           .append(track.getArtist())
                           .append(" (ID: ")
                           .append(track.getTrackId())
                           .append(")\n");
                }
                
                // Create scrolling dialog to show all tracks
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("All Tracks in Database");
                
                // Use a ScrollView to handle large lists
                ScrollView scrollView = new ScrollView(context);
                TextView textView = new TextView(context);
                textView.setText(message.toString());
                textView.setPadding(30, 30, 30, 30);
                scrollView.addView(textView);
                
                builder.setView(scrollView);
                builder.setPositiveButton("OK", null);
                builder.show();
            }
            
            @Override
            public void onError(String errorMessage) {
                // Dismiss the progress dialog
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                
                // Show error dialog
                new AlertDialog.Builder(context)
                    .setTitle("Error")
                    .setMessage("Failed to retrieve tracks: " + errorMessage)
                    .setPositiveButton("OK", null)
                    .show();
            }
        });
    }

    /**
     * Shows a dialog to invite collaborators to a collaborative playlist
     * @param context Context for showing dialog and toast messages
     * @param playlist The collaborative playlist to invite collaborators to
     */
    public static void showInviteCollaboratorsDialog(Context context, Playlist playlist) {
        if (context == null || playlist == null || playlist.getPlaylistId() == null) {
            if (context != null) {
                Toast.makeText(context, "Invalid playlist", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (!playlist.isCollaborative()) {
            Toast.makeText(context, "Only collaborative playlists can have collaborators", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create dialog for adding collaborators
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogTheme);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_invite_collaborators, null);
        builder.setView(dialogView);

        // Create the dialog
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Get views
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvDescription = dialogView.findViewById(R.id.tvDialogDescription);
        Button btnCopyLink = dialogView.findViewById(R.id.btnCopyInviteLink);
        Button btnInviteContacts = dialogView.findViewById(R.id.btnInviteContacts);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        // Set up text
        tvTitle.setText("Invite to " + playlist.getName());
        tvDescription.setText("Invite friends to collaborate on this playlist. They'll be able to add, remove, and vote on tracks.");

        // Create a unique shareable link for this playlist
        String shareableLink = "https://bananamusic.app/join?playlist=" + playlist.getPlaylistId();

        // Set up Copy Link button
        btnCopyLink.setOnClickListener(v -> {
            // Copy link to clipboard
            android.content.ClipboardManager clipboard = 
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = 
                android.content.ClipData.newPlainText("Playlist Invite Link", shareableLink);
            clipboard.setPrimaryClip(clip);
            
            Toast.makeText(context, "Invite link copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // Set up Invite Contacts button
        btnInviteContacts.setOnClickListener(v -> {
            // Create share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Join my playlist on Banana Music");
            shareIntent.putExtra(Intent.EXTRA_TEXT, 
                "Join my collaborative playlist \"" + playlist.getName() + "\" on Banana Music: " + shareableLink);
            context.startActivity(Intent.createChooser(shareIntent, "Invite via"));
            
            dialog.dismiss();
        });

        // Set up close button
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Show dialog
        dialog.show();
    }

    /**
     * Join a collaborative playlist using its ID
     * @param context Context for showing dialog and toast messages
     * @param playlistId The ID of the playlist to join
     */
    public static void joinCollaborativePlaylist(Context context, String playlistId) {
        if (context == null || playlistId == null || playlistId.isEmpty()) {
            Toast.makeText(context, "Invalid playlist information", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading indicator
        Toast.makeText(context, "Finding playlist...", Toast.LENGTH_SHORT).show();
        
        // Get current user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(context, "You need to be logged in to join a playlist", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get playlist using PlaylistManager
        PlaylistManager playlistManager = PlaylistManager.getInstance(context);
        playlistManager.getPlaylist(playlistId, new PlaylistManager.PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                // Check if the playlist is collaborative
                if (!playlist.isCollaborative()) {
                    Toast.makeText(context, "This is not a collaborative playlist", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Check if user is already a collaborator
                if (playlist.getCollaborators() != null && 
                    playlist.getCollaborators().contains(currentUser.getUid())) {
                    Toast.makeText(context, "You are already a collaborator on this playlist", Toast.LENGTH_SHORT).show();
                    
                    // Show the playlist details
                    showPlaylistDetails(context, playlist);
                    return;
                }
                
                // Confirm joining the playlist
                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogTheme);
                builder.setTitle("Join Collaborative Playlist")
                       .setMessage("Would you like to join \"" + playlist.getName() + "\"?\n\n" +
                                   "You'll be able to add, remove, and vote on tracks.")
                       .setPositiveButton("Join", (dialog, which) -> {
                           // Add user as collaborator
                           playlistManager.addCollaboratorToPlaylist(playlistId, currentUser.getUid(), 
                               new PlaylistManager.PlaylistCallback() {
                                   @Override
                                   public void onSuccess(Playlist updatedPlaylist) {
                                       Toast.makeText(context, "You've joined " + updatedPlaylist.getName(), 
                                                     Toast.LENGTH_SHORT).show();
                                       
                                       // Show the playlist details
                                       showPlaylistDetails(context, updatedPlaylist);
                                       
                                       // Refresh library if in MainActivity
                                       if (context instanceof MainActivity) {
                                           refreshLibraryFragment(context);
                                       }
                                   }
                                   
                                   @Override
                                   public void onError(String errorMessage) {
                                       Toast.makeText(context, "Error joining playlist: " + errorMessage, 
                                                     Toast.LENGTH_SHORT).show();
                                   }
                               });
                       })
                       .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                       .show();
            }
            
            @Override
            public void onError(String errorMessage) {
                Toast.makeText(context, "Error finding playlist: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Process a playlist invite link/URL
     * @param context Context for showing dialog and toast messages
     * @param inviteUrl The URL to process
     * @return true if the URL was a valid playlist invite link
     */
    public static boolean processInviteLink(Context context, String inviteUrl) {
        if (context == null || inviteUrl == null || inviteUrl.isEmpty()) {
            return false;
        }
        
        // Extract playlist ID from URL
        // Expected format: https://bananamusic.app/join?playlist=PLAYLIST_ID
        if (inviteUrl.contains("bananamusic.app/join") && inviteUrl.contains("playlist=")) {
            String playlistId = inviteUrl.substring(inviteUrl.indexOf("playlist=") + 9);
            
            // Remove any extra URL parameters
            if (playlistId.contains("&")) {
                playlistId = playlistId.substring(0, playlistId.indexOf("&"));
            }
            
            // Join the playlist
            joinCollaborativePlaylist(context, playlistId);
            return true;
        }
        
        return false;
    }

    /**
     * Update the love button icon based on whether the playlist is in the user's library
     */
    private static void updateLoveButtonState(Context context, ImageButton loveButton, Playlist playlist) {
        boolean isInLibrary = isPlaylistInLibrary(context, playlist.getPlaylistId());
        
        // Update button icon
        loveButton.setImageResource(isInLibrary ? 
                R.drawable.ic_favorite : 
                R.drawable.ic_favorite_border);
        
        // Update button tint color
        loveButton.setColorFilter(context.getResources().getColor(
                isInLibrary ? R.color.colorAccent : android.R.color.white));
    }
    
    /**
     * Toggle the playlist's saved state in the user's library
     */
    private static void togglePlaylistInLibrary(Context context, Playlist playlist, ImageButton loveButton) {
        String playlistId = playlist.getPlaylistId();
        boolean isCurrentlyInLibrary = isPlaylistInLibrary(context, playlistId);
        
        if (isCurrentlyInLibrary) {
            // Remove from library
            removePlaylistFromLibrary(context, playlistId);
            Toast.makeText(context, "Removed from your library", Toast.LENGTH_SHORT).show();
        } else {
            // Add to library
            addPlaylistToLibrary(context, playlistId);
            Toast.makeText(context, "Added to your library", Toast.LENGTH_SHORT).show();
        }
        
        // Update button state
        updateLoveButtonState(context, loveButton, playlist);
        
        // Refresh the LibraryFragment to reflect changes
        refreshLibraryFragment(context);
    }
    
    /**
     * Check if a playlist is in the user's library
     */
    private static boolean isPlaylistInLibrary(Context context, String playlistId) {
        // Check locally saved preferences first
        UserPreferences userPrefs = UserPreferences.getInstance(context);
        if (userPrefs.getSavedPlaylists().contains(playlistId)) {
            return true;
        }
        
        // If user is logged in, also check Firebase
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // We don't have a direct way to synchronously check Firebase,
            // so we rely on the local cache for the UI
            return false;
        }
        
        return false;
    }
    
    /**
     * Add a playlist to the user's library
     */
    private static void addPlaylistToLibrary(Context context, String playlistId) {
        // Save locally
        try {
            UserPreferences userPrefs = UserPreferences.getInstance(context);
            userPrefs.savePlaylist(playlistId);
            
            // If user is logged in, also save to Firebase
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                try {
                    PlaylistManager playlistManager = PlaylistManager.getInstance(context);
                    playlistManager.getPlaylist(playlistId, new PlaylistManager.PlaylistCallback() {
                        @Override
                        public void onSuccess(Playlist playlist) {
                            try {
                                // Add to user's Firebase saved playlists
                                FirebaseFirestore db = FirebaseFirestore.getInstance();
                                db.collection(FirebaseConfig.COLLECTION_USERS)
                                        .document(currentUser.getUid())
                                        .update("savedPlaylists", FieldValue.arrayUnion(playlistId))
                                        .addOnSuccessListener(aVoid -> 
                                                Log.d("PlaylistDetailsHelper", "Added playlist to user's library in Firebase"))
                                        .addOnFailureListener(e -> {
                                            Log.e("PlaylistDetailsHelper", "Error adding playlist to library in Firebase", e);
                                            // Try to create the document if it doesn't exist
                                            createUserDocumentWithPlaylist(db, currentUser.getUid(), playlistId);
                                        });
                            } catch (Exception e) {
                                Log.e("PlaylistDetailsHelper", "Error updating Firebase: " + e.getMessage(), e);
                            }
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            Log.e("PlaylistDetailsHelper", "Error getting playlist: " + errorMessage);
                        }
                    });
                } catch (Exception e) {
                    Log.e("PlaylistDetailsHelper", "Error with PlaylistManager: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            Log.e("PlaylistDetailsHelper", "Error adding playlist to library: " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to create a user document with a saved playlist if it doesn't exist
     */
    private static void createUserDocumentWithPlaylist(FirebaseFirestore db, String userId, String playlistId) {
        try {
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", userId);
            userData.put("savedPlaylists", Collections.singletonList(playlistId));
            
            db.collection(FirebaseConfig.COLLECTION_USERS)
                    .document(userId)
                    .set(userData)
                    .addOnSuccessListener(aVoid -> 
                            Log.d("PlaylistDetailsHelper", "Created new user document with playlist"))
                    .addOnFailureListener(e -> 
                            Log.e("PlaylistDetailsHelper", "Error creating user document", e));
        } catch (Exception e) {
            Log.e("PlaylistDetailsHelper", "Error creating user document: " + e.getMessage(), e);
        }
    }
    
    /**
     * Remove a playlist from the user's library
     */
    private static void removePlaylistFromLibrary(Context context, String playlistId) {
        try {
            // Remove locally
            UserPreferences userPrefs = UserPreferences.getInstance(context);
            userPrefs.removePlaylist(playlistId);
            
            // If user is logged in, also remove from Firebase
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                try {
                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    db.collection(FirebaseConfig.COLLECTION_USERS)
                            .document(currentUser.getUid())
                            .update("savedPlaylists", FieldValue.arrayRemove(playlistId))
                            .addOnSuccessListener(aVoid -> 
                                    Log.d("PlaylistDetailsHelper", "Removed playlist from user's library in Firebase"))
                            .addOnFailureListener(e -> {
                                Log.e("PlaylistDetailsHelper", "Error removing playlist from library in Firebase", e);
                                // If the document doesn't exist, this is fine - it's already not saved
                            });
                } catch (Exception e) {
                    Log.e("PlaylistDetailsHelper", "Error with Firebase: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            Log.e("PlaylistDetailsHelper", "Error removing playlist from library: " + e.getMessage(), e);
        }
    }

    /**
     * Format track duration in milliseconds to human-readable format (mm:ss)
     * @param durationMs Duration in milliseconds
     * @return Formatted duration string
     */
    private static String formatDuration(long durationMs) {
        long minutes = (durationMs / 1000) / 60;
        long seconds = (durationMs / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Shows collaborators dialog
     */
    private static void showCollaboratorsDialog(Context context, Playlist playlist, List<Track> collaborators) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Collaborators");
        
        if (collaborators == null || collaborators.isEmpty()) {
            builder.setMessage("No collaborators yet");
        } else {
            StringBuilder message = new StringBuilder();
            for (Track collaborator : collaborators) {
                message.append("• ").append(collaborator.getTitle()).append("\n");
            }
            builder.setMessage(message.toString());
        }
        
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private void handleCollaborativePlaylist(Context context, Playlist playlist) {
        if (playlist.isCollaborative()) {
            if (apiProvider == null) {
                apiProvider = ApiDataProvider.getInstance(context);
            }
            apiProvider.getCollaborators(playlist.getPlaylistId(), CallbackAdapter.toApiDataProviderCallback(new TracksCallback() {
                @Override
                public void onSuccess(List<Track> collaborators) {
                    showCollaboratorsDialog(context, playlist, collaborators);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(context, "Error loading collaborators: " + message, Toast.LENGTH_SHORT).show();
                }
            }));
        }
    }

    private void inviteCollaborator(Context context, Playlist playlist, String userId) {
        if (apiProvider == null) {
            apiProvider = ApiDataProvider.getInstance(context);
        }
        apiProvider.inviteCollaborator(playlist.getPlaylistId(), userId, CallbackAdapter.toApiDataProviderCallback(new TracksCallback() {
            @Override
            public void onSuccess(List<Track> result) {
                Toast.makeText(context, "Successfully invited collaborator", Toast.LENGTH_SHORT).show();
                handleCollaborativePlaylist(context, playlist);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(context, "Error inviting collaborator: " + message, Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void addTrackToLibrary(Track track, TracksCallback callback) {
        apiProvider.addTrackToLibrary(track, CallbackAdapter.toApiProviderCallback(callback));
    }

    public void getPlaylistTracks(Playlist playlist, TracksCallback callback) {
        apiProvider.getPlaylistTracks(playlist.getPlaylistId(), CallbackAdapter.toApiProviderCallback(callback));
    }
} 