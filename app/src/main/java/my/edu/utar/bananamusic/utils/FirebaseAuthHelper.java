package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import my.edu.utar.bananamusic.models.User;
import my.edu.utar.bananamusic.utils.FirebaseConfig;

public class FirebaseAuthHelper {
    private static FirebaseAuthHelper instance;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private GoogleSignInClient googleSignInClient;
    
    private FirebaseAuthHelper() {
        firebaseAuth = FirebaseAuth.getInstance();
        
        // Make Firestore initialization optional to prevent errors when API is not enabled
        try {
            firestore = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            // Firestore is not available, but we'll continue without it
            firestore = null;
        }
    }
    
    public static synchronized FirebaseAuthHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseAuthHelper();
        }
        return instance;
    }
    
    // Initialize Google Sign-In
    public GoogleSignInClient initGoogleSignIn(Context context, String webClientId) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        
        googleSignInClient = GoogleSignIn.getClient(context, gso);
        return googleSignInClient;
    }
    
    // Get Google Sign-In Intent
    public Intent getGoogleSignInIntent() {
        if (googleSignInClient == null) {
            throw new IllegalStateException("Google Sign-In not initialized. Call initGoogleSignIn first.");
        }
        return googleSignInClient.getSignInIntent();
    }
    
    // Handle Google Sign-In Result
    public void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask, final AuthCallback callback) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            // Google Sign In was successful, authenticate with Firebase
            firebaseAuthWithGoogle(account.getIdToken(), callback);
        } catch (ApiException e) {
            // Google Sign In failed
            callback.onError("Google sign in failed: " + e.getStatusCode());
        }
    }
    
    // Authenticate with Firebase using Google
    private void firebaseAuthWithGoogle(String idToken, final AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Sign in success
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) {
                            // Create or update user in Firestore
                            String displayName = user.getDisplayName() != null ? user.getDisplayName() : "";
                            String email = user.getEmail() != null ? user.getEmail() : "";
                            createUserInFirestore(user.getUid(), email, displayName, callback);
                        } else {
                            callback.onSuccess();
                        }
                    } else {
                        // If sign in fails, display a message to the user
                        String errorMsg = task.getException() != null ? 
                                task.getException().getMessage() : 
                                "Unknown error during Google sign in";
                        callback.onError("Google authentication failed: " + errorMsg);
                    }
                });
    }
    
    // Sign out from Google as well
    public void googleSignOut() {
        if (googleSignInClient != null) {
            googleSignInClient.signOut();
        }
        firebaseAuth.signOut();
    }
    
    public boolean isUserLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }
    
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }
    
    public String getCurrentUserId() {
        FirebaseUser user = getCurrentUser();
        return user != null ? user.getUid() : null;
    }
    
    public interface AuthCallback {
        void onSuccess();
        void onError(String errorMessage);
    }
    
    public void signUp(String email, String password, String displayName, final AuthCallback callback) {
        // First try to verify that authentication can proceed
        try {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                            
                            // Update display name
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(displayName)
                                    .build();
                            
                            firebaseUser.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        if (profileTask.isSuccessful()) {
                                            // Create user in Firestore
                                            createUserInFirestore(firebaseUser.getUid(), email, displayName, callback);
                                        } else {
                                            String errorMsg = profileTask.getException() != null ? 
                                                    profileTask.getException().getMessage() : 
                                                    "Unknown error updating profile";
                                            callback.onError("Failed to update profile: " + errorMsg);
                                        }
                                    });
                        } else {
                            String errorMsg = task.getException() != null ? 
                                    task.getException().getMessage() : 
                                    "Unknown error during sign up";
                                    
                            // Check for recaptcha errors
                            if (errorMsg != null && errorMsg.contains("RECAPTCHA")) {
                                errorMsg = "Error with security verification. Please try again later.";
                            }
                            
                            callback.onError("Sign up failed: " + errorMsg);
                        }
                    });
        } catch (Exception e) {
            // Handle any exceptions that might occur during the authentication process
            callback.onError("Authentication error: " + e.getMessage());
        }
    }
    
    private void createUserInFirestore(String userId, String email, String displayName, final AuthCallback callback) {
        // Skip Firestore operations if not available and report success anyway
        if (firestore == null) {
            callback.onSuccess();
            return;
        }
        
        User user = new User(userId, email, displayName);
        
        try {
            firestore.collection(FirebaseConfig.COLLECTION_USERS).document(userId)
                    .set(user)
                    .addOnSuccessListener(aVoid -> {
                        // Successfully created user document
                        callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        String errorMessage = e.getMessage();
                        // Check if Firestore API is disabled
                        if (errorMessage != null && errorMessage.contains("PERMISSION_DENIED") && 
                                errorMessage.contains("Firestore API has not been used")) {
                            // Return success anyway to prevent the app from blocking
                            callback.onSuccess();
                        } else {
                            callback.onError("Failed to create user profile: " + errorMessage);
                        }
                    });
        } catch (Exception e) {
            // Continue with authentication even if Firestore fails
            callback.onSuccess();
        }
    }
    
    public void signIn(String email, String password, final AuthCallback callback) {
        try {
            firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            callback.onSuccess();
                        } else {
                            String errorMsg = task.getException() != null ? 
                                    task.getException().getMessage() : 
                                    "Unknown error during sign in";
                                    
                            // Check for recaptcha errors
                            if (errorMsg != null && errorMsg.contains("RECAPTCHA")) {
                                errorMsg = "Error with security verification. Please try again later.";
                            }
                            
                            callback.onError("Sign in failed: " + errorMsg);
                        }
                    });
        } catch (Exception e) {
            callback.onError("Authentication error: " + e.getMessage());
        }
    }
    
    public void signOut() {
        // Sign out from Google as well if GoogleSignInClient is initialized
        if (googleSignInClient != null) {
            googleSignInClient.signOut();
        }
        firebaseAuth.signOut();
    }
    
    public void resetPassword(String email, final AuthCallback callback) {
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            callback.onSuccess();
                        } else {
                            callback.onError("Password reset failed: " + task.getException().getMessage());
                        }
                    }
                });
    }
    
    public void deleteAccount(final AuthCallback callback) {
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            // First delete Firestore data if available
            if (firestore == null) {
                // Skip Firestore operations and go directly to account deletion
                deleteAuthAccount(user, callback);
                return;
            }
            
            DocumentReference userRef = firestore.collection(FirebaseConfig.COLLECTION_USERS).document(user.getUid());
            userRef.delete().addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    // Continue with auth account deletion whether Firestore succeeded or not
                    deleteAuthAccount(user, callback);
                }
            });
        } else {
            callback.onError("No user is signed in");
        }
    }
    
    private void deleteAuthAccount(FirebaseUser user, AuthCallback callback) {
        user.delete().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to delete account: " + task.getException().getMessage());
                }
            }
        });
    }
    
    // Add a method to check if Firestore is available
    public boolean isFirestoreAvailable() {
        return firestore != null;
    }
} 