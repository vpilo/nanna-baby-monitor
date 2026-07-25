# Baby Monitor app

# Feature plan

## Remaining pairing/security work
* Relay is broken due to new security model
* PairingCoordinator and ClientPairingConnector: investigate making into repositories + UseCases

## Will have
- Add a 'live' red icon in server home screen when it is actually streaming (maybe grayed out while loading and/or when no frames come in)
- Add an 'eye' icon in client home screen.
- Remember the drag position in the PanningVideoFeed
- Show the animated loading icon while video connection is being established
- Add a 'close app' button/action on android notification
- Report if camera is unavailable, and enforce audio mode
- Report if audio is unavailable, and enforce video mode
- Quit if neither audio nor video are available, e.g. on a Raspberry Pi.

## Nice to have someday
- Hide client UI with a tap on the video feed, to see only the video feed.
- On server and/or client, allow disabling video rotation.
- brightness correction should be done (or at least controlled) on the client, not the server.
- On relay setting, see if reachable and if authenticated.
- Quiet mode: play a sound when there's activity on video and/or audio. if a bt headset is connected, pressing play on it will start
  streaming audio.
- Relay: investigate something like UDP hole-punching to simplify setup. RustDesk has a rendezvous server doing it.

# Issues
- android: on gl renderer release() wait until draw is done. easier to repro with high camera resolution.
- Clients sometimes reconnect in background even after killing the app (notification shows up)
- When returning to role selection screen, sometimes the app gets stuck for a few seconds
- OnBackInvokedCallback is not enabled for the application. Set 'android:enableOnBackInvokedCallback="true"' in the application manifest.
- improve error handling and surfacing issues to the user
- add versioning to relay communications

# Completed features
- Secure communication channels with clients (monitors) pairing with server (camera).
- Server can also choose to toggle audio and video capture independently
- Client UI shows server battery level and signal strength
- Device name selection on both server and client
- allow capture resolution choice
- settings system to save audio and video quality preferences, and other settings
- settings screen in menu
- Client can play and pause independently audio or video
- Relay server to allow clients to connect to a server from outside the local network.
- Filter to cut playback of silence.
- Systemctl service to re-run relay when closed/crashed.
- on internet connectivity change, restart discovery and service advertising. On client, server and relay.
- Tapping on the Android notification should open the app.
- Add slider setting for noise sensitivity.
- on client home, show if connected directly or via relay.

# Resources

ui writing tips: https://proandroiddev.com/10-jetpack-compose-ui-tricks-you-probably-dont-know-d3dd63b617c9
may try out https://github.com/kinsleykajiva/jopus to replace ffmpeg&mediacodec for audio streaming

# References / inspiration

https://github.com/zeenolife/ai-baby-monitor
https://github.com/danmacnaughtan/baby-monitor
https://github.com/trunglee17/Monitoring-Baby-System-based-on-Deep-Learning
https://github.com/codeperfectplus/AI-Baby-Monitor
https://github.com/timrappold/WeeBro
https://github.com/sdaniel631/BabyMonitor
https://github.com/enguerrand/child-monitor
