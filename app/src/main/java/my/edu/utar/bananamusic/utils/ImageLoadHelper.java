package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.DrawableRes;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import my.edu.utar.bananamusic.R;

/**
 * Enhanced helper class for loading images with Glide
 */
public class ImageLoadHelper {
    private static final String TAG = "ImageLoadHelper";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int MAX_RETRIES = 2;

    public interface ImageLoadCallback {
        void onSuccess();
        void onError(String message);
    }

    /**
     * Check if the device has internet connectivity
     */
    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    /**
     * Generate a placeholder URL for a playlist
     */
    private static String generatePlaceholderUrl(String name, String color) {
        try {
            String encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20");
            return String.format("https://placehold.co/400x400/%s/ffffff?text=%s", color, encodedName);
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Error encoding name for placeholder: " + e.getMessage());
            return String.format("https://placehold.co/400x400/%s/ffffff?text=%s", color, name.replace(" ", "-"));
        }
    }

    /**
     * Load an image from a URL into an ImageView with placeholder and error fallback
     * @param context Context for Glide
     * @param imageUrl URL of the image to load
     * @param imageView ImageView to load into
     * @param placeholderId Resource ID for the placeholder/error image
     */
    public static void loadImage(Context context, String imageUrl, ImageView imageView, int placeholderId) {
        if (context == null || imageView == null) return;
        
        // Validate and sanitize URL
        String validatedUrl = validateImageUrl(imageUrl);
        
        if (validatedUrl != null) {
            // Use enhanced Glide settings
            Glide.with(context)
                .load(validatedUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both original & resized image
                .placeholder(placeholderId)
                .error(placeholderId)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Image load failed for URL: " + validatedUrl + (e != null ? " - " + e.getMessage() : ""));
                        return false; // Let Glide handle the error image
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        return false; // Let Glide handle setting the resource
                    }
                })
                .into(imageView);
        } else {
            imageView.setImageResource(placeholderId);
        }
    }
    
    /**
     * Load an image from a URL into an ImageView with rounded corners
     * @param context Context for Glide
     * @param imageUrl URL of the image to load
     * @param imageView ImageView to load into
     * @param placeholderId Resource ID for the placeholder/error image
     * @param cornerRadius Corner radius in dp
     */
    public static void loadRoundedImage(Context context, String imageUrl, ImageView imageView, int placeholderId, int cornerRadius) {
        if (context == null || imageView == null) return;
        
        // Validate and sanitize URL
        String validatedUrl = validateImageUrl(imageUrl);
        
        RequestOptions options = new RequestOptions()
            .placeholder(placeholderId)
            .error(placeholderId)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(new RoundedCorners(cornerRadius));
            
        if (validatedUrl != null) {
            Glide.with(context)
                .load(validatedUrl)
                .apply(options)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Rounded image load failed for URL: " + validatedUrl + (e != null ? " - " + e.getMessage() : ""));
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(imageView);
        } else {
            imageView.setImageResource(placeholderId);
        }
    }
    
    /**
     * Load an image for a playlist with appropriate corners and error handling
     * @param context Context for Glide
     * @param imageUrl URL of the image to load
     * @param imageView ImageView to load into
     * @param placeholderId Resource ID for the placeholder/error image
     * @param cornerRadius Corner radius in dp
     */
    public static void loadPlaylistImage(Context context, String imageUrl, ImageView imageView, @DrawableRes int placeholderId, int cornerRadius) {
        if (context == null || imageView == null) {
            Log.e(TAG, "Invalid context or imageView");
            return;
        }

        String validatedUrl = validateImageUrl(imageUrl);
        Log.d(TAG, "Loading playlist image with URL: " + validatedUrl);
        
        if (!isNetworkAvailable(context)) {
            Log.w(TAG, "No network connection available, using placeholder");
            imageView.setImageResource(placeholderId);
            return;
        }

        RequestOptions requestOptions = new RequestOptions()
            .centerCrop()
            .transform(new RoundedCorners(cornerRadius))
            .placeholder(placeholderId)
            .error(placeholderId)
            .diskCacheStrategy(DiskCacheStrategy.ALL);

        try {
            Context applicationContext = context.getApplicationContext();
            
            if (validatedUrl != null && !validatedUrl.isEmpty()) {
                Glide.with(applicationContext)
                    .load(validatedUrl)
                    .apply(requestOptions)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Failed to load playlist image: " + validatedUrl + (e != null ? ", Error: " + e.getMessage() : ""));
                            
                            // Try alternative URL if this is a Spotify URL using Handler
                            if (validatedUrl.contains("i.scdn.co") && !validatedUrl.contains("retry")) {
                                String altUrl = validatedUrl.replace("i.scdn.co", "i.spotify.com") + "?retry=true";
                                Log.d(TAG, "Trying alternative Spotify URL: " + altUrl);
                                
                                mainHandler.post(() -> {
                                    // Load with alternative URL
                                    Glide.with(applicationContext)
                                        .load(altUrl)
                                        .apply(requestOptions)
                                        .listener(new RequestListener<Drawable>() {
                                            @Override
                                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                                // Generate and use placeholder for final fallback
                                                String placeholderUrl = generatePlaceholderUrl(
                                                    validatedUrl.substring(validatedUrl.lastIndexOf("/") + 1),
                                                    "6C4AB6"
                                                );
                                                mainHandler.post(() -> {
                                                    Glide.with(applicationContext)
                                                        .load(placeholderUrl)
                                                        .apply(requestOptions)
                                                        .into(imageView);
                                                });
                                                return true;
                                            }

                                            @Override
                                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                                return false;
                                            }
                                        })
                                        .into(imageView);
                                });
                            } else {
                                // Generate and use placeholder for final fallback
                                String placeholderUrl = generatePlaceholderUrl(
                                    validatedUrl.substring(validatedUrl.lastIndexOf("/") + 1),
                                    "6C4AB6"
                                );
                                mainHandler.post(() -> {
                                    Glide.with(applicationContext)
                                        .load(placeholderUrl)
                                        .apply(requestOptions)
                                        .into(imageView);
                                });
                            }
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.d(TAG, "Successfully loaded playlist image: " + validatedUrl);
                            return false;
                        }
                    })
                    .into(imageView);
            } else {
                // Use placeholder for null or empty URLs
                imageView.setImageResource(placeholderId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading image: " + e.getMessage());
            imageView.setImageResource(placeholderId);
        }
    }
    
    /**
     * Validates and sanitizes an image URL
     * @param url URL to validate
     * @return Valid URL or null if invalid
     */
    private static String validateImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        
        url = url.trim();
        
        // Handle placeholder URLs
        if (url.contains("placehold.co")) {
            return url;
        }
        
        // Handle Spotify URLs
        if (url.contains("i.scdn.co") || url.contains("i.spotify.com")) {
            // Make sure we're using HTTPS
            if (url.startsWith("http://")) {
                url = url.replace("http://", "https://");
            }
            // Add size parameter if missing
            if (!url.contains("/image/")) {
                url = url.replace("/image", "/image/w300");
            }
        }
        
        // Handle Deezer URLs
        if (url.contains("cdns-images.dzcdn.net")) {
            // Make sure we're using HTTPS
            if (url.startsWith("http://")) {
                url = url.replace("http://", "https://");
            }
        }

        Log.d(TAG, "Validated image URL: " + url);
        return url;
    }
} 