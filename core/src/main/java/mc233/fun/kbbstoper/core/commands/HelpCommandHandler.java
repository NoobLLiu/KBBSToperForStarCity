package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.platform.PlatformSender;

import java.util.List;

/** /bt help，按权限过滤条目。 */
public class HelpCommandHandler implements CommandHandler {

    @Override
    public void handle(PlatformSender sender, String[] args) {
        sender.sendMessage(Message.PREFIX.getString() + Message.HELP_TITLE.getString());
        if (sender.hasPermission("bbstoper.reward")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_REWARD.getString());
        }
        if (sender.hasPermission("bbstoper.testreward")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_TESTREWARD.getString());
        }
        if (sender.hasPermission("bbstoper.binding")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_BINDING.getString());
        }
        if (sender.hasPermission("bbstoper.list")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_LIST.getString());
        }
        if (sender.hasPermission("bbstoper.top")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_TOP.getString());
        }
        if (sender.hasPermission("bbstoper.check")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_CHECK.getString());
        }
        if (sender.hasPermission("bbstoper.delete")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_DELETE.getString());
        }
        if (sender.hasPermission("bbstoper.reload")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_RELOAD.getString());
        }
    }

    @Override
    public List<String> tabComplete(PlatformSender sender, String[] args) {
        return null;
    }
}
