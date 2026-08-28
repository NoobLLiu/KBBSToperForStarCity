package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;

import java.util.ArrayList;
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

    /** 我的状态页内容（双端共用文案）。 */
    public static List<String> statusLines(PlatformPlayer player) {
        List<String> out = new ArrayList<>();
        for (String line : List.of(
                Message.GUI2_STATUS_BBSID.getString(),
                Message.GUI2_STATUS_POSTTIMES.getString(),
                Message.GUI2_STATUS_MAXHP.getString(),
                Message.GUI2_STATUS_REWARDTIME.getString(),
                Message.GUI2_STATUS_REWARDBEFORE.getString())) {
            out.add(resolve(player, line));
        }
        return out;
    }

    /** 活动规则页内容（双端共用文案，纯静态）。 */
    public static List<String> rulesLines() {
        List<String> out = new ArrayList<>();
        out.add(Message.GUI2_RULES_PEAK.getString()
                .replace("%START%", String.valueOf(Option.REWARD_PEAK_START.getInt()))
                .replace("%END%", String.valueOf(Option.REWARD_PEAK_END.getInt())));
        out.add(Message.GUI2_RULES_LIMIT.getString()
                .replace("%FIRST%", String.valueOf(Option.REWARD_DAILY_FIRST.getInt()))
                .replace("%EXTRA%", String.valueOf(Option.REWARD_DAILY_EXTRA.getInt())));
        out.add(Message.GUI2_RULES_EXTRA.getString()
                .replace("%HOURS%", String.valueOf(Option.REWARD_INACTIVE_HOURS.getInt())));
        out.add(Message.GUI2_RULES_REWARD.getString()
                .replace("%GROWTH%", String.valueOf(Option.REWARD_VAL_GROWTH.getInt()))
                .replace("%GROWTHMULT%", fmtMult(Option.REWARD_VAL_GROWTH_MULT.getDouble()))
                .replace("%EXPMULT%", fmtMult(Option.REWARD_VAL_EXP_MULT.getDouble()))
                .replace("%HP%", String.valueOf(Option.REWARD_VAL_HP_STEP.getInt()))
                .replace("%CAP%", String.valueOf(Option.REWARD_VAL_HP_CAP.getInt()))
                .replace("%STAR%", String.valueOf(Option.REWARD_VAL_STAR.getInt())));
        return out;
    }

    /** 把倍率格式化成可读文本：2.5 → "x2.5"，1.25 → "x1.25"。 */
    private static String fmtMult(double v) {
        return "x" + (v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v));
    }
}
