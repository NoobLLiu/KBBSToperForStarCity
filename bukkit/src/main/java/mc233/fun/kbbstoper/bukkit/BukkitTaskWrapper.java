package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.core.platform.PlatformTask;

/** Bukkit 任务句柄包装。 */
public class BukkitTaskWrapper implements PlatformTask {

    private final org.bukkit.scheduler.BukkitTask handle;

    public BukkitTaskWrapper(org.bukkit.scheduler.BukkitTask handle) {
        this.handle = handle;
    }

    @Override
    public void cancel() {
        handle.cancel();
    }

    @Override
    public boolean isCancelled() {
        try {
            return handle.isCancelled();
        } catch (NoSuchMethodError e) {
            // 极老的服务端没有这个方法，按未取消处理
            return false;
        }
    }
}
