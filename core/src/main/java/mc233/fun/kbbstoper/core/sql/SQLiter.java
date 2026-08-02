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
                "CREATE TABLE IF NOT EXISTS `%s` ( `uuid` char(36) NOT NULL, `name` varchar(255) NOT NULL, `bbsname` varchar(255) NOT NULL COLLATE NOCASE, `binddate` bigint(0) NOT NULL, `rewardbefore` char(10) NOT NULL, `rewardtimes` int(0) NOT NULL, PRIMARY KEY (`uuid`) );",
                getTableName("posters"));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            KBBSToperCore.logger().severe("创建 posters 表失败", e);
        }
    }

    protected void createTableTopStates() {
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS `%s` ( `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `bbsname` varchar(255) NOT NULL COLLATE NOCASE, `time` varchar(16) NOT NULL);",
                getTableName("topstates"));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            KBBSToperCore.logger().warning("关闭数据库连接失败", e);
        }
    }
}
