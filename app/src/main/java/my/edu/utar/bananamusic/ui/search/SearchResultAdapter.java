package my.edu.utar.bananamusic.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder> {
    
    private final List<Track> tracks;
    private final OnSearchResultClickListener listener;
    
    public interface OnSearchResultClickListener {
        void onSearchResultClick(Track track);
        void onPlayButtonClick(Track track);
    }
    
    public SearchResultAdapter(List<Track> tracks, OnSearchResultClickListener listener) {
        this.tracks = tracks;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new SearchResultViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.bind(track, listener);
    }
    
    @Override
    public int getItemCount() {
        return tracks.size();
    }
    
    static class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private final ImageView albumArtImageView;
        private final TextView titleTextView;
        private final TextView artistTextView;
        private final TextView albumTextView;
        private final ImageButton playButton;
        
        SearchResultViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.text_track_title);
            artistTextView = itemView.findViewById(R.id.text_track_artist);
            albumTextView = itemView.findViewById(R.id.text_album_name);
            albumArtImageView = itemView.findViewById(R.id.iv_track_art);
            playButton = itemView.findViewById(R.id.button_play);
        }
        
        void bind(Track track, OnSearchResultClickListener listener) {
            // Set text data
            titleTextView.setText(track.getTitle());
            artistTextView.setText(track.getArtist());
            
            // Load album art with Glide
            String albumArtUrl = track.getAlbumArtUrl();
            String imageUrl = track.getImageUrl();
            String urlToLoad = null;
            
            // Try albumArtUrl first, then imageUrl, then fallback to default
            if (albumArtUrl != null && !albumArtUrl.isEmpty()) {
                urlToLoad = albumArtUrl;
            } else if (imageUrl != null && !imageUrl.isEmpty()) {
                urlToLoad = imageUrl;
            }
            
            if (urlToLoad != null) {
                Glide.with(itemView.getContext())
                    .load(urlToLoad)
                    .apply(new RequestOptions()
                        .transform(new RoundedCorners(8))
                        .placeholder(R.drawable.default_album_art)
                        .error(R.drawable.default_album_art))
                    .into(albumArtImageView);
            } else {
                // No image available, use default
                albumArtImageView.setImageResource(R.drawable.default_album_art);
            }
            
            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSearchResultClick(track);
                }
            });
            
            playButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlayButtonClick(track);
                }
            });
        }
    }
} 