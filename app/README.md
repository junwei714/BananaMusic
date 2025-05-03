# Music Search Feature Implementation

This feature implements a music search functionality that allows users to search for songs and play 30-second previews.

## Key Components

1. **Search Interface**
   - Users can enter a search query in the search box
   - Results are displayed in a RecyclerView with song title, artist name, and play button
   - The UI provides visual feedback on loading, no results, and playback state

2. **API Integration**
   - **Spotify Web API**: Used for initial search with Client Credentials Flow authentication
   - **Deezer API**: Used to fetch 30-second preview URLs

3. **Playback**
   - MediaPlayer handles audio playback
   - Play/pause functionality on each search result
   - Only one track plays at a time

## Implementation Details

### Authentication
- The app uses Spotify's Client Credentials Flow
- Client ID and Secret are stored in `strings.xml`
- Token is automatically requested and refreshed as needed

### Search Process
1. User input is debounced to prevent excessive API calls
2. App authenticates with Spotify if needed
3. Search query is sent to Spotify API
4. Results are parsed and displayed
5. For each Spotify track, app queries Deezer to get a preview URL
6. Results are updated as preview URLs become available

### Playback Logic
- Clicking play button on a track loads and plays its preview
- Clicking the same track toggles between play and pause
- Playing a new track automatically stops any currently playing track
- MediaPlayer is properly managed to prevent resource leaks

## Configuration

To use this feature, you need to:

1. Register your app with Spotify Developer Dashboard
2. Get your Client ID and Client Secret
3. Add them to `strings.xml`:
   ```xml
   <string name="spotify_client_id">YOUR_SPOTIFY_CLIENT_ID</string>
   <string name="spotify_client_secret">YOUR_SPOTIFY_CLIENT_SECRET</string>
   ```

## Files Modified

- `SearchFragment.java`: Core implementation of search and playback functionality
- `SearchResultAdapter.java`: Adapter for displaying search results with play/pause functionality
- `item_search_result.xml`: Layout for individual search result
- `fragment_search.xml`: Main search UI layout
- `strings.xml`: Added Spotify API credentials
- `SearchResult.java`: Model for search results
- `Track.java`: Added Spotify and Deezer-specific fields and methods 