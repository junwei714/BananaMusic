package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback;

/**
 * Extension class for PlaylistManager that adds the missing method
 */
public class PlaylistManagerExt {
    private static final String TAG = "PlaylistManagerExt";
    
    /**
     * Toggle collecting a playlist for the current user
     * 
     * @param context Application context
     * @param playlistId ID of the playlist to toggle
     * @param collect Whether to collect or uncollect the playlist
     * @param callback Callback for the result
     */
    public static void toggleCollectPlaylist(Context context, String playlistId, boolean collect, my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback callback) {
        PlaylistManager playlistManager = PlaylistManager.getInstance(context);
        
        // Create an adapter from utils.callbacks.PlaylistCallback to the PlaylistManager.PlaylistCallback
        PlaylistManager.PlaylistCallback adapter = new PlaylistManager.PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                callback.onSuccess(playlist);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
        
        // Now call with the adapter
        if (collect) {
            // Get the playlist and add it to library
            try {
                playlistManager.getPlaylist(playlistId, adapter);
            } catch (Exception e) {
                Log.e(TAG, "Error in getPlaylistById: " + e.getMessage());
                callback.onError("Error getting playlist: " + e.getMessage());
            }
        } else {
            // Remove from library
            playlistManager.removeFromLibrary(playlistId, adapter);
        }
    }
} 