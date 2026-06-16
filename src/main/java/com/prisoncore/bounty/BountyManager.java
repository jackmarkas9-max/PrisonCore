package com.prisoncore.bounty;

import com.prisoncore.PrisonCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BountyManager implements CommandExecutor {

    private final PrisonCore plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, Double> bounties = new HashMap<>();

    public BountyManager(PrisonCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/bounties.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try { dataFile.createNewFile(); } catch (IOException e) {}
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : dataConfig.getKeys(false)) {
            bounties.put(UUID.fromString(key), dataConfig.getDouble(key, 0));
        }
    }

    private void saveConfig() {
        for (Map.Entry<UUID, Double> e : bounties.entrySet()) {
            dataConfig.set(e.getKey().toString(), e.getValue());
        }
        try { dataConfig.save(dataFile); } catch (IOException e) {}
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use bounty commands.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§6§l=== BOUNTIES ===");
            player.sendMessage("§e/bounty set <player> <amount> §7- Place a bounty");
            player.sendMessage("§e/bounty list §7- List all bounties");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set":
                if (args.length < 3) { player.sendMessage("§cUsage: /bounty set <player> <amount>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage("§cPlayer not found."); return true; }
                if (target.equals(player)) { player.sendMessage("§cYou cannot bounty yourself!"); return true; }
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (amount < 10) { player.sendMessage("§cMinimum bounty is $10."); return true; }
                    if (plugin.getEconomyManager().subtractBalance(player.getUniqueId(), amount)) {
                        UUID tuid = target.getUniqueId();
                        bounties.put(tuid, bounties.getOrDefault(tuid, 0.0) + amount);
                        saveConfig();
                        Bukkit.broadcastMessage("§c§l[BOUNTY] §e" + player.getName() + " §7placed §a$" + String.format("%.0f", amount) + " §7bounty on §e" + target.getName() + "§7!");
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.0f);
                    } else {
                        player.sendMessage("§cInsufficient balance!");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid amount.");
                }
                break;

            case "list":
                player.sendMessage("§6§l=== ACTIVE BOUNTIES ===");
                if (bounties.isEmpty()) { player.sendMessage("§7No active bounties."); return true; }
                for (Map.Entry<UUID, Double> e : bounties.entrySet()) {
                    String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                    player.sendMessage("§e" + (name != null ? name : "Unknown") + " §7- §a$" + String.format("%.0f", e.getValue()));
                }
                break;

            default:
                player.sendMessage("§cUnknown bounty command.");
        }
        return true;
    }

    public double claimBounty(Player killer, Player victim) {
        UUID vuuid = victim.getUniqueId();
        if (!bounties.containsKey(vuuid)) return 0;
        double amount = bounties.remove(vuuid);
        dataConfig.set(vuuid.toString(), null);
        saveConfig();
        plugin.getEconomyManager().addBalance(killer.getUniqueId(), amount);
        Bukkit.broadcastMessage("§6§l[BOUNTY] §e" + killer.getName() + " §7claimed §a$" + String.format("%.0f", amount) + " §7bounty on §e" + victim.getName() + "§7!");
        return amount;
    }

    public double getBounty(Player player) {
        return bounties.getOrDefault(player.getUniqueId(), 0.0);
    }
}
