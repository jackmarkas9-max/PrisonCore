package com.prisoncore.clock;

import com.prisoncore.PrisonCore;
import com.prisoncore.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    private int dayLength;
    private int nightLength;
    private int cycleTick = 0;
    private boolean isNight = false;

    public PrisonClock(PrisonCore plugin) {
        this.plugin = plugin;
        this.bossBar = Bukkit.createBossBar("DAY - 06:00", BarColor.YELLOW, BarStyle.SOLID);
        this.bossBar.setVisible(true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        dayLength = plugin.getConfig().getInt("day-length", 12000);
        nightLength = plugin.getConfig().getInt("night-length", 6000);

        World world = getWorld();
        if (world != null) {
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setTime(0);
        }
    }

    private World getWorld() {
        if (Bukkit.getWorlds().isEmpty()) return null;
        return Bukkit.getWorlds().get(0);
    }

    public void addPlayer(Player player) {
        bossBar.addPlayer(player);
    }

    public void removePlayer(Player player) {
        bossBar.removePlayer(player);
    }

    public void cleanUp() {
        bossBar.removeAll();
        World world = getWorld();
        if (world != null) {
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
        }
    }

    @Override
    public void run() {
        try {
            World world = getWorld();
            if (world == null) return;

            cycleTick++;

            int totalCycle = dayLength + nightLength;
            if (cycleTick > totalCycle) cycleTick = 1;

            boolean nowNight = cycleTick > dayLength;

            if (nowNight && !wasNight) {
                handleNightTransition();
                world.setTime(13000);
            } else if (!nowNight && wasNight) {
                handleDayTransition();
                world.setTime(0);
            }

            if (nowNight) {
                int nightProgress = cycleTick - dayLength;
                long time = 13000 + (nightProgress * 11000L / nightLength);
                world.setTime(time % 24000);
            } else {
                long time = cycleTick * 13000L / dayLength;
                world.setTime(time % 24000);
            }

            wasNight = nowNight;
            isNight = nowNight;

            long hours = (world.getTime() / 1000 + 6) % 24;
            long minutes = (world.getTime() % 1000) * 60 / 1000;
            String timeString = String.format("%02d:%02d", hours, minutes);

            String title = isNight ? "NIGHT - " + timeString : "DAY - " + timeString;
            bossBar.setTitle(title);
            bossBar.setColor(isNight ? BarColor.PURPLE : BarColor.YELLOW);

            double progress;
            if (isNight) {
                progress = (double) (cycleTick - dayLength) / (double) nightLength;
            } else {
                progress = (double) cycleTick / (double) dayLength;
            }
            bossBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));

            effectUpdateCounter++;
            if (effectUpdateCounter >= 20) {
                effectUpdateCounter = 0;
                updateNightEffects(isNight);
                checkEscape();
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
                net.kyori.adventure.text.Component.text("Prisoners can now mine! Darkness effect active.", net.kyori.adventure.text.format.NamedTextColor.GRAY)
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.0f, 0.8f);
        }
    }

    private void handleDayTransition() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text("DAWN BREAKS", net.kyori.adventure.text.format.NamedTextColor.GOLD),
                net.kyori.adventure.text.Component.text("Darkness removed. Cell rules apply.", net.kyori.adventure.text.format.NamedTextColor.GRAY)
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

    private void checkEscape() {
        Location pos1 = plugin.loadLocation("escape-zone.pos1");
        Location pos2 = plugin.loadLocation("escape-zone.pos2");
        if (pos1 == null || pos2 == null) return;
        if (pos1.getWorld() == null || pos2.getWorld() == null) return;
        if (!pos1.getWorld().equals(pos2.getWorld())) return;

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        for (Player player : Bukkit.getOnlinePlayers()) {
            Rank rank = plugin.getRankManager().getRank(player);
            if (rank != Rank.PRISONER) continue;
            Location loc = player.getLocation();
            if (!loc.getWorld().equals(pos1.getWorld())) continue;
            if (loc.getX() >= minX && loc.getX() <= maxX &&
                loc.getY() >= minY && loc.getY() <= maxY &&
                loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                plugin.getRankManager().setRank(player, Rank.PCI);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
                player.showTitle(net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.text("YOU ESCAPED!", net.kyori.adventure.text.format.NamedTextColor.RED),
                    net.kyori.adventure.text.Component.text("You are now PCI. You escaped prison!", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                ));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Rank r = plugin.getRankManager().getRank(p);
                    if (r == Rank.GUARD || r == Rank.ADMIN || r == Rank.PCI) {
                        p.sendMessage("§c§l[ALERT] §e" + player.getName() + " §7has escaped prison!");
                    }
                }
            }
        }
    }

    public boolean isNightNow() {
        return isNight;
    }

    public void setNight() {
        cycleTick = dayLength + 1;
        wasNight = false;
        World world = getWorld();
        if (world != null) world.setTime(13000);
    }

    public void setDay() {
        cycleTick = 1;
        wasNight = true;
        World world = getWorld();
        if (world != null) world.setTime(0);
    }
}
