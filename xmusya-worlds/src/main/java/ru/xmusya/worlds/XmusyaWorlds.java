package ru.xmusya.worlds;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * xmusya-worlds — хаб-мир с порталами, общим Эндом и памятью позиций.
 * Paper/Leaf 26.3.
 */
public final class XmusyaWorlds extends JavaPlugin implements Listener {

    public static XmusyaWorlds I;

    // ── состояние из конфига ─────────────────────────────────────────────
    private String defaultWorld = "world_hub";
    private int blindnessTicks = 20;
    private int blindnessAmp = 0;
    private int portalDelay = 4;
    private int portalCooldown = 40;
    private int scanInterval = 5;
    private boolean rememberPositions = true;
    private boolean endSync = true;
    private String endSharedWorld = "world_the_end";
    private String endEnterMsg = "";
    private String endExitMsg = "";
    /** Куда игрок попадает в общем Энде (null = дефолтная платформа 0,50,0). */
    private Location endSpawn = null;
    private boolean hubCmdEnabled = true;
    /** Режим приключений только в хабе (в остальных мирах — выживание). */
    private boolean hubAdventure = false;
    /** Респавн в мире смерти (а не в выживании). */
    private boolean respawnInDeathWorld = true;
    /** Левитация при падении в пустоту. */
    private boolean voidLevitation = true;
    /** Вместо левитации — телепорт на спавн мира. */
    private boolean voidTeleport = true;
    /** Эффект пустоты только в хабе (а не во всех мирах). */
    private boolean voidOnlyHub = true;
    private int voidLevitationAmp = 4;       // V
    private int voidLevitationTicks = 200;   // 10 сек
    private double voidMinY = -64;           // ниже этого Y — пустота
    private String hubCmdName = "hub";
    private String hubTpMessage = "";
    /** Держать ли спавн-чанки не-хаб миров в памяти (по умолч. false — экономия RAM). */
    private boolean keepSpawnOthers = false;
    /** Синхронизация датапаков из мира-источника во все миры. */
    private boolean syncEnabled = true;
    private String syncSource = "";          // пусто = основной мир (level-name)
    private List<String> syncInclude = new ArrayList<>();
    private List<String> syncExclude = new ArrayList<>();

    /** key = world key (hub/survival/...), value = имя папки мира. */
    private final Map<String, String> worldKeyToFolder = new LinkedHashMap<>();
    /** key = имя папки мира. */
    private final Map<String, WorldCfg> worldCfgs = new HashMap<>();
    /** key = РЕАЛЬНОЕ имя Bukkit-мира (для датапак-измерений оно иное). */
    private final Map<String, WorldCfg> worldNameToCfg = new HashMap<>();
    /** uuid -> мир, в котором игрок умер (для респавна). */
    private final Map<UUID, String> deathWorld = new HashMap<>();

    private final List<Portal> portals = new ArrayList<>();
    /** uuid -> id портала, в котором игрок был в прошлом скане. */
    private final Map<UUID, String> lastPortal = new HashMap<>();
    /** uuid -> тик последнего срабатывания (для кулдауна). */
    private final Map<UUID, Long> lastTrigger = new HashMap<>();
    /** uuid -> мир-источник перед входом в общий Энд. */
    private final Map<UUID, String> endOrigin = new HashMap<>();
    /** uuid, у которых сейчас «отложенный» телепорт (анти-дребезг). */
    private final Map<UUID, Boolean> pendingTp = new HashMap<>();

    private BukkitTask scanTask;
    private BukkitTask saveTask;
    private YamlConfiguration players;
    private File playersFile;
    private boolean playersDirty = false;

    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void onLoad() {
        I = this;
        // Датапаки должны лежать в папке мира ДО его первой генерации.
        // Поэтому копируем их уже тут (до загрузки миров).
        readConfigEarly();
        installDatapacksEarly();
        syncDatapacksEarly();
    }

    @Override
    public void onEnable() {
        loadConfig();
        loadPlayers();

        createWorlds();
        buildPortalIndex();
        // платформа Энда должна быть всегда (даже если мир Энда уже существовал)
        if (endSync) {
            World ew = Bukkit.getWorld(endSharedWorld);
            if (ew != null) ensureEndPlatform(ew);
        }

        getServer().getPluginManager().registerEvents(this, this);

        startScanTask();
        startVoidTask();

        getLogger().info("xmusya-worlds включён. Миров: " + worldKeyToFolder.size()
                + ", порталов: " + portals.size() + (endSync ? ", общий Энд: " + endSharedWorld : ""));
        // диагностика Энда
        if (endSync) {
            World ew = Bukkit.getWorld(endSharedWorld);
            getLogger().info("Диагностика Энда: world=" + ew
                    + (ew != null ? (" spawn=" + ew.getSpawnLocation()
                    + " env=" + ew.getEnvironment()) : " (НЕ НАЙДЕН!)"));
            StringBuilder sb = new StringBuilder("Все миры сервера:");
            for (World w : Bukkit.getWorlds()) sb.append(' ').append(w.getName());
            getLogger().info(sb.toString());
        }
    }

    @Override
    public void onDisable() {
        if (scanTask != null) scanTask.cancel();
        if (saveTask != null) saveTask.cancel();
        savePlayers();
    }

    // ─────────────────────────────────────────────────────────────────────
    // КОНФИГ
    // ─────────────────────────────────────────────────────────────────────
    private void readConfigEarly() {
        // на onLoad конфиг ещё не грузился — читаем значения, нужные для
        // расстановки датапаков (какие миры и какие паки).
        File cfg = new File(getDataFolder(), "config.yml");
        if (!cfg.isFile()) saveDefaultConfig();
        YamlConfiguration c = YamlConfiguration.loadConfiguration(cfg);
        ConfigurationSection ws = c.getConfigurationSection("worlds");
        if (ws != null) {
            for (String key : ws.getKeys(false)) {
                ConfigurationSection w = ws.getConfigurationSection(key);
                if (w == null) continue;
                String folder = w.getString("name", "world_" + key);
                String gen = w.getString("generator", "vanilla");
                WorldCfg wc0 = new WorldCfg(key, folder, gen,
                        w.getString("environment", "NORMAL"),
                        w.getString("world-type", "NORMAL"),
                        w.getString("seed", "0"));
                wc0.buildPortals = w.getBoolean("build-portals", false);
                worldKeyToFolder.put(key, folder);
                worldCfgs.put(folder, wc0);
            }
        }
        readSyncCfg(c);
    }

    private void readSyncCfg(org.bukkit.configuration.file.FileConfiguration c) {
        ConfigurationSection ds = c.getConfigurationSection("datapacks.sync");
        if (ds == null) return;
        syncEnabled = ds.getBoolean("enabled", true);
        syncSource = ds.getString("source", "");
        syncInclude = ds.getStringList("include");
        syncExclude = ds.getStringList("exclude");
    }

    private void loadConfig() {
        reloadConfig();
        var c = getConfig();

        defaultWorld = c.getString("general.default-world", "world_hub");
        hubTpMessage = c.getString("general.hub-teleport-message", "");
        blindnessTicks = c.getInt("general.portal-blindness-ticks", 20);
        blindnessAmp = c.getInt("general.portal-blindness-amplifier", 0);
        portalDelay = Math.max(0, c.getInt("general.portal-delay-ticks", 4));
        portalCooldown = Math.max(0, c.getInt("general.portal-cooldown-ticks", 40));
        scanInterval = Math.max(1, c.getInt("general.scan-interval-ticks", 5));
        rememberPositions = c.getBoolean("general.remember-positions", true);
        endSync = c.getBoolean("general.end-sync", true);
        keepSpawnOthers = c.getBoolean("general.keep-spawn-loaded-others", false);

        readSyncCfg(c);

        endSharedWorld = c.getString("end.shared-world", "world_the_end");
        endEnterMsg = c.getString("end.enter-message", "");
        endExitMsg = c.getString("end.exit-message", "");
        ConfigurationSection es = c.getConfigurationSection("end.spawn");
        if (es != null) {
            endSpawn = new Location(null,
                    es.getDouble("x", 0), es.getDouble("y", 50), es.getDouble("z", 0),
                    (float) es.getDouble("yaw", 0), (float) es.getDouble("pitch", 0));
        }

        hubCmdEnabled = c.getBoolean("hub-command.enabled", true);
        hubCmdName = c.getString("hub-command.name", "hub");

        hubAdventure = c.getBoolean("hub.adventure-mode", false);
        respawnInDeathWorld = c.getBoolean("respawn.in-death-world", true);
        voidLevitation = c.getBoolean("void.levitation.enabled", true);
        voidTeleport = c.getBoolean("void.teleport-to-spawn", true);
        voidOnlyHub = c.getBoolean("void.only-hub", true);
        voidLevitationAmp = c.getInt("void.levitation.amplifier", 4);
        voidLevitationTicks = Math.max(0, c.getInt("void.levitation.duration-ticks", 200));
        voidMinY = c.getDouble("void.levitation.min-y", -64);

        // миры (перечитать на случай смены конфига)
        worldKeyToFolder.clear();
        worldCfgs.clear();
        ConfigurationSection ws = c.getConfigurationSection("worlds");
        if (ws != null) {
            for (String key : ws.getKeys(false)) {
                ConfigurationSection w = ws.getConfigurationSection(key);
                if (w == null) continue;
                String folder = w.getString("name", "world_" + key);
                worldKeyToFolder.put(key, folder);
                WorldCfg wc = new WorldCfg(key, folder, w.getString("generator", "vanilla"),
                        w.getString("environment", "NORMAL"),
                        w.getString("world-type", "NORMAL"),
                        w.getString("seed", "0"));
                wc.buildPortals = w.getBoolean("build-portals", false);
                ConfigurationSection sp = w.getConfigurationSection("spawn");
                if (sp != null) {
                    wc.spawn = new Location(null,
                            sp.getDouble("x", 0), sp.getDouble("y", 64), sp.getDouble("z", 0),
                            (float) sp.getDouble("yaw", 0), (float) sp.getDouble("pitch", 0));
                }
                worldCfgs.put(folder, wc);
            }
        }

        buildPortalIndex();
    }

    // ─────────────────────────────────────────────────────────────────────
    // ДАТАПАКИ (копирование в папку мира до генерации)
    // ─────────────────────────────────────────────────────────────────────
    private void installDatapacksEarly() {
        File container = worldContainer();
        // В 26.x датапаки читаются НА ВЕСЬ УРОВЕНЬ (world/datapacks), а не по мирам.
        // Поэтому companion-пак с кастомными измерениями кладём в основной мир.
        // Тотальные паки (cave/islands) больше НЕ ставим — они переопределяют
        // minecraft:overworld и ломают выживание. Их рельеф дают измерения из пакета.
        installPack(container, mainWorldFolder(), "xmusya-worlds.zip");
    }

    /** Имя папки основного мира (level-name из server.properties, по умолч. "world"). */
    private String mainWorldFolder() {
        File container = worldContainer();
        File props = new File(container, "server.properties");
        if (props.isFile()) {
            try (InputStream in = new java.io.FileInputStream(props)) {
                java.util.Properties p = new java.util.Properties();
                p.load(in);
                String l = p.getProperty("level-name");
                if (l != null && !l.isBlank()) return l;
            } catch (IOException ignored) {
            }
        }
        return "world";
    }

    /**
     * Находит мир по имени папки. Для датапак-измерений (world_cave/world_islands)
     * Bukkit-имя другое (world_xmusya_world_cave), поэтому пробуем и ключ xmusya:&lt;folder&gt;.
     */
    private World worldOf(String folder) {
        if (folder == null) return null;
        World w = Bukkit.getWorld(folder);
        if (w != null) return w;
        try {
            return Bukkit.getWorld(org.bukkit.NamespacedKey.fromString("xmusya:" + folder));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Синхронизация датапаков: копирует .zip из папки datapacks мира-источника
     * (по умолчанию основной мир) во все остальные миры. Так «быстрые счастливые
     * гасты» из world появляются и в cave/farm/islands/хабе.
     */
    private void syncDatapacksEarly() {
        // В 26.x датапаки глобальны (world/datapacks) — синхронизация по мирам больше
        // не нужна. Оставляем метод для совместимости, но ничего не копируем.
        if (syncEnabled) {
            getLogger().info("Датапаки глобальны (world/datapacks) — синхронизация не требуется.");
        }
    }

    private void installPack(File container, String worldFolder, String resource) {
        File dir = new File(new File(container, worldFolder), "datapacks");
        dir.mkdirs();
        File out = new File(dir, resource);
        try (InputStream in = getResource("datapacks/" + resource)) {
            if (in == null) {
                getLogger().warning("В jar нет datapacks/" + resource + " — мир " + worldFolder
                        + " будет без датапака.");
                return;
            }
            boolean changed = copy(in, out);
            getLogger().info((changed ? "Датапак записан: " : "Датапак уже на месте: ") + out.getPath());
        } catch (IOException ex) {
            getLogger().warning("Не удалось установить датапак " + resource + ": " + ex.getMessage());
        }
    }

    private File worldContainer() {
        try {
            File c = Bukkit.getWorldContainer();
            if (c != null) return c;
        } catch (RuntimeException | LinkageError ignored) {
        }
        File data = getDataFolder();
        return new File(data.getParentFile().getParentFile(), ".");
    }

    private static boolean copy(InputStream in, File out) throws IOException {
        out.getParentFile().mkdirs();
        Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────
    // СОЗДАНИЕ МИРОВ
    // ─────────────────────────────────────────────────────────────────────
    private void createWorlds() {
        // общий Энд
        if (endSync && Bukkit.getWorld(endSharedWorld) == null) {
            try {
                WorldCreator wc = new WorldCreator(endSharedWorld)
                        .environment(World.Environment.THE_END)
                        .type(WorldType.NORMAL)
                        .keepSpawnLoaded(TriState.byBoolean(keepSpawnOthers));
                wc.createWorld();
                World ew = Bukkit.getWorld(endSharedWorld);
                if (ew != null) ensureEndPlatform(ew);
                getLogger().info("Создан общий Энд: " + endSharedWorld);
            } catch (RuntimeException ex) {
                getLogger().severe("Не удалось создать общий Энд " + endSharedWorld + ": "
                        + ex.getMessage());
            }
        }

        for (WorldCfg cfg : worldCfgs.values()) {
            // Датапак-миры (cave/islands) приходят из companion-пака как кастомные
            // измерения — плагин их НЕ создаёт, только находит.
            if (cfg.generator.startsWith("datapack:")) {
                World dw = worldOf(cfg.folder);
                if (dw == null) {
                    getLogger().warning("Измерение " + cfg.folder + " не найдено — проверь, что "
                            + "xmusya-worlds.zip лежит в world/datapacks и сервер перезапущен.");
                } else {
                    // спавн на суше (если не задан в конфиге) — чтобы не падать в воду
                    if (cfg.spawn != null) {
                        cfg.spawn.setWorld(dw);
                        dw.setSpawnLocation(cfg.spawn);
                    } else {
                        int y = dw.getHighestBlockYAt(0, 0);
                        dw.setSpawnLocation(0, y + 1, 0);
                    }
                    getLogger().info("Найдено датапак-измерение " + cfg.folder
                            + " → " + dw.getName());
                }
                continue;
            }
            if (Bukkit.getWorld(cfg.folder) != null) continue;
            try {
                WorldCreator wc = new WorldCreator(cfg.folder)
                        .environment(parseEnv(cfg.environment))
                        .seed(parseSeed(cfg.seed));
                // Спавн-чанки в памяти: хаб держим всегда, остальные — по конфигу.
                // Это главная экономия RAM на слабом сервере (5 миров = 5 наборов).
                boolean keep = cfg.buildPortals || keepSpawnOthers
                        || cfg.folder.equals(worldKeyToFolder.get(defaultWorld));
                wc.keepSpawnLoaded(TriState.byBoolean(keep));
                switch (cfg.generator) {
                    case "void" -> wc.generator(new VoidGenerator());
                    case "flat" -> wc.type(WorldType.FLAT);
                    case "vanilla" -> { /* default */ }
                    default -> {
                        if (cfg.generator.startsWith("datapack:")) {
                            wc.type(WorldType.NORMAL); // датапак переопределяет генерацию
                        }
                    }
                }
                if (cfg.worldType != null && !cfg.generator.equals("void")) {
                    wc.type(parseWorldType(cfg.worldType));
                }
                World w = wc.createWorld();
                if (w != null) {
                    if (cfg.spawn != null) {
                        cfg.spawn.setWorld(w);
                        w.setSpawnLocation(cfg.spawn);
                    }
                    getLogger().info("Создан мир " + cfg.folder + " (" + cfg.generator + ")");
                }
            } catch (RuntimeException ex) {
                // один битый/несовместимый мир НЕ должен выключать весь плагин
                getLogger().severe("Не удалось создать мир " + cfg.folder + " (" + cfg.generator
                        + "): " + ex.getMessage());
                getLogger().severe("  → проверь папку " + cfg.folder + " (возможно, старый формат"
                        + " или остатки неудачной миграции в world/dimensions/).");
            }
        }

        // карта реальное Bukkit-имя → конфиг (для датапак-измерений имя иное)
        worldNameToCfg.clear();
        for (WorldCfg cfg : worldCfgs.values()) {
            World w = worldOf(cfg.folder);
            if (w != null) worldNameToCfg.put(w.getName(), cfg);
        }
    }

    /** Ставит обсидиановую платформу 5×5 в Энде (как ваниль), чтобы игрок не падал в бездну. */
    /**
     * Ванильная точка входа в Энд — платформа из обсидиана 100/48/0
     * (игрок стоит на Y=49). Ставим её, если вдруг не сгенерирована.
     */
    private void ensureEndPlatform(World w) {
        int y = 48;
        w.getChunkAt(6, 0); // чанк, в котором лежит x=100
        boolean has = w.getBlockAt(100, y, 0).getType() == Material.OBSIDIAN;
        if (!has) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    w.getBlockAt(100 + dx, y, dz).setType(Material.OBSIDIAN, false);
                }
            }
        }
        // спавн мира Энда — на платформе (для /spawn, респауна и т.п.)
        if (w.getSpawnLocation().getBlockX() != 100
                || w.getSpawnLocation().getBlockY() != y + 1) {
            w.setSpawnLocation(100, y + 1, 0);
        }
    }

    private Location endDest(World shared) {
        if (endSpawn != null) {
            Location l = endSpawn.clone();
            l.setWorld(shared);
            return l;
        }
        // ванильная платформа: X=100, Y=49 (стоим на обсидиане 48), Z=0
        return new Location(shared, 100.5, 49.0, 0.5);
    }

    private World.Environment parseEnv(String s) {
        return switch (s.toUpperCase()) {
            case "NETHER" -> World.Environment.NETHER;
            case "THE_END", "END" -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        };
    }

    private WorldType parseWorldType(String s) {
        return switch (s.toUpperCase()) {
            case "FLAT" -> WorldType.FLAT;
            case "AMPLIFIED" -> WorldType.AMPLIFIED;
            case "LARGE_BIOMES" -> WorldType.LARGE_BIOMES;
            case "SINGLE_BIOME_SURFACE" -> WorldType.SINGLE_BIOME_SURFACE;
            case "DEBUG_ALL_BLOCK_STATES" -> WorldType.DEBUG_ALL_BLOCK_STATES;
            default -> WorldType.NORMAL;
        };
    }

    /** Сид: "random" → случайный (уникальный мир), число → как есть, иначе -1. */
    private long parseSeed(String s) {
        if (s == null) return -1L;
        String t = s.trim();
        if (t.isEmpty()) return -1L;
        if (t.equalsIgnoreCase("random")) {
            return java.util.concurrent.ThreadLocalRandom.current().nextLong();
        }
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // ПОРТАЛЫ
    // ─────────────────────────────────────────────────────────────────────
    private void buildPortalIndex() {
        portals.clear();
        var c = getConfig();
        ConfigurationSection ps = c.getConfigurationSection("portals");
        if (ps == null) return;
        for (String id : ps.getKeys(false)) {
            ConfigurationSection p = ps.getConfigurationSection(id);
            if (p == null) continue;
            String to = p.getString("to");
            ConfigurationSection mn = p.getConfigurationSection("min");
            ConfigurationSection mx = p.getConfigurationSection("max");
            if (to == null || mn == null || mx == null) continue;
            Portal po = new Portal();
            po.id = id;
            po.to = to;
            po.label = p.getString("label", id);
            po.message = p.getString("message", "");
            po.target = p.getString("target", "memory");
            po.cooldown = p.getInt("cooldown", portalCooldown);
            po.minX = Math.min(mn.getInt("x"), mx.getInt("x"));
            po.maxX = Math.max(mn.getInt("x"), mx.getInt("x"));
            po.minY = Math.min(mn.getInt("y"), mx.getInt("y"));
            po.maxY = Math.max(mn.getInt("y"), mx.getInt("y"));
            po.minZ = Math.min(mn.getInt("z"), mx.getInt("z"));
            po.maxZ = Math.max(mn.getInt("z"), mx.getInt("z"));
            ConfigurationSection fx = p.getConfigurationSection("coords");
            if (fx != null && "coords".equals(po.target)) {
                po.fixed = new Location(null,
                        fx.getDouble("x", 0), fx.getDouble("y", 64), fx.getDouble("z", 0),
                        (float) fx.getDouble("yaw", 0), (float) fx.getDouble("pitch", 0));
            }
            portals.add(po);
        }
    }

    private void startScanTask() {
        if (scanTask != null) scanTask.cancel();
        scanTask = Bukkit.getScheduler().runTaskTimer(this, this::scanPortals, 20L, scanInterval);
        if (saveTask != null) saveTask.cancel();
        // автосейв позиций раз в 30с (600 тиков), только если есть изменения
        saveTask = Bukkit.getScheduler().runTaskTimer(this, this::autoSaveTask, 600L, 600L);
    }

    private void scanPortals() {
        if (portals.isEmpty()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            World w = p.getWorld();
            WorldCfg wc = w == null ? null : worldNameToCfg.get(w.getName());
            // быстрый выход: порталы только в build-portals мирах (обычно хаб)
            if (wc == null || !wc.buildPortals) {
                if (lastPortal.remove(p.getUniqueId()) != null) { /* cleared */ }
                continue;
            }
            Portal inside = portalAt(p.getLocation());
            String id = inside == null ? null : inside.id;
            String prev = lastPortal.get(p.getUniqueId());
            if (id != null && !id.equals(prev)) {
                long now = System.currentTimeMillis();
                long last = lastTrigger.getOrDefault(p.getUniqueId(), 0L);
                long cd = inside.cooldown * 50L;
                if (now - last >= cd && !Boolean.TRUE.equals(pendingTp.get(p.getUniqueId()))) {
                    triggerPortal(p, inside);
                }
            }
            lastPortal.put(p.getUniqueId(), id);
        }
    }

    private Portal portalAt(Location loc) {
        World w = loc.getWorld();
        if (w == null) return null;
        WorldCfg wc = worldNameToCfg.get(w.getName());
        if (wc == null || !wc.buildPortals) return null;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        for (Portal p : portals) {
            if (x >= p.minX && x <= p.maxX
                    && y >= p.minY && y <= p.maxY
                    && z >= p.minZ && z <= p.maxZ) {
                return p;
            }
        }
        return null;
    }

    private void triggerPortal(Player p, Portal portal) {
        String toFolder = worldKeyToFolder.get(portal.to);
        World target = worldOf(toFolder);
        if (target == null) {
            getLogger().warning("Портал " + portal.id + " ведёт в неизвестный мир '" + portal.to + "'.");
            return;
        }
        final World tw = target;
        lastTrigger.put(p.getUniqueId(), System.currentTimeMillis());
        pendingTp.put(p.getUniqueId(), Boolean.TRUE);

        // запомнить позицию в текущем мире
        if (rememberPositions) savePos(p);

        if (blindnessTicks > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    blindnessTicks, blindnessAmp, false, false, false));
        }

        Runnable doTp = () -> {
            Location dest = resolveTarget(p, portal, tw);
            p.teleport(dest);
            if (!portal.message.isEmpty()) msg(p, portal.message);
            pendingTp.remove(p.getUniqueId());
            lastPortal.put(p.getUniqueId(), portal.id);
        };
        if (portalDelay > 0) {
            Bukkit.getScheduler().runTaskLater(this, doTp, portalDelay);
        } else {
            doTp.run();
        }
    }

    private Location resolveTarget(Player p, Portal portal, World tw) {
        Location base;
        if ("spawn".equals(portal.target)) {
            base = tw.getSpawnLocation().clone();
        } else if ("coords".equals(portal.target) && portal.fixed != null) {
            base = portal.fixed.clone();
        } else {
            Location mem = loadPos(p.getUniqueId(), tw.getName());
            base = mem != null ? mem : tw.getSpawnLocation().clone();
        }
        base.setWorld(tw);
        return base;
    }

    // ─────────────────────────────────────────────────────────────────────
    // ОБЩИЙ ЭНД
    // ─────────────────────────────────────────────────────────────────────
    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (!endSync) return;
        World from = e.getFrom().getWorld();
        World to = e.getTo() == null ? null : e.getTo().getWorld();
        if (from == null || to == null) return;

        // Вход в Энд (целевой мир — THE_END): ведём в общий Энд.
        if (to.getEnvironment() == World.Environment.THE_END) {
            World shared = Bukkit.getWorld(endSharedWorld);
            if (shared == null) return;
            endOrigin.put(e.getPlayer().getUniqueId(), from.getName());
            ensureEndPlatform(shared); // платформа + спавн на месте, даже если мир генерился раньше
            e.setTo(endDest(shared));
            if (!endEnterMsg.isEmpty()) msg(e.getPlayer(), endEnterMsg);
            return;
        }
        // Выход из Энда (источник — THE_END): возврат в мир-источник.
        if (from.getEnvironment() == World.Environment.THE_END) {
            String originName = endOrigin.get(e.getPlayer().getUniqueId());
            World origin = originName == null ? null : Bukkit.getWorld(originName);
            if (origin == null) origin = Bukkit.getWorlds().get(0);
            Location dest;
            Location mem = rememberPositions ? loadPos(e.getPlayer().getUniqueId(), origin.getName()) : null;
            dest = mem != null ? mem : origin.getSpawnLocation().clone();
            dest.setWorld(origin);
            e.setTo(dest);
            endOrigin.remove(e.getPlayer().getUniqueId());
            if (!endExitMsg.isEmpty()) msg(e.getPlayer(), endExitMsg);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // ПОЗИЦИИ ИГРОКОВ (players.yml)
    // ─────────────────────────────────────────────────────────────────────
    private void loadPlayers() {
        playersFile = new File(getDataFolder(), "players.yml");
        players = playersFile.isFile()
                ? YamlConfiguration.loadConfiguration(playersFile)
                : new YamlConfiguration();
    }

    private void savePlayers() {
        if (players == null) return;
        try {
            players.save(playersFile);
            playersDirty = false;
        } catch (IOException ex) {
            getLogger().warning("players.yml: " + ex.getMessage());
        }
    }

    /** Периодический сброс «грязных» позиций на диск (не на каждый выход). */
    private void autoSaveTask() {
        if (playersDirty) savePlayers();
    }

    private void savePos(Player p) {
        if (p.getWorld() == null) return;
        Location l = p.getLocation();
        String base = "players." + p.getUniqueId() + ".worlds." + p.getWorld().getName();
        players.set(base + ".world", p.getWorld().getName());
        players.set(base + ".x", l.getX());
        players.set(base + ".y", l.getY());
        players.set(base + ".z", l.getZ());
        players.set(base + ".yaw", (double) l.getYaw());
        players.set(base + ".pitch", (double) l.getPitch());
        playersDirty = true;
    }

    private Location loadPos(UUID id, String worldName) {
        if (players == null) return null;
        String base = "players." + id + ".worlds." + worldName;
        if (!players.contains(base + ".x")) return null;
        return new Location(Bukkit.getWorld(worldName),
                players.getDouble(base + ".x"),
                players.getDouble(base + ".y"),
                players.getDouble(base + ".z"),
                (float) players.getDouble(base + ".yaw"),
                (float) players.getDouble(base + ".pitch"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (rememberPositions) savePos(e.getPlayer());
        UUID id = e.getPlayer().getUniqueId();
        lastPortal.remove(id);
        lastTrigger.remove(id);
        pendingTp.remove(id);
        endOrigin.remove(id);
        // НЕ пишем players.yml на каждый выход (фриз диска на телефоне) —
        // сброс по dirty-флагу в автосейве (каждые 30с) и в onDisable.
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPlayedBefore()) {
            String folder = worldKeyToFolder.get(defaultWorld);
            World w = worldOf(folder);
            if (w != null) {
                Location l = w.getSpawnLocation().clone();
                Bukkit.getScheduler().runTask(this, () -> p.teleport(l));
            }
        }
        applyGameMode(p);
    }

    /** Приключение только в хабе; в остальных мирах — выживание (если был приключенческий). */
    private void applyGameMode(Player p) {
        if (!hubAdventure) return;
        String hubFolder = worldKeyToFolder.get(defaultWorld);
        World hub = worldOf(hubFolder);
        if (hub == null) return;
        if (p.getWorld().equals(hub)) {
            if (p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.ADVENTURE);
        } else if (p.getGameMode() == GameMode.ADVENTURE) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        applyGameMode(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        deathWorld.put(e.getEntity().getUniqueId(), e.getEntity().getWorld().getName());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!respawnInDeathWorld) return;
        Player p = e.getPlayer();
        String wname = deathWorld.remove(p.getUniqueId());
        World w = wname == null ? null : Bukkit.getWorld(wname);
        if (w == null) return; // не наш мир — ванильное поведение
        Location dest;
        Location mem = rememberPositions ? loadPos(p.getUniqueId(), wname) : null;
        dest = mem != null ? mem : w.getSpawnLocation().clone();
        dest.setWorld(w);
        e.setRespawnLocation(dest);
    }

    /** Левитация при падении в пустоту (проверка раз в 10 тиков). */
    private void startVoidTask() {
        if (!voidLevitation) return;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            World hub = voidOnlyHub ? worldOf(worldKeyToFolder.get(defaultWorld)) : null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (voidOnlyHub && !p.getWorld().equals(hub)) continue;
                if (p.getLocation().getY() < voidMinY) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
                            voidLevitationTicks, voidLevitationAmp, false, false, false));
                }
            }
        }, 20L, 10L);
    }

    // ─────────────────────────────────────────────────────────────────────
    // КОМАНДЫ
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();
        if (name.equals(hubCmdName) && hubCmdEnabled) {
            if (!(sender instanceof Player p)) return true;
            String folder = worldKeyToFolder.get(defaultWorld);
            World w = worldOf(folder);
            if (w == null) { msg(p, "&cХаб не найден."); return true; }
            if (rememberPositions) savePos(p);
            p.teleport(w.getSpawnLocation().clone());
            if (!hubTpMessage.isEmpty()) msg(p, hubTpMessage);
            return true;
        }
        if (name.equals("worlds") || name.equals("world") || name.equals("xw")) {
            return onWorldsCommand(sender, args);
        }
        return false;
    }

    private boolean onWorldsCommand(CommandSender s, String[] args) {
        if (args.length == 0) {
            msg(s, "&6/worlds &7reload | list | datapacks | tp <world> [игрок] | spawn <world> | setspawn <world> | reset <world>");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                loadConfig();
                startScanTask();
                msg(s, "&aКонфиг перезагружен.");
            }
            case "list" -> {
                msg(s, "&6Миры:");
                for (var e : worldKeyToFolder.entrySet()) {
                    msg(s, "&7- &f" + e.getKey() + " &7(" + e.getValue() + ")");
                }
                if (endSync) msg(s, "&6Общий Энд: &f" + endSharedWorld);
                msg(s, "&6Порталы: &f" + portals.size());
            }
            case "tp" -> {
                if (args.length < 2) { msg(s, "&c/worlds tp <world> [игрок]"); return true; }
                String folder = worldKeyToFolder.get(args[1]);
                World w = worldOf(folder);
                if (w == null) { msg(s, "&cМир не найден: " + args[1]); return true; }
                Player t = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (s instanceof Player sp ? sp : null);
                if (t == null) { msg(s, "&cИгрок не найден."); return true; }
                if (rememberPositions) savePos(t);
                t.teleport(w.getSpawnLocation().clone());
                msg(s, "&aТелепортирован в " + args[1] + ".");
            }
            case "spawn" -> {
                if (args.length < 2) { msg(s, "&c/worlds spawn <world>"); return true; }
                World w = Bukkit.getWorld(args[1]);
                if (w == null) { msg(s, "&cМир не найден."); return true; }
                if (!(s instanceof Player p)) { msg(s, "&cТолько игрок."); return true; }
                w.setSpawnLocation(p.getLocation());
                msg(s, "&aСпавн мира " + args[1] + " установлен.");
            }
            case "setspawn" -> {
                if (args.length < 2) { msg(s, "&c/worlds setspawn <world>"); return true; }
                World w = Bukkit.getWorld(args[1]);
                if (w == null) { msg(s, "&cМир не найден."); return true; }
                w.setSpawnLocation(w.getSpawnLocation());
                msg(s, "&aСпавн мира " + args[1] + ": " + fmt(w.getSpawnLocation()));
            }
            case "datapacks" -> {
                File container = worldContainer();
                // в 26.x паки глобальные (world/datapacks) — на весь уровень
                File dir = new File(new File(container, mainWorldFolder()), "datapacks");
                File[] zips = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".zip"));
                msg(s, "&6Датапаки (глобально, " + mainWorldFolder() + "/datapacks):");
                if (zips == null || zips.length == 0) {
                    msg(s, "&7  (пусто)");
                } else {
                    for (File z : zips) msg(s, "&7- &f" + z.getName());
                }
                msg(s, "&7Кастомные измерения: &fxmusya:world_cave, xmusya:world_islands");
            }
            case "reset" -> {
                if (args.length < 2) { msg(s, "&c/worlds reset <world>"); return true; }
                String folder = worldKeyToFolder.get(args[1]);
                if (folder == null && Bukkit.getWorld(args[1]) != null) folder = args[1];
                if (folder == null) { msg(s, "&cМир не найден: " + args[1]); return true; }
                World w = Bukkit.getWorld(folder);
                if (w != null) {
                    msg(s, "&cМир " + folder + " загружен. Останови сервер и удали папку вручную.");
                    return true;
                }
                File dir = new File(worldContainer(), folder);
                if (!dir.isDirectory()) { msg(s, "&cПапка не найдена: " + folder); return true; }
                if (!deleteDir(dir)) { msg(s, "&cНе удалось удалить " + folder); return true; }
                msg(s, "&aПапка " + folder + " удалена. При следующем старте мир пересоздастся.");
            }
            default -> msg(s, "&cНеизвестная подкоманда.");
        }
        return true;
    }

    private boolean deleteDir(File dir) {
        File[] kids = dir.listFiles();
        if (kids != null) for (File k : kids) {
            if (k.isDirectory()) deleteDir(k); else k.delete();
        }
        return dir.delete();
    }

    private String fmt(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    // ─────────────────────────────────────────────────────────────────────
    // УТИЛИТЫ
    // ─────────────────────────────────────────────────────────────────────
    /** Кэш сериализаторов (не создаём на каждый вызов — экономия на слабом CPU). */
    private static final LegacyComponentSerializer AMP =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION =
            LegacyComponentSerializer.legacySection();

    private void msg(CommandSender s, String legacy) {
        if (legacy == null || legacy.isEmpty()) return;
        if (s instanceof Player p) {
            p.sendMessage(AMP.deserialize(legacy));
        } else {
            s.sendMessage(SECTION.serialize(AMP.deserialize(legacy)));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    private static final class WorldCfg {
        final String key, folder, generator, environment, worldType;
        final String seed; // "random" или число (строкой)
        boolean buildPortals;
        Location spawn;
        WorldCfg(String key, String folder, String generator, String environment,
                 String worldType, String seed) {
            this.key = key; this.folder = folder; this.generator = generator;
            this.environment = environment; this.worldType = worldType; this.seed = seed;
        }
    }

    private static final class Portal {
        String id, to, label, message, target;
        int minX, minY, minZ, maxX, maxY, maxZ, cooldown;
        Location fixed;
    }
}