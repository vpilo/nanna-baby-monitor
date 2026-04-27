# Baby Monitor app

# Feature plan

- present the server name on client UI
- Hide client UI with a tap on the video feed, to see only the video feed.
- More secure communication channels with clients (monitors) pairing with server (camera).
- Plugins for raw AV streams to:
    - reduce noise
    - detect silence
    - detect no movement
- Quiet mode: play a sound when there's activity on video and/or audio. if a bt headset is connected, pressing play on it will start
  streaming audio.
- Report if camera is unavailable, and enforce audio mode
- Report if audio is unavailable, and enforce video mode
- Quit if neither audio nor video are available, e.g. on a Raspberry Pi.
- Improve automatic reconnection to server.
- Show name of the server on the client UI instead of "Monitor".
- Systemctl service to re-run relay when closed/crashed.

# Issues

- Check server lifecycle. there should always be a notification active, also allowing the user to stop the server. It should not restart when killed manually. Server must keep streaming when the screen goes off.
- Client should keep reconnecting automatically on disconnection, and offer a button to disconnect manually.
- The role selection screen show be redone.
- Fix ktor engines setup.
- on exit, close all http clients, servers, etc first.

# Completed features

- Server can also choose to toggle audio and video capture independently
- Client UI shows server battery level and signal strength
- Device name selection on both server and client
- allow capture resolution choice
- settings system to save audio and video quality preferences, and other settings
- settings screen in menu
- Client can play and pause independently audio or video
- Relay server to allow clients to connect to a server from outside the local network.

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
