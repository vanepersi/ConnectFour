package dev.genesi.connectfour.command;

import dev.genesi.connectfour.ConnectFourPlugin;
import dev.genesi.connectfour.model.Arena;
import dev.genesi.connectfour.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ConnectFourAdminCommand implements CommandExecutor, TabCompleter {

    private final ConnectFourPlugin plugin;

    public ConnectFourAdminCommand(ConnectFourPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("connectfour.admin")) {
            plugin.getMessageService().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessageService().component("&eUsage: /" + label + " create <arena>"));
                    return true;
                }
                if (plugin.getArenaManager().get(args[1]).isPresent()) {
                    sender.sendMessage(plugin.getMessageService().component("&cArena already exists."));
                    return true;
                }
                Arena arena = plugin.getArenaManager().create(args[1]);
                plugin.getMessageService().send(sender, "arena-created", Map.of("arena", arena.getName()));
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessageService().component("&eUsage: /" + label + " delete <arena>"));
                    return true;
                }
                plugin.getGameManager().forceStop(args[1]);
                if (plugin.getArenaManager().delete(args[1])) {
                    plugin.getMessageService().send(sender, "arena-deleted", Map.of("arena", args[1].toLowerCase(Locale.ROOT)));
                } else {
                    plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
                }
            }
            case "setlobby" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                arena.setLobby(player.getLocation());
                plugin.getArenaManager().save();
                plugin.getMessageService().send(sender, "lobby-set", Map.of("arena", arena.getName()));
            }
            case "setorigin" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                Block target = player.getTargetBlockExact(8);
                if (target == null) {
                    plugin.getMessageService().send(sender, "look-at-block");
                    return true;
                }
                arena.setOrigin(target.getLocation());
                trySnapshot(arena, sender);
                plugin.getArenaManager().save();
                plugin.getMessageService().send(sender, "origin-set", Map.of("arena", arena.getName()));
            }
            case "setfacing" -> {
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                BlockFace face;
                if (args.length >= 3) {
                    try {
                        face = BlockFace.valueOf(args[2].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        sender.sendMessage(plugin.getMessageService().component("&cUse NORTH SOUTH EAST or WEST."));
                        return true;
                    }
                } else if (sender instanceof Player player) {
                    face = yawToFace(player.getLocation().getYaw());
                } else {
                    sender.sendMessage(plugin.getMessageService().component("&eUsage: /" + label + " setfacing <arena> <north|south|east|west>"));
                    return true;
                }
                if (face != BlockFace.NORTH && face != BlockFace.SOUTH && face != BlockFace.EAST && face != BlockFace.WEST) {
                    sender.sendMessage(plugin.getMessageService().component("&cFacing must be NORTH SOUTH EAST or WEST."));
                    return true;
                }
                arena.setFacing(face);
                trySnapshot(arena, sender);
                plugin.getArenaManager().save();
                plugin.getMessageService().send(sender, "facing-set", Map.of("arena", arena.getName(), "facing", face.name()));
            }
            case "setjoin" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageService().component("&eUsage: /" + label + " setjoin <arena> <red|yellow>"));
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                Team team = Team.parse(args[2]);
                if (team == null) {
                    sender.sendMessage(plugin.getMessageService().component("&cTeam must be red or yellow."));
                    return true;
                }
                Block target = player.getTargetBlockExact(8);
                if (target == null) {
                    plugin.getMessageService().send(sender, "look-at-block");
                    return true;
                }
                arena.setJoin(team, target.getLocation());
                plugin.getArenaManager().save();
                plugin.getMessageService().send(sender, "join-set", Map.of("arena", arena.getName(), "team", team.colored()));
            }
            case "setcolumn" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageService().component("&eUsage: /" + label + " setcolumn <arena> <1-7>"));
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                int column;
                try {
                    column = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(plugin.getMessageService().component("&cColumn must be 1-7."));
                    return true;
                }
                if (column < 1 || column > 7) {
                    sender.sendMessage(plugin.getMessageService().component("&cColumn must be 1-7."));
                    return true;
                }
                Block target = player.getTargetBlockExact(8);
                if (target == null) {
                    plugin.getMessageService().send(sender, "look-at-block");
                    return true;
                }
                arena.setColumnButton(column - 1, target.getLocation());
                plugin.getArenaManager().save();
                plugin.getMessageService().send(sender, "column-set", Map.of(
                        "arena", arena.getName(),
                        "column", String.valueOf(column)
                ));
            }
            case "bindcolumns" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                plugin.getGameManager().startBind(player, arena);
            }
            case "setcellsize" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.getMessageService().component(
                            "&eUsage: /" + label + " setcellsize <arena> <width> <height> [columnGap] [rowGap]"));
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                try {
                    arena.setCellWidth(Integer.parseInt(args[2]));
                    arena.setCellHeight(Integer.parseInt(args[3]));
                    if (args.length >= 5) {
                        arena.setColumnGap(Integer.parseInt(args[4]));
                    }
                    if (args.length >= 6) {
                        arena.setRowGap(Integer.parseInt(args[5]));
                    }
                } catch (NumberFormatException ex) {
                    sender.sendMessage(plugin.getMessageService().component("&cNumbers only."));
                    return true;
                }
                plugin.getArenaManager().save();
                trySnapshot(arena, sender);
                plugin.getMessageService().send(sender, "cell-size-set", Map.of(
                        "arena", arena.getName(),
                        "width", String.valueOf(arena.getCellWidth()),
                        "height", String.valueOf(arena.getCellHeight()),
                        "cg", String.valueOf(arena.getColumnGap()),
                        "rg", String.valueOf(arena.getRowGap())
                ));
            }
            case "snapshotboard" -> {
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                if (arena.getOrigin() == null || arena.getOrigin().getWorld() == null) {
                    plugin.getMessageService().send(sender, "arena-not-ready", Map.of("arena", arena.getName()));
                    return true;
                }
                plugin.getGameManager().getRenderer().capture(arena);
                plugin.getArenaManager().save();
                plugin.getMessageService().send(sender, "snapshot-saved", Map.of("arena", arena.getName()));
            }
            case "preview" -> {
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                if (arena.getOrigin() == null) {
                    plugin.getMessageService().send(sender, "arena-not-ready", Map.of("arena", arena.getName()));
                    return true;
                }
                plugin.getGameManager().getRenderer().preview(arena, 100L);
                plugin.getMessageService().send(sender, "preview-started", Map.of("arena", arena.getName()));
            }
            case "testdrop" -> {
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                if (arena.getOrigin() == null) {
                    plugin.getMessageService().send(sender, "arena-not-ready", Map.of("arena", arena.getName()));
                    return true;
                }
                int column = 0;
                if (args.length >= 3) {
                    try {
                        column = Integer.parseInt(args[2]) - 1;
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(plugin.getMessageService().component("&cColumn must be 1-7."));
                        return true;
                    }
                }
                Team team = Team.YELLOW;
                if (args.length >= 4) {
                    Team parsed = Team.parse(args[3]);
                    if (parsed != null) {
                        team = parsed;
                    }
                }
                if (column < 0 || column >= arena.getColumns()) {
                    sender.sendMessage(plugin.getMessageService().component("&cColumn must be 1-" + arena.getColumns() + "."));
                    return true;
                }
                plugin.getGameManager().getRenderer().testDrop(arena, column, team, 80L);
                plugin.getMessageService().send(sender, "testdrop-started", Map.of(
                        "arena", arena.getName(),
                        "column", String.valueOf(column + 1),
                        "team", team.colored()
                ));
            }
            case "clearboard" -> {
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                if (arena.getOrigin() == null) {
                    plugin.getMessageService().send(sender, "arena-not-ready", Map.of("arena", arena.getName()));
                    return true;
                }
                if (!arena.hasSnapshot()) {
                    sender.sendMessage(plugin.getMessageService().component(
                            "&eNo snapshot yet — capturing current board first, then clearing pieces only if needed."));
                    plugin.getGameManager().getRenderer().capture(arena);
                    plugin.getArenaManager().save();
                }
                plugin.getGameManager().getRenderer().clear(arena);
                plugin.getMessageService().send(sender, "board-cleared", Map.of("arena", arena.getName()));
            }
            case "setfee" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageService().component("&eUsage: /" + label + " setfee <arena> <amount>"));
                    return true;
                }
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                try {
                    arena.setEntryFeeOverride(Double.parseDouble(args[2]));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(plugin.getMessageService().component("&cInvalid amount."));
                    return true;
                }
                plugin.getArenaManager().save();
                plugin.getMessageService().send(sender, "fee-set", Map.of(
                        "arena", arena.getName(),
                        "fee", String.valueOf(arena.getEntryFeeOverride())
                ));
            }
            case "forcestart" -> {
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                plugin.getGameManager().forceStart(arena);
                plugin.getMessageService().send(sender, "force-started", Map.of("arena", arena.getName()));
            }
            case "forcestop" -> {
                Arena arena = requireArena(sender, args, 1);
                if (arena == null) {
                    return true;
                }
                plugin.getGameManager().forceStop(arena.getName());
                plugin.getMessageService().send(sender, "force-stopped", Map.of("arena", arena.getName()));
            }
            case "reload" -> {
                plugin.reloadPlugin();
                plugin.getMessageService().send(sender, "reloaded");
            }
            case "points" -> handlePoints(sender, args);
            case "list" -> {
                for (Arena arena : plugin.getArenaManager().all()) {
                    sender.sendMessage(plugin.getMessageService().component(
                            "&e" + arena.getName()
                                    + (arena.isReady() ? " &aREADY" : " &cINCOMPLETE")
                                    + " &7face=" + arena.getFacing()
                                    + " cell=" + arena.getCellWidth() + "x" + arena.getCellHeight()
                    ));
                }
            }
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handlePoints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageService().component("&eUsage: /cfadmin points <add|set|remove|redeem> <player> [amount]"));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(plugin.getMessageService().component("&cPlayer not found."));
            return;
        }
        int amount = 0;
        if (!action.equals("get") && args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.getMessageService().component("&cInvalid amount."));
                return;
            }
        }
        switch (action) {
            case "add" -> {
                int next = plugin.getPointsService().addPoints(target, amount);
                plugin.getMessageService().send(sender, "points-added", Map.of(
                        "amount", String.valueOf(amount),
                        "player", target.getName(),
                        "points", String.valueOf(next)
                ));
            }
            case "set" -> {
                plugin.getPointsService().setPoints(target, amount);
                plugin.getMessageService().send(sender, "points-set", Map.of(
                        "player", target.getName(),
                        "points", String.valueOf(amount)
                ));
            }
            case "remove", "redeem" -> {
                if (!plugin.getPointsService().removePoints(target, amount)) {
                    plugin.getMessageService().send(sender, "not-enough-points", Map.of(
                            "player", target.getName(),
                            "points", String.valueOf(plugin.getPointsService().getPoints(target))
                    ));
                    return;
                }
                String key = action.equals("redeem") ? "redeem-success" : "points-removed";
                plugin.getMessageService().send(sender, key, Map.of(
                        "amount", String.valueOf(amount),
                        "player", target.getName(),
                        "points", String.valueOf(plugin.getPointsService().getPoints(target))
                ));
            }
            default -> sender.sendMessage(plugin.getMessageService().component("&eUsage: points <add|set|remove|redeem> <player> <amount>"));
        }
    }

    private void trySnapshot(Arena arena, CommandSender sender) {
        if (arena.getOrigin() == null || arena.getOrigin().getWorld() == null) {
            return;
        }
        try {
            plugin.getGameManager().getRenderer().capture(arena);
            plugin.getMessageService().send(sender, "snapshot-saved", Map.of("arena", arena.getName()));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(plugin.getMessageService().component("&cCould not snapshot board: " + ex.getMessage()));
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.getMessageService().send(sender, "players-only");
        return null;
    }

    private Arena requireArena(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(plugin.getMessageService().component("&cSpecify an arena name."));
            return null;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[index]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[index]));
            return null;
        }
        return arena.get();
    }

    private static BlockFace yawToFace(float yaw) {
        float rot = (yaw % 360 + 360) % 360;
        if (rot >= 315 || rot < 45) {
            return BlockFace.SOUTH;
        }
        if (rot < 135) {
            return BlockFace.WEST;
        }
        if (rot < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageService().component("&6Connect Four Admin"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin create <arena>"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin setorigin <arena> &7- look at BOTTOM-LEFT empty cell only"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin setfacing <arena> [N/S/E/W] &7- stand facing the board"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin setjoin <arena> <red|yellow> &7- look at join block"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin setcellsize <arena> <w> <h> [cg] [rg] &7- giant discs"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin snapshotboard <arena> &7- save empty wall state"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin preview <arena> &7- flash yellow glass over full 7×6 grid"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin testdrop <arena> [col] [red|yellow] &7- animate a test disc"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin bindcolumns <arena> &7- optional 7 column buttons"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin clearboard <arena>"));
        sender.sendMessage(plugin.getMessageService().component("&e/cfadmin list|reload|forcestart|forcestop"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("connectfour.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], Arrays.asList(
                    "create", "delete", "setlobby", "setorigin", "setfacing", "setjoin",
                    "setcolumn", "bindcolumns", "setcellsize", "snapshotboard", "preview", "testdrop",
                    "clearboard", "setfee", "forcestart", "forcestop", "reload", "points", "list", "help"
            ));
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("delete", "setlobby", "setorigin", "setfacing", "setjoin", "setcolumn",
                    "bindcolumns", "setcellsize", "snapshotboard", "preview", "testdrop",
                    "clearboard", "setfee", "forcestart", "forcestop").contains(sub)) {
                return filter(args[1], plugin.getArenaManager().all().stream().map(Arena::getName).toList());
            }
            if (sub.equals("points")) {
                return filter(args[1], Arrays.asList("add", "set", "remove", "redeem"));
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setjoin")) {
            return filter(args[2], Arrays.asList("red", "yellow"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setfacing")) {
            return filter(args[2], Arrays.asList("NORTH", "SOUTH", "EAST", "WEST"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setcolumn")) {
            return filter(args[2], Arrays.asList("1", "2", "3", "4", "5", "6", "7"));
        }
        return List.of();
    }

    private static List<String> filter(String input, List<String> options) {
        String needle = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(option);
            }
        }
        return out;
    }
}
