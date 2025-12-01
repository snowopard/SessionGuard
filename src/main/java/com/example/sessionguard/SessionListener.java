package com.example.sessionguard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SessionListener implements Listener {

    private final SessionGuard plugin;

    public SessionListener(SessionGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String username = event.getName();
        String ipAddress = event.getAddress().getHostAddress();

        if (plugin.hasActiveSession(uuid)) {
            if (plugin.getConfig().getBoolean("logging.enabled", true)) {
                plugin.getLogger().info("Duplicate login detected for " + username + " (UUID: " + uuid + ") from IP: " + ipAddress);
            }

            // Handle duplicate login with timeout
            CompletableFuture<Boolean> future = plugin.handleDuplicateLogin(uuid, username, ipAddress);

            try {
                // Wait for kick to complete with timeout
                Boolean result = future.get(3, TimeUnit.SECONDS);

                if (result != null && result) {
                    // Kick successful, allow new connection
                    event.allow();
                    if (plugin.getConfig().getBoolean("logging.enabled", true)) {
                        plugin.getLogger().info("Allowing reconnection for " + username);
                    }
                } else {
                    // Kick failed or shouldn't allow
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                            plugin.getConfig().getString("connection-failed-message",
                                    "Could not establish connection. Please try again."));
                }
            } catch (Exception e) {
                // Timeout or error
                plugin.getLogger().warning("Timeout handling duplicate login for " + username + ": " + e.getMessage());
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        "Connection timeout. Please try again.");
            }
        } else {
            // No duplicate, allow connection
            event.allow();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        Player player = event.getPlayer();
        plugin.addSession(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Send welcome message if configured
        String welcomeMessage = plugin.getConfig().getString("welcome-message");
        if (welcomeMessage != null && !welcomeMessage.isEmpty()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (player.isOnline()) {
                    player.sendMessage(welcomeMessage);
                }
            }, 20L); // 1 second delay
        }

        if (plugin.getConfig().getBoolean("logging.enabled", true)) {
            plugin.getLogger().info("Player " + player.getName() + " joined successfully");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Don't remove immediately if reconnection is expected
        boolean removeOnQuit = plugin.getConfig().getBoolean("remove-on-quit", false);

        if (removeOnQuit) {
            plugin.removeSession(uuid);
        }
        // Otherwise, let the cleanup task handle it after timeout
    }
}