package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CLIUtil;
import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Crawler;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.platform.PlatformSender;

import java.util.ArrayList;
import java.util.List;

/** /bt list [页码]，列出有效期内的顶帖记录。 */
public class ListCommandHandler implements CommandHandler {

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.hasPermission("bbstoper.list")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPERMISSION.getString());
            return;
        }
        if (sender.isPlayer() && !sender.hasPermission("bbstoper.bypassquerycooldown")) {
            double cd = CLIUtil.getQueryCooldown(sender.asPlayer());
            if (cd > 0) {
                sender.sendMessage(Message.PREFIX.getString()
                        + Message.QUERYCOOLDOWN.getString().replace("%COOLDOWN%", String.valueOf((int) cd)));
                return;
            }
            CLIUtil.recordQuery(sender.asPlayer());
        }
        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
                sender.sendMessage(Message.PREFIX.getString() + Message.HELP_LIST.getString());
                return;
            }
        } else if (args.length > 2) {
            sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_LIST.getString());
            return;
        }

        Crawler crawler = Crawler.fetch();
        if (!crawler.visible) {
            sender.sendMessage(Message.PREFIX.getString() + Message.PAGENOTVISIBLE.getString());
            return;
        }

        int size = crawler.ID.size();
        int pagesize = Math.max(1, Option.BBS_PAGESIZE.getInt());
        int total = (int) Math.ceil(size / (double) pagesize);
        if (page > total) {
            sender.sendMessage(Message.PREFIX.getString() + Message.OVERPAGE.getString());
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add(Message.PREFIX.getString() + Message.POSTERNUM.getString() + ":" + size);
        int start = (page - 1) * pagesize;
        int end = Math.min(start + pagesize, size);
        for (int i = start; i < end; i++) {
            lines.add(Message.POSTERID.getString() + ":" + crawler.ID.get(i) + " "
                    + Message.POSTERTIME.getString() + ":" + crawler.Time.get(i));
        }
        if (start == end) {
            lines.add(Message.NOPOSTER.getString());
        }
        lines.add(Message.PREFIX.getString() + Message.PAGEINFO.getString()
                .replace("%PAGE%", String.valueOf(page))
                .replace("%TOTALPAGE%", String.valueOf(total)));
        lines.forEach(sender::sendMessage);
    }
}
