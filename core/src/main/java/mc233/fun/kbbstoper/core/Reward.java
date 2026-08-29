package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.commands.DebugCommandHandler;
import mc233.fun.kbbstoper.core.platform.MGactivityApi;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/** 奖励判定与发放(StarCity 魔改版)。 */
public class Reward {

    private final PlatformPlayer player;
    private final Crawler crawler;
    private final int index;
    private final Poster poster;

    private static final Pattern UPCASE_PATTERN = Pattern.compile("^[A-Z]+$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{2}-\\d{2}$");
    private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat("yyyy-M-dd");
    private static final SimpleDateFormat BBS_FORMAT = new SimpleDateFormat("yyyy-M-d HH:mm");

    public Reward(PlatformPlayer player, Crawler crawler, int index, Poster poster) {
        this.player = player;
        this.crawler = crawler;
        this.index = index;
        this.poster = poster;
    }

    /** 兼容无 poster 的测试调用(按"首顶+附加"测试)。 */
    public Reward(PlatformPlayer player, Crawler crawler, int index) {
        this(player, crawler, index, null);
    }

    private static Calendar parseDateToCalendar(String dateStr, SimpleDateFormat dateFormat) {
        Calendar calendar = Calendar.getInstance();
        if (dateStr == null || dateStr.isBlank()) {
            calendar.setTime(new Date(0));
            return calendar;
        }
        try {
            calendar.setTime(dateFormat.parse(dateStr));
        } catch (ParseException e) {
            KBBSToperCore.logger().warning("无法解析时间：" + dateStr);
            calendar.setTime(new Date(0));
        }
        return calendar;
    }

    /** 判断能否发激励奖励(保留给 GUI 提示, 兼容旧逻辑)。 */
    public static boolean canIncentiveReward(Calendar current, Calendar before) {
        if (Option.REWARD_INCENTIVEREWARD_ENABLE.getBoolean()) {
            Calendar copyofcurrent = (Calendar) before.clone();
            copyofcurrent.add(Calendar.MINUTE, Option.REWARD_INCENTIVEREWARD_PERIOD.getInt());
            return copyofcurrent.before(current);
        }
        return false;
    }

    /** 判断当前时间是否落在配置的休息日上(保留给 GUI 提示)。 */
    public static boolean canOffDayReward(Calendar current) {
        if (Option.REWARD_OFFDAYREWARD_ENABLE.getBoolean()) {
            for (String day : Option.REWARD_OFFDAYREWARD_OFFDAYS.getStringList()) {
                if (UPCASE_PATTERN.matcher(day).matches()) {
                    int dayofweek = getDayOfWeekFromString(day);
                    if (dayofweek == current.get(Calendar.DAY_OF_WEEK)) {
                        return true;
                    }
                } else if (DATE_PATTERN.matcher(day).matches()) {
                    SimpleDateFormat offdayformat = new SimpleDateFormat("M-dd");
                    Calendar offdaycalendar = parseDateToCalendar(day, offdayformat);
                    if (current.get(Calendar.MONTH) == offdaycalendar.get(Calendar.MONTH)
                            && current.get(Calendar.DAY_OF_MONTH) == offdaycalendar.get(Calendar.DAY_OF_MONTH)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int getDayOfWeekFromString(String day) {
        try {
            return Calendar.class.getField(day).getInt(null);
        } catch (Exception e) {
            KBBSToperCore.logger().warning("无法识别的休息日配置：" + day);
            return -1;
        }
    }

    /** 同一玩家距上次顶帖是否短于配置的间隔(分钟)。 */
    public boolean isIntervalTooShort(Calendar thispost, int index) {
        Date thispostDate = thispost.getTime();
        for (int x = index + 1; x < crawler.Time.size(); x++) {
            if (!crawler.ID.get(x).equalsIgnoreCase(crawler.ID.get(index))) {
                continue;
            }
            String timeStr = crawler.Time.get(x);
            if (timeStr == null || timeStr.isBlank()) {
                continue;
            }
            try {
                Date lastDate = BBS_FORMAT.parse(timeStr);
                if (lastDate == null) {
                    continue;
                }
                long minutes = (thispostDate.getTime() - lastDate.getTime()) / (1000 * 60);
                if (minutes <= Option.REWARD_INTERVAL.getInt()) {
                    return true;
                } else {
                    break;
                }
            } catch (ParseException e) {
                KBBSToperCore.logger().warning("无法解析顶贴时间：" + timeStr + "（index=" + x + "）");
            }
        }
        return false;
    }

    /** 当前时间是否处于配置的高峰期 [start-hour, end-hour)。 */
    private static boolean isPeakHour(Calendar cal) {
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int start = Option.REWARD_PEAK_START.getInt();
        int end = Option.REWARD_PEAK_END.getInt();
        if (start <= end) {
            return hour >= start && hour < end;
        } else {
            // 跨午夜的高峰段(本设计不需要, 保留兼容)
            return hour >= start || hour < end;
        }
    }

    /** 距上一次全服顶贴是否已超过配置的小时数。 */
    private boolean isInactiveLongEnough(Calendar thispost) {
        long needMillis = Option.REWARD_INACTIVE_HOURS.getInt() * 60L * 60 * 1000;
        Calendar last = null;
        for (int x = index + 1; x < crawler.Time.size(); x++) {
            String timeStr = crawler.Time.get(x);
            if (timeStr == null || timeStr.isBlank()) {
                continue;
            }
            last = parseDateToCalendar(timeStr, BBS_FORMAT);
            break;
        }
        if (last == null) {
            // 没有更早的记录, 视为长期无人顶贴
            return true;
        }
        long gap = thispost.getTime().getTime() - last.getTime().getTime();
        return gap >= needMillis;
    }

    /** 发放奖励(按首顶/额外分档, 叠加附加奖励)。 */
    public void award() {
        Calendar thispost = parseDateToCalendar(crawler.Time.get(index), BBS_FORMAT);

        if (Option.REWARD_INTERVAL.getInt() > 0 && isIntervalTooShort(thispost, index)) {
            DebugCommandHandler.trace("award(" + player.getName() + "): 顶贴过于频繁(间隔 < "
                    + Option.REWARD_INTERVAL.getInt() + " 分钟) → 不发奖");
            player.sendMessage(Message.PREFIX.getString() + Message.INTERVALTOOSHORT.getString()
                    .replaceAll("%TIME%", crawler.Time.get(index))
                    .replaceAll("%INTERVAL%", String.valueOf(Option.REWARD_INTERVAL.getInt())));
            return;
        }

        // 档位: 基于当日已领次数(本次之前的计数)
        int rt = (poster != null) ? poster.getRewardtime() : 0;
        int firstLimit = Option.REWARD_DAILY_FIRST.getInt();
        int extraLimit = Option.REWARD_DAILY_EXTRA.getInt();
        boolean isFirst = rt < firstLimit;
        boolean isExtra = !isFirst && rt < (firstLimit + extraLimit);
        DebugCommandHandler.trace("award(" + player.getName() + "): 档位判定 rewardtime=" + rt
                + " → 首顶=" + isFirst + ", 额外=" + isExtra);

        boolean isPeak = isPeakHour(thispost);
        boolean inactive = isInactiveLongEnough(thispost);
        boolean additional = Option.REWARD_ADDITIONAL_ENABLE.getBoolean() && (isPeak || inactive);
        DebugCommandHandler.trace("award(" + player.getName() + "): 高峰=" + isPeak + ", 离线超12h=" + inactive
                + " → 附加奖励=" + additional);

        List<String> cmds = new ArrayList<>();
        String name = player.getName();

        // 基础: 成长值 +growth-per-reward (每次有效奖励) —— 始终走命令(成长值接口不在 MGactivity API 内)
        cmds.addAll(replacePlayerValue(Option.REWARD_GROWTH_GRANT.getStringList(), name,
                Option.REWARD_VAL_GROWTH.getDouble()));

        // HP 步进: 首顶 +hp-step; 附加奖励再 +additional-hp-step
        int hpStep = 0;
        if (isFirst) {
            hpStep += Option.REWARD_VAL_HP_STEP.getInt();
        }
        if (additional) {
            hpStep += Option.REWARD_VAL_ADD_HP_STEP.getInt();
            // 附加奖励再发一次成长值(+additional-growth) —— 走命令
            cmds.addAll(replacePlayerValue(Option.REWARD_GROWTH_GRANT.getStringList(), name,
                    Option.REWARD_VAL_ADD_GROWTH.getDouble()));
        }

        // 生命值上限累加并钳制
        int base = Option.REWARD_VAL_HP_BASE.getInt();
        int cap = Option.REWARD_VAL_HP_CAP.getInt();
        int current = (poster != null) ? poster.getMaxhp() : base;
        if (current < base) {
            current = base;
        }
        int newMaxHp = Math.min(cap, current + hpStep);
        if (poster != null) {
            poster.setMaxhp(newMaxHp);
        }
        DebugCommandHandler.trace("award(" + player.getName() + "): 生命上限 " + current + " +" + hpStep
                + " → " + newMaxHp + " (上限钳制 " + cap + "), 星光点 +" + (additional ? Option.REWARD_VAL_STAR.getInt() : 0));

        // 星光点(仅附加奖励发放)
        double starPoints = additional ? Option.REWARD_VAL_STAR.getInt() : 0;

        // MGactivity 效果(倍率/HP): 优先走 Java API, 否则回退控制台命令
        MGactivityApi mg = KBBSToperCore.platform().getMGactivityApi();
        MgEffect effect;
        if (mg != null) {
            effect = new MgEffect(isFirst, Option.REWARD_VAL_GROWTH_MULT.getDouble(),
                    isExtra, Option.REWARD_VAL_EXP_MULT.getDouble(), true, newMaxHp);
        } else {
            effect = new MgEffect(false, 0, false, 0, false, 0);
            if (isFirst) {
                cmds.add(buildMgCommand(Option.REWARD_MG_GROWTH_CMD.getString(), name,
                        Option.REWARD_VAL_GROWTH_MULT.getDouble()));
            }
            if (isExtra) {
                cmds.add(buildMgCommand(Option.REWARD_MG_EXP_CMD.getString(), name,
                        Option.REWARD_VAL_EXP_MULT.getDouble()));
            }
            cmds.add(buildMgCommand(Option.REWARD_MG_MAXHP_CMD.getString(), name, newMaxHp));
        }

        dispatch(name, cmds, starPoints, mg, effect);

        // 成长值本次实际发放量(仅当配置了成长值发放命令时)
        double growthGranted = 0;
        if (!Option.REWARD_GROWTH_GRANT.getStringList().isEmpty()) {
            growthGranted += Option.REWARD_VAL_GROWTH.getDouble();
            if (additional) {
                growthGranted += Option.REWARD_VAL_ADD_GROWTH.getDouble();
            }
        }
        sendRewardSummary(name, isFirst, isExtra, hpStep, newMaxHp, starPoints, growthGranted);
    }

    private static List<String> replacePlayerValue(List<String> list, String name, double value) {
        String v = formatValue(value);
        List<String> out = new ArrayList<>();
        for (String s : list) {
            out.add(s.replaceAll("%PLAYER%", name).replaceAll("%VALUE%", v));
        }
        return out;
    }

    private static String buildMgCommand(String template, String name, double value) {
        return template.replaceAll("%PLAYER%", name).replaceAll("%VALUE%", formatValue(value));
    }

    /** 整数值输出成整数(100.0→100), 小数保留(1.25→1.25), 避免下发 "100.0" 给接口。 */
    private static String formatValue(double v) {
        if (!Double.isInfinite(v) && !Double.isNaN(v) && v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    /** 手动测试指定类型的奖励命令。 */
    public void testAward(String type) {
        String name = player.getName();
        List<String> cmds = new ArrayList<>();
        double star = 0;
        double growthMult = Option.REWARD_VAL_GROWTH_MULT.getDouble();
        double expMult = Option.REWARD_VAL_EXP_MULT.getDouble();
        int maxHp;
        switch (type) {
            case "NORMAL":
                cmds.addAll(replacePlayerValue(Option.REWARD_GROWTH_GRANT.getStringList(), name,
                        Option.REWARD_VAL_GROWTH.getDouble()));
                maxHp = Math.min(Option.REWARD_VAL_HP_CAP.getInt(),
                        Option.REWARD_VAL_HP_BASE.getInt() + Option.REWARD_VAL_HP_STEP.getInt());
                break;
            case "INCENTIVE":
            case "ADDITIONAL":
                cmds.addAll(replacePlayerValue(Option.REWARD_GROWTH_GRANT.getStringList(), name,
                        Option.REWARD_VAL_ADD_GROWTH.getDouble()));
                maxHp = Math.min(Option.REWARD_VAL_HP_CAP.getInt(),
                        Option.REWARD_VAL_HP_BASE.getInt() + Option.REWARD_VAL_HP_STEP.getInt()
                                + Option.REWARD_VAL_ADD_HP_STEP.getInt());
                star = Option.REWARD_VAL_STAR.getInt();
                break;
            default:
                return;
        }
        MGactivityApi mg = KBBSToperCore.platform().getMGactivityApi();
        MgEffect effect;
        if (mg != null) {
            // 测试默认走首顶+HP(不含经验倍率, 与原命令版语义一致)
            effect = new MgEffect(true, growthMult, false, expMult, true, maxHp);
        } else {
            effect = new MgEffect(false, 0, false, 0, false, 0);
            cmds.add(buildMgCommand(Option.REWARD_MG_GROWTH_CMD.getString(), name, growthMult));
            cmds.add(buildMgCommand(Option.REWARD_MG_MAXHP_CMD.getString(), name, maxHp));
        }
        dispatch(name, cmds, star, mg, effect);

        // 测试奖励同样给出明细提示
        boolean additional = !"NORMAL".equals(type);
        int hpStep = additional
                ? Option.REWARD_VAL_HP_STEP.getInt() + Option.REWARD_VAL_ADD_HP_STEP.getInt()
                : Option.REWARD_VAL_HP_STEP.getInt();
        double growthGranted = 0;
        if (!Option.REWARD_GROWTH_GRANT.getStringList().isEmpty()) {
            growthGranted = additional ? Option.REWARD_VAL_ADD_GROWTH.getDouble()
                    : Option.REWARD_VAL_GROWTH.getDouble();
        }
        sendRewardSummary(name, true, false, hpStep, maxHp, star, growthGranted);
    }

    /**
     * 奖励完成提示：列出本次发放的各奖励明细与当前值。
     * 成长值/星光点/倍率的"当前值"经 MGactivity 可选查询接口读取，
     * 未实现（返回 -1）时自动省略，仅显示本次发放量。
     */
    private void sendRewardSummary(String name, boolean isFirst, boolean isExtra,
                                   int hpStep, int newMaxHp, double starPoints,
                                   double growthGranted) {
        MGactivityApi mg = KBBSToperCore.platform().getMGactivityApi();
        List<String> parts = new ArrayList<>();

        if (growthGranted > 0) {
            double cur = (mg != null) ? mg.getGrowthValue(name) : -1;
            String seg = "成长值 +" + formatValue(growthGranted);
            if (cur >= 0) {
                seg += " (当前 " + formatValue(cur + growthGranted) + ")";
            }
            parts.add(seg);
        }
        if (isFirst) {
            double set = Option.REWARD_VAL_GROWTH_MULT.getDouble();
            double cur = (mg != null) ? mg.getGrowthMultiplier(name) : -1;
            String seg = "成长倍率 x" + formatValue(set);
            if (cur > 0) {
                seg += " (当前 x" + formatValue(Math.max(cur, set)) + ")";
            }
            parts.add(seg);
        }
        if (isExtra) {
            double set = Option.REWARD_VAL_EXP_MULT.getDouble();
            double cur = (mg != null) ? mg.getExperienceMultiplier(name) : -1;
            String seg = "经验倍率 x" + formatValue(set);
            if (cur > 0) {
                seg += " (当前 x" + formatValue(Math.max(cur, set)) + ")";
            }
            parts.add(seg);
        }
        if (hpStep > 0) {
            parts.add("生命上限 +" + hpStep + " (当前 " + newMaxHp + ")");
        }
        if (starPoints > 0) {
            long cur = (mg != null) ? mg.getStarlightPoints(name) : -1;
            String seg = "星光点 +" + formatValue(starPoints);
            if (cur >= 0) {
                seg += " (当前 " + (cur + (long) starPoints) + ")";
            }
            parts.add(seg);
        }

        String details = parts.isEmpty() ? "无" : String.join(" | ", parts);
        player.sendMessage(Message.PREFIX.getString()
                + Message.REWARDSUMMARY.getString()
                        .replaceAll("%TIME%", crawler.Time.get(index))
                        .replaceAll("%DETAILS%", details));
    }

    /** MGactivity 效果集合(供 dispatch 在主线程判定走 API 还是命令回退)。 */
    private record MgEffect(boolean growth, double growthMult,
                             boolean exp, double expMult,
                             boolean maxHp, int maxHpVal) {
    }

    /**
     * 奖励回到主线程执行: 优先走 MGactivity Java API, 否则回退控制台命令;
     * 星光点优先走 MGactivity API(addStarlightPoints), 不可用时回退 Vault 经济。
     */
    private void dispatch(String name, List<String> cmds, double starPoints,
                          MGactivityApi mg, MgEffect effect) {
        final String n = name;
        final List<String> c = cmds;
        final double sp = starPoints;
        final MGactivityApi m = mg;
        final MgEffect e = effect;
        KBBSToperCore.scheduler().runSync(() -> {
            if (m != null) {
                DebugCommandHandler.trace("dispatch(" + n + "): 走 MGactivity Java API 下发倍率/HP");
                if (e.growth()) {
                    m.setGrowthMultiplier(n, e.growthMult());
                }
                if (e.exp()) {
                    m.setExperienceMultiplier(n, e.expMult());
                }
                if (e.maxHp()) {
                    m.setMaxHp(n, e.maxHpVal());
                }
            }
            // 成长值(数值)始终走控制台命令(growth-grant-commands), 与 MG API 是否可用无关:
            // MG API 存在时 cmds 仅含成长值命令; 不存在时 cmds 还含倍率/HP 回退命令。
            if (!c.isEmpty()) {
                DebugCommandHandler.trace("dispatch(" + n + "): 下发成长值命令(" + c.size() + " 条)");
                for (String cmd : c) {
                    if (cmd != null && !cmd.isBlank()) {
                        DebugCommandHandler.trace("dispatch(" + n + "): 控制台执行 -> " + cmd);
                        KBBSToperCore.platform().dispatchConsoleCommand(cmd);
                    }
                }
                // 回读校验: 命令下发是同步执行的, 立即回读可确认成长值已写入 MGactivity 数据存储
                // (即 /actistatus 读取的那份), 若回读不到说明命令未生效(名称/权限/实现问题)。
                if (m != null) {
                    double after = m.getGrowthValue(n);
                    if (after >= 0) {
                        DebugCommandHandler.trace("dispatch(" + n + "): 命令下发后回读成长值 = " + after
                                + " (应能在 /actistatus 看到)");
                    } else {
                        DebugCommandHandler.trace("dispatch(" + n + "): MGactivity 未覆写 getGrowthValue, 无法回读校验"
                                + " (不影响发放, 请服主自行核对 /actistatus)");
                    }
                }
            }
            if (sp > 0) {
                // 星光点直接对接 EssentialsX 经济(优先 EssentialsX 金钱, 失败回退 /money give 命令)。
                // 旧 MGactivity addStarlightPoints 通道已弃用(原实现无效, 不入账)。
                DebugCommandHandler.trace("dispatch(" + n + "): 发放星光点 " + sp + " (EssentialsX → /money give 回退)");
                KBBSToperCore.platform().depositEconomy(n, sp);
            }
        });
    }

    /**
     * 跨天且上次领奖不是昨天 → 判定为断签, 派发连签中断。
     * 必须在重置 rewardbefore 之前调用。
     */
    public static void applyDailyStreakBreakIfNeeded(Poster poster) {
        String today = DAY_FORMAT.format(new Date());
        String old = poster.getRewardbefore();
        if (old == null || old.isEmpty() || old.equals(today) || old.equals(yesterday())) {
            return;
        }
        int val = Option.REWARD_VAL_STREAK.getInt();
        String name = poster.getName();
        DebugCommandHandler.trace("断签检测(" + name + "): 上次领奖 " + old + " 非今天/昨天 → 扣连签中断 " + val);
        MGactivityApi mg = KBBSToperCore.platform().getMGactivityApi();
        final int v = val;
        if (mg != null) {
            KBBSToperCore.scheduler().runSync(() -> mg.addStreakBreak(name, v));
        } else {
            String cmd = Option.REWARD_MG_STREAK_CMD.getString()
                    .replaceAll("%PLAYER%", name)
                    .replaceAll("%VALUE%", String.valueOf(val));
            final String finalCmd = cmd;
            KBBSToperCore.scheduler().runSync(() ->
                    KBBSToperCore.platform().dispatchConsoleCommand(finalCmd));
        }
    }

    /**
     * 主动把玩家的奖励数值状态同步给 MGactivity（生命上限绝对值由 MGactivity 维护并应用到游戏内属性）。
     *
     * <p>用于"数据主动刷新"的三个时机：顶帖检测后 / 玩家上线时 / 管理员 debug 调整后。
     * 优先走 Java API，未注册时回退 {@code reward.mgactivity:} 控制台命令模板。
     * 生命上限完全由 MGactivity 负责落地（本插件不再直接写玩家属性）。
     * 若 {@code resetDailyMultipliers} 为 true（仅 debug clear 用），同时把当日成长/经验倍率归位 1.0。</p>
     *
     * @param poster                玩家绑定记录（读其 maxhp 作为目标值来源）
     * @param resetDailyMultipliers 是否把每日倍率归位 1.0
     */
    public static void refreshRewardState(Poster poster, boolean resetDailyMultipliers) {
        if (poster == null) {
            return;
        }
        String name = poster.getName();
        if (name == null || name.isBlank()) {
            return;
        }
        int base = Option.REWARD_VAL_HP_BASE.getInt();
        int maxhp = Math.max(poster.getMaxhp(), base);

        MGactivityApi mg = KBBSToperCore.platform().getMGactivityApi();
        final String n = name;
        final int hp = maxhp;
        KBBSToperCore.scheduler().runSync(() -> {
            // 生命上限由 MGactivity 维护: 优先走 Java API(setMaxHp 会写入并应用到游戏内属性),
            // 未注册时回退控制台命令模板(由 MGactivity 侧应用属性)。本插件不再自行写玩家属性。
            if (mg != null) {
                mg.setMaxHp(n, hp);
                if (resetDailyMultipliers) {
                    mg.setGrowthMultiplier(n, 1.0);
                    mg.setExperienceMultiplier(n, 1.0);
                }
                KBBSToperCore.logger().info("[奖励同步] " + n + " 生命上限 -> " + hp
                        + " (MGactivity API"
                        + (resetDailyMultipliers ? " + 倍率归位" : "") + ")");
            } else {
                String maxHpCmd = Option.REWARD_MG_MAXHP_CMD.getString()
                        .replaceAll("%PLAYER%", n).replaceAll("%VALUE%", String.valueOf(hp));
                if (maxHpCmd != null && !maxHpCmd.isBlank()) {
                    KBBSToperCore.platform().dispatchConsoleCommand(maxHpCmd);
                }
                if (resetDailyMultipliers) {
                    String g = Option.REWARD_MG_GROWTH_CMD.getString()
                            .replaceAll("%PLAYER%", n).replaceAll("%VALUE%", "1");
                    String e = Option.REWARD_MG_EXP_CMD.getString()
                            .replaceAll("%PLAYER%", n).replaceAll("%VALUE%", "1");
                    if (g != null && !g.isBlank()) {
                        KBBSToperCore.platform().dispatchConsoleCommand(g);
                    }
                    if (e != null && !e.isBlank()) {
                        KBBSToperCore.platform().dispatchConsoleCommand(e);
                    }
                }
                KBBSToperCore.logger().info("[奖励同步] " + n + " 生命上限 -> " + hp
                        + " (MGactivity 命令回退"
                        + (resetDailyMultipliers ? " + 倍率归位" : "") + ")");
            }
        });
    }

    private static String yesterday() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -1);
        return DAY_FORMAT.format(c.getTime());
    }
}
