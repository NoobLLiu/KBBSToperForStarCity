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

    public List<String> getTopStates() {
        return sql.getTopStatesFromPoster(this);
    }
}
