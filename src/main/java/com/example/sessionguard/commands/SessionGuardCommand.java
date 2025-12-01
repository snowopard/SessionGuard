package com.example.sessionguard.commands;

import com.example.sessionguard.SessionGuard;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SessionGuardCommand implements CommandExecutor, TabExecutor {
    
    private final SessionGuard plugin;
    
    public SessionGuardCommand(SessionGuard plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("sessionguard.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "SessionGuard configuration reloaded!");
                break;
                
            case "status":
                if (!sender.hasPermission("sessionguard.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                sendStatus(sender);
                break;
                
            case "clearcache":
                if (!sender.hasPermission("sessionguard.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                plugin.getRecentKicks().clear();
                sender.sendMessage(ChatColor.GREEN + "Recent kicks cache cleared!");
                break;
                
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== SessionGuard v" + plugin.getDescription().getVersion() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "/sessionguard reload " + ChatColor.GRAY + "- Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/sessionguard status " + ChatColor.GRAY + "- Show plugin status");
        sender.sendMessage(ChatColor.YELLOW + "/sessionguard clearcache " + ChatColor.GRAY + "- Clear recent kicks cache");
        sender.sendMessage(ChatColor.GRAY + "Aliases: " + ChatColor.WHITE + "/sg");
    }
    
    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== SessionGuard Status ===");
        sender.sendMessage(ChatColor.YELLOW + "Reconnect delay: " + ChatColor.WHITE + 
            plugin.getConfig().getInt("reconnect-delay") + " seconds");
        sender.sendMessage(ChatColor.YELLOW + "Recent kicks cache size: " + ChatColor.WHITE + 
            plugin.getRecentKicks().size());
        sender.sendMessage(ChatColor.YELLOW + "Logging enabled: " + ChatColor.WHITE + 
            plugin.getConfig().getBoolean("logging.enabled"));
        sender.sendMessage(ChatColor.YELLOW + "Folia supported: " + ChatColor.GREEN + "Yes");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("reload", "status", "clearcache");
            return completions.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}