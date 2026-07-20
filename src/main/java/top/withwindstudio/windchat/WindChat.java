// WindChat.java
package top.withwindstudio.windchat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class WindChat extends JavaPlugin {
    public final Map<String, Integer> badWords = new HashMap<>();
    private boolean papiEnabled = false;
    private static final EventPriority[] PRIORITIES = {
            EventPriority.LOWEST,
            EventPriority.LOW,
            EventPriority.NORMAL,
            EventPriority.HIGH,
            EventPriority.HIGHEST
    };
    public SensitiveWordBs sensitiveWordBs;
    public LegacyComponentSerializer legacyComponentSerializer;

    @Override
    public void onEnable() {
        Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §aWindChat v1.0.0-beta.2");
        Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §a插件正在加载...");
        createDefaultResources();
        reloadConfig();
        initLegacyComponentSerializer();
        boolean configValid = checkConfigValid();
        if (configValid) {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                papiEnabled = true;
                Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §fPlaceholderAPI 已启用");
            } else {
                Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §ePlaceholderAPI 未找到，占位符将不会解析");
            }
            loadBadWords();
            initSensitiveWordBs();
            registerChatListener();
        } else {
            raiseError("插件加载失败：配置文件检查未通过", false);
        }
        PluginCommand wchatCommand = getCommand("wchat");
        if (wchatCommand != null) {
            wchatCommand.setExecutor(new CommandHandler(this));
            Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §a插件加载成功");
        } else {
            raiseError("插件加载失败：无法注册命令 'wchat'", true);
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §a插件已卸载");
    }

    public void raiseError(String message, boolean disablePlugin) {
        Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §c" + message);
        if (disablePlugin) {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public void reloadPlugin() {
        Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §a配置重载中...");
        reloadConfig();
        if (checkConfigValid()) {
            loadBadWords();
            initSensitiveWordBs();
            if (!isChatListenerRegistered()) {
                registerChatListener();
            }
            Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §a配置已重载");
        } else {
            Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §c配置重载失败，请检查配置文件");
            Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §e当前仍使用旧配置运行");
        }
    }

    public boolean isPapiEnabled() {
        return papiEnabled;
    }

    private void createDefaultResources() {
        Path dataFolder = getDataFolder().toPath();
        if (!Files.exists(dataFolder)) {
            try {
                Files.createDirectories(dataFolder);
            } catch (Exception e) {
                raiseError("插件加载失败：无法创建数据目录，插件已禁用", true);
            }
        }
        Path configFile = dataFolder.resolve("config.yml");
        if (!Files.exists(configFile)) {
            try {
                saveResource("config.yml", false);
            } catch (Exception e) {
                raiseError("插件加载失败：无法创建配置文件，插件已禁用", true);
            }
        }
    }

    public boolean checkConfigValid() {
        boolean isValid = true;
        ConfigurationSection config = getConfig();

        if (!config.contains("sensitive.enable")) {
            raiseError("插件加载失败：配置项 'sensitive.enable' 不存在", false);
            isValid = false;
        } else {
            if (!config.isBoolean("sensitive.enable")) {
                raiseError("插件加载失败：配置项 'sensitive.enable' 必须是布尔值 (true/false)", false);
                isValid = false;
            }
        }

        if (!config.contains("sensitive.words")) {
            raiseError("插件加载失败：配置项 'sensitive.words' 不存在", false);
            isValid = false;
        } else {
            ConfigurationSection wordsSection = config.getConfigurationSection("sensitive.words");
            if (wordsSection == null) {
                raiseError("插件加载失败：配置项 'sensitive.words' 为空", false);
                isValid = false;
            } else {
                if (wordsSection.getKeys(false).isEmpty()) {
                    raiseError("插件加载失败：配置项 'sensitive.words' 中没有定义任何敏感词", false);
                    isValid = false;
                } else {
                    for (String word : wordsSection.getKeys(false)) {
                        if (!wordsSection.isInt(word)) {
                            raiseError("插件加载失败：敏感词 '" + word + "' 的等级必须为整数", false);
                            isValid = false;
                        } else {
                            int level = wordsSection.getInt(word);
                            if (level < 1) {
                                raiseError("插件加载失败：敏感词 '" + word + "' 的等级必须大于等于 1", false);
                                isValid = false;
                            }
                            if (!config.contains("sensitive.actions." + level)) {
                                raiseError("插件加载失败：敏感词 '" + word + "' 的等级 " + level + " 在 'sensitive.actions' 中没有对应的处理规则", false);
                                isValid = false;
                            }
                        }
                    }
                }
            }
        }

        if (!config.contains("sensitive.actions")) {
            raiseError("插件加载失败：配置项 'sensitive.actions' 不存在", false);
            isValid = false;
        } else {
            List<Map<?, ?>> defaultActions = config.getMapList("sensitive.actions.1");
            if (defaultActions.isEmpty()) {
                raiseError("插件加载失败：等级 1 没有定义动作", false);
                isValid = false;
            }
            ConfigurationSection actionsSection = config.getConfigurationSection("sensitive.actions");
            if (actionsSection != null) {
                for (String levelKey : actionsSection.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(levelKey);
                        if (level < 1) {
                            raiseError("插件加载失败：动作等级 '" + levelKey + "' 必须大于等于 1", false);
                            isValid = false;
                            continue;
                        }
                        List<Map<?, ?>> actions = config.getMapList("sensitive.actions." + level);
                        if (actions.isEmpty()) {
                            raiseError("插件加载失败：等级 " + level + " 的动作列表为空", false);
                            isValid = false;
                            continue;
                        }
                        for (int i = 0; i < actions.size(); i++) {
                            Map<?, ?> action = actions.get(i);
                            if (!action.containsKey("action")) {
                                raiseError("插件加载失败：等级 " + level + " 的第 " + (i + 1) + " 个动作缺少 'action' 字段", false);
                                isValid = false;
                                continue;
                            }
                            String actionType = (String) action.get("action");
                            if (actionType == null || actionType.isEmpty()) {
                                raiseError("插件加载失败：等级 " + level + " 的第 " + (i + 1) + " 个动作的 'action' 字段为空", false);
                                isValid = false;
                                continue;
                            }
                            switch (actionType) {
                                case "change", "block" -> {}
                                case "message", "broadcast" -> {
                                    if (!action.containsKey("message") || action.get("message") == null) {
                                        raiseError("插件加载失败：等级 " + level + " 的 " + actionType + " 动作缺少 'message' 字段", false);
                                        isValid = false;
                                    }
                                }
                                case "command" -> {
                                    if (!action.containsKey("commands") || action.get("commands") == null) {
                                        raiseError("插件加载失败：等级 " + level + " 的 command 动作缺少 'commands' 字段", false);
                                        isValid = false;
                                    } else {
                                        Object commandsObj = action.get("commands");
                                        if (!(commandsObj instanceof List<?> commands)) {
                                            raiseError("插件加载失败：等级 " + level + " 的 command 动作中 'commands' 必须是列表", false);
                                            isValid = false;
                                        } else {
                                            if (commands.isEmpty()) {
                                                raiseError("插件加载失败：等级 " + level + " 的 command 动作中 'commands' 列表为空", false);
                                                isValid = false;
                                            }
                                            for (int j = 0; j < commands.size(); j++) {
                                                if (!(commands.get(j) instanceof String)) {
                                                    raiseError("插件加载失败：等级 " + level + " 的 command 动作中第 " + (j + 1) + " 条命令不是字符串", false);
                                                    isValid = false;
                                                }
                                            }
                                        }
                                    }
                                }
                                default -> {
                                    raiseError("插件加载失败：等级 " + level + " 包含未知的动作类型: '" + actionType + "'，支持的类型: block, change, message, broadcast, command", false);
                                    isValid = false;
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        raiseError("插件加载失败：动作等级 '" + levelKey + "' 不是有效的数字", false);
                        isValid = false;
                    }
                }
            }
        }

        if (!config.contains("chat_format.enable")) {
            raiseError("插件加载失败：配置项 'chat_format.enable' 不存在", false);
            isValid = false;
        } else {
            if (!config.isBoolean("chat_format.enable")) {
                raiseError("插件加载失败：配置项 'chat_format.enable' 必须是布尔值 (true/false)", false);
                isValid = false;
            }
        }

        if (config.getBoolean("chat_format.enable", true)) {
            if (!config.contains("chat_format.format")) {
                raiseError("插件加载失败：配置项 'chat_format.format' 不存在", false);
                isValid = false;
            } else {
                String format = config.getString("chat_format.format");
                if (format == null || format.isEmpty()) {
                    raiseError("插件加载失败：配置项 'chat_format.format' 不能为空", false);
                    isValid = false;
                }
            }
        }

        if (!config.contains("chat_listener_priority")) {
            raiseError("插件加载失败：配置项 'chat_listener_priority' 不存在", false);
            isValid = false;
        } else {
            if (!config.isInt("chat_listener_priority")) {
                raiseError("插件加载失败：配置项 'chat_listener_priority' 必须是整数 (1-5)", false);
                isValid = false;
            } else {
                int priority = config.getInt("chat_listener_priority");
                if (priority < 1 || priority > 5) {
                    raiseError("插件加载失败：配置项 'chat_listener_priority' 必须在 1-5 之间，当前值: " + priority, false);
                    isValid = false;
                }
            }
        }

        if (!isValid) {
            raiseError("插件加载失败：配置文件存在错误，请检查并修复后重启服务器或使用 /wchat reload", false);
        }
        return isValid;
    }

    private void loadBadWords() {
        badWords.clear();
        ConfigurationSection badWordsSection = getConfig().getConfigurationSection("sensitive.words");
        if (badWordsSection != null) {
            for (String word : badWordsSection.getKeys(false)) {
                int level = badWordsSection.getInt(word);
                badWords.put(word.toLowerCase(), level);
            }
        }
        Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §f已加载 §b" + badWords.size() + " §f个敏感词");
    }

    private void registerChatListener() {
        EventPriority chatListenerPriority = getChatListenerPriority();
        ChatListener chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvent(AsyncChatEvent.class, chatListener, chatListenerPriority, (listener, event) -> ((ChatListener) listener).onPlayerChat((AsyncChatEvent) event), this);
        Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §f聊天监听器已注册");
    }

    private boolean isChatListenerRegistered() {
        for (RegisteredListener registeredListener : HandlerList.getRegisteredListeners(this)) {
            if (registeredListener.getListener() instanceof ChatListener) {
                return true;
            }
        }
        return false;
    }

    private void initSensitiveWordBs() {
        this.sensitiveWordBs = SensitiveWordBs.newInstance().wordDeny(() -> new ArrayList<>(badWords.keySet())).ignoreCase(true).ignoreWidth(true).ignoreNumStyle(true).ignoreChineseStyle(true).ignoreEnglishStyle(true).init();
    }

    private void initLegacyComponentSerializer() {
        legacyComponentSerializer = LegacyComponentSerializer.legacySection();
    }

    public EventPriority getChatListenerPriority() {
        int priority = getConfig().getInt("chat_listener_priority");
        if (priority < 1 || priority > 5) return EventPriority.NORMAL;
        return PRIORITIES[priority - 1];
    }
}