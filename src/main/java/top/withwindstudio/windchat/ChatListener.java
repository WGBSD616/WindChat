// ChatListener.java
package top.withwindstudio.windchat;

import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.regex.Pattern;
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
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    public ChatListener(WindChat plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("ConstantConditions")
    public void onPlayerChat(AsyncChatEvent chatEvent) {
        Player player = chatEvent.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        String message = COLOR_CODE_PATTERN.matcher(PlainTextComponentSerializer.plainText().serialize(chatEvent.message())).replaceAll("");
        if (player.hasPermission("windchat.bypass")) {
            Component formattedMessage = formatMessage(player, message);
            if (formattedMessage != null) {
                chatEvent.setCancelled(true);
                Bukkit.broadcast(formattedMessage);
            }
            return;
        }

        boolean enableSensitive = plugin.getConfig().getBoolean("sensitive.enable");
        if (enableSensitive) {
            if (processSensitiveWords(message, chatEvent)) {
                Component formattedMessage = formatMessage(player, message);
                if (formattedMessage != null) {
                    chatEvent.setCancelled(true);
                    Bukkit.broadcast(formattedMessage);
                }
            }
        } else {
            Component formattedMessage = formatMessage(player, message);
            if (formattedMessage != null) {
                chatEvent.setCancelled(true);
                Bukkit.broadcast(formattedMessage);
            }
        }
    }

    private boolean processSensitiveWords(String message, AsyncChatEvent chatEvent) {
        if (plugin.sensitiveWordBs == null) {
            return true;
        }
        List<String> foundSensitiveWords = plugin.sensitiveWordBs.findAll(message);
        if (foundSensitiveWords.isEmpty()) {
            return true;
        }
        int sensitiveWordsHighestLevel = 1;
        for (String singleSensitiveWord : foundSensitiveWords) {
            int sensitiveWordsCurrentLevel = plugin.badWords.getOrDefault(singleSensitiveWord.toLowerCase(), 1);
            if (sensitiveWordsCurrentLevel > sensitiveWordsHighestLevel) {
                sensitiveWordsHighestLevel = sensitiveWordsCurrentLevel;
            }
        }
        List<Map<?, ?>> sensitiveWordsActions = plugin.getConfig().getMapList("sensitive.actions." + sensitiveWordsHighestLevel);
        boolean messageHandled = false;
        for (Map<?, ?> sensitiveWordActionConfig : sensitiveWordsActions) {
            String sensitiveWordActionType = (String) sensitiveWordActionConfig.get("action");
            if (executeSingleAction(sensitiveWordActionType, sensitiveWordActionConfig, message, chatEvent)) {
                messageHandled = true;
            }
        }
        return !messageHandled;
    }

    @SuppressWarnings("ConstantConditions")
    private boolean executeSingleAction(String sensitiveWordActionType, Map<?, ?> sensitiveWordActionConfig, String message, AsyncChatEvent chatEvent) {
        Player player = chatEvent.getPlayer();
        if (player == null || !player.isOnline()) {
            return false;
        }
        switch (sensitiveWordActionType) {
            case "block" -> {
                chatEvent.setCancelled(true);
                return true;
            }
            case "change" -> {
                String modifiedSensitiveWordMessage = plugin.sensitiveWordBs.replace(message);
                Component formattedModifiedSensitiveWordMessage = formatMessage(player, modifiedSensitiveWordMessage);
                chatEvent.setCancelled(true);
                Bukkit.broadcast(Objects.requireNonNullElseGet(formattedModifiedSensitiveWordMessage, () -> Component.text(modifiedSensitiveWordMessage)));
                return true;
            }
            case "message" -> {
                String sensitiveWordActionMessage = (String) sensitiveWordActionConfig.get("message");
                if (sensitiveWordActionMessage == null) {
                    plugin.raiseError("message 动作缺少 message 字段", false);
                    return false;
                }
                sensitiveWordActionMessage = sensitiveWordActionMessage.replace("{player}", player.getName());
                player.sendMessage(plugin.legacyComponentSerializer.deserialize(sensitiveWordActionMessage));
                return false;
            }
            case "broadcast" -> {
                String broadcastMessage = (String) sensitiveWordActionConfig.get("message");
                if (broadcastMessage == null) {
                    plugin.raiseError("broadcast 动作缺少 message 字段", false);
                    return false;
                }
                broadcastMessage = broadcastMessage.replace("{player}", player.getName());
                plugin.getServer().broadcast(plugin.legacyComponentSerializer.deserialize(broadcastMessage));
                return false;
            }
            case "command" -> {
                Object commandsObj = sensitiveWordActionConfig.get("commands");
                if (commandsObj instanceof List<?> rawList) {
                    List<String> commands = rawList.stream().filter(String.class::isInstance).map(String.class::cast).toList();
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        for (String command : commands) {
                            String finalCommand = command.replace("{player}", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                        }
                    });
                } else {
                    plugin.raiseError("commands 不是列表", false);
                }
                return false;
            }
            default -> {
                plugin.raiseError("未知的动作类型: " + sensitiveWordActionType, true);
                return false;
            }
        }
    }

    private Component formatMessage(Player player, String message) {
        if (player == null || !player.isOnline()) {
            return Component.text(message);
        }
        boolean formatEnable = plugin.getConfig().getBoolean("chat_format.enable", true);
        String playerName = player.getName();
        if (!formatEnable) {
            return Component.text("<{player}> {message}".replace("{player}", playerName).replace("{message}", message));
        }
        String formatTemplate = plugin.getConfig().getString("chat_format.format", "&a[&f玩家消息&a]&r {player}：{message}");
        if (plugin.isPapiEnabled()) {
            try {
                formatTemplate = PlaceholderAPI.setPlaceholders(player, formatTemplate);
            } catch (Exception e) {
                plugin.getLogger().warning("PlaceholderAPI 解析失败: " + e.getMessage());
            }
        }
        String formattedMessage = formatTemplate.replace("{player}", playerName).replace("{message}", message);
        return plugin.legacyComponentSerializer.deserialize(formattedMessage);
    }
}