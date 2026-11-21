package org.haganecobol.javadoci18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavadocParserTest {

    @TempDir Path dir;

    private static JavadocParser.Unit onlyUnit(String source) {
        List<JavadocParser.Unit> units = JavadocParser.units(source);
        assertEquals(1, units.size(), "number of units in: " + source);
        return units.get(0);
    }

    // ------------------------------------------------------------------
    // Finding comments
    // ------------------------------------------------------------------

    @Test
    void skipsLiteralsAndOrdinaryComments() {
        String src =
                "class A {\n"
                        + "  String s = \"/** 文字列 */\";\n"
                        + "  char c = '\"';\n"
                        + "  char d = '\\'';\n"
                        + "  // /** 行コメント */\n"
                        + "  /* /** 通常のコメント */\n"
                        + "  String t = \"\"\"\n"
                        + "      /** テキストブロック */\n"
                        + "      \"\"\";\n"
                        + "  /** 本物。 */\n"
                        + "  int x;\n"
                        + "}\n";
        assertEquals(Collections.singletonList("本物。"), TestSupport.unitTexts(src));
        assertEquals(10, JavadocParser.findComments(src).get(0).lineNumber, "line number");
    }

    @Test
    void emptyAndStarredComments() {
        assertEquals(Collections.emptyList(), TestSupport.unitTexts("/**/ /** */ class A {}"));
        assertEquals(
                Arrays.asList("星三つ。", "複数行の 星三つ。"),
                TestSupport.unitTexts("/*** 星三つ。 */\n/***\n * 複数行の\n * 星三つ。\n */\n"));
    }

    // ------------------------------------------------------------------
    // Block tags
    // ------------------------------------------------------------------

    @Test
    void splitsBlockTags() {
        String src =
                "/**\n"
                        + " * 本文。\n"
                        + " *\n"
                        + " * @param <T> 型\n"
                        + " * @param x 引数\n"
                        + " * @return 戻り値\n"
                        + " * @throws java.io.IOException 失敗した場合\n"
                        + " * @see #f(int, String) ラベル\n"
                        + " * @see \"文字列\"\n"
                        + " * @serialField name String 名前\n"
                        + " * @since 1.0 日本語\n"
                        + " * @author someone\n"
                        + " */";
        List<JavadocParser.Unit> units = JavadocParser.units(src);
        String[][] expected = {
            {"", "本文。"},
            {"@param <T> ", "型"},
            {"@param x ", "引数"},
            {"@return ", "戻り値"},
            {"@throws java.io.IOException ", "失敗した場合"},
            {"@see #f(int, String) ", "ラベル"},
            {"@see ", "\"文字列\""},
            {"@serialField name String ", "名前"},
            {"@since ", "1.0 日本語"},
        };
        assertEquals(expected.length, units.size(), "number of units");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i][0], units.get(i).prefix, "prefix of unit " + i);
            assertEquals(expected[i][1], units.get(i).text, "text of unit " + i);
        }
    }

    @Test
    void atSignInsidePreOrInlineTagIsNotABlockTag() {
        String src =
                "/**\n"
                        + " * 例:\n"
                        + " * <pre>\n"
                        + " * @Override\n"
                        + " * void f() {}\n"
                        + " * </pre>\n"
                        + " * 続き {@code\n"
                        + " * @foo bar} の後。\n"
                        + " */";
        JavadocParser.Unit unit = onlyUnit(src);
        assertTrue(unit.text.contains("@Override"), unit.text);
        assertTrue(unit.text.endsWith("@foo bar} の後。"), unit.text);
    }

    @Test
    void descriptionOnTheNextLineIsDedented() {
        String src = "/**\n" + " * @throws X\n" + " *     次の行の\n" + " *     説明\n" + " */";
        JavadocParser.Unit unit = onlyUnit(src);
        assertEquals("@throws X ", unit.prefix, "prefix");
        assertEquals("次の行の\n説明", unit.text, "text");
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Test
    void singleLineCommentStaysSingleLine() throws Exception {
        Map.Entry<String, Report> r =
                TestSupport.translate(dir, "  /** 値。 */\n  int x;\n", "値。", "The value.");
        assertEquals("  /** The value. */\n  int x;\n", r.getKey(), "output");
    }

    @Test
    void untranslatedPartsAreKeptVerbatim() throws Exception {
        String src =
                "class A {\r\n"
                        + "\t/**\r\n"
                        + "\t * 本文。\r\n"
                        + "\t * <pre>\r\n"
                        + "\t *\tcode  \r\n"
                        + "\t * </pre>\r\n"
                        + "\t *\r\n"
                        + "\t * @param x 未翻訳\r\n"
                        + "\t * @see Other\r\n"
                        + "\t */\r\n"
                        + "\tvoid f(int x) {}\r\n"
                        + "}\r\n";
        Map.Entry<String, Report> r =
                TestSupport.translate(
                        dir, src, "本文。\n<pre>\n\tcode\n</pre>", "Body.\n<pre>\n\tcode\n</pre>");
        String expected =
                "class A {\r\n"
                        + "\t/**\r\n"
                        + "\t * Body.\r\n"
                        + "\t * <pre>\r\n"
                        + "\t * \tcode\r\n"
                        + "\t * </pre>\r\n"
                        + "\t *\r\n"
                        + "\t * @param x 未翻訳\r\n"
                        + "\t * @see Other\r\n"
                        + "\t */\r\n"
                        + "\tvoid f(int x) {}\r\n"
                        + "}\r\n";
        assertEquals(expected, r.getKey(), "output");
        assertEquals(1, r.getValue().count(Problem.Kind.UNTRANSLATED), "untranslated units");
    }

    @Test
    void translatesBlockTagDescriptions() throws Exception {
        String src =
                "/**\n"
                        + " * 本文。\n"
                        + " *\n"
                        + " * @param x\n"
                        + " *     引数\n"
                        + " * @return 戻り値\n"
                        + " */";
        Map.Entry<String, Report> r =
                TestSupport.translate(
                        dir, src, "本文。", "Body.", "引数", "the argument", "戻り値", "the result");
        assertEquals(
                "/**\n * Body.\n *\n * @param x the argument\n * @return the result\n */",
                r.getKey(),
                "output");
    }

    // ------------------------------------------------------------------
    // Checking translations
    // ------------------------------------------------------------------

    @Test
    void checkTranslationRejectsBrokenStructure() {
        JavadocParser.Unit main = onlyUnit("/** 本文。 */");
        JavadocParser.Unit param = onlyUnit("/**\n * @param x 引数\n */");
        assertNotNull(JavadocParser.checkTranslation(main, "a */ b"), "*/");
        assertNotNull(JavadocParser.checkTranslation(main, "@foo bar"), "@ on the first line");
        assertNotNull(JavadocParser.checkTranslation(param, "a\n@foo bar"), "@ on line 2");
        assertNotNull(JavadocParser.checkTranslation(main, "a {@code b"), "unclosed inline tag");
        assertNotNull(JavadocParser.checkTranslation(main, "a <pre> b"), "unclosed pre");
        assertNull(JavadocParser.checkTranslation(param, "@foo is fine here"), "@ after tag");
        assertNull(
                JavadocParser.checkTranslation(main, "Example:\n<pre>\n@Override\n</pre>"),
                "@ inside pre");
        assertNull(
                JavadocParser.checkTranslation(main, "a {@code\n@foo} b"), "@ inside inline tag");
    }

    @Test
    void containsJapanese() {
        for (String s : new String[] {"あ", "ア", "漢", "ー", "。", "Ａ", "ｱ", "𠮷", "﨑", "㈱"}) {
            assertTrue(Text.containsJapanese(s), s);
        }
        for (String s : new String[] {"abc", "é", "—", "α", "①", ""}) {
            assertTrue(!Text.containsJapanese(s), s);
        }
    }
}
