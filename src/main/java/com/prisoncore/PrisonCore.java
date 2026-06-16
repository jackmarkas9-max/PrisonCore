package com.prisoncore;

import com.prisoncore.backup.BackupManager;
import com.prisoncore.clock.PrisonClock;
import com.prisoncore.economy.EconomyManager;
import com.prisoncore.mechanics.ContrabandTask;
import com.prisoncore.mechanics.PrisonListener;
import com.prisoncore.rank.Rank;
import com.prisoncore.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PrisonCore extends JavaPlugin implements CommandExecutor, org.bukkit.command.TabCompleter {
    private RankManager rankManager;
    private EconomyManager economyManager;
    private BackupManager backupManager;
    private PrisonClock clock;
    private ContrabandTask contrabandTask;

    private boolean buildModeActive = false;

    @Override
    public void onEnable() {
        // Save default config.yml
        saveDefaultConfig();

        // Initialize Managers (Rank must be first, then Economy, then Backup)
        this.rankManager = new RankManager(this);
        this.economyManager = new EconomyManager(this);
        this.backupManager = new BackupManager(this);

        // Initialize and Start Custom Clock Task (runs every tick)
        this.clock = new PrisonClock(this);
        this.clock.runTaskTimer(this, 0L, 1L);

        // Initialize and Start Contraband Scanner Task (runs every second)
        this.contrabandTask = new ContrabandTask(this);
        this.contrabandTask.runTaskTimer(this, 0L, 20L);

        // Register Event Listeners
        getServer().getPluginManager().registerEvents(new PrisonListener(this), this);

        // Register Command Executors
        if (getCommand("pay") != null) {
            getCommand("pay").setExecutor(this);
            getCommand("pay").setTabCompleter(this);
        }
        if (getCommand("requestpay") != null) {
            getCommand("requestpay").setExecutor(this);
            getCommand("requestpay").setTabCompleter(this);
        }
        if (getCommand("setrank") != null) {
            getCommand("setrank").setExecutor(this);
        }

        getLogger().info("PrisonCore 1.0 has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (this.clock != null) {
            this.clock.cleanUp();
        }
        getLogger().info("PrisonCore 1.0 has been disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setrank")) {
            return handleSetRankCommand(sender, args);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can execute prison economy commands.");
            return true;
        }

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("pay")) {
            return economyManager.handlePayCommand(player, args);
        } else if (command.getName().equalsIgnoreCase("requestpay")) {
            return economyManager.handleRequestPayCommand(player, args);
        }

        return false;
    }

    private boolean handleSetRankCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /setrank <player> <Admin|Guard|Prisoner>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cPlayer not found or offline.");
            return true;
        }

        Rank rank = Rank.fromName(args[1]);
        if (rank == null) {
            sender.sendMessage("§cInvalid rank. Options: Admin, Guard, Prisoner");
            return true;
        }

        rankManager.setRank(target, rank);
        sender.sendMessage("§aSet rank of " + target.getName() + " to " + rank.getName() + ".");
        target.sendMessage("§aYour rank has been set to " + rank.getName() + " by " + sender.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new java.util.ArrayList<>();
        if (!(sender instanceof Player)) return completions;

        String cmd = command.getName().toLowerCase();
        if (cmd.equals("pay") || cmd.equals("requestpay")) {
            if (args.length == 1) {
                String input = args[0].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input) && !p.equals(sender)) {
                        completions.add(p.getName());
                    }
                }
            } else if (args.length == 2) {
                String input = args[1].toLowerCase();
                List<String> amounts = List.of("10", "50", "100", "500", "1000");
                for (String amt : amounts) {
                    if (amt.startsWith(input)) {
                        completions.add(amt);
                    }
                }
            } else if (cmd.equals("requestpay") && args.length == 3) {
                String input = args[2].toLowerCase();
                List<String> reasons = List.of("rent", "contraband", "protection", "tax", "fine");
                for (String r : reasons) {
                    if (r.startsWith(input)) {
                        completions.add(r);
                    }
                }
            }
        }
        return completions;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public PrisonClock getClock() {
        return clock;
    }

    public ContrabandTask getContrabandTask() {
        return contrabandTask;
    }

    public boolean isBuildModeActive() {
        return buildModeActive;
    }

    public void setBuildModeActive(boolean buildModeActive) {
        this.buildModeActive = buildModeActive;
    }

    // Spawn & Location Utilities
    public void saveLocation(String path, Location loc) {
        getConfig().set(path + ".world", loc.getWorld().getName());
        getConfig().set(path + ".x", loc.getX());
        getConfig().set(path + ".y", loc.getY());
        getConfig().set(path + ".z", loc.getZ());
        getConfig().set(path + ".yaw", (double) loc.getYaw());
        getConfig().set(path + ".pitch", (double) loc.getPitch());
        saveConfig();
    }

    public Location loadLocation(String path) {
        if (!getConfig().contains(path) || getConfig().get(path) == null) return null;
        String worldName = getConfig().getString(path + ".world");
        if (worldName == null) return null;
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = getConfig().getDouble(path + ".x");
        double y = getConfig().getDouble(path + ".y");
        double z = getConfig().getDouble(path + ".z");
        float yaw = (float) getConfig().getDouble(path + ".yaw");
        float pitch = (float) getConfig().getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void teleportPlayerToRankSpawn(Player player) {
        Rank rank = rankManager.getRank(player);
        Location spawnLoc = null;

        if (rank == Rank.ADMIN) {
            spawnLoc = loadLocation("spawns.all");
        } else if (rank == Rank.GUARD) {
            spawnLoc = loadLocation("spawns.police");
            if (spawnLoc == null) spawnLoc = loadLocation("spawns.all");
        } else if (rank == Rank.PRISONER) {
            spawnLoc = loadLocation("spawns.prison");
            if (spawnLoc == null) spawnLoc = loadLocation("spawns.all");
        }

        if (spawnLoc != null) {
            player.teleport(spawnLoc);
            player.sendMessage("§aTeleported to your rank spawn location.");
        } else {
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage("§cNo spawn location set for your rank! Teleported to world spawn.");
        }
    }

    // Guard Whistle factory
    public static ItemStack createWhistle() {
        ItemStack shears = new ItemStack(Material.SHEARS);
        ItemMeta meta = shears.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("§bGuard Whistle", net.kyori.adventure.text.format.NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.BOLD));
            List<net.kyori.adventure.text.Component> lore = List.of(
                net.kyori.adventure.text.Component.text("§7Right-click to alert nearby prisoners"),
                net.kyori.adventure.text.Component.text("§7Makes prisoners glow for 5 seconds.")
            );
            meta.lore(lore);
            shears.setItemMeta(meta);
        }
        return shears;
    }

    // Prison Key factory
    public static ItemStack createKey() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("Key", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE, net.kyori.adventure.text.format.TextDecoration.BOLD));
            List<net.kyori.adventure.text.Component> lore = List.of(
                net.kyori.adventure.text.Component.text("§7Right-click on an Iron Door to open it for 3 seconds.")
            );
            meta.lore(lore);
            key.setItemMeta(meta);
        }
        return key;
    }
}
