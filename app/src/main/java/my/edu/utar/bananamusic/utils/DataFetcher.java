package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class for fetching data from APIs or local storage
 * Handles threading and provides callback mechanisms
 */
public class DataFetcher {
    private static final String TAG = "DataFetcher";
    
    // Executor for background operations
    private final ExecutorService executor;
    // Handler for main thread callbacks
    private final Handler mainHandler;
    // Application context
    private final Context context;
    
    /**
     * Callback interface for data fetching operations
     * @param <T> The type of data being fetched
     */
    public interface FetchCallback<T> {
        /**
         * Called when data is successfully fetched
         * @param data The fetched data
         */
        void onSuccess(T data);
        
        /**
         * Called when an error occurs while fetching data
         * @param errorMessage The error message
         */
        void onError(String errorMessage);
    }
    
    /**
     * Constructor
     * @param context Application context
     */
    public DataFetcher(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Execute a task on a background thread
     * @param task The task to execute
     */
    public void executeAsync(Runnable task) {
        executor.execute(task);
    }
    
    /**
     * Execute a data fetching task on a background thread with a callback
     * @param dataTask The task that fetches the data
     * @param callback The callback to handle the result
     * @param <T> The type of data being fetched
     */
    public <T> void fetch(final DataTask<T> dataTask, final FetchCallback<T> callback) {
        if (!isNetworkAvailable() && !dataTask.canRunOffline()) {
            mainHandler.post(() -> callback.onError("No network connection available"));
            return;
        }
        
        executor.execute(() -> {
            try {
                final T result = dataTask.execute();
                
                // Post the result on the main thread
                mainHandler.post(() -> {
                    if (result != null) {
                        callback.onSuccess(result);
                    } else {
                        callback.onError("Failed to fetch data: null result");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error fetching data", e);
                final String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
                
                // Post the error on the main thread
                mainHandler.post(() -> callback.onError("Error: " + errorMsg));
            }
        });
    }
    
    /**
     * Interface for data fetching tasks
     * @param <T> The type of data being fetched
     */
    public interface DataTask<T> {
        /**
         * Execute the data fetching task
         * @return The fetched data
         * @throws Exception If an error occurs
         */
        T execute() throws Exception;
        
        /**
         * Check if the task can run offline
         * @return True if the task can run offline, false otherwise
         */
        default boolean canRunOffline() {
            return false;
        }
    }
    
    /**
     * Check if the device has an active network connection
     * @return True if connected, false otherwise
     */
    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = 
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }
    
    /**
     * Cancel all pending tasks
     */
    public void cancelAll() {
        executor.shutdownNow();
    }
} 