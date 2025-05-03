package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.widget.ImageButton;
import android.graphics.PorterDuff;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.List;
import java.util.ArrayList;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.RecyclerViewSafety;
import my.edu.utar.bananamusic.utils.ImageLoadHelper;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> implements RecyclerViewSafety.ClearableAdapter {

    private static final String TAG = "PlaylistAdapter";
    private List<Playlist> playlists;
    private final Context context;
    private OnPlaylistClickListener listener;
    private List<Track> tracks;
    private OnTrackClickListener trackListener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
        void onPlaylistSaveClick(Playlist playlist);
        
        // Add method for handling long press
        default void onPlaylistLongClick(Playlist playlist) {
            // Default empty implementation - only implemented if needed
        }
    }

    public interface OnTrackClickListener {
        void onTrackClick(Track track);
    }

    // Constructor with just context
    public PlaylistAdapter(Context context) {
        this.context = context;
        this.playlists = new ArrayList<>();
        this.tracks = new ArrayList<>();
    }

    // Constructor with context and listener
    public PlaylistAdapter(Context context, OnPlaylistClickListener listener) {
        this(context);
        this.listener = listener;
    }

    // Constructor with context and playlists
    public PlaylistAdapter(Context context, List<Playlist> playlists) {
        this(context);
        this.playlists = playlists != null ? playlists : new ArrayList<>();
    }

    // Constructor with all parameters
    public PlaylistAdapter(Context context, List<Playlist> playlists, OnPlaylistClickListener listener) {
        this(context, playlists);
        this.listener = listener;
    }

    public void setOnPlaylistClickListener(OnPlaylistClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        Playlist playlist = playlists.get(position);
        return playlist.isCollaborative() ? 1 : 0;  // 1 for collaborative, 0 for regular
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == 1) {
            // Use collaborative playlist layout
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_collaborative_playlist, parent, false);
        } else {
            // Use regular playlist layout
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist, parent, false);
        }
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        if (position >= playlists.size()) {
            Log.e(TAG, "Invalid position: " + position);
            return;
        }

        Playlist playlist = playlists.get(position);
        if (playlist == null) {
            Log.e(TAG, "Null playlist at position: " + position);
            return;
        }

        Log.d(TAG, "Binding playlist at position " + position + ": " + playlist.getName());
        
        // Set playlist name
        if (holder.tvPlaylistName != null) {
            String name = playlist.getName();
            if (name != null && !name.isEmpty()) {
                holder.tvPlaylistName.setText(name);
                holder.tvPlaylistName.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set playlist name: " + name);
            } else {
                holder.tvPlaylistName.setText("Untitled Playlist");
                holder.tvPlaylistName.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set default playlist name");
            }
        } else {
            Log.e(TAG, "tvPlaylistName is null");
        }
        
        // Set creator name
        if (holder.tvCreator != null) {
            String creator = playlist.getCreatorName();
            if (creator != null && !creator.isEmpty()) {
                holder.tvCreator.setText(creator);
                holder.tvCreator.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set creator name: " + creator);
            } else {
                holder.tvCreator.setText("Unknown Creator");
                holder.tvCreator.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set default creator name");
            }
        } else {
            Log.e(TAG, "tvCreator is null");
        }
        
        // Set track count based on trackIds
        if (holder.tvTrackCount != null) {
            List<String> trackIds = playlist.getTrackIds();
            int trackCount = (trackIds != null) ? trackIds.size() : 0;
            String trackCountText = trackCount + " track" + (trackCount == 1 ? "" : "s");
            holder.tvTrackCount.setText(trackCountText);
            holder.tvTrackCount.setVisibility(View.VISIBLE);
            Log.d(TAG, "Set track count from trackIds: " + trackCount);
        } else {
            Log.e(TAG, "tvTrackCount is null");
        }
        
        // Load cover image
        if (holder.ivPlaylistCover != null) {
            String coverUrl = playlist.getCoverImageUrl();
            if (coverUrl != null && !coverUrl.isEmpty()) {
                Log.d(TAG, "Loading cover image from URL: " + coverUrl);
            Glide.with(context)
                    .load(coverUrl)
                .placeholder(R.drawable.default_playlist_cover)
                .error(R.drawable.default_playlist_cover)
                    .transform(new RoundedCorners(8))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Failed to load cover image", e);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.d(TAG, "Successfully loaded cover image");
                            return false;
                        }
                    })
                    .into(holder.ivPlaylistCover);
            } else {
                Log.d(TAG, "Using default cover image");
                holder.ivPlaylistCover.setImageResource(R.drawable.default_playlist_cover);
            }
        } else {
            Log.e(TAG, "ivPlaylistCover is null");
        }

        // Set up buttons and badges
        if (holder.collaborativeBadge != null) {
            holder.collaborativeBadge.setVisibility(playlist.isCollaborative() ? View.VISIBLE : View.GONE);
        }

        if (holder.btnLovePlaylist != null) {
            holder.btnLovePlaylist.setImageResource(
                playlist.isCollected() ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            holder.btnLovePlaylist.setOnClickListener(v -> toggleLovePlaylist(playlist, holder.btnLovePlaylist));
        }

        if (holder.btnSave != null) {
            holder.btnSave.setImageResource(
                playlist.isSaved() ? R.drawable.ic_saved : R.drawable.ic_save);
            holder.btnSave.setOnClickListener(v -> toggleSavePlaylist(playlist, holder.btnSave));
        }

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlaylistClick(playlist);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onPlaylistLongClick(playlist);
                return true;
            }
            return false;
        });
    }

    private void toggleLovePlaylist(Playlist playlist, ImageButton button) {
        boolean currentState = playlist.isCollected();
        playlist.setCollected(!currentState);
        button.setImageResource(playlist.isCollected() ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
    }

    private void toggleSavePlaylist(Playlist playlist, ImageButton button) {
        boolean currentState = playlist.isSaved();
        playlist.setSaved(!currentState);
        button.setImageResource(playlist.isSaved() ? R.drawable.ic_saved : R.drawable.ic_save);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    /**
     * Get a playlist item at a specific position
     * @param position The position of the item to get
     * @return The playlist at the specified position
     */
    public Playlist getItem(int position) {
        if (position >= 0 && position < playlists.size()) {
            return playlists.get(position);
        }
        return null;
    }

    public void setPlaylists(List<Playlist> playlists) {
        this.playlists = playlists != null ? playlists : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void updateData(List<Playlist> newPlaylists) {
        if (newPlaylists != null) {
            this.playlists = newPlaylists;
            notifyDataSetChanged();
            Log.d(TAG, "Updated playlists data with " + newPlaylists.size() + " items");
        } else {
            Log.w(TAG, "Attempted to update with null playlist data");
        }
    }

    public void addData(List<Playlist> newPlaylists) {
        if (newPlaylists != null) {
            int startPosition = this.playlists.size();
            this.playlists.addAll(newPlaylists);
            notifyItemRangeInserted(startPosition, newPlaylists.size());
            Log.d(TAG, "Added " + newPlaylists.size() + " new playlists");
        } else {
            Log.w(TAG, "Attempted to add null playlist data");
        }
    }

    public void updateTracks(List<Track> tracks) {
        this.tracks.clear();
        if (tracks != null) {
            this.tracks.addAll(tracks);
        }
        notifyDataSetChanged();
    }

    /**
     * Set the list of tracks and update the adapter
     * @param tracks The list of tracks to set
     */
    public void setItems(List<Track> tracks) {
        this.tracks.clear();
        if (tracks != null) {
            this.tracks.addAll(tracks);
        }
        notifyDataSetChanged();
    }

    @Override
    public void clearData() {
        this.playlists.clear();
        this.tracks.clear();
        notifyDataSetChanged();
    }

    public void setOnTrackClickListener(OnTrackClickListener listener) {
        this.trackListener = listener;
    }

    /**
     * Check if the adapter has no items
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return playlists == null || playlists.isEmpty();
    }

    public void updatePlaylists(List<Playlist> playlists) {
        this.playlists.clear();
        if (playlists != null) {
            this.playlists.addAll(playlists);
        }
        notifyDataSetChanged();
    }

    public class PlaylistViewHolder extends RecyclerView.ViewHolder {
        public ImageView ivPlaylistCover;
        public TextView tvPlaylistName;
        public TextView tvCreator;
        public TextView tvTrackCount;
        public ImageView collaborativeBadge;
        public ImageButton btnLovePlaylist;
        public ImageButton btnSave;

        public PlaylistViewHolder(View view) {
            super(view);
            // Initialize views with null checks
            ivPlaylistCover = view.findViewById(R.id.ivPlaylistCover);
            if (ivPlaylistCover == null) {
                Log.e("PlaylistAdapter", "Failed to find ivPlaylistCover");
            }
            
            tvPlaylistName = view.findViewById(R.id.tvPlaylistName);
            if (tvPlaylistName == null) {
                Log.e("PlaylistAdapter", "Failed to find tvPlaylistName");
            }
            
            tvCreator = view.findViewById(R.id.tvCreator);
            if (tvCreator == null) {
                Log.e("PlaylistAdapter", "Failed to find tvCreator");
            }
            
            tvTrackCount = view.findViewById(R.id.tvTrackCount);
            if (tvTrackCount == null) {
                Log.e("PlaylistAdapter", "Failed to find tvTrackCount");
            }
            
            collaborativeBadge = view.findViewById(R.id.collaborativeBadge);
            if (collaborativeBadge == null) {
                Log.e("PlaylistAdapter", "Failed to find collaborativeBadge");
            }
            
            btnLovePlaylist = view.findViewById(R.id.btnLovePlaylist);
            if (btnLovePlaylist == null) {
                Log.e("PlaylistAdapter", "Failed to find btnLovePlaylist");
            }
            
            btnSave = view.findViewById(R.id.btnSave);
            if (btnSave == null) {
                Log.e("PlaylistAdapter", "Failed to find btnSave");
            }
        }
    }
}