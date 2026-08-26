package mc233.fun.kbbstoper.core.sql;

import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Poster;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
                "INSERT INTO `%s` (`uuid`, `name`, `bbsname`, `binddate`, `rewardbefore`, `rewardtimes`, `maxhp`) VALUES (?, ?, ?, ?, ?, ?, ?);",
                getTableName("posters"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, poster.getUuid());
            pstmt.setString(2, poster.getName());
            pstmt.setString(3, poster.getBbsname());
            pstmt.setLong(4, poster.getBinddate());
            pstmt.setString(5, poster.getRewardbefore());
            pstmt.setInt(6, poster.getRewardtime());
            pstmt.setInt(7, poster.getMaxhp());
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
                "UPDATE `%s` SET `name`=?, `bbsname`=?, `binddate`=?, `rewardbefore`=?, `rewardtimes`=?, `maxhp`=? WHERE `uuid`=?;",
                getTableName("posters"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, poster.getName());
            pstmt.setString(2, poster.getBbsname());
            pstmt.setLong(3, poster.getBinddate());
            pstmt.setString(4, poster.getRewardbefore());
            pstmt.setInt(5, poster.getRewardtime());
            pstmt.setInt(6, poster.getMaxhp());
            pstmt.setString(7, poster.getUuid());
            pstmt.executeUpdate();
        } catch (Exception e) {
            KBBSToperCore.logger().severe("更新绑定记录失败(uuid=" + poster.getUuid() + ")", e);
        } finally {
            writelock.unlock();
        }
    }

    /** 记录一次顶帖。 */
    public void addTopState(String bbsname, String time) {
        writelock.lock();
        String sql = String.format("INSERT INTO `%s` (`bbsname`, `time`) VALUES (?, ?);", getTableName("topstates"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, bbsname);
            pstmt.setString(2, time);
            pstmt.executeUpdate();
        } catch (Exception e) {
            KBBSToperCore.logger().severe("记录顶帖失败(bbsname=" + bbsname + ", time=" + time + ")", e);
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
                    poster.setMaxhp(rs.getInt("maxhp"));
                }
            }
        } catch (Exception e) {
            KBBSToperCore.logger().severe("查询绑定记录失败(uuid=" + uuid + ")", e);
        } finally {
            readlock.unlock();
        }
        return poster;
    }

    public List<String> getTopStatesFromPoster(Poster poster) {
        readlock.lock();
        List<String> list = new ArrayList<>();
        String sql = String.format("SELECT `time` from `%s` WHERE `bbsname`=?;", getTableName("topstates"));
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, poster.getBbsname());
            try (ResultSet rs = pstmt.executeQuery()) {
                try {
                    if (rs.isClosed()) {
                        return list;
                    }
                } catch (AbstractMethodError ignored) {
                }

                while (rs.next()) {
                    list.add(rs.getString("time"));
                }
            }
        } catch (Exception e) {
            KBBSToperCore.logger().severe("查询顶帖记录失败(bbsname=" + poster.getBbsname() + ")", e);
        } finally {
            readlock.unlock();
        }
        return list;
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
