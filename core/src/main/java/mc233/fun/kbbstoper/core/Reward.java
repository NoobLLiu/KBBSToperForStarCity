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

    public static final int MAX_REWARD_LEVEL = 20;
    public static final int DAILY_REWARD_LIMIT = 3;
    public static final int MIN_POST_INTERVAL_MINUTES = 120;
    public static final int NORMAL_LEVEL_GAIN = 1;
    public static final int PEAK_LEVEL_GAIN = 2;
    public static final int DAILY_LEVEL_DECAY = 2;

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
                if (minutes < MIN_POST_INTERVAL_MINUTES) {
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
        applyCumulativeAward();
    }

    private void legacyAward() {
        Calendar thispost = parseDateToCalendar(crawler.Time.get(index), BBS_FORMAT);

        if (Option.REWARD_INTERVAL.getInt() > 0 && isIntervalTooShort(thispost, index)) {
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

        boolean isPeak = isPeakHour(thispost);
        boolean inactive = isInactiveLongEnough(thispost);
        boolean additional = Option.REWARD_ADDITIONAL_ENABLE.getBoolean() && (isPeak || inactive);

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

    /** Apply the cumulative reward-level rules for one valid post. */
    public boolean applyCumulativeAward() {
        Calendar thispost = parseDateToCalendar(crawler.Time.get(index), BBS_FORMAT);
        if (isIntervalTooShort(thispost, index)) {
            player.sendMessage(Message.PREFIX.getString() + Message.INTERVALTOOSHORT.getString()
                    .replaceAll("%TIME%", crawler.Time.get(index))
                    .replaceAll("%INTERVAL%", String.valueOf(MIN_POST_INTERVAL_MINUTES)));
            return false;
        }

        String name = player.getName();
        List<String> cmds = new ArrayList<>();
        cmds.addAll(replacePlayerValue(Option.REWARD_GROWTH_GRANT.getStringList(), name,
                Option.REWARD_VAL_GROWTH.getDouble()));

        boolean isPeak = isPeakHour(thispost);
        int oldLevel = poster == null ? 0 : clampLevel(poster.getRewardlevel());
        int gain = isPeak ? PEAK_LEVEL_GAIN : NORMAL_LEVEL_GAIN;
        int newLevel = Math.min(MAX_REWARD_LEVEL, oldLevel + gain);
        int levelGain = newLevel - oldLevel;
        int base = Option.REWARD_VAL_HP_BASE.getInt();
        int newMaxHp = Math.min(Option.REWARD_VAL_HP_CAP.getInt(), base + newLevel);
        if (poster != null) {
            poster.setRewardlevel(newLevel);
            poster.setMaxhp(newMaxHp);
        }

        double multiplier = multiplierForLevel(newLevel);
        dispatchLevelState(name, cmds, KBBSToperCore.platform().getMGactivityApi(), multiplier, newMaxHp);
        double growthGranted = Option.REWARD_GROWTH_GRANT.getStringList().isEmpty()
                ? 0 : Option.REWARD_VAL_GROWTH.getDouble();

        // 星光点: 沿用我方(A6)对接 EssentialsX 经济(优先 EssentialsX 金钱, 失败回退 /money give 命令)。
        // 等级制下仅在高峰顶贴发放(对应原设计稿"附加奖励"触发条件之一: 高峰期)。
        double starPoints = isPeak ? Option.REWARD_VAL_STAR.getDouble() : 0;
        if (starPoints > 0) {
            final double sp = starPoints;
            KBBSToperCore.scheduler().runSync(() -> {
                DebugCommandHandler.trace("applyCumulativeAward(" + name + "): 发放星光点 " + sp + " (EssentialsX → /money give 回退)");
                KBBSToperCore.platform().depositEconomy(name, sp);
            });
        }

        sendRewardSummary(name, levelGain, newLevel, newMaxHp, growthGranted);
        return true;
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_REWARD_LEVEL, level));
    }

    public static double multiplierForLevel(int level) {
        return 1.0 + clampLevel(level) * 0.1;
    }

    private void dispatchLevelState(String name, List<String> cmds, MGactivityApi mg,
                                    double multiplier, int maxHp) {
        final String resetGrowth = "mgactivity resetgrowthmultiplier " + name;
        final String resetExperience = "mgactivity resetexperiencemultiplier " + name;
        KBBSToperCore.scheduler().runSync(() -> {
            if (mg == null) {
                KBBSToperCore.platform().dispatchConsoleCommand(resetGrowth);
                KBBSToperCore.platform().dispatchConsoleCommand(resetExperience);
                for (String cmd : cmds) {
                    if (cmd != null && !cmd.isBlank()) {
                        KBBSToperCore.platform().dispatchConsoleCommand(cmd);
                    }
                }
                KBBSToperCore.platform().dispatchConsoleCommand(buildMgCommand(
                        Option.REWARD_MG_GROWTH_CMD.getString(), name, multiplier));
                KBBSToperCore.platform().dispatchConsoleCommand(buildMgCommand(
                        Option.REWARD_MG_EXP_CMD.getString(), name, multiplier));
                KBBSToperCore.platform().dispatchConsoleCommand(buildMgCommand(
                        Option.REWARD_MG_MAXHP_CMD.getString(), name, maxHp));
                return;
            }
            // MGactivity 的 set* 为取最大值；先 reset 才能在断签后正确下降。
            KBBSToperCore.platform().dispatchConsoleCommand(resetGrowth);
            KBBSToperCore.platform().dispatchConsoleCommand(resetExperience);
            mg.setGrowthMultiplier(name, multiplier);
            mg.setExperienceMultiplier(name, multiplier);
            mg.setMaxHp(name, maxHp);
            for (String cmd : cmds) {
                if (cmd != null && !cmd.isBlank()) {
                    KBBSToperCore.platform().dispatchConsoleCommand(cmd);
                }
            }
        });
    }

    private void sendRewardSummary(String name, int levelGain, int level,
                                   int newMaxHp, double growthGranted) {
        String details = "奖励等级 +" + levelGain + " (当前 " + level + "/" + MAX_REWARD_LEVEL
                + "), 生命上限 " + newMaxHp + ", 成长倍率 x" + formatValue(multiplierForLevel(level))
                + ", 经验倍率 x" + formatValue(multiplierForLevel(level));
        if (growthGranted > 0) {
            details += " | 成长值 +" + formatValue(growthGranted);
        }
        player.sendMessage(Message.PREFIX.getString()
                + Message.REWARDSUMMARY.getString()
                        .replaceAll("%TIME%", crawler.Time.get(index))
                        .replaceAll("%DETAILS%", details));
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
     * 星光点对接 EssentialsX 经济(优先 EssentialsX 金钱, 不可用时回退 /money give 命令) —— 沿用我方 A6。
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
                if (e.growth()) {
                    m.setGrowthMultiplier(n, e.growthMult());
                }
                if (e.exp()) {
                    m.setExperienceMultiplier(n, e.expMult());
                }
                if (e.maxHp()) {
                    m.setMaxHp(n, e.maxHpVal());
                }
            } else {
                for (String cmd : c) {
                    if (cmd != null && !cmd.isBlank()) {
                        KBBSToperCore.platform().dispatchConsoleCommand(cmd);
                    }
                }
            }
            if (sp > 0) {
                // 星光点对接 EssentialsX 经济(优先 EssentialsX 金钱, 失败回退 /money give 命令) —— 沿用我方 A6
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
        long missedDays = missedDays(old, today);
        if (missedDays <= 0) {
            return;
        }
        int oldLevel = clampLevel(poster.getRewardlevel());
        int newLevel = Math.max(0, oldLevel - (int) Math.min(Integer.MAX_VALUE,
                missedDays * DAILY_LEVEL_DECAY));
        poster.setRewardlevel(newLevel);
        poster.setMaxhp(Math.min(Option.REWARD_VAL_HP_CAP.getInt(),
                Option.REWARD_VAL_HP_BASE.getInt() + newLevel));
        if (newLevel != oldLevel) {
            refreshRewardState(poster, false);
        }
    }

    private static long missedDays(String old, String today) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-M-d");
            Date last = format.parse(old);
            Date current = format.parse(today);
            long days = (current.getTime() - last.getTime()) / (24L * 60 * 60 * 1000);
            return Math.max(0, days - 1);
        } catch (ParseException e) {
            return 0;
        }
    }

    /**
     * 主动把玩家的奖励数值状态同步给 MGactivity（生命上限绝对值）。
     *
     * <p>用于"数据主动刷新"的三个时机：顶帖检测后 / 玩家上线时 / 管理员 debug 调整后。
     * 优先走 Java API，未注册时回退 {@code reward.mgactivity:} 控制台命令模板。
     * 若 {@code resetDailyMultipliers} 为 true（仅 debug clear 用），同时把当日成长/经验倍率归位 1.0。</p>
     *
     * @param poster                玩家绑定记录（读其 maxhp）
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
        int level = clampLevel(poster.getRewardlevel());
        int base = Option.REWARD_VAL_HP_BASE.getInt();
        int maxhp = Math.min(Option.REWARD_VAL_HP_CAP.getInt(), base + level);
        double multiplier = multiplierForLevel(level);

        MGactivityApi mg = KBBSToperCore.platform().getMGactivityApi();
        final String n = name;
        final int hp = maxhp;
        KBBSToperCore.scheduler().runSync(() -> {
            KBBSToperCore.platform().dispatchConsoleCommand("mgactivity resetgrowthmultiplier " + n);
            KBBSToperCore.platform().dispatchConsoleCommand("mgactivity resetexperiencemultiplier " + n);
            if (mg != null) {
                mg.setGrowthMultiplier(n, multiplier);
                mg.setExperienceMultiplier(n, multiplier);
                mg.setMaxHp(n, hp);
            } else {
                String maxHpCmd = Option.REWARD_MG_MAXHP_CMD.getString()
                        .replaceAll("%PLAYER%", n).replaceAll("%VALUE%", String.valueOf(hp));
                String growthCmd = Option.REWARD_MG_GROWTH_CMD.getString()
                        .replaceAll("%PLAYER%", n).replaceAll("%VALUE%", formatValue(multiplier));
                String expCmd = Option.REWARD_MG_EXP_CMD.getString()
                        .replaceAll("%PLAYER%", n).replaceAll("%VALUE%", formatValue(multiplier));
                for (String cmd : List.of(maxHpCmd, growthCmd, expCmd)) {
                    if (cmd != null && !cmd.isBlank()) {
                        KBBSToperCore.platform().dispatchConsoleCommand(cmd);
                    }
                }
            }
        });
    }

    private static String yesterday() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -1);
        return DAY_FORMAT.format(c.getTime());
    }
}
