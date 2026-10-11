# Scrape Stack

A tactile Android toy: the screen is covered in a bright texture (lava, wood, ice, frosting, galaxy…) and you scrape it away with your finger to reveal the next layer underneath. Clear six layers to uncover a treasure layer.

Everything is procedural — textures are drawn at runtime and all sounds are synthesized on startup — so there are no image or audio assets. Plain Java, a single custom `View`, no AndroidX, no Gradle.

## Features

- **15 layer textures** chosen so each layer contrasts strongly with the one above it, plus 3 treasure layers (gold, gems, rainbow).
- **6 tools**, each with its own effect, particles, sound and haptics:

  | Tool | Effect |
  |---|---|
  | Scraper | ragged stroke, flings flakes |
  | Spoon | scoops big blobs |
  | Rake | combs five parallel furrows |
  | Blow dryer | pushes material along an air cone and off the screen edge |
  | Power washer | hold to drill through, sweep to wash |
  | Hammer | smashes cracked craters with screen shake |

- Sound and vibration only fire while you're actually removing material.
- A layer clears at ~95% removed; leftovers fade with a chime and confetti.
- On-screen controls fade out while scraping.
- **Resume** restores the exact half-scraped layer, even after the app is killed.
- **Home** and **lock** buttons need a 2-second hold. The lock uses Android app pinning so Home/Back/notification gestures can't kick you out.

## Install

Grab a built `ScrapeStack.apk`, copy it to an Android 8.0+ phone and open it (allow installs from that source when prompted).

## Build

Requirements:

- JDK 17+
- Android SDK with `build-tools;34.0.0` and `platforms;android-34`
- Bash (Git Bash on Windows)

```bash
JAVA_HOME=/path/to/jdk ANDROID_HOME=/path/to/android-sdk ./build.sh
```

The signed APK is written to `ScrapeStack.apk` in the project root. On first build a local signing key (`scrapestack.keystore`) is generated; it is git-ignored, so each machine signs with its own key. Android won't install an update over an APK signed with a different key — uninstall first, or share the keystore out-of-band.

If you don't have the SDK, the command-line tools can install it:

```bash
sdkmanager --sdk_root=/path/to/android-sdk "platforms;android-34" "build-tools;34.0.0"
```

## Code layout

| File | Purpose |
|---|---|
| `src/.../MainActivity.java` | Fullscreen/immersive window, lifecycle, app pinning |
| `src/.../GameView.java` | Game loop, tools, particles, HUD, menu, dialogs, save/resume |
| `src/.../Textures.java` | Seeded procedural texture generators and stack picking |
| `src/.../ToolArt.java` | Vector glyphs for the toolbar icons and in-hand tool cursors |
| `src/.../Sfx.java` | Sound synthesis → WAV → `SoundPool` |
| `src/.../Haptics.java` | Rate-limited vibration helper |
| `res/` | Adaptive launcher icon |
| `build.sh` | Gradle-free build: aapt2 → javac → d8 → zipalign → apksigner |
