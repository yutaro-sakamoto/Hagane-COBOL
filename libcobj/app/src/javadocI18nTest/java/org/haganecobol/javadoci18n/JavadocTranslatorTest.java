package org.haganecobol.javadoci18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavadocTranslatorTest {

    @TempDir Path dir;

    private Path src() {
        return dir.resolve("src");
    }

    private Path tr() {
        return dir.resolve("tr");
    }

    private TestSupport.Result check(String... extra) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("check");
        args.add("--source");
        args.add(src().toString());
        args.add("--translations");
        args.add(tr().toString());
        args.add("--repo-root");
        args.add(dir.toString());
        for (String e : extra) {
            args.add(e);
        }
        return TestSupport.run(args.toArray(new String[0]));
    }

    private TestSupport.Result sync() throws Exception {
        return TestSupport.run(
                "sync", "--source", src().toString(), "--translations", tr().toString());
    }

    // ------------------------------------------------------------------
    // The workflow of sync, check and apply
    // ------------------------------------------------------------------

    @Test
    void syncThenTranslateThenApply() throws Exception {
        TestSupport.write(src().resolve("p/A.java"), "package p;\n/** 値を返す。 */\nclass A {}\n");

        TestSupport.Result r = check("--fail-on-missing");
        assertEquals(JavadocTranslator.FAILED, r.status, r.toString());
        assertTrue(r.out.contains("1 untranslated"), r.toString());
        assertTrue(r.out.contains(Report.HOW_TO_FIX), r.toString());

        r = sync();
        assertEquals(0, r.status, r.toString());
        Path yaml = tr().resolve("p/A.yaml");
        assertEquals(
                TranslationStore.HEADER + "- ja: |-\n    値を返す。\n  en: \"\"\n",
                TestSupport.read(yaml),
                "skeleton");

        TestSupport.writeTranslations(yaml, "値を返す。", "Returns the value.");
        r = check("--fail-on-missing");
        assertEquals(0, r.status, r.toString());

        Path out = dir.resolve("out");
        r =
                TestSupport.run(
                        "apply",
                        "--source",
                        src().toString(),
                        "--translations",
                        tr().toString(),
                        "--output",
                        out.toString(),
                        "--fail-on-missing");
        assertEquals(0, r.status, r.toString());
        assertEquals(
                "package p;\n/** Returns the value. */\nclass A {}\n",
                TestSupport.read(out.resolve("p/A.java")),
                "translated source");

        r = sync();
        assertTrue(r.out.contains("0 files updated"), "sync is idempotent: " + r);
    }

    @Test
    void translationsAreLookedUpOnlyInTheirOwnFile() throws Exception {
        TestSupport.write(src().resolve("A.java"), "/** 値を返す。 */ class A {}\n");
        TestSupport.write(src().resolve("B.java"), "/** 値を返す。 */ class B {}\n");
        TestSupport.writeTranslations(tr().resolve("A.yaml"), "値を返す。", "Returns X.");

        TestSupport.Result r = check("--fail-on-missing");
        assertEquals(JavadocTranslator.FAILED, r.status, r.toString());
        assertTrue(r.out.contains("untranslated: src/B.java:1"), r.toString());
        assertTrue(r.out.contains("out of sync: tr/B.yaml"), r.toString());

        r = sync();
        assertTrue(r.out.contains("copied the translation from A.yaml to B.java"), r.toString());
        assertTrue(TestSupport.read(tr().resolve("B.yaml")).contains("Returns X."), "copied");
        assertEquals(0, check("--fail-on-missing").status, "after sync");
    }

    @Test
    void obsoleteFilesAreReportedAndRemoved() throws Exception {
        TestSupport.write(src().resolve("A.java"), "/** あ。 */ class A {}\n");
        TestSupport.write(src().resolve("B.java"), "/** い。 */ class B {}\n");
        TestSupport.write(src().resolve("C.java"), "/** う。 */ class C {}\n");
        TestSupport.writeTranslations(tr().resolve("A.yaml"), "あ。", "A.");
        TestSupport.writeTranslations(tr().resolve("B.yaml"), "い。", "B.");
        TestSupport.writeTranslations(tr().resolve("C.yaml"), "う。", "C.");
        TestSupport.writeTranslations(tr().resolve("old/D.yaml"), "え。", "D.");
        TestSupport.write(tr().resolve("notes.yaml"), "- ja: お\n  en: E\n");

        TestSupport.Result r = check("--fail-on-missing");
        assertEquals(JavadocTranslator.FAILED, r.status, r.toString());
        assertTrue(r.out.contains("out of sync: tr/old/D.yaml"), r.toString());

        r = sync();
        assertEquals(0, r.status, r.toString());
        assertFalse(Files.exists(tr().resolve("old")), "empty directory removed");
        assertTrue(Files.exists(tr().resolve("notes.yaml")), "file without the header kept");
        assertTrue(r.err.contains("notes.yaml is not used"), r.toString());
    }

    @Test
    void syncRefusesToDeleteMostFiles() throws Exception {
        TestSupport.write(src().resolve("a/A.java"), "/** あ。 */ class A {}\n");
        TestSupport.write(src().resolve("b/B.java"), "/** い。 */ class B {}\n");
        TestSupport.write(src().resolve("b/C.java"), "/** う。 */ class C {}\n");
        assertEquals(0, sync().status, "first sync");

        // --sourceにサブディレクトリを渡してしまった場合。
        TestSupport.Result r =
                TestSupport.run(
                        "sync",
                        "--source",
                        src().resolve("a").toString(),
                        "--translations",
                        tr().toString());
        assertEquals(JavadocTranslator.BAD_INPUT, r.status, r.toString());
        assertTrue(r.err.contains("refusing to delete"), r.toString());
        assertTrue(Files.exists(tr().resolve("b/C.yaml")), "nothing deleted");
    }

    @Test
    void brokenTranslationIsAnError() throws Exception {
        TestSupport.write(src().resolve("A.java"), "/** あ。 */ class A {}\n");
        TestSupport.writeTranslations(tr().resolve("A.yaml"), "あ。", "a */ b");
        TestSupport.Result r = check();
        assertEquals(JavadocTranslator.FAILED, r.status, "errors fail even without strict mode");
        assertTrue(
                r.out.contains("error: src/A.java:1: the translation contains \"*/\""),
                r.toString());
    }

    @Test
    void githubAnnotations() throws Exception {
        TestSupport.write(src().resolve("A.java"), "\n/** 100% あ。 */ class A {}\n");
        TestSupport.Result r = check("--fail-on-missing", "--github-actions");
        assertTrue(r.out.startsWith("::error::Javadoc translation:"), "summary first: " + r);
        assertTrue(
                r.out.contains(
                        "::error file=src/A.java,line=2,title=Javadoc translation%3A untranslated::"
                                + "no translation in A.yaml: 100%25 あ。"),
                r.toString());
    }

    @Test
    void badInput() throws Exception {
        assertEquals(JavadocTranslator.BAD_INPUT, TestSupport.run().status, "no arguments");
        assertEquals(
                JavadocTranslator.BAD_INPUT,
                TestSupport.run("frobnicate", "--source", "x", "--translations", "y").status,
                "unknown subcommand");
        assertEquals(
                JavadocTranslator.BAD_INPUT,
                TestSupport.run("sync", "--source", "x", "--translations", "y", "--output", "z")
                        .status,
                "option not allowed for sync");
        Files.createDirectories(src());
        TestSupport.Result r = check("--fail-on-missing");
        assertEquals(JavadocTranslator.BAD_INPUT, r.status, "no Java files: " + r);
        TestSupport.write(src().resolve("A.java"), "/** あ。 */ class A {}\n");
        TestSupport.write(tr().resolve("A.yaml"), "- ja: あ。\n  en: yes\n");
        r = check();
        assertEquals(JavadocTranslator.BAD_INPUT, r.status, r.toString());
        assertTrue(r.err.startsWith("error: "), r.toString());
        r =
                TestSupport.run(
                        "apply",
                        "--source",
                        src().toString(),
                        "--translations",
                        tr().toString(),
                        "--output",
                        src().resolve("out").toString());
        assertEquals(JavadocTranslator.BAD_INPUT, r.status, "output inside source: " + r);
    }

    // ------------------------------------------------------------------
    // The real sources and translations of libcobj
    // ------------------------------------------------------------------

    private static List<JavadocTranslator.SourceFile> realSources() throws IOException {
        String dir = System.getProperty("javadocI18n.sourceDir");
        if (dir == null) {
            return new ArrayList<>();
        }
        return JavadocTranslator.SourceFile.loadAll(Paths.get(dir));
    }

    @Test
    void realSourcesAreUnchangedWithoutTranslations() throws IOException {
        TranslationStore empty = TranslationStore.load(dir.resolve("none"));
        for (JavadocTranslator.SourceFile src : realSources()) {
            assertEquals(src.text, JavadocTranslator.translate(src, empty, new Report()), src.rel);
        }
    }

    @Test
    void identityTranslationKeepsTheStructureOfRealSources() throws IOException {
        // 原文をそのまま訳文として埋め込んでも、翻訳の単位とコメントの外側は変わらない。
        List<JavadocTranslator.SourceFile> sources = realSources();
        for (JavadocTranslator.SourceFile src : sources) {
            Map<String, TranslationStore.Entry> entries = new LinkedHashMap<>();
            for (JavadocParser.Unit unit : JavadocParser.units(src.text)) {
                entries.putIfAbsent(
                        Text.normalize(unit.text),
                        new TranslationStore.Entry(unit.text, unit.text));
            }
            TestSupport.write(
                    TranslationStore.yamlPath(dir.resolve("identity"), src.rel),
                    TranslationStore.render(new ArrayList<>(entries.values())));
        }
        TranslationStore identity = TranslationStore.load(dir.resolve("identity"));
        for (JavadocTranslator.SourceFile src : sources) {
            Report report = new Report();
            String out = JavadocTranslator.translate(src, identity, report);
            assertEquals(0, report.count(Problem.Kind.ERROR), src.rel);
            assertEquals(TestSupport.unitTexts(src.text), TestSupport.unitTexts(out), src.rel);
            assertEquals(
                    TestSupport.withoutJavadoc(src.text), TestSupport.withoutJavadoc(out), src.rel);
        }
    }

    @Test
    void realTranslationsAreCompleteAndInSync() throws Exception {
        String source = System.getProperty("javadocI18n.sourceDir");
        String translations = System.getProperty("javadocI18n.translationDir");
        if (source == null || translations == null) {
            return;
        }
        TestSupport.Result r =
                TestSupport.run(
                        "check",
                        "--source",
                        source,
                        "--translations",
                        translations,
                        "--fail-on-missing");
        assertEquals(0, r.status, r.toString());
    }
}
