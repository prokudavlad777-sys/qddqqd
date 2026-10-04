package dev.build;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class PlanItem {
    private final NamespacedKey markKey;
    private final NamespacedKey pieceKey;

    public PlanItem(BuildPlugin plugin) {
        this.markKey = new NamespacedKey(plugin, "building_plan");
        this.pieceKey = new NamespacedKey(plugin, "selected_piece");
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> lore(PieceType selected) {
        return List.of(
                plain("Обрано: " + selected.title, NamedTextColor.YELLOW),
                plain("ЛКМ — змінити частину", NamedTextColor.GRAY),
                plain("ПКМ по блоку — побудувати", NamedTextColor.GRAY),
                plain("/upgrade — покращити, /demolish — знищити", NamedTextColor.DARK_GRAY));
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("План будівництва", NamedTextColor.GOLD));
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(markKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, PieceType.FOUNDATION.name());
        meta.lore(lore(PieceType.FOUNDATION));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPlan(ItemStack item) {
        if (item == null || item.getType() != Material.MAP || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(markKey, PersistentDataType.BYTE);
    }

    public PieceType selected(ItemStack plan) {
        String s = plan.getItemMeta().getPersistentDataContainer().get(pieceKey, PersistentDataType.STRING);
        return s == null ? PieceType.FOUNDATION : PieceType.byName(s);
    }

    public void setSelected(ItemStack plan, PieceType type) {
        ItemMeta meta = plan.getItemMeta();
        meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, type.name());
        meta.lore(lore(type));
        plan.setItemMeta(meta);
    }
}
