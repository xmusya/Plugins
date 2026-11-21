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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Книга-гайд (WRITTEN_BOOK). Выдаётся игроку один раз при первом входе.
 * Текст и оформление берутся из guide.yml (в корне папки plugins или в
 * папке плагина).
 */
public final class GuideBook implements Listener {

    private final XmusyaHard plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    private boolean enabled = true;
    private String title = "&c[XMH] &6Гайд хардкора";
    private String author = "&7xmusya-hard";
    private String onceKey = "xmh_guide_book_given";
    private boolean dropIfFull = true;
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
                + ", страниц " + pages.size() + " (" + f.getName() + ").");
    }

    private NamespacedKey key() {
        return new NamespacedKey(plugin, onceKey);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled || plugin.isBot(e.getPlayer())) {
            return;
        }
        give(e.getPlayer());
    }

    /** Выдаёт книгу, если игрок её ещё не получал. */
    public boolean give(Player p) {
        if (!enabled) {
            return false;
        }
        try {
            if (p.getPersistentDataContainer().has(key(), PersistentDataType.BYTE)) {
                return false;
            }
        } catch (RuntimeException ignored) {
        }
        ItemStack book = build();
        if (book == null) {
            return false;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(book);
        if (!left.isEmpty() && dropIfFull) {
            for (ItemStack rest : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
        try {
            p.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
        } catch (RuntimeException ignored) {
        }
        Msg.chat(p, "Вам выдана книга-гайд. Откройте её в инвентаре.");
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
        List<Component> comps = new ArrayList<>(pages.size());
        for (String page : pages) {
            comps.add(legacy.deserialize(page));
        }
        meta.pages(comps);
        item.setItemMeta(meta);
        return item;
    }

    /** Для консольной команды /xmh guide <ник> (перечитать и выдать заново). */
    public void reloadAndGive(Player p) {
        load();
        try {
            p.getPersistentDataContainer().remove(key());
        } catch (RuntimeException ignored) {
        }
        give(p);
    }
}
