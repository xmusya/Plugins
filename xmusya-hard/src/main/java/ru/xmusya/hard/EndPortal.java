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
import org.bukkit.event.block.Action;
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

    /** Блок-заглушка на месте сломанной рамки (по нему идёт починка). */
    private static final Material BROKEN_BLOCK = Material.CRYING_OBSIDIAN;

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

        List<EndState.Frame> brokenOld = new ArrayList<>();
        for (EndState.Frame f : state.frames.values()) {
            if (f.broken) {
                brokenOld.add(f);
            }
        }
        boolean same = sorted.size() + brokenOld.size() == state.frames.size();
        if (same) {
            for (Block b : sorted) {
                EndState.Frame f = state.frames.get(EndState.keyOf(b));
                if (f == null || f.broken) {
                    same = false;
                    break;
                }
            }
        }
        if (!same) {
            Map<String, EndState.Frame> old = new HashMap<>(state.frames);
            state.frames.clear();
            assignItems(sorted, old);
            // Возвращаем сломанные рамки (их блоков в мире нет — заглушка)
            for (EndState.Frame f : brokenOld) {
                if (!state.frames.containsKey(f.key())) {
                    state.frames.put(f.key(), f);
                    phases.putIfAbsent(f.key(), rnd.nextFloat() * 6.283f);
                }
            }
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

    /** Чиним выбитые рамки (вызывается при /xmh end scan). */
    public void repair() {
        repairFrames();
        refreshAllDisplays();
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
        if (b == null) {
            return;
        }
        // Сломанная рамка — починка по заглушке
        if (b.getType() == BROKEN_BLOCK) {
            EndState.Frame bf = state.frames.get(EndState.keyOf(b));
            if (bf != null && bf.broken) {
                e.setCancelled(true);
                if (plugin.achievements != null) {
                    plugin.achievements.onSecretFrame(e.getPlayer());
                }
                if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    repairBrokenFrame(e.getPlayer(), b, bf);
                }
            }
            return;
        }
        if (b.getType() != Material.END_PORTAL_FRAME) {
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
        String key = EndState.keyOf(b);
        EndState.Frame f = state.frames.get(key);
        if (f == null) {
            String ck = b.getWorld().getName() + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);
            if (plugin.endState != null && plugin.endState.disabledChunks.contains(ck)) {
                // Чужая крепость — портал сломан, око не подходит
                Msg.action(p, "Этот портал сломан — око не подходит.");
                p.playSound(Msg.villagerNo());
                return;
            }
            // Рамку не знаем (ещё не просканировали или портал переехал) —
            // запускаем скан ещё раз, чтобы не писать «портал сломан».
            forceScan();
            Msg.action(p, "Портал ещё не просканирован — попробуйте через пару секунд.");
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
        if (plugin.achievements != null) {
            plugin.achievements.onFrameFill(p);
        }
        if (state.filledCount() >= state.frames.size()) {
            openPortal(p);
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
    private void openPortal(Player p) {
        if (plugin.achievements != null) {
            plugin.achievements.onPortalOpen(p);
        }
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
        // Портал Энда — плоский слой 3×3 на уровне рамок (ванильная механика).
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                w.getBlockAt(x, y, z).setType(Material.END_PORTAL, false);
            }
        }
        // Через 5 тиков подчищаем возможные дубли порталов выше/ниже
        // (в старых версиях плагин заполнял 3 слоя по Y).
        final World fw = w;
        final int fcx = cx, fcz = cz, fy = y;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        if (dy == 0) {
                            continue;
                        }
                        Block b = fw.getBlockAt(fcx + dx, fy + dy, fcz + dz);
                        if (b.getType() == Material.END_PORTAL) {
                            b.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }, 5L);
        Msg.broadcast(Messages.raw("end.portal_open", "Портал в Энд открыт!"),
                NamedTextColor.LIGHT_PURPLE);
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.playSound(Msg.witherSpawn());
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
        checkBreakEvents(day);
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
            assignRandomItem(f);
            refreshDisplays(f);
        }
        state.save();
        Msg.broadcast(Messages.format("end.portal_off",
                "Портал в Энд погас! Сброшено рамок: {0}. Соберите ресурсы заново.", count),
                NamedTextColor.DARK_PURPLE);
    }

    // ================================================================== защита
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (!cfg.enabled) {
            return;
        }
        Block b = e.getBlock();
        boolean portalBlock = b.getType() == Material.END_PORTAL;
        EndState.Frame at = state.frames.get(EndState.keyOf(b));
        boolean ourFrame = b.getType() == Material.END_PORTAL_FRAME
                && at != null;
        boolean brokenFrame = b.getType() == BROKEN_BLOCK && at != null && at.broken;
        boolean roomSpawner = manager.isPortalRoomSpawner(b);

        if (!portalBlock && !ourFrame && !brokenFrame && !roomSpawner) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (plugin.isBot(p)) {
            return;
        }
        if (!cfg.breakEffects) {
            Msg.action(p, "Это трогать нельзя!");
            p.playSound(Msg.villagerNo());
            return;
        }

        // Мгновенная регенерация + визуальный взрыв (по умолчанию выключено)
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

        // TextDisplay: только количество (или галочка у активированной рамки)
        Location tl = base.clone().add(0.5, 0.9, 0.5);
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
            Material m = f.broken
                    ? brokenIcon(f)
                    : Material.matchMaterial(f.itemId);
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
                            new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
                });
                itemId = item.getUniqueId();
            }
        }
        displays.put(f.key(), new UUID[]{itemId, text.getUniqueId()});
    }

    private Component textFor(EndState.Frame f) {
        if (f.broken) {
            return Component.text("⚠ " + (f.repairTier.equals("ultra") ? "УЛЬТРА" : "СЛОМАНО"),
                    NamedTextColor.RED, TextDecoration.BOLD);
        }
        if (f.filled) {
            return Component.text("✔", NamedTextColor.GREEN, TextDecoration.BOLD);
        }
        return Component.text("×" + f.amount, NamedTextColor.WHITE, TextDecoration.BOLD);
    }

    private Material brokenIcon(EndState.Frame f) {
        Material head = Material.matchMaterial(f.repairHead);
        return head != null ? head : Material.NETHERITE_BLOCK;
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
                        new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
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

    // ================================================================== сломы и починка
    private void checkBreakEvents(int day) {
        boolean normal = cfg.normalBreakEnabled && day >= cfg.normalBreakStartDay
                && (day - cfg.normalBreakStartDay) % cfg.normalBreakInterval == 0
                && state.lastNormalBreakDay != day;
        if (normal) {
            state.lastNormalBreakDay = day;
            if (rnd.nextDouble() < cfg.normalBreakChance) {
                int n = breakFrames(cfg.normalBreakFrames, "normal");
                if (n > 0) {
                    Msg.broadcast(Messages.format("end.break_normal",
                            "Обычный слом: выбито рамок портала — {0}. Их нужно починить вручную!", n),
                            NamedTextColor.DARK_RED);
                }
            }
            state.save();
        }
        boolean ultra = cfg.ultraBreakEnabled && day >= cfg.ultraBreakStartDay
                && (day - cfg.ultraBreakStartDay) % cfg.ultraBreakInterval == 0
                && state.lastUltraBreakDay != day;
        if (ultra) {
            state.lastUltraBreakDay = day;
            if (rnd.nextDouble() < cfg.ultraBreakChance) {
                int n = breakFrames(cfg.ultraBreakFrames, "ultra");
                if (n > 0) {
                    Msg.broadcast(Messages.raw("end.break_ultra",
                            "УЛЬТРА-СЛОМ: выбита рамка портала! Починка требует"
                            + " очень редких ресурсов и головы моба."), NamedTextColor.DARK_RED);
                }
            }
            state.save();
        }
    }

    private void assignRandomItem(EndState.Frame f) {
        if (cfg.pool.isEmpty()) {
            f.itemId = Material.ENDER_EYE.name();
            f.itemName = "Око Энда";
            f.amount = 1;
            return;
        }
        EndCfg.PoolItem pick = cfg.pool.get(rnd.nextInt(cfg.pool.size()));
        f.itemId = pick.material.name();
        f.itemName = pick.name;
        f.amount = Math.max(1, pick.amount);
    }

    /** Ломает до count случайных целых рамок. Возвращает сколько сломано. */
    private int breakFrames(int count, String tier) {
        List<EndState.Frame> candidates = new ArrayList<>();
        for (EndState.Frame f : state.frames.values()) {
            if (!f.broken && f.loc() != null) {
                candidates.add(f);
            }
        }
        Collections.shuffle(candidates, rnd);
        int broken = 0;
        for (int i = 0; i < Math.min(count, candidates.size()); i++) {
            EndState.Frame f = candidates.get(i);
            Location l = f.loc();
            if (l == null) {
                continue;
            }
            Block b = l.getBlock();
            if (b.getType() != Material.END_PORTAL_FRAME) {
                continue;
            }
            b.setType(BROKEN_BLOCK, false);
            f.broken = true;
            f.filled = false;
            f.contributor = "";
            f.repairTier = tier;
            if (tier.equals("ultra")) {
                EndCfg.HeadCfg h = randomHead();
                f.repairHead = h == null ? "" : h.material.name();
            } else {
                f.repairHead = "";
            }
            refreshDisplays(f);
            broken++;
        }
        state.save();
        return broken;
    }

    private EndCfg.HeadCfg randomHead() {
        if (cfg.ultraHeads.isEmpty()) {
            return null;
        }
        int total = 0;
        for (EndCfg.HeadCfg h : cfg.ultraHeads) {
            total += h.weight;
        }
        int roll = rnd.nextInt(Math.max(1, total));
        for (EndCfg.HeadCfg h : cfg.ultraHeads) {
            roll -= h.weight;
            if (roll < 0) {
                return h;
            }
        }
        return cfg.ultraHeads.get(cfg.ultraHeads.size() - 1);
    }

    /** Набор предметов для починки конкретной сломанной рамки. */
    public List<EndCfg.PoolItem> repairKit(EndState.Frame f) {
        List<EndCfg.PoolItem> kit = new ArrayList<>(f.repairTier.equals("ultra")
                ? cfg.ultraRepair : cfg.normalRepair);
        if (f.repairTier.equals("ultra") && !f.repairHead.isEmpty()) {
            Material head = Material.matchMaterial(f.repairHead);
            if (head != null) {
                EndCfg.PoolItem pi = new EndCfg.PoolItem();
                pi.material = head;
                pi.name = pretty(head);
                pi.amount = 1;
                kit.add(pi);
            }
        }
        return kit;
    }

    private static String pretty(Material m) {
        String n = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private boolean hasKit(Player p, List<EndCfg.PoolItem> kit) {
        for (EndCfg.PoolItem it : kit) {
            if (countInInventory(p, it.material) < it.amount) {
                return false;
            }
        }
        return true;
    }

    private String missingKitText(Player p, List<EndCfg.PoolItem> kit) {
        for (EndCfg.PoolItem it : kit) {
            int have = countInInventory(p, it.material);
            if (have < it.amount) {
                return "Для починки нужно: " + it.name + " x" + it.amount
                        + " (у вас " + have + "/" + it.amount + ")";
            }
        }
        return "Не хватает предметов для починки.";
    }

    private void takeKit(Player p, List<EndCfg.PoolItem> kit) {
        for (EndCfg.PoolItem it : kit) {
            takeMaterial(p, it.material, it.amount);
        }
    }

    private void takeMaterial(Player p, Material m, int amount) {
        int left = amount;
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

    private void repairBrokenFrame(Player p, Block b, EndState.Frame f) {
        if (plugin.isBot(p)) {
            return;
        }
        List<EndCfg.PoolItem> kit = repairKit(f);
        if (!hasKit(p, kit)) {
            Msg.action(p, missingKitText(p, kit));
            p.playSound(Msg.villagerNo());
            return;
        }
        takeKit(p, kit);
        b.setType(Material.END_PORTAL_FRAME, false);
        if (b.getBlockData() instanceof EndPortalFrame data) {
            data.setEye(false);
            b.setBlockData(data, false);
        }
        f.broken = false;
        f.filled = false;
        f.contributor = "";
        assignRandomItem(f);
        state.save();
        refreshDisplays(f);
        // Сильный бафф починщику на несколько РЕАЛЬНЫХ дней
        for (EndCfg.BuffCfg bc : cfg.repairBuffs) {
            manager.addTimedBuffForDays(p, bc.effect, bc.amplifier, cfg.repairBuffDays);
        }
        Msg.broadcast(p.getName() + " починил рамку портала ("
                + (f.repairTier.equals("ultra") ? "ультра" : "обычная")
                + ") и получил мощный бафф на " + cfg.repairBuffDays + " дн.!",
                NamedTextColor.LIGHT_PURPLE);
        p.playSound(Msg.click());
        if (plugin.achievements != null) {
            plugin.achievements.onRepair(p, f.repairTier.equals("ultra"));
        }
        // Рамка пустая — дальше игрок вставляет око обычным способом
    }

    private void restoreAllBroken() {
        for (EndState.Frame f : state.frames.values()) {
            if (!f.broken) {
                continue;
            }
            Location l = f.loc();
            if (l == null) {
                continue;
            }
            Block b = l.getBlock();
            if (b.getType() == BROKEN_BLOCK) {
                b.setType(Material.END_PORTAL_FRAME, false);
                if (b.getBlockData() instanceof EndPortalFrame data) {
                    data.setEye(false);
                    b.setBlockData(data, false);
                }
            }
            f.broken = false;
            f.repairTier = "normal";
            f.repairHead = "";
        }
        state.save();
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

    /** Полный сброс энд-механик (при /xmh reset или пересоздании мира). */
    public void resetAll() {
        // Убираем дисплеи
        for (UUID[] ids : displays.values()) {
            for (UUID id : ids) {
                Entity e = id == null ? null : Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            }
        }
        displays.clear();
        phases.clear();
        restoreAllBroken();
        // Чиним рамки, которые могли быть выбиты при подгрузке чанков
        repairFrames();
        // Гасим глаза в найденных рамках и убираем уже открытый портал
        for (EndState.Frame f : new ArrayList<>(state.frames.values())) {
            Location l = f.loc();
            if (l == null) {
                continue;
            }
            Block b = l.getBlock();
            if (b.getType() == Material.END_PORTAL_FRAME
                    && b.getBlockData() instanceof EndPortalFrame data) {
                if (data.hasEye()) {
                    data.setEye(false);
                    b.setBlockData(data, false);
                }
            }
        }
        state.resetAll();
        // Ищем крепость и рамки заново
        manager.ensureStronghold();
    }

    /** Восстанавливает рамки, которых не хватает в мире (например, выбитых). */
    private void repairFrames() {
        for (EndState.Frame f : new ArrayList<>(state.frames.values())) {
            if (f.broken) {
                continue; // только ручная сложная починка
            }
            Location l = f.loc();
            if (l == null || !l.getChunk().isLoaded()) {
                continue;
            }
            Block b = l.getBlock();
            if (b.getType() == Material.END_PORTAL_FRAME) {
                continue;
            }
            if (!b.isEmpty() && b.getType() != Material.END_PORTAL) {
                continue;
            }
            b.setType(Material.END_PORTAL_FRAME, false);
            if (b.getBlockData() instanceof EndPortalFrame data) {
                data.setEye(false);
                b.setBlockData(data, false);
            }
            plugin.getLogger().info("Рамка портала восстановлена: " + f.key());
        }
    }

    public void resetPortal() {
        restoreAllBroken();
        state.frames.clear();
        displays.clear();
        state.lastCycleDay = -1;
        state.save();
        forceScan();
    }
}
