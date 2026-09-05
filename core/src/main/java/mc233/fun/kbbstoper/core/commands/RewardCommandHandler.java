package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Crawler;
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

import java.util.ArrayList;
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

        // 倒序(从最早到最新)处理, 与自动检测一致: 连签/配额按真实顶帖时间顺序结算
        for (int i = crawler.ID.size() - 1; i >= 0; i--) {
            if (!crawler.ID.get(i).equalsIgnoreCase(poster.getBbsname())) {
                continue;
            }
            String time = crawler.Time.get(i);
            // 同一分钟内的重复顶帖只算一次
            if (temp.contains(time)) {
                iswaitamin = true;
                break;
            }
            if (!alreadyRewarded.contains(time)) {
                havepost = true;
                Crawler.ProcessResult r = crawler.processRewardForPlayer(
                        uid.toString(), poster, poster.getBbsname(), time, i);
                switch (r) {
                    case REWARDED:
                        issucceed = true;
                        break;
                    case RECORDED_CAPPED:
                        isovertime = true;
                        break;
                    case SKIPPED_INTERVAL:
                        iswaitamin = true;
                        break;
                    default:
                        break;
                }
                temp.add(time);
            }
        }
        sql.updatePoster(poster);

        // 顶帖检测后主动向 MGactivity 刷新奖励数值状态
        Reward.refreshRewardState(poster, false);

        if (issucceed) {
            sender.sendMessage(Message.PREFIX.getString() + Message.REWARDGIVED.getString());
            // 全服广播已在 processRewardForPlayer() 内部完成, 此处不再重复广播
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
