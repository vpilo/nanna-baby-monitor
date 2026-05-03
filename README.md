# Baby Monitor app

# Feature plan

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
- Systemctl service to re-run relay when closed/crashed.

# Issues

- The role selection screen show be redone.
- Fix ktor engines setup.
- on exit, close all http clients, servers, etc first.
- where is the notification?
Trailing reconnect race in receiver repo. The onDisconnected lambda captures the field reference handler?.connect(). Between stop() setting handler = null and the lambda's delay, if a fresh start() ran and assigned a new handler, the lambda would call the new handler's connect(). New handler also calls connect() itself, but WebSocketConnectionHandler.connect() short-circuits if connectionJob?.isActive == true (line 36–38), so the duplicate is a no-op. Acceptable.
- PlayReceivedAudioUseCase.toggle relies on invokeOnCompletion for _isPlaying=false
- AppViewModel.collectLatest extension is actually collect
- **Server-facing flap.** Rapid resume/pause cycles or fast capture-mode changes flap the per-medium WebSocket open/closed.
- **`PlayReceivedAudioUseCase.toggle` race.** The `shouldPlayAudio.subscribe` block guards on `playReceivedAudio.isPlaying.value`, which lags reality (it only goes back to `false` via the playback job's `invokeOnCompletion`). A pathological rapid off-then-on toggle could leave audio off because the lagged value matches the new desired value. Latency through the gate (settings save → settings flow → combine → distinctUntilChanged → subscribe) is generally larger than the cancellation window, so this is unlikely in practice. If observed, change `PlayReceivedAudioUseCase` to expose `setPlaying(Boolean)` that branches on `playbackJob?.isActive == true` rather than on `_isPlaying.value`, and call that from the VM.
- **Trailing reconnect race in receiver repo.** The `onDisconnected` lambda captures the field reference `handler?.connect()`. If `stop()` runs and a fresh `start()` reassigns `handler` before the `delay(RECONNECTION_TIMEOUT)` completes, the lambda calls the new handler's `connect()`. The new handler also calls `connect()` itself, but `WebSocketConnectionHandler.connect()` short-circuits if `connectionJob?.isActive == true`, so the duplicate is a no-op.


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
