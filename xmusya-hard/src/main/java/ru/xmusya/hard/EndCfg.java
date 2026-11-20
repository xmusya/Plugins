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

    public boolean enabled = true;

    // --- крепость
    /** Включена ли механика «одна крепость + ломаные порталы в остальных». */
    public boolean strongholdEnabled = true;
    public int minRadius = 5000;
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
    public int curseHours = 6;
    public int endBanHours = 6;

    // --- внешние острова
    public int outerUnlockDay = 80;
    public double outerMaxDistance = 450.0;

    // --- дракон
    public long dragonShieldCooldownMs = 1800L;

    // --- пустота
    public int voidMinY = 0;
    public int voidPearls = 2;
    public int voidLevitationSeconds = 10;
    /** Уровень Левитации: 4 = Левитация V (amplifier от 0). */
    public int voidLevitationAmplifier = 4;
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
            return e;
        }
        e.enabled = s.getBoolean("enabled", e.enabled);

        e.strongholdEnabled = s.getBoolean("stronghold.enabled", e.strongholdEnabled);
        e.minRadius = s.getInt("stronghold.min-radius", e.minRadius);
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

        e.outerUnlockDay = s.getInt("outer-islands.unlock-day", e.outerUnlockDay);
        e.outerMaxDistance = s.getDouble("outer-islands.max-distance", e.outerMaxDistance);

        e.dragonShieldCooldownMs = s.getLong("dragon.shield-cooldown-ms", e.dragonShieldCooldownMs);

        e.voidMinY = s.getInt("void.min-y", e.voidMinY);
        e.voidPearls = s.getInt("void.pearls", e.voidPearls);
        e.voidLevitationSeconds = s.getInt("void.levitation-seconds", e.voidLevitationSeconds);
        e.voidLevitationAmplifier = s.getInt("void.levitation-amplifier", e.voidLevitationAmplifier);
        e.voidSlowFallingSeconds = s.getInt("void.slow-falling-seconds", e.voidSlowFallingSeconds);
        e.voidSlowFallingAmplifier = s.getInt("void.slow-falling-amplifier", e.voidSlowFallingAmplifier);

        e.eggHasteAmplifier = s.getInt("egg.haste-amplifier", e.eggHasteAmplifier);
        e.eggSpeedAmplifier = s.getInt("egg.speed-amplifier", e.eggSpeedAmplifier);

        loadPool(e, s.getMapList("portal.items"));
        if (e.pool.isEmpty()) {
            defaultPool(e);
        }
        if (e.cycleResetMin > e.cycleResetMax) {
            int t = e.cycleResetMin;
            e.cycleResetMin = e.cycleResetMax;
            e.cycleResetMax = t;
        }
        return e;
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

    private static void add(EndCfg e, Material m, String name, int amount, int max) {
        PoolItem pi = new PoolItem();
        pi.material = m;
        pi.name = name;
        pi.amount = amount;
        pi.maxPerPortal = max;
        e.pool.add(pi);
    }
}
