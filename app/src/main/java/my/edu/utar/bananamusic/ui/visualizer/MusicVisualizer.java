package my.edu.utar.bananamusic.ui.visualizer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Random;

import my.edu.utar.bananamusic.R;

/**
 * A custom view that displays audio visualization effects
 */
public class MusicVisualizer extends View {

    private static final String TAG = "MusicVisualizer";
    private static final int DEFAULT_NUM_BARS = 4;
    private static final int DEFAULT_BAR_WIDTH = 10;
    private static final int DEFAULT_BAR_GAP = 5;
    private static final int DEFAULT_ANIMATION_TIME = 150;
    private static final int MIN_BAR_HEIGHT = 5;
    
    private int numBars;
    private int barWidth;
    private int barGap;
    private int animationTime;
    private int color;
    private boolean isActive = false;
    private boolean randomize = true;
    
    // Paint for drawing
    private Paint barPaint;
    
    // Animation values
    private int[] barHeights;
    private int[] targetBarHeights;
    private long lastUpdateTime;
    private Handler handler = new Handler();
    private Random random = new Random();
    
    // Visualizer for getting actual audio data
    private Visualizer visualizer;
    private int audioSessionId = -1;
    
    public MusicVisualizer(Context context) {
        this(context, null);
    }
    
    public MusicVisualizer(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public MusicVisualizer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        // Get attributes from XML
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs, R.styleable.MusicVisualizer, 0, 0);
            
            try {
                numBars = a.getInteger(R.styleable.MusicVisualizer_numBars, DEFAULT_NUM_BARS);
                barWidth = a.getDimensionPixelSize(R.styleable.MusicVisualizer_barWidth, DEFAULT_BAR_WIDTH);
                barGap = a.getDimensionPixelSize(R.styleable.MusicVisualizer_barGap, DEFAULT_BAR_GAP);
                animationTime = a.getInteger(R.styleable.MusicVisualizer_animationTime, DEFAULT_ANIMATION_TIME);
                color = a.getColor(R.styleable.MusicVisualizer_barColor, 
                        ContextCompat.getColor(context, R.color.colorAccent));
            } finally {
                a.recycle();
            }
        } else {
            // Default values
            numBars = DEFAULT_NUM_BARS;
            barWidth = DEFAULT_BAR_WIDTH;
            barGap = DEFAULT_BAR_GAP;
            animationTime = DEFAULT_ANIMATION_TIME;
            color = ContextCompat.getColor(context, R.color.colorAccent);
        }
        
        // Set up paint for drawing
        barPaint = new Paint();
        barPaint.setColor(color);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setAntiAlias(true);
        
        // Initialize bar heights
        barHeights = new int[numBars];
        targetBarHeights = new int[numBars];
        
        // Start with all bars at minimum height
        for (int i = 0; i < numBars; i++) {
            barHeights[i] = MIN_BAR_HEIGHT;
            targetBarHeights[i] = MIN_BAR_HEIGHT;
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        if (width == 0 || height == 0) {
            return;
        }
        
        // Calculate total width needed for bars and gaps
        int totalBarWidth = numBars * barWidth;
        int totalGapWidth = (numBars - 1) * barGap;
        int startX = (width - (totalBarWidth + totalGapWidth)) / 2;
        
        // Update animation progress
        updateBarHeights();
        
        // Draw each bar
        for (int i = 0; i < numBars; i++) {
            int left = startX + i * (barWidth + barGap);
            int barHeight = barHeights[i];
            
            // Center bars vertically
            int top = (height - barHeight) / 2;
            int bottom = top + barHeight;
            
            // Draw the bar
            canvas.drawRect(left, top, left + barWidth, bottom, barPaint);
        }
        
        // If active, schedule the next frame
        if (isActive) {
            generateNextFrame();
            invalidate();
        }
    }
    
    /**
     * Update bar heights based on animation time and targets
     */
    private void updateBarHeights() {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime == 0) {
            lastUpdateTime = currentTime;
            return;
        }
        
        float deltaTime = (currentTime - lastUpdateTime) / (float) animationTime;
        lastUpdateTime = currentTime;
        
        for (int i = 0; i < numBars; i++) {
            int diff = targetBarHeights[i] - barHeights[i];
            int change = (int) (diff * deltaTime);
            
            if (Math.abs(change) < 1) {
                change = diff > 0 ? 1 : diff < 0 ? -1 : 0;
            }
            
            barHeights[i] += change;
            
            // If we've reached the target, generate a new one
            if (Math.abs(targetBarHeights[i] - barHeights[i]) <= 1) {
                barHeights[i] = targetBarHeights[i];
                if (randomize) {
                    targetBarHeights[i] = getRandomBarHeight();
                }
            }
        }
    }
    
    /**
     * Generate random heights for next animation frame
     */
    private void generateNextFrame() {
        // Only use random if we're not using actual audio data
        if (visualizer == null && randomize) {
            for (int i = 0; i < numBars; i++) {
                if (barHeights[i] == targetBarHeights[i]) {
                    targetBarHeights[i] = getRandomBarHeight();
                }
            }
        }
    }
    
    /**
     * Get a random bar height within view bounds
     */
    private int getRandomBarHeight() {
        int minHeight = MIN_BAR_HEIGHT;
        int maxHeight = getHeight() > 0 ? getHeight() - 10 : 100;
        return minHeight + random.nextInt(maxHeight - minHeight);
    }
    
    /**
     * Set the active state of the visualizer
     */
    public void setActive(boolean active) {
        if (this.isActive != active) {
            this.isActive = active;
            
            if (active) {
                lastUpdateTime = System.currentTimeMillis();
                invalidate();
            }
        }
    }
    
    /**
     * Set the color of the visualizer bars
     */
    public void setColor(int color) {
        this.color = color;
        barPaint.setColor(color);
        invalidate();
    }
    
    /**
     * Link the visualizer to a MediaPlayer
     */
    public void linkToMediaPlayer(MediaPlayer mediaPlayer) {
        if (mediaPlayer == null) {
            releaseVisualizer();
            return;
        }
        
        int newSessionId = mediaPlayer.getAudioSessionId();
        if (audioSessionId != newSessionId) {
            releaseVisualizer();
            audioSessionId = newSessionId;
            setupVisualizer(newSessionId);
        }
    }
    
    /**
     * Setup the visualizer for audio session
     */
    private void setupVisualizer(int sessionId) {
        // Release the old one first
        releaseVisualizer();
        
        try {
            visualizer = new Visualizer(sessionId);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[0]);
            
            visualizer.setDataCaptureListener(
                new Visualizer.OnDataCaptureListener() {
                    @Override
                    public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                        updateVisualizerData(waveform);
                    }
                    
                    @Override
                    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                        // Not using FFT data
                    }
                },
                Visualizer.getMaxCaptureRate() / 2, true, false);
            
            visualizer.setEnabled(true);
            randomize = false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up visualizer: " + e.getMessage());
            // Fall back to random visualization
            randomize = true;
        }
    }
    
    /**
     * Update visualization based on audio data
     */
    private void updateVisualizerData(byte[] waveform) {
        if (waveform == null || waveform.length == 0) return;
        
        int height = getHeight();
        if (height == 0) return;
        
        // Sample the waveform for each bar
        for (int i = 0; i < numBars; i++) {
            int sampleIndex = (waveform.length / numBars) * i;
            
            if (sampleIndex < waveform.length) {
                // Convert byte (-128 to 127) to height
                byte sample = waveform[sampleIndex];
                int amplitude = Math.abs(sample);
                
                // Scale to the view height (0.8 to leave some margin)
                targetBarHeights[i] = Math.max(MIN_BAR_HEIGHT, 
                        (int) (amplitude / 127.0 * height * 0.8));
            }
        }
        
        // Make sure the change is visible
        handler.post(this::invalidate);
    }
    
    /**
     * Release visualizer resources
     */
    public void releaseVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing visualizer: " + e.getMessage());
            }
            visualizer = null;
        }
        
        // Reset to random mode
        randomize = true;
        audioSessionId = -1;
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseVisualizer();
    }
} 