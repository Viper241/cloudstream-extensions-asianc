# AsianC — CloudStream extension

A CloudStream 3/4 provider for **https://asianc.id** (the Dramacool/Asianc
catalogue). It supports the home page, browse/search, series and movie details,
episode lists, and playback through the site's video servers.

## Features

- **Home rows**: Recently Updated, Recently Added Movies, Recently Added
  K-Shows, Most Popular (with posters), Ongoing, and Coming Soon.
- **Search**: paginated site search plus the site's autocomplete API for
  CloudStream quick search.
- **Details**: title, poster, plot, year, genres, content rating, status,
  series/movie detection, and ascending episode numbers with air dates.
- **Playback**:
  - **Standard Server** — decrypts the `vidbasic.top` AES payload to a direct
    HLS/MP4 URL.
  - **StreamWish / HGLink** — follows `hglink.to` → `hanerix.com` and reads the
    generated HLS URL.
  - **VidHide** — custom mirror for `minochinos.com`.
  - **StreamTape** and **MixDrop** — handled by CloudStream's built-in
    extractors.
  - Upns (`upns.pro`) is intentionally skipped because it is a separate
    encrypted Vidstack app.

## Build

Requirements: JDK 17+ and an Android SDK with platform 36. The Gradle wrapper
is included.

```bash
./gradlew :AsianC:make
```

The installable plugin is written to:

```
AsianC/build/AsianC.cs3
```

A prebuilt copy is included at:

```
dist/AsianC.cs3
```

To build the repository index too:

```bash
./gradlew makePluginsJson
```

This writes `build/plugins.json`.

### Install locally

1. Copy `AsianC/build/AsianC.cs3` to the device.
2. In CloudStream: **Settings → Extensions → Install from file** and choose the
   `.cs3` file.

### Install from a repository

This project is configured for
`https://github.com/Viper241/cloudstream-extensions-asianc`. The
`builds` branch contains `AsianC.cs3` and `plugins.json`, and `repo.json`
points at it:

```json
{
  "name": "AsianC Repo",
  "iconUrl": "https://asianc.id/template/images/icon_192x192.png",
  "description": "AsianC (Dramacool) extension",
  "manifestVersion": 1,
  "pluginLists": [
    "https://raw.githubusercontent.com/Viper241/cloudstream-extensions-asianc/builds/plugins.json"
  ]
}
```

Add this raw URL in CloudStream's repository settings:

```
https://raw.githubusercontent.com/Viper241/cloudstream-extensions-asianc/main/repo.json
```

The included GitHub Actions workflow builds every push and publishes the
`.cs3` and `plugins.json` to the `builds` branch.

## Project layout

```
AsianC/
  build.gradle.kts                         plugin metadata
  src/main/kotlin/com/asianc/AsianC.kt     provider + site parsing + video extraction
  src/main/kotlin/com/asianc/AsianCExtractors.kt  HGLink + VidHide mirror extractors
  src/main/kotlin/com/asianc/AsianCPlugin.kt      plugin registration
  src/test/kotlin/com/asianc/AsianCLiveTest.kt    live smoke tests
```

## Tests

The tests hit the live site. They are useful while the site is unchanged but
should not run in offline CI:

```bash
./gradlew :AsianC:testDebugUnitTest
```

## Notes

- The site changes domains regularly. If it moves, update `mainUrl` in
  `AsianC.kt` (or use CloudStream's clone-site provider setting).
- The AES key/IV in `AsianC.kt` belongs to the site's Standard Server player.
  If the player changes its keys, update `VIDBASIC_KEY` / `VIDBASIC_IV`.
- This project is for personal use and interoperability. It does not host any
  video content.
