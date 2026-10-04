package dev.build;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.function.Predicate;

public enum Tier {
    WOOD("wood", "Дерево", "дошки", Material.OAK_PLANKS, Material.OAK_PLANKS, Tag.PLANKS::isTagged),
    STONE("stone", "Камінь", "булижник", Material.STONE_BRICKS, Material.COBBLESTONE, m -> m == Material.COBBLESTONE),
    METAL("metal", "Залізо", "залізні зливки", Material.IRON_BLOCK, Material.IRON_INGOT, m -> m == Material.IRON_INGOT),
    OBSIDIAN("obsidian", "Обсидіан", "обсидіан", Material.OBSIDIAN, Material.OBSIDIAN, m -> m == Material.OBSIDIAN);

    public final String key;
    public final String title;
    public final String costName;
    public final Material block;
    public final Material refundItem;
    private final Predicate<Material> accepts;

    Tier(String key, String title, String costName, Material block, Material refundItem, Predicate<Material> accepts) {
        this.key = key;
        this.title = title;
        this.costName = costName;
        this.block = block;
        this.refundItem = refundItem;
        this.accepts = accepts;
    }

    public boolean accepts(Material m) {
        return accepts.test(m);
    }

    public Tier next() {
        Tier[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
    }

    public static Tier byName(String s) {
        for (Tier t : values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        return WOOD;
    }
}
