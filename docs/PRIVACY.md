# Privacy Policy for Nanna Baby Monitor

**Last updated:** September 9, 2026

Nanna Baby Monitor ("Nanna", "the app") is a free, open source baby monitor developed by Valerio Pilo. This policy explains what the app
does and does not do with your information.

## Summary

**Nanna collects no personal data.** There is no account, no analytics, no advertising, no tracking, and no crash reporting. The developer
operates no servers and receives no information from the app whatsoever.

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

## Permissions

- **Camera, Microphone** — capture the audio and video to stream to your monitor devices.
- **Notifications, Foreground service (camera, microphone, media playback)** — keep the monitor working while the app is in the background
  or the screen is off.
- **Internet, Network and Wi-Fi state, Local network access, Wi-Fi multicast** — discover your other devices on the local network and stream
  to them.

These permissions are used solely for the functionality described above.

## Children

The app is used by caregivers to watch over children. It does not knowingly collect information from anyone, children included, because it
collects no information at all.

## Third parties

Nanna contains no third-party SDKs, no advertising libraries, and no analytics services. Nothing is shared with or sold to anyone.

## Changes

Any change to this policy will be published at this page, with an updated date above. The full history is publicly auditable in the app's
source repository.

## Contact

Questions about this policy: **nanna@coldshock.net**

Source code: https://github.com/vpilo/nanna-baby-monitor
