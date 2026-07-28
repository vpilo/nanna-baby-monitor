# Baby Monitor

This is a baby monitor app, with secure video and audio streaming, for Android and PC (using Java). Not just for babies!

This app requires pairing between devices. Run the app in recording mode on one device (any Android 8+ device or a PC with a webcam). Using a pairing code or a QR, pair it with another devices set to watching mode. Connect anytime to watch and/or listen.

Both the recording and the watching devices can be any Android 8+ device or any PC with a webcam. Obviously, the recording device needs a camera and microphone, and the watching device needs a screen and optional speakers!

The app provides a relay app (also using Java) to allow paired devices to see each other from anywhere. Both recording and watching devices can be then connected to any network, they only need to be able to connect to the relay hostname/port.
The relay can be installed on a home server (with Dynamic DNS, e.g. duckdns.org, and with port `47814` forwarded to it), but also on a cloud service if you have one.

# Features

- Secure video and audio streaming.
- Network discovery of recording apps on the same network.
- Secure pairing between devices, using a pairing code or QR code.
- Low light boost to see better in low light conditions.
- Silence detection to avoid sending audio when it's all quiet.
- Customizable recording quality.
- Draggable video feed to see the whole video feed on any screen.
- In theory, it works on any Android 8+ device and any computer with a webcam.
- Supports multiple recording devices and multiple watching devices.
- Relay server to allow devices to connect from anywhere.
- Private, and without any cloud services or telemetry: this stays on your home network, and you can run your own relay server to stream from anywhere to anywhere.

# Installation

## Android

..

## Desktop

..

## Relay server

See the [relay README](appRelay/systemd/README.md) for instructions on how to install the relay server.

# AI disclaimer

This app is *not* vibe coded. If it were, there would be many more tests :grimacing:

While the most of the app is my own, certain features of this app were developed with the aid of AI tools: GPU-based camera processing, the security protocols, and pairing were all developed using Claude.

As a new parent, without AI I would have never been able to get these features done to make the app publishable.
