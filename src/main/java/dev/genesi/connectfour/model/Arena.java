package dev.genesi.connectfour.model;

import dev.genesi.connectfour.board.BoardSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Arena {

    public static final int DEFAULT_COLUMNS = 7;
    public static final int DEFAULT_ROWS = 6;

    private final String name;
    private Location lobby;
    private Location origin;
    private BlockFace facing = BlockFace.NORTH;
    private Location redJoin;
    private Location yellowJoin;
    private final Location[] columnButtons = new Location[DEFAULT_COLUMNS];
    private int columns = DEFAULT_COLUMNS;
    private int rows = DEFAULT_ROWS;
    private int cellWidth = 1;
    private int cellHeight = 1;
    private int columnGap = 0;
    private int rowGap = 0;
    private Double entryFeeOverride;
    private BoardSnapshot snapshot;

    public Arena(String name) {
        this.name = name.toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public Location getLobby() {
        return cloneLocation(lobby);
    }

    public void setLobby(Location lobby) {
        this.lobby = cloneLocation(lobby);
    }

    public Location getOrigin() {
        return cloneLocation(origin);
    }

    public void setOrigin(Location origin) {
        this.origin = cloneLocation(origin);
    }

    public BlockFace getFacing() {
        return facing;
    }

    public void setFacing(BlockFace facing) {
        this.facing = facing == null ? BlockFace.NORTH : facing;
    }

    public Location getRedJoin() {
        return cloneLocation(redJoin);
    }

    public void setRedJoin(Location redJoin) {
        this.redJoin = cloneLocation(redJoin);
    }

    public Location getYellowJoin() {
        return cloneLocation(yellowJoin);
    }

    public void setYellowJoin(Location yellowJoin) {
        this.yellowJoin = cloneLocation(yellowJoin);
    }

    public Location getJoin(Team team) {
        return team == Team.RED ? getRedJoin() : getYellowJoin();
    }

    public void setJoin(Team team, Location location) {
        if (team == Team.RED) {
            setRedJoin(location);
        } else {
            setYellowJoin(location);
        }
    }

    public Location getColumnButton(int index) {
        if (index < 0 || index >= columnButtons.length) {
            return null;
        }
        return cloneLocation(columnButtons[index]);
    }

    public void setColumnButton(int index, Location location) {
        if (index < 0 || index >= columnButtons.length) {
            throw new IndexOutOfBoundsException("column " + index);
        }
        columnButtons[index] = cloneLocation(location);
    }

    public Location[] getColumnButtons() {
        Location[] copy = new Location[columnButtons.length];
        for (int i = 0; i < columnButtons.length; i++) {
            copy[i] = cloneLocation(columnButtons[i]);
        }
        return copy;
    }

    public int findColumnButton(Block block) {
        if (block == null || block.getWorld() == null) {
            return -1;
        }
        for (int i = 0; i < columnButtons.length; i++) {
            Location button = columnButtons[i];
            if (button == null || button.getWorld() == null) {
                continue;
            }
            if (button.getWorld().equals(block.getWorld())
                    && button.getBlockX() == block.getX()
                    && button.getBlockY() == block.getY()
                    && button.getBlockZ() == block.getZ()) {
                return i;
            }
        }
        return -1;
    }

    public Team findJoinTeam(Block block) {
        if (matchesBlock(redJoin, block)) {
            return Team.RED;
        }
        if (matchesBlock(yellowJoin, block)) {
            return Team.YELLOW;
        }
        return null;
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = Math.max(4, columns);
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = Math.max(4, rows);
    }

    public int getCellWidth() {
        return cellWidth;
    }

    public void setCellWidth(int cellWidth) {
        this.cellWidth = Math.max(1, cellWidth);
    }

    public int getCellHeight() {
        return cellHeight;
    }

    public void setCellHeight(int cellHeight) {
        this.cellHeight = Math.max(1, cellHeight);
    }

    public int getColumnGap() {
        return columnGap;
    }

    public void setColumnGap(int columnGap) {
        this.columnGap = Math.max(0, columnGap);
    }

    public int getRowGap() {
        return rowGap;
    }

    public void setRowGap(int rowGap) {
        this.rowGap = Math.max(0, rowGap);
    }

    public Double getEntryFeeOverride() {
        return entryFeeOverride;
    }

    public void setEntryFeeOverride(Double entryFeeOverride) {
        this.entryFeeOverride = entryFeeOverride;
    }

    public BoardSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(BoardSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public boolean hasSnapshot() {
        return snapshot != null;
    }

    public boolean isReady() {
        return lobby != null && lobby.getWorld() != null
                && origin != null && origin.getWorld() != null
                && facing != null && facing.isCartesian() && facing != BlockFace.UP && facing != BlockFace.DOWN
                && redJoin != null && redJoin.getWorld() != null
                && yellowJoin != null && yellowJoin.getWorld() != null;
    }

    public int boundColumnButtonCount() {
        int count = 0;
        for (Location button : columnButtons) {
            if (button != null && button.getWorld() != null) {
                count++;
            }
        }
        return count;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lobby", serializeLocation(lobby));
        map.put("origin", serializeLocation(origin));
        map.put("facing", facing.name());
        map.put("red-join", serializeLocation(redJoin));
        map.put("yellow-join", serializeLocation(yellowJoin));
        map.put("columns", columns);
        map.put("rows", rows);
        map.put("cell-width", cellWidth);
        map.put("cell-height", cellHeight);
        map.put("column-gap", columnGap);
        map.put("row-gap", rowGap);
        Map<String, Object> buttons = new LinkedHashMap<>();
        for (int i = 0; i < columnButtons.length; i++) {
            if (columnButtons[i] != null) {
                buttons.put(String.valueOf(i + 1), serializeLocation(columnButtons[i]));
            }
        }
        if (!buttons.isEmpty()) {
            map.put("column-buttons", buttons);
        }
        if (entryFeeOverride != null) {
            map.put("entry-fee", entryFeeOverride);
        }
        if (snapshot != null) {
            map.put("board-snapshot", snapshot.serialize());
        }
        return map;
    }

    public static Arena deserialize(String name, ConfigurationSection section) {
        Arena arena = new Arena(name);
        if (section == null) {
            return arena;
        }
        arena.lobby = deserializeLocation(section.getConfigurationSection("lobby"));
        arena.origin = deserializeLocation(section.getConfigurationSection("origin"));
        String facingName = section.getString("facing", "NORTH");
        try {
            arena.facing = BlockFace.valueOf(facingName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            arena.facing = BlockFace.NORTH;
        }
        arena.redJoin = deserializeLocation(section.getConfigurationSection("red-join"));
        arena.yellowJoin = deserializeLocation(section.getConfigurationSection("yellow-join"));
        arena.columns = section.getInt("columns", DEFAULT_COLUMNS);
        arena.rows = section.getInt("rows", DEFAULT_ROWS);
        arena.cellWidth = Math.max(1, section.getInt("cell-width", 1));
        arena.cellHeight = Math.max(1, section.getInt("cell-height", 1));
        arena.columnGap = Math.max(0, section.getInt("column-gap", 0));
        arena.rowGap = Math.max(0, section.getInt("row-gap", 0));
        ConfigurationSection buttons = section.getConfigurationSection("column-buttons");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                try {
                    int index = Integer.parseInt(key) - 1;
                    if (index >= 0 && index < arena.columnButtons.length) {
                        arena.columnButtons[index] = deserializeLocation(buttons.getConfigurationSection(key));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (section.contains("entry-fee")) {
            arena.entryFeeOverride = section.getDouble("entry-fee");
        }
        arena.snapshot = BoardSnapshot.deserialize(section.getConfigurationSection("board-snapshot"));
        return arena;
    }

    private static boolean matchesBlock(Location location, Block block) {
        if (location == null || block == null || location.getWorld() == null || block.getWorld() == null) {
            return false;
        }
        return location.getWorld().equals(block.getWorld())
                && location.getBlockX() == block.getX()
                && location.getBlockY() == block.getY()
                && location.getBlockZ() == block.getZ();
    }

    private static Map<String, Object> serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getBlockX());
        map.put("y", location.getBlockY());
        map.put("z", location.getBlockZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    private static Location deserializeLocation(ConfigurationSection section) {
        if (section == null || !section.contains("world")) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world"));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Arena arena)) {
            return false;
        }
        return Objects.equals(name, arena.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
