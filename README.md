# Baby Monitor app

# Feature plan

## Remaining pairing/security work
* PairingCoordinator: investigate making into repository/UseCases
* WebSocketConnectionHandler takes the pairing repository, needs to be removed and replaced with a fingerprint parameter; ultimately CameraSelectionScreenViewModel can give the fingerprint to the network client. It also uses Koin directly to get the relay config, must be given to it.

## Will have
- Remember the drag position in the PanningVideoFeed
- Report if camera is unavailable, and enforce audio mode
- Report if audio is unavailable, and enforce video mode
- Quit if neither audio nor video are available, e.g. on a Raspberry Pi.

## Nice to have someday
- Hide client UI with a tap on the video feed, to see only the video feed.
- On server and/or client, allow disabling video rotation.
- brightness correction should be done (or at least controlled) on the client, not the server.
- On relay settings, show whether the relay is reachable and the passphrase accepted.
- Quiet mode: play a sound when there's activity on video and/or audio. if a bt headset is connected, pressing play on it will start
  streaming audio.
- Relay: investigate something like UDP hole-punching to simplify setup. RustDesk has a rendezvous server doing it.

# Issues
- android: on gl renderer release() wait until draw is done. easier to repro with high camera resolution.
- client does not close everything when notification is tapped; local discovery for sure is still on
- When returning to role selection screen, sometimes the app gets stuck for a few seconds
- OnBackInvokedCallback is not enabled for the application. Set 'android:enableOnBackInvokedCallback="true"' in the application manifest.
- improve error handling and surfacing issues to the user
- add versioning to relay communications

# Resources

ui writing tips: https://proandroiddev.com/10-jetpack-compose-ui-tricks-you-probably-dont-know-d3dd63b617c9
may try out https://github.com/kinsleykajiva/jopus to replace ffmpeg&mediacodec for audio streaming
