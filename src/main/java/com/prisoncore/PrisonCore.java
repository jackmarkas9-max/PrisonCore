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
import java.util.Map;
import java.util.UUID;

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
        getServer().getPluginManager().registerEvents(shopManager, this);

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

        // Register Admin Command Executors & Tab Completers
        String[] adminCmds = {"rank", "give", "day", "night", "setallspawn", "setpolicespawn", "setprisonspawn", "setpcispawn", "mode", "save", "wandescape", "escape", "setvent"};
        for (String ac : adminCmds) {
            if (getCommand(ac) != null) {
                getCommand(ac).setExecutor(this);
                getCommand(ac).setTabCompleter(this);
            }
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
        String cmd = command.getName().toLowerCase();

        // Admin commands - check permission
        if (isAdminCommand(cmd)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use admin commands.");
                return true;
            }
            Player player = (Player) sender;
            Rank rank = rankManager.getRank(player);
            if (rank != Rank.ADMIN && rank != Rank.PCI && !player.getName().equals("Markusha111")) {
                player.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }
            return executeAdminCommand(player, cmd, args);
        }

        if (cmd.equals("setrank")) {
            return handleSetRankCommand(sender, args);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can execute prison economy commands.");
            return true;
        }

        Player player = (Player) sender;
        if (cmd.equals("pay")) {
            return economyManager.handlePayCommand(player, args);
        } else if (cmd.equals("requestpay")) {
            return economyManager.handleRequestPayCommand(player, args);
        } else if (cmd.equals("daily")) {
            return prisonRankManager.claimDaily(player);
        } else if (cmd.equals("baltop")) {
            showBaltop(player);
            return true;
        }

        return false;
    }

    private boolean isAdminCommand(String cmd) {
        return cmd.equals("rank") || cmd.equals("give") || cmd.equals("day") || cmd.equals("night") ||
               cmd.equals("setallspawn") || cmd.equals("setpolicespawn") || cmd.equals("setprisonspawn") ||
               cmd.equals("setpcispawn") || cmd.equals("mode") || cmd.equals("save") ||
               cmd.equals("wandescape") || cmd.equals("escape") || cmd.equals("setvent");
    }

    private boolean executeAdminCommand(Player admin, String cmd, String[] args) {
        switch (cmd) {
            case "rank":
                if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
                    admin.sendMessage("§cUsage: /rank give <player> <Admin|Guard|PCI|Prisoner>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    admin.sendMessage("§cPlayer not found.");
                    return true;
                }
                Rank rank = Rank.fromName(args[2]);
                if (rank == null) {
                    admin.sendMessage("§cInvalid rank. Options: Admin, Guard, PCI, Prisoner");
                    return true;
                }
                rankManager.setRank(target, rank);
                admin.sendMessage("§aSet rank of " + target.getName() + " to " + rank.getName() + ".");
                return true;

            case "give":
                if (args.length < 3 || !args[0].equalsIgnoreCase("money")) {
                    admin.sendMessage("§cUsage: /give money <player> <amount>");
                    return true;
                }
                Player giveTarget = Bukkit.getPlayer(args[1]);
                if (giveTarget == null || !giveTarget.isOnline()) {
                    admin.sendMessage("§cPlayer not found.");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                    if (amount <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    admin.sendMessage("§cInvalid amount. Must be a positive number.");
                    return true;
                }
                economyManager.addBalance(giveTarget.getUniqueId(), amount);
                admin.sendMessage("§aGave $" + String.format("%.2f", amount) + " to " + giveTarget.getName() + ".");
                giveTarget.sendMessage("§aYou received $" + String.format("%.2f", amount) + " from Admin " + admin.getName() + ".");
                return true;

            case "day":
                clock.setDay();
                admin.sendMessage("§eTime set to Day.");
                return true;

            case "night":
                clock.setNight();
                admin.sendMessage("§eTime set to Night.");
                return true;

            case "setallspawn":
                saveLocation("spawns.all", admin.getLocation());
                admin.sendMessage("§aSpawn for all ranks set to your location.");
                return true;

            case "setpolicespawn":
                saveLocation("spawns.police", admin.getLocation());
                admin.sendMessage("§aPolice spawn set to your location.");
                return true;

            case "setprisonspawn":
                saveLocation("spawns.prison", admin.getLocation());
                admin.sendMessage("§aPrisoner spawn set to your location.");
                return true;

            case "setpcispawn":
                saveLocation("spawns.pci", admin.getLocation());
                admin.sendMessage("§aPCI spawn set to your location.");
                return true;

            case "mode":
                if (args.length < 1) {
                    admin.sendMessage("§cUsage: /mode <build|prison>");
                    return true;
                }
                String mode = args[0].toLowerCase();
                if (mode.equals("build")) {
                    setBuildModeActive(true);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.setGameMode(org.bukkit.GameMode.CREATIVE);
                        p.sendMessage("§d[Prison] Build mode enabled. Everyone is now in Creative mode.");
                    }
                } else if (mode.equals("prison")) {
                    setBuildModeActive(false);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        Rank r = rankManager.getRank(p);
                        if (r == Rank.ADMIN) {
                            p.setGameMode(org.bukkit.GameMode.CREATIVE);
                        } else {
                            p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                        }
                        p.sendMessage("§d[Prison] Prison mode enabled. Teleporting to designated rank spawns.");
                        teleportPlayerToRankSpawn(p);
                    }
                } else {
                    admin.sendMessage("§cInvalid mode. Use: build, prison");
                }
                return true;

            case "save":
                backupManager.saveArea(admin);
                return true;

            case "wandescape":
                ItemStack wand = new ItemStack(Material.WOODEN_AXE);
                ItemMeta wandMeta = wand.getItemMeta();
                if (wandMeta != null) {
                    wandMeta.displayName(net.kyori.adventure.text.Component.text("§5Escape Wand", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE, net.kyori.adventure.text.format.TextDecoration.BOLD));
                    wandMeta.lore(List.of(
                        net.kyori.adventure.text.Component.text("§7Right-click: Set position 1"),
                        net.kyori.adventure.text.Component.text("§7Left-click: Set position 2"),
                        net.kyori.adventure.text.Component.text("§7Prisoners inside the zone escape!")
                    ));
                    wand.setItemMeta(wandMeta);
                }
                admin.getInventory().addItem(wand);
                admin.sendMessage("§5Use the Escape Wand to set the escape zone corners.");
                admin.playSound(admin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                return true;

            case "escape":
                if (args.length < 1) {
                    admin.sendMessage("§cUsage: /escape <player>");
                    return true;
                }
                Player escapeTarget = Bukkit.getPlayer(args[0]);
                if (escapeTarget == null || !escapeTarget.isOnline()) {
                    admin.sendMessage("§cPlayer not found.");
                    return true;
                }
                rankManager.setRank(escapeTarget, Rank.PCI);
                escapeTarget.playSound(escapeTarget.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
                escapeTarget.showTitle(net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.text("YOU ESCAPED!", net.kyori.adventure.text.format.NamedTextColor.RED),
                    net.kyori.adventure.text.Component.text("You are now PCI. You escaped prison!", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                ));
                admin.sendMessage("§a" + escapeTarget.getName() + " has been marked as escaped (PCI).");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Rank r = rankManager.getRank(p);
                    if (r == Rank.GUARD || r == Rank.ADMIN || r == Rank.PCI) {
                        if (!p.equals(admin) && !p.equals(escapeTarget)) {
                            p.sendMessage("§c§l[ALERT] §e" + escapeTarget.getName() + " §7has escaped prison!");
                        }
                    }
                }
                return true;

            case "setvent":
                addVentLocation(admin.getLocation());
                admin.sendMessage("§aVent location added at your position.");
                admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                return true;

            default:
                admin.sendMessage("§cUnknown admin command: " + cmd);
                return true;
        }
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

        // Admin command tab completion
        if (isAdminCommand(cmd)) {
            Player player = (Player) sender;
            Rank rank = rankManager.getRank(player);
            if (rank != Rank.ADMIN && rank != Rank.PCI && !player.getName().equals("Markusha111")) {
                return completions;
            }

            if (cmd.equals("rank") && args.length == 1) {
                String input = args[0].toLowerCase();
                if ("give".startsWith(input)) completions.add("give");
            } else if (cmd.equals("rank") && args.length == 2 && args[0].equalsIgnoreCase("give")) {
                String input = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
                }
            } else if (cmd.equals("rank") && args.length == 3 && args[0].equalsIgnoreCase("give")) {
                String input = args[2].toLowerCase();
                for (Rank r : Rank.values()) {
                    if (r.getName().toLowerCase().startsWith(input)) completions.add(r.getName());
                }
            } else if (cmd.equals("give") && args.length == 1) {
                String input = args[0].toLowerCase();
                if ("money".startsWith(input)) completions.add("money");
            } else if (cmd.equals("give") && args.length == 2 && args[0].equalsIgnoreCase("money")) {
                String input = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
                }
            } else if (cmd.equals("give") && args.length == 3 && args[0].equalsIgnoreCase("money")) {
                String input = args[2].toLowerCase();
                List<String> amounts = List.of("10", "50", "100", "500", "1000");
                for (String amt : amounts) {
                    if (amt.startsWith(input)) completions.add(amt);
                }
            } else if ((cmd.equals("escape") || cmd.equals("mode")) && args.length == 1) {
                String input = args[0].toLowerCase();
                if (cmd.equals("mode")) {
                    if ("build".startsWith(input)) completions.add("build");
                    if ("prison".startsWith(input)) completions.add("prison");
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
                    }
                }
            }
            return completions;
        }

        // Regular command tab completion
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

    // Prison Key factory (CustomModelData: 1001 for keyskin.png)
    public static ItemStack createKey() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("§5Key", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE, net.kyori.adventure.text.format.TextDecoration.BOLD));
            meta.setCustomModelData(1001);
            List<net.kyori.adventure.text.Component> lore = List.of(
                net.kyori.adventure.text.Component.text("§7Right-click an Iron Door to open it for 3s.")
            );
            meta.lore(lore);
            key.setItemMeta(meta);
        }
        return key;
    }

    // Hacking Tool factory (CustomModelData: 1003 for hackingtool.png)
    public static ItemStack createHackingTool() {
        ItemStack tool = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("§aHacking Tool", net.kyori.adventure.text.format.NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.BOLD));
            meta.setCustomModelData(1003);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            List<net.kyori.adventure.text.Component> lore = List.of(
                net.kyori.adventure.text.Component.text("§7Right-click an Iron Door to open it for 3s.")
            );
            meta.lore(lore);
            tool.setItemMeta(meta);
        }
        return tool;
    }
}
