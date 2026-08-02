package mc233.fun.kbbstoper.nukkit;

import cn.nukkit.scheduler.TaskHandler;
import mc233.fun.kbbstoper.core.platform.PlatformTask;

/** Nukkit 任务句柄包装。 */
public class NukkitTaskWrapper implements PlatformTask {

    private final TaskHandler handle;

    public NukkitTaskWrapper(TaskHandler handle) {
        this.handle = handle;
    }

    @Override
    public void cancel() {
        handle.cancel();
    }

    @Override
    public boolean isCancelled() {
        return handle.isCancelled();
    }
}
