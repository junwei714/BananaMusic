package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;

public class NewRecommendationAdapter extends RecyclerView.Adapter<NewRecommendationAdapter.ViewHolder> {
    private static final String TAG = "NewRecommendationAdapter";
    private final Context context;
    private List<Track> tracks;
    private OnTrackClickListener listener;

    public interface OnTrackClickListener {
        void onTrackClick(Track track, int position);
    }

    public NewRecommendationAdapter(Context context) {
        this.context = context;
        this.tracks = new ArrayList<>();
    }

    public void setOnTrackClickListener(OnTrackClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_track_recommendation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track track = tracks.get(position);

        // Load album art with fade animation
        if (track.getAlbumArtUrl() != null && !track.getAlbumArtUrl().isEmpty()) {
            Glide.with(context)
                .load(track.getAlbumArtUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.default_album_art)
                .error(R.drawable.default_album_art)
                .into(holder.ivAlbumArt);
        } else {
            holder.ivAlbumArt.setImageResource(R.drawable.default_album_art);
        }

        // Set text
        holder.tvTitle.setText(track.getTitle());
        holder.tvArtist.setText(track.getArtist());
        
        // Set recommendation reason if available
        if (track.getRecommendationReason() != null && !track.getRecommendationReason().isEmpty()) {
            holder.tvRecommendationReason.setVisibility(View.VISIBLE);
            holder.tvRecommendationReason.setText(track.getRecommendationReason());
        } else {
            holder.tvRecommendationReason.setVisibility(View.GONE);
        }

        // Set up chips
        holder.chipEnergy.setText("Energy");
        holder.chipDance.setText("Dance");
        holder.chipMood.setText("Mood");

        // Setup click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(track, holder.getAdapterPosition());
            }
        });

        holder.fabPlay.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(track, holder.getAdapterPosition());
            }
        });
    }

    private void animatePlayButton(View view, float alpha) {
        ObjectAnimator fadeAnimator = ObjectAnimator.ofFloat(view, "alpha", view.getAlpha(), alpha);
        fadeAnimator.setDuration(200);
        fadeAnimator.setInterpolator(new DecelerateInterpolator());
        fadeAnimator.start();
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    public void updateData(List<Track> newTracks) {
        if (newTracks != null) {
            this.tracks = newTracks;
            notifyDataSetChanged();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAlbumArt;
        TextView tvTitle;
        TextView tvArtist;
        TextView tvRecommendationReason;
        com.google.android.material.chip.Chip chipEnergy;
        com.google.android.material.chip.Chip chipDance;
        com.google.android.material.chip.Chip chipMood;
        com.google.android.material.floatingactionbutton.FloatingActionButton fabPlay;

        ViewHolder(View itemView) {
            super(itemView);
            ivAlbumArt = itemView.findViewById(R.id.ivAlbumArt);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvRecommendationReason = itemView.findViewById(R.id.tvRecommendationReason);
            chipEnergy = itemView.findViewById(R.id.chipEnergy);
            chipDance = itemView.findViewById(R.id.chipDance);
            chipMood = itemView.findViewById(R.id.chipMood);
            fabPlay = itemView.findViewById(R.id.fabPlay);
        }
    }
} 