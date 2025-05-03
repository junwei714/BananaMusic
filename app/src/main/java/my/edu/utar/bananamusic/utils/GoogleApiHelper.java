package my.edu.utar.bananamusic.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import my.edu.utar.bananamusic.R;

/**
 * Helper class to verify Google Play Services availability and handle errors
 */
public class GoogleApiHelper {
    private static final String TAG = "GoogleApiHelper";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static GoogleApiClient mGoogleApiClient;
    
    /**
     * Initialize Google API Client
     * This method must be called in onCreate of your main activity
     * @param activity The activity that will handle Google API callbacks
     * @return The initialized GoogleApiClient
     */
    public static GoogleApiClient initializeGoogleApiClient(Activity activity) {
        if (mGoogleApiClient == null) {
            try {
                // Configure sign-in to request the user's ID, email address, and basic profile
                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .build();
                
                // Build a GoogleApiClient with access to basic profile
                mGoogleApiClient = new GoogleApiClient.Builder(activity)
                        .enableAutoManage((androidx.fragment.app.FragmentActivity) activity, 
                                connectionResult -> Log.e(TAG, "Google API Client connection failed: " + connectionResult.getErrorMessage()))
                        .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                        .build();
                
                // Attempt to connect immediately
                if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
                    mGoogleApiClient.connect();
                }
                
                Log.d(TAG, "Google API Client initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Google API Client: " + e.getMessage());
            }
        }
        return mGoogleApiClient;
    }
    
    /**
     * Check if Google Play Services is available and up to date
     * @param activity The activity to show the dialog if necessary
     * @return true if Google Play Services is available and up to date
     */
    public static boolean checkPlayServices(Activity activity) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(activity);
        
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                try {
                    apiAvailability.getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                            .show();
                } catch (Exception e) {
                    Log.e(TAG, "Error showing Play Services error dialog: " + e.getMessage());
                    Toast.makeText(activity, 
                            "Google Play Services error: " + apiAvailability.getErrorString(resultCode), 
                            Toast.LENGTH_LONG).show();
                }
            } else {
                Log.e(TAG, "This device is not supported by Google Play Services");
                Toast.makeText(activity, 
                        "This device is not supported by Google Play Services", 
                        Toast.LENGTH_LONG).show();
            }
            return false;
        }
        
        // Initialize Google API Client
        initializeGoogleApiClient(activity);
        
        return true;
    }
    
    /**
     * Disconnect Google API client when no longer needed
     * Should be called in onDestroy or onStop of your activity
     */
    public static void disconnectGoogleApiClient() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
            Log.d(TAG, "Google API Client disconnected");
        }
    }
    
    /**
     * Show a dialog with instructions on how to add the SHA-1 fingerprint to Firebase
     * @param context The context to show the dialog
     * @param sha1Fingerprint The SHA-1 fingerprint to display
     */
    public static void showSha1InstructionsDialog(Context context, String sha1Fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Google API Authentication Error");
        builder.setMessage("Your app is experiencing a Google API authentication error. " +
                "This might be because the SHA-1 fingerprint is not registered in Firebase.\n\n" +
                "Your SHA-1 fingerprint is:\n" + sha1Fingerprint + "\n\n" +
                "Instructions:\n" +
                "1. Go to the Firebase Console\n" +
                "2. Select your project\n" +
                "3. Go to Project Settings\n" +
                "4. Add this SHA-1 fingerprint to your app\n" +
                "5. Rebuild and run the app");
                
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        
        builder.setNeutralButton("Copy Fingerprint", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                copyToClipboard(context, sha1Fingerprint);
                Toast.makeText(context, "SHA-1 fingerprint copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.show();
        
        // Also log the instructions for developers
        Log.e(TAG, "Google API Authentication Error - SHA-1 fingerprint missing from Firebase");
        Log.e(TAG, "SHA-1 fingerprint: " + sha1Fingerprint);
    }

    /**
     * Copy text to clipboard.
     *
     * @param context The context
     * @param text The text to copy
     */
    private static void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("SHA-1 Fingerprint", text);
        clipboard.setPrimaryClip(clip);
    }
} 