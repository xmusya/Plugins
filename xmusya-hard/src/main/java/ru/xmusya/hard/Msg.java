package ru.xmusya.hard;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Единая точка форматирования сообщений.
 * Все тексты — русские, с префиксом [xmusya-hard].
 */
public final class Msg {

    private Msg() {
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
        return Component.text("[xmusya-hard] ", NamedTextColor.RED, TextDecoration.BOLD);
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
        Component msg = prefix().append(c(text, color));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getScoreboardTags().contains("bot")) {
                p.sendMessage(msg);
            }
        }
        Bukkit.getConsoleSender().sendMessage("[xmusya-hard] " + text);
    }

    public static void broadcastRaw(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getScoreboardTags().contains("bot")) {
                p.sendMessage(msg);
            }
        }
        Bukkit.getConsoleSender().sendMessage(msg);
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
