# Mood Recommendation Feature

This document explains how mood recommendations work in BananaMusic and the recent improvements that have been made to enhance reliability, diversity, and accuracy.

## Overview

The mood recommendation feature allows users to get music recommendations based on their emotional state or desired mood. The system maps moods to specific audio features and genres that match those emotional states.

## Key Components

1. **ApiDataProvider**: Entry point for mood recommendations that calls the SpotifyHelper
2. **SpotifyHelper**: Handles Spotify API communication and recommendation logic
3. **Fallback mechanisms**: Multiple layers of fallbacks to ensure users always get recommendations

## Recent Improvements

### 1. Enhanced Genre Mapping

- Added a comprehensive mood-to-genre mapping using Spotify's available genres
- Increased genre diversity for more varied recommendations
- Implemented intelligent "related mood" detection to handle non-standard mood inputs
- Added de-duplication and proper genre selection logic

### 2. Better Error Handling

- Specific handling for different HTTP status codes (400, 401, 404, 429, 500, etc.)
- Improved token refresh mechanism to reduce authentication errors
- Better logging for easier debugging of API issues

### 3. Caching Strategy

- Added caching for recommendations to reduce API calls
- Implemented cache invalidation with appropriate timeouts
- Added fallback to cached results when API calls fail

### 4. Expanded Mood Support

Added support for additional moods:
- Chill
- Workout
- Party
- Sleep

Each mood has specific audio feature mappings and genre seeds for better targeting.

### 5. Improved Parameter Handling

- Input validation for all parameters
- Random variation in audio features to increase variety
- Added acousticness and instrumentalness parameters for more precise mood matching

## How It Works

1. When a user requests recommendations for a mood, `getMoodRecommendations()` is called in ApiDataProvider
2. This calls SpotifyHelper's `getMoodBasedRecommendations()` method
3. The method:
   - Maps the mood to appropriate audio features (energy, valence, etc.)
   - Gets genre seeds that match the mood
   - Requests recommendations from Spotify's API
   - If successful, returns the tracks
   - If unsuccessful, tries alternative approaches

## Fallback Chain

The system has multiple fallback mechanisms to ensure users always get recommendations:

1. Primary recommendation API call with all audio features
2. Simplified recommendation call with fewer parameters
3. Playlist search based on mood terms
4. Backup search with more generic terms
5. Hardcoded high-quality fallback tracks for each mood

## Testing and Debugging

When testing mood recommendations:
- Check logs for "Selected seed genres for mood" to see which genres are being used
- Look for "Requesting mood recommendations" to see the full API URL
- Error responses will include detailed information on what failed and why

## Known Limitations

- Spotify's recommendations API occasionally returns fewer tracks than requested
- Some genres may not be recognized by Spotify's API
- Highly specific moods might fall back to more generic recommendations 