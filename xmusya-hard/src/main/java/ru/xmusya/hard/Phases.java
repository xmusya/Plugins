package ru.xmusya.hard;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/**
 * Таблица этапов (фаз) и все списки предметов/рецептов, привязанные к ним.
 */
public final class Phases {

    private Phases() {
    }

    public static final int COUNT = 10;

    /** Ключи фаз в порядке прогрессии. */
    public static final String[] KEYS = {
            "wood", "stone", "iron", "diamond", "nether",
            "golden_food", "villagers", "beacons", "netherite", "endgame"
    };

    /** Человекочитаемые названия фаз. */
    public static final String[] NAMES = {
            "Деревянный старт", "Каменный век", "Эпоха металлов", "Алмазная эра",
            "Врата Незера", "Золотой пир", "Открытие торговли", "Эпоха Маяков",
            "Незеритовый финал", "Свободная игра (ультра-хардкор)"
    };

    /** Короткие названия для боссбара. */
    public static final String[] SHORT = {
            "Дерево", "Камень", "Железо", "Алмаз", "Незер",
            "Золотая еда", "Жители", "Маяки", "Незерит", "Финал"
    };

    public static final NamedTextColor[] COLORS = {
            NamedTextColor.GRAY, NamedTextColor.WHITE, NamedTextColor.YELLOW,
            NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD,
            NamedTextColor.GREEN, NamedTextColor.BLUE, NamedTextColor.DARK_RED,
            NamedTextColor.RED
    };

    /** Рецепты, открывающиеся на соответствующей фазе. */
    public static final String[][] RECIPES = {
            // 0 wood — рецепты базы выдаются отдельно (все "*" минус заблокированные)
            {},
            // 1 stone
            {"arrow", "bow", "chiseled_stone_bricks", "cobblestone_slab", "cobblestone_stairs",
                    "cobblestone_wall", "leather_horse_armor", "shield", "smooth_stone", "stone_axe",
                    "stone_brick_slab", "stone_brick_stairs", "stone_brick_wall", "stone_bricks",
                    "stone_button", "stone_hoe", "stone_pickaxe", "stone_pressure_plate",
                    "stone_shovel", "stone_sword"},
            // 2 iron
            {"blast_furnace", "bucket", "dispenser", "dropper", "flint_and_steel",
                    "gold_ingot_from_blasting_deepslate_gold_ore", "gold_ingot_from_blasting_gold_ore",
                    "gold_ingot_from_blasting_nether_gold_ore", "gold_ingot_from_blasting_raw_gold",
                    "gold_ingot_from_gold_block", "gold_ingot_from_nuggets",
                    "gold_ingot_from_smelting_deepslate_gold_ore", "gold_ingot_from_smelting_gold_ore",
                    "gold_ingot_from_smelting_nether_gold_ore", "gold_ingot_from_smelting_raw_gold",
                    "gold_nugget", "golden_axe", "golden_boots", "golden_chestplate", "golden_helmet",
                    "golden_hoe", "golden_leggings", "golden_pickaxe", "golden_shovel", "golden_sword",
                    "hopper", "iron_axe", "iron_bars", "iron_boots", "iron_chestplate", "iron_helmet",
                    "iron_hoe", "iron_ingot_from_blasting_deepslate_iron_ore",
                    "iron_ingot_from_blasting_iron_ore", "iron_ingot_from_blasting_raw_iron",
                    "iron_ingot_from_iron_block", "iron_ingot_from_nuggets",
                    "iron_ingot_from_smelting_deepslate_iron_ore", "iron_ingot_from_smelting_iron_ore",
                    "iron_ingot_from_smelting_raw_iron", "iron_leggings", "iron_nugget",
                    "iron_pickaxe", "iron_shovel", "iron_sword", "piston", "redstone", "shears",
                    "smithing_table", "smoker", "stonecutter"},
            // 3 diamond
            {"diamond_axe", "diamond_boots", "diamond_chestplate", "diamond_helmet", "diamond_hoe",
                    "diamond_leggings", "diamond_pickaxe", "diamond_shovel", "diamond_sword",
                    "enchanting_table"},
            // 4 nether
            {"brewing_stand", "cauldron", "glowstone", "nether_brick", "nether_brick_fence",
                    "nether_brick_slab", "nether_brick_slab_from_nether_bricks_stonecutting",
                    "nether_brick_stairs", "nether_brick_stairs_from_nether_bricks_stonecutting",
                    "nether_brick_wall", "nether_brick_wall_from_nether_bricks_stonecutting",
                    "nether_bricks", "respawn_anchor"},
            // 5 golden food
            {"glistering_melon_slice", "golden_apple", "golden_carrot"},
            // 6 villagers
            {"anvil", "grindstone"},
            // 7 beacons
            {"beacon"},
            // 8 netherite
            {"lodestone", "netherite_axe_smithing", "netherite_block", "netherite_boots_smithing",
                    "netherite_chestplate_smithing", "netherite_helmet_smithing", "netherite_hoe_smithing",
                    "netherite_horse_armor_smithing", "netherite_ingot", "netherite_ingot_from_netherite_block",
                    "netherite_leggings_smithing", "netherite_nautilus_armor_smithing",
                    "netherite_pickaxe_smithing", "netherite_scrap", "netherite_scrap_from_blasting",
                    "netherite_shovel_smithing", "netherite_spear_smithing", "netherite_sword_smithing",
                    "netherite_upgrade_smithing_template"},
            // 9 endgame
            {"firework_rocket", "firework_rocket_simple", "firework_star", "firework_star_fade"}
    };

    /** Броня, запрещённая до соответствующего дня (включительно). */
    public static final Set<Material> IRON_ARMOR = Set.of(
            Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS);

    public static final Set<Material> DIAMOND_ARMOR = Set.of(
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);

    public static final Set<Material> NETHERITE_ARMOR = Set.of(
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);

    public static final Set<Material> IRON_TOOLS = Set.of(
            Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE,
            Material.IRON_SHOVEL, Material.IRON_HOE,
            Material.GOLDEN_SWORD, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE,
            Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE);

    public static final Set<Material> DIAMOND_TOOLS = Set.of(
            Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE,
            Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE);

    public static final Set<Material> NETHERITE_TOOLS = Set.of(
            Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE);

    public static final Set<Material> STONE_TOOLS = Set.of(
            Material.STONE_SWORD, Material.STONE_PICKAXE, Material.STONE_AXE,
            Material.STONE_SHOVEL, Material.STONE_HOE);

    public static final List<String> HOSTILE_TYPES = List.of(
            "minecraft:zombie", "minecraft:skeleton", "minecraft:stray", "minecraft:husk",
            "minecraft:spider", "minecraft:cave_spider", "minecraft:creeper",
            "minecraft:piglin", "minecraft:wither_skeleton", "minecraft:drowned",
            // мобы Кровавой луны с 70/80/100/110 дней
            "minecraft:zombified_piglin", "minecraft:vindicator", "minecraft:pillager",
            "minecraft:witch", "minecraft:bogged", "minecraft:slime",
            "minecraft:magma_cube");

    public static int indexOfKey(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }
}
