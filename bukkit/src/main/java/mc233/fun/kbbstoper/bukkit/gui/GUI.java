package mc233.fun.kbbstoper.bukkit.gui;

import mc233.fun.kbbstoper.bukkit.BukkitPlayer;
import mc233.fun.kbbstoper.core.ConfigManager;
import mc233.fun.kbbstoper.core.KBBSToperCore;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 箱子界面，布局取自 gui.yml。 */
public class GUI {

    private static YamlConfiguration guiConfig;

    private Inventory inv;
    private final Map<Integer, String> actions = new HashMap<>();

    public static void setGuiConfig(YamlConfiguration config) {
        guiConfig = config;
    }

    public GUI(Player player, ConfigManager cfgMgr) {
        buildGui(player);
        if (inv != null) {
            Bukkit.getScheduler().runTask(
                    mc233.fun.kbbstoper.bukkit.KBBSToperBukkit.getInstance(),
                    () -> player.openInventory(inv)
            );
        }
    }

    public class GUIHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return inv;
        }

        public Map<Integer, String> getActions() {
            return actions;
        }
    }

    private void buildGui(Player player) {
        if (guiConfig == null) {
            KBBSToperCore.logger().severe("gui.yml 尚未加载！");
            return;
        }
        ConfigurationSection root = guiConfig.getConfigurationSection("gui");
        if (root == null) {
            KBBSToperCore.logger().severe("无法读取 gui.yml 中的 gui 节点！");
            return;
        }

        String rawTitle = root.getString("title", Message.GUI_TITLE.getString())
                .replace("%PREFIX%", Message.PREFIX.getString());
        String title = ChatColor.translateAlternateColorCodes('&', rawTitle);
        int rows = Math.max(1, root.getInt("rows", 3));
        inv = Bukkit.createInventory(new GUIHolder(), rows * 9, title);

        // 边框
        if (root.isConfigurationSection("border")) {
            ConfigurationSection bsec = root.getConfigurationSection("border");
            Material fill = parseMaterial(bsec.getString("fill", "WHITE_STAINED_GLASS_PANE"),
                    Material.WHITE_STAINED_GLASS_PANE);
            List<String> slotList = bsec.getStringList("slots");
            String rawSlots = String.join(";", slotList == null ? Collections.emptyList() : slotList);
            for (String part : rawSlots.split(";")) {
                try {
                    int slot = Integer.parseInt(part.trim());
                    if (slot >= 0 && slot < inv.getSize()) {
                        inv.setItem(slot, createPane(fill));
                    }
                } catch (NumberFormatException ignore) {
                    // 配置里写了非数字，跳过这一项
                }
            }
        } else {
            for (int i = 0; i < inv.getSize(); i++) {
                if (i > 9 && i < 17) {
                    continue;
                }
                inv.setItem(i, getRandomPane());
            }
        }

        ConfigurationSection items = root.getConfigurationSection("items");
        if (items == null) {
            return;
        }

        SQLer sql = SQLManager.getSQLer();
        String userUuid = player.getUniqueId().toString();
        Poster poster = (sql == null) ? null : sql.getPoster(userUuid);
        String posterId = (poster != null ? poster.getBbsname() : null);
        boolean bound = posterId != null && !posterId.isBlank();

        for (String key : items.getKeys(false)) {
            ConfigurationSection isec = items.getConfigurationSection(key);
            if (isec == null) {
                continue;
            }
            int slot = isec.getInt("slot", -1);
            if (slot < 0 || slot >= inv.getSize()) {
                continue;
            }

            boolean isBind = key.equals("bind");

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
            String parsedName = applyPlaceholders(player, isec.getString(nameKey, ""));
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', parsedName));

            List<String> rawLore = (isBind && bound && isec.isList("bound-lore"))
                    ? isec.getStringList("bound-lore")
                    : isec.getStringList("lore");
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(ChatColor.translateAlternateColorCodes('&', applyPlaceholders(player, line)));
            }
            meta.setLore(lore);

            if (mat == Material.PLAYER_HEAD && meta instanceof SkullMeta) {
                SkullMeta skull = (SkullMeta) meta;
                skull.setOwningPlayer(player);
                item.setItemMeta(skull);
            } else {
                item.setItemMeta(meta);
            }

            inv.setItem(slot, item);

            String actionKey = (isBind && bound)
                    ? isec.getString("bound-action", "")
                    : isec.getString("action", "");
            if (actionKey != null && !actionKey.isBlank()) {
                actions.put(slot, actionKey);
            }
        }
    }

    /** 材质名写错时记一条日志并回退，不让整个界面开不出来。 */
    private Material parseMaterial(String name, Material fallback) {
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

    private ItemStack getRandomPane() {
        Material[] panes = {
                Material.WHITE_STAINED_GLASS_PANE,
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.BLACK_STAINED_GLASS_PANE
        };
        return createPane(panes[new Random().nextInt(panes.length)]);
    }

    private ItemStack createPane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta me = it.getItemMeta();
        if (me != null) {
            me.setDisplayName(Message.GUI_FRAME.getString());
            it.setItemMeta(me);
        }
        return it;
    }

    private String applyPlaceholders(Player player, String text) {
        return KBBSToperCore.platform().applyPlaceholders(new BukkitPlayer(player), text);
    }
}
