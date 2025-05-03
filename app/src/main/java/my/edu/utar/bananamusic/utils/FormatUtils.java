package my.edu.utar.bananamusic.utils;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for formatting various types of data
 */
public class FormatUtils {

    /**
     * Formats milliseconds duration to "MM:SS" format
     * @param durationMs Duration in milliseconds
     * @return Formatted duration string
     */
    public static String formatDuration(long durationMs) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    /**
     * Formats file size in bytes to human-readable format
     * @param bytes File size in bytes
     * @return Formatted file size string (e.g., "3.5 MB")
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
} 