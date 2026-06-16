package com.prisoncore;

import com.prisoncore.backup.BackupManager;
import com.prisoncore.clock.PrisonClock;
import com.prisoncore.economy.EconomyManager;
import com.prisoncore.gang.GangManager;
import com.prisoncore.bounty.BountyManager;
import com.prisoncore.lottery.LotteryManager;
import com.prisoncore.social.SocialManager;
import com.prisoncore.mechanics.ContrabandTask;
import com.prisoncore.mechanics.PrisonListener;
import com.prisoncore.prisonrank.PrisonRankManager;
import com.prisoncore.rank.Rank;
import com.prisoncore.rank.RankManager;
import com.prisoncore.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public final class PrisonCore extends JavaPlugin implements CommandExecutor, org.bukkit.command.TabCompleter {
    private RankManager rankManager;
    private EconomyManager economyManager;
    private BackupManager backupManager;
    private PrisonClock clock;
    private ContrabandTask contrabandTask;
    private ShopManager shopManager;
    private PrisonRankManager prisonRankManager;
    private GangManager gangManager;
    private BountyManager bountyManager;
    private LotteryManager lotteryManager;
    private SocialManager socialManager;

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

        // Initialize Shop Manager
        this.shopManager = new ShopManager(this);

        // Initialize Prison Rank Manager
        this.prisonRankManager = new PrisonRankManager(this);

        // Initialize Gang Manager
        this.gangManager = new GangManager(this);

        // Initialize Bounty Manager
        this.bountyManager = new BountyManager(this);

        // Initialize Lottery Manager
        this.lotteryManager = new LotteryManager(this);

        // Initialize Social Manager
        this.socialManager = new SocialManager(this);

        // Initialize and Start Contraband Scanner Task (runs every second)
        this.contrabandTask = new ContrabandTask(this);
        this.contrabandTask.runTaskTimer(this, 0L, 20L);

        // Register Event Listeners
        PrisonListener listener = new PrisonListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

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
        if (getCommand("shop") != null) {
            getCommand("shop").setExecutor(shopManager);
        }
        if (getCommand("gang") != null) {
            getCommand("gang").setExecutor(gangManager);
        }
        if (getCommand("bounty") != null) {
            getCommand("bounty").setExecutor(bountyManager);
        }
        if (getCommand("lottery") != null) {
            getCommand("lottery").setExecutor(lotteryManager);
        }
        if (getCommand("msg") != null) {
            getCommand("msg").setExecutor(socialManager);
        }
        if (getCommand("reply") != null) {
            getCommand("reply").setExecutor(socialManager);
        }
        if (getCommand("r") != null) {
            getCommand("r").setExecutor(socialManager);
        }
        if (getCommand("tpa") != null) {
            getCommand("tpa").setExecutor(socialManager);
        }
        if (getCommand("tpaccept") != null) {
            getCommand("tpaccept").setExecutor(socialManager);
        }
        if (getCommand("tpdeny") != null) {
            getCommand("tpdeny").setExecutor(socialManager);
        }
        if (getCommand("daily") != null) {
            getCommand("daily").setExecutor(this);
        }
        if (getCommand("baltop") != null) {
            getCommand("baltop").setExecutor(this);
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
        } else if (command.getName().equalsIgnoreCase("daily")) {
            return prisonRankManager.claimDaily(player);
        } else if (command.getName().equalsIgnoreCase("baltop")) {
            showBaltop(player);
            return true;
        }

        return false;
    }

    private boolean handleSetRankCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /setrank <player> <Admin|Guard|PCI|Prisoner>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cPlayer not found or offline.");
            return true;
        }

        Rank rank = Rank.fromName(args[1]);
        if (rank == null) {
            sender.sendMessage("§cInvalid rank. Options: Admin, Guard, PCI, Prisoner");
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

    public ShopManager getShopManager() {
        return shopManager;
    }

    public PrisonRankManager getPrisonRankManager() {
        return prisonRankManager;
    }

    public GangManager getGangManager() {
        return gangManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public LotteryManager getLotteryManager() {
        return lotteryManager;
    }

    public SocialManager getSocialManager() {
        return socialManager;
    }

    private void showBaltop(Player player) {
        java.util.Map<UUID, Double> all = new java.util.HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            all.put(p.getUniqueId(), economyManager.getBalance(p));
        }
        List<java.util.Map.Entry<UUID, Double>> sorted = new java.util.ArrayList<>(all.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        player.sendMessage("§6§l=== TOP BALANCES ===");
        int rank = 1;
        for (java.util.Map.Entry<UUID, Double> e : sorted) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            if (name == null) continue;
            String prefix = rank == 1 ? "§e#1" : rank == 2 ? "§7#2" : rank == 3 ? "§6#3" : "§7#" + rank;
            player.sendMessage(prefix + " §f" + name + " §7- §a$" + String.format("%.2f", e.getValue()));
            rank++;
            if (rank > 10) break;
        }
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
        } else if (rank == Rank.PCI) {
            spawnLoc = loadLocation("spawns.pci");
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

    // Vent System
    public void addVentLocation(Location loc) {
        List<Map<String, Object>> vents = (List<Map<String, Object>>) getConfig().getList("vents", new java.util.ArrayList<>());
        Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("world", loc.getWorld().getName());
        entry.put("x", loc.getX());
        entry.put("y", loc.getY());
        entry.put("z", loc.getZ());
        entry.put("yaw", (double) loc.getYaw());
        entry.put("pitch", (double) loc.getPitch());
        vents.add(entry);
        getConfig().set("vents", vents);
        saveConfig();
    }

    public List<Location> getVentLocations() {
        List<Map<String, Object>> vents = (List<Map<String, Object>>) getConfig().getList("vents", new java.util.ArrayList<>());
        List<Location> result = new java.util.ArrayList<>();
        for (Map<String, Object> entry : vents) {
            String worldName = (String) entry.get("world");
            if (worldName == null) continue;
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            double x = ((Number) entry.get("x")).doubleValue();
            double y = ((Number) entry.get("y")).doubleValue();
            double z = ((Number) entry.get("z")).doubleValue();
            float yaw = ((Number) entry.get("yaw")).floatValue();
            float pitch = ((Number) entry.get("pitch")).floatValue();
            result.add(new Location(world, x, y, z, yaw, pitch));
        }
        return result;
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
