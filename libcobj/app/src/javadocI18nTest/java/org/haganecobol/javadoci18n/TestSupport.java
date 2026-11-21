package org.haganecobol.javadoci18n;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** テストで共通に使う関数。 */
final class TestSupport {

    private TestSupport() {}

    /** {@link JavadocTranslator#run}の結果。 */
    static final class Result {
        final int status;
        final String out;
        final String err;

        Result(int status, String out, String err) {
            this.status = status;
            this.out = out;
            this.err = err;
        }

        @Override
        public String toString() {
            return "status=" + status + "\n--- out\n" + out + "--- err\n" + err;
        }
    }

    static Result run(String... args) throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status =
                JavadocTranslator.run(
                        args,
                        new PrintStream(out, true, "UTF-8"),
                        new PrintStream(err, true, "UTF-8"));
        return new Result(
                status,
                new String(out.toByteArray(), StandardCharsets.UTF_8),
                new String(err.toByteArray(), StandardCharsets.UTF_8));
    }

    static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /**
     * 原文から訳文への対応を、翻訳ファイルの形式で書き出す。
     *
     * @param yaml 翻訳ファイル
     * @param pairs 原文、訳文、原文、訳文…の並び
     * @throws IOException 書き込みに失敗した場合
     */
    static void writeTranslations(Path yaml, String... pairs) throws IOException {
        List<TranslationStore.Entry> entries = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            entries.add(new TranslationStore.Entry(pairs[i], pairs[i + 1]));
        }
        write(yaml, TranslationStore.render(entries));
    }

    /**
     * 1つのソースファイルを、与えた訳文で翻訳する。
     *
     * @param dir 作業用のディレクトリ
     * @param source ソースファイルの内容
     * @param pairs 原文、訳文、原文、訳文…の並び
     * @return 翻訳したソースと集計
     * @throws IOException ファイルの読み書きに失敗した場合
     */
    static Map.Entry<String, Report> translate(Path dir, String source, String... pairs)
            throws IOException {
        Path tr = dir.resolve("tr");
        writeTranslations(tr.resolve("A.yaml"), pairs);
        TranslationStore store = TranslationStore.load(tr);
        Report report = new Report();
        String out =
                JavadocTranslator.translate(
                        new JavadocTranslator.SourceFile("A.java", dir.resolve("A.java"), source),
                        store,
                        report);
        Map<String, Report> result = new LinkedHashMap<>();
        result.put(out, report);
        return result.entrySet().iterator().next();
    }

    /**
     * ソースの翻訳の単位の原文を、正規化して出現順に返す。
     *
     * @param source ソースファイルの内容
     * @return 正規化した原文
     */
    static List<String> unitTexts(String source) {
        List<String> texts = new ArrayList<>();
        for (JavadocParser.Unit unit : JavadocParser.units(source)) {
            texts.add(Text.normalize(unit.text));
        }
        return texts;
    }

    /**
     * Javadocコメントを取り除いたソースを返す。
     *
     * @param source ソースファイルの内容
     * @return Javadocコメントを空にしたソース
     */
    static String withoutJavadoc(String source) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (JavadocParser.Comment c : JavadocParser.findComments(source)) {
            sb.append(source, pos, c.start).append("/***/");
            pos = c.end;
        }
        return sb.append(source, pos, source.length()).toString();
    }
}
