# Server Restart

Restarts the server on a countdown, and replaces the vanilla "Server closed" disconnect with
something a player can read.

```
/serverrestart [seconds]   start a countdown, default 30
/serverrestart now         skip the countdown
/serverrestart cancel      stop a countdown in progress
/serverrestart reload      re-read the config
```

Warnings broadcast at 60, 30, 15, 10, 5, 4, 3, 2 and 1 seconds by default. The countdown runs off
the server tick, so everything stays on the server thread.

## It does not restart the server

Bringing the process back up is a launcher's job. This mod kicks everyone, drops a marker file
(`restart.flag` by default) in the run directory and halts the JVM. A restart-on-exit wrapper reads
that file to tell an intentional restart from a plain `/stop`. Two starter scripts are in
`launcher/`, one for each platform.

On managed hosting the panel usually owns process relaunch, so the in-game command may not be what
actually cycles the server. `SERVER_STOPPING` is hooked for that case: whatever triggers the
shutdown - the panel's restart button, a scheduled task, `/stop` - players still get the configured
message rather than "Server closed". The panel handles the restart, this handles the text.

## Config (`config/server-restart.json`)

```json
{
  "defaultCountdownSeconds": 30,
  "warnAtSeconds": [60, 30, 15, 10, 5, 4, 3, 2, 1],
  "warnMessage": "&8[&c&l*&8] &eServer restarting in &f%s&e",
  "restartingBroadcast": "&8[&c&l*&8] &cRestarting now - back in a moment!",
  "kickMessage": "&c&lServer Restarting\n\n&7We're loading updates.\n&7Back in about a minute - see you soon!",
  "cancelledBroadcast": "&8[&a&l+&8] &aServer restart cancelled",
  "restartFlagFile": "restart.flag"
}
```

`%s` in `warnMessage` is the remaining time, rendered as a human string rather than a raw count.
`\n` in `kickMessage` gives you multiple lines on the disconnect screen.

## Server-side only - players install nothing

## Requirements

Drop these into your **server's** `mods/` folder:

| Mod | Version |
| --- | --- |
| `server-restart-0.1.0.jar` | this mod |
| [Fabric API](https://modrinth.com/mod/fabric-api) | `0.155.2+26.2` (or compatible) |
| [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) | `1.13.12+kotlin.2.4.0` (or compatible) |

Minecraft **26.2**, Fabric Loader **0.19.3+**, **Java 25**.

## Limits

Without a restart-on-exit launcher or a hosting panel that relaunches, this is a shutdown with a
countdown. Nothing here starts a process.
