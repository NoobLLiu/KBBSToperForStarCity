package mc233.fun.kbbstoper.core.sql;

import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Option;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** MySQL 后端。驱动由插件自带（shade 进 jar）。 */
public class MySQLer extends SQLer {

    private static final MySQLer SQLER = new MySQLer();
    private Connection conn;

    private MySQLer() {
    }

    public static MySQLer getInstance() {
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
        boolean ssl = Option.DATABASE_MYSQL_SSL.getBoolean();
        return "jdbc:mysql://" + Option.DATABASE_MYSQL_IP.getString() + ":"
                + Option.DATABASE_MYSQL_PORT.getString() + "/" + Option.DATABASE_MYSQL_DATABASE.getString()
                + "?useSSL=" + ssl
                + "&serverTimezone=UTC"
                + "&autoReconnect=true"
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=utf8";
    }

    @Override
    public void load() {
        connect();
        createTablePosters();
        createTableTopStates();
    }

    protected void connect() {
        String user = Option.DATABASE_MYSQL_USER.getString();
        String password = Option.DATABASE_MYSQL_PASSWORD.getString();
        try {
            // 8.x 驱动类名；找不到时回落到 5.x 的旧类名
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException ignored) {
                Class.forName("com.mysql.jdbc.Driver");
            }
            this.conn = DriverManager.getConnection(getUrl(), user, password);
        } catch (ClassNotFoundException | SQLException e) {
            KBBSToperCore.logger().warning(Message.FAILEDCONNECTSQL.getString(), e);
        }
    }

    protected void createTablePosters() {
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS `%s` ( `uuid` char(36) NOT NULL, `name` varchar(255) NOT NULL, `bbsname` varchar(255) NOT NULL, `binddate` bigint(0) NOT NULL, `rewardbefore` char(10) NOT NULL, `rewardtimes` int(0) NOT NULL, `maxhp` int(0) NOT NULL DEFAULT 20, PRIMARY KEY (`uuid`) ) CHARACTER SET utf8 COLLATE utf8_unicode_ci;",
                getTableName("posters"));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            KBBSToperCore.logger().severe("创建 posters 表失败", e);
        }
        // 兼容旧表: 补上 maxhp 列(列已存在则忽略异常)
        migrateMaxHpColumn();
    }

    private void migrateMaxHpColumn() {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE `" + getTableName("posters")
                    + "` ADD COLUMN `maxhp` int(0) NOT NULL DEFAULT 20");
        } catch (SQLException ignored) {
            // 列已存在(错误码 1060 duplicate column)时静默跳过
        }
    }

    protected void createTableTopStates() {
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS `%s` ( `id` int(0) NOT NULL AUTO_INCREMENT, `bbsname` varchar(255) NOT NULL, `time` varchar(16) NOT NULL, PRIMARY KEY (`id`) ) CHARACTER SET utf8 COLLATE utf8_unicode_ci;",
                getTableName("topstates"));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            KBBSToperCore.logger().warning("关闭数据库连接失败", e);
        }
    }
}
