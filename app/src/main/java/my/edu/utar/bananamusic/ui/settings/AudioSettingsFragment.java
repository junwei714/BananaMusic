package my.edu.utar.bananamusic.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.MusicCache;

/**
 * Fragment for audio player settings
 */
public class AudioSettingsFragment extends PreferenceFragmentCompat implements 
        SharedPreferences.OnSharedPreferenceChangeListener {

    private AudioPlayerHelper audioPlayerHelper;
    private MusicCache musicCache;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.audio_preferences, rootKey);
        
        // Initialize audio components
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        musicCache = MusicCache.getInstance(requireContext());
        
        // Set up dependencies and summaries
        setupPreferenceDependencies();
        updateAllSummaries();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Add click listener for cache size preference
        Preference cacheSizePreference = findPreference("cache_size_mb");
        if (cacheSizePreference != null) {
            cacheSizePreference.setOnPreferenceClickListener(preference -> {
                updateCacheUsageSummary();
                return false; // Allow normal click handling to continue
            });
        }
        
        // Add click listener for cache clear button
        Preference clearCachePreference = findPreference("clear_cache");
        if (clearCachePreference != null) {
            clearCachePreference.setOnPreferenceClickListener(preference -> {
                clearCache();
                return true;
            });
        }
    }
    
    private void clearCache() {
        if (musicCache != null) {
            musicCache.clearCache();
            Toast.makeText(requireContext(), R.string.cache_cleared, Toast.LENGTH_SHORT).show();
            updateCacheUsageSummary();
        }
    }
    
    private void updateCacheUsageSummary() {
        Preference cacheSizePreference = findPreference("cache_size_mb");
        if (cacheSizePreference != null && musicCache != null) {
            long currentUsage = musicCache.getCurrentCacheSize() / (1024 * 1024); // Convert to MB
            cacheSizePreference.setSummary(getString(R.string.cache_size_summary, 
                    currentUsage, 
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .getInt("cache_size_mb", 500)));
        }
    }
    
    private void setupPreferenceDependencies() {
        // Crossfade duration depends on crossfade enabled
        SwitchPreferenceCompat crossfadeEnabledPref = findPreference("enable_crossfade");
        SeekBarPreference crossfadeDurationPref = findPreference("crossfade_duration_ms");
        
        if (crossfadeEnabledPref != null && crossfadeDurationPref != null) {
            crossfadeDurationPref.setVisible(crossfadeEnabledPref.isChecked());
            crossfadeEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
                crossfadeDurationPref.setVisible((Boolean) newValue);
                return true;
            });
        }
        
        // Cache size depends on caching enabled
        SwitchPreferenceCompat cachingEnabledPref = findPreference("enable_track_caching");
        Preference cacheSizePref = findPreference("cache_size_mb");
        Preference clearCachePref = findPreference("clear_cache");
        
        if (cachingEnabledPref != null && cacheSizePref != null && clearCachePref != null) {
            boolean cachingEnabled = cachingEnabledPref.isChecked();
            cacheSizePref.setVisible(cachingEnabled);
            clearCachePref.setVisible(cachingEnabled);
            
            cachingEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                cacheSizePref.setVisible(enabled);
                clearCachePref.setVisible(enabled);
                
                // Warn about disabling caching
                if (!enabled) {
                    Toast.makeText(requireContext(), 
                            R.string.cache_disabled_warning, 
                            Toast.LENGTH_LONG).show();
                }
                
                return true;
            });
        }
    }
    
    private void updateAllSummaries() {
        updateCacheUsageSummary();
        updateCrossfadeSummary();
    }
    
    private void updateCrossfadeSummary() {
        SeekBarPreference crossfadeDurationPref = findPreference("crossfade_duration_ms");
        if (crossfadeDurationPref != null) {
            int duration = crossfadeDurationPref.getValue();
            if (duration >= 1000) {
                crossfadeDurationPref.setSummary(getString(R.string.crossfade_duration_summary_sec, 
                        duration / 1000f));
            } else {
                crossfadeDurationPref.setSummary(getString(R.string.crossfade_duration_summary_ms, 
                        duration));
            }
        }
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Apply changes immediately to the audio player
        switch (key) {
            case "enable_crossfade":
                boolean crossfadeEnabled = sharedPreferences.getBoolean(key, true);
                audioPlayerHelper.setCrossfadingEnabled(crossfadeEnabled);
                break;
                
            case "crossfade_duration_ms":
                int duration = sharedPreferences.getInt(key, 500);
                audioPlayerHelper.setCrossfadeDuration(duration);
                updateCrossfadeSummary();
                break;
                
            case "default_repeat_mode":
                int repeatMode = Integer.parseInt(sharedPreferences.getString(key, "0"));
                audioPlayerHelper.setRepeatMode(repeatMode);
                break;
                
            case "enable_track_caching":
                boolean cachingEnabled = sharedPreferences.getBoolean(key, true);
                musicCache.setCachingEnabled(cachingEnabled);
                break;
                
            case "cache_size_mb":
                int cacheSizeMb = sharedPreferences.getInt(key, 500);
                musicCache.setMaxCacheSize(cacheSizeMb * 1024 * 1024); // Convert to bytes
                updateCacheUsageSummary();
                break;
                
            case "use_low_latency_audio":
                boolean lowLatency = sharedPreferences.getBoolean(key, false);
                audioPlayerHelper.setLowLatencyMode(lowLatency);
                Toast.makeText(requireContext(), 
                        R.string.restart_app_for_audio_changes, 
                        Toast.LENGTH_SHORT).show();
                break;
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(this);
        updateAllSummaries();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(this);
    }
} 