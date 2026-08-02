package mc233.fun.kbbstoper.nukkit;

import cn.nukkit.plugin.PluginLogger;
import mc233.fun.kbbstoper.core.platform.PlatformLogger;

/** 把核心日志接口接到 Nukkit 的 PluginLogger。 */
public class NukkitLogger implements PlatformLogger {

    private final PluginLogger logger;

    public NukkitLogger(PluginLogger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warning(message);
    }

    @Override
    public void severe(String message) {
        logger.error(message);
    }

    @Override
    public void warning(String message, Throwable throwable) {
        logger.warning(message, throwable);
    }

    @Override
    public void severe(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
