package my.edu.utar.bananamusic.ml;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.res.AssetFileDescriptor;
import java.nio.channels.FileChannel;

/**
 * Voice-based emotion detection using TensorFlow Lite for Phase 3 implementation
 */
public class VoiceEmotionDetector {
    private static final String TAG = "VoiceEmotionDetector";
    
    // TensorFlow Lite model filename
    private static final String MODEL_FILENAME = "voice_emotion_model.tflite";
    
    // Audio recording configuration
    private static final int SAMPLE_RATE = 16000; // 16kHz sampling rate
    private static final int RECORDING_DURATION_MS = 3000; // 3 seconds recording
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;
    
    // Feature extraction parameters
    private static final int FFT_SIZE = 512;
    private static final int MEL_BANDS = 40;
    private static final int WINDOW_SIZE_MS = 25; // 25ms per frame
    private static final int WINDOW_STEP_MS = 10; // 10ms step between frames
    
    // Emotions the model can detect
    private static final String[] EMOTION_LABELS = {
        "neutral", "happy", "sad", "angry", "fearful", "disgusted", "surprised"
    };
    
    // Singleton instance
    private static VoiceEmotionDetector instance;
    
    // Dependencies
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // TensorFlow Lite
    private Interpreter tfliteInterpreter;
    private boolean modelInitialized = false;
    
    // Audio recording
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private File recordingFile;
    
    /**
     * Private constructor - use getInstance()
     */
    private VoiceEmotionDetector(Context context) {
        this.context = context.getApplicationContext();
        initializeTensorFlow();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized VoiceEmotionDetector getInstance(Context context) {
        if (instance == null) {
            instance = new VoiceEmotionDetector(context);
        }
        return instance;
    }
    
    /**
     * Initialize TensorFlow Lite model for voice emotion detection
     */
    private void initializeTensorFlow() {
        executor.execute(() -> {
            try {
                // Load model
                MappedByteBuffer modelBuffer = Utils.loadModelFile(context, MODEL_FILENAME);
                
                if (modelBuffer != null) {
                    Interpreter.Options options = new Interpreter.Options();
                    options.setNumThreads(2);
                    tfliteInterpreter = new Interpreter(modelBuffer, options);
                    modelInitialized = true;
                    Log.d(TAG, "Voice emotion model initialized successfully");
                } else {
                    Log.e(TAG, "Failed to load voice emotion model");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing voice emotion model: " + e.getMessage());
            }
        });
    }
    
    /**
     * Start recording audio for emotion detection
     * @param callback Callback for recording completion
     */
    public void startRecording(final RecordingCallback callback) {
        if (isRecording) {
            mainHandler.post(() -> callback.onError("Already recording"));
            return;
        }
        
        executor.execute(() -> {
            try {
                // Create a temporary file for the recording
                recordingFile = File.createTempFile("voice_emotion_", ".pcm", context.getCacheDir());
                
                // Initialize AudioRecord
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE);
                
                // Start recording
                isRecording = true;
                audioRecord.startRecording();
                mainHandler.post(callback::onRecordingStarted);
                
                // Calculate buffer size for the entire recording
                int totalSamples = (SAMPLE_RATE * RECORDING_DURATION_MS) / 1000;
                short[] audioBuffer = new short[totalSamples];
                
                // Read audio data
                int readSamples = 0;
                int remainingSamples = totalSamples;
                
                while (isRecording && remainingSamples > 0) {
                    int samplesRead = audioRecord.read(audioBuffer, readSamples, 
                            Math.min(BUFFER_SIZE / 2, remainingSamples));
                    
                    if (samplesRead > 0) {
                        readSamples += samplesRead;
                        remainingSamples -= samplesRead;
                    }
                    
                    // Check if we've recorded enough
                    if (readSamples >= totalSamples) {
                        break;
                    }
                }
                
                // Stop recording
                if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
                
                isRecording = false;
                
                // Save recording to file
                saveRecording(audioBuffer, readSamples);
                
                // Analyze immediately if model is initialized
                if (modelInitialized) {
                    Map<String, Float> emotions = analyzeVoiceEmotion(audioBuffer, readSamples);
                    mainHandler.post(() -> callback.onRecordingFinished(recordingFile, emotions));
                } else {
                    mainHandler.post(() -> callback.onRecordingFinished(recordingFile, null));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error recording audio: " + e.getMessage());
                isRecording = false;
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
                mainHandler.post(() -> callback.onError("Failed to record: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Stop recording early (before duration is complete)
     */
    public void stopRecording() {
        isRecording = false;
    }
    
    /**
     * Save recording to a file
     */
    private void saveRecording(short[] buffer, int samples) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(recordingFile)) {
            // Convert shorts to bytes
            ByteBuffer byteBuffer = ByteBuffer.allocate(samples * 2);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            
            ShortBuffer shortBuffer = byteBuffer.asShortBuffer();
            shortBuffer.put(buffer, 0, samples);
            
            fos.write(byteBuffer.array());
            fos.flush();
        }
    }
    
    /**
     * Analyze recorded audio file for emotions
     * @param recordingFile The audio file to analyze
     * @param callback Callback for analysis results
     */
    public void analyzeRecording(File recordingFile, EmotionCallback callback) {
        if (!modelInitialized) {
            mainHandler.post(() -> callback.onError("Voice emotion model not initialized"));
            return;
        }
        
        executor.execute(() -> {
            try {
                // Load audio data from file
                short[] audioData = loadAudioFile(recordingFile);
                
                // Analyze emotions
                Map<String, Float> emotions = analyzeVoiceEmotion(audioData, audioData.length);
                
                // Return results
                mainHandler.post(() -> callback.onEmotionDetected(emotions));
            } catch (Exception e) {
                Log.e(TAG, "Error analyzing voice: " + e.getMessage());
                mainHandler.post(() -> callback.onError("Failed to analyze: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Load audio data from file
     */
    private short[] loadAudioFile(File file) throws IOException {
        int fileSize = (int) file.length();
        int sampleCount = fileSize / 2; // 16-bit samples = 2 bytes per sample
        
        ByteBuffer buffer = ByteBuffer.allocate(fileSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        byte[] fileBytes = new byte[fileSize];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead = fis.getChannel().read(buffer);
            if (bytesRead != fileSize) {
                throw new IOException("Failed to read complete file");
            }
        }
        
        buffer.rewind();
        ShortBuffer shortBuffer = buffer.asShortBuffer();
        short[] audioData = new short[sampleCount];
        shortBuffer.get(audioData);
        
        return audioData;
    }
    
    /**
     * Analyze voice emotion from audio data
     * @param audioData Audio samples
     * @param samples Number of valid samples in the buffer
     * @return Map of emotions to confidence scores
     */
    private Map<String, Float> analyzeVoiceEmotion(short[] audioData, int samples) {
        // Extract audio features
        float[][] features = extractAudioFeatures(audioData, samples);
        
        // Prepare result map
        Map<String, Float> result = new HashMap<>();
        
        if (features == null) {
            // Use default values if feature extraction failed
            for (String emotion : EMOTION_LABELS) {
                result.put(emotion, 0.0f);
            }
            result.put("neutral", 1.0f); // Default to neutral
            return result;
        }
        
        // Prepare input and output tensors
        float[][][] input = new float[1][][];
        input[0] = features;
        
        float[][] output = new float[1][EMOTION_LABELS.length];
        
        // Run inference
        try {
            tfliteInterpreter.run(input, output);
            
            // Convert output to emotion map
            for (int i = 0; i < EMOTION_LABELS.length; i++) {
                result.put(EMOTION_LABELS[i], output[0][i]);
            }
            
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error running voice emotion inference: " + e.getMessage());
            
            // Return default values on error
            for (String emotion : EMOTION_LABELS) {
                result.put(emotion, 0.0f);
            }
            result.put("neutral", 1.0f); // Default to neutral
            return result;
        }
    }
    
    /**
     * Extract audio features (Mel spectrogram) from raw audio
     * This is a placeholder implementation - actual feature extraction would be more complex
     */
    private float[][] extractAudioFeatures(short[] audioData, int samples) {
        try {
            // Convert audio to float
            float[] floatAudio = new float[samples];
            for (int i = 0; i < samples; i++) {
                floatAudio[i] = audioData[i] / 32768.0f; // Normalize to [-1, 1]
            }
            
            // Calculate frame count
            int windowSize = (SAMPLE_RATE * WINDOW_SIZE_MS) / 1000;
            int windowStep = (SAMPLE_RATE * WINDOW_STEP_MS) / 1000;
            int frameCount = 1 + (samples - windowSize) / windowStep;
            
            if (frameCount <= 0) {
                Log.e(TAG, "Not enough audio data for feature extraction");
                return null;
            }
            
            // Placeholder for mel spectrogram features
            // In a real implementation, this would compute FFT and mel filtering
            float[][] features = new float[frameCount][MEL_BANDS];
            
            // Placeholder implementation - would be replaced with actual DSP
            for (int frame = 0; frame < frameCount; frame++) {
                int startSample = frame * windowStep;
                
                // Calculate energy in different frequency bands (simplified)
                for (int band = 0; band < MEL_BANDS; band++) {
                    float sum = 0;
                    for (int i = 0; i < windowSize; i++) {
                        if (startSample + i < samples) {
                            sum += Math.abs(floatAudio[startSample + i]);
                        }
                    }
                    features[frame][band] = sum / windowSize;
                }
            }
            
            return features;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting audio features: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Callback for recording progress
     */
    public interface RecordingCallback {
        void onRecordingStarted();
        void onRecordingFinished(File recordingFile, Map<String, Float> emotions);
        void onError(String message);
    }
    
    /**
     * Callback for emotion detection
     */
    public interface EmotionCallback {
        void onEmotionDetected(Map<String, Float> emotions);
        void onError(String message);
    }
    
    /**
     * Clean up resources
     */
    public void close() {
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
            tfliteInterpreter = null;
        }
        
        isRecording = false;
        modelInitialized = false;
    }
    
    /**
     * Utility class for loading the model
     */
    private static class Utils {
        static MappedByteBuffer loadModelFile(Context context, String modelFilename) {
            try {
                // Direct implementation instead of using private TensorFlowHelper method
                AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFilename);
                FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
                FileChannel fileChannel = inputStream.getChannel();
                long startOffset = fileDescriptor.getStartOffset();
                long declaredLength = fileDescriptor.getDeclaredLength();
                
                MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
                inputStream.close();
                fileDescriptor.close();
                return buffer;
            } catch (Exception e) {
                Log.e(TAG, "Error loading model file: " + e.getMessage());
                return null;
            }
        }
    }
} 