package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton;
import android.view.animation.AnimationUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.List;
import java.util.ArrayList;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.RecyclerViewSafety;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

/**
 * Adapter for displaying recommended tracks in a RecyclerView
 */
public class RecommendedAdapter extends RecyclerView.Adapter<RecommendedAdapter.ViewHolder> implements RecyclerViewSafety.ClearableAdapter {
    
    private static final String TAG = "RecommendedAdapter";
    private final Context context;
    private List<Track> tracks;
    private final OnTrackClickListener listener;
    
    public interface OnTrackClickListener {
        void onTrackClick(Track track);
        default void onPlayButtonClick(Track track, int position) {
            onTrackClick(track);
        }
        default void onShareTrack(Track track) {}
    }
    
    public RecommendedAdapter(Context context, List<Track> tracks, OnTrackClickListener listener) {
        this.context = context;
        this.tracks = tracks != null ? tracks : new ArrayList<>();
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.bind(track);
    }
    
    @Override
    public int getItemCount() {
        return tracks != null ? tracks.size() : 0;
    }
    
    public boolean isEmpty() {
        return tracks == null || tracks.isEmpty();
    }
    
    public void updateData(List<Track> newTracks) {
        if (newTracks != null) {
            this.tracks = newTracks;
            notifyDataSetChanged();
            Log.d(TAG, "Updated tracks data with " + newTracks.size() + " items");
        } else {
            Log.w(TAG, "Attempted to update with null track data");
        }
    }
    
    public void addData(List<Track> newTracks) {
        if (newTracks != null) {
            int startPosition = this.tracks.size();
            this.tracks.addAll(newTracks);
            notifyItemRangeInserted(startPosition, newTracks.size());
            Log.d(TAG, "Added " + newTracks.size() + " new tracks");
        } else {
            Log.w(TAG, "Attempted to add null track data");
        }
    }
    
    /**
     * Clear the adapter data safely - implements ClearableAdapter interface
     */
    @Override
    public void clearData() {
        if (tracks != null) {
            int size = tracks.size();
            if (size > 0) {
                tracks.clear();
                notifyItemRangeRemoved(0, size);
            }
        }
    }

    // Add method for compatibility with other adapters
    public void setTracks(List<Track> newTracks) {
        updateData(newTracks);
    }
    
    /**
     * Update the tracks in the adapter
     * @param newTracks List of new tracks to display
     */
    public void updateTracks(List<Track> newTracks) {
        if (newTracks == null) return;
        
        tracks.clear();
        tracks.addAll(newTracks);
        notifyDataSetChanged();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivTrackCover = null;
        private TextView tvTrackName = null;
        private TextView tvArtistName = null;
        private ImageButton btnPlayPause = null;
        private ImageButton btnShare = null;
        private Chip chipComingSoon = null;
        private View itemOverlay = null;
        
        ViewHolder(View itemView) {
            super(itemView);
            initializeViews(itemView);
        }

        private void initializeViews(View itemView) {
            try {
                // Find views from the layout
                ivTrackCover = itemView.findViewById(R.id.ivTrackCover);
                tvTrackName = itemView.findViewById(R.id.tvTrackName);
                tvArtistName = itemView.findViewById(R.id.tvArtistName);
                btnPlayPause = itemView.findViewById(R.id.btnPlayPause);
                btnShare = itemView.findViewById(R.id.btnShare);
                chipComingSoon = itemView.findViewById(R.id.chipComingSoon);
                itemOverlay = itemView.findViewById(R.id.itemOverlay);
                
                // Log errors if views are null
                if (tvTrackName == null) {
                    Log.e(TAG, "title TextView is null");
                }
                if (tvArtistName == null) {
                    Log.e(TAG, "artist TextView is null");
                }
                if (ivTrackCover == null) {
                    Log.e(TAG, "imageView ImageView is null");
                }
                
                // Set up hover/touch listeners for animations
                setupInteractionListeners();
                
                // Add share button if not exists
                if (btnShare == null) {
                    Log.d(TAG, "Share button not found in layout, might need layout update");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error finding views: " + e.getMessage());
            }
        }
        
        /**
         * Set up interaction listeners for hover and touch effects
         */
        private void setupInteractionListeners() {
            // Set up touch listeners for hover effects
            itemView.setOnHoverListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_HOVER_ENTER:
                        showInteractionControls(true);
                        return true;
                    case android.view.MotionEvent.ACTION_HOVER_EXIT:
                        showInteractionControls(false);
                        return true;
                }
                return false;
            });

            // Also handle touch events for mobile
            itemView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        showInteractionControls(true);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        showInteractionControls(false);
                        
                        // Handle click on ACTION_UP
                        if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                            int position = getAdapterPosition();
                            if (position != RecyclerView.NO_POSITION && listener != null) {
                                Track track = tracks.get(position);
                                // Only click if not "coming soon"
                                if (!track.isComingSoon()) {
                                    listener.onTrackClick(track);
                                }
                            }
                        }
                        break;
                }
                return true;
            });

            // Set up click listener for play button
            if (btnPlayPause != null) {
                btnPlayPause.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        Track track = tracks.get(position);
                        // Only play if not "coming soon"
                        if (!track.isComingSoon()) {
                            v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.button_press));
                            listener.onPlayButtonClick(track, position);
                        }
                    }
                });
            }
            
            // Set up click listener for share button
            if (btnShare != null) {
                btnShare.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        Track track = tracks.get(position);
                        v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.button_press));
                        
                        // Check if listener has implemented onShareTrack
                        if (track.isSharable()) {
                            // Try calling the listener first
                            listener.onShareTrack(track);
                            
                            // Also use the standard Android share dialog
                            shareTrack(track);
                        }
                    }
                });
            }
        }
        
        /**
         * Show or hide the interaction controls with animation
         */
        void showInteractionControls(boolean show) {
            // Cancel any running animations
            if (btnPlayPause != null) btnPlayPause.animate().cancel();
            if (btnShare != null) btnShare.animate().cancel();
            
            if (show) {
                // Show controls with scale animation
                float targetScale = 1.0f;
                int duration = 200;
                
                if (btnPlayPause != null) {
                    btnPlayPause.setVisibility(View.VISIBLE);
                    btnPlayPause.setScaleX(0f);
                    btnPlayPause.setScaleY(0f);
                    btnPlayPause.animate()
                            .scaleX(targetScale)
                            .scaleY(targetScale)
                            .setDuration(duration)
                            .start();
                }
                
                if (btnShare != null) {
                    btnShare.setVisibility(View.VISIBLE);
                    btnShare.setScaleX(0f);
                    btnShare.setScaleY(0f);
                    btnShare.animate()
                            .scaleX(targetScale)
                            .scaleY(targetScale)
                            .setDuration(duration)
                            .start();
                }
            } else {
                // Hide controls with fade out animation
                int duration = 200;
                
                if (btnPlayPause != null) {
                    btnPlayPause.animate()
                            .scaleX(0f)
                            .scaleY(0f)
                            .setDuration(duration)
                            .withEndAction(() -> btnPlayPause.setVisibility(View.INVISIBLE))
                            .start();
                }
                
                if (btnShare != null) {
                    btnShare.animate()
                            .scaleX(0f)
                            .scaleY(0f)
                            .setDuration(duration)
                            .withEndAction(() -> btnShare.setVisibility(View.INVISIBLE))
                            .start();
                }
            }
        }
        
        /**
         * Share a track using Android's share intent
         */
        private void shareTrack(Track track) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, track.getTitle() + " by " + track.getArtist());
            shareIntent.putExtra(Intent.EXTRA_TEXT, track.generateSharingMessage());
            
            if (itemView.getContext() != null) {
                itemView.getContext().startActivity(Intent.createChooser(shareIntent, "Share via"));
            }
        }
        
        void bind(Track track) {
            if (track == null) return;

            // Set text views with null checks
            if (tvTrackName != null) {
                tvTrackName.setText(track.getTitle());
            }
            if (tvArtistName != null) {
                tvArtistName.setText(track.getArtist());
            }
            
            // Handle "coming soon" status
            if (chipComingSoon != null) {
                if (track.isComingSoon()) {
                    chipComingSoon.setVisibility(View.VISIBLE);
                    
                    // Add a semi-transparent overlay to the card
                    if (itemOverlay != null) {
                        itemOverlay.setVisibility(View.VISIBLE);
                        itemOverlay.setAlpha(0.3f);
                    }
                } else {
                    chipComingSoon.setVisibility(View.GONE);
                    
                    // Remove the overlay
                    if (itemOverlay != null) {
                        itemOverlay.setVisibility(View.GONE);
                    }
                }
            }
            
            // Handle sharing button visibility
            if (btnShare != null) {
                btnShare.setVisibility(track.isSharable() ? View.INVISIBLE : View.GONE);
            }

            // Load image using Glide with null check
            if (ivTrackCover != null) {
                if (track.getAlbumArtUrl() != null && !track.getAlbumArtUrl().isEmpty()) {
                    try {
                        Glide.with(context)
                                .load(track.getAlbumArtUrl())
                                .placeholder(R.drawable.default_album_art)
                                .error(R.drawable.default_album_art)
                                .transition(DrawableTransitionOptions.withCrossFade())
                                .centerCrop()
                                .into(ivTrackCover);
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading image: " + e.getMessage());
                        ivTrackCover.setImageResource(R.drawable.default_album_art);
                    }
                } else {
                    ivTrackCover.setImageResource(R.drawable.default_album_art);
                }
            }
        }
    }
} 