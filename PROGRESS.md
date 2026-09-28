# Progress

State of the work as of 2026-09-28. Everything below is committed and pushed to
`origin/main` (`8715c60f`); the tree is clean. `MANGA_REMOVAL_PROGRESS.md` covers
the earlier manga removal and is still accurate.

## Landed

Everything verified on the `eb8d8789` Xiaomi (release arm64 APK, installed with
`adb install -r` so the real app is updated in place).

| Commit | What |
|---|---|
| `8dfe918` | Multiple local anime folders, set in Data and storage |
| `36b0842` | Read each anime folder once; cache local covers and previews; stop leaking temp files |
| `02daade` | Changelog and README entries for the two above |
| `cc6a65c` | Use an episode thumbnail that is already on disk instead of re-extracting a frame |
| `f71cfdc` | Photos next to the videos: a full screen gallery for image files in a local folder |
| `d943045` | Load series covers that are stored as a path |
| `c9b819d` | Stop tapping a photo from closing the viewer, and let gifs play |
| `a97bfc6` | Re-extract one folder's thumbnails when refreshing inside a series |
| `755726b` | Split the library refresh into series and episode thumbnails |
| `8715c60` | Changelog for all of the above |

## The series cover bug

Every series cover in the library was the error placeholder while the episode
previews in the very same folder rendered fine. Worth writing down because the
shape of it is easy to re-create.

`AnimeImageFetcher.getResourceType()` classifies a cover as `Type.File` when the
url starts with `/` or `file://`, and that branch handed the path straight to
`java.io.File`. Local covers *are* paths, so they skipped the copy into
`files/animecovers/` and then failed later inside the decoder, where Coil turns
the failure into the error painter and logs nothing. Episode previews go through
`LocalImage` → `LocalImageFileFetcher` → UniFile → the cache, which is why they
worked in the same folder.

`Type.File` now goes through the same `uniFileLoader` as a `content://` cover.
Also dropped the `!!` on `UniFile.fromUri`, which turned an unresolvable uri
into a bare `NullPointerException` naming no image at all.

Symptom to recognise: covers broken, previews fine, same folder, and
`files/animecovers/` gets no new entries.

## Why the caching is shaped the way it is

Coil 3.1 only disk caches **file backed** image sources — `diskCacheKey` exists
solely on `FileImageSource` (confirmed in the `coil-core` bytecode), so an image
behind a `content://` uri can never be given a cache key. Two consequences worth
remembering before touching this code again:

- Coil registers its own `ContentUriFetcher` *before* any fetcher the app adds,
  so a `Uri`-typed fetcher is dead code. That is why `LocalImage` exists as its
  own request type.
- Library covers and episode previews therefore get copied into a cache dir once
  and served as real files, keyed by uri **and mtime** so a replaced image is
  never served stale.

`coil-gif` needs no wiring. It self-registers through
`META-INF/services/coil3.util.DecoderServiceLoaderTarget`, and on API 28+ it
returns an `AnimatedImageDrawable`. The only bug was the missing `start()`:
`ImageView` starts an `Animatable` only once the view is attached and shown, and
a Coil `target` inside an `AndroidView` update can run before that, so a gif sat
on frame one forever.

## Per-series thumbnail refresh

`cc6a65c` reuses whatever frame is in `.thumbnails/` instead of running ffmpeg
again. Right as a default, but it meant a series refresh could never fix a bad
preview, because every existing thumbnail was left exactly as it was.

`LocalAnimeSource.requestThumbnailRefresh(animeUrl)` marks one url;
`getOldEpisodeList` consumes the mark and skips the `find()` short-circuit for
that fetch only. `AnimeScreenModel.fetchAllFromSource` marks the current anime
only when `manualFetch` is true, so the automatic load on entering a screen does
not re-extract, and no folder other than the one on screen is touched.

A force flag on `AnimeSource.fetchEpisodeList` would have been the obvious
design and would have changed the interface for every source. The companion set
does the same job in three lines.

## Measurements

Only the folder-scan figure is a real A/B (20 anime × 41 files, same emulator,
same instrumented build, 3 cold starts each): **1211-1302 ms → 948 ms**.

Cover and preview *loading* was verified behaviourally (files land in the cache,
are reused unchanged on a second cold start, Coil's decoded cache is populated)
but never timed. Treat any loading speed-up number as an estimate.

Gif animation is verified properly: a 4s `screenrecord` of an open gif, sampled
at 6fps, gives 24 frames and all 24 are pixel-distinct inside the image area. A
still image collapses to 1. The jpg next to it in the same folder stays static.

## Environment gotchas

- **`JAVA_HOME` is wrong in `AGENTS.md` and has been wrong twice.** It currently
  says `/usr/lib/jvm/java-17-openjdk`, which does not exist — there is no
  `/usr/lib/jvm` at all. The working JDK is `/home/mark/jdk17` (Temurin
  17.0.20.1). Commit `137f8fe` was titled "Point AGENTS.md at the JDK that
  exists" and replaced a correct path with a wrong one. Use `/home/mark/jdk17`.
- **The x86_64 emulator cannot boot**: no `/dev/kvm` on this machine, and the
  AVD is x86_64. `emulator -avd goonyomi` exits with "requires hardware
  acceleration". Everything recent was verified on the real phone instead.
- **The Xiaomi `eb8d8789` is the test device** and does connect over USB. Install
  `app-arm64-v8a-release.apk`, never the x86_64 one, and never the debug build.
- **Do not blind-tap coordinates on the phone.** Reading a coordinate off a
  screenshot and tapping it without re-reading the screen first is how a video
  got started and a PiP window left floating. Screenshot, read, then tap exactly
  that point. The 1080×2400 `screencap` is 1:1 with the real screen, so no
  scaling factor — but confirm the current state with a fresh capture every time.
- **Git identity** is `Mark <mark@example.com>`, which would rewrite the author
  of every commit. Use
  `git -c user.name=badonkski70 -c user.email=badonkski70@users.noreply.github.com`
  so no config is modified and the history stays consistent.
- **`gh` resolves the wrong repo** by default (it picks the fork parent). Always
  pass `-R badonkski70/Goonyomi`.
- The release workflow has `paths-ignore: '**.md'`, so a docs-only push correctly
  triggers no build.
- `./gradlew :app:compileReleaseKotlin` is the fast gate (~5-15 s incremental);
  `assembleRelease` is ~2-3.5 min. Run `spotlessApply` before `spotlessCheck`,
  the check alone will just tell you it failed.

## Things found but deliberately not fixed

- `LocalEpisodeThumbnailManager.update()` calls `find()` with the extension
  included (`ep01-thumbnail.jpg`) while `find()` compares against
  `nameWithoutExtension` (`ep01-thumbnail`), so that lookup can never match.
  Harmless now: extraction only runs when no matching file exists, so
  `createFile` cannot hit Android's duplicate-name renaming. Revisit if
  `update()` ever grows a second caller.
- The app has no "clear image cache" action at all. `files/animecovers/` is
  pre-existing and intentionally persistent; the new previews cache lives in
  `cacheDir` so the OS reclaims it.
- `MANAGE_EXTERNAL_STORAGE` reports `granted=false` on the test phone even
  though the local source reads the SD card fine, so `dumpsys package` is not a
  reliable way to tell whether the app can read a folder. Do not "fix" the cover
  path by adding a permission check on the strength of that output.
- Coil throws nothing useful into logcat by default. Turn on
  **More → Settings → Advanced → Verbose logging** to get `DebugLogger` output;
  it costs performance, so turn it back off. Guessing at Coil failures from
  screenshots is slow; this is the fast path.

## Gallery support

The app doubles as a gallery for the images in its local anime folders.

- `ArchiveAnime` (`source-local/.../io/ArchiveAnime.kt`) now accepts image
  extensions next to the video ones, so photos are listed as episodes.
  `isImageUrl(url)` is the same test for the call sites that only hold an
  episode url. `cover`, `background` and `thumbnail` are excluded, otherwise
  the art the app generates into the folder shows up as an episode of its own.
- A photo is its own `preview_url`, so no frame is extracted from it, and the
  cover and background of a photo only folder fall back to a photo instead of
  calling ffmpeg on an image.
- `LocalImageViewer.launchIfImage()` (`app/.../ui/image/ImageViewerActivity.kt`)
  is the single guard: it returns true and opens the gallery when the episode is
  a photo, false when the caller should play it. It is called from
  `MainActivity.startPlayerActivity` (which every episode tap, swipe and
  playlist shortcut routes through), `NotificationReceiver.openEpisode`,
  `NotificationReceiver.openEpisodePendingActivity` (a photo has nothing to
  play, so that notification opens the anime instead) and twice in
  `PlayerActivity`: `onNewIntent` and `changeEpisode`. Both player entry points
  are needed, `changeEpisode` does not go through `onNewIntent`. Without the
  second one, the next episode button in a mixed folder handed the jpeg to mpv,
  which displayed it as a still image.
- The gallery itself is a `HorizontalPager` over the folder's photos wrapped in
  `PhotoView`, which was already on the classpath from the deleted manga
  reader. It reuses the `LocalImage` coil fetcher, because Coil's own
  `ContentUriFetcher` would win for a `content://` uri. Tapping the photo does
  nothing; the close button and the back gesture both leave.
- Not done: the manifest still has no `image/*` intent filter, so the app
  cannot be chosen as the system viewer for image files. Also, in list display
  the per-anime "show previews" switch still decides whether a row shows its
  photo, so a photo folder shows text rows until that is switched on. Grid
  display always shows them.
