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

        File lang = new File(platform.getDataFolder(), "lang.yml");
        if (!lang.exists()) {
            platform.saveResource("lang.yml", false);
        }

        // 版本判据必须在升级前一次性取出:
        // 迁移会把 config.yml 的 version 改成当前版本, 若边升级边读, lang.yml 就永远不会更新。
        String fileVersion = configFile.getString("version");
        String pluginVersion = platform.getPluginVersion();
        boolean upgrade = fileVersion != null && !fileVersion.equals(pluginVersion);

        if (upgrade) {
            platform.getLogger().info("检测到配置版本 " + fileVersion + " → " + pluginVersion + ", 开始自动升级...");
            migrateConfig(cfg, pluginVersion);
            resetLang(lang);
        }
        langFile = platform.loadConfig(lang);
    }

    /**
     * config.yml 旧版自动升级(键级迁移, 不是整体覆盖):
     * 先备份旧文件为 config_old.yml, 释放新模板, 再把旧文件中"仍存在于新模板"的键用旧值写回。
     * 效果: 保留用户已改的有效配置 / 丢弃新模板已删除的失效项 / 自动补齐新增项默认值。
     */
    private void migrateConfig(File file, String pluginVersion) {
        try {
            File backup = new File(platform.getDataFolder(), "config_old.yml");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            platform.saveResource("config.yml", true);

            PlatformConfig newCfg = platform.loadConfig(file);
            PlatformConfig oldCfg = platform.loadConfig(backup);
            int kept = 0;
            for (String key : newCfg.getKeys(true)) {
                if (key.equals("version") || newCfg.isSection(key)) {
                    continue;
                }
                if (oldCfg.contains(key) && !oldCfg.isSection(key)) {
                    newCfg.set(key, oldCfg.getRaw(key));
                    kept++;
                }
            }
            newCfg.set("version", pluginVersion);
            newCfg.save(file);
            configFile = newCfg;
            platform.getLogger().info("config.yml 升级完成: 保留 " + kept
                    + " 项用户配置, 失效项已移除, 新增项已补默认值(旧文件备份为 config_old.yml)。");
        } catch (IOException ex) {
            platform.getLogger().severe("升级 config.yml 时出错", ex);
        }
    }

    /** lang.yml 随版本强制更新(文案由插件维护)，旧文件备份为 lang_old.yml。 */
    private void resetLang(File file) {
        try {
            File backup = new File(platform.getDataFolder(), "lang_old.yml");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            platform.saveResource("lang.yml", true);
            platform.getLogger().info("lang.yml 已更新为新版本文案(旧文件备份为 lang_old.yml)。");
        } catch (IOException ex) {
            platform.getLogger().severe("升级 lang.yml 时出错", ex);
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
