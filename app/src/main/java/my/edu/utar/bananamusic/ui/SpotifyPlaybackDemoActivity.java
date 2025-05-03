package my.edu.utar.bananamusic.ui;

import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.SpotifyPlayer;

public class SpotifyPlaybackDemoActivity extends AppCompatActivity {
    private static final String TAG = "SpotifyPlaybackDemo";
    
    // Demo track - Bohemian Rhapsody by Queen
    private static final String DEMO_TRACK_ID = "3z8h0TU7ReDPLIbEnYhWZb";
    
    private SpotifyPlayer spotifyPlayer;
    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    private Button changeTrackButton;
    private TextView trackInfoTextView;
    private TextView statusTextView;
    private ImageView albumArtImageView;
    private ProgressBar bufferingProgressBar;
    
    // Additional track IDs to test with
    private static final String[] SAMPLE_TRACK_IDS = {
        "3z8h0TU7ReDPLIbEnYhWZb", // Bohemian Rhapsody
        "4cOdK2wGLETKBW3PvgPWqT", // Billie Jean
        "0vjeOZ3Ft5jvAi9SBFJm1j", // Another one bites the dust
        "2EEeOnHehOozLq4aS0n6SL", // Beat It
        "5T8EDUDqKcs6OSOwEsfqG7"  // Don't Stop Me Now
    };
    private int currentTrackIndex = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spotify_playback_demo);
        
        // Set volume control to music stream
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        // Initialize UI components
        playButton = findViewById(R.id.play_button);
        pauseButton = findViewById(R.id.pause_button);
        stopButton = findViewById(R.id.stop_button);
        trackInfoTextView = findViewById(R.id.track_info);
        statusTextView = findViewById(R.id.status_text);
        albumArtImageView = findViewById(R.id.album_art);
        bufferingProgressBar = findViewById(R.id.buffering_progress);
        
        // Add a button to change tracks for testing different songs
        changeTrackButton = findViewById(R.id.change_track_button);
        if (changeTrackButton != null) {
            changeTrackButton.setOnClickListener(v -> changeTrack());
        }
        
        // Initialize SpotifyPlayer
        spotifyPlayer = new SpotifyPlayer(this);
        
        // Set up the player callback
        spotifyPlayer.setPlayerCallback(new SpotifyPlayer.PlayerCallback() {
            @Override
            public void onPlaybackStarted(Track track) {
                runOnUiThread(() -> {
                    statusTextView.setText("Playing");
                    updateTrackInfo(track);
                    updatePlaybackControls(true);
                    bufferingProgressBar.setVisibility(View.INVISIBLE);
                    
                    Log.d(TAG, "Playback started for track: " + track.getTitle());
                    Toast.makeText(SpotifyPlaybackDemoActivity.this, 
                            "Now playing: " + track.getTitle(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onPlaybackPaused() {
                runOnUiThread(() -> {
                    statusTextView.setText("Paused");
                    updatePlaybackControls(false);
                });
            }
            
            @Override
            public void onPlaybackStopped() {
                runOnUiThread(() -> {
                    statusTextView.setText("Stopped");
                    updatePlaybackControls(false);
                });
            }
            
            @Override
            public void onPlaybackError(String message) {
                runOnUiThread(() -> {
                    statusTextView.setText("Error: " + message);
                    updatePlaybackControls(false);
                    bufferingProgressBar.setVisibility(View.INVISIBLE);
                    Toast.makeText(SpotifyPlaybackDemoActivity.this, "Playback error: " + message, Toast.LENGTH_SHORT).show();
                    
                    Log.e(TAG, "Playback error: " + message);
                    
                    // If the error indicates no audio is available, try an alternate track
                    if (message.contains("preview") || message.contains("audio") || message.contains("malformed")) {
                        new android.os.Handler().postDelayed(() -> {
                            Toast.makeText(SpotifyPlaybackDemoActivity.this, 
                                    "Trying a different track...", Toast.LENGTH_SHORT).show();
                            changeTrack();
                        }, 2000);
                    }
                });
            }
            
            @Override
            public void onBufferingUpdate(int percent) {
                runOnUiThread(() -> {
                    if (bufferingProgressBar.getVisibility() == View.VISIBLE) {
                        bufferingProgressBar.setProgress(percent);
                    }
                });
            }
        });
        
        // Set up button click listeners
        playButton.setOnClickListener(v -> playDemoTrack());
        pauseButton.setOnClickListener(v -> pausePlayback());
        stopButton.setOnClickListener(v -> stopPlayback());
        
        // Check for audio permission
        checkAudioPermission();
    }
    
    /**
     * Check for audio recording permission (needed on some devices for audio playback)
     */
    private void checkAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                // Some devices require this permission for proper audio functioning
                requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, 
                    101
                );
            }
        }
    }
    
    /**
     * Change to a different track
     */
    private void changeTrack() {
        currentTrackIndex = (currentTrackIndex + 1) % SAMPLE_TRACK_IDS.length;
        String newTrackId = SAMPLE_TRACK_IDS[currentTrackIndex];
        
        statusTextView.setText("Loading new track...");
        bufferingProgressBar.setVisibility(View.VISIBLE);
        bufferingProgressBar.setProgress(0);
        
        spotifyPlayer.playTrack(newTrackId);
    }
    
    /**
     * Start playing the demo track
     */
    private void playDemoTrack() {
        statusTextView.setText("Loading...");
        bufferingProgressBar.setVisibility(View.VISIBLE);
        bufferingProgressBar.setProgress(0);
        
        // If we're already playing, just resume
        if (spotifyPlayer.getCurrentTrack() != null && !spotifyPlayer.isPlaying()) {
            spotifyPlayer.resume();
            return;
        }
        
        // Otherwise start new playback
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            // Check if device is muted or volume is too low
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            
            if (currentVolume == 0) {
                Toast.makeText(this, "Warning: Device volume is set to 0. Increasing volume...", 
                        Toast.LENGTH_SHORT).show();
                // Set to 70% of max volume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 
                        (int)(maxVolume * 0.7f), AudioManager.FLAG_SHOW_UI);
            } else if (currentVolume < maxVolume / 5) { // Less than 20% of max volume
                Toast.makeText(this, "Warning: Device volume is low. Consider increasing volume.",
                        Toast.LENGTH_SHORT).show();
            }
        }
        
        // Start playback
        spotifyPlayer.playTrack(DEMO_TRACK_ID);
    }
    
    /**
     * Pause playback
     */
    private void pausePlayback() {
        spotifyPlayer.pause();
    }
    
    /**
     * Stop playback
     */
    private void stopPlayback() {
        spotifyPlayer.stop();
        bufferingProgressBar.setVisibility(View.INVISIBLE);
    }
    
    /**
     * Update the UI with track information
     */
    private void updateTrackInfo(Track track) {
        if (track != null) {
            // Format track info
            String infoText = String.format("%s\nby %s\n%s",
                    track.getTitle(),
                    track.getArtist(),
                    track.getAlbum());
                    
            trackInfoTextView.setText(infoText);
            
            // Debug the preview URL
            Log.d(TAG, "Track preview URL: " + track.getPreviewUrl());
            
            // Load album art if available
            if (track.getAlbumArtUrl() != null && !track.getAlbumArtUrl().isEmpty()) {
                Glide.with(this)
                    .load(track.getAlbumArtUrl())
                    .apply(new RequestOptions().placeholder(R.drawable.default_album_art))
                    .into(albumArtImageView);
            } else {
                // Use default artwork
                albumArtImageView.setImageResource(R.drawable.default_album_art);
            }
        }
    }
    
    /**
     * Update the button states based on playback status
     */
    private void updatePlaybackControls(boolean isPlaying) {
        playButton.setEnabled(!isPlaying);
        pauseButton.setEnabled(isPlaying);
        stopButton.setEnabled(isPlaying);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Request audio focus when the activity comes to the foreground
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            // Check if device is muted
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (currentVolume == 0) {
                Toast.makeText(this, "Warning: Device volume is set to 0. Playback may be silent.", 
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Pause playback when the activity goes to the background
        if (spotifyPlayer != null && spotifyPlayer.isPlaying()) {
            spotifyPlayer.pause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (spotifyPlayer != null) {
            spotifyPlayer.releaseMediaPlayer();
        }
    }
} 