# Contributing to Chroma Flow

Thanks for wanting to help! Chroma Flow is a small, open source fluid art app for Android, and all kinds of contributions are welcome: bug reports, ideas, new styles, fixes and documentation.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug.** [Open a bug report](https://github.com/Neerajreddyapi/ChromaFlow/issues/new?template=bug_report.yml). Include your phone model and Android version.
- **Suggest an idea.** [Open a feature request](https://github.com/Neerajreddyapi/ChromaFlow/issues/new?template=feature_request.yml).
- **Fix or build something.** Pick an open issue (look for the `good first issue` and `help wanted` labels) or propose your own change.
- **Improve the docs.** Typos, clearer steps and better screenshots all count.

Found a security problem? Please **don't** open a public issue. See [SECURITY.md](SECURITY.md).

## Before you start

- **Small fixes** (typos, obvious bugs): just open a pull request.
- **Bigger changes** (new features, new styles, UI changes): open an issue first and describe what you'd like to do. That way we can agree on the approach before you spend time on it.
- Check the [open issues](https://github.com/Neerajreddyapi/ChromaFlow/issues) and [pull requests](https://github.com/Neerajreddyapi/ChromaFlow/pulls) so you don't duplicate someone else's work. Comment on an issue to say you're taking it.

## Project principles

Every change has to keep these promises. Pull requests that break them won't be merged.

1. **Private by design.** No internet permission, no analytics, no ads, no tracking, no accounts. The app requests **no Android permissions**.
2. **No third-party libraries.** The app is plain Java plus one HTML file with WebGL. Keep it that way.
3. **Play mode has zero UI.** Nothing is drawn on top of the fluid while playing. All options live on the home screen.
4. **Runs on modest phones.** Test on the Low and Medium quality settings, not only High and Ultra.
5. **Friendly settings.** Sliders show plain values (`0–100%`, `Off`, `−100…+100`), not engine numbers, and each label explains itself.

## Development workflow

### 1. Set up your environment

Follow [Development setup](README.md#development-setup) in the README. In short: install Android Studio, clone your fork, open it and press Run.

### 2. Fork and branch

```sh
# Fork on GitHub first, then:
git clone https://github.com/<your-username>/ChromaFlow.git
cd ChromaFlow
git remote add upstream https://github.com/Neerajreddyapi/ChromaFlow.git

# Start each change from an up-to-date master
git fetch upstream
git checkout -b fix/short-description upstream/master
```

Branch names: `fix/…` for bug fixes, `feature/…` for new features, `style/…` for new fluid styles, `docs/…` for documentation.

### 3. Make your change

- **Visuals, styles, settings and menu:** `app/src/main/assets/index.html`
- **Android behaviour** (full screen, Back gesture, keep-screen-on, font size, opening links): `app/src/main/java/com/chromaflow/app/MainActivity.java`
- Match the existing code style. Keep changes focused: one fix or feature per pull request.
- Don't change `applicationId`, `versionCode` or `versionName`. The maintainer bumps these when releasing.

#### Adding a new style

1. Add an entry to the `STYLES` list in `index.html`. Pick a `mode` (`0` glow, `1` heat, `2` waves, `3` chrome, `4` ink, `5` water), a background, a 4-color `palette`, and override only the defaults it needs (`amt`, `curl`, `dye`, `vel`, `bloom`, …).
2. Add a short tagline for it to `TAGLINES`.
3. Check its swatch in the menu. `swatchCss()` picks one based on `mode`.
4. Tune it **at the default brightness (−100)** so colors look rich without washing out to white, then check it at +100.

### 4. Test it

There are no automated UI tests, so please check by hand on a real phone or an emulator before opening a pull request:

- [ ] The app opens to the menu, and **Play** starts the fluid.
- [ ] Swipe **Back** during play returns to the menu, and Back on the menu closes the app.
- [ ] Every style you touched looks right at **Low** and **High** quality.
- [ ] Settings survive closing and reopening the app.
- [ ] **Reset everything** puts everything back to the defaults.
- [ ] The menu still fits with a large system font size (Settings → Display → Font size).
- [ ] Nothing new appears on screen during play.
- [ ] It builds: `./gradlew assembleDebug` (CI also checks this on every pull request).

For quick visual tweaks, you can open `app/src/main/assets/index.html` in desktop Chrome before testing on a phone.

### 5. Commit

Write short commit messages in the imperative mood, with a blank line and more detail if needed:

```
Add Sunset style

Warm orange-to-purple glow with slow rise. Tuned at default brightness.
```

### 6. Open a pull request

```sh
git push origin fix/short-description
```

Then open a pull request against `master` on GitHub. The pull request template will ask for:

- what changed and why (link the issue: `Fixes #12`)
- how you tested it (phone model and Android version)
- **before and after screenshots or a short video** for anything visual

A maintainer will review it, may ask for changes, and will merge it once it's ready. Please be patient; this is a side project.

## Releases (maintainers)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add the changes to [CHANGELOG.md](CHANGELOG.md).
3. Build the signed bundle with `./gradlew bundleRelease`. This needs the private upload key in `keystore.properties`, which is never committed.
4. Upload it in Play Console and tag the release: `git tag v1.x.y && git push --tags`.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE), the same license as the project.
