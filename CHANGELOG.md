# Changelog

All notable changes to Chroma Flow are listed here. Versions follow `versionName` in `app/build.gradle.kts`.

## [Unreleased]

### Added
- Touch shapes (Play options): play with Hearts, Bubbles, Stars or Sparkles instead of fluid. Only the chosen shapes appear (no fluid), auto-stir drops them too, they follow Symmetry, and Tap to splash pops a burst. Shape colors: the fluid's own colors (default), Rainbow, or one color you pick.
- iPhone and iPad app in `ios/`, using the same fluid engine (no live wallpaper there: iOS doesn't allow live wallpapers).

### Changed
- Play options (symmetry, tilt, auto-stir, tap to splash) fold away like Fine-tune, with a one-line summary of what's on.
- Fine-tune shows an "Edited" tag when a style differs from how the app came, and Reset only works then.
- On iPhone the hints say "Swipe in from the left edge" instead of "Swipe back".
- Every style's "Reset to default" button is red and asks for a second tap (it turns fully red first), like Reset everything.
- The credit now reads "Credits to Kothi (🐒)", with the monkey still swinging.

## [1.3.4] - 2026-11-15

### Added
- Live wallpaper. Settings → Set up live wallpaper turns the menu into the wallpaper setup: pick the style, colors and Fine-tune with a live preview, plus symmetry, quality and smoothness (battery saver 30 fps or smooth 60 fps), then tap Set as wallpaper and choose home screen, lock screen or both (the app asks every time). The home and lock screen keep their own looks. Once Chroma Flow is the wallpaper, changes apply straight away; the phone's wallpaper screen only opens to set it the first time. On phones that put one wallpaper on both screens (like OnePlus), it shows the lock look while locked and the home look once unlocked. On the home screen it stirs itself while you're not touching it (this can be turned off), pauses while you swipe so your strokes take over, and picks up again after you let go. On the lock screen it always stirs itself.
- A Settings screen behind the gear at the top of the menu, with Live wallpaper, Quality, Reset everything, the credits, and the Website, Privacy policy and Source code links.

### Changed
- Quality, Reset everything, the credits and the links moved from the menu to Settings. The "i" credits button is replaced by the Settings gear.
- Back now also closes Settings and the wallpaper setup.
- Every style now starts with Glow at 10% (it was up to 45%), so colors look clean instead of hazy. Ink keeps no glow. Glow you changed yourself is kept.

## [1.3.3] - 2026-11-02

### Added
- A Website link (chromaflow.vercel.app) at the bottom of the menu, beside Privacy policy and Source code.

## [1.3.2] - 2026-11-01

### Added
- "Crafted by Neeraj Reddy" at the end of the menu.

## [1.3.1] - 2026-11-01

### Added
- Privacy policy and source code links at the bottom of the menu. They open in the phone's browser.

### Changed
- Package name for Google Play is now `com.neerajreddy.chromaflow`.
- Release builds are signed with the upload key.

## [1.3] - 2026-10-31

### Added
- Credits button ("i") in the top-right corner with an animated rainbow bubble.
- Text follows the phone's font and font-size setting.

### Changed
- New default look: Brightness opens at −100, and every style was retuned so colors stay rich instead of washing out to white.
- Friendlier settings: plain values (`0–100%`, `Off`, `−100…+100`), clearer names with one-line hints, and controls a style doesn't use are hidden.
- Quality moved to its own section.

### Removed
- The Pressure slider.

### Fixed
- The app recovers if Android drops the GPU context while in the background (it used to show a black screen).
- The chosen color source is remembered after a restart.

## [1.2] - 2026-10-31

### Changed
- First pass at friendlier slider labels and values.

## [1.1] - 2026-10-27

### Added
- Home menu with 10 styles, color options, play-mode options and per-style fine-tuning.
