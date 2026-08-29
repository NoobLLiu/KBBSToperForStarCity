package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.MGactivityApi;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.sql.SQLManager;
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
        List<String> states = poster.getTopStates();
        return states == null ? 0 : states.size();
    }

    /** 今日可领上限（首顶 + 额外）。 */
    public static int todayLimit() {
        return Math.max(0, Option.REWARD_DAILY_FIRST.getInt())
                + Math.max(0, Option.REWARD_DAILY_EXTRA.getInt());
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
        String firstState = claimed >= 1 ? "✓ 已领取" : "✗ 未领取";
        String extra1 = claimed >= 2 ? "✓ 已领取" : "✗ 未领取";
        String extra2 = claimed >= 3 ? "✓ 已领取" : "✗ 未领取";

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

        out.add(resolve(player, Message.GUI2_STATUS_BBSID.getString()));
        out.add(resolve(player, Message.GUI2_STATUS_POSTTIMES.getString()));
        out.add(statusReplace(Message.GUI2_STATUS_TODAY.getString(),
                claimed, limit, firstState, extra1, extra2, curHp, curGm, curEm, curGrowth, curStar));
        out.add(statusReplace(Message.GUI2_STATUS_FIRST.getString(),
                claimed, limit, firstState, extra1, extra2, curHp, curGm, curEm, curGrowth, curStar));
        out.add(statusReplace(Message.GUI2_STATUS_EXTRA1.getString(),
                claimed, limit, firstState, extra1, extra2, curHp, curGm, curEm, curGrowth, curStar));
        out.add(statusReplace(Message.GUI2_STATUS_EXTRA2.getString(),
                claimed, limit, firstState, extra1, extra2, curHp, curGm, curEm, curGrowth, curStar));
        out.add(statusReplace(Message.GUI2_STATUS_CURHP.getString(),
                claimed, limit, firstState, extra1, extra2, curHp, curGm, curEm, curGrowth, curStar));
        out.add(statusReplace(Message.GUI2_STATUS_CURGM.getString(),
                claimed, limit, firstState, extra1, extra2, curHp, curGm, curEm, curGrowth, curStar));
        out.add(statusReplace(Message.GUI2_STATUS_CUREM.getString(),
                claimed, limit, firstState, extra1, extra2, curHp, curGm, curEm, curGrowth, curStar));
        out.add(statusReplace(Message.GUI2_STATUS_CURGROWTH.getString(),
                claimed, limit, firstState, extra1, extra2, curHp, curGm, curEm, curGrowth, curStar));
        out.add(statusReplace(Message.GUI2_STATUS_CURSTAR.getString(),
                claimed, limit, firstState, extra1, extra2, curHp, curGm, curEm, curGrowth, curStar));
        out.add(resolve(player, Message.GUI2_STATUS_REWARDBEFORE.getString()));
        return out;
    }

    /** 把状态页专属占位符替换成实时计算值。 */
    private static String statusReplace(String text, int claimed, int limit,
                                         String firstState, String extra1, String extra2,
                                         int curHp, String curGm, String curEm,
                                         String curGrowth, String curStar) {
        return text
                .replace("%TODAY%", String.valueOf(claimed))
                .replace("%LIMIT%", String.valueOf(limit))
                .replace("%FIRSTSTATE%", firstState)
                .replace("%EXTRA1%", extra1)
                .replace("%EXTRA2%", extra2)
                .replace("%CURHP%", String.valueOf(curHp))
                .replace("%CURGM%", curGm)
                .replace("%CUREM%", curEm)
                .replace("%CURGROWTH%", curGrowth)
                .replace("%CURSTAR%", curStar);
    }

    /** 活动规则页内容（双端共用文案，全部实时读 config；不展示任何管理/配置向提示）。 */
    public static List<String> rulesLines() {
        List<String> out = new ArrayList<>();
        int start = Option.REWARD_PEAK_START.getInt();
        int end = Option.REWARD_PEAK_END.getInt();
        int first = Option.REWARD_DAILY_FIRST.getInt();
        int extra = Option.REWARD_DAILY_EXTRA.getInt();
        int intervalH = Math.max(1, Option.REWARD_INTERVAL.getInt() / 60);
        int cap = Option.REWARD_VAL_HP_CAP.getInt();
        int base = Option.REWARD_VAL_HP_BASE.getInt();
        int add30 = Math.max(0, cap - base);
        long growth = (long) Option.REWARD_VAL_GROWTH.getDouble();
        long addGrowth = (long) Option.REWARD_VAL_ADD_GROWTH.getDouble();
        long star = (long) Option.REWARD_VAL_STAR.getDouble();

        out.add(Message.GUI2_RULES_PEAK.getString()
                .replace("%START%", String.valueOf(start))
                .replace("%END%", String.valueOf(end))
                .replace("%HOURS%", String.valueOf(Option.REWARD_INACTIVE_HOURS.getInt())));
        out.add(Message.GUI2_RULES_OFFPEAK.getString()
                .replace("%START%", String.valueOf(start))
                .replace("%END%", String.valueOf(end)));
        out.add(Message.GUI2_RULES_LIMIT.getString()
                .replace("%FIRST%", String.valueOf(first))
                .replace("%EXTRA%", String.valueOf(extra))
                .replace("%INTERVALH%", String.valueOf(intervalH)));
        out.add(Message.GUI2_RULES_FIRST.getString()
                .replace("%FIRST%", String.valueOf(first))
                .replace("%GROWTHMULT%", fmtMult(Option.REWARD_VAL_GROWTH_MULT.getDouble()))
                .replace("%HP%", String.valueOf(Option.REWARD_VAL_HP_STEP.getInt()))
                .replace("%GROWTH%", String.valueOf(growth)));
        out.add(Message.GUI2_RULES_EXTRA.getString()
                .replace("%EXTRA%", String.valueOf(extra))
                .replace("%EXPMULT%", fmtMult(Option.REWARD_VAL_EXP_MULT.getDouble()))
                .replace("%GROWTH%", String.valueOf(growth)));
        out.add(Message.GUI2_RULES_ADDITIONAL.getString()
                .replace("%START%", String.valueOf(start))
                .replace("%END%", String.valueOf(end))
                .replace("%ADHP%", String.valueOf(Option.REWARD_VAL_ADD_HP_STEP.getInt()))
                .replace("%ADGROWTH%", String.valueOf(addGrowth))
                .replace("%STAR%", String.valueOf(star)));
        out.add(Message.GUI2_RULES_HPCAP.getString()
                .replace("%CAP%", String.valueOf(cap))
                .replace("%ADD30%", String.valueOf(add30))
                .replace("%STREAK%", String.valueOf(Option.REWARD_VAL_STREAK.getInt())));
        out.add(Message.GUI2_RULES_CUMULATIVE.getString());
        return out;
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
