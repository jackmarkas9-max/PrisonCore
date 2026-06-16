package com.prisoncore.rank;

import com.prisoncore.PrisonCore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RankManager {
    private final PrisonCore plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, Rank> playerRanks = new HashMap<>();

    public RankManager(PrisonCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/players.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data/players.yml!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveConfig() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data/players.yml!");
        }
    }

    public Rank getRank(UUID uuid) {
        if (playerRanks.containsKey(uuid)) {
            return playerRanks.get(uuid);
        }
        String rankStr = dataConfig.getString(uuid.toString() + ".rank", "Prisoner");
        Rank rank = Rank.fromName(rankStr);
        if (rank == null) {
            rank = Rank.PRISONER;
        }
        playerRanks.put(uuid, rank);
        return rank;
    }

    public Rank getRank(Player player) {
        return getRank(player.getUniqueId());
    }

    public void setRank(Player player, Rank rank) {
        UUID uuid = player.getUniqueId();
        Rank oldRank = getRank(uuid);
        playerRanks.put(uuid, rank);
        dataConfig.set(uuid.toString() + ".rank", rank.getName());
        saveConfig();

        // Update prefix in tab list using legacy format (Adventure text)
        player.playerListName(net.kyori.adventure.text.Component.text(rank.getLegacyPrefix() + player.getName()));

        // Check Admin Promotion effects
        if (rank == Rank.ADMIN && oldRank != Rank.ADMIN) {
            playAdminPromotionEffects(player);
        }

        // Apply Game Mode rules
        if (rank == Rank.ADMIN) {
            player.setGameMode(GameMode.CREATIVE);
        } else {
            // Only drop to Survival if build mode is NOT active
            if (!plugin.isBuildModeActive()) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }

        // Give Guard Whistle if Guard
        if (rank == Rank.GUARD) {
            if (!hasWhistle(player)) {
                player.getInventory().addItem(PrisonCore.createWhistle());
                player.sendMessage("§b[Guard] You have been issued a Guard Whistle!");
            }
        }

        // Give Key if Guard or Admin
        if (rank == Rank.GUARD || rank == Rank.ADMIN) {
            if (!hasKey(player)) {
                player.getInventory().addItem(PrisonCore.createKey());
                player.sendMessage("§5[Key] You have been issued a Prison Key!");
            }
        }

        // Update Scoreboard HUD
        if (plugin.getEconomyManager() != null) {
            plugin.getEconomyManager().updateScoreboard(player);
        }
    }

    private boolean hasWhistle(Player player) {
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.SHEARS) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Whistle")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasKey(Player player) {
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.TRIPWIRE_HOOK) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Key")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void playAdminPromotionEffects(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40 || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().add(0, 1.0, 0);
                Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 1.5f);
                player.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.5, 0.5, 0.5, 0.05, dustOptions);
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
