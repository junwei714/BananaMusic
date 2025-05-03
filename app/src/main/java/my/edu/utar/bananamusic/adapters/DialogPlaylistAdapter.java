package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.utils.ImageLoadHelper;

public class DialogPlaylistAdapter extends RecyclerView.Adapter<DialogPlaylistAdapter.ViewHolder> {

    private final Context context;
    private final List<Playlist> playlists;
    private final OnPlaylistClickListener listener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
    }

    public DialogPlaylistAdapter(Context context, OnPlaylistClickListener listener) {
        this.context = context;
        this.playlists = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_dialog_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        holder.bind(playlist);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public void updatePlaylists(List<Playlist> newPlaylists) {
        playlists.clear();
        if (newPlaylists != null) {
            playlists.addAll(newPlaylists);
        }
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView ivPlaylistCover;
        private final TextView tvPlaylistName;
        private final TextView tvTrackCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPlaylistCover = itemView.findViewById(R.id.ivPlaylistCover);
            tvPlaylistName = itemView.findViewById(R.id.tvPlaylistName);
            tvTrackCount = itemView.findViewById(R.id.tvTrackCount);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlaylistClick(playlists.get(position));
                }
            });
        }

        void bind(Playlist playlist) {
            tvPlaylistName.setText(playlist.getName());
            
            int trackCount = playlist.getTrackCount();
            tvTrackCount.setText(context.getResources().getQuantityString(
                R.plurals.track_count, trackCount, trackCount));

            // Load playlist cover image
            if (playlist.getCoverImageUrl() != null && !playlist.getCoverImageUrl().isEmpty()) {
                ImageLoadHelper.loadRoundedImage(context, playlist.getCoverImageUrl(), 
                    ivPlaylistCover, R.drawable.default_playlist_cover, 8);
            } else {
                ivPlaylistCover.setImageResource(R.drawable.default_playlist_cover);
            }
        }
    }
} 