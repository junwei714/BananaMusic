package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.ImageLoadHelper;

/**
 * Adapter for displaying mood-based track recommendations
 */
public class MoodRecommendationsAdapter extends RecyclerView.Adapter<MoodRecommendationsAdapter.ViewHolder> {

    private final List<Track> tracks;
    private final Context context;
    private final OnMoodTrackClickListener listener;

    public interface OnMoodTrackClickListener {
        void onTrackClick(Track track, int position);
        void onPlayButtonClick(Track track, int position);
    }

    public MoodRecommendationsAdapter(Context context, OnMoodTrackClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.tracks = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommended_track, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track track = tracks.get(position);
        
        holder.trackTitle.setText(track.getTitle());
        holder.trackArtist.setText(track.getArtist());
        
        // Load album art - try safer approach with fallback
        String albumArtUrl = track.getAlbumArtUrl();
        
        if (albumArtUrl != null && !albumArtUrl.isEmpty()) {
            try {
                Glide.with(context)
                    .load(albumArtUrl)
                    .placeholder(R.drawable.default_track_art)
                    .error(R.drawable.default_track_art)
                    .into(holder.trackArt);
            } catch (Exception e) {
                // Fallback to default in case of loading error
                holder.trackArt.setImageResource(R.drawable.default_track_art);
            }
        } else {
            // No album art URL available, use default
            holder.trackArt.setImageResource(R.drawable.default_track_art);
        }
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(track, position);
            }
        });
        
        holder.btnPlay.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayButtonClick(track, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tracks != null ? tracks.size() : 0;
    }
    
    public void updateTracks(List<Track> newTracks) {
        this.tracks.clear();
        if (newTracks != null) {
            this.tracks.addAll(newTracks);
        }
        notifyDataSetChanged();
        
        // Log for debugging
        android.util.Log.d("MoodRecommendationsAdapter", "Updated with " + (newTracks != null ? newTracks.size() : 0) + " tracks");
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView trackArt;
        TextView trackTitle;
        TextView trackArtist;
        ImageButton btnPlay;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            trackArt = itemView.findViewById(R.id.ivTrackArt);
            trackTitle = itemView.findViewById(R.id.tvTrackTitle);
            trackArtist = itemView.findViewById(R.id.tvTrackArtist);
            btnPlay = itemView.findViewById(R.id.btnPlayTrack);
        }
    }
} 