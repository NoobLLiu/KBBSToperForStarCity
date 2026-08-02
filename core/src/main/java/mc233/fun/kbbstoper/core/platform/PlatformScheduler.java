package mc233.fun.kbbstoper.core.platform;

/**
 * 调度抽象。
 * Bukkit 用 BukkitRunnable，Nukkit 用 ServerScheduler，
 * 两边的任务句柄统一收敛到 {@link PlatformTask}。
 */
public interface PlatformScheduler {

    /** 在主线程执行一次。 */
    void runSync(Runnable runnable);

    /** 在异步线程执行一次。 */
    PlatformTask runAsync(Runnable runnable);

    /**
     * 延迟后在主线程执行一次。
     *
     * @param delayTicks 延迟刻数（20 刻 = 1 秒）
     */
    PlatformTask runLater(Runnable runnable, int delayTicks);

    /**
     * 异步周期任务。
     *
     * @param delayTicks  首次执行前的延迟刻数
     * @param periodTicks 执行间隔刻数
     */
    PlatformTask runAsyncTimer(Runnable runnable, int delayTicks, int periodTicks);

    /** 取消本插件的全部任务，插件关闭时调用。 */
    void cancelAll();
}
