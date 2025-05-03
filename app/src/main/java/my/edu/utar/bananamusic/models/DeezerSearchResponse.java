package my.edu.utar.bananamusic.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DeezerSearchResponse {
    @SerializedName("data")
    private List<Track> tracks;

    @SerializedName("total")
    private int total;

    public static class Track {
        @SerializedName("id")
        private long id;

        @SerializedName("title")
        private String title;

        @SerializedName("preview")
        private String previewUrl;

        @SerializedName("artist")
        private Artist artist;

        @SerializedName("album")
        private Album album;

        @SerializedName("duration")
        private int duration;

        public long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getPreviewUrl() {
            return previewUrl;
        }

        public Artist getArtist() {
            return artist;
        }

        public Album getAlbum() {
            return album;
        }

        public int getDuration() {
            return duration;
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
        @SerializedName("cover_medium")
        private String coverMedium;

        public String getCoverMedium() {
            return coverMedium;
        }
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public int getTotal() {
        return total;
    }
} 
 