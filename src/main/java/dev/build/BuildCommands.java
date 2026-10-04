package dev.build;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class BuildCommands implements CommandExecutor {
    private final BuildPlugin plugin;

    public BuildCommands(BuildPlugin plugin) {
        this.plugin = plugin;
    }

    private static void msg(Player p, String text, NamedTextColor color) {
        p.sendMessage(Component.text(text, color));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Тільки для гравців.", NamedTextColor.RED));
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "plan" -> {
                give(p, plugin.plan().create());
                msg(p, "Видано план будівництва.", NamedTextColor.GREEN);
            }
            case "upgrade" -> upgrade(p);
            case "demolish" -> demolish(p);
            default -> { }
        }
        return true;
    }

    private void give(Player p, ItemStack item) {
        p.getInventory().addItem(item).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    private Piece lookedAt(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null) {
            msg(p, "Подивись на частину будівлі (до 6 блоків).", NamedTextColor.RED);
            return null;
        }
        Piece piece = plugin.pieces().at(target);
        if (piece == null) {
            msg(p, "Це не частина будівлі.", NamedTextColor.RED);
            return null;
        }
        if (plugin.getConfig().getBoolean("owner-only", true)
                && piece.owner != null
                && !piece.owner.equals(p.getUniqueId())
                && !p.hasPermission("building.admin")) {
            msg(p, "Ця споруда належить іншому гравцю.", NamedTextColor.RED);
            return null;
        }
        return piece;
    }

    // ---------- /upgrade ----------

    private void upgrade(Player p) {
        Piece piece = lookedAt(p);
        if (piece == null) return;

        Tier next = piece.tier.next();
        if (next == null) {
            msg(p, piece.type.title + " вже має максимальний рівень (" + piece.tier.title + ").", NamedTextColor.YELLOW);
            return;
        }

        PieceManager pm = plugin.pieces();
        int cost = pm.cost(next, piece.type);
        boolean free = p.getGameMode() == org.bukkit.GameMode.CREATIVE && plugin.getConfig().getBoolean("creative-free", false);
        if (!free) {
            int have = pm.count(p, next);
            if (have < cost) {
                msg(p, "Не вистачає матеріалів для покращення до «" + next.title + "»: потрібно "
                        + cost + " × " + next.costName + ", є " + have + ".", NamedTextColor.RED);
                return;
            }
            pm.take(p, next, cost);
        }

        World world = pm.worldOf(piece);
        if (world == null) return;
        for (int[] c : piece.blocks()) {
            world.getBlockAt(c[0], c[1], c[2]).setType(next.block, false);
        }
        piece.tier = next;
        pm.save();

        Location loc = p.getLocation();
        world.playSound(loc, next.block.createBlockData().getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0f, 0.8f);
        int[] first = piece.blocks().get(0);
        world.spawnParticle(Particle.CRIT, first[0] + 0.5, first[1] + 1.0, first[2] + 0.5, 20, 0.8, 0.5, 0.8, 0.1);
        msg(p, piece.type.title + " покращено до «" + next.title + "».", NamedTextColor.GREEN);
    }

    // ---------- /demolish ----------

    private void demolish(Player p) {
        Piece piece = lookedAt(p);
        if (piece == null) return;

        PieceManager pm = plugin.pieces();
        World world = pm.worldOf(piece);
        if (world == null) return;

        if (piece.type.horizontal()) {
            for (int[] c : piece.blocks()) {
                if (pm.at(piece.world, c[0], c[1] + 1, c[2]) != null) {
                    msg(p, "Спочатку знищ те, що стоїть зверху.", NamedTextColor.RED);
                    return;
                }
            }
        }

        for (int[] c : piece.blocks()) {
            world.getBlockAt(c[0], c[1], c[2]).setType(org.bukkit.Material.AIR, false);
        }
        pm.remove(piece);
        pm.save();

        int refund = pm.cost(piece.tier, piece.type) * plugin.getConfig().getInt("refund-percent", 50) / 100;
        if (refund > 0) {
            give(p, new ItemStack(piece.tier.refundItem, refund));
        }
        world.playSound(p.getLocation(), Sound.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
        msg(p, piece.type.title + " знищено." + (refund > 0 ? " Повернуто: " + refund + " × " + piece.tier.costName + "." : ""),
                NamedTextColor.GREEN);
    }
}
