package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformPlayer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/** 奖励判定与发放。 */
public class Reward {

    private final PlatformPlayer player;
    private final Crawler crawler;
    private final int index;

    private static final Pattern UPCASE_PATTERN = Pattern.compile("^[A-Z]+$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{2}-\\d{2}$");

    public Reward(PlatformPlayer player, Crawler crawler, int index) {
        this.player = player;
        this.crawler = crawler;
        this.index = index;
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

    /**
     * 判断能否发激励奖励。
     *
     * @param current 需要判断的时间
     * @param before  上一次顶帖的时间
     */
    public static boolean canIncentiveReward(Calendar current, Calendar before) {
        if (Option.REWARD_INCENTIVEREWARD_ENABLE.getBoolean()) {
            Calendar copyofcurrent = (Calendar) before.clone();
            copyofcurrent.add(Calendar.MINUTE, Option.REWARD_INCENTIVEREWARD_PERIOD.getInt());
            return copyofcurrent.before(current);
        }
        return false;
    }

    /** 判断当前时间是否落在配置的休息日上。 */
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

    /** "MONDAY" -> Calendar.MONDAY，无法识别时返回 -1。 */
    private static int getDayOfWeekFromString(String day) {
        try {
            return Calendar.class.getField(day).getInt(null);
        } catch (Exception e) {
            KBBSToperCore.logger().warning("无法识别的休息日配置：" + day);
            return -1;
        }
    }

    /** 同一玩家距上次顶帖是否短于配置的间隔。 */
    public boolean isIntervalTooShort(Calendar thispost, int index) {
        SimpleDateFormat bbsformat = new SimpleDateFormat("yyyy-M-d HH:mm");
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
                Date lastDate = bbsformat.parse(timeStr);
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

    /** 发放奖励。 */
    public void award() {
        List<String> cmds = new ArrayList<>();
        boolean incentive = false;
        boolean offday = false;
        boolean normal = true;
        SimpleDateFormat bbsformat = new SimpleDateFormat("yyyy-M-d HH:mm");
        Calendar thispost = parseDateToCalendar(crawler.Time.get(index), bbsformat);

        if (Option.REWARD_INTERVAL.getInt() > 0 && isIntervalTooShort(thispost, index)) {
            player.sendMessage(Message.PREFIX.getString() + Message.INTERVALTOOSHORT.getString()
                    .replaceAll("%TIME%", crawler.Time.get(index))
                    .replaceAll("%INTERVAL%", String.valueOf(Option.REWARD_INTERVAL.getInt())));
            return;
        }

        Calendar lastpost = Calendar.getInstance();
        if (crawler.Time.size() > index + 1) {
            lastpost = parseDateToCalendar(crawler.Time.get(index + 1), bbsformat);
        } else {
            lastpost.setTime(new Date(0));
        }

        if (canIncentiveReward(thispost, lastpost)) {
            incentive = true;
        }
        if (canOffDayReward(thispost)) {
            offday = true;
        }

        String extra = null;
        if (incentive) {
            if (!(offday && !Option.REWARD_INCENTIVEREWARD_EXTRA.getBoolean()
                    && !Option.REWARD_OFFDAYREWARD_EXTRA.getBoolean())) {
                cmds.addAll(Option.REWARD_INCENTIVEREWARD_COMMANDS.getStringList());
                extra = Message.GUI_INCENTIVEREWARDS.getString();
            }
            if (!Option.REWARD_INCENTIVEREWARD_EXTRA.getBoolean()) {
                normal = false;
            }
        }
        if (offday) {
            cmds.addAll(Option.REWARD_OFFDAYREWARD_COMMANDS.getStringList());
            if (extra == null) {
                extra = Message.GUI_OFFDAYREWARDS.getString();
            } else {
                extra = extra + "+" + Message.GUI_OFFDAYREWARDS.getString();
            }
            if (!Option.REWARD_OFFDAYREWARD_EXTRA.getBoolean()) {
                normal = false;
            }
        }
        if (normal) {
            cmds.addAll(Option.REWARD_COMMANDS.getStringList());
        }

        dispatch(cmds);

        player.sendMessage(Message.PREFIX.getString()
                + Message.REWARD.getString().replaceAll("%TIME%", crawler.Time.get(index)));
        if (extra != null) {
            player.sendMessage(Message.PREFIX.getString()
                    + Message.EXTRAREWARD.getString().replaceAll("%EXTRA%", extra));
        }
    }

    /** 手动测试指定类型的奖励命令。 */
    public void testAward(String type) {
        List<String> cmds = new ArrayList<>();
        switch (type) {
            case "NORMAL":
                cmds.addAll(Option.REWARD_COMMANDS.getStringList());
                break;
            case "INCENTIVE":
                cmds.addAll(Option.REWARD_INCENTIVEREWARD_COMMANDS.getStringList());
                break;
            case "OFFDAY":
                cmds.addAll(Option.REWARD_OFFDAYREWARD_COMMANDS.getStringList());
                break;
            default:
                return;
        }
        dispatch(cmds);
    }

    /** 奖励命令必须回到主线程由控制台执行。 */
    private void dispatch(List<String> cmds) {
        final String name = player.getName();
        KBBSToperCore.scheduler().runSync(() -> {
            for (String cmd : cmds) {
                KBBSToperCore.platform().dispatchConsoleCommand(cmd.replaceAll("%PLAYER%", name));
            }
        });
    }
}
