package ru.xmusya.hard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Энд-механики (часть 2): пул предметов для рамок, дисплеи, награды,
 * цикл сброса портала, защита портала и комнаты.
 */
public final class EndPortal implements Listener {

    private final XmusyaHard plugin;
    private final EndCfg cfg;
    private final EndState state;
    private final EndManager manager;

    private final Random rnd = new Random();
    private final NamespacedKey displayKey = new NamespacedKey("xmusyahard", "end_frame");
    private final NamespacedKey displayTypeKey = new NamespacedKey("xmusyahard", "end_frame_type");

    /** Кеш дисплеев: ключ рамки -> [itemDisplayUUID, textDisplayUUID]. */
    private final Map<String, UUID[]> displays = new HashMap<>();
    /** Фаза левитации (чтобы не дёргались одинаково). */
    private final Map<String, Float> phases = new HashMap<>();

    public EndPortal(XmusyaHard plugin, EndCfg cfg, EndState state, EndManager manager) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.state = state;
        this.manager = manager;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickDisplays,
                20L, Math.max(1, cfg.displayIntervalTicks));
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAllDisplays, 60L);
    }

    // ================================================================== рамки
    /** Вызывается EndManager, когда нашёл комнату портала. */
    public void adoptFrames(List<Block> blocks) {
        List<Block> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(Block::getY)
                .thenComparingInt(Block::getX).thenComparingInt(Block::getZ));

        boolean same = sorted.size() == state.frames.size();
        if (same) {
            for (Block b : sorted) {
                if (!state.frames.containsKey(EndState.keyOf(b))) {
                    same = false;
                    break;
                }
            }
        }
        if (!same) {
            Map<String, EndState.Frame> old = new HashMap<>(state.frames);
            state.frames.clear();
            assignItems(sorted, old);
            state.save();
            plugin.getLogger().info("Найдено рамок портала: " + sorted.size());
            // уточняем координаты крепости по центру портала
            int sx = 0, sz = 0, n = 0;
            for (EndState.Frame f : state.frames.values()) {
                sx += f.x;
                sz += f.z;
                n++;
            }
            if (n > 0 && state.strongholdChosen) {
                state.strongholdX = sx / n;
                state.strongholdY = state.frames.values().iterator().next().y;
                state.strongholdZ = sz / n;
                state.save();
            }
        }
        syncFromBlocks();
        refreshAllDisplays();
    }

    private void assignItems(List<Block> blocks, Map<String, EndState.Frame> old) {
        int frameCount = Math.min(cfg.portalFrames, blocks.size());
        List<EndCfg.PoolItem> pool = new ArrayList<>(cfg.pool);
        Collections.shuffle(pool, rnd);

        Map<Material, Integer> used = new HashMap<>();
        int cursor = 0;
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            String bk = b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
            EndState.Frame keep = old.get(bk);
            if (keep != null) {
                state.frames.put(bk, keep);
                phases.putIfAbsent(bk, rnd.nextFloat() * 6.283f);
                continue;
            }
            EndState.Frame f = new EndState.Frame();
            f.world = b.getWorld().getName();
            f.x = b.getX();
            f.y = b.getY();
            f.z = b.getZ();

            if (i < frameCount) {
                EndCfg.PoolItem pick = null;
                for (int t = 0; t < pool.size(); t++) {
                    EndCfg.PoolItem cand = pool.get((cursor + t) % pool.size());
                    if (used.getOrDefault(cand.material, 0) < cand.maxPerPortal) {
                        pick = cand;
                        cursor = (cursor + t + 1) % pool.size();
                        break;
                    }
                }
                if (pick == null) {
                    pick = pool.get(rnd.nextInt(pool.size()));
                }
                used.merge(pick.material, 1, Integer::sum);
                f.itemId = pick.material.name();
                f.itemName = pick.name;
                f.amount = Math.max(1, pick.amount);
            }

            if (b.getBlockData() instanceof EndPortalFrame data && data.hasEye()) {
                f.filled = true;
                f.contributor = "(ваниль)";
            }
            state.frames.put(f.key(), f);
            phases.putIfAbsent(f.key(), rnd.nextFloat() * 6.283f);
        }
        state.save();
    }

    /** Подтягивает filled/contributor из блоков (если портал меняли вне плагина). */
    private void syncFromBlocks() {
        for (EndState.Frame f : state.frames.values()) {
            Location l = f.loc();
            if (l == null) {
                continue;
            }
            Block b = l.getBlock();
            if (b.getType() != Material.END_PORTAL_FRAME) {
                continue;
            }
            if (b.getBlockData() instanceof EndPortalFrame data) {
                if (data.hasEye() && !f.filled) {
                    f.filled = true;
                    if (f.contributor.isEmpty()) {
                        f.contributor = "(ваниль)";
                    }
                } else if (!data.hasEye() && f.filled) {
                    f.filled = false;
                    f.contributor = "";
                }
            }
        }
    }

    public void refreshAllDisplays() {
        for (EndState.Frame f : state.frames.values()) {
            refreshDisplays(f);
        }
    }

    // ================================================================== клик по рамке
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFrameClick(PlayerInteractEvent e) {
        if (!cfg.enabled) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.END_PORTAL_FRAME) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.ENDER_EYE) {
            return;
        }
        e.setCancelled(true);

        if (!(b.getBlockData() instanceof EndPortalFrame data)) {
            return;
        }
        if (state.frames.isEmpty()) {
            forceScan();
            Msg.action(p, "Портал ещё не просканирован — попробуйте через пару секунд.");
            return;
        }
        String key = EndState.keyOf(b);
        EndState.Frame f = state.frames.get(key);
        if (f == null) {
            Msg.action(p, "Этот портал сломан — око не подходит.");
            p.playSound(Msg.villagerNo());
            return;
        }
        if (data.hasEye() || f.filled) {
            Msg.action(p, "Рамка уже активирована: " + f.contributor);
            return;
        }
        if (!hasResources(p, f)) {
            Msg.action(p, missingText(p, f));
            p.playSound(Msg.villagerNo());
            return;
        }
        takeResources(p, f);
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE && item.getAmount() > 0) {
            item.setAmount(item.getAmount() - 1);
        }

        data.setEye(true);
        b.setBlockData(data, true);
        f.filled = true;
        f.contributor = p.getName();
        state.save();

        refreshDisplays(f);
        reward(p);
        checkHearts(p);
        if (state.filledCount() >= state.frames.size()) {
            openPortal();
        }
        state.save();
    }

    private boolean hasResources(Player p, EndState.Frame f) {
        return countInInventory(p, Material.matchMaterial(f.itemId)) >= f.amount;
    }

    private String missingText(Player p, EndState.Frame f) {
        Material m = Material.matchMaterial(f.itemId);
        int have = m == null ? 0 : countInInventory(p, m);
        return "Нужно: " + f.itemName + " x" + f.amount
                + " (у вас " + have + "/" + f.amount + ")";
    }

    private void takeResources(Player p, EndState.Frame f) {
        Material m = Material.matchMaterial(f.itemId);
        if (m == null) {
            return;
        }
        int left = f.amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack st = contents[i];
            if (st == null || st.getType() != m) {
                continue;
            }
            int take = Math.min(left, st.getAmount());
            st.setAmount(st.getAmount() - take);
            if (st.getAmount() <= 0) {
                p.getInventory().setItem(i, null);
            }
            left -= take;
        }
    }

    private static int countInInventory(Player p, Material m) {
        if (m == null) {
            return 0;
        }
        int n = 0;
        for (ItemStack st : p.getInventory().getContents()) {
            if (st != null && st.getType() == m) {
                n += st.getAmount();
            }
        }
        return n;
    }

    /** Собираем портал вручную (ванильный генератор не срабатывает от setEye). */
    private void openPortal() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int y = Integer.MIN_VALUE;
        World w = null;
        for (EndState.Frame f : state.frames.values()) {
            minX = Math.min(minX, f.x);
            maxX = Math.max(maxX, f.x);
            minZ = Math.min(minZ, f.z);
            maxZ = Math.max(maxZ, f.z);
            y = f.y;
            w = f.loc() == null ? w : f.loc().getWorld();
        }
        if (w == null) {
            return;
        }
        int cx = (minX + maxX) / 2;
        int cz = (minZ + maxZ) / 2;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                for (int dy = 0; dy <= 2; dy++) {
                    w.getBlockAt(x, y + dy, z).setType(Material.END_PORTAL, false);
                }
            }
        }
        Msg.broadcast("Портал в Энд открыт!", NamedTextColor.LIGHT_PURPLE);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(Msg.witherSpawn());
        }
    }

    // ================================================================== награды
    private void reward(Player p) {
        String[] effects = {"HASTE", "SPEED", "STRENGTH", "RESISTANCE",
                "FIRE_RESISTANCE", "NIGHT_VISION", "REGENERATION", "ABSORPTION"};
        int[] amps = {0, 0, 0, 0, 0, 0, 0, 0};
        int idx = rnd.nextInt(effects.length);
        PotionEffectType type = switch (effects[idx]) {
            case "HASTE" -> PotionEffectType.HASTE;
            case "SPEED" -> PotionEffectType.SPEED;
            case "STRENGTH" -> PotionEffectType.STRENGTH;
            case "RESISTANCE" -> PotionEffectType.RESISTANCE;
            case "FIRE_RESISTANCE" -> PotionEffectType.FIRE_RESISTANCE;
            case "NIGHT_VISION" -> PotionEffectType.NIGHT_VISION;
            case "REGENERATION" -> PotionEffectType.REGENERATION;
            default -> PotionEffectType.ABSORPTION;
        };
        manager.addTimedBuff(p, type.getKey().getKey(), amps[idx]);
        Msg.chat(p, Msg.c("Рамка активирована! Награда на ", NamedTextColor.GRAY)
                .append(Msg.bold(cfg.buffHours + " ч", NamedTextColor.GREEN))
                .append(Msg.c(": ", NamedTextColor.GRAY))
                .append(Msg.bold(russianEffect(effects[idx]), NamedTextColor.GREEN)));
        p.playSound(Msg.click());
    }

    private static String russianEffect(String name) {
        return switch (name) {
            case "HASTE" -> "Спешка";
            case "SPEED" -> "Скорость";
            case "STRENGTH" -> "Сила";
            case "RESISTANCE" -> "Сопротивление";
            case "FIRE_RESISTANCE" -> "Огнестойкость";
            case "NIGHT_VISION" -> "Ночное зрение";
            case "REGENERATION" -> "Регенерация";
            case "ABSORPTION" -> "Поглощение";
            default -> name;
        };
    }

    /** За каждые N закрытых игроком рамок — навсегда +1 сердце. */
    private void checkHearts(Player p) {
        int mine = 0;
        for (EndState.Frame f : state.frames.values()) {
            if (f.filled && p.getName().equals(f.contributor)) {
                mine++;
            }
        }
        int target = mine / cfg.heartsPerFrames;
        int awarded = state.awardedHearts.getOrDefault(p.getUniqueId(), 0);
        if (target > awarded) {
            int add = target - awarded;
            state.awardedHearts.put(p.getUniqueId(), target);
            manager.addBonusHearts(p, add);
            Msg.chat(p, Msg.c("За " + cfg.heartsPerFrames + " рамки: ", NamedTextColor.GRAY)
                    .append(Msg.bold("+" + add + " сердце навсегда!", NamedTextColor.RED)));
        }
    }

    // ================================================================== цикл
    public void onNewDay(int day) {
        if (!cfg.enabled || state.frames.isEmpty()) {
            return;
        }
        if (day < cfg.cycleStartDay) {
            return;
        }
        if ((day - cfg.cycleStartDay) % cfg.cycleIntervalDays != 0) {
            return;
        }
        if (state.lastCycleDay == day) {
            return;
        }
        state.lastCycleDay = day;
        cycleReset();
        state.save();
    }

    private void cycleReset() {
        // Гасим портал
        World w = null;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int y = Integer.MIN_VALUE;
        for (EndState.Frame f : state.frames.values()) {
            minX = Math.min(minX, f.x);
            maxX = Math.max(maxX, f.x);
            minZ = Math.min(minZ, f.z);
            maxZ = Math.max(maxZ, f.z);
            y = f.y;
            Location l = f.loc();
            if (l != null) {
                w = l.getWorld();
            }
        }
        if (w != null) {
            int cx = (minX + maxX) / 2;
            int cz = (minZ + maxZ) / 2;
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        Block b = w.getBlockAt(x, y + dy, z);
                        if (b.getType() == Material.END_PORTAL) {
                            b.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }

        // Сбрасываем 1..N случайных рамок
        List<EndState.Frame> all = new ArrayList<>(state.frames.values());
        Collections.shuffle(all, rnd);
        int count = cfg.cycleResetMin + rnd.nextInt(Math.max(1,
                cfg.cycleResetMax - cfg.cycleResetMin + 1));
        count = Math.min(count, all.size());

        for (int i = 0; i < count; i++) {
            EndState.Frame f = all.get(i);
            Location l = f.loc();
            if (l == null) {
                continue;
            }
            Block b = l.getBlock();
            if (b.getType() == Material.END_PORTAL_FRAME
                    && b.getBlockData() instanceof EndPortalFrame data) {
                data.setEye(false);
                b.setBlockData(data, false);
            }
            f.filled = false;
            f.contributor = "";
            EndCfg.PoolItem pick = cfg.pool.get(rnd.nextInt(cfg.pool.size()));
            f.itemId = pick.material.name();
            f.itemName = pick.name;
            f.amount = Math.max(1, pick.amount);
            refreshDisplays(f);
        }
        state.save();
        Msg.broadcast("Портал в Энд погас! Сброшено рамок: " + count
                + ". Соберите ресурсы заново.", NamedTextColor.DARK_PURPLE);
    }

    // ================================================================== защита
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (!cfg.enabled) {
            return;
        }
        Block b = e.getBlock();
        boolean portalBlock = b.getType() == Material.END_PORTAL;
        boolean ourFrame = b.getType() == Material.END_PORTAL_FRAME
                && state.frames.containsKey(EndState.keyOf(b));
        boolean roomSpawner = manager.isPortalRoomSpawner(b);

        if (!portalBlock && !ourFrame && !roomSpawner) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (plugin.isBot(p)) {
            return;
        }

        // Мгновенная регенерация + визуальный взрыв
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 6.0));
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().createExplosion(loc, 0.0f, false, false, p);
        b.getWorld().strikeLightningEffect(loc);
        p.playSound(Msg.villagerNo());
        manager.curse(p, "Портал и его рамки трогать нельзя!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlaceLiquid(BlockPlaceEvent e) {
        if (!cfg.enabled || state.frames.isEmpty()) {
            return;
        }
        Material t = e.getBlockPlaced().getType();
        if (t != Material.WATER && t != Material.LAVA) {
            return;
        }
        if (nearFrames(e.getBlockPlaced().getLocation(), 8.0)) {
            e.setCancelled(true);
            Msg.action(e.getPlayer(), "Рядом с порталом нельзя лить воду или лаву!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFlow(BlockFromToEvent e) {
        if (!cfg.enabled || state.frames.isEmpty()) {
            return;
        }
        Material t = e.getBlock().getType();
        if (t != Material.WATER && t != Material.LAVA) {
            return;
        }
        if (nearFrames(e.getToBlock().getLocation(), 8.0)) {
            e.setCancelled(true);
        }
    }

    private boolean nearFrames(Location l, double r) {
        for (EndState.Frame f : state.frames.values()) {
            Location fl = f.loc();
            if (fl == null || !fl.getWorld().equals(l.getWorld())) {
                continue;
            }
            if (fl.distanceSquared(l) <= r * r) {
                return true;
            }
        }
        return false;
    }

    /** Чешуйницы при 6+ рамках с шансом спавнят эндермитов. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSilverfishHurt(EntityDamageEvent e) {
        if (!cfg.enabled || e.getEntity().getType() != org.bukkit.entity.EntityType.SILVERFISH) {
            return;
        }
        trySpawnEndermite(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSilverfishDeath(EntityDeathEvent e) {
        if (!cfg.enabled || e.getEntityType() != org.bukkit.entity.EntityType.SILVERFISH) {
            return;
        }
        trySpawnEndermite(e.getEntity());
    }

    private final Set<UUID> endermiteGuard = new HashSet<>();

    private void trySpawnEndermite(Entity silverfish) {
        if (state.filledCount() < 6) {
            return;
        }
        if (!endermiteGuard.add(silverfish.getUniqueId())) {
            return;
        }
        if (rnd.nextDouble() > 0.30) {
            return;
        }
        silverfish.getWorld().spawnEntity(silverfish.getLocation(),
                org.bukkit.entity.EntityType.ENDERMITE);
        if (endermiteGuard.size() > 2000) {
            endermiteGuard.clear();
        }
    }

    // ================================================================== дисплеи
    private void refreshDisplays(EndState.Frame f) {
        UUID[] ids = displays.get(f.key());
        if (ids != null) {
            for (UUID id : ids) {
                Entity e = id == null ? null : Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            }
            displays.remove(f.key());
        }
        Location base = f.loc();
        if (base == null) {
            return;
        }
        World w = base.getWorld();
        if (!w.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
            return;
        }

        // TextDisplay: название + количество (или «активировано: ник»)
        Location tl = base.clone().add(0.5, 0.95, 0.5);
        TextDisplay text = w.spawn(tl, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(false);
            td.setShadowed(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
            td.text(textFor(f));
            td.setPersistent(false);
            td.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, f.key());
            td.getPersistentDataContainer().set(displayTypeKey, PersistentDataType.STRING, "text");
        });

        UUID itemId = null;
        if (!f.filled) {
            Material m = Material.matchMaterial(f.itemId);
            if (m != null) {
                Location il = base.clone().add(0.5, 1.2, 0.5);
                ItemDisplay item = w.spawn(il, ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(m, 1));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setPersistent(false);
                    d.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, f.key());
                    d.getPersistentDataContainer().set(displayTypeKey, PersistentDataType.STRING, "item");
                    d.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f), new Quaternionf(),
                            new Vector3f(0.55f, 0.55f, 0.55f), new Quaternionf()));
                });
                itemId = item.getUniqueId();
            }
        }
        displays.put(f.key(), new UUID[]{itemId, text.getUniqueId()});
    }

    private Component textFor(EndState.Frame f) {
        if (f.filled) {
            return Component.text("✔ активировано: " + f.contributor,
                    NamedTextColor.GREEN, TextDecoration.BOLD);
        }
        return Component.text(f.itemName + " x" + f.amount, NamedTextColor.WHITE,
                        TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("нажмите оком", NamedTextColor.GRAY));
    }

    private void tickDisplays() {
        if (!cfg.enabled || state.frames.isEmpty()) {
            return;
        }
        double t = System.currentTimeMillis() / 1000.0;
        for (EndState.Frame f : state.frames.values()) {
            UUID[] ids = displays.get(f.key());
            if (ids == null) {
                refreshDisplays(f);
                ids = displays.get(f.key());
                if (ids == null) {
                    continue;
                }
            }
            Location base = f.loc();
            if (base == null) {
                continue;
            }
            World w = base.getWorld();
            if (!w.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
                continue;
            }
            Player nearest = nearestPlayer(base, cfg.displayViewRange);
            if (nearest == null) {
                continue;
            }
            float phase = phases.computeIfAbsent(f.key(), k -> rnd.nextFloat() * 6.283f);
            float offset = (float) (Math.sin(t * 1.6 + phase) * 0.12);

            Entity itemE = ids[0] == null ? null : Bukkit.getEntity(ids[0]);
            if (itemE instanceof ItemDisplay item) {
                Quaternionf q = facing(base.clone().add(0, 1.2, 0), nearest);
                item.setTransformation(new Transformation(
                        new Vector3f(0f, offset, 0f), q,
                        new Vector3f(0.55f, 0.55f, 0.55f), new Quaternionf()));
                item.setInterpolationDuration(3);
                item.setInterpolationDelay(0);
            }
        }
    }

    private Player nearestPlayer(Location l, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : plugin.players()) {
            if (p.getWorld() != l.getWorld()) {
                continue;
            }
            double d = p.getLocation().distanceSquared(l);
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private Quaternionf facing(Location from, Player p) {
        Vector dir = p.getEyeLocation().toVector().subtract(from.toVector()).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dir.getY()))));
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch));
    }

    // ================================================================== админ
    public void forceScan() {
        Location l = manager.strongholdLocation();
        if (l == null) {
            plugin.getLogger().warning("Крепость не выбрана — /xmh end locate");
            return;
        }
        manager.queueScan(l, cfg.scanRadiusChunks);
    }

    public void resetPortal() {
        state.frames.clear();
        displays.clear();
        state.lastCycleDay = -1;
        state.save();
        forceScan();
    }
}
