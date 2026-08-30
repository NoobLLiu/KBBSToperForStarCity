package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Reward;
import mc233.fun.kbbstoper.core.platform.PlatformSender;

import java.util.Arrays;
import java.util.List;

/**
 * /bt testreward [normal|peak|max]，直接模拟一次奖励效果用于验证配置。
 *
 * <p>normal = 平峰期顶帖(+gain-normal 级)；peak = 高峰期顶帖(+gain-peak 级, 含星光点)；
 * max = 满级效果预览。旧参数 incentive/offday 作为别名保留兼容(GUI 仍在用)。</p>
 */
public class TestRewardCommandHandler implements CommandHandler {

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.isPlayer()) {
            sender.sendMessage(Message.PLAYERCMD.getString());
            sender.sendMessage(Message.HELP_HELP.getString());
            return;
        }
        if (!sender.hasPermission("bbstoper.testreward")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPERMISSION.getString());
            return;
        }
        String type = "NORMAL";
        if (args.length == 2) {
            String t = args[1].toUpperCase();
            // incentive/offday 为等级制之前的旧参数名, 映射到 高峰/满级
            if (t.equals("INCENTIVE")) {
                t = "PEAK";
            } else if (t.equals("OFFDAY")) {
                t = "MAX";
            }
            if (t.equals("NORMAL") || t.equals("PEAK") || t.equals("MAX")) {
                type = t;
            } else {
                sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
                sender.sendMessage(Message.PREFIX.getString() + Message.HELP_TESTREWARD.getString());
                return;
            }
        }
        new Reward(sender.asPlayer(), null, 0).testAward(type);
        sender.sendMessage(Message.PREFIX.getString() + Message.REWARDGIVED.getString());
    }

    @Override
    public List<String> tabComplete(PlatformSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("normal", "peak", "max");
        }
        return null;
    }
}
