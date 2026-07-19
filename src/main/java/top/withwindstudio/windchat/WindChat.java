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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class WindChat extends JavaPlugin {
    private final Map<String, Integer> badWords = new HashMap<>();
    private boolean papiEnabled = false;
    public SensitiveWordBs sensitiveWordBs;
    public LegacyComponentSerializer legacyComponentSerializer;

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onEnable() {
        Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §a插件已加载");
        createDefaultResources();
        reloadConfig();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiEnabled = true;
            Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §fPlaceholderAPI 已启用");
        } else {
            Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §ePlaceholderAPI 未找到，占位符将不会解析");
        }

        initLegacyComponentSerializer();
        loadBadWords();
        initSensitiveWordBs();
        registerChatListener();

        if (getCommand("wchat") != null) {
            getCommand("wchat").setExecutor(new CommandHandler(this));
        } else {
            raiseError("无法注册命令 'wchat'，请检查 plugin.yml", true);
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
        reloadConfig();
        loadBadWords();
        initSensitiveWordBs();
        Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §a配置已重载");
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
                raiseError("无法创建数据目录，插件已禁用", true);
            }
        }
        Path configFile = dataFolder.resolve("config.yml");
        if (!Files.exists(configFile)) {
            try {
                saveResource("config.yml", false);
            } catch (Exception e) {
                raiseError("无法创建配置文件，插件已禁用", true);
            }
        }
    }

    private void loadBadWords() {
        badWords.clear();
        ConfigurationSection badWordsSection = getConfig().getConfigurationSection("sensitive.words");
        if (badWordsSection == null) {
            raiseError("配置项 'sensitive.words' 不存在，插件已禁用", true);
        } else {
            for (String word : badWordsSection.getKeys(false)) {
                int level = badWordsSection.getInt(word);
                badWords.put(word, level);
            }
            Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §f已加载 §b" + badWords.size() + " §f个敏感词");
        }
    }

    private void registerChatListener() {
        EventPriority chatListenerPriority = getChatListenerPriority();
        if (chatListenerPriority == null) {
            raiseError("配置项 'chat_listener_priority' 的值无效，插件已禁用", true);
        } else {
            ChatListener chatListener = new ChatListener(this);
            getServer().getPluginManager().registerEvent(AsyncChatEvent.class, chatListener, chatListenerPriority, (listener, event) -> ((ChatListener) listener).onPlayerChat((AsyncChatEvent) event), this);
            Bukkit.getConsoleSender().sendMessage("§f[§aWindChat§f] §f聊天监听器已注册");
        }
    }

    private void initSensitiveWordBs() {
        this.sensitiveWordBs = SensitiveWordBs.newInstance().wordDeny(this::getBadWordList).ignoreCase(true).ignoreWidth(true).ignoreNumStyle(true).ignoreChineseStyle(true).ignoreEnglishStyle(true).init();
    }

    private void initLegacyComponentSerializer() {
        legacyComponentSerializer = LegacyComponentSerializer.legacySection();
    }

    public List<String> getBadWordList() {
        return new ArrayList<>(badWords.keySet());
    }

    public Map<String, Integer> getBadWordMap() {
        return new HashMap<>(badWords);
    }

    public EventPriority getChatListenerPriority() {
        int chatListenerPriority = getConfig().getInt("chat_listener_priority");
        if (!getConfig().contains("chat_listener_priority")) {
            return null;
        } else {
            return switch (chatListenerPriority) {
                case 1 -> EventPriority.LOWEST;
                case 2 -> EventPriority.LOW;
                case 3 -> EventPriority.NORMAL;
                case 4 -> EventPriority.HIGH;
                case 5 -> EventPriority.HIGHEST;
                default -> null;
            };
        }
    }
}