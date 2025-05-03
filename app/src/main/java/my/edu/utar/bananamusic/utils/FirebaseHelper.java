package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class for Firebase initialization and common operations.
 */
public class FirebaseHelper {
    private static final String TAG = "FirebaseHelper";
    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Handler mainHandler = new Handler(Looper.getMainLooper());
    private static boolean isInitialized = false;
    
    /**
     * Initialize Firebase with appropriate configuration
     * @param context Application context
     * @param isDebug Whether app is in debug mode
     */
    public static void initializeFirebase(Context context, boolean isDebug) {
        if (isInitialized) {
            Log.d(TAG, "Firebase already initialized, skipping initialization");
            return;
        }
        
        executor.execute(() -> {
            try {
                // Initialize Firebase
                FirebaseApp.initializeApp(context);
                
                // Configure Firebase Database for better offline behavior
                FirebaseDatabase.getInstance().setPersistenceEnabled(true);
                
                // Set up App Check
                FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
                if (isDebug) {
                    // Use debug provider for development
                    firebaseAppCheck.installAppCheckProviderFactory(
                            DebugAppCheckProviderFactory.getInstance());
                    Log.d(TAG, "Firebase initialized with Debug App Check Provider");
                } else {
                    // Use Play Integrity for production
                    firebaseAppCheck.installAppCheckProviderFactory(
                            PlayIntegrityAppCheckProviderFactory.getInstance());
                    Log.d(TAG, "Firebase initialized with Play Integrity App Check Provider");
                }
                
                // Set database connection timeout
                DatabaseReference.goOnline();
                
                isInitialized = true;
                Log.d(TAG, "Firebase successfully initialized");
                
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Firebase: " + e.getMessage(), e);
                
                // Post error to main thread
                final String errorMsg = e.getMessage();
                mainHandler.post(() -> {
                    Log.e(TAG, "Firebase initialization error reported to main thread: " + errorMsg);
                });
            }
        });
    }
    
    /**
     * Check if Firebase is properly initialized
     * @return true if Firebase is initialized
     */
    public static boolean isFirebaseInitialized() {
        return isInitialized;
    }
    
    /**
     * Get the current Firebase user
     * @return Current FirebaseUser or null if not signed in
     */
    public static FirebaseUser getCurrentUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }
    
    /**
     * Check if user is signed in
     * @return true if user is signed in
     */
    public static boolean isUserSignedIn() {
        return getCurrentUser() != null;
    }
    
    /**
     * Sign out the current user
     */
    public static void signOut() {
        FirebaseAuth.getInstance().signOut();
    }
    
    /**
     * Clean up Firebase connections when app is closing
     * Should be called in Application.onTerminate() or similar
     */
    public static void cleanUp() {
        try {
            // Take database offline to prevent connection issues
            DatabaseReference.goOffline();
            Log.d(TAG, "Firebase connections cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up Firebase: " + e.getMessage());
        }
    }
} 