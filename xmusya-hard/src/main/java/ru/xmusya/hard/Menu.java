package ru.xmusya.hard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Единое меню /xmh: вся информация и все действия в одном инвентаре.
 * Для консоли остаются обычные текстовые команды.
 */
public final class Menu implements Listener {

    private static final class Holder implements InventoryHolder {
        final Player player;
        Inventory inventory;

        Holder(Player player) {
            this.player = player;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public Menu() {
    }

    // ------------------------------------------------------------------ утилиты
    private static ItemStack item(Material m, String name, NamedTextColor color,
                                  List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color, TextDecoration.BOLD));
        if (lore != null && !lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(Component.text(s, NamedTextColor.GRAY));
            }
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack btn(Material m, String name, String command, String hint) {
        List<String> lore = new ArrayList<>();
        if (hint != null && !hint.isEmpty()) {
            lore.add(hint);
        }
        lore.add("Нажмите, чтобы выполнить");
        lore.add("/xmh " + command);
        return item(m, name, NamedTextColor.YELLOW, lore);
    }

    // ------------------------------------------------------------------ открытие
    public static void open(XmusyaHard plugin, Player p) {
        boolean admin = p.hasPermission("xmusyahard.admin") || p.isOp();
        Holder holder = new Holder(p);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("xmusya-hard — меню", NamedTextColor.DARK_RED,
                        TextDecoration.BOLD));
        holder.inventory = inv;

        // --- сводка
        List<String> dayLore = new ArrayList<>();
        dayLore.add("Фаза: " + plugin.cfg().name);
        dayLore.add("Барьер: " + (int) plugin.borderSize + " блоков");
        dayLore.add("До Кровавой луны: " + plugin.daysToBloodMoon() + " дн.");
        dayLore.add("Регенерация: " + (plugin.cfg().naturalRegeneration ? "ВКЛ" : "ВЫКЛ"));
        if (plugin.phasesPaused || plugin.autoPaused) {
            dayLore.add("ПАУЗА: " + (plugin.phasesPaused ? "ручная " : "")
                    + (plugin.autoPaused ? "авто" : ""));
        }
        inv.setItem(4, item(Material.CLOCK,
                "День " + plugin.currentDay, NamedTextColor.GOLD, dayLore));

        List<String> bmLore = new ArrayList<>();
        bmLore.add(plugin.bmActive ? "АКТИВНА" : "не активна");
        bmLore.add("Смертей: " + plugin.deathsOf(p));
        bmLore.add("Пережито КЛ: " + plugin.bmSurvivedOf(p));
        inv.setItem(13, item(Material.RED_BANNER, "Кровавая луна",
                plugin.bmActive ? NamedTextColor.DARK_RED : NamedTextColor.GRAY, bmLore));

        if (plugin.endState != null) {
            List<String> el = new ArrayList<>();
            el.add("Рамки: " + plugin.endState.filledCount() + "/"
                    + plugin.endState.frames.size());
            long broken = plugin.endState.frames.values().stream()
                    .filter(f -> f.broken).count();
            if (broken > 0) {
                el.add("Сломано: " + broken + " (нужна ручная починка)");
            }
            el.add(admin && plugin.endState.strongholdChosen
                    ? "Крепость: " + plugin.endState.strongholdX + ", "
                    + plugin.endState.strongholdZ
                    : "Крепость: " + (plugin.endState.strongholdChosen
                    ? "найдена" : "ещё не найдена"));
            inv.setItem(22, item(Material.ENDER_EYE, "Портал в Энд",
                    NamedTextColor.LIGHT_PURPLE, el));
        }

        // --- общие кнопки
        inv.setItem(8, btn(Material.BELL, "Показать/Скрыть BossBar", "bar",
                "Личный переключатель боссбара"));
        inv.setItem(17, btn(Material.WRITTEN_BOOK, "Книга-гайд", "guide",
                "Получить/перечитать книгу-гайд"));
        inv.setItem(26, item(Material.BREWING_STAND, "Передача эффектов",
                NamedTextColor.AQUA, List.of(
                        "/xmh give effect <ник> <эффект>",
                        "/xmh give hp <ник>",
                        "Остаток времени сохраняется")));

        if (!admin) {
            inv.setItem(0, btn(Material.BARRIER, "Закрыть", "close", null));
            p.openInventory(inv);
            return;
        }

        // --- админ: дни и паузы
        inv.setItem(28, btn(Material.LIME_DYE, "+1 день", "skip", "Пропустить день"));
        inv.setItem(29, btn(Material.TNT, "Сброс", "reset", "Сброс к дню 0"));
        inv.setItem(30, btn(Material.YELLOW_DYE, "Пауза фаз", "pause", "Остановить время"));
        inv.setItem(31, btn(Material.GREEN_DYE, "Продолжить", "resume", "Возобновить фазы"));

        // --- Кровавая луна
        inv.setItem(33, btn(Material.RED_DYE, "КЛ: старт", "bm start", "Запустить"));
        inv.setItem(34, btn(Material.GRAY_DYE, "КЛ: стоп", "bm stop", "Остановить"));
        inv.setItem(35, btn(Material.ORANGE_DYE, "КЛ: пауза", "bm pause", "Приостановить"));
        inv.setItem(36, btn(Material.LIME_DYE, "КЛ: продолжить", "bm resume", "Возобновить"));

        // --- Энд
        inv.setItem(38, btn(Material.ENDER_EYE, "Энд: статус", "end status", "Крепость и рамки"));
        inv.setItem(39, btn(Material.ENDER_PEARL, "Энд: скан", "end scan", "Пересканировать портал"));
        inv.setItem(40, btn(Material.END_PORTAL_FRAME, "Энд: сброс", "end reset", "Сбросить портал"));

        inv.setItem(42, btn(Material.COMPARATOR, "Перечитать config.yml", "reload", null));

        // --- фазы
        Material[] icons = {Material.OAK_SAPLING, Material.STONE, Material.IRON_INGOT,
                Material.DIAMOND, Material.NETHERRACK, Material.GOLDEN_APPLE,
                Material.EMERALD, Material.BEACON, Material.NETHERITE_INGOT,
                Material.ENDER_EYE};
        for (int i = 0; i < 10; i++) {
            PhaseCfg c = plugin.phases.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(c.name);
            lore.add("С дня " + c.day);
            lore.add("Нажмите, чтобы перейти");
            int slot = i < 9 ? 45 + i : 44;
            inv.setItem(slot, item(icons[i], Phases.KEYS[i],
                    Phases.COLORS[Math.min(i, Phases.COLORS.length - 1)], lore));
        }

        inv.setItem(0, btn(Material.BARRIER, "Закрыть", "close", null));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ клики
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.equals(h.player)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        if (slot == 0) {
            p.closeInventory();
            return;
        }
        if (slot == 4 || slot == 13 || slot == 22 || slot == 26) {
            return;
        }
        if ((slot >= 45 && slot <= 53) || slot == 44) {
            int idx = slot == 44 ? 9 : slot - 45;
            if (idx >= 0 && idx < Phases.KEYS.length) {
                p.performCommand("xmh phase " + Phases.KEYS[idx]);
            }
            p.closeInventory();
            Bukkit.getScheduler().runTask(XmusyaHard.I, () -> open(XmusyaHard.I, p));
            return;
        }
        String cmd = switch (slot) {
            case 8 -> "xmh bar";
            case 17 -> "xmh guide";
            case 28 -> "xmh skip";
            case 29 -> "xmh reset";
            case 30 -> "xmh pause";
            case 31 -> "xmh resume";
            case 33 -> "xmh bm start";
            case 34 -> "xmh bm stop";
            case 35 -> "xmh bm pause";
            case 36 -> "xmh bm resume";
            case 38 -> "xmh end status";
            case 39 -> "xmh end scan";
            case 40 -> "xmh end reset";
            case 42 -> "xmh reload";

            default -> "";
        };
        if (cmd == null) {
            p.closeInventory();
            return;
        }
        if (cmd.isEmpty()) {
            return;
        }
        p.closeInventory();
        p.performCommand(cmd);
        if (!cmd.startsWith("xmh end") && !cmd.equals("xmh reset")) {
            Bukkit.getScheduler().runTask(XmusyaHard.I, () -> {
                if (p.isOnline()) {
                    open(XmusyaHard.I, p);
                }
            });
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
