package dev.genesi.connectfour.board;

import dev.genesi.connectfour.ConnectFourPlugin;
import dev.genesi.connectfour.model.Arena;
import dev.genesi.connectfour.model.Team;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class BoardRenderer {

    private final ConnectFourPlugin plugin;

    public BoardRenderer(ConnectFourPlugin plugin) {
        this.plugin = plugin;
    }

    public Material emptyMaterial() {
        return material("materials.empty", Material.AIR);
    }

    public Material winHighlightMaterial() {
        return material("materials.win-highlight", Material.GLOWSTONE);
    }

    public Material teamMaterial(Team team) {
        return team.materialFromConfig(plugin.getConfig());
    }

    public void clear(Arena arena) {
        BoardGeometry geometry = new BoardGeometry(arena);
        Material empty = emptyMaterial();
        for (int c = 0; c < geometry.getColumns(); c++) {
            for (int r = 0; r < geometry.getRows(); r++) {
                paintCell(geometry, c, r, empty);
            }
        }
    }

    public void paintCell(Arena arena, int column, int row, Team team) {
        paintCell(new BoardGeometry(arena), column, row, team == null ? emptyMaterial() : teamMaterial(team));
    }

    public void paintCell(BoardGeometry geometry, int column, int row, Material material) {
        geometry.forEachBlockInCell(column, row, block -> setBlock(block, material));
    }

    public void paintGrid(Arena arena, Team[][] grid) {
        BoardGeometry geometry = new BoardGeometry(arena);
        for (int c = 0; c < geometry.getColumns(); c++) {
            for (int r = 0; r < geometry.getRows(); r++) {
                Team team = grid[c][r];
                paintCell(geometry, c, r, team == null ? emptyMaterial() : teamMaterial(team));
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
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }
}
