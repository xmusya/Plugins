package ru.xmusya.hard;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Все запреты «не по фазе»: броня, инструменты, предметы, измерения,
 * сон и невидимость в Кровавую луну.
 * Все списки берутся из конфига текущей фазы (PhaseCfg).
 */
public final class Restrictions {

    private Restrictions() {
    }

    // ------------------------------------------------------------------ броня
    public static void checkArmor(XmusyaHard plugin, Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack st = armor[i];
            if (st == null || st.getType() == Material.AIR) {
                continue;
            }
            if (!plugin.cfg().forbiddenArmor.contains(st.getType())) {
                continue;
            }
            int day = plugin.dayWhenAllowed(st.getType());
            armor[i] = null;
            changed = true;
            drop(p, st);
            Msg.action(p, "Эта экипировка откроется на " + day + "-й день — она выброшена.");
            p.playSound(Msg.villagerNo());
        }
        if (changed) {
            inv.setArmorContents(armor);
        }
    }

    // ------------------------------------------------------------------ руки
    public static void checkHands(XmusyaHard plugin, Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (forbiddenTool(plugin, main) || forbiddenTool(plugin, off)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 4, true, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 4, true, false, true));
        }
    }

    private static boolean forbiddenTool(XmusyaHard plugin, ItemStack st) {
        if (st == null || st.getType() == Material.AIR) {
            return false;
        }
        return plugin.cfg().forbiddenTools.contains(st.getType());
    }

    // ------------------------------------------------------------------ инвентарь
    public static void checkInventory(XmusyaHard plugin, Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        List<Integer> remove = new ArrayList<>();
        String message = null;

        for (int i = 0; i < contents.length; i++) {
            ItemStack st = contents[i];
            if (st == null || st.getType() == Material.AIR) {
                continue;
            }
            Material t = st.getType();
            if (!plugin.cfg().forbiddenItems.contains(t)) {
                continue;
            }
            remove.add(i);
            message = "Этот предмет откроется на " + plugin.dayWhenAllowed(t) + "-й день!";
        }

        if (remove.isEmpty()) {
            return;
        }
        for (int idx : remove) {
            ItemStack st = contents[idx];
            p.getInventory().setItem(idx, null);
            drop(p, st);
        }
        Msg.action(p, message);
        p.playSound(Msg.villagerNo());
    }

    // ------------------------------------------------------------------ невидимость
    public static void checkInvisibility(Player p) {
        boolean has = false;
        for (PotionEffect eff : p.getActivePotionEffects()) {
            if (eff.getType() == PotionEffectType.INVISIBILITY) {
                has = true;
                break;
            }
        }
        if (has) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            Msg.action(p, "Кровавая луна развеивает вашу невидимость!");
        }
    }

    // ------------------------------------------------------------------ измерения
    public static void checkDimension(XmusyaHard plugin, Player p) {
        World.Environment env = p.getWorld().getEnvironment();
        String name;
        String label;
        switch (env) {
            case NETHER -> {
                name = "nether";
                label = "Незер";
            }
            case THE_END -> {
                name = "end";
                label = "Энд";
            }
            default -> {
                name = "overworld";
                label = "Обычный мир";
            }
        }
        if (plugin.cfg().dimensions.contains(name)) {
            return;
        }
        World ow = plugin.overworld();
        if (ow == null || ow.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        // Куда вернуть: из Незера координаты ×8, из Энда — как есть в оверворлде.
        double x = p.getLocation().getX();
        double z = p.getLocation().getZ();
        if (env == World.Environment.NETHER) {
            x *= 8.0;
            z *= 8.0;
        }

        // Не выкидываем за мировой барьер (минус пара блоков отступа).
        org.bukkit.WorldBorder wb = ow.getWorldBorder();
        double half = Math.max(8.0, wb.getSize() / 2.0 - 4.0);
        double cx = wb.getCenter().getX();
        double cz = wb.getCenter().getZ();
        x = Math.max(cx - half, Math.min(cx + half, x));
        z = Math.max(cz - half, Math.min(cz + half, z));

        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = ow.getHighestBlockYAt(bx, bz) + 1;

        p.teleport(new Location(ow, bx + 0.5, by, bz + 0.5));

        // 1 секунда неуязвимости и слепоты после переноса.
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 255, true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, true, false, true));

        p.playSound(Msg.villagerNo());
        Msg.chat(p, Msg.c(label + " откроется на "
                + plugin.dayWhenDimensionAllowed(name) + "-й день!", NamedTextColor.RED));
    }

    // ------------------------------------------------------------------ сон
    public static void checkSleeping(Player p) {
        if (!p.isSleeping()) {
            return;
        }
        Location loc = p.getLocation().clone().add(0.0, 0.05, 0.0);
        p.teleport(loc);
        Msg.action(p, "Вы не можете уснуть во время Кровавой луны!");
    }

    // ------------------------------------------------------------------ дроп предмета
    /** Безопасно выбрасывает предмет на землю (со всеми чарами, без дюпа). */
    public static void drop(Player p, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        Location loc = p.getLocation().clone().add(0.0, 0.5, 0.0);
        Item item = p.getWorld().dropItemNaturally(loc, stack);
        item.setPickupDelay(40);
    }
}
