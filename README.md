# MusicApp

A modern, feature-rich Android music player and real-time social platform built with Kotlin, Firebase, and ExoPlayer.

## Overview
MusicApp is a clean, lightweight audio player designed for Android devices, focusing on smooth playback, real-time Cloud Firebase social connectivity, dynamic theme customization, an interactive 10-band graphic equalizer, listening analytics, and local library management.

## Features
- **Real-Time Social Network & Friend Activity**:
  - Connect with friends in real time using Cloud Firestore shared `friendships` collection.
  - Live Friends Activity feed displaying active online status (**Green `#1DB954`** for Online, **Red `#FF5252`** for Offline) and active Now Playing track info.
  - Inbound friend request notifications with Accept & Decline controls and privacy toggles.
  - Mutual two-way friend removal with real-time deletion marker signals.
- **Firebase Authentication & Profile Management**:
  - Multi-provider authentication with Email & Password registration (minimum 8-character password enforcement) and Google Sign-In with automatic account chooser reset.
  - Account-isolated profile picture storage and Base64 data URI cross-device image synchronization with in-memory `LruCache` and `DiffUtil` for zero-flicker real-time rendering.
- **Forced Offline Presence & Privacy Controls**:
  - Manual **User Online Status** switch to force Offline status (**Red `#FF5252`**) across all friends' feeds, overriding background playback.
  - **Allow Friend Requests** privacy switch to control incoming request discovery.
- **Modern Bottom Navigation**: Ergonomic bottom tab navigation bar anchored above system gesture/button bars, with mini-player anchored directly on top.
- **Local Audio Playback**: Mini-player bar and fullscreen player with 60fps waveform seekbar progress, high-contrast controls, and active shuffle/repeat indicators.
- **Dynamic Live Theme System**: 6 custom theme presets (*Monochrome, Forest & Ember, Grenadine & Latte, Velvet Amethyst, Obsidian Crimson, Ocean Abyss*) with real-time palette updates across all screens, bottom sheets, and dialogs.
- **10-Band Graphic Equalizer**: Interactive spline spectrum curve, LoudnessEnhancer post-gain control (-15 dB to +15 dB with 0.0 dB haptic snap), built-in hardware presets, and custom curve saving.
- **15-Second Active Listen Analytics**: Tracks listening duration for *This Week*, *This Month*, and *Today's Listening*. Play counts for top tracks of the week and month require **at least 15 seconds** of active listening, filtering out short skips.
- **Case-Insensitive Live Search**: Instant song and playlist search with theme-adaptive search brackets and clear button.
- **Playlists & Favourites**: Create, rename, delete, and reorder playlists with custom cover art from local storage or Google Photos cloud.
- **Phone Call Ringtone**: Easily assign any local audio track as your default phone call ringtone.
- **Web Album Cover Search**: Integrated online cover art search with web globe icon and metadata editor.

## Tech Stack
- **Language:** Kotlin
- **Backend & Cloud Services:** Firebase Authentication, Cloud Firestore Database
- **UI:** ViewBinding, Custom Canvas Views, Material Design 3, SwipeRefreshLayout, Glide (LRU Memory Cache & Base64 Decoders)
- **Audio Engine:** ExoPlayer (Media3 / ExoPlayer2), Android AudioFX Equalizer & LoudnessEnhancer
- **Build Tool:** Gradle (Kotlin DSL)
- **Minimum SDK:** Android 7.0 (API 24)

## Getting Started
1. Clone the repository:
   ```bash
   git clone https://github.com/Ioannioz/MusicApp.git
   ```
2. Open the project in Android Studio.
3. Let Gradle sync dependencies.
4. Run on an emulator or a connected physical device.
