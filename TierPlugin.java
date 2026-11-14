package me.tier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public class TierPlugin extends JavaPlugin implements Listener {

    record Tier(String id, String display, int points) {}

    private final List<Tier> tiers = new ArrayList<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private File dataFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "players.yml");
        loadTiers();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("tier").setExecutor(this);
    }

    @Override
    public void onDisable() { saveData(); }

    private void loadTiers() {
        tiers.clear();
        for (String s : getConfig().getStringList("tiers")) {
            String[] p = s.split(":");
            if (p.length == 3) tiers.add(new Tier(p[0], p[1], Integer.parseInt(p[2].trim())));
        }
        tiers.sort(Comparator.comparingInt(Tier::points));
    }

    private void loadData() {
        points.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        for (String k : y.getKeys(false)) points.put(UUID.fromString(k), y.getInt(k));
    }

    private void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        points.forEach((u, v) -> y.set(u.toString(), v));
        try { y.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private int tierIndex(int pts) {
        int idx = 0;
        for (int i = 0; i < tiers.size(); i++) if (pts >= tiers.get(i).points()) idx = i;
        return idx;
    }

    private void setPoints(Player p, int amount) {
        int old = points.getOrDefault(p.getUniqueId(), 0);
        int oldIdx = tierIndex(old);
        int now = Math.max(0, amount);
        points.put(p.getUniqueId(), now);
        int newIdx = tierIndex(now);
        if (newIdx > oldIdx) rankUp(p, tiers.get(oldIdx), tiers.get(newIdx));
    }

    private void rankUp(Player p, Tier from, Tier to) {
        Component title = Component.text("RANK UP", NamedTextColor.GOLD, TextDecoration.BOLD);
        Component sub = Component.text(from.display(), NamedTextColor.GRAY)
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(to.display(), NamedTextColor.YELLOW, TextDecoration.BOLD));
        p.showTitle(Title.title(title, sub, Title.Times.times(
                Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        if (getConfig().getBoolean("broadcast-rank-up", true)) {
            Bukkit.broadcast(Component.text(p.getName() + " ranked up to " + to.display() + "!", NamedTextColor.GOLD));
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        int gain = getConfig().getInt("points." + e.getEntityType().name(),
                getConfig().getInt("points.default", 1));
        if (gain <= 0) return;
        setPoints(killer, points.getOrDefault(killer.getUniqueId(), 0) + gain);
    }

    private void show(CommandSender to, Player target) {
        int pts = points.getOrDefault(target.getUniqueId(), 0);
        int idx = tierIndex(pts);
        String next = idx + 1 < tiers.size()
                ? " (" + (tiers.get(idx + 1).points() - pts) + " to " + tiers.get(idx + 1).id() + ")" : " (MAX)";
        to.sendMessage(Component.text(target.getName() + ": " + tiers.get(idx).display()
                + " [" + tiers.get(idx).id() + "] - " + pts + " pts" + next, NamedTextColor.YELLOW));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) {
            if (s instanceof Player p) show(s, p);
            return true;
        }
        if (a[0].equalsIgnoreCase("reload") && s.hasPermission("tier.admin")) {
            reloadConfig(); loadTiers();
            s.sendMessage("Reloaded.");
            return true;
        }
        if ((a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("set")) && a.length == 3
                && s.hasPermission("tier.admin")) {
            Player t = Bukkit.getPlayer(a[1]);
            if (t == null) { s.sendMessage("Player not online."); return true; }
            try {
                int n = Integer.parseInt(a[2]);
                int cur = points.getOrDefault(t.getUniqueId(), 0);
                setPoints(t, a[0].equalsIgnoreCase("give") ? cur + n : n);
                show(s, t);
            } catch (NumberFormatException ex) { s.sendMessage("Invalid number."); }
            return true;
        }
        Player t = Bukkit.getPlayer(a[0]);
        if (t != null) show(s, t); else s.sendMessage("Player not online.");
        return true;
    }
}
