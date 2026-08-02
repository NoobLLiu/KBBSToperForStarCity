package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Reward;
import mc233.fun.kbbstoper.core.platform.PlatformSender;

import java.util.Arrays;
import java.util.List;

/** /bt testreward [normal|incentive|offday]，直接跑奖励命令用于验证配置。 */
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
            if (t.equals("INCENTIVE") || t.equals("OFFDAY") || t.equals("NORMAL")) {
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
            return Arrays.asList("normal", "incentive", "offday");
        }
        return null;
    }
}
