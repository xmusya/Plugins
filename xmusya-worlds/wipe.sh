#!/bin/bash
# Отдельный вайп: сбрасывает выживание и доп.миры, СОХРАНЯЯ хаб (xmhub) и datapacks.
# Запускать при ОСТАНОВЛЕННОМ сервере.

CYAN='\033[0;36m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

cd ~/mcserver/server || { echo "нет папки сервера"; exit 1; }

echo -e "${RED}ВНИМАНИЕ: вайп выживания и миров (хаб и datapacks сохранятся).${NC}"
read -p "введи 'да' для подтверждения: " c
if [ "$c" != "da" ] && [ "$c" != "да" ]; then
    echo "отмена"; exit 0
fi

# прочие миры (плагин пересоздаст)
rm -rf world_the_end world_nether world_cave world_islands world_farm
# размерности выживания; хаб xmhub НЕ трогаем
rm -rf world/dimensions/minecraft/overworld \
       world/dimensions/minecraft/nether \
       world/dimensions/minecraft/the_end
# старый формат (на всякий)
rm -rf world/region world/entities world/poi
# world/datapacks и world/dimensions/minecraft/xmhub остаются
# данные плагинов
rm -f plugins/SetHome/homes.yml
rm -f plugins/xmusya-worlds/players.yml
rm -rf plugins/squaremap/data/* plugins/squaremap/web/tiles/*

echo -e "${YELLOW}готово. хаб и datapacks сохранены.${NC}"