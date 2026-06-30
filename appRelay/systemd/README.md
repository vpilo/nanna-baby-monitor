# Baby Monitor Relay installation

A systemd user service runs the relay in background on Linux easily, restarting it on reboot/crash.

## Install

1. Build the uber jar (works on any architecture):
   ```sh
   ./gradlew :appRelay:packageUberJarForCurrentOS
   ```
2. Drop the two files into place:
   ```sh
   mkdir -p ~/.local/share ~/.config/systemd/user
   cp appRelay/build/compose/jars/org.vpilo.babymonitor.relay-*-1.0.0.jar ~/.local/share/babymonitor-relay.jar
   cp appRelay/systemd/babymonitor-relay.service ~/.config/systemd/user/
   ```
3. Enable and start:
   ```sh
   systemctl --user daemon-reload
   systemctl --user enable --now babymonitor-relay
   ```

To keep it running when you're logged out: `sudo loginctl enable-linger $USER`.

## Manage

- Logs: `journalctl --user -u babymonitor-relay -f`
- Status: `systemctl --user status babymonitor-relay`
- Stop / disable: `systemctl --user disable --now babymonitor-relay`

## Update

Rebuild, then overwrite the jar and restart:

```sh
./gradlew :appRelay:packageUberJarForCurrentOS
cp appRelay/build/compose/jars/org.vpilo.babymonitor.relay-*-1.0.0.jar ~/.local/share/babymonitor-relay.jar
systemctl --user restart babymonitor-relay
```
