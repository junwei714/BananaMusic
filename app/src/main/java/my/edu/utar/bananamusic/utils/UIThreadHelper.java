package my.edu.utar.bananamusic.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Helper class to run code on the UI thread safely
 */
public class UIThreadHelper {
    private static final String TAG = "UIThreadHelper";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    /**
     * Run a Runnable on the UI thread safely
     * @param runnable The code to run on the UI thread
     */
    public static void runOnUiThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Already on UI thread, run directly
            try {
                runnable.run();
            } catch (Exception e) {
                Log.e(TAG, "Error running on UI thread", e);
            }
        } else {
            // Post to main handler
            mainHandler.post(() -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    Log.e(TAG, "Error running on UI thread via handler", e);
                }
            });
        }
    }
    
    /**
     * Run a Runnable on the main thread (alias for runOnUiThread for backward compatibility)
     * @param runnable The code to run on the main thread
     */
    public static void runOnMainThread(Runnable runnable) {
        runOnUiThread(runnable);
    }
    
    /**
     * Run a Runnable on the UI thread with a delay
     * @param runnable The code to run on the UI thread
     * @param delayMillis The delay in milliseconds
     */
    public static void runOnUiThreadDelayed(Runnable runnable, long delayMillis) {
        mainHandler.postDelayed(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                Log.e(TAG, "Error running delayed on UI thread", e);
            }
        }, delayMillis);
    }
    
    /**
     * Run a Runnable on the main thread with a delay (alias for backward compatibility)
     * @param runnable The code to run on the main thread
     * @param delayMillis The delay in milliseconds
     */
    public static void runOnMainThreadDelayed(Runnable runnable, long delayMillis) {
        runOnUiThreadDelayed(runnable, delayMillis);
    }
    
    /**
     * Check if the current thread is the main thread
     * @return true if on the main thread, false otherwise
     */
    public static boolean isOnMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }
    
    /**
     * Ensure the current operation is running on the main thread
     * @throws IllegalStateException if not on the main thread
     */
    public static void ensureMainThread() {
        if (!isOnMainThread()) {
            String errorMsg = "This operation must run on the main thread";
            Log.e(TAG, errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }
    
    /**
     * Remove any pending Runnables from the queue
     */
    public static void clearPendingRunnables() {
        mainHandler.removeCallbacksAndMessages(null);
    }
} 