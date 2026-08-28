package mc233.fun.kbbstoper.core;

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

        player.sendMessage(Message.PREFIX.getString()
                + Message.REWARD.getString().replaceAll("%TIME%", crawler.Time.get(index)));
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
        final boolean vaultStar = Option.REWARD_VAULT_STAR.getBoolean();
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
                // 星光点优先走 MGactivity Java API(addStarlightPoints);
                // 接口不可用或配置关闭时回退 Vault 经济(depositEconomy)
                if (m != null && Option.REWARD_MG_STAR_ENABLE.getBoolean()) {
                    m.addStarlightPoints(n, (long) sp);
                } else if (vaultStar) {
                    KBBSToperCore.platform().depositEconomy(n, sp);
                }
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

    private static String yesterday() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -1);
        return DAY_FORMAT.format(c.getTime());
    }
}
