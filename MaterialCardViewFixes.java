// Example of how to fix the MaterialCardView issues
// For the showCustomMoodInputDialog method (around line 202)

private void showCustomMoodInputDialog() {
    try {
        // Create an AlertDialog.Builder with a custom style
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);
        
        // Inflate the custom mood input layout
        LayoutInflater inflater = getLayoutInflater();
        View customMoodView = inflater.inflate(R.layout.custom_mood_input, null);
        
        // Create a card wrapper for the dialog content
        MaterialCardView cardWrapper = new MaterialCardView(requireContext());
        cardWrapper.setCardElevation(8f);
        cardWrapper.setRadius(16f);
        cardWrapper.addView(customMoodView);
        
        // Add padding to the card
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        cardWrapper.setContentPadding(padding, padding, padding, padding);
        
        // Set the card as the dialog view
        builder.setView(cardWrapper);
        
        // ... rest of your existing method ...
    } catch (Exception e) {
        Log.e(TAG, "Error showing custom mood dialog: " + e.getMessage(), e);
        // Fallback to simple dialog
        showSimpleMoodDialog();
    }
} 