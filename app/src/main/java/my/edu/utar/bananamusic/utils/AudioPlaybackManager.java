package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class AudioPlaybackManager implements MediaPlayer.OnCompletionListener,
                                          MediaPlayer.OnPreparedListener,
                                          MediaPlayer.OnErrorListener,
                                          AudioManager.OnAudioFocusChangeListener {
                                              
    private static final String TAG = "AudioPlaybackManager";
    private static AudioPlaybackManager instance;
    private final Context context;
    private final AudioManager audioManager;
    private final Handler mainHandler;
    private MediaPlayer mediaPlayer;
    private String currentTrackId;
    private PlaybackState state = PlaybackState.IDLE;
    private final TrackPlaybackCache cache;
    private final SpotifyDeviceManager deviceManager;
    private AudioFocusRequest audioFocusRequest;
    private PlaybackCallback playbackCallback;
    private boolean wasPlayingBeforeFocusLoss = false;

    public enum PlaybackState {
        IDLE, PREPARING, PLAYING, PAUSED, ERROR
    }

    public interface PlaybackCallback {
        void onPlaybackStarted(String trackId);
        void onPlaybackPaused();
        void onPlaybackStopped();
        void onPlaybackError(String message);
        void onPlaybackProgress(int progress, int duration);
    }

    private AudioPlaybackManager(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.cache = TrackPlaybackCache.getInstance(context);
        this.deviceManager = new SpotifyDeviceManager();
        setupAudioFocusRequest();
    }

    public static synchronized AudioPlaybackManager getInstance(Context context) {
        if (instance == null) {
            instance = new AudioPlaybackManager(context);
        }
        return instance;
    }

    private void setupAudioFocusRequest() {
        AudioAttributes playbackAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(this)
            .build();
    }

    public void setPlaybackCallback(PlaybackCallback callback) {
        this.playbackCallback = callback;
    }

    public void playTrack(String trackId, String previewUrl) {
        if (trackId == null || previewUrl == null) {
            notifyError("Invalid track or URL");
            return;
        }

        // Check if we're already playing this track
        if (trackId.equals(currentTrackId) && state == PlaybackState.PLAYING) {
            return;
        }

        // Request audio focus
        int result = audioManager.requestAudioFocus(audioFocusRequest);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            notifyError("Could not gain audio focus");
            return;
        }

        stopPlayback();
        currentTrackId = trackId;

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
            
            mediaPlayer.setDataSource(previewUrl);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener(this);
            
            state = PlaybackState.PREPARING;
            mediaPlayer.prepareAsync();
            
            // Cache the URL for future use
            cache.cacheTrackUrls(trackId, previewUrl, null);
            
            startProgressUpdates();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up MediaPlayer", e);
            notifyError("Error preparing playback: " + e.getMessage());
            stopPlayback();
        }
    }

    public void pausePlayback() {
        if (mediaPlayer != null && state == PlaybackState.PLAYING) {
            mediaPlayer.pause();
            state = PlaybackState.PAUSED;
            if (playbackCallback != null) {
                playbackCallback.onPlaybackPaused();
            }
        }
    }

    public void resumePlayback() {
        if (mediaPlayer != null && state == PlaybackState.PAUSED) {
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer.start();
                state = PlaybackState.PLAYING;
                if (playbackCallback != null) {
                    playbackCallback.onPlaybackStarted(currentTrackId);
                }
            }
        }
    }

    public void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                if (state == PlaybackState.PLAYING || state == PlaybackState.PAUSED) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaPlayer", e);
            }
            mediaPlayer = null;
        }
        
        audioManager.abandonAudioFocusRequest(audioFocusRequest);
        state = PlaybackState.IDLE;
        currentTrackId = null;
        
        if (playbackCallback != null) {
            playbackCallback.onPlaybackStopped();
        }
    }

    private void startProgressUpdates() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && state == PlaybackState.PLAYING && playbackCallback != null) {
                    int progress = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();
                    playbackCallback.onPlaybackProgress(progress, duration);
                    mainHandler.postDelayed(this, 1000);
                }
            }
        });
    }

    private void notifyError(String message) {
        state = PlaybackState.ERROR;
        if (playbackCallback != null) {
            playbackCallback.onPlaybackError(message);
        }
    }

    // MediaPlayer.OnPreparedListener
    @Override
    public void onPrepared(MediaPlayer mp) {
        state = PlaybackState.PLAYING;
        mp.start();
        if (playbackCallback != null) {
            playbackCallback.onPlaybackStarted(currentTrackId);
        }
    }

    // MediaPlayer.OnCompletionListener
    @Override
    public void onCompletion(MediaPlayer mp) {
        stopPlayback();
    }

    // MediaPlayer.OnErrorListener
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
        notifyError("Playback error: " + what);
        stopPlayback();
        return true;
    }

    // AudioManager.OnAudioFocusChangeListener
    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                wasPlayingBeforeFocusLoss = state == PlaybackState.PLAYING;
                stopPlayback();
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                wasPlayingBeforeFocusLoss = state == PlaybackState.PLAYING;
                pausePlayback();
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(0.3f, 0.3f);
                }
                break;
                
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(1.0f, 1.0f);
                    if (wasPlayingBeforeFocusLoss) {
                        resumePlayback();
                    }
                }
                wasPlayingBeforeFocusLoss = false;
                break;
        }
    }

    public PlaybackState getState() {
        return state;
    }

    public String getCurrentTrackId() {
        return currentTrackId;
    }
}