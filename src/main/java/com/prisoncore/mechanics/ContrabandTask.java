package com.prisoncore.mechanics;

import com.prisoncore.PrisonCore;
import com.prisoncore.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ContrabandTask extends BukkitRunnable {
    private final PrisonCore plugin;
    private final Map<UUID, Long> whistleGlowing = new HashMap<>();

    public ContrabandTask(PrisonCore plugin) {
        this.plugin = plugin;
    }

    public void applyWhistleGlow(Player prisoner) {
        whistleGlowing.put(prisoner.getUniqueId(), System.currentTimeMillis() + 5000);
        prisoner.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, false));
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Rank rank = plugin.getRankManager().getRank(player);
            if (rank != Rank.PRISONER) {
                continue;
            }

            boolean holdingContraband = isHoldingContraband(player);
            boolean whistled = whistleGlowing.containsKey(player.getUniqueId()) && now < whistleGlowing.get(player.getUniqueId());

            if (holdingContraband || whistled) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false));
            } else {
                if (player.hasPotionEffect(PotionEffectType.GLOWING)) {
                    player.removePotionEffect(PotionEffectType.GLOWING);
                }
            }
        }
    }

    private boolean isHoldingContraband(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        return isContraband(mainHand) || isContraband(offHand);
    }

    private boolean isContraband(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material type = item.getType();
        String name = type.name();
        return name.endsWith("_SWORD")
            || name.endsWith("_AXE")
            || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE")
            || type == Material.TNT
            || type == Material.FLINT_AND_STEEL
            || type == Material.LAVA_BUCKET
            || type == Material.WATER_BUCKET
            || type == Material.BUCKET
            || type == Material.COMPASS
            || type == Material.CLOCK
            || type == Material.ENDER_PEARL
            || type == Material.ENDER_CHEST
            || type == Material.OBSIDIAN
            || type == Material.COBWEB
            || type == Material.SNOWBALL
            || type == Material.EGG
            || type == Material.BOW
            || type == Material.CROSSBOW
            || type == Material.FISHING_ROD
            || type == Material.SHEARS
            || type == Material.LEAD
            || type == Material.ANVIL
            || type == Material.GRINDSTONE
            || type == Material.CRAFTING_TABLE
            || type == Material.CHEST
            || type == Material.TRAPPED_CHEST
            || type == Material.BARREL
            || type == Material.SHIELD;
    }
}
