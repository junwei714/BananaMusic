package my.edu.utar.bananamusic.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.repository.PlaylistRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class PlaylistViewModel extends ViewModel {
    private final PlaylistRepository repository;
    private final MutableLiveData<List<Playlist>> collaborativePlaylists = new MutableLiveData<>();
    private final MutableLiveData<List<Playlist>> userPlaylists = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public PlaylistViewModel() {
        repository = new PlaylistRepository();
    }

    public void loadCollaborativePlaylists() {
        isLoading.setValue(true);
        repository.getCollaborativePlaylists()
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Playlist> playlists = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Playlist playlist = document.toObject(Playlist.class);
                        if (playlist != null) {
                            playlists.add(playlist);
                        }
                    }
                    collaborativePlaylists.setValue(playlists);
                    isLoading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    error.setValue(e.getMessage());
                    isLoading.setValue(false);
                });
    }

    public void loadUserPlaylists(String userId) {
        isLoading.setValue(true);
        repository.getUserPlaylists(userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Playlist> playlists = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Playlist playlist = document.toObject(Playlist.class);
                        if (playlist != null) {
                            playlists.add(playlist);
                        }
                    }
                    userPlaylists.setValue(playlists);
                    isLoading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    error.setValue(e.getMessage());
                    isLoading.setValue(false);
                });
    }

    public void createPlaylist(Playlist playlist) {
        isLoading.setValue(true);
        repository.createPlaylist(playlist)
                .addOnSuccessListener(aVoid -> {
                    loadCollaborativePlaylists();
                    isLoading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    error.setValue(e.getMessage());
                    isLoading.setValue(false);
                });
    }

    public void voteForTrack(String playlistId, String trackId, String userId, int vote) {
        isLoading.setValue(true);
        repository.voteForTrack(playlistId, trackId, userId, vote)
                .addOnSuccessListener(aVoid -> isLoading.setValue(false))
                .addOnFailureListener(e -> {
                    error.setValue(e.getMessage());
                    isLoading.setValue(false);
                });
    }

    public void addCollaborator(String playlistId, String userId) {
        isLoading.setValue(true);
        repository.addCollaborator(playlistId, userId)
                .addOnSuccessListener(documentReference -> isLoading.setValue(false))
                .addOnFailureListener(e -> {
                    error.setValue(e.getMessage());
                    isLoading.setValue(false);
                });
    }

    public void removeCollaborator(String playlistId, String userId) {
        isLoading.setValue(true);
        repository.removeCollaborator(playlistId, userId)
                .addOnSuccessListener(aVoid -> isLoading.setValue(false))
                .addOnFailureListener(e -> {
                    error.setValue(e.getMessage());
                    isLoading.setValue(false);
                });
    }

    public LiveData<List<Playlist>> getCollaborativePlaylists() {
        return collaborativePlaylists;
    }

    public LiveData<List<Playlist>> getUserPlaylists() {
        return userPlaylists;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }
} 