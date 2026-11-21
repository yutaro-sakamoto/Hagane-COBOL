package org.haganecobol.javadoci18n;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * libcobjのJavadocを、日本語から別の言語(現在は英語)に置き換えるためのツール。
 *
 * <p>ソースコードのJavadocは日本語のまま管理し、翻訳はソースファイルごとのYAMLファイルに「日本語の原文」と「訳文」の組として保存する。
 * 翻訳の単位はJavadocの構造に従い、本文と、ブロックタグの説明文をそれぞれ1単位とする({@link JavadocParser})。
 * 原文そのものをキーにするため、ソースの日本語が変更されると対応する訳文が見つからなくなり、未翻訳として検出される。
 *
 * <p>サブコマンドは以下の通り。
 *
 * <ul>
 *   <li>{@code apply}: ソースツリーをコピーしながらJavadocを訳文に置き換える。訳文の無い単位は日本語のまま残す。
 *   <li>{@code sync}: 翻訳ファイルをソースに合わせて更新する。新しい原文を追加し(別のファイルに同じ原文の訳文があれば引き継ぐ)、使われなくなった原文とファイルを削除する。
 *   <li>{@code check}: 未翻訳の単位、正しくない訳文、{@code sync}の結果と一致しない翻訳ファイルを報告する。
 * </ul>
 *
 * <p>終了コードは、成功なら0、検査で失敗したら1、引数や入力ファイルが正しくなければ2。
 */
public final class JavadocTranslator {

    private static final String USAGE =
            "usage: JavadocTranslator apply --source DIR --translations DIR --output DIR"
                    + " [--fail-on-missing] [--repo-root DIR]\n"
                    + "       JavadocTranslator sync --source DIR --translations DIR\n"
                    + "       JavadocTranslator check --source DIR --translations DIR"
                    + " [--fail-on-missing] [--github-actions] [--repo-root DIR]\n"
                    + "  --fail-on-missing  also fail on untranslated texts and on translation"
                    + " files that `sync` would change";

    /** 終了コード: 検査で失敗した。 */
    static final int FAILED = 1;

    /** 終了コード: 引数や入力ファイルが正しくない。 */
    static final int BAD_INPUT = 2;

    private JavadocTranslator() {}

    /**
     * コマンドラインのエントリポイント。
     *
     * @param args コマンドライン引数
     * @throws IOException 標準出力の文字コードを設定できない場合
     */
    public static void main(String[] args) throws IOException {
        // 未翻訳の原文などの日本語を、プラットフォームの文字コードによらずに出力する。
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, "UTF-8");
        System.exit(run(args, out, err));
    }

    /**
     * ツールを実行する。
     *
     * @param args コマンドライン引数
     * @param out 標準出力
     * @param err 標準エラー出力
     * @return 終了コード
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        Options opts;
        try {
            opts = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            err.println(USAGE);
            return BAD_INPUT;
        }
        try {
            switch (opts.command) {
                case "apply":
                    return apply(opts, out);
                case "sync":
                    return sync(opts, out, err);
                default:
                    return check(opts, out);
            }
        } catch (IOException | IllegalStateException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            if (opts.githubActions) {
                out.println("::error::" + Report.escapeData(message));
            }
            err.println("error: " + message);
            return BAD_INPUT;
        }
    }

    // ------------------------------------------------------------------
    // Subcommands
    // ------------------------------------------------------------------

    private static int apply(Options opts, PrintStream out) throws IOException {
        Path source = opts.source.toAbsolutePath().normalize();
        Path output = opts.output.toAbsolutePath().normalize();
        if (output.startsWith(source) || source.startsWith(output)) {
            throw new IOException("--output must not overlap --source: " + opts.output);
        }
        List<SourceFile> sources = SourceFile.loadAll(opts.source);
        TranslationStore store = TranslationStore.load(opts.translations);
        Report report = new Report();
        report.addAll(store.problems());
        for (SourceFile src : sources) {
            Path dest = opts.output.resolve(src.rel);
            Files2.createParentDirectories(dest);
            Files.write(dest, translate(src, store, report).getBytes(StandardCharsets.UTF_8));
        }
        // package.html、overview.htmlやdoc-files/などJava以外のファイルはそのままコピーする。
        for (Path file : Files2.list(opts.source, null)) {
            if (file.toString().endsWith(".java")) {
                continue;
            }
            Path dest = opts.output.resolve(Files2.relative(opts.source, file));
            Files2.createParentDirectories(dest);
            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        report.print(out, false, opts.failOnMissing, opts.repoRoot, opts.failOnMissing);
        return report.failed(opts.failOnMissing) ? FAILED : 0;
    }

    private static int check(Options opts, PrintStream out) throws IOException {
        List<SourceFile> sources = SourceFile.loadAll(opts.source);
        TranslationStore store = TranslationStore.load(opts.translations);
        Report report = new Report();
        report.addAll(store.problems());
        for (SourceFile src : sources) {
            translate(src, store, report);
        }
        SyncPlan plan = SyncPlan.compute(sources, store);
        for (Map.Entry<Path, String> e : plan.files.entrySet()) {
            Path yaml = e.getKey();
            if (!Files.exists(yaml)) {
                report.add(Problem.outOfSync(yaml, "the translation file is missing"));
            } else if (!Files2.readUtf8(yaml).replace("\r\n", "\n").equals(e.getValue())) {
                report.add(
                        Problem.outOfSync(
                                yaml,
                                "the translation file does not match the Javadoc comments in the"
                                        + " source"));
            }
        }
        for (Path yaml : existingTranslationFiles(opts.translations)) {
            if (plan.files.containsKey(yaml)) {
                continue;
            }
            if (Files2.readUtf8(yaml).startsWith(TranslationStore.HEADER)) {
                report.add(
                        Problem.outOfSync(
                                yaml, "the translation file is no longer used by any source file"));
            } else {
                report.add(
                        Problem.error(
                                yaml,
                                0,
                                "the file is not used by any source file and was not written by"
                                        + " sync, so sync leaves it alone. Delete it by hand"));
            }
        }
        report.print(out, opts.githubActions, opts.failOnMissing, opts.repoRoot, true);
        return report.failed(opts.failOnMissing) ? FAILED : 0;
    }

    private static int sync(Options opts, PrintStream out, PrintStream err) throws IOException {
        List<SourceFile> sources = SourceFile.loadAll(opts.source);
        TranslationStore store = TranslationStore.load(opts.translations);
        for (Problem p : store.problems()) {
            err.println("warning: " + p.file + ": " + p.message);
        }
        SyncPlan plan = SyncPlan.compute(sources, store);

        List<Path> existing = existingTranslationFiles(opts.translations);
        List<Path> obsolete = new ArrayList<>();
        for (Path yaml : existing) {
            if (plan.files.containsKey(yaml)) {
                continue;
            }
            if (Files2.readUtf8(yaml).startsWith(TranslationStore.HEADER)) {
                obsolete.add(yaml);
            } else {
                err.println(
                        "warning: "
                                + yaml
                                + " is not used, but is left alone because it was not written by"
                                + " sync");
            }
        }
        // --sourceの誤りで翻訳ファイルをまとめて消してしまわないように、半分を超える削除は拒否する。
        if (obsolete.size() > 1 && obsolete.size() * 2 > existing.size()) {
            throw new IOException(
                    "refusing to delete "
                            + obsolete.size()
                            + " of "
                            + existing.size()
                            + " translation files. Check --source ("
                            + opts.source
                            + "), or delete the files by hand");
        }

        int written = 0;
        for (Map.Entry<Path, String> e : plan.files.entrySet()) {
            Path yaml = e.getKey();
            byte[] bytes = e.getValue().getBytes(StandardCharsets.UTF_8);
            if (!Files.exists(yaml) || !Arrays.equals(Files.readAllBytes(yaml), bytes)) {
                Files2.createParentDirectories(yaml);
                Files.write(yaml, bytes);
                written++;
            }
        }
        for (Path yaml : obsolete) {
            Files.delete(yaml);
            Files2.removeEmptyParents(yaml, opts.translations);
        }
        for (String note : plan.notes) {
            out.println(note);
        }
        out.printf(
                "sync: %d translated entries kept, %d translations copied from other files,"
                        + " %d new entries to translate, %d files updated, %d obsolete files"
                        + " removed%n",
                plan.kept, plan.copied, plan.added, written, obsolete.size());
        return 0;
    }

    private static List<Path> existingTranslationFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            for (Path yaml : Files2.list(dir, ".yaml")) {
                files.add(yaml.toAbsolutePath().normalize());
            }
        }
        return files;
    }

    // ------------------------------------------------------------------
    // Translation
    // ------------------------------------------------------------------

    /**
     * 1つのソースファイルのJavadocを翻訳する。訳文はソースファイルに対応する翻訳ファイルからだけ引く。
     *
     * @param src ソースファイル
     * @param store 訳文
     * @param report 未翻訳の単位などを記録する
     * @return Javadocを翻訳したソースファイルの内容
     */
    static String translate(SourceFile src, TranslationStore store, Report report) {
        String text = src.text;
        Path yaml = TranslationStore.yamlPath(store.dir(), src.rel);
        String newline = JavadocParser.newlineOf(text);
        StringBuilder out = new StringBuilder(text.length());
        int pos = 0;
        for (JavadocParser.Comment comment : JavadocParser.findComments(text)) {
            out.append(text, pos, comment.start);
            JavadocParser.ParsedComment parsed = JavadocParser.parse(comment.body);
            Map<JavadocParser.Unit, String> replacements = new IdentityHashMap<>();
            for (JavadocParser.Unit unit : parsed.units) {
                report.unit();
                String en = store.lookup(src.rel, Text.normalize(unit.text));
                if (en == null) {
                    report.add(
                            Problem.untranslated(
                                    src.path,
                                    comment.lineNumber,
                                    "no translation in "
                                            + yaml.getFileName()
                                            + ": "
                                            + Text.oneLine(unit.text)));
                    continue;
                }
                String error = JavadocParser.checkTranslation(unit, en);
                if (error != null) {
                    report.add(
                            Problem.error(
                                    src.path, comment.lineNumber, error + ": " + Text.oneLine(en)));
                    continue;
                }
                if (Text.containsJapanese(en)) {
                    report.add(
                            Problem.warning(
                                    src.path,
                                    comment.lineNumber,
                                    "the translation contains Japanese: " + Text.oneLine(en)));
                }
                replacements.put(unit, en);
                report.translated();
            }
            if (replacements.isEmpty()) {
                out.append(text, comment.start, comment.end);
            } else {
                String indent = JavadocParser.indentOf(text, comment.start);
                out.append(parsed.render(comment, indent, newline, replacements));
            }
            pos = comment.end;
        }
        out.append(text, pos, text.length());
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Sources and the result of sync
    // ------------------------------------------------------------------

    /** 読み込んだソースファイル。 */
    static final class SourceFile {
        /** ソースツリーの根からの相対パス({@code /}区切り)。 */
        final String rel;

        final Path path;

        final String text;

        SourceFile(String rel, Path path, String text) {
            this.rel = rel;
            this.path = path;
            this.text = text;
        }

        /**
         * ソースツリーの全てのJavaファイルを読み込む。
         *
         * @param root ソースツリーの根
         * @return ソースファイル
         * @throws IOException ディレクトリが無い場合、Javaファイルが1つも無い場合、読み込みに失敗した場合
         */
        static List<SourceFile> loadAll(Path root) throws IOException {
            if (!Files.isDirectory(root)) {
                throw new IOException("the source directory does not exist: " + root);
            }
            List<SourceFile> sources = new ArrayList<>();
            for (Path file : Files2.list(root, ".java")) {
                sources.add(
                        new SourceFile(Files2.relative(root, file), file, Files2.readUtf8(file)));
            }
            if (sources.isEmpty()) {
                throw new IOException("no Java source files found in " + root);
            }
            return sources;
        }
    }

    /** {@code sync}で書き込むべき翻訳ファイルの内容。 */
    static final class SyncPlan {
        /** 翻訳ファイルの絶対パスから内容への対応。 */
        final Map<Path, String> files = new TreeMap<>();

        /** 別のファイルから訳文を引き継いだことなどの報告。 */
        final List<String> notes = new ArrayList<>();

        int kept;
        int copied;
        int added;

        /**
         * ソースと現在の訳文から、{@code sync}の結果を計算する。ファイルには書き込まない。
         *
         * @param sources ソースファイル
         * @param store 現在の訳文
         * @return 書き込むべき内容
         */
        static SyncPlan compute(List<SourceFile> sources, TranslationStore store) {
            SyncPlan plan = new SyncPlan();
            Set<String> reported = new HashSet<>();
            for (SourceFile src : sources) {
                Map<String, TranslationStore.Entry> entries = new LinkedHashMap<>();
                for (JavadocParser.Unit unit : JavadocParser.units(src.text)) {
                    String key = Text.normalize(unit.text);
                    if (entries.containsKey(key)) {
                        continue;
                    }
                    String en = store.lookup(src.rel, key);
                    if (en != null) {
                        plan.kept++;
                    } else {
                        TranslationStore.Found found = store.lookupElsewhere(key);
                        if (found != null) {
                            en = found.en;
                            plan.copied++;
                            if (reported.add(src.rel + "\n" + key)) {
                                plan.notes.add(
                                        "copied the translation from "
                                                + found.from
                                                + " to "
                                                + src.rel
                                                + (found.conflicting
                                                        ? " (other files translate it differently)"
                                                        : "")
                                                + ": "
                                                + Text.oneLine(unit.text));
                            }
                        } else {
                            en = "";
                            plan.added++;
                        }
                    }
                    entries.put(key, new TranslationStore.Entry(unit.text, en));
                }
                if (!entries.isEmpty()) {
                    Path yaml =
                            TranslationStore.yamlPath(store.dir(), src.rel)
                                    .toAbsolutePath()
                                    .normalize();
                    plan.files.put(
                            yaml, TranslationStore.render(new ArrayList<>(entries.values())));
                }
            }
            return plan;
        }
    }

    // ------------------------------------------------------------------
    // Command line
    // ------------------------------------------------------------------

    /** コマンドライン引数。 */
    static final class Options {
        String command;
        Path source;
        Path translations;
        Path output;
        Path repoRoot;
        boolean failOnMissing;
        boolean githubActions;

        /**
         * コマンドライン引数を解析する。
         *
         * @param args コマンドライン引数
         * @return 解析した引数
         * @throws IllegalArgumentException 引数が正しくない場合
         */
        static Options parse(String[] args) {
            if (args.length == 0) {
                throw new IllegalArgumentException("no subcommand given");
            }
            Options o = new Options();
            o.command = args[0];
            List<String> allowed;
            switch (o.command) {
                case "apply":
                    allowed =
                            Arrays.asList(
                                    "--source",
                                    "--translations",
                                    "--output",
                                    "--fail-on-missing",
                                    "--repo-root");
                    break;
                case "sync":
                    allowed = Arrays.asList("--source", "--translations");
                    break;
                case "check":
                    allowed =
                            Arrays.asList(
                                    "--source",
                                    "--translations",
                                    "--fail-on-missing",
                                    "--github-actions",
                                    "--repo-root");
                    break;
                default:
                    throw new IllegalArgumentException("unknown subcommand: " + o.command);
            }
            for (int i = 1; i < args.length; i++) {
                String a = args[i];
                if (!allowed.contains(a)) {
                    throw new IllegalArgumentException(
                            "unknown option for " + o.command + ": " + a);
                }
                if ("--fail-on-missing".equals(a)) {
                    o.failOnMissing = true;
                    continue;
                }
                if ("--github-actions".equals(a)) {
                    o.githubActions = true;
                    continue;
                }
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(a + " needs a directory");
                }
                Path value = Paths.get(args[++i]);
                switch (a) {
                    case "--source":
                        o.source = value;
                        break;
                    case "--translations":
                        o.translations = value;
                        break;
                    case "--output":
                        o.output = value;
                        break;
                    default:
                        o.repoRoot = value;
                        break;
                }
            }
            if (o.source == null) {
                throw new IllegalArgumentException("--source is required");
            }
            if (o.translations == null) {
                throw new IllegalArgumentException("--translations is required");
            }
            if ("apply".equals(o.command) && o.output == null) {
                throw new IllegalArgumentException("--output is required");
            }
            return o;
        }
    }
}
