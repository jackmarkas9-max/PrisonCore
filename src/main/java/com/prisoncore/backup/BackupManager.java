package com.prisoncore.backup;

import com.prisoncore.PrisonCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackupManager {
    private final PrisonCore plugin;
    private final File backupFile;
    private boolean isRestoring = false;

    public BackupManager(PrisonCore plugin) {
        this.plugin = plugin;
        this.backupFile = new File(plugin.getDataFolder(), "data/backup.txt");
    }

    public void saveArea(Player player) {
        if (isRestoring) {
            player.sendMessage("§cCannot save while a restore is in progress!");
            return;
        }

        int radius = plugin.getConfig().getInt("backup.radius", 25);
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;

        int rangeY = 30;
        int minY = Math.max(world.getMinHeight(), centerY - rangeY);
        int maxY = Math.min(world.getMaxHeight(), centerY + rangeY);

        player.sendMessage("§a[Backup] Scanning prison blocks... Please wait.");

        int finalMinY = minY;
        int finalMaxY = maxY;

        List<String> blockLines = new ArrayList<>();
        int savedCount = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = finalMinY; y <= finalMaxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        String dataStr = block.getBlockData().getAsString();
                        blockLines.add(x + ";" + y + ";" + z + ";" + dataStr);
                        savedCount++;
                    }
                }
            }
        }

        int finalSavedCount = savedCount;
        String worldName = world.getName();
        int finalMinX = minX;
        int finalMaxX = maxX;
        int finalMinZ = minZ;
        int finalMaxZ = maxZ;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!backupFile.getParentFile().exists()) {
                    backupFile.getParentFile().mkdirs();
                }
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(backupFile))) {
                    writer.write(worldName + ";" + finalMinX + ";" + finalMaxX + ";" + finalMinY + ";" + finalMaxY + ";" + finalMinZ + ";" + finalMaxZ + ";" + centerX + ";" + centerY + ";" + centerZ);
                    writer.newLine();
                    for (String line : blockLines) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§a[Backup] Prison saved successfully! (" + finalSavedCount + " non-air blocks saved).");
                });
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save block backup!");
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c[Backup] Failed to save backup. Check console."));
            }
        });
    }

    public void loadArea(Player player) {
        if (isRestoring) {
            player.sendMessage("§cBackup restore is already in progress!");
            return;
        }

        if (!backupFile.exists()) {
            player.sendMessage("§cNo backup found! Use @save first.");
            return;
        }

        isRestoring = true;
        player.sendMessage("§a[Backup] Loading snapshot from file...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(backupFile))) {
                String header = reader.readLine();
                if (header == null) {
                    isRestoring = false;
                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cBackup file is empty!"));
                    return;
                }

                String[] headerParts = header.split(";");
                String worldName = headerParts[0];
                int minX = Integer.parseInt(headerParts[1]);
                int maxX = Integer.parseInt(headerParts[2]);
                int minY = Integer.parseInt(headerParts[3]);
                int maxY = Integer.parseInt(headerParts[4]);
                int minZ = Integer.parseInt(headerParts[5]);
                int maxZ = Integer.parseInt(headerParts[6]);

                Map<String, String> blockMap = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(";");
                    if (parts.length >= 4) {
                        String key = parts[0] + "," + parts[1] + "," + parts[2];
                        blockMap.put(key, parts[3]);
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> startRestoreProcess(player, worldName, minX, maxX, minY, maxY, minZ, maxZ, blockMap));

            } catch (Exception e) {
                isRestoring = false;
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load backup file", e);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c[Backup] Error loading backup. Check console."));
            }
        });
    }

    private void startRestoreProcess(Player admin, String worldName, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Map<String, String> blockMap) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            admin.sendMessage("§cTarget world '" + worldName + "' not loaded!");
            isRestoring = false;
            return;
        }

        List<BlockTargetState> targetStates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    String key = x + "," + y + "," + z;
                    String blockDataStr = blockMap.get(key);
                    if (blockDataStr != null) {
                        targetStates.add(new BlockTargetState(x, y, z, blockDataStr));
                    } else {
                        targetStates.add(new BlockTargetState(x, y, z, "minecraft:air"));
                    }
                }
            }
        }

        int totalBlocks = targetStates.size();
        admin.sendMessage("§a[Backup] Restoring " + totalBlocks + " blocks chunk by chunk...");

        new BukkitRunnable() {
            int index = 0;
            final int blocksPerTick = 5000;

            @Override
            public void run() {
                int limit = Math.min(index + blocksPerTick, totalBlocks);
                for (int i = index; i < limit; i++) {
                    BlockTargetState state = targetStates.get(i);
                    Block block = world.getBlockAt(state.x, state.y, state.z);
                    BlockData data = Bukkit.createBlockData(state.blockData);
                    block.setBlockData(data, false);
                }

                index = limit;
                int percent = (int) (((double) index / totalBlocks) * 100);
                if (index % 25000 == 0 || index >= totalBlocks) {
                    admin.sendMessage("§a[Backup] Restoring blocks: " + percent + "% completed...");
                }

                if (index >= totalBlocks) {
                    cancel();
                    isRestoring = false;
                    admin.sendMessage("§a[Backup] Restore complete! Teleporting ranks back to spawns.");
                    teleportAllRanks();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void teleportAllRanks() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.teleportPlayerToRankSpawn(p);
        }
    }

    private static class BlockTargetState {
        final int x, y, z;
        final String blockData;

        BlockTargetState(int x, int y, int z, String blockData) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockData = blockData;
        }
    }
}
