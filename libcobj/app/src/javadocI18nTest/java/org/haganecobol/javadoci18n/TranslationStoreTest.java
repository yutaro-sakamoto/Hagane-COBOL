package org.haganecobol.javadoci18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranslationStoreTest {

    private static final String[] VALUES = {
        "plain",
        "two\nlines",
        "  indented first line\nshallow",
        "\n  blank then indented\nx",
        "\ttab first",
        "trailing spaces   \nnext",
        "# not a comment",
        "- not a list",
        "key: value",
        "| not a block",
        "\"quoted\"",
        "yes",
        "123",
        "carriage\rreturn",
        "nel\u0085line",
        "ls ps ",
        "bell\u0007",
        "bom﻿",
        "c1\u0080control",
        "non-BMP 𠮷",
        "line\n\nwith blank line",
        "back\\slash",
    };

    @Test
    void renderedValuesReadBack() throws IOException {
        List<TranslationStore.Entry> entries = new ArrayList<>();
        for (String v : VALUES) {
            entries.add(new TranslationStore.Entry(v, v));
        }
        List<TranslationStore.Entry> back =
                TranslationStore.parse(TranslationStore.render(entries), "test");
        assertEquals(entries.size(), back.size(), "number of entries");
        for (int i = 0; i < VALUES.length; i++) {
            assertEquals(VALUES[i], back.get(i).ja, "ja of " + i);
            assertEquals(VALUES[i], back.get(i).en, "en of " + i);
        }
    }

    @Test
    void usesLiteralBlocksForOrdinaryText() {
        assertEquals("|-\n    a\n    b\n", TranslationStore.scalar("a\nb"), "plain");
        assertEquals("|2-\n      a\n    b\n", TranslationStore.scalar("  a\nb"), "indented");
        assertEquals("\"\"\n", TranslationStore.scalar(""), "empty");
    }

    @Test
    void trailingBlankLinesOfTranslationsAreRemoved() throws IOException {
        List<TranslationStore.Entry> e =
                TranslationStore.parse("- ja: あ\n  en: |\n    text\n\n", "test");
        assertEquals("text", e.get(0).en, "en");
    }

    @Test
    void rejectsMalformedFiles() {
        assertThrows(
                IOException.class,
                () -> TranslationStore.parse("- ja: あ\n  ja: い\n  en: x\n", "dup"),
                "duplicate key");
        IOException e =
                assertThrows(
                        IOException.class,
                        () -> TranslationStore.parse("- ja: あ\n  en: yes\n", "bool"),
                        "boolean");
        assertTrue(e.getMessage().contains("quote"), e.getMessage());
        assertThrows(
                IOException.class,
                () -> TranslationStore.parse("- ja: あ\n  em: x\n", "typo"),
                "unknown key");
        assertThrows(IOException.class, () -> TranslationStore.parse("ja: あ\n", "map"), "map");
    }
}
