# Changelog

All notable changes to Chroma Flow are listed here. Versions follow `versionName` in `app/build.gradle.kts`.

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
