# MusicApp

A modern, feature-rich Android music player built with Kotlin.

## Overview
MusicApp is a clean, lightweight audio player designed for Android devices, focusing on smooth playback, real-time dynamic theme customization, an interactive 10-band graphic equalizer, listening analytics, and local library management.

## Features
- **Modern Bottom Navigation**: Ergonomic bottom tab navigation bar anchored above system gesture/button bars, with mini-player anchored directly on top.
- **Local Audio Playback**: Mini-player bar and fullscreen player with 60fps waveform seekbar progress, high-contrast controls, and active shuffle/repeat indicators.
- **Dynamic Live Theme System**: 6 custom theme presets (*Monochrome, Forest & Ember, Grenadine & Latte, Velvet Amethyst, Obsidian Crimson, Ocean Abyss*) with real-time palette updates across all screens, bottom sheets, and dialogs.
- **10-Band Graphic Equalizer**: Interactive spline spectrum curve, LoudnessEnhancer post-gain control (-15 dB to +15 dB with 0.0 dB haptic snap), built-in hardware presets, and custom curve saving.
- **Listening Analytics**: Tracks listening duration for *This Week*, *This Month*, and *Today's Listening*, along with top tracks of the week and month.
- **Case-Insensitive Live Search**: Instant song and playlist search with theme-adaptive search brackets and clear button.
- **Playlists & Favourites**: Create, rename, delete, and reorder playlists with custom cover art from local storage or Google Photos cloud.
- **Phone Call Ringtone**: Easily assign any local audio track as your default phone call ringtone.
- **Web Album Cover Search**: Integrated online cover art search and metadata editor.

## Tech Stack
- **Language:** Kotlin
- **UI:** ViewBinding, Custom Canvas Views, Material Design 3, SwipeRefreshLayout
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
