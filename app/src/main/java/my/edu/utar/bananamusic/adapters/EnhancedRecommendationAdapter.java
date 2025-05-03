package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.ImageLoadHelper;

public class EnhancedRecommendationAdapter extends RecyclerView.Adapter<EnhancedRecommendationAdapter.ViewHolder> {
    private static final String TAG = "EnhancedRecAdapter";
    private final Context context;
    private List<Track> recommendations;
    private final OnTrackClickListener listener;
    private String currentMood = "happy"; // Add current mood field

    public interface OnTrackClickListener {
        void onTrackClick(Track track, int position);
        void onPlayButtonClick(Track track, int position);
        void onSaveButtonClick(Track track, int position);
    }

    public EnhancedRecommendationAdapter(Context context, List<Track> recommendations, OnTrackClickListener listener) {
        this.context = context;
        this.recommendations = recommendations != null ? recommendations : new ArrayList<>();
        this.listener = listener;
    }

    // Add method to update current mood
    public void setCurrentMood(String mood) {
        this.currentMood = mood.toLowerCase();
        notifyDataSetChanged(); // Refresh all cards to show new mood
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_enhanced_recommendation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= recommendations.size()) {
            Log.e(TAG, "Invalid position: " + position);
            return;
        }

        Track track = recommendations.get(position);
        if (track == null) {
            Log.e(TAG, "Null track at position: " + position);
            // Set placeholder content
            if (holder.titleText != null) holder.titleText.setText(R.string.placeholder_track_title);
            if (holder.artistText != null) holder.artistText.setText(R.string.placeholder_artist_name);
            if (holder.albumArt != null) holder.albumArt.setImageResource(R.drawable.default_album_art);
            if (holder.recommendationReason != null) holder.recommendationReason.setVisibility(View.GONE);
            return;
        }
        
        // Set basic track info
        if (holder.titleText != null) {
            holder.titleText.setText(track.getTitle() != null ? track.getTitle() : context.getString(R.string.unknown_track));
        }
        
        if (holder.artistText != null) {
            holder.artistText.setText(track.getArtist() != null ? track.getArtist() : context.getString(R.string.unknown_artist));
        }
        
        // Set recommendation reason if available
        if (holder.recommendationReason != null) {
            String reason = track.getRecommendationReason();
            if (reason != null && !reason.isEmpty()) {
                holder.recommendationReason.setVisibility(View.VISIBLE);
                holder.recommendationReason.setText(reason);
            } else {
                // Check for description as fallback
                String description = track.getDescription();
                if (description != null && !description.isEmpty()) {
                    holder.recommendationReason.setVisibility(View.VISIBLE);
                    holder.recommendationReason.setText(description);
                } else {
                    holder.recommendationReason.setVisibility(View.GONE);
                }
            }
        }
        
        // Load album art with enhanced helper
        if (holder.albumArt != null) {
            String albumArtUrl = track.getAlbumArtUrl();
            if (albumArtUrl != null && !albumArtUrl.isEmpty()) {
                Log.d(TAG, "Loading album art: " + albumArtUrl);
                ImageLoadHelper.loadRoundedImage(context, albumArtUrl, holder.albumArt, 
                        R.drawable.default_album_art, 16);
            } else {
                // Try using imageUrl as fallback
                String imageUrl = track.getImageUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Log.d(TAG, "Loading image URL: " + imageUrl);
                    ImageLoadHelper.loadRoundedImage(context, imageUrl, holder.albumArt, 
                            R.drawable.default_album_art, 16);
                } else {
                    // No art available, use placeholder
                    Log.d(TAG, "No album art available for: " + track.getTitle());
                    holder.albumArt.setImageResource(R.drawable.default_album_art);
                }
            }
        }
        
        // Apply current mood styling instead of track mood
        applyMoodStyling(holder, currentMood);
        
        // Set audio feature bars
        Map<String, Float> features = track.getAudioFeatures();
        if (features != null) {
            if (holder.energyBar != null) {
                updateFeatureBar(holder.energyBar, features.getOrDefault("energy", 0f));
            }
            if (holder.danceabilityBar != null) {
                updateFeatureBar(holder.danceabilityBar, features.getOrDefault("danceability", 0f));
            }
            if (holder.valenceBar != null) {
                updateFeatureBar(holder.valenceBar, features.getOrDefault("valence", 0f));
            }
        } else {
            // Set default values if no features available
            if (holder.energyBar != null) holder.energyBar.setProgress(50);
            if (holder.danceabilityBar != null) holder.danceabilityBar.setProgress(50);
            if (holder.valenceBar != null) holder.valenceBar.setProgress(50);
        }
        
        // Fallback for save button if it exists in the layout
        View saveButton = holder.itemView.findViewById(R.id.btnSave);
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSaveButtonClick(track, holder.getAdapterPosition());
                }
            });
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(track, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return recommendations != null ? recommendations.size() : 0;
    }
    
    private void updateFeatureBar(ProgressBar bar, float value) {
        if (bar != null) {
            // Convert 0-1 range to 0-100 for ProgressBar
            bar.setProgress(Math.round(value * 100));
        }
    }
    
    public void updateData(List<Track> tracks) {
        this.recommendations = tracks != null ? tracks : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    /**
     * Get the current list of tracks in the adapter
     * @return List of current tracks
     */
    public List<Track> getCurrentTracks() {
        return new ArrayList<>(recommendations);
    }

    /**
     * Apply mood-based styling to the recommendation card
     */
    private void applyMoodStyling(ViewHolder holder, String mood) {
        if (holder.moodBackground == null) {
            Log.e(TAG, "Cannot apply mood styling - moodBackground view is null");
            return;
        }
        
        Log.d(TAG, "Applying mood styling for mood: " + mood);
        
        // Set gradient background based on mood
        int backgroundRes;
        switch (mood.toLowerCase()) {
            case "party":
                backgroundRes = R.drawable.gradient_mood_party; // Purple-Pink gradient
                break;
            case "happy":
                backgroundRes = R.drawable.gradient_mood_happy; // Yellow-Orange gradient
                break;
            case "sad":
                backgroundRes = R.drawable.gradient_mood_sad; // Blue gradient
                break;
            case "energetic":
                backgroundRes = R.drawable.gradient_mood_energetic; // Red gradient
                break;
            case "relaxed":
            case "chill": // Map "chill" to relaxed gradient
                backgroundRes = R.drawable.gradient_mood_relaxed; // Green gradient
                break;
            case "focused":
            case "focus": // Map "focus" to focused gradient
                backgroundRes = R.drawable.gradient_mood_focused; // Purple gradient
                break;
            case "romantic":
                backgroundRes = R.drawable.gradient_mood_party; // Purple-Pink gradient for romantic
                break;
            default:
                Log.w(TAG, "Unknown mood: " + mood + ", using default gradient");
                backgroundRes = R.drawable.gradient_mood_happy;
                break;
        }
        
        // Apply background with fade animation
        holder.moodBackground.setAlpha(0f);
        holder.moodBackground.setBackgroundResource(backgroundRes);
        holder.moodBackground.animate()
            .alpha(1f)
            .setDuration(300)
            .start();
        
        // Set mood badge text with proper capitalization
        if (holder.currentMoodBadge != null) {
            String displayMood = mood.substring(0, 1).toUpperCase() + mood.substring(1).toLowerCase();
            holder.currentMoodBadge.setText(displayMood);
            holder.currentMoodBadge.setAlpha(0f);
            holder.currentMoodBadge.animate()
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(150)
                .start();
            Log.d(TAG, "Set mood badge text to: " + displayMood);
        } else {
            Log.e(TAG, "Cannot set mood badge text - currentMoodBadge view is null");
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView albumArt;
        final TextView titleText;
        final TextView artistText;
        final TextView recommendationReason;
        final TextView currentMoodBadge;
        final View moodBackground;
        final ProgressBar energyBar;
        final ProgressBar danceabilityBar;
        final ProgressBar valenceBar;
        // The saveButton may not exist in all layouts
        MaterialButton saveButton;

        ViewHolder(View itemView) {
            super(itemView);
            // Find views by ID, with null checks
            albumArt = itemView.findViewById(R.id.albumArt);
            titleText = itemView.findViewById(R.id.titleText);
            artistText = itemView.findViewById(R.id.artistText);
            recommendationReason = itemView.findViewById(R.id.recommendationReason);
            currentMoodBadge = itemView.findViewById(R.id.currentMoodBadge);
            moodBackground = itemView.findViewById(R.id.moodBackground);
            
            // Initialize progress bars
            energyBar = itemView.findViewById(R.id.energyBar);
            danceabilityBar = itemView.findViewById(R.id.danceabilityBar);
            valenceBar = itemView.findViewById(R.id.moodBar);
            
            saveButton = itemView.findViewById(R.id.btnSave); // May be null
            
            // Log missing views for debugging
            if (albumArt == null) Log.w(TAG, "albumArt view not found in layout");
            if (titleText == null) Log.w(TAG, "titleText view not found in layout");
            if (artistText == null) Log.w(TAG, "artistText view not found in layout");
            if (recommendationReason == null) Log.w(TAG, "recommendationReason view not found in layout");
            if (currentMoodBadge == null) Log.w(TAG, "currentMoodBadge view not found in layout");
            if (moodBackground == null) Log.w(TAG, "moodBackground view not found in layout");
            if (energyBar == null) Log.w(TAG, "energyBar view not found in layout");
            if (danceabilityBar == null) Log.w(TAG, "danceabilityBar view not found in layout");
            if (valenceBar == null) Log.w(TAG, "valenceBar view not found in layout");
            if (saveButton == null) Log.w(TAG, "saveButton view not found in layout");
        }
    }
}