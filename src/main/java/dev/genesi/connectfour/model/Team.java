package dev.genesi.connectfour.model;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public enum Team {
    RED("Red", "&c"),
    YELLOW("Yellow", "&e");

    private final String display;
    private final String legacy;

    Team(String display, String legacy) {
        this.display = display;
        this.legacy = legacy;
    }

    public String display() {
        return display;
    }

    public String legacy() {
        return legacy;
    }

    public String colored() {
        return legacy + display;
    }

    public Team opposite() {
        return this == RED ? YELLOW : RED;
    }

    public Material materialFromConfig(FileConfiguration config) {
        String key = this == RED ? "materials.red" : "materials.yellow";
        String name = config.getString(key, this == RED ? "RED_CONCRETE" : "YELLOW_CONCRETE");
        Material material = Material.matchMaterial(name == null ? "" : name);
        if (material == null || !material.isBlock()) {
            return this == RED ? Material.RED_CONCRETE : Material.YELLOW_CONCRETE;
        }
        return material;
    }

    public static Team parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "red", "r" -> RED;
            case "yellow", "y", "yel" -> YELLOW;
            default -> null;
        };
    }
}
