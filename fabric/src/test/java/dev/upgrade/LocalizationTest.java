package dev.upgrade;

import com.google.gson.stream.JsonReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Verifies the resources that are actually on the release classpath. */
public final class LocalizationTest {
    public static final List<String> LOCALES = List.of(
            "en_us", "ru_ru", "uk_ua", "de_de", "fr_fr", "es_es", "es_mx", "pt_br", "pt_pt",
            "it_it", "pl_pl", "nl_nl", "tr_tr", "zh_cn", "zh_tw", "ja_jp", "ko_kr", "id_id",
            "vi_vn", "ar_sa", "hi_in", "th_th");
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?s|%%");

    private static Map<String,String> read(String locale) throws IOException {
        String path="assets/upgrade/lang/"+locale+".json";
        var stream=LocalizationTest.class.getClassLoader().getResourceAsStream(path);
        if (stream==null) throw new AssertionError("Missing packaged language: "+locale);
        Map<String,String> result=new LinkedHashMap<>();
        try (var reader=new JsonReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key=reader.nextName(),value=reader.nextString();
                if (!key.startsWith("upgrade.") || value.isBlank() || value.contains("\uFFFD"))
                    throw new AssertionError("Invalid translation: "+locale+" "+key);
                if (result.put(key,value)!=null) throw new AssertionError("Duplicate key: "+locale+" "+key);
            }
            reader.endObject();
            if (reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)
                throw new AssertionError("Trailing JSON: "+locale);
        }
        return result;
    }

    private static List<Integer> arguments(String text) {
        var matcher=ARGUMENT.matcher(text);
        List<Integer> result=new ArrayList<>();
        int next=1;
        while (matcher.find()) {
            if (matcher.group().equals("%%")) continue;
            result.add(matcher.group(1)==null?next++:Integer.parseInt(matcher.group(1)));
        }
        if (matcher.replaceAll("").contains("%")) throw new AssertionError("Invalid placeholder: "+text);
        Collections.sort(result);
        return result;
    }

    public static void main(String[] args) throws Exception {
        var english=read("en_us");
        for (String locale:LOCALES) {
            var translated=read(locale);
            if (!translated.keySet().equals(english.keySet())) throw new AssertionError("Key mismatch: "+locale);
            for (var entry:english.entrySet()) {
                String value=translated.get(entry.getKey());
                if (!arguments(entry.getValue()).equals(arguments(value)))
                    throw new AssertionError("Placeholder mismatch: "+locale+" "+entry.getKey());
                if (entry.getValue().chars().filter(c->c=='\n').count()!=value.chars().filter(c->c=='\n').count())
                    throw new AssertionError("Tooltip line mismatch: "+locale+" "+entry.getKey());
            }
        }
        // Catch missing keys and accidentally reintroduced hard-coded player-facing Russian.
        var sources=new ArrayList<Path>();
        try (var paths=Files.walk(Path.of("src/main/java/dev/upgrade/client"))) {
            paths.filter(p->p.toString().endsWith(".java")).forEach(sources::add);
        }
        sources.add(Path.of("src/main/java/dev/upgrade/Network.java"));
        sources.add(Path.of("src/main/java/dev/upgrade/Upgrade.java"));
        var literal=Pattern.compile("\"(upgrade\\.[a-z_]+)\"");
        for (Path path:sources) {
            String source=Files.readString(path);
            var matcher=literal.matcher(source);
            while (matcher.find()) if (!english.containsKey(matcher.group(1)))
                throw new AssertionError("Missing source key in "+path+": "+matcher.group(1));
            if (Pattern.compile("[А-Яа-яЁё]").matcher(source).find())
                throw new AssertionError("Hard-coded Russian in player-facing source: "+path);
        }
        System.out.println("LOCALIZATION PASS: "+LOCALES.size()+" locales, "+english.size()+" keys each");
    }
}
