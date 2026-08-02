package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.platform.PlatformOfflinePlayer;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;

import java.util.UUID;

/** /bt delete &lt;玩家ID&gt;。 */
public class DeleteCommandHandler implements CommandHandler {

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.hasPermission("bbstoper.delete")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPERMISSION.getString());
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_DELETE.getString());
            return;
        }
        PlatformOfflinePlayer op = KBBSToperCore.platform().getOfflinePlayer(args[1]);
        if (op == null) {
            sender.sendMessage(Message.PREFIX.getString() + Message.OWNERNOTFOUND.getString());
            return;
        }
        UUID uid = op.getUniqueId();
        Poster p = SQLManager.getSQLer().getPoster(uid.toString());
        if (p == null) {
            sender.sendMessage(Message.PREFIX.getString() + Message.OWNERNOTFOUND.getString());
        } else {
            SQLManager.getSQLer().deletePoster(uid.toString());
            sender.sendMessage(Message.PREFIX.getString() + Message.DELETESUCCESS.getString());
        }
    }
}
