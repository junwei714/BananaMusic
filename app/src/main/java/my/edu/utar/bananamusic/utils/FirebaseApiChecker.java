package my.edu.utar.bananamusic.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Utility class to check Firebase API status and provide guidance
 */
public class FirebaseApiChecker {
    private static final String TAG = "FirebaseApiChecker";
    private static final String FIRESTORE_API_URL = "https://console.firebase.google.com/project/%s/firestore";
    
    /**
     * Tests Firestore connection and shows a dialog with guidance if needed
     * @param context The current context
     * @param projectId The Firebase project ID
     */
    public static void checkFirestoreAvailability(Context context, String projectId) {
        try {
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            firestore.collection("api_test").document("test")
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        Log.d(TAG, "Firestore connection successful");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Firestore API error: " + e.getMessage());
                        
                        if (e.getMessage() != null && 
                            (e.getMessage().contains("PERMISSION_DENIED") || 
                             e.getMessage().contains("API has not been used"))) {
                                 
                            showFirestoreEnableDialog(context, projectId);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error testing Firestore: " + e.getMessage());
        }
    }
    
    /**
     * Shows a dialog with instructions on how to enable Firestore
     * @param context The current context
     * @param projectId The Firebase project ID
     */
    private static void showFirestoreEnableDialog(Context context, String projectId) {
        try {
            new AlertDialog.Builder(context)
                    .setTitle("Firestore API Not Enabled")
                    .setMessage("The Firestore API is not enabled for this project. " +
                            "You need to enable it in the Firebase Console to use this app.")
                    .setPositiveButton("Enable Now", (dialog, which) -> {
                        // Open Firebase console in browser
                        String firestoreUrl = String.format(FIRESTORE_API_URL, projectId);
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(firestoreUrl));
                        context.startActivity(browserIntent);
                    })
                    .setNegativeButton("Later", null)
                    .setCancelable(true)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing Firestore dialog: " + e.getMessage());
        }
    }
} 