package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.Platform;
import mc233.fun.kbbstoper.core.platform.PlatformLogger;
import mc233.fun.kbbstoper.core.platform.PlatformScheduler;
import mc233.fun.kbbstoper.core.sql.SQLManager;

/**
 * 平台无关的插件核心。
 * bukkit / nukkit 的主类在 onEnable 里 {@link #init(Platform)}，在 onDisable 里 {@link #shutdown()}。
 */
public final class KBBSToperCore {

    private static Platform platform;
    private static ConfigManager configManager;
    private static CLI cli;

    private KBBSToperCore() {
    }

    public static void init(Platform platform) {
        KBBSToperCore.platform = platform;

        // 1. 配置与语言
        configManager = new ConfigManager(platform);
        Option.load(platform);
        Message.load(configManager);

        // 2. 数据库
        SQLManager.initializeSQLer();

        // 3. 命令分发器
        cli = new CLI(configManager);

        // 4. 周期任务
        SQLManager.startTimingReconnect();
        Util.startAutoReward();
    }

    /**
     * 关闭核心。会阻塞等待本插件创建的异步任务收尾，因此调用方应放在守护线程里。
     */
    public static void shutdown() {
        Util.waitForAllTask();
        SQLManager.closeSQLer();
        cli = null;
        configManager = null;
        platform = null;
    }

    public static Platform platform() {
        return platform;
    }

    public static PlatformLogger logger() {
        return platform.getLogger();
    }

    public static PlatformScheduler scheduler() {
        return platform.getScheduler();
    }

    public static ConfigManager configManager() {
        return configManager;
    }

    public static CLI cli() {
        return cli;
    }
}
