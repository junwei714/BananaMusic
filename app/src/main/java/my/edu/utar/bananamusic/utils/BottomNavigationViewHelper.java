package my.edu.utar.bananamusic.utils;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import my.edu.utar.bananamusic.R;

/**
 * Helper class to customize BottomNavigationView appearance
 */
public class BottomNavigationViewHelper {
    private static final String TAG = "NavViewHelper";

    /**
     * Applies custom item layouts to the BottomNavigationView to ensure text labels are visible
     * Also implements animations and custom selection states.
     *
     * @param bottomNavigationView The BottomNavigationView to customize
     */
    public static void applyCustomItemLayouts(@NonNull BottomNavigationView bottomNavigationView) {
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) bottomNavigationView.getChildAt(0);
        
        // Set label visibility mode to unlabeled
        bottomNavigationView.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_UNLABELED);
        
        if (menuView == null) return;
        
        // Get colors from resources
        @ColorInt final int activeColor;
        @ColorInt final int inactiveColor;
        
        try {
            activeColor = ContextCompat.getColor(bottomNavigationView.getContext(), R.color.colorAccent);
            inactiveColor = ContextCompat.getColor(bottomNavigationView.getContext(), R.color.text_secondary);
        } catch (Exception e) {
            Log.e(TAG, "Error getting colors: " + e.getMessage());
            return;
        }
        
        // Get animations
        final Animation scaleUp;
        final Animation scaleDown;
        
        try {
            scaleUp = AnimationUtils.loadAnimation(bottomNavigationView.getContext(), R.anim.nav_item_scale_up);
            scaleDown = AnimationUtils.loadAnimation(bottomNavigationView.getContext(), R.anim.nav_item_scale_down);
        } catch (Exception e) {
            Log.e(TAG, "Error loading animations: " + e.getMessage());
            return;
        }
        
        // Clear existing OnItemSelectedListener
        bottomNavigationView.setOnItemSelectedListener(null);
        
        // Apply custom views to each menu item
        for (int i = 0; i < menuView.getChildCount(); i++) {
            BottomNavigationItemView itemView = (BottomNavigationItemView) menuView.getChildAt(i);
            
            // Remove the original icon and text views
            itemView.removeAllViews();
            
            // Inflate our custom layout
            View customView = LayoutInflater.from(itemView.getContext())
                    .inflate(R.layout.item_bottom_navigation, itemView, false);
            
            // Get references to the icon and label views in our custom layout
            ImageView iconView = customView.findViewById(R.id.icon);
            TextView labelView = customView.findViewById(R.id.label);
            View iconBackground = customView.findViewById(R.id.icon_background);
            
            // Get the associated menu item
            MenuItem menuItem = bottomNavigationView.getMenu().getItem(i);
            
            // Set icon
            iconView.setImageDrawable(menuItem.getIcon());
            
            // Set text
            labelView.setText(menuItem.getTitle());
            
            // Set initial colors based on checked state
            boolean isChecked = menuItem.isChecked();
            iconView.setColorFilter(isChecked ? activeColor : inactiveColor);
            labelView.setTextColor(isChecked ? activeColor : inactiveColor);
            iconBackground.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
            iconBackground.setAlpha(isChecked ? 0.15f : 0f);
            
            // Set up dot indicator
            View activeIndicator = customView.findViewById(R.id.active_indicator);
            if (activeIndicator != null) {
                activeIndicator.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
                if (isChecked) {
                    activeIndicator.setScaleX(1f);
                    activeIndicator.setScaleY(1f);
                } else {
                    activeIndicator.setScaleX(0f);
                    activeIndicator.setScaleY(0f);
                }
            }
            
            // Apply animation if it's the currently selected item
            if (isChecked) {
                iconView.startAnimation(scaleUp);
            }
            
            // Store index to use in onClick
            final int itemIndex = i;
            
            // Add click listener
            customView.setOnClickListener(v -> {
                // Get the menu item at this position
                MenuItem clickedItem = bottomNavigationView.getMenu().getItem(itemIndex);
                boolean wasChecked = clickedItem.isChecked();
                
                // If already checked, don't do anything
                if (wasChecked) return;
                
                // Update all items - animate old selection out, new selection in
                for (int j = 0; j < menuView.getChildCount(); j++) {
                    BottomNavigationItemView childView = (BottomNavigationItemView) menuView.getChildAt(j);
                    View childCustomView = childView.getChildAt(0);
                    
                    if (childCustomView != null) {
                        ImageView childIconView = childCustomView.findViewById(R.id.icon);
                        TextView childLabelView = childCustomView.findViewById(R.id.label);
                        View childIconBg = childCustomView.findViewById(R.id.icon_background);
                        View childActiveIndicator = childCustomView.findViewById(R.id.active_indicator);
                        
                        // Is this the item being selected?
                        boolean isBeingSelected = (j == itemIndex);
                        
                        // Update colors and animations
                        if (isBeingSelected) {
                            // Item being selected - animate in
                            childIconView.setColorFilter(activeColor);
                            childLabelView.setTextColor(activeColor);
                            childIconBg.setVisibility(View.VISIBLE);
                            childActiveIndicator.setVisibility(View.VISIBLE);
                            
                            // Animate background and indicator
                            ValueAnimator bgAnim = ValueAnimator.ofFloat(0f, 0.15f);
                            bgAnim.setDuration(200);
                            bgAnim.addUpdateListener(animation -> {
                                childIconBg.setAlpha((Float) animation.getAnimatedValue());
                            });
                            bgAnim.start();
                            
                            // Scale animation for indicator
                            childActiveIndicator.setScaleX(0);
                            childActiveIndicator.setScaleY(0);
                            childActiveIndicator.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(300)
                                .start();
                                
                            childIconView.startAnimation(scaleUp);
                        } else if (childView.getChildAt(0) != null) {
                            // Item being deselected - animate out
                            childIconView.setColorFilter(inactiveColor);
                            childLabelView.setTextColor(inactiveColor);
                            
                            // Animate background
                            ValueAnimator bgAnim = ValueAnimator.ofFloat(childIconBg.getAlpha(), 0f);
                            bgAnim.setDuration(200);
                            bgAnim.addUpdateListener(animation -> {
                                childIconBg.setAlpha((Float) animation.getAnimatedValue());
                            });
                            bgAnim.start();
                            
                            // Fade out indicator
                            if (childActiveIndicator.getVisibility() == View.VISIBLE) {
                                childActiveIndicator.animate()
                                    .scaleX(0f)
                                    .scaleY(0f)
                                    .setDuration(200)
                                    .withEndAction(() -> childActiveIndicator.setVisibility(View.INVISIBLE))
                                    .start();
                            }
                            
                            childIconView.startAnimation(scaleDown);
                        }
                    }
                }
                
                // Set the menu item as checked
                bottomNavigationView.setSelectedItemId(clickedItem.getItemId());
            });
            
            // Add our custom view to the item
            itemView.addView(customView);
        }
        
        // Setup a regular listener for programmatic selection changes
        bottomNavigationView.setOnItemSelectedListener(item -> {
            // The UI is already updated by our click listeners, so here we just
            // need to handle programmatic selection
            return true;
        });
    }
} 