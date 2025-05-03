package my.edu.utar.bananamusic.managers;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import my.edu.utar.bananamusic.models.Track;
import java.util.ArrayList;
import java.util.List;

public class MediaPlayerManager {
    private static final String TAG = "MediaPlayerManager";
    private static MediaPlayerManager instance;
    private MediaPlayer mediaPlayer;
    private Track currentTrack;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                int currentPosition = mediaPlayer.getCurrentPosition();
                int duration = mediaPlayer.getDuration();
                playbackProgressLiveData.setValue(new PlaybackProgress(currentPosition, duration));
                
                // Update PlayerStateManager with progress
                if (duration > 0) {
                    int progressPercent = (int) ((currentPosition * 100L) / duration);
                    PlayerStateManager.getInstance().setProgress(progressPercent);
                }
                
                handler.postDelayed(this, 100); // Update every 100ms
            }
        }
    };

    // LiveData for UI updates
    private final MutableLiveData<Boolean> isPlayingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Track> currentTrackLiveData = new MutableLiveData<>();
    private final MutableLiveData<PlaybackProgress> playbackProgressLiveData = new MutableLiveData<>();

    private List<Track> currentQueue = new ArrayList<>();
    private int currentQueueIndex = -1;
    private PlaybackListener playbackListener;
    private PlayerStateManager playerStateManager;

    public interface PlaybackListener {
        void onTrackChanged(Track track);
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgressChanged(int position);
    }

    private MediaPlayerManager() {
        // Private constructor
        playerStateManager = PlayerStateManager.getInstance();
    }

    public static synchronized MediaPlayerManager getInstance() {
        if (instance == null) {
            instance = new MediaPlayerManager();
        }
        return instance;
    }

    public void setQueue(List<Track> tracks) {
        this.currentQueue = new ArrayList<>(tracks);
        this.currentQueueIndex = -1;
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public Track getCurrentQueueTrack() {
        if (currentQueue != null && currentQueueIndex >= 0 && currentQueueIndex < currentQueue.size()) {
            return currentQueue.get(currentQueueIndex);
        }
        return null;
    }

    public void playTrack(Track track) {
        if (track == null || track.getPreviewUrl() == null) return;

        if (currentTrack != null && currentTrack.equals(track) && mediaPlayer != null) {
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                setPlayingState(true);
                startProgressUpdates();
            }
            return;
        }

        releaseMediaPlayer();
        currentTrack = track;
        currentTrackLiveData.setValue(track);
        playerStateManager.setCurrentTrack(track);

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(track.getPreviewUrl());
            
            // Set buffering state
            playerStateManager.setBuffering(true);
            
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                setPlayingState(true);
                startProgressUpdates();
                playerStateManager.setBuffering(false);
                
                if (playbackListener != null) {
                    playbackListener.onTrackChanged(track);
                    playbackListener.onPlaybackStateChanged(true);
                }
            });
            
            mediaPlayer.setOnCompletionListener(mp -> {
                setPlayingState(false);
                stopProgressUpdates();
                
                if (playbackListener != null) {
                    playbackListener.onPlaybackStateChanged(false);
                }
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                setPlayingState(false);
                stopProgressUpdates();
                playerStateManager.setBuffering(false);
                return false; // Let the OnCompletionListener handle it
            });
            
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error playing track", e);
            releaseMediaPlayer();
            playerStateManager.setBuffering(false);
        }

        if (track != null && track.getPreviewUrl() != null) {
            // Update queue index if track is in queue
            int trackIndex = currentQueue.indexOf(track);
            if (trackIndex != -1) {
                currentQueueIndex = trackIndex;
            }
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            setPlayingState(false);
            stopProgressUpdates();
        }
    }

    public void resume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            setPlayingState(true);
            startProgressUpdates();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            setPlayingState(false);
            stopProgressUpdates();
        }
    }

    public void skipToNext() {
        if (currentQueue != null && currentQueueIndex < currentQueue.size() - 1) {
            currentQueueIndex++;
            playTrack(currentQueue.get(currentQueueIndex));
        }
    }

    public void skipToPrevious() {
        if (currentQueue != null && currentQueueIndex > 0) {
            currentQueueIndex--;
            playTrack(currentQueue.get(currentQueueIndex));
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            stopProgressUpdates();
            mediaPlayer.release();
            mediaPlayer = null;
            setPlayingState(false);
        }
    }

    private void startProgressUpdates() {
        handler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        handler.removeCallbacks(progressRunnable);
    }
    
    private void setPlayingState(boolean isPlaying) {
        isPlayingLiveData.setValue(isPlaying);
        playerStateManager.setPlaying(isPlaying);
        
        Log.d(TAG, "Setting playing state: " + isPlaying);
    }

    // Getters for LiveData
    public LiveData<Boolean> getIsPlayingLiveData() {
        return isPlayingLiveData;
    }

    public LiveData<Track> getCurrentTrackLiveData() {
        return currentTrackLiveData;
    }

    public LiveData<PlaybackProgress> getPlaybackProgressLiveData() {
        return playbackProgressLiveData;
    }

    public void setPlaybackListener(PlaybackListener listener) {
        this.playbackListener = listener;
    }

    public static class PlaybackProgress {
        private final int currentPosition;
        private final int duration;

        public PlaybackProgress(int currentPosition, int duration) {
            this.currentPosition = currentPosition;
            this.duration = duration;
        }

        public int getCurrentPosition() {
            return currentPosition;
        }

        public int getDuration() {
            return duration;
        }
    }
} 