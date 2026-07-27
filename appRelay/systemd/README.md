# Baby Monitor Relay installation

A systemd user service runs the relay in background on Linux easily, restarting it on reboot/crash.

Note that pairing cannot happen over a relay. Pair on the local network first.

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
3. Start the relay once to make a template config file, then edit it to set the hostname:
   ```sh
   java -jar ~/.local/share/babymonitor-relay.jar
   nano ~/.config/babymonitor/relay.conf # or any other program to edit the configuration
    ```
   The template will have two lines `host=` and `passphrase=`:
   - `host` — public hostname or IP the apps reach the relay on.
   - `passphrase` — shared secret. Enter the same value in every app under the relay settings; without it the relay refuses every connection.
4. Enable and start the systemd service:
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
