package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.core.platform.PlatformScheduler;
import mc233.fun.kbbstoper.core.platform.PlatformTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** 基于 BukkitScheduler 的调度实现。 */
public class BukkitSchedulerImpl implements PlatformScheduler {

    private final Plugin plugin;

    public BukkitSchedulerImpl(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    @Override
    public PlatformTask runAsync(Runnable runnable) {
        return new BukkitTaskWrapper(new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        }.runTaskAsynchronously(plugin));
    }

    @Override
    public PlatformTask runLater(Runnable runnable, int delayTicks) {
        return new BukkitTaskWrapper(new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        }.runTaskLater(plugin, delayTicks));
    }

    @Override
    public PlatformTask runAsyncTimer(Runnable runnable, int delayTicks, int periodTicks) {
        return new BukkitTaskWrapper(new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        }.runTaskTimerAsynchronously(plugin, delayTicks, periodTicks));
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
