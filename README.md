# Plugins

Плагины для Paper/Leaf **Minecraft 26.3**. Каждый плагин — отдельная папка со
своим исходником, `README.md` и готовым `.jar` в `BuiltPlugins/`.

## Список плагинов

| Плагин | Версия | Что делает | Jar |
|---|---|---|---|
| [xmusya-hard](xmusya-hard/) | 1.1.4 | Хардкор с прогрессией по игровым дням: фазы, барьер, запреты, Кровавая луна | [xmusya-hard-1.1.4.jar](xmusya-hard/BuiltPlugins/xmusya-hard-1.1.4.jar) |

## Установка любого плагина

1. Скачай `.jar` из `BuiltPlugins/` нужного плагина (или из раздела Releases).
2. Положи в `<сервер>/plugins/`.
3. Полностью перезапусти сервер (не `/reload`).

## Сборка из исходников

Без Gradle/Maven — обычные `javac` + `jar`.

Нужны:
- **JDK 25** (paper-api 26.3 собран под class file 69; JDK 21 не прочитает его);
- на classpath: `paper-api-26.3.build.NNN-stable.jar`, adventure 5.2.0
  (api/key/serializer-legacy/serializer-plain), guava, gson, snakeyaml,
  bungeecord-chat, org.jetbrains:annotations, examination-api.

Скрипты сборки лежат в папках плагинов и по умолчанию ищут зависимости в
`../.toolchain` (JDK в `../.toolchain/jdk-25*`, jar'ы в `../.toolchain/lib`).
Путь можно переопределить переменной `TC`:

```bash
cd xmusya-hard
TC=/opt/mc-toolchain ./build.sh     # соберёт BuiltPlugins/<name>.jar
```

Серверный jar Paper нужной версии:
[fill.papermc.io/v3/projects/paper/versions/26.3/builds/latest](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/latest).

## Структура

```
<plugin>/
  src/main/java/...        исходники
  src/main/resources/      plugin.yml, config.yml и прочие ресурсы
  build.sh                 сборка (javac + jar)
  BuiltPlugins/*.jar       готовый плагин (коммитится)
  README.md                описание, команды, конфиг
```

`.toolchain/` (JDK и библиотеки) в репозиторий не входит — качается локально.
