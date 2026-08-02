# Feature plan

## Remaining pairing/security work

* PairingCoordinator: investigate making into repository/UseCases
* WebSocketConnectionHandler takes the pairing repository, needs to be removed and replaced with a fingerprint parameter; ultimately CameraSelectionScreenViewModel can give the fingerprint to the network client. It also uses Koin directly to get the relay config, must be given to it.

## Nice to have

- when a device is renamed, call `PairingStorageRepository.updateName` and update the UI.
- Remember the drag position in the PanningVideoFeed
- Report if camera is unavailable, and enforce audio mode
- Report if audio is unavailable, and enforce video mode
- Quit if neither audio nor video are available, e.g. on a Raspberry Pi.
- Hide client UI with a tap on the video feed, to see only the video feed.
- On server and/or client, allow disabling video rotation.
- brightness correction should be done (or at least controlled) on the client, not the server.
- On relay settings, show whether the relay is reachable and the passphrase accepted.
- Quiet mode: play a sound when there's activity on video and/or audio. if a bt headset is connected, pressing play on it will start
  streaming audio.
- Relay: investigate something like UDP hole-punching to simplify setup. RustDesk has a rendezvous server doing it.

# Known issues

- android: must request camera and microphone permissions at runtime for both pairing and recording.
- android: there is a memory leak.
- android: on gl renderer release() wait until draw is done. easier to repro with high camera resolution.
- client does not close everything when 'close app' notification is tapped; eg local discovery might still be on.
- When returning to role selection screen from server:
  - the app gets stuck for a few seconds due to graceful server shutdown.
  - the service host may crash because the server is still registered when something client side tries to register.
- Android warning: OnBackInvokedCallback is not enabled for the application. Set 'android:enableOnBackInvokedCallback="true"' in the application manifest.
- ensure versioning is embedded in all communications - device-device and device-relay - to avoid version mismatches.

# Resources

ui writing tips: https://proandroiddev.com/10-jetpack-compose-ui-tricks-you-probably-dont-know-d3dd63b617c9
may try out https://github.com/kinsleykajiva/jopus to replace ffmpeg&mediacodec for audio streaming
