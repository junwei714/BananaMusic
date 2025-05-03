package my.edu.utar.bananamusic.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.HapticFeedbackConstants;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AlbumArtColorExtractor;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.PlaylistDetailsHelper;
import my.edu.utar.bananamusic.viewmodels.NowPlayingViewModel;

/**
 * A full-screen fragment that displays the currently playing track with playback controls
 */
public class NowPlayingFragment extends Fragment {

    private static final String TAG = "NowPlayingFragment";
    private static final int PROGRESS_UPDATE_INTERVAL = 1000; // 1 second for better performance
    
    // UI Components
    private ImageView ivAlbumArt;
    private TextView tvNowPlaying;
    private TextView tvTrackTitle;
    private TextView tvArtist;
    private TextView tvAlbum;
    private TextView tvCurrentTime;
    private TextView tvTotalDuration;
    private SeekBar seekBarProgress;
    private MaterialButton btnPlayPause, btnSkipNext, btnSkipPrevious, btnShuffle, btnAddToPlaylist, btnShare;
    private MaterialCardView btnCollapsePlayer;
    
    // Audio Player
    private AudioPlayerHelper audioPlayerHelper;
    private Track currentTrack;
    private boolean isPlaying = false;
    private boolean isBuffering = false;
    private boolean isUserSeeking = false;
    
    // Progress Update
    private Handler progressHandler;
    private Runnable progressRunnable;
    
    // Album Rotation Animation
    private ObjectAnimator albumArtRotation;
    
    // ViewModel
    private NowPlayingViewModel viewModel;
    
    // Interface for communication with the host activity
    public interface NowPlayingListener {
        void onMinimizeClicked();
        void onPlaybackStateChanged(boolean isPlaying);
        void onTrackChanged(Track track);
        void onNowPlayingDismissed();
        void onPlayerMinimize();
    }
    
    private NowPlayingListener nowPlayingListener;
    
    // UI Elements
    private View backgroundView;
    private View albumArtContainer;
    
    // Animation for album art
    private boolean isRotationAnimationPlaying = false;
    
    // For debounce mechanism
    private long lastSeekUpdateTime = 0;
    private static final long SEEK_UPDATE_DEBOUNCE_TIME = 250; // in milliseconds
    
    // Add a flag to prevent recursive calls
    private boolean isUpdatingFromMainActivity = false;
    
    // Add enhanced animation properties
    private static final int ALBUM_ROTATION_DURATION = 30000; // 30 seconds per rotation
    private static final float MAX_BLUR_RADIUS = 25f;
    private static final long TRANSITION_DURATION = 300;
    private ValueAnimator colorTransitionAnimator;
    private boolean isTransitioning = false;
    
    // Add these fields to the class variables section after other animation-related fields
    private float initialY;
    private float initialTouchY;
    private boolean isDragging = false;
    private static final float DISMISS_THRESHOLD = 0.3f; // 30% of screen height for dismiss
    private static final float VELOCITY_THRESHOLD = 800f; // Velocity threshold for flinging
    private VelocityTracker velocityTracker;
    private View rootView;
    private float screenHeight;
    private View dimOverlay;
    
    public NowPlayingFragment() {
        // Required empty public constructor
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            // Check if the context implements our listener interface
            if (context instanceof NowPlayingListener) {
                nowPlayingListener = (NowPlayingListener) context;
            } else {
                Log.w(TAG, "Activity does not implement NowPlayingListener, some features may not work properly");
            }
        } catch (ClassCastException e) {
            // Don't throw an exception, just log a warning
            Log.w(TAG, "Activity does not implement NowPlayingListener interface: " + e.getMessage());
        }
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "NowPlayingFragment onCreate called");
        
        // Retain the fragment instance across configuration changes
        setRetainInstance(true);
        
        // Extract any track data passed via arguments
        Bundle args = getArguments();
        if (args != null && args.containsKey("track")) {
            try {
                currentTrack = args.getParcelable("track");
                isPlaying = args.getBoolean("is_playing", false);
                Log.d(TAG, "Received track via arguments: " + (currentTrack != null ? currentTrack.getTitle() : "null"));
            } catch (Exception e) {
                Log.e(TAG, "Error extracting track data from arguments", e);
            }
        }
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "NowPlayingFragment onCreateView called");
        View view = inflater.inflate(R.layout.fragment_now_playing, container, false);
        
        initViews(view);
        setupClickListeners();
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views and setup UI
        initViews(view);
        setupClickListeners();
        
        // Get ViewModel from activity
        viewModel = new ViewModelProvider(requireActivity()).get(NowPlayingViewModel.class);
        
        // Restore state if needed
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
        
        // Start observing ViewModel
        observeViewModel();
        
        // Update UI with current state
        Track currentTrack = viewModel.getCurrentTrack().getValue();
        if (currentTrack != null) {
            updateUI(currentTrack);
        }
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save current state
        outState.putBoolean("isPlaying", isPlaying);
        outState.putBoolean("isBuffering", isBuffering);
        outState.putBoolean("isUserSeeking", isUserSeeking);
        if (seekBarProgress != null) {
            outState.putInt("seekPosition", seekBarProgress.getProgress());
        }
    }
    
    private void restoreState(Bundle savedInstanceState) {
        isPlaying = savedInstanceState.getBoolean("isPlaying", false);
        isBuffering = savedInstanceState.getBoolean("isBuffering", false);
        isUserSeeking = savedInstanceState.getBoolean("isUserSeeking", false);
        int seekPosition = savedInstanceState.getInt("seekPosition", 0);
        if (seekBarProgress != null) {
            seekBarProgress.setProgress(seekPosition);
        }
        updatePlayPauseButton(isPlaying);
    }
    
    private void observeViewModel() {
        // Observe track changes
        viewModel.getCurrentTrack().observe(getViewLifecycleOwner(), track -> {
            if (track != null) {
                currentTrack = track;
                updateUI(track);
                
                // Notify the activity of track change
                if (nowPlayingListener != null) {
                    nowPlayingListener.onTrackChanged(track);
                }
            }
        });
        
        // Observe playback state changes
        viewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            isPlaying = playing;
            updatePlayPauseButton(playing);
            
            if (playing) {
                        startProgressUpdates();
                        startAlbumArtAnimation();
                } else {
                stopProgressUpdates();
                stopAlbumArtAnimation();
            }
            
            // Notify activity of playback state change
            if (nowPlayingListener != null) {
                nowPlayingListener.onPlaybackStateChanged(playing);
            }
        });
        
        // Observe buffering state
        viewModel.getIsBuffering().observe(getViewLifecycleOwner(), buffering -> {
            isBuffering = buffering;
            updatePlayPauseButton(isPlaying);
        });
        
        // Observe duration changes
        viewModel.getDuration().observe(getViewLifecycleOwner(), duration -> {
            if (seekBarProgress != null && duration > 0) {
                seekBarProgress.setMax(duration);
                tvTotalDuration.setText(formatDuration(duration));
            }
        });
        
        // Observe buffering progress
        viewModel.getBufferingPercent().observe(getViewLifecycleOwner(), percent -> {
            if (seekBarProgress != null) {
                int duration = seekBarProgress.getMax();
                int secondaryProgress = (int) (duration * (percent / 100f));
                seekBarProgress.setSecondaryProgress(secondaryProgress);
            }
        });
        
        // Observe error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                String userFriendlyMessage = getUserFriendlyErrorMessage(errorMessage);
                Toast.makeText(getContext(), userFriendlyMessage, Toast.LENGTH_LONG).show();
                
                // Reset the error so we don't show the same error multiple times
                viewModel.clearErrorMessage();
                
                // Attempt recovery if possible
                attemptPlaybackRecovery();
            }
        });
    }
    
    private void initViews(View view) {
        ivAlbumArt = view.findViewById(R.id.ivAlbumArt);
        albumArtContainer = view.findViewById(R.id.ivTrackArt);
        tvNowPlaying = view.findViewById(R.id.tvTrackTitle);
        tvTrackTitle = view.findViewById(R.id.tvTrackTitle);
        tvArtist = view.findViewById(R.id.tvArtist);
        tvAlbum = view.findViewById(R.id.tvAlbum);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalDuration = view.findViewById(R.id.tvTotalDuration);
        seekBarProgress = view.findViewById(R.id.seekBar);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        btnSkipNext = view.findViewById(R.id.btnSkipNext);
        btnSkipPrevious = view.findViewById(R.id.btnSkipPrevious);
        btnShuffle = view.findViewById(R.id.btnShuffle);
        btnAddToPlaylist = view.findViewById(R.id.btnAddToPlaylist);
        btnShare = view.findViewById(R.id.btnShare);
        btnCollapsePlayer = view.findViewById(R.id.btnMinimize);
    }
    
    private void setupClickListeners() {
        // Add btnCollapsePlayer click listener
        btnCollapsePlayer.setOnClickListener(v -> {
            minimizePlayer();
        });
        
        // Enhance play/pause button with haptic feedback and visual animation
        btnPlayPause.setOnClickListener(v -> {
            // Add haptic feedback for better user experience
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            
            // Play button press animation
            animateButtonPress(btnPlayPause);
            
            // Use ViewModel to control media player
            viewModel.playPause();
        });
        
        // Enhance skip next button with haptic feedback and visual animation
        btnSkipNext.setOnClickListener(v -> {
            // Add haptic feedback for better user experience
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            
            // Play button press animation
            animateButtonPress(btnSkipNext);
            
            // Use ViewModel to control media player, wrap in try-catch to handle edge cases
            try {
                // Show visual loading state
                if (!isBuffering) {
                    showBufferingState(true);
                }
                
                // Skip to next track
                viewModel.skipNext();
            } catch (Exception e) {
                Log.e(TAG, "Error skipping to next track", e);
                showBufferingState(false);
                Toast.makeText(getContext(), "Unable to skip to next track", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Enhance skip previous button with haptic feedback and visual animation
        btnSkipPrevious.setOnClickListener(v -> {
            // Add haptic feedback for better user experience
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            
            // Play button press animation
            animateButtonPress(btnSkipPrevious);
            
            // Use ViewModel to control media player, wrap in try-catch to handle edge cases
            try {
                // Show visual loading state
                if (!isBuffering) {
                    showBufferingState(true);
                }
                
                // Skip to previous track
                viewModel.skipPrevious();
            } catch (Exception e) {
                Log.e(TAG, "Error skipping to previous track", e);
                showBufferingState(false);
                Toast.makeText(getContext(), "Unable to skip to previous track", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnShuffle.setOnClickListener(v -> {
            // Add haptic feedback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            
            // Animate button
            animateButtonPress(btnShuffle);
            
            // Toggle shuffle
            viewModel.toggleShuffle();
            updateShuffleButton(viewModel.isShuffleEnabled());
        });
        
        btnAddToPlaylist.setOnClickListener(v -> {
            if (currentTrack != null) {
                // Add haptic feedback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                
                // Animate button
                animateButtonPress(btnAddToPlaylist);
                
                // Show the add to playlist dialog using the helper class
                PlaylistDetailsHelper.showAddTrackToPlaylistDialog(getContext(), currentTrack);
            }
        });
        
        btnShare.setOnClickListener(v -> {
            if (currentTrack != null) {
                // Add haptic feedback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                
                // Animate button press
                animateButtonPress(btnShare);
                
                // Share the track
                shareTrack(currentTrack);
            } else {
                Toast.makeText(getContext(), "No track to share", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Setup SeekBar change listener with debounce
        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Update the UI immediately for responsiveness
                    tvCurrentTime.setText(formatDuration(progress));
                    
                    // Only perform seeking with debounce
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastSeekUpdateTime > SEEK_UPDATE_DEBOUNCE_TIME) {
                        lastSeekUpdateTime = currentTime;
                        // This only updates the UI, not the actual playback position
                    }
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
                // Stop progress updates while user is seeking
                stopProgressUpdates();
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                // Add haptic feedback when seeking completes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                
                // Seek to position via ViewModel
                viewModel.seekTo(seekBar.getProgress());
                
                // Resume progress updates
                startProgressUpdates();
            }
        });
    }
    
    /**
     * Animate button press for visual feedback
     */
    private void animateButtonPress(View button) {
        if (button == null) return;
        
        button.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(50)
            .withEndAction(() -> 
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start())
            .start();
    }
    
    /**
     * Show or hide buffering state on UI elements
     */
    private void showBufferingState(boolean buffering) {
        isBuffering = buffering;
        updatePlayPauseButton(isPlaying);
        
        // Additional visual cues could be added here
        if (buffering) {
            // Could show a loading spinner or other visual indicator
        }
    }
    
    /**
     * Update the UI with track information
     * Safely handles the case when the fragment is not attached to a context
     */
    public void updateUI(Track track) {
        if (track == null) return;
        
        // Check if fragment is attached to avoid context-related errors
        if (!isAdded()) {
            Log.d(TAG, "Fragment not attached, skipping updateUI for track: " + track.getTitle());
            // Store the track so we can update UI when the fragment is attached
        currentTrack = track;
            return;
        }
        
        Log.d(TAG, "Updating UI with track: " + track.getTitle());
        
        try {
            // Load album art with Glide
            String albumArtUrl = track.getAlbumArt();
            if (albumArtUrl != null && !albumArtUrl.isEmpty()) {
                loadAlbumArtWithGlide(albumArtUrl);
            } else {
                ivAlbumArt.setImageResource(R.drawable.default_album_art);
                
                // Generate a color for the album art
                int generatedColor = generateColorFromTrack(track);
                updateBackgroundGradient(generatedColor, darker(generatedColor, 0.7f));
            }
            
            // Set track information
        tvTrackTitle.setText(track.getTitle());
        tvArtist.setText(track.getArtist());
        tvAlbum.setText(track.getAlbum());
        
            // Set track duration
            long duration = track.getDuration();
            tvTotalDuration.setText(formatTime(duration));
            
            // Reset seek bar if needed
            if (seekBarProgress != null) {
                seekBarProgress.setProgress(0);
            }
            
            // Update playback state
            updatePlaybackState(isPlaying);
        
        // Extract colors from album art for UI styling
        extractColorsFromAlbumArt(track.getAlbumArtUrl());
        
            // Only notify listener if this update didn't come from MainActivity
            if (nowPlayingListener != null && !isUpdatingFromMainActivity) {
            nowPlayingListener.onTrackChanged(track);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI with track: " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to load album art using Glide with error handling
     * and dynamic background color extraction
     */
    private void loadAlbumArtWithGlide(String url) {
        if (getContext() == null || url == null || url.isEmpty()) {
            return;
        }
        
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.default_album_art);
        
        Glide.with(this)
                .load(url)
                .apply(options)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Failed to load album art", e);
                        return false;
                    }
                    
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        if (resource instanceof BitmapDrawable) {
                            Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                            updateBackgroundColors(bitmap);
                        }
                        return false;
                    }
                })
                .into(ivAlbumArt);
    }
    
    /**
     * Extracts colors from album art and updates the background gradient
     * Safely handles the case when the fragment is not attached to a context
     */
    private void extractColorsFromAlbumArt(String url) {
        // First check if the fragment is attached to avoid IllegalStateException
        if (!isAdded() || getContext() == null) {
            Log.d(TAG, "Fragment not attached or context is null, skipping color extraction");
            return;
        }
        
        if (url == null || url.isEmpty()) {
            // Use default colors
            try {
            int defaultPrimary = getResources().getColor(R.color.colorPrimary);
            int defaultDark = getResources().getColor(R.color.colorPrimaryDark);
            updateBackgroundGradient(defaultPrimary, defaultDark);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to get resources for default colors: " + e.getMessage());
            }
            return;
        }
        
        try {
        AlbumArtColorExtractor.extractColorsFromUrl(url, requireContext(), new AlbumArtColorExtractor.ColorExtractionListener() {
            @Override
            public void onColorsExtracted(int primaryColor, int secondaryColor) {
                    if (getActivity() != null && !getActivity().isFinishing() && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        updateBackgroundGradient(primaryColor, secondaryColor);
                    });
                }
            }
            
            @Override
            public void onExtractionFailed() {
                // Use default colors if extraction fails
                    if (getActivity() != null && !getActivity().isFinishing() && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                            try {
                        int defaultPrimary = getResources().getColor(R.color.colorPrimary);
                        int defaultDark = getResources().getColor(R.color.colorPrimaryDark);
                        updateBackgroundGradient(defaultPrimary, defaultDark);
                            } catch (IllegalStateException e) {
                                Log.e(TAG, "Failed to get resources in extraction failure: " + e.getMessage());
                            }
                    });
                }
            }
        });
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to extract colors: " + e.getMessage());
        }
    }
    
    /**
     * Updates background with a gradient using extracted colors
     */
    private void updateBackgroundGradient(int startColor, int endColor) {
        if (getContext() == null) return;
        
        View rootView = getView();
        if (rootView == null) return;
        
        // Get the current background
        Drawable currentBg = rootView.getBackground();
        int[] currentColors = {Color.BLACK, Color.BLACK};
        
        if (currentBg instanceof GradientDrawable) {
            try {
                GradientDrawable gd = (GradientDrawable) currentBg;
                // Not directly accessible through public API, use a default
                // We'll animate from these colors to the new ones anyway
                currentColors = new int[]{getResources().getColor(R.color.colorPrimary), 
                                          getResources().getColor(R.color.colorPrimaryDark)};
            } catch (Exception e) {
                Log.e(TAG, "Error getting current gradient colors", e);
            }
        }
        
        // Don't animate if we're not visible or during initial setup
        if (!isTransitioning && isVisible()) {
            isTransitioning = true;
            
            // Cancel any existing animation
            if (colorTransitionAnimator != null && colorTransitionAnimator.isRunning()) {
                colorTransitionAnimator.cancel();
            }
            
            final int[] startColors = currentColors;
            final int[] endColors = new int[]{startColor, endColor};
            
            colorTransitionAnimator = ValueAnimator.ofFloat(0f, 1f);
            colorTransitionAnimator.setDuration(TRANSITION_DURATION);
            colorTransitionAnimator.addUpdateListener(animation -> {
                try {
                    float fraction = animation.getAnimatedFraction();
                    
                    // Interpolate between colors
                    int blendedStart = blendColors(startColors[0], endColors[0], fraction);
                    int blendedEnd = blendColors(startColors[1], endColors[1], fraction);
                    
                    // Create a new gradient
        GradientDrawable gradientDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{blendedStart, blendedEnd});
        
                    // Apply the gradient
        rootView.setBackground(gradientDrawable);
                } catch (Exception e) {
                    Log.e(TAG, "Error during color transition", e);
                }
            });
        
            colorTransitionAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    isTransitioning = false;
        updateButtonTints(startColor);
                }
            });
            
            colorTransitionAnimator.start();
        } else {
            // Immediate change without animation
            GradientDrawable gradientDrawable = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{startColor, endColor});
            rootView.setBackground(gradientDrawable);
            updateButtonTints(startColor);
        }
    }
    
    /**
     * Helper method to smoothly blend between two colors
     */
    private int blendColors(int color1, int color2, float ratio) {
        final float inverseRatio = 1f - ratio;
        
        float a = (Color.alpha(color1) * inverseRatio) + (Color.alpha(color2) * ratio);
        float r = (Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio);
        float g = (Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio);
        float b = (Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio);
        
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }
    
    /**
     * Updates button tints based on extracted colors for a cohesive UI
     */
    private void updateButtonTints(int color) {
        try {
            // Don't change play/pause, shuffle, and repeat buttons as they have specific states
            btnAddToPlaylist.setIconTint(ColorStateList.valueOf(Color.WHITE));
            btnShare.setIconTint(ColorStateList.valueOf(Color.WHITE));
            
            // Adjust text color if needed for better visibility
            int textColor = isColorDark(color) ? Color.WHITE : Color.BLACK;
            tvTrackTitle.setTextColor(textColor);
            tvArtist.setTextColor(adjustAlpha(textColor, 0.8f));
            tvAlbum.setTextColor(adjustAlpha(textColor, 0.6f));
        } catch (Exception e) {
            Log.e(TAG, "Error updating button tints: " + e.getMessage());
        }
    }
    
    /**
     * Check if a color is dark (for choosing appropriate text color)
     */
    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }
    
    /**
     * Adjust alpha component of a color
     */
    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
    
    /**
     * Update background colors directly from bitmap
     */
    private void updateBackgroundColors(Bitmap bitmap) {
        if (bitmap == null) return;
        
        // Use the AlbumArtColorExtractor to get colors from the album art
        AlbumArtColorExtractor.extractColorsFromBitmap(bitmap, new AlbumArtColorExtractor.ColorExtractionListener() {
            @Override
            public void onColorsExtracted(int primaryColor, int secondaryColor) {
                if (getActivity() != null && !getActivity().isFinishing()) {
                    requireActivity().runOnUiThread(() -> {
                        updateBackgroundGradient(primaryColor, secondaryColor);
                    });
                }
            }
            
            @Override
            public void onExtractionFailed() {
                // Use default colors if extraction fails
                if (getActivity() != null && !getActivity().isFinishing()) {
                    requireActivity().runOnUiThread(() -> {
                        int defaultPrimary = getResources().getColor(R.color.colorPrimary);
                        int defaultDark = getResources().getColor(R.color.colorPrimaryDark);
                        updateBackgroundGradient(defaultPrimary, defaultDark);
                    });
                }
            }
        });
    }
    
    /**
     * Create a fallback album art color based on the track title and artist
     * This ensures that even without album art, each track gets a consistent color
     */
    private int generateColorFromTrack(Track track) {
        if (track == null) return Color.DKGRAY;
        
        String seed = (track.getTitle() + track.getArtist()).toLowerCase();
        
        // Simple hash function to generate a consistent color
        int hash = 0;
        for (char c : seed.toCharArray()) {
            hash = hash * 31 + c;
        }
        
        // Generate a vibrant color (avoid too dark or too light)
        int red = (hash & 0xFF0000) >> 16;
        int green = (hash & 0x00FF00) >> 8;
        int blue = hash & 0x0000FF;
        
        // Ensure minimum brightness
        red = Math.max(red, 30);
        green = Math.max(green, 30);
        blue = Math.max(blue, 30);
        
        return Color.rgb(red, green, blue);
    }
    
    /**
     * Updates the playback state UI (play/pause button and album animation)
     */
    private void updatePlaybackState(boolean isPlaying) {
        this.isPlaying = isPlaying;
        
        // Update play/pause button
        updatePlayPauseButton(isPlaying);
        
        // If it's a callback to the activity, notify it of the state change
        if (nowPlayingListener != null) {
            nowPlayingListener.onPlaybackStateChanged(isPlaying);
        }
    }
    
    private void updatePlayPauseButton(boolean isPlaying) {
        if (btnPlayPause == null) return;
        
        if (isBuffering) {
            // Show buffering state
            btnPlayPause.setIconResource(R.drawable.ic_pause);
            stopAlbumArtAnimation();
        } else if (isPlaying) {
            // Show pause icon
            btnPlayPause.setIconResource(R.drawable.ic_pause);
            startAlbumArtAnimation();
        } else {
            // Show play icon
            btnPlayPause.setIconResource(R.drawable.ic_play_arrow);
            stopAlbumArtAnimation();
        }
    }
    
    /**
     * Start album art rotation animation
     */
    private void startAlbumArtAnimation() {
        if (ivAlbumArt != null && !isRotationAnimationPlaying) {
            // Initialize animation if needed
            if (albumArtRotation == null) {
                setupAlbumArtAnimation();
            }
            
            // If animation isn't already running, start it with proper fade-in
            if (albumArtRotation != null && !albumArtRotation.isRunning()) {
                // Restore any previous rotation value
                Float currentRotation = (Float) ivAlbumArt.getTag(R.id.tag_current_rotation);
                if (currentRotation != null) {
                    ivAlbumArt.setRotation(currentRotation);
                }
                
                // Start with fade-in
                albumArtRotation.start();
                isRotationAnimationPlaying = true;
                
                // Add a subtle scale effect
                if (albumArtContainer != null) {
                    albumArtContainer.animate()
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(TRANSITION_DURATION)
                        .start();
                }
            }
        }
    }
    
    /**
     * Stop album art rotation animation
     */
    private void stopAlbumArtAnimation() {
        if (albumArtRotation != null && isRotationAnimationPlaying) {
            // Save current rotation value for later restoration
            if (ivAlbumArt != null) {
                ivAlbumArt.setTag(R.id.tag_current_rotation, ivAlbumArt.getRotation());
            }
            
            // Pause rather than cancel for smoother resume
            albumArtRotation.pause();
            isRotationAnimationPlaying = false;
            
            // Reset scale effect
            if (albumArtContainer != null) {
                albumArtContainer.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(TRANSITION_DURATION)
                    .start();
            }
        }
    }
    
    /**
     * Updates the progress bar and time display
     */
    private void updateProgress() {
        if (audioPlayerHelper != null && !isUserSeeking) {
            try {
                int currentPosition = audioPlayerHelper.getCurrentPosition();
                int duration = audioPlayerHelper.getDuration();
                
                if (isAdded() && !isDetached()) {
                    requireActivity().runOnUiThread(() -> {
                        // Smoothly update progress bar with animation for big changes
                        if (duration > 0 && currentPosition <= duration) {
                            if (seekBarProgress != null) {
                                // For large jumps, use animation
                                int currentProgress = seekBarProgress.getProgress();
                                if (Math.abs(currentPosition - currentProgress) > 5000) {
                                    ValueAnimator animator = ValueAnimator.ofInt(currentProgress, currentPosition);
                                    animator.setDuration(250);
                                    animator.addUpdateListener(animation -> {
                                        int animatedValue = (int) animation.getAnimatedValue();
                                        seekBarProgress.setProgress(animatedValue);
                                        tvCurrentTime.setText(formatDuration(animatedValue));
                                    });
                                    animator.start();
                                } else {
                                    // For small updates, set directly
                            seekBarProgress.setProgress(currentPosition);
                            tvCurrentTime.setText(formatDuration(currentPosition));
                                }
                            }
                        }
                        
                        // Update buffering indicator with smooth interpolation
                        int bufferingPercent = audioPlayerHelper.getBufferingPercent();
                        if (bufferingPercent > 0 && duration > 0 && seekBarProgress != null) {
                            int bufferingPosition = (int) (duration * (bufferingPercent / 100f));
                            int currentSecondaryProgress = seekBarProgress.getSecondaryProgress();
                            
                            // Smoothly animate buffer progress
                            if (Math.abs(bufferingPosition - currentSecondaryProgress) > 5000) {
                                ValueAnimator animator = ValueAnimator.ofInt(
                                        currentSecondaryProgress, bufferingPosition);
                                animator.setDuration(250);
                                animator.addUpdateListener(animation -> {
                                    int animatedValue = (int) animation.getAnimatedValue();
                                    seekBarProgress.setSecondaryProgress(animatedValue);
                                });
                                animator.start();
                            } else {
                            seekBarProgress.setSecondaryProgress(bufferingPosition);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating progress", e);
            }
        }
    }
    
    private String formatDuration(int duration) {
        SimpleDateFormat format;
        if (duration >= 3600000) {
            format = new SimpleDateFormat("h:mm:ss", Locale.getDefault());
        } else {
            format = new SimpleDateFormat("m:ss", Locale.getDefault());
        }
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(duration));
    }
    
    /**
     * Formats milliseconds into a time string (mm:ss)
     */
    private String formatTime(long timeMs) {
        // Format time from milliseconds to mm:ss
        SimpleDateFormat sdf = new SimpleDateFormat("mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(timeMs));
    }
    
    /**
     * Toggles play/pause state
     */
    private void togglePlayPause() {
        if (audioPlayerHelper != null) {
            if (isPlaying) {
                audioPlayerHelper.pausePlayback();
            } else {
                audioPlayerHelper.resumePlayback();
            }
        }
    }
    
    /**
     * Toggles shuffle mode
     */
    private void toggleShuffle() {
        if (audioPlayerHelper != null) {
            boolean isShuffleEnabled = !audioPlayerHelper.isShuffleEnabled();
            audioPlayerHelper.setShuffleEnabled(isShuffleEnabled);
            updateShuffleButton(isShuffleEnabled);
        }
    }
    
    private void updateShuffleButton(boolean isEnabled) {
        if (isEnabled) {
            btnShuffle.setIconTint(ColorStateList.valueOf(getResources().getColor(R.color.colorAccent, null)));
        } else {
            btnShuffle.setIconTint(ColorStateList.valueOf(Color.WHITE));
        }
    }
    
    /**
     * Starts progress updates
     */
    private void startProgressUpdates() {
        if (progressHandler == null) {
            Log.d(TAG, "Creating new Handler for progress updates");
            progressHandler = new Handler(Looper.getMainLooper());
            progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isAdded() && !isUserSeeking && audioPlayerHelper != null) {
                        updateProgress();
                    }
                    if (progressHandler != null) {
                        progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL);
                    }
                }
            };
        }
        
        // Remove any existing callbacks
        if (progressHandler != null && progressRunnable != null) {
        progressHandler.removeCallbacks(progressRunnable);
        // Start new update cycle
        progressHandler.post(progressRunnable);
        }
    }
    
    /**
     * Stops progress updates
     */
    private void stopProgressUpdates() {
        // Remove all callbacks
        if (progressHandler != null && progressRunnable != null) {
        progressHandler.removeCallbacks(progressRunnable);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "NowPlayingFragment onResume called");
            
            // If there's a current track but our UI hasn't been updated yet
            if (currentTrack == null) {
            Track vmTrack = viewModel.getCurrentTrack().getValue();
            if (vmTrack != null) {
                updateUI(vmTrack);
                }
            }
            
            // Start progress updates if playing
        if (viewModel.getIsPlaying().getValue() != null && viewModel.getIsPlaying().getValue()) {
                startProgressUpdates();
                startAlbumArtAnimation();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Stop progress updates
        stopProgressUpdates();
        
        // Pause album art animation
        if (albumArtRotation != null && albumArtRotation.isRunning()) {
            albumArtRotation.pause();
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Stop any ongoing animations
        if (albumArtRotation != null) {
            albumArtRotation.cancel();
            albumArtRotation = null;
        }
        
        if (colorTransitionAnimator != null) {
            colorTransitionAnimator.cancel();
            colorTransitionAnimator = null;
        }
        
        // Stop progress updates
        stopProgressUpdates();
        
        // Remove observers
        if (viewModel != null) {
            viewModel.getCurrentTrack().removeObservers(getViewLifecycleOwner());
            viewModel.getIsPlaying().removeObservers(getViewLifecycleOwner());
            viewModel.getIsBuffering().removeObservers(getViewLifecycleOwner());
            viewModel.getCurrentPosition().removeObservers(getViewLifecycleOwner());
            viewModel.getDuration().removeObservers(getViewLifecycleOwner());
            viewModel.getBufferingPercent().removeObservers(getViewLifecycleOwner());
            viewModel.getErrorMessage().removeObservers(getViewLifecycleOwner());
            viewModel.getIsLoading().removeObservers(getViewLifecycleOwner());
        }
        
        // Clear references
        ivAlbumArt = null;
        tvNowPlaying = null;
        tvTrackTitle = null;
        tvArtist = null;
        tvAlbum = null;
        tvCurrentTime = null;
        tvTotalDuration = null;
        seekBarProgress = null;
        btnPlayPause = null;
        btnSkipNext = null;
        btnSkipPrevious = null;
        btnShuffle = null;
        btnAddToPlaylist = null;
        btnShare = null;
        btnCollapsePlayer = null;
        
        // Notify MainActivity that fragment is being destroyed
        if (nowPlayingListener != null) {
            nowPlayingListener.onNowPlayingDismissed();
        }
    }
    
    /**
     * Updates the fragment with track data
     * Called by MainActivity when track changes or when fragment is attached
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
                        
                        // Sync playback state
                        if (isPlaying) {
                            startProgressUpdates();
                            // Ensure rotation animation is running if needed
                            if (isRotationAnimationPlaying) {
                                if (albumArtRotation != null) {
                                    albumArtRotation.resume();
                                } else {
                                    setupAlbumArtAnimation();
                                    startAlbumArtAnimation();
                                }
                            }
                        } else {
                            if (albumArtRotation != null) {
                                albumArtRotation.pause();
                            }
                        }
                        
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
     * Attempts to recover from playback errors
     */
    private void attemptPlaybackRecovery() {
        if (audioPlayerHelper == null || currentTrack == null) return;
        
        // Check if we have a network connection first
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network connection for recovery attempt");
            return;
        }
        
        // Strategy 1: Try to reload the current track after a short delay
        new Handler().postDelayed(() -> {
            if (getActivity() == null || !isAdded()) return;
            
            Log.d(TAG, "Attempting playback recovery for track: " + 
                    (currentTrack != null ? currentTrack.getTitle() : "unknown"));
            
            // Try playing the track again with a new source if available
            if (currentTrack.hasAlternateSource()) {
                Log.d(TAG, "Trying alternate source for recovery");
                audioPlayerHelper.playTrackFromAlternateSource(currentTrack);
            } else {
                // Or just retry the current track
                audioPlayerHelper.playTrack(currentTrack);
            }
        }, 3000); // 3-second delay before retry
    }
    
    /**
     * Checks if network is available for recovery attempts
     */
    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager connectivityManager = 
                (android.net.ConnectivityManager) requireContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (connectivityManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.net.Network network = connectivityManager.getActiveNetwork();
                    android.net.NetworkCapabilities capabilities = 
                        connectivityManager.getNetworkCapabilities(network);
                    return capabilities != null && 
                        (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || 
                         capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR));
                } else {
                    // Legacy method
                    android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network state: " + e.getMessage());
        }
        return false;
    }
    
    private void setupAlbumArtAnimation() {
        if (ivAlbumArt == null) return;
        
        // Create a smoother rotation animation for the album art
        albumArtRotation = ObjectAnimator.ofFloat(ivAlbumArt, "rotation", 0f, 360f);
        albumArtRotation.setDuration(ALBUM_ROTATION_DURATION); 
        albumArtRotation.setInterpolator(new LinearInterpolator());
        albumArtRotation.setRepeatCount(ObjectAnimator.INFINITE);
        
        // Add a gentle scale animation to the album art container when playing
        if (albumArtContainer != null) {
            albumArtContainer.setOnClickListener(v -> {
                // Toggle play/pause when clicking on album art
                togglePlayPause();
                
                // Add haptic feedback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                
                // Add a quick scale animation for feedback
                v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> 
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start())
                    .start();
            });
        }
    }
    
    /**
     * Make a color darker by a specified factor
     * @param color The color to darken
     * @param factor The factor by which to darken (0.0-1.0)
     * @return The darkened color
     */
    private int darker(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.max(Color.red(color) - (int)(Color.red(color) * factor), 0);
        int g = Math.max(Color.green(color) - (int)(Color.green(color) * factor), 0);
        int b = Math.max(Color.blue(color) - (int)(Color.blue(color) * factor), 0);
        return Color.argb(a, r, g, b);
    }

    /**
     * Animate entry of UI elements with staggered timing
     */
    private void animateEntryElements(View rootView) {
        if (rootView == null) return;
        
        // Find all major UI elements
        View[] elements = {
            albumArtContainer,
            tvTrackTitle,
            tvArtist,
            tvAlbum,
            findViewById(R.id.controlsContainer),
            findViewById(R.id.timeContainer)
        };
        
        // Animate each element with staggered delay
        long baseDelay = 100;
        for (int i = 0; i < elements.length; i++) {
            View element = elements[i];
            if (element != null) {
                element.setAlpha(0f);
                element.setTranslationY(50f);
                element.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(baseDelay * i)
                    .setDuration(500)
                    .setInterpolator(new OvershootInterpolator(0.8f))
                    .start();
            }
        }
    }

    private View findViewById(int id) {
        return getView() != null ? getView().findViewById(id) : null;
    }

    /**
     * Translates technical error messages into user-friendly versions
     */
    private String getUserFriendlyErrorMessage(String errorMessage) {
        if (errorMessage == null) return "Unable to play track";
        
        // Convert technical errors to user-friendly messages
        if (errorMessage.contains("network") || errorMessage.contains("connection") || 
            errorMessage.contains("timeout") || errorMessage.contains("io error")) {
            return "Network error. Please check your connection and try again.";
        } else if (errorMessage.contains("format") || errorMessage.contains("codec") || 
                  errorMessage.contains("unsupported")) {
            return "This track format is not supported on your device.";
        } else if (errorMessage.contains("403") || errorMessage.contains("auth") || 
                  errorMessage.contains("permission")) {
            return "You don't have permission to play this track.";
        } else if (errorMessage.contains("404") || errorMessage.contains("not found")) {
            return "Track not found. It may have been removed from the service.";
        } else if (errorMessage.contains("interrupt") || errorMessage.contains("stopped")) {
            return "Playback was interrupted.";
        } else {
            return "There was a problem playing this track. Please try again.";
        }
    }
    
    /**
     * Minimizes the player with a smooth animation
     */
    private void minimizePlayer() {
        if (nowPlayingListener != null) {
            // Get the root view
            View rootView = getView();
            if (rootView != null) {
                // Use our new dismiss animation
                animateDismiss(rootView);
            } else {
                // Fallback if view is null
                nowPlayingListener.onMinimizeClicked();
            }
        }
    }

    private void shareTrack(Track track) {
        if (track != null) {
            String artistName = track.getArtist() != null ? track.getArtist() : "Unknown Artist";
            String albumName = track.getAlbum() != null ? track.getAlbum() : "Unknown Album";
            
            String shareText = String.format(
                "Check out \"%s\" by %s from the album \"%s\" on Banana Music!",
                track.getTitle(),
                artistName,
                albumName
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, track.getTitle());
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            
            // Create chooser with a custom title
            Intent chooser = Intent.createChooser(shareIntent, "Share \"" + track.getTitle() + "\"");
            
            try {
                startActivity(chooser);
            } catch (Exception e) {
                Log.e(TAG, "Error sharing track: " + e.getMessage());
                Toast.makeText(getContext(), "Unable to share track", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupDragToDismiss(View view) {
        // Get root view by ID
        rootView = view.findViewById(R.id.now_playing_root);
        if (rootView == null) {
            rootView = view; // Fallback if ID not found
        }
        
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        // Create a dim overlay if it doesn't exist yet
        if (dimOverlay == null && getActivity() != null) {
            dimOverlay = new View(getActivity());
            dimOverlay.setBackgroundColor(Color.BLACK);
            dimOverlay.setAlpha(0.5f); // 50% dim
            // Ensure it's behind our fragment view
            ViewGroup decorView = (ViewGroup) getActivity().getWindow().getDecorView();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            decorView.addView(dimOverlay, 0, params); // Add at index 0 (bottom)
            dimOverlay.setVisibility(View.VISIBLE);
        }
        
        // Also make the drag handle work as a button for minimizing
        View dragHandle = view.findViewById(R.id.btnMinimize);
        if (dragHandle != null) {
            dragHandle.setOnClickListener(v -> {
                if (nowPlayingListener != null) {
                    minimizePlayer();
                }
            });
        }
        
        rootView.setOnTouchListener((v, event) -> {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(event);
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialY = rootView.getTranslationY();
                    initialTouchY = event.getRawY();
                    isDragging = false;
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    // Calculate how far we've moved
                    float deltaY = event.getRawY() - initialTouchY;
                    
                    // Only allow dragging downward
                    if (deltaY < 0) deltaY = 0;
                    
                    // If we've moved enough, consider it a drag
                    if (Math.abs(deltaY) > 10 || isDragging) {
                        isDragging = true;
                        rootView.setTranslationY(initialY + deltaY);
                        
                        // Update dim overlay alpha
                        float progress = Math.min(1f, deltaY / (screenHeight * DISMISS_THRESHOLD));
                        if (dimOverlay != null) {
                            dimOverlay.setAlpha(0.5f * (1f - progress));
                        }
                        
                        return true;
                    }
                    break;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging) {
                        velocityTracker.computeCurrentVelocity(1000);
                        float yVelocity = velocityTracker.getYVelocity();
                        
                        float currentTranslation = rootView.getTranslationY();
                        float dismissThreshold = screenHeight * DISMISS_THRESHOLD;
                        
                        if (currentTranslation > dismissThreshold || yVelocity > VELOCITY_THRESHOLD) {
                            // Dismiss - animate downward
                            animateDismiss(rootView);
                        } else {
                            // Return to original position
                            animateReset(rootView);
                        }
                        
                        if (velocityTracker != null) {
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }
                        isDragging = false;
                        return true;
                    }
                    break;
            }
            
            // For click events, pass to child views
            return false;
        });
    }
    
    private void animateDismiss(View view) {
        view.animate()
            .translationY(screenHeight)
            .setDuration(200)
            .setInterpolator(new AccelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // After animation ends, notify listener to close fragment
                    if (nowPlayingListener != null) {
                        nowPlayingListener.onPlayerMinimize();
                        
                        // Reset dim overlay
                        if (dimOverlay != null) {
                            dimOverlay.setAlpha(0f);
                            dimOverlay.setVisibility(View.GONE);
                            
                            // Remove from parent
                            ViewGroup parent = (ViewGroup) dimOverlay.getParent();
                            if (parent != null) {
                                parent.removeView(dimOverlay);
                            }
                        }
                    }
                }
            })
            .start();
    }
    
    private void animateReset(View view) {
        view.animate()
            .translationY(0)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Reset dim overlay
                    if (dimOverlay != null) {
                        dimOverlay.setAlpha(0.5f);
                    }
                }
            })
            .start();
    }

    /**
     * Animate the fragment sliding up from bottom when it first appears
     */
    private void animateEntryFromBottom(View view) {
        // Make sure screen height is initialized
        if (screenHeight == 0) {
            screenHeight = getResources().getDisplayMetrics().heightPixels;
        }
        
        // Start from off-screen (below bottom)
        view.setTranslationY(screenHeight);
        
        // Animate sliding up
        view.animate()
            .translationY(0)
            .setDuration(300)
            .setInterpolator(new DecelerateInterpolator())
            .start();
        
        // Also animate the dim overlay if it exists
        if (dimOverlay != null) {
            dimOverlay.setAlpha(0f);
            dimOverlay.animate()
                .alpha(0.5f)
                .setDuration(300)
                .start();
        }
    }
} 