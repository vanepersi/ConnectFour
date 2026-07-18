package dev.genesi.connectfour.command;

import dev.genesi.connectfour.ConnectFourPlugin;
import dev.genesi.connectfour.model.Arena;
import dev.genesi.connectfour.model.GameSession;
import dev.genesi.connectfour.model.PlayerState;
import dev.genesi.connectfour.model.Team;
import org.bukkit.Bukkit;
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
import java.util.stream.Collectors;

public final class ConnectFourCommand implements CommandExecutor, TabCompleter {

    private final ConnectFourPlugin plugin;

    public ConnectFourCommand(ConnectFourPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("connectfour.use")) {
            plugin.getMessageService().send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.getMessageService().component("&eUsage: /" + label + " join <arena> [red|yellow]"));
                    return true;
                }
                Optional<Arena> arena = plugin.getArenaManager().get(args[1]);
                if (arena.isEmpty()) {
                    plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", args[1]));
                    return true;
                }
                Team team = args.length >= 3 ? Team.parse(args[2]) : pickOpenTeam(arena.get());
                if (team == null) {
                    plugin.getMessageService().send(player, "arena-full", Map.of("arena", arena.get().getName()));
                    return true;
                }
                String result = plugin.getGameManager().join(player, arena.get(), team);
                switch (result) {
                    case "ok", "handled" -> {
                    }
                    case "already-playing" -> plugin.getMessageService().send(player, "already-playing");
                    case "arena-not-ready" -> plugin.getMessageService().send(player, "arena-not-ready", Map.of("arena", arena.get().getName()));
                    case "arena-busy" -> plugin.getMessageService().send(player, "arena-busy", Map.of("arena", arena.get().getName()));
                    case "economy-missing" -> plugin.getMessageService().send(player, "economy-missing");
                    default -> {
                    }
                }
            }
            case "leave" -> plugin.getGameManager().leave(player, true);
            case "start" -> plugin.getGameManager().tryStart(player);
            case "points" -> {
                if (!player.hasPermission("connectfour.points")) {
                    plugin.getMessageService().send(player, "no-permission");
                    return true;
                }
                if (args.length >= 2 && player.hasPermission("connectfour.admin")) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        player.sendMessage(plugin.getMessageService().component("&cPlayer not found."));
                        return true;
                    }
                    plugin.getMessageService().send(player, "points-other", Map.of(
                            "player", target.getName(),
                            "points", String.valueOf(plugin.getPointsService().getPoints(target))
                    ));
                } else {
                    plugin.getMessageService().send(player, "points-self", Map.of(
                            "points", String.valueOf(plugin.getPointsService().getPoints(player))
                    ));
                }
            }
            case "arenas" -> {
                String list = plugin.getArenaManager().all().stream()
                        .map(a -> a.getName() + (a.isReady() ? "" : "*"))
                        .collect(Collectors.joining(", "));
                player.sendMessage(plugin.getMessageService().component(
                        "&7Arenas: &e" + (list.isEmpty() ? "(none)" : list)));
            }
            case "info" -> {
                Optional<GameSession> session = plugin.getGameManager().getByPlayer(player.getUniqueId());
                if (session.isEmpty()) {
                    plugin.getMessageService().send(player, "not-playing");
                    return true;
                }
                PlayerState state = session.get().getPlayer(player.getUniqueId());
                player.sendMessage(plugin.getMessageService().component(
                        "&7Arena: &e" + session.get().getArenaName()
                                + " &8| &7Team: " + (state == null || state.getTeam() == null ? "&7?" : state.getTeam().colored())
                                + " &8| &7Turn: " + session.get().getTurn().colored()
                                + " &8| &7State: &f" + session.get().getState()));
            }
            case "help" -> sendHelp(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private Team pickOpenTeam(Arena arena) {
        Optional<GameSession> session = plugin.getGameManager().getByArena(arena.getName());
        if (session.isEmpty()) {
            return Team.YELLOW;
        }
        if (!session.get().hasTeam(Team.YELLOW)) {
            return Team.YELLOW;
        }
        if (!session.get().hasTeam(Team.RED)) {
            return Team.RED;
        }
        return null;
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.getMessageService().component("&6Connect Four"));
        player.sendMessage(plugin.getMessageService().component("&e/cf join <arena> [red|yellow] &7- join a seat"));
        player.sendMessage(plugin.getMessageService().component("&e/cf leave &7- leave the lobby/game"));
        player.sendMessage(plugin.getMessageService().component("&e/cf start &7- skip lobby wait when both ready"));
        player.sendMessage(plugin.getMessageService().component("&e/cf points &7- your arcade points"));
        player.sendMessage(plugin.getMessageService().component("&e/cf arenas &7- list arenas"));
        player.sendMessage(plugin.getMessageService().component("&7Or click the &cRed&7 / &eYellow &7join blocks."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("join", "leave", "start", "points", "arenas", "info", "help"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return filter(args[1], plugin.getArenaManager().all().stream().map(Arena::getName).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("join")) {
            return filter(args[2], Arrays.asList("red", "yellow"));
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
