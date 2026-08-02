package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CLIUtil;
import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;

import java.util.ArrayList;
import java.util.List;

/** /bt top [页码]，按顶帖次数排序列出玩家。 */
public class TopCommandHandler implements CommandHandler {

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.hasPermission("bbstoper.top")) {
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
                sender.sendMessage(Message.PREFIX.getString() + Message.HELP_TOP.getString());
                return;
            }
        } else if (args.length > 2) {
            sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_TOP.getString());
            return;
        }

        List<Poster> all = new ArrayList<>();
        List<Poster> counted = SQLManager.getSQLer().getTopPosters();
        if (counted != null) {
            all.addAll(counted);
        }
        List<Poster> nocount = SQLManager.getSQLer().getNoCountPosters();
        if (nocount != null) {
            all.addAll(nocount);
        }
        int size = all.size();
        int pagesize = Math.max(1, Option.BBS_PAGESIZE.getInt());
        int total = (int) Math.ceil(size / (double) pagesize);
        if (page > total) {
            sender.sendMessage(Message.PREFIX.getString() + Message.OVERPAGE.getString());
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add(Message.PREFIX.getString() + Message.POSTERTOTAL.getString() + ":" + size);
        int start = (page - 1) * pagesize;
        int end = Math.min(start + pagesize, size);
        for (int i = start; i < end; i++) {
            Poster p = all.get(i);
            lines.add(Message.POSTERPLAYER.getString() + ":" + p.getName() + " "
                    + Message.POSTERID.getString() + ":" + p.getBbsname() + " "
                    + Message.POSTERNUM.getString() + ":" + p.getCount());
        }
        if (start == end) {
            lines.add(Message.NOPLAYER.getString());
        }
        lines.add(Message.PREFIX.getString() + Message.PAGEINFOTOP.getString()
                .replace("%PAGE%", String.valueOf(page))
                .replace("%TOTALPAGE%", String.valueOf(total)));
        lines.forEach(sender::sendMessage);
    }
}
