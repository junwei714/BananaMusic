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
import my.edu.utar.bananamusic.models.Album;

public class FeaturedAlbumsAdapter extends RecyclerView.Adapter<FeaturedAlbumsAdapter.ViewHolder> {
    private final Context context;
    private List<Album> albums;
    private OnAlbumClickListener listener;

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album);
    }

    public FeaturedAlbumsAdapter(Context context) {
        this.context = context;
        this.albums = new ArrayList<>();
    }

    public void setOnAlbumClickListener(OnAlbumClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_featured_album, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Album album = albums.get(position);
        holder.bind(album);
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public void setAlbums(List<Album> newAlbums) {
        this.albums = newAlbums;
        notifyDataSetChanged();
    }

    public void addAlbums(List<Album> newAlbums) {
        int startPosition = albums.size();
        albums.addAll(newAlbums);
        notifyItemRangeInserted(startPosition, newAlbums.size());
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView albumArt;
        private final TextView albumTitle;
        private final TextView artistName;

        ViewHolder(View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.iv_album_art);
            albumTitle = itemView.findViewById(R.id.tv_album_title);
            artistName = itemView.findViewById(R.id.tv_artist_name);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onAlbumClick(albums.get(position));
                }
            });
        }

        void bind(Album album) {
            // Set album title
            albumTitle.setText(album.getName());
            
            // Set artist name
            artistName.setText(album.getArtist());

            // Load album art
            String imageUrl = album.getImageUrl();
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = album.getAlbumArtUrl(); // Fallback to albumArtUrl if imageUrl is empty
            }

            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_album_placeholder)
                    .error(R.drawable.ic_album_placeholder)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(albumArt);
            } else {
                albumArt.setImageResource(R.drawable.ic_album_placeholder);
            }
        }
    }
} 