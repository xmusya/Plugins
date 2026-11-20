#!/usr/bin/env bash
# Сборка плагина xmusya-hard без Gradle/Maven: javac + jar.
# Тулчейн по умолчанию — ../.toolchain, можно переопределить: TC=/путь ./build.sh
set -euo pipefail

cd "$(dirname "$0")"

TC="${TC:-../.toolchain}"
JDK="$(ls -d "$TC"/jdk-25* 2>/dev/null | head -1 || true)"
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/javac" ]; then
    echo "Не найден JDK 25 в $TC/jdk-25* (нужен именно 25: paper-api собран под class file 69)" >&2
    exit 1
fi

LIBS="$TC/lib"
PAPER="$(ls "$LIBS"/paper-api-26.3*.jar 2>/dev/null | head -1 || true)"
if [ -z "$PAPER" ]; then
    echo "Не найден paper-api 26.3 в $LIBS" >&2
    exit 1
fi

CP="$PAPER"
for jar in "$LIBS"/*.jar; do
    # остальные зависимости (adventure, guava, gson, snakeyaml, bungee, annotations...)
    CP="$CP:$jar"
done

OUT="build/classes"
JAR="BuiltPlugins/xmusya-hard-1.1.7.jar"

rm -rf "$OUT"
mkdir -p "$OUT" BuiltPlugins

find src/main/java -name '*.java' > build/sources.txt
"$JDK/bin/javac" -encoding UTF-8 --release 25 -Xlint:-options \
    -cp "$CP" -d "$OUT" @build/sources.txt

cp -r src/main/resources/* "$OUT"/
"$JDK/bin/jar" --create --file "$JAR" -C "$OUT" .

echo "Собрано: $JAR"
ls -lh "$JAR"
