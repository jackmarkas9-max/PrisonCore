package com.prisoncore.prisonrank;

import com.prisoncore.PrisonCore;
import org.bukkit.Bukkit;
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

public class PrisonRankManager {

    private static final String[] RANKS = {"F", "E", "D", "C", "B", "A", "S"};
    private static final double[] MONEY_REQUIRED = {0, 500, 2000, 8000, 25000, 75000, 200000};
    private static final double[] MULTIPLIERS = {1.0, 1.2, 1.5, 2.0, 2.5, 3.5, 5.0};
    private static final long RANKUP_INTERVAL = 7200; // 6 minutes in ticks (20 ticks/sec * 360 sec)

    private final PrisonCore plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, Integer> playerPrisonRanks = new HashMap<>();
    private final Map<UUID, Long> lastDaily = new HashMap<>();
    private final Map<UUID, Long> playTimeTicks = new HashMap<>();

    public PrisonRankManager(PrisonCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/prisonranks.yml");
        loadConfig();
        startRankupTask();
        startPlayTimeTracker();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try { dataFile.createNewFile(); } catch (IOException e) { plugin.getLogger().severe("Could not create data/prisonranks.yml!"); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveConfig() {
        try { dataConfig.save(dataFile); } catch (IOException e) { plugin.getLogger().severe("Could not save data/prisonranks.yml!"); }
    }

    public int getPrisonRankIndex(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerPrisonRanks.containsKey(uuid)) return playerPrisonRanks.get(uuid);
        int idx = dataConfig.getInt(uuid.toString() + ".rank", 0);
        playerPrisonRanks.put(uuid, idx);
        return idx;
    }

    public String getPrisonRankName(Player player) {
        return RANKS[getPrisonRankIndex(player)];
    }

    public double getGoldMultiplier(Player player) {
        return MULTIPLIERS[getPrisonRankIndex(player)];
    }

    public void tryRankUp(Player player) {
        int idx = getPrisonRankIndex(player);
        if (idx >= RANKS.length - 1) return;

        UUID uuid = player.getUniqueId();
        long played = playTimeTicks.getOrDefault(uuid, 0L);

        if (played < RANKUP_INTERVAL) return;

        double total = plugin.getEconomyManager().getBalance(player);
        if (total >= MONEY_REQUIRED[idx + 1]) {
            playerPrisonRanks.put(uuid, idx + 1);
            dataConfig.set(uuid.toString() + ".rank", idx + 1);
            saveConfig();
            playTimeTicks.put(uuid, 0L);

            player.sendMessage("§6§lRANK UP! §eYou are now Prison Rank §f" + RANKS[idx + 1] + "§e!");
            player.sendMessage("§7Gold multiplier: §ax" + String.format("%.1f", MULTIPLIERS[idx + 1]));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);

            plugin.getEconomyManager().updateScoreboard(player);
        }
    }

    public boolean claimDaily(Player player) {
        long now = System.currentTimeMillis() / 1000;
        UUID uuid = player.getUniqueId();
        long last = lastDaily.containsKey(uuid) ? lastDaily.get(uuid) : dataConfig.getLong(uuid.toString() + ".daily", 0);
        if (now - last < 86400) {
            long remaining = 86400 - (now - last);
            player.sendMessage("§cDaily reward available in " + (remaining / 3600) + "h " + ((remaining % 3600) / 60) + "m.");
            return false;
        }
        int idx = getPrisonRankIndex(player);
        double reward = 50.0 + (idx * 100.0);
        plugin.getEconomyManager().addBalance(uuid, reward);
        lastDaily.put(uuid, now);
        dataConfig.set(uuid.toString() + ".daily", now);
        saveConfig();
        player.sendMessage("§aDaily reward claimed: +$" + String.format("%.0f", reward));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
        return true;
    }

    public void setPlayerOnline(Player player) {
        playTimeTicks.putIfAbsent(player.getUniqueId(), 0L);
    }

    private void startRankupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    tryRankUp(p);
                }
            }
        }.runTaskTimer(plugin, 200L, 200L); // every 10 seconds
    }

    private void startPlayTimeTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID uuid = p.getUniqueId();
                    playTimeTicks.merge(uuid, 1L, Long::sum);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // every second
    }

    public double getMoneyRequired(Player player) {
        int idx = getPrisonRankIndex(player);
        if (idx >= RANKS.length - 1) return -1;
        return MONEY_REQUIRED[idx + 1];
    }

    public long getPlayTimeSeconds(Player player) {
        return playTimeTicks.getOrDefault(player.getUniqueId(), 0L) / 20;
    }
}
