package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.utils.DataFetcher;
import my.edu.utar.bananamusic.utils.PlaylistManager;

/**
 * A safe adapter for Playlist data that extends SafeDataAdapter
 */
public class SafePlaylistAdapter extends SafeDataAdapter<Playlist, SafePlaylistAdapter.ViewHolder> {

    private final OnPlaylistClickListener listener;
    private final PlaylistManager playlistManager;
    
    /**
     * Interface for handling playlist click events
     */
    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist, int position);
        default void onPlaylistOptionsClick(Playlist playlist, int position, View view) {
            // Optional implementation
        }
    }
    
    /**
     * Constructor with context only
     */
    public SafePlaylistAdapter(Context context) {
        super(context);
        this.listener = null;
        this.playlistManager = PlaylistManager.getInstance(context);
    }
    
    /**
     * Constructor with listener
     */
    public SafePlaylistAdapter(Context context, OnPlaylistClickListener listener) {
        super(context);
        this.listener = listener;
        this.playlistManager = PlaylistManager.getInstance(context);
    }
    
    /**
     * Constructor with initial data and listener
     */
    public SafePlaylistAdapter(Context context, List<Playlist> initialPlaylists, OnPlaylistClickListener listener) {
        super(context, initialPlaylists);
        this.listener = listener;
        this.playlistManager = PlaylistManager.getInstance(context);
    }
    
    /**
     * Fetch all playlists
     */
    public void fetchAllPlaylists() {
        setLoadingStarted();
        
        dataFetcher.executeAsync(() -> {
            try {
                // Using PlaylistManager instead of direct DataFetcher call
                List<Playlist> playlists = new ArrayList<>();
                // Add code to fetch playlists from PlaylistManager if available
                
                // Post results on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateData(playlists);
                    setLoadingFinished(true, null);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    setLoadingFinished(false, e.getMessage());
                });
            }
        });
        
        // Set retry action
        setRetryAction(this::fetchAllPlaylists);
    }
    
    /**
     * Fetch recommended playlists
     */
    public void fetchRecommendedPlaylists() {
        setLoadingStarted();
        
        dataFetcher.executeAsync(() -> {
            try {
                // Using PlaylistManager instead of direct DataFetcher call
                List<Playlist> playlists = new ArrayList<>();
                // Add code to fetch recommended playlists
                
                // Post results on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateData(playlists);
                    setLoadingFinished(true, null);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    setLoadingFinished(false, e.getMessage());
                });
            }
        });
        
        // Set retry action
        setRetryAction(this::fetchRecommendedPlaylists);
    }
    
    /**
     * Fetch user playlists
     */
    public void fetchUserPlaylists() {
        setLoadingStarted();
        
        dataFetcher.executeAsync(() -> {
            try {
                // Using PlaylistManager instead of direct DataFetcher call
                List<Playlist> playlists = new ArrayList<>();
                // Add code to fetch user playlists
                
                // Post results on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateData(playlists);
                    setLoadingFinished(true, null);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    setLoadingFinished(false, e.getMessage());
                });
            }
        });
        
        // Set retry action
        setRetryAction(this::fetchUserPlaylists);
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_NORMAL) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist, parent, false);
            return new ViewHolder(view);
        }
        return super.onCreateViewHolder(parent, viewType);
    }
    
    @Override
    protected void onBindViewHolderInternal(ViewHolder holder, int position) {
        Playlist playlist = items.get(position);
        
        // Bind playlist data - using getName() instead of getTitle()
        holder.playlistTitle.setText(playlist.getName());
        
        // Use getCreatorName() instead of getCreator()
        holder.playlistCreator.setText(playlist.getCreatorName());
        
        // Parse trackCount differently
        String trackCountText = String.valueOf(playlist.getTrackCount());
        if (trackCountText != null && !trackCountText.isEmpty()) {
            holder.trackCount.setVisibility(View.VISIBLE);
            holder.trackCount.setText(trackCountText);
        } else {
            holder.trackCount.setVisibility(View.GONE);
        }
        
        // Load cover image with Glide
        if (playlist.getCoverImageUrl() != null && !playlist.getCoverImageUrl().isEmpty()) {
            Glide.with(context)
                .load(playlist.getCoverImageUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.placeholder_playlist)
                .error(R.drawable.error_playlist)
                .into(holder.coverImage);
        } else {
            holder.coverImage.setImageResource(R.drawable.placeholder_playlist);
        }
        
        // Setup click listeners
        if (listener != null) {
            holder.itemView.setOnClickListener(v -> 
                listener.onPlaylistClick(playlist, holder.getBindingAdapterPosition()));
                
            holder.optionsButton.setOnClickListener(v ->
                listener.onPlaylistOptionsClick(playlist, holder.getBindingAdapterPosition(), v));
        }
    }
    
    /**
     * ViewHolder for playlist items
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView coverImage;
        public TextView playlistTitle;
        public TextView playlistCreator;
        public TextView trackCount;
        public ImageView optionsButton;
        
        public ViewHolder(View itemView) {
            super(itemView);
            coverImage = itemView.findViewById(R.id.ivPlaylistCover);
            playlistTitle = itemView.findViewById(R.id.tvPlaylistName);
            playlistCreator = itemView.findViewById(R.id.tvCreator);
            trackCount = itemView.findViewById(R.id.tvTrackCount);
            optionsButton = itemView.findViewById(R.id.ivPlaylistOptions);
        }
    }
} 