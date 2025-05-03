package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    private final Context context;
    private List<Track> tracks;
    private final QueueAdapterListener listener;
    private final ItemTouchHelper touchHelper;
    private int currentPlayingPosition = -1;
    private boolean isHistoryMode = false;
    private boolean showTrackNumbers = true;
    
    // View type constants for more efficient view recycling
    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_PLAYING = 1;

    // Cached resources to avoid lookups
    private final RequestOptions imageOptions;
    private final LayoutInflater inflater;

    public interface QueueAdapterListener {
        void onItemClick(Track track, int position);
        void onItemMove(int fromPosition, int toPosition);
        void onItemRemoved(int position);
        void onOptionsClick(Track track, int position, View view);
    }

    public QueueAdapter(Context context, List<Track> tracks, QueueAdapterListener listener, ItemTouchHelper touchHelper) {
        this.context = context;
        this.tracks = tracks != null ? new ArrayList<>(tracks) : new ArrayList<>();
        this.listener = listener;
        this.touchHelper = touchHelper;
        
        // Initialize cached resources
        this.inflater = LayoutInflater.from(context);
        this.imageOptions = new RequestOptions()
                .transform(new RoundedCorners(8))
                .placeholder(R.drawable.ic_album_placeholder)
                .error(R.drawable.ic_album_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL);
        
        // Improve performance
        setHasStableIds(true);
    }

    public void setShowTrackNumbers(boolean show) {
        if (this.showTrackNumbers != show) {
            this.showTrackNumbers = show;
            notifyDataSetChanged();
        }
    }

    @Override
    public long getItemId(int position) {
        // Generate stable IDs for items to improve RecyclerView performance
        Track track = tracks.get(position);
        return track.getId() != null ? track.getId().hashCode() : position;
    }
    
    @Override
    public int getItemViewType(int position) {
        // Use different view types for playing vs non-playing items
        return position == currentPlayingPosition ? VIEW_TYPE_PLAYING : VIEW_TYPE_NORMAL;
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_queue_track, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.bind(track, position);
    }
    
    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            // Apply partial updates if available
            Track track = tracks.get(position);
            for (Object payload : payloads) {
                if (payload instanceof String) {
                    String change = (String) payload;
                    
                    if ("playing_state".equals(change)) {
                        // Only update the playing state indicator
                        holder.updatePlayingIndicator(position == currentPlayingPosition);
                    } else if ("drag_handle".equals(change)) {
                        // Only update the drag handle visibility
                        holder.updateDragHandleVisibility(isHistoryMode);
                    }
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return tracks != null ? tracks.size() : 0;
    }

    public void updateData(List<Track> newTracks) {
        if (newTracks == null) {
            newTracks = new ArrayList<>();
        }
        
        // Make a final copy of newTracks for the inner class to use
        final List<Track> finalNewTracks = newTracks;
        
        // Use DiffUtil to calculate the minimal number of changes
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return tracks.size();
            }

            @Override
            public int getNewListSize() {
                return finalNewTracks.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                Track oldTrack = tracks.get(oldPosition);
                Track newTrack = finalNewTracks.get(newPosition);
                return oldTrack.getId() != null && oldTrack.getId().equals(newTrack.getId());
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Track oldTrack = tracks.get(oldPosition);
                Track newTrack = finalNewTracks.get(newPosition);
                return oldTrack.equals(newTrack);
            }
        });
        
        // Apply the calculated diff
        this.tracks = new ArrayList<>(finalNewTracks);
        diffResult.dispatchUpdatesTo(this);
    }

    public void setCurrentPlayingPosition(int position) {
        int oldPosition = currentPlayingPosition;
        currentPlayingPosition = position;
        
        if (oldPosition != -1 && oldPosition < tracks.size()) {
            notifyItemChanged(oldPosition, "playing_state");
        }
        if (currentPlayingPosition != -1 && currentPlayingPosition < tracks.size()) {
            notifyItemChanged(currentPlayingPosition, "playing_state");
        }
    }
    
    public void setHistoryMode(boolean isHistoryMode) {
        if (this.isHistoryMode != isHistoryMode) {
            this.isHistoryMode = isHistoryMode;
            
            // Efficient update - only update the drag handle visibilities
            for (int i = 0; i < tracks.size(); i++) {
                notifyItemChanged(i, "drag_handle");
            }
        }
    }

    public void onItemMove(int fromPosition, int toPosition) {
        // Don't perform the move if either position is invalid
        if (fromPosition < 0 || fromPosition >= tracks.size() || 
            toPosition < 0 || toPosition >= tracks.size()) {
            return;
        }
        
        // Update our local list
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(tracks, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(tracks, i, i - 1);
            }
        }
        
        // Notify adapter of the move
        notifyItemMoved(fromPosition, toPosition);
        
        // Update current playing position if needed
        if (currentPlayingPosition == fromPosition) {
            currentPlayingPosition = toPosition;
            notifyItemChanged(toPosition, "playing_state");
        } else if (currentPlayingPosition == toPosition) {
            currentPlayingPosition = fromPosition;
            notifyItemChanged(fromPosition, "playing_state");
        }
        
        // Notify listener
        if (listener != null) {
            listener.onItemMove(fromPosition, toPosition);
        }
    }

    public void onItemDismiss(int position) {
        // Verify position is valid
        if (position < 0 || position >= tracks.size()) {
            return;
        }
        
        // Update current playing position if needed
        if (position < currentPlayingPosition) {
            currentPlayingPosition--;
        } else if (position == currentPlayingPosition) {
            currentPlayingPosition = -1;
        }
        
        // Remove from our local list
        tracks.remove(position);
        notifyItemRemoved(position);
        
        // Notify listener
        if (listener != null) {
            listener.onItemRemoved(position);
        }
    }

    class QueueViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivDragHandle;
        final ImageView ivTrackArt;
        final TextView tvTrackTitle;
        final TextView tvArtistName;
        final ImageView ivNowPlaying;
        final ImageView ivTrackOptions;
        final TextView tvTrackNumber;
        
        // Keep track of the bound position to avoid position lookups
        private int boundPosition = -1;

        QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            ivDragHandle = itemView.findViewById(R.id.ivDragHandle);
            ivTrackArt = itemView.findViewById(R.id.ivTrackArt);
            tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
            tvArtistName = itemView.findViewById(R.id.tvArtistName);
            ivNowPlaying = itemView.findViewById(R.id.ivNowPlaying);
            ivTrackOptions = itemView.findViewById(R.id.ivTrackOptions);
            tvTrackNumber = itemView.findViewById(R.id.tvTrackNumber);
            
            // Set click listeners for better performance
            setClickListeners();
        }
        
        private void setClickListeners() {
            // Set up the item click handler
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null && position < tracks.size()) {
                    listener.onItemClick(tracks.get(position), position);
                }
            });
            
            // Set up the options click handler
            ivTrackOptions.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null && position < tracks.size()) {
                    listener.onOptionsClick(tracks.get(position), position, v);
                }
            });
        }

        void bind(Track track, int position) {
            boundPosition = position;
            
            // Set track data
            tvTrackTitle.setText(track.getTitle());
            tvArtistName.setText(track.getArtist());
            
            // Update playing indicator
            updatePlayingIndicator(position == currentPlayingPosition);
            
            // Load album art
            loadImage(track);
            
            // Set up drag handle
            updateDragHandleVisibility(isHistoryMode);
            
            // Set track number
            if (showTrackNumbers) {
                tvTrackNumber.setVisibility(View.VISIBLE);
                tvTrackNumber.setText(String.valueOf(position + 1));
                
                // If current playing, hide number and show playing icon
                if (position == currentPlayingPosition) {
                    tvTrackNumber.setVisibility(View.INVISIBLE);
                    ivNowPlaying.setVisibility(View.VISIBLE);
                } else {
                    ivNowPlaying.setVisibility(View.GONE);
                }
            } else {
                tvTrackNumber.setVisibility(View.GONE);
            }
        }
        
        private void loadImage(Track track) {
            if (track.getAlbumArt() != null && !track.getAlbumArt().isEmpty()) {
                Glide.with(context)
                     .load(track.getAlbumArt())
                     .apply(imageOptions)
                     .into(ivTrackArt);
            } else {
                ivTrackArt.setImageResource(R.drawable.ic_album_placeholder);
            }
        }
        
        void updatePlayingIndicator(boolean isPlaying) {
            ivNowPlaying.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
            
            // Also update track number visibility
            if (tvTrackNumber != null) {
                tvTrackNumber.setVisibility(isPlaying || !showTrackNumbers ? View.INVISIBLE : View.VISIBLE);
            }
        }
        
        void updateDragHandleVisibility(boolean inHistoryMode) {
            if (inHistoryMode) {
                ivDragHandle.setVisibility(View.INVISIBLE);
            } else {
                ivDragHandle.setVisibility(View.VISIBLE);
                ivDragHandle.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        touchHelper.startDrag(this);
                    }
                    return false;
                });
            }
        }
    }
} 