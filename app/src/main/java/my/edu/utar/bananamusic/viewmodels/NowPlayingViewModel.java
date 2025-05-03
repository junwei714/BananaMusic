package my.edu.utar.bananamusic.viewmodels;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;

/**
 * ViewModel for managing Now Playing state with LiveData
 * This provides reactive UI updates when media state changes
 */
public class NowPlayingViewModel extends AndroidViewModel implements AudioPlayerHelper.OnPlaybackChangedListener {
    private static final String TAG = "NowPlayingViewModel";
    
    private final AudioPlayerHelper audioPlayerHelper;
    
    // LiveData objects for UI to observe
    private final MutableLiveData<Track> currentTrack = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isBuffering = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> currentPosition = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> duration = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> bufferingPercent = new MutableLiveData<>(0);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    
    public NowPlayingViewModel(@NonNull Application application) {
        super(application);
        
        // Initialize audio player helper
        audioPlayerHelper = AudioPlayerHelper.getInstance(application);
        
        // Register as listener for playback changes
        audioPlayerHelper.setOnPlaybackChangedListener(this);
        
        // Initialize with current state
        initializeState();
    }
    
    private void initializeState() {
        // Get current track state from audio player
        Track track = audioPlayerHelper.getCurrentTrack();
        if (track != null) {
            currentTrack.setValue(track);
            isPlaying.setValue(audioPlayerHelper.isPlaying());
            currentPosition.setValue(audioPlayerHelper.getCurrentPosition());
            duration.setValue(audioPlayerHelper.getDuration());
        }
    }
    
    // Public LiveData getters - UI can observe these
    public LiveData<Track> getCurrentTrack() {
        return currentTrack;
    }
    
    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }
    
    public LiveData<Boolean> getIsBuffering() {
        return isBuffering;
    }
    
    public LiveData<Integer> getCurrentPosition() {
        return currentPosition;
    }
    
    public LiveData<Integer> getDuration() {
        return duration;
    }
    
    public LiveData<Integer> getBufferingPercent() {
        return bufferingPercent;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    // UI control methods
    public void playPause() {
        if (isPlaying.getValue() != null && isPlaying.getValue()) {
            audioPlayerHelper.pausePlayback();
        } else {
            audioPlayerHelper.resumePlayback();
        }
    }
    
    public void skipNext() {
        audioPlayerHelper.skipToNext();
    }
    
    public void skipPrevious() {
        audioPlayerHelper.skipToPrevious();
    }
    
    public void seekTo(int position) {
        audioPlayerHelper.seekTo(position);
    }
    
    public void toggleShuffle() {
        audioPlayerHelper.toggleShuffle();
    }
    
    public void toggleRepeat() {
        audioPlayerHelper.toggleRepeat();
    }
    
    public boolean isShuffleEnabled() {
        return audioPlayerHelper.isShuffleEnabled();
    }
    
    public int getRepeatMode() {
        return audioPlayerHelper.getRepeatMode();
    }
    
    // Method to clear error message
    public void clearErrorMessage() {
        errorMessage.setValue("");
    }
    
    // AudioPlayerHelper.OnPlaybackChangedListener implementation
    @Override
    public void onTrackPlay(Track track) {
        currentTrack.postValue(track);
        isPlaying.postValue(true);
        isBuffering.postValue(false);
        duration.postValue(audioPlayerHelper.getDuration());
        Log.d(TAG, "Track play: " + (track != null ? track.getTitle() : "null"));
    }
    
    @Override
    public void onTrackPause() {
        isPlaying.postValue(false);
        Log.d(TAG, "Track paused");
    }
    
    @Override
    public void onTrackStopped() {
        isPlaying.postValue(false);
        Log.d(TAG, "Track stopped");
    }
    
    @Override
    public void onTrackChanged(Track track) {
        if (track != null) {
            currentTrack.postValue(track);
            duration.postValue(audioPlayerHelper.getDuration());
            Log.d(TAG, "Track changed: " + track.getTitle());
        }
    }
    
    @Override
    public void onPlaybackError(String message) {
        errorMessage.postValue(message);
        isPlaying.postValue(false);
        isBuffering.postValue(false);
        Log.e(TAG, "Playback error: " + message);
    }
    
    @Override
    public void onBufferingStart() {
        isBuffering.postValue(true);
    }
    
    @Override
    public void onBufferingEnd() {
        isBuffering.postValue(false);
        isLoading.postValue(false);
    }
    
    @Override
    public void onTrackComplete() {
        isPlaying.postValue(false);
        currentPosition.postValue(0);
    }
    
    @Override
    public void onLoadingStateChanged(boolean loading) {
        isLoading.postValue(loading);
    }
    
    @Override
    public void onBufferingUpdate(int percent) {
        bufferingPercent.postValue(percent);
    }
    
    @Override
    public void onPlaybackStateChanged(boolean playing) {
        isPlaying.postValue(playing);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        // Unregister as listener when ViewModel is cleared to prevent memory leaks
        audioPlayerHelper.setOnPlaybackChangedListener(null);
    }
} 