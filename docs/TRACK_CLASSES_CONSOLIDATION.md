# Track Classes Consolidation

## Overview

The project previously had two different `Track` classes in different packages:

1. `my.edu.utar.bananamusic.model.Track` - Used in mood recommendations and classification
2. `my.edu.utar.bananamusic.models.Track` - Used across most of the application

This created confusion and potential bugs when passing Track objects between different components of the application. We've consolidated these classes into a single, enhanced `Track` class in the `models` package.

## Changes Made

1. Enhanced `models.Track` with extensive functionality:
   - Added source constants (SPOTIFY, YOUTUBE, LOCAL, etc.)
   - Added fields for multiple use cases:
     - Basic track info (id, title, artist, album)
     - Media URLs (previewUrl, youtubeUrl, pipedUrl, soundCloudUrl)
     - Metadata (releaseDate, genres, type, source)
     - Audio features (danceability, energy, tempo, valence, acousticness)
   - Added multiple getters and setters for all field variations
   - Added utility methods (toString, equals, hashCode, getFormattedDuration)
   - Added convenience constructors for various use cases
   - Added compatibility methods for backward compatibility

2. Created a `TrackConverter` utility class to help during the transition period:
   - Provides methods to convert between old and new Track models
   - Has utility methods to validate Track objects
   - Uses fully qualified class names to avoid import conflicts
   - This class can be removed once the migration is complete

3. Updated key classes to use the consolidated `models.Track`:
   - `MoodRecommendationsViewModel`
   - `MoodRecommendationsFragment`
   - `MoodRecommendationsAdapter`
   - `MoodClassifierService`

## Usage Guidelines

1. **Constructing a Track**:
   ```java
   // Basic track
   Track track = new Track(
       "1", "Song Title", "Artist", "Album",
       "https://example.com/albumart.jpg", 
       180000, // duration in ms
       "https://example.com/preview.mp3",
       true // is playable
   );

   // Track with resource ID (mainly for UI)
   Track track = new Track(
       "Song Title",
       "Artist",
       "Album", 
       "3:30", // duration as string
       R.drawable.album_art // resource ID
   );
   ```

2. **Setting Additional Properties**:
   ```java
   // Set source
   track.setSource(Track.SOURCE_SPOTIFY);
   
   // Set type
   track.setType("album");
   
   // Set streaming URLs
   track.setYoutubeUrl("https://youtube.com/watch?v=xyz");
   track.setPipedUrl("https://piped.example.com/xyz");
   
   // Set audio features
   track.setDanceability(0.8f);
   track.setEnergy(0.7f);
   track.setValence(0.9f);
   ```

3. **Compatibility Methods**:
   The class has multiple methods for the same property for backward compatibility. For example:
   - `getId()` and `getTrackId()` both return the track ID
   - `getAlbumArtUrl()`, `getAlbumArt()`, and `getImageUrl()` all return the album art URL 

## Future Work

1. Once all code is migrated to use the enhanced `models.Track`, the `model.Track` class can be safely removed.

2. References to the `TrackConverter` utility can also be removed once the migration is complete.

3. Consider adding more robust validation for Track objects, especially when deserializing from external sources.

4. Review and standardize naming conventions for fields and methods (e.g., decide whether to use `getTitle()` vs `getName()` as the primary method).

## Recommendations

1. Continue to transition all other components to use the consolidated `models.Track` class.

2. Update documentation and API references to reflect the consolidated Track model.

3. Add unit tests to ensure that all methods of the enhanced Track class work as expected.

4. Review third-party integrations that may be expecting the old model structure.

## Questions?

If you have any questions about the consolidation or encounter any issues, please contact the development team. 