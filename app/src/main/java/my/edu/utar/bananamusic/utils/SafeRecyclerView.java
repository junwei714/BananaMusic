package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A custom RecyclerView that disables state saving to prevent crashes
 */
public class SafeRecyclerView extends RecyclerView {

    public SafeRecyclerView(@NonNull Context context) {
        super(context);
        init();
    }

    public SafeRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SafeRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }
    
    private void init() {
        setSaveEnabled(false);
    }
    
    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        // Don't save state at all - prevents crash in ViewHolder.getLayoutPosition()
        // Normally we would call: super.dispatchSaveInstanceState(container)
        // But we intentionally avoid it to prevent crashes
    }
    
    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        // Don't restore state
        // Normally we would call: super.dispatchRestoreInstanceState(container)
        // But we intentionally avoid it to prevent crashes
    }
    
    @Override
    protected Parcelable onSaveInstanceState() {
        // Return empty state - don't save anything
        return BaseSavedState.EMPTY_STATE;
    }
    
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        // Don't restore any state
        super.onRestoreInstanceState(BaseSavedState.EMPTY_STATE);
    }
} 