package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.ImageLoadHelper;

public class RecommendedTracksAdapter extends RecyclerView.Adapter<RecommendedTracksAdapter.ViewHolder> {

    private final List<Track> tracks;
    private final Context context;
    private final OnRecommendedTrackClickListener listener;

    public interface OnRecommendedTrackClickListener {
        void onTrackClick(Track track, int position);
        void onPlayButtonClick(Track track, int position);
    }

    public RecommendedTracksAdapter(Context context, List<Track> tracks, OnRecommendedTrackClickListener listener) {
        this.context = context;
        this.tracks = tracks;
        this.listener = listener;
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
        
        holder.trackTitle.setText(track.getName());
        holder.trackArtist.setText(track.getArtistName());
        
        // Load album art
        if (track.getImageUrl() != null && !track.getImageUrl().isEmpty()) {
            ImageLoadHelper.loadImage(context, track.getImageUrl(), holder.trackArt, R.drawable.album_placeholder);
        } else {
            Glide.with(context)
                .load(R.drawable.album_placeholder)
                .into(holder.trackArt);
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
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView trackArt;
        TextView trackTitle;
        TextView trackArtist;
        MaterialButton btnPlay;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            trackArt = itemView.findViewById(R.id.ivTrackArt);
            trackTitle = itemView.findViewById(R.id.tvTrackTitle);
            trackArtist = itemView.findViewById(R.id.tvTrackArtist);
            btnPlay = itemView.findViewById(R.id.btnPlayTrack);
        }
    }
} 