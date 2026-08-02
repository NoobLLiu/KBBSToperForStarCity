package mc233.fun.kbbstoper.nukkit.form;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.response.FormResponseSimple;
import mc233.fun.kbbstoper.core.CLI;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.nukkit.NukkitSender;

import java.util.List;

/** 表单回应处理。 */
public class FormListener implements Listener {

    @EventHandler
    public void onFormResponded(PlayerFormRespondedEvent event) {
        Player player = event.getPlayer();
        FormRouter.PendingForm pending = FormRouter.consume(player.getUniqueId(), event.getFormID());
        if (pending == null) {
            // 不是本插件发出的表单
            return;
        }
        if (event.wasClosed() || event.getResponse() == null) {
            return;
        }

        switch (pending.type) {
            case FormRouter.PendingForm.TYPE_MAIN:
                handleMain(player, pending, event);
                break;
            case FormRouter.PendingForm.TYPE_BINDING:
                handleBinding(player, event);
                break;
            default:
                // 只读表单，无需处理
                break;
        }
    }

    private void handleMain(Player player, FormRouter.PendingForm pending, PlayerFormRespondedEvent event) {
        if (!(event.getResponse() instanceof FormResponseSimple)) {
            return;
        }
        int clicked = ((FormResponseSimple) event.getResponse()).getClickedButtonId();
        List<FormAction> actions = pending.buttons;
        if (actions == null || clicked < 0 || clicked >= actions.size()) {
            return;
        }

        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return;
        }

        switch (actions.get(clicked)) {
            case BINDING:
                FormRouter.openBindingForm(player);
                break;
            case REWARD:
                cli.onCommand(NukkitSender.of(player), new String[]{"reward"});
                break;
            case TOP:
                cli.onCommand(NukkitSender.of(player), new String[]{"top"});
                break;
            case POST:
                FormRouter.openPostForm(player);
                break;
            default:
                player.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
        }
    }

    private void handleBinding(Player player, PlayerFormRespondedEvent event) {
        if (!(event.getResponse() instanceof FormResponseCustom)) {
            return;
        }
        // 0 号元素是说明标签，输入框是 1 号
        String input = ((FormResponseCustom) event.getResponse()).getInputResponse(1);
        if (input == null || input.trim().isEmpty()) {
            player.sendMessage(Message.PREFIX.getString()
                    + Message.FORM_BINDING_EMPTY.getString("&c论坛ID不能为空。"));
            return;
        }
        String id = input.trim();
        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return;
        }
        // 走与命令完全相同的路径，因此二次确认、冷却、重复绑定检查全部生效。
        // 表单只能一次拿到一个值，所以第一次提交会提示"请再输入一次"，
        // 玩家再次提交同一个 ID 才会真正写库。
        cli.onCommand(NukkitSender.of(player), new String[]{"binding", id});

        // 提交后重新弹一次输入框，方便玩家完成二次确认；
        // 延迟 1 秒等命令的异步反馈先到聊天栏。
        KBBSToperCore.scheduler().runLater(() -> {
            if (player.isOnline() && needConfirm(player)) {
                FormRouter.openBindingForm(player);
            }
        }, 20);
    }

    /** 缓存里还留着待确认的 ID，说明这次是第一次提交。 */
    private boolean needConfirm(Player player) {
        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return false;
        }
        return cli.getCache().get(player.getUniqueId().toString()) != null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        FormRouter.clear(event.getPlayer().getUniqueId());
    }
}
