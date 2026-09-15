# Releasing

A release is a git tag. CI builds and publishes it, and F-Droid picks it up from there.

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
2. Tag and push. The tag message becomes the release notes.
   ```sh
   git tag -a 5.2.0 -m "Release notes"
   git push origin 5.2.0
   ```
3. The CI `release` jobs build with `-Pbabymonitor.release=true` and publish the signed APK and AAB, the desktop
   jars and `version.txt`. They need a full clone with tags.
4. F-Droid's update check finds the release, rebuilds the tag and adds the `Builds` entry.

## F-Droid

### Signing

Direct downloads, F-Droid and Play share one signing key. F-Droid rebuilds the tag with `babymonitor.release=true` and, if the output
matches the APK published by CI, distributes that APK with its original signature.

### Store listing

F-Droid reads it from this repository.

- `metadata/android/en-US/`: title, descriptions, changelog and screenshots.
- `metadata/android/it-IT/`, `nl-NL/`: translations. Screenshots fall back to `en-US`.
- `changelogs/default.txt`: shown only for the latest release (`CurrentVersionCode`).

### Update check

The version code is computed at build time, so F-Droid can't read it from `build.gradle.kts`. `:appAndroid:generateVersionFile` writes
it to `version.txt` instead:

```
versionCode=5002000
versionName=5.2.0
```

The recipe (`metadata/org.vpilo.babymonitor.yml` in fdroiddata) polls that file from the latest release with `UpdateCheckMode: HTTP`:

```
https://gitlab.com/vpilo/nanna-baby-monitor/-/releases/permalink/latest/downloads/version.txt
```

To test it from an fdroiddata checkout:

```sh
fdroid checkupdates -v org.vpilo.babymonitor
```
