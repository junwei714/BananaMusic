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

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.JsonUtils;

/**
 * Database helper for caching track data for offline viewing
 */
public class TrackDatabase extends SQLiteOpenHelper {
    private static final String TAG = "TrackDatabase";
    private static final String DATABASE_NAME = "track_cache.db";
    private static final int DATABASE_VERSION = 1;
    
    // Tables
    private static final String TABLE_RECOMMENDATIONS = "recommendations";
    private static final String TABLE_NEW_RELEASES = "new_releases";
    private static final String TABLE_HISTORY = "history";
    
    // Common columns
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_TRACK_ID = "track_id";
    private static final String COLUMN_TRACK_JSON = "track_json";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_MOOD = "mood";
    
    // Thread pool for async operations
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    // Singleton instance
    private static TrackDatabase instance;
    
    // Callbacks
    public interface TracksCallback {
        void onResult(List<Track> tracks);
    }
    
    private TrackDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    public static synchronized TrackDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new TrackDatabase(context.getApplicationContext());
        }
        return instance;
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create recommendations table
        db.execSQL("CREATE TABLE " + TABLE_RECOMMENDATIONS + " (" +
                   COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   COLUMN_USER_ID + " TEXT NOT NULL, " +
                   COLUMN_MOOD + " TEXT, " +
                   COLUMN_TRACK_ID + " TEXT NOT NULL, " +
                   COLUMN_TRACK_JSON + " TEXT NOT NULL, " +
                   COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                   "UNIQUE(" + COLUMN_USER_ID + ", " + COLUMN_MOOD + ", " + COLUMN_TRACK_ID + ") ON CONFLICT REPLACE)");
        
        // Create new releases table
        db.execSQL("CREATE TABLE " + TABLE_NEW_RELEASES + " (" +
                   COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   COLUMN_TRACK_ID + " TEXT NOT NULL, " +
                   COLUMN_TRACK_JSON + " TEXT NOT NULL, " +
                   COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                   "UNIQUE(" + COLUMN_TRACK_ID + ") ON CONFLICT REPLACE)");
        
        // Create history table
        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
                   COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   COLUMN_USER_ID + " TEXT NOT NULL, " +
                   COLUMN_TRACK_ID + " TEXT NOT NULL, " +
                   COLUMN_TRACK_JSON + " TEXT NOT NULL, " +
                   COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                   "UNIQUE(" + COLUMN_USER_ID + ", " + COLUMN_TRACK_ID + ") ON CONFLICT REPLACE)");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Simple version upgrade - drop and recreate
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECOMMENDATIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NEW_RELEASES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }
    
    /**
     * Cache recommendations for a user and mood
     */
    public void cacheRecommendations(String userId, String mood, List<Track> tracks) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long timestamp = System.currentTimeMillis();
                
                // Delete old entries for this user and mood
                db.delete(TABLE_RECOMMENDATIONS, 
                      COLUMN_USER_ID + " = ? AND " + COLUMN_MOOD + " = ?", 
                      new String[]{userId, mood != null ? mood : ""});
                
                // Insert new entries
                for (Track track : tracks) {
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_USER_ID, userId);
                    values.put(COLUMN_MOOD, mood != null ? mood : "");
                    values.put(COLUMN_TRACK_ID, track.getId());
                    values.put(COLUMN_TRACK_JSON, JsonUtils.toJson(track));
                    values.put(COLUMN_TIMESTAMP, timestamp);
                    
                    db.insert(TABLE_RECOMMENDATIONS, null, values);
                }
                
                db.setTransactionSuccessful();
                Log.d(TAG, "Cached " + tracks.size() + " recommendations for user " + userId + 
                      " and mood " + mood);
            } catch (Exception e) {
                Log.e(TAG, "Error caching recommendations: " + e.getMessage(), e);
            } finally {
                db.endTransaction();
            }
        });
    }
    
    /**
     * Get cached recommendations for a user and mood
     */
    public void getCachedRecommendations(String userId, String mood, TracksCallback callback) {
        executor.execute(() -> {
            List<Track> tracks = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            
            try {
                String query = "SELECT " + COLUMN_TRACK_JSON + 
                             " FROM " + TABLE_RECOMMENDATIONS + 
                             " WHERE " + COLUMN_USER_ID + " = ? AND " + COLUMN_MOOD + " = ?" +
                             " ORDER BY " + COLUMN_ID + " ASC";
                             
                Cursor cursor = db.rawQuery(query, 
                             new String[]{userId, mood != null ? mood : ""});
                
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
                
                Log.d(TAG, "Retrieved " + tracks.size() + " cached recommendations for user " + 
                      userId + " and mood " + mood);
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving cached recommendations: " + e.getMessage(), e);
            }
            
            // Make sure we call the callback on the main thread
            final List<Track> finalTracks = tracks;
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> callback.onResult(finalTracks));
        });
    }
    
    /**
     * Cache new releases
     */
    public void cacheNewReleases(List<Track> tracks) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long timestamp = System.currentTimeMillis();
                
                // Delete old entries if we have too many
                Cursor countCursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_NEW_RELEASES, null);
                
                if (countCursor != null && countCursor.moveToFirst()) {
                    int count = countCursor.getInt(0);
                    countCursor.close();
                    
                    if (count > 100) { // Keep only last 100 entries
                        db.delete(TABLE_NEW_RELEASES, 
                            COLUMN_TIMESTAMP + " <= (SELECT " + COLUMN_TIMESTAMP + 
                            " FROM " + TABLE_NEW_RELEASES + 
                            " ORDER BY " + COLUMN_TIMESTAMP + " DESC " +
                            " LIMIT 1 OFFSET 100)", null);
                    }
                }
                
                // Insert new entries
                for (Track track : tracks) {
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_TRACK_ID, track.getId());
                    values.put(COLUMN_TRACK_JSON, JsonUtils.toJson(track));
                    values.put(COLUMN_TIMESTAMP, timestamp);
                    
                    db.insert(TABLE_NEW_RELEASES, null, values);
                }
                
                db.setTransactionSuccessful();
                Log.d(TAG, "Cached " + tracks.size() + " new releases");
            } catch (Exception e) {
                Log.e(TAG, "Error caching new releases: " + e.getMessage(), e);
            } finally {
                db.endTransaction();
            }
        });
    }
    
    /**
     * Get cached new releases
     */
    public void getCachedNewReleases(TracksCallback callback) {
        executor.execute(() -> {
            List<Track> tracks = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            
            try {
                String query = "SELECT " + COLUMN_TRACK_JSON + 
                             " FROM " + TABLE_NEW_RELEASES + 
                             " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
                             
                Cursor cursor = db.rawQuery(query, null);
                
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
                
                Log.d(TAG, "Retrieved " + tracks.size() + " cached new releases");
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving cached new releases: " + e.getMessage(), e);
            }
            
            // Make sure we call the callback on the main thread
            final List<Track> finalTracks = tracks;
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> callback.onResult(finalTracks));
        });
    }
    
    /**
     * Add a track to user's history
     */
    public void addToHistory(String userId, Track track) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long timestamp = System.currentTimeMillis();
                
                // Delete old entries if we have too many
                Cursor countCursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_HISTORY + 
                    " WHERE " + COLUMN_USER_ID + " = ?", 
                    new String[]{userId});
                
                if (countCursor != null && countCursor.moveToFirst()) {
                    int count = countCursor.getInt(0);
                    countCursor.close();
                    
                    if (count > 1000) { // Keep only last 1000 entries
                        db.delete(TABLE_HISTORY, 
                            COLUMN_USER_ID + " = ? AND " + COLUMN_TIMESTAMP + 
                            " <= (SELECT " + COLUMN_TIMESTAMP + 
                            " FROM " + TABLE_HISTORY + 
                            " WHERE " + COLUMN_USER_ID + " = ? " +
                            " ORDER BY " + COLUMN_TIMESTAMP + " DESC " +
                            " LIMIT 1 OFFSET 1000)", 
                            new String[]{userId, userId});
                    }
                }
                
                // Insert new entry
                ContentValues values = new ContentValues();
                values.put(COLUMN_USER_ID, userId);
                values.put(COLUMN_TRACK_ID, track.getId());
                values.put(COLUMN_TRACK_JSON, JsonUtils.toJson(track));
                values.put(COLUMN_TIMESTAMP, timestamp);
                
                db.insert(TABLE_HISTORY, null, values);
                
                db.setTransactionSuccessful();
                Log.d(TAG, "Added track " + track.getId() + " to history for user " + userId);
            } catch (Exception e) {
                Log.e(TAG, "Error adding track to history: " + e.getMessage(), e);
            } finally {
                db.endTransaction();
            }
        });
    }
    
    /**
     * Get user's track history
     */
    public void getHistory(String userId, TracksCallback callback) {
        executor.execute(() -> {
            List<Track> tracks = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            
            try {
                String query = "SELECT " + COLUMN_TRACK_JSON + 
                             " FROM " + TABLE_HISTORY + 
                             " WHERE " + COLUMN_USER_ID + " = ? " +
                             " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
                             
                Cursor cursor = db.rawQuery(query, new String[]{userId});
                
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
                
                Log.d(TAG, "Retrieved " + tracks.size() + " tracks from history for user " + userId);
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving history: " + e.getMessage(), e);
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
                db.delete(TABLE_RECOMMENDATIONS, null, null);
                db.delete(TABLE_NEW_RELEASES, null, null);
                db.delete(TABLE_HISTORY, null, null);
                
                db.setTransactionSuccessful();
                Log.d(TAG, "Cleared all cached data");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing cached data: " + e.getMessage(), e);
            } finally {
                db.endTransaction();
            }
        });
    }
} 