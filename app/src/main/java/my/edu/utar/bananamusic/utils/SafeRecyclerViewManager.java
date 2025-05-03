package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to safely manage RecyclerView state to prevent crashes during
 * fragment transitions and lifecycle changes.
 */
public class SafeRecyclerViewManager {
    private static final String TAG = "SafeRecyclerViewManager";
    
    private static SafeRecyclerViewManager instance;
    private final Map<Integer, Boolean> stateSavingDisabledMap = new HashMap<>();
    
    private SafeRecyclerViewManager() {}
    
    /**
     * Get singleton instance of SafeRecyclerViewManager
     */
    public static synchronized SafeRecyclerViewManager getInstance() {
        if (instance == null) {
            instance = new SafeRecyclerViewManager();
        }
        return instance;
    }
    
    /**
     * Make a RecyclerView safe for fragment transitions by disabling its state saving
     * and providing a safe onSaveInstanceState handler.
     * 
     * @param recyclerView The RecyclerView to make safe
     */
    public void makeRecyclerViewSafe(@NonNull final RecyclerView recyclerView) {
        try {
            final int viewId = recyclerView.getId();
            
            // Skip if already made safe
            if (stateSavingDisabledMap.containsKey(viewId) && stateSavingDisabledMap.get(viewId)) {
                return;
            }
            
            // Perform complete disabling of state saving
            completelyDisableStateSaving(recyclerView);
            
            // Mark this view as safe
            stateSavingDisabledMap.put(viewId, true);
            
            Log.d(TAG, "Made RecyclerView with ID " + viewId + " safe for transitions");
        } catch (Exception e) {
            Log.e(TAG, "Error making RecyclerView safe: " + e.getMessage());
        }
    }
    
    /**
     * Completely disable state saving for a RecyclerView, taking drastic measures if needed
     */
    private void completelyDisableStateSaving(RecyclerView recyclerView) {
        if (recyclerView == null) return;
        
        // Method 1: Disable via standard API
        recyclerView.setSaveEnabled(false);
        
        // Method 2: Clear mLayout field in RecyclerView which prevents state saving
        try {
            Field layoutField = RecyclerView.class.getDeclaredField("mLayout");
            layoutField.setAccessible(true);
            Object mLayout = layoutField.get(recyclerView);
            
            if (mLayout != null && !(mLayout instanceof SafeRecyclerViewLayoutManager)) {
                // Only replace if not already a safe layout manager
                Context context = recyclerView.getContext();
                int orientation = LinearLayoutManager.VERTICAL;
                boolean reverseLayout = false;
                
                if (mLayout instanceof LinearLayoutManager) {
                    // Try to preserve orientation and reverse layout
                    orientation = ((LinearLayoutManager)mLayout).getOrientation();
                    reverseLayout = ((LinearLayoutManager)mLayout).getReverseLayout();
                }
                
                // Replace with our safe manager
                SafeRecyclerViewLayoutManager safeLayoutManager = 
                    new SafeRecyclerViewLayoutManager(context, orientation, reverseLayout);
                recyclerView.setLayoutManager(safeLayoutManager);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error replacing LayoutManager via reflection: " + e.getMessage());
        }
        
        // Method 3: Disable state saving for children recursively
        disableStateSavingForChildren(recyclerView);
        
        // Method 4: Prevent mChildHelper from having children to save state
        try {
            // Force the internal RecyclerView state to have no children during state saving
            Field childHelperField = RecyclerView.class.getDeclaredField("mChildHelper");
            childHelperField.setAccessible(true);
            Object childHelper = childHelperField.get(recyclerView);
            
            if (childHelper != null) {
                // Try to find mHiddenViews field and clear it
                for (Field field : childHelper.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    if (field.getName().contains("Hidden")) {
                        field.set(childHelper, new ArrayList<>());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing child views via reflection: " + e.getMessage());
        }
    }
    
    private void disableStateSavingForChildren(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            child.setSaveEnabled(false);
            if (child instanceof ViewGroup) {
                disableStateSavingForChildren((ViewGroup) child);
            }
        }
    }
    
    /**
     * Safely detach adapters from all RecyclerViews in preparation for fragment destruction.
     * Only use this method when the fragment is actually being destroyed or detached.
     * 
     * @param recyclerViews Array of RecyclerViews to prepare for destruction
     */
    public void prepareForStateChange(RecyclerView... recyclerViews) {
        try {
            for (RecyclerView recyclerView : recyclerViews) {
                if (recyclerView == null) continue;
                
                // Always disable state saving
                recyclerView.setSaveEnabled(false);
                
                // Only clear the adapter if absolutely necessary
                if (recyclerView.getParent() == null || 
                    !recyclerView.isAttachedToWindow() || 
                    recyclerView.getVisibility() != View.VISIBLE) {
                    
                    // Clear adapter reference
                    recyclerView.setAdapter(null);
                    
                    // Clear scroll listeners
                    recyclerView.clearOnScrollListeners();
                }
                
                // Apply complete disabling
                completelyDisableStateSaving(recyclerView);
                
                // Store that this RecyclerView is safe
                stateSavingDisabledMap.put(recyclerView.getId(), true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error preparing RecyclerViews for state change: " + e.getMessage());
        }
    }
    
    /**
     * Handle save instance state for a RecyclerView in a way that prevents crashes
     * 
     * @param recyclerView The RecyclerView to save state for
     * @return A dummy Parcelable that can be safely passed to the parent
     */
    public Parcelable handleSaveInstanceState(RecyclerView recyclerView) {
        try {
            // Always return null to prevent state saving
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error handling save instance state: " + e.getMessage());
            return null;
        }
    }
} 