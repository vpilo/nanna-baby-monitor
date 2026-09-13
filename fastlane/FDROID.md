# Publishing on F-Droid

The goal is one signing certificate across direct APK downloads, F-Droid and Play, so users can move between
sources with in-place updates. F-Droid supports this through reproducible builds: it rebuilds the tag from
source, compares the result against the APK published by CI and, when they match, distributes *that* APK with
its original signature instead of re-signing with the F-Droid key.

## What lives where

- `fastlane/metadata/android/en-US/` — store listing (title, descriptions, changelogs, screenshots). Read
  directly out of this repository by F-Droid, and usable by `fastlane supply` for Play.
- `fastlane/metadata/android/en-US/changelogs/default.txt` — the "What's New" text.

## Per release

1. Tag, and let `release:android` publish the signed APK. Reproducibility depends on that job passing
   `-Pbabymonitor.release=true`.
2. Refresh `fastlane/metadata/android/en-US/changelogs/default.txt`. It can go in any commit, so
   no release commit is needed; left alone, the previous text keeps showing.

Nothing else: fdroid notices the release on its own and appends the matching `Builds` entry. Only the first
submission needs the recipe filled in by hand.

`whatsNew` is only attached to the version matching `CurrentVersionCode`, which the update check below keeps
current, so `default.txt` always describes the newest release.

## How update checking works

The version code is `git rev-list --count HEAD`, not a literal in `build.gradle.kts`, so `UpdateCheckMode: Tags`
cannot work — fdroid scans the gradle files textually for a version code and only finds an expression.

Instead the `:appAndroid:generateVersionFile` Gradle task writes a `version.txt` from the same
`gitVersion.info` provider that sets the manifest's version, so the two cannot disagree:

```
versionCode=402
versionName=6.0.0
```

`release:android` publishes it as an artifact and `release:gitlab` attaches it to the release with
`filepath: /version.txt`, which makes GitLab serve it from a URL that always resolves to the newest release:

```
https://gitlab.com/vpilo/nanna-baby-monitor/-/releases/permalink/latest/downloads/version.txt
```

F-droid polls that URL in the `metadata/org.vpilo.babymonitor.yml` recipe (fdroiddata repo).

Check it from an fdroiddata checkout before submitting:

```sh
fdroid checkupdates -v org.vpilo.babymonitor
```
