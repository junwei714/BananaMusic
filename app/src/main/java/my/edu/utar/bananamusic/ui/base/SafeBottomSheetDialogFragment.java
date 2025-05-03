package my.edu.utar.bananamusic.ui.base;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import my.edu.utar.bananamusic.utils.SafeRecyclerViewLayoutManager;

/**
 * Base bottom sheet dialog fragment class that automatically applies RecyclerView safety fixes
 * to prevent the 'ViewHolder.getLayoutPosition() on a null object reference' exception
 * that occurs during fragment transitions.
 */
public class SafeBottomSheetDialogFragment extends BottomSheetDialogFragment {
    private static final String TAG = "SafeBottomSheetDialogFragment";
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            // Find and fix all RecyclerViews in this fragment
            findAndFixRecyclerViews(view);
        } catch (Exception e) {
            Log.e(TAG, "Error applying RecyclerView safety fixes: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        try {
            // Find all RecyclerViews and make sure they don't save state
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
            // Find and clear all RecyclerViews to prevent memory leaks and state saving issues
            if (getView() != null) {
                findAndClearRecyclerViews(getView());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing RecyclerViews in onDestroyView: " + e.getMessage(), e);
        }
        
        // Use a handler to detach adapters after the view is destroyed
        // This prevents race conditions during fragment transitions
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Additional cleanup after view is destroyed
                System.gc();
            } catch (Exception e) {
                Log.e(TAG, "Error in post-destroy cleanup: " + e.getMessage(), e);
            }
        });
        
        super.onDestroyView();
    }
    
    /**
     * Recursively find and apply safe layout managers to all RecyclerViews
     */
    private void findAndFixRecyclerViews(View view) {
        if (view instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) view;
            try {
                // Disable state saving
                recyclerView.setSaveEnabled(false);
                
                // Apply safe layout manager
                RecyclerView.LayoutManager currentLayoutManager = recyclerView.getLayoutManager();
                if (currentLayoutManager instanceof LinearLayoutManager && 
                    !(currentLayoutManager instanceof SafeRecyclerViewLayoutManager)) {
                    
                    LinearLayoutManager linearLayoutManager = (LinearLayoutManager) currentLayoutManager;
                    
                    // Create safe layout manager with same properties
                    SafeRecyclerViewLayoutManager safeLayoutManager = new SafeRecyclerViewLayoutManager(
                        recyclerView.getContext(),
                        linearLayoutManager.getOrientation(),
                        linearLayoutManager.getReverseLayout()
                    );
                    
                    // Copy important properties
                    safeLayoutManager.setItemPrefetchEnabled(false);
                    safeLayoutManager.setInitialPrefetchItemCount(0);
                    
                    // Get current state
                    RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                    
                    // Apply new layout manager
                    recyclerView.setLayoutManager(safeLayoutManager);
                    
                    // Ensure adapter is still set
                    if (adapter != null && recyclerView.getAdapter() == null) {
                        recyclerView.setAdapter(adapter);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error applying safe layout manager: " + e.getMessage(), e);
            }
        }
        
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup viewGroup = (android.view.ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child != null) {
                    findAndFixRecyclerViews(child);
                }
            }
        }
    }
    
    /**
     * Recursively find and clear all RecyclerViews in a view hierarchy
     */
    private void findAndClearRecyclerViews(View view) {
        if (view instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) view;
            try {
                recyclerView.setItemAnimator(null);
                recyclerView.setSaveEnabled(false);
                recyclerView.setAdapter(null);
            } catch (Exception e) {
                Log.e(TAG, "Error clearing RecyclerView: " + e.getMessage(), e);
            }
        }
        
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup viewGroup = (android.view.ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child != null) {
                    findAndClearRecyclerViews(child);
                }
            }
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
            } catch (Exception e) {
                Log.e(TAG, "Error preventing RecyclerView state saving: " + e.getMessage(), e);
            }
        }
        
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup viewGroup = (android.view.ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child != null) {
                    preventRecyclerViewStateSaving(child);
                }
            }
        }
    }
    
    /**
     * Utility method to safely set a layout manager on a RecyclerView
     */
    protected void useSafeLayoutManager(RecyclerView recyclerView) {
        if (recyclerView != null) {
            try {
                SafeRecyclerViewLayoutManager layoutManager = new SafeRecyclerViewLayoutManager(
                    recyclerView.getContext()
                );
                recyclerView.setLayoutManager(layoutManager);
                recyclerView.setSaveEnabled(false);
            } catch (Exception e) {
                Log.e(TAG, "Error setting safe layout manager: " + e.getMessage(), e);
            }
        }
    }
} 