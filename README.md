<p align="center"><img src="docs/banner.jpg" alt="Chroma Flow" width="720"></p>

# Chroma Flow

A fluid art app for Android. Touch the screen and swirl living color with your fingertips.

<p>
  <img src="docs/screenshots/menu.jpg" width="160" alt="Home screen">
  <img src="docs/screenshots/lava.jpg" width="160" alt="Lava style">
  <img src="docs/screenshots/ink.jpg" width="160" alt="Ink style">
  <img src="docs/screenshots/ocean.jpg" width="160" alt="Ocean style">
  <img src="docs/screenshots/chrome.jpg" width="160" alt="Chrome style">
</p>

## Features

- **10 styles:** Neon, Smoke, Ocean, Waves, Fire, Ink, Aurora, Lava, Galaxy and Chrome, each with its own look (glow, heat, ripples, liquid metal, ink on paper, water with caustics).
- **Your colors:** use the style's palette, a rainbow, or pick four of your own plus the background.
- **Play mode:** a bare full-screen canvas with nothing on top. Mirror, 4-way and kaleidoscope symmetry, tilt your phone to pour, auto-stir, tap to splash.
- **Fine-tune each style:** brightness, color intensity, glow, swirl, how long color lasts, brush size and more. Each style remembers its own settings.
- **Private by design:** no internet permission, no permissions at all, no ads, no tracking.

## How it works

The whole app is a full-screen WebView over [`app/src/main/assets/index.html`](app/src/main/assets/index.html), which holds the WebGL fluid engine and the menu. [`MainActivity.java`](app/src/main/java/com/chromaflow/app/MainActivity.java) only handles full screen, the Back gesture (play → menu → exit), keep-screen-on while playing, and the system font size.

## Build

Requirements: Android Studio (or JDK 17+) and the Android SDK. No third-party libraries.

```sh
./gradlew assembleRelease   # APK  -> app/build/outputs/apk/release/
./gradlew bundleRelease     # AAB  -> app/build/outputs/bundle/release/
```

Release signing reads `keystore.properties` from the repo root (not committed):

```properties
storeFile=your-upload-key.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Without that file, release builds are signed with the debug key so a fresh clone still builds and installs.

## Privacy

Chroma Flow collects nothing. Read the [privacy policy](https://neerajreddyapi.github.io/ChromaFlow/privacy-policy.html).

## Credits

- Credits to Chinni (Kothi 🐒).
- The fluid solver is adapted from [WebGL Fluid Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation) by Pavel Dobryakov (MIT).
- Title font: [Unbounded](https://github.com/googlefonts/unbounded) (SIL Open Font License 1.1).

## License

[MIT](LICENSE) © 2026 Neeraj Reddy. Third-party notices are included in the license file.
