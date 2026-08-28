package mc233.fun.kbbstoper.bukkit.gui;

import mc233.fun.kbbstoper.core.Message;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 伪铁砧输入界面（JAVA 端绑定 / 管理参数收集用）。
 *
 * <p>玩家在改名槽输入文本，点击输出槽(第3格)确认。
 * 确认时按白名单校验，非法输入会提示并重新打开。</p>
 */
public final class AnvilInput implements InventoryHolder {

    /** 论坛ID / 玩家名白名单：字母、数字、下划线、中文，长度 1-32。 */
    public static final Pattern VALID_INPUT = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5]{1,32}$");

    /** 改名槽占位物品的名字（一个空格），玩家输入后会被替换掉。 */
    private static final String PLACEHOLDER_NAME = " ";

    private Inventory inv;
    private final String guide;
    private final Consumer<String> onConfirm;
    private boolean confirmed;

    private AnvilInput(String title, String guide, Consumer<String> onConfirm) {
        this.guide = guide;
        this.onConfirm = onConfirm;
        this.inv = Bukkit.createInventory(this, InventoryType.ANVIL, truncate(title));
    }

    /** 打开铁砧输入界面。title 为界面标题，guide 为改名槽物品的说明文字。 */
    public static void open(Player player, String title, String guide, Consumer<String> onConfirm) {
        if (player == null || !player.isOnline()) {
            return;
        }
        AnvilInput anvil = new AnvilInput(title, guide, onConfirm);
        anvil.inv.setItem(0, anvil.guideItem());
        anvil.inv.setItem(1, null);
        player.openInventory(anvil.inv);
    }

    private ItemStack guideItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PLACEHOLDER_NAME);
            List<String> lore = new ArrayList<>();
            for (String line : guide.split("\n")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 玩家点击输出槽时调用。 */
    void confirm(Player player, String text) {
        if (confirmed) {
            return;
        }
        confirmed = true;
        String input = text == null ? "" : text.trim();
        if (!VALID_INPUT.matcher(input).matches()) {
            confirmed = false;
            player.sendMessage(Message.PREFIX.getString() + Message.GUI2_ANVIL_INVALID.getString());
            reopen(player);
            return;
        }
        player.closeInventory();
        onConfirm.accept(input);
    }

    /** 改名槽空掉时把占位物品放回去，保证玩家随时可以输入。 */
    void ensureGuide() {
        ItemStack first = inv.getItem(0);
        if (first == null || first.getType() == Material.AIR) {
            inv.setItem(0, guideItem());
        }
    }

    private void reopen(Player player) {
        Bukkit.getScheduler().runTask(
                mc233.fun.kbbstoper.bukkit.KBBSToperBukkit.getInstance(),
                () -> player.openInventory(this.inv));
    }

    /** 铁砧标题有 32 字上限。 */
    private static String truncate(String title) {
        if (title == null) {
            return "";
        }
        String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', title));
        return plain.length() > 31 ? plain.substring(0, 31) : plain;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
