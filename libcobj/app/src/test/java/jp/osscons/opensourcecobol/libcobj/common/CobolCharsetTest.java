package jp.osscons.opensourcecobol.libcobj.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** {@link CobolCharset}の単体テスト */
class CobolCharsetTest {
    /** 環境変数が未設定のときはShift_JISになること */
    @Test
    void resolveDefaultsToShiftJisWhenUnset() {
        assertEquals(Charset.forName("SHIFT-JIS"), CobolCharset.resolve(null));
    }

    /** 空文字列は未設定と同じ扱いになること */
    @Test
    void resolveTreatsEmptyAsUnset() {
        assertEquals(Charset.forName("SHIFT-JIS"), CobolCharset.resolve(""));
    }

    /** 指定した文字セット名が使われること */
    @Test
    void resolveUsesGivenName() {
        assertEquals(StandardCharsets.UTF_8, CobolCharset.resolve("UTF-8"));
        assertEquals(StandardCharsets.UTF_8, CobolCharset.resolve("utf8"));
    }

    /** 不正な文字セット名では、環境変数名と値を含むメッセージで失敗すること */
    @Test
    void resolveRejectsUnknownName() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> CobolCharset.resolve("NO-SUCH-CHARSET"));
        assertTrue(e.getMessage().contains("COB_CHARSET"));
        assertTrue(e.getMessage().contains("NO-SUCH-CHARSET"));
        assertThrows(IllegalArgumentException.class, () -> CobolCharset.resolve("bad name!"));
    }

    /** ASCIIと互換でない文字セットは拒否すること */
    @Test
    void resolveRejectsAsciiIncompatibleCharset() {
        assertThrows(IllegalArgumentException.class, () -> CobolCharset.resolve("UTF-16"));
    }
}
