package dev.genesi.connectfour;

import dev.genesi.connectfour.command.ConnectFourAdminCommand;
import dev.genesi.connectfour.command.ConnectFourCommand;
import dev.genesi.connectfour.listener.GameListener;
import dev.genesi.connectfour.manager.ArenaManager;
import dev.genesi.connectfour.manager.GameManager;
import dev.genesi.games.GenesiGamePlugin;
import dev.genesi.games.economy.EconomyService;
import dev.genesi.games.economy.PointsService;
import dev.genesi.games.message.MessageService;

public final class ConnectFourPlugin extends GenesiGamePlugin {

    private ArenaManager arenaManager;
    private GameManager gameManager;
    private PointsService pointsService;
    private EconomyService economyService;
    private MessageService messageService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messageService = new MessageService(this, "&8[&6Connect Four&8] &r");
        this.economyService = new EconomyService(this, "connectfour.bypass.fee");
        this.pointsService = new PointsService(this);
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this);

        arenaManager.load();
        pointsService.load();
        economyService.hook();

        ConnectFourCommand playerCommand = new ConnectFourCommand(this);
        ConnectFourAdminCommand adminCommand = new ConnectFourAdminCommand(this);
        getCommand("connectfour").setExecutor(playerCommand);
        getCommand("connectfour").setTabCompleter(playerCommand);
        getCommand("connectfouradmin").setExecutor(adminCommand);
        getCommand("connectfouradmin").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        getLogger().info("ConnectFour enabled. Economy: " + economyService.describe()
                + " | data: " + getDataFolder().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (arenaManager != null) {
            arenaManager.save();
        }
        if (pointsService != null) {
            pointsService.save();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        messageService.reload();
        arenaManager.load();
        pointsService.load();
        economyService.hook();
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public PointsService getPointsService() {
        return pointsService;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public MessageService getMessageService() {
        return messageService;
    }
}
