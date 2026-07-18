package dev.genesi.connectfour.board;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Original wall-cell materials captured at setup so clears never punch holes with AIR.
 */
public final class BoardSnapshot {

    private final int columns;
    private final int rows;
    private final int blocksPerCell;
    /** materials[column][row][blockIndex] matching {@link BoardGeometry#forEachBlockInCell} order. */
    private final Material[][][] materials;

    public BoardSnapshot(int columns, int rows, int blocksPerCell, Material[][][] materials) {
        this.columns = columns;
        this.rows = rows;
        this.blocksPerCell = blocksPerCell;
        this.materials = materials;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public int getBlocksPerCell() {
        return blocksPerCell;
    }

    public Material[] materialsFor(int column, int row) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            return new Material[0];
        }
        Material[] src = materials[column][row];
        if (src == null) {
            return new Material[0];
        }
        Material[] copy = new Material[src.length];
        System.arraycopy(src, 0, copy, 0, src.length);
        return copy;
    }

    public boolean matches(BoardGeometry geometry) {
        return geometry.getColumns() == columns
                && geometry.getRows() == rows
                && blocksPerCell == geometry.getCellWidth() * geometry.getCellHeight();
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("columns", columns);
        map.put("rows", rows);
        map.put("blocks-per-cell", blocksPerCell);
        List<String> flat = new ArrayList<>(columns * rows * blocksPerCell);
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows; r++) {
                Material[] cell = materials[c][r];
                for (int i = 0; i < blocksPerCell; i++) {
                    Material mat = cell != null && i < cell.length && cell[i] != null
                            ? cell[i]
                            : Material.AIR;
                    flat.add(mat.name());
                }
            }
        }
        map.put("materials", flat);
        return map;
    }

    public static BoardSnapshot deserialize(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        int columns = section.getInt("columns", 0);
        int rows = section.getInt("rows", 0);
        int blocksPerCell = section.getInt("blocks-per-cell", 0);
        List<String> flat = section.getStringList("materials");
        if (columns <= 0 || rows <= 0 || blocksPerCell <= 0
                || flat.size() < columns * rows * blocksPerCell) {
            return null;
        }
        Material[][][] materials = new Material[columns][rows][blocksPerCell];
        int index = 0;
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows; r++) {
                for (int i = 0; i < blocksPerCell; i++) {
                    materials[c][r][i] = parseMaterial(flat.get(index++));
                }
            }
        }
        return new BoardSnapshot(columns, rows, blocksPerCell, materials);
    }

    private static Material parseMaterial(String name) {
        if (name == null || name.isBlank()) {
            return Material.AIR;
        }
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? Material.AIR : material;
    }
}
