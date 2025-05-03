package my.edu.utar.bananamusic.ui.player;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.activity.OnBackPressedCallback;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

// Additional imports for Glide RequestListener
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.DataSource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.managers.MediaPlayerManager;
import my.edu.utar.bananamusic.viewmodels.PlaybackViewModel;
import my.edu.utar.bananamusic.MainActivity;

/**
 * A full-screen fragment that displays the currently playing track with playback controls
 */
public class NowPlayingFragment extends BottomSheetDialogFragment implements AudioPlayerHelper.OnPlaybackChangedListener {

    private static final String TAG = "NowPlayingFragment";
    private static final int UPDATE_INTERVAL = 500; // 0.5 second for updates
    private static final int PREVIEW_DURATION_MS = 30000; // 30 seconds in milliseconds
    
    // UI Components
    private ImageView albumArtImageView;
    private TextView trackTitleTextView;
    private TextView artistTextView;
    private TextView albumTextView;
    private MaterialButton playPauseButton;
    private MaterialButton skipPreviousButton;
    private MaterialButton skipNextButton;
    private MaterialButton shuffleButton;
    private SeekBar seekBar;
    private TextView currentTimeTextView;
    private TextView totalDurationTextView;
    private ImageButton minimizeButton;
    private ProgressBar loadingProgressBar;
    
    // Audio Player
    private AudioPlayerHelper audioPlayerHelper;
    private Track currentTrack;
    private boolean isPlaying = false;
    private boolean isBuffering = false;
    
    // Progress Update
    private Handler progressHandler;
    private Runnable progressRunnable;
    
    // Album Rotation Animation
    private ObjectAnimator rotationAnimator;
    
    // Listener
    private NowPlayingListener nowPlayingListener;
    
    // Visualizer
    private boolean visualizerEnabled = false;
    private View[] visualizerBars;
    
    // Visualizer Animation
    private AnimatorSet visualizerAnimSet;
    
    private MediaPlayerManager mediaPlayerManager;
    
    private PlaybackViewModel playbackViewModel;
    
    private boolean isUserSeeking = false;
    
    /**
     * Interface for communicating with the host activity
     */
    public interface NowPlayingListener {
        void onMinimizeClicked();
        void onPlaybackStateChanged(boolean isPlaying);
        void onNowPlayingTrackChanged(Track track);
        void onPlaybackComplete();
    }
    
    public NowPlayingFragment() {
        // Required empty public constructor
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            nowPlayingListener = (NowPlayingListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement NowPlayingListener");
        }
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme);
        mediaPlayerManager = MediaPlayerManager.getInstance();

        // Get ViewModel from activity
        playbackViewModel = ((MainActivity) requireActivity()).getPlaybackViewModel();

        // Handle back press
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                minimizeFragment();
            }
        });
    }
    
    @Override
    public void onStart() {
        super.onStart();
        
        // Get the BottomSheetDialog's behavior and set it to expanded state
        if (getDialog() != null) {
            View bottomSheet = ((View) getView().getParent());
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            
            // Set the dialog to match parent height
            bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_now_playing, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Enable swipe down to dismiss
        View rootView = view.findViewById(R.id.now_playing_root);
        if (rootView != null) {
            rootView.setOnTouchListener(new SwipeDismissTouchListener());
        }
        
        Log.d(TAG, "NowPlayingFragment onViewCreated called");
        
        // Initialize UI components
        initializeViews(view);
        
        // Apply initial styling and effects
        setupVisualEffects();
        
        // Setup audio player
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        
        // Set up progress update handler
        setupProgressHandler();
        
        // Set up album art animation
        setupAlbumArtAnimation();
        
        // Set up click listeners
        setupClickListeners();
        
        // Observe playback state
        observePlaybackState();
    }
    
    private void initializeViews(View view) {
        albumArtImageView = view.findViewById(R.id.ivTrackArt);
        trackTitleTextView = view.findViewById(R.id.tvTrackTitle);
        artistTextView = view.findViewById(R.id.tvTrackArtist);
        albumTextView = view.findViewById(R.id.tvAlbum);
        playPauseButton = view.findViewById(R.id.btnPlayPause);
        skipPreviousButton = view.findViewById(R.id.btnSkipPrevious);
        skipNextButton = view.findViewById(R.id.btnSkipNext);
        shuffleButton = view.findViewById(R.id.btnShare);
        seekBar = view.findViewById(R.id.seekbarProgress);
        currentTimeTextView = view.findViewById(R.id.tvCurrentTime);
        totalDurationTextView = view.findViewById(R.id.tvTotalDuration);
        minimizeButton = view.findViewById(R.id.btnMinimize);
        loadingProgressBar = view.findViewById(R.id.loading_progress_bar);
    }
    
    private void setupProgressHandler() {
        progressHandler = new Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            private int lastPosition = -1;
            
            @Override
            public void run() {
                if (audioPlayerHelper != null && isVisible()) {
                    // Only update if position has changed or first update
                    int currentPosition = audioPlayerHelper.getCurrentPosition();
                    if (currentPosition != lastPosition) {
                        lastPosition = currentPosition;
                        updateProgress();
                    }
                    
                    // Schedule next update if fragment is still visible
                    if (isVisible() && isPlaying && !isBuffering) {
                        progressHandler.postDelayed(this, UPDATE_INTERVAL);
                    }
                }
            }
        };
    }
    
    private void setupAlbumArtAnimation() {
        rotationAnimator = ObjectAnimator.ofFloat(albumArtImageView, "rotation", 0f, 360f);
        rotationAnimator.setDuration(30000); // 30 seconds for one full rotation
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
    }
    
    private void setupClickListeners() {
        // Play/Pause button
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        
        // Skip previous button
        skipPreviousButton.setOnClickListener(v -> skipToPrevious());
        
        // Skip next button
        skipNextButton.setOnClickListener(v -> skipToNext());
        
        // Shuffle button
        shuffleButton.setOnClickListener(v -> toggleShuffle());
        
        // Minimize button
        if (minimizeButton != null) {
            minimizeButton.setOnClickListener(v -> {
                Log.d(TAG, "Minimize button clicked");
                minimizeFragment();
            });
        } else {
            Log.e(TAG, "Minimize button not found in layout");
        }
        
        // Seek bar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && currentTrack != null) {
                    progress = Math.min(progress, PREVIEW_DURATION_MS);
                    updateCurrentTimeText(progress);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Pause updates while seeking
                stopProgressUpdates();
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (audioPlayerHelper != null && currentTrack != null) {
                    // Ensure we don't seek beyond 30 seconds
                    int progress = Math.min(seekBar.getProgress(), PREVIEW_DURATION_MS);
                    seekBar.setProgress(progress);
                    
                    // Seek to the selected position
                    audioPlayerHelper.seekTo(progress);
                    
                    // Resume updates if playing
                    if (isPlaying) {
                        startProgressUpdates();
                    }
                }
            }
        });
        
        // Setup swipe gestures for track control and volume
        setupSwipeGestures(getView());
    }
    
    /**
     * Apply visual styling and effects to the Now Playing screen
     */
    private void setupVisualEffects() {
        // Apply elevation for 3D effect
        View mainContent = getView();
        if (mainContent != null) {
            mainContent.setElevation(8f);
        }
        
        // Add a subtle scale animation on start
        View rootView = getView();
        if (rootView != null) {
            rootView.setScaleX(0.95f);
            rootView.setScaleY(0.95f);
            rootView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                .start();
        }
    }
    
    /**
     * Updates the UI with track information with enhanced loading effect
     */
    private void updateUI(Track track) {
        Log.d(TAG, "updateUI called with track: " + (track != null ? track.getTitle() : "null"));
        
        if (track == null || !isAdded()) {
            Log.e(TAG, "Cannot update UI: track is null or fragment not added");
            return;
        }
        
        try {
            // Update track title with a fallback
            String title = track.getTitle();
            if (title == null || title.isEmpty()) {
                title = "Unknown Title";
            }
            trackTitleTextView.setText(title);
            Log.d(TAG, "Set track title: " + title);
            
            // Update artist with a fallback
            String artist = track.getArtist();
            if (artist == null || artist.isEmpty()) {
                artist = "Unknown Artist";
            }
            artistTextView.setText(artist);
            Log.d(TAG, "Set artist: " + artist);
            
            // Update album name if available
            String album = track.getAlbum();
            if (album != null && !album.isEmpty()) {
                albumTextView.setText(album);
                albumTextView.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set album: " + album);
            } else {
                albumTextView.setVisibility(View.GONE);
                Log.d(TAG, "No album info available, hiding album text");
            }
            
            // Update album art
            String albumArtUrl = track.getAlbumArtUrl();
            Log.d(TAG, "Album art URL: " + albumArtUrl);
            
            try {
                if (albumArtUrl != null && !albumArtUrl.isEmpty() && !albumArtUrl.equals("null")) {
                    Log.d(TAG, "Loading album art from URL");
                    Glide.with(requireContext())
                        .load(albumArtUrl)
                        .apply(new RequestOptions()
                            .placeholder(R.drawable.default_album_art)
                            .error(R.drawable.default_album_art)
                            .transform(new RoundedCorners(16)))
                        .into(albumArtImageView);
                } else {
                    Log.d(TAG, "Using default album art");
                    Glide.with(requireContext())
                        .load(R.drawable.default_album_art)
                        .into(albumArtImageView);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading album art: " + e.getMessage());
                // Fallback to default image
                try {
                    Glide.with(requireContext())
                        .load(R.drawable.default_album_art)
                        .into(albumArtImageView);
                } catch (Exception ex) {
                    Log.e(TAG, "Error loading default art: " + ex.getMessage());
                }
            }
            
            // Always set duration to 30 seconds for preview
            totalDurationTextView.setText(formatTime(PREVIEW_DURATION_MS));
            seekBar.setMax(PREVIEW_DURATION_MS);
            Log.d(TAG, "Set preview duration: 30 seconds");
            
            // Update current position
            if (audioPlayerHelper != null) {
                int currentPosition = Math.min(audioPlayerHelper.getCurrentPosition(), PREVIEW_DURATION_MS);
                updateCurrentTimeText(currentPosition);
                seekBar.setProgress(currentPosition);
                Log.d(TAG, "Set current position: " + currentPosition + "ms");
            }
            
            // Update UI to match current playback state
            updatePlaybackState(isPlaying);
            
            Log.d(TAG, "UI updated successfully for: " + track.getTitle());
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI", e);
        }
    }
    
    /**
     * Updates the playback state UI (play/pause button and album animation)
     */
    private void updatePlaybackState(boolean isPlaying) {
        Log.d(TAG, "updatePlaybackState: " + (isPlaying ? "playing" : "paused"));
        
        if (!isAdded()) return;
        
        try {
            // Update play/pause button icon
            if (isBuffering) {
                // Show loading indicator while buffering
                playPauseButton.setIcon(getResources().getDrawable(R.drawable.ic_hourglass_empty, null));
                playPauseButton.setIconTint(ColorStateList.valueOf(Color.GRAY));
            } else if (isPlaying) {
                // Show pause icon
                playPauseButton.setIcon(getResources().getDrawable(R.drawable.ic_pause, null));
                playPauseButton.setIconTint(ColorStateList.valueOf(Color.WHITE));
                startProgressUpdates();
            } else {
                // Show play icon
                playPauseButton.setIcon(getResources().getDrawable(R.drawable.ic_play_arrow, null));
                playPauseButton.setIconTint(ColorStateList.valueOf(Color.WHITE));
                stopProgressUpdates();
            }
            
            // Update album rotation animation
            if (isPlaying && !isBuffering) {
                startAlbumArtAnimation();
            } else {
                stopAlbumArtAnimation();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating playback state", e);
        }
    }
    
    private void startAlbumArtAnimation() {
        if (rotationAnimator != null) {
            if (rotationAnimator.isPaused()) {
                rotationAnimator.resume();
            } else if (!rotationAnimator.isRunning()) {
                rotationAnimator.start();
            }
        }
    }
    
    private void stopAlbumArtAnimation() {
        if (rotationAnimator != null && rotationAnimator.isRunning() && !rotationAnimator.isPaused()) {
            rotationAnimator.pause();
        }
    }
    
    /**
     * Updates the progress bar and time display
     */
    private void updateProgress() {
        if (audioPlayerHelper != null && isAdded()) {
            int currentPosition = Math.min(audioPlayerHelper.getCurrentPosition(), PREVIEW_DURATION_MS);
            seekBar.setProgress(currentPosition);
            updateCurrentTimeText(currentPosition);
            
            // Auto-stop at 30 seconds
            if (currentPosition >= PREVIEW_DURATION_MS && isPlaying) {
                togglePlayPause();
            }
        }
    }
    
    private void updateCurrentTimeText(int milliseconds) {
        if (currentTimeTextView != null) {
            currentTimeTextView.setText(formatTime(milliseconds));
        }
    }
    
    /**
     * Formats milliseconds into a time string (mm:ss)
     */
    private String formatTime(long milliseconds) {
        SimpleDateFormat sdf = new SimpleDateFormat("m:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(milliseconds));
    }
    
    /**
     * Toggles play/pause state
     */
    private void togglePlayPause() {
        if (audioPlayerHelper != null && currentTrack != null) {
            if (isPlaying) {
                audioPlayerHelper.pause();
                playbackViewModel.updatePlaybackState(PlaybackViewModel.PlaybackState.PAUSED);
            } else {
                audioPlayerHelper.resume();
                playbackViewModel.updatePlaybackState(PlaybackViewModel.PlaybackState.PLAYING);
            }
        }
    }
    
    /**
     * Skips to the previous track
     */
    private void skipToPrevious() {
        if (audioPlayerHelper != null) {
            audioPlayerHelper.skipToPrevious();
        }
    }
    
    /**
     * Skips to the next track
     */
    private void skipToNext() {
        if (audioPlayerHelper != null) {
            audioPlayerHelper.skipToNext();
        }
    }
    
    /**
     * Toggles shuffle mode
     */
    private void toggleShuffle() {
        if (audioPlayerHelper != null) {
            boolean shuffleEnabled = audioPlayerHelper.toggleShuffle();
            updateShuffleState(shuffleEnabled);
        }
    }
    
    /**
     * Updates the shuffle button appearance
     */
    private void updateShuffleState(boolean enabled) {
        if (shuffleButton != null) {
            shuffleButton.setIconTint(enabled ? 
                colorStateList(R.color.colorSpotifyGreen) : 
                colorStateList(R.color.white));
        }
    }
    
    private android.content.res.ColorStateList colorStateList(int colorRes) {
        return android.content.res.ColorStateList.valueOf(
            getResources().getColor(colorRes, requireContext().getTheme()));
    }
    
    /**
     * Starts progress updates
     */
    private void startProgressUpdates() {
        stopProgressUpdates(); // Stop any existing updates
        progressHandler.post(progressRunnable);
    }
    
    /**
     * Stops progress updates
     */
    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Register as listener to receive playback updates
        if (audioPlayerHelper != null) {
            audioPlayerHelper.setOnPlaybackChangedListener(this);
        }
        
        // Start progress updates if a track is playing
        if (isPlaying) {
            startProgressUpdates();
        }
        
        // Get latest track and state
        if (audioPlayerHelper != null) {
            Track track = audioPlayerHelper.getCurrentTrack();
            if (track != null) {
                currentTrack = track;
                updateUI(track);
            }
            updatePlaybackState(audioPlayerHelper.isPlaying());
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Stop progress updates and animation to conserve resources
        stopProgressUpdates();
        if (rotationAnimator != null && rotationAnimator.isRunning()) {
            rotationAnimator.pause();
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up resources
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
            rotationAnimator = null;
        }
        stopProgressUpdates();
        
        // Remove audio player listener
        if (audioPlayerHelper != null) {
            audioPlayerHelper.setOnPlaybackChangedListener(null);
        }
    }
    
    // AudioPlayerHelper.OnPlaybackChangedListener implementation
    @Override
    public void onTrackPlay(Track track) {
        if (track != null && isAdded()) {
            requireActivity().runOnUiThread(() -> {
                currentTrack = track;
                updateUI(track);
                updatePlaybackState(true);
                startProgressUpdates();
                
                // Notify listener
                if (nowPlayingListener != null) {
                    nowPlayingListener.onNowPlayingTrackChanged(track);
                }
            });
        }
    }
    
    @Override
    public void onTrackPause() {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                updatePlaybackState(false);
                stopProgressUpdates();
            });
        }
    }
    
    @Override
    public void onTrackStopped() {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                updatePlaybackState(false);
                stopProgressUpdates();
            });
        }
    }
    
    @Override
    public void onTrackChanged(Track track) {
        if (track != null && isAdded()) {
            requireActivity().runOnUiThread(() -> {
                currentTrack = track;
                updateUI(track);
                
                // Notify listener
                if (nowPlayingListener != null) {
                    nowPlayingListener.onNowPlayingTrackChanged(track);
                }
            });
        }
    }
    
    @Override
    public void onPlaybackError(String errorMessage) {
        // Could show an error message to the user
    }
    
    @Override
    public void onBufferingStart() {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                isBuffering = true;
                // Could show a buffering indicator
            });
        }
    }
    
    @Override
    public void onBufferingEnd() {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                isBuffering = false;
                // Could hide the buffering indicator
            });
        }
    }
    
    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        // Could update loading UI if needed
    }
    
    @Override
    public void onBufferingUpdate(int percent) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                // Update secondary progress to show buffering
                seekBar.setSecondaryProgress((int) (seekBar.getMax() * (percent / 100f)));
            });
        }
    }
    
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (!isAdded()) return;
        
        Log.d(TAG, "onPlaybackStateChanged: " + (isPlaying ? "playing" : "paused"));
        
        requireActivity().runOnUiThread(() -> {
            try {
                // Update local state
                this.isPlaying = isPlaying;
                
                // Update UI
                updatePlaybackState(isPlaying);
                
                // Handle animation and progress updates
                if (isPlaying) {
                    startProgressUpdates();
                    if (rotationAnimator != null) {
                        rotationAnimator.resume();
                    }
                } else {
                    stopProgressUpdates();
                    if (rotationAnimator != null) {
                        rotationAnimator.pause();
                    }
                }
                
                // Notify MainActivity to update mini player
                if (nowPlayingListener != null) {
                    nowPlayingListener.onPlaybackStateChanged(isPlaying);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onPlaybackStateChanged", e);
            }
        });
    }
    
    @Override
    public void onTrackComplete() {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                updatePlaybackState(false);
                stopProgressUpdates();
                // Reset UI or prepare for next track
                if (nowPlayingListener != null) {
                    nowPlayingListener.onPlaybackComplete();
                }
            });
        }
    }
    
    /**
     * Updates the fragment with the given track data
     * This method can be called from MainActivity
     */
    public void updateTrack(Track track) {
        Log.d(TAG, "updateTrack called with track: " + (track != null ? track.getTitle() : "null"));
        
        if (track == null) {
            Log.e(TAG, "Cannot update with null track");
            return;
        }
        
        try {
            // Save track reference
            this.currentTrack = track;
            
            // Update UI in main thread
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    try {
                        // Update UI with track details
                        updateUI(track);
                        Log.d(TAG, "Successfully updated UI with track: " + track.getTitle());
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating UI: " + e.getMessage(), e);
                    }
                });
            } else {
                Log.w(TAG, "Fragment not attached, can't update UI immediately");
                // Save the track for when fragment attaches
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in updateTrack: " + e.getMessage(), e);
        }
    }
    
    /**
     * Shows visual feedback for swipe gestures
     */
    private void showSwipeFeedback(boolean isPrevious) {
        // Create an arrow indicator view
        ImageView indicator = new ImageView(getContext());
        // Use existing icons or create placeholders if missing
        indicator.setImageResource(isPrevious ? R.drawable.ic_skip_previous : R.drawable.ic_skip_next);
        indicator.setColorFilter(Color.WHITE);
        indicator.setAlpha(0.7f);
        
        // Add to layout
        ViewGroup root = (ViewGroup) getView();
        if (root != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.CENTER;
            root.addView(indicator, params);
            
            // Animate in and out
            indicator.setScaleX(0);
            indicator.setScaleY(0);
            indicator.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .alpha(0.9f)
                .setDuration(150)
                .withEndAction(() -> 
                    indicator.animate()
                        .scaleX(0)
                        .scaleY(0)
                        .alpha(0)
                        .setDuration(150)
                        .withEndAction(() -> root.removeView(indicator))
                        .start()
                )
                .start();
        }
    }
    
    /**
     * Shows a volume change indicator
     */
    private void showVolumeIndicator(int volumePercent, boolean isIncrease) {
        // Create the volume indicator view
        LinearLayout volumeIndicator = new LinearLayout(getContext());
        volumeIndicator.setOrientation(LinearLayout.HORIZONTAL);
        volumeIndicator.setGravity(Gravity.CENTER);
        volumeIndicator.setPadding(16, 8, 16, 8);
        volumeIndicator.setBackgroundResource(R.drawable.rounded_translucent_background);
        
        // Add volume icon (use existing icon resources)
        ImageView volumeIcon = new ImageView(getContext());
        // Use a simple volume icon or fallback to text-only
        try {
            // Try to use a generic icon that should exist
            volumeIcon.setImageResource(R.drawable.ic_share); // Using any existing icon as fallback
        } catch (Exception e) {
            // If icon doesn't exist, just use text
            volumeIcon.setVisibility(View.GONE);
        }
        volumeIcon.setColorFilter(Color.WHITE);
        
        // Add volume text
        TextView volumeText = new TextView(getContext());
        // Add direction indicator to text
        String volumeIndicatorText = isIncrease ? "↑ " + volumePercent + "%" : "↓ " + volumePercent + "%";
        volumeText.setText(volumeIndicatorText);
        volumeText.setTextColor(Color.WHITE);
        volumeText.setPadding(16, 0, 0, 0);
        
        volumeIndicator.addView(volumeIcon);
        volumeIndicator.addView(volumeText);
        
        // Add to layout
        ViewGroup root = (ViewGroup) getView();
        if (root != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.CENTER;
            root.addView(volumeIndicator, params);
            
            // Animate in and out
            volumeIndicator.setAlpha(0);
            volumeIndicator.setScaleX(0.7f);
            volumeIndicator.setScaleY(0.7f);
            volumeIndicator.animate()
                .alpha(1)
                .scaleX(1)
                .scaleY(1)
                .setDuration(200)
                .withEndAction(() -> {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        volumeIndicator.animate()
                            .alpha(0)
                            .scaleX(0.7f)
                            .scaleY(0.7f)
                            .setDuration(200)
                            .withEndAction(() -> root.removeView(volumeIndicator))
                            .start();
                    }, 800);
                })
                .start();
        }
    }
    
    /**
     * Sets up swipe gestures for navigating between tracks
     */
    private void setupSwipeGestures(View view) {
        GestureDetector swipeDetector = new GestureDetector(getContext(), 
            new GestureDetector.SimpleOnGestureListener() {
                private static final int SWIPE_THRESHOLD = 100;
                private static final int SWIPE_VELOCITY_THRESHOLD = 100;
                
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    boolean result = false;
                    
                    try {
                        if (e1 == null || e2 == null) return false;
                        
                        float diffY = e2.getY() - e1.getY();
                        float diffX = e2.getX() - e1.getX();
                        
                        // Horizontal swipe detection (left or right)
                        if (Math.abs(diffX) > Math.abs(diffY) && 
                            Math.abs(diffX) > SWIPE_THRESHOLD && 
                            Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            
                            // Right to left swipe - next track
                            if (diffX < 0) {
                                skipToNext();
                                // Visual feedback for swipe
                                showSwipeFeedback(false);
                                result = true;
                            } 
                            // Left to right swipe - previous track
                            else {
                                skipToPrevious();
                                // Visual feedback for swipe
                                showSwipeFeedback(true);
                                result = true;
                            }
                        }
                        
                        // Vertical swipe detection (volume control)
                        if (Math.abs(diffY) > Math.abs(diffX) &&
                            Math.abs(diffY) > SWIPE_THRESHOLD &&
                            Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            
                            // Bottom to top swipe - volume up
                            if (diffY < 0) {
                                adjustVolume(true);
                                result = true;
                            }
                            // Top to bottom swipe - volume down
                            else {
                                adjustVolume(false);
                                result = true;
                            }
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error detecting swipe", e);
                    }
                    
                    return result;
                }
            });
        
        // Apply the gesture detector to the album art view
        if (albumArtImageView != null) {
            albumArtImageView.setOnTouchListener((v, event) -> {
                // Let the gesture detector handle the event
                return swipeDetector.onTouchEvent(event);
            });
        }
    }
    
    /**
     * Adjusts device volume
     */
    private void adjustVolume(boolean increase) {
        try {
            AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int newVolume;
                
                if (increase) {
                    newVolume = Math.min(currentVolume + 1, maxVolume);
                } else {
                    newVolume = Math.max(currentVolume - 1, 0);
                }
                
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                
                // Show volume indicator
                int volumePercent = (int) ((float) newVolume / maxVolume * 100);
                showVolumeIndicator(volumePercent, increase);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting volume", e);
        }
    }
    
    /**
     * Toggle visualizer on/off
     */
    public void toggleVisualizer() {
        visualizerEnabled = !visualizerEnabled;
        
        // Update visualizer state
        if (visualizerEnabled) {
            startVisualizer();
        } else {
            pauseVisualizer();
            // Reset bars to original state
            for (View bar : visualizerBars) {
                if (bar != null) {
                    bar.setScaleY(0.2f);
                }
            }
        }
        
        // Toast notification
        Toast.makeText(
            requireContext(), 
            visualizerEnabled ? "Visualizer enabled" : "Visualizer disabled", 
            Toast.LENGTH_SHORT
        ).show();
    }
    
    /**
     * Starts visualizer animation if enabled
     */
    private void startVisualizer() {
        if (visualizerEnabled && visualizerAnimSet != null && isPlaying && !isBuffering) {
            visualizerAnimSet.start();
        }
    }
    
    /**
     * Pauses visualizer animation
     */
    private void pauseVisualizer() {
        if (visualizerAnimSet != null && visualizerAnimSet.isRunning()) {
            visualizerAnimSet.cancel();
        }
    }
    
    private void updateTrackInfo(Track track) {
        if (track == null) return;
        
        if (trackTitleTextView != null) {
            trackTitleTextView.setText(track.getTitle());
        }
        if (artistTextView != null) {
            artistTextView.setText(track.getArtist());
        }
        if (albumArtImageView != null) {
            Glide.with(this)
                .load(track.getAlbumArtUrl())
                .placeholder(R.drawable.default_album_art)
                .error(R.drawable.default_album_art)
                .into(albumArtImageView);
        }
    }
    
    private void updatePlayPauseButton(boolean isPlaying) {
        if (playPauseButton != null) {
            playPauseButton.setIcon(getResources().getDrawable(
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                requireContext().getTheme()
            ));
        }
    }
    
    private void updateTimeLabels(long currentPosition, long duration) {
        if (currentTimeTextView != null) {
            currentTimeTextView.setText(formatTime(currentPosition));
        }
        if (totalDurationTextView != null) {
            totalDurationTextView.setText(formatTime(duration));
        }
    }

    private class SwipeDismissTouchListener implements View.OnTouchListener {
        private float startY;
        private static final float SWIPE_THRESHOLD = 100;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    return true;

                case MotionEvent.ACTION_UP:
                    float deltaY = event.getY() - startY;
                    if (deltaY > SWIPE_THRESHOLD) {
                        // Swipe down detected, dismiss the fragment
                        dismiss();
                        return true;
                    }
                    break;
            }
            return false;
        }
    }

    @Override
    public void dismiss() {
        try {
            // Remove the recursive call to onMinimizeClicked
            super.dismiss();
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing fragment", e);
        }
    }

    private void minimizeFragment() {
        // Stop all updates before minimizing
        stopProgressUpdates();
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
        }
        
        // Remove audio player listener
        if (audioPlayerHelper != null) {
            audioPlayerHelper.setOnPlaybackChangedListener(null);
        }
        
        // Notify the activity that we're minimizing
        if (nowPlayingListener != null) {
            nowPlayingListener.onMinimizeClicked();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up any resources
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
            rotationAnimator = null;
        }
        stopProgressUpdates();
        
        // Ensure we're not holding any references
        audioPlayerHelper = null;
        playbackViewModel = null;
        nowPlayingListener = null;
    }

    public static NowPlayingFragment newInstance() {
        return new NowPlayingFragment();
    }

    private void observePlaybackState() {
        // Observe track changes
        playbackViewModel.getCurrentTrack().observe(getViewLifecycleOwner(), this::updateTrackInfo);

        // Observe playback state
        playbackViewModel.getPlaybackState().observe(getViewLifecycleOwner(), state -> {
            boolean isPlaying = state == PlaybackViewModel.PlaybackState.PLAYING;
            boolean isBuffering = state == PlaybackViewModel.PlaybackState.BUFFERING;
            
            // Update play/pause button
            updatePlayPauseButton(isPlaying);
            
            // Show/hide loading indicator
            if (loadingProgressBar != null) {
                loadingProgressBar.setVisibility(isBuffering ? View.VISIBLE : View.GONE);
            }
            
            // Update seekbar state
            if (seekBar != null) {
                seekBar.setEnabled(!isBuffering);
            }
            
            // Start/stop progress updates
            if (isPlaying) {
                startProgressUpdates();
            } else {
                stopProgressUpdates();
            }
        });

        // Observe buffering progress
        playbackViewModel.getBufferingProgress().observe(getViewLifecycleOwner(), progress -> {
            if (seekBar != null) {
                seekBar.setSecondaryProgress(progress);
            }
        });

        // Observe playback progress
        playbackViewModel.getPlaybackProgress().observe(getViewLifecycleOwner(), progress -> {
            if (seekBar != null && !isUserSeeking) {
                seekBar.setProgress(progress);
                updateTimeLabels(progress, playbackViewModel.getCurrentDuration());
            }
        });

        // Observe duration changes
        playbackViewModel.getDuration().observe(getViewLifecycleOwner(), duration -> {
            if (seekBar != null) {
                seekBar.setMax(duration.intValue());
                updateTimeLabels(playbackViewModel.getCurrentProgress(), duration);
            }
        });

        // Observe error messages
        playbackViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showError(error);
            }
        });
    }

    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}