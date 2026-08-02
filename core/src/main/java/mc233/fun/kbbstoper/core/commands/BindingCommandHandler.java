package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.BindingSession;
import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;

import java.util.List;
import java.util.Map;

/** /bt binding &lt;论坛ID&gt;，需要输入两次确认。 */
public class BindingCommandHandler implements CommandHandler {

    private final Map<String, String> cache;

    public BindingCommandHandler(Map<String, String> cache) {
        this.cache = cache;
    }

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.isPlayer()) {
            sender.sendMessage(Message.PLAYERCMD.getString());
            sender.sendMessage(Message.HELP_HELP.getString());
            return;
        }
        if (!sender.hasPermission("bbstoper.binding")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPERMISSION.getString());
            BindingSession.Holder.get().finish(sender);
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_BINDING.getString());
            BindingSession.Holder.get().finish(sender);
            return;
        }

        // 每次现取，保证数据库重连后拿到的是新连接
        SQLer sql = SQLManager.getSQLer();
        PlatformPlayer p = sender.asPlayer();
        String uuid = p.getUniqueId().toString();
        Poster poster = sql.getPoster(uuid);
        boolean exists = poster != null;
        if (!exists) {
            poster = new Poster();
            poster.setUuid(uuid);
            poster.setName(p.getName());
        } else {
            long cd = System.currentTimeMillis() - poster.getBinddate();
            long limit = (long) Option.BBS_CHANGEIDCOOLDOWN.getInt() * 86400000L;
            if (cd < limit) {
                long left = (limit - cd) / 86400000L;
                sender.sendMessage(Message.PREFIX.getString()
                        + Message.ONCOOLDOWN.getString().replace("%COOLDOWN%", String.valueOf(left)));
                BindingSession.Holder.get().finish(sender);
                return;
            }
        }

        String input = args[1];
        String otherUuid = sql.bbsNameCheck(input);
        if (otherUuid == null) {
            // 首次输入需要再确认一次
            if (input.equals(cache.get(uuid))) {
                poster.setBbsname(input);
                poster.setBinddate(System.currentTimeMillis());
                if (exists) {
                    sql.updatePoster(poster);
                } else {
                    sql.addPoster(poster);
                }
                sender.sendMessage(Message.PREFIX.getString() + Message.BINDINGSUCCESS.getString());
                cache.remove(uuid);
            } else {
                cache.put(uuid, input);
                sender.sendMessage(Message.PREFIX.getString() + Message.REPEAT.getString());
            }
        } else if (otherUuid.equals(uuid)) {
            sender.sendMessage(Message.PREFIX.getString() + Message.OWNSAMEBIND.getString());
        } else {
            sender.sendMessage(Message.PREFIX.getString() + Message.SAMEBIND.getString());
        }
        BindingSession.Holder.get().finish(sender);
    }

    @Override
    public List<String> tabComplete(PlatformSender sender, String[] args) {
        return null;
    }
}
