package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.commands.DebugCommandHandler;
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
import java.util.Calendar;
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
        // 论坛时间可能带秒, 两种格式都尝试解析
        SimpleDateFormat withSec = new SimpleDateFormat("yyyy-M-d HH:mm:ss");
        SimpleDateFormat noSec = new SimpleDateFormat("yyyy-M-d HH:mm");
        Date now = new Date();
        long validMillis = Option.REWARD_PERIOD.getInt() * 24L * 60 * 60 * 1000;
        Date expiry = new Date(now.getTime() - validMillis);

        // 倒序遍历，删除不影响未遍历的下标
        for (int i = Time.size() - 1; i >= 0; i--) {
            String timeStr = Time.get(i);
            if (timeStr == null || timeStr.isBlank()) {
                continue;
            }

            Date date = null;
            try {
                date = withSec.parse(timeStr);
            } catch (ParseException ignored) {
            }
            if (date == null) {
                try {
                    date = noSec.parse(timeStr);
                } catch (ParseException e) {
                    KBBSToperCore.logger().warning("无法解析时间: " + timeStr);
                    continue;
                }
            }
            if (date.before(expiry)) {
                Time.remove(i);
                ID.remove(i);
            }
        }
    }

    /** 单条顶帖记录的处理结果, 供自动检测与手动领取指令分别决定后续提示。 */
    public enum ProcessResult {
        /** 正常发放了奖励。 */
        REWARDED,
        /** 已达每日上限, 仅记录了顶帖。 */
        RECORDED_CAPPED,
        /** 顶帖间隔过短, 本次无效。 */
        SKIPPED_INTERVAL,
        /** 其他原因跳过(无权限/数据异常等)。 */
        SKIPPED
    }

    /**
     * 遍历抓到的记录，给已绑定的玩家自动发奖。
     *
     * <p>倒序(从最早到最新)处理: 论坛页面是最新在前, 同一玩家积压多条记录时
     * 按顶帖时间先后依次结算, 保证连签天数与每日配额按真实顶帖顺序累计。
     * 玩家离线时同样发奖(MGactivity 命令与经济都按名字入账), 仅跳过聊天提示。</p>
     */
    public void activeReward() {
        for (int i = ID.size() - 1; i >= 0; i--) {
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

    /**
     * 处理一条未入库的顶帖记录: 按顶帖时间结算连签/断签/每日配额并发放奖励。
     * 玩家离线时照常结算与入账, 只是收不到聊天提示。
     */
    public ProcessResult processRewardForPlayer(String uuid, Poster poster, String bbsname, String time, int index) {
        PlatformOfflinePlayer offline;
        try {
            offline = KBBSToperCore.platform().getOfflinePlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            KBBSToperCore.logger().warning("数据库里存在非法 UUID：" + uuid);
            return ProcessResult.SKIPPED;
        }

        PlatformPlayer olplayer = offline.getOnlinePlayer();
        // 在线才校验权限; 离线玩家权限不可查, 直接放行(离线照样发奖)
        if (olplayer != null && !olplayer.hasPermission("bbstoper.reward")) {
            DebugCommandHandler.trace("检测: 玩家 " + uuid + " 缺少 bbstoper.reward 权限 → 跳过");
            return ProcessResult.SKIPPED;
        }
        DebugCommandHandler.trace("检测: bbsname=" + bbsname + " time=" + time
                + " 玩家 " + uuid + (olplayer == null ? " 不在线 → 离线发奖" : " 在线且有权限 → 进入发奖流程"));

        // 按论坛顶帖时间结算连签/断签与每日配额(与玩家领取/上线时刻无关)
        Calendar postTime = Reward.parsePostTime(time);
        boolean settled = Reward.settlePost(poster, postTime);
        if (settled) {
            sql.updatePoster(poster);
            DebugCommandHandler.trace("检测: 玩家 " + uuid + " 顶帖日期结算, 连续天数=" + poster.getStreak()
                    + ", 奖励等级=" + poster.getRewardlevel());
        }

        if (poster.getRewardtime() >= Option.REWARD_TIMES.getInt(3)) {
            // 超出每日上限: 仅记录顶贴, 不再发奖
            DebugCommandHandler.trace("检测: 玩家 " + uuid + " 今日已计入 "
                    + poster.getRewardtime() + "/" + Option.REWARD_TIMES.getInt(3) + " 达上限 → 仅记录顶贴");
            int kind = Reward.isPeakForTime(time) ? 1 : 0;
            sql.addTopState(bbsname, time, kind, poster.getRewardtime() + 1, null);
            sql.updatePoster(poster);
            return ProcessResult.RECORDED_CAPPED;
        }

        DebugCommandHandler.trace("检测: 玩家 " + uuid + " 今日已计入 "
                + poster.getRewardtime() + "/" + Option.REWARD_TIMES.getInt(3) + " → 调用 applyCumulativeAward() 计算奖励");
        Reward.RewardResult result = new Reward(olplayer, this, index, poster).applyCumulativeAward();
        if (result == null) {
            return ProcessResult.SKIPPED_INTERVAL;
        }
        int kind = result.peak ? 1 : 0;
        sql.addTopState(bbsname, time, kind, poster.getRewardtime() + 1, result.rewardText);
        poster.setRewardtime(poster.getRewardtime() + 1);
        sql.updatePoster(poster);

        // 顶帖检测后主动向 MGactivity 刷新奖励数值状态
        Reward.refreshRewardState(poster, false);

        broadcastReward(olplayer, poster.getName());
        return ProcessResult.REWARDED;
    }

    /** 全服广播某人顶帖领奖。rewarded 为 null 表示玩家当前离线(此时不做隐身可见性过滤)。 */
    private void broadcastReward(PlatformPlayer rewarded, String name) {
        for (PlatformPlayer p : KBBSToperCore.platform().getOnlinePlayers()) {
            if (rewarded != null && !p.canSee(rewarded)) {
                continue;
            }
            if (!p.hasPermission("bbstoper.reward")) {
                continue;
            }
            p.sendMessage(Message.BROADCAST.getString().replace("%PLAYER%", name));
        }
    }

    public static void setSQLer(SQLer sql) {
        Crawler.sql = sql;
    }

    /**
     * 调试用: 以空上下文模拟一次"检测到该玩家顶帖", 走完整发奖逻辑(不抓取网络)。
     * 连签/断签/每日配额均按真实规则结算, 方便反复测试整条奖励链路。
     */
    public void simulateTopPost(PlatformPlayer player) {
        String uuid = player.getUniqueId().toString();
        Poster poster = sql.getPoster(uuid);
        if (poster == null) {
            player.sendMessage(Message.PREFIX.getString() + Message.NOTBOUND.getString());
            return;
        }
        String time = new SimpleDateFormat("yyyy-M-d HH:mm:ss").format(new Date());
        ID.add(poster.getBbsname());
        Time.add(time);
        processRewardForPlayer(uuid, poster, poster.getBbsname(), time, 0);
    }
}
