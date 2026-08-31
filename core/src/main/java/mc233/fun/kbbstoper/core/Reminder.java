package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.sql.SQLer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 玩家进服/切换维度时的同步与提示。
 * 平台模块负责监听自己的事件，然后调用 {@link #onJoin(PlatformPlayer)} 或
 * {@link #onDimensionChange(PlatformPlayer)}。
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
                // 上线即主动向 MGactivity 刷新奖励数值状态(生命上限等)，不受 joinmessage 开关影响
                if (poster != null && poster.getBbsname() != null && !poster.getBbsname().isBlank()) {
                    Reward.refreshRewardState(poster, false);
                }
                if (!Option.BBS_JOINMESSAGE.getBoolean()) {
                    return;
                }
                boolean isbinded = true;
                boolean isposted = true;
                String datenow = new SimpleDateFormat("yyyy-M-dd").format(new Date());
                if (poster == null) {
                    isbinded = false;
                    isposted = false;
                } else if (!datenow.equals(poster.getRewardbefore())) {
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
                    Reward.refreshRewardState(poster, false);
                }
            } finally {
                Util.exitTask();
            }
        });
    }
}
