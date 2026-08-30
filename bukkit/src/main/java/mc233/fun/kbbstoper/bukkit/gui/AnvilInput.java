package mc233.fun.kbbstoper.bukkit.gui;

import mc233.fun.kbbstoper.core.Message;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 伪铁砧输入界面（JAVA 端绑定 / 管理参数收集用）。
 *
 * <p>玩家在改名槽输入文本，点击输出槽（第 3 格）确认。
 * 确认时按白名单校验，非法输入会提示并重新打开。</p>
 *
 * <h2>为什么玩家没经验时无法提交</h2>
 * <p>原版铁砧改名要 1 级经验：玩家经验不足时，客户端结果槽直接为空，
 * 点了也没反应。修复办法：</p>
 * <ol>
 *   <li>在 {@link PrepareAnvilEvent} 里把修复/改名费用清零（{@code setRepairCost(0)}），
 *       并显式 setResult，客户端就不再要求经验；</li>
 *   <li>开启时若玩家不足 1 级则临时补到 1 级，关闭/提交时连同经验一起还原，
 *       保证任何情况下都能提交，且整个过程不消耗玩家经验。</li>
 * </ol>
 */
public final class AnvilInput implements InventoryHolder {

    /** 论坛ID / 玩家名白名单：字母、数字、下划线、中文，长度 1-32。 */
    public static final Pattern VALID_INPUT = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5]{1,32}$");

    /** 改名槽占位物品的显示名（提示玩家输入论坛用户名），玩家改名后会被替换掉。 */
    private static final String PLACEHOLDER_NAME = "输入论坛用户名";

    private final Inventory inv;
    private final String guide;
    private final Consumer<String> onConfirm;
    private boolean confirmed;

    /** 最近一次 PrepareAnvilEvent 解析出的改名文本。点击输出槽时优先用它，
     *  避免某些 Paper 版本在点击瞬间 getRenameText() 返回空导致无法提交、界面像被重新打开。 */
    private String entered;

    private Player player;
    private int origLevel;
    private float origExp;

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
        anvil.player = player;
        anvil.inv.setItem(0, anvil.guideItem());
        anvil.inv.setItem(1, null);
        anvil.prepareXp();
        player.openInventory(anvil.inv);
    }

    /**
     * 记录开启前的经验，并在需要时临时补到 1 级。
     * 客户端在"经验 < 修复费用"时结果槽为空，补 1 级用于兜底（配合 onPrepare 的费用清零）。
     */
    private void prepareXp() {
        origLevel = player.getLevel();
        origExp = player.getExp();
        if (player.getGameMode() != GameMode.CREATIVE && origLevel < 1) {
            player.setLevel(1);
        }
    }

    /** 关闭或提交后把经验还原，保证铁砧输入不消耗玩家经验。 */
    void restoreXp() {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setLevel(origLevel);
        player.setExp(origExp);
    }

    /**
     * 铁砧准备结果时调用：把改名费用清零并给出结果物品。
     * 不清零的话，玩家经验不足时客户端结果槽为空，也就点不动、无法提交。
     */
    void onPrepare(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        inv.setRepairCost(0);
        inv.setMaximumRepairCost(0);
        inv.setRepairCostAmount(0);

        String text = inv.getRenameText();
        this.entered = text;
        event.setResult((text == null || text.isBlank()) ? null : namedItem(text));
    }

    /** 最近一次解析出的改名文本（点击输出槽时回退使用）。 */
    String getEntered() {
        return entered;
    }

    /** 玩家点击输出槽时调用。 */
    void confirm(Player player, String text) {
        if (confirmed) {
            return;
        }
        confirmed = true;
        String input = text == null ? "" : text.trim();
        // 占位提示("输入论坛用户名")本身也是合法中文, 必须显式排除, 否则玩家不修改直接点确认会被当成有效论坛ID
        if (input.isEmpty() || input.equals(PLACEHOLDER_NAME) || !VALID_INPUT.matcher(input).matches()) {
            confirmed = false;
            player.sendMessage(Message.PREFIX.getString() + Message.GUI2_ANVIL_INVALID.getString());
            reopen(player);
            return;
        }
        player.closeInventory();
        restoreXp();
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

    /** 造一个"改名后"的结果物品，让客户端结果槽有东西可点。 */
    private ItemStack namedItem(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
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
