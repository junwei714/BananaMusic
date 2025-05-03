package my.edu.utar.bananamusic.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SpotifySearchResponse {
    @SerializedName("tracks")
    private Tracks tracks;

    public static class Tracks {
        @SerializedName("items")
        private List<Track> items;

        public List<Track> getItems() {
            return items;
        }
    }

    public static class Track {
        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        @SerializedName("artists")
        private List<Artist> artists;

        @SerializedName("album")
        private Album album;

        @SerializedName("duration_ms")
        private int durationMs;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<Artist> getArtists() {
            return artists;
        }

        public Album getAlbum() {
            return album;
        }

        public int getDurationMs() {
            return durationMs;
        }
    }

    public static class Artist {
        @SerializedName("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    public static class Album {
        @SerializedName("images")
        private List<Image> images;

        public List<Image> getImages() {
            return images;
        }
    }

    public static class Image {
        @SerializedName("url")
        private String url;

        public String getUrl() {
            return url;
        }
    }

    public Tracks getTracks() {
        return tracks;
    }
} 
 