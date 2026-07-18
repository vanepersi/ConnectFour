package dev.genesi.connectfour.board;

import dev.genesi.connectfour.ConnectFourPlugin;
import dev.genesi.connectfour.model.Arena;
import dev.genesi.connectfour.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class BoardRenderer {

    private final ConnectFourPlugin plugin;

    public BoardRenderer(ConnectFourPlugin plugin) {
        this.plugin = plugin;
    }

    public Material emptyMaterial() {
        return material("materials.empty", Material.BLACK_CONCRETE);
    }

    public Material winHighlightMaterial() {
        return material("materials.win-highlight", Material.GLOWSTONE);
    }

    public Material teamMaterial(Team team) {
        return team.materialFromConfig(plugin.getConfig());
    }

    public BoardSnapshot capture(Arena arena) {
        BoardGeometry geometry = new BoardGeometry(arena);
        int blocksPerCell = geometry.blocksPerCell();
        Material[][][] materials = new Material[geometry.getColumns()][geometry.getRows()][blocksPerCell];
        for (int c = 0; c < geometry.getColumns(); c++) {
            for (int r = 0; r < geometry.getRows(); r++) {
                List<Material> cell = new ArrayList<>(blocksPerCell);
                geometry.forEachBlockInCell(c, r, block -> cell.add(block.getType()));
                materials[c][r] = cell.toArray(Material[]::new);
            }
        }
        BoardSnapshot snapshot = new BoardSnapshot(
                geometry.getColumns(),
                geometry.getRows(),
                blocksPerCell,
                materials
        );
        arena.setSnapshot(snapshot);
        return snapshot;
    }

    public void clear(Arena arena) {
        BoardGeometry geometry = new BoardGeometry(arena);
        BoardSnapshot snapshot = arena.getSnapshot();
        if (snapshot != null && snapshot.matches(geometry)) {
            restoreSnapshot(geometry, snapshot);
            return;
        }
        clearPiecesOnly(geometry);
    }

    public void paintCell(Arena arena, int column, int row, Team team) {
        BoardGeometry geometry = new BoardGeometry(arena);
        if (team == null) {
            restoreCell(arena, geometry, column, row);
        } else {
            paintCell(geometry, column, row, teamMaterial(team));
        }
    }

    public void paintCell(BoardGeometry geometry, int column, int row, Material material) {
        geometry.forEachBlockInCell(column, row, block -> setBlock(block, material));
    }

    public void paintGrid(Arena arena, Team[][] grid) {
        BoardGeometry geometry = new BoardGeometry(arena);
        for (int c = 0; c < geometry.getColumns(); c++) {
            for (int r = 0; r < geometry.getRows(); r++) {
                Team team = grid[c][r];
                if (team == null) {
                    restoreCell(arena, geometry, c, r);
                } else {
                    paintCell(geometry, c, r, teamMaterial(team));
                }
            }
        }
    }

    public void highlight(Arena arena, List<int[]> cells) {
        BoardGeometry geometry = new BoardGeometry(arena);
        Material highlight = winHighlightMaterial();
        for (int[] cell : cells) {
            paintCell(geometry, cell[0], cell[1], highlight);
        }
    }

    /**
     * Temporarily paints the full grid so admins can verify origin + facing.
     * Restores the snapshot (or previous blocks) after {@code ticks}.
     */
    public void preview(Arena arena, long ticks) {
        if (!arena.hasSnapshot()) {
            capture(arena);
        }
        BoardGeometry geometry = new BoardGeometry(arena);
        Material marker = Material.YELLOW_STAINED_GLASS;
        for (int c = 0; c < geometry.getColumns(); c++) {
            for (int r = 0; r < geometry.getRows(); r++) {
                paintCell(geometry, c, r, marker);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> clear(arena), Math.max(20L, ticks));
    }

    /** Paint one test disc at the bottom of a column, then clear after a few seconds. */
    public void testDrop(Arena arena, int column, Team team, long ticks) {
        if (!arena.hasSnapshot()) {
            capture(arena);
        }
        BoardGeometry geometry = new BoardGeometry(arena);
        if (column < 0 || column >= geometry.getColumns()) {
            return;
        }
        // Animate a quick fall for the admin test
        int top = geometry.getRows() - 1;
        AtomicInteger row = new AtomicInteger(top);
        var task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                int r = row.getAndDecrement();
                if (r < top) {
                    paintCell(arena, column, r + 1, null);
                }
                paintCell(arena, column, r, team);
                if (r <= 0) {
                    cancel();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> clear(arena), Math.max(40L, ticks));
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 3L);
    }

    private void clearPiecesOnly(BoardGeometry geometry) {
        Set<Material> pieceMats = pieceMaterials();
        Material empty = emptyMaterial();
        if (empty == Material.AIR) {
            return;
        }
        for (int c = 0; c < geometry.getColumns(); c++) {
            for (int r = 0; r < geometry.getRows(); r++) {
                geometry.forEachBlockInCell(c, r, block -> {
                    if (pieceMats.contains(block.getType())) {
                        setBlock(block, empty);
                    }
                });
            }
        }
    }

    private void restoreSnapshot(BoardGeometry geometry, BoardSnapshot snapshot) {
        for (int c = 0; c < geometry.getColumns(); c++) {
            for (int r = 0; r < geometry.getRows(); r++) {
                restoreCell(geometry, snapshot, c, r);
            }
        }
    }

    private void restoreCell(Arena arena, BoardGeometry geometry, int column, int row) {
        BoardSnapshot snapshot = arena.getSnapshot();
        if (snapshot != null && snapshot.matches(geometry)) {
            restoreCell(geometry, snapshot, column, row);
            return;
        }
        Material empty = emptyMaterial();
        Set<Material> pieceMats = pieceMaterials();
        geometry.forEachBlockInCell(column, row, block -> {
            if (pieceMats.contains(block.getType()) && empty != Material.AIR) {
                setBlock(block, empty);
            }
        });
    }

    private void restoreCell(BoardGeometry geometry, BoardSnapshot snapshot, int column, int row) {
        Material[] mats = snapshot.materialsFor(column, row);
        AtomicInteger index = new AtomicInteger(0);
        geometry.forEachBlockInCell(column, row, block -> {
            int i = index.getAndIncrement();
            if (i < mats.length) {
                setBlock(block, mats[i]);
            }
        });
    }

    private Set<Material> pieceMaterials() {
        Set<Material> set = EnumSet.noneOf(Material.class);
        set.add(teamMaterial(Team.RED));
        set.add(teamMaterial(Team.YELLOW));
        set.add(winHighlightMaterial());
        set.add(Material.YELLOW_STAINED_GLASS);
        return set;
    }

    private Material material(String path, Material fallback) {
        FileConfiguration config = plugin.getConfig();
        String name = config.getString(path, fallback.name());
        Material material = Material.matchMaterial(name == null ? "" : name);
        if (material == null || (!material.isBlock() && material != Material.AIR)) {
            return fallback;
        }
        return material;
    }

    private static void setBlock(Block block, Material material) {
        BlockData data = material.createBlockData();
        if (!block.getBlockData().matches(data)) {
            // applyPhysics=false keeps the wall stable; clients still receive the change
            block.setBlockData(data, false);
        }
    }
}
