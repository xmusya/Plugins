package ru.xmusya.hard;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Все настраиваемые тексты плагина (messages.yml).
 *
 * <p>Файл лежит в {@code plugins/xmusya-hard/messages.yml}. Его можно
 * переводить на любой язык и менять как угодно: недостающие ключи
 * дописываются из встроенного образца при старте (копия старого файла —
 * {@code messages.yml.bak}), а если ключа нет совсем — используется
 * значение по умолчанию из кода.</p>
 *
 * <p>Подстановки: {@code {0}}, {@code {1}}, ... Цвета: {@code &a}-{@code &f},
 * {@code &l}, {@code &r} (как в config.yml).</p>
 */
public final class Messages {

    private static YamlConfiguration yml = new YamlConfiguration();
    private static boolean loaded = false;

    private Messages() {
    }

    /** Читает/дополняет messages.yml. Вызывать при старте и /xmh reload. */
    /**
     * Без JavaPlugin — для тестов и для случаев, когда нужно прочитать
     * уже готовый файл. Файл должен существовать.
     */
    public static void load(File folder) {
        if (folder == null) {
            return;
        }
        File file = new File(folder, "messages.yml");
        if (!file.isFile()) {
            yml = new YamlConfiguration();
            loaded = true;
            return;
        }
        try {
            yml = YamlConfiguration.loadConfiguration(file);
        } catch (RuntimeException ex) {
            yml = new YamlConfiguration();
        }
        loaded = true;
    }

    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.isFile()) {
            try {
                plugin.saveResource("messages.yml", false);
            } catch (RuntimeException ignored) {
            }
        }
        if (file.isFile()) {
            int added = ConfigMerge.merge(plugin.getDataFolder(), "messages.yml",
                    plugin.getResource("messages.yml"));
            if (added > 0) {
                plugin.getLogger().info("В messages.yml добавлено новых строк: " + added
                        + " (старые переводы сохранены, копия — messages.yml.bak).");
            }
            try {
                yml = YamlConfiguration.loadConfiguration(file);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("messages.yml: " + ex.getMessage());
                yml = new YamlConfiguration();
            }
        } else {
            yml = new YamlConfiguration();
        }
        loaded = true;
    }

    /** Значение ключа или встроенное значение по умолчанию. */
    public static String raw(String key, String def) {
        if (!loaded || yml == null) {
            return def;
        }
        String s = yml.getString(key);
        return s == null ? def : s;
    }

    /** Значение ключа с подстановкой {0}, {1}, ... */
    public static String format(String key, String def, Object... args) {
        String s = raw(key, def);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                s = s.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        return s;
    }
}
