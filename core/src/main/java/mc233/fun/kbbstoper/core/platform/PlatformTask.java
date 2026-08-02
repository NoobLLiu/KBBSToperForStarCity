package mc233.fun.kbbstoper.core.platform;

/** 任务句柄，只需要取消与查询取消状态。 */
public interface PlatformTask {

    void cancel();

    boolean isCancelled();
}
