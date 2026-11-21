package org.haganecobol.javadoci18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Javadocコメントを探し、翻訳の単位に分解し、訳文を埋め込んで出力し直す。
 *
 * <p>翻訳の単位は、コメントの本文と、ブロックタグ({@code @param}、{@code @return}、{@code @see}など)の説明文である。
 * 日本語を含まない単位は翻訳の対象にしない。
 */
final class JavadocParser {

    /** ブロックタグの開始行。 */
    private static final Pattern BLOCK_TAG_LINE = Pattern.compile("^\\s*@[a-zA-Z]+(\\s|$)");

    /** ブロックタグの名前。 */
    private static final Pattern TAG_NAME = Pattern.compile("^\\s*@([a-zA-Z]+)");

    private JavadocParser() {}

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    /** コメントの1行。先頭の空白と{@code *}を取り除いたもの。 */
    static final class Line {
        /** {@code *}と本文の間にあった区切り。空白1つか空文字列。 */
        final String gap;

        /**
         * {@code *}と区切りの後ろの本文。末尾の空白は取り除く。{@code *}の無い行では、先頭の空白を含む行全体(Javadocは先頭の空白を本文として扱う)。
         */
        final String content;

        /** 行が{@code *}で始まっていたかどうか。 */
        final boolean starred;

        Line(String gap, String content, boolean starred) {
            this.gap = gap;
            this.content = content;
            this.starred = starred;
        }
    }

    /** ソース中のJavadocコメント。 */
    static final class Comment {
        /** {@code /**}の位置。 */
        final int start;

        /** {@code *}{@code /}の直後の位置。 */
        final int end;

        /** {@code /**}がある行の番号(1始まり)。 */
        final int lineNumber;

        /** コメントの各行。前後の空行は含まない。 */
        final List<Line> body;

        /** 元のコメントが1行で書かれていたかどうか。 */
        final boolean singleLine;

        Comment(int start, int end, int lineNumber, List<Line> body, boolean singleLine) {
            this.start = start;
            this.end = end;
            this.lineNumber = lineNumber;
            this.body = body;
            this.singleLine = singleLine;
        }
    }

    /** 翻訳の単位。本文、またはブロックタグの説明文。 */
    static final class Unit {
        /** 説明文の前に出力する部分。本文では空、ブロックタグでは{@code "@param name "}など。 */
        final String prefix;

        /** 日本語の原文。 */
        final String text;

        /** 本文かどうか。本文では訳文の1行目もブロックタグとして解釈されうる。 */
        final boolean mainDescription;

        /** 元のコメントの行。訳文が無い場合はこれをそのまま出力する。 */
        final List<Line> original;

        Unit(String prefix, String text, boolean mainDescription, List<Line> original) {
            this.prefix = prefix;
            this.text = text;
            this.mainDescription = mainDescription;
            this.original = original;
        }
    }

    /** コメントを構成する部分。翻訳の単位か、そのまま出力する行の並び。 */
    static final class Part {
        final Unit unit;
        final List<Line> verbatim;

        Part(Unit unit, List<Line> verbatim) {
            this.unit = unit;
            this.verbatim = verbatim;
        }
    }

    /** 翻訳の単位に分解したJavadocコメント。 */
    static final class ParsedComment {
        final List<Part> parts = new ArrayList<>();
        final List<Unit> units = new ArrayList<>();

        /**
         * 訳文を埋め込んだコメントを出力する。訳文の無い部分は元の行をそのまま出力する。
         *
         * @param comment 元のコメント
         * @param indent コメントの開始位置の字下げ
         * @param newline ソースファイルの改行文字
         * @param replacements 単位ごとの訳文。含まれない単位は元のまま出力する
         * @return {@code /**}から{@code *}{@code /}までのコメント
         */
        String render(
                Comment comment, String indent, String newline, Map<Unit, String> replacements) {
            // 各行は、字下げを含めた完全な行として組み立てる。
            List<String> lines = new ArrayList<>();
            for (Part part : parts) {
                if (part.unit == null) {
                    addVerbatim(lines, part.verbatim, indent);
                    continue;
                }
                String en = replacements.get(part.unit);
                if (en == null) {
                    addVerbatim(lines, part.unit.original, indent);
                    continue;
                }
                String[] enLines = Text.lines(en);
                for (int k = 0; k < enLines.length; k++) {
                    String line = k == 0 ? part.unit.prefix + enLines[k] : enLines[k];
                    line = Text.rtrim(line);
                    lines.add(indent + (line.isEmpty() ? " *" : " * " + line));
                }
            }
            String singlePrefix = indent + " * ";
            if (comment.singleLine && lines.size() == 1 && lines.get(0).startsWith(singlePrefix)) {
                return "/** " + lines.get(0).substring(singlePrefix.length()) + " */";
            }
            StringBuilder sb = new StringBuilder("/**").append(newline);
            for (String line : lines) {
                sb.append(line).append(newline);
            }
            sb.append(indent).append(" */");
            return sb.toString();
        }

        private static void addVerbatim(List<String> out, List<Line> lines, String indent) {
            for (Line line : lines) {
                if (!line.starred) {
                    out.add(line.content);
                } else {
                    out.add(
                            indent
                                    + (line.content.isEmpty()
                                            ? " *"
                                            : " *" + line.gap + line.content));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Finding Javadoc comments
    // ------------------------------------------------------------------

    /**
     * ソースコードからJavadocコメントを探す。文字列リテラル、テキストブロック、文字リテラル、通常のコメントの中は読み飛ばす。
     *
     * @param text ソースファイルの内容
     * @return 見つかったJavadocコメント
     */
    static List<Comment> findComments(String text) {
        List<Comment> result = new ArrayList<>();
        int n = text.length();
        int i = 0;
        int line = 1;
        int counted = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"' && text.startsWith("\"\"\"", i)) {
                i = skipTextBlock(text, i);
            } else if (c == '"' || c == '\'') {
                i = skipLiteral(text, i, c);
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                int eol = text.indexOf('\n', i);
                i = eol < 0 ? n : eol;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int close = text.indexOf("*/", i + 2);
                int end = close < 0 ? n : close + 2;
                boolean javadoc = close > i + 2 && text.charAt(i + 2) == '*';
                if (javadoc) {
                    for (; counted < i; counted++) {
                        if (text.charAt(counted) == '\n') {
                            line++;
                        }
                    }
                    String inner = text.substring(i + 3, close);
                    boolean singleLine = inner.indexOf('\n') < 0;
                    result.add(new Comment(i, end, line, stripComment(inner), singleLine));
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

    private static int skipTextBlock(String text, int start) {
        int i = start + 3;
        while (i < text.length()) {
            if (text.charAt(i) == '\\') {
                i += 2;
            } else if (text.startsWith("\"\"\"", i)) {
                return i + 3;
            } else {
                i++;
            }
        }
        return i;
    }

    /** コメントの各行から先頭の空白、{@code *}と、その後の空白1つを取り除き、前後の空行も取り除く。 */
    private static List<Line> stripComment(String inner) {
        String[] raw = inner.split("\r?\n", -1);
        List<Line> lines = new ArrayList<>();
        for (int k = 0; k < raw.length; k++) {
            String line = raw[k];
            int j = 0;
            if (k > 0) {
                while (j < line.length() && Character.isWhitespace(line.charAt(j))) {
                    j++;
                }
            }
            boolean star = false;
            while (j < line.length() && line.charAt(j) == '*') {
                j++;
                star = true;
            }
            if (k > 0 && !star) {
                // *の無い行では、Javadocは先頭の空白も本文として扱う。<pre>の字下げを保つため、行全体を残す。
                lines.add(new Line("", Text.rtrim(line), false));
                continue;
            }
            String gap = " ";
            if (j < line.length() && line.charAt(j) == ' ') {
                j++;
            } else if (star || k == 0) {
                gap = "";
            }
            lines.add(new Line(gap, Text.rtrim(line.substring(j)), true));
        }
        int from = 0;
        int to = lines.size();
        while (from < to && lines.get(from).content.isEmpty()) {
            from++;
        }
        while (to > from && lines.get(to - 1).content.isEmpty()) {
            to--;
        }
        return new ArrayList<>(lines.subList(from, to));
    }

    // ------------------------------------------------------------------
    // Block tags
    // ------------------------------------------------------------------

    /**
     * 行ごとに、ブロックタグが始まる行かどうかを判定する。{@code <pre>}の中と、複数行にまたがるインラインタグ({@code {@code ...}}など)の中では、
     * {@code @}で始まる行もブロックタグとみなさない。
     */
    static final class TagScanner {
        private boolean inPre;
        private int depth;

        /**
         * 次の行を読む。
         *
         * @param line 行の本文
         * @return この行でブロックタグが始まる場合はtrue
         */
        boolean next(String line) {
            boolean blockTag = !inPre && depth == 0 && BLOCK_TAG_LINE.matcher(line).find();
            String lower = line.toLowerCase(Locale.ROOT);
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '<' && depth == 0) {
                    if (startsTag(lower, i, "<pre")) {
                        inPre = true;
                    } else if (lower.startsWith("</pre>", i)) {
                        inPre = false;
                    }
                } else if (c == '{' && i + 1 < line.length() && line.charAt(i + 1) == '@') {
                    depth++;
                } else if (depth > 0 && c == '{') {
                    depth++;
                } else if (depth > 0 && c == '}') {
                    depth--;
                }
            }
            return blockTag;
        }

        /**
         * 読んだ行の終わりで、{@code <pre>}やインラインタグが閉じているかどうか。
         *
         * @return 閉じていればtrue
         */
        boolean closed() {
            return !inPre && depth == 0;
        }

        private static boolean startsTag(String lower, int i, String tag) {
            if (!lower.startsWith(tag, i)) {
                return false;
            }
            int after = i + tag.length();
            return after >= lower.length()
                    || lower.charAt(after) == '>'
                    || Character.isWhitespace(lower.charAt(after));
        }
    }

    /**
     * ブロックタグの説明文の前にある、名前や参照先の語の数を返す。
     *
     * @param tag タグの名前({@code @}を除く)
     * @param rest タグの名前より後ろの部分
     * @return 説明文の前にある語の数
     */
    private static int wordsBeforeDescription(String tag, String rest) {
        switch (tag) {
            case "param":
            case "throws":
            case "exception":
            case "uses":
            case "provides":
                return 1;
            case "serialField":
                return 2;
            case "see":
                // @see "文字列" と @see <a href=...>ラベル</a> は全体が説明文で、それ以外は参照先の後ろがラベル。
                String r = Text.ltrim(rest);
                return r.startsWith("\"") || r.startsWith("<") ? 0 : 1;
            default:
                return 0;
        }
    }

    /**
     * 語を1つ読み飛ばした位置を返す。{@code #method(int, String)}のような括弧の中の空白は語の区切りとみなさない。
     *
     * @param s 対象の文字列
     * @param from 読み始める位置
     * @return 語の直後の位置。語が無い場合は{@code from}
     */
    private static int skipWord(String s, int from) {
        int i = from;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i == s.length()) {
            return from;
        }
        int parens = 0;
        while (i < s.length() && (parens > 0 || !Character.isWhitespace(s.charAt(i)))) {
            char c = s.charAt(i);
            if (c == '(') {
                parens++;
            } else if (c == ')' && parens > 0) {
                parens--;
            }
            i++;
        }
        return i;
    }

    // ------------------------------------------------------------------
    // Parsing a comment into translation units
    // ------------------------------------------------------------------

    /**
     * Javadocコメントを翻訳の単位に分解する。日本語を含まない部分はそのまま出力する行として扱う。
     *
     * @param body コメントの各行
     * @return 分解したコメント
     */
    static ParsedComment parse(List<Line> body) {
        List<List<Line>> blocks = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        blocks.add(current);
        TagScanner scanner = new TagScanner();
        for (Line line : body) {
            if (scanner.next(line.content)) {
                current = new ArrayList<>();
                blocks.add(current);
            }
            current.add(line);
        }

        ParsedComment parsed = new ParsedComment();
        for (int b = 0; b < blocks.size(); b++) {
            List<Line> block = blocks.get(b);
            if (block.isEmpty()) {
                continue;
            }
            // ブロックの末尾の空行は、翻訳の単位に含めずそのまま出力する。
            int end = block.size();
            while (end > 0 && block.get(end - 1).content.isEmpty()) {
                end--;
            }
            List<Line> lines = new ArrayList<>(block.subList(0, end));
            List<Line> trailing = new ArrayList<>(block.subList(end, block.size()));

            List<String> textLines = new ArrayList<>();
            for (Line line : lines) {
                textLines.add(line.content);
            }
            String prefix = "";
            if (b > 0) {
                String first = textLines.get(0);
                Matcher tag = TAG_NAME.matcher(first);
                if (!tag.find()) {
                    throw new IllegalStateException("not a block tag: " + first);
                }
                int pos = tag.end();
                int words = wordsBeforeDescription(tag.group(1), first.substring(pos));
                for (int w = 0; w < words; w++) {
                    pos = skipWord(first, pos);
                }
                prefix = Text.rtrim(first.substring(0, pos)) + " ";
                textLines.set(0, Text.ltrim(first.substring(pos)));
            }
            String text = Text.join(dedent(textLines));
            if (Text.containsJapanese(text)) {
                Unit unit = new Unit(prefix, text, b == 0, lines);
                parsed.units.add(unit);
                parsed.parts.add(new Part(unit, null));
            } else {
                parsed.parts.add(new Part(null, lines));
            }
            if (!trailing.isEmpty()) {
                parsed.parts.add(new Part(null, trailing));
            }
        }
        return parsed;
    }

    /**
     * 説明文の先頭の空行と、全ての行に共通する字下げを取り除く。説明文をタグの次の行から字下げして書いた場合に、原文を読みやすくする。
     *
     * @param lines 説明文の各行
     * @return 先頭の空行と共通の字下げを取り除いた各行
     */
    private static List<String> dedent(List<String> lines) {
        int from = 0;
        while (from < lines.size() && lines.get(from).isEmpty()) {
            from++;
        }
        List<String> rest = lines.subList(from, lines.size());
        int common = Integer.MAX_VALUE;
        for (String line : rest) {
            if (!line.isEmpty()) {
                common = Math.min(common, line.length() - Text.ltrim(line).length());
            }
        }
        List<String> result = new ArrayList<>();
        for (String line : rest) {
            result.add(line.isEmpty() ? line : line.substring(Math.min(common, line.length())));
        }
        return result;
    }

    /**
     * 訳文を埋め込んでもコメントの構造が変わらないかを検査する。
     *
     * @param unit 翻訳の単位
     * @param en 訳文
     * @return 問題があればその説明、無ければnull
     */
    static String checkTranslation(Unit unit, String en) {
        if (en.contains("*/")) {
            return "the translation contains \"*/\"";
        }
        String[] enLines = Text.lines(en);
        TagScanner scanner = new TagScanner();
        for (int i = 0; i < enLines.length; i++) {
            boolean blockTag = scanner.next(enLines[i]);
            if (blockTag && (i > 0 || unit.mainDescription)) {
                return "line "
                        + (i + 1)
                        + " of the translation starts with @ and would start a block tag";
            }
        }
        TagScanner original = new TagScanner();
        for (String line : Text.lines(unit.text)) {
            original.next(line);
        }
        if (scanner.closed() != original.closed()) {
            return "the translation does not close <pre> or an inline tag such as {@code ...}"
                    + " in the same way as the original";
        }
        return null;
    }

    /**
     * ソースの{@code offset}の位置の行の字下げを返す。
     *
     * @param text ソースファイルの内容
     * @param offset 位置
     * @return 行頭から{@code offset}までの空白とタブ
     */
    static String indentOf(String text, int offset) {
        int bol = text.lastIndexOf('\n', offset - 1) + 1;
        int i = bol;
        while (i < offset && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return text.substring(bol, i);
    }

    /**
     * ソースファイルの改行文字を返す。
     *
     * @param text ソースファイルの内容
     * @return CRLFを含めば{@code "\r\n"}、そうでなければ{@code "\n"}
     */
    static String newlineOf(String text) {
        return text.contains("\r\n") ? "\r\n" : "\n";
    }

    /**
     * ソースファイル中の全ての翻訳の単位を、出現順に返す。
     *
     * @param text ソースファイルの内容
     * @return 翻訳の単位
     */
    static List<Unit> units(String text) {
        List<Unit> units = new ArrayList<>();
        for (Comment comment : findComments(text)) {
            units.addAll(parse(comment.body).units);
        }
        return Collections.unmodifiableList(units);
    }
}
