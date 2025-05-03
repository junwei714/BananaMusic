package my.edu.utar.bananamusic.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Set;

import my.edu.utar.bananamusic.BuildConfig;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.auth.LoginActivity;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.FirebaseAuthHelper;
import my.edu.utar.bananamusic.utils.MusicCache;
import my.edu.utar.bananamusic.utils.TrackDownloadManager;
import my.edu.utar.bananamusic.utils.UserPreferences;

public class SettingsFragment extends Fragment {
    
    private static final String TAG = "SettingsFragment";
    
    // Settings UI components
    private RadioGroup rgAudioQuality;
    private RadioButton rbLowQuality, rbNormalQuality, rbHighQuality;
    private SeekBar sbCrossfade;
    private TextView tvCrossfadeValue;
    private SwitchCompat switchOfflineMode;
    private SwitchCompat switchWifiOnly;
    private SwitchCompat switchDarkMode;
    private View manageDownloadsContainer;
    private View clearDownloadsContainer;
    private TextView tvDownloadedTracks;
    private View logoutContainer;
    private TextView tvVersion;
    
    // Utilities
    private UserPreferences preferences;
    private MusicCache musicCache;
    private TrackDownloadManager downloadManager;
    private FirebaseAuthHelper authHelper;
    private AudioPlayerHelper audioPlayerHelper;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        // Initialize utilities
        preferences = UserPreferences.getInstance(requireContext());
        musicCache = MusicCache.getInstance(requireContext());
        downloadManager = TrackDownloadManager.getInstance(requireContext());
        authHelper = FirebaseAuthHelper.getInstance();
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        
        // Initialize UI components
        initViews(view);
        
        // Load settings from preferences
        loadSettings();
        
        // Setup listeners
        setupListeners();
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Update download info each time fragment is resumed
        updateDownloadInfo();
    }
    
    /**
     * Initialize all UI components
     */
    private void initViews(View view) {
        // Audio quality
        rgAudioQuality = view.findViewById(R.id.rg_audio_quality);
        rbLowQuality = view.findViewById(R.id.rb_low_quality);
        rbNormalQuality = view.findViewById(R.id.rb_normal_quality);
        rbHighQuality = view.findViewById(R.id.rb_high_quality);
        
        // Crossfade
        sbCrossfade = view.findViewById(R.id.sb_crossfade);
        tvCrossfadeValue = view.findViewById(R.id.tv_crossfade_value);
        
        // Offline mode
        switchOfflineMode = view.findViewById(R.id.switch_offline_mode);
        switchWifiOnly = view.findViewById(R.id.switch_wifi_only);
        manageDownloadsContainer = view.findViewById(R.id.manage_downloads_container);
        clearDownloadsContainer = view.findViewById(R.id.clear_downloads_container);
        tvDownloadedTracks = view.findViewById(R.id.tv_downloaded_tracks);
        
        // Appearance
        switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        
        // Account
        logoutContainer = view.findViewById(R.id.logout_container);
        
        // App version
        tvVersion = view.findViewById(R.id.tv_version);
        tvVersion.setText(String.format(Locale.US, "Version %s", BuildConfig.VERSION_NAME));
    }
    
    /**
     * Load saved settings from preferences
     */
    private void loadSettings() {
        // Audio quality
        int audioQuality = preferences.getAudioQuality();
        switch (audioQuality) {
            case 0:
                rbLowQuality.setChecked(true);
                break;
            case 1:
                rbNormalQuality.setChecked(true);
                break;
            case 2:
                rbHighQuality.setChecked(true);
                break;
        }
        
        // Crossfade
        int crossfadeDuration = preferences.getCrossfadeDuration();
        sbCrossfade.setProgress(crossfadeDuration);
        updateCrossfadeValueText(crossfadeDuration);
        
        // Offline mode
        switchOfflineMode.setChecked(preferences.isOfflineModeEnabled());
        switchWifiOnly.setChecked(preferences.getCacheOnlyOnWifi());
        
        // Update download info
        updateDownloadInfo();
        
        // Dark mode
        switchDarkMode.setChecked(preferences.isDarkModeEnabled());
    }
    
    /**
     * Setup all interaction listeners
     */
    private void setupListeners() {
        // Audio quality
        rgAudioQuality.setOnCheckedChangeListener((group, checkedId) -> {
            int audioQuality;
            if (checkedId == R.id.rb_low_quality) {
                audioQuality = 0;
            } else if (checkedId == R.id.rb_high_quality) {
                audioQuality = 2;
            } else {
                audioQuality = 1;
            }
            preferences.setAudioQuality(audioQuality);
        });
        
        // Crossfade
        sbCrossfade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateCrossfadeValueText(progress);
                if (fromUser) {
                    preferences.setCrossfadeDuration(progress);
                    audioPlayerHelper.setCrossfadeDuration(progress);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }
        });
        
        // Offline mode
        switchOfflineMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setOfflineMode(isChecked);
            if (isChecked) {
                Set<String> cachedTracks = preferences.getCachedTracks();
                if (cachedTracks.isEmpty()) {
                    showNoDownloadsDialog();
                    // Don't turn on offline mode if there are no downloads
                    buttonView.setChecked(false);
                    preferences.setOfflineMode(false);
                } else {
                    Snackbar.make(buttonView, "Offline mode enabled", Snackbar.LENGTH_SHORT).show();
                }
            } else {
                Snackbar.make(buttonView, "Offline mode disabled", Snackbar.LENGTH_SHORT).show();
            }
        });
        
        // Download on WiFi only
        switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setCacheOnlyOnWifi(isChecked);
            if (isChecked) {
                Snackbar.make(buttonView, "Downloads will only happen on Wi-Fi", Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(buttonView, "Downloads can use mobile data", Snackbar.LENGTH_SHORT).show();
            }
        });
        
        // Manage downloads
        manageDownloadsContainer.setOnClickListener(v -> {
            // Open downloads activity
            openDownloadsManager();
        });
        
        // Clear downloads
        clearDownloadsContainer.setOnClickListener(v -> {
            // Show confirmation dialog
            showClearDownloadsDialog();
        });
        
        // Dark mode
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setDarkMode(isChecked);
            Snackbar.make(buttonView, "App restart required to apply theme changes", Snackbar.LENGTH_LONG).show();
        });
        
        // Logout
        logoutContainer.setOnClickListener(v -> {
            showLogoutConfirmationDialog();
        });
    }
    
    /**
     * Update crossfade value text based on progress
     */
    private void updateCrossfadeValueText(int progress) {
        if (progress == 0) {
            tvCrossfadeValue.setText("Off");
        } else {
            tvCrossfadeValue.setText(String.format(Locale.US, "%ds", progress));
        }
    }
    
    /**
     * Update the download info text with current cache stats
     */
    private void updateDownloadInfo() {
        // Calculate total size of downloads
        long totalSize = 0;
        int trackCount = 0;
        
        File cacheDir = new File(requireContext().getCacheDir(), "music_cache");
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".mp3")) {
                        totalSize += file.length();
                        trackCount++;
                    }
                }
            }
        }
        
        // Format size as MB
        double sizeMB = totalSize / (1024.0 * 1024.0);
        DecimalFormat df = new DecimalFormat("#.##");
        
        // Update text
        tvDownloadedTracks.setText(String.format(Locale.US, "%d tracks downloaded (%s MB)", 
                trackCount, df.format(sizeMB)));
    }
    
    /**
     * Open the downloads manager screen
     */
    private void openDownloadsManager() {
        // TODO: Create a DownloadsActivity to manage downloaded tracks
        Toast.makeText(requireContext(), "Coming soon: Downloads manager", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Show dialog to confirm clearing all downloads
     */
    private void showClearDownloadsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear Downloads")
                .setMessage("Are you sure you want to delete all downloaded music? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Clear cache
                    musicCache.clearCache();
                    
                    // Update UI
                    updateDownloadInfo();
                    
                    // Show confirmation
                    Snackbar.make(requireView(), "All downloads cleared", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Show dialog when user tries to enable offline mode with no downloads
     */
    private void showNoDownloadsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("No Downloads Available")
                .setMessage("You don't have any music downloaded. Offline mode requires downloaded tracks to play music.")
                .setPositiveButton("Download Music", (dialog, which) -> {
                    // Open downloads manager
                    openDownloadsManager();
                })
                .setNegativeButton("OK", null)
                .show();
    }
    
    /**
     * Show confirmation dialog for logout
     */
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    // Log out user
                    authHelper.signOut();
                    
                    // Navigate to login screen
                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}