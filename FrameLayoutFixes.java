// Example of how to fix the FrameLayout issues
// For the updateMoodBackground method (around line 566)

private void updateMoodBackground(String mood) {
    // Find the mood background container
    FrameLayout moodBackgroundContainer = (FrameLayout) getView().findViewById(R.id.moodBackgroundContainer);
    if (moodBackgroundContainer == null) return;
    
    // Find all mood background ImageViews
    ImageView bgHappy = getView().findViewById(R.id.ivBackgroundHappy);
    ImageView bgSad = getView().findViewById(R.id.ivBackgroundSad);
    ImageView bgEnergetic = getView().findViewById(R.id.ivBackgroundEnergetic);
    ImageView bgRelaxed = getView().findViewById(R.id.ivBackgroundRelaxed);
    
    // First, reset all backgrounds to alpha 0
    if (bgHappy != null) bgHappy.animate().alpha(0).setDuration(500).start();
    if (bgSad != null) bgSad.animate().alpha(0).setDuration(500).start(); 
    if (bgEnergetic != null) bgEnergetic.animate().alpha(0).setDuration(500).start();
    if (bgRelaxed != null) bgRelaxed.animate().alpha(0).setDuration(500).start();
    
    // Choose the right background based on mood
    ImageView targetBackground = null;
    float targetAlpha = 0.2f; // Subtle background effect
    
    // ... rest of your existing code ...
}

// For the addRippleEffect method (around line 612)
private void addRippleEffect(String mood) {
    // Get the ripple container
    FrameLayout rippleContainer = (FrameLayout) getView().findViewById(R.id.rippleContainer);
    if (rippleContainer == null) return;
    
    try {
        // Create a new ImageView for the ripple
        ImageView ripple = new ImageView(requireContext());
        ripple.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        // ... rest of your existing code ...
    } catch (Exception e) {
        Log.e(TAG, "Error creating ripple effect: " + e.getMessage());
    }
}

// For the createFallbackMoodToggle method (around line 1720)
private void createFallbackMoodToggle(View parentView) {
    Log.d(TAG, "Creating fallback mood toggle button");
    
    try {
        if (parentView instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) parentView;
            
            // Create a floating action button for mood selection
            FloatingActionButton fab = new FloatingActionButton(requireContext());
            
            // Set FAB properties
            fab.setImageResource(android.R.drawable.ic_dialog_info);
            fab.setBackgroundTintList(ColorStateList.valueOf(
                getResources().getColor(R.color.colorAccent)));
            
            // Set layout params
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            params.setMargins(0, 0, 32, 32);
            fab.setLayoutParams(params);
            
            // ... rest of your existing code ...
        }
    } catch (Exception e) {
        Log.e(TAG, "Error creating fallback mood toggle: " + e.getMessage(), e);
    }
} 