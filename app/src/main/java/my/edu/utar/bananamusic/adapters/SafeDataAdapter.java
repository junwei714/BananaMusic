package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.utils.DataFetcher;
import my.edu.utar.bananamusic.utils.RecyclerViewSafety;
import my.edu.utar.bananamusic.utils.SafeRecyclerViewLayoutManager;

/**
 * A base adapter class for safe handling of RecyclerView data with loading state management
 * @param <T> The data type for the adapter
 * @param <VH> The ViewHolder type for the adapter
 */
public abstract class SafeDataAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // View Types
    public static final int VIEW_TYPE_NORMAL = 0;
    public static final int VIEW_TYPE_LOADING = 1;
    public static final int VIEW_TYPE_ERROR = 2;
    public static final int VIEW_TYPE_EMPTY = 3;

    protected List<T> items;
    protected Context context;
    protected DataFetcher dataFetcher;
    
    // Loading state
    private boolean isLoading = false;
    private boolean hasErrorOccurred = false;
    private String errorMessage = null;
    
    // UI elements for loading state
    private ProgressBar loadingProgressBar;
    private View errorView;
    private TextView errorTextView;
    private View emptyView;
    
    // Retry action
    private Runnable retryAction;
    
    /**
     * Basic constructor
     */
    public SafeDataAdapter(Context context) {
        this.context = context;
        this.items = new ArrayList<>();
        this.dataFetcher = new DataFetcher(context);
    }
    
    /**
     * Constructor with initial data
     */
    public SafeDataAdapter(Context context, List<T> initialItems) {
        this.context = context;
        this.items = new ArrayList<>(initialItems);
        this.dataFetcher = new DataFetcher(context);
    }
    
    /**
     * Set up loading UI elements
     */
    public void setupLoadingViews(ProgressBar progressBar, @NonNull View errorView, 
                                 @NonNull TextView errorTextView, @NonNull View emptyView) {
        this.loadingProgressBar = progressBar;
        this.errorView = errorView;
        this.errorTextView = errorTextView;
        this.emptyView = emptyView;
        
        updateLoadingViews();
    }
    
    /**
     * Clear all data
     */
    public void clearData() {
        int itemCount = items.size();
        items.clear();
        notifyItemRangeRemoved(0, itemCount);
        updateLoadingViews();
    }
    
    /**
     * Set new data
     */
    public void setData(List<T> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
        updateLoadingViews();
    }
    
    /**
     * Update data (clears and adds)
     */
    public void updateData(List<T> newItems) {
        setData(newItems);
    }
    
    /**
     * Add single item
     */
    public void addItem(T item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
        updateLoadingViews();
    }
    
    /**
     * Add multiple items
     */
    public void addItems(List<T> newItems) {
        int startPosition = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(startPosition, newItems.size());
        updateLoadingViews();
    }
    
    /**
     * Remove item at position
     */
    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
            updateLoadingViews();
        }
    }
    
    /**
     * Remove specific item
     */
    public void removeItem(T item) {
        int position = items.indexOf(item);
        if (position != -1) {
            removeItem(position);
        }
    }
    
    /**
     * Get item at position
     */
    public T getItem(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }
    
    /**
     * Get all items
     */
    public List<T> getAllItems() {
        return new ArrayList<>(items);
    }
    
    /**
     * Set loading state
     */
    protected void setLoadingStarted() {
        isLoading = true;
        hasErrorOccurred = false;
        errorMessage = null;
        updateLoadingViews();
        notifyDataSetChanged();
    }
    
    /**
     * Set loading finished state
     */
    protected void setLoadingFinished(boolean success, String errorMsg) {
        isLoading = false;
        hasErrorOccurred = !success;
        errorMessage = errorMsg;
        updateLoadingViews();
        notifyDataSetChanged();
    }
    
    /**
     * Update loading state UI elements
     */
    private void updateLoadingViews() {
        if (loadingProgressBar != null) {
            loadingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        
        if (errorView != null) {
            errorView.setVisibility(hasErrorOccurred ? View.VISIBLE : View.GONE);
        }
        
        if (errorTextView != null && errorMessage != null) {
            errorTextView.setText(errorMessage);
        }
        
        if (emptyView != null) {
            boolean shouldShowEmptyView = !isLoading && !hasErrorOccurred && (items == null || items.isEmpty());
            emptyView.setVisibility(shouldShowEmptyView ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * Set the action to perform when retry is clicked
     */
    public void setRetryAction(Runnable retryAction) {
        this.retryAction = retryAction;
    }
    
    @Override
    public int getItemCount() {
        if (isLoading) {
            return 1; // Show only loading item
        } else if (hasErrorOccurred) {
            return 1; // Show only error item
        } else if (items == null || items.isEmpty()) {
            return 1; // Show empty view
        }
        return items.size();
    }
    
    @Override
    public int getItemViewType(int position) {
        if (isLoading) {
            return VIEW_TYPE_LOADING;
        } else if (hasErrorOccurred) {
            return VIEW_TYPE_ERROR;
        } else if (items == null || items.isEmpty()) {
            return VIEW_TYPE_EMPTY;
        }
        return VIEW_TYPE_NORMAL;
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOADING) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_loading, parent, false);
            return new LoadingViewHolder(view);
        } else if (viewType == VIEW_TYPE_ERROR) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_error, parent, false);
            return new ErrorViewHolder(view);
        } else if (viewType == VIEW_TYPE_EMPTY) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_empty, parent, false);
            return new EmptyViewHolder(view);
        }
        
        // Must be overridden by child classes for normal items
        throw new RuntimeException("onCreateViewHolder for VIEW_TYPE_NORMAL must be implemented by child class");
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadingViewHolder) {
            // Nothing to bind for loading view
        } else if (holder instanceof ErrorViewHolder) {
            ErrorViewHolder errorHolder = (ErrorViewHolder) holder;
            errorHolder.errorMessage.setText(errorMessage != null ? errorMessage : "Error loading data");
            errorHolder.retryButton.setOnClickListener(v -> {
                if (retryAction != null) {
                    setLoadingStarted();
                    retryAction.run();
                }
            });
        } else if (holder instanceof EmptyViewHolder) {
            // Nothing to bind for empty view
        } else {
            // Cast to child's ViewHolder type and bind normal item
            onBindViewHolderInternal((VH) holder, position);
        }
    }
    
    /**
     * Method that must be implemented by child classes to bind their custom ViewHolder
     */
    protected abstract void onBindViewHolderInternal(VH holder, int position);
    
    /**
     * ViewHolder for loading state
     */
    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progressBar;
        
        LoadingViewHolder(View itemView) {
            super(itemView);
            progressBar = itemView.findViewById(R.id.progressBar);
        }
    }
    
    /**
     * ViewHolder for error state
     */
    static class ErrorViewHolder extends RecyclerView.ViewHolder {
        ImageView errorIcon;
        TextView errorMessage;
        Button retryButton;
        
        ErrorViewHolder(View itemView) {
            super(itemView);
            errorIcon = itemView.findViewById(R.id.iv_error);
            errorMessage = itemView.findViewById(R.id.tv_error_message);
            retryButton = itemView.findViewById(R.id.btn_retry);
        }
    }
    
    /**
     * ViewHolder for empty state
     */
    static class EmptyViewHolder extends RecyclerView.ViewHolder {
        TextView emptyMessage;
        
        EmptyViewHolder(View itemView) {
            super(itemView);
            emptyMessage = itemView.findViewById(R.id.tv_empty_message);
        }
    }
    
    /**
     * Check if adapter is currently loading
     */
    public boolean isLoading() {
        return isLoading;
    }
    
    /**
     * Check if adapter has encountered an error
     */
    public boolean hasError() {
        return hasErrorOccurred;
    }
    
    /**
     * Get error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Set a custom DataFetcher implementation
     */
    public void setDataFetcher(DataFetcher dataFetcher) {
        this.dataFetcher = dataFetcher;
    }
    
    /**
     * Setup a RecyclerView with this adapter and safe layout manager
     */
    public void setupWithRecyclerView(RecyclerView recyclerView, boolean isVertical) {
        // Apply safe layout manager
        SafeRecyclerViewLayoutManager layoutManager = new SafeRecyclerViewLayoutManager(
            context, 
            isVertical ? RecyclerView.VERTICAL : RecyclerView.HORIZONTAL,
            false
        );
        
        // Configure for maximum safety
        layoutManager.configureRecyclerViewForMaximumSafety(recyclerView);
        
        // Set layout manager and adapter
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(this);
    }
    
    /**
     * Setup a RecyclerView with this adapter and a grid layout manager
     */
    public void setupWithGridRecyclerView(RecyclerView recyclerView, int spanCount, boolean isVertical) {
        // Create a safe grid layout manager
        GridLayoutManager gridManager = SafeRecyclerViewLayoutManager.createSafeGridLayoutManager(
            context,
            spanCount,
            isVertical ? RecyclerView.VERTICAL : RecyclerView.HORIZONTAL,
            false
        );
        
        // Set span size lookup to handle full-width items like loading and error states
        gridManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int viewType = getItemViewType(position);
                if (viewType == VIEW_TYPE_LOADING || viewType == VIEW_TYPE_ERROR || viewType == VIEW_TYPE_EMPTY) {
                    return spanCount; // Full width for state items
                }
                return 1; // Normal items take 1 span
            }
        });
        
        // Apply safety settings to RecyclerView
        recyclerView.setItemAnimator(null);
        recyclerView.setSaveEnabled(false);
        recyclerView.setHasFixedSize(true);
        
        // Set layout manager and adapter
        recyclerView.setLayoutManager(gridManager);
        recyclerView.setAdapter(this);
    }
} 