package mc233.fun.kbbstoper.nukkit;

import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;
import mc233.fun.kbbstoper.core.platform.PlatformScheduler;
import mc233.fun.kbbstoper.core.platform.PlatformTask;

/** 基于 ServerScheduler 的调度实现。 */
public class NukkitSchedulerImpl implements PlatformScheduler {

    private final Plugin plugin;

    public NukkitSchedulerImpl(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runSync(Runnable runnable) {
        if (Server.getInstance().isPrimaryThread()) {
            runnable.run();
            return;
        }
        Server.getInstance().getScheduler().scheduleTask(plugin, runnable, false);
    }

    @Override
    public PlatformTask runAsync(Runnable runnable) {
        return new NukkitTaskWrapper(
                Server.getInstance().getScheduler().scheduleTask(plugin, runnable, true));
    }

    @Override
    public PlatformTask runLater(Runnable runnable, int delayTicks) {
        return new NukkitTaskWrapper(
                Server.getInstance().getScheduler().scheduleDelayedTask(plugin, runnable, delayTicks));
    }

    @Override
    public PlatformTask runAsyncTimer(Runnable runnable, int delayTicks, int periodTicks) {
        return new NukkitTaskWrapper(Server.getInstance().getScheduler()
                .scheduleDelayedRepeatingTask(plugin, runnable, delayTicks, periodTicks, true));
    }

    @Override
    public void cancelAll() {
        Server.getInstance().getScheduler().cancelTask(plugin);
    }
}
