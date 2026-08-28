package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.core.platform.MGactivityApi;
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
        // 1) 优先直接对接 EssentialsX 经济(Essentials 金钱)；无论是否安装 Vault 都能发放
        if (depositEssentials(player, amount)) {
            return;
        }
        // 2) 回退 Vault 经济核心(任何提供 Economy 服务的插件, 含 Essentials 经 Vault 的提供)
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null && rsp.getProvider() != null) {
            // 按玩家名发放(与顶帖绑定名一致); 用 OfflinePlayer 重载(非废弃, 兼容性更好)
            OfflinePlayer op = Bukkit.getOfflinePlayer(player);
            rsp.getProvider().depositPlayer(op, amount);
            return;
        }
        logger.warning("未安装 EssentialsX 或 Vault 经济核心，无法给 " + player + " 发放星光点 " + amount);
    }

    /**
     * 通过 EssentialsX 原生 API 直接给玩家加钱(星光点 = Essentials 金钱)。
     * 全部使用全限定名(不 import)，未安装 EssentialsX 或缺口时返回 false 由调用方回退 Vault。
     *
     * @return 是否成功发放
     */
    @SuppressWarnings("deprecation")
    private boolean depositEssentials(String player, double amount) {
        try {
            org.bukkit.plugin.Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (!(ess instanceof com.earth2me.essentials.Essentials)) {
                return false;
            }
            com.earth2me.essentials.Essentials essentials = (com.earth2me.essentials.Essentials) ess;
            UUID uuid = resolveUuid(player);
            if (uuid == null) {
                return false;
            }
            // getUser 按 UUID 取/建账号, 再走静态 API 的 add(User, BigDecimal)
            com.earth2me.essentials.User user = essentials.getUser(uuid);
            if (user == null) {
                return false;
            }
            com.earth2me.essentials.api.Economy.add(user, java.math.BigDecimal.valueOf(amount));
            return true;
        } catch (Throwable t) {
            logger.warning("EssentialsX 发放星光点失败(" + player + ", " + amount + "): " + t);
            return false;
        }
    }

    /** 优先用在线玩家精确匹配解析 UUID, 否则回退 OfflinePlayer。 */
    @SuppressWarnings("deprecation")
    private UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            return op.getUniqueId();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public MGactivityApi getMGactivityApi() {
        // MGactivity 已实现并注册时返回其实现，否则返回 null（调用方回退命令）
        return Bukkit.getServer().getServicesManager().load(MGactivityApi.class);
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
