// Example of how to fix the navigation code
// For the navigateToMoodRecommendations method (around line 1976)

private void navigateToMoodRecommendations() {
    try {
        // Use the NavController to navigate to the mood recommendations fragment
        NavHostFragment navHostFragment = (NavHostFragment) requireActivity()
                .getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            navController.navigate(R.id.navigation_mood_recommendations);
            
            // Log navigation
            Log.d(TAG, "Navigating to MoodRecommendationsFragment");
        } else {
            Log.e(TAG, "NavHostFragment not found");
            // Fallback: Use FragmentManager directly
            requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, new my.edu.utar.bananamusic.ui.fragments.MoodRecommendationsFragment())
                .addToBackStack(null)
                .commit();
        }
    } catch (Exception e) {
        Log.e(TAG, "Error navigating to mood recommendations: " + e.getMessage(), e);
        Toast.makeText(requireContext(), "Could not open mood recommendations", Toast.LENGTH_SHORT).show();
    }
} 