package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.commands.DebugCommandHandler;
import mc233.fun.kbbstoper.core.platform.MGactivityApi;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 奖励判定与发放(StarCity 魔改版, "累计奖励等级"模型)。
 *
 * <p>规则要点(全部取自 config.yml, 无硬编码)：
 * <ul>
 *   <li>每次有效顶帖: 平峰 +{@code reward.level.gain-normal} 级, 高峰 +{@code reward.level.gain-peak} 级,
 *       封顶 {@code reward.level.max}。高峰/平峰按<b>论坛顶帖时间</b>判定, 与玩家领取时刻无关。</li>
 *   <li>生命上限 = {@code reward.values.hp-base} + 等级, 不超过 {@code reward.values.hp-hard-cap}。</li>
 *   <li>成长倍率 = 1 + 等级 × {@code reward.level.multiplier-step}。</li>
 *   <li>每次有效顶帖发放成长值 {@code reward.values.growth-per-reward}。</li>
 *   <li>高峰期顶帖额外发放星光点 {@code reward.values.star-points}(走 EssentialsX 经济)。</li>
 *   <li>断签: 每漏掉一整天扣 {@code reward.level.decay-per-missed-day} 级,
 *       以<b>顶帖日期</b>结算({@link #settlePost} / {@link #maintainDailyState}), 玩家离线同样生效。</li>
 * </ul>
 */
public class Reward {

    private final PlatformPlayer player;
    private final Crawler crawler;
    private final int index;
    private final Poster poster;

    private static final String DAY_PATTERN = "yyyy-M-dd";
    private static final String TIME_PATTERN = "yyyy-M-d HH:mm";
    private static final String TIME_PATTERN_SS = "yyyy-M-d HH:mm:ss";

    public Reward(PlatformPlayer player, Crawler crawler, int index, Poster poster) {
        this.player = player;
        this.crawler = crawler;
        this.index = index;
        this.poster = poster;
    }

    /** 兼容无 poster 的测试调用。 */
    public Reward(PlatformPlayer player, Crawler crawler, int index) {
        this(player, crawler, index, null);
    }

    // ================= 配置读取(带默认值, 旧配置缺键也不会读成 0) =================

    /** 奖励等级上限。 */
    public static int maxLevel() {
        return Math.max(1, Option.REWARD_LEVEL_MAX.getInt(20));
    }

    /** 平峰期每次有效顶帖提升的等级。 */
    public static int gainNormal() {
        return Math.max(0, Option.REWARD_LEVEL_GAIN_NORMAL.getInt(1));
    }

    /** 高峰期每次有效顶帖提升的等级。 */
    public static int gainPeak() {
        return Math.max(0, Option.REWARD_LEVEL_GAIN_PEAK.getInt(2));
    }

    /** 每级倍率增量(成长倍率 = 1 + 等级 × step)。 */
    public static double multiplierStep() {
        return Math.max(0, Option.REWARD_LEVEL_MULT_STEP.getDouble(0.1));
    }

    /** 断签时每漏掉一整天扣减的等级。 */
    public static int decayPerMissedDay() {
        return Math.max(0, Option.REWARD_LEVEL_DECAY.getInt(2));
    }

    /** 同一玩家两次有效顶帖的最小间隔(分钟), 0 = 不限制。 */
    public static int intervalMinutes() {
        return Math.max(0, Option.REWARD_INTERVAL.getInt(120));
    }

    /** 生命上限基准值。 */
    public static int hpBase() {
        return Option.REWARD_VAL_HP_BASE.getInt(20);
    }

    /** 生命上限硬上限。 */
    public static int hpCap() {
        return Option.REWARD_VAL_HP_CAP.getInt(50);
    }

    /** 高峰期时段起始小时(含)。 */
    public static int peakStart() {
        return Option.REWARD_PEAK_START.getInt(10);
    }

    /** 高峰期时段结束小时(不含)。 */
    public static int peakEnd() {
        return Option.REWARD_PEAK_END.getInt(22);
    }

    // ================= 日期/时间工具 =================

    /**
     * 每次调用都新建实例: SimpleDateFormat 非线程安全,
     * 插件的自动奖励/上下线维护都是异步任务, 不能共享静态格式化实例。
     */
    private static SimpleDateFormat dayFormat() {
        return new SimpleDateFormat(DAY_PATTERN);
    }

    /** 今天的日期字符串(yyyy-M-dd)。 */
    public static String todayString() {
        return dayFormat().format(new Date());
    }

    /**
     * 解析论坛顶帖时间字符串。
     *
     * <p>论坛的 span title 可能带秒("yyyy-M-d HH:mm:ss")也可能不带("yyyy-M-d HH:mm"),
     * 单一格式解析带秒的串会失败回退成 1970 年, 导致高峰期误判为平峰, 这里按两种格式依次尝试。</p>
     */
    public static Calendar parsePostTime(String timeStr) {
        Calendar calendar = Calendar.getInstance();
        if (timeStr == null || timeStr.isBlank()) {
            calendar.setTime(new Date(0));
            return calendar;
        }
        Date parsed = null;
        for (String pattern : new String[]{TIME_PATTERN_SS, TIME_PATTERN}) {
            try {
                parsed = new SimpleDateFormat(pattern).parse(timeStr);
                break;
            } catch (ParseException ignored) {
            }
        }
        if (parsed == null) {
            KBBSToperCore.logger().warning("无法解析顶帖时间：" + timeStr);
            calendar.setTime(new Date(0));
        } else {
            calendar.setTime(parsed);
        }
        return calendar;
    }

    /** 两个日期字符串(yyyy-M-dd)相差的天数(b - a), 解析失败返回 0。 */
    public static long daysBetween(String dayA, String dayB) {
        if (dayA == null || dayA.isBlank() || dayB == null || dayB.isBlank()) {
            return 0;
        }
        try {
            SimpleDateFormat format = dayFormat();
            Date a = format.parse(dayA);
            Date b = format.parse(dayB);
            return (b.getTime() - a.getTime()) / (24L * 60 * 60 * 1000);
        } catch (ParseException e) {
            return 0;
        }
    }

    /** 日期字符串(yyyy-M-dd)平移 N 天。 */
    private static String shiftDay(String day, int amount) {
        try {
            SimpleDateFormat format = dayFormat();
            Calendar c = Calendar.getInstance();
            c.setTime(format.parse(day));
            c.add(Calendar.DAY_OF_MONTH, amount);
            return format.format(c.getTime());
        } catch (ParseException e) {
            return day;
        }
    }

    /** 当前时间是否处于配置的高峰期 [start-hour, end-hour)。 */
    public static boolean isPeakHour(Calendar cal) {
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int start = peakStart();
        int end = peakEnd();
        if (start <= end) {
            return hour >= start && hour < end;
        }
        // 跨午夜的高峰段(本设计不需要, 保留兼容)
        return hour >= start || hour < end;
    }

    /** 此刻是否处于高峰期。 */
    public static boolean isPeakNow() {
        return isPeakHour(Calendar.getInstance());
    }

    /** 给定顶帖时间字符串是否处于高峰期（用于记录「类型」判断）。 */
    public static boolean isPeakForTime(String timeStr) {
        return isPeakHour(parsePostTime(timeStr));
    }

    /** 一次有效顶帖的发放结果，供调用方写入「我的顶帖记录」。 */
    public static final class RewardResult {
        public final int levelGain;
        public final int newLevel;
        public final int newMaxHp;
        public final double multiplier;
        public final double growthGranted;
        public final double starPoints;
        public final boolean peak;
        /** 记录页展示用的奖励文案。 */
        public final String rewardText;

        RewardResult(int levelGain, int newLevel, int newMaxHp, double multiplier,
                     double growthGranted, double starPoints, boolean peak, String rewardText) {
            this.levelGain = levelGain;
            this.newLevel = newLevel;
            this.newMaxHp = newMaxHp;
            this.multiplier = multiplier;
            this.growthGranted = growthGranted;
            this.starPoints = starPoints;
            this.peak = peak;
            this.rewardText = rewardText;
        }
    }

    /** 同一玩家距上次顶帖是否短于配置的间隔(分钟)。 */
    public boolean isIntervalTooShort(Calendar thispost, int index) {
        int limit = intervalMinutes();
        if (limit <= 0 || crawler == null) {
            return false;
        }
        long thispostMillis = thispost.getTimeInMillis();
        for (int x = index + 1; x < crawler.Time.size(); x++) {
            if (!crawler.ID.get(x).equalsIgnoreCase(crawler.ID.get(index))) {
                continue;
            }
            String timeStr = crawler.Time.get(x);
            if (timeStr == null || timeStr.isBlank()) {
                continue;
            }
            Calendar last = parsePostTime(timeStr);
            if (last.getTimeInMillis() <= 0) {
                // 解析失败回退成 1970 的脏数据, 跳过
                continue;
            }
            long minutes = (thispostMillis - last.getTimeInMillis()) / (1000 * 60);
            if (minutes < limit) {
                return true;
            }
            break;
        }
        return false;
    }

    // ================= 按顶帖日期结算连签/断签/配额 =================

    /**
     * 检测到一条新顶帖时, 以<b>顶帖日期</b>(而非玩家领取时刻)结算连签与每日配额。
     *
     * <ul>
     *   <li>与 lastpostday 同日: 连签不变, 不重置配额。</li>
     *   <li>恰好隔一天: streak + 1。</li>
     *   <li>隔了多天: 按漏掉的天数扣减奖励等级, streak 重新从 1 开始。</li>
     *   <li>顶帖日期进入新的一天: rewardbefore/rewardtime 按顶帖日期重置。</li>
     * </ul>
     *
     * @return poster 状态是否发生变化(调用方据此落库)
     */
    public static boolean settlePost(Poster poster, Calendar postTime) {
        if (poster == null || postTime == null) {
            return false;
        }
        String postDay = dayFormat().format(postTime.getTime());
        boolean changed = false;

        String last = poster.getLastpostday() == null ? "" : poster.getLastpostday();
        if (!postDay.equals(last)) {
            if (last.isEmpty()) {
                poster.setStreak(1);
                poster.setLastpostday(postDay);
                changed = true;
            } else {
                long gap = daysBetween(last, postDay);
                if (gap == 1) {
                    poster.setStreak(poster.getStreak() + 1);
                    poster.setLastpostday(postDay);
                    changed = true;
                } else if (gap >= 2) {
                    decayLevel(poster, (int) Math.min(Integer.MAX_VALUE, gap - 1));
                    poster.setStreak(1);
                    poster.setLastpostday(postDay);
                    changed = true;
                }
                // gap <= 0: 顶帖时间早于已记录日期(脏数据), 忽略
            }
        }

        // 每日计入配额同样以顶帖日期为准: 离线期间跨天顶帖也能正确重置
        if (!postDay.equals(poster.getRewardbefore())) {
            poster.setRewardbefore(postDay);
            poster.setRewardtime(0);
            changed = true;
        }
        return changed;
    }

    /**
     * 跨天维护(定时刷新/玩家上下线/切维度时调用): 长期未顶帖的玩家按漏掉天数扣级并断签。
     *
     * <p>幂等设计: 结算后把 lastpostday 推进到"昨天", 同一天内重复调用不会重复扣级;
     * 玩家此后再顶帖时 {@link #settlePost} 会把它当作"隔一天"重新开始计数。</p>
     *
     * @return poster 是否被修改(调用方据此落库)
     */
    public static boolean maintainDailyState(Poster poster, String today) {
        if (poster == null) {
            return false;
        }
        String last = poster.getLastpostday() == null ? "" : poster.getLastpostday();
        if (last.isEmpty()) {
            return false;
        }
        long gap = daysBetween(last, today);
        if (gap < 2) {
            return false;
        }
        decayLevel(poster, (int) Math.min(Integer.MAX_VALUE, gap - 1));
        poster.setStreak(0);
        poster.setLastpostday(shiftDay(today, -1));
        return true;
    }

    /** 定时全量维护: 所有绑定玩家(含离线)的断签扣级, 在线玩家同步刷新 MGactivity 数值。 */
    public static void maintainAllPosters() {
        SQLer sql = SQLManager.getSQLer();
        if (sql == null) {
            return;
        }
        List<Poster> all = sql.getAllPosters();
        if (all == null || all.isEmpty()) {
            return;
        }
        String today = todayString();
        for (Poster poster : all) {
            if (poster.getBbsname() == null || poster.getBbsname().isBlank()) {
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(poster.getUuid());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!maintainDailyState(poster, today)) {
                continue;
            }
            sql.updatePoster(poster);
            DebugCommandHandler.trace("维护: " + poster.getName() + " 断签结算, 奖励等级 → "
                    + clampLevel(poster.getRewardlevel()));
            PlatformPlayer online = KBBSToperCore.platform().getPlayer(uuid);
            if (online != null) {
                refreshRewardState(poster, false);
            }
        }
    }

    /** 按漏掉的整天数扣减奖励等级并同步生命上限。 */
    private static void decayLevel(Poster poster, int missedDays) {
        int decay = decayPerMissedDay();
        if (missedDays <= 0 || decay <= 0) {
            return;
        }
        int oldLevel = clampLevel(poster.getRewardlevel());
        int newLevel = (int) Math.max(0, oldLevel - Math.min(Integer.MAX_VALUE, (long) missedDays * decay));
        poster.setRewardlevel(newLevel);
        poster.setMaxhp(hpForLevel(newLevel));
    }

    // ================= 奖励发放 =================

    /** 发放奖励(累计奖励等级模型)，返回本次结果（失败返回 null）。玩家离线时同样生效, 仅跳过聊天提示。 */
    public RewardResult award() {
        return applyCumulativeAward();
    }

    /** Apply the cumulative reward-level rules for one valid post. 失败返回 null。 */
    public RewardResult applyCumulativeAward() {
        Calendar thispost = parsePostTime(crawler.Time.get(index));
        if (isIntervalTooShort(thispost, index)) {
            if (player != null) {
                player.sendMessage(Message.PREFIX.getString() + Message.INTERVALTOOSHORT.getString()
                        .replaceAll("%TIME%", crawler.Time.get(index))
                        .replaceAll("%INTERVAL%", String.valueOf(intervalMinutes())));
            }
            return null;
        }

        String name = player != null ? player.getName()
                : (poster != null ? poster.getName() : "");
        List<String> cmds = new ArrayList<>(replacePlayerValue(
                Option.REWARD_GROWTH_GRANT.getStringList(), name, Option.REWARD_VAL_GROWTH.getDouble()));

        // 高峰/平峰按论坛顶帖时间判定
        boolean isPeak = isPeakHour(thispost);
        int oldLevel = poster == null ? 0 : clampLevel(poster.getRewardlevel());
        int gain = isPeak ? gainPeak() : gainNormal();
        int newLevel = Math.min(maxLevel(), oldLevel + gain);
        int levelGain = newLevel - oldLevel;
        int newMaxHp = hpForLevel(newLevel);
        if (poster != null) {
            poster.setRewardlevel(newLevel);
            poster.setMaxhp(newMaxHp);
        }

        double multiplier = multiplierForLevel(newLevel);
        dispatchLevelState(name, cmds, KBBSToperCore.platform().getMGactivityApi(), multiplier, newMaxHp);
        double growthGranted = Option.REWARD_GROWTH_GRANT.getStringList().isEmpty()
                ? 0 : Option.REWARD_VAL_GROWTH.getDouble();

        // 星光点: 对接 EssentialsX 经济(优先 EssentialsX 金钱, 失败回退 /money give 命令), 按名字入账, 离线可发。
        // 等级制下仅在高峰期顶帖发放。
        double starPoints = isPeak ? Option.REWARD_VAL_STAR.getDouble() : 0;
        if (starPoints > 0) {
            final double sp = starPoints;
            KBBSToperCore.scheduler().runSync(() -> {
                DebugCommandHandler.trace("applyCumulativeAward(" + name + "): 发放星光点 " + sp
                        + " (EssentialsX → /money give 回退)");
                KBBSToperCore.platform().depositEconomy(name, sp);
            });
        }

        // 记录页展示用奖励文案
        String mult = formatValue(multiplier);
        StringBuilder rewardText = new StringBuilder();
        rewardText.append("等级+").append(levelGain)
                .append(" (").append(newLevel).append("/").append(maxLevel()).append(")")
                .append(", 生命上限").append(newMaxHp)
                .append(", 倍率x").append(mult)
                .append(", 成长+").append(formatValue(growthGranted));
        if (starPoints > 0) {
            rewardText.append(", 星光点+").append(formatValue(starPoints));
        }

        sendRewardSummary(crawler.Time.get(index), levelGain, newLevel, newMaxHp, growthGranted, starPoints);
        return new RewardResult(levelGain, newLevel, newMaxHp, multiplier, growthGranted, starPoints, isPeak, rewardText.toString());
    }

    /** 把等级钳制到 [0, max]。 */
    public static int clampLevel(int level) {
        return Math.max(0, Math.min(maxLevel(), level));
    }

    /** 指定等级对应的成长倍率。 */
    public static double multiplierForLevel(int level) {
        return 1.0 + clampLevel(level) * multiplierStep();
    }

    /** 指定等级对应的生命上限(已钳制到 [hp-base, hp-hard-cap])。 */
    public static int hpForLevel(int level) {
        int base = hpBase();
        return Math.max(base, Math.min(hpCap(), base + clampLevel(level)));
    }

    private void dispatchLevelState(String name, List<String> cmds, MGactivityApi mg,
                                    double multiplier, int maxHp) {
        final String resetGrowth = "mgactivity resetgrowthmultiplier " + name;
        KBBSToperCore.scheduler().runSync(() -> {
            // MGactivity 的 set* 为取最大值；先 reset 才能在断签后正确下降。
            KBBSToperCore.platform().dispatchConsoleCommand(resetGrowth);
            if (mg == null) {
                for (String cmd : cmds) {
                    if (cmd != null && !cmd.isBlank()) {
                        KBBSToperCore.platform().dispatchConsoleCommand(cmd);
                    }
                }
                KBBSToperCore.platform().dispatchConsoleCommand(buildMgCommand(
                        Option.REWARD_MG_GROWTH_CMD.getString(), name, multiplier));
                KBBSToperCore.platform().dispatchConsoleCommand(buildMgCommand(
                        Option.REWARD_MG_MAXHP_CMD.getString(), name, maxHp));
                return;
            }
            mg.setGrowthMultiplier(name, multiplier);
            mg.setMaxHp(name, maxHp);
            for (String cmd : cmds) {
                if (cmd != null && !cmd.isBlank()) {
                    KBBSToperCore.platform().dispatchConsoleCommand(cmd);
                }
            }
        });
    }

    /** 奖励完成提示：本次等级变化 + 当前生效数值。离线发奖时没有聊天对象, 跳过。 */
    private void sendRewardSummary(String time, int levelGain, int level, int newMaxHp,
                                   double growthGranted, double starPoints) {
        if (player == null) {
            return;
        }
        String mult = formatValue(multiplierForLevel(level));
        StringBuilder details = new StringBuilder();
        details.append("奖励等级 +").append(levelGain)
                .append(" (当前 ").append(level).append("/").append(maxLevel()).append(")")
                .append(", 生命上限 ").append(newMaxHp)
                .append(", 成长倍率 x").append(mult);
        if (growthGranted > 0) {
            details.append(" | 成长值 +").append(formatValue(growthGranted));
        }
        if (starPoints > 0) {
            details.append(" | 星光点 +").append(formatValue(starPoints));
        }
        player.sendMessage(Message.PREFIX.getString()
                + Message.REWARDSUMMARY.getString()
                        .replaceAll("%TIME%", time == null ? "-" : time)
                        .replaceAll("%DETAILS%", details.toString()));
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
        if (template == null || template.isBlank()) {
            return "";
        }
        return template.replaceAll("%PLAYER%", name).replaceAll("%VALUE%", formatValue(value));
    }

    /** 整数值输出成整数(100.0→100), 小数保留(1.2→1.2), 避免下发 "100.0" 给接口。 */
    private static String formatValue(double v) {
        if (!Double.isInfinite(v) && !Double.isNaN(v) && v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    /**
     * 手动模拟一次奖励效果, 用于验证 MGactivity/经济对接(不写数据库)。
     *
     * @param type NORMAL = 平峰期一次顶帖; PEAK = 高峰期一次顶帖; MAX = 满级效果
     */
    public void testAward(String type) {
        String name = player.getName();
        int current = 0;
        Poster p = GuiDataResolver.poster(player);
        if (p != null) {
            current = clampLevel(p.getRewardlevel());
        }

        int level;
        double star;
        switch (type == null ? "NORMAL" : type.toUpperCase()) {
            case "PEAK":
            case "INCENTIVE":
                level = Math.min(maxLevel(), current + gainPeak());
                star = Option.REWARD_VAL_STAR.getDouble();
                break;
            case "MAX":
            case "OFFDAY":
                level = maxLevel();
                star = Option.REWARD_VAL_STAR.getDouble();
                break;
            case "NORMAL":
                level = Math.min(maxLevel(), current + gainNormal());
                star = 0;
                break;
            default:
                return;
        }

        int maxHp = hpForLevel(level);
        double multiplier = multiplierForLevel(level);
        List<String> cmds = new ArrayList<>(replacePlayerValue(
                Option.REWARD_GROWTH_GRANT.getStringList(), name, Option.REWARD_VAL_GROWTH.getDouble()));
        dispatchLevelState(name, cmds, KBBSToperCore.platform().getMGactivityApi(), multiplier, maxHp);

        if (star > 0) {
            final double sp = star;
            KBBSToperCore.scheduler().runSync(() -> {
                DebugCommandHandler.trace("testAward(" + name + "): 发放星光点 " + sp);
                KBBSToperCore.platform().depositEconomy(name, sp);
            });
        }
        double growthGranted = Option.REWARD_GROWTH_GRANT.getStringList().isEmpty()
                ? 0 : Option.REWARD_VAL_GROWTH.getDouble();
        sendRewardSummary(dayFormat().format(new Date()), level - current, level, maxHp, growthGranted, star);
    }

    /**
     * 主动把玩家的奖励数值状态同步给 MGactivity（生命上限 + 倍率绝对值）。
     *
     * <p>用于"数据主动刷新"的三个时机：顶帖检测后 / 玩家上线时 / 管理员 debug 调整后。
     * 优先走 Java API，未注册时回退 {@code reward.mgactivity:} 控制台命令模板。</p>
     *
     * @param poster                玩家绑定记录(读其奖励等级)
     * @param resetDailyMultipliers 是否把倍率归位 1.0(仅 debug clear 用, 等级已被清零时等效)
     */
    public static void refreshRewardState(Poster poster, boolean resetDailyMultipliers) {
        if (poster == null) {
            return;
        }
        String name = poster.getName();
        if (name == null || name.isBlank()) {
            return;
        }
        int level = resetDailyMultipliers ? 0 : clampLevel(poster.getRewardlevel());
        int maxhp = hpForLevel(level);
        double multiplier = multiplierForLevel(level);

        MGactivityApi mg = KBBSToperCore.platform().getMGactivityApi();
        final String n = name;
        final int hp = maxhp;
        KBBSToperCore.scheduler().runSync(() -> {
            KBBSToperCore.platform().dispatchConsoleCommand("mgactivity resetgrowthmultiplier " + n);
            if (mg != null) {
                mg.setGrowthMultiplier(n, multiplier);
                mg.setMaxHp(n, hp);
            } else {
                for (String cmd : List.of(
                        buildMgCommand(Option.REWARD_MG_MAXHP_CMD.getString(), n, hp),
                        buildMgCommand(Option.REWARD_MG_GROWTH_CMD.getString(), n, multiplier))) {
                    if (cmd != null && !cmd.isBlank()) {
                        KBBSToperCore.platform().dispatchConsoleCommand(cmd);
                    }
                }
            }
        });
    }

    /**
     * 把玩家的 MGactivity 状态重置为默认值（生命上限 = hp-base，倍率 = 1.0）。
     * 用于未绑定玩家的异常状态清理，防止残留的血量上限/倍率导致游戏异常。
     *
     * @param playerName 玩家游戏名
     */
    public static void resetToDefault(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        int baseHp = hpBase();
        MGactivityApi mg = KBBSToperCore.platform().getMGactivityApi();
        final String n = playerName;
        KBBSToperCore.scheduler().runSync(() -> {
            KBBSToperCore.platform().dispatchConsoleCommand("mgactivity resetgrowthmultiplier " + n);
            if (mg != null) {
                mg.setGrowthMultiplier(n, 1.0);
                mg.setMaxHp(n, baseHp);
            } else {
                for (String cmd : List.of(
                        buildMgCommand(Option.REWARD_MG_MAXHP_CMD.getString(), n, baseHp),
                        buildMgCommand(Option.REWARD_MG_GROWTH_CMD.getString(), n, 1.0))) {
                    if (cmd != null && !cmd.isBlank()) {
                        KBBSToperCore.platform().dispatchConsoleCommand(cmd);
                    }
                }
            }
        });
    }
}
