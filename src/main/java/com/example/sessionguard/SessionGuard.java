package com.example.sessionguard;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SessionGuard extends JavaPlugin {

    private ScheduledTask cleanupTask;
    private final Map<UUID, String> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentlyKicked = new ConcurrentHashMap<>();
    private final Set<UUID> processingKicks = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("sessionguard").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(new SessionListener(this), this);
        scheduleCleanupTask();
        getLogger().info("SessionGuard v" + getDescription().getVersion() + " enabled!");
    }

    private void scheduleCleanupTask() {
        long intervalTicks = 20L * 10L; // Every 10 seconds for quick cleanup

        cleanupTask = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(this, task -> {
                    cleanupExpiredData();
                }, 100L, intervalTicks);
    }

    private void cleanupExpiredData() {
        long currentTime = System.currentTimeMillis();
        long reconnectDelay = getConfig().getLong("reconnection-delay", 2L) * 1000L;

        // Clean recently kicked players after delay
        recentlyKicked.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > reconnectDelay);

        // Clean processing kicks
        processingKicks.clear();

        // Clean inactive sessions
        int cleaned = 0;
        Iterator<Map.Entry<UUID, String>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, String> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                cleaned++;
            }
        }

        if (getConfig().getBoolean("logging.enabled", true) && cleaned > 0) {
            getLogger().info("Cleaned " + cleaned + " inactive sessions");
        }
    }

    public CompletableFuture<Boolean> handleDuplicateLogin(UUID uuid, String username, String ipAddress) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Check if this UUID is already being processed
        if (processingKicks.contains(uuid)) {
            future.complete(false);
            return future;
        }

        // Check if player was recently kicked (within reconnection delay)
        Long kickTime = recentlyKicked.get(uuid);
        long reconnectDelay = getConfig().getLong("reconnection-delay", 2L) * 1000L;

        /* if (kickTime != null && System.currentTimeMillis() - kickTime < reconnectDelay) {
            // Player is reconnecting within allowed time, allow it
            if (getConfig().getBoolean("logging.enabled", true)) {
                getLogger().info("Allowing reconnection for " + username + " (UUID: " + uuid + ") within delay period");
            }
            future.complete(true); // true means allow connection
            return future;
        } */

        Player existingPlayer = Bukkit.getPlayer(uuid);
        if (existingPlayer == null && getConfig().getBoolean("check-username", true)) {
            existingPlayer = Bukkit.getPlayer(username);
        }

        if (existingPlayer == null || !existingPlayer.isOnline()) {
            future.complete(true); // No duplicate found, allow connection
            return future;
        }

        // Mark as processing to prevent loops
        processingKicks.add(uuid);

        final Player finalPlayer = existingPlayer;
        String kickMessage = getConfig().getString("kick-message",
                "§cYou were disconnected because you logged in from another location.");

        Component kickComponent = Component.text(kickMessage);

        // Kick existing player using Folia's thread-safe scheduler
        finalPlayer.getScheduler().run(this, task -> {
            try {
                finalPlayer.kick(kickComponent);

                // Store kick time for reconnection delay
                recentlyKicked.put(uuid, System.currentTimeMillis());

                if (getConfig().getBoolean("logging.enabled", true)) {
                    getLogger().info("Kicked existing player '" + finalPlayer.getName() +
                            "' (UUID: " + uuid + ") for duplicate login by '" + username + "'");
                }

                future.complete(true); // Successfully kicked, allow new connection

            } catch (Exception e) {
                getLogger().severe("Failed to kick player '" + finalPlayer.getName() + "': " + e.getMessage());
                future.complete(false); // Failed to kick, don't allow new connection
            } finally {
                processingKicks.remove(uuid);
            }
        }, () -> {
            // Task couldn't be scheduled
            getLogger().warning("Could not schedule kick task for player: " + finalPlayer.getName());
            processingKicks.remove(uuid);
            future.complete(false);
        });

        return future;
    }

    public void addSession(UUID uuid, String username) {
        activeSessions.put(uuid, username);
        recentlyKicked.remove(uuid); // Remove from kicked list when they successfully login

        if (getConfig().getBoolean("logging.enabled", true)) {
            getLogger().info("Session started for " + username + " (UUID: " + uuid + ")");
        }
    }

    public void removeSession(UUID uuid) {
        String username = activeSessions.remove(uuid);

        if (getConfig().getBoolean("logging.enabled", true) && username != null) {
            getLogger().info("Session ended for " + username + " (UUID: " + uuid + ")");
        }
    }

    public boolean hasActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("sessionguard.admin")) {
            sender.sendMessage("§cNo permission!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6§lSessionGuard v" + getDescription().getVersion());
            sender.sendMessage("§eActive sessions: §f" + activeSessions.size());
            sender.sendMessage("§eRecently kicked: §f" + recentlyKicked.size());
            sender.sendMessage("§eReconnection delay: §f" + getConfig().getLong("reconnection-delay", 2) + "s");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            if (cleanupTask != null) {
                cleanupTask.cancel();
            }
            scheduleCleanupTask();
            sender.sendMessage("§aConfiguration reloaded!");
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            sender.sendMessage("§6§lDebug Information:");
            sender.sendMessage("§eActive sessions: §f" + activeSessions.size());
            sender.sendMessage("§eRecently kicked: §f" + recentlyKicked.size());
            sender.sendMessage("§eProcessing kicks: §f" + processingKicks.size());
            return true;
        }

        return true;
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        getLogger().info("SessionGuard disabled!");
    }
}