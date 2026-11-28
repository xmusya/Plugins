#!/usr/bin/env bash
# Сборка xmusya-worlds без Gradle/Maven: javac + jar.
# Тулчейн по умолчанию — ../.toolchain, переопределить: TC=/путь ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

TC="${TC:-../.toolchain}"
JDK="$(ls -d "$TC"/jdk-25* 2>/dev/null | head -1 || true)"
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/javac" ]; then
    echo "Не найден JDK 25 в $TC/jdk-25* (нужен именно 25: paper-api под class file 69)" >&2
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
    CP="$CP:$jar"
done

OUT="build/classes"
# версия берётся из plugin.yml (единственное место для правки)
VER="$(grep -E '^version:' src/main/resources/plugin.yml | head -1 | sed -E 's/version:[[:space:]]*//; s/["'\'' ]//g')"
[ -z "$VER" ] && VER="0.0.0"
JAR="BuiltPlugins/xmusya-worlds-${VER}.jar"

rm -rf "$OUT"
mkdir -p "$OUT" BuiltPlugins

find src/main/java -name '*.java' > build/sources.txt
"$JDK/bin/javac" -encoding UTF-8 --release 25 -Xlint:-options \
    -cp "$CP" -d "$OUT" @build/sources.txt

cp -r src/main/resources/* "$OUT"/

"$JDK/bin/jar" --create --file "$JAR" -C "$OUT" .

echo "Собрано: $JAR"
ls -lh "$JAR"