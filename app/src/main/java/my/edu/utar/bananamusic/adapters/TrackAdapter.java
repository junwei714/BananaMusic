package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.chip.ChipGroup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.SearchTrack;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {
    private static final String TAG = "TrackAdapter";
    private List<SearchTrack> searchTracks;
    private List<Track> tracks;
    private Context context;
    private int currentPlayingPosition = -1;
    private OnTrackClickListener clickListener;
    private OnTrackActionListener actionListener;
    private boolean useSearchTracks = true;
    private Track currentlyPlayingTrack = null;
    private int expandedPosition = -1;
    private AudioPlayerHelper audioPlayerHelper;

    /**
     * Interface for track click events
     */
    public interface OnTrackClickListener {
        void onTrackClick(Track track, int position);
    }

    /**
     * Interface for track actions like play, delete, etc.
     */
    public interface OnTrackActionListener {
        void onTrackPlay(Track track, int position);
        void onTrackDelete(Track track, int position);
        void onTrackAddToPlaylist(Track track, int position);
        void onTrackMenuClick(Track track, int position, View view);
    }

    /**
     * Basic constructor with context only
     */
    public TrackAdapter(Context context) {
        this.context = context;
        this.searchTracks = new ArrayList<>();
        this.tracks = new ArrayList<>();
        this.audioPlayerHelper = AudioPlayerHelper.getInstance(context);
    }

    /**
     * Constructor for SearchTrack list with click listener
     */
    public TrackAdapter(List<SearchTrack> searchTracks, OnTrackClickListener listener) {
        this.searchTracks = searchTracks != null ? searchTracks : new ArrayList<>();
        this.tracks = new ArrayList<>();
        this.clickListener = listener;
        this.useSearchTracks = true;
    }

    /**
     * Constructor for Track list with click listener
     */
    public TrackAdapter(Context context, List<Track> tracks, OnTrackClickListener listener) {
        this.context = context;
        this.tracks = tracks != null ? tracks : new ArrayList<>();
        this.searchTracks = new ArrayList<>();
        this.clickListener = listener;
        this.useSearchTracks = false;
        this.audioPlayerHelper = AudioPlayerHelper.getInstance(context);
    }

    /**
     * Constructor for Track list with action listener
     */
    public TrackAdapter(Context context, List<Track> tracks, OnTrackActionListener actionListener) {
        this.context = context;
        this.tracks = tracks != null ? tracks : new ArrayList<>();
        this.searchTracks = new ArrayList<>();
        this.actionListener = actionListener;
        this.useSearchTracks = false;
        this.audioPlayerHelper = AudioPlayerHelper.getInstance(context);
    }

    /**
     * Constructor with context and Track list for compatibility
     */
    public TrackAdapter(Context context, List<Track> tracks) {
        this.context = context;
        this.tracks = tracks != null ? tracks : new ArrayList<>();
        this.searchTracks = new ArrayList<>();
        this.useSearchTracks = false;
        this.audioPlayerHelper = AudioPlayerHelper.getInstance(context);
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        if (useSearchTracks) {
            SearchTrack searchTrack = searchTracks.get(position);
            holder.titleTextView.setText(searchTrack.getTitle());
            holder.artistTextView.setText(searchTrack.getArtist());

            // Load album art using Glide
            if (searchTrack.getAlbumArtUrl() != null && !searchTrack.getAlbumArtUrl().isEmpty()) {
                Glide.with(holder.albumArtImageView.getContext())
                    .load(searchTrack.getAlbumArtUrl())
                    .placeholder(R.drawable.default_album_art)
                    .error(R.drawable.default_album_art)
                    .into(holder.albumArtImageView);
            } else {
                holder.albumArtImageView.setImageResource(R.drawable.default_album_art);
            }

            // Convert SearchTrack to Track for playing
            Track track = new Track();
            track.setTitle(searchTrack.getTitle());
            track.setArtist(searchTrack.getArtist());
            track.setPreviewUrl(searchTrack.getPreviewUrl());
            track.setAlbumArtUrl(searchTrack.getAlbumArtUrl());

            // Update play button state based on currently playing track
            boolean isCurrentlyPlaying = currentlyPlayingTrack != null && 
                currentlyPlayingTrack.getTitle().equals(track.getTitle()) &&
                currentlyPlayingTrack.getArtist().equals(track.getArtist());
            holder.playButton.setImageResource(isCurrentlyPlaying ? 
                R.drawable.ic_pause : R.drawable.ic_play);

            // Set click listeners
            holder.playButton.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTrackClick(track, holder.getAdapterPosition());
                    // Show mini player in MainActivity
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).showMiniPlayer();
                    }
                }
            });

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTrackClick(track, holder.getAdapterPosition());
                    // Show mini player in MainActivity
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).showMiniPlayer();
                    }
                }
            });
        } else {
            Track track = tracks.get(position);
            holder.titleTextView.setText(track.getTitle());
            holder.artistTextView.setText(track.getArtist());

            // Load album art using Glide
            if (track.getAlbumArtUrl() != null && !track.getAlbumArtUrl().isEmpty()) {
                Glide.with(holder.albumArtImageView.getContext())
                    .load(track.getAlbumArtUrl())
                    .placeholder(R.drawable.default_album_art)
                    .error(R.drawable.default_album_art)
                    .into(holder.albumArtImageView);
            } else {
                holder.albumArtImageView.setImageResource(R.drawable.default_album_art);
            }

            // Update play button state based on currently playing track
            boolean isCurrentlyPlaying = currentlyPlayingTrack != null && 
                currentlyPlayingTrack.getTitle().equals(track.getTitle()) &&
                currentlyPlayingTrack.getArtist().equals(track.getArtist());
            holder.playButton.setImageResource(isCurrentlyPlaying ? 
                R.drawable.ic_pause : R.drawable.ic_play);

            // Set click listeners
            holder.playButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onTrackPlay(track, holder.getAdapterPosition());
                    // Show mini player in MainActivity
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).showMiniPlayer();
                    }
                } else if (clickListener != null) {
                    clickListener.onTrackClick(track, holder.getAdapterPosition());
                    // Show mini player in MainActivity
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).showMiniPlayer();
                    }
                }
            });

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTrackClick(track, holder.getAdapterPosition());
                    // Show mini player in MainActivity
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).showMiniPlayer();
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return useSearchTracks ? searchTracks.size() : tracks.size();
    }

    /**
     * Update the adapter with new search tracks
     */
    public void updateTracks(List<SearchTrack> newTracks) {
        currentPlayingPosition = -1;
        this.searchTracks = newTracks != null ? newTracks : new ArrayList<>();
        this.useSearchTracks = true;
        notifyDataSetChanged();
    }

    /**
     * Update the adapter with standard tracks
     */
    public void updateStandardTracks(List<Track> newTracks) {
        currentPlayingPosition = -1;
        this.tracks = newTracks != null ? newTracks : new ArrayList<>();
        this.useSearchTracks = false;
        notifyDataSetChanged();
    }

    /**
     * Alternative name for updateStandardTracks for compatibility
     */
    public void updateTrackList(List<Track> newTracks) {
        updateStandardTracks(newTracks);
    }

    /**
     * Set the currently playing track, updating UI if needed
     */
    public void setCurrentlyPlayingTrack(Track track) {
        this.currentlyPlayingTrack = track;
        notifyDataSetChanged();
    }

    /**
     * Alternative name for updateStandardTracks for compatibility
     */
    public void setTracks(List<Track> newTracks) {
        updateStandardTracks(newTracks);
    }

    /**
     * Release resources when adapter is destroyed
     */
    public void release() {
        // No need to release anything since we're using AudioPlayerHelper
    }

    /**
     * ViewHolder for tracks
     */
    public static class TrackViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView artistTextView;
        ImageButton playButton;
        ImageView albumArtImageView;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.text_track_title);
            artistTextView = itemView.findViewById(R.id.text_track_artist);
            playButton = itemView.findViewById(R.id.button_play);
            albumArtImageView = itemView.findViewById(R.id.iv_track_art);
        }
    }

    /**
     * Expand an item to show more details
     */
    public void expandItem(int position) {
        if (position != expandedPosition) {
            expandedPosition = position;
            notifyItemChanged(position);
        }
    }

    /**
     * Collapse an expanded item
     */
    public void collapseItem(int position) {
        if (position == expandedPosition) {
            expandedPosition = -1;
            notifyItemChanged(position);
        }
    }

    /**
     * Collapse all expanded items
     */
    public void collapseAll() {
        expandedPosition = -1;
        notifyDataSetChanged();
    }

    /**
     * Check if an item is expanded
     */
    public boolean isItemExpanded(int position) {
        return position == expandedPosition;
    }

    /**
     * Get the currently playing track
     */
    public Track getCurrentlyPlayingTrack() {
        return currentlyPlayingTrack;
    }

    public void updatePlayingState(boolean isPlaying) {
        if (currentPlayingPosition != -1) {
            if (useSearchTracks && currentPlayingPosition < searchTracks.size()) {
                searchTracks.get(currentPlayingPosition).setPlaying(isPlaying);
            } else if (!useSearchTracks && currentPlayingPosition < tracks.size()) {
                Track track = tracks.get(currentPlayingPosition);
                if (track != null) {
                    track.setPlaying(isPlaying);
                }
            }
            notifyItemChanged(currentPlayingPosition);
        }
    }
}