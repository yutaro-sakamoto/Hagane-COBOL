package org.haganecobol.javadoci18n;

import java.util.List;
import java.util.regex.Pattern;

/** 文字列を扱う小さな関数の集まり。 */
final class Text {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Text() {}

    /**
     * 比較用に原文を正規化する。行の折り返し位置の違いを無視するため、連続する空白を1つの空白にまとめる。
     *
     * @param text 原文
     * @return 正規化した原文
     */
    static String normalize(String text) {
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }

    /**
     * 日本語の文字(ひらがな、カタカナ、漢字、和文の句読点、全角・半角形の文字)を含むかどうかを判定する。
     *
     * @param text 判定する文字列
     * @return 日本語の文字を含む場合はtrue
     */
    static boolean containsJapanese(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isJapanese(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean isJapanese(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        if (script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA) {
            return true;
        }
        // 長音記号「ー」や中黒「・」の文字体系はCOMMONなので、ブロックでも判定する。
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
                || block == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY;
    }

    /**
     * 文字列を改行({@code \n})で分割する。末尾の空の要素も残す。
     *
     * @param text 分割する文字列
     * @return 各行
     */
    static String[] lines(String text) {
        return text.split("\n", -1);
    }

    static String join(List<String> lines) {
        return String.join("\n", lines);
    }

    static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    static String ltrim(String s) {
        int start = 0;
        while (start < s.length() && Character.isWhitespace(s.charAt(start))) {
            start++;
        }
        return s.substring(start);
    }

    /**
     * 末尾の空行(空白だけの行を含む)を取り除く。
     *
     * @param text 対象の文字列
     * @return 末尾の空行を取り除いた文字列
     */
    static String stripTrailingBlankLines(String text) {
        String[] lines = lines(text);
        int end = lines.length;
        while (end > 0 && lines[end - 1].trim().isEmpty()) {
            end--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /**
     * メッセージ用に、文字列を1行に縮めて長すぎる部分を省略する。
     *
     * @param text 対象の文字列
     * @return 1行に縮めた文字列
     */
    static String oneLine(String text) {
        String s = normalize(text);
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }
}
