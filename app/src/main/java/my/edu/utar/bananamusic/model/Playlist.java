package my.edu.utar.bananamusic.model;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    private String id;
    private String name;
    private String description;
    private String imageUrl;
    private String ownerId;
    private String ownerName;
    private boolean isCollaborative;
    private List<Track> tracks;

    public Playlist() {
        // Required empty constructor
        this.tracks = new ArrayList<>();
    }

    public Playlist(String id, String name, String description, String imageUrl, String ownerId, String ownerName, boolean isCollaborative) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.isCollaborative = isCollaborative;
        this.tracks = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean isCollaborative() {
        return isCollaborative;
    }

    public void setCollaborative(boolean collaborative) {
        isCollaborative = collaborative;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public void setTracks(List<Track> tracks) {
        this.tracks = tracks;
    }

    public void addTrack(Track track) {
        if (this.tracks == null) {
            this.tracks = new ArrayList<>();
        }
        this.tracks.add(track);
    }

    public void removeTrack(Track track) {
        if (this.tracks != null) {
            this.tracks.remove(track);
        }
    }

    public int getTracksCount() {
        return tracks != null ? tracks.size() : 0;
    }
} 