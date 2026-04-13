# Baby Monitor app

feature plan

- Client can play and pause independently audio or video 
- Client UI hideable with a tap on the video feed.
- Secure lan-only communication channel, with clients (monitors) pairing with server (camera).
- Plugins for raw AV streams to:
    - reduce noise
    - detect silence
    - detect no movement
- Quiet mode: play a sound when there's activity on video and/or audio. if a bt headset is connected, pressing play on it will start streaming audio.

completed

- Server can also choose to toggle audio and video capture independently
- Client UI shows server battery level and signal strength
- Device name selection on both server and client
- allow capture resolution choice
- settings system to save audio and video quality preferences, and other settings
- settings screen in menu

references

https://github.com/zeenolife/ai-baby-monitor
https://github.com/danmacnaughtan/baby-monitor
https://github.com/trunglee17/Monitoring-Baby-System-based-on-Deep-Learning
https://github.com/codeperfectplus/AI-Baby-Monitor
https://github.com/timrappold/WeeBro
https://github.com/sdaniel631/BabyMonitor
https://github.com/enguerrand/child-monitor

use https://github.com/pavi2410/kmp-app-updater for app updates

https://dev.to/coltonidle/compose-for-desktop-window-tricks-55mf window tricks for compose desktop

https://proandroiddev.com/10-jetpack-compose-ui-tricks-you-probably-dont-know-d3dd63b617c9 for ui writing tips

use https://github.com/kinsleykajiva/jopus to replace ffmpeg and android mediacodec for audio.
