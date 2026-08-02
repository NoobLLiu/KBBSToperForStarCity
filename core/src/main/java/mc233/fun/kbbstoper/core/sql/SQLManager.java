package mc233.fun.kbbstoper.core.sql;

import mc233.fun.kbbstoper.core.Crawler;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.Reminder;
import mc233.fun.kbbstoper.core.Util;
import mc233.fun.kbbstoper.core.platform.PlatformTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 数据库生命周期管理。 */
public class SQLManager {

    public static SQLer sql;
    private static PlatformTask timingreconnecttask;

    /**
     * 需要在数据库重建后重新注入 SQLer 的消费者。
     * 平台模块（如 PAPI 扩展）在启动时注册自己，避免 core 反向依赖平台代码。
     */
    private static final List<Consumer<SQLer>> LISTENERS = new ArrayList<>();

    public static void registerSQLerListener(Consumer<SQLer> listener) {
        LISTENERS.add(listener);
        if (sql != null) {
            listener.accept(sql);
        }
    }

    /** 初始化或重载数据库。 */
    public static void initializeSQLer() {
        SQLer.writelock.lock();
        try {
            if (sql != null) {
                sql.closeConnection();
            }
            if ("mysql".equalsIgnoreCase(Option.DATABASE_TYPE.getString())) {
                sql = MySQLer.getInstance();
            } else if ("sqlite".equalsIgnoreCase(Option.DATABASE_TYPE.getString())) {
                sql = SQLiter.getInstance();
            }
            sql.load();
            SQLer instance = sql;
            Crawler.setSQLer(instance);
            Poster.setSQLer(instance);
            Reminder.setSQLer(instance);
            for (Consumer<SQLer> listener : LISTENERS) {
                listener.accept(instance);
            }
        } catch (Exception e) {
            KBBSToperCore.logger().severe("初始化数据库失败", e);
        } finally {
            SQLer.writelock.unlock();
        }
    }

    public static void closeSQLer() {
        if (sql != null) {
            sql.closeConnection();
        }
        sql = null;
    }

    public static SQLer getSQLer() {
        return sql;
    }

    /** 按配置周期重连数据库；周期为 0 时不启动。 */
    public static void startTimingReconnect() {
        if (timingreconnecttask != null && !timingreconnecttask.isCancelled()) {
            timingreconnecttask.cancel();
        }
        int period = Option.DATABASE_TIMINGRECONNECT.getInt() * 20;
        if (period > 0) {
            timingreconnecttask = KBBSToperCore.scheduler().runAsyncTimer(() -> {
                Util.enterTask();
                try {
                    initializeSQLer();
                } finally {
                    Util.exitTask();
                }
            }, period, period);
        }
    }
}
