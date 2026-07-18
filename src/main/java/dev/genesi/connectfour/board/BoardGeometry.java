package dev.genesi.connectfour.board;

import dev.genesi.connectfour.model.Arena;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Maps Connect Four grid coordinates onto a wall board.
 * {@code facing} is the direction the board faces toward players.
 * Column 0 is on the player's left; row 0 is the bottom.
 *
 * <p>You only set the bottom-left cell ({@code origin}); the plugin maps the full 7×6 grid from that.
 */
public final class BoardGeometry {

    /** How far in front/behind the cell plane clicks still count (AIR holes hit the back wall). */
    private static final int DEPTH_TOLERANCE = 3;

    private final World world;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final BlockFace facing;
    private final int columns;
    private final int rows;
    private final int cellWidth;
    private final int cellHeight;
    private final int columnGap;
    private final int rowGap;
    private final Vector right;
    private final Vector outward;

    public BoardGeometry(Arena arena) {
        Location origin = arena.getOrigin();
        if (origin == null || origin.getWorld() == null) {
            throw new IllegalArgumentException("arena origin missing");
        }
        this.world = origin.getWorld();
        this.originX = origin.getBlockX();
        this.originY = origin.getBlockY();
        this.originZ = origin.getBlockZ();
        this.facing = arena.getFacing();
        this.columns = arena.getColumns();
        this.rows = arena.getRows();
        this.cellWidth = arena.getCellWidth();
        this.cellHeight = arena.getCellHeight();
        this.columnGap = arena.getColumnGap();
        this.rowGap = arena.getRowGap();
        this.right = rightVector(facing);
        this.outward = facing.getDirection();
    }

    public static Vector rightVector(BlockFace facing) {
        return switch (facing) {
            case NORTH -> new Vector(1, 0, 0);
            case SOUTH -> new Vector(-1, 0, 0);
            case EAST -> new Vector(0, 0, 1);
            case WEST -> new Vector(0, 0, -1);
            default -> new Vector(1, 0, 0);
        };
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public World getWorld() {
        return world;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public int getCellWidth() {
        return cellWidth;
    }

    public int getCellHeight() {
        return cellHeight;
    }

    public int blocksPerCell() {
        return cellWidth * cellHeight;
    }

    /** Bottom-left block of a cell (column left→right, row bottom→top). */
    public Location cellOrigin(int column, int row) {
        int stepX = cellWidth + columnGap;
        int stepY = cellHeight + rowGap;
        int x = originX + (int) right.getX() * column * stepX;
        int y = originY + row * stepY;
        int z = originZ + (int) right.getZ() * column * stepX;
        return new Location(world, x, y, z);
    }

    public void forEachBlockInCell(int column, int row, CellConsumer consumer) {
        Location base = cellOrigin(column, row);
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        for (int dy = 0; dy < cellHeight; dy++) {
            for (int dx = 0; dx < cellWidth; dx++) {
                int x = bx + (int) right.getX() * dx;
                int z = bz + (int) right.getZ() * dx;
                consumer.accept(world.getBlockAt(x, by + dy, z));
            }
        }
    }

    /**
     * Resolve which column a clicked block belongs to, or -1 if outside the grid.
     * Includes blocks in front/behind the cell plane so AIR-hole boards still register.
     */
    public int columnAt(Block block) {
        if (block == null || block.getWorld() == null || !block.getWorld().equals(world)) {
            return -1;
        }
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < rows; row++) {
                if (containsWithDepth(block, column, row)) {
                    return column;
                }
            }
            // Allow clicking slightly above the top cell (drop chute / frame)
            if (containsColumnStrip(block, column)) {
                return column;
            }
        }
        return -1;
    }

    /**
     * Ray-trace from the player's eye to find a board column (works with AIR holes / cancelled clicks).
     */
    public int columnFromPlayer(Player player, double maxDistance) {
        if (player == null || player.getWorld() == null || !player.getWorld().equals(world)) {
            return -1;
        }
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                maxDistance,
                FluidCollisionMode.NEVER,
                true
        );
        if (hit != null && hit.getHitBlock() != null) {
            int column = columnAt(hit.getHitBlock());
            if (column >= 0) {
                return column;
            }
        }
        // Walk the ray and test nearby board planes
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (double d = 0.5; d <= maxDistance; d += 0.25) {
            Location point = eye.clone().add(dir.clone().multiply(d));
            Block block = point.getBlock();
            int column = columnAt(block);
            if (column >= 0) {
                return column;
            }
        }
        return -1;
    }

    public boolean contains(Block block, int column, int row) {
        return containsWithDepth(block, column, row, 0, 0);
    }

    private boolean containsWithDepth(Block block, int column, int row) {
        return containsWithDepth(block, column, row, -DEPTH_TOLERANCE, DEPTH_TOLERANCE);
    }

    private boolean containsWithDepth(Block block, int column, int row, int depthMin, int depthMax) {
        Location base = cellOrigin(column, row);
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        int ox = (int) Math.round(outward.getX());
        int oz = (int) Math.round(outward.getZ());
        for (int depth = depthMin; depth <= depthMax; depth++) {
            for (int dy = 0; dy < cellHeight; dy++) {
                for (int dx = 0; dx < cellWidth; dx++) {
                    int x = bx + (int) right.getX() * dx + ox * depth;
                    int z = bz + (int) right.getZ() * dx + oz * depth;
                    if (block.getX() == x && block.getY() == by + dy && block.getZ() == z) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Matches the full column footprint including a couple of blocks above the top cell. */
    private boolean containsColumnStrip(Block block, int column) {
        Location base = cellOrigin(column, 0);
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        int ox = (int) Math.round(outward.getX());
        int oz = (int) Math.round(outward.getZ());
        int totalHeight = rows * (cellHeight + rowGap) + 2;
        for (int depth = -DEPTH_TOLERANCE; depth <= DEPTH_TOLERANCE; depth++) {
            for (int dy = 0; dy < totalHeight; dy++) {
                for (int dx = 0; dx < cellWidth; dx++) {
                    int x = bx + (int) right.getX() * dx + ox * depth;
                    int z = bz + (int) right.getZ() * dx + oz * depth;
                    if (block.getX() == x && block.getY() == by + dy && block.getZ() == z) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isOnBoard(Block block) {
        return columnAt(block) >= 0;
    }

    @FunctionalInterface
    public interface CellConsumer {
        void accept(Block block);
    }
}
