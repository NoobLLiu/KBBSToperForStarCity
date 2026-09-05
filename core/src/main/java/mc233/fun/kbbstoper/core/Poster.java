package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.sql.SQLer;

import java.util.List;

/** 一名顶帖者的绑定记录。 */
public class Poster {

    public static SQLer sql;

    private String uuid = "";
    private String name = "";
    private String bbsname = "";
    private long binddate = 0;
    private String rewardbefore = "";
    private int rewardtime = 0;
    private int count = 0;
    /** 当前生命值上限。未顶过帖的玩家初始(最低)为 20，随顶帖累加，由 MGactivity 钳制持久化。 */
    private int maxhp = 20;
    /** 累计奖励等级(对方 287fc94 等级制奖励引擎使用)。0~MAX_REWARD_LEVEL。 */
    private int rewardlevel = 0;
    /** 连续顶帖天数(截至 lastpostday, 断签后归零)。 */
    private int streak = 0;
    /** 最后一次顶帖的日期(yyyy-M-dd, 以论坛顶帖时间为准)。 */
    private String lastpostday = "";
    /** 上次在线(上线/下线)时刻的奖励等级, 用于上线对比升降; -1 = 从未记录。 */
    private int lastlevel = -1;
    /** 上次上线日期(yyyy-M-dd), 用于"每日第一次上线"展示判定。 */
    private String lastseeday = "";

    public static void setSQLer(SQLer sql) {
        Poster.sql = sql;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBbsname() {
        return bbsname;
    }

    public void setBbsname(String bbsname) {
        this.bbsname = bbsname;
    }

    public long getBinddate() {
        return binddate;
    }

    public void setBinddate(long binddate) {
        this.binddate = binddate;
    }

    public String getRewardbefore() {
        return rewardbefore;
    }

    public void setRewardbefore(String rewardbefore) {
        this.rewardbefore = rewardbefore;
    }

    public int getRewardtime() {
        return rewardtime;
    }

    public void setRewardtime(int rewardtime) {
        this.rewardtime = rewardtime;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getMaxhp() {
        return maxhp;
    }

    public void setMaxhp(int maxhp) {
        this.maxhp = maxhp;
    }

    public int getRewardlevel() {
        return rewardlevel;
    }

    public void setRewardlevel(int rewardlevel) {
        this.rewardlevel = rewardlevel;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public String getLastpostday() {
        return lastpostday;
    }

    public void setLastpostday(String lastpostday) {
        this.lastpostday = lastpostday;
    }

    public int getLastlevel() {
        return lastlevel;
    }

    public void setLastlevel(int lastlevel) {
        this.lastlevel = lastlevel;
    }

    public String getLastseeday() {
        return lastseeday;
    }

    public void setLastseeday(String lastseeday) {
        this.lastseeday = lastseeday;
    }

    public List<TopState> getTopStates() {
        return sql.getTopStatesFromPoster(this);
    }
}
