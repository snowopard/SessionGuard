package com.example.sessionguard.utils;

import com.example.sessionguard.SessionGuard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KickManager {
    
    private final SessionGuard plugin;
    private final LegacyComponentSerializer legacySerializer;
    
    public KickManager(SessionGuard plugin) {
        this.plugin = plugin;
        this.legacySerializer = LegacyComponentSerializer.legacySection();
    }
    
    public CompletableFuture<Boolean> kickExistingPlayer(UUID uuid, String newUsername) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Find existing player
        Player existingPlayer = Bukkit.getPlayer(uuid);
        if (existingPlayer == null) {
            // Try by username if configured
            if (plugin.getConfig().getBoolean("advanced.check-username")) {
                existingPlayer = Bukkit.getPlayer(newUsername);
            }
            
            if (existingPlayer == null) {
                future.complete(false);
                return future;
            }
        }
        
        String playerName = existingPlayer.getName();
        String kickMessage = plugin.getConfig().getString("kick-messages.duplicate-login",
            "§cYou were disconnected because you logged in from another location.");
        
        Component kickComponent = legacySerializer.deserialize(kickMessage);
        
        // Use Folia's entity scheduler for thread-safe player operations
        existingPlayer.getScheduler().run(plugin, kickTask -> {
            try {
                // Kick the player
                existingPlayer.kick(kickComponent);
                
                // Log the kick
                if (plugin.getConfig().getBoolean("logging.log-kick-events")) {
                    plugin.getLogger().info("Kicked existing player '" + playerName + 
                        "' (UUID: " + uuid + ") for duplicate login by '" + newUsername + "'");
                }
                
                future.complete(true);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to kick player '" + playerName + "': " + e.getMessage());
                e.printStackTrace();
                future.complete(false);
            }
        }, () -> {
            // This runs if the task couldn't be scheduled (player not loaded)
            plugin.getLogger().warning("Could not schedule kick task for player: " + playerName);
            future.complete(false);
        });
        
        return future;
    }
}