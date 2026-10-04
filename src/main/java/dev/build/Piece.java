package dev.build;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Одна частина будівлі. Сітка 3x3: клітинка (cx, cz) займає x = cx*3..cx*3+2, z = cz*3..cz*3+2.
 * y — нижній шар частини.
 */
public final class Piece {
    public final UUID world;
    public final PieceType type;
    public Tier tier;
    public final int cx, cz, y;
    public final BlockFace dir; // для стіни і проєму: сторона клітинки; для решти null
    public final UUID owner;

    public Piece(UUID world, PieceType type, Tier tier, int cx, int cz, int y, BlockFace dir, UUID owner) {
        this.world = world;
        this.type = type;
        this.tier = tier;
        this.cx = cx;
        this.cz = cz;
        this.y = y;
        this.dir = dir;
        this.owner = owner;
    }

    /** Координати всіх блоків частини як {x, y, z}. */
    public List<int[]> blocks() {
        List<int[]> out = new ArrayList<>(9);
        int minX = cx * 3;
        int minZ = cz * 3;
        if (type.horizontal()) {
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    out.add(new int[]{minX + dx, y, minZ + dz});
                }
            }
            return out;
        }
        for (int i = 0; i < 3; i++) {
            for (int h = 0; h < 3; h++) {
                if (type == PieceType.DOORWAY && i == 1 && h < 2) continue; // отвір 1x2
                int x;
                int z;
                switch (dir) {
                    case NORTH -> { x = minX + i; z = minZ; }
                    case SOUTH -> { x = minX + i; z = minZ + 2; }
                    case WEST -> { x = minX; z = minZ + i; }
                    default -> { x = minX + 2; z = minZ + i; }
                }
                out.add(new int[]{x, y + h, z});
            }
        }
        return out;
    }
}
