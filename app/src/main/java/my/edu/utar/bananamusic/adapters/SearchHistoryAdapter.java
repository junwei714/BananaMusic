package my.edu.utar.bananamusic.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;

public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {
    private List<String> searchHistory;
    private OnSearchHistoryClickListener listener;

    public interface OnSearchHistoryClickListener {
        void onSearchItemClick(String query);
        void onSearchItemDelete(String query);
    }

    public SearchHistoryAdapter(OnSearchHistoryClickListener listener) {
        this.searchHistory = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String query = searchHistory.get(position);
        holder.searchQueryText.setText(query);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSearchItemClick(query);
            }
        });
        
        // Long press to delete
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onSearchItemDelete(query);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return searchHistory.size();
    }

    public void setSearchHistory(List<String> history) {
        this.searchHistory = new ArrayList<>(history);
        notifyDataSetChanged();
    }

    /**
     * Update the search history data
     * @param history New search history data
     */
    public void updateData(List<String> history) {
        setSearchHistory(history);
    }

    /**
     * Update items in the adapter with new list of search history items
     * @param items The new list of search history items
     */
    public void updateItems(List<String> items) {
        this.searchHistory = new ArrayList<>(items);
        notifyDataSetChanged();
    }

    public void addItem(String query) {
        // Check if query already exists
        if (searchHistory.contains(query)) {
            // Remove the existing item
            int existingPosition = searchHistory.indexOf(query);
            searchHistory.remove(existingPosition);
            notifyItemRemoved(existingPosition);
        }
        
        // Add to the beginning of the list
        searchHistory.add(0, query);
        notifyItemInserted(0);
    }

    public void removeItem(int position) {
        if (position >= 0 && position < searchHistory.size()) {
            searchHistory.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void clearHistory() {
        searchHistory.clear();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView searchQueryText;

        ViewHolder(View itemView) {
            super(itemView);
            searchQueryText = itemView.findViewById(R.id.tv_search_query);
        }
    }
} 