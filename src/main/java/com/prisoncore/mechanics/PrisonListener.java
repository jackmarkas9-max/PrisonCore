package com.prisoncore.mechanics;

import com.prisoncore.PrisonCore;
import com.prisoncore.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PrisonListener implements Listener {
    private final PrisonCore plugin;
    private final Map<UUID, Long> whistleCooldowns = new HashMap<>();

    public PrisonListener(PrisonCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getClock().addPlayer(player);

        // Fetch rank, default Prisoner
        Rank rank = plugin.getRankManager().getRank(player);
        player.playerListName(net.kyori.adventure.text.Component.text(rank.getLegacyPrefix() + player.getName()));

        // Creative mode for Admins automatically
        if (rank == Rank.ADMIN) {
            player.setGameMode(org.bukkit.GameMode.CREATIVE);
        } else if (!plugin.isBuildModeActive()) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }

        // Initialize HUD Scoreboard
        plugin.getEconomyManager().updateScoreboard(player);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getClock().removePlayer(player);
        whistleCooldowns.remove(player.getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        Rank rank = plugin.getRankManager().getRank(player);

        // Intercept Admin Chat Commands starting with @
        if (message.startsWith("@")) {
            if (rank == Rank.ADMIN || rank == Rank.PCI || player.getName().equals("Markusha111")) {
                event.setCancelled(true);
                // Commands must be executed on the main server thread
                Bukkit.getScheduler().runTask(plugin, () -> executeAdminCommand(player, message));
                return;
            } else {
                player.sendMessage("§cOnly Admins can execute chat commands starting with @.");
                event.setCancelled(true);
                return;
            }
        }

        // Set prefix format for normal chat
        event.setFormat(rank.getLegacyPrefix() + "%1$s §8» §f%2$s");
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player)) return;
        Player player = (Player) event.getSender();

        Rank pRank = plugin.getRankManager().getRank(player);
        if (pRank != Rank.ADMIN && pRank != Rank.PCI && !player.getName().equals("Markusha111")) return;

        String buffer = event.getBuffer();
        if (!buffer.startsWith("@")) return;

        List<String> completions = new ArrayList<>();
        String[] parts = buffer.split(" ", -1);

        if (parts.length == 1) {
            String input = parts[0].toLowerCase();
            List<String> commands = List.of("@rank", "@give", "@day", "@night", "@setallspawn", "@setpolicespawn", "@setprisonspawn", "@setPCIspawn", "@mode", "@save", "@load", "@escape", "@wandescape", "@setvent");
            for (String cmd : commands) {
                if (cmd.startsWith(input)) {
                    completions.add(cmd);
                }
            }
        } else if (parts[0].equalsIgnoreCase("@rank")) {
            if (parts.length == 2) {
                String input = parts[1].toLowerCase();
                if ("give".startsWith(input)) completions.add("give");
            } else if (parts.length == 3 && parts[1].equalsIgnoreCase("give")) {
                String input = parts[2].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) {
                        completions.add(p.getName());
                    }
                }
            } else if (parts.length == 4 && parts[1].equalsIgnoreCase("give")) {
                String input = parts[3].toLowerCase();
                for (Rank r : Rank.values()) {
                    if (r.getName().toLowerCase().startsWith(input)) {
                        completions.add(r.getName());
                    }
                }
            }
        } else if (parts[0].equalsIgnoreCase("@give")) {
            if (parts.length == 2) {
                String input = parts[1].toLowerCase();
                if ("money".startsWith(input)) completions.add("money");
            } else if (parts.length == 3 && parts[1].equalsIgnoreCase("money")) {
                String input = parts[2].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) {
                        completions.add(p.getName());
                    }
                }
            } else if (parts.length == 4 && parts[1].equalsIgnoreCase("money")) {
                String input = parts[3].toLowerCase();
                List<String> amounts = List.of("10", "50", "100", "500", "1000");
                for (String amt : amounts) {
                    if (amt.startsWith(input)) {
                        completions.add(amt);
                    }
                }
            }
        } else if (parts[0].equalsIgnoreCase("@mode")) {
            if (parts.length == 2) {
                String input = parts[1].toLowerCase();
                if ("build".startsWith(input)) completions.add("build");
                if ("prison".startsWith(input)) completions.add("prison");
            }
        }

        if (!completions.isEmpty()) {
            event.setCompletions(completions);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Rank rank = plugin.getRankManager().getRank(player);

        if (rank == Rank.PRISONER) {
            boolean isNight = plugin.getClock().isNightNow();
            if (!isNight) {
                // Day Rule: Prisoners cannot break blocks during the Day
                event.setCancelled(true);
                player.sendActionBar(net.kyori.adventure.text.Component.text("§cPrisoners are locked! You cannot break blocks during the day."));
                return;
            }

            // Night Rule: Can break blocks EXCEPT Iron Doors
            Material type = event.getBlock().getType();
            if (type == Material.IRON_DOOR) {
                event.setCancelled(true);
                player.sendMessage("§cIron Doors are unbreakable for prisoners!");
                return;
            }

            // Trigger Alarm at Police Spawn
            Location policeSpawn = plugin.loadLocation("spawns.police");
            if (policeSpawn != null && policeSpawn.getWorld() != null) {
                policeSpawn.getWorld().playSound(policeSpawn, Sound.BLOCK_BELL_USE, 2.0f, 1.0f);
            }
            // Alert guards/admins/pci in chat
            for (Player p : Bukkit.getOnlinePlayers()) {
                Rank r = plugin.getRankManager().getRank(p);
                if (r == Rank.GUARD || r == Rank.ADMIN || r == Rank.PCI) {
                    p.sendMessage("§c§l[ALARM] §e" + player.getName() + " §7broke §6" + type.name() + " §7at §f" + event.getBlock().getX() + ", " + event.getBlock().getY() + ", " + event.getBlock().getZ());
                }
            }

            // Economy: Get money for mining Gold Ore or Raw Gold Block
            if (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE || type == Material.RAW_GOLD_BLOCK) {
                double pickMulti = plugin.getShopManager().getPickaxeMultiplier(player);
                double rankMulti = plugin.getPrisonRankManager().getGoldMultiplier(player);
                double earn = 1.0 * pickMulti * rankMulti;
                plugin.getEconomyManager().addBalance(player.getUniqueId(), earn);
                player.sendActionBar(net.kyori.adventure.text.Component.text("§a+$" + String.format("%.2f", earn) + " Mined Gold (P" + plugin.getPrisonRankManager().getPrisonRankName(player) + ")"));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.8f);
            }
        } else if (rank == Rank.PCI) {
            Material type = event.getBlock().getType();
            if (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE || type == Material.RAW_GOLD_BLOCK) {
                double pickMulti = plugin.getShopManager().getPickaxeMultiplier(player);
                double rankMulti = plugin.getPrisonRankManager().getGoldMultiplier(player);
                double earn = 2.0 * pickMulti * rankMulti;
                plugin.getEconomyManager().addBalance(player.getUniqueId(), earn);
                player.sendActionBar(net.kyori.adventure.text.Component.text("§a+$" + String.format("%.2f", earn) + " Mined Gold (PCI x" + String.format("%.1f", rankMulti) + ")"));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.8f);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Rank rank = plugin.getRankManager().getRank(player);
        ItemStack item = event.getItem();
        Block clickedBlock = event.getClickedBlock();

        // 0. Escape Wand Logic
        if (item != null && item.getType() == Material.WOODEN_AXE && item.hasItemMeta()) {
            ItemMeta wandMeta = item.getItemMeta();
            if (wandMeta != null && wandMeta.hasDisplayName() && wandMeta.getDisplayName().contains("Escape Wand")) {
                event.setCancelled(true);
                Block targetBlock = clickedBlock != null ? clickedBlock : player.getTargetBlockExact(10);
                if (targetBlock == null) {
                    player.sendMessage("§cYou must look at a block to set a corner.");
                    return;
                }
                Location loc = targetBlock.getLocation();
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                    plugin.saveLocation("escape-zone.pos1", loc);
                    player.sendMessage("§aEscape zone position 1 set to: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                } else if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
                    plugin.saveLocation("escape-zone.pos2", loc);
                    player.sendMessage("§aEscape zone position 2 set to: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                }
                return;
            }
        }

        // 1. Key Logic (Open Iron Doors for 3 seconds)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clickedBlock != null && clickedBlock.getType() == Material.IRON_DOOR) {
            if (item != null && item.getType() == Material.TRIPWIRE_HOOK) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Key")) {
                    event.setCancelled(true);
                    openIronDoorTemporarily(clickedBlock);
                    return;
                }
            }
        }

        // 2. Vent Teleport Logic (PCI with Screwdriver on Iron Trapdoor)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clickedBlock != null && clickedBlock.getType() == Material.IRON_TRAPDOOR) {
            if (item != null && item.getType() == Material.SHEARS) {
                ItemMeta screwMeta = item.getItemMeta();
                if (screwMeta != null && screwMeta.hasDisplayName() && screwMeta.getDisplayName().contains("Screwdriver")) {
                    if (rank != Rank.PCI && rank != Rank.ADMIN) {
                        player.sendMessage("§cOnly PCI can use vents!");
                        return;
                    }
                    event.setCancelled(true);
                    List<Location> vents = plugin.getVentLocations();
                    if (vents.size() < 2) {
                        player.sendMessage("§cNot enough vents configured! Need at least 2.");
                        return;
                    }
                    Location nearest = null;
                    double nearestDist = Double.MAX_VALUE;
                    for (Location vent : vents) {
                        if (!vent.getWorld().equals(player.getWorld())) continue;
                        double dist = vent.distanceSquared(player.getLocation());
                        if (dist > 0 && dist < nearestDist) {
                            nearestDist = dist;
                            nearest = vent;
                        }
                    }
                    if (nearest == null) {
                        player.sendMessage("§cNo nearby vent found!");
                        return;
                    }
                    player.teleport(nearest);
                    player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0f, 0.5f);
                    player.sendActionBar(net.kyori.adventure.text.Component.text("§8▸ Vent system activated ▸"));
                    return;
                }
            }
        }

        // 3. Redstone Interaction Protection for Prisoners (All but Admins and Guards)
        if (rank == Rank.PRISONER) {
            // Right-click button/lever
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clickedBlock != null) {
                Material type = clickedBlock.getType();
                if (isRedstoneActivator(type)) {
                    event.setCancelled(true);
                    player.sendActionBar(net.kyori.adventure.text.Component.text("§cPrisoners cannot interact with security switches!"));
                    return;
                }
            }
            // Stepping on pressure plates
            if (event.getAction() == Action.PHYSICAL && clickedBlock != null) {
                Material type = clickedBlock.getType();
                if (type.name().endsWith("_PRESSURE_PLATE")) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // 4. Guard Whistle Logic
        if (rank == Rank.GUARD) {
            if (item != null && item.getType() == Material.SHEARS) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Whistle")) {
                    event.setCancelled(true);
                    
                    // Cooldown Check (10 seconds)
                    long now = System.currentTimeMillis();
                    if (whistleCooldowns.containsKey(player.getUniqueId())) {
                        long expire = whistleCooldowns.get(player.getUniqueId());
                        if (now < expire) {
                            long secondsLeft = (expire - now + 999) / 1000;
                            player.sendActionBar(net.kyori.adventure.text.Component.text("§cWhistle is on cooldown for " + secondsLeft + "s!"));
                            return;
                        }
                    }

                    whistleCooldowns.put(player.getUniqueId(), now + 10000); // 10s cooldown
                    triggerWhistle(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            plugin.getBountyManager().claimBounty(killer, victim);
        }
    }

    private void triggerWhistle(Player guard) {
        // Sound effect
        guard.getWorld().playSound(guard.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 1.2f);

        // Visual sound wave particles (A ring of note particles)
        Location center = guard.getLocation();
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            double x = Math.cos(angle) * 1.8;
            double z = Math.sin(angle) * 1.8;
            guard.getWorld().spawnParticle(org.bukkit.Particle.NOTE, center.clone().add(x, 0.8, z), 1);
        }

        // Actionbar broadcast
        guard.sendMessage("§b*Whistle* Guards are alert! Nearby prisoners are now glowing.");

        // Glowing effect to nearby Prisoners (15 blocks radius)
        List<Entity> nearby = guard.getNearbyEntities(15, 15, 15);
        for (Entity entity : nearby) {
            if (entity instanceof Player) {
                Player target = (Player) entity;
                Rank targetRank = plugin.getRankManager().getRank(target);
                if (targetRank == Rank.PRISONER) {
                    plugin.getContrabandTask().applyWhistleGlow(target);
                    target.sendMessage("§cYou were whistled by Guard " + guard.getName() + "! You are now glowing.");
                }
            }
        }
    }

    private void executeAdminCommand(Player admin, String message) {
        String[] parts = message.split(" ");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "@rank":
                if (parts.length < 4 || !parts[1].equalsIgnoreCase("give")) {
                    admin.sendMessage("§cUsage: @rank give [player] [rank]");
                    return;
                }
                Player target = Bukkit.getPlayer(parts[2]);
                if (target == null || !target.isOnline()) {
                    admin.sendMessage("§cPlayer not found.");
                    return;
                }
                Rank rank = Rank.fromName(parts[3]);
                if (rank == null) {
                    admin.sendMessage("§cInvalid rank. Options: Admin, Guard, PCI, Prisoner");
                    return;
                }
                plugin.getRankManager().setRank(target, rank);
                admin.sendMessage("§aSet rank of " + target.getName() + " to " + rank.getName() + ".");
                break;

            case "@give":
                if (parts.length < 4 || !parts[1].equalsIgnoreCase("money")) {
                    admin.sendMessage("§cUsage: @give money [player] [amount]");
                    return;
                }
                Player giveTarget = Bukkit.getPlayer(parts[2]);
                if (giveTarget == null || !giveTarget.isOnline()) {
                    admin.sendMessage("§cPlayer not found.");
                    return;
                }
                double amount;
                try {
                    amount = Double.parseDouble(parts[3]);
                    if (amount <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    admin.sendMessage("§cInvalid amount. Must be a positive number.");
                    return;
                }
                plugin.getEconomyManager().addBalance(giveTarget.getUniqueId(), amount);
                admin.sendMessage("§aGave $" + String.format("%.2f", amount) + " to " + giveTarget.getName() + ".");
                giveTarget.sendMessage("§aYou received $" + String.format("%.2f", amount) + " from Admin " + admin.getName() + ".");
                break;

            case "@day":
                for (World w : Bukkit.getWorlds()) {
                    w.setTime(0);
                }
                admin.sendMessage("§eTime set to Day (0).");
                break;

            case "@night":
                for (World w : Bukkit.getWorlds()) {
                    w.setTime(13000);
                }
                admin.sendMessage("§eTime set to Night (13000).");
                break;

            case "@setallspawn":
                plugin.saveLocation("spawns.all", admin.getLocation());
                admin.sendMessage("§aSpawn for all ranks set to your location.");
                break;

            case "@setpolicespawn":
                plugin.saveLocation("spawns.police", admin.getLocation());
                admin.sendMessage("§aPolice spawn set to your location.");
                break;

            case "@setprisonspawn":
                plugin.saveLocation("spawns.prison", admin.getLocation());
                admin.sendMessage("§aPrisoner spawn set to your location.");
                break;

            case "@mode":
                if (parts.length < 2) {
                    admin.sendMessage("§cUsage: @mode [build/prison]");
                    return;
                }
                String mode = parts[1].toLowerCase();
                if (mode.equals("build")) {
                    plugin.setBuildModeActive(true);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.setGameMode(org.bukkit.GameMode.CREATIVE);
                        p.sendMessage("§d[Prison] Build mode enabled. Everyone is now in Creative mode.");
                    }
                } else if (mode.equals("prison")) {
                    plugin.setBuildModeActive(false);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        Rank r = plugin.getRankManager().getRank(p);
                        if (r == Rank.ADMIN) {
                            p.setGameMode(org.bukkit.GameMode.CREATIVE); // Admins remain creative
                        } else {
                            p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                        }
                        p.sendMessage("§d[Prison] Prison mode enabled. Teleporting to designated rank spawns.");
                        plugin.teleportPlayerToRankSpawn(p);
                    }
                } else {
                    admin.sendMessage("§cInvalid mode. Use: build, prison");
                }
                break;

            case "@save":
                plugin.getBackupManager().saveArea(admin);
                break;

            case "@wandescape":
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
                break;

            case "@setPCIspawn":
                plugin.saveLocation("spawns.pci", admin.getLocation());
                admin.sendMessage("§aPCI spawn set to your location.");
                break;

            case "@setvent":
                plugin.addVentLocation(admin.getLocation());
                admin.sendMessage("§aVent location added at your position.");
                admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                break;

            case "@escape":
                if (parts.length < 2) {
                    admin.sendMessage("§cUsage: @escape <player>");
                    return;
                }
                Player escapeTarget = Bukkit.getPlayer(parts[1]);
                if (escapeTarget == null || !escapeTarget.isOnline()) {
                    admin.sendMessage("§cPlayer not found.");
                    return;
                }
                plugin.getRankManager().setRank(escapeTarget, Rank.PCI);
                escapeTarget.playSound(escapeTarget.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
                escapeTarget.showTitle(net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.text("YOU ESCAPED!", net.kyori.adventure.text.format.NamedTextColor.RED),
                    net.kyori.adventure.text.Component.text("You are now PCI. You escaped prison!", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                ));
                admin.sendMessage("§a" + escapeTarget.getName() + " has been marked as escaped (PCI).");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Rank r = plugin.getRankManager().getRank(p);
                    if (r == Rank.GUARD || r == Rank.ADMIN || r == Rank.PCI) {
                        if (!p.equals(admin) && !p.equals(escapeTarget)) {
                            p.sendMessage("§c§l[ALERT] §e" + escapeTarget.getName() + " §7has escaped prison!");
                        }
                    }
                }
                break;

            default:
                admin.sendMessage("§cUnknown admin command: " + cmd);
                break;
        }
    }

    private boolean isRedstoneActivator(Material type) {
        String name = type.name();
        return name.equals("LEVER") || name.endsWith("_BUTTON");
    }

    private void openIronDoorTemporarily(Block block) {
        if (block.getType() != Material.IRON_DOOR) return;

        org.bukkit.block.data.type.Door door = (org.bukkit.block.data.type.Door) block.getBlockData();
        if (door.isOpen()) return;

        door.setOpen(true);
        block.setBlockData(door);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.0f);

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (block.getType() == Material.IRON_DOOR) {
                    org.bukkit.block.data.type.Door d = (org.bukkit.block.data.type.Door) block.getBlockData();
                    if (d.isOpen()) {
                        d.setOpen(false);
                        block.setBlockData(d);
                        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.0f);
                    }
                }
            }
        }.runTaskLater(plugin, 60L); // 3 seconds
    }
}
