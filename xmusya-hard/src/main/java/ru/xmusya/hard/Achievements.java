package ru.xmusya.hard;

import io.papermc.paper.datapack.Datapack;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Достижения плагина.
 *
 * <p>Сами достижения (ванильные тосты, вкладка «Достижения») лежат в датапаке
 * {@code xmusya-hard-achievements.zip}, который плагин кладёт в
 * {@code world/datapacks} при старте и включает. Плагин лишь выдаёт их
 * ({@code advancement grant ... only xmusya:<id>}) и показывает своё сообщение,
 * если датапак не загружен.</p>
 */
public final class Achievements implements Listener {

    private static final String NS = "xmusya:";
    private static final String PACK = "file/xmusya-hard-achievements.zip";

    // id -> "Название|Описание"
    private static final Map<String, String> INFO = new HashMap<>();

    static {
        info("root", "xmusya-hard", "Хардкор с прогрессией по игровым дням");
        info("first_day", "Первый день", "Дожить до 2-го дня");
        info("day_50", "Полсотни", "Дожить до 50-го дня");
        info("day_100", "Сотка", "Дожить до 100-го дня");
        info("day_200", "Ветеран", "Дожить до 200-го дня");
        info("day_365", "Год в аду", "Дожить до 365-го дня");
        info("phase_stone", "Каменный век", "Достичь фазы stone");
        info("phase_iron", "Железный век", "Достичь фазы iron");
        info("phase_diamond", "Алмазный век", "Достичь фазы diamond");
        info("phase_netherite", "Незеритовый век", "Достичь фазы netherite");
        info("walker", "Пешеход", "Пройти 10 000 блоков");
        info("miner", "Шахтёр", "Добыть 2 000 блоков");
        info("builder", "Архитектор", "Поставить 2 000 блоков");
        info("bm_first", "Кровавая луна", "Пережить первую Кровавую луну");
        info("bm_5", "Охотник на луну", "Пережить 5 Кровавых лун");
        info("bm_10", "Повелитель ночи", "Пережить 10 Кровавых лун");
        info("bm_miner", "Из шахты", "Пережить Кровавую луну ниже Y=40");
        info("bm_high", "Высотник", "Пережить Кровавую луну выше Y=120");
        info("bm_no_sleep", "Бессонница", "Провести 3 ночи без сна");
        info("bm_kills_10", "Мясник", "Убить 10 мобов за одну Кровавую луну");
        info("bm_kills_100", "Кровавая жатва", "Убить 100 мобов Кровавой луны");
        info("bm_vex", "Вексобой", "Убить 10 вексов Кровавой луны");
        info("bm_noskull", "Без черепа", "Убить визер-скелета Кровавой луны");
        info("bm_lowhp", "На волоске", "Встретить рассвет с 3 сердцами или меньше");
        info("end_first_eye", "Око Энда", "Вставить первое око в рамку портала");
        info("end_open", "Врата", "Открыть портал в Энд (12 из 12 рамок)");
        info("end_repair", "Ремонтник", "Починить сломанную рамку портала");
        info("end_repair_all", "Мастер ремонта", "Починить 12 рамок портала");
        info("end_ultra_repair", "Ультра-ремонт", "Починить рамку ультра-слома");
        info("end_dragon", "Драконоборец", "Убить дракона Энда");
        info("end_void", "Пустота отпустила", "Спастись из пустоты Энда");
        info("end_hearts_10", "Сердце Энда", "Накопить 10 бонусных сердец");
        info("end_hearts_20", "Бессмертный", "Накопить 20 бонусных сердец");
        info("end_curse", "Проклятый", "Получить проклятие портала");
        info("end_shield", "Щит дракона", "Заблокировать атаку дракона щитом");
        info("beacon_first", "Драконий маяк", "Поставить яйцо дракона на маяк");
        info("beacon_lvl4", "Полная мощность", "Собрать Драконий маяк 4 уровня");
        info("beacon_exclusive", "Неуязвимость", "Включить эксклюзивный эффект маяка");
        info("beacon_collector", "Коллекционер", "Собрать 5 разных эффектов на маяках");
        info("give_effect", "Щедрость", "Передать эффект другому игроку");
        info("give_hearts", "Дар", "Передать бонусное сердце");
        info("trade_first", "Торговец", "Совершить первую сделку с жителем");
        info("trade_50", "Магнат", "Совершить 50 сделок");
        info("guide", "Книжник", "Получить книгу-гайд");
        info("death_10", "Упрямый", "Умереть 10 раз и продолжить играть");
        info("no_death_10", "Хардкор", "Прожить 10 дней без единой смерти");
        info("secret_egg", "Яйцо не моё", "Кликнуть по яйцу дракона");
        info("secret_frame", "Не сломать", "Попытаться сломать рамку портала");
        info("secret_slept_bm", "Сон под луной", "Попытаться лечь спать во время Кровавой луны");
    }

    private static void info(String id, String title, String desc) {
        INFO.put(id, title + "|" + desc);
    }

    private static final class PA {
        final Set<String> granted = new HashSet<>();
        final Map<String, Integer> counters = new HashMap<>();
        /** Уникальные эффекты маяка, которые игрок когда-либо выбирал. */
        final Set<String> beaconEffects = new LinkedHashSet<>();
        int daysSinceDeath;
        int daysSinceSleep;
        int bmKillsNight;
        long lastSave;
    }

    private final XmusyaHard plugin;
    private boolean enabled = true;
    private final Map<UUID, PA> data = new HashMap<>();
    private final Set<String> known = new HashSet<>();
    private File file;
    private boolean packOk = false;

    public Achievements(XmusyaHard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "achievements.yml");
        this.enabled = plugin.getConfig().getBoolean("achievements.enabled", true);
        load();
        if (!enabled) {
            plugin.getLogger().info("Достижения выключены (achievements.enabled: false).");
        }
    }

    /** Ставим датапак уже после полной загрузки сервера. */
    public void start() {
        if (!enabled) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::installDatapack, 40L);
    }

    // ================================================================== файл
    private void load() {
        data.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (RuntimeException ex) {
                continue;
            }
            PA a = new PA();
            a.granted.addAll(y.getStringList(key + ".granted"));
            for (String ck : y.getConfigurationSection(key + ".counters") == null
                    ? List.<String>of() : y.getConfigurationSection(key + ".counters").getKeys(false)) {
                a.counters.put(ck, y.getInt(key + ".counters." + ck));
            }
            a.beaconEffects.addAll(y.getStringList(key + ".beacon-effects"));
            a.daysSinceDeath = y.getInt(key + ".days-since-death");
            a.daysSinceSleep = y.getInt(key + ".days-since-sleep");
            a.bmKillsNight = y.getInt(key + ".bm-kills-night");
            data.put(id, a);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PA> e : data.entrySet()) {
            String k = e.getKey().toString();
            PA a = e.getValue();
            y.set(k + ".granted", new ArrayList<>(a.granted));
            for (Map.Entry<String, Integer> c : a.counters.entrySet()) {
                y.set(k + ".counters." + c.getKey(), c.getValue());
            }
            y.set(k + ".beacon-effects", new ArrayList<>(a.beaconEffects));
            y.set(k + ".days-since-death", a.daysSinceDeath);
            y.set(k + ".days-since-sleep", a.daysSinceSleep);
            y.set(k + ".bm-kills-night", a.bmKillsNight);
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("achievements.yml: " + ex.getMessage());
        }
    }

    // ================================================================== датапак
    /** Кладёт bundled-датапак в world/datapacks и включает его. */
    /**
     * Кладёт пак в {@code <worldContainer>/<level-name>/datapacks} без
     * DatapackManager — можно вызывать из {@code onLoad()}, до загрузки мира.
     */
    public static void installPackEarly(JavaPlugin plugin) {
        if (!achievementsEnabledOnDisk(plugin)) {
            return;
        }
        File out = new File(packsDir(plugin), "xmusya-hard-achievements.zip");
        File dir = out.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        try (InputStream in = plugin.getResource("xmusya-hard-achievements.zip")) {
            if (in == null) {
                plugin.getLogger().warning("В jar нет xmusya-hard-achievements.zip —"
                        + " ачивки будут без ванильных тостов.");
                return;
            }
            boolean changed = copy(in, out);
            plugin.getLogger().info((changed ? "Датапак ачивок записан: "
                    : "Датапак ачивок уже на месте: ") + out.getPath());
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось установить датапак ачивок: " + ex.getMessage());
        }
    }

    /** Папка datapacks основного мира (имя мира из server.properties). */
    private static File packsDir(JavaPlugin plugin) {
        File container = null;
        try {
            container = Bukkit.getWorldContainer();
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (container == null) {
            File data = plugin.getDataFolder();
            container = new File(data.getParentFile().getParentFile(), ".");
        }
        String level = "world";
        File props = new File(container, "server.properties");
        if (props.isFile()) {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(props)) {
                p.load(in);
            } catch (IOException ignored) {
            }
            String l = p.getProperty("level-name");
            if (l != null && !l.isBlank()) {
                level = l;
            }
        }
        return new File(new File(container, level), "datapacks");
    }

    private static boolean achievementsEnabledOnDisk(JavaPlugin plugin) {
        File cfg = new File(plugin.getDataFolder(), "config.yml");
        if (!cfg.isFile()) {
            return true;
        }
        try {
            return YamlConfiguration.loadConfiguration(cfg)
                    .getBoolean("achievements.enabled", true);
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /** Рантайм-проверка: пак уже есть — включаем и запоминаем id ачивок. */
    private void installDatapack() {
        installPackEarly(plugin);
        try {
            Bukkit.getDatapackManager().refreshPacks();
            Datapack pack = findPack();
            if (pack == null) {
                StringBuilder sb = new StringBuilder();
                for (Datapack p : Bukkit.getDatapackManager().getPacks()) {
                    sb.append(p.getName()).append(' ');
                }
                plugin.getLogger().info("Датапак ачивок пока не виден. Известные паки: " + sb);
            } else {
                if (!pack.isEnabled()) {
                    pack.setEnabled(true);
                    plugin.getLogger().info("Датапак ачивок включён.");
                }
                packOk = true;
            }
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("Не удалось включить датапак ачивок: " + ex.getMessage());
        }
        readKnown();
        // Paper может применить пак не мгновенно — перечитаем позже
        Bukkit.getScheduler().runTaskLater(plugin, this::readKnown, 60L);
        Bukkit.getScheduler().runTaskLater(plugin, this::readKnown, 200L);
        Bukkit.getScheduler().runTaskLater(plugin, this::readKnown, 400L);
    }

    private Datapack findPack() {
        Datapack byName = Bukkit.getDatapackManager().getPack(PACK);
        if (byName != null) {
            return byName;
        }
        for (Datapack p : Bukkit.getDatapackManager().getPacks()) {
            if (p.getName().endsWith("xmusya-hard-achievements.zip")) {
                return p;
            }
        }
        return null;
    }

    private void readKnown() {
        known.clear();
        for (String id : INFO.keySet()) {
            if (Bukkit.getAdvancement(NamespacedKey.fromString(NS + id)) != null) {
                known.add(id);
            }
        }
        plugin.getLogger().info("Ачивки: загружено " + known.size() + "/" + INFO.size()
                + (packOk ? " (датапак активен)" : ""));
    }

    private static boolean sameContent(File a, File b) throws IOException {
        if (a.length() != b.length()) {
            return false;
        }
        return java.util.Arrays.equals(java.nio.file.Files.readAllBytes(a.toPath()),
                java.nio.file.Files.readAllBytes(b.toPath()));
    }

    private static boolean copy(InputStream in, File out) throws IOException {
        File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
        if (out.isFile() && sameContent(out, tmp)) {
            // содержимое не изменилось — не трогаем (иначе Paper перезагрузит пак)
            tmp.delete();
            return false;
        }
        if (out.exists()) {
            out.delete();
        }
        return tmp.renameTo(out);
    }

    // ================================================================== выдача
    private PA pa(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new PA());
    }

    /** Выдаёт ачивку (идемпотентно). */
    public void grant(Player p, String id) {
        if (!enabled) {
            return;
        }
        PA a = pa(p);
        if (a.granted.contains(id)) {
            return;
        }
        a.granted.add(id);
        if (known.contains(id)) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "advancement grant " + p.getName() + " only " + NS + id);
            } catch (RuntimeException ignored) {
            }
        }
        String[] parts = INFO.getOrDefault(id, "Достижение|").split("\\|", 2);
        String title = parts.length > 0 ? parts[0] : id;
        p.playSound(p.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
        Msg.chat(p, Msg.legacy(Messages.format("achievements.granted",
                "&6Достижение: &e{0}", title)));
        save();
    }

    // ================================================================== счётчики
    public void add(Player p, String key, int delta) {
        pa(p).counters.merge(key, delta, Integer::sum);
        check(p);
    }

    public int count(Player p, String key) {
        return pa(p).counters.getOrDefault(key, 0);
    }

    // ================================================================== события-триггеры
    /** Вызывается при смене дня у игрока. */
    public void onDay(Player p) {
        PA a = pa(p);
        a.daysSinceDeath++;
        a.daysSinceSleep++;
        if (plugin.bmActive) {
            a.bmKillsNight = 0;
        }
        // статистика пешехода
        try {
            int cm = p.getStatistic(Statistic.WALK_ONE_CM);
            a.counters.put("walk_cm", cm);
        } catch (RuntimeException ignored) {
        }
        check(p);
    }

    /** Вызывается при смерти игрока. */
    public void onDeath(Player p) {
        PA a = pa(p);
        a.daysSinceDeath = 0;
        a.counters.merge("deaths", 1, Integer::sum);
        check(p);
    }

    /** Игрок лёг спать. */
    public void onSleep(Player p) {
        pa(p).daysSinceSleep = 0;
    }

    /** Игрок убил моба (bmKill — во время Кровавой луны). */
    public void onKill(Player p, EntityType type, boolean bmKill) {
        PA a = pa(p);
        if (bmKill) {
            a.bmKillsNight++;
            a.counters.merge("bm_kills_total", 1, Integer::sum);
            if (a.bmKillsNight >= 10) {
                grant(p, "bm_kills_10");
            }
            if (count(p, "bm_kills_total") >= 100) {
                grant(p, "bm_kills_100");
            }
        }
        if (type == EntityType.VEX && bmKill) {
            add(p, "bm_vex_kills", 1);
            if (count(p, "bm_vex_kills") >= 10) {
                grant(p, "bm_vex");
            }
        }
        if (type == EntityType.WITHER_SKELETON && bmKill) {
            grant(p, "bm_noskull");
        }
        if (type == EntityType.ENDER_DRAGON) {
            add(p, "dragons", 1);
            grant(p, "end_dragon");
        }
    }

    /** Рассвет Кровавой луны: игрок её пережил. */
    public void onBloodMoonSurvived(Player p, double y, double health) {
        int survived = plugin.bmSurvivedOf(p) + 1;
        grant(p, "bm_first");
        if (survived >= 5) {
            grant(p, "bm_5");
        }
        if (survived >= 10) {
            grant(p, "bm_10");
        }
        if (y < plugin.bmMineY) {
            grant(p, "bm_miner");
        }
        if (y > plugin.bmHeightY) {
            grant(p, "bm_high");
        }
        if (health <= 6.0) {
            grant(p, "bm_lowhp");
        }
        if (pa(p).daysSinceSleep >= 3) {
            grant(p, "bm_no_sleep");
        }
        pa(p).bmKillsNight = 0;
        check(p);
    }

    /** Вставлено око / заполнена рамка. */
    public void onFrameFill(Player p) {
        add(p, "eyes", 1);
        grant(p, "end_first_eye");
    }

    /** Портал открыт (12/12). */
    public void onPortalOpen(Player p) {
        grant(p, "end_open");
    }

    /** Рамка починена. */
    public void onRepair(Player p, boolean ultra) {
        add(p, "repairs", 1);
        grant(p, "end_repair");
        if (count(p, "repairs") >= 12) {
            grant(p, "end_repair_all");
        }
        if (ultra) {
            grant(p, "end_ultra_repair");
        }
    }

    public void onVoidRescue(Player p) {
        add(p, "void_rescues", 1);
        grant(p, "end_void");
    }

    public void onCurse(Player p) {
        add(p, "curses", 1);
        grant(p, "end_curse");
    }

    public void onShieldBlock(Player p) {
        add(p, "shield_blocks", 1);
        grant(p, "end_shield");
    }

    /** Драконий маяк: создан / улучшен / выбран эффект. */
    public void onBeacon(Player p, int level, boolean exclusive, String effectId) {
        add(p, "beacons", 1);
        grant(p, "beacon_first");
        if (level >= 4) {
            grant(p, "beacon_lvl4");
        }
        if (exclusive) {
            grant(p, "beacon_exclusive");
        }
        PA a = pa(p);
        if (effectId != null && !effectId.isEmpty()) {
            a.beaconEffects.add(effectId);
            if (a.beaconEffects.size() >= 5) {
                grant(p, "beacon_collector");
            }
        }
        save();
    }

    public void onGive(Player p, boolean hearts) {
        add(p, "gives", 1);
        grant(p, hearts ? "give_hearts" : "give_effect");
    }

    public void onTrade(Player p) {
        add(p, "trades", 1);
        grant(p, "trade_first");
        if (count(p, "trades") >= 50) {
            grant(p, "trade_50");
        }
    }

    public void onGuide(Player p) {
        add(p, "guides", 1);
        grant(p, "guide");
    }

    public void onSecretEgg(Player p) {
        grant(p, "secret_egg");
    }

    public void onSecretFrame(Player p) {
        grant(p, "secret_frame");
    }

    public void onSecretSleepBm(Player p) {
        grant(p, "secret_slept_bm");
    }

    // ================================================================== проверка
    /** Пересчитывает простые «накопительные» ачивки. */
    public void check(Player p) {
        PA a = pa(p);
        int day = plugin.currentDay;
        if (day >= 2) {
            grant(p, "first_day");
        }
        if (day >= 50) {
            grant(p, "day_50");
        }
        if (day >= 100) {
            grant(p, "day_100");
        }
        if (day >= 200) {
            grant(p, "day_200");
        }
        if (day >= 365) {
            grant(p, "day_365");
        }
        if (plugin.phase >= Phases.indexOfKey("stone")) {
            grant(p, "phase_stone");
        }
        if (plugin.phase >= Phases.indexOfKey("iron")) {
            grant(p, "phase_iron");
        }
        if (plugin.phase >= Phases.indexOfKey("diamond")) {
            grant(p, "phase_diamond");
        }
        if (plugin.phase >= Phases.indexOfKey("netherite")) {
            grant(p, "phase_netherite");
        }
        if (count(p, "blocks_mined") >= 2000) {
            grant(p, "miner");
        }
        if (count(p, "blocks_placed") >= 2000) {
            grant(p, "builder");
        }
        if (a.counters.getOrDefault("walk_cm", 0) / 100 >= 10000) {
            grant(p, "walker");
        }
        if (count(p, "deaths") >= 10) {
            grant(p, "death_10");
        }
        if (a.daysSinceDeath >= 10) {
            grant(p, "no_death_10");
        }
        if (a.daysSinceSleep >= 3) {
            grant(p, "bm_no_sleep");
        }
        int hearts = plugin.endState == null ? 0
                : plugin.endState.bonusHearts.getOrDefault(p.getUniqueId(), 0);
        if (hearts >= 10) {
            grant(p, "end_hearts_10");
        }
        if (hearts >= 20) {
            grant(p, "end_hearts_20");
        }
        if (count(p, "repairs") >= 12) {
            grant(p, "end_repair_all");
        }
        if (count(p, "repairs") >= 1) {
            grant(p, "end_repair");
        }
        if (count(p, "trades") >= 50) {
            grant(p, "trade_50");
        }
        if (count(p, "trades") >= 1) {
            grant(p, "trade_first");
        }
        if (count(p, "guides") >= 1) {
            grant(p, "guide");
        }
    }

    // ================================================================== листенеры
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        pa(p);
        check(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!(e.getPlayer() instanceof Player p)) {
            return;
        }
        add(p, "blocks_mined", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        add(e.getPlayer(), "blocks_placed", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeathEvent(PlayerDeathEvent e) {
        onDeath(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null || e.getEntity() instanceof Player) {
            return;
        }
        boolean bmKill = plugin.bmActive
                && (isHostile(e.getEntityType())
                || e.getEntity().getScoreboardTags().contains("xh_bm_flyer"));
        onKill(killer, e.getEntityType(), bmKill);
    }

    private static boolean isHostile(EntityType t) {
        return switch (t) {
            case ZOMBIE, SKELETON, STRAY, HUSK, SPIDER, CAVE_SPIDER, CREEPER,
                 PIGLIN, WITHER_SKELETON, DROWNED, VEX, PHANTOM, ENDERMAN,
                 BLAZE, GHAST, SLIME, MAGMA_CUBE, WITCH, SILVERFISH, ENDERMITE,
                 GUARDIAN, ELDER_GUARDIAN, SHULKER, PILLAGER, VINDICATOR,
                 EVOKER, RAVAGER, HOGLIN, ZOGLIN, WITHER, WARDEN, BOGGED,
                 BREEZE -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBed(PlayerBedEnterEvent e) {
        // Заснул только если BedEnterResult == OK (иначе КЛ/призраки запретили)
        if (e.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) {
            onSleep(e.getPlayer());
        }
    }

    /** Сделка с жителем: забираем результат торговли. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTradeClick(InventoryClickEvent e) {
        if (e.getInventory().getType() != InventoryType.MERCHANT) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getRawSlot() != 2) {
            return;
        }
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType().isAir()) {
            return;
        }
        onTrade(p);
    }

    /** Тик раз в 30 секунд: дешёвые накопительные проверки + автосохранение. */
    public void tick() {
        for (Player p : plugin.players()) {
            check(p);
            PA a = data.get(p.getUniqueId());
            if (a != null && System.currentTimeMillis() - a.lastSave > 30_000L) {
                a.lastSave = System.currentTimeMillis();
                save();
            }
        }
    }

    /** Все ачивки игрока (для будущего GUI). */
    public Set<String> grantedOf(Player p) {
        return new HashSet<>(pa(p).granted);
    }

    /** Список ачивок, известных серверу. */
    public List<String> allIds() {
        return new ArrayList<>(INFO.keySet());
    }

    /** Сколько ачивок вообще есть. */
    public int total() {
        return INFO.size();
    }

    /** Есть ли ачивка (для отладки/команд). */
    public boolean has(Player p, String id) {
        return pa(p).granted.contains(id);
    }

    /** Принудительная выдача (команда /xmh ach). */
    public void grantCommand(Player p, String id) {
        if (!INFO.containsKey(id)) {
            Msg.chat(p, "Неизвестная ачивка: " + id, NamedTextColor.RED);
            return;
        }
        grant(p, id);
    }

    /** Сброс всех ачивок (админ). */
    public void resetAll() {
        data.clear();
        save();
    }
}