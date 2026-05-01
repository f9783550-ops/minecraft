package pl.pcraft.chowany;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ChowanyPlugin extends JavaPlugin implements Listener {

    private boolean gameRunning = false;
    private Player seeker;
    private List<Player> helpers = new ArrayList<>();
    private List<Player> hiders = new ArrayList<>();
    private List<Player> spectators = new ArrayList<>();
    private BossBar bossBar;
    private int gameTime = 300;
    private HashMap<Player, Long> cooldowns = new HashMap<>();
    private HashMap<Player, Material> disguisedPlayers = new HashMap<>();
    private static final int COOLDOWN_TIME = 30;
    private static final String ADMIN_NICK = "Pcraft600";

    private static final Material[] NETHER_BLOCKS = {
            Material.NETHERRACK,
            Material.NETHER_BRICKS,
            Material.CRIMSON_STEM,
            Material.WARPED_STEM,
            Material.CRIMSON_NYLIUM,
            Material.WARPED_NYLIUM,
            Material.NETHER_WART_BLOCK,
            Material.WARPED_WART_BLOCK,
            Material.BLACKSTONE
    };

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Chowany Plugin - Arena Piekielna gotowa!");
    }

    @Override
    public void onDisable() {
        if (gameRunning) stopGame();
        getLogger().info("Chowany Plugin - Wylaczony.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("chowany")) return false;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "/chowany start - Rozpocznij gre");
            sender.sendMessage(ChatColor.GOLD + "/chowany stop - Zakoncz gre");
            sender.sendMessage(ChatColor.GOLD + "/chowany test - Testuj przemiane");
            return true;
        }

        if (args[0].equalsIgnoreCase("test")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Tylko gracz!");
                return true;
            }
            Player p = (Player) sender;
            if (!p.getName().equalsIgnoreCase(ADMIN_NICK)) {
                p.sendMessage(ChatColor.RED + "Tylko admin!");
                return true;
            }
            giveDisguiseCompass(p);
            p.sendMessage(ChatColor.GREEN + "Masz kompas testowy! Skacz by wrocic.");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (gameRunning) {
                sender.sendMessage(ChatColor.RED + "Gra juz trwa!");
                return true;
            }
            List<Player> players = new ArrayList<>(getServer().getOnlinePlayers());
            players.removeIf(p -> p.getName().equalsIgnoreCase(ADMIN_NICK));
            if (players.size() < 3) {
                sender.sendMessage(ChatColor.RED + "Minimum 3 graczy!");
                return true;
            }
            startGame(players);
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            if (!gameRunning) {
                sender.sendMessage(ChatColor.RED + "Gra nie trwa!");
                return true;
            }
            stopGame();
            sender.sendMessage(ChatColor.GREEN + "Gra zatrzymana!");
            return true;
        }
        return false;
    }

    private void startGame(List<Player> players) {
        gameRunning = true;
        helpers.clear();
        hiders.clear();
        spectators.clear();
        disguisedPlayers.clear();
        cooldowns.clear();

        Collections.shuffle(players);
        seeker = players.get(0);
        hiders.addAll(players.subList(1, players.size()));

        bossBar = BossBar.bossBar(
                Component.text("Czas gry: 5:00", NamedTextColor.GREEN),
                1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS
        );
        for (Player p : players) p.showBossBar(bossBar);
        for (Player p : players) p.getInventory().clear();

        new BukkitRunnable() {
            int count = 5;
            public void run() {
                if (count == 0) {
                    for (Player p : players) {
                        p.showTitle(Title.title(
                                Component.text("START!", NamedTextColor.GREEN),
                                Component.text("Chowajcie sie!", NamedTextColor.YELLOW)
                        ));
                    }
                    seeker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 300, 1));
                    seeker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, 100));
                    for (Player h : hiders) {
                        h.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
                        h.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 300, 1));
                        giveDisguiseCompass(h);
                    }
                    Player admin = getServer().getPlayer(ADMIN_NICK);
                    if (admin != null) {
                        admin.getInventory().clear();
                        giveDisguiseCompass(admin);
                    }
                    new BukkitRunnable() {
                        public void run() {
                            seeker.removePotionEffect(PotionEffectType.BLINDNESS);
                            seeker.removePotionEffect(PotionEffectType.SLOWNESS);
                            giveSeekerItems(seeker);
                            for (Player h : hiders) {
                                h.removePotionEffect(PotionEffectType.SPEED);
                                h.removePotionEffect(PotionEffectType.INVISIBILITY);
                            }
                        }
                    }.runTaskLater(ChowanyPlugin.this, 300);
                    startTimer(players);
                    this.cancel();
                    return;
                }
                for (Player p : players) {
                    p.showTitle(Title.title(
                            Component.text(String.valueOf(count), NamedTextColor.RED),
                            Component.empty()
                    ));
                }
                count--;
            }
        }.runTaskTimer(this, 0, 20);
    }

    BukkitRunnable timerTask;

    private void startTimer(List<Player> players) {
        timerTask = new BukkitRunnable() {
            int timeLeft = gameTime;
            public void run() {
                if (timeLeft <= 0) {
                    for (Player p : players) {
                        p.sendMessage(ChatColor.GREEN + "Koniec czasu! Chowajacy wygrywaja!");
                        p.hideBossBar(bossBar);
                    }
                    stopGame();
                    this.cancel();
                    return;
                }
                int min = timeLeft / 60;
                int sec = timeLeft % 60;
                bossBar.name(Component.text("Czas gry: " + min + ":" + String.format("%02d", sec), NamedTextColor.GREEN));
                bossBar.progress((float) timeLeft / gameTime);
                timeLeft--;
            }
        };
        timerTask.runTaskTimer(this, 320, 20);
    }

    private void stopGame() {
        gameRunning = false;
        if (timerTask != null) timerTask.cancel();
        if (bossBar != null) {
            for (Player p : getServer().getOnlinePlayers()) p.hideBossBar(bossBar);
        }
        for (Player p : getServer().getOnlinePlayers()) {
            undisguisePlayer(p);
            p.getInventory().clear();
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.SPEED);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.removePotionEffect(PotionEffectType.GLOWING);
            p.setGameMode(GameMode.SURVIVAL);
        }
        helpers.clear();
        hiders.clear();
        spectators.clear();
        disguisedPlayers.clear();
        cooldowns.clear();
        seeker = null;
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent e) {
        if (!gameRunning) return;
        if (!(e.getDamager() instanceof Player)) return;
        if (!(e.getEntity() instanceof Player)) return;
        Player damager = (Player) e.getDamager();
        Player target = (Player) e.getEntity();
        if (!damager.equals(seeker) && !helpers.contains(damager)) return;
        if (!hiders.contains(target)) return;
        e.setCancelled(true);
        findHider(target);
    }

    private void findHider(Player hider) {
        hiders.remove(hider);
        undisguisePlayer(hider);
        if (helpers.size() < 2) {
            helpers.add(hider);
            hider.sendMessage(ChatColor.GOLD + "Zostales POMOCNIKIEM!");
            hider.getInventory().clear();
        } else {
            spectators.add(hider);
            hider.setGameMode(GameMode.SPECTATOR);
        }
        for (Player p : getServer().getOnlinePlayers()) {
            p.sendMessage(ChatColor.YELLOW + hider.getName() + " znaleziony! (" + hiders.size() + " pozostalo)");
        }
        if (hiders.isEmpty()) {
            for (Player p : getServer().getOnlinePlayers()) {
                p.sendMessage(ChatColor.RED + "SZUKAJACY WYGRYWAJA!");
                p.hideBossBar(bossBar);
            }
            stopGame();
        }
    }

    private void giveDisguiseCompass(Player p) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Przemiana w blok");
        compass.setItemMeta(meta);
        p.getInventory().addItem(compass);
    }

    private void giveSeekerItems(Player p) {
        p.getInventory().clear();
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Miecz Szukajacego");
        sword.setItemMeta(meta);
        p.getInventory().addItem(sword);
        ItemStack gui = new ItemStack(Material.CHEST);
        ItemMeta guiMeta = gui.getItemMeta();
        guiMeta.setDisplayName(ChatColor.AQUA + "Wspomagacze");
        gui.setItemMeta(guiMeta);
        p.getInventory().addItem(gui);
    }

    private void openDisguiseGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("Wybierz blok", NamedTextColor.DARK_PURPLE));
        for (Material mat : NETHER_BLOCKS) {
            inv.addItem(new ItemStack(mat));
        }
        p.openInventory(inv);
    }

    private void openSeekerHelpGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("Wspomagacze", NamedTextColor.RED));
        ItemStack fire = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta fm = fire.getItemMeta();
        fm.setDisplayName(ChatColor.GOLD + "Fajerwerka");
        fire.setItemMeta(fm);
        inv.setItem(3, fire);
        ItemStack arrow = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta am = arrow.getItemMeta();
        am.setDisplayName(ChatColor.AQUA + "Strzala Widma");
        arrow.setItemMeta(am);
        inv.setItem(5, arrow);
        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().title().toString();
        if (title.contains("Wybierz blok")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            disguisePlayer(p, e.getCurrentItem().getType());
            p.closeInventory();
            return;
        }
        if (title.contains("Wspomagacze")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            if (cooldowns.containsKey(p)) {
                long left = COOLDOWN_TIME - ((System.currentTimeMillis() - cooldowns.get(p)) / 1000);
                if (left > 0) {
                    p.sendMessage(ChatColor.RED + "Poczekaj " + left + "s!");
                    p.closeInventory();
                    return;
                }
            }
            if (e.getCurrentItem().getType() == Material.FIREWORK_ROCKET) {
                if (hiders.isEmpty()) {
                    p.sendMessage(ChatColor.RED + "Brak chowajacych!");
                    p.closeInventory();
                    return;
                }
                Player target = hiders.get(new Random().nextInt(hiders.size()));
                target.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, target.getLocation().add(0, 1, 0), 50, 0.5, 1.5, 0.5, 0.1);
                p.sendMessage(ChatColor.GOLD + "Fajerwerka nad " + target.getName() + "!");
                cooldowns.put(p, System.currentTimeMillis());
                p.closeInventory();
                return;
            }
            if (e.getCurrentItem().getType() == Material.SPECTRAL_ARROW) {
                for (Player h : hiders) {
                    h.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 1));
                }
                p.sendMessage(ChatColor.AQUA + "Chowajacy podswietleni na 15s!");
                cooldowns.put(p, System.currentTimeMillis());
                p.closeInventory();
            }
        }
    }

    @EventHandler
    public void onCompassClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getItem() == null || e.getItem().getType() != Material.COMPASS) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        if (p.getName().equalsIgnoreCase(ADMIN_NICK) || (gameRunning && hiders.contains(p))) {
            openDisguiseGUI(p);
        }
    }

    @EventHandler
    public void onChestClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getItem() == null || e.getItem().getType() != Material.CHEST) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        if (gameRunning && (p.equals(seeker) || helpers.contains(p))) {
            openSeekerHelpGUI(p);
        }
    }

    private void disguisePlayer(Player p, Material mat) {
        undisguisePlayer(p);
        disguisedPlayers.put(p, mat);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        p.sendMessage(ChatColor.GREEN + "Przemieniles sie w " + mat.name() + ". Skocz by wrocic.");
    }

    private void undisguisePlayer(Player p) {
        if (disguisedPlayers.containsKey(p)) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            disguisedPlayers.remove(p);
        }
    }

    @EventHandler
    public void onPlayerJump(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!disguisedPlayers.containsKey(p)) return;
        if (!p.isOnGround() && p.getVelocity().getY() > 0) {
            undisguisePlayer(p);
            p.sendMessage(ChatColor.YELLOW + "Skoczyles! Juz nie jestes blokiem.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (gameRunning && bossBar != null) {
            e.getPlayer().showBossBar(bossBar);
        }
    }
}
