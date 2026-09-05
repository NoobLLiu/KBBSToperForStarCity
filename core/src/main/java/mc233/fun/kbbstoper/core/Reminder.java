package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.sql.SQLer;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家进服/退出/切换维度时的状态维护与提示。
 * 平台模块负责监听自己的事件，然后调用 {@link #onJoin(PlatformPlayer)}、
 * {@link #onQuit(PlatformPlayer)} 或 {@link #onDimensionChange(PlatformPlayer)}。
 *
 * <p>三个时机都会做一次"跨天维护"(断签扣级, 见 {@link Reward#maintainDailyState}),
 * 保证连签/等级状态即使玩家长期离线也与论坛顶帖时间对齐；
 * 每日第一次上线时额外在聊天栏展示顶帖状态概览。</p>
 */
public class Reminder {

    private static SQLer sql;

    public static void setSQLer(SQLer sql) {
        Reminder.sql = sql;
    }

    /** 玩家进服后调用；内部自行切到异步线程，调用方可在主线程调用。 */
    public static void onJoin(PlatformPlayer player) {
        // 涉及数据库与网络 IO，必须异步
        KBBSToperCore.scheduler().runAsync(() -> {
            Util.enterTask();
            try {
                Poster poster = sql.getPoster(player.getUniqueId().toString());
                if (poster != null && poster.getBbsname() != null && !poster.getBbsname().isBlank()) {
                    String today = Reward.todayString();
                    // 上线维护: 离线期间的断签扣级此刻结算(幂等)
                    if (Reward.maintainDailyState(poster, today)) {
                        sql.updatePoster(poster);
                    }
                    // 上线即主动向 MGactivity 刷新奖励数值状态(生命上限等)
                    Reward.refreshRewardState(poster, false);
                    // 每日第一次上线: 聊天栏展示顶帖状态概览
                    if (!today.equals(poster.getLastseeday())) {
                        sendJoinStatus(player, poster, today);
                        poster.setLastseeday(today);
                    }
                    // 记录本次上线时的等级, 供下次上线对比升降
                    poster.setLastlevel(Reward.clampLevel(poster.getRewardlevel()));
                    sql.updatePoster(poster);
                } else {
                    // 未绑定玩家：重置 MGactivity 状态为默认值，防止残留异常
                    Reward.resetToDefault(player.getName());
                }
                if (!Option.BBS_JOINMESSAGE.getBoolean()) {
                    return;
                }
                boolean isbinded = true;
                boolean isposted = true;
                // rewardbefore 现在记录"上次顶帖计入日", 离线顶帖也会刷新
                if (poster == null) {
                    isbinded = false;
                    isposted = false;
                } else if (!Reward.todayString().equals(poster.getRewardbefore())) {
                    isposted = false;
                }
                if (!isposted) {
                    List<String> list = new ArrayList<>(Message.INFO.getStringList());
                    Crawler crawler = Crawler.fetch();
                    String extra = Util.getExtraReward(crawler);
                    if (extra != null) {
                        list.add(Message.EXTRAINFO.getString().replaceAll("%EXTRA%", extra));
                    }
                    String url = "https://" + Option.WEBSITE.getString()
                            + "/thread-" + Option.BBS_URL.getString() + "-1-1.html";
                    for (String msg : list) {
                        player.sendMessage(Message.PREFIX.getString() + msg.replaceAll("%PAGE%", url));
                    }
                }
                if (!isbinded) {
                    player.sendMessage(Message.PREFIX.getString() + Message.HELP_BINDING.getString());
                }
            } finally {
                Util.exitTask();
            }
        });
    }

    /** 玩家退出后调用；维护其连签/等级状态并快照下线时等级。 */
    public static void onQuit(PlatformPlayer player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            Util.enterTask();
            try {
                Poster poster = sql.getPoster(player.getUniqueId().toString());
                if (poster == null || poster.getBbsname() == null || poster.getBbsname().isBlank()) {
                    return;
                }
                String today = Reward.todayString();
                if (Reward.maintainDailyState(poster, today)) {
                    sql.updatePoster(poster);
                }
                // 快照下线时的等级, 供下次上线对比升降
                poster.setLastlevel(Reward.clampLevel(poster.getRewardlevel()));
                sql.updatePoster(poster);
            } finally {
                Util.exitTask();
            }
        });
    }

    /**
     * 玩家切换维度后调用；读取最新数据并向 MGactivity 同步血量上限与成长倍率。
     * <p>维度切换时 Paper 会重置玩家血量为默认值，需要主动把持久化的上限刷回去。</p>
     */
    public static void onDimensionChange(PlatformPlayer player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            Util.enterTask();
            try {
                Poster poster = sql.getPoster(player.getUniqueId().toString());
                if (poster != null && poster.getBbsname() != null && !poster.getBbsname().isBlank()) {
                    String today = Reward.todayString();
                    if (Reward.maintainDailyState(poster, today)) {
                        sql.updatePoster(poster);
                    }
                    Reward.refreshRewardState(poster, false);
                } else {
                    Reward.resetToDefault(player.getName());
                }
            } finally {
                Util.exitTask();
            }
        });
    }

    /**
     * 每日第一次上线的顶帖状态概览：
     * <ul>
     *   <li>今日是否已顶帖(已计入次数/上限)</li>
     *   <li>连顶状态: 已连续 N 天 / 连顶已中断 N 天</li>
     *   <li>奖励等级相比上次上线的升降</li>
     * </ul>
     */
    private static void sendJoinStatus(PlatformPlayer player, Poster poster, String today) {
        // ---- 第一行: 今日顶帖状态 + 连顶状态 ----
        String line;
        if (today.equals(poster.getRewardbefore())) {
            line = Message.JOINSTATUS_POSTED.getString()
                    .replace("%COUNT%", String.valueOf(poster.getRewardtime()))
                    .replace("%LIMIT%", String.valueOf(GuiDataResolver.todayLimit()));
            line = line + " " + streakText(poster, today);
        } else {
            line = Message.JOINSTATUS_NOT_POSTED.getString();
            String last = poster.getLastpostday() == null ? "" : poster.getLastpostday();
            if (last.isEmpty()) {
                line = line + " " + Message.JOINSTATUS_NEVER_POSTED.getString();
            } else {
                long gap = Reward.daysBetween(last, today);
                if (gap <= 1) {
                    // 昨天顶过, 今日顶帖即可延续连顶
                    line = line + " " + Message.JOINSTATUS_STREAK_CONT.getString()
                            .replace("%STREAK%", String.valueOf(Math.max(1, poster.getStreak())));
                } else {
                    line = line + " " + Message.JOINSTATUS_STREAK_BROKEN.getString()
                            .replace("%DAYS%", String.valueOf(gap))
                            .replace("%LASTDAY%", last);
                }
            }
        }
        player.sendMessage(Message.PREFIX.getString() + line);

        // ---- 第二行: 相比上次上线的奖励等级变化 ----
        int cur = Reward.clampLevel(poster.getRewardlevel());
        int old = poster.getLastlevel();
        if (old < 0) {
            return; // 从未记录过上线等级(新绑定), 不展示
        }
        if (cur > old) {
            player.sendMessage(Message.PREFIX.getString() + Message.JOINSTATUS_LEVEL_UP.getString()
                    .replace("%OLD%", String.valueOf(old))
                    .replace("%NEW%", String.valueOf(cur))
                    .replace("%DIFF%", String.valueOf(cur - old)));
        } else if (cur < old) {
            player.sendMessage(Message.PREFIX.getString() + Message.JOINSTATUS_LEVEL_DOWN.getString()
                    .replace("%OLD%", String.valueOf(old))
                    .replace("%NEW%", String.valueOf(cur))
                    .replace("%DIFF%", String.valueOf(old - cur)));
        } else {
            player.sendMessage(Message.PREFIX.getString() + Message.JOINSTATUS_LEVEL_SAME.getString()
                    .replace("%LEVEL%", String.valueOf(cur)));
        }
    }

    /** 今日已顶帖时的连顶文案(今日顶过, streak 一定有效)。 */
    private static String streakText(Poster poster, String today) {
        return Message.JOINSTATUS_STREAK_CONT.getString()
                .replace("%STREAK%", String.valueOf(Math.max(1, poster.getStreak())));
    }
}
