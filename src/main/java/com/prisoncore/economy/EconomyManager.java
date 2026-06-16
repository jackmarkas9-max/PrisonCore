package com.prisoncore.economy;

import com.prisoncore.PrisonCore;
import com.prisoncore.rank.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private final PrisonCore plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, Double> playerBalances = new HashMap<>();

    public EconomyManager(PrisonCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/economy.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data/economy.yml!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveConfig() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data/economy.yml!");
        }
    }

    public double getBalance(UUID uuid) {
        if (playerBalances.containsKey(uuid)) {
            return playerBalances.get(uuid);
        }
        double balance = dataConfig.getDouble(uuid.toString() + ".balance", 0.0);
        playerBalances.put(uuid, balance);
        return balance;
    }

    public double getBalance(Player player) {
        return getBalance(player.getUniqueId());
    }

    public void setBalance(UUID uuid, double amount) {
        playerBalances.put(uuid, amount);
        dataConfig.set(uuid.toString() + ".balance", amount);
        saveConfig();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            updateScoreboard(player);
        }
    }

    public void addBalance(UUID uuid, double amount) {
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public boolean subtractBalance(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current < amount) return false;
        setBalance(uuid, current - amount);
        return true;
    }

    public void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = player.getScoreboard();
        if (board == manager.getMainScoreboard()) {
            board = manager.getNewScoreboard();
            player.setScoreboard(board);
        }

        Objective objective = board.getObjective("prison_hud");
        if (objective != null) {
            objective.unregister();
        }

        objective = board.registerNewObjective("prison_hud", Criteria.DUMMY, Component.text("§6§lPRISON CORE"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Rank rank = plugin.getRankManager().getRank(player);
        double balance = getBalance(player.getUniqueId());

        int scoreIndex = 4;
        objective.getScore("§7------------------").setScore(scoreIndex--);
        objective.getScore("§fRank: " + rank.getLegacyPrefix().trim()).setScore(scoreIndex--);
        objective.getScore("§fBalance: §a$" + String.format("%.2f", balance)).setScore(scoreIndex--);
        objective.getScore("§7-------------------").setScore(scoreIndex--);
    }

    public boolean handlePayCommand(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /pay [player] [amount]");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cPlayer not found or offline.");
            return true;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage("§cYou cannot pay yourself.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount. Must be a positive number.");
            return true;
        }

        if (subtractBalance(sender.getUniqueId(), amount)) {
            addBalance(target.getUniqueId(), amount);
            sender.sendMessage("§aYou paid $" + String.format("%.2f", amount) + " to " + target.getName() + ".");
            target.sendMessage("§aYou received $" + String.format("%.2f", amount) + " from " + sender.getName() + ".");
        } else {
            sender.sendMessage("§cInsufficient balance!");
        }
        return true;
    }

    public boolean handleRequestPayCommand(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /requestpay [player] [amount] [reason]");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cPlayer not found or offline.");
            return true;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage("§cYou cannot request payment from yourself.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount. Must be a positive number.");
            return true;
        }

        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            reasonBuilder.append(args[i]).append(" ");
        }
        String reason = reasonBuilder.toString().trim();

        // Build Adventure interactive message
        Component message = Component.text(sender.getName(), NamedTextColor.GOLD)
            .append(Component.text(" requested ", NamedTextColor.GRAY))
            .append(Component.text("$" + String.format("%.2f", amount), NamedTextColor.GREEN))
            .append(Component.text(". Reason: " + reason + " ", NamedTextColor.GRAY))
            .append(Component.text("[CLICK TO PAY]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/pay " + sender.getName() + " " + String.format("%.2f", amount)))
                .hoverEvent(HoverEvent.showText(Component.text("Click here to pay " + sender.getName() + " $" + String.format("%.2f", amount), NamedTextColor.GREEN))));

        target.sendMessage(message);
        sender.sendMessage("§aPayment request sent to " + target.getName() + ".");
        return true;
    }
}
