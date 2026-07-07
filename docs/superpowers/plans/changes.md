CameraSelectionScreen
- I want a group for paired devices and a group for unpaired ones
- paired devices are sorted by online/not online
- unpaired are only online ones 

AppMenuContents
- forget device for client

ServerIdentity
- change to object
- share KEY_ALIAS = "babymonitor-server" as it's used in other places (eg the CN= in this class)

DefaultNetworkServerRepository
set with a let {}, don't like

ClientPairingConnector
make to a repository and set up a UseCase to chain them

toml
kotlinx-coroutines-test move to test section

PairedDevicesScreenViewModel
- do we really need the networ repo to disconnect? (nicer with a leaner repo)

ServerPairingScreen
- mention pairing is only available while the screen is active
- mention pairing can only be done in the same network as the client

ClientPairingScreen
- mention pairing can only be done in the same network as the server

DefaultNetworkRelayRepository
- authentication is now completely missing and broken, need to remake it.

:camera:presentation
- android: mlkit import; is there a smaller lib?
- desktop: missing qr reader altogether, nicer if you can scan on desktop if a camera is available.
- both: if camera is available show both cam preview and pin input.
- 
