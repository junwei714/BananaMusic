package my.edu.utar.bananamusic.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Utility class that provides methods to safely handle RecyclerView operations
 * to prevent common crashes during state saving and fragment transitions.
 * 
 * Enhanced with improved memory management and support for GridLayoutManager
 */
public class RecyclerViewSafety {
    private static final String TAG = "RecyclerViewSafety";
    
    // Using WeakHashMap to avoid memory leaks with RecyclerViews that are no longer in use
    private static final Map<RecyclerView, Boolean> FIXED_RECYCLERVIEWS = new WeakHashMap<>();
    
    /**
     * Interface for adapters that can clear their data
     * Implementing this interface allows RecyclerViewSafety to safely clear data before detaching
     */
    public interface ClearableAdapter {
        void clearData();
    }
    
    /**
     * Safely clear a RecyclerView by removing its adapter and disabling animations/state saving
     * @param recyclerView The RecyclerView to clear
     */
    public static void clearRecyclerView(final RecyclerView recyclerView) {
        if (recyclerView == null) return;
        
        try {
            // First disable animations to prevent animation callbacks
            recyclerView.setItemAnimator(null);
            
            // Disable state saving
            recyclerView.setSaveEnabled(false);
            
            // Clear any pending scroll operations
            recyclerView.stopScroll();
            
            // Check if the RecyclerView is in a valid state
            boolean isAttachedToWindow = recyclerView.isAttachedToWindow();
            
            // Clear ViewHolder caches if using a SafeRecyclerViewLayoutManager
            if (recyclerView.getLayoutManager() instanceof SafeRecyclerViewLayoutManager) {
                ((SafeRecyclerViewLayoutManager) recyclerView.getLayoutManager()).clearViewCaches();
            }
            
            // First try to clear any adapter data if it's a clearable adapter
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            if (adapter instanceof ClearableAdapter) {
                try {
                    ((ClearableAdapter) adapter).clearData();
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing adapter data: " + e.getMessage());
                }
            }
            
            // Try to remove all views safely
            try {
                if (recyclerView.getLayoutManager() != null) {
                    recyclerView.getLayoutManager().removeAllViews();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing RecyclerView views: " + e.getMessage());
            }
            
            // Clear adapter on the main thread if not already there
            if (Looper.myLooper() == Looper.getMainLooper()) {
                clearAdapterSynchronously(recyclerView, isAttachedToWindow);
            } else {
                // Post to main thread if we're not already there
                new Handler(Looper.getMainLooper()).post(() -> 
                    clearAdapterSynchronously(recyclerView, recyclerView.isAttachedToWindow()));
            }
            
            // Clear any RecyclerView decorations
            try {
                for (int i = recyclerView.getItemDecorationCount() - 1; i >= 0; i--) {
                    recyclerView.removeItemDecorationAt(i);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing item decorations: " + e.getMessage());
            }
            
            // Clear any view focus that might be keeping references
            recyclerView.clearFocus();
            
            // Clear any OnScrollListener instances
            recyclerView.clearOnScrollListeners();
            
        } catch (Exception e) {
            Log.e(TAG, "Error clearing RecyclerView: " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to clear adapter synchronously on the main thread
     */
    private static void clearAdapterSynchronously(RecyclerView recyclerView, boolean isAttached) {
        try {
            // Only try to set adapter to null if it's currently attached
            if (isAttached && recyclerView.getAdapter() != null) {
                recyclerView.swapAdapter(null, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing adapter synchronously: " + e.getMessage(), e);
            // If that fails, try a less aggressive approach
            try {
                if (recyclerView.getAdapter() != null) {
                    // Just notify that all items are removed instead of setting adapter to null
                    int itemCount = recyclerView.getAdapter().getItemCount();
                    if (itemCount > 0) {
                        recyclerView.getAdapter().notifyItemRangeRemoved(0, itemCount);
                    }
                }
            } catch (Exception e2) {
                Log.e(TAG, "Error notifying adapter: " + e2.getMessage());
            }
        }
    }
    
    /**
     * Safely apply a SafeRecyclerViewLayoutManager to a RecyclerView
     * @param recyclerView The RecyclerView to set the layout manager on
     * @param vertical Whether the layout should be vertical (true) or horizontal (false)
     */
    public static void applySafeLayoutManager(RecyclerView recyclerView, boolean vertical) {
        if (recyclerView == null) return;
        
        try {
            // Clear the adapter first to prevent crashes
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            recyclerView.setAdapter(null);
            
            // Create and apply a safe layout manager
            SafeRecyclerViewLayoutManager layoutManager = new SafeRecyclerViewLayoutManager(
                recyclerView.getContext(),
                vertical ? RecyclerView.VERTICAL : RecyclerView.HORIZONTAL,
                false
            );
            
            // Set the layout manager
            recyclerView.setLayoutManager(layoutManager);
            
            // Apply additional safety configurations
            layoutManager.configureRecyclerViewForMaximumSafety(recyclerView);
            
            // Restore the adapter if there was one
            if (adapter != null) {
                recyclerView.setAdapter(adapter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying safe layout manager: " + e.getMessage(), e);
        }
    }
    
    /**
     * Apply a GridLayoutManager with the same safety features
     * @param recyclerView The RecyclerView to set the layout manager on
     * @param spanCount Number of columns in the grid
     * @param vertical Whether the layout should be vertical (true) or horizontal (false)
     */
    public static void applySafeGridLayoutManager(RecyclerView recyclerView, int spanCount, boolean vertical) {
        if (recyclerView == null) return;
        
        try {
            // Clear the adapter first to prevent crashes
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            recyclerView.setAdapter(null);
            
            // Create and apply a safe grid layout manager
            GridLayoutManager layoutManager = SafeRecyclerViewLayoutManager.createSafeGridLayoutManager(
                recyclerView.getContext(), 
                spanCount,
                vertical ? RecyclerView.VERTICAL : RecyclerView.HORIZONTAL, 
                false
            );
            
            // Set the layout manager
            recyclerView.setLayoutManager(layoutManager);
            
            // Apply safety settings to the RecyclerView itself
            recyclerView.setItemAnimator(null);
            recyclerView.setSaveEnabled(false);
            recyclerView.setHasFixedSize(true);
            
            // Restore the adapter if there was one
            if (adapter != null) {
                recyclerView.setAdapter(adapter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying safe grid layout manager: " + e.getMessage(), e);
        }
    }
    
    /**
     * Apply basic safety settings to a RecyclerView without changing its layout manager
     * @param recyclerView The RecyclerView to apply safety settings to
     */
    public static void applySafetySettings(RecyclerView recyclerView) {
        if (recyclerView == null) return;
        
        try {
            // Disable animations
            recyclerView.setItemAnimator(null);
            
            // Disable state saving
            recyclerView.setSaveEnabled(false);
            
            // Improve performance with fixed size if possible
            recyclerView.setHasFixedSize(true);
            
            // Disable nested scrolling which can cause issues
            recyclerView.setNestedScrollingEnabled(false);
            
            // If the layout manager is a LinearLayoutManager but not our safe version,
            // we log a recommendation but don't automatically change it
            RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
            if (manager instanceof LinearLayoutManager && 
                !(manager instanceof SafeRecyclerViewLayoutManager)) {
                Log.w(TAG, "RecyclerView is using a standard LinearLayoutManager. " +
                          "Consider calling applySafeLayoutManager() for better stability.");
            } else if (manager instanceof SafeRecyclerViewLayoutManager) {
                // If it's already our safe manager, apply additional configuration
                ((SafeRecyclerViewLayoutManager) manager).configureRecyclerViewForMaximumSafety(recyclerView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying safety settings: " + e.getMessage(), e);
        }
    }
    
    /**
     * Apply a suite of fixes to all RecyclerViews in a fragment to prevent the 
     * "Attempt to invoke virtual method 'int androidx.recyclerview.widget.RecyclerView$ViewHolder.getLayoutPosition()' on a null object reference"
     * exception that occurs during fragment transitions.
     * 
     * @param fragment The fragment containing RecyclerViews
     */
    public static void fixAllRecyclerViewsInFragment(@NonNull Fragment fragment) {
        try {
            if (fragment.getView() == null) return;
            
            Log.d(TAG, "Applying RecyclerView fixes to fragment: " + fragment.getClass().getSimpleName());
            findAndFixRecyclerViews(fragment.getView());
        } catch (Exception e) {
            Log.e(TAG, "Error fixing RecyclerViews in fragment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Find and fix all RecyclerViews in a view hierarchy
     */
    private static void findAndFixRecyclerViews(View root) {
        if (root == null) return;
        
        // If this view is a RecyclerView, fix it
        if (root instanceof RecyclerView) {
            fixRecyclerView((RecyclerView) root);
        }
        
        // If it's a ViewGroup, check all children
        if (root instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) root;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child != null) {
                    findAndFixRecyclerViews(child);
                }
            }
        }
    }
    
    /**
     * Apply all known fixes to a RecyclerView
     */
    public static void fixRecyclerView(@NonNull RecyclerView recyclerView) {
        try {
            // Skip if already fixed
            if (FIXED_RECYCLERVIEWS.containsKey(recyclerView)) {
                return;
            }
            
            // Apply fixes
            recyclerView.setSaveEnabled(false);
            recyclerView.setItemAnimator(null);
            recyclerView.setHasFixedSize(true);
            preventLayoutParamsSaving(recyclerView);
            
            // Replace with safe layout manager if needed
            if (recyclerView.getLayoutManager() != null && 
                !(recyclerView.getLayoutManager() instanceof SafeRecyclerViewLayoutManager)) {
                
                replaceLayoutManager(recyclerView);
            } else if (recyclerView.getLayoutManager() instanceof SafeRecyclerViewLayoutManager) {
                // Already using safe manager, just configure it optimally
                ((SafeRecyclerViewLayoutManager) recyclerView.getLayoutManager())
                    .configureRecyclerViewForMaximumSafety(recyclerView);
            }
            
            // Mark as fixed
            FIXED_RECYCLERVIEWS.put(recyclerView, Boolean.TRUE);
            Log.d(TAG, "Fixed RecyclerView: " + recyclerView);
        } catch (Exception e) {
            Log.e(TAG, "Error fixing RecyclerView: " + e.getMessage(), e);
        }
    }
    
    /**
     * Prevent layout params from saving state
     */
    private static void preventLayoutParamsSaving(RecyclerView recyclerView) {
        try {
            // Disable state saving for all children
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View child = recyclerView.getChildAt(i);
                if (child != null) {
                    child.setSaveEnabled(false);
                    
                    // For ViewGroups, prevent state saving recursively
                    if (child instanceof ViewGroup) {
                        preventStateSavingRecursively((ViewGroup) child);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error disabling layout params saving: " + e.getMessage());
        }
    }
    
    /**
     * Recursively prevent state saving in view hierarchy
     */
    private static void preventStateSavingRecursively(ViewGroup viewGroup) {
        if (viewGroup == null) return;
        
        try {
            viewGroup.setSaveEnabled(false);
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child != null) {
                    child.setSaveEnabled(false);
                    if (child instanceof ViewGroup) {
                        preventStateSavingRecursively((ViewGroup) child);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in preventStateSavingRecursively: " + e.getMessage());
        }
    }
    
    /**
     * Replace the layout manager with our safe version
     */
    private static void replaceLayoutManager(RecyclerView recyclerView) {
        try {
            if (recyclerView == null || recyclerView.getLayoutManager() == null) return;
            
            RecyclerView.LayoutManager oldManager = recyclerView.getLayoutManager();
            
            if (oldManager instanceof GridLayoutManager) {
                GridLayoutManager gridManager = (GridLayoutManager) oldManager;
                
                // Save properties before replacement
                int spanCount = gridManager.getSpanCount();
                int orientation = gridManager.getOrientation();
                boolean reverseLayout = gridManager.getReverseLayout();
                
                // Create a new safe grid layout manager with the same properties
                GridLayoutManager safeGridManager = SafeRecyclerViewLayoutManager.createSafeGridLayoutManager(
                    recyclerView.getContext(),
                    spanCount,
                    orientation,
                    reverseLayout
                );
                
                // Transfer the SpanSizeLookup if it exists
                GridLayoutManager.SpanSizeLookup spanSizeLookup = gridManager.getSpanSizeLookup();
                if (spanSizeLookup != null && !(spanSizeLookup instanceof GridLayoutManager.DefaultSpanSizeLookup)) {
                    safeGridManager.setSpanSizeLookup(spanSizeLookup);
                }
                
                // Get current adapter
                RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                
                // Apply new layout manager
                recyclerView.setLayoutManager(safeGridManager);
                
                // Ensure adapter is still set
                if (adapter != null && recyclerView.getAdapter() == null) {
                    recyclerView.setAdapter(adapter);
                }
                
                Log.d(TAG, "Replaced GridLayoutManager with SafeGridLayoutManager");
                
            } else if (oldManager instanceof LinearLayoutManager) {
                LinearLayoutManager linearManager = (LinearLayoutManager) oldManager;
                
                // Save properties before replacement
                int orientation = linearManager.getOrientation();
                boolean reverseLayout = linearManager.getReverseLayout();
                boolean stackFromEnd = linearManager.getStackFromEnd();
                
                // Create a new safe layout manager with the same properties
                SafeRecyclerViewLayoutManager safeManager = new SafeRecyclerViewLayoutManager(
                    recyclerView.getContext(),
                    orientation,
                    reverseLayout
                );
                
                // Apply same stack from end
                safeManager.setStackFromEnd(stackFromEnd);
                
                // Get current adapter
                RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                
                // Apply new layout manager
                recyclerView.setLayoutManager(safeManager);
                
                // Configure for maximum safety
                safeManager.configureRecyclerViewForMaximumSafety(recyclerView);
                
                // Ensure adapter is still set
                if (adapter != null && recyclerView.getAdapter() == null) {
                    recyclerView.setAdapter(adapter);
                }
                
                Log.d(TAG, "Replaced LinearLayoutManager with SafeRecyclerViewLayoutManager");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error replacing layout manager: " + e.getMessage());
        }
    }
    
    /**
     * Apply fix on a specific fragment containing RecyclerViews
     * This should be called in onViewCreated of a fragment
     * 
     * @param fragment The fragment to fix
     */
    public static void applyFix(Fragment fragment) {
        try {
            fixAllRecyclerViewsInFragment(fragment);
        } catch (Exception e) {
            Log.e(TAG, "Error applying fix to fragment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Clean up any references when a fragment is being destroyed
     * @param fragment The fragment being destroyed
     */
    public static void cleanUpFragment(@Nullable Fragment fragment) {
        if (fragment == null || fragment.getView() == null) return;
        
        try {
            findAndClearRecyclerViews(fragment.getView());
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up fragment RecyclerViews: " + e.getMessage());
        }
    }
    
    /**
     * Find and clear all RecyclerViews in a view hierarchy
     */
    private static void findAndClearRecyclerViews(View root) {
        if (root == null) return;
        
        if (root instanceof RecyclerView) {
            clearRecyclerView((RecyclerView) root);
        }
        
        if (root instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) root;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child != null) {
                    findAndClearRecyclerViews(child);
                }
            }
        }
    }
} 