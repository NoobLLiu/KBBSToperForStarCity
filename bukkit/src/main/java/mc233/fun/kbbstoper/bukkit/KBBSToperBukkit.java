package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.bukkit.gui.GUI;
import mc233.fun.kbbstoper.bukkit.gui.GUIManager;
import mc233.fun.kbbstoper.bukkit.gui.IDListener;
import mc233.fun.kbbstoper.bukkit.papi.PAPIExpansion;
import mc233.fun.kbbstoper.core.BindingSession;
import mc233.fun.kbbstoper.core.CLI;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Reminder;
import mc233.fun.kbbstoper.core.commands.ReloadHook;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Bukkit 主类。 */
public class KBBSToperBukkit extends JavaPlugin implements TabExecutor, Listener {

    private static KBBSToperBukkit instance;
    private BukkitPlatform platform;

    /** 箱子界面的布局配置，仅 Bukkit 端需要。 */
    private static YamlConfiguration guiConfig;

    public static KBBSToperBukkit getInstance() {
        return instance;
    }

    public static YamlConfiguration getGuiConfig() {
        return guiConfig;
    }

    @Override
    public void onEnable() {
        instance = this;
        platform = new BukkitPlatform(this);

        loadGuiConfig();

        // Bukkit 端用聊天监听收集论坛 ID，绑定结束时要注销监听
        BindingSession.Holder.set(new BindingSession() {
            @Override
            public void finish(PlatformSender sender) {
                if (sender.isPlayer()) {
                    IDListener.unregister(sender.asPlayer().getUniqueId());
                }
            }
        });

        // reload 时把 gui.yml 一起重载
        ReloadHook.Holder.set(this::loadGuiConfig);

        KBBSToperCore.init(platform);

        // PAPI 扩展在数据库就绪后注册，并跟随数据库重建刷新 SQLer
        if (platform.isPlaceholderApiPresent()) {
            new PAPIExpansion(platform).register();
        }

        getCommand("kbbstoper").setExecutor(this);
        getCommand("kbbstoper").setTabCompleter(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new GUIManager(), this);

        // bStats 统计
        new Metrics(this, 21098);

        getLogger().info(Message.ENABLE.getString());
        getLogger().info("------");
        getLogger().info("插件原作者 R_Josef");
        getLogger().info("GitHub项目地址 https://github.com/R-Josef/BBSToper");
        getLogger().info("------");
        getLogger().info("修改作者 小浩");
        getLogger().info("项目地址 https://github.com/NoobLLiu/KBBSToperForStarCity");
        getLogger().info("-----");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        Thread thread = new Thread(() -> {
            KBBSToperCore.shutdown();
            instance = null;
        });
        thread.setDaemon(true);
        thread.start();
    }

    /** 与 resources/gui.yml 顶部的 layout-version 保持一致。 */
    private static final int GUI_LAYOUT_VERSION = 2;

    private void loadGuiConfig() {
        File f = new File(getDataFolder(), "gui.yml");
        if (!f.exists()) {
            saveResource("gui.yml", false);
        }
        // 布局模板升级: 服务端已有旧版 gui.yml 时, 备份为 gui_old.yml 后覆盖为新模板
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(f);
        if (loaded.getInt("layout-version", 0) != GUI_LAYOUT_VERSION) {
            try {
                Files.copy(f.toPath(),
                        new File(getDataFolder(), "gui_old.yml").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                saveResource("gui.yml", true);
                getLogger().info("gui.yml 布局模板已升级到 v" + GUI_LAYOUT_VERSION + ", 旧文件备份为 gui_old.yml");
            } catch (IOException e) {
                getLogger().severe("升级 gui.yml 时出错: " + e.getMessage());
            }
        }
        guiConfig = YamlConfiguration.loadConfiguration(f);
        GUI.setGuiConfig(guiConfig);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return true;
        }
        return cli.onCommand(BukkitSender.of(sender), args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return java.util.Collections.emptyList();
        }
        return cli.onTabComplete(BukkitSender.of(sender), args);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Reminder.onJoin(new BukkitPlayer(event.getPlayer()));
    }
}
