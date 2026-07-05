# Baby Monitor Relay installation

A systemd user service runs the relay in background on Linux easily, restarting it on reboot/crash.

## Install

1. Build the uber jar (works on any architecture):
   ```sh
   rm -f appRelay/build/compose/jars/*
   ./gradlew :appRelay:packageUberJarForCurrentOS
   ```
2. Copy the two files into place:
   ```sh
   mkdir -p ~/.local/share ~/.config/systemd/user
   cp appRelay/build/compose/jars/org.vpilo.babymonitor.relay-*.jar ~/.local/share/babymonitor-relay.jar
   cp appRelay/systemd/babymonitor-relay.service ~/.config/systemd/user/
   ```
3. Edit the service file to set your own host name:
   ```sh
    RELAY_HOST='some.host.name' # edit this
   sed -i -re "s/<my-host-name>/$RELAY_HOST/" babymonitor-relay.service
    ```
4. Enable and start:
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

To update, rebuild:

```sh
rm -f appRelay/build/compose/jars/*
./gradlew :appRelay:packageUberJarForCurrentOS
```
Then overwrite the jar and restart:
```sh
systemctl --user stop babymonitor-relay
cp appRelay/build/compose/jars/org.vpilo.babymonitor.relay-*.jar ~/.local/share/babymonitor-relay.jar
systemctl --user start babymonitor-relay
```
Note that using 'systemctl restart' doesn't work well when replacing the original jar. Stop, copy and start the service instead.
