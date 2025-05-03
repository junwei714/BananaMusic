package my.edu.utar.bananamusic.utils;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class SpotifyDeviceManager {
    private static final String TAG = "SpotifyDeviceManager";
    
    public static class SpotifyDevice {
        private final String id;
        private final String name;
        private final String type;
        private final boolean isActive;
        private final boolean isRestricted;
        private final int volumePercent;
        private final boolean supportsVolume;
        
        public SpotifyDevice(JSONObject json) {
            this.id = json.optString("id");
            this.name = json.optString("name");
            this.type = json.optString("type");
            this.isActive = json.optBoolean("is_active", false);
            this.isRestricted = json.optBoolean("is_restricted", false);
            this.volumePercent = json.optInt("volume_percent", 100);
            this.supportsVolume = json.optBoolean("supports_volume", true);
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isActive() { return isActive; }
        public boolean isRestricted() { return isRestricted; }
        public int getVolumePercent() { return volumePercent; }
        public boolean supportsVolume() { return supportsVolume; }
    }
    
    public interface DeviceCallback {
        void onDevicesFound(List<SpotifyDevice> devices);
        void onError(String message);
    }
    
    public static void getAvailableDevices(String accessToken, DeviceCallback callback) {
        // Make API call to Spotify's devices endpoint
        NetworkManager.getInstance().get(
            "https://api.spotify.com/v1/me/player/devices",
            accessToken,
            response -> {
                try {
                    JSONObject json = new JSONObject(response);
                    JSONArray devicesArray = json.getJSONArray("devices");
                    List<SpotifyDevice> devices = new ArrayList<>();
                    
                    for (int i = 0; i < devicesArray.length(); i++) {
                        JSONObject deviceJson = devicesArray.getJSONObject(i);
                        SpotifyDevice device = new SpotifyDevice(deviceJson);
                        devices.add(device);
                    }
                    
                    callback.onDevicesFound(devices);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing devices response", e);
                    callback.onError("Failed to parse devices response");
                }
            },
            error -> {
                Log.e(TAG, "Error getting devices: " + error.getMessage(), null);
                callback.onError(error.getMessage());
            }
        );
    }
    
    public static void getActiveDevices(String accessToken, DeviceCallback callback) {
        // This method is the same as getAvailableDevices but named differently
        // to match references in the code
        getAvailableDevices(accessToken, callback);
    }
    
    public static SpotifyDevice selectBestDevice(List<SpotifyDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            return null;
        }
        
        // First, try to find the currently active device
        for (SpotifyDevice device : devices) {
            if (device.isActive() && !device.isRestricted()) {
                return device;
            }
        }
        
        // Next, prefer Smartphone/Tablet devices as they are most likely to work
        for (SpotifyDevice device : devices) {
            String type = device.getType().toLowerCase();
            if ((type.contains("smartphone") || type.contains("tablet")) && !device.isRestricted()) {
                return device;
            }
        }
        
        // Then try to find any unrestricted device
        for (SpotifyDevice device : devices) {
            if (!device.isRestricted()) {
                return device;
            }
        }
        
        // If all else fails, return the first device
        return devices.get(0);
    }
    
    public static void setActiveDevice(String deviceId, String accessToken, NetworkCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("device_ids", new JSONArray().put(deviceId));
            body.put("play", false); // Don't start playback automatically
            
            NetworkManager.getInstance().put(
                "https://api.spotify.com/v1/me/player",
                accessToken,
                body.toString(),
                response -> callback.onSuccess("Device activated successfully"),
                error -> {
                    Log.e(TAG, "Error setting active device: " + error.getMessage(), null);
                    callback.onError(error.getMessage());
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error creating request body", e);
            callback.onError("Failed to create request body");
        }
    }
    
    public static void setVolume(String deviceId, int volumePercent, String accessToken, NetworkCallback callback) {
        if (volumePercent < 0 || volumePercent > 100) {
            callback.onError("Volume must be between 0 and 100");
            return;
        }
        
        String url = String.format(
            "https://api.spotify.com/v1/me/player/volume?volume_percent=%d&device_id=%s",
            volumePercent,
            deviceId
        );
        
        NetworkManager.getInstance().put(
            url,
            accessToken,
            "",
            response -> callback.onSuccess("Volume set successfully"),
            error -> {
                Log.e(TAG, "Error setting volume: " + error.getMessage(), null);
                callback.onError(error.getMessage());
            }
        );
    }
    
    public interface NetworkCallback {
        void onSuccess(String response);
        void onError(String message);
    }
}