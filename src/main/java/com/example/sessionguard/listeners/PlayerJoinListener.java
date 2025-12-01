package com.example.sessionguard.listeners;

import com.example.sessionguard.SessionGuard;
import com.example.sessionguard.utils.KickManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PlayerJoinListener implements Listener {
    
    private final SessionGuard plugin;
    private final KickManager kickManager;
    
    public PlayerJoinListener(SessionGuard plugin) {
        this.plugin = plugin;
        this.kickManager = new KickManager(plugin);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String username = event.getName();
        
        if (plugin.getConfig().getBoolean("logging.log-duplicate-detection")) {
            plugin.getLogger().info("Pre-login check for " + username + " (" + uuid + ")");
        }
        
        // Check if player is already online using async approach
        CompletableFuture<Boolean> checkFuture = CompletableFuture.supplyAsync(() -> {
            Player existingPlayer = Bukkit.getPlayer(uuid);
            boolean isOnline = existingPlayer != null && existingPlayer.isOnline();
            
            // Also check by username if configured
            if (!isOnline && plugin.getConfig().getBoolean("advanced.check-username")) {
                existingPlayer = Bukkit.getPlayer(username);
                isOnline = existingPlayer != null && existingPlayer.isOnline();
            }
            
            return isOnline;
        });
        
        try {
            int timeout = plugin.getConfig().getInt("advanced.status-check-timeout", 100);
            boolean isOnline = checkFuture.get(timeout, TimeUnit.MILLISECONDS);
            
            if (isOnline) {
                handleDuplicateConnection(event, uuid, username);
            } else {
                // Allow normal connection
                event.setLoginResult(AsyncPlayerPreLoginEvent.Result.ALLOWED);
                
                if (plugin.getConfig().getBoolean("logging.verbose")) {
                    plugin.getLogger().info("Allowed connection for " + username + " (no duplicate found)");
                }
            }
            
        } catch (java.util.concurrent.TimeoutException e) {
            plugin.getLogger().warning("Timeout checking player status for " + username);
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(plugin.getConfig().getString("kick-messages.error", 
                "Server timeout during connection check"));
                
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking player status for " + username + ": " + e.getMessage());
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(plugin.getConfig().getString("kick-messages.error", 
                "Internal error checking session"));
        }
    }
    
    private void handleDuplicateConnection(AsyncPlayerPreLoginEvent event, UUID uuid, String username) {
        if (plugin.isRecentlyKicked(uuid)) {
            // Allow reconnection within grace period
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.ALLOWED);
            
            if (plugin.getConfig().getBoolean("logging.log-reconnections")) {
                plugin.getLogger().info("Allowing reconnection for " + username + " (within grace period)");
            }
        } else {
            // Kick existing player and allow new connection
            kickManager.kickExistingPlayer(uuid, username).thenAccept(success -> {
                if (success) {
                    plugin.addRecentKick(uuid);
                    
                    // Allow new connection after successful kick
                    event.setLoginResult(AsyncPlayerPreLoginEvent.Result.ALLOWED);
                    
                    if (plugin.getConfig().getBoolean("logging.log-kick-events")) {
                        plugin.getLogger().info("Kicked duplicate session for " + username + 
                            ". New connection allowed.");
                    }
                } else {
                    event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                    event.kickMessage(plugin.getConfig().getString("kick-messages.error", 
                        "Unable to resolve session conflict"));
                }
            });
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Schedule removal from recent kicks after delay
        int delayTicks = plugin.getReconnectDelaySeconds() * 20;
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
            plugin.getRecentKicks().remove(uuid);
            
            if (plugin.getConfig().getBoolean("logging.verbose")) {
                plugin.getLogger().info("Removed " + player.getName() + " from recent kicks cache");
            }
        }, delayTicks);
        
        // Log successful connection
        if (plugin.getConfig().getBoolean("logging.enabled")) {
            plugin.getLogger().info("Player " + player.getName() + " connected successfully");
        }
    }
}