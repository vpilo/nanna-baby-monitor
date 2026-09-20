# Privacy Policy for Nanna Baby Monitor

**Last updated:** September 21, 2026

Nanna Baby Monitor ("Nanna", "the app") is a free, open source baby monitor developed by Valerio Pilo. This policy explains what the app
does and does not do with your information.

## Summary

**Nanna collects no personal data.** There is no account, no analytics, no advertising, no tracking, and no automatic error reporting.
The developer operates no servers and receives no information from the app, except error reports which you may manually send.

## Error reporting

The app keeps a record of its operations (logging) of the current session, and deletes it either when the app is closed normally or during
the next session.
When the app stops unexpectedly, you are asked whether you want to share or email an error report to the developer. This is
entirely optional.
You can save and review the draft email with the report before sending it. No personally identifying information is sent as part of an error
report: the record contains no personal data, and to help fix issues contains the following details: logging data, device make/model or
desktop OS and architecture, OS or runtime version, app version, current app role (camera or monitor).
Declining or failing to send the report destroys it. The logging information is destroyed regardless.

## Audio and video

- When a device acts as the camera, Nanna captures audio and video and streams it directly to your own monitor devices over your local
  network. Streaming only starts when a paired monitor device is connected.
- Audio and video are **never recorded, stored, or uploaded**. Frames exist in memory only for as long as it takes to send them.
- Streams are end-to-end encrypted. Devices are paired by scanning a QR code or entering a PIN, and only paired devices can connect.

## Optional relay

If your devices are on different networks, you may run the optional relay component. The relay is software you host yourself, on hardware
you control, protected by a passphrase you choose. It forwards encrypted data it cannot read. The developer neither hosts nor has access to
any relay.

## Data stored on your device

Nanna stores your settings and the encryption keys of devices you have paired in the app's private storage. This data never leaves your
device, is not readable by other apps, and is deleted when you uninstall the app.
The app stores the current session's logging for optional manual error reporting at the next session.

## Permissions

- **Camera, Microphone** — capture the audio and video to stream to your monitor devices.
- **Notifications, Foreground service (camera, microphone, media playback)** — keeps capturing on the camera, and receiving on the monitor,
  while the app is in the background or the screen is off.
- **Internet, Network and Wi-Fi state, Local network access, Wi-Fi multicast** — discover your other devices on the local network and stream
  to them.

These permissions are used solely for the functionality described above.

## Children

The app is used by caregivers to watch over children. It does not knowingly collect information from anyone at all, children included.

## Third parties

Nanna contains no third-party SDKs, no advertising libraries, and no analytics services. Nothing is shared with or sold to anyone.

## Changes

Any change to this policy will be published at this page, with an updated date above. The full history is publicly auditable in the app's
source repository.

## Contact

Questions about this policy: **nanna@coldshock.net**

Source code: https://github.com/vpilo/nanna-baby-monitor
