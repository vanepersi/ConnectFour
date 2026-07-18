package dev.genesi.connectfour.manager;

import dev.genesi.connectfour.ConnectFourPlugin;
import dev.genesi.connectfour.board.BoardGeometry;
import dev.genesi.connectfour.board.BoardLogic;
import dev.genesi.connectfour.board.BoardRenderer;
import dev.genesi.connectfour.model.Arena;
import dev.genesi.connectfour.model.GameSession;
import dev.genesi.connectfour.model.PlayerState;
import dev.genesi.connectfour.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class GameManager {

    private final ConnectFourPlugin plugin;
    private final BoardRenderer renderer;
    private final Map<String, GameSession> byArena = new HashMap<>();
    private final Map<UUID, GameSession> byPlayer = new HashMap<>();
    private final Map<UUID, BindSession> binders = new HashMap<>();

    public GameManager(ConnectFourPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new BoardRenderer(plugin);
    }

    public BoardRenderer getRenderer() {
        return renderer;
    }

    public Optional<GameSession> getByPlayer(UUID uuid) {
        return Optional.ofNullable(byPlayer.get(uuid));
    }

    public Optional<GameSession> getByArena(String arenaName) {
        return Optional.ofNullable(byArena.get(arenaName.toLowerCase(Locale.ROOT)));
    }

    public boolean isBusy(String arenaName) {
        GameSession session = byArena.get(arenaName.toLowerCase(Locale.ROOT));
        return session != null
                && session.getState() != GameSession.State.WAITING
                && session.getState() != GameSession.State.LOBBY_COUNTDOWN;
    }

    public String join(Player player, Arena arena, Team team) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            return "already-playing";
        }
        if (!arena.isReady()) {
            return "arena-not-ready";
        }
        if (team == null) {
            return "arena-not-ready";
        }

        GameSession session = byArena.computeIfAbsent(
                arena.getName(),
                name -> new GameSession(name, arena.getColumns(), arena.getRows())
        );
        if (session.getState() != GameSession.State.WAITING && session.getState() != GameSession.State.LOBBY_COUNTDOWN) {
            return "arena-busy";
        }
        if (session.hasTeam(team)) {
            plugin.getMessageService().send(player, "seat-taken", Map.of("team", team.colored()));
            return "handled";
        }
        if (session.playerCount() >= 2) {
            plugin.getMessageService().send(player, "arena-full", Map.of("arena", arena.getName()));
            return "handled";
        }

        double fee = plugin.getArenaManager().resolveEntryFee(arena);
        if (fee > 0) {
            if (!plugin.getEconomyService().isReady()) {
                return "economy-missing";
            }
            if (!plugin.getEconomyService().has(player, fee) && !player.hasPermission("connectfour.bypass.fee")) {
                plugin.getMessageService().send(player, "not-enough-money", Map.of(
                        "amount", plugin.getEconomyService().format(fee),
                        "balance", plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player))
                ));
                return "handled";
            }
            if (!plugin.getEconomyService().charge(player, fee)) {
                plugin.getMessageService().send(player, "not-enough-money", Map.of(
                        "amount", plugin.getEconomyService().format(fee),
                        "balance", plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player))
                ));
                return "handled";
            }
        }

        PlayerState state = new PlayerState(player);
        state.setTeam(team);
        snapshotPlayer(player, state);
        session.getPlayers().put(player.getUniqueId(), state);
        byPlayer.put(player.getUniqueId(), session);

        Location lobby = arena.getLobby();
        if (lobby != null) {
            player.teleport(lobby);
        }

        plugin.getMessageService().send(player, "joined-waiting", Map.of(
                "arena", arena.getName(),
                "team", team.colored(),
                "count", String.valueOf(session.playerCount())
        ));
        broadcast(session, "joined-waiting", Map.of(
                "arena", arena.getName(),
                "team", team.colored(),
                "count", String.valueOf(session.playerCount())
        ), player.getUniqueId());

        maybeStartLobbyCountdown(session, arena);
        return "ok";
    }

    public boolean leave(Player player, boolean announce) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null) {
            if (announce) {
                plugin.getMessageService().send(player, "not-playing");
            }
            return false;
        }

        if (session.getState() == GameSession.State.PLAYING
                || session.getState() == GameSession.State.START_COUNTDOWN
                || session.getState() == GameSession.State.ANIMATING) {
            PlayerState quitter = session.getPlayer(player.getUniqueId());
            Team winnerTeam = quitter == null || quitter.getTeam() == null ? null : quitter.getTeam().opposite();
            PlayerState winner = winnerTeam == null ? null : session.playerWithTeam(winnerTeam);
            if (winner != null) {
                Player winnerPlayer = Bukkit.getPlayer(winner.getUuid());
                if (winnerPlayer != null) {
                    plugin.getMessageService().send(winnerPlayer, "opponent-quit", Map.of("player", player.getName()));
                }
                endGame(session, winnerTeam, false);
            } else {
                endGame(session, null, false);
            }
            return true;
        }

        PlayerState state = session.getPlayers().remove(player.getUniqueId());
        byPlayer.remove(player.getUniqueId());
        if (state != null) {
            purgePieces(player);
            restorePlayer(player, state);
        }

        if (announce) {
            plugin.getMessageService().send(player, "left");
        }
        broadcast(session, "player-left", Map.of("player", player.getName()), player.getUniqueId());

        if (session.getState() == GameSession.State.LOBBY_COUNTDOWN && session.playerCount() < 2) {
            session.cancelTasks();
            session.setState(GameSession.State.WAITING);
        }
        if (session.playerCount() == 0) {
            cleanupSession(session);
        }
        return true;
    }

    public void tryStart(Player player) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null) {
            plugin.getMessageService().send(player, "not-playing");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(session.getArenaName());
        if (arena.isEmpty()) {
            return;
        }
        if (session.playerCount() < 2 || !session.hasTeam(Team.RED) || !session.hasTeam(Team.YELLOW)) {
            plugin.getMessageService().send(player, "need-more-players");
            return;
        }
        if (session.getState() != GameSession.State.WAITING && session.getState() != GameSession.State.LOBBY_COUNTDOWN) {
            plugin.getMessageService().send(player, "arena-busy", Map.of("arena", arena.get().getName()));
            return;
        }
        session.cancelTasks();
        beginMatch(session, arena.get());
    }

    public void forceStart(Arena arena) {
        GameSession session = byArena.get(arena.getName());
        if (session == null || session.playerCount() < 2) {
            return;
        }
        session.cancelTasks();
        beginMatch(session, arena);
    }

    public void forceStop(String arenaName) {
        GameSession session = byArena.get(arenaName.toLowerCase(Locale.ROOT));
        if (session != null) {
            endGame(session, null, true);
        }
    }

    public boolean handleColumnClick(Player player, Arena arena, int column) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null || !session.getArenaName().equalsIgnoreCase(arena.getName())) {
            return false;
        }
        if (session.getState() != GameSession.State.PLAYING || session.isAnimating()) {
            return true;
        }
        PlayerState state = session.getPlayer(player.getUniqueId());
        if (state == null || state.getTeam() == null) {
            return true;
        }
        if (state.getTeam() != session.getTurn()) {
            plugin.getMessageService().send(player, "not-your-turn");
            return true;
        }
        if (!plugin.getPieceItems().isPiece(player.getInventory().getItemInMainHand(), state.getTeam())) {
            plugin.getMessageService().send(player, "need-piece");
            return true;
        }
        if (BoardLogic.isColumnFull(session.getGrid(), column)) {
            plugin.getMessageService().send(player, "column-full");
            return true;
        }

        dropDisc(session, arena, column, state.getTeam());
        return true;
    }

    public int resolveColumn(Arena arena, Block block) {
        int button = arena.findColumnButton(block);
        if (button >= 0) {
            return button;
        }
        try {
            return new BoardGeometry(arena).columnAt(block);
        } catch (IllegalArgumentException ex) {
            return -1;
        }
    }

    public void startBind(Player player, Arena arena) {
        binders.put(player.getUniqueId(), new BindSession(arena.getName(), 0));
        plugin.getMessageService().send(player, "bind-start", Map.of("arena", arena.getName()));
    }

    public boolean handleBindClick(Player player, Block block) {
        BindSession bind = binders.get(player.getUniqueId());
        if (bind == null) {
            return false;
        }
        if (player.isSneaking()) {
            binders.remove(player.getUniqueId());
            plugin.getMessageService().send(player, "bind-cancelled");
            return true;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(bind.arenaName);
        if (arena.isEmpty()) {
            binders.remove(player.getUniqueId());
            return true;
        }
        arena.get().setColumnButton(bind.nextIndex, block.getLocation());
        bind.nextIndex++;
        plugin.getArenaManager().save();
        if (bind.nextIndex >= 7) {
            binders.remove(player.getUniqueId());
            plugin.getMessageService().send(player, "bind-done", Map.of("arena", arena.get().getName()));
        } else {
            plugin.getMessageService().send(player, "bind-progress", Map.of(
                    "column", String.valueOf(bind.nextIndex),
                    "arena", arena.get().getName()
            ));
        }
        return true;
    }

    public boolean isBinding(UUID uuid) {
        return binders.containsKey(uuid);
    }

    public void cancelBind(UUID uuid) {
        binders.remove(uuid);
    }

    public void shutdown() {
        for (GameSession session : List.copyOf(byArena.values())) {
            endGame(session, null, true);
        }
        binders.clear();
    }

    private void maybeStartLobbyCountdown(GameSession session, Arena arena) {
        if (session.playerCount() < 2 || !session.hasTeam(Team.RED) || !session.hasTeam(Team.YELLOW)) {
            return;
        }
        if (session.getState() == GameSession.State.LOBBY_COUNTDOWN) {
            return;
        }
        session.setState(GameSession.State.LOBBY_COUNTDOWN);
        int seconds = Math.max(1, plugin.getConfig().getInt("lobby-countdown-seconds", 5));
        AtomicInteger remaining = new AtomicInteger(seconds);
        session.setLobbyTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int left = remaining.getAndDecrement();
            if (left <= 0) {
                session.cancelTasks();
                beginMatch(session, arena);
                return;
            }
            broadcast(session, "lobby-countdown", Map.of("seconds", String.valueOf(left)), null);
        }, 0L, 20L));
    }

    private void beginMatch(GameSession session, Arena arena) {
        session.cancelTasks();
        session.clearGrid();
        if (!arena.hasSnapshot() && arena.getOrigin() != null) {
            plugin.getLogger().warning("Arena '" + arena.getName()
                    + "' has no board snapshot. Run /cfadmin snapshotboard " + arena.getName()
                    + " after the empty board looks correct.");
        }
        renderer.clear(arena);
        givePieces(session);
        session.setState(GameSession.State.START_COUNTDOWN);
        session.setTurn(resolveFirstTurn(session));

        int seconds = Math.max(1, plugin.getConfig().getInt("start-countdown-seconds", 3));
        AtomicInteger remaining = new AtomicInteger(seconds);
        session.setStartTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int left = remaining.getAndDecrement();
            if (left <= 0) {
                session.cancelTasks();
                session.setState(GameSession.State.PLAYING);
                broadcast(session, "go", Map.of(), null);
                for (UUID uuid : session.getPlayers().keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                    }
                }
                startActionBar(session);
                notifyTurn(session);
                return;
            }
            broadcast(session, "countdown", Map.of("seconds", String.valueOf(left)), null);
            for (UUID uuid : session.getPlayers().keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    plugin.getMessageService().title(player, "&e" + left, "");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
            }
        }, 0L, 20L));
    }

    private void givePieces(GameSession session) {
        int amount = plugin.getPieceItems().startingAmount();
        for (PlayerState state : session.getPlayers().values()) {
            Player player = Bukkit.getPlayer(state.getUuid());
            if (player == null || state.getTeam() == null) {
                continue;
            }
            purgePieces(player);
            ItemStack piece = plugin.getPieceItems().create(state.getTeam(), amount);
            player.getInventory().setItem(0, piece);
            player.getInventory().setHeldItemSlot(0);
            plugin.getMessageService().send(player, "piece-given", Map.of(
                    "team", state.getTeam().colored(),
                    "amount", String.valueOf(amount)
            ));
        }
    }

    private void purgePieces(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (plugin.getPieceItems().isPiece(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
        if (plugin.getPieceItems().isPiece(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    private void consumePiece(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!plugin.getPieceItems().isPiece(hand)) {
            return;
        }
        int amount = hand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(amount - 1);
        }
    }

    private Team resolveFirstTurn(GameSession session) {
        String mode = plugin.getConfig().getString("first-turn", "yellow");
        if (mode == null) {
            mode = "yellow";
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "red" -> Team.RED;
            case "random" -> ThreadLocalRandom.current().nextBoolean() ? Team.RED : Team.YELLOW;
            case "first-join" -> {
                PlayerState first = null;
                for (PlayerState state : session.getPlayers().values()) {
                    if (first == null) {
                        first = state;
                    }
                }
                yield first != null && first.getTeam() != null ? first.getTeam() : Team.YELLOW;
            }
            default -> Team.YELLOW;
        };
    }

    private void dropDisc(GameSession session, Arena arena, int column, Team team) {
        int targetRow = BoardLogic.lowestEmptyRow(session.getGrid(), column);
        if (targetRow < 0) {
            return;
        }

        session.setAnimating(true);
        session.setState(GameSession.State.ANIMATING);
        int ticksPerRow = Math.max(0, plugin.getConfig().getInt("drop-ticks-per-row", 2));
        int topRow = arena.getRows() - 1;

        if (ticksPerRow == 0 || topRow <= targetRow) {
            finishDrop(session, arena, column, targetRow, team);
            return;
        }

        // Animate from the top empty visual row down to the landing slot.
        AtomicInteger visualRow = new AtomicInteger(topRow);
        var task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                int row = visualRow.getAndDecrement();
                if (row < topRow) {
                    renderer.paintCell(arena, column, row + 1, null);
                }
                renderer.paintCell(arena, column, row, team);
                for (UUID uuid : session.getPlayers().keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.35f, 1.35f);
                    }
                }
                if (row <= targetRow) {
                    cancel();
                    finishDrop(session, arena, column, targetRow, team);
                }
            }
        };
        session.setDropTask(task.runTaskTimer(plugin, 0L, ticksPerRow));
    }

    private void finishDrop(GameSession session, Arena arena, int column, int row, Team team) {
        session.getGrid()[column][row] = team;
        renderer.paintCell(arena, column, row, team);
        session.setAnimating(false);

        PlayerState mover = session.playerWithTeam(team);
        if (mover != null) {
            Player moverPlayer = Bukkit.getPlayer(mover.getUuid());
            if (moverPlayer != null) {
                consumePiece(moverPlayer);
                plugin.getMessageService().send(moverPlayer, "dropped", Map.of("column", String.valueOf(column + 1)));
            }
        }

        for (UUID uuid : session.getPlayers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 0.8f);
            }
        }

        List<int[]> win = BoardLogic.findWinningLine(session.getGrid(), team);
        if (!win.isEmpty()) {
            session.setState(GameSession.State.ENDING);
            renderer.highlight(arena, win);
            long highlight = Math.max(1, plugin.getConfig().getLong("win-highlight-ticks", 60));
            session.setEndTask(Bukkit.getScheduler().runTaskLater(plugin, () -> endGame(session, team, false), highlight));
            return;
        }

        if (BoardLogic.isBoardFull(session.getGrid())) {
            session.setState(GameSession.State.ENDING);
            long delay = Math.max(1, plugin.getConfig().getLong("reset-delay-ticks", 80));
            session.setEndTask(Bukkit.getScheduler().runTaskLater(plugin, () -> endGame(session, null, false), delay));
            return;
        }

        session.setTurn(team.opposite());
        session.setState(GameSession.State.PLAYING);
        notifyTurn(session);
    }

    private void endGame(GameSession session, Team winner, boolean silent) {
        session.cancelTasks();
        session.setState(GameSession.State.ENDING);
        session.setAnimating(false);

        Optional<Arena> arenaOpt = plugin.getArenaManager().get(session.getArenaName());
        Arena arena = arenaOpt.orElse(null);

        if (!silent) {
            if (winner != null) {
                PlayerState winnerState = session.playerWithTeam(winner);
                String winnerName = winnerState == null ? winner.display() : winnerState.getName();
                broadcast(session, "win", Map.of(
                        "player", winnerName,
                        "team", winner.colored(),
                        "winner", winnerName
                ), null);
            } else {
                broadcast(session, "draw", Map.of(), null);
            }
        }

        int winPoints = plugin.getConfig().getInt("points-win", 3);
        int drawPoints = plugin.getConfig().getInt("points-draw", 1);

        for (PlayerState state : session.getPlayers().values()) {
            Player player = Bukkit.getPlayer(state.getUuid());
            if (player == null) {
                byPlayer.remove(state.getUuid());
                continue;
            }
            purgePieces(player);
            if (!silent) {
                if (winner == null) {
                    plugin.getPointsService().addPoints(player, drawPoints);
                    plugin.getMessageService().send(player, "you-draw", Map.of("points", String.valueOf(drawPoints)));
                    runRewardCommands(player, drawPoints, session.getArenaName(), "draw");
                } else if (state.getTeam() == winner) {
                    plugin.getPointsService().addPoints(player, winPoints);
                    plugin.getMessageService().send(player, "you-win", Map.of("points", String.valueOf(winPoints)));
                    runRewardCommands(player, winPoints, session.getArenaName(), "win");
                } else {
                    plugin.getMessageService().send(player, "you-lose", Map.of());
                    runRewardCommands(player, 0, session.getArenaName(), "loss");
                }
            }
            restorePlayer(player, state);
            byPlayer.remove(state.getUuid());
        }

        long resetDelay = Math.max(1, plugin.getConfig().getLong("reset-delay-ticks", 80));
        if (arena != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> renderer.clear(arena), Math.min(resetDelay, 5));
        }

        cleanupSession(session);
    }

    private void runRewardCommands(Player player, int points, String arena, String result) {
        if (plugin.getConfig().getBoolean("reward.deposit-to-vault", false) && points > 0) {
            plugin.getEconomyService().deposit(player, points);
        }
        for (String command : plugin.getConfig().getStringList("reward.commands")) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String parsed = command
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{points}", String.valueOf(points))
                    .replace("{arena}", arena)
                    .replace("{result}", result);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private void notifyTurn(GameSession session) {
        Team turn = session.getTurn();
        PlayerState current = session.playerWithTeam(turn);
        PlayerState other = session.playerWithTeam(turn.opposite());
        if (current != null) {
            Player player = Bukkit.getPlayer(current.getUuid());
            if (player != null) {
                plugin.getMessageService().send(player, "your-turn");
                plugin.getMessageService().title(player, "&aYour turn", turn.colored());
                // Keep a piece in hand if they somehow lost it
                if (!plugin.getPieceItems().isPiece(player.getInventory().getItemInMainHand(), turn)) {
                    boolean has = false;
                    for (ItemStack stack : player.getInventory().getContents()) {
                        if (plugin.getPieceItems().isPiece(stack, turn)) {
                            has = true;
                            break;
                        }
                    }
                    if (!has) {
                        player.getInventory().addItem(plugin.getPieceItems().create(turn, 1));
                    }
                }
            }
        }
        if (other != null && current != null) {
            Player player = Bukkit.getPlayer(other.getUuid());
            if (player != null) {
                plugin.getMessageService().send(player, "opponent-turn", Map.of("player", current.getName()));
            }
        }
    }

    private void startActionBar(GameSession session) {
        session.setActionBarTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.getState() != GameSession.State.PLAYING && session.getState() != GameSession.State.ANIMATING) {
                return;
            }
            String template = plugin.getConfig().getString("action-bar", "");
            for (PlayerState state : session.getPlayers().values()) {
                Player player = Bukkit.getPlayer(state.getUuid());
                if (player == null || state.getTeam() == null) {
                    continue;
                }
                String message = plugin.getMessageService().apply(template, Map.of(
                        "turn", session.getTurn().colored(),
                        "team", state.getTeam().colored()
                ));
                plugin.getMessageService().actionBar(player, message);
            }
        }, 0L, 20L));
    }

    private void cleanupSession(GameSession session) {
        session.cancelTasks();
        for (UUID uuid : List.copyOf(session.getPlayers().keySet())) {
            byPlayer.remove(uuid);
        }
        session.getPlayers().clear();
        byArena.remove(session.getArenaName());
    }

    private void broadcast(GameSession session, String key, Map<String, String> placeholders, UUID exclude) {
        for (UUID uuid : session.getPlayers().keySet()) {
            if (exclude != null && exclude.equals(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.getMessageService().send(player, key, placeholders);
            }
        }
    }

    private void snapshotPlayer(Player player, PlayerState state) {
        state.setPreviousLocation(player.getLocation());
        state.setPreviousGameMode(player.getGameMode());
        state.setPreviousHealth(player.getHealth());
        state.setPreviousFood(player.getFoodLevel());
        state.setPreviousSaturation(player.getSaturation());
        state.setPreviousExhaustion(player.getExhaustion());
        state.setPreviousInventory(player.getInventory().getContents().clone());
        state.setPreviousArmor(player.getInventory().getArmorContents().clone());
        state.setPreviousOffhand(player.getInventory().getItemInOffHand().clone());
        state.setPreviousEffects(player.getActivePotionEffects());
        state.setSnapshotted(true);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(Math.min(player.getHealth(), maxHealth.getValue()));
        }
    }

    private void restorePlayer(Player player, PlayerState state) {
        if (!state.isSnapshotted()) {
            return;
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.getInventory().setContents(state.getPreviousInventory() == null ? new ItemStack[0] : state.getPreviousInventory());
        player.getInventory().setArmorContents(state.getPreviousArmor());
        player.getInventory().setItemInOffHand(state.getPreviousOffhand());
        if (state.getPreviousGameMode() != null) {
            player.setGameMode(state.getPreviousGameMode());
        }
        player.setFoodLevel(state.getPreviousFood());
        player.setSaturation(state.getPreviousSaturation());
        player.setExhaustion(state.getPreviousExhaustion());
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double health = state.getPreviousHealth();
        if (maxHealth != null) {
            health = Math.min(health, maxHealth.getValue());
        }
        player.setHealth(Math.max(1.0, health));
        for (PotionEffect effect : state.getPreviousEffects()) {
            player.addPotionEffect(effect);
        }
        if (plugin.getConfig().getBoolean("teleport-on-end", true) && state.getPreviousLocation() != null) {
            player.teleport(state.getPreviousLocation());
        }
    }

    private static final class BindSession {
        private final String arenaName;
        private int nextIndex;

        private BindSession(String arenaName, int nextIndex) {
            this.arenaName = arenaName;
            this.nextIndex = nextIndex;
        }
    }
}
