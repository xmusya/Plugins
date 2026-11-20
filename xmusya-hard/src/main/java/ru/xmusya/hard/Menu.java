package ru.xmusya.hard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Интерактивное меню /xmh: сводка мира, личная статистика, кнопки.
 */
public final class Menu {

    private Menu() {
    }

    private static Component btn(String label, NamedTextColor color, String command, String hint) {
        return Component.text("[" + label + "] ", color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hint, NamedTextColor.GRAY)));
    }

    private static Component line() {
        return Component.text("════════ ", NamedTextColor.GOLD)
                .append(Component.text("Xmusya Hard", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" ════════", NamedTextColor.GOLD));
    }

    public static void open(XmusyaHard plugin, Player p) {
        p.sendMessage(line());

        int daysToBm = plugin.daysToBloodMoon();
        p.sendMessage(Component.text("День: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(plugin.currentDay), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("  |  До Кровавой луны: ", NamedTextColor.GRAY))
                .append(Component.text(daysToBm + " дн.", NamedTextColor.RED)));

        p.sendMessage(Component.text("Фаза: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.cfg().name,
                        Phases.COLORS[Math.min(plugin.phase, Phases.COLORS.length - 1)], TextDecoration.BOLD)));

        p.sendMessage(Component.text("Барьер: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf((int) plugin.borderSize), NamedTextColor.AQUA))
                .append(Component.text(" блоков  |  Регенерация: ", NamedTextColor.GRAY))
                .append(plugin.cfg().naturalRegeneration
                        ? Component.text("ВКЛ", NamedTextColor.GREEN)
                        : Component.text("ВЫКЛ (ультра-хардкор)", NamedTextColor.RED)));

        if (plugin.phasesPaused) {
            p.sendMessage(Component.text("Фазы: ", NamedTextColor.GRAY)
                    .append(Component.text("ПАУЗА", NamedTextColor.GOLD)));
        }
        if (plugin.autoPaused) {
            p.sendMessage(Component.text("Мир: ", NamedTextColor.GRAY)
                    .append(Component.text("АВТО-ПАУЗА (нет живых игроков)", NamedTextColor.GOLD)));
        }
        if (plugin.bmActive) {
            p.sendMessage(Component.text("Кровавая луна: ", NamedTextColor.GRAY)
                    .append(Component.text("АКТИВНА", NamedTextColor.DARK_RED, TextDecoration.BOLD)));
        }

        p.sendMessage(Component.text("Смертей: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(plugin.deathsOf(p)), NamedTextColor.WHITE))
                .append(Component.text("  |  Пережито Кровавых лун: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(plugin.bmSurvivedOf(p)), NamedTextColor.LIGHT_PURPLE)));

        p.sendMessage(Component.text("Памятка: ", NamedTextColor.GRAY)
                .append(Component.text("/tag <ник_бота> add bot", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.suggestCommand("/tag "))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Исключить бота из всех проверок", NamedTextColor.GRAY)))));

        p.sendMessage(btn("Показать/Скрыть BossBar", NamedTextColor.AQUA,
                "/xmh bar", "Личный переключатель BossBar"));

        boolean isAdmin = p.hasPermission("xmusyahard.admin") || p.isOp();
        if (plugin.endState != null) {
            String stronghold;
            if (!plugin.endState.strongholdChosen) {
                stronghold = "ещё не найдена";
            } else if (isAdmin) {
                stronghold = plugin.endState.strongholdX + ", " + plugin.endState.strongholdZ;
            } else {
                stronghold = "найдена";
            }
            p.sendMessage(Component.text("Энд: ", NamedTextColor.GRAY)
                    .append(Component.text("рамки " + plugin.endState.filledCount() + "/"
                            + plugin.endState.frames.size(), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text("  |  Крепость: ", NamedTextColor.GRAY))
                    .append(Component.text(stronghold, NamedTextColor.AQUA)));
        }

        if (!isAdmin) {
            return;
        }

        p.sendMessage(Component.text("───── Админ-панель ─────", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        p.sendMessage(btn("+1 День", NamedTextColor.GOLD, "/xmh skip", "Пропустить один день")
                .append(btn("Сброс", NamedTextColor.RED, "/xmh reset", "Сбросить прогресс к дню 0"))
                .append(btn("Пауза фаз", NamedTextColor.YELLOW, "/xmh pause", "Остановить счётчик дней"))
                .append(btn("Продолжить", NamedTextColor.GREEN, "/xmh resume", "Возобновить фазы")));

        p.sendMessage(btn("Энд", NamedTextColor.LIGHT_PURPLE, "/xmh end status", "Крепость и рамки портала")
                .append(btn("Скан", NamedTextColor.AQUA, "/xmh end scan", "Пересканировать комнату портала"))
                .append(btn("Сброс", NamedTextColor.RED, "/xmh end reset", "Сбросить рамки портала")));

        p.sendMessage(Component.text("КЛ: ", NamedTextColor.DARK_RED)
                .append(btn("Старт", NamedTextColor.RED, "/xmh bm start", "Запустить Кровавую луну"))
                .append(btn("Стоп", NamedTextColor.GRAY, "/xmh bm stop", "Остановить Кровавую луну"))
                .append(btn("Пауза", NamedTextColor.YELLOW, "/xmh bm pause", "Приостановить эффекты"))
                .append(btn("Возобновить", NamedTextColor.GREEN, "/xmh bm resume", "Возобновить эффекты")));

        String[] shortNames = {"Wood", "Stone", "Iron", "Diamond", "Nether",
                "GoldFood", "Villagers", "Beacons", "Netherite", "Endgame"};
        Component row1 = Component.empty();
        for (int i = 0; i < 5; i++) {
            row1 = row1.append(btn(shortNames[i], NamedTextColor.AQUA,
                    "/xmh phase " + Phases.KEYS[i], "Фаза: " + plugin.phases.get(i).name));
        }
        Component row2 = Component.empty();
        for (int i = 5; i < 10; i++) {
            row2 = row2.append(btn(shortNames[i], NamedTextColor.AQUA,
                    "/xmh phase " + Phases.KEYS[i], "Фаза: " + plugin.phases.get(i).name));
        }
        p.sendMessage(row1);
        p.sendMessage(row2);
    }
}
