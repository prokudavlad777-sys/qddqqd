package dev.build;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;
import java.util.UUID;

public final class PlanListener implements Listener {
    private final BuildPlugin plugin;
    private final PieceManager pm;
    private final PlanItem plan;

    public PlanListener(BuildPlugin plugin) {
        this.plugin = plugin;
        this.pm = plugin.pieces();
        this.plan = plugin.plan();
    }

    private static void bar(Player p, String text, NamedTextColor color) {
        p.sendActionBar(Component.text(text, color));
    }

    // ---------------- Крафт плану: тільки 5-й слот ----------------

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof Keyed k) || !k.getKey().equals(plugin.recipeKey())) return;

        ItemStack[] m = e.getInventory().getMatrix();
        boolean ok = m.length == 9 && m[4] != null && !m[4].getType().isAir();
        if (ok) {
            for (int i = 0; i < 9; i++) {
                if (i != 4 && m[i] != null && !m[i].getType().isAir()) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) e.getInventory().setResult(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipe(plugin.recipeKey());
    }

    // ---------------- Робота з планом ----------------

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItem(e.getNewSlot());
        if (plan.isPlan(item)) {
            showSelection(e.getPlayer(), plan.selected(item));
        }
    }

    private void showSelection(Player p, PieceType type) {
        Tier t = Tier.WOOD;
        bar(p, "Обрано: " + type.title + " (" + t.title + ") — " + pm.cost(t, type) + " × " + t.costName,
                NamedTextColor.YELLOW);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (!plan.isPlan(item)) return;

        e.setCancelled(true); // порожня карта не має створювати звичайну карту
        Player p = e.getPlayer();
        Action a = e.getAction();

        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            PieceType next = plan.selected(item).next();
            plan.setSelected(item, next);
            p.getInventory().setItemInMainHand(item);
            showSelection(p, next);
            return;
        }
        if (a != Action.RIGHT_CLICK_BLOCK) {
            bar(p, "Клікни правою кнопкою по блоку, щоб побудувати", NamedTextColor.GRAY);
            return;
        }
        place(p, plan.selected(item), e.getClickedBlock(), e.getBlockFace(), e.getInteractionPoint());
    }

    // ---------------- Будівництво ----------------

    private void place(Player p, PieceType type, Block clicked, BlockFace face, Location point) {
        World world = clicked.getWorld();
        UUID wid = world.getUID();
        Piece below = pm.at(clicked);

        int cx;
        int cz;
        int y;
        BlockFace dir = null;

        if (!type.horizontal()) {
            // стіна / проєм — на верх фундаменту або підлоги, біля краю клітинки
            if (below == null || !below.type.horizontal() || face != BlockFace.UP) {
                bar(p, type.title + " ставиться на верх фундаменту або підлоги — клікни біля потрібного краю",
                        NamedTextColor.RED);
                return;
            }
            cx = below.cx;
            cz = below.cz;
            y = below.y + 1;

            double px = point != null ? point.getX() : clicked.getX() + 0.5;
            double pz = point != null ? point.getZ() : clicked.getZ() + 0.5;
            double lx = px - cx * 3.0;
            double lz = pz - cz * 3.0;
            dir = BlockFace.NORTH;
            double best = lz;
            if (3 - lz < best) { best = 3 - lz; dir = BlockFace.SOUTH; }
            if (lx < best) { best = lx; dir = BlockFace.WEST; }
            if (3 - lx < best) { dir = BlockFace.EAST; }
        } else {
            if (type == PieceType.FOUNDATION && below != null && face == BlockFace.UP) {
                bar(p, "Фундамент не ставиться на іншу частину. Клікни збоку фундаменту, щоб розширити.",
                        NamedTextColor.RED);
                return;
            }
            Block base = clicked.getRelative(face);
            cx = Math.floorDiv(base.getX(), 3);
            cz = Math.floorDiv(base.getZ(), 3);
            y = base.getY();
        }

        Piece piece = new Piece(wid, type, Tier.WOOD, cx, cz, y, dir, p.getUniqueId());

        // 1. місце вільне?
        Block feet = p.getLocation().getBlock();
        Block head = p.getEyeLocation().getBlock();
        for (int[] c : piece.blocks()) {
            Block b = world.getBlockAt(c[0], c[1], c[2]);
            if (pm.at(b) != null || !(b.getType().isAir() || b.isReplaceable())) {
                bar(p, "Місце зайняте", NamedTextColor.RED);
                return;
            }
            if (same(b, feet) || same(b, head)) {
                bar(p, "Відійди — ти стоїш на місці будівлі", NamedTextColor.RED);
                return;
            }
        }

        // 2. опора
        if (!supported(piece, world)) {
            bar(p, supportHint(type), NamedTextColor.RED);
            return;
        }

        // 3. матеріали
        Tier tier = piece.tier;
        int cost = pm.cost(tier, type);
        boolean free = p.getGameMode() == GameMode.CREATIVE && plugin.getConfig().getBoolean("creative-free", false);
        if (!free) {
            int have = pm.count(p, tier);
            if (have < cost) {
                bar(p, "Не вистачає матеріалів: потрібно " + cost + " × " + tier.costName + ", є " + have,
                        NamedTextColor.RED);
                return;
            }
            pm.take(p, tier, cost);
        }

        // 4. будуємо
        for (int[] c : piece.blocks()) {
            world.getBlockAt(c[0], c[1], c[2]).setType(tier.block, false);
        }
        pm.add(piece);
        pm.save();

        int[] first = piece.blocks().get(0);
        world.playSound(new Location(world, first[0] + 0.5, first[1] + 0.5, first[2] + 0.5),
                tier.block.createBlockData().getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
        bar(p, type.title + " побудовано. Покращити — /upgrade", NamedTextColor.GREEN);
    }

    private static boolean same(Block a, Block b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    private String supportHint(PieceType type) {
        return switch (type) {
            case FOUNDATION -> "Фундамент потребує землі під собою або сусіднього фундаменту";
            case FLOOR -> "Підлога ставиться зверху на стіни (клікни по верху стіни) або поруч з іншою підлогою";
            default -> "Немає опори";
        };
    }

    private boolean supported(Piece piece, World world) {
        UUID wid = piece.world;
        int minX = piece.cx * 3;
        int minZ = piece.cz * 3;

        switch (piece.type) {
            case FOUNDATION -> {
                int solid = 0;
                for (int dx = 0; dx < 3; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        Block b = world.getBlockAt(minX + dx, piece.y - 1, minZ + dz);
                        if (b.getType().isSolid()) solid++;
                    }
                }
                if (solid >= 5) return true;
                return hasNeighbour(piece, wid, List.of(PieceType.FOUNDATION));
            }
            case FLOOR -> {
                for (int dx = 0; dx < 3; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        Piece under = pm.at(wid, minX + dx, piece.y - 1, minZ + dz);
                        if (under != null && !under.type.horizontal()
                                && under.cx == piece.cx && under.cz == piece.cz) {
                            return true;
                        }
                    }
                }
                return hasNeighbour(piece, wid, List.of(PieceType.FOUNDATION, PieceType.FLOOR));
            }
            default -> {
                return true; // стіни і проєми вже стоять на фундаменті/підлозі
            }
        }
    }

    private boolean hasNeighbour(Piece piece, UUID wid, List<PieceType> types) {
        int[][] offs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : offs) {
            int x = (piece.cx + o[0]) * 3 + 1;
            int z = (piece.cz + o[1]) * 3 + 1;
            Piece n = pm.at(wid, x, piece.y, z);
            if (n != null && types.contains(n.type)) return true;
        }
        return false;
    }

    // ---------------- Захист споруд ----------------

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (pm.at(e.getBlock()) == null) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Component.text("Це частина будівлі — використай /demolish", NamedTextColor.RED));
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> pm.at(b) != null);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> pm.at(b) != null);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent e) {
        if (pm.at(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (pm.at(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (pm.at(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
