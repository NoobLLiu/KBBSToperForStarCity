package mc233.fun.kbbstoper.nukkit.form;

import cn.nukkit.Player;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.form.element.ElementLabel;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.window.FormWindowSimple;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.PlaceholderResolver;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;
import mc233.fun.kbbstoper.nukkit.NukkitPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表单构建与状态跟踪。
 *
 * <p>Nukkit 的表单回调只带回表单 id，不带上下文，
 * 所以这里按「玩家 UUID + 表单 id」记住每张表单是什么、按钮各对应什么动作。</p>
 */
public final class FormRouter {

    /** key = 玩家UUID + "#" + 表单id。 */
    private static final Map<String, PendingForm> PENDING = new ConcurrentHashMap<>();

    private FormRouter() {
    }

    /** 一张已发出、等待回应的表单。 */
    public static final class PendingForm {

        /** 主界面表单，buttons 记录每个按钮下标对应的动作。 */
        public static final int TYPE_MAIN = 0;
        /** 绑定输入表单。 */
        public static final int TYPE_BINDING = 1;
        /** 纯展示表单（宣传帖链接、排行榜等），点任何按钮都只是关闭。 */
        public static final int TYPE_INFO = 2;

        public final int type;
        public final List<FormAction> buttons;

        PendingForm(int type, List<FormAction> buttons) {
            this.type = type;
            this.buttons = buttons;
        }
    }

    private static String key(UUID uuid, int formId) {
        return uuid + "#" + formId;
    }

    /** 取出并移除一张待回应表单。 */
    public static PendingForm consume(UUID uuid, int formId) {
        return PENDING.remove(key(uuid, formId));
    }

    /** 玩家退出时清掉他所有待回应表单，避免累积。 */
    public static void clear(UUID uuid) {
        String prefix = uuid + "#";
        PENDING.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // ---------------------------------------------------------------
    // 主界面
    // ---------------------------------------------------------------

    /**
     * 打开主界面。
     * 会读数据库判断是否已绑定，所以内部先切到异步线程，再回主线程发表单。
     */
    public static void openMainForm(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            SQLer sql = SQLManager.getSQLer();
            Poster poster = (sql == null) ? null : sql.getPoster(player.getUniqueId().toString());
            String bbsid = (poster == null || poster.getBbsname() == null || poster.getBbsname().isBlank())
                    ? null : poster.getBbsname();
            int posttimes = (poster == null) ? 0 : poster.getTopStates().size();

            KBBSToperCore.scheduler().runSync(() -> sendMainForm(player, bbsid, posttimes));
        });
    }

    private static void sendMainForm(Player player, String bbsid, int posttimes) {
        String title = Message.FORM_TITLE.getString("&l&dKBBSToper")
                .replace("%PREFIX%", Message.PREFIX.getString());

        StringBuilder content = new StringBuilder();
        content.append(Message.FORM_CONTENT.getString("&7论坛顶帖奖励")).append("\n\n");
        content.append(Message.POSTERID.getString()).append(": ")
                .append(bbsid == null ? Message.GUI_NOTBOUND.getString() : bbsid).append("\n");
        content.append(Message.POSTERNUM.getString()).append(": ").append(posttimes).append("\n");

        FormWindowSimple form = new FormWindowSimple(strip(title), strip(content.toString()));

        List<FormAction> actions = new ArrayList<>();

        String bindLabel = (bbsid == null)
                ? Message.FORM_BUTTON_BINDING.getString("&a绑定论坛ID")
                : Message.FORM_BUTTON_REBINDING.getString("&e更换论坛ID");
        form.addButton(new ElementButton(strip(bindLabel)));
        actions.add(FormAction.BINDING);

        form.addButton(new ElementButton(strip(Message.FORM_BUTTON_REWARD.getString("&b领取顶帖奖励"))));
        actions.add(FormAction.REWARD);

        form.addButton(new ElementButton(strip(Message.FORM_BUTTON_TOP.getString("&6顶帖排行榜"))));
        actions.add(FormAction.TOP);

        form.addButton(new ElementButton(strip(Message.FORM_BUTTON_POST.getString("&9查看宣传帖"))));
        actions.add(FormAction.POST);

        int id = player.showFormWindow(form);
        PENDING.put(key(player.getUniqueId(), id), new PendingForm(PendingForm.TYPE_MAIN, actions));
    }

    // ---------------------------------------------------------------
    // 绑定表单
    // ---------------------------------------------------------------

    /** 打开绑定输入表单。基岩版没有可点击聊天消息，所以直接用输入框。 */
    public static void openBindingForm(Player player) {
        KBBSToperCore.scheduler().runSync(() -> {
            FormWindowCustom form = new FormWindowCustom(
                    strip(Message.FORM_BINDING_TITLE.getString("&l绑定论坛ID")));
            form.addElement(new ElementLabel(
                    strip(Message.FORM_BINDING_LABEL.getString("&7请输入你的 KLPBBS 论坛用户名（不是 uid）"))));
            form.addElement(new ElementInput(
                    strip(Message.FORM_BINDING_INPUT.getString("论坛ID")),
                    strip(Message.FORM_BINDING_PLACEHOLDER.getString("在此输入")),
                    ""));

            int id = player.showFormWindow(form);
            PENDING.put(key(player.getUniqueId(), id), new PendingForm(PendingForm.TYPE_BINDING, null));
        });
    }

    // ---------------------------------------------------------------
    // 宣传帖信息表单
    // ---------------------------------------------------------------

    /** 显示宣传帖链接。基岩版无法点击打开链接，所以把地址原样列出来供玩家复制。 */
    public static void openPostForm(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            String url = "https://" + Option.WEBSITE.getString() + "/thread-"
                    + Option.BBS_URL.getString() + "-1-1.html";
            String lastpost = PlaceholderResolver.resolve(new NukkitPlayer(player), "lastpost");

            StringBuilder sb = new StringBuilder();
            for (String line : Message.CLICKPOSTICON.getStringList()) {
                sb.append(line.replace("%PAGE%", url)).append("\n");
            }
            if (lastpost != null) {
                sb.append("\n").append(Message.POSTERTIME.getString()).append(": ").append(lastpost);
            }

            final String content = sb.toString();
            KBBSToperCore.scheduler().runSync(() -> {
                FormWindowSimple form = new FormWindowSimple(
                        strip(Message.FORM_POST_TITLE.getString("&l本服宣传帖")),
                        strip(content));
                int id = player.showFormWindow(form);
                PENDING.put(key(player.getUniqueId(), id), new PendingForm(PendingForm.TYPE_INFO, null));
            });
        });
    }

    /** 用一张只读表单展示多行文本（排行榜、列表等）。 */
    public static void openInfoForm(Player player, String title, List<String> lines) {
        KBBSToperCore.scheduler().runSync(() -> {
            FormWindowSimple form = new FormWindowSimple(strip(title), strip(String.join("\n", lines)));
            int id = player.showFormWindow(form);
            PENDING.put(key(player.getUniqueId(), id), new PendingForm(PendingForm.TYPE_INFO, null));
        });
    }

    /**
     * 表单标题与内容不支持 § 颜色码渲染的场合会显示成乱码，
     * 这里把已经转换过的颜色码去掉，只保留纯文本。
     */
    private static String strip(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
