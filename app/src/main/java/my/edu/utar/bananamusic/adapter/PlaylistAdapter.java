package my.edu.utar.bananamusic.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;

import java.util.ArrayList;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {
    private List<Playlist> playlists = new ArrayList<>();
    private final Context context;
    private OnPlaylistClickListener listener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
    }

    public PlaylistAdapter(Context context, OnPlaylistClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        holder.bind(playlist);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public void setPlaylists(List<Playlist> playlists) {
        this.playlists = playlists;
        notifyDataSetChanged();
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ImageView coverImageView;
        private final TextView nameTextView;
        private final TextView creatorTextView;
        private final TextView trackCountTextView;
        private final TextView descriptionTextView;

        PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            coverImageView = itemView.findViewById(R.id.ivPlaylistCover);
            nameTextView = itemView.findViewById(R.id.tvPlaylistName);
            creatorTextView = itemView.findViewById(R.id.tvCreator);
            trackCountTextView = itemView.findViewById(R.id.tvTrackCount);
            descriptionTextView = itemView.findViewById(R.id.tvPlaylistDescription);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlaylistClick(playlists.get(position));
                }
            });
        }

        void bind(Playlist playlist) {
            nameTextView.setText(playlist.getName());
            creatorTextView.setText(playlist.getCreatorName());
            descriptionTextView.setText(playlist.getDescription());
            
            if (playlist.getTrackCount() > 0) {
                trackCountTextView.setVisibility(View.VISIBLE);
                trackCountTextView.setText(context.getString(R.string.track_count_format, playlist.getTrackCount()));
            } else {
                trackCountTextView.setVisibility(View.GONE);
            }

            // Load cover image with Glide
            if (playlist.getCoverImageUrl() != null && !playlist.getCoverImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(playlist.getCoverImageUrl())
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .apply(new RequestOptions()
                        .placeholder(R.drawable.default_playlist_cover)
                        .error(R.drawable.default_playlist_cover)
                        .centerCrop())
                    .into(coverImageView);
            } else {
                coverImageView.setImageResource(R.drawable.default_playlist_cover);
            }
        }
    }
} 