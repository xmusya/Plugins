package ru.xmusya.hard;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Настройки одной фазы (этапа прогрессии).
 * Всё, что можно менять в config.yml, живёт здесь.
 */
public final class PhaseCfg {

    /** Ключ фазы: wood, stone, iron, diamond, nether, golden_food, villagers, beacons, netherite, endgame. */
    public String key;
    /** Название для чата/меню. */
    public String name;
    /** Короткое название для боссбара. */
    public String shortName;

    /** День, с которого начинается фаза. */
    public int day = 0;
    /** Размер барьера, выставляемый при ручном переходе на эту фазу. */
    public int border = 650;

    /** Геймрул limited_crafting на этой фазе. */
    public boolean limitedCrafting = true;
    /** Геймрул natural_health_regeneration на этой фазе. */
    public boolean naturalRegeneration = true;
    /** Разрешена ли торговля с жителями. */
    public boolean trade = false;

    /** Броня, которую НЕЛЬЗЯ носить на этой фазе. */
    public final Set<Material> forbiddenArmor = new HashSet<>();
    /** Инструменты/оружие, которые НЕЛЬЗЯ держать на этой фазе. */
    public final Set<Material> forbiddenTools = new HashSet<>();
    /** Предметы, которые будут выброшены из инвентаря на этой фазе. */
    public final Set<Material> forbiddenItems = new HashSet<>();
    /** Разрешённые измерения: overworld / nether / end. */
    public final Set<String> dimensions = new HashSet<>();
    /** Рецепты, открывающиеся именно на этой фазе. */
    public final List<String> recipes = new ArrayList<>();

    public PhaseCfg(String key) {
        this.key = key;
        this.name = key;
        this.shortName = key;
    }

    /** Фабрика значений по умолчанию (полное ТЗ). */
    public static List<PhaseCfg> defaults() {
        List<PhaseCfg> out = new ArrayList<>();
        int[] days = {0, 1, 5, 10, 25, 30, 40, 45, 50, 60};
        int[] borders = {650, 650, 650, 650, 2500, 2500, 3500, 4000, 4500, 5500};

        for (int i = 0; i < Phases.COUNT; i++) {
            PhaseCfg c = new PhaseCfg(Phases.KEYS[i]);
            c.name = Phases.NAMES[i];
            c.shortName = Phases.SHORT[i];
            c.day = days[i];
            c.border = borders[i];
            c.limitedCrafting = days[i] < 50;
            c.naturalRegeneration = days[i] < 60;
            c.trade = i >= 6;
            c.recipes.addAll(List.of(Phases.RECIPES[i]));
            out.add(c);
        }

        // ---------------- wood: ничего железного и выше
        PhaseCfg wood = out.get(0);
        wood.dimensions.add("overworld");
        wood.forbiddenArmor.addAll(Phases.IRON_ARMOR);
        wood.forbiddenArmor.addAll(Phases.DIAMOND_ARMOR);
        wood.forbiddenArmor.addAll(Phases.NETHERITE_ARMOR);
        wood.forbiddenArmor.add(Material.ELYTRA);
        wood.forbiddenTools.addAll(Phases.STONE_TOOLS);
        wood.forbiddenTools.addAll(Phases.IRON_TOOLS);
        wood.forbiddenTools.addAll(Phases.DIAMOND_TOOLS);
        wood.forbiddenTools.addAll(Phases.NETHERITE_TOOLS);
        wood.forbiddenItems.add(Material.GOLDEN_APPLE);
        wood.forbiddenItems.add(Material.ENCHANTED_GOLDEN_APPLE);
        wood.forbiddenItems.add(Material.GOLDEN_CARROT);
        wood.forbiddenItems.add(Material.BEACON);
        wood.forbiddenItems.add(Material.TOTEM_OF_UNDYING);

        // ---------------- stone
        PhaseCfg stone = out.get(1);
        stone.dimensions.add("overworld");
        stone.forbiddenArmor.addAll(Phases.IRON_ARMOR);
        stone.forbiddenArmor.addAll(Phases.DIAMOND_ARMOR);
        stone.forbiddenArmor.addAll(Phases.NETHERITE_ARMOR);
        stone.forbiddenArmor.add(Material.ELYTRA);
        stone.forbiddenTools.addAll(Phases.IRON_TOOLS);
        stone.forbiddenTools.addAll(Phases.DIAMOND_TOOLS);
        stone.forbiddenTools.addAll(Phases.NETHERITE_TOOLS);
        stone.forbiddenItems.addAll(wood.forbiddenItems);

        // ---------------- iron
        PhaseCfg iron = out.get(2);
        iron.dimensions.add("overworld");
        iron.forbiddenArmor.addAll(Phases.DIAMOND_ARMOR);
        iron.forbiddenArmor.addAll(Phases.NETHERITE_ARMOR);
        iron.forbiddenArmor.add(Material.ELYTRA);
        iron.forbiddenTools.addAll(Phases.DIAMOND_TOOLS);
        iron.forbiddenTools.addAll(Phases.NETHERITE_TOOLS);
        iron.forbiddenItems.add(Material.GOLDEN_APPLE);
        iron.forbiddenItems.add(Material.ENCHANTED_GOLDEN_APPLE);
        iron.forbiddenItems.add(Material.GOLDEN_CARROT);
        iron.forbiddenItems.add(Material.BEACON);
        iron.forbiddenItems.add(Material.TOTEM_OF_UNDYING);

        // ---------------- diamond
        PhaseCfg diamond = out.get(3);
        diamond.dimensions.add("overworld");
        diamond.forbiddenArmor.addAll(Phases.NETHERITE_ARMOR);
        diamond.forbiddenArmor.add(Material.ELYTRA);
        diamond.forbiddenTools.addAll(Phases.NETHERITE_TOOLS);
        diamond.forbiddenItems.addAll(iron.forbiddenItems);

        // ---------------- nether
        PhaseCfg nether = out.get(4);
        nether.dimensions.add("overworld");
        nether.dimensions.add("nether");
        nether.forbiddenArmor.addAll(Phases.NETHERITE_ARMOR);
        nether.forbiddenArmor.add(Material.ELYTRA);
        nether.forbiddenTools.addAll(Phases.NETHERITE_TOOLS);
        nether.forbiddenItems.add(Material.GOLDEN_APPLE);
        nether.forbiddenItems.add(Material.ENCHANTED_GOLDEN_APPLE);
        nether.forbiddenItems.add(Material.GOLDEN_CARROT);
        nether.forbiddenItems.add(Material.BEACON);
        nether.forbiddenItems.add(Material.TOTEM_OF_UNDYING);

        // ---------------- golden_food
        PhaseCfg golden = out.get(5);
        golden.dimensions.addAll(nether.dimensions);
        golden.forbiddenArmor.addAll(nether.forbiddenArmor);
        golden.forbiddenTools.addAll(nether.forbiddenTools);
        golden.forbiddenItems.add(Material.BEACON);
        golden.forbiddenItems.add(Material.TOTEM_OF_UNDYING);

        // ---------------- villagers
        PhaseCfg villagers = out.get(6);
        villagers.dimensions.addAll(nether.dimensions);
        villagers.forbiddenArmor.addAll(nether.forbiddenArmor);
        villagers.forbiddenTools.addAll(nether.forbiddenTools);
        villagers.forbiddenItems.add(Material.BEACON);
        villagers.forbiddenItems.add(Material.TOTEM_OF_UNDYING);

        // ---------------- beacons
        PhaseCfg beacons = out.get(7);
        beacons.dimensions.addAll(nether.dimensions);
        beacons.forbiddenArmor.addAll(nether.forbiddenArmor);
        beacons.forbiddenTools.addAll(nether.forbiddenTools);
        beacons.forbiddenItems.add(Material.TOTEM_OF_UNDYING);

        // ---------------- netherite
        PhaseCfg netherite = out.get(8);
        netherite.dimensions.addAll(nether.dimensions);
        netherite.forbiddenArmor.add(Material.ELYTRA);
        netherite.forbiddenItems.add(Material.TOTEM_OF_UNDYING);

        // ---------------- endgame: всё открыто
        PhaseCfg endgame = out.get(9);
        endgame.dimensions.add("overworld");
        endgame.dimensions.add("nether");
        endgame.dimensions.add("end");

        return out;
    }
}
