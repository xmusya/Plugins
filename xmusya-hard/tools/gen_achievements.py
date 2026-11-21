#!/usr/bin/env python3
# Генератор датапака с ачивками xmusya-hard.
# Результат: xmusya-hard/achievements/ (pack.mcmeta + data/xmusya/advancement/*.json)
# Сборка zip: build.sh (jar --create из этой папки -> xmusya-hard-achievements.zip)
import json
import os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "achievements")

# id, title, description, icon, frame, parent, hidden
A = [
    ("root", "xmusya-hard", "Хардкор с прогрессией по игровым дням",
     "minecraft:crying_obsidian", "task", None, False),

    # --- прогрессия
    ("first_day", "Первый день", "Дожить до 2-го дня", "minecraft:clock", "task", "xmusya:root", False),
    ("day_50", "Полсотни", "Дожить до 50-го дня", "minecraft:golden_apple", "task", "xmusya:first_day", False),
    ("day_100", "Сотка", "Дожить до 100-го дня", "minecraft:gold_block", "goal", "xmusya:day_50", False),
    ("day_200", "Ветеран", "Дожить до 200-го дня", "minecraft:diamond_block", "goal", "xmusya:day_100", False),
    ("day_365", "Год в аду", "Дожить до 365-го дня", "minecraft:nether_star", "challenge", "xmusya:day_200", False),
    ("phase_stone", "Каменный век", "Достичь фазы stone", "minecraft:stone", "task", "xmusya:root", False),
    ("phase_iron", "Железный век", "Достичь фазы iron", "minecraft:iron_ingot", "task", "xmusya:phase_stone", False),
    ("phase_diamond", "Алмазный век", "Достичь фазы diamond", "minecraft:diamond", "goal", "xmusya:phase_iron", False),
    ("phase_netherite", "Незеритовый век", "Достичь фазы netherite", "minecraft:netherite_ingot", "goal", "xmusya:phase_diamond", False),
    ("walker", "Пешеход", "Пройти 10 000 блоков", "minecraft:iron_boots", "task", "xmusya:root", False),
    ("miner", "Шахтёр", "Добыть 2 000 блоков", "minecraft:diamond_pickaxe", "task", "xmusya:root", False),
    ("builder", "Архитектор", "Поставить 2 000 блоков", "minecraft:bricks", "task", "xmusya:root", False),

    # --- кровавая луна
    ("bm_first", "Кровавая луна", "Пережить первую Кровавую луну", "minecraft:rotten_flesh", "task", "xmusya:root", False),
    ("bm_5", "Охотник на луну", "Пережить 5 Кровавых лун", "minecraft:bone", "goal", "xmusya:bm_first", False),
    ("bm_10", "Повелитель ночи", "Пережить 10 Кровавых лун", "minecraft:wither_skeleton_skull", "challenge", "xmusya:bm_5", False),
    ("bm_miner", "Из шахты", "Пережить Кровавую луну ниже Y=40", "minecraft:iron_pickaxe", "goal", "xmusya:bm_first", False),
    ("bm_high", "Высотник", "Пережить Кровавую луну выше Y=120", "minecraft:phantom_membrane", "goal", "xmusya:bm_first", False),
    ("bm_no_sleep", "Бессонница", "Провести 3 ночи без сна", "minecraft:feather", "task", "xmusya:bm_first", False),
    ("bm_kills_10", "Мясник", "Убить 10 мобов за одну Кровавую луну", "minecraft:iron_sword", "task", "xmusya:bm_first", False),
    ("bm_kills_100", "Кровавая жатва", "Убить 100 мобов Кровавой луны", "minecraft:diamond_sword", "goal", "xmusya:bm_kills_10", False),
    ("bm_vex", "Вексобой", "Убить 10 вексов Кровавой луны", "minecraft:iron_nugget", "goal", "xmusya:bm_kills_10", False),
    ("bm_noskull", "Без черепа", "Убить визер-скелета Кровавой луны", "minecraft:coal", "task", "xmusya:bm_first", False),
    ("bm_lowhp", "На волоске", "Встретить рассвет с 3 сердцами или меньше", "minecraft:glistering_melon_slice", "challenge", "xmusya:bm_first", False),

    # --- энд и портал
    ("end_first_eye", "Око Энда", "Вставить первое око в рамку портала", "minecraft:ender_eye", "task", "xmusya:root", False),
    ("end_open", "Врата", "Открыть портал в Энд (12 из 12 рамок)", "minecraft:end_portal_frame", "goal", "xmusya:end_first_eye", False),
    ("end_repair", "Ремонтник", "Починить сломанную рамку портала", "minecraft:crying_obsidian", "task", "xmusya:end_open", False),
    ("end_repair_all", "Мастер ремонта", "Починить 12 рамок портала", "minecraft:obsidian", "goal", "xmusya:end_repair", False),
    ("end_ultra_repair", "Ультра-ремонт", "Починить рамку ультра-слома", "minecraft:heavy_core", "challenge", "xmusya:end_repair", False),
    ("end_dragon", "Драконоборец", "Убить дракона Энда", "minecraft:dragon_egg", "goal", "xmusya:end_open", False),
    ("end_void", "Пустота отпустила", "Спастись из пустоты Энда", "minecraft:ender_pearl", "task", "xmusya:end_open", False),
    ("end_hearts_10", "Сердце Энда", "Накопить 10 бонусных сердец", "minecraft:enchanted_golden_apple", "goal", "xmusya:end_open", False),
    ("end_hearts_20", "Бессмертный", "Накопить 20 бонусных сердец", "minecraft:totem_of_undying", "challenge", "xmusya:end_hearts_10", False),
    ("end_curse", "Проклятый", "Получить проклятие портала", "minecraft:wither_rose", "task", "xmusya:end_open", False),
    ("end_shield", "Щит дракона", "Заблокировать атаку дракона щитом", "minecraft:shield", "task", "xmusya:end_open", False),

    # --- драконий маяк
    ("beacon_first", "Драконий маяк", "Поставить яйцо дракона на маяк", "minecraft:beacon", "task", "xmusya:root", False),
    ("beacon_lvl4", "Полная мощность", "Собрать Драконий маяк 4 уровня", "minecraft:netherite_block", "goal", "xmusya:beacon_first", False),
    ("beacon_exclusive", "Неуязвимость", "Включить эксклюзивный эффект маяка", "minecraft:magma_cream", "task", "xmusya:beacon_first", False),
    ("beacon_collector", "Коллекционер", "Собрать 5 разных эффектов на маяках", "minecraft:brewing_stand", "goal", "xmusya:beacon_first", False),

    # --- социальное и прочее
    ("give_effect", "Щедрость", "Передать эффект другому игроку", "minecraft:splash_potion", "task", "xmusya:root", False),
    ("give_hearts", "Дар", "Передать бонусное сердце", "minecraft:honey_bottle", "task", "xmusya:give_effect", False),
    ("trade_first", "Торговец", "Совершить первую сделку с жителем", "minecraft:emerald", "task", "xmusya:root", False),
    ("trade_50", "Магнат", "Совершить 50 сделок", "minecraft:emerald_block", "goal", "xmusya:trade_first", False),
    ("guide", "Книжник", "Получить книгу-гайд", "minecraft:writable_book", "task", "xmusya:root", False),
    ("death_10", "Упрямый", "Умереть 10 раз и продолжить играть", "minecraft:skeleton_skull", "task", "xmusya:root", False),
    ("no_death_10", "Хардкор", "Прожить 10 дней без единой смерти", "minecraft:netherite_chestplate", "challenge", "xmusya:root", False),

    # --- секретные
    ("secret_egg", "Яйцо не моё", "Кликнуть по яйцу дракона", "minecraft:dragon_egg", "task", "xmusya:root", True),
    ("secret_frame", "Не сломать", "Попытаться сломать рамку портала", "minecraft:end_portal_frame", "task", "xmusya:root", True),
    ("secret_slept_bm", "Сон под луной", "Попытаться лечь спать во время Кровавой луны", "minecraft:red_bed", "task", "xmusya:root", True),
]


def main():
    adv_dir = os.path.join(OUT, "data", "xmusya", "advancement")
    os.makedirs(adv_dir, exist_ok=True)
    for name in os.listdir(adv_dir):
        if name.endswith(".json"):
            os.remove(os.path.join(adv_dir, name))

    for aid, title, desc, icon, frame, parent, hidden in A:
        full = "xmusya:" + aid
        d = {
            "display": {
                "icon": {"id": icon},
                "title": {"text": title},
                "description": {"text": desc},
                "frame": frame,
                "show_toast": True,
                "announce_to_chat": False,
                "hidden": hidden,
            },
            "criteria": {"impossible": {"trigger": "minecraft:impossible"}},
        }
        if parent:
            d["parent"] = parent
        else:
            d["display"]["background"] = "minecraft:gui/advancements/backgrounds/stone"
            d["display"]["show_toast"] = False
        with open(os.path.join(adv_dir, aid + ".json"), "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False, indent=2)
            f.write("\n")

    mcmeta = {
        "pack": {
            "description": "xmusya-hard: достижения",
            "min_format": 121,
            "max_format": 121,
        }
    }
    with open(os.path.join(OUT, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump(mcmeta, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("advancements:", len(A))


if __name__ == "__main__":
    main()