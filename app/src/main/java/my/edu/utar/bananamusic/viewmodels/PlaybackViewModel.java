package my.edu.utar.bananamusic.viewmodels;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import my.edu.utar.bananamusic.models.Track;

public class PlaybackViewModel extends ViewModel {
    private static final String TAG = "PlaybackViewModel";

    public enum PlaybackState {
        IDLE,
        LOADING,
        BUFFERING,
        PLAYING,
        PAUSED,
        ERROR
    }

    // Use private MutableLiveData and public LiveData to prevent external modification
    private final MutableLiveData<Track> _currentTrack = new MutableLiveData<>();
    private final MutableLiveData<PlaybackState> _playbackState = new MutableLiveData<>(PlaybackState.IDLE);
    private final MutableLiveData<Integer> _playbackProgress = new MutableLiveData<>(0);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> _bufferingProgress = new MutableLiveData<>(0);
    private final MutableLiveData<Long> _duration = new MutableLiveData<>(0L);

    private final Object stateLock = new Object();
    private boolean isStateTransitionInProgress = false;

    // Public LiveData getters
    public LiveData<Track> getCurrentTrack() {
        Log.d(TAG, "Getting current track LiveData observer");
        return _currentTrack;
    }

    public LiveData<PlaybackState> getPlaybackState() {
        Log.d(TAG, "Getting playback state LiveData observer");
        return _playbackState;
    }

    public LiveData<Integer> getPlaybackProgress() {
        return _playbackProgress;
    }

    public LiveData<String> getErrorMessage() {
        return _errorMessage;
    }

    public LiveData<Integer> getBufferingProgress() {
        return _bufferingProgress;
    }

    public LiveData<Long> getDuration() {
        return _duration;
    }

    // State update methods
    public void startNewTrack(Track track) {
        synchronized (stateLock) {
            try {
                Log.d(TAG, "Starting new track: " + (track != null ? track.getTitle() : "null"));
                isStateTransitionInProgress = true;
                _currentTrack.setValue(track);
                _playbackState.setValue(PlaybackState.LOADING);
                _playbackProgress.setValue(0);
                _bufferingProgress.setValue(0);
                _errorMessage.setValue(null);
            } catch (Exception e) {
                Log.e(TAG, "Error during startNewTrack", e);
                setError("Failed to start track: " + e.getMessage());
            } finally {
                isStateTransitionInProgress = false;
            }
        }
    }

    public void updatePlaybackState(PlaybackState state) {
        synchronized (stateLock) {
            try {
                if (isStateTransitionInProgress) {
                    Log.d(TAG, "Skipping state update due to ongoing transition");
                    return;
                }
                Log.d(TAG, "Updating playback state to: " + state);
                _playbackState.setValue(state);
            } catch (Exception e) {
                Log.e(TAG, "Error during updatePlaybackState", e);
            }
        }
    }

    public void updateProgress(int progress) {
        synchronized (stateLock) {
            try {
                if (!isStateTransitionInProgress) {
                    _playbackProgress.setValue(progress);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during updateProgress", e);
            }
        }
    }

    public void updateBufferingProgress(int progress) {
        synchronized (stateLock) {
            try {
                if (!isStateTransitionInProgress) {
                    _bufferingProgress.setValue(progress);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during updateBufferingProgress", e);
            }
        }
    }

    public void setError(String message) {
        synchronized (stateLock) {
            try {
                Log.e(TAG, "Setting error: " + message);
                isStateTransitionInProgress = true;
                _errorMessage.setValue(message);
                _playbackState.setValue(PlaybackState.ERROR);
            } catch (Exception e) {
                Log.e(TAG, "Error during setError", e);
            } finally {
                isStateTransitionInProgress = false;
            }
        }
    }

    public void setDuration(long durationMs) {
        _duration.setValue(durationMs);
    }

    // Convenience methods
    public void play() {
        synchronized (stateLock) {
            Log.d(TAG, "Setting state to PLAYING");
            if (_currentTrack.getValue() != null && _playbackState.getValue() != PlaybackState.ERROR) {
                _playbackState.setValue(PlaybackState.PLAYING);
            }
        }
    }

    public void pause() {
        synchronized (stateLock) {
            Log.d(TAG, "Setting state to PAUSED");
            if (_playbackState.getValue() == PlaybackState.PLAYING) {
                _playbackState.setValue(PlaybackState.PAUSED);
            }
        }
    }

    public void startBuffering() {
        Log.d(TAG, "Setting state to BUFFERING");
        _playbackState.setValue(PlaybackState.BUFFERING);
    }

    // Get current values synchronously
    public Track getCurrentTrackValue() {
        Track track = _currentTrack.getValue();
        Log.d(TAG, "Getting current track value: " + (track != null ? track.getTitle() : "null"));
        return track;
    }

    public PlaybackState getCurrentState() {
        PlaybackState state = _playbackState.getValue() != null ? _playbackState.getValue() : PlaybackState.IDLE;
        Log.d(TAG, "Getting current state: " + state);
        return state;
    }

    public boolean isPlaying() {
        return getCurrentState() == PlaybackState.PLAYING;
    }

    public boolean isBuffering() {
        return getCurrentState() == PlaybackState.BUFFERING;
    }

    public int getCurrentProgress() {
        Integer progress = _playbackProgress.getValue();
        return progress != null ? progress : 0;
    }

    public long getCurrentDuration() {
        Long value = _duration.getValue();
        return value != null ? value : 0L;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "ViewModel cleared");
    }
} 