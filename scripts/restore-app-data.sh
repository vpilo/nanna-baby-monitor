#!/bin/sh
set -eu

package=org.vpilo.babymonitor
archive=nanna.tar

tar -tf "$archive" files/babymonitor.preferences_pb > /dev/null
adb shell am force-stop "$package"
adb shell -T run-as "$package" tar -xf - < "$archive"
printf 'Restored app data from %s\n' "$archive"
