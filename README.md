# MAGESH DOWNLOADER

An Android video and audio downloader built with **Kotlin**, **Jetpack Compose (Material 3)**, **Media3 ExoPlayer**, and **[youtubedl-android](https://github.com/yausername/youtubedl-android)** (`yt-dlp` + **FFmpeg**).

## Features

- **Single-Screen Clean UI**: Paste link field with one-tap **Paste** and **Fetch** buttons.
- **Supported Platforms**: Public links from **YouTube**, **Instagram**, and **Facebook** (plus other `yt-dlp` supported public URLs).
- **In-App Preview**: Displays the video thumbnail, title, uploader, duration, and an integrated **Media3 ExoPlayer** preview.
- **All Resolutions & Audio-Only**:
  - Lists available video resolutions (`144p` through `1080p` / `4K` MP4, merged automatically with FFmpeg when needed) with file sizes.
  - Provides **Audio-Only** options (**MP3** and **M4A**) extracted via FFmpeg, with estimated sizes.
- **Background Downloads & Notifications**:
  - Saves files directly to `Downloads/MAGESH DOWNLOADER`.
  - Shows a live progress bar, ETA, persistent Android notification, and **Cancel** button (both in-app and in the notification).
- **Share Intent Auto-Detect**: Share any link from YouTube, Instagram, or Facebook directly to **MAGESH DOWNLOADER** to auto-fill and fetch formats.
- **Android 7.0 – 14 Support**: `minSdk 24`, `targetSdk 34`, handling Scoped Storage (`Downloads/`), legacy storage (`Android 10`), and runtime `POST_NOTIFICATIONS` (`Android 13–14`).

## Project Setup & Cloud Build (GitHub Actions)

This repository includes `.github/workflows/build.yml`, which automatically builds a debug APK on every push to `main`/`master` or manual workflow dispatch:

1. Push this project to a GitHub repository.
2. Open the **Actions** tab on GitHub and run **Build Debug APK** (or push a commit to `main`).
3. Download the compiled `MAGESH-DOWNLOADER-debug-apk` (`app-debug.apk`) from the workflow run's **Artifacts** section.

## Tech Stack

- **Language**: Kotlin 1.9
- **UI**: Jetpack Compose, Material 3, Coil
- **Media Preview**: AndroidX Media3 ExoPlayer
- **Downloader Engine**: `io.github.junkfood02.youtubedl-android:library:0.18.1` & `ffmpeg:0.18.1`
- **Build**: Gradle 8.7, AGP 8.5.2, JDK 17
