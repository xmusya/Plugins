package ru.xmusya.hard;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Настройки «энд-механик» (секция end: в config.yml).
 * Если секции нет — берутся значения по умолчанию, сервер продолжает работать.
 */
public final class EndCfg {

    /** Предмет из пула, который нужно принести к рамке портала. */
    public static final class PoolItem {
        public Material material = Material.IRON_INGOT;
        public String name = "Предмет";
        public int amount = 1;
        public int maxPerPortal = 1;
    }

    /** Вариант головы для ультра-набора (weight — относительный шанс). */
    public static final class HeadCfg {
        public Material material = Material.ZOMBIE_HEAD;
        public String name = "Голова зомби";
        public int weight = 1;
    }

    /** Бафф за починку рамки. */
    public static final class BuffCfg {
        public String effect = "resistance";
        public int amplifier = 1;
    }

    public boolean enabled = true;

    // --- крепость
    /** Включена ли механика «одна крепость + ломаные порталы в остальных». */
    public boolean strongholdEnabled = true;
    public int minRadius = 0;
    public int maxRadius = 8000;
    /** Радиус поиска комнаты портала вокруг найденной крепости (в чанках). */
    public int scanRadiusChunks = 8;
    public int scanYMin = -64;
    public int scanYMax = 128;
    public int scanChunksPerTick = 8;
    /** На время первого поиска крепости барьер временно расширяется до этого размера. */
    public int searchBorderSize = 50000;
    /** Ломать порталы во всех остальных крепостях (только при stronghold.enabled). */
    public boolean disableOtherPortals = true;

    // --- ока
    public double eyeBreakChance = 0.20;
    public int eyeFlightTicks = 30;
    public double eyeLightningDamage = 4.0;
    public double eyeLightningRadius = 4.0;
    public int eyeGlowSeconds = 10;
    /** Звук при поломке ока (пусто = без звука). */
    public String eyeBreakSound = "entity.lightning_bolt.thunder";
    public float eyeBreakSoundVolume = 1.0f;
    public float eyeBreakSoundPitch = 1.0f;
    public long eyeCooldownMs = 400L;

    // --- жемчуг
    public double pearlChance = 0.10;
    public double pearlChanceLooting = 0.20;
    public boolean removePearlFromBarter = true;

    // --- спавнеры блейзов
    public double blazeNoBuildRadius = 12.0;
    public boolean protectExplosions = true;
    public double ghastChance = 0.15;
    public int ghastMax = 2;
    public double ghastRadius = 24.0;

    // --- портал
    public int portalFrames = 12;
    public int buffHours = 24;
    public int heartsPerFrames = 2;
    public final List<PoolItem> pool = new ArrayList<>();

    // --- цикл сброса
    public int cycleStartDay = 100;
    public int cycleIntervalDays = 50;
    public int cycleResetMin = 1;
    public int cycleResetMax = 4;

    // --- защита портала
    public int curseHours = 2;
    public int endBanHours = 2;
    /** Эффекты (лечение/проклятие/молния) при попытке сломать рамку/спавнер.
     *  По умолчанию ВЫКЛЮЧЕНО — рамка просто не ломается. */
    public boolean breakEffects = false;

    // --- обычный и ультра слом рамок (авто-ивенты)
    public boolean normalBreakEnabled = true;
    public int normalBreakStartDay = 100;
    public int normalBreakInterval = 80;
    public int normalBreakFrames = 4;
    public double normalBreakChance = 0.40;
    public boolean ultraBreakEnabled = true;
    public int ultraBreakStartDay = 100;
    public int ultraBreakInterval = 150;
    public int ultraBreakFrames = 1;
    public double ultraBreakChance = 0.40;

    // --- наборы для сложной починки
    public final List<PoolItem> normalRepair = new ArrayList<>();
    public final List<PoolItem> ultraRepair = new ArrayList<>();
    public final List<HeadCfg> ultraHeads = new ArrayList<>();
    /** Сколько РЕАЛЬНЫХ дней длится бафф за починку рамки. */
    public int repairBuffDays = 3;
    public final List<BuffCfg> repairBuffs = new ArrayList<>();

    // --- внешние острова
    public int outerUnlockDay = 80;
    public double outerMaxDistance = 450.0;

    // --- дракон
    public long dragonShieldCooldownMs = 1800L;

    // --- пустота
    public int voidMinY = 0;
    public int voidPearls = 2;
    public int voidLevitationSeconds = 10;
    /** Уровень Левитации: 14 = Левитация XV (amplifier = уровень − 1). */
    public int voidLevitationAmplifier = 14;
    /** Сердца при спасении из пустоты: + даёт, − отнимает, 0 = не трогать. */
    public int voidHearts = -1;
    /** Снимать ачивку «Great View From Up Here» (minecraft:end/levitate). */
    public boolean cancelLevitationAdvancement = true;
    /** Сколько опыта возвращать за снятую ачивку (0 = не возвращать). */
    public int levitationAdvancementXp = 50;
    /** Снимать обычную левитацию (уровни 1..N) в пустоте Энда ниже max-y. */
    public boolean voidStripLevitation = true;
    /** Максимальный УРОВЕНЬ левитации, который снимаем (1 = только «Левитация I»). */
    public int voidStripLevitationMaxLevel = 3;
    /** Высота: на этой и выше — левитацию не трогаем. */
    public int voidStripLevitationMaxY = 15;
    public int voidSlowFallingSeconds = 15;
    /** Уровень Плавного падения: 0 = Slow Falling I. */
    public int voidSlowFallingAmplifier = 0;

    // --- яйцо дракона
    public int eggHasteAmplifier = 1;
    public int eggSpeedAmplifier = 0;

    // --- дисплеи
    public int displayIntervalTicks = 2;
    public double displayViewRange = 24.0;

    public static EndCfg load(FileConfiguration c) {
        EndCfg e = new EndCfg();
        ConfigurationSection s = c.getConfigurationSection("end");
        if (s == null) {
            defaultPool(e);
            defaultNormalRepair(e);
            defaultUltraRepair(e);
            defaultHeads(e);
            defaultRepairBuffs(e);
            return e;
        }
        e.enabled = s.getBoolean("enabled", e.enabled);

        e.strongholdEnabled = s.getBoolean("stronghold.enabled", e.strongholdEnabled);
        e.minRadius = Math.max(0, s.getInt("stronghold.min-radius", e.minRadius));
        e.maxRadius = Math.max(e.minRadius + 500, s.getInt("stronghold.max-radius", e.maxRadius));
        e.scanRadiusChunks = s.getInt("stronghold.scan-radius-chunks", e.scanRadiusChunks);
        e.scanYMin = s.getInt("stronghold.scan-y-min", e.scanYMin);
        e.scanYMax = s.getInt("stronghold.scan-y-max", e.scanYMax);
        e.scanChunksPerTick = Math.max(1, s.getInt("stronghold.scan-chunks-per-tick", e.scanChunksPerTick));
        e.searchBorderSize = s.getInt("stronghold.search-border-size", e.searchBorderSize);
        e.disableOtherPortals = s.getBoolean("stronghold.disable-other-portals", e.disableOtherPortals);

        e.eyeBreakChance = s.getDouble("eyes.break-chance", e.eyeBreakChance);
        e.eyeFlightTicks = s.getInt("eyes.flight-ticks", e.eyeFlightTicks);
        e.eyeLightningDamage = s.getDouble("eyes.lightning-damage", e.eyeLightningDamage);
        e.eyeLightningRadius = s.getDouble("eyes.lightning-radius", e.eyeLightningRadius);
        e.eyeGlowSeconds = s.getInt("eyes.glow-seconds", e.eyeGlowSeconds);
        e.eyeBreakSound = s.getString("eyes.break-sound", e.eyeBreakSound);
        e.eyeBreakSoundVolume = (float) s.getDouble("eyes.break-sound-volume", e.eyeBreakSoundVolume);
        e.eyeBreakSoundPitch = (float) s.getDouble("eyes.break-sound-pitch", e.eyeBreakSoundPitch);
        e.eyeCooldownMs = s.getLong("eyes.cooldown-ms", e.eyeCooldownMs);

        e.pearlChance = s.getDouble("pearls.enderman-chance", e.pearlChance);
        e.pearlChanceLooting = s.getDouble("pearls.enderman-chance-looting", e.pearlChanceLooting);
        e.removePearlFromBarter = s.getBoolean("pearls.remove-from-barter", e.removePearlFromBarter);

        e.blazeNoBuildRadius = s.getDouble("blaze.no-build-radius", e.blazeNoBuildRadius);
        e.protectExplosions = s.getBoolean("blaze.protect-explosions", e.protectExplosions);
        e.ghastChance = s.getDouble("blaze.ghast-chance", e.ghastChance);
        e.ghastMax = s.getInt("blaze.ghast-max", e.ghastMax);
        e.ghastRadius = s.getDouble("blaze.ghast-radius", e.ghastRadius);

        e.portalFrames = s.getInt("portal.frames", e.portalFrames);
        e.buffHours = s.getInt("portal.buff-hours", e.buffHours);
        e.heartsPerFrames = Math.max(1, s.getInt("portal.hearts-per-frames", e.heartsPerFrames));
        e.displayIntervalTicks = Math.max(1, s.getInt("portal.display-interval-ticks", e.displayIntervalTicks));
        e.displayViewRange = s.getDouble("portal.display-view-range", e.displayViewRange);

        e.cycleStartDay = s.getInt("cycle.start-day", e.cycleStartDay);
        e.cycleIntervalDays = Math.max(1, s.getInt("cycle.interval-days", e.cycleIntervalDays));
        e.cycleResetMin = s.getInt("cycle.reset-min", e.cycleResetMin);
        e.cycleResetMax = s.getInt("cycle.reset-max", e.cycleResetMax);

        e.curseHours = s.getInt("protection.curse-hours", e.curseHours);
        e.endBanHours = s.getInt("protection.end-ban-hours", e.endBanHours);
        e.breakEffects = s.getBoolean("protection.break-effects", e.breakEffects);

        e.normalBreakEnabled = s.getBoolean("break.normal.enabled", e.normalBreakEnabled);
        e.normalBreakStartDay = s.getInt("break.normal.start-day", e.normalBreakStartDay);
        e.normalBreakInterval = Math.max(1, s.getInt("break.normal.interval-days", e.normalBreakInterval));
        e.normalBreakFrames = Math.max(1, s.getInt("break.normal.frames", e.normalBreakFrames));
        e.normalBreakChance = s.getDouble("break.normal.chance", e.normalBreakChance);
        e.ultraBreakEnabled = s.getBoolean("break.ultra.enabled", e.ultraBreakEnabled);
        e.ultraBreakStartDay = s.getInt("break.ultra.start-day", e.ultraBreakStartDay);
        e.ultraBreakInterval = Math.max(1, s.getInt("break.ultra.interval-days", e.ultraBreakInterval));
        e.ultraBreakFrames = Math.max(1, s.getInt("break.ultra.frames", e.ultraBreakFrames));
        e.ultraBreakChance = s.getDouble("break.ultra.chance", e.ultraBreakChance);

        e.repairBuffDays = Math.max(1, s.getInt("repair.buff.real-days", e.repairBuffDays));

        e.outerUnlockDay = s.getInt("outer-islands.unlock-day", e.outerUnlockDay);
        e.outerMaxDistance = s.getDouble("outer-islands.max-distance", e.outerMaxDistance);

        e.dragonShieldCooldownMs = s.getLong("dragon.shield-cooldown-ms", e.dragonShieldCooldownMs);

        e.voidMinY = s.getInt("void.min-y", e.voidMinY);
        e.voidPearls = s.getInt("void.pearls", e.voidPearls);
        e.voidLevitationSeconds = s.getInt("void.levitation-seconds", e.voidLevitationSeconds);
        e.voidLevitationAmplifier = Math.max(0, s.getInt("void.levitation-amplifier", e.voidLevitationAmplifier));
        e.voidHearts = s.getInt("void.hearts", e.voidHearts);
        e.cancelLevitationAdvancement = s.getBoolean("void.cancel-levitation-advancement", e.cancelLevitationAdvancement);
        e.levitationAdvancementXp = Math.max(0, s.getInt("void.levitation-advancement-xp", e.levitationAdvancementXp));
        e.voidStripLevitation = s.getBoolean("void.strip-levitation", e.voidStripLevitation);
        e.voidStripLevitationMaxLevel = Math.max(1, s.getInt("void.strip-levitation-max-level", e.voidStripLevitationMaxLevel));
        e.voidStripLevitationMaxY = s.getInt("void.strip-levitation-max-y", e.voidStripLevitationMaxY);
        e.voidSlowFallingSeconds = s.getInt("void.slow-falling-seconds", e.voidSlowFallingSeconds);
        e.voidSlowFallingAmplifier = s.getInt("void.slow-falling-amplifier", e.voidSlowFallingAmplifier);

        e.eggHasteAmplifier = s.getInt("egg.haste-amplifier", e.eggHasteAmplifier);
        e.eggSpeedAmplifier = s.getInt("egg.speed-amplifier", e.eggSpeedAmplifier);

        loadPool(e, s.getMapList("portal.items"));
        if (e.pool.isEmpty()) {
            defaultPool(e);
        }

        loadList(e.normalRepair, s.getMapList("repair.normal.items"));
        if (e.normalRepair.isEmpty()) {
            defaultNormalRepair(e);
        }
        loadList(e.ultraRepair, s.getMapList("repair.ultra.items"));
        if (e.ultraRepair.isEmpty()) {
            defaultUltraRepair(e);
        }
        for (java.util.Map<?, ?> raw : s.getMapList("repair.ultra.heads")) {
            Object mat = raw.get("material");
            if (mat == null) {
                continue;
            }
            Material m = Material.matchMaterial(String.valueOf(mat));
            if (m == null) {
                org.bukkit.Bukkit.getLogger().warning(
                        "[xmusya-hard] repair.ultra.heads: неизвестный материал " + mat);
                continue;
            }
            HeadCfg h = new HeadCfg();
            h.material = m;
            h.name = raw.get("name") == null ? prettify(m) : String.valueOf(raw.get("name"));
            Object w = raw.get("weight");
            h.weight = w instanceof Number n ? Math.max(1, n.intValue()) : 1;
            e.ultraHeads.add(h);
        }
        if (e.ultraHeads.isEmpty()) {
            defaultHeads(e);
        }
        for (java.util.Map<?, ?> raw : s.getMapList("repair.buff.effects")) {
            Object eff = raw.get("effect");
            if (eff == null) {
                continue;
            }
            BuffCfg b = new BuffCfg();
            b.effect = String.valueOf(eff).toLowerCase(java.util.Locale.ROOT)
                    .replace("minecraft:", "").trim();
            Object amp = raw.get("amplifier");
            b.amplifier = amp instanceof Number n ? Math.max(0, n.intValue()) : 1;
            e.repairBuffs.add(b);
        }
        if (e.repairBuffs.isEmpty()) {
            defaultRepairBuffs(e);
        }
        if (e.cycleResetMin > e.cycleResetMax) {
            int t = e.cycleResetMin;
            e.cycleResetMin = e.cycleResetMax;
            e.cycleResetMax = t;
        }
        return e;
    }

    private static void loadList(List<PoolItem> out, List<java.util.Map<?, ?>> list) {
        for (java.util.Map<?, ?> raw : list) {
            Object mat = raw.get("material");
            if (mat == null) {
                continue;
            }
            Material m = Material.matchMaterial(String.valueOf(mat));
            if (m == null) {
                org.bukkit.Bukkit.getLogger().warning(
                        "[xmusya-hard] repair: неизвестный материал " + mat);
                continue;
            }
            PoolItem pi = new PoolItem();
            pi.material = m;
            pi.name = raw.get("name") == null ? prettify(m) : String.valueOf(raw.get("name"));
            Object amt = raw.get("amount");
            pi.amount = amt instanceof Number n ? Math.max(1, n.intValue()) : 1;
            out.add(pi);
        }
    }

    private static void loadPool(EndCfg e, List<java.util.Map<?, ?>> list) {
        for (java.util.Map<?, ?> raw : list) {
            Object mat = raw.get("material");
            if (mat == null) {
                continue;
            }
            Material m = Material.matchMaterial(String.valueOf(mat));
            if (m == null) {
                org.bukkit.Bukkit.getLogger().warning(
                        "[xmusya-hard] end.portal.items: неизвестный материал " + mat);
                continue;
            }
            PoolItem pi = new PoolItem();
            pi.material = m;
            pi.name = raw.get("name") == null ? prettify(m) : String.valueOf(raw.get("name"));
            Object amt = raw.get("amount");
            pi.amount = amt instanceof Number n ? n.intValue() : 1;
            Object mx = raw.get("max-per-portal");
            pi.maxPerPortal = mx instanceof Number n ? n.intValue() : 1;
            e.pool.add(pi);
        }
    }

    private static String prettify(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Пул предметов по умолчанию. */
    private static void defaultPool(EndCfg e) {
        e.pool.clear();
        add(e, Material.IRON_INGOT, "Железо", 16, 3);
        add(e, Material.GOLD_INGOT, "Золото", 12, 2);
        add(e, Material.DIAMOND, "Алмазы", 4, 2);
        add(e, Material.EMERALD, "Изумруды", 8, 2);
        add(e, Material.ENDER_PEARL, "Жемчуг Энда", 4, 2);
        add(e, Material.BLAZE_ROD, "Огненный стержень", 6, 2);
        add(e, Material.OBSIDIAN, "Обсидиан", 16, 2);
        add(e, Material.CRYING_OBSIDIAN, "Плачущий обсидиан", 6, 2);
        add(e, Material.ANCIENT_DEBRIS, "Древние обломки", 2, 1);
        add(e, Material.NETHERITE_INGOT, "Незеритовый слиток", 1, 1);
        add(e, Material.NETHER_STAR, "Звезда Незера", 1, 1);
        add(e, Material.GOLD_BLOCK, "Блок золота", 2, 1);
    }

    private static void defaultNormalRepair(EndCfg e) {
        e.normalRepair.clear();
        addTo(e.normalRepair, Material.IRON_BLOCK, "Блок железа", 8);
        addTo(e.normalRepair, Material.GOLD_BLOCK, "Блок золота", 2);
        addTo(e.normalRepair, Material.DIAMOND, "Алмазы", 4);
        addTo(e.normalRepair, Material.EMERALD, "Изумруды", 2);
        addTo(e.normalRepair, Material.NETHERITE_INGOT, "Незеритовый слиток", 1);
    }

    private static void defaultUltraRepair(EndCfg e) {
        e.ultraRepair.clear();
        addTo(e.ultraRepair, Material.NETHERITE_BLOCK, "Блок незерита", 1);
        addTo(e.ultraRepair, Material.HEAVY_CORE, "Тяжёлое ядро", 1);
        addTo(e.ultraRepair, Material.NETHER_STAR, "Звезда Незера", 1);
        addTo(e.ultraRepair, Material.ENCHANTED_GOLDEN_APPLE, "Зачарованное золотое яблоко", 2);
        addTo(e.ultraRepair, Material.TOTEM_OF_UNDYING, "Тотем бессмертия", 1);
        addTo(e.ultraRepair, Material.CONDUIT, "Кондуит", 1);
        addTo(e.ultraRepair, Material.TRIDENT, "Трезубец", 1);
        addTo(e.ultraRepair, Material.SPONGE, "Губка", 1);
        addTo(e.ultraRepair, Material.SNIFFER_EGG, "Яйцо нюхача", 1);
        addTo(e.ultraRepair, Material.OMINOUS_TRIAL_KEY, "Зловещий ключ", 1);
        addTo(e.ultraRepair, Material.CHORUS_FLOWER, "Цветок хоруса", 1);
        addTo(e.ultraRepair, Material.HEART_OF_THE_SEA, "Сердце моря", 1);
    }

    private static void defaultHeads(EndCfg e) {
        e.ultraHeads.clear();
        e.ultraHeads.add(head(Material.ZOMBIE_HEAD, "Голова зомби", 55));
        e.ultraHeads.add(head(Material.CREEPER_HEAD, "Голова крипера", 30));
        e.ultraHeads.add(head(Material.PIGLIN_HEAD, "Голова пиглина", 15));
    }

    private static HeadCfg head(Material m, String name, int weight) {
        HeadCfg h = new HeadCfg();
        h.material = m;
        h.name = name;
        h.weight = weight;
        return h;
    }

    private static void defaultRepairBuffs(EndCfg e) {
        e.repairBuffs.clear();
        e.repairBuffs.add(buff("resistance", 1));
        e.repairBuffs.add(buff("regeneration", 1));
        e.repairBuffs.add(buff("strength", 1));
    }

    private static BuffCfg buff(String effect, int amp) {
        BuffCfg b = new BuffCfg();
        b.effect = effect;
        b.amplifier = amp;
        return b;
    }

    private static void addTo(List<PoolItem> out, Material m, String name, int amount) {
        PoolItem pi = new PoolItem();
        pi.material = m;
        pi.name = name;
        pi.amount = amount;
        out.add(pi);
    }

    private static void add(EndCfg e, Material m, String name, int amount, int max) {
        PoolItem pi = new PoolItem();
        pi.material = m;
        pi.name = name;
        pi.amount = amount;
        pi.maxPerPortal = max;
        e.pool.add(pi);
    }
}
