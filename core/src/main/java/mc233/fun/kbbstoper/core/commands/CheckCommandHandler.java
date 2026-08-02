package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.platform.PlatformOfflinePlayer;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** /bt check &lt;bbsid|player&gt; &lt;值&gt;。 */
public class CheckCommandHandler implements CommandHandler {

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.hasPermission("bbstoper.check")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPERMISSION.getString());
            return;
        }
        if (args.length != 3) {
            sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_CHECK.getString());
            return;
        }
        switch (args[1].toLowerCase()) {
            case "bbsid": {
                String user = SQLManager.getSQLer().bbsNameCheck(args[2]);
                if (user == null) {
                    sender.sendMessage(Message.PREFIX.getString() + Message.IDNOTFOUND.getString());
                    return;
                }
                String name = null;
                try {
                    PlatformOfflinePlayer op = KBBSToperCore.platform().getOfflinePlayer(UUID.fromString(user));
                    name = op.getName();
                } catch (IllegalArgumentException e) {
                    KBBSToperCore.logger().warning("数据库里存在非法 UUID：" + user);
                }
                sender.sendMessage(Message.PREFIX.getString() + Message.IDOWNER.getString()
                        .replace("%PLAYER%", name == null ? user : name)
                        .replace("%UUID%", user));
                break;
            }
            case "player": {
                PlatformOfflinePlayer op = KBBSToperCore.platform().getOfflinePlayer(args[2]);
                if (op == null) {
                    sender.sendMessage(Message.PREFIX.getString() + Message.OWNERNOTFOUND.getString());
                    return;
                }
                Poster p = SQLManager.getSQLer().getPoster(op.getUniqueId().toString());
                if (p == null) {
                    sender.sendMessage(Message.PREFIX.getString() + Message.OWNERNOTFOUND.getString());
                } else {
                    sender.sendMessage(Message.PREFIX.getString()
                            + Message.OWNERID.getString().replace("%ID%", p.getBbsname()));
                }
                break;
            }
            default:
                sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
        }
    }

    @Override
    public List<String> tabComplete(PlatformSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("bbsid", "player");
        }
        return null;
    }
}
