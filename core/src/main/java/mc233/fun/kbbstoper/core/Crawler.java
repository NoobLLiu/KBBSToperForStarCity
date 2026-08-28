package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformOfflinePlayer;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.sql.SQLer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** 抓取论坛顶帖记录并驱动自动发奖。 */
public class Crawler {

    private static SQLer sql;

    public List<String> ID = new ArrayList<>();
    public List<String> Time = new ArrayList<>();

    /** 宣传帖是否可见。抓取失败或版面被隐藏时为 false。 */
    public boolean visible = true;

    // ---- 抓取结果 TTL 缓存 ----
    // 论坛页面没有必要高频抓取: 自动轮询 30s 一次、每次进服提醒、每条 reward/list 命令
    // 都 new Crawler() 的话, 玩家一多就是瞬间几十个请求打论坛, 会被限流甚至封 IP。
    // 缓存窗口内所有调用方共享同一份抓取结果, 窗口结束后下一次调用才重新抓。
    private static final long CACHE_TTL_MS = 8000;

    /** volatile 保证多线程下缓存引用的可见性。 */
    private static volatile Crawler cached;
    private static volatile long cachedAt;

    /**
     * 取抓取结果, 8 秒窗口内复用同一份, 不重复请求论坛。
     * 线程安全: 并发时只有一个线程真正抓取, 其余等待后读取缓存。
     */
    public static Crawler fetch() {
        Crawler now = cached;
        if (now != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return now;
        }
        synchronized (Crawler.class) {
            now = cached;
            if (now != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return now;
            }
            Crawler fresh = new Crawler();
            cached = fresh;
            cachedAt = System.currentTimeMillis();
            return fresh;
        }
    }

    /** 直接抓取一次, 不进缓存。 */
    public Crawler() {
        this(true);
    }

    /**
     * @param resolve true=正常抓取论坛页面并剔除过期记录; false=不抓取, 得到空上下文。
     *                调试用 simulate 走 false, 避免每次模拟都打一次论坛。
     */
    public Crawler(boolean resolve) {
        if (resolve) {
            resolveWebData();
            kickExpiredData();
        }
    }

    public void resolveWebData() {
        String url = "https://" + Option.WEBSITE.getString()
                + "/forum.php?mod=misc&action=viewthreadmod&tid=" + Option.BBS_URL.getString() + "&mobile=no";
        Document doc;
        try {
            if (Option.PROXY_ENABLE.getBoolean()) {
                doc = Jsoup.connect(url).proxy(Option.PROXY_IP.getString(), Option.PROXY_PORT.getInt()).get();
            } else {
                doc = Jsoup.connect(url).get();
            }
        } catch (IOException e) {
            if (Option.DEBUG.getBoolean()) {
                KBBSToperCore.logger().warning("抓取论坛页面失败", e);
            }
            KBBSToperCore.logger().warning(Message.FAILEDGETWEB.getString());
            return;
        }

        Elements listclass = doc.getElementsByClass("list");
        Element list;
        try {
            list = listclass.get(0);
        } catch (IndexOutOfBoundsException e) {
            this.visible = false;
            KBBSToperCore.logger().warning(Message.FAILEDRESOLVEWEB.getString());
            return;
        }

        Element listbody = list.getElementsByTag("tbody").get(0);
        for (Element rows : listbody.getElementsByTag("tr")) {
            Elements cells = rows.getElementsByTag("td");
            String action = cells.get(2).text();
            if (!(action.equals("提升(提升卡)") || action.equals("提升(服务器/交易代理提升卡)"))) {
                continue;
            }

            Element idcell = cells.get(0);
            String id = idcell.getElementsByTag("a").get(0).text();
            String time = extractTime(cells.get(1));

            ID.add(id);
            Time.add(time);
        }
    }

    private String extractTime(Element timecell) {
        Element timespan = timecell.getElementsByTag("span").first();
        if (timespan != null) {
            return timespan.attr("title");
        }
        return timecell.text();
    }

    /** 丢掉超过有效期的记录。 */
    public void kickExpiredData() {
        SimpleDateFormat sdfm = new SimpleDateFormat("yyyy-M-d HH:mm");
        Date now = new Date();
        long validMillis = Option.REWARD_PERIOD.getInt() * 24L * 60 * 60 * 1000;
        Date expiry = new Date(now.getTime() - validMillis);

        // 倒序遍历，删除不影响未遍历的下标
        for (int i = Time.size() - 1; i >= 0; i--) {
            String timeStr = Time.get(i);
            if (timeStr == null || timeStr.isBlank()) {
                continue;
            }

            try {
                Date date = sdfm.parse(timeStr);
                if (date.before(expiry)) {
                    Time.remove(i);
                    ID.remove(i);
                }
            } catch (ParseException e) {
                KBBSToperCore.logger().warning("无法解析时间: " + timeStr);
            }
        }
    }

    /** 遍历抓到的记录，给在线且已绑定的玩家自动发奖。 */
    public void activeReward() {
        for (int i = 0; i < ID.size(); i++) {
            String bbsname = ID.get(i);
            String time = Time.get(i);

            if (!sql.checkTopstate(bbsname, time)) {
                String uuid = sql.bbsNameCheck(bbsname);
                if (uuid == null) {
                    continue;
                }
                Poster poster = sql.getPoster(uuid);
                if (poster == null) {
                    continue;
                }
                processRewardForPlayer(uuid, poster, bbsname, time, i);
            }
        }
    }

    private void processRewardForPlayer(String uuid, Poster poster, String bbsname, String time, int index) {
        PlatformOfflinePlayer offline;
        try {
            offline = KBBSToperCore.platform().getOfflinePlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            KBBSToperCore.logger().warning("数据库里存在非法 UUID：" + uuid);
            return;
        }

        PlatformPlayer olplayer = offline.getOnlinePlayer();
        if (olplayer == null) {
            return;
        }
        if (!olplayer.hasPermission("bbstoper.reward")) {
            return;
        }

        String datenow = new SimpleDateFormat("yyyy-M-dd").format(new Date());
        if (!datenow.equals(poster.getRewardbefore())) {
            Reward.applyDailyStreakBreakIfNeeded(poster);
            poster.setRewardbefore(datenow);
            poster.setRewardtime(0);
        }

        if (poster.getRewardtime() >= Option.REWARD_TIMES.getInt()) {
            // 超出每日上限: 仅记录顶贴, 不再发奖
            sql.addTopState(bbsname, time);
            return;
        }

        new Reward(olplayer, this, index, poster).award();
        sql.addTopState(bbsname, time);
        poster.setRewardtime(poster.getRewardtime() + 1);
        sql.updatePoster(poster);

        // 顶帖检测后主动向 MGactivity 刷新奖励数值状态
        Reward.refreshRewardState(poster, false);

        broadcastReward(olplayer);
    }

    private void broadcastReward(PlatformPlayer rewarded) {
        for (PlatformPlayer p : KBBSToperCore.platform().getOnlinePlayers()) {
            if (!p.canSee(rewarded)) {
                continue;
            }
            if (!p.hasPermission("bbstoper.reward")) {
                continue;
            }
            p.sendMessage(Message.BROADCAST.getString().replace("%PLAYER%", rewarded.getName()));
        }
    }

    public static void setSQLer(SQLer sql) {
        Crawler.sql = sql;
    }

    /**
     * 调试用: 以空上下文模拟一次"检测到该玩家顶帖", 走完整发奖逻辑(不抓取网络)。
     * 模拟使用空上下文, 因此必然走"长期无人顶贴"分支, 附加奖励(HP+成长+星光点)会一并触发;
     * 每日次数配额与 2h 间隔仍按真实规则判定, 方便反复测试整条奖励链路。
     */
    public void simulateTopPost(PlatformPlayer player) {
        String uuid = player.getUniqueId().toString();
        Poster poster = sql.getPoster(uuid);
        if (poster == null) {
            player.sendMessage(Message.PREFIX.getString() + Message.NOTBOUND.getString());
            return;
        }
        String time = new SimpleDateFormat("yyyy-M-d HH:mm").format(new Date());
        ID.add(poster.getBbsname());
        Time.add(time);
        processRewardForPlayer(uuid, poster, poster.getBbsname(), time, 0);
    }
}
