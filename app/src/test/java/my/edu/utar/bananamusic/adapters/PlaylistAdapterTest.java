package my.edu.utar.bananamusic.adapters;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PlaylistAdapterTest {

    @Mock
    private Context mockContext;

    @Mock
    private PlaylistAdapter.OnPlaylistClickListener mockListener;

    @Mock
    private View mockItemView;

    @Mock
    private ImageView mockCoverImageView;

    @Mock
    private TextView mockNameTextView;

    @Mock
    private TextView mockCreatorTextView;

    @Mock
    private TextView mockTrackCountTextView;

    private PlaylistAdapter adapter;
    private List<Playlist> testPlaylists;

    @Before
    public void setup() {
        // Set up mock views
        when(mockItemView.findViewById(R.id.iv_playlist_cover)).thenReturn(mockCoverImageView);
        when(mockItemView.findViewById(R.id.tv_playlist_name)).thenReturn(mockNameTextView);
        when(mockItemView.findViewById(R.id.tv_playlist_creator)).thenReturn(mockCreatorTextView);
        when(mockItemView.findViewById(R.id.tv_track_count)).thenReturn(mockTrackCountTextView);

        // Create test data
        testPlaylists = Arrays.asList(
            createTestPlaylist("1", "Test Playlist 1", "Creator 1"),
            createTestPlaylist("2", "Test Playlist 2", "Creator 2")
        );

        // Initialize adapter
        adapter = new PlaylistAdapter(mockContext);
        adapter.setOnPlaylistClickListener(mockListener);
    }

    @Test
    public void testConstructor() {
        assertNotNull(adapter);
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void testUpdateData() {
        adapter.updateData(testPlaylists);
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void testClearData() {
        adapter.updateData(testPlaylists);
        adapter.clearData();
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void testOnBindViewHolder() {
        adapter.updateData(testPlaylists);
        PlaylistAdapter.PlaylistViewHolder holder = mock(PlaylistAdapter.PlaylistViewHolder.class);
        when(holder.itemView).thenReturn(mockItemView);

        adapter.onBindViewHolder(holder, 0);

        verify(mockNameTextView).setText("Test Playlist 1");
        verify(mockCreatorTextView).setText("Creator 1");
    }

    @Test
    public void testClickListener() {
        adapter.updateData(testPlaylists);
        PlaylistAdapter.PlaylistViewHolder holder = new PlaylistAdapter.PlaylistViewHolder(mockItemView);
        
        // Simulate click
        holder.itemView.performClick();
        
        // Verify listener was called with correct playlist
        verify(mockListener).onPlaylistClick(testPlaylists.get(0));
    }

    private Playlist createTestPlaylist(String id, String name, String creatorName) {
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setName(name);
        playlist.setCreatorName(creatorName);
        return playlist;
    }
} 