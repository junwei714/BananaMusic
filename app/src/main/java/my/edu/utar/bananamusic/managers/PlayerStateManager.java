package my.edu.utar.bananamusic.managers;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import my.edu.utar.bananamusic.models.Track;

public class PlayerStateManager {
    private static PlayerStateManager instance;
    
    // LiveData for various player states
    private final MutableLiveData<Track> currentTrackLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isBufferingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> progressLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isPlayingLiveData = new MutableLiveData<>(false);

    private PlayerStateManager() {
        // Private constructor for singleton
    }

    public static synchronized PlayerStateManager getInstance() {
        if (instance == null) {
            instance = new PlayerStateManager();
        }
        return instance;
    }

    public void setCurrentTrack(Track track) {
        currentTrackLiveData.setValue(track);
    }

    public void setBuffering(boolean isBuffering) {
        isBufferingLiveData.setValue(isBuffering);
    }

    public void setProgress(int progress) {
        progressLiveData.setValue(progress);
    }

    public void setPlaying(boolean isPlaying) {
        isPlayingLiveData.setValue(isPlaying);
    }

    public LiveData<Track> getCurrentTrackLiveData() {
        return currentTrackLiveData;
    }

    public LiveData<Boolean> getIsBufferingLiveData() {
        return isBufferingLiveData;
    }

    public LiveData<Integer> getProgressLiveData() {
        return progressLiveData;
    }

    public LiveData<Boolean> getIsPlayingLiveData() {
        return isPlayingLiveData;
    }

    public Track getCurrentTrack() {
        return currentTrackLiveData.getValue();
    }

    public boolean isBuffering() {
        Boolean value = isBufferingLiveData.getValue();
        return value != null && value;
    }

    public boolean isPlaying() {
        Boolean value = isPlayingLiveData.getValue();
        return value != null && value;
    }

    public int getProgress() {
        Integer value = progressLiveData.getValue();
        return value != null ? value : 0;
    }
} 