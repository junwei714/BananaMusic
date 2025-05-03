package my.edu.utar.bananamusic.utils;

/**
 * Configuration class for Firebase-related settings
 */
public class FirebaseConfig {
    // Firebase project ID from google-services.json
    public static final String PROJECT_ID = "bananamusic-f2944";
    
    // Links to Firebase console
    public static final String FIREBASE_CONSOLE_URL = "https://console.firebase.google.com/project/" + PROJECT_ID;
    public static final String FIRESTORE_CONSOLE_URL = FIREBASE_CONSOLE_URL + "/firestore/data";
    
    // Firestore collections
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_PLAYLISTS = "playlists";
    public static final String COLLECTION_TRACKS = "tracks";
} 