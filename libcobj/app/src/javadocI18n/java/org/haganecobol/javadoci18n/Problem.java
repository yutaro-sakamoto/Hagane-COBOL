package org.haganecobol.javadoci18n;

import java.nio.file.Path;

/** 検査で見つかった問題。 */
final class Problem {

    /** 問題の種類。 */
    enum Kind {
        /** 訳文が無い。 */
        UNTRANSLATED,
        /** 翻訳ファイルが{@code sync}の結果と一致しない。 */
        OUT_OF_SYNC,
        /** 訳文や翻訳ファイルが正しくない。常に失敗として扱う。 */
        ERROR,
        /** 失敗としては扱わない注意。 */
        WARNING
    }

    final Kind kind;

    /** 問題のあるファイル。 */
    final Path file;

    /** 問題のある行(1始まり)。不明なら0。 */
    final int line;

    final String message;

    private Problem(Kind kind, Path file, int line, String message) {
        this.kind = kind;
        this.file = file;
        this.line = line;
        this.message = message;
    }

    static Problem untranslated(Path file, int line, String message) {
        return new Problem(Kind.UNTRANSLATED, file, line, message);
    }

    static Problem outOfSync(Path file, String message) {
        return new Problem(Kind.OUT_OF_SYNC, file, 0, message);
    }

    static Problem error(Path file, int line, String message) {
        return new Problem(Kind.ERROR, file, line, message);
    }

    static Problem warning(Path file, int line, String message) {
        return new Problem(Kind.WARNING, file, line, message);
    }
}
