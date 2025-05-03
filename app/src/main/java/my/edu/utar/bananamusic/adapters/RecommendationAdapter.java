package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;

public class RecommendationAdapter extends RecyclerView.Adapter<RecommendationAdapter.ViewHolder> {

    private List<Track> tracks;
    private Context context;
    private OnTrackClickListener listener;

    public interface OnTrackClickListener {
        void onTrackClick(Track track, int position);
    }

    public RecommendationAdapter(Context context, List<Track> tracks, OnTrackClickListener listener) {
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
        
        holder.tvTrackTitle.setText(track.getTitle());
        holder.tvTrackArtist.setText(track.getArtist());
        
        // Load image using Glide
        if (track.getImageUrl() != null && !track.getImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(track.getImageUrl())
                    .placeholder(R.drawable.default_track_art)
                    .error(R.drawable.default_track_art)
                    .into(holder.ivTrackArt);
        } else {
            holder.ivTrackArt.setImageResource(R.drawable.default_track_art);
        }
        
        // Set click listener
        holder.btnPlayTrack.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(track, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return tracks == null ? 0 : tracks.size();
    }

    public void updateTracks(List<Track> newTracks) {
        this.tracks = newTracks;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivTrackArt;
        TextView tvTrackTitle;
        TextView tvTrackArtist;
        ImageButton btnPlayTrack;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTrackArt = itemView.findViewById(R.id.ivTrackArt);
            tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
            tvTrackArtist = itemView.findViewById(R.id.tvTrackArtist);
            btnPlayTrack = itemView.findViewById(R.id.btnPlayTrack);
        }
    }
} 