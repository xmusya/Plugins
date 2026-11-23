package ru.xmusya.hard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Книга-гайд (WRITTEN_BOOK).
 * Текст и оформление берутся из guide.yml (в корне папки plugins или в
 * папке плагина).
 */
public final class GuideBook implements Listener {

    private static final long COOLDOWN_MILLIS = 120_000L; // 2 минуты

    private final XmusyaHard plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, Long> lastGiveTime = new HashMap<>();

    private boolean enabled = true;
    private String title = "&c[XMH] &6Гайд хардкора";
    private String author = "&7xmusya-hard";
    private String onceKey = "xmh_guide_book_given";
    private boolean dropIfFull = true;
    /** Выдавать книгу автоматически при входе (по умолчанию ВЫКЛ). */
    private boolean giveOnJoin = false;
    /** Ширина строки в пикселях (ванильная книга: TEXT_WIDTH = 114). */
    private int pageWidthPx = 112;
    /** Запасной вариант: ширина в символах (если page-width-px <= 0). */
    private int pageWidth = 19;
    /** Максимум строк на странице: 128 px / 9 px = 14 строк (ваниль). */
    private int pageLines = 14;
    private final List<String> pages = new ArrayList<>();

    public GuideBook(XmusyaHard plugin) {
        this.plugin = plugin;
        load();
    }

    /** Ищет guide.yml: сначала plugins/guide.yml, потом plugins/xmusya-hard/guide.yml. */
    private File guideFile() {
        File parent = plugin.getDataFolder().getParentFile();
        if (parent != null) {
            File root = new File(parent, "guide.yml");
            if (root.isFile()) {
                return root;
            }
        }
        return new File(plugin.getDataFolder(), "guide.yml");
    }

    public void load() {
        File f = guideFile();
        if (!f.isFile()) {
            // Кладём встроенный образец в папку плагина
            try {
                plugin.saveResource("guide.yml", false);
            } catch (RuntimeException ignored) {
            }
            f = new File(plugin.getDataFolder(), "guide.yml");
        }
        if (!f.isFile()) {
            plugin.getLogger().warning("guide.yml не найден — книга-гайд не будет выдаваться.");
            enabled = false;
            return;
        }
        YamlConfiguration y;
        try {
            y = YamlConfiguration.loadConfiguration(f);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Не удалось прочитать guide.yml: " + ex.getMessage());
            enabled = false;
            return;
        }
        enabled = y.getBoolean("book.enabled", true);
        title = y.getString("book.title", title);
        author = y.getString("book.author", author);
        onceKey = y.getString("book.give-once-key", onceKey);
        dropIfFull = y.getBoolean("book.drop-if-inventory-full", true);
        giveOnJoin = y.getBoolean("book.give-on-join", false);
        pageWidthPx = y.getInt("book.page-width-px", pageWidthPx);
        pageWidth = Math.max(10, y.getInt("book.page-width", pageWidth));
        pageLines = Math.max(1, y.getInt("book.page-lines", pageLines));
        pages.clear();
        pages.addAll(y.getStringList("book.pages"));
        if (pages.isEmpty()) {
            // На случай, если pages записан как список строк в другом отступе
            for (Object o : y.getList("book.pages", List.of())) {
                if (o != null) {
                    pages.add(String.valueOf(o));
                }
            }
        }
        plugin.getLogger().info("Книга-гайд: " + (enabled ? "включена" : "выключена")
                + ", страниц " + pages.size() + " (" + f.getName() + ")"
                + (enabled && !giveOnJoin ? ", выдача при входе ВЫКЛЮЧЕНА" : "")
                + ".");
    }

    private NamespacedKey key() {
        return new NamespacedKey(plugin, onceKey);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled || !giveOnJoin || plugin.isBot(e.getPlayer())) {
            return; // книга не выдаётся автоматически — только по /xmh guide
        }
        give(e.getPlayer());
    }

    /** Проверяет, совпадает ли книга по названию и автору с текущей книгой-гайдом. */
    private boolean isMatchingGuideBook(ItemStack stack, String expectedTitleSerialized, String expectedAuthorSerialized) {
        if (stack == null || stack.getType() != Material.WRITTEN_BOOK || !stack.hasItemMeta()) {
            return false;
        }
        if (!(stack.getItemMeta() instanceof BookMeta bm)) {
            return false;
        }
        if (!bm.hasTitle() || !bm.hasAuthor()) {
            return false;
        }
        String actualTitle = legacy.serialize(bm.title());
        String actualAuthor = legacy.serialize(bm.author());
        return expectedTitleSerialized.equals(actualTitle) && expectedAuthorSerialized.equals(actualAuthor);
    }

    /** Удаляет из инвентаря игрока все старые книги-гайды с текущим названием и автором. */
    private void removeOldGuideBooks(Player p) {
        String expectedTitle = legacy.serialize(legacy.deserialize(title));
        String expectedAuthor = legacy.serialize(legacy.deserialize(author));
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMatchingGuideBook(contents[i], expectedTitle, expectedAuthor)) {
                p.getInventory().setItem(i, null);
            }
        }
    }

    /** Выдаёт книгу с кулдауном в 2 минуты, удаляя старые книги по совпадению названия и автора. */
    public boolean give(Player p) {
        if (!enabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long prev = lastGiveTime.get(p.getUniqueId());
        if (prev != null) {
            long elapsed = now - prev;
            if (elapsed < COOLDOWN_MILLIS) {
                long leftSec = (COOLDOWN_MILLIS - elapsed + 999L) / 1000L;
                Msg.chat(p, Messages.format("guide.cooldown",
                        "Подождите ещё {0} сек. перед повторным получением книги-гайда.", leftSec));
                return false;
            }
        }

        ItemStack book = build();
        if (book == null) {
            return false;
        }

        removeOldGuideBooks(p);

        Map<Integer, ItemStack> left = p.getInventory().addItem(book);
        if (!left.isEmpty() && dropIfFull) {
            for (ItemStack rest : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
        lastGiveTime.put(p.getUniqueId(), now);
        try {
            p.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
        } catch (RuntimeException ignored) {
        }
        Msg.chat(p, Messages.raw("guide.received",
                "Вам выдана книга-гайд. Откройте её в инвентаре."));
        if (plugin.achievements != null) {
            plugin.achievements.onGuide(p);
        }
        return true;
    }

    /** Собирает книгу из guide.yml. */
    public ItemStack build() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return null;
        }
        meta.title(legacy.deserialize(title));
        meta.author(legacy.deserialize(author));
        List<Component> comps = new ArrayList<>();
        for (String chunk : paginateRaw(pages, pageWidthPx, pageWidth, pageLines)) {
            comps.add(legacy.deserialize(chunk));
        }
        meta.pages(comps);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Разбивает страницы guide.yml на реальные страницы книги.
     * Строки переносятся по ШИРИНЕ В ПИКСЕЛЯХ (как в ванили: 114 px, 14 строк),
     * поэтому страница заполняется полностью и текст не обрезается.
     */
    static List<String> paginateRaw(List<String> pages, int widthPx, int widthChars,
                                    int pageLines) {
        List<String> result = new ArrayList<>();
        for (String page : pages) {
            List<String> lines = new ArrayList<>();
            for (String raw : page.split("\n", -1)) {
                wrapLine(raw, widthPx, widthChars, lines);
            }
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            if (lines.isEmpty()) {
                continue;
            }
            // Заполняем страницы подряд, без «равных частей»: иначе половина
            // страницы оставалась пустой.
            for (int i = 0; i < lines.size(); i += pageLines) {
                result.add(String.join("\n",
                        lines.subList(i, Math.min(i + pageLines, lines.size()))));
            }
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    /**
     * Переносит одну строку по словам. Ширина считается в пикселях по таблице
     * ванильного шрифта (ascii.png + nonlatin_european.png), жирный текст
     * учитывается (+1 px на символ). Стиль (&2, &l ...) запоминается и
     * повторяется в начале новой строки, поэтому цвет не теряется.
     */
    private static void wrapLine(String line, int widthPx, int widthChars, List<String> out) {
        if (line.isEmpty()) {
            out.add("");
            return;
        }
        StringBuilder plain = new StringBuilder();
        List<Integer> src = new ArrayList<>();
        List<String> styleAt = new ArrayList<>();
        String style = "";
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '&' && i + 1 < line.length()) {
                style = applyCode(style, Character.toLowerCase(line.charAt(i + 1)));
                i++;
                continue;
            }
            plain.append(c);
            src.add(i);
            styleAt.add(style);
        }
        int len = plain.length();
        if (len == 0) {
            out.add(style);
            return;
        }
        int startChar = 0;
        while (startChar < len) {
            int width = 0;
            int lastSpace = -1;
            int k = startChar;
            while (k < len) {
                char c = plain.charAt(k);
                boolean bold = styleAt.get(k).indexOf('l') >= 0;
                int cw = glyphWidth(c) + (bold ? 1 : 0);
                boolean fits = widthPx > 0
                        ? width + cw <= widthPx
                        : (k - startChar) < widthChars;
                if (!fits && k > startChar) {
                    break;
                }
                width += cw;
                if (c == ' ') {
                    lastSpace = k;
                }
                k++;
            }
            int end = k;
            if (k < len && lastSpace > startChar) {
                end = lastSpace;
            }
            if (end <= startChar) {
                end = startChar + 1;
            }
            String part = line.substring(src.get(startChar),
                    end < len ? src.get(end) : line.length());
            out.add((styleAt.get(startChar) + part).replaceAll("[ ]+$", ""));
            int next = end;
            while (next < len && plain.charAt(next) == ' ') {
                next++;
            }
            if (next <= startChar) {
                next = startChar + 1;
            }
            startChar = next;
        }
    }

    // Ширина глифа в пикселях (уже с межбуквенным интервалом +1) —
    // снято с ванильных текстур шрифта 26.3. Для прочих символов — 7.
    private static final int[] ASCII_W = {
 0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
 0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
 4,  2,  4,  6,  6,  6,  6,  2,  4,  4,  4,  6,  2,  6,  2,  6,
 6,  6,  6,  6,  6,  6,  6,  6,  6,  6,  2,  2,  5,  6,  5,  6,
 7,  6,  6,  6,  6,  6,  6,  6,  6,  4,  6,  6,  6,  6,  6,  6,
 6,  6,  6,  6,  6,  6,  6,  6,  6,  6,  6,  4,  6,  4,  6,  6,
 3,  6,  6,  6,  6,  6,  5,  6,  6,  2,  6,  5,  3,  6,  6,  6,
 6,  6,  6,  6,  4,  6,  6,  6,  6,  6,  6,  4,  2,  4,  7,  1
    };

    private static final int[] CYR_W = {
 7,  7,  8,  7,  6,  6,  4,  7,  6,  8,  7,  7,  7,  7,  7,  7,
 6,  6,  6,  6,  7,  6,  8,  6,  6,  7,  6,  6,  6,  6,  6,  6,
 6,  6,  6,  6,  8,  6,  7,  6,  8,  9,  7,  8,  6,  6,  8,  6,
 6,  6,  6,  5,  7,  6,  6,  6,  6,  7,  5,  6,  6,  6,  6,  6,
 6,  6,  6,  6,  6,  6,  6,  6,  6,  7,  7,  7,  6,  6,  8,  6,
 7,  7,  7,  7,  6,  6,  2,  7,  6,  8,  7,  7,  7,  7,  7,  7
    };

    private static int glyphWidth(char c) {
        if (c >= 32 && c < 127) {
            return ASCII_W[c];
        }
        if (c >= 0x400 && c <= 0x45F) {
            return CYR_W[c - 0x400];
        }
        return 7;
    }

    /** Пересчитывает текущий стиль после кода цвета/форматирования. */
    private static String applyCode(String style, char code) {
        if (code == 'r') {
            return "";
        }
        if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
            return "&" + code; // цвет сбрасывает форматирование
        }
        if (code >= 'k' && code <= 'o') {
            String add = "&" + code;
            return style.contains(add) ? style : style + add;
        }
        return style;
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '&' && i + 1 < s.length()) {
                i++;
                continue;
            }
            w++;
        }
        return w;
    }

    /** Для команды /xmh guide [ник] (перечитать и выдать заново без ожидания кулдауна). */
    public void reloadAndGive(Player p) {
        load();
        lastGiveTime.remove(p.getUniqueId());
        try {
            p.getPersistentDataContainer().remove(key());
        } catch (RuntimeException ignored) {
        }
        give(p);
    }
}
