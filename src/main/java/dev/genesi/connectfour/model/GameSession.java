package dev.genesi.connectfour.model;

import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GameSession {

    public enum State {
        WAITING,
        LOBBY_COUNTDOWN,
        START_COUNTDOWN,
        PLAYING,
        ANIMATING,
        ENDING
    }

    private final String arenaName;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private State state = State.WAITING;
    private Team turn = Team.YELLOW;
    private final Team[][] grid;
    private final int columns;
    private final int rows;
    private boolean animating;
    private BukkitTask lobbyTask;
    private BukkitTask startTask;
    private BukkitTask dropTask;
    private BukkitTask endTask;
    private BukkitTask actionBarTask;

    public GameSession(String arenaName, int columns, int rows) {
        this.arenaName = arenaName.toLowerCase();
        this.columns = columns;
        this.rows = rows;
        this.grid = new Team[columns][rows];
    }

    public String getArenaName() {
        return arenaName;
    }

    public Map<UUID, PlayerState> getPlayers() {
        return players;
    }

    public PlayerState getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public int playerCount() {
        return players.size();
    }

    public PlayerState playerWithTeam(Team team) {
        for (PlayerState state : players.values()) {
            if (state.getTeam() == team) {
                return state;
            }
        }
        return null;
    }

    public boolean hasTeam(Team team) {
        return playerWithTeam(team) != null;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Team getTurn() {
        return turn;
    }

    public void setTurn(Team turn) {
        this.turn = turn;
    }

    public Team[][] getGrid() {
        return grid;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public boolean isAnimating() {
        return animating;
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
    }

    public void clearGrid() {
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows; r++) {
                grid[c][r] = null;
            }
        }
    }

    public void cancelTasks() {
        if (lobbyTask != null) {
            lobbyTask.cancel();
            lobbyTask = null;
        }
        if (startTask != null) {
            startTask.cancel();
            startTask = null;
        }
        if (dropTask != null) {
            dropTask.cancel();
            dropTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
    }

    public void setLobbyTask(BukkitTask lobbyTask) {
        this.lobbyTask = lobbyTask;
    }

    public void setStartTask(BukkitTask startTask) {
        this.startTask = startTask;
    }

    public void setDropTask(BukkitTask dropTask) {
        this.dropTask = dropTask;
    }

    public void setEndTask(BukkitTask endTask) {
        this.endTask = endTask;
    }

    public void setActionBarTask(BukkitTask actionBarTask) {
        this.actionBarTask = actionBarTask;
    }
}
