package mc233.fun.kbbstoper.core.platform;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 平台门面。core 里所有与服务端打交道的动作都从这里出发，
 * bukkit / nukkit 各自提供一个实现。
 */
public interface Platform {

    // ---- 插件自身信息 ----

    PlatformLogger getLogger();

    File getDataFolder();

    /** 插件版本，用于配置版本比对与 PAPI 展示。 */
    String getPluginVersion();

    List<String> getPluginAuthors();

    /**
     * 读取 jar 内资源。
     *
     * @return 不存在时返回 null
     */
    InputStream getResource(String name);

    /**
     * 把 jar 内资源释放到数据目录。
     *
     * @param replace 目标已存在时是否覆盖
     */
    void saveResource(String name, boolean replace);

    // ---- 配置 ----

    /** 加载数据目录下的一个 YAML 文件，文件不存在时返回空配置而不是 null。 */
    PlatformConfig loadConfig(File file);

    // ---- 调度 ----

    PlatformScheduler getScheduler();

    // ---- 玩家 ----

    Collection<PlatformPlayer> getOnlinePlayers();

    /**
     * 按 UUID 找在线玩家。
     *
     * @return 不在线时返回 null
     */
    PlatformPlayer getPlayer(UUID uuid);

    /** 按 UUID 取离线玩家，恒不为 null。 */
    PlatformOfflinePlayer getOfflinePlayer(UUID uuid);

    /**
     * 按名字取离线玩家。
     *
     * @return 该名字从未在本服出现时返回 null
     */
    PlatformOfflinePlayer getOfflinePlayer(String name);

    /** 控制台发送者。 */
    PlatformSender getConsoleSender();

    /** 以控制台身份执行一条命令（奖励命令由此下发）。 */
    void dispatchConsoleCommand(String command);

    /**
     * 通过经济核心（如 Vault）给玩家发放货币/积分（星光点等）。
     * 平台不保证一定有经济插件：未安装时静默忽略（不抛异常）。
     * amount &le; 0 时静默忽略。
     *
     * @param player 玩家名（与顶帖绑定名一致）
     * @param amount 发放数量
     */
    void depositEconomy(String player, double amount);

    // ---- 杂项 ----

    /** 把 &a 之类的颜色码转成平台使用的格式。 */
    String colorize(String text);

    /** PlaceholderAPI（或等价插件）是否可用。 */
    boolean isPlaceholderApiPresent();

    /** 对一段文本套用占位符解析，不可用时原样返回。 */
    String applyPlaceholders(PlatformPlayer player, String text);
}
