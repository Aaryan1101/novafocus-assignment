# Alphabet Launcher

Alphabet Launcher is a Kotlin and Jetpack Compose launcher-style assignment. It loads the device's real launcher activities, presents installed apps as a quiet black home screen, and reveals letter-filtered results while the user drags across the right-side alphabet.

## Features

- Real installed launcher applications via `ACTION_MAIN` and `CATEGORY_LAUNCHER`
- Cached, background application loading with bitmap icons
- Case-insensitive label sorting and A-Z grouping
- Exact launcher-component launching with failure feedback
- Responsive black home screen with live clock, date, and installed-app shortcuts
- Continuous Gaussian alphabet curve driven by finger position
- Selected-letter bubble, empty-letter state, and scrollable result list
- One haptic tick per selected-letter change
- Loading and retryable error states

Search, editable persistent favourites, package-change refresh, theme switching, and default-home registration are intentionally not included in this minimal verified build.

## Screenshots

Screenshots and recordings were not produced in this environment because no Android device or emulator was verified. The ignored `review-artifacts/` directory is reserved for them.

## Requirements

- Android Studio with SDK 36 or a compatible command-line Android build environment
- JDK 17
- Android device/API 26 or newer for runtime use

## Build

From the project directory:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew compileReleaseKotlin
```

The local environment used for verification had two conflicting SDK environment variables. Commands were run with `ANDROID_SDK_ROOT` unset so `ANDROID_HOME` selected `/home/potato/Android/Sdk`.

## Architecture

`AppRepository` owns `PackageManager` access and runs discovery on `Dispatchers.IO`. `LauncherViewModel` exposes immutable loading state and keeps the loaded application list out of composables. `LauncherLogic.kt` contains framework-free grouping, sorting, touch mapping, and curve mathematics. `MainActivity.kt` renders the home/results UI and keeps the pointer-critical curve calculation local to the alphabet canvas.

Multiple launcher activities are retained when their `ComponentName` differs. Exact duplicate component results are removed. This launcher excludes its own package from the displayed list.

## Android 11 package visibility

The manifest declares a `<queries>` entry for `ACTION_MAIN` plus `CATEGORY_LAUNCHER`. No `QUERY_ALL_PACKAGES` permission is used.

## Curve algorithm

Each letter keeps its normal vertical center. During a drag, its horizontal displacement is the negative of a Gaussian bell curve evaluated at its distance from the continuous finger Y position. The spacing-scaled width makes the selected letter move most, nearby letters move progressively less, and distant letters return close to their resting X coordinate.

## Performance and caching

Package discovery, label sorting, grouping, and icon conversion happen once during repository loading, outside gesture callbacks. Dragging only maps Y to an index and evaluates 26 inexpensive Gaussian values. There is no package query, disk access, full-list filtering, or per-move coroutine in the drag path.

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

Pure tests cover letter centers, clamping, grouping edge cases, case-insensitive sorting, curve symmetry, falloff, and distant-letter behavior. No real device, emulator, screenshot, recording, performance profiler, package install/removal flow, system navigation mode, font-scale matrix, or default-home flow was verified here.

## AI disclosure

AI assistance was used for planning, implementation review, debugging suggestions, and documentation refinement. The application architecture, curve behavior, testing, and final implementation were reviewed and understood by the developer.
