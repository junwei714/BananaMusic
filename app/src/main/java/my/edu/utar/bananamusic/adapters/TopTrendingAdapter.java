package my.edu.utar.bananamusic.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;

public class TopTrendingAdapter extends RecyclerView.Adapter<TopTrendingAdapter.ViewHolder> {
    private List<Track> tracks = new ArrayList<>();
    private final OnTrackClickListener listener;

    public interface OnTrackClickListener {
        void onTrackClick(Track track, int position);
        void onPlayPreviewClick(Track track, int position);
    }

    public TopTrendingAdapter(OnTrackClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trending_track, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.bind(track, position + 1, listener);
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    public void setTracks(List<Track> newTracks) {
        this.tracks = newTracks;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final CardView cardView;
        private final ImageView albumArt;
        private final TextView rankingNumber;
        private final TextView trackTitle;
        private final TextView artistName;
        private final MaterialButton playPreviewButton;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            albumArt = itemView.findViewById(R.id.albumArt);
            rankingNumber = itemView.findViewById(R.id.rankingNumber);
            trackTitle = itemView.findViewById(R.id.trackTitle);
            artistName = itemView.findViewById(R.id.artistName);
            playPreviewButton = itemView.findViewById(R.id.playPreviewButton);
        }

        void bind(Track track, int ranking, OnTrackClickListener listener) {
            trackTitle.setText(track.getTitle());
            artistName.setText(track.getArtist());
            rankingNumber.setText(String.valueOf(ranking));

            // Load album art using Glide
            Glide.with(albumArt.getContext())
                    .load(track.getAlbumArtUrl())
                    .placeholder(R.drawable.album_placeholder)
                    .error(R.drawable.album_placeholder)
                    .into(albumArt);

            // Highlight top 3 tracks with different styling
            if (ranking <= 3) {
                cardView.setCardElevation(12f); // Higher elevation
                cardView.setRadius(24f); // Larger corner radius
                // Set different background colors for top 3
                int colorResId;
                switch (ranking) {
                    case 1:
                        colorResId = R.color.top_1_background;
                        break;
                    case 2:
                        colorResId = R.color.top_2_background;
                        break;
                    case 3:
                        colorResId = R.color.top_3_background;
                        break;
                    default:
                        colorResId = R.color.card_background;
                        break;
                }
                cardView.setCardBackgroundColor(itemView.getContext().getColor(colorResId));
            } else {
                cardView.setCardElevation(4f); // Normal elevation
                cardView.setRadius(12f); // Normal corner radius
                cardView.setCardBackgroundColor(itemView.getContext().getColor(R.color.card_background));
            }

            // Set click listeners
            itemView.setOnClickListener(v -> listener.onTrackClick(track, getAdapterPosition()));
            playPreviewButton.setOnClickListener(v -> listener.onPlayPreviewClick(track, getAdapterPosition()));
        }
    }
} 