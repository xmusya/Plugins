package ru.xmusya.hard;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Доливает в пользовательский config.yml ключи, которых там нет (новые версии
 * плагина). Значения и комментарии пользователя сохраняются — дописываются
 * только отсутствующие параметры.
 *
 * Без этого обновление плагина требовало удалять config.yml вручную.
 */
public final class ConfigMerge {

    private ConfigMerge() {
    }

    /**
     * @return сколько ключей было добавлено
     */
    public static int merge(File folder, InputStream defaultsStream) {
        return merge(folder, "config.yml", defaultsStream);
    }

    /**
     * То же для произвольного YAML-файла (например messages.yml).
     *
     * @return сколько ключей было добавлено
     */
    public static int merge(File folder, String fileName, InputStream defaultsStream) {
        File file = new File(folder, fileName);
        if (!file.exists()) {
            return 0;
        }
        YamlConfiguration user;
        try {
            user = YamlConfiguration.loadConfiguration(file);
        } catch (RuntimeException ex) {
            return 0;
        }
        String defText;
        try (InputStream in = defaultsStream) {
            if (in == null) {
                return 0;
            }
            defText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return 0;
        }
        YamlConfiguration def = new YamlConfiguration();
        try {
            def.loadFromString(defText);
        } catch (Exception ex) {
            return 0;
        }

        // Плоские ключи, которых нет у пользователя
        List<String> missing = new ArrayList<>();
        for (String key : def.getKeys(true)) {
            if (def.isConfigurationSection(key)) {
                continue; // сами секции не добавляем — только значения
            }
            if (!user.contains(key)) {
                missing.add(key);
            }
        }
        if (missing.isEmpty()) {
            return 0;
        }

        List<String> lines;
        try {
            lines = new ArrayList<>(java.nio.file.Files.readAllLines(
                    file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return 0;
        }

        // Резервная копия
        try {
            java.nio.file.Files.copy(file.toPath(),
                    new File(folder, fileName + ".bak").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }

        int added = 0;
        for (String key : missing) {
            int dot = key.lastIndexOf('.');
            String parent = dot < 0 ? "" : key.substring(0, dot);
            String child = dot < 0 ? key : key.substring(dot + 1);
            int[] range = ensureSection(lines, parent);
            int insertAt = range == null ? lines.size() : range[1];
            while (insertAt > 0 && lines.get(insertAt - 1).isBlank()) {
                insertAt--;
            }
            int indent = range == null ? 0 : indentOf(lines.get(range[0])) + 2;
            List<String> block = render(child, def.get(key), indent);
            lines.addAll(insertAt, block);
            added++;
        }

        try {
            java.nio.file.Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return 0;
        }
        return added;
    }

    /** Ищет секцию по пути («end.portal»); возвращает {начало, конец} или null. */
    private static int[] findSection(List<String> lines, String path) {
        if (path.isEmpty()) {
            return null;
        }
        String[] parts = path.split("\\.");
        List<String> stackNames = new ArrayList<>();
        List<Integer> stackIndents = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String t = stripComment(lines.get(i));
            if (t.isBlank()) {
                continue;
            }
            int ind = indentOf(t);
            String c = t.trim();
            if (c.endsWith(":")) {
                while (!stackIndents.isEmpty()
                        && stackIndents.get(stackIndents.size() - 1) >= ind) {
                    stackIndents.remove(stackIndents.size() - 1);
                    stackNames.remove(stackNames.size() - 1);
                }
                stackNames.add(c.substring(0, c.length() - 1).trim());
                stackIndents.add(ind);
                if (stackNames.size() == parts.length && stackNames.equals(List.of(parts))) {
                    int end = i + 1;
                    while (end < lines.size()) {
                        String t2 = stripComment(lines.get(end));
                        if (!t2.isBlank() && indentOf(t2) <= ind) {
                            break;
                        }
                        end++;
                    }
                    return new int[]{i, end};
                }
                continue;
            }
            while (!stackIndents.isEmpty()
                    && ind <= stackIndents.get(stackIndents.size() - 1)) {
                stackIndents.remove(stackIndents.size() - 1);
                stackNames.remove(stackNames.size() - 1);
            }
        }
        return null;
    }

    /** Гарантирует наличие секции (создаёт отсутствующие родительские). */
    private static int[] ensureSection(List<String> lines, String path) {
        int[] found = findSection(lines, path);
        if (found != null) {
            return found;
        }
        if (path.isEmpty()) {
            return null;
        }
        int dot = path.lastIndexOf('.');
        if (dot > 0) {
            int[] parent = ensureSection(lines, path.substring(0, dot));
            int at = parent == null ? lines.size() : parent[1];
            while (at > 0 && lines.get(at - 1).isBlank()) {
                at--;
            }
            int indent = parent == null ? 0 : indentOf(lines.get(parent[0])) + 2;
            lines.add(at, " ".repeat(indent) + path.substring(dot + 1) + ":");
            return findSection(lines, path);
        }
        int at = lines.size();
        while (at > 0 && lines.get(at - 1).isBlank()) {
            at--;
        }
        lines.add(at, path + ":");
        return findSection(lines, path);
    }

    private static String stripComment(String line) {
        boolean inS = false, inD = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inD) {
                inS = !inS;
            } else if (c == '"' && !inS) {
                inD = !inD;
            } else if (c == '#' && !inS && !inD) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static int indentOf(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    /** Рендерит «ключ: значение» (для вложенных — с правильными отступами). */
    private static List<String> render(String key, Object value, int indent) {
        YamlConfiguration tmp = new YamlConfiguration();
        tmp.set("k", value);
        List<String> out = new ArrayList<>();
        String[] dump = tmp.saveToString().split("\n");
        String pad = " ".repeat(indent);
        for (int i = 0; i < dump.length; i++) {
            if (dump[i].isBlank()) {
                continue;
            }
            if (i == 0 && dump[i].startsWith("k:")) {
                out.add(pad + key + dump[i].substring(1));
            } else {
                out.add(pad + dump[i]);
            }
        }
        return out;
    }
}