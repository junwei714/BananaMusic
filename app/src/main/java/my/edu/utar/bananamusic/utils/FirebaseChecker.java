package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Utility class to verify Firebase connectivity
 */
public class FirebaseChecker {
    private static final String TAG = "FirebaseChecker";
    
    /**
     * Checks if Firebase is properly connected by performing a test read
     * @param context The application context
     */
    public static void verifyFirebaseConnection(Context context) {
        try {
            // Make sure Firebase is initialized
            FirebaseApp.getInstance();
            
            // Try to read a test value from the database
            DatabaseReference reference = FirebaseDatabase.getInstance().getReference("connection_test");
            reference.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    Log.d(TAG, "Firebase connection successful!");
                    Toast.makeText(context, "Firebase connected successfully!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Firebase database error: " + databaseError.getMessage());
                    Toast.makeText(context, "Firebase connection error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            
            // Write a timestamp to the test reference
            reference.setValue(System.currentTimeMillis());
            
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization error: " + e.getMessage());
            Toast.makeText(context, "Firebase initialization error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
} 