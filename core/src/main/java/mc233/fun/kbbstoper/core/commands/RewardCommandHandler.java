package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Crawler;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.Reward;
import mc233.fun.kbbstoper.core.TopState;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;

import java.util.HashSet;
import java.util.Set;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /bt reward，主动领取顶帖奖励。 */
public class RewardCommandHandler implements CommandHandler {

    private final Map<UUID, Long> cooldownRecord;

    public RewardCommandHandler(Map<UUID, Long> cooldownRecord) {
        this.cooldownRecord = cooldownRecord;
    }

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.isPlayer()) {
            sender.sendMessage(Message.PLAYERCMD.getString());
            sender.sendMessage(Message.HELP_HELP.getString());
            return;
        }
        if (!sender.hasPermission("bbstoper.reward")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPERMISSION.getString());
            return;
        }

        SQLer sql = SQLManager.getSQLer();
        PlatformPlayer player = sender.asPlayer();
        UUID uid = player.getUniqueId();
        Poster poster = sql.getPoster(uid.toString());
        if (poster == null) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOTBOUND.getString());
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_BINDING.getString());
            return;
        }

        if (!sender.hasPermission("bbstoper.bypassquerycooldown")) {
            long now = System.currentTimeMillis();
            long last = cooldownRecord.getOrDefault(uid, 0L);
            double cdSec = (Option.REWARD_MANUAL_COOLDOWN.getInt() * 1000 - (now - last)) / 1000.0;
            if (cdSec > 0) {
                sender.sendMessage(Message.PREFIX.getString()
                        + Message.MANUALCOOLDOWN.getString().replace("%COOLDOWN%", String.valueOf((int) cdSec)));
                return;
            }
            cooldownRecord.put(uid, now);
        }

        Crawler crawler = Crawler.fetch();
        if (!crawler.visible) {
            sender.sendMessage(Message.PREFIX.getString() + Message.PAGENOTVISIBLE.getString());
            return;
        }

        boolean issucceed = false;
        boolean isovertime = false;
        boolean iswaitamin = false;
        boolean havepost = false;
        List<String> temp = new ArrayList<>();

        // 已入库的顶帖记录只查一次, 循环里复用, 避免 N+1 查询
        Set<String> alreadyRewarded = new HashSet<>();
        for (TopState s : poster.getTopStates()) {
            alreadyRewarded.add(s.time);
        }

        for (int i = 0; i < crawler.ID.size(); i++) {
            if (!crawler.ID.get(i).equalsIgnoreCase(poster.getBbsname())) {
                continue;
            }
            // 同一分钟内的重复顶帖只算一次
            for (String t : temp) {
                if (t.equals(crawler.Time.get(i))) {
                    iswaitamin = true;
                    break;
                }
            }
            if (iswaitamin) {
                break;
            }
            if (!alreadyRewarded.contains(crawler.Time.get(i))) {
                havepost = true;
                String today = new SimpleDateFormat("yyyy-M-dd").format(new Date());
                if (!today.equals(poster.getRewardbefore())) {
                    Reward.applyDailyStreakBreakIfNeeded(poster);
                    poster.setRewardbefore(today);
                    poster.setRewardtime(0);
                }
                if (poster.getRewardtime() < Option.REWARD_TIMES.getInt()) {
                    Reward.RewardResult result = new Reward(player, crawler, i, poster).award();
                    if (result != null) {
                        int kind = result.peak ? 1 : 0;
                        sql.addTopState(poster.getBbsname(), crawler.Time.get(i), kind,
                                poster.getRewardtime() + 1, result.rewardText);
                        poster.setRewardtime(poster.getRewardtime() + 1);
                        issucceed = true;
                    }
                } else {
                    isovertime = true;
                    // 达每日上限: 记录一条"无奖励"的顶帖, 方便玩家在记录页看到上限说明
                    int kind = Reward.isPeakForTime(crawler.Time.get(i)) ? 1 : 0;
                    sql.addTopState(poster.getBbsname(), crawler.Time.get(i), kind,
                            poster.getRewardtime() + 1, null);
                }
                temp.add(crawler.Time.get(i));
            }
        }
        sql.updatePoster(poster);

        // 顶帖检测后主动向 MGactivity 刷新奖励数值状态
        Reward.refreshRewardState(poster, false);

        if (issucceed) {
            sender.sendMessage(Message.PREFIX.getString() + Message.REWARDGIVED.getString());
            KBBSToperCore.platform().getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("bbstoper.reward"))
                    .filter(p -> p.canSee(player))
                    .forEach(p -> p.sendMessage(
                            Message.BROADCAST.getString().replace("%PLAYER%", player.getName())));
        }
        if (isovertime) {
            sender.sendMessage(Message.PREFIX.getString() + Message.OVERTIME.getString()
                    .replace("%REWARDTIMES%", String.valueOf(Option.REWARD_TIMES.getInt())));
        }
        if (iswaitamin) {
            sender.sendMessage(Message.PREFIX.getString() + Message.WAITAMIN.getString());
        }
        if (!havepost) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPOST.getString());
        }
    }

    @Override
    public List<String> tabComplete(PlatformSender sender, String[] args) {
        return null;
    }
}
