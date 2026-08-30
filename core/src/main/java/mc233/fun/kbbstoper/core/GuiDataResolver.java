package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.MGactivityApi;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.TopState;
import mc233.fun.kbbstoper.core.sql.SQLer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * GUI 双端共用的内置占位符解析，不依赖 PlaceholderAPI。
 *
 * <p>支持占位符：%BBSNAME% %POSTTIMES% %MAXHP% %REWARDTIME% %REWARDBEFORE%
 * %TODAYLIMIT% %COOLDOWN% %PAGEURL% %PREFIX%。
 * 未知占位符原样保留（若装了 PAPI，上层可再叠加一次 PAPI 解析）。</p>
 */
public final class GuiDataResolver {

    private GuiDataResolver() {
    }

    /** 取玩家绑定记录，未绑定或数据库未就绪时返回 null。 */
    public static Poster poster(PlatformPlayer player) {
        if (player == null) {
            return null;
        }
        SQLer sql = SQLManager.getSQLer();
        if (sql == null) {
            return null;
        }
        return sql.getPoster(player.getUniqueId().toString());
    }

    /** 顶帖次数（记录条数）。 */
    public static int postTimes(Poster poster) {
        if (poster == null || poster.getBbsname() == null || poster.getBbsname().isBlank()) {
            return 0;
        }
        List<TopState> states = poster.getTopStates();
        return states == null ? 0 : states.size();
    }

    /** 今日可计入奖励的顶帖次数上限。 */
    public static int todayLimit() {
        return Math.max(0, Option.REWARD_TIMES.getInt(3));
    }

    /** 宣传帖地址。 */
    public static String pageUrl() {
        return "https://" + Option.WEBSITE.getString() + "/thread-"
                + Option.BBS_URL.getString() + "-1-1.html";
    }

    /** 查询冷却剩余秒数。 */
    public static double cooldownSeconds(PlatformPlayer player) {
        CLI cli = KBBSToperCore.cli();
        if (cli == null || player == null) {
            return 0;
        }
        return cli.getQueryCooldown(player.getUniqueId());
    }

    /** 解析一段文本里的内置占位符。player 可为 null（无玩家上下文）。 */
    public static String resolve(PlatformPlayer player, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Poster poster = poster(player);
        String bbsname = (poster == null || poster.getBbsname() == null || poster.getBbsname().isBlank())
                ? Message.GUI_NOTBOUND.getString() : poster.getBbsname();
        String rewardbefore = (poster == null || poster.getRewardbefore() == null || poster.getRewardbefore().isBlank())
                ? Message.NONE.getString() : poster.getRewardbefore();
        int rewardtime = poster == null ? 0 : poster.getRewardtime();
        int maxhp = poster == null ? Option.REWARD_VAL_HP_BASE.getInt() : poster.getMaxhp();
        double cd = cooldownSeconds(player);

        return text
                .replace("%BBSNAME%", bbsname)
                .replace("%POSTTIMES%", String.valueOf(postTimes(poster)))
                .replace("%MAXHP%", String.valueOf(maxhp))
                .replace("%REWARDTIME%", String.valueOf(rewardtime))
                .replace("%REWARDBEFORE%", rewardbefore)
                .replace("%TODAYLIMIT%", String.valueOf(todayLimit()))
                .replace("%COOLDOWN%", String.valueOf((int) Math.ceil(cd)))
                .replace("%PAGEURL%", pageUrl())
                .replace("%PREFIX%", Message.PREFIX.getString());
    }

    /** 我的状态页内容（双端共用文案，实时读配置与 MGactivity 当前值）。 */
    public static List<String> statusLines(PlatformPlayer player) {
        List<String> out = new ArrayList<>();
        Poster poster = poster(player);
        boolean bound = poster != null && poster.getBbsname() != null && !poster.getBbsname().isBlank();
        if (!bound) {
            out.add(resolve(player, Message.GUI2_STATUS_BBSID.getString()));
            return out;
        }
        // 今日已领取次数: 仅当 rewardbefore 为今天才算数, 否则视为 0(跨天未领)
        String todayStr = new SimpleDateFormat("yyyy-M-dd").format(new Date());
        boolean today = poster.getRewardbefore() != null && poster.getRewardbefore().equals(todayStr);
        int limit = todayLimit();
        int claimed = today ? poster.getRewardtime() : 0;
        int level = Reward.clampLevel(poster.getRewardlevel());
        int maxLevel = Reward.maxLevel();
        int nextGain = Reward.isPeakNow() ? Reward.gainPeak() : Reward.gainNormal();
        int nextLevel = Math.min(maxLevel, level + nextGain);

        // 当前数值(优先读 MGactivity 查询接口, 未实现则用本插件记录的目标值 / "—")
        MGactivityApi mg = KBBSToperCore.platform().getMGactivityApi();
        String name = poster.getName();
        int curHp = (mg != null) ? mg.getMaxHp(name) : -1;
        if (curHp < 0) {
            curHp = poster.getMaxhp();
        }
        String curGm = multStr(mg == null ? -1 : mg.getGrowthMultiplier(name));
        String curEm = multStr(mg == null ? -1 : mg.getExperienceMultiplier(name));
        String curGrowth = valStr(mg == null ? -1 : mg.getGrowthValue(name));
        String curStar = starStr(mg == null ? -1 : mg.getStarlightPoints(name));

        StatusCtx ctx = new StatusCtx(claimed, limit, level, maxLevel, nextGain, nextLevel,
                curHp, curGm, curEm, curGrowth, curStar);

        out.add(resolve(player, Message.GUI2_STATUS_BBSID.getString()));
        out.add(resolve(player, Message.GUI2_STATUS_POSTTIMES.getString()));
        out.add(statusReplace(Message.GUI2_STATUS_LEVEL.getString(), ctx));
        out.add(statusReplace(Message.GUI2_STATUS_TODAY.getString(), ctx));
        out.add(statusReplace(Message.GUI2_STATUS_NEXT.getString(), ctx));
        out.add(statusReplace(Message.GUI2_STATUS_CURHP.getString(), ctx));
        out.add(statusReplace(Message.GUI2_STATUS_CURGM.getString(), ctx));
        out.add(statusReplace(Message.GUI2_STATUS_CUREM.getString(), ctx));
        out.add(statusReplace(Message.GUI2_STATUS_CURGROWTH.getString(), ctx));
        out.add(statusReplace(Message.GUI2_STATUS_CURSTAR.getString(), ctx));
        out.add(resolve(player, Message.GUI2_STATUS_REWARDBEFORE.getString()));
        return out;
    }

    /** 状态页实时数值集合。 */
    private record StatusCtx(int claimed, int limit, int level, int maxLevel,
                             int nextGain, int nextLevel, int curHp,
                             String curGm, String curEm, String curGrowth, String curStar) {
    }

    /** 把状态页专属占位符替换成实时计算值。 */
    private static String statusReplace(String text, StatusCtx c) {
        if (text == null) {
            return "";
        }
        return text
                .replace("%TODAY%", String.valueOf(c.claimed()))
                .replace("%LIMIT%", String.valueOf(c.limit()))
                .replace("%LEVEL%", String.valueOf(c.level()))
                .replace("%MAXLEVEL%", String.valueOf(c.maxLevel()))
                .replace("%NEXTGAIN%", String.valueOf(c.nextGain()))
                .replace("%NEXTLEVEL%", String.valueOf(c.nextLevel()))
                .replace("%CURHP%", String.valueOf(c.curHp()))
                .replace("%CURGM%", c.curGm())
                .replace("%CUREM%", c.curEm())
                .replace("%CURGROWTH%", c.curGrowth())
                .replace("%CURSTAR%", c.curStar());
    }

    /** 活动规则页内容（双端共用文案，全部实时读 config；不展示任何管理/配置向提示）。 */
    public static List<String> rulesLines() {
        int start = Reward.peakStart();
        int end = Reward.peakEnd();
        int times = todayLimit();
        int intervalMin = Reward.intervalMinutes();
        String interval = intervalMin >= 60 && intervalMin % 60 == 0
                ? (intervalMin / 60) + " 小时" : intervalMin + " 分钟";
        int base = Reward.hpBase();
        int cap = Reward.hpCap();
        int maxLevel = Reward.maxLevel();
        int gainNormal = Reward.gainNormal();
        int gainPeak = Reward.gainPeak();
        int decay = Reward.decayPerMissedDay();
        long growth = (long) Option.REWARD_VAL_GROWTH.getDouble();
        long star = (long) Option.REWARD_VAL_STAR.getDouble();
        int perDay = Math.max(1, gainPeak * times);
        int daysToMax = (maxLevel + perDay - 1) / perDay;

        List<String> out = new ArrayList<>();
        for (Message key : List.of(Message.GUI2_RULES_HOWTO, Message.GUI2_RULES_PEAK,
                Message.GUI2_RULES_OFFPEAK, Message.GUI2_RULES_LIMIT, Message.GUI2_RULES_LEVEL,
                Message.GUI2_RULES_HP, Message.GUI2_RULES_MULT, Message.GUI2_RULES_GROWTH,
                Message.GUI2_RULES_STAR, Message.GUI2_RULES_DECAY, Message.GUI2_RULES_TIP)) {
            String line = key.getString();
            if (line == null || line.isBlank()) {
                continue;
            }
            out.add(line
                    .replace("%START%", String.valueOf(start))
                    .replace("%END%", String.valueOf(end))
                    .replace("%TIMES%", String.valueOf(times))
                    .replace("%INTERVAL%", interval)
                    .replace("%MAXLEVEL%", String.valueOf(maxLevel))
                    .replace("%GAINNORMAL%", String.valueOf(gainNormal))
                    .replace("%GAINPEAK%", String.valueOf(gainPeak))
                    .replace("%HPBASE%", String.valueOf(base))
                    .replace("%HPCAP%", String.valueOf(cap))
                    .replace("%MAXMULT%", fmtMult(Reward.multiplierForLevel(maxLevel)))
                    .replace("%STEP%", trimNum(Reward.multiplierStep()))
                    .replace("%GROWTH%", String.valueOf(growth))
                    .replace("%STAR%", String.valueOf(star))
                    .replace("%DECAY%", String.valueOf(decay))
                    .replace("%DAYS%", String.valueOf(daysToMax))
                    .replace("%PAGEURL%", pageUrl()));
        }
        return out;
    }

    /** 数字去掉多余小数：0.1 → "0.1"，2.0 → "2"。 */
    private static String trimNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** 把倍率格式化成可读文本：2.5 → "x2.5"，1.25 → "x1.25"。 */
    private static String fmtMult(double v) {
        return "x" + (v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v));
    }

    /** 倍率查询值(<0 表示 MGactivity 未实现) → "x1.25" 或 "—"。 */
    private static String multStr(double v) {
        return v < 0 ? "—" : fmtMult(v);
    }

    /** 成长值查询值(<0) → 整数文本 或 "—"。 */
    private static String valStr(double v) {
        return v < 0 ? "—" : (v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v));
    }

    /** 星光点查询值(<0) → 整数文本 或 "—"。 */
    private static String starStr(long v) {
        return v < 0 ? "—" : String.valueOf(v);
    }
}
