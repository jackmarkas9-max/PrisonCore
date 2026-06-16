package com.prisoncore.mechanics;

import com.prisoncore.PrisonCore;
import com.prisoncore.rank.Rank;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
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
        plugin.getPrisonRankManager().setPlayerOnline(player);

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
        Rank rank = plugin.getRankManager().getRank(player);
        event.setFormat(rank.getLegacyPrefix() + "%1$s §8» §f%2$s");
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        // Tab completion handled by Bukkit command system now
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

        // 1. Key / Hacking Tool Logic (Open Iron Doors for 3 seconds)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clickedBlock != null && clickedBlock.getType() == Material.IRON_DOOR) {
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    String name = meta.getDisplayName();
                    if (name.contains("Key") || name.contains("Hacking Tool")) {
                        event.setCancelled(true);
                        openIronDoorTemporarily(clickedBlock);
                        return;
                    }
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
