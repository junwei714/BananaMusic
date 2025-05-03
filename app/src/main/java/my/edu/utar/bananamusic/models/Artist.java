package my.edu.utar.bananamusic.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class Artist implements Parcelable {
    private String id;
    private String name;
    private String imageUrl;
    private List<Album> albums;
    private List<Track> tracks;
    private int popularity;
    private List<String> genres;
    private String spotifyId;
    private boolean isLocal;

    public Artist() {
        this.albums = new ArrayList<>();
        this.tracks = new ArrayList<>();
        this.genres = new ArrayList<>();
        this.isLocal = true;
    }

    public Artist(String id, String name, String imageUrl) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.albums = new ArrayList<>();
        this.tracks = new ArrayList<>();
        this.genres = new ArrayList<>();
        this.isLocal = true;
    }

    protected Artist(Parcel in) {
        id = in.readString();
        name = in.readString();
        imageUrl = in.readString();
        albums = new ArrayList<>();
        in.readList(albums, Album.class.getClassLoader());
        tracks = new ArrayList<>();
        in.readList(tracks, Track.class.getClassLoader());
        popularity = in.readInt();
        genres = in.createStringArrayList();
        spotifyId = in.readString();
        isLocal = in.readByte() != 0;
    }

    public static final Creator<Artist> CREATOR = new Creator<Artist>() {
        @Override
        public Artist createFromParcel(Parcel in) {
            return new Artist(in);
        }

        @Override
        public Artist[] newArray(int size) {
            return new Artist[size];
        }
    };

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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }

    public void addAlbum(Album album) {
        if (this.albums == null) {
            this.albums = new ArrayList<>();
        }
        this.albums.add(album);
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

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(imageUrl);
        dest.writeList(albums);
        dest.writeList(tracks);
        dest.writeInt(popularity);
        dest.writeStringList(genres);
        dest.writeString(spotifyId);
        dest.writeByte((byte) (isLocal ? 1 : 0));
    }
} 