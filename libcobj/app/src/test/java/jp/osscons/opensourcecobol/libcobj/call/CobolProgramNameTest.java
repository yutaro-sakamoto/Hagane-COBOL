/*
 * Copyright (C) 2021-2022 TOKYO SYSTEM HOUSE Co., Ltd.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 3.0,
 * or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; see the file COPYING.LIB.  If
 * not, write to the Free Software Foundation, 51 Franklin Street, Fifth Floor
 * Boston, MA 02110-1301 USA
 */
package jp.osscons.opensourcecobol.libcobj.call;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.osscons.opensourcecobol.libcobj.data.AbstractCobolField;
import org.junit.jupiter.api.Test;

/** コンパイラのcb_encode_program_id()(cobj/typeck.c)と同じ変換結果になることを確認する。 */
class CobolProgramNameTest {
    private static final Charset SJIS = Charset.forName("SJIS");
    private static final int KEEP = CobolProgramName.CASE_KEEP;
    private static final int LOWER = CobolProgramName.CASE_LOWER;
    private static final int UPPER = CobolProgramName.CASE_UPPER;

    @Test
    void testAsciiName() {
        assertEquals("SUB", CobolProgramName.encodeSjis("SUB".getBytes(SJIS), KEEP));
        assertEquals("SUB", CobolProgramName.encodeUtf8("SUB".getBytes(SJIS), KEEP));
        assertEquals(List.of("SUB_A"), CobolProgramName.candidates("SUB_A", KEEP));
    }

    @Test
    void testHyphen() {
        assertEquals(List.of("SUB__A"), CobolProgramName.candidates("SUB-A", KEEP));
    }

    @Test
    void testInitialDigit() {
        assertEquals(List.of("_31SUB"), CobolProgramName.candidates("1SUB", KEEP));
        assertEquals("_39_241", CobolProgramName.encodeSjis("9$1".getBytes(SJIS), KEEP));
    }

    @Test
    void testJapaneseShiftJis() {
        byte[] name = "テスト".getBytes(SJIS);
        assertEquals("_83e_83X_83g", CobolProgramName.encodeSjis(name, KEEP));
        assertTrue(CobolProgramName.candidates(name, KEEP).contains("_83e_83X_83g"));
        assertTrue(CobolProgramName.candidates("テスト", KEEP).contains("_83e_83X_83g"));
    }

    @Test
    void testJapaneseUtf8() {
        byte[] name = "テスト".getBytes(StandardCharsets.UTF_8);
        assertEquals("_E38386_E382B9_E38388", CobolProgramName.encodeUtf8(name, KEEP));
        assertTrue(CobolProgramName.candidates(name, KEEP).contains("_E38386_E382B9_E38388"));
        assertTrue(CobolProgramName.candidates("テスト", KEEP).contains("_E38386_E382B9_E38388"));
    }

    @Test
    void testFieldBytesEncodedWithRuntimeCharset() {
        /* UTF-8版のcobjでも、VALUE句の値はランタイムの文字セット(既定はShift_JIS)で格納される */
        byte[] name = "テスト".getBytes(AbstractCobolField.charSetSJIS);
        List<String> list = CobolProgramName.candidates(name, KEEP);
        assertTrue(list.contains("_E38386_E382B9_E38388"));
        assertTrue(list.contains("_83e_83X_83g"));
    }

    @Test
    void testTruncatedUtf8Sequence() {
        byte[] name = {'A', (byte) 0xE3, (byte) 0x83, 'B'};
        assertEquals("A_E3_83B", CobolProgramName.encodeUtf8(name, KEEP));
    }

    @Test
    void testEncodedNameIsUnchanged() {
        assertEquals(List.of("_83e_83X_83g"), CobolProgramName.candidates("_83e_83X_83g", KEEP));
    }

    @Test
    void testLoadCase() {
        assertEquals(List.of("sub__a"), CobolProgramName.candidates("SUB-A", LOWER));
        assertEquals(List.of("_31SUB"), CobolProgramName.candidates("1sub", UPPER));
        /* Shift_JISの2バイト目と16進表記は大文字・小文字を変換しない */
        byte[] sjis = "テストab".getBytes(SJIS);
        assertEquals("_83e_83X_83gAB", CobolProgramName.encodeSjis(sjis, UPPER));
        byte[] utf8 = "テAB".getBytes(StandardCharsets.UTF_8);
        assertEquals("_E38386ab", CobolProgramName.encodeUtf8(utf8, LOWER));
    }
}
