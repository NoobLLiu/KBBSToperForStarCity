package mc233.fun.kbbstoper.core.platform;

import java.util.UUID;

/** 离线玩家抽象，仅需要名字、UUID 与在线状态。 */
public interface PlatformOfflinePlayer {

    UUID getUniqueId();

    /** 可能为 null（该 UUID 从未登录过本服）。 */
    String getName();

    boolean isOnline();

    /**
     * 取得在线对象。
     *
     * @return 不在线时返回 null
     */
    PlatformPlayer getOnlinePlayer();
}
