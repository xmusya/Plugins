package ru.xmusya.hard;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Единая точка форматирования сообщений.
 * Все тексты — русские, с настраиваемым префиксом [XMH].
 */
public final class Msg {

    /** Текущий префикс (из config.yml, ключ prefix). */
    private static Component prefix = LegacyComponentSerializer.legacyAmpersand()
            .deserialize("&c[XMH] ");

    private Msg() {
    }

    /**
     * Устанавливает префикс из конфига. Поддерживаются коды &amp;a-f, &amp;l, &amp;r
     * и MiniMessage-подобные теги (&lt;red&gt;…&lt;/red&gt;).
     */
    public static void setPrefix(String raw) {
        if (raw == null || raw.isEmpty()) {
            prefix = LegacyComponentSerializer.legacyAmpersand().deserialize("&c[XMH] ");
            return;
        }
        prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(miniToLegacy(raw));
    }

    /** Простейший перевод MiniMessage-тегов в legacy-коды. */
    private static String miniToLegacy(String s) {
        return s.replace("<black>", "&0").replace("</black>", "&r")
                .replace("<dark_blue>", "&1").replace("</dark_blue>", "&r")
                .replace("<dark_green>", "&2").replace("</dark_green>", "&r")
                .replace("<dark_aqua>", "&3").replace("</dark_aqua>", "&r")
                .replace("<dark_red>", "&4").replace("</dark_red>", "&r")
                .replace("<dark_purple>", "&5").replace("</dark_purple>", "&r")
                .replace("<gold>", "&6").replace("</gold>", "&r")
                .replace("<gray>", "&7").replace("</gray>", "&r")
                .replace("<dark_gray>", "&8").replace("</dark_gray>", "&r")
                .replace("<blue>", "&9").replace("</blue>", "&r")
                .replace("<green>", "&a").replace("</green>", "&r")
                .replace("<aqua>", "&b").replace("</aqua>", "&r")
                .replace("<red>", "&c").replace("</red>", "&r")
                .replace("<light_purple>", "&d").replace("</light_purple>", "&r")
                .replace("<yellow>", "&e").replace("</yellow>", "&r")
                .replace("<white>", "&f").replace("</white>", "&r")
                .replace("<bold>", "&l").replace("</bold>", "&r");
    }

    public static Component c(String text) {
        return Component.text(text);
    }

    public static Component c(String text, NamedTextColor color) {
        return Component.text(text, color);
    }

    public static Component c(String text, TextColor color) {
        return Component.text(text, color);
    }

    public static Component bold(String text, NamedTextColor color) {
        return Component.text(text, color, TextDecoration.BOLD);
    }

    public static Component prefix() {
        return prefix;
    }

    public static void chat(Player p, Component body) {
        p.sendMessage(prefix().append(body));
    }

    public static void chat(Player p, String text) {
        chat(p, c(text, NamedTextColor.GRAY));
    }

    public static void chat(Player p, String text, NamedTextColor color) {
        chat(p, c(text, color));
    }

    public static void action(Player p, String text) {
        p.sendActionBar(Component.text(text, NamedTextColor.RED));
    }

    public static void action(Player p, Component text) {
        p.sendActionBar(text);
    }

    public static void broadcast(String text, NamedTextColor color) {
        Component msg = prefix().append(legacy(text, color));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getScoreboardTags().contains("bot")) {
                p.sendMessage(msg);
            }
        }
        Bukkit.getConsoleSender().sendMessage(
                PlainTextComponentSerializer.plainText().serialize(msg));
    }

    public static void broadcastRaw(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getScoreboardTags().contains("bot")) {
                p.sendMessage(msg);
            }
        }
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    /** Строка с &amp;-кодами, покрашенная в цвет по умолчанию. */
    private static Component legacy(String text, NamedTextColor color) {
        Component base = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        if (color == null) {
            return base;
        }
        return Component.empty().color(color).append(base);
    }

    public static Sound villagerNo() {
        return Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 1.0f, 1.0f);
    }

    public static Sound witherSpawn() {
        return Sound.sound(Key.key("entity.wither.spawn"), Sound.Source.MASTER, 1.0f, 1.0f);
    }

    public static Sound click() {
        return Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 1.0f, 1.0f);
    }
}
