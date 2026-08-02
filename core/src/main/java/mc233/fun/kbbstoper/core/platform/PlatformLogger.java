package mc233.fun.kbbstoper.core.platform;

/** 平台无关的日志出口。Bukkit 走 java.util.logging，Nukkit 走 PluginLogger。 */
public interface PlatformLogger {

    void info(String message);

    void warning(String message);

    void severe(String message);

    /** 带异常堆栈的警告。 */
    void warning(String message, Throwable throwable);

    /** 带异常堆栈的严重错误。 */
    void severe(String message, Throwable throwable);
}
