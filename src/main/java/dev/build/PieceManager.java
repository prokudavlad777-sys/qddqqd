package dev.build;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PieceManager {
    private record Pos(UUID world, int x, int y, int z) {}

    private final BuildPlugin plugin;
    private final File file;
    private final Map<Pos, Piece> byBlock = new HashMap<>();
    private final List<Piece> all = new ArrayList<>();

    public PieceManager(BuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pieces.yml");
    }

    // ---------- пошук ----------

    public Piece at(Block b) {
        return byBlock.get(new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()));
    }

    public Piece at(UUID world, int x, int y, int z) {
        return byBlock.get(new Pos(world, x, y, z));
    }

    // ---------- додавання / видалення ----------

    public void add(Piece p) {
        all.add(p);
        for (int[] c : p.blocks()) {
            byBlock.put(new Pos(p.world, c[0], c[1], c[2]), p);
        }
    }

    public void remove(Piece p) {
        all.remove(p);
        for (int[] c : p.blocks()) {
            byBlock.remove(new Pos(p.world, c[0], c[1], c[2]));
        }
    }

    // ---------- вартість і інвентар ----------

    public int cost(Tier tier, PieceType type) {
        return plugin.getConfig().getInt("costs." + tier.key + "." + type.key, 10);
    }

    public int count(Player p, Tier tier) {
        int total = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && tier.accepts(it.getType())) total += it.getAmount();
        }
        return total;
    }

    public void take(Player p, Tier tier, int amount) {
        var inv = p.getInventory();
        int left = amount;
        int size = inv.getStorageContents().length;
        for (int i = 0; i < size && left > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !tier.accepts(it.getType())) continue;
            int use = Math.min(it.getAmount(), left);
            left -= use;
            if (use >= it.getAmount()) {
                inv.setItem(i, null);
            } else {
                it.setAmount(it.getAmount() - use);
                inv.setItem(i, it);
            }
        }
    }

    // ---------- збереження ----------

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Piece p : all) {
            Map<String, Object> m = new HashMap<>();
            m.put("world", p.world.toString());
            m.put("type", p.type.name());
            m.put("tier", p.tier.name());
            m.put("cx", p.cx);
            m.put("cz", p.cz);
            m.put("y", p.y);
            m.put("dir", p.dir == null ? "-" : p.dir.name());
            m.put("owner", p.owner == null ? "-" : p.owner.toString());
            list.add(m);
        }
        yml.set("pieces", list);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не вдалося зберегти pieces.yml: " + e.getMessage());
        }
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : yml.getMapList("pieces")) {
            try {
                UUID world = UUID.fromString(String.valueOf(m.get("world")));
                PieceType type = PieceType.byName(String.valueOf(m.get("type")));
                Tier tier = Tier.byName(String.valueOf(m.get("tier")));
                int cx = ((Number) m.get("cx")).intValue();
                int cz = ((Number) m.get("cz")).intValue();
                int y = ((Number) m.get("y")).intValue();
                String d = String.valueOf(m.get("dir"));
                BlockFace dir = d.equals("-") ? null : BlockFace.valueOf(d);
                String o = String.valueOf(m.get("owner"));
                UUID owner = o.equals("-") ? null : UUID.fromString(o);
                add(new Piece(world, type, tier, cx, cz, y, dir, owner));
            } catch (Exception ex) {
                plugin.getLogger().warning("Пропущено пошкоджений запис у pieces.yml: " + ex.getMessage());
            }
        }
    }

    public World worldOf(Piece p) {
        return Bukkit.getWorld(p.world);
    }
}
