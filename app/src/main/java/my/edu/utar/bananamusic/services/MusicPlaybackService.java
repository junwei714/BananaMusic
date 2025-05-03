package my.edu.utar.bananamusic.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.utils.RecentlyPlayedManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MusicPlaybackService extends Service implements AudioPlayerHelper.OnPlaybackChangedListener {

    private static final String TAG = "MusicPlaybackService";
    private static final String CHANNEL_ID = "music_playback_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Action constants for notification controls
    public static final String ACTION_PLAY = "my.edu.utar.bananamusic.ACTION_PLAY";
    public static final String ACTION_PAUSE = "my.edu.utar.bananamusic.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "my.edu.utar.bananamusic.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "my.edu.utar.bananamusic.ACTION_NEXT";
    public static final String ACTION_STOP = "my.edu.utar.bananamusic.ACTION_STOP";
    
    private final IBinder mBinder = new MusicBinder();
    private AudioPlayerHelper audioPlayerHelper;
    private MediaSessionCompat mediaSession;
    private Track currentTrack;
    private Bitmap currentArtwork;
    private boolean isServiceBound = false;
    
    // Track callbacks to activities
    private PlaybackCallback playbackCallback;
    
    // Track list management
    private List<Track> trackList = new ArrayList<>();
    private List<Track> shuffledTrackList = new ArrayList<>();
    private int currentTrackIndex = 0;
    private Random random = new Random();
    
    // Playback state
    private boolean shuffleEnabled = false;
    private int repeatMode = AudioPlayerHelper.REPEAT_OFF;
    
    // Broadcast receiver for notification actions
    private BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_PLAY:
                        if (audioPlayerHelper != null) {
                            audioPlayerHelper.resumePlayback();
                        }
                        break;
                    case ACTION_PAUSE:
                        if (audioPlayerHelper != null) {
                            audioPlayerHelper.pausePlayback();
                        }
                        break;
                    case ACTION_PREVIOUS:
                        if (playbackCallback != null) {
                            playbackCallback.onPreviousClicked();
                        }
                        break;
                    case ACTION_NEXT:
                        if (playbackCallback != null) {
                            playbackCallback.onNextClicked();
                        }
                        break;
                    case ACTION_STOP:
                        stopSelf();
                        break;
                }
            }
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        // Initialize audio player helper
        audioPlayerHelper = AudioPlayerHelper.getInstance(this);
        audioPlayerHelper.setOnPlaybackChangedListener(this);
        
        // Initialize media session
        initializeMediaSession();
        
        // Create notification channel for Android O and above
        createNotificationChannel();
        
        // Register broadcast receiver for notification actions
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_PLAY);
        intentFilter.addAction(ACTION_PAUSE);
        intentFilter.addAction(ACTION_PREVIOUS);
        intentFilter.addAction(ACTION_NEXT);
        intentFilter.addAction(ACTION_STOP);
        registerReceiver(actionReceiver, intentFilter);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        isServiceBound = true;
        Log.d(TAG, "Service bound");
        return mBinder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        isServiceBound = false;
        Log.d(TAG, "Service unbound");
        
        // If we're still playing, continue as a foreground service
        if (audioPlayerHelper != null && audioPlayerHelper.isPlaying()) {
            Log.d(TAG, "Continuing as foreground service");
            return true;
        } else {
            // If not playing, stop the service
            stopSelf();
            return false;
        }
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        
        // Clean up
        if (audioPlayerHelper != null) {
            audioPlayerHelper.releaseMediaPlayer();
            audioPlayerHelper.setOnPlaybackChangedListener(null);
        }
        
        if (mediaSession != null) {
            mediaSession.release();
        }
        
        // Unregister receiver
        try {
            unregisterReceiver(actionReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered", e);
        }
        
        super.onDestroy();
    }
    
    /**
     * Start playback of a track
     * @param track Track to play
     */
    public void playTrack(Track track) {
        if (track == null) return;
        
        this.currentTrack = track;
        
        // Start playback
        audioPlayerHelper.playTrack(track);
        
        // Load artwork for notification
        loadArtwork(track.getAlbumArt());
    }
    
    private void loadArtwork(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            // Use default artwork
            currentArtwork = null;
            updateNotification();
            return;
        }
        
        Glide.with(this)
                .asBitmap()
                .load(imageUrl)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        currentArtwork = resource;
                        updateNotification();
                    }
                    
                    @Override
                    public void onLoadCleared(Drawable placeholder) {
                        currentArtwork = null;
                    }
                    
                    @Override
                    public void onLoadFailed(Drawable errorDrawable) {
                        currentArtwork = null;
                        updateNotification();
                    }
                });
    }
    
    /**
     * Create a notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows the currently playing track");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager notificationManager = 
                getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Update the notification with current track information
     */
    private void updateNotification() {
        if (currentTrack == null) return;
        
        // Create pending intent for opening the app
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE);

        // Create notification actions
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(currentTrack.getTitle())
            .setContentText(currentTrack.getArtist())
            .setSubText(currentTrack.getAlbum())
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_skip_previous, "Previous", createActionIntent(ACTION_PREVIOUS))
            .addAction(audioPlayerHelper.isPlaying() ? 
                R.drawable.ic_pause : R.drawable.ic_play,
                audioPlayerHelper.isPlaying() ? "Pause" : "Play",
                createActionIntent(audioPlayerHelper.isPlaying() ? ACTION_PAUSE : ACTION_PLAY))
            .addAction(R.drawable.ic_skip_next, "Next", createActionIntent(ACTION_NEXT))
            .addAction(R.drawable.ic_close, "Stop", createActionIntent(ACTION_STOP))
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(audioPlayerHelper.isPlaying());

        // Load album art if available
        if (currentTrack.getAlbumArt() != null && !currentTrack.getAlbumArt().isEmpty()) {
            Glide.with(this)
                .asBitmap()
                .load(currentTrack.getAlbumArt())
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap bitmap, Transition<? super Bitmap> transition) {
                        builder.setLargeIcon(bitmap);
                        showNotification(builder.build());
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        showNotification(builder.build());
                    }
                });
        } else {
            showNotification(builder.build());
        }
    }

    private void showNotification(Notification notification) {
        if (audioPlayerHelper.isPlaying()) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            stopForeground(false);
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * Create a PendingIntent for the given action
     */
    private PendingIntent createActionIntent(String action) {
        Intent intent = new Intent(action);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }
    
    /**
     * Set the playback callback
     */
    public void setPlaybackCallback(PlaybackCallback callback) {
        this.playbackCallback = callback;
    }
    
    /**
     * Get the current playing track
     */
    public Track getCurrentTrack() {
        return currentTrack;
    }
    
    /**
     * Check if service is playing
     */
    public boolean isPlaying() {
        return audioPlayerHelper != null && audioPlayerHelper.isPlaying();
    }
    
    /**
     * Resume playback
     */
    public void resumePlayback() {
        if (audioPlayerHelper != null) {
            audioPlayerHelper.resumePlayback();
        }
    }
    
    /**
     * Pause playback
     */
    public void pausePlayback() {
        if (audioPlayerHelper != null) {
            audioPlayerHelper.pausePlayback();
        }
    }
    
    /**
     * Toggle shuffle mode
     */
    public boolean toggleShuffle() {
        if (audioPlayerHelper != null) {
            boolean enabled = audioPlayerHelper.toggleShuffle();
            
            // Update shuffled list when shuffle is enabled
            if (enabled) {
                updateShuffledList();
            }
            
            return enabled;
        }
        return false;
    }
    
    /**
     * Set shuffle mode
     */
    public void setShuffleMode(boolean enabled) {
        if (audioPlayerHelper != null) {
            audioPlayerHelper.setShuffleEnabled(enabled);
            
            // Update shuffled list when shuffle is enabled
            if (enabled) {
                updateShuffledList();
            }
        }
    }
    
    /**
     * Get current shuffle mode
     */
    public boolean isShuffleEnabled() {
        return audioPlayerHelper != null && audioPlayerHelper.isShuffleEnabled();
    }
    
    /**
     * Cycle through repeat modes
     */
    public int cycleRepeatMode() {
        if (audioPlayerHelper != null) {
            return audioPlayerHelper.cycleRepeatMode();
        }
        return AudioPlayerHelper.REPEAT_OFF;
    }
    
    /**
     * Set repeat mode
     */
    public void setRepeatMode(int mode) {
        if (audioPlayerHelper != null) {
            audioPlayerHelper.setRepeatMode(mode);
        }
    }
    
    /**
     * Get current repeat mode
     */
    public int getRepeatMode() {
        if (audioPlayerHelper != null) {
            return audioPlayerHelper.getRepeatMode();
        }
        return AudioPlayerHelper.REPEAT_OFF;
    }
    
    /**
     * Set the track list for playback
     * @param tracks List of tracks
     * @param initialTrackIndex Index of the track to start playing
     */
    public void setTrackList(List<Track> tracks, int initialTrackIndex) {
        if (tracks == null || tracks.isEmpty()) {
            Log.e(TAG, "Cannot set empty track list");
            return;
        }
        
        this.trackList = new ArrayList<>(tracks);
        this.currentTrackIndex = Math.min(initialTrackIndex, trackList.size() - 1);
        
        // Also update shuffled list
        updateShuffledList();
        
        // Start playing the initial track
        playCurrentTrack();
    }
    
    /**
     * Play the current track
     */
    private void playCurrentTrack() {
        Track track = getCurrentTrackFromList();
        if (track != null) {
            playTrack(track);
        }
    }
    
    /**
     * Get the current track from either the normal or shuffled list
     */
    private Track getCurrentTrackFromList() {
        if (trackList.isEmpty()) {
            return null;
        }
        
        if (shuffleEnabled && !shuffledTrackList.isEmpty()) {
            return shuffledTrackList.get(currentTrackIndex);
        } else {
            return trackList.get(currentTrackIndex);
        }
    }
    
    /**
     * Update the shuffled track list
     */
    private void updateShuffledList() {
        shuffledTrackList = new ArrayList<>(trackList);
        Collections.shuffle(shuffledTrackList, random);
    }
    
    /**
     * Play the next track
     */
    public void playNextTrack() {
        if (trackList.isEmpty()) {
            return;
        }
        
        // For repeat all mode, wrap around
        if (repeatMode == AudioPlayerHelper.REPEAT_ALL) {
            currentTrackIndex = (currentTrackIndex + 1) % trackList.size();
        } 
        // For no repeat, stop at the end
        else if (repeatMode == AudioPlayerHelper.REPEAT_OFF) {
            if (currentTrackIndex < trackList.size() - 1) {
                currentTrackIndex++;
            } else {
                // At the end of the list, don't advance
                return;
            }
        }
        // For repeat one, we don't change index but this method might be called
        // explicitly by user, so move to next track anyway
        else {
            currentTrackIndex = (currentTrackIndex + 1) % trackList.size();
        }
        
        playCurrentTrack();
    }
    
    /**
     * Play the previous track
     */
    public void playPreviousTrack() {
        if (trackList.isEmpty()) {
            return;
        }
        
        // For repeat all mode, wrap around
        if (repeatMode == AudioPlayerHelper.REPEAT_ALL) {
            currentTrackIndex = (currentTrackIndex > 0) ? 
                    currentTrackIndex - 1 : trackList.size() - 1;
        } 
        // For no repeat, stop at the beginning
        else if (repeatMode == AudioPlayerHelper.REPEAT_OFF) {
            if (currentTrackIndex > 0) {
                currentTrackIndex--;
            } else {
                // At beginning of list, don't go back
                return;
            }
        }
        // For repeat one, we don't change index but this method might be called
        // explicitly by user, so move to previous track anyway
        else {
            currentTrackIndex = (currentTrackIndex > 0) ? 
                    currentTrackIndex - 1 : trackList.size() - 1;
        }
        
        playCurrentTrack();
    }
    
    @Override
    public void onTrackChanged(Track track) {
        // Add completed track to recently played
        if (track != null) {
            RecentlyPlayedManager.getInstance(this).addTrackToRecentlyPlayed(track);
        }

        // Handle track completion based on repeat mode
        if (repeatMode == AudioPlayerHelper.REPEAT_ONE) {
            // AudioPlayerHelper will handle repeating the track
            return;
        } else if (repeatMode == AudioPlayerHelper.REPEAT_ALL || 
                   (repeatMode == AudioPlayerHelper.REPEAT_OFF && currentTrackIndex < trackList.size() - 1)) {
            // Play next track for repeat all or when not at the end
            playNextTrack();
        } else {
            // At the end with no repeat, just notify callback
            if (playbackCallback != null) {
                playbackCallback.onTrackComplete();
            }
        }
    }
    
    // AudioPlayerHelper.OnPlaybackChangedListener implementation
    
    @Override
    public void onTrackPlay(Track track) {
        updatePlaybackState();
        updateMediaMetadata();
        updateNotification();
        if (playbackCallback != null) {
            playbackCallback.onPlaybackStateChanged(true);
        }
    }
    
    @Override
    public void onTrackPause() {
        updatePlaybackState();
        updateNotification();
        if (playbackCallback != null) {
            playbackCallback.onPlaybackStateChanged(false);
        }
    }
    
    @Override
    public void onTrackStopped() {
        updatePlaybackState();
        stopForeground(true);
        if (playbackCallback != null) {
            playbackCallback.onPlaybackStateChanged(false);
        }
    }
    
    @Override
    public void onPlaybackError(String errorMessage) {
        if (playbackCallback != null) {
            playbackCallback.onPlaybackError(errorMessage);
        }
    }
    
    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        // Update notification based on loading state
        updateNotification();
    }
    
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        updatePlaybackState();
        updateNotification();
        if (playbackCallback != null) {
            playbackCallback.onPlaybackStateChanged(isPlaying);
        }
    }
    
    @Override
    public void onBufferingUpdate(int percent) {
        // Not needed for service
    }
    
    @Override
    public void onBufferingStart() {
        // Update notification to show buffering state
        updateNotification();
    }
    
    @Override
    public void onBufferingEnd() {
        // Update notification when buffering ends
        updateNotification();
    }
    
    @Override
    public void onTrackComplete() {
        // Handle track completion based on repeat mode
        if (repeatMode == AudioPlayerHelper.REPEAT_ONE) {
            // AudioPlayerHelper will handle repeating the track
            return;
        } else if (repeatMode == AudioPlayerHelper.REPEAT_ALL || 
                   (repeatMode == AudioPlayerHelper.REPEAT_OFF && currentTrackIndex < trackList.size() - 1)) {
            // Play next track for repeat all or when not at the end
            playNextTrack();
        } else {
            // At the end with no repeat, just notify callback
            if (playbackCallback != null) {
                playbackCallback.onTrackComplete();
            }
        }
    }
    
    /**
     * Binder class for clients to access the service
     */
    public class MusicBinder extends Binder {
        public MusicPlaybackService getService() {
            return MusicPlaybackService.this;
        }
    }
    
    /**
     * Callback interface for activities to receive playback events
     */
    public interface PlaybackCallback {
        void onPlaybackStateChanged(boolean isPlaying);
        void onTrackComplete();
        void onPlaybackError(String errorMessage);
        void onPreviousClicked();
        void onNextClicked();
        void onShuffleModeChanged(boolean enabled);
        void onRepeatModeChanged(int repeatMode);
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "BananaMusicSession");
        
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (audioPlayerHelper != null) {
                    audioPlayerHelper.resumePlayback();
                }
            }

            @Override
            public void onPause() {
                if (audioPlayerHelper != null) {
                    audioPlayerHelper.pausePlayback();
                }
            }

            @Override
            public void onSkipToNext() {
                playNextTrack();
            }

            @Override
            public void onSkipToPrevious() {
                playPreviousTrack();
            }

            @Override
            public void onStop() {
                stopSelf();
            }

            @Override
            public void onSeekTo(long pos) {
                if (audioPlayerHelper != null) {
                    audioPlayerHelper.seekTo((int) pos);
                }
            }

            @Override
            public void onSetRepeatMode(int repeatMode) {
                setRepeatMode(repeatMode);
                if (playbackCallback != null) {
                    playbackCallback.onRepeatModeChanged(repeatMode);
                }
            }

            @Override
            public void onSetShuffleMode(int shuffleMode) {
                boolean enabled = shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL;
                setShuffleMode(enabled);
                if (playbackCallback != null) {
                    playbackCallback.onShuffleModeChanged(enabled);
                }
            }
        });

        mediaSession.setActive(true);
    }

    private void updatePlaybackState() {
        if (audioPlayerHelper == null) return;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_SEEK_TO
            );

        int state = audioPlayerHelper.isPlaying() ?
            PlaybackStateCompat.STATE_PLAYING :
            PlaybackStateCompat.STATE_PAUSED;

        stateBuilder.setState(
            state,
            audioPlayerHelper.getCurrentPosition(),
            1.0f
        );

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void updateMediaMetadata() {
        if (currentTrack == null) return;

        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTrack.getTitle())
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentTrack.getArtist())
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentTrack.getAlbum())
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, audioPlayerHelper.getDuration());

        if (currentTrack.getAlbumArt() != null && !currentTrack.getAlbumArt().isEmpty()) {
            Glide.with(this)
                .asBitmap()
                .load(currentTrack.getAlbumArt())
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, 
                        Transition<? super Bitmap> transition) {
                        metadataBuilder.putBitmap(
                            MediaMetadataCompat.METADATA_KEY_ALBUM_ART, 
                            resource
                        );
                        mediaSession.setMetadata(metadataBuilder.build());
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        mediaSession.setMetadata(metadataBuilder.build());
                    }
                });
        } else {
            mediaSession.setMetadata(metadataBuilder.build());
        }
    }
}