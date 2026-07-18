package dev.genesi.connectfour.board;

import dev.genesi.connectfour.model.Arena;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Maps Connect Four grid coordinates onto a wall board.
 * {@code facing} is the direction the board faces toward players.
 * Column 0 is on the player's left; row 0 is the bottom.
 */
public final class BoardGeometry {

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
    private final Vector up = new Vector(0, 1, 0);

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
    }

    public static Vector rightVector(BlockFace facing) {
        return switch (facing) {
            case NORTH -> new Vector(1, 0, 0);   // columns +X (player's right when looking north)
            case SOUTH -> new Vector(-1, 0, 0);  // columns -X
            case EAST -> new Vector(0, 0, 1);    // columns +Z
            case WEST -> new Vector(0, 0, -1);   // columns -Z
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
     * Resolve which column a clicked board block belongs to, or -1 if outside the grid.
     */
    public int columnAt(Block block) {
        if (block == null || !block.getWorld().equals(world)) {
            return -1;
        }
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < rows; row++) {
                if (contains(block, column, row)) {
                    return column;
                }
            }
        }
        return -1;
    }

    public boolean contains(Block block, int column, int row) {
        Location base = cellOrigin(column, row);
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        for (int dy = 0; dy < cellHeight; dy++) {
            for (int dx = 0; dx < cellWidth; dx++) {
                int x = bx + (int) right.getX() * dx;
                int z = bz + (int) right.getZ() * dx;
                if (block.getX() == x && block.getY() == by + dy && block.getZ() == z) {
                    return true;
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
