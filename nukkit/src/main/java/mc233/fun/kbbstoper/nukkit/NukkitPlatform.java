package mc233.fun.kbbstoper.nukkit;

import cn.nukkit.IPlayer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import mc233.fun.kbbstoper.core.platform.Platform;
import mc233.fun.kbbstoper.core.platform.PlatformConfig;
import mc233.fun.kbbstoper.core.platform.PlatformLogger;
import mc233.fun.kbbstoper.core.platform.PlatformOfflinePlayer;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.platform.PlatformScheduler;
import mc233.fun.kbbstoper.core.platform.PlatformSender;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Nukkit 平台实现。 */
public class NukkitPlatform implements Platform {

    private final PluginBase plugin;
    private final PlatformLogger logger;
    private final PlatformScheduler scheduler;

    public NukkitPlatform(PluginBase plugin) {
        this.plugin = plugin;
        this.logger = new NukkitLogger(plugin.getLogger());
        this.scheduler = new NukkitSchedulerImpl(plugin);
    }

    @Override
    public PlatformLogger getLogger() {
        return logger;
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public List<String> getPluginAuthors() {
        return plugin.getDescription().getAuthors();
    }

    @Override
    public InputStream getResource(String name) {
        return plugin.getResource(name);
    }

    @Override
    public void saveResource(String name, boolean replace) {
        plugin.saveResource(name, replace);
    }

    @Override
    public PlatformConfig loadConfig(File file) {
        return new NukkitConfigAdapter(new Config(file, Config.YAML));
    }

    @Override
    public PlatformScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public Collection<PlatformPlayer> getOnlinePlayers() {
        List<PlatformPlayer> list = new ArrayList<>();
        for (Player p : Server.getInstance().getOnlinePlayers().values()) {
            list.add(new NukkitPlayer(p));
        }
        return list;
    }

    @Override
    public PlatformPlayer getPlayer(UUID uuid) {
        Optional<Player> p = Server.getInstance().getPlayer(uuid);
        return p.map(NukkitPlayer::new).orElse(null);
    }

    @Override
    public PlatformOfflinePlayer getOfflinePlayer(UUID uuid) {
        return new NukkitOfflinePlayer(Server.getInstance().getOfflinePlayer(uuid));
    }

    @Override
    public PlatformOfflinePlayer getOfflinePlayer(String name) {
        // 基岩服玩家 UUID 由服务端按名字生成，先查名字表；查不到就没有这个人的数据
        Optional<UUID> uuid = Server.getInstance().lookupName(name);
        if (uuid.isEmpty()) {
            return null;
        }
        IPlayer p = Server.getInstance().getOfflinePlayer(uuid.get());
        return p == null ? null : new NukkitOfflinePlayer(p);
    }

    @Override
    public PlatformSender getConsoleSender() {
        return new NukkitSender(Server.getInstance().getConsoleSender());
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        Server.getInstance().dispatchCommand(Server.getInstance().getConsoleSender(), command);
    }

    @Override
    public String colorize(String text) {
        return TextFormat.colorize('&', text);
    }

    @Override
    public boolean isPlaceholderApiPresent() {
        // Nukkit 端的 PlaceholderAPI 是若干互不兼容的第三方分支，
        // 这里不做集成，占位符只通过插件自身的表单与消息使用。
        return false;
    }

    @Override
    public String applyPlaceholders(PlatformPlayer player, String text) {
        return text;
    }

    public PluginBase plugin() {
        return plugin;
    }
}
