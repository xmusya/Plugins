package ru.xmusya.hard;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Энд-механики (часть 1): одна рабочая крепость, капризные ока, жемчуг только
 * с эндерменов, спавнеры блейзов, внешние острова, щит дракона, спасение из
 * пустоты, яйцо дракона, проклятия.
 */
public final class EndManager implements Listener {

    private final XmusyaHard plugin;
    public final EndCfg cfg;
    public final EndState state;

    private final Random rnd = new Random();
    private final NamespacedKey heartsKey = new NamespacedKey("xmusyahard", "bonus_hearts");

    private final Map<UUID, Long> eyeCooldown = new HashMap<>();
    private final Map<UUID, Long> lastDragonHit = new HashMap<>();
    private final Map<String, Long> ghastCooldown = new HashMap<>();
    /** Известные спавнеры блейзов: ключ блока -> время последнего подтверждения. */
    private final Map<String, Long> blazeSpawners = new HashMap<>();
    /** Игроки, которых сейчас спасаем из пустоты (не спамим эффектами). */
    private final Set<UUID> voidRescue = new HashSet<>();

    private boolean locating = false;
    private final List<int[]> scanQueue = new ArrayList<>();
    private boolean scanScheduled = false;
    /** Предупреждение «рамки не найдены» пишем в лог только один раз. */
    private boolean scanWarned = false;

    public EndManager(XmusyaHard plugin, EndCfg cfg, EndState state) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.state = state;
    }

    // ------------------------------------------------------------------ запуск
    public void start() {
        // Поиск крепости — через 5 секунд после старта (на это время
        // барьер временно расширяется до search-border-size)
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureStronghold, 100L);
        // Обработка очереди чанков для поиска комнаты портала
        Bukkit.getScheduler().runTaskTimer(plugin, this::processScanQueue, 1L, 1L);
        // Спавнеры блейзов (кеш) + гасты
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBlazeSpawners, 100L, 100L);
        // Пустота / внешние острова / проклятия / яйцо / баффы
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHalfSecond, 10L, 10L);
        // Применение бонусных сердец и баффов
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickSlow, 100L, 100L);
        // Ночное зрение и прочие баффы портала — каждые 4 сек (чтобы не мигало)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPortalBuffs, 80L, 80L);
        // Проверка чужих порталов рядом с игроками (раз в 10 сек)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickNearbyPortals, 200L, 200L);
        // Если игрок рядом с порталом (±100 по X/Y/Z) — раз в 5 сек скан и починка
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPortalArea, 100L, 100L);
    }

    // ================================================================== крепость
    public void ensureStronghold() {
        if (state.strongholdChosen || locating) {
            return;
        }
        World ow = plugin.overworld();
        if (ow == null) {
            return;
        }
        locating = true;
        // Пока ищем — временно расширяем барьер (иначе кажется, что поиск
        // ограничен стартовыми 650 блоками). Потом возвращаем как было.
        Map<World, Double> savedBorders = new HashMap<>();
        boolean borderChanged = false;
        if (cfg.searchBorderSize > 0) {
            for (World w : Bukkit.getWorlds()) {
                savedBorders.put(w, w.getWorldBorder().getSize());
                w.getWorldBorder().setSize(cfg.searchBorderSize);
                borderChanged = true;
            }
        }
        Location spawn = ow.getSpawnLocation();
        Structure stronghold = Registry.STRUCTURE.get(NamespacedKey.minecraft("stronghold"));
        if (stronghold == null) {
            plugin.getLogger().warning("Не найдена структура minecraft:stronghold — энд-механики крепости отключены.");
            locating = false;
            return;
        }

        // Механика «одна крепость + ломаные порталы» отключена — берём ближайшую
        // без ограничений по радиусу.
        int minR = cfg.strongholdEnabled ? cfg.minRadius : 0;
        int maxR = cfg.strongholdEnabled ? cfg.maxRadius : 2_000_000;

        Location origin = spawn.clone();
        Location found = null;
        int guard = 0;
        while (guard++ < 32) {
            StructureSearchResult r;
            try {
                r = ow.locateNearestStructure(origin, stronghold, maxR, false);
            } catch (Throwable t) {
                plugin.getLogger().warning("Поиск крепости не удался: " + t.getMessage());
                break;
            }
            if (r == null || r.getLocation() == null) {
                break;
            }
            Location loc = r.getLocation();
            double dist = Math.hypot(loc.getX() - spawn.getX(), loc.getZ() - spawn.getZ());
            if (dist > maxR) {
                break;
            }
            if (dist >= minR) {
                found = loc;
                break;
            }
            // слишком близко — ищем следующую (стартуем дальше по направлению от спавна)
            double dx = loc.getX() - spawn.getX();
            double dz = loc.getZ() - spawn.getZ();
            double len = Math.max(1.0, Math.hypot(dx, dz));
            double step = dist + 1500.0;
            origin = spawn.clone().add(dx / len * step, 0, dz / len * step);
        }

        locating = false;
        if (borderChanged) {
            for (Map.Entry<World, Double> en : savedBorders.entrySet()) {
                en.getKey().getWorldBorder().setSize(en.getValue());
            }
        }
        if (found == null) {
            plugin.getLogger().warning("Не нашёл крепость в радиусе " + cfg.minRadius
                    + "–" + cfg.maxRadius + " блоков. Око будет вести к ближайшей доступной.");
            try {
                StructureSearchResult r = ow.locateNearestStructure(spawn, stronghold, maxR, false);
                found = r == null ? null : r.getLocation();
            } catch (Throwable ignored) {
            }
            if (found == null) {
                return;
            }
        }

        state.strongholdWorld = ow.getName();
        state.strongholdX = found.getBlockX();
        state.strongholdY = found.getBlockY();
        state.strongholdZ = found.getBlockZ();
        state.strongholdChosen = true;
        state.save();

        plugin.getLogger().info("Выбрана крепость Энда: " + state.strongholdX + ", "
                + state.strongholdY + ", " + state.strongholdZ
                + " (расстояние " + (int) Math.hypot(state.strongholdX - spawn.getX(),
                state.strongholdZ - spawn.getZ()) + " блоков).");
        queueScan(found, cfg.scanRadiusChunks);
    }

    /** Ставит в очередь чанки вокруг крепости для поиска комнаты портала. */
    public void queueScan(Location center, int radiusChunks) {
        scanQueue.clear();
        int ccx = center.getBlockX() >> 4;
        int ccz = center.getBlockZ() >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                scanQueue.add(new int[]{ccx + dx, ccz + dz});
            }
        }
        // ближе к центру — раньше
        scanQueue.sort(Comparator.comparingInt(a -> Math.abs(a[0] - ccx) + Math.abs(a[1] - ccz)));
        if (!scanScheduled) {
            scanScheduled = true;
        }
    }

    private void processScanQueue() {
        if (scanQueue.isEmpty()) {
            if (scanScheduled) {
                scanScheduled = false;
                if (state.frames.isEmpty() && !scanWarned) {
                    scanWarned = true;
                    plugin.getLogger().warning("Рамки портала не найдены рядом с крепостью — "
                            + "попробуйте /xmh end scan (повтор предупреждения отключён).");
                } else if (!state.frames.isEmpty()) {
                    scanWarned = false;
                }
            }
            return;
        }
        World ow = plugin.overworld();
        if (ow == null) {
            return;
        }
        int budget = cfg.scanChunksPerTick;
        while (budget-- > 0 && !scanQueue.isEmpty()) {
            int[] c = scanQueue.remove(0);
            if (!isStrongholdChunk(ow, c[0], c[1])) {
                continue;
            }
            List<Block> frames = scanFrames(ow, c[0], c[1]);
            if (!frames.isEmpty()) {
                // Портал 3×3 часто пересекает границу чанка (например, 9 рамок
                // в одном чанке и 3 в соседнем) — собираем рамки из всех
                // соседних чанков и соединяем только смежные.
                List<Block> all = new ArrayList<>(frames);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        all.addAll(scanFrames(ow, c[0] + dx, c[1] + dz));
                    }
                }
                List<Block> ring = joinNearby(all, frames);
                if (ring.size() >= 8) {
                    scanQueue.clear();
                    plugin.endPortal.adoptFrames(ring);
                    return;
                }
            }
        }
    }

    /** Соединяем рамки, стоящие вплотную друг к другу (один портал). */
    private List<Block> joinNearby(List<Block> all, List<Block> seed) {
        List<Block> accepted = new ArrayList<>(seed);
        List<Block> pool = new ArrayList<>(all);
        boolean grew = true;
        while (grew) {
            grew = false;
            Iterator<Block> it = pool.iterator();
            while (it.hasNext()) {
                Block b = it.next();
                if (accepted.contains(b)) {
                    it.remove();
                    continue;
                }
                for (Block a : accepted) {
                    if (Math.abs(a.getX() - b.getX()) > 2
                            || Math.abs(a.getY() - b.getY()) > 2
                            || Math.abs(a.getZ() - b.getZ()) > 2) {
                        continue;
                    }
                    accepted.add(b);
                    it.remove();
                    grew = true;
                    break;
                }
            }
        }
        return accepted;
    }

    private boolean isStrongholdChunk(World w, int cx, int cz) {
        try {
            Structure s = Registry.STRUCTURE.get(NamespacedKey.minecraft("stronghold"));
            if (s == null) {
                return true;
            }
            return !w.getStructures(cx, cz, s).isEmpty();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Ищет блоки рамок портала в чанке (по снимку, без нагрузки на основной поток). */
    private List<Block> scanFrames(World w, int cx, int cz) {
        List<Block> out = new ArrayList<>();
        try {
            Chunk chunk = w.getChunkAt(cx, cz);
            if (!chunk.isLoaded()) {
                chunk.load();
            }
            ChunkSnapshot snap = chunk.getChunkSnapshot(true, false, false);
            int yMax = Math.min(w.getMaxHeight() - 1, cfg.scanYMax);
            int yMin = Math.max(w.getMinHeight(), cfg.scanYMin);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        if (snap.getBlockType(x, y, z) == Material.END_PORTAL_FRAME) {
                            out.add(w.getBlockAt((cx << 4) + x, y, (cz << 4) + z));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Ошибка сканирования чанка " + cx + "," + cz + ": " + t.getMessage());
        }
        return out;
    }

    /** Координата крепости (куда ведут ока). */
    public Location strongholdLocation() {
        World w = Bukkit.getWorld(state.strongholdWorld);
        if (w == null) {
            w = plugin.overworld();
        }
        if (w == null || !state.strongholdChosen) {
            return null;
        }
        return new Location(w, state.strongholdX, state.strongholdY, state.strongholdZ);
    }

    // ================================================================== ока
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEyeThrow(PlayerInteractEvent e) {
        if (!cfg.enabled) {
            return;
        }
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.ENDER_EYE) {
            return;
        }
        Block clicked = e.getClickedBlock();
        if (clicked != null && clicked.getType() == Material.END_PORTAL_FRAME) {
            return; // клик по рамке обрабатывает EndPortal
        }
        Player p = e.getPlayer();
        if (plugin.isBot(p)) {
            return;
        }
        e.setCancelled(true);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        long now = System.currentTimeMillis();
        Long last = eyeCooldown.get(p.getUniqueId());
        if (last != null && now - last < cfg.eyeCooldownMs) {
            return;
        }
        eyeCooldown.put(p.getUniqueId(), now);

        Location target = strongholdLocation();
        if (target == null) {
            Msg.action(p, "Крепость ещё не найдена — попробуйте позже или /xmh end locate");
            return;
        }

        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        Location eyeLoc = p.getEyeLocation().clone();
        EnderSignal signal;
        try {
            signal = p.getWorld().spawn(eyeLoc, EnderSignal.class);
        } catch (Throwable t) {
            return;
        }
        signal.setTargetLocation(target);
        signal.setDropItem(false);
        signal.setItem(new ItemStack(Material.ENDER_EYE));

        boolean breaks = rnd.nextDouble() < cfg.eyeBreakChance;
        if (breaks) {
            int ticks = Math.max(5, cfg.eyeFlightTicks);
            signal.setDespawnTimer(ticks);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (signal.isValid()) {
                    Location loc = signal.getLocation().clone();
                    signal.remove();
                    breakEffect(p, loc);
                }
            }, ticks);
        } else {
            signal.setDespawnTimer(100);
        }
    }

    /** Визуальная молния + лёгкий урон рядом + свечение игроку. */
    private void breakEffect(Player p, Location loc) {
        World w = loc.getWorld();
        if (w == null) {
            return;
        }
        w.strikeLightningEffect(loc);
        for (Entity e : w.getNearbyEntities(loc, cfg.eyeLightningRadius, cfg.eyeLightningRadius,
                cfg.eyeLightningRadius)) {
            if (e instanceof LivingEntity le && !(e instanceof Player)) {
                le.damage(cfg.eyeLightningDamage, p);
            } else if (e instanceof Player other && !other.equals(p)) {
                other.damage(cfg.eyeLightningDamage * 0.5, p);
            }
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                cfg.eyeGlowSeconds * 20, 0, true, false, true));
        if (cfg.eyeBreakSound != null && !cfg.eyeBreakSound.isBlank()) {
            w.playSound(loc, cfg.eyeBreakSound, cfg.eyeBreakSoundVolume, cfg.eyeBreakSoundPitch);
        }
        Msg.action(p, "Око разбилось! Оно указало направление к крепости...");
    }

    /** /locate structure stronghold ведёт только к выбранной крепости. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLocateCommand(PlayerCommandPreprocessEvent e) {
        if (!cfg.enabled || !cfg.strongholdEnabled || !state.strongholdChosen) {
            return;
        }
        String msg = e.getMessage().toLowerCase(Locale.ROOT).trim();
        if (!msg.startsWith("/locate")) {
            return;
        }
        if (!msg.contains("stronghold") && !msg.contains("крепост")) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        Location l = strongholdLocation();
        if (l == null) {
            Msg.chat(p, "Крепость ещё не найдена.", NamedTextColor.RED);
            return;
        }
        Msg.chat(p, Msg.c("Ближайшая крепость: ", NamedTextColor.GRAY)
                .append(Msg.bold(l.getBlockX() + ", " + l.getBlockZ(), NamedTextColor.AQUA))
                .append(Msg.c(" (расстояние ~" + (int) Math.hypot(
                        l.getX() - p.getLocation().getX(),
                        l.getZ() - p.getLocation().getZ()) + " блоков)", NamedTextColor.GRAY)));
    }

    // ================================================================== порталы чужих крепостей
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!cfg.enabled || !cfg.strongholdEnabled || !cfg.disableOtherPortals
                || !state.strongholdChosen) {
            return;
        }
        Chunk chunk = e.getChunk();
        World w = chunk.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        String ck = w.getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        if (state.disabledChunks.contains(ck)) {
            return;
        }
        for (EndState.Frame f : state.frames.values()) {
            if (f.world.equals(w.getName())
                    && (f.x >> 4) == chunk.getX() && (f.z >> 4) == chunk.getZ()) {
                return; // это наш портал
            }
        }
        // Чанк выбранной крепости не трогаем
        if (Math.abs(chunk.getX() - (state.strongholdX >> 4)) <= cfg.scanRadiusChunks
                && Math.abs(chunk.getZ() - (state.strongholdZ >> 4)) <= cfg.scanRadiusChunks) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> disableOtherPortal(chunk, ck), 2L);
    }

    private void disableOtherPortal(Chunk chunk, String ck) {
        World w = chunk.getWorld();
        if (!isStrongholdChunk(w, chunk.getX(), chunk.getZ())) {
            return;
        }
        // чанк с нашими рамками не трогаем
        for (EndState.Frame f : state.frames.values()) {
            if (f.world.equals(w.getName())
                    && (f.x >> 4) == chunk.getX() && (f.z >> 4) == chunk.getZ()) {
                return;
            }
        }
        List<Block> frames = scanFrames(w, chunk.getX(), chunk.getZ());
        boolean changed = false;
        for (Block b : frames) {
            if (b.getBlockData() instanceof EndPortalFrame f && f.hasEye()) {
                f.setEye(false);
                b.setBlockData(f, false);
                changed = true;
            }
        }
        if (!frames.isEmpty()) {
            // Выбиваем одну рамку — портал в этой крепости больше не собрать
            frames.sort(Comparator.comparingInt(Block::getX)
                    .thenComparingInt(Block::getZ).thenComparingInt(Block::getY));
            frames.get(0).setType(Material.AIR, false);
            changed = true;
        }
        // Гасим уже существующий портал, если он был активен
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = Math.max(w.getMinHeight(), cfg.scanYMin);
                     y <= Math.min(w.getMaxHeight() - 1, cfg.scanYMax); y++) {
                    Block b = w.getBlockAt((chunk.getX() << 4) + x, y, (chunk.getZ() << 4) + z);
                    if (b.getType() == Material.END_PORTAL) {
                        b.setType(Material.AIR, false);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            state.disabledChunks.add(ck);
            state.save();
            plugin.getLogger().info("Портал в чужой крепости отключён (чанк " + ck + ").");
        }
    }

    // ================================================================== жемчуг
    @EventHandler(priority = EventPriority.HIGH)
    public void onEndermanDeath(EntityDeathEvent e) {
        if (!cfg.enabled || e.getEntityType() != EntityType.ENDERMAN) {
            return;
        }
        Player killer = e.getEntity().getKiller();
        int looting = 0;
        if (killer != null) {
            looting = killer.getInventory().getItemInMainHand()
                    .getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOTING);
        }
        // Убираем ванильный жемчуг и выдаём свой, с шансом (без дюпов)
        e.getDrops().removeIf(st -> st != null && st.getType() == Material.ENDER_PEARL);
        double chance = cfg.pearlChance;
        if (looting > 0) {
            // Looting I..III линейно: база -> значение при Looting III
            double step = Math.max(0.0, cfg.pearlChanceLooting - cfg.pearlChance) / 3.0;
            chance = Math.min(1.0, cfg.pearlChance + step * Math.min(3, looting));
        }
        if (rnd.nextDouble() < chance) {
            e.getDrops().add(new ItemStack(Material.ENDER_PEARL, 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPiglinBarter(PiglinBarterEvent e) {
        if (!cfg.enabled || !cfg.removePearlFromBarter) {
            return;
        }
        e.getOutcome().removeIf(st -> st != null && st.getType() == Material.ENDER_PEARL);
    }

    // ================================================================== спавнеры блейзов
    private void refreshBlazeSpawners() {
        if (!cfg.enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player p : plugin.players()) {
            World w = p.getWorld();
            int ccx = p.getLocation().getBlockX() >> 4;
            int ccz = p.getLocation().getBlockZ() >> 4;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (!w.isChunkLoaded(ccx + dx, ccz + dz)) {
                        continue; // не грузим чанки ради проверки спавнеров
                    }
                    Chunk chunk = w.getChunkAt(ccx + dx, ccz + dz);
                    for (BlockState st : chunk.getTileEntities()) {
                        if (st instanceof CreatureSpawner cs
                                && "minecraft:blaze".equals(cs.getCreatureTypeName())) {
                            String key = st.getWorld().getName() + ":" + st.getX() + ":"
                                    + st.getY() + ":" + st.getZ();
                            blazeSpawners.put(key, now);
                        }
                    }
                }
            }
        }
        // Забываем спавнеры, которые давно не видели
        blazeSpawners.entrySet().removeIf(en -> now - en.getValue() > 60_000L);
        spawnGhasts(now);
    }

    private void spawnGhasts(long now) {
        if (cfg.ghastChance <= 0) {
            return;
        }
        for (Map.Entry<String, Long> en : new ArrayList<>(blazeSpawners.entrySet())) {
            String key = en.getKey();
            String[] p = key.split(":");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) {
                continue;
            }
            Location loc = new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    Integer.parseInt(p[3]));
            boolean nearPlayer = false;
            for (Player pl : plugin.players()) {
                if (pl.getWorld().equals(w) && pl.getLocation().distanceSquared(loc) < 96 * 96) {
                    nearPlayer = true;
                    break;
                }
            }
            if (!nearPlayer) {
                continue;
            }
            Long cd = ghastCooldown.get(key);
            if (cd != null && now - cd < 45_000L) {
                continue;
            }
            if (rnd.nextDouble() > cfg.ghastChance) {
                continue;
            }
            ghastCooldown.put(key, now);
            int count = 1 + rnd.nextInt(Math.max(1, cfg.ghastMax));
            for (int i = 0; i < count; i++) {
                Location sp = findAirNear(w, loc, cfg.ghastRadius);
                if (sp == null) {
                    continue;
                }
                Entity g = w.spawnEntity(sp, EntityType.GHAST);
                g.addScoreboardTag("xh_scaled");
                w.strikeLightningEffect(sp);
            }
        }
    }

    private Location findAirNear(World w, Location center, double radius) {
        for (int i = 0; i < 20; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = 6 + rnd.nextDouble() * radius;
            int x = center.getBlockX() + (int) Math.round(Math.cos(a) * d);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(a) * d);
            int y = Math.min(w.getMaxHeight() - 4, center.getBlockY() + 8 + rnd.nextInt(14));
            Location l = new Location(w, x + 0.5, y, z + 0.5);
            if (l.getBlock().isEmpty() && l.clone().add(0, 1, 0).getBlock().isEmpty()) {
                return l;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!cfg.enabled || cfg.blazeNoBuildRadius <= 0) {
            return;
        }
        if (e.getPlayer().getWorld().getEnvironment() != World.Environment.NETHER) {
            return;
        }
        Location placed = e.getBlockPlaced().getLocation();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> en : blazeSpawners.entrySet()) {
            if (now - en.getValue() > 60_000L) {
                continue;
            }
            String[] p = en.getKey().split(":");
            World w = Bukkit.getWorld(p[0]);
            if (w == null || !w.equals(placed.getWorld())) {
                continue;
            }
            Location loc = new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    Integer.parseInt(p[3]));
            if (loc.distanceSquared(placed) <= cfg.blazeNoBuildRadius * cfg.blazeNoBuildRadius) {
                e.setCancelled(true);
                Msg.action(e.getPlayer(), "Рядом со спавнером нельзя ставить блоки!");
                e.getPlayer().playSound(Msg.villagerNo());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent e) {
        if (!cfg.enabled || !cfg.protectExplosions) {
            return;
        }
        EntityType t = e.getEntity().getType();
        if (t != EntityType.GHAST && t != EntityType.FIREBALL && t != EntityType.SMALL_FIREBALL) {
            return;
        }
        e.blockList().removeIf(b -> {
            String n = b.getType().name();
            return b.getType() == Material.SPAWNER
                    || n.startsWith("NETHER_BRICK")
                    || b.getType() == Material.IRON_BARS;
        });
    }

    // ================================================================== внешние острова
    @EventHandler(priority = EventPriority.HIGH)
    public void onPortal(EntityPortalEvent e) {
        if (!cfg.enabled) {
            return;
        }
        if (e.getEntity().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        if (plugin.currentDay >= cfg.outerUnlockDay) {
            return;
        }
        // End Gateway (внешние острова) до 80-го дня закрыт
        if (e.getPortalType() == org.bukkit.PortalType.END_GATEWAY) {
            e.setCancelled(true);
            if (e.getEntity() instanceof Player p) {
                Msg.action(p, "Внешние острова Энда откроются на " + cfg.outerUnlockDay + "-й день!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!cfg.enabled || plugin.currentDay >= cfg.outerUnlockDay) {
            return;
        }
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return;
        }
        Location to = e.getTo();
        if (to == null || to.getWorld() == null
                || to.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        e.setCancelled(true);
        Msg.action(e.getPlayer(), "Внешние острова Энда откроются на "
                + cfg.outerUnlockDay + "-й день!");
    }

    /**
     * Левитация (в т.ч. от спасения из пустоты) выдаёт ачивку
     * «Great View From Up Here» (minecraft:end/levitate) и опыт — снимаем её
     * и возвращаем накопленный опыт.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancementDone(PlayerAdvancementDoneEvent e) {
        if (!cfg.enabled || !cfg.cancelLevitationAdvancement) {
            return;
        }
        NamespacedKey key = e.getAdvancement().getKey();
        if (!key.equals(NamespacedKey.minecraft("end/levitate"))) {
            return;
        }
        Player p = e.getPlayer();
        // Ваниль может выдавать ачивку заново, пока висит левитация.
        // Снимаем молча (без broadcast в чат) и не чаще раза в 5 секунд.
        if (!revokeLevitationAdvancement(p)) {
            return;
        }
        int back = cfg.levitationAdvancementXp;
        if (back > 0) {
            try {
                p.giveExp(-back);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private final Map<UUID, Long> lastLevAdvRevoke = new HashMap<>();

    /** Снимает ачивку «Great View From Up Here» без сообщений в общий чат. */
    private boolean revokeLevitationAdvancement(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastLevAdvRevoke.get(p.getUniqueId());
        if (last != null && now - last < 5000L) {
            return false;
        }
        lastLevAdvRevoke.put(p.getUniqueId(), now);
        Advancement adv = Bukkit.getAdvancement(NamespacedKey.minecraft("end/levitate"));
        if (adv != null) {
            AdvancementProgress prog = p.getAdvancementProgress(adv);
            for (String crit : new ArrayList<>(prog.getAwardedCriteria())) {
                try {
                    prog.revokeCriteria(crit);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return true;
    }

    // ================================================================== тики
    private void tickHalfSecond() {
        if (!cfg.enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player p : plugin.players()) {
            tickOuterIslands(p);
            stripVoidLevitation(p);
            tickVoid(p);
            tickCurseAndBan(p, now);
        }
    }

    private void tickOuterIslands(Player p) {
        if (plugin.currentDay >= cfg.outerUnlockDay) {
            return;
        }
        Location l = p.getLocation();
        if (l.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        double dist = Math.hypot(l.getX(), l.getZ());
        if (dist <= cfg.outerMaxDistance) {
            return;
        }
        double scale = (cfg.outerMaxDistance * 0.85) / dist;
        Location back = new Location(l.getWorld(), l.getX() * scale, l.getY() + 1, l.getZ() * scale);
        int y = l.getWorld().getHighestBlockYAt(back.getBlockX(), back.getBlockZ()) + 1;
        back.setY(Math.max(y, l.getWorld().getMinHeight() + 2));
        p.teleport(back);
        Msg.action(p, "Внешние острова Энда откроются на " + cfg.outerUnlockDay + "-й день!");
    }

    /**
     * Снимает обычную левитацию (уровни 1..N) у игрока в пустоте Энда
     * ниже заданной высоты. Левитацию от спасения плагина не трогает.
     */
    private void stripVoidLevitation(Player p) {
        if (!cfg.enabled || !cfg.voidStripLevitation) {
            return;
        }
        Location l = p.getLocation();
        if (l.getWorld().getEnvironment() != World.Environment.THE_END
                || l.getY() >= cfg.voidStripLevitationMaxY) {
            return;
        }
        if (voidRescue.contains(p.getUniqueId())) {
            return; // своя левитация спасения из пустоты
        }
        PotionEffect lev = p.getPotionEffect(PotionEffectType.LEVITATION);
        if (lev == null) {
            return;
        }
        if (lev.getAmplifier() >= cfg.voidStripLevitationMaxLevel) {
            return; // выше разрешённого уровня — не наша забота
        }
        p.removePotionEffect(PotionEffectType.LEVITATION);
        Msg.action(p, "Левитация не действует в пустоте Энда.");
    }

    private void tickVoid(Player p) {
        Location l = p.getLocation();
        if (l.getWorld().getEnvironment() != World.Environment.THE_END
                || l.getY() >= cfg.voidMinY) {
            voidRescue.remove(p.getUniqueId());
            return;
        }
        if (voidRescue.contains(p.getUniqueId())) {
            return;
        }
        int pearls = countPearls(p);
        if (pearls < cfg.voidPearls) {
            return;
        }
        voidRescue.add(p.getUniqueId());
        takePearls(p, cfg.voidPearls);

        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
                cfg.voidLevitationSeconds * 20, cfg.voidLevitationAmplifier, true, false, true));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                    cfg.voidSlowFallingSeconds * 20, cfg.voidSlowFallingAmplifier, true, false, true));
        }, cfg.voidLevitationSeconds * 20L);

        int hearts = 1 + rnd.nextInt(0);
        addBonusHearts(p, hearts);
        p.playSound(Msg.click());
        Msg.chat(p, Msg.c("Пустота отпустила вас! ", NamedTextColor.LIGHT_PURPLE)
                .append(Msg.c("Списано " + cfg.voidPearls + " жемчуга, +", NamedTextColor.GRAY))
                .append(Msg.bold(hearts + " сердца", NamedTextColor.RED)));
    }

    private void tickCurseAndBan(Player p, long now) {
        Long curse = state.cursedUntil.get(p.getUniqueId());
        if (curse != null) {
            if (curse <= now) {
                state.cursedUntil.remove(p.getUniqueId());
                state.save();
                Msg.chat(p, Msg.c("Проклятие портала спало.", NamedTextColor.GREEN));
            } else {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 1, true, false, true));
            }
        }
        Long ban = state.endBannedUntil.get(p.getUniqueId());
        if (ban != null) {
            if (ban <= now) {
                state.endBannedUntil.remove(p.getUniqueId());
                state.save();
                Msg.chat(p, Msg.c("Запрет входа в Энд снят.", NamedTextColor.GREEN));
            } else if (p.getWorld().getEnvironment() == World.Environment.THE_END) {
                World ow = plugin.overworld();
                if (ow != null) {
                    p.teleport(ow.getSpawnLocation());
                    Msg.chat(p, Msg.c("Вам запрещён вход в Энд до "
                            + new java.text.SimpleDateFormat("HH:mm dd.MM")
                            .format(new java.util.Date(ban)) + ".", NamedTextColor.RED));
                }
            }
        }
    }

    /** Баффы портала: ночное зрение на 15 сек, перевыдача каждые 4 сек. */
    private void tickPortalBuffs() {
        if (!cfg.enabled || endPortal() == null) {
            return;
        }
        for (Player p : plugin.players()) {
            applyPortalBuffs(p);
        }
    }

    private EndPortal endPortal() {
        return plugin.endPortal;
    }

    /**
     * Раз в 5 секунд: если хотя бы один игрок в кубе ±100 по X/Y/Z от портала,
     * пересканируем и чиним рамки (кнопка /xmh end scan в автоматическом режиме).
     */
    private void tickPortalArea() {
        if (!cfg.enabled) {
            return;
        }
        Location c = strongholdLocation();
        if (c == null || plugin.endPortal == null) {
            return;
        }
        boolean near = false;
        for (Player p : plugin.players()) {
            if (!p.getWorld().equals(c.getWorld())) {
                continue;
            }
            Location l = p.getLocation();
            if (Math.abs(l.getX() - c.getX()) <= 100.0
                    && Math.abs(l.getY() - c.getY()) <= 100.0
                    && Math.abs(l.getZ() - c.getZ()) <= 100.0) {
                near = true;
                break;
            }
        }
        if (!near) {
            return;
        }
        // Починка выбитых рамок
        plugin.endPortal.repair();
        // Неполный набор — пересканировать (может, нашли не все чанки)
        if (state.frames.size() < cfg.portalFrames) {
            plugin.endPortal.forceScan();
        }
    }

    /** Раз в 10 сек проверяем крепости в радиусе 100 блоков от игроков. */
    private void tickNearbyPortals() {
        if (!cfg.enabled || !cfg.strongholdEnabled || !cfg.disableOtherPortals
                || !state.strongholdChosen) {
            return;
        }
        int r = Math.max(1, 100 >> 4);
        for (Player p : plugin.players()) {
            World w = p.getWorld();
            if (w.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            int pcx = p.getLocation().getBlockX() >> 4;
            int pcz = p.getLocation().getBlockZ() >> 4;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    String ck = w.getName() + ":" + cx + ":" + cz;
                    if (state.disabledChunks.contains(ck)) {
                        continue;
                    }
                    if (Math.abs(cx - (state.strongholdX >> 4)) <= cfg.scanRadiusChunks
                            && Math.abs(cz - (state.strongholdZ >> 4)) <= cfg.scanRadiusChunks) {
                        continue; // наша крепость
                    }
                    disableOtherPortal(w.getChunkAt(cx, cz), ck);
                }
            }
        }
    }

    private void tickSlow() {
        for (Player p : plugin.players()) {
            applyBonusHearts(p);
            applyPortalBuffs(p);
        }
    }

    // ================================================================== проклятие/запрет
    public void curse(Player p, String reason) {
        long until = System.currentTimeMillis() + cfg.curseHours * 3_600_000L;
        state.cursedUntil.put(p.getUniqueId(), until);
        state.endBannedUntil.put(p.getUniqueId(), until);
        state.save();
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 1, true, false, true));
        Msg.chat(p, Msg.c(reason + " Проклятие на " + cfg.curseHours + " ч и запрет входа в Энд.",
                NamedTextColor.DARK_RED));
    }

    public boolean isCursed(Player p) {
        Long t = state.cursedUntil.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    // ================================================================== сердца / баффы
    public void addBonusHearts(Player p, int hearts) {
        int cur = state.bonusHearts.getOrDefault(p.getUniqueId(), 0);
        state.bonusHearts.put(p.getUniqueId(), cur + hearts);
        state.save();
        applyBonusHearts(p);
    }

    public void applyBonusHearts(Player p) {
        Integer hearts = state.bonusHearts.get(p.getUniqueId());
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) {
            return;
        }
        // hearts может быть отрицательным (пустота отнимает сердца)
        double want = (hearts == null ? 0 : hearts) * 2.0;
        // не опускаем макс. здоровье ниже 2 HP (база 20)
        want = Math.max(-18.0, want);
        for (AttributeModifier m : attr.getModifiers()) {
            if (m.getKey() != null && m.getKey().equals(heartsKey)) {
                if (Math.abs(m.getAmount() - want) < 0.001) {
                    return; // уже правильное значение — не трогаем
                }
                attr.removeModifier(m);
                break;
            }
        }
        if (want != 0) {
            attr.addModifier(new AttributeModifier(heartsKey, want,
                    AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /** Бафф на 24 ч реального времени (суммируется: две Спешки = Спешка II). */
    public void addTimedBuff(Player p, String effect, int amplifier) {
        Map<String, long[]> map = state.buffs.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        long until = System.currentTimeMillis() + cfg.buffHours * 3_600_000L;
        long[] cur = map.get(effect);
        int amp = amplifier;
        if (cur != null && cur[1] > System.currentTimeMillis()) {
            amp = (int) Math.min(4, cur[0] + amplifier);
        }
        map.put(effect, new long[]{amp, until});
        state.save();
        applyPortalBuffs(p);
    }

    /** Бафф за починку рамки: длительность в РЕАЛЬНЫХ днях. */
    public void addTimedBuffForDays(Player p, String effect, int amplifier, int days) {
        Map<String, long[]> map = state.buffs.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        long until = System.currentTimeMillis() + Math.max(1, days) * 86_400_000L;
        long[] cur = map.get(effect);
        int amp = amplifier;
        if (cur != null && cur[1] > System.currentTimeMillis()) {
            amp = (int) Math.min(6, Math.max(cur[0], amplifier));
        }
        map.put(effect, new long[]{amp, until});
        state.save();
        applyPortalBuffs(p);
    }

    /**
     * Забирает у игрока постоянный бафф (например, за починку рамки).
     * Возвращает {amplifier, until} или null, если баффа нет.
     */
    public long[] takeBuff(Player p, String effect) {
        Map<String, long[]> map = state.buffs.get(p.getUniqueId());
        if (map == null) {
            return null;
        }
        long[] cur = map.remove(effect);
        if (map.isEmpty()) {
            state.buffs.remove(p.getUniqueId());
        }
        if (cur != null) {
            state.save();
        }
        return cur;
    }

    /** Выдаёт бафф с готовым сроком (перенос эффекта между игроками). */
    public void giveBuff(Player p, String effect, long[] raw) {
        if (raw == null || raw[1] <= System.currentTimeMillis()) {
            return;
        }
        state.buffs.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(effect, raw);
        state.save();
        applyPortalBuffs(p);
    }

    private void applyPortalBuffs(Player p) {
        Map<String, long[]> map = state.buffs.get(p.getUniqueId());
        if (map == null || map.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, long[]>> it = map.entrySet().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Map.Entry<String, long[]> e = it.next();
            if (e.getValue()[1] <= now) {
                it.remove();
                changed = true;
                continue;
            }
            PotionEffectType type = org.bukkit.Registry.EFFECT.get(
                    NamespacedKey.fromString(e.getKey()));
            if (type == null) {
                it.remove();
                changed = true;
                continue;
            }
            int amp = (int) Math.max(0, Math.min(4, e.getValue()[0]));
            int duration = "night_vision".equals(e.getKey()) ? 300 : 200;
            p.addPotionEffect(new PotionEffect(type, duration, amp, true, false, true));
        }
        if (changed) {
            state.save();
        }
    }

    // ================================================================== яйцо дракона
    public void applyEggBuffs(Player p) {
        if (state.eggKeeper == null || !state.eggKeeper.equals(p.getUniqueId())) {
            return;
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 120,
                Math.max(0, cfg.eggHasteAmplifier), true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120,
                Math.max(0, cfg.eggSpeedAmplifier), true, false, true));
    }

    public boolean activateEgg(Player p) {
        if (!containsItem(p, Material.DRAGON_EGG)) {
            Msg.chat(p, "Нужно держать яйцо дракона в инвентаре.", NamedTextColor.RED);
            return false;
        }
        UUID prev = state.eggKeeper;
        state.eggKeeper = p.getUniqueId();
        state.save();
        Msg.broadcast("Хранитель яйца дракона: " + p.getName()
                + ". Бесконечные Спешка II и Скорость I.", NamedTextColor.LIGHT_PURPLE);
        if (prev != null && !prev.equals(p.getUniqueId())) {
            Player old = Bukkit.getPlayer(prev);
            if (old != null) {
                Msg.chat(old, "Права хранителя яйца дракона перешли к " + p.getName() + ".",
                        NamedTextColor.GRAY);
            }
        }
        return true;
    }

    // ================================================================== утилиты
    private static boolean containsItem(Player p, Material m) {
        for (ItemStack st : p.getInventory().getContents()) {
            if (st != null && st.getType() == m) {
                return true;
            }
        }
        return false;
    }

    private static int countPearls(Player p) {
        int n = 0;
        for (ItemStack st : p.getInventory().getContents()) {
            if (st != null && st.getType() == Material.ENDER_PEARL) {
                n += st.getAmount();
            }
        }
        return n;
    }

    private static void takePearls(Player p, int count) {
        int left = count;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack st = contents[i];
            if (st == null || st.getType() != Material.ENDER_PEARL) {
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

    // ================================================================== дракон / щит
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonHit(EntityDamageByEntityEvent e) {
        if (!cfg.enabled || !(e.getEntity() instanceof Player p)) {
            return;
        }
        if (!(e.getDamager() instanceof EnderDragon)) {
            return;
        }
        if (!p.isBlocking()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastDragonHit.get(p.getUniqueId());
        if (last != null && now - last < cfg.dragonShieldCooldownMs) {
            // Не тратим прочность щита чаще, чем раз в cooldown
            e.setCancelled(true);
            return;
        }
        lastDragonHit.put(p.getUniqueId(), now);
    }

    /** Спавнер в комнате портала нельзя ломать. */
    public boolean isPortalRoomSpawner(Block b) {
        if (b.getType() != Material.SPAWNER || state.frames.isEmpty()) {
            return false;
        }
        for (EndState.Frame f : state.frames.values()) {
            if (f.world.equals(b.getWorld().getName())
                    && Math.abs(f.x - b.getX()) <= 16
                    && Math.abs(f.z - b.getZ()) <= 16
                    && Math.abs(f.y - b.getY()) <= 16) {
                return true;
            }
        }
        return false;
    }
}
