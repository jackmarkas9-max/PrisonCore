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
import org.bukkit.inventory.ItemFlag;
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

    private PrisonListener prisonListener;
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
        prisonListener = new PrisonListener(this);
        getServer().getPluginManager().registerEvents(prisonListener, this);
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
        if (getCommand("darkshop") != null) {
            getCommand("darkshop").setExecutor(this);
        }

        // Register Admin Command Executors & Tab Completers
        String[] adminCmds = {"give", "day", "night", "setallspawn", "setpolicespawn", "setprisonspawn", "setpcispawn", "mode", "save", "wandescape", "escape", "setvent", "clearmoney", "setcell", "checkguy", "reqs"};
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

            // Allow Guards to use checkguy, otherwise require Admin
            boolean hasPerm = rank == Rank.ADMIN || player.getName().equals("Markusha111");
            if (!hasPerm && cmd.equals("checkguy")) {
                hasPerm = rank == Rank.GUARD;
            }
            if (!hasPerm) {
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
        } else if (cmd.equals("darkshop")) {
            openDarkShop(player);
            return true;
        }

        return false;
    }

    private boolean isAdminCommand(String cmd) {
        return cmd.equals("give") || cmd.equals("day") || cmd.equals("night") ||
               cmd.equals("setallspawn") || cmd.equals("setpolicespawn") || cmd.equals("setprisonspawn") ||
               cmd.equals("setpcispawn") || cmd.equals("mode") || cmd.equals("save") ||
               cmd.equals("wandescape") || cmd.equals("escape") || cmd.equals("setvent") ||
               cmd.equals("clearmoney") || cmd.equals("setcell") || cmd.equals("checkguy") || cmd.equals("reqs");
    }

    private boolean executeAdminCommand(Player admin, String cmd, String[] args) {
        switch (cmd) {
            case "give":
                if (args.length < 1) {
                    admin.sendMessage("§cUsage: /give money <player> <amount>  or  /give rank <player> <Admin|Guard|PCI|Prisoner>");
                    return true;
                }
                if (args[0].equalsIgnoreCase("rank")) {
                    if (args.length < 3) {
                        admin.sendMessage("§cUsage: /give rank <player> <Admin|Guard|PCI|Prisoner>");
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
                }
                if (args[0].equalsIgnoreCase("money")) {
                    if (args.length < 3) {
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
                }
                admin.sendMessage("§cUsage: /give money <player> <amount>  or  /give rank <player> <Admin|Guard|PCI|Prisoner>");
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
                admin.getInventory().addItem(makeWand(Material.WOODEN_AXE, "§eAll Spawn Wand", "All ranks teleport here"));
                admin.sendMessage("§eUse the wand to set the all-ranks spawn zone.");
                admin.playSound(admin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                return true;

            case "setpolicespawn":
                admin.getInventory().addItem(makeWand(Material.WOODEN_AXE, "§bPolice Spawn Wand", "Police/Guard teleport here"));
                admin.sendMessage("§bUse the wand to set the police spawn zone.");
                admin.playSound(admin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                return true;

            case "setprisonspawn":
                admin.getInventory().addItem(makeWand(Material.WOODEN_AXE, "§cPrison Spawn Wand", "Prisoner teleport here"));
                admin.sendMessage("§cUse the wand to set the prisoner spawn zone.");
                admin.playSound(admin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                return true;

            case "setpcispawn":
                admin.getInventory().addItem(makeWand(Material.WOODEN_AXE, "§aPCI Spawn Wand", "PCI teleport here"));
                admin.sendMessage("§aUse the wand to set the PCI spawn zone.");
                admin.playSound(admin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
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
                admin.getInventory().addItem(makeWand(Material.WOODEN_AXE,
                    "§5Escape Wand",
                    "Prisoners inside the zone escape!"));
                admin.sendMessage("§5Use the Escape Wand to set the escape zone corners.");
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
                if (args.length < 1) {
                    admin.sendMessage("§cUsage: /setvent <id>");
                    return true;
                }
                int ventId;
                try {
                    ventId = Integer.parseInt(args[0]);
                    if (ventId < 1) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    admin.sendMessage("§cID must be a positive number.");
                    return true;
                }
                addVentLocation(ventId, admin.getLocation());
                admin.sendMessage("§aVent #" + ventId + " added at your position.");
                admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                return true;

            case "clearmoney":
                if (args.length < 1) {
                    admin.sendMessage("§cUsage: /clearmoney <player>");
                    return true;
                }
                Player cmTarget = Bukkit.getPlayer(args[0]);
                if (cmTarget == null || !cmTarget.isOnline()) {
                    admin.sendMessage("§cPlayer not found.");
                    return true;
                }
                economyManager.setBalance(cmTarget.getUniqueId(), 0.0);
                admin.sendMessage("§aCleared all money from " + cmTarget.getName() + ".");
                cmTarget.sendMessage("§cYour money has been cleared by " + admin.getName() + ".");
                return true;

            case "setcell":
                admin.getInventory().addItem(makeWand(Material.GOLDEN_AXE,
                    "§6Cell Wand",
                    "PCI inside this zone → prisoner"));
                admin.sendMessage("§6Use the Cell Wand to set the cell zone corners.");
                admin.playSound(admin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                return true;

            case "checkguy":
                if (args.length < 1) {
                    admin.sendMessage("§cUsage: /checkguy <player>");
                    return true;
                }
                Player cgTarget = Bukkit.getPlayer(args[0]);
                if (cgTarget == null || !cgTarget.isOnline()) {
                    admin.sendMessage("§cPlayer not found.");
                    return true;
                }
                getPrisonListener().openCheckGuyInventory(admin, cgTarget);
                return true;

            case "reqs":
                showReqsGUI(admin);
                return true;

            default:
                admin.sendMessage("§cUnknown admin command: " + cmd);
                return true;
        }
    }

    private void showReqsGUI(Player admin) {
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 18, net.kyori.adventure.text.Component.text("§8Requirements Checklist"));

        // Check each requirement
        gui.setItem(0, makeReqItem(getSpawnZonePos1("spawns.all") != null && getSpawnZonePos2("spawns.all") != null, "§7Default Spawn Zone", "All ranks spawn zone"));
        gui.setItem(1, makeReqItem(getSpawnZonePos1("spawns.police") != null && getSpawnZonePos2("spawns.police") != null, "§bPolice Spawn Zone", "Police/Guard spawn zone"));
        gui.setItem(2, makeReqItem(getSpawnZonePos1("spawns.prison") != null && getSpawnZonePos2("spawns.prison") != null, "§cPrisoner Spawn Zone", "Prisoner spawn zone"));
        gui.setItem(3, makeReqItem(getSpawnZonePos1("spawns.pci") != null && getSpawnZonePos2("spawns.pci") != null, "§aPCI Spawn Zone", "PCI spawn zone"));
        gui.setItem(4, makeReqItem(loadLocation("escape-zone.pos1") != null && loadLocation("escape-zone.pos2") != null, "§6Escape Zone", "Prisoner → PCI promotion zone"));
        gui.setItem(5, makeReqItem(getCellZonePos1() != null && getCellZonePos2() != null, "§eCell Zone", "PCI → Prisoner demotion zone"));
        gui.setItem(6, makeReqItem(!getVentLocations().isEmpty(), "§8Vents", "At least 1 vent location"));

        // Fill empty slots
        ItemStack fill = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillMeta = fill.getItemMeta();
        if (fillMeta != null) {
            fillMeta.displayName(net.kyori.adventure.text.Component.text(""));
            fill.setItemMeta(fillMeta);
        }
        for (int i = 0; i < 18; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, fill);
        }

        admin.openInventory(gui);
        admin.playSound(admin.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    private ItemStack makeReqItem(boolean done, String title, String description) {
        ItemStack item = new ItemStack(done ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String color = done ? "§a✔ " : "§c✘ ";
            String status = done ? "§aSET" : "§cNOT SET";
            meta.displayName(net.kyori.adventure.text.Component.text(color + title));
            meta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7" + description),
                net.kyori.adventure.text.Component.text("§7Status: " + status)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openDarkShop(Player player) {
        Rank rank = rankManager.getRank(player);
        if (rank != Rank.PCI && rank != Rank.PRISONER) {
            player.sendMessage("§cOnly PCI and Prisoners can use the dark shop!");
            return;
        }
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 18, net.kyori.adventure.text.Component.text("§8Dark Shop"));
        // Screwdriver — slot 0
        ItemStack screwItem = new ItemStack(Material.SHEARS);
        ItemMeta screwMeta = screwItem.getItemMeta();
        if (screwMeta != null) {
            screwMeta.displayName(net.kyori.adventure.text.Component.text("§8Screwdriver"));
            screwMeta.setCustomModelData(1004);
            screwMeta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7Allows you to use vents"),
                net.kyori.adventure.text.Component.text("§7Cost: §a$150,000")
            ));
            screwItem.setItemMeta(screwMeta);
        }
        inv.setItem(0, screwItem);
        // Police Key — slot 1
        ItemStack keyItem = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta keyMeta = keyItem.getItemMeta();
        if (keyMeta != null) {
            keyMeta.displayName(net.kyori.adventure.text.Component.text("§6Police Key"));
            keyMeta.setCustomModelData(1002);
            keyMeta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7A master key for security doors"),
                net.kyori.adventure.text.Component.text("§7Cost: §a$200,000")
            ));
            keyItem.setItemMeta(keyMeta);
        }
        inv.setItem(1, keyItem);
        // Fill remaining
        ItemStack fill = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillMeta = fill.getItemMeta();
        if (fillMeta != null) {
            fillMeta.displayName(net.kyori.adventure.text.Component.text(""));
            fill.setItemMeta(fillMeta);
        }
        for (int i = 0; i < 18; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, fill);
        }
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
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
            boolean hasPerm = rank == Rank.ADMIN || player.getName().equals("Markusha111");
            if (!hasPerm && cmd.equals("checkguy")) {
                hasPerm = rank == Rank.GUARD;
            }
            if (!hasPerm) {
                return completions;
            }

            if (cmd.equals("give") && args.length == 1) {
                String input = args[0].toLowerCase();
                if ("money".startsWith(input)) completions.add("money");
                if ("rank".startsWith(input)) completions.add("rank");
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
            } else if (cmd.equals("give") && args.length == 2 && args[0].equalsIgnoreCase("rank")) {
                String input = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
                }
            } else if (cmd.equals("give") && args.length == 3 && args[0].equalsIgnoreCase("rank")) {
                String input = args[2].toLowerCase();
                for (Rank r : Rank.values()) {
                    if (r.getName().toLowerCase().startsWith(input)) completions.add(r.getName());
                }
            } else if ((cmd.equals("escape") || cmd.equals("mode") || cmd.equals("clearmoney") || cmd.equals("checkguy")) && args.length == 1) {
                String input = args[0].toLowerCase();
                if (cmd.equals("mode")) {
                    if ("build".startsWith(input)) completions.add("build");
                    if ("prison".startsWith(input)) completions.add("prison");
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
                    }
                }
            } else if (cmd.equals("setvent") && args.length == 1) {
                String input = args[0].toLowerCase();
                for (int i = 1; i <= 10; i++) {
                    String s = String.valueOf(i);
                    if (s.startsWith(input)) completions.add(s);
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
            spawnLoc = getRandomSpawnLocation("spawns.all");
        } else if (rank == Rank.GUARD) {
            spawnLoc = getRandomSpawnLocation("spawns.police");
            if (spawnLoc == null) spawnLoc = getRandomSpawnLocation("spawns.all");
        } else if (rank == Rank.PCI) {
            spawnLoc = getRandomSpawnLocation("spawns.pci");
            if (spawnLoc == null) spawnLoc = getRandomSpawnLocation("spawns.all");
        } else if (rank == Rank.PRISONER) {
            spawnLoc = getRandomSpawnLocation("spawns.prison");
            if (spawnLoc == null) spawnLoc = getRandomSpawnLocation("spawns.all");
        }

        if (spawnLoc != null) {
            player.teleport(spawnLoc);
            player.sendMessage("§aTeleported to your rank spawn location.");
        } else {
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage("§cNo spawn location set for your rank! Teleported to world spawn.");
        }
    }

    // Spawn Zone helpers (wand-based boxes)
    public void saveSpawnZone(String spawnName, Location pos1, Location pos2) {
        getConfig().set(spawnName + ".pos1", locationToMap(pos1));
        getConfig().set(spawnName + ".pos2", locationToMap(pos2));
        saveConfig();
    }

    public Location getSpawnZonePos1(String spawnName) {
        return getLocationFromConfig(spawnName + ".pos1");
    }

    public Location getSpawnZonePos2(String spawnName) {
        return getLocationFromConfig(spawnName + ".pos2");
    }

    public Location getRandomSpawnLocation(String spawnName) {
        Location pos1 = getLocationFromConfig(spawnName + ".pos1");
        Location pos2 = getLocationFromConfig(spawnName + ".pos2");
        if (pos1 == null || pos2 == null) return null;
        if (!pos1.getWorld().equals(pos2.getWorld())) return null;
        World world = pos1.getWorld();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        java.util.Random rand = new java.util.Random();
        for (int attempts = 0; attempts < 20; attempts++) {
            int rx = minX + rand.nextInt(maxX - minX + 1);
            int rz = minZ + rand.nextInt(maxZ - minZ + 1);
            org.bukkit.block.Block highest = world.getHighestBlockAt(rx, rz);
            if (highest != null && !highest.getType().isAir()) {
                return highest.getLocation().add(0.5, 1, 0.5);
            }
        }
        // Fallback: center of zone at surface
        int cx = (minX + maxX) / 2;
        int cz = (minZ + maxZ) / 2;
        org.bukkit.block.Block center = world.getHighestBlockAt(cx, cz);
        if (center != null && !center.getType().isAir()) {
            return center.getLocation().add(0.5, 1, 0.5);
        }
        return new Location(world, cx + 0.5, Math.max(pos1.getY(), pos2.getY()), cz + 0.5);
    }

    public ItemStack makeWand(Material material, String displayName, String description) {
        ItemStack wand = new ItemStack(material);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text(displayName));
            meta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7Right-click: Set position 1"),
                net.kyori.adventure.text.Component.text("§7Left-click: Set position 2"),
                net.kyori.adventure.text.Component.text("§7" + description)
            ));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    // Vent System (with IDs)
    public void addVentLocation(int id, Location loc) {
        List<Map<String, Object>> vents = (List<Map<String, Object>>) getConfig().getList("vents", new java.util.ArrayList<>());
        // Remove existing entry with same ID
        vents.removeIf(e -> e.get("id") instanceof Number && ((Number) e.get("id")).intValue() == id);
        Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("id", id);
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

    public Location getVentLocation(int id) {
        List<Map<String, Object>> vents = (List<Map<String, Object>>) getConfig().getList("vents", new java.util.ArrayList<>());
        for (Map<String, Object> entry : vents) {
            if (!(entry.get("id") instanceof Number)) continue;
            if (((Number) entry.get("id")).intValue() != id) continue;
            String worldName = (String) entry.get("world");
            if (worldName == null) return null;
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            double x = ((Number) entry.get("x")).doubleValue();
            double y = ((Number) entry.get("y")).doubleValue();
            double z = ((Number) entry.get("z")).doubleValue();
            float yaw = ((Number) entry.get("yaw")).floatValue();
            float pitch = ((Number) entry.get("pitch")).floatValue();
            return new Location(world, x, y, z, yaw, pitch);
        }
        return null;
    }

    public Map<Integer, Location> getVentLocations() {
        List<Map<String, Object>> vents = (List<Map<String, Object>>) getConfig().getList("vents", new java.util.ArrayList<>());
        Map<Integer, Location> result = new java.util.LinkedHashMap<>();
        for (Map<String, Object> entry : vents) {
            if (!(entry.get("id") instanceof Number)) continue;
            int id = ((Number) entry.get("id")).intValue();
            String worldName = (String) entry.get("world");
            if (worldName == null) continue;
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            double x = ((Number) entry.get("x")).doubleValue();
            double y = ((Number) entry.get("y")).doubleValue();
            double z = ((Number) entry.get("z")).doubleValue();
            float yaw = ((Number) entry.get("yaw")).floatValue();
            float pitch = ((Number) entry.get("pitch")).floatValue();
            result.put(id, new Location(world, x, y, z, yaw, pitch));
        }
        return result;
    }

    // Cell Zone
    public void saveCellZone(Location pos1, Location pos2) {
        getConfig().set("cell-zone.pos1", locationToMap(pos1));
        getConfig().set("cell-zone.pos2", locationToMap(pos2));
        saveConfig();
    }

    public Location getCellZonePos1() {
        return getLocationFromConfig("cell-zone.pos1");
    }

    public Location getCellZonePos2() {
        return getLocationFromConfig("cell-zone.pos2");
    }

    public boolean isInCellZone(Location loc) {
        Location pos1 = getCellZonePos1();
        Location pos2 = getCellZonePos2();
        if (pos1 == null || pos2 == null) return false;
        if (!pos1.getWorld().equals(loc.getWorld())) return false;
        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX &&
               loc.getY() >= minY && loc.getY() <= maxY &&
               loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    private Map<String, Object> locationToMap(Location loc) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("world", loc.getWorld().getName());
        map.put("x", loc.getX());
        map.put("y", loc.getY());
        map.put("z", loc.getZ());
        map.put("yaw", (double) loc.getYaw());
        map.put("pitch", (double) loc.getPitch());
        return map;
    }

    private Location getLocationFromConfig(String path) {
        Map<String, Object> map = (Map<String, Object>) getConfig().get(path);
        if (map == null) return null;
        String worldName = (String) map.get("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = ((Number) map.get("x")).doubleValue();
        double y = ((Number) map.get("y")).doubleValue();
        double z = ((Number) map.get("z")).doubleValue();
        float yaw = ((Number) map.get("yaw")).floatValue();
        float pitch = ((Number) map.get("pitch")).floatValue();
        return new Location(world, x, y, z, yaw, pitch);
    }

    public PrisonListener getPrisonListener() {
        return prisonListener;
    }

    // Guard Whistle factory
    public static ItemStack createWhistle() {
        ItemStack horn = new ItemStack(Material.GOAT_HORN);
        ItemMeta meta = horn.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("§bGuard Whistle", net.kyori.adventure.text.format.NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.BOLD));
            List<net.kyori.adventure.text.Component> lore = List.of(
                net.kyori.adventure.text.Component.text("§7Right-click to alert nearby prisoners"),
                net.kyori.adventure.text.Component.text("§7Makes prisoners glow for 5 seconds.")
            );
            meta.lore(lore);
            horn.setItemMeta(meta);
        }
        return horn;
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

    // Handcuffs factory
    public static ItemStack createHandcuffs() {
        ItemStack cuffs = new ItemStack(Material.IRON_BARS);
        ItemMeta meta = cuffs.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("§fHandcuffs", net.kyori.adventure.text.format.NamedTextColor.WHITE, net.kyori.adventure.text.format.TextDecoration.BOLD));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            List<net.kyori.adventure.text.Component> lore = List.of(
                net.kyori.adventure.text.Component.text("§7Right-click a player to cuff/release.")
            );
            meta.lore(lore);
            cuffs.setItemMeta(meta);
        }
        return cuffs;
    }
}
