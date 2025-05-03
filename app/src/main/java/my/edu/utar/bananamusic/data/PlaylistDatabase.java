package my.edu.utar.bananamusic.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.JsonUtils;

/**
 * Database helper for caching playlist data for offline viewing
 */
public class PlaylistDatabase extends SQLiteOpenHelper {
    private static final String TAG = "PlaylistDatabase";
    private static final String DATABASE_NAME = "playlist_cache.db";
    private static final int DATABASE_VERSION = 1;
    
    // Tables
    private static final String TABLE_FEATURED = "featured_playlists";
    private static final String TABLE_PLAYLIST_TRACKS = "playlist_tracks";
    
    // Common columns
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_PLAYLIST_ID = "playlist_id";
    private static final String COLUMN_PLAYLIST_JSON = "playlist_json";
    private static final String COLUMN_TRACK_JSON = "track_json";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    
    // Thread pool for async operations
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    // Singleton instance
    private static PlaylistDatabase instance;
    
    // Callbacks
    public interface PlaylistsCallback {
        void onResult(List<Playlist> playlists);
    }
    
    private PlaylistDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    public static synchronized PlaylistDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new PlaylistDatabase(context.getApplicationContext());
        }
        return instance;
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create featured playlists table
        db.execSQL("CREATE TABLE " + TABLE_FEATURED + " (" +
                   COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   COLUMN_PLAYLIST_ID + " TEXT NOT NULL, " +
                   COLUMN_PLAYLIST_JSON + " TEXT NOT NULL, " +
                   COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                   "UNIQUE(" + COLUMN_PLAYLIST_ID + ") ON CONFLICT REPLACE)");
        
        // Create playlist tracks table
        db.execSQL("CREATE TABLE " + TABLE_PLAYLIST_TRACKS + " (" +
                   COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   COLUMN_PLAYLIST_ID + " TEXT NOT NULL, " +
                   COLUMN_TRACK_JSON + " TEXT NOT NULL, " +
                   COLUMN_TIMESTAMP + " INTEGER NOT NULL)");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Simple version upgrade - drop and recreate
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FEATURED);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLIST_TRACKS);
        onCreate(db);
    }
    
    /**
     * Cache featured playlists
     */
    public void cacheFeaturedPlaylists(List<Playlist> playlists) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long timestamp = System.currentTimeMillis();
                
                // Delete old entries
                db.delete(TABLE_FEATURED, null, null);
                
                // Insert new entries
                for (Playlist playlist : playlists) {
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_PLAYLIST_ID, playlist.getPlaylistId());
                    values.put(COLUMN_PLAYLIST_JSON, JsonUtils.toJson(playlist));
                    values.put(COLUMN_TIMESTAMP, timestamp);
                    
                    db.insert(TABLE_FEATURED, null, values);
                }
                
                db.setTransactionSuccessful();
                Log.d(TAG, "Cached " + playlists.size() + " featured playlists");
            } catch (Exception e) {
                Log.e(TAG, "Error caching featured playlists: " + e.getMessage(), e);
            } finally {
                db.endTransaction();
            }
        });
    }
    
    /**
     * Get cached featured playlists
     */
    public void getCachedFeaturedPlaylists(PlaylistsCallback callback) {
        executor.execute(() -> {
            List<Playlist> playlists = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            
            try {
                String query = "SELECT " + COLUMN_PLAYLIST_JSON + 
                             " FROM " + TABLE_FEATURED + 
                             " ORDER BY " + COLUMN_ID + " ASC";
                             
                Cursor cursor = db.rawQuery(query, null);
                
                if (cursor != null) {
                    int playlistJsonIndex = cursor.getColumnIndex(COLUMN_PLAYLIST_JSON);
                    
                    while (cursor.moveToNext()) {
                        if (playlistJsonIndex != -1) {
                            String playlistJson = cursor.getString(playlistJsonIndex);
                            Playlist playlist = JsonUtils.fromJson(playlistJson, Playlist.class);
                            if (playlist != null) {
                                playlists.add(playlist);
                            }
                        }
                    }
                    
                    cursor.close();
                }
                
                Log.d(TAG, "Retrieved " + playlists.size() + " cached featured playlists");
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving cached featured playlists: " + e.getMessage(), e);
            }
            
            // Make sure we call the callback on the main thread
            final List<Playlist> finalPlaylists = playlists;
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> callback.onResult(finalPlaylists));
        });
    }
    
    /**
     * Cache a playlist with its tracks
     */
    public void cachePlaylistWithTracks(Playlist playlist, List<Track> tracks) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long timestamp = System.currentTimeMillis();
                
                // Cache playlist
                ContentValues playlistValues = new ContentValues();
                playlistValues.put(COLUMN_PLAYLIST_ID, playlist.getPlaylistId());
                playlistValues.put(COLUMN_PLAYLIST_JSON, JsonUtils.toJson(playlist));
                playlistValues.put(COLUMN_TIMESTAMP, timestamp);
                
                db.insert(TABLE_FEATURED, null, playlistValues);
                
                // Cache tracks
                for (Track track : tracks) {
                    ContentValues trackValues = new ContentValues();
                    trackValues.put(COLUMN_PLAYLIST_ID, playlist.getPlaylistId());
                    trackValues.put(COLUMN_TRACK_JSON, JsonUtils.toJson(track));
                    trackValues.put(COLUMN_TIMESTAMP, timestamp);
                    
                    db.insert(TABLE_PLAYLIST_TRACKS, null, trackValues);
                }
                
                db.setTransactionSuccessful();
                Log.d(TAG, "Cached playlist " + playlist.getPlaylistId() + " with " + tracks.size() + " tracks");
            } catch (Exception e) {
                Log.e(TAG, "Error caching playlist with tracks: " + e.getMessage(), e);
            } finally {
                db.endTransaction();
            }
        });
    }
    
    /**
     * Get tracks for a cached playlist
     */
    public void getPlaylistTracks(String playlistId, TrackDatabase.TracksCallback callback) {
        executor.execute(() -> {
            List<Track> tracks = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            
            try {
                String query = "SELECT " + COLUMN_TRACK_JSON + 
                             " FROM " + TABLE_PLAYLIST_TRACKS + 
                             " WHERE " + COLUMN_PLAYLIST_ID + " = ? " +
                             " ORDER BY " + COLUMN_ID + " ASC";
                             
                Cursor cursor = db.rawQuery(query, new String[]{playlistId});
                
                if (cursor != null) {
                    int trackJsonIndex = cursor.getColumnIndex(COLUMN_TRACK_JSON);
                    
                    while (cursor.moveToNext()) {
                        if (trackJsonIndex != -1) {
                            String trackJson = cursor.getString(trackJsonIndex);
                            Track track = JsonUtils.fromJson(trackJson, Track.class);
                            if (track != null) {
                                tracks.add(track);
                            }
                        }
                    }
                    
                    cursor.close();
                }
                
                Log.d(TAG, "Retrieved " + tracks.size() + " tracks for playlist " + playlistId);
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving playlist tracks: " + e.getMessage(), e);
            }
            
            // Make sure we call the callback on the main thread
            final List<Track> finalTracks = tracks;
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> callback.onResult(finalTracks));
        });
    }
    
    /**
     * Clear all cached data
     */
    public void clearAllData() {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                // Delete all data from all tables
                db.delete(TABLE_FEATURED, null, null);
                db.delete(TABLE_PLAYLIST_TRACKS, null, null);
                
                db.setTransactionSuccessful();
                Log.d(TAG, "Cleared all cached playlist data");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing cached data: " + e.getMessage(), e);
            } finally {
                db.endTransaction();
            }
        });
    }
} 