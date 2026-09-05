package mc233.fun.kbbstoper.core.sql;

import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Option;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite 后端。驱动由插件自带（shade 进 jar）。 */
public class SQLiter extends SQLer {

    private static final SQLiter SQLER = new SQLiter();
    private Connection conn;

    private SQLiter() {
    }

    public static SQLiter getInstance() {
        return SQLER;
    }

    @Override
    protected Connection getConnection() {
        return this.conn;
    }

    @Override
    public void closeConnection() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            KBBSToperCore.logger().warning("关闭数据库连接失败", e);
        }
    }

    public String getUrl() {
        String folder = KBBSToperCore.platform().getDataFolder().getPath();
        String path = Option.DATABASE_SQLITE_FOLDER.getString().replaceAll("%PLUGIN_FOLDER%", "%s");
        String url = "jdbc:sqlite:" + path + File.separator + Option.DATABASE_SQLITE_DATABASE.getString();
        return String.format(url, folder);
    }

    @Override
    public void load() {
        connect();
        createTablePosters();
        createTableTopStates();
    }

    protected void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            this.conn = DriverManager.getConnection(getUrl());
        } catch (ClassNotFoundException | SQLException e) {
            KBBSToperCore.logger().warning(Message.FAILEDCONNECTSQL.getString(), e);
        }
    }

    protected void createTablePosters() {
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS `%s` ( `uuid` char(36) NOT NULL, `name` varchar(255) NOT NULL, `bbsname` varchar(255) NOT NULL COLLATE NOCASE, `binddate` bigint(0) NOT NULL, `rewardbefore` char(10) NOT NULL, `rewardtimes` int(0) NOT NULL, `maxhp` int(0) NOT NULL DEFAULT 20, `rewardlevel` int(0) NOT NULL DEFAULT 0, `streak` int(0) NOT NULL DEFAULT 0, `lastpostday` varchar(16) NOT NULL DEFAULT '', `lastlevel` int(0) NOT NULL DEFAULT -1, `lastseeday` varchar(16) NOT NULL DEFAULT '', PRIMARY KEY (`uuid`) );",
                getTableName("posters"));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            KBBSToperCore.logger().severe("创建 posters 表失败", e);
        }
        // 兼容旧表: 逐个补列(列已存在则忽略异常)
        migrateMaxHpColumn();
        migrateRewardLevelColumn();
        migratePosterColumn("streak", "int(0) NOT NULL DEFAULT 0");
        migratePosterColumn("lastpostday", "varchar(16) NOT NULL DEFAULT ''");
        migratePosterColumn("lastlevel", "int(0) NOT NULL DEFAULT -1");
        migratePosterColumn("lastseeday", "varchar(16) NOT NULL DEFAULT ''");
    }

    private void migrateMaxHpColumn() {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE `" + getTableName("posters")
                    + "` ADD COLUMN `maxhp` int(0) NOT NULL DEFAULT 20");
        } catch (SQLException ignored) {
            // 列已存在(duplicate column)或驱动不支持时静默跳过
        }
    }

    private void migrateRewardLevelColumn() {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE `" + getTableName("posters")
                    + "` ADD COLUMN `rewardlevel` int(0) NOT NULL DEFAULT 0");
        } catch (SQLException ignored) {
            // 列已存在(duplicate column)或驱动不支持时静默跳过
        }
    }

    /** 兼容旧表: 给 posters 补新列(streak/lastpostday/lastlevel/lastseeday)。 */
    private void migratePosterColumn(String column, String definition) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE `" + getTableName("posters")
                    + "` ADD COLUMN `" + column + "` " + definition);
        } catch (SQLException ignored) {
            // 列已存在(duplicate column)时静默跳过
        }
    }

    protected void createTableTopStates() {
        // time 放宽到 varchar(32): 论坛时间带秒时为 17 字符, 旧的 16 会被截断导致去重失灵
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS `%s` ( `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `bbsname` varchar(255) NOT NULL COLLATE NOCASE, `time` varchar(32) NOT NULL);",
                getTableName("topstates"));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            KBBSToperCore.logger().warning("创建顶帖记录表失败", e);
        }
        // 迁移到带 kind/seq/reward 列的新结构（旧库已在用, 不能丢数据）
        migrateTopStatesColumns();
    }
}
