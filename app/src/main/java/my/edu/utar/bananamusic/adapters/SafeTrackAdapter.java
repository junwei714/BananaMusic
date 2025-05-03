package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.DataFetcher;
import my.edu.utar.bananamusic.utils.ApiDataProvider;

/**
 * A safe adapter for Track data that extends SafeDataAdapter
 */
public class SafeTrackAdapter extends SafeDataAdapter<Track, SafeTrackAdapter.ViewHolder> {

    private final OnTrackClickListener listener;
    private final ApiDataProvider apiDataProvider;
    
    /**
     * Interface for handling track click events
     */
    public interface OnTrackClickListener {
        void onTrackClick(Track track, int position);
        default void onTrackOptionsClick(Track track, int position, View view) {
            // Optional implementation
        }
    }
    
    /**
     * Constructor with context only
     */
    public SafeTrackAdapter(Context context) {
        super(context);
        this.listener = null;
        this.apiDataProvider = ApiDataProvider.getInstance(context);
    }
    
    /**
     * Constructor with listener
     */
    public SafeTrackAdapter(Context context, OnTrackClickListener listener) {
        super(context);
        this.listener = listener;
        this.apiDataProvider = ApiDataProvider.getInstance(context);
    }
    
    /**
     * Constructor with initial data and listener
     */
    public SafeTrackAdapter(Context context, List<Track> initialTracks, OnTrackClickListener listener) {
        super(context, initialTracks);
        this.listener = listener;
        this.apiDataProvider = ApiDataProvider.getInstance(context);
    }
    
    /**
     * Fetch tracks by mood
     */
    public void fetchTracksByMood(String mood) {
        setLoadingStarted();
        
        dataFetcher.executeAsync(() -> {
            try {
                // Use ApiDataProvider to fetch the tracks
                List<Track> tracks = new ArrayList<>();
                // Add code to fetch tracks by mood
                
                // Post back on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateData(tracks);
                    setLoadingFinished(true, null);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    setLoadingFinished(false, e.getMessage());
                });
            }
        });
        
        // Set retry action
        setRetryAction(() -> fetchTracksByMood(mood));
    }
    
    /**
     * Fetch trending tracks
     */
    public void fetchTrendingTracks() {
        setLoadingStarted();
        
        dataFetcher.executeAsync(() -> {
            try {
                // Use ApiDataProvider to fetch the tracks
                List<Track> tracks = new ArrayList<>();
                // Add code to fetch trending tracks
                
                // Post back on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateData(tracks);
                    setLoadingFinished(true, null);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    setLoadingFinished(false, e.getMessage());
                });
            }
        });
        
        // Set retry action
        setRetryAction(this::fetchTrendingTracks);
    }
    
    /**
     * Search for tracks
     */
    public void searchTracks(String query) {
        setLoadingStarted();
        
        dataFetcher.executeAsync(() -> {
            try {
                // Use ApiDataProvider to search tracks
                List<Track> tracks = new ArrayList<>();
                // Add code to search tracks
                
                // Post back on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateData(tracks);
                    setLoadingFinished(true, null);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    setLoadingFinished(false, e.getMessage());
                });
            }
        });
        
        // Set retry action
        setRetryAction(() -> searchTracks(query));
    }
    
    /**
     * Fetch user library tracks
     */
    public void fetchUserLibrary() {
        setLoadingStarted();
        
        dataFetcher.executeAsync(() -> {
            try {
                // Use ApiDataProvider to fetch user library
                List<Track> tracks = new ArrayList<>();
                // Add code to fetch user library
                
                // Post back on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateData(tracks);
                    setLoadingFinished(true, null);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    setLoadingFinished(false, e.getMessage());
                });
            }
        });
        
        // Set retry action
        setRetryAction(this::fetchUserLibrary);
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_NORMAL) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
            return new ViewHolder(view);
        }
        return super.onCreateViewHolder(parent, viewType);
    }
    
    @Override
    protected void onBindViewHolderInternal(ViewHolder holder, int position) {
        Track track = items.get(position);
        
        // Bind track data to ViewHolder
        holder.trackTitle.setText(track.getTitle());
        holder.artistName.setText(track.getArtist());
        
        // Load album art with Glide - getAlbumArt() is an alias for getAlbumArtUrl()
        if (track.getAlbumArt() != null && !track.getAlbumArt().isEmpty()) {
            Glide.with(context)
                .load(track.getAlbumArt())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.placeholder_album)
                .error(R.drawable.error_album)
                .into(holder.albumArt);
        } else {
            holder.albumArt.setImageResource(R.drawable.placeholder_album);
        }
        
        // Setup click listeners
        if (listener != null) {
            holder.itemView.setOnClickListener(v -> 
                listener.onTrackClick(track, holder.getBindingAdapterPosition()));
                
            holder.optionsButton.setOnClickListener(v ->
                listener.onTrackOptionsClick(track, holder.getBindingAdapterPosition(), v));
        }
        
        // Hide explicit badge by default since isExplicit() doesn't exist
        holder.explicitBadge.setVisibility(View.GONE);
        
        // Format duration
        if (track.getDuration() > 0) {
            int minutes = (int) (track.getDuration() / 60000);
            int seconds = (int) ((track.getDuration() % 60000) / 1000);
            holder.duration.setText(String.format("%d:%02d", minutes, seconds));
        } else {
            holder.duration.setText("--:--");
        }
    }
    
    /**
     * ViewHolder for track items
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView albumArt;
        public TextView trackTitle;
        public TextView artistName;
        public TextView duration;
        public View explicitBadge;
        public ImageView optionsButton;
        
        public ViewHolder(View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.iv_album_art);
            trackTitle = itemView.findViewById(R.id.tv_track_title);
            artistName = itemView.findViewById(R.id.tv_artist_name);
            duration = itemView.findViewById(R.id.tv_duration);
            explicitBadge = itemView.findViewById(R.id.badge_explicit);
            optionsButton = itemView.findViewById(R.id.btn_options);
        }
    }
} 