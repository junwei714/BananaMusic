package my.edu.utar.bananamusic.utils;

import android.util.Log;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Map;

public class PlaybackErrorHandler {
    private static final String TAG = "PlaybackErrorHandler";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second
    
    private final Map<String, AtomicInteger> retryCountMap = new HashMap<>();
    private final Map<String, Long> lastRetryTimeMap = new HashMap<>();
    
    private static PlaybackErrorHandler instance;
    
    public interface ErrorCallback {
        void onRetry();
        void onFallback();
        void onGiveUp(String reason);
    }
    
    private PlaybackErrorHandler() {}
    
    public static synchronized PlaybackErrorHandler getInstance() {
        if (instance == null) {
            instance = new PlaybackErrorHandler();
        }
        return instance;
    }
    
    public void handlePlaybackError(String trackId, int errorCode, String errorMessage, ErrorCallback callback) {
        Log.d(TAG, "Handling playback error for track " + trackId + ": " + errorMessage + " (code: " + errorCode + ")");
        
        // Get or create retry counter for this track
        AtomicInteger retryCount = retryCountMap.computeIfAbsent(trackId, k -> new AtomicInteger(0));
        
        // Check if we should implement backoff
        Long lastRetryTime = lastRetryTimeMap.get(trackId);
        long currentTime = System.currentTimeMillis();
        if (lastRetryTime != null) {
            long timeSinceLastRetry = currentTime - lastRetryTime;
            if (timeSinceLastRetry < RETRY_DELAY_MS) {
                Log.d(TAG, "Too soon to retry, waiting " + (RETRY_DELAY_MS - timeSinceLastRetry) + "ms");
                // Use UIThreadHelper for delayed execution instead of creating a new Handler
                // This prevents potential memory leaks and improves thread management
                my.edu.utar.bananamusic.utils.UIThreadHelper.runOnMainThreadDelayed(
                    () -> handleRetry(trackId, errorCode, callback),
                    RETRY_DELAY_MS - timeSinceLastRetry);
                return;
            }
        }
        
        handleRetry(trackId, errorCode, callback);
    }
    
    private void handleRetry(String trackId, int errorCode, ErrorCallback callback) {
        AtomicInteger retryCount = retryCountMap.get(trackId);
        if (retryCount == null) {
            retryCount = new AtomicInteger(0);
            retryCountMap.put(trackId, retryCount);
        }
        
        // Update last retry time
        lastRetryTimeMap.put(trackId, System.currentTimeMillis());
        
        // Check if we should retry based on error code
        if (shouldRetry(errorCode) && retryCount.get() < MAX_RETRIES) {
            Log.d(TAG, "Retrying playback for track " + trackId + " (attempt " + (retryCount.get() + 1) + "/" + MAX_RETRIES + ")");
            retryCount.incrementAndGet();
            
            // Use UIThreadHelper to ensure callback runs on the main thread
            my.edu.utar.bananamusic.utils.UIThreadHelper.runOnMainThread(() -> {
                callback.onRetry();
            });
        } else if (shouldFallback(errorCode)) {
            Log.d(TAG, "Using fallback for track " + trackId);
            
            // Use UIThreadHelper to ensure callback runs on the main thread
            my.edu.utar.bananamusic.utils.UIThreadHelper.runOnMainThread(() -> {
                callback.onFallback();
            });
            resetTrackCounters(trackId);
        } else {
            Log.d(TAG, "Giving up on track " + trackId + " after " + retryCount.get() + " retries");
            final String reason = getErrorReason(errorCode);
            
            // Use UIThreadHelper to ensure callback runs on the main thread
            my.edu.utar.bananamusic.utils.UIThreadHelper.runOnMainThread(() -> {
                callback.onGiveUp(reason);
            });
            resetTrackCounters(trackId);
        }
    }
    
    private boolean shouldRetry(int errorCode) {
        // Retry on network errors, timeout errors, or temporary service errors
        switch (errorCode) {
            case -1: // Generic error
            case 1001: // Network timeout
            case 1002: // Network error
            case 2001: // Service temporary unavailable
            case 2002: // Rate limited
            case 4001: // Playback error
                return true;
            default:
                return false;
        }
    }
    
    private boolean shouldFallback(int errorCode) {
        // Use fallback for content unavailable errors or geo-restriction errors
        switch (errorCode) {
            case 3001: // Content unavailable
            case 3002: // Geo-restricted
            case 3003: // Premium required
            case 4002: // Format not supported
                return true;
            default:
                return false;
        }
    }
    
    private String getErrorReason(int errorCode) {
        switch (errorCode) {
            case 3001:
                return "Content is no longer available";
            case 3002:
                return "Content is not available in your region";
            case 3003:
                return "This content requires Spotify Premium";
            case 4002:
                return "This format is not supported on your device";
            default:
                return "Playback failed after multiple attempts";
        }
    }
    
    public void resetTrackCounters(String trackId) {
        retryCountMap.remove(trackId);
        lastRetryTimeMap.remove(trackId);
    }
    
    public void resetAllCounters() {
        retryCountMap.clear();
        lastRetryTimeMap.clear();
    }
}