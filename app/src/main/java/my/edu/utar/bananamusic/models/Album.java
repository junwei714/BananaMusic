package my.edu.utar.bananamusic.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Album implements Parcelable {
    private String id;
    private String title;
    private String artist;
    private String coverUrl;
    private Date releaseDate;
    private String deezerUrl;
    private String imageUrl;
    private String type; // "album" or "playlist"
    private String artistId;
    private String albumArtUrl;
    private int year;
    private List<Track> tracks;
    private int trackCount;
    private String spotifyId;
    private boolean isLocal;

    public Album() {
        this.tracks = new ArrayList<>();
    }

    public Album(String id, String title, String artist, String coverUrl, Date releaseDate, String deezerUrl) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.coverUrl = coverUrl;
        this.releaseDate = releaseDate;
        this.deezerUrl = deezerUrl;
        this.tracks = new ArrayList<>();
        this.isLocal = true;
    }

    public Album(String id, String title, String artist, String coverUrl) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.coverUrl = coverUrl;
        this.tracks = new ArrayList<>();
        this.isLocal = true;
    }

    protected Album(Parcel in) {
        id = in.readString();
        title = in.readString();
        artist = in.readString();
        artistId = in.readString();
        coverUrl = in.readString();
        releaseDate = (Date) in.readSerializable();
        imageUrl = in.readString();
        type = in.readString();
        year = in.readInt();
        tracks = new ArrayList<>();
        in.readList(tracks, Track.class.getClassLoader());
        trackCount = in.readInt();
        spotifyId = in.readString();
        isLocal = in.readByte() != 0;
        albumArtUrl = in.readString();
        deezerUrl = in.readString();
    }

    public static final Creator<Album> CREATOR = new Creator<Album>() {
        @Override
        public Album createFromParcel(Parcel in) {
            return new Album(in);
        }

        @Override
        public Album[] newArray(int size) {
            return new Album[size];
        }
    };

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAlbumArtUrl() {
        return albumArtUrl;
    }

    public void setAlbumArtUrl(String albumArtUrl) {
        this.albumArtUrl = albumArtUrl;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
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

    public int getTrackCount() {
        return trackCount;
    }

    public void setTrackCount(int trackCount) {
        this.trackCount = trackCount;
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public void setSpotifyId(String spotifyId) {
        this.spotifyId = spotifyId;
    }

    public boolean isLocal() {
        return isLocal;
    }

    public void setLocal(boolean local) {
        isLocal = local;
    }

    public String getDeezerUrl() {
        return deezerUrl;
    }

    public String getName() {
        return title;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(artistId);
        dest.writeString(coverUrl);
        dest.writeSerializable(releaseDate);
        dest.writeString(imageUrl);
        dest.writeString(type);
        dest.writeInt(year);
        dest.writeList(tracks);
        dest.writeInt(trackCount);
        dest.writeString(spotifyId);
        dest.writeByte((byte) (isLocal ? 1 : 0));
        dest.writeString(albumArtUrl);
        dest.writeString(deezerUrl);
    }
} 