# Vidsize (Android, Jetpack Compose)

A from-scratch Android app that resizes videos by **aspect ratio**, **resolution**
(including exact custom dimensions), **trim range**, **rotation**, and **mute**,
with a live **video preview** and a filmstrip trim scrubber. Built dark-mode-first
with a light/system toggle in the top bar, plus a **Studio** history screen. Also
supports a **watermark/logo overlay**, one-tap **social-media export presets**
(TikTok/Reels/Shorts, YouTube, Instagram), and a live export progress % + ETA.

## What it does
1. Pick a video via Android's built-in **Photo Picker** (the same picker UI as
   Photos/Gallery apps use) — `video/*` only.
2. See it right away in an **embedded video preview player** (play/pause/seek)
   plus resolution and duration.
3. **Trim**: a filmstrip strip (8 evenly-spaced thumbnails from the clip) sits
   behind a range slider so you can see roughly what you're cutting, not just
   drag blindly.
4. Tap a **Preset media sosial** chip (TikTok/Reels/Shorts, YouTube, Instagram
   Feed square, Instagram Feed portrait) to instantly set the exact resolution
   and a realistic bitrate for that platform — or skip straight to the manual
   controls below.
5. Choose a target **aspect ratio**: Original, 16:9, 9:16, 1:1, 4:3, 4:5.
6. Choose a target **resolution**: Original, 480p, 720p, 1080p, or **Custom**
   (type an exact width × height in a dialog).
7. Choose a **resize mode**: **Crop** (fill the target box, cropping overflow)
   or **Stretch** (fit exactly, distorting aspect ratio if needed).
8. Choose a **rotation**: 0°, 90°, 180°, 270°.
9. **Mute audio** with a toggle switch.
10. Choose a **Kualitas / bitrate** preset (Original, Rendah, Sedang, Tinggi,
    or Custom kbps) and see a live **estimated output size** before exporting.
11. Add an optional **Watermark / logo**: pick a still image, anchor it to one
    of 4 corners or the center, and adjust its size/opacity with sliders.
12. Tap **Resize video** — re-encodes the clip using
    [`androidx.media3.transformer.Transformer`](https://developer.android.com/media/media3/transformer):
    trimming via `MediaItem.ClippingConfiguration`, resizing/cropping/stretching via a
    `Presentation` effect, rotation via `ScaleAndRotateTransformation`, muting
    via `EditedMediaItem.setRemoveAudio`, bitrate via
    `DefaultEncoderFactory` + `VideoEncoderSettings.setBitrate(...)`, and the
    watermark via `OverlayEffect` + `BitmapOverlay`. A progress bar shows
    live percentage plus a rough remaining-time estimate while it runs.
11. Exported file is published to **Gallery > Movies > VideoResizer**, with a
    **Share / Open** button as a secondary path.

## Studio
Tap the gallery icon in the top bar to open **Studio** — every video you've
resized in this app, each with:
- A thumbnail (first frame of the exported file).
- The aspect ratio / resolution / rotation / mute settings used.
- **Edit ulang**: reopens the original source video on the main screen with
  the same trim range and settings already applied, ready to tweak and
  re-render. This needs the source video to still be reachable. The app
  requests a persistable read permission on the video when it's first picked,
  but **Android's Photo Picker generally does not grant persistable access**
  (by design — it's meant to be a one-time, privacy-scoped grant), so
  "Edit ulang" reliably works within the same app session but may fail after
  the app has been fully closed and reopened. When it fails, you'll see a
  clear message rather than a crash.
- **Share**: re-opens the share sheet for that result.
- **Delete** (trash icon): removes the entry and its backing files.

History and thumbnails live in the app's private cache/SharedPreferences —
clearing the app's storage from Android settings clears Studio's history too,
but anything already published to Gallery > Movies is unaffected either way.

## Dark mode
- `ui/theme/Color.kt` — full dark palette (default) and a light palette.
- `ui/theme/Theme.kt` — `VideoResizerTheme` builds a Material 3 `ColorScheme` from
  either palette, and also colors the system status/navigation bars to match.
- `res/values/themes.xml` + `res/values-night/themes.xml` — pre-Compose window
  background/status bar so there's no light flash before Compose takes over.
- The moon/sun icon in the top app bar lets the user force **Dark**, **Light**,
  or **Follow system**; the app defaults to Dark.

## Opening the project
1. Unzip, open the `VideoResizer` folder in Android Studio (Koala/2024.1+ recommended).
2. Let Gradle sync — it will pull the AndroidX, Compose, and Media3 dependencies
   listed in `app/build.gradle.kts` (no new dependencies were added for the
   quality/bitrate control either — `DefaultEncoderFactory` and
   `VideoEncoderSettings` both live in `media3-transformer`, already a
   dependency).
3. Run on a device/emulator with **API 24+**.

## Building from a phone only (no PC / Android Studio)

The easiest path is to let **GitHub Actions** compile the APK in the cloud, then
download the finished file on your phone.

1. **Create a free GitHub account** (if you don't have one) and, from your phone's
   browser or the GitHub app, create a new empty repository (e.g. `video-resizer`).
2. **Upload this project** into that repo. On a phone the simplest way is:
   - Open the GitHub app or mobile site → your new repo → "Add file" → "Upload files".
   - Unzip `VideoResizer.zip` first (any file manager or a zip app can do this),
     then upload the *contents* of the `VideoResizer` folder (so `build.gradle.kts`,
     `app/`, `.github/`, etc. sit at the repo root — not nested one level deeper).
   - GitHub's web uploader only accepts files in batches without folder structure
     in some apps; if that's a problem, install **Working Copy** (iOS) or
     **Termux + git** (Android) to push the whole folder in one go:
     ```
     cd VideoResizer
     git init
     git remote add origin https://github.com/<you>/video-resizer.git
     git add .
     git commit -m "Initial commit"
     git branch -M main
     git push -u origin main
     ```
3. Once pushed, GitHub Actions runs automatically (see `.github/workflows/build.yml`).
   Check progress under the repo's **Actions** tab.
4. When the run finishes (green check), open that run → scroll to **Artifacts** →
   download **Vidsize-release**. It downloads as a `.zip` containing
   `Vidsize-release.apk` — a **release build** (not debug), which avoids the extra
   overhead/instrumentation a debug build carries, so playback and UI should
   feel noticeably smoother. It's signed with Gradle's auto-generated debug
   keystore purely so it stays directly installable; that's fine for
   personal use but isn't a Play Store–ready signature.
5. Unzip it on your phone, tap `Vidsize-release.apk` to install (Android will ask you to
   allow "install unknown apps" for your browser/file manager the first time),
   and open **Vidsize**.

### Alternative: build entirely on-device with Termux
If you'd rather not use GitHub at all, Termux can build Android projects locally,
but it's heavier to set up (installing a JDK, the Android SDK command-line tools,
and accepting SDK licenses all inside Termux). Rough outline:
```
pkg install openjdk-17 wget unzip
# download & unzip Android SDK command-line tools, then:
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
cd VideoResizer
gradle assembleDebug   # or use a gradle binary you install separately
```
This works but can take a while on a phone and needs several GB of free storage —
the GitHub Actions route above is much faster in practice.

## Changelog

> Entri v1.13 ke bawah di sini adalah histori lengkap sampai versi itu.
> Perubahan **setelah** v1.13 dicatat di [`CHANGELOG.md`](./CHANGELOG.md) —
> cek file itu dulu untuk tahu apa yang berubah paling baru.

**v1.13 — Before/after preview, side by side**
- Added a **"Sebelum vs sesudah"** comparison to the result card shown
  right after a successful export: a frame from the original source next
  to a frame from the actual exported file, side by side. Adds no extra
  video-decoding work — the "before" frame is reused from `filmstrip`
  (already extracted for the trim scrubber) and the "after" frame is
  reused from the thumbnail already generated for Studio history. Each
  side keeps its own real aspect ratio rather than being forced into a
  square, so a 16:9→9:16 resize (the whole point of this app) is
  immediately visually obvious rather than something you have to take on
  faith from a text summary.

**v1.12 — Full logic audit: fixed a real cancel-race bug, completed a dead code path**
- **Fixed**: cancelling a running batch (button or back gesture) only ever
  reset the screen's visible state — the actual background loop coroutine
  kept silently running underneath, suspended forever waiting on a
  callback (`Transformer.cancel()` doesn't invoke it) that would now never
  come. Invisible on its own, but if the user pressed "Proses semua" again
  right after cancelling, that zombie loop and the fresh one could both be
  alive at once, racing over the same shared state (`activeTransformer`,
  the item list, the foreground service). Fixed by capturing the loop's
  actual coroutine `Job` and cancelling *that* directly — the loop now
  provably stops the moment cancellation is requested, not just the UI
  that was reporting it.
- **Fixed a dead/illogical leftover**: Batch Export declared
  `selectedSocialPreset` and defensively reset it to `null` in several
  places, as if a preset selection could be active there — but there was
  never actually a picker for it in that screen, so it could never be
  anything but `null`. Rather than delete it, completed it: Batch Export
  now has the same one-tap social-media preset row the main screen has,
  applying to every video in the queue.

**v1.11 — Fixed missing back-gesture handling on Batch Export**
- **Fixed**: Batch Export had no `BackHandler` at all — only the on-screen
  back arrow in its TopAppBar went through the cancel-confirmation path.
  This app has no Navigation-Compose back stack (screens are manual state,
  not backstack entries), so a system back press/gesture with nothing
  registered to intercept it falls through to the default platform
  behavior: finishing the Activity outright. Pressing the phone's actual
  back button/gesture while on Batch Export — including while a batch was
  actively processing — could exit the whole app with none of the
  cancel-safety the on-screen button had. Main screen and Studio already
  had this covered; Batch was the one gap. Fixed by adding the same
  `BackHandler` logic Studio already uses, so both the on-screen button and
  the system back gesture now behave identically everywhere in the app.

**v1.10 — Fixed trim-handle drag lag (root cause), two new custom themes, versioned CI artifact**
- **Fixed** the reported trim-handle gesture defect at its root: the
  handle's internal drag accumulator was only clamped to 0f..1f, while the
  caller separately re-clamped the value it actually applied to a tighter
  range (leaving room for the other handle's minimum gap). Those two
  clamps disagreeing meant dragging a handle into a limit let the
  accumulator silently keep climbing past what was actually shown/applied
  — so reversing direction required "catching up" through the invisible
  overshoot before the handle would visually move again. Fixed by having
  the caller pass the *exact* valid range each handle is allowed to reach,
  clamped at the same point the accumulator is updated, so the two can
  never disagree.
- Also fixed along the way: the touch target for each trim handle was
  exactly the visible bar's width (16dp), well under Android's 48dp
  recommended minimum — widened to a 48dp invisible hit area (same visible
  bar inside) so the handle is easier to actually grab. And the drag
  gesture detector was keyed on `trackWidthPx`, which meant a mid-drag
  change to that value (e.g. rotating the screen) would silently drop the
  in-progress gesture; it's now a stable key with the latest values read
  through `rememberUpdatedState` instead, so a drag in progress can't be
  cut off that way. Also switched the handle's position to
  `absoluteOffset` instead of `offset`, since raw drag deltas from
  `detectDragGestures` are never RTL-mirrored but `offset` is — this app
  declares RTL support, so an RTL locale would have dragged handles
  backwards relative to the finger without this.
- Added two **custom themes** beyond Dark/Light/Follow-system, each a
  genuinely distinct visual identity (color palette **and** corner-shape
  language **and** title typography — not a recolor of the same
  components): **Midnight Neon** (near-black background, electric
  cyan/magenta accents, sharp 2–16dp corners, monospace labels) and
  **Warm Paper** (warm cream background, terracotta/olive accents, soft
  8–36dp corners, serif titles). Both use only built-in generic font
  families (`FontFamily.Monospace` / `FontFamily.Serif`) — no font files
  bundled, so this can't introduce a missing-asset build failure. Picked
  from the same theme menu as before (now with a divider separating the
  two groups).
- **Fixed**: the CI-built release APK's filename was static
  (`VideoResizer-release.apk`) regardless of what version was actually
  built, so a downloaded artifact couldn't be told apart from any other
  version by its name. The workflow now reads `versionName` straight out
  of `app/build.gradle.kts` and names both the APK file and the uploaded
  artifact after it (e.g. `VideoResizer-v1.10-release.apk`) — can't drift
  out of sync with the app's own reported version since it's read from the
  same place.

**v1.9 — Removed an unrelated app that had gotten merged into this repo, restored real release signing**
- **What happened**: this repo's `main` branch turned out to already contain
  a *different*, unrelated Video Resizer rebuild (Navigation Compose,
  DataStore settings, GIF export, MVVM `ViewModel`s, under `data/`,
  `ui/components/`, `ui/navigation/`, `ui/screens/`, `util/`, `video/`, plus
  a `VideoResizerApp.kt` file) — most likely from a separate session/tool,
  before this project's zip-based workflow with me ever started. Every past
  zip only ever *adds/overwrites* files (`unzip -o`), never deletes, so
  those files kept silently riding along unbuilt through every update until
  a real CI run finally tried to compile everything together and failed on
  missing `datastore`/`navigation-compose` dependencies and undefined
  string resources that code needed. **Removed** in this update: all of the
  paths listed above, plus a stray top-level `CHANGELOG.md` from that same
  source (this README's changelog is the one being maintained). None of it
  was reachable from this app's actual entry point (`MainActivity.kt`'s
  `VideoResizerApp`), so nothing about the working app changes — it was
  dead weight, not a feature.
- **Restored real release signing**: fixing the build above meant touching
  `signingConfigs` again, which had a real history of its own in this repo
  (commits `e5807ce`/`4001440`) that predates my involvement — a permanent
  `release.keystore` (alias `videoresizer`) with its password rotated out of
  git history entirely and into a `RELEASE_KEYSTORE_PASSWORD` GitHub
  Secret. Every zip I'd delivered up to v1.8 was unknowingly overwriting
  that with a debug-keystore placeholder each time, since `build.gradle.kts`
  is a file my zips always fully replace. Re-wired `app/build.gradle.kts`
  to read `RELEASE_KEYSTORE_PASSWORD` from the environment again exactly as
  it was, and added the matching `env:` block to
  `.github/workflows/build.yml`'s build step so the secret actually reaches
  Gradle — that env injection did not exist in this repo's real workflow
  file before this. **If `RELEASE_KEYSTORE_PASSWORD` is no longer set in
  this repo's Settings > Secrets, the release build will silently sign with
  a bogus fallback password** — worth a quick check there before assuming
  this is fully fixed.
- Also carried over one hard-won fix from that removed codebase's own
  history rather than losing it: an explicit `com.google.guava:guava`
  dependency, added there after a real build hit an unresolved-reference
  error on `ImmutableList` via Media3's `OverlayEffect` (the same API this
  project's watermark feature uses).

**v1.8 — Fix state-reset bug, fix repeated notification prompt, UX polish**
- **Fixed**: opening Studio or Batch Export used to fully unmount the main
  screen, which threw away every `remember`-held piece of state living
  there — picked video, aspect ratio, resolution, quality, watermark, trim
  range, all of it. Tapping Studio/Batch by accident after carefully
  configuring an export meant coming back to a completely reset screen. It
  also silently cancelled an in-progress export's coroutine if you
  navigated away mid-export. Fixed by keeping the main screen permanently
  composed underneath and drawing Studio/Batch as opaque overlays on top of
  it instead of swapping it out — same visuals, nothing underneath loses
  state or gets torn down anymore. (One side effect this introduced and
  also fixed in the same pass: without it, a video actively playing in
  preview would have kept its audio running in the background behind
  Studio/Batch, since the player was no longer being destroyed either —
  now it explicitly pauses the moment the main screen stops being the
  visible one.)
- **Fixed**: the "allow notifications" system prompt used to fire on
  *every single export* for as long as the permission wasn't granted,
  with no way to make it stop. It's now asked at most once ever (tracked
  in SharedPreferences, survives app restarts) — matching Android's own
  guidance, and matching that the export itself doesn't actually depend on
  the permission being granted either way.
- UX: added the same plain-language "what does this actually do" captions
  under **Kualitas/bitrate** (what Rendah/Sedang/Tinggi trade off) that
  Mode Resize already had, and brought Batch Export's option panel up to
  the same level of explanation as the main screen's.

**v1.7 — Batch export**
- Added **Batch Export**: a new screen (icon next to Studio in the top bar)
  where you pick several videos at once (Photo Picker's
  `PickMultipleVisualMedia`, up to 20) and apply one shared set of
  settings — aspect ratio, resolution/custom, resize mode, rotation, mute,
  quality/bitrate, and watermark — to all of them. They're processed
  **sequentially, one at a time** (Media3's `Transformer` isn't designed for
  concurrent exports on one device), with a per-item status row
  (waiting/percent/done/failed) and a Cancel button that stops the current
  item and marks the rest as cancelled. Each finished item is published to
  the gallery and saved to Studio history exactly like a normal single
  export — nothing batch-specific there.
- **Known, deliberate limitation**: batch mode has no per-video trim —
  each picked video is exported in full, since a single shared trim range
  wouldn't mean the same thing across clips of different lengths. Trim a
  clip individually first if you need that.
- **Process note on this update**: while wiring the batch screen into this
  file, an in-progress edit briefly deleted `VideoEditorPreview`'s function
  signature while leaving its body in place — caught and fixed via a
  balance/structure check (open/close brace and paren counts per file, plus
  a full list of top-level declarations) before this zip was built, so it
  never should have reached you broken. Mentioning it here rather than
  quietly — the point of the structure check is exactly to catch this class
  of mistake before it becomes your problem to debug.

**v1.6 — Watermark/logo, preset media sosial, dan perkiraan waktu ekspor**
- Added a **Watermark / logo** overlay: pick a still image (via the same
  Photo Picker used for videos), place it in one of 5 anchors (4 corners +
  center), and control its size (5–50% of frame width) and opacity
  (10–100%) with sliders. Implemented with
  `androidx.media3.effect.OverlayEffect` +
  `BitmapOverlay.createStaticBitmapOverlay(context, uri, overlaySettings)`,
  the same overlay pipeline Google's own Transformer sample app uses for
  logo/picture-in-picture overlays — applied *after* the presentation/crop
  and rotation effects so the watermark sits on the final framed output, not
  the raw source. Watermark settings are saved per Studio history entry and
  restored on **Edit ulang**, same as every other setting.
- Added **Preset media sosial**: one-tap chips for TikTok/Reels/Shorts
  (1080×1920), YouTube (1920×1080), Instagram Feed square (1080×1080), and
  Instagram Feed portrait (1080×1350) — each sets an exact custom
  resolution + a realistic target bitrate for that platform in one tap,
  instead of a novice user having to know what pixel dimensions or bitrate
  a given platform actually wants. Touching resolution or quality manually
  afterward silently clears the preset selection (it's a shortcut into the
  existing manual controls, not a separate code path, so it can never drift
  out of sync with them).
- Export progress now also shows a **rough remaining-time estimate**
  (`elapsed / percent * (100 - percent)`), derived entirely from the
  percentage feed that already existed since v1.2's progress-polling fix —
  no new Media3 API surface needed for this part. The label next to it
  ("hardware encoder perangkat") is a note, not a new capability switch:
  `DefaultEncoderFactory` was already using the device's `MediaCodec`
  hardware video encoder by default before this change too.
- Small visual refresh: a gradient app mark in the top bar and a gradient
  primary "Resize video" button (using the same `AccentPrimary`/
  `AccentSecondary` palette colors `ui/theme/Color.kt` already defined),
  so the app doesn't read as flat/default Material anymore. No new colors
  were added, no existing screen was restructured — this is intentionally
  a light touch to keep the risk of this round low.
- **COMPATIBILITY NOTE**: the watermark feature is the first thing in this
  project to use `androidx.media3.effect.OverlayEffect`/`BitmapOverlay`/
  `OverlaySettings`, which take a Guava `com.google.common.collect.ImmutableList`
  in their public API surface. No new Gradle dependency was added for this —
  Guava is already on the compile classpath as a transitive dependency of
  `media3-effect`/`media3-common` (same reason `Presentation` and
  `ScaleAndRotateTransformation`, both already in this project since v1.2/
  v1.3, work without an explicit Guava dependency either). If Android Studio
  ever flags `com.google.common.collect.ImmutableList` as unresolved, that
  would mean the transitive dependency changed — check the actual resolved
  version of `media3-effect` before adding an explicit `com.google.guava:guava`
  dependency by hand.
- **CONFIDENCE NOTE on the anchor math**: `OverlaySettings.Builder`'s
  `setOverlayFrameAnchor`/`setBackgroundFrameAnchor` NDC-coordinate
  convention was checked against `media3-effect` 1.3.1-era sample code
  specifically — Media3 **1.6.0 flipped the sign convention** of
  `setOverlayFrameAnchor` (see that version's release notes). This project
  is pinned to `media3-effect:1.3.1`, so the corner math in
  `VideoResizer.buildWatermarkOverlay` uses the pre-1.6.0 convention on
  purpose. If this project's Media3 version is ever bumped past 1.6.0, the
  four corner anchor pairs need re-deriving (or the watermark will land in
  the wrong corner, mirrored).

**v1.5 — Kontrol kualitas/bitrate + perbaikan pipeline CI yang hilang**
- Added a **Kualitas / bitrate** control (Original / Rendah / Sedang / Tinggi /
  Custom kbps) using `androidx.media3.transformer.DefaultEncoderFactory` +
  `VideoEncoderSettings.setBitrate(...)` with `setEnableFallback(true)` so a
  device that can't hit the exact requested bitrate falls back gracefully
  instead of failing the export outright. This is the "manual bitrate/quality
  preset" the v1.4 README explicitly deferred — it's now been verified
  against the real `DefaultEncoderFactory`/`VideoEncoderSettings` API surface.
- Added a live **"Perkiraan ukuran"** (estimated output size) label under the
  quality picker, computed from target resolution × bitrate × trimmed
  duration, so a novice user gets a before-you-commit ballpark file size
  instead of finding out only after a multi-minute export finishes.
- Quality/bitrate is now saved per Studio history entry and restored on
  **Edit ulang**, same as every other setting.
- **COMPATIBILITY FIX**: this zip is the first version to actually include
  `.github/workflows/build.yml`. Every earlier version's README described a
  GitHub Actions build pipeline that the zip itself never shipped — pushing
  to a fresh repo had nothing to trigger and would silently do nothing,
  which is a very likely source of repeated "why isn't it building"
  troubleshooting. The new workflow deliberately does **not** depend on
  `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` (none of which were present
  either, and the wrapper jar is a binary that must match Gradle's own
  checksums to be trustworthy) — instead it uses the official
  `gradle/actions/setup-gradle@v4` action to install Gradle 8.7 directly
  (matching `gradle/wrapper/gradle-wrapper.properties`) and runs
  `gradle assembleRelease`. Push to `main` or trigger it manually from the
  repo's **Actions** tab ("Run workflow") — see the updated on-device build
  steps below.

**v1.4 — Fix oversized trim handles and layout gap**
- The trim RangeSlider is no longer overlaid directly on the filmstrip with
  transparent track colors — that combination was rendering its handles as
  oversized blobs and appears to have caused an unexplained layout gap in
  the card. Reverted to a cleaner split: a read-only filmstrip (with thin
  dimming overlays showing what's trimmed away) sits above a normal,
  default-styled `RangeSlider` in its own row below.
- App version is now shown as `versionName`/`versionCode` in
  `app/build.gradle.kts` and bumped with each meaningful round of fixes —
  this round is **1.4**.
- The GitHub Actions artifact (and the APK file inside it) is now named
  **`VideoResizer-release`** instead of `video-resizer-release-apk` /
  `app-release.apk`.

**v1.3 — Preview, filmstrip trim, gallery picker, custom resolution, release build**
- Added an embedded **video preview player** (ExoPlayer + `PlayerView`) so you
  can see/scrub the source clip before editing.
- Trim now shows a **filmstrip** (8 sample thumbnails, extracted off the main
  thread) behind the range slider instead of a bare slider.
- Switched the video picker to Android's built-in **Photo Picker**
  (`ActivityResultContracts.PickVisualMedia`) for a native Gallery-app feel,
  replacing the generic document picker.
- Added a **Crop vs Stretch** resize-mode toggle (`Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP`
  vs `Presentation.LAYOUT_STRETCH_TO_FIT`) and a **Custom resolution** dialog
  for typing an exact width × height, alongside the existing presets. Added
  a 4:3 aspect ratio preset.
- **CI now builds a Release APK**, not Debug (`gradle assembleRelease`),
  signed with Gradle's debug keystore so it stays directly installable. This
  avoids the extra debug-build overhead that can cause jank/lag.
- Added `kotlinx-coroutines-android` as a dependency, used to keep filmstrip
  thumbnail extraction off the main thread.

**v1.2 — Trim, mute, rotate, and Studio**
- Added trim range (`MediaItem.ClippingConfiguration`), mute audio toggle
  (`EditedMediaItem.setRemoveAudio`), and 0/90/180/270° rotation
  (`ScaleAndRotateTransformation`).
- Added the **Studio** screen: history of past resized videos with
  thumbnails, settings summary, re-edit, share, and delete.
- No new Gradle dependencies were required for any of this.

**v1.1 — Public gallery export**
- Resized videos are now published to the public `Movies/VideoResizer` folder
  via `MediaStore` (Android 10+) or direct file write + media scan (Android 9
  and below), so they show up in Gallery / Google Photos immediately.
- Added the one-time `WRITE_EXTERNAL_STORAGE` runtime permission prompt,
  scoped to API 28 and below only (not required on Android 10+).
- The in-app **Share / Open** button remains available as a secondary path.

## Notes / things to double check when you build

- `androidx.media3:media3-transformer:1.3.1` is used. Media3's `Transformer.Listener`
  callback signature has changed a couple of times across versions (some versions pass
  `MediaItem`, newer ones pass `Composition`). If Android Studio flags a signature
  mismatch in `VideoResizer.kt`, check the version you resolved and adjust
  `onCompleted`/`onError` accordingly — Android Studio's quick-fix will show the
  exact expected signature.
- **Confidence note on the new v1.2 APIs**: `MediaItem.setUri` / `ClippingConfiguration`
  are long-stable ExoPlayer/Media3 APIs (high confidence). `EditedMediaItem.setRemoveAudio`
  and `ScaleAndRotateTransformation.Builder().setRotationDegrees(...)` are the standard,
  commonly-documented way to mute/rotate with Transformer, but — same as with any
  `@UnstableApi`-marked surface — a minor signature change between Media3 point
  releases is possible. If a build error appears in `VideoResizer.kt` around trim/mute/
  rotate specifically, it's almost certainly one of these three calls; Android Studio's
  error will name the exact expected signature to adjust to.
- **Confidence note on the new v1.3 APIs**: `Presentation.LAYOUT_STRETCH_TO_FIT` is
  the constant name I'm fairly confident about for the "Stretch" mode, but slightly
  less certain than the already-proven `LAYOUT_SCALE_TO_FIT_WITH_CROP` — if the build
  fails specifically on that line, it's the one to check first (Android Studio's
  autocomplete on `Presentation.LAYOUT_` will show the exact available constants).
  `ActivityResultContracts.PickVisualMedia` and `ExoPlayer`/`PlayerView` are stable,
  long-established AndroidX APIs (high confidence).
- **Confidence note on the v1.5 bitrate API**: `DefaultEncoderFactory.Builder(context)
  .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bps).build())`
  plus `.setEnableFallback(true)`, wired in via `Transformer.Builder().setEncoderFactory(...)`,
  was checked against `androidx.media3:media3-transformer:1.3.1`'s actual API surface (same
  package/class names used by Google's own Transformer demo app) — high confidence, but
  still `@UnstableApi` like the rest of this surface, so a point-release signature change
  remains possible. If a build error appears specifically around bitrate/quality in
  `VideoResizer.kt`, `setRequestedVideoEncoderSettings`/`setBitrate`/`setEnableFallback`
  are the calls to check first.
- **Confidence note on the v1.6 watermark API**: `OverlayEffect`, `BitmapOverlay
  .createStaticBitmapOverlay(context, uri, overlaySettings)`, and `OverlaySettings.Builder`
  (`setOverlayFrameAnchor`/`setBackgroundFrameAnchor`/`setScale`/`setAlphaScale`) were
  checked against `media3-effect` 1.3.1-era usage (Google's own Transformer sample code
  and the AndroidX issue tracker) — high confidence on method names, but the anchor
  *sign convention specifically* changed in Media3 1.6.0 (see the compatibility note in
  the v1.6 changelog entry above), so that's the one spot to re-check first if this
  project's Media3 version is ever bumped. If a build error appears around the watermark
  specifically, `createStaticBitmapOverlay`'s parameter order/types are the next thing
  to check — Android Studio's error will name the exact expected signature.
- No app icon PNGs are bundled; `ic_launcher` is a simple vector drawable so the
  project builds without needing image assets. Swap in your own launcher icons
  whenever you're ready.
- **Confidence note on batch export (v1.7)**: `ActivityResultContracts.PickMultipleVisualMedia(maxItems)`
  and its `List<Uri>` callback were checked against the official Android developer docs
  and current real-world usage examples — high confidence. The batch queue itself reuses
  the exact same `VideoResizer`/`runResize`/`ResizeRequest` pipeline as single-video export
  (just called once per item, sequentially, awaited via a `CompletableDeferred` before
  moving to the next), so it inherits that pipeline's existing correctness rather than
  introducing a new export code path.
- With batch export in, the feature set from the original three options
  (bitrate/quality, batch export, watermark/logo) is complete. Two more
  things that were natural next steps beyond that original list — **text
  caption overlay** and **per-batch-item preview thumbnails** in the queue
  list — were built in Batch 8 (see `CHANGELOG.md`), reusing the
  watermark's overlay pipeline and the trim scrubber's frame extractor
  respectively rather than adding new subsystems for either.

## Project & CI notes (baca ini dulu sebelum ubah setup build/git)

- **Repo GitHub**: `FDzaki-dev/Video-resizer`. Nama folder proyek yang
  konsisten dipakai: `Video-resizer` (bukan `Video-resizer-main` — itu
  cuma nama folder bawaan GitHub saat export ZIP branch `main`, dan
  sempat menyebabkan repo lokal di satu device ter-`git init` sebagai
  repo baru alih-alih tersambung ke repo aslinya).
- **`release.keystore` HARUS tetap ter-commit ke git, JANGAN masuk
  `.gitignore`.** `.github/workflows/build.yml` men-checkout sources apa
  adanya dan langsung memakai `release.keystore` dari situ — ia **tidak**
  merekonstruksi keystore dari GitHub secret manapun (secret
  `RELEASE_KEYSTORE_PASSWORD` cuma untuk password-nya, bukan file
  keystore-nya). Meng-gitignore-kan file ini terlihat seperti langkah
  keamanan yang wajar tapi akan langsung mematahkan step
  `validateSigningRelease` di CI — ini sudah pernah terjadi sekali, lihat
  `CHANGELOG.md`.
- **Status backend hardening**: Batch 1 (lihat `CHANGELOG.md`) sudah
  menutup crash-crash di jalur export inti (`VideoResizer.resize()` setup,
  `ExportForegroundService.onCreate()`). `VideoHistoryStore.kt` sudah
  diaudit dan aman. **Batch 2** (lihat `CHANGELOG.md`) menambahkan crash
  logger bawaan (`CrashLogger.kt`, tulis ke
  `Documents/VideoResizer/logs/` via MediaStore, retensi FIFO 50 file),
  memperbaiki `.github/workflows/build.yml` supaya APK rilis muncul sebagai
  **GitHub Release** asli (bukan cuma Actions Artifact), dan membersihkan
  seluruh sisa force-unwrap (`!!`) di `MainActivity.kt` dan `VideoResizer.kt`.
  **Masih perlu dicek manual**: pastikan secret `RELEASE_KEYSTORE_PASSWORD`
  masih terset di GitHub repo Settings — kalau tidak, build rilis
  ter-sign pakai password fallback bodong (lihat catatan di atas).
- **Debugging**: kalau app crash, cek dulu file terbaru di
  `Documents/VideoResizer/logs/` (lewat file manager mana pun) sebelum minta
  Logcat/ADB — isinya versi app, OS, model device, thread, dan stack trace
  lengkap.
- **Status visual/UI**: **Batch 3** (lihat `CHANGELOG.md`) menambahkan tema
  ke-5, **"Midnight Blue Glass"** — gaya glassmorphism iOS dengan gradasi
  midnight-blue di belakang tiap layar — dan menjadikannya default baru
  aplikasi (tema Dark/Light/Midnight Neon/Warm Paper tetap ada, bisa dipilih
  lewat menu tema di top bar). Semua di `ui/theme/` (`Color.kt`, `Type.kt`,
  `Theme.kt`) plus penyesuaian ringan di `MainActivity.kt` (background
  per-layar, border+shape 4 Card, 2 gradient CTA jadi theme-aware). Tidak
  ada blur asli (`RenderEffect`/API 31+) — efek kaca dicapai lewat
  transparansi + border tipis + gradasi latar, jadi tetap konsisten di
  semua versi Android yang didukung app (minSdk 24).
- **Status build speed**: **Batch 4** (lihat `CHANGELOG.md`) mempercepat CI
  (`assembleRelease` di GitHub Actions) lewat `gradle.properties` (heap
  daemon lebih besar, `org.gradle.caching`, `-XX:+UseParallelGC`),
  `lint.checkReleaseBuilds = false` di `app/build.gradle.kts`, dan flag
  `--parallel --build-cache` eksplisit di `build.yml`. Bump versi
  AGP/Kotlin dan `org.gradle.configuration-cache` sengaja belum disentuh —
  keduanya butuh CI run nyata untuk diverifikasi, bukan cocok ditebak tanpa
  compiler lokal (lihat alasan lengkap di `CHANGELOG.md`).
