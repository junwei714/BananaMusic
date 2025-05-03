// Example of how to fix the ColorStateList issues
// For the setupRecyclerViews method (around line 957)

// Apply a ripple background to make the entire section more touchable-looking
try {
    int[][] states = new int[][] {
        new int[] { android.R.attr.state_pressed },
        new int[] { }
    };
    
    int[] colors = new int[] {
        getResources().getColor(R.color.colorAccent),
        android.graphics.Color.TRANSPARENT
    };
    
    ColorStateList rippleColors = ColorStateList.valueOf(colors[0]);
    
    // ... rest of your existing code ...
} catch (Exception e) {
    Log.e(TAG, "Error enhancing New Releases section: " + e.getMessage());
}

// For the createFallbackMoodToggle method (around line 1716)
// Set FAB properties
fab.setImageResource(android.R.drawable.ic_dialog_info);
fab.setBackgroundTintList(ColorStateList.valueOf(
    getResources().getColor(R.color.colorAccent))); 