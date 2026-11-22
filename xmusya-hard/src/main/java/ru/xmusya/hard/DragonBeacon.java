package ru.xmusya.hard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * «Драконий маяк»: яйцо дракона, поставленное прямо на блок маяка.
 *
 * <para>Уровень берётся из ванильной пирамиды (3x3 … 9x9) под маяком,
 * радиус по горизонтали = 24 * уровень, по вертикали — вся высота мира.
 * Работает без доступа к небу. Эффекты выбираются в собственном GUI.</para>
 */
public final class DragonBeacon implements Listener {

    // ------------------------------------------------------------------ эффекты
    private static final class Fx {
        final String id;
        final String name;
        final Material icon;
        final int minLevel;
        final boolean exclusive;
        final PotionEffectType potion;

        Fx(String id, String name, Material icon, int minLevel, boolean exclusive,
           PotionEffectType potion) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.minLevel = minLevel;
            this.exclusive = exclusive;
            this.potion = potion;
        }
    }

    private static final List<Fx> EFFECTS = List.of(
            new Fx("speed", "Скорость", Material.SUGAR, 1, false, PotionEffectType.SPEED),
            new Fx("haste", "Спешка", Material.GOLDEN_PICKAXE, 1, false, PotionEffectType.HASTE),
            new Fx("resistance", "Сопротивление", Material.IRON_CHESTPLATE, 2, false,
                    PotionEffectType.RESISTANCE),
            new Fx("jump_boost", "Прыгучесть", Material.RABBIT_FOOT, 2, false,
                    PotionEffectType.JUMP_BOOST),
            new Fx("strength", "Сила", Material.BLAZE_POWDER, 3, false, PotionEffectType.STRENGTH),
            new Fx("regeneration", "Регенерация", Material.GOLDEN_APPLE, 4, false,
                    PotionEffectType.REGENERATION),
            new Fx("slow_falling", "Плавное падение", Material.PHANTOM_MEMBRANE, 1, false,
                    PotionEffectType.SLOW_FALLING),
            new Fx("dolphins_grace", "Дельфинья грация", Material.HEART_OF_THE_SEA, 1, false,
                    PotionEffectType.DOLPHINS_GRACE),
            new Fx("luck", "Удача", Material.RABBIT_FOOT, 1, false, PotionEffectType.LUCK),
            new Fx("saturation", "Насыщение", Material.CAKE, 1, true, PotionEffectType.SATURATION),
            new Fx("immunity", "Неуязвимость", Material.MAGMA_CREAM, 1, true, null)
    );

    private static Fx fx(String id) {
        for (Fx f : EFFECTS) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ состояние
    private static final class Beacon {
        String world;
        int x, y, z;
        String primary = "";
        String secondary = "";
        boolean primaryII = false;
        String tier = "normal"; // запас на будущее

        String key() {
            return world + ":" + x + ":" + y + ":" + z;
        }

        String eggKey() {
            return world + ":" + x + ":" + (y + 1) + ":" + z;
        }
    }

    private static final class Holder implements InventoryHolder {
        final Beacon beacon;
        Inventory inventory;

        Holder(Beacon beacon) {
            this.beacon = beacon;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Слоты под эффекты: 3 + 5 + 3 (всего 11 эффектов). */
    private static final int[] SLOTS = {10, 11, 12, 19, 20, 21, 22, 23, 28, 29, 30};

    private final XmusyaHard plugin;
    private final Map<String, Beacon> beacons = new LinkedHashMap<>();
    /** Игроки, которым сейчас даёт «Неуязвимость» хотя бы один маяк. */
    private final java.util.Set<java.util.UUID> immunityNow = new java.util.HashSet<>();
    private File file;

    public DragonBeacon(XmusyaHard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dragon-beacons.yml");
        load();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------------ файл
    private void load() {
        beacons.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            Beacon b = new Beacon();
            b.world = y.getString(key + ".world", "");
            b.x = y.getInt(key + ".x");
            b.y = y.getInt(key + ".y");
            b.z = y.getInt(key + ".z");
            b.primary = y.getString(key + ".primary", "");
            b.secondary = y.getString(key + ".secondary", "");
            b.primaryII = y.getBoolean(key + ".primary-ii", false);
            if (fx(b.primary) == null) {
                b.primary = "";
                b.primaryII = false;
            }
            if (fx(b.secondary) == null || b.secondary.equals(b.primary)) {
                b.secondary = "";
            }
            if (b.world.isEmpty()) {
                continue;
            }
            beacons.put(b.key(), b);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Beacon b : beacons.values()) {
            String k = safe(b.key());
            y.set(k + ".world", b.world);
            y.set(k + ".x", b.x);
            y.set(k + ".y", b.y);
            y.set(k + ".z", b.z);
            y.set(k + ".primary", b.primary);
            y.set(k + ".secondary", b.secondary);
            y.set(k + ".primary-ii", b.primaryII);
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("dragon-beacons.yml: " + ex.getMessage());
        }
    }

    private static String safe(String s) {
        return s.replace(':', '_').replace('.', '_');
    }

    // ------------------------------------------------------------------ события
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.DRAGON_EGG) {
            return;
        }
        Block egg = e.getBlockPlaced();
        Block below = egg.getRelative(0, -1, 0);
        if (below.getType() != Material.BEACON) {
            return;
        }
        Beacon b = new Beacon();
        b.world = below.getWorld().getName();
        b.x = below.getX();
        b.y = below.getY();
        b.z = below.getZ();
        beacons.put(b.key(), b);
        save();
        if (plugin.achievements != null) {
            plugin.achievements.onBeacon(e.getPlayer(), pyramidLevel(below), false, "");
        }
        Msg.chat(e.getPlayer(), Messages.raw("beacon.created",
                "Драконий маяк создан! ПКМ по яйцу — настройка эффектов."));
        e.getPlayer().playSound(e.getPlayer().getLocation(), "block.beacon.activate", 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }
        // Обычный маяк под яйцом — не открываем ванильный GUI
        if (b.getType() == Material.BEACON) {
            Block above = b.getRelative(0, 1, 0);
            if (above.getType() == Material.DRAGON_EGG) {
                if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
                    return; // ЛКМ — можно ломать маяк
                }
                e.setCancelled(true);
                // Заодно регистрируем, если структуру собрали не через BlockPlace
                Beacon st = beaconAt(b);
                if (st == null) {
                    st = new Beacon();
                    st.world = b.getWorld().getName();
                    st.x = b.getX();
                    st.y = b.getY();
                    st.z = b.getZ();
                    beacons.put(st.key(), st);
                    save();
                }
                if (!e.getPlayer().isSneaking()) {
                    open(e.getPlayer(), st);
                }
                return;
            }
        }
        // ЛКМ по яйцу на маяке — сломать и выбить яйцо (без ванильного телепорта)
        if (b.getType() == Material.DRAGON_EGG && e.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block below = b.getRelative(0, -1, 0);
            if (below.getType() == Material.BEACON) {
                e.setCancelled(true);
                breakEgg(e.getPlayer(), b);
            }
            return;
        }
        if (plugin.achievements != null) {
            plugin.achievements.onSecretEgg(e.getPlayer());
        }
        // ПКМ по яйцу — свой GUI, и никакой телепортации яйца
        if (b.getType() == Material.DRAGON_EGG && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block below = b.getRelative(0, -1, 0);
            if (below.getType() == Material.BEACON) {
                e.setCancelled(true);
                Beacon st = beaconAt(below);
                if (st == null) {
                    st = new Beacon();
                    st.world = below.getWorld().getName();
                    st.x = below.getX();
                    st.y = below.getY();
                    st.z = below.getZ();
                    beacons.put(st.key(), st);
                    save();
                }
                open(e.getPlayer(), st);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Beacon st = null;
        if (b.getType() == Material.BEACON) {
            st = beaconAt(b);
        } else if (b.getType() == Material.DRAGON_EGG) {
            st = beaconAt(b.getRelative(0, -1, 0));
            if (st != null) {
                // Яйцо на маяке ломается обычным способом и выпадает предметом
                e.setCancelled(true);
                breakEgg(e.getPlayer(), b);
                return;
            }
        }
        if (st == null) {
            return;
        }
        beacons.remove(st.key());
        save();
        clearEffects(st);
        Msg.chat(e.getPlayer(), "Драконий маяк разрушен, эффекты сняты.", NamedTextColor.GRAY);
    }

    /** Ломает яйцо дракона Драконьего маяка и выдаёт его предметом. */
    private void breakEgg(Player p, Block egg) {
        Block below = egg.getRelative(0, -1, 0);
        Beacon st = beaconAt(below);
        if (st != null) {
            beacons.remove(st.key());
            save();
            clearEffects(st);
        }
        egg.setType(Material.AIR, false);
        egg.getWorld().dropItemNaturally(egg.getLocation().add(0.5, 0.3, 0.5),
                new ItemStack(Material.DRAGON_EGG));
        if (p != null) {
            p.playSound(p.getLocation(), "block.stone.break", 1f, 0.8f);
            Msg.chat(p, "Драконий маяк разрушен: яйцо выпало.", NamedTextColor.GRAY);
        }
    }

    // ------------------------------------------------------------------ поиск / уровень
    private Beacon beaconAt(Block beaconBlock) {
        String key = beaconBlock.getWorld().getName() + ":" + beaconBlock.getX() + ":"
                + beaconBlock.getY() + ":" + beaconBlock.getZ();
        return beacons.get(key);
    }

    private static boolean isBase(Material m) {
        return m == Material.IRON_BLOCK || m == Material.GOLD_BLOCK
                || m == Material.DIAMOND_BLOCK || m == Material.EMERALD_BLOCK
                || m == Material.NETHERITE_BLOCK;
    }

    /** Уровень ванильной пирамиды 0..4. */
    public int pyramidLevel(Block beaconBlock) {
        World w = beaconBlock.getWorld();
        int bx = beaconBlock.getX();
        int bz = beaconBlock.getZ();
        int by = beaconBlock.getY();
        for (int lvl = 1; lvl <= 4; lvl++) {
            int y = by - lvl;
            for (int dx = -lvl; dx <= lvl; dx++) {
                for (int dz = -lvl; dz <= lvl; dz++) {
                    if (!isBase(w.getBlockAt(bx + dx, y, bz + dz).getType())) {
                        return lvl - 1;
                    }
                }
            }
        }
        return 4;
    }

    private Block beaconBlock(Beacon b) {
        World w = Bukkit.getWorld(b.world);
        return w == null ? null : w.getBlockAt(b.x, b.y, b.z);
    }

    // ------------------------------------------------------------------ тик
    private void tick() {
        if (beacons.isEmpty()) {
            return;
        }
        boolean changed = false;
        java.util.Set<java.util.UUID> immune = new java.util.HashSet<>();
        for (Beacon b : new ArrayList<>(beacons.values())) {
            World w = Bukkit.getWorld(b.world);
            if (w == null) {
                continue;
            }
            Block beacon = w.getBlockAt(b.x, b.y, b.z);
            Block egg = w.getBlockAt(b.x, b.y + 1, b.z);
            if (beacon.getType() != Material.BEACON || egg.getType() != Material.DRAGON_EGG) {
                beacons.remove(b.key());
                changed = true;
                clearEffects(b);
                continue;
            }
            int level = pyramidLevel(beacon);
            if (level <= 0) {
                continue;
            }
            validateSelection(b, level);
            if (!b.primary.isEmpty() || !b.secondary.isEmpty()) {
                applyEffects(b, level, w, immune);
            }
        }
        if (changed) {
            save();
        }
        immunityNow.clear();
        immunityNow.addAll(immune);
    }

    private void validateSelection(Beacon b, int level) {
        Fx p = fx(b.primary);
        if (p == null) {
            b.primary = "";
            b.primaryII = false;
        } else if (p.minLevel > level) {
            b.primary = "";
            b.primaryII = false;
        }
        if (b.primaryII && level < 4) {
            b.primaryII = false;
        }
        if (p != null && p.exclusive) {
            b.secondary = "";
            b.primaryII = false;
        }
        Fx s = fx(b.secondary);
        if (s == null || s.minLevel > level || s.exclusive
                || s.id.equals(b.primary) || b.primaryII || b.primary.isEmpty()) {
            b.secondary = "";
        }
    }

    private void applyEffects(Beacon b, int level, World w,
                              java.util.Set<java.util.UUID> immune) {
        double radius = 24.0 * level;
        int radius2 = (int) Math.round(radius);
        double cx = b.x + 0.5;
        double cz = b.z + 0.5;
        for (Player p : plugin.players()) {
            if (!p.getWorld().equals(w)) {
                continue;
            }
            double dx = p.getLocation().getX() - cx;
            double dz = p.getLocation().getZ() - cz;
            if (Math.abs(dx) > radius2 || Math.abs(dz) > radius2) {
                continue;
            }
            applyOne(p, b.primary, b.primaryII ? 1 : 0);
            applyOne(p, b.secondary, 0);
            if (b.primary.equals("immunity") || b.secondary.equals("immunity")) {
                immune.add(p.getUniqueId());
            }
        }
    }

    private void applyOne(Player p, String id, int amplifier) {
        if (id == null || id.isEmpty()) {
            return;
        }
        Fx f = fx(id);
        if (f == null) {
            return;
        }
        if (f.potion != null) {
            p.addPotionEffect(new PotionEffect(f.potion, 80, amplifier,
                    true, false, true));
            return;
        }
        switch (id) {
            case "immunity" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 80, 0,
                        true, false, true));
                p.setFireTicks(0);
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.DARKNESS);
                p.removePotionEffect(PotionEffectType.POISON);
                p.removePotionEffect(PotionEffectType.WITHER);
            }
            default -> {
            }
        }
    }

    /** Снимает эффекты маяка с ближайших игроков (при разрушении). */
    private void clearEffects(Beacon b) {
        World w = Bukkit.getWorld(b.world);
        if (w == null) {
            return;
        }
        List<PotionEffectType> types = new ArrayList<>();
        for (String id : new String[]{b.primary, b.secondary}) {
            Fx f = fx(id);
            if (f != null && f.potion != null) {
                types.add(f.potion);
            }
        }
        for (Player p : plugin.players()) {
            if (!p.getWorld().equals(w)) {
                continue;
            }
            for (PotionEffectType t : types) {
                PotionEffect eff = p.getPotionEffect(t);
                if (eff != null && eff.getDuration() <= 100) {
                    p.removePotionEffect(t);
                }
            }
        }
    }

    // ------------------------------------------------------------------ GUI
    private int guiLevel(Beacon b) {
        Block block = beaconBlock(b);
        return block == null ? 0 : pyramidLevel(block);
    }

    public void open(Player p, Beacon b) {
        int level = guiLevel(b);
        Holder holder = new Holder(b);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Драконий маяк", NamedTextColor.DARK_PURPLE,
                        TextDecoration.BOLD));
        holder.inventory = inv;

        ItemStack info = new ItemStack(Material.DRAGON_EGG);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("Драконий маяк", NamedTextColor.LIGHT_PURPLE,
                TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Уровень пирамиды: " + level + "/4",
                level > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.text("Радиус: " + (24 * Math.max(0, level)) + " блоков",
                NamedTextColor.GRAY));
        lore.add(Component.text("Работает без доступа к небу", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("ЛКМ — выбрать/убрать", NamedTextColor.YELLOW));
        lore.add(Component.text("ПКМ — усилить до II (4 ур.)", NamedTextColor.AQUA));
        im.lore(lore);
        info.setItemMeta(im);
        inv.setItem(4, info);

        int idx = 0;
        for (Fx f : EFFECTS) {
            int slot = SLOTS[idx++];
            boolean unlocked = f.minLevel <= level;
            ItemStack it = new ItemStack(unlocked ? f.icon : Material.BARRIER);
            ItemMeta m = it.getItemMeta();
            boolean selected = unlocked && (f.id.equals(b.primary) || f.id.equals(b.secondary));
            m.displayName(Component.text(f.name,
                    unlocked ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
            List<Component> l = new ArrayList<>();
            if (!unlocked) {
                l.add(Component.text("Нужен уровень пирамиды " + f.minLevel,
                        NamedTextColor.RED));
            } else {
                if (f.id.equals(b.primary)) {
                    l.add(Component.text("Основной"
                            + (b.primaryII ? " (II)" : " (I)"), NamedTextColor.GREEN));
                } else if (f.id.equals(b.secondary)) {
                    l.add(Component.text("Вторичный (I)", NamedTextColor.AQUA));
                }
                if (f.exclusive) {
                    l.add(Component.text("Эксклюзивный", NamedTextColor.GOLD));
                }
                if (f.minLevel > 1) {
                    l.add(Component.text("Открывается с " + f.minLevel + " ур.",
                            NamedTextColor.DARK_GRAY));
                }
                l.add(Component.text("ЛКМ — выбрать/убрать", NamedTextColor.YELLOW));
                if (level >= 4) {
                    l.add(Component.text("ПКМ — усилить до II", NamedTextColor.AQUA));
                }
            }
            m.lore(l);
            if (selected) {
                // Подсветка выбранного эффекта — зачарованное сияние
                m.setEnchantmentGlintOverride(true);
            }
            it.setItemMeta(m);
            inv.setItem(slot, it);
        }
        if (plugin.achievements != null) {
            plugin.achievements.onBeacon(p, level, false, "");
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 27) {
            return;
        }
        int idx = -1;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return;
        }
        Fx f = EFFECTS.get(idx);
        int level = guiLevel(h.beacon);
        if (f.minLevel > level) {
            p.playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
            return;
        }
        Beacon b = h.beacon;
        boolean right = e.isRightClick();
        boolean shift = e.isShiftClick();

        if (right) {
            // Улучшить/ухудшить основной эффект
            if (level >= 4 && f.id.equals(b.primary)) {
                b.primaryII = !b.primaryII;
                if (b.primaryII) {
                    b.secondary = "";
                }
            } else if (level >= 4) {
                b.primary = f.id;
                b.primaryII = true;
                b.secondary = "";
            }
        } else if (f.id.equals(b.primary)) {
            // убрать основной
            b.primary = "";
            b.primaryII = false;
            if (b.secondary.isEmpty()) {
                // nothing
            }
        } else if (f.id.equals(b.secondary)) {
            b.secondary = "";
        } else if (b.primary.isEmpty()) {
            b.primary = f.id;
            b.primaryII = false;
        } else if (f.exclusive || b.primaryII) {
            // эксклюзив заменяет всё
            if (f.exclusive) {
                b.primary = f.id;
                b.secondary = "";
                b.primaryII = false;
            } else {
                b.primary = f.id;
                b.primaryII = false;
                b.secondary = "";
            }
        } else {
            Fx prim = fx(b.primary);
            if (prim != null && prim.exclusive) {
                // был эксклюзив — заменяем
                b.primary = f.id;
                b.secondary = "";
                b.primaryII = false;
            } else {
                b.secondary = f.id;
            }
        }
        validateSelection(b, level);
        save();
        if (plugin.achievements != null) {
            Fx chosen = fx(b.primary.isEmpty() ? b.secondary : b.primary);
            plugin.achievements.onBeacon(p, level,
                    chosen != null && chosen.exclusive,
                    chosen == null ? "" : chosen.id);
        }
        p.playSound(p.getLocation(), "ui.button.click", 0.6f, 1.4f);
        open(p, b);
    }

    /** «Неуязвимость»: блокируем урон от огня, яда и иссушения. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !immunityNow.contains(p.getUniqueId())) {
            return;
        }
        switch (e.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE, POISON, WITHER -> {
                e.setCancelled(true);
                p.setFireTicks(0);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    /** Для /xmh reload: перечитать файл. */
    public void reload() {
        beacons.clear();
        load();
    }

    public int count() {
        return beacons.size();
    }
}
