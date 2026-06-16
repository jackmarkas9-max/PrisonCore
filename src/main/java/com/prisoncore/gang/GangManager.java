package com.prisoncore.gang;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GangManager implements CommandExecutor {

    private final PrisonCore plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<String, GangData> gangs = new HashMap<>();
    private final Map<UUID, String> playerGang = new HashMap<>();

    private static class GangData {
        String name;
        String leader;
        Set<String> members = new HashSet<>();
        double balance;

        GangData(String name, String leader) {
            this.name = name;
            this.leader = leader;
            members.add(leader);
        }
    }

    public GangManager(PrisonCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/gangs.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try { dataFile.createNewFile(); } catch (IOException e) {}
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : dataConfig.getKeys(false)) {
            GangData g = new GangData(dataConfig.getString(key + ".name"), dataConfig.getString(key + ".leader"));
            g.members.addAll(dataConfig.getStringList(key + ".members"));
            g.balance = dataConfig.getDouble(key + ".balance", 0);
            gangs.put(g.name.toLowerCase(), g);
            for (String m : g.members) {
                Player p = Bukkit.getPlayerExact(m);
                if (p != null) playerGang.put(p.getUniqueId(), g.name.toLowerCase());
            }
        }
    }

    private void saveConfig() {
        dataConfig.getKeys(false).forEach(k -> dataConfig.set(k, null));
        for (GangData g : gangs.values()) {
            String key = g.name.toLowerCase();
            dataConfig.set(key + ".name", g.name);
            dataConfig.set(key + ".leader", g.leader);
            dataConfig.set(key + ".members", new ArrayList<>(g.members));
            dataConfig.set(key + ".balance", g.balance);
        }
        try { dataConfig.save(dataFile); } catch (IOException e) {}
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use gang commands.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§6§l=== GANGS ===");
            player.sendMessage("§e/gang create <name> §7- Create a gang");
            player.sendMessage("§e/gang invite <player> §7- Invite a player");
            player.sendMessage("§e/gang join <name> §7- Join a gang");
            player.sendMessage("§e/gang leave §7- Leave your gang");
            player.sendMessage("§e/gang info [name] §7- View gang info");
            player.sendMessage("§e/gang balance §7- View gang balance");
            player.sendMessage("§e/gang chat <message> §7- Send gang chat");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) { player.sendMessage("§cUsage: /gang create <name>"); return true; }
                if (playerGang.containsKey(player.getUniqueId())) { player.sendMessage("§cYou are already in a gang!"); return true; }
                if (gangs.containsKey(args[1].toLowerCase())) { player.sendMessage("§cGang already exists!"); return true; }
                gangs.put(args[1].toLowerCase(), new GangData(args[1], player.getName()));
                playerGang.put(player.getUniqueId(), args[1].toLowerCase());
                saveConfig();
                player.sendMessage("§aGang '" + args[1] + "' created! Invite players with /gang invite.");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                break;

            case "invite":
                if (args.length < 2) { player.sendMessage("§cUsage: /gang invite <player>"); return true; }
                if (!playerGang.containsKey(player.getUniqueId())) { player.sendMessage("§cYou are not in a gang!"); return true; }
                GangData g = gangs.get(playerGang.get(player.getUniqueId()));
                if (g == null) return true;
                if (!g.leader.equals(player.getName())) { player.sendMessage("§cOnly the leader can invite!"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage("§cPlayer not found."); return true; }
                if (playerGang.containsKey(target.getUniqueId())) { player.sendMessage("§cThey are already in a gang."); return true; }
                g.members.add(target.getName());
                playerGang.put(target.getUniqueId(), g.name.toLowerCase());
                saveConfig();
                target.sendMessage("§aYou joined gang '" + g.name + "'!");
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                player.sendMessage("§a" + target.getName() + " joined the gang.");
                break;

            case "join":
                if (args.length < 2) { player.sendMessage("§cUsage: /gang join <name>"); return true; }
                if (playerGang.containsKey(player.getUniqueId())) { player.sendMessage("§cYou are already in a gang!"); return true; }
                GangData joinGang = gangs.get(args[1].toLowerCase());
                if (joinGang == null) { player.sendMessage("§cGang not found."); return true; }
                joinGang.members.add(player.getName());
                playerGang.put(player.getUniqueId(), joinGang.name.toLowerCase());
                saveConfig();
                player.sendMessage("§aYou joined gang '" + joinGang.name + "'!");
                for (String m : joinGang.members) {
                    Player mp = Bukkit.getPlayerExact(m);
                    if (mp != null) mp.sendMessage("§a" + player.getName() + " joined the gang!");
                }
                break;

            case "leave":
                if (!playerGang.containsKey(player.getUniqueId())) { player.sendMessage("§cYou are not in a gang!"); return true; }
                GangData leaveGang = gangs.get(playerGang.get(player.getUniqueId()));
                if (leaveGang == null) return true;
                if (leaveGang.leader.equals(player.getName())) {
                    player.sendMessage("§cYou are the leader! Use /gang disband or transfer leadership.");
                    return true;
                }
                leaveGang.members.remove(player.getName());
                playerGang.remove(player.getUniqueId());
                saveConfig();
                player.sendMessage("§eYou left the gang.");
                for (String m : leaveGang.members) {
                    Player mp = Bukkit.getPlayerExact(m);
                    if (mp != null) mp.sendMessage("§e" + player.getName() + " left the gang.");
                }
                break;

            case "disband":
                if (!playerGang.containsKey(player.getUniqueId())) { player.sendMessage("§cYou are not in a gang!"); return true; }
                GangData disbandGang = gangs.get(playerGang.get(player.getUniqueId()));
                if (disbandGang == null || !disbandGang.leader.equals(player.getName())) { player.sendMessage("§cOnly the leader can disband!"); return true; }
                for (String m : disbandGang.members) {
                    playerGang.remove(Bukkit.getOfflinePlayer(m).getUniqueId());
                    Player mp = Bukkit.getPlayerExact(m);
                    if (mp != null) mp.sendMessage("§cYour gang '" + disbandGang.name + "' was disbanded.");
                }
                gangs.remove(disbandGang.name.toLowerCase());
                saveConfig();
                player.sendMessage("§cGang disbanded.");
                break;

            case "info": {
                String gName = args.length >= 2 ? args[1] : (playerGang.containsKey(player.getUniqueId()) ? gangs.get(playerGang.get(player.getUniqueId())).name : null);
                if (gName == null) { player.sendMessage("§cUsage: /gang info <name>"); return true; }
                GangData infoGang = gangs.get(gName.toLowerCase());
                if (infoGang == null) { player.sendMessage("§cGang not found."); return true; }
                player.sendMessage("§6§l=== " + infoGang.name + " ===");
                player.sendMessage("§eLeader: §f" + infoGang.leader);
                player.sendMessage("§eMembers: §f" + String.join("§7, §f", infoGang.members));
                player.sendMessage("§eBalance: §a$" + String.format("%.2f", infoGang.balance));
                break;
            }

            case "balance":
                if (!playerGang.containsKey(player.getUniqueId())) { player.sendMessage("§cYou are not in a gang!"); return true; }
                GangData balGang = gangs.get(playerGang.get(player.getUniqueId()));
                player.sendMessage("§eGang balance: §a$" + String.format("%.2f", balGang.balance));
                break;

            case "deposit":
                if (args.length < 2) { player.sendMessage("§cUsage: /gang deposit <amount>"); return true; }
                if (!playerGang.containsKey(player.getUniqueId())) { player.sendMessage("§cYou are not in a gang!"); return true; }
                try {
                    double dep = Double.parseDouble(args[1]);
                    if (dep <= 0) throw new NumberFormatException();
                    if (plugin.getEconomyManager().subtractBalance(player.getUniqueId(), dep)) {
                        GangData depGang = gangs.get(playerGang.get(player.getUniqueId()));
                        depGang.balance += dep;
                        saveConfig();
                        player.sendMessage("§aDeposited $" + String.format("%.2f", dep) + " to gang.");
                    } else {
                        player.sendMessage("§cInsufficient balance!");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid amount.");
                }
                break;

            case "chat":
            case "c":
                if (args.length < 2) { player.sendMessage("§cUsage: /gang chat <message>"); return true; }
                if (!playerGang.containsKey(player.getUniqueId())) { player.sendMessage("§cYou are not in a gang!"); return true; }
                String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                GangData chatGang = gangs.get(playerGang.get(player.getUniqueId()));
                for (String m : chatGang.members) {
                    Player mp = Bukkit.getPlayerExact(m);
                    if (mp != null) mp.sendMessage("§b[G] §f" + player.getName() + "§7: §f" + msg);
                }
                break;

            default:
                player.sendMessage("§cUnknown gang command.");
        }
        return true;
    }

    public String getPlayerGang(Player player) {
        String g = playerGang.get(player.getUniqueId());
        return g != null ? gangs.get(g).name : null;
    }
}
