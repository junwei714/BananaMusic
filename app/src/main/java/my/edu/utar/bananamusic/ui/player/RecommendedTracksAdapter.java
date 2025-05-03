package my.edu.utar.bananamusic.ui.player;

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

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;

public class RecommendedTracksAdapter extends RecyclerView.Adapter<RecommendedTracksAdapter.TrackViewHolder> {

    private final List<Track> tracks = new ArrayList<>();
    private final Context context;
    private OnTrackSelectedListener listener;

    public interface OnTrackSelectedListener {
        void onTrackSelected(Track track);
    }

    public RecommendedTracksAdapter(Context context) {
        this.context = context;
    }

    public void setOnTrackSelectedListener(OnTrackSelectedListener listener) {
        this.listener = listener;
    }

    public void setTracks(List<Track> newTracks) {
        tracks.clear();
        if (newTracks != null) {
            tracks.addAll(newTracks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommended_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.bind(track);
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    class TrackViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivTrackArt;
        private final TextView tvTrackTitle;
        private final TextView tvTrackArtist;
        private final MaterialButton btnPlayTrack;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTrackArt = itemView.findViewById(R.id.ivTrackArt);
            tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
            tvTrackArtist = itemView.findViewById(R.id.tvTrackArtist);
            btnPlayTrack = itemView.findViewById(R.id.btnPlayTrack);

            // Set click listeners
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onTrackSelected(tracks.get(position));
                }
            });

            btnPlayTrack.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onTrackSelected(tracks.get(position));
                }
            });
        }

        void bind(Track track) {
            tvTrackTitle.setText(track.getTitle());
            tvTrackArtist.setText(track.getArtist());

            // Load album art
            if (track.getImageUrl() != null && !track.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(track.getImageUrl())
                        .placeholder(R.drawable.album_placeholder)
                        .error(R.drawable.album_placeholder)
                        .into(ivTrackArt);
            } else {
                ivTrackArt.setImageResource(R.drawable.album_placeholder);
            }
        }
    }
} 