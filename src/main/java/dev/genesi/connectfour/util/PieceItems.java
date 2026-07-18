package dev.genesi.connectfour.util;

import dev.genesi.connectfour.ConnectFourPlugin;
import dev.genesi.connectfour.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

public final class PieceItems {

    public static final String PDC_KEY = "piece_team";

    private final ConnectFourPlugin plugin;
    private final NamespacedKey teamKey;

    public PieceItems(ConnectFourPlugin plugin) {
        this.plugin = plugin;
        this.teamKey = new NamespacedKey(plugin, PDC_KEY);
    }

    public ItemStack create(Team team, int amount) {
        Material material = itemMaterial(team);
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString(
                    team == Team.RED ? "pieces.red.display-name" : "pieces.yellow.display-name",
                    team == Team.RED ? "&cRed Disc" : "&eYellow Disc"
            );
            meta.displayName(legacy(name));
            List<String> lore = plugin.getConfig().getStringList(
                    team == Team.RED ? "pieces.red.lore" : "pieces.yellow.lore"
            );
            if (lore.isEmpty()) {
                lore = List.of("&7Right-click a column to drop.");
            }
            meta.lore(lore.stream().map(this::legacy).toList());
            meta.getPersistentDataContainer().set(teamKey, PersistentDataType.STRING, team.name());
            meta.setUnbreakable(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isPiece(ItemStack stack) {
        return teamOf(stack) != null;
    }

    public boolean isPiece(ItemStack stack, Team team) {
        Team found = teamOf(stack);
        return found != null && found == team;
    }

    public Team teamOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(teamKey, PersistentDataType.STRING);
        return Team.parse(raw);
    }

    public Material itemMaterial(Team team) {
        String path = team == Team.RED ? "pieces.red.material" : "pieces.yellow.material";
        String fallback = team == Team.RED ? "RED_CONCRETE" : "YELLOW_CONCRETE";
        String name = plugin.getConfig().getString(path, fallback);
        Material material = Material.matchMaterial(name == null ? "" : name.toUpperCase(Locale.ROOT));
        if (material == null || material.isAir()) {
            return team == Team.RED ? Material.RED_CONCRETE : Material.YELLOW_CONCRETE;
        }
        return material;
    }

    public int startingAmount() {
        return Math.max(1, plugin.getConfig().getInt("pieces.starting-amount", 21));
    }

    private Component legacy(String input) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input == null ? "" : input);
    }
}
