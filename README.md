# Baby Monitor app

# Feature plan

- Hide client UI with a tap on the video feed, to see only the video feed.
- More secure communication channels with clients (monitors) pairing with server (camera).
- Plugins for raw AV streams to:
    - reduce noise
    - detect no movement
- Quiet mode: play a sound when there's activity on video and/or audio. if a bt headset is connected, pressing play on it will start
  streaming audio.
- Report if camera is unavailable, and enforce audio mode
- Report if audio is unavailable, and enforce video mode
- Quit if neither audio nor video are available, e.g. on a Raspberry Pi.
- Add slider setting for noise sensitivity.
- On server and/or client, allow disabling video rotation.
- Tapping on the Android notification should open the app.

# Issues

- remove nullability for PanningVideoFeed in screens by making a custom version for Previews (from makePlaceholderCameraFrame)
- All DeviceStateRepository values should be `distinctUntilChanged` on both platforms.
- AudioAttributes.USAGE_MEDIA -> USAGE_VOICE_COMMUNICATION ?

# Completed features

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

# Resources

app updates: https://github.com/pavi2410/kmp-app-updater
window tricks for compose desktop: https://dev.to/coltonidle/compose-for-desktop-window-tricks-55mf
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
