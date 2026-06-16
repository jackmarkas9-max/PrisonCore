package com.prisoncore.social;

import com.prisoncore.PrisonCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SocialManager implements CommandExecutor {

    private final PrisonCore plugin;
    private final Map<UUID, UUID> lastMsg = new HashMap<>();
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();

    public SocialManager(PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "msg": {
                if (args.length < 2) { player.sendMessage("§cUsage: /msg <player> <message>"); return true; }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) { player.sendMessage("§cPlayer not found."); return true; }
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                target.sendMessage("§d[MSG] §f" + player.getName() + "§7: §f" + message);
                player.sendMessage("§d[MSG] §f" + player.getName() + " §7-> §f" + target.getName() + "§7: §f" + message);
                lastMsg.put(target.getUniqueId(), player.getUniqueId());
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.8f);
                break;
            }

            case "reply":
            case "r": {
                if (args.length < 1) { player.sendMessage("§cUsage: /reply <message>"); return true; }
                UUID replyTo = lastMsg.get(player.getUniqueId());
                if (replyTo == null) { player.sendMessage("§cNo one to reply to."); return true; }
                Player target = Bukkit.getPlayer(replyTo);
                if (target == null) { player.sendMessage("§cThat player is offline."); return true; }
                String replyMsg = String.join(" ", java.util.Arrays.copyOfRange(args, 0, args.length));
                target.sendMessage("§d[MSG] §f" + player.getName() + "§7: §f" + replyMsg);
                player.sendMessage("§d[MSG] §f" + player.getName() + " §7-> §f" + target.getName() + "§7: §f" + replyMsg);
                lastMsg.put(target.getUniqueId(), player.getUniqueId());
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.8f);
                break;
            }

            case "tpa": {
                if (args.length < 1) { player.sendMessage("§cUsage: /tpa <player>"); return true; }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) { player.sendMessage("§cPlayer not found."); return true; }
                if (target.equals(player)) { player.sendMessage("§cYou cannot teleport to yourself!"); return true; }
                tpaRequests.put(target.getUniqueId(), player.getUniqueId());
                player.sendMessage("§aTeleport request sent to " + target.getName() + ".");
                target.sendMessage("§e" + player.getName() + " §7wants to teleport to you.");
                target.sendMessage("§a/tpaccept §7or §c/tpdeny");
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                break;
            }

            case "tpaccept": {
                UUID req = tpaRequests.get(player.getUniqueId());
                if (req == null) { player.sendMessage("§cNo pending teleport requests."); return true; }
                Player requester = Bukkit.getPlayer(req);
                if (requester == null) { player.sendMessage("§cThat player is offline."); tpaRequests.remove(player.getUniqueId()); return true; }
                requester.teleport(player.getLocation());
                requester.sendMessage("§aTeleported to " + player.getName() + ".");
                player.sendMessage("§aTeleport accepted.");
                requester.playSound(requester.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
                tpaRequests.remove(player.getUniqueId());
                break;
            }

            case "tpdeny": {
                UUID req = tpaRequests.get(player.getUniqueId());
                if (req == null) { player.sendMessage("§cNo pending teleport requests."); return true; }
                Player requester = Bukkit.getPlayer(req);
                if (requester != null) requester.sendMessage("§c" + player.getName() + " denied your teleport request.");
                player.sendMessage("§cTeleport denied.");
                tpaRequests.remove(player.getUniqueId());
                break;
            }

            default:
                player.sendMessage("§cUnknown command.");
        }
        return true;
    }
}
