package my.edu.utar.bananamusic;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;

import com.google.android.material.color.DynamicColors;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.work.Configuration;

import my.edu.utar.bananamusic.utils.FirebaseHelper;
import my.edu.utar.bananamusic.utils.RecyclerViewSafety;
import my.edu.utar.bananamusic.ml.MoodClassifier;
import my.edu.utar.bananamusic.workers.RecommendationRefreshWorker;
import android.os.Bundle;
import android.app.Activity;

public class BananaMusicApplication extends Application implements Configuration.Provider {
    private static final String TAG = "BananaMusicApp";
    public static final boolean DEBUG = true;
    private static BananaMusicApplication instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "Application starting up");
        
        // Set up global error handling
        setupGlobalErrorHandler();
        
        // Improve performance with hardware acceleration
        initPerformanceOptimizations();
        
        if (DEBUG) {
            setupStrictMode();
        }
        
        // Initialize Firebase with the appropriate configuration
        FirebaseHelper.initializeFirebase(this, DEBUG);
        
        // Add Google Play Services check
        checkGooglePlayServices();
        
        // Initialize MoodClassifier to preload ML model
        initializeMoodClassifier();
        
        // Register a global activity lifecycle callback to apply RecyclerView fixes
        registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (activity instanceof FragmentActivity) {
                    FragmentActivity fragmentActivity = (FragmentActivity) activity;
                    fragmentActivity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                        new FragmentManager.FragmentLifecycleCallbacks() {
                            @Override
                            public void onFragmentViewCreated(FragmentManager fm, Fragment fragment, View view, Bundle savedInstanceState) {
                                super.onFragmentViewCreated(fm, fragment, view, savedInstanceState);
                                try {
                                    // Apply RecyclerView safety fixes to all fragments automatically
                                    RecyclerViewSafety.fixAllRecyclerViewsInFragment(fragment);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error fixing RecyclerViews in fragment: " + e);
                                }
                            }
                        }, true);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {}

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });

        // Schedule periodic recommendation refresh
        RecommendationRefreshWorker.schedulePeriodicRefresh(this);
    }
    
    /**
     * Set up global error handling for uncaught exceptions
     */
    private void setupGlobalErrorHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception", throwable);
            
            // Check for specific types of errors
            if (throwable instanceof IllegalStateException) {
                if (throwable.getMessage() != null && 
                    (throwable.getMessage().contains("fragment") || 
                     throwable.getMessage().contains("Fragment"))) {
                    // Fragment transaction issues
                    logSpecificError("Fragment Transaction Error", throwable);
                }
            } else if (throwable instanceof IndexOutOfBoundsException) {
                // Likely RecyclerView related
                logSpecificError("RecyclerView Error", throwable);
            } else if (throwable instanceof NullPointerException) {
                // Common in UI operations
                logSpecificError("Null Reference Error", throwable);
            }
            
            // Show error to user if app is in foreground
            showErrorToUser(throwable);
            
            // Allow the default handler to continue processing the exception
            if (Thread.getDefaultUncaughtExceptionHandler() != null) {
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, throwable);
            }
        });
    }
    
    /**
     * Log specific error details for analytics and debugging
     */
    private void logSpecificError(String errorType, Throwable throwable) {
        Log.e(TAG, errorType + ": " + throwable.getMessage());
        // Here you could also send error reports to your analytics system
        // FirebaseAnalytics.logEvent(...) or similar
    }
    
    /**
     * Show error message to user if appropriate
     */
    private void showErrorToUser(Throwable throwable) {
        // Only show user-friendly messages in production
        if (!DEBUG) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Toast.makeText(
                        getApplicationContext(),
                        "Something went wrong. The app will recover shortly.",
                        Toast.LENGTH_SHORT
                    ).show();
                } catch (Exception e) {
                    // Ignore - we're already handling an exception
                }
            });
        }
    }
    
    /**
     * Initialize performance optimizations
     */
    private void initPerformanceOptimizations() {
        // Disable Material Dynamic Colors for better performance
        // DynamicColors.applyToActivitiesIfAvailable(this);  // Disabled - may cause UI issues
        
        // Use hardware acceleration when possible
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        
        // Check touch slop value - but we can't modify it directly through public API
        try {
            int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
            Log.d(TAG, "Device touch slop value: " + touchSlop);
        } catch (Exception e) {
            Log.e(TAG, "Error checking touch slop", e);
        }
        
        // Disable night mode changes for consistent performance
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
    
    /**
     * Get application instance
     */
    public static BananaMusicApplication getInstance() {
        return instance;
    }
    
    /**
     * Get context from anywhere
     */
    public static Context getAppContext() {
        return instance.getApplicationContext();
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        
        // Clean up Firebase resources
        FirebaseHelper.cleanUp();
        Log.d(TAG, "Application shutting down");
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d(TAG, "Low memory condition detected");
        
        // Release non-critical resources
        System.gc();
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        
        // Free memory based on severity level
        if (level >= TRIM_MEMORY_MODERATE) {
            Log.d(TAG, "Trim memory: Moderate or higher level");
            System.gc();
        }
    }
    
    /**
     * Configure StrictMode to detect UI thread violations and resource leaks
     */
    private void setupStrictMode() {
        // Simplify StrictMode to just log issues rather than penalizing
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .penaltyLog()
                .build());
        
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build());
        
        Log.d(TAG, "StrictMode policies configured with reduced strictness");
    }
    
    /**
     * Check Google Play Services availability in background
     */
    private void checkGooglePlayServices() {
        try {
            com.google.android.gms.common.GoogleApiAvailability apiAvailability = 
                com.google.android.gms.common.GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
            
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.w(TAG, "Google Play Services not available or out of date: " + 
                      apiAvailability.getErrorString(resultCode));
            } else {
                Log.d(TAG, "Google Play Services available and up to date");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking Google Play Services: " + e.getMessage());
        }
    }
    
    /**
     * Initialize MoodClassifier to preload the ML model
     */
    private void initializeMoodClassifier() {
        try {
            // Initialize on a background thread
            new Thread(() -> {
                try {
                    // This will trigger the file copy and model loading
                    MoodClassifier.getInstance(getApplicationContext());
                    Log.d(TAG, "MoodClassifier initialized successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Error initializing MoodClassifier", e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MoodClassifier initialization", e);
        }
    }

    /**
     * Provides WorkManager configuration through Configuration.Provider interface
     */
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build();
    }
}