package com.prisoncore.lottery;

import com.prisoncore.PrisonCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class LotteryManager implements CommandExecutor {

    private final PrisonCore plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final List<UUID> tickets = new ArrayList<>();
    private double jackpot = 1000.0;
    private static final double TICKET_PRICE = 50.0;

    public LotteryManager(PrisonCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/lottery.yml");
        loadConfig();
        startLotteryTimer();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try { dataFile.createNewFile(); } catch (IOException e) {}
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        jackpot = dataConfig.getDouble("jackpot", 1000.0);
        for (String key : dataConfig.getStringList("tickets")) {
            tickets.add(UUID.fromString(key));
        }
    }

    private void saveConfig() {
        dataConfig.set("jackpot", jackpot);
        List<String> list = new ArrayList<>();
        for (UUID u : tickets) list.add(u.toString());
        dataConfig.set("tickets", list);
        try { dataConfig.save(dataFile); } catch (IOException e) {}
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (plugin.getEconomyManager().subtractBalance(player.getUniqueId(), TICKET_PRICE)) {
            tickets.add(player.getUniqueId());
            jackpot += TICKET_PRICE * 0.8;
            saveConfig();
            player.sendMessage("§aYou bought a lottery ticket for §e$" + String.format("%.0f", TICKET_PRICE) + "§a!");
            player.sendMessage("§7Current jackpot: §6$" + String.format("%.0f", jackpot));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        } else {
            player.sendMessage("§cYou need $" + String.format("%.0f", TICKET_PRICE) + " to buy a ticket!");
        }
        return true;
    }

    private void startLotteryTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                drawWinner();
            }
        }.runTaskTimer(plugin, 36000L, 36000L);
    }

    private void drawWinner() {
        if (tickets.isEmpty()) {
            jackpot += 500;
            saveConfig();
            return;
        }

        UUID winner = tickets.get(new Random().nextInt(tickets.size()));
        String name = Bukkit.getOfflinePlayer(winner).getName();
        plugin.getEconomyManager().addBalance(winner, jackpot);
        Bukkit.broadcastMessage("§6§l=== LOTTERY WINNER ===");
        Bukkit.broadcastMessage("§e" + (name != null ? name : "Someone") + " §7won §a$" + String.format("%.0f", jackpot) + " §7in the lottery!");
        Bukkit.broadcastMessage("§7Buy tickets with §e/lottery");

        tickets.clear();
        jackpot = 1000.0;
        saveConfig();
    }
}
