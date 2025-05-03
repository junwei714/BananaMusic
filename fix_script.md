# Guide to Fix Callback Interface Issues in BananaMusic

## Problem Overview

BananaMusic has multiple callback interfaces with similar purposes but different method names:

1. `my.edu.utar.bananamusic.callbacks.TracksCallback`
   - Method: `onTracksLoaded(List<Track> tracks)`
   
2. `my.edu.utar.bananamusic.utils.callbacks.TracksCallback`
   - Method: `onSuccess(List<Track> tracks)`

3. `DeezerHelper.DeezerTracksCallback`
   - Method: `onTracksLoaded(List<Track> tracks)`

Similar issues exist with PlaylistCallback, PlaylistsCallback, and OperationCallback interfaces.

## Solution Approach

We've created a `CallbackAdapter` utility class to convert between these interfaces. Here's how to use it:

### 1. Import the CallbackAdapter

```java
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter.PlaylistCallbackImpl;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter.PlaylistsCallbackImpl;
import my.edu.utar.bananamusic.utils.callbacks.CallbackAdapter.OperationCallbackImpl;
```

### 2. Fix Method Calls Using the Wrong Callback Interface

#### When Passing a TracksCallback to a Method Expecting DeezerTracksCallback:

```java
// BEFORE:
deezerHelper.searchAndConvertToTracks(query, new TracksCallback() {
    @Override
    public void onSuccess(List<Track> tracks) {
        // handle results
    }
    @Override
    public void onError(String errorMessage) {
        // handle error
    }
});

// AFTER:
deezerHelper.searchAndConvertToTracks(query, CallbackAdapter.toOriginalTracksCallback(new TracksCallback() {
    @Override
    public void onSuccess(List<Track> tracks) {
        // handle results
    }
    @Override
    public void onError(String errorMessage) {
        // handle error
    }
}));
```

#### When Passing a PlaylistCallback to PlaylistManager:

```java
// BEFORE:
playlistManager.addToLibrary(playlist, new PlaylistCallback() {
    @Override
    public void onSuccess(Playlist playlist) {
        // handle success
    }
    @Override
    public void onError(String message) {
        // handle error
    }
});

// AFTER:
playlistManager.addToLibrary(playlist, CallbackAdapter.createPlaylistManagerCallback(new PlaylistCallbackImpl() {
    @Override
    public void onSuccess(Playlist playlist) {
        // handle success
    }
    @Override
    public void onError(String message) {
        // handle error
    }
}));
```

#### When Passing a PlaylistsCallback to PlaylistManager:

```java
// BEFORE:
playlistManager.getUserPlaylists(new PlaylistsCallback() {
    @Override
    public void onSuccess(List<Playlist> playlists) {
        // handle results
    }
    @Override
    public void onError(String message) {
        // handle error
    }
});

// AFTER:
playlistManager.getUserPlaylists(CallbackAdapter.createPlaylistManagerPlaylistsCallback(new PlaylistsCallbackImpl() {
    @Override
    public void onSuccess(List<Playlist> playlists) {
        // handle results
    }
    @Override
    public void onError(String message) {
        // handle error
    }
}));
```

### 3. Additional Fixes

For files with compilation errors related to PlaylistManager methods that don't exist (like getTrendingPlaylists()), you may need to implement these methods in the PlaylistManager class.

## Full List of Available Adapters

- `CallbackAdapter.toOriginalTracksCallback()`
- `CallbackAdapter.fromOriginalTracksCallback()`
- `CallbackAdapter.toDeezerTracksCallback()`
- `CallbackAdapter.toPlaylistManagerCallback()`
- `CallbackAdapter.fromPlaylistManagerCallback()`
- `CallbackAdapter.toPlaylistManagerPlaylistsCallback()`
- `CallbackAdapter.fromPlaylistManagerPlaylistsCallback()`
- `CallbackAdapter.toPlaylistManagerOperationCallback()`
- `CallbackAdapter.fromPlaylistManagerOperationCallback()`
- `CallbackAdapter.toSpotifyRecommendationsCallback()`
- `CallbackAdapter.fromSpotifyRecommendationsCallback()`

### Inline Interfaces

For simpler implementation, you can also use these inline interfaces:
- `CallbackAdapter.PlaylistCallbackImpl`
- `CallbackAdapter.PlaylistsCallbackImpl`
- `CallbackAdapter.OperationCallbackImpl` 