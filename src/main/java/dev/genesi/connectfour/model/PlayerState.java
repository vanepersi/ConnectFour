package dev.genesi.connectfour.model;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class PlayerState {

    private final UUID uuid;
    private final String name;
    private Team team;
    private Location previousLocation;
    private GameMode previousGameMode;
    private double previousHealth;
    private int previousFood;
    private float previousSaturation;
    private float previousExhaustion;
    private ItemStack[] previousInventory;
    private ItemStack[] previousArmor;
    private ItemStack previousOffhand;
    private final List<PotionEffect> previousEffects = new ArrayList<>();
    private boolean snapshotted;

    public PlayerState(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public boolean isSnapshotted() {
        return snapshotted;
    }

    public void setSnapshotted(boolean snapshotted) {
        this.snapshotted = snapshotted;
    }

    public Location getPreviousLocation() {
        return previousLocation == null ? null : previousLocation.clone();
    }

    public void setPreviousLocation(Location previousLocation) {
        this.previousLocation = previousLocation == null ? null : previousLocation.clone();
    }

    public GameMode getPreviousGameMode() {
        return previousGameMode;
    }

    public void setPreviousGameMode(GameMode previousGameMode) {
        this.previousGameMode = previousGameMode;
    }

    public double getPreviousHealth() {
        return previousHealth;
    }

    public void setPreviousHealth(double previousHealth) {
        this.previousHealth = previousHealth;
    }

    public int getPreviousFood() {
        return previousFood;
    }

    public void setPreviousFood(int previousFood) {
        this.previousFood = previousFood;
    }

    public float getPreviousSaturation() {
        return previousSaturation;
    }

    public void setPreviousSaturation(float previousSaturation) {
        this.previousSaturation = previousSaturation;
    }

    public float getPreviousExhaustion() {
        return previousExhaustion;
    }

    public void setPreviousExhaustion(float previousExhaustion) {
        this.previousExhaustion = previousExhaustion;
    }

    public ItemStack[] getPreviousInventory() {
        return previousInventory;
    }

    public void setPreviousInventory(ItemStack[] previousInventory) {
        this.previousInventory = previousInventory;
    }

    public ItemStack[] getPreviousArmor() {
        return previousArmor;
    }

    public void setPreviousArmor(ItemStack[] previousArmor) {
        this.previousArmor = previousArmor;
    }

    public ItemStack getPreviousOffhand() {
        return previousOffhand;
    }

    public void setPreviousOffhand(ItemStack previousOffhand) {
        this.previousOffhand = previousOffhand;
    }

    public List<PotionEffect> getPreviousEffects() {
        return previousEffects;
    }

    public void setPreviousEffects(Collection<PotionEffect> effects) {
        previousEffects.clear();
        if (effects != null) {
            previousEffects.addAll(effects);
        }
    }
}
