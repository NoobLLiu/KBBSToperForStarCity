package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.Platform;
import mc233.fun.kbbstoper.core.platform.PlatformConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 负责释放、升级与重载 config.yml / lang.yml。
 * gui.yml 只有 Bukkit 端的箱子界面需要，因此由 Bukkit 模块自行管理。
 */
public class ConfigManager {

    private final Platform platform;
    private PlatformConfig configFile;
    private PlatformConfig langFile;

    public ConfigManager(Platform platform) {
        this.platform = platform;
        setup();
    }

    private void setup() {
        File cfg = new File(platform.getDataFolder(), "config.yml");
        if (!cfg.exists()) {
            platform.saveResource("config.yml", false);
        }
        configFile = platform.loadConfig(cfg);
        updateConfig(cfg, "config");

        File lang = new File(platform.getDataFolder(), "lang.yml");
        if (!lang.exists()) {
            platform.saveResource("lang.yml", false);
        }
        langFile = platform.loadConfig(lang);
        updateConfig(lang, "lang");
    }

    /**
     * 配置里记的版本号与插件版本不一致时，备份旧文件再释放新模板。
     * 与原版行为保持一致：判据始终取 config.yml 里的 version 字段。
     */
    private void updateConfig(File file, String name) {
        String fileVersion = configFile.getString("version");
        String pluginVersion = platform.getPluginVersion();

        if (fileVersion != null && !fileVersion.equals(pluginVersion)) {
            try {
                Files.copy(
                        file.toPath(),
                        new File(platform.getDataFolder(), name + "_old.yml").toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
                platform.saveResource(name + ".yml", true);
            } catch (IOException ex) {
                platform.getLogger().severe("更新 " + name + ".yml 时出错", ex);
            }
        }
    }

    public PlatformConfig getConfigFile() {
        return configFile;
    }

    public PlatformConfig getLangFile() {
        return langFile;
    }

    public void reloadConfig() {
        configFile = platform.loadConfig(new File(platform.getDataFolder(), "config.yml"));
        langFile = platform.loadConfig(new File(platform.getDataFolder(), "lang.yml"));
    }
}
