package org.haganecobol.javadoci18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * libcobjのJavadocを、日本語から別の言語(現在は英語)に置き換えるためのツール。
 *
 * <p>ソースコードのJavadocは日本語のまま管理し、翻訳はソースファイルごとのYAMLファイルに「日本語の原文」と「訳文」の組として保存する。
 * 翻訳の単位はJavadocの構造に従い、本文と、{@code @param}・{@code @return}・{@code @throws}などのブロックタグの説明文をそれぞれ1単位とする。
 * 日本語を含まない単位は翻訳の対象にしない。
 *
 * <p>原文そのものをキーにするため、ソースの日本語が変更されると対応する訳文が見つからなくなり、未翻訳として検出される。 サブコマンドは以下の通り。
 *
 * <ul>
 *   <li>{@code apply}: ソースツリーをコピーしながらJavadocを訳文に置き換える。訳文の無い単位は日本語のまま残す。
 *   <li>{@code sync}: YAMLファイルをソースに合わせて更新する。新しい原文は空の訳文で追加し、使われなくなった原文は削除する。
 *   <li>{@code check}: 未翻訳の単位を一覧表示する。
 * </ul>
 */
public final class JavadocTranslator {

    private static final String USAGE =
            "usage: JavadocTranslator apply --source DIR --translations DIR --output DIR"
                    + " [--fail-on-missing]\n"
                    + "       JavadocTranslator sync --source DIR --translations DIR\n"
                    + "       JavadocTranslator check --source DIR --translations DIR"
                    + " [--fail-on-missing] [--github-actions]";

    /** 説明文の前に名前を取るブロックタグ。 */
    private static final Pattern NAMED_TAG = Pattern.compile("@(param|throws|exception)\\b");

    /** 説明文を翻訳するブロックタグ。 */
    private static final Pattern TRANSLATED_TAG =
            Pattern.compile("@(param|return|throws|exception|deprecated|serial|serialField)\\b");

    /** ブロックタグの開始行。 */
    private static final Pattern BLOCK_TAG_LINE = Pattern.compile("^\\s*@[a-zA-Z]+(\\s|$)");

    private JavadocTranslator() {}

    /**
     * コマンドラインのエントリポイント。
     *
     * @param args コマンドライン引数
     * @throws IOException ファイルの読み書きに失敗した場合
     */
    public static void main(String[] args) throws IOException {
        Options opts = Options.parse(args);
        if (opts == null) {
            System.err.println(USAGE);
            System.exit(2);
            return;
        }
        int status;
        switch (opts.command) {
            case "apply":
                status = apply(opts);
                break;
            case "sync":
                status = sync(opts);
                break;
            case "check":
                status = check(opts);
                break;
            default:
                System.err.println(USAGE);
                status = 2;
                break;
        }
        System.exit(status);
    }

    // ------------------------------------------------------------------
    // Subcommands
    // ------------------------------------------------------------------

    private static int apply(Options opts) throws IOException {
        if (opts.output == null) {
            System.err.println(USAGE);
            return 2;
        }
        Translations translations = Translations.load(opts.translations);
        List<Path> sources = listFiles(opts.source, ".java");
        Report report = new Report();
        for (Path src : sources) {
            Path rel = opts.source.relativize(src);
            String text = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);
            String translated = translateSource(rel, text, translations, report);
            Path dest = opts.output.resolve(rel.toString());
            Files.createDirectories(Objects.requireNonNull(dest.getParent()));
            Files.write(dest, translated.getBytes(StandardCharsets.UTF_8));
        }
        // package.html、overview.htmlやdoc-files/などJava以外のファイルはそのままコピーする。
        for (Path src : listFiles(opts.source, null)) {
            if (src.toString().endsWith(".java")) {
                continue;
            }
            Path dest = opts.output.resolve(opts.source.relativize(src).toString());
            Files.createDirectories(Objects.requireNonNull(dest.getParent()));
            Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        report.printSummary(System.out, false);
        return opts.failOnMissing && report.missingCount() > 0 ? 1 : 0;
    }

    private static int check(Options opts) throws IOException {
        Translations translations = Translations.load(opts.translations);
        Report report = new Report();
        for (Path src : listFiles(opts.source, ".java")) {
            Path rel = opts.source.relativize(src);
            String text = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);
            translateSource(rel, text, translations, report);
        }
        report.printDetails(System.out, opts.githubActions);
        report.printSummary(System.out, opts.githubActions);
        boolean failed =
                report.errorCount() > 0 || (opts.failOnMissing && report.missingCount() > 0);
        return failed ? 1 : 0;
    }

    private static int sync(Options opts) throws IOException {
        if (listFiles(opts.source, ".java").isEmpty()) {
            // パスの誤りで翻訳ファイルを全て削除してしまわないように、何もせずに終了する。
            System.err.println("sync: no Java source files found in " + opts.source);
            return 1;
        }
        Translations translations = Translations.load(opts.translations);
        int added = 0;
        int kept = 0;
        int written = 0;
        List<Path> expected = new ArrayList<>();
        for (Path src : listFiles(opts.source, ".java")) {
            Path rel = opts.source.relativize(src);
            String text = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);
            Map<String, Entry> entries = new LinkedHashMap<>();
            for (Comment comment : findComments(text)) {
                for (Unit unit : parseComment(comment.body).units) {
                    String key = normalize(unit.text);
                    if (entries.containsKey(key)) {
                        continue;
                    }
                    String en = translations.lookup(rel, key);
                    if (en == null) {
                        en = "";
                        added++;
                    } else {
                        kept++;
                    }
                    entries.put(key, new Entry(unit.text, en));
                }
            }
            Path yaml = Translations.yamlPath(opts.translations, rel);
            if (entries.isEmpty()) {
                continue;
            }
            expected.add(yaml.toAbsolutePath().normalize());
            String content = Translations.render(new ArrayList<>(entries.values()));
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (!Files.exists(yaml) || !java.util.Arrays.equals(Files.readAllBytes(yaml), bytes)) {
                Files.createDirectories(Objects.requireNonNull(yaml.getParent()));
                Files.write(yaml, bytes);
                written++;
            }
        }
        int removed = 0;
        if (Files.isDirectory(opts.translations)) {
            for (Path yaml : listFiles(opts.translations, ".yaml")) {
                if (!expected.contains(yaml.toAbsolutePath().normalize())) {
                    Files.delete(yaml);
                    removed++;
                }
            }
        }
        System.out.printf(
                "sync: %d translated entries kept, %d new entries to translate,"
                        + " %d files updated, %d obsolete files removed%n",
                kept, added, written, removed);
        return 0;
    }

    // ------------------------------------------------------------------
    // Translation of a source file
    // ------------------------------------------------------------------

    /**
     * 1つのソースファイルのJavadocを翻訳する。
     *
     * @param rel ソースツリーの根からの相対パス
     * @param text ソースファイルの内容
     * @param translations 訳文
     * @param report 未翻訳の単位などを記録する
     * @return Javadocを翻訳したソースファイルの内容
     */
    static String translateSource(Path rel, String text, Translations translations, Report report) {
        StringBuilder out = new StringBuilder(text.length());
        int pos = 0;
        for (Comment comment : findComments(text)) {
            out.append(text, pos, comment.start);
            ParsedComment parsed = parseComment(comment.body);
            boolean changed = false;
            for (Unit unit : parsed.units) {
                String en = translations.lookup(rel, normalize(unit.text));
                int line = lineNumber(text, comment.start);
                if (en == null || en.trim().isEmpty()) {
                    report.addMissing(rel, line, unit.text);
                    continue;
                }
                if (en.contains("*/")) {
                    report.addError(rel, line, "translation contains \"*/\": " + oneLine(en));
                    continue;
                }
                if (hasBlockTagLine(en)) {
                    report.addError(
                            rel,
                            line,
                            "a line after the first one starts with @ and would become a block tag:"
                                    + " "
                                    + oneLine(en));
                    continue;
                }
                if (containsJapanese(en)) {
                    report.addWarning(rel, line, "translation contains Japanese: " + oneLine(en));
                }
                unit.replacement = en;
                changed = true;
                report.translated();
            }
            if (changed) {
                boolean singleLine = text.substring(comment.start, comment.end).indexOf('\n') < 0;
                out.append(parsed.render(indentOf(text, comment.start), singleLine));
            } else {
                out.append(text, comment.start, comment.end);
            }
            pos = comment.end;
        }
        out.append(text, pos, text.length());
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Finding Javadoc comments
    // ------------------------------------------------------------------

    /** ソース中のJavadocコメントの位置と、先頭の{@code *}を取り除いた本文。 */
    static final class Comment {
        final int start;
        final int end;
        final List<String> body;

        Comment(int start, int end, List<String> body) {
            this.start = start;
            this.end = end;
            this.body = body;
        }
    }

    /**
     * ソースコードからJavadocコメントを探す。文字列リテラル、文字リテラル、通常のコメントの中は読み飛ばす。
     *
     * @param text ソースファイルの内容
     * @return 見つかったJavadocコメント
     */
    static List<Comment> findComments(String text) {
        List<Comment> result = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i, c);
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                int eol = text.indexOf('\n', i);
                i = eol < 0 ? n : eol;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int close = text.indexOf("*/", i + 2);
                int end = close < 0 ? n : close + 2;
                boolean javadoc =
                        i + 2 < n && text.charAt(i + 2) == '*' && close >= 0 && close > i + 2;
                if (javadoc) {
                    result.add(new Comment(i, end, stripComment(text.substring(i + 3, close))));
                }
                i = end;
            } else {
                i++;
            }
        }
        return result;
    }

    private static int skipLiteral(String text, int start, char quote) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote || c == '\n') {
                return i + 1;
            } else {
                i++;
            }
        }
        return i;
    }

    /** 各行の先頭の空白、{@code *}と、その後の空白1つを取り除き、前後の空行も取り除く。 */
    private static List<String> stripComment(String inner) {
        String[] raw = inner.split("\r?\n", -1);
        List<String> lines = new ArrayList<>();
        for (int k = 0; k < raw.length; k++) {
            String line = raw[k];
            if (k > 0) {
                int j = 0;
                while (j < line.length() && Character.isWhitespace(line.charAt(j))) {
                    j++;
                }
                if (j < line.length() && line.charAt(j) == '*') {
                    while (j < line.length() && line.charAt(j) == '*') {
                        j++;
                    }
                    line = line.substring(j);
                } else {
                    line = line.substring(j);
                }
            }
            if (line.startsWith(" ")) {
                line = line.substring(1);
            }
            lines.add(rtrim(line));
        }
        while (!lines.isEmpty() && lines.get(0).isEmpty()) {
            lines.remove(0);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // Parsing a Javadoc comment into translation units
    // ------------------------------------------------------------------

    /** 翻訳の単位。本文、またはブロックタグの説明文。 */
    static final class Unit {
        /** 説明文の前に出力する部分。本文では空、ブロックタグでは{@code "@param name "}など。 */
        final String prefix;

        /** 日本語の原文。 */
        final String text;

        /** 訳文。翻訳されない場合はnull。 */
        String replacement;

        Unit(String prefix, String text) {
            this.prefix = prefix;
            this.text = text;
        }
    }

    /** コメントを構成する部分。翻訳の単位か、そのまま出力する行の並び。 */
    static final class Part {
        final Unit unit;
        final List<String> verbatim;

        Part(Unit unit, List<String> verbatim) {
            this.unit = unit;
            this.verbatim = verbatim;
        }
    }

    /** 翻訳の単位に分解したJavadocコメント。 */
    static final class ParsedComment {
        final List<Part> parts = new ArrayList<>();
        final List<Unit> units = new ArrayList<>();

        /**
         * 訳文を埋め込んだコメントを出力する。
         *
         * @param indent コメントの開始位置の字下げ
         * @param singleLine 元のコメントが1行で書かれていた場合はtrue。訳文も1行なら1行のコメントとして出力する
         * @return {@code /**}から{@code *}{@code /}までのコメント
         */
        String render(String indent, boolean singleLine) {
            List<String> lines = new ArrayList<>();
            for (Part part : parts) {
                if (part.unit == null) {
                    lines.addAll(part.verbatim);
                    continue;
                }
                String body =
                        part.unit.replacement != null ? part.unit.replacement : part.unit.text;
                String[] bodyLines = body.split("\n", -1);
                for (int k = 0; k < bodyLines.length; k++) {
                    lines.add(k == 0 ? part.unit.prefix + bodyLines[k] : bodyLines[k]);
                }
            }
            if (singleLine && lines.size() == 1) {
                return "/** " + lines.get(0) + " */";
            }
            StringBuilder sb = new StringBuilder("/**\n");
            for (String line : lines) {
                sb.append(indent).append(line.isEmpty() ? " *" : " * " + line).append('\n');
            }
            sb.append(indent).append(" */");
            return sb.toString();
        }
    }

    /**
     * Javadocコメントの本文を翻訳の単位に分解する。日本語を含まない部分はそのまま出力する行として扱う。
     *
     * @param body 先頭の{@code *}を取り除いたコメントの各行
     * @return 分解したコメント
     */
    static ParsedComment parseComment(List<String> body) {
        // 本文とブロックタグごとの行の塊に分ける。<pre>の中の@で始まる行はブロックタグとみなさない。
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        blocks.add(current);
        boolean inPre = false;
        for (String line : body) {
            if (!inPre && BLOCK_TAG_LINE.matcher(line).find()) {
                current = new ArrayList<>();
                blocks.add(current);
            }
            current.add(line);
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.lastIndexOf("<pre") > lower.lastIndexOf("</pre>")) {
                inPre = true;
            } else if (lower.contains("</pre>")) {
                inPre = false;
            }
        }

        ParsedComment parsed = new ParsedComment();
        for (int b = 0; b < blocks.size(); b++) {
            List<String> block = blocks.get(b);
            if (block.isEmpty()) {
                continue;
            }
            // ブロックの末尾の空行は、翻訳の単位に含めずそのまま出力する。
            List<String> trailing = new ArrayList<>();
            while (!block.isEmpty() && block.get(block.size() - 1).isEmpty()) {
                trailing.add(0, block.remove(block.size() - 1));
            }
            String prefix = "";
            List<String> textLines = new ArrayList<>(block);
            if (b > 0) {
                String first = block.get(0);
                Matcher tag = TRANSLATED_TAG.matcher(first.trim());
                if (!tag.lookingAt()) {
                    parsed.parts.add(new Part(null, block));
                    addTrailing(parsed, trailing);
                    continue;
                }
                int leading = first.length() - ltrim(first).length();
                String rest = first.substring(leading + tag.end());
                int restStart = leading + tag.end();
                if (NAMED_TAG.matcher(first.trim()).lookingAt()) {
                    Matcher name = Pattern.compile("^(\\s+\\S+)").matcher(rest);
                    if (name.find()) {
                        restStart += name.end();
                        rest = rest.substring(name.end());
                    }
                }
                String afterSpace = ltrim(rest);
                restStart += rest.length() - afterSpace.length();
                prefix = first.substring(0, restStart);
                if (!prefix.endsWith(" ")) {
                    prefix = prefix + " ";
                }
                textLines.set(0, afterSpace);
            }
            String text = join(textLines);
            if (containsJapanese(text) && !text.trim().isEmpty()) {
                Unit unit = new Unit(prefix, text);
                parsed.units.add(unit);
                parsed.parts.add(new Part(unit, null));
            } else {
                parsed.parts.add(new Part(null, block));
            }
            addTrailing(parsed, trailing);
        }
        return parsed;
    }

    private static void addTrailing(ParsedComment parsed, List<String> trailing) {
        if (!trailing.isEmpty()) {
            parsed.parts.add(new Part(null, trailing));
        }
    }

    // ------------------------------------------------------------------
    // Translation files
    // ------------------------------------------------------------------

    /** 原文と訳文の組。 */
    static final class Entry {
        final String ja;
        final String en;

        Entry(String ja, String en) {
            this.ja = ja;
            this.en = en;
        }
    }

    /** 全ての翻訳ファイルの内容。 */
    static final class Translations {
        /** ソースの相対パスごとの、正規化した原文から訳文への対応。 */
        private final Map<String, Map<String, String>> byFile = new TreeMap<>();

        /** 全ファイルを通した、正規化した原文から訳文への対応。コメントが別のファイルへ移動した場合に使う。 */
        private final Map<String, String> global = new TreeMap<>();

        static Path yamlPath(Path dir, Path rel) {
            String name = rel.toString().replace('\\', '/');
            name = name.substring(0, name.length() - ".java".length()) + ".yaml";
            return dir.resolve(name);
        }

        static Translations load(Path dir) throws IOException {
            Translations t = new Translations();
            if (!Files.isDirectory(dir)) {
                return t;
            }
            for (Path yaml : listFiles(dir, ".yaml")) {
                String rel = dir.relativize(yaml).toString().replace('\\', '/');
                String key = rel.substring(0, rel.length() - ".yaml".length()) + ".java";
                Map<String, String> map = new LinkedHashMap<>();
                for (Entry e : parse(yaml)) {
                    if (e.en.trim().isEmpty()) {
                        continue;
                    }
                    String norm = normalize(e.ja);
                    map.put(norm, e.en);
                    if (!t.global.containsKey(norm)) {
                        t.global.put(norm, e.en);
                    }
                }
                t.byFile.put(key, map);
            }
            return t;
        }

        String lookup(Path rel, String normalizedJa) {
            Map<String, String> map = byFile.get(rel.toString().replace('\\', '/'));
            if (map != null && map.containsKey(normalizedJa)) {
                return map.get(normalizedJa);
            }
            return global.get(normalizedJa);
        }

        private static List<Entry> parse(Path yaml) throws IOException {
            Yaml parser = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object doc;
            try (InputStream in = Files.newInputStream(yaml)) {
                doc = parser.load(in);
            } catch (RuntimeException e) {
                throw new IOException("cannot parse " + yaml + ": " + e.getMessage(), e);
            }
            if (doc == null) {
                return Collections.emptyList();
            }
            if (!(doc instanceof List)) {
                throw new IOException(yaml + ": the top level must be a list");
            }
            List<Entry> entries = new ArrayList<>();
            for (Object item : (List<?>) doc) {
                if (!(item instanceof Map)) {
                    throw new IOException(yaml + ": each entry must be a mapping with ja and en");
                }
                Map<?, ?> map = (Map<?, ?>) item;
                Object ja = map.get("ja");
                Object en = map.get("en");
                if (!(ja instanceof String) || (en != null && !(en instanceof String))) {
                    throw new IOException(yaml + ": ja and en must be strings: " + map);
                }
                entries.add(new Entry((String) ja, en == null ? "" : (String) en));
            }
            return entries;
        }

        /**
         * 翻訳ファイルの内容を出力する。複数行の文字列を読みやすく保つため、YAMLのリテラルブロックで書く。
         *
         * @param entries 原文と訳文の組
         * @return YAMLの文字列
         */
        static String render(List<Entry> entries) {
            StringBuilder sb = new StringBuilder();
            sb.append("# Translations of the Javadoc comments. Generated and updated by\n")
                    .append("# `./gradlew syncJavadocTranslations` in libcobj/.\n")
                    .append("# Fill in `en` for each `ja`. Do not edit `ja` by hand:\n")
                    .append("# it must match the Japanese text in the source file.\n");
            for (Entry e : entries) {
                sb.append("- ja: ");
                appendScalar(sb, e.ja);
                sb.append("  en: ");
                appendScalar(sb, e.en);
            }
            return sb.toString();
        }

        private static void appendScalar(StringBuilder sb, String value) {
            if (value.isEmpty()) {
                sb.append("\"\"\n");
                return;
            }
            // 最初の空でない行が字下げされていると、YAMLがそこから字下げの幅を決めてしまい、
            // それより浅い後続の行を読めなくなる。その場合は字下げの幅を明示する。
            sb.append(firstNonEmptyLineIsIndented(value) ? "|4-" : "|-").append('\n');
            for (String line : value.split("\n", -1)) {
                sb.append(line.isEmpty() ? "" : "    " + line).append('\n');
            }
        }
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    /** 翻訳の結果。 */
    static final class Report {
        private final List<String> missing = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private int translated;

        void translated() {
            translated++;
        }

        void addMissing(Path rel, int line, String text) {
            missing.add(rel + ":" + line + ": " + oneLine(text));
        }

        void addError(Path rel, int line, String message) {
            errors.add(rel + ":" + line + ": " + message);
        }

        void addWarning(Path rel, int line, String message) {
            warnings.add(rel + ":" + line + ": " + message);
        }

        int missingCount() {
            return missing.size();
        }

        int errorCount() {
            return errors.size();
        }

        void printDetails(PrintStream out, boolean githubActions) {
            for (String m : missing) {
                out.println("untranslated: " + m);
            }
            for (String w : warnings) {
                out.println("warning: " + w);
            }
            for (String e : errors) {
                out.println((githubActions ? "::error::" : "error: ") + e);
            }
        }

        void printSummary(PrintStream out, boolean githubActions) {
            int total = translated + missing.size();
            String summary =
                    String.format(
                            "Javadoc translation: %d of %d units translated, %d untranslated,"
                                    + " %d errors, %d warnings",
                            translated, total, missing.size(), errors.size(), warnings.size());
            if (githubActions && !missing.isEmpty()) {
                out.println("::warning::" + summary);
            } else {
                out.println(summary);
            }
            if (!githubActions) {
                for (String e : errors) {
                    System.err.println("error: " + e);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    /** コマンドライン引数。 */
    static final class Options {
        String command;
        Path source;
        Path translations;
        Path output;
        boolean failOnMissing;
        boolean githubActions;

        static Options parse(String[] args) {
            if (args.length == 0) {
                return null;
            }
            Options o = new Options();
            o.command = args[0];
            for (int i = 1; i < args.length; i++) {
                String a = args[i];
                if ("--fail-on-missing".equals(a)) {
                    o.failOnMissing = true;
                } else if ("--github-actions".equals(a)) {
                    o.githubActions = true;
                } else if (i + 1 < args.length && "--source".equals(a)) {
                    o.source = Paths.get(args[++i]);
                } else if (i + 1 < args.length && "--translations".equals(a)) {
                    o.translations = Paths.get(args[++i]);
                } else if (i + 1 < args.length && "--output".equals(a)) {
                    o.output = Paths.get(args[++i]);
                } else {
                    return null;
                }
            }
            if (o.source == null || o.translations == null) {
                return null;
            }
            return o;
        }
    }

    /**
     * 比較用に原文を正規化する。行の折り返し位置の違いを無視するため、連続する空白を1つの空白にまとめる。
     *
     * @param text 原文
     * @return 正規化した原文
     */
    static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }

    /**
     * 日本語の文字(ひらがな、カタカナ、漢字、全角の記号)を含むかどうかを判定する。
     *
     * @param text 判定する文字列
     * @return 日本語の文字を含む場合はtrue
     */
    static boolean containsJapanese(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
            if (block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                    || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static List<Path> listFiles(Path dir, String suffix) throws IOException {
        final List<Path> files = new ArrayList<>();
        Files.walkFileTree(
                dir,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (suffix == null || file.toString().endsWith(suffix)) {
                            files.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        Collections.sort(files);
        return files;
    }

    private static int lineNumber(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String indentOf(String text, int offset) {
        int bol = text.lastIndexOf('\n', offset - 1) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = bol; i < offset && (text.charAt(i) == ' ' || text.charAt(i) == '\t'); i++) {
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    private static String ltrim(String s) {
        int start = 0;
        while (start < s.length() && Character.isWhitespace(s.charAt(start))) {
            start++;
        }
        return s.substring(start);
    }

    private static boolean firstNonEmptyLineIsIndented(String value) {
        for (String line : value.split("\n", -1)) {
            if (!line.isEmpty()) {
                return Character.isWhitespace(line.charAt(0));
            }
        }
        return false;
    }

    /** 2行目以降に、ブロックタグとして解釈されてしまう{@code @}で始まる行があるかどうかを判定する。 */
    private static boolean hasBlockTagLine(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 1; i < lines.length; i++) {
            if (BLOCK_TAG_LINE.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private static String oneLine(String text) {
        String s = normalize(text);
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }
}
