package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformPlayer;

import java.util.UUID;

/** 查询冷却的读写快捷方法。 */
public class CLIUtil {

    /** 剩余查询冷却（秒），0 表示可以查询。 */
    public static double getQueryCooldown(PlatformPlayer p) {
        UUID uuid = p.getUniqueId();
        return CLI.getInstance().getQueryCooldown(uuid);
    }

    public static void recordQuery(PlatformPlayer p) {
        UUID uuid = p.getUniqueId();
        CLI.getInstance().recordQuery(uuid, System.currentTimeMillis());
    }
}
