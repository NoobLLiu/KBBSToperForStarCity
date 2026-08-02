package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformTask;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/** 自动奖励任务与关服等待。 */
public class Util {

    private static PlatformTask autorewardtask;

    /**
     * 正在运行的本插件异步任务计数。
     * 原实现记录 Bukkit 的 taskId，多平台下任务 ID 语义不同，
     * 这里改成计数器，语义等价且不依赖平台。
     */
    private static final AtomicInteger RUNNING = new AtomicInteger();

    /** 异步任务开始，与 {@link #exitTask()} 成对使用（放在 finally 里）。 */
    public static void enterTask() {
        RUNNING.incrementAndGet();
    }

    public static void exitTask() {
        RUNNING.decrementAndGet();
    }

    /** 启动自动奖励轮询；间隔为 0 时不启动。 */
    public static void startAutoReward() {
        if (autorewardtask != null && !autorewardtask.isCancelled()) {
            autorewardtask.cancel();
        }
        int period = Option.REWARD_AUTO.getInt() * 20;
        if (period > 0) {
            autorewardtask = KBBSToperCore.scheduler().runAsyncTimer(() -> {
                enterTask();
                try {
                    Crawler crawler = Crawler.fetch();
                    if (!crawler.visible) {
                        return;
                    }
                    crawler.activeReward();
                } finally {
                    exitTask();
                }
            }, 0, period);
        }
    }

    /** 阻塞直到本插件的异步任务全部结束，最多等 30 秒。 */
    public static void waitForAllTask() {
        int count = 0;
        try {
            while (RUNNING.get() > 0) {
                if (count > 30000) {
                    KBBSToperCore.logger().warning("等待异步任务结束超时，仍有 " + RUNNING.get() + " 个任务未完成。");
                    return;
                }
                Thread.sleep(100);
                count += 100;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 计算当前时刻顶帖能拿到的额外奖励文本。
     *
     * @return 没有额外奖励时返回 null
     */
    public static String getExtraReward(Crawler crawler) {
        boolean incentive = false;
        boolean offday = false;

        Calendar current = Calendar.getInstance();

        // 上一次顶帖时间，默认 1970-01-01
        Calendar lastpost = Calendar.getInstance();
        lastpost.setTime(new Date(0));

        if (!crawler.Time.isEmpty()) {
            String firstTime = crawler.Time.get(0);
            if (firstTime != null && !firstTime.isBlank()) {
                try {
                    lastpost.setTime(new SimpleDateFormat("yyyy-M-d HH:mm").parse(firstTime));
                } catch (ParseException e) {
                    KBBSToperCore.logger().warning("无法解析顶帖时间: \"" + firstTime + "\"");
                }
            }
        }

        if (Reward.canIncentiveReward(current, lastpost)) {
            incentive = true;
        }
        if (Reward.canOffDayReward(current)) {
            offday = true;
        }

        String extra = null;
        if (incentive) {
            // 两项都不是"额外奖励"且同时命中时，只算休息日奖励
            if (!(offday
                    && !Option.REWARD_INCENTIVEREWARD_EXTRA.getBoolean()
                    && !Option.REWARD_OFFDAYREWARD_EXTRA.getBoolean())) {
                extra = Message.GUI_INCENTIVEREWARDS.getString();
            }
        }
        if (offday) {
            String offText = Message.GUI_OFFDAYREWARDS.getString();
            extra = (extra == null) ? offText : (extra + "+" + offText);
        }
        return extra;
    }
}
