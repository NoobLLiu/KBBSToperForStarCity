package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.sql.SQLer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 玩家进服提示。
 * 平台模块负责监听自己的加入事件，然后调用 {@link #onJoin(PlatformPlayer)}。
 */
public class Reminder {

    private static SQLer sql;

    public static void setSQLer(SQLer sql) {
        Reminder.sql = sql;
    }

    /** 玩家进服后调用；内部自行切到异步线程，调用方可在主线程调用。 */
    public static void onJoin(PlatformPlayer player) {
        if (!Option.BBS_JOINMESSAGE.getBoolean()) {
            return;
        }
        // 涉及数据库与网络 IO，必须异步
        KBBSToperCore.scheduler().runAsync(() -> {
            Util.enterTask();
            try {
                boolean isbinded = true;
                boolean isposted = true;
                Poster poster = sql.getPoster(player.getUniqueId().toString());
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
}
