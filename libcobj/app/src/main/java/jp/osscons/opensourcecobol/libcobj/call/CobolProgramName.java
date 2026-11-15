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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import jp.osscons.opensourcecobol.libcobj.data.AbstractCobolField;

/**
 * COBOLのプログラム名(PROGRAM-ID)を、コンパイラが生成するJavaクラス名へ変換するクラス。<br>
 * 変換規則はコンパイラの{@code cb_encode_program_id()}(cobj/typeck.c)と一致させる必要がある。
 *
 * <ul>
 *   <li>先頭が数字の場合、その数字を{@code _XX}(16進大文字)に変換する
 *   <li>英数字と{@code _}はそのまま出力する
 *   <li>{@code -}は{@code __}に変換する
 *   <li>それ以外のバイトは{@code _XX}に変換する。ただしUTF-8ビルドのコンパイラは、
 *       正しいUTF-8のマルチバイト文字を1文字まとめて{@code _XXXXXX}に変換する
 * </ul>
 *
 * コンパイラのビルド時の文字コード(Shift_JIS / UTF-8)は実行時には分からないため、両方の規則による変換結果を候補として返す。
 */
final class CobolProgramName {
    /** 名前の大文字・小文字を変換しない */
    static final int CASE_KEEP = 0;

    /** 名前を小文字に変換する(環境変数COB_LOAD_CASE=LOWER) */
    static final int CASE_LOWER = 1;

    /** 名前を大文字に変換する(環境変数COB_LOAD_CASE=UPPER) */
    static final int CASE_UPPER = 2;

    /** Shift_JISビルドのコンパイラがjavacに渡す文字コード */
    private static final Charset SJIS = Charset.forName("SJIS");

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private CobolProgramName() {}

    /**
     * Javaの文字列で与えられたプログラム名から、クラス名の候補を求める。<br>
     * 文字列はCALL文のリテラルなど、javacがCOBOLソースと同じ文字コードで読み込んだものを想定する。
     *
     * @param name プログラム名
     * @param caseMode {@link #CASE_KEEP}、{@link #CASE_LOWER}、{@link #CASE_UPPER}のいずれか
     * @return クラス名の候補(重複なし、1件以上)。優先して検索すべきものが先頭にある
     */
    static List<String> candidates(String name, int caseMode) {
        String utf8 = encodeUtf8(name.getBytes(StandardCharsets.UTF_8), caseMode);
        String sjis = encodeSjis(name.getBytes(SJIS), caseMode);
        return order(utf8, sjis);
    }

    /**
     * COBOLのデータ項目のバイト列で与えられたプログラム名から、クラス名の候補を求める。<br>
     * データ項目のVALUE句などは、ランタイムの文字セット({@link AbstractCobolField#charSetSJIS})で符号化されている。
     * そのため、まずその文字セットで文字列に戻してから{@link #candidates(String, int)}と同じ規則で変換する。
     * 文字セットで正しく復号できないバイト列にも対応するため、バイト列をそのまま変換した結果も後ろに加える。
     *
     * @param name プログラム名のバイト列
     * @param caseMode {@link #CASE_KEEP}、{@link #CASE_LOWER}、{@link #CASE_UPPER}のいずれか
     * @return クラス名の候補(重複なし、1件以上)。優先して検索すべきものが先頭にある
     */
    static List<String> candidates(byte[] name, int caseMode) {
        Set<String> set = new LinkedHashSet<>();
        String decoded = decode(name);
        if (decoded != null) {
            set.addAll(candidates(decoded, caseMode));
        }
        set.addAll(order(encodeUtf8(name, caseMode), encodeSjis(name, caseMode)));
        return new ArrayList<>(set);
    }

    /**
     * バイト列をランタイムの文字セットで復号する。
     *
     * @param name プログラム名のバイト列
     * @return 復号した文字列。その文字セットとして不正なバイト列の場合はnull
     */
    private static String decode(byte[] name) {
        try {
            return AbstractCobolField.charSetSJIS
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(name))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * 2つの候補を、ランタイムの文字セットに合うものが先頭になるように並べる。
     *
     * @param utf8 UTF-8ビルドの規則による変換結果
     * @param sjis Shift_JISビルドの規則による変換結果
     * @return 候補のリスト
     */
    private static List<String> order(String utf8, String sjis) {
        List<String> list = new ArrayList<>(2);
        if (utf8.equals(sjis)) {
            list.add(utf8);
        } else if (StandardCharsets.UTF_8.equals(AbstractCobolField.charSetSJIS)) {
            list.add(utf8);
            list.add(sjis);
        } else {
            list.add(sjis);
            list.add(utf8);
        }
        return list;
    }

    /**
     * Shift_JISビルドのコンパイラと同じ規則でプログラム名を変換する。
     *
     * @param s プログラム名のバイト列
     * @param caseMode 大文字・小文字の変換方法
     * @return 変換後のクラス名
     */
    static String encodeSjis(byte[] s, int caseMode) {
        StringBuilder sb = new StringBuilder(s.length * 3);
        int i = encodeInitialDigit(s, sb);
        while (i < s.length) {
            int c = s[i] & 0xFF;
            if (isSjisLead(c) && i + 1 < s.length) {
                /* 2バイト文字の2バイト目が英字でも、大文字・小文字の変換をしない */
                appendHex(sb, c);
                appendByte(sb, s[i + 1] & 0xFF, CASE_KEEP);
                i += 2;
            } else {
                appendByte(sb, c, caseMode);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * UTF-8ビルドのコンパイラと同じ規則でプログラム名を変換する。
     *
     * @param s プログラム名のバイト列
     * @param caseMode 大文字・小文字の変換方法
     * @return 変換後のクラス名
     */
    static String encodeUtf8(byte[] s, int caseMode) {
        StringBuilder sb = new StringBuilder(s.length * 3);
        int i = encodeInitialDigit(s, sb);
        while (i < s.length) {
            int c = s[i] & 0xFF;
            int n = utf8SequenceLength(c);
            if (n > 1) {
                int j = 1;
                while (j < n && i + j < s.length && (s[i + j] & 0xC0) == 0x80) {
                    j++;
                }
                /* 途中で途切れた不正な列は、先頭バイトだけを変換する */
                if (j < n) {
                    n = 1;
                }
            }
            if (n > 1) {
                sb.append('_');
                for (int k = 0; k < n; k++) {
                    appendHexDigits(sb, s[i + k] & 0xFF);
                }
                i += n;
            } else {
                appendByte(sb, c, caseMode);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * 先頭が数字なら{@code _XX}に変換して出力する。
     *
     * @param s プログラム名のバイト列
     * @param sb 出力先
     * @return 次に処理するバイトの位置
     */
    private static int encodeInitialDigit(byte[] s, StringBuilder sb) {
        if (s.length > 0 && s[0] >= '0' && s[0] <= '9') {
            appendHex(sb, s[0]);
            return 1;
        }
        return 0;
    }

    /**
     * 1バイトを変換して出力する。
     *
     * @param sb 出力先
     * @param c 変換するバイト(0〜255)
     * @param caseMode 大文字・小文字の変換方法
     */
    private static void appendByte(StringBuilder sb, int c, int caseMode) {
        if (c >= 'a' && c <= 'z') {
            sb.append((char) (caseMode == CASE_UPPER ? c - 'a' + 'A' : c));
        } else if (c >= 'A' && c <= 'Z') {
            sb.append((char) (caseMode == CASE_LOWER ? c - 'A' + 'a' : c));
        } else if ((c >= '0' && c <= '9') || c == '_') {
            sb.append((char) c);
        } else if (c == '-') {
            sb.append("__");
        } else {
            appendHex(sb, c);
        }
    }

    private static void appendHex(StringBuilder sb, int c) {
        sb.append('_');
        appendHexDigits(sb, c);
    }

    private static void appendHexDigits(StringBuilder sb, int c) {
        sb.append(HEX[(c >> 4) & 0x0F]).append(HEX[c & 0x0F]);
    }

    /**
     * Shift_JISの2バイト文字の1バイト目かどうかを判定する。
     *
     * @param c 判定するバイト(0〜255)
     * @return 1バイト目であればtrue
     */
    private static boolean isSjisLead(int c) {
        return (c >= 0x81 && c <= 0x9F) || (c >= 0xE0 && c <= 0xFC);
    }

    /**
     * UTF-8の先頭バイトから、その文字のバイト数を求める(cobj/cobj.hの{@code COB_U8BYTE_1}と同じ)。
     *
     * @param c 先頭バイト(0〜255)
     * @return 文字のバイト数。先頭バイトとして不正な場合は0
     */
    private static int utf8SequenceLength(int c) {
        if ((c >> 7) == 0x00) {
            return 1;
        } else if ((c >> 5) == 0x06) {
            return 2;
        } else if ((c >> 4) == 0x0E) {
            return 3;
        } else if ((c >> 3) == 0x1E) {
            return 4;
        } else if ((c >> 2) == 0x3E) {
            return 5;
        } else if ((c >> 1) == 0x7E) {
            return 6;
        }
        return 0;
    }
}
