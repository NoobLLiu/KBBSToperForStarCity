package mc233.fun.kbbstoper.core.sql;

import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.Reward;
import mc233.fun.kbbstoper.core.TopState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** 数据访问层。纯 JDBC，两个平台共用。 */
public abstract class SQLer {

    public static final ReadWriteLock lock = new ReentrantReadWriteLock();
    public static final Lock readlock = lock.readLock();
    public static final Lock writelock = lock.writeLock();

    public String getTableName(String name) {
        return Option.DATABASE_PREFIX.getString() + name;
    }

    public void addPoster(Poster poster) {
        writelock.lock();
        String sql = String.format(
                "INSERT INTO `%s` (`uuid`, `name`, `bbsname`, `binddate`, `rewardbefore`, `rewardtimes`, `maxhp`, `rewardlevel`) VALUES (?, ?, ?, ?, ?, ?, ?, ?);",
                getTableName("posters"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, poster.getUuid());
            pstmt.setString(2, poster.getName());
            pstmt.setString(3, poster.getBbsname());
            pstmt.setLong(4, poster.getBinddate());
            pstmt.setString(5, poster.getRewardbefore());
            pstmt.setInt(6, poster.getRewardtime());
            pstmt.setInt(7, poster.getMaxhp());
            pstmt.setInt(8, poster.getRewardlevel());
            pstmt.executeUpdate();
        } catch (Exception e) {
            KBBSToperCore.logger().severe("写入绑定记录失败(uuid=" + poster.getUuid() + ")", e);
        } finally {
            writelock.unlock();
        }
    }

    public void updatePoster(Poster poster) {
        writelock.lock();
        String sql = String.format(
                "UPDATE `%s` SET `name`=?, `bbsname`=?, `binddate`=?, `rewardbefore`=?, `rewardtimes`=?, `maxhp`=?, `rewardlevel`=? WHERE `uuid`=?;",
                getTableName("posters"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, poster.getName());
            pstmt.setString(2, poster.getBbsname());
            pstmt.setLong(3, poster.getBinddate());
            pstmt.setString(4, poster.getRewardbefore());
            pstmt.setInt(5, poster.getRewardtime());
            pstmt.setInt(6, poster.getMaxhp());
            pstmt.setInt(7, poster.getRewardlevel());
            pstmt.setString(8, poster.getUuid());
            pstmt.executeUpdate();
        } catch (Exception e) {
            KBBSToperCore.logger().severe("更新绑定记录失败(uuid=" + poster.getUuid() + ")", e);
        } finally {
            writelock.unlock();
        }
    }

    /** 记录一次顶帖（兼容旧调用：类型/序号/奖励均未知）。 */
    public void addTopState(String bbsname, String time) {
        addTopState(bbsname, time, 0, 0, null);
    }

    /**
     * 记录一次顶帖，并带上类型/当日序号/奖励文案，供「我的顶帖记录」展示。
     *
     * @param kind   0=平峰期，1=高峰期
     * @param seq    当天第几次（1 起；0=未知）
     * @param reward 本轮奖励文案；{@code null}/空 表示未发放奖励（如已达每日上限）
     */
    public void addTopState(String bbsname, String time, int kind, int seq, String reward) {
        writelock.lock();
        String sql = String.format(
                "INSERT INTO `%s` (`bbsname`, `time`, `kind`, `seq`, `reward`) VALUES (?, ?, ?, ?, ?);",
                getTableName("topstates"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, bbsname);
            pstmt.setString(2, time);
            pstmt.setInt(3, kind);
            pstmt.setInt(4, seq);
            pstmt.setString(5, reward);
            pstmt.executeUpdate();
        } catch (Exception e) {
            KBBSToperCore.logger().severe("记录顶帖失败(bbsname=" + bbsname + ", time=" + time + ")", e);
        } finally {
            writelock.unlock();
        }
    }

    /**
     * 在建表后把 topstates 表迁移到带 kind/seq/reward 列的新结构（幂等）。
     * 同时兼容 SQLite 与 MySQL：列已存在时 ALTER 会抛 duplicate 类错误，忽略即可。
     */
    protected void migrateTopStatesColumns() {
        migrateAddColumn("kind", "INT NOT NULL DEFAULT 0");
        migrateAddColumn("seq", "INT NOT NULL DEFAULT 0");
        migrateAddColumn("reward", "TEXT");
    }

    private void migrateAddColumn(String column, String definition) {
        writelock.lock();
        try (PreparedStatement pstmt = getConnection().prepareStatement(
                String.format("ALTER TABLE `%s` ADD COLUMN `%s` %s;", getTableName("topstates"), column, definition))) {
            pstmt.executeUpdate();
        } catch (SQLException e) {
            // 列已存在（或数据库不支持的部分情况）：忽略 duplicate 类错误，其余上报
            String msg = (e.getMessage() == null) ? "" : e.getMessage().toLowerCase();
            if (!msg.contains("duplicate") && !msg.contains("exists")) {
                KBBSToperCore.logger().severe("迁移 topstates 列失败(" + column + ")", e);
            }
        } finally {
            writelock.unlock();
        }
    }

    /** 清除某个论坛 ID 的全部顶帖记录(调试 clear 用)。 */
    public void clearTopStates(String bbsname) {
        writelock.lock();
        String sql = String.format("DELETE FROM `%s` WHERE `bbsname`=?;", getTableName("topstates"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, bbsname);
            pstmt.executeUpdate();
        } catch (Exception e) {
            KBBSToperCore.logger().severe("清除顶帖记录失败(bbsname=" + bbsname + ")", e);
        } finally {
            writelock.unlock();
        }
    }

    public Poster getPoster(String uuid) {
        readlock.lock();
        String sql = String.format("SELECT * from `%s` WHERE `uuid`=?;", getTableName("posters"));
        Poster poster = null;
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            try (ResultSet rs = pstmt.executeQuery()) {
                try {
                    if (rs.isClosed()) {
                        return poster;
                    }
                } catch (AbstractMethodError ignored) {
                }

                if (rs.next()) {
                    poster = new Poster();
                    poster.setUuid(rs.getString("uuid"));
                    poster.setName(rs.getString("name"));
                    poster.setBbsname(rs.getString("bbsname"));
                    poster.setBinddate(rs.getLong("binddate"));
                    poster.setRewardbefore(rs.getString("rewardbefore"));
                    poster.setRewardtime(rs.getInt("rewardtimes"));
                    int maxhp = rs.getInt("maxhp");
                    poster.setMaxhp(maxhp);
                    int level = rs.getInt("rewardlevel");
                    poster.setRewardlevel(Reward.clampLevel(level > 0 ? level
                            : Math.max(0, maxhp - Reward.hpBase())));
                }
            }
        } catch (Exception e) {
            KBBSToperCore.logger().severe("查询绑定记录失败(uuid=" + uuid + ")", e);
        } finally {
            readlock.unlock();
        }
        return poster;
    }

    public List<TopState> getTopStatesFromPoster(Poster poster) {
        readlock.lock();
        List<TopState> list = new ArrayList<>();
        // 新表结构带 kind/seq/reward；旧表可能只有 time（迁移前），逐列容错读取。
        String sqlFull = String.format(
                "SELECT `time`,`kind`,`seq`,`reward` FROM `%s` WHERE `bbsname`=? ORDER BY `id` DESC;",
                getTableName("topstates"));
        String sqlTimeOnly = String.format(
                "SELECT `time` FROM `%s` WHERE `bbsname`=? ORDER BY `id` DESC;", getTableName("topstates"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sqlFull)) {
            pstmt.setString(1, poster.getBbsname());
            try (ResultSet rs = pstmt.executeQuery()) {
                try {
                    if (rs.isClosed()) {
                        return list;
                    }
                } catch (AbstractMethodError ignored) {
                }
                while (rs.next()) {
                    list.add(readTopState(rs));
                }
            }
        } catch (Exception e) {
            // 旧表无扩展列时回退到 time-only 查询
            try (PreparedStatement pstmt = getConnection().prepareStatement(sqlTimeOnly)) {
                pstmt.setString(1, poster.getBbsname());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new TopState(rs.getString("time"), 0, 0, null));
                    }
                }
            } catch (Exception e2) {
                KBBSToperCore.logger().severe("查询顶帖记录失败(bbsname=" + poster.getBbsname() + ")", e2);
            }
        } finally {
            readlock.unlock();
        }
        return list;
    }

    /** 从结果集读取一条记录，kind/seq/reward 列缺失时容错为默认值。 */
    private static TopState readTopState(ResultSet rs) throws Exception {
        String time = rs.getString("time");
        int kind = 0;
        int seq = 0;
        String reward = null;
        try {
            kind = rs.getInt("kind");
        } catch (Exception ignored) {
        }
        try {
            seq = rs.getInt("seq");
        } catch (Exception ignored) {
        }
        try {
            reward = rs.getString("reward");
        } catch (Exception ignored) {
        }
        return new TopState(time, kind, seq, reward);
    }

    /**
     * 查这个论坛 ID 被谁绑定了。
     *
     * @return 无人绑定时返回 null
     */
    public String bbsNameCheck(String bbsname) {
        readlock.lock();
        String sql = String.format("SELECT `uuid` from `%s` WHERE `bbsname`=?;", getTableName("posters"));
        String uuid = null;
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, bbsname);
            try (ResultSet rs = pstmt.executeQuery()) {
                try {
                    // 查询为空时 sqlite 会直接关闭结果集
                    if (rs.isClosed()) {
                        return uuid;
                    }
                } catch (AbstractMethodError ignored) {
                    // 低版本驱动没有这个方法
                }

                // mysql 则返回一个空结果集
                if (rs.next()) {
                    uuid = rs.getString("uuid");
                }
            }
        } catch (Exception e) {
            KBBSToperCore.logger().severe("按论坛ID查询绑定失败(bbsname=" + bbsname + ")", e);
        } finally {
            readlock.unlock();
        }
        return uuid;
    }

    /** 这条顶帖记录是否已入库。 */
    public boolean checkTopstate(String bbsname, String time) {
        readlock.lock();
        String sql = String.format("SELECT * FROM `%s` WHERE `bbsname`=? AND `time`=? LIMIT 1;",
                getTableName("topstates"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, bbsname);
            pstmt.setString(2, time);
            try (ResultSet rs = pstmt.executeQuery()) {
                try {
                    if (rs.isClosed()) {
                        return false;
                    }
                } catch (AbstractMethodError ignored) {
                }

                if (!rs.next()) {
                    return false;
                }
            }
        } catch (Exception e) {
            KBBSToperCore.logger().severe("检查顶帖记录失败(bbsname=" + bbsname + ", time=" + time + ")", e);
        } finally {
            readlock.unlock();
        }
        return true;
    }

    /** 按顶帖次数排序，不含从未顶帖的玩家。 */
    public List<Poster> getTopPosters() {
        readlock.lock();
        String sql = String.format("SELECT bbsname,COUNT(*) FROM `%s` GROUP BY bbsname ORDER BY COUNT(*) DESC;",
                getTableName("topstates"));
        List<Poster> list = new ArrayList<>();
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String uuid = bbsNameCheck(rs.getString("bbsname"));
                Poster poster = getPoster(uuid);
                if (poster == null) {
                    continue;
                }
                poster.setCount(rs.getInt("COUNT(*)"));
                list.add(poster);
            }
            return list;
        } catch (Exception e) {
            KBBSToperCore.logger().severe("查询顶帖排行榜失败", e);
        } finally {
            readlock.unlock();
        }
        return null;
    }

    /** 已绑定但从未领过奖的玩家，用于补齐排行榜。 */
    public List<Poster> getNoCountPosters() {
        readlock.lock();
        String sql = String.format("SELECT * FROM `%s` WHERE `rewardbefore`='';", getTableName("posters"));
        List<Poster> posterlist = new ArrayList<>();
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Poster poster = new Poster();
                poster.setUuid(rs.getString("uuid"));
                poster.setName(rs.getString("name"));
                poster.setBbsname(rs.getString("bbsname"));
                poster.setBinddate(rs.getLong("binddate"));
                poster.setRewardbefore(rs.getString("rewardbefore"));
                poster.setRewardtime(rs.getInt("rewardtimes"));
                int maxhp = rs.getInt("maxhp");
                poster.setMaxhp(maxhp);
                int level = rs.getInt("rewardlevel");
                poster.setRewardlevel(Reward.clampLevel(level > 0 ? level
                        : Math.max(0, maxhp - Reward.hpBase())));
                poster.setCount(0);
                posterlist.add(poster);
            }
            return posterlist;
        } catch (Exception e) {
            KBBSToperCore.logger().severe("查询无奖励记录失败", e);
        } finally {
            readlock.unlock();
        }
        return null;
    }

    public void deletePoster(String uuid) {
        writelock.lock();
        String sql = String.format("DELETE FROM `%s` WHERE `uuid`=?;", getTableName("posters"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.executeUpdate();
        } catch (Exception e) {
            KBBSToperCore.logger().severe("删除绑定记录失败(uuid=" + uuid + ")", e);
        } finally {
            writelock.unlock();
        }
    }

    protected abstract Connection getConnection();

    /** 关闭连接，已关闭时应静默返回。 */
    public abstract void closeConnection();

    /** 建立连接并建表。 */
    public abstract void load();
}
