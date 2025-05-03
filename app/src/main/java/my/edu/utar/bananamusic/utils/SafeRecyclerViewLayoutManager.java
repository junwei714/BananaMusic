package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Recycler;
import androidx.recyclerview.widget.RecyclerView.State;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A LinearLayoutManager that safely handles state saving/restoration
 * to prevent crashes during fragment transitions.
 * 
 * Enhanced with additional safety features:
 * - Memory leak prevention
 * - Improved error handling
 * - Support for Grid layouts
 * - Better scrolling behavior
 */
public class SafeRecyclerViewLayoutManager extends LinearLayoutManager {
    
    private static final String TAG = "SafeRecyclerViewLM";
    
    // Settings
    private boolean mPreventStateSaving = true;
    private final AtomicBoolean mIsDetaching = new AtomicBoolean(false);
    private WeakReference<RecyclerView> mRecyclerViewRef = null;
    private int mMaxScrollAttempts = 3;
    
    // Constants
    private static final String BUNDLE_KEY_EMPTY_STATE = "empty_state";
    private static final String BUNDLE_KEY_SCROLL_POSITION = "scroll_position";
    private static final String BUNDLE_KEY_SCROLL_OFFSET = "scroll_offset";
    private static final int DEFAULT_CACHE_SIZE = 2; // Default RecyclerView cache size
    
    /**
     * Constructor for vertical orientation
     */
    public SafeRecyclerViewLayoutManager(Context context) {
        super(context);
        initSafeSettings();
    }
    
    /**
     * Constructor for specified orientation
     */
    public SafeRecyclerViewLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
        initSafeSettings();
    }
    
    /**
     * Initialize safe settings for this layout manager
     */
    private void initSafeSettings() {
        // Disable operations that can cause crashes
        setItemPrefetchEnabled(false);
        setSmoothScrollbarEnabled(true);
    }
    
    /**
     * Set the RecyclerView reference when attached to enable advanced safety features
     */
    @Override
    public void onAttachedToWindow(RecyclerView view) {
        super.onAttachedToWindow(view);
        
        try {
            if (view != null) {
                mRecyclerViewRef = new WeakReference<>(view);
                mIsDetaching.set(false);
                
                // Apply additional safety to the RecyclerView
                view.setItemAnimator(null);
                view.setHasFixedSize(true);
                view.setPreserveFocusAfterLayout(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onAttachedToWindow: " + e.getMessage());
        }
    }
    
    @Override
    public Parcelable onSaveInstanceState() {
        if (mPreventStateSaving) {
            try {
                // Create a minimal bundle with position info
                Bundle bundle = new Bundle();
                bundle.putBoolean(BUNDLE_KEY_EMPTY_STATE, true);
                
                // Optionally save minimal state (just scroll position)
                // for possible future use
                if (getChildCount() > 0 && findFirstVisibleItemPosition() >= 0) {
                    bundle.putInt(BUNDLE_KEY_SCROLL_POSITION, findFirstVisibleItemPosition());
                    View firstView = findViewByPosition(findFirstVisibleItemPosition());
                    int offset = firstView != null ? firstView.getTop() : 0;
                    bundle.putInt(BUNDLE_KEY_SCROLL_OFFSET, offset);
                }
                
                return bundle;
            } catch (Exception e) {
                Log.e(TAG, "Error in onSaveInstanceState: " + e.getMessage());
                return new Bundle();
            }
        } else {
            try {
                return super.onSaveInstanceState();
            } catch (Exception e) {
                Log.e(TAG, "Error in super.onSaveInstanceState: " + e.getMessage());
                return new Bundle();
            }
        }
    }
    
    @Override
    public void onRestoreInstanceState(Parcelable state) {
        try {
            if (state instanceof Bundle) {
                Bundle bundle = (Bundle) state;
                if (bundle.getBoolean(BUNDLE_KEY_EMPTY_STATE, false)) {
                    // Our custom state, handle optional position restore
                    if (bundle.containsKey(BUNDLE_KEY_SCROLL_POSITION)) {
                        final int position = bundle.getInt(BUNDLE_KEY_SCROLL_POSITION, 0);
                        final int offset = bundle.getInt(BUNDLE_KEY_SCROLL_OFFSET, 0);
                        
                        // Post to ensure RecyclerView is ready
                        RecyclerView recyclerView = mRecyclerViewRef != null ? mRecyclerViewRef.get() : null;
                        if (recyclerView != null) {
                            recyclerView.post(() -> {
                                try {
                                    scrollToPositionWithOffset(position, offset);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in post-restore scrolling: " + e.getMessage());
                                }
                            });
                        }
                    }
                    return;
                }
            }
            
            // Not our state, let the parent handle it (or pass null to be safe)
            super.onRestoreInstanceState(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in onRestoreInstanceState: " + e.getMessage());
        }
    }
    
    @Override
    public void onDetachedFromWindow(RecyclerView view, Recycler recycler) {
        try {
            // Mark as detaching to prevent operations during cleanup
            mIsDetaching.set(true);
            
            // Clear our reference
            mRecyclerViewRef = null;
            
            // Clear adapter reference when detached - but only if it's being removed
            if (view != null && view.getParent() == null) {
                // Only set adapter to null if the view is fully detached
                if (!view.isAttachedToWindow()) {
                    // Clear adapters by explicitly setting null
                    try {
                        view.setAdapter(null);
                    } catch (Exception e) {
                        Log.e(TAG, "Error clearing adapter: " + e.getMessage());
                    }
                    
                    // Clear any item decorations to prevent memory leaks
                    try {
                        for (int i = view.getItemDecorationCount() - 1; i >= 0; i--) {
                            view.removeItemDecorationAt(i);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error clearing decorations: " + e.getMessage());
                    }
                }
            }
            
            try {
                super.onDetachedFromWindow(view, recycler);
            } catch (Exception e) {
                Log.e(TAG, "Error in super.onDetachedFromWindow: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDetachedFromWindow: " + e.getMessage());
        }
    }
    
    @Override
    public void onLayoutChildren(Recycler recycler, State state) {
        // Add try-catch to prevent crashes
        try {
            // Check for detached state
            if (mIsDetaching.get()) {
                Log.w(TAG, "Ignoring onLayoutChildren during detach");
                return;
            }
            
            // Check for null or empty recycler before proceeding
            if (recycler == null || state == null) {
                Log.e(TAG, "Null recycler or state in onLayoutChildren");
                return;
            }

            // Handle the case where the adapter has no items
            if (getItemCount() == 0) {
                detachAndScrapAttachedViews(recycler);
                return;
            }

            // Proceed with normal layout only if we have valid data
            super.onLayoutChildren(recycler, state);
        } catch (NullPointerException e) {
            Log.e(TAG, "NullPointerException in onLayoutChildren: " + e.getMessage(), e);
            // Safely handle NPE - just remove all views without attempting to recycle
            try {
                removeAllViews();
            } catch (Exception e2) {
                Log.e(TAG, "Error removing all views: " + e2.getMessage(), e2);
            }
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "IndexOutOfBoundsException in onLayoutChildren: " + e.getMessage(), e);
            // This happens when data set changes cause inconsistent state
            try {
                removeAllViews();
            } catch (Exception e2) {
                Log.e(TAG, "Error removing all views: " + e2.getMessage(), e2);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onLayoutChildren: " + e.getMessage(), e);
            // Just remove and recycle all views
            try {
                removeAndRecycleAllViews(recycler);
            } catch (Exception e2) {
                Log.e(TAG, "Error in removeAndRecycleAllViews: " + e2.getMessage(), e2);
                // Last resort fallback
                try {
                    removeAllViews();
                } catch (Exception e3) {
                    Log.e(TAG, "Error removing all views: " + e3.getMessage(), e3);
                }
            }
        }
    }
    
    @Override
    public int scrollVerticallyBy(int dy, Recycler recycler, State state) {
        // Avoid scrolling during detachment or if inactive
        if (mIsDetaching.get() || dy == 0) {
            return 0;
        }
        
        // Handle potential crashes during scrolling
        int scrollAttempts = 0;
        while (scrollAttempts < mMaxScrollAttempts) {
            try {
                return super.scrollVerticallyBy(dy, recycler, state);
            } catch (Exception e) {
                Log.e(TAG, "Error in scrollVerticallyBy attempt " + scrollAttempts + ": " + e.getMessage());
                scrollAttempts++;
                
                // Sleep briefly to let things settle
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // All attempts failed, return 0 (no scroll)
        return 0;
    }
    
    @Override
    public int scrollHorizontallyBy(int dx, Recycler recycler, State state) {
        // Avoid scrolling during detachment or if inactive
        if (mIsDetaching.get() || dx == 0) {
            return 0;
        }
        
        // Handle potential crashes during scrolling
        int scrollAttempts = 0;
        while (scrollAttempts < mMaxScrollAttempts) {
            try {
                return super.scrollHorizontallyBy(dx, recycler, state);
            } catch (Exception e) {
                Log.e(TAG, "Error in scrollHorizontallyBy attempt " + scrollAttempts + ": " + e.getMessage());
                scrollAttempts++;
                
                // Sleep briefly to let things settle
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // All attempts failed, return 0 (no scroll)
        return 0;
    }
    
    /**
     * Create a GridLayoutManager version with all the same safety features
     * @param context Context for the GridLayoutManager
     * @param spanCount Number of grid columns
     * @param orientation Grid orientation
     * @param reverseLayout Whether to reverse layout direction
     * @return A safety-enhanced GridLayoutManager
     */
    public static GridLayoutManager createSafeGridLayoutManager(
            Context context, int spanCount, int orientation, boolean reverseLayout) {
        
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, spanCount, orientation, reverseLayout) {
            
            private final AtomicBoolean mIsDetaching = new AtomicBoolean(false);
            
            @Override
            public Parcelable onSaveInstanceState() {
                // Same safety behavior as SafeRecyclerViewLayoutManager
                Bundle bundle = new Bundle();
                bundle.putBoolean(BUNDLE_KEY_EMPTY_STATE, true);
                return bundle;
            }
            
            @Override
            public void onRestoreInstanceState(Parcelable state) {
                // Do nothing, avoid crashes
            }
            
            @Override
            public void onDetachedFromWindow(RecyclerView view, Recycler recycler) {
                mIsDetaching.set(true);
                try {
                    super.onDetachedFromWindow(view, recycler);
                } catch (Exception e) {
                    Log.e(TAG, "Error in grid onDetachedFromWindow: " + e.getMessage());
                }
            }
            
            @Override
            public void onLayoutChildren(Recycler recycler, State state) {
                try {
                    if (mIsDetaching.get()) {
                        return;
                    }
                    super.onLayoutChildren(recycler, state);
                } catch (Exception e) {
                    Log.e(TAG, "Error in grid onLayoutChildren: " + e.getMessage());
                    removeAllViews();
                }
            }
            
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
            
            // Safe span size lookup method - fixed to correctly handle position parameter
            public int getSafeSpanSize(int position) {
                try {
                    GridLayoutManager.SpanSizeLookup lookup = getSpanSizeLookup();
                    if (lookup != null) {
                        return lookup.getSpanSize(position);
                    }
                    return 1;
                } catch (Exception e) {
                    Log.e(TAG, "Error in getSafeSpanSize: " + e.getMessage());
                    return 1;
                }
            }
        };
        
        // Apply safety settings
        gridLayoutManager.setItemPrefetchEnabled(false);
        
        return gridLayoutManager;
    }
    
    @Override
    public boolean supportsPredictiveItemAnimations() {
        // Disable predictive animations to avoid crashes
        return false;
    }
    
    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, State state, int position) {
        try {
            // Bounds checking first
            final int itemCount = getItemCount();
            if (position < 0 || position >= itemCount) {
                Log.e(TAG, "Invalid smoothScrollToPosition: " + position + ", itemCount: " + itemCount);
                return;
            }
            
            super.smoothScrollToPosition(recyclerView, state, position);
        } catch (Exception e) {
            Log.e(TAG, "Error in smoothScrollToPosition: " + e.getMessage());
            // Fallback - instant scroll instead of smooth
            try {
                scrollToPosition(position);
            } catch (Exception e2) {
                Log.e(TAG, "Error in fallback scrollToPosition: " + e2.getMessage());
            }
        }
    }
    
    /**
     * Enable or disable state saving prevention
     * @param preventStateSaving True to prevent state saving (safer), false to allow
     */
    public void setPreventStateSaving(boolean preventStateSaving) {
        this.mPreventStateSaving = preventStateSaving;
    }
    
    /**
     * Override getPosition to prevent NullPointerException when getting ViewHolder position
     */
    @Override
    public int getPosition(View view) {
        try {
            if (view == null) {
                return RecyclerView.NO_POSITION;
            }
            
            if (!(view.getLayoutParams() instanceof RecyclerView.LayoutParams)) {
                return RecyclerView.NO_POSITION;
            }
            
            // Directly get layout position without using ViewHolder
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
            return params.getViewAdapterPosition();
        } catch (Exception e) {
            Log.e(TAG, "Error in getPosition: " + e.getMessage());
            return RecyclerView.NO_POSITION;
        }
    }
    
    /**
     * Override findViewByPosition to add null checking
     */
    @Override
    public View findViewByPosition(int position) {
        try {
            return super.findViewByPosition(position);
        } catch (Exception e) {
            Log.e(TAG, "Error in findViewByPosition: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Override onItemsRemoved to prevent NullPointerException
     */
    @Override
    public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        try {
            super.onItemsRemoved(recyclerView, positionStart, itemCount);
        } catch (Exception e) {
            Log.e(TAG, "Error in onItemsRemoved: " + e.getMessage());
        }
    }
    
    /**
     * Override onItemsAdded to prevent NullPointerException
     */
    @Override
    public void onItemsAdded(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        try {
            super.onItemsAdded(recyclerView, positionStart, itemCount);
        } catch (Exception e) {
            Log.e(TAG, "Error in onItemsAdded: " + e.getMessage());
        }
    }
    
    /**
     * Override onItemsMoved to prevent NullPointerException
     */
    @Override
    public void onItemsMoved(@NonNull RecyclerView recyclerView, int from, int to, int itemCount) {
        try {
            super.onItemsMoved(recyclerView, from, to, itemCount);
        } catch (Exception e) {
            Log.e(TAG, "Error in onItemsMoved: " + e.getMessage());
        }
    }
    
    /**
     * Override onItemsUpdated to prevent NullPointerException
     */
    @Override
    public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        try {
            super.onItemsUpdated(recyclerView, positionStart, itemCount);
        } catch (Exception e) {
            Log.e(TAG, "Error in onItemsUpdated: " + e.getMessage());
        }
    }
    
    /**
     * Override onAdapterChanged to prevent NullPointerException
     */
    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        try {
            super.onAdapterChanged(oldAdapter, newAdapter);
        } catch (Exception e) {
            Log.e(TAG, "Error in onAdapterChanged: " + e.getMessage());
        }
    }
    
    /**
     * Override removeAndRecycleAllViews to prevent NullPointerException
     */
    @Override
    public void removeAndRecycleAllViews(@NonNull Recycler recycler) {
        try {
            // Verify the recycler is valid
            if (recycler == null) {
                Log.e(TAG, "Null recycler in removeAndRecycleAllViews");
                return;
            }
            
            // Get the child count before removing views
            final int childCount = getChildCount();
            
            // Only attempt to recycle if we have child views
            if (childCount > 0) {
                // Safely detach and recycle each view with null checks
                for (int i = childCount - 1; i >= 0; i--) {
                    View child = getChildAt(i);
                    if (child != null) {
                        try {
                            // Safely remove view hierarchy listeners to prevent leaks
                            if (child instanceof ViewGroup) {
                                ViewGroup viewGroup = (ViewGroup) child;
                                viewGroup.setOnHierarchyChangeListener(null);
                            }
                            
                            // Use the safe method to remove and recycle the view
                            removeAndRecycleView(child, recycler);
                        } catch (Exception e) {
                            Log.e(TAG, "Error recycling view at position " + i + ": " + e.getMessage(), e);
                            // If recycling fails, just remove the view
                            removeView(child);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in removeAndRecycleAllViews: " + e.getMessage(), e);
            // Last resort, just try to remove all views without recycling
            try {
                removeAllViews();
            } catch (Exception e2) {
                Log.e(TAG, "Failed to remove all views: " + e2.getMessage(), e2);
            }
        }
    }
    
    /**
     * Helper method to get RecyclerView position for a View safely
     */
    private int getPositionSafely(View child) {
        if (child == null) {
            return RecyclerView.NO_POSITION;
        }
        
        try {
            return getPosition(child);
        } catch (Exception e) {
            Log.e(TAG, "Error getting position: " + e.getMessage(), e);
            return RecyclerView.NO_POSITION;
        }
    }
    
    /**
     * Set maximum scroll recovery attempts
     * @param maxAttempts Maximum number of attempts to make
     */
    public void setMaxScrollAttempts(int maxAttempts) {
        this.mMaxScrollAttempts = Math.max(1, Math.min(10, maxAttempts));
    }
    
    /**
     * Safely clear all pool-cached views to prevent memory leaks
     */
    public void clearViewCaches() {
        RecyclerView recyclerView = mRecyclerViewRef != null ? mRecyclerViewRef.get() : null;
        if (recyclerView != null) {
            try {
                // Clear the RecyclerView pool
                recyclerView.getRecycledViewPool().clear();
                
                // Clear any view cache by setting to 0 then back to default
                recyclerView.setItemViewCacheSize(0);
                recyclerView.setItemViewCacheSize(DEFAULT_CACHE_SIZE);
            } catch (Exception e) {
                Log.e(TAG, "Error clearing view caches: " + e.getMessage());
            }
        }
    }
    
    /**
     * Configure RecyclerView for maximum safety
     */
    public void configureRecyclerViewForMaximumSafety(@Nullable RecyclerView recyclerView) {
        if (recyclerView == null) return;
        
        try {
            // Update our reference
            mRecyclerViewRef = new WeakReference<>(recyclerView);
            
            // Disable animations
            recyclerView.setItemAnimator(null);
            
            // Optimize view cache
            recyclerView.setItemViewCacheSize(20);
            
            // Disable nested scrolling which can cause issues
            recyclerView.setNestedScrollingEnabled(false);
            
            // Ensure fixed size if possible (better performance)
            recyclerView.setHasFixedSize(true);
        } catch (Exception e) {
            Log.e(TAG, "Error configuring RecyclerView for safety: " + e.getMessage());
        }
    }
} 