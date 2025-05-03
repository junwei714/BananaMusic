package my.edu.utar.bananamusic.models;

import java.util.ArrayList;
import java.util.List;

public class User {
    private String userId;
    private String email;
    private String displayName;
    private String profileImageUrl;
    private List<String> recentlyPlayed;
    private List<String> savedPlaylists;

    // Empty constructor needed for Firestore
    public User() {
        recentlyPlayed = new ArrayList<>();
        savedPlaylists = new ArrayList<>();
    }

    public User(String userId, String email, String displayName) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = "";
        this.recentlyPlayed = new ArrayList<>();
        this.savedPlaylists = new ArrayList<>();
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public List<String> getRecentlyPlayed() {
        return recentlyPlayed;
    }

    public void setRecentlyPlayed(List<String> recentlyPlayed) {
        this.recentlyPlayed = recentlyPlayed;
    }

    public List<String> getSavedPlaylists() {
        return savedPlaylists;
    }

    public void setSavedPlaylists(List<String> savedPlaylists) {
        this.savedPlaylists = savedPlaylists;
    }

    public void addRecentlyPlayed(String trackId) {
        if (recentlyPlayed.contains(trackId)) {
            recentlyPlayed.remove(trackId);
        }
        recentlyPlayed.add(0, trackId);
        
        // Limit to last 20 tracks
        if (recentlyPlayed.size() > 20) {
            recentlyPlayed = recentlyPlayed.subList(0, 20);
        }
    }

    public void addSavedPlaylist(String playlistId) {
        if (!savedPlaylists.contains(playlistId)) {
            savedPlaylists.add(playlistId);
        }
    }

    public void removeSavedPlaylist(String playlistId) {
        savedPlaylists.remove(playlistId);
    }
} 