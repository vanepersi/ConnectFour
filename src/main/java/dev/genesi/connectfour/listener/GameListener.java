package dev.genesi.connectfour.listener;

import dev.genesi.connectfour.ConnectFourPlugin;
import dev.genesi.connectfour.model.Arena;
import dev.genesi.connectfour.model.Team;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();

        if (plugin.getGameManager().handleBindClick(player, block)) {
            event.setCancelled(true);
            return;
        }

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

        Optional<Arena> columnArena = plugin.getArenaManager().findByColumnButton(block);
        if (columnArena.isEmpty()) {
            var sessionOpt = plugin.getGameManager().getByPlayer(player.getUniqueId());
            if (sessionOpt.isPresent()) {
                Optional<Arena> playing = plugin.getArenaManager().get(sessionOpt.get().getArenaName());
                if (playing.isPresent()) {
                    int column = plugin.getGameManager().resolveColumn(playing.get(), block);
                    if (column >= 0) {
                        event.setCancelled(true);
                        plugin.getGameManager().handleColumnClick(player, playing.get(), column);
                    }
                }
            }
            return;
        }

        event.setCancelled(true);
        Arena arena = columnArena.get();
        int column = arena.findColumnButton(block);
        if (column >= 0) {
            plugin.getGameManager().handleColumnClick(player, arena, column);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getPieceItems().isPiece(event.getItemInHand())) {
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
            // Allow hotbar swaps within player inventory only
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(player.getInventory())
                    && event.getView().getTopInventory() != player.getInventory()) {
                // clicking into chest/etc with piece
                if (plugin.getPieceItems().isPiece(current) || plugin.getPieceItems().isPiece(cursor)) {
                    event.setCancelled(true);
                }
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
