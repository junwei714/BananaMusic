package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;

public class PlaylistTrackAdapter extends RecyclerView.Adapter<PlaylistTrackAdapter.ViewHolder> {
    private final Context context;
    private List<Track> tracks;
    private final OnTrackClickListener listener;
    private int playingTrackPosition = -1;
    private boolean isLoading = false;

    public interface OnTrackClickListener {
        void onTrackClick(Track track, int position);
        void onPlayButtonClick(Track track, int position);
    }

    public PlaylistTrackAdapter(Context context, OnTrackClickListener listener) {
        this.context = context;
        this.tracks = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_playlist_track, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track track = tracks.get(position);
        
        // Set track number
        holder.tvTrackNumber.setText(String.valueOf(position + 1));
        
        // Set track title
        holder.tvTrackTitle.setText(track.getTitle());
        
        // Set artist name
        holder.tvArtist.setText(track.getArtist());
        
        // Load track artwork
        String albumArt = track.getAlbumArtUrl();
        if (albumArt != null && !albumArt.isEmpty()) {
            Glide.with(context)
                .load(albumArt)
                .apply(new RequestOptions()
                    .placeholder(R.drawable.default_track_art)
                    .error(R.drawable.default_track_art))
                .into(holder.ivTrackArt);
        } else {
            holder.ivTrackArt.setImageResource(R.drawable.default_track_art);
        }

        // Update play button and loading state
        if (position == playingTrackPosition) {
            if (isLoading) {
                holder.btnPlay.setVisibility(View.INVISIBLE);
                holder.progressBar.setVisibility(View.VISIBLE);
            } else {
                holder.btnPlay.setVisibility(View.VISIBLE);
                holder.progressBar.setVisibility(View.GONE);
                holder.btnPlay.setImageResource(R.drawable.ic_pause);
            }
        } else {
            holder.btnPlay.setVisibility(View.VISIBLE);
            holder.progressBar.setVisibility(View.GONE);
            holder.btnPlay.setImageResource(R.drawable.ic_play);
        }

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(track, position);
            }
        });

        holder.btnPlay.setOnClickListener(v -> {
            if (listener != null && !isLoading) {
                listener.onPlayButtonClick(track, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tracks != null ? tracks.size() : 0;
    }

    public void updateTracks(List<Track> newTracks) {
        this.tracks = newTracks;
        notifyDataSetChanged();
    }

    public void setPlayingTrackPosition(int position) {
        int oldPosition = playingTrackPosition;
        playingTrackPosition = position;
        
        // Update the UI for both old and new positions
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition);
        }
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    public void setLoading(boolean loading) {
        if (this.isLoading != loading) {
            this.isLoading = loading;
            if (playingTrackPosition != -1) {
                notifyItemChanged(playingTrackPosition);
            }
        }
    }

    public int getPlayingTrackPosition() {
        return playingTrackPosition;
    }

    public void setPlayingState(boolean isPlaying) {
        if (playingTrackPosition != -1) {
            notifyItemChanged(playingTrackPosition);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTrackNumber;
        ImageView ivTrackArt;
        TextView tvTrackTitle;
        TextView tvArtist;
        ImageButton btnPlay;
        ProgressBar progressBar;

        ViewHolder(View itemView) {
            super(itemView);
            tvTrackNumber = itemView.findViewById(R.id.tvTrackNumber);
            ivTrackArt = itemView.findViewById(R.id.ivTrackArt);
            tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            btnPlay = itemView.findViewById(R.id.btnPlay);
            progressBar = itemView.findViewById(R.id.progressBar);
        }
    }
} 