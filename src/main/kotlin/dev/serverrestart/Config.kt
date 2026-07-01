package dev.serverrestart

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * User-editable config, stored at `config/server-restart.json`. All messages use `&` legacy colour
 * codes; a literal `\n` in a message becomes a real line break (handy for the multi-line disconnect
 * screen).
 */
data class Config(
    // Default countdown (seconds) when `/serverrestart` is run with no number.
    var defaultCountdownSeconds: Int = 30,
    // Seconds-remaining marks at which a warning is broadcast to online players.
    var warnAtSeconds: List<Int> = listOf(60, 30, 15, 10, 5, 4, 3, 2, 1),
    // Broadcast shown at each warn mark. `%s` is replaced with a human time ("30 seconds", "1 minute").
    var warnMessage: String = "&8[&c&l*&8] &eServer restarting in &f%s&e",
    // Broadcast to everyone the instant the restart fires, just before they're kicked.
    var restartingBroadcast: String = "&8[&c&l*&8] &cRestarting now - back in a moment!",
    // The disconnect-screen message each player sees when kicked for the restart.
    var kickMessage: String = "&c&lServer Restarting\n\n&7We're loading updates.\n&7Back in about a minute - see you soon!",
    // Broadcast when a scheduled restart is cancelled with `/serverrestart cancel`.
    var cancelledBroadcast: String = "&8[&a&l+&8] &aServer restart cancelled",
    // Name of the marker file dropped in the server run directory just before halting, so a
    // restart-on-exit launcher can tell an intentional restart apart from a plain /stop.
    var restartFlagFile: String = "restart.flag",
) {
    companion object {
        private val LOG = LoggerFactory.getLogger("server-restart")
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        private fun path(): Path =
            FabricLoader.getInstance().configDir.resolve("server-restart.json")

        fun load(): Config {
            val p = path()
            return try {
                if (Files.exists(p)) {
                    val cfg = Files.newBufferedReader(p).use { GSON.fromJson(it, Config::class.java) }
                        ?: Config()
                    // Re-save so any newly added fields get written back with their defaults.
                    cfg.save()
                    cfg
                } else {
                    Config().also {
                        it.save()
                        LOG.info("Wrote default config to {}", p)
                    }
                }
            } catch (e: Exception) {
                LOG.error("Failed to read {} - using defaults.", p, e)
                Config()
            }
        }

        private fun Config.save() {
            val p = path()
            try {
                Files.createDirectories(p.parent)
                Files.newBufferedWriter(p).use { GSON.toJson(this, it) }
            } catch (e: Exception) {
                LOG.error("Failed to write {}", p, e)
            }
        }
    }
}
