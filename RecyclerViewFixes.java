// Example of how to fix the RecyclerView EdgeEffect implementation
// Replace the existing code in your setupRecyclerViews() method

// For the first RecyclerView (around line 860)
if (rvRecommended != null) {
    useSafeLayoutManager(rvRecommended, false); // Horizontal layout
    rvRecommended.addItemDecoration(itemDecoration);
    rvRecommended.setOverScrollMode(RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS);
    
    // Add fade edge effect
    rvRecommended.setEdgeEffectFactory(new RecyclerView.EdgeEffectFactory() {
        @Override
        protected EdgeEffect createEdgeEffect(RecyclerView view, int direction) {
            EdgeEffect edgeEffect = new EdgeEffect(view.getContext());
            edgeEffect.setColor(getResources().getColor(R.color.colorAccent));
            return edgeEffect;
        }
    });
}

// Similarly for rvTrendingPlaylists (around line 883)
if (rvTrendingPlaylists != null) {
    useSafeLayoutManager(rvTrendingPlaylists, false); // Horizontal layout
    rvTrendingPlaylists.addItemDecoration(itemDecoration);
    rvTrendingPlaylists.setOverScrollMode(RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS);
    
    // Add fade edge effect
    rvTrendingPlaylists.setEdgeEffectFactory(new RecyclerView.EdgeEffectFactory() {
        @Override
        protected EdgeEffect createEdgeEffect(RecyclerView view, int direction) {
            EdgeEffect edgeEffect = new EdgeEffect(view.getContext());
            edgeEffect.setColor(getResources().getColor(R.color.colorPrimary));
            return edgeEffect;
        }
    });
}

// And for rvNewReleases (around line 906)
if (rvNewReleases != null) {
    useSafeLayoutManager(rvNewReleases, false); // Horizontal layout
    rvNewReleases.addItemDecoration(new HorizontalSpacingDecoration(requireContext(), 8)); // Wider spacing
    rvNewReleases.setOverScrollMode(RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS);
    applyItemAnimations(rvNewReleases);
    
    // Add fade edge effect with custom color for New Releases
    rvNewReleases.setEdgeEffectFactory(new RecyclerView.EdgeEffectFactory() {
        @Override
        protected EdgeEffect createEdgeEffect(RecyclerView view, int direction) {
            EdgeEffect edgeEffect = new EdgeEffect(view.getContext());
            edgeEffect.setColor(getResources().getColor(R.color.colorAccent));
            return edgeEffect;
        }
    });
} 