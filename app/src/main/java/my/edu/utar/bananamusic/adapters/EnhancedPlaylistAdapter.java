package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.PlaylistDetailsHelper;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.RecyclerViewSafety;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;

/**
 * Enhanced Playlist Adapter that shows track previews in each playlist item
 */
public class EnhancedPlaylistAdapter extends RecyclerView.Adapter<EnhancedPlaylistAdapter.PlaylistViewHolder> 
        implements RecyclerViewSafety.ClearableAdapter {

    private static final String TAG = "EnhancedPlaylistAdapter";
    private final List<Playlist> playlists;
    private final Context context;
    private final PlaylistClickListener listener;
    private final PlaylistManager playlistManager;

    public interface PlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
        void onTrackClick(Track track);
    }

    public EnhancedPlaylistAdapter(Context context, List<Playlist> playlists, PlaylistClickListener listener) {
        this.context = context.getApplicationContext();
        this.playlists = playlists != null ? playlists : new ArrayList<>();
        this.listener = listener;
        this.playlistManager = PlaylistManager.getInstance(context);
    }

    @Override
    public void clearData() {
        // Clean up any active listeners
        cleanupListeners();
        
        if (playlists != null) {
            int size = playlists.size();
            if (size > 0) {
                playlists.clear();
                notifyItemRangeRemoved(0, size);
            }
        }
    }

    /**
     * Clean up all active listeners to prevent memory leaks
     */
    public void cleanupListeners() {
        if (playlists != null) {
            for (int i = 0; i < getItemCount(); i++) {
                RecyclerView.ViewHolder holder = null;
                try {
                    // We don't have direct access to ViewHolders, but we can clean up our data
                    // that might have listeners attached. In a real implementation, we would
                    // need to ensure all listeners are properly removed.
                    
                    // Note: In a production app, we might want to keep track of all registered
                    // listeners separately so we can clean them up properly here.
                } catch (Exception e) {
                    // Safely ignore any errors while cleaning up
                }
            }
        }
        
        // For this specific adapter, we know that PlaylistViewHolder has trackListener
        // that needs to be cleaned up, so we'll notify the PlaylistManager to remove all
        // listeners for these playlists
        if (playlists != null && playlistManager != null) {
            for (Playlist playlist : playlists) {
                if (playlist != null && playlist.getPlaylistId() != null) {
                    playlistManager.removePlaylistListener(playlist.getPlaylistId());
                }
            }
        }
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist_with_tracks, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        holder.bind(playlist);
    }

    @Override
    public int getItemCount() {
        return playlists != null ? playlists.size() : 0;
    }

    public void setPlaylists(List<Playlist> newPlaylists) {
        this.playlists.clear();
        if (newPlaylists != null) {
            this.playlists.addAll(newPlaylists);
        }
        notifyDataSetChanged();
    }

    /**
     * Update adapter data with a new list of playlists
     */
    public void updateData(List<Playlist> newPlaylists) {
        this.playlists.clear();
        if (newPlaylists != null) {
            this.playlists.addAll(newPlaylists);
        }
        notifyDataSetChanged();
    }

    /**
     * Add a playlist to the adapter
     */
    public void addPlaylist(Playlist playlist) {
        if (playlist != null) {
            this.playlists.add(0, playlist);
            notifyItemInserted(0);
        }
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivPlaylistCover;
        private final TextView tvPlaylistName;
        private final TextView tvCreator;
        private final TextView tvMoodTag;
        private final TextView tvTrackCount;
        private final FloatingActionButton fabPlay;
        private final View[] trackViews = new View[3];
        private final ImageButton btnLovePlaylist;
        private ListenerRegistration trackListener;
        private View trackContainer;

        PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            
            // Find views
            ivPlaylistCover = itemView.findViewById(R.id.ivPlaylistCover);
            tvPlaylistName = itemView.findViewById(R.id.tvPlaylistName);
            tvCreator = itemView.findViewById(R.id.tvCreator);
            tvMoodTag = itemView.findViewById(R.id.tvMoodTag);
            tvTrackCount = itemView.findViewById(R.id.tvTrackCount);
            fabPlay = itemView.findViewById(R.id.fabPlay);
            btnLovePlaylist = itemView.findViewById(R.id.btnLovePlaylist);
            
            // Initialize track preview views
            trackViews[0] = itemView.findViewById(R.id.trackPreview1);
            trackViews[1] = itemView.findViewById(R.id.trackPreview2);
            trackViews[2] = itemView.findViewById(R.id.trackPreview3);
            
            // Initialize track container
            trackContainer = itemView.findViewById(R.id.trackPreviewContainer);

            // Set click listeners
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlaylistClick(playlists.get(position));
                }
            });

            // Set up love button click listener
            btnLovePlaylist.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Playlist playlist = playlists.get(position);
                    togglePlaylistInLibrary(playlist);
                }
            });

            fabPlay.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    // Apply pulse animation to the play button
                    Animation pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.pulse_animation);
                    fabPlay.startAnimation(pulseAnimation);
                    
                    // Get the playlist
                    Playlist playlist = playlists.get(position);
                    
                    // Notify listener after a slight delay for better visual feedback
                    new Handler().postDelayed(() -> {
                        // Get tracks for the playlist to start playback
                        playlistManager.getPlaylistTracks(playlist.getPlaylistId(), 
                            new TracksCallback() {
                                @Override
                                public void onSuccess(List<Track> tracks) {
                                    if (tracks != null && !tracks.isEmpty()) {
                                        // Get the first track
                                        Track firstTrack = tracks.get(0);
                                        
                                        // Set track source to Deezer if not specified
                                        if (firstTrack.getSource() == null || firstTrack.getSource().isEmpty()) {
                                            firstTrack.setSource(Track.SOURCE_DEEZER);
                                        }
                                        
                                        try {
                                            // Play the track with AudioPlayerHelper
                                            AudioPlayerHelper audioPlayerHelper = AudioPlayerHelper.getInstance(context);
                                            audioPlayerHelper.playTrack(firstTrack);
                                            
                                            // Show toast message
                                            Toast.makeText(context, "Playing: " + playlist.getName(), Toast.LENGTH_SHORT).show();
                                            
                                            // Also notify the playlist click listener to update mini player
                                            listener.onPlaylistClick(playlist);
                                        } catch (Exception e) {
                                            Toast.makeText(context, "Error playing playlist: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    } else {
                                        // No tracks found, just open playlist details
                                        Toast.makeText(context, "No tracks in this playlist", Toast.LENGTH_SHORT).show();
                                        listener.onPlaylistClick(playlist);
                                    }
                                }
                                
                                @Override
                                public void onError(String errorMessage) {
                                    // Error getting tracks, just open playlist details
                                    Toast.makeText(context, "Error loading tracks", Toast.LENGTH_SHORT).show();
                                    listener.onPlaylistClick(playlist);
                                }
                            });
                    }, 150);
                }
            });

            // Add hover effects for better user feedback
            fabPlay.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        fabPlay.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        fabPlay.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        break;
                }
                return false;
            });
        }

        void bind(Playlist playlist) {
            // Clear previous track listener
            if (trackListener != null) {
                trackListener.remove();
                trackListener = null;
            }
            
            // Set basic playlist info
            tvPlaylistName.setText(playlist.getName());
            
            if (playlist.getCreatorName() != null && !playlist.getCreatorName().isEmpty()) {
                tvCreator.setText(playlist.getCreatorName());
            } else if (playlist.getDescription() != null && !playlist.getDescription().isEmpty()) {
                tvCreator.setText(playlist.getDescription());
            } else {
                tvCreator.setText("Created by you");
            }
            
            // Update love button state
            updateLoveButtonState(playlist);
            
            // Set track count
            int trackCount = playlist.getTrackIds() != null ? playlist.getTrackIds().size() : 0;
            tvTrackCount.setText(String.format(Locale.getDefault(), "%d track%s", 
                    trackCount, trackCount == 1 ? "" : "s"));
            
            // Set mood tag if available
            if (playlist.getMood() != null && !playlist.getMood().isEmpty()) {
                tvMoodTag.setVisibility(View.VISIBLE);
                tvMoodTag.setText(playlist.getMood());
                
                // Set mood tag color based on mood
                int colorResId;
                switch (playlist.getMood().toLowerCase()) {
                    case "happy":
                        colorResId = R.color.colorHappy;
                        break;
                    case "sad":
                        colorResId = R.color.colorSad;
                        break;
                    case "energetic":
                        colorResId = R.color.colorEnergetic;
                        break;
                    case "relaxed":
                        colorResId = R.color.colorRelaxed;
                        break;
                    case "romantic":
                        colorResId = R.color.colorAccent;
                        break;
                    default:
                        colorResId = R.color.colorPrimary;
                        break;
                }
                tvMoodTag.setBackgroundTintList(context.getColorStateList(colorResId));
            } else {
                tvMoodTag.setVisibility(View.GONE);
            }
            
            // Load playlist cover image
            if (playlist.getCoverImageUrl() != null && !playlist.getCoverImageUrl().isEmpty()) {
                Glide.with(context)
                    .load(playlist.getCoverImageUrl())
                    .apply(RequestOptions.centerCropTransform())
                    .placeholder(R.drawable.playlist_placeholder)
                    .error(R.drawable.playlist_placeholder)
                    .into(ivPlaylistCover);
            } else if (playlist.getCoverImageResourceId() != 0) {
                ivPlaylistCover.setImageResource(playlist.getCoverImageResourceId());
            } else {
                ivPlaylistCover.setImageResource(R.drawable.playlist_placeholder);
            }
            
            // Load track previews
            loadTrackPreviews(playlist);
        }
        
        /**
         * Load track previews for this playlist
         */
        private void loadTrackPreviews(Playlist playlist) {
            if (playlist == null || playlist.getPlaylistId() == null) {
                return;
            }

            // Get tracks for the playlist
            playlistManager.getPlaylistTracks(playlist.getPlaylistId(), 
                new TracksCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        if (tracks == null || tracks.isEmpty()) {
                            // Hide track previews if no tracks
                            new Handler(Looper.getMainLooper()).post(() -> {
                                try {
                                    if (trackContainer != null) {
                                        trackContainer.setVisibility(View.GONE);
                                    }
                                    for (View trackView : trackViews) {
                                        if (trackView != null) {
                                            trackView.setVisibility(View.GONE);
                                        }
                                    }
                                } catch (Exception e) {
                                    // Safely handle any exceptions
                                    Log.e(TAG, "Error updating track previews", e);
                                }
                            });
                            return;
                        }

                        // Update track previews on main thread
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try {
                                // Show track container
                                if (trackContainer != null) {
                                    trackContainer.setVisibility(View.VISIBLE);
                                }

                                // Update up to 3 track previews
                                int previewCount = Math.min(tracks.size(), 3);
                                for (int i = 0; i < trackViews.length; i++) {
                                    if (i < previewCount) {
                                        Track track = tracks.get(i);
                                        setupTrackPreview(trackViews[i], track, i + 1, playlist);
                                        trackViews[i].setVisibility(View.VISIBLE);
                                    } else {
                                        trackViews[i].setVisibility(View.GONE);
                                    }
                                }
                            } catch (Exception e) {
                                // Safely handle any exceptions
                                Log.e(TAG, "Error setting up track previews", e);
                            }
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        // Hide track previews on error
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try {
                                if (trackContainer != null) {
                                    trackContainer.setVisibility(View.GONE);
                                }
                                for (View trackView : trackViews) {
                                    if (trackView != null) {
                                        trackView.setVisibility(View.GONE);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error hiding track previews", e);
                            }
                        });
                    }
                });
        }
        
        /**
         * Set up a single track preview view with track data
         */
        private void setupTrackPreview(View trackView, Track track, int trackNumber, Playlist playlist) {
            trackView.setVisibility(View.VISIBLE);
            
            // Find view components
            TextView tvTrackNumber = trackView.findViewById(R.id.tvTrackNumber);
            ImageView ivAlbumArt = trackView.findViewById(R.id.ivAlbumArt);
            TextView tvTrackTitle = trackView.findViewById(R.id.tvTrackTitle);
            TextView tvArtist = trackView.findViewById(R.id.tvArtist);
            TextView tvDuration = trackView.findViewById(R.id.tvDuration);
            ImageButton btnPlay = trackView.findViewById(R.id.btnPlay);
            
            // Set track data
            tvTrackNumber.setText(String.valueOf(trackNumber));
            tvTrackTitle.setText(track.getTitle());
            tvArtist.setText(track.getArtist());
            
            // Format duration for tracks
            long durationMs = track.getDuration();
            if (durationMs > 0) {
                long minutes = durationMs / 60000;
                long seconds = (durationMs % 60000) / 1000;
                tvDuration.setText(String.format(Locale.getDefault(), "%d:%02d", minutes, seconds));
            } else {
                tvDuration.setText("--:--");
            }
            
            // Clear any previous drawables (remove source icon/indicator)
            tvArtist.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            
            // Load album art
            if (track.getAlbumArtUrl() != null && !track.getAlbumArtUrl().isEmpty()) {
                Glide.with(context)
                    .load(track.getAlbumArtUrl())
                    .apply(RequestOptions.centerCropTransform())
                    .placeholder(R.drawable.ic_album_placeholder)
                    .error(R.drawable.ic_album_placeholder)
                    .into(ivAlbumArt);
            } else {
                ivAlbumArt.setImageResource(R.drawable.ic_album_placeholder);
            }
            
            // Set click listeners
            if (btnPlay != null) {
                btnPlay.setOnClickListener(v -> {
                    if (listener != null) {
                        // Prepare track for playback if source is not specified
                        if (track.getSource() == null || track.getSource().isEmpty()) {
                            track.setSource(Track.SOURCE_DEEZER);
                        }
                        
                        // Apply animation to play button
                        Animation pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.pulse_animation);
                        btnPlay.startAnimation(pulseAnimation);
                        
                        // Notify listener of track click after a slight delay for better visual feedback
                        new Handler().postDelayed(() -> {
                            listener.onTrackClick(track);
                        }, 150);
                    }
                });
            }
            
            // Set click listener for the whole track view
            trackView.setOnClickListener(v -> {
                if (listener != null) {
                    // Prepare track for playback if source is not specified
                    if (track.getSource() == null || track.getSource().isEmpty()) {
                        track.setSource(Track.SOURCE_DEEZER);
                    }
                    
                    // Notify listener of track click
                    listener.onTrackClick(track);
                }
            });
        }

        private void updateLoveButtonState(Playlist playlist) {
            if (playlist == null || btnLovePlaylist == null) {
                return;
            }
            
            boolean isCollected = false;
            
            if (playlist.getPlaylistId() != null) {
                // Use the playlist manager to check if it's collected
                PlaylistManager playlistManager = PlaylistManager.getInstance(context);
                isCollected = playlistManager.isPlaylistCollected(playlist.getPlaylistId());
                
                // Update the playlist object in our data
                playlist.setCollected(isCollected);
            }
            
            // Set the appropriate icon
            btnLovePlaylist.setImageResource(isCollected 
                ? R.drawable.ic_favorite 
                : R.drawable.ic_favorite_border);
        }
        
        private boolean isPlaylistInLibrary(String playlistId) {
            if (playlistId == null) {
                return false;
            }
            
            // Use the playlist manager to check
            PlaylistManager playlistManager = PlaylistManager.getInstance(context);
            return playlistManager.isPlaylistCollected(playlistId);
        }
        
        private void togglePlaylistInLibrary(Playlist playlist) {
            if (playlist == null || playlist.getPlaylistId() == null) {
                return;
            }
            
            String playlistId = playlist.getPlaylistId();
            boolean currentlyInLibrary = isPlaylistInLibrary(playlistId);
            
            if (currentlyInLibrary) {
                // Remove from library
                playlistManager.removeFromLibrary(playlistId, 
                    new PlaylistManager.PlaylistCallback() {
                        @Override
                        public void onSuccess(Playlist updatedPlaylist) {
                            // Update button status upon removal
                            btnLovePlaylist.setImageResource(R.drawable.ic_favorite_border);
                            playlist.setCollected(false);
                            
                            // Notify user if needed
                            if (context instanceof my.edu.utar.bananamusic.MainActivity) {
                                ((my.edu.utar.bananamusic.MainActivity) context)
                                    .showToast("Removed from your library");
                            }
                        }
                        
                        @Override
                        public void onError(String message) {
                            // Show error
                            if (context instanceof my.edu.utar.bananamusic.MainActivity) {
                                ((my.edu.utar.bananamusic.MainActivity) context)
                                    .showToast("Error removing playlist: " + message);
                            }
                        }
                    });
            } else {
                // Add to library
                playlistManager.addToLibrary(playlist, 
                    new PlaylistManager.PlaylistCallback() {
                        @Override
                        public void onSuccess(Playlist updatedPlaylist) {
                            // Update button status upon addition
                            btnLovePlaylist.setImageResource(R.drawable.ic_favorite);
                            playlist.setCollected(true);
                            
                            // Notify user if needed
                            if (context instanceof my.edu.utar.bananamusic.MainActivity) {
                                ((my.edu.utar.bananamusic.MainActivity) context)
                                    .showToast("Added to your library");
                            }
                        }
                        
                        @Override
                        public void onError(String message) {
                            // Show error
                            if (context instanceof my.edu.utar.bananamusic.MainActivity) {
                                ((my.edu.utar.bananamusic.MainActivity) context)
                                    .showToast("Error adding playlist: " + message);
                            }
                        }
                    });
            }
        }

        /**
         * Clean up any resources when view holder is recycled
         */
        void cleanup() {
            if (trackListener != null) {
                trackListener.remove();
                trackListener = null;
            }
        }
    }
} 