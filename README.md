# MusicApp

A modern Android music player built with Kotlin.

## Overview
MusicApp is a clean, feature-rich music player designed for Android devices, focusing on smooth playback, dynamic theme customization, graphic equalizer, and local library management.

## Features
- **Local Audio Playback**: Mini-player bar and fullscreen player with waveform seekbar progress.
- **Dynamic Themes**: 6 custom themes (*Monochrome, Forest & Ember, Grenadine & Latte, Velvet Amethyst, Obsidian Crimson, Ocean Abyss*).
- **10-Band Graphic Equalizer**: Interactive spline curve with frequency handles, LoudnessEnhancer post-gain control, built-in presets, and custom preset saving.
- **Listening Stats**: Daily & Weekly active listening duration, top track of the week, and top track of the month.
- **Playlists & Favourites**: Create, rename, delete, reorder songs, and custom playlist cover art.
- **Case-Insensitive Search**: Instant live song and playlist search.
- **Web Album Cover Picker**: Integrated online album cover search and metadata editing.

## Tech Stack
- **Language:** Kotlin
- **UI:** ViewBinding, Custom Views, Material Design 3, SwipeRefreshLayout
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
