package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.SearchResult;
import my.edu.utar.bananamusic.utils.FormatUtils;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder> {

    private final Context context;
    private final List<SearchResult> searchResults;
    private final OnSearchResultListener listener;
    private int currentlyPlayingPosition = -1;
    private boolean isPlaying = false;

    public interface OnSearchResultListener {
        void onItemClick(SearchResult searchResult);
        void onItemClick(SearchResult searchResult, int position);
        void onPlayButtonClick(SearchResult searchResult, int position);
    }

    public SearchResultAdapter(Context context, OnSearchResultListener listener) {
        this.context = context;
        this.searchResults = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_search_result, parent, false);
        return new SearchResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        SearchResult currentItem = searchResults.get(position);
        
        // Load album artwork
        Glide.with(holder.itemView.getContext())
                .load(currentItem.getAlbumArtUrl())
                .placeholder(R.drawable.placeholder_album)
                .into(holder.imageViewArtwork);
        
        // Set text views
        holder.textViewTitle.setText(currentItem.getTitle());
        holder.textViewArtist.setText(currentItem.getArtist());
        holder.textViewDuration.setText(FormatUtils.formatDuration(currentItem.getDuration()));
        
        // Set play button state
        updatePlayButtonState(holder, currentItem);
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(currentItem);
                listener.onItemClick(currentItem, position);
            }
        });
        
        holder.playButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayButtonClick(currentItem, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return searchResults.size();
    }

    public void updateResults(List<SearchResult> newResults) {
        searchResults.clear();
        searchResults.addAll(newResults);
        notifyDataSetChanged();
    }

    public void clearResults() {
        searchResults.clear();
        notifyDataSetChanged();
    }

    public void setPlayingState(int position, boolean isPlaying) {
        if (position < 0 || position >= searchResults.size()) {
            return;
        }
        
        int oldPosition = currentlyPlayingPosition;
        currentlyPlayingPosition = position;
        this.isPlaying = isPlaying;
        
        if (oldPosition >= 0 && oldPosition < searchResults.size()) {
            notifyItemChanged(oldPosition);
        }
        notifyItemChanged(position);
    }

    /**
     * Alias for setPlayingState to maintain compatibility with SearchFragment
     */
    public void updatePlayingState(int position, boolean isPlaying) {
        setPlayingState(position, isPlaying);
    }

    public void stopPlayback() {
        int oldPosition = currentlyPlayingPosition;
        currentlyPlayingPosition = -1;
        isPlaying = false;
        
        if (oldPosition >= 0 && oldPosition < searchResults.size()) {
            notifyItemChanged(oldPosition);
        }
    }

    private void updatePlayButtonState(SearchResultViewHolder holder, SearchResult searchResult) {
        if (searchResult.isPlayable()) {
            if (currentlyPlayingPosition == holder.getAdapterPosition()) {
                holder.playButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            } else {
                holder.playButton.setImageResource(R.drawable.ic_play);
            }
            holder.playButton.setEnabled(true);
            holder.playButton.setAlpha(1.0f);
        } else {
            holder.playButton.setImageResource(R.drawable.ic_play);
            holder.playButton.setEnabled(false);
            holder.playButton.setAlpha(0.5f);
        }
    }

    static class SearchResultViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageViewArtwork;
        final TextView textViewTitle;
        final TextView textViewArtist;
        final TextView textViewDuration;
        final ImageButton playButton;

        SearchResultViewHolder(@NonNull View itemView) {
            super(itemView);
            imageViewArtwork = itemView.findViewById(R.id.iv_track_art);
            textViewTitle = itemView.findViewById(R.id.tv_track_title);
            textViewArtist = itemView.findViewById(R.id.tv_artist_name);
            textViewDuration = itemView.findViewById(R.id.tv_duration);
            playButton = itemView.findViewById(R.id.button_play);
        }
    }
} 