package com.example.sessionguard.tasks;

import com.example.sessionguard.SessionGuard;
import org.bukkit.entity.Player;
import java.util.UUID;

public class ReconnectTask implements Runnable {
    
    private final SessionGuard plugin;
    private final UUID playerUUID;
    private final String playerName;
    
    public ReconnectTask(SessionGuard plugin, UUID playerUUID, String playerName) {
        this.plugin = plugin;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
    }
    
    @Override
    public void run() {
        // This task can be extended for custom reconnection logic
        Player player = plugin.getServer().getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            // Player successfully reconnected
            if (plugin.getConfig().getBoolean("logging.log-reconnections")) {
                plugin.getLogger().info("Player " + playerName + " successfully reconnected");
            }
        }
    }
}