package dev.genesi.connectfour.manager;

import dev.genesi.connectfour.ConnectFourPlugin;
import dev.genesi.connectfour.model.Arena;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class ArenaManager {

    private final ConnectFourPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private File file;
    private FileConfiguration yaml;

    public ArenaManager(ConnectFourPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        file = new File(plugin.getDataFolder(), "arenas.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create arenas.yml", e);
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("arenas");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                arenas.put(key.toLowerCase(Locale.ROOT), Arena.deserialize(key, section.getConfigurationSection(key)));
            }
        }
        applyDefaultsFromConfig();
    }

    private void applyDefaultsFromConfig() {
        int columns = plugin.getConfig().getInt("board-defaults.columns", Arena.DEFAULT_COLUMNS);
        int rows = plugin.getConfig().getInt("board-defaults.rows", Arena.DEFAULT_ROWS);
        int cellWidth = plugin.getConfig().getInt("board-defaults.cell-width", 1);
        int cellHeight = plugin.getConfig().getInt("board-defaults.cell-height", 1);
        int columnGap = plugin.getConfig().getInt("board-defaults.column-gap", 0);
        int rowGap = plugin.getConfig().getInt("board-defaults.row-gap", 0);
        for (Arena arena : arenas.values()) {
            if (arena.getColumns() <= 0) {
                arena.setColumns(columns);
            }
            if (arena.getRows() <= 0) {
                arena.setRows(rows);
            }
            if (arena.getCellWidth() <= 0) {
                arena.setCellWidth(cellWidth);
            }
            if (arena.getCellHeight() <= 0) {
                arena.setCellHeight(cellHeight);
            }
            if (arena.getColumnGap() < 0) {
                arena.setColumnGap(columnGap);
            }
            if (arena.getRowGap() < 0) {
                arena.setRowGap(rowGap);
            }
        }
    }

    public void save() {
        if (yaml == null) {
            load();
        }
        yaml.set("arenas", null);
        for (Arena arena : arenas.values()) {
            yaml.createSection("arenas." + arena.getName(), arena.serialize());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml", e);
        }
    }

    public Optional<Arena> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(arenas.get(name.toLowerCase(Locale.ROOT)));
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public Arena create(String name) {
        Arena arena = new Arena(name);
        arena.setColumns(plugin.getConfig().getInt("board-defaults.columns", Arena.DEFAULT_COLUMNS));
        arena.setRows(plugin.getConfig().getInt("board-defaults.rows", Arena.DEFAULT_ROWS));
        arena.setCellWidth(plugin.getConfig().getInt("board-defaults.cell-width", 1));
        arena.setCellHeight(plugin.getConfig().getInt("board-defaults.cell-height", 1));
        arena.setColumnGap(plugin.getConfig().getInt("board-defaults.column-gap", 0));
        arena.setRowGap(plugin.getConfig().getInt("board-defaults.row-gap", 0));
        arenas.put(arena.getName(), arena);
        save();
        return arena;
    }

    public boolean delete(String name) {
        Arena removed = arenas.remove(name.toLowerCase(Locale.ROOT));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public double resolveEntryFee(Arena arena) {
        if (arena.getEntryFeeOverride() != null) {
            return arena.getEntryFeeOverride();
        }
        return plugin.getConfig().getDouble("entry-fee", 0.0);
    }

    public Optional<Arena> findByJoinBlock(org.bukkit.block.Block block) {
        for (Arena arena : arenas.values()) {
            if (arena.findJoinTeam(block) != null) {
                return Optional.of(arena);
            }
        }
        return Optional.empty();
    }

    public Optional<Arena> findByColumnButton(org.bukkit.block.Block block) {
        for (Arena arena : arenas.values()) {
            if (arena.findColumnButton(block) >= 0) {
                return Optional.of(arena);
            }
        }
        return Optional.empty();
    }
}
