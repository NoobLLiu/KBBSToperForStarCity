package mc233.fun.kbbstoper.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双端 GUI 分页状态跟踪。
 * 我的记录 / 排行榜 共用一套页码状态；玩家退出时由平台清理。
 */
public final class MenuRouter {

    private static final Map<UUID, PageState> STATES = new ConcurrentHashMap<>();

    private MenuRouter() {
    }

    public static final class PageState {
        /** 我的记录当前页。 */
        public int recordPage = 1;
        /** 排行榜当前页。 */
        public int topPage = 1;
        /** 我的记录总页数。 */
        public int totalRecordPages = 1;
        /** 排行榜总页数。 */
        public int totalTopPages = 1;
    }

    public static PageState state(UUID uuid) {
        return STATES.computeIfAbsent(uuid, k -> new PageState());
    }

    public static void clear(UUID uuid) {
        STATES.remove(uuid);
    }
}
