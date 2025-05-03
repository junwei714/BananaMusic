package my.edu.utar.bananamusic.ui.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.managers.MediaPlayerManager;
import my.edu.utar.bananamusic.utils.UserPreferences;

import android.view.GestureDetector;
import android.os.Vibrator;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.widget.Toast;
import android.view.animation.LinearInterpolator;
import android.view.HapticFeedbackConstants;
import android.media.AudioManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import androidx.fragment.app.FragmentActivity;

/**
 * A custom view that displays a Spotify-style mini player at the bottom of the screen
 * showing the currently playing track and playback controls.
 */
public class MiniPlayerView extends MaterialCardView implements AudioPlayerHelper.OnPlaybackChangedListener {

    private static final String TAG = "MiniPlayerView";
    private static final int UPDATE_INTERVAL = 100; // 0.1 second for time updates (smoother)
    private static final int SWIPE_THRESHOLD = 100; // Distance in pixels to consider a swipe
    private static final int TAP_TIMEOUT = 100; // Time in ms to consider a touch as a tap
    private static final int PROGRESS_UPDATE_INTERVAL = 40; // 25 fps for smoother visual updates
    private static final boolean DEBUG_LOGGING = true; // Control detailed logging

    // Views
    private ShapeableImageView albumArtImageView;
    private TextView trackTitleTextView;
    private TextView artistTextView;
    private MaterialButton playPauseButton;
    private MaterialButton nextButton;
    private MaterialButton likeButton;
    private MaterialButton addToPlaylistButton;
    private View loadingIndicator;
    private TextView loadingText;
    private LinearProgressIndicator progressBar;
    
    // Add time labels
    private TextView currentTimeTextView;
    private TextView totalTimeTextView;

    // Audio player
    private AudioPlayerHelper audioPlayerHelper;
    private Track currentTrack;
    private boolean isPlaying = false;
    private boolean isBuffering = false;
    private boolean isLiked = false;

    // Time update handler
    private Handler timeHandler;
    private Runnable timeRunnable;

    // Touch handling for swipe to dismiss
    private float downX;
    private float downY;
    private boolean isBeingDragged = false;
    private float originalX = 0;
    private float translationDistance = 0;

    // Gesture detector for better click handling
    private GestureDetector gestureDetector;
    private long touchDownTime = 0;
    
    // For smooth progress animation
    private ValueAnimator progressAnimator;
    private int lastProgress = 0;

    // Callbacks
    private OnMiniPlayerClickListener clickListener;

    // Add a field to track the last update time for logging purposes
    private long lastUpdateTime = 0;

    // Volume control gesture
    private float startY;
    private float volumeChangeThreshold = 10f; // dp
    private boolean isVolumeGestureActive = false;
    private int initialVolume;
    private AudioManager audioManager;
    private boolean volumeGestureEnabled = true;
    
    // Visualization
    private boolean showVisualizer = true;
    private View visualizerView;
    private ValueAnimator visualizerAnimator;
    private boolean visualizerEnabled = true;

    private MediaPlayerManager mediaPlayerManager;

    /**
     * Interface for mini player click events
     */
    public interface OnMiniPlayerClickListener {
        void onMiniPlayerClicked();
        void onPlayPauseClicked();
        void onNextClicked();
        void onPreviousClicked();
        void onDismissRequested();
        void onLikeClicked(Track track, boolean isLiked);
        void onAddToPlaylistClicked(Track track);
    }

    public MiniPlayerView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public MiniPlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MiniPlayerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_mini_player, this, true);
        
        // Initialize views
        albumArtImageView = findViewById(R.id.iv_track_art);
        trackTitleTextView = findViewById(R.id.text_track_title);
        artistTextView = findViewById(R.id.text_track_artist);
        playPauseButton = findViewById(R.id.button_play_pause);
        nextButton = findViewById(R.id.btnNext);
        likeButton = findViewById(R.id.btnLike);
        addToPlaylistButton = findViewById(R.id.btnAddToPlaylist);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        loadingText = findViewById(R.id.loadingText);
        progressBar = findViewById(R.id.progressBar);
        currentTimeTextView = findViewById(R.id.tvCurrentTime);
        totalTimeTextView = findViewById(R.id.tvTotalTime);

        // Get the root view (ConstraintLayout)
        View rootView = findViewById(R.id.mini_player);
        if (rootView == null) {
            Log.e(TAG, "Root view (mini_player) not found!");
            return;
        }

        // CRITICAL: Set up click handling
        setClickable(true);
        setFocusable(true);
        rootView.setClickable(true);
        rootView.setFocusable(true);

        // Set click listener on both the root view and this view
        OnClickListener clickListener = v -> {
            Log.d(TAG, "MiniPlayerView clicked - view id: " + v.getId());
            
            // Guard against controls clicks
            if (v == playPauseButton || v == nextButton || v == likeButton || v == addToPlaylistButton) {
                Log.d(TAG, "Ignoring click on control button");
                return;
            }
            
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            
            if (this.clickListener != null) {
                Log.d(TAG, "Calling onMiniPlayerClicked callback");
                // Don't use post() here to avoid nested UI thread calls
                this.clickListener.onMiniPlayerClicked();
            } else {
                Log.e(TAG, "Click listener is null!");
            }
        };

        rootView.setOnClickListener(clickListener);
        setOnClickListener(clickListener);

        // Set up control button click listeners
        playPauseButton.setOnClickListener(v -> {
            Log.d(TAG, "Play/Pause button clicked");
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (this.clickListener != null) {
                this.clickListener.onPlayPauseClicked();
            }
        });

        nextButton.setOnClickListener(v -> {
            Log.d(TAG, "Next button clicked");
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (this.clickListener != null) {
                this.clickListener.onNextClicked();
            }
        });

        likeButton.setOnClickListener(v -> {
            Log.d(TAG, "Like button clicked");
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentTrack != null && this.clickListener != null) {
                isLiked = !isLiked;
                updateLikeButton();
                this.clickListener.onLikeClicked(currentTrack, isLiked);
            }
        });

        addToPlaylistButton.setOnClickListener(v -> {
            Log.d(TAG, "Add to playlist button clicked");
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentTrack != null && this.clickListener != null) {
                this.clickListener.onAddToPlaylistClicked(currentTrack);
            }
        });

        // Prevent control buttons from intercepting parent's click
        playPauseButton.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        nextButton.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        likeButton.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        addToPlaylistButton.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        // Initialize other components
        audioPlayerHelper = AudioPlayerHelper.getInstance(context);
        audioPlayerHelper.setOnPlaybackChangedListener(this);

        // Set up text marquee
        trackTitleTextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        trackTitleTextView.setMarqueeRepeatLimit(-1);
        trackTitleTextView.setSingleLine(true);
        trackTitleTextView.setSelected(true);
        
        artistTextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        artistTextView.setMarqueeRepeatLimit(-1);
        artistTextView.setSingleLine(true);
        artistTextView.setSelected(true);

        // Check current playback state
        if (audioPlayerHelper.getCurrentTrack() != null) {
            currentTrack = audioPlayerHelper.getCurrentTrack();
            isPlaying = audioPlayerHelper.isPlaying();
            updatePlayPauseButton();
        }

        // Setup time update handler
        timeHandler = new Handler(Looper.getMainLooper());
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    // Direct update approach - skip all the animation logic that could be causing issues
                    if (currentTrack != null && audioPlayerHelper != null) {
                        int duration = (int) currentTrack.getDurationMs();
                        int currentPosition = audioPlayerHelper.getCurrentPosition();
                        
                        // Only calculate progress if we have valid values
                        if (duration > 0 && currentPosition >= 0) {
                            // Calculate progress and ensure it's within valid range
                            int progress = (int) ((currentPosition * 100L) / duration);
                            progress = Math.max(0, Math.min(100, progress));
                            
                            // Set progress directly without animation for more reliable updates
                            progressBar.setProgress(progress);
                            lastProgress = progress;
                            
                            // Update time labels
                            if (currentTimeTextView != null) {
                                currentTimeTextView.setText(formatTime(currentPosition));
                            }
                            if (totalTimeTextView != null) {
                                totalTimeTextView.setText(formatTime(duration));
                            }
                            
                            // Add some logging every second for debugging
                            if (System.currentTimeMillis() - lastUpdateTime > 1000) {
                                Log.d(TAG, "Progress update (direct): " + progress + "%, pos=" + 
                                      currentPosition + "ms, dur=" + duration + "ms");
                                lastUpdateTime = System.currentTimeMillis();
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating progress bar", e);
                } finally {
                    // Use a shorter interval for more responsive updates (50ms = 20 updates per second)
                    timeHandler.postDelayed(this, 50);
                }
            }
        };

        // Setup touch listener for swipe to dismiss
        setupTouchListener();
        
        // Add animation for initial display
        setAlpha(0f);
        setVisibility(GONE);
        setCardElevation(10f);

        // Setup gesture detector
        setupGestureDetector();

        mediaPlayerManager = MediaPlayerManager.getInstance();
        
        // Observe MediaPlayer state
        if (context instanceof FragmentActivity) {
            FragmentActivity activity = (FragmentActivity) context;
            mediaPlayerManager.getCurrentTrackLiveData().observe(activity, this::updateTrackInfo);
            mediaPlayerManager.getIsPlayingLiveData().observe(activity, this::updatePlayPauseButton);
        }
    }

    private void initializeClickListeners() {
        Log.d(TAG, "Initializing click listeners");

        // Set up click listener for the entire mini player to open full player
        View clickableOverlay = findViewById(R.id.clickableOverlay);
        View container = findViewById(R.id.miniPlayerContainer);

        View.OnClickListener mainClickListener = v -> {
            Log.d(TAG, "Click detected on view: " + v.getId());
            // For regular clicks, we don't need to check controls
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (clickListener != null) {
                Log.d(TAG, "Triggering onMiniPlayerClicked");
                clickListener.onMiniPlayerClicked();
            } else {
                Log.e(TAG, "Click listener is null!");
            }
        };

        // Apply click listeners to all clickable areas
        if (clickableOverlay != null) {
            Log.d(TAG, "Setting up clickable overlay");
            clickableOverlay.setClickable(true);
            clickableOverlay.setFocusable(true);
            clickableOverlay.setOnClickListener(mainClickListener);
        }

        if (container != null) {
            Log.d(TAG, "Setting up container click listener");
            container.setClickable(true);
            container.setFocusable(true);
            container.setOnClickListener(mainClickListener);
        }

        // Play/Pause button
        playPauseButton.setOnClickListener(v -> {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (clickListener != null) {
                clickListener.onPlayPauseClicked();
            }
        });

        // Next button
        nextButton.setOnClickListener(v -> {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (clickListener != null) {
                clickListener.onNextClicked();
            }
        });

        // Like button
        likeButton.setOnClickListener(v -> {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentTrack != null && clickListener != null) {
                isLiked = !isLiked;
                updateLikeButton();
                clickListener.onLikeClicked(currentTrack, isLiked);
            }
        });

        // Add to playlist button
        addToPlaylistButton.setOnClickListener(v -> {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentTrack != null && clickListener != null) {
                clickListener.onAddToPlaylistClicked(currentTrack);
            }
        });

        // Set up gesture detector for swipe and tap handling
        setupGestureDetector();

        // Make the entire view clickable
        setClickable(true);
        setFocusable(true);
        setOnClickListener(mainClickListener);
    }

    /**
     * Checks if a touch event is on the control buttons
     */
    private boolean isTouchOnControls(MotionEvent event) {
        return isPointInsideView(event.getX(), event.getY(), playPauseButton) ||
               isPointInsideView(event.getX(), event.getY(), nextButton) ||
               isPointInsideView(event.getX(), event.getY(), likeButton) ||
               (addToPlaylistButton.getVisibility() == VISIBLE && 
                isPointInsideView(event.getX(), event.getY(), addToPlaylistButton));
    }

    /**
     * Checks if a point is inside a view
     */
    private boolean isPointInsideView(float x, float y, View view) {
        if (view == null) return false;
        
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        
        int viewX = location[0];
        int viewY = location[1];
        
        // Convert coordinates to be relative to the view's parent
        float relativeX = x + getLeft();
        float relativeY = y + getTop();
        
        return (relativeX >= viewX && relativeX <= viewX + view.getWidth() &&
                relativeY >= viewY && relativeY <= viewY + view.getHeight());
    }

    private void animateButtonPress(MaterialButton button) {
        // Add enhanced press animation
        button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        
        // Scale down with faster animation
        button.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(50)
            .withEndAction(() -> {
                // Scale back up with slight bounce
                button.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        // Return to normal size
                        button.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(50)
                            .start();
                    })
                    .start();
            })
            .start();
    }
    
    private void updateLikeButton() {
        if (isLiked) {
            likeButton.setIconResource(R.drawable.ic_favorite);
            likeButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.colorSpotifyGreen)));
        } else {
            likeButton.setIconResource(R.drawable.ic_favorite_border);
            likeButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.text_secondary)));
        }
        // Add scale animation
        likeButton.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(100)
                .withEndAction(() -> 
                    likeButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
    }

    private void updatePlayPauseButton() {
        if (isPlaying) {
            playPauseButton.setIconResource(R.drawable.ic_pause);
        } else {
            playPauseButton.setIconResource(R.drawable.ic_play_arrow);
        }
        // Add scale animation
        playPauseButton.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(100)
                .withEndAction(() -> 
                    playPauseButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
    }

    private void setupTouchListener() {
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Log.d(TAG, "onSingleTapUp detected at x=" + e.getX() + ", y=" + e.getY());
                
                if (!isTouchOnControls(e)) {
                    Log.d(TAG, "Tap is not on controls - will open now playing");
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    
                    if (clickListener != null) {
                        post(() -> {
                            Log.d(TAG, "Calling onMiniPlayerClicked");
                            clickListener.onMiniPlayerClicked();
                        });
                        return true;
                    } else {
                        Log.e(TAG, "Click listener is null! Cannot open now playing");
                    }
                } else {
                    Log.d(TAG, "Tap is on controls - ignoring for now playing");
                }
                
                return false;
            }
            
            @Override
            public boolean onDown(MotionEvent e) {
                // Reset drag state on down event
                touchDownTime = System.currentTimeMillis();
                downX = e.getRawX();
                downY = e.getRawY();
                isBeingDragged = false;
                startY = e.getRawY();
                isVolumeGestureActive = false;
                if (audioManager != null) {
                    initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                }
                return false;
            }
            
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Handle double tap to play/pause
                if (clickListener != null) {
                    clickListener.onPlayPauseClicked();
                    return true;
                }
                return false;
            }
        });
        
        // Set touch listener for the entire view
        setOnTouchListener((v, event) -> {
            // Let the gesture detector handle the event
            boolean handled = gestureDetector.onTouchEvent(event);
            
            // If not handled by gesture detector and it's a tap
            if (!handled && event.getAction() == MotionEvent.ACTION_UP) {
                float deltaX = Math.abs(event.getX() - downX);
                float deltaY = Math.abs(event.getY() - downY);
                
                // If it's a tap (minimal movement)
                if (deltaX < 10 && deltaY < 10 && !isTouchOnControls(event)) {
                    Log.d(TAG, "Direct tap detected on mini player");
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (clickListener != null) {
                        post(() -> {
                            Log.d(TAG, "Calling onMiniPlayerClicked from direct tap");
                            clickListener.onMiniPlayerClicked();
                        });
                        return true;
                    }
                }
            }
            
            return handled;
        });
        
        // Make sure the view is clickable
        setClickable(true);
        setFocusable(true);
    }
    
    /**
     * Update the mini player with track information
     */
    public void updateTrackInfo(Track track) {
        if (track == null) {
            Log.e(TAG, "Cannot update MiniPlayer with null track");
            return;
        }
        
        Log.d(TAG, "Updating track info: " + track.getTitle());
        
        // Ensure we're on the main thread
        post(() -> {
            try {
                // Make sure we're visible
                setVisibility(VISIBLE);
                show();
                
                // Update current track
                currentTrack = track;
                
                // Update track title with enhanced marquee effect
                if (trackTitleTextView != null) {
                    String title = track.getTitle();
                    trackTitleTextView.setText(title != null ? title : "Unknown Title");
                    trackTitleTextView.setSelected(true);
                }
                
                // Update artist name
                if (artistTextView != null) {
                    String artist = track.getArtist();
                    artistTextView.setText(artist != null ? artist : "Unknown Artist");
                    artistTextView.setSelected(true);
                }
                
                // Update album art
                if (albumArtImageView != null) {
                    String imageUrl = track.getAlbumArtUrl();
                    if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.equals("null")) {
                        Glide.with(getContext())
                            .load(imageUrl)
                            .apply(new RequestOptions()
                                .placeholder(R.drawable.album_placeholder)
                                .error(R.drawable.album_placeholder)
                                .transform(new RoundedCorners(8)))
                            .into(albumArtImageView);
                    } else {
                        Glide.with(getContext())
                            .load(R.drawable.album_placeholder)
                            .into(albumArtImageView);
                    }
                }
                
                // Update progress bar
                if (progressBar != null) {
                    progressBar.setMax(100);
                    progressBar.setProgress(0);
                }
                
                // Update time labels
                if (currentTimeTextView != null) {
                    currentTimeTextView.setText("0:00");
                }
                
                if (totalTimeTextView != null) {
                    int duration = (int) track.getDurationMs();
                    totalTimeTextView.setText(formatTime(duration));
                }
                
                // Start time updates if playing
                if (isPlaying) {
                    startTimeUpdates();
                }
                
                Log.d(TAG, "Track info updated successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error updating track info", e);
            }
        });
    }
    
    /**
     * Check if the current track is liked by user
     */
    private void checkIfTrackIsLiked(Track track) {
        if (track != null && track.getId() != null) {
            UserPreferences userPreferences = UserPreferences.getInstance(getContext());
            isLiked = userPreferences.isTrackFavorite(track.getId());
            updateLikeButton();
        }
    }
    
    /**
     * Update the progress bar with current playback position
     */
    private void updateProgressBar() {
        if (currentTrack == null) {
            Log.d(TAG, "updateProgressBar: currentTrack is null");
            return;
        }
        
        // Check if we need to synchronize player state
        boolean actuallyPlaying = false;
        if (audioPlayerHelper != null) {
            try {
                actuallyPlaying = audioPlayerHelper.isPlaying();
                // Fix state inconsistency if needed
                if (actuallyPlaying != isPlaying) {
                    Log.d(TAG, "State inconsistency detected: UI thinks playing=" + isPlaying + 
                        " but player is actually playing=" + actuallyPlaying);
                    isPlaying = actuallyPlaying;
                    updatePlayPauseButton();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking playback state", e);
            }
        }
        
        // Get current position and duration
        int duration = (int) currentTrack.getDurationMs();
        int currentPosition = 0;
        
        if (audioPlayerHelper != null) {
            try {
                currentPosition = audioPlayerHelper.getCurrentPosition();
                // If we get 0 but we know we're playing, something is wrong
                if (currentPosition == 0 && actuallyPlaying && System.currentTimeMillis() - lastUpdateTime > 1000) {
                    Log.w(TAG, "Got position 0 while playing - possible synchronization issue");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting current position", e);
                return;
            }
        } else {
            Log.d(TAG, "audioPlayerHelper is null");
            return;
        }
        
        // Update even if not playing, to show current position
        if (duration > 0) {
            // Calculate progress percentage (protect against divide by zero)
            int progress = 0;
            if (duration > 0) {
                progress = (int) ((currentPosition * 100L) / duration);
                // Ensure progress is in valid range
                progress = Math.max(0, Math.min(100, progress));
            }
            
            // Log less frequently to avoid log spam
            if (Math.abs(progress - lastProgress) > 2 || (System.currentTimeMillis() - lastUpdateTime > 3000)) {
                Log.d(TAG, "Progress update: position=" + currentPosition + "ms, duration=" + 
                      duration + "ms, progress=" + progress + "%");
                lastUpdateTime = System.currentTimeMillis();
            }
            
            // If progress hasn't changed much, no need for animation
            if (Math.abs(progress - lastProgress) < 2) {
                progressBar.setProgress(progress);
                lastProgress = progress;
                return;
            }
            
            // Smooth animation between progress updates
            if (progressAnimator != null && progressAnimator.isRunning()) {
                progressAnimator.cancel();
            }
            
            // Set progress directly if the change is too large
            if (Math.abs(progress - lastProgress) > 20) {
                progressBar.setProgress(progress);
                lastProgress = progress;
                return;
            }
            
            progressAnimator = ValueAnimator.ofInt(lastProgress, progress);
            progressAnimator.setDuration(UPDATE_INTERVAL);
            progressAnimator.setInterpolator(new LinearInterpolator());
            progressAnimator.addUpdateListener(animation -> {
                int animatedValue = (int) animation.getAnimatedValue();
                progressBar.setProgress(animatedValue);
            });
            progressAnimator.start();
            
            lastProgress = progress;
        } else {
            Log.d(TAG, "Invalid duration or position values for progress calculation");
        }
    }
    
    /**
     * Reset the progress bar
     */
    private void resetProgress() {
        lastProgress = 0;
        if (progressBar != null) {
            progressBar.setProgress(0);
        }
    }
    
    /**
     * Update play/pause button state
     */
    public void updatePlayPauseButton(boolean isPlaying) {
        this.isPlaying = isPlaying;
        updatePlayPauseButton();
    }
    
    /**
     * Start time updates for progress bar
     */
    public void startTimeUpdates() {
        Log.d(TAG, "startTimeUpdates called, isPlaying=" + isPlaying + ", track=" + (currentTrack != null ? currentTrack.getTitle() : "null"));
        
        // Stop any existing updates first
        stopTimeUpdates();
        
        // Make sure we have valid track information
        if (currentTrack == null || audioPlayerHelper == null) {
            Log.e(TAG, "Cannot start time updates - missing track or player");
            return;
        }
        
        // Check track duration
        long duration = currentTrack.getDurationMs();
        if (duration <= 0) {
            Log.w(TAG, "Track has invalid duration: " + duration + "ms");
            
            // Try to get duration from player if available
            try {
                int playerDuration = audioPlayerHelper.getDuration();
                if (playerDuration > 0) {
                    Log.d(TAG, "Using player duration instead: " + playerDuration + "ms");
                    // Update track with actual duration
                    currentTrack.setDurationMs(playerDuration);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting player duration", e);
            }
        }
        
        // Force an immediate update before starting timer
        try {
            int currentPosition = audioPlayerHelper.getCurrentPosition();
            int calculatedProgress = 0;
            
            if (duration > 0 && currentPosition >= 0) {
                calculatedProgress = (int) ((currentPosition * 100L) / duration);
                calculatedProgress = Math.max(0, Math.min(100, calculatedProgress));
                progressBar.setProgress(calculatedProgress);
                lastProgress = calculatedProgress;
                Log.d(TAG, "Initial progress set to " + calculatedProgress + "%");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting initial progress", e);
        }
        
        // Use a more efficient update approach with ValueAnimator for smoother progress updates
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }
        
        progressAnimator = ValueAnimator.ofInt(0, 100);
        progressAnimator.setDuration(UPDATE_INTERVAL);
        progressAnimator.setInterpolator(new LinearInterpolator());
        progressAnimator.setRepeatCount(ValueAnimator.INFINITE);
        progressAnimator.addUpdateListener(animation -> {
            if (currentTrack != null && audioPlayerHelper != null) {
                int trackDuration = (int) currentTrack.getDurationMs();
                int currentPosition = audioPlayerHelper.getCurrentPosition();
                
                // Only calculate progress if we have valid values
                if (trackDuration > 0 && currentPosition >= 0) {
                    // Calculate progress percentage
                    int progress = (int) ((currentPosition * 100L) / trackDuration);
                    progress = Math.max(0, Math.min(100, progress));
                    
                    // Update progress bar
                    progressBar.setProgress(progress);
                    
                    // Update time labels
                    if (currentTimeTextView != null) {
                        currentTimeTextView.setText(formatTime(currentPosition));
                    }
                    if (totalTimeTextView != null) {
                        totalTimeTextView.setText(formatTime(trackDuration));
                    }
                }
            }
        });
        
        progressAnimator.start();
        Log.d(TAG, "Time updates started");
    }
    
    /**
     * Stop time updates
     */
    public void stopTimeUpdates() {
        Log.d(TAG, "stopTimeUpdates called");
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }
    }
    
    /**
     * Show or hide loading state
     */
    public void showLoadingState(boolean isLoading) {
        if (isLoading) {
            loadingIndicator.setVisibility(VISIBLE);
            loadingText.setVisibility(VISIBLE);
        } else {
            loadingIndicator.setVisibility(GONE);
            loadingText.setVisibility(GONE);
        }
        
        this.isBuffering = isLoading;
        updatePlayPauseButton();
    }
    
    /**
     * Format time in milliseconds to MM:SS format
     */
    private String formatTime(int timeMs) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) - 
                       TimeUnit.MINUTES.toSeconds(minutes);
        
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
    
    // Implement OnPlaybackChangedListener methods
    @Override
    public void onTrackPlay(Track track) {
        post(() -> {
            isPlaying = true;
            updatePlayPauseButton();
            if (track != null && !track.equals(currentTrack)) {
                updateTrackInfo(track);
            }
            startTimeUpdates();
        });
    }
    
    @Override
    public void onTrackPause() {
        post(() -> {
            isPlaying = false;
            updatePlayPauseButton();
        });
    }
    
    @Override
    public void onTrackStopped() {
        isPlaying = false;
        updatePlayPauseButton();
        resetProgress();
        stopTimeUpdates();
    }
    
    @Override
    public void onTrackChanged(Track track) {
        post(() -> {
            if (track != null) {
                updateTrackInfo(track);
                checkIfTrackIsLiked(track);
            }
        });
    }
    
    @Override
    public void onPlaybackError(String errorMessage) {
        Log.e(TAG, "Playback error: " + errorMessage);
        
        // Show error state
        if (playPauseButton != null) {
            playPauseButton.setIconTint(ColorStateList.valueOf(Color.RED));
            
            // Reset after delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (playPauseButton != null) {
                    playPauseButton.setIconTint(ColorStateList.valueOf(Color.WHITE));
                }
            }, 1500);
        }
        
        // Subtle vibration feedback
        try {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(80);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during vibration feedback: " + e.getMessage());
        }
    }
    
    @Override
    public void onBufferingStart() {
        isBuffering = true;
        showLoadingState(true);
        updatePlayPauseButton();
    }
    
    @Override
    public void onBufferingEnd() {
        isBuffering = false;
        showLoadingState(false);
        updatePlayPauseButton();
        
        // Start time updates if playing
        if (isPlaying) {
            startTimeUpdates();
        }
    }
    
    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        showLoadingState(isLoading);
    }
    
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        this.isPlaying = isPlaying;
        updatePlayPauseButton();
        
        // Handle visualization
        if (visualizerAnimator != null && visualizerEnabled) {
            if (isPlaying && !isBuffering) {
                visualizerAnimator.start();
            } else {
                visualizerAnimator.pause();
            }
        }
        
        // Manage time updates based on playback state
        if (isPlaying) {
            startTimeUpdates();
        } else {
            stopTimeUpdates();
        }
        
        Log.d(TAG, "Playback state changed to: " + (isPlaying ? "playing" : "not playing"));
    }
    
    @Override
    public void onBufferingUpdate(int percent) {
        // Update buffering indicator if needed
        this.isBuffering = percent < 100;
        showLoadingState(isBuffering);
        updatePlayPauseButton();
    }
    
    /**
     * Shows or hides the connect device button based on available devices
     * @param hasAvailableDevices true if there are available devices to connect to
     */
    public void updateConnectDeviceButton(boolean hasAvailableDevices) {
        if (addToPlaylistButton != null) {
            addToPlaylistButton.setVisibility(hasAvailableDevices ? VISIBLE : GONE);
        }
    }
    
    /**
     * Show the mini player with animation
     */
    public void show() {
        Log.d(TAG, "show() called");
        
        // Run on UI thread to ensure thread safety
        post(() -> {
            try {
                // Cancel any ongoing animations
                clearAnimation();
                animate().cancel();
                
                // Reset any transformations
                setTranslationY(0f);
                setTranslationX(0f);
                setScaleX(1f);
                setScaleY(1f);
                setAlpha(1f);
                
                // Make visible with animation
                if (getVisibility() != VISIBLE) {
                    setVisibility(VISIBLE);
                    setAlpha(0f);
                    setTranslationY(getHeight());
                    
                    animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            // Ensure proper z-ordering
                            if (getParent() instanceof ViewGroup) {
                                bringToFront();
                            }
                            
                            // Start time updates if playing
                            if (isPlaying) {
                                startTimeUpdates();
                            }
                            
                            // Force progress update
                            forceProgressUpdate();
                        })
                        .start();
                } else {
                    // Already visible, just ensure proper state
                    if (getParent() instanceof ViewGroup) {
                        bringToFront();
                    }
                    
                    if (isPlaying) {
                        startTimeUpdates();
                    }
                    forceProgressUpdate();
                }
                
                Log.d(TAG, "Mini player show animation started");
            } catch (Exception e) {
                Log.e(TAG, "Error showing mini player", e);
            }
        });
    }
    
    /**
     * Hide the mini player with animation
     */
    public void hide() {
        if (getVisibility() != VISIBLE || getAlpha() < 0.1f) {
            return; // Already hidden
        }
        
        // Cancel any ongoing show animations
        clearAnimation();
        
        // Stop time updates
        stopTimeUpdates();
        
        // Animate out
        animate()
            .alpha(0f)
            .translationY(getHeight())
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                setVisibility(GONE);
                // Reset transformations after hiding
                setTranslationY(0f);
            })
            .start();
        
        Log.d(TAG, "Hiding mini player");
    }

    /**
     * Force the progress bar to update regardless of playback state
     * This is useful to ensure the bar is showing the correct position
     * after a user action or when playback state changes
     */
    public void forceProgressUpdate() {
        Log.d(TAG, "forceProgressUpdate called");
        if (currentTrack == null || audioPlayerHelper == null) {
            Log.d(TAG, "Cannot force update - missing track or player");
            return;
        }
        
        try {
            // First, synchronize playing state
            boolean actuallyPlaying = audioPlayerHelper.isPlaying();
            if (isPlaying != actuallyPlaying) {
                Log.d(TAG, "Forced sync: UI playing=" + isPlaying + ", actual=" + actuallyPlaying);
                isPlaying = actuallyPlaying;
                updatePlayPauseButton();
            }
            
            // Get current position and duration
            int duration = (int) currentTrack.getDurationMs();
            int currentPosition = audioPlayerHelper.getCurrentPosition();
            
            Log.d(TAG, "Force update with position=" + currentPosition + ", duration=" + duration);
            
            if (duration > 0 && currentPosition >= 0) {
                // Calculate progress directly
                int progress = (int) ((currentPosition * 100L) / duration);
                progress = Math.max(0, Math.min(100, progress));
                
                // Update progress bar directly without animation
                progressBar.setProgress(progress);
                lastProgress = progress;
                
                Log.d(TAG, "Force updated progress to " + progress + "%");
                
                // Make sure time updates are running if playing
                if (isPlaying) {
                    stopTimeUpdates();
                    startTimeUpdates();
                }
            } else {
                Log.d(TAG, "Could not force update - invalid position (" + currentPosition + 
                      ") or duration (" + duration + ")");
                
                // Try to reset to 0 if we can't calculate progress
                progressBar.setProgress(0);
                lastProgress = 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error forcing progress update", e);
        }
    }

    private void setupVolumeGesture() {
        // Initialize AudioManager for volume control
        audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        
        // Convert dp to pixels
        final float density = getResources().getDisplayMetrics().density;
        volumeChangeThreshold = 8 * density; // 8dp threshold
    }
    
    private void setupVisualizer() {
        visualizerView = findViewById(R.id.visualizerView);
        if (visualizerView != null && visualizerEnabled) {
            visualizerAnimator = ValueAnimator.ofFloat(0f, 1f);
            visualizerAnimator.setDuration(1500);
            visualizerAnimator.setRepeatCount(ValueAnimator.INFINITE);
            visualizerAnimator.setRepeatMode(ValueAnimator.REVERSE);
            visualizerAnimator.setInterpolator(new DecelerateInterpolator());
            visualizerAnimator.addUpdateListener(animation -> {
                if (isPlaying && !isBuffering && visualizerView != null) {
                    float value = (float) animation.getAnimatedValue();
                    visualizerView.setScaleY(0.5f + (value * 0.5f));
                }
            });
        }
    }

    // Add methods for enabling/disabling features
    public void setVolumeGestureEnabled(boolean enabled) {
        this.volumeGestureEnabled = enabled;
    }
    
    public void setVisualizerEnabled(boolean enabled) {
        this.visualizerEnabled = enabled;
        if (visualizerView != null) {
            visualizerView.setVisibility(enabled ? VISIBLE : GONE);
        }
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Log.d(TAG, "onSingleTapUp detected");
                
                // Only handle tap if it's not on the control buttons
                if (!isTouchOnControls(e)) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (clickListener != null) {
                        Log.d(TAG, "Triggering onMiniPlayerClicked");
                        post(() -> clickListener.onMiniPlayerClicked());
                        return true;
                    } else {
                        Log.e(TAG, "Click listener is null!");
                    }
                } else {
                    Log.d(TAG, "Tap was on controls - ignoring for now playing");
                }
                return false;
            }
        });
    }

    private void performHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } else {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG, "onTouchEvent: " + event.getAction());
        if (event.getAction() == MotionEvent.ACTION_UP) {
            Log.d(TAG, "Touch UP detected on MiniPlayerView");
            if (clickListener != null) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                // Don't use post() to avoid nested UI thread calls
                clickListener.onMiniPlayerClicked();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        
        Log.d(TAG, "onFinishInflate called");
        
        // Find all clickable areas
        View clickableOverlay = findViewById(R.id.clickableOverlay);
        View container = findViewById(R.id.miniPlayerContainer);
        
        // Set up click listeners with proper logging
        View.OnClickListener mainClickListener = v -> {
            Log.d(TAG, "Click detected on view: " + v.getId());
            if (clickListener != null) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                post(() -> {
                    Log.d(TAG, "Executing onMiniPlayerClicked");
                    clickListener.onMiniPlayerClicked();
                });
            } else {
                Log.e(TAG, "Click listener is null when mini player clicked");
            }
        };
        
        // Apply click listener to all clickable areas
        if (clickableOverlay != null) {
            Log.d(TAG, "Setting up clickable overlay");
            clickableOverlay.setOnClickListener(mainClickListener);
        }
        
        if (container != null) {
            Log.d(TAG, "Setting up container click listener");
            container.setOnClickListener(mainClickListener);
        }
        
        // Set click listener on the view itself as a fallback
        Log.d(TAG, "Setting up fallback click listener");
        setOnClickListener(mainClickListener);
        
        // Ensure the view is clickable
        setClickable(true);
        setFocusable(true);
    }

    /**
     * Set the click listener for the mini player
     */
    public void setOnMiniPlayerClickListener(OnMiniPlayerClickListener listener) {
        Log.d(TAG, "Setting click listener: " + (listener != null ? "non-null" : "null"));
        this.clickListener = listener;
        
        // Re-initialize click listeners when the callback is set
        initializeClickListeners();
    }

    public void updateProgress(int progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
    }

    @Override
    public void onTrackComplete() {
        // Update the mini player UI when track completes
        post(() -> {
            updatePlayPauseButton(false);
            // Reset progress or other UI elements if needed
            if (progressBar != null) {
                progressBar.setProgress(0);
            }
        });
    }

    /**
     * Update like button state for external changes
     */
    public void updateLikeButtonState(boolean liked) {
        isLiked = liked;
        updateLikeButton();
    }
}

