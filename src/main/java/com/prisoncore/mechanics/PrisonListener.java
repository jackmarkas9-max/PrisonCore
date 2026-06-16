package com.prisoncore.mechanics;

import com.prisoncore.PrisonCore;
import com.prisoncore.rank.Rank;
import org.bukkit.Bukkit;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PrisonListener implements Listener {
    private final PrisonCore plugin;
    private final Map<UUID, Long> whistleCooldowns = new HashMap<>();

    // Handcuff state: player UUID -> unlock time (millis)
    private final Map<UUID, Long> cuffedPlayers = new HashMap<>();
    // Post-release stun lock (3 second forced hold)
    private final Set<UUID> postCuffLock = new HashSet<>();

    // Drag state: guard UUID -> prisoner UUID (prisoner follows guard)
    private final Map<UUID, UUID> draggedPlayers = new HashMap<>();

    // CheckGuy viewers: admin UUID -> target player UUID
    private final Map<UUID, UUID> checkGuyViewers = new HashMap<>();

    public PrisonListener(PrisonCore plugin) {
        this.plugin = plugin;
        startDragTask();
    }

    private void startDragTask() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, UUID> entry : draggedPlayers.entrySet()) {
                    Player guard = Bukkit.getPlayer(entry.getKey());
                    Player prisoner = Bukkit.getPlayer(entry.getValue());
                    if (guard == null || !guard.isOnline() || prisoner == null || !prisoner.isOnline()) {
                        draggedPlayers.remove(entry.getKey());
                        continue;
                    }
                    // Teleport prisoner 2 blocks behind the guard's facing direction
                    org.bukkit.util.Vector dir = guard.getLocation().getDirection().normalize();
                    Location behind = guard.getLocation().subtract(dir.multiply(2));
                    behind.setYaw(guard.getLocation().getYaw());
                    behind.setPitch(0);
                    prisoner.teleport(behind);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
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
        UUID playerId = player.getUniqueId();
        plugin.getClock().removePlayer(player);
        whistleCooldowns.remove(playerId);
        postCuffLock.remove(playerId);

        // If a guard quits while dragging someone, release the prisoner
        if (draggedPlayers.containsKey(playerId)) {
            UUID prisonerId = draggedPlayers.get(playerId);
            cuffedPlayers.remove(prisonerId);
            Player prisoner = Bukkit.getPlayer(prisonerId);
            if (prisoner != null && prisoner.isOnline()) {
                prisoner.removePotionEffect(PotionEffectType.SLOWNESS);
                prisoner.sendMessage("§aThe guard disconnected — you are free.");
            }
        }
        cuffedPlayers.remove(playerId);
        draggedPlayers.remove(playerId);
        draggedPlayers.values().remove(playerId);
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

        // Only Admin can break blocks
        if (rank != Rank.ADMIN) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§cOnly Admins can break blocks!"));
            return;
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        String name = meta.getDisplayName();
        if (item.getType() == Material.GREEN_CONCRETE && name.contains("Hacking Tool")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot place the Hacking Tool on the ground!");
        } else if (item.getType() == Material.IRON_BARS && name.contains("Handcuffs")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot place Handcuffs on the ground!");
        } else if (item.getType() == Material.TRIPWIRE_HOOK && name.contains("Key")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot place the Key on the ground!");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Rank rank = plugin.getRankManager().getRank(player);

        // If cuffed or post-cuff locked, prevent movement (unless being dragged)
        if (cuffedPlayers.containsKey(player.getUniqueId()) || postCuffLock.contains(player.getUniqueId())) {
            // Don't cancel if this player is being dragged by a guard
            if (!draggedPlayers.containsValue(player.getUniqueId())) {
                Location from = event.getFrom();
                Location to = event.getTo();
                if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // Cell zone check for PCI
        if (rank == Rank.PCI) {
            if (plugin.isInCellZone(player.getLocation())) {
                player.getInventory().clear();
                plugin.getRankManager().setRank(player, Rank.PRISONER);
                player.sendTitle("§c§lARRESTED!", "§7You entered a cell zone and lost everything!", 10, 70, 20);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof Player target)) return;

        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null || item.getType() != Material.IRON_BARS || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.getDisplayName().contains("Handcuffs")) return;

        event.setCancelled(true);
        Rank playerRank = plugin.getRankManager().getRank(player);
        if (playerRank != Rank.GUARD && playerRank != Rank.ADMIN) {
            player.sendMessage("§cOnly Guards can use handcuffs!");
            return;
        }

        Rank targetRank = plugin.getRankManager().getRank(target);
        if (targetRank != Rank.PRISONER && targetRank != Rank.PCI) {
            player.sendMessage("§cYou can only cuff prisoners or PCI!");
            return;
        }

        UUID targetId = target.getUniqueId();
        UUID guardId = player.getUniqueId();

        // State 1: target is being dragged by this guard → release
        if (draggedPlayers.containsKey(guardId) && draggedPlayers.get(guardId).equals(targetId)) {
            draggedPlayers.remove(guardId);
            releaseCuffs(target, player);
            return;
        }

        // State 2: target is cuffed (by anyone) but not being dragged → start dragging
        if (cuffedPlayers.containsKey(targetId)) {
            // Check if someone else is already dragging them
            if (draggedPlayers.containsValue(targetId)) {
                player.sendMessage("§c" + target.getName() + " is already being dragged by another guard.");
                return;
            }
            draggedPlayers.put(guardId, targetId);
            player.sendMessage("§aYou are now dragging " + target.getName() + ".");
            target.sendMessage("§cYou are being dragged by " + player.getName() + "!");
            for (Player p : Bukkit.getOnlinePlayers()) {
                Rank r = plugin.getRankManager().getRank(p);
                if (r == Rank.GUARD || r == Rank.ADMIN) {
                    p.sendMessage("§b[GUARD] " + player.getName() + " is dragging " + target.getName() + ".");
                }
            }
            return;
        }

        // State 3: not cuffed → apply cuffs
        cuffedPlayers.put(targetId, System.currentTimeMillis() + 5000);
        target.sendTitle("§c§lYOU ARE CUFFED!", "§7Right-click again to start dragging.", 10, 70, 20);
        player.sendMessage("§aYou cuffed " + target.getName() + ". Right-click again to drag them.");
        target.sendMessage("§cYou have been cuffed by " + player.getName() + "!");
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, org.bukkit.potion.PotionEffect.INFINITE_DURATION, 250, false, false));

        for (Player p : Bukkit.getOnlinePlayers()) {
            Rank r = plugin.getRankManager().getRank(p);
            if (r == Rank.GUARD || r == Rank.ADMIN) {
                p.sendMessage("§b[GUARD] " + player.getName() + " cuffed " + target.getName() + ".");
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Rank rank = plugin.getRankManager().getRank(player);
        ItemStack item = event.getItem();
        Block clickedBlock = event.getClickedBlock();

        // 0. Escape Wand / Cell Wand Logic
        if (item != null && item.getType() == Material.WOODEN_AXE && item.hasItemMeta()) {
            ItemMeta wandMeta = item.getItemMeta();
            if (wandMeta != null && wandMeta.hasDisplayName()) {
                Block targetBlock = clickedBlock != null ? clickedBlock : player.getTargetBlockExact(10);
                if (targetBlock == null) {
                    player.sendMessage("§cYou must look at a block to set a corner.");
                    return;
                }
                Location loc = targetBlock.getLocation();

                if (wandMeta.getDisplayName().contains("Escape Wand")) {
                    event.setCancelled(true);
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
                } else if (wandMeta.getDisplayName().contains("Cell Wand")) {
                    event.setCancelled(true);
                    Location pos1 = plugin.getCellZonePos1();
                    Location pos2 = plugin.getCellZonePos2();
                    if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                        plugin.saveCellZone(loc, pos2 != null ? pos2 : loc);
                        player.sendMessage("§aCell zone position 1 set to: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                    } else if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
                        plugin.saveCellZone(pos1 != null ? pos1 : loc, loc);
                        player.sendMessage("§aCell zone position 2 set to: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                    }
                    return;
                }
                // Spawn zone wands
                String[] spawnWandNames = {"All Spawn", "Police Spawn", "Prison Spawn", "PCI Spawn"};
                String[] spawnConfigKeys = {"spawns.all", "spawns.police", "spawns.prison", "spawns.pci"};
                String matchedKey = null;
                for (int i = 0; i < spawnWandNames.length; i++) {
                    if (wandMeta.getDisplayName().contains(spawnWandNames[i])) {
                        matchedKey = spawnConfigKeys[i];
                        break;
                    }
                }
                if (matchedKey != null) {
                    event.setCancelled(true);
                    Location sPos1 = plugin.getSpawnZonePos1(matchedKey);
                    Location sPos2 = plugin.getSpawnZonePos2(matchedKey);
                    if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                        plugin.saveSpawnZone(matchedKey, loc, sPos2 != null ? sPos2 : loc);
                        player.sendMessage("§a" + matchedKey + " zone position 1 set.");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                    } else if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
                        plugin.saveSpawnZone(matchedKey, sPos1 != null ? sPos1 : loc, loc);
                        player.sendMessage("§a" + matchedKey + " zone position 2 set.");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                    }
                    return;
                }
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

        // 2. Vent Teleport Logic (PCI with Screwdriver on Iron Trapdoor -> GUI)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clickedBlock != null && clickedBlock.getType() == Material.IRON_TRAPDOOR) {
            if (item != null && item.getType() == Material.SHEARS) {
                ItemMeta screwMeta = item.getItemMeta();
                if (screwMeta != null && screwMeta.hasDisplayName() && screwMeta.getDisplayName().contains("Screwdriver")) {
                    if (rank != Rank.PCI && rank != Rank.ADMIN) {
                        player.sendMessage("§cOnly PCI can use vents!");
                        return;
                    }
                    event.setCancelled(true);
                    Map<Integer, Location> vents = plugin.getVentLocations();
                    if (vents.isEmpty()) {
                        player.sendMessage("§cNo vents configured!");
                        return;
                    }
                    openVentGUI(player, vents);
                    return;
                }
            }
        }

        // 3. Redstone/switch interaction — only Admin can use buttons, levers, pressure plates
        if (rank != Rank.ADMIN) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clickedBlock != null) {
                Material type = clickedBlock.getType();
                if (isRedstoneActivator(type)) {
                    event.setCancelled(true);
                    player.sendActionBar(net.kyori.adventure.text.Component.text("§cOnly Admins can interact with switches!"));
                    return;
                }
            }
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
            if (item != null && item.getType() == Material.GOAT_HORN) {
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

    // Vent GUI: Shows all vent locations, click to teleport
    private void openVentGUI(Player player, Map<Integer, Location> vents) {
        int size = ((vents.size() / 9) + 1) * 9;
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        Inventory gui = Bukkit.createInventory(null, size, net.kyori.adventure.text.Component.text("§8Vent System"));

        for (Map.Entry<Integer, Location> entry : vents.entrySet()) {
            int id = entry.getKey();
            Location loc = entry.getValue();
            ItemStack ventItem = new ItemStack(Material.IRON_TRAPDOOR);
            ItemMeta meta = ventItem.getItemMeta();
            if (meta != null) {
                meta.displayName(net.kyori.adventure.text.Component.text("§8Vent #" + id));
                meta.lore(List.of(
                    net.kyori.adventure.text.Component.text("§7World: " + (loc.getWorld() != null ? loc.getWorld().getName() : "?")),
                    net.kyori.adventure.text.Component.text("§7X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ()),
                    net.kyori.adventure.text.Component.text(""),
                    net.kyori.adventure.text.Component.text("§eClick to teleport!")
                ));
                ventItem.setItemMeta(meta);
            }
            gui.setItem(gui.firstEmpty(), ventItem);
        }

        player.openInventory(gui);
    }

    // CheckGuy inventory inspection
    public void openCheckGuyInventory(Player admin, Player target) {
        Inventory inv = Bukkit.createInventory(null, 45, net.kyori.adventure.text.Component.text("§8Checking: " + target.getName()));

        // Copy target's inventory contents
        for (int i = 0; i < 36; i++) {
            ItemStack item = target.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                inv.setItem(i, item.clone());
            }
        }
        // Armor
        for (int i = 0; i < 4; i++) {
            ItemStack armor = target.getInventory().getArmorContents()[i];
            if (armor != null && armor.getType() != Material.AIR) {
                inv.setItem(36 + i, armor.clone());
            }
        }
        // Offhand
        ItemStack offhand = target.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            inv.setItem(40, offhand.clone());
        }

        checkGuyViewers.put(admin.getUniqueId(), target.getUniqueId());
        admin.openInventory(inv);
        admin.sendMessage("§aClick any item in " + target.getName() + "'s inventory to confiscate it.");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        String title = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(event.getView().title());

        // Vent System GUI - click to teleport
        if (title.contains("Vent System")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;
            String displayName = meta.getDisplayName().replaceAll("§.", "");
            if (!displayName.contains("Vent #")) return;
            try {
                int id = Integer.parseInt(displayName.replaceAll("[^0-9]", ""));
                Location loc = plugin.getVentLocation(id);
                if (loc != null) {
                    admin.closeInventory();
                    admin.teleport(loc);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0f, 0.5f);
                    admin.sendActionBar(net.kyori.adventure.text.Component.text("§8▸ Teleported to Vent #" + id + " ▸"));
                } else {
                    admin.sendMessage("§cVent #" + id + " location not found.");
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        // CheckGuy system
        UUID targetId = checkGuyViewers.get(admin.getUniqueId());
        if (targetId == null) return;

        event.setCancelled(true);

        if (!title.contains("Checking:")) {
            checkGuyViewers.remove(admin.getUniqueId());
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            admin.sendMessage("§cTarget is no longer online.");
            admin.closeInventory();
            checkGuyViewers.remove(admin.getUniqueId());
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 45) return;

        if (slot < 36) {
            ItemStack targetItem = target.getInventory().getItem(slot);
            if (targetItem != null && targetItem.getType() != Material.AIR) {
                target.getInventory().setItem(slot, null);
                admin.sendMessage("§aConfiscated §6" + targetItem.getType().name() + " §afrom " + target.getName() + ".");
                target.sendMessage("§cAn item was confiscated from your inventory by " + admin.getName() + ".");
                event.getInventory().setItem(slot, null);
            }
        } else if (slot >= 36 && slot <= 39) {
            int armorSlot = slot - 36;
            ItemStack[] armor = target.getInventory().getArmorContents();
            ItemStack armorItem = armor[armorSlot];
            if (armorItem != null && armorItem.getType() != Material.AIR) {
                armor[armorSlot] = null;
                target.getInventory().setArmorContents(armor);
                admin.sendMessage("§aConfiscated §6" + armorItem.getType().name() + " §afrom " + target.getName() + ".");
                target.sendMessage("§cAn armor item was confiscated by " + admin.getName() + ".");
                event.getInventory().setItem(slot, null);
            }
        } else if (slot == 40) {
            ItemStack offhandItem = target.getInventory().getItemInOffHand();
            if (offhandItem != null && offhandItem.getType() != Material.AIR) {
                target.getInventory().setItemInOffHand(null);
                admin.sendMessage("§aConfiscated §6" + offhandItem.getType().name() + " §afrom " + target.getName() + ".");
                target.sendMessage("§cAn item was confiscated by " + admin.getName() + ".");
                event.getInventory().setItem(slot, null);
            }
        }

        plugin.getEconomyManager().updateScoreboard(target);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(event.getView().title());
        if (title.contains("Checking:") || title.contains("Vent System")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            plugin.getBountyManager().claimBounty(killer, victim);
        }
        cuffedPlayers.remove(victim.getUniqueId());
        postCuffLock.remove(victim.getUniqueId());
        draggedPlayers.values().remove(victim.getUniqueId());
        draggedPlayers.remove(victim.getUniqueId());
    }

    private void releaseCuffs(Player target, Player guard) {
        UUID targetId = target.getUniqueId();
        UUID guardId = guard.getUniqueId();
        cuffedPlayers.remove(targetId);
        // Clean up drag state if present
        if (draggedPlayers.containsKey(guardId) && draggedPlayers.get(guardId).equals(targetId)) {
            draggedPlayers.remove(guardId);
        }

        guard.sendMessage("§aYou released " + target.getName() + ".");
        target.sendMessage("§aYou have been released.");

        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.sendTitle("§a§lRELEASED!", "§7Stay still for 3 more seconds.", 10, 60, 20);

        // 3-second post-cuff lock
        postCuffLock.add(targetId);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                postCuffLock.remove(targetId);
                if (target.isOnline()) {
                    target.sendMessage("§aYou are free to move now.");
                }
            }
        }.runTaskLater(plugin, 60L);
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
