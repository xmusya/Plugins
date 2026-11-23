package ru.xmusya.hard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Состояние энд-механик: выбранная крепость, рамки портала, награды,
 * проклятия, бонусные сердца. Хранится в end-state.yml (переживает рестарт).
 */
public final class EndState {

    /** Одна рамка портала. */
    public static final class Frame {
        public String world = "world";
        public int x, y, z;
        public String itemId = "";
        /** Альтернативы через запятую: подойдёт любой из этих предметов. */
        public String itemAny = "";
        public String itemName = "";
        public int amount = 1;
        public boolean filled = false;
        /** Рамка сломана и требует сложной починки. */
        public boolean broken = false;
        /** Набор для починки: "normal" или "ultra". */
        public String repairTier = "normal";
        /** Голова, выбранная для ультра-набора (материал). */
        public String repairHead = "";
        /** Ник игрока, вставившего око (для закрытой рамки). */
        public String contributor = "";

        public String key() {
            return world + ":" + x + ":" + y + ":" + z;
        }

        public Location loc() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    /** Выбранная крепость (её портал — единственный рабочий). */
    /** UID мира, к которому относится состояние (для определения вайпа). */
    public String worldUid = "";

    public String strongholdWorld = "";
    public int strongholdX, strongholdY, strongholdZ;
    public boolean strongholdChosen = false;
    /** Рамки выбранного портала: ключ блока -> данные. */
    public final Map<String, Frame> frames = new LinkedHashMap<>();
    /** Чанки чужих крепостей, где портал уже сломан (чтобы не сканировать повторно). */
    public final Set<String> disabledChunks = new HashSet<>();

    /** Последний день, когда сбрасывался портал (защита от дублей). */
    public int lastCycleDay = -1;
    /** Последний день обычного/ультра слома рамок. */
    public int lastNormalBreakDay = -1;
    public int lastUltraBreakDay = -1;

    /** Баффы за рамки: игрок -> (имя эффекта -> [amplifier, untilMillis]). */
    public final Map<UUID, Map<String, long[]>> buffs = new HashMap<>();
    /** Бонусные сердца (в сердцах, 1 сердце = 2.0 HP). */
    public final Map<UUID, Integer> bonusHearts = new HashMap<>();
    /** Сколько сердец уже начислено (чтобы не дублировать). */
    public final Map<UUID, Integer> awardedHearts = new HashMap<>();

    public UUID eggKeeper = null;

    public final Map<UUID, Long> cursedUntil = new HashMap<>();
    public final Map<UUID, Long> endBannedUntil = new HashMap<>();

    // ------------------------------------------------------------------ файл
    private File file;

    public void bind(File dataFolder) {
        this.file = new File(dataFolder, "end-state.yml");
    }

    public void load() {
        if (file == null || !file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        String storedUid = y.getString("world-uid", "");
        String currentUid = worldUidNow();
        if (!storedUid.isEmpty() && !currentUid.isEmpty() && !storedUid.equals(currentUid)) {
            Bukkit.getLogger().info("[xmusya-hard] Мир пересоздан — состояние Энда сброшено.");
            resetAll();
            return;
        }

        strongholdWorld = y.getString("stronghold.world", "");
        strongholdX = y.getInt("stronghold.x");
        strongholdY = y.getInt("stronghold.y");
        strongholdZ = y.getInt("stronghold.z");
        strongholdChosen = y.getBoolean("stronghold.chosen", false);
        lastCycleDay = y.getInt("cycle.last-day", -1);
        lastNormalBreakDay = y.getInt("break.last-normal-day", -1);
        lastUltraBreakDay = y.getInt("break.last-ultra-day", -1);

        disabledChunks.addAll(y.getStringList("disabled-chunks"));

        ConfigurationSection fs = y.getConfigurationSection("frames");
        if (fs != null) {
            for (String key : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                Frame f = new Frame();
                f.world = s.getString("world", "world");
                f.x = s.getInt("x");
                f.y = s.getInt("y");
                f.z = s.getInt("z");
                f.itemId = s.getString("item", "");
                f.itemAny = s.getString("any", "");
                f.itemName = s.getString("name", "");
                f.amount = s.getInt("amount", 1);
                f.filled = s.getBoolean("filled", false);
                f.broken = s.getBoolean("broken", false);
                f.repairTier = s.getString("repair-tier", "normal");
                f.repairHead = s.getString("repair-head", "");
                f.contributor = s.getString("contributor", "");
                frames.put(f.key(), f);
            }
        }

        ConfigurationSection bs = y.getConfigurationSection("buffs");
        if (bs != null) {
            for (String id : bs.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(id);
                    ConfigurationSection s = bs.getConfigurationSection(id);
                    if (s == null) {
                        continue;
                    }
                    Map<String, long[]> map = new HashMap<>();
                    for (String eff : s.getKeys(false)) {
                        List<Long> v = s.getLongList(eff);
                        if (v.size() >= 2) {
                            map.put(eff, new long[]{v.get(0), v.get(1)});
                        }
                    }
                    buffs.put(u, map);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        for (String id : y.getStringList("bonus-hearts")) {
            String[] p = id.split("=");
            if (p.length == 2) {
                try {
                    bonusHearts.put(UUID.fromString(p[0]), Integer.parseInt(p[1]));
                } catch (RuntimeException ignored) {
                }
            }
        }
        for (String id : y.getStringList("awarded-hearts")) {
            String[] p = id.split("=");
            if (p.length == 2) {
                try {
                    awardedHearts.put(UUID.fromString(p[0]), Integer.parseInt(p[1]));
                } catch (RuntimeException ignored) {
                }
            }
        }
        for (String id : y.getStringList("cursed")) {
            String[] p = id.split("=");
            if (p.length == 2) {
                try {
                    cursedUntil.put(UUID.fromString(p[0]), Long.parseLong(p[1]));
                } catch (RuntimeException ignored) {
                }
            }
        }
        for (String id : y.getStringList("end-banned")) {
            String[] p = id.split("=");
            if (p.length == 2) {
                try {
                    endBannedUntil.put(UUID.fromString(p[0]), Long.parseLong(p[1]));
                } catch (RuntimeException ignored) {
                }
            }
        }

        String keeper = y.getString("egg.keeper", "");
        if (!keeper.isEmpty()) {
            try {
                eggKeeper = UUID.fromString(keeper);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("world-uid", worldUidNow());
        y.set("stronghold.world", strongholdWorld);
        y.set("stronghold.x", strongholdX);
        y.set("stronghold.y", strongholdY);
        y.set("stronghold.z", strongholdZ);
        y.set("stronghold.chosen", strongholdChosen);
        y.set("cycle.last-day", lastCycleDay);
        y.set("break.last-normal-day", lastNormalBreakDay);
        y.set("break.last-ultra-day", lastUltraBreakDay);
        y.set("disabled-chunks", new ArrayList<>(disabledChunks));

        for (Map.Entry<String, Frame> e : frames.entrySet()) {
            Frame f = e.getValue();
            String p = "frames." + safe(e.getKey()) + ".";
            y.set(p + "world", f.world);
            y.set(p + "x", f.x);
            y.set(p + "y", f.y);
            y.set(p + "z", f.z);
            y.set(p + "item", f.itemId);
            y.set(p + "any", f.itemAny);
            y.set(p + "name", f.itemName);
            y.set(p + "amount", f.amount);
            y.set(p + "filled", f.filled);
            y.set(p + "broken", f.broken);
            y.set(p + "repair-tier", f.repairTier);
            y.set(p + "repair-head", f.repairHead);
            y.set(p + "contributor", f.contributor);
        }

        for (Map.Entry<UUID, Map<String, long[]>> e : buffs.entrySet()) {
            for (Map.Entry<String, long[]> b : e.getValue().entrySet()) {
                List<Long> v = new ArrayList<>();
                v.add(b.getValue()[0]);
                v.add(b.getValue()[1]);
                y.set("buffs." + e.getKey() + "." + safe(b.getKey()), v);
            }
        }
        List<String> bh = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : bonusHearts.entrySet()) {
            bh.add(e.getKey() + "=" + e.getValue());
        }
        y.set("bonus-hearts", bh);
        List<String> ah = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : awardedHearts.entrySet()) {
            ah.add(e.getKey() + "=" + e.getValue());
        }
        y.set("awarded-hearts", ah);
        y.set("cursed", pairs(cursedUntil));
        y.set("end-banned", pairs(endBannedUntil));
        y.set("egg.keeper", eggKeeper == null ? "" : eggKeeper.toString());

        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            y.save(file);
        } catch (IOException ex) {
            Bukkit.getLogger().warning("[xmusya-hard] не удалось сохранить end-state.yml: " + ex.getMessage());
        }
    }

    /** Полный сброс (вайп мира или /xmh reset). */
    public void resetAll() {
        strongholdWorld = "";
        strongholdX = strongholdY = strongholdZ = 0;
        strongholdChosen = false;
        frames.clear();
        disabledChunks.clear();
        lastCycleDay = -1;
        lastNormalBreakDay = -1;
        lastUltraBreakDay = -1;
        buffs.clear();
        bonusHearts.clear();
        awardedHearts.clear();
        eggKeeper = null;
        cursedUntil.clear();
        endBannedUntil.clear();
        save();
    }

    /** UID оверворлда (меняется при пересоздании мира). */
    private static String worldUidNow() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                return w.getUID().toString();
            }
        }
        return "";
    }

    private static List<String> pairs(Map<UUID, Long> m) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : m.entrySet()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        return out;
    }

    /** Ключи YAML не любят двоеточия — заменяем на подчёркивания. */
    private static String safe(String s) {
        return s.replace(':', '_').replace('.', '_');
    }

    // ------------------------------------------------------------------ рамки
    public Frame frameAt(String key) {
        return frames.get(key);
    }

    public static String keyOf(org.bukkit.block.Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public int filledCount() {
        int n = 0;
        for (Frame f : frames.values()) {
            if (f.filled) {
                n++;
            }
        }
        return n;
    }
}
