package com.prisoncore.shop;

import com.prisoncore.PrisonCore;
import com.prisoncore.rank.Rank;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopManager implements CommandExecutor, Listener {

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
        new ShopItem("screwdriver", Material.SHEARS, 150000, "§8Screwdriver", Arrays.asList("§7Allows you to use vents", "§7Right-click an iron trapdoor"), Rank.PCI, true),
        new ShopItem("policekey", Material.TRIPWIRE_HOOK, 200000, "§6Police Key", Arrays.asList("§7A master key for security doors", "§7Opens all restricted areas"), Rank.PCI, false)
    );

    private static final String SHOP_TITLE = "§8§lPrison Shop";

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
            try { dataFile.createNewFile(); } catch (IOException e) { plugin.getLogger().severe("Could not create data/shop.yml!"); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveConfig() {
        try { dataConfig.save(dataFile); } catch (IOException e) { plugin.getLogger().severe("Could not save data/shop.yml!"); }
    }

    public int getPlayerTier(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerTiers.containsKey(uuid)) return playerTiers.get(uuid);
        int tier = dataConfig.getInt(uuid.toString() + ".tier", 0);
        playerTiers.put(uuid, tier);
        return tier;
    }

    public double getPickaxeMultiplier(Player player) {
        int tier = getPlayerTier(player);
        return TIERS.get(tier).multiplier;
    }

    private Inventory createShopInventory(Player player) {
        int currentTier = getPlayerTier(player);
        Rank rank = plugin.getRankManager().getRank(player);

        Inventory inv = Bukkit.createInventory(null, 27, Component.text(SHOP_TITLE));

        // Row 1: Pickaxes (slots 0-8)
        for (int i = 0; i < TIERS.size(); i++) {
            PickaxeTier tier = TIERS.get(i);
            ItemStack display = new ItemStack(tier.material);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                boolean owned = i <= currentTier;
                boolean canAfford = plugin.getEconomyManager().getBalance(player) >= tier.cost;

                if (owned) {
                    meta.displayName(Component.text("§a" + tier.name + " Pickaxe §7[OWNED]"));
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                } else if (canAfford) {
                    meta.displayName(Component.text("§e" + tier.name + " Pickaxe"));
                } else {
                    meta.displayName(Component.text("§c" + tier.name + " Pickaxe"));
                }

                if (i > 0) {
                    meta.lore(List.of(
                        Component.text("§7Cost: §a$" + String.format("%.0f", tier.cost)),
                        Component.text("§7Efficiency " + tier.efficiency + " | §7Mult: §ax" + String.format("%.1f", tier.multiplier)),
                        owned ? Component.text("§a✓ Owned") : (canAfford ? Component.text("§eClick to buy!") : Component.text("§cCan't afford"))
                    ));
                } else {
                    meta.lore(List.of(
                        Component.text("§7Free starter pickaxe"),
                        Component.text("§7Efficiency " + tier.efficiency + " | §7Mult: §ax" + String.format("%.1f", tier.multiplier)),
                        Component.text("§a✓ Owned")
                    ));
                }
                display.setItemMeta(meta);
            }
            inv.setItem(i, display);
        }

        // Row 2: Special items (slot 9-17)
        int slot = 9;
        for (ShopItem item : ITEMS) {
            if (item.requiredRank != null && rank != item.requiredRank && rank != Rank.ADMIN) continue;

            ItemStack display = new ItemStack(item.material);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                boolean alreadyHas = item.unique && hasItem(player, item);
                boolean canAfford = plugin.getEconomyManager().getBalance(player) >= item.cost;

                if (alreadyHas) {
                    meta.displayName(Component.text("§a" + item.displayName + " §7[OWNED]"));
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                } else if (canAfford) {
                    meta.displayName(Component.text("§e" + item.displayName));
                } else {
                    meta.displayName(Component.text("§c" + item.displayName));
                }

                List<Component> lore = new java.util.ArrayList<>();
                for (String l : item.lore) lore.add(Component.text(l));
                lore.add(Component.text("§7Cost: §a$" + String.format("%.0f", item.cost)));
                if (alreadyHas) lore.add(Component.text("§a✓ Already owned"));
                else if (canAfford) lore.add(Component.text("§eClick to buy!"));
                else lore.add(Component.text("§cCan't afford"));
                meta.lore(lore);

                display.setItemMeta(meta);
            }
            inv.setItem(slot, display);
            slot++;
        }

        // Fill empty slots with glass panes
        ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillMeta = fill.getItemMeta();
        if (fillMeta != null) {
            fillMeta.displayName(Component.text(""));
            fill.setItemMeta(fillMeta);
        }
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, fill);
        }

        return inv;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().title() == null) return;
        if (!event.getView().title().equals(Component.text(SHOP_TITLE))) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Check pickaxe slots (0-5)
        if (slot >= 0 && slot < TIERS.size()) {
            int currentTier = getPlayerTier(player);
            if (slot <= currentTier) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }
            handleBuyPickaxe(player, slot);
            return;
        }

        // Check special item slots (9-...)
        Rank rank = plugin.getRankManager().getRank(player);
        int itemSlot = 9;
        for (ShopItem item : ITEMS) {
            if (item.requiredRank != null && rank != item.requiredRank && rank != Rank.ADMIN) continue;
            if (slot == itemSlot) {
                handleBuyItem(player, item);
                return;
            }
            itemSlot++;
        }
    }

    private void handleBuyPickaxe(Player player, int targetTier) {
        int currentTier = getPlayerTier(player);
        if (targetTier <= currentTier) {
            player.sendMessage("§cYou already own this pickaxe!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            reopenShop(player);
            return;
        }

        double cost = TIERS.get(targetTier).cost;
        if (!plugin.getEconomyManager().subtractBalance(player.getUniqueId(), cost)) {
            player.sendMessage("§cYou need $" + String.format("%.0f", cost) + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            reopenShop(player);
            return;
        }

        playerTiers.put(player.getUniqueId(), targetTier);
        dataConfig.set(player.getUniqueId().toString() + ".tier", targetTier);
        saveConfig();
        givePickaxe(player, targetTier);

        player.sendMessage("§aBought " + TIERS.get(targetTier).name + " Pickaxe for $" + String.format("%.0f", cost) + "!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        reopenShop(player);
    }

    private void handleBuyItem(Player player, ShopItem item) {
        Rank rank = plugin.getRankManager().getRank(player);

        if (item.requiredRank != null && rank != item.requiredRank && rank != Rank.ADMIN) {
            player.sendMessage("§cYou cannot buy this item!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            reopenShop(player);
            return;
        }

        if (item.unique && hasItem(player, item)) {
            player.sendMessage("§cYou already have this item!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            reopenShop(player);
            return;
        }

        if (!plugin.getEconomyManager().subtractBalance(player.getUniqueId(), item.cost)) {
            player.sendMessage("§cYou need $" + String.format("%.0f", item.cost) + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            reopenShop(player);
            return;
        }

        giveSpecialItem(player, item);
        player.sendMessage("§aBought " + item.displayName + "§a for $" + String.format("%.0f", item.cost) + "!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        reopenShop(player);
    }

    private void reopenShop(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.openInventory(createShopInventory(player));
        }, 1L);
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use the shop.");
            return true;
        }
        Player player = (Player) sender;
        player.openInventory(createShopInventory(player));
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
        return true;
    }

    private void givePickaxe(Player player, int tierIndex) {
        PickaxeTier tier = TIERS.get(tierIndex);
        ItemStack pickaxe = new ItemStack(tier.material);
        ItemMeta meta = pickaxe.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§e" + tier.name + " Pickaxe"));
            meta.lore(List.of(
                Component.text("§7Efficiency " + tier.efficiency),
                Component.text("§7Gold Multiplier: §ax" + String.format("%.1f", tier.multiplier))
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
            // CustomModelData for texture pack support
            if (item.name.equals("screwdriver")) meta.setCustomModelData(1004);
            if (item.name.equals("policekey")) meta.setCustomModelData(1002);
            stack.setItemMeta(meta);
        }
        player.getInventory().addItem(stack);
    }
}
