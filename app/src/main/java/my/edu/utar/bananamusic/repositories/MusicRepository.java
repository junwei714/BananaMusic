package my.edu.utar.bananamusic.repositories;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.models.Album;
import my.edu.utar.bananamusic.models.Artist;
import my.edu.utar.bananamusic.models.Track;

public class MusicRepository {
    private static final String TAG = "MusicRepository";
    private static MusicRepository instance;
    private final Context context;
    private final ContentResolver contentResolver;

    private MusicRepository(Context context) {
        this.context = context.getApplicationContext();
        this.contentResolver = context.getContentResolver();
    }

    public static synchronized MusicRepository getInstance(Context context) {
        if (instance == null) {
            instance = new MusicRepository(context);
        }
        return instance;
    }

    public List<Track> searchTracks(String query) {
        List<Track> tracks = new ArrayList<>();
        
        try {
            String selection = MediaStore.Audio.Media.TITLE + " LIKE ? OR " +
                    MediaStore.Audio.Media.ARTIST + " LIKE ? OR " +
                    MediaStore.Audio.Media.ALBUM + " LIKE ?";
            String[] selectionArgs = new String[]{"%" + query + "%", "%" + query + "%", "%" + query + "%"};

            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA
            };

            try (Cursor cursor = contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    null)) {

                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                    int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                    int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                    int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

                    while (cursor.moveToNext()) {
                        String id = cursor.getString(idColumn);
                        String title = cursor.getString(titleColumn);
                        String artist = cursor.getString(artistColumn);
                        String album = cursor.getString(albumColumn);
                        long albumId = cursor.getLong(albumIdColumn);
                        int duration = cursor.getInt(durationColumn);
                        String path = cursor.getString(dataColumn);

                        // Get album art URI
                        Uri albumArtUri = Uri.parse("content://media/external/audio/albumart");
                        Uri albumArtUriWithId = Uri.withAppendedPath(albumArtUri, String.valueOf(albumId));
                        String albumArtUrl = albumArtUriWithId.toString();

                        Track track = new Track(id, title, artist, album, albumArtUrl, null, duration, true);
                        track.setSource(Track.SOURCE_LOCAL);
                        track.setType("track");
                        tracks.add(track);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error searching local tracks: " + e.getMessage());
        }
        
        return tracks;
    }

    public List<Album> searchAlbums(String query) {
        List<Album> albums = new ArrayList<>();
        
        try {
            String selection = MediaStore.Audio.Albums.ALBUM + " LIKE ? OR " +
                    MediaStore.Audio.Albums.ARTIST + " LIKE ?";
            String[] selectionArgs = new String[]{"%" + query + "%", "%" + query + "%"};

            Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Albums._ID,
                    MediaStore.Audio.Albums.ALBUM,
                    MediaStore.Audio.Albums.ARTIST,
                    MediaStore.Audio.Albums.ALBUM_ART,
                    MediaStore.Audio.Albums.NUMBER_OF_SONGS
            };

            try (Cursor cursor = contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    null)) {

                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID);
                    int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM);
                    int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST);
                    int albumArtColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART);
                    int trackCountColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS);

                    while (cursor.moveToNext()) {
                        String id = cursor.getString(idColumn);
                        String title = cursor.getString(albumColumn);
                        String artist = cursor.getString(artistColumn);
                        String albumArt = cursor.getString(albumArtColumn);
                        int trackCount = cursor.getInt(trackCountColumn);

                        Album album = new Album(id, title, artist, albumArt);
                        album.setTrackCount(trackCount);
                        albums.add(album);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error searching local albums: " + e.getMessage());
        }
        
        return albums;
    }

    public List<Artist> searchArtists(String query) {
        List<Artist> artists = new ArrayList<>();
        
        try {
            String selection = MediaStore.Audio.Artists.ARTIST + " LIKE ?";
            String[] selectionArgs = new String[]{"%" + query + "%"};

            Uri uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Artists._ID,
                    MediaStore.Audio.Artists.ARTIST,
                    MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
                    MediaStore.Audio.Artists.NUMBER_OF_TRACKS
            };

            try (Cursor cursor = contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    null)) {

                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID);
                    int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST);

                    while (cursor.moveToNext()) {
                        String id = cursor.getString(idColumn);
                        String name = cursor.getString(artistColumn);

                        // We don't have image URLs for local artists
                        Artist artist = new Artist(id, name, null);
                        artists.add(artist);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error searching local artists: " + e.getMessage());
        }
        
        return artists;
    }
} 