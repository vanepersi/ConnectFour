package dev.genesi.connectfour.listener;

import dev.genesi.connectfour.ConnectFourPlugin;
import dev.genesi.connectfour.board.BoardGeometry;
import dev.genesi.connectfour.model.Arena;
import dev.genesi.connectfour.model.Team;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public final class GameListener implements Listener {

    private final ConnectFourPlugin plugin;

    public GameListener(ConnectFourPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * ignoreCancelled=false — Adventure / WorldGuard often cancel board clicks;
     * we still need to resolve column drops.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR
                && action != Action.LEFT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block != null && plugin.getGameManager().handleBindClick(player, block)) {
            event.setCancelled(true);
            return;
        }

        if (block != null) {
            Optional<Arena> joinArena = plugin.getArenaManager().findByJoinBlock(block);
            if (joinArena.isPresent()) {
                event.setCancelled(true);
                Arena arena = joinArena.get();
                Team team = arena.findJoinTeam(block);
                if (team == null) {
                    return;
                }
                if (!player.hasPermission("connectfour.use")) {
                    plugin.getMessageService().send(player, "no-permission");
                    return;
                }
                String result = plugin.getGameManager().join(player, arena, team);
                switch (result) {
                    case "ok", "handled" -> {
                    }
                    case "already-playing" -> plugin.getMessageService().send(player, "already-playing");
                    case "arena-not-ready" -> plugin.getMessageService().send(player, "arena-not-ready", Map.of("arena", arena.getName()));
                    case "arena-busy" -> plugin.getMessageService().send(player, "arena-busy", Map.of("arena", arena.getName()));
                    case "economy-missing" -> plugin.getMessageService().send(player, "economy-missing");
                    default -> {
                    }
                }
                return;
            }
        }

        var sessionOpt = plugin.getGameManager().getByPlayer(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        Optional<Arena> playing = plugin.getArenaManager().get(sessionOpt.get().getArenaName());
        if (playing.isEmpty()) {
            return;
        }
        Arena arena = playing.get();

        int column = -1;
        if (block != null) {
            column = plugin.getGameManager().resolveColumn(arena, block);
        }
        if (column < 0) {
            try {
                column = new BoardGeometry(arena).columnFromPlayer(player, 12.0);
            } catch (IllegalArgumentException ignored) {
                return;
            }
        }
        if (column < 0) {
            return;
        }

        event.setCancelled(true);
        plugin.getGameManager().handleColumnClick(player, arena, column);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getGameManager().getByPlayer(event.getPlayer().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getPieceItems().isPiece(event.getItemInHand())
                || plugin.getGameManager().getByPlayer(event.getPlayer().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getPieceItems().isPiece(event.getItemDrop().getItemStack())
                && plugin.getGameManager().getByPlayer(event.getPlayer().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (plugin.getGameManager().getByPlayer(player.getUniqueId()).isEmpty()) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (plugin.getPieceItems().isPiece(current) || plugin.getPieceItems().isPiece(cursor)) {
            if (event.getClickedInventory() != null
                    && !event.getClickedInventory().equals(player.getInventory())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.getPieceItems().isPiece(event.getItem().getItemStack())
                && plugin.getGameManager().getByPlayer(player.getUniqueId()).isEmpty()) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().cancelBind(event.getPlayer().getUniqueId());
        plugin.getGameManager().leave(event.getPlayer(), false);
    }
}
