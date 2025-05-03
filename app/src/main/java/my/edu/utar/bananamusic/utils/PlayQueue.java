package my.edu.utar.bananamusic.utils;

import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.Playlist;

public class PlayQueue {
    private static final String TAG = "PlayQueue";
    
    private final List<Track> queue;
    private final List<Track> shuffledQueue;
    private final List<Track> history;
    private int currentIndex;
    private boolean isShuffled;
    private PlayQueueListener listener;
    
    public interface PlayQueueListener {
        void onQueueChanged();
        void onCurrentTrackChanged(Track track);
    }
    
    public PlayQueue() {
        queue = new ArrayList<>();
        shuffledQueue = new ArrayList<>();
        history = new ArrayList<>();
        currentIndex = -1;
        isShuffled = false;
    }
    
    public void setListener(PlayQueueListener listener) {
        this.listener = listener;
    }
    
    public void add(Track track) {
        queue.add(track);
        if (isShuffled) {
            shuffledQueue.add(track);
            Collections.shuffle(shuffledQueue);
        }
        if (currentIndex == -1) {
            currentIndex = 0;
        }
        notifyQueueChanged();
    }
    
    public void addToQueueNext(Track track) {
        if (currentIndex >= 0 && currentIndex < queue.size() - 1) {
            queue.add(currentIndex + 1, track);
            if (isShuffled) {
                int currentShuffledIndex = shuffledQueue.indexOf(getCurrent());
                if (currentShuffledIndex >= 0) {
                    shuffledQueue.add(currentShuffledIndex + 1, track);
                } else {
                    shuffledQueue.add(track);
                }
            }
        } else {
            add(track);
        }
        notifyQueueChanged();
    }
    
    public void addAll(List<Track> tracks) {
        queue.addAll(tracks);
        if (isShuffled) {
            shuffledQueue.addAll(tracks);
            Collections.shuffle(shuffledQueue);
        }
        if (currentIndex == -1 && !queue.isEmpty()) {
            currentIndex = 0;
        }
        notifyQueueChanged();
    }
    
    public void remove(int index) {
        if (index >= 0 && index < queue.size()) {
            Track track = queue.remove(index);
            if (isShuffled) {
                shuffledQueue.remove(track);
            }
            if (index < currentIndex) {
                currentIndex--;
            }
            notifyQueueChanged();
        }
    }
    
    public void move(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < queue.size() && 
            toIndex >= 0 && toIndex < queue.size()) {
            Track track = queue.remove(fromIndex);
            queue.add(toIndex, track);
            
            if (fromIndex == currentIndex) {
                currentIndex = toIndex;
            } else if (fromIndex < currentIndex && toIndex >= currentIndex) {
                currentIndex--;
            } else if (fromIndex > currentIndex && toIndex <= currentIndex) {
                currentIndex++;
            }
            
            notifyQueueChanged();
        }
    }
    
    public void clear() {
        queue.clear();
        shuffledQueue.clear();
        history.clear();
        currentIndex = -1;
        notifyQueueChanged();
    }
    
    public Track getCurrent() {
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            return isShuffled ? shuffledQueue.get(currentIndex) : queue.get(currentIndex);
        }
        return null;
    }
    
    public Track getNext() {
        if (queue.isEmpty()) return null;
        
        if (currentIndex < (isShuffled ? shuffledQueue.size() : queue.size()) - 1) {
            currentIndex++;
            Track next = getCurrent();
            notifyCurrentTrackChanged(next);
            return next;
        }
        return null;
    }
    
    public Track getPrevious() {
        if (queue.isEmpty()) return null;
        
        if (currentIndex > 0) {
            currentIndex--;
            Track prev = getCurrent();
            notifyCurrentTrackChanged(prev);
            return prev;
        }
        return null;
    }
    
    public void jumpTo(int index) {
        if (index >= 0 && index < queue.size()) {
            currentIndex = index;
            notifyCurrentTrackChanged(getCurrent());
        }
    }
    
    public Track skipToIndex(int index) {
        if (index >= 0 && index < queue.size()) {
            currentIndex = index;
            Track track = getCurrent();
            notifyCurrentTrackChanged(track);
            return track;
        }
        return null;
    }
    
    public Track resetToFirst() {
        if (!queue.isEmpty()) {
            currentIndex = 0;
            Track track = getCurrent();
            notifyCurrentTrackChanged(track);
            return track;
        }
        return null;
    }
    
    public void reset() {
        currentIndex = 0;
        notifyCurrentTrackChanged(getCurrent());
    }
    
    public void toEnd() {
        if (!queue.isEmpty()) {
            currentIndex = queue.size() - 1;
            notifyCurrentTrackChanged(getCurrent());
        }
    }
    
    public void toggleShuffle() {
        if (queue.isEmpty()) return;
        
        Track currentTrack = getCurrent();
        isShuffled = !isShuffled;
        
        if (isShuffled) {
            shuffledQueue.clear();
            shuffledQueue.addAll(queue);
            Collections.shuffle(shuffledQueue);
            // Make sure current track stays the same
            if (currentTrack != null) {
                int newIndex = shuffledQueue.indexOf(currentTrack);
                if (newIndex >= 0) {
                    if (newIndex != currentIndex) {
                        Collections.swap(shuffledQueue, currentIndex, newIndex);
                    }
                }
            }
        }
        
        notifyQueueChanged();
    }
    
    public void setQueueFromPlaylist(Playlist playlist) {
        clear();
        if (playlist != null && playlist.getTracks() != null) {
            addAll(playlist.getTracks());
        }
    }
    
    public Playlist createPlaylistFromQueue(String name, String description) {
        Playlist playlist = new Playlist(name, 0, 0);
        playlist.setDescription(description);
        playlist.setTracks(new ArrayList<>(queue));
        return playlist;
    }
    
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    public int size() {
        return queue.size();
    }
    
    public List<Track> getQueue() {
        return new ArrayList<>(queue);
    }
    
    public List<Track> getHistory() {
        return new ArrayList<>(history);
    }
    
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }
    
    public boolean hasNext() {
        return currentIndex < (isShuffled ? shuffledQueue.size() : queue.size()) - 1;
    }
    
    private void notifyQueueChanged() {
        if (listener != null) {
            listener.onQueueChanged();
        }
    }
    
    private void notifyCurrentTrackChanged(Track track) {
        if (listener != null) {
            listener.onCurrentTrackChanged(track);
        }
    }
}