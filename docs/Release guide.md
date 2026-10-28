# Releasing

A release is a git tag. CI builds and publishes it, and F-Droid picks it up from there.
Update `fastlane/metadata/android/<locale>/changelogs/default.txt` before tagging. The English one is used for GitHub release notes.

## Versions

Versions are `MAJOR.MINOR.PATCH`, derived from git:

- `MAJOR.MINOR` comes from the nearest `MAJOR.MINOR.0` tag.
- `PATCH` is the number of commits since that tag, up to 999.
- The version code is `MAJOR * 10^6 + MINOR * 10^3 + PATCH`.

To get the version of a commit:

```sh
git describe --tags --long --match "[0-9]*.[0-9]*.0" <commit>
```

For example, `5.1.0-13-g9fd8e35` is version `5.1.13`.

## Choosing the tag

- For a regular release, bump the minor or major and tag `MAJOR.MINOR.0`.
- For a patch on top of `MAJOR.MINOR.0`, tag the commit with its version as computed above, e.g. `5.1.13`. A release build on a tag
  with any other patch number fails.
- Only release patches from `main`. Commits on a side branch get the same versions as `main`, so tag a new `MAJOR.MINOR.0` on `main`
  before releasing from a side branch.

## Steps

1. Update `fastlane/metadata/android/<locale>/changelogs/default.txt` in every locale. This can be done in any commit.
2. Tag and push. The English changelog becomes the release notes.
   ```sh
   git tag -a 5.2.0 -m "Release notes"
   git push origin 5.2.0
   ```
3. The CI `release` jobs build with `-Pbabymonitor.release=true` and publish the signed APK, R8 mapping, desktop jars and
   `version.txt` to GitHub. The signed AAB stays in the Android build artifact for Google Play. These builds need a full clone with tags.
4. F-Droid's update check finds the release, rebuilds the tag and adds the `Builds` entry.
5. The separate Google Play workflow starts after the Release workflow succeeds. Its result does not affect the normal release.

## Google Play

The upload uses the release tag's Fastlane setup and store listing, and downloads the signed AAB, R8 mapping and `version.txt`
from its `android-release` build artifact. The destination track is the GitHub Actions variable `GOOGLE_PLAY_TRACK`.

To retry a failed upload, run **Actions → Publish release to Google Play → Run workflow** and enter the release tag.
Manual runs find the latest successful Release build for that tag's commit and reuse its artifact; no rebuild is needed.

## F-Droid

### Signing

Direct downloads, F-Droid and Play share one signing key. F-Droid rebuilds the tag with `babymonitor.release=true` and, if the output
matches the APK published by CI, distributes that APK with its original signature.

### Store listing

F-Droid reads the shared Fastlane layout under `fastlane/metadata/android/` from the release source.
Include listing changes in a release tag; F-Droid imports them on its own schedule. Google Play uses the same listing files.

- `en-US/`: title, descriptions and graphics (icon, feature graphic, phone screenshots).
- `it-IT/`, `nl-NL/`: translations and localized feature graphics. Other graphics fall back to `en-US`.
- `<locale>/changelogs/default.txt`: shown for the latest release when no version-specific notes exist.
- `docs/play-feature-graphic.odg`: source of the feature graphics.

Text limits: 30 characters for titles, 80 for short descriptions, 4,000 for full descriptions and 500 for release notes.

### Update check

The version code is computed at build time, so F-Droid can't read it from `build.gradle.kts`. `:appAndroid:generateVersionFile` writes
it to `version.txt` instead:

```
versionCode=5002000
versionName=5.2.0
```

The recipe (`metadata/org.vpilo.babymonitor.yml` in fdroiddata) polls that file from the latest release with `UpdateCheckMode: HTTP`:

```
https://github.com/vpilo/nanna-baby-monitor/releases/latest/download/version.txt
```
