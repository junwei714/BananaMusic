# BananaMusic

BananaMusic is a modern Android music player application that offers a seamless music listening experience with features similar to popular streaming services.

## Features

### Music Playback
- High-quality audio playback
- Queue management with drag-and-drop reordering
- Play history tracking
- Background playback support
- Mini player with quick controls

### Playlist Management
- Create and manage personal playlists
- Collaborative playlists support
- Add/remove tracks from playlists
- Playlist mood categorization
- Save queue as playlist

### User Interface
- Modern Material Design implementation
- Dark theme optimized for OLED displays
- Smooth animations and transitions
- Bottom navigation for easy access
- Responsive layout supporting various screen sizes

### Library Organization
- Browse by playlists, artists, and tracks
- Search functionality
- Recently played tracks
- Trending music section

## Technical Requirements

- Android 6.0 (API level 23) or higher
- Internet connection for streaming features
- Google Play Services
- ~50MB free storage space

## Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/BananaMusic.git
```

2. Open the project in Android Studio

3. Sync project with Gradle files

4. Build and run the application

## Dependencies

- AndroidX Core and AppCompat
- Material Design Components
- Firebase Authentication and Firestore
- Glide for image loading
- ExoPlayer for audio playback
- RecyclerView for list displays
- SwipeRefreshLayout for pull-to-refresh
- Shimmer for loading animations

## Architecture

The app follows MVVM (Model-View-ViewModel) architecture pattern and uses:
- Repository pattern for data management
- LiveData for observable data holders
- ViewBinding for view access
- Safe Fragment handling
- Dependency injection principles

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Material Design for Android
- Firebase team for backend services
- ExoPlayer team for audio functionality
- All contributors and testers

## Contact

Your Name - [@yourtwitter](https://twitter.com/yourtwitter)

Project Link: [https://github.com/yourusername/BananaMusic](https://github.com/yourusername/BananaMusic)

## Screenshots

[Add screenshots of your app here]

## Roadmap

- [ ] Add offline mode support
- [ ] Implement audio equalizer
- [ ] Add crossfade between tracks
- [ ] Support for podcasts
- [ ] Integration with more streaming services
- [ ] Social features and sharing
- [ ] Custom themes support 