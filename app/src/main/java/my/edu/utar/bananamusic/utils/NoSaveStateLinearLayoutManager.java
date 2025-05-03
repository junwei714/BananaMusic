package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A LinearLayoutManager that doesn't save its state, to prevent crashes
 * with NullPointerException when calling ViewHolder.getLayoutPosition() on a null object
 * 
 * Also optimized for performance with increased prefetch and smoother scrolling
 */
public class NoSaveStateLinearLayoutManager extends LinearLayoutManager {
    
    // Performance constants
    private static final int DEFAULT_EXTRA_LAYOUT_SPACE = 600; // Extra layout space in pixels
    private static final int DEFAULT_INITIAL_PREFETCH_ITEM_COUNT = 10;

    public NoSaveStateLinearLayoutManager(Context context) {
        super(context);
        init();
    }

    public NoSaveStateLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
        init();
    }

    public NoSaveStateLinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    
    /**
     * Initialize with performance optimizations
     */
    private void init() {
        // Performance improvements
        setItemPrefetchEnabled(true);
        setInitialPrefetchItemCount(DEFAULT_INITIAL_PREFETCH_ITEM_COUNT);
    }

    /**
     * Disable saving state to prevent NullPointerException in getLayoutPosition()
     */
    @Override
    public Parcelable onSaveInstanceState() {
        return null;
    }
    
    /**
     * Disable state restoration as well
     */
    @Override
    public void onRestoreInstanceState(Parcelable state) {
        // Do nothing - don't restore state
    }
    
    /**
     * Add extra space to allow for smoother scrolling
     */
    @Override
    protected int getExtraLayoutSpace(RecyclerView.State state) {
        return DEFAULT_EXTRA_LAYOUT_SPACE;
    }
    
    /**
     * Override to prevent layout from crashing on certain devices
     */
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            super.onLayoutChildren(recycler, state);
        } catch (Exception e) {
            // Catch IndexOutOfBoundsException or any other layout exceptions
        }
    }
    
    /**
     * Prevent crashes during scrolling
     */
    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            return super.scrollVerticallyBy(dy, recycler, state);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Prevent crashes during scrolling
     */
    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            return super.scrollHorizontallyBy(dx, recycler, state);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Optimize for smoother scrolling
     */
    @Override
    public boolean supportsPredictiveItemAnimations() {
        return false; // Disable predictive animations for better performance
    }
} 