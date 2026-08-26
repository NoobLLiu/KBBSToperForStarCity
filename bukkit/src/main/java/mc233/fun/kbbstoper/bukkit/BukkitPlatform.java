package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.core.platform.Platform;
import mc233.fun.kbbstoper.core.platform.PlatformConfig;
import mc233.fun.kbbstoper.core.platform.PlatformLogger;
import mc233.fun.kbbstoper.core.platform.PlatformOfflinePlayer;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.platform.PlatformScheduler;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Bukkit 平台实现。 */
public class BukkitPlatform implements Platform {

    private final JavaPlugin plugin;
    private final PlatformLogger logger;
    private final PlatformScheduler scheduler;

    public BukkitPlatform(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = new BukkitLogger(plugin.getLogger());
        this.scheduler = new BukkitSchedulerImpl(plugin);
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
        return new BukkitConfig(YamlConfiguration.loadConfiguration(file));
    }

    @Override
    public PlatformScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public Collection<PlatformPlayer> getOnlinePlayers() {
        List<PlatformPlayer> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            list.add(new BukkitPlayer(p));
        }
        return list;
    }

    @Override
    public PlatformPlayer getPlayer(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p == null ? null : new BukkitPlayer(p);
    }

    @Override
    public PlatformOfflinePlayer getOfflinePlayer(UUID uuid) {
        return new BukkitOfflinePlayer(Bukkit.getOfflinePlayer(uuid));
    }

    @Override
    @SuppressWarnings("deprecation")
    public PlatformOfflinePlayer getOfflinePlayer(String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        // Bukkit 对陌生名字也会造一个对象，这里保持原有行为直接返回
        return new BukkitOfflinePlayer(op);
    }

    @Override
    public PlatformSender getConsoleSender() {
        return new BukkitSender(Bukkit.getConsoleSender());
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void depositEconomy(String player, double amount) {
        if (amount <= 0) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            logger.warning("Vault 经济核心未安装，无法给 " + player + " 发放星光点 " + amount);
            return;
        }
        // 按玩家名发放(与顶帖绑定名一致); Vault 的 String 重载虽标注废弃但兼容性最好
        rsp.getProvider().depositPlayer(player, amount);
    }

    @Override
    public String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public boolean isPlaceholderApiPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Override
    public String applyPlaceholders(PlatformPlayer player, String text) {
        if (!isPlaceholderApiPresent()) {
            return text;
        }
        Player handle = (player instanceof BukkitPlayer) ? ((BukkitPlayer) player).player() : null;
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(handle, text);
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
