package org.haganecobol.javadoci18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 翻訳ファイル(ソースファイルごとのYAML)の読み書き。
 *
 * <p>各ファイルは{@code ja}(日本語の原文)と{@code en}(訳文)の組のリストである。訳文は、同じソースファイルに対応するファイルからだけ引く。
 * 別のファイルの訳文は、{@code sync}が新しい原文に訳文の初期値を与えるときにだけ使う({@link #lookupElsewhere})。
 */
final class TranslationStore {

    /** 翻訳ファイルの先頭のコメント。{@code sync}はこれで始まるファイルだけを削除する。 */
    static final String HEADER =
            "# Translations of the Javadoc comments. Generated and updated by\n"
                    + "# `./gradlew syncJavadocTranslations` in libcobj/.\n"
                    + "# Fill in `en` for each `ja`. Do not edit `ja` by hand:\n"
                    + "# it must match the Japanese text in the source file.\n";

    /** 原文と訳文の組。 */
    static final class Entry {
        final String ja;
        final String en;

        Entry(String ja, String en) {
            this.ja = ja;
            this.en = en;
        }
    }

    /** 別のファイルで見つかった訳文。 */
    static final class Found {
        /** 採用した訳文。 */
        final String en;

        /** 採用した訳文を持つ翻訳ファイルの、翻訳ファイルのディレクトリからの相対パス。 */
        final String from;

        /** 同じ原文に別の訳文を付けているファイルがあるかどうか。 */
        final boolean conflicting;

        Found(String en, String from, boolean conflicting) {
            this.en = en;
            this.from = from;
            this.conflicting = conflicting;
        }
    }

    private final Path dir;

    /** ソースファイルの相対パスごとの、正規化した原文から訳文への対応。空の訳文は含まない。 */
    private final Map<String, Map<String, String>> byFile = new TreeMap<>();

    /** 正規化した原文ごとの、訳文からそれを持つ翻訳ファイルの一覧への対応。 */
    private final Map<String, Map<String, List<String>>> everywhere = new TreeMap<>();

    /** 読み込み時に見つかった、致命的でない問題。 */
    private final List<Problem> problems = new ArrayList<>();

    private TranslationStore(Path dir) {
        this.dir = dir;
    }

    /**
     * ディレクトリ以下の全ての翻訳ファイルを読み込む。ディレクトリが無ければ空として扱う。
     *
     * @param dir 翻訳ファイルのディレクトリ
     * @return 読み込んだ訳文
     * @throws IOException 読み込みに失敗した場合や、ファイルの形式が正しくない場合
     */
    static TranslationStore load(Path dir) throws IOException {
        TranslationStore store = new TranslationStore(dir);
        if (!Files.isDirectory(dir)) {
            return store;
        }
        for (Path yaml : Files2.list(dir, ".yaml")) {
            String rel = Files2.relative(dir, yaml);
            String source = rel.substring(0, rel.length() - ".yaml".length()) + ".java";
            String content = Files2.readUtf8(yaml);
            Map<String, String> map = new LinkedHashMap<>();
            for (Entry e : parse(content, yaml.toString())) {
                String norm = Text.normalize(e.ja);
                if (map.containsKey(norm)) {
                    store.problems.add(
                            Problem.warning(
                                    yaml,
                                    0,
                                    "the same Japanese text appears twice; the later entry is used:"
                                            + " "
                                            + Text.oneLine(e.ja)));
                }
                if (e.en.isEmpty()) {
                    map.remove(norm);
                    continue;
                }
                map.put(norm, e.en);
                store.everywhere
                        .computeIfAbsent(norm, k -> new TreeMap<>())
                        .computeIfAbsent(e.en, k -> new ArrayList<>())
                        .add(rel);
            }
            store.byFile.put(source, map);
        }
        return store;
    }

    Path dir() {
        return dir;
    }

    List<Problem> problems() {
        return Collections.unmodifiableList(problems);
    }

    /**
     * ソースファイルに対応する翻訳ファイルから訳文を引く。
     *
     * @param source ソースファイルの相対パス({@code /}区切り)
     * @param normalizedJa 正規化した原文
     * @return 訳文。無ければnull
     */
    String lookup(String source, String normalizedJa) {
        Map<String, String> map = byFile.get(source);
        return map == null ? null : map.get(normalizedJa);
    }

    /**
     * 全ての翻訳ファイルから訳文を探す。コメントを別のクラスへ移したときに、{@code sync}が訳文を引き継ぐために使う。
     * 複数の訳文がある場合は、最も多くのファイルで使われているものを採用する。
     *
     * @param normalizedJa 正規化した原文
     * @return 見つかった訳文。無ければnull
     */
    Found lookupElsewhere(String normalizedJa) {
        Map<String, List<String>> candidates = everywhere.get(normalizedJa);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        String best = null;
        List<String> bestFiles = null;
        for (Map.Entry<String, List<String>> c : candidates.entrySet()) {
            if (best == null || c.getValue().size() > bestFiles.size()) {
                best = c.getKey();
                bestFiles = c.getValue();
            }
        }
        return new Found(best, bestFiles.get(0), candidates.size() > 1);
    }

    /**
     * ソースファイルに対応する翻訳ファイルのパスを返す。
     *
     * @param dir 翻訳ファイルのディレクトリ
     * @param source ソースファイルの相対パス({@code /}区切り)
     * @return 翻訳ファイルのパス
     */
    static Path yamlPath(Path dir, String source) {
        return dir.resolve(source.substring(0, source.length() - ".java".length()) + ".yaml");
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * 翻訳ファイルの内容を読む。
     *
     * @param content ファイルの内容
     * @param name エラーメッセージに使うファイル名
     * @return 原文と訳文の組。訳文の末尾の空行は取り除く
     * @throws IOException ファイルの形式が正しくない場合
     */
    static List<Entry> parse(String content, String name) throws IOException {
        Object doc;
        try {
            doc = newYaml().load(content);
        } catch (RuntimeException e) {
            throw new IOException("cannot parse " + name + ": " + e.getMessage(), e);
        }
        if (doc == null) {
            return Collections.emptyList();
        }
        if (!(doc instanceof List)) {
            throw new IOException(name + ": the top level must be a list of entries");
        }
        List<Entry> entries = new ArrayList<>();
        int index = 0;
        for (Object item : (List<?>) doc) {
            index++;
            if (!(item instanceof Map)) {
                throw new IOException(
                        name + ": entry " + index + " must be a mapping with `ja` and `en`");
            }
            Map<?, ?> map = (Map<?, ?>) item;
            for (Object key : map.keySet()) {
                if (!"ja".equals(key) && !"en".equals(key)) {
                    throw new IOException(
                            name + ": entry " + index + " has an unknown key: " + key);
                }
            }
            Object ja = map.get("ja");
            Object en = map.get("en");
            if (!(ja instanceof String)) {
                throw new IOException(name + ": entry " + index + " has no `ja` text");
            }
            if (en != null && !(en instanceof String)) {
                throw new IOException(
                        name
                                + ": `en` of entry "
                                + index
                                + " is not a string but "
                                + en
                                + ". Write it as a literal block (`en: |-`) or quote it: "
                                + Text.oneLine((String) ja));
            }
            entries.add(new Entry((String) ja, en == null ? "" : stripEn((String) en)));
        }
        return entries;
    }

    private static String stripEn(String en) {
        return en.trim().isEmpty() ? "" : Text.stripTrailingBlankLines(en);
    }

    private static Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /**
     * 翻訳ファイルの内容を出力する。複数行の文字列を読みやすく保つため、なるべくYAMLのリテラルブロックで書く。
     *
     * @param entries 原文と訳文の組
     * @return YAMLの文字列
     */
    static String render(List<Entry> entries) {
        StringBuilder sb = new StringBuilder(HEADER);
        for (Entry e : entries) {
            sb.append("- ja: ").append(scalar(e.ja));
            sb.append("  en: ").append(scalar(e.en));
        }
        String content = sb.toString();
        verifyRoundTrip(entries, content);
        return content;
    }

    /**
     * 値を、マッピングの値として書くYAMLのスカラーにする。リテラルブロックで正しく読み戻せない値は、二重引用符でエスケープして書く。
     *
     * @param value 値
     * @return 改行で終わるYAMLのスカラー
     */
    static String scalar(String value) {
        if (value.isEmpty()) {
            return "\"\"\n";
        }
        if (literalSafe(value)) {
            String literal = literal(value);
            if (readsBackAs(literal, value)) {
                return literal;
            }
        }
        return quoted(value) + "\n";
    }

    private static String literal(String value) {
        StringBuilder sb = new StringBuilder();
        // 最初の空でない行が空白で始まると、YAMLはそこから字下げの幅を決めてしまう。その場合は幅を明示する。
        // 指示子は親のマッピングの字下げ(2)からの相対値なので、4桁の字下げは2になる。
        sb.append(firstNonEmptyLineIsIndented(value) ? "|2-" : "|-").append('\n');
        for (String line : Text.lines(value)) {
            sb.append(line.isEmpty() ? "" : "    " + line).append('\n');
        }
        return sb.toString();
    }

    /** リテラルブロックでは表せない文字(CR、改行として扱われる文字、制御文字など)を含まないかどうか。 */
    private static boolean literalSafe(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean printable =
                    c == '\n'
                            || c == '\t'
                            || (c >= 0x20 && c < 0x7f)
                            || (c >= 0xa0 && c <= 0xfffd && c != 0x2028 && c != 0x2029)
                            || Character.isSurrogate(c);
            if (!printable || c == 0xfeff) {
                return false;
            }
        }
        return true;
    }

    private static boolean readsBackAs(String literal, String value) {
        try {
            // 実際のファイルと同じく、リストの要素のマッピングの値として読む。
            Object doc = newYaml().load("- v: " + literal);
            if (!(doc instanceof List) || ((List<?>) doc).size() != 1) {
                return false;
            }
            Object item = ((List<?>) doc).get(0);
            return item instanceof Map && value.equals(((Map<?, ?>) item).get("v"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String quoted(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20 || (c >= 0x7f && c < 0xa0) || c >= 0x2028 && c <= 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else if (c == 0xfeff || c == 0xfffe || c == 0xffff) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.append('"').toString();
    }

    private static boolean firstNonEmptyLineIsIndented(String value) {
        for (String line : Text.lines(value)) {
            if (!line.isEmpty()) {
                return Character.isWhitespace(line.charAt(0));
            }
        }
        return false;
    }

    /** 出力した内容を読み戻して、元の値と一致することを確かめる。一致しなければこのツールの不具合である。 */
    private static void verifyRoundTrip(List<Entry> entries, String content) {
        List<Entry> back;
        try {
            back = parse(content, "(rendered translations)");
        } catch (IOException e) {
            throw new IllegalStateException("rendered YAML cannot be read back", e);
        }
        if (back.size() != entries.size()) {
            throw new IllegalStateException("rendered YAML has a different number of entries");
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry a = entries.get(i);
            Entry b = back.get(i);
            if (!a.ja.equals(b.ja) || !stripEn(a.en).equals(b.en)) {
                throw new IllegalStateException(
                        "rendered YAML does not read back the same value: " + Text.oneLine(a.ja));
            }
        }
    }
}
