package dev.serverrestart

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * Server-side graceful restart. `/serverrestart [seconds]` counts down on the server tick,
 * broadcasts warnings, kicks everyone with a configurable message and halts the JVM, dropping a
 * marker file (default `restart.flag`) so a restart-on-exit wrapper can tell an intentional restart
 * from a plain `/stop`. `SERVER_STOPPING` is hooked as well, so a shutdown this mod did not trigger
 * still replaces the vanilla "Server closed" text.
 */
object ServerRestartMod : DedicatedServerModInitializer {

    private val LOG = LoggerFactory.getLogger("server-restart")

    private var cfg = Config()

    /** Ticks left until the restart fires, or -1 when no restart is scheduled. */
    @Volatile private var remainingTicks: Int = -1

    override fun onInitializeServer() {
        cfg = Config.load()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val root = Commands.literal("serverrestart")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes { ctx -> schedule(ctx.source, cfg.defaultCountdownSeconds) }
                .then(
                    Commands.literal("now")
                        .executes { ctx -> fireNow(ctx.source) },
                )
                .then(
                    Commands.literal("cancel")
                        .executes { ctx -> cancel(ctx.source) },
                )
                .then(
                    Commands.literal("reload")
                        .executes { ctx -> reload(ctx.source) },
                )
                .then(
                    Commands.argument("seconds", IntegerArgumentType.integer(0))
                        .executes { ctx -> schedule(ctx.source, IntegerArgumentType.getInteger(ctx, "seconds")) },
                )
            val built = dispatcher.register(root)
            // `/restart` as a convenience alias.
            dispatcher.register(
                Commands.literal("restart")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .redirect(built),
            )
        }

        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server -> onTick(server) })

        // Replace the vanilla "Server closed" text on any shutdown, including panel-triggered.
        // Fires at the head of shutdown, before vanilla disconnects the players itself.
        ServerLifecycleEvents.SERVER_STOPPING.register(
            ServerLifecycleEvents.ServerStopping { server -> kickAll(server, Fmt.legacy(cfg.kickMessage)) },
        )

        LOG.info("Server Restart ready. Use /serverrestart [seconds].")
    }

    // ---- command handlers -------------------------------------------------------------------

    private fun schedule(source: CommandSourceStack, seconds: Int): Int {
        if (seconds <= 0) return fireNow(source)
        remainingTicks = seconds * 20
        source.sendSuccess(
            { Fmt.legacy("&8[&e*&8] &7Restart scheduled in &f${humanTime(seconds)}&7. &8(/serverrestart cancel)") },
            true,
        )
        broadcastWarn(source.server, seconds)
        return Command.SINGLE_SUCCESS
    }

    private fun fireNow(source: CommandSourceStack): Int {
        source.sendSuccess({ Fmt.legacy("&8[&c*&8] &7Restarting now...") }, true)
        fire(source.server)
        return Command.SINGLE_SUCCESS
    }

    private fun cancel(source: CommandSourceStack): Int {
        if (remainingTicks < 0) {
            source.sendFailure(Fmt.legacy("&cNo restart is currently scheduled."))
            return 0
        }
        remainingTicks = -1
        broadcast(source.server, cfg.cancelledBroadcast)
        source.sendSuccess({ Fmt.legacy("&aRestart cancelled.") }, true)
        return Command.SINGLE_SUCCESS
    }

    private fun reload(source: CommandSourceStack): Int {
        cfg = Config.load()
        source.sendSuccess({ Fmt.legacy("&aReloaded server-restart config.") }, false)
        return Command.SINGLE_SUCCESS
    }

    // ---- countdown --------------------------------------------------------------------------

    private fun onTick(server: MinecraftServer) {
        if (remainingTicks < 0) return
        if (remainingTicks == 0) {
            remainingTicks = -1
            fire(server)
            return
        }
        // On exact second boundaries, broadcast a warning if this second is a configured mark.
        if (remainingTicks % 20 == 0) {
            val secs = remainingTicks / 20
            if (secs in cfg.warnAtSeconds) broadcastWarn(server, secs)
        }
        remainingTicks--
    }

    private fun fire(server: MinecraftServer) {
        LOG.info("Server restart firing - kicking {} player(s) and halting.", server.playerList.players.size)
        broadcast(server, cfg.restartingBroadcast)
        dropRestartFlag()
        kickAll(server, Fmt.legacy(cfg.kickMessage))
        // Graceful shutdown; the main loop exits after this tick and the JVM returns. This also
        // fires SERVER_STOPPING, but the player list is already empty so nobody is kicked twice.
        server.halt(false)
    }

    /** Disconnect everyone with [reason]. Snapshots the list first, because disconnecting mutates it. */
    private fun kickAll(server: MinecraftServer, reason: Component) {
        server.playerList.players.toList().forEach { it.connection.disconnect(reason) }
    }

    /** Marker file so a restart-on-exit launcher knows this exit was an intentional restart. */
    private fun dropRestartFlag() {
        try {
            val flag = FabricLoader.getInstance().gameDir.resolve(cfg.restartFlagFile)
            Files.write(flag, ByteArray(0))
            LOG.info("Wrote restart marker: {}", flag)
        } catch (e: Exception) {
            LOG.error("Could not write restart marker file - the launcher may not relaunch.", e)
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private fun broadcastWarn(server: MinecraftServer, seconds: Int) {
        broadcast(server, cfg.warnMessage.format(humanTime(seconds)))
    }

    private fun broadcast(server: MinecraftServer, legacy: String) {
        val msg = Fmt.legacy(legacy)
        server.playerList.players.forEach { it.sendSystemMessage(msg) }
    }

    private fun humanTime(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        val parts = ArrayList<String>(2)
        if (m > 0) parts += "$m minute" + if (m == 1) "" else "s"
        if (s > 0 || m == 0) parts += "$s second" + if (s == 1) "" else "s"
        return parts.joinToString(" ")
    }
}
