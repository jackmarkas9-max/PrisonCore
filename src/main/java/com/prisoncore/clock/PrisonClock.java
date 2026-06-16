package com.prisoncore.clock;

import com.prisoncore.PrisonCore;
import com.prisoncore.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Sound;

public class PrisonClock extends BukkitRunnable {
    private final PrisonCore plugin;
    private final BossBar bossBar;
    private boolean wasNight = false;
    private int effectUpdateCounter = 0;

    public PrisonClock(PrisonCore plugin) {
        this.plugin = plugin;
        this.bossBar = Bukkit.createBossBar(
            "DAY - 06:00",
            BarColor.YELLOW,
            BarStyle.SOLID
        );
        this.bossBar.setVisible(true);

        // Turn off natural daylight cycle to allow our manual ticks
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }

        // Add currently online players (for reloads)
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
    }

    public void addPlayer(Player player) {
        bossBar.addPlayer(player);
    }

    public void removePlayer(Player player) {
        bossBar.removePlayer(player);
    }

    public void cleanUp() {
        bossBar.removeAll();
    }

    @Override
    public void run() {
        try {
            if (Bukkit.getWorlds().isEmpty()) return;
            World world = Bukkit.getWorlds().get(0); // Main world
            if (world == null) return;

            // Advance world time manually (+2 ticks per tick)
            long currentWorldTime = world.getTime();
            long newWorldTime = (currentWorldTime + 1) % 24000;

            // Sync to all worlds
            for (World w : Bukkit.getWorlds()) {
                w.setTime(newWorldTime);
            }

            boolean isNight = newWorldTime >= 13000; // 13000 to 24000 is night

            // Check for Transitions
            if (isNight && !wasNight) {
                handleNightTransition();
            } else if (!isNight && wasNight) {
                handleDayTransition();
            }
            wasNight = isNight;

            // Update HUD (BossBar)
            long hours = (newWorldTime / 1000 + 6) % 24;
            long minutes = (newWorldTime % 1000) * 60 / 1000;
            String timeString = String.format("%02d:%02d", hours, minutes);

            String title = isNight ? "NIGHT - " + timeString : "DAY - " + timeString;
            bossBar.setTitle(title);
            bossBar.setColor(isNight ? BarColor.PURPLE : BarColor.YELLOW);

            // Progress bar: relative to the current phase (Day/Night duration)
            double progress;
            if (isNight) {
                progress = (double) (newWorldTime - 13000) / 11000.0;
            } else {
                progress = (double) newWorldTime / 13000.0;
            }
            bossBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));

            // Handle Potion Effects every second (20 ticks)
            effectUpdateCounter++;
            if (effectUpdateCounter >= 20) {
                effectUpdateCounter = 0;
                updateNightEffects(isNight);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("PrisonClock error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleNightTransition() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text("NIGHT HAS FALLEN", net.kyori.adventure.text.format.NamedTextColor.RED),
                net.kyori.adventure.text.Component.text("Guards and Prisoners receive Darkness effect", net.kyori.adventure.text.format.NamedTextColor.GRAY)
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.0f, 0.8f);
        }
    }

    private void handleDayTransition() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text("DAWN BREAKS", net.kyori.adventure.text.format.NamedTextColor.GOLD),
                net.kyori.adventure.text.Component.text("Darkness is removed, cell rules apply", net.kyori.adventure.text.format.NamedTextColor.GRAY)
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
    }

    private void updateNightEffects(boolean isNight) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Rank rank = plugin.getRankManager().getRank(player);
            if (isNight) {
                if (rank == Rank.PRISONER || rank == Rank.GUARD) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, false));
                }
            } else {
                if (player.hasPotionEffect(PotionEffectType.DARKNESS)) {
                    player.removePotionEffect(PotionEffectType.DARKNESS);
                }
            }
        }
    }

    public boolean isNightNow() {
        if (Bukkit.getWorlds().isEmpty()) return false;
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return false;
        long time = world.getTime() % 24000;
        return time >= 13000;
    }
}
