package com.example.sessionguard;

import com.example.sessionguard.commands.SessionGuardCommand;
import com.example.sessionguard.listeners.PlayerJoinListener;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SessionGuard extends JavaPlugin {
    
    private static SessionGuard instance;
    private final ConcurrentHashMap<UUID, Long> recentKicks = new ConcurrentHashMap<>();
    private PlayerJoinListener playerJoinListener;
    
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        // Initialize listener
        playerJoinListener = new PlayerJoinListener(this);
        getServer().getPluginManager().registerEvents(playerJoinListener, this);
        
        // Register command
        SessionGuardCommand commandExecutor = new SessionGuardCommand(this);
        getCommand("sessionguard").setExecutor(commandExecutor);
        getCommand("sessionguard").setTabCompleter(commandExecutor);
        
        // Schedule cleanup task for recent kicks map (global scheduler for Folia)
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            cleanupRecentKicks();
        }, 60, 60, TimeUnit.SECONDS);
        
        getLogger().info("§aSessionGuard v" + getDescription().getVersion() + " has been enabled!");
        getLogger().info("§7Reconnection delay: " + getConfig().getInt("reconnect-delay") + " seconds");
        getLogger().info("§7Logging enabled: " + getConfig().getBoolean("logging.enabled"));
    }
    
    @Override
    public void onDisable() {
        recentKicks.clear();
        getLogger().info("§cSessionGuard has been disabled!");
    }
    
    private void cleanupRecentKicks() {
        long currentTime = System.currentTimeMillis();
        long delayMillis = getConfig().getInt("reconnect-delay") * 1000L;
        
        recentKicks.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > (delayMillis + 1000)); // Extra 1 second buffer
        
        if (getConfig().getBoolean("logging.verbose")) {
            getLogger().info("Cleaned up recent kicks cache. Size: " + recentKicks.size());
        }
    }
    
    public static SessionGuard getInstance() {
        return instance;
    }
    
    public ConcurrentHashMap<UUID, Long> getRecentKicks() {
        return recentKicks;
    }
    
    public void addRecentKick(UUID uuid) {
        recentKicks.put(uuid, System.currentTimeMillis());
        
        if (getConfig().getBoolean("logging.log-kick-events")) {
            getLogger().info("Added UUID " + uuid + " to recent kicks cache");
        }
    }
    
    public boolean isRecentlyKicked(UUID uuid) {
        Long kickTime = recentKicks.get(uuid);
        if (kickTime == null) return false;
        
        long timeSinceKick = System.currentTimeMillis() - kickTime;
        long delayMillis = getConfig().getInt("reconnect-delay") * 1000L;
        
        boolean isRecent = timeSinceKick < delayMillis;
        
        if (getConfig().getBoolean("advanced.debug-mode")) {
            getLogger().info("Checking recent kick for " + uuid + 
                ": " + isRecent + " (" + timeSinceKick + "ms/" + delayMillis + "ms)");
        }
        
        return isRecent;
    }
    
    public int getReconnectDelaySeconds() {
        return getConfig().getInt("reconnect-delay", 2);
    }
}