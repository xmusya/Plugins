package ru.xmusya.hard;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * xmusya-hard — хардкор с прогрессией по игровым дням.
 * Полный порт одноимённого датапака на Paper API (Minecraft 26.3).
 */
public final class XmusyaHard extends JavaPlugin implements Listener {

    public static XmusyaHard I;

    // ------------------------------------------------------------------ конфиг
    /** Настройки всех 10 фаз (из config.yml, с полными значениями по умолчанию). */
    public final List<PhaseCfg> phases = new ArrayList<>();

    // Производные пороги дней (для быстрых проверок и боссбара)
    public int dayWood = 0;
    public int dayStone = 1;
    public int dayIron = 5;
    public int dayDiamond = 10;
    public int dayNether = 25;
    public int dayGolden = 30;
    public int dayVillagers = 40;
    public int dayBeacons = 45;
    public int dayNetherite = 50;
    public int dayEndgame = 60;

    public int starterBuffsUntilDay = 4;

    public int borderStart = 650;
    public int borderMid1 = 1250;
    public int borderMid2 = 2500;
    public int borderDaily = 100;
    public int borderMax = 100000;
    public int bmInterval = 10;
    /** Усиление мобов в Кровавую луну (amplifier; -1 = не выдавать). */
    public int bmMobStrength = 0;
    public int bmMobSpeed = 0;
    /** Порог «шахтёра» (ниже — не действует КЛ, если был там до начала). */
    public int bmMineY = 40;
    /** Порог «высотника» (выше — не действует КЛ, если был там до начала). */
    public int bmHeightY = 120;
    /** С какого дня в КЛ появляются визер-скелеты, утопленники и вексы. */
    public int bmEndgameDay = 60;
    /** Сколько мобов за одну волну (каждые 100 тиков) вокруг игрока. */
    public int bmSpawnPerWave = 3;
    /** Предел враждебных мобов в радиусе 16 блоков от игрока. */
    public int bmMaxHostiles = 28;
    /** Попыток найти место для спавна за одну попытку. */
    public int bmSpawnAttempts = 16;
    /** Радиус поиска места спавна (блоки). */
    public int bmSpawnRadius = 24;
    /** Сколько мобов спавнится залпом сразу при старте КЛ. */
    public int bmBurstOnStart = 10;
    /** Анти-кемп (вексы/фантомы) действует и на игроков-исключений (шахта/высота). */
    public boolean bmExemptBlocksAntiCamp = false;
    /** Игроки, спрятанные в шахте/на высоте на момент старта КЛ. */
    public final Set<UUID> bmExempt = new HashSet<>();
    private NamespacedKey bmWitherKey;

    // ------------------------------------------------------------------ состояние
    public long totalTicks = 0L;
    public int currentDay = 0;
    public int prevDay = 0;
    public int phase = 0;
    public int lastDaytime = 0;

    public boolean phasesPaused = false;
    public boolean autoPaused = false;
    public int emptyTicks = 0;

    public boolean bmActive = false;
    public boolean bmForced = false;
    public boolean bmPaused = false;

    public double borderSize = 650.0;

    public final Map<UUID, Integer> deaths = new HashMap<>();
    public final Map<UUID, Integer> bmSurvived = new HashMap<>();

    // энд-механики
    public EndCfg endCfg;
    public EndState endState;
    public EndManager endManager;
    public EndPortal endPortal;
    public DragonBeacon dragonBeacon;
    public GuideBook guideBook;
    /** true, если обнаружено пересоздание мира (сбрасываем прогресс). */
    public boolean wipeDetected = false;

    private final Random rnd = new Random();
    private BossBar mainBar;
    private BossBar warnBar;
    private boolean warnVisible = false;

    // ------------------------------------------------------------------ включение
    @Override
    public void onEnable() {
        I = this;
        saveDefaultConfig();
        // Доливаем новые ключи в существующий config.yml (старые значения не трогаем)
        int merged = ConfigMerge.merge(getDataFolder(), getResource("config.yml"));
        if (merged > 0) {
            getLogger().info("В config.yml добавлено новых параметров: " + merged
                    + " (старые значения сохранены, копия — config.yml.bak).");
        }
        loadConfig();
        bmWitherKey = new NamespacedKey(this, "bm_wither_skeleton");
        loadState();
        // Дополнительная проверка «мир сброшен»: состояние опережает мир
        // (например, остался день 30 и фаза netherite, а мир только создан).
        World ow0 = overworld();
        if (!wipeDetected && ow0 != null) {
            int worldDay = (int) (ow0.getFullTime() / 24000L);
            if (currentDay > worldDay + 1) {
                wipeDetected = true;
                currentDay = worldDay;
                prevDay = worldDay;
                phase = 0;
                bmActive = false;
                bmForced = false;
                bmPaused = false;
                getLogger().info("Состояние опережало мир (день " + worldDay
                        + ") — прогресс сброшен.");
            }
        }

        mainBar = BossBar.bossBar(Component.text("Xmusya Hard"), 0.0f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        warnBar = BossBar.bossBar(bmWarningName(), 1.0f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("xmh") != null) {
            getCommand("xmh").setExecutor(this);
        }

        // --- энд-механики
        endCfg = EndCfg.load(getConfig());
        if (endCfg.enabled) {
            endState = new EndState();
            endState.bind(getDataFolder());
            endState.load();
            if (wipeDetected) {
                endState.resetAll();
            }
            endManager = new EndManager(this, endCfg, endState);
            endPortal = new EndPortal(this, endCfg, endState, endManager);
            getServer().getPluginManager().registerEvents(endManager, this);
            getServer().getPluginManager().registerEvents(endPortal, this);
            endManager.start();
            endPortal.start();
        }

        // Яйцо дракона на маяке
        dragonBeacon = new DragonBeacon(this);
        getServer().getPluginManager().registerEvents(dragonBeacon, this);
        dragonBeacon.start();
        // Книга-гайд
        guideBook = new GuideBook(this);
        getServer().getPluginManager().registerEvents(guideBook, this);
        // Меню /xmh
        getServer().getPluginManager().registerEvents(new Menu(), this);

        collectRecipeKeys();
        getLogger().info("Рецептов на сервере: " + allRecipeKeys.size()
                + " (firework_rocket: " + (recipeExists("firework_rocket") ? "есть" : "НЕТ") + ")");

        // Центр и размер барьера приводим в порядок при старте (во всех мирах)
        applyBorder(borderSize, 0L);
        applyRules();

        // 1 тик: счётчик дней и авто-пауза
        Bukkit.getScheduler().runTaskTimer(this, this::tickTime, 1L, 1L);
        // 10 тиков
        Bukkit.getScheduler().runTaskTimer(this, this::tick10, 1L, 10L);
        // 20 тиков
        Bukkit.getScheduler().runTaskTimer(this, this::tick20, 1L, 20L);
        // 100 тиков — спавн в темноте во время КЛ
        Bukkit.getScheduler().runTaskTimer(this, this::tick100, 100L, 100L);
        // 200 тиков — отсидка в помещении
        Bukkit.getScheduler().runTaskTimer(this, this::tick200, 200L, 200L);
        // 300 тиков — высота/глубина
        Bukkit.getScheduler().runTaskTimer(this, this::tick300, 300L, 300L);

        // Онлайн-игрокам сразу выдаём рецепты и меню
        for (Player p : players()) {
            syncRecipes(p);
        }

        getLogger().info("xmusya-hard включён. День " + currentDay + ", фаза "
                + Phases.KEYS[phase] + ", барьер " + (int) borderSize + ".");
    }

    @Override
    public void onDisable() {
        saveState();
        if (endState != null) {
            endState.save();
        }
        if (dragonBeacon != null) {
            dragonBeacon.save();
        }
        if (mainBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(mainBar);
                p.hideBossBar(warnBar);
            }
        }
        getLogger().info("xmusya-hard выключен, состояние сохранено.");
    }

    // ------------------------------------------------------------------ конфиг
    public void loadConfig() {
        reloadConfig();
        borderStart = getConfig().getInt("border.start", 650);
        borderMid1 = getConfig().getInt("border.mid1", 1250);
        borderMid2 = getConfig().getInt("border.mid2", 2500);
        borderDaily = getConfig().getInt("border.daily", 100);
        borderMax = getConfig().getInt("border.max", 100000);
        bmInterval = getConfig().getInt("bloodmoon.interval", 10);
        bmMobStrength = getConfig().getInt("bloodmoon.mob-strength", 0);
        bmMobSpeed = getConfig().getInt("bloodmoon.mob-speed", 0);
        bmMineY = getConfig().getInt("bloodmoon.mine-y", 40);
        bmHeightY = getConfig().getInt("bloodmoon.height-y", 120);
        bmEndgameDay = getConfig().getInt("bloodmoon.endgame-mobs-day", 60);
        bmSpawnPerWave = Math.max(1, getConfig().getInt("bloodmoon.spawn-per-wave", 3));
        bmMaxHostiles = Math.max(4, getConfig().getInt("bloodmoon.max-hostiles-nearby", 28));
        bmSpawnAttempts = Math.max(4, getConfig().getInt("bloodmoon.spawn-attempts", 16));
        bmSpawnRadius = Math.max(8, getConfig().getInt("bloodmoon.spawn-radius", 24));
        bmBurstOnStart = Math.max(0, getConfig().getInt("bloodmoon.burst-on-start", 10));
        bmExemptBlocksAntiCamp = getConfig().getBoolean("bloodmoon.exempt-blocks-anti-camp", false);
        starterBuffsUntilDay = getConfig().getInt("starter-buffs-until-day", 4);
        Msg.setPrefix(getConfig().getString("prefix", "&c[XMH] "));

        List<PhaseCfg> loaded = PhaseCfg.defaults();
        org.bukkit.configuration.ConfigurationSection ps =
                getConfig().getConfigurationSection("phases");
        if (ps != null) {
            for (int i = 0; i < loaded.size(); i++) {
                org.bukkit.configuration.ConfigurationSection s =
                        ps.getConfigurationSection(Phases.KEYS[i]);
                if (s != null) {
                    applyPhaseSection(loaded.get(i), s);
                }
            }
        } else {
            getLogger().warning("В config.yml нет секции 'phases' — используются значения по умолчанию.");
        }
        phases.clear();
        phases.addAll(loaded);

        dayWood = phases.get(0).day;
        dayStone = phases.get(1).day;
        dayIron = phases.get(2).day;
        dayDiamond = phases.get(3).day;
        dayNether = phases.get(4).day;
        dayGolden = phases.get(5).day;
        dayVillagers = phases.get(6).day;
        dayBeacons = phases.get(7).day;
        dayNetherite = phases.get(8).day;
        dayEndgame = phases.get(9).day;
    }

    private void applyPhaseSection(PhaseCfg c,
                                   org.bukkit.configuration.ConfigurationSection s) {
        c.day = s.getInt("day", c.day);
        c.border = s.getInt("border", c.border);
        c.dailyBorder = s.getInt("daily-border", c.dailyBorder);
        c.name = s.getString("name", c.name);
        c.shortName = s.getString("short-name", c.shortName);
        c.limitedCrafting = s.getBoolean("limited-crafting", c.limitedCrafting);
        c.naturalRegeneration = s.getBoolean("natural-regeneration", c.naturalRegeneration);
        c.trade = s.getBoolean("trade", c.trade);

        if (s.isSet("dimensions")) {
            c.dimensions.clear();
            for (String d : s.getStringList("dimensions")) {
                c.dimensions.add(d.toLowerCase().replace("minecraft:", "").trim());
            }
        }
        if (s.isSet("forbidden-armor")) {
            c.forbiddenArmor.clear();
            for (String n : s.getStringList("forbidden-armor")) {
                addArmor(c.forbiddenArmor, n, c.key);
            }
        }
        if (s.isSet("forbidden-tools")) {
            c.forbiddenTools.clear();
            for (String n : s.getStringList("forbidden-tools")) {
                addTools(c.forbiddenTools, n, c.key);
            }
        }
        if (s.isSet("forbidden-items")) {
            c.forbiddenItems.clear();
            for (String n : s.getStringList("forbidden-items")) {
                addItem(c.forbiddenItems, n, c.key);
            }
        }
        if (s.isSet("recipes")) {
            c.recipes.clear();
            for (String r : s.getStringList("recipes")) {
                c.recipes.add(r.toLowerCase().replace("minecraft:", "").trim());
            }
        }
    }

    private void addArmor(Set<Material> out, String name, String phaseKey) {
        switch (name.toLowerCase().replace("minecraft:", "").trim()) {
            case "iron" -> out.addAll(Phases.IRON_ARMOR);
            case "golden", "gold" -> {
                out.add(Material.GOLDEN_HELMET);
                out.add(Material.GOLDEN_CHESTPLATE);
                out.add(Material.GOLDEN_LEGGINGS);
                out.add(Material.GOLDEN_BOOTS);
            }
            case "diamond" -> out.addAll(Phases.DIAMOND_ARMOR);
            case "netherite" -> out.addAll(Phases.NETHERITE_ARMOR);
            case "elytra" -> out.add(Material.ELYTRA);
            default -> addItem(out, name, phaseKey);
        }
    }

    private void addTools(Set<Material> out, String name, String phaseKey) {
        switch (name.toLowerCase().replace("minecraft:", "").trim()) {
            case "stone" -> out.addAll(Phases.STONE_TOOLS);
            case "iron" -> out.addAll(Phases.IRON_TOOLS);
            case "golden", "gold" -> {
                out.add(Material.GOLDEN_SWORD);
                out.add(Material.GOLDEN_PICKAXE);
                out.add(Material.GOLDEN_AXE);
                out.add(Material.GOLDEN_SHOVEL);
                out.add(Material.GOLDEN_HOE);
            }
            case "diamond" -> out.addAll(Phases.DIAMOND_TOOLS);
            case "netherite" -> out.addAll(Phases.NETHERITE_TOOLS);
            default -> addItem(out, name, phaseKey);
        }
    }

    private void addItem(Set<Material> out, String name, String phaseKey) {
        String id = name.toLowerCase().replace("minecraft:", "").trim();
        Material m = Material.matchMaterial(id);
        if (m == null) {
            getLogger().warning("Фаза " + phaseKey + ": неизвестный предмет '" + name + "' в config.yml");
            return;
        }
        out.add(m);
    }

    /** Настройки текущей фазы. */
    public PhaseCfg cfg() {
        return phases.get(Math.max(0, Math.min(phases.size() - 1, phase)));
    }

    @Override
    public void saveDefaultConfig() {
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }
    }

    // ------------------------------------------------------------------ состояние
    private File stateFile() {
        return new File(getDataFolder(), "state.yml");
    }

    private void loadState() {
        File f = stateFile();
        if (!f.exists()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration y =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        String storedUid = y.getString("world-uid", "");
        String currentUid = stateWorldUid();
        if (!storedUid.isEmpty() && !currentUid.isEmpty() && !storedUid.equals(currentUid)) {
            wipeDetected = true;
            getLogger().info("Мир пересоздан — прогресс xmusya-hard сброшен к дню 0 и фазе wood.");
            return;
        }
        totalTicks = y.getLong("total-ticks", 0L);
        currentDay = y.getInt("day", 0);
        prevDay = y.getInt("prev-day", currentDay);
        phase = y.getInt("phase", 0);
        phasesPaused = y.getBoolean("phases-paused", false);
        autoPaused = y.getBoolean("auto-paused", false);
        bmActive = y.getBoolean("bm-active", false);
        bmForced = y.getBoolean("bm-forced", false);
        bmPaused = y.getBoolean("bm-paused", false);
        borderSize = y.getDouble("border", borderStart);
        if (y.isConfigurationSection("deaths")) {
            for (String k : y.getConfigurationSection("deaths").getKeys(false)) {
                try {
                    deaths.put(UUID.fromString(k), y.getInt("deaths." + k));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (y.isConfigurationSection("bm-survived")) {
            for (String k : y.getConfigurationSection("bm-survived").getKeys(false)) {
                try {
                    bmSurvived.put(UUID.fromString(k), y.getInt("bm-survived." + k));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void saveState() {
        org.bukkit.configuration.file.YamlConfiguration y =
                new org.bukkit.configuration.file.YamlConfiguration();
        y.set("world-uid", stateWorldUid());
        y.set("total-ticks", totalTicks);
        y.set("day", currentDay);
        y.set("prev-day", prevDay);
        y.set("phase", phase);
        y.set("phases-paused", phasesPaused);
        y.set("auto-paused", autoPaused);
        y.set("bm-active", bmActive);
        y.set("bm-forced", bmForced);
        y.set("bm-paused", bmPaused);
        y.set("border", borderSize);
        for (Map.Entry<UUID, Integer> e : deaths.entrySet()) {
            y.set("deaths." + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Integer> e : bmSurvived.entrySet()) {
            y.set("bm-survived." + e.getKey(), e.getValue());
        }
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            y.save(stateFile());
        } catch (IOException e) {
            getLogger().warning("Не удалось сохранить состояние: " + e.getMessage());
        }
    }

    /** UID оверворлда — меняется при пересоздании мира (детект вайпа). */
    private String stateWorldUid() {
        World w = overworld();
        return w == null ? "" : w.getUID().toString();
    }

    // ------------------------------------------------------------------ утилиты
    public World overworld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                return w;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    public static boolean isBot(Player p) {
        return p.getScoreboardTags().contains("bot");
    }

    public List<Player> players() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isBot(p)) {
                out.add(p);
            }
        }
        return out;
    }

    public void console(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    /**
     * Геймрулы в Java свои у каждого мира (оверворлд/незер/энд), поэтому
     * команда выполняется через "execute in <измерение>" для всех миров.
     */
    public void gamerule(String rule, Object value) {
        String want = String.valueOf(value);
        for (World w : Bukkit.getWorlds()) {
            String cur = w.getGameRuleValue("minecraft:" + rule);
            if (cur == null) {
                cur = w.getGameRuleValue(rule);
            }
            if (want.equalsIgnoreCase(cur)) {
                continue;
            }
            console("execute in " + w.getKey() + " run gamerule minecraft:" + rule + " " + want);
        }
    }

    /** Барьер — тоже у каждого мира свой: ставим везде. ticks>0 = плавно. */
    public void applyBorder(double size, long ticks) {
        for (World w : Bukkit.getWorlds()) {
            WorldBorder wb = w.getWorldBorder();
            wb.setCenter(0.0, 0.0);
            if (ticks > 0) {
                wb.setSize(size, ticks);
            } else {
                wb.setSize(size);
            }
        }
    }

    public int phaseForDay(int day) {
        int[] map = {dayWood, dayStone, dayIron, dayDiamond, dayNether,
                dayGolden, dayVillagers, dayBeacons, dayNetherite, dayEndgame};
        int res = 0;
        for (int i = 0; i < map.length; i++) {
            if (day >= map[i]) {
                res = i;
            }
        }
        return res;
    }

    public void applyRules() {
        PhaseCfg c = cfg();
        gamerule("limited_crafting", c.limitedCrafting);
        gamerule("natural_health_regeneration", c.naturalRegeneration);
    }

    /** Все рецепты, реально существующие на сервере (ключи). */
    private final java.util.Set<org.bukkit.NamespacedKey> allRecipeKeys = new java.util.HashSet<>();
    private final java.util.Set<String> reportedUnknown = new java.util.HashSet<>();

    private void collectRecipeKeys() {
        allRecipeKeys.clear();
        java.util.Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            org.bukkit.inventory.Recipe r = it.next();
            if (r instanceof org.bukkit.Keyed k && k.getKey() != null) {
                allRecipeKeys.add(k.getKey());
            }
        }
    }

    private boolean recipeExists(String id) {
        if (!allRecipeKeys.isEmpty()
                && allRecipeKeys.contains(org.bukkit.NamespacedKey.minecraft(id))) {
            return true;
        }
        // рецепт может быть невидим в итераторе (спец-рецепты) — но существовать
        return allRecipeKeys.isEmpty() || Bukkit.getRecipe(org.bukkit.NamespacedKey.minecraft(id)) != null;
    }

    /**
     * Рецепты выдаются напрямую через API (Player#discoverRecipes), чтобы не
     * спамить чат сообщениями "Unlocked/Took N recipe(s)" и не зависеть от
     * парсинга команд. Несуществующие рецепты пропускаются.
     */
    public void syncRecipes(Player p) {
        if (!allRecipeKeys.isEmpty()) {
            p.discoverRecipes(allRecipeKeys);
        } else {
            console("recipe give " + p.getName() + " *");
        }

        java.util.List<org.bukkit.NamespacedKey> take = new java.util.ArrayList<>();
        for (int f = phase + 1; f < phases.size(); f++) {
            for (String r : phases.get(f).recipes) {
                if (!recipeExists(r)) {
                    if (reportedUnknown.add(r)) {
                        getLogger().info("Рецепта minecraft:" + r
                                + " нет на этом сервере — пропускаю (не ошибка конфига).");
                    }
                    continue;
                }
                take.add(org.bukkit.NamespacedKey.minecraft(r));
            }
        }
        if (!take.isEmpty()) {
            p.undiscoverRecipes(take);
        }
    }

    private void syncRecipesAll() {
        for (Player p : players()) {
            syncRecipes(p);
        }
    }

    /** Первый день, начиная с которого предмет разрешён (для сообщений игрокам). */
    public int dayWhenAllowed(Material m) {
        for (PhaseCfg c : phases) {
            if (!c.forbiddenArmor.contains(m)
                    && !c.forbiddenTools.contains(m)
                    && !c.forbiddenItems.contains(m)) {
                return c.day;
            }
        }
        return dayEndgame;
    }

    /** Первый день, начиная с которого измерение разрешено. */
    public int dayWhenDimensionAllowed(String dim) {
        for (PhaseCfg c : phases) {
            if (c.dimensions.contains(dim)) {
                return c.day;
            }
        }
        return dayEndgame;
    }

    /** Первый день, начиная с которого разрешена торговля. */
    public int dayWhenTradeAllowed() {
        for (PhaseCfg c : phases) {
            if (c.trade) {
                return c.day;
            }
        }
        return dayEndgame;
    }

    // ------------------------------------------------------------------ время и дни
    private void tickTime() {
        World w = overworld();
        if (w == null) {
            return;
        }
        int t = (int) (w.getTime() % 24000L);
        if (t < 0) {
            t += 24000;
        }
        int delta = t - lastDaytime;
        if (delta < 0) {
            delta += 24000;
        }
        if (delta > 24000) {
            delta = 0;
        }
        lastDaytime = t;

        // Дни считаем по мировому времени (getFullTime), а не своим счётчиком:
        // так учитываются сон, /time set, пропуски и т.п.
        long full = w.getFullTime();
        totalTicks = full;
        currentDay = (int) (full / 24000L);
        if (currentDay < prevDay) {
            prevDay = currentDay;
        }
        while (currentDay > prevDay) {
            prevDay++;
            onNewDay(prevDay);
        }
        tickAutoPause();
    }

    private void tickAutoPause() {
        boolean anyAlive = !players().isEmpty();
        if (anyAlive) {
            emptyTicks = 0;
            if (autoPaused) {
                autoPaused = false;
                if (!phasesPaused) {
                    gamerule("advance_time", true);
                    gamerule("advance_weather", true);
                    Msg.broadcast("Живой игрок в сети. Ход времени, погоды и дней возобновлен.",
                            NamedTextColor.GREEN);
                }
            }
        } else {
            emptyTicks++;
            if (emptyTicks == 600) {
                autoPaused = true;
                gamerule("advance_time", false);
                gamerule("advance_weather", false);
                Msg.broadcast("На сервере нет живых игроков (30 сек). Мир и таймер дней "
                        + "поставлены на авто-паузу.", NamedTextColor.YELLOW);
            }
        }
    }

    private void onNewDay(int day) {
        Msg.broadcastRaw(Msg.prefix()
                .append(Msg.c("Наступил день ", NamedTextColor.GRAY))
                .append(Msg.bold(String.valueOf(day), NamedTextColor.YELLOW))
                .append(Msg.c(".", NamedTextColor.GRAY)));
        growBorder(day);
        applyRules();
        checkPhase();
        if (endPortal != null) {
            endPortal.onNewDay(day);
        }
    }

    /** Прирост барьера в день на текущей фазе. */
    private int dailyBorderNow() {
        PhaseCfg c = cfg();
        return c != null && c.dailyBorder > 0 ? c.dailyBorder : borderDaily;
    }

    private void growBorder(int day) {
        if (day == 11) {
            borderSize = borderMid1;
            applyBorder(borderMid1, 200L);
            Msg.broadcast("Барьер расширяется до " + borderMid1 + " блоков.", NamedTextColor.AQUA);
        } else if (day == 21) {
            borderSize = borderMid2;
            applyBorder(borderMid2, 300L);
            Msg.broadcast("Барьер расширяется до " + borderMid2 + " блоков.", NamedTextColor.AQUA);
        } else if (day >= 31) {
            if (borderSize >= borderMax) {
                return;
            }
            int add = dailyBorderNow();
            borderSize = Math.min(borderMax, borderSize + add);
            applyBorder(borderSize, 100L);
            Msg.broadcast("Барьер вырос: " + (int) borderSize + " блоков.", NamedTextColor.AQUA);
        }
    }

    // ------------------------------------------------------------------ фазы
    public void checkPhase() {
        if (phasesPaused) {
            return;
        }
        int target = phaseForDay(currentDay);
        while (phase < target) {
            phase++;
            onPhaseEnter(phase);
        }
    }

    public void onPhaseEnter(int p) {
        // Барьер фазы применяем при переходе (расширяем, если он больше текущего)
        PhaseCfg pc = cfg();
        if (pc != null && pc.border > borderSize) {
            borderSize = pc.border;
            applyBorder(borderSize, 200L);
            Msg.broadcast("Барьер расширен до " + (int) borderSize + " блоков (новая фаза).",
                    NamedTextColor.AQUA);
        }
        applyRules();
        syncRecipesAll();
        announcePhase(p);
        if (p >= 3) {
            for (Player pl : players()) {
                rewardBuff(pl);
            }
        }
    }

    public void announcePhase(int p) {
        Msg.broadcastRaw(Msg.prefix()
                .append(Msg.c("Фаза: ", NamedTextColor.GRAY))
                .append(Msg.bold(phases.get(p).name, Phases.COLORS[Math.min(p, Phases.COLORS.length - 1)]))
                .append(Msg.c(" (день " + currentDay + ")", NamedTextColor.GRAY)));
    }

    public void rewardBuff(Player p) {
        String[] labels = {"Спешка II", "Скорость II", "Сила I", "Сопротивление урону I",
                "Огнестойкость", "Ночное зрение", "Регенерация I", "Поглощение II"};
        PotionEffectType[] types = {PotionEffectType.HASTE, PotionEffectType.SPEED,
                PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
                PotionEffectType.FIRE_RESISTANCE, PotionEffectType.NIGHT_VISION,
                PotionEffectType.REGENERATION, PotionEffectType.ABSORPTION};
        int[] amps = {1, 1, 0, 0, 0, 0, 0, 1};
        int idx = rnd.nextInt(8);
        p.addPotionEffect(new PotionEffect(types[idx], 600 * 20, amps[idx], true, false, true));
        p.sendMessage(Msg.prefix()
                .append(Msg.c("Награда фазы: ", NamedTextColor.GRAY))
                .append(Msg.bold(labels[idx], NamedTextColor.GREEN))
                .append(Msg.c(" на 10 минут!", NamedTextColor.GRAY)));
        p.playSound(Msg.click());
    }

    /** Ручная (админская) установка фазы — работает в обе стороны. */
    public void jumpToPhase(int p) {
        p = Math.max(0, Math.min(phases.size() - 1, p));
        PhaseCfg target = phases.get(p);
        int day = target.day;
        World ow = overworld();
        if (ow != null) {
            ow.setFullTime((long) day * 24000L + 1000L);
        }
        totalTicks = (long) day * 24000L + 1000L;
        currentDay = day;
        prevDay = day;
        phase = p;
        borderSize = target.border;
        applyBorder(borderSize, 0L);
        World w = overworld();
        lastDaytime = (int) (w == null ? 0 : w.getTime() % 24000L);
        applyRules();
        syncRecipesAll();
        announcePhase(p);
        if (p >= 3) {
            for (Player pl : players()) {
                rewardBuff(pl);
            }
        }
        Msg.broadcast("Фаза установлена администратором: " + target.name
                + " (день " + day + ", барьер " + (int) borderSize + ").", NamedTextColor.GOLD);
        saveState();
    }

    // ------------------------------------------------------------------ тики
    private void tick10() {
        for (Player p : players()) {
            Restrictions.checkArmor(this, p);
            Restrictions.checkHands(this, p);
            if (bmActive && !bmPaused) {
                Restrictions.checkInvisibility(p);
            }
        }
        if (bmActive && !bmPaused) {
            bloodMoonAura();
            bloodMoonCreepers();
        }
        updateBossBars();
    }

    private void tick20() {
        for (Player p : players()) {
            Restrictions.checkInventory(this, p);
            Restrictions.checkDimension(this, p);
            if (currentDay <= starterBuffsUntilDay) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 0, true, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false, true));
            }
        }
        checkBloodMoon();
        checkPhase();
        if (bmActive && !bmPaused) {
            for (Player p : players()) {
                if (!isBmExempt(p)) {
                    Restrictions.checkSleeping(p);
                }
            }
        }
    }

    private void tick100() {
        if (!bmActive || bmPaused) {
            return;
        }
        for (Player p : players()) {
            if (!isBmExempt(p)) {
                bloodMoonProbe(p);
            }
            retargetBmFlyers(p);
        }
    }

    private void tick200() {
        if (!bmActive || bmPaused) {
            return;
        }
        for (Player p : players()) {
            if (isBmExempt(p)) {
                continue;
            }
            if (!canSeeSky(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 140, 0, true, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 140, 0, true, false, true));
                Msg.action(p, "Стены не спасут вас от Кровавой луны... Выйдите наружу!");
                int count = 1 + rnd.nextInt(2);
                for (int i = 0; i < count; i++) {
                    Location loc = randomOutdoorLocation(p.getWorld(), p.getLocation(), 14, 20);
                    if (loc != null) {
                        Entity e = p.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                        if (e instanceof LivingEntity le) {
                            le.getEquipment().setItemInMainHand(new ItemStack(Material.WOODEN_SWORD));
                            le.addScoreboardTag("xh_scaled");
                        }
                    }
                }
            }
        }
    }

    private void tick300() {
        if (!bmActive || bmPaused) {
            return;
        }
        for (Player p : players()) {
            if (!bmExemptBlocksAntiCamp && isBmExempt(p)) {
                continue;
            }
            double y = p.getLocation().getY();
            if (y < bmMineY || y > bmHeightY) {
                Msg.action(p, "Глубина и высота не скроют вас от ночной охоты!");
                if (y < bmMineY) {
                    // в глубине — вексы прямо рядом с игроком
                    for (int i = 0; i < 2; i++) {
                        Location loc = findBmSpawn(p, true);
                        if (loc != null) {
                            spawnBmMob(p, loc, EntityType.VEX);
                        }
                    }
                    // и обычная живность по пещере
                    for (int i = 0; i < 2; i++) {
                        Location loc = findBmSpawn(p, false);
                        if (loc != null) {
                            spawnBmMob(p, loc, pickBmType(currentDay >= bmEndgameDay));
                        }
                    }
                } else {
                    // высоко — фантомы на 10–15 блоков выше игрока, сразу с целью
                    for (int i = 0; i < 3; i++) {
                        Location spawnAt = findPhantomSpot(p);
                        if (spawnAt != null) {
                            spawnBmMob(p, spawnAt, EntityType.PHANTOM);
                        }
                    }
                }
            }
        }
    }

    /** Точка для фантома: 10–15 блоков выше игрока, в воздухе. */
    private Location findPhantomSpot(Player p) {
        for (int i = 0; i < 12; i++) {
            Location at = p.getLocation().clone().add(
                    rnd.nextInt(11) - 5, 10 + rnd.nextInt(6), rnd.nextInt(11) - 5);
            if (at.getBlockY() >= at.getWorld().getMaxHeight() - 2) {
                at.setY(at.getWorld().getMaxHeight() - 3);
            }
            if (at.getBlock().isEmpty() && at.clone().add(0, 1, 0).getBlock().isEmpty()) {
                return at;
            }
        }
        return null;
    }

    private boolean canSeeSky(Player p) {
        Location loc = p.getLocation();
        int highest = p.getWorld().getHighestBlockYAt(loc);
        return highest <= loc.getBlockY();
    }

    private Location randomOutdoorLocation(World w, Location center, int min, int max) {
        for (int i = 0; i < 12; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = min + rnd.nextDouble() * (max - min);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = w.getHighestBlockYAt(x, z) + 1;
            Location loc = new Location(w, x + 0.5, y, z + 0.5);
            if (loc.getBlock().isEmpty() && !loc.clone().add(0, 1, 0).getBlock().isLiquid()) {
                return loc;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ кровавая луна
    private void checkBloodMoon() {
        if (bmPaused) {
            return;
        }
        World w = overworld();
        if (w == null) {
            return;
        }
        int t = (int) (w.getTime() % 24000L);
        boolean nightWindow = t >= 13000 && t <= 23000;
        boolean scheduled = !bmForced && currentDay > 0 && currentDay % bmInterval == 0 && nightWindow;

        if (bmActive) {
            if (!bmForced && !nightWindow) {
                stopBloodMoon();
            }
        } else if (bmForced || scheduled) {
            startBloodMoon();
        }
    }

    public void startBloodMoon() {
        if (bmActive) {
            return;
        }
        bmActive = true;
        World w = overworld();
        if (w != null) {
            w.setStorm(true);
            w.setThundering(true);
            w.setWeatherDuration(12000);
        }
        bmExempt.clear();
        for (Player p : players()) {
            double y = p.getLocation().getY();
            if (y < bmMineY || y > bmHeightY) {
                bmExempt.add(p.getUniqueId());
            }
            p.playSound(Msg.witherSpawn());
            // Залп при старте: сразу «растрел» вокруг игрока
            for (int i = 0; i < bmBurstOnStart; i++) {
                Location loc = findBmSpawn(p, i % 4 == 0);
                if (loc == null) {
                    continue;
                }
                spawnBmMob(p, loc, pickBmType(currentDay >= bmEndgameDay));
            }
        }
        Msg.broadcastRaw(Component.text("[КРОВАВАЯ ЛУНА] ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(Component.text("Наступила кровавая ночь! Сон и невидимость заблокированы, "
                        + "монстры вышли на охоту!", NamedTextColor.RED)));
        saveState();
    }

    public void stopBloodMoon() {
        if (!bmActive) {
            return;
        }
        bmActive = false;
        bmForced = false;
        World w = overworld();
        if (w != null) {
            w.setStorm(false);
            w.setThundering(false);
            w.setWeatherDuration(6000);
        }
        bmExempt.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains("xh_bm_flyer")) {
                    e.remove(); // вексы и фантомы КЛ деспавнятся к рассвету
                    continue;
                }
                e.removeScoreboardTag("bm_checked");
                e.removeScoreboardTag("xh_bm_spawned");
            }
        }
        for (Player p : players()) {
            bmSurvived.merge(p.getUniqueId(), 1, Integer::sum);
            p.giveExpLevels(3);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 1, true, false, true));
            p.sendMessage(Component.text("[КРОВАВАЯ ЛУНА] ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text("Кровавая луна пережита! +1 к счетчику, +3 уровня опыта, "
                            + "насыщение.", NamedTextColor.GOLD)));
        }
        Msg.broadcast("Кровавая луна окончена. Погода ясная.", NamedTextColor.GOLD);
        saveState();
    }

    private void bloodMoonAura() {
        for (Player p : players()) {
            for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 40, 40, 40)) {
                if (!(e instanceof LivingEntity le) || !isScalable(le)) {
                    continue;
                }
                if (bmMobStrength >= 0) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                            100, bmMobStrength, true, false, true));
                }
                if (bmMobSpeed >= 0) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                            100, bmMobSpeed, true, false, true));
                }
            }
        }
    }

    private void bloodMoonCreepers() {
        for (Player p : players()) {
            for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 40, 40, 40)) {
                if (e.getType() != EntityType.CREEPER || e.getScoreboardTags().contains("bm_checked")) {
                    continue;
                }
                e.addScoreboardTag("bm_checked");
                if (rnd.nextInt(100) < 70) {
                    Location loc = e.getLocation();
                    e.remove();
                    Entity z = loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                    z.addScoreboardTag("xh_bm_spawned");
                }
            }
        }
    }

    private void bloodMoonProbe(Player p) {
        World w = p.getWorld();
        int hostiles = countBmHostiles(p);
        if (hostiles >= bmMaxHostiles) {
            return;
        }
        boolean endgame = currentDay >= bmEndgameDay;
        for (int n = 0; n < bmSpawnPerWave && hostiles < bmMaxHostiles; n++) {
            EntityType type = pickBmType(endgame);
            Location loc = findBmSpawn(p, type == EntityType.VEX);
            if (loc == null) {
                continue;
            }
            spawnBmMob(p, loc, type);
            hostiles++;
        }
    }

    /** Фантомы/вексы КЛ должны видеть цель — иначе они просто летают рядом. */
    private void retargetBmFlyers(Player p) {
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 48, 48, 48)) {
            if (!(e instanceof Mob mob) || !mob.isValid()) {
                continue;
            }
            boolean flyer = e.getType() == EntityType.PHANTOM || e.getType() == EntityType.VEX;
            if (!flyer || !e.getScoreboardTags().contains("xh_bm_spawned")) {
                continue;
            }
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isValid() || target.isDead()
                    || !target.getWorld().equals(mob.getWorld())) {
                mob.setTarget(p);
            }
        }
    }

    private int countBmHostiles(Player p) {
        int n = 0;
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 16, 16, 16)) {
            if (e.getScoreboardTags().contains("xh_bm_flyer")) {
                n++;
            } else if (e instanceof LivingEntity le && isScalable(le)) {
                n++;
            }
        }
        return n;
    }

    private EntityType pickBmType(boolean endgame) {
        if (!endgame) {
            return switch (rnd.nextInt(3)) {
                case 0 -> EntityType.ZOMBIE;
                case 1 -> EntityType.SKELETON;
                default -> EntityType.SPIDER;
            };
        }
        return switch (rnd.nextInt(6)) {
            case 0 -> EntityType.ZOMBIE;
            case 1 -> EntityType.SKELETON;
            case 2 -> EntityType.DROWNED;
            case 3 -> EntityType.WITHER_SKELETON;
            case 4 -> EntityType.VEX;
            default -> EntityType.SPIDER;
        };
    }

    /** Ищет место рядом с игроком: для вексов — вплотную, для остальных — 10..radius. */
    private Location findBmSpawn(Player p, boolean vex) {
        World w = p.getWorld();
        int min = vex ? 3 : 10;
        int max = Math.max(min + 2, vex ? 6 : bmSpawnRadius);
        for (int i = 0; i < bmSpawnAttempts; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = min + rnd.nextDouble() * (max - min);
            int x = p.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = p.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            // у поверхности — по высоте земли, в шахте/пещере — рядом с игроком
            int y = p.getLocation().getBlockY() + rnd.nextInt(7) - 3;
            Location loc = new Location(w, x + 0.5, y, z + 0.5);
            if (!loc.getBlock().isEmpty() || loc.getBlock().getLightLevel() > 9) {
                continue;
            }
            if (!loc.clone().add(0, 1, 0).getBlock().isEmpty()) {
                continue;
            }
            return loc;
        }
        // подстраховка: прямо рядом с игроком
        Location near = p.getLocation().clone().add(rnd.nextInt(5) - 2, 1, rnd.nextInt(5) - 2);
        return near.getBlock().isEmpty() ? near : null;
    }

    private void spawnBmMob(Player p, Location loc, EntityType type) {
        Entity spawned = p.getWorld().spawnEntity(loc, type);
        spawned.addScoreboardTag("xh_bm_spawned");
        if (type == EntityType.VEX || type == EntityType.PHANTOM) {
            spawned.addScoreboardTag("xh_bm_flyer");
        }
        if (type == EntityType.VEX) {
            spawned.addScoreboardTag("xh_bm_vex");
        }
        if (type == EntityType.WITHER_SKELETON && bmWitherKey != null) {
            spawned.getPersistentDataContainer().set(bmWitherKey,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        }
        if (spawned instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            // Летающие моблы (фантом/векса) сами не всегда находят цель — тыкаем пальцем
            if (type == EntityType.PHANTOM || type == EntityType.VEX) {
                mob.setTarget(p);
            }
        }
    }

    /**
     * Игрок был в шахте/на высоте в момент начала КЛ и всё ещё там —
     * Кровавая луна на него не действует. Вернулся в обычные высоты —
     * исключение снимается до конца ночи.
     */
    public boolean isBmExempt(Player p) {
        if (!bmActive || bmExempt.isEmpty()) {
            return false;
        }
        if (!bmExempt.contains(p.getUniqueId())) {
            return false;
        }
        double y = p.getLocation().getY();
        if (y < bmMineY || y > bmHeightY) {
            return true;
        }
        bmExempt.remove(p.getUniqueId());
        return false;
    }

    /** Визер-скелеты, заспавненные КЛ, не дропают череп. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBloodMoonWitherDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (bmWitherKey == null || e.getEntityType() != EntityType.WITHER_SKELETON) {
            return;
        }
        if (!e.getEntity().getPersistentDataContainer().has(bmWitherKey,
                org.bukkit.persistence.PersistentDataType.BYTE)) {
            return;
        }
        e.getDrops().removeIf(st -> st != null && st.getType() == Material.WITHER_SKELETON_SKULL);
    }

    public boolean isScalable(Entity e) {
        return Phases.HOSTILE_TYPES.contains(e.getType().getKey().toString());
    }

    // ------------------------------------------------------------------ мобы
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof LivingEntity le)) {
            return;
        }
        if (e.getType() == EntityType.ENDER_DRAGON) {
            if (currentDay >= dayNetherite) {
                scaleBoss(le, 400.0);
            }
            return;
        }
        if (e.getType() == EntityType.WITHER) {
            if (currentDay >= dayNetherite) {
                scaleBoss(le, 600.0);
            }
            return;
        }
        if (!isScalable(le) || e.getScoreboardTags().contains("xh_scaled")) {
            return;
        }
        e.addScoreboardTag("xh_scaled");
        if (currentDay >= dayDiamond) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    Integer.MAX_VALUE, 0, true, false, false));
        }
        if (currentDay >= dayNether) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    Integer.MAX_VALUE, 0, true, false, false));
            le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                    Integer.MAX_VALUE, 0, true, false, false));
            if (e.getType() == EntityType.CREEPER && !bmActive && rnd.nextInt(5) == 0) {
                ((org.bukkit.entity.Creeper) e).setPowered(true);
            }
        }
    }

    private void scaleBoss(LivingEntity le, double hp) {
        if (le.getScoreboardTags().contains("boss_scaled")) {
            return;
        }
        AttributeInstance attr = le.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hp);
        }
        le.setHealth(hp);
        le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, 0, true, false, false));
        le.addScoreboardTag("boss_scaled");
    }

    /** Мелочь: включение {powered:1b} у крипера через Bukkit API. */

    // ------------------------------------------------------------------ боссбары
    private Component mainBarName() {
        if (phase >= 0 && phase < Phases.COUNT) {
            return Component.text("Фаза: ", NamedTextColor.GRAY)
                    .append(Component.text(cfg().shortName, Phases.COLORS[Math.min(phase, Phases.COLORS.length - 1)], TextDecoration.BOLD))
                    .append(Component.text(" | День ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(currentDay), NamedTextColor.YELLOW))
                    .append(Component.text(" из " + dayEndgame, NamedTextColor.GRAY));
        }
        return Component.text("Xmusya Hard");
    }

    private Component bmWarningName() {
        return Component.text("\u26a0 ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("ВНИМАНИЕ: Завтра наступит Кровавая луна! "
                        + "Подготовьтесь к обороне!", NamedTextColor.RED, TextDecoration.BOLD));
    }

    private void updateBossBars() {
        if (mainBar == null) {
            return;
        }
        mainBar.name(mainBarName());
        float progress = Math.min(1.0f, Math.max(0.0f, currentDay / (float) dayEndgame));
        mainBar.progress(progress);

        boolean showWarn = !bmPaused && isWarningDay(currentDay);
        warnVisible = showWarn;

        boolean showMain = currentDay < dayEndgame;

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean hidden = p.getScoreboardTags().contains("hide_bossbar") || isBot(p);
            if (showMain && !hidden) {
                p.showBossBar(mainBar);
            } else {
                p.hideBossBar(mainBar);
            }
            if (showWarn && !hidden) {
                p.showBossBar(warnBar);
            } else {
                p.hideBossBar(warnBar);
            }
        }
    }

    private boolean isWarningDay(int day) {
        return day > 0 && (day + 1) % bmInterval == 0 && day % bmInterval != 0;
    }

    // ------------------------------------------------------------------ события
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        syncRecipes(p);
        if (endManager != null) {
            endManager.applyBonusHearts(p);
            endManager.applyEggBuffs(p);
        }
        p.sendMessage(Msg.prefix()
                .append(Msg.c("Добро пожаловать. Меню: ", NamedTextColor.GRAY))
                .append(Component.text("/xmh", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/xmh"))
                        .hoverEvent(HoverEvent.showText(Component.text("Открыть меню", NamedTextColor.GRAY)))));
        if (!isBot(p) && autoPaused) {
            emptyTicks = 0;
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        deaths.merge(p.getUniqueId(), 1, Integer::sum);
        saveState();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!bmActive || bmPaused || isBot(event.getPlayer())
                || isBmExempt(event.getPlayer())) {
            return;
        }
        event.setUseBed(org.bukkit.event.Event.Result.DENY);
        Msg.action(event.getPlayer(), "Вы не можете уснуть во время Кровавой луны!");
        event.getPlayer().playSound(Msg.villagerNo());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        if (isBot(p) || cfg().trade) {
            return;
        }
        EntityType t = event.getRightClicked().getType();
        if (t == EntityType.VILLAGER || t == EntityType.WANDERING_TRADER) {
            event.setCancelled(true);
            p.playSound(Msg.villagerNo());
            Msg.action(p, "Торговля с жителями откроется на "
                    + dayWhenTradeAllowed() + "-й день!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p) || isBot(p) || cfg().trade) {
            return;
        }
        if (event.getInventory() instanceof MerchantInventory) {
            event.setCancelled(true);
            p.playSound(Msg.villagerNo());
            Msg.action(p, "Торговля с жителями откроется на "
                    + dayWhenTradeAllowed() + "-й день!");
        }
    }

    // ------------------------------------------------------------------ команды
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                Menu.open(this, p);
                return true;
            }
            sendAdminHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();

        if (sub.equals("bar")) {
            if (!(sender instanceof Player p)) {
                return true;
            }
            if (p.getScoreboardTags().contains("hide_bossbar")) {
                p.removeScoreboardTag("hide_bossbar");
                p.sendMessage(Msg.prefix().append(Msg.c("BossBar показан.", NamedTextColor.GRAY)));
            } else {
                p.addScoreboardTag("hide_bossbar");
                p.sendMessage(Msg.prefix().append(Msg.c("BossBar скрыт.", NamedTextColor.GRAY)));
            }
            updateBossBars();
            return true;
        }

        // /xmh give effect <ник> <эффект>   |   /xmh give hp <ник>
        if (sub.equals("give")) {
            if (!(sender instanceof Player from)) {
                sender.sendMessage("Передача эффектов доступна только игроку.");
                return true;
            }
            if (args.length < 2) {
                Msg.chat(from, "Использование: /xmh give effect <ник> <эффект> "
                        + "или /xmh give hp <ник>");
                return true;
            }
            String kind = args[1].toLowerCase();
            if (kind.equals("hp") || kind.equals("hearts") || kind.equals("сердца")) {
                if (args.length < 3) {
                    Msg.chat(from, "Использование: /xmh give hp <ник>");
                    return true;
                }
                Player to = Bukkit.getPlayerExact(args[2]);
                if (to == null) {
                    Msg.chat(from, "Игрок " + args[2] + " не в сети.", NamedTextColor.RED);
                    return true;
                }
                effectTransfer(from, to, "hearts");
                return true;
            }
            if (args.length < 4) {
                Msg.chat(from, "Использование: /xmh give effect <ник> <эффект>");
                return true;
            }
            Player to = Bukkit.getPlayerExact(args[2]);
            if (to == null) {
                Msg.chat(from, "Игрок " + args[2] + " не в сети.", NamedTextColor.RED);
                return true;
            }
            effectTransfer(from, to, args[3]);
            return true;
        }

        // Старая форма: /xmh effect give <эффект|hearts> <ник>
        if (sub.equals("effect")) {
            if (!(sender instanceof Player from)) {
                sender.sendMessage("Команда только для игрока.");
                return true;
            }
            if (args.length < 4) {
                Msg.chat(from, "Использование: /xmh give effect <ник> <эффект>");
                return true;
            }
            Player to = Bukkit.getPlayerExact(args[3]);
            if (to == null) {
                Msg.chat(from, "Игрок " + args[3] + " не в сети.", NamedTextColor.RED);
                return true;
            }
            effectTransfer(from, to, args[2]);
            return true;
        }

        // /xmh guide — получить книгу-гайд (админ может выдать другому)
        if (sub.equals("guide")) {
            if (guideBook == null) {
                sender.sendMessage("Книга-гайд выключена (guide.yml).");
                return true;
            }
            Player target;
            if (args.length > 1 && (!(sender instanceof Player) || sender.isOp()
                    || sender.hasPermission("xmusyahard.admin"))) {
                target = Bukkit.getPlayerExact(args[1]);
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage("Использование: /xmh guide <ник>");
                return true;
            }
            if (target == null) {
                sender.sendMessage("Игрок не в сети.");
                return true;
            }
            guideBook.reloadAndGive(target);
            return true;
        }

        boolean admin = !(sender instanceof Player p) || p.hasPermission("xmusyahard.admin")
                || sender.isOp();
        if (!admin) {
            sender.sendMessage(Msg.prefix().append(Msg.c("Недостаточно прав.", NamedTextColor.RED)));
            return true;
        }

        switch (sub) {
            case "skip" -> {
                World w = overworld();
                if (w != null) {
                    w.setFullTime(w.getFullTime() + 24000L);
                    lastDaytime = (int) (w.getTime() % 24000L);
                }
                Msg.broadcast("День пропущен администратором.", NamedTextColor.GOLD);
                saveState();
            }
            case "reset" -> reset();
            case "pause" -> {
                phasesPaused = true;
                gamerule("advance_time", false);
                Msg.broadcast("Фазы и счётчик дней поставлены на паузу (время мира остановлено).",
                        NamedTextColor.GOLD);
                saveState();
            }
            case "resume" -> {
                phasesPaused = false;
                gamerule("advance_time", true);
                gamerule("advance_weather", true);
                Msg.broadcast("Фазы и счётчик дней продолжаются.", NamedTextColor.GREEN);
                saveState();
            }
            case "bm" -> {
                String act = args.length > 1 ? args[1].toLowerCase() : "";
                switch (act) {
                    case "start" -> {
                        bmForced = true;
                        bmPaused = false;
                        startBloodMoon();
                        Msg.broadcast("Кровавая луна запущена вручную.", NamedTextColor.GOLD);
                    }
                    case "stop" -> {
                        stopBloodMoon();
                        Msg.broadcast("Кровавая луна остановлена вручную.", NamedTextColor.GOLD);
                    }
                    case "pause" -> {
                        bmPaused = true;
                        Msg.broadcast("Кровавая луна на паузе (эффекты приостановлены).", NamedTextColor.GOLD);
                    }
                    case "resume" -> {
                        bmPaused = false;
                        Msg.broadcast("Кровавая луна возобновлена.", NamedTextColor.GREEN);
                    }
                    default -> sendAdminHelp(sender);
                }
                saveState();
            }
            case "phase" -> {
                if (args.length < 2) {
                    sendAdminHelp(sender);
                    return true;
                }
                int idx = Phases.indexOfKey(args[1]);
                if (idx < 0) {
                    sender.sendMessage(Msg.prefix().append(Msg.c(
                            "Неизвестная фаза. Доступно: wood, stone, iron, diamond, nether, "
                                    + "golden_food, villagers, beacons, netherite, endgame.",
                            NamedTextColor.RED)));
                    return true;
                }
                jumpToPhase(idx);
            }
            case "end" -> {
                if (endManager == null) {
                    sender.sendMessage(Msg.prefix().append(Msg.c(
                            "Энд-механики выключены в config.yml (end.enabled).", NamedTextColor.RED)));
                    return true;
                }
                String act = args.length > 1 ? args[1].toLowerCase() : "status";
                switch (act) {
                    case "locate" -> endManager.ensureStronghold();
                    case "scan" -> {
                        endPortal.repair();
                        endPortal.forceScan();
                    }
                    case "reset" -> endPortal.resetPortal();
                    default -> {
                        Location l = endManager.strongholdLocation();
                        sender.sendMessage(Msg.prefix().append(Msg.c("Энд:", NamedTextColor.GOLD)));
                        sender.sendMessage(Component.text("Крепость: ", NamedTextColor.GRAY)
                                .append(Component.text(l == null ? "не выбрана"
                                        : l.getBlockX() + ", " + l.getBlockZ(), NamedTextColor.AQUA)));
                        sender.sendMessage(Component.text("Рамок найдено: ", NamedTextColor.GRAY)
                                .append(Component.text(String.valueOf(endState.frames.size()),
                                        NamedTextColor.YELLOW))
                                .append(Component.text(", закрыто: ", NamedTextColor.GRAY))
                                .append(Component.text(String.valueOf(endState.filledCount()),
                                        NamedTextColor.GREEN)));
                    }
                }
            }
            case "reload" -> {
                loadConfig();
                applyRules();
                sender.sendMessage(Msg.prefix().append(Msg.c("Конфиг перечитан.", NamedTextColor.GREEN)));
            }
            default -> sendAdminHelp(sender);
        }
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(Msg.prefix().append(Msg.c("Админ-команды:", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("/xmh skip", NamedTextColor.YELLOW)
                .append(Msg.c(" — пропустить день", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/xmh reset", NamedTextColor.YELLOW)
                .append(Msg.c(" — сброс к дню 0", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/xmh pause | /xmh resume", NamedTextColor.YELLOW)
                .append(Msg.c(" — пауза фаз", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/xmh bm start|stop|pause|resume", NamedTextColor.YELLOW)
                .append(Msg.c(" — Кровавая луна", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/xmh phase <name>", NamedTextColor.YELLOW)
                .append(Msg.c(" — установить фазу", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/xmh end status|locate|scan|reset", NamedTextColor.YELLOW)
                .append(Msg.c(" — крепость/портал Энда", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/xmh give effect <ник> <эффект>", NamedTextColor.YELLOW)
                .append(Msg.c(" — передать свой эффект (остаток времени сохраняется)",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/xmh give hp <ник>", NamedTextColor.YELLOW)
                .append(Msg.c(" — передать 1 бонусное сердце", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/xmh guide [ник]", NamedTextColor.YELLOW)
                .append(Msg.c(" — выдать книгу-гайд", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/xmh reload", NamedTextColor.YELLOW)
                .append(Msg.c(" — перечитать config.yml", NamedTextColor.GRAY)));
    }

    /** Передача положительного эффекта (или сердца) другому игроку. */
    private void effectTransfer(Player from, Player to, String what) {
        if (what.equalsIgnoreCase("hearts") || what.equalsIgnoreCase("сердца")
                || what.equalsIgnoreCase("сердце")) {
            int have = stateHearts(from);
            if (have <= 0) {
                Msg.chat(from, "У вас нет бонусных сердец (их дают за рамки портала).",
                        NamedTextColor.RED);
                return;
            }
            heartsTransfer(from, to, 1);
            Msg.chat(from, Msg.c("Вы передали ", NamedTextColor.GRAY)
                    .append(Msg.c("1 сердце", NamedTextColor.RED))
                    .append(Msg.c(" игроку " + to.getName() + ".", NamedTextColor.GRAY)));
            Msg.chat(to, Msg.c("Вам передали ", NamedTextColor.GRAY)
                    .append(Msg.c("1 сердце", NamedTextColor.RED))
                    .append(Msg.c(" (от " + from.getName() + ")", NamedTextColor.GRAY)));
            return;
        }

        PotionEffectType type = matchEffect(what);
        if (type == null) {
            Msg.chat(from, "Неизвестный эффект. Примеры: haste, speed, strength, "
                    + "resistance, regeneration, fire_resistance, absorption, "
                    + "night_vision, jump_boost, luck.", NamedTextColor.RED);
            return;
        }
        PotionEffect mine = null;
        for (PotionEffect eff : from.getActivePotionEffects()) {
            if (eff.getType().equals(type)) {
                mine = eff;
                break;
            }
        }
        if (mine == null) {
            Msg.chat(from, "У вас нет эффекта «" + prettyEffect(type) + "».", NamedTextColor.RED);
            return;
        }
        int left = mine.getDuration() / 20;
        if (left < 5) {
            Msg.chat(from, "Эффект почти закончился — передавать нечего.", NamedTextColor.RED);
            return;
        }
        // Только положительные эффекты
        if (!isPositiveEffect(type)) {
            Msg.chat(from, "Этот эффект отрицательный — передавать нельзя.", NamedTextColor.RED);
            return;
        }
        // Постоянный бафф (за починку рамки и т.п.) переносим вместе с эффектом,
        // иначе он мгновенно вернётся бывшему владельцу (EndManager перевыдаёт).
        String buffKey = type.getKey().getKey();
        long[] buff = endManager == null ? null : endManager.takeBuff(from, buffKey);
        from.removePotionEffect(type);
        to.addPotionEffect(new PotionEffect(type, left * 20, mine.getAmplifier(),
                true, true, true));
        if (buff != null && endManager != null) {
            endManager.giveBuff(to, buffKey, buff);
        }
        Msg.chat(from, Msg.c("Вы передали ", NamedTextColor.GRAY)
                .append(Msg.c(prettyEffect(type), NamedTextColor.GREEN))
                .append(Msg.c(" игроку " + to.getName() + ".", NamedTextColor.GRAY)));
        Msg.chat(to, Msg.c("Вам передали ", NamedTextColor.GRAY)
                .append(Msg.c(prettyEffect(type), NamedTextColor.GREEN))
                .append(Msg.c(" (от " + from.getName() + ")", NamedTextColor.GRAY)));
    }

    private static PotionEffectType matchEffect(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        try {
            return org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(n));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String prettyEffect(PotionEffectType type) {
        String s = type.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isPositiveEffect(PotionEffectType t) {
        return switch (t.getKey().getKey()) {
            case "speed", "haste", "strength", "jump_boost", "regeneration",
                 "resistance", "fire_resistance", "water_breathing",
                 "invisibility", "night_vision", "health_boost", "absorption",
                 "saturation", "luck", "slow_falling", "conduit_power",
                 "dolphins_grace", "hero_of_the_village", "instant_health",
                 "instant_damage" -> true;
            default -> false;
        };
    }

    private int stateHearts(Player p) {
        return endState == null ? 0 : endState.bonusHearts.getOrDefault(p.getUniqueId(), 0);
    }

    private void heartsTransfer(Player from, Player to, int count) {
        if (endState == null || endManager == null) {
            return;
        }
        int have = endState.bonusHearts.getOrDefault(from.getUniqueId(), 0);
        int take = Math.min(have, count);
        if (take <= 0) {
            return;
        }
        endState.bonusHearts.put(from.getUniqueId(), have - take);
        endState.bonusHearts.merge(to.getUniqueId(), take, Integer::sum);
        endState.save();
        endManager.applyBonusHearts(from);
        endManager.applyBonusHearts(to);
    }

    public void reset() {
        // Сбрасываем энд-механики (рамки, глаза, баффы, сердца, проклятия)
        if (endPortal != null) {
            endPortal.resetAll();
        }
        // Снимаем все наложенные эффекты с игроков
        for (Player p : players()) {
            for (PotionEffect eff : new java.util.ArrayList<>(p.getActivePotionEffects())) {
                p.removePotionEffect(eff.getType());
            }
        }
        totalTicks = 0L;
        currentDay = 0;
        prevDay = 0;
        phase = 0;
        phasesPaused = false;
        autoPaused = false;
        emptyTicks = 0;
        bmActive = false;
        bmForced = false;
        bmPaused = false;
        borderSize = borderStart;
        for (World w : Bukkit.getWorlds()) {
            w.setFullTime(1000L);
            w.setStorm(false);
            w.setThundering(false);
        }
        applyBorder(borderStart, 0L);
        World w = overworld();
        lastDaytime = 1000;
        applyRules();
        syncRecipesAll();
        Msg.broadcast("Прогресс сброшен (день 0, фаза wood, барьер " + borderStart + ").",
                NamedTextColor.RED);
        saveState();
    }

    // ------------------------------------------------------------------ геттеры для меню
    public int daysToBloodMoon() {
        if (bmInterval <= 0) {
            return 0;
        }
        int rem = currentDay % bmInterval;
        return rem == 0 ? 0 : bmInterval - rem;
    }

    public int deathsOf(Player p) {
        return deaths.getOrDefault(p.getUniqueId(), 0);
    }

    public int bmSurvivedOf(Player p) {
        return bmSurvived.getOrDefault(p.getUniqueId(), 0);
    }

    public boolean warnVisible() {
        return warnVisible;
    }
}
