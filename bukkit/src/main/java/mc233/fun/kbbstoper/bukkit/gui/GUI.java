package mc233.fun.kbbstoper.bukkit.gui;

import mc233.fun.kbbstoper.bukkit.BukkitPlayer;
import mc233.fun.kbbstoper.bukkit.BukkitSender;
import mc233.fun.kbbstoper.core.GuiAction;
import mc233.fun.kbbstoper.core.GuiDataResolver;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.MenuRouter;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 箱子界面构建（v2 最终稿）。
 * 主菜单布局取自 gui.yml（配置驱动，OP 4 行 / 普通玩家 3 行），
 * 子界面（状态/记录/排行/规则/管理/测试/调试/帮助）由代码构建。
 * 所有动态数据走 {@link GuiDataResolver} 内置占位符，不依赖 PAPI。
 */
public final class GUI {

    private static YamlConfiguration guiConfig;

    private static final int RECORD_PAGE_SIZE = 45;
    private static final int TOP_PAGE_SIZE = 45;

    private GUI() {
    }

    public static void setGuiConfig(YamlConfiguration config) {
        guiConfig = config;
    }

    /** 持有一张界面及其点击动作表。kind 用于翻页时区分记录/排行。 */
    public static final class Holder implements InventoryHolder {

        private final Map<Integer, GuiAction> actions = new HashMap<>();
        private final String kind;
        private Inventory inv;

        Holder(String kind) {
            this.kind = kind;
        }

        void bind(Inventory inv) {
            this.inv = inv;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        public Map<Integer, GuiAction> getActions() {
            return actions;
        }

        public String getKind() {
            return kind;
        }
    }

    // ---------------------------------------------------------------
    // 主菜单（gui.yml 驱动）
    // ---------------------------------------------------------------

    public static void openMain(Player player) {
        if (guiConfig == null) {
            KBBSToperCore.logger().severe("gui.yml 尚未加载！");
            return;
        }
        ConfigurationSection root = guiConfig.getConfigurationSection("gui");
        if (root == null) {
            KBBSToperCore.logger().severe("无法读取 gui.yml 中的 gui 节点！");
            return;
        }

        boolean op = player.isOp() || player.hasPermission("bbstoper.manage");
        int ymlRows = Math.max(1, root.getInt("rows", 4));
        int rows = op ? ymlRows : Math.min(3, ymlRows);

        String rawTitle = root.getString("title", Message.GUI_TITLE.getString())
                .replace("%PREFIX%", Message.PREFIX.getString());
        String title = color(rawTitle);

        Holder holder = new Holder(null);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.bind(inv);

        // 边框
        ConfigurationSection bsec = root.getConfigurationSection("border");
        if (bsec != null) {
            Material fill = parseMaterial(bsec.getString("fill", "GRAY_STAINED_GLASS_PANE"),
                    Material.GRAY_STAINED_GLASS_PANE);
            String rawSlots = String.join(";", bsec.getStringList("slots"));
            for (String part : rawSlots.split(";")) {
                try {
                    int slot = Integer.parseInt(part.trim());
                    if (slot >= 0 && slot < inv.getSize()) {
                        inv.setItem(slot, pane(fill));
                    }
                } catch (NumberFormatException ignore) {
                    // 配置里写了非数字，跳过
                }
            }
        }

        ConfigurationSection items = root.getConfigurationSection("items");
        if (items != null) {
            BukkitPlayer pp = new BukkitPlayer(player);
            Poster poster = GuiDataResolver.poster(pp);
            boolean bound = poster != null && poster.getBbsname() != null && !poster.getBbsname().isBlank();

            for (String key : items.getKeys(false)) {
                ConfigurationSection isec = items.getConfigurationSection(key);
                if (isec == null) {
                    continue;
                }
                // OP 专属项对普通玩家隐藏
                if (isec.getBoolean("op-only", false) && !op) {
                    continue;
                }
                int slot = isec.getInt("slot", -1);
                if (slot < 0 || slot >= inv.getSize()) {
                    continue;
                }

                boolean isBind = "bind".equals(key);
                String typeKey = (isBind && bound && isec.getString("bound-type") != null)
                        ? "bound-type" : "type";
                Material mat = parseMaterial(isec.getString(typeKey, "STONE"), Material.STONE);
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    continue;
                }

                String nameKey = (isBind && bound && isec.getString("bound-displayName") != null)
                        ? "bound-displayName" : "displayName";
                meta.setDisplayName(color(placeholders(player, isec.getString(nameKey, ""))));

                List<String> rawLore = (isBind && bound && isec.isList("bound-lore"))
                        ? isec.getStringList("bound-lore") : isec.getStringList("lore");
                List<String> lore = new ArrayList<>();
                for (String line : rawLore) {
                    lore.add(color(placeholders(player, line)));
                }
                meta.setLore(lore);

                if (mat == Material.PLAYER_HEAD && meta instanceof SkullMeta) {
                    ((SkullMeta) meta).setOwningPlayer(player);
                }
                item.setItemMeta(meta);
                inv.setItem(slot, item);

                String actionKey = (isBind && bound)
                        ? isec.getString("bound-action", "") : isec.getString("action", "");
                if (actionKey != null && !actionKey.isBlank()) {
                    GuiAction act = parseAction(actionKey);
                    if (act != null) {
                        holder.getActions().put(slot, act);
                    }
                }
            }
        }
        player.openInventory(inv);
    }

    // ---------------------------------------------------------------
    // 我的状态
    // ---------------------------------------------------------------

    public static void openStatus(Player player) {
        List<String> lines = GuiDataResolver.statusLines(new BukkitPlayer(player));

        Inventory inv = menu(5, Message.GUI2_STATUS_TITLE.getString(), "status");
        int placed = 0;
        for (String line : lines) {
            int row = 1 + placed / 7;
            int col = 1 + (placed % 7);
            place(inv, row * 9 + col, Material.PAPER, line, null, null);
            placed++;
        }
        place(inv, 40, Material.OAK_DOOR, Message.GUI2_MAIN.getString(), null, GuiAction.BACK);
        open(player, inv);
    }

    // ---------------------------------------------------------------
    // 我的顶帖记录（分页）
    // ---------------------------------------------------------------

    public static void openRecords(Player player, int page) {
        BukkitPlayer pp = new BukkitPlayer(player);
        Poster poster = GuiDataResolver.poster(pp);
        List<String> all = (poster == null) ? new ArrayList<>() : poster.getTopStates();

        MenuRouter.PageState st = MenuRouter.state(player.getUniqueId());
        int total = Math.max(1, (int) Math.ceil(all.size() / (double) RECORD_PAGE_SIZE));
        int cur = Math.max(1, Math.min(page, total));
        st.recordPage = cur;
        st.totalRecordPages = total;

        Inventory inv = menu(6, Message.GUI2_RECORDS_TITLE.getString(), "records");

        int start = (cur - 1) * RECORD_PAGE_SIZE;
        int end = Math.min(start + RECORD_PAGE_SIZE, all.size());
        if (start >= all.size()) {
            place(inv, 22, Material.PAPER, Message.GUI2_RECORDS_EMPTY.getString(), null, null);
        } else {
            int slot = 0;
            for (int i = start; i < end && slot < 45; i++, slot++) {
                place(inv, slot, Material.PAPER, all.get(i),
                        List.of(Message.POSTERTIME.getString()), null);
            }
        }
        paging(inv, cur, total, "records");
        open(player, inv);
    }

    // ---------------------------------------------------------------
    // 顶帖排行榜（分页）
    // ---------------------------------------------------------------

    public static void openTop(Player player, int page) {
        List<Poster> all = new ArrayList<>();
        SQLer sql = SQLManager.getSQLer();
        if (sql != null) {
            List<Poster> counted = sql.getTopPosters();
            if (counted != null) {
                all.addAll(counted);
            }
            List<Poster> nocount = sql.getNoCountPosters();
            if (nocount != null) {
                all.addAll(nocount);
            }
        }

        MenuRouter.PageState st = MenuRouter.state(player.getUniqueId());
        int total = Math.max(1, (int) Math.ceil(all.size() / (double) TOP_PAGE_SIZE));
        int cur = Math.max(1, Math.min(page, total));
        st.topPage = cur;
        st.totalTopPages = total;

        Inventory inv = menu(6, Message.GUI2_TOP_TITLE.getString(), "top");

        int start = (cur - 1) * TOP_PAGE_SIZE;
        int end = Math.min(start + TOP_PAGE_SIZE, all.size());
        if (start >= all.size()) {
            place(inv, 22, Material.EMERALD, Message.GUI2_TOP_EMPTY.getString(), null, null);
        } else {
            int slot = 0;
            for (int i = start; i < end && slot < 45; i++, slot++) {
                Poster p = all.get(i);
                List<String> lore = new ArrayList<>();
                lore.add(color(Message.POSTERID.getString() + ": &f" + p.getBbsname()));
                lore.add(color(Message.POSTERNUM.getString() + ": &f" + p.getCount()));
                ItemStack head = skull(p.getName());
                placeItem(inv, slot, head, color("&e#" + (i + 1) + " &f" + p.getName()), lore);
            }
        }
        paging(inv, cur, total, "top");
        open(player, inv);
    }

    // ---------------------------------------------------------------
    // 活动规则
    // ---------------------------------------------------------------

    public static void openRules(Player player) {
        List<String> lines = GuiDataResolver.rulesLines();

        Inventory inv = menu(5, Message.GUI2_RULES_TITLE.getString(), "rules");
        int placed = 0;
        for (String line : lines) {
            int row = 1 + placed / 7;
            int col = 1 + (placed % 7);
            place(inv, row * 9 + col, Material.PAPER, line, null, null);
            placed++;
        }
        place(inv, 40, Material.OAK_DOOR, Message.GUI2_MAIN.getString(), null, GuiAction.BACK);
        open(player, inv);
    }

    // ---------------------------------------------------------------
    // 管理菜单（OP）
    // ---------------------------------------------------------------

    public static void openManage(Player player) {
        Inventory inv = menu(3, Message.GUI2_MANAGE_TITLE.getString(), "manage");
        place(inv, 10, Material.SUNFLOWER, Message.GUI2_MANAGE_TEST.getString(), null, GuiAction.TEST_REWARD);
        place(inv, 12, Material.BOOK, Message.GUI2_MANAGE_LIST.getString(), null, GuiAction.LIST);
        place(inv, 14, Material.COMPASS, Message.GUI2_MANAGE_CHECK.getString(), null, GuiAction.CHECK);
        place(inv, 16, Material.BARRIER, Message.GUI2_MANAGE_DELETE.getString(), null, GuiAction.DELETE);
        place(inv, 20, Material.REDSTONE, Message.GUI2_MANAGE_RELOAD.getString(), null, GuiAction.RELOAD);
        place(inv, 22, Material.COMMAND_BLOCK, Message.GUI2_MANAGE_DEBUG.getString(), null, GuiAction.DEBUG);
        place(inv, 26, Material.OAK_DOOR, Message.GUI2_MAIN.getString(), null, GuiAction.BACK);
        open(player, inv);
    }

    /** 测试奖励子菜单（OP）。 */
    public static void openTestReward(Player player) {
        Inventory inv = menu(3, Message.GUI2_TEST_TITLE.getString(), "test");
        place(inv, 11, Material.EMERALD, Message.GUI2_TEST_NORMAL.getString(), null, GuiAction.TEST_NORMAL);
        place(inv, 13, Material.FIREWORK_ROCKET, Message.GUI2_TEST_INCENTIVE.getString(), null, GuiAction.TEST_INCENTIVE);
        place(inv, 15, Material.CLOCK, Message.GUI2_TEST_OFFDAY.getString(), null, GuiAction.TEST_OFFDAY);
        place(inv, 22, Material.OAK_DOOR, Message.GUI2_MAIN.getString(), null, GuiAction.BACK);
        open(player, inv);
    }

    /** 调试子菜单（OP）。 */
    public static void openDebug(Player player) {
        Inventory inv = menu(3, Message.GUI2_MANAGE_DEBUG.getString(), "debug");
        place(inv, 11, Material.REDSTONE, "&c清空", null, GuiAction.DEBUG_CLEAR);
        place(inv, 13, Material.PAPER, "&a状态", null, GuiAction.DEBUG_STATUS);
        place(inv, 15, Material.COMMAND_BLOCK, "&e模拟", null, GuiAction.DEBUG_SIMULATE);
        place(inv, 22, Material.OAK_DOOR, Message.GUI2_MAIN.getString(), null, GuiAction.BACK);
        open(player, inv);
    }

    // ---------------------------------------------------------------
    // 帮助
    // ---------------------------------------------------------------

    public static void openHelp(Player player) {
        Inventory inv = menu(3, Message.GUI2_HELP_TITLE.getString(), "help");
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
        place(inv, 13, Material.WRITABLE_BOOK, Message.GUI2_HELP_TITLE.getString(), lines, null);
        place(inv, 22, Material.OAK_DOOR, Message.GUI2_MAIN.getString(), null, GuiAction.BACK);
        open(player, inv);
    }

    // ---------------------------------------------------------------
    // 铁砧输入入口（绑定 / 检查 / 删除）
    // ---------------------------------------------------------------

    public static void openBindingAnvil(Player player) {
        AnvilInput.open(player, Message.GUI2_ANVIL_TITLE.getString(),
                Message.GUI2_ANVIL_GUIDE.getString(),
                id -> {
                    // 走与命令完全相同的路径：二次确认 / 冷却 / 重复检查全部生效
                    KBBSToperCore.cli().handleDirect(BukkitSender.of(player), new String[]{"binding", id});
                    // 缓存里还留着待确认的 ID，说明是第一次提交，重新弹铁砧完成二次确认
                    if (KBBSToperCore.cli().getCache().containsKey(player.getUniqueId().toString())) {
                        KBBSToperCore.scheduler().runLater(() -> {
                            if (player.isOnline()) {
                                openBindingAnvilConfirm(player);
                            }
                        }, 2);
                    }
                });
    }

    private static void openBindingAnvilConfirm(Player player) {
        AnvilInput.open(player, Message.GUI2_ANVIL_TITLE.getString(),
                Message.GUI2_ANVIL_GUIDE.getString() + "\n" + Message.GUI2_ANVIL_CONFIRM.getString(),
                id -> KBBSToperCore.cli().handleDirect(BukkitSender.of(player), new String[]{"binding", id}));
    }

    public static void openCheckAnvil(Player player) {
        AnvilInput.open(player, Message.GUI2_MANAGE_CHECK.getString(),
                Message.GUI2_ANVIL_GUIDE.getString(),
                id -> KBBSToperCore.cli().handleDirect(BukkitSender.of(player), new String[]{"check", "bbsid", id}));
    }

    public static void openDeleteAnvil(Player player) {
        AnvilInput.open(player, Message.GUI2_MANAGE_DELETE.getString(),
                Message.GUI2_ANVIL_GUIDE_PLAYER.getString(),
                id -> KBBSToperCore.cli().handleDirect(BukkitSender.of(player), new String[]{"delete", id}));
    }

    // ---------------------------------------------------------------
    // 工具方法
    // ---------------------------------------------------------------

    /** 建一张带边框的菜单。kind 用于翻页路由。 */
    private static Inventory menu(int rows, String title, String kind) {
        Holder holder = new Holder(kind);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, color(title));
        holder.bind(inv);
        fillBorder(inv);
        return inv;
    }

    private static void open(Player player, Inventory inv) {
        player.openInventory(inv);
    }

    private static void fillBorder(Inventory inv) {
        int rows = inv.getSize() / 9;
        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
    }

    /** 底部翻页栏：上一页 / 页码 / 下一页 / 返回。 */
    private static void paging(Inventory inv, int page, int total, String kind) {
        int size = inv.getSize();
        place(inv, size - 9, Material.ARROW, Message.GUI2_PREV.getString(), null, GuiAction.PREV_PAGE);
        place(inv, size - 7, Material.PAPER,
                Message.GUI2_PAGE.getString().replace("%PAGE%", String.valueOf(page))
                        .replace("%TOTAL%", String.valueOf(total)), null, null);
        place(inv, size - 1, Material.ARROW, Message.GUI2_NEXT.getString(), null, GuiAction.NEXT_PAGE);
        place(inv, size - 5, Material.OAK_DOOR, Message.GUI2_MAIN.getString(), null, GuiAction.BACK);
    }

    private static void place(Inventory inv, int slot, Material mat, String name, List<String> lore,
                              GuiAction action) {
        placeItem(inv, slot, new ItemStack(mat), color(name), lore == null ? null : colorAll(lore), action);
    }

    private static void placeItem(Inventory inv, int slot, ItemStack item, String name, List<String> lore) {
        placeItem(inv, slot, item, name, lore, null);
    }

    private static void placeItem(Inventory inv, int slot, ItemStack item, String name, List<String> lore,
                                  GuiAction action) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null) {
                meta.setLore(colorAll(lore));
            }
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
        if (action != null) {
            ((Holder) inv.getHolder()).getActions().put(slot, action);
        }
    }

    private static ItemStack skull(String playerName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta) {
            if (playerName != null && !playerName.isBlank()) {
                ((SkullMeta) meta).setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta me = it.getItemMeta();
        if (me != null) {
            me.setDisplayName(Message.GUI_FRAME.getString());
            it.setItemMeta(me);
        }
        return it;
    }

    /** 内置占位符 + PAPI（若安装）双重解析。 */
    private static String placeholders(Player player, String text) {
        BukkitPlayer pp = new BukkitPlayer(player);
        return KBBSToperCore.platform().applyPlaceholders(pp, GuiDataResolver.resolve(pp, text));
    }

    private static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    private static List<String> colorAll(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            KBBSToperCore.logger().warning("gui.yml 中的材质名无法识别：" + name + "，已回退为 " + fallback);
            return fallback;
        }
    }

    /**
     * 解析 gui.yml 里的动作名；兼容旧版模板的动作名(open/status/info 等)，
     * 避免旧布局下按钮失灵。
     */
    private static GuiAction parseAction(String actionKey) {
        String up = actionKey.trim().toUpperCase();
        try {
            return GuiAction.valueOf(up);
        } catch (IllegalArgumentException e) {
            switch (up) {
                case "OPEN":
                    return GuiAction.PROMO_POST;
                case "STATUS":
                case "INFO":
                    return GuiAction.MY_STATUS;
                case "RECORD":
                case "MYRECORD":
                    return GuiAction.MY_RECORDS;
                case "PAGE":
                    return GuiAction.PROMO_POST;
                default:
                    KBBSToperCore.logger().warning("gui.yml 中的动作无法识别: " + actionKey);
                    return null;
            }
        }
    }
}
