# Progress

State of the work as of 2026-09-27. Everything below is committed and pushed to
`origin/main` (`cc6a65c`); the tree is clean. `MANGA_REMOVAL_PROGRESS.md` covers
the earlier manga removal and is still accurate.

## Landed

Four commits, each verified on the `emulator-5554` device before pushing:

| Commit | What |
|---|---|
| `8dfe918` | Multiple local anime folders, set in Data and storage |
| `36b0842` | Read each anime folder once; cache local covers and previews; stop leaking temp files |
| `02daade` | Changelog and README entries for the two above |
| `cc6a65c` | Use an episode thumbnail that is already on disk instead of re-extracting a frame |

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

## Measurements

Only the folder-scan figure is a real A/B (20 anime × 41 files, same emulator,
same instrumented build, 3 cold starts each): **1211-1302 ms → 948 ms**.

Cover and preview *loading* was verified behaviourally (files land in the cache,
are reused unchanged on a second cold start, Coil's decoded cache is populated)
but never timed. Treat any loading speed-up number as an estimate.

## Environment gotchas

- **`JAVA_HOME`**: `AGENTS.md` says `/home/mark/jdk17` — that path does not
  exist. Use `/usr/lib/jvm/java-17-openjdk`.
- **Device**: the Xiaomi from `AGENTS.md` is *not* connected. `emulator-5554`
  (x86_64, API 35) is what everything was verified on, so install
  `app-x86_64-release.apk`, not arm64.
- **Screenshots**: the emulator is 1080×2400 but `screencap` output is scaled to
  900×1920 in this UI. Multiply image coordinates by 1.2 before `adb input tap`.
- **Git identity is unset.** Commits use
  `git -c user.name=badonkski70 -c user.email=badonkski70@users.noreply.github.com`
  so no config is modified.
- **`gh` resolves the wrong repo** by default (it picks the fork parent). Always
  pass `-R badonkski70/Goonyomi`.
- The release workflow has `paths-ignore: '**.md'`, so a docs-only push correctly
  triggers no build.

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

## Open idea, not started

The app was asked to double as a gallery for viewing images. Groundwork for that
decision: the local source only accepts video extensions
(`ArchiveAnime.kt:8`), there is no zoomable image-viewer component, and the
manifest has no `image/*` intent filter, so the app cannot currently be chosen
as a viewer for image files. The user paused to think about the approach before
anything was written.
