package com.prisoncore.shop;

import com.prisoncore.PrisonCore;
import com.prisoncore.rank.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopManager implements CommandExecutor {

    private static class PickaxeTier {
        final String name;
        final Material material;
        final double cost;
        final int efficiency;
        final double multiplier;

        PickaxeTier(String name, Material material, double cost, int efficiency, double multiplier) {
            this.name = name;
            this.material = material;
            this.cost = cost;
            this.efficiency = efficiency;
            this.multiplier = multiplier;
        }
    }

    private static final List<PickaxeTier> TIERS = Arrays.asList(
        new PickaxeTier("Wooden", Material.WOODEN_PICKAXE, 0, 1, 1.0),
        new PickaxeTier("Stone", Material.STONE_PICKAXE, 200, 3, 1.5),
        new PickaxeTier("Iron", Material.IRON_PICKAXE, 1000, 5, 2.0),
        new PickaxeTier("Gold", Material.GOLDEN_PICKAXE, 5000, 7, 3.0),
        new PickaxeTier("Diamond", Material.DIAMOND_PICKAXE, 20000, 9, 4.0),
        new PickaxeTier("Netherite", Material.NETHERITE_PICKAXE, 100000, 11, 6.0)
    );

    private static class ShopItem {
        final String name;
        final Material material;
        final double cost;
        final String displayName;
        final List<String> lore;
        final Rank requiredRank;
        final boolean unique;

        ShopItem(String name, Material material, double cost, String displayName, List<String> lore, Rank requiredRank, boolean unique) {
            this.name = name;
            this.material = material;
            this.cost = cost;
            this.displayName = displayName;
            this.lore = lore;
            this.requiredRank = requiredRank;
            this.unique = unique;
        }
    }

    private static final List<ShopItem> ITEMS = Arrays.asList(
        new ShopItem("screwdriver", Material.SHEARS, 150, "§8Screwdriver", Arrays.asList("§7Allows you to use vents", "§7Right-click an iron trapdoor"), Rank.PCI, true),
        new ShopItem("policekey", Material.TRIPWIRE_HOOK, 200000, "§6Police Key", Arrays.asList("§7A master key for security doors", "§7Opens all restricted areas"), Rank.PCI, false)
    );

    private final PrisonCore plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, Integer> playerTiers = new HashMap<>();

    public ShopManager(PrisonCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/shop.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data/shop.yml!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveConfig() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data/shop.yml!");
        }
    }

    public int getPlayerTier(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerTiers.containsKey(uuid)) {
            return playerTiers.get(uuid);
        }
        int tier = dataConfig.getInt(uuid.toString() + ".tier", 0);
        playerTiers.put(uuid, tier);
        return tier;
    }

    public double getPickaxeMultiplier(Player player) {
        int tier = getPlayerTier(player);
        return TIERS.get(tier).multiplier;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use the shop.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showShop(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("buy")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /shop buy <stone|iron|gold|diamond|netherite|screwdriver|policekey>");
                return true;
            }
            handleBuy(player, args[1]);
            return true;
        }

        showShop(player);
        return true;
    }

    private void showShop(Player player) {
        int currentTier = getPlayerTier(player);
        Rank rank = plugin.getRankManager().getRank(player);

        player.sendMessage("§6§l=== PICKAXE SHOP ===");
        player.sendMessage("§7Current: §f" + TIERS.get(currentTier).name + " §7(§ax" + String.format("%.1f", TIERS.get(currentTier).multiplier) + "§7)");
        player.sendMessage("");
        for (int i = 0; i < TIERS.size(); i++) {
            PickaxeTier tier = TIERS.get(i);
            String status;
            if (i == currentTier) status = " §a§l[OWNED]";
            else if (i < currentTier) status = " §7[OWNED]";
            else status = " §e($" + String.format("%.0f", tier.cost) + ")";
            player.sendMessage("§e" + (i + 1) + ". §f" + tier.name + " Pickaxe" + status);
        }

        player.sendMessage("");
        player.sendMessage("§6§l=== SPECIAL ITEMS ===");
        for (ShopItem item : ITEMS) {
            if (item.requiredRank != null && rank != item.requiredRank && rank != Rank.ADMIN) continue;
            player.sendMessage("§d• §f" + item.displayName + " §7- §a$" + String.format("%.0f", item.cost));
            for (String l : item.lore) player.sendMessage("  " + l);
        }

        player.sendMessage("");
        player.sendMessage("§e/shop buy <name> §7- Purchase item");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    private void handleBuy(Player player, String name) {
        Rank rank = plugin.getRankManager().getRank(player);

        // Check pickaxe tiers
        for (int i = 0; i < TIERS.size(); i++) {
            if (TIERS.get(i).name.equalsIgnoreCase(name)) {
                int currentTier = getPlayerTier(player);
                if (i <= currentTier) {
                    player.sendMessage("§cYou already own this or a better pickaxe!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
                double cost = TIERS.get(i).cost;
                if (!plugin.getEconomyManager().subtractBalance(player.getUniqueId(), cost)) {
                    player.sendMessage("§cYou need $" + String.format("%.0f", cost) + "!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
                playerTiers.put(player.getUniqueId(), i);
                dataConfig.set(player.getUniqueId().toString() + ".tier", i);
                saveConfig();
                givePickaxe(player, i);
                player.sendMessage("§aBought " + TIERS.get(i).name + " Pickaxe for $" + String.format("%.0f", cost) + "!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                return;
            }
        }

        // Check special items
        for (ShopItem item : ITEMS) {
            if (item.name.equalsIgnoreCase(name)) {
                if (item.requiredRank != null && rank != item.requiredRank && rank != Rank.ADMIN) {
                    player.sendMessage("§cYou cannot buy this item!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
                if (item.unique && hasItem(player, item)) {
                    player.sendMessage("§cYou already have this item!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
                if (!plugin.getEconomyManager().subtractBalance(player.getUniqueId(), item.cost)) {
                    player.sendMessage("§cYou need $" + String.format("%.0f", item.cost) + "!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
                giveSpecialItem(player, item);
                player.sendMessage("§aBought " + item.displayName + "§a for $" + String.format("%.0f", item.cost) + "!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                return;
            }
        }

        player.sendMessage("§cItem not found!");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    private boolean hasItem(Player player, ShopItem item) {
        for (ItemStack inv : player.getInventory().getContents()) {
            if (inv != null && inv.getType() == item.material && inv.hasItemMeta()) {
                ItemMeta meta = inv.getItemMeta();
                if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains(item.displayName.replaceAll("§.", ""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void givePickaxe(Player player, int tierIndex) {
        PickaxeTier tier = TIERS.get(tierIndex);
        ItemStack pickaxe = new ItemStack(tier.material);
        ItemMeta meta = pickaxe.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§e" + tier.name + " Pickaxe", NamedTextColor.YELLOW, TextDecoration.BOLD));
            meta.lore(List.of(
                Component.text("§7Efficiency " + tier.efficiency, NamedTextColor.GRAY),
                Component.text("§7Gold Multiplier: §ax" + String.format("%.1f", tier.multiplier), NamedTextColor.GRAY)
            ));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, tier.efficiency, true);
            meta.setUnbreakable(true);
            pickaxe.setItemMeta(meta);
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().name().endsWith("_PICKAXE")) {
                player.getInventory().remove(item);
            }
        }
        player.getInventory().addItem(pickaxe);
    }

    private void giveSpecialItem(Player player, ShopItem item) {
        ItemStack stack = new ItemStack(item.material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(item.displayName));
            meta.lore(item.lore.stream().map(l -> Component.text(l)).toList());
            meta.setUnbreakable(true);
            stack.setItemMeta(meta);
        }
        player.getInventory().addItem(stack);
    }
}
