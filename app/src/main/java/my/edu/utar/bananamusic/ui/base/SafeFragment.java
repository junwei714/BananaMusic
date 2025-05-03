package my.edu.utar.bananamusic.ui.base;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import my.edu.utar.bananamusic.utils.RecyclerViewSafety;
import my.edu.utar.bananamusic.utils.SafeRecyclerViewLayoutManager;

/**
 * A base fragment class that automatically applies safety fixes to RecyclerView instances
 * to prevent common crashes during fragment transitions.
 * 
 * Enhanced with improved memory management and support for grid layouts.
 */
public class SafeFragment extends Fragment {
    private static final String TAG = "SafeFragment";
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            // Apply enhanced safety fixes to all RecyclerViews
            RecyclerViewSafety.fixAllRecyclerViewsInFragment(this);
        } catch (Exception e) {
            // Fallback to direct method if the utility fails
            Log.e(TAG, "Error in RecyclerViewSafety.fixAllRecyclerViewsInFragment: " + e.getMessage(), e);
            findAndSetupRecyclerViews(view);
        }
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        try {
            // Prevent RecyclerView state saving to avoid crashes
            if (getView() != null) {
                preventRecyclerViewStateSaving(getView());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onSaveInstanceState: " + e.getMessage(), e);
        }
        
        try {
            super.onSaveInstanceState(outState);
        } catch (Exception e) {
            Log.e(TAG, "Error in super.onSaveInstanceState: " + e.getMessage(), e);
            // Create minimal bundle to prevent NPE
            outState.putBoolean("safe_fragment_empty_state", true);
        }
    }
    
    @Override
    public void onDestroyView() {
        try {
            // Use enhanced cleanup for RecyclerViews
            RecyclerViewSafety.cleanUpFragment(this);
        } catch (Exception e) {
            Log.e(TAG, "Error using RecyclerViewSafety.cleanUpFragment: " + e.getMessage(), e);
            
            // Fallback to direct method if the utility fails
            if (getView() != null) {
                findAndClearRecyclerViews(getView());
            }
        }
        
        // Use a handler to trigger garbage collection after the view is destroyed
        // This helps clean up any lingering references
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                // Additional cleanup after view is destroyed
                System.gc();
            } catch (Exception e) {
                Log.e(TAG, "Error in post-destroy cleanup: " + e.getMessage(), e);
            }
        }, 500); // Delay by 500ms to ensure fragment transition is complete
        
        super.onDestroyView();
    }
    
    /**
     * Recursively find and apply safe settings to all RecyclerViews
     * WITHOUT changing the layout manager
     */
    private void findAndSetupRecyclerViews(View view) {
        if (view instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) view;
            try {
                // Apply enhanced safety settings
                RecyclerViewSafety.applySafetySettings(recyclerView);
                
                // If using a standard layout manager, provide warning log
                if (recyclerView.getLayoutManager() != null && 
                    !(recyclerView.getLayoutManager() instanceof SafeRecyclerViewLayoutManager)) {
                    Log.w(TAG, "RecyclerView using standard layout manager. Consider calling useSafeLayoutManager()");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error applying RecyclerView safety settings: " + e.getMessage(), e);
            }
        }
        
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child != null) {
                    findAndSetupRecyclerViews(child);
                }
            }
        }
    }
    
    /**
     * Recursively find and clear all RecyclerViews in a view hierarchy
     */
    private void findAndClearRecyclerViews(View view) {
        if (view == null) return;
        
        try {
            if (view instanceof RecyclerView) {
                RecyclerViewSafety.clearRecyclerView((RecyclerView) view);
            }
            
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                int childCount = viewGroup.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = null;
                    try {
                        child = viewGroup.getChildAt(i);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting child view: " + e.getMessage());
                    }
                    
                    if (child != null) {
                        findAndClearRecyclerViews(child);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in findAndClearRecyclerViews: " + e.getMessage(), e);
        }
    }
    
    /**
     * Prevent RecyclerView state saving before onSaveInstanceState
     */
    private void preventRecyclerViewStateSaving(View view) {
        if (view instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) view;
            try {
                // Disable animations
                recyclerView.setItemAnimator(null);
                
                // Prevent state saving
                recyclerView.setSaveEnabled(false);
                
                // If using safe layout manager, ensure state saving is prevented
                if (recyclerView.getLayoutManager() instanceof SafeRecyclerViewLayoutManager) {
                    ((SafeRecyclerViewLayoutManager) recyclerView.getLayoutManager())
                        .setPreventStateSaving(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error preventing RecyclerView state saving: " + e.getMessage(), e);
            }
        }
        
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child != null) {
                    preventRecyclerViewStateSaving(child);
                }
            }
        }
    }
    
    /**
     * Utility method for fragments to manually set a safe layout manager on a RecyclerView.
     * Call this in onCreateView or onViewCreated BEFORE setting an adapter.
     * 
     * @param recyclerView The RecyclerView to set a safe layout manager on
     * @param vertical true for vertical orientation, false for horizontal
     */
    protected void useSafeLayoutManager(RecyclerView recyclerView, boolean vertical) {
        if (recyclerView != null) {
            try {
                RecyclerViewSafety.applySafeLayoutManager(recyclerView, vertical);
            } catch (Exception e) {
                Log.e(TAG, "Error setting safe layout manager: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Overloaded method that defaults to vertical orientation
     */
    protected void useSafeLayoutManager(RecyclerView recyclerView) {
        useSafeLayoutManager(recyclerView, true);
    }
    
    /**
     * Utility method to apply a safe grid layout manager to a RecyclerView
     * @param recyclerView The RecyclerView to use
     * @param spanCount Number of columns/rows in the grid
     * @param vertical Whether the grid should scroll vertically or horizontally
     */
    protected void useSafeGridLayoutManager(RecyclerView recyclerView, int spanCount, boolean vertical) {
        if (recyclerView != null) {
            try {
                RecyclerViewSafety.applySafeGridLayoutManager(recyclerView, spanCount, vertical);
            } catch (Exception e) {
                Log.e(TAG, "Error setting safe grid layout manager: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Overloaded method that defaults to vertical orientation
     */
    protected void useSafeGridLayoutManager(RecyclerView recyclerView, int spanCount) {
        useSafeGridLayoutManager(recyclerView, spanCount, true);
    }
    
    /**
     * Force cleanup of a specific RecyclerView
     * Useful for manually clearing a RecyclerView outside of the fragment lifecycle
     */
    protected void clearRecyclerView(RecyclerView recyclerView) {
        if (recyclerView != null) {
            RecyclerViewSafety.clearRecyclerView(recyclerView);
        }
    }
} 