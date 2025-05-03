package my.edu.utar.bananamusic.adapters;

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

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.RecyclerViewSafety;

/**
 * Adapter for displaying albums in a RecyclerView
 */
public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.ViewHolder> implements RecyclerViewSafety.ClearableAdapter {
    
    private final Context context;
    private List<Track> albums;
    private OnAlbumClickListener clickListener;
    
    public interface OnAlbumClickListener {
        void onAlbumClick(Track album);
    }
    
    public AlbumAdapter(Context context, List<Track> albums) {
        this.context = context;
        this.albums = albums != null ? albums : new ArrayList<>();
    }
    
    public void setOnAlbumClickListener(OnAlbumClickListener listener) {
        this.clickListener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_album_grid, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track album = albums.get(position);
        holder.bind(album);
        
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onAlbumClick(album);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return albums.size();
    }
    
    public void updateData(List<Track> newAlbums) {
        this.albums = newAlbums != null ? newAlbums : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    public void addData(List<Track> newAlbums) {
        if (newAlbums != null) {
            int startPosition = albums.size();
            albums.addAll(newAlbums);
            notifyItemRangeInserted(startPosition, newAlbums.size());
        }
    }
    
    @Override
    public void clearData() {
        int size = albums.size();
        albums.clear();
        notifyItemRangeRemoved(0, size);
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView albumArt;
        private final TextView albumTitle;
        private final TextView artistName;
        
        ViewHolder(View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.iv_album_art);
            albumTitle = itemView.findViewById(R.id.tv_album_title);
            artistName = itemView.findViewById(R.id.tv_artist_name);
        }
        
        void bind(Track album) {
            albumTitle.setText(album.getTitle());
            artistName.setText(album.getArtist());
            
            // Load album art
            String imageUrl = album.getAlbumArt();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.album_placeholder)
                    .error(R.drawable.album_placeholder)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(albumArt);
            } else {
                albumArt.setImageResource(R.drawable.album_placeholder);
            }
        }
    }
} 