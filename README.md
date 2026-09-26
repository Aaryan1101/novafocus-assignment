# Alphabet Launcher

Alphabet Launcher is a Kotlin and Jetpack Compose launcher assignment. It loads the device's real launcher activities, presents installed apps on a quiet system-themed home screen, and reveals letter-filtered results while the user drags across the right-side alphabet.

## Features

- Real installed launcher applications via `ACTION_MAIN` and `CATEGORY_LAUNCHER`
- Cached, background application loading with bitmap icons
- Case-insensitive label sorting and A-Z grouping
- Exact launcher-component launching with failure feedback
- Responsive home screen with live clock, date, and installed-app shortcuts
- Continuous Gaussian alphabet curve driven by finger position
- Finger-tracked letter bubble, dimmed empty letters, and a clear empty-letter state
- Scrollable letter results so every matching installed app remains reachable
- Haptic feedback when the selected letter changes
- Spring release that straightens the sidebar and restores the clock and favourites
- Finger-tracked swipe-up search with automatic keyboard focus
- Fluid spring reveal, cancellation, back retreat, and bidirectional empty-search swipe return
- Persistent long-press favourites with launcher-tracked recent-app fallback
- Optional Android home-screen registration through `CATEGORY_HOME`
- Automatic app-list refresh after package additions, removals, changes, or replacements
- System light and dark theme support, including matching system-bar appearance
- Draw-phase gesture tracking and lightly overshooting spring motion
- Loading and retryable error states

## Screenshots

Submission screenshots and recordings are not bundled. The ignored `review-artifacts/` directory is reserved for final evidence captured from the test phone.

## Requirements

- Android Studio with SDK 36 or a compatible command-line Android build environment
- JDK 17
- Android device/API 26 or newer for runtime use

## Build

From the project directory:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew compileReleaseKotlin
./gradlew assembleBenchmark # signed, non-debuggable local performance build
```

The `benchmark` build type uses release runtime behavior with local debug signing so frame performance can be measured on a phone without creating production signing credentials. It is a local validation artifact, not a production release.

The local environment used for verification had two conflicting SDK environment variables. Commands were run with `ANDROID_SDK_ROOT` unset so `ANDROID_HOME` selected `/home/potato/Android/Sdk`.

## Architecture

`AppRepository` owns `PackageManager` access and runs discovery on `Dispatchers.IO`. `LauncherViewModel` exposes immutable loading state and keeps the loaded application list out of composables. `LauncherLogic.kt` contains framework-free grouping, sorting, touch mapping, and curve mathematics. `MainActivity.kt` renders the home/results UI and keeps the pointer-critical curve calculation local to the alphabet canvas.

Multiple launcher activities are retained when their `ComponentName` differs. Exact duplicate component results are removed. This launcher excludes its own package from the displayed list.

## Android 11 package visibility

The manifest declares a `<queries>` entry for `ACTION_MAIN` plus `CATEGORY_LAUNCHER`. No `QUERY_ALL_PACKAGES` permission is used.

## Favourites and recent apps

Long-press an app on the home screen, in a letter result, or in search to add or remove it from Favourites. Favourite component names are stored locally in `SharedPreferences` and survive restarts. When no explicit favourites exist, the home prioritizes apps most recently launched through Alphabet Launcher and fills remaining positions with stable alphabetic suggestions. It intentionally does not request Android Usage Access or inspect activity from other launchers.

## Curve algorithm

Each fixed-width sidebar glyph keeps its normal vertical center. During a drag, its horizontal displacement is the negative of a Gaussian bell curve evaluated at its distance from the continuous finger Y position. The spacing-scaled width makes the selected glyph move most, nearby glyphs move progressively less, and distant glyphs return close to their resting X coordinate. On release, the content returns to the clock and favourites while the curve follows a lightly overshooting spring back to rest.

## Performance and caching

Package discovery, label sorting, grouping, Compose image conversion, and size-capped icon conversion happen during repository loading, outside gesture callbacks; discovery runs again only after an explicit retry or package-change broadcast. Continuous finger and spring state is read only in Canvas or graphics-layer phases, so raw pointer samples do not recompose or re-layout either screen. Filtered results use one scrollable Canvas rather than rebuilding a lazy row layout at each letter boundary. Search is precomposed and revealed through render transforms. There is no package query, disk access, full-list filtering, or per-move coroutine in either drag path.

On the documented Samsung test phone, a warmed four-swipe trace of the non-debuggable benchmark build rendered 488 frames with 1.64% deadline misses, a 6 ms median, 11 ms p90, and 14 ms p95 at a 90 Hz display mode. These measurements describe the local test device rather than a universal guarantee.

The final bonus-complete build was also exercised on an older Samsung SM-T385 running API 28. Its warmed synthetic six-swipe trace rendered 356 frames with 16 frame-deadline misses, a 12 ms median, 23 ms p90, and 42 ms p95. ADB-generated input produced high input-latency counters on that device, while slow bitmap uploads and slow draw-command counts remained zero.

## Dependencies

Production dependencies:

| Dependency | Version | Reason |
|---|---:|---|
| Android Gradle Plugin | 8.13.1 | Android build tooling |
| Kotlin Gradle Plugin | 2.2.20 | Kotlin compilation |
| Compose compiler plugin | 2.2.20 | Compose compiler integration |
| `androidx.core:core-ktx` | 1.17.0 | Android Kotlin extensions |
| `androidx.activity:activity-compose` | 1.10.1 | Compose activity integration |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.2 | Lifecycle-aware state collection |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.2 | ViewModel access from Compose |
| Compose BOM | 2025.08.00 | Aligned Compose versions |
| Compose UI, graphics, preview, foundation, Material 3 | BOM-managed | UI and drawing |

Test dependencies:

| Dependency | Version | Reason |
|---|---:|---|
| `junit:junit` | 4.13.2 | Pure JVM logic tests |

## Testing and limitations

Pure tests cover sidebar selection and clamping, letter centers, grouping edge cases, case-insensitive sorting, curve symmetry, falloff, and distant-letter behavior. Samsung SM-M136B and SM-T385 devices were used for installation, interaction checks, and frame timing. HOME-role discovery, light and dark theme rendering, release-to-home, search, favourite-dialog behavior, haptics, and real app launching were verified on the API 28 tablet. Package install/removal refresh, multi-touch scrolling while holding the sidebar, wider device/font-scale matrices, and the final submission recording remain outside the physically verified scope.

## AI disclosure

AI assistance was used for planning, implementation review, debugging, documentation refinement, and adapting the supplied calligraphic reference into the launcher foreground artwork. The application architecture, curve behavior, testing, and final implementation were reviewed and understood by the developer.
