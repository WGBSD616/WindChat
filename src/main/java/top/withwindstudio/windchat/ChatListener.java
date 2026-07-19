// ChatListener.java
package top.withwindstudio.windchat;

import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import me.clip.placeholderapi.PlaceholderAPI;

public class ChatListener implements Listener {
    private final WindChat plugin;
    public ChatListener(WindChat plugin) {
        this.plugin = plugin;
    }

    public void onPlayerChat(AsyncChatEvent chatEvent) {
        String playerName = chatEvent.getPlayer().getName();
        String message = PlainTextComponentSerializer.plainText().serialize(chatEvent.message()).replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        if (chatEvent.getPlayer().hasPermission("windchat.bypass")) {
            Component formattedMessage = formatMessage(playerName, message);
            if (formattedMessage != null) {
                chatEvent.setCancelled(true);
                Bukkit.broadcast(formattedMessage);
            }
            return;
        }

        boolean enableSensitive = plugin.getConfig().getBoolean("sensitive.enable");
        if (!plugin.getConfig().contains("sensitive.enable")) {
            plugin.raiseError("配置项 'sensitive.enable' 不存在，插件已禁用", true);
            return;
        }

        if (enableSensitive) {
            if (processSensitiveWords(message, chatEvent)) {
                Component formattedMessage = formatMessage(playerName, message);
                if (formattedMessage != null) {
                    chatEvent.setCancelled(true);
                    Bukkit.broadcast(formattedMessage);
                }
            }
        } else {
            Component formattedMessage = formatMessage(playerName, message);
            if (formattedMessage != null) {
                chatEvent.setCancelled(true);
                Bukkit.broadcast(formattedMessage);
            }
        }
    }

    private boolean processSensitiveWords(String message, AsyncChatEvent chatEvent) {
        List<String> foundSensitiveWords = plugin.sensitiveWordBs.findAll(message);
        if (foundSensitiveWords.isEmpty()) {
            return true;
        }
        int sensitiveWordsHighestLevel = 1;
        for (String singleSensitiveWord : foundSensitiveWords) {
            int sensitiveWordsCurrentLevel = plugin.getBadWordMap().getOrDefault(singleSensitiveWord.toLowerCase(), 1);
            if (sensitiveWordsCurrentLevel > sensitiveWordsHighestLevel) {
                sensitiveWordsHighestLevel = sensitiveWordsCurrentLevel;
            }
        }
        List<Map<?, ?>> sensitiveWordsActions = plugin.getConfig().getMapList("sensitive.actions." + sensitiveWordsHighestLevel);
        if (sensitiveWordsActions.isEmpty()) {
            plugin.raiseError("配置项 'sensitive.actions." + sensitiveWordsHighestLevel + "' 不存在，插件已禁用", true);
            return false;
        }
        boolean messageHandled = false;
        for (Map<?, ?> sensitiveWordActionConfig : sensitiveWordsActions) {
            String sensitiveWordActionType = (String) sensitiveWordActionConfig.get("action");
            if (executeSingleAction(sensitiveWordActionType, sensitiveWordActionConfig, foundSensitiveWords, sensitiveWordsHighestLevel, message, chatEvent)) {
                messageHandled = true;
            }
        }
        return !messageHandled;
    }

    @SuppressWarnings("unchecked")
    private boolean executeSingleAction(String sensitiveWordActionType, Map<?, ?> sensitiveWordActionConfig, List<String> foundSensitiveWords, int sensitiveWordLevel, String message, AsyncChatEvent chatEvent) {
        Player player = chatEvent.getPlayer();
        switch (sensitiveWordActionType) {
            case "block" -> {
                chatEvent.setCancelled(true);
                return true;
            }
            case "change" -> {
                String changeSensitiveWordTo = (String) sensitiveWordActionConfig.get("change_to");
                if (changeSensitiveWordTo == null) {
                    plugin.raiseError("配置项 'sensitive.actions." + sensitiveWordLevel + ".change_to' 不存在，插件已禁用", true);
                    return false;
                }
                String modifiedSensitiveWordMessage = message;
                for (String foundSensitiveWord : foundSensitiveWords) {
                    modifiedSensitiveWordMessage = modifiedSensitiveWordMessage.replace(foundSensitiveWord, changeSensitiveWordTo);
                }
                Component formattedModifiedSensitiveWordMessage = formatMessage(player.getName(), modifiedSensitiveWordMessage);
                if (formattedModifiedSensitiveWordMessage != null) {
                    chatEvent.setCancelled(true);
                    Bukkit.broadcast(formattedModifiedSensitiveWordMessage);
                } else {
                    chatEvent.setCancelled(true);
                    Bukkit.broadcast(Component.text(modifiedSensitiveWordMessage));
                }
                return true;
            }
            case "message" -> {
                String sensitiveWordActionMessage = (String) sensitiveWordActionConfig.get("message");
                if (sensitiveWordActionMessage == null) {
                    plugin.raiseError("配置项 'sensitive.actions." + sensitiveWordLevel + ".message' 不存在，插件已禁用", true);
                    return false;
                }
                sensitiveWordActionMessage = sensitiveWordActionMessage.replace("{player}", player.getName());
                player.sendMessage(plugin.legacyComponentSerializer.deserialize(sensitiveWordActionMessage));
                return false;
            }
            case "broadcast" -> {
                String broadcastMessage = (String) sensitiveWordActionConfig.get("message");
                if (broadcastMessage == null) {
                    plugin.raiseError("配置项 'sensitive.actions." + sensitiveWordLevel + ".message' 不存在，插件已禁用", true);
                    return false;
                }
                broadcastMessage = broadcastMessage.replace("{player}", player.getName());
                plugin.getServer().broadcast(plugin.legacyComponentSerializer.deserialize(broadcastMessage));
                return false;
            }
            case "command" -> {
                List<String> sensitiveWordActionCommands = (List<String>) sensitiveWordActionConfig.get("commands");
                if (sensitiveWordActionCommands == null || sensitiveWordActionCommands.isEmpty()) {
                    plugin.raiseError("配置项 'sensitive.actions." + sensitiveWordLevel + ".commands' 不存在，插件已禁用", true);
                    return false;
                }
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    for (String sensitiveWordActionCommand : sensitiveWordActionCommands) {
                        String finalCommand = sensitiveWordActionCommand.replace("{player}", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                    }
                });
                return false;
            }
            default -> {
                plugin.raiseError("未知的动作类型: " + sensitiveWordActionType, true);
                return false;
            }
        }
    }

    private Component formatMessage(String playerName, String message) {
        boolean formatEnable = plugin.getConfig().getBoolean("chat_format.enable", true);
        if (!plugin.getConfig().contains("chat_format.enable")) {
            plugin.raiseError("配置项 'chat_format.enable' 不存在，请检查配置文件", false);
            return Component.text("<{player}> {message}".replace("{player}", playerName).replace("{message}", message));
        }
        if (!formatEnable) {
            return Component.text("<{player}> {message}".replace("{player}", playerName).replace("{message}", message));
        }
        String formatTemplate = plugin.getConfig().getString("chat_format.format", "&a[&f玩家消息&a]&r {player}：{message}");
        if (plugin.isPapiEnabled()) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                formatTemplate = PlaceholderAPI.setPlaceholders(player, formatTemplate);
            }
        }
        String formattedMessage = formatTemplate.replace("{player}", playerName).replace("{message}", message);
        return plugin.legacyComponentSerializer.deserialize(formattedMessage);
    }
}