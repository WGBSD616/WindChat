// CommandHandler.java
package top.withwindstudio.windchat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

public class CommandHandler implements CommandExecutor {
    private final WindChat plugin;

    public CommandHandler(WindChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (args == null || args.length == 0) {
            sender.sendMessage("§c用法: /wchat reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("windchat.reload")) {
                sender.sendMessage("§c你没有权限执行此命令");
                return true;
            }
            plugin.reloadPlugin();
            return true;
        }

        sender.sendMessage("§c未知命令，用法: /wchat reload");
        return true;
    }
}