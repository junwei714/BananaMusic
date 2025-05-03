package my.edu.utar.bananamusic.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

public class AlbumArtColorExtractor {
    
    private static final String TAG = "AlbumArtColorExtractor";
    
    public interface ColorExtractionListener {
        void onColorsExtracted(int primaryColor, int secondaryColor);
        void onExtractionFailed();
    }
    
    /**
     * Extracts dominant colors from album art URL
     * @param url URL of the album art
     * @param context Context for Glide
     * @param listener Listener for color extraction results
     */
    public static void extractColorsFromUrl(String url, android.content.Context context, ColorExtractionListener listener) {
        if (url == null || url.isEmpty()) {
            listener.onExtractionFailed();
            return;
        }
        
        try {
            Glide.with(context)
                .asBitmap()
                .load(url)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        extractColorsFromBitmap(resource, listener);
                    }
                    
                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        listener.onExtractionFailed();
                    }
                    
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // Do nothing
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error loading image for color extraction: " + e.getMessage());
            listener.onExtractionFailed();
        }
    }
    
    /**
     * Extracts dominant colors from a bitmap
     * @param bitmap Bitmap to extract colors from
     * @param listener Listener for color extraction results
     */
    public static void extractColorsFromBitmap(Bitmap bitmap, ColorExtractionListener listener) {
        if (bitmap == null) {
            listener.onExtractionFailed();
            return;
        }
        
        try {
            Palette.from(bitmap).generate(palette -> {
                if (palette == null) {
                    listener.onExtractionFailed();
                    return;
                }
                
                // Get dominant colors
                int primaryColor = palette.getDominantColor(Color.BLACK);
                
                // Darken the primary color for better contrast
                int secondaryColor = darkenColor(primaryColor, 0.7f);
                
                // Ensure colors have enough contrast with white text
                primaryColor = ensureColorContrast(primaryColor);
                secondaryColor = ensureColorContrast(secondaryColor);
                
                listener.onColorsExtracted(primaryColor, secondaryColor);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error extracting colors from bitmap: " + e.getMessage());
            listener.onExtractionFailed();
        }
    }
    
    /**
     * Darkens a color by a given factor
     * @param color The color to darken
     * @param factor Factor to darken by (0-1)
     * @return Darkened color
     */
    private static int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor; // value component
        return Color.HSVToColor(hsv);
    }
    
    /**
     * Ensures a color has good contrast with white text
     * @param color Color to check
     * @return Modified color with better contrast
     */
    private static int ensureColorContrast(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        
        // If the color is too light, darken it
        if (hsv[2] > 0.7f) {
            hsv[2] = 0.7f;
        }
        
        return Color.HSVToColor(hsv);
    }
} 