package mc233.fun.kbbstoper.bukkit.gui;

import mc233.fun.kbbstoper.bukkit.BukkitPlayer;
import mc233.fun.kbbstoper.bukkit.BukkitSender;
import mc233.fun.kbbstoper.bukkit.KBBSToperBukkit;
import mc233.fun.kbbstoper.core.GuiDataResolver;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.PlaceholderResolver;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.TopState;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 基岩版(Geyser)玩家的原生表单（v3 反射版）。
 *
 * <p>v3 关键改动：<b>不再直接引用任何 Cumulus 类型</b>。表单构造与发送全部走反射，
 * 且所用类一律从 {@code GeyserApi.sendForm(UUID, Form)} 签名里的 {@code Form} 类型
 * 的类加载器（即 Geyser 自己的类加载器）加载。</p>
 *
 * <p>原因：Geyser-Spigot 内置了 Cumulus，而服务器上其它插件（如 Floodgate、或任何
 * shade 过 cumulus 的插件）也可能内置 Cumulus。插件若直接引用
 * {@code org.geysermc.cumulus.form.Form}，Bukkit 的类加载器会从"另一个插件"里解析到
 * <b>不同的</b> {@code Form} Class 对象，与 {@code GeyserApi.sendForm} 签名里的
 * {@code Form} 形成双份类，触发
 * {@code loader constraint violation ... have different Class objects for the type
 * org.geysermc.cumulus.form.Form}。</p>
 *
 * <p>v3 之后发出去的 Form 对象必然是 Geyser 的 Form，与 {@code GeyserApi} 签名一致，
 * 不再冲突。发送失败仍安全回退 {@link GUI#openMain}；Geyser 未安装时本类正常加载。</p>
 */
public final class BedrockForm {

    /** 基岩版每页行数（表单行高有限，比 JAVA 少）。 */
    private static final int PAGE_SIZE = 12;

    /** GeyserApi.sendForm(UUID, Form) 反射句柄。 */
    private static Method SEND_FORM;
    /** GeyserApi.sendForm 签名里的 Form 类型（Geyser 自己加载的那份，作为一切 Cumulus 类的加载源头）。 */
    private static Class<?> FORM_TYPE;
    /** GeyserApi.api() 静态访问器。 */
    private static Method GEYSER_API_INSTANCE;

    static {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            GEYSER_API_INSTANCE = apiClass.getMethod("api");
            for (Method m : apiClass.getMethods()) {
                if ("sendForm".equals(m.getName()) && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == UUID.class
                        && "org.geysermc.cumulus.form.Form".equals(m.getParameterTypes()[1].getName())) {
                    SEND_FORM = m;
                    FORM_TYPE = m.getParameterTypes()[1];
                    break;
                }
            }
        } catch (Throwable ignore) {
            // Geyser 未安装：SEND_FORM/FORM_TYPE 保持 null，发送时安全回退
        }
    }

    private BedrockForm() {
    }

    /** 表单动作（与 Nukkit 端 FormRouter 对齐）。 */
    private enum FormAction {
        BINDING, REWARD, MY_RECORDS, MY_STATUS, TOP, POST, RULES, MANAGE, HELP,
        PREV_PAGE, NEXT_PAGE, BACK,
        TEST_REWARD, TEST_NORMAL, TEST_PEAK, TEST_MAX,
        LIST, CHECK, DELETE, RELOAD, DEBUG,
        DEBUG_CLEAR, DEBUG_STATUS, DEBUG_SIMULATE,
        BACK_MANAGE
    }

    // ---------------------------------------------------------------
    // 基岩版检测
    // ---------------------------------------------------------------

    /**
     * 该玩家是否经 Geyser 接入的基岩版。Geyser 未安装/未就绪时安全返回 false。
     *
     * <p>判定优先级：</p>
     * <ol>
     *   <li>UUID 前缀：Floodgate/Geyser 为基岩玩家分配的 UUID 前 4 段固定为
     *       {@code 00000000-0000-0000-0009}，不依赖任何 API 即可判定（覆盖
     *       Geyser-Spigot 与「代理端 Geyser + Floodgate」两种部署）；</li>
     *   <li>Geyser API：{@code GeyserApi.api().isBedrockPlayer(uuid)}。</li>
     * </ol>
     */
    public static boolean isBedrock(Player player) {
        String uuid = player.getUniqueId().toString();
        if (uuid.startsWith("00000000-0000-0000-0009")) {
            return true;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api = apiClass.getMethod("api").invoke(null);
            if (api == null) {
                return false;
            }
            Object ok = apiClass.getMethod("isBedrockPlayer", UUID.class)
                    .invoke(api, player.getUniqueId());
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 把表单发给玩家；Geyser/Floodgate 均不可用或发送失败返回 false（调用方应回退到 Java 界面）。
     * form 必须是 {@link #FORM_TYPE} 同加载器构造的对象（由 {@link RefForm} 保证）。
     */
    private static boolean sendForm(Player player, Object form) {
        try {
            if (SEND_FORM == null) {
                return false;
            }
            Object api = GEYSER_API_INSTANCE.invoke(null);
            if (api == null) {
                return false;
            }
            Object ok = SEND_FORM.invoke(api, player.getUniqueId(), form);
            return ok == null || Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            KBBSToperCore.logger().warning("Geyser 发送基岩表单失败: " + t);
            return sendFloodgate(player, form);
        }
    }

    /** 代理端 Geyser + Floodgate 部署的兜底通道（反射，零编译期依赖）。 */
    private static boolean sendFloodgate(Player player, Object form) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return false;
            }
            Object ok = apiClass.getMethod("sendForm", UUID.class, FORM_TYPE)
                    .invoke(api, player.getUniqueId(), form);
            return ok == null || Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            return false;
        }
    }

    /** & 颜色码转 §（基岩版表单渲染 §）。 */
    private static String fmt(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('&', '§');
    }

    /** 在主线程执行命令（表单响应回调可能不在主线程，命令派发需回主线程）。 */
    private static void runCommand(Player player, String... args) {
        Bukkit.getScheduler().runTask(KBBSToperBukkit.getInstance(),
                () -> KBBSToperCore.cli().onCommand(BukkitSender.of(player), args));
    }

    /** 从 Geyser 的类加载器加载 Cumulus 类（与 GeyserApi.sendForm 签名里的 Form 同源）。 */
    private static Class<?> load(String name) throws ClassNotFoundException {
        ClassLoader cl = FORM_TYPE != null
                ? FORM_TYPE.getClassLoader()
                : org.geysermc.geyser.api.GeyserApi.class.getClassLoader();
        return Class.forName(name, true, cl);
    }

    /** 从 SimpleFormResponse（Geyser 的类）反射读取点击的按钮索引。 */
    private static int clickedId(Object resp) {
        try {
            return (int) resp.getClass().getMethod("clickedButtonId").invoke(resp);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 从 CustomFormResponse（Geyser 的类）反射读取下一个输入值。 */
    private static String nextInput(Object resp) {
        try {
            return (String) resp.getClass().getMethod("next").invoke(resp);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 反射版表单构造器（不引用任何 Cumulus 类型）。
     * 所有方法调用均落在 {@link #FORM_TYPE} 的类加载器上，保证产物是 Geyser 的 Form。
     */
    private static final class RefForm {

        private final Object builder;
        private final Class<?> cls;

        private RefForm(String formClassName) {
            try {
                Class<?> fc = load(formClassName);
                this.builder = fc.getMethod("builder").invoke(null);
                this.cls = builder.getClass();
            } catch (Throwable t) {
                throw new IllegalStateException("构造表单失败: " + formClassName, t);
            }
        }

        static RefForm simple(String title, String content) {
            return new RefForm("org.geysermc.cumulus.form.SimpleForm")
                    .title(title).content(content);
        }

        static RefForm custom(String title) {
            return new RefForm("org.geysermc.cumulus.form.CustomForm").title(title);
        }

        RefForm title(String v) {
            return call("title", v);
        }

        RefForm content(String v) {
            return call("content", v);
        }

        RefForm button(String v) {
            return call("button", v);
        }

        RefForm label(String v) {
            return call("label", v);
        }

        RefForm input(String label, String placeholder, String def) {
            try {
                cls.getMethod("input", String.class, String.class, String.class)
                        .invoke(builder, label, placeholder, def);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
            return this;
        }

        /** 有效提交/按钮点击回调。handler 收到的是 Geyser 的响应对象（用 clickedId/nextInput 反射读取）。 */
        RefForm onValid(Consumer<Object> handler) {
            try {
                cls.getMethod("validResultHandler", Consumer.class).invoke(builder, handler);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
            return this;
        }

        RefForm onClose(Runnable r) {
            try {
                cls.getMethod("closedOrInvalidResultHandler", Runnable.class).invoke(builder, r);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
            return this;
        }

        Object build() {
            try {
                return cls.getMethod("build").invoke(builder);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

        private RefForm call(String method, String arg) {
            try {
                cls.getMethod(method, String.class).invoke(builder, arg);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
            return this;
        }
    }

    // ---------------------------------------------------------------
    // 主界面
    // ---------------------------------------------------------------

    public static void openMain(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            Poster poster = GuiDataResolver.poster(new BukkitPlayer(player));
            boolean bound = poster != null && poster.getBbsname() != null && !poster.getBbsname().isBlank();
            List<String> status = GuiDataResolver.statusLines(new BukkitPlayer(player));
            boolean op = player.hasPermission("bbstoper.manage");
            KBBSToperCore.scheduler().runSync(() -> sendMainForm(player, status, bound, op));
        });
    }

    private static void sendMainForm(Player player, List<String> status, boolean bound, boolean op) {
        String title = fmt(Message.FORM_TITLE.getString("KBBSToperForStarCity")
                .replace("%PREFIX%", Message.PREFIX.getString()));

        StringBuilder content = new StringBuilder();
        for (String line : status) {
            content.append(line).append("\n");
        }

        RefForm form = RefForm.simple(title, fmt(content.toString()));
        List<FormAction> actions = new ArrayList<>();

        String bindLabel = bound
                ? Message.FORM_BUTTON_REBINDING.getString("更换论坛ID")
                : Message.FORM_BUTTON_BINDING.getString("绑定论坛ID");
        form.button(fmt(bindLabel));
        actions.add(FormAction.BINDING);

        form.button(fmt(Message.FORM_BUTTON_REWARD.getString("领取顶帖奖励")));
        actions.add(FormAction.REWARD);

        form.button(fmt(Message.FORM2_BTN_MYRECORDS.getString("我的顶帖记录")));
        actions.add(FormAction.MY_RECORDS);

        form.button(fmt(Message.FORM2_BTN_STATUS.getString("我的状态")));
        actions.add(FormAction.MY_STATUS);

        form.button(fmt(Message.FORM_BUTTON_TOP.getString("顶帖排行榜")));
        actions.add(FormAction.TOP);

        form.button(fmt(Message.FORM_BUTTON_POST.getString("查看宣传帖")));
        actions.add(FormAction.POST);

        form.button(fmt(Message.FORM2_BTN_RULES.getString("活动规则")));
        actions.add(FormAction.RULES);

        if (op) {
            form.button(fmt(Message.FORM2_BTN_MANAGE.getString("管理菜单")));
            actions.add(FormAction.MANAGE);
        }

        form.button(fmt(Message.GUI2_HELP_TITLE.getString("帮助")));
        actions.add(FormAction.HELP);

        final List<FormAction> finalActions = actions;
        form.onValid(resp -> {
            int id = clickedId(resp);
            if (id >= 0 && id < finalActions.size()) {
                dispatch(player, finalActions.get(id));
            }
        });
        form.onClose(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    private static void dispatch(Player player, FormAction action) {
        switch (action) {
            case BINDING:
                openBinding(player);
                break;
            case REWARD:
                runCommand(player, "reward");
                break;
            case MY_RECORDS:
                openRecords(player, 1);
                break;
            case MY_STATUS:
                openStatus(player);
                break;
            case TOP:
                openTop(player, 1);
                break;
            case POST:
                openPost(player);
                break;
            case RULES:
                openRules(player);
                break;
            case HELP:
                openHelp(player);
                break;
            case MANAGE:
                openManage(player);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------
    // 绑定输入表单
    // ---------------------------------------------------------------

    public static void openBinding(Player player) {
        KBBSToperCore.scheduler().runSync(() -> {
            RefForm form = RefForm.custom(fmt(Message.FORM_BINDING_TITLE.getString("绑定论坛ID")))
                    .label(fmt(Message.FORM_BINDING_LABEL.getString("请输入你的 KLPBBS 论坛用户名（不是 uid）")))
                    .input(fmt(Message.FORM_BINDING_INPUT.getString("论坛ID")),
                            fmt(Message.FORM_BINDING_PLACEHOLDER.getString("在此输入")), "");
            form.onValid(resp -> {
                String input = nextInput(resp);
                if (input == null || input.trim().isEmpty()) {
                    player.sendMessage(Message.PREFIX.getString()
                            + Message.FORM_BINDING_EMPTY.getString("&c论坛ID不能为空。"));
                    return;
                }
                String id = input.trim();
                // 走与命令完全相同的路径：二次确认 / 冷却 / 重复绑定检查全部生效
                runCommand(player, "binding", id);
                // 缓存里还留着待确认的 ID，说明是第一次提交，重新弹表单完成二次确认
                Bukkit.getScheduler().runTaskLater(KBBSToperBukkit.getInstance(), () -> {
                    if (player.isOnline()
                            && KBBSToperCore.cli().getCache().containsKey(player.getUniqueId().toString())) {
                        openBinding(player);
                    }
                }, 10L);
            });
            form.onClose(() -> {
            });

            if (!sendForm(player, form.build())) {
                GUI.openMain(player);
            }
        });
    }

    // ---------------------------------------------------------------
    // 我的状态 / 规则 / 帮助 / 宣传帖（只读表单）
    // ---------------------------------------------------------------

    public static void openStatus(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            List<String> lines = GuiDataResolver.statusLines(new BukkitPlayer(player));
            KBBSToperCore.scheduler().runSync(() ->
                    openInfo(player, Message.FORM2_STATUS_TITLE.getString("我的状态"), lines));
        });
    }

    public static void openRules(Player player) {
        List<String> lines = GuiDataResolver.rulesLines();
        KBBSToperCore.scheduler().runSync(() ->
                openInfo(player, Message.FORM2_RULES_TITLE.getString("活动规则"), lines));
    }

    /** 帮助（指令速查，与 Java 版 GUI.openHelp 内容一致）。 */
    public static void openHelp(Player player) {
        List<String> lines = List.of(
                Message.GUI2_HELP_HELP.getString(),
                Message.GUI2_HELP_BINDING.getString(),
                Message.GUI2_HELP_REWARD.getString(),
                Message.GUI2_HELP_LIST.getString(),
                Message.GUI2_HELP_TOP.getString(),
                Message.GUI2_HELP_CHECK.getString(),
                Message.GUI2_HELP_DELETE.getString(),
                Message.GUI2_HELP_RELOAD.getString(),
                Message.GUI2_HELP_DEBUG.getString());
        KBBSToperCore.scheduler().runSync(() ->
                openInfo(player, Message.GUI2_HELP_TITLE.getString("帮助"), lines));
    }

    public static void openPost(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            String url = "https://" + Option.WEBSITE.getString() + "/thread-"
                    + Option.BBS_URL.getString() + "-1-1.html";
            String lastpost = PlaceholderResolver.resolve(new BukkitPlayer(player), "lastpost");

            StringBuilder sb = new StringBuilder();
            for (String line : Message.CLICKPOSTICON.getStringList()) {
                sb.append(line.replace("%PAGE%", url)).append("\n");
            }
            if (lastpost != null) {
                sb.append("\n").append(Message.POSTERTIME.getString()).append(": ").append(lastpost);
            }
            KBBSToperCore.scheduler().runSync(() ->
                    openInfo(player, Message.FORM_POST_TITLE.getString("本服宣传帖"), List.of(sb.toString())));
        });
    }

    /** 一张只读表单 + 一个"返回主菜单"按钮。 */
    private static void openInfo(Player player, String title, List<String> lines) {
        RefForm form = RefForm.simple(fmt(title), fmt(lines == null ? "" : String.join("\n", lines)))
                .button(fmt(Message.FORM2_BACK.getString("返回主菜单")));
        form.onValid(resp -> openMain(player));
        form.onClose(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    // ---------------------------------------------------------------
    // 我的记录 / 排行榜（分页）
    // ---------------------------------------------------------------

    public static void openRecords(Player player, int page) {
        KBBSToperCore.scheduler().runAsync(() -> {
            Poster poster = GuiDataResolver.poster(new BukkitPlayer(player));
            List<TopState> states = (poster == null) ? new ArrayList<>() : poster.getTopStates();
            List<String> lines = new ArrayList<>();
            for (TopState ts : states) {
                String kind = ts.isPeak() ? Message.FORM2_RECORD_PEAK.getString()
                        : Message.FORM2_RECORD_OFFPEAK.getString();
                String seq = ts.seq <= 0 ? "?" : String.valueOf(ts.seq);
                StringBuilder sb = new StringBuilder();
                sb.append(ts.time).append(" | ").append(kind).append(" 第").append(seq).append("次");
                if (ts.hasReward()) {
                    sb.append(" | ").append(ts.reward);
                } else {
                    sb.append(" | ").append(Message.FORM2_RECORD_NOREWARD.getString());
                }
                lines.add(sb.toString());
            }
            KBBSToperCore.scheduler().runSync(() ->
                    sendPagedForm(player, Message.FORM2_RECORDS_TITLE.getString("我的顶帖记录"), "records", page, lines));
        });
    }

    public static void openTop(Player player, int page) {
        KBBSToperCore.scheduler().runAsync(() -> {
            List<String> all = new ArrayList<>();
            SQLer sql = SQLManager.getSQLer();
            if (sql != null) {
                List<Poster> counted = sql.getTopPosters();
                if (counted != null) {
                    all.addAll(rankLines(counted));
                }
                List<Poster> nocount = sql.getNoCountPosters();
                if (nocount != null) {
                    all.addAll(rankLines(nocount));
                }
            }
            KBBSToperCore.scheduler().runSync(() ->
                    sendPagedForm(player, Message.FORM2_TOP_TITLE.getString("顶帖排行榜"), "top", page, all));
        });
    }

    private static List<String> rankLines(List<Poster> list) {
        List<String> out = new ArrayList<>();
        for (Poster p : list) {
            out.add(p.getName() + " (" + p.getBbsname() + ") - " + p.getCount());
        }
        return out;
    }

    private static void sendPagedForm(Player player, String title, String kind, int page, List<String> all) {
        int total = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        int cur = Math.max(1, Math.min(page, total));
        int start = (cur - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());

        StringBuilder content = new StringBuilder();
        if (start >= all.size()) {
            content.append(fmt(Message.GUI2_RECORDS_EMPTY.getString("暂无数据")));
        } else {
            for (int i = start; i < end; i++) {
                content.append(i + 1).append(". ").append(all.get(i)).append("\n");
            }
        }
        content.append(fmt(Message.FORM2_PAGE.getString("第 %PAGE%/%TOTAL% 页")
                .replace("%PAGE%", String.valueOf(cur))
                .replace("%TOTAL%", String.valueOf(total))));

        RefForm form = RefForm.simple(fmt(title), content.toString());
        List<FormAction> actions = new ArrayList<>();
        if (cur > 1) {
            form.button(fmt(Message.FORM2_PREV.getString("上一页")));
            actions.add(FormAction.PREV_PAGE);
        }
        if (cur < total) {
            form.button(fmt(Message.FORM2_NEXT.getString("下一页")));
            actions.add(FormAction.NEXT_PAGE);
        }
        form.button(fmt(Message.FORM2_BACK.getString("返回主菜单")));
        actions.add(FormAction.BACK);

        final List<FormAction> finalActions = actions;
        form.onValid(resp -> {
            int id = clickedId(resp);
            if (id < 0 || id >= finalActions.size()) {
                return;
            }
            FormAction a = finalActions.get(id);
            switch (a) {
                case PREV_PAGE:
                    if ("top".equals(kind)) {
                        openTop(player, cur - 1);
                    } else {
                        openRecords(player, cur - 1);
                    }
                    break;
                case NEXT_PAGE:
                    if ("top".equals(kind)) {
                        openTop(player, cur + 1);
                    } else {
                        openRecords(player, cur + 1);
                    }
                    break;
                case BACK:
                    openMain(player);
                    break;
                default:
                    break;
            }
        });
        form.onClose(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    // ---------------------------------------------------------------
    // 管理 / 测试 / 调试（菜单表单）
    // ---------------------------------------------------------------

    public static void openManage(Player player) {
        RefForm form = RefForm.simple(fmt(Message.FORM2_MANAGE_TITLE.getString("管理菜单")), "");
        List<FormAction> actions = new ArrayList<>();

        addMenu(form, actions, Message.FORM2_MANAGE_TEST.getString("测试奖励"), FormAction.TEST_REWARD);
        addMenu(form, actions, Message.FORM2_MANAGE_LIST.getString("顶帖列表(全服)"), FormAction.LIST);
        addMenu(form, actions, Message.FORM2_MANAGE_CHECK.getString("检查绑定"), FormAction.CHECK);
        addMenu(form, actions, Message.FORM2_MANAGE_DELETE.getString("删除玩家数据"), FormAction.DELETE);
        addMenu(form, actions, Message.FORM2_MANAGE_RELOAD.getString("重载插件"), FormAction.RELOAD);
        addMenu(form, actions, Message.FORM2_MANAGE_DEBUG.getString("调试"), FormAction.DEBUG);
        addMenu(form, actions, Message.FORM2_BACK.getString("返回主菜单"), FormAction.BACK);

        final List<FormAction> finalActions = actions;
        form.onValid(resp -> {
            int id = clickedId(resp);
            if (id >= 0 && id < finalActions.size()) {
                dispatchManage(player, finalActions.get(id));
            }
        });
        form.onClose(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    private static void addMenu(RefForm form, List<FormAction> actions, String label, FormAction action) {
        form.button(fmt(label));
        actions.add(action);
    }

    private static void dispatchManage(Player player, FormAction action) {
        switch (action) {
            case TEST_REWARD:
                openTest(player);
                break;
            case LIST:
                runCommand(player, "list");
                break;
            case CHECK:
                openInput(player, "check", Message.FORM2_MANAGE_CHECK.getString("检查绑定"), "论坛ID");
                break;
            case DELETE:
                openInput(player, "delete", Message.FORM2_MANAGE_DELETE.getString("删除玩家数据"), "玩家名");
                break;
            case RELOAD:
                runCommand(player, "reload");
                break;
            case DEBUG:
                openDebug(player);
                break;
            case BACK:
                openMain(player);
                break;
            default:
                break;
        }
    }

    /** 测试奖励子菜单：三种类型 + 返回管理菜单。 */
    public static void openTest(Player player) {
        RefForm form = RefForm.simple(fmt(Message.FORM2_TEST_TITLE.getString("测试奖励")), "");
        List<FormAction> actions = new ArrayList<>();

        addMenu(form, actions, Message.FORM2_TEST_NORMAL.getString("平峰期顶帖"), FormAction.TEST_NORMAL);
        addMenu(form, actions, Message.FORM2_TEST_PEAK.getString("高峰期顶帖"), FormAction.TEST_PEAK);
        addMenu(form, actions, Message.FORM2_TEST_MAX.getString("满级效果预览"), FormAction.TEST_MAX);
        addMenu(form, actions, Message.FORM2_BACK.getString("返回管理菜单"), FormAction.BACK_MANAGE);

        final List<FormAction> finalActions = actions;
        form.onValid(resp -> {
            int id = clickedId(resp);
            if (id >= 0 && id < finalActions.size()) {
                dispatchTest(player, finalActions.get(id));
            }
        });
        form.onClose(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    private static void dispatchTest(Player player, FormAction action) {
        switch (action) {
            case TEST_NORMAL:
                runCommand(player, "testreward", "normal");
                break;
            case TEST_PEAK:
                runCommand(player, "testreward", "peak");
                break;
            case TEST_MAX:
                runCommand(player, "testreward", "max");
                break;
            case BACK_MANAGE:
                openManage(player);
                break;
            default:
                break;
        }
    }

    /** 调试子菜单。 */
    public static void openDebug(Player player) {
        RefForm form = RefForm.simple(fmt(Message.FORM2_MANAGE_DEBUG.getString("调试")), "");
        List<FormAction> actions = new ArrayList<>();

        addMenu(form, actions, "清空", FormAction.DEBUG_CLEAR);
        addMenu(form, actions, "状态", FormAction.DEBUG_STATUS);
        addMenu(form, actions, "模拟", FormAction.DEBUG_SIMULATE);
        addMenu(form, actions, fmt(Message.FORM2_BACK.getString("返回管理菜单")), FormAction.BACK_MANAGE);

        final List<FormAction> finalActions = actions;
        form.onValid(resp -> {
            int id = clickedId(resp);
            if (id >= 0 && id < finalActions.size()) {
                dispatchDebug(player, finalActions.get(id));
            }
        });
        form.onClose(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    private static void dispatchDebug(Player player, FormAction action) {
        switch (action) {
            case DEBUG_CLEAR:
                runCommand(player, "debug", "clear");
                break;
            case DEBUG_STATUS:
                runCommand(player, "debug", "status");
                break;
            case DEBUG_SIMULATE:
                runCommand(player, "debug", "simulate");
                break;
            case BACK_MANAGE:
                openManage(player);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------
    // 参数输入表单（检查 / 删除）
    // ---------------------------------------------------------------

    public static void openInput(Player player, String inputAction, String title, String label) {
        KBBSToperCore.scheduler().runSync(() -> {
            RefForm form = RefForm.custom(fmt(title))
                    .label(fmt(Message.FORM2_INPUT_LABEL.getString("请输入:")))
                    .input(fmt(label), fmt(Message.FORM2_INPUT_PLACEHOLDER.getString("在此输入")), "");
            form.onValid(resp -> {
                String input = nextInput(resp);
                if (input == null || input.trim().isEmpty()) {
                    player.sendMessage(Message.PREFIX.getString()
                            + Message.FORM2_INPUT_EMPTY.getString("&c输入不能为空。"));
                    return;
                }
                String id = input.trim();
                if ("check".equals(inputAction)) {
                    runCommand(player, "check", "bbsid", id);
                } else if ("delete".equals(inputAction)) {
                    runCommand(player, "delete", id);
                }
            });
            form.onClose(() -> {
            });

            if (!sendForm(player, form.build())) {
                GUI.openMain(player);
            }
        });
    }
}
