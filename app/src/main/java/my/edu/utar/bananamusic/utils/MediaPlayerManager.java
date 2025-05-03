package my.edu.utar.bananamusic.utils;

import android.content.Context;
import android.media.MediaPlayer;
import my.edu.utar.bananamusic.models.Track;
import java.util.List;
import java.util.ArrayList;

public class MediaPlayerManager {
    private Context context;
    private MediaPlayer mediaPlayer;
    private Track currentTrack;
    private List<Track> queue;
    private int currentIndex;

    public MediaPlayerManager(Context context) {
        this.context = context;
        this.mediaPlayer = new MediaPlayer();
        this.queue = new ArrayList<>();
        this.currentIndex = -1;
    }

    public void setQueue(List<Track> tracks) {
        this.queue = new ArrayList<>(tracks);
        this.currentIndex = -1;
    }

    public void playTrack(Track track) {
        this.currentTrack = track;
        // Implementation for playing track
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
} 