#!/bin/sh
set -eu

package=org.vpilo.babymonitor
archive=nanna.tar

adb shell am force-stop "$package"

temporary_archive=$(mktemp "./$archive.XXXXXX")
trap 'rm -f "$temporary_archive"' EXIT

adb shell -T -n run-as "$package" tar -cf - files > "$temporary_archive"
tar -tf "$temporary_archive" files/babymonitor.preferences_pb > /dev/null
mv "$temporary_archive" "$archive"
printf 'Saved app data to %s\n' "$archive"
