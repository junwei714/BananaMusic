package my.edu.utar.bananamusic.repository;

import my.edu.utar.bananamusic.models.Playlist;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class PlaylistRepository {
    private static final String PLAYLISTS_COLLECTION = "playlists";
    private final FirebaseFirestore db;

    public PlaylistRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public Task<Void> createPlaylist(Playlist playlist) {
        return db.collection(PLAYLISTS_COLLECTION)
                .document(playlist.getPlaylistId())
                .set(playlist);
    }

    public Task<Void> updatePlaylist(Playlist playlist) {
        return db.collection(PLAYLISTS_COLLECTION)
                .document(playlist.getPlaylistId())
                .set(playlist);
    }

    public Task<Void> deletePlaylist(String playlistId) {
        return db.collection(PLAYLISTS_COLLECTION)
                .document(playlistId)
                .delete();
    }

    public Task<Void> addCollaborator(String playlistId, String userId) {
        return db.collection(PLAYLISTS_COLLECTION)
                .document(playlistId)
                .collection("collaborators")
                .document(userId)
                .set(new HashMap<>());
    }

    public Task<Void> removeCollaborator(String playlistId, String userId) {
        return db.collection(PLAYLISTS_COLLECTION)
                .document(playlistId)
                .collection("collaborators")
                .document(userId)
                .delete();
    }

    public Task<Void> voteForTrack(String playlistId, String trackId, String userId, int vote) {
        Map<String, Object> voteData = new HashMap<>();
        voteData.put(userId, vote);

        return db.collection(PLAYLISTS_COLLECTION)
                .document(playlistId)
                .collection("trackVotes")
                .document(trackId)
                .set(voteData);
    }

    public Query getCollaborativePlaylists() {
        return db.collection(PLAYLISTS_COLLECTION)
                .whereEqualTo("isCollaborative", true);
    }

    public Query getUserPlaylists(String userId) {
        return db.collection(PLAYLISTS_COLLECTION)
                .whereArrayContains("collaborators", userId);
    }

    public CollectionReference getPlaylistsCollection() {
        return db.collection(PLAYLISTS_COLLECTION);
    }

    public Task<QuerySnapshot> searchPlaylists(String query) {
        return db.collection(PLAYLISTS_COLLECTION)
                .whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + "\uf8ff")
                .get();
    }
} 